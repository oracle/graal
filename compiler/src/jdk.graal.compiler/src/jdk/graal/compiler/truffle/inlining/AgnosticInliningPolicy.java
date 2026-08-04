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
package jdk.graal.compiler.truffle.inlining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiArrayNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.truffle.ConstantArgumentInfo;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.phases.inlining.CallNode;
import jdk.graal.compiler.truffle.phases.inlining.CallTree;
import jdk.graal.compiler.truffle.phases.inlining.InliningPolicy;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

/// Implements language agnostic Truffle inlining by repeatedly expanding, analyzing, and inlining
/// nodes in a [CallTree].
///
/// Each round expands promising cutoff nodes within a bounded exploration budget, groups expanded
/// nodes into cost-benefit clusters, and inlines the most valuable clusters. The priorities account
/// for execution frequency, graph size, remaining cutoffs, recursion depth, and nested Truffle
/// callees. Before analyzing an expanded call, the policy also propagates constant call arguments
/// into the callee graph when safe and applies conditional elimination and partial escape analysis
/// to expose further optimization opportunities.
public class AgnosticInliningPolicy implements InliningPolicy {

    private static final int TRUFFLE_LEAF_BONUS = 50;
    private static final int TRUFFLE_CALLEE_BUDGET = 12;
    private static final int TRUFFLE_CALLEE_SCALE = 1;
    private static final int OVER_EXPLORATION_BUDGET = 12;
    private static final int OVER_EXPLORATION_SCALE = 1;
    private static final int EXPANSIONS_PER_CUTOFF_MULTIPLIER = 3;
    private static final Comparator<CallNode> CALL_NODE_EXPAND_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(CallNode x, CallNode y) {
            final int compare = Double.compare(data(y).expansionPriority, data(x).expansionPriority);
            if (compare == 0) {
                return x.compareTo(y);
            }
            return compare;
        }
    };
    private static final Comparator<CallNode> CALL_NODE_COST_BENEFIT_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(CallNode x, CallNode y) {
            final int compare = costBenefit(y).compareTo(costBenefit(x));
            if (compare == 0) {
                return x.compareTo(y);
            }
            return compare;
        }
    };
    private static final int ALLOW_FORCED_THRESHOLD = 10;
    private static final int ALLOW_FORCED_RECURSIONS = 2;
    private static final int SCALE = 6;
    private static final double NODE_COUNT_PENALTY = 0.005;
    private static final double EXPAND_ALL_PROXIMITY_FACTOR = 0.5;
    private static final double EXPAND_ALL_PROXIMITY_BONUS = 10;
    private static final double CUTOFF_COUNT_PENALTY = 0.1;
    private final InliningConstants inliningConstants;
    private int round;
    private boolean beenExpanding;
    private boolean beenInlining;
    private int expansionsLeft;
    protected final CoreProviders providers;
    private final ConditionalEliminationPhase conditionalElimination;
    private final PartialEscapePhase partialEscape;
    private final CanonicalizerPhase canonicalizer;

    @SuppressWarnings("unused")
    AgnosticInliningPolicy(OptionValues options, CoreProviders providers) {
        this.inliningConstants = new InliningConstants(options);
        this.providers = providers;
        this.canonicalizer = CanonicalizerPhase.create();
        this.conditionalElimination = new ConditionalEliminationPhase(canonicalizer, false);
        this.partialEscape = new PartialEscapePhase(TruffleCompilerOptions.IterativePartialEscape.getValue(options), canonicalizer, options);
    }

    private static CostBenefit costBenefit(CallNode node) {
        return data(node).costBenefit;
    }

    private static CallNodeData data(CallNode node) {
        return (CallNodeData) node.getPolicyData();
    }

    /**
     * This method is looking for these two patterns.
     *
     * <pre>
     *     P(1)
     *      |
     *     PiArray
     *      |
     *      Pi <-- return this node
     *      | \
     *     ..  ..
     * </pre>
     *
     * <pre>
     *     P(1)
     *      |
     *      Pi <-- return this node
     *      | \
     *     ..  ..
     * </pre>
     */
    private static ValueNode unproxifyArgumentArray(ParameterNode parameterArray) {
        ValueNode castArray;
        final NodeIterable<Node> parameterArrayUsages = parameterArray.usages().filter(n -> !(n instanceof FrameState));
        // Argument array not used in callee.
        if (parameterArrayUsages.count() == 0) {
            return null;
        }
        if (parameterArrayUsages.count() != 1 || !(parameterArrayUsages.first() instanceof PiArrayNode || parameterArrayUsages.first() instanceof PiNode)) {
            parameterArray.getDebug().log(DebugContext.VERBOSE_LEVEL, "Parameter array is not used only in a PiArray: %s", parameterArrayUsages.snapshot());
            return null;
        }
        if (parameterArrayUsages.first() instanceof PiArrayNode) {
            PiArrayNode piArray = (PiArrayNode) parameterArrayUsages.first();
            final NodeIterable<Node> piArrayUsages = piArray.usages().filter(n -> !(n instanceof FrameState));
            if (piArrayUsages.count() == 0) {
                return null;
            }
            if (piArrayUsages.count() != 1 || !(piArrayUsages.first() instanceof PiNode)) {
                parameterArray.getDebug().log(DebugContext.VERBOSE_LEVEL, "PiArray is not used only in a Pi: %s", piArrayUsages.snapshot());
                return null;
            }
            castArray = (PiNode) piArrayUsages.first();
        } else {
            castArray = (PiNode) parameterArrayUsages.first();
        }
        return castArray;
    }

    private static int expansionsPerRound(int cutoffCount) {
        return EXPANSIONS_PER_CUTOFF_MULTIPLIER * cutoffCount;
    }

    private static void analyseNode(CallNode node) {
        data(node).cluster = node.isTrivial();
        initCostBenefit(node);
        final PriorityQueue<CallNode> analysisQueue = resetAnalysisQueue(node);
        CallNode first;
        while ((first = analysisQueue.peek()) != null) {
            final CostBenefit currentNodeCB = costBenefit(node);
            final CostBenefit firstNodeCB = costBenefit(first);
            final CostBenefit mergedCB = currentNodeCB.merge(firstNodeCB);
            if (firstNodeCB.benefitPerCost() > 0 && mergedCB.compareTo(currentNodeCB) >= 0) {
                data(node).costBenefit = mergedCB;
                data(first).cluster = true;
                analysisQueue.addAll(data(first).analysisQueue);
                analysisQueue.poll();
            } else {
                break;
            }
        }
        updateLeafCluster(node);
    }

    private static void initCostBenefit(CallNode node) {
        data(node).costBenefit = new CostBenefit(cost(node), getInitialAnalysisBenefit(node));
    }

    private static int cost(CallNode node) {
        if (node.getState() == CallNode.State.Cutoff) {
            return Integer.MAX_VALUE;
        }
        return node.getSize();
    }

    private static double getInitialAnalysisBenefit(CallNode node) {
        double initialBenefit = getAnalysisBenefit(node);
        for (CallNode child : node.getChildren()) {
            if (child.getState() != CallNode.State.Removed && child.getState() != CallNode.State.BailedOut) {
                initialBenefit -= getAnalysisBenefit(child);
            }
        }
        return initialBenefit;
    }

    private static double getAnalysisBenefit(CallNode node) {
        return node.getRootRelativeFrequency();
    }

    private static PriorityQueue<CallNode> resetAnalysisQueue(CallNode node) {
        final PriorityQueue<CallNode> inlineQueue = data(node).analysisQueue;
        inlineQueue.clear();
        for (CallNode child : node.getChildren()) {
            if (child.getState() == CallNode.State.Expanded || child.getState() == CallNode.State.Inlined) {
                inlineQueue.add(child);
            }
        }
        return inlineQueue;
    }

    private static void updateLeafCluster(CallNode callNode) {
        boolean isLeafCluster = true;
        for (CallNode child : callNode.getChildren()) {
            if (child.getState() == CallNode.State.Indirect) {
                isLeafCluster = false;
                break;
            }
            if (child.getState() != CallNode.State.Removed && child.getState() != CallNode.State.BailedOut) {
                isLeafCluster &= (data(child).cluster && data(child).isLeafCluster);
            }
        }
        data(callNode).isLeafCluster = isLeafCluster;
    }

    @Override
    public void run(CallTree tree) {
        do {
            tree.dumpInfo("before expand round %d", round);
            expand(tree);
            tree.dumpInfo("before analyse round %d", round);
            analyse(tree);
            tree.dumpInfo("before inline round %d", round);
            inline(tree);
        } while (!isConverged());
    }

    private boolean isConverged() {
        final boolean converged = !beenExpanding && !beenInlining;
        beenExpanding = false;
        beenInlining = false;
        round++;
        return converged;
    }

    private void expand(CallTree tree) {
        // Populate priority lists.
        expansionsLeft = expansionsPerRound(initializeForExpansion(tree.getRoot()));

        // Descend until convergence.
        while (continueExpand()) {
            descend(tree.getRoot());
        }
    }

    private int initializeForExpansion(CallNode node) {
        switch (node.getState()) {
            case Cutoff:
                return 1;
            case Inlined:
            case Expanded:
                return resetExpandQueue(node);
            case Removed:
            case BailedOut:
            case Indirect:
                return 0;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(node.getState()); // ExcludeFromJacocoGeneratedReport
    }

    private int resetExpandQueue(CallNode node) {
        PriorityQueue<CallNode> expandQueue = data(node).expandQueue;
        expandQueue.clear();
        int cutoffCount = 0;
        for (CallNode child : node.getChildren()) {
            int childCutoffCount = initializeForExpansion(child);
            if (childCutoffCount > 0) {
                expandQueue.add(child);
            }
            cutoffCount += childCutoffCount;
        }
        return cutoffCount;
    }

    private boolean continueExpand() {
        expansionsLeft--;
        return expansionsLeft >= 0;
    }

    private CallNode descend(CallNode node) {
        switch (node.getState()) {
            case Inlined:
            case Expanded:
                PriorityQueue<CallNode> subtrees = data(node).expandQueue;
                if (subtrees.isEmpty()) {
                    return null;
                }
                CallNode first = subtrees.poll();
                first = descend(first);
                if (first != null && !first.getChildren().isEmpty()) {
                    subtrees.add(first);
                    return node;
                } else if (!subtrees.isEmpty()) {
                    return node;
                } else {
                    return null;
                }
            case Cutoff:
                if (shouldExpand(node)) {
                    beenExpanding = true;
                    node.expand();
                    return node;
                } else {
                    return null;
                }
            case Indirect:
            case Removed:
            case BailedOut:
                return null;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(node.getState()); // ExcludeFromJacocoGeneratedReport
    }

    private boolean shouldExpand(CallNode node) {
        final CallTree callTree = node.getCallTree();
        final int subtreeIRNodeCount = data(callTree.getRoot()).subtreeIRNodeCount;
        final double threshold = growth(inliningConstants.expansionBaseBudget, SCALE, subtreeIRNodeCount);
        final double expansionBenefit = getExpansionBenefit(node);
        return expansionBenefit >= threshold || isReasonablyForced(node, threshold);
    }

    private static boolean isReasonablyForced(CallNode node, double threshold) {
        return node.isForced() && threshold < ALLOW_FORCED_THRESHOLD && node.getRecursionDepth() < ALLOW_FORCED_RECURSIONS;
    }

    protected double getExpansionBenefit(CallNode node) {
        return node.getRootRelativeFrequency() - getRecursiveExpansionPenalty(node) - overExplorationPenalty(node, node.getCallTree()) + truffleCalleeFactor(node);
    }

    private double overExplorationPenalty(CallNode node, CallTree callTree) {
        return node.getRootRelativeFrequency() * growth(OVER_EXPLORATION_BUDGET, OVER_EXPLORATION_SCALE, (double) callTree.getExpandedCount() / callTree.getInlinedCount());
    }

    private double truffleCalleeFactor(CallNode node) {
        final int length = node.getTruffleCallees();
        if (length == 0) {
            return TRUFFLE_LEAF_BONUS * node.getRootRelativeFrequency();
        }
        return -1 * node.getRootRelativeFrequency() * growth(TRUFFLE_CALLEE_BUDGET, TRUFFLE_CALLEE_SCALE, length);
    }

    private double getRecursiveExpansionPenalty(CallNode node) {
        int depth = node.getRecursionDepth();
        return Math.max(1, node.getRootRelativeFrequency()) * Math.max(0, Math.pow(2, depth) - Math.pow(2, this.inliningConstants.penaltyFreeRecursionDepth));
    }

    private static void analyse(CallTree tree) {
        analyse(tree.getRoot());
    }

    private static void analyse(CallNode node) {
        for (CallNode child : node.getChildren()) {
            analyse(child);
        }
        if (node.getState() == CallNode.State.Cutoff || node.getState() == CallNode.State.Expanded) {
            analyseNode(node);
        }
    }

    private void inline(CallTree tree) {
        inline(tree.getRoot());
    }

    private void inline(CallNode root) {
        PriorityQueue<CallNode> front = getInliningFront(root);
        while (!front.isEmpty()) {
            // Pick most promising callsite.
            CallNode inlineCandidate = front.poll();
            if (canInline(inlineCandidate)) {
                inlineCluster(inlineCandidate, front);
                root.recalculateSize();
            }
        }
    }

    private PriorityQueue<CallNode> getInliningFront(CallNode root) {
        PriorityQueue<CallNode> front = new PriorityQueue<>(CALL_NODE_COST_BENEFIT_COMPARATOR);
        collectInliningFront(root, front);
        return front;
    }

    private void collectInliningFront(CallNode node, PriorityQueue<CallNode> front) {
        switch (node.getState()) {
            case Cutoff:
            case Indirect:
            case BailedOut:
            case Removed:
                return;
            case Inlined:
                for (CallNode child : node.getChildren()) {
                    collectInliningFront(child, front);
                }
                break;
            case Expanded:
                front.add(node);
                break;
        }
    }

    private boolean canInline(CallNode candidate) {
        if (!InliningPolicy.acceptForInline(candidate, inliningConstants.inlineOnly)) {
            return false;
        }
        final CallTree callTree = candidate.getCallTree();
        int rootCost = callTree.getRoot().getSize();
        final double nextCost = rootCost + costBenefit(candidate).getCost();
        double threshold = growth(inliningConstants.inliningBaseBudget, SCALE, nextCost);
        data(candidate).threshold = threshold;
        final double benefitPerCost = costBenefitForInlining(candidate).benefitPerCost();
        data(candidate).benefitPerCost = benefitPerCost;
        return benefitPerCost > threshold || (threshold < ALLOW_FORCED_THRESHOLD && candidate.isForced()) || candidate.isTrivial();
    }

    /**
     * Exponential growth function. The function is defined by budget and scale such that:
     *
     * <pre>
     * growth(budget, scale, budget) == Math.pow(10, -1 * scale)
     * growth(budget, scale, 2 * budget) == 1
     * </pre>
     *
     * i.e. The function always yields the value 1 for x == 2 * budget, and the value for x ==
     * budget is always 10^-scale.
     */
    protected double growth(double baseBudget, int scale, double x) {
        return Math.exp(((x - 2 * baseBudget) * (scale * Math.log(10))) / baseBudget);
    }

    protected CostBenefit costBenefitForInlining(CallNode node) {
        CostBenefit costBenefit = data(node).costBenefit;
        final double frequency = node.getRootRelativeFrequency();
        if (data(node).isLeafCluster) {
            costBenefit = costBenefit.withBenefitBoostedBy(frequency);
        }
        if (node.getParent().getChildren().size() == 1) {
            costBenefit = costBenefit.withBenefitBoostedBy(frequency);
        }
        if (node.getDirectCallTarget().getKnownCallSiteCount() == 1) {
            costBenefit = costBenefit.withBenefitBoostedBy(frequency);
        }
        for (CallNode child : node.getChildren()) {
            if (child.getState() == CallNode.State.Indirect) {
                costBenefit = costBenefit.withBenefitBoostedBy(-1 * frequency);
            }
        }
        return costBenefit;
    }

    private void inlineCluster(CallNode inlineCandidate, PriorityQueue<CallNode> front) {
        inlineCandidate.inline(new DuplicationUtil.EEInliningReturnAction(providers));
        for (CallNode child : inlineCandidate.getChildren()) {
            if (child.getState() == CallNode.State.Indirect || child.getState() == CallNode.State.Removed || child.getState() == CallNode.State.BailedOut) {
                continue;
            }
            if (!InliningPolicy.acceptForInline(inlineCandidate, inliningConstants.inlineOnly)) {
                continue;
            }
            if (data(child).cluster) {
                inlineCluster(child, front);
            } else if (child.getState() != CallNode.State.Cutoff) {
                front.add(child);
            }
        }
    }

    @Override
    public void afterAddChildren(CallNode callNode) {
        for (CallNode child : callNode.getChildren()) {
            if (child.getState() != CallNode.State.Removed && child.getState() != CallNode.State.BailedOut) {
                data(callNode).expandQueue.add(child);
            }
        }
    }

    @Override
    public void removedNode(CallNode node) {
        data(node.getParent()).expandQueue.remove(node);
    }

    @Override
    public void afterExpand(CallNode callNode) {
        expandTrivialChildren(callNode);
        tryEnhancement(callNode);
        updateParentChain(callNode);
    }

    private void tryEnhancement(CallNode node) {
        Iterable<Node> enhancedNodes = enhanceParameters(node);
        if (enhancedNodes != null) {
            data(node).enhanced = true;
            canonicalizer.applyIncremental(node.getIR(), providers, enhancedNodes);
            enhance(node);
            for (CallNode child : node.getChildren()) {
                if (child.getInvoke() == null || !child.getInvoke().isAlive()) {
                    child.remove();
                }
            }
        }
    }

    private Iterable<Node> enhanceParameters(CallNode node) {
        Invoke invoke = node.getInvoke();
        if (invoke != null && invoke.callTarget() != null) {
            final ConstantArgumentInfo[] constants = node.findConstantGuestArguments();
            if (constants != null) {
                return replaceCalleeParameters(node, constants);
            }
        }
        return null;
    }

    private Iterable<Node> replaceCalleeParameters(CallNode node, ConstantArgumentInfo[] constants) {
        List<Node> updatedUsages = new ArrayList<>();
        if (constants == null) {
            node.getDebug().log(DebugContext.VERBOSE_LEVEL, "Constants array is null.");
            return null;
        }
        // Check that the argument array is safe, where safe means that it is not modified.
        final StructuredGraph ir = node.getIR();
        final ParameterNode parameterArray = ir.getParameter(2);
        final ValueNode castArray = unproxifyArgumentArray(parameterArray);
        if (castArray == null) {
            return null;
        }
        if (CallNode.isArgumentArrayMutated(castArray, null)) {
            return null;
        }

        // Replace callee parameters.
        boolean replaced = false;
        for (Node usage : castArray.usages()) {
            if (!(usage instanceof LoadIndexedNode && ((LoadIndexedNode) usage).index() instanceof ConstantNode)) {
                continue;
            }
            LoadIndexedNode loadParameter = (LoadIndexedNode) usage;
            int index = loadParameter.index().asJavaConstant().asInt();
            if (index >= constants.length) {
                // Callee has a different speculation about the number of arguments.
                continue;
            }
            if (constants[index] == null) {
                continue;
            }
            for (Node updatedUsage : loadParameter.usages()) {
                updatedUsages.add(updatedUsage);
            }
            constants[index].replaceInGraph(loadParameter, providers.getMetaAccess());
            replaced = true;
        }
        if (replaced) {
            return updatedUsages; // might be an empty list, but the graph is changed
        } else {
            return null;
        }
    }

    private void enhance(CallNode node) {
        StructuredGraph ir = node.getIR();
        // Apply conditional elimination in the root, to remove redundant branches.
        conditionalElimination.apply(ir, providers);
        // Apply partial-analysis to get rid parameter list allocations.
        partialEscape.apply(ir, providers);
    }

    protected void expandTrivialChildren(CallNode callNode) {
        for (CallNode child : callNode.getChildren()) {
            if (child.isTrivial()) {
                child.expand();
            }
        }
    }

    protected void updateParentChain(CallNode callNode) {
        assert callNode.getState() != CallNode.State.Cutoff : "Node must not be a Cutoff: " + callNode;
        data(callNode).subtreeCallNodes = 1;
        data(callNode).subtreeCutoffCount = 0;
        data(callNode).subtreeIRNodeCount = callNode.getIR().getNodeCount();
        for (CallNode child : callNode.getChildren()) {
            if (child.getState() == CallNode.State.Removed || child.getState() == CallNode.State.BailedOut) {
                continue;
            }
            if (child.getState() == CallNode.State.Indirect) {
                data(callNode).subtreeCallNodes++;
            } else {
                data(callNode).subtreeCallNodes += data(child).subtreeCallNodes;
            }
            if (child.getState() == CallNode.State.Cutoff) {
                data(callNode).subtreeCutoffCount++;
            }
            if (child.getState() == CallNode.State.Expanded || child.getState() == CallNode.State.Inlined) {
                data(callNode).subtreeCutoffCount += data(child).subtreeCutoffCount;
                data(callNode).subtreeIRNodeCount += data(child).subtreeIRNodeCount;
            }
        }
        data(callNode).intrinsicExpansionPriority = getIntrinsicExpansionPriority(callNode);
        data(callNode).expansionPriority = getExpansionPriority(callNode);

        final CallNode parent = callNode.getParent();
        if (parent != null) {
            updateParentChain(parent);
        }
    }

    private static double getExpansionPriority(CallNode node) {
        return getExpansionPriority(node, data(node).intrinsicExpansionPriority);
    }

    private static double getExpansionPriority(CallNode node, double intrinsicExpansionPriority) {
        return intrinsicExpansionPriority - getExpansionPenalty(node);
    }

    private static double getExpansionPenalty(CallNode node) {
        final int subtreeCutoffCount = node.getState() == CallNode.State.Cutoff ? 1 : data(node).subtreeCutoffCount;
        final int subtreeIRNodeCount = node.getState() == CallNode.State.Cutoff ? 0 : data(node).subtreeIRNodeCount;
        final double subtreeIRNodeFactor = NODE_COUNT_PENALTY * subtreeIRNodeCount;
        final double subtreeCutoffFactor = CUTOFF_COUNT_PENALTY * subtreeCutoffCount;
        final double expandAllProximityFactor = EXPAND_ALL_PROXIMITY_FACTOR * Math.max(0, EXPAND_ALL_PROXIMITY_BONUS - Math.pow(subtreeCutoffCount, 2));
        return node.getRootRelativeFrequency() * (subtreeIRNodeFactor + subtreeCutoffFactor - expandAllProximityFactor);
    }

    private double getIntrinsicExpansionPriority(CallNode node) {
        final CallNode.State state = node.getState();
        if (state == CallNode.State.Cutoff) {
            return getExpansionBenefit(node);
        }
        if (state == CallNode.State.Expanded || state == CallNode.State.Inlined) {
            double max = Double.MIN_VALUE;
            for (CallNode child : node.getChildren()) {
                if (child.getState() != CallNode.State.Removed && child.getState() != CallNode.State.Indirect && child.getState() != CallNode.State.BailedOut) {
                    max = Math.max(max, data(child).expansionPriority);
                }
            }
            return max;
        }
        GraalError.shouldNotReachHere("Looking up priorities for node with state " + state); // ExcludeFromJacocoGeneratedReport
        return Double.MIN_VALUE;
    }

    @Override
    public void putProperties(CallNode node, Map<Object, Object> properties) {
        CallNodeData data = data(node);
        // Data is only for indirect call nodes
        if (data == null) {
            return;
        }
        properties.put("Leaf cluster", data.isLeafCluster);
        properties.put("Expansion Benefit", getExpansionBenefit(node));
        properties.put("Expansion Priority", node.getState() != CallNode.State.Removed && node.getState() != CallNode.State.BailedOut ? getExpansionPriority(node) : 0);
        properties.put("Cost Benefit", data.costBenefit);
        properties.put("Threshold", Double.toString(data.threshold));
        properties.put("Inline BpC", Double.toString(data.benefitPerCost));
        properties.put("Cluster", data.cluster);
        properties.put("Subtree Call Nodes", data.subtreeCallNodes);
        properties.put("Subtree IR Nodes", data.subtreeIRNodeCount);
        properties.put("Subtree Cutoffs", data.subtreeCutoffCount);
        properties.put("Enhanced", data.enhanced);
    }

    @Override
    public Object newCallNodeData(CallNode callNode) {
        return new CallNodeData(callNode);
    }

    static class InliningConstants {
        final double expansionBaseBudget;
        final double inliningBaseBudget;
        final int penaltyFreeRecursionDepth;
        private final String inlineOnly;

        InliningConstants(OptionValues options) {
            this.expansionBaseBudget = TruffleCompilerOptions.InliningExpansionBudget.getValue(options);
            this.inliningBaseBudget = TruffleCompilerOptions.InliningInliningBudget.getValue(options);
            this.penaltyFreeRecursionDepth = TruffleCompilerOptions.InliningRecursionDepth.getValue(options);
            this.inlineOnly = TruffleCompilerOptions.InlineOnly.getValue(options);
        }
    }

    class CallNodeData {
        final PriorityQueue<CallNode> expandQueue;
        final PriorityQueue<CallNode> analysisQueue;
        double benefitPerCost;
        CostBenefit costBenefit;
        double threshold;
        double expansionPriority;
        double intrinsicExpansionPriority;
        boolean cluster;
        int subtreeCallNodes;
        int subtreeCutoffCount;
        int subtreeIRNodeCount;
        boolean isLeafCluster;
        boolean enhanced;

        CallNodeData(CallNode callNode) {
            this.intrinsicExpansionPriority = getIntrinsicExpansionPriority(callNode);
            this.expansionPriority = getExpansionPriority(callNode, intrinsicExpansionPriority);
            this.costBenefit = new CostBenefit(Double.NaN, Double.NaN);
            this.expandQueue = new PriorityQueue<>(CALL_NODE_EXPAND_COMPARATOR);
            this.analysisQueue = new PriorityQueue<>(CALL_NODE_COST_BENEFIT_COMPARATOR);
            this.subtreeCallNodes = 1;
            this.subtreeCutoffCount = 1;
        }
    }
}
