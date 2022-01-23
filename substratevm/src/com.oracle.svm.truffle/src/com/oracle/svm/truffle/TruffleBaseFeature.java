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
package com.oracle.svm.truffle;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.configure.ResourcesRegistry;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

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
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalObjectReplacer;
import com.oracle.svm.graal.hosted.GraalProviderObjectReplacements;
import com.oracle.svm.graal.hosted.RuntimeGraalSetup;
import com.oracle.svm.graal.hosted.SubstrateRuntimeGraalSetup;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
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
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.Profile;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import sun.misc.Unsafe;

/**
 * Base feature for using Truffle in the SVM. If only this feature is used (not included through
 * {@link TruffleFeature}'s dependency), then {@link TruffleRuntime} <b>must</b> be set to the
 * {@link DefaultTruffleRuntime}.
 */
public final class TruffleBaseFeature implements com.oracle.svm.core.graal.GraalFeature {

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

    private ClassLoader imageClassLoader;
    private AnalysisMetaAccess metaAccess;
    private GraalObjectReplacer graalObjectReplacer;
    private final Set<Class<?>> registeredClasses = new HashSet<>();
    private boolean profilingEnabled;
    private boolean needsAllEncodings;

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
        r.register0("isProfilingEnabled", new InvocationPlugin() {
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

        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) access;

        metaAccess = ((FeatureImpl.DuringSetupAccessImpl) access).getMetaAccess();
        if (!ImageSingletons.contains(RuntimeGraalSetup.class)) {
            ImageSingletons.add(RuntimeGraalSetup.class, new SubstrateRuntimeGraalSetup());
        }
        GraalProviderObjectReplacements providerReplacements = ImageSingletons.lookup(RuntimeGraalSetup.class)
                        .getProviderObjectReplacements(metaAccess);
        graalObjectReplacer = new GraalObjectReplacer(config.getUniverse(), metaAccess, providerReplacements);
        access.registerObjectReplacer(this::replaceNodeFieldAccessor);
    }

