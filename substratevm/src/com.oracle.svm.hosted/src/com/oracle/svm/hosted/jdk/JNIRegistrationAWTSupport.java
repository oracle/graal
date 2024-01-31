/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;

@Platforms({Platform.WINDOWS.class, Platform.LINUX.class})
@AutomaticallyRegisteredFeature
public class JNIRegistrationAWTSupport extends JNIRegistrationUtil implements InternalFeature {
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        JNIRegistrationSupport jniRegistrationSupport = JNIRegistrationSupport.singleton();
        if (jniRegistrationSupport.isRegisteredLibrary("awt")) {
            jniRegistrationSupport.addJvmShimExports(
                            "jio_snprintf");
            jniRegistrationSupport.addJavaShimExports(
                            "JNU_CallMethodByName",
                            "JNU_CallStaticMethodByName",
                            "JNU_GetEnv",
                            "JNU_GetStaticFieldByName",
                            "JNU_IsInstanceOfByName",
                            "JNU_NewObjectByName",
                            "JNU_NewStringPlatform",
                            "JNU_SetFieldByName",
                            "JNU_ThrowArrayIndexOutOfBoundsException",
                            "JNU_ThrowByName",
                            "JNU_ThrowIllegalArgumentException",
                            "JNU_ThrowInternalError",
                            "JNU_ThrowNullPointerException",
                            "JNU_ThrowOutOfMemoryError");
            if (isWindows()) {
                jniRegistrationSupport.addJvmShimExports(
                                "JVM_CurrentTimeMillis",
                                "JVM_RaiseSignal");
                jniRegistrationSupport.addJavaShimExports(
                                "JDK_LoadSystemLibrary",
                                "JNU_CallMethodByNameV",
                                "JNU_ClassString",
                                "JNU_GetFieldByName",
                                "JNU_ThrowIOException",
                                "getEncodingFromLangID",
                                "getJavaIDFromLangID");
            } else {
                jniRegistrationSupport.addJvmShimExports(
                                "jio_fprintf");
                jniRegistrationSupport.addJavaShimExports(
                                "JNU_GetStringPlatformChars",
                                "JNU_ReleaseStringPlatformChars");
                /* Since `awt` loads either `awt_headless` or `awt_xawt`, we register them both. */
                jniRegistrationSupport.registerLibrary("awt_headless");
                jniRegistrationSupport.registerLibrary("awt_xawt");
            }
        }
        if (jniRegistrationSupport.isRegisteredLibrary("javaaccessbridge")) {
            /* Dependency on `jawt` is not expressed in Java, so we register it manually here. */
            jniRegistrationSupport.registerLibrary("jawt");
        }
        if (jniRegistrationSupport.isRegisteredLibrary("javajpeg")) {
            jniRegistrationSupport.addJavaShimExports(
                            "JNU_GetEnv",
                            "JNU_ThrowByName",
                            "JNU_ThrowNullPointerException");
            if (isWindows()) {
                jniRegistrationSupport.addJavaShimExports(
                                "jio_snprintf");
            } else {
                jniRegistrationSupport.addJvmShimExports(
                                "jio_snprintf");
            }
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (isWindows() && JNIRegistrationSupport.singleton().isRegisteredLibrary("awt")) {
            ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
                /*
                 * Add Windows libraries that are pulled in as a side effect of exporting the
                 * `getEncodingFromLangID` and `getJavaIDFromLangID` symbols.
                 */
                linkerInvocation.addNativeLinkerOption("shell32.lib");
                linkerInvocation.addNativeLinkerOption("ole32.lib");
                return linkerInvocation;
            });
        }
    }
}
