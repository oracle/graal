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

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node.IndirectInputChangedCanonicalization;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.calc.FloatingNode;

import jdk.vm.ci.meta.TriState;

@NodeInfo(allowedUsageTypes = {Condition}, size = SIZE_1)
public abstract class LogicNode extends FloatingNode implements IndirectInputChangedCanonicalization {

    public static final NodeClass<LogicNode> TYPE = NodeClass.create(LogicNode.class);

    public LogicNode(NodeClass<? extends LogicNode> c) {
        super(c, StampFactory.forVoid());
    }

    public static LogicNode and(LogicNode a, LogicNode b, BranchProbabilityData shortCircuitProbability) {
        return and(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode and(LogicNode a, boolean negateA, LogicNode b, boolean negateB, BranchProbabilityData shortCircuitProbability) {
        StructuredGraph graph = a.graph();
        LogicNode notAorNotB = graph.addOrUniqueWithInputs(ShortCircuitOrNode.create(a, !negateA, b, !negateB, shortCircuitProbability));
        return graph.addOrUniqueWithInputs(LogicNegationNode.create(notAorNotB));
    }

    public static LogicNode or(LogicNode a, LogicNode b, BranchProbabilityData shortCircuitProbability) {
        return or(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode or(LogicNode a, boolean negateA, LogicNode b, boolean negateB, BranchProbabilityData shortCircuitProbability) {
        return a.graph().unique(new ShortCircuitOrNode(a, negateA, b, negateB, shortCircuitProbability));
    }

    public final boolean isTautology() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return logicConstantNode.getValue();
        }

        return false;
    }

    public final boolean isContradiction() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return !logicConstantNode.getValue();
        }

        return false;
    }

    /**
     * Determines what this condition implies about the other.
     *
     * <ul>
     * <li>If negate(this, thisNegated) => other, returns {@link TriState#TRUE}</li>
     * <li>If negate(this, thisNegated) => !other, returns {@link TriState#FALSE}</li>
     * </ul>
     *
     * @param thisNegated whether this condition should be considered as false.
     * @param other the other condition.
     */
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (this == other) {
            return TriState.get(!thisNegated);
        }
        if (other instanceof LogicNegationNode) {
            return flip(this.implies(thisNegated, ((LogicNegationNode) other).getValue()));
        }
        return TriState.UNKNOWN;
    }

    private static TriState flip(TriState triState) {
        return triState.isUnknown()
                        ? triState
                        : TriState.get(!triState.toBoolean());
    }
}
