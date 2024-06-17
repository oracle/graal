/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted.runtimecompilation;

import static com.oracle.svm.common.meta.MultiMethod.ORIGINAL_METHOD;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;

public final class CallTreeInfo {
    private final Map<AnalysisMethod, RuntimeCompiledMethod> runtimeCompilations;
    private Map<RuntimeCompilationCandidate, InvokeNode> runtimeCandidateMap;
    private Map<AnalysisMethod, MethodNode> analysisMethodMap;
    private boolean initialized = false;

    private CallTreeInfo(Map<AnalysisMethod, RuntimeCompiledMethod> runtimeCompilations) {
        this.runtimeCompilations = runtimeCompilations;
    }

    public Collection<RuntimeCompiledMethod> runtimeCompilations() {
        return runtimeCompilations.values();
    }

    public static CallTreeInfo create(AnalysisUniverse aUniverse, Map<AnalysisMethod, String> invalidForRuntimeCompilation) {
        Map<AnalysisMethod, RuntimeCompiledMethod> runtimeCompilations = new HashMap<>();
        for (var method : aUniverse.getMethods()) {
            var rMethod = method.getMultiMethod(RUNTIME_COMPILED_METHOD);
            if (rMethod != null && rMethod.isReachable() && !invalidForRuntimeCompilation.containsKey(rMethod) && rMethod.getAnalyzedGraph() != null) {
                var origInlinedMethods = rMethod.getAnalyzedGraph().getInlinedMethods().stream().map(inlinedMethod -> {
                    AnalysisMethod orig = ((AnalysisMethod) inlinedMethod).getMultiMethod(ORIGINAL_METHOD);
                    assert orig != null;
                    return orig;
                }).collect(Collectors.toUnmodifiableSet());
                var previous = runtimeCompilations.put(rMethod, new RuntimeCompiledMethod(rMethod, origInlinedMethods));
                assert previous == null : previous;
            }
        }

        return new CallTreeInfo(runtimeCompilations);
    }

    private void initializeCallerInfo() {
        analysisMethodMap = new HashMap<>();
        runtimeCandidateMap = new HashMap<>();

        for (var runtimeCompilation : runtimeCompilations()) {
            AnalysisMethod method = runtimeCompilation.runtimeMethod;
            MethodNode callerMethodNode = analysisMethodMap.computeIfAbsent(method, MethodNode::new);

            for (InvokeInfo invokeInfo : method.getInvokes()) {
                AnalysisMethod invokeTarget = invokeInfo.getTargetMethod();
                boolean deoptInvokeTypeFlow = invokeInfo.isDeoptInvokeTypeFlow();
                if (deoptInvokeTypeFlow) {
                    assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(invokeTarget);
                    invokeTarget = invokeTarget.getMultiMethod(ORIGINAL_METHOD);
                }
                assert invokeTarget.isOriginalMethod();
                for (AnalysisMethod callee : invokeInfo.getAllCallees()) {
                    /*
                     * Special handling is needed for deoptInvokeTypeFlows because they only have
                     * the deopt method variant as a callee.
                     */
                    if (deoptInvokeTypeFlow || SubstrateCompilationDirectives.isRuntimeCompiledMethod(callee)) {
                        MethodNode calleeMethodNode = analysisMethodMap.computeIfAbsent(callee.getMultiMethod(RUNTIME_COMPILED_METHOD), MethodNode::new);
                        InvokeNode invoke = new InvokeNode(callerMethodNode, invokeInfo.getPosition());
                        calleeMethodNode.addCaller(invoke);

                        var origCallee = callee.getMultiMethod(ORIGINAL_METHOD);
                        assert origCallee != null;
                        runtimeCandidateMap.putIfAbsent(new RuntimeCompilationCandidate(origCallee, invokeTarget), invoke);
                    } else if (callee.isOriginalMethod() && callee.getMultiMethod(RUNTIME_COMPILED_METHOD) == null) {
                        /*
                         * Recording that this call was reachable, but not converted to a runtime
                         * compiled method.
                         */
                        runtimeCandidateMap.computeIfAbsent(new RuntimeCompilationCandidate(callee, invokeTarget),
                                        (candidate) -> {
                                            return new InvokeNode(callerMethodNode, invokeInfo.getPosition());
                                        });
                    }
                }
            }
        }
    }

