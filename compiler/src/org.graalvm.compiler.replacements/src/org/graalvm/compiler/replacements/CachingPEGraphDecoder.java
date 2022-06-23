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
package org.graalvm.compiler.replacements;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A graph decoder that provides all necessary encoded graphs on-the-fly (by parsing the methods and
 * encoding the graphs).
 */
public class CachingPEGraphDecoder extends PEGraphDecoder {

    private static final TimerKey BuildGraphTimer = DebugContext.timer("PartialEvaluation-GraphBuilding");

    protected final Providers providers;
    protected final GraphBuilderConfiguration graphBuilderConfig;
    protected final OptimisticOptimizations optimisticOpts;
    private final AllowAssumptions allowAssumptions;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> persistentGraphCache;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> localGraphCache;
    private final Supplier<AutoCloseable> createPersistentCachedGraphScope;
    private final BasePhase<? super CoreProviders> postParsingPhase;

    public CachingPEGraphDecoder(Architecture architecture, StructuredGraph graph, Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    AllowAssumptions allowAssumptions, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin[] inlineInvokePlugins,
                    ParameterPlugin parameterPlugin,
                    NodePlugin[] nodePlugins, ResolvedJavaMethod peRootForInlining, SourceLanguagePositionProvider sourceLanguagePositionProvider,
                    BasePhase<? super CoreProviders> postParsingPhase, EconomicMap<ResolvedJavaMethod, EncodedGraph> persistentGraphCache, Supplier<AutoCloseable> createPersistentCachedGraphScope,
                    boolean needsExplicitException) {
        super(architecture, graph, providers, loopExplosionPlugin,
                        invocationPlugins, inlineInvokePlugins, parameterPlugin, nodePlugins, peRootForInlining, sourceLanguagePositionProvider,
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), needsExplicitException);

