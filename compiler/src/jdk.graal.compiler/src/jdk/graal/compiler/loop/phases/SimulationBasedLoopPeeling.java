/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.phases;

import jdk.graal.compiler.phases.common.util.LoopUtility;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static jdk.graal.compiler.options.OptionType.Debug;
import static org.graalvm.word.LocationIdentity.any;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.phases.LoopVectorizationAnalysis;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiArrayNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.memory.FloatableThreadLocalAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Binary;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Unary;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.contract.NodeCostUtil;
import jdk.graal.compiler.truffle.nodes.TruffleSafepointNode;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.IndexedCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.LoadCacheEntry;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState.UnsafeLoadCacheEntry;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * This class implements a complex loop peeling heuristic based on dominance-based duplication
 * simulation (used by the tail duplication algorithm) see {@linkplain DuplicationPhase} for
 * details.
 *
 * Loop peeling is an optimization that cuts off interesting first or last iterations of a loop.
 * This process of cutting off a loop iteration is called "peeling" off an iteration.
 *
 * Consider the following code:
 *
 * <pre>
 * int phi = 0;
 * while (c) {
 *     // use phi
 *     if (phi == 0) {
 *         // do something special
 *     }
 *     phi++;
 * }
 * </pre>
 *
 * In this loop the user code performs the if only in case phi == 0, which is the case only in the
 * first iteration. Peeling of the first iteration can be beneficial to simplify the loop to:
 *
 * <pre>
 *  // use phi where phi == 0
 *  // do something special
 *  phi = 1
 *  while(c){
 *      // use phi
 *      phi ++;
 *  }
 * </pre>
 *
 * Now the loop is simpler and subject to more optimizations, e.g. partial unrolling or
 * vectorization since it is smaller and simpler. Additionally, the runtime of the loop is reduced
 * since the check if phi == 0 is no longer part of the code of the loop.
 *
 * The heuristic implemented in this class uses simulation, i.e. acting as if the loop was already
 * peeled, to find interesting optimizations applicable after peeling a loop.
 *
 * It does so in a 2 phase process:
 *
 * (1) the heuristic is first interested in the optimization potential in the peeled fraction of the
 * loop after peeling, therefore it uses the body of the loop and replaces all loop phis with their
 * values in the initial iteration and sees if something is optimizable For example, for the loop
 * above, replacing the usages of the phi with its initial value yields the following code:
 *
 * <pre>
 *  // use 0
 *  if(0==0) {
 *   // do something special
 *  }
 *  phi=0+1
 * </pre>
 *
 * where we can easily see the compiler can simulate the effects of a conditional elimination and
 * deduce that the if folds away.
 *
 * in the (2) phase the compiler does the same by using a combined value for all backedge values of
 * a loop phi and simulates how the loop is affected by peeling the first iteration, i.e. excluding
 * the forward phi values from their variable stamps yielding the code below [beware, for the
 * simulation step of the loop fraction we cannot assume there are no backedges thus the loop still
 * is a loop]
 *
 * <pre>
 *  phi = [1,max] // definitely does not include 0
 *  while (c) {
 *     // use phi
 *     if (phi == 0) { // phi must never be 0 after peeling thus this is always false
 *         // do something special
 *     }
 *     phi++;
 *  }
 * </pre>
 */
public class SimulationBasedLoopPeeling {

    public static final CounterKey PeelingsIgnored = DebugContext.counter("SimulationBasedPeeling_Ignored");
    public static final CounterKey PossiblePeelings = DebugContext.counter("SimulationBasedPeeling_Possible");
    public static final CounterKey VectorizablePeelingsIgnored = DebugContext.counter("SimulationBasedPeeling_NotPeeledVectorizable");

    public static class Options {
        //@formatter:off
        @Option(help = "Uses the dominance-based duplication simulation (DBDS) algorithm to simulate the impact of peeling on a loop.", type = OptionType.Expert)
        public static final OptionKey<Boolean> SimulationBasedLoopPeeling = new OptionKey<>(true);

        @Option(help = "Minimal relative frequency of loop begin necessary to consider peeling.", type = OptionType.Debug)
        /*
         * Minimum relative frequency to consider peeling. We must at least execute
         * as often as the start block to consider optimizing the "first" iteration of a loop.
         * If the relative frequency is < 1 it means we not even execute the loop entry every
         * invocation of the method.
         */
        public static final OptionKey<Double> PeelingConsideredMinRelativeFrequency = new OptionKey<>(4.0D);
        /*
         * Minimum number of loop body iterations to consider peeling. We must expect to execute the loop body more than just once to consider optimizing the "first" iteration of a loop.
         */
        @Option(help = "Minimal loop body iterations necessary to consider peeling.", type = OptionType.Debug)
        public static final OptionKey<Double> PeelingConsideredMinLoopIterations = new OptionKey<>(1.5D);
        @Option(help = "Debug simulation synonyms during simulation-based loop peeling.", type = Debug)
        public static final OptionKey<Boolean> DebugPeelingSynonyms = new OptionKey<>(false);
        @Option(help = "Loop peeling will consider any loop with a size (in terms of estimated machine instructions) below this " +
                       "value to be a prime candidate for peeling. Larger loops will only be considered for peeling if the " +
                       "simulated benefit of peeling is relatively high. The larger the loop, the greater the expected benefit " +
                       "has to be.", type = Debug)
        public static final OptionKey<Double> TrivialLoopSizeLimitForPeeling = new OptionKey<>(512D);
        @Option(help = "Cost/Benefit heuristic for simulation-based loop peeling in high tier: reduce cost by a constant factor when " +
                       "comparing with relative benefit.", type = Debug)
        public static final OptionKey<Double> PeelingHighTierCostReductionFactor = new OptionKey<>(64D);
        @Option(help = "Cost/Benefit heuristic for simulation-based loop peeling in mid tier: reduce cost by a constant factor when " +
                       "comparing with relative benefit.", type = Debug)
        public static final OptionKey<Double> PeelingMidTierCostReductionFactor = new OptionKey<>(8D);
        @Option(help = "Allow iterative peeling of loops with an outer frequency bonus above this value.", type = Debug)
        public static final OptionKey<Double> IterativePeelingOuterFrequencyBonusThreshold = new OptionKey<>(4D);
        @Option(help = "Method Filter to ignore vectorization checks for peeling.", type = Debug)
        public static final OptionKey<String> PeelingIgnoreVectorizationCheck = new OptionKey<>(null);
        //@formatter:on
    }

    /**
     * In high tier there are more opportunities available due to the higher level of abstraction,
     * therefore we allow more sub-optimal opportunities to be peeled.
     */
    public static LoopPeelingFactors getHighTierPeelingFactors(OptionValues options) {
        return new LoopPeelingFactors(Options.PeelingHighTierCostReductionFactor.getValue(options), true);
    }

    /**
     * In mid tier there are generally less opportunities available, thus we try to find high
     * probably ones, which we will boost by giving them a high loop frequency bonus. In general
     * however, we reduce the budget for mid-tier peeling requiring better opportunities than in
     * high-tier.
     */
    public static LoopPeelingFactors getMidTierPeelingFactors(OptionValues options) {
        return new LoopPeelingFactors(Options.PeelingMidTierCostReductionFactor.getValue(options), false);

    }

    public static class LoopPeelingFactors {
        private final double costReductionFactor;

        private final boolean considerPEA;

        public LoopPeelingFactors(double costReductionFactor, boolean considerPEA) {
            this.costReductionFactor = costReductionFactor;
            this.considerPEA = considerPEA;
        }
    }

    private static final TimerKey graphSizeCheck = DebugContext.timer("Time_Peeling_GraphSizeCheck");

    private static int computeDominatedInvariantConditionsAfterPeeling(ControlFlowGraph cfg, Loop loop) {
        int dominatedLoopInvariantConditions = 0;
        LoopBeginNode lb = loop.loopBegin();
        HIRBlock beginBlock = cfg.blockFor(lb);
        HIRBlock commonEndDominator = cfg.commonDominatorFor(lb.loopEnds());

        if (commonEndDominator != null) {
            HIRBlock cur = commonEndDominator;
            while (cur != beginBlock) {
                AbstractBeginNode beginNode = cur.getBeginNode();
                Node predecessor = beginNode.predecessor();
                if (predecessor instanceof IfNode) {
                    IfNode ifNode = (IfNode) predecessor;
                    LogicNode condition = ifNode.condition();
                    if (!loop.inside().contains(condition)) {
                        dominatedLoopInvariantConditions++;
                    }

                }
                cur = cur.getDominator();
            }
        }
        return dominatedLoopInvariantConditions;
    }

