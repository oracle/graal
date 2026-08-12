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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;

import com.oracle.svm.shared.security.SecurityProviderCatalog;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import sun.security.util.Debug;

public final class BuiltInSecurityProviderLoader {
    private BuiltInSecurityProviderLoader() {
    }

    public static String getProviderName(String providerNameOrClassName) {
        return SecurityProviderCatalog.getProviderName(providerNameOrClassName);
    }

    public static String getProviderClassName(String providerNameOrClassName) {
        return SecurityProviderCatalog.getProviderClassName(providerNameOrClassName);
    }

    public static boolean isBuiltIn(String providerNameOrClassName) {
        return SecurityProviderCatalog.isDirectlyConstructible(providerNameOrClassName);
    }

    /** §FS-security-providers.3.1, §FS-security-providers.4.3, and §FS-security-providers.7.1. */
    public static Provider load(String providerNameOrClassName, Debug debug) {
        String providerClassName = getProviderClassName(providerNameOrClassName);
        if (providerClassName == null) {
            return null;
        }
        return switch (providerClassName) {
            case "sun.security.provider.Sun" ->
                SecurityProviderRuntimeState.isJdkConstructible(providerClassName) ? new sun.security.provider.Sun() : loadReflectively(providerClassName, debug);
            case "sun.security.rsa.SunRsaSign" ->
                SecurityProviderRuntimeState.isJdkConstructible(providerClassName) ? new sun.security.rsa.SunRsaSign() : loadReflectively(providerClassName, debug);
            case "com.sun.crypto.provider.SunJCE" ->
                SecurityProviderRuntimeState.isJdkConstructible(providerClassName) ? new com.sun.crypto.provider.SunJCE() : loadReflectively(providerClassName, debug);
            case "sun.security.ssl.SunJSSE" ->
                SecurityProviderRuntimeState.isJdkConstructible(providerClassName) ? new sun.security.ssl.SunJSSE() : loadReflectively(providerClassName, debug);
            case "sun.security.ec.SunEC" ->
                SecurityProviderRuntimeState.isJdkConstructible(providerClassName) ? allocateSunECProvider() : loadReflectively(providerClassName, debug);
            case "apple.security.AppleProvider" -> loadReflectively(providerClassName, debug);
            default -> null;
        };
    }

    private static Provider allocateSunECProvider() {
        Constructor<?> constructor = SecurityProviderRuntimeState.getSunECConstructor();
        VMError.guarantee(constructor != null, "The SunEC constructor is not present.");
        try {
            return (Provider) constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere("The SunEC provider cannot be constructed.", e);
        }
    }

    private static Provider loadReflectively(String providerClassName, Debug debug) {
        try {
            Class<?> providerClass = Class.forName(GraalDirectives.opaque(providerClassName));
            // §FS-security-providers.2.2: Use the same construction path the build-time rule chose.
            return SecurityProviderRuntimeAccess.constructProvider(providerClass);
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
}
