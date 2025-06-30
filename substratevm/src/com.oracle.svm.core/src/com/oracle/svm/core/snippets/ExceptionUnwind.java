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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.stack.JavaFrameAnchors.verifyTopFrameAnchor;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.BooleanPointer;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.word.Word;

public abstract class ExceptionUnwind {

    public static final SubstrateForeignCallDescriptor UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS = SnippetRuntime.findForeignCall(ExceptionUnwind.class,
                    "unwindExceptionWithoutCalleeSavedRegisters", NO_SIDE_EFFECT, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS = SnippetRuntime.findForeignCall(ExceptionUnwind.class, "unwindExceptionWithCalleeSavedRegisters",
                    NO_SIDE_EFFECT, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS,
                    UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS
    };

    public static final FastThreadLocalObject<Throwable> currentException = FastThreadLocalFactory.createObject(Throwable.class, "ExceptionUnwind.currentException");
    public static final FastThreadLocalBytes<BooleanPointer> lazyDeoptStubShouldReturnToExceptionHandler = FastThreadLocalFactory.createBytes(() -> SizeOf.get(BooleanPointer.class),
                    "ExceptionUnwind.lazyDeoptStubShouldReturnToExceptionHandler");

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLazyDeoptStubShouldReturnToExceptionHandler(boolean val) {
        lazyDeoptStubShouldReturnToExceptionHandler.getAddress().write(val);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean getLazyDeoptStubShouldReturnToExceptionHandler() {
        return lazyDeoptStubShouldReturnToExceptionHandler.getAddress().read();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean exceptionsAreFatal() {
        /*
         * If an exception is thrown while the thread is not in the Java state, most likely
         * something went wrong in our state transition code. We cannot reliably unwind the stack,
         * so exiting quickly is better.
         */
        return !VMThreads.StatusSupport.isStatusJava();
    }

    /** Foreign call: {@link #UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    private static void unwindExceptionWithoutCalleeSavedRegisters(Throwable exception, Pointer callerSP) {
        unwindException(exception, callerSP, false, false);
    }

    /** Foreign call: {@link #UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    private static void unwindExceptionWithCalleeSavedRegisters(Throwable exception, Pointer callerSP) {
        unwindException(exception, callerSP, true, false);
    }

    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    public static void unwindExceptionSkippingCaller(Throwable exception, Pointer callerSP) {
        unwindException(exception, callerSP, true, true);
    }

    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    private static void unwindException(Throwable exception, Pointer callerSP, boolean fromMethodWithCalleeSavedRegisters, boolean skipCaller) {
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
            VMError.guarantee(!skipCaller, "Skipping the caller frame is not supported with custom exception unwind");
            ImageSingletons.lookup(ExceptionUnwind.class).customUnwindException(callerSP);
        } else {
            defaultUnwindException(callerSP, fromMethodWithCalleeSavedRegisters, skipCaller);
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
    @Uninterruptible(reason = "Does not need to be uninterruptible because it throws a fatal error.", calleeMustBe = false)
    private static void reportRecursiveUnwind(Throwable exception) {
        Log.log().string("Fatal error: recursion in exception handling: ").string(exception.getClass().getName());
        Log.log().string(" thrown while unwinding ").string(currentException.get().getClass().getName()).newline().newline();
        VMError.shouldNotReachHere("Recursion in exception handling");
    }

    /**
     * Exception unwinding is interruptible. Otherwise a lot of code would need to be marked as
     * uninterruptible (like all of the code metadata lookup methods), and unwinding also loops over
     * the potentially large number of stack frames. But exceptions can be thrown from code that is
     * truly uninterruptible, because it is impossible to write Java code that is free of implicit
     * exception checks such as null pointer or array bounds checks. In such cases, exceptions are
     * treated as fatal errors.
     */
    @Uninterruptible(reason = "Does not need to be uninterruptible because it throws a fatal error.", calleeMustBe = false)
    private static void reportFatalUnwind(Throwable exception) {
        Log.log().string("Fatal error: exception unwind while thread is not in Java state: ");
        Log.log().exception(exception).newline().newline();
        VMError.shouldNotReachHere("Exception unwind while thread is not in Java state");
    }

    /**
     * This is the final line of defense if an entry point from C to Java does not have a proper
     * catch-all exception handler. For the Java main method and newly started Java threads, the
     * proper exception handling and reporting of "unhandled" user exceptions is at a higher level
     * using a normal Java catch-all exception handler.
     */
    @Uninterruptible(reason = "Does not need to be uninterruptible because it throws a fatal error.", calleeMustBe = false)
    private static void reportUnhandledException(Throwable exception) {
        Log.log().string("Fatal error: unhandled exception in isolate ").hex(CurrentIsolate.getIsolate()).string(": ");
        Log.log().exception(exception).newline().newline();
        VMError.shouldNotReachHere("Unhandled exception");
    }

    /** Hook to allow a {@link Feature} to install custom exception unwind code. */
    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    protected abstract void customUnwindException(Pointer callerSP);

    /**
     * Unwinds the stack to find an exception handler.
     *
     * @param startSP The SP from which to start the stack unwinding.
     * @param fromMethodWithCalleeSavedRegisters Whether the first frame (identified by startSP) has
     *            callee saved registers.
     * @param skipCaller Whether the first (caller) frame should be skipped. If this is true, then
     *            the value of fromMethodWithCalleeSavedRegisters will be ignored.
     */
    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    private static void defaultUnwindException(Pointer startSP, boolean fromMethodWithCalleeSavedRegisters, boolean skipCaller) {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        boolean hasCalleeSavedRegisters = fromMethodWithCalleeSavedRegisters;
        boolean skipFrame = skipCaller;

        /*
         * startSP identifies the caller of the frame that wants to unwind an exception. So we can
         * start looking for the exception handler in that frame, optionally skipping one frame if
         * requested.
         */
        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initialize(walk, thread, startSP);

        while (JavaStackWalker.advance(walk, thread)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Exception unwinding must not encounter unknown frame");

            if (!skipFrame) {
                Pointer sp = frame.getSP();
                if (DeoptimizationSupport.enabled()) {
                    DeoptimizedFrame deoptFrame = Deoptimizer.checkEagerDeoptimized(frame);
                    if (deoptFrame != null) {
                        /* Deoptimization entry points always have an exception handler. */
                        deoptFrame.takeException();
                        jumpToHandler(sp, DeoptimizationSupport.getEagerDeoptStubPointer(), hasCalleeSavedRegisters);
                        UnreachableNode.unreachable();
                        return; /* Unreachable */
                    } else if (Deoptimizer.checkLazyDeoptimized(frame)) {
                        long exceptionOffset = frame.getExceptionOffset();
                        if (exceptionOffset != CodeInfoQueryResult.NO_EXCEPTION_OFFSET) {
                            setLazyDeoptStubShouldReturnToExceptionHandler(true);
                            /*
                             * When handling exceptions, we always jump to the "object return" lazy
                             * deopt stub, because the Exception object is always passed as the
                             * return value.
                             */
                            jumpToHandler(sp, DeoptimizationSupport.getLazyDeoptStubObjectReturnPointer(), hasCalleeSavedRegisters);
                            UnreachableNode.unreachable();
                            return; /* Unreachable */
                        }
                    }
                }

                long exceptionOffset = frame.getExceptionOffset();
                if (exceptionOffset != CodeInfoQueryResult.NO_EXCEPTION_OFFSET) {
                    CodePointer handlerIP = (CodePointer) ((UnsignedWord) frame.getIP()).add(Word.signed(exceptionOffset));
                    jumpToHandler(sp, handlerIP, hasCalleeSavedRegisters);
                    UnreachableNode.unreachable();
                    return; /* Unreachable */
                }
            } else {
                skipFrame = false;
            }

            /* No handler found in this frame, walk to caller frame. */
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point methods must have an exception handler.");
            hasCalleeSavedRegisters = CodeInfoQueryResult.hasCalleeSavedRegisters(frame.getEncodedFrameSize());
        }
    }

    @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
    private static void jumpToHandler(Pointer sp, CodePointer handlerIP, boolean hasCalleeSavedRegisters) {
        verifyTopFrameAnchor(sp);

        Throwable exception = currentException.get();
        currentException.set(null);

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
}
