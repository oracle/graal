/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.thread.ThreadingSupportImpl.Options.SupportRecurringCallback;

import java.io.Serial;
import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Threading.RecurringCallback;
import org.graalvm.nativeimage.Threading.RecurringCallbackAccess;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.thread.VMThreads.ActionOnTransitionToJavaSupport;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredImageSingleton(ThreadingSupport.class)
public class ThreadingSupportImpl implements ThreadingSupport {
    public static class Options {
        @Option(help = "Support a per-thread timer that is called at a specific interval.") //
        public static final HostedOptionKey<Boolean> SupportRecurringCallback = new HostedOptionKey<>(true);

        @Option(help = "Test whether a thread's recurring callback is pending on each transition from native code to Java.") //
        public static final HostedOptionKey<Boolean> CheckRecurringCallbackOnNativeToJavaTransition = new HostedOptionKey<>(false);
    }

    /**
     * Implementation of a per-thread timer that uses safepoint check operations to invoke a
     * callback method in regular (best-effort) time intervals. The timer starts with an initial
     * {@link #INITIAL_CHECKS number of check operations} for the interval and then measures how
     * frequently check operations occur to determine the period of check operations for the
     * requested time intervals. The timer uses an exponentially weighted moving average (EWMA) to
     * adapt to a changing frequency of safepoint checks in the code that the thread executes.
     */
    public static class RecurringCallbackTimer {
        private static final FastThreadLocalObject<Throwable> EXCEPTION_TL = FastThreadLocalFactory.createObject(Throwable.class, "RecurringCallbackTimer.exception");
        private static final RecurringCallbackAccess CALLBACK_ACCESS = new RecurringCallbackAccessImpl();

        /**
         * Weight of the newest sample in {@link #ewmaChecksPerNano}. Older samples have a total
         * weight of 1 - {@link #EWMA_LAMBDA}.
         */
        private static final double EWMA_LAMBDA = 0.3;
        private static final double TARGET_INTERVAL_FLEXIBILITY = 0.95;
        private static final int INITIAL_CHECKS = 100;
        private static final long MINIMUM_INTERVAL_NANOS = 1_000;

        private final long targetIntervalNanos;
        private final long flexibleTargetIntervalNanos;
        private final RecurringCallback callback;

        private int requestedChecks;
        private double ewmaChecksPerNano;
        private long lastCapture;
        private long lastCallbackExecution;

        private volatile boolean isExecuting = false;

        RecurringCallbackTimer(long targetIntervalNanos, RecurringCallback callback) {
            this.targetIntervalNanos = Math.max(MINIMUM_INTERVAL_NANOS, targetIntervalNanos);
            this.flexibleTargetIntervalNanos = (long) (targetIntervalNanos * TARGET_INTERVAL_FLEXIBILITY);
            this.callback = callback;

            long now = System.nanoTime();
            this.lastCapture = now;
            this.lastCallbackExecution = now;
            this.requestedChecks = INITIAL_CHECKS;
        }

