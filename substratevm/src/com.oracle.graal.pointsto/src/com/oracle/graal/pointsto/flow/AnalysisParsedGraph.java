/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.infrastructure.GraphProvider.Purpose;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.graal.pointsto.util.AnalysisError;

public final class AnalysisParsedGraph {
    private final StructuredGraph graph;
    private final boolean isIntrinsic;

    private AnalysisParsedGraph(StructuredGraph graph, boolean isIntrinsic) {
        this.graph = graph;
        this.isIntrinsic = isIntrinsic;
    }

    public StructuredGraph getGraph() {
        return graph;
    }

    public boolean isIntrinsic() {
        return isIntrinsic;
    }

    @SuppressWarnings("try")
    public static AnalysisParsedGraph parseBytecode(BigBang bb, AnalysisMethod method) {
        if (bb == null) {
            throw AnalysisError.shouldNotReachHere("BigBang object required for parsing method " + method.format("%H.%p(%n)"));
        }

        OptionValues options = bb.getOptions();
        Description description = new Description(method, method.getClass().getSimpleName() + ":" + method.getId());
        DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(bb.getProviders().getSnippetReflection())).description(description).build();

        try (Indent indent = debug.logAndIndent("parse graph %s", method)) {

            StructuredGraph graph = method.buildGraph(debug, method, bb.getProviders(), Purpose.ANALYSIS);
            if (graph != null) {
                return new AnalysisParsedGraph(graph, false);
            }

            InvocationPlugin plugin = bb.getProviders().getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
            if (plugin != null && !plugin.inlineOnly()) {
                Bytecode code = new ResolvedJavaMethodBytecode(method);
                graph = new SubstrateIntrinsicGraphBuilder(options, debug, bb.getProviders(), code).buildGraph(plugin);
                if (graph != null) {
                    return new AnalysisParsedGraph(graph, true);
                }
            }

            if (method.getCode() == null) {
                return new AnalysisParsedGraph(null, false);
            }

            graph = new StructuredGraph.Builder(options, debug).method(method).build();
            try (DebugContext.Scope s = debug.scope("ClosedWorldAnalysis", graph, method)) {

                // enable this logging to get log output in compilation passes
                try (Indent indent2 = debug.logAndIndent("parse graph phases")) {

                    GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(bb.getProviders().getGraphBuilderPlugins()).withEagerResolving(true)
                                    .withUnresolvedIsError(PointstoOptions.UnresolvedIsError.getValue(bb.getOptions()))
                                    .withNodeSourcePosition(true).withBytecodeExceptionMode(BytecodeExceptionMode.CheckAll);

                    /*
                     * We want to always disable the liveness analysis, since we want the points-to
                     * analysis to be as conservative as possible. The analysis results can then be
                     * used with the liveness analysis enabled or disabled.
                     */
                    config = config.withRetainLocalVariables(true);

                    bb.getHostVM().createGraphBuilderPhase(bb.getProviders(), config, OptimisticOptimizations.NONE, null).apply(graph);
                } catch (PermanentBailoutException ex) {
                    bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                    return new AnalysisParsedGraph(null, false);
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            return new AnalysisParsedGraph(graph, false);
        }
    }
}