        this.providers = providers;
        this.graphBuilderConfig = graphBuilderConfig;
        this.optimisticOpts = optimisticOpts;
        this.allowAssumptions = allowAssumptions;
        this.postParsingPhase = postParsingPhase;
        this.persistentGraphCache = persistentGraphCache;
        this.createPersistentCachedGraphScope = createPersistentCachedGraphScope;
        this.localGraphCache = EconomicMap.create();
    }

    protected GraphBuilderPhase.Instance createGraphBuilderPhaseInstance(IntrinsicContext initialIntrinsicContext) {
        return new GraphBuilderPhase.Instance(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    @SuppressWarnings("try")
    private EncodedGraph createGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution) {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        StructuredGraph graphToEncode;
        if (isSubstitution && IS_IN_NATIVE_IMAGE) {
            throw GraalError.shouldNotReachHere("dead path");
        } else {
            graphToEncode = buildGraph(method, intrinsicBytecodeProvider, isSubstitution, canonicalizer);
        }

        /*
         * ConvertDeoptimizeToGuardPhase reduces the number of merges in the graph, so that fewer
         * frame states will be created. This significantly reduces the number of nodes in the
         * initial graph.
         */
        try (DebugContext.Scope scope = debug.scope("createGraph", graphToEncode)) {
            new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graphToEncode, providers);
            if (GraalOptions.EarlyGVN.getValue(graphToEncode.getOptions())) {
                new DominatorBasedGlobalValueNumberingPhase().apply(graphToEncode, providers);
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graphToEncode, architecture);
        persistentGraphCache.put(method, encodedGraph);
        return encodedGraph;
    }

    @SuppressWarnings("try")
    private StructuredGraph buildGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution, CanonicalizerPhase canonicalizer) {
        StructuredGraph graphToEncode;// @formatter:off
        graphToEncode = new StructuredGraph.Builder(options, debug, allowAssumptions).
                profileProvider(null).
                trackNodeSourcePosition(graphBuilderConfig.trackNodeSourcePosition()).
                method(method).
                setIsSubstitution(isSubstitution).
                cancellable(graph.getCancellable()).
                build();
        // @formatter:on
        try (DebugContext.Scope scope = debug.scope("buildGraph", graphToEncode); DebugCloseable a = BuildGraphTimer.start(debug)) {
            if (intrinsicBytecodeProvider != null) {
                throw GraalError.shouldNotReachHere("isn't this dead?");
            }
            IntrinsicContext initialIntrinsicContext = null;
            GraphBuilderPhase.Instance graphBuilderPhaseInstance = createGraphBuilderPhaseInstance(initialIntrinsicContext);
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

    private static boolean verifyAssumptions(EncodedGraph graph) {
        Assumptions assumptions = graph.getAssumptions();
        if (assumptions == null || assumptions.isEmpty()) {
            return true; // verified
        }
        for (Assumptions.Assumption assumption : assumptions) {
            if (assumption instanceof Assumptions.LeafType) {
                Assumptions.LeafType leafType = (Assumptions.LeafType) assumption;
                /*
                 * LeafType cannot be fully verified because the assumption doesn't imply that the
                 * type is (also) concrete. We check a common case (leaf + concrete type).
                 */
                Assumptions.AssumptionResult<ResolvedJavaType> assumptionResult = leafType.context.findLeafConcreteSubtype();
                if (assumptionResult != null) {
                    ResolvedJavaType candidate = assumptionResult.getResult();
                    if (!leafType.context.equals(candidate)) {
                        return false;
                    }
                }
            } else if (assumption instanceof Assumptions.ConcreteSubtype) {
                Assumptions.ConcreteSubtype concreteSubtype = (Assumptions.ConcreteSubtype) assumption;
                /*
                 * ConcreteSubtype cannot be fully verified because the assumption doesn't imply
                 * that the concrete subtype is (also) a leaf. We check a common case (leaf +
                 * concrete type).
                 */
                Assumptions.AssumptionResult<ResolvedJavaType> assumptionResult = concreteSubtype.context.findLeafConcreteSubtype();
                if (assumptionResult != null) {
                    ResolvedJavaType candidate = assumptionResult.getResult();
                    /*
                     * No equality check here because the ConcreteSubtype assumption allows
                     * non-concrete subtypes for interfaces.
                     */
                    if (!concreteSubtype.subtype.isAssignableFrom(candidate)) {
                        return false;
                    }
                }
            } else if (assumption instanceof Assumptions.ConcreteMethod) {
                Assumptions.ConcreteMethod concreteMethod = (Assumptions.ConcreteMethod) assumption;
                /*
                 * ConcreteMethod is the only assumption that can be verified since it matches
                 * findUniqueConcreteMethod semantics. If the assumption cannot be retrieved
                 * (findUniqueConcreteMethod returns null) then it was invalidated.
                 */
                Assumptions.AssumptionResult<ResolvedJavaMethod> assumptionResult = concreteMethod.context.findUniqueConcreteMethod(concreteMethod.method);
                if (assumptionResult == null || !concreteMethod.impl.equals(assumptionResult.getResult())) {
                    return false;
                }
            } else if (assumption instanceof Assumptions.NoFinalizableSubclass || assumption instanceof Assumptions.CallSiteTargetValue ||
                            "org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption".equals(assumption.getClass().getName())) {
                /*
                 * These assumptions cannot be (even partially) verified. The cached graph will be
                 * invalidated on code installation.
                 */
            } else {
                throw GraalError.shouldNotReachHere("unexpected assumption " + assumption);
            }
        }
        return true;
    }

    @SuppressWarnings({"unused", "try"})
    private EncodedGraph lookupOrCreatePersistentEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution,
                    boolean trackNodeSourcePosition) {
        EncodedGraph result = persistentGraphCache.get(method);
        if (result == null && method.hasBytecodes()) {
            try (AutoCloseable scope = createPersistentCachedGraphScope.get()) {
                // Encoded graphs that can be cached across compilations are wrapped by "scopes"
                // provided by createCachedGraphScope.
                result = createGraph(method, intrinsicBytecodeProvider, isSubstitution);
            } catch (Throwable ex) {
                throw debug.handle(ex);
            }
        }
        return result;
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution,
                    boolean trackNodeSourcePosition) {
        // Graph's assumptions are fresh or validated recently.
        EncodedGraph result = localGraphCache.get(method);
        if (result != null) {
            return result;
        }

        result = persistentGraphCache.get(method);
        if (result == null) {
            // Embedded assumptions in a fresh encoded graph should be up-to-date, so no need to
            // validate them.
            result = lookupOrCreatePersistentEncodedGraph(method, intrinsicBytecodeProvider, isSubstitution, trackNodeSourcePosition);
            if (result != null) {
                localGraphCache.put(method, result);
            }
        } else if (!verifyAssumptions(result)) {
            // Cached graph has invalid assumptions, drop from persistent cache and re-parse.
            persistentGraphCache.removeKey(method);
            // Embedded assumptions in a fresh encoded graph should be up-to-date, so no need to
            // validate them.
            result = lookupOrCreatePersistentEncodedGraph(method, intrinsicBytecodeProvider, isSubstitution, trackNodeSourcePosition);
            if (result != null) {
                localGraphCache.put(method, result);
            }
        } else {
            // Assumptions validated, avoid further checks.
            localGraphCache.put(method, result);
        }

        return result;
    }
}
