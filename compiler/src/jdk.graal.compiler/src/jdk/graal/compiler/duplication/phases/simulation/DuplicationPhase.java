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
package jdk.graal.compiler.duplication.phases.simulation;

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterDuplicatedNodes;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterSimulationBasedDuplicationRegionSinking;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterSimulationBasedDuplicationSplitRegions;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.timerDuplication;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.timerPhaseIteration;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.timerRegionComputation;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.DuplicateALot;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.DuplicationBudgetFactor;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.DuplicationBudgetFactorLate;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.ExcludeFunctionFromDuplication;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.IgnoreBadDuplications;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.MinimalRegions;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import jdk.graal.compiler.duplication.opt.BudgetCostModel;
import jdk.graal.compiler.duplication.opt.DuplicationNodeCountBudget;
import jdk.graal.compiler.duplication.util.DuplicationGraphSizeMeterClosable;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.duplication.util.DuplicationUtil.DuplicationRegion;

import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.RawConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.graal.compiler.phases.contract.NodeCostUtil;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

/**
 * Performs simulation based duplication (SBD) on a graph. Simulation based duplication first
 * performs a {@linkplain HighTierDuplicationSimulationPhase} simulation on the graph to collect
 * optimization opportunities after duplication. DBDS returns a summary of opportunities for each
 * {@linkplain EndNode} at a {@linkplain MergeNode}. Then SBD tries to find additional opportunities
 * by looking at the duplication regions. Opportunities include
 * <ul>
 * <li>From DBDS: Conditional Elimination of Control Split Nodes
 * <li>From DBDS: Conditional Elimination of Guard Nodes
 * <li>From DBDS: Canonicalizations (Usage of Node Costs: Saved Cycles)
 * <li>Region based: Read Elimination Opportunities
 * <li>Region based: Non-escaping objects if objects escape on merge phis.
 * <li>Region based: Same condition for multiple tests
 * <li>Region based: Removal of a merge itself (saves the jump)
 * <li>Region based: Sinking duplications: All paths after the merge are sinking.
 * </ul>
 *
 * Additionally DBDS saves the {@linkplain NodeSize} costs for all nodes that effectively need to be
 * duplicated for a duplication operation.
 *
 * SBD sorts the possible duplications by their benefit and performs them one after another. It
 * maintains a budget based cost model and tries to avoid duplications that have very high cost with
 * a very small benefit. If the given budget is reached or no useful further duplications can be
 * made duplications will stop.
 *
 * As a cleanup SBD performs an incremental canonicalization of all the duplicated nodes and a full
 * conditional elimination to eliminate/optimize all the opportunities simulation found before.
 *
 * A trivial example of SBD can be seen below:
 *
 * <pre>
 *  phi = ...
 *  if (condition) {
 *      phi = constantValue1
 *      // end1
 *  } else {
 *      phi = non-constant
 *      // end2
 *  }
 *  // merge
 *  if (phi == constantValue2) {
 *      // a lot of complex code
 *  }
 * </pre>
 *
 * DBDS returns a list of opportunities for duplication at end1 and end2. SBD then duplicates the
 * code into the predecessor branches. After that the if in the true branch can be removed.
 *
 * <pre>
 *  phi = ...
 *  if (condition) {
 *      phi = constantValue1
 *      // the following if can now be eliminated
 *      if (phi == constantValue2) {
 *          // a lot of complex code
 *      }
 *  } else {
 *      phi = non-constant
 *      if (phi == constantValue2) {
 *          // a lot of complex code
 *      }
 *  }
 * </pre>
 */
public class DuplicationPhase extends BasePhase<CoreProviders> {
    public static final DuplicationConfig FACTORS_INCLUDING_PEA = new DuplicationConfig(32, 16, 1, 1, true, true, 0);

    /**
     * Maximum number of code size increase in terms of {@linkplain NodeSize} for one execution of
     * the {@linkplain DuplicationPhase} on a {@linkplain StructuredGraph}.
     */
    private static final int DUPLICATION_PHASE_CODE_SIZE_INCREASE_FACTOR = 4;

