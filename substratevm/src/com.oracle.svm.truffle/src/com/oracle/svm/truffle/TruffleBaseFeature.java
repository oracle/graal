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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.graalvm.compiler.options.OptionType.User;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

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
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionStability;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.host.InjectImmutableFrameFieldsPhase;
import org.graalvm.compiler.truffle.compiler.host.TruffleHostEnvironment;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPlugins;
import org.graalvm.home.HomeFinder;
import org.graalvm.home.impl.DefaultHomeFinder;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalGraphObjectReplacer;
import com.oracle.svm.graal.hosted.SubstrateGraalCompilerSetup;
import com.oracle.svm.graal.hosted.SubstrateProviders;
import com.oracle.svm.graal.meta.SubstrateUniverseFactory;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.heap.PodSupport;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.dsl.InlineSupport;
import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryExport;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.provider.DefaultExportProvider;
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
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.polyglot.Engine;

/**
 * Base feature for using Truffle in the SVM. If only this feature is used (not included through
 * {@link TruffleFeature}'s dependency), then {@link TruffleRuntime} <b>must</b> be set to the
 * {@link DefaultTruffleRuntime}.
 */
public final class TruffleBaseFeature implements InternalFeature {

    private static final String NATIVE_IMAGE_FILELIST_FILE_NAME = "native-image-resources.filelist";

    @Override
    public String getURL() {
        return "https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.truffle/src/com/oracle/svm/truffle/TruffleBaseFeature.java";
    }

    @Override
    public String getDescription() {
        return "Provides support for Truffle languages";
    }

