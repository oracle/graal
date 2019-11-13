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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jni.JNIRuntimeAccess;

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticFeature
class JNIRegistrationsJavaZip extends JNIRegistrationUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "java.util.zip.Inflater", "java.util.zip.Deflater");
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            /*
             * On JDK 11 and later, these classes have class initializers that lazily load the zip
             * library. On JDK 8, the zip library is loaded by the VM early during startup.
             */
            rerunClassInit(a, "java.util.zip.Adler32", "java.util.zip.CRC32");
            rerunClassInit(a, "sun.net.www.protocol.jar.JarFileFactory", "sun.net.www.protocol.jar.JarURLConnection");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            a.registerReachabilityHandler(JNIRegistrationsJavaZip::registerJDK11InflaterInitIDs, method(a, "java.util.zip.Inflater", "initIDs"));
        } else {
            a.registerReachabilityHandler(JNIRegistrationsJavaZip::registerJDK8InflaterInitIDs, method(a, "java.util.zip.Inflater", "initIDs"));
            a.registerReachabilityHandler(JNIRegistrationsJavaZip::registerJDK8DeflaterInitIDs, method(a, "java.util.zip.Deflater", "initIDs"));
        }
    }

    private static void registerJDK11InflaterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.util.zip.Inflater", "inputConsumed", "outputConsumed"));
    }

    private static void registerJDK8InflaterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.util.zip.Inflater", "needDict", "finished", "buf", "off", "len"));
    }

    private static void registerJDK8DeflaterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.util.zip.Deflater", "level", "strategy", "setParams", "finish", "finished", "buf", "off", "len"));
    }
}
