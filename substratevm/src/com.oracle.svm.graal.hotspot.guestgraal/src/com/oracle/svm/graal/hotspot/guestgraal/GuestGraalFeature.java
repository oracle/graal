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
package com.oracle.svm.graal.hotspot.guestgraal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.oracle.svm.graal.hotspot.GetCompilerConfig;
import com.oracle.svm.graal.hotspot.GetJNIConfig;
import com.oracle.svm.hosted.FeatureImpl;
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
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.code.TargetDescription;

/**
 * This feature builds the libgraal shared library (e.g., libjvmcicompiler.so on linux).
 * <p>
 * With use of {@code -H:GuestJavaHome}, the Graal and JVMCI classes from which libgraal is built
 * can be from a "guest" JDK that may be different from the JDK on which Native Image is running.
 * <p>
 * To build libgraal, invoke {@code native-image} with the jar containing this feature and it
 * dependencies. For example:
 * 
 * <pre>
 *      native-image -p jniutils.jar -cp guestgraal-library.jar
 * </pre>
 *
 * If building with mx, execute this from the {@code vm} suite:
 * 
 * <pre>
 *      mx --env guestgraal native-image \
 *          -p $(mx --env guestgraal --quiet path JNIUTILS) \
 *          -cp $(mx --env guestgraal --quiet path GUESTGRAAL_LIBRARY)
 * </pre>
 *
 * This feature is composed of these key classes:
 * <ul>
 * <li>{@link GuestGraalClassLoader}</li>
 * <li>{@link GuestGraal}</li>
 * <li>{@link GuestGraalSubstitutions}</li>
 * </ul>
 *
 * Additionally, it defines
 * {@code META-INF/native-image/com.oracle.svm.graal.hotspot.guestgraal/native-image.properties}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestGraalFeature implements Feature {

    static class Options {
        @Option(help = "The value of the java.home system property reported by the Java " +
                        "installation that includes the Graal classes in its runtime image " +
                        "from which libgraal will be built. If not provided, the java.home " +
                        "of the Java installation running native-image will be used.") //
        public static final HostedOptionKey<Path> GuestJavaHome = new HostedOptionKey<>(Path.of(System.getProperty("java.home")));
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(GuestGraalFeature.class);
        }
    }

    private GuestGraalFeature() {
        // GuestGraalFieldsOffsetsFeature implements InternalFeature which is in
        // the non-public package com.oracle.svm.core.feature
        accessModulesToClass(ModuleSupport.Access.EXPORT, GuestGraalFeature.class, "org.graalvm.nativeimage.builder");

        // GuestGraalFeature accesses a few Graal classes (see import statements above)
        accessModulesToClass(ModuleSupport.Access.EXPORT, GuestGraalFeature.class, "jdk.graal.compiler");
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(GuestGraalFieldsOffsetsFeature.class);
    }

    final MethodHandles.Lookup mhl = MethodHandles.lookup();

    /**
     * Loader used for loading classes from the guest GraalVM.
     */
    GuestGraalClassLoader loader;

    /**
     * Handle to {@link jdk.graal.compiler.hotspot.guestgraal.BuildTime} in the guest.
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

    /**
     * Performs tasks once this feature is registered.
     * <ul>
     * <li>Create the {@link GuestGraalClassLoader} instance.</li>
     * <li>Get a handle to the {@link jdk.graal.compiler.hotspot.guestgraal.BuildTime} class in the
     * guest.</li>
     * <li>Initializes the options in the guest.</li>
     * <li>Initializes some state needed by {@link GuestGraalSubstitutions}.</li>
     * </ul>
     */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(NativeBridgeSupport.class, new GuestGraalNativeBridgeSupport());
        // Target_jdk_graal_compiler_serviceprovider_VMSupport.getIsolateID needs access to
        // org.graalvm.nativeimage.impl.IsolateSupport
        accessModulesToClass(ModuleSupport.Access.EXPORT, GuestGraalFeature.class, "org.graalvm.nativeimage");

        loader = new GuestGraalClassLoader(Options.GuestJavaHome.getValue().resolve(Path.of("lib", "modules")));

        try {
            buildTimeClass = loader.loadClassOrFail("jdk.graal.compiler.hotspot.guestgraal.BuildTime");

            // Guest JVMCI and Graal need access to some JDK internal packages
            String[] basePackages = {"jdk.internal.misc", "jdk.internal.util"};
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, null, false, "java.base", basePackages);

            /*
             * Ensure OptionsParser.cachedOptionDescriptors is initialized with OptionDescriptors
             * from GuestGraalClassLoader.
             */
            mhl.findStatic(buildTimeClass, "configureOptionsParserCachedOptionDescriptors", methodType(void.class)).invoke();
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("Failed to invoke jdk.graal.compiler.hotspot.guestgraal.BuildTime.configureOptionsParserCachedOptionDescriptors", e);
        }

        try {
            /*
             * Get GlobalAtomicLong.getInitialValue() method from GuestGraalClassLoader for
             * GuestGraalGraalSubstitutions.GlobalAtomicLongAddressProvider FieldValueTransformer
             */
            handleGlobalAtomicLongGetInitialValue = mhl.findVirtual(loader.loadClassOrFail("jdk.graal.compiler.serviceprovider.GlobalAtomicLong"),
                            "getInitialValue", methodType(long.class));

        } catch (Throwable e) {
            VMError.shouldNotReachHere(e);
        }
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
        try {
            var basePhaseStatisticsClass = loader.loadClassOrFail("jdk.graal.compiler.phases.BasePhase$BasePhaseStatistics");
            var lirPhaseStatisticsClass = loader.loadClassOrFail("jdk.graal.compiler.lir.phases.LIRPhase$LIRPhaseStatistics");
            MethodType statisticsCTorType = methodType(void.class, Class.class);
            var basePhaseStatisticsCTor = mhl.findConstructor(basePhaseStatisticsClass, statisticsCTorType);
            var lirPhaseStatisticsCTor = mhl.findConstructor(lirPhaseStatisticsClass, statisticsCTorType);
            newBasePhaseStatistics = new StatisticsCreator(basePhaseStatisticsCTor)::create;
            newLIRPhaseStatistics = new StatisticsCreator(lirPhaseStatisticsCTor)::create;

            basePhaseClass = loader.loadClassOrFail("jdk.graal.compiler.phases.BasePhase");
            lirPhaseClass = loader.loadClassOrFail("jdk.graal.compiler.lir.phases.LIRPhase");

            ImageSingletons.add(GuestGraalCompilerSupport.class, new GuestGraalCompilerSupport());
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("Failed to invoke jdk.graal.compiler.hotspot.guestgraal.BuildTime methods", e);
        }

        DuringSetupAccessImpl accessImpl = (DuringSetupAccessImpl) access;

        accessImpl.registerClassReachabilityListener(this::registerPhaseStatistics);

        GetJNIConfig.register(loader);
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
                GuestGraalCompilerSupport.registerStatistics(newlyReachableClass, GuestGraalCompilerSupport.get().basePhaseStatistics,
                                newBasePhaseStatistics.apply(newlyReachableClass));

            } else if (lirPhaseClass.isAssignableFrom(newlyReachableClass)) {
                GuestGraalCompilerSupport.registerStatistics(newlyReachableClass, GuestGraalCompilerSupport.get().lirPhaseStatistics,
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
        RuntimeClassInitialization.initializeAtRunTime(loader.loadClassOrFail("jdk.vm.ci.hotspot.HotSpotModifiers"));
        RuntimeClassInitialization.initializeAtRunTime(loader.loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream"));
        RuntimeClassInitialization.initializeAtRunTime(loader.loadClassOrFail("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream$Tag"));
        /* ThreadLocal in static field jdk.graal.compiler.debug.DebugContext.activated */
        RuntimeClassInitialization.initializeAtRunTime(loader.loadClassOrFail("jdk.graal.compiler.debug.DebugContext"));

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

        /* Configure static state of Graal so that suitable for the GuestGraal use case */
        try {
            TargetDescription targetDescription = ImageSingletons.lookup(SubstrateTargetDescription.class);
            String arch = targetDescription.arch.getName();

            Consumer<Class<?>> registerAsInHeap = nodeClass -> impl.getMetaAccess().lookupJavaType(nodeClass)
                            .registerAsInstantiated("All NodeClass classes are marked as instantiated eagerly.");

            Consumer<List<Class<?>>> hostedGraalSetFoldNodePluginClasses = GeneratedInvocationPlugin::setFoldNodePluginClasses;

            MethodHandle configureGraalForLibGraal = mhl.findStatic(buildTimeClass,
                            "configureGraalForLibGraal",
                            methodType(void.class,
                                            String.class, // arch
                                            Consumer.class, // registerAsInHeap
                                            Consumer.class, // hostedGraalSetFoldNodePluginClasses
                                            String.class // encodedGuestObjects
                            ));
            GetCompilerConfig.Result configResult = GetCompilerConfig.from(Options.GuestJavaHome.getValue(), bb.getOptions());
            for (var e : configResult.opens().entrySet()) {
                for (String source : e.getValue()) {
                    ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, buildTimeClass, false, e.getKey(), source);
                }
            }

            configureGraalForLibGraal.invoke(arch,
                            registerAsInHeap,
                            hostedGraalSetFoldNodePluginClasses,
                            configResult.encodedConfig());

            initRuntimeHandles(mhl.findStatic(buildTimeClass, "getRuntimeHandles", methodType(Map.class)));

        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("Failed to invoke jdk.graal.compiler.hotspot.guestgraal.BuildTime methods", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void initRuntimeHandles(MethodHandle getRuntimeHandles) throws Throwable {
        ImageSingletons.add(GuestGraal.class, new GuestGraal((Map<String, MethodHandle>) getRuntimeHandles.invoke()));
    }

    @SuppressWarnings("try")
    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        /*
         * Verify we only have JVMCI & Graal classes reachable that are coming from
         * GuestGraalClassLoader except for hosted JVMCI & Graal classes that are legitimately used
         * by SubstrateVM runtime implementation classes (mostly from package com.oracle.svm.core).
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

        BigBang bigBang = ((AfterAnalysisAccessImpl) a).getBigBang();
        CallTreePrinter callTreePrinter = new CallTreePrinter(bigBang);
        callTreePrinter.buildCallTree();

        DebugContext debug = bigBang.getDebug();
        List<String> forbiddenReachableTypes = new ArrayList<>();
        try (DebugContext.Scope ignored = debug.scope("GuestGraal")) {
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
            VMError.shouldNotReachHere("GuestGraal build found forbidden hosted types as reachable: %s", String.join(", ", forbiddenReachableTypes));
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (!Platform.includedIn(Platform.WINDOWS.class)) {
            ((FeatureImpl.BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
                Path imageFilePath = linkerInvocation.getOutputFile();
                String imageName = imageFilePath.getFileName().toString();
                String posixLibraryPrefix = "lib";
                assert !imageName.startsWith(posixLibraryPrefix);
                String posixImageName = posixLibraryPrefix + imageName;
                linkerInvocation.setOutputFile(imageFilePath.getParent().resolve(posixImageName));
                return linkerInvocation;
            });
        }
    }

    private static Pattern classesPattern(String packageName, String... regexes) {
        return Pattern.compile("%s(%s)".formatted(Pattern.quote(packageName + '.'), String.join("|", regexes)));
    }
}
