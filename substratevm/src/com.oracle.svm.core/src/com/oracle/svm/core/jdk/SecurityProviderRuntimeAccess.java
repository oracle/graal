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

import org.graalvm.nativeimage.MissingReflectionRegistrationError;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.security.ProviderConstruction;
import com.oracle.svm.shared.security.SecurityProviderCatalog;

public final class SecurityProviderRuntimeAccess {
    private SecurityProviderRuntimeAccess() {
    }

    // §AR-001-security-providers.2
    /** Apply the single JDK-acquisition filter to hosted or run-time eligibility state. */
    public static boolean passesJdkAcquisitionFilter(boolean eligible) {
        return eligible;
    }

    /** Resolve and filter a provider class through the layered run-time manifest. */
    public static boolean isJdkAcquirable(String providerClassName) {
        return passesJdkAcquisitionFilter(SecurityProviderRuntimeState.isJdkConstructible(providerClassName));
    }

    /** Determine whether a provider-list configuration can be loaded without probing it. */
    static boolean isConfiguredProviderAcquirable(String providerName) {
        SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProvider(providerName);
        String configuredProviderClassName = configuredProvider != null ? configuredProvider.providerClassName() : null;
        String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(providerName);
        String providerClassName = configuredProviderClassName != null ? configuredProviderClassName
                        : builtInProviderClassName != null ? builtInProviderClassName : providerName;
        return isJdkAcquirable(providerClassName);
    }

    /** §FS-002-security-providers.4.1 and §FS-002-security-providers.5.1. */
    public static void requireRegisteredProvider(Provider provider) {
        Class<?> providerClass = provider.getClass();
        // §FS-002-security-providers.5.1: A class defined at run time cannot carry build-time
        // metadata, so the JDK semantics for the factory call apply unchanged.
        if (DynamicHub.fromClass(providerClass).isRuntimeLoaded()) {
            return;
        }
        if (SecurityProviderRuntimeState.getProviderInfo(provider) == null) {
            reportMissingRegistration(providerClass);
        }
    }

