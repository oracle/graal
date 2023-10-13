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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.core.common.spi.ConstantFieldProvider;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.java.BytecodeParser;
import jdk.compiler.graal.java.GraphBuilderPhase;
import jdk.compiler.graal.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.compiler.graal.nodes.CallTargetNode;
import jdk.compiler.graal.nodes.FixedGuardNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.GraphEncoder;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.LogicConstantNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.IntrinsicContext;
import jdk.compiler.graal.nodes.java.MethodCallTargetNode;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.IterativeConditionalEliminationPhase;
import jdk.compiler.graal.phases.common.inlining.InliningUtil;
import jdk.compiler.graal.phases.util.Providers;
import jdk.compiler.graal.truffle.phases.DeoptimizeOnExceptionPhase;
import jdk.compiler.graal.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;
import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Marker for current strategy for supporting RuntimeCompilationFeature. Eventually will be
 * supplanted by {@link ParseOnceRuntimeCompilationFeature}.
 */
public class LegacyRuntimeCompilationFeature extends RuntimeCompilationFeature implements Feature {
    protected Map<AnalysisMethod, CallTreeNode> runtimeCompiledMethodMap;
    protected Set<RuntimeCompilationCandidate> runtimeCompilationCandidates;

    private static final class CallTreeNode extends AbstractCallTreeNode implements RuntimeCompiledMethod, RuntimeCompilationCandidate {
        final String sourceReference;

        StructuredGraph graph;
        final Set<Invoke> unreachableInvokes;

        CallTreeNode(AnalysisMethod implementationMethod, AnalysisMethod targetMethod, CallTreeNode parent, String sourceReference) {
            super(parent, targetMethod, implementationMethod);
            this.sourceReference = sourceReference;
            this.unreachableInvokes = new HashSet<>();
        }

        @Override
        public String getPosition() {
            return sourceReference;
        }

        @Override
        public int getNodeCount() {
            return graph == null ? -1 : graph.getNodeCount();
        }

        private StructuredGraph getGraph() {
            return graph;
        }

        @Override
        public AnalysisMethod getMethod() {
            return getImplementationMethod();
        }

        @Override
        public Collection<ResolvedJavaMethod> getInlinedMethods() {
            return graph == null ? List.of() : graph.getMethods();
        }

        @Override
        public Collection<ResolvedJavaMethod> getInvokeTargets() {
            if (graph != null) {
                return graph.getNodes(MethodCallTargetNode.TYPE).stream().map(CallTargetNode::targetMethod).collect(Collectors.toUnmodifiableList());
            }
            return List.of();
        }

    }

    public static class RuntimeGraphBuilderPhase extends SubstrateGraphBuilderPhase {

