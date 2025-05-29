/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;

final class PolyglotThreadLocalActions {

    private static final Future<Void> COMPLETED_FUTURE = CompletableFuture.completedFuture(null);

    static final ThreadLocalHandshake TL_HANDSHAKE = EngineAccessor.ACCESSOR.runtimeSupport().getThreadLocalHandshake();
    private final PolyglotContextImpl context;
    private final Map<AbstractTLHandshake, Void> activeEvents = new LinkedHashMap<>();
    private long idCounter;
    @CompilationFinal private boolean traceActions;
    private List<PolyglotStatisticsAction> statistics;  // final after context patching
    private Timer missingPollTimer;  // final after context patching
    private int missingPollMillis;  // final after context patching
    private Timer intervalTimer;  // final after context patching

    PolyglotThreadLocalActions(PolyglotContextImpl context) {
        this.context = context;
        initialize();
    }

    void prepareContextStore() {
        if (missingPollTimer != null) {
            missingPollTimer.cancel();
            missingPollTimer = null;
        }
        if (intervalTimer != null) {
            intervalTimer.cancel();
            intervalTimer = null;
        }
    }

    void onContextPatch() {
        initialize();
    }

    boolean hasActiveEvents() {
        assert Thread.holdsLock(context);
        return !activeEvents.isEmpty();
    }

    private void initialize() {
        OptionValuesImpl options = this.context.engine.getEngineOptionValues();
        boolean safepointALot = options.get(PolyglotEngineOptions.SafepointALot);
        missingPollMillis = options.get(PolyglotEngineOptions.TraceMissingSafepointPollInterval);

        if (safepointALot || missingPollMillis > 0) {
            statistics = new ArrayList<>();
        } else {
            statistics = null;
        }

        if (missingPollMillis > 0) {
            missingPollTimer = new Timer(false);
        } else {
            missingPollTimer = null;
        }

        this.traceActions = options.get(PolyglotEngineOptions.TraceThreadLocalActions);
        long interval = options.get(PolyglotEngineOptions.TraceStackTraceInterval);
        if (interval > 0) {
            intervalTimer = new Timer(true);
            setupIntervalTimer(interval);
        } else {
            intervalTimer = null;
        }
    }

