/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
import static jdk.graal.compiler.java.BytecodeParserOptions.InlineDuringParsingMaxDepth;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.ParallelExecutionException;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.RuntimeCompilationCanaryFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.nodes.InlinedInvokeArgumentsNode;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.SubstrateGraalRuntime;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.TruffleRuntimeCompilationSupport;
import com.oracle.svm.graal.hosted.DeoptimizationFeature;
import com.oracle.svm.graal.hosted.FieldsOffsetsFeature;
import com.oracle.svm.graal.hosted.GraalCompilerFeature;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.graal.meta.SubstrateUniverseFactory;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.RuntimeCompilationCallbacks;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.SVMParsingSupport;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;

import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.ExplicitOOMEExceptionEdges;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.phases.DeoptimizeOnExceptionPhase;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The main handler for running the Graal compiler in the Substrate VM at run time. This feature
 * (and features it depends on like {@link FieldsOffsetsFeature}) encodes Graal graphs for runtime
 * compilation, ensures that all required {@link SubstrateType}, {@link SubstrateMethod},
 * {@link SubstrateField} objects are created by {@link GraalGraphObjectReplacer} and added to the
 * image. Data that is prepared during image generation and used at run time is stored in
 * {@link TruffleRuntimeCompilationSupport}.
 */
public final class RuntimeCompilationFeature implements Feature, RuntimeCompilationCallbacks {

    public static class Options {
        @Option(help = "Print methods available for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompileMethods = new HostedOptionKey<>(false);

        @Option(help = "Print call tree of methods reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompilationCallTree = new HostedOptionKey<>(false);

        @Option(help = "Maximum number of methods allowed for runtime compilation.", stability = OptionStability.STABLE)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> MaxRuntimeCompileMethods = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @Option(help = "Enforce checking of maximum number of methods allowed for runtime compilation. Useful for checking in the gate that the number of methods does not go up without a good reason.")//
        public static final HostedOptionKey<Boolean> EnforceMaxRuntimeCompileMethods = new HostedOptionKey<>(false);

        @Option(help = "Deprecated, option no longer has any effect.", deprecated = true, deprecationMessage = "It no longer has any effect, and no replacement is available")//
        public static final HostedOptionKey<Boolean> RuntimeCompilationInlineBeforeAnalysis = new HostedOptionKey<>(true);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(RuntimeCompilationFeature.class);
        }
    }

    public static RuntimeCompilationFeature singleton() {
        return ImageSingletons.lookup(RuntimeCompilationFeature.class);
    }

    public interface RuntimeCompilationCandidatePredicate {
        boolean allowRuntimeCompilation(ResolvedJavaMethod method);
    }

    public interface AllowInliningPredicate {
        enum InlineDecision {
            INLINE,
            INLINING_DISALLOWED,
            NO_DECISION
        }

        InlineDecision allowInlining(GraphBuilderContext builder, ResolvedJavaMethod target);
    }

    private GraalGraphObjectReplacer objectReplacer;
    private HostedProviders hostedProviders;
    private GraphEncoder graphEncoder;

    private boolean initialized;
    private GraphBuilderConfiguration graphBuilderConfig;
    private OptimisticOptimizations optimisticOpts;
    private RuntimeCompilationCandidatePredicate runtimeCompilationCandidatePredicate;
    private boolean runtimeCompilationCandidatePredicateUpdated = false;
    private Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;

    private SubstrateUniverseFactory universeFactory = new SubstrateUniverseFactory();

    private final Set<AnalysisMethod> registeredRuntimeCompilations = ConcurrentHashMap.newKeySet();
    private final Set<SubstrateMethod> substrateAnalysisMethods = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisMethod, String> invalidForRuntimeCompilation = new ConcurrentHashMap<>();
    private final Set<RuntimeCompilationCandidate> runtimeCompilationCandidates = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> runtimeCompilationsFailedDuringParsing = ConcurrentHashMap.newKeySet();
    private CallTreeInfo callTreeMetadata = null;
    private HostedProviders analysisProviders = null;
    private AllowInliningPredicate allowInliningPredicate = (builder, target) -> AllowInliningPredicate.InlineDecision.INLINING_DISALLOWED;
    private boolean allowInliningPredicateUpdated = false;
    private Function<ConstantFieldProvider, ConstantFieldProvider> constantFieldProviderWrapper = Function.identity();
    private Consumer<CallTreeInfo> blocklistChecker = (ignore) -> {
    };