        RuntimeGraphBuilderPhase(Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new RuntimeBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    public static class RuntimeBytecodeParser extends SubstrateGraphBuilderPhase.SubstrateBytecodeParser {

        RuntimeBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, false);
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

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return RuntimeCompilationFeature.getRequiredFeaturesHelper();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(!SubstrateOptions.ParseOnceJIT.getValue(), "This feature is only supported when ParseOnceJIT is not set");

        ImageSingletons.add(RuntimeCompilationFeature.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        super.duringSetupHelper(c);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess c) {
        super.beforeAnalysisHelper(c);

        runtimeCompiledMethodMap = new LinkedHashMap<>();
        runtimeCompilationCandidates = new HashSet<>();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess c) {
        FeatureImpl.DuringAnalysisAccessImpl config = (FeatureImpl.DuringAnalysisAccessImpl) c;

        Deque<CallTreeNode> worklist = new ArrayDeque<>();
        worklist.addAll(runtimeCompiledMethodMap.values());

        while (!worklist.isEmpty()) {
            processMethod(worklist.removeFirst(), worklist, config.getBigBang());
        }

        SubstrateMethod[] methodsToCompileArr = new SubstrateMethod[runtimeCompiledMethodMap.size()];
        int idx = 0;
        for (CallTreeNode node : runtimeCompiledMethodMap.values()) {
            methodsToCompileArr[idx++] = objectReplacer.createMethod(node.getImplementationMethod());
        }
        if (GraalSupport.setMethodsToCompile(config, methodsToCompileArr)) {
            config.requireAnalysisIteration();
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

    @SuppressWarnings("try")
    private void processMethod(CallTreeNode node, Deque<CallTreeNode> worklist, BigBang bb) {
        AnalysisMethod method = node.getImplementationMethod();
        assert method.isImplementationInvoked();

        if (node.graph == null) {
            if (method.getAnnotation(Fold.class) != null || method.getAnnotation(Node.NodeIntrinsic.class) != null) {
                throw VMError.shouldNotReachHere("Parsing method annotated with @Fold or @NodeIntrinsic: " + method.format("%H.%n(%p)"));
            }
            if (!method.allowRuntimeCompilation()) {
                throw VMError.shouldNotReachHere("Parsing method that is not available for runtime compilation: " + method.format("%H.%n(%p)"));
            }

            boolean parse = false;

            DebugContext debug = bb.getDebug();
            StructuredGraph graph = method.buildGraph(debug, method, hostedProviders, GraphProvider.Purpose.PREPARE_RUNTIME_COMPILATION);
            if (graph == null) {
                if (!method.hasBytecodes()) {
                    return;
                }
                parse = true;
                graph = new StructuredGraph.Builder(debug.getOptions(), debug, StructuredGraph.AllowAssumptions.YES).method(method)
                                /*
                                 * Needed for computation of the list of all runtime compilable
                                 * methods in TruffleFeature.
                                 */
                                .recordInlinedMethods(true).build();
            }

            try (DebugContext.Scope scope = debug.scope("RuntimeCompile", graph)) {
                if (parse) {
                    RuntimeGraphBuilderPhase builderPhase = new RuntimeGraphBuilderPhase(hostedProviders, graphBuilderConfig, optimisticOpts, null, hostedProviders.getWordTypes());
                    builderPhase.apply(graph);
                }

                CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
                canonicalizer.apply(graph, hostedProviders);
                if (deoptimizeOnExceptionPredicate != null) {
                    new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate).apply(graph);
                }
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, hostedProviders);

                if (DeoptimizationUtils.createGraphInvalidator(graph).get()) {
                    return;
                }

                unwrapImageHeapConstants(graph, hostedProviders.getMetaAccess());

                graphEncoder.prepare(graph);
                node.graph = graph;
                assert RuntimeCompilationFeature.verifyNodes(graph);

            } catch (Throwable ex) {
                debug.handle(ex);
            }
        }

        assert node.graph != null;
        List<MethodCallTargetNode> callTargets = node.graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        callTargets.sort(Comparator.comparingInt(t -> t.invoke().bci()));

        for (MethodCallTargetNode targetNode : callTargets) {
            AnalysisMethod targetMethod = (AnalysisMethod) targetNode.targetMethod();
            ResolvedJavaMethod callerMethod = targetNode.invoke().stateAfter().getMethod();
            Collection<AnalysisMethod> allImplementationMethods;
            if (callerMethod instanceof PointsToAnalysisMethod) {
                PointsToAnalysisMethod pointToCalledMethod = (PointsToAnalysisMethod) callerMethod;
                InvokeTypeFlow invokeFlow = pointToCalledMethod.getTypeFlow().getInvokes().get(targetNode.invoke().bci());

                if (invokeFlow == null) {
                    continue;
                }
                allImplementationMethods = invokeFlow.getOriginalCallees();
            } else {
                allImplementationMethods = Arrays.asList(method.getImplementations());
            }

            /*
             * Eventually we want to remove all invokes that are unreachable, i.e., have no
             * implementation. But the analysis is iterative, and we don't know here if we have
             * already reached the fixed point. So we only collect unreachable invokes here, and
             * remove them after the analysis has finished.
             */
            if (allImplementationMethods.size() == 0) {
                node.unreachableInvokes.add(targetNode.invoke());
            } else {
                node.unreachableInvokes.remove(targetNode.invoke());
            }

            if (allImplementationMethods.size() > 0) {
                /* Sort to make printing order and method discovery order deterministic. */
                List<AnalysisMethod> implementationMethods = new ArrayList<>(allImplementationMethods);
                implementationMethods.sort(Comparator.comparing(AnalysisMethod::getQualifiedName));

                String sourceReference = buildSourceReference(targetNode.invoke().stateAfter());
                for (AnalysisMethod implementationMethod : implementationMethods) {
                    CallTreeNode calleeNode = new CallTreeNode(implementationMethod, targetMethod, node, sourceReference);
                    boolean added = runtimeCompilationCandidates.add(calleeNode);
                    if (added) {
                        calleeNode.linkAsChild();
                    }
                    /*
                     * Filter out all the implementation methods that have already been processed.
                     *
                     * We don't filter out earlier so that different <implementation-target>
                     * combinations are recorded for blocklist checking.
                     */
                    if (runtimeCompiledMethodMap.containsKey(implementationMethod)) {
                        continue;
                    }
                    if (runtimeCompilationCandidatePredicate.allowRuntimeCompilation(implementationMethod)) {
                        assert !runtimeCompiledMethodMap.containsKey(implementationMethod);
                        runtimeCompiledMethodMap.put(implementationMethod, calleeNode);
                        worklist.add(calleeNode);
                        objectReplacer.createMethod(implementationMethod);
                    }

                    /*
                     * We must compile all methods which may be called. It may be the case that a
                     * call target does not reach the compile queue by default, e.g. if it is
                     * inlined at image generation but not at runtime compilation.
                     */
                    SubstrateCompilationDirectives.singleton().registerForcedCompilation(implementationMethod);
                }
            }
        }
    }

    @Override
    protected AbstractCallTreeNode getCallTreeNode(RuntimeCompilationCandidate candidate) {
        assert candidate != null;
        return (CallTreeNode) candidate;
    }

    @Override
    protected CallTreeNode getCallTreeNode(RuntimeCompiledMethod method) {
        assert method != null;
        return (CallTreeNode) method;
    }

    @Override
    protected AbstractCallTreeNode getCallTreeNode(ResolvedJavaMethod method) {
        AnalysisMethod aMethod;
        if (method instanceof HostedMethod) {
            aMethod = ((HostedMethod) method).getWrapped();
        } else {
            aMethod = (AnalysisMethod) method;
        }
        var result = runtimeCompiledMethodMap.get(aMethod);
        assert result != null;
        return result;
    }

    @Override
    public Collection<RuntimeCompiledMethod> getRuntimeCompiledMethods() {
        return Collections.unmodifiableCollection(runtimeCompiledMethodMap.values());
    }

    @Override
    public Collection<RuntimeCompilationCandidate> getAllRuntimeCompilationCandidates() {
        return runtimeCompilationCandidates;
    }

    private static String buildSourceReference(FrameState startState) {
        StringBuilder sourceReferenceBuilder = new StringBuilder();
        for (FrameState state = startState; state != null; state = state.outerFrameState()) {
            if (sourceReferenceBuilder.length() > 0) {
                sourceReferenceBuilder.append(" -> ");
            }
            sourceReferenceBuilder.append(state.getCode().asStackTraceElement(state.bci).toString());
        }
        return sourceReferenceBuilder.toString();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        super.afterAnalysisHelper();
    }

    @Override
    @SuppressWarnings("try")
    public void beforeCompilation(BeforeCompilationAccess c) {
        super.beforeCompilationHelper();

        FeatureImpl.CompilationAccessImpl config = (FeatureImpl.CompilationAccessImpl) c;

        /*
         * Start fresh with a new GraphEncoder, since we are going to optimize all graphs now that
         * the static analysis results are available.
         */
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);

        StrengthenStampsPhase strengthenStamps = new RuntimeStrengthenStampsPhase(config.getUniverse(), objectReplacer);
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        IterativeConditionalEliminationPhase conditionalElimination = new IterativeConditionalEliminationPhase(canonicalizer, true);
        ConvertDeoptimizeToGuardPhase convertDeoptimizeToGuard = new ConvertDeoptimizeToGuardPhase(canonicalizer);

        for (CallTreeNode node : runtimeCompiledMethodMap.values()) {
            StructuredGraph graph = node.getGraph();
            if (graph != null) {
                DebugContext debug = graph.getDebug();
                try (DebugContext.Scope scope = debug.scope("RuntimeOptimize", graph)) {
                    removeUnreachableInvokes(node);
                    strengthenStamps.apply(graph);
                    canonicalizer.apply(graph, hostedProviders);

                    conditionalElimination.apply(graph, hostedProviders);

                    /*
                     * ConvertDeoptimizeToGuardPhase was already executed after parsing, but
                     * optimizations applied in between can provide new potential.
                     */
                    convertDeoptimizeToGuard.apply(graph, hostedProviders);

                    unwrapImageHeapConstants(graph, hostedProviders.getMetaAccess());

                    graphEncoder.prepare(graph);
                    assert RuntimeCompilationFeature.verifyNodes(graph);
                } catch (Throwable ex) {
                    debug.handle(ex);
                }
            }
        }

        graphEncoder.finishPrepare();

        for (CallTreeNode node : runtimeCompiledMethodMap.values()) {
            CallTreeNode callTreeNode = node;
            if (callTreeNode.graph != null) {
                DeoptimizationUtils.registerDeoptEntries(callTreeNode.graph, callTreeNode.getLevel() == 0, m -> m);

                long startOffset = graphEncoder.encode(callTreeNode.graph);
                objectReplacer.createMethod(callTreeNode.getImplementationMethod()).setEncodedGraphStartOffset(startOffset);
                /* We do not need the graph anymore, let the GC do it's work. */
                callTreeNode.graph = null;
            }
        }

        HeapBreakdownProvider.singleton().setGraphEncodingByteLength(graphEncoder.getEncoding().length);
        GraalSupport.setGraphEncoding(config, graphEncoder.getEncoding(), graphEncoder.getObjects(), graphEncoder.getNodeClasses());

        objectReplacer.setMethodsImplementations();

        /* All the temporary data structures used during encoding are no longer necessary. */
        graphEncoder = null;
    }

