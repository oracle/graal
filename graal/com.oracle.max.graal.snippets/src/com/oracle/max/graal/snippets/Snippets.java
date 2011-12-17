/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.snippets;

import java.lang.reflect.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Utilities for snippet installation and management.
 */
public class Snippets {

    public static void install(GraalRuntime runtime, CiTarget target, SnippetsInterface obj, boolean plotGraphs, PhasePlan plan) {
        Class<? extends SnippetsInterface> clazz = obj.getClass();
        GraalContext context = new GraalContext("Installing Snippet");
        BoxingMethodPool pool = new BoxingMethodPool(runtime);
        if (clazz.isAnnotationPresent(ClassSubstitution.class)) {
            installSubstitution(runtime, target, plotGraphs, plan, clazz, context, pool, clazz.getAnnotation(ClassSubstitution.class).value());
        } else {
            installSnippets(runtime, target, plotGraphs, plan, clazz, context, pool);
        }
    }

    private static void installSnippets(GraalRuntime runtime, CiTarget target, boolean plotGraphs, PhasePlan plan, Class< ? extends SnippetsInterface> clazz, GraalContext context,
                    BoxingMethodPool pool) {
        for (Method snippet : clazz.getDeclaredMethods()) {
            try {
                int modifiers = snippet.getModifiers();
                if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                    throw new RuntimeException("Snippet must not be abstract or native");
                }
                RiResolvedMethod snippetRiMethod = runtime.getRiMethod(snippet);
                if (snippetRiMethod.compilerStorage().get(Graph.class) == null) {
                    buildSnippetGraph(snippetRiMethod, runtime, target, context, pool, plotGraphs, plan);
                }
            } catch (GraalInternalError error) {
                if (context.isObserved()) {
                    if (error.node() != null) {
                        context.observable.fireCompilationEvent("VerificationError on Node " + error.node(), CompilationEvent.ERROR, error.node().graph());
                    } else if (error.graph() != null) {
                        context.observable.fireCompilationEvent("VerificationError on Graph " + error.graph(), CompilationEvent.ERROR, error.graph());
                    }
                }
                throw error;
            } catch (Throwable t) {
                throw new RuntimeException("Error when installing snippet for " + clazz, t);
            }
        }
    }

    private static void installSubstitution(GraalRuntime runtime, CiTarget target, boolean plotGraphs, PhasePlan plan, Class< ? extends SnippetsInterface> clazz, GraalContext context,
                    BoxingMethodPool pool, Class<?> original) throws GraalInternalError {
        for (Method snippet : clazz.getDeclaredMethods()) {
            try {
                Method method = original.getDeclaredMethod(snippet.getName(), snippet.getParameterTypes());
                if (!method.getReturnType().isAssignableFrom(snippet.getReturnType())) {
                    throw new RuntimeException("Snippet has incompatible return type");
                }
                int modifiers = snippet.getModifiers();
                if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                    throw new RuntimeException("Snippet must not be abstract or native");
                }
                RiResolvedMethod snippetRiMethod = runtime.getRiMethod(snippet);
                StructuredGraph graph = buildSnippetGraph(snippetRiMethod, runtime, target, context, pool, plotGraphs, plan);
                runtime.getRiMethod(method).compilerStorage().put(Graph.class, graph);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Could not resolve method to substitute with: " + snippet.getName(), e);
            } catch (GraalInternalError error) {
                if (context.isObserved()) {
                    if (error.node() != null) {
                        context.observable.fireCompilationEvent("VerificationError on Node " + error.node(), CompilationEvent.ERROR, error.node().graph());
                    } else if (error.graph() != null) {
                        context.observable.fireCompilationEvent("VerificationError on Graph " + error.graph(), CompilationEvent.ERROR, error.graph());
                    }
                }
                throw error;
            } catch (Throwable t) {
                throw new RuntimeException("Error when installing snippet for " + clazz, t);
            }
        }
    }

    private static StructuredGraph buildSnippetGraph(RiResolvedMethod snippetRiMethod, GraalRuntime runtime, CiTarget target, GraalContext context, BoxingMethodPool pool, boolean plotGraphs, PhasePlan plan) {
        IdealGraphPrinterObserver observer = null;
        if (plotGraphs) {
            observer = new IdealGraphPrinterObserver(GraalOptions.PrintIdealGraphAddress, GraalOptions.PrintIdealGraphPort);
            observer.compilationStarted(CiUtil.format("snippet:%h.%n(%p)", snippetRiMethod));
        }
        StructuredGraph graph = buildSnippetGraph(snippetRiMethod, runtime, target, context, pool, plan, observer);
        if (observer != null) {
            observer.compilationFinished(null);
        }
        return graph;
    }

    private static StructuredGraph buildSnippetGraph(RiResolvedMethod snippetRiMethod, GraalRuntime runtime, CiTarget target, GraalContext context, BoxingMethodPool pool, PhasePlan plan, IdealGraphPrinterObserver observer) {

        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDeoptFreeDefault();
        GraphBuilderPhase graphBuilder = new GraphBuilderPhase(runtime, snippetRiMethod, null, config);
        StructuredGraph graph = new StructuredGraph();
        graphBuilder.apply(graph, context);

        if (observer != null) {
            observer.printGraph(snippetRiMethod.name() + ":" + GraphBuilderPhase.class.getSimpleName(), graph);
        }

        new SnippetIntrinsificationPhase(runtime, pool).apply(graph, context);

        for (Invoke invoke : graph.getInvokes()) {
            MethodCallTargetNode callTarget = invoke.callTarget();
            RiResolvedMethod targetMethod = callTarget.targetMethod();
            RiResolvedType holder = targetMethod.holder();
            if (holder.isSubtypeOf(runtime.getType(SnippetsInterface.class))) {
                StructuredGraph targetGraph = (StructuredGraph) targetMethod.compilerStorage().get(Graph.class);
                if (targetGraph == null) {
                    targetGraph = buildSnippetGraph(targetMethod, runtime, target, context, pool, plan, observer);
                }
                InliningUtil.inline(invoke, targetGraph, true);
                new CanonicalizerPhase(target, runtime, null).apply(graph);
            }
        }

        new SnippetIntrinsificationPhase(runtime, pool).apply(graph, context);

        if (observer != null) {
            observer.printGraph(snippetRiMethod.name() + ":" + SnippetIntrinsificationPhase.class.getSimpleName(), graph);
        }
        new DeadCodeEliminationPhase().apply(graph, context);
        new CanonicalizerPhase(target, runtime, null).apply(graph, context);

        // TODO (gd) remove when we have safepoint polling elimination
        for (LoopEndNode end : graph.getNodes(LoopEndNode.class)) {
            end.setSafepointPolling(false);
        }

        if (observer != null) {
            observer.printGraph(snippetRiMethod.name() + ":" + "Final", graph);
        }

        snippetRiMethod.compilerStorage().put(Graph.class, graph);

        return graph;
    }

}
