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

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.truffle.debug.*;
import com.oracle.graal.truffle.phases.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of a cache for Truffle graphs for improving partial evaluation time.
 */
public class TruffleCacheImpl implements TruffleCache {

    private final Providers providers;
    private final GraphBuilderConfiguration config;
    private final OptimisticOptimizations optimisticOptimizations;

    private final HashMap<List<Object>, StructuredGraph> cache = new HashMap<>();
    private final HashMap<List<Object>, Long> lastUsed = new HashMap<>();
    private final StructuredGraph markerGraph = new StructuredGraph(AllowAssumptions.NO);

    private final ResolvedJavaType stringBuilderClass;
    private final ResolvedJavaType runtimeExceptionClass;
    private final ResolvedJavaType errorClass;
    private final ResolvedJavaType controlFlowExceptionClass;

    private long counter;

    public TruffleCacheImpl(Providers providers, GraphBuilderConfiguration config, OptimisticOptimizations optimisticOptimizations) {
        this.providers = providers;
        this.config = config;
        this.optimisticOptimizations = optimisticOptimizations;

        this.stringBuilderClass = providers.getMetaAccess().lookupJavaType(StringBuilder.class);
        this.runtimeExceptionClass = providers.getMetaAccess().lookupJavaType(RuntimeException.class);
        this.errorClass = providers.getMetaAccess().lookupJavaType(Error.class);
        this.controlFlowExceptionClass = providers.getMetaAccess().lookupJavaType(ControlFlowException.class);
    }

    private static List<Object> computeCacheKey(ResolvedJavaMethod method, NodeInputList<ValueNode> arguments) {
        List<Object> key = new ArrayList<>(arguments.size() + 1);
        key.add(method);
        for (ValueNode v : arguments) {
            if (v.getKind() == Kind.Object) {
                key.add(v.stamp());
            }
        }
        return key;
    }

    public StructuredGraph lookup(ResolvedJavaMethod method, NodeInputList<ValueNode> arguments, CanonicalizerPhase canonicalizer) {
        List<Object> key = computeCacheKey(method, arguments);
        StructuredGraph resultGraph = cache.get(key);
        if (resultGraph == markerGraph) {
            // compilation failed previously, don't try again
            return null;
        }
        StructuredGraph graph = cacheLookup(method, arguments, canonicalizer);
        assert graph != markerGraph : "markerGraph should not leak out";
        return graph;
    }

    private StructuredGraph cacheLookup(ResolvedJavaMethod method, NodeInputList<ValueNode> arguments, CanonicalizerPhase canonicalizer) {
        if (method.getAnnotation(CompilerDirectives.TruffleBoundary.class) != null) {
            return null;
        }

        List<Object> key = computeCacheKey(method, arguments);
        StructuredGraph resultGraph = cache.get(key);
        if (resultGraph != null) {
            lastUsed.put(key, counter++);
            return resultGraph;
        }

        if (lastUsed.values().size() >= TruffleCompilerOptions.TruffleMaxCompilationCacheSize.getValue()) {
            lookupExceedsMaxSize();
        }

        StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
        PhaseContext phaseContext = new PhaseContext(providers);
        try (Scope s = Debug.scope("TruffleCache", providers.getMetaAccess(), method)) {

            graph = parseGraph(graph, phaseContext);
            if (graph == null) {
                return null;
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        try (Scope s = Debug.scope("TruffleCache", providers.getMetaAccess(), method, graph)) {

            lastUsed.put(key, counter++);
            cache.put(key, markerGraph);

            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                if (param.getKind() == Kind.Object) {
                    ValueNode actualArgument = arguments.get(param.index());
                    param.setStamp(param.stamp().join(actualArgument.stamp()));
                }
            }

            // Intrinsify methods.
            new ReplaceIntrinsicsPhase(providers.getReplacements()).apply(graph);

            // Convert deopt to guards.
            new ConvertDeoptimizeToGuardPhase().apply(graph);

            PartialEscapePhase partialEscapePhase = new PartialEscapePhase(false, canonicalizer);

            Mark mark = null;
            while (true) {

                partialEscapePhase.apply(graph, phaseContext);

                // Conditional elimination.
                ConditionalEliminationPhase conditionalEliminationPhase = new ConditionalEliminationPhase();
                conditionalEliminationPhase.apply(graph);

                // Canonicalize / constant propagate.
                canonicalizer.apply(graph, phaseContext);

                boolean inliningProgress = false;
                for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    if (!graph.getMark().equals(mark)) {
                        mark = lookupProcessMacroSubstitutions(graph, mark);
                    }
                    if (methodCallTarget.isAlive() && methodCallTarget.invoke() != null && shouldInline(methodCallTarget)) {
                        inliningProgress = true;
                        lookupDoInline(graph, phaseContext, canonicalizer, methodCallTarget);
                    }
                }

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                new EarlyReadEliminationPhase(canonicalizer).apply(graph, phaseContext);

                if (!inliningProgress) {
                    break;
                }
            }

            if (TruffleCompilerOptions.TraceTrufflePerformanceWarnings.getValue()) {
                int warnNodeCount = TruffleCompilerOptions.TrufflePerformanceWarningGraalNodeCount.getValue();
                if (graph.getNodeCount() > warnNodeCount) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("nodeCount", graph.getNodeCount());
                    map.put("method", method.toString());
                    TracePerformanceWarningsListener.logPerformanceWarning(String.format("Method on fast path contains more than %d graal nodes.", warnNodeCount), map);

                    try (Scope s2 = Debug.scope("TrufflePerformanceWarnings")) {
                        Debug.dump(graph, "performance warning");
                    }
                }
            }

            cache.put(key, graph);
            if (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue()) {
                TTY.println(String.format("[truffle] added to graph cache method %s with %d nodes.", method, graph.getNodeCount()));
            }
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private void lookupExceedsMaxSize() {
        List<Long> lastUsedList = new ArrayList<>();
        for (long l : lastUsed.values()) {
            lastUsedList.add(l);
        }
        Collections.sort(lastUsedList);
        long mid = lastUsedList.get(lastUsedList.size() / 2);

        List<List<Object>> toRemoveList = new ArrayList<>();
        for (Entry<List<Object>, Long> entry : lastUsed.entrySet()) {
            if (entry.getValue() < mid) {
                toRemoveList.add(entry.getKey());
            }
        }

        for (List<Object> entry : toRemoveList) {
            cache.remove(entry);
            lastUsed.remove(entry);
        }
    }

    private Mark lookupProcessMacroSubstitutions(StructuredGraph graph, Mark mark) {
        // Make sure macro substitutions such as
        // CompilerDirectives.transferToInterpreter get processed first.
        for (Node newNode : graph.getNewNodes(mark)) {
            if (newNode instanceof MethodCallTargetNode) {
                MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) newNode;
                Class<? extends FixedWithNextNode> macroSubstitution = providers.getReplacements().getMacroSubstitution(methodCallTargetNode.targetMethod());
                if (macroSubstitution != null) {
                    InliningUtil.inlineMacroNode(methodCallTargetNode.invoke(), methodCallTargetNode.targetMethod(), macroSubstitution);
                } else {
                    tryCutOffRuntimeExceptionsAndErrors(methodCallTargetNode);
                }
            }
        }
        return graph.getMark();
    }

