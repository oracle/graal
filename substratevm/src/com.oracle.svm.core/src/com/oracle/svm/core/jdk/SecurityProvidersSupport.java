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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import sun.security.util.Debug;

/**
 * AR-security-providers: Security Provider Architecture
 *
 * This class holds the build-time and run-time structures for JCA security-provider inclusion,
 * verification, and metadata tracing. The required behavior is specified separately by
 * §FS-security-providers.
 * See the <a href="../../../../../../../../../../../../docs/reference-manual/native-image/JCASecurityServices.md">
 * JCA Security Services documentation</a> for the user-facing configuration model.
 *
 * ## 1. Build-Time Inclusion and Verification
 *
 * {@code SecurityServicesFeature} coordinates two analysis inputs: subtype reachability discovers
 * candidate {@link Provider} classes, and JCA factory reachability discovers used service types.
 * For a provider candidate, the feature queries the reflection registry for type, constructor, or
 * factory-method registration. It instantiates accepted candidates through the declared nullary
 * constructor or static {@code provider()} method, then registers their service implementation
 * classes. The service-driven path calls the same service-registration machinery independently.
 * These mechanisms implement §FS-security-providers.2 and §FS-security-providers.3.
 *
 * During analysis, the feature obtains each included provider's JCE verification result and stores
 * it in this image singleton, keyed by provider class name and provider name. The feature removes
 * those entries from the JDK's object-keyed cache so build-time provider instances do not remain
 * reachable in the image heap.
 *
 * ## 2. Run-Time Verification-Result Lookup
 *
 * The {@code javax.crypto.JceSecurity} substitutions consult the maps in this singleton when the
 * JDK verification cache has no entry. {@link Boolean#TRUE} encodes successful verification; an
 * exception object encodes the original verification failure. This lets run-time JCE checks reuse
 * the build-time result without retaining the provider instance or repeating JAR verification.
 *
 * ## 3. Type and Constructor Tracing
 *
 * The metadata tracer represents provider loading as two distinct accesses: a dynamic
 * {@link Class#forName(String)} lookup records the type, and constructor access records how the
 * provider is instantiated. Because the JCE lookup already has a provider instance,
 * {@link #traceProviderLookup(Provider)} emits the constructor access directly instead of creating
 * another provider. This is the native-image counterpart of the Tracing Agent's provider event.
 *
 * On a verification-result cache miss, {@link #reportMissingProviderRegistration(Class)} performs
 * an opaque, non-initializing {@code Class.forName} lookup using the provider's class loader. The
 * opaque name prevents image-build analysis from removing the probe. The lookup enters the regular
 * missing-reflection-registration machinery required by
 * §FS-security-providers.5.3. A successful lookup without a verification result is
 * an internal invariant violation.
 *
 * ## 4. Run-Time Provider Construction
 *
 * With run-time provider initialization, the {@code ProviderConfig} substitutions ask this class
 * to construct included JDK providers directly. Other configured providers follow the JDK's
 * reflective loading path. The substitutions preserve the JDK's provider-list state, recursion
 * guard, and retry counter, while the verification maps remain independent of provider creation.
 *
 * ## 5. Concurrent Analysis Registration
 *
 * Provider subtype callbacks add candidates to a concurrent set and mark it changed. A serialized
 * security-services analysis pass consumes new candidates and requests another analysis iteration
 * when processing registers new reflection or JNI metadata. The callbacks do not request analysis
 * iterations themselves, so concurrent discovery cannot race with iteration scheduling or lose
 * registrations pending for a later iteration.
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addIncludedSecurityProviderClass(String providerClassName) {
        verifiedSecurityProviderClasses.put(providerClassName, Boolean.TRUE);
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

    /** §AR-security-providers.3: Cache misses probe type access for standard diagnostics. */
    public static void reportMissingProviderRegistration(Class<?> providerClass) {
        try {
            Class.forName(GraalDirectives.opaque(providerClass.getName()), false, providerClass.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere("A reachable security provider class was not found.", ex);
        }
        throw VMError.shouldNotReachHere("A security provider without a verification result was registered for reflection: " + providerClass.getName());
    }

    /** §AR-security-providers.3: Existing providers trace constructor access directly. */
    public static Provider traceProviderLookup(Provider provider) {
        if (provider == null) {
            return null;
        }
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceMethodAccess(provider.getClass(), CONSTRUCTOR_NAME, SignatureUtil.toInternalSignature(NO_PARAMETERS),
                            ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
        }
        return provider;
    }

    private static Provider loadProviderReflectively(String providerClassName, Debug debug) {
        try {
            Class<?> providerClass = Class.forName(GraalDirectives.opaque(providerClassName));
            return (Provider) providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            if (debug != null) {
                debug.println("Error loading provider " + providerClassName);
                // Checkstyle: allow System.err (for JDK compatibility)
                ex.printStackTrace(System.err);
                // Checkstyle: disallow System.err
            }
            return null;
        }
    }

    public Provider loadBuiltInProvider(String provName, Debug debug) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" ->
                isSecurityProviderIncluded("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : loadProviderReflectively("sun.security.provider.Sun", debug);
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" ->
                isSecurityProviderIncluded("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : loadProviderReflectively("sun.security.rsa.SunRsaSign", debug);
            case "SunJCE", "com.sun.crypto.provider.SunJCE" ->
                isSecurityProviderIncluded("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : loadProviderReflectively("com.sun.crypto.provider.SunJCE", debug);
            case "SunJSSE", "sun.security.ssl.SunJSSE" ->
                isSecurityProviderIncluded("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : loadProviderReflectively("sun.security.ssl.SunJSSE", debug);
            case "SunEC", "sun.security.ec.SunEC" ->
                isSecurityProviderIncluded("SunEC", "sun.security.ec.SunEC") ? allocateSunECProvider() : loadProviderReflectively("sun.security.ec.SunEC", debug);
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
