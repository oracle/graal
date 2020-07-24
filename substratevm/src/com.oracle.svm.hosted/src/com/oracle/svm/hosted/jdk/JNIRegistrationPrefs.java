/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticFeature
public class JNIRegistrationPrefs extends JNIRegistrationUtil implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * The default Preferences class implementations trigger loading of the 'prefs' library in
         * their static initializers on Java 11. We force a class initialization rerun in order to
         * ensure we pick up the loadLibrary call and properly link against the library.
         */
        String preferencesImplementation = getPlatformPreferencesClassName();
        rerunClassInit(access, preferencesImplementation);

        if (isDarwin()) {
            rerunClassInit(access, "java.util.prefs.MacOSXPreferencesFile");
        }

        access.registerReachabilityHandler(JNIRegistrationPrefs::handlePreferencesClassReachable, clazz(access, preferencesImplementation));
    }

    private static String getPlatformPreferencesClassName() {
        if (isLinux()) {
            return "java.util.prefs.FileSystemPreferences";
        } else if (isWindows()) {
            return "java.util.prefs.WindowsPreferences";
        } else if (isDarwin()) {
            return "java.util.prefs.MacOSXPreferences";
        }
        throw VMError.shouldNotReachHere("Unexpected platform");
    }

    private static void handlePreferencesClassReachable(DuringAnalysisAccess access) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("prefs");
        } else {
            /* On JDK versions below 8, prefs is part of libjava */
            if (isDarwin()) {
                NativeLibraries nativeLibraries = ((FeatureImpl.DuringAnalysisAccessImpl) access).getNativeLibraries();
                nativeLibraries.addStaticJniLibrary("osx");
            }
        }

        if (isDarwin()) {
            /* Darwin allocates a string array from native code */
            JNIRuntimeAccess.register(String[].class);
        }
    }

}
