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

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;

import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Threading.RecurringCallback;
import org.graalvm.nativeimage.Threading.RecurringCallbackAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.thread.Safepoint.Master;
import com.oracle.svm.core.thread.Safepoint.SafepointException;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.UserError;

public class ThreadingSupportImpl implements ThreadingSupport {
    static class Options {
        @Option(help = "Test whether a thread's recurring callback is pending on each transition from native code to Java.") //
        public static final HostedOptionKey<Boolean> CheckRecurringCallbackOnNativeToJavaTransition = new HostedOptionKey<>(false);
    }

    static void initialize() {
        ImageSingletons.add(ThreadingSupport.class, new ThreadingSupportImpl());
    }

    /**
     * Recurring callbacks execute arbitrary code and can throw {@link SafepointException}s. In some
     * code parts (e.g., when executing VM operations), we can't deal with arbitrary code execution
     * and therefore need to pause the execution of recurring callbacks.
     */
    public static class PauseRecurringCallback implements AutoCloseable {
        private boolean closed = false;

        public PauseRecurringCallback(String reason) {
            pauseRecurringCallback(reason);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                resumeRecurringCallback();
            }
        }
    }

    /**
     * Implementation of a per-thread timer that uses safepoint check operations to invoke a
     * callback method in regular (best-effort) time intervals. The timer starts with an initial
     * {@link #INITIAL_CHECKS number of check operations} for the interval and then measures how
     * frequently check operations occur to determine the period of check operations for the
     * requested time intervals. The timer uses an exponentially weighted moving average (EWMA) to
     * adapt to a changing frequency of safepoint checks in the code that the thread executes.
     */
    static class RecurringCallbackTimer {
        private static final RecurringCallbackAccess CALLBACK_ACCESS = new RecurringCallbackAccess() {
            @Override
            public void throwException(Throwable t) {
                throw new SafepointException(t);
            }
        };

        /**
         * Weight of the newest sample in {@link #ewmaChecksPerNano}. Older samples have a total
         * weight of 1 - {@link #EWMA_LAMBDA}.
         */
        private static final double EWMA_LAMBDA = 0.3;
        private static final double TARGET_INTERVAL_FLEXIBILITY = 0.95;
        private static final long UNSIGNED_INT_MAX = 0xFFFFFFFFL;
        private static final int INITIAL_CHECKS = 100;
        private static final long MINIMUM_INTERVAL_NANOS = 1_000;

        private final long targetIntervalNanos;
        private final RecurringCallback callback;

        private long requestedChecks;
        private double ewmaChecksPerNano;
        private long lastCapture;
        private long lastCallbackExecution;

        private volatile boolean isExecuting = false;

        RecurringCallbackTimer(long targetIntervalNanos, RecurringCallback callback) {
            this.targetIntervalNanos = Math.max(MINIMUM_INTERVAL_NANOS, targetIntervalNanos);
            this.callback = callback;

            long now = System.nanoTime();
            this.lastCapture = now;
            this.lastCallbackExecution = now;
            this.requestedChecks = INITIAL_CHECKS;
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

        @Uninterruptible(reason = "Called by uninterruptible code.")
        private void updateStatistics() {
            long now = System.nanoTime();
            long elapsedNanos = now - lastCapture;
            int safepointRequestedValue = getSafepointRequestedValue(CurrentIsolate.getCurrentThread());
            long skippedChecks = safepointRequestedValue & UNSIGNED_INT_MAX;
            if (elapsedNanos > 0 && skippedChecks < requestedChecks) { // basic sanity
                double checksPerNano = (requestedChecks - skippedChecks) / (double) elapsedNanos;
                if (ewmaChecksPerNano == 0) { // initialization
                    ewmaChecksPerNano = checksPerNano;
                } else {
                    ewmaChecksPerNano = EWMA_LAMBDA * checksPerNano + (1 - EWMA_LAMBDA) * ewmaChecksPerNano;
                }
                lastCapture = now;
            }
        }

        @Uninterruptible(reason = "Called by uninterruptible code.")
        private static int getSafepointRequestedValue(IsolateThread thread) {
            /*
             * A concurrent safepoint request could destroy the safepointRequested value. However,
             * the safepoint logic saves the original value in
             * safepointRequestedValueBeforeSafepoint. So, we access that value if necessary to
             * avoid race conditions.
             */
            int rawValue = Safepoint.getSafepointRequested(thread);
            if (rawValue == Safepoint.SafepointRequestValues.ENTER) {
                int valueSavedBeforeSafepoint = Safepoint.getSafepointRequestedValueBeforeSafepoint(thread);
                if (valueSavedBeforeSafepoint != Safepoint.SafepointRequestValues.RESET) {
                    assert Master.singleton().getRequestingThread().isNonNull();
                    return valueSavedBeforeSafepoint;
                }
            }
            return rawValue;
        }

        @Uninterruptible(reason = "Called by uninterruptible code.")
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
                if (System.nanoTime() >= lastCallbackExecution + (long) (targetIntervalNanos * TARGET_INTERVAL_FLEXIBILITY)) {
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

        @Uninterruptible(reason = "Called by uninterruptible code.")
        private void updateSafepointRequested() {
            long nextDeadline = lastCallbackExecution + targetIntervalNanos;
            long remainingNanos = nextDeadline - System.nanoTime();
            if (remainingNanos < 0 && isCallbackDisabled()) {
                /*
                 * If we are already behind the deadline and recurring callbacks are disabled for
                 * some reason, then we can safely assume that there is no need to enter the
                 * safepoint slow path for at least a full target interval (callback execution will
                 * be triggered manually as soon as possible anyways).
                 */
                remainingNanos = targetIntervalNanos;
            } else {
                remainingNanos = (remainingNanos < MINIMUM_INTERVAL_NANOS) ? MINIMUM_INTERVAL_NANOS : remainingNanos;
            }

            double checks = ewmaChecksPerNano * remainingNanos;
            requestedChecks = (checks > UNSIGNED_INT_MAX) ? UNSIGNED_INT_MAX : ((checks < 1L) ? 1L : (long) checks);
            Safepoint.setSafepointRequested((int) requestedChecks);
        }

        @Uninterruptible(reason = "Called by uninterruptible code.")
        private boolean isCallbackDisabled() {
            /*
             * Avoid recursively entering the callback, or executing the callback while currently
             * unwinding the stack, the latter of which is particularly problematic when the
             * callback throws an exception itself.
             */
            return isExecuting || SnippetRuntime.isUnwindingForException() || isRecurringCallbackPaused();
        }

        /**
         * Separate method to invoke {@link #callback} so that {@link #evaluate()} can be strictly
         * {@link Uninterruptible} and allocation-free.
         */
        @Uninterruptible(reason = "Required by caller, but does not apply to callee.", calleeMustBe = false)
        @RestrictHeapAccess(reason = "Callee may allocate", access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true)
        private void invokeCallback() {
            try {
                callback.run(CALLBACK_ACCESS);
            } catch (SafepointException se) {
                throw se;
            } catch (Throwable t) {
                Log.log().string("Exception caught in recurring callback (ignored): ").object(t).newline();
            }
        }
    }

    private static final FastThreadLocalObject<RecurringCallbackTimer> activeTimer = FastThreadLocalFactory.createObject(RecurringCallbackTimer.class);

    private static final FastThreadLocalInt currentPauseDepth = FastThreadLocalFactory.createInt();

    @Override
    public void registerRecurringCallback(long interval, TimeUnit unit, RecurringCallback callback) {
        if (callback != null) {
            UserError.guarantee(MultiThreaded.getValue(), "Recurring callbacks are only supported in multi-threaded mode.");
            long intervalNanos = unit.toNanos(interval);
            if (intervalNanos < 1) {
                throw new IllegalArgumentException("intervalNanos");
            }
            RecurringCallbackTimer timer = new RecurringCallbackTimer(intervalNanos, callback);
            activeTimer.set(timer);
            Safepoint.setSafepointRequested((int) timer.requestedChecks);
        } else {
            activeTimer.set(null);
        }
    }

    /**
     * Updates the statistics that are used to compute how frequently a thread needs to enter the
     * safepoint slowpath and executes the callback if necessary. This also resets the safepoint
     * requested counter so that some time can pass before this thread enters the safepoint slowpath
     * again.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    static void onSafepointCheckSlowpath() {
        RecurringCallbackTimer timer = activeTimer.get();
        if (timer != null) {
            timer.evaluate();
        } else {
            Safepoint.setSafepointRequested(Safepoint.SafepointRequestValues.RESET);
        }
    }

    @Uninterruptible(reason = "Called by uninterruptible code.", mayBeInlined = true)
    static boolean isRecurringCallbackRegistered(IsolateThread thread) {
        return activeTimer.get(thread) != null;
    }

    static boolean needsNativeToJavaSlowpath() {
        return Options.CheckRecurringCallbackOnNativeToJavaTransition.getValue() && activeTimer.get() != null;
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    public static void pauseRecurringCallback(@SuppressWarnings("unused") String reason) {
        if (!MultiThreaded.getValue()) {
            return;
        }

        /*
         * Even if no callback is registered at the moment, we still need to execute the code below
         * because a callback could be registered while recurring callbacks are paused.
         */
        assert currentPauseDepth.get() >= 0;
        currentPauseDepth.set(currentPauseDepth.get() + 1);
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    public static void resumeRecurringCallback() {
        if (!MultiThreaded.getValue()) {
            return;
        }

        assert currentPauseDepth.get() > 0;
        currentPauseDepth.set(currentPauseDepth.get() - 1);
        if (!isRecurringCallbackPaused()) {
            try {
                onSafepointCheckSlowpath();
            } catch (SafepointException e) {
                throwUnchecked(e.inner); // needed: callers cannot declare `throws Throwable`
            }
        }
    }

    /**
     * Returns true if recurring callbacks are paused. Always returns false if
     * {@linkplain SubstrateOptions#MultiThreaded} is disabled as callbacks are not supported in
     * that case.
     */
    public static boolean isRecurringCallbackPaused() {
        if (!MultiThreaded.getValue()) {
            return false;
        }
        return currentPauseDepth.get() != 0;
    }

    @Uninterruptible(reason = "Called by uninterruptible code.")
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T {
        throw (T) exception; // T is inferred as RuntimeException, but doesn't have to be
    }
}

@AutomaticFeature
class ThreadingSupportFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ThreadingSupportImpl.initialize();
    }
}
