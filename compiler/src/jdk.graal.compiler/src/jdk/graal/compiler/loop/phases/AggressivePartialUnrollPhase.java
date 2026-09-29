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

import static jdk.graal.compiler.options.OptionType.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.loop.phases.LoopTransformations.ProtectionData;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.iterators.FilteredNodeIterable;
import jdk.graal.compiler.loop.phases.LoopTransformations.PreMainPostResult;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MemoryProxyNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopFragment;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.ExcludeFromJacocoGeneratedReport;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.OptimizeExactArithmeticPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.LogNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticNode;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;

/// Performs simulation-guided partial unrolling for arbitrary loop shapes, including loops with
/// control flow in the body. This phase runs before proxy removal and requires proxy nodes to
/// represent values crossing loop exits. The master [GraalOptions#PartialUnroll] option controls
/// all partial unrolling, while
/// [Options#AggressivePartialUnroll] selects this implementation. The tier-specific options
/// [Options#HighTierPartialUnrolling] and [Options#MidTierPartialUnrolling], together with the
/// cost and benefit options below, refine where it runs. Enabling the aggressive option while
/// disabling the master option is invalid.
///
/// The [SimpleLoopPartialUnrollPhase] runs later, does not require proxy nodes, and supports only
/// counted loops with three blocks: a header, a body without control flow, and an exit. This phase
/// can also consider shapes such as a loop with an early exit:
///
/// ```java
/// for (int i = 0; i < n; i++) {
///     if (stop(i)) {
///         break;
///     }
///     body(i);
/// }
/// // Aggressive unrolling uses loop-exit proxies to evaluate this shape.
/// ```
public class AggressivePartialUnrollPhase extends LoopPhase<LoopPolicies> {

    public static class Options {
        //@formatter:off
        @Option(help = "Enables the advanced version of partial loop unrolling that considers more loop shapes for unrolling.", type = OptionType.Expert)
        public static final OptionKey<Boolean> AggressivePartialUnroll = new OptionKey<>(true) {
            @Override
            public Boolean getValue(OptionValues values) {
                Boolean aggressive = super.getValue(values);
                if (aggressive && !GraalOptions.PartialUnroll.getValue(values)) {
                    if (hasBeenSet(values)) {
                        throw new IllegalArgumentException("AggressivePartialUnroll cannot be enabled when PartialUnroll is disabled.");
                    }
                    return false;
                }
                return aggressive;
            }

            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (newValue && values.containsKey(GraalOptions.PartialUnroll) && !Boolean.TRUE.equals(values.get(GraalOptions.PartialUnroll))) {
                    throw new IllegalArgumentException("AggressivePartialUnroll cannot be enabled when PartialUnroll is disabled.");
                }
            }
        };

        /**
         * Verifies the relationship between the master partial unrolling switch and its advanced
         * implementation. This also covers option maps assembled directly by tests and embedders,
         * which do not invoke {@link OptionKey#onValueUpdate}.
         */
        public static void checkPartialUnroll(OptionValues options) {
            AggressivePartialUnroll.getValue(options);
        }

        @Option(help = "Cost/Benefit heuristic for aggressive unrolling: If a loop has multiple exits, cost is increased by this value for every sinking loop exit.", type = Debug)
        public static final OptionKey<Integer> MultiExitCostFactorSink = new OptionKey<>(2);

        @Option(help = "Cost/Benefit heuristic for aggressive unrolling: If a loop has multiple exits, cost is increased by this value for every none-sinking loop exit.", type = Debug)
        public static final OptionKey<Integer> MultiExitCostFactor = new OptionKey<>(32);

        @Option(help = "Unroll loops with multiple loop ends.", type = Debug)
        public static final OptionKey<Boolean> UnrollMultiEndLoops = new OptionKey<>(true);

        @Option(help = "Unroll loops with multiple loop exits.", type = Debug)
        public static final OptionKey<Boolean> UnrollMultiExitLoops = new OptionKey<>(true);

        @Option(help = "Force partial unrolling of loops if at all possible.", type = Debug)
        public static final OptionKey<Boolean> ForceUnroll = new OptionKey<>(false);

        @Option(help = "Unroll inverted (tail counted) loops.", type = Debug)
        public static final OptionKey<Boolean> UnrollInvertedLoops = new OptionKey<>(true);

        @Option(help = "Unroll empty loops.", type = Debug)
        public static final OptionKey<Boolean> UnrollEmptyLoops = new OptionKey<>(false);

        @Option(help = "Do not unroll the main loop, only create pre-main-post.", type = Debug)
        public static final OptionKey<Boolean> InsertPreMainPostOnly = new OptionKey<>(false);

