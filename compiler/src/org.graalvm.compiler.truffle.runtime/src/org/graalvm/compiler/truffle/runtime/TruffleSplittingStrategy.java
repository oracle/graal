/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

final class TruffleSplittingStrategy {

    private static final Set<OptimizedCallTarget> waste = Collections.synchronizedSet(new HashSet<>());
    private static final int RECURSIVE_SPLIT_DEPTH = 3;

    static void beforeCall(OptimizedDirectCallNode call, OptimizedCallTarget currentTarget) {
        final EngineData engineData = currentTarget.engine;
        if (engineData.traceSplittingSummary) {
            if (currentTarget.getCallCount() == 0) {
                synchronized (engineData.reporter) {
                    engineData.reporter.totalExecutedNodeCount += currentTarget.getUninitializedNodeCount();
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
            synchronized (engineData.reporter) {
                calculateSplitWasteImpl(call.getCurrentCallTarget());
            }
        }
        call.split();
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.reporter) {
                engineData.reporter.splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
                engineData.reporter.splitCount++;
                engineData.reporter.splitTargets.put(call.getCallTarget(), engineData.reporter.splitTargets.getOrDefault(call.getCallTarget(), 0) + 1);
            }
        }
    }

    private static boolean shouldSplit(EngineData engine, OptimizedDirectCallNode call) {
        OptimizedCallTarget callTarget = call.getCurrentCallTarget();
        if (!callTarget.isNeedsSplit()) {
            return false;
        }
        if (!canSplit(engine, call) || isRecursiveSplit(call, RECURSIVE_SPLIT_DEPTH) ||
                        engine.splitCount + call.getCallTarget().getUninitializedNodeCount() >= engine.splitLimit) {
            return false;
        }
        if (callTarget.getUninitializedNodeCount() > engine.splittingMaxCalleeSize) {
            return false;
        }
        return true;
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
                synchronized (engineData.reporter) {
                    engineData.reporter.forcedSplitCount++;
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
            synchronized (engineData.reporter) {
                engineData.reporter.totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
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
                engineData.reporter.wastedTargetCount++;
                engineData.reporter.wastedNodeCount += clonedCallTarget.getUninitializedNodeCount();
                calculateSplitWasteImpl(clonedCallTarget);
            }
        }
    }

    static void newPolymorphicSpecialize(Node node, EngineData engineData) {
        if (engineData.traceSplittingSummary) {
            synchronized (engineData.reporter) {
                final Map<Class<? extends Node>, Integer> polymorphicNodes = engineData.reporter.polymorphicNodes;
                final Class<? extends Node> aClass = node.getClass();
                polymorphicNodes.put(aClass, polymorphicNodes.getOrDefault(aClass, 0) + 1);
            }
        }
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
            if (engineData.traceSplittingSummary) {
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
            for (Map.Entry<OptimizedCallTarget, Integer> entry : sortByIntegerValue(splitTargets).entrySet()) {
                rt.log(String.format(D_FORMAT, entry.getKey(), entry.getValue()));
            }

            rt.log(String.format(DELIMITER_FORMAT, "NODES"));
            for (Map.Entry<Class<? extends Node>, Integer> entry : sortByIntegerValue(polymorphicNodes).entrySet()) {
                rt.log(String.format(D_LONG_FORMAT, entry.getKey(), entry.getValue()));
            }
        }

        public static <K, T> Map<K, Integer> sortByIntegerValue(Map<K, Integer> map) {
            List<Entry<K, Integer>> list = new ArrayList<>(map.entrySet());
            list.sort((x, y) -> y.getValue().compareTo(x.getValue()));

            Map<K, Integer> result = new LinkedHashMap<>();
            for (Entry<K, Integer> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }

            return result;
        }
    }

}
