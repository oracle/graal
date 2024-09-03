/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

@AutomaticallyRegisteredFeature
public class JNIRegistrationManagementExt extends JNIRegistrationUtil implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        initializeAtRunTime(access, "com.sun.management.internal.OperatingSystemImpl");

        access.registerReachabilityHandler(this::linkManagementExt, clazz(access, "com.sun.management.internal.OperatingSystemImpl"));
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("com_sun_management_internal_OperatingSystemImpl");
    }

    private void linkManagementExt(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = ((DuringAnalysisAccessImpl) access).getNativeLibraries();
        /*
         * Note that we register `management_ext` as a non-JNI static library. This avoids pulling
         * in `JNI_OnLoad`, which is fine since we're only interested in the native methods of the
         * `OperatingSystemImpl` class anyway.
         */
        nativeLibraries.addStaticNonJniLibrary("management_ext", "java");
        if (isWindows()) {
            nativeLibraries.addDynamicNonJniLibrary("psapi");
        }
    }
}