    @SuppressWarnings("try")
    private static boolean loopQualifiesForPeeling(Loop loop, StructuredGraph graph, ControlFlowGraph cfg) {
        if (!loop.canDuplicateLoop()) {
            return false;
        }
        boolean specialCases = false;
        double minRelativeFrequency = Options.PeelingConsideredMinRelativeFrequency.getValue(graph.getOptions());
        if (loop.detectCounted() && LoopUtility.isConstantLoopCount(loop, (long) minRelativeFrequency)) {
            specialCases = true;
        }
        if (!specialCases && LoopUtility.maybeSwitchWhenLoop(loop)) {
            specialCases = true;
        }
        // if we are dealing with one of the special case small loops only reason about the size
        // from here, see #isConstantLoopCount for details
        if (!specialCases) {
            if (ProfileSource.isTrusted(loop.localFrequencySource())) {
                if (loop.getCFGLoop().getHeader().getRelativeFrequency() < Options.PeelingConsideredMinRelativeFrequency.getValue(graph.getOptions())) {
                    PeelingsIgnored.increment(graph.getDebug());
                    return false;
                }
            }
            if (cfg.localLoopFrequency(loop.loopBegin()) < Options.PeelingConsideredMinLoopIterations.getValue(graph.getOptions())) {
                PeelingsIgnored.increment(graph.getDebug());
                return false;
            }
        }
        try (DebugCloseable dc = graphSizeCheck.start(graph.getDebug())) {
            if (NodeCostUtil.computeGraphSize(graph) > DuplicationOptions.MaxGraphSizeNodeCost.getValue(graph.getOptions())) {
                PeelingsIgnored.increment(graph.getDebug());
                return false;
            }
        }
        return true;
    }

    /**
     * The maximum frequency bonus to be used by the peeling benefit calculation.
     */
    private static final double MAX_FREQUENCY_BONUS = 8;

    // @formatter:off
    /**
     * Compute an (abstract) benefit of peeling {@code loop}.
     *
     * The benefit computation is twofold: when peeling a loop, we have independent optimization
     * opportunities in the peeled part of the loop and the remaining loop tail. The algorithm
     * simulates a peeling operation by assuming initial phi values for the body of the loop and
     * calculates a number of abstract saved cycles in the peeled part and then simulates the loop
     * body with phi input values from the peeled part and calculates an abstract number of
     * cycles saved for the remaining loop tail. Both parts have different frequencies after peeling
     * so the benefit is adjusted for that. We favor very high frequency loops thus we give the loop
     * tail an additional frequency bonus. This boils down to:
     *
     * <pre>
     * benefit = (abstractCyclesSavedInPeel * frequencyOfPeel) +
     *           (abstractCyclesSavedInTail * frequencyOfTail * frequencyBonus)
     * </pre>
     *
     * <pre>
     * // Original Loop
     * Object phi = null;
     * for (int i = 0; i < n; i++) {  // frequency = 1000
     *     if (phi == null) {
     *         SideEffect = 12;
     *     }
     *     phi = ConstantObject;
     * }
     * return phi;
     *
     * // after peeling
     * Object phi = null;
     * if (phi == null) {   // trivially true -> can be optimized
     *     SideEffect = 12;
     * }
     * phi = ConstantObject;
     * for (int i = 0; i < n; i++) {
     *     if (phi == null) { // phi now always constant object -> can be optimized
     *         SideEffect = 12;
     *     }
     *     phi = ConstantObject;
     * }
     * return phi;
     *
     * // after optimization
     * SideEffect = 12;
     * for (int i = 0; i < n; i++) { // loop frequency is now 1000 - 1 = 999
     *     // loop is now empty
     * }
     * return ConstantObject;
     * </pre>
     *
     * For this example the simulation heuristics deduce loop peeling most likely produces the
     * following outcome:
     *
     * Peeled head:
     * <pre>
     *   optimize the if statement away
     *   --> remove NullCheck + kill branch + remove loop counter addition
     *   --> {@link IsNullNode#estimatedNodeCycles() nullCheckCycles} + {@link SpecialBenefit#killsBranch} + {@link AddNode#estimatedNodeCycles() addCycles}
     *   --> 2 + 16 + 1
     *   --> 19
     * </pre>
     *
     *
     * Loop tail:
     * <pre>
     *   optimize the if statement away
     *   --> remove NullCheck + kill branch
     *   --> {@link IsNullNode#estimatedNodeCycles() nullCheckCycles} + {@link SpecialBenefit#killsBranch}
     *   --> 2 + 16
     *   --> 18
     * </pre>
     *
     * The loop frequency N is 1000, the normalized peelFraction is 0.001 and the normalized tailFraction is 0.999
     * and the frequency bonus for the tail is 1+log10(N) which is 4. Based on this, we calculate:
     *
     * <pre>
     * benefitPeel = peelCyclesSaved * peelFraction
     *             = 19 * 0.001
     *             = 0.019
     *
     * benefitTail = tailCyclesSaved * tailFraction
     *             = 18 * 0.999
     *             = 17.982
     *
     * benefit = benefitPeel * benefitTail * tailBonus
     *         = 0.019 + 17.982 * 4
     *         = 71.947
     */
    // @formatter:on
    private static double computeBenefit(Loop loop, PeelingBenefit simulationBenefit, ControlFlowGraph cfg, CoreProviders providers) {
        double cummulativeBenefit = 0;
        StructuredGraph graph = cfg.graph;
        LoopBeginNode lb = loop.loopBegin();

        int dominatedLoopInvariantConditions = computeDominatedInvariantConditionsAfterPeeling(cfg, loop);

        boolean potentialVectorLoop = LoopUtility.potentialVectorLoop(loop, graph, providers);

        String ignoreVectCheck = Options.PeelingIgnoreVectorizationCheck.getValue(graph.getOptions());
        boolean ignoreVectorCheck = ignoreVectCheck != null && MethodFilter.parse(ignoreVectCheck).matches(graph.method());

        if (!ignoreVectorCheck && potentialVectorLoop && simulationBenefit.cyclesSavedLoopRest == 0 && LoopVectorizationAnalysis.peelingMayHarmVectorization(loop)) {
            graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Not peeling loop %s in method %s because loop might be vectorizable", lb, graph);
            VectorizablePeelingsIgnored.increment(graph.getDebug());
            return -1;
        }
        if (simulationBenefit.cyclesSavedPeeledIteration > 0) {
            FoldingsPeeled.increment(graph.getDebug());
        }
        if (simulationBenefit.cyclesSavedLoopRest > 0) {
            FoldingRest.increment(graph.getDebug());
        }
        final double loopFrequency = loop.localLoopFrequency();

        /*
         * Calculate frequency for the peeled fraction
         */
        final double peelFraction = 1.0 / loopFrequency;
        assert NumUtil.assertPositiveDouble(peelFraction);

        /*
         * Calculate frequency for the loop fraction
         */
        double loopFraction = loopFrequency > 1.0 ? 1.0 - peelFraction : 0.0;
        assert NumUtil.assertPositiveDouble(loopFraction);

        /*
         * Give a bonus to low frequency loops in high frequency regions.
         */
        double outerFrequencyBonus = computeOuterFrequencyBonus(lb, loopFrequency, cfg);
        assert NumUtil.assertPositiveDouble(outerFrequencyBonus);

        /*
         * Give a good bonus to loops with high probability
         */
        // Example:
        // FrequencyBonusConstant = 32, Real Frequency = 10000
        // frequencyBonus = 1 + max(0,min(32,log(1000)))
        // = 1 + max(0,min(32,3))
        // = 1 + max(0,3)
        // = 1 + 3 = 3
        double loopFrequencyBonus = 1 + Math.max(0, Math.min(MAX_FREQUENCY_BONUS, Math.log10(loopFrequency)));
        assert NumUtil.assertPositiveDouble(loopFrequencyBonus);

        // the benefit in cycles saved in the peeled fraction of the loop has a different frequency
        // than the rest of the loop
        cummulativeBenefit += simulationBenefit.cyclesSavedPeeledIteration * peelFraction * outerFrequencyBonus;
        // the benefit of the rest of the loop becomes the frequency bonus
        cummulativeBenefit += simulationBenefit.cyclesSavedLoopRest * loopFraction * loopFrequencyBonus;
        // and we additional benefit from conditions dominating the loop
        cummulativeBenefit += dominatedLoopInvariantConditions * loopFraction * loopFrequencyBonus;
        assert NumUtil.assertPositiveDouble(cummulativeBenefit);
        return cummulativeBenefit;
    }

    /**
     * Give a bonus to peeling loops with low frequency in high probability program regions. For
     * example:
     *
     * <pre>
     * outerLimit = 1_000_000;
     * innerLimit = 2;
     * for (i = 0; i < outerLimit; i++) {
     *     for (j = 0; j < innerLimit; j++) {
     *         someOperation;
     *         safepoint;
     *     }
     * }
     * </pre>
     *
     * The safepoint will be executed 2 million times. If we peel the inner loop, the peeled
     * iteration won't have the safepoint. Overall we will still execute the operation 2 million
     * times but eliminate 1 million safepoint executions. More generally, we will get any peeled
     * iteration cycle benefit 1 million times.
     * </p>
     *
     * The bonus is logarithmic in the ratio of the surrounding region's frequency and the loop
     * iterations, so we only add a significant bonus if the loop frequency is very low in relation
     * to its surroundings. Examples (each with 2 million executions of the inner loop body before
     * peeling):
     *
     * <pre>
     * outerLimit  innerLimit  log10(outer/inner)  bonus
     *  1_000_000           2                 5.7    6.7
     *      2_000       1_000                 0.3    1.3
     *      1_000       2_000                -0.3    1.0
     *          2   1_000_000                -5.7    1.0
     * </pre>
     */
    private static double computeOuterFrequencyBonus(LoopBeginNode lb, double loopFrequency, ControlFlowGraph cfg) {
        double outerFrequency = cfg.blockFor(lb.forwardEnd()).getRelativeFrequency();
        double outerFrequencyBonus = 1 + Math.max(0, Math.min(MAX_FREQUENCY_BONUS, Math.log10(outerFrequency / loopFrequency)));
        lb.graph().getDebug().log(DebugContext.DETAILED_LEVEL, "Computed outer frequency bonus %s for %s (outer frequency %s, loop frequency %s)", outerFrequencyBonus, lb, outerFrequency,
                        loopFrequency);
        return outerFrequencyBonus;
    }

