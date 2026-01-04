/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.io.Serial;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Threading.RecurringCallback;
import org.graalvm.nativeimage.Threading.RecurringCallbackAccess;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.sampler.JfrRecurringCallbackExecutionSampler;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

/**
 * Recurring callbacks are per-thread timers that invoke a specific callback method at regular
 * (best-effort) time intervals. These callbacks are implemented on top of the safepoint mechanism,
 * see {@link SafepointSlowpath} for more details.
 * <p>
 * Recurring callbacks are executed at the end of the safepoint slowpath, so only carefully designed
 * {@link Uninterruptible} Java code may be executed. Running normal Java code, or even worse JDK
 * code, may result in deadlocks or crashes. Problems typically happen because the recurring
 * callback execution has some unexpected side effect on the execution state of the application or
 * VM. Even allocating Java objects can already be enough to cause problems.
 */
public class RecurringCallbackSupport {
    public static class ConcealedOptions {
        @Option(help = "Support a per-thread timer that is called at a specific interval.") //
        public static final HostedOptionKey<Boolean> SupportRecurringCallback = new HostedOptionKey<>(false);

        @Option(help = "Test whether a thread's recurring callback is pending on each transition from native code to Java.") //
        static final HostedOptionKey<Boolean> CheckRecurringCallbackOnNativeToJavaTransition = new HostedOptionKey<>(false);
    }

    /**
     * The value of this thread-local can change at any safepoint (another thread may for example
     * install a recurring callback for all active threads in a VM operation). We need to take this
     * into account when accessing the value.
     */
    private static final FastThreadLocalObject<RecurringCallbackTimer> timerTL = FastThreadLocalFactory.createObject(RecurringCallbackTimer.class, "RecurringCallbackSupport.timer");
    private static final FastThreadLocalInt suspendedTL = FastThreadLocalFactory.createInt("RecurringCallbackSupport.suspended");

    @Fold
    public static boolean isEnabled() {
        return ConcealedOptions.SupportRecurringCallback.getValue() || JfrRecurringCallbackExecutionSampler.isPresent();
    }

