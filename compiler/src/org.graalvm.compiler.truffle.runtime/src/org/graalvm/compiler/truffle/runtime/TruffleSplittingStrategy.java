/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExperimentalSplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplitting;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingGrowthLimit;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleTraceSplittingSummary;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExperimentalSplitting;

final class TruffleSplittingStrategy {

    private static Set<OptimizedCallTarget> waste = new HashSet<>();
    private static SplitStatisticsReporter reporter = new SplitStatisticsReporter();

    static void beforeCall(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            final GraalTVMCI.EngineData engineData = getEngineData(call, tvmci);
            reporter.engineDataSet.add(engineData);
            if (call.getCurrentCallTarget().getCompilationProfile().getInterpreterCallCount() == 0) {
                reporter.totalExecutedNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            }
        }
        if (TruffleCompilerOptions.getValue(TruffleExperimentalSplitting)) {
            if (polymorphicSpecializationBasedShouldSplit(call, tvmci)) {
                final GraalTVMCI.EngineData engineData = tvmci.getEngineData(call.getRootNode());
                engineData.splitCount += call.getCallTarget().getUninitializedNodeCount();
                doSplit(call);
            }
            return;
        }
        if (call.getCallCount() == 2) {
            final GraalTVMCI.EngineData engineData = getEngineData(call, tvmci);
            if (shouldSplit(call, engineData)) {
                engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
                doSplit(call);
            }
        }
    }

    private static GraalTVMCI.EngineData getEngineData(OptimizedDirectCallNode callNode, GraalTVMCI tvmci) {
        return tvmci.getEngineData(callNode.getCallTarget().getRootNode());
    }

    private static void doSplit(OptimizedDirectCallNode call) {
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            calculateSplitWasteImpl(call.getCurrentCallTarget());
        }
        call.split();
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            reporter.splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            reporter.splitCount++;
            reporter.splitTargets.put(call.getCallTarget(), reporter.splitTargets.getOrDefault(call.getCallTarget(), 0) + 1);
        }
    }

    private static boolean polymorphicSpecializationBasedShouldSplit(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        OptimizedCallTarget callTarget = call.getCurrentCallTarget();
        if (!callTarget.isNeedsSplit()) {
            return false;
        }
        final GraalTVMCI.EngineData engineData = getEngineData(call, tvmci);
        if (!canSplit(call) || isRecursiveSplit(call) || engineData.splitCount + call.getCallTarget().getUninitializedNodeCount() >= engineData.splitLimit) {
            return false;
        }
        if (callTarget.getUninitializedNodeCount() > TruffleCompilerOptions.getValue(TruffleSplittingMaxCalleeSize)) {
            return false;
        }
        return true;
    }

    static void forceSplitting(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        if (!TruffleCompilerOptions.getValue(TruffleExperimentalSplitting) || TruffleCompilerOptions.getValue(TruffleExperimentalSplittingAllowForcedSplits)) {
            if (!canSplit(call) || isRecursiveSplit(call)) {
                return;
            }
            final GraalTVMCI.EngineData engineData = getEngineData(call, tvmci);
            engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            doSplit(call);
            if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
                reporter.forcedSplitCount++;
            }
        }
    }

    private static boolean canSplit(OptimizedDirectCallNode call) {
        if (call.isCallTargetCloned()) {
            return false;
        }
        if (!TruffleCompilerOptions.getValue(TruffleSplitting)) {
            return false;
        }
        if (!call.isCallTargetCloningAllowed()) {
            return false;
        }
        return true;
    }

    private static boolean shouldSplit(OptimizedDirectCallNode call, GraalTVMCI.EngineData engineData) {
        if (engineData.splitCount + call.getCurrentCallTarget().getUninitializedNodeCount() > engineData.splitLimit) {
            return false;
        }
        if (!canSplit(call)) {
            return false;
        }

        OptimizedCallTarget callTarget = call.getCallTarget();
        int nodeCount = callTarget.getUninitializedNodeCount();
        if (nodeCount > TruffleCompilerOptions.getValue(TruffleSplittingMaxCalleeSize)) {
            return false;
        }

        RootNode rootNode = call.getRootNode();
        if (rootNode == null) {
            return false;
        }
        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) rootNode.getCallTarget();
        if (root == callTarget || root.getSourceCallTarget() == callTarget) {
            // recursive call found
            return false;
        }

        // Disable splitting if it will cause a deep split-only recursion
        if (isRecursiveSplit(call)) {
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall(call)) {
            return true;
        }
        return countPolymorphic(call) >= 1;
    }

    private static boolean isRecursiveSplit(OptimizedDirectCallNode call) {
        final OptimizedCallTarget splitCandidateTarget = call.getCallTarget();
        final RootNode rootNode = call.getRootNode();
        if (rootNode == null) {
            return false;
        }
        OptimizedCallTarget callRootTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        OptimizedCallTarget callSourceTarget = callRootTarget.getSourceCallTarget();
        int depth = 0;
        while (callSourceTarget != null) {
            if (callSourceTarget == splitCandidateTarget) {
                depth++;
                if (depth == 2) {
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

    static void newTargetCreated(GraalTVMCI tvmci, RootCallTarget target) {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) target;
        if (TruffleCompilerOptions.getValue(TruffleSplitting)) {
            final GraalTVMCI.EngineData engineData = tvmci.getEngineData(target.getRootNode());
            final int newLimit = (int) (engineData.splitLimit + TruffleCompilerOptions.getValue(TruffleSplittingGrowthLimit) * callTarget.getUninitializedNodeCount());
            engineData.splitLimit = Math.min(newLimit, TruffleCompilerOptions.getValue(TruffleSplittingMaxNumberOfSplitNodes));
        }
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            reporter.totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
        }
    }

    private static void calculateSplitWasteImpl(OptimizedCallTarget callTarget) {
        final List<OptimizedDirectCallNode> callNodes = NodeUtil.findAllNodeInstances(callTarget.getRootNode(), OptimizedDirectCallNode.class);
        callNodes.removeIf(callNode -> !callNode.isCallTargetCloned());
        for (OptimizedDirectCallNode node : callNodes) {
            final OptimizedCallTarget clonedCallTarget = node.getClonedCallTarget();
            if (waste.add(clonedCallTarget)) {
                reporter.wastedTargetCount++;
                reporter.wastedNodeCount += clonedCallTarget.getUninitializedNodeCount();
                calculateSplitWasteImpl(clonedCallTarget);
            }
        }
    }

    static void newPolymorphicSpecialize(Node node) {
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            final Map<Class<? extends Node>, Integer> polymorphicNodes = reporter.polymorphicNodes;
            final Class<? extends Node> aClass = node.getClass();
            polymorphicNodes.put(aClass, polymorphicNodes.getOrDefault(aClass, 0) + 1);
        }
    }

    static void newDirectCallNodeCreated(OptimizedDirectCallNode directCallNode) {
        if (TruffleCompilerOptions.getValue(TruffleExperimentalSplitting)) {
            final OptimizedCallTarget callTarget = directCallNode.getCallTarget();
            callTarget.addKnownCallNode(directCallNode);
        }
    }

    static class SplitStatisticsReporter extends Thread {
        final Set<GraalTVMCI.EngineData> engineDataSet = new HashSet<>();
        final Map<Class<? extends Node>, Integer> polymorphicNodes = new HashMap<>();
        final Map<OptimizedCallTarget, Integer> splitTargets = new HashMap<>();
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

        SplitStatisticsReporter() {
            if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
                Runtime.getRuntime().addShutdownHook(this);
            }
        }

        @Override
        public void run() {
            for (GraalTVMCI.EngineData engineData : engineDataSet) {
                TTY.out().println(String.format(D_FORMAT, "Split count", engineData.splitCount));
                TTY.out().println(String.format(D_FORMAT, "Split limit", engineData.splitLimit));
            }
            TTY.out().println(String.format(D_FORMAT, "Splits", splitCount));
            TTY.out().println(String.format(D_FORMAT, "Forced splits", forcedSplitCount));
            TTY.out().println(String.format(D_FORMAT, "Nodes created through splitting", splitNodeCount));
            TTY.out().println(String.format(D_FORMAT, "Nodes created without splitting", totalCreatedNodeCount));
            TTY.out().println(String.format(P_FORMAT, "Increase in nodes", (splitNodeCount * 100.0) / (totalCreatedNodeCount)));
            TTY.out().println(String.format(D_FORMAT, "Split nodes wasted", wastedNodeCount));
            TTY.out().println(String.format(P_FORMAT, "Percent of split nodes wasted", (wastedNodeCount * 100.0) / (splitNodeCount)));
            TTY.out().println(String.format(D_FORMAT, "Targets wasted due to splitting", wastedTargetCount));
            TTY.out().println(String.format(D_FORMAT, "Total nodes executed", totalExecutedNodeCount));

            TTY.out().println(String.format(DELIMITER_FORMAT, "SPLIT TARGETS"));
            for (Map.Entry<OptimizedCallTarget, Integer> entry : splitTargets.entrySet()) {
                TTY.out().println(String.format(D_FORMAT, entry.getKey(), entry.getValue()));
            }

            TTY.out().println(String.format(DELIMITER_FORMAT, "NODES"));
            for (Map.Entry<Class<? extends Node>, Integer> entry : polymorphicNodes.entrySet()) {
                TTY.out().println(String.format(D_LONG_FORMAT, entry.getKey(), entry.getValue()));
            }
        }
    }

}