    @SuppressWarnings("deprecation")
    private Object replaceNodeFieldAccessor(Object source) {
        if (source instanceof com.oracle.truffle.api.nodes.NodeFieldAccessor ||
                        (source instanceof com.oracle.truffle.api.nodes.NodeFieldAccessor[] && ((com.oracle.truffle.api.nodes.NodeFieldAccessor[]) source).length > 0)) {
            throw VMError.shouldNotReachHere("Cannot have NodeFieldAccessor in image, they must be created lazily");

        } else if (source instanceof NodeClass && !(source instanceof SubstrateType)) {
            NodeClass nodeClass = (NodeClass) source;
            NodeClass replacement = graalObjectReplacer.createType(metaAccess.lookupJavaType(nodeClass.getType()));
            assert replacement != null;
            return replacement;
        }
        return source;
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
    public void duringAnalysis(DuringAnalysisAccess access) {
        StaticObjectSupport.duringAnalysis(access);

        for (Class<?> clazz : access.reachableSubtypes(com.oracle.truffle.api.nodes.Node.class)) {
            registerUnsafeAccess(access, clazz.asSubclass(com.oracle.truffle.api.nodes.Node.class));

            AnalysisType type = ((DuringAnalysisAccessImpl) access).getMetaAccess().lookupJavaType(clazz);
            if (type.isInstantiated()) {
                graalObjectReplacer.createType(type);
            }
        }

        for (AnalysisType type : ((DuringAnalysisAccessImpl) access).getBigBang().getUniverse().getTypes()) {
            if (!access.isReachable(type.getJavaClass())) {
                continue;
            }
            initializeTruffleLibrariesAtBuildTime(type);
            initializeDynamicObjectLayouts(type);
        }
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

        for (com.oracle.truffle.api.nodes.NodeFieldAccessor accessor : nodeClass.getFields()) {
            Field field;
            try {
                field = accessor.getDeclaringClass().getDeclaredField(accessor.getName());
            } catch (NoSuchFieldException ex) {
                throw shouldNotReachHere(ex);
            }

            if (accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.PARENT || accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD ||
                            accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN) {
                /*
                 * It's a field which represents an edge in the graph. Such fields are written with
                 * Unsafe in the NodeClass, e.g. when making changes in the graph.
                 */
                // TODO register unsafe accessed Truffle nodes in a separate partition?
                access.registerAsUnsafeAccessed(field);
            }

            if (accessor.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.DATA && com.oracle.truffle.api.nodes.NodeCloneable.class.isAssignableFrom(accessor.getType())) {
                /*
                 * It's a cloneable non-child data field of the node. Such fields are written with
                 * Unsafe in the NodeUtil.deepCopyImpl.
                 */
                access.registerAsUnsafeAccessed(field);
            }

            /* All other fields are only read with Unsafe. */
            access.registerAsAccessed(field);
        }

        access.requireAnalysisIteration();
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
            invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory$ResolvedDispatch", "lookup",
                            Collections.singleton(Class.class), type.getJavaClass());
        }
    }

    private final Set<Class<?>> dynamicObjectClasses = new HashSet<>();

    @SuppressWarnings("deprecation")
    private void initializeDynamicObjectLayouts(AnalysisType type) {
        if (type.isInstantiated()) {
            Class<?> javaClass = type.getJavaClass();
            if (DynamicObject.class.isAssignableFrom(javaClass) && dynamicObjectClasses.add(javaClass)) {
                // Force layout initialization.
                com.oracle.truffle.api.object.Layout.newLayout().type(javaClass.asSubclass(DynamicObject.class))
                                .build();
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

    private static final class StaticObjectSupport {
        private static final Method STORAGE_CLASS_NAME = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "storageClassName");

        private static final Class<?> GENERATOR_CLASS_LOADER_CLASS = loadClass(
                        "com.oracle.truffle.api.staticobject.GeneratorClassLoader");
        private static final Constructor<?> GENERATOR_CLASS_LOADER_CONSTRUCTOR = ReflectionUtil
                        .lookupConstructor(GENERATOR_CLASS_LOADER_CLASS, Class.class);

        private static final Class<?> SHAPE_GENERATOR = loadClass(
                        "com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
        private static final Method GET_SHAPE_GENERATOR = ReflectionUtil.lookupMethod(SHAPE_GENERATOR,
                        "getShapeGenerator", TruffleLanguage.class, GENERATOR_CLASS_LOADER_CLASS, Class.class, Class.class, String.class);

        private static final Method VALIDATE_CLASSES = ReflectionUtil.lookupMethod(StaticShape.Builder.class,
                        "validateClasses", Class.class, Class.class);

        private static final Map<Class<?>, ClassLoader> CLASS_LOADERS = new ConcurrentHashMap<>();
        private static BeforeAnalysisAccess beforeAnalysisAccess;

        private static final IdentityHashMap<Object, Object> registeredShapeGenerators = new IdentityHashMap<>();

        static void beforeAnalysis(BeforeAnalysisAccess access) {
            StaticObjectSupport.beforeAnalysisAccess = access;
        }

        static void registerInvocationPlugins(Plugins plugins, ParsingReason reason) {
            if (reason == ParsingReason.PointsToAnalysis) {
                InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(),
                                StaticShape.Builder.class);
                r.register3("build", InvocationPlugin.Receiver.class, Class.class, Class.class, new InvocationPlugin() {
                    @Override
                    public boolean inlineOnly() {
                        // Use the plugin only during parsing.
                        return true;
                    }

                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                                    InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2) {
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
            /*
             * We need to register as unsafe-accessed the primitive, object, and shape fields of
             * generated storage classes. However, these classes do not share a common super type,
             * and their fields are not annotated. Plus, the invocation plugin does not intercept
             * calls to `StaticShape.Builder.build()` that happen during the analysis, for example
             * because of context pre-initialization. Therefore, we inspect the generator cache in
             * ArrayBasedShapeGenerator, which contains references to all generated storage classes.
             */
            ConcurrentHashMap<?, ?> generatorCache = ReflectionUtil.readStaticField(SHAPE_GENERATOR, "generatorCache");
            for (Map.Entry<?, ?> entry : generatorCache.entrySet()) {
                Object shapeGenerator = entry.getValue();
                if (!registeredShapeGenerators.containsKey(shapeGenerator)) {
                    registeredShapeGenerators.put(shapeGenerator, shapeGenerator);
                    requiresIteration = true;
                    Class<?> storageClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedStorageClass",
                                    shapeGenerator);
                    Class<?> factoryClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedFactoryClass",
                                    shapeGenerator);
                    for (Constructor<?> c : factoryClass.getDeclaredConstructors()) {
                        RuntimeReflection.register(c);
                    }
                    for (String fieldName : new String[]{"primitive", "object", "shape"}) {
                        beforeAnalysisAccess
                                        .registerAsUnsafeAccessed(ReflectionUtil.lookupField(storageClass, fieldName));
                    }
                }
            }
            if (requiresIteration) {
                access.requireAnalysisIteration();
            }
        }

        private static Class<?> getArgumentClass(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                        int parameterIndex, ValueNode arg) {
            SubstrateGraphBuilderPlugins.checkParameterUsage(arg.isConstant(), b, targetMethod, parameterIndex,
                            "parameter is not a compile time constant");
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(),
                            b.getConstantReflection().asJavaType(arg.asJavaConstant()));
        }

        @SuppressWarnings("unused")
        private static void generate(Class<?> storageSuperClass, Class<?> factoryInterface,
                        BeforeAnalysisAccess access) {
            try {
                validateClasses(storageSuperClass, factoryInterface);
                ClassLoader generatorCL = getGeneratorClassLoader(factoryInterface);
                getGetShapeGenerator(generatorCL, storageSuperClass, factoryInterface);
            } catch (ReflectiveOperationException e) {
                if (e instanceof InvocationTargetException && e.getCause() instanceof IllegalArgumentException) {
                    Target_com_oracle_truffle_api_staticobject_StaticShape_Builder.ExceptionCache.set(storageSuperClass, factoryInterface, (IllegalArgumentException) e.getCause());
                } else {
                    throw VMError.shouldNotReachHere(e);
                }
            }
        }

        private static void validateClasses(Class<?> storageSuperClass, Class<?> factoryInterface) throws ReflectiveOperationException {
            VALIDATE_CLASSES.invoke(null, storageSuperClass, factoryInterface);
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
            GET_SHAPE_GENERATOR.invoke(null, null, generatorCL, storageSuperClass, factoryInterface, storageClassName);
        }

        private static Class<?> loadClass(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
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

@TargetClass(className = "com.oracle.truffle.api.staticobject.StaticProperty", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_StaticProperty {

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = Target_com_oracle_truffle_api_staticobject_StaticProperty.OffsetTransformer.class) //
    int offset;

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

        NodeClass nodeClass = (NodeClass) DynamicHub.fromClass(clazz).getMetaType();
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