    private static int computeGraphGrowthForLoop(Loop loop) {
        int growthSize = 0;
        for (Node n : loop.inside().nodes()) {
            growthSize += n.estimatedNodeSize().value;
        }
        if (loop.parent() != null) {
            int loopSize = NodeCostUtil.computeNodesSize(loop.inside().nodes());
            int parentSize = NodeCostUtil.computeNodesSize(loop.parent().inside().nodes());
            if (loop.parent().loopBegin().peelings() > 0) {
                if (loopSize * 2 > parentSize) {
                    /*
                     * The parent loop was already peeled, but we are still inside the parent loop,
                     * i.e., not the peeled version of this loop in the parent loop's peeled body,
                     * thus we need a very high benefit to peel inside again. This loops make up
                     * more than half of the parent loop in terms of number of nodes. There is a
                     * risk that this loop disproportionately increases parent loop body size.
                     */
                    growthSize *= 2;
                }
            }
        }
        return growthSize;
    }

    private static final double MIN_BENEFIT = 0.1D;

    /**
     * Determine if the calculated benefit of this loop makes it a candidate for loop peeling.
     */
    private static boolean benefitIsCandidateForPeeling(Loop loop, double benefit) {
        LoopBeginNode lb = loop.loopBegin();
        StructuredGraph graph = lb.graph();
        if (benefit < MIN_BENEFIT) {
            graph.getDebug().log(DebugContext.DETAILED_LEVEL,
                            "Not peeling loop %s in method %s because benefit is too small %f", lb, graph, benefit);
            PeelingsIgnored.increment(graph.getDebug());
            return false;
        }
        return true;
    }

    @SuppressWarnings("try")
    public static boolean shouldPeel(LoopPeelingFactors factors, Loop loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration) {
        LoopBeginNode lb = loop.loopBegin();
        StructuredGraph graph = lb.graph();

        // Determine if the loop is eligible for peeling based on frequency info and graph sizes
        if (!loopQualifiesForPeeling(loop, graph, cfg)) {
            return false;
        }

        // Determine if the loop is eligible for iterative peeling based on the outer frequency
        // bonus.
        if (peelingIteration > 0) {
            double outerFrequencyBonus = computeOuterFrequencyBonus(lb, loop.localLoopFrequency(), cfg);
            double threshold = Options.IterativePeelingOuterFrequencyBonusThreshold.getValue(graph.getOptions());
            if (outerFrequencyBonus < threshold) {
                return false;
            }
        }

        // compute the benefit of peeling based on simulating a peeling and optimizing the
        // simulation result
        final PeelingBenefit simulationBenefit = computePeelingBenefit(loop, cfg, providers, factors);
        // compute static benefit factors for peeling
        final double benefit = computeBenefit(loop, simulationBenefit, cfg, providers);

        // if the benefit is too low we avoid peeling
        if (!benefitIsCandidateForPeeling(loop, benefit)) {
            return false;
        }

        // compute the size in NodeSize (using the node cost model) the graph size will be increased
        // if this loop is peeled
        final double growthSize = computeGraphGrowthForLoop(loop);

        // trade-off the estimated benefit vs cost in peeling this loop to make a final decision
        boolean peel = tradeOffCostBenefit(graph, growthSize, benefit, factors);

        logPeelingDecision(graph, simulationBenefit, peel, growthSize, benefit, lb, loop.localLoopFrequency());

        if (peel) {
            return true;
        } else {
            PeelingsIgnored.increment(graph.getDebug());
            return false;
        }
    }

    /**
     * Trade-off the cost vs the benefit in peeling this particular loop. Benefit is calculated
     * using the {@link NodeCycles} (the node cost model) and expresses a combined estimate in saved
     * cycles in the peeled fraction of the loop and saved cycles in the loop fraction of the loop.
     * Size is calculated using the {@link Node size} (node cost model) and expresses the estimated
     * number of instructions (NodeSize) added to the graph when peeling this loop.
     * </p>
     *
     * Benefit and cost can be largely different numerical values, however, we want to express the
     * following when reasoning about the effect of peeling a loop: For small loops peeling should
     * be effortless, i.e., a small benefit should be enough to peel a loop and incur a small code
     * size increase, however for (very) large loops the benefit must be considerably high compared
     * to the cost in order to consider peeling a loop. Therefore, we use a so called
     * costReductionFactor {@link LoopPeelingFactors#costReductionFactor}, i.e., a factor that is
     * used to decrease the cost of a loop if the loop is very small. For loops having a size >= the
     * TrivialLoopLimitSize the cost reduction factor is set to 1 and effectively disabled.
     */
    private static boolean tradeOffCostBenefit(StructuredGraph graph, double growthSize, double benefit, LoopPeelingFactors factors) {
        PossiblePeelings.increment(graph.getDebug());
        final double trivialLoopSizeLimit = Options.TrivialLoopSizeLimitForPeeling.getValue(graph.getOptions());
        /*
         *
         * The cost reduction factor is a static option and is adjusted based on a loop's size. We
         * normalize the desired loop size to an interval [0,1] and normalize the size of a loop
         * into that interval and the size value of the loop relative to the desired size
         * (normalized to [0,1]) is used to decrease the cost reduction factor.
         *
         */
        // desiredSize - Math.min(size,desiredSize) / desiredSize
        // Example 1 desired = 100
        // Size = 50
        // 100 - Math.min(50,100) / 100 = (100-50)/100 = 0.5
        //
        // Example 2 desired = 100
        // Size = 101
        // 100 - Math.min(101,100) / 100 = (100-100)/100 = 0 / 100 = 0
        double sizePressure = (trivialLoopSizeLimit - Math.min(trivialLoopSizeLimit, growthSize)) / trivialLoopSizeLimit;

        // Make the initial maximum cost reduction smaller by the size factor of the loop
        //
        //
        // Example 1 from above: ReductionConstant=10 - Pressure = 0.5
        // --> costReduction = max(1,10 * 0.5) = max(1,5)=5
        //
        // Example 2 from above: ReductionConstant=10 - Pressure = 0
        // --> costReduction = max(1,10*0) = max(1,0) = 1
        //
        double costReductionFactor = Math.max(1, factors.costReductionFactor * sizePressure);

        // Reduce the cost by the factor linear to its size (determined by the desired loop size)
        final double cost = growthSize / costReductionFactor;
        assert NumUtil.assertPositiveDouble(cost) : "abcd";
        assert NumUtil.assertPositiveDouble(benefit);

        // Final cost vs benefit trade-off to decide if the loop should be peeled
        return benefit >= cost;
    }

    private static void logPeelingDecision(StructuredGraph graph, PeelingBenefit simulationBenefit, boolean peel, double growthSize, double benefit, @SuppressWarnings("unused") LoopBeginNode lb,
                    double frequency) {
        if (Options.DebugPeelingSynonyms.getValue(graph.getOptions())) {
            graph.getDebug().log(DebugContext.DETAILED_LEVEL,
                            "%s loop %s because of peeled benefit %d and loop benefit %d code size increase %f [peeled synonyms=%s] [rest synonyms=%s] [benefit=%f:cost=%f] frequency=%f%n", graph,
                            peel ? "Peeling" : "Not peeling", simulationBenefit.cyclesSavedPeeledIteration, simulationBenefit.cyclesSavedLoopRest, growthSize,
                            simulationBenefit.optimizedPeeledIteration,
                            simulationBenefit.optimizedLoopIteration, benefit, growthSize, frequency);
        }
    }

    public static class LoopOptimizationBenefit {
        final int cyclesSavedLoopRest;
        /**
         * List of optimization candidates: null in production.
         */
        NodeMap<Node> optimizedLoopIteration;

        LoopOptimizationBenefit(OptionValues options, int cyclesSavedLoopRest, NodeMap<Node> optimizedLoopIteration) {
            super();
            this.cyclesSavedLoopRest = cyclesSavedLoopRest;
            if (options != null && Options.DebugPeelingSynonyms.getValue(options)) {
                this.optimizedLoopIteration = optimizedLoopIteration;
            }
        }

        public int cyclesSavedLoopRest() {
            return cyclesSavedLoopRest;
        }

        @Override
        public String toString() {
            return "Loop Benefit: Cycles Saved loop rest=" + cyclesSavedLoopRest;
        }

    }

