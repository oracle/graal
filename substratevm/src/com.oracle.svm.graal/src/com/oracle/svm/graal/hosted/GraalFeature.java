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
package com.oracle.svm.graal.hosted;

import static com.oracle.svm.core.util.VMError.guarantee;

import java.lang.reflect.Executable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.infrastructure.GraphProvider.Purpose;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.SubstrateGraalRuntime;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompilationInfoSupport;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The main handler for running Graal in the Substrate VM at run time. This feature (and features it
 * depends on like {@link FieldsOffsetsFeature}) encodes Graal graphs for runtime compilation,
 * ensures that all required {@link SubstrateType}, {@link SubstrateMethod}, {@link SubstrateField}
 * objects are created by {@link GraalObjectReplacer} and added to the image. Data that is prepared
 * during image generation and used at run time is stored in {@link GraalSupport}.
 */
public final class GraalFeature implements Feature {

    public static class Options {
        @Option(help = "Print call tree of methods available for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompileMethods = new HostedOptionKey<>(false);

        @Option(help = "Print truffle boundaries found during the analysis")//
        public static final HostedOptionKey<Boolean> PrintStaticTruffleBoundaries = new HostedOptionKey<>(false);

        @Option(help = "Maximum number of methods allowed for runtime compilation.")//
        public static final HostedOptionKey<String[]> MaxRuntimeCompileMethods = new HostedOptionKey<>(new String[]{});

        @Option(help = "Enforce checking of maximum number of methods allowed for runtime compilation. Useful for checking in the gate that the number of methods does not go up without a good reason.")//
        public static final HostedOptionKey<Boolean> EnforceMaxRuntimeCompileMethods = new HostedOptionKey<>(false);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(GraalFeature.class);
        }
    }