        @Uninterruptible(reason = "Prevent recurring callback execution.", callerMustBe = true)
        public static Throwable getAndClearPendingException() {
            Throwable t = EXCEPTION_TL.get();
            VMError.guarantee(t != null, "There must be a recurring callback exception pending.");
            EXCEPTION_TL.set(null);
            return t;
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        void evaluate() {
            updateStatistics();
            try {
                executeCallback();
            } finally {
                updateSafepointRequested();
            }
        }

        @Uninterruptible(reason = "Must be uninterruptible to avoid races with the safepoint code.")
        void updateStatistics() {
            long now = System.nanoTime();
            long elapsedNanos = now - lastCapture;

            int skippedChecks = getSkippedChecks(CurrentIsolate.getCurrentThread());
            int executedChecks = requestedChecks - skippedChecks;
            assert executedChecks >= 0;
            if (elapsedNanos > 0 && executedChecks > 0) {
                double checksPerNano = executedChecks / (double) elapsedNanos;
                if (ewmaChecksPerNano == 0) { // initialization
                    ewmaChecksPerNano = checksPerNano;
                } else {
                    ewmaChecksPerNano = EWMA_LAMBDA * checksPerNano + (1 - EWMA_LAMBDA) * ewmaChecksPerNano;
                }
                lastCapture = now;
            }
        }

        @Uninterruptible(reason = "Must be uninterruptible to avoid races with the safepoint code.")
        private static int getSkippedChecks(IsolateThread thread) {
            int rawValue = Safepoint.getSafepointRequested(thread);
            return rawValue >= 0 ? rawValue : -rawValue;
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        private void executeCallback() {
            if (isCallbackDisabled()) {
                return;
            }

            isExecuting = true;
            try {
                /*
                 * Allow the callback to trigger a bit early - otherwise, it can happen that we
                 * enter the slowpath multiple times while closing in on the deadline.
                 */
                if (System.nanoTime() >= lastCallbackExecution + flexibleTargetIntervalNanos) {
                    /*
                     * Before executing the callback, reset the safepoint requested counter as we
                     * don't want to trigger another callback execution in the near future.
                     */
                    setSafepointRequested(Safepoint.THREAD_REQUEST_RESET);
                    try {
                        invokeCallback();
                        /*
                         * The callback is allowed to throw an exception (e.g., to stop or interrupt
                         * long-running code). All code that must run to reinitialize the recurring
                         * callback state must therefore be in a finally-block.
                         */
                    } finally {
                        lastCallbackExecution = System.nanoTime();
                        updateStatistics();
                    }
                }
            } finally {
                isExecuting = false;
            }
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        private void updateSafepointRequested() {
            long nextDeadline = lastCallbackExecution + targetIntervalNanos;
            long remainingNanos = nextDeadline - System.nanoTime();
            if (remainingNanos < 0 && isCallbackDisabled()) {
                /*
                 * If we are already behind the deadline and recurring callbacks are disabled for
                 * some reason, then we can safely assume that there won't be any need to trigger
                 * recurring callback execution for a long time (reenabling the callbacks triggers
                 * the execution explicitly).
                 */
                setSafepointRequested(Safepoint.THREAD_REQUEST_RESET);
            } else {
                remainingNanos = (remainingNanos < MINIMUM_INTERVAL_NANOS) ? MINIMUM_INTERVAL_NANOS : remainingNanos;
                double checks = ewmaChecksPerNano * remainingNanos;
                setSafepointRequested(checks > Safepoint.THREAD_REQUEST_RESET ? Safepoint.THREAD_REQUEST_RESET : ((checks < 1) ? 1 : (int) checks));
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void setSafepointRequested(int value) {
            requestedChecks = value;
            Safepoint.setSafepointRequested(value);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private boolean isCallbackDisabled() {
            return isExecuting || isRecurringCallbackPaused();
        }

        /**
         * Recurring callbacks may be executed in any method that contains a safepoint check. This
         * includes methods that need to be allocation free. Therefore, recurring callbacks must not
         * allocate any Java heap memory.
         */
        @Uninterruptible(reason = "Required by caller, but does not apply to callee.", calleeMustBe = false)
        @RestrictHeapAccess(reason = "Recurring callbacks must not allocate.", access = NO_ALLOCATION)
        private void invokeCallback() {
            try {
                callback.run(CALLBACK_ACCESS);
            } catch (SafepointException e) {
                throw e;
            } catch (Throwable t) {
                /*
                 * Recurring callbacks are specified to ignore exceptions (except if the exception
                 * is thrown via RecurringCallbackAccess.throwException(), which is handled above).
                 * We cannot even log the exception because that could lead to a StackOverflowError
                 * (especially when the recurring callback failed with a StackOverflowError).
                 */
            }
        }

        /**
         * We need to distinguish between arbitrary exceptions (must be swallowed) and exceptions
         * that are thrown via {@link RecurringCallbackAccess#throwException} (must be forwarded to
         * the application). When a recurring callback uses
         * {@link RecurringCallbackAccess#throwException}, we store the exception in a thread local
         * (to avoid allocations) and throw a pre-allocated marker exception instead. We catch the
         * marker exception internally, accesses the thread local, and rethrow that exception.
         */
        private static final class RecurringCallbackAccessImpl implements RecurringCallbackAccess {
            @Override
            public void throwException(Throwable t) {
                EXCEPTION_TL.set(t);
                throw SafepointException.SINGLETON;
            }
        }
    }

    private static final FastThreadLocalObject<RecurringCallbackTimer> activeTimer = FastThreadLocalFactory.createObject(RecurringCallbackTimer.class, "ThreadingSupportImpl.activeTimer");

    private static final FastThreadLocalInt currentPauseDepth = FastThreadLocalFactory.createInt("ThreadingSupportImpl.currentPauseDepth");

    private static final String enableSupportOption = SubstrateOptionsParser.commandArgument(SupportRecurringCallback, "+");

    /**
     * Registers or removes a recurring callback for the current thread. Only one recurring callback
     * can be registered at a time. If there is already another recurring callback registered, it
     * will be overwritten.
     */
    @Override
    public void registerRecurringCallback(long interval, TimeUnit unit, RecurringCallback callback) {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (callback != null) {
            if (!SupportRecurringCallback.getValue()) {
                VMError.shouldNotReachHere("Recurring callbacks must be enabled during image build with option " + enableSupportOption);
            }

            long intervalNanos = unit.toNanos(interval);
            if (intervalNanos < 1) {
                throw new IllegalArgumentException("The intervalNanos field is less than one.");
            }

            RecurringCallbackTimer timer = createRecurringCallbackTimer(intervalNanos, callback);
            registerRecurringCallback0(thread, timer);
        } else {
            removeRecurringCallback(thread);
        }
    }

    public static RecurringCallbackTimer createRecurringCallbackTimer(long intervalNanos, RecurringCallback callback) {
        assert callback != null;
        return new RecurringCallbackTimer(intervalNanos, callback);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    private static void registerRecurringCallback0(IsolateThread thread, RecurringCallbackTimer timer) {
        removeRecurringCallback(thread);
        setRecurringCallback(thread, timer);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    public static void setRecurringCallback(IsolateThread thread, RecurringCallbackTimer timer) {
        assert SupportRecurringCallback.getValue();
        assert timer.targetIntervalNanos > 0;
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        activeTimer.set(thread, timer);
        Safepoint.setSafepointRequested(thread, timer.requestedChecks);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.", callerMustBe = true)
    public static RecurringCallback getRecurringCallback(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        RecurringCallbackTimer value = activeTimer.get(thread);
        if (value != null) {
            return value.callback;
        }
        return null;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void removeRecurringCallback(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        activeTimer.set(thread, null);
    }

    /**
     * Updates the statistics that are used to compute how frequently a thread needs to enter the
     * safepoint slowpath and executes the callback if necessary. This also resets the safepoint
     * requested counter so that some time can pass before this thread enters the safepoint slowpath
     * again.
     *
     * Note that the callback execution can trigger safepoints and throw exceptions, so it is NOT
     * uninterruptible.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    static void onSafepointCheckSlowpath() {
        assert StatusSupport.isStatusJava() : "must only be executed when the thread is in Java state";
        RecurringCallbackTimer timer = isRecurringCallbackSupported() ? activeTimer.get() : null;
        if (timer != null) {
            timer.evaluate();
        } else {
            Safepoint.setSafepointRequested(Safepoint.THREAD_REQUEST_RESET);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isRecurringCallbackRegistered(IsolateThread thread) {
        return isRecurringCallbackSupported() && activeTimer.get(thread) != null;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean needsNativeToJavaSlowpath() {
        return ActionOnTransitionToJavaSupport.isActionPending() ||
                        (isRecurringCallbackSupported() && Options.CheckRecurringCallbackOnNativeToJavaTransition.getValue() &&
                                        activeTimer.get() != null && !isRecurringCallbackPaused());
    }

    /**
     * Recurring callbacks execute arbitrary code and may throw exceptions. In some code parts
     * (e.g., when executing VM operations), we can't deal with arbitrary code execution and
     * therefore need to pause the execution of recurring callbacks.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    public static void pauseRecurringCallback(@SuppressWarnings("unused") String reason) {
        if (!isRecurringCallbackSupported()) {
            return;
        }

        /*
         * Even if no callback is registered at the moment, we still need to execute the code below
         * because a callback could be registered while recurring callbacks are paused.
         */
        assert currentPauseDepth.get() >= 0;
        currentPauseDepth.set(currentPauseDepth.get() + 1);
    }

    /**
     * Resumes the execution of recurring callbacks. The callback execution might be triggered at
     * the next safepoint check.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    public static void resumeRecurringCallbackAtNextSafepoint() {
        if (resumeCallbackExecution()) {
            RecurringCallbackTimer timer = activeTimer.get();
            assert timer != null;
            timer.updateStatistics();
            timer.setSafepointRequested(1);
        }
    }

    /**
     * Like {@link #resumeRecurringCallbackAtNextSafepoint()} but with the difference that this
     * method may trigger the execution of the recurring callback right away.
     */
    public static void resumeRecurringCallback() {
        if (resumeCallbackExecution()) {
            maybeExecuteRecurringCallback();
        }
    }

    @Uninterruptible(reason = "Prevent safepoints.")
    private static void maybeExecuteRecurringCallback() {
        try {
            onSafepointCheckSlowpath();
        } catch (SafepointException e) {
            /* Callers cannot declare `throws Throwable`. */
            throwUnchecked(RecurringCallbackTimer.getAndClearPendingException());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean resumeCallbackExecution() {
        if (!isRecurringCallbackSupported()) {
            return false;
        }

        assert currentPauseDepth.get() > 0;
        currentPauseDepth.set(currentPauseDepth.get() - 1);
        return !isRecurringCallbackPaused() && isRecurringCallbackRegistered(CurrentIsolate.getCurrentThread());
    }

    /**
     * Returns true if recurring callbacks are paused. Always returns false if recurring callbacks
     * are {@linkplain #isRecurringCallbackSupported() not supported}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isRecurringCallbackPaused() {
        if (!isRecurringCallbackSupported()) {
            return false;
        }
        return currentPauseDepth.get() != 0;
    }

    @Fold
    public static boolean isRecurringCallbackSupported() {
        return SupportRecurringCallback.getValue();
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T {
        throw (T) exception; // T is inferred as RuntimeException, but doesn't have to be
    }

    static final class SafepointException extends RuntimeException {
        public static final SafepointException SINGLETON = new SafepointException();

        @Serial //
        private static final long serialVersionUID = 1L;

        private SafepointException() {
        }
    }
}
