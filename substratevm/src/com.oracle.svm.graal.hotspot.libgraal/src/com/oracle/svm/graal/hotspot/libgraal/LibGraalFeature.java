/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.libgraal.BuildTime;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.serviceprovider.LibGraalService;
import jdk.graal.nativeimage.LibGraalFeatureComponent;
import jdk.graal.nativeimage.LibGraalLoader;
import jdk.graal.nativeimage.hosted.GlobalData;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotModifiers;

/**
 * This feature builds the libgraal shared library (e.g., libjvmcicompiler.so on linux).
 * <p>
 * With use of {@code -Djdk.graal.internal.libgraal.javahome=path}, the Graal and JVMCI classes from
 * which libgraal is built can be from a "guest" JDK that may be different from the JDK on which
 * Native Image is running.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class LibGraalFeature implements Feature {

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    final MethodHandles.Lookup mhl = MethodHandles.lookup();

    LibGraalLoader libgraalLoader;

    /**
     * Loader used for loading classes from the guest GraalVM.
     */
    ClassLoader loader;

    /**
     * Handle to {@link BuildTime} in the guest.
     */
    Class<?> buildTimeClass;

    /**
     * Set of {@link LibGraalFeatureComponent}s created during analysis.
     */
    private final Set<LibGraalFeatureComponent> libGraalFeatureComponents = ConcurrentHashMap.newKeySet();

    /**
     * Loads the class {@code c} in the libgraal class loader.
     *
     * @throws Error if loading fails
     */
    public Class<?> loadClassOrFail(Class<?> c) {
        if (c.getClassLoader() == loader) {
            return c;
        }
        if (c.isArray()) {
            return loadClassOrFail(c.getComponentType()).arrayType();
        }
        return loadClassOrFail(c.getName());
    }

    /**
     * Loads the class named {@code name} in the libgraal class loader. The
     * {@link #loadClassOrFail(Class)} method should be used instead if the relevant class literal
     * is accessible.
     *
     * @throws Error if loading fails
     */
    public Class<?> loadClassOrFail(String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("%s unable to load class '%s'".formatted(loader.getName(), name));
        }
    }

    public Class<?> loadClassOrNull(String name) {
        try {
            return loader.loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Performs tasks once this feature is registered.
     * <ul>
     * <li>Create the {@code HostedLibGraalClassLoader} instance.</li>
     * <li>Get a handle to the {@link BuildTime} class in the guest.</li>
     * <li>Initializes the options in the guest.</li>
     * <li>Initializes some state needed by {@link LibGraalSubstitutions}.</li>
     * </ul>
     */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // LibGraalEntryPoints uses a number of classes in org.graalvm.nativeimage.builder
        exportModulesToLibGraal("org.graalvm.nativeimage.builder");

        // LibGraalFeature accesses a few Graal classes (see import statements above)
        exportModulesToLibGraal("jdk.graal.compiler");

        // LibGraalTruffleToLibGraalEntryPoints access TruffleToLibGraal.Id
        exportModulesToLibGraal("org.graalvm.truffle.compiler");

        ImageSingletons.add(NativeBridgeSupport.class, new LibGraalNativeBridgeSupport());

        libgraalLoader = createHostedLibGraalClassLoader(access);
        loader = libgraalLoader.getClassLoader();
        ImageSingletons.lookup(ClassForNameSupport.class).setLibGraalLoader(loader);

        buildTimeClass = loadClassOrFail(BuildTime.class);

        // Guest JVMCI and Graal need access to some JDK internal packages
        String[] basePackages = {"jdk.internal.misc", "jdk.internal.util", "jdk.internal.vm"};
        LibGraalUtil.accessPackagesToClass(LibGraalUtil.Access.EXPORT, null, false, "java.base", basePackages);
    }

    @SuppressWarnings("unchecked")
    private static LibGraalLoader createHostedLibGraalClassLoader(AfterRegistrationAccess access) {
        var hostedLibGraalClassLoaderClass = access.findClassByName("jdk.graal.compiler.libgraal.loader.HostedLibGraalClassLoader");
        LibGraalUtil.accessPackagesToClass(LibGraalUtil.Access.EXPORT, hostedLibGraalClassLoaderClass, false, "java.base", "jdk.internal.module");
        return LibGraalUtil.newInstance((Class<LibGraalLoader>) hostedLibGraalClassLoaderClass);
    }

    static void exportModulesToLibGraal(String... moduleNames) {
        accessModulesToClass(LibGraalUtil.Access.EXPORT, LibGraalFeature.class, moduleNames);
    }

    static void accessModulesToClass(LibGraalUtil.Access access, Class<?> accessingClass, String... moduleNames) {
        for (String moduleName : moduleNames) {
            var module = getBootModule(moduleName);
            LibGraalUtil.accessPackagesToClass(access, accessingClass, false,
                            module.getName(), module.getPackages().toArray(String[]::new));
        }
    }

    static Module getBootModule(String moduleName) {
        return ModuleLayer.boot().findModule(moduleName).orElseThrow();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ClassLoader runtimeLoader = libgraalLoader.getRuntimeClassLoader();
        access.registerObjectReplacer(obj -> obj == loader ? runtimeLoader : obj);

        optionCollector = new OptionCollector();
        access.registerObjectReachabilityHandler(optionCollector::accept, OptionKey.class);
        access.registerObjectReachabilityHandler(optionCollector::accept, loadClassOrFail(OptionKey.class));
        GetJNIConfig.register(loader);
    }

    private OptionCollector optionCollector;

    /**
     * Collects all options that are reachable at run time. Reachable options are the
     * {@link OptionKey} instances reached by the static analysis. The VM options are instances of
     * {@link OptionKey} loaded by the {@link com.oracle.svm.hosted.NativeImageClassLoader} and
     * compiler options are instances of {@link OptionKey} loaded by the
     * {@code HostedLibGraalClassLoader}.
     */
    private class OptionCollector implements Consumer<Object> {
        private final Set<Object> options = Collections.newSetFromMap(new ConcurrentHashMap<>());

        /**
         * Libgraal VM options.
         */
        private final EconomicMap<String, OptionDescriptor> vmOptionDescriptors = EconomicMap.create();

        /**
         * Libgraal compiler options info.
         */
        private final Object compilerOptionsInfo;

        private boolean sealed;

        OptionCollector() {
            try {
                MethodType mt = methodType(Object.class);
                MethodHandle mh = mhl.findStatic(buildTimeClass, "initLibgraalOptions", mt);
                compilerOptionsInfo = mh.invoke();
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }

        @Override
        public void accept(Object option) {
            if (sealed) {
                GraalError.guarantee(options.contains(option), "All options must have been discovered during static analysis: %s", option);
            } else {
                options.add(option);
            }
        }

        @SuppressWarnings("unchecked")
        void afterAnalysis(AfterAnalysisAccess access) {
            sealed = true;
            List<Object> compilerOptions = new ArrayList<>(options.size());
            for (Object option : options) {
                if (option instanceof OptionKey<?> optionKey) {
                    OptionDescriptor descriptor = optionKey.getDescriptor();
                    if (descriptor.isServiceLoaded()) {
                        GraalError.guarantee(access.isReachable(option.getClass()), "%s", option.getClass());
                        GraalError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());
                        vmOptionDescriptors.put(optionKey.getName(), descriptor);
                    }
                } else {
                    ClassLoader optionCL = option.getClass().getClassLoader();
                    GraalError.guarantee(optionCL == loader, "unexpected option loader: %s", optionCL);
                    compilerOptions.add(option);
                }
            }
            try {
                MethodType mt = methodType(Iterable.class, List.class, Object.class, Map.class);
                MethodHandle mh = mhl.findStatic(buildTimeClass, "finalizeLibgraalOptions", mt);
                LibGraalLoader manifest = (LibGraalLoader) loader;
                Map<String, String> modules = manifest.getModuleMap();
                Iterable<Object> values = (Iterable<Object>) mh.invoke(compilerOptions, compilerOptionsInfo, modules);
                for (Object descriptor : values) {
                    GraalError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());
                }
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<Class<?>> findLibGraalServices() {
        LibGraalLoader manifest = (LibGraalLoader) loader;
        Set<String> libgraalServicesModules = manifest.getServicesModules();
        Map<String, String> modules = manifest.getModuleMap();

        Class<? extends Annotation> service = (Class<? extends Annotation>) loadClassOrFail(LibGraalService.class);
        List<Class<?>> libgraalServices = new ArrayList<>();
        modules.entrySet().stream()//
                        .filter(e -> libgraalServicesModules.contains(e.getValue()))
                        .map(Map.Entry::getKey)//
                        .map(this::loadClassOrNull)//
                        .filter(c -> c != null && c.getAnnotation(service) != null)//
                        .forEach(libgraalServices::add);
        return libgraalServices;
    }

    private BeforeCompilationAccess beforeCompilationAccess;

    /**
     * Transformer for {@code Fields.offsets} and {@code Edges.iterationMask} which need to be
     * recomputed to use SVM field offsets instead of HotSpot field offsets.
     */
    class FieldOffsetsTransformer implements FieldValueTransformer {
        /**
         * Map from {@link Fields} objects to a (newOffsets, newIterationMask) tuple represented as
         * a {@link java.util.Map.Entry} value.
         */
        private final Map<Object, Map.Entry<long[], Long>> replacements = new IdentityHashMap<>();

        final Class<?> edgesClass;
        final Class<?> fieldsClass;
        final Field fieldsOffsetsField;
        final Field edgesIterationMaskField;
        final Method recomputeOffsetsAndIterationMaskMethod;

        FieldOffsetsTransformer() {
            edgesClass = loadClassOrFail(Edges.class.getName());
            fieldsClass = loadClassOrFail(Fields.class.getName());
            fieldsOffsetsField = LibGraalUtil.lookupField(fieldsClass, "offsets");
            edgesIterationMaskField = LibGraalUtil.lookupField(edgesClass, "iterationMask");
            recomputeOffsetsAndIterationMaskMethod = LibGraalUtil.lookupMethod(fieldsClass, "recomputeOffsetsAndIterationMask", BeforeCompilationAccess.class);
        }

        void register(BeforeAnalysisAccess access) {
            access.registerFieldValueTransformer(fieldsOffsetsField, this);
            access.registerFieldValueTransformer(edgesIterationMaskField, this);
        }

        @Override
        public boolean isAvailable() {
            return beforeCompilationAccess != null;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Map.Entry<long[], Long> repl = getReplacement(receiver);
            if (originalValue instanceof long[]) {
                return repl.getKey();
            }
            return repl.getValue();
        }

        private Map.Entry<long[], Long> getReplacement(Object receiver) {
            synchronized (replacements) {
                return replacements.computeIfAbsent(receiver, this::computeReplacement);
            }
        }

        @SuppressWarnings("unchecked")
        private Map.Entry<long[], Long> computeReplacement(Object receiver) {
            try {
                return (Map.Entry<long[], Long>) recomputeOffsetsAndIterationMaskMethod.invoke(receiver, beforeCompilationAccess);
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }
    }

    /**
     * Transforms {@code GlobalAtomicLong.addressSupplier} by replacing it with a {@link GlobalData}
     * backed address supplier.
     */
    class GlobalAtomicLongTransformer implements FieldValueTransformer {
        private MethodHandle globalAtomicLongGetInitialValue;

        void register(BeforeAnalysisAccess access) {
            Class<?> globalAtomicLongClass = loadClassOrFail(GlobalAtomicLong.class.getName());
            Field addressSupplierField = LibGraalUtil.lookupField(globalAtomicLongClass, "addressSupplier");
            access.registerFieldValueTransformer(addressSupplierField, this);
            try {
                globalAtomicLongGetInitialValue = mhl.findVirtual(globalAtomicLongClass, "getInitialValue", methodType(long.class));
            } catch (Throwable e) {
                GraalError.shouldNotReachHere(e);
            }
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            long initialValue;
            try {
                initialValue = (long) globalAtomicLongGetInitialValue.invoke(receiver);
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
            return GlobalData.createGlobal(initialValue);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        new FieldOffsetsTransformer().register(access);
        new GlobalAtomicLongTransformer().register(access);

        /* Contains static fields that depend on HotSpotJVMCIRuntime */
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail(HotSpotModifiers.class));
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream"));
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream$Tag"));
        /* ThreadLocal in static field jdk.graal.compiler.debug.DebugContext.activated */
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail(DebugContext.class));

        /* Needed for runtime calls to BoxingSnippets.Templates.getCacheClass(JavaKind) */
        RuntimeReflection.registerAllDeclaredClasses(Character.class);
        RuntimeReflection.register(LibGraalUtil.lookupField(LibGraalUtil.lookupClass("java.lang.Character$CharacterCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Byte.class);
        RuntimeReflection.register(LibGraalUtil.lookupField(LibGraalUtil.lookupClass("java.lang.Byte$ByteCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Short.class);
        RuntimeReflection.register(LibGraalUtil.lookupField(LibGraalUtil.lookupClass("java.lang.Short$ShortCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Integer.class);
        RuntimeReflection.register(LibGraalUtil.lookupField(LibGraalUtil.lookupClass("java.lang.Integer$IntegerCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Long.class);
        RuntimeReflection.register(LibGraalUtil.lookupField(LibGraalUtil.lookupClass("java.lang.Long$LongCache"), "cache"));

        /* Configure static state of Graal. */
        try {
            Consumer<Class<?>> registerAsInHeap = access::registerAsInHeap;

            Consumer<List<Class<?>>> hostedGraalSetFoldNodePluginClasses = GeneratedInvocationPlugin::setFoldNodePluginClasses;

            List<Class<?>> guestServiceClasses = findLibGraalServices();

            // Transfer libgraal qualifier (e.g. "PGO optimized") from host to guest.
            String nativeImageLocationQualifier = CompilerConfigurationFactory.getNativeImageLocationQualifier();

            MethodHandle configureGraalForLibGraal = mhl.findStatic(buildTimeClass,
                            "configureGraalForLibGraal",
                            methodType(void.class,
                                            String.class, // arch
                                            Collection.class, // libGraalFeatureComponents
                                            List.class, // guestServiceClasses
                                            Consumer.class, // registerAsInHeap
                                            Consumer.class, // hostedGraalSetFoldNodePluginClasses
                                            String.class, // nativeImageLocationQualifier
                                            byte[].class // encodedGuestObjects
                            ));
            Path libGraalJavaHome = LibGraalUtil.readField(loader.getClass(), "libGraalJavaHome", loader);
            GetCompilerConfig.Result configResult = GetCompilerConfig.from(libGraalJavaHome);
            for (var e : configResult.opens().entrySet()) {
                for (String source : e.getValue()) {
                    LibGraalUtil.accessPackagesToClass(LibGraalUtil.Access.OPEN, buildTimeClass, false, e.getKey(), source);
                }
            }

            Architecture arch = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch;
            configureGraalForLibGraal.invoke(arch.getName(),
                            libGraalFeatureComponents,
                            guestServiceClasses,
                            registerAsInHeap,
                            hostedGraalSetFoldNodePluginClasses,
                            nativeImageLocationQualifier,
                            configResult.encodedConfig());

            initGraalRuntimeHandles(mhl.findStatic(buildTimeClass, "getRuntimeHandles", methodType(Map.class)));
            initializeTruffle();
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        for (var c : libGraalFeatureComponents) {
            c.duringAnalysis(access);
        }
    }

    @SuppressWarnings("unchecked")
    private static void initGraalRuntimeHandles(MethodHandle getRuntimeHandles) throws Throwable {
        ImageSingletons.add(LibGraalEntryPoints.class, new LibGraalEntryPoints((Map<String, MethodHandle>) getRuntimeHandles.invoke()));
    }

    @SuppressWarnings("unchecked")
    private void initializeTruffle() throws Throwable {
        Class<?> truffleBuildTimeClass = loadClassOrFail("jdk.graal.compiler.hotspot.libgraal.truffle.BuildTime");
        MethodHandle getLookup = mhl.findStatic(truffleBuildTimeClass, "initializeLookup", methodType(Map.Entry.class, Lookup.class, Class.class, Class.class));
        Map.Entry<Lookup, Class<?>> truffleLibGraal = (Map.Entry<Lookup, Class<?>>) getLookup.invoke(mhl, TruffleFromLibGraalStartPoints.class, NativeImageHostEntryPoints.class);
        ImageSingletons.add(LibGraalTruffleToLibGraalEntryPoints.class, new LibGraalTruffleToLibGraalEntryPoints(truffleLibGraal.getKey(), truffleLibGraal.getValue()));
        MethodHandle truffleConfigureGraalForLibGraal = mhl.findStatic(truffleBuildTimeClass, "configureGraalForLibGraal", methodType(void.class));
        truffleConfigureGraalForLibGraal.invoke();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        checkForbiddenTypes((AfterAnalysisAccessImpl) access);
        optionCollector.afterAnalysis(access);
    }

    @SuppressWarnings("try")
    private void checkForbiddenTypes(AfterAnalysisAccessImpl access) {
        /*
         * Verify we only have JVMCI & Graal classes reachable that are coming from
         * LibGraalClassLoader except for hosted JVMCI & Graal classes that are legitimately used by
         * SubstrateVM runtime implementation classes (mostly from package com.oracle.svm.core).
         */
        List<Pattern> hostedAllowed = List.of(
                        classesPattern("jdk.graal.compiler.core.common",
                                        "NumUtil"),
                        classesPattern("jdk.graal.compiler.core.common.util",
                                        "AbstractTypeReader", "TypeConversion", "TypeReader", "UnsafeArrayTypeReader"),
                        classesPattern("jdk.graal.compiler.core.common.type",
                                        "CompressibleConstant"),
                        classesPattern("jdk.graal.compiler.debug",
                                        "GraalError"),
                        classesPattern("jdk.graal.compiler.options",
                                        "ModifiableOptionValues", "Option.*"),
                        classesPattern("jdk.graal.compiler.util.json",
                                        "JsonWriter", "JsonBuilder.*"),
                        classesPattern("org.graalvm.collections",
                                        "EconomicMap.*", "EmptyMap.*", "Equivalence.*", "Pair"),
                        classesPattern("jdk.vm.ci.amd64",
                                        "AMD64.*"),
                        classesPattern("jdk.vm.ci.aarch64",
                                        "AArch64.*"),
                        classesPattern("jdk.vm.ci.riscv64",
                                        "RISCV64.*"),
                        classesPattern("jdk.vm.ci.code",
                                        "Architecture", "Register.*", "TargetDescription"),
                        classesPattern("jdk.vm.ci.meta",
                                        "JavaConstant", "JavaKind", "MetaUtil", "NullConstant", "PrimitiveConstant"));

        Set<String> forbiddenHostedModules = Set.of("jdk.internal.vm.ci", "org.graalvm.collections", "org.graalvm.word", "jdk.graal.compiler");

        BigBang bigBang = access.getBigBang();
        CallTreePrinter callTreePrinter = new CallTreePrinter(bigBang);
        callTreePrinter.buildCallTree();

        DebugContext debug = bigBang.getDebug();
        List<String> forbiddenReachableTypes = new ArrayList<>();
        try (DebugContext.Scope ignored = debug.scope("LibGraalEntryPoints")) {
            for (AnalysisType analysisType : callTreePrinter.usedAnalysisTypes()) {
                Class<?> reachableType = analysisType.getJavaClass();
                if (reachableType.getClassLoader() == loader || reachableType.isArray()) {
                    continue;
                }
                Module module = reachableType.getModule();
                if (module.isNamed() && forbiddenHostedModules.contains(module.getName())) {
                    String fqn = reachableType.getName();
                    if (hostedAllowed.stream().anyMatch(pattern -> pattern.matcher(fqn).matches())) {
                        debug.log("Allowing hosted class %s from %s", fqn, module);
                        continue;
                    }
                    forbiddenReachableTypes.add(String.format("%s/%s", module.getName(), fqn));
                }
            }
        }
        if (!forbiddenReachableTypes.isEmpty()) {
            CallTreePrinter.print(bigBang, "reports", "report");
            throw new GraalError("LibGraalEntryPoints build found forbidden hosted types as reachable: %s", String.join(", ", forbiddenReachableTypes));
        }
    }

    private static Pattern classesPattern(String packageName, String... regexes) {
        return Pattern.compile("%s(%s)".formatted(Pattern.quote(packageName + '.'), String.join("|", regexes)));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        beforeCompilationAccess = access;
    }
}
