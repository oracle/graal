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

import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.CallGraphCompilerNodeLimit;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.CallGraphSizeLimit;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.CallGraphSizePenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.CompilerNodePenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.CutoffCodeSizePenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.ExpansionInertiaBaseValue;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.ExpansionInertiaInvokeBonus;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.InlinedCompilerNodeLimit;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.LargeChildrenCountPenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.RootSizePenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.RootSizePenaltyTypicalGraphSize;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.SmallRootIrPenaltyCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.TypicalCallGraphSize;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.TypicalGraphSize;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.TypicalGraphSizeInvokeBonus;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;

/**
 * Expands the call graph until a policy-specific condition is met.
 */
public class Expander {

    public static final double NODE_LOWEST_PRIORITY = -Double.MAX_VALUE;

    private final Policy policy;
    private final TuningPolicy tuningPolicy;
    private final TimerKey expanderExtraAnalysisDuration;

    public Expander(Policy policy, TuningPolicy tuningPolicy, TimerKey expanderExtraAnalysisDuration) {
        this.policy = policy;
        this.tuningPolicy = tuningPolicy;
        this.expanderExtraAnalysisDuration = expanderExtraAnalysisDuration;
    }

    public void run(CallTree callTree, CoreProviders coreProviders, int expansionRound) {
        DebugContext debugContext = callTree.getDebug();
        policy.beforeExpansion(callTree, coreProviders, expansionRound, expanderExtraAnalysisDuration);

        SubgraphNode root = callTree.root();
        if (!root.hasActiveCutoffs()) {
            restoreActiveCutoffNodes(root);
        }

        callTree.resetExpansionsLeft();

        while (callTree.hasExpansionsLeft() && !policy.isCallGraphTooBig(callTree)) {
            expandHighestPriorityAndUpsweepInvariants(root, coreProviders, expansionRound);
        }

        debugContext.dump(DebugContext.VERBOSE_LEVEL, callTree, "round %d, after expansion", expansionRound);

        // Run post-expansion analysis phase.
        policy.afterExpansionPhase(callTree, coreProviders, expansionRound, expanderExtraAnalysisDuration);
    }

    private static void restoreActiveCutoffNodes(CallTreeNode node) {
        if (node instanceof ParentNode) {
            node.setActiveCutoffCount(0);
            for (CallTreeNode child : node.children()) {
                if (child.cutoffCount() > 0) {
                    restoreActiveCutoffNodes(child);
                    node.increaseActiveCutoffCount(child.activeCutoffCount());
                    if (child.hasActiveCutoffs()) {
                        ((ParentNode) node).addToExpansionQueue(child);
                    }
                }
            }
        } else if (node instanceof CutoffNode) {
            node.setActiveCutoffCount(1);
        }
    }

    private CallTreeNode expandHighestPriorityAndUpsweepInvariants(CallTreeNode callNode, CoreProviders coreProviders, int expansionRound) {
        CallTree callTree = callNode.callTree();
        if (callNode instanceof ParentNode) {
            if (!callNode.hasActiveCutoffs()) {
                callTree.decrementExpansionLeft();
                return callNode;
            }
            ParentNode node = (ParentNode) callNode;

            // Pick subtree with the highest priority, and expand it.
            CallTreeNode oldChild;
            do {
                oldChild = node.pollExpansionQueue();
            } while (oldChild != null && oldChild.isDeleted());

            if (oldChild == null || !oldChild.hasActiveCutoffs()) {
                // Remaining child nodes have no active cutoffs.
                node.setActiveCutoffCount(0);
                callTree.decrementExpansionLeft();
                return node;
            }

            // Exclude this child from the parent's cut off counters.
            node.excludeChildInCounts(oldChild);

            // Update the children queue if necessary.
            CallTreeNode newChild = expandHighestPriorityAndUpsweepInvariants(oldChild, coreProviders, expansionRound);

            if (newChild.hasActiveCutoffs()) {
                node.addToExpansionQueue(newChild);
            }
            if (newChild.needsParameterEnhancement() || newChild.hasChildForParameterEnhancement()) {
                node.setHasChildForParameterEnhancement(true);
            }

            // Include the new child from the parent's cut off counters.
            node.includeChildInCounts(newChild);

            policy.updateParentNodePriority(node);
            node.updateSubtreeStatistics();
            node.markNeedsCostBenefitUpdate();

            // No need to update local benefit, because it did not change.
            // No need to decrement expansionsLeft, because no expansion was done.

            callTree.getDebug().dump(DebugContext.DETAILED_LEVEL, callTree, "Call graph during expanding parent node %s at child node %s", node, newChild);
            return node;
        } else if (callNode instanceof CutoffNode) {
            CutoffNode node = (CutoffNode) callNode;

            if (!policy.shouldExpand(node)) {
                node.setActiveCutoffCount(0);
                // No need to decrement expansionsLeft because no actual expansion was done.
                return node;
            }

            CallTreeNode replacementNode = expandCutoffAndRestoreSubtreeInvariants(node, coreProviders, expansionRound);
            callTree.decrementExpansionLeft();
            return replacementNode;
        } else {
            throw GraalError.shouldNotReachHere("Should not expand " + callNode + ", while inlining: " + callTree.root().getReadonlySubgraph().method().format("%H.%n")); // ExcludeFromJacocoGeneratedReport
        }
    }

