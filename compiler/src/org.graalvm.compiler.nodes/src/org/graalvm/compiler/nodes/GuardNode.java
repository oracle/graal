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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Condition;
import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * A guard is a node that deoptimizes if its {@linkplain #getCondition() condition} is false (unless
 * it's {@linkplain #isNegated() negated} in which case it deoptimizes when the condition is true).
 *
 * Guards are not attached to a certain frame state, they can move around freely and will always use
 * the correct frame state when the nodes are scheduled (i.e., the last emitted frame state). The
 * node that is guarded has a data dependency on the guard and the guard in turn has a data
 * dependency on the condition. A guard may only be executed if it is guaranteed that the guarded
 * node is executed too (if no exceptions are thrown). Therefore, an anchor is placed after a
 * control flow split and the guard has a data dependency to the anchor. The anchor is the most
 * distant node that is post-dominated by the guarded node and the guard can be scheduled anywhere
 * between those two nodes. This ensures maximum flexibility for the guard node and guarantees that
 * deoptimization occurs only if the control flow would have reached the guarded node (without
 * taking exceptions into account).
 */
@NodeInfo(nameTemplate = "Guard(!={p#negated}) {p#reason/s}", allowedUsageTypes = {Guard}, size = SIZE_2, cycles = CYCLES_2)
public class GuardNode extends FloatingAnchoredNode implements Canonicalizable, GuardingNode, DeoptimizingGuard, IterableNodeType {

    public static final NodeClass<GuardNode> TYPE = NodeClass.create(GuardNode.class);
    @Input(Condition) protected LogicNode condition;
    protected DeoptimizationReason reason;
    protected DeoptimizationAction action;
    protected Speculation speculation;
    protected boolean negated;
    protected NodeSourcePosition noDeoptSuccessorPosition;

    public GuardNode(LogicNode condition, AnchoringNode anchor, DeoptimizationReason reason, DeoptimizationAction action, boolean negated, Speculation speculation,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        this(TYPE, condition, anchor, reason, action, negated, speculation, noDeoptSuccessorPosition);
    }

    protected GuardNode(NodeClass<? extends GuardNode> c, LogicNode condition, AnchoringNode anchor, DeoptimizationReason reason, DeoptimizationAction action, boolean negated,
                    Speculation speculation, NodeSourcePosition noDeoptSuccessorPosition) {
        super(c, StampFactory.forVoid(), anchor);
        this.condition = condition;
        this.reason = reason;
        this.action = action;
        this.negated = negated;
        this.speculation = speculation;
        this.noDeoptSuccessorPosition = noDeoptSuccessorPosition;
    }

    /**
     * Gets the instruction that produces the tested boolean value.
     *
     * The guard will deoptimize if the value is false (unless {@link #isNegated()} returns true in
     * which case it deoptimizes when the condition value is true).
     */
    @Override
    public LogicNode getCondition() {
        return condition;
    }

    @Override
    public void setCondition(LogicNode x, boolean negated) {
        updateUsages(condition, x);
        condition = x;
        this.negated = negated;
    }

    /**
     * Returns true iff the guard deoptimizes when its condition is true, false otherwise.
     */
    @Override
    public boolean isNegated() {
        return negated;
    }

    @Override
    public DeoptimizationReason getReason() {
        return reason;
    }

    @Override
    public DeoptimizationAction getAction() {
        return action;
    }

    @Override
    public Speculation getSpeculation() {
        return speculation;
    }

    public void setSpeculation(Speculation speculation) {
        this.speculation = speculation;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(verbosity);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getCondition() instanceof LogicNegationNode) {
            LogicNegationNode negation = (LogicNegationNode) getCondition();
            return new GuardNode(negation.getValue(), getAnchor(), reason, action, !negated, speculation, noDeoptSuccessorPosition);
        }
        if (getCondition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) getCondition();
            if (c.getValue() != negated) {
                return null;
            }
        }
        return this;
    }

    public FixedWithNextNode lowerGuard() {
        return null;
    }

    public void negate() {
        negated = !negated;
    }

    @Override
    public void setAction(DeoptimizationAction invalidaterecompile) {
        this.action = invalidaterecompile;
    }

    @Override
    public void setReason(DeoptimizationReason reason) {
        this.reason = reason;
    }

    @Override
    public NodeSourcePosition getNoDeoptSuccessorPosition() {
        return noDeoptSuccessorPosition;
    }

    @Override
    public void setNoDeoptSuccessorPosition(NodeSourcePosition noDeoptSuccessorPosition) {
        this.noDeoptSuccessorPosition = noDeoptSuccessorPosition;
    }
}
