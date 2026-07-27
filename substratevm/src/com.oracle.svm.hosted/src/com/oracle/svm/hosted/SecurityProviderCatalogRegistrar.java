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
package com.oracle.svm.hosted;

import java.security.Provider;
import java.security.Provider.Service;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;

/**
 * Realizes complete provider plans and writes their service catalog and run-time manifest.
 */
final class SecurityProviderCatalogRegistrar {
    interface Host {
        boolean isLoadableProviderClass(DuringAnalysisAccess access, Class<?> providerClass);

        Provider instantiateProvider(Class<?> providerClass);

        boolean isValidService(Service service);

        void registerService(DuringAnalysisAccess access, Service service);

        void registerSelectedConstructionPath(Class<?> providerClass);

        Object getProviderVerificationResult(Provider provider);
    }

    private final Host host;
    private final Map<String, List<Provider>> buildTimeProvidersByClassName;
    private final Set<Provider> usedProviders = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    SecurityProviderCatalogRegistrar(Host host, Map<String, List<Provider>> buildTimeProvidersByClassName) {
        this.host = host;
        this.buildTimeProvidersByClassName = buildTimeProvidersByClassName;
    }

    boolean isUsed(Provider provider) {
        return usedProviders.contains(provider);
    }

    void includeProviderClass(DuringAnalysisAccess access, Class<?> providerClass) {
        if (!host.isLoadableProviderClass(access, providerClass)) {
            registerApplicationSuppliedProviderClass(providerClass);
            return;
        }
        List<Provider> providers = buildTimeProvidersByClassName.get(providerClass.getName());
        if (providers == null) {
            providers = List.of(host.instantiateProvider(providerClass));
        }
        // Register every configured instance and the union of their service metadata.
        // §FS-security-providers.2.3
        for (Provider provider : providers) {
            registerProvider(access, provider);
            for (Service service : provider.getServices()) {
                if (host.isValidService(service)) {
                    host.registerService(access, service);
                }
            }
        }
    }

    void registerProvider(DuringAnalysisAccess access, Provider provider) {
        if (usedProviders.add(provider)) {
            RuntimeReflection.register(provider.getClass());
            if (host.isLoadableProviderClass(access, provider.getClass())) {
                host.registerSelectedConstructionPath(provider.getClass());
            }
            /* Trigger initialization of lazy field java.security.Provider.entrySet. */
            provider.entrySet();
            String providerClassName = provider.getClass().getName();
            Object verificationResult = host.getProviderVerificationResult(provider);
            SecurityProviderRuntimeState state = SecurityProviderRuntimeState.currentLayer();
            if (host.isLoadableProviderClass(access, provider.getClass())) {
                state.registerJdkConstructibleProvider(providerClassName, verificationResult);
            } else {
                state.registerApplicationSuppliedProvider(providerClassName, verificationResult);
            }
        }
    }

    private void registerApplicationSuppliedProviderClass(Class<?> providerClass) {
        // §FS-security-providers.5.3: Preserve verification without reconstructing the provider.
        List<Provider> buildTimeProviders = buildTimeProvidersByClassName.get(providerClass.getName());
        Object verificationResult = buildTimeProviders == null ? Boolean.TRUE : host.getProviderVerificationResult(buildTimeProviders.getFirst());
        SecurityProviderRuntimeState.currentLayer().registerApplicationSuppliedProvider(providerClass.getName(), verificationResult);
    }
}