    public void initialize(Set<AnalysisMethod> registeredRoots) {
        if (initialized) {
            return;
        }

        initializeCallerInfo();
        initialized = true;

        // ensure invokeInfo calculated

        Queue<MethodNode> worklist = new LinkedList<>();
        /*
         * First initialize all nodes which are registered roots
         */
        for (var methodNode : analysisMethodMap.values()) {
            if (registeredRoots.contains(methodNode.method.getMultiMethod(ORIGINAL_METHOD))) {
                worklist.add(methodNode);
                methodNode.trace = new TraceInfo(0, new BytecodePosition(null, methodNode.method, BytecodeFrame.UNKNOWN_BCI), null);
            }
        }

        /* Walk through to find a reachable path for all nodes */
        while (!worklist.isEmpty()) {
            MethodNode callerMethodNode = worklist.remove();
            TraceInfo callerTrace = callerMethodNode.trace;
            VMError.guarantee(callerTrace != null);

            for (InvokeInfo invokeInfo : callerMethodNode.method.getInvokes()) {
                boolean deoptInvokeTypeFlow = invokeInfo.isDeoptInvokeTypeFlow();
                if (deoptInvokeTypeFlow) {
                    // we do not need to trace deoptInvokes
                    continue;
                }
                InvokeNode callerInvokeNode = null;
                for (AnalysisMethod callee : invokeInfo.getAllCallees()) {
                    if (SubstrateCompilationDirectives.isRuntimeCompiledMethod(callee)) {
                        MethodNode calleeMethodNode = analysisMethodMap.get(callee);
                        if (calleeMethodNode != null && calleeMethodNode.trace == null) {
                            /*
                             * If this was the first time this node was reached, then add to
                             * worklist.
                             */
                            if (callerInvokeNode == null) {
                                callerInvokeNode = new InvokeNode(callerMethodNode, invokeInfo.getPosition());
                            }
                            worklist.add(calleeMethodNode);
                            calleeMethodNode.trace = new TraceInfo(callerTrace.level + 1, invokeInfo.getPosition(), callerInvokeNode);
                            callerTrace.addTraceTarget(calleeMethodNode);
                        }
                    }
                }
            }
        }
    }

    private static final String[] UNKNOWN_TRACE = new String[]{"Unknown"};
    private static final String[] EMPTY_STRING = new String[0];

    static String[] getCallTrace(CallTreeInfo callTreeInfo, AnalysisMethod method, Set<AnalysisMethod> registeredRuntimeCompilations) {
        callTreeInfo.initialize(registeredRuntimeCompilations);
        MethodNode methodNode = callTreeInfo.analysisMethodMap.get(method);
        if (methodNode == null) {
            return UNKNOWN_TRACE;
        }

        ArrayList<String> trace = new ArrayList<>();
        findCallTraceHelper(trace, methodNode);
        return trace.toArray(EMPTY_STRING);
    }

    static String[] getCallTrace(CallTreeInfo callTreeInfo, RuntimeCompilationCandidate candidate, Set<AnalysisMethod> registeredRuntimeCompilations) {
        callTreeInfo.initialize(registeredRuntimeCompilations);
        InvokeNode invokeNode = callTreeInfo.runtimeCandidateMap.get(candidate);
        if (invokeNode == null) {
            return UNKNOWN_TRACE;
        }

        ArrayList<String> trace = new ArrayList<>();
        findCallTraceHelper(trace, invokeNode.method);
        return trace.toArray(EMPTY_STRING);
    }