    public static class PeelingBenefit extends LoopOptimizationBenefit {
        final int cyclesSavedPeeledIteration;
        /**
         * List of optimization candidates: null in production.
         */
        NodeMap<Node> optimizedPeeledIteration;

        PeelingBenefit(OptionValues options, int cyclesSavedPeeledIteration, int cyclesSavedLoopRest, NodeMap<Node> optimizedPeeledIteration, NodeMap<Node> optimizedLoopIteration) {
            super(options, cyclesSavedLoopRest, optimizedLoopIteration);
            this.cyclesSavedPeeledIteration = cyclesSavedPeeledIteration;
            if (options != null && Options.DebugPeelingSynonyms.getValue(options)) {
                this.optimizedPeeledIteration = optimizedPeeledIteration;
            }
        }

        @Override
        public String toString() {
            return "Peeling Benefit: Cycles Saved Peeled Iteration=" + cyclesSavedPeeledIteration + " cycles saved loop rest=" + cyclesSavedLoopRest;
        }
    }

    static final PeelingBenefit NoBenefit = new PeelingBenefit(null, 0, 0, null, null);

    private static final TimerKey timeFirstSimulation = DebugContext.timer("Time_Peeling_Simulation_First");
    private static final TimerKey timeSecondSimulation = DebugContext.timer("Time_Peeling_Simulation_Second");

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static class DummyNullValueNode extends FloatingNode implements GuardingNode, Canonicalizable {
        public static final NodeClass<DummyNullValueNode> TYPE = NodeClass.create(DummyNullValueNode.class);

        protected DummyNullValueNode(Stamp stamp) {
            super(TYPE, stamp);
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // delete this dummy node again
            return null;
        }

    }

    public static boolean willBecomeLoopInvariantAfterFloatingReads(Node node, Loop loop, NodeBitMap dominatingInvariantNodes, boolean expectSpeculativeGuardMovement) {
        if (isLoopInvariant(node, loop)) {
            return true;
        }
        if (node instanceof PhiNode && loop.loopBegin().phis().contains((PhiNode) node)) {
            return false;
        }
        /*
         * We expect field loads to float if they don't depend on anything that won't float out of
         * the loop. We also expect guards to float through speculative guard movement if they only
         * depend on nodes we expect to float.
         */
        boolean isPossiblyInvariantLoad = node instanceof LoadFieldNode || node instanceof GuardedUnsafeLoadNode || node instanceof LoadIndexedNode ||
                        (node instanceof FloatableThreadLocalAccess && ((FloatableThreadLocalAccess) node).canFloat());
        boolean isPossiblyInvariantGuard = expectSpeculativeGuardMovement && (node instanceof PiNode || node instanceof PiArrayNode || node instanceof LogicNode || node instanceof FixedGuardNode);
        if (isPossiblyInvariantLoad || isPossiblyInvariantGuard) {
            if (node instanceof LoadFieldNode && ((LoadFieldNode) node).isStatic()) {
                // will float
                return true;
            }
            for (Node input : node.inputs()) {
                if (!(dominatingInvariantNodes.contains(input) || willBecomeLoopInvariantAfterFloatingReads(input, loop, dominatingInvariantNodes, expectSpeculativeGuardMovement))) {
                    return false;
                }
            }
            return true;
        }
        return !isLoopVariant(node, loop);
    }

    public static boolean isLoopInvariant(Node node, Loop loop) {
        return loop.isOutsideLoop(node);
    }

    public static boolean isLoopVariant(Node node, Loop loop) {
        return !isLoopInvariant(node, loop);
    }

    /**
     * Compute an abstract benefit (in {@linkplain NodeCycles}) that can be achieved by
     * optimizations if the supplied loop is unrolled. This method will look for
     * {@linkplain Canonicalizable} nodes and read eliminations
     * {@linkplain ReadEliminationPhase}. It only looks at unrolling one iteration.
     */
    public static LoopOptimizationBenefit computeBenefitUnrolling(Loop loop, ControlFlowGraph cfg, CoreProviders providers) {
        loop.detectCounted();
        StructuredGraph graph = cfg.graph;
        CanonicalizerTool defaultSimplifier = GraphUtil.getDefaultSimplifier(providers, false, graph.getAssumptions(), graph.getOptions());

        /*
         * Filter out memory related nodes that will float out of the loop once we apply
         * FloatingReadsPhase. Marking them as opportunities would be wrong.
         */
        boolean expectFloatingReads = graph.isBeforeStage(StageFlag.FLOATING_READS) && GraalOptions.OptFloatingReads.getValue(graph.getOptions());
        boolean expectSpeculativeGuardMovement = graph.isBeforeStage(StageFlag.GUARD_MOVEMENT) && GraalOptions.SpeculativeGuardMovement.getValue(graph.getOptions());
        NodeBitMap invariantReads = graph.createNodeBitMap();
        EconomicSet<LocationIdentity> killedIdentities = EconomicSet.create();
        for (Node n : loop.inside().nodes()) {
            if (expectFloatingReads && willBecomeLoopInvariantAfterFloatingReads(n, loop, invariantReads, expectSpeculativeGuardMovement)) {
                invariantReads.checkAndMarkInc(n);
            } else if (MemoryKill.isSingleMemoryKill(n)) {
                LocationIdentity identity = ((SingleMemoryKill) n).getKilledLocationIdentity();
                killedIdentities.add(identity);
            } else if (MemoryKill.isMultiMemoryKill(n)) {
                for (LocationIdentity identity : ((MultiMemoryKill) n).getKilledLocationIdentities()) {
                    killedIdentities.add(identity);
                }
            } else {
                assert !(n instanceof StoreFieldNode) && !(n instanceof WriteNode) && !(n instanceof StoreIndexedNode) : "node " + n + " should have been handled as a memory kill";
            }
        }
        for (Node n : invariantReads.snapshot()) {
            if (n instanceof LoadFieldNode) {
                for (LocationIdentity loc : killedIdentities) {
                    if (loc.overlaps(new FieldLocationIdentity(((LoadFieldNode) n).field()))) {
                        invariantReads.clear(n);
                    }
                }
            } else if (n instanceof LoadIndexedNode) {
                for (LocationIdentity loc : killedIdentities) {
                    if (loc.overlaps(NamedLocationIdentity.getArrayLocation(((LoadIndexedNode) n).elementKind()))) {
                        invariantReads.clear(n);
                    }
                }
            }
        }

        /*
         * While AggressivePartialUnrollPhase can only unroll loops with a single LoopEndNode it
         * still poses questions on the simulation logic here with loops with multiple ends. This is
         * done to answer the question if a loop should be unrolled without modifying it. To avoid
         * merging loop ends of loops that will anyway not be unrolled.
         *
         * In order to simulate a loop unrolling along the single loop end we create artificial phis
         * for simulation.
         *
         * Consider the following loop
         *
         * <pre>
         * phi = entryVal;
         * while (true) {
         *     if (sth) {
         *         // sth more
         *         phi = LoopEndVal1;
         *         continue;
         *     }
         *     if (sthElse) {
         *         // sth else more
         *         phi = LoopEndVal2;
         *         continue;
         *     }
         *     break;
         * }
         * </pre>
         *
         * The loop has 2 loop ends. In order to understand the value of each phi along the
         * unrolling path (a single loop end) we would normally merge loop ends before unrolling to
         * get a loop like this:
         *
         * <pre>
         * phi = entryVal;
         * loop: while (true) {
         *     phi commonPhi;
         *     commonBlock: {
         *         if (sth) {
         *             // sth more
         *             commonPhi = LoopEndVal1;
         *             break commonBlock;
         *         }
         *         if (sthElse) {
         *             // sth else more
         *             commonPhi = LoopEndVal2;
         *             break commonBlock;
         *         } else {
         *             break loop;
         *         }
         *     }
         *     phi = commonPhi;
         *     continue loop;
         * }
         * </pre>
         *
         * The artificial phis created by the simulation in this example would be {@code commonPhi}.
         *
         * We simulate adding a phi on the merged loop ends, but since we have no merge node yet,
         * add it on the loop begin. The phi's inputs will never be accessed.
         */
        List<ValuePhiNode> originalPhis = loop.loopBegin().valuePhis().snapshot();
        final boolean useCombinedBackEdge = loop.loopBegin().loopEnds().count() > 1;
        EconomicMap<PhiNode, PhiNode> old2New = null;
        if (useCombinedBackEdge) {
            LoopBeginNode lb = loop.loopBegin();
            old2New = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            for (PhiNode phi : lb.phis().snapshot()) {
                PhiNode copy = phi.duplicateOn(lb);
                for (LoopEndNode le : lb.loopEnds()) {
                    copy.addInput(phi.valueAt(le));
                }
                phi.inferStamp();
                old2New.put(phi, copy);
            }
        }

        NodeMap<Node> synonyms = new NodeMap<>(graph);
        ReadEliminationBlockState state = new ReadEliminationBlockState();
        simulateIteration(loop, false, cfg, synonyms, state, defaultSimplifier, invariantReads);

        // put synonyms for backedge values & for the next iteration values
        for (ValuePhiNode vp : originalPhis) {
            ValueNode backEdgeValue = null;
            if (useCombinedBackEdge) {
                backEdgeValue = old2New.get(vp);
                assert backEdgeValue != null;
            } else {
                backEdgeValue = vp.valueAt(loop.loopBegin().getSingleLoopEnd());
            }

            synonyms.put(vp, backEdgeValue);
            boolean allUsagesOutsideLoop = true;
            for (Node usage : vp.usages()) {
                if (usage instanceof VirtualState) {
                    continue;
                }
                if (usage instanceof ValueProxyNode && ((ValueProxyNode) usage).proxyPoint().loopBegin() == loop.loopBegin()) {
                    continue;
                }
                if (!loop.isOutsideLoop(usage)) {
                    allUsagesOutsideLoop = false;
                    break;
                }
            }
            if (allUsagesOutsideLoop) {
                /**
                 * This phi's value is only used outside the loop. The next iteration will not see
                 * its value. But the next iteration may use the backEdgeValue inside the iteration,
                 * and there is no reason to overwrite that with an invalid placeholder synonym.
                 */
            } else {
                // we would replace here the input and canonicalize through the value but that is
                // not necessary, the identity of the node is important
                ValueNode secondIterationValue = new DummyNullValueNode(backEdgeValue.stamp(NodeView.DEFAULT));
                synonyms.put(backEdgeValue, secondIterationValue);
            }
        }

        int cyclesSavedSecondIteration = simulateIteration(loop, false, cfg, synonyms, state, defaultSimplifier, invariantReads);

        NodeBitMap optimizableMonitorOps = LoopUtility.benefitLockCoarsening(loop);
        if (optimizableMonitorOps != null) {
            for (Node n : optimizableMonitorOps) {
                cyclesSavedSecondIteration += n.estimatedNodeCycles().value;
            }
        }

        if (useCombinedBackEdge) {
            for (PhiNode phi : old2New.getValues()) {
                phi.safeDelete();
            }
        }

        /*
         * Check for Partial Escape Analysis opportunities: E.g. a phi that only escapes on the loop
         * end
         */
        NodeIterable<PhiNode> loopPhis = loop.loopBegin().phis();
        outer: for (PhiNode phi : loopPhis) {
            for (int i = 1; i < phi.valueCount(); i++) {
                ValueNode value = phi.valueAt(i);
                if (!(value instanceof VirtualizableAllocation || value instanceof AllocatedObjectNode)) {
                    continue outer;
                }
                for (Node usage : value.usages()) {
                    if (loop.loopBegin().isPhiAtMerge(usage) || usage instanceof VirtualState) {
                        continue;
                    }
                    if (!(usage instanceof VirtualizableAllocation)) {
                        continue outer;
                    }
                }
                /*
                 * the backedge value of the loop is an allocation with only virtualizable usages or
                 * the phi, thus we can push down the allocation when unrolling this loop
                 */
                cyclesSavedSecondIteration += AbstractNewObjectNode.TYPE.cycles().value;
            }
        }

        if (cyclesSavedSecondIteration > 0) {
            return new LoopOptimizationBenefit(graph.getOptions(), cyclesSavedSecondIteration, synonyms);
        }
        return NoBenefit;
    }

