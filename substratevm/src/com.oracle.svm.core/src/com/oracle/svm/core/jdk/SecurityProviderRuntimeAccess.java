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

import java.security.Provider;
import java.util.function.Supplier;

import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.shared.NeverInline;

public final class SecurityProviderRuntimeAccess {
    private static final ThreadLocal<Boolean> LOAD_UNREGISTERED_CONFIGURED_PROVIDER = new ThreadLocal<>();

    private SecurityProviderRuntimeAccess() {
    }

    /** §FS-security-providers.4.3: Explicit configured-provider lookups retain diagnostics. */
    public static Provider loadUnregisteredConfiguredProvider(Supplier<Provider> loader) {
        Boolean previous = LOAD_UNREGISTERED_CONFIGURED_PROVIDER.get();
        LOAD_UNREGISTERED_CONFIGURED_PROVIDER.set(true);
        try {
            return loader.get();
        } finally {
            if (previous == null) {
                LOAD_UNREGISTERED_CONFIGURED_PROVIDER.remove();
            } else {
                LOAD_UNREGISTERED_CONFIGURED_PROVIDER.set(previous);
            }
        }
    }

    /** §FS-security-providers.7.1: Provider-list construction filters unregistered providers. */
    public static boolean shouldLoadUnregisteredConfiguredProvider() {
        return Boolean.TRUE.equals(LOAD_UNREGISTERED_CONFIGURED_PROVIDER.get());
    }

    /** §FS-security-providers.4.3: Cache misses probe type access for standard diagnostics. */
    @NeverInline("Keep the provider class name unknown to static analysis without an opaque compiler node.")
    public static void reportMissingRegistration(Class<?> providerClass) {
        try {
            Class.forName(providerClass.getName(), false, providerClass.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new SecurityException(
                            "Attempted to use a security provider that was not registered for reflection at build time: " + providerClass.getName() + ". " +
                                            "Add the provider type to reachability-metadata.json and rebuild the image.",
                            ex);
        }
        throw new SecurityException("Attempted to use a security provider without build-time verification: " + providerClass.getName());
    }

    /** §FS-security-providers.6.1: Existing provider instances trace type access. */
    public static Provider traceLookup(Provider provider) {
        if (provider != null && MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceReflectionType(provider.getClass());
        }
        return provider;
    }

    /** §FS-security-providers.6.1: Enumeration traces every provider returned by the JDK. */
    public static Provider[] traceLookups(Provider[] providers) {
        if (providers != null && MetadataTracer.enabled()) {
            for (Provider provider : providers) {
                traceLookup(provider);
            }
        }
        return providers;
    }
}
