/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.core.ArchitectureSpecific;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.JVMCIServiceLocator;

/**
 * This class is used at image build-time when a libgraal image gets built. Its static methods are
 * called from {@code com.oracle.svm.graal.hotspot.libgraal.LibGraalFeature} before static analysis.
 * These methods ensure the static field state of Graal and JVMCI classes loaded by the
 * LibGraalClassLoader is set up correctly for getting built into libgraal.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class BuildTime {

    private static final String VALID_LOADER_NAME = "LibGraalClassLoader";
    private static final ClassLoader LOADER = BuildTime.class.getClassLoader();

    /**
     * Creates and registers the data structures used for looking up libgraal compiler options and
     * determining which of them are enterprise options.
     */
    @SuppressWarnings("unused")
    public static Object initLibgraalOptions() {
        return OptionsParser.setLibgraalOptions(OptionsParser.LibGraalOptionsInfo.create());
    }

    /**
     * Processes the entries in {@code optionObjects} and adds their
     * {@linkplain OptionDescriptor#isServiceLoaded() non-service loaded} descriptors to
     * {@code descriptorsObject}.
     *
     * @param optionObjects a list of {@link OptionKey} objects
     * @param optionsInfoObject the value returned by {@link #initLibgraalOptions()}
     * @param modules unmodifiable map from the {@linkplain Class#forName(String) name} of a class
     *            to the name of its enclosing module.
     * @return the {@link OptionDescriptor} objects added to {@code descriptorsObject}
     */
    @SuppressWarnings("unused")
    public static Iterable<?> finalizeLibgraalOptions(List<Object> optionObjects, Object optionsInfoObject, Map<String, String> modules) {
        GraalError.guarantee(VALID_LOADER_NAME.equals(LOADER.getName()),
                        "Only call this method from classloader " + VALID_LOADER_NAME);
        OptionsParser.LibGraalOptionsInfo optionsInfo = (OptionsParser.LibGraalOptionsInfo) optionsInfoObject;
        for (Object optionObject : optionObjects) {
            OptionKey<?> option = (OptionKey<?>) optionObject;
            OptionDescriptor descriptor = option.getDescriptor();
            if (descriptor.isServiceLoaded()) {
                String name = option.getName();
                optionsInfo.descriptors().put(name, descriptor);
                String module = modules.get(descriptor.getDeclaringClass().getName());
                if (module.contains("enterprise")) {
                    optionsInfo.enterpriseOptions().add(name);
                }
            }
        }
        return optionsInfo.descriptors().getValues();
    }

    @SuppressWarnings("unused")
    public static long[] getInputEdgesOffsets(Object rawNodeClass) {
        /* Used by LibGraalFieldsOffsetsFeature.IterationMaskRecomputation */
        NodeClass<?> nodeclass = (NodeClass<?>) rawNodeClass;
        return nodeclass.getInputEdges().getOffsets();
    }

    @SuppressWarnings("unused")
    public static long[] getSuccessorEdgesOffsets(Object rawNodeClass) {
        /* Used by LibGraalFieldsOffsetsFeature.IterationMaskRecomputation */
        NodeClass<?> nodeclass = (NodeClass<?>) rawNodeClass;
        return nodeclass.getSuccessorEdges().getOffsets();
    }

    @SuppressWarnings("unchecked")
    private static void addProviders(Map<Class<?>, List<?>> services, String arch, Class<?> service) {
        List<Object> providers = (List<Object>) services.computeIfAbsent(service, key -> new ArrayList<>());
        for (Object provider : ServiceLoader.load(service, LOADER)) {
            if (provider instanceof ArchitectureSpecific as && !as.getArchitecture().equals(arch)) {
                // Skip provider for another architecture
                continue;
            }
            providers.add(provider);
        }
    }

    /**
     * Configures the static state needed for libgraal.
     *
     * @param arch a value compatible with {@link ArchitectureSpecific#getArchitecture()}
     */
    @SuppressWarnings({"try", "unused", "unchecked"})
    public static void configureGraalForLibGraal(String arch,
                    List<Class<?>> guestServiceClasses,
                    Consumer<Class<?>> registerAsInHeap,
                    String nativeImageLocationQualifier,
                    byte[] encodedGuestObjects) {
        GraalError.guarantee(VALID_LOADER_NAME.equals(LOADER.getName()),
                        "Only call this method from classloader " + VALID_LOADER_NAME);

        Map<Class<?>, List<?>> services = new HashMap<>();
        guestServiceClasses.forEach(c -> addProviders(services, arch, c));
        GraalServices.setLibgraalServices(services);

        CompilerConfigurationFactory.setNativeImageLocationQualifier(nativeImageLocationQualifier);

        try {
            Field cachedHotSpotJVMCIBackendFactoriesField = ObjectCopier.getField(HotSpotJVMCIRuntime.class, "cachedHotSpotJVMCIBackendFactories");
            GraalError.guarantee(cachedHotSpotJVMCIBackendFactoriesField.get(null) == null, "Expect cachedHotSpotJVMCIBackendFactories to be null");
            ServiceLoader<HotSpotJVMCIBackendFactory> load = ServiceLoader.load(HotSpotJVMCIBackendFactory.class, LOADER);
            List<HotSpotJVMCIBackendFactory> backendFactories = load.stream()//
                            .map(ServiceLoader.Provider::get)//
                            .filter(s -> s.getArchitecture().equals(arch))//
                            .toList();
            cachedHotSpotJVMCIBackendFactoriesField.set(null, backendFactories);
            GraalError.guarantee(backendFactories.size() == 1, "%s", backendFactories);

            var jvmciServiceLocatorCachedLocatorsField = ObjectCopier.getField(JVMCIServiceLocator.class, "cachedLocators");
            GraalError.guarantee(jvmciServiceLocatorCachedLocatorsField.get(null) == null, "Expect cachedLocators to be null");
            Iterable<JVMCIServiceLocator> serviceLocators = ServiceLoader.load(JVMCIServiceLocator.class, LOADER);
            List<JVMCIServiceLocator> cachedLocators = new ArrayList<>();
            serviceLocators.forEach(cachedLocators::add);
            jvmciServiceLocatorCachedLocatorsField.set(null, cachedLocators);

            EconomicMap<String, Object> libgraalObjects = (EconomicMap<String, Object>) ObjectCopier.decode(encodedGuestObjects, LOADER);
            EncodedSnippets encodedSnippets = (EncodedSnippets) libgraalObjects.get("encodedSnippets");

            // Mark all the Node classes as allocated so they are available during graph decoding.
            for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
                registerAsInHeap.accept(nodeClass.getClazz());
            }
            HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);

            List<ForeignCallSignature> foreignCallSignatures = (List<ForeignCallSignature>) libgraalObjects.get("foreignCallSignatures");
            HotSpotForeignCallLinkage.Stubs.initStubs(foreignCallSignatures);
        } catch (ReflectiveOperationException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static final Lookup MHL = MethodHandles.lookup();

    /**
     * Gets method handles to call Graal and JVMCI methods.
     *
     * @return a named set of handles
     */
    public static Map<String, MethodHandle> getRuntimeHandles() {
        try {
            return Map.of("compileMethod", MHL.findStatic(RunTime.class, "compileMethod",
                            methodType(long.class, long.class,
                                            boolean.class, boolean.class, boolean.class, boolean.class,
                                            long.class, int.class, int.class,
                                            String.class, BiConsumer.class, Supplier.class)),
                            "hashConstantOopFields", MHL.findStatic(RunTime.class, "hashConstantOopFields",
                                            methodType(long.class, long.class, boolean.class, int.class, int.class,
                                                            boolean.class, Runnable.class)),
                            "getJNIEnv", MHL.findStatic(RunTime.class, "getJNIEnv",
                                            methodType(long.class)),
                            "attachCurrentThread", MHL.findStatic(RunTime.class, "attachCurrentThread",
                                            methodType(boolean.class, boolean.class, long[].class)),
                            "detachCurrentThread", MHL.findStatic(RunTime.class, "detachCurrentThread",
                                            methodType(boolean.class, boolean.class)),
                            "getSavedProperty", MHL.findStatic(GraalServices.class, "getSavedProperty",
                                            methodType(String.class, String.class)),
                            "ttyPrintf", MHL.findStatic(TTY.class, "printf",
                                            methodType(void.class, String.class, Object[].class)));
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }
}
