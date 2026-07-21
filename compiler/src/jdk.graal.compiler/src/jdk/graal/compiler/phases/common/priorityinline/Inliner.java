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
package jdk.graal.compiler.phases.common.priorityinline;

import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.InlineAllBonus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitCostTuple;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DeletedNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DontInlineCause;
import jdk.graal.compiler.phases.common.priorityinline.nodes.GenericNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCause;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;

/**
 * Analyses the call graph and does inlining on it.
 */
public class Inliner {

    private final Inliner.Policy policy;
    private final TuningPolicy tuning;

    public Inliner(Policy policy, TuningPolicy tuning) {
        this.policy = policy;
        this.tuning = tuning;
    }

    public void run(CallTree callTree, CoreProviders coreProviders, int expansionRound) {
        enhanceParameters(callTree);
        policy.analyzeCostBenefit(callTree);
        markClusters(callTree, expansionRound);
        inlineClusters(callTree);
        removeDeletedChildren(callTree);
        restoreInvariants(callTree);
        policy.postInliningRound(callTree, coreProviders);
    }

    public Inliner.Policy policy() {
        return policy;
    }

    /**
     * Do incremental parameter enhancement - visit reachable nodes in the call graph that did not
     * get their parameters enhanced, and apply canonicalizations to those parts of the call graph.
     */
    private static void enhanceParameters(CallTree callTree) {
        callTree.filteredPostOrder(node -> node.needsParameterEnhancement() || node.hasChildForParameterEnhancement(), node -> {
            // The effect of the hasChildForParameterEnhancement flag is implicit -- it enables
            // traversing of the call graph subtree rooted by this node. The child itself should
            // have the needsParameterEnhancement flag set in order to continue exploration.
            if (node.needsParameterEnhancement()) {
                node.enhanceParameters();
                node.setNeedsParameterEnhancement(false);
            }
            callTree.restoreSubtreeInvariants(node, false);
            if (node.hasChildForParameterEnhancement()) {
                node.setHasChildForParameterEnhancement(false);
            }
        });
    }

    /**
     * Decide whether to inline parts of the call graph that have been marked for inlining. Traverse
     * the tree once more in priority queue order, by following the links in the graph of
     * non-inlined descendants. Note that non-inlined descendants are not necessarily direct
     * children, as inlining the method requires inlining the sub-callgraph associated with the
     * previously assigned cost-priority tuple. For each node: (1) Decide whether to inline or not,
     * based on the current spending. (2) If deciding to inline, mark the node and add its
     * non-inlined descendants to the queue. (3) If deciding to inline, record the total spending
     * and the total priority.
     */
    private void markClusters(CallTree callTree, int expansionRound) {
        callTree.priorityOrder(getTuplePriorityComparator(), node -> {
            if (policy.shouldInline(node, expansionRound)) {
                node.markInlined();
                callTree.addSpending(node.getCostBenefit().getCost());
                callTree.addBenefit(node.getCostBenefit().getBenefit());
                return node.getNonInlinedDescendants();
            }
            return Collections.emptyList();
        });
    }

    /**
     * Traverse the call graph again breadth-first, and inline reachable call graph nodes that had
     * been marked as inlined.
     */
    public void inlineClusters(CallTree callTree) {
        SubgraphNode root = callTree.root();
        Queue<CallTreeNode> front = new ArrayDeque<>();
        ArrayList<CallTreeNode> rootChildren = new ArrayList<>(root.children());
        root.children().clear();
        for (CallTreeNode child : rootChildren) {
            if (child.isMarkedInlined()) {
                front.add(child);
            } else if (tuningMustInline(child)) {
                front.add(child);
            } else {
                root.addChild(child);
            }
        }
        inlineQueuedRootChildren(callTree, front, child -> {
            if (child.isMarkedInlined()) {
                assert child instanceof ParentNode : "Child must be a ParentNode but is " + child;
                front.add(child);
            } else {
                addRootChild(root, child);
            }
        });
    }

