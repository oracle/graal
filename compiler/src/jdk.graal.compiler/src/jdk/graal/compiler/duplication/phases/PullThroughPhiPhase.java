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
package jdk.graal.compiler.duplication.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.duplication.opt.BudgetCostModel;
import jdk.graal.compiler.duplication.opt.OptimizationEffect;
import jdk.graal.compiler.duplication.util.DuplicationUtil.CFGFrequencyInfo;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Binary;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Unary;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.graal.compiler.phases.contract.NodeCostUtil;

/**
 * Performs a limited form of tail duplication on floating nodes that are known to be optimizable if
 * duplicated.
 *
 * For example:
 *
 * <pre>
 * phi = 0
 * if (condition) {
 *    // some unknown value
 *    phi = call1()
 * } else {
 *    // a constant
 *    phi = 3
 * }
 * x = phi * 3
 * y = phi * call2()
 * // some code using x
 * usage(x)
 * </pre>
 *
 * The first usage of the phi in {@code x = phi * 3} can either have the value {@code call() * 3} or
 * {@code 3 * 3 = 9}. We know that {@code x = phi * 3} can be optimized if it is carried out in one
 * of the preceding branches. Thus we can duplicate the operation and pull its result through a
 * newly created phi (phi1 in the following example) in the predecessors.
 *
 * <pre>
 * phi = 0
 * phi1 = 0
 * if (condition) {
 *    // some unknown value
 *    tmp = call1()
 *    phi = tmp
 *    phi1 = tmp * 3
 * } else {
 *    // a constant
 *    phi = 3
 *    phi1 = 9
 * }
 * y = phi * call2()
 * // code using new phi
 * usage(phi1)
 * </pre>
 *
 * One pull operation can often lead to subsequent ones, thus this optimization is carried out
 * iteratively with a budget cost model that tries to find good candidates for optimization. The
 * cost model has a hard budget limit that ensures this optimization never explodes a graph.
 *
 *
 * PullThroughPhi is an optimization that performs the replacement of floating nodes for value phis
 * and new (canonicalized) inputs which can heavily increase register pressure if the old phi and
 * inputs have other usages and thus cannot be removed.
 *
 */
