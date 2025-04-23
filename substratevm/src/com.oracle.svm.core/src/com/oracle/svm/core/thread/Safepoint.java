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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;
import static com.oracle.svm.core.thread.VMThreads.THREAD_MUTEX;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.SafepointBeginEvent;
import com.oracle.svm.core.jfr.events.SafepointEndEvent;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;

/**
 * Manages the initiation of safepoints. A safepoint is a global state where all Java threads,
 * except one, are paused so that invasive operations (such as a garbage collection) can execute
 * without interferences.
 * <p>
 * When a safepoint is requested, one Java thread (the master) acquires the
 * {@link VMThreads#THREAD_MUTEX}. The master notifies all other threads about the pending safepoint
 * by modifying each thread's {@link SafepointCheckCounter} thread-local.
 * <p>
 * Each Java threads periodically checks the value of its {@link SafepointCheckCounter}. If a
 * safepoint is pending, the thread enters the safepoint slowpath and blocks on the mutex that the
 * master is holding. Blocking on the mutex (or any other native calls) transitions the thread to
 * {@link StatusSupport#STATUS_IN_NATIVE}, see {@link ThreadStatusTransition}.
 * <p>
 * The master iterates over all Java threads. When it sees a thread with
 * {@link StatusSupport#STATUS_IN_NATIVE}, it atomically sets the thread's status to
 * {@link StatusSupport#STATUS_IN_SAFEPOINT}. Java threads that are at a safepoint, will be blocked
 * if they try to return from native code, see {@link ThreadStatusTransition}.
 * <p>
 * After all Java threads reach a safepoint, the master executes pending {@link VMOperation}s that
 * need a safepoint. After executing these operations, the master restores each thread's status to
 * {@link StatusSupport#STATUS_IN_NATIVE} and releases the mutex. Then, the Java threads resume
 * their normal execution.
 * <p>
 * {@link VMThreads#THREAD_MUTEX} is the natural choice because the master must hold that mutex to
 * walk the thread list. Holding that mutex also ensures that no new threads can be attached while a
 * safepoint is pending or in progress.
 */
