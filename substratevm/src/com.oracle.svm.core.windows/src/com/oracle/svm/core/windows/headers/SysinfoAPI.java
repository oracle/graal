/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

// Checkstyle: stop

/**
 * Definitions for Windows sysinfoapi.h
 */
@CContext(WindowsDirectives.class)
public class SysinfoAPI {

    /** Retrieves the path of the system directory. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetSystemDirectoryW(WCharPointer lpBuffer, int uSize);

    /** Return information about the current computer system. */
    @CFunction(transition = NO_TRANSITION)
    public static native void GetSystemInfo(SYSTEM_INFO lpSystemInfo);

    /** Structure containing information about the current computer system. */
    @CStruct
    public interface SYSTEM_INFO extends PointerBase {
        @CField
        short wProcessorArchitecture();

        @CField
        short wReserved();

        @CField
        int dwPageSize();

        @CField
        PointerBase lpMinimumApplicationAddress();

        @CField
        PointerBase lpMaximumApplicationAddress();

        @CField
        long dwActiveProcessorMask();

        @CField
        int dwNumberOfProcessors();

        @CField
        int dwProcessorType();

        @CField
        int dwAllocationGranularity();

        @CField
        short wProcessorLevel();

        @CField
        short wProcessorRevision();
    }

    /** Retrieves the path of the Windows directory. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetWindowsDirectoryW(WCharPointer lpBuffer, int uSize);

    /** Return information about physical and virtual memory. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GlobalMemoryStatusEx(MEMORYSTATUSEX lpBuffer);

    /** Structure containing information about physical and virtual memory. */
    @CStruct
    public interface MEMORYSTATUSEX extends PointerBase {
        @CField
        int dwLength();

        @CField
        void set_dwLength(int value);

        @CField
        int dwMemoryLoad();

        @CField
        long ullTotalPhys();

        @CField
        long ullAvailPhys();

        @CField
        long ullTotalPageFile();

        @CField
        long ullAvailPageFile();

        @CField
        long ullTotalVirtual();

        @CField
        long ullAvailVirtual();

        @CField
        long ullAvailExtendedVirtual();
    }

    /** Returns version information about the currently running operating system. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetVersionExA(OSVERSIONINFOEXA lpVersionInformation);

    /** Contains operating system version information. */
    @CStruct
    public interface OSVERSIONINFOEXA extends PointerBase {
        @CField
        int dwOSVersionInfoSize();

        @CField
        void dwOSVersionInfoSize(int value);

        @CField
        int dwMajorVersion();

        @CField
        int dwMinorVersion();

        @CField
        int dwBuildNumber();

        @CField
        int dwPlatformId();

        @CFieldAddress
        CCharPointer szCSDVersion();

        @CField
        short wServicePackMajor();

        @CField
        short wServicePackMinor();

        @CField
        short wSuiteMask();

        @CField
        byte wProductType();

        @CField
        byte wReserved();
    }
}
