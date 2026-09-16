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

import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.createNewIntBaseIV;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.ivCanOverflow32Bit;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.ivContains;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.toLong;
import static jdk.graal.compiler.phases.common.util.LoopUtility.isInt;
import static jdk.graal.compiler.phases.common.util.LoopUtility.isLong;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.duplication.opt.BudgetCostModel;
import jdk.graal.compiler.duplication.opt.OptimizationEffect;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventListener;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase.LoopSafepointPlan;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopBeginNode.SafepointState;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MemoryProxyNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopExpandableNode;
import jdk.graal.compiler.nodes.loop.LoopFragment;
import jdk.graal.compiler.nodes.loop.LoopFragmentInside;
import jdk.graal.compiler.nodes.loop.LoopFragmentWhole;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.LazyValue;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.contract.NodeCostUtil;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.LogNode;

/**
 * Implements the GraalVM Counted Loop Strip Mining optimization.
 *
 * Strip mining, also known as loop tiling or loop sectioning, is an optimization that splits a
 * long-running loop into a nested loop, where the inner loop runs for a bounded time. The term
 * strip mining is inspired from mining coal, for example, with the excavator, which uses a bucket
 * (or bucket wheel) to "strip" the coal. This phase specifically handles counted loops, that is,
 * loops for which a {@linkplain Loop#detectCounted() counted loop structure} can be derived; for
 * non-counted loops, see {@link NonCountedStripMiningPhase}.
 *
 * This enables a number of further optimizations:
 *
 * <ul>
 * <li>Safepoints can be moved into the outer loop by {@link LoopSafepointEliminationPhase} to
 * reduce safepoint polling overhead. With the right value for the outer loop stride, a reasonable
 * time-to-safepoint latency can be ensured, which is particularly important for low pause time
 * garbage collectors such as ZGC and Shenandoah.
 *
 * <li>Vectorization of the inner counted loop by {@link LoopVectorizationPhase}.
 * </ul>
 *
 * For example, this loop
 *
 * <pre>
 * for (long i = init; i < limit; i += stride) {
 *     use(i);
 * }
 * </pre>
 *
 * is transformed such that the inner loop will iterate stripMax iterations (only iterating less on
 * the final outer trip). Any uses of the original counter i are replaced with the counter i_. This
 * brings the inner loop counter j within int bounds, so it can be reduced from long to int.
 *
 * <pre>
 * final long stripMax = (long) CountedStripMiningInnerLoopTrips;
 * for (long i = init; i < limit;) {
 *     long innerTrips = i < limit - stripMax ? stripMax : limit - i;
 *     long i_ = i;
 *     for (long j = 0; j |<| innerTrips; j++) {
 *         use(i_);
 *         i_ += stride;
 *     }
 *     i = i_;
 * }
 * </pre>
 *
 * For down-counted loops, such as
 *
 * <pre>
 * for (long i = init; i > limit; i -= stride) {
 *     use(i);
 * }
 * </pre>
 *
 * the transformed inner loop becomes up-counted.
 *
 * <pre>
 * final long stripMax = (long) CountedStripMiningInnerLoopTrips;
 * for (long i = init; i > limit;) {
 *     long innerTrips = i > limit + stripMax ? stripMax : i - limit;
 *     long i_ = i;
 *     for (long j = 0; j |<| innerTrips; j++) {
 *         use(i_);
 *         i_ -= stride;
 *     }
 *     i = i_;
 * }
 * </pre>
 */
public class CountedStripMiningPhase extends BasePhase<MidTierContext> {
    public static class Options {
        //@formatter:off
        @Option(help = "The max number of iterations the counted inner loop takes.", type = OptionType.Debug)
        public static final OptionKey<Integer> CountedStripMiningInnerLoopTrips = new OptionKey<>(1024 * 4);
        @Option(help = "Minimal frequency to consider a loop for strip mining.", type = OptionType.Debug)
        public static final OptionKey<Integer> CountedStripMiningMinFrequency = new OptionKey<>(4);
        @Option(help = "Print counter phi values on each outer and inner loop iteration.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CountedStripMiningLogCounters = new OptionKey<>(false);
        @Option(help = "Strip mine inverted loops.", type = OptionType.Debug)
        public static final OptionKey<Boolean> StripMineInvertedLoops = new OptionKey<>(true);
        @Option(help = "Rewrite the counter of a strip mined loop to have a 32bit type.", type = OptionType.Debug)
        public static final OptionKey<Boolean> RewriteStripMinedCounterTo32Bit = new OptionKey<>(true);
        @Option(help = "Force strip mining of all loops that can be strip mined.", type = OptionType.Debug)
        public static final OptionKey<Boolean> StripMineALot = new OptionKey<>(false);
        @Option(help = "Code size budget of the strip mining transformation in terms of NodeCostSize.", type = OptionType.Debug)
        public static final OptionKey<Double> CountedStripMiningBudget = new OptionKey<>(0.1);
        @Option(help = "See CountedStripMiningBudget.", type = OptionType.Debug)
        public static final OptionKey<Double> CountedStripMiningBudgetHotCode = new OptionKey<>(2.5);
        @Option(help = "Code size in NodeSize defining when code size heuristics starts capping strip mining.", type = OptionType.Debug)
        public static final OptionKey<Integer> CountedStripMiningNodeSizeSmallGraphs = new OptionKey<>(2000);
        @Option(help = "Prefer a vector safepoint over a strip mined, safepoint free, inner loop.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PreferVectorSafepoint = new OptionKey<>(false);
        //@formatter:on
    }

    /**
     * Dump a message to {@link TTY} when this optimization strip-mines a loop.
     */
    private static final boolean TTY_PRINT_STRIP_MINING = false;

    public static final CounterKey FailedCanStripMine = DebugContext.counter("CountedStripMining_FailedCanStripMine");
    public static final CounterKey FailedShouldStripMine = DebugContext.counter("CountedStripMining_FailedShouldStripMine");
    public static final CounterKey FailedFrequency = DebugContext.counter("CountedStripMining_Failed_FrequencyBelowStripMax");
    public static final CounterKey UsedInvertedSchedule = DebugContext.counter("CountedStripMining_UsedInvertedSchedule");

    private final CanonicalizerPhase canonicalizer;
    private final Predicate<Loop> shouldStripMine;
    private final SafepointPlanProvider safepointPlanProvider;

    @FunctionalInterface
    public interface SafepointPlanProvider {
        LoopSafepointPlan optimize(StructuredGraph graph, MidTierContext context, LoopsData loopsData);
    }

    public CountedStripMiningPhase(CanonicalizerPhase canonicalizer) {
        this(canonicalizer, null, (graph, context, loopsData) -> new LoopSafepointEliminationPhase.SafepointOptimizer(graph, context, true).optimizeSafepoints(loopsData));
    }

    public CountedStripMiningPhase(CanonicalizerPhase canonicalizer, Predicate<Loop> shouldStripMine) {
        this(canonicalizer, shouldStripMine, (graph, context, loopsData) -> new LoopSafepointEliminationPhase.SafepointOptimizer(graph, context, true).optimizeSafepoints(loopsData));
    }

    public CountedStripMiningPhase(CanonicalizerPhase canonicalizer, Predicate<Loop> shouldStripMine, SafepointPlanProvider safepointPlanProvider) {
        this.canonicalizer = canonicalizer;
        this.shouldStripMine = shouldStripMine;
        this.safepointPlanProvider = safepointPlanProvider;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState));
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        if (!graphState.isAfterStage(StageFlag.STRIP_MINING)) {
            super.updateGraphState(graphState);
            graphState.setAfterStage(StageFlag.STRIP_MINING);
        }
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        assert ensureSaneGraph(graph);
        LoopsData dataBeforeProlog = prolog(graph, context, canonicalizer);
        final EconomicSet<LoopBeginNode> stripMinedLoops = performStripMiningOptimization(graph, context, dataBeforeProlog);
        if (stripMinedLoops != null && stripMinedLoops.size() > 0) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            optimizeIVs(graph, context, stripMinedLoops, canonicalizer, loopsData);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After strip mining");