        /**
         * Rationale for a minimum frequency of (at least) 4: Partial unrolling transforms one loop
         * into a sequence of three loops, the pre/main/post loops. For very small numbers of
         * iterations this increases the number of loop condition checks instead of reducing it.
         *
         * (For head counted loops) a frequency of 4 means 4 loop condition checks, i.e., 3
         * iterations of the original loop body. These 3 iterations will lead to 1 pre loop
         * iteration and 1 main loop iteration (unrolled 2x); at a lower number of iterations we
         * cannot enter the main loop. Therefore a frequency of 4 is the minimum for benefiting from
         * the main loop at all.
         *
         * Conversely, minimum frequencies of 8 or more regress some benchmarks. Even some
         * low-frequency loops can be performance relevant and profit from partial unrolling.
         */
        @Option(help = "Minimal loop frequency to consider a loop for partial unrolling", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMinFrequency = new OptionKey<>(4);

        @Option(help = "Enable aggressive partial unrolling in high tier.")
        public static final OptionKey<Boolean> HighTierPartialUnrolling = new OptionKey<>(true);

        @Option(help = "Maximum node cost size of a loop to be considered for high tier unrolling.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMaxSizeHighTier = new OptionKey<>(256);

        @Option(help = "Maximum number of iterations to unroll for a high tier main loop.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMaxIterationsHighTier = new OptionKey<>(4);

        @Option(help = "Cost/Benefit heuristic for aggressive unrolling in high tier: reduce cost by a constant factor when comparing with relative benefit.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollCostReductionFactorHighTier = new OptionKey<>(2);

        @Option(help = "Enable aggressive partial unrolling in mid tier.")
        public static final OptionKey<Boolean> MidTierPartialUnrolling = new OptionKey<>(true);

        @Option(help = "Maximum node cost size of a loop to be considered for mid tier tier unrolling.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMaxSizeMidTier = new OptionKey<>(256);

        @Option(help = "See PartialUnrollMaxSizeMidTier", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMaxSizeHotCodeMidTier = new OptionKey<>(256 * 4);

        @Option(help = "Maximum number of iterations to unroll for a mid tier main loop.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollMaxIterationsMidTier = new OptionKey<>(16);

        @Option(help = "Cost/Benefit heuristic for aggressive unrolling in mid tier: reduce cost by a constant factor when comparing with relative benefit.", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollCostReductionFactorMidTier = new OptionKey<>(8);

        @Option(help = "See PartialUnrollCostReductionFactorMidTier", type = OptionType.Debug)
        public static final OptionKey<Integer> PartialUnrollCostReductionFactorHotCodeMidTier = new OptionKey<>(8 * 4);

        @Option(help = "Benefit boost for strip mined non counted loops.", type = OptionType.Debug)
        public static final OptionKey<Integer> NonCountedStripMinedBenefitBoost = new OptionKey<>(64);

        @Option(help = "Benefit boost for strip mined counted loops.", type = OptionType.Debug)
        public static final OptionKey<Integer> CountedStripMinedBenefitBoost = new OptionKey<>(0);
        //@formatter:on
    }

    public static final CounterKey UnsupportedEndExitLoop = DebugContext.counter("AggressivePartialUnrolling_UnsupportedEndExitLoop");
    public static final CounterKey InvertedLoops = DebugContext.counter("AggressivePartialUnrolling_Inverted");
    public static final CounterKey RotatedLoops = DebugContext.counter("AggressivePartialUnrolling_Rotated");

    private final CanonicalizerPhase canonicalizer;
    private final Boolean highTier;

    public AggressivePartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        this(policies, canonicalizer, null);
    }

    /// Creates the production phase with the aggressive policy tuned for the selected tier.
    public AggressivePartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer, boolean highTier) {
        this(policies, canonicalizer, Boolean.valueOf(highTier));
    }

    private AggressivePartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer, Boolean highTier) {
        super(policies, canonicalizer);
        this.canonicalizer = canonicalizer;
        this.highTier = highTier;
    }

    private static final CounterKey HighTierNotUnrolledVectorizable = DebugContext.counter("Aggressive_HighTier_PartialUnrolling_NotUnrolled_Vectorizable");
    private static final CounterKey HighTierNotUnrolledFrequency = DebugContext.counter("Aggressive_HighTier_PartialUnrolling_NotUnrolled_TooLowFrequency");
    private static final CounterKey HighTierNotUnrolledLoopSize = DebugContext.counter("Aggressive_HighTier_PartialUnrolling_NotUnrolled_LoopSize");
    private static final CounterKey HighTierNotUnrolledBenefit = DebugContext.counter("Aggressive_HighTier_PartialUnrolling_NotUnrolled_BenefitCalculation");
    private static final CounterKey HighTierUnrolled = DebugContext.counter("Aggressive_HighTier_PartialUnrolling_Unrolled");
    private static final CounterKey MidTierNotUnrolledVectorizable = DebugContext.counter("Aggressive_MidTier_PartialUnrolling_NotUnrolled_Vectorizable");
    private static final CounterKey MidTierNotUnrolledFrequency = DebugContext.counter("Aggressive_MidTier_PartialUnrolling_NotUnrolled_TooLowFrequency");
    private static final CounterKey MidTierNotUnrolledLoopSize = DebugContext.counter("Aggressive_MidTier_PartialUnrolling_NotUnrolled_LoopSize");
    private static final CounterKey MidTierNotUnrolledBenefit = DebugContext.counter("Aggressive_MidTier_PartialUnrolling_NotUnrolled_BenefitCalculation");
    private static final CounterKey MidTierUnrolled = DebugContext.counter("Aggressive_MidTier_PartialUnrolling_Unrolled");

    private boolean shouldPartiallyUnroll(Loop loop, CoreProviders providers) {
        if (highTier == null) {
            // Tests and specialized callers can continue to supply an explicit policy.
            return getPolicies().shouldPartiallyUnroll(loop, providers);
        }
        LoopBeginNode loopBegin = loop.loopBegin();
        assert loopBegin.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) : "Graph must be before stage " + StageFlag.VALUE_PROXY_REMOVAL + " but is " + loopBegin.graph().getGraphState();
        CounterKey vectorizable = highTier ? HighTierNotUnrolledVectorizable : MidTierNotUnrolledVectorizable;
        CounterKey frequency = highTier ? HighTierNotUnrolledFrequency : MidTierNotUnrolledFrequency;
        if (!LoopUtility.loopQualifiesForPartialUnrolling(loop, providers, vectorizable, frequency)) {
            return false;
        }
        int size = LoopUtility.loopSize(loop);
        int maxSize;
        int costReductionFactor;
        int maxIterations;
        CounterKey loopSizeCounter;
        CounterKey benefitCounter;
        CounterKey unrolledCounter;
        if (highTier) {
            maxSize = Options.PartialUnrollMaxSizeHighTier.getValue(loopBegin.getOptions());
            costReductionFactor = Options.PartialUnrollCostReductionFactorHighTier.getValue(loopBegin.getOptions());
            maxIterations = Options.PartialUnrollMaxIterationsHighTier.getValue(loopBegin.getOptions());
            loopSizeCounter = HighTierNotUnrolledLoopSize;
            benefitCounter = HighTierNotUnrolledBenefit;
            unrolledCounter = HighTierUnrolled;
            if (OptimizeExactArithmeticPhase.isLikelyOptimizable(loop)) {
                return false;
            }
        } else {
            StructuredGraph graph = loopBegin.graph();
            maxSize = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.PartialUnrollMaxSizeMidTier, Options.PartialUnrollMaxSizeHotCodeMidTier);
            costReductionFactor = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.PartialUnrollCostReductionFactorMidTier,
                            Options.PartialUnrollCostReductionFactorHotCodeMidTier);
            maxIterations = Options.PartialUnrollMaxIterationsMidTier.getValue(loopBegin.getOptions());
            loopSizeCounter = MidTierNotUnrolledLoopSize;
            benefitCounter = MidTierNotUnrolledBenefit;
            unrolledCounter = MidTierUnrolled;
        }
        return LoopUtility.loopSizeAllowsUnrolling(loop, maxSize, size, loopSizeCounter) &&
                        LoopUtility.shouldPartiallyUnroll(loop, size, providers, benefitCounter, unrolledCounter, costReductionFactor, maxIterations);
    }

    public static boolean isUnrollableLoop(Loop loop, int maxLoopEnds) {
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            return false;
        }
        if (!loop.isCounted() || !loop.counted().getLimitCheckedIV().isConstantStride() || !loop.getCFGLoop().getChildren().isEmpty()) {
            return false;
        }
        if (!(loop.counted().getCountedExit() instanceof LoopExitNode)) {
            // deopt exited counted loop
            return false;
        }
        assert loop.counted().getDirection() != null;
        LoopBeginNode loopBegin = loop.loopBegin();
        LogicNode condition = loop.counted().getLimitTest().condition();
        if (!(condition instanceof CompareNode)) {
            return false;
        }
        if (((CompareNode) condition).condition() == CanonicalCondition.EQ) {
            condition.getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s condition unsupported %s ", loopBegin, ((CompareNode) condition).condition());
            return false;
        }
        if (LoopTransformations.strideAdditionOverflows(loop)) {
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s doubling the stride overflows %d", loopBegin, loop.counted().getLimitCheckedIV().constantStride());
            return false;
        }
        if (!loop.canDuplicateLoop()) {
            condition.getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s must not unroll loop due to loop.canDuplicate", loopBegin);
            return false;
        }
        if (loop.loopBegin().isOsrLoop()) {
            condition.getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s must not unroll osr loop", loopBegin);
            return false;
        }
        if (loop.loopBegin().isPreLoop() || loop.loopBegin().isPostLoop()) {
            return false;
        }
        if (LoopUtility.isConstantLoopCount(loop, Options.PartialUnrollMinFrequency.getValue(loop.loopBegin().getOptions()))) {
            condition.getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s must not unroll loop with a very small number of iteration", loopBegin);
            return false;
        }

        final int loopEndCount = loop.loopBegin().loopEnds().count();
        final int loopExitCount = loop.loopBegin().loopExits().count();

        if (loopEndCount > maxLoopEnds || (loopEndCount > 1 && !Options.UnrollMultiEndLoops.getValue(loopBegin.getOptions())) || loopExitCount < 1 ||
                        (loopExitCount > 1 && !Options.UnrollMultiExitLoops.getValue(loopBegin.getOptions()))) {
            UnsupportedEndExitLoop.increment(loop.loopBegin().getDebug());
            return false;
        }
        if (LoopTransformations.countedLoopExitConditionHasMultipleUsages(loop)) {
            return false;
        }
        if (!loop.counted().loopMightBeEntered()) {
            condition.getDebug().log("isUnrollableLoop %s cannot be entered", loopBegin);
            return false;
        }
        if (!Options.UnrollEmptyLoops.getValue(loopBegin.getOptions())) {
            if (LoopUtility.isEmptyLoop(loop)) {
                loop.loopBegin().getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s loop is empty", loop.loopBegin());
                return false;
            }
        }

        if (loop.counted().isInverted()) {
            CountedLoopInfo ecli = loop.counted();
            if (!ecli.emptyInvertedCountedBackedge()) {
                return false;
            }
            if (containsExactMath(loop.counted().getLimitCheckedIV())) {
                /*
                 * Loop of the form: for(...;...;i=Integer.addExact(i,stride));
                 *
                 * if we unroll such a loop it would look like
                 *
                 * for(...;...;i=Integer.addExact(Integer.addExact(i,stride)),stride));
                 *
                 * where we cannot simply fold through the two addExact operations. While the exact
                 * math nodes will have proper floating/fixed guarded logic the folding does not
                 * happen out of the box in the canonicalizer. This means the limit check after
                 * unrolling no longer is based on an induction variable but yet another node. We
                 * refrain from unrolling such loops.
                 */
                loop.loopBegin().getDebug().log(DebugContext.BASIC_LEVEL, "isUnrollableLoop %s loop exact induction variable", loop.loopBegin());
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the given induction variable or any of its base IVs contain
     * {@link IntegerExactArithmeticNode} math nodes.
     */
    private static boolean containsExactMath(InductionVariable iv) {
        InductionVariable curIV = iv;
        while (curIV instanceof DerivedInductionVariable dv) {
            if (dv.valueNode() instanceof IntegerExactArithmeticNode) {
                return true;
            }
            curIV = dv.getBase();
        }
        assert curIV instanceof BasicInductionVariable : Assertions.errorMessage("Must be a base iv after iteration", iv, curIV);
        return ((BasicInductionVariable) curIV).getOp() instanceof IntegerExactArithmeticNode;
    }

    private static LoopsData getLoopsData(StructuredGraph graph, CoreProviders context) {
        return getLoopsData(graph, context, true);
    }

    private static LoopsData getLoopsData(StructuredGraph graph, CoreProviders context, boolean detectCounted) {
        LoopsData dataCounted = context.getLoopsDataProvider().getLoopsData(graph);
        if (detectCounted) {
            dataCounted.detectCountedLoops();
        }
        return dataCounted;
    }

    /**
     * Assert sanity about the internal structure of the counted loops data. When creating the
     * pre-main-post scheme the internal LoopEx data structure must still be valid, i.e., assertions
     * inside must hold, thus we test it for all pre-main-post loops by creating it directly after
     * the pre-main-post insertion.
     */
    private static boolean validCountedLoopData(StructuredGraph graph, CoreProviders context) {
        if (Assertions.assertionsEnabled()) {
            /*
             * Sanity assertion that another iteration over the loops data does not invalidly
             * include wrong nodes in the set of loop nodes. This is especially important for shared
             * virtual object states of framestates inside the loop.
             */
            for (Loop loop : getLoopsData(graph, context).countedLoops()) {
                /*
                 * Main loops that just have been created must be unrollable
                 */
                assert !loop.loopBegin().isMainLoop() || loop.loopBegin().getUnrollFactor() != 0 || isUnrollableLoop(loop, 1) : "Must be unrollable loop, main has just been created " + loop;
            }
        }
        return true;
    }

    private boolean insertAllPreMainPost(StructuredGraph graph, CoreProviders context) {
        boolean insertedOne = false;
        ArrayList<PreMainPostResult> loopsToProtectAfter = null;
        for (Loop loop : getLoopsData(graph, context).countedLoops()) {
            if (!isUnrollableLoop(loop, 1)) {
                continue;
            }
            if (shouldPartiallyUnroll(loop, context)) {
                if (loop.loopBegin().isSimpleLoop()) {
                    boolean invertedFirstIterationCheck = loop.counted().isInverted();
                    if (invertedFirstIterationCheck) {
                        if (!Options.UnrollInvertedLoops.getValue(graph.getOptions())) {
                            continue;
                        }
                    }
                    LoopUtility.preserveCounterStampsForDivAfterUnroll(loop);
                    final boolean neverOverflows = loop.counted().counterNeverOverflows();
                    PreMainPostResult result = LoopTransformations.insertPrePostLoops(loop);
                    if (neverOverflows) {
                        if (!result.getPreLoop().canNeverOverflow()) {
                            result.getPreLoop().setCanNeverOverflow();
                        }
                        if (!result.getMainLoop().canNeverOverflow()) {
                            result.getMainLoop().setCanNeverOverflow();
                        }
                        if (!result.getPostLoop().canNeverOverflow()) {
                            result.getPostLoop().setCanNeverOverflow();
                        }
                    }
                    if (invertedFirstIterationCheck) {
                        InvertedLoops.increment(graph.getDebug());
                        if (loopsToProtectAfter == null) {
                            loopsToProtectAfter = new ArrayList<>();
                        }
                        loopsToProtectAfter.add(result);
                    }
                    if (graph.getDebug().areCountersEnabled()) {
                        DebugContext.counter("AggressivePartialUnrolling_PreMainPostCreated_" + getPolicies()).increment(graph.getDebug());
                        if (loop.loopBegin().isRotated()) {
                            RotatedLoops.increment(graph.getDebug());
                        }
                    }
                    insertedOne = true;
                }
            }
        }
        if (loopsToProtectAfter != null) {
            LoopsData data = context.getLoopsDataProvider().getLoopsData(graph);
            for (PreMainPostResult res : loopsToProtectAfter) {
                MainLoopProtection protectionPendants = new MainLoopProtection(graph);
                for (Loop loop1 : data.loops()) {
                    if (res.getMainLoop() == loop1.loopBegin() || res.getPostLoop() == loop1.loopBegin()) {
                        // ignore the protection of inverted loops for now
                        assert loop1.detectCounted(true) : "Loop " + loop1 + " must be counted after pre/main/post insertion";
                        protectFirstLoopIteration(loop1, res, protectionPendants);
                    }
                }
            }
            new DeadCodeEliminationPhase().apply(graph);
            if (Assertions.assertionsEnabled()) {
                new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true).apply(graph, context);
            }
        }
        assert validCountedLoopData(graph, context);
        return insertedOne;
    }

    private static class MainLoopProtection {
        final NodeMap<ValueNode> preLoopProxyToMainLoopPhis;

        MainLoopProtection(StructuredGraph g) {
            preLoopProxyToMainLoopPhis = g.createNodeMap();
        }
    }

    private static final boolean DEBUG_MAIN_TO_POST_PHIS = Boolean.parseBoolean(GraalServices.getSavedProperty("debug.graal.AggressivePartialUnroll.debugMainToPostPhis"));
    private static final boolean LOG_EXIT_VALUES = Boolean.parseBoolean(GraalServices.getSavedProperty("debug.graal.AggressivePartialUnroll.logExitValues"));

    /**
     * Protect the main and post loop during partial unrolling of inverted loops. The code uses the
     * pre loop proxies to derive the phi values at the protection diamond.
     *
     * NOTE: This code assumes that every value proxied in the original inverted loop is actually
     * inside the loop, i.e., if the original loop proxies a value that dominates the original loop,
     * we cannot select a proper proxy and the code below will fail. However, if we proxy values
     * that dominate a loop this is an unnecessary proxy and thus considered a structural bug in the
     * IR.
     */
    private static void protectFirstLoopIteration(Loop loop, PreMainPostResult result, MainLoopProtection protection) {
        final LoopBeginNode lb = loop.loopBegin();
        final StructuredGraph graph = lb.graph();
        assert lb.getLoopEndCount() == 1 : lb;
        boolean protectingMainLoop = lb == result.getMainLoop();
        loop.detectCounted(true/* ignore protection, we are building it */);
        assert loop.isCounted() : "Unrolled " + (protectingMainLoop ? "main" : "post") + "  loop " + loop + " not counted";
        final LoopFragment frag = loop.loopBegin() == result.getMainLoop() ? result.getMainLoopFragment() : result.getPostLoopFragment();

        /*
         * If we are protecting the main loop everything is straight forward: we get the proxy
         * values from the pre loop and their pendants in the main loop. For the post loop its more
         * complex, since we need to replace the not entered protection merge's phi value with the
         * newly created phis from the merge of the main loop protection.
         *
         * Special care has to be taken if the same original value in the pre loop has proxies of
         * different kind attached to it: e.g. a node that is used as by a value and guard or a
         * value and memory proxy.
         */
        final LoopFragment preLoopFragment = result.getPreLoopFragment();
        preLoopFragment.loop().invalidateFragmentsAndIVs();
        preLoopFragment.loop().resetCounted();
        preLoopFragment.loop().detectCounted(true);
        assert preLoopFragment.loop().isCounted() : "Preloop should be counted " + preLoopFragment.loop();

        LoopExitNode lex = (LoopExitNode) loop.counted().getCountedExit();
        EconomicMap<ProxyNode, Node> zeroTripProxyValues = EconomicMap.create();
        EconomicMap<ProxyNode, Node> preLoopProxyToThisLoopProxy = EconomicMap.create();
        List<ProxyNode> thisLoopProxiesToVisit = lex.proxies().snapshot();

        for (ProxyNode thisLoopProxy : thisLoopProxiesToVisit) {
            InputType thisLoopProxyType = getInputKind(thisLoopProxy);
            ValueNode thisLoopProxyValue = thisLoopProxy.value();

            // get everything from the pre loop
            EconomicMap<Node, Node> reverseDuplicationMap = frag.reverseDuplicationMap();
            ProxyNode preLoopProxy = (ProxyNode) reverseDuplicationMap.get(thisLoopProxy);
            if (preLoopProxy == null) {
                Node preLoopVal = reverseDuplicationMap.get(thisLoopProxyValue);
                assert preLoopVal != null;
                FilteredNodeIterable<Node> possibleProxies = preLoopVal.usages().filter(x -> x instanceof ProxyNode &&
                                ((ProxyNode) x).proxyPoint() == preLoopFragment.loop().counted().getCountedExit());
                List<Node> possibleProxySnapShot = possibleProxies.snapshot();
                ProxyNode candidate = null;
                for (Node candidateUsages : possibleProxySnapShot) {
                    InputType proxyType = getInputKind(candidateUsages);
                    if (proxyType == thisLoopProxyType) {
                        candidate = (ProxyNode) candidateUsages;
                    }
                }
                assert candidate != null;
                preLoopProxy = candidate;
            }
            if (protectingMainLoop) {
                zeroTripProxyValues.put(thisLoopProxy, preLoopProxy);
                preLoopProxyToThisLoopProxy.put(preLoopProxy, thisLoopProxy);
            } else {
                // get everything from the main loop with already created phis
                ValueNode mainLoopPhi = protection.preLoopProxyToMainLoopPhis.get(preLoopProxy);
                assert mainLoopPhi != null : "Cannot have null for preLoopProxy " + preLoopProxy + " in post loop";
                zeroTripProxyValues.put(thisLoopProxy, mainLoopPhi);
            }
        }
        ProtectionData d = LoopTransformations.protectFirstLoopIteration(loop, zeroTripProxyValues);
        EconomicMap<ProxyNode, PhiNode> phisCreated = d.proxyToPhiMap();
        if (DEBUG_MAIN_TO_POST_PHIS) {
            logValueFlowThroughPreMainPost(graph, result, protectingMainLoop, lb);
        }
        if (protectingMainLoop) {
            MapCursor<ProxyNode, Node> cursor = preLoopProxyToThisLoopProxy.getEntries();
            while (cursor.advance()) {
                PhiNode thisLoopPhi = phisCreated.get((ProxyNode) cursor.getValue());
                assert thisLoopPhi != null : "Cannot have null phi for " + cursor.getKey() + "->" + cursor.getValue();
                protection.preLoopProxyToMainLoopPhis.set(cursor.getKey(), thisLoopPhi);
            }
        }
        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
        }

    }

    private static InputType getInputKind(Node node) {
        if (node instanceof ValueProxyNode) {
            return InputType.Value;
        } else if (node instanceof MemoryProxyNode) {
            return InputType.Memory;
        } else if (node instanceof GuardProxyNode) {
            return InputType.Guard;
        } else {
            throw GraalError.shouldNotReachHere("Unknown proxy type " + node); // ExcludeFromJacocoGeneratedReport
        }
    }

    @SuppressWarnings("deprecation")
    @ExcludeFromJacocoGeneratedReport("Only used for interactive debugging")
    private static void logValueFlowThroughPreMainPost(StructuredGraph graph, PreMainPostResult result, boolean protectingMainLoop, LoopBeginNode lb) {
        if (graph.getDebug().isDumpEnabledForMethod()) {
            if (protectingMainLoop) {
                int phiNr = 0;
                for (ValuePhiNode phi : result.getPreLoop().valuePhis()) {
                    FixedNode f = result.getPreLoop().next();
                    String s = "Entering pre  loop with phi nr:" + phiNr + " phiId:" + phi.getId() + "=%d\n";
                    LogNode as = graph.add(new LogNode(s, phi, null));
                    result.getPreLoop().setNext(null);
                    result.getPreLoop().setNext(as);
                    as.setNext(f);
                    phiNr++;
                }
            }
            int phiNr = 0;
            for (ValuePhiNode phi : lb.valuePhis()) {
                FixedNode f = lb.next();
                String s = protectingMainLoop ? "Entering main loop with phi nr:" + phiNr + " phiId:" +
                                phi.getId() + "=%d\n"
                                : "Entering post loop with phi nr:" + phiNr + " phiId:" + phi.getId() + "=%d\n";
                LogNode as = graph.add(new LogNode(s, phi, null));
                lb.setNext(null);
                lb.setNext(as);
                as.setNext(f);
                phiNr++;
            }
        }
    }

    @SuppressWarnings("try")
    private boolean unrollAllLoops(StructuredGraph graph, CoreProviders context, EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides, Graph.Mark phaseStartMark) {
        if (Options.InsertPreMainPostOnly.getValue(graph.getOptions())) {
            return false;
        }
        assert GraphOrder.assertSchedulableGraph(graph) : "Loops must verify schedule before we start unrolling";
        boolean unrolledOne = false;
        NodeBitMap loopsUnrolled = null;
        EconomicSetNodeEventListener ecs = new EconomicSetNodeEventListener();
        boolean continueOuter = true;
        while (continueOuter) {
            continueOuter = false;
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(ecs)) {
                LoopsData ld = getLoopsData(graph, context, false);
                for (Loop loop : ld.loops()) {
                    if (loopsUnrolled != null && loopsUnrolled.isMarked(loop.loopBegin())) {
                        // ignore the protection of inverted loops for now
                        GraalError.guarantee(loop.detectCounted(true), "Loop %s must be counted after unrolling", loop);
                    } else {
                        loop.detectCounted();
                    }
                }

                for (Loop loop : ld.countedLoops()) {
                    if (!graph.isNew(phaseStartMark, loop.loopBegin())) {
                        /*
                         * only consider main loops created in this application of the phase
                         */
                        continue;
                    }
                    if (!isUnrollableLoop(loop, 1)) {
                        continue;
                    }
                    if (loop.loopBegin().isMainLoop()) {
                        /*
                         * Always unroll main loops at least once: The fact that we have a
                         * pre/main/post loop structure means that the original loop's frequency
                         * passed the test against PartialUnrollMinFrequency, even if the current
                         * main loop's frequency is now just below that threshold.
                         */
                        if (shouldPartiallyUnroll(loop, context) || loop.loopBegin().getUnrollFactor() == 1) {
                            LoopTransformations.partialUnroll(loop, opaqueUnrolledStrides);
                            for (LoopExitNode lex : loop.loopBegin().loopExits().snapshot()) {
                                for (ProxyNode p : lex.proxies().snapshot()) {
                                    if (p.hasNoUsages()) {
                                        GraphUtil.killWithUnusedFloatingInputs(p);
                                    }
                                }
                            }
                            /**
                             * Ideally we would want to run a proper schedule verification here.
                             * However, schedule verification is ran with immutableGraph=true which
                             * means the scheduler will not delete dead floating nodes. Dead
                             * floating nodes will not be part of the schedule of the verified graph
                             * and thus it can be that schedule verification fails because of dead
                             * floating nodes. Schedule verification cannot easily be extended to
                             * understand dead floating nodes here: given we use the verification to
                             * run on potentially broken graphs we cannot tell apart a real dead
                             * node from a broken graph without actually doing the full transitive
                             * closure of users for each node.
                             *
                             * This is a chicken egg problem because running schedule verification
                             * with dead code elimination can result in the verifier deleting nodes
                             * that are only dead because of previous broken transformations.
                             *
                             * Thus, we only run an early schedule here (which is the same used by
                             * the schedule verification) to catch any obviously broken graphs.
                             */
                            if (Assertions.assertionsEnabled()) {
                                SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.EARLIEST, true);
                            }
                            unrolledOne = true;
                            if (loop.counted().isInverted()) {
                                if (loopsUnrolled == null) {
                                    loopsUnrolled = graph.createNodeBitMap();
                                }
                                loopsUnrolled.checkAndMarkInc(loop.loopBegin());
                            }
                            continueOuter = true;
                        }
                    }
                }
            }
            canonicalizer.copyWithoutSimplification().applyIncremental(graph, context, ecs.getNodes());
            ecs.getNodes().clear();
            if (Assertions.assertionsEnabled()) {
                new SchedulePhase(true, graph.getOptions()).apply(graph, context);
            }
        }
        if (unrolledOne) {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After unrolling all  loops");
            if (loopsUnrolled != null) {
                for (Loop loop : getLoopsData(graph, context, false).loops()) {
                    if (loopsUnrolled.isMarked(loop.loopBegin())) {
                        // ignore the protection of inverted loops for now
                        GraalError.guarantee(loop.detectCounted(true), "Loop %s must be counted after unrolling", loop);
                        adaptProtectionLimit(graph, opaqueUnrolledStrides, loop);
                        GraalError.guarantee(loop.detectCounted(), "Loop %s must be counted after unrolling", loop);
                    }
                }
            }
        }
        return unrolledOne;
    }

    /**
     * Adapt the inverted loop entry check limit after unrolling a main loop. The main loop is
     * protected when it is created in
     * {@link AggressivePartialUnrollPhase#insertAllPreMainPost(StructuredGraph, CoreProviders)}
     * with the original stride. Once we unrolled it we need to adapt with the new stride.
     */
    private static void adaptProtectionLimit(StructuredGraph graph, EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides, Loop loop) {
        assert loop.counted().isInverted() : "Loop " + loop + " must be inverted";
        CountedLoopInfo counted = loop.counted();
        OpaqueNode opaque = opaqueUnrolledStrides.get(loop.loopBegin());
        if (opaque != null && opaque.isAlive()) {
            // unrolling is done, we remove the opaque outside the loop, else it may be re-used by
            // another round of unrolling which would be wrong
            opaque.remove();
        }

        ValueNode newConditionIV = counted.limitCheckedPreviousOrRootEntryValue();
        IfNode limitTest = loop.counted().getLimitTest();
        LogicNode condition = limitTest.condition();
        assert condition instanceof BinaryOpLogicNode : condition;
        boolean useX = !loop.whole().contains(((BinaryOpLogicNode) condition).getY());
        assert useX || !loop.whole().contains(((BinaryOpLogicNode) condition).getX()) : ((BinaryOpLogicNode) condition).getX();
        BinaryOpLogicNode copy = (BinaryOpLogicNode) condition.copyWithInputs(true);
        if (useX) {
            copy.setX(graph.addOrUniqueWithInputs(newConditionIV));
        } else {
            copy.setY(graph.addOrUniqueWithInputs(newConditionIV));
        }
        IfNode protectionIf = (IfNode) AbstractBeginNode.prevBegin(loop.loopBegin().forwardEnd()).predecessor();
        protectionIf.setCondition(copy);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After adapting entry check unrolled iterations for loop %s", loop);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (!graph.hasLoops()) {
            return;
        }

        Mark before = graph.getMark();
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        prepareGraphForUnrolling(graph, context, canonicalizer);
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            LoopsData l = getLoopsData(graph, context);
            boolean recomputeLoopsData = false;
            for (Loop loop : l.countedLoops()) {
                if (LoopUtility.createDeoptCountedLoopExitNode(loop)) {
                    recomputeLoopsData = true;
                }
                if (loop.isCounted() && LoopTransformations.countedLoopExitConditionHasMultipleUsages(loop)) {
                    LogicNode condition = loop.counted().getLimitTest().condition();
                    for (Node usage : condition.usages().snapshot()) {
                        if (usage == loop.counted().getLimitTest()) {
                            // we leave the limit test usage untouched
                            continue;
                        }
                        ValueNode copy = (ValueNode) condition.copyWithInputs(true);
                        // the copy has one opaque input to ensure we do not gvn between
                        // pre/main/post insertion and actual unrolling
                        assert copy instanceof CompareNode : Assertions.errorMessage(copy, "Must be a compare node to be a counted loop");
                        CompareNode compare = (CompareNode) copy;
                        compare.setX(graph.addWithoutUnique(new OpaqueValueNode(compare.getX())));
                        usage.replaceAllInputs(condition, copy);
                    }
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After making condition usages of %s unique", condition);
                    recomputeLoopsData = true;
                }
            }
            if (recomputeLoopsData) {
                l = getLoopsData(graph, context);
            }
            for (Loop loop : l.countedLoops()) {
                if (!isUnrollableLoop(loop, Integer.MAX_VALUE)) {
                    continue;
                }
                int countedBefore = 0;
                if (Assertions.assertionsEnabled()) {
                    countedBefore = getLoopsData(graph, context).countedLoops().size();
                }
                if (loop.loopBegin().getLoopEndCount() > 1 && shouldPartiallyUnroll(loop, context)) {
                    // merge them in preparation for unrolling
                    LoopUtility.mergeLoopEnds(loop.loopBegin());
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After merging loop ends for loop %s", loop.loopBegin());
                }
                int countedAfter = 0;
                if (Assertions.assertionsEnabled()) {
                    countedAfter = getLoopsData(graph, context).countedLoops().size();
                    assert countedBefore == countedAfter : "Merging loop ends made a counted loop " + loop + " non counted";
                }
            }
            unroll(graph, context);
            for (OpaqueNode o : graph.getNewNodes(before).filter(OpaqueNode.class)) {
                if (o.isAlive()) {
                    /*
                     * Removes remaining OpaqueNodes created in
                     * LoopUtility#preserveCounterStampsForDivAfterUnroll.
                     */
                    o.remove();
                }
            }
        }
        if (!listener.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getNodes());
            new InjectLoopCounterStampsPhase().apply(graph, context);
        }

        logIVAndExitVals(graph, context);
    }

    private static void logIVAndExitVals(StructuredGraph graph, CoreProviders context) {
        if (!LOG_EXIT_VALUES) {
            return;
        }
        if (graph.isAfterStage(StageFlag.HIGH_TIER_LOWERING)) {
            for (Loop lex : getLoopsData(graph, context).loops()) {
                if ((lex.loopBegin().isPreLoop() || lex.loopBegin().isPostLoop() || lex.loopBegin().isMainLoop()) && lex.detectCounted()) {
                    EconomicMap<Node, InductionVariable> ivs = lex.getInductionVariables();
                    for (InductionVariable iv : ivs.getValues()) {
                        FixedNode f = lex.loopBegin().next();
                        ValueNode v1 = iv.valueNode();
                        ValueNode v2 = iv.exitValueNode();
                        String s = "[AggressivePartialUnroll Marker] Entering " + lex.loopBegin() + " loop with iv=" + iv + " with value node " + v1 + "=%d and exitValue" + v2 + "=%d\n";
                        LogNode as = graph.add(new LogNode(s, v1, v2));
                        lex.loopBegin().setNext(null);
                        lex.loopBegin().setNext(as);
                        as.setNext(f);
                    }

                }
                FixedNode f = lex.loopBegin().next();
                String s = "[AggressivePartialUnroll Marker] Entering " + lex.loopBegin() + " loop with maxTripCount=%d\n";
                LogNode as = graph.add(new LogNode(s, lex.counted().maxTripCountNode(), null));
                lex.loopBegin().setNext(null);
                lex.loopBegin().setNext(as);
                as.setNext(f);

            }
        }
    }

    private static void prepareGraphForUnrolling(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
        /*
         * Proxies for inverted loops: detecting when proxying a value is not necessary since it
         * dominates the original loop is very complex, thus this step is necessary to remove the
         * created, obsolete proxies.
         */
        LoopUtility.removeObsoleteProxies(graph, context, canonicalizer);
    }

    @SuppressWarnings("try")
    private void unroll(StructuredGraph graph, CoreProviders context) {
        if (graph.hasLoops()) {
            Graph.Mark phaseStartMark = graph.getMark();
            EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
            // first insert pre main post
            boolean inserted = false;
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                Graph.Mark mark = graph.getMark();
                inserted = insertAllPreMainPost(graph, context);
                if (inserted && !listener.getNodes().isEmpty()) {
                    new DeadCodeEliminationPhase().apply(graph);
                    assert checkCounted(graph, context, mark);
                    /*
                     * We run a canonicalization without simplification here since simplification
                     * can cause counted loops to become none counted by partially unrolling AND
                     * optimizing them: special case integer add exact loop counters have a
                     * simplification path creating the split version of the node already, thus we
                     * run simplification after having run the entire rest
                     */
                    canonicalizer.copyWithoutSimplification().applyIncremental(graph, context, listener.getNodes());
                    listener.getNodes().clear();
                    if (Assertions.assertionsEnabled()) {
                        /*
                         * Memory schedule verification & sanity check that there are no wrong (dead
                         * for example) nodes in the graph.
                         */
                        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph, context);
                    }
                }
            }
            if (!inserted) {
                return;
            }
            // then unroll
            boolean changed = true;
            EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides = EconomicMap.create(Equivalence.IDENTITY);
            /*
             * During unrolling of multi-exit loops we want to avoid the aggressive duplication of
             * merges before loop-ends/exits to avoid loosing common nodes to merge early exits on.
             */
            CanonicalizerPhase withoutSimplification = canonicalizer.copyWithoutSimplification();
            while (changed) {
                changed = false;
                try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                    if (unrollAllLoops(graph, context, opaqueUnrolledStrides, phaseStartMark) && !listener.getNodes().isEmpty()) {
                        new DeadCodeEliminationPhase().apply(graph);
                        withoutSimplification.applyIncremental(graph, context, listener.getNodes());
                        listener.getNodes().clear();
                        changed = true;
                    }
                }
            }
            if (opaqueUnrolledStrides.size() > 0) {
                try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                    for (OpaqueNode opaque : opaqueUnrolledStrides.getValues()) {
                        if (opaque.isAlive()) {
                            opaque.remove();
                        }
                    }
                    if (!listener.getNodes().isEmpty()) {
                        canonicalizer.applyIncremental(graph, context, listener.getNodes());
                    }
                }
            }
        }
    }

    private static boolean checkCounted(StructuredGraph graph, CoreProviders context, Graph.Mark mark) {
        LoopsData dataCounted;
        dataCounted = getLoopsData(graph, context, false);
        for (Loop anyLoop : dataCounted.loops()) {
            if (graph.isNew(mark, anyLoop.loopBegin())) {
                anyLoop.detectCounted();
                assert anyLoop.isCounted() || anyLoop.loopBegin().isPreLoop() : "pre/post transformation loses counted loop " + anyLoop.loopBegin();
            }
        }
        return true;
    }

    @Override
    public float codeSizeIncrease() {
        return Options.PartialUnrollMaxIterationsMidTier.getDefaultValue() * 2;
    }
}
