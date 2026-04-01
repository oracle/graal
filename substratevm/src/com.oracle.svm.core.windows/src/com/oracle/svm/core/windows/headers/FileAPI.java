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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

// Checkstyle: stop

/**
 * Definitions for Windows fileapi.h
 */
@CContext(WindowsDirectives.class)
public class FileAPI {

    /** Generic Access Rights */
    @CConstant
    public static native int GENERIC_READ();

    @CConstant
    public static native int GENERIC_WRITE();

    /** Creates or opens a file or I/O device. */
    @CFunction(transition = NO_TRANSITION)
    public static native HANDLE CreateFileW(WCharPointer lpFileName, int dwDesiredAccess, int dwShareMode,
                    PointerBase lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes,
                    HANDLE hTemplateFile);

    /** CreateFile - dwShareMode Constants */
    @CConstant
    public static native int FILE_SHARE_READ();

    @CConstant
    public static native int FILE_SHARE_WRITE();

    @CConstant
    public static native int FILE_SHARE_DELETE();

    /** CreateFile - dwCreationDisposition Constants */
    @CConstant
    public static native int OPEN_EXISTING();

    @CConstant
    public static native int CREATE_NEW();

    @CConstant
    public static native int CREATE_ALWAYS();

    @CConstant
    public static native int FILE_ATTRIBUTE_NORMAL();

    @CConstant
    public static native int FILE_BEGIN();

    @CConstant
    public static native int FILE_CURRENT();

    /**
     * 64-bit integer needed by various APIs.
     */
    @CStruct
    public interface LARGE_INTEGER extends PointerBase {
        @CField("QuadPart")
        long getQuadPart();

        @CField("QuadPart")
        void setQuadPart(long value);
    }

    @CFunction
    public static native int WriteFile(HANDLE hFile, CCharPointer lpBuffer, UnsignedWord nNumberOfBytesToWrite,
                    CIntPointer lpNumberOfBytesWritten, PointerBase lpOverlapped);

    @CFunction
    public static native int FlushFileBuffers(HANDLE hFile);

    @CConstant
    public static native int STD_INPUT_HANDLE();

    @CConstant
    public static native int STD_OUTPUT_HANDLE();

    @CConstant
    public static native int STD_ERROR_HANDLE();

    @CFunction
    public static native HANDLE GetStdHandle(int stdHandle);

    @CFunction(transition = NO_TRANSITION)
    public static native int GetTempPathW(int nBufferLength, WCharPointer lpBuffer);

    public static class NoTransition {
        @CFunction(transition = NO_TRANSITION)
        public static native int ReadFile(HANDLE hFile, CCharPointer lpBuffer, UnsignedWord nNumberOfBytesToRead, CIntPointer lpNumberOfBytesRead, PointerBase lpOverlapped);

        @CFunction(transition = NO_TRANSITION)
        public static native int GetFileSizeEx(HANDLE hFile, LARGE_INTEGER lpFileSize);

        @CFunction(transition = NO_TRANSITION)
        public static native int SetFilePointerEx(HANDLE hFile, long liDistanceToMove, LARGE_INTEGER lpNewFilePointer, int dwMoveMethod);

        @CFunction(transition = NO_TRANSITION)
        public static native int WriteFile(HANDLE hFile, CCharPointer lpBuffer, UnsignedWord nNumberOfBytesToWrite, CIntPointer lpNumberOfBytesWritten, PointerBase lpOverlapped);

        @CFunction(transition = NO_TRANSITION)
        public static native int FlushFileBuffers(HANDLE hFile);

        @CFunction(transition = NO_TRANSITION)
        public static native HANDLE GetStdHandle(int stdHandle);
    }
}