    public static RecurringCallbackTimer createCallbackTimer(long intervalNanos, RecurringCallback callback) {
        assert isEnabled();
        assert callback != null;

        return new RecurringCallbackTimer(intervalNanos, callback);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    public static void installCallback(IsolateThread thread, RecurringCallbackTimer timer, boolean overwriteExisting) {
        if (overwriteExisting) {
            uninstallCallback(thread);
        }
        installCallback(thread, timer);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    public static void installCallback(IsolateThread thread, RecurringCallbackTimer timer) {
        assert isEnabled();
        assert timer != null;
        assert timer.targetIntervalNanos > 0;
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
        assert timerTL.get(thread) == null : "only one callback can be installed at a time";

        timerTL.set(thread, timer);
        SafepointCheckCounter.setVolatile(thread, timer.requestedChecks);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    public static void uninstallCallback(IsolateThread thread) {
        assert isEnabled();
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        timerTL.set(thread, null);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.", callerMustBe = true)
    public static RecurringCallback getCallback(IsolateThread thread) {
        assert isEnabled();
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        RecurringCallbackTimer value = timerTL.get(thread);
        if (value != null) {
            return value.callback;
        }
        return null;
    }

    /**
     * Updates the statistics that are used to compute how frequently this thread needs to enter the
     * safepoint slowpath and executes the callback if necessary. This also resets the
     * {@link SafepointCheckCounter} so that some time can pass before this thread enters the
     * safepoint slowpath again.
     *
     * Note that the callback execution may throw exceptions, so this is NOT necessarily fully
     * uninterruptible.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    static void maybeExecuteCallback() {
        assert VMThreads.StatusSupport.isStatusJava() : "must only be executed when the thread is in Java state";

        RecurringCallbackTimer timer = isEnabled() ? timerTL.get() : null;
        if (timer != null) {
            timer.evaluate();
        } else {
            SafepointCheckCounter.setVolatile(SafepointCheckCounter.MAX_VALUE);
        }
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.", callerMustBe = true)
    static boolean isCallbackInstalled(IsolateThread thread) {
        return isEnabled() && timerTL.get(thread) != null;
    }

    static boolean needsNativeToJavaSlowpath() {
        return isEnabled() && ConcealedOptions.CheckRecurringCallbackOnNativeToJavaTransition.getValue() && timerTL.get() != null && !isCallbackTimerSuspended();
    }

    /**
     * Suspends the execution of recurring callbacks for the current thread. In some code parts
     * (e.g., when executing VM operations), we need to suspend the recurring callback execution
     * temporarily because we can't deal with arbitrary code execution or thrown exceptions.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void suspendCallbackTimer(@SuppressWarnings("unused") String reason) {
        if (!isEnabled()) {
            return;
        }

        /*
         * Even if no callback is installed at the moment, we still need to increment the counter
         * because a callback could be installed at a later point.
         */
        incrementSuspended();
    }

    /**
     * Resumes the execution of recurring callbacks for the current thread. The callback execution
     * might be triggered at the next safepoint check.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void resumeCallbackTimerAtNextSafepointCheck() {
        if (!isEnabled()) {
            return;
        }

        decrementSuspended();
        if (!isCallbackTimerSuspended()) {
            RecurringCallbackTimer timer = timerTL.get();
            if (timer != null) {
                timer.updateStatistics();
                timer.setCounter(1);
            }
        }
    }

    /**
     * Like {@link #resumeCallbackTimerAtNextSafepointCheck()} but with the difference that this
     * method may trigger the execution of the recurring callback right away.
     */
    public static void resumeCallbackTimer() {
        if (!isEnabled()) {
            return;
        }

        decrementSuspended();
        if (!isCallbackTimerSuspended()) {
            resumeCallbackTimer0();
        }
    }

    @Uninterruptible(reason = "Prevent unexpected recurring callback execution (pending exception must not be destroyed).")
    private static void resumeCallbackTimer0() {
        try {
            maybeExecuteCallback();
        } catch (SafepointException e) {
            /* Callers cannot declare `throws Throwable`. */
            throwUnchecked(RecurringCallbackTimer.getAndClearPendingException());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCallbackUnsupportedOrTimerSuspended() {
        return !isEnabled() || isCallbackTimerSuspended();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isCallbackTimerSuspended() {
        assert isEnabled();
        return suspendedTL.get() != 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void incrementSuspended() {
        assert isEnabled();
        assert suspendedTL.get() >= 0;
        suspendedTL.set(suspendedTL.get() + 1);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void decrementSuspended() {
        assert isEnabled();
        int newValue = suspendedTL.get() - 1;
        assert newValue >= 0;
        suspendedTL.set(newValue);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T {
        throw (T) exception; // T is inferred as RuntimeException, but doesn't have to be
    }

    /**
     * The timer starts with an initial {@link #INITIAL_CHECKS number of safepoint check operations}
     * for the interval and then measures how frequently check operations occur to determine the
     * period of check operations for the requested time intervals. The timer uses an exponentially
     * weighted moving average (EWMA) to adapt to a changing frequency of safepoint checks in the
     * code that the thread executes.
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
                maybeExecuteCallback();
            } finally {
                updateCounter();
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
            int rawValue = SafepointCheckCounter.getVolatile(thread);
            return rawValue >= 0 ? rawValue : -rawValue;
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        private void maybeExecuteCallback() {
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
                     *
                     * Recurring callbacks typically execute VM-internal code that performs hardly
                     * any safepoint checks. We explicitly don't update the recurring callback
                     * statistics anywhere in this method as this could skew the numbers.
                     */
                    setCounter(SafepointCheckCounter.MAX_VALUE);
                    try {
                        invokeCallback();
                        /*
                         * The callback is allowed to throw an exception (e.g., to stop or interrupt
                         * long-running code). All code that must run to reinitialize the recurring
                         * callback state must therefore be in a finally-block.
                         */
                    } finally {
                        lastCallbackExecution = System.nanoTime();
                    }
                }
            } finally {
                isExecuting = false;
            }
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        private void updateCounter() {
            long nextDeadline = lastCallbackExecution + targetIntervalNanos;
            long remainingNanos = nextDeadline - System.nanoTime();
            if (remainingNanos < 0 && isCallbackDisabled()) {
                /*
                 * If we are already behind the deadline and recurring callbacks are disabled for
                 * some reason, then we can safely assume that there won't be any need to trigger
                 * recurring callback execution for a long time (reenabling the callbacks triggers
                 * the execution explicitly).
                 */
                setCounter(SafepointCheckCounter.MAX_VALUE);
            } else {
                remainingNanos = UninterruptibleUtils.Math.max(remainingNanos, MINIMUM_INTERVAL_NANOS);
                double checks = ewmaChecksPerNano * remainingNanos;
                setCounter(checks > SafepointCheckCounter.MAX_VALUE ? SafepointCheckCounter.MAX_VALUE : ((checks < 1) ? 1 : (int) checks));
            }
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        void setCounter(int value) {
            requestedChecks = value;
            SafepointCheckCounter.setVolatile(value);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private boolean isCallbackDisabled() {
            return isExecuting || isCallbackTimerSuspended();
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

    static final class SafepointException extends RuntimeException {
        public static final SafepointException SINGLETON = new SafepointException();

        @Serial //
        private static final long serialVersionUID = 1L;

        private SafepointException() {
        }
    }
}
