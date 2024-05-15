/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted;

import static com.oracle.svm.common.meta.MultiMethod.DEOPT_TARGET_METHOD;
import static com.oracle.svm.common.meta.MultiMethod.ORIGINAL_METHOD;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsingMaxDepth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.nodes.MacroNode;
import org.graalvm.compiler.replacements.nodes.MacroWithExceptionNode;
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptEntrySupport;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.graal.nodes.InlinedInvokeArgumentsNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.RuntimeCompilationSupport;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.SVMParsingSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Runtime compilation strategy used when {@link com.oracle.svm.core.SubstrateOptions#ParseOnceJIT}
 * is enabled.
 */
public class ParseOnceRuntimeCompilationFeature extends RuntimeCompilationFeature implements Feature, RuntimeCompilationSupport {

    public static class Options {
        @Option(help = "Remove Deopt(Entries,Anchors,Proxies) determined to be unneeded after the runtime compiled graphs have been finalized.")//
        public static final HostedOptionKey<Boolean> RemoveUnneededDeoptSupport = new HostedOptionKey<>(true);

        @Option(help = "Perform InlineBeforeAnalysis on runtime compiled methods")//
        public static final HostedOptionKey<Boolean> RuntimeCompilationInlineBeforeAnalysis = new HostedOptionKey<>(true);
    }

    public static final class CallTreeNode extends AbstractCallTreeNode {
        final BytecodePosition position;

        CallTreeNode(AnalysisMethod implementationMethod, AnalysisMethod targetMethod, CallTreeNode parent, BytecodePosition position) {
            super(parent, targetMethod, implementationMethod);
            this.position = position;
        }

        @Override
        public String getPosition() {
            if (position == null) {
                return "[root]";
            }
            return position.toString();
        }

        /**
         * It is not worthwhile to decode the graph to get the node count.
         */
        @Override
        public int getNodeCount() {
            return -1;
        }

    }

    static class RuntimeCompilationCandidateImpl implements RuntimeCompilationCandidate {
        AnalysisMethod implementationMethod;
        AnalysisMethod targetMethod;

        RuntimeCompilationCandidateImpl(AnalysisMethod implementationMethod, AnalysisMethod targetMethod) {
            this.implementationMethod = implementationMethod;
            this.targetMethod = targetMethod;
        }

        @Override
        public AnalysisMethod getImplementationMethod() {
            return implementationMethod;
        }

        @Override
        public AnalysisMethod getTargetMethod() {
            return targetMethod;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RuntimeCompilationCandidateImpl that = (RuntimeCompilationCandidateImpl) o;
            return implementationMethod.equals(that.implementationMethod) && targetMethod.equals(that.targetMethod);
        }

        @Override
        public int hashCode() {
            return Objects.hash(implementationMethod, targetMethod);
        }
    }

    static final class RuntimeCompiledMethodImpl implements RuntimeCompiledMethod {
        final AnalysisMethod method;
        final Collection<ResolvedJavaMethod> inlinedMethods;

        private RuntimeCompiledMethodImpl(AnalysisMethod method, Collection<ResolvedJavaMethod> inlinedMethods) {
            this.method = method;
            this.inlinedMethods = inlinedMethods;
        }

        @Override
        public AnalysisMethod getMethod() {
            return method;
        }

        @Override
        public Collection<ResolvedJavaMethod> getInlinedMethods() {
            return inlinedMethods;
        }

        @Override
        public Collection<ResolvedJavaMethod> getInvokeTargets() {
            List<ResolvedJavaMethod> targets = new ArrayList<>();
            for (var invoke : method.getInvokes()) {
                targets.add(invoke.getTargetMethod());
            }
            return targets;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RuntimeCompiledMethodImpl that = (RuntimeCompiledMethodImpl) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method);
        }
    }

    private static final class RuntimeGraphBuilderPhase extends AnalysisGraphBuilderPhase {

        private RuntimeGraphBuilderPhase(Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes, SVMHost hostVM) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes, hostVM);
        }

        static RuntimeGraphBuilderPhase createRuntimeGraphBuilderPhase(BigBang bb, Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {

            // Adjust graphbuilderconfig to match analysis phase
            var newGraphBuilderConfig = graphBuilderConfig.withEagerResolving(true).withUnresolvedIsError(PointstoOptions.UnresolvedIsError.getValue(bb.getOptions()));
            return new RuntimeGraphBuilderPhase(providers, newGraphBuilderConfig, optimisticOpts, null, providers.getWordTypes(), (SVMHost) bb.getHostVM());
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new RuntimeBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext, hostVM);
        }
    }

    private static final class RuntimeBytecodeParser extends AnalysisGraphBuilderPhase.AnalysisBytecodeParser {

        RuntimeBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, SVMHost svmHost) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, svmHost, false);
        }

        @Override
        protected boolean tryInvocationPlugin(CallTargetNode.InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            boolean result = super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
            if (result) {
                SubstrateCompilationDirectives.singleton().registerAsDeoptInlininingExclude(targetMethod);
            }
            return result;
        }

        @Override
        protected boolean shouldVerifyFrameStates() {
            /*
             * (GR-46115) Ideally we should verify frame states in methods registered for runtime
             * compilations, as well as any other methods that can deoptimize. Because runtime
             * compiled methods can pull in almost arbitrary code, this means most frame states
             * should be verified. We currently use illegal states as placeholders in many places,
             * so this cannot be enabled at the moment.
             */
            return false;
        }
    }

    private final Set<AnalysisMethod> registeredRuntimeCompilations = ConcurrentHashMap.newKeySet();
    private final Set<SubstrateMethod> substrateAnalysisMethods = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisMethod, String> invalidForRuntimeCompilation = new ConcurrentHashMap<>();
    private final Set<RuntimeCompilationCandidate> runtimeCompilationCandidates = ConcurrentHashMap.newKeySet();
    private Set<RuntimeCompiledMethod> runtimeCompilations = null;
    private Map<RuntimeCompilationCandidate, CallTreeNode> runtimeCandidateCallTree = null;
    private Map<AnalysisMethod, CallTreeNode> runtimeCompiledMethodCallTree = null;
    private HostedProviders analysisProviders = null;
    private AllowInliningPredicate allowInliningPredicate = (builder, target) -> AllowInliningPredicate.InlineDecision.INLINING_DISALLOWED;
    private boolean allowInliningPredicateUpdated = false;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return RuntimeCompilationFeature.getRequiredFeaturesHelper();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SVMParsingSupport.class, new RuntimeCompilationParsingSupport());
        ImageSingletons.add(HostVM.MultiMethodAnalysisPolicy.class, new RuntimeCompilationAnalysisPolicy());
        ImageSingletons.add(RuntimeCompilationFeature.class, this);
        ImageSingletons.add(RuntimeCompilationSupport.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        duringSetupHelper(c);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess c) {
        beforeAnalysisHelper(c);
    }

    @Override
    public void registerAllowInliningPredicate(AllowInliningPredicate predicate) {
        assert !allowInliningPredicateUpdated;
        allowInliningPredicate = predicate;
        allowInliningPredicateUpdated = true;
    }

    @Override
    public void initializeAnalysisProviders(BigBang bb, Function<ConstantFieldProvider, ConstantFieldProvider> generator) {
        HostedProviders defaultProviders = bb.getProviders(ORIGINAL_METHOD);
        HostedProviders customHostedProviders = (HostedProviders) defaultProviders.copyWith(generator.apply(defaultProviders.getConstantFieldProvider()));
        customHostedProviders.setGraphBuilderPlugins(hostedProviders.getGraphBuilderPlugins());
        analysisProviders = customHostedProviders;
    }

    boolean newRuntimeMethodsSeen = false;

    @Override
    public void duringAnalysis(DuringAnalysisAccess c) {
        /*
         * Note this will be removed once graphEncoder and the graal graph object replacer are
         * thread friendly.
         */
        FeatureImpl.DuringAnalysisAccessImpl config = (FeatureImpl.DuringAnalysisAccessImpl) c;

        if (newRuntimeMethodsSeen) {
            SubstrateMethod[] methodsToCompileArr = substrateAnalysisMethods.stream().toArray(SubstrateMethod[]::new);
            GraalSupport.setMethodsToCompile(config, methodsToCompileArr);
            config.requireAnalysisIteration();
            newRuntimeMethodsSeen = false;
        }

        graphEncoder.finishPrepare();
        AnalysisMetaAccess metaAccess = config.getMetaAccess();
        NodeClass<?>[] nodeClasses = graphEncoder.getNodeClasses();
        for (NodeClass<?> nodeClass : nodeClasses) {
            metaAccess.lookupJavaType(nodeClass.getClazz()).registerAsAllocated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }
        if (GraalSupport.setGraphEncoding(config, graphEncoder.getEncoding(), graphEncoder.getObjects(), nodeClasses)) {
            config.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        /*
         * At this point need to determine which methods are actually valid for runtime compilation
         * and calculate their reachability info.
         */
        buildCallTrees();

        runtimeCompilations = new HashSet<>();
        FeatureImpl.AfterAnalysisAccessImpl impl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        for (var method : impl.getUniverse().getMethods()) {
            var rMethod = method.getMultiMethod(RUNTIME_COMPILED_METHOD);
            if (rMethod != null && rMethod.isReachable() && !invalidForRuntimeCompilation.containsKey(rMethod)) {
                var runtimeInlinedMethods = rMethod.getAnalyzedGraph().getInlinedMethods();
                var inlinedMethods = runtimeInlinedMethods.stream().map(inlinedMethod -> {
                    ResolvedJavaMethod orig = ((AnalysisMethod) inlinedMethod).getMultiMethod(ORIGINAL_METHOD);
                    assert orig != null;
                    return orig;
                }).collect(Collectors.toUnmodifiableSet());
                boolean added = runtimeCompilations.add(new RuntimeCompiledMethodImpl(method, inlinedMethods));
                assert added;
                assert runtimeCompiledMethodCallTree.containsKey(method);
            }
        }

        // call super after
        afterAnalysisHelper();

        // after analysis has completed we must ensure no new SubstrateTypes are introduced
        objectReplacer.forbidNewTypes();
    }

    @Override
    protected AbstractCallTreeNode getCallTreeNode(RuntimeCompilationCandidate candidate) {
        var result = runtimeCandidateCallTree.get(candidate);
        assert result != null;
        return result;
    }

    @Override
    protected AbstractCallTreeNode getCallTreeNode(RuntimeCompiledMethod method) {
        return getCallTreeNode(method.getMethod());
    }

    @Override
    protected AbstractCallTreeNode getCallTreeNode(ResolvedJavaMethod method) {
        var result = runtimeCompiledMethodCallTree.get(method);
        assert result != null;
        return result;
    }

    @Override
    public Collection<RuntimeCompiledMethod> getRuntimeCompiledMethods() {
        return runtimeCompilations;
    }

    @Override
    public Collection<RuntimeCompilationCandidate> getAllRuntimeCompilationCandidates() {
        return runtimeCompilationCandidates;
    }

    private void buildCallTrees() {
        /*
         * While it is possible to dynamically calculate call traces by enabling
         * PointstoOptions#TraceAccessChain, creating call trees post-analysis for runtime compiled
         * methods allows use to not have this overhead during analysis and also to determine the
         * access chains for multiple call sites with the same destination.
         *
         * This is useful to create more stringent blocklist checks.
         */
        assert runtimeCandidateCallTree == null && runtimeCompiledMethodCallTree == null;
        runtimeCandidateCallTree = new HashMap<>();
        runtimeCompiledMethodCallTree = new HashMap<>();

        Queue<CallTreeNode> worklist = new LinkedList<>();

        /* First initialize with registered runtime compilations */
        for (AnalysisMethod root : registeredRuntimeCompilations) {
            var runtimeRoot = root.getMultiMethod(RUNTIME_COMPILED_METHOD);
            if (runtimeRoot != null) {
                runtimeCandidateCallTree.computeIfAbsent(new RuntimeCompilationCandidateImpl(root, root), (candidate) -> {
                    var result = new CallTreeNode(root, root, null, null);
                    worklist.add(result);
                    return result;
                });
            }
        }

        /*
         * Find all runtime compiled methods reachable from registered runtime compilations.
         *
         * Note within the maps we store the original methods, not the runtime methods.
         */
        while (!worklist.isEmpty()) {
            var caller = worklist.remove();
            caller.linkAsChild();

            /*
             * We only need to record one trace for methods
             */
            var method = caller.getImplementationMethod();
            if (runtimeCompiledMethodCallTree.containsKey(method)) {
                // This method has already been processed
                continue;
            } else {
                runtimeCompiledMethodCallTree.put(method, caller);
            }
            var runtimeMethod = method.getMultiMethod(RUNTIME_COMPILED_METHOD);
            assert runtimeMethod != null;

            for (InvokeInfo invokeInfo : runtimeMethod.getInvokes()) {
                AnalysisMethod invokeTarget = invokeInfo.getTargetMethod();
                if (invokeInfo.isDeoptInvokeTypeFlow()) {
                    assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(invokeTarget);
                    invokeTarget = invokeTarget.getMultiMethod(ORIGINAL_METHOD);
                }
                AnalysisMethod target = invokeTarget;
                assert target.isOriginalMethod();
                for (AnalysisMethod implementation : invokeInfo.getAllCallees()) {
                    if (SubstrateCompilationDirectives.isRuntimeCompiledMethod(implementation)) {
                        var origImpl = implementation.getMultiMethod(ORIGINAL_METHOD);
                        assert origImpl != null;
                        runtimeCandidateCallTree.computeIfAbsent(new RuntimeCompilationCandidateImpl(origImpl, target), (candidate) -> {
                            var result = new CallTreeNode(origImpl, target, caller, invokeInfo.getPosition());
                            worklist.add(result);
                            return result;
                        });
                    } else if (implementation.isOriginalMethod() && implementation.getMultiMethod(RUNTIME_COMPILED_METHOD) == null) {
                        /*
                         * Recording that this call was reachable, but not converted to a runtime
                         * compiled method.
                         */
                        runtimeCandidateCallTree.computeIfAbsent(new RuntimeCompilationCandidateImpl(implementation, target),
                                        (candidate) -> {
                                            var result = new CallTreeNode(implementation, target, caller, invokeInfo.getPosition());
                                            result.linkAsChild();
                                            return result;
                                        });
                    }
                }
            }
        }
    }

    public Set<ResolvedJavaMethod> parsedRuntimeMethods = ConcurrentHashMap.newKeySet();
    public AtomicLong totalParsedRuntimeMethods = new AtomicLong();
    public Set<ResolvedJavaMethod> parsedDeoptMethods = ConcurrentHashMap.newKeySet();
    public AtomicLong totalParsedDeoptMethods = new AtomicLong();

    private class RuntimeCompileTask implements CompletionExecutor.DebugContextRunnable {
        final HostedMethod method;

        RuntimeCompileTask(HostedMethod method) {
            this.method = method;
        }

        @Override
        public DebugContext getDebug(OptionValues options, List<DebugHandlersFactory> factories) {
            return new DebugContext.Builder(options, factories).description(getDescription()).build();
        }

        @Override
        public void run(DebugContext debug) {
            compileRuntimeCompiledMethod(debug, method);
        }
    }

    private final Map<HostedMethod, StructuredGraph> runtimeGraphs = new ConcurrentHashMap<>();

    @SuppressWarnings("try")
    private void compileRuntimeCompiledMethod(DebugContext debug, HostedMethod method) {
        assert method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;

        AnalysisMethod aMethod = method.getWrapped();
        StructuredGraph graph = aMethod.decodeAnalyzedGraph(debug, null);
        if (graph == null) {
            throw VMError.shouldNotReachHere("Method not parsed during static analysis: " + aMethod.format("%r %H.%n(%p)"));
        }
        /*
         * The graph in the analysis universe is no longer necessary once it is transplanted into
         * the hosted universe.
         */
        aMethod.setAnalyzedGraph(null);

        /*
         * The availability of NodeSourcePosition for JIT compilation is controlled by a separate
         * option and not TrackNodeSourcePosition to decouple AOT and JIT compilation.
         */
        boolean trackNodeSourcePosition = SubstrateOptions.IncludeNodeSourcePositions.getValue();
        if (!trackNodeSourcePosition) {
            for (Node node : graph.getNodes()) {
                node.clearNodeSourcePosition();
            }
        }

        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        IterativeConditionalEliminationPhase conditionalElimination = new IterativeConditionalEliminationPhase(canonicalizer, true);
        ConvertDeoptimizeToGuardPhase convertDeoptimizeToGuard = new ConvertDeoptimizeToGuardPhase(canonicalizer);

        try (DebugContext.Scope s = debug.scope("RuntimeOptimize", graph, method, this)) {
            canonicalizer.apply(graph, hostedProviders);

            conditionalElimination.apply(graph, hostedProviders);

            /*
             * ConvertDeoptimizeToGuardPhase was already executed after parsing, but optimizations
             * applied in between can provide new potential.
             */
            convertDeoptimizeToGuard.apply(graph, hostedProviders);

            /*
             * More optimizations can be added here.
             */
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        /*
         * Registering all deopt entries seen within the optimized graph. This should be strictly a
         * subset of the deopt entrypoints seen during evaluation.
         */
        AnalysisMethod origMethod = method.getMultiMethod(ORIGINAL_METHOD).getWrapped();
        DeoptimizationUtils.registerDeoptEntries(graph, registeredRuntimeCompilations.contains(origMethod),
                        (deoptEntryMethod -> {
                            PointsToAnalysisMethod deoptMethod = (PointsToAnalysisMethod) ((PointsToAnalysisMethod) deoptEntryMethod).getMultiMethod(DEOPT_TARGET_METHOD);
                            VMError.guarantee(deoptMethod != null, "New deopt target method seen: %s", deoptEntryMethod);
                            return deoptMethod;
                        }));

        assert RuntimeCompilationFeature.verifyNodes(graph);
        var previous = runtimeGraphs.put(method, graph);
        assert previous == null;

        // graph encoder is not currently threadsafe
        synchronized (this) {
            graphEncoder.prepare(graph);
        }
    }

    @SuppressWarnings("try")
    private void encodeRuntimeCompiledMethods() {
        graphEncoder.finishPrepare();

        // at this point no new deoptimization entrypoints can be registered.
        SubstrateCompilationDirectives.singleton().sealDeoptimizationInfo();

        for (var runtimeInfo : runtimeGraphs.entrySet()) {
            var graph = runtimeInfo.getValue();
            var method = runtimeInfo.getKey();
            DebugContext debug = new DebugContext.Builder(graph.getOptions(), new GraalDebugHandlersFactory(hostedProviders.getSnippetReflection())).build();
            graph.resetDebug(debug);
            try (DebugContext.Scope s = debug.scope("Graph Encoding", graph);
                            DebugContext.Activation a = debug.activate()) {
                long startOffset = graphEncoder.encode(graph);
                objectReplacer.createMethod(method).setEncodedGraphStartOffset(startOffset);
            } catch (Throwable ex) {
                debug.handle(ex);
            }
        }

        HeapBreakdownProvider.singleton().setGraphEncodingByteLength(graphEncoder.getEncoding().length);
        GraalSupport.setGraphEncoding(null, graphEncoder.getEncoding(), graphEncoder.getObjects(), graphEncoder.getNodeClasses());

        objectReplacer.setMethodsImplementations();

        /* All the temporary data structures used during encoding are no longer necessary. */
        graphEncoder = null;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess c) {
        beforeCompilationHelper();
    }

    @Override
    public void onCompileQueueCreation(HostedUniverse universe, CompileQueue compileQueue) {
        /*
         * Start fresh with a new GraphEncoder, since we are going to optimize all graphs now that
         * the static analysis results are available.
         */
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);

        SubstrateCompilationDirectives.singleton().resetDeoptEntries();
        /*
         * Customize runtime compile methods for compiling them into substrate graphs.
         */
        CompletionExecutor executor = compileQueue.getExecutor();
        try {
            compileQueue.runOnExecutor(() -> {
                universe.getMethods().stream().map(method -> method.getMultiMethod(RUNTIME_COMPILED_METHOD)).filter(method -> {
                    if (method != null) {
                        AnalysisMethod aMethod = method.getWrapped();
                        return aMethod.isImplementationInvoked() && !invalidForRuntimeCompilation.containsKey(aMethod);
                    }
                    return false;
                }).forEach(method -> {
                    executor.execute(new RuntimeCompileTask(method));
                });
            });
        } catch (InterruptedException exception) {
            VMError.shouldNotReachHere(exception);
        }
        encodeRuntimeCompiledMethods();

        /*
         * For Deoptimization Targets add a custom phase which removes all deoptimization
         * entrypoints which are deemed no longer necessary.
         */
        CompileQueue.ParseHooks deoptParseHooks = new CompileQueue.ParseHooks(compileQueue) {
            @Override
            protected PhaseSuite<HighTierContext> getAfterParseSuite() {
                PhaseSuite<HighTierContext> suite = super.getAfterParseSuite();
                if (Options.RemoveUnneededDeoptSupport.getValue()) {
                    var iterator = suite.findPhase(StrengthenStampsPhase.class);
                    if (iterator == null) {
                        suite.prependPhase(new RemoveUnneededDeoptSupport());
                    } else {
                        iterator.add(new RemoveUnneededDeoptSupport());
                    }
                }

                return suite;
            }
        };

        universe.getMethods().stream().map(method -> method.getMultiMethod(DEOPT_TARGET_METHOD)).filter(method -> {
            if (method != null) {
                return compileQueue.isRegisteredDeoptTarget(method);
            }
            return false;
        }).forEach(method -> method.compilationInfo.setCustomParseHooks(deoptParseHooks));

    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        super.afterCompilationHelper(a);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        afterHeapLayoutHelper(a);
    }

    @Override
    public SubstrateMethod prepareMethodForRuntimeCompilation(ResolvedJavaMethod method, FeatureImpl.BeforeAnalysisAccessImpl config) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        assert aMethod.isOriginalMethod();

        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);
        substrateAnalysisMethods.add(sMethod);

        if (registeredRuntimeCompilations.add(aMethod)) {
            aMethod.getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD);
            /*
             * For static methods it is important to also register the runtime and deopt targets as
             * roots to ensure the methods will be linked appropriately. However, we do not need to
             * make the entire flow for the deopt version until we see what FrameStates exist within
             * the runtime version.
             */
            aMethod.getOrCreateMultiMethod(DEOPT_TARGET_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
            config.registerAsRoot(aMethod, true, "Runtime compilation, registered in " + ParseOnceRuntimeCompilationFeature.class, RUNTIME_COMPILED_METHOD, DEOPT_TARGET_METHOD);
        }

        return sMethod;
    }

    @Override
    protected void requireFrameInformationForMethodHelper(AnalysisMethod aMethod, FeatureImpl.BeforeAnalysisAccessImpl config, boolean registerAsRoot) {
        assert aMethod.isOriginalMethod();
        AnalysisMethod deoptTarget = aMethod.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(aMethod, deoptTarget);
        if (registerAsRoot) {
            config.registerAsRoot(aMethod, true, "Frame information required, registered in " + ParseOnceRuntimeCompilationFeature.class, DEOPT_TARGET_METHOD);
        }
    }

    private class RuntimeCompilationParsingSupport implements SVMParsingSupport {
        RuntimeCompilationInlineBeforeAnalysisPolicy runtimeInlineBeforeAnalysisPolicy = null;

        @Override
        public HostedProviders getHostedProviders(MultiMethod.MultiMethodKey key) {
            if (key == RUNTIME_COMPILED_METHOD) {
                assert analysisProviders != null;
                return analysisProviders;
            }
            return null;
        }

        @Override
        public boolean allowAssumptions(AnalysisMethod method) {
            return method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;
        }

        @Override
        public boolean recordInlinedMethods(AnalysisMethod method) {
            return method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;
        }

        @Override
        public Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method) {
            // want to have a couple more checks here that are in DeoptimizationUtils
            if (method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD) {
                return parseRuntimeCompiledMethod(bb, debug, method);
            }
            return HostVM.PARSING_UNHANDLED;
        }

        @Override
        public GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method) {
            if (method.isDeoptTarget()) {
                /*
                 * The assertion setting for the deoptTarget and the runtime compiled method must
                 * match.
                 *
                 * Local variables are never retained to help ensure the state of the deoptimization
                 * source will always be a superset of the deoptimization target.
                 */
                return config.withOmitAssertions(graphBuilderConfig.omitAssertions()).withRetainLocalVariables(false);
            }
            return config;
        }

        @SuppressWarnings("try")
        private Object parseRuntimeCompiledMethod(BigBang bb, DebugContext debug, AnalysisMethod method) {

            boolean parsed = false;

            StructuredGraph graph = method.buildGraph(debug, method, analysisProviders, GraphProvider.Purpose.PREPARE_RUNTIME_COMPILATION);
            if (graph == null) {
                if (!method.hasBytecodes()) {
                    recordFailed(method);
                    return HostVM.PARSING_FAILED;
                }

                parsed = true;
                graph = new StructuredGraph.Builder(debug.getOptions(), debug, StructuredGraph.AllowAssumptions.YES).method(method)
                                /*
                                 * Needed for computation of the list of all runtime compilable
                                 * methods in TruffleFeature.
                                 */
                                .recordInlinedMethods(true).build();
            }
            try (DebugContext.Scope scope = debug.scope("RuntimeCompile", graph, method)) {
                if (parsed) {
                    // enable this logging to get log output in compilation passes
                    try (Indent indent2 = debug.logAndIndent("parse graph phases")) {
                        RuntimeGraphBuilderPhase.createRuntimeGraphBuilderPhase(bb, analysisProviders, graphBuilderConfig, optimisticOpts).apply(graph);
                    } catch (PermanentBailoutException ex) {
                        bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                        recordFailed(method);
                        return HostVM.PARSING_FAILED;
                    }
                }

                CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
                canonicalizer.apply(graph, analysisProviders);
                if (deoptimizeOnExceptionPredicate != null) {
                    new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate).apply(graph);
                }
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, analysisProviders);

                if (DeoptimizationUtils.createGraphInvalidator(graph).get()) {
                    recordFailed(method);
                    return HostVM.PARSING_FAILED;
                }

            } catch (Throwable ex) {
                debug.handle(ex);
            }

            return graph;
        }

        private void recordFailed(AnalysisMethod method) {
            // Will need to create post to invalidate other MethodTypeFlows (if they exist)
            invalidForRuntimeCompilation.computeIfAbsent(method, (m) -> "generic failure");
        }

        @Override
        public boolean validateGraph(PointsToAnalysis bb, StructuredGraph graph) {
            PointsToAnalysisMethod aMethod = (PointsToAnalysisMethod) graph.method();
            MultiMethod.MultiMethodKey multiMethodKey = aMethod.getMultiMethodKey();
            Supplier<Boolean> graphInvalidator = DeoptimizationUtils.createGraphInvalidator(graph);
            if (aMethod.isOriginalMethod() && DeoptimizationUtils.canDeoptForTesting(aMethod, false, graphInvalidator)) {
                DeoptimizationUtils.registerDeoptEntriesForDeoptTesting(bb, graph, aMethod);
                return true;
            }
            if (multiMethodKey != ORIGINAL_METHOD) {
                if (graphInvalidator.get()) {
                    recordFailed(aMethod);
                    return false;
                }
            }
            if (multiMethodKey == RUNTIME_COMPILED_METHOD) {
                parsedRuntimeMethods.add(aMethod);
                totalParsedRuntimeMethods.incrementAndGet();
                /*
                 * Register all FrameStates as DeoptEntries.
                 */
                AnalysisMethod origMethod = aMethod.getMultiMethod(ORIGINAL_METHOD);

                /*
                 * Because this graph will have its flowgraph immediately updated after this, there
                 * is no reason to make this method's flowgraph a stub on creation.
                 */
                Collection<ResolvedJavaMethod> recomputeMethods = DeoptimizationUtils.registerDeoptEntries(graph, registeredRuntimeCompilations.contains(origMethod),
                                (deoptEntryMethod -> ((PointsToAnalysisMethod) deoptEntryMethod).getOrCreateMultiMethod(DEOPT_TARGET_METHOD)));

                /*
                 * If new frame states are found, then redo the type flow
                 */
                for (ResolvedJavaMethod method : recomputeMethods) {
                    assert MultiMethod.isDeoptTarget(method);
                    ((PointsToAnalysisMethod) method).getTypeFlow().updateFlowsGraph(bb, MethodFlowsGraph.GraphKind.FULL, null, true);
                }

                // Note that this will be made thread-safe in the future
                synchronized (this) {
                    newRuntimeMethodsSeen = true;
                    var origAMethod = aMethod.getMultiMethod(ORIGINAL_METHOD);
                    assert origAMethod != null;
                    var sMethod = objectReplacer.createMethod(origAMethod);
                    substrateAnalysisMethods.add(sMethod);
                    graphEncoder.prepare(graph);
                }
                assert RuntimeCompilationFeature.verifyNodes(graph);
            } else if (multiMethodKey == DEOPT_TARGET_METHOD) {
                parsedDeoptMethods.add(aMethod);
                totalParsedDeoptMethods.incrementAndGet();
            }

            return true;
        }

        @Override
        public void afterParsingHook(AnalysisMethod method, StructuredGraph graph) {
            if (method.isDeoptTarget()) {
                new ConvertMacroNodes().apply(graph);
            }
        }

        @Override
        public void initializeInlineBeforeAnalysisPolicy(SVMHost svmHost, InlineBeforeAnalysisPolicyUtils inliningUtils) {
            if (Options.RuntimeCompilationInlineBeforeAnalysis.getValue()) {
                assert runtimeInlineBeforeAnalysisPolicy == null;
                runtimeInlineBeforeAnalysisPolicy = new RuntimeCompilationInlineBeforeAnalysisPolicy(svmHost, inliningUtils);
            }
        }

        @Override
        public InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy(MultiMethod.MultiMethodKey multiMethodKey, InlineBeforeAnalysisPolicy defaultPolicy) {
            if (multiMethodKey == ORIGINAL_METHOD) {
                return defaultPolicy;
            } else if (multiMethodKey == DEOPT_TARGET_METHOD) {
                return InlineBeforeAnalysisPolicy.NO_INLINING;
            } else if (multiMethodKey == RUNTIME_COMPILED_METHOD) {
                if (Options.RuntimeCompilationInlineBeforeAnalysis.getValue()) {
                    assert runtimeInlineBeforeAnalysisPolicy != null;
                    return runtimeInlineBeforeAnalysisPolicy;
                }
                return InlineBeforeAnalysisPolicy.NO_INLINING;
            } else {
                throw VMError.shouldNotReachHere("Unexpected method key: %s", multiMethodKey);
            }
        }

        @Override
        public Function<AnalysisType, ResolvedJavaType> getStrengthenGraphsToTargetFunction(MultiMethod.MultiMethodKey key) {
            if (key == RUNTIME_COMPILED_METHOD) {
                /*
                 * For runtime compiled methods, we must be careful to ensure new SubstrateTypes are
                 * not created during the AnalysisStrengthenGraphsPhase. If the type does not
                 * already exist at this point (which is after the analysis phase), then we must
                 * return null.
                 */
                return (t) -> objectReplacer.typeCreated(t) ? t : null;
            }
            return null;
        }
    }

    /**
     * This policy is a combination of the default InliningBeforeAnalysisImpl and the Trivial
     * Inlining Policy. When the depth is less than
     * {@code BytecodeParserOptions#InlineDuringParsingMaxDepth} (and all inlined parents were also
     * trivial inlined), then it will continue to try to trivial inline methods (i.e., inline
     * methods with less than a given number of nodes). Then, up to
     * {@code InlineBeforeAnalysisPolicyUtils.Options#InlineBeforeAnalysisAllowedDepth}, inlining is
     * enabled as long as the cumulative number of nodes inlined stays within the specified limits.
     *
     * Note that this policy is used exclusively by the runtime compiled methods, so there is no
     * need to check multi-method keys; all callers (and callees) should be
     * {@code RUNTIME_COMPILED_METHOD}s.
     */
    private class RuntimeCompilationInlineBeforeAnalysisPolicy extends InlineBeforeAnalysisPolicy {
        private final int trivialAllowingInliningDepth = InlineDuringParsingMaxDepth.getValue(HostedOptionValues.singleton());

        final SVMHost hostVM;
        final InlineBeforeAnalysisPolicyUtils inliningUtils;

        protected RuntimeCompilationInlineBeforeAnalysisPolicy(SVMHost hostVM, InlineBeforeAnalysisPolicyUtils inliningUtils) {
            super(new NodePlugin[]{new ConstantFoldLoadFieldPlugin(ParsingReason.PointsToAnalysis)});
            this.hostVM = hostVM;
            this.inliningUtils = inliningUtils;
        }

        @Override
        protected boolean tryInvocationPlugins() {
            return true;
        }

        @Override
        protected boolean needsExplicitExceptions() {
            return false;
        }

        @Override
        protected FixedWithNextNode processInvokeArgs(ResolvedJavaMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments) {
            StructuredGraph graph = insertionPoint.graph();
            InlinedInvokeArgumentsNode newNode = graph.add(new InlinedInvokeArgumentsNode(targetMethod, arguments));
            graph.addAfterFixed(insertionPoint, newNode);
            return newNode;
        }

        @Override
        protected boolean shouldInlineInvoke(GraphBuilderContext b, AbstractPolicyScope policyScope, ResolvedJavaMethod method, ValueNode[] args) {
            if (allowInliningPredicate.allowInlining(b, method) != AllowInliningPredicate.InlineDecision.INLINE) {
                return false;
            }

            InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope accScope;
            if (policyScope instanceof InlineBeforeAnalysisPolicyUtils.AlwaysInlineScope) {
                /*
                 * If we are in "trivial inlining" mode, we make inlining decisions as if we are
                 * still the root (= null) accumulative inlining scope.
                 */
                accScope = null;
            } else {
                accScope = (InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope) policyScope;
            }
            return inliningUtils.shouldInlineInvoke(b, hostVM, accScope, method);
        }

        @Override
        protected InlineInvokePlugin.InlineInfo createInvokeInfo(ResolvedJavaMethod method) {
            /*
             * Set this graph initially to a stub. If there are no explicit calls to this method
             * (i.e., all calls to this method are inlined), then the method's full flow will not
             * need to be created.
             */
            AnalysisMethod runtimeMethod = ((AnalysisMethod) method).getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(runtimeMethod);
        }

        @Override
        protected AbstractPolicyScope createRootScope() {
            /* We do not need a scope for the root method. */
            return null;
        }

        @Override
        protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, ResolvedJavaMethod method) {
            if (outer instanceof InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope accOuter) {
                /*
                 * once the accumulative policy is activated, then we cannot return to the trivial
                 * policy
                 */
                return inliningUtils.createAccumulativeInlineScope(accOuter, method);
            }

            assert outer == null || outer instanceof InlineBeforeAnalysisPolicyUtils.AlwaysInlineScope : "unexpected outer scope: " + outer;

            /*
             * Check if trivial is possible. We use the graph size as the main criteria, similar to
             * the trivial inlining for AOT compilation.
             *
             * In addition, we do not allow method handle internals to be processed by the trivial
             * inlining. The regular accumulative inlining scope has a special mode for method
             * handle intrinsification with larger thresholds in order to fully inline the method
             * handle.
             */
            boolean trivialInlineAllowed = hostVM.isAnalysisTrivialMethod((AnalysisMethod) method) &&
                            !AnnotationAccess.isAnnotationPresent(method, InlineBeforeAnalysisPolicyUtils.COMPILED_LAMBDA_FORM_ANNOTATION);
            int inliningDepth = outer == null ? 1 : outer.inliningDepth + 1;
            if (trivialInlineAllowed && inliningDepth <= trivialAllowingInliningDepth) {
                return new InlineBeforeAnalysisPolicyUtils.AlwaysInlineScope(inliningDepth);
            } else {
                // start with a new accumulative inline scope
                return inliningUtils.createAccumulativeInlineScope(null, method);
            }
        }
    }

    private class RuntimeCompilationAnalysisPolicy implements HostVM.MultiMethodAnalysisPolicy {

        @Override
        public <T extends AnalysisMethod> Collection<T> determineCallees(BigBang bb, T implementation, T target, MultiMethod.MultiMethodKey callerMultiMethodKey, InvokeTypeFlow invokeFlow) {
            if (invokeFlow.isDeoptInvokeTypeFlow()) {
                /*
                 * When the type flow represents a deopt invoke, then the arguments only need to be
                 * linked to the deopt target. As there is not a call here (the call is inlined), no
                 * other linking is necessary.
                 */
                assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(implementation);
                var originalTarget = implementation.getMultiMethod(ORIGINAL_METHOD);
                assert originalTarget != null;
                runtimeCompilationCandidates.add(new RuntimeCompilationCandidateImpl(originalTarget, originalTarget));
                return List.of(getStubDeoptVersion(implementation));
            }
            assert implementation.isOriginalMethod() && target.isOriginalMethod();

            boolean registeredRuntimeCompilation = registeredRuntimeCompilations.contains(implementation);
            if (callerMultiMethodKey == ORIGINAL_METHOD) {
                /*
                 * Unless the method is a registered runtime compilation, it is not possible for an
                 * original variant to call a runtime variant (and indirectly the deoptimiztation
                 * variant).
                 *
                 * However, frame information is matched using the deoptimization entry point of a
                 * method, so deopt targets must be created for them as well.
                 */
                if (registeredRuntimeCompilation) {
                    return List.of(implementation, getStubDeoptVersion(implementation), getRuntimeVersion(bb, implementation, true, invokeFlow));
                } else if (SubstrateCompilationDirectives.singleton().isFrameInformationRequired(implementation)) {
                    return List.of(implementation, getDeoptVersion(bb, implementation, true, invokeFlow));
                } else if (DeoptimizationUtils.canDeoptForTesting(implementation, false, () -> false)) {
                    /*
                     * If the target is registered for deoptimization, then we must also make a
                     * deoptimized version.
                     */
                    return List.of(implementation, getStubDeoptVersion(implementation));
                } else {
                    return List.of(implementation);
                }
            } else {
                boolean runtimeCompilationCandidate = registeredRuntimeCompilation || runtimeCompilationCandidatePredicate.allowRuntimeCompilation(implementation);

                if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD) {
                    // recording compilation candidate
                    runtimeCompilationCandidates.add(new RuntimeCompilationCandidateImpl(implementation, target));
                    /*
                     * The runtime method can call all three types: original (if it is not partial
                     * evaluated), runtime (if it is partial evaluated), and deoptimized (if the
                     * runtime deoptimizes).
                     */
                    if (runtimeCompilationCandidate) {
                        return List.of(implementation, getStubDeoptVersion(implementation), getRuntimeVersion(bb, implementation, true, invokeFlow));
                    } else {
                        /*
                         * If this method cannot be jitted, then only the original implementation is
                         * needed.
                         */
                        return List.of(implementation);
                    }
                } else {
                    assert callerMultiMethodKey == DEOPT_TARGET_METHOD;
                    /*
                     * A deoptimization target will always call the original method. However, the
                     * return can also be from a deoptimized version when a deoptimization is
                     * triggered in an inlined callee. In addition, because we want runtime
                     * information to flow into this method via the return, we also need to link
                     * against the runtime variant. We only register the runtime variant as a stub
                     * though because its flow only needs to be made upon it being reachable from a
                     * runtime compiled method's invoke.
                     */
                    if (runtimeCompilationCandidate) {
                        return List.of(implementation, getStubDeoptVersion(implementation), getRuntimeVersion(bb, implementation, false, invokeFlow));
                    } else {
                        /*
                         * If this method cannot be jitted, then only the original implementation is
                         * needed.
                         */
                        return List.of(implementation);
                    }
                }
            }

        }

        protected <T extends AnalysisMethod> T getStubDeoptVersion(T implementation) {
            /*
             * Flows for deopt versions are only created once a frame state for the method is seen
             * within a runtime compiled method.
             */
            return getDeoptVersion(null, implementation, false, null);
        }

        @SuppressWarnings("unchecked")
        protected <T extends AnalysisMethod> T getDeoptVersion(BigBang bb, T implementation, boolean createFlow, InvokeTypeFlow parsingReason) {
            if (createFlow) {
                PointsToAnalysisMethod runtimeMethod = (PointsToAnalysisMethod) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
                PointsToAnalysis analysis = (PointsToAnalysis) bb;
                runtimeMethod.getTypeFlow().updateFlowsGraph(analysis, MethodFlowsGraph.GraphKind.FULL, parsingReason, true);
                return (T) runtimeMethod;
            } else {
                /*
                 * If a flow is not needed then temporarily a stub can be created.
                 */
                return (T) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
            }
        }

        @SuppressWarnings("unchecked")
        protected <T extends AnalysisMethod> T getRuntimeVersion(BigBang bb, T implementation, boolean createFlow, InvokeTypeFlow parsingReason) {
            if (createFlow) {
                PointsToAnalysisMethod runtimeMethod = (PointsToAnalysisMethod) implementation.getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD);
                PointsToAnalysis analysis = (PointsToAnalysis) bb;
                runtimeMethod.getTypeFlow().updateFlowsGraph(analysis, MethodFlowsGraph.GraphKind.FULL, parsingReason, false);
                return (T) runtimeMethod;
            } else {
                /*
                 * If a flow is not needed then temporarily a stub can be created.
                 */
                return (T) implementation.getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
            }
        }

        @Override
        public boolean performParameterLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD) {
                /* A runtime method can call all three. */
                return true;
            } else if (callerMultiMethodKey == DEOPT_TARGET_METHOD) {
                /* A deopt method can call the original version only. */
                return calleeMultiMethodKey == ORIGINAL_METHOD;
            }
            assert callerMultiMethodKey == ORIGINAL_METHOD;
            /* An original method can call all three. */
            return true;
        }

        @Override
        public boolean performReturnLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD) {
                /* A runtime method can be returned to from either a runtime or original method. */
                return calleeMultiMethodKey == RUNTIME_COMPILED_METHOD || calleeMultiMethodKey == ORIGINAL_METHOD;
            } else if (callerMultiMethodKey == DEOPT_TARGET_METHOD) {
                /* A deopt method can be returned to from all three. */
                return true;
            }
            assert callerMultiMethodKey == ORIGINAL_METHOD;
            /* An original method can can be returned to from all three. */
            return true;
        }

        @Override
        public boolean canComputeReturnedParameterIndex(MultiMethod.MultiMethodKey multiMethodKey) {
            /*
             * Since Deopt Target Methods may have their flow created multiple times, this
             * optimization is not allowed.
             */
            return multiMethodKey != DEOPT_TARGET_METHOD;
        }

        @Override
        public boolean insertPlaceholderParamAndReturnFlows(MultiMethod.MultiMethodKey multiMethodKey) {
            return multiMethodKey == DEOPT_TARGET_METHOD || multiMethodKey == RUNTIME_COMPILED_METHOD;
        }
    }

    /**
     * Converts {@link MacroWithExceptionNode}s into explicit {@link InvokeWithExceptionNode}s. This
     * is necessary to ensure a MacroNode within runtime compilation converted back to an invoke
     * will always have a proper deoptimization target.
     */
    static class ConvertMacroNodes extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            for (Node n : graph.getNodes().snapshot()) {
                VMError.guarantee(!(n instanceof MacroNode), "DeoptTarget Methods do not support Macro Nodes: method %s, node %s", graph.method(), n);

                if (n instanceof MacroWithExceptionNode macro) {
                    macro.replaceWithInvoke();
                }
            }
        }
    }

    /**
     * Removes {@link DeoptEntryNode}s, {@link DeoptProxyAnchorNode}s, and {@link DeoptProxyNode}s
     * which are determined to be unnecessary after the runtime compilation methods are optimized.
     */
    static class RemoveUnneededDeoptSupport extends Phase {
        enum RemovalDecision {
            KEEP,
            PROXIFY,
            REMOVE
        }

        @Override
        protected void run(StructuredGraph graph) {
            EconomicMap<StateSplit, RemovalDecision> decisionCache = EconomicMap.create();

            // First go through and delete all unneeded proxies
            for (DeoptProxyNode proxyNode : graph.getNodes(DeoptProxyNode.TYPE).snapshot()) {
                ValueNode proxyPoint = proxyNode.getProxyPoint();
                if (proxyPoint instanceof StateSplit) {
                    if (getDecision((StateSplit) proxyPoint, decisionCache) == RemovalDecision.REMOVE) {
                        proxyNode.replaceAtAllUsages(proxyNode.getOriginalNode(), true);
                        proxyNode.safeDelete();
                    }
                }
            }

            // Next, remove all unneeded DeoptEntryNodes
            for (DeoptEntryNode deoptEntry : graph.getNodes().filter(DeoptEntryNode.class).snapshot()) {
                switch (getDecision(deoptEntry, decisionCache)) {
                    case REMOVE -> {
                        deoptEntry.killExceptionEdge();
                        graph.removeSplit(deoptEntry, deoptEntry.getPrimarySuccessor());
                    }
                    case PROXIFY -> {
                        deoptEntry.killExceptionEdge();
                        DeoptProxyAnchorNode newAnchor = graph.add(new DeoptProxyAnchorNode(deoptEntry.getProxifiedInvokeBci()));
                        newAnchor.setStateAfter(deoptEntry.stateAfter());
                        graph.replaceSplitWithFixed(deoptEntry, newAnchor, deoptEntry.getPrimarySuccessor());
                    }
                }
            }

            // Finally, remove all unneeded DeoptProxyAnchorNodes
            for (DeoptProxyAnchorNode proxyAnchor : graph.getNodes().filter(DeoptProxyAnchorNode.class).snapshot()) {
                if (getDecision(proxyAnchor, decisionCache) == RemovalDecision.REMOVE) {
                    graph.removeFixed(proxyAnchor);
                }
            }
        }

        RemovalDecision getDecision(StateSplit node, EconomicMap<StateSplit, RemovalDecision> decisionCache) {
            RemovalDecision cached = decisionCache.get(node);
            if (cached != null) {
                return cached;
            }

            DeoptEntrySupport proxyNode;
            if (node instanceof ExceptionObjectNode exceptionObject) {
                /*
                 * For the exception edge of a DeoptEntryNode, we insert the proxies on the
                 * exception object.
                 */
                proxyNode = (DeoptEntrySupport) exceptionObject.predecessor();
            } else {
                proxyNode = (DeoptEntrySupport) node;
            }

            RemovalDecision decision = RemovalDecision.REMOVE;
            var directive = SubstrateCompilationDirectives.singleton();
            FrameState state = proxyNode.stateAfter();
            HostedMethod method = (HostedMethod) state.getMethod();
            if (proxyNode instanceof DeoptEntryNode) {
                if (directive.isDeoptEntry(method, state.bci, state.duringCall(), state.rethrowException())) {
                    // must keep all deopt entries which are still guarding nodes
                    decision = RemovalDecision.KEEP;
                }
            }

            if (decision == RemovalDecision.REMOVE) {
                // now check for any implicit deopt entry being protected against
                int proxifiedInvokeBci = proxyNode.getProxifiedInvokeBci();
                if (proxifiedInvokeBci != BytecodeFrame.UNKNOWN_BCI && directive.isDeoptEntry(method, proxifiedInvokeBci, true, false)) {
                    // must keep still keep a proxy for nodes which are "proxifying" an invoke
                    decision = proxyNode instanceof DeoptEntryNode ? RemovalDecision.PROXIFY : RemovalDecision.KEEP;
                }
            }

            // cache the decision
            decisionCache.put(node, decision);
            if (proxyNode != node) {
                decisionCache.put(proxyNode, decision);
            }
            return decision;
        }

        @Override
        public CharSequence getName() {
            return "RemoveUnneededDeoptSupport";
        }
    }
}