    public HostedProviders getHostedProviders() {
        return hostedProviders;
    }

    public GraalGraphObjectReplacer getObjectReplacer() {
        return objectReplacer;
    }

    public void setUniverseFactory(SubstrateUniverseFactory universeFactory) {
        this.universeFactory = universeFactory;
    }

    @SuppressWarnings("unused")
    private static boolean defaultAllowRuntimeCompilation(ResolvedJavaMethod method) {
        return false;
    }

    public void initializeRuntimeCompilationForTesting(FeatureImpl.BeforeAnalysisAccessImpl config, RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate) {
        initializeRuntimeCompilationConfiguration(hostedProviders, graphBuilderConfig, newRuntimeCompilationCandidatePredicate, deoptimizeOnExceptionPredicate, blocklistChecker);
        initializeRuntimeCompilationForTesting(config);
    }

    public void initializeRuntimeCompilationForTesting(BeforeAnalysisAccessImpl config) {
        initializeAnalysisProviders(config.getBigBang(), provider -> provider);
    }

    public void initializeRuntimeCompilationConfiguration(HostedProviders newHostedProviders, GraphBuilderConfiguration newGraphBuilderConfig,
                    RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate,
                    Predicate<ResolvedJavaMethod> newDeoptimizeOnExceptionPredicate, Consumer<CallTreeInfo> newBlocklistChecker) {
        guarantee(initialized == false, "runtime compilation configuration already initialized");
        initialized = true;

        hostedProviders = newHostedProviders;

        graphBuilderConfig = newGraphBuilderConfig.withNodeSourcePosition(true).withOOMEExceptionEdges(ExplicitOOMEExceptionEdges.DisableOOMEExceptionEdges);
        assert !runtimeCompilationCandidatePredicateUpdated : "Updated compilation predicate multiple times";
        runtimeCompilationCandidatePredicate = newRuntimeCompilationCandidatePredicate;
        runtimeCompilationCandidatePredicateUpdated = true;
        deoptimizeOnExceptionPredicate = newDeoptimizeOnExceptionPredicate;
        blocklistChecker = newBlocklistChecker;
    }

    public SubstrateMethod requireFrameInformationForMethod(ResolvedJavaMethod method, BeforeAnalysisAccessImpl config, boolean registerAsRoot) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        assert aMethod.isOriginalMethod();
        AnalysisMethod deoptTarget = aMethod.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(aMethod, deoptTarget);
        if (registerAsRoot) {
            config.registerAsRoot(aMethod, true, "Frame information required, registered in " + RuntimeCompilationFeature.class, DEOPT_TARGET_METHOD);
        }

        return sMethod;
    }

    public SubstrateMethod prepareMethodForRuntimeCompilation(Executable method, BeforeAnalysisAccessImpl config) {
        return prepareMethodForRuntimeCompilation(config.getMetaAccess().lookupJavaMethod(method), config);
    }

    public void registerAllowInliningPredicate(AllowInliningPredicate predicate) {
        assert !allowInliningPredicateUpdated;
        allowInliningPredicate = predicate;
        allowInliningPredicateUpdated = true;
    }

    public void initializeAnalysisProviders(BigBang bb, Function<ConstantFieldProvider, ConstantFieldProvider> generator) {
        HostedProviders defaultProviders = bb.getProviders(ORIGINAL_METHOD);
        HostedProviders customHostedProviders = defaultProviders.copyWith(generator.apply(defaultProviders.getConstantFieldProvider()));
        constantFieldProviderWrapper = generator;
        customHostedProviders.setGraphBuilderPlugins(hostedProviders.getGraphBuilderPlugins());
        analysisProviders = customHostedProviders;
    }

    public String[] getCallTrace(CallTreeInfo callTreeInfo, AnalysisMethod method) {
        return CallTreeInfo.getCallTrace(callTreeInfo, method, registeredRuntimeCompilations);
    }

    public String[] getCallTrace(CallTreeInfo callTreeInfo, RuntimeCompilationCandidate candidate) {
        return CallTreeInfo.getCallTrace(callTreeInfo, candidate, registeredRuntimeCompilations);
    }

    public CallTreeInfo getCallTreeInfo() {
        VMError.guarantee(callTreeMetadata != null);
        return callTreeMetadata;
    }

