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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULEPointer;

// Checkstyle: stop

/**
 * Definitions for Windows libloaderapi.h
 */
@CContext(WindowsDirectives.class)
public class LibLoaderAPI {

    /** Frees the loaded dynamic-link library (DLL) module. */
    @CFunction(transition = NO_TRANSITION)
    public static native int FreeLibrary(HMODULE hLibModule);

    /** Retrieves the fully qualified path of the file that contains the specified module. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetModuleFileNameA(HMODULE hModule, CCharPointer lpFilename, int nSize);

    /** Retrieves the fully qualified path of the file that contains the specified module. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetModuleFileNameW(HMODULE hModule, WCharPointer lpFilename, int nSize);

    /** Retrieves a module handle for the specified module. */
    @CFunction(transition = NO_TRANSITION)
    public static native HMODULE GetModuleHandleA(CCharPointer lpModuleName);

    /** Retrieves a module handle for the specified module. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetModuleHandleExA(int dwFlags, CCharPointer lpModuleName, HMODULEPointer phModule);

    /** The lpModuleName parameter is an address in the module. */
    @CConstant
    public static native int GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS();

    /** The reference count for the module is not incremented. */
    @CConstant
    public static native int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT();

    /** Retrieves the address of an exported function or variable from the specified module. */
    @CFunction(transition = NO_TRANSITION)
    public static native PointerBase GetProcAddress(HMODULE hModule, CCharPointer lpProcName);

    /** Loads the specified module into the address space of the calling process. */
    @CFunction(transition = NO_TRANSITION)
    public static native HMODULE LoadLibraryA(CCharPointer lpLibFileName);
}
