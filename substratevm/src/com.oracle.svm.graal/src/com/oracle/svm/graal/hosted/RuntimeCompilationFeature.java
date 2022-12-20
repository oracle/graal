/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.AfterCompilationAccess;
import org.graalvm.nativeimage.hosted.Feature.AfterHeapLayoutAccess;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.RuntimeCompilationCanaryFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
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
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.services.Services;

/**
 * The main handler for running the Graal compiler in the Substrate VM at run time. This feature
 * (and features it depends on like {@link FieldsOffsetsFeature}) encodes Graal graphs for runtime
 * compilation, ensures that all required {@link SubstrateType}, {@link SubstrateMethod},
 * {@link SubstrateField} objects are created by {@link GraalGraphObjectReplacer} and added to the
 * image. Data that is prepared during image generation and used at run time is stored in
 * {@link GraalSupport}.
 */
public abstract class RuntimeCompilationFeature {

    public static class Options {
        @Option(help = "Print call tree of methods available for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompileMethods = new HostedOptionKey<>(false);

        @Option(help = "Maximum number of methods allowed for runtime compilation.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> MaxRuntimeCompileMethods = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Enforce checking of maximum number of methods allowed for runtime compilation. Useful for checking in the gate that the number of methods does not go up without a good reason.")//
        public static final HostedOptionKey<Boolean> EnforceMaxRuntimeCompileMethods = new HostedOptionKey<>(false);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(RuntimeCompilationFeature.class);
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
            return isEnabledAndNotLibgraal();
        }
    }

    public static boolean isEnabledAndNotLibgraal() {
        return ImageSingletons.contains(RuntimeCompilationFeature.class) && !SubstrateUtil.isBuildingLibgraal();
    }

    public static RuntimeCompilationFeature singleton() {
        return ImageSingletons.lookup(RuntimeCompilationFeature.class);
    }

    public static Class<? extends Feature> getRuntimeCompilationFeature() {
        if (SubstrateOptions.ParseOnceJIT.getValue()) {
            return ParseOnceRuntimeCompilationFeature.class;
        } else {
            return LegacyRuntimeCompilationFeature.class;
        }
    }

    public interface RuntimeCompilationCandidatePredicate {
        boolean allowRuntimeCompilation(ResolvedJavaMethod method);
    }

    public interface AbstractCallTreeNode {
        AnalysisMethod getImplementationMethod();

        AnalysisMethod getTargetMethod();

        StructuredGraph getGraph();

        String getSourceReference();

        AbstractCallTreeNode getParent();

        List<AbstractCallTreeNode> getChildren();

        int getLevel();
    }

    public AbstractCallTreeNode getCallTreeNode(ResolvedJavaMethod method) {
        assert method instanceof AnalysisMethod;
        return getRuntimeCompiledMethods().get(method);
    }

    protected GraalGraphObjectReplacer objectReplacer;
    protected HostedProviders hostedProviders;
    protected GraphEncoder graphEncoder;
    private SharedRuntimeConfigurationBuilder runtimeConfigBuilder;

    private boolean initialized;
    protected GraphBuilderConfiguration graphBuilderConfig;
    protected OptimisticOptimizations optimisticOpts;
    protected RuntimeCompilationCandidatePredicate runtimeCompilationCandidatePredicate;
    protected Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;
    protected Map<AnalysisMethod, AbstractCallTreeNode> runtimeCompiledMethodMap;
    protected Set<AbstractCallTreeNode> runtimeCompilationCandidates;

    public StructuredGraph lookupMethodGraph(AnalysisMethod method) {
        AbstractCallTreeNode callTree = runtimeCompiledMethodMap.get(method);
        assert callTree != null : "Unable to find method.";
        StructuredGraph graph = callTree.getGraph();
        assert graph != null : "Method's graph is null.";
        return graph;
    }

    public HostedProviders getHostedProviders() {
        return hostedProviders;
    }

    public GraalGraphObjectReplacer getObjectReplacer() {
        return objectReplacer;
    }

    protected static List<Class<? extends Feature>> getRequiredFeaturesHelper() {
        if (Services.IS_BUILDING_NATIVE_IMAGE) {
            return List.of(FieldsOffsetsFeature.class);
        }
        return List.of(RuntimeCompilationCanaryFeature.class, DeoptimizationFeature.class, FieldsOffsetsFeature.class);
    }

