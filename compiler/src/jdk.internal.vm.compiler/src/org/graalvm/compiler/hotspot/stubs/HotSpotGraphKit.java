/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.HotSpotGraphBuilderInstance;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot-specific extensions for manually building a graph.
 *
 * The support for inlining calls is here and not in the base class because the
 * {@link GraphBuilderPhase} is VM specific, i.e., for other VMs a subclass of
 * {@link GraphBuilderPhase} might be necessary for correct parsing.
 */
public class HotSpotGraphKit extends GraphKit {

    public HotSpotGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers, WordTypes wordTypes, Plugins graphBuilderPlugins, CompilationIdentifier compilationId, String name,
                    boolean trackNodeSourcePosition, boolean recordInlinedMethods) {
        super(debug, stubMethod, providers, wordTypes, graphBuilderPlugins, compilationId, name, trackNodeSourcePosition, recordInlinedMethods);
    }

    /**
     * Recursively {@linkplain #inlineAsIntrinsic inlines} all invocations currently in the graph.
     * The graph of the inlined method is processed in the same manner as for snippets and method
     * substitutions (e.g. intrinsics).
     */
    public void inlineInvokesAsIntrinsics(String reason, String phase) {
        while (!graph.getNodes().filter(InvokeNode.class).isEmpty()) {
            for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class).snapshot()) {
                inlineAsIntrinsic(invoke, reason, phase);
            }
        }

        // Clean up all code that is now dead after inlining.
        new DeadCodeEliminationPhase().apply(graph);
    }

    /**
     * Inlines a given invocation to a method. The graph of the inlined method is processed in the
     * same manner as for snippets and method substitutions (e.g. intrinsics).
     */
    public void inlineAsIntrinsic(Invoke invoke, String reason, String phase) {
        assert invoke instanceof Node;
        Node invokeNode = (Node) invoke;
        ResolvedJavaMethod method = invoke.callTarget().targetMethod();

        Plugins plugins = new Plugins(graphBuilderPlugins);
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);

        StructuredGraph calleeGraph;
        if (IS_IN_NATIVE_IMAGE) {
            calleeGraph = getReplacements().getSnippet(method, null, null, null, false, null, invokeNode.getOptions());
        } else {
            calleeGraph = new StructuredGraph.Builder(invokeNode.getOptions(), invokeNode.getDebug()).method(method).trackNodeSourcePosition(
                            invokeNode.graph().trackNodeSourcePosition()).setIsSubstitution(true).build();
            IntrinsicContext initialReplacementContext = new IntrinsicContext(method, method, getReplacements().getDefaultReplacementBytecodeProvider(), INLINE_AFTER_PARSING);
            GraphBuilderPhase.Instance instance = createGraphBuilderInstance(config, OptimisticOptimizations.NONE, initialReplacementContext);
            instance.apply(calleeGraph);
        }
        new DeadCodeEliminationPhase().apply(calleeGraph);

        InliningUtil.inline(invoke, calleeGraph, false, method, reason, phase);
    }

    protected GraphBuilderPhase.Instance createGraphBuilderInstance(GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        /* There is no HotSpot-specific subclass of GraphBuilderPhase yet. */
        return new HotSpotGraphBuilderInstance(getProviders(), graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }
}