            /*
             * Compile time optimization: optimize IV only touches floating nodes and no fixed nodes
             * so we can just recompute the fragments and ivs.
             */
            loopsData.loops().forEach(x -> x.invalidateFragmentsAndIVs());
            LoopUtility.removeObsoleteProxies(graph, context, canonicalizer, loopsData);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After removing obsolete proxies");

        }
        assert assertStripMinedInnerLoopsCounted(graph, context, stripMinedLoops);
    }

    private static final class CFGTouchedListener extends NodeEventListener {

        boolean cfgInvalidated;

        @Override
        public void changed(NodeEvent e, Node node) {
            if (e == NodeEvent.NODE_ADDED || e == NodeEvent.NODE_REMOVED) {
                if (node instanceof FixedNode) {
                    cfgInvalidated = true;
                }
            }
        }
    }

    /**
     * Structurally prepare a graph for the strip mining optimization: this means merge loop ends,
     * remove unnecessary {@link ProxyNode}, insert {@link BeginNode} so all loop bodies start with
     * a fixed with next nodes, and try to create {@link LoopExitNode} for deopt counted loops.
     */
    @SuppressWarnings({"try"})
    private static EconomicMap<ValuePhiNode, ValuePhiNode> prepareGraphForStripMining(StructuredGraph graph, MidTierContext context, EconomicSet<LoopBeginNode> stripMiningCandidates,
                    CanonicalizerPhase canonicalizer, LoopsData computedData) {

        CFGTouchedListener cfgValid = new CFGTouchedListener();
        try (NodeEventScope s = graph.trackNodeEvents(cfgValid)) {
            LoopUtility.removeObsoleteProxies(graph, context, canonicalizer, computedData);
        }
        boolean cfgInvalidated = cfgValid.cfgInvalidated;
        // first merge loop ends, this changes the CFG, then do any loop ex related changes
        for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
            if (stripMiningCandidates.contains(lb)) {
                cfgInvalidated = LoopUtility.mergeLoopEnds(lb) || cfgInvalidated;
            }
        }
        LoopsData loopsData = null;
        if (!cfgInvalidated) {
            loopsData = computedData;
            // if loops data is given it is guaranteed to have computed counted loops already
        } else {
            loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();
        }
        for (Loop lex : loopsData.countedLoops()) {
            if (stripMiningCandidates.contains(lex.loopBegin())) {
                for (Node inside : lex.inside().nodes()) {
                    if (inside instanceof FloatingIntegerDivRemNode<?>) {
                        /*
                         * Floating division nodes and strip mining: Strip mining as a whole has the
                         * effect that it may destroy stamps created by the inject loop counter
                         * stamps phase. We do not repair them at the moment and reset them to the
                         * original stamp values. While this in general may only have performance
                         * relevant implications for floating division nodes we must guarantee the
                         * divisor stamp never includes 0. So in order to not trigger false positive
                         * assertions later in the compiler we inject a few pi stamps with the
                         * original stamps (not the PI stamps since there can be nodes in between).
                         */
                        FloatingIntegerDivRemNode<?> idiv = (FloatingIntegerDivRemNode<?>) inside;
                        ValueNode divisor = idiv.getY();
                        IntegerStamp divisorStamp = (IntegerStamp) divisor.stamp(NodeView.DEFAULT);
                        assert !divisorStamp.contains(0) : divisorStamp;
                        ValueNode dividend = idiv.getX();
                        IntegerStamp dividendStamp = (IntegerStamp) dividend.stamp(NodeView.DEFAULT);
                        /*
                         * Ideally we would just like to create the necessary pi nodes and retain
                         * them to after the transformations and let them be handled later on.
                         * However, during strip mining we have to run canonicalizations to cleanup
                         * left-over nodes of the transformation. This would remove the pi nodes
                         * again if new stamps have not been inferred for all involved nodes of the
                         * strip mined loop. Thus, to keep the necessary amount of work to a minimum
                         * we create OpaqueNodes for the divisors. These nodes have unrestricted
                         * stamps, thus the pi usages are not optimized away immediately. After all
                         * canonicalizations have been performed and the new stamps propagated
                         * through the loop nodes we can remove them and the necessary pi nodes
                         * survive.
                         */
                        ValueNode opaqueDivisor = graph.addWithoutUnique(new OpaqueValueNode(divisor));
                        PiNode piY = graph.addWithoutUnique(new PiNode(opaqueDivisor, divisorStamp,
                                        AbstractBeginNode.prevBegin(lex.entryPoint())));
                        idiv.setY(piY);

                        ValueNode opaqueDividend = graph.addWithoutUnique(new OpaqueValueNode(dividend));
                        PiNode piX = graph.addWithoutUnique(new PiNode(opaqueDividend, dividendStamp, AbstractBeginNode.prevBegin(lex.entryPoint())));
                        idiv.setX(piX);

                        if (idiv.getGuard() == null) {
                            idiv.setGuard(graph.addWithoutUnique(new MultiGuardNode(piX.getGuard().asNode(), piY.getGuard().asNode())));
                        }
                    }
                }
                if (lex.counted().isInverted()) {
                    graph.addAfterFixed(lex.loopBegin(), graph.add(new BeginNode()));
                }
                LoopUtility.createDeoptCountedLoopExitNode(lex);
                /*
                 * For all exit states ensure they are unique, i.e., ensure no virtual state mapping
                 * is used by any other code. Any other code that could use them would come after
                 * the loop exit, i.e., is dominated by it. This code could force that we have to
                 * create complex proxy structures, avoid this by simply using a unique state per
                 * loop exit.
                 */
                for (LoopExitNode loopExit : lex.loopBegin().loopExits()) {
                    loopExit.setStateAfter(loopExit.stateAfter().duplicateWithVirtualState());
                }
            }
        }

        /*
         * Floating division and integer stamps: Injecting loop counter stamps can also change the
         * stamp of value proxy nodes. After introducing the outer loop we create phi of phi nodes
         * that can destroy the improved stamp view because of cyclic phi stamps. Thus, we need to
         * preserve proxy stamps.
         */
        for (Loop lex : loopsData.countedLoops()) {
            if (stripMiningCandidates.contains(lex.loopBegin())) {
                LoopBeginNode lb = lex.loopBegin();
                for (LoopExitNode loopExit : lb.loopExits()) {
                    for (ValueProxyNode vp : loopExit.proxies().filter(ValueProxyNode.class).snapshot()) {
                        Stamp proxyStamp = vp.stamp(NodeView.DEFAULT);
                        if (proxyStamp.unrestricted().tryImproveWith(proxyStamp) != null) {
                            OpaqueNode opaqueVP = graph.addWithoutUnique(new OpaqueValueNode(vp));
                            PiNode betterStampPi = graph.addWithoutUnique(new PiNode(opaqueVP, proxyStamp, loopExit));
                            vp.replaceAtMatchingUsages(betterStampPi, proxyUsage -> {
                                if (proxyUsage == opaqueVP) {
                                    return false;
                                }
                                if (proxyUsage instanceof VirtualState) {
                                    if (loopExit.stateAfter().isPartOfThisState((VirtualState) proxyUsage)) {
                                        return false;
                                    }
                                }
                                return true;
                            });
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing usages of proxy %s with %s to preserve stamp.", vp, betterStampPi);
                        }
                    }
                }
            }
        }
        /**
         * Loop phis, inject loop counter stamps and strip mining: Graal uses
         * InjectLoopCounterStamps to create better stamps for loop phis during compilation. In
         * strip mining we re-use many nodes of the original loop, some of which are the loop phis
         * with the better counter stamps. Since strip mining rewrites all loops to be upcounted
         * loops we need to ensure no wrong transformations happen because of the old out-dated hand
         * set stamps of loop phis. Since we are dealing with cycles here we need to rewrite all the
         * loop phis and their backedge values to have clean, unoptimized stamps.
         */
        EconomicMap<ValuePhiNode, ValuePhiNode> newPhisToOriginalOnes = EconomicMap.create();
        for (LoopBeginNode lb : stripMiningCandidates) {
            for (ValuePhiNode phi : lb.valuePhis().snapshot()) {
                assert phi.valueCount() == 2 : Assertions.errorMessage(phi, phi.values());
                ValuePhiNode copy = graph.addWithoutUnique(new ValuePhiNode(phi.stamp(NodeView.DEFAULT).unrestricted(), lb));
                copy.addInput(phi.valueAt(0));
                copy.addInput(phi.valueAt(1));
                phi.replaceAtUsagesAndDelete(copy);
                copy.valueAt(1).inferStamp();
                newPhisToOriginalOnes.put(copy, phi);
            }
        }

        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Counted strip mining after graph preparation");

        return newPhisToOriginalOnes;
    }

    /**
     * Find, using structural and heuristic checks, candidate loops for strip mining.
     */
    private EconomicSet<LoopBeginNode> findStripMiningCandidates(StructuredGraph graph, MidTierContext context, LoopsData loopsData) {
        EconomicSet<LoopBeginNode> stripMiningCandidates = EconomicSet.create();
        final DebugContext debug = graph.getDebug();
        final LazyValue<ScheduleResult> lazySched = new LazyValue<>(new Supplier<ScheduleResult>() {

            @Override
            public ScheduleResult get() {
                SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.EARLIEST);
                return graph.getLastSchedule();
            }
        });
        // we gather the safepoint plan, but never apply anything with it
        final LoopSafepointEliminationPhase.LoopSafepointPlan graphSafepointOptimizationPlan = safepointPlanProvider.optimize(graph, context, loopsData);
        for (Loop loop : loopsData.countedLoops()) {
            if (!canStripMine(loop, graph, lazySched, context)) {
                FailedCanStripMine.increment(debug);
                continue;
            }
            if (!shouldStripMine(loop, graphSafepointOptimizationPlan, lazySched, graph, context)) {
                FailedShouldStripMine.increment(debug);
                continue;
            }
            stripMiningCandidates.add(loop.loopBegin());
        }
        double codeSizeIncreaseFactor = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.CountedStripMiningBudget, Options.CountedStripMiningBudgetHotCode);
        return pruneStripMiningCandidates(stripMiningCandidates, loopsData.getCFG(), codeSizeIncreaseFactor);
    }

    /**
     * The strip mining transformations, like other optimizations, comes at a code size cost. For
     * strip mining the cost is IR size and final code size. For each original {@link PhiNode},
     * strip mining creates an outer phi. In addition, the condition, the outer loop header, back
     * edges, value proxies and a few other nodes increase code size as well.
     *
     * This method processes the loops in {@code stripMiningCandidates} to select those for which
     * the aggregated {@linkplain #estimateCodeSizeIncrease estimated} node size increase of strip
     * mining does not exceed the budget specified by {@link Options#CountedStripMiningBudget}.
     *
     * @return the selected loops for strip mining that stay within budget
     */
    public static EconomicSet<LoopBeginNode> pruneStripMiningCandidates(EconomicSet<LoopBeginNode> stripMiningCandidates, ControlFlowGraph cfg, double codeSizeIncreaseFactor) {
        int graphsize = NodeCostUtil.computeGraphSize(cfg.graph);
        EconomicSet<LoopBeginNode> prunedCandidates = stripMiningCandidates;
        if (graphsize > Options.CountedStripMiningNodeSizeSmallGraphs.getValue(cfg.graph.getOptions())) {
            LoopBeginNode[] loops = stripMiningCandidates.toArray(new LoopBeginNode[stripMiningCandidates.size()]);
            prunedCandidates = EconomicSet.create();
            Arrays.sort(loops, (x, y) -> Double.compare(cfg.blockFor(y).getRelativeFrequency(), cfg.blockFor(x).getRelativeFrequency()));
            double maxBudget = graphsize * codeSizeIncreaseFactor;
            BudgetCostModel budget = new BudgetCostModel(maxBudget);
            for (LoopBeginNode cur : loops) {
                int codeSizeIncrease = estimateCodeSizeIncrease(cur);
                OptimizationEffect effect = budget.potentialOpt(1, codeSizeIncrease);
                // always perform a single strip mining, if the budget is full after that is fine
                prunedCandidates.add(cur);
                if (effect.budgetExceeded()) {
                    break;
                }
                budget.applyLastOp();
            }
        }
        if (cfg.graph.getDebug().areCountersEnabled()) {
            int originalSize = stripMiningCandidates.size();
            int prunedSize = prunedCandidates.size();
            Pruned.add(cfg.graph.getDebug(), originalSize - prunedSize);
        }
        return prunedCandidates;
    }

    public static final CounterKey Pruned = DebugContext.counter("StripMining_Pruned");

    /**
     * Estimates the code size increase of the strip mining transformation on a single loop.
     */
    private static int estimateCodeSizeIncrease(LoopBeginNode loopBegin) {
        int codeSizeIncrease = 0;
        int irIncreaseOnly = 0;
        irIncreaseOnly += 1; // LoopBegin
        irIncreaseOnly += 1; // FrameState
        irIncreaseOnly += loopBegin.loopExits().count(); // Loop exit nodes
        irIncreaseOnly += loopBegin.loopEnds().count(); // Loop end nodes
        irIncreaseOnly += loopBegin.loopEnds().count(); // Framestate per exit
        codeSizeIncrease += IfNode.TYPE.size().value;
        for (LoopExitNode lex : loopBegin.loopExits()) {
            irIncreaseOnly += lex.proxies().count();
        }
        for (PhiNode phi : loopBegin.phis()) {
            codeSizeIncrease += phi.estimatedNodeSize().value;
            // those have to be proxied
            irIncreaseOnly += 1;
        }
        /*
         * Framestates: Strip mining requires us to build a new state for the inner loop header and
         * the inner-to-outer loop exit node. Thus we also need to count them: they are deep copies
         * and can be very complex.
         */
        CountVirtualNodes headerStateCount = new CountVirtualNodes();
        loopBegin.stateAfter().applyToVirtual(headerStateCount);
        irIncreaseOnly += headerStateCount.count;
        for (LoopExitNode lex : loopBegin.loopExits()) {
            CountVirtualNodes lexStateCount = new CountVirtualNodes();
            lex.stateAfter().applyToVirtual(lexStateCount);
            irIncreaseOnly += lexStateCount.count;
        }
        return (int) (codeSizeIncrease + irIncreaseOnly * 0.75);
    }

    private static final class CountVirtualNodes implements VirtualState.VirtualClosure {
        int count;

        @Override
        public void apply(VirtualState node) {
            count++;
        }
    }

    /**
     * Actually perform the strip mining optimization on all candidate loops that satisfy any
     * heuristical or structural preconditions.
     */
    private static EconomicSet<LoopBeginNode> stripMineCandidateLoops(StructuredGraph graph, CoreProviders context, EconomicSet<LoopBeginNode> stripMiningCandidates, NodeBitMap obsoleteGuards,
                    EconomicMap<ValuePhiNode, ValuePhiNode> newUnrestrictedToOldPhis) {
        EconomicSet<LoopBeginNode> stripMined = EconomicSet.create();
        outer: while (true) { // TERMINATION ARGUMENT: processing a fixed set of candidate loops
            CompilationAlarm.checkProgress(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();
            for (Loop lex : loopsData.countedLoops()) {
                if (stripMiningCandidates.contains(lex.loopBegin())) {
                    if (canStripMineAfterPreparation(lex)) {
                        if (TTY_PRINT_STRIP_MINING) {
                            TTY.println("Strip mining %s in %s, %s with counted exit %s", lex, graph.method(), graph.compilationId(), lex.counted().getCountedExit());
                        }
                        performStripMiningTransformationForLoop(lex, graph, obsoleteGuards, newUnrestrictedToOldPhis);
                        graph.getOptimizationLog().report(CountedStripMiningPhase.class, "LoopStripMining", lex.loopBegin());
                        stripMiningCandidates.remove(lex.loopBegin());
                        stripMined.add(lex.loopBegin());
                        continue outer;
                    } else {
                        stripMiningCandidates.remove(lex.loopBegin());
                    }
                }
            }
            break;
        }
        return stripMined;
    }

    /**
     * In very infrequent patterns (deopt counted loops with code between the exit condition and the
     * deopt) we cannot create a simple loop exit for the deopt and thus still cannot strip mine
     * deopt counted candidate loops.
     */
    private static boolean canStripMineAfterPreparation(Loop lex) {
        return lex.counted().getCountedExit() instanceof LoopExitNode;
    }

    @SuppressWarnings({"try"})
    private EconomicSet<LoopBeginNode> performStripMiningOptimization(StructuredGraph graph, MidTierContext context, LoopsData previousData) {
        final CanonicalizerPhase canonWithoutSimplification = canonicalizer.copyWithoutSimplification();
        final EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        EconomicSet<LoopBeginNode> stripMinedLoops = null;
        // remember all guard nodes that must be removed after strip mining, see cleanupGuards for
        // more details
        NodeBitMap obsoleteGuards = null;
        Graph.Mark before = graph.getMark();
        try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
            final LoopsData loopsData = previousData != null ? previousData : context.getLoopsDataProvider().getLoopsData(graph);

            /*
             * If prolog ran but returned a loops data no speculative guard movement was ran so also
             * no counted loops have been computed, so nothing to reset only compute counted loops.
             */
            loopsData.detectCountedLoops();

            final EconomicSet<LoopBeginNode> candidateLoops = findStripMiningCandidates(graph, context, loopsData);

            if (!candidateLoops.isEmpty()) {
                obsoleteGuards = graph.createNodeBitMap();
                EconomicMap<ValuePhiNode, ValuePhiNode> newUnrestrictedToOldPhis = prepareGraphForStripMining(graph, context, candidateLoops, canonicalizer, loopsData);
                stripMinedLoops = stripMineCandidateLoops(graph, context, candidateLoops, obsoleteGuards, newUnrestrictedToOldPhis);
            }
        }
        if (!ec.getNodes().isEmpty()) {
            // run canon without simplification to have still single loop ends
            canonWithoutSimplification.applyIncremental(graph, context, ec.getNodes());
        }
        // cleanup left over guards
        ec.getNodes().clear();
        cleanUpGuards(ec, obsoleteGuards, graph, context, canonWithoutSimplification);
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After cleaning up guards");
        removeOpaques(graph, before);
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After cleaning up opaque values");

        return stripMinedLoops;
    }

    private static void removeOpaques(StructuredGraph graph, Graph.Mark before) {
        for (Node newNode : graph.getNewNodes(before)) {
            if (newNode instanceof OpaqueNode) {
                ((OpaqueNode) newNode).remove();
            }
        }
    }

    /**
     * Run a necessary optimization prolog for this phase in order to make better heuristic
     * decisions. Strip mining can be beneficial if 64bit guards can be hoisted. For this to be
     * effective any trivially optimizable guards should already be cleaned up before.
     *
     * Returns a computed set of {@link LoopsData} in case no prolog graph rewrite happened,
     * otherwise returns {@code null}. If a non-null object is returned it is guaranteed that
     * counted loops {@link LoopsData#detectCountedLoops()} have not been computed yet.
     */
    private static LoopsData prolog(StructuredGraph graph, MidTierContext context, CanonicalizerPhase canonicalizer) {
        if (graph.hasLoops() && GraalOptions.SpeculativeGuardMovement.getValue(graph.getOptions())) {
            LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
            boolean runProlog = false;
            EconomicSet<Loop> loopsToCanon = null;
            for (Loop loop : ld.loops()) {
                if (canHoistLongRangeCheckAfterStripMining(loop)) {
                    runProlog = true;
                    if (loopsToCanon == null) {
                        loopsToCanon = EconomicSet.create();
                        loopsToCanon.add(loop);
                    }
                    break;
                }
            }
            if (runProlog && graph.getSpeculationLog() != null) {
                /*
                 * Run speculative guard motion before strip mining to move any guards that are
                 * anyway already loop invariant out of the loop before trying to strip mine
                 * something to move guards while in reality this is no longer necessary.
                 */
                ld.detectCountedLoops();
                NodeBitMap toProcess = graph.createNodeBitMap();
                loopsToCanon.forEach(x -> toProcess.markAll(x.inside().nodes()));
                SpeculativeGuardMovementPhase.performSpeculativeGuardMovement(context, graph, ld, toProcess);
                for (Loop toCanon : loopsToCanon) {
                    canonicalizer.applyIncremental(graph, context, toCanon.inside().nodes());
                }
                // we ran the canon, we cannot guarantee the graph before was canonical thus
                // potential this can cause larger changes that require a new CFG
                return null;
            }
            // no transform happened, loopsdata not touched
            return ld;
        }
        // no loops or speculative guard movement disabled
        return null;
    }

    private static boolean assertStripMinedInnerLoopsCounted(StructuredGraph graph, CoreProviders context, EconomicSet<LoopBeginNode> loopsToStripMine) {
        if (Assertions.assertionsEnabled() && loopsToStripMine != null) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop loop : loopsData.loops()) {
                if (loopsToStripMine.contains(loop.loopBegin())) {
                    assert loop.detectCounted() : "Strip mined inner loop must be counted afterwards: " + loop.loopBegin();
                }
            }
        }
        return true;
    }

    /**
     * Tries to optimize the IVs of the strip mined loop by expressing them as offset IVs of the
     * base IV that is the counter of the strip mined inner loop. See
     * {@link #expressIVsViaLoopCounterInIntRange} for details.
     */
    @SuppressWarnings({"try", "unused"})
    private static void optimizeIVs(StructuredGraph graph, CoreProviders context, EconomicSet<LoopBeginNode> stripMinedLoops, CanonicalizerPhase canonicalizer, LoopsData loopsData) {
        if (stripMinedLoops == null) {
            // no strip mining happened
            return;
        }
        if (!Options.RewriteStripMinedCounterTo32Bit.getValue(graph.getOptions())) {
            // we do not try to express the strip mined inner loop's counter as a 32 bit int IV
            return;
        }
        final EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
            if (!stripMinedLoops.isEmpty()) {
                for (Loop loop : loopsData.loops()) {
                    if (stripMinedLoops.contains(loop.loopBegin())) {
                        loop.detectCounted();
                        if (loop.isCounted()) {
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before optimizing IVs %s", loop);
                            expressIVsViaLoopCounterInIntRange(loop, loop.loopBegin().getStripMinedLimit());
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After optimizing IVs for %s", loop);
                            loop.invalidateFragmentsAndIVs();
                            ec.getNodes().add(loop.loopBegin());
                        }
                    }
                }
                canonicalizer.applyIncremental(graph, context, ec.getNodes());
            }
        }
    }

    /**
     * Tries to reduce the number of distinct IVs (mostly in 64 bit long range) by expressing them
     * as IVs of the strip mined inner counter. This typically involves rewriting 64 bit (sometimes
     * 32 bit ones for int counted loops with long index checks) {@link InductionVariable} of the
     * previously strip mined loop to 32 bit instead. Followed by sign extension for all usages
     * (that expect long IVs).
     *
     * In general we differentiate between the {@link CountedLoopInfo#getLimitCheckedIV()} and all
     * other {@link BasicInductionVariable}.
     *
     * For the limit checked IV we rewrite both the IV and the check to be 32 bit. We use a narrow
     * operation on the limit because we know by the logic in
     * {@link #calculateStripMax(InductionVariable, boolean)} this never overflows integer range.
     *
     * All other induction variables can be rewritten if we know that the
     * {@code constantTripCount * otherIV.stride} does not overflow integer range.
     *
     * To illustrate the transformation consider the following loop: Consider the following loop
     *
     * <pre>
     * long longPhi = longStart;
     * int intPhi = 0;
     * while (intPhi < limit) {
     *     use(intPhi);
     *     use(longPhi);
     *     intPhi++;
     *     longPhi++;
     * }
     * </pre>
     *
     * which can be expressed using the int phi only:
     *
     * <pre>
     * long longPhi = longStart;
     * int intPhi = 0;
     * while (intPhi < limit) {
     *     use(intPhi);
     *     use(longStart + signExtend_32_to_64(intPhi));
     *     intPhi++;
     *     longPhi++;
     * }
     * </pre>
     *
     * then the long phi can be removed completely (except if the loop header state requires it,
     * then it typically folds away after {@link FrameStateAssignmentPhase}).
     */
    private static void expressIVsViaLoopCounterInIntRange(Loop loop, int stripMinedLimit) {
        final CountedLoopInfo countedLoop = loop.counted();
        // only rewrite the limit checked IV if this was a long loop before
        final boolean rewriteLimitCheckedIV = isLong(countedLoop.getLimitCheckedIV().valueNode());
        final LoopBeginNode loopBegin = loop.loopBegin();
        final StructuredGraph graph = loopBegin.graph();
        final DebugContext debug = graph.getDebug();
        final InductionVariable limitCheckedIV = countedLoop.getLimitCheckedIV();
        final long limitCheckedIVStride = limitCheckedIV.constantStride();

        if (limitCheckedIV instanceof DerivedInductionVariable) {
            DerivedInductionVariable div = (DerivedInductionVariable) limitCheckedIV;
            assert div.getBase() instanceof BasicInductionVariable : "Strip mining invariant iv must be one level derived " + div;
            BasicInductionVariable biv = (BasicInductionVariable) div.getBase();
            if (biv.getOp() != div.valueNode()) {
                // only process "simple" inverted loops for now with the same stride as the counter
                // phi, future support can be added for more
                return;
            }
        }
        ValueNode limitCheckedIVRewritten = null;
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        for (InductionVariable iv : ivs.getValues()) {
            if (iv.isConstantStride()) {
                final long stride = iv.constantStride();
                if (rewriteLimitCheckedIV && iv == limitCheckedIV) {
                    // LIMIT CHECKED IV CASE: rewrite IV and limit check
                    assert !ivCanOverflow32Bit(0, stripMinedLimit, limitCheckedIVStride, stride);
                    final int stripMax = (int) (stripMinedLimit / limitCheckedIVStride);
                    limitCheckedIVRewritten = rewriteLoopCounterToInt32(stripMinedLimit, stripMax, countedLoop, loopBegin, graph, debug, limitCheckedIV, stride);
                } else if (iv instanceof BasicInductionVariable && !ivContains(limitCheckedIV, iv)) {
                    // ALL OTHER BASIC IVS: re-use the limit checked IV if possible to avoid
                    // creating redundant phis.
                    if (ivCanOverflow32Bit(0/* strip mined loop is 0-based up-counted */, stripMinedLimit, limitCheckedIVStride, stride)) {
                        // original stride is so large it can overflow int range
                        continue;
                    }

                    /*
                     * Calculate the strip max again from the strip mined limit (which is not the
                     * same if limitCheckedIVStride!=1)
                     *
                     * the ivCanOverflow32bits check above guarantees this calculation does not
                     * overflow in any part
                     */
                    final int stripMax = (int) (stripMinedLimit / limitCheckedIVStride);

                    BasicInductionVariable biv = (BasicInductionVariable) iv;
                    // for simplicity only consider induction variables created from the original
                    // loop, i.e., the basic IV's init is the original outer loop phi
                    if (loop.parent() != null && loop.parent().loopBegin().isPhiAtMerge(biv.initNode())) {
                        boolean newIVtoLong = isLong(biv.valueNode());
                        ValueNode replacement = null;
                        if (biv.isConstantStride() && loop.counted().getLimitCheckedIV().constantStride() == biv.constantStride() && limitCheckedIV instanceof BasicInductionVariable) {
                            // reuse existing iv phi, consider that the limit checked iv might not
                            // be the first iv to encounter in the map cursor
                            if (limitCheckedIVRewritten == null && biv.valueNode().stamp(NodeView.DEFAULT).isCompatible(limitCheckedIV.valueNode().stamp(NodeView.DEFAULT))) {
                                replacement = loop.counted().getLimitCheckedIV().valueNode();
                            } else {
                                replacement = limitCheckedIVRewritten;
                            }
                        }
                        if (replacement == null) {
                            replacement = createNewIntBaseIV(graph, loopBegin, 0, (int) stride, stripMax);
                        }
                        if (newIVtoLong && isInt(replacement)) {
                            replacement = toLong(replacement, graph);
                        }
                        StampInjectedAdd injectedAdd = graph.addWithoutUnique(new StampInjectedAdd(replacement, biv.initNode(), biv.valueNode().stamp(NodeView.DEFAULT)));
                        injectedAdd.inferStamp();
                        replacement = injectedAdd;
                        biv.valueNode().replaceAndDelete(replacement);
                        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After expressing %s via new counter based offset %s", biv, replacement);
                    }
                }
            }
        }
    }

    /**
     * Express the counter of this loop as a 32 bit int operation because we know that
     * {@code stride * stripMinedLimit} does not overflow.
     */
    private static ValueNode rewriteLoopCounterToInt32(int stripMinedLimit, int stripMax, final CountedLoopInfo countedLoop, final LoopBeginNode loopBegin, final StructuredGraph graph,
                    final DebugContext debug,
                    final InductionVariable limitCheckedIV, final long stride) {
        ValuePhiNode new32BitPhi = createNewIntBaseIV(graph, loopBegin, 0, (int) stride, stripMax);
        final boolean inverted = countedLoop.isInverted();
        final LogicNode oldCondition = countedLoop.getLimitTest().condition();
        GraalError.guarantee(oldCondition instanceof CompareNode, "limit test condition must be a compare: %s", oldCondition);
        final CompareNode oldCompare = (CompareNode) oldCondition;

        ValueNode oldX = oldCompare.getX();
        ValueNode oldY = oldCompare.getY();

        final ValuePhiNode counterPhi = getPhiFromIV(limitCheckedIV);

        ValueNode counter = inverted ? counterPhi.valueAt(1) : counterPhi;
        ValueNode newCounter = inverted ? new32BitPhi.valueAt(1) : new32BitPhi;

        ValueNode xReplacement = oldX == counter ? newCounter
                        : graph.addWithoutUniqueWithInputs(PiNode.create(new NarrowNode(oldX, 32), IntegerStamp.create(32, 0, stripMinedLimit)));
        ValueNode yReplacement = oldY == counter ? newCounter
                        : graph.addWithoutUniqueWithInputs(PiNode.create(new NarrowNode(oldY, 32), IntegerStamp.create(32, 0, stripMinedLimit)));

        LogicNode condition = CompareNode.createCompareNode(graph, oldCompare.condition(), xReplacement, yReplacement, null, NodeView.DEFAULT);

        countedLoop.getLimitTest().setCondition(condition);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing condition to be 32bit");

        // replace 64bit phi with the new 32 bit version
        ValueNode backEdgeVal = counterPhi.valueAt(1);
        backEdgeVal.replaceAtUsages(graph.addWithoutUnique(new SignExtendNode(new32BitPhi.valueAt(1), 64)));
        counterPhi.replaceAtUsages(graph.addWithoutUnique(new SignExtendNode(new32BitPhi, 64)));
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing 64bit inner counter phi with the 32 bit nodes");
        return new32BitPhi;
    }

    /**
     * Special add node that carries context knowledge in its internal stamp.
     *
     * When strip mining counted loops we are rewriting IVs to express them via the special inner
     * loop counter (see {@link #expressIVsViaLoopCounterInIntRange} for details). During this
     * rewrite we lose stamp information because the strip mined outer loop is no longer counted and
     * without additional actions its phis have unrestricted stamps.
     *
     * We preserve this stamp information by setting the original IVs phi stamp to the offset if
     * from outer to inner. These special add nodes that are then optimized away if the stamp of the
     * node becomes better than the injected stamp based on the inputs.
     */
    @NodeInfo(shortName = "<plus>")
    static class StampInjectedAdd extends AddNode {
        public static final NodeClass<StampInjectedAdd> TYPE = NodeClass.create(StampInjectedAdd.class);

        private final Stamp originalStamp;

        protected StampInjectedAdd(ValueNode x, ValueNode y, Stamp originalStamp) {
            super(TYPE, originalStamp, x, y);
            this.originalStamp = originalStamp;
        }

        @Override
        public boolean inferStamp() {
            boolean changed = super.inferStamp();
            // always use the injected strip mined meta stamp, see #canonical for details
            if (this.stamp.canBeImprovedWith(originalStamp)) {
                Stamp improved = this.stamp.improveWith(originalStamp);
                updateStamp(improved);
                changed = true;
            }
            return changed;
        }

        @Override
        public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
            final NodeView view = NodeView.from(tool);
            Stamp foldedStamp = foldStamp(getX().stamp(view), getY().stamp(view));
            if (this.stamp.canBeImprovedWith(foldedStamp)) {
                // use the folded better stamp by going back to a regular add node
                return add(forX, forY);
            }
            if (this.stamp.equals(this.stamp.unrestricted())) {
                // use a regular add, there is nothing to gain in terms of stamps here
                return add(forX, forY);
            }

            ValueNode addOptimized = AddNode.create(forX, forY, view);
            if (addOptimized.isAlive()) {
                /*
                 * Optimized to another node in the graph, meaning we found (GVN) a simpler
                 * expression to compute this operation. Prefer that one over the stamp injected
                 * addition node we are dealing with (this).
                 */
                return addOptimized;
            }
            Stamp optimizedStamp = addOptimized.stamp(view);
            if (this.stamp(view).canBeImprovedWith(optimizedStamp)) {
                // the optimized stamp is better, assume that is a better node
                return addOptimized;
            }
            /*
             * After all memory is fixed we can get rid of this node and perform any GVN if
             * necessary.
             */
            if (this.graph() != null && this.graph().isAfterStage(StageFlag.FIXED_READS)) {
                return add(forX, forY);
            }
            return this;
        }

        @Override
        public boolean isNarrowable(int resultBits) {
            return false;
        }
    }

    /**
     * Guard processing for strip mining: guards can be interconnected, after strip mining the outer
     * loop can have guards that are still used by other (duplicated) guards, thus we need to clean
     * them up with a fix point algorithm.
     *
     * An example pattern requiring multiple iterations of the algorithm would be
     *
     * <pre>
     * Guard g = guardedOnLoopBegin;
     * ReadNode r1 = guardedBy(g);
     * IsNullNode isNull = nullCheck(r1);
     * Guard g2 = guardNot(isNull, loopBegin);
     * </pre>
     *
     * where we first have to remove g2 (which has no usages), then canonicalize the {@code isNull}
     * and {@code r1} before we can delete {@code g}.
     */
    private static final int MAX_GUARD_ITERATIONS = 1024;

    /**
     * The strip mining transformation creates an outer loop for the strip-mined iteration space of
     * the inner loop. The outer loop has no functioning body except setting the necessary loop
     * bounds for the inner loop (it does not contain fixed control flow except the counted exit
     * check). To achieve this the strip mining transformation duplicates the original loop. In this
     * duplication it also duplicates any (floating) guards of the original loop (because it uses
     * the {@link LoopFragment} API. The guards of the outer loop duplicate the guards of the inner
     * loop. This is not necessary as we only need one set of the original guards and since we want
     * to optimize the inner loop later to let guards float out we delete the duplicated guards of
     * the outer loop.
     */
    @SuppressWarnings("try")
    private static void cleanUpGuards(EconomicSetNodeEventListener ec, NodeBitMap obsoleteGuards, StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonWithoutSimplification) {
        if (obsoleteGuards == null) {
            return;
        }
        final int localMaxIterations = Math.min(MAX_GUARD_ITERATIONS, obsoleteGuards.count() + 1);
        int iterations = 0;
        // cannot use obsoleteGuards.isNotEmpty since nodes have been deleted in between that still
        // show up in the nodebitmap, need to do one full cycle before being sure nothing changed
        boolean progress = true;
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before deleting redundant guards on the outer loop body begin");
        while (progress) {
            if (iterations++ >= localMaxIterations) {
                throw new PermanentBailoutException("Expected to remove all guards in the duplicated loop %s", obsoleteGuards.snapshot());
            }
            progress = false;
            try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
                for (Node n : obsoleteGuards) {
                    if (n.isAlive() && n.hasNoUsages()) {
                        n.safeDelete();
                        progress = true;
                    }
                }
            }
            canonWithoutSimplification.applyIncremental(graph, context, ec.getNodes());
            ec.getNodes().clear();
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After deleting redundant guards on the outer loop body begin iteration %d", iterations - 1);
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After deleting redundant guards on the outer loop body begin");
    }

    /**
     * Actually perform a strip mining of the given {@code loop}. The loop needs to have a
     * {@link LoopExitNode} as its counted exit and a single loop end.
     *
     * @param newUnrestrictedToOldPhis
     */
    @SuppressWarnings("fallthrough")
    private static void performStripMiningTransformationForLoop(Loop loop, StructuredGraph graph, NodeBitMap obsoleteGuards, EconomicMap<ValuePhiNode, ValuePhiNode> newUnrestrictedToOldPhis) {
        assert loop.isCounted() : "Loop must be counted " + loop;
        assert loop.counted().getCountedExit() instanceof LoopExitNode : "Must be a loop exit " + loop.counted().getCountedExit();
        assert loop.loopBegin().getLoopEndCount() == 1 : "Loop ends should have been merged";
        final double localLoopFrequency = loop.localLoopFrequency();
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before strip mining %s", loop.loopBegin());
        final CountedLoopInfo countedLoop = loop.counted();

        /*
         * We are consuming the max trip count node in this optimization to calculate inner loop
         * trips and reason about when the iteration is done, this node can only be consumed if the
         * loop condition does not overflow. If the limit checked IV overflows the outer loop may
         * continue iterating but the max trip count node used to calculate inner loop trips is
         * wrong. We cannot compute a max trip count node for a loop that overflows.
         */
        if (!countedLoop.counterNeverOverflows()) {
            // canStripMine guarantees that we can actually create an overflow check for this loop
            countedLoop.createOverFlowGuard();
        }

        // create the outer trip count node before any transformation so the division is before the
        // loop
        ValueNode maxTripCountNode = CountedStripMiningUtility.remainingTripCountNode(countedLoop, countedLoop.getBodyIVStart());

        final boolean inverted = countedLoop.isInverted();
        final Stamp counterStamp = countedLoop.getLimitCheckedIV().initNode().stamp(NodeView.DEFAULT);
        final boolean falseSuccIsLexSucc = loop.isCfgLoopExit(countedLoop.getLimitTest().falseSuccessor());

        // Duplicate the loop (the duplicate will become the outer loop)
        LoopFragmentWhole outer = loop.whole().duplicate();
        LoopBeginNode innerBegin = loop.loopBegin();
        LoopBeginNode outerBegin = outer.getDuplicatedNode(innerBegin);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After loop duplication (inner=%s, outer=%s)", innerBegin, outerBegin);

        // We aggressively include floating guards inside loops, see LoopFragment.markFloating. Yet,
        // during strip mining, we should only retain the control flow construct for the outer loop,
        // but not the floating guards, as the guarded conditions are 1) checked in the inner loop,
        // and 2) meaningless in the outer loop, which serves purely as a structural construct.
        for (Node n : outer.nodes()) {
            if (n instanceof GuardNode) {
                obsoleteGuards.markAndGrow(n);
            }
        }

        // Nest original loop after the duplicate's limit test

        final IfNode innerLimitTest = countedLoop.getLimitTest();
        final IfNode outerLimitTest = outer.getDuplicatedNode(innerLimitTest);
        FixedWithNextNode innerBodyBegin = null;
        FixedWithNextNode originalOuterBodyBegin = null;
        BeginNode outerBodyBegin = null;

        AbstractBeginNode falseSuccessor = outerLimitTest.falseSuccessor();
        if (inverted) {
            // for tail counted loops we introduce a strip mining placeholder fixed with next node
            // during preparation to ensure we have a fixed with next node as the body next node
            innerBodyBegin = (FixedWithNextNode) countedLoop.getBody().next();
            originalOuterBodyBegin = outer.getDuplicatedNode(innerBodyBegin);

            outerLimitTest.replaceAtPredecessor(null);
            outerBegin.setNext(null);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After cutting out outer body");

            innerBegin.forwardEnd().replaceAtPredecessor(outerBegin.forwardEnd());
            outerBegin.setNext(innerBegin.forwardEnd());
            outerBodyBegin = graph.add(new BeginNode());
            graph.addAfterFixed(outerBegin, outerBodyBegin);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Setting inner loop to be successor of outer loop header");
        } else {
            // head counted loop start with fixed with next nodes per definition
            innerBodyBegin = countedLoop.getBody();
            originalOuterBodyBegin = outer.getDuplicatedNode(innerBodyBegin);
            innerBegin.forwardEnd().replaceAtPredecessor(outerBegin.forwardEnd());
            outerBodyBegin = graph.add(new BeginNode());
            graph.addAfterFixed(outerBodyBegin, innerBegin.forwardEnd());
            if (falseSuccIsLexSucc) {
                outerLimitTest.setTrueSuccessor(outerBodyBegin);
            } else {
                assert falseSuccessor == originalOuterBodyBegin : falseSuccessor + "!=" + originalOuterBodyBegin;
                outerLimitTest.setFalseSuccessor(outerBodyBegin);
            }
        }
        // replace any guard and anchor edges of the outer loop (for exact math counted loops)
        originalOuterBodyBegin.replaceAtUsages(outerBodyBegin);

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After nesting inner loop after outer loop limit test");

        // Delete duplicate body and attach inner loop exits
        LoopEndNode outerBodyEnd = outerBegin.loopEnds().first();
        final LoopExitNode innerCountedExit = (LoopExitNode) countedLoop.getCountedExit();
        FixedNode innerExitNext = innerCountedExit.next();

        EconomicMap<PhiNode, PhiNode> newToOuterStampsToFix = EconomicMap.create();

        for (PhiNode phi : innerBegin.phis()) {
            // Set inner loop phi entry value to outer loop phi and set the outer loop phi
            // back edge value to the inner loop back edge.
            PhiNode outerPhi = outer.getDuplicatedNode(phi);
            assert phi.valueCount() == 2 && outerPhi.valueCount() == 2 : phi + " " + outerPhi;
            phi.setValueAt(0, outerPhi);
            if (inverted) { // take the backedge value for the outer phi since thats the checked one
                            // -> must be a derived IV which is ensured in canStripMine
                ProxyNode p = LoopFragmentInside.patchProxyAtPhi(phi, innerCountedExit, phi.valueAt(1), true);
                phi.valueAt(1).inferStamp();
                outerPhi.setValueAt(1, p);
            } else {
                outerPhi.setValueAt(1, phi.createProxyFor(innerCountedExit));
            }
            newToOuterStampsToFix.put(phi, outerPhi);
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After setting loop phi values");

        if (inverted) {
            // for inverted loops we check the shape of the limit checked induction variable in
            // canStripMine(): we expect a derived offset induction variable of the form iv + stride
            // < limit where the arithmetic node's x is the base IV and y is the stride
            BinaryOpLogicNode outerCondition = (BinaryOpLogicNode) outerLimitTest.condition();
            ValueNode xC = outerCondition.getX();
            ValueNode yC = outerCondition.getY();
            ValueNode xReplacement = xC == countedLoop.getLimit() ? xC : (((BinaryArithmeticNode<?>) xC).getX());
            ValueNode yReplacement = yC == countedLoop.getLimit() ? yC : (((BinaryArithmeticNode<?>) yC).getX());
            if (xC != xReplacement) {
                outerCondition.replaceAllInputs(xC, xReplacement);
            }
            if (yC != yReplacement) {
                outerCondition.replaceAllInputs(yC, yReplacement);
            }
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After repalcement of the outer cond");
        }

        if (inverted) {
            reRouteDuplicatedProxiesToDominatingInnerLoop(innerCountedExit, outer.getDuplicatedNode(innerCountedExit), outer);
        }

        if (inverted) {
            innerCountedExit.setNext(outerLimitTest);
            assert innerExitNext instanceof EndNode : innerExitNext;
            GraphUtil.killCFG(innerExitNext);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After attaching inner loop exits inverted");
        } else {
            outerBodyEnd.replaceAtPredecessor(null);
            assert innerExitNext instanceof EndNode : innerExitNext;
            innerCountedExit.setNext(outerBodyEnd);
            GraphUtil.killCFG(innerExitNext);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After attaching inner loop exits");
        }

        InductionVariable counter = countedLoop.getLimitCheckedIV();
        /*
         * We are about to adapt the inner loop phi, we have then later 2 value phis:
         *
         * (1) the inner loop phi which now counts up from 0 to inner trips (using the original
         * stride in an up-counting fashion abs(originalStride)).
         *
         * (2) a phi that uses the outer iv val and applies the stride (which can be != 1) every
         * iteration to the inner loop
         *
         * For inverted loops the IV has to be a derived offset IV, we check that in canStripMine.
         * Thus, we can safely reason about the head counted case (base IV) or the tail counted case
         * (offset iv).
         */
        ValuePhiNode innerCounterPhi = getPhiFromIV(counter);
        ValueNode innerCounterPhiLimitCheckedVal = counter.valueNode(); // implicitly use derived iv
                                                                        // op for inverted loops
        ValuePhiNode outerCounterPhi = outer.getDuplicatedNode(innerCounterPhi);
        debug.log(DebugContext.VERY_DETAILED_LEVEL, "innerCounter:%s outerCounter:%s init:%s limit:%s stride:%s direction:%s",
                        innerCounterPhi, outerCounterPhi, counter.initNode(), countedLoop.getLimit(), counter.strideNode(), counter.direction());

        // we can use math.abs here because we already checked the abs of the stride in
        // #calculateStripMax
        final long stride;
        try {
            stride = NumUtil.safeAbs(counter.constantStride(), IntegerStamp.getBits(counter.strideNode().stamp(NodeView.DEFAULT)));
        } catch (ArithmeticException e) {
            throw GraalError.shouldNotReachHere(e);
        }

        final Graph.Mark beforeAddingIVLogic = graph.getMark();
        boolean negated = false;
        if (!falseSuccIsLexSucc) {
            negated = true;
        }
        final ConstantNode stripMax = forConstantVal(calculateStripMax(counter, false), counterStamp, graph);
        innerBegin.setStripMinedLimit(/* known to be int */(int) stripMax.asJavaConstant().asLong());
        final ValueNode limit = countedLoop.getTripCountLimit();

        final ValueNode newInnerTrips = calculateNewInnerTripCount(stripMax, limit, outerCounterPhi, graph, countedLoop, stride, maxTripCountNode,
                        outerBodyBegin);
        final ValueNode innerTrips = newInnerTrips.isAlive() ? newInnerTrips : graph.addWithoutUnique(newInnerTrips);

        // Rewrite inner loop bounds to this form (even for down-counted):
        // for (j = 0; j |<| innerTrips; j+=stride)
        //
        // Set inner init to 0
        innerCounterPhi.setValueAt(0, forConstantVal(0, counterStamp, graph));
        // Set inner condition to j |<| innerTrips
        LogicNode newInnerCondition = null;

        if (negated) {
            swapSuccessors(innerLimitTest);
        }
        newInnerCondition = graph.addWithoutUnique(createCompareNode(innerCounterPhiLimitCheckedVal, innerTrips));
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new inner condition %s", newInnerCondition);
        innerLimitTest.replaceFirstInput(innerLimitTest.condition(), newInnerCondition);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing condition of inner limit test %s with %s", innerLimitTest, newInnerCondition);

        innerCounterPhi.refineStampWith(IntegerStamp.create(IntegerStamp.getBits(innerCounterPhi.stamp(NodeView.DEFAULT)), 0, stripMax.asJavaConstant().asLong()));

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After setting new stamp for inner counter phi %s", innerCounterPhi);

        BinaryArithmeticNode<?> innerOp = getOpFromIV(counter);
        // copy the outer op to have a new node to avoid changing any outer derived iv if we are
        // handling an inverted loop
        BinaryArithmeticNode<?> outerOp = (BinaryArithmeticNode<?>) outer.getDuplicatedNode(innerOp).copyWithInputs();

        ValueNode newInnerOp = new AddNode(innerCounterPhi, forConstantVal(stride, counterStamp, graph));

        innerCounterPhi.replaceAllInputs(innerOp, graph.addWithoutUnique(newInnerOp));
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After setting inner loop bounds");

        final ValuePhiNode innerStridePhi = offsetInnerPhiUsagesWithNewStripMinedCounterPhi(graph, outerCounterPhi, innerBegin, outerOp, innerCounterPhi, newInnerCondition, newInnerOp,
                        innerCounterPhiLimitCheckedVal);

        GraphUtil.killCFG(originalOuterBodyBegin);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After killing outer loop body");

        if (inverted) {
            newInnerCondition.replaceFirstInput(innerCounterPhiLimitCheckedVal, newInnerOp);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing input %s of %s with %s",
                            innerCounterPhi, newInnerCondition, newInnerOp);
        }

        if (inverted) {
            // repair post dominating values of the outer loop in the remaining portion of code
            outerCounterPhi.replaceAtUsages(outerCounterPhi.valueAt(1),
                            x -> outer.contains(x) && x.isAlive() && !(x instanceof VirtualState) &&
                                            !(graph.isNew(beforeAddingIVLogic, x)));
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing post dominating loop usages of derived inverted IVs with phi backedge values");
        }

        promoteNonCountedInnerLexToOuterLex(graph, innerBegin, innerCountedExit, outerBegin, loop);

        loop.invalidateFragmentsAndIVs();
        assignInnerExitState(graph, loop, innerCountedExit, inverted);

        insertPhiLogNodes(graph, outerCounterPhi, limit, innerCounterPhi, innerTrips, innerStridePhi, outerBodyBegin, innerBodyBegin, innerBegin, outerBegin);

        /*
         * The outer loop does Math.ceil(originalFrequency / innerLoopTrips) trips + 1 for the final
         * loop exit check
         */
        adaptOuterLoopExitProbability(outerLimitTest, localLoopFrequency, stripMax, inverted);

        loopBeginPropertyEpilog(innerBegin, outerBegin);

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before injecting old phi stamps");
        MapCursor<PhiNode, PhiNode> stampsToFix = newToOuterStampsToFix.getEntries();
        while (stampsToFix.advance()) {
            PhiNode newInner = stampsToFix.getKey();
            PhiNode outerPhi = stampsToFix.getValue();
            if (newInner instanceof ValuePhiNode vp) {
                assert newUnrestrictedToOldPhis.containsKey(vp) : Assertions.errorMessage("Must have entries for phis", newInner);
                final Stamp oldStamp = newUnrestrictedToOldPhis.get(vp).stamp(NodeView.DEFAULT);
                if (newInner == innerCounterPhi) {
                    /*
                     * The original counter usages go to the other stride phi now, use the stamp of
                     * the original counter.
                     */
                    duplicateWithNewStamp(innerStridePhi, oldStamp, false);
                } else {
                    duplicateWithNewStamp((ValuePhiNode) newInner, oldStamp, false);
                }
                duplicateWithNewStamp((ValuePhiNode) outerPhi, oldStamp, outerPhi == outerCounterPhi);
            }
        }

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After injecting old phi stamps");
        assert ensureSaneGraph(graph);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After strip mining %s", loop.loopBegin());
    }

    private static void adaptOuterLoopExitProbability(IfNode exitCheck, double originalFrequency, ConstantNode stripMaxNode, boolean inverted) {
        final AbstractBeginNode lex = exitCheck.trueSuccessor() instanceof LoopExitNode ? exitCheck.trueSuccessor() : exitCheck.falseSuccessor();
        final long stripMax = stripMaxNode.asJavaConstant().asLong();
        /*
         * The frequency of the outer loop is computed by dividing the original frequency by
         * the strip mine limit and rounding the result up to the next integer. For head counted
         * loops, the original frequency has to be reduced by 1 to account for the additional loop
         * exit check. This removed "iteration" has to be added again to the division result. Some
         * examples with a strip mine limit of 4096:
         *
         * @formatter:off
         *  1) head counted, frequency = 10_000 --> newFrequency = Math.ceil(9999 / 4096) + 1 = 4
         *  2) head counted, frequency = 1000   --> newFrequency = Math.ceil(999 / 4096) + 1 = 2
         *  3) tail counted, frequency = 10_000 --> newFrequency = Math.ceil(10_000 / 4096) = 3
         *  4) tail counted, frequency = 1000   --> newFrequency = Math.ceil(1000 / 4096) = 1
         * @formatter:on
         *
         * If the frequency for the head counted loop from example (2) was not incremented by 1, the
         * exit frequency would be 1, implying that the body would never be entered. This can prevent
         * further optimizations in the inner loop due to low relative frequencies being forwarded.
         */
        final int headCountedIteration = inverted ? 0 : 1;
        final double originalBodyFrequency = originalFrequency - headCountedIteration;
        final double newFrequency = Math.ceil(originalBodyFrequency / stripMax) + headCountedIteration;
        LoopTransformations.adaptCountedLoopExitProbability(lex, newFrequency);
    }

    /**
     * Replaces a phi with a copy with a new stamp, preserving its inputs and merge. When
     * {@code derivedFromLimitCheckedIV} is true, the copy is marked as the outer phi that
     * reconstructs the original limit checked IV of the strip mined outer loop.
     *
     * @param oldPhi the phi to replace
     * @param newStamp the stamp for the new phi
     * @param derivedFromLimitCheckedIV whether this node is derived from the original loop's limit checked IV
     */
    private static void duplicateWithNewStamp(ValuePhiNode oldPhi, Stamp newStamp, boolean derivedFromLimitCheckedIV) {
        ValuePhiNode newPhi;
        if (derivedFromLimitCheckedIV) {
            newPhi = CountedStripMiningUtility.createOriginalLimitCheckedIVPhi(newStamp, oldPhi.merge());
        } else {
            newPhi = new ValuePhiNode(newStamp, oldPhi.merge());
        }

        newPhi = oldPhi.graph().addWithoutUnique(newPhi);
        for (ValueNode v : oldPhi.values()) {
            newPhi.addInput(v);
        }
        newPhi.inferStamp();
        oldPhi.replaceAndDelete(newPhi);
    }

    /**
     * Assign the correct inner to outer loop exit state - that state is the loop header state at
     * the particular iterations with all values properly proxied.
     *
     * Note that this is a different state for head counted and tail counted (inverted loops).
     *
     * For head counted loops like the following:
     *
     * <pre>
     * int i = 0;
     * while (true) {
     *     if (i < limit) { // <-- head state, if we go back to header or exit, we both do it in the
     *                      // head counted position
     *         // body
     *         i++;
     *         continue;
     *     }
     *     break;
     * }
     * </pre>
     *
     * it is the same as the loop header state (with values proxied).
     *
     * For inverted loops like
     *
     * <pre>
     * int i = 0;
     * while (true) {
     *     // body
     *     i++;
     *     if (l == limit) { // <-- strip mining re-writes the loop here to an outer loop trip
     *         break;
     *     }
     * }
     * </pre>
     *
     * it is the last state of the loop body, which can be the loop header if there was no
     * side-effect in between. It is not the header state as the outer loop is not guaranteed to
     * enter the inner loop again.
     */
    private static void assignInnerExitState(StructuredGraph graph, Loop innerLoop, LoopExitNode innerExit, boolean inverted) {
        final LoopBeginNode innerLoopBegin = innerLoop.loopBegin();
        GraalError.guarantee(innerExit.loopBegin() == innerLoopBegin, "Must match lex and begin");
        GraalError.guarantee(innerLoopBegin.getLoopEndCount() == 1, "Must have a singe loop end %s",
                        innerLoopBegin);
        final FrameState originalLoopState = innerLoopBegin.stateAfter();
        GraalError.guarantee(originalLoopState != null, "Must have framestates still");

        FrameState newState = null;
        final FrameState oldState = innerExit.stateAfter();
        if (inverted) {
            newState = GraphUtil.findLastFrameState((FixedNode) innerExit.predecessor()).duplicateWithVirtualState();
            GraalError.guarantee(newState != null, "Last state from body can either be a real node or the loop header but is null for %s", innerExit);
        } else {
            newState = originalLoopState.duplicateWithVirtualState();
        }

        newState.applyToNonVirtual(new NodePositionClosure<>() {
            @Override
            public void apply(Node from, Position p) {
                ValueNode usage = (ValueNode) p.get(from);
                /*
                 * The loops data has been become inconsistent in between since phis have been
                 * added, but those are the only new nodes we need to treat specially.
                 */
                if (usage instanceof VirtualObjectNode) {
                    /*
                     * Virtual object nodes are not duplicated with their state, they remain shared.
                     * If they had no other usage before strip mining they will be considered part
                     * of the loop.
                     */
                    return;
                }
                /*
                 * Instead of manually tracking all added nodes we just create proxies for all nodes
                 * and later cleanup all unnecessary proxies created by this phase, this is a little
                 * more expensive but the most correct way of doing it.
                 */
                ProxyNode proxyAt = null;
                switch (p.getInputType()) {
                    case Value:
                        proxyAt = graph.addOrUnique(new ValueProxyNode(usage, innerExit));
                        break;
                    case Memory:
                        proxyAt = graph.addOrUnique(new MemoryProxyNode((MemoryKill) usage, innerExit,
                                        ((SingleMemoryKill) usage).getKilledLocationIdentity()));
                        break;
                    case Guard:
                        proxyAt = graph.addOrUnique(new GuardProxyNode((GuardingNode) usage, innerExit));
                        break;
                    default:
                        // all other input types can be ignored, they are not forcing us to create
                        // proxies
                        break;
                }
                if (proxyAt != null) {
                    p.set(from, proxyAt);
                }
            }
        });
        innerExit.setStateAfter(newState);
        assert oldState.hasNoUsages();
        GraphUtil.killWithUnusedFloatingInputs(oldState);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating inner loop exit state %s for %s", newState, innerExit);
    }

    /**
     * For unsigned compared counted loops we prefer a signed condition of the inner upcounting loop
     * because we do not want to think about unsigned conditions and overflowing value ranges.
     * Mostly important for inverted loops, for more details see
     * {@link CountedLoopInfo#getLimitCheckedIV()} for details.
     */
    private static LogicNode createCompareNode(ValueNode a, ValueNode b) {
        return new IntegerLessThanNode(a, b);
    }

    /**
     * Head counted loops can only proxy phis because there cannot be a value between the counted
     * check and the loop begin (except floating nodes which are fine).
     *
     * For inverted loops we end up proxying values of the inner loop to the outer one, we need to
     * update them correctly.
     */
    private static void reRouteDuplicatedProxiesToDominatingInnerLoop(LoopExitNode innerExit, LoopExitNode outerExit, LoopFragmentWhole outer) {
        GraalError.guarantee(innerExit.next() instanceof EndNode, "Next node must be merge of outer duplicate");
        EndNode end = (EndNode) innerExit.next();
        GraalError.guarantee(end.merge() instanceof MergeNode, "Loop and duplicate must merge on a real merge node");
        MergeNode merge = (MergeNode) end.merge();
        EconomicMap<Node, Node> reverseDuplicatioMap = outer.reverseDuplicationMap();

        // proxies of the outer loop can still reference body code of the outer loop
        for (ProxyNode proxy : outerExit.proxies().snapshot()) {
            proxy.replaceAtUsages(proxy.duplicateOn(outerExit, proxy.duplicateOn(innerExit, (ValueNode) reverseDuplicatioMap.get(proxy.value()))));
            proxy.safeDelete();
        }
        innerExit.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, innerExit.next().graph(), "After replacing old wrong proxies with new values");

        for (PhiNode phi : merge.phis().snapshot()) {
            phi.replaceAtUsages(phi.valueAt(1));
            GraphUtil.killWithUnusedFloatingInputs(phi);
        }

        innerExit.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, innerExit.next().graph(), "After repairing non value usages outside of the merged loop exits");
    }

    /**
     * The method creates the new phi for the original stride phi that will now use the outer loop
     * offset and stride fo the inner loop. See the javadoc of this phase for details on the new
     * (always up counted) limit checked IV and the newly introduced stride phi.
     *
     * As an example consider the following original loop:
     *
     * <pre>
     * for (phi = 0; phi < limit; phi += stride) {
     *     use(phi);
     * }
     * </pre>
     *
     * At this point in the strip mining transformation we are currently fixing up the outer-inner
     * phi relation and the graph is roughly in the following state (we already adapted the new
     * inner limit but we have not yet updated the usages of the original limit checked IV to the
     * new phi):
     *
     * <pre>
     * for (phi = 0; phi < limit; phi += stride) {
     *     long stripMax = calculateStripMax();
     *     for (innerPhi = 0; innerPhi < stripMax; innerPhi += 1) {
     *         use(innerPhi);
     *     }
     * }
     * </pre>
     *
     * this method will create the new inner loop phi that is offset by the outer phi and adapt all
     * usages accordingly
     *
     * <pre>
     * for (phi = 0; phi < limit; phi += stride) {
     *     long stripMax = calculateStripMax();
     *     long stridePhi = phi;
     *     for (innerPhi = 0; innerPhi < stripMax; innerPhi += 1) {
     *         use(stridePhi);
     *         stridePhi += stride;
     *     }
     * }
     * </pre>
     */
    private static ValuePhiNode offsetInnerPhiUsagesWithNewStripMinedCounterPhi(StructuredGraph graph, ValuePhiNode outerCounterPhi, LoopBeginNode innerBegin,
                    BinaryArithmeticNode<?> outerOp,
                    ValuePhiNode innerCounterPhi, ValueNode newInnerCondition, ValueNode newInnerOp, ValueNode innerOpDerivedIvVal) {

        final DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before offset phis.");

        // Create phi that applies the outer stride every inner iteration
        ValuePhiNode innerStridePhi = graph.addWithoutUnique(new ValuePhiNode(outerCounterPhi.stamp(NodeView.DEFAULT), innerBegin));
        outerOp.setX(innerStridePhi);

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After setting outer op x");

        innerStridePhi.addInput(outerCounterPhi);
        innerStridePhi.addInput(outerOp);
        for (Node usage : innerCounterPhi.usages().snapshot()) {
            if (usage != newInnerCondition && usage != newInnerOp && usage != innerOpDerivedIvVal) {
                usage.replaceAllInputs(innerCounterPhi, innerStridePhi);
            }
        }

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating offset inner loop counter for base phi");

        for (Node usage : innerOpDerivedIvVal.usages().snapshot()) {
            if (usage != newInnerCondition && usage != newInnerOp) {
                usage.replaceAllInputs(innerOpDerivedIvVal, outerOp);
            }
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After offset phis.");

        return innerStridePhi;
    }

    private static void promoteNonCountedInnerLexToOuterLex(StructuredGraph graph, LoopBeginNode innerBegin, LoopExitNode innerCountedExit, LoopBeginNode outerBegin, Loop loop) {
        final DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before promoting non counted exits of inner loop to be exits of outer loop");
        // for all other loop exits of the inner loop that are not the counted exit create loop
        // exits to the outer loop as well
        for (LoopExitNode lex : innerBegin.loopExits().snapshot()) {
            if (lex == innerCountedExit) {
                continue;
            }
            NonCountedStripMiningPhase.createOuterLoopExitAfterInnerExit(lex, loop, outerBegin);
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After promoting non counted exits of inner loop to be exits of outer loop");
    }

    /**
     * Set various properties for inner and outer loop after strip mining.
     */
    private static void loopBeginPropertyEpilog(LoopBeginNode innerBegin, LoopBeginNode outerBegin) {
        innerBegin.markCountedStripMinedInner();
        outerBegin.markCountedStripMinedOuter();

        // after strip mining, we know the inner counter can never overflow
        if (!innerBegin.canNeverOverflow()) {
            innerBegin.setCanNeverOverflow();
        }

        // only remove safepoints if the original loop MUST NOT safepoint
        if (!innerBegin.canEndsSafepoint()) {
            outerBegin.setGuestSafepoint(SafepointState.OPTIMIZER_DISABLED);
            outerBegin.setLoopEndSafepoint(SafepointState.OPTIMIZER_DISABLED);
        }
    }

    private static boolean ensureSaneGraph(StructuredGraph graph) {
        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            assert GraphOrder.assertSchedulableGraph(graph) : "Graph must be schedulable";
            // schedule latest as well, we introduce complex framestate cycles
            SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.LATEST_OUT_OF_LOOPS);
        }
        return true;
    }

    /**
     * If compiled code runtime logging is enabled also log which loop exit paths (by id) are taken
     * at runtime.
     */
    private static final boolean LOG_LOOP_EXIT_TAKEN = true;

    private static void insertPhiLogNodes(StructuredGraph graph, ValueNode outerCounterPhi, ValueNode limit, ValueNode innerCounterPhi, ValueNode innerTrips, ValueNode innerStridePhi,
                    FixedWithNextNode outerBodyBegin, FixedWithNextNode innerBodyBegin, LoopBeginNode innerBegin, LoopBeginNode outerBegin) {

        if (Options.CountedStripMiningLogCounters.getValue(graph.getOptions())) {

            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before adding log nodes for insertPhiLogNodes");

            int bits = IntegerStamp.getBits(limit.stamp(NodeView.DEFAULT));
            String formatter = bits == 32 ? "%d" : "%ld";

            String methodString = graph.method() != null ? graph.method().format("%H.%n(%p)") + "->" : "";
            LogNode outerLog = new LogNode(
                            methodString + "outer phi: " + outerCounterPhi + " = " + formatter + ", outer limit: " + limit + " = " + formatter + ", inner trips: " + innerTrips + " = " + formatter +
                                            "\n",
                            outerCounterPhi, limit,
                            innerTrips);
            LogNode innerLog = new LogNode(methodString + "inner phi: " + innerCounterPhi + " = " + formatter + ", inner stride phi: " + innerStridePhi + " = " + formatter + "\n", innerCounterPhi,
                            innerStridePhi);
            if (outerBodyBegin.next() instanceof IntegerDivRemNode) {
                // the remaining trip count of another log is in the loop already
                graph.addAfterFixed((FixedWithNextNode) outerBodyBegin.next(), graph.add(outerLog));
            } else {
                graph.addAfterFixed(outerBodyBegin, graph.add(outerLog));
            }
            graph.addAfterFixed(innerBodyBegin, graph.add(innerLog));

            if (LOG_LOOP_EXIT_TAKEN) {
                for (LoopExitNode lex : innerBegin.loopExits()) {
                    graph.addAfterFixed(lex, graph.add(new LogNode("Exiting loop through exit " + lex + "\n")));
                    for (ProxyNode proxy : lex.proxies()) {
                        if (proxy instanceof ValueProxyNode) {
                            graph.addAfterFixed(lex, graph.add(new LogNode("Exiting loop through exit " + lex + " with proxy " + proxy + "=" + formatter + "\n", proxy)));
                        }
                    }

                }
                for (LoopExitNode lex : outerBegin.loopExits()) {
                    graph.addAfterFixed(lex, graph.add(new LogNode("Exiting loop through exit " + lex + "\n")));
                }
            }

            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After adding log nodes for insertPhiLogNodes");
        }
    }

    /**
     * See the javadoc of this phase.
     *
     * Calculate the new limit of the inner, strip mined, loop. This is
     * {@code min(stripMax, restIterations)}.
     *
     * Implementation note: This method uses {@link StructuredGraph#addWithoutUnique(Node)} to add
     * the sub computations for the new inner trip nodes. This is necessary to avoid using any
     * values of the outer duplicated loop since GVN may re-use nodes of the duplicated
     * {@link LoopFragment}. This can cause problems when fixing usages later in this phase. Thus,
     * no computation inside here except the last conditional is allowed to be added with
     * {@link StructuredGraph#addOrUnique(Node)}.
     */
    private static ValueNode calculateNewInnerTripCount(ValueNode stripMax, ValueNode limit, ValueNode outerCounterPhi, StructuredGraph graph, CountedLoopInfo countedLoop, long absStride,
                    final ValueNode maxTripCountNode, FixedWithNextNode insertLogCaller) {
        assert assertSameBitCount(stripMax, limit);

        ValueNode stripMaxNoStride = forConstantVal(calculateStripMax(countedLoop.getLimitCheckedIV(), true), limit.stamp(NodeView.DEFAULT), graph);
        ValueNode restIterations = CountedStripMiningUtility.remainingTripCountNode(countedLoop, outerCounterPhi);
        LogicNode isLastIteration = graph.addWithoutUnique(new IntegerBelowNode(restIterations, stripMaxNoStride));
        ValueNode innerTrips = graph.addWithoutUnique(
                        new ConditionalNode(isLastIteration, graph.addWithoutUnique(new MulNode(restIterations, forConstantVal(absStride, limit.stamp(NodeView.DEFAULT), graph))), stripMax));

        if (Options.CountedStripMiningLogCounters.getValue(graph.getOptions())) {
            FixedWithNextNode insertLog = insertLogCaller;
            if (restIterations == insertLogCaller.next()) {
                // next iterations is in outer loop
                insertLog = (FixedWithNextNode) restIterations;
            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before adding log nodes for calculatingInnerTripCount");
            int bits = IntegerStamp.getBits(limit.stamp(NodeView.DEFAULT));
            String formatter = bits == 32 ? "%u" : "%lu";
            ValueNode currentTripCountNode = CountedStripMiningUtility.remainingTripCountNode(countedLoop, outerCounterPhi);
            if (currentTripCountNode == insertLog.next()) {
                // next iterations is in outer loop
                insertLog = (FixedWithNextNode) currentTripCountNode;
            }
            ValueNode doneIterations = graph.addWithoutUnique(new SubNode(maxTripCountNode, currentTripCountNode));
            graph.addAfterFixed(insertLog, graph.add(new LogNode("InnerTrips=" + formatter + "\n", innerTrips)));
            graph.addAfterFixed(insertLog, graph.add(new LogNode("RestIterations=" + formatter + "\n", restIterations)));
            graph.addAfterFixed(insertLog, graph.add(new LogNode("DoneIterations=" + formatter + "\n", doneIterations)));
            graph.addAfterFixed(insertLog, graph.add(new LogNode("CurrentTripCount=" + formatter + "\n", currentTripCountNode)));
            graph.addAfterFixed(insertLog, graph.add(new LogNode("MaxTripCount=" + formatter + "\n", maxTripCountNode)));
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After adding log nodes for calculatingInnerTripCount");
        }

        return innerTrips;

    }

    private static boolean assertSameBitCount(ValueNode stripMax, ValueNode limit) {
        IntegerStamp stripStamp = (IntegerStamp) stripMax.stamp(NodeView.DEFAULT);
        IntegerStamp limitStamp = (IntegerStamp) limit.stamp(NodeView.DEFAULT);
        assert stripStamp.getBits() == limitStamp.getBits() : "Must have same bit count " + stripMax + " limit=" + limit;
        return true;
    }

    /**
     * @see #getPhiFromIV(InductionVariable)
     */
    private static BinaryArithmeticNode<?> getOpFromIV(InductionVariable iv) {
        if (iv instanceof BasicInductionVariable) {
            return ((BasicInductionVariable) iv).getOp();
        } else if (iv instanceof DerivedInductionVariable) {
            DerivedInductionVariable dIv = (DerivedInductionVariable) iv;
            GraalError.guarantee(dIv instanceof DerivedOffsetInductionVariable, "Must have an offset iv for the limit checked iv for inverted loops.");
            InductionVariable base = dIv.getBase();
            GraalError.guarantee(base instanceof BasicInductionVariable, "Must have a basic iv for the limit checked iv");
            return ((BasicInductionVariable) base).getOp();
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(iv); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Return the phi node from the given induction variable, unwrapping one level of derived
     * induction variables. This uses the implicit knowledge that the limit checked induction
     * variable of a loop is either a {@link BasicInductionVariable} or a
     * {@code DerivedInductionVariable} which's base IV is a basic iv.
     */
    private static ValuePhiNode getPhiFromIV(InductionVariable iv) {
        if (iv instanceof BasicInductionVariable) {
            return (ValuePhiNode) iv.valueNode();
        } else if (iv instanceof DerivedInductionVariable) {
            DerivedInductionVariable dIv = (DerivedInductionVariable) iv;
            GraalError.guarantee(dIv instanceof DerivedOffsetInductionVariable, "Must have an offset iv for the limit checked iv for inverted loops.");
            InductionVariable base = dIv.getBase();
            GraalError.guarantee(base instanceof BasicInductionVariable, "Must have a basic iv for the limit checked iv");
            return (ValuePhiNode) base.valueNode();
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(iv); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Swap the successors of the given if node.
     */
    private static void swapSuccessors(IfNode ifNode) {
        BranchProbabilityData oldData = ifNode.getProfileData();
        AbstractBeginNode trueSucc = ifNode.trueSuccessor();
        AbstractBeginNode falseSucc = ifNode.falseSuccessor();
        ifNode.setTrueSuccessor(null);
        ifNode.setFalseSuccessor(null);
        ifNode.setTrueSuccessor(falseSucc);
        ifNode.setFalseSuccessor(trueSucc);
        ifNode.setTrueSuccessorProbability(oldData.negated());
        ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "After swapping successors at if %s", ifNode);
    }

    public static final CounterKey FailedCanStripMineCanNotStripMine = DebugContext.counter("CountedStripMining_FailedCanStripMine_CanNotStripMine");
    public static final CounterKey FailedCanStripMineCanNotDuplicateLoop = DebugContext.counter("CountedStripMining_FailedCanStripMine_CanNotDuplicateLoop");
    public static final CounterKey FailedCanStripMineAlreadyStripMined = DebugContext.counter("CountedStripMining_FailedCanStripMine_AlreadyStripMined");
    public static final CounterKey FailedCanStripMineHasChildren = DebugContext.counter("CountedStripMining_FailedCanStripMine_HasChildren");
    public static final CounterKey FailedShouldStripMineNonConstantStride = DebugContext.counter("CountedStripMining_FailedShouldStripMine_NonConstantStride");
    public static final CounterKey FailedShouldStripMineLimitCheckedIVConverts = DebugContext.counter("CountedStripMining_FailedShouldStripMine_LimitCheckedIVConverts");
    public static final CounterKey FailedShouldStripMineGuardingIV = DebugContext.counter("CountedStripMining_FailedShouldStripMine_GuardinGIVOperationWithInLoopBodyGuard");
    public static final CounterKey FailedShouldStripMineIVShape = DebugContext.counter("CountedStripMining_FailedShouldStripMine_GuardinGIVShapeWrong");
    public static final CounterKey FailedShouldStripMineNoBenefit = DebugContext.counter("CountedStripMining_FailedShouldStripMine_NoBenefit");

    /**
     * Determine if, based on the shape of the loop, the given loop can structurally be stripped
     * mined with the algorithms implemented in this phase.
     */
    private static boolean canStripMine(Loop loop, StructuredGraph graph, LazyValue<ScheduleResult> lazySchedule, MidTierContext context) {
        DebugContext debug = graph.getDebug();
        CountedLoopInfo countedLoop = loop.counted();
        LoopBeginNode loopBegin = loop.loopBegin();
        if (loopBegin.isAnyStripMinedOuter() || loopBegin.isAnyStripMinedInner()) {
            FailedCanStripMineAlreadyStripMined.increment(debug);
            return false;
        }
        if (!countedLoop.getLimitCheckedIV().isConstantStride()) {
            FailedShouldStripMineNonConstantStride.increment(debug);
            return false;
        }
        if (loop.getCFGLoop().getChildren().size() > 0) {
            // only strip mine inner loops
            FailedCanStripMineHasChildren.increment(debug);
            return false;
        }
        if (LoopUtility.snippetSideEffectLoop(loop)) {
            return false;
        }
        if (LoopUtility.isConstantLoopCount(loop, LoopPeelingPhase.Options.IterativePeelingLimit.getValue(graph.getOptions()) + 1)) {
            /*
             * Do not strip mine constant loops. Especially not if they are removed by peeling
             * later.
             */
            return false;
        }
        if (countedLoop.getLimitCheckedIV() instanceof DerivedConvertedInductionVariable) {
            // having to deal with convert between the involved operations is too complex
            FailedShouldStripMineLimitCheckedIVConverts.increment(debug);
            return false;
        }
        InductionVariable limitCheckedIv = countedLoop.getLimitCheckedIV();
        if (limitCheckedIv instanceof BasicInductionVariable) {
            BasicInductionVariable biv = (BasicInductionVariable) limitCheckedIv;
            BinaryArithmeticNode<?> ban = biv.getOp();
            if (ban instanceof GuardedNode) {   // mostly IntegerExactArithmeticNode is of interest
                GuardingNode guard = ((GuardedNode) ban).getGuard();
                if (guard instanceof GuardNode) {
                    if (((GuardNode) guard).getAnchor() != countedLoop.getBody()) {
                        FailedShouldStripMineGuardingIV.increment(debug);
                        return false;
                    }
                }
            }
            if (countedLoop.isInverted()) {
                // only head counted loops with base IVs are supported
                FailedShouldStripMineIVShape.increment(debug);
                return false;
            }
        } else {
            if (!countedLoop.isInverted()) {
                // only inverted loops are supported with derived IVs
                FailedShouldStripMineIVShape.increment(debug);
                return false;
            }
            if (!(limitCheckedIv instanceof DerivedInductionVariable && ((DerivedInductionVariable) limitCheckedIv).getBase() instanceof BasicInductionVariable)) {
                // only simple derived IVs are supported
                FailedShouldStripMineIVShape.increment(debug);
                return false;
            }
            InductionVariable diV = limitCheckedIv;
            if (!(diV instanceof DerivedOffsetInductionVariable)) {
                // only simple derived IVs are supported
                FailedShouldStripMineIVShape.increment(debug);
                return false;
            }
            while (diV instanceof DerivedInductionVariable) {
                diV = ((DerivedInductionVariable) diV).getBase();
            }
            assert diV instanceof BasicInductionVariable : diV;
            ValuePhiNode limitCheckedPhi = (ValuePhiNode) diV.valueNode();
            // we know by the preprocess method a loop only has a single loop end
            assert loop.loopBegin().getLoopEndCount() == 1 : "Strip mining's preprocess step must create a single loop end per loop " + loop.loopBegin();
            InductionVariable backedgeIV = loop.getInductionVariables().get(limitCheckedPhi.valueAt(1));
            if (backedgeIV != limitCheckedIv) {
                FailedShouldStripMineIVShape.increment(debug);
                return false;
            }
        }
        // consider "hidden" parent loops, i.e., loops containing nodes that may expand to child
        // loops during lowering later, this is not a structural limitation of strip mining but a
        // performance consideration, we only want to consider inner most loop for strip mining to
        // keep control flow graph complexity low (given that for outer loops strip mining mostly
        // won't have a noticable benefit in performance)
        for (Node n : loop.inside().nodes()) {
            if (!Loop.canStripMineLoopNode(n)) {
                FailedCanStripMineCanNotStripMine.increment(debug);
                return false;
            }
            if (!Loop.canDuplicateLoopNode(n)) {
                FailedCanStripMineCanNotDuplicateLoop.increment(debug);
                return false;
            }
            if (n instanceof LoopExpandableNode && ((LoopExpandableNode) n).mayExpandToLoop()) {
                // only strip mine inner loops
                FailedCanStripMineHasChildren.increment(debug);
                return false;
            }
        }

        if (countedLoop.isInverted()) {
            CountedLoopInfo ecli = loop.counted();
            if (!ecli.emptyInvertedCountedBackedge()) {
                return false;
            }

            /*
             * Special case inverted loops and backedge values of loop phis (which will be re-routed
             * from inner to outer loops): during strip mining we route inner loop phi backedge
             * values (for inverted loops backedge values for head counted loops loop phis) to outer
             * loop phis. This requires, for inverted loops (for head counted loops its trivially
             * true per definition) that the backedge value dominates the counted loop exit, which
             * is not necessary always the case if there are multi exit loops.
             */
            UsedInvertedSchedule.increment(debug);
            AbstractBeginNode countedLoopExit = countedLoop.getCountedExit();

            ScheduleResult sched = lazySchedule.get();
            HIRBlock countedExit = sched.getNodeToBlockMap().get(countedLoopExit);
            for (PhiNode phi : loopBegin.phis()) {
                for (int i = 1; i < phi.valueCount(); i++) {
                    ValueNode backedgeValue = phi.valueAt(i);
                    HIRBlock backedgeBlock = sched.getNodeToBlockMap().get(backedgeValue);
                    if (backedgeBlock == null) {
                        if (backedgeValue instanceof PhiNode) {
                            backedgeBlock = sched.getNodeToBlockMap().get(((PhiNode) backedgeValue).merge());
                        } else if (backedgeValue instanceof ProxyNode) {
                            backedgeBlock = sched.getNodeToBlockMap().get(((ProxyNode) backedgeValue).proxyPoint());
                        } else {
                            throw GraalError.shouldNotReachHere("Unknown node not part of a schedule " + backedgeValue); // ExcludeFromJacocoGeneratedReport
                        }
                    }
                    assert backedgeBlock != null;
                    if (!backedgeBlock.dominates(countedExit)) {
                        return false;
                    }
                }
            }

            /*
             * Inverted loops: ensure no code is between the counted exit limit test and the loop
             * end. Removing the duplicate loop body would be more complex to do so and graal only
             * supports a limited set of nodes between the limit check and the loop end (Infopoints
             * for example).
             */
            IfNode limitTest = countedLoop.getLimitTest();
            AbstractBeginNode nonExitSuccessor = limitTest.trueSuccessor();
            if (limitTest.trueSuccessor() == countedLoopExit) {
                nonExitSuccessor = limitTest.falseSuccessor();
            }
            if (!(nonExitSuccessor.next() instanceof LoopEndNode)) {
                return false;
            }

        }
        if (countedLoop.isUnsignedCheck()) {
            /*
             * TODO: special case with constant limit detected as unsigned which is not unsigned
             * after strip mining, consider adding support for such cases to counted loop detection
             * in the future
             */
            IntegerStamp limitStamp = (IntegerStamp) countedLoop.getTripCountLimit().stamp(NodeView.DEFAULT);
            IntegerStamp counterStamp = (IntegerStamp) countedLoop.getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);
            if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedLowerBound()) {
                return false;
            }
        }

        if (!countedLoop.counterNeverOverflows()) {
            // overflow can happen
            boolean allowsLoopLimitChecks = context.getOptimisticOptimizations().useLoopLimitChecks(graph.getOptions());
            boolean allowsFloatingGuards = graph.getGuardsStage().allowsFloatingGuards();
            if (!allowsLoopLimitChecks || !allowsFloatingGuards) {
                // we can never create an overflow guard for this loop
                return false;
            }
        }
        return true;
    }

    /**
     * Determine, heuristically, if the given loop should be strip mined to an inner and outer loop.
     *
     * Note that strip mining computes a new limit for the inner loop. There is a potential for
     * overflow if the original limit is close to MAX/MIN value. In order to ensure later when
     * calculating the inner trips (see
     * {@link #calculateNewInnerTripCount(ValueNode, ValueNode, ValueNode, StructuredGraph, CountedLoopInfo, long, ValueNode, FixedWithNextNode)}
     * for details) we do not face the problem of overflow we need to ensure that the original loop
     * limit is at least = stripMax (better >=). Thus, this method, if all heuristics succeeded,
     * returns a logic node that expresses the following check:
     *
     * <pre>
     * int stripMax = getFromOption(); // new inner loop limit after strip mining
     * int loopLimit = getLimitFromCountedLoop(); // original loop limit
     * boolean smallLoop = !(loopLimit < stripMax);
     * </pre>
     *
     * this check can later be used to create a regular guard of the form
     *
     * <pre>
     * guard(smallLoop)
     * </pre>
     *
     * which can be negated because the condition is negated resulting (after guard lowering) in the
     * following control flow
     *
     * <pre>
     * if (loopLimit < stripMax) {
     *     deoptimize();
     * }
     * </pre>
     *
     * @param lazySched
     *
     * @param context
     */
    private boolean shouldStripMine(Loop loop, LoopSafepointPlan graphSafepointOptimizationPlan, LazyValue<ScheduleResult> lazySched, StructuredGraph graph, MidTierContext context) {
        DebugContext debug = graph.getDebug();
        CountedLoopInfo countedLoop = loop.counted();
        InductionVariable counter = countedLoop.getLimitCheckedIV();

        if (shouldStripMine != null && shouldStripMine.test(loop)) {
            return true;
        }

        Stamp counterStamp = counter.valueNode().stamp(NodeView.DEFAULT);
        if (!(counterStamp instanceof IntegerStamp)) {
            return false;
        }
        if (IntegerStamp.getBits(counterStamp) != 32 && IntegerStamp.getBits(counterStamp) != 64) {
            return false;
        }
        if (countedLoop.isInverted()) {
            if (!Options.StripMineInvertedLoops.getValue(graph.getOptions())) {
                return false;
            }
        }

        if (!Options.StripMineALot.getValue(graph.getOptions())) {
            /*
             * Direct improvements are
             *
             * 1. safepoint removal: only if the loop is not already subject to safepoint removal
             *
             * 2. long range check hoisting: check if there are long range checks inside the loop
             * that are subject to hoisting afterwards
             */
            if (!(canHoistLongRangeCheckAfterStripMining(loop) || canRemoveSafepoint(loop, graphSafepointOptimizationPlan, lazySched, context))) {
                FailedShouldStripMineNoBenefit.increment(debug);
                return false;
            }
        }
        try {
            calculateStripMax(counter, true);
        } catch (ArithmeticException e) {
            // strip max * stride overflows integer range -> no safepoint removal, abort
            return false;
        }

        if (!Options.StripMineALot.getValue(graph.getOptions())) {
            double localFrequency = loop.localLoopFrequency();
            if (ProfileSource.isTrusted(loop.localFrequencySource())) {
                if (localFrequency < Options.CountedStripMiningMinFrequency.getValue(graph.getOptions())) {
                    // loop to small to strip mine
                    FailedFrequency.increment(graph.getDebug());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Determine if we can statically tell if {@link LoopSafepointEliminationPhase} will remove the
     * safepoint on all loop ends for the given loop.
     *
     * @param lazySched
     */
    private static boolean canRemoveSafepoint(Loop loop, LoopSafepointPlan graphSafepointOptimizationPlan, LazyValue<ScheduleResult> lazySched, MidTierContext context) {
        final OptionValues opt = loop.loopBegin().getOptions();
        // check if there is any loop end that remains having a safepoint then we should strip
        // mine
        if (LoopSafepointEliminationPhase.Options.RemoveLoopSafepoints.getValue(opt)) {

            /**
             * Special case: loops with a low trip count that is below the strip mined limit and
             * thus never would take an outer loop backedge that are still so expensive that we
             * would increase time to safepoint too much. Do not strip mine, also safepoint removal
             * will ignore them.
             */
            final int innerLoopDefaultTrips = CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips.getValue(opt);
            /*
             * We use our lazy schedule here meaning most of the time we actually dont have a
             * schedule. That is fine, the special case we are treating here is incredibly unlikely
             * and if we have a schedule computed already we use it.
             */
            if (LoopSafepointEliminationPhase.loopIsInIterationRange(loop, innerLoopDefaultTrips) &&
                            (!lazySched.isAvailable() || !LoopSafepointEliminationPhase.loopIsLightweight(loop, lazySched.get()))) {
                return false;
            }

            if (Options.PreferVectorSafepoint.getValue(opt) && VectorIntrinsics.Options.Vectorization.getValue(opt)) {
                /*
                 * Interaction of strip mining and vectorization: The strip mined loop construct is
                 * generally more complex than the original loop. Phi values and induction variables
                 * are offset, additional IVs can be added etc. When we strip mine loops that are
                 * vectorizable we always may destroy a loop's vectorization potential. Thus, we use
                 * a more complex heuristic here.
                 *
                 * The "best" version of a loop from the optimizers perspective is a strip-mined
                 * (because we take care to properly visit a safepoint regularly) and vectorized
                 * (because its faster than the scalar version) loop.
                 *
                 * However, it can happen that strip mining changes a loop in a way that the
                 * vectorizer can no longer vectorize it. There are multiple reasons for this -
                 * foremost the vectorizer also has implementation restrictions/short comings and
                 * there are a few very special corner cases where strip mining changes stamps in a
                 * way that we actually lose information. Since we cannot "undo" strip mining given
                 * its complexity we use a different policy here. Experiments have shown that a loop
                 * version with a vectorized safe point (vectorizer can also vectorize safepoints)
                 * is mostly within a 10% performance ballpark with the strip mined vectorized
                 * version. We can "easily" tell if a loop with a safepoint will be vectorizable. If
                 * so - we prefer the safepoint version of it over the strip mined since its
                 * guaranteed to be vectorized. It can also happen that we cannot vectorize the
                 * safepoint - in this case we use regular strip mining and hope that it vectorizes
                 * (mostly it does).
                 */
                final StructuredGraph graph = loop.loopBegin().graph();
                NodeBitMap loopEndsThatWillSafepoint = graph.createNodeBitMap();
                for (LoopEndNode len : loop.loopBegin().loopEnds()) {
                    if (graphSafepointOptimizationPlan.canSafepoint(len)) {
                        /*
                         * A single loop end that can still safepoint, we should strip mine to get
                         * rid of this.
                         */
                        loopEndsThatWillSafepoint.mark(len);
                    }
                }
                if (loopEndsThatWillSafepoint.count() > 0) {
                    /*
                     * We would insert a safepoint on at least one loop end, see if that loop would
                     * still vectorize in the safepoint version, if so, do not strip mine it.
                     */
                    final LoopBeginNode loopBeginNode = loop.loopBegin();
                    final ControlFlowGraph cfg = loop.loopsData().getCFG();
                    final NodeMap<HIRBlock> nodeToBlock = cfg.getNodeToBlock();

                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before added safepoint check safepoint node for %s", loopBeginNode);

                    // patch the safepoint nodes into loop data structures and CFG
                    for (Node marked : loopEndsThatWillSafepoint) {
                        GraalError.guarantee(marked instanceof LoopEndNode, "We are only collection loop end nodes but then found %s", marked);
                        LoopEndNode loopEndNode = (LoopEndNode) marked;

                        SafepointNode safepointNode = graph.add(new SafepointNode(loopBeginNode));
                        graph.addBeforeFixed(loopEndNode, safepointNode);
                        safepointNode.setLoopLink(loopBeginNode);

                        nodeToBlock.setAndGrow(safepointNode, nodeToBlock.get(loopEndNode));
                    }

                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After added safepoint check safepoint node for %s", loopBeginNode);

                    // invalidate fragment and IV
                    loop.invalidateFragmentsAndIVs();

                    final boolean willVectorize = LoopUtility.potentialVectorLoop(loop, graph, context);

                    for (Node marked : loopEndsThatWillSafepoint) {
                        GraalError.guarantee(marked instanceof LoopEndNode, "We are only collection loop end nodes but then found %s", marked);
                        LoopEndNode loopEndNode = (LoopEndNode) marked;

                        SafepointNode safepointNode = (SafepointNode) loopEndNode.predecessor();
                        assert safepointNode.getLoopLink() == loopBeginNode : Assertions.errorMessage("Must be same safepoint", safepointNode, safepointNode.getLoopLink(), loopBeginNode);

                        // delete the node again from the block map
                        nodeToBlock.removeKey(safepointNode);

                        graph.removeFixed(safepointNode);

                    }

                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Afre removing safepoint check safepoint node for %s", loopBeginNode);

                    // clear again to drop safepoint
                    loop.invalidateFragmentsAndIVs();

                    // only strip mine if we cannot vectorize this loop
                    return !willVectorize;
                }
            } else {
                for (LoopEndNode len : loop.loopBegin().loopEnds()) {
                    if (graphSafepointOptimizationPlan.canSafepoint(len)) {
                        // a single loop end that can still safepoint, we should strip mine to get
                        // rid of this
                        return true;
                    }
                }
            }
        }
        // the range does not force a safepoint, figure out if guest safepoints may still be needed,
        // in such a case strip mining will allow us to remove the guest safepoint
        for (Node node : loop.whole().nodes()) {
            if (node instanceof CommitAllocationNode || node instanceof AbstractNewObjectNode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the given loop contains guards that operate on 64bit integer induction
     * variables of the loop. If so, strip mining can rewrite them to use 32bit base induction
     * variables that may enable long-to-int range check elimination.
     */
    private static boolean canHoistLongRangeCheckAfterStripMining(Loop loop) {
        for (Node inside : loop.inside().nodes()) {
            if (inside instanceof GuardNode) {
                GuardNode guard = (GuardNode) inside;
                LogicNode c = guard.getCondition();
                if (c instanceof IntegerLessThanNode || c instanceof IntegerBelowNode) {
                    CompareNode compare = (CompareNode) c;
                    InductionVariable ivX = loop.getInductionVariables().get(compare.getX());
                    InductionVariable ivY = loop.getInductionVariables().get(compare.getY());
                    if (ivX == null && ivY == null) {
                        continue;
                    }
                    InductionVariable iv;
                    if (ivX == null || (ivY != null && ivY.getLoop().getCFGLoop().getDepth() > ivX.getLoop().getCFGLoop().getDepth())) {
                        iv = ivY;
                        if (is64BitConstantStrideIV(iv)) {
                            return true;
                        }
                    } else {
                        iv = ivX;
                        if (is64BitConstantStrideIV(iv)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean is64BitConstantStrideIV(InductionVariable iv) {
        ValueNode value = iv.valueNode();
        if (isLong(value)) {
            return iv.isConstantStride();
        }
        return false;
    }

    private static ConstantNode forConstantVal(long val, Stamp counterStamp, StructuredGraph graph) {
        return ConstantNode.forIntegerBits(((IntegerStamp) counterStamp).getBits(), val, graph);
    }

    /**
     * Calculate the {@link CountedLoopInfo#constantMaxTripCount()} of the loop given the induction
     * variable and the strip max constant.
     *
     * @throws ArithmeticException if the new trip count would overflow int range
     */
    private static long calculateStripMax(InductionVariable iv, boolean forEntryCheck) throws ArithmeticException {
        StructuredGraph graph = iv.graph();
        final long absStride = NumUtil.safeAbs(iv.constantStride(), IntegerStamp.getBits(iv.strideNode().stamp(NodeView.DEFAULT)));
        if (absStride > Integer.MAX_VALUE) {
            throw new ArithmeticException();
        }
        int stripMax = Options.CountedStripMiningInnerLoopTrips.getValue(graph.getOptions());
        // div once here in case an overflow occurs we do not even want to create the entry check
        Math.multiplyExact((int) absStride, stripMax);
        if (forEntryCheck) {
            return stripMax;
        }
        return Math.multiplyExact((int) absStride, stripMax);
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
