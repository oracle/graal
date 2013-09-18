/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.phases.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of a cache for Truffle graphs for improving partial evaluation time.
 */
public final class TruffleCache {

    private final MetaAccessProvider metaAccessProvider;
    private final GraphBuilderConfiguration config;
    private final OptimisticOptimizations optimisticOptimizations;
    private final Replacements replacements;

    private final HashMap<List<Object>, StructuredGraph> cache = new HashMap<>();
    private final StructuredGraph markerGraph = new StructuredGraph();

    public TruffleCache(MetaAccessProvider metaAccessProvider, GraphBuilderConfiguration config, OptimisticOptimizations optimisticOptimizations, Replacements replacements) {
        this.metaAccessProvider = metaAccessProvider;
        this.config = config;
        this.optimisticOptimizations = optimisticOptimizations;
        this.replacements = replacements;
    }

    @SuppressWarnings("unused")
    public StructuredGraph lookup(final ResolvedJavaMethod method, final NodeInputList<ValueNode> arguments, final Assumptions assumptions, final CanonicalizerPhase finalCanonicalizer) {

        List<Object> key = new ArrayList<>(arguments.size() + 1);
        key.add(method);
        for (ValueNode v : arguments) {
            if (v.kind() == Kind.Object) {
                key.add(v.stamp());
            }
        }
        StructuredGraph resultGraph = cache.get(key);
        if (resultGraph != null) {
            return resultGraph;
        }

        if (resultGraph == markerGraph) {
            // Avoid recursive inline.
            return null;
        }

        cache.put(key, markerGraph);
        resultGraph = Debug.scope("TruffleCache", new Object[]{metaAccessProvider, method}, new Callable<StructuredGraph>() {

            public StructuredGraph call() {

                final StructuredGraph graph = new StructuredGraph(method);
                PhaseContext context = new PhaseContext(metaAccessProvider, new Assumptions(false), replacements);
                new GraphBuilderPhase(metaAccessProvider, config, optimisticOptimizations).apply(graph);

                for (LocalNode l : graph.getNodes(LocalNode.class)) {
                    if (l.kind() == Kind.Object) {
                        ValueNode actualArgument = arguments.get(l.index());
                        l.setStamp(l.stamp().join(actualArgument.stamp()));
                    }
                }

                // Intrinsify methods.
                new ReplaceIntrinsicsPhase(replacements).apply(graph);

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                CanonicalizerPhase canonicalizerPhase = new CanonicalizerPhase(!AOTCompilation.getValue());

                // Canonicalize / constant propagate.
                canonicalizerPhase.apply(graph, context);

                int mark = graph.getMark();
                for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
                    if (graph.getMark() != mark) {
                        canonicalizerPhase.applyIncremental(graph, context, mark);
                        mark = graph.getMark();
                    }
                    String name = methodCallTarget.targetName();
                    expandInvoke(methodCallTarget.invoke());
                }

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                // Canonicalize / constant propagate.
                canonicalizerPhase.apply(graph, context);

                // Conditional elimination.
                ConditionalEliminationPhase conditionalEliminationPhase = new ConditionalEliminationPhase(metaAccessProvider);
                conditionalEliminationPhase.apply(graph);

                if (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue()) {
                    TTY.println(String.format("[truffle] added to graph cache method %s with %d nodes.", method, graph.getNodeCount()));
                }
                return graph;
            }
        });
        cache.put(key, resultGraph);
        return resultGraph;
    }

    private FixedNode expandInvoke(Invoke invoke) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            final MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) invoke.callTarget();

            if ((methodCallTargetNode.invokeKind() == InvokeKind.Special || methodCallTargetNode.invokeKind() == InvokeKind.Static) &&
                            !Modifier.isNative(methodCallTargetNode.targetMethod().getModifiers()) && methodCallTargetNode.targetMethod().getAnnotation(ExplodeLoop.class) == null &&
                            methodCallTargetNode.targetMethod().getAnnotation(CompilerDirectives.SlowPath.class) == null) {
                Class<? extends FixedWithNextNode> macroSubstitution = replacements.getMacroSubstitution(methodCallTargetNode.targetMethod());
                if (macroSubstitution != null) {
                    return InliningUtil.inlineMacroNode(invoke, methodCallTargetNode.targetMethod(), methodCallTargetNode.graph(), macroSubstitution);
                } else {
                    StructuredGraph inlinedGraph = Debug.scope("ExpandInvoke", methodCallTargetNode.targetMethod(), new Callable<StructuredGraph>() {

                        public StructuredGraph call() {
                            StructuredGraph inlineGraph = replacements.getMethodSubstitution(methodCallTargetNode.targetMethod());
                            if (inlineGraph == null) {
                                inlineGraph = TruffleCache.this.lookup(methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), null, null);
                            }
                            return inlineGraph;
                        }
                    });
                    if (inlinedGraph == null) {
                        // Can happen for recursive calls.
                        return invoke.asNode();
                    }
                    FixedNode fixedNode = (FixedNode) invoke.predecessor();
                    InliningUtil.inline(invoke, inlinedGraph, true);
                    return fixedNode;
                }
            }
        }
        return invoke.asNode();
    }
}
