/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.ServiceCatalogSupport;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.analysis.Inflation;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

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
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> ServiceLoaderFeatureExcludeServices = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        @Option(help = "Comma-separated list of service providers that should be excluded", type = OptionType.Expert) //
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> ServiceLoaderFeatureExcludeServiceProviders = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

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
                    "jdk.graal.compiler.hotspot.CompilerConfigurationFactory",
                    "jdk.graal.compiler.hotspot.HotSpotBackendFactory",
                    "jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider$Extensions",
                    "jdk.graal.compiler.hotspot.meta.HotSpotInvocationPluginProvider",
                    "jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory");

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
                    "jdk.graal.compiler.hotspot.meta.HotSpotDisassemblerProvider",
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
            Class<?> serviceClass = access.findClassByName(serviceName);
            boolean skipService = false;
            /* If the service should not end up in the image, we remove all the providers with it */
            Collection<String> providersToSkip = providers;
            if (servicesToSkip.contains(serviceName)) {
                skipService = true;
            } else if (serviceClass == null || serviceClass.isArray() || serviceClass.isPrimitive()) {
                skipService = true;
            } else if (!accessImpl.getHostVM().platformSupported(serviceClass)) {
                skipService = true;
            } else {
                providersToSkip = providers.stream().filter(serviceProvidersToSkip::contains).collect(Collectors.toList());
                if (!providersToSkip.isEmpty()) {
                    skipService = true;
                }
            }
            if (skipService) {
                ServiceCatalogSupport.singleton().removeServicesFromServicesCatalog(serviceName, new HashSet<>(providersToSkip));
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

            /*
             * Find either a public static provider() method or a nullary constructor (or both).
             * Skip providers that do not comply with requirements.
             *
             * See ServiceLoader#loadProvider and ServiceLoader#findStaticProviderMethod.
             */
            Constructor<?> nullaryConstructor = null;
            Method nullaryProviderMethod = null;
            try {
                /* Only look for a provider() method if provider class is in an explicit module. */
                if (providerClass.getModule().isNamed() && !providerClass.getModule().getDescriptor().isAutomatic()) {
                    for (Method method : providerClass.getDeclaredMethods()) {
                        if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) &&
                                        method.getParameterCount() == 0 && method.getName().equals("provider")) {
                            if (nullaryProviderMethod == null) {
                                nullaryProviderMethod = method;
                            } else {
                                /* There must be at most one public static provider() method. */
                                nullaryProviderMethod = null;
                                break;
                            }
                        }
                    }
                }

                Constructor<?> constructor = providerClass.getDeclaredConstructor();
                if (Modifier.isPublic(constructor.getModifiers())) {
                    nullaryConstructor = constructor;
                }
            } catch (NoSuchMethodException | SecurityException | LinkageError e) {
                // ignore
            }
            if (nullaryConstructor != null || nullaryProviderMethod != null) {
                RuntimeReflection.register(providerClass);
                if (nullaryConstructor != null) {
                    RuntimeReflection.register(nullaryConstructor);
                } else {
                    RuntimeReflection.registerConstructorLookup(providerClass);
                }
                if (nullaryProviderMethod != null) {
                    RuntimeReflection.register(nullaryProviderMethod);
                } else {
                    /*
                     * If there's no declared public provider() method, register it as negative
                     * lookup to avoid throwing a MissingReflectionRegistrationError at run time.
                     */
                    RuntimeReflection.registerMethodLookup(providerClass, "provider");
                }
            }
            /*
             * Register the provider in both cases: when it is JCA-compliant (has a nullary
             * constructor or a provider method) or when it lacks both. If neither is present, a
             * ServiceConfigurationError will be thrown at runtime, consistent with HotSpot
             * behavior.
             */
            registeredProviders.add(provider);
        }
        if (!registeredProviders.isEmpty()) {
            String serviceResourceLocation = "META-INF/services/" + serviceProvider.getName();
            byte[] serviceFileData = registeredProviders.stream().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
            RuntimeResourceAccess.addResource(access.getApplicationClassLoader().getUnnamedModule(), serviceResourceLocation, serviceFileData);
        }
    }
}
