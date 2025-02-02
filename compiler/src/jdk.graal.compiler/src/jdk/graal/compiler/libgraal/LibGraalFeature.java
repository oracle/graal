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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hotspot.GetCompilerConfig;
import com.oracle.svm.graal.hotspot.GetJNIConfig;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ModuleSupport.Access;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.libgraal.BuildTime;
import jdk.graal.compiler.hotspot.libgraal.LibGraalClassLoaderBase;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.serviceprovider.LibGraalService;
import jdk.vm.ci.code.TargetDescription;

/**
 * This feature builds the libgraal shared library (e.g., libjvmcicompiler.so on linux).
 * <p>
 * With use of {@code -Djdk.graal.internal.libgraal.javahome=path}, the Graal and JVMCI classes from
 * which libgraal is built can be from a "guest" JDK that may be different from the JDK on which
 * Native Image is running.
 * <p>
 * This feature is composed of these key classes:
 * <ul>
 * <li>{@code HostedLibGraalClassLoader}</li>
 * <li>{@link LibGraalEntryPoints}</li>
 * <li>{@link LibGraalSubstitutions}</li>
 * </ul>
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class LibGraalFeature implements Feature {

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(LibGraalFieldsOffsetsFeature.class);
    }

    final MethodHandles.Lookup mhl = MethodHandles.lookup();

    LibGraalClassLoaderBase libGraalClassLoader;

    /**
     * Loader used for loading classes from the guest GraalVM.
     */
    ClassLoader loader;

    /**
     * Handle to {@link BuildTime} in the guest.
     */
    Class<?> buildTimeClass;

    /**
     * Handle to {@link jdk.graal.compiler.phases.BasePhase} in the guest.
     */
    private Class<?> basePhaseClass;

    private Function<Class<?>, Object> newBasePhaseStatistics;

    /**
     * Handle to {@link jdk.graal.compiler.lir.phases.LIRPhase} in the guest.
     */
    private Class<?> lirPhaseClass;

    private Function<Class<?>, Object> newLIRPhaseStatistics;

    MethodHandle handleGlobalAtomicLongGetInitialValue;

    public ClassLoader getLoader() {
        return loader;
    }

    public Class<?> loadClassOrFail(Class<?> c) {
        if (c.getClassLoader() == loader) {
            return c;
        }
        if (c.isArray()) {
            return loadClassOrFail(c.getComponentType()).arrayType();
        }
        return loadClassOrFail(c.getName());
    }

    public Class<?> loadClassOrFail(String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("%s unable to load class '%s'".formatted(loader.getName(), name));
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
        accessModulesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class,
                        "org.graalvm.nativeimage.builder");

        // LibGraalFeature accesses a few Graal classes (see import statements above)
        accessModulesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, "jdk.graal.compiler");

        // LibGraalTruffleToLibGraalEntryPoints access TruffleToLibGraal.Id
        accessModulesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, "org.graalvm.truffle.compiler");

        ImageSingletons.add(NativeBridgeSupport.class, new LibGraalNativeBridgeSupport());
        // Target_jdk_graal_compiler_serviceprovider_VMSupport.getIsolateID needs access to
        // org.graalvm.nativeimage.impl.IsolateSupport
        accessModulesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, "org.graalvm.nativeimage");

        libGraalClassLoader = createHostedLibGraalClassLoader(access);
        loader = libGraalClassLoader.getClassLoader();
        ClassForNameSupport.currentLayer().setLibGraalLoader(loader);

        buildTimeClass = loadClassOrFail("jdk.graal.compiler.hotspot.libgraal.BuildTime");

        // Guest JVMCI and Graal need access to some JDK internal packages
        String[] basePackages = {"jdk.internal.misc", "jdk.internal.util", "jdk.internal.vm"};
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, null, false, "java.base", basePackages);

        try {
            /*
             * Get GlobalAtomicLong.getInitialValue() method from LibGraalClassLoader for
             * LibGraalGraalSubstitutions.GlobalAtomicLongAddressProvider FieldValueTransformer
             */
            handleGlobalAtomicLongGetInitialValue = mhl.findVirtual(loadClassOrFail("jdk.graal.compiler.serviceprovider.GlobalAtomicLong"),
                            "getInitialValue", methodType(long.class));

        } catch (Throwable e) {
            VMError.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static LibGraalClassLoaderBase createHostedLibGraalClassLoader(AfterRegistrationAccess access) {
        var hostedLibGraalClassLoaderClass = access.findClassByName("jdk.graal.compiler.hotspot.libgraal.HostedLibGraalClassLoader");
        ModuleSupport.accessPackagesToClass(Access.EXPORT, hostedLibGraalClassLoaderClass, false, "java.base", "jdk.internal.module");
        return ReflectionUtil.newInstance((Class<LibGraalClassLoaderBase>) hostedLibGraalClassLoaderClass);
    }

    private static void accessModulesToClass(ModuleSupport.Access access, Class<?> accessingClass, String... moduleNames) {
        for (String moduleName : moduleNames) {
            var module = getBootModule(moduleName);
            ModuleSupport.accessPackagesToClass(access, accessingClass, false,
                            module.getName(), module.getPackages().toArray(String[]::new));
        }
    }

    static Module getBootModule(String moduleName) {
        return ModuleLayer.boot().findModule(moduleName).orElseThrow();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {

        /*
         * HostedLibGraalClassLoader provides runtime-replacement loader instance. Make sure
         * HostedLibGraalClassLoader gets replaced by customRuntimeLoader instance in image.
         */
        ClassLoader customRuntimeLoader = libGraalClassLoader.getRuntimeClassLoader();
        access.registerObjectReplacer(obj -> obj == loader ? customRuntimeLoader : obj);

        try {
            var basePhaseStatisticsClass = loadClassOrFail("jdk.graal.compiler.phases.BasePhase$BasePhaseStatistics");
            var lirPhaseStatisticsClass = loadClassOrFail("jdk.graal.compiler.lir.phases.LIRPhase$LIRPhaseStatistics");
            MethodType statisticsCTorType = methodType(void.class, Class.class);
            var basePhaseStatisticsCTor = mhl.findConstructor(basePhaseStatisticsClass, statisticsCTorType);
            var lirPhaseStatisticsCTor = mhl.findConstructor(lirPhaseStatisticsClass, statisticsCTorType);
            newBasePhaseStatistics = new StatisticsCreator(basePhaseStatisticsCTor)::create;
            newLIRPhaseStatistics = new StatisticsCreator(lirPhaseStatisticsCTor)::create;

            basePhaseClass = loadClassOrFail("jdk.graal.compiler.phases.BasePhase");
            lirPhaseClass = loadClassOrFail("jdk.graal.compiler.lir.phases.LIRPhase");

            ImageSingletons.add(LibGraalCompilerSupport.class, new LibGraalCompilerSupport());
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("Failed to invoke jdk.graal.compiler.hotspot.libgraal.BuildTime methods", e);
        }

        DuringSetupAccessImpl accessImpl = (DuringSetupAccessImpl) access;
        accessImpl.registerClassReachabilityListener(this::registerPhaseStatistics);
        optionCollector = new OptionCollector(LibGraalEntryPoints.vmOptionDescriptors);
        access.registerObjectReachabilityHandler(optionCollector::accept, OptionKey.class);
        access.registerObjectReachabilityHandler(optionCollector::accept, loadClassOrFail(OptionKey.class.getName()));
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
        private final EconomicMap<String, OptionDescriptor> vmOptionDescriptors;

        /**
         * Libgraal compiler options info.
         */
        private final Object compilerOptionsInfo;

        private boolean sealed;

        OptionCollector(EconomicMap<String, OptionDescriptor> vmOptionDescriptors) {
            this.vmOptionDescriptors = vmOptionDescriptors;
            try {
                MethodType mt = methodType(Object.class);
                MethodHandle mh = mhl.findStatic(buildTimeClass, "initLibgraalOptions", mt);
                compilerOptionsInfo = mh.invoke();
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        @Override
        public void accept(Object option) {
            if (sealed) {
                VMError.guarantee(options.contains(option), "All options must have been discovered during static analysis: %s", option);
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
                        VMError.guarantee(access.isReachable(option.getClass()), "%s", option.getClass());
                        VMError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());
                        vmOptionDescriptors.put(optionKey.getName(), descriptor);
                    }
                } else {
                    ClassLoader optionCL = option.getClass().getClassLoader();
                    VMError.guarantee(optionCL == loader, "unexpected option loader: %s", optionCL);
                    compilerOptions.add(option);
                }
            }
            try {
                MethodType mt = methodType(Iterable.class, List.class, Object.class, Map.class);
                MethodHandle mh = mhl.findStatic(buildTimeClass, "finalizeLibgraalOptions", mt);
                Map<String, String> modules = ReflectionUtil.invokeMethod(ReflectionUtil.lookupMethod(loader.getClass(), "getModules"), loader);
                Iterable<Object> values = (Iterable<Object>) mh.invoke(compilerOptions, compilerOptionsInfo, modules);
                for (Object descriptor : values) {
                    VMError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());
                }
            } catch (Throwable e) {
                VMError.shouldNotReachHere(e);
            }
        }
    }

    private record StatisticsCreator(MethodHandle ctorHandle) {
        Object create(Class<?> clazz) {
            try {
                return ctorHandle.invoke(clazz);
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere("Failed to create new instance of Statistics clazz with MethodHandle " + ctorHandle, e);
            }
        }
    }

    private void registerPhaseStatistics(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!Modifier.isAbstract(newlyReachableClass.getModifiers())) {
            boolean requireAnalysisIteration = true;
            if (basePhaseClass.isAssignableFrom(newlyReachableClass)) {
                LibGraalCompilerSupport.registerStatistics(newlyReachableClass, LibGraalCompilerSupport.get().basePhaseStatistics,
                                newBasePhaseStatistics.apply(newlyReachableClass));

            } else if (lirPhaseClass.isAssignableFrom(newlyReachableClass)) {
                LibGraalCompilerSupport.registerStatistics(newlyReachableClass, LibGraalCompilerSupport.get().lirPhaseStatistics,
                                newLIRPhaseStatistics.apply(newlyReachableClass));
            } else {
                requireAnalysisIteration = false;
            }

            if (requireAnalysisIteration) {
                access.requireAnalysisIteration();
            }
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess baa) {
        BeforeAnalysisAccessImpl impl = (BeforeAnalysisAccessImpl) baa;
        var bb = impl.getBigBang();

        /* Contains static fields that depend on HotSpotJVMCIRuntime */
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.vm.ci.hotspot.HotSpotModifiers"));
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream"));
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream$Tag"));
        /* ThreadLocal in static field jdk.graal.compiler.debug.DebugContext.activated */
        RuntimeClassInitialization.initializeAtRunTime(loadClassOrFail("jdk.graal.compiler.debug.DebugContext"));

        /* Needed for runtime calls to BoxingSnippets.Templates.getCacheClass(JavaKind) */
        RuntimeReflection.registerAllDeclaredClasses(Character.class);
        RuntimeReflection.register(ReflectionUtil.lookupField(ReflectionUtil.lookupClass("java.lang.Character$CharacterCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Byte.class);
        RuntimeReflection.register(ReflectionUtil.lookupField(ReflectionUtil.lookupClass("java.lang.Byte$ByteCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Short.class);
        RuntimeReflection.register(ReflectionUtil.lookupField(ReflectionUtil.lookupClass("java.lang.Short$ShortCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Integer.class);
        RuntimeReflection.register(ReflectionUtil.lookupField(ReflectionUtil.lookupClass("java.lang.Integer$IntegerCache"), "cache"));
        RuntimeReflection.registerAllDeclaredClasses(Long.class);
        RuntimeReflection.register(ReflectionUtil.lookupField(ReflectionUtil.lookupClass("java.lang.Long$LongCache"), "cache"));

        /* Configure static state of Graal. */
        try {
            TargetDescription targetDescription = ImageSingletons.lookup(SubstrateTargetDescription.class);
            String arch = targetDescription.arch.getName();

            Consumer<Class<?>> registerAsInHeap = nodeClass -> impl.getMetaAccess().lookupJavaType(nodeClass)
                            .registerAsInstantiated("All NodeClass classes are marked as instantiated eagerly.");

            List<Class<?>> guestServiceClasses = new ArrayList<>();
            List<Class<?>> serviceClasses = impl.getImageClassLoader().findAnnotatedClasses(LibGraalService.class, false);
            serviceClasses.stream().map(c -> loadClassOrFail(c.getName())).forEach(guestServiceClasses::add);

            // Transfer libgraal qualifier (e.g. "PGO optimized") from host to guest.
            String nativeImageLocationQualifier = CompilerConfigurationFactory.getNativeImageLocationQualifier();

            MethodHandle configureGraalForLibGraal = mhl.findStatic(buildTimeClass,
                            "configureGraalForLibGraal",
                            methodType(void.class,
                                            String.class, // arch
                                            List.class, // guestServiceClasses
                                            Consumer.class, // registerAsInHeap
                                            String.class, // nativeImageLocationQualifier
                                            byte[].class // encodedGuestObjects
                            ));
            Path libGraalJavaHome = ReflectionUtil.readField(loader.getClass(), "libGraalJavaHome", loader);
            GetCompilerConfig.Result configResult = GetCompilerConfig.from(libGraalJavaHome, bb.getOptions());
            for (var e : configResult.opens().entrySet()) {
                for (String source : e.getValue()) {
                    ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, buildTimeClass, false, e.getKey(), source);
                }
            }

            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, buildTimeClass, false, "org.graalvm.word", "org.graalvm.word.impl");

            configureGraalForLibGraal.invoke(arch,
                            guestServiceClasses,
                            registerAsInHeap,
                            nativeImageLocationQualifier,
                            configResult.encodedConfig());

            initGraalRuntimeHandles(mhl.findStatic(buildTimeClass, "getRuntimeHandles", methodType(Map.class)));
            initializeTruffle();
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
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

    @SuppressWarnings("try")
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
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

        AfterAnalysisAccessImpl accessImpl = (AfterAnalysisAccessImpl) access;
        BigBang bigBang = accessImpl.getBigBang();
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
            VMError.shouldNotReachHere("LibGraalEntryPoints build found forbidden hosted types as reachable: %s", String.join(", ", forbiddenReachableTypes));
        }
        optionCollector.afterAnalysis(access);
    }

    private static Pattern classesPattern(String packageName, String... regexes) {
        return Pattern.compile("%s(%s)".formatted(Pattern.quote(packageName + '.'), String.join("|", regexes)));
    }
}