    public static Class<?> lookupClass(String className) {
        try {
            return Class.forName(className, false, TruffleBaseFeature.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new AssertionError(ex);
        }
    }

    public static class Options {

        @Option(help = "Automatically copy the necessary language resources to the resources/languages directory next to the produced image." +
                        "Language resources for each language are specified in the native-image-resources.filelist file located in the language home directory." +
                        "If there is no native-image-resources.filelist file in the language home directory or the file is empty, then no resources are copied.", type = User, stability = OptionStability.STABLE)//
        public static final HostedOptionKey<Boolean> CopyLanguageResources = new HostedOptionKey<>(true);

        @Option(help = "Check that context pre-initialization does not introduce absolute TruffleFiles into the image heap.")//
        public static final HostedOptionKey<Boolean> TruffleCheckPreinitializedFiles = new HostedOptionKey<>(true);
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

    private static final Field UNSAFE_FIELD_name = ReflectionUtil.lookupField(InlineSupport.InlinableField.class.getSuperclass(), "name");
    private static final Field UNSAFE_FIELD_declaringClass = ReflectionUtil.lookupField(InlineSupport.InlinableField.class.getSuperclass(), "declaringClass");

    private ClassLoader imageClassLoader;
    private AnalysisMetaAccess metaAccess;
    private GraalGraphObjectReplacer graalGraphObjectReplacer;
    private final Set<Class<?>> registeredClasses = new HashSet<>();
    private final Map<Class<?>, PossibleReplaceCandidatesSubtypeHandler> subtypeChecks = new HashMap<>();
    private boolean profilingEnabled;
    private boolean needsAllEncodings;

    private Field layoutInfoMapField;
    private Field layoutMapField;
    private Field libraryFactoryCacheField;

    private Map<String, Path> languageHomesToCopy;

    private Consumer<Field> markAsUnsafeAccessed;
    private final ConcurrentMap<Object, Boolean> processedInlinedFields = new ConcurrentHashMap<>();

    private static void initializeTruffleReflectively(ClassLoader imageClassLoader) {
        invokeStaticMethod("com.oracle.truffle.api.impl.Accessor", "getTVMCI", Collections.emptyList());
        invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "initializeNativeImageState",
                        Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "initializeNativeImageState",
                        Collections.singletonList(ClassLoader.class), imageClassLoader);
        invokeStaticMethod("com.oracle.truffle.api.impl.TruffleLocator", "initializeNativeImageState",
                        Collections.emptyList());
    }

    private static void initializeHomeFinder() {
        Set<String> languages = invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "collectLanguages", Collections.emptyList());
        Set<String> tools = invokeStaticMethod("com.oracle.truffle.polyglot.InstrumentCache", "collectInstruments", Collections.emptyList());
        invokeStaticMethod(DefaultHomeFinder.class.getName(), "setNativeImageLanguages", List.of(Set.class), languages);
        invokeStaticMethod(DefaultHomeFinder.class.getName(), "setNativeImageTools", List.of(Set.class), tools);
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

    /**
     * Register all fields accessed by a InlinedField for an instance field or a static field as
     * unsafe accessed, which is necessary for correctness of the static analysis.
     */
    private Object processInlinedField(Object obj) {
        if (obj instanceof InlineSupport.InlinableField && processedInlinedFields.putIfAbsent(obj, true) == null) {
            VMError.guarantee(markAsUnsafeAccessed != null, "New InlinedField found after static analysis");
            try {
                String name = (String) UNSAFE_FIELD_name.get(obj);
                Class<?> declaringClass = (Class<?>) UNSAFE_FIELD_declaringClass.get(obj);
                markAsUnsafeAccessed.accept(declaringClass.getDeclaredField(name));
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return obj;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        imageClassLoader = a.getApplicationClassLoader();

        TruffleRuntime runtime = Truffle.getRuntime();
        UserError.guarantee(runtime != null, "TruffleRuntime not available via Truffle.getRuntime()");
        UserError.guarantee(isSubstrateRuntime(runtime) || runtime instanceof DefaultTruffleRuntime,
                        "Unsupported TruffleRuntime %s (only SubstrateTruffleRuntime or DefaultTruffleRuntime allowed)",
                        runtime.getClass().getName());

        RuntimeClassInitialization.initializeAtBuildTime("com.oracle.graalvm.locator",
                        "Truffle classes are always initialized at build time");

        initializeTruffleReflectively(imageClassLoader);
        initializeHomeFinder();
        needsAllEncodings = invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "getNeedsAllEncodings",
                        Collections.emptyList());

        // reinitialize language cache
        invokeStaticMethod("com.oracle.truffle.api.library.LibraryFactory", "reinitializeNativeImageState",
                        Collections.emptyList());

        // pre-initialize TruffleLogger$LoggerCache.INSTANCE
        invokeStaticMethod("com.oracle.truffle.api.TruffleLogger$LoggerCache", "getInstance", Collections.emptyList());

        profilingEnabled = false;
    }

    @SuppressWarnings({"null", "unused"})
    private static boolean isSubstrateRuntime(TruffleRuntime runtime) {
        /*
         * We cannot directly depend on SubstrateTruffleRuntime from the base feature, as the
         * runtime might not be on the module-path.
         */
        return runtime.getClass().getName().equals("com.oracle.svm.truffle.api.SubstrateTruffleRuntime");
    }

    public void setProfilingEnabled(boolean profilingEnabled) {
        this.profilingEnabled = profilingEnabled;
    }

    @Override
    public void cleanup() {

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
        TruffleInvocationPlugins.register(providers.getLowerer().getTarget().arch, plugins.getInvocationPlugins(), providers.getReplacements());

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
        if (!ImageSingletons.contains(TruffleFeature.class) && isSubstrateRuntime(Truffle.getRuntime())) {
            VMError.shouldNotReachHere("TruffleFeature is required for SubstrateTruffleRuntime.");
        }
        access.registerObjectReplacer(this::processInlinedField);

        StaticObjectSupport.duringSetup(access);

        HomeFinder hf = HomeFinder.getInstance();
        if (Options.CopyLanguageResources.getValue()) {
            if (!(hf instanceof DefaultHomeFinder)) {
                VMError.shouldNotReachHere(String.format("HomeFinder %s cannot be used if CopyLanguageResources option of TruffleBaseFeature is enabled", hf.getClass().getName()));
            }
            Map<String, Path> languageHomes = hf.getLanguageHomes();
            Set<String> includedLanguages = invokeStaticMethod("com.oracle.truffle.polyglot.LanguageCache", "collectLanguages",
                            Collections.emptyList());
            for (Map.Entry<String, Path> languageHomeEntry : languageHomes.entrySet()) {
                String languageId = languageHomeEntry.getKey();
                if (includedLanguages.contains(languageId)) {
                    Path languageHome = languageHomeEntry.getValue();
                    Path fileListFile = languageHome.resolve(NATIVE_IMAGE_FILELIST_FILE_NAME);
                    try {
                        if (Files.exists(fileListFile) && Files.size(fileListFile) > 0) {
                            Path homeRelativePath = Paths.get("resources", "languages", languageId);
                            boolean relativeHomeSet = invokeStaticMethod(DefaultHomeFinder.class.getName(), "setRelativeLanguageHomeIfNotAlreadySet",
                                            Arrays.asList(new Class<?>[]{String.class, Path.class}), languageId,
                                            homeRelativePath);
                            if (relativeHomeSet) {
                                if (languageHomesToCopy == null) {
                                    languageHomesToCopy = new LinkedHashMap<>();
                                }
                                languageHomesToCopy.put(languageId, homeRelativePath);
                            }
                        }
                    } catch (IOException ioe) {
                        throw VMError.shouldNotReachHere(String.format("Cannot read the %s file for language %s.", NATIVE_IMAGE_FILELIST_FILE_NAME, languageId), ioe);
                    }
                }
            }
        }

        ImageSingletons.add(NodeClassSupport.class, new NodeClassSupport());
        if (!ImageSingletons.contains(SubstrateGraalCompilerSetup.class)) {
            ImageSingletons.add(SubstrateGraalCompilerSetup.class, new SubstrateGraalCompilerSetup());
        }

        DuringSetupAccessImpl config = (DuringSetupAccessImpl) access;
        metaAccess = config.getMetaAccess();

        layoutInfoMapField = config.findField("com.oracle.truffle.object.DefaultLayout$LayoutInfo", "LAYOUT_INFO_MAP");
        layoutMapField = config.findField("com.oracle.truffle.object.DefaultLayout", "LAYOUT_MAP");
        libraryFactoryCacheField = config.findField("com.oracle.truffle.api.library.LibraryFactory$ResolvedDispatch", "CACHE");
        if (Options.TruffleCheckPreinitializedFiles.getValue()) {
            access.registerObjectReplacer(new TruffleFileCheck(((FeatureImpl.DuringSetupAccessImpl) access).getHostVM().getClassInitializationSupport()));
        }
    }

    void setGraalGraphObjectReplacer(GraalGraphObjectReplacer graalGraphObjectReplacer) {
        assert this.graalGraphObjectReplacer == null;
        this.graalGraphObjectReplacer = graalGraphObjectReplacer;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (graalGraphObjectReplacer == null) {
            BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;
            SubstrateProviders substrateProviders = ImageSingletons.lookup(SubstrateGraalCompilerSetup.class).getSubstrateProviders(metaAccess);
            graalGraphObjectReplacer = new GraalGraphObjectReplacer(config.getUniverse(), substrateProviders, new SubstrateUniverseFactory());
            graalGraphObjectReplacer.setAnalysisAccess(config);
        }

        TruffleHostEnvironment.overrideLookup(new SubstrateTruffleHostEnvironmentLookup());

        StaticObjectSupport.beforeAnalysis(access);
        markAsUnsafeAccessed = access::registerAsUnsafeAccessed;

        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;

        config.registerHierarchyForReflectiveInstantiation(DefaultExportProvider.class);
        config.registerHierarchyForReflectiveInstantiation(TruffleInstrument.class);

        registerDynamicObjectFields(config);

        config.registerSubtypeReachabilityHandler(TruffleBaseFeature::registerTruffleLibrariesAsInHeap,
                        LibraryFactory.class);
        config.registerSubtypeReachabilityHandler(TruffleBaseFeature::registerTruffleLibrariesAsInHeap,
                        LibraryExport.class);

        if (needsAllEncodings) {
            ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "org/graalvm/shadowed/org/jcodings/tables/.*bin$");
        }
        Class<?> frameClass = config.findClassByName("com.oracle.truffle.api.impl.FrameWithoutBoxing");
        config.registerFieldValueTransformer(config.findField(frameClass, "ASSERTIONS_ENABLED"), new AssertionStatusFieldTransformer(frameClass));
    }

    private static class AssertionStatusFieldTransformer implements FieldValueTransformer {
        private final Class<?> clazz;

        AssertionStatusFieldTransformer(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            boolean assertionsEnabled = RuntimeAssertionsSupport.singleton().desiredAssertionStatus(clazz);
            return assertionsEnabled;
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        markAsUnsafeAccessed = null;
        invokeStaticMethod("com.oracle.truffle.polyglot.InternalResourceCache", "resetNativeImageState", List.of());
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
    private static void registerTruffleLibrariesAsInHeap(DuringAnalysisAccess a, Class<?> clazz) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        assert access.isReachable(clazz) : clazz;
        assert LibraryFactory.class.isAssignableFrom(clazz) || LibraryExport.class.isAssignableFrom(clazz) : clazz;
        if (!Modifier.isAbstract(clazz.getModifiers())) {
            access.registerAsInHeap(clazz, "Truffle library class registered by TruffleBaseFeature.");
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
                graalGraphObjectReplacer.createType(type);
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
    public void registerGraalPhases(Providers providers, SnippetReflectionProvider snippetReflection, Suites suites, boolean hosted) {
        /*
         * Please keep this code in sync with the HotSpot configuration in
         * TruffleCommunityCompilerConfiguration.
         */
        if (hosted) {
            InjectImmutableFrameFieldsPhase.install(suites.getHighTier(), HostedOptionValues.singleton());
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl config = (FeatureImpl.AfterCompilationAccessImpl) access;

        graalGraphObjectReplacer.updateSubstrateDataAfterCompilation(config.getUniverse(), config.getProviders());
        graalGraphObjectReplacer.registerImmutableObjects(access);
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
            if (AnnotationAccess.getAnnotation(u, DenyReplace.class) != null) {
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
        if (type.isAnnotationPresent(ExportLibrary.class) || type.isAnnotationPresent(ExportLibrary.Repeat.class)) {
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

    private static final class TruffleFileCheck implements Function<Object, Object> {
        private final ClassInitializationSupport classInitializationSupport;

        TruffleFileCheck(ClassInitializationSupport classInitializationSupport) {
            this.classInitializationSupport = classInitializationSupport;
        }

        @Override
        public Object apply(Object object) {
            if (object instanceof TruffleFile) {
                TruffleFile file = (TruffleFile) object;
                if (file.isAbsolute()) {
                    UserError.abort("Detected an absolute TruffleFile %s in the image heap. " +
                                    "Files with an absolute path created during the context pre-initialization may not be valid at the image execution time. " +
                                    "This check can be disabled using -H:-TruffleCheckPreinitializedFiles." +
                                    classInitializationSupport.objectInstantiationTraceMessage(object, "", culprit -> ""),
                                    file.getPath());
                }
            }
            return object;
        }
    }

    static final class StaticObjectSupport {
        private static final Method VALIDATE_CLASSES = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "validateClasses", Class.class, Class.class);

        static void duringSetup(DuringSetupAccess access) {
            StaticObjectArrayBasedSupport.duringSetup(access);
        }

        static void beforeAnalysis(BeforeAnalysisAccess access) {
            StaticObjectArrayBasedSupport.beforeAnalysis(access);
        }

        static void registerInvocationPlugins(Plugins plugins, ParsingReason reason) {
            if (reason.duringAnalysis()) {
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
            return OriginalClassProvider.getJavaClass(b.getConstantReflection().asJavaType(arg.asJavaConstant()));
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

        static final class StaticObjectArrayBasedSupport {
            private static final Method STORAGE_CLASS_NAME = ReflectionUtil.lookupMethod(StaticShape.Builder.class, "storageClassName");

            private static final Class<?> GENERATOR_CLASS_LOADERS_CLASS = loadClass("com.oracle.truffle.api.staticobject.GeneratorClassLoaders");
            private static final Constructor<?> GENERATOR_CLASS_LOADERS_CONSTRUCTOR = ReflectionUtil.lookupConstructor(GENERATOR_CLASS_LOADERS_CLASS, Class.class);

            private static final Class<?> ARRAY_BASED_STATIC_SHAPE = loadClass("com.oracle.truffle.api.staticobject.ArrayBasedStaticShape");
            private static final Class<?> ARRAY_BASED_SHAPE_GENERATOR = loadClass("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");
            private static final Method GET_ARRAY_BASED_SHAPE_GENERATOR = ReflectionUtil.lookupMethod(ARRAY_BASED_SHAPE_GENERATOR, "getShapeGenerator", TruffleLanguage.class,
                            GENERATOR_CLASS_LOADERS_CLASS, Class.class, Class.class, String.class);

            private static final Map<Class<?>, Object> GENERATOR_CLASS_LOADERS_MAP = new ConcurrentHashMap<>();
            private static final int ALIGNMENT_CORRECTION;
            private static BeforeAnalysisAccess beforeAnalysisAccess;

            private static final IdentityHashMap<Object, Object> registeredShapeGenerators = new IdentityHashMap<>();

            static volatile ConcurrentHashMap<Object, Object> replacements;
            private static final Class<?> FACTORY_CLASS_LOADER;

            static {
                // ArrayBasedShapeGenerator$ArrayBasedPropertyLayout makes sure that primitives are
                // stored in a byte[] at offsets that are long-aligned. When using the Static Object
                // Model during context pre-init, these offsets are computed using values that are
                // correct for the base JDK but might differ from those of Native Image. When this
                // happens, we allocate a larger byte[] and copy over the contents of the original
                // one at a base offset that keeps the other offsets long-aligned.
                int longIndexScale = ConfigurationValues.getObjectLayout().getArrayIndexScale(JavaKind.Long);
                int misalignment = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte) % longIndexScale;
                ALIGNMENT_CORRECTION = misalignment == 0 ? 0 : longIndexScale - misalignment;

                if (ALIGNMENT_CORRECTION != 0) {
                    // Can be an equality-based map because factory classes do not override
                    // `hashCode()` and `equals()`.
                    replacements = new ConcurrentHashMap<>();
                    // `ArrayBasedStaticShape.replacements` must be initialized only when there is
                    // an alignment correction, else some factories will not be replaced and will
                    // continue to register replacement candidates.
                    ReflectionUtil.writeStaticField(ARRAY_BASED_STATIC_SHAPE, "replacements", replacements);
                    FACTORY_CLASS_LOADER = loadClass("com.oracle.truffle.api.staticobject.GeneratorClassLoaders$FactoryClassLoader");
                } else {
                    replacements = null;
                    FACTORY_CLASS_LOADER = null;
                }
            }

            static void duringSetup(DuringSetupAccess access) {
                if (ALIGNMENT_CORRECTION != 0) {
                    access.registerObjectReplacer(obj -> {
                        if (!replacements.isEmpty()) {
                            boolean isByteArray = obj instanceof byte[];
                            if (isByteArray || FACTORY_CLASS_LOADER.isInstance(obj.getClass().getClassLoader())) {
                                Object replacement = replacements.get(obj);
                                if (replacement != null) {
                                    // The `replacements` map is populated by the generated
                                    // factories. The keys of this map are the primitive byte arrays
                                    // and the factory instances that must be replaced, and the
                                    // values are their replacements. Before a replacement is
                                    // computed, the value is identical to the key.
                                    if (replacement == obj) {
                                        // on first access: generate the replacement and register it
                                        if (isByteArray) {
                                            // primitive storage array
                                            byte[] oldArray = (byte[]) obj;
                                            byte[] newArray = new byte[oldArray.length + ALIGNMENT_CORRECTION];
                                            System.arraycopy(oldArray, 0, newArray, ALIGNMENT_CORRECTION, oldArray.length);
                                            replacement = newArray;
                                        } else {
                                            // factory instance
                                            Class<?> factoryClass = obj.getClass();
                                            StaticShape<?> shape = ReflectionUtil.readField(factoryClass, "shape", obj);
                                            int primitiveArraySize = ReflectionUtil.readField(factoryClass, "primitiveArraySize", obj);
                                            int objectArraySize = ReflectionUtil.readField(factoryClass, "objectArraySize", obj);

                                            Constructor<?> constructor = ReflectionUtil.lookupConstructor(factoryClass,
                                                            shape.getClass(),
                                                            int.class,
                                                            int.class,
                                                            boolean.class);

                                            Object newFactory = ReflectionUtil.newInstance(constructor,
                                                            shape,
                                                            primitiveArraySize + ALIGNMENT_CORRECTION,
                                                            objectArraySize,
                                                            // do not register patched factories,
                                                            // else we end up patching them again
                                                            false);

                                            replacement = newFactory;
                                        }
                                        if (!replacements.replace(obj, obj, replacement)) {
                                            // another thread already registered a replacement in
                                            // the meanwhile. Use that one.
                                            replacement = replacements.get(obj);
                                        }
                                    }
                                    return replacement;
                                }
                            }
                        }
                        return obj;
                    });
                }
            }

            static void beforeAnalysis(BeforeAnalysisAccess access) {
                beforeAnalysisAccess = access;

                access.registerFieldValueTransformer(ReflectionUtil.lookupField(ArrayBasedShapeGeneratorOffsetTransformer.SHAPE_GENERATOR, "byteArrayOffset"),
                                new ArrayBasedShapeGeneratorOffsetTransformer("primitive"));
                access.registerFieldValueTransformer(ReflectionUtil.lookupField(ArrayBasedShapeGeneratorOffsetTransformer.SHAPE_GENERATOR, "objectArrayOffset"),
                                new ArrayBasedShapeGeneratorOffsetTransformer("object"));
                access.registerFieldValueTransformer(ReflectionUtil.lookupField(ArrayBasedShapeGeneratorOffsetTransformer.SHAPE_GENERATOR, "shapeOffset"),
                                new ArrayBasedShapeGeneratorOffsetTransformer("shape"));

                access.registerFieldValueTransformer(ReflectionUtil.lookupField(StaticProperty.class, "offset"),
                                new StaticPropertyOffsetTransformer(ALIGNMENT_CORRECTION));
            }

            static void onBuildInvocation(Class<?> storageSuperClass, Class<?> factoryInterface) {
                try {
                    /*
                     * Trigger code generation also for those (storageSuperClass; factoryInterface)
                     * pairs that are not encountered during context pre-initialization. Since
                     * ArrayBasedShapeGenerator caches generated classes, there won't be code
                     * generation at run time.
                     */
                    Object gcls = getGeneratorClassLoaders(factoryInterface);
                    getGetShapeGenerator(gcls, storageSuperClass, factoryInterface);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere(e);
                }
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

            private static Object getGeneratorClassLoaders(Class<?> factoryInterface) throws ReflectiveOperationException {
                Object gcls = GENERATOR_CLASS_LOADERS_MAP.get(factoryInterface);
                if (gcls == null) {
                    Object newGCLs = GENERATOR_CLASS_LOADERS_CONSTRUCTOR.newInstance(factoryInterface);
                    gcls = GENERATOR_CLASS_LOADERS_MAP.putIfAbsent(factoryInterface, newGCLs);
                    if (gcls == null) {
                        gcls = newGCLs;
                    }
                }
                return gcls;
            }

            private static void getGetShapeGenerator(Object gcls, Class<?> storageSuperClass, Class<?> factoryInterface) throws ReflectiveOperationException {
                String storageClassName = (String) STORAGE_CLASS_NAME.invoke(null);
                GET_ARRAY_BASED_SHAPE_GENERATOR.invoke(null, null, gcls, storageSuperClass, factoryInterface, storageClassName);
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

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        if (Options.CopyLanguageResources.getValue()) {
            Path buildDir = access.getImagePath();
            if (buildDir != null) {
                Path parent = buildDir.getParent();
                if (parent != null) {
                    copyResources(parent);
                }
            }
        }
    }

    private void copyResources(Path buildDir) {
        Path resourcesDir = buildDir.resolve("resources");
        if (languageHomesToCopy != null) {
            Path languagesDir = resourcesDir.resolve("languages");
            if (Files.exists(languagesDir)) {
                try (Stream<Path> filesToDelete = Files.walk(languagesDir)) {
                    filesToDelete.sorted(Comparator.reverseOrder())
                                    .forEach(f -> {
                                        try {
                                            Files.deleteIfExists(f);
                                        } catch (IOException ioe) {
                                            throw VMError.shouldNotReachHere("Deletion of previous language resources directory failed.", ioe);
                                        }
                                    });
                } catch (IOException ioe) {
                    throw VMError.shouldNotReachHere("Deletion of previous language resources directory failed.", ioe);
                }
            }
            HomeFinder hf = HomeFinder.getInstance();
            Map<String, Path> languageHomes = hf.getLanguageHomes();
            languageHomesToCopy.forEach((s, path) -> {
                Path copyTo = buildDir.resolve(path);
                Path copyFrom = languageHomes.get(s);
                Path fileListFile = copyFrom.resolve(NATIVE_IMAGE_FILELIST_FILE_NAME);
                try {
                    Files.lines(fileListFile).forEach(fileName -> {
                        copy(s, copyFrom.resolve(fileName), copyTo.resolve(fileName));
                    });
                } catch (IOException ioe) {
                    throw VMError.shouldNotReachHere(String.format("Copying of language resources failed for language %s.", s), ioe);
                }
                BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.LANGUAGE_HOME, copyTo);
            });
        }
        try {
            if (Engine.copyResources(resourcesDir)) {
                BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.LANGUAGE_INTERNAL_RESOURCES, resourcesDir);
            }
        } catch (IOException ioe) {
            throw VMError.shouldNotReachHere("Copying of internal resources failed.", ioe);
        }
    }

    private static void copy(String language, Path source, Path dest) {
        try {
            if (!Files.isDirectory(source) || !Files.exists(dest)) {
                if (!Files.isDirectory(source)) {
                    Path dir = dest.getParent();
                    assert dir != null;
                    Files.createDirectories(dir);
                }
                Files.copy(source, dest, REPLACE_EXISTING);
            }
        } catch (IOException ioe) {
            throw VMError.shouldNotReachHere(String.format("Copying of language resources failed for language %s.", language), ioe);
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

@TargetClass(className = "com.oracle.truffle.api.staticobject.ArrayBasedStaticShape", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_ArrayBasedStaticShape {
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = MapCleaner.class, isFinal = true) //
    static ConcurrentHashMap<Object, Object> replacements;

    private static class MapCleaner implements FieldValueTransformerWithAvailability {
        @Override
        public ValueAvailability valueAvailability() {
            return ValueAvailability.AfterCompilation;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object transform(Object receiver, Object originalValue) {
            if (originalValue != null) {
                ConcurrentHashMap<Object, Object> originalMap = (ConcurrentHashMap<Object, Object>) originalValue;
                // Copied so that the object replacer can continue replacing references, even after
                // compilation.
                TruffleBaseFeature.StaticObjectSupport.StaticObjectArrayBasedSupport.replacements = new ConcurrentHashMap<>(originalMap);
                // Cleared so that factory instances that hold a reference to it do not leak
                // objects.
                originalMap.clear();
                // Return null so that new factory instances do not register replacements. See
                // `ArrayBasedStaticShape.create()`.
            }
            return null;
        }
    }
}

@TargetClass(className = "com.oracle.truffle.api.staticobject.StaticProperty", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_staticobject_StaticProperty {
    @Alias //
    native Class<?> getPropertyType();

    @Alias
    native void initOffset(int o);
}

final class StaticPropertyOffsetTransformer implements FieldValueTransformerWithAvailability {
    /*
     * We have to use reflection to access private members instead of aliasing them in the
     * substitution class since substitutions are present only at runtime
     */
    private static final Method GET_PROPERTY_TYPE = ReflectionUtil.lookupMethod(StaticProperty.class, "getPropertyType");
    private final int primitiveAlignmentCorrection;

    StaticPropertyOffsetTransformer(int primitiveAlignmentCorrection) {
        this.primitiveAlignmentCorrection = primitiveAlignmentCorrection;
    }

    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
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

        JavaKind javaKind;
        int baseOffset;
        int indexScale;
        int svmAlignmentCorrection;
        if (propertyType.isPrimitive()) {
            javaKind = JavaKind.Byte;
            baseOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
            indexScale = Unsafe.ARRAY_BYTE_INDEX_SCALE;
            svmAlignmentCorrection = primitiveAlignmentCorrection;
        } else {
            javaKind = JavaKind.Object;
            baseOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            indexScale = Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            svmAlignmentCorrection = 0;
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
        return svmArrayBaseOffset + svmAlignmentCorrection + svmArrayIndexScaleOffset * index;
    }

}

final class ArrayBasedShapeGeneratorOffsetTransformer implements FieldValueTransformerWithAvailability {
    static final Class<?> SHAPE_GENERATOR = TruffleBaseFeature.lookupClass("com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator");

    private final String storageClassFieldName;

    ArrayBasedShapeGeneratorOffsetTransformer(String storageClassFieldName) {
        this.storageClassFieldName = storageClassFieldName;
    }

    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        Class<?> generatedStorageClass = ReflectionUtil.readField(SHAPE_GENERATOR, "generatedStorageClass", receiver);
        Field field = ReflectionUtil.lookupField(generatedStorageClass, storageClassFieldName);
        int offset = ImageSingletons.lookup(ReflectionSubstitutionSupport.class).getFieldOffset(field, false);
        if (offset <= 0) {
            throw VMError.shouldNotReachHere("Field is not marked as accessed: " + field);
        }
        return Integer.valueOf(offset);
    }
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

@TargetClass(className = "com.oracle.truffle.polyglot.InternalResourceCache", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_InternalResourceCache {

    /*
     * The field is also reset explicitly in InternalResourceCache.resetFileSystemNativeImageState.
     * However, the explicit reset comes too late for the String-must-not-contain-the-home-directory
     * verification in DisallowedImageHeapObjectFeature, so we also do the implicit reset using a
     * substitution.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private static volatile Pair<Path, Boolean> cacheRoot;
}

@TargetClass(className = "com.oracle.truffle.polyglot.InternalResourceCache$ResettableCachedRoot", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_polyglot_InternalResourceCache_ResettableCachedRoot {

    /*
     * The field is also reset explicitly in InternalResourceCache.resetFileSystemNativeImageState.
     * However, the explicit reset comes too late for the String-must-not-contain-the-home-directory
     * verification in DisallowedImageHeapObjectFeature, so we also do the implicit reset using a
     * substitution.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private volatile Path resourceCacheRoot;
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
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetComputer.class, isFinal = true) //
    private long offset;

    private static class OffsetComputer implements FieldValueTransformerWithAvailability {
        @Override
        public ValueAvailability valueAvailability() {
            return ValueAvailability.AfterAnalysis;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Class<?> declaringClass = ReflectionUtil.readField(receiver.getClass(), "declaringClass", receiver);
            String name = ReflectionUtil.readField(receiver.getClass(), "name", receiver);
            Field field = ReflectionUtil.lookupField(declaringClass, name);
            int offset = ImageSingletons.lookup(ReflectionSubstitutionSupport.class).getFieldOffset(field, false);
            if (offset <= 0) {
                throw VMError.shouldNotReachHere("Field is not marked as accessed: " + field);
            }
            return Long.valueOf(offset);
        }
    }
}

@TargetClass(className = "com.oracle.truffle.api.dsl.InlineSupport$UnsafeField", onlyWith = TruffleBaseFeature.IsEnabled.class)
final class Target_com_oracle_truffle_api_dsl_InlineSupport_UnsafeField {

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = OffsetComputer.class, isFinal = true) //
    private long offset;

    /*
     * These fields are not needed at runtime in a native image. The offset is enough.
     */
    @Delete private Class<?> declaringClass;
    @Delete private String name;

    private static class OffsetComputer implements FieldValueTransformerWithAvailability {
        @Override
        public ValueAvailability valueAvailability() {
            return ValueAvailability.AfterAnalysis;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Class<?> declaringClass = ReflectionUtil.readField(InlinableField.class.getSuperclass(), "declaringClass", receiver);
            String name = ReflectionUtil.readField(InlinableField.class.getSuperclass(), "name", receiver);
            Field field = ReflectionUtil.lookupField(declaringClass, name);
            int offset = ImageSingletons.lookup(ReflectionSubstitutionSupport.class).getFieldOffset(field, false);
            if (offset == -1) {
                throw VMError.shouldNotReachHere("Field is not marked as accessed: " + field);
            }
            return Long.valueOf(offset);
        }
    }

}