public class PullThroughPhiPhase extends BasePhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "PullThroughPhiOptimization: Enable floating node duplication over multiple phi nodes at once.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TryExplodeOverPhis = new OptionKey<>(true);
        @Option(help = "PullThroughPhiOptimization: Enable floating node duplication over phis where the target node has different phis as input.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TryPhiPhiPulls = new OptionKey<>(true);
        @Option(help = "PullThroughPhiOptimization: Percentage in node cost graph size for the floating node duplication budget. " +
                       "Computed relative to the method's graph size.", type = OptionType.Debug)
        public static final OptionKey<Double> PullThroughPhiCodeSizeIncrease = new OptionKey<>(0.1/*== 10% of the initial graph size*/);
        @Option(help = "See PullThroughPhiCodeSizeIncrease", type = OptionType.Debug)
        public static final OptionKey<Double> PullThroughPhiCodeSizeIncreaseHotCode = new OptionKey<>(2.5/*== 250% of the initial graph size*/);
        @Option(help = "PullThroughPhiOptimization: Cost/Benefit heuristic for EE floating node duplication: " +
                       "reduces cost by a constant factor when comparing with relative benefit.", type = OptionType.Debug)
        public static final OptionKey<Double> CostReductionFactor = new OptionKey<>(32D);
        @Option(help = "See CostReductionFactor.", type = OptionType.Debug)
        public static final OptionKey<Double> CostReductionFactorHotCode = new OptionKey<>(128D);
        @Option(help = "PullThroughPhiOptimization: Maximum number of algorithm iterations per optimization invocation.", type = OptionType.Debug)
        public static final OptionKey<Integer> MaximumTransitiveEnabledPullFactor = new OptionKey<>(2);
        @Option(help = "PullThroughPhiOptimization: Ignore low frequency branches during duplication.", type = OptionType.Debug)
        public static final OptionKey<Double> MinBlockFrequencyPull = new OptionKey<>(0.66);
        @Option(help = "PullThroughPhiOptimization: Abstract cost for the creation of a new live value: new values can have a negative" +
                       "impact on register allocation, therefore we penalize it.", type = OptionType.Debug)
        public static final OptionKey<Integer> CostNewLiveVariable = new OptionKey<>(4);
        // @formatter:on
    }

    private static final CounterKey PushedCanonicalizations = DebugContext.counter("PullThroughPhi_PushedCanonicalizations");
    private static final CounterKey PushedDuplications = DebugContext.counter("PullThroughPhi_PushedDuplications");
    private static final CounterKey PhiPhiPull = DebugContext.counter("PullThroughPhi_PhiPhiPull");
    private static final CounterKey AbovePhiExploded = DebugContext.counter("PullThroughPhi_ExplodedAbovePhi");
    private static final CounterKey BudgetExceeded = DebugContext.counter("PullThroughPhi_BudgetExceeded");
    private static final CounterKey ModelIterations = DebugContext.counter("PullThroughPhi_ModelIterations");
    private static final CounterKey BadPullsIgnored = DebugContext.counter("PullThroughPhi_BadPullsIgnored");

    private static final float PHASE_MAX_CODE_SIZE_INCREASE = 1.5F;

    private final CanonicalizerPhase canonicalizer;

    public PullThroughPhiPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public float codeSizeIncrease() {
        return PHASE_MAX_CODE_SIZE_INCREASE;
    }

    static class PullCostModel extends BudgetCostModel {

        PullCostModel(double budget) {
            super(budget);
        }

        static PullCostModel create(StructuredGraph graph) {
            int graphSize = NodeCostUtil.computeGraphSize(graph);
            double codeSizeIncreaseBudget = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.PullThroughPhiCodeSizeIncrease.getValue(graph.getOptions()),
                            Options.PullThroughPhiCodeSizeIncreaseHotCode.getValue(graph.getOptions()));
            codeSizeIncreaseBudget *= graphSize;
            return new PullCostModel(codeSizeIncreaseBudget);
        }

        static double getMaxPhiValueCount(List<ValuePhiNode> initialWorkList) {
            int maxPhiINput = 0;
            for (ValuePhiNode phi : initialWorkList) {
                maxPhiINput = Math.max(maxPhiINput, phi.valueCount());
            }
            return maxPhiINput;
        }

    }

    private static class DomCheck {
        private final ControlFlowGraph cfg;

        DomCheck(ControlFlowGraph cfg) {
            this.cfg = cfg;
        }

        boolean mergePostDom(AbstractMergeNode a, AbstractMergeNode b) {
            return cfg.blockFor(a).getDominatorDepth() > cfg.blockFor(b).getDominatorDepth();
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return canonicalizer.notApplicableTo(graphState);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {

        List<ValuePhiNode> nodes = null;
        for (MergeNode merge : graph.getNodes(MergeNode.TYPE)) {
            outer: for (ValuePhiNode phi : merge.valuePhis()) {
                for (Node usage : phi.usages()) {
                    if (canPullThroughPhi(usage)) {
                        if (nodes == null) {
                            nodes = new ArrayList<>();
                        }
                        nodes.add(phi);
                        continue outer;
                    }
                }
            }
        }
        if (nodes == null) {
            return;
        }

        DebugContext.counter("PullThrooughPhiListSize_" + nodes.size()).increment(graph.getDebug());

        final CanonicalizerPhase canonicalizerWithoutSimplification = canonicalizer.copyWithoutSimplification();

        final int maxIterations = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Integer.MAX_VALUE,
                        nodes.size() * Options.MaximumTransitiveEnabledPullFactor.getValue(graph.getOptions()));
        SimplifierTool defaultSimplifier = GraphUtil.getDefaultSimplifier(context, false, graph.getAssumptions(), graph.getOptions());
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();
        DomCheck domCheck = new DomCheck(cfg);
        final ControlFlowGraph cf = cfg;
        final double minFrequencyMerge = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, 0D, Options.MinBlockFrequencyPull.getValue(graph.getOptions()));
        /*
         * We use the frequency info here. It calculates a normalized frequency in the range of
         * [0,1] for a basic block by putting it into relation with the highest frequency basic
         * block in a compilation unit.
         */
        CFGFrequencyInfo frequencyInfo = new CFGFrequencyInfo(cfg);
        // do not ignore basic blocks if we cannot trust the profiles, process everything
        // within bounds of the code size cost model
        if (frequencyInfo.isTrusted()) {
            nodes.removeIf(x -> frequencyInfo.getFrequencyNormalizedToMaxFrequency(cf.blockFor(x.merge())) < minFrequencyMerge);
        }
        Collections.sort(nodes, (x, y) -> Double.compare(frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(y.merge())),
                        frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(x.merge()))));
        PullCostModel model = PullCostModel.create(graph);
        PullCostFunction puller = new PullCostFunction(nodes, graph, cfg, model, frequencyInfo);
        final EconomicSetNodeEventListener localChanges = new EconomicSetNodeEventListener(EnumSet.of(Graph.NodeEvent.NODE_ADDED, Graph.NodeEvent.ZERO_USAGES));
        final EconomicSetNodeEventListener globalChanges = new EconomicSetNodeEventListener();
        int iterations = 0;
        try (NodeEventScope s = graph.trackNodeEvents(globalChanges)) {

            while (puller.hasNext()) {
                CompilationAlarm.checkProgress(graph);
                if (iterations > maxIterations) {
                    break;
                }
                /*
                 * note: we reuse the node event set for additional canonicalizations to process
                 * that result from using addOrUnique after changing the inputs of the duplicated
                 * nodes as updating the inputs of the duplicated nodes does not fire node events if
                 * the node is not in a graph
                 */
                // collect all possible pulls with the current phis
                ValuePhiNode phi = puller.next();
                List<Node> additionalWorklistNodes = new ArrayList<>();
                if (phi.merge() instanceof MergeNode) {
                    // additional actions in the pull operation will refill the worklist
                    tryPullOverPhi(defaultSimplifier, domCheck, cfg, frequencyInfo, phi, puller.workList, additionalWorklistNodes, puller.openPulls);
                }
                // resort them benefit and cost wise
                puller.advance();
                try (NodeEventScope s1 = graph.trackNodeEvents(localChanges)) {
                    // pull all of them if possible
                    puller.pullOpen(graph.getDebug());
                }
                puller.clearOpen();

                for (Node n : additionalWorklistNodes) {
                    if (n.isAlive()) {
                        localChanges.getNodes().add(n);
                    }
                }
                /*
                 * Use canonicalizer without control flow graph simplifications to avoid any
                 * necessary recomputations of the CFG during the phase.
                 */
                if (localChanges.getNodes().size() > 0) {
                    canonicalizerWithoutSimplification.applyIncremental(graph, context, localChanges.getNodes());
                    localChanges.getNodes().clear();
                }
                ModelIterations.increment(graph.getDebug());
                puller.budgetExceeded();
                iterations++;
            }
        }
        canonicalizer.applyIncremental(graph, context, globalChanges.getNodes());
    }

    private class PullCostFunction implements Iterator<ValuePhiNode> {
        private final List<ValuePhiNode> workList;
        private final PullCostModel model;
        private final List<PullOperation> openPulls;
        private final StructuredGraph graph;
        private final ControlFlowGraph cfg;
        private final double costReductionFactor;
        private final CFGFrequencyInfo info;

        PullCostFunction(List<ValuePhiNode> workList, StructuredGraph graph, ControlFlowGraph cfg, PullCostModel model, CFGFrequencyInfo info) {
            this.workList = workList;
            this.model = model;
            this.graph = graph;
            this.cfg = cfg;
            this.costReductionFactor = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.CostReductionFactor, Options.CostReductionFactorHotCode);
            openPulls = new ArrayList<>();
            this.info = info;
        }

        void advance() {
            // resort min cost first
            openPulls.sort((x, y) -> Double.compare(x.cost(), y.cost()));
            // resort max benefit first
            openPulls.sort((x, y) -> Double.compare(y.benefit(), x.benefit()));
        }

        void pullOpen(DebugContext debug) {
            // either there are no pulls left or the budget is exceeded
            while (!openPulls.isEmpty() && !budgetExceeded) {
                PullOperation pull = openPulls.remove(0);
                if (pull.stillValid() && pull.enablePullOpType() && shouldPull(pull.benefit(), pull.cost(), pull, debug)) {
                    pull(pull, graph, workList);
                    pulled();
                }
            }
        }

        void clearOpen() {
            openPulls.clear();
        }

        private boolean budgetExceeded;

        boolean shouldPull(double benefit, long cost, PullOperation pull, DebugContext debug) {
            OptimizationEffect op = model.potentialOpt(benefit, cost);
            budgetExceeded = op.budgetExceeded();
            if (op == OptimizationEffect.STOP || op == OptimizationEffect.NO_BENEFIT) {
                return false;
            }
            if (tradeOffCostBenefit(cost, benefit, op.getBudgetFilling())) {
                debug.log(DebugContext.DETAILED_LEVEL, "Positive Pulling operation %s", pull);
                pull.phi.graph().getOptimizationLog().report(PullThroughPhiPhase.class, "Pull", pull.phi);
                return true;
            } else {
                debug.log(DebugContext.DETAILED_LEVEL, "Not Pulling operation %s", pull);
                BadPullsIgnored.increment(graph.getDebug());
                return false;
            }
        }

        private boolean tradeOffCostBenefit(double cost, double benefit, double budgetFilling) {
            double increasedCost = cost + 1;
            return benefit * (1D - budgetFilling) > increasedCost / costReductionFactor;
        }

        void pulled() {
            model.applyLastOp();
        }

        void budgetExceeded() {
            if (budgetExceeded) {
                workList.clear();
                BudgetExceeded.increment(graph.getDebug());
                return;
            }
            /*
             * Sort phis according to their relative frequency
             *
             * Performing calculations with relative block frequency is dangerous since the values
             * are relatively "unbounded" and can thus cause troubles in any heuristic that performs
             * numerical calculations. However, we can still use the relative frequencies for
             * sorting.
             */
            workList.sort((x, y) -> Double.compare(y.isAlive() ? info.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(y.merge())) : 0,
                            x.isAlive() ? info.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(x.merge())) : 0));
        }

        @Override
        public boolean hasNext() {
            return !workList.isEmpty();
        }

        @Override
        public ValuePhiNode next() {
            return workList.remove(0);
        }

    }

    @SuppressWarnings("unchecked")
    private static PullOperation phiExplode(SimplifierTool tool, Node n, ControlFlowGraph cfg, CFGFrequencyInfo frequencyInfo, Collection<Node> additionalWorkListCanonicalizer) {
        if (n instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<ValueNode> c = (Canonicalizable.Binary<ValueNode>) n;
            ValueNode x = c.getX();
            ValueNode y = c.getY();
            if (x instanceof ValuePhiNode && y instanceof ValuePhiNode) {
                ValuePhiNode xPhi = (ValuePhiNode) x;
                ValuePhiNode yPhi = (ValuePhiNode) y;
                if (xPhi.merge() == yPhi.merge()) {
                    // look if there is a canonicalization possible, if so explode the set
                    ValueNode[] newValues = null;
                    boolean[] canonicalizations = null;
                    double[] predFrequencies = null;
                    for (int i = 0; i < xPhi.valueCount(); i++) {
                        Node canonicalized = c.canonical(tool, xPhi.valueAt(i), yPhi.valueAt(i));
                        if (canonicalized != c) {
                            if (newValues == null) {
                                newValues = new ValueNode[xPhi.valueCount()];
                                canonicalizations = new boolean[xPhi.valueCount()];
                                predFrequencies = new double[xPhi.valueCount()];
                            }
                            newValues[i] = (ValueNode) canonicalized;
                            canonicalizations[i] = true;
                            predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(xPhi.merge().forwardEndAt(i)));
                        }
                    }
                    if (newValues != null) {
                        for (int i = 0; i < newValues.length; i++) {
                            if (newValues[i] == null) {
                                ValueNode copy = (ValueNode) n.copyWithInputs(false);
                                copy.getNodeClass().replaceFirstInput(copy, xPhi, xPhi.valueAt(i));
                                copy.getNodeClass().replaceFirstInput(copy, yPhi, yPhi.valueAt(i));
                                newValues[i] = copy;
                                predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(xPhi.merge().forwardEndAt(i)));
                            }
                        }
                        final ValueNode[] values = newValues;
                        return new PhiExplodePullOperation(xPhi, n, () -> {
                            for (int i = 0; i < values.length; i++) {
                                additionalWorkListCanonicalizer.add(values[i]);
                                additionalWorkListCanonicalizer.addAll(values[i].inputs().snapshot());
                                additionalWorkListCanonicalizer.addAll(values[i].usages().snapshot());
                            }
                        }, values, predFrequencies, canonicalizations);
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static PullOperation phiPhiPull(Node target, ValuePhiNode phi, SimplifierTool tool, DomCheck domCheck, ControlFlowGraph cfg, CFGFrequencyInfo frequencyInfo,
                    Collection<Node> additionalWorkListCanonicalizer) {
        /*
         * case that a canonicalization has two phis as input where we can pull through one (the
         * current one) and keep the other as input
         */
        if (target instanceof Canonicalizable.Binary<?>) {
            Binary<ValueNode> binary = (Binary<ValueNode>) target;
            ValueNode x = binary.getX();
            ValueNode y = binary.getY();
            PhiNode xPhi = x == phi ? phi : null;
            PhiNode yPhi = y == phi ? phi : null;
            ValueNode[] newValues = null;
            boolean[] canonicalizations = null;
            double[] predFrequencies = null;
            if ((xPhi != null && (y instanceof PhiNode && ((PhiNode) y).merge() != xPhi.merge()) && domCheck.mergePostDom(xPhi.merge(), ((PhiNode) y).merge())) ||
                            (yPhi != null && (x instanceof PhiNode && ((PhiNode) x).merge() != yPhi.merge())) && domCheck.mergePostDom(yPhi.merge(), ((PhiNode) x).merge())) {
                for (int i = 0; i < phi.valueCount(); i++) {
                    Node improved = binary.canonical(tool, xPhi != null ? xPhi.valueAt(i) : x, xPhi != null ? y : yPhi.valueAt(i));
                    if (improved != binary) {
                        if (newValues == null) {
                            newValues = new ValueNode[phi.valueCount()];
                            canonicalizations = new boolean[phi.valueCount()];
                            predFrequencies = new double[phi.valueCount()];
                        }
                        newValues[i] = (ValueNode) improved;
                        canonicalizations[i] = true;
                        predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(phi.merge().forwardEndAt(i)));
                    }
                }
                if (newValues != null) {
                    for (int i = 0; i < newValues.length; i++) {
                        if (newValues[i] == null) {
                            ValueNode copy = (ValueNode) target.copyWithInputs(false);
                            copy.getNodeClass().replaceFirstInput(copy, xPhi != null ? xPhi : yPhi, xPhi != null ? xPhi.valueAt(i) : yPhi.valueAt(i));
                            newValues[i] = copy;
                            predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(phi.merge().forwardEndAt(i)));
                        }
                    }
                    final ValueNode[] values = newValues;
                    return new PhiPhiPullOperation((ValuePhiNode) (xPhi != null ? xPhi : yPhi), target, () -> {
                        for (int i = 0; i < values.length; i++) {
                            additionalWorkListCanonicalizer.add(values[i]);
                            additionalWorkListCanonicalizer.addAll(values[i].inputs().snapshot());
                        }
                    }, newValues, predFrequencies, canonicalizations);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static PullOperation regularPull(Node target, ValuePhiNode phi, List<ValuePhiNode> workList, Collection<Node> additionalWorkListCanonicalizer, ControlFlowGraph cfg,
                    CFGFrequencyInfo frequencyInfo, SimplifierTool tool) {
        ValueNode[] newValues = null;
        boolean[] canonicalizations = null;
        double[] predFrequencies = null;
        boolean binaryInputs = false;
        for (int i = 0; i < phi.valueCount(); i++) {
            Node canonicalized = tryCanonicalize(target, i, phi, tool);
            if (canonicalized != target) {
                binaryInputs = target instanceof Binary<?>;
                if (newValues == null) {
                    newValues = new ValueNode[phi.values().size()];
                    canonicalizations = new boolean[phi.valueCount()];
                    predFrequencies = new double[phi.valueCount()];
                }
                newValues[i] = (ValueNode) canonicalized;
                canonicalizations[i] = true;
                predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(phi.merge().forwardEndAt(i)));
            }
        }

        if (newValues != null) {
            final List<ValuePhiNode> additionalWorkList = new ArrayList<>(phi.valueCount());
            for (int i = 0; i < phi.values().size(); i++) {
                if (newValues[i] == null) {
                    ValueNode value = phi.valueAt(i);
                    ValueNode copy = (ValueNode) target.copyWithInputs(false);
                    if (binaryInputs) {
                        Binary<?> b = (Binary<Node>) target;
                        PhiNode xPhi = b.getX() == phi ? phi : null;
                        PhiNode yPhi = b.getY() == phi ? phi : null;
                        if (xPhi != null) {
                            copy.getNodeClass().replaceFirstInput(copy, phi, value);
                        }
                        if (yPhi != null) {
                            copy.getNodeClass().replaceFirstInput(copy, phi, value);
                        }
                    } else {
                        copy.getNodeClass().replaceFirstInput(copy, phi, value);
                    }
                    newValues[i] = copy;
                    if (value instanceof ValuePhiNode) {
                        additionalWorkList.add((ValuePhiNode) value);
                    }
                    predFrequencies[i] = frequencyInfo.getFrequencyNormalizedToMaxFrequency(cfg.blockFor(phi.merge().forwardEndAt(i)));
                }
            }
            final ValueNode[] values = newValues;
            return new RegularPullOperation(phi, target, () -> {
                // feed canonicalizer
                for (int i = 0; i < values.length; i++) {
                    additionalWorkListCanonicalizer.add(values[i]);
                    additionalWorkListCanonicalizer.addAll(values[i].inputs().snapshot());
                }
                // feed phi list
                for (ValuePhiNode v : additionalWorkList) {
                    workList.add(v);
                }
            }, newValues, predFrequencies, canonicalizations);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Node tryCanonicalize(Node target, int pathIndex, ValuePhiNode phi, SimplifierTool tool) {
        if (target instanceof Canonicalizable.Unary<?>) {
            Unary<ValueNode> unary = (Unary<ValueNode>) target;
            ValueNode unaryVal = unary.getValue();
            assert unaryVal == phi : unaryVal + " " + phi;
            ValueNode value = phi.valueAt(pathIndex);
            Node improved = unary.canonical(tool, value);
            if (improved != unary) {
                return improved;
            }
        } else if (target instanceof Canonicalizable.Binary<?>) {
            Binary<ValueNode> binary = (Binary<ValueNode>) target;
            ValueNode x = binary.getX();
            ValueNode y = binary.getY();
            PhiNode xPhi = x == phi ? phi : null;
            PhiNode yPhi = y == phi ? phi : null;
            if ((xPhi != null || noTransitiveFixedDependency(x)) && (yPhi != null || noTransitiveFixedDependency(y))) {
                Node improved = binary.canonical(tool, xPhi == null ? x : xPhi.valueAt(pathIndex), yPhi == null ? y : yPhi.valueAt(pathIndex));
                if (improved != binary) {
                    return improved;
                }
            }

        }
        return target;
    }

    static void tryPullOverPhi(SimplifierTool tool, DomCheck domCheck, ControlFlowGraph cfg, CFGFrequencyInfo frequencyInfo, ValuePhiNode root, List<ValuePhiNode> workList,
                    Collection<Node> additionalWorkListCanonicalizer, List<PullOperation> pullOut) {
        for (Node usage : root.usages().snapshot()) {
            if (canPullThroughPhi(usage)) {
                PullOperation pull = phiExplode(tool, usage, cfg, frequencyInfo, additionalWorkListCanonicalizer);
                if (pull == null) {
                    pull = phiPhiPull(usage, root, tool, domCheck, cfg, frequencyInfo, additionalWorkListCanonicalizer);
                }
                if (pull == null) {
                    pull = regularPull(usage, root, workList, additionalWorkListCanonicalizer, cfg, frequencyInfo, tool);
                }
                if (pull != null) {
                    pullOut.add(pull);
                }
            }
        }
    }

    private static void pull(PullOperation pull, StructuredGraph g, List<ValuePhiNode> workList) {
        DebugContext debug = g.getDebug();
        pull.reportDebugPulled(debug);
        pull.addToGraph(g);
        g.getDebug();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, g, "Before pulling %s through %s", pull.target, pull.phi);
        ValuePhiNode newPhi = g.addOrUniqueWithInputs(new ValuePhiNode(pull.values[0].stamp(NodeView.DEFAULT).unrestricted(), pull.phi.merge(), pull.values));
        if (workList != null) {
            workList.add(newPhi);
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, g, "Before inferring new stamp for phi %s", newPhi);
        // we infer stamps here as they may be the result of a previous pull operation not yet fully
        // inferred
        for (ValueNode v : pull.values) {
            v.inferStamp();
        }
        newPhi.inferStamp();
        debug.dump(DebugContext.DETAILED_LEVEL, g, "After inferring new stamp for phi %s", newPhi);
        pull.target.replaceAtUsages(newPhi);
        GraphUtil.killWithUnusedFloatingInputs(pull.target);
        pull.additionalAction.run();
        debug.dump(DebugContext.DETAILED_LEVEL, g, "After pulling %s through %s", pull.target, pull.phi);
    }

    private abstract static class PullOperation {
        protected final ValuePhiNode phi;
        protected final Node target;
        protected final Runnable additionalAction;
        protected final ValueNode[] values;
        protected final double[] predRelativeProbabilities;
        protected final boolean[] canonicalizations;

        PullOperation(ValuePhiNode phi, Node target, Runnable additionalAction, ValueNode[] values, double[] predRelativeProbabilities, boolean[] canonicalizations) {
            this.phi = phi;
            this.target = target;
            this.additionalAction = additionalAction;
            this.values = values;
            this.predRelativeProbabilities = predRelativeProbabilities;
            this.canonicalizations = canonicalizations;
        }

        abstract void reportDebugPulled(DebugContext debug);

        boolean enablePullOpType() {
            return true;
        }

        boolean stillValid() {
            return target.isAlive() && phi.isAlive();
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " phi=" + phi + " target=" + target + " values=" + Arrays.toString(values) + " probabilities=" + Arrays.toString(predRelativeProbabilities) +
                            " canonicalizations=" + Arrays.toString(canonicalizations) + " benefit=" + benefit() + " cost=" + cost() + " phi usage cost part=" + phiUsageCost() +
                            " merge current phi count=" + phi.merge().valuePhis().count();
        }

        double benefit() {
            double oldCycles = 0.0d;
            double newCycles = 0.0d;
            int targetNodeCycles = target.estimatedNodeCycles().value;
            for (int i = 0; i < canonicalizations.length; i++) {
                final double predecessorProbability = predRelativeProbabilities[i];
                final ValueNode value = values[i];
                oldCycles += targetNodeCycles * predecessorProbability;
                if (canonicalizations[i]) {
                    if (!value.isAlive()) {
                        // new node
                        newCycles += value.estimatedNodeCycles().value * predecessorProbability;
                    }
                    // else deleted the target node entirely
                } else {
                    newCycles += value.estimatedNodeCycles().value * predecessorProbability;
                }
            }
            if (newCycles > oldCycles) {
                /*
                 * Indirect canonicalizations can happen that require building the input tree of the
                 * node and find all that nodes which usage count drops to zero. We return a default
                 * value of the same "unit".
                 */
                return AddNode.TYPE.cycles().value;
            }
            return oldCycles - newCycles;
        }

        // see comment at the usage in phiUsageCost()
        private static final int MaxPhiCountCost = 256;

        int cost() {
            int originalSize = target.estimatedNodeSize().value;
            int newSize = 0;
            for (int i = 0; i < canonicalizations.length; i++) {
                final ValueNode value = values[i];
                if (canonicalizations[i]) {
                    if (!value.isAlive()) {
                        // new node
                        newSize += value.estimatedNodeSize().value;
                    }
                    // else deleted the target node entirely
                } else {
                    newSize += value.estimatedNodeSize().value;
                }
            }
            /*
             * Pulling values through phi nodes up in predecessor branches effectively creates new
             * live values if the phi that is processed has usages besides the original node. Since
             * we create new phis at the merge we increase register pressure, thus, we only want to
             * allow a certain number of phi nodes per merge. The higher the number of existing phi
             * nodes already is the more benefit must be created later to still allow the floating
             * node duplication.
             */
            int cost = Math.max(0, newSize - originalSize);
            cost += (int) phiUsageCost();
            return cost;
        }

        double phiUsageCost() {
            double cost = 0d;
            // the current number of phi nodes at the respective merge
            int currentPhiCount = phi.merge().valuePhis().count();
            // @formatter:off
            /*
             * In the interval [0,10], where 10 is the maximum number of desired phi nodes per
             * merge, it should be increasingly harder for the heuristic to allow a certain
             * duplication. Thus, we increase the cost by a constant factor that reflects how many
             * phis are already created: this is a function f(x)=(e^x)/4x in the range [0,10]
             * that is bounded by MAX_PHI_COUNT_COST and rises slightly sub exponential.
             *
             * x=1 f(x)=0.679570
             * x=2 f(x)=0.923632
             * x=3 f(x)=1.673795
             * x=4 f(x)=3.412384
             * x=5 f(x)=7.420658
             * x=6 f(x)=16.809533
             * x=7 f(x)=39.165470
             * x=8 f(x)=93.154937
             * x=9 f(x)=225.085665
             */
            // @formatter:on
            /*
             * if the original phi has usages besides the target it will be alive after the
             * operation so we effectively created another live variable which increases register
             * pressure
             */
            if (phi.hasMoreThanOneUsage()) {
                final double x = Math.max(1, currentPhiCount);
                double fX = Math.exp(x) / (4 * x);
                fX = Math.min(MaxPhiCountCost, fX);
                cost += fX;
                /*
                 * Every value after canonicalization that is a new node, i.e. not alive before, is
                 * a new live value in the respective branch.
                 */
                for (Node n : values) {
                    if (n.isUnregistered()) {
                        cost += Options.CostNewLiveVariable.getValue(phi.getOptions());
                    }
                }
            }
            return cost;
        }

        void addToGraph(StructuredGraph g) {
            for (int i = 0; i < values.length; i++) {
                if (!values[i].isAlive()) {
                    values[i] = g.addOrUniqueWithInputs(values[i]);
                }
            }
        }

        int duplications() {
            int duplications = 0;
            for (int i = 0; i < canonicalizations.length; i++) {
                if (!canonicalizations[i]) {
                    duplications++;
                }
            }
            return duplications;
        }

        int canonicalizations() {
            int canonicals = 0;
            for (int i = 0; i < canonicalizations.length; i++) {
                if (canonicalizations[i]) {
                    canonicals++;
                }
            }
            return canonicals;
        }
    }

    private static class RegularPullOperation extends PullOperation {

        RegularPullOperation(ValuePhiNode phi, Node target, Runnable additionalAction, ValueNode[] values, double[] predProbabilies, boolean[] canonicalizations) {
            super(phi, target, additionalAction, values, predProbabilies, canonicalizations);
        }

        @Override
        void reportDebugPulled(DebugContext debug) {
            PushedCanonicalizations.add(debug, canonicalizations());
            PushedDuplications.add(debug, duplications());
        }

    }

    private static final class PhiExplodePullOperation extends RegularPullOperation {

        PhiExplodePullOperation(ValuePhiNode phi, Node target, Runnable additionalAction, ValueNode[] values, double[] predProbabilies, boolean[] canonicalizations) {
            super(phi, target, additionalAction, values, predProbabilies, canonicalizations);
        }

        @Override
        boolean enablePullOpType() {
            return Options.TryExplodeOverPhis.getValue(phi.getOptions());
        }

        @Override
        void reportDebugPulled(DebugContext debug) {
            super.reportDebugPulled(debug);
            AbovePhiExploded.increment(debug);
        }

    }

    private static final class PhiPhiPullOperation extends RegularPullOperation {

        PhiPhiPullOperation(ValuePhiNode phi, Node target, Runnable additionalAction, ValueNode[] values, double[] predProbabilies, boolean[] canonicalizations) {
            super(phi, target, additionalAction, values, predProbabilies, canonicalizations);
        }

        @Override
        boolean enablePullOpType() {
            return Options.TryPhiPhiPulls.getValue(phi.getOptions());
        }

        @Override
        void reportDebugPulled(DebugContext debug) {
            super.reportDebugPulled(debug);
            PhiPhiPull.increment(debug);
        }

    }

    private static boolean noTransitiveFixedDependency(ValueNode n) {
        return n instanceof FloatingNode && n.isAlive() && (n.isConstant() || n instanceof ParameterNode);
    }

    private static boolean canPullThroughPhi(Node n) {
        if (n.isAlive() && n instanceof FloatingNode && n.isAllowedUsageType(InputType.Value)) {
            /*
             * We only consider canonicalizable nodes for pulling operations. However, unary/binary
             * nodes can have additional floating/fixed inputs to their value inputs. We do not want
             * to check that these additional inputs respect scheduling constraints we must to
             * maintain thus we only allow canonicalizable nodes with value inputs.
             *
             * For value inputs #noTransitiveFixedDependency check is done later ensuring
             * (heuristic) a valid schedule
             */
            if (n instanceof Canonicalizable.Unary<?>) {
                return n.inputs().count() == 1 && !usagesContainDivs(n);
            }
            if (n instanceof Canonicalizable.Binary<?>) {
                return n.inputs().count() == 2 && !usagesContainDivs(n);
            }
        }
        return false;
    }

    private static boolean usagesContainDivs(Node n) {
        return n.usages().filter(FloatingIntegerDivRemNode.class).isNotEmpty();
    }

}
