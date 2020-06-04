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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.annotate.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.annotate.RestrictHeapAccess.Access.NO_HEAP_ACCESS;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NotIncludedAutomatically;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.ErrHandlingAPI;

@AutomaticFeature
class WindowsSubstrateSegfaultHandlerFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateSegfaultHandler.class, new WindowsSubstrateSegfaultHandler());
    }
}

class WindowsSubstrateSegfaultHandler extends SubstrateSegfaultHandler {
    @Override
    protected void install() {
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

    private static final CEntryPointLiteral<CFunctionPointer> HANDLER_LITERAL = CEntryPointLiteral.create(WindowsSubstrateSegfaultHandler.class,
                    "handler", ErrHandlingAPI.EXCEPTION_POINTERS.class);

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.SymbolOnly, include = NotIncludedAutomatically.class)
    @Uninterruptible(reason = "Must be uninterruptible until we get immune to safepoints.")
    @RestrictHeapAccess(access = NO_HEAP_ACCESS, reason = "We have yet to enter the isolate.")
    private static int handler(ErrHandlingAPI.EXCEPTION_POINTERS exceptionInfo) {
        ErrHandlingAPI.EXCEPTION_RECORD exceptionRecord = exceptionInfo.ExceptionRecord();
        if (exceptionRecord.ExceptionCode() != ErrHandlingAPI.EXCEPTION_ACCESS_VIOLATION()) {
            /* Not a segfault. */
            return ErrHandlingAPI.EXCEPTION_CONTINUE_SEARCH();
        }

        ErrHandlingAPI.CONTEXT context = exceptionInfo.ContextRecord();
        if (tryEnterIsolate(context)) {
            dump(context);
            throw shouldNotReachHere();
        }
        /* Nothing we can do. */
        return ErrHandlingAPI.EXCEPTION_CONTINUE_SEARCH();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in segfault handler.", overridesCallers = true)
    private static RuntimeException shouldNotReachHere() {
        return VMError.shouldNotReachHere();
    }
}
