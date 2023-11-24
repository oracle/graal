/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.Optional;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AbstractNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

public class ExpandLogicPhase extends PostRunCanonicalizationPhase<CoreProviders> {
    private static final double EPSILON = 1E-6;

    public ExpandLogicPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<BasePhase.NotApplicable> notApplicableTo(GraphState graphState) {
        return BasePhase.NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        BasePhase.NotApplicable.ifApplied(this, StageFlag.EXPAND_LOGIC, graphState),
                        BasePhase.NotApplicable.unlessRunAfter(this, StageFlag.LOW_TIER_LOWERING, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        expandLogic(graph);
    }

    @SuppressWarnings("try")
    public static void expandLogic(StructuredGraph graph) {
        for (ShortCircuitOrNode logic : graph.getNodes(ShortCircuitOrNode.TYPE)) {
            expandBinary(logic);
        }
        assert graph.getNodes(ShortCircuitOrNode.TYPE).isEmpty();

        for (AbstractNormalizeCompareNode logic : graph.getNodes(AbstractNormalizeCompareNode.TYPE)) {
            try (DebugCloseable s = logic.withNodeSourcePosition()) {
                processNormalizeCompareNode(logic);
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.EXPAND_LOGIC);
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
    private static void expandBinary(ShortCircuitOrNode s) {
        NodeStack toProcess = new NodeStack();
        toProcess.push(s);

        outer: while (!toProcess.isEmpty()) {
            ShortCircuitOrNode binary = (ShortCircuitOrNode) toProcess.pop();

            while (binary.usages().isNotEmpty()) {
                Node usage = binary.usages().first();
                try (DebugCloseable nsp = usage.withNodeSourcePosition()) {
                    if (usage instanceof ShortCircuitOrNode) {
                        toProcess.push(binary);
                        toProcess.push(usage);
                        continue outer;
                    } else if (usage instanceof IfNode) {
                        processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage, binary.getShortCircuitProbability().getDesignatedSuccessorProbability());
                    } else if (usage instanceof ConditionalNode) {
                        processConditional(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (ConditionalNode) usage);
                    } else {
                        throw GraalError.shouldNotReachHereUnexpectedValue(usage); // ExcludeFromJacocoGeneratedReport
                    }
                }
            }
            binary.safeDelete();

        }

    }

    private static void processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, double shortCircuitProbability) {
        processIf(x, xNegated, y, yNegated, ifNode, shortCircuitProbability, false);
    }

    /**
     * Expand the given logic {@code or} node represented by {@code x} and {@code y} to actual
     * control flow at the given {@code ifNode} original usage.
     *
     * For example the code shape
     *
     * <pre>
     * if (x || y) {
     *     a();
     * } else {
     *     b();
     * }
     * </pre>
     *
     * will be expanded to
     *
     * <pre>
     * if(x){
     *  goto trueMerge;
     * } else {
     *  if(y) {
     *      goto trueMerge;
     *  }else {
     *      goto falseMerge;
     *  }
     * }
     * trueMerge:
     *     a();
     * falseMerge:
     *     b();
     * </pre>
     *
     * If {@code createGuardPhi == true} this method will return a {@code GuardPhiNode} on the
     * {@code trueMerge} with the two true successor branches as guard inputs.
     */
    public static GuardPhiNode processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, double shortCircuitProbability, boolean createGuardPhi) {
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

        GuardPhiNode guardPhi = null;

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
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing if %s", ifNode);

        if (createGuardPhi) {
            guardPhi = graph.addWithoutUnique(new GuardPhiNode(trueTargetMerge));
            guardPhi.addInput(firstIf.trueSuccessor());
            guardPhi.addInput(secondIf.trueSuccessor());
        }

        return guardPhi;
    }

    private static boolean doubleEquals(double a, double b) {
        assert !Double.isNaN(a) && !Double.isNaN(b) && !Double.isInfinite(a) && !Double.isInfinite(b) : a + " " + b;
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
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing conditional %s", conditional);
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
