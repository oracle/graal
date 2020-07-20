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
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.RegisterDumper;

// Checkstyle: stop

/**
 * Definitions for Windows errhandlingapi.h
 */
@CContext(WindowsDirectives.class)
public class ErrHandlingAPI {

    /** Registers a vectored continue handler. */
    @CFunction(transition = NO_TRANSITION)
    public static native PointerBase AddVectoredContinueHandler(int first, CFunctionPointer handler);

    @CConstant
    public static native int EXCEPTION_CONTINUE_SEARCH();

    /** Contains pointers to exception and context records. */
    @CStruct
    public interface EXCEPTION_POINTERS extends PointerBase {
        @CField
        EXCEPTION_RECORD ExceptionRecord();

        @CField
        CONTEXT ContextRecord();
    }

    /** Contains a description of the exception. */
    @CStruct
    public interface EXCEPTION_RECORD extends PointerBase {
        @CField
        int ExceptionCode();
    }

    @CConstant
    public static native int EXCEPTION_ACCESS_VIOLATION();

    /** Contains processor-specific register data. */
    @CStruct
    public interface CONTEXT extends RegisterDumper.Context {
        @CField
        int EFlags();

        @CField
        long Rax();

        @CField
        long Rcx();

        @CField
        long Rdx();

        @CField
        long Rbx();

        @CField
        long Rsp();

        @CField
        long Rbp();

        @CField
        long Rsi();

        @CField
        long Rdi();

        @CField
        long R8();

        @CField
        long R9();

        @CField
        long R10();

        @CField
        long R11();

        @CField
        long R12();

        @CField
        long R13();

        @CField
        long R14();

        @CField
        long R15();

        @CField
        long Rip();
    }
}