    private static void removeUnreachableInvokes(CallTreeNode node) {
        for (Invoke invoke : node.unreachableInvokes) {
            if (!invoke.asNode().isAlive()) {
                continue;
            }

            if (invoke.callTarget().invokeKind().hasReceiver()) {
                InliningUtil.nonNullReceiver(invoke);
            }
            FixedGuardNode guard = new FixedGuardNode(LogicConstantNode.forBoolean(true, node.graph), DeoptimizationReason.UnreachedCode, DeoptimizationAction.None, true);
            node.graph.addBeforeFixed(invoke.asFixedNode(), node.graph.add(guard));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        super.afterCompilationHelper(a);
    }

    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess a) {
        super.beforeHeapLayoutHelper(a);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        super.afterHeapLayoutHelper(a);
    }

    @Override
    public SubstrateMethod prepareMethodForRuntimeCompilation(ResolvedJavaMethod method, FeatureImpl.BeforeAnalysisAccessImpl config) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        if (!runtimeCompiledMethodMap.containsKey(aMethod)) {
            runtimeCompiledMethodMap.put(aMethod, new CallTreeNode(aMethod, aMethod, null, ""));
            config.registerAsRoot(aMethod, true, "Runtime compilation, registered in " + LegacyRuntimeCompilationFeature.class);
        }

        return sMethod;
    }

    @Override
    protected void requireFrameInformationForMethodHelper(AnalysisMethod aMethod, FeatureImpl.BeforeAnalysisAccessImpl config, boolean registerAsRoot) {
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(aMethod, aMethod);
        if (registerAsRoot) {
            config.registerAsRoot(aMethod, true, "Frame information required, registered in " + LegacyRuntimeCompilationFeature.class);
        }
    }

    @Override
    public void initializeAnalysisProviders(BigBang bb, Function<ConstantFieldProvider, ConstantFieldProvider> generator) {
        /*
         * No action is needed for the legacy implementation.
         */
    }

    @Override
    public void registerAllowInliningPredicate(AllowInliningPredicate predicate) {
        /*
         * No action is needed for the legacy implementation.
         */
    }

}