    public Collection<RuntimeCompilationCandidate> getAllRuntimeCompilationCandidates() {
        return runtimeCompilationCandidates;
    }

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
            getStubDeoptVersion(aMethod);
            config.registerAsRoot(aMethod, true, "Runtime compilation, registered in " + RuntimeCompilationFeature.class, RUNTIME_COMPILED_METHOD, DEOPT_TARGET_METHOD);
        }

        return sMethod;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(RuntimeCompilationCanaryFeature.class, DeoptimizationFeature.class, GraalCompilerFeature.class);
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ImageSingletons.add(SVMParsingSupport.class, new RuntimeCompilationParsingSupport());
        ImageSingletons.add(HostVM.MultiMethodAnalysisPolicy.class, new RuntimeCompilationAnalysisPolicy());
        ImageSingletons.add(RuntimeCompilationCallbacks.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        if (SubstrateOptions.useLLVMBackend()) {
            throw UserError.abort("Runtime compilation is currently unimplemented on the LLVM backend (GR-43073).");
        }
        ImageSingletons.add(TruffleRuntimeCompilationSupport.class, new TruffleRuntimeCompilationSupport());
        if (!ImageSingletons.contains(SubstrateGraalCompilerSetup.class)) {
            ImageSingletons.add(SubstrateGraalCompilerSetup.class, new SubstrateGraalCompilerSetup());
        }

        DuringSetupAccessImpl config = (DuringSetupAccessImpl) c;
        AnalysisMetaAccess aMetaAccess = config.getMetaAccess();
        SubstrateWordTypes wordTypes = new SubstrateWordTypes(aMetaAccess, ConfigurationValues.getWordKind());
        SubstrateProviders substrateProviders = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class).getSubstrateProviders(aMetaAccess, wordTypes);
        objectReplacer = new GraalGraphObjectReplacer(config.getUniverse(), substrateProviders, universeFactory);
        config.registerObjectReplacer(objectReplacer);
    }

    private void installRuntimeConfig(BeforeAnalysisAccessImpl config) {
        Function<Providers, SubstrateBackend> backendProvider = TruffleRuntimeCompilationSupport.getRuntimeBackendProvider();
        ClassInitializationSupport classInitializationSupport = config.getHostVM().getClassInitializationSupport();
        Providers originalProviders = GraalAccess.getOriginalProviders();
        SubstratePlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(
                        ImageSingletons.lookup(BarrierSetProvider.class).createBarrierSet(config.getMetaAccess()));
        RuntimeConfiguration runtimeConfig = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class)
                        .createRuntimeConfigurationBuilder(RuntimeOptionValues.singleton(), config.getHostVM(), config.getUniverse(), config.getMetaAccess(),
                                        backendProvider, classInitializationSupport, originalProviders.getLoopsDataProvider(), platformConfig,
                                        config.getBigBang().getSnippetReflectionProvider())
                        .build();

        Providers runtimeProviders = runtimeConfig.getProviders();
        hostedProviders = new HostedProviders(runtimeProviders.getMetaAccess(), runtimeProviders.getCodeCache(), runtimeProviders.getConstantReflection(),
                        runtimeProviders.getConstantFieldProvider(),
                        runtimeProviders.getForeignCalls(), runtimeProviders.getLowerer(), runtimeProviders.getReplacements(), runtimeProviders.getStampProvider(),
                        runtimeConfig.getProviders().getSnippetReflection(), runtimeProviders.getWordTypes(), runtimeProviders.getPlatformConfigurationProvider(),
                        new GraphPrepareMetaAccessExtensionProvider(),
                        runtimeProviders.getLoopsDataProvider(), runtimeProviders.getIdentityHashCodeProvider());

        FeatureHandler featureHandler = config.getFeatureHandler();
        final boolean supportsStubBasedPlugins = !SubstrateOptions.useLLVMBackend();

        NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, runtimeConfig, hostedProviders, config.getMetaAccess(), config.getUniverse(), config.getNativeLibraries(),
                        config.getImageClassLoader(), ParsingReason.JITCompilation, ((Inflation) config.getBigBang()).getAnnotationSubstitutionProcessor(),
                        new SubstrateClassInitializationPlugin(config.getHostVM()), ConfigurationValues.getTarget(), supportsStubBasedPlugins);

        NativeImageGenerator.registerReplacements(DebugContext.forCurrentThread(), featureHandler, runtimeConfig, runtimeConfig.getProviders(), false, true,
                        new RuntimeCompiledMethodSupport.RuntimeCompilationGraphEncoder(ConfigurationValues.getTarget().arch, config.getUniverse().getHeapScanner()));

        featureHandler.forEachGraalFeature(feature -> feature.registerCodeObserver(runtimeConfig));
        Suites suites = NativeImageGenerator.createSuites(featureHandler, runtimeConfig, false);
        LIRSuites lirSuites = NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), false);
        Suites firstTierSuites = NativeImageGenerator.createFirstTierSuites(featureHandler, runtimeConfig, false);
        LIRSuites firstTierLirSuites = NativeImageGenerator.createFirstTierLIRSuites(featureHandler, runtimeConfig.getProviders(), false);

        TruffleRuntimeCompilationSupport.setRuntimeConfig(runtimeConfig, suites, lirSuites, firstTierSuites, firstTierLirSuites);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess c) {

        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) c;
        installRuntimeConfig(config);

        SubstrateGraalRuntime graalRuntime = new SubstrateGraalRuntime();
        objectReplacer.setGraalRuntime(graalRuntime);
        objectReplacer.setAnalysisAccess(config);
        ImageSingletons.add(GraalRuntime.class, graalRuntime);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new GraalSupport.GraalShutdownHook());

        /* Initialize configuration with reasonable default values. */
        graphBuilderConfig = GraphBuilderConfiguration.getDefault(hostedProviders.getGraphBuilderPlugins()).withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly);
        runtimeCompilationCandidatePredicate = RuntimeCompilationFeature::defaultAllowRuntimeCompilation;
        optimisticOpts = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
        graphEncoder = new RuntimeCompiledMethodSupport.RuntimeCompilationGraphEncoder(ConfigurationValues.getTarget().arch, config.getUniverse().getHeapScanner());

        /*
         * Ensure all snippet types are registered as used.
         */
        SubstrateReplacements replacements = (SubstrateReplacements) TruffleRuntimeCompilationSupport.getRuntimeConfig().getProviders().getReplacements();
        for (NodeClass<?> nodeClass : replacements.getSnippetNodeClasses()) {
            config.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsAllocated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }

        /*
         * The snippet graphs are prepared for runtime compilation by the
         * RuntimeCompilationGraphEncoder, so constants are represented as SubstrateObjectConstant.
         * Get back the ImageHeapConstant.
         */
        Function<Object, Object> objectTransformer = (object) -> {
            if (object instanceof JavaConstant constant) {
                return SubstrateGraalUtils.runtimeToHosted(constant, config.getUniverse().getHeapScanner());
            }
            return object;
        };
        /*
         * Ensure runtime snippet graphs are analyzed.
         */
        NativeImageGenerator.performSnippetGraphAnalysis(config.getBigBang(), replacements, config.getBigBang().getOptions(), objectTransformer);

        /*
         * Ensure that all snippet methods have their SubstrateMethod object created by the object
         * replacer, to avoid corner cases later when writing the native image.
         */
        for (ResolvedJavaMethod method : replacements.getSnippetMethods()) {
            objectReplacer.apply(method);
        }
    }

    boolean newRuntimeMethodsSeen = false;

    @Override
    public void duringAnalysis(Feature.DuringAnalysisAccess c) {
        /*
         * Note this will be removed once graphEncoder and the graal graph object replacer are
         * thread friendly.
         */
        FeatureImpl.DuringAnalysisAccessImpl config = (FeatureImpl.DuringAnalysisAccessImpl) c;

        if (newRuntimeMethodsSeen) {
            SubstrateMethod[] methodsToCompileArr = substrateAnalysisMethods.stream().toArray(SubstrateMethod[]::new);
            TruffleRuntimeCompilationSupport.setMethodsToCompile(config, methodsToCompileArr);
            config.requireAnalysisIteration();
            newRuntimeMethodsSeen = false;
        }

        graphEncoder.finishPrepare();
        AnalysisMetaAccess metaAccess = config.getMetaAccess();
        NodeClass<?>[] nodeClasses = graphEncoder.getNodeClasses();
        for (NodeClass<?> nodeClass : nodeClasses) {
            metaAccess.lookupJavaType(nodeClass.getClazz()).registerAsAllocated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }
        if (TruffleRuntimeCompilationSupport.setGraphEncoding(config, graphEncoder.getEncoding(), graphEncoder.getObjects(), nodeClasses)) {
            config.requireAnalysisIteration();
        }
    }

    private void checkMaxRuntimeCompiledMethods(CallTreeInfo callTreeInfo) {
        int maxMethods = 0;
        for (String value : Options.MaxRuntimeCompileMethods.getValue().values()) {
            String numberStr = null;
            try {
                /* Strip optional comment string from MaxRuntimeCompileMethods value */
                numberStr = value.split("#")[0];
                maxMethods += Integer.parseInt(numberStr);
            } catch (NumberFormatException ex) {
                throw UserError.abort("Invalid value for option 'MaxRuntimeCompileMethods': '%s' is not a valid number", numberStr);
            }
        }

        if (Options.EnforceMaxRuntimeCompileMethods.getValue() && maxMethods != 0 && callTreeInfo.runtimeCompilations().size() > maxMethods) {
            CallTreeInfo.printDeepestPath(callTreeInfo, registeredRuntimeCompilations);
            throw VMError.shouldNotReachHere("Number of methods for runtime compilation exceeds the allowed limit: " + callTreeInfo.runtimeCompilations().size() + " > " + maxMethods);
        }
    }

    @Override
    public void afterAnalysis(Feature.AfterAnalysisAccess access) {
        VMError.guarantee(callTreeMetadata == null);
        callTreeMetadata = CallTreeInfo.create(((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse(), invalidForRuntimeCompilation);
        if (!runtimeCompilationsFailedDuringParsing.isEmpty()) {
            System.out.println("PermanentBailouts seen while parsing runtime compiled methods. One reason for this can be encountering invalid frame states.");
            for (AnalysisMethod failedMethod : runtimeCompilationsFailedDuringParsing) {
                printFailingRuntimeMethodTrace(callTreeMetadata, failedMethod, failedMethod);
            }
        }

        ProgressReporter.singleton().setNumRuntimeCompiledMethods(callTreeMetadata.runtimeCompilations().size());
        // after analysis has completed we must ensure no new SubstrateTypes are introduced
        objectReplacer.forbidNewTypes();

        VMError.guarantee(callTreeMetadata != null);
        if (Options.PrintRuntimeCompileMethods.getValue()) {
            System.out.println("****Start Print Runtime Compile Methods***");
            callTreeMetadata.runtimeCompilations().stream().map(m -> m.getRuntimeMethod().format("%H.%n(%p)")).sorted().toList().forEach(System.out::println);
            System.out.println("****End Print Runtime Compile Methods***");
        }

        if (Options.PrintRuntimeCompilationCallTree.getValue()) {
            System.out.println("****Start Print Runtime Compile Call Tree***");
            CallTreeInfo.printCallTree(callTreeMetadata, registeredRuntimeCompilations);
            System.out.println("****End Print Runtime Compile Call Tree***");
        }

        checkMaxRuntimeCompiledMethods(callTreeMetadata);
    }

    @Override
    public void onCompileQueueCreation(BigBang bb, HostedUniverse hUniverse, CompileQueue compileQueue) {
        graphEncoder = null;
        Stream<HostedMethod> methodsToCompile = hUniverse.getMethods().stream().map(method -> method.getMultiMethod(RUNTIME_COMPILED_METHOD)).filter(method -> {
            if (method != null) {
                AnalysisMethod aMethod = method.getWrapped();
                return aMethod.isImplementationInvoked() && !invalidForRuntimeCompilation.containsKey(aMethod);
            }
            return false;
        });
        RuntimeCompiledMethodSupport.onCompileQueueCreation(bb, hUniverse, compileQueue, hostedProviders, constantFieldProviderWrapper, objectReplacer,
                        registeredRuntimeCompilations, methodsToCompile);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;

        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterCompilation(hUniverse, config.getProviders());
    }

    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess a) {
        objectReplacer.registerImmutableObjects(a);
        TruffleRuntimeCompilationSupport.registerImmutableObjects(a);
        ((SubstrateReplacements) TruffleRuntimeCompilationSupport.getRuntimeConfig().getProviders().getReplacements()).registerImmutableObjects(a);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        AfterHeapLayoutAccessImpl config = (AfterHeapLayoutAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterHeapLayout(hUniverse);
    }

    /**
     * When a failure occurs during parsing, it is likely due to a missing truffle boundary. Check
     * to see if anything on the blocklist has been reached and/or the number of runtime compiled
     * methods exceeds the maximum allowed.
     */
    @Override
    public void reportAnalysisError(AnalysisUniverse aUniverse, Throwable t) {
        var treeInfo = CallTreeInfo.create(aUniverse, invalidForRuntimeCompilation);

        blocklistChecker.accept(treeInfo);

        checkMaxRuntimeCompiledMethods(treeInfo);

        if (t instanceof ParallelExecutionException exception) {
            for (var e : exception.getExceptions()) {
                if (e instanceof AnalysisError.ParsingError parsingError) {
                    AnalysisMethod errorMethod = parsingError.getMethod();
                    if (errorMethod.isDeoptTarget() || SubstrateCompilationDirectives.isRuntimeCompiledMethod(errorMethod)) {
                        AnalysisMethod failingRuntimeMethod = null;
                        if (SubstrateCompilationDirectives.isRuntimeCompiledMethod(errorMethod)) {
                            failingRuntimeMethod = errorMethod;
                        } else if (errorMethod.isDeoptTarget()) {
                            failingRuntimeMethod = errorMethod.getMultiMethod(RUNTIME_COMPILED_METHOD);
                        }
                        printFailingRuntimeMethodTrace(treeInfo, failingRuntimeMethod, errorMethod);
                        System.out.println("error: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void printFailingRuntimeMethodTrace(CallTreeInfo treeInfo, AnalysisMethod failingRuntimeMethod, AnalysisMethod errorMethod) {
        System.out.println("Parsing failed on a special method version: " + errorMethod.format("%H.%n"));
        System.out.println("Method reachability trace");
        if (failingRuntimeMethod != null) {
            Arrays.stream(CallTreeInfo.getCallTrace(treeInfo, failingRuntimeMethod, registeredRuntimeCompilations)).forEach(System.out::println);
        } else {
            System.out.println("trace unavailable");
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
                    runtimeCompilationsFailedDuringParsing.add(method);
                    return HostVM.PARSING_FAILED;
                }

                parsed = true;
                graph = new StructuredGraph.Builder(debug.getOptions(), debug, StructuredGraph.AllowAssumptions.YES)
                                .method(method)
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
                        RuntimeCompiledMethodSupport.RuntimeGraphBuilderPhase.createRuntimeGraphBuilderPhase(bb, analysisProviders, graphBuilderConfig, optimisticOpts).apply(graph);
                    } catch (PermanentBailoutException ex) {
                        bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                        recordFailed(method);
                        runtimeCompilationsFailedDuringParsing.add(method);
                        return HostVM.PARSING_FAILED;
                    }
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
            Supplier<Boolean> graphChecker = DeoptimizationUtils.createGraphChecker(graph,
                            multiMethodKey == RUNTIME_COMPILED_METHOD ? DeoptimizationUtils.RUNTIME_COMPILATION_INVALID_NODES : DeoptimizationUtils.AOT_COMPILATION_INVALID_NODES);
            if (multiMethodKey != ORIGINAL_METHOD) {
                if (!graphChecker.get()) {
                    recordFailed(aMethod);
                    return false;
                }
            } else if (DeoptimizationUtils.canDeoptForTesting(aMethod, false, graphChecker)) {
                DeoptimizationUtils.registerDeoptEntriesForDeoptTesting(bb, graph, aMethod);
                return true;
            }
            if (multiMethodKey == RUNTIME_COMPILED_METHOD) {
                /*
                 * Register all FrameStates as DeoptEntries.
                 */
                AnalysisMethod origMethod = aMethod.getMultiMethod(ORIGINAL_METHOD);

                /*
                 * Because this graph will have its flowgraph immediately updated after this, there
                 * is no reason to make this method's flowgraph a stub on creation.
                 *
                 * We intentionally do not call getFullDeoptVersion because we want to wait until
                 * all deopt entries are registered before triggering the flow update.
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
                assert RuntimeCompiledMethodSupport.verifyNodes(graph);
            }

            return true;
        }

        @Override
        public void afterParsingHook(AnalysisMethod method, StructuredGraph graph) {
            if (method.isDeoptTarget()) {
                new RuntimeCompiledMethodSupport.ConvertMacroNodes().apply(graph);
            }
        }

        @Override
        public void initializeInlineBeforeAnalysisPolicy(SVMHost svmHost, InlineBeforeAnalysisPolicyUtils inliningUtils) {
            assert runtimeInlineBeforeAnalysisPolicy == null : runtimeInlineBeforeAnalysisPolicy;
            runtimeInlineBeforeAnalysisPolicy = new RuntimeCompilationInlineBeforeAnalysisPolicy(svmHost, inliningUtils);
        }

        @Override
        public InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy(MultiMethod.MultiMethodKey multiMethodKey, InlineBeforeAnalysisPolicy defaultPolicy) {
            if (multiMethodKey == ORIGINAL_METHOD) {
                return defaultPolicy;
            } else if (multiMethodKey == DEOPT_TARGET_METHOD) {
                return InlineBeforeAnalysisPolicy.NO_INLINING;
            } else if (multiMethodKey == RUNTIME_COMPILED_METHOD) {
                assert runtimeInlineBeforeAnalysisPolicy != null;
                return runtimeInlineBeforeAnalysisPolicy;
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
        protected boolean shouldOmitIntermediateMethodInState(AnalysisMethod method) {
            /*
             * We don't want to miss any FrameStates within runtime compiled methods since they are
             * needed in case a deoptimization occurs.
             */
            return false;
        }

        @Override
        protected FixedWithNextNode processInvokeArgs(AnalysisMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments, NodeSourcePosition sourcePosition) {
            StructuredGraph graph = insertionPoint.graph();
            assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(targetMethod) : targetMethod;
            InlinedInvokeArgumentsNode newNode = graph.add(new InlinedInvokeArgumentsNode(targetMethod, arguments));
            newNode.setNodeSourcePosition(sourcePosition);
            graph.addAfterFixed(insertionPoint, newNode);
            return newNode;
        }

        @Override
        protected boolean shouldInlineInvoke(GraphBuilderContext b, AbstractPolicyScope policyScope, AnalysisMethod method, ValueNode[] args) {
            if (allowInliningPredicate.allowInlining(b, method) != AllowInliningPredicate.InlineDecision.INLINE) {
                return false;
            }

            InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope accScope;
            if (policyScope instanceof RuntimeCompilationAlwaysInlineScope) {
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
        protected InlineInvokePlugin.InlineInfo createInvokeInfo(AnalysisMethod method) {
            /*
             * Set this graph initially to a stub. If there are no explicit calls to this method
             * (i.e., all calls to this method are inlined), then the method's full flow will not
             * need to be created.
             */
            AnalysisMethod runtimeMethod = getStubRuntimeVersion(method);
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(runtimeMethod);
        }

        @Override
        protected AbstractPolicyScope createRootScope() {
            /* We do not need a scope for the root method. */
            return null;
        }

        @Override
        protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, AnalysisMethod method) {
            if (outer instanceof InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope accOuter) {
                /*
                 * Once the accumulative policy is activated, we cannot return to the trivial
                 * policy.
                 */
                return inliningUtils.createAccumulativeInlineScope(accOuter, method, DeoptimizationUtils.RUNTIME_COMPILATION_INVALID_NODES);
            }

            assert outer == null || outer instanceof RuntimeCompilationAlwaysInlineScope : "unexpected outer scope: " + outer;

            /*
             * Check if trivial is possible. We use the graph size as the main criteria, similar to
             * the trivial inlining for AOT compilation.
             *
             * In addition, we do not allow method handle internals to be processed by the trivial
             * inlining. The regular accumulative inlining scope has a special mode for method
             * handle intrinsification with larger thresholds in order to fully inline the method
             * handle.
             */
            boolean trivialInlineAllowed = hostVM.isAnalysisTrivialMethod(method) && !InlineBeforeAnalysisPolicyUtils.isMethodHandleIntrinsificationRoot(method);
            int inliningDepth = outer == null ? 1 : outer.inliningDepth + 1;
            if (trivialInlineAllowed && inliningDepth <= trivialAllowingInliningDepth) {
                return new RuntimeCompilationAlwaysInlineScope(inliningDepth);
            } else {
                // start with a new accumulative inline scope
                return inliningUtils.createAccumulativeInlineScope(null, method, DeoptimizationUtils.RUNTIME_COMPILATION_INVALID_NODES);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends AnalysisMethod> T getStubDeoptVersion(T implementation) {
        return (T) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
    }

    @SuppressWarnings("unchecked")
    private static <T extends AnalysisMethod> T getFullDeoptVersion(BigBang bb, T implementation, InvokeTypeFlow parsingReason) {
        PointsToAnalysisMethod runtimeMethod = (PointsToAnalysisMethod) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD);
        PointsToAnalysis analysis = (PointsToAnalysis) bb;
        runtimeMethod.getTypeFlow().updateFlowsGraph(analysis, MethodFlowsGraph.GraphKind.FULL, parsingReason, true);
        return (T) runtimeMethod;
    }

    @SuppressWarnings("unchecked")
    private static <T extends AnalysisMethod> T getStubRuntimeVersion(T implementation) {
        return (T) implementation.getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
    }

    @SuppressWarnings("unchecked")
    private static <T extends AnalysisMethod> T getFullRuntimeVersion(BigBang bb, T implementation, InvokeTypeFlow parsingReason) {
        PointsToAnalysisMethod runtimeMethod = (PointsToAnalysisMethod) implementation.getOrCreateMultiMethod(RUNTIME_COMPILED_METHOD);
        PointsToAnalysis analysis = (PointsToAnalysis) bb;
        runtimeMethod.getTypeFlow().updateFlowsGraph(analysis, MethodFlowsGraph.GraphKind.FULL, parsingReason, false);
        return (T) runtimeMethod;
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
                runtimeCompilationCandidates.add(new RuntimeCompilationCandidate(originalTarget, originalTarget));
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
                    return List.of(implementation, getStubDeoptVersion(implementation), getFullRuntimeVersion(bb, implementation, invokeFlow));
                } else if (SubstrateCompilationDirectives.singleton().isFrameInformationRequired(implementation)) {
                    return List.of(implementation, getFullDeoptVersion(bb, implementation, invokeFlow));
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
                    runtimeCompilationCandidates.add(new RuntimeCompilationCandidate(implementation, target));
                    /*
                     * The runtime method can call all three types: original (if it is not partial
                     * evaluated), runtime (if it is partial evaluated), and deoptimized (if the
                     * runtime deoptimizes).
                     */
                    if (runtimeCompilationCandidate) {
                        return List.of(implementation, getStubDeoptVersion(implementation), getFullRuntimeVersion(bb, implementation, invokeFlow));
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
                        return List.of(implementation, getStubDeoptVersion(implementation), getStubRuntimeVersion(implementation));
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
                /*
                 * A runtime method can be returned to from either a runtime or original method.
                 */
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

        @Override
        public boolean unknownReturnValue(BigBang bb, MultiMethod.MultiMethodKey callerMultiMethodKey, AnalysisMethod implementation) {
            if (callerMultiMethodKey == RUNTIME_COMPILED_METHOD || implementation.isDeoptTarget()) {
                /*
                 * If the method may be intrinsified later, the implementation can change.
                 *
                 * We also must ensure deopt methods always return a superset of the original
                 * method.
                 */
                var origImpl = implementation.getMultiMethod(ORIGINAL_METHOD);
                var options = bb.getOptions();
                return (hostedProviders.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(origImpl, options) != null) ||
                                hostedProviders.getReplacements().hasSubstitution(origImpl, options);
            }
            return false;
        }
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

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        return true;
    }
}

/**
 * This scope will always allow nodes to be inlined.
 */
class RuntimeCompilationAlwaysInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {

    RuntimeCompilationAlwaysInlineScope(int inliningDepth) {
        super(inliningDepth);
    }

    @Override
    public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
        // nothing to do
    }

    @Override
    public void abortCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
        // nothing to do
    }

    @Override
    public boolean processNode(AnalysisMetaAccess metaAccess, AnalysisMethod method, Node node) {
        /*
         * Inline as long as an invalid node has not been seen.
         */
        return !DeoptimizationUtils.RUNTIME_COMPILATION_INVALID_NODES.test(node);
    }

    @Override
    public boolean processNonInlinedInvoke(CoreProviders providers, CallTargetNode node) {
        // always inlining
        return true;
    }

    @Override
    public String toString() {
        return "RuntimeCompilationAlwaysInlineScope";
    }
}