    // §FS-002-security-providers.7.1
    /** Load an already-resolved ServiceLoader provider directly. */
    public static Provider loadRegisteredConfiguredProvider(String providerName, String providerClassName, String constructionClassName, String argument) {
        Provider candidate;
        /* §FS-002-security-providers.6.1: A filtered lookup must not trace providers constructed
         * while materializing the full provider list. The acquisition boundary traces the selected
         * provider, so internal construction must not be recorded as application use. */
        try (var _ = MetadataTracer.disableTracing("security provider list construction")) {
            Class<?> constructionClass = Class.forName(constructionClassName, true, ClassLoader.getSystemClassLoader());
            candidate = constructProvider(constructionClass);
            validateConstructedProvider(providerName, providerClassName, constructionClassName, candidate);
            candidate = configureProvider(candidate, argument);
            if (candidate != null && !providerClassName.equals(candidate.getClass().getName()) &&
                            !isJdkAcquirable(candidate.getClass().getName())) {
                throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName,
                                "configured to the unregistered implementation class " + candidate.getClass().getName(), null);
            }
        } catch (MissingReflectionRegistrationError ex) {
            throw ex;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName, "could not be constructed or configured", ex);
        }
        return validateConfiguredProvider(providerName, providerClassName, constructionClassName, candidate);
    }

    // §FS-002-security-providers.1.3
    /** Apply a configured entry's argument and retain the returned provider instance. */
    static Provider configureProvider(Provider candidate, String argument) {
        return candidate != null && !argument.isEmpty() ? candidate.configure(argument) : candidate;
    }

    static Provider validateConfiguredProvider(String providerName, String providerClassName, String constructionClassName, Provider candidate) {
        if (candidate == null) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName, "returned null from its construction path", null);
        }
        return candidate;
    }

    private static void validateConstructedProvider(String providerName, String providerClassName, String constructionClassName, Provider candidate) {
        if (candidate == null) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName, "returned null from its construction path", null);
        }
        if (!providerClassName.equals(candidate.getClass().getName())) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName,
                            "returned an instance of " + candidate.getClass().getName() + " before configuration", null);
        }
        if (!providerName.equals(providerClassName) && !providerName.equals(candidate.getName())) {
            throw unusableConfiguredProvider(providerName, providerClassName, constructionClassName,
                            "reported the provider name " + candidate.getName() + " before configuration", null);
        }
    }

    private static SecurityException unusableConfiguredProvider(String providerName, String providerClassName, String constructionClassName, String problem, Throwable cause) {
        return new SecurityException("The configured security provider " + providerName + " resolved to the implementation class " + providerClassName +
                        " through the service provider class " + constructionClassName + ", which " + problem +
                        ". The provider was registered for reflection and constructible when the native image was built, so the provider class changed or its " +
                        "construction depends on run-time state. Register the provider class that answers the configured entry and rebuild the native image.",
                        cause);
    }

    // §FS-002-security-providers.2.2
    /** Construct a provider through the path the JDK would use. */
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

    /** §FS-002-security-providers.4.3: Cache misses probe type access for standard diagnostics. */
    @NeverInline("Keep the provider class name unknown to static analysis without an opaque compiler node.")
    public static void reportMissingRegistration(Class<?> providerClass) {
        reportMissingRegistration(providerClass.getName());
    }

    /** Report an omitted configured provider without loading or caching its class. */
    @NeverInline("Keep the provider class name unknown to static analysis without an opaque compiler node.")
    public static void reportMissingRegistration(String providerClassName) {
        String remediation = missingRegistrationRemediation();
        if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
            StackTraceElement responsibleClass = findExactMetadataCaller();
            if (responsibleClass != null) {
                MissingReflectionRegistrationUtils.reportClassAccess(providerClassName, responsibleClass);
            }
        }
        throw new SecurityException(
                        "Attempted to use a security provider that was not registered for reflection at build time: " + providerClassName + ". " + remediation);
    }

    /** §FS-002-security-providers.4.3: Diagnose a configured omission without constructing it. */
    public static void reportMissingConfiguredProvider(String providerName) {
        // §FS-002-security-providers.7.3: Legacy modes preserve their earlier lookup failures.
        if (!FutureDefaultsOptions.metadataSecurityProviderRegistration()) {
            return;
        }
        SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProviderForDiagnostics(providerName);
        String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(providerName);
        String providerClassName = configuredProvider != null ? configuredProvider.providerClassName()
                        : builtInProviderClassName;
        // §FS-002-security-providers.4.3: Provider-object-only plans remain ordinary list misses.
        boolean applicationSuppliedOnly = isApplicationSuppliedOnlyDiagnostic(providerClassName, builtInProviderClassName);
        if (providerClassName != null && !isJdkAcquirable(providerClassName) &&
                        !applicationSuppliedOnly) {
            reportMissingRegistration(providerClassName);
        }
    }

    static boolean isApplicationSuppliedOnlyDiagnostic(String providerClassName, String builtInProviderClassName) {
        return providerClassName != null && builtInProviderClassName == null &&
                        SecurityProviderRuntimeState.isApplicationSuppliedOnly(providerClassName);
    }

    private static StackTraceElement findExactMetadataCaller() {
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (MissingRegistrationSupport.singleton().reportMissingRegistrationErrors(frame)) {
                return frame;
            }
        }
        return null;
    }

    private static String missingRegistrationRemediation() {
        if (FutureDefaultsOptions.metadataSecurityProviderRegistration()) {
            return "Register the provider type or a supported construction path in reachability-metadata.json and rebuild the image.";
        }
        return "Provider reflection metadata does not enable provider construction or services in compatibility mode. " +
                        "Add qualifying metadata and rebuild with --future-defaults=metadata-security-provider-registration.";
    }

    // §FS-002-security-providers.6.1
    /** Successful service selection traces its JDK-managed provider and SPI. */
    public static void traceServiceSelection(Provider provider, Class<?> serviceClass) {
        if (MetadataTracer.enabled()) {
            traceJdkProviderLookup(provider);
            if (serviceClass != null) {
                MetadataTracer.singleton().traceReflectionType(serviceClass);
            }
        }
    }

    /** §FS-002-security-providers.6.1: JDK-managed provider lookups retain construction. */
    public static Provider traceJdkProviderLookup(Provider provider) {
        SecurityProviderRuntimeState.ProviderInfo providerInfo = provider == null ? null : SecurityProviderRuntimeState.getProviderInfo(provider);
        if (providerInfo != null && providerInfo.acquisitionKind() == SecurityProviderRuntimeState.AcquisitionKind.JDK_CONSTRUCTIBLE && MetadataTracer.enabled()) {
            Class<? extends Provider> providerClass = provider.getClass();
            MetadataTracer tracer = MetadataTracer.singleton();
            tracer.traceReflectionType(providerClass);
            if (SecurityProviderCatalog.isDirectlyConstructible(providerClass.getName())) {
                tracer.traceMethodAccess(providerClass, "<init>", "()", ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
            }
        }
        return provider;
    }

    /** §FS-002-security-providers.6.1: Enumeration traces every JDK-managed provider returned. */
    public static Provider[] traceJdkProviderLookups(Provider[] providers) {
        if (providers != null && MetadataTracer.enabled()) {
            for (Provider provider : providers) {
                traceJdkProviderLookup(provider);
            }
        }
        return providers;
    }
}