    private CallTreeNode expandCutoffAndRestoreSubtreeInvariants(CutoffNode node, CoreProviders coreProviders, int expansionRound) {
        CallTree callTree = node.callTree();
        CallTreeNode replacementNode = callTree.expandCutoffNode(node);
        callTree.copyDirectedInliningCallsites(node, replacementNode);

        assert replacementNode != node : "Expansion must replace cutoff node " + node + ", replacement: " + replacementNode;
        node.replaceAtPredecessor(replacementNode);
        policy.afterExpandingCutoffNode(replacementNode, node, coreProviders, expansionRound, expanderExtraAnalysisDuration);
        node.safeDelete();
        callTree.restoreSubtreeInvariants(replacementNode, true);
        return replacementNode;
    }

    /**
     * Expands a force-inlined cutoff that is a direct child of the root after the main expansion
     * loop has completed. This does not update the normal expansion budget.
     */
    public CallTreeNode expandFinalForceInlinedRootCutoff(CutoffNode node, CoreProviders coreProviders, int expansionRound) {
        assert node.parent() == node.callTree().root() : node;
        return expandFinalForceInlinedCutoff(node, coreProviders, expansionRound);
    }

    /**
     * Expands a force-inlined cutoff after the main expansion loop has completed. This does not
     * update the normal expansion budget.
     */
    public CallTreeNode expandFinalForceInlinedCutoff(CutoffNode node, CoreProviders coreProviders, int expansionRound) {
        CallTreeNode parent = node.parent();
        CallTreeNode replacementNode = expandCutoffAndRestoreSubtreeInvariants(node, coreProviders, expansionRound);
        if (parent != null) {
            replacementNode.callTree().restoreSubtreeInvariants(parent, false);
        }
        return replacementNode;
    }

    public final Policy policy() {
        return policy;
    }

    public TuningPolicy tuningPolicy() {
        return tuningPolicy;
    }

    public abstract static class Policy {

        public abstract void updateParentNodeLocalBenefit(ParentNode node);

        public abstract void updateParentNodePriority(ParentNode node);

        public abstract void updateCutoffNodeLocalBenefit(CutoffNode node);

        public abstract void updateCutoffNodePriority(CutoffNode node);

        public abstract boolean isCallGraphTooBig(CallTree callTree);

        public abstract boolean isInlinedGraphTooBig(CallTree callTree);

        public abstract boolean shouldStopPeeling(CallTree callTree);

        public abstract boolean shouldExpand(CutoffNode node);

        public abstract boolean shouldBeIndirect(CutoffNode node);

        public abstract boolean isExpandedOften(CallTreeNode node);

        public abstract int typicalGraphSize(OptionValues options);

        public abstract int typicalGraphSizeInvokeBonus(OptionValues options);

        public abstract int expansionInertiaBaseValue(OptionValues options);

        public abstract int expansionInertiaInvokeBonus(OptionValues options);

        @SuppressWarnings("unused")
        public void afterExpansionPhase(CallTree callTree, CoreProviders coreProviders, int expansionRound, TimerKey expanderExtraAnalysisDuration) {
        }

        @SuppressWarnings("unused")
        public void afterExpandingCutoffNode(CallTreeNode replacementNode, CallTreeNode replacedNode, CoreProviders coreProviders, int expansionRound, TimerKey expanderExtraAnalysisDuration) {
        }

        @SuppressWarnings("unused")
        public void beforeRound(CallTree callTree) {
        }

