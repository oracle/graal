/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.SwitchFoldable;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

@NodeInfo(nameTemplate = "FixedGuard(!={p#negated}) {p#reason/s}", allowedUsageTypes = Guard, size = SIZE_2, cycles = CYCLES_2)
public final class FixedGuardNode extends AbstractFixedGuardNode implements Lowerable, IterableNodeType, SwitchFoldable {
    public static final NodeClass<FixedGuardNode> TYPE = NodeClass.create(FixedGuardNode.class);

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, false);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, negated);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated, NodeSourcePosition noDeoptSuccessorPosition) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, negated, noDeoptSuccessorPosition);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated) {
        super(TYPE, condition, deoptReason, action, speculation, negated);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        super(TYPE, condition, deoptReason, action, speculation, negated, noDeoptSuccessorPosition);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        super.simplify(tool);

        if (getCondition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) getCondition();
            if (c.getValue() == isNegated()) {
                FixedNode currentNext = this.next();
                if (currentNext != null) {
                    tool.deleteBranch(currentNext);
                }

                DeoptimizeNode deopt = graph().add(new DeoptimizeNode(getAction(), getReason(), getSpeculation()));
                deopt.setStateBefore(stateBefore());
                setNext(deopt);
            }
            if (hasUsages()) {
                /*
                 * Try to eliminate any exposed usages. We could always just insert the
                 * ValueAnchorNode and let canonicalization clean it up but since it's almost always
                 * trivially removable it's better to clean it up here. This also means that any
                 * actual insertions of a ValueAnchorNode by this code indicates a real mismatch
                 * between the piStamp and its input.
                 */
                PiNode.tryEvacuate(tool, this);
            }
            if (hasUsages()) {
                /*
                 * There are still guard usages after simplification so preserve this anchor point.
                 * It may still go away after further simplification but any uses that hang around
                 * are required to be anchored. After guard lowering it might be possible to replace
                 * the ValueAnchorNode with the Begin of the current block.
                 */
                graph().replaceFixedWithFixed(this, graph().add(new ValueAnchorNode()));
            } else {
                graph().removeFixed(this);
            }
        } else if (getCondition() instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode shortCircuitOr = (ShortCircuitOrNode) getCondition();
            if (isNegated() && hasNoUsages()) {
                graph().addAfterFixed(this,
                                graph().add(new FixedGuardNode(shortCircuitOr.getY(), getReason(), getAction(), getSpeculation(), !shortCircuitOr.isYNegated(), getNoDeoptSuccessorPosition())));
                graph().replaceFixedWithFixed(this,
                                graph().add(new FixedGuardNode(shortCircuitOr.getX(), getReason(), getAction(), getSpeculation(), !shortCircuitOr.isXNegated(), getNoDeoptSuccessorPosition())));
            }
        }
    }

    public boolean canFloat() {
        return DeoptimizeNode.canFloat(getReason(), getAction());
    }

    @SuppressWarnings("try")
    @Override
    public void lower(LoweringTool tool) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            if (graph().getGuardsStage().allowsFloatingGuards()) {
                if (canFloat()) {
                    ValueNode guard = tool.createGuard(this, getCondition(), getReason(), getAction(), getSpeculation(), isNegated(), getNoDeoptSuccessorPosition()).asNode();
                    this.replaceAtUsages(guard);
                    graph().removeFixed(this);
                }
            } else {
                lowerToIf().lower(tool);
            }
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public Node getNextSwitchFoldableBranch() {
        return next();
    }

    @Override
    public boolean isInSwitch(ValueNode switchValue) {
        return hasNoUsages() && isNegated() && SwitchFoldable.maybeIsInSwitch(condition()) && SwitchFoldable.sameSwitchValue(condition(), switchValue);
    }

    @Override
    public void cutOffCascadeNode() {
        /* nop */
    }

    @Override
    public void cutOffLowestCascadeNode() {
        setNext(null);
    }

    @Override
    public boolean isDefaultSuccessor(AbstractBeginNode beginNode) {
        return beginNode.next() == next();
    }

    @Override
    public AbstractBeginNode getDefault() {
        FixedNode defaultNode = next();
        setNext(null);
        return BeginNode.begin(defaultNode);
    }

    @Override
    public ValueNode switchValue() {
        if (SwitchFoldable.maybeIsInSwitch(condition())) {
            return ((IntegerEqualsNode) condition()).getX();
        }
        return null;
    }

    @Override
    public boolean isNonInitializedProfile() {
        // @formatter:off
        // Checkstyle: stop
        /*
         * These nodes can appear in non initialized cascades. Though they are technically profiled
         * nodes, their presence does not really prevent us from constructing a uniform distribution
         * for the new switch, while keeping these to probability 0. Furthermore, these can be the
         * result of the pattern:
         * if (c) {
         *     CompilerDirectives.transferToInterpreter();
         * }
         * Since we cannot differentiate this case from, say, a guard created because profiling
         * determined that the branch was never taken, and given what we saw before, we will
         * consider all fixedGuards as nodes with no profiles for switch folding purposes.
         */
        // Checkstyle: resume
        // @formatter:on
        return true;
    }

    @Override
    public ProfileData.ProfileSource profileSource() {
        return ProfileData.ProfileSource.INJECTED;
    }

    @Override
    public int intKeyAt(int i) {
        assert i == 0 : i;
        return ((IntegerEqualsNode) condition()).getY().asJavaConstant().asInt();
    }

    @Override
    public double keyProbability(int i) {
        return 0;
    }

    @Override
    public AbstractBeginNode keySuccessor(int i) {
        DeoptimizeNode deopt = new DeoptimizeNode(getAction(), getReason(), getSpeculation());
        deopt.setNodeSourcePosition(getNodeSourcePosition());
        AbstractBeginNode begin = new BeginNode();
        // Link the two nodes, but do not add them to the graph yet, so we do not need to remove
        // them on an abort.
        begin.next = deopt;
        return begin;
    }

    @Override
    public double defaultProbability() {
        return 1.0d;
    }

    /**
     * Determine if the this guard will lead to an unconditional {@link DeoptimizeNode} because its
     * condition is a constant.
     */
    public boolean willDeoptUnconditionally() {
        if (getCondition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) getCondition();
            return c.getValue() == negated;
        }
        return false;
    }

}
