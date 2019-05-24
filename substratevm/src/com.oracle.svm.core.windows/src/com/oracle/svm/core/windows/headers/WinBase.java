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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

// Checkstyle: stop

@CContext(WindowsDirectives.class)
@Platforms(Platform.WINDOWS.class)
public class WinBase {

    public static final int MAX_PATH = 260;

    /**
     * Windows opaque Handle type
     */
    public interface HANDLE extends WordBase {
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
     * Structure containing information about physical and virtual memory.
     */
    @CStruct(addStructKeyword = false)
    public interface MEMORYSTATUSEX extends PointerBase {
        @CField
        @AllowWideningCast
        int dwLength();

        @CField
        void set_dwLength(int value);

        @CField
        @AllowWideningCast
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

    /**
     * Return information about physical and virtual memory.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native boolean GlobalMemoryStatusEx(MEMORYSTATUSEX lpBuffer);

    /**
     * Structure containing information about the current computer system.
     */
    @CStruct(addStructKeyword = false)
    public interface SYSTEM_INFO extends PointerBase {
        @CField
        @AllowWideningCast
        int wProcessorArchitecture();

        @CField
        @AllowWideningCast
        int wReserved();

        @CField
        @AllowWideningCast
        int dwPageSize();

        @CField
        Pointer lpMinimumApplicationAddress();

        @CField
        Pointer lpMaximumApplicationAddress();

        @CField
        @AllowWideningCast
        long dwActiveProcessorMask();

        @CField
        @AllowWideningCast
        int dwNumberOfProcessors();

        @CField
        @AllowWideningCast
        int dwProcessorType();

        @CField
        @AllowWideningCast
        int dwAllocationGranularity();

        @CField
        @AllowWideningCast
        short wProcessorLevel();

        @CField
        @AllowWideningCast
        short wProcessorRevision();
    }

    /**
     * Return information about the current computer system.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void GetSystemInfo(SYSTEM_INFO lpSystemInfo);

    /**
     * CreateFileMapping - reserve, commit or change states of a region of pages.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native Pointer CreateFileMapping(long hFile, Pointer lpFileMappingAttributes, int flProtect, int dwMaximumSizeHigh, int dwMaximumSizeLow, Pointer lpName);

    /**
     * MapViewOfFile - dwDesiredAccess Constants
     */

    @CConstant
    public static native int FILE_MAP_EXECUTE();

    @CConstant
    public static native int FILE_MAP_READ();

    @CConstant
    public static native int FILE_MAP_WRITE();

    /**
     * MapViewOfFile - Maps a view of a file into the address space
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native Pointer MapViewOfFile(Pointer hFileMappingObject, int dwDesiredAccess, int dwFileOffsetHigh, int dwFileOffsetLow, UnsignedWord dwNumberOfBytesToMap);

    /**
     * VirtualAlloc - flAllocationType Constants
     */
    @CConstant
    public static native int MEM_COMMIT();

    @CConstant
    public static native int MEM_RESERVE();

    @CConstant
    public static native int MEM_RESET();

    @CConstant
    public static native int MEM_LARGE_PAGES();

    @CConstant
    public static native int MEM_PHYSICAL();

    @CConstant
    public static native int MEM_TOP_DOWN();

    @CConstant
    public static native int MEM_WRITE_WATCH();

    /**
     * VirtualAlloc - flProtect Constants
     */
    @CConstant
    public static native int PAGE_EXECUTE();

    @CConstant
    public static native int PAGE_EXECUTE_READ();

    @CConstant
    public static native int PAGE_EXECUTE_READWRITE();

    @CConstant
    public static native int PAGE_GUARD();

    @CConstant
    public static native int PAGE_NOACCESS();

    @CConstant
    public static native int PAGE_NOCACHE();

    @CConstant
    public static native int PAGE_READONLY();

    @CConstant
    public static native int PAGE_READWRITE();

    @CConstant
    public static native int PAGE_WRITECOMBINE();

    /**
     * VirtualAlloc - reserve, commit or change states of a region of pages.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native Pointer VirtualAlloc(PointerBase lpAddress, UnsignedWord dwSize, int flAllocationType, int flProtect);

    /**
     * VirtualFree - dwFreeType Constants
     */
    @CConstant
    public static native int MEM_DECOMMIT();

    @CConstant
    public static native int MEM_RELEASE();

    /**
     * VirtualFree
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int VirtualFree(PointerBase lpAddress, UnsignedWord dwSize, int dwFreeType);

    /**
     * VirtualProtect - change states of a region of pages.
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int VirtualProtect(PointerBase lpAddress, UnsignedWord dwSize, int flNewProtect, CIntPointer lpflOldProtect);

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

    /**
     * GetModuleHandle
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HMODULE GetModuleHandleA(PointerBase lpModuleName);

    /**
     * GetModuleFileNameA
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetModuleFileNameA(HMODULE hModule, CCharPointer lpFilename, int nSize);

    @CConstant
    public static native int GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS();

    @CConstant
    public static native int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT();

    /**
     * GetModuleHandleExA
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native boolean GetModuleHandleExA(int flags, PointerBase lpModuleName, HMODULEPointer module);

    /**
     * GetProcAddress
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native PointerBase GetProcAddress(HMODULE hModule, PointerBase lpProcName);

    /**
     * LoadLibraryA
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HMODULE LoadLibraryA(PointerBase lpFileName);

    /**
     * LoadLibraryExA
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HMODULE LoadLibraryExA(PointerBase lpFileName, int dummy, int flags);

    /**
     * FreeLibrary
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void FreeLibrary(PointerBase pointer);

    /**
     * SetDllDirectoryA
     */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native boolean SetDllDirectoryA(PointerBase lpPathName);

    @CStruct(addStructKeyword = false)
    public interface MEMORY_BASIC_INFORMATION extends PointerBase {
        @CField
        Pointer BaseAddress();

        @CField
        Pointer AllocationBase();

        @CField
        int AllocationProtect();

        @CField
        UnsignedWord RegionSize();

        @CField
        int State();

        @CField
        int Protect();

        @CField
        int Type();
    }

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native UnsignedWord VirtualQuery(PointerBase lpAddress, MEMORY_BASIC_INFORMATION lpBuffer, UnsignedWord dwLength);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetVersion();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetCurrentDirectoryA(long nBufferLength, CCharPointer lpBuffer);
}
