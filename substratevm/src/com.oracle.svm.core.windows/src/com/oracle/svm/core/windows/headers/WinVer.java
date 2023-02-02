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
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

// Checkstyle: stop

/**
 * Definitions for Windows winver.h
 */
@CLibrary("version")
@CContext(WindowsDirectives.class)
public class WinVer {

    /** Determines the size of version information for a specified file if available. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetFileVersionInfoSizeW(WCharPointer lptstrFilename, CIntPointer lpdwHandle);

    /** Retrieves version information for the specified file. */
    @CFunction(transition = NO_TRANSITION)
    public static native int GetFileVersionInfoW(WCharPointer lptstrFilename, int dwHandle, int dwLen, VoidPointer lpData);

    /** Retrieves specified version information from the specified version-information resource. */
    @CFunction(transition = NO_TRANSITION)
    public static native int VerQueryValueW(VoidPointer pBlock, WCharPointer lpSubBlock, WordPointer lplpBuffer, CIntPointer puLen);
}
