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

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.util.Properties;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.SignatureUtil;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import sun.security.util.Debug;

/**
 * The class that holds various build-time and run-time structures necessary for security providers
 * (see the <a href=
 * "../../../../../../../../../../../../docs/reference-manual/native-image/JCASecurityServices.md">
 * JCA Security Services documentation</a> for details).
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public final class SecurityProvidersSupport {
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    /**
     * A map of providers, identified by their names (see {@link Provider#getName()}), and the
     * results of their verification (see javax.crypto.JceSecurity#getVerificationResult). This
     * structure is used instead of the (see javax.crypto.JceSecurity#verifyingProviders) map to
     * avoid keeping provider objects in the image heap.
     */
    private final EconomicMap<String, Object> verifiedSecurityProviders = ImageHeapMap.create("verifiedSecurityProviders");
    private final EconomicMap<String, Object> verifiedSecurityProviderClasses = ImageHeapMap.create("verifiedSecurityProviderClasses");

    private Properties savedInitialSecurityProperties;

    private Constructor<?> sunECConstructor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SecurityProvidersSupport() {
    }

    @Fold
    public static SecurityProvidersSupport singleton() {
        return ImageSingletons.lookup(SecurityProvidersSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addVerifiedSecurityProvider(String providerName, String providerClassName, Object verificationResult) {
        verifiedSecurityProviders.put(providerName, verificationResult);
        verifiedSecurityProviderClasses.put(providerClassName, verificationResult);
    }

    public Object getSecurityProviderVerificationResult(Provider provider) {
        Object result = verifiedSecurityProviderClasses.get(provider.getClass().getName());
        return result != null ? result : verifiedSecurityProviders.get(provider.getName());
    }

    /**
     * Returns {@code true} if the provider, identified by either its name (e.g., SUN) or fully
     * qualified name (e.g., sun.security.provider.Sun), was included in the native image.
     */
    public boolean isSecurityProviderIncluded(String providerName, String providerFQName) {
        return verifiedSecurityProviders.containsKey(providerName) || verifiedSecurityProviderClasses.containsKey(providerFQName);
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

    public boolean isMissingBuiltInProvider(String provName) {
        String providerName = getBuiltInProviderName(provName);
        String providerFQName = getBuiltInProviderClassName(provName);
        return providerName != null && !isSecurityProviderIncluded(providerName, providerFQName);
    }

    public static SecurityException missingBuiltInProvider(String provName) {
        String providerName = getBuiltInProviderName(provName);
        String providerFQName = getBuiltInProviderClassName(provName);
        if (providerName == null || providerFQName == null) {
            throw VMError.shouldNotReachHere("Unsupported built-in provider: " + provName);
        }
        return new SecurityException(
                        missingProviderMessage(providerName, providerFQName));
    }

    public static String missingProviderMessage(String providerName, String providerFQName) {
        return "The security provider '" + providerName + "' (" + providerFQName + ") was requested at run time but was not included in the native image. " +
                        "Run your application with the tracing agent so the provider is recorded automatically, register " + providerFQName +
                        " for reflection in reachability-metadata.json, or build with -H:Preserve=all to include all JDK providers.";
    }

    public static Provider traceProviderLookup(Provider provider) {
        if (provider == null || singleton().isMissingBuiltInProvider(provider.getName()) || singleton().isMissingBuiltInProvider(provider.getClass().getName())) {
            return null;
        }
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceMethodAccess(provider.getClass(), CONSTRUCTOR_NAME, SignatureUtil.toInternalSignature(NO_PARAMETERS),
                            ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
        }
        return provider;
    }

    public Provider loadBuiltInProvider(String provName, Debug debug) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" ->
                isSecurityProviderIncluded("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : null;
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" ->
                isSecurityProviderIncluded("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : null;
            case "SunJCE", "com.sun.crypto.provider.SunJCE" ->
                isSecurityProviderIncluded("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : null;
            case "SunJSSE", "sun.security.ssl.SunJSSE" ->
                isSecurityProviderIncluded("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : null;
            case "SunEC", "sun.security.ec.SunEC" ->
                isSecurityProviderIncluded("SunEC", "sun.security.ec.SunEC") ? allocateSunECProvider() : null;
            case "Apple", "apple.security.AppleProvider" -> {
                try {
                    Class<?> c = Class.forName("apple.security.AppleProvider");
                    if (Provider.class.isAssignableFrom(c)) {
                        yield (Provider) c.getDeclaredConstructor().newInstance();
                    }
                } catch (Exception ex) {
                    if (debug != null) {
                        debug.println("Error loading provider Apple");
                        // Checkstyle: allow System.err (for JDK compatibility)
                        ex.printStackTrace(System.err);
                        // Checkstyle: disallow System.err
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
                            "SunJSSE", "sun.security.ssl.SunJSSE",
                            "SunEC", "sun.security.ec.SunEC",
                            "Apple", "apple.security.AppleProvider" ->
                true;
            default -> false;
        };
    }
}
