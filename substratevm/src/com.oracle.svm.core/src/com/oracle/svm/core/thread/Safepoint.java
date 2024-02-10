/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.SafepointBeginEvent;
import com.oracle.svm.core.jfr.events.SafepointEndEvent;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.CodeSynchronizationNode;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.VMThreads.ActionOnTransitionToJavaSupport;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.options.Option;

/**
 * Support for initiating safepoints, which are a global state in which all threads are paused so
 * that an invasive operation (such as garbage collection) can execute without interferences.
 * <p>
 * When a safepoint is requested, one thread (the master) will acquire a mutex for the duration of
 * this safepoint operation. The master will set the thread-local variable
 * {@link #safepointRequested} of each other thread (the slaves) accordingly.
 * <p>
 * The slaves occasionally check their thread-local {@link #safepointRequested} values, and if a
 * safepoint is pending, they call {@link Safepoint#slowPathSafepointCheck} to block on the mutex
 * that the master is holding. Blocking on the mutex (or other native calls) will transition the
 * slaves to being in native code, via CFunctionSnippets.prologueSnippet().
 * <p>
 * The master loops waiting for each slave to be in native code. At that point the master will
 * atomically change the thread status of the slave to being at a safepoint. Once a thread is at a
 * safepoint, CFunctionSnippets.epilogueSnippet() will prevent it from returning to Java code.
 * <p>
 * When all slaves are at the safepoint, the master can execute any VMOperations that have
 * accumulated. When the VMOperation queues are empty, the master will change the status of each
 * slave back to being in native code. Once a slave is back in native code, it might return to Java
 * code, though any slaves that noticed the safepoint request will be blocked on the mutex. Then the
 * master drops the mutex, and the slaves that were blocked on the mutex are free to continue their
 * execution.
 * <p>
 * CFunctionSnippets.epilogueSnippet() tries to do an atomic compare-and-set of the thread status
 * from being in native code to being in Java code. In the usual case (the fast path) that will
 * succeed and the thread will be back running Java code. If, however, the thread was
 * <em>already</em> in native code when the safepoint was requested, it will have missed the request
 * and will not be blocked on the mutex. In that case the atomic compare-and-set fails because the
 * thread state is at a safepoint. The failure will send the thread to the slow path which will
 * block on the mutex.
 * <p>
 * {@link VMThreads#THREAD_MUTEX} is used as the mutex on which slaves block to freeze and is a
 * natural choice because the master also has to hold that mutex to walk the thread list. Because
 * the mutex is held from the time the safepoint is initiated until it is complete, new threads can
 * not be created (or attached) during the safepoint.
 * <p>
 * A safepoint check is implemented as a check of {@link #safepointRequested} <= 0, which, if true,
 * triggers a call to {@link #slowPathSafepointCheck}. {@link ThreadingSupportImpl} implements an
 * optional per-thread timer on top of the safepoint mechanism. If that timer feature is
 * {@linkplain ThreadingSupportImpl.Options#SupportRecurringCallback supported in the image}, each
 * safepoint check decrements {@link #safepointRequested} before the comparison, which will cause it
 * to periodically enter the slow path. If a timer is registered and the slow path determines that
 * that timer has expired, the timer callback is executed and {@link #safepointRequested} is reset
 * with a value that estimates the number of safepoint checks during the intended timer interval.
 * When an actual safepoint is requested, the master does an arithmetic negation of each slave's
 * {@link #safepointRequested} value to make it enter the slow path on the next safepoint check.
 * When no timer is active on a thread, its {@link #safepointRequested} value is reset to
 * {@link Safepoint#THREAD_REQUEST_RESET}. Because {@link #safepointRequested} still eventually
 * decrements to 0, threads can very infrequently call {@link #slowPathSafepointCheck} without
 * cause.
 *
 * @see SafepointCheckNode
 */
