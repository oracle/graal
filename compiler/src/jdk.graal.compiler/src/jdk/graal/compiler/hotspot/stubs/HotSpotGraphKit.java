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
package jdk.graal.compiler.hotspot.stubs;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderInstance;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot-specific extensions for manually building a graph.
 *
 * The support for inlining calls is here and not in the base class because the
 * {@link GraphBuilderPhase} is VM specific, i.e., for other VMs a subclass of
 * {@link GraphBuilderPhase} might be necessary for correct parsing.
 */
public class HotSpotGraphKit extends GraphKit {

    public HotSpotGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers, Plugins graphBuilderPlugins, CompilationIdentifier compilationId, String name,
                    boolean trackNodeSourcePosition, boolean recordInlinedMethods) {
        super(debug, stubMethod, providers, graphBuilderPlugins, compilationId, name, trackNodeSourcePosition, recordInlinedMethods);
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
        assert invoke instanceof Node : Assertions.errorMessage(invoke, reason, phase);
        Node invokeNode = (Node) invoke;
        ResolvedJavaMethod method = invoke.callTarget().targetMethod();
        StructuredGraph calleeGraph = getReplacements().getSnippet(method, null, null, null, false, null, invokeNode.getOptions());
        new DeadCodeEliminationPhase().apply(calleeGraph);
        InliningUtil.inline(invoke, calleeGraph, false, method, reason, phase);
    }

    protected GraphBuilderPhase.Instance createGraphBuilderInstance(GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        /* There is no HotSpot-specific subclass of GraphBuilderPhase yet. */
        return new HotSpotGraphBuilderInstance(getProviders(), graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }
}
