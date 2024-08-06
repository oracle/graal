/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.SourceLanguagePositionProvider;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.ParameterPlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A graph decoder that provides all necessary encoded graphs on-the-fly (by parsing the methods and
 * encoding the graphs).
 */
public class CachingPEGraphDecoder extends PEGraphDecoder {

    private static final TimerKey BuildGraphTimer = DebugContext.timer("PartialEvaluation-GraphBuilding");

    protected final Providers providers;
    protected final GraphBuilderConfiguration graphBuilderConfig;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> persistentGraphCache;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> localGraphCache;
    private final Supplier<AutoCloseable> createPersistentCachedGraphScope;
    private final BasePhase<? super CoreProviders> postParsingPhase;

    private final boolean allowAssumptionsDuringParsing;
    private final GraphBuilderPhase.Instance graphBuilderPhaseInstance;

    /**
     * Creates a new CachingPEGraphDecoder.
     *
     * @param forceLink if {@code true} and the graph contains an invoke of a method from a class
     *            that has not yet been linked, linking is performed.
     */
    public CachingPEGraphDecoder(Architecture architecture,
                    StructuredGraph graph,
                    Providers providers,
                    GraphBuilderConfiguration graphBuilderConfig,
                    LoopExplosionPlugin loopExplosionPlugin,
                    InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins,
                    ParameterPlugin parameterPlugin,
                    NodePlugin[] nodePlugins,
                    ResolvedJavaMethod peRootForInlining,
                    SourceLanguagePositionProvider sourceLanguagePositionProvider,
                    BasePhase<? super CoreProviders> postParsingPhase,
                    EconomicMap<ResolvedJavaMethod, EncodedGraph> persistentGraphCache,
                    Supplier<AutoCloseable> createPersistentCachedGraphScope,
                    GraphBuilderPhase.Instance graphBuilderPhaseInstance,
                    boolean allowAssumptionsDuringParsing,
                    boolean needsExplicitException,
                    boolean forceLink) {
        super(architecture, graph, providers, loopExplosionPlugin,
                        invocationPlugins, inlineInvokePlugins, parameterPlugin, nodePlugins, peRootForInlining, sourceLanguagePositionProvider,
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), needsExplicitException, forceLink);

        assert !graphBuilderConfig.trackNodeSourcePosition() || graph.trackNodeSourcePosition();

        this.providers = providers;
        this.graphBuilderConfig = graphBuilderConfig;
        this.postParsingPhase = postParsingPhase;
        this.persistentGraphCache = persistentGraphCache;
        this.createPersistentCachedGraphScope = createPersistentCachedGraphScope;
        this.localGraphCache = EconomicMap.create();
        this.allowAssumptionsDuringParsing = allowAssumptionsDuringParsing;
        this.graphBuilderPhaseInstance = graphBuilderPhaseInstance;
    }

    @SuppressWarnings("try")
    private EncodedGraph createGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        StructuredGraph graphToEncode;
        if (graph.isSubstitution() && IS_IN_NATIVE_IMAGE) {
            throw GraalError.shouldNotReachHere("dead path"); // ExcludeFromJacocoGeneratedReport
        } else {
            graphToEncode = buildGraph(method, intrinsicBytecodeProvider, canonicalizer);
        }

        /*
         * ConvertDeoptimizeToGuardPhase reduces the number of merges in the graph, so that fewer
         * frame states will be created. This significantly reduces the number of nodes in the
         * initial graph.
         */
        try (DebugContext.Scope scope = debug.scope("createGraph", graphToEncode)) {
            new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graphToEncode, providers);
            if (GraalOptions.EarlyGVN.getValue(graphToEncode.getOptions())) {
                new DominatorBasedGlobalValueNumberingPhase(canonicalizer).apply(graphToEncode, providers);
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        return GraphEncoder.encodeSingleGraph(graphToEncode, architecture);
    }

    @SuppressWarnings("try")
    private StructuredGraph buildGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, CanonicalizerPhase canonicalizer) {
        StructuredGraph graphToEncode;
        /*
         * Always parse graphs without assumptions, this is required when graphs are cached across
         * compilations. If the shared graph cache is disabled (--engine.EncodedGraphCache=false) a
         * compilation-local cache is used and graphs can contain assumptions, but this offers
         * little to no benefit and only meant for debugging. Assumptions can be added/enabled later
         * during PE/compilation.
         */
        // @formatter:off
        graphToEncode = new StructuredGraph.Builder(options, debug, AllowAssumptions.ifTrue(allowAssumptionsDuringParsing)).
                profileProvider(null).
                trackNodeSourcePosition(graphBuilderConfig.trackNodeSourcePosition()).
                method(method).
                cancellable(graph.getCancellable()).
                build();
        // @formatter:on
        try (DebugContext.Scope scope = debug.scope("buildGraph", graphToEncode); DebugCloseable a = BuildGraphTimer.start(debug)) {
            if (intrinsicBytecodeProvider != null) {
                throw GraalError.shouldNotReachHere("isn't this dead?"); // ExcludeFromJacocoGeneratedReport
            }
            graphBuilderPhaseInstance.apply(graphToEncode);
            canonicalizer.apply(graphToEncode, providers);
            if (postParsingPhase != null) {
                postParsingPhase.apply(graphToEncode, providers);
            }
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
        return graphToEncode;
    }

    @SuppressWarnings({"unused", "try"})
    private EncodedGraph lookupOrCreatePersistentEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
        EncodedGraph result = persistentGraphCache.get(method);
        if (result == null && method.hasBytecodes()) {
            try (AutoCloseable scope = createPersistentCachedGraphScope.get()) {
                // Encoded graphs creation must be wrapped by "scopes" provided by
                // createCachedGraphScope.
                result = createGraph(method, intrinsicBytecodeProvider);
            } catch (Throwable ex) {
                throw debug.handle(ex);
            }
            persistentGraphCache.put(method, result);
        }
        return result;
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
        EncodedGraph result = localGraphCache.get(method);
        if (result != null) {
            return result;
        }

        result = lookupOrCreatePersistentEncodedGraph(method, intrinsicBytecodeProvider);
        // Cached graph from previous compilation may not have source positions, re-parse and
        // store in compilation-local cache.
        if (result != null && !result.trackNodeSourcePosition() && graph.trackNodeSourcePosition()) {
            assert method.hasBytecodes();
            result = createGraph(method, intrinsicBytecodeProvider);
            assert result.trackNodeSourcePosition();
        }

        if (result != null) {
            localGraphCache.put(method, result);
        }

        return result;
    }
}