    /**
     * This predicate is used to distinguish between building a Graal native image as a shared
     * library for HotSpot (non-pure) or Graal as a compiler used only for a runtime in the same
     * image (pure).
     */
    public static final class IsEnabledAndNotLibgraal implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(GraalFeature.class) && !SubstrateUtil.isBuildingLibgraal();
        }
    }

    public interface IncludeCalleePredicate {
        boolean includeCallee(CallTreeNode calleeNode, List<AnalysisMethod> implementationMethods);
    }

    public static final class CallTreeNode {
        protected final AnalysisMethod implementationMethod;
        protected final AnalysisMethod targetMethod;

        protected final CallTreeNode parent;
        protected final List<CallTreeNode> children;
        protected final int level;
        protected final String sourceReference;

        protected StructuredGraph graph;
        protected final Set<Invoke> unreachableInvokes;

        public CallTreeNode(ResolvedJavaMethod implementationMethod, ResolvedJavaMethod targetMethod, CallTreeNode parent, int level, String sourceReference) {
            this.implementationMethod = (AnalysisMethod) implementationMethod;
            this.targetMethod = (AnalysisMethod) targetMethod;
            this.parent = parent;
            this.level = level;
            this.sourceReference = sourceReference;
            this.children = new ArrayList<>();
            this.unreachableInvokes = new HashSet<>();
        }

        public AnalysisMethod getImplementationMethod() {
            return implementationMethod;
        }

        public AnalysisMethod getTargetMethod() {
            return targetMethod;
        }

        public CallTreeNode getParent() {
            return parent;
        }

        public List<CallTreeNode> getChildren() {
            return children;
        }

        public int getLevel() {
            return level;
        }

        public String getSourceReference() {
            return sourceReference;
        }

        public StructuredGraph getGraph() {
            return graph;
        }

        public void setGraph(StructuredGraph graph) {
            this.graph = graph;
        }
    }

    public static class RuntimeGraphBuilderPhase extends SubstrateGraphBuilderPhase {

        final CallTreeNode node;

        RuntimeGraphBuilderPhase(Providers providers,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes,
                        CallTreeNode node) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
            this.node = node;
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
        protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            boolean result = super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
            if (result) {
                CompilationInfoSupport.singleton().registerAsDeoptInlininingExclude(targetMethod);
            }
            return result;
        }

        public CallTreeNode getCallTreeNode() {
            return ((RuntimeGraphBuilderPhase) getGraphBuilderInstance()).node;
        }
    }

    private GraalObjectReplacer objectReplacer;
    private HostedProviders hostedProviders;
    private GraphEncoder graphEncoder;
    private SharedRuntimeConfigurationBuilder runtimeConfigBuilder;

    private boolean initialized;
    private GraphBuilderConfiguration graphBuilderConfig;
    private OptimisticOptimizations optimisticOpts;
    private IncludeCalleePredicate includeCalleePredicate;
    private Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;
    private Map<AnalysisMethod, CallTreeNode> methods;

    public StructuredGraph lookupMethodGraph(AnalysisMethod method) {
        CallTreeNode callTree = methods.get(method);
        assert callTree != null : "Unable to find method.";
        StructuredGraph graph = callTree.getGraph();
        assert graph != null : "Method's graph is null.";
        return graph;
    }

    public HostedProviders getHostedProviders() {
        return hostedProviders;
    }

    public GraalObjectReplacer getObjectReplacer() {
        return objectReplacer;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(DeoptimizationFeature.class, FieldsOffsetsFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        DuringSetupAccessImpl config = (DuringSetupAccessImpl) c;
        AnalysisMetaAccess aMetaAccess = config.getMetaAccess();

        try {
            /*
             * Check early that the classpath is set up correctly. The base class of SubstrateType
             * is the NodeClass from Truffle. So we require Truffle on the class path for any images
             * and tests that use Graal at run time.
             */
            aMetaAccess.lookupJavaType(SubstrateType.class);
        } catch (NoClassDefFoundError ex) {
            throw VMError.shouldNotReachHere("Building a native image with Graal support requires Truffle on the class path. For unit tests run with 'svmtest', add the option '--truffle'.");
        }

        ImageSingletons.add(GraalSupport.class, new GraalSupport());

        if (!ImageSingletons.contains(RuntimeGraalSetup.class)) {
            ImageSingletons.add(RuntimeGraalSetup.class, new SubstrateRuntimeGraalSetup());
        }
        GraalProviderObjectReplacements providerReplacements = ImageSingletons.lookup(RuntimeGraalSetup.class).getProviderObjectReplacements(aMetaAccess);
        objectReplacer = new GraalObjectReplacer(config.getUniverse(), aMetaAccess, providerReplacements);
        config.registerObjectReplacer(objectReplacer);

        config.registerClassReachabilityListener(GraalSupport::registerPhaseStatistics);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess c) {
        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) c;

        GraalSupport.allocatePhaseStatisticsCache();

        populateMatchRuleRegistry();

        Function<Providers, SubstrateBackend> backendProvider = GraalSupport.getRuntimeBackendProvider();
        ClassInitializationSupport classInitializationSupport = config.getHostVM().getClassInitializationSupport();
        Providers originalProviders = GraalAccess.getOriginalProviders();
        runtimeConfigBuilder = ImageSingletons.lookup(RuntimeGraalSetup.class)
                        .createRuntimeConfigurationBuilder(RuntimeOptionValues.singleton(), config.getHostVM(), config.getUniverse(), config.getMetaAccess(),
                                        originalProviders.getConstantReflection(), backendProvider, config.getNativeLibraries(), classInitializationSupport)
                        .build();
        RuntimeConfiguration runtimeConfig = runtimeConfigBuilder.getRuntimeConfig();

        Providers runtimeProviders = runtimeConfig.getProviders();
        WordTypes wordTypes = runtimeConfigBuilder.getWordTypes();
        hostedProviders = new HostedProviders(runtimeProviders.getMetaAccess(), runtimeProviders.getCodeCache(), runtimeProviders.getConstantReflection(), runtimeProviders.getConstantFieldProvider(),
                        runtimeProviders.getForeignCalls(), runtimeProviders.getLowerer(), runtimeProviders.getReplacements(), runtimeProviders.getStampProvider(),
                        runtimeConfig.getSnippetReflection(), wordTypes, runtimeProviders.getPlatformConfigurationProvider(), new GraphPrepareMetaAccessExtensionProvider());

        SubstrateGraalRuntime graalRuntime = new SubstrateGraalRuntime();
        objectReplacer.setGraalRuntime(graalRuntime);
        ImageSingletons.add(GraalRuntime.class, graalRuntime);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new GraalSupport.GraalShutdownHook());

        FeatureHandler featureHandler = config.getFeatureHandler();
        NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, runtimeConfig, hostedProviders, config.getMetaAccess(), config.getUniverse(), null, null, config.getNativeLibraries(),
                        config.getImageClassLoader(), false, false, ((Inflation) config.getBigBang()).getAnnotationSubstitutionProcessor(), new SubstrateClassInitializationPlugin(config.getHostVM()),
                        classInitializationSupport, ConfigurationValues.getTarget());
        DebugContext debug = DebugContext.forCurrentThread();
        NativeImageGenerator.registerReplacements(debug, featureHandler, runtimeConfig, runtimeConfig.getProviders(), runtimeConfig.getSnippetReflection(), false, true);
        featureHandler.forEachGraalFeature(feature -> feature.registerCodeObserver(runtimeConfig));
        Suites suites = NativeImageGenerator.createSuites(featureHandler, runtimeConfig, runtimeConfig.getSnippetReflection(), false);
        LIRSuites lirSuites = NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), false);
        Suites firstTierSuites = NativeImageGenerator.createFirstTierSuites(featureHandler, runtimeConfig, runtimeConfig.getSnippetReflection(), false);
        LIRSuites firstTierLirSuites = NativeImageGenerator.createFirstTierLIRSuites(featureHandler, runtimeConfig.getProviders(), false);
        GraalSupport.setRuntimeConfig(runtimeConfig, suites, lirSuites, firstTierSuites, firstTierLirSuites);

        NodeClass<?>[] snippetNodeClasses = ((SubstrateReplacements) runtimeProviders.getReplacements()).getSnippetNodeClasses();
        for (NodeClass<?> nodeClass : snippetNodeClasses) {
            config.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsAllocated(null);
        }

        /* Initialize configuration with reasonable default values. */
        graphBuilderConfig = GraphBuilderConfiguration.getDefault(hostedProviders.getGraphBuilderPlugins()).withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly);
        includeCalleePredicate = GraalFeature::defaultIncludeCallee;
        optimisticOpts = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
        methods = new LinkedHashMap<>();
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);

        /*
         * Ensure that all snippet methods have their SubstrateMethod object created by the object
         * replacer, to avoid corner cases later when writing the native image.
         */
        for (ResolvedJavaMethod method : ((SubstrateReplacements) runtimeProviders.getReplacements()).getSnippetMethods()) {
            objectReplacer.apply(method);
        }
    }

    private static void populateMatchRuleRegistry() {
        GraalSupport.get().setMatchRuleRegistry(new HashMap<>());
        GraalConfiguration.instance().populateMatchRuleRegistry(GraalSupport.get().getMatchRuleRegistry());
    }

    @SuppressWarnings("unused")
    private static boolean defaultIncludeCallee(CallTreeNode calleeNode, List<AnalysisMethod> implementationMethods) {
        return false;
    }

    public void initializeRuntimeCompilationConfiguration(IncludeCalleePredicate newIncludeCalleePredicate) {
        initializeRuntimeCompilationConfiguration(hostedProviders, graphBuilderConfig, newIncludeCalleePredicate, deoptimizeOnExceptionPredicate);
    }

    public void initializeRuntimeCompilationConfiguration(HostedProviders newHostedProviders, GraphBuilderConfiguration newGraphBuilderConfig, IncludeCalleePredicate newIncludeCalleePredicate,
                    Predicate<ResolvedJavaMethod> newDeoptimizeOnExceptionPredicate) {
        guarantee(initialized == false, "runtime compilation configuration already initialized");
        initialized = true;

        hostedProviders = newHostedProviders;
        graphBuilderConfig = newGraphBuilderConfig;
        includeCalleePredicate = newIncludeCalleePredicate;
        deoptimizeOnExceptionPredicate = newDeoptimizeOnExceptionPredicate;

        if (SubstrateOptions.IncludeNodeSourcePositions.getValue()) {
            graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
        }
    }

    public SubstrateMethod requireFrameInformationForMethod(ResolvedJavaMethod method) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        CompilationInfoSupport.singleton().registerFrameInformationRequired(aMethod);

        return sMethod;
    }

    public SubstrateMethod prepareMethodForRuntimeCompilation(Executable method, BeforeAnalysisAccessImpl config) {
        return prepareMethodForRuntimeCompilation(config.getMetaAccess().lookupJavaMethod(method), config);
    }

    public SubstrateMethod prepareMethodForRuntimeCompilation(ResolvedJavaMethod method, BeforeAnalysisAccessImpl config) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        assert !methods.containsKey(aMethod);
        methods.put(aMethod, new CallTreeNode(aMethod, aMethod, null, 0, ""));
        config.registerAsInvoked(aMethod);

        return sMethod;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess c) {
        DuringAnalysisAccessImpl config = (DuringAnalysisAccessImpl) c;

        Deque<CallTreeNode> worklist = new ArrayDeque<>();
        worklist.addAll(methods.values());

        while (!worklist.isEmpty()) {
            processMethod(worklist.removeFirst(), worklist, config.getBigBang());
        }

        SubstrateMethod[] methodsToCompileArr = new SubstrateMethod[methods.size()];
        int idx = 0;
        for (CallTreeNode node : methods.values()) {
            methodsToCompileArr[idx++] = objectReplacer.createMethod(node.implementationMethod);
        }
        if (GraalSupport.setMethodsToCompile(methodsToCompileArr)) {
            config.requireAnalysisIteration();
        }

        graphEncoder.finishPrepare();
        AnalysisMetaAccess metaAccess = config.getMetaAccess();
        NodeClass<?>[] nodeClasses = graphEncoder.getNodeClasses();
        for (NodeClass<?> nodeClass : nodeClasses) {
            metaAccess.lookupJavaType(nodeClass.getClazz()).registerAsAllocated(null);
        }
        if (GraalSupport.setGraphEncoding(graphEncoder.getEncoding(), graphEncoder.getObjects(), nodeClasses)) {
            config.requireAnalysisIteration();
        }

        if (objectReplacer.updateDataDuringAnalysis(config.getMetaAccess())) {
            config.requireAnalysisIteration();
        }
    }

    @SuppressWarnings("try")
    private void processMethod(CallTreeNode node, Deque<CallTreeNode> worklist, BigBang bb) {
        AnalysisMethod method = node.implementationMethod;
        assert method.isImplementationInvoked();

        if (node.graph == null) {
            if (method.getAnnotation(Fold.class) != null || method.getAnnotation(NodeIntrinsic.class) != null) {
                throw VMError.shouldNotReachHere("Parsing method annotated with @Fold or @NodeIntrinsic: " + method.format("%H.%n(%p)"));
            }
            if (!method.allowRuntimeCompilation()) {
                throw VMError.shouldNotReachHere("Parsing method that is not available for runtime compilation: " + method.format("%H.%n(%p)"));
            }

            boolean parse = false;

            DebugContext debug = bb.getDebug();
            StructuredGraph graph = method.buildGraph(debug, method, hostedProviders, Purpose.PREPARE_RUNTIME_COMPILATION);
            if (graph == null) {
                if (!method.hasBytecodes()) {
                    return;
                }
                parse = true;
                graph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.YES).method(method).build();
            }

            try (DebugContext.Scope scope = debug.scope("RuntimeCompile", graph)) {
                if (parse) {
                    RuntimeGraphBuilderPhase builderPhase = new RuntimeGraphBuilderPhase(hostedProviders, graphBuilderConfig, optimisticOpts, null, hostedProviders.getWordTypes(), node);
                    builderPhase.apply(graph);
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
                    return;
                }

                CanonicalizerPhase.create().apply(graph, hostedProviders);
                if (deoptimizeOnExceptionPredicate != null) {
                    new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate).apply(graph);
                }
                new ConvertDeoptimizeToGuardPhase().apply(graph, hostedProviders);

                graphEncoder.prepare(graph);
                node.graph = graph;

            } catch (Throwable ex) {
                debug.handle(ex);
            }
        }

        assert node.graph != null;
        List<MethodCallTargetNode> callTargets = node.graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        callTargets.sort((t1, t2) -> Integer.compare(t1.invoke().bci(), t2.invoke().bci()));

        for (MethodCallTargetNode targetNode : callTargets) {
            AnalysisMethod targetMethod = (AnalysisMethod) targetNode.targetMethod();
            AnalysisMethod callerMethod = (AnalysisMethod) targetNode.invoke().stateAfter().getMethod();
            InvokeTypeFlow invokeFlow = callerMethod.getTypeFlow().getOriginalMethodFlows().getInvoke(targetNode.invoke().bci());

            if (invokeFlow == null) {
                continue;
            }
            Collection<AnalysisMethod> allImplementationMethods = invokeFlow.getCallees();

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

            List<AnalysisMethod> implementationMethods = new ArrayList<>();
            for (AnalysisMethod implementationMethod : allImplementationMethods) {
                /* Filter out all the implementation methods that have already been processed. */
                if (!methods.containsKey(implementationMethod)) {
                    implementationMethods.add(implementationMethod);
                }
            }

            if (implementationMethods.size() > 0) {
                /* Sort to make printing order and method discovery order deterministic. */
                implementationMethods.sort((m1, m2) -> m1.getQualifiedName().compareTo(m2.getQualifiedName()));

                String sourceReference = buildSourceReference(targetNode.invoke().stateAfter());
                for (AnalysisMethod implementationMethod : implementationMethods) {
                    CallTreeNode calleeNode = new CallTreeNode(implementationMethod, targetMethod, node, node.level + 1, sourceReference);
                    if (includeCalleePredicate.includeCallee(calleeNode, implementationMethods)) {
                        assert !methods.containsKey(implementationMethod);
                        methods.put(implementationMethod, calleeNode);
                        worklist.add(calleeNode);
                        node.children.add(calleeNode);
                        objectReplacer.createMethod(implementationMethod);
                    }

                    /*
                     * We must compile all methods which may be called. It may be the case that a
                     * call target does not reach the compile queue by default, e.g. if it is
                     * inlined at image generation but not at runtime compilation.
                     */
                    CompilationInfoSupport.singleton().registerForcedCompilation(implementationMethod);
                }
            }
        }
    }

    public static String buildSourceReference(FrameState startState) {
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
    @SuppressWarnings("try")
    public void beforeCompilation(BeforeCompilationAccess c) {
        CompilationAccessImpl config = (CompilationAccessImpl) c;

        if (Options.PrintRuntimeCompileMethods.getValue()) {
            printCallTree();
        }
        System.out.println(methods.size() + " method(s) included for runtime compilation");

        if (Options.PrintStaticTruffleBoundaries.getValue()) {
            printStaticTruffleBoundaries();
        }

        int maxMethods = 0;
        for (String value : Options.MaxRuntimeCompileMethods.getValue()) {
            String numberStr = null;
            try {
                /* Strip optional comment string from MaxRuntimeCompileMethods value */
                numberStr = value.split("#")[0];
                maxMethods += Long.parseLong(numberStr);
            } catch (NumberFormatException ex) {
                throw UserError.abort("Invalid value for option 'MaxRuntimeCompileMethods': '%s' is not a valid number", numberStr);
            }
        }
        if (Options.EnforceMaxRuntimeCompileMethods.getValue() && maxMethods != 0 && methods.size() > maxMethods) {
            printDeepestLevelPath();
            throw VMError.shouldNotReachHere("Number of methods for runtime compilation exceeds the allowed limit: " + methods.size() + " > " + maxMethods);
        }

        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        runtimeConfigBuilder.updateLazyState(hMetaAccess);

        /*
         * Start fresh with a new GraphEncoder, since we are going to optimize all graphs now that
         * the static analysis results are available.
         */
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);

        StrengthenStampsPhase strengthenStamps = new RuntimeStrengthenStampsPhase(config.getUniverse(), objectReplacer);
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        for (CallTreeNode node : methods.values()) {
            StructuredGraph graph = node.graph;
            if (graph != null) {
                DebugContext debug = graph.getDebug();
                try (DebugContext.Scope scope = debug.scope("RuntimeOptimize", graph)) {
                    removeUnreachableInvokes(node);
                    strengthenStamps.apply(graph);
                    canonicalizer.apply(graph, hostedProviders);
                    GraalConfiguration.instance().runAdditionalCompilerPhases(graph, this);
                    canonicalizer.apply(graph, hostedProviders);
                    graphEncoder.prepare(graph);
                } catch (Throwable ex) {
                    debug.handle(ex);
                }
            }
        }

        graphEncoder.finishPrepare();

        for (CallTreeNode node : methods.values()) {
            if (node.graph != null) {
                registerDeoptEntries(node);

                long startOffset = graphEncoder.encode(node.graph);
                objectReplacer.createMethod(node.implementationMethod).setEncodedGraphStartOffset(startOffset);
                /* We do not need the graph anymore, let the GC do it's work. */
                node.graph = null;
            }
        }

        GraalSupport.setGraphEncoding(graphEncoder.getEncoding(), graphEncoder.getObjects(), graphEncoder.getNodeClasses());

        objectReplacer.updateDataDuringAnalysis((AnalysisMetaAccess) hMetaAccess.getWrapped());
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
            node.graph.addBeforeFixed(invoke.asNode(), node.graph.add(guard));
        }
    }

    private static void registerDeoptEntries(CallTreeNode node) {
        for (FrameState frameState : node.graph.getNodes(FrameState.TYPE)) {
            if (node.level > 0 && frameState.hasExactlyOneUsage() && frameState.usages().first() == node.graph.start()) {
                /*
                 * During method inlining, the FrameState associated with the StartNode disappears.
                 * Therefore, this frame state cannot be a deoptimization target.
                 */
                continue;
            }

            /*
             * We need to make sure that all inlined caller frames are available for deoptimization
             * too.
             */
            for (FrameState inlineState = frameState; inlineState != null; inlineState = inlineState.outerFrameState()) {
                if (inlineState.bci >= 0) {
                    CompilationInfoSupport.singleton().registerDeoptEntry(inlineState.getMethod(), inlineState.bci, inlineState.duringCall(), inlineState.rethrowException());
                }
            }
        }

        for (Node n : node.graph.getNodes()) {
            /*
             * graph.getInvokes() only iterates invokes that have a MethodCallTarget, so by using it
             * we would miss invocations that are already intrinsified to an indirect call.
             */
            if (n instanceof Invoke) {
                Invoke invoke = (Invoke) n;

                /*
                 * The FrameState for the invoke (which is visited by the above loop) is the state
                 * after the call (where deoptimization that happens after the call has returned
                 * will continue execution). We also need to register the state during the call
                 * (where deoptimization while the call is on the stack will continue execution).
                 *
                 * Note that the bci of the Invoke and the bci of the FrameState of the Invoke are
                 * different: the Invoke has the bci of the invocation bytecode, the FrameState has
                 * the bci of the next bytecode after the invoke.
                 */
                CompilationInfoSupport.singleton().registerDeoptEntry(invoke.stateAfter().getMethod(), invoke.bci(), true, false);
            }
        }
    }

    private void printDeepestLevelPath() {
        CallTreeNode maxLevelCallTreeNode = null;
        for (CallTreeNode callTreeNode : methods.values()) {
            if (maxLevelCallTreeNode == null || maxLevelCallTreeNode.level < callTreeNode.level) {
                maxLevelCallTreeNode = callTreeNode;
            }
        }

        System.out.println(String.format("Deepest level call tree path (%d calls):", maxLevelCallTreeNode.level));
        CallTreeNode node = maxLevelCallTreeNode;
        while (node != null) {
            System.out.format("%5d ; %s ; %s", node.graph == null ? -1 : node.graph.getNodeCount(), node.sourceReference, node.implementationMethod == null ? ""
                            : node.implementationMethod.format("%H.%n(%p)"));
            if (node.targetMethod != null && !node.targetMethod.equals(node.implementationMethod)) {
                System.out.print(" ; " + node.targetMethod.format("%H.%n(%p)"));
            }
            System.out.println();
            node = node.parent;
        }
    }

    private void printStaticTruffleBoundaries() {
        HashSet<ResolvedJavaMethod> foundBoundaries = new HashSet<>();
        int callSiteCount = 0;
        int calleeCount = 0;
        for (CallTreeNode node : methods.values()) {
            StructuredGraph graph = node.graph;
            for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod targetMethod = callTarget.targetMethod();
                TruffleBoundary truffleBoundary = targetMethod.getAnnotation(TruffleBoundary.class);
                if (truffleBoundary != null) {
                    ++callSiteCount;
                    if (foundBoundaries.contains(targetMethod)) {
                        // nothing to do
                    } else {
                        foundBoundaries.add(targetMethod);
                        System.out.println("Truffle boundary found: " + targetMethod);
                        calleeCount++;
                    }
                }
            }
        }
        System.out.println(String.format("Number of Truffle call boundaries: %d, number of unique called methods outside the boundary: %d", callSiteCount, calleeCount));
    }

    private void printCallTree() {
        System.out.println("depth;method;Graal nodes;invoked from source;full method name;full name of invoked virtual method");
        for (CallTreeNode node : methods.values()) {
            if (node.level == 0) {
                printCallTreeNode(node);
            }
        }
        System.out.println();
    }

    private void printCallTreeNode(CallTreeNode node) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < node.level; i++) {
            indent.append("  ");
        }
        if (node.implementationMethod != null) {
            indent.append(node.implementationMethod.format("%h.%n"));
        }

        System.out.format("%4d ; %-80s  ;%5d ; %s ; %s", node.level, indent, node.graph == null ? -1 : node.graph.getNodeCount(), node.sourceReference,
                        node.implementationMethod == null ? "" : node.implementationMethod.format("%H.%n(%p)"));
        if (node.targetMethod != null && !node.targetMethod.equals(node.implementationMethod)) {
            System.out.print(" ; " + node.targetMethod.format("%H.%n(%p)"));
        }
        System.out.println();

        for (CallTreeNode child : node.children) {
            printCallTreeNode(child);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;

        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = (HostedUniverse) hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterCompilation(hUniverse);

        objectReplacer.registerImmutableObjects(config);
        GraalSupport.registerImmutableObjects(config);
        ((SubstrateReplacements) runtimeConfigBuilder.getRuntimeConfig().getProviders().getReplacements()).registerImmutableObjects(config);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        AfterHeapLayoutAccessImpl config = (AfterHeapLayoutAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = (HostedUniverse) hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterHeapLayout(hUniverse);
    }
}