    /**
     * Compute an abstract benefit of peeling this particular loop by simulating how the code inside
     * (and the peeled fraction) changes after peeling.
     * </p>
     *
     * The general algorithm works by computing the replacements of the loop phis for the peeled
     * fraction (i.e. the forward inputs of the phis) and the loop fraction (using a combined node
     * for all backedges of the loop merged in the peeled iteration).
     * </p>
     *
     * In the mid tier we also try to simulate the costs of any safepoints needed by the loop. The
     * peeled iteration will not need to execute safepoints, which can give it an extra benefit
     * compared to the loop.
     *
     * @param loop the loop for which a peeling operation should be simulated
     * @param cfg the cfg of the function
     * @param providers
     * @return a new {@linkplain LoopOptimizationBenefit} if there was a benefit or
     *         {@linkplain SimulationBasedLoopPeeling#NoBenefit} if there was no benefit
     */
    @SuppressWarnings("try")
    private static PeelingBenefit computePeelingBenefit(Loop loop, ControlFlowGraph cfg, CoreProviders providers, LoopPeelingFactors factors) {
        loop.detectCounted();
        StructuredGraph graph = cfg.graph;
        LoopBeginNode lb = loop.loopBegin();
        CanonicalizerTool defaultSimplifier = GraphUtil.getDefaultSimplifier(providers, false, graph.getAssumptions(), graph.getOptions());

        NodeMap<Node> synonymsPeeledIteration = computeSynonymsPeeledIteration(lb);

        /*
         * There might be read eliminations enabled be peeling one loop. Therefore we re-use the
         * read elimination state during simulation along the peeled iteration for the loop
         * iteration.
         */
        ReadEliminationBlockState state = new ReadEliminationBlockState();
        int cyclesSavedPeeledIteration = 0;

        try (DebugCloseable dc = timeFirstSimulation.start(graph.getDebug())) {
            cyclesSavedPeeledIteration = simulateIteration(loop, false, cfg, synonymsPeeledIteration, state, defaultSimplifier, null);
        }

        /*
         * We need the synonyms of the peeled iteration to compute the phi stamps for the loop rest
         */
        NodeMap<Node> synonymsLoopRest = computeSynonymsLoopRest(loop, lb, synonymsPeeledIteration);

        int cyclesSavedLoopRest = 0;
        try (DebugCloseable dc = timeSecondSimulation.start(graph.getDebug())) {
            cyclesSavedLoopRest = simulateIteration(loop, true, cfg, synonymsLoopRest, state, defaultSimplifier, null);
        }

        if (factors.considerPEA) {
            /*
             * Check for Partial Escape Analysis opportunities: E.g. a phi that only escapes on the
             * loop end
             */
            NodeIterable<PhiNode> loopPhis = lb.phis();
            outer: for (PhiNode phi : loopPhis) {
                ValueNode initialValue = phi.valueAt(0);
                if (!(initialValue instanceof VirtualizableAllocation || initialValue instanceof AllocatedObjectNode)) {
                    for (int i = 1; i < phi.valueCount(); i++) {
                        ValueNode value = phi.valueAt(i);
                        if (!(value instanceof VirtualizableAllocation || value instanceof AllocatedObjectNode)) {
                            continue outer;
                        }
                    }
                    // the initial value is not an allocation, while all others are: peeling is
                    // highly beneficial, this means we can move an allocation outside of a loop
                    cyclesSavedLoopRest += AbstractNewObjectNode.TYPE.cycles().value;
                }
            }
        }

        if (graph.isAfterStage(StageFlag.GUARD_MOVEMENT) && graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            /*
             * We are after safepoint elimination, so the loop begin's safepoint flags give precise
             * information on whether safepoints are needed. We are also before safepoint insertion,
             * so any needed safepoints aren't actually in the graph yet. Use the flags to simulate
             * the cycle cost of the expected safepoints.
             */
            if (lb.canEndsSafepoint()) {
                cyclesSavedPeeledIteration += SafepointNode.TYPE.cycles().value;
            }
            if (lb.canEndsGuestSafepoint()) {
                cyclesSavedPeeledIteration += TruffleSafepointNode.TYPE.cycles().value;
            }
        }

        if (cyclesSavedPeeledIteration > 0 || cyclesSavedLoopRest > 0) {
            return new PeelingBenefit(graph.getOptions(), cyclesSavedPeeledIteration, cyclesSavedLoopRest, synonymsPeeledIteration, synonymsLoopRest);
        }
        return NoBenefit;
    }

    /**
     * Compute the set of loop phi inputs for the peeled fraction of the loop.
     */
    private static NodeMap<Node> computeSynonymsPeeledIteration(LoopBeginNode lb) {
        /*
         * Synonyms for the peeled iteration of a loop are the forward end predecessor phi inputs.
         */
        NodeMap<Node> synonymsPeeledIteration = new NodeMap<>(lb.graph());
        // propagate 1st iteration phi
        for (PhiNode phi : lb.phis()) {
            synonymsPeeledIteration.put(phi, phi.valueAt(lb.forwardEnd()));
        }
        return synonymsPeeledIteration;
    }