    /**
     * Inlines root children from {@code front}. The {@code handleInlinedChild} callback is called
     * for each child invoke exposed by an inline, allowing callers to decide how to process it,
     * such as enqueueing it for the current inlining cluster or reattaching it as a root child.
     */
    private static void inlineQueuedRootChildren(CallTree callTree, Queue<CallTreeNode> front, Consumer<CallTreeNode> handleInlinedChild) {
        SubgraphNode root = callTree.root();
        StructuredGraph compilerGraph = root.getReadonlySubgraph();
        HighTierContext context = callTree.getContext();
        EconomicSet<Node> canonicalizableNodes = EconomicSet.create();
        while (!front.isEmpty()) {
            CallTreeNode node = front.poll();
            if (node.invoke().asNode().isAlive()) {
                if (node.parent() != null) {
                    callTree.snapshotDirectedInliningCallsites(node);
                    node.replaceAtPredecessor(null);
                }
                ParentNode parentNode = (ParentNode) node;
                parentNode.inline(context, child -> {
                    assert child != null;
                    StructuredGraph childGraph = child.invoke().asNode().graph();
                    assert compilerGraph == childGraph : compilerGraph + "!=" + childGraph;
                    handleInlinedChild.accept(child);
                }, child -> {
                    if (child.parent() != null) {
                        child.replaceAtPredecessor(null);
                    }
                    child.safeRecursiveDelete();
                }, nodes -> canonicalizableNodes.addAll(nodes));
                InliningLog inliningLog = compilerGraph.getInliningLog();
                if (inliningLog != null) {
                    inliningLog.checkInvariants(compilerGraph);
                }

                InliningUtil.logInliningDecision(node.getDebug(), "Setting \"inlined since\" because of %s with target %s",
                                node, node.targetMethod() == null ? "null" : node.targetMethod().format("%H.%n"));
                callTree.state().setInlinedSinceLastExpansion(true);

                assert node != root : "Node must not be root " + node;
                assert node.parent() == null : "Parent must be null " + node;
                node.clearSuccessors();
                node.safeDelete();
            }
        }

        callTree.getCanonicalizer().applyIncremental(compilerGraph, context, canonicalizableNodes);
    }

    void inlineForceInlinedRootChildAndRestoreInvariants(CallTree callTree, ParentNode child) {
        assert child.parent() == callTree.root() : child;
        SubgraphNode root = callTree.root();
        /*
         * Same root-child flow as inlineClusters, but with a single selected child: clear the root,
         * queue that child for deletion after inlining, and reattach the other root children.
         */
        Queue<CallTreeNode> front = new ArrayDeque<>();
        ArrayList<CallTreeNode> rootChildren = new ArrayList<>(root.children());
        root.children().clear();
        for (CallTreeNode rootChild : rootChildren) {
            if (rootChild == child) {
                setForceInlineCause(callTree, rootChild);
                front.add(rootChild);
            } else {
                addRootChild(root, rootChild);
            }
        }
        inlineQueuedRootChildren(callTree, front, grandchild -> addRootChild(callTree.root(), grandchild));
        removeDeletedChildren(callTree);
        restoreInvariants(callTree);
    }

    static void setForceInlineCause(CallTree callTree, CallTreeNode node) {
        if (callTree.matchDirectedInline(node) != null) {
            node.setInlineCause(InlineCause.DirectedInline);
        } else {
            node.setInlineCause(InlineCause.Forced);
        }
    }

    private static void addRootChild(SubgraphNode root, CallTreeNode child) {
        if (child.parent() != null) {
            if (child.parent() != root) {
                root.callTree().snapshotDirectedInliningCallsites(child);
            }
            child.replaceAtPredecessor(null);
        }
        root.addChild(child);
    }

