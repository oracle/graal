/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted.runtimecompilation;

import static com.oracle.svm.common.meta.MultiMethod.DEOPT_TARGET_METHOD;
import static com.oracle.svm.common.meta.MultiMethod.ORIGINAL_METHOD;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptEntrySupport;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedConstantFieldProvider;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;

import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.nodes.MacroNode;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class infrastructure needed for creating the RuntimeCompiledMethods variants.
 */
public class RuntimeCompiledMethodSupport {

    public static class Options {
        @Option(help = "Remove Deopt(Entries,Anchors,Proxies) determined to be unneeded after the runtime compiled graphs have been finalized.")//
        public static final HostedOptionKey<Boolean> RemoveUnneededDeoptSupport = new HostedOptionKey<>(true);

        @Option(help = "Verify runtime compilation framestates during bytecode parsing.")//
        public static final HostedOptionKey<Boolean> VerifyRuntimeCompilationFrameStates = new HostedOptionKey<>(false);
    }

    private record CompilationState(
                    GraalGraphObjectReplacer objectReplacer,
                    GraphEncoder graphEncoder,
                    HostedProviders runtimeCompilationProviders,
                    ImageHeapScanner heapScanner,
                    Map<HostedMethod, StructuredGraph> runtimeGraphs,
                    Set<AnalysisMethod> registeredRuntimeCompilations) {
    }

    public static void onCompileQueueCreation(BigBang bb, HostedUniverse hUniverse, CompileQueue compileQueue, HostedProviders hostedProviders,
                    Function<ConstantFieldProvider, ConstantFieldProvider> constantFieldProviderWrapper,
                    GraalGraphObjectReplacer objectReplacer, Set<AnalysisMethod> registeredRuntimeCompilations, Stream<HostedMethod> methodsToCompile) {

        ImageHeapScanner imageScanner = bb.getUniverse().getHeapScanner();

        GraphEncoder graphEncoder = new RuntimeCompiledMethodSupport.RuntimeCompilationGraphEncoder(ConfigurationValues.getTarget().arch, imageScanner);
        HostedProviders runtimeCompilationProviders = hostedProviders //
                        .copyWith(constantFieldProviderWrapper.apply(new RuntimeCompilationFieldProvider(hostedProviders.getMetaAccess(), hUniverse))) //
                        .copyWith(new RuntimeCompilationReflectionProvider(bb, hUniverse.hostVM().getClassInitializationSupport()));

        SubstrateCompilationDirectives.singleton().resetDeoptEntries();

        CompilationState compilationState = new CompilationState(objectReplacer, graphEncoder, runtimeCompilationProviders, imageScanner, new ConcurrentHashMap<>(),
                        registeredRuntimeCompilations);

        /*
         * Customize runtime compile methods for compiling them into substrate graphs.
         */
        CompletionExecutor executor = compileQueue.getExecutor();
        try {
            compileQueue.runOnExecutor(() -> {
                methodsToCompile.forEach(method -> {
                    executor.execute(new RuntimeCompileTask(method, compilationState));
                });
            });
        } catch (InterruptedException exception) {
            VMError.shouldNotReachHere(exception);
        }

        encodeRuntimeCompiledMethods(compilationState);

        /*
         * For Deoptimization Targets add a custom phase which removes all deoptimization
         * entrypoints which are deemed no longer necessary.
         */
        CompileQueue.ParseHooks deoptParseHooks = new CompileQueue.ParseHooks(compileQueue) {
            @Override
            protected PhaseSuite<HighTierContext> getAfterParseSuite() {
                PhaseSuite<HighTierContext> suite = super.getAfterParseSuite();
                if (Options.RemoveUnneededDeoptSupport.getValue()) {
                    suite.prependPhase(new RemoveUnneededDeoptSupport());
                }

                return suite;
            }
        };

        hUniverse.getMethods().stream().map(method -> method.getMultiMethod(DEOPT_TARGET_METHOD)).filter(method -> {
            if (method != null) {
                return compileQueue.isRegisteredDeoptTarget(method);
            }
            return false;
        }).forEach(method -> method.compilationInfo.setCustomParseHooks(deoptParseHooks));

    }

