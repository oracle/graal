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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.graal.hosted.GraalFeature.CallTreeNode;
import com.oracle.svm.graal.hosted.GraalFeature.RuntimeBytecodeParser;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.truffle.api.SubstrateThreadLocalHandshake;
import com.oracle.svm.truffle.api.SubstrateThreadLocalHandshakeSnippets;
import com.oracle.svm.truffle.api.SubstrateTruffleCompiler;
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
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryExport;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.Profile;

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

        @Option(help = "Fail if a method known as not suitable for partial evaluation is reachable for runtime compilation", deprecated = true)//
        public static final HostedOptionKey<Boolean> TruffleCheckBlackListedMethods = new HostedOptionKey<>(true);

        @Option(help = "Fail if a method known as not suitable for partial evaluation is reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> TruffleCheckBlockListMethods = new HostedOptionKey<>(true);

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

    // Checkstyle: stop
    private ClassLoader imageClassLoader;
    // Checkstyle: resume

    private final Set<ResolvedJavaMethod> blocklistMethods;
    private final Set<GraalFeature.CallTreeNode> blocklistViolations;
    private final Set<ResolvedJavaMethod> warnMethods;
    private final Set<GraalFeature.CallTreeNode> warnViolations;
    private final Set<GraalFeature.CallTreeNode> neverPartOfCompilationViolations;

    public TruffleFeature() {
        blocklistMethods = new HashSet<>();
        blocklistViolations = new TreeSet<>(TruffleFeature::blocklistViolationComparator);
        warnMethods = new HashSet<>();
        warnViolations = new TreeSet<>(TruffleFeature::blocklistViolationComparator);
        neverPartOfCompilationViolations = new TreeSet<>(TruffleFeature::blocklistViolationComparator);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(GraalFeature.class, NodeClassFeature.class);
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection, SubstrateForeignCallsProvider foreignCalls, boolean hosted) {
        foreignCalls.register(providers, SubstrateThreadLocalHandshake.FOREIGN_POLL);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        new SubstrateThreadLocalHandshakeSnippets(options, factories, providers, snippetReflection, lowerings);
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
        imageClassLoader = a.getApplicationClassLoader();

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
        initializeTruffleReflectively(imageClassLoader);

        // reinitialize language cache
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "reinitializeNativeImageState", Collections.emptyList());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(TruffleSupport.class)) {
            ImageSingletons.add(TruffleSupport.class, new TruffleSupport());
        }
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
        invokeStaticMethod("com.oracle.truffle.api.impl.ThreadLocalHandshake", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "resetNativeImageState", Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.api.nodes.Node", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.source.Source", "resetNativeImageState", Collections.emptyList());
        // clean up cached object layouts
        invokeStaticMethod("com.oracle.truffle.object.LayoutImpl", "resetNativeImageState", Collections.emptyList());
    }

    public static boolean useTruffleCompiler() {
        return Truffle.getRuntime() instanceof SubstrateTruffleRuntime;
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        StaticObjectSupport.registerInvocationPlugins(plugins, reason);

        /*
         * We need to constant-fold Profile.isProfilingEnabled already during static analysis, so
         * that we get exact types for fields that store profiles.
         */
        Registration r = new Registration(plugins.getInvocationPlugins(), Profile.class);
        r.register0("isProfilingEnabled", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                boolean constantBoolean;
                if (profilingEnabled == null) {
                    constantBoolean = false;
                } else {
                    constantBoolean = profilingEnabled;
                }
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(constantBoolean));
                return true;
            }
        });

        if (reason != ParsingReason.JITCompilation) {
            r = new Registration(plugins.getInvocationPlugins(), CompilerDirectives.class);
            /*
             * For AOT compilation and static analysis, we intrinsify CompilerDirectives.castExact
             * with explicit exception edges. For runtime compilation, TruffleGraphBuilderPlugins
             * registers a plugin that uses deoptimization.
             */
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

    private Boolean profilingEnabled;

    @SuppressWarnings("deprecation")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        StaticObjectSupport.beforeAnalysis(access);

        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;

        getLanguageClasses().forEach(RuntimeReflection::registerForReflectiveInstantiation);

        config.registerHierarchyForReflectiveInstantiation(DefaultExportProvider.class);
        config.registerHierarchyForReflectiveInstantiation(TruffleInstrument.class);

        registerDynamicObjectFields(config);

        config.registerSubtypeReachabilityHandler(TruffleFeature::registerTruffleLibrariesAsInHeap, LibraryFactory.class);
        config.registerSubtypeReachabilityHandler(TruffleFeature::registerTruffleLibrariesAsInHeap, LibraryExport.class);

        if (useTruffleCompiler()) {
            SubstrateTruffleRuntime truffleRuntime = (SubstrateTruffleRuntime) Truffle.getRuntime();

            for (Class<?> initType : truffleRuntime.getLookupTypes()) {
                access.registerAsUsed(initType);
            }

            // register thread local foreign poll as compiled otherwise the stub won't work
            config.registerAsCompiled((AnalysisMethod) SubstrateThreadLocalHandshake.FOREIGN_POLL.findMethod(config.getMetaAccess()));

            GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
            SnippetReflectionProvider snippetReflection = graalFeature.getHostedProviders().getSnippetReflection();
            SubstrateTruffleCompiler truffleCompiler = truffleRuntime.initTruffleCompiler();
            truffleRuntime.lookupCallMethods(config.getMetaAccess());

            PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();
            registerKnownTruffleFields(config, partialEvaluator.getKnownTruffleTypes());
            TruffleSupport.singleton().registerInterpreterEntryMethodsAsCompiled(partialEvaluator, access);

            GraphBuilderConfiguration graphBuilderConfig = partialEvaluator.getConfigPrototype();

            if (Options.TruffleInlineDuringParsing.getValue()) {
                graphBuilderConfig.getPlugins().appendInlineInvokePlugin(
                                new TruffleParsingInlineInvokePlugin(config.getHostVM(), graalFeature.getHostedProviders().getReplacements(), graphBuilderConfig.getPlugins().getInvocationPlugins(),
                                                partialEvaluator, method -> includeCallee(method, null, null)));
            }

            registerNeverPartOfCompilation(graphBuilderConfig.getPlugins().getInvocationPlugins());
            graphBuilderConfig.getPlugins().getInvocationPlugins().closeRegistration();

            Providers peProviders = partialEvaluator.getProviders();
            HostedProviders newHostedProviders = new HostedProviders(
                            peProviders.getMetaAccess(),
                            peProviders.getCodeCache(),
                            peProviders.getConstantReflection(),
                            new HostedTruffleConstantFieldProvider(peProviders.getConstantFieldProvider()),
                            peProviders.getForeignCalls(),
                            peProviders.getLowerer(),
                            peProviders.getReplacements(),
                            peProviders.getStampProvider(),
                            snippetReflection,
                            graalFeature.getHostedProviders().getWordTypes(),
                            graalFeature.getHostedProviders().getPlatformConfigurationProvider(),
                            graalFeature.getHostedProviders().getMetaAccessExtensionProvider(),
                            graalFeature.getHostedProviders().getLoopsDataProvider());
            newHostedProviders.setGraphBuilderPlugins(graphBuilderConfig.getPlugins());

            graalFeature.initializeRuntimeCompilationConfiguration(newHostedProviders, graphBuilderConfig, this::includeCallee, this::deoptimizeOnException);
            for (ResolvedJavaMethod method : partialEvaluator.getCompilationRootMethods()) {
                graalFeature.prepareMethodForRuntimeCompilation(method, config);
            }

            initializeMethodBlocklist(config.getMetaAccess());

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

            /*
             * This effectively initializes the Truffle fallback engine which does all the system
             * property option parsing to initialize the profilingEnabled flag correctly. A polyglot
             * fallback engine can not stay in the the image though, so we clear it right after. We
             * don't expect it to be used except for profiling enabled check.
             */
            this.profilingEnabled = Truffle.getRuntime().isProfilingEnabled();
            invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotEngineImpl", "resetFallbackEngine", Collections.emptyList());
        }

        firstAnalysisRun = true;
    }

    /**
     * Reachable libraries and receivers are instantiated during initialization.
     *
     * @see #initializeTruffleLibrariesAtBuildTime
     */
    private static void registerTruffleLibrariesAsInHeap(DuringAnalysisAccess access, Class<?> clazz) {
        assert access.isReachable(clazz) : clazz;
        assert LibraryFactory.class.isAssignableFrom(clazz) || LibraryExport.class.isAssignableFrom(clazz) : clazz;
        if (!Modifier.isAbstract(clazz.getModifiers())) {
            access.registerAsInHeap(clazz);
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
            if (original.hasNeverInlineDirective()) {
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
        StaticObjectSupport.duringAnalysis(access);

        if (firstAnalysisRun) {
            firstAnalysisRun = false;
            Object keep = invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotContextImpl", "resetSingleContextState", Collections.singleton(boolean.class), false);
            invokeStaticMethod("org.graalvm.polyglot.Engine$ImplHolder", "preInitializeEngine", Collections.emptyList());
            invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotContextImpl", "restoreSingleContextState", Collections.singleton(Object.class), keep);
            invokeStaticMethod("com.oracle.truffle.api.impl.ThreadLocalHandshake", "resetNativeImageState", Collections.emptyList());
            access.requireAnalysisIteration();
        }

        for (AnalysisType type : ((DuringAnalysisAccessImpl) access).getBigBang().getUniverse().getTypes()) {
            if (!access.isReachable(type.getJavaClass())) {
                continue;
            }
            initializeTruffleLibrariesAtBuildTime(type);
            initializeDynamicObjectLayouts(type);
        }
    }

    /**
     * Ensure that the necessary generated classes are properly initialized and registered, which
     * will eventually make them reachable.
     *
     * @see #registerTruffleLibrariesAsInHeap
     */
    private static void initializeTruffleLibrariesAtBuildTime(AnalysisType type) {
        if (type.isAnnotationPresent(GenerateLibrary.class)) {
            /* Eagerly resolve library type. */
            LibraryFactory.resolve(type.getJavaClass().asSubclass(Library.class));
        }
        if (type.getDeclaredAnnotationsByType(ExportLibrary.class).length != 0) {
            /* Eagerly resolve receiver type. */
            invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory$ResolvedDispatch", "lookup", Collections.singleton(Class.class), type.getJavaClass());
        }
    }

    private final Set<Class<?>> dynamicObjectClasses = new HashSet<>();

    @SuppressWarnings("deprecation")
    private void initializeDynamicObjectLayouts(AnalysisType type) {
        if (type.isInstantiated()) {
            Class<?> javaClass = type.getJavaClass();
            if (DynamicObject.class.isAssignableFrom(javaClass) && dynamicObjectClasses.add(javaClass)) {
                // Force layout initialization.
                com.oracle.truffle.api.object.Layout.newLayout().type(javaClass.asSubclass(DynamicObject.class)).build();
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
        } else if (implementationMethod.hasNeverInlineDirective()) {
            /* Ensure that NeverInline methods are also never inlined during Truffle compilation. */
            return false;
        } else if (uninterruptibleAnnotation != null && !uninterruptibleAnnotation.mayBeInlined()) {
            /* The semantics of Uninterruptible would get lost during partial evaluation. */
            return false;
        } else if (implementationMethod.getAnnotation(TruffleCallBoundary.class) != null) {
            return false;
        } else if (calleeNode != null && implementationMethods.size() > 4 && isBlocklisted(calleeNode.getTargetMethod())) {
            blocklistViolations.add(new GraalFeature.CallTreeNode(calleeNode.getTargetMethod(), calleeNode.getTargetMethod(), calleeNode.getParent(), calleeNode.getLevel(),
                            calleeNode.getSourceReference()));
            return false;
        } else if (isBlocklisted(implementationMethod)) {
            if (calleeNode != null) {
                blocklistViolations.add(calleeNode);
            }
            return false;

        } else if (warnMethods.contains(implementationMethod)) {
            if (calleeNode != null) {
                warnViolations.add(calleeNode);
            }
        }

        return true;
    }

    private boolean isBlocklisted(ResolvedJavaMethod method) {
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
        return blocklistMethods.contains(method);
    }

    @SuppressWarnings("deprecation")
    private boolean deoptimizeOnException(ResolvedJavaMethod method) {
        if (method == null) {
            return false;
        }
        CompilerDirectives.TruffleBoundary truffleBoundary = method.getAnnotation(CompilerDirectives.TruffleBoundary.class);
        return truffleBoundary != null && truffleBoundary.transferToInterpreterOnException();
    }

    private void initializeMethodBlocklist(MetaAccessProvider metaAccess) {
        blocklistMethod(metaAccess, Object.class, "clone");
        blocklistMethod(metaAccess, Object.class, "equals", Object.class);
        blocklistMethod(metaAccess, Object.class, "hashCode");
        blocklistMethod(metaAccess, Object.class, "toString");
        blocklistMethod(metaAccess, String.class, "valueOf", Object.class);
        blocklistMethod(metaAccess, String.class, "getBytes");
        blocklistMethod(metaAccess, Throwable.class, "initCause", Throwable.class);
        blocklistMethod(metaAccess, Throwable.class, "addSuppressed", Throwable.class);
        blocklistMethod(metaAccess, System.class, "getProperty", String.class);

        blocklistAllMethods(metaAccess, AssertionError.class);
        blocklistAllMethods(metaAccess, BigInteger.class);
        blocklistAllMethods(metaAccess, BigDecimal.class);
        blocklistAllMethods(metaAccess, Comparable.class);
        blocklistAllMethods(metaAccess, Comparator.class);
        blocklistAllMethods(metaAccess, Collection.class);
        blocklistAllMethods(metaAccess, List.class);
        blocklistAllMethods(metaAccess, Set.class);
        blocklistAllMethods(metaAccess, Map.class);
        blocklistAllMethods(metaAccess, Map.Entry.class);
        blocklistAllMethods(metaAccess, TreeMap.class);
        blocklistAllMethods(metaAccess, HashMap.class);
        blocklistAllMethods(metaAccess, ConcurrentHashMap.class);
        blocklistAllMethods(metaAccess, WeakHashMap.class);
        blocklistAllMethods(metaAccess, IdentityHashMap.class);
        blocklistAllMethods(metaAccess, Iterable.class);
        blocklistAllMethods(metaAccess, Iterator.class);
        blocklistAllMethods(metaAccess, ListIterator.class);
        blocklistAllMethods(metaAccess, ReentrantLock.class);

        allowlistMethod(metaAccess, BigInteger.class, "signum");
        allowlistMethod(metaAccess, ReentrantLock.class, "isLocked");
        allowlistMethod(metaAccess, ReentrantLock.class, "isHeldByCurrentThread");

        /* Methods with synchronization are currently not supported as deoptimization targets. */
        blocklistAllMethods(metaAccess, StringBuffer.class);
        blocklistAllMethods(metaAccess, Vector.class);
        blocklistAllMethods(metaAccess, Hashtable.class);

        /* Block list generic functional interfaces. */
        blocklistAllMethods(metaAccess, BiConsumer.class);
        blocklistAllMethods(metaAccess, BiFunction.class);
        blocklistAllMethods(metaAccess, BinaryOperator.class);
        blocklistAllMethods(metaAccess, BiPredicate.class);
        blocklistAllMethods(metaAccess, BooleanSupplier.class);
        blocklistAllMethods(metaAccess, Consumer.class);
        blocklistAllMethods(metaAccess, DoubleBinaryOperator.class);
        blocklistAllMethods(metaAccess, DoubleConsumer.class);
        blocklistAllMethods(metaAccess, DoubleFunction.class);
        blocklistAllMethods(metaAccess, DoublePredicate.class);
        blocklistAllMethods(metaAccess, DoubleSupplier.class);
        blocklistAllMethods(metaAccess, DoubleToIntFunction.class);
        blocklistAllMethods(metaAccess, DoubleToLongFunction.class);
        blocklistAllMethods(metaAccess, DoubleUnaryOperator.class);
        blocklistAllMethods(metaAccess, Function.class);
        blocklistAllMethods(metaAccess, IntBinaryOperator.class);
        blocklistAllMethods(metaAccess, IntConsumer.class);
        blocklistAllMethods(metaAccess, IntFunction.class);
        blocklistAllMethods(metaAccess, IntPredicate.class);
        blocklistAllMethods(metaAccess, IntSupplier.class);
        blocklistAllMethods(metaAccess, IntToDoubleFunction.class);
        blocklistAllMethods(metaAccess, IntToLongFunction.class);
        blocklistAllMethods(metaAccess, IntUnaryOperator.class);
        blocklistAllMethods(metaAccess, LongBinaryOperator.class);
        blocklistAllMethods(metaAccess, LongConsumer.class);
        blocklistAllMethods(metaAccess, LongFunction.class);
        blocklistAllMethods(metaAccess, LongPredicate.class);
        blocklistAllMethods(metaAccess, LongSupplier.class);
        blocklistAllMethods(metaAccess, LongToDoubleFunction.class);
        blocklistAllMethods(metaAccess, LongToIntFunction.class);
        blocklistAllMethods(metaAccess, LongUnaryOperator.class);
        blocklistAllMethods(metaAccess, ObjDoubleConsumer.class);
        blocklistAllMethods(metaAccess, ObjIntConsumer.class);
        blocklistAllMethods(metaAccess, ObjLongConsumer.class);
        blocklistAllMethods(metaAccess, Predicate.class);
        blocklistAllMethods(metaAccess, Supplier.class);
        blocklistAllMethods(metaAccess, ToDoubleBiFunction.class);
        blocklistAllMethods(metaAccess, ToDoubleFunction.class);
        blocklistAllMethods(metaAccess, ToIntBiFunction.class);
        blocklistAllMethods(metaAccess, ToIntFunction.class);
        blocklistAllMethods(metaAccess, ToLongBiFunction.class);
        blocklistAllMethods(metaAccess, ToLongFunction.class);
        blocklistAllMethods(metaAccess, UnaryOperator.class);

        /*
         * Core Substrate VM classes that very certainly should not be reachable for runtime
         * compilation. Warn when they get reachable to detect explosion of reachable methods.
         */
        warnAllMethods(metaAccess, JavaStackWalker.class);
        warnAllMethods(metaAccess, Deoptimizer.class);
        warnAllMethods(metaAccess, Heap.getHeap().getClass());
    }

    private void blocklistAllMethods(MetaAccessProvider metaAccess, Class<?> clazz) {
        for (Executable m : clazz.getDeclaredMethods()) {
            blocklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
        for (Executable m : clazz.getDeclaredConstructors()) {
            blocklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
    }

    private void blocklistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            blocklistMethods.add(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Removes a previously blocklisted method from the blocklist.
     */
    private void allowlistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            if (!blocklistMethods.remove(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)))) {
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

    private static int blocklistViolationComparator(GraalFeature.CallTreeNode n1, GraalFeature.CallTreeNode n2) {
        int result = n1.getTargetMethod().getQualifiedName().compareTo(n2.getTargetMethod().getQualifiedName());
        if (result == 0) {
            result = n1.getSourceReference().compareTo(n2.getSourceReference());
        }
        return result;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess config) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) config;

        boolean failBlockListViolations = Options.TruffleCheckBlockListMethods.getValue() || Options.TruffleCheckBlackListedMethods.getValue();
        boolean printBlockListViolations = GraalFeature.Options.PrintRuntimeCompileMethods.getValue() || failBlockListViolations;
        if (printBlockListViolations && blocklistViolations.size() > 0) {
            System.out.println();
            System.out.println("=== Found " + blocklistViolations.size() + " compilation blocklist violations ===");
            System.out.println();
            for (GraalFeature.CallTreeNode node : blocklistViolations) {
                System.out.println("Blocklisted method");
                System.out.println(node.getImplementationMethod().format("  %H.%n(%p)"));
                System.out.println("called from");
                for (GraalFeature.CallTreeNode cur = node; cur != null; cur = cur.getParent()) {
                    System.out.println("  " + cur.getSourceReference());
                }
            }
            if (failBlockListViolations) {
                throw VMError.shouldNotReachHere("Blocklisted methods are reachable for runtime compilation");
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
                    throw UserError.abort("More than one implementation of %s found. For performance reasons, Truffle languages must not provide new implementations, " +
                                    "and instead only use the single implementation provided by the Truffle runtime. To disable this check, add %s to the native-image command line. Found classes: %s",
                                    Frame.class.getTypeName(), SubstrateOptionsParser.commandArgument(Options.TruffleCheckFrameImplementation, "-"),
                                    implementations.stream().map(m -> m.toJavaName(true)).collect(Collectors.joining(", ")));
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

    private static final class StaticObjectSupport {
        private static final Class<?> GENERATOR_CLASS_LOADER_CLASS = loadClass("com.oracle.truffle.api.staticobject.GeneratorClassLoader");
        private static final Constructor<?> GENERATOR_CLASS_LOADER_CONSTRUCTOR = ReflectionUtil.lookupConstructor(GENERATOR_CLASS_LOADER_CLASS, Class.class);

        private static final Class<?> SHAPE_GENERATOR = loadClass("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
        private static final Method GET_SHAPE_GENERATOR = ReflectionUtil.lookupMethod(SHAPE_GENERATOR, "getShapeGenerator", TruffleLanguage.class, GENERATOR_CLASS_LOADER_CLASS, Class.class,
                        Class.class);

        private static final Method VALIDATE_CLASSES = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "validateClasses", Class.class, Class.class);

        private static final Map<Class<?>, ClassLoader> CLASS_LOADERS = new ConcurrentHashMap<>();
        private static BeforeAnalysisAccess beforeAnalysisAccess;

        private static final IdentityHashMap<Object, Object> registeredShapeGenerators = new IdentityHashMap<>();

        static void beforeAnalysis(BeforeAnalysisAccess access) {
            StaticObjectSupport.beforeAnalysisAccess = access;
        }

        static void registerInvocationPlugins(Plugins plugins, ParsingReason reason) {
            if (reason == ParsingReason.PointsToAnalysis) {
                InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), StaticShape.Builder.class);
                r.register3("build", InvocationPlugin.Receiver.class, Class.class, Class.class, new InvocationPlugin() {
                    @Override
                    public boolean inlineOnly() {
                        // Use the plugin only during parsing.
                        return true;
                    }

                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2) {
                        Class<?> superClass = getArgumentClass(b, targetMethod, 1, arg1);
                        Class<?> factoryInterface = getArgumentClass(b, targetMethod, 2, arg2);
                        generate(superClass, factoryInterface, beforeAnalysisAccess);
                        return false;
                    }
                });
            }
        }

        static void duringAnalysis(DuringAnalysisAccess access) {
            boolean requiresIteration = false;
            // We need to register as unsafe-accessed the primitive, object, and shape fields of
            // generated storage classes. However, these classes do not share a common super
            // type, and their fields are not annotated. Plus, the invocation plugin does not
            // intercept calls to `StaticShape.Builder.build()` that happen during the analysis,
            // for example because of context pre-initialization. Therefore, we inspect the
            // generator cache in ArrayBasedShapeGenerator, which contains references to all
            // generated storage classes.
            ConcurrentHashMap<?, ?> generatorCache = ReflectionUtil.readStaticField(SHAPE_GENERATOR, "generatorCache");
            for (Map.Entry<?, ?> entry : generatorCache.entrySet()) {
                Object shapeGenerator = entry.getValue();
                if (!registeredShapeGenerators.containsKey(shapeGenerator)) {
                    registeredShapeGenerators.put(shapeGenerator, shapeGenerator);
                    requiresIteration = true;
                    Class<?> storageClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedStorageClass", shapeGenerator);
                    Class<?> factoryClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedFactoryClass", shapeGenerator);
                    for (Constructor<?> c : factoryClass.getDeclaredConstructors()) {
                        RuntimeReflection.register(c);
                    }
                    for (String fieldName : new String[]{"primitive", "object", "shape"}) {
                        beforeAnalysisAccess.registerAsUnsafeAccessed(ReflectionUtil.lookupField(storageClass, fieldName));
                    }
                }
            }
            if (requiresIteration) {
                access.requireAnalysisIteration();
            }
        }

        private static Class<?> getArgumentClass(GraphBuilderContext b, ResolvedJavaMethod targetMethod, int parameterIndex, ValueNode arg) {
            SubstrateGraphBuilderPlugins.checkParameterUsage(arg.isConstant(), b, targetMethod, parameterIndex, "parameter is not a compile time constant");
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), b.getConstantReflection().asJavaType(arg.asJavaConstant()));
        }

        @SuppressWarnings("unused")
        private static void generate(Class<?> storageSuperClass, Class<?> factoryInterface, BeforeAnalysisAccess access) {
            try {
                VALIDATE_CLASSES.invoke(null, storageSuperClass, factoryInterface);
            } catch (ReflectiveOperationException e) {
                if (e instanceof InvocationTargetException && e.getCause() instanceof IllegalArgumentException) {
                    // Do not generate classes that will fail validation at run time.
                    registerReflectionAccessesForRuntimeValidation(storageSuperClass, factoryInterface);
                    return;
                }
                throw VMError.shouldNotReachHere(e);
            }

            // Checkstyle: stop
            ClassLoader generatorCL = getGeneratorClassLoader(factoryInterface);
            // Checkstyle: resume
            Object generator;
            try {
                GET_SHAPE_GENERATOR.invoke(null, null, generatorCL, storageSuperClass, factoryInterface);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        // Checkstyle: stop
        private static ClassLoader getGeneratorClassLoader(Class<?> factoryInterface) {
            ClassLoader cl = CLASS_LOADERS.get(factoryInterface);
            if (cl == null) {
                ClassLoader newCL;
                try {
                    newCL = (ClassLoader) GENERATOR_CLASS_LOADER_CONSTRUCTOR.newInstance(factoryInterface);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere(e);
                }
                cl = CLASS_LOADERS.putIfAbsent(factoryInterface, newCL);
                if (cl == null) {
                    cl = newCL;
                }
            }
            return cl;
        }
        // Checkstyle: resume

        // Checkstyle: stop
        private static Class<?> loadClass(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
        // Checkstyle: resume

        private static void registerReflectionAccessesForRuntimeValidation(Class<?> storageSuperClass, Class<?> factoryInterface) {
            for (Method m : factoryInterface.getMethods()) {
                RuntimeReflection.register(m);
            }
            for (Constructor<?> c : storageSuperClass.getDeclaredConstructors()) {
                RuntimeReflection.register(c);
            }
            for (Class<?> clazz = storageSuperClass; clazz != null; clazz = clazz.getSuperclass()) {
                for (Method m : clazz.getDeclaredMethods()) {
                    RuntimeReflection.register(m);
                }
            }
        }
    }
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_ArrayBasedShapeGenerator {

    public static final class OffsetTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
        private static final Class<?> SHAPE_GENERATOR;

        static {
            // Checkstyle: stop
            try {
                SHAPE_GENERATOR = Class.forName("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
            }
            // Checkstyle: resume
        }

        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue) {
            Class<?> generatedStorageClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedStorageClass", receiver);
            String name;
            switch (original.getName()) {
                case "byteArrayOffset":
                    name = "primitive";
                    break;
                case "objectArrayOffset":
                    name = "object";
                    break;
                case "shapeOffset":
                    name = "shape";
                    break;
                default:
                    throw VMError.shouldNotReachHere();
            }
            Field f = ReflectionUtil.lookupField(generatedStorageClass, name);
            assert metaAccess instanceof HostedMetaAccess;
            return ((HostedMetaAccess) metaAccess).lookupJavaField(f).getLocation();
        }
    }

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int byteArrayOffset;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int objectArrayOffset;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int shapeOffset;
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

@TargetClass(className = "com.oracle.truffle.polyglot.PolyglotContextThreadLocal", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_PolyglotContextThreadLocal {

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