    /**
     * If the tuning policy insists that this node should be inlined we just need to ensure it's not
     * an {@link InlineCacheNode} with no {@link SubgraphNode} children because if we inline such a
     * case (which normally can't happen but this is the tuning policy "forcing" it) we will end up
     * with the same scenario after inlining as before which can lead to an infinite loop of
     * inlining the same {@link InlineCacheNode}.
     */
    private boolean tuningMustInline(CallTreeNode node) {
        if (node instanceof ParentNode parentNode && (node.isForceInlined() || tuning.mustInline(parentNode))) {
            if (!(parentNode instanceof InlineCacheNode)) {
                return true;
            }
            for (CallTreeNode child : parentNode.children()) {
                if (child instanceof SubgraphNode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Child invokes could have been deleted during inlining (e.g. when all paths in subgraph end
     * with an unwind), so we need to remove children of the root whose invokes have been deleted.
     */
    public static void removeDeletedChildren(CallTree callTree) {
        SubgraphNode root = callTree.root();
        boolean hasDeletedChildren = false;
        for (CallTreeNode child : root.children()) {
            if (child.invoke().asNode().isDeleted()) {
                hasDeletedChildren = true;
                break;
            }
        }
        if (hasDeletedChildren) {
            List<CallTreeNode> oldChildren = root.children().snapshot();
            root.children().clear();
            for (CallTreeNode child : oldChildren) {
                if (child.invoke().asNode().isDeleted()) {
                    child.safeRecursiveDelete();
                } else {
                    root.children().add(child);
                }
            }
        }
    }

    /**
     * After inlining, we need to restore call graph invariants. The only place where invariants are
     * violated is at the root and its immediate children, because the subtrees below the root's
     * immediate children were not touched during inlining.
     */
    private static void restoreInvariants(CallTree callTree) {
        SubgraphNode root = callTree.root();
        root.createImmediateChildren(null);
        for (CallTreeNode child : root.children()) {
            callTree.restoreSubtreeInvariants(child, false);
        }
        root.enhanceParameters();
        callTree.restoreSubtreeInvariants(root, false);
    }

    private static Comparator<CallTreeNode> getTuplePriorityComparator() {
        return Comparator.comparing(CallTreeNode::getCostBenefit);
    }

    private static BenefitCostTuple getInitialCostBenefitTuple(ParentNode node) {
        if (node instanceof InlineCacheNode) {
            return BenefitCostTuple.NEUTRAL;
        } else {
            assert node instanceof SubgraphNode : "Node must be a SubgraphNode but is " + node;
            int localCost = ((SubgraphNode) node).getLocalCost();
            double localBenefit = node.getLocalBenefit();
            double childBenefits = 0.0;
            for (CallTreeNode child : node.children()) {
                if (child.getCostBenefit().hasPositiveBenefit()) {
                    childBenefits += child.getLocalBenefit();
                }
            }
            localBenefit = localBenefit - childBenefits;
            return new BenefitCostTuple(localBenefit, localCost);
        }
    }

    private static BenefitCostTuple getInitialInlineAllCostBenefitTuple(ParentNode parentNode) {
        if (parentNode instanceof InlineCacheNode) {
            return BenefitCostTuple.NEUTRAL;
        } else {
            SubgraphNode subgraphNode = (SubgraphNode) parentNode;
            return new BenefitCostTuple(subgraphNode.getLocalBenefit(), subgraphNode.getRawLocalCost());
        }
    }

    public abstract static class Policy {
        /**
         * Decide whether the call graph node should be inlined.
         */
        public abstract boolean shouldInline(CallTreeNode node, int expansionRound);

        /**
         * Do incremental cost-benefit analysis - update only parts of the call graph that changed
         * since the last analysis run.
         * <p>
         * Effectively, a subset of the call graph is traversed the tree bottom-up, and the
         * following is done for each node. First, the initial cost-priority tuple is assigned to
         * the node. Then, if a descendant is used to improve the cost priority of the node, the
         * child is marked as inlined. Otherwise, the child is marked as non-inlined. The list of
         * children that were not yet inlined for that node is recorded.
         */
        public abstract void analyzeCostBenefit(CallTree callTree);

        /**
         * Decide whether to proceed to the next inlining round.
         */
        public abstract boolean shouldContinueInlining(CallTree callTree, Optimizer optimizer, CoreProviders coreProviders);

        @SuppressWarnings("unused")
        public void postInliningRound(CallTree callTree, CoreProviders coreProviders) {
        }

        @SuppressWarnings("unused")
        public void beforeRound(CallTree callTree, Optimizer optimizer, CoreProviders coreProviders) {
        }
    }

    public static class DefaultPolicy extends Policy {

        public static final String TOO_MANY_ITERATIONS_FORMAT = "Ending inlining due to too many iterations, round %d is more than MaxRounds.";

        protected static void applyBenefitPerCostAnalysis(CallTreeNode node) {
            if (node instanceof ParentNode) {
                ParentNode parentNode = (ParentNode) node;
                BenefitCostTuple tuple = getInitialCostBenefitTuple(parentNode);
                PriorityQueue<CallTreeNode> queue = new PriorityQueue<>(getTuplePriorityComparator());
                BenefitCostTuple inlineAllTuple = getInitialInlineAllCostBenefitTuple(parentNode);

                // Mark each child non-inlined, unless that child's subtree is fully inlineable.
                // Then, compute inline-all benefit cost tuple.
                for (CallTreeNode child : node.children()) {
                    if (child.getInlineAllCostBenefitTuple().isPossible()) {
                        child.setInlineCause(InlineCause.WholeTree);
                        child.markInlined();
                    } else {
                        child.markNotInlined();
                    }
                    queue.add(child);

                    inlineAllTuple = inlineAllTuple.add(child.getInlineAllCostBenefitTuple());
                }

                while (!queue.isEmpty()) {
                    CallTreeNode child = queue.peek();
                    BenefitCostTuple childTuple = child.getCostBenefit();
                    // In the case where all children of an InlineCacheNode are with 0 benefit,
                    // which is likely due to not expanding enough, the InlineCacheNode will be
                    // also with 0 benefit. The following guard avoids inlining the whole inline
                    // cache regardless of the receiver profile, and hopes that the benefit would
                    // be updated in the next expansion round.
                    if (child instanceof InlineCacheNode && childTuple.getBenefit() == 0) {
                        queue.poll();
                        continue;
                    }
                    BenefitCostTuple improvedTuple = null;
                    if (childTuple.hasPositiveBenefit()) {
                        improvedTuple = tuple.tryImprove(childTuple);
                    }
                    if (improvedTuple != null) {
                        queue.poll();
                        child.setInlineCause(InlineCause.CostBenefit);
                        child.markInlined();
                        tuple = improvedTuple;
                        for (CallTreeNode descendant : child.getNonInlinedDescendants()) {
                            descendant.markNotInlined();
                            queue.add(descendant);
                        }
                    } else {
                        // Other children have worst benefit to cost ratio, and inlining them
                        // can only hurt benefits at this node.
                        break;
                    }
                }

                // Add extra bonus to the inline-all tuple if it is possible to inline entire
                // subtree.
                if (inlineAllTuple.isPossible()) {
                    double inlineAllBonus = InlineAllBonus.getValue(node.getOptions());
                    inlineAllTuple = inlineAllTuple.add(BenefitCostTuple.createFreeBenefit(node.getFrequency() * inlineAllBonus));
                }

                node.setNonInlinedDescendants(queue);
                node.setCostBenefitTuple(tuple);
                node.setInlineAllCostBenefitTuple(inlineAllTuple);

                // Ensure that inline-all children get inlined.
                for (CallTreeNode child : node.children()) {
                    boolean possible = child.getInlineAllCostBenefitTuple().isPossible();
                    boolean better = child.getInlineAllCostBenefitTuple().isBetterThan(child.getCostBenefit());
                    if (possible && better) {
                        child.setCostBenefitTuple(child.getInlineAllCostBenefitTuple());
                        child.setInlineCause(InlineCause.WholeTree);
                        child.markInlined();
                    }
                }
            } else if (node instanceof DeletedNode) {
                BenefitCostTuple tuple = new BenefitCostTuple(node.getLocalBenefit(), BenefitCostTuple.ZERO_COST);
                node.setCostBenefitTuple(tuple);
                node.setInlineAllCostBenefitTuple(tuple);
            } else {
                assert node instanceof CutoffNode || node instanceof IndirectNode || node instanceof GenericNode : "Unexpected node type: " + node;
                node.setCostBenefitTuple(BenefitCostTuple.IMPOSSIBLE);
                node.setInlineAllCostBenefitTuple(BenefitCostTuple.IMPOSSIBLE);
            }
            node.markCostBenefitUpdated();
        }

        @Override
        @SuppressWarnings("try")
        public boolean shouldContinueInlining(CallTree callTree, Optimizer optimizer, CoreProviders coreProviders) {
            DebugContext debug = callTree.getDebug();
            if (callTree.state().round() > PriorityInliningPhase.Options.MaxRounds.getValue(callTree.getOptions())) {
                InliningUtil.logInliningDecision(debug, TOO_MANY_ITERATIONS_FORMAT, callTree.state().round());
                assert false : String.format(TOO_MANY_ITERATIONS_FORMAT, callTree.state().round());
                return false;
            }
            if (callTree.isCallGraphTooBig()) {
                InliningUtil.logInliningDecision(debug, "inlining stopped in round %d, large call graph", callTree.state().round());
                return false;
            }
            if (callTree.getPolicy().isInlinedGraphTooBig(callTree)) {
                InliningUtil.logInliningDecision(debug, "inlining stopped in round %d, large root compiler graph", callTree.state().round());
                return false;
            }
            // If there is no remaining active cutoff, check if there was any inlining done, and if
            // there is any cutoff at all.
            if (!callTree.root().hasActiveCutoffs()) {
                // Terminate if not changed since last time there was nothing left to expand.
                if (!callTree.state().hasInlinedSinceLastExpansion()) {
                    if (optimizer.performEscapeAnalysis(callTree, coreProviders)) {
                        Inliner.removeDeletedChildren(callTree);
                        InliningUtil.logInliningDecision(debug, "inlining continues in round %d, escape analysis", callTree.state().round());
                        return true;
                    }

                    InliningUtil.logInliningDecision(debug, "inlining converged in round %d, no active cutoffs and no inline since last expansion", callTree.state().round());
                    return false;
                }
                if (callTree.root().cutoffCount() == 0) {
                    InliningUtil.logInliningDecision(debug, "inlining converged in round %d, no cutoffs left", callTree.state().round());
                    return false;
                }
            }
            InliningUtil.logInliningDecision(debug, "inlining continues in round %d, cutoff count: %d, expanded since: %b, inlined since: %b, hasActiveCutoffs: %b",
                            callTree.state().round(),
                            callTree.root().cutoffCount(),
                            callTree.state().hasExpandedSinceLastRound(),
                            callTree.state().hasInlinedSinceLastExpansion(),
                            callTree.root().hasActiveCutoffs());
            return true;
        }

        @Override
        public boolean shouldInline(CallTreeNode node, int expansionRound) {
            BenefitCostTuple tuple = node.getCostBenefit();

            String directedDontInlineRule = node.callTree().matchDirectedDontInline(node);
            if (directedDontInlineRule != null) {
                node.setDontInlineCause(DontInlineCause.DirectedDontInline);
                InliningUtil.logInliningDecision(node.getDebug(), "directed dont-inline directive matched %s for %s", directedDontInlineRule, node);
                return false;
            }

            if (node.isForceInlined() && node instanceof ParentNode) {
                // This call must be inlined.
                String directedInlineRule = node.callTree().matchDirectedInline(node);
                if (directedInlineRule != null) {
                    node.setInlineCause(InlineCause.DirectedInline);
                    InliningUtil.logInliningDecision(node.getDebug(), "directed inline directive matched %s for %s", directedInlineRule, node);
                } else {
                    node.setInlineCause(InlineCause.Forced);
                }
                return true;
            }

            if (tuple.isImpossible()) {
                // It is not possible to inline this call.
                if (node instanceof CutoffNode) {
                    node.setDontInlineCause(DontInlineCause.NotWithinBudget);
                } else if (node.getDontInlineCause() == DontInlineCause.Unspecified) {
                    node.setDontInlineCause(DontInlineCause.Indirect);
                }
                return false;
            }

            if (tuple.hasNegativeBenefit()) {
                // Inlining this call would reduce global benefit under the assumptions used by the
                // heuristics.
                node.setDontInlineCause(DontInlineCause.CostBenefit);
                return false;
            }

            if (tuple.isZeroCost()) {
                // Inlining this call does not have any associated cost.
                node.setInlineCause(InlineCause.ZeroCost);
                return true;
            }

            BenefitCostTuple rootTuple = new BenefitCostTuple(node.callTree().getTotalBenefit(), node.callTree().root().getLocalCost());
            if (rootTuple.tryImprove(tuple) != null) {
                node.setInlineCause(InlineCause.CostBenefit);
                return true;
            }

            boolean shouldInline = isWithinBudget(node, expansionRound);
            if (shouldInline) {
                node.setInlineCause(InlineCause.WithinBudget);
            } else {
                node.setDontInlineCause(DontInlineCause.NotWithinBudget);
            }
            return shouldInline;
        }

        protected boolean isWithinBudget(CallTreeNode node, int expansionRound) {
            double threshold = InliningMath.defaultInliningThreshold(node, expansionRound);
            BenefitCostTuple tuple = node.getCostBenefit();
            double relativeBenefit = tuple.relativeBenefit();
            boolean decision = relativeBenefit > threshold;
            logDecision(node, decision, threshold, relativeBenefit);
            return decision;
        }

        protected static void logDecision(CallTreeNode node, boolean decision, double threshold, double relativeBenefit) {
            DebugContext debug = node.getDebug();
            if (debug.isLogEnabled()) {
                debug.log("Inlining decision %s with threshold %f and relative priority %.4e, node=%s", decision, threshold, relativeBenefit, node);
            }
        }

        @Override
        public void analyzeCostBenefit(CallTree callTree) {
            callTree.filteredPostOrder(CallTreeNode::needsCostBenefitUpdate, DefaultPolicy::applyBenefitPerCostAnalysis);
        }

    }
}
