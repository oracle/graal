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
import java.util.Properties;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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

    /**
     * The implementation class controls registration and service retention. The construction class
     * is the ServiceLoader declaration that owns the selected construction path and can differ from
     * the implementation class for a provider method.
     */
    public record ConfiguredProviderInfo(String providerClassName, String constructionClassName) {
        public String effectiveConstructionClassName() {
            return constructionClassName != null ? constructionClassName : providerClassName;
        }
    }

    private final EconomicMap<String, ProviderInfo> providerInfos = ImageHeapMap.createNonLayeredMap();
    private final EconomicMap<String, ConfiguredProviderInfo> configuredProviders = ImageHeapMap.createNonLayeredMap();
    private final EconomicMap<String, Boolean> ambiguousConfiguredProviderNames = ImageHeapMap.createNonLayeredMap();

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
    public void registerJdkConstructibleProvider(String providerClassName, Object verificationResult) {
        registerProvider(providerClassName, AcquisitionKind.JDK_CONSTRUCTIBLE, verificationResult);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerApplicationSuppliedProvider(String providerClassName, Object verificationResult) {
        registerProvider(providerClassName, AcquisitionKind.APPLICATION_SUPPLIED_ONLY, verificationResult);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private synchronized void registerProvider(String providerClassName, AcquisitionKind acquisitionKind, Object verificationResult) {
        Exception verificationFailure = verificationResult instanceof Exception exception ? exception : null;
        providerInfos.put(providerClassName, merge(providerInfos.get(providerClassName), new ProviderInfo(acquisitionKind, verificationFailure)));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerConfiguredProviderName(String providerName, String providerClassName) {
        ConfiguredProviderInfo previous = configuredProviders.get(providerName);
        if (previous == null) {
            configuredProviders.put(providerName, new ConfiguredProviderInfo(providerClassName, null));
        } else if (!previous.providerClassName().equals(providerClassName)) {
            /* §FS-security-providers.7.1: Provider names are not globally unique class keys. */
            ambiguousConfiguredProviderNames.put(providerName, true);
        }
    }

    /** Records the first ServiceLoader declaration that resolved an already-configured provider. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerServiceLoadedConfiguredProvider(String providerName, String providerClassName, String constructionClassName) {
        ConfiguredProviderInfo previous = configuredProviders.get(providerName);
        if (previous != null && previous.providerClassName().equals(providerClassName) && previous.constructionClassName() == null) {
            configuredProviders.put(providerName, new ConfiguredProviderInfo(providerClassName, constructionClassName));
        }
    }

    private static ProviderInfo merge(ProviderInfo oldInfo, ProviderInfo newInfo) {
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
            result = merge(result, state.providerInfos.get(providerClassName));
        }
        return result;
    }

    public static boolean isJdkConstructible(String providerClassName) {
        ProviderInfo info = getProviderInfo(providerClassName);
        return info != null && info.acquisitionKind() == AcquisitionKind.JDK_CONSTRUCTIBLE;
    }

    public static ConfiguredProviderInfo getConfiguredProvider(String providerName) {
        ConfiguredProviderInfo result = null;
        for (SecurityProviderRuntimeState state : singletons()) {
            if (Boolean.TRUE.equals(state.ambiguousConfiguredProviderNames.get(providerName))) {
                return null;
            }
            ConfiguredProviderInfo info = state.configuredProviders.get(providerName);
            if (info != null) {
                if (result != null && !result.equals(info)) {
                    return null;
                }
                result = info;
            }
        }
        return result;
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
