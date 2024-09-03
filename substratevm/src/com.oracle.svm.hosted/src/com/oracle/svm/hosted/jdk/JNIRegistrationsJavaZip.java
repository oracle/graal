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
package com.oracle.svm.hosted.jdk;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.hosted.c.NativeLibraries;

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticallyRegisteredFeature
class JNIRegistrationsJavaZip extends JNIRegistrationUtil implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        initializeAtRunTime(a, "java.util.zip.Inflater", "java.util.zip.Deflater");
        /* These classes have class initializers that lazily load the zip library. */
        initializeAtRunTime(a, "java.util.zip.Adler32", "java.util.zip.CRC32");
        initializeAtRunTime(a, "sun.net.www.protocol.jar.JarFileFactory", "sun.net.www.protocol.jar.JarURLConnection");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        a.registerReachabilityHandler(JNIRegistrationsJavaZip::registerInflaterInitIDs, method(a, "java.util.zip.Inflater", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationsJavaZip::registerAndLinkZip, method(a, "java.util.zip.ZipUtils", "loadLibrary"));
    }

    private static void registerInflaterInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.util.zip.Inflater", "inputConsumed", "outputConsumed"));
    }

    private static void registerAndLinkZip(@SuppressWarnings("unused") DuringAnalysisAccess a) {
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("zip");
        NativeLibraries.singleton().addStaticJniLibrary("zip");
    }
}
