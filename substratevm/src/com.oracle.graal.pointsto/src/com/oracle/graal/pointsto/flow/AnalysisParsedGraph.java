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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.infrastructure.GraphProvider.Purpose;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.runtime.JVMCI;

public final class AnalysisParsedGraph {

    /**
     * Analysis graph parsing is (currently) done in two stages. This is necessary to break cyclic
     * dependencies between methods when performing pre-analysis optimizations. For example, if an
     * optimization {@code Opt0} processes the graph of {@code methodA} on {@code threadA}, it will
     * hold a lock during this operation. Assume {@code Opt0} requires to access the graph of
     * {@code methodB}. This will issue a parsing request that may be executed by a different thread
     * {@code threadB}. Now, it may be that {@code Opt0} also processes the graph of {@code methodB}
     * and to do so, it needs to access the graph of {@code methodA}. This would end up in a
     * deadlock. Staged parsing avoids this problem. In stage {@link #BYTECODE_PARSED}, only
     * bytecode parsing will be done and any pre-analysis optimizations run in stage
     * {@link #AFTER_PARSING_HOOKS_DONE}. Hence, {@code Opt0} can request the graph of the previous
     * stage to break the cycle.
     *
     * Right now, it is not supported to add further parsing stages, and we are limited to the
     * existing two stages. If further parsing stages are required, we first need to figure out: (1)
     * if and how the stage graphs should be persisted for layered images, and (2) how to specify if
     * a stage graph can be skipped if a later stage was requested.
     */
    public enum Stage {
        /**
         * This stage only performs bytecode parsing for the requested method. No additional
         * optimizations are applied except of any graph builder plugins that are used by the
         * bytecode parser.
         */
        BYTECODE_PARSED(null),

        /**
         * This stage performs additional after-parsing optimizations before the graph is published.
         */
        AFTER_PARSING_HOOKS_DONE(BYTECODE_PARSED);

        private final Stage previous;

        Stage(Stage previous) {
            this.previous = previous;
        }

        public static Stage finalStage() {
            return AFTER_PARSING_HOOKS_DONE;
        }

        public Stage previous() {
            return previous;
        }

        public boolean hasPrevious() {
            return previous != null;
        }

        public static boolean isRequiredStage(Stage stage, AnalysisMethod method) {
            return switch (stage) {
                case BYTECODE_PARSED -> method.isClassInitializer();
                case AFTER_PARSING_HOOKS_DONE -> true;
            };
        }
    }

    /**
     * The architecture that the image builder is running on. This determines whether unaligned
     * memory accesses are available for graph encoding / decoding at image build time.
     */
    public static final Architecture HOST_ARCHITECTURE;
    static {
        GraalJVMCICompiler compiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        HOST_ARCHITECTURE = compiler.getGraalRuntime().getCapability(RuntimeProvider.class).getHostBackend().getTarget().arch;
    }

    private static final AnalysisParsedGraph EMPTY = new AnalysisParsedGraph(null, false);

    private final EncodedGraph encodedGraph;
    private final boolean isIntrinsic;

    public AnalysisParsedGraph(EncodedGraph encodedGraph, boolean isIntrinsic) {
        this.isIntrinsic = isIntrinsic;
        this.encodedGraph = encodedGraph;
    }

    public EncodedGraph getEncodedGraph() {
        return encodedGraph;
    }

    public boolean isIntrinsic() {
        return isIntrinsic;
    }

    public static AnalysisParsedGraph parseBytecode(BigBang bb, AnalysisMethod method) {
        return parseBytecodeForStage(bb, method, Stage.BYTECODE_PARSED);

    }

