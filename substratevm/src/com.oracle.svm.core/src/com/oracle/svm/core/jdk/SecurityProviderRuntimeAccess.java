/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.reflect.Method;
import java.security.Provider;
import java.util.function.Supplier;

import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.security.ProviderConstruction;
import com.oracle.svm.shared.security.SecurityProviderCatalog;

public final class SecurityProviderRuntimeAccess {
    private static final ThreadLocal<Boolean> LOAD_UNREGISTERED_CONFIGURED_PROVIDER = new ThreadLocal<>();

    private SecurityProviderRuntimeAccess() {
    }

    /** §FS-security-providers.4.3: Explicit configured-provider lookups retain diagnostics. */
    public static Provider loadUnregisteredConfiguredProvider(Supplier<Provider> loader) {
        Boolean previous = LOAD_UNREGISTERED_CONFIGURED_PROVIDER.get();
        LOAD_UNREGISTERED_CONFIGURED_PROVIDER.set(true);
        try {
            return loader.get();
        } finally {
            if (previous == null) {
                LOAD_UNREGISTERED_CONFIGURED_PROVIDER.remove();
            } else {
                LOAD_UNREGISTERED_CONFIGURED_PROVIDER.set(previous);
            }
        }
    }

    /** §FS-security-providers.7.1: Provider-list construction filters unregistered providers. */
    public static boolean shouldLoadUnregisteredConfiguredProvider() {
        return Boolean.TRUE.equals(LOAD_UNREGISTERED_CONFIGURED_PROVIDER.get());
    }

    /** §FS-security-providers.7.1: Load an already-resolved ServiceLoader provider directly. */
    public static Provider loadRegisteredConfiguredProvider(String providerName, String providerClassName, String constructionClassName) {
        Provider candidate;
        try {
            Class<?> constructionClass = Class.forName(constructionClassName, true, ClassLoader.getSystemClassLoader());
            candidate = constructProvider(constructionClass);
        } catch (ReflectiveOperationException | SecurityException | LinkageError ex) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName, "could not be constructed", ex);
        }
        return validateConfiguredProvider(providerName, providerClassName, constructionClassName, candidate);
    }

    static Provider validateConfiguredProvider(String providerName, String providerClassName, String constructionClassName, Provider candidate) {
        if (candidate == null) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName, "returned null from its construction path", null);
        }
        if (!providerClassName.equals(candidate.getClass().getName())) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName,
                            "returned an instance of " + candidate.getClass().getName(), null);
        }
        /* §FS-security-providers.7.1: A renamed provider does not answer the configured entry. */
        if (!providerName.equals(candidate.getName())) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName,
                            "was constructed but reports the provider name " + candidate.getName(), null);
        }
        return candidate;
    }

    private static SecurityException unusableConfiguredProvider(String providerName, String providerClassName, String constructionClassName, String problem, Throwable cause) {
        return new SecurityException("The configured security provider " + providerName + " resolved to the implementation class " + providerClassName +
                        " through the service provider class " + constructionClassName + ", which " + problem +
                        ". The provider was registered for reflection and constructible when the native image was built, so the provider class changed or its " +
                        "construction depends on run-time state. Register the provider class that answers the configured entry and rebuild the native image.",
                        cause);
    }

    /**
     * §FS-security-providers.2.2: Construct a provider through the path the JDK would use. The
     * build-time registration selected the same path, so the member this looks up is registered.
     */
    public static Provider constructProvider(Class<?> providerClass) throws ReflectiveOperationException {
        Method providerMethod = findProviderMethod(providerClass);
        if (providerMethod != null) {
            return (Provider) providerMethod.invoke(null);
        }
        return providerClass.asSubclass(Provider.class).getConstructor().newInstance();
    }

    private static Method findProviderMethod(Class<?> providerClass) {
        if (!ProviderConstruction.isInExplicitModule(providerClass)) {
            return null;
        }
        try {
            Method method = providerClass.getDeclaredMethod(ProviderConstruction.PROVIDER_METHOD_NAME);
            return ProviderConstruction.isQualifyingProviderMethod(method) ? method : null;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** §FS-security-providers.4.3: Cache misses probe type access for standard diagnostics. */
    @NeverInline("Keep the provider class name unknown to static analysis without an opaque compiler node.")
    public static void reportMissingRegistration(Class<?> providerClass) {
        String remediation = missingRegistrationRemediation();
        try {
            Class.forName(providerClass.getName(), false, providerClass.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new SecurityException(
                            "Attempted to use a security provider that was not registered for reflection at build time: " + providerClass.getName() + ". " +
                                            remediation,
                            ex);
        }
        throw new SecurityException("Attempted to use a security provider without build-time verification: " + providerClass.getName() + ". " + remediation);
    }

    private static String missingRegistrationRemediation() {
        if (FutureDefaultsOptions.explicitSecurityProviderRegistration()) {
            return "Register the provider type or a supported construction path in reachability-metadata.json and rebuild the image.";
        }
        return "Provider reflection metadata does not enable provider construction or services in compatibility mode. " +
                        "Add qualifying metadata and rebuild with --future-defaults=explicit-security-provider-registration.";
    }

    /** §FS-security-providers.6.1: Existing provider instances trace type access. */
    public static Provider traceLookup(Provider provider) {
        if (provider != null && MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceReflectionType(provider.getClass());
        }
        return provider;
    }

    /** §FS-security-providers.6.1: Successful service selection traces its provider and SPI. */
    public static void traceServiceSelection(Provider.Service service, Class<?> serviceClass) {
        if (MetadataTracer.enabled()) {
            Provider provider = service.getProvider();
            MetadataTracer tracer = MetadataTracer.singleton();
            tracer.traceReflectionType(provider.getClass());
            if (serviceClass != null) {
                tracer.traceReflectionType(serviceClass);
            }
        }
    }

    /** §FS-security-providers.6.1: JDK-managed provider lookups retain construction. */
    public static Provider traceJdkProviderLookup(Provider provider) {
        if (provider != null && MetadataTracer.enabled()) {
            Class<? extends Provider> providerClass = provider.getClass();
            MetadataTracer tracer = MetadataTracer.singleton();
            tracer.traceReflectionType(providerClass);
            if (SecurityProviderCatalog.isDirectlyConstructible(providerClass.getName())) {
                tracer.traceMethodAccess(providerClass, "<init>", "()", ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
            }
        }
        return provider;
    }

    /** §FS-security-providers.6.1: Enumeration traces every JDK-managed provider returned. */
    public static Provider[] traceJdkProviderLookups(Provider[] providers) {
        if (providers != null && MetadataTracer.enabled()) {
            for (Provider provider : providers) {
                traceJdkProviderLookup(provider);
            }
        }
        return providers;
    }
}
