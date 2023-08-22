/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.hosted.analysis.Inflation;

/**
 * Support for {@link ServiceLoader} on Substrate VM.
 *
 * Services are registered in the folder {@code "META-INF/services/"} using files whose name is the
 * fully qualified service interface name. We do not know which services are going to be used by a
 * native image: The parameter of {@code ServiceLoader#load} is often but not always a compile-time
 * constant that we can track. But we also cannot put all registered services into the native image.
 *
 * We therefore use the following heuristic: We add all service loader files and service
 * implementation classes when the service interfaces that are seen as reachable by the static
 * analysis.
 *
 * Each used service implementation class is added for reflection (using
 * {@link org.graalvm.nativeimage.hosted.RuntimeReflection#register(Class[])}) and for reflective
 * instantiation (using {@link RuntimeReflection#registerForReflectiveInstantiation(Class[])}).
 *
 * For each service interface, a single service loader file is added as a resource to the image. The
 * single file combines all the individual files that can come from different .jar files.
 */
@AutomaticallyRegisteredFeature
public class ServiceLoaderFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Automatically register services for run-time lookup using ServiceLoader", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseServiceLoaderFeature = new HostedOptionKey<>(true);

        @Option(help = "Comma-separated list of services that should be excluded", type = OptionType.Expert) //
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ServiceLoaderFeatureExcludeServices = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Comma-separated list of service providers that should be excluded", type = OptionType.Expert) //
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ServiceLoaderFeatureExcludeServiceProviders = new HostedOptionKey<>(
                        LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    }

    private static final Set<String> SKIPPED_SERVICES = Set.of(
                    // image builder internal ServiceLoader interfaces
                    "com.oracle.svm.hosted.NativeImageClassLoaderPostProcessing",
                    "org.graalvm.nativeimage.Platform",
                    /*
                     * Loaded in java.util.random.RandomGeneratorFactory.FactoryMapHolder, which is
                     * initialized at image build time.
                     */
                    "java.util.random.RandomGenerator",
                    "java.security.Provider",                     // see SecurityServicesFeature
                    "sun.util.locale.provider.LocaleDataMetaInfo", // see LocaleSubstitutions
                    /* Graal hotspot-specific services */
                    "jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory",
                    "org.graalvm.compiler.hotspot.CompilerConfigurationFactory",
                    "org.graalvm.compiler.hotspot.HotSpotBackendFactory",
                    "org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider$Extensions",
                    "org.graalvm.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
                    "org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory");

    // NOTE: Platform class had to be added to this list since our analysis discovers that
    // Platform.includedIn is reachable regardless of fact that it is constant folded at
    // registerPlatformPlugins method of SubstrateGraphBuilderPlugins. This issue hasn't manifested
    // before because implementation classes were instantiated using runtime reflection instead of
    // ServiceLoader (and thus weren't reachable in analysis).

    /**
     * Services that should not be processed here, for example because they are handled by
     * specialized features.
     */
    private final Set<String> servicesToSkip = new HashSet<>(SKIPPED_SERVICES);

    private static final Set<String> SKIPPED_PROVIDERS = Set.of(
                    /* Graal hotspot-specific service-providers */
                    "org.graalvm.compiler.hotspot.meta.HotSpotDisassemblerProvider",
                    /* Skip console providers until GR-44085 is fixed */
                    "jdk.internal.org.jline.JdkConsoleProviderImpl", "jdk.jshell.execution.impl.ConsoleImpl$ConsoleProviderImpl");

    private final Set<String> serviceProvidersToSkip = new HashSet<>(SKIPPED_PROVIDERS);

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.UseServiceLoaderFeature.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        servicesToSkip.addAll(Options.ServiceLoaderFeatureExcludeServices.getValue().values());
        serviceProvidersToSkip.addAll(Options.ServiceLoaderFeatureExcludeServiceProviders.getValue().values());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        accessImpl.imageClassLoader.classLoaderSupport.serviceProvidersForEach((serviceName, providers) -> {
            if (servicesToSkip.contains(serviceName)) {
                return;
            }
            Class<?> serviceClass = access.findClassByName(serviceName);
            if (serviceClass == null || serviceClass.isArray() || serviceClass.isPrimitive()) {
                return;
            }
            if (!accessImpl.getHostVM().platformSupported(serviceClass)) {
                return;
            }
            access.registerReachabilityHandler(a -> handleServiceClassIsReachable(a, serviceClass, providers), serviceClass);
        });
    }

    void handleServiceClassIsReachable(DuringAnalysisAccess access, Class<?> serviceProvider, Collection<String> providers) {
        LinkedHashSet<String> registeredProviders = new LinkedHashSet<>();
        for (String provider : providers) {
            if (serviceProvidersToSkip.contains(provider)) {
                continue;
            }
            /* Make provider reflectively instantiable */
            Class<?> providerClass = access.findClassByName(provider);

            if (providerClass == null || providerClass.isArray() || providerClass.isPrimitive()) {
                continue;
            }
            FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
            if (!accessImpl.getHostVM().platformSupported(providerClass)) {
                continue;
            }
            if (((Inflation) accessImpl.getBigBang()).getAnnotationSubstitutionProcessor().isDeleted(providerClass)) {
                /* Disallow services with implementation classes that are marked as @Deleted */
                continue;
            }

            Constructor<?> nullaryConstructor;
            try {
                nullaryConstructor = providerClass.getDeclaredConstructor();
            } catch (NoSuchMethodException | SecurityException | LinkageError e) {
                /* Skip providers that do not comply with requirements */
                nullaryConstructor = null;
            }
            if (nullaryConstructor != null) {
                RuntimeReflection.register(providerClass);
                RuntimeReflection.register(nullaryConstructor);
                RuntimeReflection.registerAllDeclaredMethods(providerClass);
                registeredProviders.add(provider);
            }
        }
        if (!registeredProviders.isEmpty()) {
            String serviceResourceLocation = "META-INF/services/" + serviceProvider.getName();
            byte[] serviceFileData = registeredProviders.stream().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
            RuntimeResourceAccess.addResource(access.getApplicationClassLoader().getUnnamedModule(), serviceResourceLocation, serviceFileData);
        }
    }
}
