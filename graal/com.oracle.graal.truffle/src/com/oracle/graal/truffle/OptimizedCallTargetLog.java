/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.truffle.TruffleInlining.CallTreeNodeVisitor;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

public final class OptimizedCallTargetLog {

    protected static final PrintStream OUT = TTY.out().out();

    static {
        if (TruffleCallTargetProfiling.getValue()) {
            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    printProfiling();
                }
            });
        }
    }

    private OptimizedCallTargetLog() {
    }

    public static void logInliningDecision(OptimizedCallTarget target) {
        TruffleInlining inlining = target.getInlining();
        if (inlining == null) {
            return;
        }

        logInliningStart(target);
        logInliningDecisionRecursive(inlining, 1);
        logInliningDone(target);
    }

    private static void logInliningDecisionRecursive(TruffleInlining result, int depth) {
        for (TruffleInliningDecision decision : result) {
            TruffleInliningProfile profile = decision.getProfile();
            boolean inlined = decision.isInline();
            String msg = inlined ? "inline success" : "inline failed";
            logInlinedImpl(msg, decision.getProfile().getCallNode(), profile, depth);
            if (inlined) {
                logInliningDecisionRecursive(decision, depth + 1);
            }
        }
    }

    public static void logTruffleCallTree(OptimizedCallTarget compilable) {
        CallTreeNodeVisitor visitor = new CallTreeNodeVisitor() {

            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode callNode = ((OptimizedDirectCallNode) node);
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    TruffleInliningDecision inlining = CallTreeNodeVisitor.getCurrentInliningDecision(decisionStack);
                    String dispatched = "<dispatched>";
                    if (inlining != null && inlining.isInline()) {
                        dispatched = "";
                    }
                    Map<String, Object> properties = new LinkedHashMap<>();
                    addASTSizeProperty(callNode.getCurrentCallTarget(), properties);
                    properties.putAll(callNode.getCurrentCallTarget().getDebugProperties());
                    properties.put("Stamp", callNode.getCurrentCallTarget().getArgumentStamp());
                    log((depth * 2), "opt call tree", callNode.getCurrentCallTarget().toString() + dispatched, properties);
                } else if (node instanceof OptimizedIndirectCallNode) {
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    log((depth * 2), "opt call tree", "<indirect>", new LinkedHashMap<String, Object>());
                }
                return true;
            }

        };

        TruffleInlining inlining = compilable.getInlining();
        if (inlining == null) {
            compilable.getRootNode().accept(visitor);
        } else {
            inlining.accept(compilable, visitor);
        }
    }

    private static void logInlinedImpl(String status, OptimizedDirectCallNode callNode, TruffleInliningProfile profile, int depth) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (profile != null) {
            properties.putAll(profile.getDebugProperties());
        }
        log((depth * 2), status, callNode.getCurrentCallTarget().toString(), properties);
    }

    private static void logInliningStart(OptimizedCallTarget target) {
        if (TraceTruffleInlining.getValue()) {
            log(0, "inline start", target.toString(), target.getDebugProperties());
        }
    }

    private static void logInliningDone(OptimizedCallTarget target) {
        if (TraceTruffleInlining.getValue()) {
            log(0, "inline done", target.toString(), target.getDebugProperties());
        }
    }

    public static void logOptimizingQueued(OptimizedCallTarget target) {
        if (TraceTruffleCompilationDetails.getValue()) {
            log(0, "opt queued", target.toString(), target.getDebugProperties());
        }
    }

    public static void logOptimizingUnqueued(OptimizedCallTarget target, Node oldNode, Node newNode, CharSequence reason) {
        if (TraceTruffleCompilationDetails.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addReplaceProperties(properties, oldNode, newNode);
            properties.put("Reason", reason);
            log(0, "opt unqueued", target.toString(), properties);
        }
    }

    private static void addReplaceProperties(Map<String, Object> properties, Node oldNode, Node newNode) {
        if (oldNode != null && newNode != null) {
            properties.put("OldClass", oldNode.getClass().getSimpleName());
            properties.put("NewClass", newNode.getClass().getSimpleName());
            properties.put("Node", newNode);
        }
    }

    static void logOptimizingStart(OptimizedCallTarget target) {
        if (TraceTruffleCompilationDetails.getValue()) {
            log(0, "opt start", target.toString(), target.getDebugProperties());
        }
    }

    public static void logOptimizedInvalidated(OptimizedCallTarget target, Node oldNode, Node newNode, CharSequence reason) {
        if (TraceTruffleCompilation.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addReplaceProperties(properties, oldNode, newNode);
            properties.put("Reason", reason);
            log(0, "opt invalidated", target.toString(), properties);
        }
    }

    public static void logOptimizingFailed(OptimizedCallTarget callSite, CharSequence reason) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Reason", reason);
        log(0, "opt fail", callSite.toString(), properties);
    }

    public static void logOptimizingDone(OptimizedCallTarget target, Map<String, Object> properties) {
        if (TraceTruffleCompilationDetails.getValue() || TraceTruffleCompilation.getValue()) {
            log(0, "opt done", target.toString(), properties);
        }
        if (TraceTruffleCompilationPolymorphism.getValue()) {
            target.nodeStream(true).filter(node -> node != null && (node.getCost() == NodeCost.MEGAMORPHIC || node.getCost() == NodeCost.POLYMORPHIC))//
            .forEach(node -> {
                NodeCost cost = node.getCost();
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("simpleName", node.getClass().getSimpleName());
                props.put("subtree", "\n" + NodeUtil.printCompactTreeToString(node));
                String msg = cost == NodeCost.MEGAMORPHIC ? "megamorphic" : "polymorphic";
                log(0, msg, node.toString(), props);
            });
        }
    }

    private static int splitCount = 0;

    static void logSplit(OptimizedDirectCallNode callNode, OptimizedCallTarget target, OptimizedCallTarget newTarget) {
        if (TraceTruffleSplitting.getValue()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addASTSizeProperty(target, properties);
            properties.put("Split#", ++splitCount);
            properties.put("Source", callNode.getEncapsulatingSourceSection());
            log(0, "split", newTarget.toString(), properties);
        }
    }

    public static void addASTSizeProperty(OptimizedCallTarget target, Map<String, Object> properties) {
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(target, false);
        int deepNodeCount = nodeCount;
        TruffleInlining inlining = target.getInlining();
        if (inlining != null) {
            deepNodeCount += inlining.getInlinedNodeCount();
        }
        properties.put("ASTSize", String.format("%5d/%5d", nodeCount, deepNodeCount));

    }

    public static void logPerformanceWarning(String details, Map<String, Object> properties) {
        log(0, "perf warn", details, properties);
    }

    static void log(int indent, String msg, String details, Map<String, Object> properties) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[truffle] %-16s ", msg));
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        sb.append(String.format("%-" + (60 - indent) + "s", details));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
                sb.append(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                sb.append(String.format(" %" + length + "s ", propertyBuilder.toString()));
            }
        }
        OUT.println(sb.toString());
    }

    private static int sumCalls(List<OptimizedCallTarget> targets, Function<TraceCompilationProfile, Integer> function) {
        return targets.stream().collect(Collectors.summingInt(target -> function.apply((TraceCompilationProfile) target.getCompilationProfile())));
    }

    private static void printProfiling() {
        Map<OptimizedCallTarget, List<OptimizedCallTarget>> groupedTargets = Truffle.getRuntime().getCallTargets().stream()//
        .map(target -> (OptimizedCallTarget) target)//
        .collect(Collectors.groupingBy(target -> {
            if (target.getSourceCallTarget() != null) {
                return target.getSourceCallTarget();
            }
            return target;
        }));

        List<OptimizedCallTarget> uniqueSortedTargets = groupedTargets.keySet().stream()//
        .sorted((target1, target2) -> sumCalls(groupedTargets.get(target2), p -> p.getTotalCallCount()) - sumCalls(groupedTargets.get(target1), p -> p.getTotalCallCount()))//
        .collect(Collectors.toList());

        int totalDirectCallCount = 0;
        int totalInlinedCallCount = 0;
        int totalIndirectCallCount = 0;
        int totalTotalCallCount = 0;
        int totalInterpretedCallCount = 0;
        int totalInvalidationCount = 0;

        OUT.println();
        OUT.printf(" %-50s  | %-15s || %-15s | %-15s || %-15s | %-15s | %-15s || %3s \n", "Call Target", "Total Calls", "Interp. Calls", "Opt. Calls", "Direct Calls", "Inlined Calls",
                        "Indirect Calls", "Invalidations");
        for (OptimizedCallTarget uniqueCallTarget : uniqueSortedTargets) {
            List<OptimizedCallTarget> allCallTargets = groupedTargets.get(uniqueCallTarget);
            int directCallCount = sumCalls(allCallTargets, p -> p.getDirectCallCount());
            int indirectCallCount = sumCalls(allCallTargets, p -> p.getIndirectCallCount());
            int inlinedCallCount = sumCalls(allCallTargets, p -> p.getInlinedCallCount());
            int interpreterCallCount = sumCalls(allCallTargets, p -> p.getInterpreterCallCount());
            int totalCallCount = sumCalls(allCallTargets, p -> p.getTotalCallCount());
            int invalidationCount = allCallTargets.stream().collect(Collectors.summingInt(target -> target.getCompilationProfile().getInvalidationCount()));

            totalDirectCallCount += directCallCount;
            totalInlinedCallCount += inlinedCallCount;
            totalIndirectCallCount += indirectCallCount;
            totalInvalidationCount += invalidationCount;
            totalInterpretedCallCount += interpreterCallCount;
            totalTotalCallCount += totalCallCount;

            if (totalCallCount > 0) {
                OUT.printf("  %-50s | %15d || %15d | %15d || %15d | %15d | %15d || %3d\n", uniqueCallTarget, totalCallCount, interpreterCallCount, totalCallCount - interpreterCallCount,
                                directCallCount, inlinedCallCount, indirectCallCount, invalidationCount);
            }

        }

        OUT.printf(" %-50s  | %15d || %15d | %15d || %15d | %15d | %15d || %3d\n", "Total", totalTotalCallCount, totalInterpretedCallCount, totalTotalCallCount - totalInterpretedCallCount,
                        totalDirectCallCount, totalInlinedCallCount, totalIndirectCallCount, totalInvalidationCount);

    }
}
