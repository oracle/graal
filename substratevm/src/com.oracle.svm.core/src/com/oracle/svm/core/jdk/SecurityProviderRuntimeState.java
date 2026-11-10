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
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Provider registration and JCE verification state recorded at build time and consulted at run
 * time by {@link SecurityProviderRuntimeAccess} and {@link JceProviderVerificationSupport}.
 * Entries are keyed by provider implementation class name, rather than provider name or instance.
 * Configured names and diagnostic construction paths are stored separately from eligibility.
 * <p>
 * Each image layer owns its registrations. Run-time lookups combine active entries across all
 * layers; the hosted callbacks carry forward configuration needed to validate extension layers.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SecurityProviderRuntimeState.LayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class SecurityProviderRuntimeState {
    /** Whether an active provider registration also permits JDK-managed construction. */
    public enum AcquisitionKind {
        /**
         * Records JCE recognition and verification without the JDK-managed construction allowed by
         * {@link #JDK_CONSTRUCTIBLE}. For example, an application can supply its own provider:
         *
         * <pre>{@code
         * Provider provider = new CustomProvider(configuration);
         * Mac mac = Mac.getInstance("HmacSHA256", provider);
         * }</pre>
         *
         * Programmatic insertion via {@code Security.addProvider(provider)} remains allowed. This
         * kind registers neither reflective construction nor the service catalog; service
         * implementations still need metadata (§FS-002-security-providers.design.4.1 and
         * §FS-002-security-providers.design.4.3). No manifest entry means no recorded outcome.
         */
        APPLICATION_SUPPLIED_ONLY,
        /** The active registration also permits the JDK-selected constructor or provider factory. */
        JDK_CONSTRUCTIBLE
    }

    /** Active registration state; a null verification failure denotes successful recognition. */
    public record ProviderInfo(AcquisitionKind acquisitionKind, Exception verificationFailure) {
    }

    private record ConditionalVerificationFailure(RuntimeDynamicAccessMetadata metadata, Exception exception) {
    }

    /**
     * Recognition and JDK construction have separate conditions: an unconditional application
     * allocation must not activate a conditional construction registration. Verification failures
     * retain their own conditions so that recognizing an application-supplied object does not
     * activate a failure belonging to an inactive configured-provider registration
     * (§FS-002-security-providers.design.4.3).
     */
    private record ConditionalProviderInfo(RuntimeDynamicAccessMetadata registrationMetadata,
                    RuntimeDynamicAccessMetadata jdkConstructionMetadata, List<ConditionalVerificationFailure> verificationFailures) {
        Exception activeVerificationFailure() {
            for (ConditionalVerificationFailure failure : verificationFailures) {
                if (failure.metadata().satisfied()) {
                    return failure.exception();
                }
            }
            return null;
        }
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

    /** Names used for diagnostics only; {@code <init>} denotes a constructor. */
    public record ConstructionPath(String className, String methodName) {
    }

    /** §FS-002-security-providers.design.3.3: Diagnostic paths grant no eligibility and retain no classes. */
    private final EconomicMap<String, ConstructionPath> constructionPathsForDiagnostics = ImageHeapMap.createNonLayeredMap();

    /** Per-class registrations whose conditions are evaluated when the provider is used. */
    private final EconomicMap<String, ConditionalProviderInfo> providerInfos = ImageHeapMap.createNonLayeredMap();

    /** Configured names or implementation class names mapped to ordered resolution candidates. */
    private final EconomicMap<String, List<ConfiguredProviderInfo>> configuredProviders = ImageHeapMap.createNonLayeredMap();

    /** Build-time configuration used to validate extension layers. */
    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Map<String, String> initialProperties = new TreeMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Set<String> serviceProviders = new ConcurrentSkipListSet<>();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private Set<String> previousServiceProviders = Set.of();

    private Properties savedInitialSecurityProperties;
    private Constructor<?> sunECConstructor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SecurityProviderRuntimeState(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            initialProperties.put(key, properties.getProperty(key));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static SecurityProviderRuntimeState currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(SecurityProviderRuntimeState.class, false, true);
    }

    /**
     * Records provider service descriptors while rejecting additions to a previous layer's entries
     * (§FS-002-security-providers.design.6.2).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerServiceProviders(Set<String> providers) {
        if (!previousServiceProviders.isEmpty()) {
            Set<String> additionalProviders = new TreeSet<>(providers);
            additionalProviders.removeAll(previousServiceProviders);
            /* GR-66387: Resources cannot extend a positive service-file entry from a base layer. */
            UserError.guarantee(additionalProviders.isEmpty(),
                            "Cannot add security providers to the service file in an extension layer: %s. Register these providers in the base layer instead. Security provider service files cannot yet be extended across layers (GR-66387).",
                            String.join(", ", additionalProviders));
        }
        serviceProviders.addAll(providers);
    }

    private static SecurityProviderRuntimeState[] singletons() {
        return MultiLayeredImageSingleton.getAllLayers(SecurityProviderRuntimeState.class);
    }

    /**
     * Adds a registration to this layer, preserving independent recognition, construction, and
     * verification-failure conditions when the same implementation class is registered again.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerProvider(String providerClassName, AcquisitionKind acquisitionKind, Exception verificationFailure, RuntimeDynamicAccessMetadata metadata) {
        RuntimeDynamicAccessMetadata constructionMetadata = acquisitionKind == AcquisitionKind.JDK_CONSTRUCTIBLE ? metadata : null;
        providerInfos.put(providerClassName, mergeConditional(providerInfos.get(providerClassName),
                        new ConditionalProviderInfo(metadata, constructionMetadata,
                                        verificationFailure == null ? List.of() : List.of(new ConditionalVerificationFailure(metadata, verificationFailure)))));
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerConstructionForDiagnostics(String providerClassName, String constructionClassName, String methodName) {
        if (!constructionPathsForDiagnostics.containsKey(providerClassName)) {
            constructionPathsForDiagnostics.put(providerClassName, new ConstructionPath(constructionClassName, methodName));
        }
    }

    public static ConstructionPath getConstructionForDiagnostics(String providerClassName) {
        for (SecurityProviderRuntimeState state : singletons()) {
            ConstructionPath path = state.constructionPathsForDiagnostics.get(providerClassName);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    /** Records ordered ServiceLoader candidates under both provider and implementation names. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void registerServiceLoadedConfiguredProvider(String providerName, String providerClassName, String constructionClassName, int descriptorOrder) {
        registerServiceLoadedConfiguredProviderKey(providerName, providerClassName, constructionClassName, descriptorOrder);
        // Security properties accept either the provider name or its implementation class name
        // (§FS-002-security-providers.design.6.1).
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
        List<ConditionalVerificationFailure> verificationFailures = new ArrayList<>(oldInfo.verificationFailures());
        for (ConditionalVerificationFailure failure : newInfo.verificationFailures()) {
            if (!verificationFailures.contains(failure)) {
                verificationFailures.add(failure);
            }
        }
        return new ConditionalProviderInfo(registrationMetadata, constructionMetadata, List.copyOf(verificationFailures));
    }

    /** Construction eligibility is additive across layers; an active failure survives success. */
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

    /**
     * Combines active registrations for the object's implementation class across image layers.
     * Returns null if no recorded registration condition is satisfied.
     */
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
                result = mergeActive(result, new ProviderInfo(acquisitionKind, info.activeVerificationFailure()));
            }
        }
        return result;
    }

    /**
     * Tests construction conditions independently of recognition: an existing application-supplied
     * object can be usable while the configured provider's construction condition is unsatisfied.
     */
    static boolean isJdkConstructible(String providerClassName) {
        for (SecurityProviderRuntimeState state : singletons()) {
            ConditionalProviderInfo info = state.providerInfos.get(providerClassName);
            if (info != null && info.jdkConstructionMetadata() != null && info.jdkConstructionMetadata().satisfied()) {
                return true;
            }
        }
        return false;
    }

    /** §FS-002-security-providers.design.1.2: Select the first active descriptor candidate in order. */
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

    /** §FS-002-security-providers.design.3.3: Resolve diagnostics without constructing a provider. */
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

    /**
     * Persists configuration for extension-layer validation. Provider registrations remain in each
     * layer's image heap and are combined by run-time lookups rather than copied into a new layer.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String PROPERTY_KEYS = "securityPropertyKeys";
        private static final String PROPERTY_VALUES = "securityPropertyValues";
        private static final String SERVICE_PROVIDERS = "securityServiceProviders";

        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<SecurityProviderRuntimeState>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, SecurityProviderRuntimeState singleton) {
                    writer.writeStringList(PROPERTY_KEYS, List.copyOf(singleton.initialProperties.keySet()));
                    writer.writeStringList(PROPERTY_VALUES, List.copyOf(singleton.initialProperties.values()));
                    writer.writeStringList(SERVICE_PROVIDERS, List.copyOf(singleton.serviceProviders));
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, SecurityProviderRuntimeState singleton) {
                    List<String> keys = loader.readStringList(PROPERTY_KEYS);
                    List<String> values = loader.readStringList(PROPERTY_VALUES);
                    Map<String, String> previousProperties = new TreeMap<>();
                    for (int i = 0; i < keys.size(); i++) {
                        previousProperties.put(keys.get(i), values.get(i));
                    }
                    Set<String> changedKeys = new TreeSet<>(keys);
                    changedKeys.addAll(singleton.initialProperties.keySet());
                    changedKeys.removeIf(key -> Objects.equals(previousProperties.get(key), singleton.initialProperties.get(key)));
                    // Static-final JDK fields can retain security-property values from the base
                    // (§FS-002-security-providers.design.6.1).
                    UserError.guarantee(changedKeys.isEmpty(),
                                    "Security properties differ between image layers: %s. Use the same java.security configuration when building every layer.",
                                    String.join(", ", changedKeys));
                    singleton.previousServiceProviders = Set.copyOf(loader.readStringList(SERVICE_PROVIDERS));
                    singleton.serviceProviders.addAll(singleton.previousServiceProviders);
                }
            });
        }
    }
}
