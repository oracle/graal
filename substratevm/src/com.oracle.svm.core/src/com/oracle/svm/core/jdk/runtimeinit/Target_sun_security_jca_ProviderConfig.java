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

import java.security.Provider;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.BuiltInSecurityProviderLoader;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeAccess;
import com.oracle.svm.core.jdk.SecurityProviderRuntimeState;
import com.oracle.svm.core.jdk.SecurityProvidersInitializedAtRunTime;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

/*
 * Share one target for the injected state, constructor, and provider arrays in both run-time modes.
 * A second alias can expose the original type instead of its injected-fields wrapper during
 * hosted type construction, particularly when no configured provider is retained.
 */
@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = SecurityProvidersInitializedAtRunTime.class)
@SuppressWarnings({"unused", "static-method"})
public final class Target_sun_security_jca_ProviderConfig {

    @Alias //
    String provName;

    @Alias //
    String argument;

    @Alias//
    private static sun.security.util.Debug debug;

    @Alias//
    public Provider provider;

    /** The descriptor selected for a cached JDK-managed provider, before configure(). */
    @Inject private SecurityProviderRuntimeState.ConfiguredProviderInfo selectedProvider;

    @Alias//
    private boolean isLoading;

    @Alias//
    private int tries;

    @Alias
    public Target_sun_security_jca_ProviderConfig(@SuppressWarnings("unused") Provider provider) {
    }

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
    public Provider getProvider() {
        if (provider != null && selectedProvider == null) {
            return provider;
        }
        synchronized (this) {
            SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProvider(provName);
            if (provider != null) {
                if (selectedProvider == null || configuredProvider != null && selectedProvider == configuredProvider) {
                    return provider;
                }
                // §FS-002-security-providers.design.1.4: Reevaluate a cached name after activation.
                provider = null;
            }
            // §FS-002-security-providers.design.1.4: Retry budgets belong to the selected
            // descriptor.
            if (selectedProvider != configuredProvider) {
                selectedProvider = configuredProvider;
                tries = 0;
            }
            String configuredProviderClassName = configuredProvider != null ? configuredProvider.providerClassName() : null;
            String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(provName);
            String providerClassName = configuredProviderClassName != null ? configuredProviderClassName
                            : builtInProviderClassName != null ? builtInProviderClassName : provName;
            // Omit unregistered providers from the run-time list
            // (§FS-002-security-providers.design.6.1).
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
                        /* §FS-002.design.1.2, §FS-002.design.6.1:
                         * ordered retained descriptors, with the JDK ServiceLoader recursion and
                         * retry state machine. */
                        selectedProvider = configuredProvider;
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
