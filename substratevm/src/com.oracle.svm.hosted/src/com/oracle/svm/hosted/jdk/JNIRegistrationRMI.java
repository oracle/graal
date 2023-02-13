/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticallyRegisteredFeature
public class JNIRegistrationRMI extends JNIRegistrationUtil implements InternalFeature {

    private static final String STATIC_LIB = "rmi";
    private static final String GC = "sun.rmi.transport.GC";
    private static final String GC_LOADER = "sun.rmi.transport.GC$1";

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * The default RMI class implementations trigger loading of the 'rmi' library in their
         * static initializers on Java 11+. We force a class initialization rerun in order to ensure
         * we pick up the loadLibrary call and properly link against the library.
         */
        rerunClassInit(access, GC, GC_LOADER);

        access.registerReachabilityHandler(JNIRegistrationRMI::handleGCClassReachable, clazz(access, GC), clazz(access, GC_LOADER));
    }

    private static void handleGCClassReachable(DuringAnalysisAccess access) {
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary(STATIC_LIB);

        NativeLibraries nativeLibraries = ((FeatureImpl.DuringAnalysisAccessImpl) access).getNativeLibraries();
        nativeLibraries.addStaticJniLibrary(STATIC_LIB);
    }
}
