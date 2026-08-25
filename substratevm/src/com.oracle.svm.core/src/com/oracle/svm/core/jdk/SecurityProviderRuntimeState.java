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

import java.lang.reflect.Constructor;
import java.security.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class SecurityProviderRuntimeState {
    public enum AcquisitionKind {
        APPLICATION_SUPPLIED_ONLY,
        JDK_CONSTRUCTIBLE
    }

    public record ProviderInfo(AcquisitionKind acquisitionKind, Exception verificationFailure) {
    }

    private record ConditionalProviderInfo(RuntimeDynamicAccessMetadata registrationMetadata,
                    RuntimeDynamicAccessMetadata jdkConstructionMetadata, Exception verificationFailure) {
    }

    /**
     * The implementation class controls registration and service retention. The construction class
     * is the ServiceLoader declaration that owns the selected construction path and can differ from
     * the implementation class for a provider method.
     */
    public record ConfiguredProviderInfo(String providerClassName, String constructionClassName, int descriptorOrder) {
        public String effectiveConstructionClassName() {
            return constructionClassName != null ? constructionClassName : providerClassName;
        }
    }

    private final EconomicMap<String, ConditionalProviderInfo> providerInfos = ImageHeapMap.createNonLayeredMap();
    private final EconomicMap<String, List<ConfiguredProviderInfo>> configuredProviders = ImageHeapMap.createNonLayeredMap();

    private Properties savedInitialSecurityProperties;
    private Constructor<?> sunECConstructor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SecurityProviderRuntimeState() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static SecurityProviderRuntimeState currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(SecurityProviderRuntimeState.class, false, true);
    }

    private static SecurityProviderRuntimeState[] singletons() {
        return MultiLayeredImageSingleton.getAllLayers(SecurityProviderRuntimeState.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerProvider(String providerClassName, AcquisitionKind acquisitionKind, Object verificationResult, RuntimeDynamicAccessMetadata metadata) {
        Exception verificationFailure = verificationResult instanceof Exception exception ? exception : null;
        RuntimeDynamicAccessMetadata constructionMetadata = acquisitionKind == AcquisitionKind.JDK_CONSTRUCTIBLE ? metadata : null;
        providerInfos.put(providerClassName, mergeConditional(providerInfos.get(providerClassName),
                        new ConditionalProviderInfo(metadata, constructionMetadata, verificationFailure)));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerConfiguredProviderName(String providerName, String providerClassName) {
        registerConfiguredProviderKey(providerName, providerClassName);
        registerConfiguredProviderKey(providerClassName, providerClassName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void registerConfiguredProviderKey(String configuredValue, String providerClassName) {
        registerConfiguredProviderCandidate(configuredValue, new ConfiguredProviderInfo(providerClassName, null, -1));
    }

    /** Records the first ServiceLoader declaration that resolves a provider name. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerServiceLoadedConfiguredProvider(String providerName, String providerClassName, String constructionClassName, int descriptorOrder) {
        registerServiceLoadedConfiguredProviderKey(providerName, providerClassName, constructionClassName, descriptorOrder);
        // §FS-002-security-providers.7.1
        // Security properties accept either the provider name or its implementation class name.
        registerServiceLoadedConfiguredProviderKey(providerClassName, providerClassName, constructionClassName, descriptorOrder);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void registerServiceLoadedConfiguredProviderKey(String configuredValue, String providerClassName, String constructionClassName, int descriptorOrder) {
        registerConfiguredProviderCandidate(configuredValue, new ConfiguredProviderInfo(providerClassName, constructionClassName, descriptorOrder));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void registerConfiguredProviderCandidate(String configuredValue, ConfiguredProviderInfo candidate) {
        List<ConfiguredProviderInfo> candidates = configuredProviders.get(configuredValue);
        if (candidates == null) {
            candidates = new ArrayList<>();
            configuredProviders.put(configuredValue, candidates);
        }
        for (int index = 0; index < candidates.size(); index++) {
            ConfiguredProviderInfo previous = candidates.get(index);
            if (previous.providerClassName().equals(candidate.providerClassName())) {
                if (previous.constructionClassName() == null && candidate.constructionClassName() != null) {
                    candidates.set(index, candidate);
                }
                return;
            }
        }
        int insertionIndex = candidates.size();
        while (insertionIndex > 0 && candidate.descriptorOrder() >= 0 &&
                        candidates.get(insertionIndex - 1).descriptorOrder() > candidate.descriptorOrder()) {
            insertionIndex--;
        }
        candidates.add(insertionIndex, candidate);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static ConditionalProviderInfo mergeConditional(ConditionalProviderInfo oldInfo, ConditionalProviderInfo newInfo) {
        if (oldInfo == null || newInfo == null) {
            return oldInfo != null ? oldInfo : newInfo;
        }
        RuntimeDynamicAccessMetadata registrationMetadata = RuntimeDynamicAccessMetadata.merge(oldInfo.registrationMetadata(), newInfo.registrationMetadata());
        RuntimeDynamicAccessMetadata constructionMetadata = RuntimeDynamicAccessMetadata.merge(oldInfo.jdkConstructionMetadata(), newInfo.jdkConstructionMetadata());
        Exception verificationFailure = oldInfo.verificationFailure() != null ? oldInfo.verificationFailure() : newInfo.verificationFailure();
        return new ConditionalProviderInfo(registrationMetadata, constructionMetadata, verificationFailure);
    }

    private static ProviderInfo mergeActive(ProviderInfo oldInfo, ProviderInfo newInfo) {
        if (oldInfo == null || newInfo == null) {
            return oldInfo != null ? oldInfo : newInfo;
        }
        AcquisitionKind acquisitionKind = oldInfo.acquisitionKind() == AcquisitionKind.JDK_CONSTRUCTIBLE || newInfo.acquisitionKind() == AcquisitionKind.JDK_CONSTRUCTIBLE
                        ? AcquisitionKind.JDK_CONSTRUCTIBLE
                        : AcquisitionKind.APPLICATION_SUPPLIED_ONLY;
        Exception verificationFailure = oldInfo.verificationFailure() != null ? oldInfo.verificationFailure() : newInfo.verificationFailure();
        return new ProviderInfo(acquisitionKind, verificationFailure);
    }

    public static ProviderInfo getProviderInfo(Provider provider) {
        return getProviderInfo(provider.getClass().getName());
    }

    private static ProviderInfo getProviderInfo(String providerClassName) {
        SecurityProviderRuntimeState[] states = singletons();
        ProviderInfo result = null;
        for (SecurityProviderRuntimeState state : states) {
            ConditionalProviderInfo info = state.providerInfos.get(providerClassName);
            if (info != null && info.registrationMetadata().satisfied()) {
                AcquisitionKind acquisitionKind = info.jdkConstructionMetadata() != null && info.jdkConstructionMetadata().satisfied()
                                ? AcquisitionKind.JDK_CONSTRUCTIBLE
                                : AcquisitionKind.APPLICATION_SUPPLIED_ONLY;
                result = mergeActive(result, new ProviderInfo(acquisitionKind, info.verificationFailure()));
            }
        }
        return result;
    }

    static boolean isJdkConstructible(String providerClassName) {
        for (SecurityProviderRuntimeState state : singletons()) {
            ConditionalProviderInfo info = state.providerInfos.get(providerClassName);
            if (info != null && info.jdkConstructionMetadata() != null && info.jdkConstructionMetadata().satisfied()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the active plan permits only use through an application-supplied provider object. */
    static boolean isApplicationSuppliedOnly(String providerClassName) {
        ProviderInfo info = getProviderInfo(providerClassName);
        return info != null && info.acquisitionKind() == AcquisitionKind.APPLICATION_SUPPLIED_ONLY;
    }

    /** §FS-002-security-providers.1.3: Select the first active descriptor candidate in order. */
    public static ConfiguredProviderInfo getConfiguredProvider(String providerName) {
        for (SecurityProviderRuntimeState state : singletons()) {
            List<ConfiguredProviderInfo> candidates = state.configuredProviders.get(providerName);
            if (candidates != null) {
                for (ConfiguredProviderInfo candidate : candidates) {
                    if (isJdkConstructible(candidate.providerClassName())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** §FS-002-security-providers.4.3: Resolve diagnostics without constructing a provider. */
    public static ConfiguredProviderInfo getConfiguredProviderForDiagnostics(String providerName) {
        for (SecurityProviderRuntimeState state : singletons()) {
            List<ConfiguredProviderInfo> candidates = state.configuredProviders.get(providerName);
            if (candidates != null && !candidates.isEmpty()) {
                return candidates.getFirst();
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSunECConstructor(Constructor<?> constructor) {
        sunECConstructor = constructor;
    }

    static Constructor<?> getSunECConstructor() {
        for (SecurityProviderRuntimeState state : singletons()) {
            if (state.sunECConstructor != null) {
                return state.sunECConstructor;
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSavedInitialSecurityProperties(Properties properties) {
        savedInitialSecurityProperties = properties;
    }

    public static Properties getSavedInitialSecurityProperties() {
        for (SecurityProviderRuntimeState state : singletons()) {
            if (state.savedInitialSecurityProperties != null) {
                return state.savedInitialSecurityProperties;
            }
        }
        return null;
    }
}
