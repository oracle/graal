/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.getOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;
import com.oracle.truffle.api.nodes.RootNode;

final class TruffleSplittingStrategy {

    private static Set<OptimizedCallTarget> waste = new HashSet<>();
    private static final int LEGACY_RECURSIVE_SPLIT_DEPTH = 2;
    private static final int RECURSIVE_SPLIT_DEPTH = 3;

    static void beforeCall(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        final EngineData engineData = call.getCurrentCallTarget().engineData;
        if (engineData.options.isTraceSplittingSummary()) {
            if (call.getCurrentCallTarget().getCompilationProfile().getCallCount() == 0) {
                engineData.reporter.totalExecutedNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            }
        }
        if (engineData.options.isLegacySplitting()) {
            if (call.getCallCount() == 2) {
                if (legacyShouldSplit(call, engineData)) {
                    engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
                    doSplit(engineData, call);
                }
            }
            return;
        }
        if (shouldSplit(engineData.options, call, tvmci)) {
            engineData.splitCount += call.getCallTarget().getUninitializedNodeCount();
            doSplit(engineData, call);
        }
    }

    private static EngineData getEngineData(OptimizedDirectCallNode callNode, GraalTVMCI tvmci) {
        return tvmci.getEngineData(callNode.getCallTarget().getRootNode());
    }

    private static void doSplit(EngineData engineData, OptimizedDirectCallNode call) {
        final RuntimeOptionsCache options = engineData.options;
        if (options.isTraceSplittingSummary()) {
            calculateSplitWasteImpl(call.getCurrentCallTarget());
        }
        call.split();
        if (options.isTraceSplittingSummary()) {
            engineData.reporter.splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            engineData.reporter.splitCount++;
            engineData.reporter.splitTargets.put(call.getCallTarget(), engineData.reporter.splitTargets.getOrDefault(call.getCallTarget(), 0) + 1);
        }
    }

    private static boolean shouldSplit(RuntimeOptionsCache options, OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        OptimizedCallTarget callTarget = call.getCurrentCallTarget();
        if (!callTarget.isNeedsSplit()) {
            return false;
        }
        final EngineData engineData = getEngineData(call, tvmci);
        if (!canSplit(options, call) || isRecursiveSplit(call, RECURSIVE_SPLIT_DEPTH) ||
                        engineData.splitCount + call.getCallTarget().getUninitializedNodeCount() >= engineData.splitLimit) {
            return false;
        }
        if (callTarget.getUninitializedNodeCount() > options.getSplittingMaxCalleeSize()) {
            return false;
        }
        return true;
    }

    static void forceSplitting(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        final EngineData engineData = getEngineData(call, tvmci);
        final RuntimeOptionsCache options = engineData.options;
        if (options.isLegacySplitting() || options.isSplittingAllowForcedSplits()) {
            if (!canSplit(options, call) || isRecursiveSplit(call, LEGACY_RECURSIVE_SPLIT_DEPTH)) {
                return;
            }
            engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            doSplit(engineData, call);
            if (options.isTraceSplittingSummary()) {
                engineData.reporter.forcedSplitCount++;
            }
        }
    }

    private static boolean canSplit(RuntimeOptionsCache options, OptimizedDirectCallNode call) {
        if (call.isCallTargetCloned()) {
            return false;
        }
        if (!options.isSplitting()) {
            return false;
        }
        if (!call.isCallTargetCloningAllowed()) {
            return false;
        }
        return true;
    }

    private static boolean legacyShouldSplit(OptimizedDirectCallNode call, EngineData engineData) {
        // In general, splitting in multi-tier compilations could be useful,
        // but enabling splitting as-is currently overflows the compiler with too many requests.
        if (SharedTruffleRuntimeOptions.TruffleMultiTier.getValue(getOptions())) {
            return false;
        }

        if (engineData.splitCount + call.getCurrentCallTarget().getUninitializedNodeCount() > engineData.splitLimit) {
            return false;
        }
        if (!canSplit(engineData.options, call)) {
            return false;
        }

        OptimizedCallTarget callTarget = call.getCallTarget();
        int nodeCount = callTarget.getUninitializedNodeCount();
        if (nodeCount > engineData.options.getSplittingMaxCalleeSize()) {
            return false;
        }

        RootNode rootNode = call.getRootNode();
        if (rootNode == null) {
            return false;
        }
        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) rootNode.getCallTarget();
        if (root == callTarget || (root != null && root.getSourceCallTarget() == callTarget)) {
            // recursive call found
            return false;
        }

