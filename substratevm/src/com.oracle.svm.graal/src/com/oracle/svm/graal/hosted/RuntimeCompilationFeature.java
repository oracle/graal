/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.AfterCompilationAccess;
import org.graalvm.nativeimage.hosted.Feature.AfterHeapLayoutAccess;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.BeforeHeapLayoutAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
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
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
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
import com.oracle.svm.graal.meta.SubstrateUniverseFactory;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

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
        @Option(help = "Print methods available for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompileMethods = new HostedOptionKey<>(false);

        @Option(help = "Print call tree of methods reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> PrintRuntimeCompilationCallTree = new HostedOptionKey<>(false);

        @Option(help = "Maximum number of methods allowed for runtime compilation.", stability = OptionStability.STABLE)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> MaxRuntimeCompileMethods = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

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
        return ParseOnceRuntimeCompilationFeature.class;
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

    public abstract static class AbstractCallTreeNode implements Comparable<AbstractCallTreeNode> {
        private final AnalysisMethod implementationMethod;
        private final AnalysisMethod targetMethod;
        private final AbstractCallTreeNode parent;
        private final int level;
        private Set<AbstractCallTreeNode> children;

        protected AbstractCallTreeNode(AbstractCallTreeNode parent, AnalysisMethod targetMethod, AnalysisMethod implementationMethod) {
            this.implementationMethod = implementationMethod;
            this.targetMethod = targetMethod;
            this.parent = parent;
            this.children = null;
            if (parent != null) {
                level = parent.level + 1;
            } else {
                level = 0;
            }
        }

        public AnalysisMethod getImplementationMethod() {
            return implementationMethod;
        }

        public AnalysisMethod getTargetMethod() {
            return targetMethod;
        }

        protected AbstractCallTreeNode getParent() {
            return parent;
        }

        /**
         * Helper method to create parent->child link when this node becomes part of the call tree.
         */
        protected void linkAsChild() {
            if (parent != null) {
                if (parent.children == null) {
                    parent.children = new LinkedHashSet<>();
                }
                boolean added = parent.children.add(this);
                assert added : "child linked to parent multiple times.";
            }
        }

        private Collection<AbstractCallTreeNode> getChildren() {
            return children == null ? List.of() : children;
        }

        protected int getLevel() {
            return level;
        }

        public abstract String getPosition();

        public abstract int getNodeCount();

        @Override
        public int compareTo(AbstractCallTreeNode o) {
            int result = implementationMethod.getQualifiedName().compareTo(o.implementationMethod.getQualifiedName());
            if (result != 0) {
                return result;
            }
            result = targetMethod.getQualifiedName().compareTo(o.targetMethod.getQualifiedName());
            if (result != 0) {
                return result;
            }

            result = Integer.compare(level, o.level);
            if (result != 0) {
                return result;
            }

            if (parent != null && o.parent != null) {
                return parent.compareTo(o.parent);
            }

            // this must be true otherwise the level check should return different value
            assert parent == null && o.parent == null;
            return 0;
        }

        /**
         * For equality purposes we only care whether it is an equivalent call site (i.e., the
         * target and implementation match).
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AbstractCallTreeNode that = (AbstractCallTreeNode) o;

            return implementationMethod.equals(that.implementationMethod) && targetMethod.equals(that.targetMethod);
        }

        @Override
        public int hashCode() {
            return Objects.hash(implementationMethod, targetMethod);
        }
    }

    protected abstract AbstractCallTreeNode getCallTreeNode(RuntimeCompilationCandidate candidate);

    protected abstract AbstractCallTreeNode getCallTreeNode(RuntimeCompiledMethod method);

    protected abstract AbstractCallTreeNode getCallTreeNode(ResolvedJavaMethod method);

    public interface RuntimeCompiledMethod {
        AnalysisMethod getMethod();

        Collection<ResolvedJavaMethod> getInlinedMethods();

        Collection<ResolvedJavaMethod> getInvokeTargets();
    }

    public abstract Collection<RuntimeCompiledMethod> getRuntimeCompiledMethods();

    public interface RuntimeCompilationCandidate {
        AnalysisMethod getImplementationMethod();

        AnalysisMethod getTargetMethod();
    }

    public abstract Collection<RuntimeCompilationCandidate> getAllRuntimeCompilationCandidates();

    public final Comparator<RuntimeCompilationCandidate> getRuntimeCompilationComparator() {
        return (o1, o2) -> getCallTreeNode(o1).compareTo(getCallTreeNode(o2));
    }

    public final List<String> getCallTrace(ResolvedJavaMethod method) {
        return getCallTraceHelper(getCallTreeNode(method));
    }

    public final List<String> getCallTrace(RuntimeCompilationCandidate candidate) {
        return getCallTraceHelper(getCallTreeNode(candidate));
    }

    private static List<String> getCallTraceHelper(AbstractCallTreeNode node) {
        List<String> trace = new ArrayList<>();
        for (AbstractCallTreeNode cur = node; cur != null; cur = cur.getParent()) {
            trace.add(cur.getPosition());
        }
        return trace;
    }

    protected GraalGraphObjectReplacer objectReplacer;
    protected HostedProviders hostedProviders;
    protected GraphEncoder graphEncoder;

    private boolean initialized;
    protected GraphBuilderConfiguration graphBuilderConfig;
    protected OptimisticOptimizations optimisticOpts;
    protected RuntimeCompilationCandidatePredicate runtimeCompilationCandidatePredicate;
    private boolean runtimeCompilationCandidatePredicateUpdated = false;
    protected Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;

    private SubstrateUniverseFactory universeFactory = new SubstrateUniverseFactory();

    public HostedProviders getHostedProviders() {
        return hostedProviders;
    }

    public GraalGraphObjectReplacer getObjectReplacer() {
        return objectReplacer;
    }

    protected static List<Class<? extends Feature>> getRequiredFeaturesHelper() {
        if (SubstrateUtil.isBuildingLibgraal()) {
            return List.of(FieldsOffsetsFeature.class);
        } else {
            return List.of(RuntimeCompilationCanaryFeature.class, DeoptimizationFeature.class, FieldsOffsetsFeature.class);
        }
    }

    public void setUniverseFactory(SubstrateUniverseFactory universeFactory) {
        this.universeFactory = universeFactory;
    }

    protected final void duringSetupHelper(DuringSetupAccess c) {
        if (SubstrateOptions.useLLVMBackend()) {
            throw UserError.abort("Runtime compilation is currently unimplemented on the LLVM backend (GR-43073).");
        }
        ImageSingletons.add(GraalSupport.class, new GraalSupport());
        if (!ImageSingletons.contains(SubstrateGraalCompilerSetup.class)) {
            ImageSingletons.add(SubstrateGraalCompilerSetup.class, new SubstrateGraalCompilerSetup());
        }

        DuringSetupAccessImpl config = (DuringSetupAccessImpl) c;
        AnalysisMetaAccess aMetaAccess = config.getMetaAccess();
        SubstrateProviders substrateProviders = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class).getSubstrateProviders(aMetaAccess);
        objectReplacer = new GraalGraphObjectReplacer(config.getUniverse(), substrateProviders, universeFactory);
        config.registerObjectReplacer(objectReplacer);

        config.registerClassReachabilityListener(GraalSupport::registerPhaseStatistics);
    }

    private void installRuntimeConfig(BeforeAnalysisAccessImpl config) {
        Function<Providers, SubstrateBackend> backendProvider = GraalSupport.getRuntimeBackendProvider();
        ClassInitializationSupport classInitializationSupport = config.getHostVM().getClassInitializationSupport();
        Providers originalProviders = GraalAccess.getOriginalProviders();
        SubstratePlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(ImageSingletons.lookup(BarrierSetProvider.class).createBarrierSet(config.getMetaAccess()));
        RuntimeConfiguration runtimeConfig = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class)
                        .createRuntimeConfigurationBuilder(RuntimeOptionValues.singleton(), config.getHostVM(), config.getUniverse(), config.getMetaAccess(),
                                        backendProvider, classInitializationSupport, originalProviders.getLoopsDataProvider(), platformConfig)
                        .build();

        Providers runtimeProviders = runtimeConfig.getProviders();
        WordTypes wordTypes = runtimeConfig.getWordTypes();
        hostedProviders = new HostedProviders(runtimeProviders.getMetaAccess(), runtimeProviders.getCodeCache(), runtimeProviders.getConstantReflection(), runtimeProviders.getConstantFieldProvider(),
                        runtimeProviders.getForeignCalls(), runtimeProviders.getLowerer(), runtimeProviders.getReplacements(), runtimeProviders.getStampProvider(),
                        runtimeConfig.getSnippetReflection(), wordTypes, runtimeProviders.getPlatformConfigurationProvider(), new GraphPrepareMetaAccessExtensionProvider(),
                        runtimeProviders.getLoopsDataProvider());

        FeatureHandler featureHandler = config.getFeatureHandler();
        final boolean supportsStubBasedPlugins = !SubstrateOptions.useLLVMBackend();

        NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, runtimeConfig, hostedProviders, config.getMetaAccess(), config.getUniverse(), null, null, config.getNativeLibraries(),
                        config.getImageClassLoader(), ParsingReason.JITCompilation, ((Inflation) config.getBigBang()).getAnnotationSubstitutionProcessor(),
                        new SubstrateClassInitializationPlugin(config.getHostVM()), ConfigurationValues.getTarget(), supportsStubBasedPlugins);

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
        objectReplacer.setAnalysisAccess(config);
        ImageSingletons.add(GraalRuntime.class, graalRuntime);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new GraalSupport.GraalShutdownHook());

        /* Initialize configuration with reasonable default values. */
        graphBuilderConfig = GraphBuilderConfiguration.getDefault(hostedProviders.getGraphBuilderPlugins()).withBytecodeExceptionMode(BytecodeExceptionMode.ExplicitOnly);
        runtimeCompilationCandidatePredicate = RuntimeCompilationFeature::defaultAllowRuntimeCompilation;
        optimisticOpts = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
        graphEncoder = new GraphEncoder(ConfigurationValues.getTarget().arch);

        /*
         * Ensure all snippet types are registered as used.
         */
        SubstrateReplacements replacements = (SubstrateReplacements) GraalSupport.getRuntimeConfig().getProviders().getReplacements();
        for (NodeClass<?> nodeClass : replacements.getSnippetNodeClasses()) {
            config.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsAllocated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }
        /*
         * Ensure runtime snippet graphs are analyzed.
         */
        if (!SubstrateUtil.isBuildingLibgraal()) {
            NativeImageGenerator.performSnippetGraphAnalysis(config.getBigBang(), replacements, config.getBigBang().getOptions());
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

    public void initializeRuntimeCompilationForTesting(FeatureImpl.BeforeAnalysisAccessImpl config, RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate) {
        initializeRuntimeCompilationConfiguration(hostedProviders, graphBuilderConfig, newRuntimeCompilationCandidatePredicate, deoptimizeOnExceptionPredicate);
        initializeRuntimeCompilationForTesting(config);
    }

    public void initializeRuntimeCompilationForTesting(BeforeAnalysisAccessImpl config) {
        initializeAnalysisProviders(config.getBigBang(), provider -> provider);
    }

    public void initializeRuntimeCompilationConfiguration(HostedProviders newHostedProviders, GraphBuilderConfiguration newGraphBuilderConfig,
                    RuntimeCompilationCandidatePredicate newRuntimeCompilationCandidatePredicate,
                    Predicate<ResolvedJavaMethod> newDeoptimizeOnExceptionPredicate) {
        guarantee(initialized == false, "runtime compilation configuration already initialized");
        initialized = true;

        hostedProviders = newHostedProviders;
        graphBuilderConfig = newGraphBuilderConfig;
        assert !runtimeCompilationCandidatePredicateUpdated : "Updated compilation predicate multiple times";
        runtimeCompilationCandidatePredicate = newRuntimeCompilationCandidatePredicate;
        runtimeCompilationCandidatePredicateUpdated = true;
        deoptimizeOnExceptionPredicate = newDeoptimizeOnExceptionPredicate;

        if (SubstrateOptions.IncludeNodeSourcePositions.getValue() || SubstrateOptions.parseOnce()) {
            graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
        }
    }

    public SubstrateMethod requireFrameInformationForMethod(ResolvedJavaMethod method, BeforeAnalysisAccessImpl config, boolean registerAsRoot) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        SubstrateMethod sMethod = objectReplacer.createMethod(aMethod);

        requireFrameInformationForMethodHelper(aMethod, config, registerAsRoot);

        return sMethod;
    }

    protected abstract void requireFrameInformationForMethodHelper(AnalysisMethod aMethod, BeforeAnalysisAccessImpl config, boolean registerAsRoot);

    public SubstrateMethod prepareMethodForRuntimeCompilation(Executable method, BeforeAnalysisAccessImpl config) {
        return prepareMethodForRuntimeCompilation(config.getMetaAccess().lookupJavaMethod(method), config);
    }

    public abstract void initializeAnalysisProviders(BigBang bb, Function<ConstantFieldProvider, ConstantFieldProvider> generator);

    public abstract void registerAllowInliningPredicate(AllowInliningPredicate predicate);

    public abstract SubstrateMethod prepareMethodForRuntimeCompilation(ResolvedJavaMethod method, BeforeAnalysisAccessImpl config);

    protected final void afterAnalysisHelper() {
        ProgressReporter.singleton().setNumRuntimeCompiledMethods(getRuntimeCompiledMethods().size());
    }

    /**
     * Checks if any illegal nodes are present within the graph. Runtime Compiled methods should
     * never have explicit BytecodeExceptions; instead they should have deoptimizations.
     */
    protected static boolean verifyNodes(StructuredGraph graph) {
        for (var node : graph.getNodes()) {
            boolean invalidNodeKind = node instanceof BytecodeExceptionNode || node instanceof ThrowBytecodeExceptionNode;
            assert !invalidNodeKind : "illegal node in graph: " + node + " method: " + graph.method();
        }
        return true;
    }

    protected final void beforeCompilationHelper() {
        if (Options.PrintRuntimeCompileMethods.getValue()) {
            System.out.println("****Start Print Runtime Compile Methods***");
            getRuntimeCompiledMethods().stream().map(m -> m.getMethod().format("%H.%n(%p)")).sorted().collect(Collectors.toList()).forEach(System.out::println);
            System.out.println("****End Print Runtime Compile Methods***");
        }

        if (Options.PrintRuntimeCompilationCallTree.getValue()) {
            System.out.println("****Start Print Runtime Compile Call Tree***");
            printCallTree();
            System.out.println("****End Print Runtime Compile Call Tree***");
        }

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
        if (Options.EnforceMaxRuntimeCompileMethods.getValue() && maxMethods != 0 && getRuntimeCompiledMethods().size() > maxMethods) {
            printDeepestLevelPath();
            throw VMError.shouldNotReachHere("Number of methods for runtime compilation exceeds the allowed limit: " + getRuntimeCompiledMethods().size() + " > " + maxMethods);
        }
    }

    private void printDeepestLevelPath() {
        AbstractCallTreeNode maxLevelCallTreeNode = null;
        for (var method : getRuntimeCompiledMethods()) {
            var callTreeNode = getCallTreeNode(method);
            if (maxLevelCallTreeNode == null || maxLevelCallTreeNode.getLevel() < callTreeNode.getLevel()) {
                maxLevelCallTreeNode = callTreeNode;
            }
        }

        System.out.println(String.format("Deepest level call tree path (%d calls):", maxLevelCallTreeNode.getLevel()));
        AbstractCallTreeNode node = maxLevelCallTreeNode;
        while (node != null) {
            AnalysisMethod implementationMethod = node.getImplementationMethod();
            AnalysisMethod targetMethod = node.getTargetMethod();

            System.out.format("%5d ; %s ; %s", node.getNodeCount(), node.getPosition(), implementationMethod == null ? ""
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
        for (var method : getRuntimeCompiledMethods()) {
            var node = getCallTreeNode(method);
            if (node.getLevel() == 0) {
                printCallTreeNode(node);
            }
        }
    }

    private void printCallTreeNode(AbstractCallTreeNode node) {
        AnalysisMethod implementationMethod = node.getImplementationMethod();
        AnalysisMethod targetMethod = node.getTargetMethod();

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < node.getLevel(); i++) {
            indent.append("  ");
        }
        if (implementationMethod != null) {
            indent.append(implementationMethod.format("%h.%n"));
        }

        System.out.format("%4d ; %-80s  ;%5d ; %s ; %s", node.getLevel(), indent, node.getNodeCount(), node.getPosition(),
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
        objectReplacer.updateSubstrateDataAfterCompilation(hUniverse, config.getProviders());
    }

    protected final void beforeHeapLayoutHelper(BeforeHeapLayoutAccess a) {
        objectReplacer.registerImmutableObjects(a);
        GraalSupport.registerImmutableObjects(a);
        ((SubstrateReplacements) GraalSupport.getRuntimeConfig().getProviders().getReplacements()).registerImmutableObjects(a);
    }

    protected final void afterHeapLayoutHelper(AfterHeapLayoutAccess a) {
        AfterHeapLayoutAccessImpl config = (AfterHeapLayoutAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();
        HostedUniverse hUniverse = hMetaAccess.getUniverse();
        objectReplacer.updateSubstrateDataAfterHeapLayout(hUniverse);
    }

    /**
     * Unwrap the hosted constant from an {@link ImageHeapConstant} before encoding the graph for
     * run time compilation. {@link ImageHeapConstant} is a hosted only constant representation.
     */
    public static void unwrapImageHeapConstants(StructuredGraph graph, MetaAccessProvider metaAccess) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ConstantNode constantNode) {
                if (constantNode.getValue() instanceof ImageHeapConstant heapConstant) {
                    VMError.guarantee(heapConstant.isBackedByHostedObject(), "Expected to find a heap object backed by a hosted object, found %s", heapConstant);
                    constantNode.replace(graph, ConstantNode.forConstant(heapConstant.getHostedObject(), metaAccess, graph));
                }
            }
        }
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
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        return true;
    }
}
