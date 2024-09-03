/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.windows.headers.ErrHandlingAPI.EXCEPTION_ACCESS_VIOLATION;
import static com.oracle.svm.core.windows.headers.ErrHandlingAPI.EXCEPTION_IN_PAGE_ERROR;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.ErrHandlingAPI;

@AutomaticallyRegisteredImageSingleton(SubstrateSegfaultHandler.class)
class WindowsSubstrateSegfaultHandler extends SubstrateSegfaultHandler {
    private static final int EX_READ = 0;
    private static final int EX_WRITE = 1;
    private static final int EX_EXECUTE = 8;

    @Override
    protected void installInternal() {
        /*
         * Normally we would use SEH (Structured Exception Handling) for this. However, in order for
         * SEH to work, the OS must be able to perform stack walking. On x64, this requires the
         * presence of unwinding info for all methods in the PE image, and we do not currently
         * support its generation.
         *
         * This leaves us with two options to choose from: VEH (Vectored Exception Handler) and VCH
         * (Vectored Continue Handler). The problem with VEHs is that they are called too early,
         * before any SEH processing, so we can't know if an exception will be handled, and we don't
         * want to interfere with the native code.
         *
         * On the other hand, VCHs are called after SEH processing, but only under certain
         * conditions. In fact, this implementation actually relies on the OS to call them
         * unconditionally due to the lack of stack-walking support. While this is obviously far
         * from ideal, it should be good enough for now.
         */
        if (ErrHandlingAPI.AddVectoredContinueHandler(0, HANDLER_LITERAL.getFunctionPointer()).isNull()) {
            VMError.shouldNotReachHere("SubstrateSegfaultHandler installation failed.");
        }
    }

    private static final CEntryPointLiteral<CFunctionPointer> HANDLER_LITERAL = CEntryPointLiteral.create(WindowsSubstrateSegfaultHandler.class, "handler", ErrHandlingAPI.EXCEPTION_POINTERS.class);

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.")
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault signal handler.")
    private static int handler(ErrHandlingAPI.EXCEPTION_POINTERS exceptionInfo) {
        ErrHandlingAPI.EXCEPTION_RECORD exceptionRecord = exceptionInfo.ExceptionRecord();
        if (exceptionRecord.ExceptionCode() != ErrHandlingAPI.EXCEPTION_ACCESS_VIOLATION()) {
            /* Not a segfault. */
            return ErrHandlingAPI.EXCEPTION_CONTINUE_SEARCH();
        }

        ErrHandlingAPI.CONTEXT context = exceptionInfo.ContextRecord();
        if (tryEnterIsolate(context)) {
            dump(exceptionInfo, context);
            throw shouldNotReachHere();
        }

        /*
         * Attach failed - nothing we need to do. If there is no other OS-level exception handler
         * installed, then Windows will abort the process.
         */
        return ErrHandlingAPI.EXCEPTION_CONTINUE_SEARCH();
    }

    @Override
    protected void printSignalInfo(Log log, PointerBase signalInfo) {
        ErrHandlingAPI.EXCEPTION_POINTERS exceptionInfo = (ErrHandlingAPI.EXCEPTION_POINTERS) signalInfo;
        ErrHandlingAPI.EXCEPTION_RECORD exceptionRecord = exceptionInfo.ExceptionRecord();

        int exceptionCode = exceptionRecord.ExceptionCode();
        log.string("siginfo: ExceptionCode: ").signed(exceptionCode);

        int numParameters = exceptionRecord.NumberParameters();
        if ((exceptionCode == EXCEPTION_ACCESS_VIOLATION() || exceptionCode == EXCEPTION_IN_PAGE_ERROR()) && numParameters >= 2) {
            CLongPointer exInfo = exceptionRecord.ExceptionInformation();
            long operation = exInfo.addressOf(0).read();
            if (operation == EX_READ) {
                log.string(", reading address");
            } else if (operation == EX_WRITE) {
                log.string(", writing address");
            } else if (operation == EX_EXECUTE) {
                log.string(", data execution prevention violation at address");
            } else {
                log.string(", ExceptionInformation=").zhex(operation);
            }
            log.string(" ");
            printSegfaultAddressInfo(log, exInfo.addressOf(1).read());
        } else {
            if (numParameters > 0) {
                log.string(", ExceptionInformation=");
                CLongPointer exInfo = exceptionRecord.ExceptionInformation();
                for (int i = 0; i < numParameters; i++) {
                    log.string(" ").zhex(exInfo.addressOf(i).read());
                }
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.")
    private static RuntimeException shouldNotReachHere() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }
}
