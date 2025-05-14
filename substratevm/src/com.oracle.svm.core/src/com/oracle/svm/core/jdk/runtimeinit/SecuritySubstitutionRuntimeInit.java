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
import com.oracle.svm.core.jdk.JDKInitializedAtRunTime;
import com.oracle.svm.core.jdk.SecurityProvidersSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

@TargetClass(value = java.security.Security.class, onlyWith = JDKInitializedAtRunTime.class)
final class Target_java_security_Security {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;
}

@TargetClass(value = java.security.Security.class, innerClass = "SecPropLoader", onlyWith = JDKInitializedAtRunTime.class)
final class Target_java_security_Security_SecPropLoader {

    @Substitute
    private static void loadMaster() {
        Target_java_security_Security.props = SecurityProvidersSupport.singleton().getSavedInitialSecurityProperties();
    }
}

@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = JDKInitializedAtRunTime.class)
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * The JceSecurity.verificationResults cache is initialized by the SecurityServicesFeature at
     * build time, for all registered providers. The cache is used by JceSecurity.canUseProvider()
     * at runtime to check whether a provider is properly signed and can be used by JCE. It does
     * that via jar verification which we cannot support.
     */

    // Checkstyle: stop
    @Alias //
    private static Object PROVIDER_VERIFIED;
    // Checkstyle: resume

    // Map<Provider,?> of the providers we already have verified
    // value == PROVIDER_VERIFIED is successfully verified
    // value is failure cause Exception in error case
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
        /* Start code block copied from original method. */
        /* The verification results map key is an identity wrapper object. */
        Object o = SecurityProvidersSupport.singleton().getSecurityProviderVerificationResult(p.getName());
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /* End code block copied from original method. */
        /*
         * If the verification result is not found in the verificationResults map JDK proceeds to
         * verify it. That requires accessing the code base which we don't support. The substitution
         * for getCodeBase() would be enough to take care of this too, but substituting
         * getVerificationResult() allows for a better error message.
         */
        throw VMError.unsupportedFeature("Trying to verify a provider that was not registered at build time: " + p + ". " +
                        "All providers must be registered and verified in the Native Image builder. ");
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = JDKInitializedAtRunTime.class)
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
        // volatile variable load
        Provider p = provider;
        if (p != null) {
            return p;
        }
        // DCL
        synchronized (this) {
            p = provider;
            if (p != null) {
                return p;
            }
            if (!shouldLoad()) {
                return null;
            }

            // Create providers which are in java.base directly
            SecurityProvidersSupport support = SecurityProvidersSupport.singleton();
            switch (provName) {
                case "SUN", "sun.security.provider.Sun": {
                    p = support.isSecurityProviderExpected("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : null;
                    break;
                }
                case "SunRsaSign", "sun.security.rsa.SunRsaSign": {
                    p = support.isSecurityProviderExpected("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : null;
                    break;
                }
                case "SunJCE", "com.sun.crypto.provider.SunJCE": {
                    p = support.isSecurityProviderExpected("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : null;
                    break;
                }
                case "SunJSSE": {
                    p = support.isSecurityProviderExpected("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : null;
                    break;
                }
                case "SunEC": {
                    // Constructor inside method and then allocate. ModuleSupport to open.
                    p = support.isSecurityProviderExpected("SunEC", "sun.security.ec.SunEC") ? support.allocateSunECProvider() : null;
                    break;
                }
                case "Apple", "apple.security.AppleProvider": {
                    // need to use reflection since this class only exists on MacOsx
                    try {
                        Class<?> c = Class.forName("apple.security.AppleProvider");
                        if (Provider.class.isAssignableFrom(c)) {
                            @SuppressWarnings("deprecation")
                            Object newInstance = c.newInstance();
                            p = (Provider) newInstance;
                        }
                    } catch (Exception ex) {
                        if (debug != null) {
                            debug.println("Error loading provider Apple");
                            ex.printStackTrace();
                        }
                    }
                    break;
                }
                default: {
                    if (isLoading) {
                        // because this method is synchronized, this can only
                        // happen if there is recursion.
                        if (debug != null) {
                            debug.println("Recursion loading provider: " + this);
                            new Exception("Call trace").printStackTrace();
                        }
                        return null;
                    }
                    try {
                        isLoading = true;
                        tries++;
                        p = doLoadProvider();
                    } finally {
                        isLoading = false;
                    }
                }
            }
            provider = p;
        }
        return p;
    }
}

public class SecuritySubstitutionRuntimeInit {
}
