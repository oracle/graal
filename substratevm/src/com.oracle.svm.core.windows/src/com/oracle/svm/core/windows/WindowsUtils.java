/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;

public class WindowsUtils {
    private static final long MAX_DWORD = (1L << 32) - 1;

    @TargetClass(className = "java.lang.ProcessImpl")
    private static final class Target_java_lang_ProcessImpl {
        @Alias long handle;
    }

    public static int getpid(java.lang.Process process) {
        Target_java_lang_ProcessImpl processImpl = SubstrateUtil.cast(process, Target_java_lang_ProcessImpl.class);
        return com.oracle.svm.core.windows.headers.Process.NoTransitions.GetProcessId(WordFactory.pointer(processImpl.handle));
    }

    @TargetClass(java.io.FileDescriptor.class)
    private static final class Target_java_io_FileDescriptor {
        /** Invalidates the standard FileDescriptors, which are allowed in the image heap. */
        static class InvalidHandleValueComputer implements FieldValueTransformer {
            @Override
            public Object transform(Object receiver, Object originalValue) {
                return -1L;
            }
        }

        @Alias @RecomputeFieldValue(kind = Custom, declClass = InvalidHandleValueComputer.class)//
        long handle;
    }

    static void setHandle(FileDescriptor descriptor, HANDLE handle) {
        SubstrateUtil.cast(descriptor, Target_java_io_FileDescriptor.class).handle = handle.rawValue();
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
    public static boolean writeBytes(HANDLE handle, CCharPointer bytes, UnsignedWord length) {
        if (handle == WinBase.INVALID_HANDLE_VALUE()) {
            return false;
        }

        CCharPointer curBuf = bytes;
        UnsignedWord curLen = length;
        while (curLen.notEqual(0)) {
            CIntPointer pDwBytesWritten = UnsafeStackValue.get(CIntPointer.class);

            int ret = FileAPI.WriteFile(handle, curBuf, toDwordOrMaxValue(curLen), pDwBytesWritten, WordFactory.nullPointer());
            if (ret == 0) {
                return false;
            }

            long written = WindowsUtils.dwordToLong(pDwBytesWritten.read());
            curBuf = curBuf.addressOf(WordFactory.signed(written));
            curLen = curLen.subtract(WordFactory.signed(written));
        }
        return true;
    }

    static boolean flush(HANDLE handle) {
        if (handle == WinBase.INVALID_HANDLE_VALUE()) {
            return false;
        }
        int result = FileAPI.FlushFileBuffers(handle);
        return (result != 0);
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

    /** Sentinel value denoting the uninitialized kernel handle. */
    public static final PointerBase UNINITIALIZED_HANDLE = WordFactory.pointer(1);

    @CPointerTo(nameOfCType = "void*")
    interface CFunctionPointerPointer<T extends CFunctionPointer> extends PointerBase {
        T read();

        void write(T value);
    }

    /** Sentinel value denoting the uninitialized pointer. */
    static final PointerBase UNINITIALIZED_POINTER = WordFactory.pointer(0xBAD);

    /**
     * Retrieves and caches the address of an exported function from an already loaded DLL if the
     * cached function pointer is {@linkplain #UNINITIALIZED_POINTER uninitialized}, otherwise it
     * returns the cached value.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T extends CFunctionPointer> T getAndCacheFunctionPointer(CFunctionPointerPointer<T> cachedFunctionPointer,
                    CCharPointer dllName, CCharPointer functionName) {
        T functionPointer = cachedFunctionPointer.read();
        if (functionPointer.equal(UNINITIALIZED_POINTER)) {
            functionPointer = getFunctionPointer(dllName, functionName, false);
            cachedFunctionPointer.write(functionPointer);
        }
        return functionPointer;
    }

    /** Retrieves the address of an exported function from an already loaded DLL. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T extends CFunctionPointer> T getFunctionPointer(CCharPointer dllName, CCharPointer functionName, boolean failOnError) {
        PointerBase functionPointer = LibLoaderAPI.GetProcAddress(getDLLHandle(dllName), functionName);
        if (functionPointer.isNull() && failOnError) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), functionName);
        }
        return (T) functionPointer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static HMODULE getDLLHandle(CCharPointer dllName) {
        HMODULE dllHandle = LibLoaderAPI.GetModuleHandleA(dllName);
        if (dllHandle.isNull()) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), dllName);
        }
        return dllHandle;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int toUnsignedIntOrMaxValue(UnsignedWord value) {
        return toUnsignedIntOrMaxValue(value.rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int toUnsignedIntOrMaxValue(long value) {
        assert value >= 0;
        if (value > MAX_DWORD) {
            return (int) MAX_DWORD;
        }
        return (int) value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long unsignedIntToLong(int value) {
        return value & 0xFFFFFFFFL;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord unsignedIntToUnsignedWord(int value) {
        return WordFactory.unsigned(unsignedIntToLong(value));
    }

    /* DWORD is an unsigned 32-bit value. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int toDwordOrMaxValue(UnsignedWord value) {
        return toUnsignedIntOrMaxValue(value.rawValue());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int toDwordOrMaxValue(long value) {
        return toUnsignedIntOrMaxValue(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long dwordToLong(int value) {
        return unsignedIntToLong(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord dwordToUnsignedWord(int value) {
        return unsignedIntToUnsignedWord(value);
    }
}
