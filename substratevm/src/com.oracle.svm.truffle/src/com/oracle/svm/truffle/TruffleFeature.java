/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

//Checkstyle: allow reflection

import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsingMaxDepth;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue;
import org.graalvm.compiler.truffle.runtime.OptimizedAssumption;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.graal.hosted.GraalFeature.CallTreeNode;
import com.oracle.svm.graal.hosted.GraalFeature.RuntimeBytecodeParser;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.option.RuntimeOptionFeature;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.truffle.api.SubstrateOptimizedCallTarget;
import com.oracle.svm.truffle.api.SubstratePartialEvaluator;
import com.oracle.svm.truffle.api.SubstrateTruffleCompiler;
import com.oracle.svm.truffle.api.SubstrateTruffleCompilerImpl;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.library.DefaultExportProvider;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.profiles.Profile;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class TruffleFeature implements com.oracle.svm.core.graal.GraalFeature {

    public static class Options {
        @Option(help = "Check that CompilerAsserts.neverPartOfCompilation is not reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> TruffleCheckNeverPartOfCompilation = new HostedOptionKey<>(true);

        @Option(help = "Enforce that the Truffle runtime provides the only implementation of Frame")//
        public static final HostedOptionKey<Boolean> TruffleCheckFrameImplementation = new HostedOptionKey<>(true);

        @Option(help = "Fail if a method known as not suitable for partial evaluation is reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> TruffleCheckBlackListedMethods = new HostedOptionKey<>(false);

        @Option(help = "Inline trivial methods in Truffle graphs during native image generation")//
        public static final HostedOptionKey<Boolean> TruffleInlineDuringParsing = new HostedOptionKey<>(true);
    }

    /**
     * True in the first analysis run where contexts are pre-initialized.
     */
    private boolean firstAnalysisRun;

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(TruffleFeature.class);
        }
    }

    public static final class IsCreateProcessDisabled implements BooleanSupplier {
        static boolean query() {
            try {
                // Checkstyle: stop
                Class<?> clazz = Class.forName("com.oracle.truffle.polyglot.PolyglotEngineImpl");
                // Checkstyle: resume
                final boolean allowCreateProcess = ReflectionUtil.readField(clazz, "ALLOW_CREATE_PROCESS", null);
                return !allowCreateProcess;
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        static final boolean ALLOW_CREATE_PROCESS = query();

        @Override
        public boolean getAsBoolean() {
            return ALLOW_CREATE_PROCESS;
        }
    }

    public static class Support {
        public SubstrateOptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
            return new SubstrateOptimizedCallTarget(sourceCallTarget, rootNode);
        }

        public SubstratePartialEvaluator createPartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture) {
            return new SubstratePartialEvaluator(providers, configForRoot, snippetReflection, architecture);
        }

        @SuppressWarnings("unused")
        public void registerInterpreterEntryMethodsAsCompiled(PartialEvaluator partialEvaluator, BeforeAnalysisAccess access) {
        }

        public SubstrateTruffleCompiler createTruffleCompiler(SubstrateTruffleRuntime runtime) {
            GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
            SnippetReflectionProvider snippetReflectionProvider = graalFeature.getHostedProviders().getSnippetReflection();
            return new SubstrateTruffleCompilerImpl(runtime,
                            graalFeature.getHostedProviders().getGraphBuilderPlugins(),
                            GraalSupport.getSuites(),
                            GraalSupport.getLIRSuites(),
                            GraalSupport.getRuntimeConfig().getBackendForNormalMethod(),
                            GraalSupport.getFirstTierSuites(),
                            GraalSupport.getFirstTierLirSuites(),
                            GraalSupport.getFirstTierProviders(),
                            snippetReflectionProvider);
        }

        public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
            Object target = SubstrateObjectConstant.asObject(optimizedAssumptionConstant);
            OptimizedAssumption assumption = (OptimizedAssumption) KnownIntrinsics.convertUnknownValue(target, Object.class);
            return assumption.registerDependency();
        }

        public JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
            Object target = SubstrateObjectConstant.asObject(callNodeConstant);
            OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) KnownIntrinsics.convertUnknownValue(target, Object.class);
            OptimizedCallTarget callTarget = callNode.getCallTarget();
            return SubstrateObjectConstant.forObject(callTarget);
        }

        public BackgroundCompileQueue createBackgroundCompileQueue(@SuppressWarnings("unused") SubstrateTruffleRuntime runtime) {
            return new BackgroundCompileQueue(runtime);
        }

        public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
            return (CompilableTruffleAST) KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(OptimizedCallTarget.class, constant), Object.class);
        }

        @SuppressWarnings("unused")
        public boolean tryLog(SubstrateTruffleRuntime runtime, CompilableTruffleAST compilable, String message) {
            return false;
        }
    }

    private Support support;

    private final Set<ResolvedJavaMethod> blacklistMethods;
    private final Set<GraalFeature.CallTreeNode> blacklistViolations;
    private final Set<ResolvedJavaMethod> warnMethods;
    private final Set<GraalFeature.CallTreeNode> warnViolations;
    private final Set<GraalFeature.CallTreeNode> neverPartOfCompilationViolations;

    public TruffleFeature() {
        blacklistMethods = new HashSet<>();
        blacklistViolations = new TreeSet<>(TruffleFeature::blacklistViolationComparator);
        warnMethods = new HashSet<>();
        warnViolations = new TreeSet<>(TruffleFeature::blacklistViolationComparator);
        neverPartOfCompilationViolations = new TreeSet<>(TruffleFeature::blacklistViolationComparator);
    }

    public static TruffleFeature getSingleton() {
        return ImageSingletons.lookup(TruffleFeature.class);
    }

    public static void setSupport(Support support) {
        getSingleton().support = support;
    }

    public static Support getSupport() {
        return getSingleton().support;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(GraalFeature.class, NodeClassFeature.class);
    }

    private static void initializeTruffleReflectively(ClassLoader imageClassLoader) {
        invokeStaticMethod("com.oracle.truffle.api.impl.Accessor", "getTVMCI", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "initializeNativeImageState", Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "initializeNativeImageState", Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.api.impl.TruffleLocator", "initializeNativeImageState", Collections.emptyList());
    }

    public static void removeTruffleLanguage(String mimeType) {
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "removeLanguageFromNativeImage", Collections.singletonList(String.class), mimeType);
    }

    private static Collection<Class<?>> getLanguageClasses() {
        return invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "getLanguageClasses", Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStaticMethod(String className, String methodName, Collection<Class<?>> parameterTypes, Object... args) {
        try {
            // Checkstyle: stop
            Class<?> clazz = Class.forName(className);
            // Checkstyle: resume
            Method method = ReflectionUtil.lookupMethod(clazz, methodName, parameterTypes.toArray(new Class<?>[0]));
            return (T) method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        if (support == null) {
            support = new Support();
        }

        TruffleRuntime runtime = Truffle.getRuntime();
        UserError.guarantee(runtime != null, "TruffleRuntime not available via Truffle.getRuntime()");
        UserError.guarantee(runtime instanceof SubstrateTruffleRuntime || runtime instanceof DefaultTruffleRuntime,
                        "Unsupported TruffleRuntime %s (only SubstrateTruffleRuntime or DefaultTruffleRuntime allowed)",
                        runtime.getClass().getName());

        RuntimeClassInitialization.initializeAtBuildTime("com.oracle.graalvm.locator", "Truffle classes are always initialized at build time");

        if (useTruffleCompiler()) {
            SubstrateTruffleRuntime truffleRuntime = (SubstrateTruffleRuntime) runtime;
            truffleRuntime.resetHosted();
        }
        for (TruffleLanguage.Provider provider : ServiceLoader.load(TruffleLanguage.Provider.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(provider.getClass());
        }
        for (TruffleInstrument.Provider provider : ServiceLoader.load(TruffleInstrument.Provider.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(provider.getClass());
        }
        initializeTruffleReflectively(Thread.currentThread().getContextClassLoader());

        // reinitialize language cache
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "reinitializeNativeImageState", Collections.emptyList());
    }

    @Override
    public void cleanup() {
        // clean the cached call target nodes to prevent them from keeping application classes alive
        TruffleRuntime runtime = Truffle.getRuntime();
        if (runtime instanceof SubstrateTruffleRuntime) {
            ((SubstrateTruffleRuntime) runtime).resetNativeImageState();
        } else if (!(runtime instanceof DefaultTruffleRuntime)) {
            throw VMError.shouldNotReachHere("Only SubstrateTruffleRuntime and DefaultTruffleRuntime supported");
        }

        // clean up the language cache
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("org.graalvm.polyglot.Engine$ImplHolder", "resetPreInitializedEngine", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.impl.TruffleLocator", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.nodes.Node", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.source.Source", "resetNativeImageState", Collections.emptyList());
    }

    public static boolean useTruffleCompiler() {
        return Truffle.getRuntime() instanceof SubstrateTruffleRuntime;
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        /*
         * We need to constant-fold Profile.isProfilingEnabled already during static analysis, so
         * that we get exact types for fields that store profiles.
         */
        Registration r = new Registration(invocationPlugins, Profile.class);
        r.register0("isProfilingEnabled", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(Truffle.getRuntime().isProfilingEnabled()));
                return true;
            }
        });

        if (analysis || hosted) {
            /*
             * For AOT compilation and static analysis, we intrinsify CompilerDirectives.castExact
             * with explicit exception edges. For runtime compilation, TruffleGraphBuilderPlugins
             * registers a plugin that uses deoptimization.
             */
            r = new Registration(invocationPlugins, CompilerDirectives.class);
            SubstrateGraphBuilderPlugins.registerCastExact(r);
        }
    }

    private void registerNeverPartOfCompilation(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerAsserts.class);
        r.setAllowOverwrite(true);
        r.register0("neverPartOfCompilation", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return handleNeverPartOfCompilation(b, targetMethod, null);
            }
        });
        r.register1("neverPartOfCompilation", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                return handleNeverPartOfCompilation(b, targetMethod, message);
            }
        });
    }

    private boolean handleNeverPartOfCompilation(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode messageNode) {
        String message = "CompilerAsserts.neverPartOfCompilation()";
        if (messageNode != null && messageNode.isConstant()) {
            message = messageNode.asConstant().toValueString();
        }
        NeverPartOfCompilationNode neverPartOfCompilation = b.add(new NeverPartOfCompilationNode(message));

        if (Options.TruffleCheckNeverPartOfCompilation.getValue()) {
            if (neverPartOfCompilation.stateAfter().getMethod().getDeclaringClass().equals(targetMethod.getDeclaringClass())) {
                /* Ignore internal use from another method in CompilerAsserts class. */
            } else {
                CallTreeNode callerNode = ((RuntimeBytecodeParser) b).getCallTreeNode();
                CallTreeNode calleeNode = new CallTreeNode(targetMethod, targetMethod, callerNode, callerNode.getLevel() + 1, GraalFeature.buildSourceReference(neverPartOfCompilation.stateAfter()));
                neverPartOfCompilationViolations.add(calleeNode);
            }
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;

        getLanguageClasses().forEach(RuntimeReflection::registerForReflectiveInstantiation);

        config.registerHierarchyForReflectiveInstantiation(DefaultExportProvider.class);
        config.registerHierarchyForReflectiveInstantiation(TruffleInstrument.class);

        registerDynamicObjectFields(config);

        if (useTruffleCompiler()) {
            SubstrateTruffleRuntime truffleRuntime = (SubstrateTruffleRuntime) Truffle.getRuntime();
            GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
            SnippetReflectionProvider snippetReflection = graalFeature.getHostedProviders().getSnippetReflection();
            SubstrateTruffleCompiler truffleCompiler = truffleRuntime.initTruffleCompiler();
            truffleRuntime.lookupCallMethods(config.getMetaAccess());

            PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();
            registerKnownTruffleFields(config, partialEvaluator.getKnownTruffleTypes());
            support.registerInterpreterEntryMethodsAsCompiled(partialEvaluator, access);

            registerTruffleOptions(config);

            GraphBuilderConfiguration graphBuilderConfig = partialEvaluator.getConfigPrototype();

            if (Options.TruffleInlineDuringParsing.getValue()) {
                graphBuilderConfig.getPlugins().appendInlineInvokePlugin(
                                new TruffleParsingInlineInvokePlugin(config.getHostVM(), graalFeature.getHostedProviders().getReplacements(), graphBuilderConfig.getPlugins().getInvocationPlugins(),
                                                partialEvaluator, method -> includeCallee(method, null, null)));
            }

            registerNeverPartOfCompilation(graphBuilderConfig.getPlugins().getInvocationPlugins());
            graphBuilderConfig.getPlugins().getInvocationPlugins().closeRegistration();

            HostedProviders newHostedProviders = new HostedProviders(
                            partialEvaluator.getProviders().getMetaAccess(),
                            partialEvaluator.getProviders().getCodeCache(),
                            partialEvaluator.getProviders().getConstantReflection(),
                            new HostedTruffleConstantFieldProvider(partialEvaluator.getProviders().getConstantFieldProvider()),
                            partialEvaluator.getProviders().getForeignCalls(),
                            partialEvaluator.getProviders().getLowerer(),
                            partialEvaluator.getProviders().getReplacements(),
                            partialEvaluator.getProviders().getStampProvider(),
                            snippetReflection,
                            graalFeature.getHostedProviders().getWordTypes(),
                            graalFeature.getHostedProviders().getPlatformConfigurationProvider(),
                            graalFeature.getHostedProviders().getMetaAccessExtensionProvider());
            newHostedProviders.setGraphBuilderPlugins(graphBuilderConfig.getPlugins());

            graalFeature.initializeRuntimeCompilationConfiguration(newHostedProviders, graphBuilderConfig, this::includeCallee, this::deoptimizeOnException);
            for (ResolvedJavaMethod method : partialEvaluator.getCompilationRootMethods()) {
                graalFeature.prepareMethodForRuntimeCompilation(method, config);
            }

            initializeMethodBlacklist(config.getMetaAccess());

            /*
             * Stack frames that are visited by Truffle-level stack walking must have full frame
             * information available, otherwise SubstrateStackIntrospection cannot visit them.
             */
            for (ResolvedJavaMethod method : truffleRuntime.getAnyFrameMethod()) {
                graalFeature.requireFrameInformationForMethod(method);
                /*
                 * To avoid corner case errors, we also force compilation of these methods. This
                 * only affects builds where no Truffle language is included, because any real
                 * language makes these methods reachable (and therefore compiled).
                 */
                config.registerAsCompiled((AnalysisMethod) method);
            }
        }
        firstAnalysisRun = true;
    }

    private static Class<?> findGeneratedLibraryClass(DuringAnalysisAccess config, Class<?> lib) {
        String className = lib.getPackage().getName() + "." + lib.getSimpleName() + "Gen";
        Class<?> genClass = config.findClassByName(className);
        if (genClass == null) {
            if (className.startsWith("com.oracle.truffle.api.library.test")) {
                /*
                 * The Truffle unit tests contain libraries that don't have generated code as they
                 * were deliberately containing errors. Ignore them for this check.
                 */
                return null;
            }
            throw UserError.abort(String.format("Could not find generated library class '%s'. Did the Java compilation succeed and did the Truffle annotation processor run?", className));
        }
        return genClass;
    }

    /**
     * The {@link SharedTruffleRuntimeOptions} are initialized by values assigned to
     * {@link SharedTruffleCompilerOptions}. Fields of the latter must be registered as as accessed
     * so that the {@link RuntimeOptionFeature} will pick them up to make the options settable at
     * runtime.
     */
    private static void registerTruffleOptions(BeforeAnalysisAccessImpl config) {
        for (Field field : SharedTruffleCompilerOptions.class.getDeclaredFields()) {
            if (OptionKey.class.isAssignableFrom(field.getType())) {
                config.registerAsAccessed(field);
            }
        }
    }

    static class TruffleParsingInlineInvokePlugin implements InlineInvokePlugin {

        private final SVMHost hostVM;
        private final Replacements replacements;
        private final InvocationPlugins invocationPlugins;
        private final PartialEvaluator partialEvaluator;
        private final Predicate<ResolvedJavaMethod> includeMethodPredicate;

        TruffleParsingInlineInvokePlugin(SVMHost hostVM, Replacements replacements, InvocationPlugins invocationPlugins, PartialEvaluator partialEvaluator,
                        Predicate<ResolvedJavaMethod> includeMethodPredicate) {
            this.hostVM = hostVM;
            this.replacements = replacements;
            this.invocationPlugins = invocationPlugins;
            this.partialEvaluator = partialEvaluator;
            this.includeMethodPredicate = includeMethodPredicate;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            if (SubstrateUtil.NativeImageLoadingShield.isNeverInline(original)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (invocationPlugins.lookupInvocation(original) != null) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (original.getAnnotation(ExplodeLoop.class) != null) {
                /*
                 * We cannot inline a method annotated with @ExplodeLoop, because then loops are no
                 * longer exploded.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (builder.getMethod().getAnnotation(ExplodeLoop.class) != null) {
                /*
                 * We cannot inline anything into a method annotated with @ExplodeLoop, because then
                 * loops of the inlined callee are exploded too.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (replacements.hasSubstitution(original)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }

            for (ResolvedJavaMethod m : partialEvaluator.getNeverInlineMethods()) {
                if (original.equals(m)) {
                    return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
                }
            }

            if (original.getCode() != null && includeMethodPredicate.test(original) && hostVM.isAnalysisTrivialMethod((AnalysisMethod) original) &&
                            builder.getDepth() < InlineDuringParsingMaxDepth.getValue(HostedOptionValues.singleton())) {
                return createStandardInlineInfo(original);
            }

            return null;
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (firstAnalysisRun) {
            firstAnalysisRun = false;
            Object keep = invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotContextImpl", "resetSingleContextState", Collections.singleton(boolean.class), false);
            invokeStaticMethod("org.graalvm.polyglot.Engine$ImplHolder", "preInitializeEngine", Collections.emptyList());
            access.requireAnalysisIteration();
            invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotContextImpl", "restoreSingleContextState", Collections.singleton(Object.class), keep);
        }

        /*
         * Register library and exports for Truffle Libraries.
         */
        for (AnalysisType type : ((DuringAnalysisAccessImpl) access).getBigBang().getUniverse().getTypes()) {
            for (ExportLibrary library : type.getDeclaredAnnotationsByType(ExportLibrary.class)) {
                Class<?> genLib = findGeneratedLibraryClass(access, library.value());
                if (genLib != null) {
                    access.registerAsInHeap(genLib);
                }
            }
            GenerateLibrary generateLibrary = type.getAnnotation(GenerateLibrary.class);
            if (generateLibrary != null) {
                Class<?> lib = findGeneratedLibraryClass(access, type.getJavaClass());
                if (lib != null) {
                    access.registerAsInHeap(lib);
                }
            }
        }

        initializeDynamicObjectLayouts(access);
    }

    private final Set<Class<?>> dynamicObjectClasses = new HashSet<>();

    private void initializeDynamicObjectLayouts(DuringAnalysisAccess access) {
        DuringAnalysisAccessImpl config = (DuringAnalysisAccessImpl) access;
        for (AnalysisType type : config.getUniverse().getTypes()) {
            if (!type.isInstantiated()) {
                continue;
            }
            Class<?> javaClass = type.getJavaClass();
            if (DynamicObject.class.isAssignableFrom(javaClass) && dynamicObjectClasses.add(javaClass)) {
                // Force layout initialization.
                Layout.newLayout().type(javaClass.asSubclass(DynamicObject.class)).build();
            }
        }
    }

    private static void registerKnownTruffleFields(BeforeAnalysisAccessImpl config, KnownTruffleTypes knownTruffleFields) {
        for (Class<?> klass = knownTruffleFields.getClass(); klass != Object.class; klass = klass.getSuperclass()) {
            for (Field field : klass.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    try {
                        Object value = field.get(knownTruffleFields);
                        if (value != null && value instanceof ResolvedJavaField) {
                            config.registerAsAccessed((AnalysisField) value);
                        }
                    } catch (IllegalAccessException ex) {
                        throw VMError.shouldNotReachHere(ex);
                    }
                }
            }
        }
    }

    private static void registerDynamicObjectFields(BeforeAnalysisAccessImpl config) {
        Class<?> dynamicFieldClass = config.findClassByName(DynamicObject.class.getName().concat("$DynamicField"));
        if (dynamicFieldClass == null) {
            throw VMError.shouldNotReachHere("DynamicObject.DynamicField annotation not found.");
        }
        for (Field field : config.findAnnotatedFields(dynamicFieldClass.asSubclass(Annotation.class))) {
            config.registerAsUnsafeAccessed(field);
        }
    }

    private boolean includeCallee(GraalFeature.CallTreeNode calleeNode, List<AnalysisMethod> implementationMethods) {
        return includeCallee(calleeNode.getImplementationMethod(), calleeNode, implementationMethods);
    }

    private boolean includeCallee(ResolvedJavaMethod implementationMethod, GraalFeature.CallTreeNode calleeNode, List<AnalysisMethod> implementationMethods) {
        Uninterruptible uninterruptibleAnnotation = implementationMethod.getAnnotation(Uninterruptible.class);
        if (implementationMethod.getAnnotation(CompilerDirectives.TruffleBoundary.class) != null) {
            return false;
        } else if (SubstrateUtil.NativeImageLoadingShield.isNeverInline(implementationMethod)) {
            /* Ensure that NeverInline methods are also never inlined during Truffle compilation. */
            return false;
        } else if (uninterruptibleAnnotation != null && !uninterruptibleAnnotation.mayBeInlined()) {
            /* The semantics of Uninterruptible would get lost during partial evaluation. */
            return false;
        } else if (implementationMethod.getAnnotation(TruffleCallBoundary.class) != null) {
            return false;
        } else if (calleeNode != null && implementationMethods.size() > 4 && isBlacklisted(calleeNode.getTargetMethod())) {
            blacklistViolations.add(new GraalFeature.CallTreeNode(calleeNode.getTargetMethod(), calleeNode.getTargetMethod(), calleeNode.getParent(), calleeNode.getLevel(),
                            calleeNode.getSourceReference()));
            return false;
        } else if (isBlacklisted(implementationMethod)) {
            if (calleeNode != null) {
                blacklistViolations.add(calleeNode);
            }
            return false;

        } else if (warnMethods.contains(implementationMethod)) {
            if (calleeNode != null) {
                warnViolations.add(calleeNode);
            }
        }

        return true;
    }

    private boolean isBlacklisted(ResolvedJavaMethod method) {
        if (!((AnalysisMethod) method).allowRuntimeCompilation()) {
            return true;
        }

        if (method.isSynchronized() && method.getName().equals("fillInStackTrace")) {
            /*
             * We do not want anything related to Throwable.fillInStackTrace in the image. For
             * simplicity, we just check the method name and not the declaring class too, but it is
             * unlikely that some non-exception method is called "fillInStackTrace".
             */
            return true;
        }
        return blacklistMethods.contains(method);
    }

    @SuppressWarnings("deprecation")
    private boolean deoptimizeOnException(ResolvedJavaMethod method) {
        if (method == null) {
            return false;
        }
        CompilerDirectives.TruffleBoundary truffleBoundary = method.getAnnotation(CompilerDirectives.TruffleBoundary.class);
        return truffleBoundary != null && truffleBoundary.transferToInterpreterOnException();
    }

    private void initializeMethodBlacklist(MetaAccessProvider metaAccess) {
        blacklistMethod(metaAccess, Object.class, "clone");
        blacklistMethod(metaAccess, Object.class, "equals", Object.class);
        blacklistMethod(metaAccess, Object.class, "hashCode");
        blacklistMethod(metaAccess, Object.class, "toString");
        blacklistMethod(metaAccess, String.class, "valueOf", Object.class);
        blacklistMethod(metaAccess, String.class, "getBytes");
        blacklistMethod(metaAccess, Throwable.class, "initCause", Throwable.class);
        blacklistMethod(metaAccess, System.class, "getProperty", String.class);

        blacklistAllMethods(metaAccess, AssertionError.class);
        blacklistAllMethods(metaAccess, BigInteger.class);
        blacklistAllMethods(metaAccess, BigDecimal.class);
        blacklistAllMethods(metaAccess, Comparable.class);
        blacklistAllMethods(metaAccess, Comparator.class);
        blacklistAllMethods(metaAccess, Collection.class);
        blacklistAllMethods(metaAccess, List.class);
        blacklistAllMethods(metaAccess, Set.class);
        blacklistAllMethods(metaAccess, Map.class);
        blacklistAllMethods(metaAccess, Map.Entry.class);
        blacklistAllMethods(metaAccess, TreeMap.class);
        blacklistAllMethods(metaAccess, HashMap.class);
        blacklistAllMethods(metaAccess, ConcurrentHashMap.class);
        blacklistAllMethods(metaAccess, WeakHashMap.class);
        blacklistAllMethods(metaAccess, IdentityHashMap.class);
        blacklistAllMethods(metaAccess, Iterable.class);
        blacklistAllMethods(metaAccess, Iterator.class);
        blacklistAllMethods(metaAccess, ListIterator.class);
        blacklistAllMethods(metaAccess, ReentrantLock.class);

        whitelistMethod(metaAccess, BigInteger.class, "signum");
        whitelistMethod(metaAccess, ReentrantLock.class, "isLocked");
        whitelistMethod(metaAccess, ReentrantLock.class, "isHeldByCurrentThread");

        /* Methods with synchronization are currently not supported as deoptimization targets. */
        blacklistAllMethods(metaAccess, StringBuffer.class);
        blacklistAllMethods(metaAccess, Vector.class);
        blacklistAllMethods(metaAccess, Hashtable.class);

        /*
         * Core Substrate VM classes that very certainly should not be reachable for runtime
         * compilation. Warn when they get reachable to detect explosion of reachable methods.
         */
        warnAllMethods(metaAccess, JavaStackWalker.class);
        warnAllMethods(metaAccess, Deoptimizer.class);
        warnAllMethods(metaAccess, Heap.getHeap().getClass());
    }

    private void blacklistAllMethods(MetaAccessProvider metaAccess, Class<?> clazz) {
        for (Executable m : clazz.getDeclaredMethods()) {
            blacklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
        for (Executable m : clazz.getDeclaredConstructors()) {
            blacklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
    }

    private void blacklistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            blacklistMethods.add(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Removes a previously blacklisted method from the blacklist.
     */
    private void whitelistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            if (!blacklistMethods.remove(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)))) {
                throw VMError.shouldNotReachHere();
            }
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void warnAllMethods(MetaAccessProvider metaAccess, Class<?> clazz) {
        for (Executable m : clazz.getDeclaredMethods()) {
            /*
             * Filter out methods that are, e.g., only present on a certain platform. We do not need
             * all methods in the warning list, just enough to trigger the warnings. Accessors are
             * generally allowed too.
             */
            if (m.getAnnotations().length == 0 && !m.getName().startsWith("get") && !m.getName().startsWith("set")) {
                warnMethods.add(metaAccess.lookupJavaMethod(m));
            }
        }
        for (Executable m : clazz.getDeclaredConstructors()) {
            if (m.getAnnotations().length == 0) {
                warnMethods.add(metaAccess.lookupJavaMethod(m));
            }
        }
    }

    private static int blacklistViolationComparator(GraalFeature.CallTreeNode n1, GraalFeature.CallTreeNode n2) {
        int result = n1.getTargetMethod().getQualifiedName().compareTo(n2.getTargetMethod().getQualifiedName());
        if (result == 0) {
            result = n1.getSourceReference().compareTo(n2.getSourceReference());
        }
        return result;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess config) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) config;

        boolean failBlackListViolations = Options.TruffleCheckBlackListedMethods.getValue();
        boolean printBlackListViolations = GraalFeature.Options.PrintRuntimeCompileMethods.getValue() || failBlackListViolations;
        if (printBlackListViolations && blacklistViolations.size() > 0) {
            System.out.println();
            System.out.println("=== Found " + blacklistViolations.size() + " compilation blacklist violations ===");
            System.out.println();
            for (GraalFeature.CallTreeNode node : blacklistViolations) {
                System.out.println("Blacklisted method");
                System.out.println(node.getImplementationMethod().format("  %H.%n(%p)"));
                System.out.println("called from");
                for (GraalFeature.CallTreeNode cur = node; cur != null; cur = cur.getParent()) {
                    System.out.println("  " + cur.getSourceReference());
                }
            }
            if (failBlackListViolations) {
                throw VMError.shouldNotReachHere("Blacklisted methods are reachable for runtime compilation");
            }
        }

        if (warnViolations.size() > 0) {
            /*
             * It is enough to print one warning message with one stack trace. Take the shortest
             * stack trace.
             */
            GraalFeature.CallTreeNode printNode = null;
            int printLength = Integer.MAX_VALUE;
            for (GraalFeature.CallTreeNode warnNode : warnViolations) {
                int warnLength = 0;
                for (GraalFeature.CallTreeNode cur = warnNode; cur != null; cur = cur.getParent()) {
                    warnLength++;
                }
                if (warnLength < printLength) {
                    printNode = warnNode;
                    printLength = warnLength;
                }
            }

            System.out.println("WARNING: suspicious method reachable for runtime compilation: " + printNode.getImplementationMethod().format("%H.%n(%p)"));
            System.out.println("Check the complete tree of reachable methods using the option " + GraalFeature.Options.PrintRuntimeCompileMethods.getDescriptor().getFieldName());
            System.out.println("Suspicious method is called from");
            for (GraalFeature.CallTreeNode cur = printNode; cur != null; cur = cur.getParent()) {
                System.out.println("  " + cur.getSourceReference());
            }
        }

        if (neverPartOfCompilationViolations.size() > 0) {
            System.out.println("ERROR: CompilerAsserts.neverPartOfCompilation reachable for runtime compilation from " + neverPartOfCompilationViolations.size() + " places:");
            for (GraalFeature.CallTreeNode neverPartOfCompilationNode : neverPartOfCompilationViolations) {
                System.out.println("called from");
                for (GraalFeature.CallTreeNode cur = neverPartOfCompilationNode; cur != null; cur = cur.getParent()) {
                    System.out.println("  " + cur.getSourceReference());
                }
            }
            throw VMError.shouldNotReachHere("CompilerAsserts.neverPartOfCompilation reachable for runtime compilation");
        }

        if (Options.TruffleCheckFrameImplementation.getValue() && useTruffleCompiler()) {
            /*
             * Check that only one Frame implementation is seen as instantiated by the static
             * analysis. That allows de-virtualization of all calls to Frame methods in the
             * interpreter.
             *
             * The DefaultTruffleRuntime uses multiple Frame implementations (DefaultVirtualFrame,
             * DefaultMaterializedFrame, ReadOnlyFrame) to detect wrong usages of the Frame API, so
             * we can only check when running with compilation enabled.
             */
            Optional<? extends ResolvedJavaType> optionalFrameType = access.getMetaAccess().optionalLookupJavaType(Frame.class);
            if (optionalFrameType.isPresent()) {
                HostedType frameType = (HostedType) optionalFrameType.get();
                Set<HostedType> implementations = new HashSet<>();
                collectImplementations(frameType, implementations);

                if (implementations.size() > 1) {
                    throw UserError.abort("More than one implementation of " + Frame.class.getTypeName() +
                                    " found. For performance reasons, Truffle languages must not provide new implementations, and instead only use the single implementation provided by the Truffle runtime. " +
                                    "To disable this check, add " + SubstrateOptionsParser.commandArgument(Options.TruffleCheckFrameImplementation, "-") + " to the native-image command line. " +
                                    "Found classes: " + implementations.stream().map(m -> m.toJavaName(true)).collect(Collectors.joining(", ")));
                } else {
                    assert implementations.size() == 0 || implementations.iterator().next() == frameType.getSingleImplementor();
                }
            }
        }
    }

    private static void collectImplementations(HostedType type, Set<HostedType> implementations) {
        for (HostedType subType : type.getSubTypes()) {
            if (!subType.isAbstract()) {
                implementations.add(subType);
            }
            collectImplementations(subType, implementations);
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_truffle_runtime_OptimizedCallTarget {

    /*
     * Retry compilation when they failed during image generation.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    boolean compilationFailed;
    /*
     * The initialized time stamp is not useful when collected during image generation.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    long initializedTimestamp;
}

// Checkstyle: stop

@TargetClass(className = "com.oracle.truffle.polyglot.PolyglotContextImpl", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_PolyglotContextImpl {

    /**
     * Truffle code can run during image generation, i.e., one or many contexts can be used during
     * image generation. Truffle optimizes the case where only one context is ever created, and also
     * stores additional information regarding which thread or threads used the context. We need to
     * start with a completely fresh specialization state. To simplify that, all static state that
     * stores context information is abstracted in the SingleContextState class, and it is enough to
     * recompute a single static field to a new SingleContextState instance.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClassName = "com.oracle.truffle.polyglot.PolyglotContextImpl$SingleContextState", isFinal = true) //
    static Target_com_oracle_truffle_polyglot_PolyglotContextImpl_SingleContextState singleContextState;
}

@TargetClass(className = "com.oracle.truffle.polyglot.ContextThreadLocal", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_ContextThreadLocal {

    /**
     * Don't store any threads in the image.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    Thread activeSingleThread;
}

@TargetClass(className = "com.oracle.truffle.polyglot.PolyglotContextImpl$SingleContextState", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_PolyglotContextImpl_SingleContextState {
}

// If allowProcess() is disabled at build time, then we ensure that ProcessBuilder is not reachable.
// The main purpose of this is to test that ProcessBuilder is not part of the image when building
// language images with allowProcess() disabled, which we interpret as "forbid shelling out to
// external processes" (GR-14041).
@Delete
@TargetClass(className = "java.lang.ProcessBuilder", onlyWith = {TruffleFeature.IsEnabled.class, TruffleFeature.IsCreateProcessDisabled.class})
final class Target_java_lang_ProcessBuilder {
}

// If allowProcess() is disabled at build time, then we ensure ObjdumpDisassemblerProvider does not
// try to invoke the nonexistent ProcessBuilder.
@TargetClass(className = "org.graalvm.compiler.code.ObjdumpDisassemblerProvider", onlyWith = {TruffleFeature.IsEnabled.class, TruffleFeature.IsCreateProcessDisabled.class})
final class Target_org_graalvm_compiler_code_ObjdumpDisassemblerProvider {

    @Substitute
    @SuppressWarnings("unused")
    static Process createProcess(String[] cmd) {
        return null;
    }
}

@TargetClass(className = "com.oracle.truffle.polyglot.LanguageCache", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_LanguageCache {

    /*
     * The field is also reset explicitly in LanguageCache.resetNativeImageCacheLanguageHomes.
     * However, the explicit reset comes too late for the String-must-not-contain-the-home-directory
     * verification in DisallowedImageHeapObjectFeature, so we also do the implicit reset using a
     * substitution.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private String languageHome;
}

@TargetClass(className = "com.oracle.truffle.polyglot.HostObject", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_HostObject {

    /**
     * TODO: Remove when java.lang.reflect.Array.getLength is made PE-safe (GR-23860).
     */
    @Substitute
    static int getArrayLength(Object array) {
        if (array == null || !array.getClass().isArray()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }
        return KnownIntrinsics.readArrayLength(array);
    }
}

@TargetClass(className = "com.oracle.truffle.object.CoreLocations$DynamicObjectFieldLocation", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_object_CoreLocations_DynamicObjectFieldLocation {
    @Alias @RecomputeFieldValue(kind = Kind.AtomicFieldUpdaterOffset)//
    private long offset;
}

@TargetClass(className = "com.oracle.truffle.object.CoreLocations$DynamicLongFieldLocation", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_object_CoreLocations_DynamicLongFieldLocation {
    @Alias @RecomputeFieldValue(kind = Kind.AtomicFieldUpdaterOffset)//
    private long offset;
}
