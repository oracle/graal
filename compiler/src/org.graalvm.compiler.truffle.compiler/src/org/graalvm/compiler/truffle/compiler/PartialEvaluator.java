/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.LanguageAgnosticInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumGraalNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumInlineNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.NodeSourcePositions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PrintExpansionHistogram;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceStackTraceLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TreatPerformanceWarningsAsErrors;

import java.io.Closeable;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.ConstantNode;
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
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.CachingPEGraphDecoder;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.InlineKind;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.compiler.debug.HistogramInlineInvokePlugin;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionInjectNode;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionNode;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentBranchesPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentTruffleBoundariesPhase;
import org.graalvm.compiler.truffle.compiler.phases.VerifyFrameDoesNotEscapePhase;
import org.graalvm.compiler.truffle.compiler.phases.inlining.AgnosticInliningPhase;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningKind;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public abstract class PartialEvaluator {

    protected final Providers providers;
    protected final Architecture architecture;
    private final CanonicalizerPhase canonicalizer;
    private final SnippetReflectionProvider snippetReflection;
    private final ResolvedJavaMethod callDirectMethod;
    protected final ResolvedJavaMethod callInlined;
    protected final ResolvedJavaMethod inlinedPERoot;
    private final ResolvedJavaMethod callIndirectMethod;
    private final ResolvedJavaMethod profiledPERoot;
    private final GraphBuilderConfiguration configPrototype;
    private final InvocationPlugins decodingInvocationPlugins;
    private final NodePlugin[] nodePlugins;
    private final KnownTruffleTypes knownTruffleTypes;
    private final ResolvedJavaMethod callBoundary;
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

    public PartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture,
                    KnownTruffleTypes knownFields) {
        this.providers = providers;
        this.architecture = architecture;
        this.canonicalizer = CanonicalizerPhase.create();
        this.snippetReflection = snippetReflection;
        this.knownTruffleTypes = knownFields;

        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        final MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        ResolvedJavaMethod[] methods = type.getDeclaredMethods();
        this.callDirectMethod = findRequiredMethod(type, methods, "callDirectOrInlined", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.callInlined = findRequiredMethod(type, methods, "callInlined", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.inlinedPERoot = findRequiredMethod(type, methods, "inlinedPERoot", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callIndirectMethod = findRequiredMethod(type, methods, "callIndirect", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.profiledPERoot = findRequiredMethod(type, methods, "profiledPERoot", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callBoundary = findRequiredMethod(type, methods, "callBoundary", "([Ljava/lang/Object;)Ljava/lang/Object;");

        this.configPrototype = createGraphBuilderConfig(configForRoot, true);
        this.decodingInvocationPlugins = createDecodingInvocationPlugins(configForRoot.getPlugins());
        this.nodePlugins = createNodePlugins(configForRoot.getPlugins());
    }

    void initialize(OptionValues options) {
        instrumentationCfg = new InstrumentPhase.InstrumentationConfiguration(options);
        boolean needSourcePositions = TruffleCompilerOptions.getPolyglotOptionValue(options, NodeSourcePositions) ||
                        instrumentationCfg.instrumentBranches ||
                        instrumentationCfg.instrumentBoundaries ||
                        !TruffleCompilerOptions.getPolyglotOptionValue(options, TracePerformanceWarnings).isEmpty();
        configForParsing = configPrototype.withNodeSourcePosition(configPrototype.trackNodeSourcePosition() || needSourcePositions).withOmitAssertions(
                        TruffleCompilerOptions.getPolyglotOptionValue(options, ExcludeAssertions));
    }

    /**
     * Gets the instrumentation manager associated with this compiler, creating it first if
     * necessary. Each compiler instance has its own instrumentation manager.
     */
    // TODO GR-22185 Make private, used only in test outside PartialEvaluator
    public final InstrumentPhase.Instrumentation getInstrumentation() {
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

    private static void removeInlineTokenNodes(StructuredGraph graph) {
        for (InlineDecisionNode node : graph.getNodes(InlineDecisionNode.TYPE)) {
            node.notInlined();
        }
        for (InlineDecisionInjectNode node : graph.getNodes(InlineDecisionInjectNode.TYPE)) {
            node.resolve();
        }
    }

    /**
     * @return OptimizedCallTarget#callDirectOrInlined
     */
    public ResolvedJavaMethod getCallDirectMethod() {
        return callDirectMethod;
    }

    /**
     * @return OptimizedCallTarget#callIndirect
     */
    public ResolvedJavaMethod getCallIndirectMethod() {
        return callIndirectMethod;
    }

    /**
     * @return OptimizedCallTarget#callBoundary
     */
    public ResolvedJavaMethod getCallBoundary() {
        return callBoundary;
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
        return new ResolvedJavaMethod[]{profiledPERoot, callInlined, inlinedPERoot};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod, inlinedPERoot};
    }

    public final class Request {
        public final OptionValues options;
        public final DebugContext debug;
        public final CompilableTruffleAST compilable;
        public final TruffleInliningPlan inliningPlan;
        public final AllowAssumptions allowAssumptions;
        public final CompilationIdentifier compilationId;
        public final SpeculationLog log;
        public final Cancellable cancellable;
        public final StructuredGraph graph;
        final HighTierContext highTierContext;

        public Request(OptionValues options, DebugContext debug, CompilableTruffleAST compilable, ResolvedJavaMethod method, TruffleInliningPlan inliningPlan,
                        AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, SpeculationLog log, Cancellable cancellable) {
            this.options = options;
            this.debug = debug;
            this.compilable = compilable;
            this.inliningPlan = inliningPlan;
            this.allowAssumptions = allowAssumptions;
            this.compilationId = compilationId;
            this.log = log;
            this.cancellable = cancellable;
            // @formatter:off
            StructuredGraph.Builder builder = new StructuredGraph.Builder(TruffleCompilerOptions.getOptions(), this.debug, this.allowAssumptions).
                    name(this.compilable.toString()).
                    method(method).
                    speculationLog(this.log).
                    compilationId(this.compilationId).
                    trackNodeSourcePosition(configForParsing.trackNodeSourcePosition()).
                    cancellable(this.cancellable);
            // @formatter:on
            builder = customizeStructuredGraphBuilder(builder);
            this.graph = builder.build();
            highTierContext = new HighTierContext(providers, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);
        }
    }

    @SuppressWarnings("try")
    public StructuredGraph evaluate(Request request) {
        try (PerformanceInformationHandler handler = PerformanceInformationHandler.install(request.options)) {
            try (DebugContext.Scope s = request.debug.scope("CreateGraph", request.graph);
                            Indent indent = request.debug.logAndIndent("evaluate %s", request.graph);) {
                fastPartialEvaluation(request, handler);
                if (request.cancellable != null && request.cancellable.isCancelled()) {
                    return null;
                }
                new VerifyFrameDoesNotEscapePhase().apply(request.graph, false);
                postPartialEvaluation(request.options, request.graph);

            } catch (Throwable e) {
                throw request.debug.handle(e);
            }
            return request.graph;
        }
    }

    public StructuredGraph evaluate(Request request, InlineInvokePlugin plugin, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining) {
        // This is only called by agnostic inlining. Legacy inlining does not use this method.
        doGraphPE(request, plugin, graphCacheForInlining);
        return request.graph;
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
        return inlinedPERoot;
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

    public static class PEInlineInvokePlugin implements InlineInvokePlugin {

        private TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();

        protected InlineInfo asInlineInfo(ResolvedJavaMethod method) {
            final InlineKind inlineKind = rt.getInlineKind(method, true);
            switch (inlineKind) {
                case DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION:
                    return InlineInfo.DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION;
                case DO_NOT_INLINE_NO_EXCEPTION:
                    return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
                case DO_NOT_INLINE_WITH_EXCEPTION:
                case DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION:
                    return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
                case INLINE:
                    return InlineInfo.createStandardInlineInfo(method);
                default:
                    throw new IllegalArgumentException(String.valueOf(inlineKind));
            }
        }
    }

    private class PEInliningPlanInvokePlugin extends PEInlineInvokePlugin {

        private final Deque<TruffleInliningPlan> inlining;
        private final int nodeLimit;
        private final StructuredGraph graph;
        private final int inliningNodeLimit;
        private final OptionValues options;
        private boolean graphTooBigReported;

        PEInliningPlanInvokePlugin(OptionValues options, TruffleInliningPlan inlining, StructuredGraph graph) {
            this.options = options;
            this.inlining = new ArrayDeque<>();
            this.inlining.push(inlining);
            this.graph = graph;
            this.nodeLimit = getPolyglotOptionValue(options, MaximumGraalNodeCount);
            this.inliningNodeLimit = getPolyglotOptionValue(options, MaximumInlineNodeCount);
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            if (graph.getNodeCount() > nodeLimit) {
                throw builder.bailout("Graph too big to safely compile. Node count: " + graph.getNodeCount() + ". Limit: " + nodeLimit);
            }
            InlineInfo inlineInfo = asInlineInfo(original);
            if (!inlineInfo.allowsInlining()) {
                return inlineInfo;
            }
            assert !builder.parsingIntrinsic();

            if (original.equals(callDirectMethod)) {
                ValueNode arg0 = arguments[1];
                if (!arg0.isConstant()) {
                    GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
                }
                if (graph.getNodeCount() > inliningNodeLimit) {
                    logGraphTooBig();
                    return inlineInfo;
                }
                TruffleInliningPlan.Decision decision = getDecision(inlining.peek(), (JavaConstant) arg0.asConstant());
                if (decision != null && decision.shouldInline()) {
                    inlining.push(decision);
                    JavaConstant assumption = decision.getNodeRewritingAssumption();
                    builder.getAssumptions().record(new TruffleAssumption(assumption));
                    return createStandardInlineInfo(callInlined);
                }
            }

            return inlineInfo;
        }

        private void logGraphTooBig() {
            if (!graphTooBigReported && getPolyglotOptionValue(options, TraceInlining)) {
                graphTooBigReported = true;
                final HashMap<String, Object> properties = new HashMap<>();
                properties.put("graph node count", graph.getNodeCount());
                properties.put("graph node limit", inliningNodeLimit);
                TruffleCompilerRuntime.getRuntime().logEvent(0, "Truffle inlining caused graal node count to be too big during partial evaluation.", "", properties);
            }
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod inlinedTargetMethod) {
            if (inlinedTargetMethod.equals(callInlined)) {
                inlining.pop();
            }
        }
    }

    public static class TruffleSourceLanguagePositionProvider implements SourceLanguagePositionProvider {

        private TruffleInliningPlan inliningPlan;

        public TruffleSourceLanguagePositionProvider(TruffleInliningPlan inliningPlan) {
            this.inliningPlan = inliningPlan;
        }

        @Override
        public SourceLanguagePosition getPosition(JavaConstant node) {
            final TruffleSourceLanguagePosition position = inliningPlan.getPosition(node);
            return position == null ? null : new SourceLanguagePositionImpl(position);
        }
    }

    private class ParsingInlineInvokePlugin extends PEInlineInvokePlugin {

        private final ReplacementsImpl replacements;
        private final InvocationPlugins invocationPlugins;
        private final LoopExplosionPlugin loopExplosionPlugin;

        ParsingInlineInvokePlugin(ReplacementsImpl replacements, InvocationPlugins invocationPlugins, LoopExplosionPlugin loopExplosionPlugin) {
            this.replacements = replacements;
            this.invocationPlugins = invocationPlugins;
            this.loopExplosionPlugin = loopExplosionPlugin;
        }

        private boolean hasMethodHandleArgument(ValueNode[] arguments) {
            for (ValueNode argument : arguments) {
                if (argument.isConstant()) {
                    JavaConstant constant = argument.asJavaConstant();
                    if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull() && knownTruffleTypes.classMethodHandle.isInstance(constant)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            if (invocationPlugins.lookupInvocation(original) != null || replacements.hasSubstitution(original, builder.bci())) {
                /*
                 * During partial evaluation, the invocation plugin or the substitution might
                 * trigger, so we want the call to remain (we have better type information and more
                 * constant values during partial evaluation). But there is no guarantee for that,
                 * so we also need to preserve exception handler information for the call.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (loopExplosionPlugin.loopExplosionKind(original) != LoopExplosionPlugin.LoopExplosionKind.NONE) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }

            InlineInfo inlineInfo = asInlineInfo(original);
            if (!inlineInfo.allowsInlining()) {
                return inlineInfo;
            }
            if (original.equals(callIndirectMethod) || original.equals(inlinedPERoot) || original.equals(callDirectMethod)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            if (hasMethodHandleArgument(arguments)) {
                /*
                 * We want to inline invokes that have a constant MethodHandle parameter to remove
                 * invokedynamic related calls as early as possible.
                 */
                return inlineInfo;
            }
            return null;
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
        plugins.appendInlineInvokePlugin(new ParsingInlineInvokePlugin(replacements, parsingInvocationPlugins, loopExplosionPlugin));
        if (!getPolyglotOptionValue(request.options, PrintExpansionHistogram)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        DeoptimizeOnExceptionPhase postParsingPhase = new DeoptimizeOnExceptionPhase(
                        method -> TruffleCompilerRuntime.getRuntime().getInlineKind(method, true) == InlineKind.DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION);

        Providers compilationUnitProviders = providers.copyWith(new TruffleConstantFieldProvider(providers.getConstantFieldProvider(), providers.getMetaAccess()));
        return new CachingPEGraphDecoder(architecture, request.graph, compilationUnitProviders, newConfig, TruffleCompilerImpl.Optimizations,
                        AllowAssumptions.ifNonNull(request.graph.getAssumptions()),
                        loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin, nodePluginList, callInlined, inlinedPERoot,
                        sourceLanguagePositionProvider, postParsingPhase, graphCache);
    }

    public void doGraphPE(Request request, InlineInvokePlugin inlineInvokePlugin, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        LoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();
        ParameterPlugin parameterPlugin = new InterceptReceiverPlugin(request.compilable);

        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        InlineInvokePlugin[] inlineInvokePlugins;
        HistogramInlineInvokePlugin histogramPlugin = null;
        Boolean printTruffleExpansionHistogram = getPolyglotOptionValue(request.options, PrintExpansionHistogram);
        if (printTruffleExpansionHistogram) {
            histogramPlugin = new HistogramInlineInvokePlugin(request.graph);
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin, histogramPlugin};
        } else {
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin};
        }

        SourceLanguagePositionProvider sourceLanguagePosition = new TruffleSourceLanguagePositionProvider(request.inliningPlan);
        PEGraphDecoder decoder = createGraphDecoder(request, loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin,
                        nodePlugins,
                        sourceLanguagePosition, graphCache);
        decoder.decode(request.graph.method(), request.graph.isSubstitution(), request.graph.trackNodeSourcePosition());

        if (printTruffleExpansionHistogram) {
            histogramPlugin.print(request.compilable);
        }
    }

    protected GraphBuilderConfiguration createGraphBuilderConfig(GraphBuilderConfiguration config, boolean canDelayIntrinsification) {
        GraphBuilderConfiguration newConfig = config.copy();
        InvocationPlugins invocationPlugins = newConfig.getPlugins().getInvocationPlugins();
        registerTruffleInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        return newConfig;
    }

    protected NodePlugin[] createNodePlugins(Plugins plugins) {
        return plugins.getNodePlugins();
    }

    protected void registerTruffleInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, canDelayIntrinsification, providers, knownTruffleTypes);
        for (TruffleInvocationPluginProvider p : GraalServices.load(TruffleInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(providers, architecture, invocationPlugins, canDelayIntrinsification);
        }
    }

    protected InvocationPlugins createDecodingInvocationPlugins(Plugins parent) {
        @SuppressWarnings("hiding")
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins(parent.getInvocationPlugins());
        registerTruffleInvocationPlugins(decodingInvocationPlugins, false);
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    private static final TimerKey PartialEvaluationTimer = DebugContext.timer("PartialEvaluation").doc("Time spent in partial evaluation.");

    @SuppressWarnings({"try", "unused"})
    private void fastPartialEvaluation(Request request, PerformanceInformationHandler handler) {
        try (DebugCloseable a = PartialEvaluationTimer.start(request.debug)) {
            agnosticInliningOrGraphPE(request);
        }
        request.debug.dump(DebugContext.BASIC_LEVEL, request.graph, "After Partial Evaluation");

        request.graph.maybeCompress();

        // Perform deoptimize to guard conversion.
        new ConvertDeoptimizeToGuardPhase().apply(request.graph, request.highTierContext);

        for (MethodCallTargetNode methodCallTargetNode : request.graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (methodCallTargetNode.invoke().useForInlining()) {
                StructuredGraph inlineGraph = providers.getReplacements().getSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci(),
                                request.graph.trackNodeSourcePosition(),
                                methodCallTargetNode.asNode().getNodeSourcePosition(), request.debug.getOptions());
                if (inlineGraph != null) {
                    InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
                }
            }
        }

        // Perform conditional elimination.
        new ConditionalEliminationPhase(false).apply(request.graph, request.highTierContext);

        canonicalizer.apply(request.graph, request.highTierContext);

        // Do single partial escape and canonicalization pass.
        try (DebugContext.Scope pe = request.debug.scope("TrufflePartialEscape", request.graph)) {
            new PartialEscapePhase(getPolyglotOptionValue(request.options, IterativePartialEscape), canonicalizer, request.graph.getOptions()).apply(request.graph, request.highTierContext);
        } catch (Throwable t) {
            request.debug.handle(t);
        }

        // recompute loop frequencies now that BranchProbabilities have had time to canonicalize
        ComputeLoopFrequenciesClosure.compute(request.graph);

        applyInstrumentationPhases(request);

        request.graph.maybeCompress();

        handler.reportPerformanceWarnings(request.compilable, request.graph);
    }

    private void agnosticInliningOrGraphPE(Request request) {
        if (getPolyglotOptionValue(request.options, LanguageAgnosticInlining)) {
            AgnosticInliningPhase agnosticInlining = new AgnosticInliningPhase(this, request);
            agnosticInlining.apply(request.graph, providers);
        } else {
            final PEInliningPlanInvokePlugin plugin = new PEInliningPlanInvokePlugin(request.options, request.inliningPlan, request.graph);
            doGraphPE(request, plugin, EconomicMap.create());
        }
        removeInlineTokenNodes(request.graph);
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

    private static void postPartialEvaluation(OptionValues options, final StructuredGraph graph) {
        NeverPartOfCompilationNode.verifyNotFoundIn(graph);
        for (AllowMaterializeNode materializeNode : graph.getNodes(AllowMaterializeNode.TYPE).snapshot()) {
            materializeNode.replaceAtUsages(materializeNode.getFrame());
            graph.removeFixed(materializeNode);
        }
        TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
        for (VirtualObjectNode virtualObjectNode : graph.getNodes(VirtualObjectNode.TYPE)) {
            if (virtualObjectNode instanceof VirtualInstanceNode) {
                VirtualInstanceNode virtualInstanceNode = (VirtualInstanceNode) virtualObjectNode;
                ResolvedJavaType type = virtualInstanceNode.type();
                if (rt.isValueType(type)) {
                    virtualInstanceNode.setIdentity(false);
                }
            }
        }

        if (!getPolyglotOptionValue(options, InlineAcrossTruffleBoundary)) {
            // Do not inline across Truffle boundaries.
            for (MethodCallTargetNode mct : graph.getNodes(MethodCallTargetNode.TYPE)) {
                TruffleCompilerRuntime.InlineKind inlineKind = rt.getInlineKind(mct.targetMethod(), false);
                if (!inlineKind.allowsInlining()) {
                    mct.invoke().setUseForInlining(false);
                }
            }
        }
    }

    private static TruffleInliningPlan.Decision getDecision(TruffleInliningPlan inlining, JavaConstant callNode) {
        TruffleInliningPlan.Decision decision = inlining.findDecision(callNode);
        if (decision == null) {
            JavaConstant target = TruffleCompilerRuntime.getRuntime().getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportDecisionIsNull(target, callNode);
        } else if (!decision.isTargetStable()) {
            JavaConstant target = TruffleCompilerRuntime.getRuntime().getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportCallTargetChanged(target, callNode, decision);
            return null;
        }
        return decision;
    }

    // TODO GR-22187 Move out of PE
    public static final class PerformanceInformationHandler implements Closeable {

        private static final ThreadLocal<PerformanceInformationHandler> instance = new ThreadLocal<>();
        private final OptionValues options;
        private Set<PerformanceWarningKind> warningKinds = EnumSet.noneOf(PerformanceWarningKind.class);

        private PerformanceInformationHandler(OptionValues options) {
            this.options = options;
        }

        private void addWarning(PerformanceWarningKind warningKind) {
            warningKinds.add(warningKind);
        }

        private Set<PerformanceWarningKind> getWarnings() {
            return warningKinds;
        }

        @Override
        public void close() {
            assert instance.get() != null : "No PerformanceInformationHandler installed";
            instance.remove();
        }

        static PerformanceInformationHandler install(OptionValues options) {
            assert instance.get() == null : "PerformanceInformationHandler already installed";
            PerformanceInformationHandler handler = new PerformanceInformationHandler(options);
            instance.set(handler);
            return handler;
        }

        public static boolean isWarningEnabled(PerformanceWarningKind warningKind) {
            PerformanceInformationHandler handler = instance.get();
            return getPolyglotOptionValue(handler.options, TracePerformanceWarnings).contains(warningKind) ||
                            getPolyglotOptionValue(handler.options, PerformanceWarningsAreFatal).contains(warningKind) ||
                            getPolyglotOptionValue(handler.options, TreatPerformanceWarningsAsErrors).contains(warningKind);
        }

        public static void logPerformanceWarning(PerformanceWarningKind warningKind, String callTargetName, List<? extends Node> locations, String details,
                        Map<String, Object> properties) {
            PerformanceInformationHandler handler = instance.get();
            handler.addWarning(warningKind);
            logPerformanceWarningImpl(callTargetName, "perf warn", details, properties);
            handler.logPerformanceStackTrace(locations);
        }

        private static void logInliningWarning(String callTargetName, String details, Map<String, Object> properties) {
            logPerformanceWarningImpl(callTargetName, "inlining warn", details, properties);
        }

        private static void logPerformanceInfo(String callTargetName, List<? extends Node> locations, String details, Map<String, Object> properties) {
            logPerformanceWarningImpl(callTargetName, "perf info", details, properties);
            instance.get().logPerformanceStackTrace(locations);
        }

        private static void logPerformanceWarningImpl(String callTargetName, String msg, String details, Map<String, Object> properties) {
            TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
            runtime.logEvent(0, msg, String.format("%-60s|%s", callTargetName, details), properties);
        }

        private void logPerformanceStackTrace(List<? extends Node> locations) {
            if (locations == null || locations.isEmpty()) {
                return;
            }
            int limit = getPolyglotOptionValue(options, TraceStackTraceLimit); // TODO
            if (limit <= 0) {
                return;
            }

            EconomicMap<String, List<Node>> groupedByStackTrace = EconomicMap.create(Equivalence.DEFAULT);
            for (Node location : locations) {
                StackTraceElement[] stackTrace = GraphUtil.approxSourceStackTraceElement(location);
                StringBuilder sb = new StringBuilder();
                String indent = "    ";
                for (int i = 0; i < stackTrace.length && i < limit; i++) {
                    if (i != 0) {
                        sb.append('\n');
                    }
                    sb.append(indent).append("at ").append(stackTrace[i]);
                }
                if (stackTrace.length > limit) {
                    sb.append('\n').append(indent).append("...");
                }
                String stackTraceAsString = sb.toString();
                if (!groupedByStackTrace.containsKey(stackTraceAsString)) {
                    groupedByStackTrace.put(stackTraceAsString, new ArrayList<>());
                }
                groupedByStackTrace.get(stackTraceAsString).add(location);
            }
            MapCursor<String, List<Node>> entry = groupedByStackTrace.getEntries();
            while (entry.advance()) {
                String stackTrace = entry.getKey();
                List<Node> locationGroup = entry.getValue();
                TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
                if (stackTrace.isEmpty()) {
                    runtime.log(String.format("  No stack trace available for %s.", locationGroup));
                } else {
                    runtime.log(String.format("  Approximated stack trace for %s:", locationGroup));
                    runtime.log(stackTrace);
                }
            }
        }

        @SuppressWarnings("try")
        void reportPerformanceWarnings(CompilableTruffleAST target, StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            ArrayList<ValueNode> warnings = new ArrayList<>();
            if (isWarningEnabled(PerformanceWarningKind.VIRTUAL_RUNTIME_CALL)) {
                for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    if (call.targetMethod().isNative()) {
                        continue; // native methods cannot be inlined
                    }
                    TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
                    if (runtime.getInlineKind(call.targetMethod(), true).allowsInlining()) {
                        logPerformanceWarning(PerformanceWarningKind.VIRTUAL_RUNTIME_CALL, target.getName(), Arrays.asList(call),
                                        String.format("Partial evaluation could not inline the virtual runtime call %s to %s (%s).",
                                                        call.invokeKind(),
                                                        call.targetMethod(),
                                                        call),
                                        null);
                        warnings.add(call);
                    }
                }
            }
            if (isWarningEnabled(PerformanceWarningKind.VIRTUAL_INSTANCEOF)) {
                EconomicMap<ResolvedJavaType, ArrayList<ValueNode>> groupedByType = EconomicMap.create(Equivalence.DEFAULT);
                for (InstanceOfNode instanceOf : graph.getNodes().filter(InstanceOfNode.class)) {
                    if (!instanceOf.type().isExact()) {
                        ResolvedJavaType type = instanceOf.type().getType();
                        if (isSecondaryType(type)) {
                            warnings.add(instanceOf);
                            if (!groupedByType.containsKey(type)) {
                                groupedByType.put(type, new ArrayList<>());
                            }
                            groupedByType.get(type).add(instanceOf);
                        }
                    }
                }
                MapCursor<ResolvedJavaType, ArrayList<ValueNode>> entry = groupedByType.getEntries();
                while (entry.advance()) {
                    ResolvedJavaType type = entry.getKey();
                    String reason = "Partial evaluation could not resolve virtual instanceof to an exact type due to: " +
                                    String.format(type.isInterface() ? "interface type check: %s" : "too deep in class hierarchy: %s", type);
                    logPerformanceInfo(target.getName(), entry.getValue(), reason, Collections.singletonMap("Nodes", entry.getValue()));
                }
            }

            if (debug.areScopesEnabled() && !warnings.isEmpty()) {
                try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                    debug.dump(DebugContext.BASIC_LEVEL, graph, "performance warnings %s", warnings);
                } catch (Throwable t) {
                    debug.handle(t);
                }
            }

            if (!Collections.disjoint(getWarnings(), getPolyglotOptionValue(options, PerformanceWarningsAreFatal))) { // TODO
                throw new AssertionError("Performance warning detected and is fatal.");
            }
            if (!Collections.disjoint(getWarnings(), getPolyglotOptionValue(options, TreatPerformanceWarningsAsErrors))) {
                throw new AssertionError("Performance warning detected and is treated as a compilation error.");
            }
        }

        /**
         * On HotSpot, a type check against a class that is at a depth <= 8 in the class hierarchy
         * (including Object) is just one extra memory load.
         */
        private static boolean isPrimarySupertype(ResolvedJavaType type) {
            if (type.isInterface()) {
                return false;
            }
            ResolvedJavaType supr = type;
            int depth = 0;
            while (supr != null) {
                depth++;
                supr = supr.getSuperclass();
            }
            return depth <= 8;
        }

        private static boolean isSecondaryType(ResolvedJavaType type) {
            return !isPrimarySupertype(type);
        }

        static void reportDecisionIsNull(JavaConstant target, JavaConstant callNode) {
            if (TruffleCompilerOptions.getPolyglotOptionValue(instance.get().options, TraceInlining)) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("callNode", callNode.toValueString());
                logInliningWarning(target.toValueString(), "A direct call within the Truffle AST is not reachable anymore. Call node could not be inlined.", properties);
            }
        }

        static void reportCallTargetChanged(JavaConstant target, JavaConstant callNode, TruffleInliningPlan.Decision decision) {
            if (TruffleCompilerOptions.getPolyglotOptionValue(instance.get().options, TraceInlining)) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("originalTarget", decision.getTargetName());
                properties.put("callNode", callNode.toValueString());
                logInliningWarning(target.toValueString(), "CallTarget changed during compilation. Call node could not be inlined.", properties);
            }
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
    }
}
