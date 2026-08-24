/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases.priorityinline;

import static com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisCallTreeState.getIPEACallTreeState;
import static com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase.Options.IPEACutoffMaterializationWeight;
import static com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase.Options.IPEAMaterializationBoostConstant;
import static com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase.Options.IPEAVirtualEscapeBoostSingle;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult.Materialization;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult.VirtualCutoffEscapee;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.CallerContext;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.VirtualInfo;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;

public class InterproceduralPartialEscapeAnalysisUtil {

    private static final double VIRTUAL_ARG_WEIGHT = 1;
    private static final double VIRTUAL_RETURN_WEIGHT = 1;
    private static final double VIRTUAL_ESCAPE_BOOST_GROWTH = 0.5;
    private static final int MAX_RECURSION_CUTOFF_MATERIALIZATION = 3;

    static AnalysisResult runOnFullTree(CallTree callTree, CoreProviders coreProviders) {
        StructuredGraph compilerGraph = callTree.root().getReadonlySubgraph();
        InterproceduralPartialEscapeAnalysisPhase partialEscapeAnalysisPhase = new InterproceduralPartialEscapeAnalysisPhase(callTree.getCanonicalizer(), compilerGraph.getOptions());
        return partialEscapeAnalysisPhase.runFromRoot(callTree, compilerGraph, coreProviders).analysisResult();
    }

    static AnalysisResult runOnSubtree(CallTreeNode callTreeNode, CallerContext callerContext, CoreProviders coreProviders) {
        CallTree callTree = callTreeNode.callTree();
        if (callTreeNode.isRoot()) {
            return runOnFullTree(callTree, coreProviders);
        }
        OptionValues optionValues = callerContext.graph().getOptions();
        InterproceduralPartialEscapeAnalysisPhase partialEscapeAnalysisPhase = new InterproceduralPartialEscapeAnalysisPhase(callerContext.canonicalizer, optionValues);
        return partialEscapeAnalysisPhase.runFromCallerContext(callerContext, coreProviders).analysisResult();
    }

    static AnalysisResult afterExpandingCutoffNode(CallTreeNode replacementNode, CallTreeNode replacedNode, CoreProviders coreProviders, AnalysisResult analysisResult) {
        CallerContext parentCallerContext = analysisResult.callTreeNodeToCallerContext().get(replacementNode.parent());
        analysisResult.cutoffEscapees().removeKey(replacedNode);
        if (parentCallerContext != null && !parentCallerContext.isRootContext()) {
            analysisResult.clearSubtreeResults(replacementNode.parent());
            /*
             * We need to run from parent of expanded node, as expansion may have an effect on
             * materializations in parent.
             */
            return InterproceduralPartialEscapeAnalysisUtil.runOnSubtree(replacementNode.parent(), parentCallerContext, coreProviders);
        } else {
            return InterproceduralPartialEscapeAnalysisUtil.runOnFullTree(replacementNode.callTree(), coreProviders);
        }
    }

    /* TODO: can be optimized (GR-39609) */
    static void afterExpansionPhase(CallTree callTree, AnalysisResult analysisResult) {

        InterproceduralPartialEscapeAnalysisCallTreeState state = getIPEACallTreeState(callTree);
        DeadlockWatchdog watchdog = DeadlockWatchdog.singleton();

        callTree.root().preOrderTraverse(callTreeNode -> {
            watchdog.recordActivity();
            if (analysisResult.allocations().get(callTreeNode) == null) {
                return;
            }
            for (VirtualizableAllocation allocation : analysisResult.allocations().get(callTreeNode)) {
                CallerContext currentCallerContext = analysisResult.callTreeNodeToCallerContext().get(callTreeNode);

                Set<CallTreeNode> virtuallyInjectedCallTreeNodes = new HashSet<>();

                double thisMatFrequencySum = subtreeMaterializationFrequency(callTreeNode, allocation, analysisResult, virtuallyInjectedCallTreeNodes, watchdog);

                double parentMatFrequencySum = 0.0;

                if (!callTreeNode.isRoot()) {
                    /*
                     * Case where the virtually allocated object in this CallTreeNode escapes to
                     * parent node. Count Frequency of materializations in parent as well.
                     */
                    Invoke currentInvoke = currentCallerContext.callTarget.invoke();
                    VirtualInfo virtualInfo = currentCallerContext.getVirtualReturnObject(currentInvoke);
                    if (virtualInfo != null && virtualInfo.virtual != null && analysisResult.virtualObjectAllocationMap().get(virtualInfo.virtual) == allocation) {
                        virtuallyInjectedCallTreeNodes.add(callTreeNode);
                        parentMatFrequencySum = subtreeMaterializationFrequency(callTreeNode.parent(), allocation, analysisResult, virtuallyInjectedCallTreeNodes, watchdog);
                    }
                    /*
                     * Compute relative frequency under assumption every materialization in parent
                     * is dominated by invoke returning the object. Assumption must hold as the
                     * AllocationNode is in current.
                     */
                    double currInvokeFrequency = currentCallerContext.getCallTreeNode().getFrequency();
                    parentMatFrequencySum = Math.max(0.0D, Math.min(1.0D, parentMatFrequencySum / currInvokeFrequency));
                }
                double totalMatFrequency = Math.max(0.0D, Math.min(1.0D, thisMatFrequencySum + parentMatFrequencySum));

                if (SubstratePriorityInliningPhase.Options.TrackIPEAStatistics.getValue(callTree.getOptions()).shouldTrack()) {
                    SubstratePriorityInliningPhase.IPEAStatistics.enter(callTree, allocation, virtuallyInjectedCallTreeNodes.size(), totalMatFrequency, boostFunction(totalMatFrequency));
                }

                /*
                 * Compute boosts for each CallTreeNode, put into boostFunctionCache to use for
                 * intermediate rounds.
                 */
                for (CallTreeNode injectedCallTreeNode : virtuallyInjectedCallTreeNodes) {
                    state.setCachedLocalBenefitBoost(injectedCallTreeNode, Math.max(1.0D, boostFunction(totalMatFrequency)));
                }

            }
        });
    }