    private static class RuntimeCompileTask implements CompletionExecutor.DebugContextRunnable {
        final HostedMethod method;
        final CompilationState compilationState;

        RuntimeCompileTask(HostedMethod method, CompilationState compilationState) {
            this.method = method;
            this.compilationState = compilationState;
        }

        @Override
        public DebugContext getDebug(OptionValues options, List<DebugHandlersFactory> factories) {
            return new DebugContext.Builder(options, factories).description(getDescription()).build();
        }

        @Override
        public void run(DebugContext debug) {
            compileRuntimeCompiledMethod(debug);
        }

        @SuppressWarnings("try")
        private void compileRuntimeCompiledMethod(DebugContext debug) {
            assert method.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;

            /*
             * The availability of NodeSourcePosition for JIT compilation is controlled by a
             * separate option and not TrackNodeSourcePosition to decouple AOT and JIT compilation.
             */
            boolean trackNodeSourcePosition = SubstrateOptions.IncludeNodeSourcePositions.getValue();

            AnalysisMethod aMethod = method.getWrapped();
            StructuredGraph graph = aMethod.decodeAnalyzedGraph(debug, null, trackNodeSourcePosition, false,
                            (arch, analyzedGraph) -> new RuntimeCompilationGraphDecoder(arch, analyzedGraph, compilationState.heapScanner));
            if (graph == null) {
                throw VMError.shouldNotReachHere("Method not parsed during static analysis: " + aMethod.format("%r %H.%n(%p)"));
            }
            /*
             * The graph in the analysis universe is no longer necessary once it is transplanted
             * into the hosted universe.
             */
            aMethod.setAnalyzedGraph(null);

            if (!trackNodeSourcePosition) {
                /*
                 * GR-52693: Even if a graph is built with trackNodeSourcePosition set to false,
                 * that explicit information is overwritten when the option TrackNodeSourcePosition
                 * is set to true. We therefore clear the NodeSourcePosition manually.
                 */
                for (Node node : graph.getNodes()) {
                    node.clearNodeSourcePosition();
                }
            }

            try (DebugContext.Scope s = debug.scope("RuntimeOptimize", graph, method, this)) {
                CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
                canonicalizer.apply(graph, compilationState.runtimeCompilationProviders);

                new DominatorBasedGlobalValueNumberingPhase(canonicalizer).apply(graph, compilationState.runtimeCompilationProviders);

                new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, compilationState.runtimeCompilationProviders);

                /*
                 * ConvertDeoptimizeToGuardPhase was already executed after parsing, but
                 * optimizations applied in between can provide new potential.
                 */
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, compilationState.runtimeCompilationProviders);

                /*
                 * More optimizations can be added here.
                 */
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            /*
             * Registering all deopt entries seen within the optimized graph. This should be
             * strictly a subset of the deopt entrypoints seen during evaluation.
             */
            AnalysisMethod origMethod = method.getMultiMethod(ORIGINAL_METHOD).getWrapped();
            DeoptimizationUtils.registerDeoptEntries(graph, compilationState.registeredRuntimeCompilations.contains(origMethod),
                            (deoptEntryMethod -> {
                                PointsToAnalysisMethod deoptMethod = (PointsToAnalysisMethod) ((PointsToAnalysisMethod) deoptEntryMethod).getMultiMethod(DEOPT_TARGET_METHOD);
                                VMError.guarantee(deoptMethod != null, "New deopt target method seen: %s", deoptEntryMethod);
                                return deoptMethod;
                            }));

            assert verifyNodes(graph);
            var previous = compilationState.runtimeGraphs.put(method, graph);
            assert previous == null;