    protected final void duringSetupHelper(DuringSetupAccess c) {
        ImageSingletons.add(GraalSupport.class, new GraalSupport());
        if (!ImageSingletons.contains(SubstrateGraalCompilerSetup.class)) {
            ImageSingletons.add(SubstrateGraalCompilerSetup.class, new SubstrateGraalCompilerSetup());
        }

        DuringSetupAccessImpl config = (DuringSetupAccessImpl) c;
        AnalysisMetaAccess aMetaAccess = config.getMetaAccess();
        SubstrateProviders substrateProviders = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class).getSubstrateProviders(aMetaAccess);
        objectReplacer = new GraalGraphObjectReplacer(config.getUniverse(), aMetaAccess, substrateProviders);
        config.registerObjectReplacer(objectReplacer);

        config.registerClassReachabilityListener(GraalSupport::registerPhaseStatistics);
    }

    public Map<AnalysisMethod, AbstractCallTreeNode> getRuntimeCompiledMethods() {
        return runtimeCompiledMethodMap;
    }

    public Set<AbstractCallTreeNode> getAllRuntimeCompilationCandidates() {
        return runtimeCompilationCandidates;
    }

    private void installRuntimeConfig(BeforeAnalysisAccessImpl config) {
        Function<Providers, SubstrateBackend> backendProvider = GraalSupport.getRuntimeBackendProvider();
        ClassInitializationSupport classInitializationSupport = config.getHostVM().getClassInitializationSupport();
        Providers originalProviders = GraalAccess.getOriginalProviders();
        SubstratePlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(ImageSingletons.lookup(BarrierSetProvider.class).createBarrierSet(config.getMetaAccess()));
        runtimeConfigBuilder = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class).createRuntimeConfigurationBuilder(RuntimeOptionValues.singleton(), config.getHostVM(), config.getUniverse(),
                        config.getMetaAccess(),
                        originalProviders.getConstantReflection(), backendProvider, config.getNativeLibraries(), classInitializationSupport, originalProviders.getLoopsDataProvider(),
                        platformConfig).build();
        RuntimeConfiguration runtimeConfig = runtimeConfigBuilder.getRuntimeConfig();

        Providers runtimeProviders = runtimeConfig.getProviders();
        WordTypes wordTypes = runtimeConfigBuilder.getWordTypes();
        hostedProviders = new HostedProviders(runtimeProviders.getMetaAccess(), runtimeProviders.getCodeCache(), runtimeProviders.getConstantReflection(), runtimeProviders.getConstantFieldProvider(),
                        runtimeProviders.getForeignCalls(), runtimeProviders.getLowerer(), runtimeProviders.getReplacements(), runtimeProviders.getStampProvider(),
                        runtimeConfig.getSnippetReflection(), wordTypes, runtimeProviders.getPlatformConfigurationProvider(), new GraphPrepareMetaAccessExtensionProvider(),
                        runtimeProviders.getLoopsDataProvider());

        FeatureHandler featureHandler = config.getFeatureHandler();
        NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, runtimeConfig, hostedProviders, config.getMetaAccess(), config.getUniverse(), null, null, config.getNativeLibraries(),
                        config.getImageClassLoader(), ParsingReason.JITCompilation, ((Inflation) config.getBigBang()).getAnnotationSubstitutionProcessor(),
                        new SubstrateClassInitializationPlugin(config.getHostVM()), ConfigurationValues.getTarget());

        NativeImageGenerator.registerReplacements(DebugContext.forCurrentThread(), featureHandler, runtimeConfig, runtimeConfig.getProviders(), false, true);
        featureHandler.forEachGraalFeature(feature -> feature.registerCodeObserver(runtimeConfig));
        Suites suites = NativeImageGenerator.createSuites(featureHandler, runtimeConfig, runtimeConfig.getSnippetReflection(), false);
        LIRSuites lirSuites = NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), false);
        Suites firstTierSuites = NativeImageGenerator.createFirstTierSuites(featureHandler, runtimeConfig, runtimeConfig.getSnippetReflection(), false);
        LIRSuites firstTierLirSuites = NativeImageGenerator.createFirstTierLIRSuites(featureHandler, runtimeConfig.getProviders(), false);

        GraalSupport.setRuntimeConfig(runtimeConfig, suites, lirSuites, firstTierSuites, firstTierLirSuites);
    }

    protected final void beforeAnalysisHelper(BeforeAnalysisAccess c) {
        DebugContext debug = DebugContext.forCurrentThread();

        // box lowering accesses the caches for those classes and thus needs reflective access
        for (JavaKind kind : new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char,
                        JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Short}) {
            RuntimeReflection.register(kind.toBoxedJavaClass());
            Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
            if (innerClasses != null && innerClasses.length > 0) {
                RuntimeReflection.register(innerClasses[0]);
                try {
                    RuntimeReflection.register(innerClasses[0].getDeclaredField("cache"));
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }

        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) c;

        GraalSupport.allocatePhaseStatisticsCache();

        populateMatchRuleRegistry();

        installRuntimeConfig(config);

        SubstrateGraalRuntime graalRuntime = new SubstrateGraalRuntime();
        objectReplacer.setGraalRuntime(graalRuntime);
        ImageSingletons.add(GraalRuntime.class, graalRuntime);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new GraalSupport.GraalShutdownHook());

        /* Initialize configuration with reasonable default values. */
        graphBuilderConfig = GraphBuilderConfiguration.getDefault(hostedProviders.getGraphBuilderPlugins()).withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly);
        runtimeCompilationCandidatePredicate = RuntimeCompilationFeature::defaultAllowRuntimeCompilation;
        optimisticOpts = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
        runtimeCompiledMethodMap = new LinkedHashMap<>();
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);
        runtimeCompilationCandidates = new HashSet<>();

        /*
         * Ensure all snippet types are registered as used.
         */
        SubstrateReplacements replacements = (SubstrateReplacements) GraalSupport.getRuntimeConfig().getProviders().getReplacements();
        for (NodeClass<?> nodeClass : replacements.getSnippetNodeClasses()) {
            config.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsAllocated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }

        /*
         * Ensure that all snippet methods have their SubstrateMethod object created by the object
         * replacer, to avoid corner cases later when writing the native image.
         */
        for (ResolvedJavaMethod method : replacements.getSnippetMethods()) {
            objectReplacer.apply(method);
        }
    }

    private static void populateMatchRuleRegistry() {
        GraalSupport.get().setMatchRuleRegistry(new HashMap<>());
        GraalConfiguration.runtimeInstance().populateMatchRuleRegistry(GraalSupport.get().getMatchRuleRegistry());
    }

    @SuppressWarnings("unused")
    private static boolean defaultAllowRuntimeCompilation(ResolvedJavaMethod method) {
        return false;
    }

    public void initializeRuntimeCompilationConfiguration(RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate) {
        initializeRuntimeCompilationConfiguration(hostedProviders, graphBuilderConfig, newRuntimeCompilationCandidatePredicate, deoptimizeOnExceptionPredicate);
    }

    public void initializeRuntimeCompilationConfiguration(HostedProviders newHostedProviders, GraphBuilderConfiguration newGraphBuilderConfig,
                    RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate,
                    Predicate<ResolvedJavaMethod> newDeoptimizeOnExceptionPredicate) {
        guarantee(initialized == false, "runtime compilation configuration already initialized");
        initialized = true;

        hostedProviders = newHostedProviders;
        graphBuilderConfig = newGraphBuilderConfig;
        runtimeCompilationCandidatePredicate = newRuntimeCompilationCandidatePredicate;
        deoptimizeOnExceptionPredicate = newDeoptimizeOnExceptionPredicate;

        if (SubstrateOptions.IncludeNodeSourcePositions.getValue()) {
            graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
        }
    }

    public SubstrateMethod requireFrameInformationForMethod(ResolvedJavaMethod method) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        requireFrameInformationForMethodHelper(aMethod);

        return sMethod;
    }

    protected abstract void requireFrameInformationForMethodHelper(AnalysisMethod aMethod);

    public SubstrateMethod prepareMethodForRuntimeCompilation(Executable method, BeforeAnalysisAccessImpl config) {
        return prepareMethodForRuntimeCompilation(config.getMetaAccess().lookupJavaMethod(method), config);
    }

    public abstract SubstrateMethod prepareMethodForRuntimeCompilation(ResolvedJavaMethod method, BeforeAnalysisAccessImpl config);

    protected final void afterAnalysisHelper() {
        ProgressReporter.singleton().setNumRuntimeCompiledMethods(runtimeCompiledMethodMap.size());
    }

    protected final void beforeCompilationHelper() {
        if (Options.PrintRuntimeCompileMethods.getValue()) {
            printCallTree();
        }

        int maxMethods = 0;
        for (String value : Options.MaxRuntimeCompileMethods.getValue().values()) {
            String numberStr = null;
            try {
                /* Strip optional comment string from MaxRuntimeCompileMethods value */
                numberStr = value.split("#")[0];
                maxMethods += Long.parseLong(numberStr);
            } catch (NumberFormatException ex) {
                throw UserError.abort("Invalid value for option 'MaxRuntimeCompileMethods': '%s' is not a valid number", numberStr);
            }
        }
        if (Options.EnforceMaxRuntimeCompileMethods.getValue() && maxMethods != 0 && runtimeCompiledMethodMap.size() > maxMethods) {
            printDeepestLevelPath();
            throw VMError.shouldNotReachHere("Number of methods for runtime compilation exceeds the allowed limit: " + runtimeCompiledMethodMap.size() + " > " + maxMethods);
        }
    }

    private void printDeepestLevelPath() {
        AbstractCallTreeNode maxLevelCallTreeNode = null;
        for (AbstractCallTreeNode callTreeNode : runtimeCompiledMethodMap.values()) {
            if (maxLevelCallTreeNode == null || maxLevelCallTreeNode.getLevel() < callTreeNode.getLevel()) {
                maxLevelCallTreeNode = callTreeNode;
            }
        }

        System.out.println(String.format("Deepest level call tree path (%d calls):", maxLevelCallTreeNode.getLevel()));
        AbstractCallTreeNode node = maxLevelCallTreeNode;
        while (node != null) {
            AnalysisMethod implementationMethod = node.getImplementationMethod();
            AnalysisMethod targetMethod = node.getTargetMethod();
            StructuredGraph graph = node.getGraph();

            System.out.format("%5d ; %s ; %s", graph == null ? -1 : graph.getNodeCount(), node.getSourceReference(), implementationMethod == null ? ""
                            : implementationMethod.format("%H.%n(%p)"));
            if (targetMethod != null && !targetMethod.equals(implementationMethod)) {
                System.out.print(" ; " + targetMethod.format("%H.%n(%p)"));
            }
            System.out.println();
            node = node.getParent();
        }
    }

    private void printCallTree() {
        System.out.println("depth;method;Graal nodes;invoked from source;full method name;full name of invoked virtual method");
        for (AbstractCallTreeNode node : runtimeCompiledMethodMap.values()) {
            if (node.getLevel() == 0) {
                printCallTreeNode(node);
            }
        }
        System.out.println();
    }

    private void printCallTreeNode(AbstractCallTreeNode node) {
        AnalysisMethod implementationMethod = node.getImplementationMethod();
        AnalysisMethod targetMethod = node.getTargetMethod();
        StructuredGraph graph = node.getGraph();

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < node.getLevel(); i++) {
            indent.append("  ");
        }
        if (implementationMethod != null) {
            indent.append(implementationMethod.format("%h.%n"));
        }

        System.out.format("%4d ; %-80s  ;%5d ; %s ; %s", node.getLevel(), indent, graph == null ? -1 : graph.getNodeCount(), node.getSourceReference(),
                        implementationMethod == null ? "" : implementationMethod.format("%H.%n(%p)"));
        if (targetMethod != null && !targetMethod.equals(implementationMethod)) {
            System.out.print(" ; " + targetMethod.format("%H.%n(%p)"));
        }
        System.out.println();

        for (AbstractCallTreeNode child : node.getChildren()) {
            printCallTreeNode(child);
        }
    }

    protected final void afterCompilationHelper(AfterCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;

        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterCompilation(hUniverse, config.getProviders().getConstantFieldProvider());

        objectReplacer.registerImmutableObjects(config);
        GraalSupport.registerImmutableObjects(config);
        ((SubstrateReplacements) runtimeConfigBuilder.getRuntimeConfig().getProviders().getReplacements()).registerImmutableObjects(config);
    }

    protected final void afterHeapLayoutHelper(AfterHeapLayoutAccess a) {
        AfterHeapLayoutAccessImpl config = (AfterHeapLayoutAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = hMetaAccess.getUniverse();
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
    private final GraalGraphObjectReplacer objectReplacer;

    RuntimeStrengthenStampsPhase(HostedUniverse hUniverse, GraalGraphObjectReplacer objectReplacer) {
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

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        return true;
    }
}