    private void setupIntervalTimer(long interval) {
        intervalTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                submit(null, PolyglotEngineImpl.ENGINE_ID, new PrintStackTraceAction(false, false), true);
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
            PolyglotStatisticsAction collector = new PolyglotStatisticsAction(Thread.currentThread());
            statistics.add(collector);
            submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, collector, false);
        }
    }

    void notifyContextClosed() {
        assert Thread.holdsLock(context);
        assert !context.isActive() || context.state == PolyglotContextImpl.State.CLOSED_CANCELLED ||
                        context.state == PolyglotContextImpl.State.CLOSED_EXITED : "context is still active, cannot flush safepoints";
        if (missingPollTimer != null) {
            missingPollTimer.cancel();
        }
        if (intervalTimer != null) {
            intervalTimer.cancel();
        }

        if (!activeEvents.isEmpty()) {
            /*
             * The set can be modified during the subsequent iteration.
             */
            ArrayList<AbstractTLHandshake> activeEventsList = new ArrayList<>(activeEvents.keySet());
            boolean pendingThreadLocalAction = false;
            for (AbstractTLHandshake handshake : activeEventsList) {
                Future<?> future = handshake.future;
                if (!future.isDone()) {
                    if (context.state == PolyglotContextImpl.State.CLOSED_CANCELLED || context.state == PolyglotContextImpl.State.CLOSED_EXITED) {
                        // we allow cancellation for cancelled or exited contexts
                        future.cancel(true);
                        pendingThreadLocalAction = true;
                    } else {
                        /*
                         * otherwise this should not happen as leaving the context before close
                         * should perform all events.
                         */
                        throw new AssertionError("Pending thread local actions found. Did the actions not process on last leave? Pending action: " + handshake.action);
                    }
                }
            }
            if (!pendingThreadLocalAction) {
                /*
                 * We have to keep pending events as threads can still leave after close is
                 * completed in which case the active events still need to be deactivated otherwise
                 * the waiters can be blocked forever.
                 */
                activeEvents.clear();
            }
        }

        if (statistics != null) {
            logStatistics();
        }
    }

    private void logStatistics() {
        LongSummaryStatistics all = new LongSummaryStatistics();
        LongSummaryStatistics blockedAll = new LongSummaryStatistics();
        StringBuilder s = new StringBuilder();
        s.append(String.format("Safepoint Statistics %n"));
        s.append(String.format("  ------------------------------------------------------------------------------------------------------------------------------------------------------- %n"));
        s.append(String.format("   Thread Name         Safepoints | Interval     Avg              Min              Max      | Blocked Intervals   Avg              Min              Max%n"));
        s.append(String.format("  ------------------------------------------------------------------------------------------------------------------------------------------------------- %n"));

        long totalSafepointCount = 0;
        for (PolyglotStatisticsAction statistic : statistics) {
            totalSafepointCount += statistic.safepointCount;
            all.combine(statistic.intervalStatistics);
            blockedAll.combine(statistic.blockedIntervalStatistics);
            formatStatisticLine(s, "  " + statistic.threadName, statistic.safepointCount, statistic.intervalStatistics, statistic.blockedIntervalStatistics);
        }
        s.append(String.format("  ------------------------------------------------------------------------------------------------------------------------------------------------------- %n"));
        formatStatisticLine(s, "  All threads", totalSafepointCount, all, blockedAll);
        context.engine.getEngineLogger().log(Level.INFO, s.toString());
        statistics.clear();
    }

    private static void formatStatisticLine(StringBuilder s, String label, long safepointCount, LongSummaryStatistics statistics, LongSummaryStatistics blockedStatistics) {
        s.append(String.format(" %-20s  %10d | %16.3f us  %12.1f us  %12.1f us   | %7d %16.3f us  %12.1f us  %12.1f us%n", label,
                        safepointCount,
                        statistics.getAverage() / 1000,
                        statistics.getMin() / 1000d,
                        statistics.getMax() / 1000d,
                        blockedStatistics.getCount(),
                        blockedStatistics.getAverage() / 1000,
                        blockedStatistics.getMin() / 1000d,
                        blockedStatistics.getMax() / 1000d));
    }

    Future<Void> submit(Thread[] threads, String originId, ThreadLocalAction action, boolean needsEnter) {
        boolean sync = EngineAccessor.LANGUAGE.isSynchronousTLAction(action);
        return submit(threads, originId, action, new HandshakeConfig(needsEnter, sync, sync, false));
    }

    Future<Void> submit(Thread[] threads, String originId, ThreadLocalAction action, HandshakeConfig config) {
        return submit(threads, originId, action, config, null);
    }

    Future<Void> submit(Thread[] threads, String originId, ThreadLocalAction action, HandshakeConfig config, RecurringFuture existingFuture) {
        TL_HANDSHAKE.testSupport();
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

            if (context.state.isClosed() && !config.ignoreContextClosed) {
                return COMPLETED_FUTURE;
            }

            boolean recurring = EngineAccessor.LANGUAGE.isRecurringTLAction(action);
            assert existingFuture == null || recurring : "recurring invariant";

            boolean sync = EngineAccessor.LANGUAGE.isSynchronousTLAction(action);
            boolean sideEffect = EngineAccessor.LANGUAGE.isSideEffectingTLAction(action);
            List<Thread> activePolyglotThreads = new ArrayList<>();

            if (threads == null) {
                for (PolyglotThreadInfo info : context.getSeenThreads().values()) {
                    Thread t = info.getThread();
                    if (info.isActive() && (!info.isFinalizationComplete() || config.ignoreContextClosed)) {
                        checkRecursiveSynchronousAction(info, sync);
                        activePolyglotThreads.add(t);
                    }
                }
            } else {
                for (Thread t : threads) {
                    PolyglotThreadInfo info = context.getThreadInfo(t);
                    /*
                     * We need to ignore unknown threads (info is null) because the language might
                     * pass a thread which was disposed concurrently.
                     */
                    if (info != null && info.isActive() && (!info.isFinalizationComplete() || config.ignoreContextClosed)) {
                        checkRecursiveSynchronousAction(info, sync);
                        activePolyglotThreads.add(t);
                    }
                }
            }

            Thread[] activeThreads = activePolyglotThreads.toArray(new Thread[0]);
            AbstractTLHandshake handshake;
            if (sync) {
                assert config.syncStartOfEvent || config.syncEndOfEvent : "No synchronization requested for sync event!";
                handshake = new SyncEvent(context, threads, originId, action, config);
            } else {
                assert !config.syncStartOfEvent : "Start of event sync requested for async event!";
                assert !config.syncEndOfEvent : "End of event sync requested for async event!";
                handshake = new AsyncEvent(context, threads, originId, action, config);
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
                String recurringLabel = recurring ? "recurring" : "one-shot";
                handshake.debugId = idCounter++;
                log("submit", handshake, String.format("%-25s  %s  %s %s", threadLabel, sideEffectLabel, syncLabel, recurringLabel));
            }
            Future<Void> future;
            if (activeThreads.length > 0) {
                int syncActionMaxWait = context.engine.getEngineOptionValues().get(PolyglotEngineOptions.SynchronousThreadLocalActionMaxWait);
                boolean syncActionPrintStackTraces = context.engine.getEngineOptionValues().get(PolyglotEngineOptions.SynchronousThreadLocalActionPrintStackTraces);
                future = TL_HANDSHAKE.runThreadLocal(context, activeThreads, handshake, AbstractTLHandshake::notifyDone, handshake.notifyBlockedConsumer, handshake.notifyUnblockedConsumer,
                                EngineAccessor.LANGUAGE.isSideEffectingTLAction(action), EngineAccessor.LANGUAGE.isRecurringTLAction(action), config.syncStartOfEvent, config.syncEndOfEvent,
                                syncActionMaxWait, syncActionPrintStackTraces, context.engine.getEngineLogger());
                this.activeEvents.put(handshake, null);

            } else {
                future = COMPLETED_FUTURE;
                if (recurring) {
                    /*
                     * make sure recurring events are registered, but don't register multiple times
                     */
                    if (existingFuture == null || existingFuture.currentFuture != COMPLETED_FUTURE) {
                        this.activeEvents.put(handshake, null);
                    }
                }
            }
            handshake.rawFuture = future;
            if (recurring) {
                if (existingFuture != null) {
                    existingFuture.setCurrentFuture(future);
                    future = existingFuture;
                } else {
                    future = new RecurringFuture(future);
                }
            }
            handshake.future = future;
            return future;
        }
    }

    private static void checkRecursiveSynchronousAction(PolyglotThreadInfo info, boolean sync) {
        if (info.isCurrent() && sync && info.isSafepointActive()) {
            throw new IllegalStateException("Recursive synchronous thread local action detected. " +
                            "They are disallowed as they may cause deadlocks. " +
                            "Schedule an asynchronous thread local action instead.");
        }
    }

    private static final class RecurringFuture implements Future<Void> {

        private volatile Future<Void> firstFuture;
        private volatile Future<Void> currentFuture;
        volatile boolean cancelled;

        RecurringFuture(Future<Void> f) {
            Objects.requireNonNull(f);
            this.firstFuture = f;
            this.currentFuture = f;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return currentFuture.cancel(mayInterruptIfRunning);
        }

        public Void get() throws InterruptedException, ExecutionException {
            if (cancelled) {
                return null;
            }
            Future<Void> first = firstFuture;
            if (first == null) {
                return null;
            }
            return first.get();
        }

        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (cancelled) {
                return null;
            }
            Future<Void> first = firstFuture;
            if (first == null) {
                return null;
            }
            return first.get(timeout, unit);
        }

        Future<Void> getCurrentFuture() {
            return currentFuture;
        }

        void setCurrentFuture(Future<Void> currentFuture) {
            assert !(currentFuture instanceof RecurringFuture) : "no recursive recurring futures";
            assert currentFuture != null;
            this.firstFuture = null;
            this.currentFuture = currentFuture;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isDone() {
            if (cancelled) {
                return true;
            }
            Future<Void> first = firstFuture;
            if (first == null) {
                return true;
            }
            return first.isDone();
        }
    }

    private void log(String action, AbstractTLHandshake handshake, String details) {
        if (traceActions) {
            context.engine.getEngineLogger().log(Level.INFO,
                            String.format("[tl] %-18s %8d  %-30s %-10s %-30s %s", action,
                                            handshake.debugId,
                                            "thread[" + Thread.currentThread().getName() + "]",
                                            handshake.originId,
                                            "action[" + handshake.action.toString() + "]", details));
        }
    }

    @SuppressWarnings({"fallthrough"})
    Set<ThreadLocalAction> notifyThreadActivation(PolyglotThreadInfo info, boolean active) {
        assert !active || info.getEnteredCount() == 1 : "must be currently entered successfully";
        assert Thread.holdsLock(context);

        if (activeEvents.isEmpty()) {
            // fast common path
            return Collections.emptySet();
        }

        Set<ThreadLocalAction> updatedActions = new HashSet<>();

        // we cannot process the events while the context lock is held
        // so we need to collect them first.
        TruffleSafepoint s = TruffleSafepoint.getCurrent();
        /*
         * The set can be modified during the subsequent iteration.
         */
        ArrayList<AbstractTLHandshake> activeEventsList = new ArrayList<>(activeEvents.keySet());
        /*
         * Re-submits must be postponed after the main loop so that the order of safepoint
         * handshakes entries respects the order in activeEvents.
         */
        ArrayList<AbstractTLHandshake> eventsToResubmit = new ArrayList<>();
        for (AbstractTLHandshake handshake : activeEventsList) {
            if (!handshake.isEnabledForThread(Thread.currentThread())) {
                continue;
            }
            Future<?> f = handshake.future;
            if (f instanceof RecurringFuture recurringFuture) {
                f = recurringFuture.getCurrentFuture();
                assert f != null : "current future must never be null";
            }
            if (f != handshake.rawFuture) {
                assert f instanceof RecurringFuture;
                /*
                 * The recurring thread local action has already been re-submitted, and so the
                 * handshake is outdated.
                 */
                continue;
            }
            if (active) {
                if (traceActions) {
                    log("activate", handshake, "");
                }
                if (f == COMPLETED_FUTURE) {
                    assert handshake.future instanceof RecurringFuture;
                    eventsToResubmit.add(handshake);
                } else {
                    ThreadLocalHandshake.ActivationResult activationResult = TL_HANDSHAKE.activateThread(s, f);
                    switch (activationResult) {
                        case ACTIVATED:
                        case REACTIVATED:
                            updatedActions.add(handshake.action);
                            break;
                        case TERMINATED:
                            if (handshake.future instanceof RecurringFuture) {
                                eventsToResubmit.add(handshake);
                            }
                            break;
                        case ACTIVE:
                            // already active, nothing to do
                            break;
                        case PROCESSED:
                            // already processed, nothing to do
                            break;
                    }
                }
            } else {
                if (traceActions) {
                    log("deactivate", handshake, "");
                }
                if (f == COMPLETED_FUTURE) {
                    assert handshake.future instanceof RecurringFuture;
                    // nothing to do, wait for reactivation
                } else {
                    if (TL_HANDSHAKE.deactivateThread(s, f)) {
                        updatedActions.add(handshake.action);
                    }
                }
            }
        }
        for (AbstractTLHandshake handshake : eventsToResubmit) {
            assert handshake.future instanceof RecurringFuture;
            Future<?> previousWrappedFuture = ((RecurringFuture) handshake.future).getCurrentFuture();
            Future<Void> newFuture = handshake.resubmitRecurring();
            if (newFuture != null && previousWrappedFuture == COMPLETED_FUTURE) {
                assert newFuture instanceof RecurringFuture;
                Future<?> newWrappedFuture = ((RecurringFuture) newFuture).getCurrentFuture();
                if (newWrappedFuture != COMPLETED_FUTURE) {
                    activeEvents.remove(handshake, null);
                }
            }
        }

        return updatedActions;
    }

    void notifyLastDone(AbstractTLHandshake handshake) {
        assert Thread.holdsLock(context);
        if (activeEvents.remove(handshake, null)) {
            // this might actually be called multiple times due to a race condition.
            // in onDone notification in ThreadLocalHandshake.
            if (traceActions) {
                if (handshake.future.isCancelled()) {
                    log("cancelled", handshake, "");
                } else {
                    log("done", handshake, "");
                }
            }
            // important to remove and resubmit recurring events in the same lock
            // otherwise we might race with entering and leaving the thread.
            handshake.resubmitRecurring();
        }
    }

    private final class PrintStackTraceAction extends ThreadLocalAction {

        PrintStackTraceAction(boolean hasSideEffects, boolean synchronous) {
            super(hasSideEffects, synchronous);
        }

        @Override
        protected void perform(Access access) {
            context.engine.getEngineLogger().log(Level.INFO, String.format("Stack Trace Thread %s: %s",
                            Thread.currentThread().getName(),
                            PolyglotExceptionImpl.printStackToString(context.getHostContext(), access.getLocation())));
        }
    }

    static final class PolyglotTLAccess extends ThreadLocalAction.Access {

        final Thread thread;
        final Node location;
        volatile boolean invalid;

        PolyglotTLAccess(Thread thread, Node location) {
            super(PolyglotImpl.SECRET);
            this.thread = thread;
            this.location = location;
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

    static final class HandshakeConfig {

        final boolean needsEnter;
        final boolean syncStartOfEvent;
        final boolean syncEndOfEvent;
        final boolean ignoreContextClosed;

        HandshakeConfig(boolean needsEnter, boolean syncStartOfEvent, boolean syncEndOfEvent, boolean ignoreContextClosed) {
            this.needsEnter = needsEnter;
            this.syncStartOfEvent = syncStartOfEvent;
            this.syncEndOfEvent = syncEndOfEvent;
            this.ignoreContextClosed = ignoreContextClosed;
        }
    }

    abstract static class AbstractTLHandshake implements Consumer<Node> {

        private final String originId;
        final ThreadLocalAction action;
        long debugId;
        protected final PolyglotContextImpl context;
        final HandshakeConfig config;
        final Thread[] filterThreads;
        Future<Void> future;
        /*
         * The submit method either returns the future for the submitted thread local actions
         * directly or wraps it by RecurringFuture. The return value is then assigned to the field
         * future. However, we also need the wrapped future, and so we assign it to the field
         * rawFuture. Therefore, either future == rawFuture or future is an instance of
         * RecurringFuture. In the latter case, the rawFuture is used to determine whether this
         * handshake is up-to-date (future.getCurrentFuture() == rawFuture) or the recurring thread
         * local action has already been re-submitted and this handshake is outdated.
         */
        Future<Void> rawFuture;
        private final Consumer<Node> notifyBlockedConsumer;
        private final Consumer<Node> notifyUnblockedConsumer;

        AbstractTLHandshake(PolyglotContextImpl context, Thread[] filterThreads, String originId, ThreadLocalAction action, HandshakeConfig config) {
            this.action = action;
            this.originId = originId;
            this.context = context;
            this.config = config;
            this.filterThreads = filterThreads;
            this.notifyBlockedConsumer = node -> notifyBlocked(node, true);
            this.notifyUnblockedConsumer = node -> notifyBlocked(node, false);
        }

        protected final Future<Void> resubmitRecurring() {
            assert Thread.holdsLock(context);
            if (future instanceof RecurringFuture f && f.getCurrentFuture() == rawFuture) {
                /*
                 * The rawFuture check prevents duplicated submissions
                 */
                if (!f.cancelled) {
                    return context.threadLocalActions.submit(filterThreads, originId, action, config, f);
                } else {
                    /*
                     * The caller decides whether to delete the handshake from activeEvents based on
                     * the return value.
                     */
                    return future;
                }
            }
            return null;
        }

        final boolean isEnabledForThread(Thread currentThread) {
            if (filterThreads == null) {
                return true;
            } else {
                for (Thread filterThread : filterThreads) {
                    if (filterThread == currentThread) {
                        return true;
                    }
                }
                return false;
            }
        }

        final void notifyDone() {
            synchronized (context) {
                this.context.threadLocalActions.notifyLastDone(this);
            }
        }

        final void notifyBlocked(Node location, boolean blocked) {
            Object prev = null;
            if (config.needsEnter) {
                prev = context.engine.enterIfNeeded(context, false);
            }
            try {
                PolyglotTLAccess access = new PolyglotTLAccess(Thread.currentThread(), location);
                try {
                    EngineAccessor.LANGUAGE.notifyTLActionBlocked(action, access, blocked);
                } catch (Throwable t) {
                    if (!PolyglotContextImpl.isInternalError(t)) {
                        throw new AssertionError("Running Truffle guest code is disallowed in setBlocked thread local action notifications.", t);
                    }
                    throw t;
                } finally {
                    access.invalid = true;
                }
            } finally {
                if (config.needsEnter) {
                    context.engine.leaveIfNeeded(prev, context);
                }
            }
        }

        public final void accept(Node location) {
            Object prev = null;
            if (config.needsEnter) {
                prev = context.engine.enterIfNeeded(context, false);
            }
            try {
                notifyStart();
                PolyglotTLAccess access = new PolyglotTLAccess(Thread.currentThread(), location);
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
                if (config.needsEnter) {
                    context.engine.leaveIfNeeded(prev, context);
                }
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
                context.threadLocalActions.log("  perform-failed", this, " exception: " + t.toString());
            }
        }

        protected abstract void acceptImpl(PolyglotTLAccess access);

        @Override
        public String toString() {
            return action.toString();
        }
    }

    private static final class AsyncEvent extends AbstractTLHandshake {

        AsyncEvent(PolyglotContextImpl context, Thread[] filerThreads, String originId, ThreadLocalAction action, HandshakeConfig config) {
            super(context, filerThreads, originId, action, config);
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            EngineAccessor.LANGUAGE.performTLAction(action, access);
        }

    }

    private static final class SyncEvent extends AbstractTLHandshake {

        SyncEvent(PolyglotContextImpl context, Thread[] filterThreads, String originId, ThreadLocalAction action, HandshakeConfig config) {
            super(context, filterThreads, originId, action, config);
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            PolyglotThreadInfo thread;
            synchronized (context) {
                thread = context.getCurrentThreadInfo();
            }
            thread.setSafepointActive(true);
            try {
                EngineAccessor.LANGUAGE.performTLAction(action, access);
            } finally {
                thread.setSafepointActive(false);
            }
        }

    }

    private final class PolyglotStatisticsAction extends ThreadLocalAction {

        private final LongSummaryStatistics intervalStatistics = new LongSummaryStatistics();
        private final LongSummaryStatistics blockedIntervalStatistics = new LongSummaryStatistics();
        private final String threadName;

        private long safepointCount;
        private long prevTime = 0;
        private long blockedTime = 0;
        private TimerTask task = null;
        private volatile StackTraceElement[] stackTrace = null;

        PolyglotStatisticsAction(Thread thread) {
            // no side-effects, async, recurring
            super(false, false, true);
            this.threadName = thread.getName();
        }

        @Override
        protected void perform(Access access) {
            if (this.task != null) {
                // Cancel the previous task if it has not started yet, does nothing otherwise.
                // If it has not started yet, then we have polled a safepoint before
                // missingPollMillis,
                // so we don't need a stacktrace/to run that task.
                this.task.cancel();
            }

            safepointCount++;
            long prev = this.prevTime;
            if (prev != 0) {
                long now = System.nanoTime();
                long duration = now - prev;
                intervalStatistics.accept(duration);

                if (stackTrace != null && !PolyglotLanguageContext.isContextCreation(stackTrace)) {
                    context.engine.getEngineLogger().info("No TruffleSafepoint.poll() for " + Duration.ofNanos(duration).toMillis() + "ms on " + threadName + " (stacktrace " + missingPollMillis +
                                    "ms after the last poll)" +
                                    System.lineSeparator() + formatStackTrace(stackTrace));
                    stackTrace = null;
                }
            }
            prepareForNextRun(access, System.nanoTime());
        }

        private void prepareForNextRun(Access access, long now) {
            this.prevTime = now;

            if (missingPollTimer != null) {
                Thread thread = access.getThread();
                this.task = new TimerTask() {
                    @Override
                    public void run() {
                        stackTrace = thread.getStackTrace();
                    }
                };
                missingPollTimer.schedule(this.task, missingPollMillis);
            }
        }

        private static String formatStackTrace(StackTraceElement[] stackTrace) {
            final Exception exception = new Exception();
            exception.setStackTrace(stackTrace);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(stream));
            final String stackTraceString = stream.toString();
            // Remove the java.lang.Exception line
            return stackTraceString.substring(stackTraceString.indexOf("\t"));
        }

        @Override
        protected void notifyBlocked(Access access) {
            if (this.task != null) {
                this.task.cancel();
            }
            this.prevTime = 0L;
            this.blockedTime = System.nanoTime();
        }

        @Override
        protected void notifyUnblocked(Access access) {
            if (this.prevTime == 0L) {
                long now = System.nanoTime();
                blockedIntervalStatistics.accept(now - this.blockedTime);
                prepareForNextRun(access, now);
            }
        }

        @Override
        public String toString() {
            return "PolyglotStatisticsAction@" + Integer.toHexString(hashCode());
        }

    }

}
