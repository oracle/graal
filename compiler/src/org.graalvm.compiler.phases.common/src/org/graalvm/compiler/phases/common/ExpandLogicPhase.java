/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AbstractNormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.phases.Phase;

public class ExpandLogicPhase extends Phase {
    private static final double EPSILON = 1E-6;

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        for (ShortCircuitOrNode logic : graph.getNodes(ShortCircuitOrNode.TYPE)) {
            processBinary(logic);
        }
        assert graph.getNodes(ShortCircuitOrNode.TYPE).isEmpty();

        for (AbstractNormalizeCompareNode logic : graph.getNodes(AbstractNormalizeCompareNode.TYPE)) {
            try (DebugCloseable context = logic.withNodeSourcePosition()) {
                processNormalizeCompareNode(logic);
            }
        }
        graph.setAfterStage(StageFlag.EXPAND_LOGIC);
    }

    private static void processNormalizeCompareNode(AbstractNormalizeCompareNode normalize) {
        StructuredGraph graph = normalize.graph();
        LogicNode equalComp = graph.addOrUniqueWithInputs(normalize.createEqualComparison());
        LogicNode lessComp = graph.addOrUniqueWithInputs(normalize.createLowerComparison());
        Stamp stamp = normalize.stamp(NodeView.DEFAULT);
        ConditionalNode equalValue = graph.unique(new ConditionalNode(equalComp, ConstantNode.forIntegerStamp(stamp, 0, graph), ConstantNode.forIntegerStamp(stamp, 1, graph)));
        ConditionalNode value = graph.unique(new ConditionalNode(lessComp, ConstantNode.forIntegerStamp(stamp, -1, graph), equalValue));
        normalize.replaceAtUsagesAndDelete(value);
    }

    @SuppressWarnings("try")
    private static void processBinary(ShortCircuitOrNode binary) {
        while (binary.usages().isNotEmpty()) {
            Node usage = binary.usages().first();
            try (DebugCloseable nsp = usage.withNodeSourcePosition()) {
                if (usage instanceof ShortCircuitOrNode) {
                    processBinary((ShortCircuitOrNode) usage);
                } else if (usage instanceof IfNode) {
                    processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage, binary.getShortCircuitProbability().getDesignatedSuccessorProbability());
                } else if (usage instanceof ConditionalNode) {
                    processConditional(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (ConditionalNode) usage);
                } else {
                    throw GraalError.shouldNotReachHere();
                }
            }
        }
        binary.safeDelete();
    }

    private static void processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, double shortCircuitProbability) {
        /*
         * this method splits an IfNode, which has a ShortCircuitOrNode as its condition, into two
         * separate IfNodes: if(X) and if(Y)
         *
         * for computing the probabilities P(X) and P(Y), we use two different approaches. The first
         * one assumes that the shortCircuitProbability and the probability on the IfNode were
         * created with each other in mind. If this assumption does not hold, we fall back to
         * another mechanism for computing the probabilities.
         */
        AbstractBeginNode trueTarget = ifNode.trueSuccessor();
        AbstractBeginNode falseTarget = ifNode.falseSuccessor();

        // 1st approach
        // assumption: P(originalIf.trueSuccessor) == P(X) + ((1 - P(X)) * P(Y))
        double firstIfTrueProbability = shortCircuitProbability;
        double secondIfTrueProbability = sanitizeProbability((ifNode.getTrueSuccessorProbability() - shortCircuitProbability) / (1 - shortCircuitProbability));
        double expectedOriginalIfTrueProbability = firstIfTrueProbability + (1 - firstIfTrueProbability) * secondIfTrueProbability;

        if (!doubleEquals(ifNode.getTrueSuccessorProbability(), expectedOriginalIfTrueProbability)) {
            /*
             * 2nd approach
             *
             * the assumption above did not hold, so we either used an artificial probability as
             * shortCircuitProbability or the ShortCircuitOrNode was moved to some other IfNode.
             *
             * so, we distribute the if's trueSuccessorProbability between the newly generated if
             * nodes according to the shortCircuitProbability. the following invariant is always
             * true in this case: P(originalIf.trueSuccessor) == P(X) + ((1 - P(X)) * P(Y))
             */
            firstIfTrueProbability = ifNode.getTrueSuccessorProbability() * shortCircuitProbability;
            secondIfTrueProbability = sanitizeProbability(1 - (ifNode.probability(falseTarget) / (1 - firstIfTrueProbability)));
        }

        ifNode.clearSuccessors();
        Graph graph = ifNode.graph();
        AbstractMergeNode trueTargetMerge = graph.add(new MergeNode());
        trueTargetMerge.setNext(trueTarget);
        EndNode firstTrueEnd = graph.add(new EndNode());
        EndNode secondTrueEnd = graph.add(new EndNode());
        trueTargetMerge.addForwardEnd(firstTrueEnd);
        trueTargetMerge.addForwardEnd(secondTrueEnd);
        AbstractBeginNode firstTrueTarget = BeginNode.begin(firstTrueEnd);
        firstTrueTarget.setNodeSourcePosition(trueTarget.getNodeSourcePosition());
        AbstractBeginNode secondTrueTarget = BeginNode.begin(secondTrueEnd);
        secondTrueTarget.setNodeSourcePosition(trueTarget.getNodeSourcePosition());
        if (yNegated) {
            secondIfTrueProbability = 1.0 - secondIfTrueProbability;
        }
        if (xNegated) {
            firstIfTrueProbability = 1.0 - firstIfTrueProbability;
        }
        IfNode secondIf = new IfNode(y, yNegated ? falseTarget : secondTrueTarget, yNegated ? secondTrueTarget : falseTarget, ifNode.getProfileData().copy(secondIfTrueProbability));
        secondIf.setNodeSourcePosition(ifNode.getNodeSourcePosition());
        AbstractBeginNode secondIfBegin = BeginNode.begin(graph.add(secondIf));
        secondIfBegin.setNodeSourcePosition(falseTarget.getNodeSourcePosition());
        IfNode firstIf = graph.add(new IfNode(x, xNegated ? secondIfBegin : firstTrueTarget, xNegated ? firstTrueTarget : secondIfBegin, ifNode.getProfileData().copy(firstIfTrueProbability)));
        firstIf.setNodeSourcePosition(ifNode.getNodeSourcePosition());
        ifNode.replaceAtPredecessor(firstIf);
        ifNode.safeDelete();
    }

    private static boolean doubleEquals(double a, double b) {
        assert !Double.isNaN(a) && !Double.isNaN(b) && !Double.isInfinite(a) && !Double.isInfinite(b);
        return a - EPSILON < b && a + EPSILON > b;
    }

    private static double sanitizeProbability(double value) {
        double newValue = Math.min(1.0, Math.max(0.0, value));
        if (Double.isNaN(newValue)) {
            newValue = 0.5;
        }
        return newValue;
    }

    @SuppressWarnings("try")
    private static void processConditional(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ConditionalNode conditional) {
        try (DebugCloseable context = conditional.withNodeSourcePosition()) {
            ValueNode trueTarget = conditional.trueValue();
            ValueNode falseTarget = conditional.falseValue();
            Graph graph = conditional.graph();
            ConditionalNode secondConditional = graph.unique(new ConditionalNode(y, yNegated ? falseTarget : trueTarget, yNegated ? trueTarget : falseTarget));
            ConditionalNode firstConditional = graph.unique(new ConditionalNode(x, xNegated ? secondConditional : trueTarget, xNegated ? trueTarget : secondConditional));
            conditional.replaceAndDelete(firstConditional);
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
