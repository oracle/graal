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

import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.util.VMError;

public final class SecurityProviderRuntimeAccess {
    private SecurityProviderRuntimeAccess() {
    }

    /** §FS-security-providers.4.3: Cache misses probe type access for standard diagnostics. */
    @NeverInline("Keep the provider class name unknown to static analysis without an opaque compiler node.")
    public static void reportMissingRegistration(Class<?> providerClass) {
        try {
            Class.forName(providerClass.getName(), false, providerClass.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere("A reachable security provider class was not found.", ex);
        }
        throw VMError.shouldNotReachHere("A security provider without a verification result was registered for reflection: " + providerClass.getName());
    }

    /** §FS-security-providers.6.1: Existing provider instances trace type access. */
    public static Provider traceLookup(Provider provider) {
        if (provider != null && MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceReflectionType(provider.getClass());
        }
        return provider;
    }
}