        @SuppressWarnings("unused")
        public void beforeExpansion(CallTree callTree, CoreProviders coreProviders, int round, TimerKey expanderExtraAnalysisDuration) {
        }

        @SuppressWarnings("unused")
        public boolean enhanceIndirectNode(IndirectNode node) {
            return false;
        }

        @SuppressWarnings("unused")
        public boolean isSpecialCallTarget(Invoke node) {
            return false;
        }

        @SuppressWarnings("unused")
        public CallTreeNode expandSpecialTarget(CallTree callTree, CutoffNode node) {
            throw GraalError.unimplemented("This policy does not allow expanding special targets."); // ExcludeFromJacocoGeneratedReport
        }

        @SuppressWarnings("unused")
        public void applyPostPhases(StructuredGraph rootGraph, HighTierContext context) {
        }

        @SuppressWarnings("unused")
        public int getExtraStatisticsMetric(CallTree callTree) {
            return 0;
        }

        public CallTreeState createCallTreeState() {
            return new CallTreeState();
        }
    }

    public static class DefaultPolicy extends Policy {

        private static final int MAX_PEELING_SIZE = 6000;
        private static final double RECURSION_PENALTY_MODIFIER = 0.01;
        private static final int LARGE_EXPLORED_IR_CHECK_THRESHOLD = 600;
        private static final int CHILDREN_COUNT_CHECK_THRESHOLD = 40;

        @Override
        public boolean isCallGraphTooBig(CallTree callTree) {
            OptionValues options = callTree.getOptions();
            int nonRootCallCount = callTree.getNodeCount() - callTree.root().children().size();
            int compilerNodeCount = callTree.root().getSubtreeTotalCompilerNodeCount();
            return nonRootCallCount >= CallGraphSizeLimit.getValue(options) || compilerNodeCount >= CallGraphCompilerNodeLimit.getValue(options);
        }

        @Override
        public boolean isInlinedGraphTooBig(CallTree callTree) {
            final OptionValues options = callTree.getOptions();
            return callTree.root().getReadonlySubgraph().getNodeCount() > InlinedCompilerNodeLimit.getValue(options);
        }

