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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@CLibrary(value = JDKLibZipSubstitutions.CLibraryName, requireStatic = true)
public class JDKLibZipSubstitutions {
    static final String CLibraryName = "zip";

    public static boolean initIDs() {
        try {
            System.loadLibrary(CLibraryName);
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                Target_java_util_zip_Inflater.initIDs();
                Target_java_util_zip_Deflater.initIDs();
            }
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.log().string("System.loadLibrary failed, " + e).newline();
            return false;
        }
    }

    @TargetClass(className = "java.util.zip.Inflater")
    static final class Target_java_util_zip_Inflater {
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        @Alias
        static native void initIDs();
    }

    @TargetClass(className = "java.util.zip.Deflater")
    static final class Target_java_util_zip_Deflater {
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        @Alias
        static native void initIDs();
    }
}

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticFeature
class JDKLibZipFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.util.zip.ZipFile"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.util.zip.Inflater"), "required for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.util.zip.Deflater"), "required for substitutions");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("needDict"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("finished"));
            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("inputConsumed"));
                JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("outputConsumed"));
            } else {
                JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("buf"));
                JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("off"));
                JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("len"));
            }

            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("level"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("strategy"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("setParams"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("finish"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("finished"));
            if (JavaVersionUtil.JAVA_SPEC < 11) {
                JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("buf"));
                JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("off"));
                JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("len"));
            }
        } catch (NoSuchFieldException e) {
            VMError.shouldNotReachHere("LibZipFeature: Error in registering jni access:", e);
        }
    }
}
