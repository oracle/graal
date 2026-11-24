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
import java.util.Properties;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import sun.security.util.Debug;

/** Holds the final application-layer state needed by security providers at run time. */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = ApplicationLayerOnly.class)
public final class RuntimeSecurityProvidersSupport {
    /* These values describe the complete runtime configuration rather than per-layer additions. */
    private final Properties savedInitialSecurityProperties;
    private final Constructor<?> sunECConstructor;

    public RuntimeSecurityProvidersSupport(Constructor<?> sunECConstructor, Properties savedInitialSecurityProperties) {
        this.sunECConstructor = sunECConstructor;
        this.savedInitialSecurityProperties = savedInitialSecurityProperties;
    }

    public static RuntimeSecurityProvidersSupport singleton() {
        return ImageSingletons.lookup(RuntimeSecurityProvidersSupport.class);
    }

    public Properties getSavedInitialSecurityProperties() {
        return savedInitialSecurityProperties;
    }

    public Provider loadBuiltInProvider(String provName, Debug debug) {
        return switch (provName) {
            case "SUN", "sun.security.provider.Sun" ->
                LayeredSecurityProvidersSupport.isSecurityProviderRequested("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : null;
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" ->
                LayeredSecurityProvidersSupport.isSecurityProviderRequested("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : null;
            case "SunJCE", "com.sun.crypto.provider.SunJCE" ->
                LayeredSecurityProvidersSupport.isSecurityProviderRequested("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : null;
            case "SunJSSE", "sun.security.ssl.SunJSSE" ->
                LayeredSecurityProvidersSupport.isSecurityProviderRequested("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : null;
            case "SunEC", "sun.security.ec.SunEC" ->
                LayeredSecurityProvidersSupport.isSecurityProviderRequested("SunEC", "sun.security.ec.SunEC") ? allocateSunECProvider() : null;
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

    private Provider allocateSunECProvider() {
        try {
            return (Provider) sunECConstructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere("The SunEC constructor is not present.", e);
        }
    }
}