        @Override
        public boolean shouldStopPeeling(CallTree callTree) {
            if (isCallGraphTooBig(callTree)) {
                return true;
            }
            if (callTree.root().getReadonlySubgraph().getNodeCount() > MAX_PEELING_SIZE) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldExpand(CutoffNode node) {
            if (node.isForceInlined()) {
                return true;
            }

            OptionValues options = node.getOptions();
            CallTree callTree = node.callTree();
            final SubgraphNode root = callTree.root();
            final TuningPolicy tuning = node.callTree().tuningPolicy();

            // Calculate the relative benefit.
            double recursionPenalty = RECURSION_PENALTY_MODIFIER * InliningMath.defaultRecursionPenalty(node);
            double relativeBenefit = (node.getLocalBenefit() - recursionPenalty) / node.getCostEstimate();

            // The base threshold value.
            double benefitThreshold = InliningMath.defaultExpansionThreshold(this, node);
            benefitThreshold *= tuning.relativeBenefitThresholdMultiplier(node);

            // Increase the threshold if the call graph is very large.
            int callTreeSize = Math.max(0, node.callTree().getNodeCount() - getTypicalCallGraphSizeValue(options));
            benefitThreshold += callTreeSize * getCallGraphSizePenaltyCoefficientValue(options) * tuning.callGraphSizePenaltyMultiplier(node);

            // Increase the threshold if the root graph is very large.
            int rootSize = Math.max(0, root.getReadonlySubgraph().getNodeCount() - rootSizePenaltyTypicalGraphSize(options));
            benefitThreshold += rootSize * getRootSizePenaltyCoefficientValue(options) * tuning.rootSizePenaltyMultiplier(node);

            // Increase the threshold if the explored IR size is much larger than the root IR size.
            final int exploredIr = root.getSubtreeTotalCompilerNodeCount();
            final int inlinedIr = root.getReadonlySubgraph().getNodeCount();
            if (exploredIr > 2 * inlinedIr && exploredIr > LARGE_EXPLORED_IR_CHECK_THRESHOLD) {
                benefitThreshold += SmallRootIrPenaltyCoefficient.getValue(options) * exploredIr / inlinedIr * tuning.smallRootIrPenaltyMultiplier(node);
            }

            // Increase the threshold if the root has a lot of children.
            final int childrenCount = root.children().size();
            if (childrenCount > CHILDREN_COUNT_CHECK_THRESHOLD) {
                benefitThreshold += LargeChildrenCountPenaltyCoefficient.getValue(options) * (childrenCount - CHILDREN_COUNT_CHECK_THRESHOLD) * tuning.largeChildrenCountPenaltyMultiplier(node);
            }

            return relativeBenefit > benefitThreshold;
        }

        protected int rootSizePenaltyTypicalGraphSize(OptionValues options) {
            return RootSizePenaltyTypicalGraphSize.getValue(options);
        }

        @Override
        public int typicalGraphSize(OptionValues options) {
            return TypicalGraphSize.getValue(options);
        }

        @Override
        public int typicalGraphSizeInvokeBonus(OptionValues options) {
            return TypicalGraphSizeInvokeBonus.getValue(options);
        }

        @Override
        public int expansionInertiaBaseValue(OptionValues options) {
            return ExpansionInertiaBaseValue.getValue(options);
        }

        @Override
        public int expansionInertiaInvokeBonus(OptionValues options) {
            return ExpansionInertiaInvokeBonus.getValue(options);
        }

        protected int getTypicalCallGraphSizeValue(OptionValues options) {
            return TypicalCallGraphSize.getValue(options);
        }

        protected double getCallGraphSizePenaltyCoefficientValue(OptionValues options) {
            return CallGraphSizePenaltyCoefficient.getValue(options);
        }

        protected double getRootSizePenaltyCoefficientValue(OptionValues options) {
            return RootSizePenaltyCoefficient.getValue(options);
        }

        @Override
        public boolean shouldBeIndirect(CutoffNode node) {
            final TuningPolicy tuning = node.callTree().tuningPolicy();
            return node.getFrequency() < InliningMath.defaultMinimumFrequencyForExpansion() * tuning.defaultMinimumFrequencyForExpansionMultiplier(node);
        }

        @Override
        public boolean isExpandedOften(CallTreeNode node) {
            return node.callTree().methodHistogram().get(node.invoke().getTargetMethod(), 0) > InliningMath.defaultExpansionLimitPerRecursiveMethod();
        }

        @Override
        public void updateParentNodeLocalBenefit(ParentNode node) {
            double amplifier = InliningMath.defaultBenefitAmplifier(node);
            amplifier *= node.callTree().tuningPolicy().parentLocalBenefitAmplifier(node);
            node.setLocalBenefit(node.getFrequency() * amplifier);
        }

        @Override
        public void updateParentNodePriority(ParentNode node) {
            OptionValues options = node.getOptions();
            double maxLeafPriority = NODE_LOWEST_PRIORITY;
            double sizePenalty = 0.0;
            for (CallTreeNode child : node.children()) {
                maxLeafPriority = Math.max(maxLeafPriority, child.getMaxLeafPriority());
                sizePenalty += child.getSubtreeTotalCutoffCodeSize() * CutoffCodeSizePenaltyCoefficient.getValue(options);
                sizePenalty += child.getSubtreeTotalCompilerNodeCount() * CompilerNodePenaltyCoefficient.getValue(options);
            }

            double penalty = sizePenalty;

            node.setPriority(maxLeafPriority - penalty);
            node.setMaxLeafPriority(maxLeafPriority);
        }

        @Override
        public void updateCutoffNodeLocalBenefit(CutoffNode node) {
            double frequency = node.getFrequency();
            if (node.parent() instanceof InlineCacheNode) {
                frequency = node.parent().getFrequency();
            }
            double amplifier = InliningMath.defaultBenefitAmplifier(node);
            amplifier *= node.callTree().tuningPolicy().cutoffLocalBenefitAmplifier(node);
            node.setLocalBenefit(frequency * amplifier);
        }

        @Override
        public void updateCutoffNodePriority(CutoffNode node) {
            double localBenefitBonus = node.getLocalBenefit();

            if (node.getRecursionDepth() > InliningMath.defaultMaximumRecursionDepth()) {
                node.setLowestPriority();
                return;
            }

            double lockBonus = InliningMath.defaultLockBonus(node);
            double recursionPenalty = InliningMath.defaultRecursionPenalty(node);
            double estimatedBenefit = localBenefitBonus - recursionPenalty + lockBonus;
            double estimatedCost = node.getCostEstimate();

            node.setPriorityAndMaxLeafPriority(estimatedBenefit / estimatedCost);
        }
    }

}