    /**
     * Compute the set of loop phi inputs for the loop fraction of the loop (i.e. one node combining
     * all loop phi inputs except the forward input).
     */
    private static NodeMap<Node> computeSynonymsLoopRest(Loop ex, LoopBeginNode lb, NodeMap<Node> synonymsPeeledIteration) {
        /*
         * Synonyms for the rest of a peeled loop are different then for the peeled iteration since
         * all loop ends are merged so the synonym for the loop rest portion phis is a node with the
         * stamp of all phi inputs. forward phi inputs may already be improved via peeling, i.e.,
         * the simulation may give better stamps for those values
         *
         */
        NodeMap<Node> synonymsLoopRest = new NodeMap<>(lb.graph());

        /*
         * For each phi we have to do the following: create a node with a stamp which is the union
         * of all inputs, i.e., all loop end inputs and the forward input, where the phi forward
         * predecessor input may already be improved via simulation, i.e., we have a synonym for it.
         */
        for (PhiNode phi : lb.valuePhis()) {
            Stamp synonymStamp = null;
            /*
             * Counted loops: We check if we can get an actual better stamp for the rest of the
             * iterations by reducing the value range of the loop counter (see
             * InjectLoopCounterStamps for details).
             */
            ValueNode v = (lb.getLoopEndCount() == 1 || phi.singleBackValueOrThis() != phi) ? (ValueNode) synonymsPeeledIteration.get(phi.valueAt(lb.loopEnds().first())) : null;
            if (ex.isCounted() && v != null) {
                CountedLoopInfo counted = ex.counted();
                InductionVariable counter = counted.getLimitCheckedIV();
                ValueNode counterNode = counter.valueNode();
                if (counterNode instanceof ValuePhiNode && counter.isConstantStride() && counted.counterNeverOverflows()) {
                    if (counterNode == phi) {
                        ValueNode extremumNode = counter.extremumNode();
                        ValueNode initNode = counter.initNode();
                        extremumNode.inferStamp();
                        initNode.inferStamp();
                        IntegerStamp extremumStamp = (IntegerStamp) extremumNode.stamp(NodeView.DEFAULT);
                        IntegerStamp initStamp = (IntegerStamp) v.stamp(NodeView.DEFAULT);
                        IntegerStamp originalStamp = (IntegerStamp) phi.stamp(NodeView.DEFAULT);
                        Stamp stamp = InjectLoopCounterStampsPhase.betterLoopCounterStamp(counted, counter, initStamp, extremumStamp, originalStamp);
                        if (stamp != null) {
                            synonymStamp = stamp;
                        }
                    }
                }
            }
            /*
             * We have not been able to create a better stamp for the loop counter phi, therefore we
             * combine all loop end phi inputs and the forward predecessor input (which may already
             * be improved via a synonym) to one stamp.
             *
             * Normally a loop phi stamp is the union of the forward predecessor input and all
             * backedge inputs. However, for the loop rest after peeling, the forward predecessor
             * inputs are replaced by the loop end inputs merged in the first iteration, i.e.,
             * synonyms and the new backedge values.
             *
             * Thus, we add all backedge values to the new phi stamp set (which are anyway always
             * part of the set since the loop survives and has backedges), but we also have the
             * forward values from the simulated peeled iteration, we also add them to the set since
             * they may improve our stamp set
             */
            if (synonymStamp == null) {
                EconomicSet<ValueNode> valuesPeeledIterationForwardInputsLoopIteration = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
                EconomicSet<ValueNode> valuesLoopIterationLoopEndInputsLoopIteration = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

                // compute new forward input stamps, i.e., a combination of all simulated peeled
                // backedge values
                for (LoopEndNode len : lb.loopEnds()) {
                    ValueNode phiInput = phi.valueAt(len);
                    ValueNode syn = (ValueNode) synonymsPeeledIteration.get(phiInput);
                    if (syn != null) {
                        /*
                         * Better value from the peeled iteration for the forward inputs (which are
                         * now
                         */
                        valuesPeeledIterationForwardInputsLoopIteration.add(syn);
                    }
                }

                // compute the backedge input stamp, i.e., a combination of all regular backedge
                // values
                for (LoopEndNode len : lb.loopEnds()) {
                    ValueNode phiInput = phi.valueAt(len);
                    valuesLoopIterationLoopEndInputsLoopIteration.add(phiInput);
                }

                // combine them for the new loop phi stamp
                valuesLoopIterationLoopEndInputsLoopIteration.addAll(valuesPeeledIterationForwardInputsLoopIteration);

                if (valuesLoopIterationLoopEndInputsLoopIteration.size() == 1) {
                    // All phi inputs after peeling reduce to one node, take this node as a synonym
                    synonymsLoopRest.put(phi, valuesLoopIterationLoopEndInputsLoopIteration.iterator().next());
                } else {
                    synonymStamp = StampTool.meet(valuesLoopIterationLoopEndInputsLoopIteration);
                    if (synonymStamp == null) {
                        synonymStamp = phi.stamp(NodeView.DEFAULT);
                    }
                    synonymsLoopRest.put(phi, new StampPlaceholderNode(synonymStamp, phi.estimatedNodeSize(), phi.estimatedNodeCycles()));
                }
            } else {
                synonymsLoopRest.put(phi, new StampPlaceholderNode(synonymStamp, phi.estimatedNodeSize(), phi.estimatedNodeCycles()));
            }

        }
        return synonymsLoopRest;
    }

    /**
     * Simulate one iteration of the loop with the synonyms registered. For each node try to
     * optimize it (by canonicalization, read elimination or folding its stamp).
     *
     * @param loop the loop to simulation
     * @param inLoop a flag determining if this iteration is considered to be in a loop (i.e. phis
     *            survive)
     * @param cfg the cfg of the function
     * @param synonymsIteration the synonyms registered for this iteration (replacement nodes for
     *            phis)
     * @param state the read elimination state for this loop
     * @param defaultSimplifier
     * @return an abstract estimate of how many cycles are saved by replacing the nodes in this loop
     *         with the initial set of synonyms supplied
     */
    private static int simulateIteration(Loop loop, boolean inLoop, ControlFlowGraph cfg, NodeMap<Node> synonymsIteration, ReadEliminationBlockState state, CanonicalizerTool defaultSimplifier,
                    NodeBitMap nodesToSkip) {
        int cyclesSaved = 0;
        NodeMap<Node> readEliminationAliases = new NodeMap<>(loop.loopBegin().graph());
        NodeBitMap visited = loop.loopBegin().graph().createNodeBitMap();

        /*
         * Special case fixed and floating nodes: To simulate read elimination inside the loop we
         * follow fixed nodes in reverse post order to have a correct dominance relation and then we
         * process the (not visited) floating nodes after.
         */
        SpecialBenefit ben = new SpecialBenefit();
        List<HIRBlock> loopBlocks = loop.getCFGLoop().getBlocks();
        for (HIRBlock b : cfg.reversePostOrder()) {
            if (loopBlocks.contains(b)) {
                FixedNode cur = b.getBeginNode();
                while (true) { // TERMINATION ARGUMENT: process fixed nodes of a basic block
                    CompilationAlarm.checkProgress(cfg.graph);
                    if (nodesToSkip == null || !nodesToSkip.isMarkedAndGrow(cur)) {
                        processNode(cur, inLoop, loop, state, readEliminationAliases, synonymsIteration, defaultSimplifier, ben);
                    }
                    if (synonymsIteration.get(cur) != null) {
                        // if we found something, re-iterate the usages of the node inside the loop
                        // to capture transitive improvements
                        for (Node usage : cur.usages()) {
                            if (loop.inside().contains(usage)) {
                                visited.mark(usage);
                                if (nodesToSkip == null || !nodesToSkip.isMarkedAndGrow(usage)) {
                                    processNode(usage, inLoop, loop, state, readEliminationAliases, synonymsIteration, defaultSimplifier, ben);
                                }
                            }
                        }
                    }
                    visited.mark(cur);
                    if (cur == b.getEndNode()) {
                        break;
                    }
                    cur = ((FixedWithNextNode) cur).next();
                }
            }
        }

        for (Node loopNode : loop.inside().nodes()) {
            if (visited.isMarked(loopNode)) {
                continue;
            }
            if (nodesToSkip == null || !nodesToSkip.isMarkedAndGrow(loopNode)) {
                processNode(loopNode, inLoop, loop, state, readEliminationAliases, synonymsIteration, defaultSimplifier, ben);
            }
            if (synonymsIteration.get(loopNode) != null) {
                // if we found something, re-iterate the usages of the node inside the loop to
                // capture transitive improvements
                for (Node usage : loopNode.usages()) {
                    if (loop.inside().contains(usage)) {
                        if (nodesToSkip == null || !nodesToSkip.isMarkedAndGrow(usage)) {
                            processNode(usage, inLoop, loop, state, readEliminationAliases, synonymsIteration, defaultSimplifier, ben);
                        }
                    }

                }
            }

        }

        MapCursor<Node, Node> cursor = synonymsIteration.getEntries();
        outer: while (cursor.advance()) {
            Node original = cursor.getKey();
            Node synonymInPeeledIteration = cursor.getValue();
            int originalTime = original.estimatedNodeCycles().value;
            if (synonymInPeeledIteration instanceof DummyNullValueNode) {
                continue;
            }
            if (synonymInPeeledIteration == original && nodesToSkip != null && !nodesToSkip.isMarkedAndGrow(original)) {
                /*
                 * A load that will not become loop invariant by peeling or unrolling. We do not
                 * save cycles for this node.
                 */
                continue;
            }
            if (synonymInPeeledIteration.isAlive()) {
                /*
                 * We replace a node with an existent one, so we save the entire time of the
                 * original computation
                 */
                cyclesSaved += originalTime;
            } else {
                /*
                 * We replace a node with a new operation that may also need time
                 */
                for (Node input : synonymInPeeledIteration.inputs()) {
                    if (!input.isAlive()) {
                        // tree of new nodes, opt out to complex to compute the transitive set of
                        // new nodes
                    }
                    continue outer;
                }
                int synonymTime = synonymInPeeledIteration.estimatedNodeCycles().value;
                if (synonymTime < originalTime) {
                    cyclesSaved += originalTime - synonymTime;
                }
            }
        }
        cyclesSaved += ben.deletedCanonicalizationCycles;
        cyclesSaved += ben.killsBranch ? BranchKilledBenefit : 0;
        return cyclesSaved;
    }