        // Disable splitting if it will cause a deep split-only recursion
        if (isRecursiveSplit(call, LEGACY_RECURSIVE_SPLIT_DEPTH)) {
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall(call)) {
            return true;
        }
        return countPolymorphic(call) >= 1;
    }

    private static boolean isRecursiveSplit(OptimizedDirectCallNode call, int allowedDepth) {
        final OptimizedCallTarget splitCandidateTarget = call.getCallTarget();
        final RootNode rootNode = call.getRootNode();
        if (rootNode == null) {
            return false;
        }
        OptimizedCallTarget callRootTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        if (callRootTarget == null) {
            return false;
        }
        OptimizedCallTarget callSourceTarget = callRootTarget.getSourceCallTarget();
        int depth = 0;
        while (callSourceTarget != null) {
            if (callSourceTarget == splitCandidateTarget) {
                depth++;
                if (depth == allowedDepth) {
                    return true;
                }
            }
            final OptimizedDirectCallNode splitCallSite = callRootTarget.getCallSiteForSplit();
            if (splitCallSite == null) {
                break;
            }
            final RootNode splitCallSiteRootNode = splitCallSite.getRootNode();
            if (splitCallSiteRootNode == null) {
                break;
            }
            callRootTarget = (OptimizedCallTarget) splitCallSiteRootNode.getCallTarget();
            if (callRootTarget == null) {
                break;
            }
            callSourceTarget = callRootTarget.getSourceCallTarget();
        }
        return false;
    }

    private static boolean isMaxSingleCall(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCallTarget().getRootNode(), new NodeCountFilter() {
            @Override
            public boolean isCounted(Node node) {
                return node instanceof DirectCallNode;
            }
        }) <= 1;
    }

    private static int countPolymorphic(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCallTarget().getRootNode(), new NodeCountFilter() {
            @Override
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                return polymorphic;
            }
        });
    }

    static void newTargetCreated(RootCallTarget target) {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) target;
        final EngineData engineData = callTarget.engineData;
        final RuntimeOptionsCache runtimeOptionsCache = engineData.options;
        if (runtimeOptionsCache.isSplitting()) {
            final int newLimit = (int) (engineData.splitLimit + runtimeOptionsCache.getSplittingGrowthLimit() * callTarget.getUninitializedNodeCount());
            engineData.splitLimit = Math.min(newLimit, runtimeOptionsCache.getSplittingMaxNumberOfSplitNodes());
        }
        if (runtimeOptionsCache.isTraceSplittingSummary()) {
            engineData.reporter.totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
        }
    }

    private static void calculateSplitWasteImpl(OptimizedCallTarget callTarget) {
        final List<OptimizedDirectCallNode> callNodes = NodeUtil.findAllNodeInstances(callTarget.getRootNode(), OptimizedDirectCallNode.class);
        callNodes.removeIf(callNode -> !callNode.isCallTargetCloned());
        for (OptimizedDirectCallNode node : callNodes) {
            final OptimizedCallTarget clonedCallTarget = node.getClonedCallTarget();
            if (waste.add(clonedCallTarget)) {
                final EngineData engineData = clonedCallTarget.engineData;
                engineData.reporter.wastedTargetCount++;
                engineData.reporter.wastedNodeCount += clonedCallTarget.getUninitializedNodeCount();
                calculateSplitWasteImpl(clonedCallTarget);
            }
        }
    }

    static void newPolymorphicSpecialize(Node node, EngineData engineData) {
        if (engineData.options.isTraceSplittingSummary()) {
            final Map<Class<? extends Node>, Integer> polymorphicNodes = engineData.reporter.polymorphicNodes;
            final Class<? extends Node> aClass = node.getClass();
            polymorphicNodes.put(aClass, polymorphicNodes.getOrDefault(aClass, 0) + 1);
        }
    }

    static void newDirectCallNodeCreated(OptimizedDirectCallNode directCallNode) {
        final OptimizedCallTarget callTarget = directCallNode.getCallTarget();
        if (callTarget.engineData.options.isLegacySplitting()) {
            return;
        }
        callTarget.addKnownCallNode(directCallNode);
    }

    static class SplitStatisticsReporter extends Thread {
        final Map<Class<? extends Node>, Integer> polymorphicNodes = new HashMap<>();
        final Map<OptimizedCallTarget, Integer> splitTargets = new HashMap<>();
        private final EngineData engineData;
        int splitCount;
        int forcedSplitCount;
        int splitNodeCount;
        int totalExecutedNodeCount;
        int totalCreatedNodeCount;
        int wastedNodeCount;
        int wastedTargetCount;

        static final String D_FORMAT = "[truffle] %-40s: %10d";
        static final String D_LONG_FORMAT = "[truffle] %-120s: %10d";
        static final String P_FORMAT = "[truffle] %-40s: %9.2f%%";
        static final String DELIMITER_FORMAT = "%n[truffle] --- %s";

        SplitStatisticsReporter(EngineData engineData) {
            this.engineData = engineData;
            if (TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary)) {
                Runtime.getRuntime().addShutdownHook(this);
            }
        }

        @Override
        public void run() {
            final GraalTruffleRuntime rt = GraalTruffleRuntime.getRuntime();
            rt.log(String.format(D_FORMAT, "Split count", engineData.splitCount));
            rt.log(String.format(D_FORMAT, "Split limit", engineData.splitLimit));
            rt.log(String.format(D_FORMAT, "Splits", splitCount));
            rt.log(String.format(D_FORMAT, "Forced splits", forcedSplitCount));
            rt.log(String.format(D_FORMAT, "Nodes created through splitting", splitNodeCount));
            rt.log(String.format(D_FORMAT, "Nodes created without splitting", totalCreatedNodeCount));
            rt.log(String.format(P_FORMAT, "Increase in nodes", (splitNodeCount * 100.0) / (totalCreatedNodeCount)));
            rt.log(String.format(D_FORMAT, "Split nodes wasted", wastedNodeCount));
            rt.log(String.format(P_FORMAT, "Percent of split nodes wasted", (wastedNodeCount * 100.0) / (splitNodeCount)));
            rt.log(String.format(D_FORMAT, "Targets wasted due to splitting", wastedTargetCount));
            rt.log(String.format(D_FORMAT, "Total nodes executed", totalExecutedNodeCount));

            rt.log(String.format(DELIMITER_FORMAT, "SPLIT TARGETS"));
            for (Map.Entry<OptimizedCallTarget, Integer> entry : splitTargets.entrySet()) {
                rt.log(String.format(D_FORMAT, entry.getKey(), entry.getValue()));
            }

            rt.log(String.format(DELIMITER_FORMAT, "NODES"));
            for (Map.Entry<Class<? extends Node>, Integer> entry : polymorphicNodes.entrySet()) {
                rt.log(String.format(D_LONG_FORMAT, entry.getKey(), entry.getValue()));
            }
        }
    }

}
