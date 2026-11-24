/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Provider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
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
import com.oracle.svm.shared.util.VMError;

/**
 * Holds the security-provider state contributed by one image layer. Runtime queries aggregate the
 * state from all layers. This support is only used when security providers are initialized at run
 * time (see the <a href=
 * "../../../../../../../../../../../../docs/reference-manual/native-image/JCASecurityServices.md">
 * JCA Security Services documentation</a> for details).
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = LayeredSecurityProvidersSupport.LayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class LayeredSecurityProvidersSupport {
    /**
     * A set of providers to be loaded using the service-loading technique at runtime, but not
     * discoverable at build-time when processing services in the feature (see
     * ServiceLoaderFeature#handleServiceClassIsReachable). This occurs when the user does not
     * explicitly request a provider, but the provider is discovered via static analysis from a
     * JCA-compliant security service used by the user's code (see
     * SecurityServicesFeature#registerServiceReachabilityHandlers).
     */
    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Set<String> markedAsNotLoaded = ConcurrentHashMap.newKeySet();

    /** Set of fully qualified provider names, required for runtime resource access. */
    private final EconomicSet<String> userRequestedSecurityProviders = EconomicSet.create();

    /**
     * A map of providers, identified by their names (see {@link Provider#getName()}), and the
     * results of their verification (see javax.crypto.JceSecurity#getVerificationResult). This
     * structure is used instead of the (see javax.crypto.JceSecurity#verifyingProviders) map to
     * avoid keeping provider objects in the image heap.
     */
    private final EconomicMap<String, Object> verifiedSecurityProviders = ImageHeapMap.createNonLayeredMap();

    /** Build-time configuration used to validate extension layers. */
    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Map<String, String> initialProperties = new TreeMap<>();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final Set<String> serviceProviders = new ConcurrentSkipListSet<>();

    @Platforms(Platform.HOSTED_ONLY.class)//
    private Set<String> previousServiceProviders = Set.of();

    @Platforms(Platform.HOSTED_ONLY.class)
    public LayeredSecurityProvidersSupport(List<String> userRequestedSecurityProviders, Properties properties) {
        this.userRequestedSecurityProviders.addAll(userRequestedSecurityProviders);
        for (String key : properties.stringPropertyNames()) {
            initialProperties.put(key, properties.getProperty(key));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static LayeredSecurityProvidersSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(LayeredSecurityProvidersSupport.class, false, true);
    }

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

    private static LayeredSecurityProvidersSupport[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(LayeredSecurityProvidersSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addVerifiedSecurityProvider(String key, Object verificationResult) {
        verifiedSecurityProviders.put(key, verificationResult);
    }

    public static Object getSecurityProviderVerificationResult(String key) {
        LayeredSecurityProvidersSupport[] singletons = layeredSingletons();
        /* Prefer the result from the latest layer that verified this provider. */
        for (int i = singletons.length - 1; i >= 0; i--) {
            Object result = singletons[i].verifiedSecurityProviders.get(key);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void markSecurityProviderAsNotLoaded(String provider) {
        markedAsNotLoaded.add(provider);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isSecurityProviderNotLoaded(String provider) {
        return markedAsNotLoaded.contains(provider);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isUserRequestedSecurityProvider(String provider) {
        return userRequestedSecurityProviders.contains(provider);
    }

    /**
     * Returns {@code true} if the provider, identified by either its name (e.g., SUN) or fully
     * qualified name (e.g., sun.security.provider.Sun), is either user-requested or reachable via a
     * security service.
     */
    public static boolean isSecurityProviderRequested(String providerName, String providerFQName) {
        for (LayeredSecurityProvidersSupport singleton : layeredSingletons()) {
            if (singleton.verifiedSecurityProviders.containsKey(providerName) || singleton.userRequestedSecurityProviders.contains(providerFQName)) {
                return true;
            }
        }
        return false;
    }

    public static String getBuiltInProviderName(String provName) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" -> "SUN";
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" -> "SunRsaSign";
            case "SunJCE", "com.sun.crypto.provider.SunJCE" -> "SunJCE";
            case "SunJSSE", "sun.security.ssl.SunJSSE" -> "SunJSSE";
            case "SunEC", "sun.security.ec.SunEC" -> "SunEC";
            case "Apple", "apple.security.AppleProvider" -> "Apple";
            default -> null;
        };
    }

    public static String getBuiltInProviderClassName(String provName) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" -> "sun.security.provider.Sun";
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" -> "sun.security.rsa.SunRsaSign";
            case "SunJCE", "com.sun.crypto.provider.SunJCE" -> "com.sun.crypto.provider.SunJCE";
            case "SunJSSE", "sun.security.ssl.SunJSSE" -> "sun.security.ssl.SunJSSE";
            case "SunEC", "sun.security.ec.SunEC" -> "sun.security.ec.SunEC";
            case "Apple", "apple.security.AppleProvider" -> "apple.security.AppleProvider";
            default -> null;
        };
    }

    public static boolean isMissingBuiltInProvider(String provName) {
        String providerName = getBuiltInProviderName(provName);
        String providerFQName = getBuiltInProviderClassName(provName);
        return providerName != null && !isSecurityProviderRequested(providerName, providerFQName);
    }

    public static SecurityException missingBuiltInProvider(String provName) {
        String providerName = getBuiltInProviderName(provName);
        String providerFQName = getBuiltInProviderClassName(provName);
        if (providerName == null || providerFQName == null) {
            throw VMError.shouldNotReachHere("Unsupported built-in provider: " + provName);
        }
        return new SecurityException(
                        "The security provider '" + providerName + "' (" + providerFQName + ") was requested at run time, " +
                                        "but it was not registered for inclusion in the native image. " +
                                        "Add the option -H:AdditionalSecurityProviders=" + providerFQName + " to the native-image build and rebuild the image.");
    }

    public static boolean isBuiltInProvider(String provName) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun",
                            "SunRsaSign", "sun.security.rsa.SunRsaSign",
                            "SunJCE", "com.sun.crypto.provider.SunJCE",
                            "SunJSSE",
                            "SunEC",
                            "Apple", "apple.security.AppleProvider" ->
                true;
            default -> false;
        };
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String PROPERTY_KEYS = "securityPropertyKeys";
        private static final String PROPERTY_VALUES = "securityPropertyValues";
        private static final String SERVICE_PROVIDERS = "securityServiceProviders";

        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<LayeredSecurityProvidersSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, LayeredSecurityProvidersSupport singleton) {
                    writer.writeStringList(PROPERTY_KEYS, List.copyOf(singleton.initialProperties.keySet()));
                    writer.writeStringList(PROPERTY_VALUES, List.copyOf(singleton.initialProperties.values()));
                    writer.writeStringList(SERVICE_PROVIDERS, List.copyOf(singleton.serviceProviders));
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, LayeredSecurityProvidersSupport singleton) {
                    List<String> keys = loader.readStringList(PROPERTY_KEYS);
                    List<String> values = loader.readStringList(PROPERTY_VALUES);
                    Map<String, String> previousProperties = new TreeMap<>();
                    for (int i = 0; i < keys.size(); i++) {
                        previousProperties.put(keys.get(i), values.get(i));
                    }
                    Set<String> changedKeys = new TreeSet<>(keys);
                    changedKeys.addAll(singleton.initialProperties.keySet());
                    changedKeys.removeIf(key -> Objects.equals(previousProperties.get(key), singleton.initialProperties.get(key)));
                    /* Static-final JDK fields can retain security-property values from the base. */
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