    @SuppressWarnings("try")
    private static AnalysisParsedGraph parseBytecodeForStage(BigBang bb, AnalysisMethod method, Stage stage) {
        if (bb == null) {
            throw AnalysisError.shouldNotReachHere("BigBang object required for parsing method " + method.format("%H.%p(%n)"));
        }

        OptionValues options = bb.getOptions();
        Description description = new Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();

        try (Indent indent = debug.logAndIndent("parse graph %s", method)) {

            Object result = bb.getHostVM().parseGraph(bb, debug, method);
            if (result != HostVM.PARSING_UNHANDLED) {
                if (result instanceof StructuredGraph) {
                    return optimizeAndEncode(bb, method, (StructuredGraph) result, false, stage);
                } else {
                    assert result == HostVM.PARSING_FAILED : result;
                    return EMPTY;
                }
            }

            StructuredGraph graph = method.buildGraph(debug, method, bb.getProviders(method), Purpose.ANALYSIS);
            if (graph != null) {
                return optimizeAndEncode(bb, method, graph, false, stage);
            }

            InvocationPlugin plugin = bb.getProviders(method).getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method, options);
            if (plugin != null && !plugin.inlineOnly()) {
                Bytecode code = new ResolvedJavaMethodBytecode(method);
                graph = new SubstrateIntrinsicGraphBuilder(options, debug, bb.getProviders(method), code).buildGraph(plugin);
                if (graph != null) {
                    return optimizeAndEncode(bb, method, graph, true, stage);
                }
            }

            if (method.getCode() == null) {
                return EMPTY;
            }

            graph = new StructuredGraph.Builder(options, debug)
                            .method(method)
                            .recordInlinedMethods(bb.getHostVM().recordInlinedMethods(method))
                            .build();
            try (DebugContext.Scope s = debug.scope("ClosedWorldAnalysis", graph, method)) {

                // enable this logging to get log output in compilation passes
                try (Indent indent2 = debug.logAndIndent("parse graph phases")) {

                    GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(bb.getProviders(method).getGraphBuilderPlugins())
                                    .withEagerResolving(true)
                                    .withUnresolvedIsError(false)
                                    .withNodeSourcePosition(true)
                                    .withBytecodeExceptionMode(BytecodeExceptionMode.CheckAll)
                                    .withRetainLocalVariables(true);

                    config = bb.getHostVM().updateGraphBuilderConfiguration(config, method);

                    bb.getHostVM().createGraphBuilderPhase(bb.getProviders(method), config, OptimisticOptimizations.NONE, null).apply(graph);
                } catch (PermanentBailoutException ex) {
                    bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                    return EMPTY;
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            return optimizeAndEncode(bb, method, graph, false, stage);
        }
    }

    /**
     * Creates the final stage (i.e. {@link Stage#AFTER_PARSING_HOOKS_DONE}) graph for the given
     * method.
     *
     * There are two ways for how the final stage graph can be created: The stage 1 graph is (1)
     * available, or (2) is not available.
     *
     * For {@code (1)}, the graph was usually created with {@link #parseBytecode} (i.e. stage
     * {@link Stage#BYTECODE_PARSED}) but this is neither a strong requirement nor enforced. The
     * graph will then be decoded, the {@link HostVM#methodAfterParsingHook after parsing hook} will
     * be called, and again encoded.
     *
     * For {@code (2)} (if the input graph is {@code null}), the final stage graph will directly be
     * created. In particular, this will parse the method's bytecode and call
     * {@link #optimizeAndEncode} directly for stage {@link Stage#AFTER_PARSING_HOOKS_DONE}. This
     * means that the {@link HostVM#methodAfterParsingHook after parsing hook} will ONLY be called
     * for {@link Stage#AFTER_PARSING_HOOKS_DONE}.
     */
    public static AnalysisParsedGraph createFinalStage(BigBang bb, AnalysisMethod method, AnalysisParsedGraph stage1Graph) {
        if (stage1Graph == null) {
            return parseBytecodeForStage(bb, method, Stage.AFTER_PARSING_HOOKS_DONE);
        }
        if (stage1Graph.encodedGraph == null) {
            return EMPTY;
        }
        return optimizeAndEncode(bb, method, decodeParsedGraph(bb, method, stage1Graph), stage1Graph.isIntrinsic, Stage.AFTER_PARSING_HOOKS_DONE);
    }

    @SuppressWarnings("try")
    private static StructuredGraph decodeParsedGraph(BigBang bb, AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();

        StructuredGraph result = new StructuredGraph.Builder(bb.getOptions(), debug, bb.getHostVM().allowAssumptions(method))
                        .method(method)
                        .trackNodeSourcePosition(analysisParsedGraph.encodedGraph.trackNodeSourcePosition())
                        .recordInlinedMethods(analysisParsedGraph.encodedGraph.isRecordingInlinedMethods())
                        .build();

        try (DebugContext.Scope s = debug.scope("ClosedWorldAnalysis", result, method)) {
            GraphDecoder decoder = new GraphDecoder(HOST_ARCHITECTURE, result);
            decoder.decode(analysisParsedGraph.encodedGraph);
            return result;
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    @SuppressWarnings("try")
    private static AnalysisParsedGraph optimizeAndEncode(BigBang bb, AnalysisMethod method, StructuredGraph graph, boolean isIntrinsic, Stage stage) {
        try (DebugContext.Scope s = graph.getDebug().scope("ClosedWorldAnalysis", graph, method)) {
            /*
             * Must be called before any other thread can access the graph, i.e., before the graph
             * is published.
             */
            switch (stage) {
                case BYTECODE_PARSED -> bb.getHostVM().methodAfterBytecodeParsedHook(bb, method, graph);
                case AFTER_PARSING_HOOKS_DONE -> bb.getHostVM().methodAfterParsingHook(bb, method, graph);
            }

            EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, HOST_ARCHITECTURE);
            return new AnalysisParsedGraph(encodedGraph, isIntrinsic);
        } catch (Throwable e) {
            throw graph.getDebug().handle(e);
        }
    }
}