    /**
     * Benefit used when simulation finds out that an entire branch can be killed.
     */
    private static final int BranchKilledBenefit = 16;

    static class SpecialBenefit {
        boolean killsBranch;
        int deletedCanonicalizationCycles;
        private EconomicSet<Node> deletedCanonicalizations;

        /**
         * Records the benefit of a canonicalization that deletes an unused node.
         */
        void recordDeletedCanonicalization(Node node) {
            if (node.hasNoUsages() && (deletedCanonicalizations == null || !deletedCanonicalizations.contains(node))) {
                if (deletedCanonicalizations == null) {
                    deletedCanonicalizations = EconomicSet.create(Equivalence.IDENTITY);
                }
                deletedCanonicalizations.add(node);
                deletedCanonicalizationCycles += node.estimatedNodeCycles().value;
            }
        }
    }

    private static boolean processNode(Node node, boolean inLoop, Loop loop, ReadEliminationBlockState state, NodeMap<Node> aliases, NodeMap<Node> synonyms, CanonicalizerTool canonicalizerTool,
                    SpecialBenefit specialBenefit) {
        if (node instanceof AccessFieldNode) {
            AccessFieldNode access = (AccessFieldNode) node;
            if (access.ordersMemoryAccesses()) {
                killReadCacheByIdentity(state, any(), access);
            } else {
                ValueNode object = GraphUtil.unproxify(access.object());
                LoadCacheEntry identifier = new LoadCacheEntry(object, new FieldLocationIdentity(access.field()));
                ValueNode cachedValue = state.getCacheEntry(identifier);
                if (node instanceof LoadFieldNode) {
                    if (!inLoop) {
                        if (cachedValue != null && access.stamp(NodeView.DEFAULT).isCompatible(cachedValue.stamp(NodeView.DEFAULT))) {
                            pushSynonym(synonyms, access, cachedValue);
                            aliases.set(access, cachedValue);
                        } else {
                            state.addCacheEntry(identifier, access);
                        }
                    }
                } else {
                    assert node instanceof StoreFieldNode : node;
                    StoreFieldNode store = (StoreFieldNode) node;
                    ValueNode value = (ValueNode) getMemoryAlias((ValueNode) synonym(store.value(), synonyms), aliases);
                    if (!inLoop) {
                        if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                            pushSynonym(synonyms, node, value);
                        }
                    }
                    // will be a field location identity not killing array accesses
                    killReadCacheByIdentity(state, identifier.identity, node);
                    if (!inLoop) {
                        state.addCacheEntry(identifier, value);
                    }
                }
            }
        } else if (node instanceof ReadNode read && !inLoop && !MemoryKill.isMemoryKill(read)) {
            if (read.getLocationIdentity().isSingle()) {
                ValueNode object = GraphUtil.unproxify(read.getAddress());
                LoadCacheEntry identifier = new LoadCacheEntry(object, read.getLocationIdentity());
                ValueNode cachedValue = state.getCacheEntry(identifier);
                if (cachedValue != null) {
                    pushSynonym(synonyms, read, cachedValue);
                    aliases.set(read, cachedValue);
                } else {
                    state.addCacheEntry(identifier, read);
                }
            }
        } else if (node instanceof WriteNode) {
            WriteNode write = (WriteNode) node;
            if (write.getLocationIdentity().isSingle()) {
                ValueNode object = GraphUtil.unproxify(write.getAddress());
                LoadCacheEntry identifier = new LoadCacheEntry(object, write.getLocationIdentity());
                ValueNode cachedValue = state.getCacheEntry(identifier);
                ValueNode value = (ValueNode) getMemoryAlias((ValueNode) synonym(write.value(), synonyms), aliases);
                if (!inLoop) {
                    if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                        pushSynonym(synonyms, node, value);
                    }
                }
                killReadCacheByIdentity(state, write.getLocationIdentity(), node);
                if (!inLoop) {
                    state.addCacheEntry(identifier, value);
                }
            } else {
                killReadCacheByIdentity(state, write.getLocationIdentity(), node);
            }
        } else if (node instanceof UnsafeAccessNode) {
            /*
             * We do not know if we are writing an array or a normal object
             */
            if (node instanceof RawLoadNode) {
                RawLoadNode load = (RawLoadNode) node;
                if (!inLoop) {
                    if (load.getLocationIdentity().isSingle()) {
                        ValueNode object = GraphUtil.unproxify(load.object());
                        UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, load.offset(), load.getLocationIdentity(), load.accessKind());
                        ValueNode cachedValue = state.getCacheEntry(identifier);
                        if (cachedValue != null) {
                            if (load.accessKind() == JavaKind.Boolean) {
                                // perform boolean coercion
                                LogicNode cmp = IntegerEqualsNode.create(cachedValue, ConstantNode.forInt(0), NodeView.DEFAULT);
                                ValueNode boolValue = ConditionalNode.create(cmp, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT);
                                cachedValue = boolValue;
                            }
                            pushSynonym(synonyms, load, cachedValue);
                            aliases.set(load, cachedValue);
                        } else {
                            state.addCacheEntry(identifier, load);
                        }
                    }
                }
            } else {
                assert node instanceof RawStoreNode : node;
                RawStoreNode write = (RawStoreNode) node;
                if (write.getLocationIdentity().isSingle()) {
                    ValueNode object = GraphUtil.unproxify(write.object());
                    UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, write.offset(), write.getLocationIdentity(), write.accessKind());
                    ValueNode cachedValue = state.getCacheEntry(identifier);
                    ValueNode value = (ValueNode) getMemoryAlias((ValueNode) synonym(write.value(), synonyms), aliases);
                    if (!inLoop && GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                        pushSynonym(synonyms, node, value);
                    }
                    killReadCacheByIdentity(state, write.getLocationIdentity(), node);
                    if (!inLoop) {
                        state.addCacheEntry(identifier, value);
                    }
                } else {
                    killReadCacheByIdentity(state, write.getLocationIdentity(), node);
                }
            }
        } else if (node instanceof LoadIndexedNode) {
            ValueNode constantFoldedValue = null;
            LoadIndexedNode load = (LoadIndexedNode) node;

            ValueNode arraySynynonym = load.array().isConstant() ? load.array() : (ValueNode) synonym(load.array(), synonyms);
            ValueNode indexSynonym = load.index().isConstant() ? load.index() : (ValueNode) synonym(load.index(), synonyms);

            if (arraySynynonym != null && arraySynynonym.isConstant() && indexSynonym != null && indexSynonym.isConstant()) {
                constantFoldedValue = LoadIndexedNode.tryConstantFold(arraySynynonym, indexSynonym, canonicalizerTool.getMetaAccess(), canonicalizerTool.getConstantReflection());
                if (constantFoldedValue != null) {
                    pushSynonym(synonyms, load, constantFoldedValue);
                }
            }

            // BALOAD (with elementKind being Byte) can be used to retrieve values from boolean
            // arrays.
            JavaKind elementKind = load.elementKind();
            if (elementKind == JavaKind.Byte) {
                elementKind = getElementKindFromStamp((ValueNode) synonym(load.array(), synonyms));
                if (elementKind == JavaKind.Illegal) {
                    return false;
                }
            }
            ValueNode unproxifiedArray = GraphUtil.unproxify((ValueNode) synonym(load.array(), synonyms));
            ValueNode unproxifiedIndex = GraphUtil.unproxify((ValueNode) synonym(load.index(), synonyms));
            if (inLoop && (!unproxifiedArray.isAlive() || loop.inside().contains(unproxifiedArray) ||
                            !unproxifiedIndex.isAlive() || loop.inside().contains(unproxifiedIndex))) {
                // may not be loop invariant, not alive nodes are placeholder values for the
                // loop backedge stamps, they are always loop variant
                return false;
            }
            if (unproxifiedArray instanceof ControlSinkNode || unproxifiedIndex instanceof ControlSinkNode ||
                            !unproxifiedIndex.isAlive() || !unproxifiedArray.isAlive()) {
                return false;
            }
            CacheEntry<?> identifier = new IndexedCacheEntry(unproxifiedArray,
                            NamedLocationIdentity.getArrayLocation(load.elementKind()), unproxifiedIndex,
                            load.elementKind());
            ValueNode cachedValue = state.getCacheEntry(identifier);
            if (cachedValue != null) {
                aliases.set(load, cachedValue);
                pushSynonym(synonyms, node, cachedValue);
                return true;
            } else {
                state.addCacheEntry(identifier, constantFoldedValue != null ? constantFoldedValue : load);
            }
        } else if (node instanceof StoreIndexedNode) {
            StoreIndexedNode store = (StoreIndexedNode) node;
            ValueNode unproxifiedIndex = GraphUtil.unproxify((ValueNode) synonym(store.index(), synonyms));
            ValueNode unproxifiedArray = GraphUtil.unproxify((ValueNode) synonym(store.array(), synonyms));
            if (inLoop && (!unproxifiedArray.isAlive() || loop.inside().contains(unproxifiedArray) ||
                            !unproxifiedIndex.isAlive() || loop.inside().contains(unproxifiedIndex))) {
                // may not be loop invariant, not alive nodes are placeholder values for the
                // loop backedge stamps, they are always loop variant
                return false;
            }
            if (unproxifiedArray instanceof ControlSinkNode || unproxifiedIndex instanceof ControlSinkNode ||
                            !unproxifiedIndex.isAlive() || !unproxifiedArray.isAlive()) {
                return false;
            }
            // BASTORE (with elementKind being Byte) can be used to store values in boolean
            // arrays.
            JavaKind elementKind = store.elementKind();
            if (elementKind == JavaKind.Byte) {
                elementKind = getElementKindFromStamp((ValueNode) synonym(store.array(), synonyms));
                if (elementKind == JavaKind.Illegal) {
                    return false;
                }
            }

            CacheEntry<?> identifier = new IndexedCacheEntry(unproxifiedArray,
                            NamedLocationIdentity.getArrayLocation(store.elementKind()), unproxifiedIndex,
                            store.elementKind());
            ValueNode cachedValue = state.getCacheEntry(identifier);
            ValueNode finalValue = (ValueNode) getMemoryAlias((ValueNode) synonym(store.value(), synonyms), aliases);
            boolean deleted = false;
            if (GraphUtil.unproxify(finalValue) == GraphUtil.unproxify(cachedValue)) {
                deleted = true;
                pushSynonym(synonyms, node, finalValue);
            }
            state.killReadCache(node, identifier.getIdentity(), unproxifiedIndex, unproxifiedArray);
            state.addCacheEntry(identifier, finalValue);
            return deleted;
        } else if (MemoryKill.isSingleMemoryKill(node)) {
            LocationIdentity identity = ((SingleMemoryKill) node).getKilledLocationIdentity();
            killReadCacheByIdentity(state, identity, node);
        } else if (MemoryKill.isMultiMemoryKill(node)) {
            for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                killReadCacheByIdentity(state, identity, node);
            }
        } else if (node instanceof IfNode || node instanceof FixedGuardNode) {
            Node c = null;
            if (node instanceof IfNode) {
                c = ((IfNode) node).condition();
            } else {
                c = ((FixedGuardNode) node).condition();
            }
            processPotentialCanonicalizations(c, synonyms, canonicalizerTool, specialBenefit);
            Node syn = synonym((ValueNode) c, synonyms);
            if (syn != c && syn instanceof LogicConstantNode) {
                // killed a branch
                specialBenefit.killsBranch = true;
            } else {
                if (c instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) c;
                    ValueNode value = unaryLogicNode.getValue();
                    Stamp stamp = ((ValueNode) synonym(value, synonyms)).stamp(NodeView.DEFAULT);
                    TriState result = unaryLogicNode.tryFold(stamp);
                    if (result.isKnown()) {
                        Foldings.increment(loop.loopBegin().graph().getDebug());
                        specialBenefit.killsBranch = true;
                    }
                } else if (c instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) c;
                    ValueNode x = (ValueNode) synonym(binaryOpLogicNode.getX(), synonyms);
                    ValueNode y = (ValueNode) synonym(binaryOpLogicNode.getY(), synonyms);
                    TriState result = binaryOpLogicNode.tryFold(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT));
                    if (result.isKnown()) {
                        Foldings.increment(loop.loopBegin().graph().getDebug());
                        specialBenefit.killsBranch = true;
                    }
                }
            }
        } else if (node instanceof IntegerSwitchNode integerSwitchNode) {
            ValueNode switchValue = integerSwitchNode.switchValue();
            Node syn = synonym(switchValue, synonyms);
            if (syn != switchValue && syn instanceof ConstantNode) {
                specialBenefit.killsBranch = true;
            }
        } else {
            processPotentialCanonicalizations(node, synonyms, canonicalizerTool, specialBenefit);
        }
        return false;
    }

    private static final CounterKey Foldings = DebugContext.counter("Peeling_Foldings");
    private static final CounterKey FoldingRest = DebugContext.counter("Peeling_BenefitRest");
    private static final CounterKey FoldingsPeeled = DebugContext.counter("Peeling_BenefitPeeled");

    private static void killReadCacheByIdentity(ReadEliminationBlockState state, LocationIdentity identity, Node kill) {
        state.killReadCache(kill, identity, null, null);
    }

    @SuppressWarnings("unchecked")
    private static void processPotentialCanonicalizations(Node node, NodeMap<Node> synonyms, CanonicalizerTool canonicalizerTool, SpecialBenefit specialBenefit) {
        if (node instanceof Canonicalizable.Unary<?>) {
            Unary<ValueNode> unary = (Unary<ValueNode>) node;
            if (unary.getValue() != null && synonyms != null && (synonyms.getAndGrow(unary.getValue()) != null)) {
                final ValueNode value = (ValueNode) synonyms.get(unary.getValue());
                if (value instanceof ControlSinkNode) {
                    /*
                     * Synonym can already be reduced to a deopt from some canonicalizations
                     */
                    return;
                }
                if (value != null && value != unary.getValue()) {
                    final Node improved = unary.canonical(canonicalizerTool, value);
                    if (improved != unary) {
                        if (improved == null) {
                            specialBenefit.recordDeletedCanonicalization(node);
                        } else {
                            pushSynonym(synonyms, node, improved);
                        }
                        return;
                    }
                }
            }
        } else if (node instanceof Canonicalizable.Binary<?>) {
            Binary<ValueNode> binary = (Binary<ValueNode>) node;
            final ValueNode x = binary.getX();
            final ValueNode y = binary.getY();
            assert x != null;
            assert y != null;
            if (synonyms != null) {
                final ValueNode xImproved = (ValueNode) synonyms.getAndGrow(x);
                final ValueNode yImproved = (ValueNode) synonyms.getAndGrow(y);
                if (xImproved != null || yImproved != null) {
                    ValueNode xUsed = xImproved == null ? x : xImproved;
                    ValueNode yUsed = yImproved == null ? y : yImproved;
                    if (xUsed instanceof ControlSinkNode || yUsed instanceof ControlSinkNode) {
                        /*
                         * Synonym can already be reduced to a deopt from some canonicalizations
                         */
                        return;
                    }
                    if (xUsed != x || yUsed != y) {
                        final Node improved = binary.canonical(canonicalizerTool, xUsed, yUsed);
                        if (improved != binary) {
                            if (improved == null) {
                                specialBenefit.recordDeletedCanonicalization(node);
                            } else {
                                pushSynonym(synonyms, node, improved);
                            }
                            return;
                        }
                    }
                }
            }
            if (node instanceof ShortCircuitOrNode) {
                ShortCircuitOrNode sc = (ShortCircuitOrNode) node;
                processPotentialCanonicalizations(sc.getX(), synonyms, canonicalizerTool, specialBenefit);
                processPotentialCanonicalizations(sc.getY(), synonyms, canonicalizerTool, specialBenefit);
            }
        }
    }

    private static void pushSynonym(NodeMap<Node> synonyms, Node key, Node value) {
        assert key != null;
        assert value != null;
        assert !synonyms.isNew(key) : "Simulation must not change the graph";
        /*
         * Note: It may be possible that we see different stamp kinds for synonyms that are not
         * compatible, i.e., we write a float to an int array but read an int. Thus, we only allow
         * compatible stamps in the synonym maps.
         */
        if (((ValueNode) key).stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Void ||
                        ((ValueNode) key).stamp(NodeView.DEFAULT).isCompatible(((ValueNode) value).stamp(NodeView.DEFAULT))) {
            synonyms.set(key, value);
        }
    }

    private static Node getMemoryAlias(ValueNode original, NodeMap<Node> aliases) {
        if (original.isAlive()) {
            Node alias = aliases.getAndGrow(original);
            return alias != null ? alias : original;
        }
        return original;
    }

    private static Node synonym(ValueNode original, NodeMap<Node> synonyms) {
        Node synonym = synonyms.get(original);
        return synonym != null ? synonym : original;
    }

    private static JavaKind getElementKindFromStamp(ValueNode array) {
        ResolvedJavaType type = StampTool.typeOrNull(array);
        if (type != null && type.isArray()) {
            return type.getComponentType().getJavaKind();
        } else {
            // It is likely an OSRLocal without valid stamp
            return JavaKind.Illegal;
        }
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
    private static class StampPlaceholderNode extends FloatingNode implements GuardingNode, Canonicalizable {
        public static final NodeClass<StampPlaceholderNode> TYPE = NodeClass.create(StampPlaceholderNode.class);

        private final NodeSize originalSize;
        private final NodeCycles originalCycles;

        protected StampPlaceholderNode(Stamp stamp, NodeSize originalSize, NodeCycles originalCycles) {
            super(TYPE, stamp);
            this.originalSize = originalSize;
            this.originalCycles = originalCycles;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // delete this dummy node again
            return null;
        }

        @Override
        public NodeCycles estimatedNodeCycles() {
            return originalCycles;
        }

        @Override
        protected NodeSize dynamicNodeSizeEstimate() {
            return originalSize;
        }
    }

}
