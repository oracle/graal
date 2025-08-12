/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.runtimeinit;

import java.net.URL;
import java.security.Provider;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.SecurityProvidersInitializedAtRunTime;
import com.oracle.svm.core.jdk.SecurityProvidersSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

@TargetClass(value = java.security.Security.class, onlyWith = SecurityProvidersInitializedAtRunTime.class)
final class Target_java_security_Security {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;
}

@TargetClass(value = java.security.Security.class, innerClass = "SecPropLoader", onlyWith = SecurityProvidersInitializedAtRunTime.class)
final class Target_java_security_Security_SecPropLoader {

    /**
     * On HotSpot, this method loads the properties from the JDK's default location. Since we do not
     * have a full JDK at run time, we use a snapshot of these values captured at build time from
     * the host JVM.
     */
    @Substitute
    private static void loadMaster() {
        Target_java_security_Security.props = SecurityProvidersSupport.singleton().getSavedInitialSecurityProperties();
    }
}

/**
 * The {@code javax.crypto.JceSecurity#verificationResults} cache is initialized by the
 * SecurityServicesFeature at build time, for all registered providers. The cache is used by
 * {@code javax.crypto.JceSecurity#canUseProvider} at run time to check whether a provider is
 * properly signed and can be used by JCE. It does that via jar verification which we cannot
 * support.
 */
@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * Map<Provider, ?> of providers that have already been verified. A value of PROVIDER_VERIFIED
     * indicates successful verification. Otherwise, the value is the Exception that caused the
     * verification to fail.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Object, Object> verificationResults;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Map<Class<?>, URL> codeBaseCacheRef = new WeakHashMap<>();

    @Substitute
    static Exception getVerificationResult(Provider p) {
        /* The verification results map key is an identity wrapper object. */
        Object o = SecurityProvidersSupport.singleton().getSecurityProviderVerificationResult(p.getName());
        if (o == Boolean.TRUE) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /*
         * If the verification result is not found in the verificationResults map, HotSpot will
         * attempt to verify the provider. This requires accessing the code base, which isn't
         * supported in Native Image, so we need to fail. We could either fail here or substitute
         * getCodeBase() and fail there, but handling it here is a cleaner approach.
         */
        throw new SecurityException(
                        "Attempted to verify a provider that was not registered at build time: " + p + ". " +
                                        "All security providers must be registered and verified during native image generation. " +
                                        "Try adding the option: -H:AdditionalSecurityProviders=" + p + " and rebuild the image.");
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig {

    @Alias //
    private String provName;

    @Alias//
    private static sun.security.util.Debug debug;

    @Alias//
    private Provider provider;

    @Alias//
    private boolean isLoading;

    @Alias//
    private int tries;

    @Alias
    private native Provider doLoadProvider();

    @Alias
    private native boolean shouldLoad();

    /**
     * The `entrypoint` for allocating security providers at runtime. The implementation is copied
     * from the JDK with a small tweak to filter out providers that are neither user-requested nor
     * reachable via a security service.
     */
    @Substitute
    @SuppressFBWarnings(value = "DC_DOUBLECHECK", justification = "This double-check is implemented correctly and is intentional.")
    Provider getProvider() {
        if (provider != null) {
            return provider;
        }
        synchronized (this) {
            if (provider != null) {
                return provider;
            }
            if (!shouldLoad()) {
                return null;
            }
            // Create providers which are in java.base directly
            if (SecurityProvidersSupport.isBuiltInProvider(provName)) {
                provider = SecurityProvidersSupport.singleton().loadBuiltInProvider(provName, debug);
            } else {
                if (isLoading) {
                    /*
                     * This method is synchronized, so this can only happen if there is recursion.
                     */
                    if (debug != null) {
                        debug.println("Recursion loading provider: " + this);
                        new Exception("Call trace").printStackTrace();
                    }
                    return null;
                }
                try {
                    isLoading = true;
                    tries++;
                    provider = doLoadProvider();
                } finally {
                    isLoading = false;
                }
            }
        }
        return provider;
    }
}

@SuppressWarnings("unused")
public class SecuritySubstitutionRuntimeInit {
}