@AutomaticallyRegisteredImageSingleton
public final class Safepoint {
    public static class Options {
        @Option(help = "Print a warning if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessWarningNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L), RelevantForCompilationIsolates);

        @Option(help = "Exit the VM if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessFailureNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L), RelevantForCompilationIsolates);
    }

    @DuplicatedInNativeCode private static final int NOT_AT_SAFEPOINT = 0;
    @DuplicatedInNativeCode private static final int SYNCHRONIZING = 1;
    @DuplicatedInNativeCode private static final int AT_SAFEPOINT = 2;

    private volatile int safepointState;
    private volatile UnsignedWord safepointId;
    private volatile IsolateThread requestingThread;

    @Platforms(Platform.HOSTED_ONLY.class)
    Safepoint() {
        this.safepointState = NOT_AT_SAFEPOINT;
    }

    @Fold
    public static Safepoint singleton() {
        return ImageSingletons.lookup(Safepoint.class);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isInProgress() {
        return safepointState == AT_SAFEPOINT;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isPendingOrInProgress() {
        /* Only read the state once. */
        int state = safepointState;
        return state == SYNCHRONIZING || state == AT_SAFEPOINT;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getSafepointId() {
        return safepointId;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isMasterThread() {
        return requestingThread == CurrentIsolate.getCurrentThread();
    }

    /**
     * Initiates a safepoint.
     *
     * May only be called by the VM operation thread and must not allocate any Java heap objects.
     * Those invariants and some extra logic in the code for safepoint checks allow us to lock the
     * {@link VMThreads#THREAD_MUTEX} from interruptible code (this would normally result in
     * deadlocks).
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "The safepoint logic must not allocate.")
    boolean startSafepoint(String reason) {
        assert VMOperationControl.mayExecuteVmOperations();
        long startTicks = JfrTicks.elapsedTicks();

        /* The current thread may already own the lock for non-safepoint reasons. */
        boolean lock = !THREAD_MUTEX.isOwner();
        if (lock) {
            THREAD_MUTEX.lock();
        }

        assert requestingThread.isNull();
        requestingThread = CurrentIsolate.getCurrentThread();
        ImageSingletons.lookup(Heap.class).prepareForSafepoint();

        safepointState = SYNCHRONIZING;
        int numJavaThreads = requestThreadsEnterSafepoint(reason);

        safepointState = AT_SAFEPOINT;
        safepointId = safepointId.add(1);
        SafepointBeginEvent.emit(getSafepointId(), numJavaThreads, startTicks);
        return lock;
    }

    /** Let all threads proceed from their safepoint. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "The safepoint logic must not allocate.")
    void endSafepoint(boolean unlock) {
        assert VMOperationControl.mayExecuteVmOperations();
        long startTicks = JfrTicks.elapsedTicks();

        safepointState = NOT_AT_SAFEPOINT;
        releaseThreadsFromSafepoint();
        /* Some Java threads can continue execution now. */

        if (unlock) {
            THREAD_MUTEX.unlock();
        }

        /*
         * Now that the safepoint was released, we can treat this thread like a normal VM thread and
         * don't need the special handling in the safepoint slowpath anymore. Other threads also
         * can't start a new safepoint yet (this code is still executed in the VM operation thread
         * and only the VM operation thread may start a safepoint).
         */
        requestingThread = Word.nullPointer();

        /*
         * Everything down here can take a moment, so we do it after letting all other threads
         * proceed.
         */
        ImageSingletons.lookup(Heap.class).endSafepoint();
        SafepointEndEvent.emit(getSafepointId(), startTicks);
        VMThreads.singleton().cleanupExitedOsThreads();
    }

    /** Blocks until all threads (other than the current thread) have entered the safepoint. */
    private static int requestThreadsEnterSafepoint(String reason) {
        assert THREAD_MUTEX.isOwner() : "must hold mutex while waiting for safepoints";

        long startNanos = System.nanoTime();
        long loopNanos = startNanos;

        long warningNanos = -1;
        long failureNanos = -1;

        for (int loopCount = 1; /* return */ ; loopCount++) {
            int numThreads = 0;
            int atSafepoint = 0;
            int ignoreSafepoints = 0;
            int notAtSafepoint = 0;

            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                numThreads++;
                if (thread == CurrentIsolate.getCurrentThread()) {
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
                int status = StatusSupport.getStatusVolatile(thread);
                int safepointBehavior = SafepointBehavior.getSafepointBehaviorVolatile(thread);
                if (safepointBehavior == SafepointBehavior.PREVENT_VM_FROM_REACHING_SAFEPOINT) {
                    notAtSafepoint++;
                } else if (safepointBehavior == SafepointBehavior.THREAD_CRASHED) {
                    ignoreSafepoints++;
                } else {
                    assert safepointBehavior == SafepointBehavior.ALLOW_SAFEPOINT;
                    switch (status) {
                        case StatusSupport.STATUS_IN_JAVA:
                        case StatusSupport.STATUS_IN_VM: {
                            /* Request the safepoint (or re-request in case of a lost update). */
                            if (SafepointCheckCounter.getVolatile(thread) > 0) {
                                requestEnterSafepoint(thread);
                            }
                            notAtSafepoint++;
                            break;
                        }
                        case StatusSupport.STATUS_IN_SAFEPOINT: {
                            atSafepoint++;
                            break;
                        }
                        case StatusSupport.STATUS_IN_NATIVE: {
                            if (StatusSupport.compareAndSetNativeToSafepoint(thread)) {
                                atSafepoint++;
                            } else {
                                notAtSafepoint++;
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
                /* All relevant threads entered the safepoint. */
                return numThreads;
            }

            if (warningNanos == -1 || failureNanos == -1) {
                /* Cache the option values. */
                warningNanos = Options.SafepointPromptnessWarningNanos.getValue();
                failureNanos = Options.SafepointPromptnessFailureNanos.getValue();
            }

            long nanosSinceStart = TimeUtils.nanoSecondsSince(startNanos);
            if (warningNanos > 0 || failureNanos > 0) {
                long nanosSinceLastWarning = TimeUtils.nanoSecondsSince(loopNanos);

                boolean printWarning = warningNanos > 0 && TimeUtils.nanoTimeLessThan(warningNanos, nanosSinceLastWarning);
                boolean fatalError = failureNanos > 0 && TimeUtils.nanoTimeLessThan(failureNanos, nanosSinceStart);
                if (printWarning || fatalError) {
                    Log.log().string("[Safepoint: not all threads reached a safepoint (").string(reason).string(") within ").signed(warningNanos).string(" ns. ")
                                    .string("Total wait time so far: ").signed(nanosSinceStart).string(" ns.").newline();
                    Log.log().string("  loopCount: ").signed(loopCount)
                                    .string("  atSafepoint: ").signed(atSafepoint)
                                    .string("  ignoreSafepoints: ").signed(ignoreSafepoints)
                                    .string("  notAtSafepoint: ").signed(notAtSafepoint)
                                    .string("]")
                                    .newline();

                    loopNanos = System.nanoTime();
                    if (fatalError) {
                        VMError.guarantee(false, "Safepoint promptness failure.");
                    }
                }
            }

            /* Give the other threads a chance to enter the safepoint. */
            yieldOrSleepOrPause(nanosSinceStart);
        }
    }

    private static void yieldOrSleepOrPause(long nanosSinceStart) {
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

    private static void releaseThreadsFromSafepoint() {
        assert THREAD_MUTEX.isOwner() : "must hold mutex when releasing safepoints.";

        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            if (thread == CurrentIsolate.getCurrentThread() || SafepointBehavior.ignoresSafepoints(thread)) {
                continue;
            }

            assert StatusSupport.getStatusVolatile(thread) == StatusSupport.STATUS_IN_SAFEPOINT;
            restoreCounter(thread);

            /* Skip suspended threads so that they remain in STATUS_IN_SAFEPOINT. */
            if (ThreadSuspendSupport.isSuspended(thread)) {
                continue;
            }

            /*
             * Change the thread status back to native. Most threads will remain blocked until we
             * unlock the mutex. However, some threads may continue executing Java code right away
             * even though the safepoint is still technically in progress. This can for example
             * happen if a thread returns from native to Java code right after we changed its thread
             * status back to native (such threads won't enter the safepoint slowpath).
             */
            StatusSupport.setStatusNative(thread);
        }
    }

    /**
     * Requesting a safepoint does not guarantee that the other thread will honor that request. It
     * can also decide to ignore it (usually due to race conditions that can't be avoided for
     * performance reasons).
     *
     * If recurring callbacks are enabled, a safepoint is requested by doing an atomic arithmetic
     * negation of {@link SafepointCheckCounter}. As a side effect, this also preserves the old
     * value before a safepoint was requested.
     */
    private static void requestEnterSafepoint(IsolateThread thread) {
        if (RecurringCallbackSupport.isEnabled()) {
            int value;
            do {
                value = SafepointCheckCounter.getVolatile(thread);
                assert value >= 0 : "the value can only be negative if a safepoint was requested";
            } while (!SafepointCheckCounter.compareAndSet(thread, value, -value));
        } else {
            SafepointCheckCounter.setVolatile(thread, 0);
        }
    }

    /**
     * Restores {@link SafepointCheckCounter} to the value before the safepoint was requested. For
     * threads that do a native-to-Java transition, it can happen that they freeze at the
     * transition, even though the value in {@link SafepointCheckCounter} was not negated.
     */
    private static void restoreCounter(IsolateThread thread) {
        int value = SafepointCheckCounter.getVolatile(thread);
        if (value < 0) {
            /*
             * After a safepoint was requested, the slave thread typically executes one more
             * decrement operation before it realized that it needs to enter the safepoint slowpath.
             * This only does not happen in rare cases (e.g., when a safepoint is requested when the
             * counter is already 0). The code below assumes that this decrement operation was
             * executed and modifies the value accordingly.
             */
            int newValue = -(value + 2);
            assert newValue >= -2 && newValue < Integer.MAX_VALUE : "overflow";
            newValue = newValue <= 0 ? 1 : newValue;
            SafepointCheckCounter.setVolatile(thread, newValue);
        }
    }
}
