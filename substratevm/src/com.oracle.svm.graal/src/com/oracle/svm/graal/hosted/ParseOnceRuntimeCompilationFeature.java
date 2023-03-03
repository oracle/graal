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

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
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
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
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
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Runtime compilation strategy used when {@link com.oracle.svm.core.SubstrateOptions#ParseOnceJIT}
 * is enabled.
 */
public class ParseOnceRuntimeCompilationFeature extends RuntimeCompilationFeature implements Feature, RuntimeCompilationSupport {

    public static class Options {
        /*
         * Note this phase is currently overly aggressive and can illegally remove proxies. This
         * will be fixed in GR-44459.
         */
        @Option(help = "Remove Deopt(Entries,Anchors,Proxies) determined to be unneeded after the runtime compiled graphs have been finalized.")//
        public static final HostedOptionKey<Boolean> RemoveUnneededDeoptSupport = new HostedOptionKey<>(false);
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

        private RuntimeCompiledMethodImpl(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public AnalysisMethod getMethod() {
            return method;
        }

        @Override
        public Collection<ResolvedJavaMethod> getInlinedMethods() {
            /*
             * Currently no inlining is performed when ParseOnceJIT is enabled.
             */
            return List.of();
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

    public static class RuntimeGraphBuilderPhase extends AnalysisGraphBuilderPhase {

        RuntimeGraphBuilderPhase(Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes, SVMHost hostVM) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes, hostVM);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new RuntimeBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext, hostVM);
        }
    }

    public static class RuntimeBytecodeParser extends AnalysisGraphBuilderPhase.AnalysisBytecodeParser {

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
    }

    private final Set<AnalysisMethod> registeredRuntimeCompilations = ConcurrentHashMap.newKeySet();
    private final Set<SubstrateMethod> substrateAnalysisMethods = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisMethod, String> invalidForRuntimeCompilation = new ConcurrentHashMap<>();
    private final Set<RuntimeCompilationCandidate> runtimeCompilationCandidates = ConcurrentHashMap.newKeySet();
    private Set<RuntimeCompiledMethod> runtimeCompilations = null;
    private Map<RuntimeCompilationCandidate, CallTreeNode> runtimeCandidateCallTree = null;
    private Map<AnalysisMethod, CallTreeNode> runtimeCompiledMethodCallTree = null;
    private HostedProviders analysisProviders = null;

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

        if (objectReplacer.updateDataDuringAnalysis()) {
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
                boolean added = runtimeCompilations.add(new RuntimeCompiledMethodImpl(method));
                if (added) {
                    assert runtimeCompiledMethodCallTree.containsKey(method);
                }
            }
        }

        // call super after
        afterAnalysisHelper();
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
                AnalysisMethod target = invokeInfo.getTargetMethod();
                for (AnalysisMethod implementation : invokeInfo.getAllCallees()) {
                    if (implementation.getMultiMethodKey() == RUNTIME_COMPILED_METHOD) {
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
        DeoptimizationUtils.registerDeoptEntries(graph, registeredRuntimeCompilations.contains(origMethod), ParseOnceRuntimeCompilationFeature::getDeoptTargetMethod);

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

        ProgressReporter.singleton().setGraphEncodingByteLength(graphEncoder.getEncoding().length);
        GraalSupport.setGraphEncoding(null, graphEncoder.getEncoding(), graphEncoder.getObjects(), graphEncoder.getNodeClasses());

        objectReplacer.updateDataDuringAnalysis();

        /* All the temporary data structures used during encoding are no longer necessary. */
        graphEncoder = null;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess c) {
        beforeCompilationHelper();

        System.out.println("Num runtime parsed methods " + parsedRuntimeMethods.size());
        System.out.println("Num deopt parsed methods " + parsedDeoptMethods.size());
        System.out.println("total count of runtime parsed methods " + totalParsedRuntimeMethods.get());
        System.out.println("total count of deopt parsed methods " + totalParsedDeoptMethods.get());

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
            config.registerAsRoot(aMethod, true);
        }

        return sMethod;
    }

    private static ResolvedJavaMethod getDeoptTargetMethod(ResolvedJavaMethod method) {
        PointsToAnalysisMethod deoptMethod = (PointsToAnalysisMethod) ((PointsToAnalysisMethod) method).getMultiMethod(DEOPT_TARGET_METHOD);
        VMError.guarantee(deoptMethod != null, "I need to implement this");
        return deoptMethod;
    }

    @Override
    protected void requireFrameInformationForMethodHelper(AnalysisMethod aMethod) {
        /*
         * Note: it may be necessary to also register this method as a registeredRuntimeCompilation
         * (or in a new datastructure) to ensure these deoptimization targets are parsed during
         * analysis.
         */
        AnalysisMethod deoptTarget = aMethod.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(aMethod, deoptTarget);
    }

    private class RuntimeCompilationParsingSupport implements SVMParsingSupport {

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
        public Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method) {
            // want to have a couple more checks here that are in DeoptimizationUtils
            if (method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD) {
                return parseRuntimeCompiledMethod(bb, debug, method);
            }
            return HostVM.PARSING_UNHANDLED;
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
                        new RuntimeGraphBuilderPhase(analysisProviders, graphBuilderConfig, optimisticOpts, null, analysisProviders.getWordTypes(), (SVMHost) bb.getHostVM()).apply(graph);
                    } catch (PermanentBailoutException ex) {
                        bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                        recordFailed(method);
                        return HostVM.PARSING_FAILED;
                    }
                }

                if (graph.getNodes(StackValueNode.TYPE).isNotEmpty()) {
                    /*
                     * Stack allocated memory is not seen by the deoptimization code, i.e., it is
                     * not copied in case of deoptimization. Also, pointers to it can be used for
                     * arbitrary address arithmetic, so we would not know how to update derived
                     * pointers into stack memory during deoptimization. Therefore, we cannot allow
                     * methods that allocate stack memory for runtime compilation. To remove this
                     * limitation, we would need to change how we handle stack allocated memory in
                     * Graal.
                     */
                    recordFailed(method);
                    return HostVM.PARSING_FAILED;
                }

                CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
                canonicalizer.apply(graph, analysisProviders);
                if (deoptimizeOnExceptionPredicate != null) {
                    new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate).apply(graph);
                }
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, analysisProviders);

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
            if (multiMethodKey != ORIGINAL_METHOD) {
                if (graph.getNodes(StackValueNode.TYPE).isNotEmpty()) {
                    /*
                     * Stack allocated memory is not seen by the deoptimization code, i.e., it is
                     * not copied in case of deoptimization. Also, pointers to it can be used for
                     * arbitrary address arithmetic, so we would not know how to update derived
                     * pointers into stack memory during deoptimization. Therefore, we cannot allow
                     * methods that allocate stack memory for runtime compilation. To remove this
                     * limitation, we would need to change how we handle stack allocated memory in
                     * Graal.
                     */
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
                Collection<ResolvedJavaMethod> recomputeMethods = DeoptimizationUtils.registerDeoptEntries(graph, registeredRuntimeCompilations.contains(origMethod),
                                ParseOnceRuntimeCompilationFeature::getDeoptTargetMethod);

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
            } else if (multiMethodKey == DEOPT_TARGET_METHOD) {
                parsedDeoptMethods.add(aMethod);
                totalParsedDeoptMethods.incrementAndGet();
            }

            return true;
        }
    }

    private class RuntimeCompilationAnalysisPolicy implements HostVM.MultiMethodAnalysisPolicy {

        @Override
        public <T extends AnalysisMethod> Collection<T> determineCallees(BigBang bb, T implementation, T target, MultiMethod.MultiMethodKey callerMultiMethodKey, InvokeTypeFlow parsingReason) {
            assert implementation.isOriginalMethod() && target.isOriginalMethod();

            // recording compilation candidate
            if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD) {
                runtimeCompilationCandidates.add(new RuntimeCompilationCandidateImpl(implementation, target));
            }

            boolean jitPossible = runtimeCompilationCandidatePredicate.allowRuntimeCompilation(implementation);
            if (!jitPossible) {
                assert !registeredRuntimeCompilations.contains(implementation) : "invalid method registered for runtime compilation";
                /*
                 * If this method cannot be jitted, then only the original implementation is needed.
                 */
                return List.of(implementation);
            }

            if (callerMultiMethodKey == ORIGINAL_METHOD) {
                /*
                 * Unless the method is a registered runtime compilation, it is not possible for an
                 * original variant to call a runtime variant (and indirectly the deoptimiztation
                 * variant).
                 */
                if (registeredRuntimeCompilations.contains(implementation)) {
                    return List.of(implementation, getDeoptVersion(implementation), getRuntimeVersion(bb, implementation, true, parsingReason));
                } else {
                    return List.of(implementation);
                }
            } else if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD) {
                /*
                 * The runtime method can call all three types: original (if it is not partial
                 * evaluated), runtime (if it is partial evaluated), and deoptimized (if the runtime
                 * deoptimizes).
                 */
                return List.of(implementation, getDeoptVersion(implementation), getRuntimeVersion(bb, implementation, true, parsingReason));
            } else {
                assert callerMultiMethodKey == DEOPT_TARGET_METHOD;
                /*
                 * A deoptimization target will always call the original method. However, the return
                 * can also be from a deoptimized version when a deoptimization is triggered in an
                 * inlined callee. In addition, because we want runtime information to flow into
                 * this method via the return, we also need to link against the runtime variant. We
                 * only register the runtime variant as a stub though because its flow only needs to
                 * be made upon it being reachable from a runtime compiled method's invoke.
                 */
                return List.of(implementation, getDeoptVersion(implementation), getRuntimeVersion(bb, implementation, false, parsingReason));
            }

        }

        @SuppressWarnings("unchecked")
        protected <T extends AnalysisMethod> T getDeoptVersion(T implementation) {
            /*
             * Flows for deopt versions are only created once a frame state for the method is seen
             * within a runtime compiled method.
             */
            return (T) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
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
            return multiMethodKey != DEOPT_TARGET_METHOD;
        }

        @Override
        public boolean insertPlaceholderParamAndReturnFlows(MultiMethod.MultiMethodKey multiMethodKey) {
            return multiMethodKey == DEOPT_TARGET_METHOD || multiMethodKey == RUNTIME_COMPILED_METHOD;
        }
    }

    /**
     * Removes Deoptimizations Entrypoints which are deemed to be unnecessary after the runtime
     * compilation methods are optimized.
     */
    static class RemoveUnneededDeoptSupport extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            EconomicMap<StateSplit, Boolean> decisionCache = EconomicMap.create();

            // First go through and delete all unneeded proxies
            for (DeoptProxyNode proxyNode : graph.getNodes(DeoptProxyNode.TYPE).snapshot()) {
                ValueNode proxyPoint = proxyNode.getProxyPoint();
                if (proxyPoint instanceof StateSplit) {
                    if (proxyPoint instanceof DeoptEntryNode && shouldRemove((StateSplit) proxyPoint, decisionCache)) {
                        proxyNode.replaceAtAllUsages(proxyNode.getOriginalNode(), true);
                        proxyNode.safeDelete();
                    }
                }
            }

            // Next remove all unneeded DeoptEntryNodes
            for (DeoptEntryNode deoptEntry : graph.getNodes().filter(DeoptEntryNode.class).snapshot()) {
                if (shouldRemove(deoptEntry, decisionCache)) {
                    deoptEntry.killExceptionEdge();
                    graph.removeSplit(deoptEntry, deoptEntry.getPrimarySuccessor());
                }
            }
        }

        boolean shouldRemove(StateSplit node, EconomicMap<StateSplit, Boolean> decisionCache) {
            Boolean cached = decisionCache.get(node);
            if (cached != null) {
                return cached;
            }

            var directive = SubstrateCompilationDirectives.singleton();
            FrameState state = node.stateAfter();
            HostedMethod method = (HostedMethod) state.getMethod();

            boolean result = true;
            if (directive.isRegisteredDeoptTarget(method)) {
                result = !directive.isDeoptEntry(method, state.bci, state.duringCall(), state.rethrowException());
            }

            // cache the decision
            decisionCache.put(node, result);
            return result;
        }

        @Override
        public CharSequence getName() {
            return "RemoveDeoptEntries";
        }
    }
}
