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
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions for Windows memoryapi.h
 */
@CContext(WindowsDirectives.class)
public class MemoryAPI {

    /** Memory Protection Constants */
    @CConstant
    public static native int PAGE_EXECUTE();

    @CConstant
    public static native int PAGE_EXECUTE_READ();

    @CConstant
    public static native int PAGE_EXECUTE_READWRITE();

    @CConstant
    public static native int PAGE_NOACCESS();

    @CConstant
    public static native int PAGE_READONLY();

    @CConstant
    public static native int PAGE_READWRITE();

    /** Reserves, commits, or changes the state of a region of pages. */
    @CFunction(transition = NO_TRANSITION)
    public static native Pointer VirtualAlloc(PointerBase lpAddress, UnsignedWord dwSize, int flAllocationType, int flProtect);

    /** VirtualAlloc - flAllocationType Constants */
    @CConstant
    public static native int MEM_COMMIT();

    @CConstant
    public static native int MEM_RESERVE();

    /** Releases, decommits, or releases and decommits a region of pages. */
    @CFunction(transition = NO_TRANSITION)
    public static native int VirtualFree(PointerBase lpAddress, UnsignedWord dwSize, int dwFreeType);

    /** VirtualFree - dwFreeType Constants */
    @CConstant
    public static native int MEM_DECOMMIT();

    @CConstant
    public static native int MEM_RELEASE();

    /** Changes the protection on a region of committed pages. */
    @CFunction(transition = NO_TRANSITION)
    public static native int VirtualProtect(PointerBase lpAddress, UnsignedWord dwSize, int flNewProtect, CIntPointer lpflOldProtect);

    /** Retrieves information about a range of pages. */
    @CFunction(transition = NO_TRANSITION)
    public static native UnsignedWord VirtualQuery(PointerBase lpAddress, MEMORY_BASIC_INFORMATION lpBuffer, UnsignedWord dwLength);

    /** Contains information about a range of pages. */
    @CStruct
    public interface MEMORY_BASIC_INFORMATION extends PointerBase {
        @CField
        PointerBase BaseAddress();

        @CField
        PointerBase AllocationBase();

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
}
