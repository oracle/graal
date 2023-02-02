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
package com.oracle.svm.core.windows.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

// Checkstyle: stop

@CContext(WindowsDirectives.class)
public class WinBase {

    public static final int MAX_PATH = 260;
    public static final int UNLEN = 256;

    /**
     * Windows opaque Handle type
     */
    public interface HANDLE extends PointerBase {
    }

    @CConstant
    public static native HANDLE INVALID_HANDLE_VALUE();

    @CPointerTo(nameOfCType = "HANDLE")
    public interface LPHANDLE extends PointerBase {
        HANDLE read();
    }

    /**
     * Windows Module Handle type
     */
    public interface HMODULE extends PointerBase {
    }

    @CPointerTo(nameOfCType = "HMODULE")
    public interface HMODULEPointer extends PointerBase {
        public HMODULE read();

        public void write(HMODULE value);
    }

    /**
     * Contains a 64-bit value representing the number of 100-nanosecond intervals since January 1,
     * 1601 (UTC).
     */
    @CStruct
    public interface FILETIME extends PointerBase {
        @CField
        int dwLowDateTime();

        @CField
        int dwHighDateTime();
    }

    /**
     * GetLastError - Return additional error information
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetLastError();

    @CConstant
    public static native int ERROR_TIMEOUT();

    /**
     * QueryPerformance Counter - used for elapsed time
     */

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void QueryPerformanceCounter(CLongPointer counter);

    /**
     * QueryPerformance Frequency - used for elapsed time
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void QueryPerformanceFrequency(CLongPointer counter);

    /**
     * CloseHandle
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int CloseHandle(HANDLE hFile);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int DuplicateHandle(HANDLE hSourceProcessHandle, HANDLE hSourceHandle, HANDLE hTargetProcessHandle, LPHANDLE lpTargetHandle, int dwDesiredAccess, boolean bInheritHandle,
                    int dwOptions);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetCurrentDirectoryW(int nBufferLength, WCharPointer lpBuffer);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetUserNameW(WCharPointer lpBuffer, CIntPointer pcbBuffer);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetUserProfileDirectoryW(HANDLE hToken, WCharPointer lpProfileDir, CIntPointer lpcchSize);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native WinBase.HANDLE CreateSemaphoreA(VoidPointer lpSemaphoreAttributes, int lInitialCount, int lMaximumCount, VoidPointer lpName);

}
