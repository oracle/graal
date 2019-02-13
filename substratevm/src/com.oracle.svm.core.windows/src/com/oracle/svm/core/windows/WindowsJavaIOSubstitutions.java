/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.jni.JNIRuntimeAccess;
import org.graalvm.nativeimage.RuntimeClassInitialization;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.PrintStream;

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@CLibrary("java")
class WindowsJavaIOSubstituteFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // Can't re-initialize the classes list below:
        // Error: com.oracle.graal.pointsto.constraints.UnsupportedFeatureException: No instances
        // are allowed in the image heap for a class
        // that is initialized or reinitialized at image runtime: java.io.XXX.
        //
        // RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.io.FileDescriptor"));
        // RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.io.FileInputStream"));
        // RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.io.FileOutputStream"));
        // RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.io.WinNTFileSystem"));

        RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.io.RandomAccessFile"));
        RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.util.zip.ZipFile"));
        RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.util.zip.Inflater"));
        RuntimeClassInitialization.rerunClassInitialization(access.findClassByName("java.util.zip.Deflater"));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            JNIRuntimeAccess.register(java.lang.String.class);
            JNIRuntimeAccess.register(java.io.File.class);
            JNIRuntimeAccess.register(java.io.File.class.getDeclaredField("path"));
            JNIRuntimeAccess.register(java.io.FileOutputStream.class);
            JNIRuntimeAccess.register(java.io.FileOutputStream.class.getDeclaredField("fd"));
            JNIRuntimeAccess.register(java.io.FileInputStream.class);
            JNIRuntimeAccess.register(java.io.FileInputStream.class.getDeclaredField("fd"));
            JNIRuntimeAccess.register(java.io.FileDescriptor.class);
            JNIRuntimeAccess.register(java.io.FileDescriptor.class.getDeclaredField("fd"));
            JNIRuntimeAccess.register(java.io.FileDescriptor.class.getDeclaredField("handle"));
            JNIRuntimeAccess.register(java.io.RandomAccessFile.class);
            JNIRuntimeAccess.register(java.io.RandomAccessFile.class.getDeclaredField("fd"));
            JNIRuntimeAccess.register(access.findClassByName("java.io.WinNTFileSystem"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("needDict"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("finished"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("buf"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("off"));
            JNIRuntimeAccess.register(java.util.zip.Inflater.class.getDeclaredField("len"));

            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("level"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("strategy"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("setParams"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("finish"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("finished"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("buf"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("off"));
            JNIRuntimeAccess.register(java.util.zip.Deflater.class.getDeclaredField("len"));
        } catch (NoSuchFieldException e) {
            VMError.shouldNotReachHere("WindowsJavaIOSubstitutionFeature: Error registering class or method: ", e);
        }
    }
}

@TargetClass(java.io.FileInputStream.class)
@Platforms(Platform.WINDOWS.class)
final class Target_java_io_FileInputStream {

    @Alias
    static native void initIDs();
}

@TargetClass(java.io.FileDescriptor.class)
@Platforms(Platform.WINDOWS.class)
final class Target_java_io_FileDescriptor {

    @Alias
    static native void initIDs();

    @Alias
    static native FileDescriptor standardStream(int fd);

    @Alias static FileDescriptor in;
    @Alias static FileDescriptor out;
    @Alias static FileDescriptor err;

}

@TargetClass(java.io.FileOutputStream.class)
@Platforms(Platform.WINDOWS.class)
final class Target_java_io_FileOutputStream {

    @Alias
    static native void initIDs();
}

@TargetClass(className = "java.io.WinNTFileSystem")
@Platforms(Platform.WINDOWS.class)
final class Target_java_io_WinNTFileSystem {

    @Alias
    static native void initIDs();
}

/** Dummy class to have a class with the file's name. */
@Platforms(Platform.WINDOWS.class)
public final class WindowsJavaIOSubstitutions {

    /** Private constructor: No instances. */
    private WindowsJavaIOSubstitutions() {
    }

    public static boolean initIDs() {
        try {
            /*
             * java.dll is normally loaded by the VM. After loading java.dll, the VM then calls
             * initializeSystemClasses which loads zip.dll.
             *
             * We might want to consider calling System.initializeSystemClasses instead of
             * explicitly loading the builtin zip library.
             */
            System.loadLibrary("java");

            Target_java_io_FileDescriptor.initIDs();
            Target_java_io_FileInputStream.initIDs();
            Target_java_io_FileOutputStream.initIDs();
            Target_java_io_WinNTFileSystem.initIDs();

            Target_java_io_FileDescriptor.in = Target_java_io_FileDescriptor.standardStream(0);
            Target_java_io_FileDescriptor.out = Target_java_io_FileDescriptor.standardStream(1);
            Target_java_io_FileDescriptor.err = Target_java_io_FileDescriptor.standardStream(2);

            System.setIn(new BufferedInputStream(new FileInputStream(FileDescriptor.in)));
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 128), true));
            System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 128), true));

            System.loadLibrary("zip");
            Target_java_util_zip_Inflater.initIDs();
            Target_java_util_zip_Deflater.initIDs();
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.log().string("System.loadLibrary failed, " + e).newline();
            return false;
        }
    }
}
