/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

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
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

final class TruffleSplittingStrategy {

    private static final Set<OptimizedCallTarget> waste = Collections.synchronizedSet(new HashSet<>());
    private static final int RECURSIVE_SPLIT_DEPTH = 3;

    @InliningCutoff
    static void beforeCall(OptimizedDirectCallNode call, OptimizedCallTarget currentTarget) {
        final EngineData engineData = currentTarget.engine;
        if (engineData.traceSplittingSummary) {
            traceSplittingPreShouldSplit(engineData, currentTarget);
        }
        if (shouldSplit(engineData, call)) {
            doSplit(engineData, call);
        }
    }

    private static void traceSplittingPreShouldSplit(final EngineData engineData, OptimizedCallTarget currentTarget) {
        if (currentTarget.getCallCount() == 0) {
            synchronized (engineData.splittingStatistics) {
                engineData.splittingStatistics.totalExecutedNodeCount += currentTarget.getUninitializedNodeCount();
            }
        }
    }

    private static void doSplit(EngineData engineData, OptimizedDirectCallNode call) {
        engineData.splitCount += call.getCallTarget().getUninitializedNodeCount();
        if (engineData.traceSplittingSummary) {
            traceSplittingPreSplit(engineData, call);
        }
        call.split();
        if (engineData.traceSplittingSummary) {
            traceSplittingPostSplit(engineData, call);
        }
    }

    private static void traceSplittingPreSplit(EngineData engineData, OptimizedDirectCallNode call) {
        synchronized (engineData.splittingStatistics) {
            calculateSplitWasteImpl(call.getCurrentCallTarget());
        }
    }

    private static void traceSplittingPostSplit(EngineData engineData, OptimizedDirectCallNode call) {
        synchronized (engineData.splittingStatistics) {
            engineData.splittingStatistics.splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            engineData.splittingStatistics.splitCount++;
            engineData.splittingStatistics.splitTargets.put(call.getCallTarget(), engineData.splittingStatistics.splitTargets.getOrDefault(call.getCallTarget(), 0) + 1);
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
            OptimizedTruffleRuntime.getRuntime().getListener().onCompilationSplitFailed(call, messageFactory.apply(call, engine));
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
                traceSplittingForcedSplit(engineData);
            }
        }
    }

    private static void traceSplittingForcedSplit(final EngineData engineData) {
        synchronized (engineData.splittingStatistics) {
            engineData.splittingStatistics.forcedSplitCount++;
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

        if (callTarget.isSplit()) { // ignore split call targets
            return;
        }

        if (callTarget.isOSR()) {
            /*
             * There is no splitting needed for OSR call targets as there are no direct call nodes
             * created with OSR targets. so there is also no need to increase the splitting budget
             * from call targets that are created like this.
             */
            return;
        }

        final EngineData engineData = callTarget.engine;
        if (engineData.splitting) {
            engineData.splitLimit = (int) (engineData.splitLimit + engineData.splittingGrowthLimit * callTarget.getUninitializedNodeCount());
        }
        if (engineData.traceSplittingSummary) {
            traceSplittingNewCallTarget(callTarget, engineData);
        }
    }

    private static void traceSplittingNewCallTarget(OptimizedCallTarget callTarget, EngineData engineData) {
        synchronized (engineData.splittingStatistics) {
            engineData.splittingStatistics.totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
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
            traceSplittingNewPolymorphicSpecialize(node, engineData);
        }
    }

    private static void traceSplittingNewPolymorphicSpecialize(Node node, EngineData engineData) {
        synchronized (engineData.splittingStatistics) {
            final Map<Class<? extends Node>, Integer> polymorphicNodes = engineData.splittingStatistics.polymorphicNodes;
            final Class<? extends Node> aClass = node.getClass();
            polymorphicNodes.put(aClass, polymorphicNodes.getOrDefault(aClass, 0) + 1);
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

    private static final class SplitStatisticsReporter implements OptimizedTruffleRuntimeListener {

        private static final String D_FORMAT = "%n%-82s: %10d";
        private static final String D_LONG_FORMAT = "%n%-120s: %10d";
        private static final String P_FORMAT = "%n%-82s: %9.2f%%";
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
                    out.printf(D_FORMAT, "Split count (sum of uninitializedNodeCount for all split targets)", engineData.splitCount);
                    out.printf(D_FORMAT, "Split limit (limit for the number of nodes to create through splitting)", engineData.splitLimit);
                    out.printf(D_FORMAT, "Splits (number of targets created through splitting)", stat.splitCount);
                    out.printf(D_FORMAT, "Forced splits (number of targets created through DirectCallNode#cloneCallTarget)", stat.forcedSplitCount);
                    out.printf(D_FORMAT, "Nodes created through splitting (sum of uninitializedNodeCount for split targets)", stat.splitNodeCount);
                    out.printf(D_FORMAT, "Nodes created without splitting (sum of uninitializedNodeCount for source targets)", stat.totalCreatedNodeCount);
                    out.printf(P_FORMAT, "Increase in nodes", (stat.splitNodeCount * 100.0) / (stat.totalCreatedNodeCount));
                    out.printf(D_FORMAT, "Split nodes wasted (callee split nodes wasted due to splitting the caller later)", stat.wastedNodeCount);
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
                final TruffleLogger log = engineData.getEngineLogger();
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

    static void installListener(OptimizedTruffleRuntime runtime) {
        runtime.addListener(new SplitStatisticsReporter());
    }
}
