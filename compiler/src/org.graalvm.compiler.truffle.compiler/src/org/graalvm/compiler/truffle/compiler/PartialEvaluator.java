/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.PrintTruffleExpansionHistogram;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTrufflePerformanceWarnings;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTruffleStackTraceLimit;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleFunctionInlining;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInstrumentBranches;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleIterativePartialEscape;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TrufflePerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.CachingPEGraphDecoder;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.debug.HistogramInlineInvokePlugin;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentBranchesPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentTruffleBoundariesPhase;
import org.graalvm.compiler.truffle.compiler.phases.VerifyFrameDoesNotEscapePhase;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public abstract class PartialEvaluator {

    protected final Providers providers;
    protected final Architecture architecture;
    private final CanonicalizerPhase canonicalizer;
    private final SnippetReflectionProvider snippetReflection;
    private final ResolvedJavaMethod callDirectMethod;
    private final ResolvedJavaMethod callInlinedMethod;
    private final ResolvedJavaMethod callSiteProxyMethod;
    private final ResolvedJavaMethod callRootMethod;
    private final GraphBuilderConfiguration configForParsing;
    private final InvocationPlugins decodingInvocationPlugins;
    private final NodePlugin[] nodePlugins;
    private final KnownTruffleTypes knownTruffleTypes;

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
        this.canonicalizer = new CanonicalizerPhase();
        this.snippetReflection = snippetReflection;
        this.knownTruffleTypes = knownFields;

        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        final MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        ResolvedJavaMethod[] methods = type.getDeclaredMethods();
        this.callDirectMethod = findRequiredMethod(type, methods, "callDirect", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callInlinedMethod = findRequiredMethod(type, methods, "callInlined", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callRootMethod = findRequiredMethod(type, methods, "callRoot", "([Ljava/lang/Object;)Ljava/lang/Object;");

        type = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode");
        this.callSiteProxyMethod = findRequiredMethod(type, type.getDeclaredMethods(), "callProxy",
                        "(Lcom/oracle/truffle/api/nodes/Node;Lcom/oracle/truffle/api/CallTarget;[Ljava/lang/Object;Z)Ljava/lang/Object;");

        this.configForParsing = createGraphBuilderConfig(configForRoot, true);
        this.decodingInvocationPlugins = createDecodingInvocationPlugins(configForRoot.getPlugins());
        this.nodePlugins = createNodePlugins(configForRoot.getPlugins());
    }

    /**
     * Gets the instrumentation manager associated with this compiler, creating it first if
     * necessary. Each compiler instance has its own instrumentation manager.
     */
    public final InstrumentPhase.Instrumentation getInstrumentation() {
        if (instrumentation == null) {
            synchronized (this) {
                if (instrumentation == null) {
                    OptionValues options = TruffleCompilerOptions.getOptions();
                    long[] accessTable = new long[TruffleCompilerOptions.TruffleInstrumentationTableSize.getValue(options)];
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

    public Providers getProviders() {
        return providers;
    }

    public GraphBuilderConfiguration getConfigForParsing() {
        return configForParsing;
    }

    public KnownTruffleTypes getKnownTruffleTypes() {
        return knownTruffleTypes;
    }

    public ResolvedJavaMethod[] getCompilationRootMethods() {
        return new ResolvedJavaMethod[]{callRootMethod, callInlinedMethod};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callSiteProxyMethod, callDirectMethod};
    }

    @SuppressWarnings("try")
    public StructuredGraph createGraph(DebugContext debug, final CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan,
                    AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, SpeculationLog log, Cancellable cancellable) {

        String name = compilable.toString();
        OptionValues options = TruffleCompilerOptions.getOptions();
        ResolvedJavaMethod rootMethod = rootForCallTarget(compilable);
        // @formatter:off
        StructuredGraph.Builder builder = new StructuredGraph.Builder(options, debug, allowAssumptions).
                        name(name).
                        method(rootMethod).
                        speculationLog(log).
                        compilationId(compilationId).
                        cancellable(cancellable);
        // @formatter:on
        builder = customizeStructuredGraphBuilder(builder);
        final StructuredGraph graph = builder.build();

        try (DebugContext.Scope s = debug.scope("CreateGraph", graph);
                        Indent indent = debug.logAndIndent("createGraph %s", graph);) {

            PhaseContext baseContext = new PhaseContext(providers);
            HighTierContext tierContext = new HighTierContext(providers, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

            fastPartialEvaluation(compilable, inliningPlan, graph, baseContext, tierContext);

            if (cancellable != null && cancellable.isCancelled()) {
                return null;
            }

            new VerifyFrameDoesNotEscapePhase().apply(graph, false);
            postPartialEvaluation(graph);

        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return graph;
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
        return callRootMethod;
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

    private class PEInlineInvokePlugin implements InlineInvokePlugin {

        private Deque<TruffleInliningPlan> inlining;
        private JavaConstant lastDirectCallNode;

        PEInlineInvokePlugin(TruffleInliningPlan inlining) {
            this.inlining = new ArrayDeque<>();
            this.inlining.push(inlining);
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            InlineInfo inlineInfo = rt.getInlineInfo(original, true);
            if (!inlineInfo.allowsInlining()) {
                return inlineInfo;
            }
            assert !builder.parsingIntrinsic();

            if (TruffleCompilerOptions.getValue(TruffleFunctionInlining)) {
                if (original.equals(callSiteProxyMethod)) {
                    ValueNode arg0 = arguments[0];
                    if (!arg0.isConstant()) {
                        GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
                    }

                    lastDirectCallNode = (JavaConstant) arg0.asConstant();
                } else if (original.equals(callDirectMethod)) {
                    TruffleInliningPlan.Decision decision = getDecision(inlining.peek(), lastDirectCallNode);
                    lastDirectCallNode = null;
                    if (decision != null && decision.shouldInline()) {
                        inlining.push(decision);
                        JavaConstant assumption = decision.getNodeRewritingAssumption();
                        builder.getAssumptions().record(new TruffleAssumption(assumption));
                        return createStandardInlineInfo(callInlinedMethod);
                    }
                }
            }

            return inlineInfo;
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod inlinedTargetMethod) {
            if (inlinedTargetMethod.equals(callInlinedMethod)) {
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
            return inliningPlan.getPosition(node);
        }
    }

    private class ParsingInlineInvokePlugin implements InlineInvokePlugin {

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

            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            InlineInfo inlineInfo = rt.getInlineInfo(original, true);
            if (!inlineInfo.allowsInlining()) {
                return inlineInfo;
            }
            if (original.equals(callSiteProxyMethod) || original.equals(callDirectMethod)) {
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
            return rt.getLoopExplosionKind(method);
        }
    }

    @SuppressWarnings("unused")
    protected PEGraphDecoder createGraphDecoder(StructuredGraph graph, final HighTierContext tierContext, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin, NodePlugin[] nodePluginList, ResolvedJavaMethod callInlined,
                    SourceLanguagePositionProvider sourceLanguagePositionProvider) {
        final GraphBuilderConfiguration newConfig = configForParsing.copy();
        InvocationPlugins parsingInvocationPlugins = newConfig.getPlugins().getInvocationPlugins();

        Plugins plugins = newConfig.getPlugins();
        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        plugins.clearInlineInvokePlugins();
        plugins.appendInlineInvokePlugin(replacements);
        plugins.appendInlineInvokePlugin(new ParsingInlineInvokePlugin(replacements, parsingInvocationPlugins, loopExplosionPlugin));
        if (!TruffleCompilerOptions.getValue(PrintTruffleExpansionHistogram)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        Providers compilationUnitProviders = providers.copyWith(new TruffleConstantFieldProvider(providers.getConstantFieldProvider(), providers.getMetaAccess()));
        return new CachingPEGraphDecoder(architecture, graph, compilationUnitProviders, newConfig, TruffleCompilerImpl.Optimizations, AllowAssumptions.ifNonNull(graph.getAssumptions()),
                        loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin, nodePluginList, callInlined, sourceLanguagePositionProvider);
    }

    protected void doGraphPE(CompilableTruffleAST compilable, StructuredGraph graph, HighTierContext tierContext, TruffleInliningPlan inliningDecision) {
        LoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();
        ParameterPlugin parameterPlugin = new InterceptReceiverPlugin(compilable);

        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        InlineInvokePlugin[] inlineInvokePlugins;
        InlineInvokePlugin inlineInvokePlugin = new PEInlineInvokePlugin(inliningDecision);

        HistogramInlineInvokePlugin histogramPlugin = null;
        if (TruffleCompilerOptions.getValue(PrintTruffleExpansionHistogram)) {
            histogramPlugin = new HistogramInlineInvokePlugin(graph);
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin, histogramPlugin};
        } else {
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin};
        }

        if (configForParsing.trackNodeSourcePosition()) {
            graph.setTrackNodeSourcePosition();
        }
        SourceLanguagePositionProvider sourceLanguagePosition = new TruffleSourceLanguagePositionProvider(inliningDecision);
        PEGraphDecoder decoder = createGraphDecoder(graph, tierContext, loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin, nodePlugins, callInlinedMethod,
                        sourceLanguagePosition);
        decoder.decode(graph.method(), graph.trackNodeSourcePosition());

        if (TruffleCompilerOptions.getValue(PrintTruffleExpansionHistogram)) {
            histogramPlugin.print(compilable);
        }
    }

    protected GraphBuilderConfiguration createGraphBuilderConfig(GraphBuilderConfiguration config, boolean canDelayIntrinsification) {
        GraphBuilderConfiguration newConfig = config.copy();
        InvocationPlugins invocationPlugins = newConfig.getPlugins().getInvocationPlugins();
        registerTruffleInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        boolean mustInstrumentBranches = TruffleCompilerOptions.getValue(TruffleInstrumentBranches) || TruffleCompilerOptions.getValue(TruffleInstrumentBoundaries);
        return newConfig.withNodeSourcePosition(newConfig.trackNodeSourcePosition() || mustInstrumentBranches || TruffleCompilerOptions.getValue(TraceTrufflePerformanceWarnings));
    }

    protected NodePlugin[] createNodePlugins(Plugins plugins) {
        return plugins.getNodePlugins();
    }

    protected void registerTruffleInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, canDelayIntrinsification, providers.getMetaAccess(), constantReflection, knownTruffleTypes);

        for (TruffleInvocationPluginProvider p : GraalServices.load(TruffleInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(providers.getMetaAccess(), invocationPlugins, canDelayIntrinsification, constantReflection);
        }
    }

    protected InvocationPlugins createDecodingInvocationPlugins(Plugins parent) {
        @SuppressWarnings("hiding")
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins(parent.getInvocationPlugins());
        registerTruffleInvocationPlugins(decodingInvocationPlugins, false);
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    @SuppressWarnings({"try", "unused"})
    private void fastPartialEvaluation(CompilableTruffleAST compilable, TruffleInliningPlan inliningDecision, StructuredGraph graph, PhaseContext baseContext, HighTierContext tierContext) {
        DebugContext debug = graph.getDebug();
        doGraphPE(compilable, graph, tierContext, inliningDecision);
        debug.dump(DebugContext.BASIC_LEVEL, graph, "After Partial Evaluation");

        graph.maybeCompress();

        // Perform deoptimize to guard conversion.
        new ConvertDeoptimizeToGuardPhase().apply(graph, tierContext);

        for (MethodCallTargetNode methodCallTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (methodCallTargetNode.invoke().useForInlining()) {
                StructuredGraph inlineGraph = providers.getReplacements().getSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci(), graph.trackNodeSourcePosition(),
                                methodCallTargetNode.asNode().getNodeSourcePosition());
                if (inlineGraph != null) {
                    InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
                }
            }
        }

        // Perform conditional elimination.
        new ConditionalEliminationPhase(false).apply(graph, tierContext);

        canonicalizer.apply(graph, tierContext);

        // Do single partial escape and canonicalization pass.
        try (DebugContext.Scope pe = debug.scope("TrufflePartialEscape", graph)) {
            new PartialEscapePhase(TruffleCompilerOptions.getValue(TruffleIterativePartialEscape), canonicalizer, graph.getOptions()).apply(graph, tierContext);
        } catch (Throwable t) {
            debug.handle(t);
        }

        // recompute loop frequencies now that BranchProbabilities have had time to canonicalize
        ComputeLoopFrequenciesClosure.compute(graph);

        applyInstrumentationPhases(graph, tierContext);

        graph.maybeCompress();

        PerformanceInformationHandler.reportPerformanceWarnings(compilable, graph);
    }

    protected void applyInstrumentationPhases(StructuredGraph graph, HighTierContext tierContext) {
        if (TruffleCompilerOptions.TruffleInstrumentBranches.getValue(graph.getOptions())) {
            new InstrumentBranchesPhase(graph.getOptions(), snippetReflection, getInstrumentation()).apply(graph, tierContext);
        }
        if (TruffleCompilerOptions.TruffleInstrumentBoundaries.getValue(graph.getOptions())) {
            new InstrumentTruffleBoundariesPhase(graph.getOptions(), snippetReflection, getInstrumentation()).apply(graph, tierContext);
        }
    }

    private static void postPartialEvaluation(final StructuredGraph graph) {
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

        if (!TruffleCompilerOptions.getValue(TruffleInlineAcrossTruffleBoundary)) {
            // Do not inline across Truffle boundaries.
            for (MethodCallTargetNode mct : graph.getNodes(MethodCallTargetNode.TYPE)) {
                InlineInfo inlineInfo = rt.getInlineInfo(mct.targetMethod(), false);
                if (!inlineInfo.allowsInlining()) {
                    mct.invoke().setUseForInlining(false);
                }
            }
        }
    }

    private static TruffleInliningPlan.Decision getDecision(TruffleInliningPlan inlining, JavaConstant callNode) {
        TruffleInliningPlan.Decision decision = inlining.findDecision(callNode);
        if (decision == null) {
            JavaConstant target = getRuntime().getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportDecisionIsNull(target, callNode);
        } else if (!decision.isTargetStable()) {
            JavaConstant target = getRuntime().getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportCallTargetChanged(target, callNode, decision);
            return null;
        }
        return decision;
    }

    public static final class PerformanceInformationHandler {

        private static boolean warningSeen = false;

        public static boolean isEnabled() {
            return TruffleCompilerOptions.getValue(TraceTrufflePerformanceWarnings) || TruffleCompilerOptions.getValue(TrufflePerformanceWarningsAreFatal);
        }

        public static void logPerformanceWarning(String callTargetName, List<? extends Node> locations, String details, Map<String, Object> properties) {
            warningSeen = true;
            logPerformanceWarningImpl(callTargetName, "perf warn", details, properties);
            logPerformanceStackTrace(locations);
        }

        private static void logPerformanceInfo(String callTargetName, List<? extends Node> locations, String details, Map<String, Object> properties) {
            logPerformanceWarningImpl(callTargetName, "perf info", details, properties);
            logPerformanceStackTrace(locations);
        }

        private static void logPerformanceWarningImpl(String callTargetName, String msg, String details, Map<String, Object> properties) {
            TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
            runtime.logEvent(0, msg, String.format("%-60s|%s", callTargetName, details), properties);
        }

        private static void logPerformanceStackTrace(List<? extends Node> locations) {
            if (locations == null || locations.isEmpty()) {
                return;
            }
            int limit = TruffleCompilerOptions.getValue(TraceTruffleStackTraceLimit);
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
        static void reportPerformanceWarnings(CompilableTruffleAST target, StructuredGraph graph) {
            if (!isEnabled()) {
                return;
            }
            DebugContext debug = graph.getDebug();
            ArrayList<ValueNode> warnings = new ArrayList<>();
            for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
                if (call.targetMethod().isNative()) {
                    continue; // native methods cannot be inlined
                }
                TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
                if (runtime.getInlineInfo(call.targetMethod(), true).getMethodToInline() != null) {
                    logPerformanceWarning(target.getName(), Arrays.asList(call), String.format("not inlined %s call to %s (%s)", call.invokeKind(), call.targetMethod(), call), null);
                    warnings.add(call);
                }
            }

            EconomicMap<String, ArrayList<ValueNode>> groupedByType = EconomicMap.create(Equivalence.DEFAULT);
            for (InstanceOfNode instanceOf : graph.getNodes().filter(InstanceOfNode.class)) {
                if (!instanceOf.type().isExact()) {
                    warnings.add(instanceOf);
                    String name = instanceOf.type().getType().getName();
                    if (!groupedByType.containsKey(name)) {
                        groupedByType.put(name, new ArrayList<>());
                    }
                    groupedByType.get(name).add(instanceOf);
                }
            }
            MapCursor<String, ArrayList<ValueNode>> entry = groupedByType.getEntries();
            while (entry.advance()) {
                logPerformanceInfo(target.getName(), entry.getValue(), String.format("non-leaf type check: %s", entry.getKey()), Collections.singletonMap("Nodes", entry.getValue()));
            }

            if (debug.areScopesEnabled() && !warnings.isEmpty()) {
                try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                    debug.dump(DebugContext.BASIC_LEVEL, graph, "performance warnings %s", warnings);
                } catch (Throwable t) {
                    debug.handle(t);
                }
            }

            if (warningSeen && TruffleCompilerOptions.getValue(TrufflePerformanceWarningsAreFatal)) {
                warningSeen = false;
                throw new AssertionError("Performance warning detected and is fatal.");
            }
        }

        static void reportDecisionIsNull(JavaConstant target, JavaConstant callNode) {
            if (!isEnabled()) {
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("callNode", callNode.toValueString());
            logPerformanceWarning(target.toValueString(), null, "A direct call within the Truffle AST is not reachable anymore. Call node could not be inlined.", properties);
        }

        static void reportCallTargetChanged(JavaConstant target, JavaConstant callNode, TruffleInliningPlan.Decision decision) {
            if (!isEnabled()) {
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("originalTarget", decision.getTargetName());
            properties.put("callNode", callNode.toValueString());
            logPerformanceWarning(target.toValueString(), null, "CallTarget changed during compilation. Call node could not be inlined.", properties);
        }
    }
}
