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
package com.oracle.svm.truffle;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.hosted.heap.PodSupport;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInlineOnlyInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import com.oracle.svm.util.DirectAnnotationAccess;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalObjectReplacer;
import com.oracle.svm.graal.hosted.GraalProviderObjectReplacements;
import com.oracle.svm.graal.hosted.RuntimeGraalSetup;
import com.oracle.svm.graal.hosted.SubstrateRuntimeGraalSetup;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.library.DefaultExportProvider;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryExport;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.DenyReplace;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.Profile;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base feature for using Truffle in the SVM. If only this feature is used (not included through
 * {@link TruffleFeature}'s dependency), then {@link TruffleRuntime} <b>must</b> be set to the
 * {@link DefaultTruffleRuntime}.
 */
public final class TruffleBaseFeature implements com.oracle.svm.core.graal.InternalFeature {

    @Override
    public String getURL() {
        return "https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.truffle/src/com/oracle/svm/truffle/TruffleBaseFeature.java";
    }

    @Override
    public String getDescription() {
        return "Provides base support for Truffle";
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(TruffleBaseFeature.class);
        }
    }

    public static final class IsCreateProcessDisabled implements BooleanSupplier {
        static boolean query() {
            try {
                Class<?> clazz = Class.forName("com.oracle.truffle.polyglot.PolyglotEngineImpl");
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

    private static final Method NODE_CLASS_getAccesssedFields = ReflectionUtil.lookupMethod(NodeClass.class, "getAccessedFields");

    private ClassLoader imageClassLoader;
    private AnalysisMetaAccess metaAccess;
    private GraalObjectReplacer graalObjectReplacer;
    private final Set<Class<?>> registeredClasses = new HashSet<>();
    private final Map<Class<?>, PossibleReplaceCandidatesSubtypeHandler> subtypeChecks = new HashMap<>();
    private boolean profilingEnabled;
    private boolean needsAllEncodings;

    private Field layoutInfoMapField;
    private Field layoutMapField;
    private Field libraryFactoryCacheField;

    private static void initializeTruffleReflectively(ClassLoader imageClassLoader) {
        invokeStaticMethod("com.oracle.truffle.api.impl.Accessor", "getTVMCI", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "initializeNativeImageState",
                        Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "initializeNativeImageState",
                        Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.api.impl.TruffleLocator", "initializeNativeImageState",
                        Collections.emptyList());
    }

    public static void removeTruffleLanguage(String mimeType) {
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "removeLanguageFromNativeImage",
                        Collections.singletonList(String.class), mimeType);
    }

    @SuppressWarnings("unchecked")
    static <T> T invokeStaticMethod(String className, String methodName, Collection<Class<?>> parameterTypes,
                    Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
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

        RuntimeClassInitialization.initializeAtBuildTime("com.oracle.graalvm.locator",
                        "Truffle classes are always initialized at build time");

        for (TruffleLanguage.Provider provider : ServiceLoader.load(TruffleLanguage.Provider.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(provider.getClass());
        }
        for (TruffleInstrument.Provider provider : ServiceLoader.load(TruffleInstrument.Provider.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(provider.getClass());
        }
        initializeTruffleReflectively(imageClassLoader);
        needsAllEncodings = invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "getNeedsAllEncodings",
                        Collections.emptyList());

        // reinitialize language cache
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "reinitializeNativeImageState",
                        Collections.emptyList());

        // pre-initialize TruffleLogger$LoggerCache.INSTANCE
        invokeStaticMethod("com.oracle.truffle.api.TruffleLogger$LoggerCache", "getInstance", Collections.emptyList());

        profilingEnabled = false;
    }

    public void setProfilingEnabled(boolean profilingEnabled) {
        this.profilingEnabled = profilingEnabled;
    }

    @Override
    public void cleanup() {
        /*
         * Clean the cached call target nodes to prevent them from keeping application classes alive
         */
        TruffleRuntime runtime = Truffle.getRuntime();
        if (!(runtime instanceof DefaultTruffleRuntime) && !(runtime instanceof SubstrateTruffleRuntime)) {
            throw VMError.shouldNotReachHere("Only SubstrateTruffleRuntime and DefaultTruffleRuntime supported");
        }

        // clean up the language cache
        invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotFastThreadLocals", "resetNativeImageState", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "resetNativeImageState",
                        Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "resetNativeImageState",
                        Collections.emptyList());
        invokeStaticMethod("org.graalvm.polyglot.Engine$ImplHolder", "resetPreInitializedEngine",
                        Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.impl.TruffleLocator", "resetNativeImageState",
                        Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.impl.ThreadLocalHandshake", "resetNativeImageState",
                        Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "resetNativeImageState",
                        Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.api.source.Source", "resetNativeImageState", Collections.emptyList());
        // clean up cached object layouts
        invokeStaticMethod("com.oracle.truffle.object.LayoutImpl", "resetNativeImageState", Collections.emptyList());
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection,
                    Plugins plugins, ParsingReason reason) {
        StaticObjectSupport.registerInvocationPlugins(plugins, reason);

        /*
         * We need to constant-fold Profile.isProfilingEnabled already during static analysis, so
         * that we get exact types for fields that store profiles.
         */
        Registration r = new Registration(plugins.getInvocationPlugins(), Profile.class);
        r.register(new RequiredInvocationPlugin("isProfilingEnabled") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(profilingEnabled));
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

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(TruffleFeature.class) && (Truffle.getRuntime() instanceof SubstrateTruffleRuntime)) {
            VMError.shouldNotReachHere("TruffleFeature is required for SubstrateTruffleRuntime.");
        }

        ImageSingletons.add(NodeClassSupport.class, new NodeClassSupport());
        if (!ImageSingletons.contains(RuntimeGraalSetup.class)) {
            ImageSingletons.add(RuntimeGraalSetup.class, new SubstrateRuntimeGraalSetup());
        }

        DuringSetupAccessImpl config = (DuringSetupAccessImpl) access;
        metaAccess = config.getMetaAccess();
        GraalProviderObjectReplacements providerReplacements = ImageSingletons.lookup(RuntimeGraalSetup.class)
                        .getProviderObjectReplacements(metaAccess);
        graalObjectReplacer = new GraalObjectReplacer(config.getUniverse(), metaAccess, providerReplacements);

        layoutInfoMapField = config.findField("com.oracle.truffle.object.DefaultLayout$LayoutInfo", "LAYOUT_INFO_MAP");
        layoutMapField = config.findField("com.oracle.truffle.object.DefaultLayout", "LAYOUT_MAP");
        libraryFactoryCacheField = config.findField("com.oracle.truffle.api.library.LibraryFactory$ResolvedDispatch", "CACHE");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        StaticObjectSupport.beforeAnalysis(access);

        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;

        config.registerHierarchyForReflectiveInstantiation(DefaultExportProvider.class);
        config.registerHierarchyForReflectiveInstantiation(TruffleInstrument.class);

        registerDynamicObjectFields(config);

        config.registerSubtypeReachabilityHandler(TruffleBaseFeature::registerTruffleLibrariesAsInHeap,
                        LibraryFactory.class);
        config.registerSubtypeReachabilityHandler(TruffleBaseFeature::registerTruffleLibrariesAsInHeap,
                        LibraryExport.class);

        if (needsAllEncodings) {
            ImageSingletons.lookup(ResourcesRegistry.class).addResources(ConfigurationCondition.alwaysTrue(), "org/graalvm/shadowed/org/jcodings/tables/.*bin$");
        }
    }

    public static void preInitializeEngine() {
        invokeStaticMethod("org.graalvm.polyglot.Engine$ImplHolder", "preInitializeEngine",
                        Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.api.impl.ThreadLocalHandshake", "resetNativeImageState",
                        Collections.emptyList());
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

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        StaticObjectSupport.duringAnalysis(a);

        for (Class<?> clazz : access.reachableSubtypes(com.oracle.truffle.api.nodes.Node.class)) {
            registerUnsafeAccess(access, clazz.asSubclass(com.oracle.truffle.api.nodes.Node.class));

            AnalysisType type = access.getMetaAccess().lookupJavaType(clazz);
            if (type.isInstantiated()) {
                graalObjectReplacer.createType(type);
            }
        }

        for (AnalysisType type : access.getBigBang().getUniverse().getTypes()) {
            if (!a.isReachable(type.getJavaClass())) {
                continue;
            }
            initializeTruffleLibrariesAtBuildTime(type);
            initializeDynamicObjectLayouts(type);
        }
        access.rescanRoot(layoutInfoMapField);
        access.rescanRoot(layoutMapField);
        access.rescanRoot(libraryFactoryCacheField);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl config = (FeatureImpl.AfterCompilationAccessImpl) access;

        graalObjectReplacer.updateSubstrateDataAfterCompilation(config.getUniverse(), config.getProviders().getConstantFieldProvider());
        graalObjectReplacer.registerImmutableObjects(access);
    }

    @SuppressWarnings("deprecation")
    private void registerUnsafeAccess(DuringAnalysisAccess access,
                    Class<? extends com.oracle.truffle.api.nodes.Node> clazz) {
        if (registeredClasses.contains(clazz)) {
            return;
        }
        registeredClasses.add(clazz);

        NodeClass nodeClass = NodeClass.get(clazz);
        NodeClassSupport.singleton().nodeClasses.put(clazz, nodeClass);

        Field[] fields;
        try {
            fields = (Field[]) NODE_CLASS_getAccesssedFields.invoke(nodeClass);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw shouldNotReachHere(e);
        }
        for (Field field : fields) {
            /*
             * All node fields are at least read with unsafe. All reference fields are also written
             * but only with exactly the same type, so no need to register as unsafe accessed. If it
             * is always the same type we are writing then the type flow analysis is not impacted
             * and it is therefore enough to just register these child fields as accessed instead of
             * unsafe accessed.
             */
            access.registerAsAccessed(field);

            if (field.getAnnotation(Child.class) != null) {
                /*
                 * Values of fields annotated with @Child may be replaced unsafely with any
                 * replaceable subtype.
                 *
                 * If a field is registered for unsafe access and there is more than one
                 * implementation for a value of a field, then any inlining could get prevented
                 * unnecessarily. As an optimization we try to avoid registering for unsafe access
                 * whenever possible to leverage the result of the type flow analysis.
                 */
                Class<?> type = field.getType();
                if (Modifier.isFinal(type.getModifiers())) {
                    // optimization: there is only one possible value for fields with final types
                    // -> registering as as accessed is enough
                } else if (type == Node.class || type == NodeInterface.class) {
                    // optimization: there are always more than one node subclasses
                    // -> we need to register as unsafe accessed eagerly
                    access.registerAsUnsafeAccessed(field);
                } else {
                    /*
                     * For any other type we count the non abstract subclasses that are not
                     * annotated with @DenyReplace. If we see more than one of such types we need to
                     * register the field as unsafely accessed, as replace might introduce types
                     * there, that the type flow analysis would not see. But if it is just a single
                     * or no subtype then registerAsAccessed is again enough.
                     */
                    PossibleReplaceCandidatesSubtypeHandler detector = subtypeChecks.get(type);
                    if (detector == null) {
                        detector = new PossibleReplaceCandidatesSubtypeHandler(type);
                        access.registerSubtypeReachabilityHandler(detector, type);
                        subtypeChecks.put(type, detector);
                    }
                    detector.addField(access, field);
                }
            }
        }

        access.requireAnalysisIteration();
    }

    /**
     * Counts the number of subclasses that could be possible candidates for a {@link Child} field
     * through replaces. Registers all added fields as unsafe accessed in case more then one
     * replaceable subtype is used.
     */
    static class PossibleReplaceCandidatesSubtypeHandler implements BiConsumer<DuringAnalysisAccess, Class<?>> {

        /**
         * The fields are added serially, from the duringAnalysis phase which is run when the
         * analysis reaches a local fix point, so no need for synchronization.
         */
        List<Field> fields = new ArrayList<>();
        final Class<?> fieldType;
        /**
         * The candidates are counted from a reachability handler, which is run in parallel with the
         * analysis.
         */
        final AtomicInteger candidateCount = new AtomicInteger(0);

        PossibleReplaceCandidatesSubtypeHandler(Class<?> fieldType) {
            this.fieldType = fieldType;
        }

        void addField(DuringAnalysisAccess access, Field field) {
            assert field.getType() == fieldType;
            if (candidateCount.get() > 1) {
                /*
                 * Limit already reached no need to remember fields anymore we can directly register
                 * them as unsafe accessed.
                 */
                access.registerAsUnsafeAccessed(field);
            } else {
                fields.add(field);
            }
        }

        @Override
        public void accept(DuringAnalysisAccess t, Class<?> u) {
            /*
             * Never replaceable classes do not count as candidates. They are checked to never be
             * used for replacing.
             */
            if (DirectAnnotationAccess.getAnnotation(u, DenyReplace.class) != null) {
                return;
            }

            /*
             * Abstract classes do not account to the number of possible child field candidates.
             * They cannot be instantiated so are also not possible values for a child field.
             */
            if (Modifier.isAbstract(u.getModifiers())) {
                return;
            }

            /* Limit reached, register the fields and clear the list. */
            if (candidateCount.incrementAndGet() == 2) {
                for (Field field : fields) {
                    t.registerAsUnsafeAccessed(field);
                }
                fields = null;
            }
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
            LibraryFactory<? extends Library> factory = LibraryFactory.resolve(type.getJavaClass().asSubclass(Library.class));
            /* Trigger computation of uncachedDispatch. */
            factory.getUncached();
        }
        if (type.getDeclaredAnnotationsByType(ExportLibrary.class).length != 0) {
            /* Eagerly resolve receiver type. */
            invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory$ResolvedDispatch", "lookup",
                            Collections.singleton(Class.class), type.getJavaClass());
        }
    }

    private final Set<Class<?>> dynamicObjectClasses = new HashSet<>();

    private void initializeDynamicObjectLayouts(AnalysisType type) {
        if (type.isInstantiated()) {
            Class<?> javaClass = type.getJavaClass();
            if (DynamicObject.class.isAssignableFrom(javaClass) && dynamicObjectClasses.add(javaClass)) {
                initializeDynamicObjectLayoutImpl(javaClass);
            }
        }
    }

    private static void initializeDynamicObjectLayoutImpl(Class<?> javaClass) {
        // Initialize DynamicObject layout info for every instantiated DynamicObject subclass.
        invokeStaticMethod("com.oracle.truffle.object.LayoutImpl", "initializeDynamicObjectLayout", Collections.singleton(Class.class), javaClass);
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

    private static final class StaticObjectSupport {
        private static final Method VALIDATE_CLASSES = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "validateClasses", Class.class, Class.class);

        static void beforeAnalysis(BeforeAnalysisAccess access) {
            StaticObjectArrayBasedSupport.beforeAnalysis(access);
        }

        static void registerInvocationPlugins(Plugins plugins, ParsingReason reason) {
            if (reason == ParsingReason.PointsToAnalysis) {
                InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), StaticShape.Builder.class);
                r.register(new RequiredInlineOnlyInvocationPlugin("build", InvocationPlugin.Receiver.class, Class.class, Class.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                        Class<?> storageSuperClass = getArgumentClass(b, targetMethod, 1, arg1);
                        Class<?> factoryInterface = getArgumentClass(b, targetMethod, 2, arg2);
                        if (validateClasses(storageSuperClass, factoryInterface)) {
                            StaticObjectArrayBasedSupport.onBuildInvocation(storageSuperClass, factoryInterface);
                            StaticObjectPodBasedSupport.onBuildInvocation(storageSuperClass, factoryInterface);
                        }
                        return false;
                    }
                });
            }
        }

        static void duringAnalysis(DuringAnalysisAccess access) {
            StaticObjectArrayBasedSupport.duringAnalysis(access);
        }

        private static Class<?> getArgumentClass(GraphBuilderContext b, ResolvedJavaMethod targetMethod, int parameterIndex, ValueNode arg) {
            SubstrateGraphBuilderPlugins.checkParameterUsage(arg.isConstant(), b, targetMethod, parameterIndex, "parameter is not a compile time constant");
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), b.getConstantReflection().asJavaType(arg.asJavaConstant()));
        }

        private static boolean validateClasses(Class<?> storageSuperClass, Class<?> factoryInterface) {
            try {
                VALIDATE_CLASSES.invoke(null, storageSuperClass, factoryInterface);
                return true;
            } catch (ReflectiveOperationException e) {
                if (e instanceof InvocationTargetException && e.getCause() instanceof IllegalArgumentException) {
                    Target_com_oracle_truffle_api_staticobject_StaticShape_Builder.ExceptionCache.set(storageSuperClass, factoryInterface, (IllegalArgumentException) e.getCause());
                    return false;
                } else {
                    throw VMError.shouldNotReachHere(e);
                }
            }
        }

        private static final class StaticObjectArrayBasedSupport {
            private static final Method STORAGE_CLASS_NAME = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "storageClassName");

            private static final Class<?> GENERATOR_CLASS_LOADER_CLASS = loadClass("com.oracle.truffle.api.staticobject.GeneratorClassLoader");
            private static final Constructor<?> GENERATOR_CLASS_LOADER_CONSTRUCTOR = ReflectionUtil.lookupConstructor(GENERATOR_CLASS_LOADER_CLASS, Class.class);

            private static final Class<?> ARRAY_BASED_SHAPE_GENERATOR = loadClass("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
            private static final Method GET_ARRAY_BASED_SHAPE_GENERATOR = ReflectionUtil.lookupMethod(ARRAY_BASED_SHAPE_GENERATOR, "getShapeGenerator", TruffleLanguage.class,
                            GENERATOR_CLASS_LOADER_CLASS, Class.class, Class.class, String.class);

            private static final Map<Class<?>, ClassLoader> CLASS_LOADERS = new ConcurrentHashMap<>();
            private static BeforeAnalysisAccess beforeAnalysisAccess;

            private static final IdentityHashMap<Object, Object> registeredShapeGenerators = new IdentityHashMap<>();

            static void beforeAnalysis(BeforeAnalysisAccess access) {
                beforeAnalysisAccess = access;
            }

            static void onBuildInvocation(Class<?> storageSuperClass, Class<?> factoryInterface) {
                generateArrayBasedStorage(storageSuperClass, factoryInterface, beforeAnalysisAccess);
            }

            static void duringAnalysis(DuringAnalysisAccess access) {
                boolean requiresIteration = false;
                /*
                 * We need to register as unsafe-accessed the primitive, object, and shape fields of
                 * generated storage classes. However, these classes do not share a common super
                 * type, and their fields are not annotated. Plus, the invocation plugin does not
                 * intercept calls to `StaticShape.Builder.build()` that happen during the analysis,
                 * for example because of context pre-initialization. Therefore, we inspect the
                 * generator cache in ArrayBasedShapeGenerator, which contains references to all
                 * generated storage classes.
                 */
                ConcurrentHashMap<?, ?> generatorCache = ReflectionUtil.readStaticField(ARRAY_BASED_SHAPE_GENERATOR, "generatorCache");
                for (Map.Entry<?, ?> entry : generatorCache.entrySet()) {
                    Object shapeGenerator = entry.getValue();
                    if (!registeredShapeGenerators.containsKey(shapeGenerator)) {
                        registeredShapeGenerators.put(shapeGenerator, shapeGenerator);
                        requiresIteration = true;
                        Class<?> storageClass = ReflectionUtil.readField(ARRAY_BASED_SHAPE_GENERATOR, "generatedStorageClass", shapeGenerator);
                        Class<?> factoryClass = ReflectionUtil.readField(ARRAY_BASED_SHAPE_GENERATOR, "generatedFactoryClass", shapeGenerator);
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

            @SuppressWarnings("unused")
            private static void generateArrayBasedStorage(Class<?> storageSuperClass, Class<?> factoryInterface, BeforeAnalysisAccess access) {
                try {
                    ClassLoader generatorCL = getGeneratorClassLoader(factoryInterface);
                    getGetShapeGenerator(generatorCL, storageSuperClass, factoryInterface);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }

            private static ClassLoader getGeneratorClassLoader(Class<?> factoryInterface) throws ReflectiveOperationException {
                ClassLoader cl = CLASS_LOADERS.get(factoryInterface);
                if (cl == null) {
                    ClassLoader newCL = (ClassLoader) GENERATOR_CLASS_LOADER_CONSTRUCTOR.newInstance(factoryInterface);
                    cl = CLASS_LOADERS.putIfAbsent(factoryInterface, newCL);
                    if (cl == null) {
                        cl = newCL;
                    }
                }
                return cl;
            }

            /*
             * Triggers shape generation.
             */
            private static void getGetShapeGenerator(ClassLoader generatorCL, Class<?> storageSuperClass, Class<?> factoryInterface) throws ReflectiveOperationException {
                String storageClassName = (String) STORAGE_CLASS_NAME.invoke(null);
                GET_ARRAY_BASED_SHAPE_GENERATOR.invoke(null, null, generatorCL, storageSuperClass, factoryInterface, storageClassName);
            }

            private static Class<?> loadClass(String name) {
                try {
                    return Class.forName(name);
                } catch (ClassNotFoundException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
        }

        private static final class StaticObjectPodBasedSupport {
            static void onBuildInvocation(Class<?> storageSuperClass, Class<?> factoryInterface) {
                PodSupport.singleton().registerSuperclass(storageSuperClass, factoryInterface);
            }
        }
    }
}

/*
 * Cache validation exceptions triggered at build time and throw them at run time.
 */
@TargetClass(className = "com.oracle.truffle.api.staticobject.StaticShape", innerClass = "Builder", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_StaticShape_Builder {
    static final class ExceptionCache {
        private static final ConcurrentHashMap<Pair<Class<?>, Class<?>>, IllegalArgumentException> cache = new ConcurrentHashMap<>();

        static IllegalArgumentException get(Class<?> storageSuperClass, Class<?> storageFactoryInterface) {
            return cache.get(Pair.create(storageSuperClass, storageFactoryInterface));
        }

        static void set(Class<?> storageSuperClass, Class<?> storageFactoryInterface, IllegalArgumentException e) {
            cache.putIfAbsent(Pair.create(storageSuperClass, storageFactoryInterface), e);
        }
    }

    @Substitute
    static void validateClasses(Class<?> storageSuperClass, Class<?> storageFactoryInterface) {
        IllegalArgumentException exception = ExceptionCache.get(storageSuperClass, storageFactoryInterface);
        if (exception != null) {
            // To have both the run-time and the build-time stack traces, throw a new exception
            // caused by the build-time exception
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.PodBasedShapeGenerator", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_PodBasedShapeGenerator<T> {
    @Alias //
    Class<?> storageSuperClass;

    @Alias //
    Class<T> storageFactoryInterface;

    @Substitute
    @SuppressWarnings("unchecked")
    Target_com_oracle_truffle_api_staticobject_PodBasedStaticShape<T> generateShape(Target_com_oracle_truffle_api_staticobject_PodBasedStaticShape<T> parentShape,
                    Map<String, Target_com_oracle_truffle_api_staticobject_StaticProperty> staticProperties, boolean safetyChecks) {
        Pod.Builder<T> builder;
        if (parentShape == null) {
            builder = Pod.Builder.createExtending(storageSuperClass, storageFactoryInterface);
        } else {
            Object pod = parentShape.pod;
            if (pod instanceof Pod) {
                builder = Pod.Builder.createExtending((Pod<T>) pod);
            } else {
                throw new IllegalArgumentException("Expected pod of type: '" + Pod.class.getName() + "'; got: " + pod);
            }
        }
        ArrayList<Pair<Target_com_oracle_truffle_api_staticobject_StaticProperty, Pod.Field>> propertyFields = new ArrayList<>(staticProperties.size());
        for (var staticProperty : staticProperties.values()) {
            Pod.Field f = builder.addField(staticProperty.getPropertyType());
            propertyFields.add(Pair.create(staticProperty, f));
        }
        Pod<T> pod = builder.build();
        for (var entry : propertyFields) {
            entry.getLeft().initOffset(entry.getRight().getOffset());
        }
        return Target_com_oracle_truffle_api_staticobject_PodBasedStaticShape.create(storageSuperClass, pod.getFactory(), safetyChecks, pod);
    }
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.PodBasedStaticShape", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_PodBasedStaticShape<T> {
    @Alias //
    Object pod;

    @Alias
    static native <T> Target_com_oracle_truffle_api_staticobject_PodBasedStaticShape<T> create(Class<?> generatedStorageClass, T factory, boolean safetyChecks, Object pod);
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.StaticProperty", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_StaticProperty {

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_com_oracle_truffle_api_staticobject_StaticProperty.OffsetTransformer.class) //
    int offset;

    @Alias //
    native Class<?> getPropertyType();

    @Alias
    native void initOffset(int o);

    public static final class OffsetTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
        /*
         * We have to use reflection to access private members instead of aliasing them in the
         * substitution class since substitutions are present only at runtime
         */

        private static final Method GET_PROPERTY_TYPE;
        static {
            GET_PROPERTY_TYPE = ReflectionUtil.lookupMethod(StaticProperty.class, "getPropertyType");
        }

        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated,
                        Object receiver, Object originalValue) {
            int offset = (int) originalValue;
            if (offset == 0) {
                /*
                 * The offset is not yet initialized, probably because no shape was built for the
                 * receiver static property
                 */
                return offset;
            }

            StaticProperty receiverStaticProperty = (StaticProperty) receiver;

            Class<?> propertyType;
            try {
                propertyType = (Class<?>) GET_PROPERTY_TYPE.invoke(receiverStaticProperty);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }

            int baseOffset;
            int indexScale;
            JavaKind javaKind;
            if (propertyType.isPrimitive()) {
                javaKind = JavaKind.Byte;
                baseOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_BYTE_INDEX_SCALE;
            } else {
                javaKind = JavaKind.Object;
                baseOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            }

            assert offset >= baseOffset && (offset - baseOffset) % indexScale == 0;

            /*
             * Reverse the offset computation to find the index
             */
            int index = (offset - baseOffset) / indexScale;

            /*
             * Find SVM array base offset and array index scale for this JavaKind
             */
            int svmArrayBaseOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(javaKind);
            int svmArrayIndexScaleOffset = ConfigurationValues.getObjectLayout().getArrayIndexScale(javaKind);

            /*
             * Redo the offset computation with the SVM array base offset and array index scale
             */
            return svmArrayBaseOffset + svmArrayIndexScaleOffset * index;
        }
    }
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_ArrayBasedShapeGenerator {

    public static final class OffsetTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
        private static final Class<?> SHAPE_GENERATOR;

        static {
            try {
                SHAPE_GENERATOR = Class.forName("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.AfterAnalysis;
        }

        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated,
                        Object receiver, Object originalValue) {
            Class<?> generatedStorageClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedStorageClass",
                            receiver);
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

        @Override
        public Class<?>[] types() {
            return new Class<?>[]{int.class};
        }
    }

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int byteArrayOffset;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int objectArrayOffset;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetTransformer.class) //
    int shapeOffset;
}

/*
 * If allowProcess() is disabled at build time, then we ensure that ProcessBuilder is not reachable.
 * The main purpose of this is to test that ProcessBuilder is not part of the image when building
 * language images with allowProcess() disabled, which we interpret as
 * "forbid shelling out to external processes" (GR-14041).
 */
@Delete
@TargetClass(className = "java.lang.ProcessBuilder", onlyWith = {TruffleBaseFeature.IsEnabled.class,
                TruffleBaseFeature.IsCreateProcessDisabled.class})
final class Target_java_lang_ProcessBuilder {
}

/*
 * Ensure ProcessBuilder is not reachable through the enclosing class of Redirect.
 */
@Delete
@TargetClass(className = "java.lang.ProcessBuilder", innerClass = "Redirect", onlyWith = {TruffleBaseFeature.IsEnabled.class,
                TruffleBaseFeature.IsCreateProcessDisabled.class})
final class Target_java_lang_ProcessBuilder_Redirect {
}

/*
 * If allowProcess() is disabled at build time, then we ensure ObjdumpDisassemblerProvider does not
 * try to invoke the nonexistent ProcessBuilder.
 */
@TargetClass(className = "org.graalvm.compiler.code.ObjdumpDisassemblerProvider", onlyWith = {
                TruffleBaseFeature.IsEnabled.class, TruffleBaseFeature.IsCreateProcessDisabled.class})
final class Target_org_graalvm_compiler_code_ObjdumpDisassemblerProvider {

    @Substitute
    @SuppressWarnings("unused")
    static Process createProcess(String[] cmd) {
        return null;
    }
}

@TargetClass(className = "com.oracle.truffle.polyglot.LanguageCache", onlyWith = TruffleBaseFeature.IsEnabled.class)
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

@TargetClass(className = "com.oracle.truffle.object.CoreLocations$DynamicObjectFieldLocation", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_object_CoreLocations_DynamicObjectFieldLocation {
    @Alias @RecomputeFieldValue(kind = Kind.AtomicFieldUpdaterOffset) //
    private long offset;
}

@TargetClass(className = "com.oracle.truffle.object.CoreLocations$DynamicLongFieldLocation", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_object_CoreLocations_DynamicLongFieldLocation {
    @Alias @RecomputeFieldValue(kind = Kind.AtomicFieldUpdaterOffset) //
    private long offset;
}

@TargetClass(className = "com.oracle.truffle.api.nodes.NodeClass", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_nodes_NodeClass {
    @Substitute
    public static NodeClass get(Class<?> clazz) {
        CompilerAsserts.neverPartOfCompilation();

        NodeClass nodeClass = NodeClassSupport.singleton().nodeClasses.get(clazz);
        if (nodeClass == null) {
            throw shouldNotReachHere("Unknown node class: " + clazz.getName());
        }
        return nodeClass;
    }
}

@TargetClass(className = "com.oracle.truffle.api.nodes.Node", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_nodes_Node {
    @AnnotateOriginal
    @NeverInline("")
    public native void adoptChildren();
}

@TargetClass(className = "com.oracle.truffle.api.nodes.NodeClassImpl", innerClass = "NodeFieldData", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_nodes_NodeClassImpl_NodeFieldData {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetComputer.class) //
    private long offset;

    private static class OffsetComputer implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.AfterAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Class<?> declaringClass = ReflectionUtil.readField(receiver.getClass(), "declaringClass", receiver);
            String name = ReflectionUtil.readField(receiver.getClass(), "name", receiver);
            Field field = ReflectionUtil.lookupField(declaringClass, name);
            return (long) metaAccess.lookupJavaField(field).getOffset();
        }

        @Override
        public Class<?>[] types() {
            return new Class<?>[]{long.class};
        }
    }
}
