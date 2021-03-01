/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.sun.management.ThreadMXBean;

final class PolyglotThreadLocalActions {

    private static final ThreadLocalHandshake TL_HANDSHAKE = EngineAccessor.ACCESSOR.runtimeSupport().getThreadLocalHandshake();
    private final PolyglotContextImpl context;
    private final Map<AbstractTLHandshake, Void> activeEvents = new IdentityHashMap<>();
    private final TruffleLogger logger;
    private long idCounter;
    private final boolean traceActions;
    private final List<StatisticsThreadLocalAction> statistics;
    private final Timer intervalTimer;

    PolyglotThreadLocalActions(PolyglotContextImpl context) {
        this.context = context;
        OptionValuesImpl options = this.context.engine.getEngineOptionValues();
        if (options.get(PolyglotEngineOptions.SafepointALot)) {
            statistics = new ArrayList<>();
        } else {
            statistics = null;
        }
        this.traceActions = options.get(PolyglotEngineOptions.TraceThreadLocalActions);
        long interval = options.get(PolyglotEngineOptions.TraceStackTraceInterval);
        if (interval > 0) {
            intervalTimer = new Timer(true);
            setupIntervalTimer(interval);
        } else {
            intervalTimer = null;
        }

        if (statistics != null || traceActions || interval > 0) {
            logger = this.context.engine.getEngineLogger();
        } else {
            logger = null;
        }
    }

