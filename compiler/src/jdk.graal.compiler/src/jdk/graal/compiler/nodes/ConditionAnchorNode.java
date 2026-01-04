/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A {@link ConditionAnchorNode} is used to anchor a floatable unsafe load or {@linkplain PiNode
 * unsafe cast} in the control flow and associate it with a control flow dependency (i.e. a guard),
 * represented as a boolean condition. Conditional Elimination then tries to find a guard that
 * corresponds to this condition, and rewires the condition anchor's usages to that guard. If no
 * such relationship can be established, the condition anchor is replaced with an unconditional
 * {@link ValueAnchorNode}.
 *
 * @see jdk.graal.compiler.phases.common.ConditionalEliminationPhase
 * @see jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode
 */
@NodeInfo(nameTemplate = "ConditionAnchor(!={p#negated})", allowedUsageTypes = Guard, cycles = CYCLES_0, size = SIZE_0)
public final class ConditionAnchorNode extends FixedWithNextNode implements Canonicalizable.Unary<Node>, Lowerable, GuardingNode {

    public static final NodeClass<ConditionAnchorNode> TYPE = NodeClass.create(ConditionAnchorNode.class);
    @Input(Condition) LogicNode condition;
    protected boolean negated;

    public ConditionAnchorNode(LogicNode condition, boolean negated) {
        super(TYPE, StampFactory.forVoid());
        this.negated = negated;
        this.condition = condition;
    }

    /**
     * Creates a condition anchor from a boolean value representing the guarding condition.
     *
     * Note: The caller must handle the case where no anchor is needed for constant true.
     *
     * @param booleanValue the condition as a boolean value
     */
    public static FixedWithNextNode create(ValueNode booleanValue, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, NodeView view) {
        if (booleanValue.isConstant()) {
            return new ValueAnchorNode();
        } else {
            return create(CompareNode.createCompareNode(constantReflection, metaAccess, options, null, CanonicalCondition.EQ, booleanValue, ConstantNode.forBoolean(true), view));
        }
    }

    /**
     * Creates a condition anchor from a logical comparison representing the guarding condition.
     *
     * @param condition the condition
     * @see #canonical(CanonicalizerTool, Node)
     */
    public static FixedWithNextNode create(LogicNode condition) {
        if (condition instanceof LogicConstantNode) {
            /*
             * Even if the condition is true, an anchor that has usages must still exist since it's
             * possible the condition is true for control flow reasons so the Pi stamp is also only
             * valid for those reasons.
             */
            return new ValueAnchorNode();
        } else {
            return new ConditionAnchorNode(condition, false);
        }
    }

    public LogicNode condition() {
        return condition;
    }

    public boolean isNegated() {
        return negated;
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
    public Node canonical(CanonicalizerTool tool, Node forValue) {
        if (forValue instanceof LogicNegationNode negation) {
            return new ConditionAnchorNode(negation.getValue(), !negated);
        }
        if (forValue instanceof LogicConstantNode c) {
            // An anchor that still has usages must still exist since it's possible the condition is
            // true for control flow reasons so the Pi stamp is also only valid for those reasons.
            if (c.getValue() == negated || hasUsages()) {
                return new ValueAnchorNode();
            } else {
                return null;
            }
        }
        if (tool.allUsagesAvailable() && this.hasNoUsages()) {
            return null;
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areDeoptsFixed()) {
            ValueAnchorNode newAnchor = graph().add(new ValueAnchorNode());
            graph().replaceFixedWithFixed(this, newAnchor);
        }
    }

    @Override
    public Node getValue() {
        return condition;
    }
}
