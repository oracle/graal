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

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterBadDuplicationsIgnored;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterBudgetExceededIgnored;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterCodeSizeDuplicated;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterCyclesSaved;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterDuplicationBudgetExceeded;
import static jdk.graal.compiler.duplication.phases.simulation.DuplicationDebugUtil.counterReducedRegionShrinked;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.duplication.opt.BudgetCostModel;
import jdk.graal.compiler.duplication.opt.OptimizationEffect;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.DuplicationOpportunity;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.LockCoarseningOpportunity;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.PEAOpportunity;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.ReadEliminationOpportunity;
import jdk.graal.compiler.duplication.util.DuplicationUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * An implementation of the duplication cost function based on the {@link NodeSize} and
 * {@link NodeCycles} data for nodes including {@linkplain NodeSize} and {@linkplain NodeCycles} of
 * IR nodes.
 *
 * The node cost model based function uses a {@linkplain BudgetCostModel} to implement a hard
 * boundary for the number of duplications.
 *
 * It collects all the improvements found during simulation and tries to find the *minimal*
 * duplication region for which the computed benefit satisfies the cost function.
 *
 */
public class NodeCostModelBasedDuplicationCostFunction implements DuplicationCostFunction {
    private final BudgetCostModel costModel;
    private final boolean ignoreBadDuplications;
    private final boolean minimalRegions;
    private final boolean duplicateALot;
    private final boolean considerLockCoarseningOpportunities;
    private final boolean considerEscapeAnaylsisOpportunities;

    NodeCostModelBasedDuplicationCostFunction(int budget, boolean ignoreBadDuplications, boolean dupAlot, boolean minimalRegions,
                    boolean considerLockCoarseningOpportunities,
                    boolean considerEscapeAnaylsisOpportunities) {
        costModel = new BudgetCostModel(budget);
        this.ignoreBadDuplications = ignoreBadDuplications;
        this.minimalRegions = minimalRegions;
        this.duplicateALot = dupAlot;
        this.considerLockCoarseningOpportunities = considerLockCoarseningOpportunities;
        this.considerEscapeAnaylsisOpportunities = considerEscapeAnaylsisOpportunities;
    }

    private boolean budgetExceeded;

    @Override
    public BudgetCostModel costModel() {
        return costModel;
    }

    private static final class Accumulation {
        private double benefit;
        private int cost;
        private FixedNode duplicationTarget;
        private int usagePhiCreationCount;
        private boolean requiresReadEliminationCleanUp;
    }

    int expectedPhisLastDuplication;

    /**
     * During simulation we estimate how many phis need to be generated in order to duplicate a set
     * of nodes. This estimation is not correct for compile time reasons (too costly to compute
     * fully). Therefore we estimate how many phis are generated for each duplication. If our
     * estimation is completely wrong we stop duplicating for compile time reasons. This number is
     * the maximum ratio, i.e. the maximum error we are allowed to make before we stop duplication.
     */
    private static final int MAX_PHI_CREATION_RATIO = 32;