    /* TODO: can be optimized (GR-39609) */
    private static double subtreeMaterializationFrequency(CallTreeNode current, VirtualizableAllocation allocation, AnalysisResult analysisResults, Set<CallTreeNode> virtuallyInjectedCallTreeNodes,
                    DeadlockWatchdog watchdog) {
        watchdog.recordActivity();
        EconomicMap<VirtualizableAllocation, EconomicSet<Materialization>> callTreeNodeMats = analysisResults.materializations().get(current);
        double totalMatFrequency = 0.0;
        if (callTreeNodeMats == null) {
            return 0.0D; // No Materializations in this CallTreeNode.
        }
        EconomicSet<Materialization> materializations = callTreeNodeMats.get(allocation);
        if (materializations == null) {
            return 0.0D; // No Materializations in this CallTreeNode.
        }
        for (Materialization mat : materializations) {
            watchdog.recordActivity();
            FixedNode materializedBefore = mat.materializedBefore;
            if (materializedBefore instanceof Invoke) {
                CallTreeNode invokeCallTreeNode = analysisResults.callTreeNodeForInvokeInCopiedGraph((Invoke) materializedBefore, current);
                if (invokeCallTreeNode instanceof SubgraphNode) {
                    virtuallyInjectedCallTreeNodes.add(invokeCallTreeNode);
                    /*
                     * Remark: Here we potentially over-approximate frequency of materializations in
                     * the case of passing the same allocation to multiple SubgraphNode Invokes.
                     */
                    totalMatFrequency += mat.localFrequency * subtreeMaterializationFrequency(invokeCallTreeNode, allocation, analysisResults, virtuallyInjectedCallTreeNodes, watchdog);
                } else if (invokeCallTreeNode instanceof CutoffNode) {
                    totalMatFrequency += mat.localFrequency * cutoffMaterializationFunction((CutoffNode) invokeCallTreeNode);
                } else {
                    totalMatFrequency += mat.localFrequency;
                }
            } else {
                totalMatFrequency += mat.localFrequency;
            }
        }
        return Math.min(1.0D, totalMatFrequency);
    }

    private static double cutoffMaterializationFunction(CutoffNode cutoffNode) {
        double slope = (1.0D - IPEACutoffMaterializationWeight.getValue()) * MAX_RECURSION_CUTOFF_MATERIALIZATION;
        return (slope * cutoffNode.getRecursionDepth() + IPEACutoffMaterializationWeight.getValue());
    }

    private static double boostFunction(double x) {
        if (x <= 0) {
            return IPEAMaterializationBoostConstant.getValue();
        } else if (x >= 1) {
            return 1.0D;
        } else {
            return IPEAMaterializationBoostConstant.getValue() / (Math.pow(IPEAMaterializationBoostConstant.getValue(), x));
        }
    }

    // @formatter:off
    /*
     * escapingCutoffBonus := (#escapees - 1.0)^ VIRTUAL_ESCAPE_BOOST_GROWTH + VirtualEscapeBoostSingle
     *
     * #escapees := #virtualArgs * VIRTUAL_ARG_WEIGHT + #virtualReturnCandidate * VIRTUAL_RETURN_WEIGHT
     */
    // @formatter:on
    static double escapingObjectCutoffBonus(CutoffNode node, AnalysisResult analysisResults) {
        if (analysisResults == null || analysisResults.cutoffEscapees().get(node) == null) {
            return 1.0;
        }
        double cutoffEscape = 0D;
        for (VirtualCutoffEscapee escapee : analysisResults.cutoffEscapees().get(node)) {
            cutoffEscape += escapee.isVirtualArgument() ? VIRTUAL_ARG_WEIGHT : VIRTUAL_RETURN_WEIGHT;
        }
        return virtualArgsAmplifier(cutoffEscape);
    }

    static double virtualArgsAmplifier(double n) {
        if (n < 1.0) {
            return 1.0;
        } else {
            return Math.pow(n - 1.0, VIRTUAL_ESCAPE_BOOST_GROWTH) + IPEAVirtualEscapeBoostSingle.getValue();
        }
    }
}