    private static void findCallTraceHelper(ArrayList<String> trace, MethodNode first) {
        if (first.trace != null) {
            /*
             * If there is a known trace from root, then we can return this
             */
            MethodNode current = first;
            while (current != null) {
                MethodNode parent = null;
                InvokeNode caller = current.trace.invokeParent;
                if (caller != null) {
                    parent = caller.method;
                    trace.add(caller.position.toString());
                }
                current = parent;
            }
            trace.add("[Root]");

        } else {
            /*
             * Otherwise we will walk an arbitrary caller until there is not a caller or we
             * encounter a cycle.
             */
            Set<MethodNode> covered = new HashSet<>();
            MethodNode current = first;
            covered.add(current);

            while (current != null) {
                // find parent
                MethodNode parent = null;
                for (InvokeNode caller : current.getCallers()) {
                    if (covered.add(caller.method)) {
                        parent = caller.method;
                        trace.add(caller.position.toString());
                        break;
                    }
                }
                current = parent;
            }
        }
    }

    public static void printCallTree(CallTreeInfo info, Set<AnalysisMethod> registeredRuntimeCompilations) {
        info.initialize(registeredRuntimeCompilations);

        System.out.println("depth;method;invoke position");
        for (MethodNode methodNode : info.analysisMethodMap.values()) {
            if (methodNode.trace != null && methodNode.trace.level == 0) {
                printCallTreeNode(methodNode);
            }
        }
    }

    private static void printCallTreeNode(MethodNode node) {
        TraceInfo trace = node.trace;
        StringBuilder indent = new StringBuilder();
        indent.append("  ".repeat(Math.max(0, trace.level)));
        indent.append(node.method.format("%H.%n"));
        System.out.format("%4d ; %-80s  ; %s%n", trace.level, indent, trace.position);
        for (MethodNode child : trace.getTraceTargets()) {
            printCallTreeNode(child);
        }
    }

    public static void printDeepestPath(CallTreeInfo info, Set<AnalysisMethod> registeredRuntimeCompilations) {
        info.initialize(registeredRuntimeCompilations);

        Optional<MethodNode> deepestNode = info.analysisMethodMap.values().stream().max(Comparator.comparingInt(t -> t.trace == null ? -1 : t.trace.level));

        if (deepestNode.isEmpty() || deepestNode.get().trace == null) {
            System.out.println("Could not find a trace");
            return;
        }

        MethodNode node = deepestNode.get();
        System.out.printf("Deepest level call tree path (%s calls):%n", node.trace.level);
        System.out.println("depth;method;invoke position");
        do {
            TraceInfo trace = node.trace;
            StringBuilder indent = new StringBuilder();
            indent.append("  ".repeat(Math.max(0, trace.level)));
            indent.append(node.method.format("%H.%n"));
            System.out.format("%4d ; %-80s  ; %s%n", trace.level, indent, trace.position);
            InvokeNode call = trace.invokeParent;
            node = call == null ? null : call.method;
        } while (node != null);
    }
}

class MethodNode {
    public AnalysisMethod method;
    public List<InvokeNode> callers;
    public TraceInfo trace;

    MethodNode(AnalysisMethod method) {
        this.method = method;
        this.callers = null;

    }

    List<InvokeNode> getCallers() {
        return callers == null ? List.of() : callers;
    }

    void addCaller(InvokeNode invoke) {
        if (callers == null) {
            callers = new ArrayList<>();
        }
        callers.add(invoke);
    }
}

class InvokeNode {
    final MethodNode method;
    final BytecodePosition position;

    InvokeNode(MethodNode method, BytecodePosition position) {
        this.method = method;
        this.position = position;
    }
}

class TraceInfo {
    final int level;
    final BytecodePosition position;
    final InvokeNode invokeParent;
    List<MethodNode> traceTargets;

    TraceInfo(int level, BytecodePosition position, InvokeNode invokeParent) {
        this.level = level;
        this.position = position;
        this.invokeParent = invokeParent;
    }

    List<MethodNode> getTraceTargets() {
        return traceTargets == null ? List.of() : traceTargets;
    }

    void addTraceTarget(MethodNode node) {
        if (traceTargets == null) {
            traceTargets = new ArrayList<>();
        }
        traceTargets.add(node);
    }
}
