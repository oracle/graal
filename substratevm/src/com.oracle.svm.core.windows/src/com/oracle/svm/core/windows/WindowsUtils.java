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

import com.oracle.svm.core.windows.headers.FileAPI;

import java.io.FileDescriptor;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.snippets.KnownIntrinsics;

@Platforms(Platform.WINDOWS.class)
public class WindowsUtils {

    @TargetClass(java.io.FileDescriptor.class)
    private static final class Target_java_io_FileDescriptor {
        @Alias long handle;
    }

    public static int getHandle(FileDescriptor descriptor) {
        return (int) KnownIntrinsics.unsafeCast(descriptor, Target_java_io_FileDescriptor.class).handle;
    }

    public static void setHandle(FileDescriptor descriptor, int handle) {
        KnownIntrinsics.unsafeCast(descriptor, Target_java_io_FileDescriptor.class).handle = handle;
    }

    /**
     * Low-level output of bytes already in native memory. This method is allocation free, so that
     * it can be used, e.g., in low-level logging routines.
     */
    public static boolean writeBytes(int handle, CCharPointer bytes, UnsignedWord length) {
        CCharPointer curBuf = bytes;
        UnsignedWord curLen = length;
        while (curLen.notEqual(0)) {
            if (handle == -1) {
                return false;
            }

            CIntPointer bytesWritten = StackValue.get(CIntPointer.class);

            int ret = FileAPI.WriteFile(handle, curBuf, curLen, bytesWritten, WordFactory.nullPointer());

            if (ret == 0) {
                return false;
            }

            int writtenCount = bytesWritten.read();
            if (curLen.notEqual(writtenCount)) {
                return false;
            }

            curBuf = curBuf.addressOf(writtenCount);
            curLen = curLen.subtract(writtenCount);
        }
        return true;
    }

    static boolean flush(int handle) {
        if (handle == -1) {
            return false;
        }
        int result = FileAPI.FlushFileBuffers(handle);
        return (result != 0);
    }

}