/**
 * The graphs for runtime compilation use the analysis universe, but we want to incorporate static
 * analysis results from the hosted universe. So we temporarily convert metadata objects to the
 * hosted universe, and the final type back to the analysis universe.
 */
class RuntimeStrengthenStampsPhase extends StrengthenStampsPhase {

    private final HostedUniverse hUniverse;
    private final GraalObjectReplacer objectReplacer;

    RuntimeStrengthenStampsPhase(HostedUniverse hUniverse, GraalObjectReplacer objectReplacer) {
        this.hUniverse = hUniverse;
        this.objectReplacer = objectReplacer;
    }

    @Override
    protected HostedType toHosted(ResolvedJavaType type) {
        if (type == null) {
            return null;
        }
        assert type instanceof AnalysisType;
        return hUniverse.lookup(type);
    }

    @Override
    protected HostedMethod toHosted(ResolvedJavaMethod method) {
        if (method == null) {
            return null;
        }
        assert method instanceof AnalysisMethod;
        return hUniverse.lookup(method);
    }

    @Override
    protected HostedField toHosted(ResolvedJavaField field) {
        if (field == null) {
            return null;
        }
        assert field instanceof AnalysisField;
        return hUniverse.lookup(field);
    }

    @Override
    protected ResolvedJavaType toTarget(ResolvedJavaType type) {
        AnalysisType result = ((HostedType) type).getWrapped();

        if (!objectReplacer.typeCreated(result)) {
            /*
             * The SubstrateType has not been created during analysis. We cannot crate new types at
             * this point, because it can make objects reachable that the static analysis has not
             * seen.
             */
            return null;
        }

        return result;
    }
}

/**
 * Same behavior as {@link SubstrateMetaAccessExtensionProvider}, but operating on
 * {@link AnalysisType} instead of {@link SharedType} since parsing of graphs for runtime
 * compilation happens in the Analysis universe.
 */
class GraphPrepareMetaAccessExtensionProvider implements MetaAccessExtensionProvider {

    @Override
    public JavaKind getStorageKind(JavaType type) {
        return ((AnalysisType) type).getStorageKind();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
        assert type instanceof AnalysisType : "AnalysisType is required; AnalysisType lazily creates array types of any depth, so type cannot be null";
        return ((AnalysisType) type).isInstantiated();
    }
}