    @Override
    @SuppressWarnings("try")
    public DuplicationDecision shouldDuplicate(DuplicationConfig factors, SimulationEndInfo s, FixedNode regionEnd, int phisLastIterationCreated, int iteration) {
        try (DebugContext.Scope c = regionEnd.getDebug().scope("DuplicationBenefitComputation")) {
            if (budgetExceeded) {
                GraalError.shouldNotReachHere("budget exceeded"); // ExcludeFromJacocoGeneratedReport
            }
            Accumulation result = computeValues(factors, s, regionEnd);
            if (!duplicateALot) {
                if (iteration > 0) {
                    double ratio = expectedPhisLastDuplication != 0 ? (phisLastIterationCreated / expectedPhisLastDuplication) : phisLastIterationCreated;
                    if (ratio > MAX_PHI_CREATION_RATIO && phisLastIterationCreated > MAX_PHI_CREATION_RATIO) {
                        DuplicationDebugUtil.counterPhiEstimationCutOff.increment(regionEnd.getDebug());
                        return null;
                    }
                }
            }
            expectedPhisLastDuplication = result.usagePhiCreationCount;
            /*
             * When duplicating code inside a potentially vectorizable loop we have to ensure
             * vectorization capabilites are not destroyed because we introduce complex control flow
             * or unvectorizable expressions/effects.
             */
            if (DuplicationOptions.ConsiderVectorizableLoops.getValue(regionEnd.getOptions())) {
                if (s.isLoopVectorizable()) {
                    if (!(result.duplicationTarget.predecessor() instanceof MergeNode)) {
                        // duplicating a fixed node, potentially destroying vectorization
                        // capabilities
                        for (FixedNode fn : GraphUtil.predecessorIterable(result.duplicationTarget)) {
                            if (fn instanceof AbstractMergeNode) {
                                break;
                            }
                            /*
                             * Duplication runs in high tier: there we have many nodes still fixed.
                             * Most nodes will float in mid tier, thus the only nodes we should care
                             * about are the ones that will never float: writes.
                             */
                            if (MemoryKill.isMemoryKill(fn)) {
                                // not duplicating this node as it would destroy vectorization
                                DuplicationDebugUtil.counterNotDuplicatedVectorizable.increment(regionEnd.getDebug());
                                return null;
                            }
                        }
                    }
                    if (s.splits() && !s.killsBranches()) {
                        // not duplicating this node as it would destroy vectorization
                        DuplicationDebugUtil.counterNotDuplicatedVectorizable.increment(regionEnd.getDebug());
                        return null;
                    }
                }
            }
            /*
             * When duplicating control flow splits inside a loop that are not foldable afterwards
             * (i.e. they are not optimizable after duplication) we effectively create more phi
             * nodes and potentially more complex code inside a loop, i.e., more phis and more
             * complex control flow. This can hinder further optimizations and create more live
             * values.
             */
            if (DuplicationOptions.PenalizeComplexLoopControlFlow.getValue(regionEnd.getOptions())) {
                if (s.isInsideLoop() && s.splits() && !s.killsBranches()) {
                    result.cost += (int) Math.pow(2, s.getForwardEnd().getLoopDepth());
                }
            }
            FixedNode duplicate = makeDecision(result.benefit, result.cost, result.usagePhiCreationCount, getCostReductionFactor(regionEnd.graph()), result.duplicationTarget);
            if (duplicate == null) {
                return null;
            } else {
                regionEnd.getDebug().log("#<-># Duplicating! at region end %s", duplicate);
                return new DuplicationDecision(duplicate, result.cost, result.benefit, result.requiresReadEliminationCleanUp);
            }
        }
    }

