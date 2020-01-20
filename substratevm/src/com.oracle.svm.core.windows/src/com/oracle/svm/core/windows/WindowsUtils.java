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

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Custom;

import java.io.FileDescriptor;
import java.io.IOException;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.WinBase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.WINDOWS.class)
public class WindowsUtils {

    @TargetClass(className = "java.lang.ProcessImpl")
    private static final class Target_java_lang_ProcessImpl {
        @Alias long handle;
    }

    public static int getpid(java.lang.Process process) {
        Target_java_lang_ProcessImpl processImpl = SubstrateUtil.cast(process, Target_java_lang_ProcessImpl.class);
        return com.oracle.svm.core.windows.headers.Process.GetProcessId(WordFactory.pointer(processImpl.handle));
    }

    @TargetClass(java.io.FileDescriptor.class)
    private static final class Target_java_io_FileDescriptor {
        /** Invalidates the standard FileDescriptors, which are allowed in the image heap. */
        static class InvalidHandleValueComputer implements CustomFieldValueComputer {
            @Override
            public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
                return -1L;
            }
        }

        @Alias @RecomputeFieldValue(kind = Custom, declClass = InvalidHandleValueComputer.class)//
        long handle;
    }

    static void setHandle(FileDescriptor descriptor, long handle) {
        SubstrateUtil.cast(descriptor, Target_java_io_FileDescriptor.class).handle = handle;
    }

    static boolean outOfBounds(int off, int len, byte[] array) {
        return off < 0 || len < 0 || array.length - off < len;
    }

    /** Return the error string for the last error, or a default message. */
    public static String lastErrorString(String defaultMsg) {
        int error = WinBase.GetLastError();
        return defaultMsg + " GetLastError: " + error;
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

    @SuppressWarnings("unused")
    static void writeBytes(FileDescriptor descriptor, byte[] bytes, int off, int len, boolean append) throws IOException {
        if (bytes == null) {
            throw new NullPointerException();
        } else if (WindowsUtils.outOfBounds(off, len, bytes)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }

        try (PinnedObject bytesPin = PinnedObject.create(bytes)) {
            CCharPointer curBuf = bytesPin.addressOfArrayElement(off);
            UnsignedWord curLen = WordFactory.unsigned(len);
            /** Temp fix until we complete FileDescriptor substitutions. */
            int handle = FileAPI.GetStdHandle(FileAPI.STD_ERROR_HANDLE());

            CIntPointer bytesWritten = StackValue.get(CIntPointer.class);

            int ret = FileAPI.WriteFile(handle, curBuf, curLen, bytesWritten, WordFactory.nullPointer());

            if (ret == 0) {
                throw new IOException(lastErrorString("Write error"));
            }

            int writtenCount = bytesWritten.read();
            if (curLen.notEqual(writtenCount)) {
                throw new IOException(lastErrorString("Write error"));
            }
        }
    }

    private static long performanceFrequency = 0L;
    public static final long NANOSECS_PER_SEC = 1000000000L;
    public static final int NANOSECS_PER_MILLISEC = 1000000;

    /** Retrieve a nanosecond counter for elapsed time measurement. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getNanoCounter() {
        if (performanceFrequency == 0L) {
            CLongPointer count = StackValue.get(CLongPointer.class);
            WinBase.QueryPerformanceFrequency(count);
            performanceFrequency = count.read();
        }

        CLongPointer currentCount = StackValue.get(CLongPointer.class);
        WinBase.QueryPerformanceCounter(currentCount);
        double current = currentCount.read();
        double freq = performanceFrequency;
        return (long) ((current / freq) * NANOSECS_PER_SEC);
    }
}
