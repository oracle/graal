/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.features.jdk;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

public class JavaNetHttpFeature implements Feature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("java.net.http");
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return requiredModule().isPresent();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // for reading properties at run time
        RuntimeClassInitialization.initializeAtRunTime("jdk.internal.net.http");
        // contains a SecureRandom reference
        RuntimeClassInitialization.initializeAtRunTime("jdk.internal.net.http.websocket.OpeningHandshake");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(JavaNetHttpFeature::registerInitFiltersAccess, method(access, "jdk.internal.net.http.HttpClientImpl", "initFilters"));
    }

    private static void registerInitFiltersAccess(DuringAnalysisAccess a) {
        RuntimeReflection.registerForReflectiveInstantiation(clazz(a, "jdk.internal.net.http.AuthenticationFilter"));
        RuntimeReflection.registerForReflectiveInstantiation(clazz(a, "jdk.internal.net.http.RedirectFilter"));
        RuntimeReflection.registerForReflectiveInstantiation(clazz(a, "jdk.internal.net.http.CookieFilter"));
    }

    protected static Class<?> clazz(FeatureAccess access, String className) {
        return Objects.requireNonNull(access.findClassByName(className), () -> String.format("should not reach here: class %s not found", className));
    }

    protected static Method method(FeatureAccess access, String className, String methodName, Class<?>... parameterTypes) {
        try {
            return clazz(access, className).getDeclaredMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new RuntimeException(ex);
        }
    }
}

class SimpleWebServerFeature implements Feature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("jdk.httpserver");
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return requiredModule().isPresent();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // Allocates InetAddress in class initializers
        RuntimeClassInitialization.initializeAtRunTime("sun.net.httpserver.simpleserver");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(a -> {
            RuntimeResourceAccess.addResourceBundle(requiredModule().get(), "sun.net.httpserver.simpleserver.resources.simpleserver");
        }, access.findClassByName("sun.net.httpserver.simpleserver.SimpleFileServerImpl"));
    }
}