    private final CanonicalizerPhase canonicalizer;
    private final SimulationConfig simulationConfig;
    private final DuplicationConfig duplicationConfig;
    private final boolean dupALot;
    private final boolean beforeFixedReads;
    private final boolean replaceInputsWithConstants;

    public interface VectorizationCheck {
        boolean isVectorizable(Loop loop, StructuredGraph g, CoreProviders providers);
    }

    private final VectorizationCheck vectCheck;

    public DuplicationPhase(SimulationConfig config, boolean beforeFixedReads, boolean replaceInputsWithConstants, DuplicationConfig factors, CanonicalizerPhase canonicalizer,
                    VectorizationCheck vectCheck,
                    OptionValues options) {
        this(config, beforeFixedReads, replaceInputsWithConstants, factors, canonicalizer, DuplicateALot.getValue(options), vectCheck);
    }

    public DuplicationPhase(SimulationConfig config, boolean beforeFixedReads, boolean replaceInputsWithConstants, DuplicationConfig factors, CanonicalizerPhase canonicalizer,
                    OptionValues options) {
        this(config, beforeFixedReads, replaceInputsWithConstants, factors, canonicalizer, DuplicateALot.getValue(options), (x, y, z) -> false);
    }

    public DuplicationPhase(SimulationConfig config, boolean beforeFixedReads, boolean replaceInputsWithConstants, DuplicationConfig factors, CanonicalizerPhase canonicalizer,
                    boolean dupALot, VectorizationCheck vectCheck) {
        this.canonicalizer = canonicalizer;
        this.duplicationConfig = factors;
        this.dupALot = dupALot;
        this.simulationConfig = config;
        this.beforeFixedReads = beforeFixedReads;
        this.replaceInputsWithConstants = replaceInputsWithConstants;
        this.vectCheck = vectCheck;
    }

    public DuplicationPhase copyWithoutReplaceConstantInputs() {
        return new DuplicationPhase(simulationConfig, beforeFixedReads, false, duplicationConfig, canonicalizer, dupALot, vectCheck);
    }

    private enum DuplicationBoundary {
        ADVANCE,
        STOP
    }

    @Override
    public float codeSizeIncrease() {
        return DUPLICATION_PHASE_CODE_SIZE_INCREASE_FACTOR;
    }

    private class DuplicationContext {
        private final StructuredGraph graph;
        private final DuplicationNodeCountBudget graphBudget;
        private SimulationEndInfo[] potentialDuplications;
        private DuplicationUtil duplicationUtil;
        private final CoreProviders providers;
        private SimplifierTool simplifierTool;
        private final DuplicationCostFunction duplicationCostFunction;
        boolean runConditionalEliminationCleanUp;
        boolean runReadEliminationCleanUp;
        private int iteration;
        private int nrOfPhisAddedLastIteration;
        private final EconomicSetNodeEventListener singleDuplicationListener;

