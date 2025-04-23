/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.snippets.SafepointSnippets;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.RecurringCallbackSupport.RecurringCallbackTimer;
import com.oracle.svm.core.thread.VMThreads.ActionOnTransitionToJavaSupport;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

/**
 * A safepoint check compares the value of the thread-local {@link SafepointCheckCounter} to 0. If
 * the value is less than or equal to 0, we enter the safepoint
 * {@link #slowPathSafepointCheck(int, boolean, boolean) slowpath}. See {@link SafepointCheckNode}
 * and {@link SafepointSnippets} for more details.
 * <p>
 * {@link RecurringCallbackSupport Recurring callbacks} are implemented on top of the safepoint
 * mechanism. If {@linkplain RecurringCallbackSupport#isEnabled supported}, each safepoint check
 * decrements {@link SafepointCheckCounter} before the comparison. So, threads enter the safepoint
 * slowpath periodically, even if no safepoint is pending. At the end of the slowpath, the recurring
 * callback is executed if the timer has expired.
 */
public class SafepointSlowpath {
    /*
     * For all safepoint-related foreign calls, we must assume that they kill the TLAB locations
     * because those might be modified by a GC. We ignore all other writes as those need to use
     * volatile semantics anyways (to prevent normal race conditions). For performance reasons, we
     * need to assume that recurring callbacks don't do any writes that interfere in a problematic
     * way with the read elimination that is done for the application (otherwise, we would have to
     * kill all memory locations at every safepoint).
     *
     * NOTE: all locations that are killed by safepoint slowpath calls must also be killed by most
     * other foreign calls because the call target may contain a safepoint.
     */
    public static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_SAFEPOINT_CHECK = SnippetRuntime.findForeignCall(SafepointSlowpath.class,
                    "enterSlowPathSafepointCheck", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS = SnippetRuntime.findForeignCall(SafepointSlowpath.class,
                    "enterSlowPathTransitionFromNativeToNewStatus", NO_SIDE_EFFECT);
    static final SubstrateForeignCallDescriptor SLOW_PATH_RUN_PENDING_ACTIONS = SnippetRuntime.findForeignCall(SafepointSlowpath.class,
                    "enterSlowPathRunPendingActions", NO_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    ENTER_SLOW_PATH_SAFEPOINT_CHECK,
                    ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS,
                    SLOW_PATH_RUN_PENDING_ACTIONS,
    };

    /**
     * Runs any {@link ActionOnTransitionToJavaSupport pending transition actions}.
     *
     * This method cannot use the {@link StubCallingConvention} with callee saved registers: the
     * reference map of the C call and this slow-path call must be the same. This is only guaranteed
     * when both the C call and the call to this slow path do not use callee saved registers.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void enterSlowPathRunPendingActions() {
        VMError.guarantee(StatusSupport.isStatusJava(), "must be already back in Java mode");
        assert ActionOnTransitionToJavaSupport.isActionPending() : "must not be called otherwise";
        ActionOnTransitionToJavaSupport.runPendingActions();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathSafepointCheck() throws Throwable {
        slowPathSafepointCheck();
    }

    @AlwaysInline("Always inline into foreign call stub")
    @Uninterruptible(reason = "Must not contain safepoint checks", mayBeInlined = true)
    public static void slowPathSafepointCheck() throws Throwable {
        if (VMThreads.SafepointBehavior.ignoresSafepoints()) {
            /* Safepoints are explicitly disabled for this thread. */
            SafepointCheckCounter.setVolatile(SafepointCheckCounter.MAX_VALUE);
            return;
        }

