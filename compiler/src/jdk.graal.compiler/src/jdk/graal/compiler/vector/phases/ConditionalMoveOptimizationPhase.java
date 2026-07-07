/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import static jdk.graal.compiler.vector.phases.ConditionalMoveOptimizationPhase.Options.MaxMispredictionCostIncreaseFactor;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;

/**
 * This phase tries to transform {@code if} statements to conditional moves. This has a positive
 * impact on code patterns, where branch prediction is difficult, e.g. in the monte carlo
 * simulation:
 *
 * <pre>
 * int n = &lt;...&gt;;
 * int hit = 0;
 * for (int i = 0; i &lt; n; i++) {
 *   double a = &lt;random&gt;;
 *   double b = &lt;random&gt;;
 *   if (a*a + b*b &lt; 1.0d) {
 *     hit++;
 *   }
 * }
 * </pre>
 *
 * We need to be conservative when introducing conditional moves because they are only beneficial if
 * a branch is hard to predict. We are using a heuristic because our profiling information does not
 * contain any information on how predictable a branch is. The branch probability does not help us
 * much as a 50% true probability can indicate a 100% random branch or a 99.9% predictable branch (n
 * times taken and afterwards m times not taken). Before introducing conditional moves, we need to
 * consider the at least the following costs:
 * <ul>
 * <li>All values that are needed for the conditional's true or false values are evaluated eagerly.
 * This can be especially costly when fully optimizing a cascade of IfNode such as
 * {@code if (a && b && c)} as it removes short circuit execution.</li>
 * <li>For predictable branches, the conditional move instruction is only an overhead.</li>
 * <li>The number of conditional moves that we need for replacing an if depends on the number of
 * values that are changed in the if.</li>
 * <li>Every conditional move (except the first one) introduces an additional test instruction.</li>
 * </ul>
 */
public class ConditionalMoveOptimizationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public ConditionalMoveOptimizationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Optimizes simple if branches with conditional moves. " +
                       "This can improve performance for patterns where branch prediction of a " +
                       "CPU does not work (if branches have nearly equal probability).", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptConditionalMoves = new OptionKey<>(true);
        @Option(help = "Abstract measure of the cost of branch misprediction. Higher values make generation of conditional moves more likely.",
                type = OptionType.Debug)
        public static final OptionKey<Double> MaxMispredictionCostIncreaseFactor = new OptionKey<>(2.0);
        @Option(help = "Perform CMove transformation on every IfNode possible.",
                        type = OptionType.Debug)
                public static final OptionKey<Boolean> CMoveALot = new OptionKey<>(false);
        // @formatter:on
    }

    private static final double PERFECTLY_PREDICTED_BRANCH_PROBABILITY = 0.99;
    private static final double EPSILON = 1E-6;

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        loopsData.detectCountedLoops();
        NodeBitMap visited = new NodeBitMap(graph);
        ArrayList<IfNode> ifStack = new ArrayList<>();
        for (IfNode n : graph.getNodes(IfNode.TYPE)) {
            if (!visited.isMarked(n)) {
                ifStack.add(n);
                while (!ifStack.isEmpty()) {
                    tryOptimize(visited, ifStack, loopsData);
                }
            }
        }
    }

    private static void tryOptimize(NodeBitMap visited, ArrayList<IfNode> ifStack, LoopsData loopsData) {
        IfNode ifNode = ifStack.get(ifStack.size() - 1);
        FixedNode nextTrueNode = ifNode.trueSuccessor().next();
        FixedNode nextFalseNode = ifNode.falseSuccessor().next();

        // Check if there are other IfNodes in our way that we might need to optimize before
        // optimizing the current if. By checking that, we ensure that the algorithm is
        // independent of the node order in the graph.
        if (visited.checkAndMarkInc(ifNode)) { // Ensures progress, node put on stack at most once.
            boolean skip = false;
            if (nextTrueNode instanceof IfNode) {
                ifStack.add((IfNode) nextTrueNode);  // Then process next IfNode.
                skip = true;
            }
            if (nextFalseNode instanceof IfNode) {
                ifStack.add((IfNode) nextFalseNode); // Then process next IfNode.
                skip = true;
            }
            if (skip) {
                return;
            }
        }

        ifStack.remove(ifStack.size() - 1);

        if (nextTrueNode instanceof AbstractEndNode && nextFalseNode instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) nextTrueNode;
            AbstractEndNode falseEnd = (AbstractEndNode) nextFalseNode;
            if (canBeOptimized(ifNode, trueEnd, falseEnd)) {
                AbstractMergeNode merge = trueEnd.merge();
                List<PhiNode> phis = merge.phis().snapshot();
                if (isWorthOptimizing(ifNode, trueEnd, falseEnd, phis, loopsData)) {
                    createConditionalsAndReplace(ifNode, trueEnd, falseEnd);
                }
            }
        }
    }

    @SuppressWarnings("try")
    public static void createConditionalsAndReplace(IfNode ifNode, AbstractEndNode trueEnd, AbstractEndNode falseEnd) {
        try (DebugCloseable position = ifNode.withNodeSourcePosition()) {
            List<PhiNode> phis = trueEnd.merge().phis().snapshot();
            for (PhiNode phi : phis) {
                ValueNode conditional = createConditional(ifNode, phi.valueAt(trueEnd), phi.valueAt(falseEnd));
                phi.setValueAt(trueEnd, conditional);
            }

            GraalError.guarantee(ifNode.trueSuccessor().hasNoUsages(), "Must not have usages %s", ifNode.trueSuccessor());
            GraalError.guarantee(ifNode.falseSuccessor().hasNoUsages(), "Must not have usages %s", ifNode.falseSuccessor());

            AbstractBeginNode trueBegin = ifNode.trueSuccessor();
            ifNode.graph().removeSplitPropagate(ifNode, trueBegin);
            trueBegin.graph().removeFixed(trueBegin);
            ifNode.graph().getOptimizationLog().report(ConditionalMoveOptimizationPhase.class, "ConditionalMoveConversion", ifNode);
        }
    }

    private static ValueNode createConditional(IfNode ifNode, ValueNode trueValue, ValueNode falseValue) {
        if (trueValue == falseValue) {
            return falseValue;
        } else {
            return ifNode.graph().unique(new ConditionalNode(ifNode.condition(), trueValue, falseValue));
        }
    }

    private static boolean canBeOptimized(IfNode ifNode, AbstractEndNode trueEnd, AbstractEndNode falseEnd) {
        return canBeOptimized(ifNode, trueEnd, falseEnd, false);
    }

    public static boolean canBeOptimized(IfNode ifNode, AbstractEndNode trueEnd, AbstractEndNode falseEnd, boolean allowFloatingPointConditionals) {
        return canBeOptimized(ifNode, trueEnd, falseEnd, allowFloatingPointConditionals, false);
    }

    public static boolean canBeOptimized(IfNode ifNode, AbstractEndNode trueEnd, AbstractEndNode falseEnd, boolean allowFloatingPointConditionals, boolean ignoreAnchors) {
        if (trueEnd.merge() != falseEnd.merge()) {
            return false;
        }
        if (ifNode.trueSuccessor() instanceof LoopExitNode || ifNode.falseSuccessor() instanceof LoopExitNode) {
            return false;
        }
        if (!ignoreAnchors) {
            if (ifNode.trueSuccessor().anchored().isNotEmpty() || ifNode.falseSuccessor().anchored().isNotEmpty()) {
                return false;
            }
        }

        for (PhiNode phi : trueEnd.merge().phis()) {
            ValueNode trueValue = phi.valueAt(trueEnd);
            ValueNode falseValue = phi.valueAt(falseEnd);
            if (trueValue != falseValue) {
                if (!(phi instanceof ValuePhiNode)) {
                    // we would need to create a conditional for a phi that is not a ValuePhi
                    return false;
                }

                // Floating point conditionals are not supported well on x86-64, so only allow them
                // if the caller (the loop vectorizer) asks for them explicitly.
                if (!allowFloatingPointConditionals) {
                    if (trueValue.stamp(NodeView.DEFAULT) instanceof FloatStamp || falseValue.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
                        return false;
                    }
                }

                // SIMD values are not supported for conditional move generation
                if (trueValue.stamp(NodeView.DEFAULT) instanceof SimdStamp || falseValue.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isWorthOptimizing(IfNode ifNode, AbstractEndNode trueEnd, AbstractEndNode falseEnd, List<PhiNode> phis, LoopsData loopsData) {
        final StructuredGraph graph = ifNode.graph();
        if (Options.CMoveALot.getValue(graph.getOptions())) {
            return true;
        }
        if (!ProfileSource.isTrusted(ifNode.profileSource())) {
            // don't try to optimize based on untrustworthy data
            return false;
        }
        if (ifNode.profileSource().isInferred()) {
            /*
             * Explicitly exclude inferred profiles from cmove optimization: ML models tend to use
             * 0.5 for unknown decisions as well. The branch predictor should only be bypassed by a
             * cmov for real 0.5 profiles.
             */
            return false;
        }
        double trueProbability = ifNode.getTrueSuccessorProbability();
        double falseProbability = 1 - trueProbability;
        if (trueProbability > PERFECTLY_PREDICTED_BRANCH_PROBABILITY || falseProbability > PERFECTLY_PREDICTED_BRANCH_PROBABILITY) {
            // it never makes sense to optimize branches that are easy to predict
            return false;
        }

        if (trueEnd.merge().forwardEndCount() > 2) {
            // we don't want to optimize cases where we are unable to remove the phi
            return false;
        }

        NodeMap<Integer> seenNodeUsages = new NodeMap<>(graph);

        double maxMispredictionRate = 2 * Math.min(trueProbability, falseProbability);
        double maxMispredictionCostIncreaseFactor = MaxMispredictionCostIncreaseFactor.getValue(graph.getOptions());
        double ifCosts = IfNode.TYPE.cycles().value;
        double conditionalCosts = 0.0;
        int conditionalCount = 0;
        NodeBitMap visited = graph.createNodeBitMap();
        for (PhiNode phi : phis) {
            ValueNode trueValue = phi.valueAt(trueEnd);
            ValueNode falseValue = phi.valueAt(falseEnd);
            if (trueValue != falseValue) {
                assert phi instanceof ValuePhiNode : phi;

                // If the phi node is attached to a loop we only want to optimize cases where it
                // represents a kind of conditional induction computation, for example:
                //
                // phi(init, phi, phi + 1)
                //
                // which would then become:
                //
                // phi(init, conditional(cond, phi, phi + 1))
                //
                // More precisely, only consider converting this phi if all of its inputs
                // (recursively) are the phi itself or loop invariant.
                if (phi.isLoopPhi() && !isConditionalInductionPhi((ValuePhiNode) phi, loopsData)) {
                    return false;
                }

                // we need one conditional for each mismatching phi. the true and false values that
                // are used by the conditional will be evaluated unconditionally.
                seenNodeUsages.clear();
                visited.clearAll();
                double trueValueCosts = computeCostsOfInput(trueValue, seenNodeUsages, visited);
                assert trueValue != phi || trueValueCosts == 0.0 : trueValue + " vs " + phi + " trueValueCosts=" + trueValueCosts;

                seenNodeUsages.clear();
                visited.clearAll();
                double falseValueCosts = computeCostsOfInput(falseValue, seenNodeUsages, visited);
                assert falseValue != phi || falseValueCosts == 0.0 : falseValue + " vs " + phi + " falseValueCosts=" + falseValueCosts;

                conditionalCosts += conditionalNodeCost(++conditionalCount) + trueValueCosts + falseValueCosts;
                ifCosts += trueValueCosts * trueProbability + falseValueCosts * falseProbability;

                double maxCosts = ifCosts + maxMispredictionCostIncreaseFactor * maxMispredictionRate + EPSILON;
                if (conditionalCosts > maxCosts) {
                    return false;
                }
            }
            GraalError.guarantee(ifNode.trueSuccessor().hasNoUsages(), "Must not have usages %s", ifNode.trueSuccessor());
            GraalError.guarantee(ifNode.falseSuccessor().hasNoUsages(), "Must not have usages %s", ifNode.falseSuccessor());
        }

        return true;
    }

    /**
     * Determines whether this phi node expresses an inductive computation involving a conditional
     * choice. The phi must be a loop phi ({@link PhiNode#isLoopPhi()}).
     */
    private static boolean isConditionalInductionPhi(ValuePhiNode phi, LoopsData loopsData) {
        Loop loop = loopsData.loop((LoopBeginNode) phi.merge());

        NodeFlood worklist = phi.graph().createNodeFlood();
        worklist.addAll(phi.values());
        for (Node n : worklist) {
            if (n == phi || loop.isOutsideLoop(n)) {
                continue;
            } else if (n instanceof ArithmeticOperation) {
                worklist.addAll(n.inputs());
            } else {
                return false;
            }
        }

        // All inputs of the phi are simple arithmetic nodes using only loop invariant inputs or the
        // phi itself.
        return true;
    }

    private static int conditionalNodeCost(int conditionalCount) {
        int costs = ConditionalNode.TYPE.cycles().value;
        if (conditionalCount > 1) {
            // for every further conditional, we need to create one additional test instruction
            costs += IntegerEqualsNode.TYPE.cycles().value;
        }
        return costs;
    }

    private static final int MAX_INPUT_COST_ITERATION = 128;

    private static final double MAX_COST_INPUT_OPTIMIZATION = Double.MAX_VALUE;

    private static double computeCostsOfInput(ValueNode value, NodeMap<Integer> seenNodeUsages, NodeBitMap visited) {
        NodeStack toProcess = new NodeStack();
        /*
         * Only count the cost of nodes that are exclusively used by either the true or the false
         * value. Here, we assume that values don't costs anything if they have other usages as such
         * values need to be computed for the other usages anyways.
         *
         * This works reasonably but it is *not* a 100% valid assumption as the compiler can
         * duplicate code parts (e.g., during partial redundancy elimination).
         */
        double result = 0.0;
        toProcess.push(value);
        int iterations = 0;
        visited.mark(value);
        while (!toProcess.isEmpty()) {
            if (iterations++ > MAX_INPUT_COST_ITERATION) {
                /*
                 * Input cost estimation is getting out of hand. Aborting iteration and return
                 * maximum value here. The cost is then so high the caller will not perform this
                 * particular optimization.
                 */
                return MAX_COST_INPUT_OPTIMIZATION;
            }
            ValueNode cur = (ValueNode) toProcess.pop();
            if (cur instanceof FloatingNode && seenAllUsages(seenNodeUsages, cur)) {
                result += getCost(cur);
                for (Node input : cur.inputs()) {
                    if (visited.isMarkedAndGrow(input)) {
                        // Break cycles on loop phi backedges
                        continue;
                    }
                    toProcess.push(input);
                }
            }
        }
        return result;
    }

    private static int getCost(ValueNode value) {
        NodeCycles cycles = value.estimatedNodeCycles();
        if (cycles.isValueKnown()) {
            return cycles.value;
        }

        // if the cycles are not defined, fall back to the size so that we at least avoid pulling in
        // complex computations
        NodeSize nodeSize = value.estimatedNodeSize();
        return nodeSize.value;
    }

    private static boolean seenAllUsages(NodeMap<Integer> seenNodeUsages, Node input) {
        int usageCount = input.getUsageCount();
        if (usageCount == 1) {
            return true;
        } else {
            int seenUsages = seenUsages(seenNodeUsages, input) + 1;
            assert usageCount > 1 && seenUsages <= usageCount : "UsageCount=" + usageCount + " seenUsages=" + seenUsages + " for " + input + seenNodeUsages;
            seenNodeUsages.put(input, seenUsages);
            return seenUsages == usageCount;
        }
    }

    private static int seenUsages(NodeMap<Integer> seenNodeUsages, Node input) {
        Integer value = seenNodeUsages.get(input);
        if (value == null) {
            return 0;
        } else {
            return value.intValue();
        }
    }
}
