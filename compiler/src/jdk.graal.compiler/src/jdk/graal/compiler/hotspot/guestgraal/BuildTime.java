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
package jdk.graal.compiler.hotspot.guestgraal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.graal.compiler.code.DisassemblerProvider;
import jdk.graal.compiler.core.common.CompilerProfiler;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.match.MatchStatementSet;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotBackendFactory;
import jdk.graal.compiler.hotspot.HotSpotCodeCacheListener;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotInvocationPluginProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.truffle.PartialEvaluatorConfiguration;
import jdk.graal.compiler.truffle.substitutions.GraphBuilderInvocationPluginProvider;
import jdk.graal.compiler.truffle.substitutions.GraphDecoderInvocationPluginProvider;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.JVMCIServiceLocator;
import org.graalvm.collections.EconomicMap;

/**
 * This class is used at image build-time when a libgraal image gets built. Its static methods are
 * called from {@code com.oracle.svm.graal.hotspot.guestgraal.GuestGraalFeature} before static
 * analysis. These methods ensure the static field state of Graal and JVMCI classes loaded by the
 * GuestGraalClassLoader is set up correctly for getting built into libgraal.
 */
public class BuildTime {

    private static final String VALID_LOADER_NAME = "GuestGraalClassLoader";
    private static final ClassLoader LOADER = BuildTime.class.getClassLoader();

    /**
     * This method gets called from {@code GuestGraalFeature#afterRegistration()} to ensure static
     * field {@code OptionsParser#cachedOptionDescriptors} gets initialized only with
     * OptionDescriptors service providers from the classes in the GuestGraalClassLoader.
     */
    @SuppressWarnings("unused")
    public static void configureOptionsParserCachedOptionDescriptors() {
        GraalError.guarantee(VALID_LOADER_NAME.equals(LOADER.getName()),
                        "Only call this method from classloader " + VALID_LOADER_NAME);

        Iterable<OptionDescriptors> optionsLoaderIterable = OptionsParser.getOptionsLoader(LOADER);
        List<OptionDescriptors> cachedOptionDescriptors = new ArrayList<>();
        optionsLoaderIterable.forEach(cachedOptionDescriptors::add);
        OptionsParser.setCachedOptionDescriptors(List.copyOf(cachedOptionDescriptors));
    }

    @SuppressWarnings("unused")
    public static long[] getInputEdgesOffsets(Object rawNodeClass) {
        /* Used by GuestGraalFieldsOffsetsFeature.IterationMaskRecomputation */
        NodeClass<?> nodeclass = (NodeClass<?>) rawNodeClass;
        return nodeclass.getInputEdges().getOffsets();
    }

    @SuppressWarnings("unused")
    public static long[] getSuccessorEdgesOffsets(Object rawNodeClass) {
        /* Used by GuestGraalFieldsOffsetsFeature.IterationMaskRecomputation */
        NodeClass<?> nodeclass = (NodeClass<?>) rawNodeClass;
        return nodeclass.getSuccessorEdges().getOffsets();
    }

    /**
     * This method gets called from {@code GuestGraalFeature#beforeAnalysis()}. It ensures static
     * field {@code GraalServices#servicesCache} is set up correctly for Graal-usage needed in
     * libgraal. It creates a new instance of EncodedSnippets exclusively from instances of
     * GuestGraalClassLoader classes. The information how the EncodedSnippets object graph needs to
     * look like comes from parameter encodedSnippetsData. This EncodedSnippets instance is then
     * installed into static field {@code HotSpotReplacementsImpl#encodedSnippets} so that at
     * libgraal run-time Graal has access to it. Finally, it passes the FoldNodePlugin Classes to
     * the caller via a Consumer so that hosted GeneratedInvocationPlugin Graal class knows about
     * them.
     */
    @SuppressWarnings({"try", "unused", "unchecked"})
    public static void configureGraalForLibGraal(String arch,
                    Consumer<Class<?>> registerAsInHeap,
                    Consumer<List<Class<?>>> hostedGraalSetFoldNodePluginClasses,
                    String encodedGuestObjects) {
        GraalError.guarantee(VALID_LOADER_NAME.equals(LOADER.getName()),
                        "Only call this method from classloader " + VALID_LOADER_NAME);

        GraalServices.setLibgraalConfig(new GraalServices.LibgraalConfig(LOADER, arch));
        // Services that will be used in libgraal.
        GraalServices.load(CompilerConfigurationFactory.class);
        GraalServices.load(HotSpotBackendFactory.class);
        GraalServices.load(GraphBuilderInvocationPluginProvider.class);
        GraalServices.load(GraphDecoderInvocationPluginProvider.class);
        GraalServices.load(PartialEvaluatorConfiguration.class);
        GraalServices.load(HotSpotCodeCacheListener.class);
        GraalServices.load(DisassemblerProvider.class);
        GraalServices.load(HotSpotInvocationPluginProvider.class);
        GraalServices.load(DefaultHotSpotLoweringProvider.Extensions.class);
        GraalServices.load(CompilerProfiler.class);
        GraalServices.load(MatchStatementSet.class);

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

            EconomicMap<String, Object> guestObjects = (EconomicMap<String, Object>) ObjectCopier.decode(encodedGuestObjects, LOADER);
            EncodedSnippets encodedSnippets = (EncodedSnippets) guestObjects.get("encodedSnippets");

            // Mark all the Node classes as allocated so they are available during graph decoding.
            for (NodeClass<?> nodeClass : encodedSnippets.getSnippetNodeClasses()) {
                registerAsInHeap.accept(nodeClass.getClazz());
            }
            HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);

            List<ForeignCallSignature> foreignCallSignatures = (List<ForeignCallSignature>) guestObjects.get("foreignCallSignatures");
            HotSpotForeignCallLinkage.Stubs.initStubs(foreignCallSignatures);

            hostedGraalSetFoldNodePluginClasses.accept(GeneratedInvocationPlugin.getFoldNodePluginClasses());

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
                            "getJNIEnv", MHL.findStatic(RunTime.class, "getJNIEnv",
                                            methodType(long.class)),
                            "getSavedProperty", MHL.findStatic(GraalServices.class, "getSavedProperty",
                                            methodType(String.class, String.class)),
                            "ttyPrintf", MHL.findStatic(TTY.class, "printf",
                                            methodType(void.class, String.class, Object[].class)));
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }
}
