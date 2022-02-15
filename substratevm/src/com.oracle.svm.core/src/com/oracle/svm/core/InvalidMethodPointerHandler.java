/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.annotate.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Provides stub methods that can be used for uninitialized method pointers. Instead of a segfault,
 * the stubs provide full diagnostic output with a stack trace.
 */
public final class InvalidMethodPointerHandler {
    public static final Method INVALID_VTABLE_ENTRY_HANDLER_METHOD = ReflectionUtil.lookupMethod(InvalidMethodPointerHandler.class, "invalidVTableEntryHandler");
    public static final String INVALID_VTABLE_ENTRY_MSG = "Fatal error: Virtual method call used an illegal vtable entry that was seen as unused by the static analysis";

    public static final Method METHOD_POINTER_NOT_COMPILED_HANDLER_METHOD = ReflectionUtil.lookupMethod(InvalidMethodPointerHandler.class, "methodPointerNotCompiledHandler");
    public static final String METHOD_POINTER_NOT_COMPILED_MSG = "Fatal error: Method pointer invoked on a method that was not compiled because it was not seen as invoked by the static analysis nor was it directly registered for compilation";

    @StubCallingConvention
    @NeverInline("We need a separate frame that stores all registers")
    private static void invalidVTableEntryHandler() {
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        failFatally(callerSP, callerIP, INVALID_VTABLE_ENTRY_MSG);
    }

    @StubCallingConvention
    @NeverInline("We need a separate frame that stores all registers")
    private static void methodPointerNotCompiledHandler() {
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        failFatally(callerSP, callerIP, METHOD_POINTER_NOT_COMPILED_MSG);
    }

    @Uninterruptible(reason = "Prevent safepoints until everything is set up for printing the fatal error.", calleeMustBe = false)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
    private static void failFatally(Pointer callerSP, CodePointer callerIP, String message) {
        SafepointBehavior.preventSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        /*
         * Since this is so far the only use case we have for a fatal error with
         * frameHasCalleeSavedRegisters=true, we inline the usual fatal error handling. Note that
         * this has the added benefit that the instructions printed as part of the crash dump are
         * from the method that has the illegal vtable call. That can be helpful when debugging the
         * cause of the fatal error.
         */
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, callerIP, message, null);
        if (log != null) {
            SubstrateDiagnostics.printFatalError(log, callerSP, callerIP, WordFactory.nullPointer(), true);
            log.string(message).newline();
        }
        logHandler.fatalError();
    }
}
