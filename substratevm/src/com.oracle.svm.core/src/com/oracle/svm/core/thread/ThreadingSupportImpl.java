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

import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Threading.RecurringCallback;
import org.graalvm.nativeimage.Threading.RecurringCallbackAccess;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.thread.Safepoint.SafepointException;
import com.oracle.svm.core.thread.Safepoint.SafepointRequestValues;
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

    @Fold
    static ThreadingSupportImpl singleton() {
        return (ThreadingSupportImpl) ImageSingletons.lookup(ThreadingSupport.class);
    }

    public static class PauseRecurringCallback implements AutoCloseable {
        private boolean closed = false;

        public PauseRecurringCallback() {
            pauseRecurringCallback();
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

        private static final int INITIAL_CHECKS = 100;
        private static final long MINIMUM_INTERVAL_NANOS = 1_000;

        private final long targetIntervalNanos;
        private final RecurringCallback callback;

        private long requestedChecks;
        private double ewmaChecksPerNano;
        private long lastCapture;
        private long nextDeadline;

        private volatile boolean isExecuting = false;
        private boolean isPending = false;

        RecurringCallbackTimer(long targetIntervalNanos, RecurringCallback callback) {
            this.targetIntervalNanos = Math.max(MINIMUM_INTERVAL_NANOS, targetIntervalNanos);
            this.callback = callback;

            long now = System.nanoTime();
            this.lastCapture = now;
            this.nextDeadline = now + targetIntervalNanos;
            this.requestedChecks = INITIAL_CHECKS;
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        void evaluate(long timestamp, int value) {
            if (isExecuting || SnippetRuntime.isUnwindingForException()) {
                /*
                 * Avoid recursively entering the callback, or executing the callback while
                 * currently unwinding the stack, the latter of which is particularly problematic
                 * when the callback throws an exception itself.
                 */
                return;
            }
            assert VMThreads.StatusSupport.isStatusJava();
            final long unsignedIntMax = 0xffffffffL;
            isExecuting = true;
            long now = 0;
            try {
                long elapsedNanos = timestamp - lastCapture;
                long skippedChecks = value & unsignedIntMax; // interpret as unsigned
                if (elapsedNanos > 0 && skippedChecks < requestedChecks) { // basic sanity
                    double checksPerNano = (requestedChecks - skippedChecks) / (double) elapsedNanos;
                    if (ewmaChecksPerNano == 0) { // initialization
                        ewmaChecksPerNano = checksPerNano;
                    } else {
                        ewmaChecksPerNano = EWMA_LAMBDA * checksPerNano + (1 - EWMA_LAMBDA) * ewmaChecksPerNano;
                    }
                }
                boolean shouldInvoke = isPending || (timestamp >= nextDeadline);
                if (!shouldInvoke) {
                    now = System.nanoTime();
                    shouldInvoke = (now >= nextDeadline);
                }
                if (shouldInvoke) {
                    try {
                        if (!isRecurringCallbackPaused()) {
                            isPending = false;
                            invokeCallback();
                            /*
                             * The callback is allowed to throw an exception (e.g., to stop or
                             * interrupt long-running code). All code that must run to reinitialize
                             * the recurring callback state must therefore be in a finally-block.
                             */
                        } else {
                            isPending = true;
                        }
                    } finally {
                        now = System.nanoTime();
                        nextDeadline = now + targetIntervalNanos;
                    }
                }
            } finally {
                long remainingNanos = nextDeadline - now;
                remainingNanos = (remainingNanos < MINIMUM_INTERVAL_NANOS) ? MINIMUM_INTERVAL_NANOS : remainingNanos;
                double checks = ewmaChecksPerNano * remainingNanos;
                requestedChecks = (checks > unsignedIntMax) ? unsignedIntMax : ((checks < 1L) ? 1L : (long) checks);
                lastCapture = now;
                Safepoint.setSafepointRequested((int) requestedChecks);
                isExecuting = false;
            }
        }

        /**
         * Separate method to invoke {@link #callback} so that {@link #evaluate(long, int)} can be
         * strictly {@link Uninterruptible} and allocation-free.
         */
        @Uninterruptible(reason = "Required by caller, but does not apply to callee.", calleeMustBe = false)
        @RestrictHeapAccess(reason = "Callee may allocate", access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true)
        private void invokeCallback() {
            try {
                Safepoint.setSafepointRequested(SafepointRequestValues.RESET);
                callback.run(CALLBACK_ACCESS);
            } catch (SafepointException se) {
                throw se;
            } catch (Throwable t) {
                Log.log().string("Exception caught in recurring callback (ignored): ").object(t).newline();
            }
        }

        @Uninterruptible(reason = "Must not contain safepoint checks.")
        void afterResume() {
            assert !isRecurringCallbackPaused();
            if (isPending) {
                long callbackTime = System.nanoTime();
                int callbackValue = Safepoint.getSafepointRequested(CurrentIsolate.getCurrentThread());
                try {
                    evaluate(callbackTime, callbackValue);
                } catch (SafepointException e) {
                    throwUnchecked(e.inner); // needed: callers cannot declare `throws Throwable`
                }
            }
        }

        @Uninterruptible(reason = "Called by uninterruptible code.")
        @SuppressWarnings("unchecked")
        private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T {
            throw (T) exception; // T is inferred as RuntimeException, but doesn't have to be
        }
    }

    private static final FastThreadLocalObject<RecurringCallbackTimer> activeTimer = FastThreadLocalFactory.createObject(RecurringCallbackTimer.class);

    private static final FastThreadLocalInt currentPauseDepth = FastThreadLocalFactory.createInt();

    @Override
    public void registerRecurringCallback(long interval, TimeUnit unit, RecurringCallback callback) {
        if (callback != null) {
            UserError.guarantee(SubstrateOptions.MultiThreaded.getValue(), "Recurring callbacks are only supported in multi-threaded mode.");
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
     * Callback from the safepoint check slow path.
     *
     * @param timestamp Time when the slow path was entered (before the execution of safepoint
     *            operations, if any), as reported by {@link System#nanoTime}.
     * @param value The value of {@code Safepoint.safepointRequested} when the slow path was entered
     *            (before the execution of safepoint operations, if any). Might be incorrect under
     *            rare circumstances, such as races.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks.")
    void onSafepointCheckSlowpath(long timestamp, int value) {
        RecurringCallbackTimer timer = activeTimer.get();
        if (timer != null) {
            timer.evaluate(timestamp, value);
        }
    }

    @Uninterruptible(reason = "Called by uninterruptible code.")
    boolean needsCallbackOnSafepointCheckSlowpath() {
        return activeTimer.get() != null;
    }

    boolean needsNativeToJavaSlowpath() {
        return Options.CheckRecurringCallbackOnNativeToJavaTransition.getValue() && activeTimer.get() != null;
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void pauseRecurringCallback() {
        assert currentPauseDepth.get() >= 0;
        currentPauseDepth.set(currentPauseDepth.get() + 1);
    }

    @Uninterruptible(reason = "Must not contain safepoint checks.")
    private static void resumeRecurringCallback() {
        assert currentPauseDepth.get() > 0;
        currentPauseDepth.set(currentPauseDepth.get() - 1);
        if (!isRecurringCallbackPaused() && activeTimer.get() != null) {
            activeTimer.get().afterResume();
        }
    }

    @Uninterruptible(reason = "Called by uninterruptible code.")
    static boolean isRecurringCallbackPaused() {
        return currentPauseDepth.get() != 0;
    }
}

@AutomaticFeature
class ThreadingSupportFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ThreadingSupportImpl.initialize();
    }
}
