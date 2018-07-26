/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Support for initiating safepoints, which are a global state in which all threads are paused so
 * that an invasive operation (such as garbage collection) can execute without interferences.
 * <p>
 * When a safepoint is requested, one thread (the master) will acquire a mutex for the duration of
 * this safepoint operation. The master will set the thread-local variable
 * {@link #safepointRequested} of each other thread (the slaves) accordingly.
 * <p>
 * The slaves occasionally check their thread-local {@link #safepointRequested} values, and if a
 * safepoint is pending, they call {@link Safepoint#slowPathSafepointCheck()} to block on the mutex
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
 * master drops the mutex, and the slaves that were blocked on the mutex are free continue their
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
 * {@link ThreadingSupportFeature} implements an optional per-thread timer on top of the safepoint
 * mechanism. For that purpose, a safepoint check is actually implemented as a decrement of
 * {@link #safepointRequested} with a zero check that triggers a call to
 * {@link #slowPathSafepointCheck()}. If a timer is registered and the slow path determines that
 * that timer has expired, a timer callback is executed and {@link #safepointRequested} is reset
 * with a value that estimates the number of safepoint checks during the intended timer interval.
 * When an actual safepoint is requested, the master overwrites each slave's
 * {@link #safepointRequested} with {@link SafepointRequestValues#ENTER} so it becomes 0 on the next
 * decrement. When no timer is active on a thread, its {@link #safepointRequested} value is reset to
 * {@link SafepointRequestValues#RESET}. Because {@link #safepointRequested} still eventually
 * decrements to 0, threads can very infrequently call {@link #slowPathSafepointCheck()} without
 * cause.
 *
 * @see SafepointCheckNode
 */
public final class Safepoint {

    private static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_SAFEPOINT_CHECK = SnippetRuntime.findForeignCall(Safepoint.class, "enterSlowPathSafepointCheck", true);
    private static final SubstrateForeignCallDescriptor ENTER_SLOW_PATH_NATIVE_TO_JAVA = SnippetRuntime.findForeignCall(Safepoint.class, "enterSlowPathNativeToJava", true);

    /** All foreign calls defined in this class. */
    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ENTER_SLOW_PATH_NATIVE_TO_JAVA, ENTER_SLOW_PATH_SAFEPOINT_CHECK};

    /** Private constructor: No instances: only statics. */
    private Safepoint() {
    }

    public static class Options {
        @Option(help = "Print a warning if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessWarningNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L));

        @Option(help = "Exit the VM if I can not come to a safepoint in this many nanoseconds. 0 implies forever.")//
        public static final RuntimeOptionKey<Long> SafepointPromptnessFailureNanos = new RuntimeOptionKey<>(TimeUtils.millisToNanos(0L));

        @Option(help = "Are safepoint promptness failures fatal?")//
        public static final RuntimeOptionKey<Boolean> SafepointPromptnessFailureIsFatal = new RuntimeOptionKey<>(true);
    }

    private static long getSafepointPromptnessWarningNanos() {
        return Options.SafepointPromptnessWarningNanos.getValue().longValue();
    }

    private static long getSafepointPromptnessFailureNanos() {
        return Options.SafepointPromptnessFailureNanos.getValue().longValue();
    }

    private static boolean getSafepointPromptnessFailureIsFatal() {
        return Options.SafepointPromptnessFailureIsFatal.getValue();
    }

    /**
     * A statically-allocated exception to be thrown in case I can not come to a safepoint promptly.
     */
    private static final PromptnessException promptnessException = new PromptnessException("Could not come to safepoint promptly.");

    public static class PromptnessException extends RuntimeException {

        protected PromptnessException(String message) {
            super(message);
        }

        /** Every exception needs a serialVersionUID. */
        private static final long serialVersionUID = -1924498867085800218L;

    }

    static class SafepointException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final Throwable inner;

        SafepointException(Throwable inner) {
            this.inner = inner;
        }
    }

    @Uninterruptible(reason = "Called during safepointing.")
    protected static VMMutex getMutex() {
        return VMThreads.THREAD_MUTEX;
    }

    /** Stop at a safepoint. */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void slowPathSafepointCheck() {
        final IsolateThread myself = CEntryPointContext.getCurrentIsolateThread();

        boolean needsCallback = ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath();
        boolean wasFrozen = false;
        long callbackTime = 0;
        int callbackValue = 0;
        do {
            IsolateThread requestingThread = Master.singleton().getRequestingThread();
            if (requestingThread.isNonNull()) {
                if (VMOperationControl.isLockOwner()) {
                    /*
                     * This can happen when a VM operation executes so many safepoint checks that
                     * safepointRequested reaches zero and enters this slow path, so we just reset
                     * the counter and return. The counter is re-initialized after the safepoint is
                     * over and normal execution continues.
                     */
                    setSafepointRequested(myself, SafepointRequestValues.RESET);
                    return;
                }
                VMError.guarantee(requestingThread != myself, "Must be the LockOwner");

                if (needsCallback && !wasFrozen) {
                    callbackTime = System.nanoTime();
                    callbackValue = getSafepointRequestedValueBeforeSafepoint(myself);
                    wasFrozen = true;
                }
                Statistics.incFrozen();
                freezeAtSafepoint();
                Statistics.incThawed();
            }

            /*
             * If we entered this code as slow path for a native-to-Java transition and no safepoint
             * is actually pending, we have to do the transition to Java before continuing. However,
             * the CAS can fail if another thread is currently initiating a safepoint and already
             * brought us into state IN_SAFEPOINT, in which case we have to start over.
             */
        } while (!VMThreads.StatusSupport.isStatusJava() && !VMThreads.StatusSupport.compareAndSetNativeToJava());

        if (needsCallback) {
            if (!wasFrozen) {
                callbackTime = System.nanoTime();
                callbackValue = getSafepointRequested(myself);
                // NOTE: a concurrent safepoint request can have overwritten safepointRequested
            }
            ThreadingSupportImpl.singleton().onSafepointCheckSlowpath(callbackTime, callbackValue);
        }
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.", calleeMustBe = false)
    private static void freezeAtSafepoint() {
        /* Grab the safepoint mutex, which will block me after a transition to native. */
        getMutex().lock();
        /* Then release the mutex when I resume from the safepoint. */
        getMutex().unlock();
    }

    /** Specific values for {@link #safepointRequested}. */
    public interface SafepointRequestValues {
        int RESET = 0xffffffff;
        int ENTER = 1;
    }

    /** Per-thread variable for safepoint requests. */
    private static final FastThreadLocalInt safepointRequested = FastThreadLocalFactory.createInt();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setSafepointRequested(IsolateThread vmThread, int value) {
        safepointRequested.setVolatile(vmThread, value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    protected static int getSafepointRequested(IsolateThread vmThread) {
        return safepointRequested.get(vmThread); // need not be volatile
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void setSafepointRequested(int value) {
        safepointRequested.setVolatile(value);
    }

    private static final FastThreadLocalInt safepointRequestedValueBeforeSafepoint = FastThreadLocalFactory.createInt();

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void setSafepointRequestedValueBeforeSafepoint(IsolateThread vmThread, int value) {
        safepointRequestedValueBeforeSafepoint.setVolatile(vmThread, value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static int getSafepointRequestedValueBeforeSafepoint(IsolateThread vmThread) {
        return safepointRequestedValueBeforeSafepoint.get(vmThread);
    }

    /**
     * Returns the memory location identity accessed by {@link #checkSafepointRequested()}.
     */
    public static LocationIdentity getThreadLocalSafepointRequestedLocationIdentity() {
        return safepointRequested.getLocationIdentity();
    }

    public static long getThreadLocalSafepointRequestedOffset() {
        return VMThreadLocalInfos.getOffset(safepointRequested);
    }

    /**
     * Check if a safepoint has been requested, and block if it is requested.
     *
     * Can only be called from snippets. The fast path is inlined, the slow path is a method call.
     */
    public static void checkSafepointRequested() {
        final boolean needSlowPath = SafepointCheckNode.test();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathSafepointCheck(ENTER_SLOW_PATH_SAFEPOINT_CHECK);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathSafepointCheck(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    /** Foreign call: {@link #ENTER_SLOW_PATH_SAFEPOINT_CHECK}. */
    @SubstrateForeignCallTarget
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathSafepointCheck() throws Throwable {
        if (VMThreads.StatusSupport.isStatusIgnoreSafepoints(CEntryPointContext.getCurrentIsolateThread())) {
            /* Reset counter so that we do not enter the slow path immediately again. */
            Safepoint.setSafepointRequested(Safepoint.SafepointRequestValues.RESET);
            return;
        }
        VMError.guarantee(VMThreads.StatusSupport.isStatusJava(), "Attempting to do a safepoint check when not in Java mode");

        try {
            /*
             * Block on mutex held by thread that requested safepoint, i.e., transition to native
             * code.
             */
            slowPathSafepointCheck();

        } catch (SafepointException se) {
            /* This exception is intended to be thrown from safepoint checks, at one's own risk */
            throw se.inner;

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
     * Transition from native to Java.
     *
     * Can only be called from snippets. The fast path is inlined, the slow path is a method call.
     */
    public static void transitionNativeToJava() {
        // Transition from C to Java, checking for safepoint.
        boolean needSlowPath = ThreadingSupportImpl.singleton().needsNativeToJavaSlowpath() || !VMThreads.StatusSupport.compareAndSetNativeToJava();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathNativeToJava(Safepoint.ENTER_SLOW_PATH_NATIVE_TO_JAVA);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathNativeToJava(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    /**
     * Block until I can transition from native to Java. This is not inlined and need not be fast.
     * In fact, it often blocks. But it can not do much except block, since it starts out running
     * with "native" thread status.
     *
     * Foreign call: {@link #ENTER_SLOW_PATH_NATIVE_TO_JAVA}.
     */
    @SubstrateForeignCallTarget
    @Uninterruptible(reason = "Must not contain safepoint checks")
    private static void enterSlowPathNativeToJava() {
        VMError.guarantee(!VMThreads.StatusSupport.isStatusJava(),
                        "Attempting to do a Native-to-Java transition when already in Java mode");
        VMError.guarantee(!VMThreads.StatusSupport.isStatusIgnoreSafepoints(CEntryPointContext.getCurrentIsolateThread()),
                        "When safepoints are disabled, the thread can only be in Native mode, so the fast path transition must succeed and this slow path must not be called");

        Statistics.incSlowPathFrozen();
        try {
            slowPathSafepointCheck();
        } finally {
            Statistics.incSlowPathThawed();
        }
    }

    /** Methods for the thread that brings the system to a safepoint. */
    public static final class Master {

        static void initialize() {
            ImageSingletons.add(Master.class, new Master());
        }

        @Fold
        public static Master singleton() {
            return ImageSingletons.lookup(Master.class);
        }

        /**
         * For assertion checking that the system is at a safepoint, i.e., only one thread is
         * running and it is executing a {@link VMOperation}.
         *
         * Note that the value is still false while the VM is getting to a safepoint, and while the
         * VM is waking up from a safepoint. So the value false does not imply that other threads
         * are running normally.
         */
        private boolean isFrozen;

        /**
         * The thread requesting a safepoint.
         */
        private volatile IsolateThread requestingThread;

        @Platforms(Platform.HOSTED_ONLY.class)
        private Master() {
            this.isFrozen = false;
        }

        /** Have each of the threads (except myself!) stop at a safepoint. */
        public void freeze(String reason) {
            assert SubstrateOptions.MultiThreaded.getValue() : "Should only freeze for a safepoint when multi-threaded.";
            /*
             * Since I am *sharing* the VMThreads.THREAD_MUTEX between the VMOperation code and the
             * Safepoint code, all I can do here is verify that <em>someone</em> has the mutex held,
             * rather than locking it. I can not even verify who is holding the mutex.
             */
            getMutex().assertIsLocked("Should hold mutex when freezing for a safepoint.");

            requestingThread = CEntryPointContext.getCurrentIsolateThread();

            Statistics.reset();
            Statistics.setStartNanos();
            requestSafepoints(reason);
            waitForSafepoints(reason);
            Statistics.setFrozenNanos();

            isFrozen = true;
        }

        /** Let all of the threads proceed from their safepoint. */
        public void thaw(String reason) {
            assert SubstrateOptions.MultiThreaded.getValue() : "Should only thaw from a safepoint when multi-threaded.";
            isFrozen = false;
            requestingThread = WordFactory.nullPointer();
            releaseSafepoints(reason);
            Statistics.setThawedNanos();
            getMutex().assertIsLocked("Should hold mutex when thawing from a safepoint.");
        }

        private static boolean isMyself(IsolateThread vmThread) {
            return vmThread == CEntryPointContext.getCurrentIsolateThread();
        }

        /** Send each of the threads (except myself) a request to come to a safepoint. */
        private static void requestSafepoints(String reason) {
            final Log trace = Log.noopLog().string("[Safepoint.Master.requestSafepoints:  reason: ").string(reason);
            Safepoint.getMutex().assertIsLocked("Lock should be held by the time I request a safepoint.");

            // Walk the threads list and ask each thread (except myself) to come to a safepoint.
            // TODO: Do I always bring *all* threads to a safepoint? Could I stop some of them?
            for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                if (isMyself(vmThread)) {
                    continue;
                }
                if (VMThreads.StatusSupport.isStatusIgnoreSafepoints(vmThread)) {
                    /*
                     * If the thread is exiting/exited or safepoints are disabled for another
                     * reason, do not ask it to stop at safepoints.
                     */
                    continue;
                }
                // Request that the thread come to a safepoint.
                int saved = getSafepointRequested(vmThread);
                setSafepointRequested(vmThread, SafepointRequestValues.ENTER);
                setSafepointRequestedValueBeforeSafepoint(vmThread, saved);
                Statistics.incRequested();
            }
            trace.string("  returns");
            if (trace.isEnabled() && Statistics.Options.GatherSafepointStatistics.getValue()) {
                trace.string(" with requests: ").signed(Statistics.getRequested());
            }
            trace.string("]").newline();
        }

        /** Wait for there to be no threads (except myself) still waiting to reach a safepoint. */

        private static void waitForSafepoints(String reason) {
            final Log trace = Log.noopLog().string("[Safepoint.Master.waitForSafepoints:  reason: ").string(reason).newline();
            Safepoint.getMutex().assertIsLocked("Should hold mutex while waiting for safepoints.");
            final long startNanos = System.nanoTime();
            long loopNanos = startNanos;

            for (int loopCount = 1; /* return */; loopCount += 1) {
                int atSafepoint = 0;
                int inNative = 0;
                int ignoreSafepoints = 0;
                int notAtSafepoint = 0;
                for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                    if (isMyself(vmThread)) {
                        /* Don't wait for myself. */

                    } else if (VMThreads.StatusSupport.isStatusIgnoreSafepoints(vmThread)) {
                        /*
                         * If the thread has exited or safepoints are disabled for another reason,
                         * then I do not need to worry about bringing it to a safepoint.
                         */
                        ignoreSafepoints += 1;

                    } else if (VMThreads.StatusSupport.isStatusSafepoint(vmThread)) {
                        /*
                         * If the thread is *already* at a safepoint, e.g., from a previous loop,
                         * then I do not have to think about it further.
                         */
                        atSafepoint += 1;

                    } else if (VMThreads.StatusSupport.compareAndSetNativeToSafepoint(vmThread)) {
                        /*
                         * Check if the thread is in native code, and if so atomically change it to
                         * be at a safepoint. The compareAndSet could fail if the thread is still
                         * (or again) in Java code, which is why there is the surrounding
                         * "loopCount" for-loop.
                         */
                        inNative += 1;
                        Statistics.incInstalled();

                    } else {
                        if (getSafepointRequested(vmThread) != SafepointRequestValues.ENTER && !VMThreads.StatusSupport.isStatusIgnoreSafepoints(vmThread)) {
                            /* Re-request the safepoint in case of a lost update of the variable */
                            setSafepointRequested(vmThread, SafepointRequestValues.ENTER);
                            Statistics.incRequested();
                        }

                        notAtSafepoint += 1;
                    }
                }
                if (notAtSafepoint == 0) {
                    trace.string("  returns");
                    if (trace.isEnabled() && Statistics.Options.GatherSafepointStatistics.getValue()) {
                        trace.string(" with installed: ").signed(Statistics.getInstalled());
                    }
                    trace.string("]").newline();
                    return;
                }

                trace.string("  loopCount: ").signed(loopCount)
                                .string("  atSafepoint: ").signed(atSafepoint)
                                .string("  inNative: ").signed(inNative)
                                .string("  ignoreSafepoints: ").signed(ignoreSafepoints)
                                .string("  notAtSafepoint: ").signed(notAtSafepoint)
                                .newline();
                loopNanos = doNotLoopTooLong(loopNanos, startNanos, reason);
                maybeFatallyTooLong(startNanos, reason);

                // Wait impatiently for requested threads to come to a safepoint.
                PauseNode.pause();
            }
        }

        /** Release each thread at a safepoint. */
        private static void releaseSafepoints(String reason) {
            final Log trace = Log.noopLog().string("[Safepoint.Master.releaseSafepoints:").string("  reason: ").string(reason).newline();
            Safepoint.getMutex().assertIsLocked("Should hold mutex when releasing safepoints.");
            // Set all the thread statuses that are at safepoint back to being in native code.
            for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                if (!isMyself(vmThread)) {
                    if (trace.isEnabled()) {
                        trace.string("  vmThread status: ").string(VMThreads.StatusSupport.getStatusString(vmThread));
                    }

                    /*
                     * Release the thread back to native code. Most threads will transition from
                     * safepoint to native; but some threads will already be in native code if they
                     * returned from native code, found the safepoint in progress and blocked on the
                     * mutex putting themselves back in native code again.
                     */
                    VMThreads.StatusSupport.setStatusNative(vmThread);

                    Statistics.incReleased();
                    if (trace.isEnabled()) {
                        trace.string("  ->  ").string(VMThreads.StatusSupport.getStatusString(vmThread)).newline();
                    }
                }
            }
            trace.string("]").newline();
        }

        /** Have I looped for too long? If so, complain, but reset the wait. */
        private static long doNotLoopTooLong(long loopNanos, long startNanos, String reason) {
            long result = loopNanos;
            final long waitedNanos = TimeUtils.nanoSecondsSince(loopNanos);
            final long warningNanos = Safepoint.getSafepointPromptnessWarningNanos();
            if ((0 < warningNanos) && TimeUtils.nanoTimeLessThan(warningNanos, waitedNanos)) {
                final Log warning = Log.log().string("[Safepoint.Master.doNotLoopTooLong:");
                warning.string("  warningNanos: ").signed(warningNanos).string(" < ").string(" waitedNanos: ").signed(waitedNanos);
                warning.string("  startNanos: ").signed(startNanos);
                warning.string("  reason: ").string(reason).string("]").newline();
                result = System.nanoTime();
            }
            return result;
        }

        private static void maybeFatallyTooLong(long startNanos, String reason) {
            final long failureNanos = Safepoint.getSafepointPromptnessFailureNanos();
            if (0 < failureNanos) {
                /* If a promptness limit was set. */
                final long nanosSinceStart = TimeUtils.nanoSecondsSince(startNanos);
                if (TimeUtils.nanoTimeLessThan(failureNanos, nanosSinceStart)) {
                    /* If the promptness limit was exceeded. */
                    final Log warning = Log.log().string("[Safepoint.Master.maybeFatallyTooLong:");
                    warning.string("  failureNanos: ").signed(failureNanos).string(" < nanosSinceStart: ").signed(nanosSinceStart);
                    warning.string("  startNanos: ").signed(startNanos);
                    warning.string("  reason: ").string(reason).string("]").newline();
                    if (Safepoint.getSafepointPromptnessFailureIsFatal()) {
                        VMError.guarantee(false, "Fatal: Safepoint promptness failure.");
                    } else {
                        throw promptnessException;
                    }
                }
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected IsolateThread getRequestingThread() {
            return requestingThread;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isFrozen() {
            return isFrozen;
        }

        /** A sample method to execute in a VMOperation. */
        public static class TestingBackdoor {

            public static int countingVMOperation() {
                final Log trace = Log.log().string("[Safepoint.Master.TestingBackdoor.countingVMOperation:").newline();
                VMOperation.guaranteeInProgress("Should hold mutex while simulating safepoint operation.");
                int atSafepoint = 0;
                int inNative = 0;
                int ignoreSafepoints = 0;
                int notAtSafepoint = 0;
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread != VMThreads.nullThread(); vmThread = VMThreads.nextThread(vmThread)) {
                    // Check if the thread is at a safepoint or in native code.
                    if (VMThreads.StatusSupport.isStatusSafepoint(vmThread)) {
                        atSafepoint += 1;
                    } else if (VMThreads.StatusSupport.isStatusNative(vmThread)) {
                        inNative += 1;
                    } else if (VMThreads.StatusSupport.isStatusIgnoreSafepoints(vmThread)) {
                        ignoreSafepoints += 1;
                    } else {
                        notAtSafepoint += 1;
                    }
                }
                trace.string("  atSafepoint: ").signed(atSafepoint)
                                .string("  inNative: ").signed(inNative)
                                .string("  ignoreSafepoints: ").signed(ignoreSafepoints)
                                .string("  notAtSafepoint: ").signed(notAtSafepoint);
                trace.string("]").newline();
                final int result = atSafepoint + inNative;
                return result;
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

        public static Log toLog(Log log, boolean newLine, String prefix) {
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
            return log;
        }
    }
}

@AutomaticFeature
class SafepointFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        Safepoint.Master.initialize();
    }
}
