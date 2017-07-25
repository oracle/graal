/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.PrintTruffleExpansionHistogram;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTrufflePerformanceWarnings;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleStackTraceLimit;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleFunctionInlining;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBranches;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleIterativePartialEscape;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TrufflePerformanceWarningsAreFatal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure;
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
import org.graalvm.compiler.truffle.debug.AbstractDebugCompilationListener;
import org.graalvm.compiler.truffle.debug.HistogramInlineInvokePlugin;
import org.graalvm.compiler.truffle.nodes.AssumptionValidAssumption;
import org.graalvm.compiler.truffle.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.phases.InstrumentBranchesPhase;
import org.graalvm.compiler.truffle.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.phases.InstrumentTruffleBoundariesPhase;
import org.graalvm.compiler.truffle.phases.VerifyFrameDoesNotEscapePhase;
import org.graalvm.compiler.truffle.substitutions.KnownTruffleFields;
import org.graalvm.compiler.truffle.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.compiler.truffle.substitutions.TruffleInvocationPluginProvider;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;
import org.graalvm.util.MapCursor;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public abstract class PartialEvaluator {

    protected final Providers providers;
    protected final Architecture architecture;
    protected final InstrumentPhase.Instrumentation instrumentation;
    private final CanonicalizerPhase canonicalizer;
    private final SnippetReflectionProvider snippetReflection;
    private final ResolvedJavaMethod callDirectMethod;
    private final ResolvedJavaMethod callInlinedMethod;
    private final ResolvedJavaMethod callSiteProxyMethod;
    private final ResolvedJavaMethod callRootMethod;
    private final GraphBuilderConfiguration configForParsing;
    private final InvocationPlugins decodingInvocationPlugins;
    private final NodePlugin[] nodePlugins;
    private final KnownTruffleFields knownTruffleFields;

    public PartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture,
                    InstrumentPhase.Instrumentation instrumentation, KnownTruffleFields knownFields) {
        this.providers = providers;
        this.architecture = architecture;
        this.canonicalizer = new CanonicalizerPhase();
        this.snippetReflection = snippetReflection;
        this.instrumentation = instrumentation;
        this.knownTruffleFields = knownFields;

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        this.callDirectMethod = metaAccess.lookupJavaMethod(OptimizedCallTarget.getCallDirectMethod());
        this.callInlinedMethod = metaAccess.lookupJavaMethod(OptimizedCallTarget.getCallInlinedMethod());
        this.callSiteProxyMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_NODE_METHOD);

        try {
            callRootMethod = metaAccess.lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("callRoot", Object[].class));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }

        this.configForParsing = createGraphBuilderConfig(configForRoot, true);
        this.decodingInvocationPlugins = createDecodingInvocationPlugins(configForRoot.getPlugins());
        this.nodePlugins = createNodePlugins(configForRoot.getPlugins());
    }

    public Providers getProviders() {
        return providers;
    }

    public GraphBuilderConfiguration getConfigForParsing() {
        return configForParsing;
    }

    public KnownTruffleFields getKnownTruffleFields() {
        return knownTruffleFields;
    }

    public ResolvedJavaMethod[] getCompilationRootMethods() {
        return new ResolvedJavaMethod[]{callRootMethod, callInlinedMethod};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callSiteProxyMethod, callDirectMethod};
    }

    @SuppressWarnings("unused")
    public StructuredGraph createGraph(DebugContext debug, final OptimizedCallTarget callTarget, TruffleInlining inliningDecision, ResolvedJavaMethod rootMethod,
                    AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, CancellableCompileTask task) {
        return createGraph(debug, callTarget, inliningDecision, rootForCallTarget(callTarget), allowAssumptions, compilationId, callTarget.getSpeculationLog(), task);
    }

    public StructuredGraph createGraph(DebugContext debug, final OptimizedCallTarget callTarget, TruffleInlining inliningDecision,
                    AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, CancellableCompileTask task) {
        return createGraph(debug, callTarget, inliningDecision, rootForCallTarget(callTarget), allowAssumptions, compilationId, callTarget.getSpeculationLog(), task);
    }

    @SuppressWarnings("try")
    public StructuredGraph createGraph(DebugContext debug, final OptimizedCallTarget callTarget, TruffleInlining inliningDecision, ResolvedJavaMethod rootMethod,
                    AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, SpeculationLog log, CancellableCompileTask task) {

        String name = callTarget.toString();
        OptionValues options = TruffleCompilerOptions.getOptions();
        // @formatter:off
        final StructuredGraph graph = new StructuredGraph.Builder(options, debug, allowAssumptions).
                        name(name).
                        method(rootMethod).
                        speculationLog(log).
                        compilationId(compilationId).
                        cancellable(task).
                        build();
        // @formatter:on

        try (DebugContext.Scope s = debug.scope("CreateGraph", graph); Indent indent = debug.logAndIndent("createGraph %s", graph)) {

            try (Scope c = debug.scope("TruffleTree")) {
                debug.dump(DebugContext.BASIC_LEVEL, new TruffleTreeDumpHandler.TruffleTreeDump(callTarget), "%s", callTarget);
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            PhaseContext baseContext = new PhaseContext(providers);
            HighTierContext tierContext = new HighTierContext(providers, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

            fastPartialEvaluation(callTarget, inliningDecision, graph, baseContext, tierContext);

            if (task != null && task.isCancelled()) {
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
     * Hook for subclasses: return a customized compilation root for a specific call target.
     *
     * @param callTarget The call target that is being compiled.
     */
    public ResolvedJavaMethod rootForCallTarget(OptimizedCallTarget callTarget) {
        return callRootMethod;
    }

    private class InterceptReceiverPlugin implements ParameterPlugin {

        private final Object receiver;

        InterceptReceiverPlugin(Object receiver) {
            this.receiver = receiver;
        }

        @Override
        public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
            if (index == 0) {
                return ConstantNode.forConstant(snippetReflection.forObject(receiver), providers.getMetaAccess());
            }
            return null;
        }
    }

    private class PEInlineInvokePlugin implements InlineInvokePlugin {

        private Deque<TruffleInlining> inlining;
        private OptimizedDirectCallNode lastDirectCallNode;

        PEInlineInvokePlugin(TruffleInlining inlining) {
            this.inlining = new ArrayDeque<>();
            this.inlining.push(inlining);
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            TruffleBoundary truffleBoundary = original.getAnnotation(TruffleBoundary.class);
            if (truffleBoundary != null) {
                return truffleBoundary.throwsControlFlowException() ? InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION : InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
            assert !builder.parsingIntrinsic();

            if (TruffleCompilerOptions.getValue(TruffleFunctionInlining)) {
                if (original.equals(callSiteProxyMethod)) {
                    ValueNode arg1 = arguments[0];
                    if (!arg1.isConstant()) {
                        GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
                    }

                    Object callNode = snippetReflection.asObject(Object.class, (JavaConstant) arg1.asConstant());
                    if (callNode instanceof OptimizedDirectCallNode) {
                        OptimizedDirectCallNode directCallNode = (OptimizedDirectCallNode) callNode;
                        lastDirectCallNode = directCallNode;
                    }
                } else if (original.equals(callDirectMethod)) {
                    TruffleInliningDecision decision = getDecision(inlining.peek(), lastDirectCallNode);
                    lastDirectCallNode = null;
                    if (decision != null && decision.isInline()) {
                        inlining.push(decision);
                        JavaConstant assumption = snippetReflection.forObject(decision.getTarget().getNodeRewritingAssumption());
                        builder.getAssumptions().record(new AssumptionValidAssumption(assumption));
                        return createStandardInlineInfo(callInlinedMethod);
                    }
                }
            }

            return createStandardInlineInfo(original);
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod inlinedTargetMethod) {
            if (inlinedTargetMethod.equals(callInlinedMethod)) {
                inlining.pop();
            }
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
                    if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
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
            TruffleBoundary truffleBoundary = original.getAnnotation(TruffleBoundary.class);
            if (truffleBoundary != null) {
                return truffleBoundary.throwsControlFlowException() ? InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION : InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            if (original.equals(callSiteProxyMethod) || original.equals(callDirectMethod)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            if (hasMethodHandleArgument(arguments)) {
                /*
                 * We want to inline invokes that have a constant MethodHandle parameter to remove
                 * invokedynamic related calls as early as possible.
                 */
                return createStandardInlineInfo(original);
            }
            return null;
        }
    }

    /**
     * Both Truffle and Graal define an enum with the same elements (the Graal one being strictly
     * larger than the Truffle one), because the two projects do not depend on each other. We
     * convert between the two enums, and ensure they stay in sync.
     */
    static final EnumMap<ExplodeLoop.LoopExplosionKind, LoopExplosionPlugin.LoopExplosionKind> LOOP_EXPLOSION_KIND_MAP;
    static {
        LOOP_EXPLOSION_KIND_MAP = new EnumMap<>(ExplodeLoop.LoopExplosionKind.class);
        for (ExplodeLoop.LoopExplosionKind truffleKind : ExplodeLoop.LoopExplosionKind.values()) {
            LoopExplosionPlugin.LoopExplosionKind graalKind = LoopExplosionPlugin.LoopExplosionKind.valueOf(truffleKind.name());
            JVMCIError.guarantee(graalKind != null, "No match found for Truffle LoopExplosionKind %s", truffleKind.name());
            LOOP_EXPLOSION_KIND_MAP.put(truffleKind, graalKind);
        }
    }

    private class PELoopExplosionPlugin implements LoopExplosionPlugin {

        @SuppressWarnings("deprecation")
        @Override
        public LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method) {
            ExplodeLoop explodeLoop = method.getAnnotation(ExplodeLoop.class);
            if (explodeLoop == null) {
                return LoopExplosionKind.NONE;
            }

            /*
             * Support for the deprecated Truffle property until it is removed in a future Truffle
             * release.
             */
            if (explodeLoop.merge()) {
                return LoopExplosionKind.MERGE_EXPLODE;
            }

            return LOOP_EXPLOSION_KIND_MAP.get(explodeLoop.kind());
        }
    }

    @SuppressWarnings("unused")
    protected PEGraphDecoder createGraphDecoder(StructuredGraph graph, final HighTierContext tierContext, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin, NodePlugin[] nodePluginList) {
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
        return new CachingPEGraphDecoder(architecture, graph, compilationUnitProviders, newConfig, TruffleCompiler.Optimizations, AllowAssumptions.ifNonNull(graph.getAssumptions()),
                        loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin, nodePluginList);
    }

    protected void doGraphPE(OptimizedCallTarget callTarget, StructuredGraph graph, HighTierContext tierContext, TruffleInlining inliningDecision) {
        LoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();
        ParameterPlugin parameterPlugin = new InterceptReceiverPlugin(callTarget);

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

        PEGraphDecoder decoder = createGraphDecoder(graph, tierContext, loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin, nodePlugins);
        decoder.decode(graph.method());

        if (TruffleCompilerOptions.getValue(PrintTruffleExpansionHistogram)) {
            histogramPlugin.print(callTarget);
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
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, canDelayIntrinsification, constantReflection, knownTruffleFields);

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
    private void fastPartialEvaluation(OptimizedCallTarget callTarget, TruffleInlining inliningDecision, StructuredGraph graph, PhaseContext baseContext, HighTierContext tierContext) {
        DebugContext debug = graph.getDebug();
        doGraphPE(callTarget, graph, tierContext, inliningDecision);
        debug.dump(DebugContext.BASIC_LEVEL, graph, "After Partial Evaluation");

        graph.maybeCompress();

        // Perform deoptimize to guard conversion.
        new ConvertDeoptimizeToGuardPhase().apply(graph, tierContext);

        for (MethodCallTargetNode methodCallTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            StructuredGraph inlineGraph = providers.getReplacements().getSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci());
            if (inlineGraph != null) {
                InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
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

        PerformanceInformationHandler.reportPerformanceWarnings(callTarget, graph);
    }

    protected void applyInstrumentationPhases(StructuredGraph graph, HighTierContext tierContext) {
        if (TruffleCompilerOptions.TruffleInstrumentBranches.getValue(graph.getOptions())) {
            new InstrumentBranchesPhase(graph.getOptions(), snippetReflection, instrumentation).apply(graph, tierContext);
        }
        if (TruffleCompilerOptions.TruffleInstrumentBoundaries.getValue(graph.getOptions())) {
            new InstrumentTruffleBoundariesPhase(graph.getOptions(), snippetReflection, instrumentation).apply(graph, tierContext);
        }
    }

    private static void postPartialEvaluation(final StructuredGraph graph) {
        NeverPartOfCompilationNode.verifyNotFoundIn(graph);
        for (AllowMaterializeNode materializeNode : graph.getNodes(AllowMaterializeNode.TYPE).snapshot()) {
            materializeNode.replaceAtUsages(materializeNode.getFrame());
            graph.removeFixed(materializeNode);
        }
        for (VirtualObjectNode virtualObjectNode : graph.getNodes(VirtualObjectNode.TYPE)) {
            if (virtualObjectNode instanceof VirtualInstanceNode) {
                VirtualInstanceNode virtualInstanceNode = (VirtualInstanceNode) virtualObjectNode;
                ResolvedJavaType type = virtualInstanceNode.type();
                if (type.getAnnotation(CompilerDirectives.ValueType.class) != null) {
                    virtualInstanceNode.setIdentity(false);
                }
            }
        }

        if (!TruffleCompilerOptions.getValue(TruffleInlineAcrossTruffleBoundary)) {
            // Do not inline across Truffle boundaries.
            for (MethodCallTargetNode mct : graph.getNodes(MethodCallTargetNode.TYPE)) {
                TruffleBoundary annotation = mct.targetMethod().getAnnotation(TruffleBoundary.class);
                if (annotation != null && !annotation.allowInlining()) {
                    mct.invoke().setUseForInlining(false);
                }
            }
        }
    }

    private static TruffleInliningDecision getDecision(TruffleInlining inlining, OptimizedDirectCallNode callNode) {
        OptimizedCallTarget target = callNode.getCallTarget();
        TruffleInliningDecision decision = inlining.findByCall(callNode);
        if (decision == null) {
            PerformanceInformationHandler.reportDecisionIsNull(target, callNode);
        } else if (decision.getTarget() != decision.getProfile().getCallNode().getCurrentCallTarget()) {
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
            AbstractDebugCompilationListener.log(0, msg, String.format("%-60s|%s", callTargetName, details), properties);
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
                if (stackTrace.isEmpty()) {
                    GraalTruffleRuntime.getRuntime().log(String.format("  No stack trace available for %s.", locationGroup));
                } else {
                    GraalTruffleRuntime.getRuntime().log(String.format("  Approximated stack trace for %s:", locationGroup));
                    GraalTruffleRuntime.getRuntime().log(stackTrace);
                }
            }
        }

        @SuppressWarnings("try")
        static void reportPerformanceWarnings(OptimizedCallTarget target, StructuredGraph graph) {
            if (!isEnabled()) {
                return;
            }
            DebugContext debug = graph.getDebug();
            ArrayList<ValueNode> warnings = new ArrayList<>();
            for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
                if (call.targetMethod().isNative()) {
                    continue; // native methods cannot be inlined
                }
                if (call.targetMethod().getAnnotation(TruffleBoundary.class) == null && call.targetMethod().getAnnotation(TruffleCallBoundary.class) == null) {
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

        static void reportDecisionIsNull(OptimizedCallTarget target, OptimizedDirectCallNode callNode) {
            if (!isEnabled()) {
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("callNode", callNode);
            logPerformanceWarning(target.getName(), null, "A direct call within the Truffle AST is not reachable anymore. Call node could not be inlined.", properties);
        }

        static void reportCallTargetChanged(OptimizedCallTarget target, OptimizedDirectCallNode callNode, TruffleInliningDecision decision) {
            if (!isEnabled()) {
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("originalTarget", decision.getTarget());
            properties.put("callNode", callNode);
            logPerformanceWarning(target.getName(), null, "CallTarget changed during compilation. Call node could not be inlined.", properties);
        }
    }
}
