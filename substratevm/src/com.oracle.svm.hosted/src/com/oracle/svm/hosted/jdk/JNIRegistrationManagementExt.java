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

import java.lang.reflect.Method;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.management.LibManagementExtSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class JNIRegistrationManagementExt extends JNIRegistrationUtil implements InternalFeature {

    private static final String OPERATING_SYSTEM_IMPL = "com.sun.management.internal.OperatingSystemImpl";
    private NativeLibraries nativeLibraries;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        nativeLibraries = ((BeforeAnalysisAccessImpl) access).getNativeLibraries();

        rerunClassInit(access, OPERATING_SYSTEM_IMPL);

        access.registerReachabilityHandler(this::linkManagementExt, clazz(access, OPERATING_SYSTEM_IMPL));
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("com_sun_management_internal_OperatingSystemImpl");
    }

    private void linkManagementExt(@SuppressWarnings("unused") DuringAnalysisAccess access) {
        /*
         * Note that we register `management_ext` as a non-JNI static library. This avoids pulling
         * in `JNI_OnLoad`, which is fine since we're only interested in the native methods of the
         * `OperatingSystemImpl` class anyway.
         */
        nativeLibraries.addStaticNonJniLibrary("management_ext", "java");
        if (isWindows()) {
            nativeLibraries.addDynamicNonJniLibrary("psapi");
        } else {
            /*
             * Register our port of the native function `throw_internal_error`. This avoids linking
             * the entire object file of the original function, which is necessary to prevent linker
             * errors such as JDK-8264047.
             */
            Method method = ReflectionUtil.lookupMethod(LibManagementExtSupport.class, "throwInternalError", IsolateThread.class, CCharPointer.class);
            CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> CEntryPointData.create(method));
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        boolean managementExtNeeded = access.isReachable(clazz(access, OPERATING_SYSTEM_IMPL));
        if (managementExtNeeded && NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary("awt_headless")) {
            /*
             * Ensure that `management_ext` comes before `awt_headless` on the linker command line.
             * This is necessary to prevent linker errors such as JDK-8264047.
             */
            nativeLibraries.addStaticNonJniLibrary("management_ext", "awt_headless");
        }
    }
}