    private static int getCostReductionFactor(StructuredGraph graph) {
        return GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, DuplicationOptions.DuplicationCostReductionFactor, DuplicationOptions.DuplicationCostReductionFactorHotCode);
    }

    private Accumulation computeValues(DuplicationConfig factors, SimulationEndInfo s, FixedNode regionEnd) {
        Accumulation accumulatedResult = new Accumulation();
        accumulatedResult.duplicationTarget = /* will be used if not reduced */ regionEnd;
        accumulatedResult.cost += s.getCodeSize();

        final int killedBranches = s.getKilledBranches() * factors.getSplitKillEnhance();
        final int killedGuards = s.getKilledGuards() * factors.getGuardKillEnhance();

        ReadEliminationOpportunity readEliminationOpportunity = factors.benefitReadEliminations(s);
        final int cyclesSavedReadElimination = readEliminationOpportunity.cyclesSaved();

        PEAOpportunity escapeAnalysisOpportunity = considerEscapeAnaylsisOpportunities ? factors.benefitEscapingPhis(s, regionEnd) : PEAOpportunity.DEFAULT_ESCAPING_OPPORTUNITY;
        final int escapingPhis = escapeAnalysisOpportunity.cyclesSaved();

        LockCoarseningOpportunity lockCoarseningOpportunity = considerLockCoarseningOpportunities
                        ? LockCoarseningOpportunity.getLockCoarseningOpportunity(s.getOriginalMerge(), (EndNode) s.forwardEnd.getEndNode())
                        : LockCoarseningOpportunity.NO_OPPORTUNITY;
        final int cyclesSavedLockCoarsening = lockCoarseningOpportunity.cyclesSaved();

        DuplicationOpportunity canonicalizationOpportunity = s.canonicalOpportuntiy();
        final int cyclesSavedCanonicalization = canonicalizationOpportunity.cyclesSaved();

        ReducedRegion reducedRegion = canReduceRegionSize(killedBranches, killedGuards,
                        new DuplicationOpportunity[]{readEliminationOpportunity, escapeAnalysisOpportunity, canonicalizationOpportunity, lockCoarseningOpportunity}, s.getOriginalMerge(), regionEnd);

        accumulatedResult.benefit += killedBranches;
        accumulatedResult.benefit += killedGuards;
        accumulatedResult.benefit += cyclesSavedReadElimination;
        accumulatedResult.benefit += escapingPhis;
        accumulatedResult.benefit += cyclesSavedCanonicalization;
        accumulatedResult.benefit += cyclesSavedLockCoarsening;

        accumulatedResult.requiresReadEliminationCleanUp = cyclesSavedReadElimination > 0;

        int mergeRemovedSplit = 0;
        int mergeRemovedSink = 0;
        int conditionDominated = 0;

        if (reducedRegion == null) {
            mergeRemovedSplit = factors.benefitMergeRemovedSplit(s);
            accumulatedResult.benefit += mergeRemovedSplit;
            mergeRemovedSink = factors.benefitMergeRemovedSink(s);
            accumulatedResult.benefit += mergeRemovedSink;
            conditionDominated = factors.benefitConditionDominatedByEnd(s, regionEnd);
            accumulatedResult.benefit += conditionDominated;
        } else {
            if (regionEnd != reducedRegion.fixed) {
                assert reducedRegion.fixed != null;
                int reducedRegionCodeSize = recomputeCodeSize(reducedRegion.fixed, s.getOriginalMerge(), accumulatedResult, s.killsBranches());
                if (reducedRegionCodeSize < s.codeSize ||
                                (s.codeSize == 0 && s.cyclesSaved == 0 && s.getKilledBranches() == 0 && s.getKilledGuards() == 0)) {
                    accumulatedResult.cost = reducedRegionCodeSize;
                    accumulatedResult.duplicationTarget = reducedRegion.fixed;
                    checkBenefitInvariant(accumulatedResult, reducedRegion);
                    counterReducedRegionShrinked.increment(regionEnd.getDebug());
                }
            }
        }
        /*
         * recompute code size if the last optimizable node is different than the one from
         * simulation (already done if we have a reduced region)
         *
         * we determine the number of (estimated) phis we need to create for the next iteration
         */
        if (reducedRegion == null) {
            if (accumulatedResult.duplicationTarget != s.lastOptimizationAnchor()) {
                accumulatedResult.cost = recomputeCodeSize(accumulatedResult.duplicationTarget, s.getOriginalMerge(), accumulatedResult, s.killsBranches());
            } else {
                // if we kill the if there is no need to generate phis as there is no new merge
                if (!s.killsBranches()) {
                    accumulatedResult.usagePhiCreationCount = s.minimalPhiCreationCount();
                }
            }
        }

        logDecisionValues(regionEnd.graph().method(), s.getOriginalMerge(), killedBranches, killedGuards, cyclesSavedCanonicalization, mergeRemovedSplit, mergeRemovedSink, cyclesSavedReadElimination,
                        conditionDominated,
                        escapingPhis,
                        s.getProbabilityAfter(), accumulatedResult.benefit, accumulatedResult.cost);

        // weight by the probability
        if (accumulatedResult.duplicationTarget instanceof DeoptimizeNode && s.getProbabilityAfter() == 0D) {
            // while deopt paths have a probability of 0, we also want to consider them for
            // duplication
            accumulatedResult.benefit *= ControlFlowGraph.MIN_RELATIVE_FREQUENCY;
        } else {
            accumulatedResult.benefit *= s.getProbabilityAfter();
        }
        return accumulatedResult;
    }

    @SuppressFBWarnings(value = "FE_FLOATING_POINT_EQUALITY", justification = "accumulatedResult.benefit guaranteed to be a round number")
    private static void checkBenefitInvariant(Accumulation accumulatedResult, ReducedRegion reducedRegion) {
        GraalError.guarantee(accumulatedResult.benefit == reducedRegion.cyclesSaved, "Can only shrink if we have solely one benefit");
    }

    public static final TimerKey recomputeCodeSize = DebugContext.timer("Duplication_RecomputeCodeSizeAndOutOfRegionUsage");

    private static int recomputeCodeSize(FixedNode duplicationTarget, MergeNode merge, Accumulation result, boolean killsBranches) {
        final NodeBitMap inRegion = duplicationTarget.graph().createNodeBitMap();
        final NodeBitMap processed = duplicationTarget.graph().createNodeBitMap();
        final NodeBitMap visited = duplicationTarget.graph().createNodeBitMap();
        boolean splitIncluded = duplicationTarget instanceof ControlSplitNode;
        List<FixedNode> fixedNodes = new ArrayList<>();
        for (FixedNode f : GraphUtil.predecessorIterable(duplicationTarget)) {
            fixedNodes.add(f);
        }
        for (int i = fixedNodes.size() - 1; i >= 0; i--) {
            FixedNode f = fixedNodes.get(i);
            visited.clearAll();
            DuplicationSimulationUtil.inRegion(inRegion, processed, visited, merge, f, true, EMPTY_CONSUMER);
        }
        EconomicSet<Node> worklist = EconomicSet.create(Equivalence.IDENTITY);
        if (merge.stateAfter() != null) {
            worklist.add(merge.stateAfter());
        }
        int codeSize = 0;
        for (Node rrNode : inRegion) {
            worklist.add(rrNode);
        }
        for (Node rrNode : inRegion) {
            if (!SimulationEndInfo.excludeNodeFromSize(rrNode, worklist)) {
                codeSize += rrNode.estimatedNodeSize().value;
            }
        }
        if (!killsBranches && splitIncluded) {
            result.usagePhiCreationCount = DuplicationSimulationUtil.fastEstimateUsageCount(merge, worklist);
        }
        return codeSize;
    }

    private static final Consumer<Node> EMPTY_CONSUMER = (x) -> {
    };

    private FixedNode makeDecision(double benefit, long cost, long usagePhiCreationCount, int costReductionFactor, FixedNode duplicationTarget) {
        if (cost < 0) {
            return duplicationTarget;
        }
        OptimizationEffect op = costModel.potentialOpt(benefit, cost);
        budgetExceeded = op.budgetExceeded() && !duplicateALot;
        if (op == OptimizationEffect.STOP || op == OptimizationEffect.NO_BENEFIT) {
            return null;
        }
        if (duplicateALot) {
            return duplicationTarget;
        }
        if (op.getBudgetFilling() <= 1.0D) {
            if (ignoreBadDuplications) {
                if (tradeOffCostBenefit(cost, benefit, usagePhiCreationCount, costReductionFactor)) {
                    return duplicationTarget;
                } else {
                    counterBadDuplicationsIgnored.increment(duplicationTarget.getDebug());
                    return null;
                }
            } else {
                return duplicationTarget;
            }
        } else {
            counterBudgetExceededIgnored.increment(duplicationTarget.getDebug());
            // budget will be exceeded after this duplication, therefore do not perform it
            return null;
        }
    }

    /**
     * Duplication may require the generation of phis in post-dominating usages, this can become
     * compile-time costly, therefore, we raise the cost of a duplication for each phi by a constant
     * estimating the compile-time overhead introduced via the creation of the phi.
     */
    private static final double PHI_CREATION_COST_MULTIPLIER = 16;

    /**
     * We decide upon the value of duplications by computing their cost / benefit ratio. We allow
     * the cost to be a costReductionFactor higher than the benefit. This constant specifies a ratio
     * between performance and code size. We therefore, relate estimated cycles saved with the code
     * size increase: For a 1 cycle performance increase we are willing to spend costReductionFactor
     * byte code size increase.
     */
    private static boolean tradeOffCostBenefit(double cost, double benefit, long usagePhiCreationCount, int costReductionFactor) {
        double increasedCost = cost;
        increasedCost += usagePhiCreationCount * PHI_CREATION_COST_MULTIPLIER;
        if (costReductionFactor == 0) {
            return benefit > increasedCost;
        }
        return benefit > increasedCost / costReductionFactor;
    }

    @Override
    public void afterDuplication(SimulationEndInfo s) {
        // commit the effects on the cost model
        DebugContext debug = s.getForwardEnd().getBeginNode().getDebug();
        counterCyclesSaved.add(debug, s.getCyclesSaved());
        counterCodeSizeDuplicated.add(debug, s.getCodeSize());
        costModel.applyLastOp();
    }

    @Override
    public boolean stopDuplication(DebugContext debug) {
        if (budgetExceeded) {
            counterDuplicationBudgetExceeded.increment(debug);
            return true;
        }
        return false;
    }

    @Override
    public double overallBenefit() {
        return costModel.overallBenefit();
    }

    /**
     * Determines if a duplication at the end node to the target node is valid. Which means the
     * target node dominates the valid end node, which is always a valid duplication target.
     * Captures code size in {@linkplain NodeSize} during iteration.
     *
     * @return the code size of the region or {@code -1} if the region is not valid.
     */
    private static boolean reducedValidRegion(MergeNode merge, FixedNode target, FixedNode currentValidEnd) {
        // invalid region
        if (target instanceof MergeNode) {
            return false;
        }
        boolean checkForOriginalMerge = false;
        for (FixedNode f : GraphUtil.predecessorIterable(currentValidEnd)) {
            // we found a split before we found a target (and the target is no split)
            // we cannot reduce the region to a node after the split
            if (checkForOriginalMerge) {
                if (f instanceof ControlSplitNode && !(target instanceof ControlSplitNode)) {
                    return false;
                }
            }
            if (f == target) {
                checkForOriginalMerge = true;
            }
            if (checkForOriginalMerge) {
                if (f == merge && f != target) {
                    return true;
                }
            }
            if (f instanceof MergeNode) {
                return false;
            }
        }
        return false;
    }

    /**
     * A reduced duplication region containing the last optimized fixed node after duplication as
     * well as a code size estimate of the duplicated code.
     */
    private static class ReducedRegion {
        private final FixedNode fixed;
        private final int cyclesSaved;

        ReducedRegion(FixedNode fixed, int cyclesSaved) {
            this.fixed = fixed;
            this.cyclesSaved = cyclesSaved;
        }
    }

    /**
     * Tries to reduce the duplication region size determined by
     * {@linkplain DuplicationUtil#findRegionEnd(AbstractMergeNode)}. Goes through the different
     * benefits and if one benefit is valid and the others are not tries to proof that a reduced
     * region up to the last optimizable node is still a valid duplication target.
     */
    private ReducedRegion canReduceRegionSize(int killedBranches, int killedGuards, DuplicationOpportunity[] opportunitites, MergeNode merge, FixedNode currentValidEnd) {
        if (minimalRegions) {
            if (killedBranches == 0 && killedGuards == 0) {
                int index = -1;
                for (int i = 0; i < opportunitites.length; ++i) {
                    if (opportunitites[i].hasBenefit()) {
                        if (index != -1) {
                            /*
                             * There are more benefits, we cannot pick one opportunity for region
                             * shrinking.
                             *
                             * TODO: Implement heuristic to find (if there is something) the best
                             * opportunity to shrink to.
                             */
                            return null;
                        }
                        index = i;
                    }
                }
                return index >= 0 ? canReduceRegion(opportunitites[index], merge, currentValidEnd) : null;
            }
        }
        return null;
    }

    private static ReducedRegion canReduceRegion(DuplicationOpportunity opportunity, MergeNode merge, FixedNode validEnd) {
        FixedNode lastFixedAnchor = null;
        if (opportunity.lastOptimizableNode() instanceof FixedNode) {
            lastFixedAnchor = (FixedNode) opportunity.lastOptimizableNode();
        }
        if (reducedValidRegion(merge, lastFixedAnchor, validEnd)) {
            DuplicationDebugUtil.counterReducedRegion.increment(validEnd.getDebug());
            return new ReducedRegion(lastFixedAnchor, opportunity.cyclesSaved());
        }
        return null;
    }

    private static void logDecisionValues(ResolvedJavaMethod method, MergeNode merge, int killedBranches, int killedGuards, int saved, int mergeRemovedSplit, int mergeRemovedSink, int re,
                    int condDominated, int escapingPhis, double probability, double benefit,
                    int cost) {
        if (merge.getDebug().isLogEnabled(DebugContext.VERBOSE_LEVEL)) {
            merge.getDebug().logv(DebugContext.VERBOSE_LEVEL, "Duplication data %s benefit at merge %s cycles saved %10d - killed branches %10d - killed guards  %10d" +
                            " - merge removed split %10d - merge removed sink %10d - re %10d - condDominated %10d - escaping %10d " +
                            " - prob %10f  benefit unweighted %10f benefit weighted %10f--> costs %10d", method.format("%H.%n(%p)"), merge, saved,
                            killedBranches, killedGuards, mergeRemovedSplit, mergeRemovedSink, re, condDominated, escapingPhis,
                            probability, benefit, benefit * probability, cost);
        }
    }
}
