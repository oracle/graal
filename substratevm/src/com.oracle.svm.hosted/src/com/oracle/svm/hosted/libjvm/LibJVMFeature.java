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

package com.oracle.svm.hosted.libjvm;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.libjvm.LibJVMMainMethodWrappers;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeJNIAccess;

final class LibJVMFeature extends JNIRegistrationUtil implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        JVMCIRuntimeJNIAccess.register(method(access, "java.lang.VersionProps", "print", boolean.class));

        RuntimeResourceAccess.addResourceBundle(Object.class.getModule(), "sun.launcher.resources.launcher");

        String launcherHelper = "sun.launcher.LauncherHelper";
        JVMCIRuntimeJNIAccess.register(method(access, launcherHelper, "checkAndLoadMain", boolean.class, int.class, String.class));
        JVMCIRuntimeJNIAccess.register(method(access, launcherHelper, "makePlatformString", boolean.class, byte[].class));
        JVMCIRuntimeJNIAccess.register(method(access, launcherHelper, "getApplicationClass"));
        JVMCIRuntimeJNIAccess.register(fields(access, launcherHelper, "isStaticMain", "noArgMain"));

        JVMCIRuntimeJNIAccess.register(method(access, launcherHelper, "listModules"));

        // Workaround for GR-71358
        ImageSingletons.add(LibJVMMainMethodWrappers.class, new LibJVMMainMethodWrappers());
        var libJVMMainMethodWrappersName = LibJVMMainMethodWrappers.class.getName();
        JVMCIRuntimeJNIAccess.register(method(access, libJVMMainMethodWrappersName, "main", String[].class));
        JVMCIRuntimeJNIAccess.register(method(access, libJVMMainMethodWrappersName, "main"));

        // This is needed so that the jdk.internal.loader.ClassLoaders fields get set via
        // ArchivedClassLoaders and adjusted with BuiltinClassLoader.setClassPath(URLClassPath)
        initializeAtRunTime(access, "jdk.internal.loader.ClassLoaders");
    }
}