        VMError.guarantee(StatusSupport.isStatusJava(), "Attempting to do a safepoint check when not in Java mode");
        slowPathSafepointCheck(StatusSupport.STATUS_IN_JAVA, false, false);
    }

    /**
     * This method cannot use the {@link StubCallingConvention} with callee saved registers: the
     * reference map of the C call and this slow-path call must be the same. This is only guaranteed
     * when both the C call and the call to this slow path do not use callee saved registers.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathTransitionFromNativeToNewStatus(int newStatus, boolean popFrameAnchor) throws Throwable {
        VMError.guarantee(StatusSupport.isStatusNativeOrSafepoint(), "Must either be at a safepoint or in native mode");
        VMError.guarantee(!VMThreads.SafepointBehavior.ignoresSafepoints(),
                        "The safepoint handling doesn't change the status of threads that ignore safepoints. So, the fast path transition must succeed and this slow path must not be called");

        slowPathSafepointCheck(newStatus, true, popFrameAnchor);
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathSafepointCheck(int newStatus, boolean callerHasJavaFrameAnchor, boolean popFrameAnchor) throws Throwable {
        try {
            slowPathSafepointCheck0(newStatus, callerHasJavaFrameAnchor, popFrameAnchor);
        } catch (RecurringCallbackSupport.SafepointException e) {
            /* This exception is intended to be thrown from safepoint checks, at one's own risk */
            throw RecurringCallbackTimer.getAndClearPendingException();
        } catch (Throwable ex) {
            /*
             * The foreign call from snippets to this method does not have an exception edge. So we
             * could miss an exception handler if we unwind an exception from this method.
             *
             * Any exception coming out of a safepoint would be surprising to users. There is a good
             * reason why Thread.stop() has been deprecated a long time ago (we do not support it on
             * Substrate VM).
             */
            VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Depending on the situation, this method is called for at least one of the following reasons:
     * <ul>
     * <li>to do a thread state transition from native state to Java or VM state.</li>
     * <li>to suspend the thread at a safepoint and resume execution after the safepoint.</li>
     * <li>to execute the recurring callback periodically.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathSafepointCheck0(int newStatus, boolean callerHasJavaFrameAnchor, boolean popFrameAnchor) {
        if (Safepoint.singleton().isMasterThread()) {
            /* Must not stop at a safepoint nor trigger a callback. */
            assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended();
        } else {
            do {
                if (Safepoint.singleton().isPendingOrInProgress() || ThreadSuspendSupport.isCurrentThreadSuspended()) {
                    freezeAtSafepoint(newStatus, callerHasJavaFrameAnchor);
                    assert StatusSupport.getStatusVolatile() == newStatus : "Transition to the new thread status must have been successful.";
                }

                /* The CAS can fail if another thread initiated a safepoint in the meanwhile. */
            } while (StatusSupport.getStatusVolatile() != newStatus && !StatusSupport.compareAndSetNativeToNewStatus(newStatus));
        }
        assert StatusSupport.getStatusVolatile() == newStatus : "Transition to the new thread status must have been successful.";

        if (popFrameAnchor) {
            /*
             * Pop the frame anchor immediately after changing the status to make stack walks safe
             * again, especially for the recurring callback and any other slow-path actions which
             * might throw an exception.
             */
            assert newStatus == StatusSupport.STATUS_IN_JAVA;
            JavaFrameAnchors.popFrameAnchor();
        }

        if (newStatus == StatusSupport.STATUS_IN_JAVA) {
            ActionOnTransitionToJavaSupport.runPendingActions();
            RecurringCallbackSupport.maybeExecuteCallback();
        }
    }

    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void freezeAtSafepoint(int newStatus, boolean callerHasJavaFrameAnchor) {
        if (StatusSupport.isStatusJava()) {
            /* We were called from a regular safepoint slow path. */
            assert newStatus == StatusSupport.STATUS_IN_JAVA;
            /*
             * Set up JavaFrameAnchor for stack traversal, and transition thread into Native state.
             * If the thread is currently in Safepoint state, we overwrite that with Native too.
             * That is allowed because we are about to grab the mutex, and that operation blocks
             * until the safepoint had ended, i.e., until the thread state would have been set back
             * to Native anyway.
             */
            CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
            /*
             * Grab the safepoint mutex. This is the place where all threads line up until the
             * safepoint is finished. Note that this call must never be inlined because we expect
             * exactly one call between the prologue and epilogue, therefore we call a helper method
             * that is marked as @NeverInline.
             */
            notInlinedLockNoTransition();
            /*
             * Remove the JavaFrameAnchor and transition the thread state back into the Java state.
             * The transition must not fail: because we are holding the safepoint mutex, no
             * safepoint can be active.
             */
            CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
        } else {
            /*
             * We were called from the slow path after the thread is coming back from a C function
             * call. This means we can be in native state, or in safepoint state. We must not push a
             * new JavaFrameAnchor: in the time window between the push and the initialization of
             * the new JavaFrameAnchor, its state is uninitialized. The safepoint thread can access
             * the JavaFrameAnchor at any time to initiate a stack walk, i.e., an uninitialized
             * JavaFrameAnchor leads to crashes.
             *
             * There is still a JavaFrameAnchor pushed from the C function prologue. Stack walks can
             * use that anchor. However, that means that the frame of this method and our caller
             * (the slowpath handler frame) are not visited during stack walks. They must have an
             * empty pointer map, otherwise the GC can miss root pointers.
             */
            VMError.guarantee(callerHasJavaFrameAnchor);

            /*
             * Grab the safepoint mutex before trying to change the thread status. This is necessary
             * to avoid races between the safepoint logic and this code (the safepoint could end any
             * time).
             */
            notInlinedLockNoTransition();

            boolean result = StatusSupport.compareAndSetNativeToNewStatus(newStatus);
            if (!result) {
                throw VMError.shouldNotReachHere("Transition to the new thread status failed.");
            }
        }

        /*
         * Release the mutex. This does not block, so it does not matter that we no longer have a
         * JavaFrameAnchor.
         */
        VMThreads.THREAD_MUTEX.unlock();
    }

    @NeverInline("CFunctionPrologue and CFunctionEpilogue are placed around call to this function")
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void notInlinedLockNoTransition() {
        VMThreads.THREAD_MUTEX.lockNoTransition();
        ThreadSuspendSupport.blockCurrentThreadIfSuspended();
    }
}