public final class Safepoint {
    /*
     * For all safepoint-related foreign calls, we must assume that they kill the TLAB locations
     * because those might be modified by a GC or when a recurring callback allocates. We ignore all
     * other writes as those need to use volatile semantics anyways (to prevent normal race
     * conditions). For performance reasons, we need to assume that recurring callbacks don't do any
     * writes that interfere in a problematic way with the read elimination that is done for the
     * application (otherwise, we would have to kill all memory locations at every safepoint).
     *
     * NOTE: all locations that are killed by safepoint slowpath calls must also be killed by most
     * other foreign calls because the call target may contain a safepoint.
     */
    public static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_SAFEPOINT_CHECK = SnippetRuntime.findForeignCall(Safepoint.class, "enterSlowPathSafepointCheck", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS = SnippetRuntime.findForeignCall(Safepoint.class,
                    "enterSlowPathTransitionFromNativeToNewStatus", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_TRANSITION_FROM_VM_TO_JAVA = SnippetRuntime.findForeignCall(Safepoint.class, "enterSlowPathTransitionFromVMToJava",
                    NO_SIDE_EFFECT);

    /** All foreign calls defined in this class. */
    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    ENTER_SLOW_PATH_SAFEPOINT_CHECK,
                    ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS,
                    ENTER_SLOW_PATH_TRANSITION_FROM_VM_TO_JAVA,
    };

    /** Private constructor: No instances: only statics. */
    private Safepoint() {
    }

    public static class Options {
        @Option(help = "Print a warning if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessWarningNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L), RelevantForCompilationIsolates);

        @Option(help = "Exit the VM if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessFailureNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L), RelevantForCompilationIsolates);
    }

    private static long getSafepointPromptnessWarningNanos() {
        return Options.SafepointPromptnessWarningNanos.getValue();
    }

    private static long getSafepointPromptnessFailureNanos() {
        return Options.SafepointPromptnessFailureNanos.getValue();
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathSafepointCheck(int newStatus, boolean callerHasJavaFrameAnchor, boolean popFrameAnchor) throws Throwable {
        try {
            slowPathSafepointCheck0(newStatus, callerHasJavaFrameAnchor, popFrameAnchor);
        } catch (ThreadingSupportImpl.SafepointException e) {
            /* This exception is intended to be thrown from safepoint checks, at one's own risk */
            throw ThreadingSupportImpl.RecurringCallbackTimer.getAndClearPendingException();
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
     **/
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathSafepointCheck0(int newStatus, boolean callerHasJavaFrameAnchor, boolean popFrameAnchor) {
        final IsolateThread myself = CurrentIsolate.getCurrentThread();

        SafepointListenerSupport.singleton().beforeSlowpathSafepointCheck();

        if (Master.singleton().getRequestingThread() == myself) {
            /* the safepoint master thread must not stop at a safepoint nor trigger a callback */
            assert !ThreadingSupportImpl.isRecurringCallbackRegistered(myself) || ThreadingSupportImpl.isRecurringCallbackPaused();
        } else {
            do {
                if (Master.singleton().getRequestingThread().isNonNull() || suspendedTL.getVolatile() > 0) {
                    Statistics.incFrozen();
                    freezeAtSafepoint(newStatus, callerHasJavaFrameAnchor);
                    SafepointListenerSupport.singleton().afterFreezeAtSafepoint();
                    Statistics.incThawed();
                    VMError.guarantee(StatusSupport.getStatusVolatile() == newStatus, "Transition to the new thread status must have been successful.");
                }

                /*
                 * If we entered this code as slow path for a native-to-Java or native-to-VM
                 * transition and no safepoint is actually pending, we have to do the transition
                 * before continuing. However, the CAS can fail if another thread is currently
                 * initiating a safepoint and already brought us into state IN_SAFEPOINT, in which
                 * case we have to start over.
                 */
            } while (StatusSupport.getStatusVolatile() != newStatus && !StatusSupport.compareAndSetNativeToNewStatus(newStatus));
        }
        VMError.guarantee(StatusSupport.getStatusVolatile() == newStatus, "Transition to the new thread status must have been successful.");

        if (popFrameAnchor) {
            /*
             * Pop the frame anchor immediately after changing status to make stack walks safe
             * again, especially for the recurring callback and any other slow-path actions which
             * might throw an exception.
             */
            assert newStatus == StatusSupport.STATUS_IN_JAVA;
            JavaFrameAnchors.popFrameAnchor();
        }

        if (newStatus == StatusSupport.STATUS_IN_JAVA) {
            // Resetting the safepoint counter or executing the recurring callback must only be done
            // if the thread is in Java state.
            slowPathRunJavaStateActions();
        }
    }

    /**
     * Slow path code run after a safepoint check or after transitioning from VM to Java state. It
     * resets the safepoint counter, runs recurring callbacks if necessary, and executes pending
     * {@link ActionOnTransitionToJavaSupport transition actions}.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathRunJavaStateActions() {
        if (ActionOnTransitionToJavaSupport.isActionPending()) {
            if (ActionOnTransitionToJavaSupport.isSynchronizeCode()) {
                CodeSynchronizationNode.synchronizeCode();
            } else {
                assert false : "Unexpected action pending.";
            }
            ActionOnTransitionToJavaSupport.clearActions();
        }

        // Do this last so an exception cannot skip the above
        ThreadingSupportImpl.onSafepointCheckSlowpath();
    }

    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void freezeAtSafepoint(int newStatus, boolean callerHasJavaFrameAnchor) {
        if (StatusSupport.isStatusJava()) {
            // We were called from a regular safepoint slow path
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
             * call. This means we can be in Native state, or in Safepoint state if a safepoint is
             * currently going on. We must not push a new JavaFrameAnchor: in the time window
             * between the push and the initialization of the new JavaFrameAnchor, its state is
             * uninitialized. The safepoint thread can access the JavaFrameAnchor at any time to
             * initiate a stack walk, i.e., an uninitialized JavaFrameAnchor leads to crashes.
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
        while (suspendedTL.get() > 0) {
            COND_SUSPEND.blockNoTransition();
        }
    }

    /**
     * Per-thread counter for safepoint requests. It can have one of the following values:
     * <ul>
     * <li>value > 0: remaining number of safepoint checks before the safepoint slowpath code is
     * executed.</li>
     * <li>value == 0: the safepoint slowpath code will be executed. If the counter is 0, we know
     * that the thread that owns the counter decremented it to 0 in the safepoint fast path (i.e.,
     * there is no other way that the counter can reach 0).</li>
     * <li>value < 0: another thread requested a safepoint by doing an arithmetic negation on the
     * value.</li>
     * </ul>
     */
    static final FastThreadLocalInt safepointRequested = FastThreadLocalFactory.createInt("Safepoint.safepointRequested").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    /** The value to reset a thread's {@link #safepointRequested} value to after a safepoint. */
    static final int THREAD_REQUEST_RESET = Integer.MAX_VALUE;

    /**
     * Use this method with care as it potentially destroys or skews data that is needed for
     * scheduling the execution of recurring callbacks (i.e., the number of executed safepoints in a
     * period of time).
     *
     * This method must only be used in places where no races with other threads can happen (e.g.,
     * before attaching the thread or at a safepoint).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void setSafepointRequested(IsolateThread vmThread, int value) {
        assert CurrentIsolate.getCurrentThread() == vmThread || StatusSupport.isStatusCreated(vmThread) || VMOperationControl.mayExecuteVmOperations();
        assert value > 0;
        safepointRequested.setVolatile(vmThread, value);
    }

    /**
     * Use this method with care as it potentially destroys or skews data that is needed for
     * scheduling the execution of recurring callbacks (i.e., the number of executed safepoints in a
     * period of time).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void setSafepointRequested(int value) {
        assert value >= 0;
        safepointRequested.setVolatile(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int getSafepointRequested(IsolateThread vmThread) {
        return safepointRequested.getVolatile(vmThread);
    }

    /**
     * Returns the memory location identity for {@link #safepointRequested}.
     */
    public static LocationIdentity getThreadLocalSafepointRequestedLocationIdentity() {
        return safepointRequested.getLocationIdentity();
    }

    public static int getThreadLocalSafepointRequestedOffset() {
        return VMThreadLocalInfos.getOffset(safepointRequested);
    }

    /**
     * The possible value is {@code 0} (not suspended), or positive (suspended, possibly blocked on
     * {@link #COND_SUSPEND}). This counter may only be modified while holding the
     * {@link VMThreads#THREAD_MUTEX}.
     */
    static final FastThreadLocalInt suspendedTL = FastThreadLocalFactory.createInt("Safepoint.suspended");

    /** Condition on which to block when suspended. */
    static final VMCondition COND_SUSPEND = new VMCondition(VMThreads.THREAD_MUTEX);

    /** Foreign call: {@link #ENTER_SLOW_PATH_SAFEPOINT_CHECK}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathSafepointCheck() throws Throwable {
        slowPathSafepointCheck();
    }

    @AlwaysInline("Always inline into foreign call stub")
    @Uninterruptible(reason = "Must not contain safepoint checks", mayBeInlined = true)
    public static void slowPathSafepointCheck() throws Throwable {
        if (SafepointBehavior.ignoresSafepoints()) {
            /* Safepoints are explicitly disabled for this thread. */
            Safepoint.setSafepointRequested(THREAD_REQUEST_RESET);
            return;
        }

        VMError.guarantee(StatusSupport.isStatusJava(), "Attempting to do a safepoint check when not in Java mode");
        slowPathSafepointCheck(StatusSupport.STATUS_IN_JAVA, false, false);
    }

    /**
     * Transition from native to Java, checking for safepoint.
     *
     * Can only be called from snippets. The fast path is inlined, the slow path is a method call.
     */
    public static void transitionNativeToJava(boolean popFrameAnchor) {
        /*
         * At this point the JavaFrameAnchor (if any) still needs to remain pushed: a concurrently
         * running safepoint code can start a stack traversal at any time.
         */
        StatusSupport.assertStatusNativeOrSafepoint();
        int newStatus = StatusSupport.STATUS_IN_JAVA;
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY, !ThreadingSupportImpl.needsNativeToJavaSlowpath()) &&
                        BranchProbabilityNode.probability(BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY, StatusSupport.compareAndSetNativeToNewStatus(newStatus))) {
            if (popFrameAnchor) {
                // The thread is now back in the Java state, it is safe to pop the JavaFrameAnchor.
                JavaFrameAnchors.popFrameAnchor();
            }
        } else {
            callSlowPathNativeToNewStatus(Safepoint.ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS, newStatus, popFrameAnchor);
        }

        /*
         * Kill all memory locations to ensure that no floating reads are scheduled before the
         * thread is properly transitioned into the Java state.
         */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.ANY_LOCATION);
    }

    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static boolean tryFastTransitionNativeToVM() {
        StatusSupport.assertStatusNativeOrSafepoint();
        return StatusSupport.compareAndSetNativeToNewStatus(StatusSupport.STATUS_IN_VM);
    }

    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void slowTransitionNativeToVM() {
        StatusSupport.assertStatusNativeOrSafepoint();
        int newStatus = StatusSupport.STATUS_IN_VM;
        boolean needSlowPath = !StatusSupport.compareAndSetNativeToNewStatus(newStatus);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathNativeToNewStatus(Safepoint.ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS, newStatus, false);
        }
    }

    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void transitionVMToJava(boolean popFrameAnchor) {
        // We can directly change the thread status as no other thread will touch the status field
        // as long as we are in VM status.
        StatusSupport.assertStatusVM();
        StatusSupport.setStatusJavaUnguarded();
        if (popFrameAnchor) {
            JavaFrameAnchors.popFrameAnchor();
        }
        boolean needSlowPath = ThreadingSupportImpl.needsNativeToJavaSlowpath();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathSafepointCheck(Safepoint.ENTER_SLOW_PATH_TRANSITION_FROM_VM_TO_JAVA);
        }
    }

    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void transitionJavaToVM() {
        // We can directly change the thread state without a safepoint check as the safepoint
        // mechanism does not touch the thread if the status is VM.
        StatusSupport.assertStatusJava();
        StatusSupport.setStatusVM();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void transitionVMToNative() {
        // We can directly change the thread state without a safepoint check as the safepoint
        // mechanism does not touch the thread if the status is VM.
        StatusSupport.assertStatusVM();
        StatusSupport.setStatusNative();
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathSafepointCheck(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    /**
     * Block until I can transition from native to a new thread status. This is not inlined and need
     * not be fast. In fact, it often blocks. But it can not do much except block, since it starts
     * out running with "native" thread status.
     *
     * Foreign call: {@link #ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS}.
     *
     * This method cannot use the {@link StubCallingConvention} with callee saved registers: the
     * reference map of the C call and this slow-path call must be the same. This is only guaranteed
     * when both the C call and the call to this slow path do not use callee saved registers.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathTransitionFromNativeToNewStatus(int newStatus, boolean popFrameAnchor) throws Throwable {
        VMError.guarantee(StatusSupport.isStatusNativeOrSafepoint(), "Must either be at a safepoint or in native mode");
        VMError.guarantee(!SafepointBehavior.ignoresSafepoints(),
                        "The safepoint handling doesn't change the status of threads that ignore safepoints. So, the fast path transition must succeed and this slow path must not be called");

        Statistics.incSlowPathFrozen();
        try {
            slowPathSafepointCheck(newStatus, true, popFrameAnchor);
        } finally {
            Statistics.incSlowPathThawed();
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathNativeToNewStatus(@ConstantNodeParameter ForeignCallDescriptor descriptor, int newThreadStatus, boolean popFrameAnchor);

    /**
     * Transitions from VM to Java do not need a safepoint check. We only need to make sure that any
     * {@link ActionOnTransitionToJavaSupport pending transition action} is executed.
     *
     * Foreign call: {@link #ENTER_SLOW_PATH_TRANSITION_FROM_VM_TO_JAVA}.
     *
     * This method cannot use the {@link StubCallingConvention} with callee saved registers: the
     * reference map of the C call and this slow-path call must be the same. This is only guaranteed
     * when both the C call and the call to this slow path do not use callee saved registers.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void enterSlowPathTransitionFromVMToJava() {
        VMError.guarantee(StatusSupport.isStatusJava(), "Must be already back in Java mode");

        slowPathRunJavaStateActions();
    }

    /** Methods for the thread that brings the system to a safepoint. */
    @AutomaticallyRegisteredImageSingleton
    public static final class Master {
        @DuplicatedInNativeCode private static final int NOT_AT_SAFEPOINT = 0;
        @DuplicatedInNativeCode private static final int SYNCHRONIZING = 1;
        @DuplicatedInNativeCode private static final int AT_SAFEPOINT = 2;

        @Fold
        public static Master singleton() {
            return ImageSingletons.lookup(Master.class);
        }

        private volatile int safepointState;
        private volatile UnsignedWord safepointId;

        /** The thread requesting a safepoint. */
        private volatile IsolateThread requestingThread;

        @Platforms(Platform.HOSTED_ONLY.class)
        Master() {
            this.safepointState = NOT_AT_SAFEPOINT;
        }

        /**
         * Have each of the threads (except myself!) stop at a safepoint.
         *
         * Locking {@linkplain VMThreads#THREAD_MUTEX} in this method is fine because the method is
         * only executed by the VM operation thread. Therefore, no other thread can initiate a
         * safepoint and Java allocations are disabled as well.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "The safepoint logic must not allocate.")
        boolean freeze(String reason) {
            assert VMOperationControl.mayExecuteVmOperations();
            long startTicks = JfrTicks.elapsedTicks();

            /* the current thread may already own the lock for non-safepoint reasons */
            boolean lock = !VMThreads.THREAD_MUTEX.isOwner();
            if (lock) {
                VMThreads.THREAD_MUTEX.lock();
            }

            requestingThread = CurrentIsolate.getCurrentThread();
            Statistics.reset();
            Statistics.setStartNanos();
            ImageSingletons.lookup(Heap.class).prepareForSafepoint();
            safepointState = SYNCHRONIZING;
            int numJavaThreads = requestSafepoints(reason);
            waitForSafepoints(reason);
            Statistics.setFrozenNanos();
            safepointState = AT_SAFEPOINT;
            safepointId = safepointId.add(1);
            SafepointBeginEvent.emit(getSafepointId(), numJavaThreads, startTicks);
            return lock;
        }

        /** Let all threads proceed from their safepoint. */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "The safepoint logic must not allocate.")
        void thaw(boolean unlock) {
            assert VMOperationControl.mayExecuteVmOperations();
            long startTicks = JfrTicks.elapsedTicks();
            safepointState = NOT_AT_SAFEPOINT;
            releaseSafepoints();
            SafepointEndEvent.emit(getSafepointId(), startTicks);
            ImageSingletons.lookup(Heap.class).endSafepoint();
            Statistics.setThawedNanos();
            requestingThread = WordFactory.nullPointer();

            if (unlock) {
                VMThreads.THREAD_MUTEX.unlock();
            }

            // this can take a moment, so we do it after letting all other threads proceed
            VMThreads.singleton().cleanupExitedOsThreads();
        }

        private static boolean isMyself(IsolateThread thread) {
            return thread == CurrentIsolate.getCurrentThread();
        }

        /** Send each of the threads (except myself) a request to come to a safepoint. */
        private static int requestSafepoints(String reason) {
            assert VMThreads.THREAD_MUTEX.isOwner() : "Must hold mutex while requesting a safepoint.";
            final Log trace = Log.noopLog().string("[Safepoint.Master.requestSafepoints:  reason: ").string(reason);
            int numOfThreads = 0;

            // Walk the threads list and ask each thread (except myself) to come to a safepoint.
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                numOfThreads++;
                if (isMyself(vmThread)) {
                    continue;
                }
                if (SafepointBehavior.ignoresSafepoints(vmThread)) {
                    /* If safepoints are disabled, do not ask it to stop at safepoints. */
                    continue;
                }
                requestSafepoint(vmThread);
            }
            trace.string("  returns");
            if (trace.isEnabled() && Statistics.Options.GatherSafepointStatistics.getValue()) {
                trace.string(" with requests: ").signed(Statistics.getRequested());
            }
            trace.string("]").newline();
            return numOfThreads;
        }

        /**
         * Request a safepoint by doing an atomic arithmetic negation of
         * {@link Safepoint#safepointRequested}. As a side effect, this also preserves the old value
         * before a safepoint was requested. Requesting a safepoint does not guarantee that the
         * other thread will honor that request. It can also decide to ignore it (usually due to
         * race conditions that can't be avoided for performance reasons).
         */
        private static void requestSafepoint(IsolateThread vmThread) {
            if (ThreadingSupportImpl.isRecurringCallbackSupported()) {
                int value;
                do {
                    value = safepointRequested.getVolatile(vmThread);
                    assert value >= 0 : "the value can only be negative if a safepoint was requested";
                } while (!safepointRequested.compareAndSet(vmThread, value, -value));
            } else {
                safepointRequested.setVolatile(vmThread, 0);
            }

            Statistics.incRequested();
        }

        /**
         * Restores the {@link Safepoint#safepointRequested} value to the value before the safepoint
         * was requested. For threads that do a native-to-Java transition, it could happen that they
         * freeze at the transition, even though their {@link Safepoint#safepointRequested} counter
         * was not negated.
         */
        private static void restoreSafepointRequestedValue(IsolateThread vmThread) {
            int value = getSafepointRequested(vmThread);
            if (value < 0) {
                /*
                 * After requesting a safepoint, the slave thread typically executed one more
                 * decrement operation before it realized that a safepoint was requested. This only
                 * does not happen in rare cases (e.g., when a safepoint is requested when the
                 * safepointRequested value is already 0). The code below always assumes that this
                 * decrement operation was executed and modifies the value accordingly.
                 */
                int newValue = -(value + 2);
                assert newValue >= -2 && newValue < Integer.MAX_VALUE : "overflow";
                newValue = newValue <= 0 ? 1 : newValue;
                setSafepointRequested(vmThread, newValue);
            }
        }

        /** Wait for there to be no threads (except myself) still waiting to reach a safepoint. */
        private static void waitForSafepoints(String reason) {
            assert VMThreads.THREAD_MUTEX.isOwner() : "Must hold mutex while waiting for safepoints.";
            final long startNanos = System.nanoTime();
            long loopNanos = startNanos;

            long warningNanos = -1;
            long failureNanos = -1;

            for (int loopCount = 1; /* return */; loopCount += 1) {
                int atSafepoint = 0;
                int ignoreSafepoints = 0;
                int notAtSafepoint = 0;
                int lostUpdates = 0;
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (isMyself(vmThread)) {
                        /* Don't wait for myself. */
                        continue;
                    }

                    /*-
                     * The status must be read before the safepoint behavior to prevent races
                     * like the following:
                     * - thread A reads the safepoint behavior of thread B.
                     * - thread B sets the safepoint behavior to PREVENT_VM_FROM_REACHING_SAFEPOINT.
                     * - thread B sets the status to STATUS_IN_NATIVE.
                     * - thread A reads the status of thread B and the VM reaches a safepoint.
                     */
                    int status = StatusSupport.getStatusVolatile(vmThread);
                    int safepointBehavior = SafepointBehavior.getSafepointBehaviorVolatile(vmThread);
                    if (safepointBehavior == SafepointBehavior.PREVENT_VM_FROM_REACHING_SAFEPOINT) {
                        notAtSafepoint++;
                    } else if (safepointBehavior == SafepointBehavior.THREAD_CRASHED) {
                        ignoreSafepoints++;
                    } else {
                        assert safepointBehavior == SafepointBehavior.ALLOW_SAFEPOINT;
                        switch (status) {
                            case StatusSupport.STATUS_IN_JAVA:
                            case StatusSupport.STATUS_IN_VM: {
                                /* Re-request the safepoint in case of a lost update. */
                                if (getSafepointRequested(vmThread) > 0) {
                                    requestSafepoint(vmThread);
                                    lostUpdates++;
                                }
                                notAtSafepoint += 1;
                                break;
                            }
                            case StatusSupport.STATUS_IN_SAFEPOINT: {
                                atSafepoint += 1;
                                break;
                            }
                            case StatusSupport.STATUS_IN_NATIVE: {
                                /*
                                 * Check if the thread is in native code, and if so atomically
                                 * change it to be at a safepoint. The compareAndSet could fail if
                                 * the thread is still (or again) in Java code, which is why there
                                 * is the surrounding "loopCount" for-loop.
                                 */
                                if (StatusSupport.compareAndSetNativeToSafepoint(vmThread)) {
                                    atSafepoint += 1;
                                    Statistics.incInstalled();
                                } else {
                                    notAtSafepoint += 1;
                                }
                                break;
                            }
                            case StatusSupport.STATUS_CREATED:
                            default: {
                                throw VMError.shouldNotReachHere("Unexpected thread status");
                            }
                        }
                    }
                }
                if (notAtSafepoint == 0) {
                    return;
                }

                if (warningNanos == -1 || failureNanos == -1) {
                    warningNanos = Safepoint.getSafepointPromptnessWarningNanos();
                    failureNanos = Safepoint.getSafepointPromptnessFailureNanos();
                }

                long nanosSinceStart = TimeUtils.nanoSecondsSince(startNanos);
                if (warningNanos > 0 || failureNanos > 0) {
                    long nanosSinceLastWarning = TimeUtils.nanoSecondsSince(loopNanos);

                    boolean printWarning = warningNanos > 0 && TimeUtils.nanoTimeLessThan(warningNanos, nanosSinceLastWarning);
                    boolean fatalError = failureNanos > 0 && TimeUtils.nanoTimeLessThan(failureNanos, nanosSinceStart);
                    if (printWarning || fatalError) {
                        Log.log().string("[Safepoint.Master: not all threads reached a safepoint (").string(reason).string(") within ").signed(warningNanos).string(" ns. Total wait time so far: ")
                                        .signed(nanosSinceStart).string(" ns.").newline();
                        Log.log().string("  loopCount: ").signed(loopCount)
                                        .string("  atSafepoint: ").signed(atSafepoint)
                                        .string("  ignoreSafepoints: ").signed(ignoreSafepoints)
                                        .string("  notAtSafepoint: ").signed(notAtSafepoint)
                                        .string("  lostUpdates: ").signed(lostUpdates)
                                        .string("]")
                                        .newline();

                        loopNanos = System.nanoTime();
                        if (fatalError) {
                            VMError.guarantee(false, "Safepoint promptness failure.");
                        }
                    }
                }

                // Wait for requested threads to come to a safepoint.
                if (VMThreads.singleton().supportsNativeYieldAndSleep()) {
                    if (nanosSinceStart < TimeUtils.nanosPerMilli) {
                        VMThreads.singleton().yield();
                    } else {
                        VMThreads.singleton().nativeSleep(1);
                    }
                } else {
                    PauseNode.pause();
                }
            }
        }

        /** Release each thread at a safepoint. */
        private static void releaseSafepoints() {
            assert VMThreads.THREAD_MUTEX.isOwner() : "Must hold mutex when releasing safepoints.";
            // Set all the thread statuses that are at safepoint back to being in native code.
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (!isMyself(vmThread) && !SafepointBehavior.ignoresSafepoints(vmThread)) {
                    restoreSafepointRequestedValue(vmThread);

                    /* Skip suspended threads so that they remain in STATUS_IN_SAFEPOINT. */
                    if (suspendedTL.get(vmThread) > 0) {
                        continue;
                    }

                    /*
                     * Release the thread back to native code. Most threads will transition from
                     * safepoint to native; but some threads will already be in native code if they
                     * returned from native code, found the safepoint in progress and blocked on the
                     * mutex putting themselves back in native code again.
                     */
                    StatusSupport.setStatusNative(vmThread);
                    Statistics.incReleased();
                }
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        IsolateThread getRequestingThread() {
            return requestingThread;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        boolean isFrozen() {
            return safepointState == AT_SAFEPOINT;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public UnsignedWord getSafepointId() {
            return safepointId;
        }

        public static class TestingBackdoor {
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public static int getCurrentThreadSafepointRequestedCount() {
                return getSafepointRequested(CurrentIsolate.getCurrentThread());
            }
        }
    }

    /**
     * Statistics about the progress of a particular safepoint. For debugging in places where I can
     * not use logging. Methods for variables that are on a path to blocking for a safepoint have to
     * be uninterruptible because they might have method-exit safepoints, which would be bad.
     */
    public static final class Statistics {

        public static class Options {
            @Option(help = "Gather statistics about each safepoint.")//
            public static final HostedOptionKey<Boolean> GatherSafepointStatistics = new HostedOptionKey<>(false);
        }
        // Statistics that are updated by the master can be primitives.

        /** When this safepoint was requested. */
        private static long startNanos;
        /** When this safepoint was established. */
        private static long frozenNanos;
        /** When this safepoint was thawed. */
        private static long thawedNanos;
        /** The number of safepoints that have been requested. */
        private static int requested;
        /** The number of safepoints that have been installed. */
        private static int installed;
        /** The number of safepoints that have been released. */
        private static int released;

        // Statistics that are updated by multiple running threads have to be atomic.

        /** The number of threads that have frozen by blocking on the mutex. */
        private static final UninterruptibleUtils.AtomicInteger frozen = new UninterruptibleUtils.AtomicInteger(0);
        /** The number of threads that have thawed after blocking on the mutex. */
        private static final UninterruptibleUtils.AtomicInteger thawed = new UninterruptibleUtils.AtomicInteger(0);
        /** The number of threads frozen on the slow path. */
        private static final UninterruptibleUtils.AtomicInteger slowPathFrozen = new UninterruptibleUtils.AtomicInteger(0);
        /** The number of threads thawed on the slow path. */
        private static final AtomicInteger slowPathThawed = new AtomicInteger(0);

        private Statistics() {
            // All static: no instances.
        }

        public static void reset() {
            if (Options.GatherSafepointStatistics.getValue()) {
                startNanos = 0L;
                frozenNanos = 0L;
                thawedNanos = 0L;
                requested = 0;
                installed = 0;
                released = 0;
                frozen.set(0);
                thawed.set(0);
                slowPathFrozen.set(0);
                slowPathThawed.set(0);
            }
        }

        public static long getStartNanos() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return startNanos;
        }

        public static void setStartNanos() {
            if (Options.GatherSafepointStatistics.getValue()) {
                startNanos = System.nanoTime();
            }
        }

        public static long getFrozenNanos() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return frozenNanos;
        }

        public static void setFrozenNanos() {
            if (Options.GatherSafepointStatistics.getValue()) {
                frozenNanos = TimeUtils.nanoSecondsSince(getStartNanos());
            }
        }

        public static long getThawedNanos() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return thawedNanos;
        }

        public static void setThawedNanos() {
            if (Options.GatherSafepointStatistics.getValue()) {
                thawedNanos = TimeUtils.nanoSecondsSince(getStartNanos());
            }
        }

        public static int getRequested() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return requested;
        }

        public static void incRequested() {
            if (Options.GatherSafepointStatistics.getValue()) {
                requested += 1;
            }
        }

        public static int getInstalled() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return installed;
        }

        public static void incInstalled() {
            if (Options.GatherSafepointStatistics.getValue()) {
                installed += 1;
            }
        }

        public static int getReleased() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return released;
        }

        public static void incReleased() {
            if (Options.GatherSafepointStatistics.getValue()) {
                released += 1;
            }
        }

        public static int getFrozen() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return frozen.get();
        }

        @Uninterruptible(reason = "Called when safepoints are requested.")
        public static void incFrozen() {
            if (Options.GatherSafepointStatistics.getValue()) {
                frozen.incrementAndGet();
            }
        }

        public static int getThawed() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return thawed.get();
        }

        @Uninterruptible(reason = "Called during safepointing.")
        public static void incThawed() {
            if (Options.GatherSafepointStatistics.getValue()) {
                thawed.incrementAndGet();
            }
        }

        public static int getSlowPathFrozen() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return slowPathFrozen.get();
        }

        @Uninterruptible(reason = "Called when safepoints are requested.")
        public static void incSlowPathFrozen() {
            if (Options.GatherSafepointStatistics.getValue()) {
                slowPathFrozen.incrementAndGet();
            }
        }

        public static int getSlowPathThawed() {
            assert Options.GatherSafepointStatistics.getValue() : "Should have set GatherSafepointStatistics.";
            return slowPathThawed.get();
        }

        @Uninterruptible(reason = "Called when safepoints are requested.")
        public static void incSlowPathThawed() {
            if (Options.GatherSafepointStatistics.getValue()) {
                slowPathThawed.incrementAndGet();
            }
        }

        public static void toLog(Log log, boolean newLine, String prefix) {
            if (log.isEnabled() && Options.GatherSafepointStatistics.getValue()) {
                if (newLine) {
                    log.newline();
                }
                log.string("[Safepoint.Statistics: ").string(prefix).newline();
                log.string("      startNanos: ").signed(getStartNanos()).newline();
                log.string("     frozenNanos: ").signed(getFrozenNanos()).newline();
                log.string("     thawedNanos: ").signed(getThawedNanos()).newline();
                log.string("       requested: ").signed(getRequested()).newline();
                log.string("       installed: ").signed(getInstalled()).newline();
                log.string("        released: ").signed(getReleased()).newline();
                log.string("          frozen: ").signed(getFrozen()).newline();
                log.string("          thawed: ").signed(getThawed()).newline();
                log.string("  slowPathFrozen: ").signed(getSlowPathFrozen()).newline();
                log.string("  slowPathThawed: ").signed(getSlowPathThawed()).string("]").newline();
            }
        }
    }
}
