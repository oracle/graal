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
package jdk.graal.compiler.libgraal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.libgraal.truffle.LibGraalTruffleHostEnvironmentLookup;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.JVMCIServiceLocator;
import jdk.vm.ci.services.Services;
import org.graalvm.collections.EconomicMap;
import org.graalvm.jniutils.NativeBridgeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.nativeimage.LibGraalFeatureComponent;
import jdk.graal.nativeimage.LibGraalLoader;
import jdk.graal.nativeimage.hosted.GlobalData;
import jdk.vm.ci.hotspot.HotSpotModifiers;

/**
 * This feature builds the libgraal shared library (e.g., libjvmcicompiler.so on linux).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class LibGraalFeature implements Feature {

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            Class<LibGraalFeature> clazz = LibGraalFeature.class;
            if (ImageSingletons.contains(clazz)) {
                GraalError.guarantee("LibGraalClassLoader".equals(IsEnabled.class.getClassLoader().getName()),
                                "Only ever return true when LibGraalFeature got loaded by HostedLibGraalClassLoader");
                return true;
            }
            return false;
        }
    }

    final MethodHandles.Lookup mhl = MethodHandles.lookup();

    final LibGraalLoader libgraalLoader = (LibGraalLoader) getClass().getClassLoader();

    /**
     * Set of {@link LibGraalFeatureComponent}s created during analysis.
     */
    private final Set<LibGraalFeatureComponent> libGraalFeatureComponents = ConcurrentHashMap.newKeySet();

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // LibGraalEntryPoints uses a number of classes in org.graalvm.nativeimage.builder
        exportModulesToLibGraal("org.graalvm.nativeimage.builder");

        // LibGraalFeature accesses a few Graal classes (see import statements above)
        exportModulesToLibGraal("jdk.graal.compiler");

        // LibGraalTruffleToLibGraalEntryPoints access TruffleToLibGraal.Id
        exportModulesToLibGraal("org.graalvm.truffle.compiler");

        ImageSingletons.add(NativeBridgeSupport.class, new LibGraalNativeBridgeSupport());

        // Guest JVMCI and Graal need access to some JDK internal packages
        String[] basePackages = {
                        "jdk.internal.misc",
                        "jdk.internal.util",
                        "jdk.internal.vm"};
        LibGraalUtil.accessPackagesToClass(LibGraalUtil.Access.EXPORT, null, false, "java.base", basePackages);
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
        ClassLoader cl = libgraalLoader.getClassLoader();
        access.registerObjectReplacer(obj -> obj == cl ? runtimeLoader : obj);

        optionCollector = new OptionCollector();
        access.registerObjectReachabilityHandler(optionCollector::accept, OptionKey.class);
        GetJNIConfig.register(cl);
    }

    private OptionCollector optionCollector;

    /**
     * Collects all options that are reachable at run time. Reachable options are the
     * {@link OptionKey} instances reached by the static analysis. The VM options are instances of
     * {@link OptionKey} loaded by the {@code com.oracle.svm.hosted.NativeImageClassLoader} and
     * compiler options are instances of {@link OptionKey} loaded by the
     * {@code HostedLibGraalClassLoader}.
     */
    private class OptionCollector implements Consumer<OptionKey<?>> {
        private final Set<OptionKey<?>> options = Collections.newSetFromMap(new ConcurrentHashMap<>());

        /**
         * Libgraal compiler options info.
         */
        private final OptionsParser.LibGraalOptionsInfo compilerOptionsInfo;

        private boolean sealed;

        OptionCollector() {
            compilerOptionsInfo = OptionsParser.setLibgraalOptions(OptionsParser.LibGraalOptionsInfo.create());
        }

        @Override
        public void accept(OptionKey<?> option) {
            if (sealed) {
                GraalError.guarantee(options.contains(option), "All options must have been discovered during static analysis: %s", option);
            } else {
                options.add(option);
            }
        }

        void afterAnalysis(AfterAnalysisAccess access) {
            sealed = true;
            Map<String, String> modules = libgraalLoader.getModuleMap();
            for (OptionKey<?> option : options) {
                OptionDescriptor descriptor = option.getDescriptor();
                if (descriptor.isServiceLoaded()) {
                    GraalError.guarantee(access.isReachable(option.getClass()), "%s", option.getClass());
                    GraalError.guarantee(access.isReachable(descriptor.getClass()), "%s", descriptor.getClass());

                    String name = option.getName();
                    compilerOptionsInfo.descriptors().put(name, descriptor);

                    String module = modules.get(descriptor.getDeclaringClass().getName());
                    if (module.contains("enterprise")) {
                        compilerOptionsInfo.enterpriseOptions().add(name);
                    }
                }
            }
        }
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
            edgesClass = Edges.class;
            fieldsClass = Fields.class;
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
            Field addressSupplierField = LibGraalUtil.lookupField(GlobalAtomicLong.class, "addressSupplier");
            access.registerFieldValueTransformer(addressSupplierField, this);
            try {
                globalAtomicLongGetInitialValue = mhl.findVirtual(GlobalAtomicLong.class, "getInitialValue", methodType(long.class));
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

    @SuppressWarnings("unchecked")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        new FieldOffsetsTransformer().register(access);
        new GlobalAtomicLongTransformer().register(access);

        /* Contains static fields that depend on HotSpotJVMCIRuntime */
        RuntimeClassInitialization.initializeAtRunTime(HotSpotModifiers.class);
        RuntimeClassInitialization.initializeAtRunTime(LibGraalUtil.lookupClass("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream"));
        RuntimeClassInitialization.initializeAtRunTime(LibGraalUtil.lookupClass("jdk.vm.ci.hotspot.HotSpotCompiledCodeStream$Tag"));
        /* ThreadLocal in static field jdk.graal.compiler.debug.DebugContext.activated */
        RuntimeClassInitialization.initializeAtRunTime(DebugContext.class);

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

        doLegacyJVMCIInitialization();

        Path libGraalJavaHome = libgraalLoader.getJavaHome();
        GetCompilerConfig.Result configResult = GetCompilerConfig.from(libGraalJavaHome);
        for (var e : configResult.opens().entrySet()) {
            for (String source : e.getValue()) {
                LibGraalUtil.accessPackagesToClass(LibGraalUtil.Access.OPEN, getClass(), false, e.getKey(), source);
            }
        }

        Fields.setLibGraalFeatureComponents(libGraalFeatureComponents);

        EconomicMap<String, Object> libgraalObjects = (EconomicMap<String, Object>) ObjectCopier.decode(configResult.encodedConfig(), libgraalLoader.getClassLoader());
        EncodedSnippets encodedSnippets = (EncodedSnippets) libgraalObjects.get("encodedSnippets");

        // Mark all the Node classes as allocated so they are available during graph decoding.
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            access.registerAsInHeap(nodeClass.getClazz());
        }
        HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);

        List<ForeignCallSignature> foreignCallSignatures = (List<ForeignCallSignature>) libgraalObjects.get("foreignCallSignatures");
        HotSpotForeignCallLinkage.Stubs.initStubs(foreignCallSignatures);

        TruffleHostEnvironment.overrideLookup(new LibGraalTruffleHostEnvironmentLookup());
    }

    /**
     * Initialization of JVMCI code that needs to be done for JDK versions that do not include
     * JDK-8346781.
     */
    private void doLegacyJVMCIInitialization() {
        if (has8346781()) {
            return;
        }
        try {
            String rawArch = GraalServices.getSavedProperty("os.arch");
            String arch = switch (rawArch) {
                case "x86_64" -> "AMD64";
                case "amd64" -> "AMD64";
                case "aarch64" -> "aarch64";
                case "riscv64" -> "riscv64";
                default -> throw new GraalError("Unknown or unsupported arch: %s", rawArch);
            };

            ClassLoader cl = libgraalLoader.getClassLoader();
            Field cachedHotSpotJVMCIBackendFactoriesField = ObjectCopier.getField(HotSpotJVMCIRuntime.class, "cachedHotSpotJVMCIBackendFactories");
            GraalError.guarantee(cachedHotSpotJVMCIBackendFactoriesField.get(null) == null, "Expect cachedHotSpotJVMCIBackendFactories to be null");
            ServiceLoader<HotSpotJVMCIBackendFactory> load = ServiceLoader.load(HotSpotJVMCIBackendFactory.class, cl);
            List<HotSpotJVMCIBackendFactory> backendFactories = load.stream()//
                            .map(ServiceLoader.Provider::get)//
                            .filter(s -> s.getArchitecture().equals(arch))//
                            .toList();
            cachedHotSpotJVMCIBackendFactoriesField.set(null, backendFactories);
            GraalError.guarantee(backendFactories.size() == 1, "%s", backendFactories);

            var jvmciServiceLocatorCachedLocatorsField = ObjectCopier.getField(JVMCIServiceLocator.class, "cachedLocators");
            GraalError.guarantee(jvmciServiceLocatorCachedLocatorsField.get(null) == null, "Expect cachedLocators to be null");
            Iterable<JVMCIServiceLocator> serviceLocators = ServiceLoader.load(JVMCIServiceLocator.class, cl);
            List<JVMCIServiceLocator> cachedLocators = new ArrayList<>();
            serviceLocators.forEach(cachedLocators::add);
            jvmciServiceLocatorCachedLocatorsField.set(null, cachedLocators);
        } catch (Throwable e) {
            throw new GraalError(e);
        }
    }

    /**
     * Determines if the JDK runtime includes JDK-8346781. Without it, initialization of some JVMCI
     * static cache fields must be done explicitly by {@link LibGraalFeature}.
     */
    private static boolean has8346781() {
        try {
            Services.class.getField("IS_BUILDING_NATIVE_IMAGE");
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        for (var c : libGraalFeatureComponents) {
            c.duringAnalysis(access);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        optionCollector.afterAnalysis(access);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        beforeCompilationAccess = access;
    }
}