    private void lookupDoInline(StructuredGraph graph, PhaseContext phaseContext, CanonicalizerPhase canonicalizer, MethodCallTargetNode methodCallTarget) {
        List<Node> canonicalizerUsages = new ArrayList<>();
        for (Node n : methodCallTarget.invoke().asNode().usages()) {
            if (n instanceof Canonicalizable) {
                canonicalizerUsages.add(n);
            }
        }
        List<ValueNode> argumentSnapshot = methodCallTarget.arguments().snapshot();
        Mark beforeInvokeMark = graph.getMark();
        expandInvoke(methodCallTarget, canonicalizer);
        for (Node arg : argumentSnapshot) {
            if (arg != null) {
                for (Node argUsage : arg.usages()) {
                    if (graph.isNew(beforeInvokeMark, argUsage) && argUsage instanceof Canonicalizable) {
                        canonicalizerUsages.add(argUsage);
                    }
                }
            }
        }
        canonicalizer.applyIncremental(graph, phaseContext, canonicalizerUsages);
    }

    protected StructuredGraph parseGraph(StructuredGraph graph, final PhaseContext phaseContext) {
        new GraphBuilderPhase.Instance(phaseContext.getMetaAccess(), phaseContext.getStampProvider(), null, config, optimisticOptimizations, false).apply(graph);
        return graph;
    }

    private void expandInvoke(MethodCallTargetNode methodCallTargetNode, CanonicalizerPhase canonicalizer) {
        StructuredGraph inlineGraph = providers.getReplacements().getMethodSubstitution(methodCallTargetNode.targetMethod());
        if (inlineGraph == null) {
            inlineGraph = cacheLookup(methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), canonicalizer);
        }
        if (inlineGraph == null) {
            return;
        }
        if (inlineGraph == this.markerGraph) {
            // Can happen for recursive calls.
            throw GraphUtil.approxSourceException(methodCallTargetNode, new IllegalStateException("Found illegal recursive call to " + methodCallTargetNode.targetMethod() +
                            ", must annotate such calls with @TruffleBoundary!"));
        }
        Invoke invoke = methodCallTargetNode.invoke();
        InliningUtil.inline(invoke, inlineGraph, true, null);
    }

    private boolean tryCutOffRuntimeExceptionsAndErrors(MethodCallTargetNode methodCallTargetNode) {
        if (methodCallTargetNode.targetMethod().isConstructor()) {
            ResolvedJavaType declaringClass = methodCallTargetNode.targetMethod().getDeclaringClass();
            ResolvedJavaType exceptionType = Objects.requireNonNull(StampTool.typeOrNull(methodCallTargetNode.receiver().stamp()));

            boolean removeAllocation = runtimeExceptionClass.isAssignableFrom(declaringClass) || errorClass.isAssignableFrom(declaringClass);
            boolean isControlFlowException = controlFlowExceptionClass.isAssignableFrom(exceptionType);
            if (removeAllocation && !isControlFlowException) {
                DeoptimizeNode deoptNode = methodCallTargetNode.graph().add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode));
                FixedNode invokeNode = methodCallTargetNode.invoke().asNode();
                invokeNode.replaceAtPredecessor(deoptNode);
                GraphUtil.killCFG(invokeNode);
                return true;
            }
        }
        return false;
    }

    protected boolean shouldInline(MethodCallTargetNode methodCallTargetNode) {
        boolean result = methodCallTargetNode.invokeKind().isDirect() && methodCallTargetNode.targetMethod().hasBytecodes() && methodCallTargetNode.targetMethod().canBeInlined() &&
                        methodCallTargetNode.targetMethod().getAnnotation(ExplodeLoop.class) == null &&
                        methodCallTargetNode.targetMethod().getAnnotation(CompilerDirectives.TruffleBoundary.class) == null &&
                        !methodCallTargetNode.targetMethod().getDeclaringClass().equals(stringBuilderClass);
        return result;
    }
}