            // graph encoder is not currently threadsafe
            synchronized (compilationState.graphEncoder) {
                compilationState.graphEncoder.prepare(graph);
            }
        }
    }

    /**
     * Checks if any illegal nodes are present within the graph. Runtime Compiled methods should
     * never have explicit BytecodeExceptions; instead they should have deoptimizations.
     */
    static boolean verifyNodes(StructuredGraph graph) {
        for (var node : graph.getNodes()) {
            boolean invalidNodeKind = node instanceof BytecodeExceptionNode || node instanceof ThrowBytecodeExceptionNode;
            assert !invalidNodeKind : "illegal node in graph: " + node + " method: " + graph.method();
        }
        return true;
    }

    @SuppressWarnings("try")
    private static void encodeRuntimeCompiledMethods(CompilationState compilationState) {
        compilationState.graphEncoder.finishPrepare();

        // at this point no new deoptimization entrypoints can be registered.
        SubstrateCompilationDirectives.singleton().sealDeoptimizationInfo();

        for (var runtimeInfo : compilationState.runtimeGraphs.entrySet()) {
            var graph = runtimeInfo.getValue();
            var method = runtimeInfo.getKey();
            DebugContext debug = new DebugContext.Builder(graph.getOptions(), new GraalDebugHandlersFactory(compilationState.runtimeCompilationProviders.getSnippetReflection())).build();
            graph.resetDebug(debug);
            try (DebugContext.Scope s = debug.scope("Graph Encoding", graph);
                            DebugContext.Activation a = debug.activate()) {
                long startOffset = compilationState.graphEncoder.encode(graph);
                compilationState.objectReplacer.createMethod(method).setEncodedGraphStartOffset(startOffset);
            } catch (Throwable ex) {
                debug.handle(ex);
            }
        }

        HeapBreakdownProvider.singleton().setGraphEncodingByteLength(compilationState.graphEncoder.getEncoding().length);
        com.oracle.svm.graal.TruffleRuntimeCompilationSupport.setGraphEncoding(null, compilationState.graphEncoder.getEncoding(), compilationState.graphEncoder.getObjects(),
                        compilationState.graphEncoder.getNodeClasses());

        compilationState.objectReplacer.setMethodsImplementations();
    }

    /**
     * Since we perform runtime compilation after universe creation, we can leverage components of
     * the hosted universe provider for identifying final fields.
     */
    static class RuntimeCompilationFieldProvider extends AnalysisConstantFieldProvider {
        final HostedUniverse hUniverse;

        RuntimeCompilationFieldProvider(MetaAccessProvider metaAccess, HostedUniverse hUniverse) {
            super(metaAccess, hUniverse.hostVM());
            this.hUniverse = hUniverse;
        }

        @Override
        public boolean isFinalField(ResolvedJavaField f, ConstantFieldTool<?> tool) {
            HostedField hField = hUniverse.lookup(f);
            if (HostedConstantFieldProvider.isFinalField(hField)) {
                return true;
            }
            return super.isFinalField(f, tool);
        }
    }

    static class RuntimeCompilationReflectionProvider extends AnalysisConstantReflectionProvider {

        RuntimeCompilationReflectionProvider(BigBang bb, ClassInitializationSupport classInitializationSupport) {
            super(bb.getUniverse(), bb.getMetaAccess(), classInitializationSupport);
        }

        @Override
        public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
            /*
             * We cannot fold simulated values during initial before-analysis graph creation;
             * however, this runs after analysis has completed.
             */
            return readValue((AnalysisField) field, receiver, true);
        }
    }

    /**
     * A graph encoder that unwraps the {@link ImageHeapConstant} objects. This is used both after
     * analysis and after compilation. The corresponding graph decoder used during AOT compilation,
     * {@link RuntimeCompilationGraphDecoder}, looks-up the constant in the shadow heap and re-wraps
     * it.
     * <p>
     * The reason why we need to unwrap the {@link ImageHeapConstant}s after analysis, and not only
     * when we finally encode the graphs for run time compilation, is because the values in
     * {@link GraphEncoder#objectsArray} are captured in GraalSupport#graphObjects and
     * SubstrateReplacements#snippetObjects which are then scanned.
     */
    @SuppressWarnings("javadoc")
    public static class RuntimeCompilationGraphEncoder extends GraphEncoder {

        private final ImageHeapScanner heapScanner;
        /**
         * Cache already converted location identity objects to avoid creating multiple new
         * instances for the same underlying location identity.
         */
        private final Map<ImageHeapConstant, LocationIdentity> locationIdentityCache;

        public RuntimeCompilationGraphEncoder(Architecture architecture, ImageHeapScanner heapScanner) {
            super(architecture);
            this.heapScanner = heapScanner;
            this.locationIdentityCache = new ConcurrentHashMap<>();
        }

        @Override
        protected void addObject(Object object) {
            super.addObject(hostedToRuntime(object));
        }

        @Override
        protected void writeObjectId(Object object) {
            super.writeObjectId(hostedToRuntime(object));
        }

        @Override
        protected GraphDecoder graphDecoderForVerification(StructuredGraph decodedGraph) {
            return new RuntimeCompilationGraphDecoder(architecture, decodedGraph, heapScanner);
        }

        private Object hostedToRuntime(Object object) {
            if (object instanceof ImageHeapConstant heapConstant) {
                return SubstrateGraalUtils.hostedToRuntime(heapConstant, heapScanner.getConstantReflection());
            } else if (object instanceof ObjectLocationIdentity oli && oli.getObject() instanceof ImageHeapConstant heapConstant) {
                return locationIdentityCache.computeIfAbsent(heapConstant, (hc) -> ObjectLocationIdentity.create(SubstrateGraalUtils.hostedToRuntime(hc, heapScanner.getConstantReflection())));
            }
            return object;
        }
    }

    static class RuntimeCompilationGraphDecoder extends GraphDecoder {

        private final ImageHeapScanner heapScanner;
        /**
         * Cache already converted location identity objects to avoid creating multiple new
         * instances for the same underlying location identity.
         */
        private final Map<JavaConstant, LocationIdentity> locationIdentityCache;

        RuntimeCompilationGraphDecoder(Architecture architecture, StructuredGraph graph, ImageHeapScanner heapScanner) {
            super(architecture, graph);
            this.heapScanner = heapScanner;
            this.locationIdentityCache = new ConcurrentHashMap<>();
        }

        @Override
        protected Object readObject(MethodScope methodScope) {
            Object object = super.readObject(methodScope);
            if (object instanceof JavaConstant constant) {
                return SubstrateGraalUtils.runtimeToHosted(constant, heapScanner);
            } else if (object instanceof ObjectLocationIdentity oli) {
                return locationIdentityCache.computeIfAbsent(oli.getObject(), (constant) -> ObjectLocationIdentity.create(SubstrateGraalUtils.runtimeToHosted(constant, heapScanner)));
            }
            return object;
        }
    }

    static final class RuntimeGraphBuilderPhase extends AnalysisGraphBuilderPhase {

        private RuntimeGraphBuilderPhase(Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, SVMHost hostVM) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, hostVM);
        }

        static RuntimeGraphBuilderPhase createRuntimeGraphBuilderPhase(BigBang bb, Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {

            // Adjust graphbuilderconfig to match analysis phase
            var newGraphBuilderConfig = graphBuilderConfig.withEagerResolving(true).withUnresolvedIsError(false);
            return new RuntimeGraphBuilderPhase(providers, newGraphBuilderConfig, optimisticOpts, null, (SVMHost) bb.getHostVM());
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
            return Options.VerifyRuntimeCompilationFrameStates.getValue();
        }
    }

    /**
     * Converts {@link MacroWithExceptionNode}s into explicit {@link InvokeWithExceptionNode}s. This
     * is necessary to ensure a MacroNode within runtime compilation converted back to an invoke
     * will always have a proper deoptimization target.
     */
    public static class ConvertMacroNodes extends Phase {
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
    private static class RemoveUnneededDeoptSupport extends Phase {
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
                if (directive.isDeoptEntry(method, state.bci, state.getStackState())) {
                    // must keep all deopt entries which are still guarding nodes
                    decision = RemovalDecision.KEEP;
                }
            }

            if (decision == RemovalDecision.REMOVE) {
                // now check for any implicit deopt entry being protected against
                int proxifiedInvokeBci = proxyNode.getProxifiedInvokeBci();
                if (proxifiedInvokeBci != BytecodeFrame.UNKNOWN_BCI && directive.isDeoptEntry(method, proxifiedInvokeBci, FrameState.StackState.AfterPop)) {
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
