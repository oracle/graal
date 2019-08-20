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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@CLibrary(value = "java", requireStatic = true)
class WindowsJavaIOSubstituteFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        JNIRuntimeAccess.register(access.findClassByName("java.io.WinNTFileSystem"));
        try {
            JNIRuntimeAccess.register(FileDescriptor.class.getDeclaredField("handle"));
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
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.log().string("System.loadLibrary failed, " + e).newline();
            return false;
        }
    }
}
