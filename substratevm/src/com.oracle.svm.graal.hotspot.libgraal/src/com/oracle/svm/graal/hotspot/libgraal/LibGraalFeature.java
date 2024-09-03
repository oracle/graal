/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfigAccess;
import jdk.graal.compiler.serviceprovider.LibGraalService;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.ObjectReachableCallback;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.GraalCompilerFeature;
import com.oracle.svm.graal.hotspot.GetJNIConfig;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.jni.JNIFeature;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.ArchitectureSpecific;
import jdk.graal.compiler.core.GraalServiceThread;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.SnippetObjectConstant;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionDescriptorsMap;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

public class LibGraalFeature implements InternalFeature {

    private final OptionCollector optionCollector = new OptionCollector(HotSpotGraalOptionValuesUtil.vmOptionDescriptors);
    private HotSpotReplacementsImpl hotSpotSubstrateReplacements;

    public LibGraalFeature() {
        /* Open up all modules needed to build LibGraal image */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.graal.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "jdk.graal.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.collections");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.word");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage", "org.graalvm.nativeimage.impl");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.base");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, false, "org.graalvm.nativeimage.builder");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, LibGraalFeature.class, true, "org.graalvm.nativeimage.llvm");
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        /*
         * LibGraal needs JNIFeature for the upcalls from HotSpot and ReflectionFeature to construct
         * exceptions in jdk.internal.vm.TranslatedException.create(). However, both of these
         * features are automatically registered (i.e. annotated by @AutomaticallyRegisteredFeature)
         * so no need to explicitly add them here. Simply trying to look them up ensures that they
         * are available.
         */
        ImageSingletons.lookup(ReflectionFeature.class);
        ImageSingletons.lookup(JNIFeature.class);

        return List.of(GraalCompilerFeature.class);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibGraalFeature.class);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        access.registerObjectReachableCallback(OptionKey.class, optionCollector::doCallback);

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        GetJNIConfig.register(imageClassLoader.getClassLoader());
    }

    /**
     * Collects all {@link OptionKey}s that are reachable at run time.
     * <p>
     * This {@linkplain OptionsParser#setLibgraalOptions} initializes} the set of compiler options
     * available in libgraal to an empty set that is populated after analysis.
     */
    private static class OptionCollector implements ObjectReachableCallback<OptionKey<?>> {
        private final ConcurrentHashMap<OptionKey<?>, OptionKey<?>> options = new ConcurrentHashMap<>();

        /**
         * Libgraal compiler options. This is disjoint from {@link #vmOptionDescriptors}.
         * {@link #vmOptionDescriptors}.
         */
        private final OptionsParser.LibGraalOptionsInfo compilerOptions;

        /**
         * Libgraal VM options. This is disjoint from {@link #compilerOptions}.
         */
        private final EconomicMap<String, OptionDescriptor> vmOptionDescriptors;

        private boolean sealed;

        OptionCollector(EconomicMap<String, OptionDescriptor> vmOptionDescriptors) {
            this.compilerOptions = OptionsParser.setLibgraalOptions(OptionsParser.LibGraalOptionsInfo.create());
            this.vmOptionDescriptors = vmOptionDescriptors;
        }

        @Override
        public void doCallback(DuringAnalysisAccess access, OptionKey<?> option, ObjectScanner.ScanReason reason) {
            if (sealed) {
                GraalError.guarantee(options.contains(option), "All options must have been discovered during static analysis");
            } else {
                options.put(option, option);
            }
        }

        void afterAnalysis(AfterAnalysisAccess access) {
            sealed = true;
            for (OptionKey<?> option : options.keySet()) {
                OptionDescriptor descriptor = option.getDescriptor();
                if (descriptor.isServiceLoaded()) {
                    VMError.guarantee(access.isReachable(option.getClass()));
                    VMError.guarantee(access.isReachable(descriptor.getClass()));
                    String name = option.getName();
                    if (isCompilerOption(descriptor)) {
                        if (option instanceof RuntimeOptionKey) {
                            throw VMError.shouldNotReachHere("%s cannot be a compiler option", descriptor.getLocation());
                        }
                        compilerOptions.descriptors().put(name, descriptor);
                        String module = descriptor.getDeclaringClass().getModule().getName();
                        if (module.contains("enterprise")) {
                            compilerOptions.enterpriseOptions().add(name);
                        }
                    } else {
                        vmOptionDescriptors.put(name, descriptor);
                    }
                }
            }
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers substrateProviders,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        hotSpotSubstrateReplacements = getReplacements();
    }

    /**
     * Determines if {@code provider} should be added as a provider of a service.
     *
     * @param arch a value compatible with {@link ArchitectureSpecific#getArchitecture()}
     */
    protected boolean shouldAddProvider(Object provider, String arch) {
        if (provider instanceof ArchitectureSpecific as) {
            if (!as.getArchitecture().equals(arch)) {
                return false;
            }
        } else {
            String name = provider.getClass().getName();
            for (var knownArch : GraalHotSpotVMConfigAccess.KNOWN_ARCHITECTURES) {
                String archPackage = ".%s.".formatted(knownArch);
                if (name.contains(archPackage)) {
                    throw VMError.shouldNotReachHere("%s should implement %s", name, ArchitectureSpecific.class);
                }
            }
        }
        Module module = provider.getClass().getModule();
        return isGraalModule(module);
    }

    private static boolean isGraalModule(Module module) {
        String name = module.getName();
        if (name != null) {
            // Only services in the core graal modules should be added
            return name.equals("jdk.graal.compiler") ||
                            name.equals("jdk.graal.compiler.management") ||
                            name.equals("com.oracle.graal.graal_enterprise");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void addProviders(Map<Class<?>, List<?>> services, String arch, Class<?> service) {
        List<Object> providers = (List<Object>) services.computeIfAbsent(service, key -> new ArrayList<>());
        ModuleLayer layer = GraalServices.class.getModule().getLayer();
        ServiceLoader.load(layer, service).stream().map(ServiceLoader.Provider::get).filter(provider -> shouldAddProvider(provider, arch)).forEach(providers::add);
    }

    @SuppressWarnings({"try", "unchecked"})
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        BigBang bb = impl.getBigBang();

        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        String arch = compiler.getGraalRuntime().getTarget().arch.getName();

        ImageClassLoader imageClassLoader = impl.getImageClassLoader();
        List<Class<?>> serviceClasses = imageClassLoader.findAnnotatedClasses(LibGraalService.class, false);
        Map<Class<?>, List<?>> services = new HashMap<>();
        for (var c : serviceClasses) {
            addProviders(services, arch, c);
        }
        GraalServices.setLibgraalServices(services);

        // Instantiate the truffle compiler to ensure the backends it uses are initialized.
        List<HotSpotBackend> truffleBackends = HotSpotTruffleCompilerImpl.ensureBackendsInitialized(RuntimeOptionValues.singleton());

        // Filter out any cached services which are for a different architecture
        try {
            final Field servicesCacheField = ReflectionUtil.lookupField(Services.class, "servicesCache");
            Map<Class<?>, List<?>> servicesCache = (Map<Class<?>, List<?>>) servicesCacheField.get(null);
            filterArchitectureServices(arch, servicesCache);
            servicesCache.remove(GeneratedPluginFactory.class);

            Field cachedHotSpotJVMCIBackendFactoriesField = ReflectionUtil.lookupField(HotSpotJVMCIRuntime.class, "cachedHotSpotJVMCIBackendFactories");
            List<HotSpotJVMCIBackendFactory> cachedHotSpotJVMCIBackendFactories = (List<HotSpotJVMCIBackendFactory>) cachedHotSpotJVMCIBackendFactoriesField.get(null);
            cachedHotSpotJVMCIBackendFactories.removeIf(factory -> !factory.getArchitecture().equalsIgnoreCase(arch));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        // Force construction of all stubs so their types are known.
        HotSpotProviders providers = getReplacements().getProviders();
        registerForeignCalls(providers);

        for (Backend backend : truffleBackends) {
            registerForeignCalls((HotSpotProviders) backend.getProviders());
        }

        hotSpotSubstrateReplacements.encode(bb.getOptions());
        if (!RuntimeAssertionsSupport.singleton().desiredAssertionStatus(SnippetParameterInfo.class)) {
            // Clear the saved names if assertions aren't enabled
            hotSpotSubstrateReplacements.clearSnippetParameterNames();
        }

        // Mark all the Node classes as allocated so they are available during graph decoding.
        EncodedSnippets encodedSnippets = HotSpotReplacementsImpl.getEncodedSnippets();
        for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
            impl.getMetaAccess().lookupJavaType(nodeClass.getClazz()).registerAsInstantiated("All " + NodeClass.class.getName() + " classes are marked as instantiated eagerly.");
        }
    }

    private static void registerForeignCalls(HotSpotProviders providers) {
        HotSpotHostForeignCallsProvider foreignCalls = providers.getForeignCalls();
        foreignCalls.forEachForeignCall((sig, linkage) -> {
            if (linkage == null || linkage.isCompiledStub()) {
                if (HotSpotForeignCallLinkage.Stubs.initStub(sig)) {
                    if (linkage != null) {
                        // Construct the stub so that all types it uses are registered in
                        // SymbolicSnippetEncoder.snippetTypes
                        foreignCalls.lookupForeignCall(sig);
                    }
                }
            }
        });
    }

    private static void filterArchitectureServices(String archPackage, Map<Class<?>, List<?>> services) {
        for (List<?> list : services.values()) {
            list.removeIf(o -> {
                String name = o.getClass().getName();
                if (name.contains(".aarch64.") || name.contains(".amd64.") || name.contains(".riscv64.")) {
                    return !name.contains(archPackage);
                }
                return false;
            });
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        optionCollector.afterAnalysis(access);
        verifyReachableTruffleClasses(access);
    }

    /**
     * Verifies that the Truffle compiler does not bring Truffle API types into an image. We need to
     * use the points to analysis to verify that the Truffle API types are not reachable.
     */
    private static void verifyReachableTruffleClasses(AfterAnalysisAccess access) {
        AnalysisUniverse universe = ((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse();
        Map<AnalysisMethod, Object> seen = new LinkedHashMap<>();
        for (AnalysisMethod analysisMethod : universe.getMethods()) {
            if (analysisMethod.isDirectRootMethod() && analysisMethod.isSimplyImplementationInvoked()) {
                seen.put(analysisMethod, "direct root");
            }
            if (analysisMethod.isVirtualRootMethod()) {
                for (AnalysisMethod impl : analysisMethod.collectMethodImplementations(false)) {
                    VMError.guarantee(impl.isImplementationInvoked());
                    seen.put(impl, "virtual root");
                }
            }
        }
        Deque<AnalysisMethod> todo = new ArrayDeque<>(seen.keySet());
        SortedSet<String> disallowedTypes = new TreeSet<>();
        while (!todo.isEmpty()) {
            AnalysisMethod m = todo.removeFirst();
            String className = m.getDeclaringClass().toClassName();
            if (!isAllowedType(className)) {
                StringBuilder msg = new StringBuilder(className);
                Object reason = m;
                while (true) {
                    msg.append("<-");
                    if (reason instanceof ResolvedJavaMethod) {
                        msg.append(((ResolvedJavaMethod) reason).format("%H.%n(%p)"));
                        reason = seen.get(reason);
                    } else {
                        msg.append(reason);
                        break;
                    }
                }
                disallowedTypes.add(msg.toString());
            }
            for (InvokeInfo invoke : m.getInvokes()) {
                for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                    if (seen.putIfAbsent(callee, m) == null) {
                        todo.add(callee);
                    }
                }
            }
        }
        if (!disallowedTypes.isEmpty()) {
            throw UserError.abort("Following non allowed Truffle types are reachable on heap: %s", String.join(", ", disallowedTypes));
        }
    }

    private static boolean isAllowedType(String className) {
        if (className.startsWith("com.oracle.truffle.")) {
            return className.startsWith("com.oracle.truffle.compiler.");
        }
        return true;
    }

    static HotSpotReplacementsImpl getReplacements() {
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        HotSpotProviders originalProvider = compiler.getGraalRuntime().getHostProviders();
        return (HotSpotReplacementsImpl) originalProvider.getReplacements();
    }

    private static boolean isCompilerOption(OptionDescriptor descriptor) {
        return isGraalModule(descriptor.getDeclaringClass().getModule());
    }
}

@TargetClass(className = "jdk.vm.ci.hotspot.SharedLibraryJVMCIReflection", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_SharedLibraryJVMCIReflection {

    @Substitute
    static Object convertUnknownValue(Object object) {
        return object;
    }

    // Annotations are currently unsupported in libgraal. These substitutions will turn their use
    // into an image time build error.
    @Delete
    static native Annotation[] getClassAnnotations(String className);

    @Delete
    static native Annotation[][] getParameterAnnotations(String className, String methodName);

    @Delete
    static native Annotation[] getMethodAnnotationsInternal(ResolvedJavaMethod javaMethod);
}

@TargetClass(value = SpeculationReasonGroup.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_serviceprovider_SpeculationReasonGroup {

    /**
     * Delete this constructor to ensure {@link SpeculationReasonGroup} ids are in the libgraal
     * image and thus global across all libgraal isolates.
     */
    @Delete
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void constructor(String name, Class<?>... signature);
}

/**
 * {@link HotSpotConstantReflectionProvider#forObject} can only be used to wrap compiler objects so
 * interpose to return a {@link SnippetObjectConstant}.
 */
@TargetClass(className = "jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_HotSpotConstantReflectionProvider {

    @Substitute
    public JavaConstant forString(String value) {
        return forObject(value);
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    public JavaConstant forObject(Object value) {
        return new SnippetObjectConstant(value);
    }
}

@TargetClass(className = "jdk.vm.ci.hotspot.DirectHotSpotObjectConstantImpl", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_DirectHotSpotObjectConstantImpl {

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    void constructor(Object object, boolean compressed) {
        throw new InternalError("DirectHotSpotObjectConstantImpl unsupported");
    }
}

@TargetClass(className = "jdk.graal.compiler.hotspot.HotSpotGraalOptionValues", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_hotspot_HotSpotGraalOptionValues {

    @Substitute
    private static void notifyLibgraalOptions(Map<String, String> vmOptionSettings) {
        HotSpotGraalOptionValuesUtil.initializeOptions(vmOptionSettings);
    }

    @Substitute
    private static void printLibgraalProperties(PrintStream out, String prefix) {
        HotSpotGraalOptionValuesUtil.printOptions(out, prefix);
    }
}

/**
 * Support for {@link Target_jdk_graal_compiler_hotspot_HotSpotGraalOptionValues}.
 */
final class HotSpotGraalOptionValuesUtil {
    /**
     * Options configuring the VM in which libgraal is running.
     */
    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    static EconomicMap<String, OptionDescriptor> vmOptionDescriptors = EconomicMap.create();

    static void initializeOptions(Map<String, String> settings) {
        processXOptions(settings);
        EconomicMap<OptionKey<?>, Object> vmOptionValues = OptionValues.newOptionMap();
        Iterable<OptionDescriptors> vmOptionLoader = List.of(new OptionDescriptorsMap(vmOptionDescriptors));
        OptionsParser.parseOptions(EconomicMap.wrapMap(settings), vmOptionValues, vmOptionLoader);
        RuntimeOptionValues.singleton().update(vmOptionValues);
    }

    private static void processXOptions(Map<String, String> settings) {
        for (var i = settings.entrySet().iterator(); i.hasNext();) {
            var e = i.next();
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith("X") && value.isEmpty()) {
                String xarg = key.substring(1);
                if (XOptions.setOption(xarg)) {
                    i.remove();
                }
            }
        }
    }

    static void printOptions(PrintStream out, String prefix) {
        RuntimeOptionValues vmOptions = RuntimeOptionValues.singleton();
        Iterable<OptionDescriptors> vmOptionLoader = Collections.singletonList(new OptionDescriptorsMap(vmOptionDescriptors));
        vmOptions.printHelp(vmOptionLoader, out, prefix, true);
    }
}

@TargetClass(className = "jdk.graal.compiler.core.GraalServiceThread", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_core_GraalServiceThread {
    @Substitute()
    void beforeRun() {
        GraalServiceThread thread = SubstrateUtil.cast(this, GraalServiceThread.class);
        if (!LibGraal.attachCurrentThread(thread.isDaemon(), null)) {
            throw new InternalError("Couldn't attach to HotSpot runtime");
        }
    }

    @Substitute
    @SuppressWarnings("static-method")
    void afterRun() {
        LibGraal.detachCurrentThread(false);
    }
}

@TargetClass(className = "jdk.graal.compiler.hotspot.SymbolicSnippetEncoder", onlyWith = LibGraalFeature.IsEnabled.class)
@Delete("shouldn't appear in libgraal")
final class Target_jdk_graal_compiler_hotspot_SymbolicSnippetEncoder {
}
