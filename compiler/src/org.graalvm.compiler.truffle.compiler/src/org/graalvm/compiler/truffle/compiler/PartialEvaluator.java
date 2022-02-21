/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumGraalNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.NodeSourcePositions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PrintExpansionHistogram;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;

import java.net.URI;
import java.nio.Buffer;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.CachingPEGraphDecoder;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.InlineKind;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl.CancellableTruffleCompilationTask;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.truffle.compiler.phases.FrameAccessVerificationPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentBranchesPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentTruffleBoundariesPhase;
import org.graalvm.compiler.truffle.compiler.phases.VerifyFrameDoesNotEscapePhase;
import org.graalvm.compiler.truffle.compiler.phases.inlining.AgnosticInliningPhase;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.TruffleOptions;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public abstract class PartialEvaluator {

    private static final TimerKey PartialEvaluationTimer = DebugContext.timer("PartialEvaluation-Decoding").doc("Time spent in the graph-decoding of partial evaluation.");
    private static final TimerKey TruffleEscapeAnalysisTimer = DebugContext.timer("PartialEvaluation-EscapeAnalysis").doc("Time spent in the escape-analysis in Truffle tier.");
    private static final TimerKey TruffleFrameVerifyFrameTimer = DebugContext.timer("PartialEvaluation-VerifyFrame").doc("Time spent in frame access verification in Truffle tier.");
    private static final TimerKey TruffleConditionalEliminationTimer = DebugContext.timer("PartialEvaluation-ConditionalElimination").doc("Time spent in conditional elimination in Truffle tier.");
    private static final TimerKey TruffleCanonicalizerTimer = DebugContext.timer("PartialEvaluation-Canonicalizer").doc("Time spent in the canonicalizer in the Truffle tier.");
    private static final TimerKey TruffleConvertDeoptimizeTimer = DebugContext.timer("PartialEvaluation-ConvertDeoptimizeToGuard").doc("Time spent in converting deoptimize to guard in Truffle tier.");

    protected final TruffleCompilerConfiguration config;
    protected final Providers providers;
    protected final Architecture architecture;
    private final CanonicalizerPhase canonicalizer;
    private final SnippetReflectionProvider snippetReflection;
    final ResolvedJavaMethod callDirectMethod;
    protected final ResolvedJavaMethod callInlined;
    final ResolvedJavaMethod callIndirectMethod;
    private final ResolvedJavaMethod profiledPERoot;
    private final GraphBuilderConfiguration configPrototype;
    private final InvocationPlugins firstTierDecodingPlugins;
    private final InvocationPlugins lastTierDecodingPlugins;
    private final NodePlugin[] nodePlugins;
    final KnownTruffleTypes knownTruffleTypes;
    final ResolvedJavaMethod callBoundary;
    private volatile GraphBuilderConfiguration configForParsing;

    /**
     * Holds instrumentation options initialized in
     * {@link #initialize(org.graalvm.options.OptionValues)} method before the first compilation.
     * These options are not engine aware.
     */
    volatile InstrumentPhase.InstrumentationConfiguration instrumentationCfg;
    /**
     * The instrumentation object is used by the Truffle instrumentation to count executions. The
     * value is lazily initialized the first time it is requested because it depends on the Truffle
     * options, and tests that need the instrumentation table need to override these options after
     * the TruffleRuntime object is created.
     */
    protected volatile InstrumentPhase.Instrumentation instrumentation;

    protected final TruffleConstantFieldProvider compilationLocalConstantProvider;

    public PartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration configForRoot, KnownTruffleTypes knownFields) {
        this.config = config;
        this.providers = config.lastTier().providers();
        this.architecture = config.architecture();
        this.canonicalizer = CanonicalizerPhase.create();
        this.snippetReflection = config.snippetReflection();
        this.knownTruffleTypes = knownFields;

        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        final MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        ResolvedJavaMethod[] methods = type.getDeclaredMethods();
        this.callDirectMethod = findRequiredMethod(type, methods, "callDirect", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.callInlined = findRequiredMethod(type, methods, "callInlined", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.callIndirectMethod = findRequiredMethod(type, methods, "callIndirect", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.profiledPERoot = findRequiredMethod(type, methods, "profiledPERoot", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callBoundary = findRequiredMethod(type, methods, "callBoundary", "([Ljava/lang/Object;)Ljava/lang/Object;");

        this.configPrototype = createGraphBuilderConfig(configForRoot, true);
        this.firstTierDecodingPlugins = createDecodingInvocationPlugins(config.firstTier().partialEvaluator(), configForRoot.getPlugins(), config.firstTier().providers());
        this.lastTierDecodingPlugins = createDecodingInvocationPlugins(config.lastTier().partialEvaluator(), configForRoot.getPlugins(), config.lastTier().providers());
        this.nodePlugins = createNodePlugins(configForRoot.getPlugins());
        this.compilationLocalConstantProvider = new TruffleConstantFieldProvider(providers.getConstantFieldProvider(), providers.getMetaAccess());
    }

    protected void initialize(OptionValues options) {
        instrumentationCfg = new InstrumentPhase.InstrumentationConfiguration(options);
        boolean needSourcePositions = options.get(NodeSourcePositions) ||
                        instrumentationCfg.instrumentBranches ||
                        instrumentationCfg.instrumentBoundaries ||
                        !options.get(TracePerformanceWarnings).isEmpty() ||
                        (TruffleOptions.AOT && options.get(TraceTransferToInterpreter));
        configForParsing = configPrototype.withNodeSourcePosition(configPrototype.trackNodeSourcePosition() || needSourcePositions).withOmitAssertions(
                        options.get(ExcludeAssertions));
    }

    public EconomicMap<ResolvedJavaMethod, EncodedGraph> getOrCreateEncodedGraphCache() {
        return EconomicMap.create();
    }

    /**
     * Gets the instrumentation manager associated with this compiler, creating it first if
     * necessary. Each compiler instance has its own instrumentation manager.
     */
    private InstrumentPhase.Instrumentation getInstrumentation() {
        if (instrumentation == null) {
            synchronized (this) {
                if (instrumentation == null) {
                    if (instrumentationCfg == null) {
                        throw new IllegalStateException("PartialEvaluator is not yet initialized");
                    }
                    long[] accessTable = new long[instrumentationCfg.instrumentationTableSize];
                    instrumentation = new InstrumentPhase.Instrumentation(accessTable);
                }
            }
        }
        return instrumentation;
    }

    static ResolvedJavaMethod findRequiredMethod(ResolvedJavaType declaringClass, ResolvedJavaMethod[] methods, String name, String descriptor) {
        for (ResolvedJavaMethod method : methods) {
            if (method.getName().equals(name) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return method;
            }
        }
        throw new NoSuchMethodError(declaringClass.toJavaName() + "." + name + descriptor);
    }

    public static InlineInvokePlugin.InlineInfo asInlineInfo(ResolvedJavaMethod method) {
        final TruffleCompilerRuntime.InlineKind inlineKind = TruffleCompilerRuntime.getRuntime().getInlineKind(method, true);
        switch (inlineKind) {
            case DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION;
            case DO_NOT_INLINE_NO_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            case DO_NOT_INLINE_WITH_EXCEPTION:
            case DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            case INLINE:
                return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
            default:
                throw new IllegalArgumentException(String.valueOf(inlineKind));
        }
    }

    public Providers getProviders() {
        return providers;
    }

    /**
     * Returns the root {@link GraphBuilderConfiguration}. The root configuration provides plugins
     * used by this {@link PartialEvaluator} but it's not configured with engine options. The root
     * configuration should be used in image generation time where the {@link PartialEvaluator} is
     * not yet initialized with engine options. At runtime the {@link #getConfig} should be used.
     */
    public GraphBuilderConfiguration getConfigPrototype() {
        return configPrototype;
    }

    /**
     * Returns the {@link GraphBuilderConfiguration} used by parsing. The returned configuration is
     * configured with engine options. In the image generation time the {@link PartialEvaluator} is
     * not yet initialized and the {@link #getConfigPrototype} should be used instead.
     *
     * @throws IllegalStateException when called on non initialized {@link PartialEvaluator}
     */
    public GraphBuilderConfiguration getConfig() {
        if (configForParsing == null) {
            throw new IllegalStateException("PartialEvaluator is not yet initialized");
        }
        return configForParsing;
    }

    public KnownTruffleTypes getKnownTruffleTypes() {
        return knownTruffleTypes;
    }

    public ResolvedJavaMethod[] getCompilationRootMethods() {
        return new ResolvedJavaMethod[]{profiledPERoot, callInlined, callDirectMethod};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod};
    }

    public ResolvedJavaMethod getCallDirect() {
        return callDirectMethod;
    }

    public ResolvedJavaMethod getCallInlined() {
        return callInlined;
    }

    public final class Request {
        public final OptionValues options;
        public final DebugContext debug;
        public final CompilableTruffleAST compilable;
        public final CompilationIdentifier compilationId;
        public final SpeculationLog log;
        public final CancellableTruffleCompilationTask task;
        public final StructuredGraph graph;
        final HighTierContext highTierContext;

        public Request(OptionValues options, DebugContext debug, CompilableTruffleAST compilable, ResolvedJavaMethod method,
                        CompilationIdentifier compilationId, SpeculationLog log, CancellableTruffleCompilationTask task) {
            Objects.requireNonNull(options);
            Objects.requireNonNull(debug);
            Objects.requireNonNull(compilable);
            Objects.requireNonNull(compilationId);
            Objects.requireNonNull(task);
            this.options = options;
            this.debug = debug;
            this.compilable = compilable;
            this.compilationId = compilationId;
            this.log = log;
            this.task = task;
            // @formatter:off
            StructuredGraph.Builder builder = new StructuredGraph.Builder(this.debug.getOptions(), this.debug, AllowAssumptions.YES).
                    name(this.compilable.getName()).
                    method(method).
                    speculationLog(this.log).
                    compilationId(this.compilationId).
                    trackNodeSourcePosition(configForParsing.trackNodeSourcePosition()).
                    cancellable(this.task);
            // @formatter:on
            builder = customizeStructuredGraphBuilder(builder);
            this.graph = builder.build();
            this.graph.getAssumptions().record(new TruffleAssumption(compilable.getValidRootAssumptionConstant()));
            this.graph.getAssumptions().record(new TruffleAssumption(compilable.getNodeRewritingAssumptionConstant()));
            highTierContext = new HighTierContext(providers, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);
        }

        public boolean isFirstTier() {
            return task.isFirstTier();
        }
    }

    @SuppressWarnings("try")
    public final StructuredGraph evaluate(Request request) {
        try (PerformanceInformationHandler handler = PerformanceInformationHandler.install(request.options)) {
            try (DebugContext.Scope s = request.debug.scope("CreateGraph", request.graph);
                            Indent indent = request.debug.logAndIndent("evaluate %s", request.graph);) {
                inliningGraphPE(request);
                assert GraphOrder.assertSchedulableGraph(request.graph) : "PE result must be schedulable in order to apply subsequent phases";
                // Even if we have not inlined any call target, we need to run the truffle tier
                // phases again after the PE inlining phase has finalized the graph.
                truffleTier(request);
                applyInstrumentationPhases(request);
                handler.reportPerformanceWarnings(request.compilable, request.graph);
                if (request.task.isCancelled()) {
                    return null;
                }
                new VerifyFrameDoesNotEscapePhase().apply(request.graph, false);
                NeverPartOfCompilationNode.verifyNotFoundIn(request.graph);
                materializeFrames(request.graph);
                TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
                setIdentityForValueTypes(request, rt);
                handleInliningAcrossTruffleBoundary(request, rt);
            } catch (Throwable e) {
                throw request.debug.handle(e);
            }
            return request.graph;
        }
    }

    private static void handleInliningAcrossTruffleBoundary(Request request, TruffleCompilerRuntime rt) {
        if (!request.options.get(InlineAcrossTruffleBoundary)) {
            // Do not inline across Truffle boundaries.
            for (MethodCallTargetNode mct : request.graph.getNodes(MethodCallTargetNode.TYPE)) {
                InlineKind inlineKind = rt.getInlineKind(mct.targetMethod(), false);
                if (!inlineKind.allowsInlining()) {
                    mct.invoke().setUseForInlining(false);
                }
            }
        }
    }

    private static void setIdentityForValueTypes(Request request, TruffleCompilerRuntime rt) {
        for (VirtualObjectNode virtualObjectNode : request.graph.getNodes(VirtualObjectNode.TYPE)) {
            if (virtualObjectNode instanceof VirtualInstanceNode) {
                VirtualInstanceNode virtualInstanceNode = (VirtualInstanceNode) virtualObjectNode;
                ResolvedJavaType type = virtualInstanceNode.type();
                if (rt.isValueType(type)) {
                    virtualInstanceNode.setIdentity(false);
                }
            }
        }
    }

    private static void materializeFrames(StructuredGraph graph) {
        for (AllowMaterializeNode materializeNode : graph.getNodes(AllowMaterializeNode.TYPE).snapshot()) {
            materializeNode.replaceAtUsages(materializeNode.getFrame());
            graph.removeFixed(materializeNode);
        }
    }

    @SuppressWarnings({"unused", "try"})
    private void partialEscape(Request request) {
        try (DebugContext.Scope pe = request.debug.scope("TrufflePartialEscape", request.graph)) {
            new PartialEscapePhase(request.options.get(IterativePartialEscape), canonicalizer, request.graph.getOptions()).apply(request.graph, request.highTierContext);
        } catch (Throwable t) {
            request.debug.handle(t);
        }
    }

    private void inlineReplacements(Request request) {
        for (MethodCallTargetNode methodCallTargetNode : request.graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (!methodCallTargetNode.invokeKind().isDirect()) {
                continue;
            }
            StructuredGraph inlineGraph = providers.getReplacements().getInlineSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci(),
                            methodCallTargetNode.invoke().getInlineControl(), request.graph.trackNodeSourcePosition(), methodCallTargetNode.asNode().getNodeSourcePosition(),
                            request.graph.allowAssumptions(), request.debug.getOptions());
            if (inlineGraph != null) {
                InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
            }
        }
    }

    /**
     * Hook for subclasses: customize the StructuredGraph.
     */
    protected StructuredGraph.Builder customizeStructuredGraphBuilder(StructuredGraph.Builder builder) {
        return builder;
    }

    /**
     * Hook for subclasses: return a customized compilation root for a specific call target.
     *
     * @param compilable the Truffle AST being compiled.
     */
    public ResolvedJavaMethod rootForCallTarget(CompilableTruffleAST compilable) {
        return profiledPERoot;
    }

    /**
     * Hook for subclasses: return a customized compilation root when inlining a specific call
     * target.
     *
     * @param compilable the Truffle AST being compiled.
     */
    public ResolvedJavaMethod inlineRootForCallTarget(CompilableTruffleAST compilable) {
        return callInlined;
    }

    private class InterceptReceiverPlugin implements ParameterPlugin {

        private final CompilableTruffleAST compilable;

        InterceptReceiverPlugin(CompilableTruffleAST compilable) {
            this.compilable = compilable;
        }

        @Override
        public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
            if (index == 0) {
                JavaConstant c = compilable.asJavaConstant();
                return ConstantNode.forConstant(c, providers.getMetaAccess());
            }
            return null;
        }
    }

    private static final class TruffleSourceLanguagePositionProvider implements SourceLanguagePositionProvider {

        private TruffleInliningData inliningPlan;

        private TruffleSourceLanguagePositionProvider(TruffleInliningData inliningPlan) {
            this.inliningPlan = inliningPlan;
        }

        @Override
        public SourceLanguagePosition getPosition(JavaConstant node) {
            final TruffleSourceLanguagePosition position = inliningPlan.getPosition(node);
            return position == null ? null : new SourceLanguagePositionImpl(position);
        }
    }

    private class PELoopExplosionPlugin implements LoopExplosionPlugin {

        @Override
        public LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            TruffleCompilerRuntime.LoopExplosionKind explosionKind = rt.getLoopExplosionKind(method);
            switch (explosionKind) {
                case NONE:
                    return LoopExplosionKind.NONE;
                case FULL_EXPLODE:
                    return LoopExplosionKind.FULL_EXPLODE;
                case FULL_EXPLODE_UNTIL_RETURN:
                    return LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN;
                case FULL_UNROLL:
                    return LoopExplosionKind.FULL_UNROLL;
                case MERGE_EXPLODE:
                    return LoopExplosionKind.MERGE_EXPLODE;
                case FULL_UNROLL_UNTIL_RETURN:
                    return LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN;
                default:
                    throw new IllegalStateException("Unsupported TruffleCompilerRuntime.LoopExplosionKind: " + String.valueOf(explosionKind));
            }
        }
    }

    @SuppressWarnings("unused")
    protected PEGraphDecoder createGraphDecoder(Request request, LoopExplosionPlugin loopExplosionPlugin,
                    InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin, NodePlugin[] nodePluginList,
                    SourceLanguagePositionProvider sourceLanguagePositionProvider, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        final GraphBuilderConfiguration newConfig = configForParsing.copy();
        InvocationPlugins parsingInvocationPlugins = newConfig.getPlugins().getInvocationPlugins();

        Plugins plugins = newConfig.getPlugins();
        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        plugins.clearInlineInvokePlugins();
        plugins.appendInlineInvokePlugin(replacements);
        plugins.appendInlineInvokePlugin(new ParsingInlineInvokePlugin(this, replacements, parsingInvocationPlugins, loopExplosionPlugin));
        if (!request.options.get(PrintExpansionHistogram)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }
        InvocationPlugins decodingPlugins = request.isFirstTier() ? firstTierDecodingPlugins : lastTierDecodingPlugins;

        DeoptimizeOnExceptionPhase postParsingPhase = new DeoptimizeOnExceptionPhase(
                        method -> TruffleCompilerRuntime.getRuntime().getInlineKind(method, true) == InlineKind.DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION);

        Providers compilationUnitProviders = providers.copyWith(compilationLocalConstantProvider);
        return new CachingPEGraphDecoder(architecture, request.graph, compilationUnitProviders, newConfig, TruffleCompilerImpl.Optimizations,
                        AllowAssumptions.ifNonNull(request.graph.getAssumptions()),
                        loopExplosionPlugin, decodingPlugins, inlineInvokePlugins, parameterPlugin, nodePluginList, callInlined,
                        sourceLanguagePositionProvider, postParsingPhase, graphCache, false);
    }

    public void doGraphPE(Request request, InlineInvokePlugin inlineInvokePlugin, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        InlineInvokePlugin[] inlineInvokePlugins = new InlineInvokePlugin[]{
                        (ReplacementsImpl) providers.getReplacements(),
                        new NodeLimitControlPlugin(request.options.get(MaximumGraalNodeCount)),
                        inlineInvokePlugin
        };
        PEGraphDecoder decoder = createGraphDecoder(request,
                        new PELoopExplosionPlugin(),
                        request.isFirstTier() ? firstTierDecodingPlugins : lastTierDecodingPlugins,
                        inlineInvokePlugins,
                        new InterceptReceiverPlugin(request.compilable),
                        nodePlugins,
                        new TruffleSourceLanguagePositionProvider(request.task.inliningData()),
                        graphCache);
        decoder.decode(request.graph.method(), request.graph.isSubstitution(), request.graph.trackNodeSourcePosition());
    }

    @SuppressWarnings("try")
    public void truffleTier(Request request) {
        try (DebugCloseable a = TruffleConvertDeoptimizeTimer.start(request.debug)) {
            new ConvertDeoptimizeToGuardPhase().apply(request.graph, request.highTierContext);
        }
        inlineReplacements(request);
        try (DebugCloseable a = TruffleConditionalEliminationTimer.start(request.debug)) {
            new ConditionalEliminationPhase(false).apply(request.graph, request.highTierContext);
        }
        try (DebugCloseable a = TruffleCanonicalizerTimer.start(request.debug)) {
            canonicalizer.apply(request.graph, request.highTierContext);
        }
        try (DebugCloseable a = TruffleFrameVerifyFrameTimer.start(request.debug)) {
            new FrameAccessVerificationPhase(request.compilable).apply(request.graph, request.highTierContext);
        }
        try (DebugCloseable a = TruffleEscapeAnalysisTimer.start(request.debug)) {
            partialEscape(request);
        }
    }

    /**
     * Graph-builder configuration is shared between the first- and the second-tier compilations,
     * because the encoded graphs for the partial evaluator are shared between these compilation
     * tiers.
     */
    protected GraphBuilderConfiguration createGraphBuilderConfig(GraphBuilderConfiguration graphBuilderConfig, boolean canDelayIntrinsification) {
        GraphBuilderConfiguration newConfig = graphBuilderConfig.copy();
        InvocationPlugins invocationPlugins = newConfig.getPlugins().getInvocationPlugins();
        registerGraphBuilderInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        appendParsingNodePlugins(newConfig.getPlugins());
        return newConfig;
    }

    protected void appendParsingNodePlugins(Plugins plugins) {
        if (JavaVersionUtil.JAVA_SPEC >= 16) {
            ResolvedJavaType memorySegmentProxyType = TruffleCompilerRuntime.getRuntime().resolveType(providers.getMetaAccess(), "jdk.internal.access.foreign.MemorySegmentProxy");
            for (ResolvedJavaMethod m : memorySegmentProxyType.getDeclaredMethods()) {
                if (m.getName().equals("scope")) {
                    appendMemorySegmentProxyScopePlugin(plugins, m);
                }
            }
        }
    }

    /**
     * The calls to MemorySegmentProxy.scope() from {@link Buffer} are problematic for Truffle
     * because they would remain as non-inlineable invokes after PE. Because these are virtual
     * calls, we can also not use a {@link InvocationPlugin} during PE to intrinsify the invoke to a
     * deoptimization. Therefore, we already do the intrinsification during bytecode parsing using a
     * {@link NodePlugin}, because that is also invoked for virtual calls.
     */
    private static void appendMemorySegmentProxyScopePlugin(Plugins plugins, ResolvedJavaMethod scopeMethod) {
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (scopeMethod.equals(method) && !b.needsExplicitException()) {
                    b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint));
                    return true;
                }
                return false;
            }
        });
    }

    protected NodePlugin[] createNodePlugins(Plugins plugins) {
        return plugins.getNodePlugins();
    }

    protected void registerGraphBuilderInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, canDelayIntrinsification, providers, knownTruffleTypes);
        for (GraphBuilderInvocationPluginProvider p : GraalServices.load(GraphBuilderInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(providers, architecture, invocationPlugins, canDelayIntrinsification);
        }
    }

    /**
     * Graph-decoding plugins are not shared between the first- and the second-tier compilations,
     * because the different tiers intrinsify the respective methods differently.
     */
    protected InvocationPlugins createDecodingInvocationPlugins(PartialEvaluatorConfiguration peConfig, Plugins parent, Providers tierProviders) {
        @SuppressWarnings("hiding")
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins(null, parent.getInvocationPlugins());
        registerGraphBuilderInvocationPlugins(decodingInvocationPlugins, false);
        peConfig.registerDecodingInvocationPlugins(decodingInvocationPlugins, false, tierProviders, config.architecture());
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    @SuppressWarnings({"unused", "try"})
    private boolean inliningGraphPE(Request request) {
        boolean inlined;
        try (DebugCloseable a = PartialEvaluationTimer.start(request.debug)) {
            AgnosticInliningPhase inliningPhase = new AgnosticInliningPhase(this, request);
            inliningPhase.apply(request.graph, providers);
            inlined = inliningPhase.hasInlined();
        }
        request.graph.maybeCompress();
        return inlined;
    }

    protected void applyInstrumentationPhases(Request request) {
        InstrumentPhase.InstrumentationConfiguration cfg = instrumentationCfg;
        if (cfg.instrumentBranches) {
            new InstrumentBranchesPhase(request.options, snippetReflection, getInstrumentation(), cfg.instrumentBranchesPerInlineSite).apply(request.graph, request.highTierContext);
        }
        if (cfg.instrumentBoundaries) {
            new InstrumentTruffleBoundariesPhase(request.options, snippetReflection, getInstrumentation(), cfg.instrumentBoundariesPerInlineSite).apply(request.graph, request.highTierContext);
        }
    }

    private static final SpeculationReasonGroup TRUFFLE_BOUNDARY_EXCEPTION_SPECULATIONS = new SpeculationReasonGroup("TruffleBoundaryWithoutException", ResolvedJavaMethod.class);

    public static SpeculationReason createTruffleBoundaryExceptionSpeculation(ResolvedJavaMethod targetMethod) {
        return TRUFFLE_BOUNDARY_EXCEPTION_SPECULATIONS.createSpeculationReason(targetMethod);
    }

    private static final class SourceLanguagePositionImpl implements SourceLanguagePosition {
        private final TruffleSourceLanguagePosition delegate;

        SourceLanguagePositionImpl(final TruffleSourceLanguagePosition delegate) {
            this.delegate = delegate;
        }

        @Override
        public String toShortString() {
            return delegate.getDescription();
        }

        @Override
        public int getOffsetEnd() {
            return delegate.getOffsetEnd();
        }

        @Override
        public int getOffsetStart() {
            return delegate.getOffsetStart();
        }

        @Override
        public int getLineNumber() {
            return delegate.getLineNumber();
        }

        @Override
        public URI getURI() {
            return delegate.getURI();
        }

        @Override
        public String getLanguage() {
            return delegate.getLanguage();
        }

        @Override
        public int getNodeId() {
            return delegate.getNodeId();
        }

        @Override
        public String getNodeClassName() {
            return delegate.getNodeClassName();
        }

    }

    private static final class NodeLimitControlPlugin implements InlineInvokePlugin {

        NodeLimitControlPlugin(int nodeLimit) {
            this.nodeLimit = nodeLimit;
        }

        private final int nodeLimit;

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            final StructuredGraph graph = b.getGraph();
            if (graph.getNodeCount() > nodeLimit) {
                try {
                    throw b.bailout("Graph too big to safely compile. Node count: " + graph.getNodeCount() + ". Limit: " + nodeLimit);
                } catch (BailoutException e) {
                    // wrap it to detect it later
                    throw new GraphTooBigBailoutException(e);
                }
            }
            // Continue onto other plugins.
            return null;
        }
    }
}
