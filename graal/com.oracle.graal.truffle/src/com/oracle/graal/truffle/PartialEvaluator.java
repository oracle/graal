/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.nodes.StructuredGraph.NO_PROFILING_INFO;
import static com.oracle.graal.truffle.TruffleCompilerOptions.PrintTruffleExpansionHistogram;

import java.lang.invoke.MethodHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.services.Services;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.java.ComputeLoopFrequenciesClosure;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.LoopExplosionPlugin;
import com.oracle.graal.nodes.graphbuilderconf.ParameterPlugin;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.virtual.VirtualInstanceNode;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.ConvertDeoptimizeToGuardPhase;
import com.oracle.graal.phases.common.DominatorConditionalEliminationPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.CachingPEGraphDecoder;
import com.oracle.graal.replacements.InlineDuringParsingPlugin;
import com.oracle.graal.replacements.PEGraphDecoder;
import com.oracle.graal.replacements.ReplacementsImpl;
import com.oracle.graal.truffle.debug.AbstractDebugCompilationListener;
import com.oracle.graal.truffle.debug.HistogramInlineInvokePlugin;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.graal.truffle.nodes.asserts.NeverPartOfCompilationNode;
import com.oracle.graal.truffle.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.truffle.nodes.frame.NewFrameNode.VirtualOnlyInstanceNode;
import com.oracle.graal.truffle.phases.VerifyFrameDoesNotEscapePhase;
import com.oracle.graal.truffle.substitutions.TruffleGraphBuilderPlugins;
import com.oracle.graal.truffle.substitutions.TruffleInvocationPluginProvider;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public class PartialEvaluator {

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

    public PartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture) {
        this.providers = providers;
        this.architecture = architecture;
        this.canonicalizer = new CanonicalizerPhase();
        this.snippetReflection = snippetReflection;
        this.callDirectMethod = providers.getMetaAccess().lookupJavaMethod(OptimizedCallTarget.getCallDirectMethod());
        this.callInlinedMethod = providers.getMetaAccess().lookupJavaMethod(OptimizedCallTarget.getCallInlinedMethod());
        this.callSiteProxyMethod = providers.getMetaAccess().lookupJavaMethod(GraalFrameInstance.CALL_NODE_METHOD);

        try {
            callRootMethod = providers.getMetaAccess().lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("callRoot", Object[].class));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }

        this.configForParsing = createGraphBuilderConfig(configForRoot, true);
        this.decodingInvocationPlugins = createDecodingInvocationPlugins();
    }

    public Providers getProviders() {
        return providers;
    }

    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public GraphBuilderConfiguration getConfigForParsing() {
        return configForParsing;
    }

    public ResolvedJavaMethod[] getCompilationRootMethods() {
        return new ResolvedJavaMethod[]{callRootMethod, callInlinedMethod};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callSiteProxyMethod, callDirectMethod};
    }

    @SuppressWarnings("try")
    public StructuredGraph createGraph(final OptimizedCallTarget callTarget, AllowAssumptions allowAssumptions) {
        try (Scope c = Debug.scope("TruffleTree")) {
            Debug.dump(callTarget, "%s", callTarget);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        final StructuredGraph graph = new StructuredGraph(callTarget.toString(), callRootMethod, allowAssumptions, callTarget.getSpeculationLog(), NO_PROFILING_INFO);
        assert graph != null : "no graph for root method";

        try (Scope s = Debug.scope("CreateGraph", graph); Indent indent = Debug.logAndIndent("createGraph %s", graph)) {

            PhaseContext baseContext = new PhaseContext(providers);
            HighTierContext tierContext = new HighTierContext(providers, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

            fastPartialEvaluation(callTarget, graph, baseContext, tierContext);

            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            new VerifyFrameDoesNotEscapePhase().apply(graph, false);
            postPartialEvaluation(graph);

        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        return graph;
    }

    private class InterceptReceiverPlugin implements ParameterPlugin {

        private final Object receiver;

        InterceptReceiverPlugin(Object receiver) {
            this.receiver = receiver;
        }

        public FloatingNode interceptParameter(GraphBuilderContext b, int index, Stamp stamp) {
            if (index == 0) {
                return ConstantNode.forConstant(snippetReflection.forObject(receiver), providers.getMetaAccess());
            }
            return null;
        }
    }

    private class PEInlineInvokePlugin implements InlineInvokePlugin {

        private Deque<TruffleInlining> inlining;
        private OptimizedDirectCallNode lastDirectCallNode;
        private final ReplacementsImpl replacements;

        PEInlineInvokePlugin(TruffleInlining inlining, ReplacementsImpl replacements) {
            this.inlining = new ArrayDeque<>();
            this.inlining.push(inlining);
            this.replacements = replacements;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments, JavaType returnType) {
            TruffleBoundary truffleBoundary = original.getAnnotation(TruffleBoundary.class);
            if (truffleBoundary != null) {
                return truffleBoundary.throwsControlFlowException() ? InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION : InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
            if (replacements.hasSubstitution(original, builder.bci())) {
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
            assert !builder.parsingIntrinsic();

            if (TruffleCompilerOptions.TruffleFunctionInlining.getValue()) {
                if (original.equals(callSiteProxyMethod)) {
                    ValueNode arg1 = arguments[0];
                    if (!arg1.isConstant()) {
                        JVMCIError.shouldNotReachHere("The direct call node does not resolve to a constant!");
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
                        builder.getAssumptions().record(new AssumptionValidAssumption((OptimizedAssumption) decision.getTarget().getNodeRewritingAssumption()));
                        return new InlineInfo(callInlinedMethod, false);
                    }
                }
            }

            return new InlineInfo(original, false);
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
                    if (constant.getJavaKind() == JavaKind.Object && snippetReflection.asObject(MethodHandle.class, constant) != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments, JavaType returnType) {
            if (invocationPlugins.lookupInvocation(original) != null) {
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            } else if (loopExplosionPlugin.shouldExplodeLoops(original)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            TruffleBoundary truffleBoundary = original.getAnnotation(TruffleBoundary.class);
            if (truffleBoundary != null) {
                return truffleBoundary.throwsControlFlowException() ? InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION : InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
            if (replacements.hasSubstitution(original, builder.bci())) {
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            if (original.equals(callSiteProxyMethod) || original.equals(callDirectMethod)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            if (hasMethodHandleArgument(arguments)) {
                /*
                 * We want to inline invokes that have a constant MethodHandle parameter to remove
                 * invokedynamic related calls as early as possible.
                 */
                return new InlineInfo(original, false);
            }
            return null;
        }
    }

    private class PELoopExplosionPlugin implements LoopExplosionPlugin {

        public boolean shouldExplodeLoops(ResolvedJavaMethod method) {
            return method.getAnnotation(ExplodeLoop.class) != null;
        }

        public boolean shouldMergeExplosions(ResolvedJavaMethod method) {
            ExplodeLoop explodeLoop = method.getAnnotation(ExplodeLoop.class);
            if (explodeLoop != null) {
                return explodeLoop.merge();
            }
            return false;
        }

    }

    protected PEGraphDecoder createGraphDecoder(StructuredGraph graph) {
        GraphBuilderConfiguration newConfig = configForParsing.copy();
        InvocationPlugins parsingInvocationPlugins = newConfig.getPlugins().getInvocationPlugins();

        LoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();

        Plugins plugins = newConfig.getPlugins();
        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        plugins.clearInlineInvokePlugins();
        plugins.appendInlineInvokePlugin(replacements);
        plugins.appendInlineInvokePlugin(new ParsingInlineInvokePlugin(replacements, parsingInvocationPlugins, loopExplosionPlugin));
        if (!PrintTruffleExpansionHistogram.getValue()) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        return new CachingPEGraphDecoder(providers, newConfig, TruffleCompiler.Optimizations, AllowAssumptions.from(graph.getAssumptions() != null), architecture);
    }

    protected void doGraphPE(OptimizedCallTarget callTarget, StructuredGraph graph) {
        callTarget.setInlining(new TruffleInlining(callTarget, new DefaultInliningPolicy()));

        PEGraphDecoder decoder = createGraphDecoder(graph);

        LoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();
        ParameterPlugin parameterPlugin = new InterceptReceiverPlugin(callTarget);

        ReplacementsImpl replacements = (ReplacementsImpl) providers.getReplacements();
        InlineInvokePlugin[] inlineInvokePlugins;
        InlineInvokePlugin inlineInvokePlugin = new PEInlineInvokePlugin(callTarget.getInlining(), replacements);

        HistogramInlineInvokePlugin histogramPlugin = null;
        if (PrintTruffleExpansionHistogram.getValue()) {
            histogramPlugin = new HistogramInlineInvokePlugin(graph);
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin, histogramPlugin};
        } else {
            inlineInvokePlugins = new InlineInvokePlugin[]{replacements, inlineInvokePlugin};
        }

        decoder.decode(graph, graph.method(), loopExplosionPlugin, decodingInvocationPlugins, inlineInvokePlugins, parameterPlugin);

        if (PrintTruffleExpansionHistogram.getValue()) {
            histogramPlugin.print(callTarget);
        }
    }

    protected GraphBuilderConfiguration createGraphBuilderConfig(GraphBuilderConfiguration config, boolean canDelayIntrinsification) {
        GraphBuilderConfiguration newConfig = config.copy();
        InvocationPlugins invocationPlugins = newConfig.getPlugins().getInvocationPlugins();
        registerTruffleInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        invocationPlugins.closeRegistration();
        return newConfig;
    }

    protected void registerTruffleInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        TruffleGraphBuilderPlugins.registerInvocationPlugins(providers.getMetaAccess(), invocationPlugins, canDelayIntrinsification, snippetReflection);

        for (TruffleInvocationPluginProvider p : Services.load(TruffleInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(providers.getMetaAccess(), invocationPlugins, canDelayIntrinsification, snippetReflection);
        }
    }

    protected InvocationPlugins createDecodingInvocationPlugins() {
        @SuppressWarnings("hiding")
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins(providers.getMetaAccess());
        registerTruffleInvocationPlugins(decodingInvocationPlugins, false);
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    @SuppressWarnings({"try", "unused"})
    private void fastPartialEvaluation(OptimizedCallTarget callTarget, StructuredGraph graph, PhaseContext baseContext, HighTierContext tierContext) {
        doGraphPE(callTarget, graph);
        Debug.dump(graph, "After FastPE");

        graph.maybeCompress();

        // Perform deoptimize to guard conversion.
        new ConvertDeoptimizeToGuardPhase().apply(graph, tierContext);

        for (MethodCallTargetNode methodCallTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            StructuredGraph inlineGraph = providers.getReplacements().getSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci());
            if (inlineGraph != null) {
                InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, null);
            }
        }

        // Perform conditional elimination.
        new DominatorConditionalEliminationPhase(false).apply(graph);

        canonicalizer.apply(graph, tierContext);

        // Do single partial escape and canonicalization pass.
        try (Scope pe = Debug.scope("TrufflePartialEscape", graph)) {
            new PartialEscapePhase(TruffleCompilerOptions.TruffleIterativePartialEscape.getValue(), canonicalizer).apply(graph, tierContext);
        } catch (Throwable t) {
            Debug.handle(t);
        }

        // recompute loop frequencies now that BranchProbabilities have had time to canonicalize
        ComputeLoopFrequenciesClosure.compute(graph);

        graph.maybeCompress();

        if (TruffleCompilerOptions.TraceTrufflePerformanceWarnings.getValue()) {
            reportPerformanceWarnings(callTarget, graph);
        }
    }

    @SuppressWarnings("try")
    private static void reportPerformanceWarnings(OptimizedCallTarget target, StructuredGraph graph) {
        ArrayList<ValueNode> warnings = new ArrayList<>();
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (call.targetMethod().isNative()) {
                continue; // native methods cannot be inlined
            }
            if (call.targetMethod().getAnnotation(TruffleBoundary.class) == null && call.targetMethod().getAnnotation(TruffleCallBoundary.class) == null) {
                logPerformanceWarning(target, String.format("not inlined %s call to %s (%s)", call.invokeKind(), call.targetMethod(), call), null);
                warnings.add(call);
            }
        }

        HashMap<String, ArrayList<ValueNode>> groupedByType;
        groupedByType = new HashMap<>();
        for (CheckCastNode cast : graph.getNodes().filter(CheckCastNode.class)) {
            if (!cast.type().isExactType()) {
                warnings.add(cast);
                groupedByType.putIfAbsent(cast.type().getType().getName(), new ArrayList<>());
                groupedByType.get(cast.type().getType().getName()).add(cast);
            }
        }
        for (Map.Entry<String, ArrayList<ValueNode>> entry : groupedByType.entrySet()) {
            logPerformanceInfo(target, String.format("non-leaf type checkcast: %s", entry.getKey()), Collections.singletonMap("Nodes", entry.getValue()));
        }

        groupedByType = new HashMap<>();
        for (InstanceOfNode instanceOf : graph.getNodes().filter(InstanceOfNode.class)) {
            if (!instanceOf.type().isExactType()) {
                warnings.add(instanceOf);
                groupedByType.putIfAbsent(instanceOf.type().getType().getName(), new ArrayList<>());
                groupedByType.get(instanceOf.type().getType().getName()).add(instanceOf);
            }
        }
        for (Map.Entry<String, ArrayList<ValueNode>> entry : groupedByType.entrySet()) {
            logPerformanceInfo(target, String.format("non-leaf type instanceof: %s", entry.getKey()), Collections.singletonMap("Nodes", entry.getValue()));
        }

        if (Debug.isEnabled() && !warnings.isEmpty()) {
            try (Scope s = Debug.scope("TrufflePerformanceWarnings", graph)) {
                Debug.dump(graph, "performance warnings %s", warnings);
            } catch (Throwable t) {
                Debug.handle(t);
            }
        }
    }

    private static void postPartialEvaluation(final StructuredGraph graph) {
        NeverPartOfCompilationNode.verifyNotFoundIn(graph);
        for (MaterializeFrameNode materializeNode : graph.getNodes(MaterializeFrameNode.TYPE).snapshot()) {
            materializeNode.replaceAtUsages(materializeNode.getFrame());
            graph.removeFixed(materializeNode);
        }
        for (VirtualObjectNode virtualObjectNode : graph.getNodes(VirtualObjectNode.TYPE)) {
            if (virtualObjectNode instanceof VirtualOnlyInstanceNode) {
                VirtualOnlyInstanceNode virtualOnlyInstanceNode = (VirtualOnlyInstanceNode) virtualObjectNode;
                virtualOnlyInstanceNode.setAllowMaterialization(true);
            } else if (virtualObjectNode instanceof VirtualInstanceNode) {
                VirtualInstanceNode virtualInstanceNode = (VirtualInstanceNode) virtualObjectNode;
                ResolvedJavaType type = virtualInstanceNode.type();
                if (type.getAnnotation(CompilerDirectives.ValueType.class) != null) {
                    virtualInstanceNode.setIdentity(false);
                }
            }
        }

        if (!TruffleCompilerOptions.TruffleInlineAcrossTruffleBoundary.getValue()) {
            // Do not inline across Truffle boundaries.
            for (MethodCallTargetNode mct : graph.getNodes(MethodCallTargetNode.TYPE)) {
                if (mct.targetMethod().getAnnotation(TruffleBoundary.class) != null) {
                    mct.invoke().setUseForInlining(false);
                }
            }
        }
    }

    private static TruffleInliningDecision getDecision(TruffleInlining inlining, OptimizedDirectCallNode callNode) {
        OptimizedCallTarget target = callNode.getCallTarget();
        TruffleInliningDecision decision = inlining.findByCall(callNode);
        if (decision == null) {
            if (TruffleCompilerOptions.TraceTrufflePerformanceWarnings.getValue()) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("callNode", callNode);
                logPerformanceWarning(target, "A direct call within the Truffle AST is not reachable anymore. Call node could not be inlined.", properties);
            }
        }

        if (decision != null && decision.getTarget() != decision.getProfile().getCallNode().getCurrentCallTarget()) {
            if (TruffleCompilerOptions.TraceTrufflePerformanceWarnings.getValue()) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("originalTarget", decision.getTarget());
                properties.put("callNode", callNode);
                logPerformanceWarning(target, "CallTarget changed during compilation. Call node could not be inlined.", properties);
            }
            return null;
        }
        return decision;
    }

    private static void logPerformanceWarning(OptimizedCallTarget target, String details, Map<String, Object> properties) {
        logPerformanceWarning(target, "perf warn", details, properties);
    }

    private static void logPerformanceInfo(OptimizedCallTarget target, String details, Map<String, Object> properties) {
        logPerformanceWarning(target, "perf info", details, properties);
    }

    private static void logPerformanceWarning(OptimizedCallTarget target, String msg, String details, Map<String, Object> properties) {
        AbstractDebugCompilationListener.log(target, 0, msg, String.format("%-60s|%s", target, details), properties);
    }
}
