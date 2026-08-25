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
package com.oracle.svm.hosted.jca;

import java.security.Provider;
import java.security.Provider.Service;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState.AcquisitionKind;

/**
 * Realizes complete provider plans and writes their service catalog and run-time manifest.
 */
final class SecurityProviderCatalogRegistrar {
    private final SecurityServicesFeature feature;
    private final Map<String, List<Provider>> buildTimeProvidersByClassName;
    private final Set<Provider> usedProviders = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    SecurityProviderCatalogRegistrar(SecurityServicesFeature feature, Map<String, List<Provider>> buildTimeProvidersByClassName) {
        this.feature = feature;
        this.buildTimeProvidersByClassName = buildTimeProvidersByClassName;
    }

    boolean isUsed(Provider provider) {
        return usedProviders.contains(provider);
    }

    static void recordConfiguredProvider(Provider provider) {
        SecurityProviderRuntimeState.currentLayer().registerConfiguredProviderName(provider.getName(), provider.getClass().getName());
    }

    static void recordServiceLoadedConfiguredProvider(Provider provider, Class<?> constructionClass, int descriptorOrder) {
        SecurityProviderRuntimeState.currentLayer().registerServiceLoadedConfiguredProvider(
                        provider.getName(), provider.getClass().getName(), constructionClass.getName(), descriptorOrder);
    }

    void includeProviderClass(DuringAnalysisAccess access, Class<?> providerClass, RuntimeDynamicAccessMetadata metadata) {
        List<Provider> providers = buildTimeProvidersByClassName.get(providerClass.getName());
        if (providers == null) {
            if (!feature.isLoadableProviderClass(access, providerClass)) {
                registerApplicationSuppliedProviderClass(providerClass, metadata);
                return;
            }
            providers = List.of(feature.instantiateProviderImplementation(providerClass));
        }
        // §FS-002-security-providers.2.3:
        // Register every configured instance and the union of their service metadata.
        for (Provider provider : providers) {
            registerProvider(access, provider, metadata);
            for (Service service : provider.getServices()) {
                if (SecurityServicesFeature.isValid(service)) {
                    feature.registerService(access, service, metadata);
                }
            }
        }
    }

    /** §FS-002-security-providers.7.3: Include one provider without its whole catalog. */
    void includeProviderForLegacyService(DuringAnalysisAccess access, Provider provider, RuntimeDynamicAccessMetadata metadata) {
        registerProvider(access, provider, metadata);
    }

    private void registerProvider(DuringAnalysisAccess access, Provider provider, RuntimeDynamicAccessMetadata metadata) {
        usedProviders.add(provider);
        feature.registerForReflection(provider.getClass(), metadata);
        if (feature.isLoadableProviderClass(access, provider.getClass())) {
            feature.registerProviderConstructionPaths(provider.getClass(), metadata);
        }
        /* Trigger initialization of lazy field java.security.Provider.entrySet. */
        provider.entrySet();
        String providerClassName = provider.getClass().getName();
        Object verificationResult = feature.getProviderVerificationResult(provider);
        recordConfiguredProvider(provider);
        AcquisitionKind acquisitionKind = feature.isLoadableProviderClass(access, provider.getClass())
                        ? AcquisitionKind.JDK_CONSTRUCTIBLE
                        : AcquisitionKind.APPLICATION_SUPPLIED_ONLY;
        writeProviderManifest(providerClassName, acquisitionKind, verificationResult, metadata);
    }

    static void registerApplicationSuppliedProviderClass(Class<?> providerClass, RuntimeDynamicAccessMetadata metadata) {
        // §FS-002-security-providers.5.3: Instantiation establishes application verification.
        // A configured instance of the class must not leak failure into provider-object use.
        writeProviderManifest(providerClass.getName(), AcquisitionKind.APPLICATION_SUPPLIED_ONLY, Boolean.TRUE, metadata);
    }

    /** The sole production chokepoint that writes provider eligibility into the run-time manifest. */
    private static void writeProviderManifest(String providerClassName, AcquisitionKind acquisitionKind, Object verificationResult, RuntimeDynamicAccessMetadata metadata) {
        SecurityProviderRuntimeState.currentLayer().registerProvider(providerClassName, acquisitionKind, verificationResult, metadata);
    }
}
