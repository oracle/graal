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
package com.oracle.svm.core.jdk;

import java.util.Optional;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

@AutomaticallyRegisteredFeature
public class JavaNetHttpFeature extends JNIRegistrationUtil implements InternalFeature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("java.net.http");
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return requiredModule().isPresent();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        JavaNetHttpFeature.class.getModule().addReads(requiredModule().get());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.initializeAtRunTime("jdk.internal.net.http", "for reading properties at run time");
        rci.initializeAtRunTime("jdk.internal.net.http.websocket.OpeningHandshake", "contains a SecureRandom reference");
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
}

@AutomaticallyRegisteredFeature
class SimpleWebServerFeature implements InternalFeature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("jdk.httpserver");
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return requiredModule().isPresent();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        SimpleWebServerFeature.class.getModule().addReads(requiredModule().get());

        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.initializeAtRunTime("sun.net.httpserver.simpleserver", "Allocates InetAddress in class initializers");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(a -> {
            ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "sun.net.httpserver.simpleserver.resources.simpleserver");
        }, access.findClassByName("sun.net.httpserver.simpleserver.SimpleFileServerImpl"));
    }
}
