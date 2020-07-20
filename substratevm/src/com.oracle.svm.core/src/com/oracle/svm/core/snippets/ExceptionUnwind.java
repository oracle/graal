/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.snippets;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

public abstract class ExceptionUnwind {

    public static final SubstrateForeignCallDescriptor UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS = SnippetRuntime.findForeignCall(ExceptionUnwind.class,
                    "unwindExceptionWithoutCalleeSavedRegisters", true, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS = SnippetRuntime.findForeignCall(ExceptionUnwind.class, "unwindExceptionWithCalleeSavedRegisters",
                    true, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS,
                    UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS
    };

    public static final FastThreadLocalObject<Throwable> currentException = FastThreadLocalFactory.createObject(Throwable.class);

    @Uninterruptible(reason = "Called from uninterruptible callers.", mayBeInlined = true)
    static boolean exceptionsAreFatal() {
        /*
         * If an exception is thrown while the thread is not in the Java state, most likely
         * something went wrong in our state transition code. We cannot reliably unwind the stack,
         * so exiting quickly is better.
         */
        return SubstrateOptions.MultiThreaded.getValue() && !VMThreads.StatusSupport.isStatusJava();
    }

    /** Foreign call: {@link #UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not execute recurring callbacks or a stack overflow check.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when unwinding the stack.")
    private static void unwindExceptionWithoutCalleeSavedRegisters(Throwable exception, Pointer callerSP) {
        /*
         * Make the yellow zone available and pause recurring callbacks to avoid that unexpected
         * exceptions are thrown. This is reverted before execution continues in the exception
         * handler (see ExceptionStackFrameVisitor.visitFrame).
         */
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        ThreadingSupportImpl.pauseRecurringCallback("Arbitrary code must not be executed while unwinding.");

        unwindExceptionInterruptible(exception, callerSP, false);
    }

