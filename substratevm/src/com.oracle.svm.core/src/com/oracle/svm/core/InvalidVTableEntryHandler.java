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

// Checkstyle: allow reflection

import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Provides a stub method that can be used to fill otherwise unused vtable slots. Instead of a
 * segfault, this method provides a full diagnostic output with a stack trace.
 */
public final class InvalidVTableEntryHandler {
    public static final Method HANDLER_METHOD = ReflectionUtil.lookupMethod(InvalidVTableEntryHandler.class, "invalidVTableEntryHandler");
    public static final String MSG = "Fatal error: Virtual method call used an illegal vtable entry that was seen as unused by the static analysis";

    @StubCallingConvention
    @NeverInline("We need a separate frame that stores all registers")
    private static void invalidVTableEntryHandler() {
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

        /*
         * Since this is so far the only use case we have for a fatal error with
         * frameHasCalleeSavedRegisters=true, we inline the usual fatal error handling. Note that
         * this has the added benefit that the instructions printed as part of the crash dump are
         * from the method that has the illegal vtable call. That can be helpful when debugging the
         * cause of the fatal error.
         */
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
        Log log = Log.enterFatalContext(logHandler, callerIP, MSG, null);
        if (log != null) {
            SubstrateDiagnostics.print(log, callerSP, callerIP, WordFactory.nullPointer(), true);
            log.string(MSG).newline();
        }
        logHandler.fatalError();
    }
}