        DuplicationContext(StructuredGraph graph, CoreProviders providers, DuplicationCostFunction duplicationCostFunction, int maxGraphSize) {
            this.graph = graph;
            this.providers = providers;
            this.duplicationCostFunction = duplicationCostFunction;
            this.singleDuplicationListener = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.NODE_ADDED, NodeEvent.ZERO_USAGES));
            this.graphBudget = new DuplicationNodeCountBudget(maxGraphSize);
        }

        boolean needsDuplicationInit() {
            return simplifierTool == null;
        }

        void initDuplicationTooling() {
            this.simplifierTool = GraphUtil.getDefaultSimplifier(providers, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), graph.getOptions());
            this.duplicationUtil = new DuplicationUtil(graph, simplifierTool);
        }

        void reset() {
            potentialDuplications = null;
            runReadEliminationCleanUp = false;
        }

        void calculateCreatedPhis() {
            nrOfPhisAddedLastIteration = 0;
            for (Node changed : singleDuplicationListener.getNodes()) {
                if (changed.isAlive()) {
                    if (changed instanceof PhiNode) {
                        nrOfPhisAddedLastIteration++;
                    }
                }
            }
        }

        @SuppressWarnings("try")
        private DuplicationBoundary processSingleDuplication(SimulationEndInfo s) {
            EndNode end = s.getEnd();
            AbstractMergeNode merge = end.merge();
            DebugContext debug = graph.getDebug();
            if (s.getEnd().isAlive() && merge.isAlive() && merge == s.getOriginalMerge()) {
                if (!DuplicationUtil.mergeQualifiesForDuplication(merge)) {
                    return DuplicationBoundary.ADVANCE;
                }
                boolean splits = s.splits();
                boolean sinks = s.sinks();
                FixedNode regionEnd = null;
                try (DebugCloseable c = timerRegionComputation.start(debug)) {
                    regionEnd = DuplicationUtil.findRegionEnd(merge, splits, sinks);
                }
                if (regionEnd == merge) {
                    return DuplicationBoundary.ADVANCE;
                }
                if (regionEnd instanceof ControlSplitNode) {
                    if (graph.getGuardsStage().allowsFloatingGuards()) {
                        if (!DuplicationUtil.mayRemoveSplit((ControlSplitNode) regionEnd)) {
                            return DuplicationBoundary.ADVANCE;
                        }
                    }
                }
                DuplicationDecision decision = null;
                if (!(regionEnd instanceof ControlFlowAnchored)) {
                    singleDuplicationListener.getNodes().clear();
                    try (DebugCloseable c = timerDuplication.start(debug)) {
                        decision = duplicationCostFunction.shouldDuplicate(duplicationConfig, s, regionEnd, nrOfPhisAddedLastIteration, iteration);
                        if (decision != null) {
                            regionEnd = decision.getTarget();
                        } else {
                            regionEnd = null;
                        }
                    }
                    if (regionEnd != null) {
                        DuplicationRegion region = DuplicationUtil.createRegion(null, merge, regionEnd, end);
                        if (needsDuplicationInit()) {
                            initDuplicationTooling();
                        }
                        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before duplicating %s -> %s| %s", end, merge, decision);
                        try (DebugCloseable c = timerDuplication.start(debug);
                                        NodeEventScope nes = graph.trackNodeEvents(singleDuplicationListener)) {
                            duplicationUtil.duplicate(merge, region, canonicalizer, providers);
                        }
                        calculateCreatedPhis();
                        iteration++;
                        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating %s -> %s| regionend %s", end, merge, regionEnd);
                        if (debug.areCountersEnabled()) {
                            if (regionEnd instanceof ControlSplitNode) {
                                counterSimulationBasedDuplicationSplitRegions.increment(debug);
                            }
                            if (sinks) {
                                counterSimulationBasedDuplicationRegionSinking.increment(debug);
                            }
                        }
                        debug.log(DebugContext.VERBOSE_LEVEL, "Simulation Based Duplication: Duplicating end %s at merge %s with target node %s", end, merge, regionEnd);
                        graph.getOptimizationLog().report(DuplicationPhase.class, "Duplication", end);
                        duplicationCostFunction.afterDuplication(s);
                        if (s.killsBranches()) {
                            runConditionalEliminationCleanUp = true;
                        }
                        if (decision.requiresReadElimination()) {
                            runReadEliminationCleanUp = true;
                        }
                    } else {
                        debug.log(DebugContext.VERBOSE_LEVEL, "Simulation Based Duplication: Not duplicating end %s at merge %s", end, merge);
                    }
                }
            }
            return DuplicationBoundary.ADVANCE;
        }

        @SuppressWarnings("try")
        private double duplicate() {
            double benefitBefore = duplicationCostFunction.overallBenefit();
            try (DebugCloseable c = DuplicationDebugUtil.duplicateTime.start(graph.getDebug())) {
                Arrays.sort(potentialDuplications);
                for (int i = 0; i < potentialDuplications.length; i++) {
                    CompilationAlarm.checkProgress(graph);
                    if (graphBudget.budgetExceeded(graph)) {
                        DuplicationDebugUtil.nodeCountBudgetReached.increment(graph.getDebug());
                        return duplicationCostFunction.overallBenefit() - benefitBefore;
                    }
                    SimulationEndInfo s = potentialDuplications[i];
                    DuplicationBoundary boundary = processSingleDuplication(s);
                    if (duplicationCostFunction.stopDuplication(graph.getDebug()) || boundary == DuplicationBoundary.STOP) {
                        break;
                    }
                }
                assert DuplicationUtil.verifyDuplication(graph);
                return duplicationCostFunction.overallBenefit() - benefitBefore;
            }
        }

        @SuppressWarnings("try")
        private void simulate(CoreProviders context) {
            try (DebugCloseable c = DuplicationDebugUtil.simulateTime.start(graph.getDebug())) {
                CanonicalizerTool defaultSimplifier = GraphUtil.getDefaultSimplifier(providers, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), graph.getOptions());
                HighTierDuplicationSimulationPhase simulator = new HighTierDuplicationSimulationPhase(graph.getOptions(), simulationConfig, defaultSimplifier, vectCheck);
                simulator.apply(graph, context);
                potentialDuplications = simulator.getImprovements();
            }
        }

    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunAfter(this, StageFlag.LOOP_OVERFLOWS_CHECKED, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FLOATING_READS, graphState),
                        canonicalizer.notApplicableTo(graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        /*
         * Check our preconditions before making any modifications to the graph. If the verification
         * fails later on during duplication, we want to be sure that the problem is not caused by
         * an error in a previous phase that made, e.g., the graph unschedulable.
         */
        assert DuplicationUtil.verifyDuplication(graph);

        if (!DuplicationUtil.graphQualifiesForDuplication(graph)) {
            return;
        }
        OptionValues options = graph.getOptions();
        if (shouldExclude(graph)) {
            return;
        }

        final DebugContext debug = graph.getDebug();
        final boolean hotGlobalSelfTime = GlobalProfilesOptimizationUtility.shouldPrioritizeForOptimization(graph);
        int rewrittenGuardInputs = DuplicationUtil.splitRegularMergeGuardInputs(graph);
        if (rewrittenGuardInputs > 0) {
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After splitting %s guard-phi merge inputs before duplication", rewrittenGuardInputs);
            assert DuplicationUtil.verifyDuplication(graph);
        }
        int nodeCostGraphSize = NodeCostUtil.computeGraphSize(graph);
        final double codeSizeIncreaseFactor = codeSizeIncreaseFactor(options, nodeCostGraphSize, hotGlobalSelfTime);
        final int nodeCostMaxGraphSize = DuplicationOptions.MaxGraphSizeNodeCost.getValue(options);
        if (nodeCostGraphSize > nodeCostMaxGraphSize) {
            /*
             * Graph that is already too large which can result in problems during code installation
             * later.
             */
            return;
        }
        final int budget = (int) (nodeCostGraphSize * codeSizeIncreaseFactor);
        final boolean ignoreBadDuplications = !dupALot && IgnoreBadDuplications.getValue(options);
        final boolean considerLockOpportunities = graph.getNodes(MonitorEnterNode.TYPE).count() > 0;
        final boolean considerEscapeAnalysisOpportunities = graph.isBeforeStage(StageFlag.FINAL_PARTIAL_ESCAPE) || graph.getGraphState().isDuringStage(StageFlag.FINAL_PARTIAL_ESCAPE);
        final DuplicationCostFunction dupFunction = new NodeCostModelBasedDuplicationCostFunction(budget, ignoreBadDuplications, dupALot, MinimalRegions.getValue(options),
                        considerLockOpportunities, considerEscapeAnalysisOpportunities);
        final BudgetCostModel costModel = dupFunction.costModel();
        try (DebugCloseable sizeClosable = DuplicationGraphSizeMeterClosable.create(graph, dupFunction)) {
            boolean iterate = false;
            int iterations = 0;
            double prevBenefit = 0D;
            EconomicSetNodeEventListener changeListener = null;
            DuplicationContext duplicationContext = null;
            while (duplicationContext == null || duplicationContext.graphBudget.inBudget(graph)) {
                try (Scope s = debug.scope("Duplication_Iteration_" + iterations); DebugCloseable c = timerPhaseIteration.start(debug)) {
                    iterate = false;
                    if (DuplicationUtil.graphQualifiesForDuplication(graph)) {
                        final int sizeBefore = debug.areCountersEnabled() ? graph.getNodeCount() : 0;
                        // lazy change listener initialization
                        if (changeListener == null) {
                            changeListener = new EconomicSetNodeEventListener();
                        } else {
                            changeListener.getNodes().clear();
                        }
                        // lazy duplication context initialization
                        if (duplicationContext == null) {
                            duplicationContext = new DuplicationContext(graph, context, dupFunction, nodeCostMaxGraphSize);
                        } else {
                            duplicationContext.reset();
                        }
                        duplicationContext.simulate(context);
                        double newBenefit = 0;
                        try (NodeEventScope nes = graph.trackNodeEvents(changeListener)) {
                            newBenefit = duplicationContext.duplicate();
                            if (newBenefit == 0.0D) {
                                /*
                                 * Early exit, if there was no benefit there is no cleanup
                                 * necessary.
                                 */
                                return;
                            }
                            if (debug.areCountersEnabled()) {
                                counterDuplicatedNodes.add(debug, graph.getNodeCount() - sizeBefore);
                            }
                            newBenefit = newBenefit - prevBenefit;
                            if (!changeListener.getNodes().isEmpty()) {
                                if (beforeFixedReads) {
                                    /*
                                     * Early read elimination performs better elimination of unsafe
                                     * accesses, therefore we always execute it as the PEA is not
                                     * capable of performing all necessary optimizations.
                                     */
                                    if (duplicationConfig.isConsiderReadEliminations() && duplicationContext.runReadEliminationCleanUp &&
                                                    GraalOptions.OptReadElimination.getValue(graph.getOptions())) {
                                        try (DebugCloseable c1 = DuplicationDebugUtil.cleanUpRETime.start(graph.getDebug())) {
                                            new ReadEliminationPhase(canonicalizer).apply(graph, context);
                                            duplicationContext.runReadEliminationCleanUp = false;
                                        }
                                    }
                                } else {
                                    /*
                                     * Late duplication is before PRE the last optimization before
                                     * code generation. There is no follow up optimization suite
                                     * that performs all the necessary opts to get the improvements
                                     * found during simulation, thus we need to always perform them.
                                     */
                                    lateCleanUp(graph, context, replaceInputsWithConstants, simulationConfig, duplicationConfig, duplicationContext, canonicalizer);
                                }
                            }

                            CompilationAlarm.checkProgress(graph);
                            iterate = performIteration(newBenefit, prevBenefit, iterations, options, hotGlobalSelfTime);

                            if (!iterate) {
                                /*
                                 * Early return to avoid unnecessary cleanup that is anyway
                                 * performed by follow up optimizations
                                 */
                                canonicalizer.applyIncremental(graph, context, changeListener.getNodes());
                                return;
                            }
                            if (beforeFixedReads) {
                                earlyCleanUp(graph, context, simulationConfig, duplicationContext, canonicalizer);
                            }
                            // canonicalize what is left to do
                            canonicalizer.applyIncremental(graph, context, changeListener.getNodes());
                            changeListener.getNodes().clear();
                        }
                        nodeCostGraphSize = correctCostModel(graph, nodeCostGraphSize, costModel);
                        prevBenefit = newBenefit;
                    }
                    if (!iterate || dupFunction.stopDuplication(debug) || nodeCostGraphSize >= nodeCostMaxGraphSize) {
                        return;
                    }
                    iterations++;
                }
            }
        } finally {
            assert beforeFixedReads || verifyLateDuplication(graph);
        }
    }

    private static int correctCostModel(StructuredGraph graph, int oldGraphSize, BudgetCostModel costModel) {
        /*
         * Adjust correct graph size after duplication, canonicalization and cleanup.
         */
        int newGraphSize = NodeCostUtil.computeGraphSize(graph);
        int diff = oldGraphSize - newGraphSize;
        if (diff <= 0) {
            costModel.setUsedBudget(-diff);
        } else {
            costModel.setUsedBudget(0);
        }
        return newGraphSize;
    }

    private static final int DUPALOT_MAX_ITERATIONS = 64;

    private static final double MIN_INITIAL_BENEFIT = 4;

    private static int maxIterations(OptionValues options, boolean hotGlobalSelfTime) {
        if (hotGlobalSelfTime) {
            return DuplicationOptions.MaxSimulationIterationsHotCode.getValue(options);
        }
        return DuplicationOptions.MaxSimulationIterations.getValue(options);
    }

    private boolean performIteration(double newBenefit, double prevBenefit, int iterations, OptionValues options, boolean hotGlobalSelfTime) {
        boolean iterate = false;
        double necessaryNewBenefit = 0;
        /*
         * Benefit is computed relative to the normalized probability. So a benefit of e.g. 1 means
         * there has been e.g. a canonicalization that gains NODE_CYCLES_1 in the hottest basic
         * block of the method.
         */
        if (iterations == 0) {
            necessaryNewBenefit = MIN_INITIAL_BENEFIT;
        } else {
            necessaryNewBenefit = prevBenefit + prevBenefit * 0.25 * (iterations + 1);
        }
        iterate = newBenefit > necessaryNewBenefit;
        /*
         * There are very large graphs that always show an improvement therefore need to place a
         * hard upper limit.
         */
        if (dupALot) {
            iterate = iterations <= DUPALOT_MAX_ITERATIONS && newBenefit > 0;
        } else {
            iterate &= iterations + 1 < maxIterations(options, hotGlobalSelfTime);
        }

        if (!beforeFixedReads) {
            iterate = false;
        }
        return iterate;
    }

    @SuppressWarnings("try")
    private static void earlyCleanUp(StructuredGraph graph, CoreProviders context, SimulationConfig simulationConfig, DuplicationContext duplicationContext, CanonicalizerPhase canonicalizer) {
        try (DebugCloseable c = DuplicationDebugUtil.cleanUpCETime.start(graph.getDebug())) {
            if (simulationConfig.findConditionalEliminations() && duplicationContext.runConditionalEliminationCleanUp && GraalOptions.ConditionalElimination.getValue(graph.getOptions())) {
                new ConditionalEliminationPhase(canonicalizer, false, false).apply(graph, context);
            }
            if (GraalOptions.OptConvertDeoptsToGuards.getValue(graph.getOptions())) {
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
            }
        }
    }

    @SuppressWarnings("try")
    private static void lateCleanUp(StructuredGraph graph, CoreProviders context, boolean replaceInputsWithConstants, SimulationConfig simulationConfig, DuplicationConfig duplicationConfig,
                    DuplicationContext duplicationContext, CanonicalizerPhase canonicalizer) {
        assert context instanceof LowTierContext : context;
        /*
         * Running a late read elimination to clean up after duplication. Must be done before the
         * fix reads phase as the read elimination changes the graph and schedule.
         */
        if (duplicationConfig.isConsiderReadEliminations()) {
            new ReadEliminationPhase(canonicalizer, false).apply(graph, context);
        }
        if (simulationConfig.findConditionalEliminations() && duplicationContext.runConditionalEliminationCleanUp) {
            new RawConditionalEliminationPhase(replaceInputsWithConstants).apply(graph, (LowTierContext) context);
        }
    }

    private double codeSizeIncreaseFactor(OptionValues options, int nodeCostGraphSize, boolean hotGlobalSelfTime) {
        if (hotGlobalSelfTime) {
            return DuplicationOptions.DuplicationBudgetFactorHotCode.getValue(options);
        }
        final int smallGraphSize = DuplicationOptions.SmallGraphSize.getValue(options);
        if (nodeCostGraphSize < smallGraphSize) {
            return DuplicationOptions.SmallGraphDuplicationBudgetFactor.getValue(options);
        }
        return beforeFixedReads ? DuplicationBudgetFactor.getValue(options) : DuplicationBudgetFactorLate.getValue(options);
    }

    private static boolean shouldExclude(StructuredGraph graph) {
        if (graph.method() == null) {
            return false;
        }
        String exclude = ExcludeFunctionFromDuplication.getValue(graph.getOptions());
        return exclude != null && MethodFilter.parse(exclude).matches(graph.method());
    }

    private static boolean verifyLateDuplication(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ValuePhiNode) {
                ValuePhiNode phi = (ValuePhiNode) n;
                if (phi.valueAt(0) instanceof AMD64AddressNode) {
                    GraalError.shouldNotReachHere("Address phi nodes are not supported by the backend."); // ExcludeFromJacocoGeneratedReport
                }
            }
        }
        return true;
    }

}
