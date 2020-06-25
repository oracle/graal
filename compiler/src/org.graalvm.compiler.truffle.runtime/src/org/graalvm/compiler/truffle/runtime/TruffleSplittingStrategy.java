/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

final class TruffleSplittingStrategy {

    private static final Set<OptimizedCallTarget> waste = Collections.synchronizedSet(new HashSet<>());
    private static final int RECURSIVE_SPLIT_DEPTH = 3;

    static void beforeCall(OptimizedDirectCallNode call, OptimizedCallTarget currentTarget) {
        final EngineData engineData = currentTarget.engine;
        if (engineData.traceSplittingSummary) {
            if (currentTarget.getCallCount() == 0) {
                synchronized (engineData.splittingStatistics) {
                    engineData.splittingStatistics.totalExecutedNodeCount += currentTarget.getUninitializedNodeCount();
                }
            }
        }
        if (shouldSplit(engineData, call)) {
            engineData.splitCount += call.getCallTarget().getUninitializedNodeCount();
            doSplit(engineData, call);
        }
    }

    private static void doSplit(EngineData engineData, OptimizedDirectCallNode call) {
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.splittingStatistics) {
                calculateSplitWasteImpl(call.getCurrentCallTarget());
            }
        }
        call.split();
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.splittingStatistics) {
                engineData.splittingStatistics.splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
                engineData.splittingStatistics.splitCount++;
                engineData.splittingStatistics.splitTargets.put(call.getCallTarget(), engineData.splittingStatistics.splitTargets.getOrDefault(call.getCallTarget(), 0) + 1);
            }
        }
    }

    private static boolean shouldSplit(EngineData engine, OptimizedDirectCallNode call) {
        OptimizedCallTarget callTarget = call.getCurrentCallTarget();
        if (!callTarget.isNeedsSplit()) {
            return false;
        }
        if (!canSplit(engine, call)) {
            maybeTraceFail(engine, call, TruffleSplittingStrategy::splitNotPossibleMessageFactory);
            return false;
        }
        if (isRecursiveSplit(call, RECURSIVE_SPLIT_DEPTH)) {
            maybeTraceFail(engine, call, TruffleSplittingStrategy::recursiveSplitMessageFactory);
            return false;
        }
        if (engine.splitCount + call.getCallTarget().getUninitializedNodeCount() >= engine.splitLimit) {
            maybeTraceFail(engine, call, TruffleSplittingStrategy::notEnoughBudgetMessageFactory);
            return false;
        }
        if (callTarget.getUninitializedNodeCount() > engine.splittingMaxCalleeSize) {
            maybeTraceFail(engine, call, TruffleSplittingStrategy::targetTooBigMessageFactory);
            return false;
        }
        return true;
    }

    private static String targetTooBigMessageFactory(OptimizedDirectCallNode call, EngineData engine) {
        return "Target too big: " + call.getCallTarget().getUninitializedNodeCount() + " > " + engine.splittingMaxCalleeSize;
    }

    private static String notEnoughBudgetMessageFactory(OptimizedDirectCallNode call, EngineData engine) {
        return "Not enough budget. " + (engine.splitCount + call.getCallTarget().getUninitializedNodeCount()) + " > " + engine.splitLimit;
    }

    @SuppressWarnings("unused")
    private static String splitNotPossibleMessageFactory(OptimizedDirectCallNode node, EngineData data) {
        return "Split not possible.";
    }

    @SuppressWarnings("unused")
    private static String recursiveSplitMessageFactory(OptimizedDirectCallNode node, EngineData data) {
        return "Recursive split.";
    }

    private static void maybeTraceFail(EngineData engine, OptimizedDirectCallNode call, BiFunction<OptimizedDirectCallNode, EngineData, String> messageFactory) {
        if (engine.traceSplits) {
            GraalTruffleRuntime.getRuntime().getListener().onCompilationSplitFailed(call, messageFactory.apply(call, engine));
        }
    }

    static void forceSplitting(OptimizedDirectCallNode call) {
        final EngineData engineData = call.getCallTarget().engine;
        if (engineData.splittingAllowForcedSplits) {
            if (!canSplit(engineData, call) || isRecursiveSplit(call, RECURSIVE_SPLIT_DEPTH)) {
                return;
            }
            engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            doSplit(engineData, call);
            if (engineData.traceSplittingSummary) {
                synchronized (engineData.splittingStatistics) {
                    engineData.splittingStatistics.forcedSplitCount++;
                }
            }
        }
    }

    private static boolean canSplit(EngineData engine, OptimizedDirectCallNode call) {
        if (call.isCallTargetCloned()) {
            return false;
        }
        if (!engine.splitting) {
            return false;
        }
        if (!call.isCallTargetCloningAllowed()) {
            return false;
        }
        return true;
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

    static void newTargetCreated(RootCallTarget target) {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) target;
        final EngineData engineData = callTarget.engine;
        if (engineData.splitting) {
            final int newLimit = (int) (engineData.splitLimit + engineData.splittingGrowthLimit * callTarget.getUninitializedNodeCount());
            engineData.splitLimit = Math.min(newLimit, engineData.splittingMaxNumberOfSplitNodes);
        }
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.splittingStatistics) {
                engineData.splittingStatistics.totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
            }
        }
    }

    private static void calculateSplitWasteImpl(OptimizedCallTarget callTarget) {
        final List<OptimizedDirectCallNode> callNodes = NodeUtil.findAllNodeInstances(callTarget.getRootNode(), OptimizedDirectCallNode.class);
        callNodes.removeIf(callNode -> !callNode.isCallTargetCloned());
        for (OptimizedDirectCallNode node : callNodes) {
            final OptimizedCallTarget clonedCallTarget = node.getClonedCallTarget();
            if (waste.add(clonedCallTarget)) {
                final EngineData engineData = clonedCallTarget.engine;
                engineData.splittingStatistics.wastedTargetCount++;
                engineData.splittingStatistics.wastedNodeCount += clonedCallTarget.getUninitializedNodeCount();
                calculateSplitWasteImpl(clonedCallTarget);
            }
        }
    }

    static void newPolymorphicSpecialize(Node node, EngineData engineData) {
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.splittingStatistics) {
                final Map<Class<? extends Node>, Integer> polymorphicNodes = engineData.splittingStatistics.polymorphicNodes;
                final Class<? extends Node> aClass = node.getClass();
                polymorphicNodes.put(aClass, polymorphicNodes.getOrDefault(aClass, 0) + 1);
            }
        }
    }

    static class SplitStatisticsData {
        final Map<Class<? extends Node>, Integer> polymorphicNodes = new HashMap<>();
        final Map<OptimizedCallTarget, Integer> splitTargets = new HashMap<>();
        int splitCount;
        int forcedSplitCount;
        int splitNodeCount;
        int totalExecutedNodeCount;
        int totalCreatedNodeCount;
        int wastedNodeCount;
        int wastedTargetCount;

        SplitStatisticsData() {
        }
    }

    private static final class SplitStatisticsReporter implements GraalTruffleRuntimeListener {

        private static final String D_FORMAT = "%n%-40s: %10d";
        private static final String D_LONG_FORMAT = "%n%-120s: %10d";
        private static final String P_FORMAT = "%n%-40s: %9.2f%%";
        private static final String DELIMITER_FORMAT = "%n--- %s";

        SplitStatisticsReporter() {
        }

        @Override
        public void onEngineClosed(EngineData engineData) {
            if (engineData.traceSplittingSummary) {
                SplitStatisticsData stat = engineData.splittingStatistics;
                StringWriter messageBuilder = new StringWriter();
                try (PrintWriter out = new PrintWriter(messageBuilder)) {
                    out.print("Splitting Statistics");
                    out.printf(D_FORMAT, "Split count", engineData.splitCount);
                    out.printf(D_FORMAT, "Split limit", engineData.splitLimit);
                    out.printf(D_FORMAT, "Splits", stat.splitCount);
                    out.printf(D_FORMAT, "Forced splits", stat.forcedSplitCount);
                    out.printf(D_FORMAT, "Nodes created through splitting", stat.splitNodeCount);
                    out.printf(D_FORMAT, "Nodes created without splitting", stat.totalCreatedNodeCount);
                    out.printf(P_FORMAT, "Increase in nodes", (stat.splitNodeCount * 100.0) / (stat.totalCreatedNodeCount));
                    out.printf(D_FORMAT, "Split nodes wasted", stat.wastedNodeCount);
                    out.printf(P_FORMAT, "Percent of split nodes wasted", (stat.wastedNodeCount * 100.0) / (stat.splitNodeCount));
                    out.printf(D_FORMAT, "Targets wasted due to splitting", stat.wastedTargetCount);
                    out.printf(D_FORMAT, "Total nodes executed", stat.totalExecutedNodeCount);

                    out.printf(DELIMITER_FORMAT, "SPLIT TARGETS");
                    for (Map.Entry<OptimizedCallTarget, Integer> entry : sortByIntegerValue(stat.splitTargets).entrySet()) {
                        out.printf(D_FORMAT, entry.getKey(), entry.getValue());
                    }

                    out.printf(DELIMITER_FORMAT, "NODES");
                    for (Map.Entry<Class<? extends Node>, Integer> entry : sortByIntegerValue(stat.polymorphicNodes).entrySet()) {
                        out.printf(D_LONG_FORMAT, entry.getKey(), entry.getValue());
                    }
                }
                final TruffleLogger log = engineData.getLogger();
                log.log(Level.INFO, messageBuilder.toString());
            }
        }

        private static <K, T> Map<K, Integer> sortByIntegerValue(Map<K, Integer> map) {
            List<Entry<K, Integer>> list = new ArrayList<>(map.entrySet());
            list.sort((x, y) -> y.getValue().compareTo(x.getValue()));

            Map<K, Integer> result = new LinkedHashMap<>();
            for (Entry<K, Integer> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }

            return result;
        }
    }

    static void installListener(GraalTruffleRuntime runtime) {
        runtime.addListener(new SplitStatisticsReporter());
    }
}
