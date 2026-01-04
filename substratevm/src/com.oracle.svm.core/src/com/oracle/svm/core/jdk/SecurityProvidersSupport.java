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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import sun.security.util.Debug;

/**
 * The class that holds various build-time and run-time structures necessary for security providers,
 * but only in case they are initialized at run time (see the <a href=
 * "../../../../../../../../../../../../docs/reference-manual/native-image/JCASecurityServices.md">
 * JCA Security Services documentation</a> for details).
 */
public final class SecurityProvidersSupport {
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
    private final EconomicMap<String, Object> verifiedSecurityProviders = ImageHeapMap.create("verifiedSecurityProviders");

    private Properties savedInitialSecurityProperties;

    private Constructor<?> sunECConstructor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SecurityProvidersSupport(List<String> userRequestedSecurityProviders) {
        this.userRequestedSecurityProviders.addAll(userRequestedSecurityProviders);
    }

    @Fold
    public static SecurityProvidersSupport singleton() {
        return ImageSingletons.lookup(SecurityProvidersSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addVerifiedSecurityProvider(String key, Object verificationResult) {
        verifiedSecurityProviders.put(key, verificationResult);
    }

    public Object getSecurityProviderVerificationResult(String key) {
        return verifiedSecurityProviders.get(key);
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
    public boolean isSecurityProviderRequested(String providerName, String providerFQName) {
        return verifiedSecurityProviders.containsKey(providerName) || userRequestedSecurityProviders.contains(providerFQName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSunECConstructor(Constructor<?> sunECConstructor) {
        this.sunECConstructor = sunECConstructor;
    }

    public Provider allocateSunECProvider() {
        try {
            return (Provider) sunECConstructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere("The SunEC constructor is not present.");
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSavedInitialSecurityProperties(Properties savedSecurityProperties) {
        this.savedInitialSecurityProperties = savedSecurityProperties;
    }

    public Properties getSavedInitialSecurityProperties() {
        return savedInitialSecurityProperties;
    }

    public Provider loadBuiltInProvider(String provName, Debug debug) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" ->
                isSecurityProviderRequested("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : null;
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" ->
                isSecurityProviderRequested("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : null;
            case "SunJCE", "com.sun.crypto.provider.SunJCE" ->
                isSecurityProviderRequested("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : null;
            case "SunJSSE" ->
                isSecurityProviderRequested("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : null;
            case "SunEC" ->
                isSecurityProviderRequested("SunEC", "sun.security.ec.SunEC") ? allocateSunECProvider() : null;
            case "Apple", "apple.security.AppleProvider" -> {
                try {
                    Class<?> c = Class.forName("apple.security.AppleProvider");
                    if (Provider.class.isAssignableFrom(c)) {
                        yield (Provider) c.getDeclaredConstructor().newInstance();
                    }
                } catch (Exception ex) {
                    if (debug != null) {
                        debug.println("Error loading provider Apple");
                        ex.printStackTrace();
                    }
                }
                yield null;
            }
            default -> null;
        };
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
}