    /** Foreign call: {@link #UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not execute recurring callbacks or a stack overflow check.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when unwinding the stack.")
    private static void unwindExceptionWithCalleeSavedRegisters(Throwable exception, Pointer callerSP) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        ThreadingSupportImpl.pauseRecurringCallback("Arbitrary code must not be executed while unwinding.");

        unwindExceptionInterruptible(exception, callerSP, true);
    }

    /*
     * The stack walking objects must be stateless (no instance fields), because multiple threads
     * can use them simultaneously. All state must be in separate VMThreadLocals.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when unwinding the stack.")
    private static void unwindExceptionInterruptible(Throwable exception, Pointer callerSP, boolean fromMethodWithCalleeSavedRegisters) {
        if (currentException.get() != null) {
            reportRecursiveUnwind(exception);
            return; /* Unreachable code. */
        }
        currentException.set(exception);

        if (exceptionsAreFatal()) {
            reportFatalUnwind(exception);
            return; /* Unreachable code. */
        }

        if (ImageSingletons.contains(ExceptionUnwind.class)) {
            ImageSingletons.lookup(ExceptionUnwind.class).customUnwindException(callerSP);
        } else {
            defaultUnwindException(callerSP, fromMethodWithCalleeSavedRegisters);
        }

        /*
         * The stack walker does not return if an exception handler is found, but instead performs a
         * direct jump to the handler. So when we reach this point, we can just report an unhandled
         * exception.
         */
        reportUnhandledException(exception);
        /* Unreachable code. */
    }

    /**
     * Exception unwinding cannot be called recursively. The most likely reason to end up here is an
     * exception being thrown while walking the stack to find an exception handler.
     */
    private static void reportRecursiveUnwind(Throwable exception) {
        Log.log().string("Fatal error: recursion in exception handling: ").string(exception.getClass().getName());
        Log.log().string(" thrown while unwinding ").string(currentException.get().getClass().getName()).newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /**
     * Exception unwinding is interruptible. Otherwise a lot of code would need to be marked as
     * uninterruptible (like all of the code metadata lookup methods), and unwinding also loops over
     * the potentially large number of stack frames. But exceptions can be thrown from code that is
     * truly uninterruptible, because it is impossible to write Java code that is free of implicit
     * exception checks such as null pointer or array bounds checks. In such cases, exceptions are
     * treated as fatal errors.
     */
    private static void reportFatalUnwind(Throwable exception) {
        Log.log().string("Fatal error: exception unwind while thread is not in Java state: ");
        Log.log().exception(exception);
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /**
     * This is the final line of defense if an entry point from C to Java does not have a proper
     * catch-all exception handler. For the Java main method and newly started Java threads, the
     * proper exception handling and reporting of "unhandled" user exceptions is at a higher level
     * using a normal Java catch-all exception handler.
     */
    private static void reportUnhandledException(Throwable exception) {
        Log.log().string("Fatal error: unhandled exception in isolate ").hex(CurrentIsolate.getIsolate()).string(": ");
        Log.log().exception(exception);
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /** Hook to allow a {@link Feature} to install custom exception unwind code. */
    protected abstract void customUnwindException(Pointer callerSP);

    @Uninterruptible(reason = "Prevent deoptimization apart from the few places explicitly considered safe for deoptimization")
    private static void defaultUnwindException(Pointer startSP, boolean fromMethodWithCalleeSavedRegisters) {
        boolean hasCalleeSavedRegisters = fromMethodWithCalleeSavedRegisters;
        CodePointer startIP = FrameAccess.singleton().readReturnAddress(startSP);

        /*
         * callerSP and startIP identify already the caller of the frame that wants to unwind an
         * exception. So we can start looking for the exception handler immediately in that frame,
         * without skipping any frames in between.
         */
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        JavaStackWalker.initWalk(walk, startSP, startIP);

        while (true) {
            SimpleCodeInfoQueryResult codeInfoQueryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
            Pointer sp = walk.getSP();
            CodePointer ip = walk.getPossiblyStaleIP();

            DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptFrame == null) {
                UntetheredCodeInfo untetheredInfo = walk.getIPCodeInfo();
                if (untetheredInfo.isNull()) {
                    JavaStackWalker.reportUnknownFrameEncountered(sp, ip, deoptFrame);
                    return; /* Unreachable code. */
                }

                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo codeInfo = CodeInfoAccess.convert(untetheredInfo, tether);

                    lookupCodeInfoInterruptible(codeInfo, ip, codeInfoQueryResult);
                    /*
                     * Frame could have been deoptimized during interruptible lookup above, check
                     * again.
                     */
                    deoptFrame = Deoptimizer.checkDeoptimized(sp);
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }

            if (deoptFrame != null && DeoptimizationSupport.enabled()) {
                /* Deoptimization entry points always have an exception handler. */
                deoptTakeExceptionInterruptible(deoptFrame);
                jumpToHandler(sp, DeoptimizationSupport.getDeoptStubPointer(), hasCalleeSavedRegisters);
                return; /* Unreachable code. */
            }

            long exceptionOffset = codeInfoQueryResult.getExceptionOffset();
            if (exceptionOffset != CodeInfoQueryResult.NO_EXCEPTION_OFFSET) {
                CodePointer handlerIP = (CodePointer) ((UnsignedWord) ip).add(WordFactory.signed(exceptionOffset));
                jumpToHandler(sp, handlerIP, hasCalleeSavedRegisters);
                return; /* Unreachable code. */
            }

            /* No handler found in this frame, walk to caller frame. */
            hasCalleeSavedRegisters = CodeInfoQueryResult.hasCalleeSavedRegisters(codeInfoQueryResult.getEncodedFrameSize());
            if (!JavaStackWalker.continueWalk(walk, codeInfoQueryResult, deoptFrame)) {
                /* No more caller frame found. */
                return;
            }
        }
    }

    @Uninterruptible(reason = "Prevent deotpimization while dispatching to exception handler")
    private static void jumpToHandler(Pointer sp, CodePointer handlerIP, boolean hasCalleeSavedRegisters) {
        Throwable exception = currentException.get();
        currentException.set(null);

        ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
        StackOverflowCheck.singleton().protectYellowZone();

        if (hasCalleeSavedRegisters) {
            /*
             * The fromMethodWithCalleeSavedRegisters parameter of farReturn must be a compile-time
             * constant. The method is intrinsified, and the constant parameter simplifies code
             * generation for the intrinsic.
             */
            KnownIntrinsics.farReturn(exception, sp, handlerIP, true);
        } else {
            KnownIntrinsics.farReturn(exception, sp, handlerIP, false);
        }
        /* Unreachable code: the intrinsic performs a jump to the specified instruction pointer. */
    }

    @Uninterruptible(reason = "Wrap call to interruptible code.", calleeMustBe = false)
    private static void deoptTakeExceptionInterruptible(DeoptimizedFrame deoptFrame) {
        deoptFrame.takeException();
    }

    @Uninterruptible(reason = "Wrap call to interruptible code.", calleeMustBe = false)
    private static void lookupCodeInfoInterruptible(CodeInfo codeInfo, CodePointer ip, SimpleCodeInfoQueryResult codeInfoQueryResult) {
        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), codeInfoQueryResult);
    }
}