    private void setupIntervalTimer(long interval) {
        intervalTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                submit(null, new PrintStackTraceAction(false, false));
            }
        }, interval, interval);
    }

    /**
     * Invoked when a thread is newly entered for a context for the first time.
     */
    void notifyEnterCreatedThread() {
        assert Thread.holdsLock(context);

        /*
         * This potentially initialized fast thread locals. Before that no events must be submitted.
         * Since this hold the context lock it is not possible to do that.
         */
        TL_HANDSHAKE.ensureThreadInitialized();

        if (statistics != null) {
            StatisticsThreadLocalAction collector = new StatisticsThreadLocalAction(this, Thread.currentThread());
            statistics.add(collector);
            submit(new Thread[]{Thread.currentThread()}, collector);
        }
    }

    void notifyContextClosed() {
        assert Thread.holdsLock(context);
        assert !context.isActive() : "context is still active, cannot flush safepoints";
        if (intervalTimer != null) {
            intervalTimer.cancel();
        }
        if (statistics != null) {
            logStatistics();
        }
        for (AbstractTLHandshake handshake : activeEvents.keySet()) {
            Future<?> future = handshake.future;
            if (!future.isDone()) {
                if (context.invalid || context.cancelled) {
                    // we allow cancellation for invalid or cancelled contexts
                    future.cancel(true);
                } else {
                    // otherwise this should not happen as leaving the context before close should
                    // perform all events.
                    throw new AssertionError("Pending thread local actions found. Did the actions not process on last leave? Pending action: " + handshake.action);
                }
            }
        }
        activeEvents.clear();
    }

    private void logStatistics() {
        LongSummaryStatistics all = new LongSummaryStatistics();
        StringBuilder s = new StringBuilder();
        s.append(String.format("Safepoint Statistics %n"));
        s.append(String.format("  -------------------------------------------------------------------------------------- %n"));
        s.append(String.format("   Thread Name         Safepoints | Interval     Avg              Min              Max %n"));
        s.append(String.format("  -------------------------------------------------------------------------------------- %n"));

        for (StatisticsThreadLocalAction statistic : statistics) {
            all.combine(statistic.intervalStatistics);
            formatStatisticLine(s, "  " + statistic.threadName, statistic.intervalStatistics);
        }
        s.append(String.format("  ------------------------------------------------------------------------------------- %n"));
        formatStatisticLine(s, "  All threads", all);
        logger.log(Level.INFO, s.toString());
        statistics.clear();
    }

    private static void formatStatisticLine(StringBuilder s, String label, LongSummaryStatistics statistics) {
        s.append(String.format(" %-20s  %10d | %16.3f us  %12.1f us  %12.1f us%n", label,
                        statistics.getCount() + 1, // intervals + 1 is the number of safepoints
                        statistics.getAverage() / 1000,
                        statistics.getMin() / 1000d,
                        statistics.getMax() / 1000d));
    }

    Future<Void> submit(Thread[] threads, ThreadLocalAction action) {
        Objects.requireNonNull(action);
        if (threads != null) {
            for (int i = 0; i < threads.length; i++) {
                Objects.requireNonNull(threads[i]);
            }
        }
        // lock to stop new threads
        synchronized (context) {
            // send enter/leave to slow-path
            context.setCachedThreadInfo(PolyglotThreadInfo.NULL);

            if (!!context.closed) {
                throw new IllegalStateException("Thread local actions can no longer be submitted for this context as it is already closed.");
            }

            Set<Thread> filterThreads = null;
            if (threads != null) {
                filterThreads = new HashSet<>(Arrays.asList(threads));
            }

            boolean sync = EngineAccessor.LANGUAGE.isSynchronousTLAction(action);
            boolean sideEffect = EngineAccessor.LANGUAGE.isSideEffectingTLAction(action);
            List<Thread> activePolyglotThreads = new ArrayList<>();
            for (PolyglotThreadInfo info : context.getSeenThreads().values()) {
                Thread t = info.getThread();
                if (info.isActiveNotCancelled() && (filterThreads == null || filterThreads.contains(t))) {
                    if (info.isCurrent() && sync && info.isSafepointActive()) {
                        throw new IllegalStateException(
                                        "Recursive synchronous thread local action detected. " +
                                                        "They are disallowed as they may cause deadlocks. " +
                                                        "Schedule an asynchronous thread local action instead.");
                    }
                    activePolyglotThreads.add(t);
                }
            }

            Thread[] activeThreads = activePolyglotThreads.toArray(new Thread[0]);
            AbstractTLHandshake handshake;
            if (sync) {
                handshake = new SyncEvent(context, activeThreads, action);
            } else {
                handshake = new AsyncEvent(context, action);
            }

            if (traceActions) {
                String threadLabel;
                if (threads == null) {
                    threadLabel = "all-threads";
                } else if (threads.length == 1) {
                    threadLabel = "single-thread";
                } else {
                    threadLabel = "multiple-threads-" + threads.length;
                }
                threadLabel += "[alive=" + activePolyglotThreads.size() + "]";
                String sideEffectLabel = sideEffect ? "side-effecting  " : "side-effect-free";
                String syncLabel = sync ? "synchronous " : "asynchronous";
                handshake.debugId = idCounter++;
                log("submit", handshake, String.format("%-25s   %s   %s   action: %s", threadLabel, sideEffectLabel, syncLabel, action.toString()));
            }
            Future<Void> future = handshake.future = TL_HANDSHAKE.runThreadLocal(activeThreads, handshake,
                            AbstractTLHandshake::notifyDone, EngineAccessor.LANGUAGE.isSideEffectingTLAction(action));
            this.activeEvents.put(handshake, null);
            return future;
        }
    }

    private void log(String action, AbstractTLHandshake handshake, String details) {
        if (traceActions) {
            logger.log(Level.INFO, String.format("[tl] %-15s %8d  %-30s   %s", action, handshake.debugId, "thread[" + Thread.currentThread().getName() + "]", details));
        }
    }

    void notifyDone(AbstractTLHandshake handshake) {
        synchronized (context) {
            activeEvents.remove(handshake);
            if (traceActions) {
                if (handshake.future.isCancelled()) {
                    log("cancelled", handshake, "");
                } else {
                    log("done", handshake, "");
                }
            }
        }
    }

    private final class PrintStackTraceAction extends ThreadLocalAction {

        PrintStackTraceAction(boolean hasSideEffects, boolean synchronous) {
            super(hasSideEffects, synchronous);
        }

        @Override
        protected void perform(Access access) {
            logger.log(Level.INFO, String.format("Stack Trace Thread %s: %s",
                            Thread.currentThread().getName(),
                            PolyglotExceptionImpl.printStackToString(context.getHostContext(), access.getLocation())));
        }
    }

    static final class PolyglotTLAccess extends ThreadLocalAction.Access {

        final Thread thread;
        final Node location;
        volatile boolean invalid;
        final boolean wasActive;

        PolyglotTLAccess(Thread thread, Node location, boolean wasActive) {
            super(PolyglotImpl.getInstance());
            this.thread = thread;
            this.location = location;
            this.wasActive = wasActive;
        }

        @Override
        public boolean isContextActive() {
            checkInvalid();
            return wasActive;
        }

        @Override
        public Node getLocation() {
            checkInvalid();
            return location;
        }

        @Override
        public Thread getThread() {
            checkInvalid();
            return Thread.currentThread();
        }

        private void checkInvalid() {
            if (thread != Thread.currentThread()) {
                throw new IllegalStateException("ThreadLocalAccess used on the wrong thread.");
            } else if (invalid) {
                throw new IllegalStateException("ThreadLocalAccess is no longer valid.");
            }
        }
    }

    private abstract static class AbstractTLHandshake implements Consumer<Node> {

        final ThreadLocalAction action;
        long debugId;
        protected final PolyglotContextImpl context;
        Future<Void> future;

        AbstractTLHandshake(PolyglotContextImpl context, ThreadLocalAction action) {
            this.action = action;
            this.context = context;
        }

        public final void accept(Node location) {
            boolean isActive = context.isActive(Thread.currentThread());
            if (!isActive && context.closed) {
                return;
            }
            Object prev = context.engine.enterIfNeeded(context);
            try {
                notifyStart();
                PolyglotTLAccess access = new PolyglotTLAccess(Thread.currentThread(), location, isActive);
                try {
                    acceptImpl(access);
                } finally {
                    access.invalid = true;
                }
                notifySuccess();
            } catch (Throwable t) {
                if (!EngineAccessor.LANGUAGE.isSideEffectingTLAction(action)) {
                    // no truffle exceptions allowed in non side-effecting events.
                    if (InteropLibrary.getUncached().isException(t)) {
                        AssertionError e = new AssertionError("Throwing Truffle exception is disallowed in non-side-effecting thread local actions.", t);
                        notifyFailed(e);
                        throw e;
                    }
                }
                notifyFailed(t);
                throw t;
            } finally {
                context.engine.leaveIfNeeded(prev, context, false);
            }
        }

        private void notifyStart() {
            context.threadLocalActions.log("  perform-start", this, "");
        }

        private void notifySuccess() {
            context.threadLocalActions.log("  perform-done", this, "");
        }

        private void notifyFailed(Throwable t) {
            if (context.threadLocalActions.traceActions) {
                context.threadLocalActions.log("  perform-failed", this, "exception: " + t.toString());
            }
        }

        final void notifyDone() {
            context.threadLocalActions.notifyDone(this);
        }

        protected abstract void acceptImpl(PolyglotTLAccess access);
    }

    @SuppressWarnings("serial")
    static class ExpectedException extends RuntimeException {

        final Throwable inner;

        ExpectedException(Throwable inner) {
            this.inner = inner;
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

    }

    private static final class AsyncEvent extends AbstractTLHandshake {

        AsyncEvent(PolyglotContextImpl context, ThreadLocalAction action) {
            super(context, action);
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            EngineAccessor.LANGUAGE.performTLAction(action, access);
        }

    }

    private static final class SyncEvent extends AbstractTLHandshake {

        private final CountDownLatch doneLatch;
        private final CountDownLatch awaitLatch;

        SyncEvent(PolyglotContextImpl context, Thread[] threads, ThreadLocalAction action) {
            super(context, action);
            this.doneLatch = new CountDownLatch(threads.length);
            this.awaitLatch = new CountDownLatch(threads.length);
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            PolyglotThreadInfo thread;
            synchronized (context) {
                thread = context.getCurrentThreadInfo();
            }
            awaitLatch.countDown();

            try {
                awaitLatch.await();
            } catch (InterruptedException e1) {
            }
            thread.setSafepointActive(true);
            try {
                EngineAccessor.LANGUAGE.performTLAction(action, access);
            } finally {
                thread.setSafepointActive(false);
                doneLatch.countDown();
                while (true) {
                    try {
                        doneLatch.await();
                        break;
                    } catch (InterruptedException e1) {
                    }
                }
            }
        }

    }

    private static final class StatisticsThreadLocalAction extends ThreadLocalAction {

        private static volatile ThreadMXBean threadBean;

        private final PolyglotThreadLocalActions actions;
        private long prevTime = 0;
        private final LongSummaryStatistics intervalStatistics = new LongSummaryStatistics();
        private final String threadName;

        StatisticsThreadLocalAction(PolyglotThreadLocalActions actions, Thread thread) {
            super(false, false);
            this.actions = actions;
            this.threadName = thread.getName();
        }

        @Override
        protected void perform(Access access) {
            long prev = this.prevTime;
            if (prev != 0) {
                long now = System.nanoTime();
                intervalStatistics.accept(now - prev);
            }
            actions.submit(new Thread[]{access.getThread()}, this);
            this.prevTime = System.nanoTime();
        }

        @TruffleBoundary
        static long getCurrentCPUTime() {
            ThreadMXBean bean = threadBean;
            if (bean == null) {
                /*
                 * getThreadMXBean is synchronized so better cache in a local volatile field to
                 * avoid contention.
                 */
                threadBean = bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            }
            return bean.getCurrentThreadCpuTime();
        }

    }

}
