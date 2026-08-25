/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.BuiltInSecurityProviderLoader;
import com.oracle.svm.core.jdk.JceProviderVerificationSupport;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeAccess;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;
import com.oracle.svm.core.jdk.SecurityProvidersInitializedAtRunTime;
import com.oracle.svm.shared.util.BasedOnJDKFile;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

@TargetClass(value = java.security.Security.class, onlyWith = SecurityProvidersInitializedAtRunTime.class)
final class Target_java_security_Security {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;

    /** §FS-002-security-providers.4.3: Keep omission diagnostics outside ProviderList mutation. */
    @Substitute
    public static Provider getProvider(String name) {
        Provider provider = sun.security.jca.Providers.getProviderList().getProvider(name);
        if (provider == null) {
            SecurityProviderRuntimeAccess.reportMissingConfiguredProvider(name);
        }
        return SecurityProviderRuntimeAccess.traceJdkProviderLookup(provider);
    }
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
        Target_java_security_Security.props = SecurityProviderRuntimeState.getSavedInitialSecurityProperties();
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
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
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
        return JceProviderVerificationSupport.getVerificationResult(p);
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig {

    @Alias //
    String provName;

    @Alias //
    String argument;

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
            SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProvider(provName);
            String configuredProviderClassName = configuredProvider != null ? configuredProvider.providerClassName() : null;
            String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(provName);
            String providerClassName = configuredProviderClassName != null ? configuredProviderClassName
                            : builtInProviderClassName != null ? builtInProviderClassName : provName;
            // §FS-002-security-providers.7.1
            // Omit unregistered providers from the run-time list.
            if (!SecurityProviderRuntimeAccess.isJdkAcquirable(providerClassName)) {
                return null;
            }
            if (!shouldLoad()) {
                return null;
            }
            // Create providers which are in java.base directly
            if (BuiltInSecurityProviderLoader.isBuiltIn(provName)) {
                provider = BuiltInSecurityProviderLoader.load(provName, debug);
            } else {
                if (isLoading) {
                    /*
                     * This method is synchronized, so this can only happen if there is recursion.
                     */
                    if (debug != null) {
                        debug.println("Recursion loading provider: " + this);
                        // Checkstyle: allow System.err (for JDK compatibility)
                        new Exception("Call trace").printStackTrace(System.err);
                        // Checkstyle: disallow System.err
                    }
                    return null;
                }
                try {
                    isLoading = true;
                    tries++;
                    if (configuredProviderClassName != null) {
                        /* §FS-002-security-providers.1.3 and §FS-002-security-providers.7.1:
                         * Use the ordered retained descriptor candidate under the same recursion
                         * and retry state machine as the JDK ServiceLoader path. */
                        provider = SecurityProviderRuntimeAccess.loadRegisteredConfiguredProvider(provName, configuredProviderClassName,
                                        configuredProvider.effectiveConstructionClassName(), argument);
                    } else {
                        provider = doLoadProvider();
                    }
                } finally {
                    isLoading = false;
                }
            }
        }
        return provider;
    }

}

@TargetClass(className = "sun.security.jca.ProviderList", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderList {

    @Alias //
    private Target_sun_security_jca_ProviderConfig[] configs;

    @Alias
    private native Provider getProvider(int index);

    @Alias
    private native int getIndex(String name);

    @Substitute
    public Provider getProvider(String name) {
        int index = getIndex(name);
        if (index >= 0) {
            return SecurityProviderRuntimeAccess.traceJdkProviderLookup(getProvider(index));
        }
        return null;
    }

    /** Return only active providers without removing inactive configurations from this list. */
    @Substitute
    public Provider[] toArray() {
        ArrayList<Provider> activeProviders = new ArrayList<>(configs.length);
        for (Target_sun_security_jca_ProviderConfig config : configs) {
            Provider provider = config.getProvider();
            if (provider != null) {
                activeProviders.add(provider);
            }
        }
        return activeProviders.toArray(new Provider[0]);
    }
}

@TargetClass(className = "sun.security.jca.GetInstance", onlyWith = SecurityProvidersInitializedAtRunTime.class)
final class Target_sun_security_jca_GetInstance_RuntimeInit {
    /** §FS-002-security-providers.4.3: Named factories diagnose at their acquisition boundary. */
    @Substitute
    public static Provider.Service getService(String type, String algorithm, String providerName)
                    throws NoSuchAlgorithmException, NoSuchProviderException {
        // Checkstyle: allow inconsistent exceptions and errors (JDK-compatible messages)
        if (providerName == null || providerName.isEmpty()) {
            throw new IllegalArgumentException("missing provider");
        }
        Provider provider = sun.security.jca.Providers.getProviderList().getProvider(providerName);
        if (provider == null) {
            SecurityProviderRuntimeAccess.reportMissingConfiguredProvider(providerName);
            throw new NoSuchProviderException("no such provider: " + providerName);
        }
        Provider.Service service = provider.getService(type, algorithm);
        if (service == null) {
            throw new NoSuchAlgorithmException("no such algorithm: " + algorithm + " for provider " + providerName);
        }
        // Checkstyle: disallow inconsistent exceptions and errors
        return service;
    }
}

@SuppressWarnings("unused")
public class SecuritySubstitutionRuntimeInit {
}
