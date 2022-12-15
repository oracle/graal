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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.util.Providers;
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
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.SVMParsingSupport;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Runtime compilation strategy used when {@link com.oracle.svm.core.SubstrateOptions#ParseOnceJIT}
 * is enabled.
 */
public class ParseOnceRuntimeCompilationFeature extends RuntimeCompilationFeature implements Feature {

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
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, svmHost);
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
    private final Map<AnalysisMethod, String> invalidForRuntimeCompilation = new ConcurrentHashMap<>();

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return RuntimeCompilationFeature.getRequiredFeaturesHelper();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SVMParsingSupport.class, new RuntimeCompilationParsingSupport());
        ImageSingletons.add(HostVM.MultiMethodAnalysisPolicy.class, new RuntimeCompilationAnalysisPolicy());
        ImageSingletons.add(RuntimeCompilationFeature.class, this);
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
    public void afterAnalysis(AfterAnalysisAccess access) {
        /*
         * At this point need to determine which methods are actually valid for runtime compilation
         * and make the call tree
         */
        buildRuntimeCallTree();

        // call super after
        afterAnalysisHelper();
    }

    // In reality do not need to build a call tree; can create traces as necessary
    public void buildRuntimeCallTree() {
        Queue<AnalysisMethod> worklist = new LinkedList<>();
        Map<AnalysisMethod, AbstractCallTreeNode> callTree = new HashMap<>();
        for (AnalysisMethod root : registeredRuntimeCompilations) {
            var runtimeRoot = root.getMultiMethod(RUNTIME_COMPILED_METHOD);
            if (runtimeRoot != null) {
                callTree.computeIfAbsent(runtimeRoot, (m) -> {
                    worklist.add(m);
                    return new CallTreeNode();
                });
            }
        }
        while (!worklist.isEmpty()) {
            AnalysisMethod caller = worklist.remove();
            for (InvokeInfo invokeInfo : caller.getInvokes()) {
                for (AnalysisMethod implementation : invokeInfo.getAllCallees()) {
                    if (implementation.getMultiMethodKey() == RUNTIME_COMPILED_METHOD) {
                        callTree.computeIfAbsent(implementation, (m) -> {
                            worklist.add(m);
                            return new CallTreeNode();
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

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        /*
         * Need to create the runtime compiled implementations
         */
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess c) {
        beforeCompilationHelper();

        System.out.println("Num runtime parsed methods " + parsedRuntimeMethods.size());
        System.out.println("Num deopt parsed methods " + parsedDeoptMethods.size());
        System.out.println("total count of runtime parsed methods " + totalParsedRuntimeMethods.get());
        System.out.println("total count of deopt parsed methods " + totalParsedDeoptMethods.get());

        /*
         * Need to create the runtime compiled implementations
         */
    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        afterCompilationHelper(a);
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

        if (registeredRuntimeCompilations.add(aMethod)) {
            config.registerAsRoot(aMethod, true);
        }

        return sMethod;
    }

    @Override
    protected void requireFrameInformationForMethodHelper(AnalysisMethod aMethod) {
        AnalysisMethod deoptTarget = aMethod.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(aMethod, deoptTarget);
    }

    private class RuntimeCompilationParsingSupport implements SVMParsingSupport {
        @Override
        public boolean allowAssumptions(AnalysisMethod method) {
            return method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;
        }

        @Override
        public Object parseGraph(BigBang bb, AnalysisMethod method) {
            // want to have a couple more checks here that are in DeoptimizationUtils
            if (method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD) {
                return parseRuntimeCompiledMethod(bb, method);
            }
            return HostVM.PARSING_UNHANDLED;
        }

        @SuppressWarnings("try")
        private Object parseRuntimeCompiledMethod(BigBang bb, AnalysisMethod method) {
            DebugContext debug = DebugContext.forCurrentThread();

            boolean parsed = false;

            StructuredGraph graph = method.buildGraph(debug, method, hostedProviders, GraphProvider.Purpose.PREPARE_RUNTIME_COMPILATION);
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
            try (DebugContext.Scope scope = debug.scope("RuntimeCompile", graph)) {
                if (parsed) {
                    new RuntimeGraphBuilderPhase(hostedProviders, graphBuilderConfig, optimisticOpts, null, hostedProviders.getWordTypes(), (SVMHost) bb.getHostVM()).apply(graph);
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
                canonicalizer.apply(graph, hostedProviders);
                if (deoptimizeOnExceptionPredicate != null) {
                    new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate).apply(graph);
                }
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, hostedProviders);

            } catch (Throwable ex) {
                debug.handle(ex);
            }

            return graph;
        }

        private void recordFailed(AnalysisMethod method) {
            // Will need to create post to invalidate other MethodTypeFlows (if they exist)
            invalidForRuntimeCompilation.computeIfAbsent(method, (m) -> "generic failure");
        }

        private ResolvedJavaMethod getDeoptTargetMethod(ResolvedJavaMethod method) {
            PointsToAnalysisMethod deoptMethod = (PointsToAnalysisMethod) ((PointsToAnalysisMethod) method).getMultiMethod(DEOPT_TARGET_METHOD);
            VMError.guarantee(deoptMethod != null, "I need to implement this");
            return deoptMethod;
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
                Collection<ResolvedJavaMethod> recomputeMethods = DeoptimizationUtils.registerDeoptEntries(graph, registeredRuntimeCompilations.contains(origMethod), this::getDeoptTargetMethod);

                /*
                 * If new frame states are found, then redo the type flow
                 */
                for (ResolvedJavaMethod method : recomputeMethods) {
                    assert MultiMethod.isDeoptTarget(method);
                    ((PointsToAnalysisMethod) method).getTypeFlow().updateFlowsGraph(bb, MethodFlowsGraph.GraphKind.FULL, null, true);
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

    private static class CallTreeNode implements AbstractCallTreeNode {

        @Override
        public AnalysisMethod getImplementationMethod() {
            return null;
        }

        @Override
        public AnalysisMethod getTargetMethod() {
            return null;
        }

        @Override
        public StructuredGraph getGraph() {
            return null;
        }

        @Override
        public String getSourceReference() {
            return null;
        }

        @Override
        public AbstractCallTreeNode getParent() {
            return null;
        }

        @Override
        public List<AbstractCallTreeNode> getChildren() {
            return null;
        }

        @Override
        public int getLevel() {
            return 0;
        }
    }

}
