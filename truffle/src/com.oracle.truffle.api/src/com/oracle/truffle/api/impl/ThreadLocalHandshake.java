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
package com.oracle.truffle.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;

/**
 * Implementation class for thread local handshakes. Contains the parts that can be shared between
 * runtimes.
 */
public abstract class ThreadLocalHandshake {

    /*
     * This map contains all state objects for all threads accessible for other threads. Since the
     * thread needs to be weak and synchronized it is less efficient to access and is only used when
     * accessing the state of other threads.
     */
    private static final Map<Thread, TruffleSafepointImpl> SAFEPOINTS = Collections.synchronizedMap(new WeakHashMap<>());

    static void resetNativeImageState() {
        for (TruffleSafepointImpl impl : SAFEPOINTS.values()) {
            impl.verifyUnused();
        }
        SAFEPOINTS.clear();
    }

    protected ThreadLocalHandshake() {
    }

    public abstract void poll(Node enclosingNode);

    public abstract TruffleSafepointImpl getCurrent();

    protected boolean isSupported() {
        return true;
    }

    public void testSupport() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Thread local handshakes are not supported on this platform. " +
                            "A possible reason may be that the underlying JVMCI version is too old.");
        }
    }

    public boolean setChangeAllowActions(TruffleSafepoint safepoint, boolean enabled) {
        return ((TruffleSafepointImpl) safepoint).setChangeAllowActions(enabled);
    }

    public boolean isAllowActions(TruffleSafepoint safepoint) {
        return ((TruffleSafepointImpl) safepoint).isAllowActions();
    }

    /**
     * If this method is invoked the thread must be guaranteed to be polled. If the thread dies and
     * {@link #poll(Node)} was not invoked then an {@link IllegalStateException} is thrown;
     */
    @TruffleBoundary
    public final <T extends Consumer<Node>> Future<Void> runThreadLocal(Thread[] threads, T onThread, Consumer<T> onDone, boolean sideEffecting, boolean syncStartOfEvent, boolean syncEndOfEvent,
                    int syncActionMaxWait, boolean syncActionPrintStackTraces, TruffleLogger engineLogger) {
        testSupport();
        assert threads.length > 0;
        Handshake<T> handshake = new Handshake<>(threads, onThread, onDone, sideEffecting, threads.length, syncStartOfEvent, syncEndOfEvent, syncActionMaxWait, syncActionPrintStackTraces,
                        engineLogger);
        if (syncStartOfEvent || syncEndOfEvent) {
            synchronized (ThreadLocalHandshake.class) {
                addHandshakes(threads, handshake);
            }
        } else {
            addHandshakes(threads, handshake);
        }
        return handshake;
    }

    private <T extends Consumer<Node>> void addHandshakes(Thread[] threads, Handshake<T> handshake) {
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            if (!t.isAlive()) {
                throw new IllegalStateException("Thread no longer alive with pending handshake.");
            }
            getThreadState(t).addHandshake(t, handshake);
        }
    }

    @SuppressWarnings("static-method")
    public final boolean activateThread(TruffleSafepoint s, Future<?> f) {
        return ((TruffleSafepointImpl) s).activateThread((Handshake<?>) f);
    }

    @SuppressWarnings("static-method")
    public final boolean deactivateThread(TruffleSafepoint s, Future<?> f) {
        return ((TruffleSafepointImpl) s).deactivateThread((Handshake<?>) f);
    }

    public void ensureThreadInitialized() {
    }

    protected abstract void setFastPending(Thread t);

    @TruffleBoundary
    protected final void processHandshake(Node location) {
        TruffleSafepointImpl s = getCurrent();
        if (s.fastPendingSet) {
            s.processHandshakes(location, s.takeHandshakes());
        }
    }

    protected abstract void clearFastPending();

    private static Throwable combineThrowable(Throwable current, Throwable t) {
        if (current == null) {
            return t;
        }
        if (t instanceof ThreadDeath) {
            t.addSuppressed(current);
            return t;
        } else {
            current.addSuppressed(t);
            return current;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    private static final class StackTrace {
        final StackTraceElement[] elements;

        private StackTrace(StackTraceElement[] elements) {
            this.elements = elements;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }

        @Override
        public boolean equals(Object other) {
            return other == this || (other instanceof StackTrace stacktrace && Arrays.equals(elements, stacktrace.elements));
        }
    }

    /**
     * This class is based on {@link java.util.concurrent.CountDownLatch} but also supports dynamic
     * registration. A CountDownLatch already works like a (single-usage) barrier, so the only
     * addition is that dynamic registration. CountDownLatch works fine as a barrier, because all
     * countDown() calls happen-before await() calls and so all actions in all the threads involved
     * happen-before the actions after the await(). It is similar to {@code
     * Phaser#arriveAndAwaitAdvance()} which is equivalent to {@code awaitAdvance(arrive())}. The
     * way this works is the count in the latch is the volatile variable, every countDown() is a
     * compare-and-set, when the count reaches 0, all await() are released but only after reading
     * the count (in tryAcquireShared()), and hence observing the effects of all countDown() calls.
     */
    @SuppressWarnings("serial")
    private static final class Barrier extends AbstractQueuedSynchronizer {
        Barrier(int initialParties) {
            setState(initialParties);
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            assert acquires == 1;
            return getState() == 0 ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            assert releases == 1;
            while (true) {
                int count = getState();
                if (count == 0) {
                    return false; // no waiters when already 0
                }
                int nextCount = count - 1;
                if (compareAndSetState(count, nextCount)) {
                    return nextCount == 0; // release waiters
                }
            }
        }

        public boolean register() {
            while (true) {
                int count = getState();
                if (count == 0) {
                    return false; // too late to register
                } else {
                    int nextCount = count + 1;
                    if (compareAndSetState(count, nextCount)) {
                        return true;
                    }
                }
            }
        }

        public void arrive() {
            releaseShared(1);
        }

        public void await() throws InterruptedException {
            acquireSharedInterruptibly(1);
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        public int getCount() {
            return getState();
        }

        public boolean isTerminated() {
            return getState() == 0;
        }

        public void releaseAll() {
            while (!isTerminated()) {
                arrive();
            }
        }
    }

    public static final class Handshake<T extends Consumer<Node>> implements Future<Void> {

        private final boolean sideEffecting;
        private volatile boolean cancelled;
        private final T action;
        private final boolean syncStartOfEvent;
        private final Barrier startBarrier;
        private final boolean syncEndOfEvent;
        private final Barrier endBarrier;
        private final int syncActionMaxWait;
        private final boolean syncActionPrintStackTraces;
        private final TruffleLogger engineLogger;
        private final AtomicBoolean warned = new AtomicBoolean(false);
        // avoid rescheduling processed events on the same thread
        private final Map<Thread, Boolean> threads;
        private final Consumer<T> onDone;

        Handshake(Thread[] initialThreads, T action, Consumer<T> onDone, boolean sideEffecting, int numberOfThreads, boolean syncStartOfEvent, boolean syncEndOfEvent, int syncActionMaxWait,
                        boolean syncActionPrintStackTraces, TruffleLogger engineLogger) {
            this.action = action;
            this.onDone = onDone;
            this.sideEffecting = sideEffecting;
            this.syncStartOfEvent = syncStartOfEvent;
            this.startBarrier = syncStartOfEvent ? new Barrier(numberOfThreads) : null;
            this.syncEndOfEvent = syncEndOfEvent;
            this.endBarrier = new Barrier(numberOfThreads);
            this.syncActionMaxWait = syncActionMaxWait;
            this.syncActionPrintStackTraces = syncActionPrintStackTraces;
            this.engineLogger = engineLogger;
            /*
             * Mark the handshake for all initial threads as active (not deactivated).
             */
            this.threads = new ConcurrentHashMap<>(Arrays.stream(initialThreads).collect(Collectors.toMap(t -> t, t -> Boolean.FALSE)));
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        private boolean isTerminated() {
            return endBarrier.isTerminated();
        }

        void perform(Node node) {
            try {
                if (syncStartOfEvent) {
                    startBarrier.arrive();
                    await(startBarrier);
                }
                if (!cancelled) {
                    action.accept(node);
                }
            } finally {
                endBarrier.arrive();

                if (syncEndOfEvent) {
                    await(endBarrier);
                    assert isTerminated();
                }

                if (isTerminated()) {
                    onDone.accept(action);
                }
            }
        }

        private void await(Barrier barrier) {
            boolean interrupted = false;

            if (syncActionMaxWait == 0) {
                while (true) {
                    try {
                        barrier.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } else {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(syncActionMaxWait);
                long remaining;
                boolean success = false;
                while ((remaining = deadline - System.nanoTime()) > 0) {
                    try {
                        success = barrier.await(remaining, TimeUnit.NANOSECONDS);
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (!success) {
                    if (awaitTimeout(barrier)) {
                        // This thread will do the cancellation if awaitTimeout() returned true.
                        cancel(true);
                    } else {
                        /*
                         * Other threads should wait until cancelled is set to true, to ensure they
                         * don't run the action. This also ensures other threads continue to await()
                         * to have stacktraces representative of what they were doing before the
                         * timeout. That way when we print stacktraces it is much easier to
                         * understand the situation, as it represents the situation before the
                         * cancellation happened for any thread.
                         */
                        while (true) {
                            try {
                                barrier.await();
                                break;
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                        }
                    }
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean awaitTimeout(Barrier barrier) {
            if (warned.get() || !warned.compareAndSet(false, true)) {
                return false;
            }

            engineLogger.warning(barrier.getCount() + " threads did not reach the synchronous ThreadLocalAction " + action + " in " + syncActionMaxWait + " seconds. " +
                            "When using virtual threads this may be due to the issue that once more than Runtime.availableProcessors() virtual threads are pinned and waiting for each other, no virtual threads can progress (JDK-8334304). " +
                            "Cancelling this ThreadLocalAction to unblock. Use --engine.SynchronousThreadLocalActionPrintStackTraces=true to print thread stacktraces.");
            if (syncActionPrintStackTraces) {
                Map<StackTrace, List<Thread>> grouped = new LinkedHashMap<>();
                for (var entry : threads.entrySet()) {
                    if (!entry.getValue()) {
                        Thread thread = entry.getKey();
                        var stackTrace = new StackTrace(thread.getStackTrace());
                        grouped.computeIfAbsent(stackTrace, t -> new ArrayList<>()).add(thread);
                    }
                }

                for (var entry : grouped.entrySet()) {
                    var out = new StringBuilder("Stacktrace for:").append(System.lineSeparator());
                    for (Thread thread : entry.getValue()) {
                        out.append(thread).append(System.lineSeparator());
                    }

                    final Exception exception = new Exception();
                    exception.setStackTrace(entry.getKey().elements);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    exception.printStackTrace(new PrintStream(stream));
                    String stackTraceString = stream.toString();
                    // Remove the java.lang.Exception line
                    stackTraceString = stackTraceString.substring(stackTraceString.indexOf("\t"));

                    engineLogger.warning(out + stackTraceString);
                }
            }

            return true;
        }

        boolean activateThread() {
            if (syncStartOfEvent) {
                if (!startBarrier.register()) {
                    return false;
                }

                if (!endBarrier.register()) {
                    /*
                     * endBarrier.register() -> false with startBarrier.register() -> true before is
                     * impossible, because the other threads must wait this thread in
                     * `await(startBarrier)` before continuing.
                     */
                    throw CompilerDirectives.shouldNotReachHere();
                }

                return true;
            } else {
                return endBarrier.register();
            }
        }

        void deactivateThread() {
            if (syncStartOfEvent) {
                startBarrier.arrive();
            }
            endBarrier.arrive();

            if (isTerminated()) {
                onDone.accept(action);
            }
        }

        @Override
        public Void get() throws InterruptedException {
            endBarrier.await();
            if (cancelled) {
                throw new CancellationException();
            }
            return null;
        }

        public Void get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            if (!endBarrier.await(timeout, unit)) {
                throw new TimeoutException();
            }
            if (cancelled) {
                throw new CancellationException();
            }
            return null;
        }

        public boolean isDone() {
            return cancelled || isTerminated();
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!isTerminated()) {
                cancelled = true;
                // Release all waiters on the barriers
                if (syncStartOfEvent) {
                    startBarrier.releaseAll();
                }
                endBarrier.releaseAll();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "Handshake[action=" + action + ", startBarrier=" + startBarrier + ", endBarrier=" + endBarrier + ", cancelled=" + cancelled + ", sideEffecting=" +
                            sideEffecting + ", syncStartOfEvent=" + syncStartOfEvent + ", syncEndOfEvent=" + syncEndOfEvent + "]";
        }

    }

    static final class HandshakeEntry {

        final Handshake<?> handshake;
        final boolean reactivated;

        HandshakeEntry(Handshake<?> handshake, boolean reactivated) {
            this.handshake = handshake;
            this.reactivated = reactivated;
        }

        @Override
        public String toString() {
            return "HandshakeEntry[" + handshake + " reactivated=" + reactivated + "]";
        }
    }

    protected final TruffleSafepointImpl getThreadState(Thread thread) {
        return SAFEPOINTS.computeIfAbsent(thread, (t) -> new TruffleSafepointImpl(this));
    }

    /** One per {@link Thread}, see {@link #SAFEPOINTS}. */
    protected static final class TruffleSafepointImpl extends TruffleSafepoint {

        private final ReentrantLock lock = new ReentrantLock();
        private final ThreadLocalHandshake impl;
        private volatile boolean fastPendingSet;
        private boolean sideEffectsEnabled = true;
        private boolean enabled = true;
        private volatile boolean changeAllowActionsAllowed;
        private Interrupter blockedAction;
        private boolean interrupted;

        private final LinkedList<HandshakeEntry> handshakes = new LinkedList<>();

        TruffleSafepointImpl(ThreadLocalHandshake handshake) {
            super(DefaultRuntimeAccessor.ENGINE);
            this.impl = handshake;
        }

        void verifyUnused() throws AssertionError {
            if (this.lock.isHeldByCurrentThread() || this.lock.isLocked()) {
                throw new AssertionError("Invalid locked state for safepoint.");
            }
            this.lock.lock();
            try {
                if (this.blockedAction != null) {
                    throw new AssertionError("Invalid pending blocked action.");
                }
                if (this.interrupted) {
                    throw new AssertionError("Invalid pending interrupted state.");
                }
                if (this.isPending()) {
                    throw new AssertionError("Invalid pending handshakes.");
                }
                // correct usage always needs to reset the side-effects enabled state
                if (!this.sideEffectsEnabled) {
                    throw new AssertionError("Invalid side-effects disabled state");
                }

                if (!this.enabled) {
                    throw new AssertionError("Invalid allow actions disabled state");
                }
            } finally {
                this.lock.unlock();
            }
        }

        void processHandshakes(Node location, List<HandshakeEntry> toProcess) {
            if (toProcess == null) {
                return;
            }
            Throwable ex = null;
            for (HandshakeEntry current : toProcess) {
                if (claimEntry(current)) {
                    try {
                        current.handshake.perform(location);
                    } catch (Throwable e) {
                        ex = combineThrowable(ex, e);
                    }
                }
            }
            if (fastPendingSet) {
                resetPending();
            }
            if (ex != null) {
                throw sneakyThrow(ex);
            }
        }

        public boolean deactivateThread(Handshake<?> handshake) {
            lock.lock();
            try {
                HandshakeEntry current = lookupEntry(handshake);
                if (current != null) {
                    /*
                     * We cannot guarantee that side-effecting events are processed as they can be
                     * disabled.
                     */
                    assert !current.reactivated || current.handshake.sideEffecting : "Reactivated handshake was not processed!";
                    handshake.deactivateThread();
                    claimEntry(current);
                    /*
                     * Mark the handshake for the current thread as deactivated.
                     */
                    handshake.threads.put(Thread.currentThread(), Boolean.TRUE);
                    resetPending();
                    return true;
                }

            } finally {
                lock.unlock();
            }
            return false;
        }

        public boolean activateThread(Handshake<?> handshake) {
            if (handshake.isDone()) {
                return false;
            }
            lock.lock();
            try {
                HandshakeEntry current = lookupEntry(handshake);
                if (current != null) {
                    /*
                     * The handshake has already been put to this thread and it is ready to be
                     * processed.
                     */
                    return false;
                }
                boolean reactivated = false;
                if (handshake.threads.containsKey(Thread.currentThread())) {
                    if (!handshake.threads.get(Thread.currentThread())) {
                        /*
                         * The handshake has already been processed.
                         */
                        return false;
                    } else {
                        /*
                         * The handshake has been deactivated before it was processed and should be
                         * reactivated.
                         */
                        reactivated = true;
                    }
                }
                /*
                 * Mark the handshake for the current thread as active (not deactivated).
                 */
                handshake.threads.put(Thread.currentThread(), Boolean.FALSE);
                if (handshake.activateThread()) {
                    addHandshakeImpl(Thread.currentThread(), handshake, reactivated);
                    return true;
                }
            } finally {
                lock.unlock();
            }
            return false;
        }

        private HandshakeEntry lookupEntry(Handshake<?> handshake) {
            assert lock.isHeldByCurrentThread();

            for (HandshakeEntry entry : handshakes) {
                if (entry.handshake == handshake) {
                    return entry;
                }
            }
            return null;
        }

        void addHandshake(Thread t, Handshake<?> handshake) {
            lock.lock();
            try {
                addHandshakeImpl(t, handshake, false);
            } finally {
                lock.unlock();
            }
        }

        private void addHandshakeImpl(Thread t, Handshake<?> handshake, boolean reactivated) {
            handshakes.add(new HandshakeEntry(handshake, reactivated));
            if (isPending()) {
                setFastPendingAndInterrupt(t);
            }
        }

        private void setFastPendingAndInterrupt(Thread t) {
            assert lock.isHeldByCurrentThread();
            if (!fastPendingSet) {
                fastPendingSet = true;
                impl.setFastPending(t);
            }
            Interrupter action = this.blockedAction;
            if (action != null) {
                interrupted = true;
                action.interrupt(t);
            }
        }

        List<HandshakeEntry> takeHandshakes() {
            lock.lock();
            try {
                if (this.interrupted) {
                    this.blockedAction.resetInterrupted();
                    this.interrupted = false;
                }
                if (isPending()) {
                    List<HandshakeEntry> taken = takeHandshakeImpl();
                    assert !taken.isEmpty();
                    return taken;
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        public boolean isFastPendingSet() {
            return fastPendingSet;
        }

        private void resetPending() {
            lock.lock();
            try {
                if (fastPendingSet && !isPending()) {
                    fastPendingSet = false;
                    impl.clearFastPending();
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean claimEntry(HandshakeEntry entry) {
            lock.lock();
            try {
                return this.handshakes.removeFirstOccurrence(entry);
            } finally {
                lock.unlock();
            }
        }

        private List<HandshakeEntry> takeHandshakeImpl() {
            if (!enabled) {
                return Collections.emptyList();
            }
            List<HandshakeEntry> toProcess = new ArrayList<>(this.handshakes.size());
            for (HandshakeEntry entry : this.handshakes) {
                if (isPending(entry)) {
                    toProcess.add(entry);
                }
            }
            return toProcess;
        }

        private boolean isPending(HandshakeEntry entry) {
            if (sideEffectsEnabled || !entry.handshake.sideEffecting) {
                return true;
            }
            return false;
        }

        @Override
        public <T> void setBlocked(Node location, Interrupter interrupter, Interruptible<T> interruptible, T object, Runnable beforeInterrupt, Consumer<Throwable> afterInterrupt) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";

            /*
             * We want to avoid to ever call the Interruptible interface on compiled code paths to
             * make native image avoid marking it as runtime compiled. It is common that
             * interruptibles are just a method reference to e.g. Lock::lockInterruptibly which
             * could no longer be used otherwise as PE would fail badly for these methods and we
             * would get black list method errors in native image.
             *
             * A good workaround is to use our own interface that is a subclass of Interruptible but
             * that must be used to opt-in to compilation.
             */
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(interruptible) && interruptible instanceof CompiledInterruptible<?>) {
                setBlockedCompiled(location, interrupter, (CompiledInterruptible<T>) interruptible, object, beforeInterrupt, afterInterrupt);
            } else {
                setBlockedBoundary(location, interrupter, interruptible, object, beforeInterrupt, afterInterrupt);
            }
        }

        private <T> void setBlockedCompiled(Node location, Interrupter interrupter, CompiledInterruptible<T> interruptible, T object, Runnable beforeInterrupt,
                        Consumer<Throwable> afterInterrupt) {
            Interrupter prev = this.blockedAction;
            try {
                while (true) {
                    try {
                        setBlockedImpl(location, interrupter, false);
                        interruptible.apply(object);
                        break;
                    } catch (InterruptedException e) {
                        setBlockedAfterInterrupt(location, prev, beforeInterrupt, afterInterrupt);
                    }
                }
            } finally {
                setBlockedImpl(location, prev, false);
            }
        }

        @TruffleBoundary
        private <T> void setBlockedBoundary(Node location, Interrupter interrupter, Interruptible<T> interruptible, T object, Runnable beforeInterrupt, Consumer<Throwable> afterInterrupt) {
            Interrupter prev = this.blockedAction;
            try {
                while (true) {
                    try {
                        setBlockedImpl(location, interrupter, false);
                        interruptible.apply(object);
                        break;
                    } catch (InterruptedException e) {
                        setBlockedAfterInterrupt(location, prev, beforeInterrupt, afterInterrupt);
                    }
                }
            } finally {
                setBlockedImpl(location, prev, false);
            }
        }

        @Override
        public <T, R> R setBlockedFunction(Node location, Interrupter interrupter, InterruptibleFunction<T, R> interruptible, T object, Runnable beforeInterrupt,
                        Consumer<Throwable> afterInterrupt) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";

            /*
             * We want to avoid to ever call the InterruptibleFunction interface on compiled code
             * paths to make native image avoid marking it as runtime compiled. It is common that
             * interruptibles are just a method reference to e.g. BlockingQueue::take which could no
             * longer be used otherwise as PE would fail badly for these methods and we would get
             * black list method errors in native image.
             *
             * A good workaround is to use our own interface that is a subclass of
             * InterruptibleFunction but that must be used to opt-in to compilation.
             */
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(interruptible) && interruptible instanceof CompiledInterruptibleFunction<?, ?>) {
                return setBlockedFunctionCompiled(location, interrupter, (CompiledInterruptibleFunction<T, R>) interruptible, object, beforeInterrupt, afterInterrupt);
            } else {
                return setBlockedFunctionBoundary(location, interrupter, interruptible, object, beforeInterrupt, afterInterrupt);
            }
        }

        private <T, R> R setBlockedFunctionCompiled(Node location, Interrupter interrupter, CompiledInterruptibleFunction<T, R> interruptible, T object, Runnable beforeInterrupt,
                        Consumer<Throwable> afterInterrupt) {
            Interrupter prev = this.blockedAction;
            try {
                while (true) {
                    try {
                        setBlockedImpl(location, interrupter, false);
                        return interruptible.apply(object);
                    } catch (InterruptedException e) {
                        setBlockedAfterInterrupt(location, prev, beforeInterrupt, afterInterrupt);
                    }
                }
            } finally {
                setBlockedImpl(location, prev, false);
            }
        }

        @TruffleBoundary
        private <T, R> R setBlockedFunctionBoundary(Node location, Interrupter interrupter, InterruptibleFunction<T, R> interruptible, T object, Runnable beforeInterrupt,
                        Consumer<Throwable> afterInterrupt) {
            Interrupter prev = this.blockedAction;
            try {
                while (true) {
                    try {
                        setBlockedImpl(location, interrupter, false);
                        return interruptible.apply(object);
                    } catch (InterruptedException e) {
                        setBlockedAfterInterrupt(location, prev, beforeInterrupt, afterInterrupt);
                    }
                }
            } finally {
                setBlockedImpl(location, prev, false);
            }
        }

        @TruffleBoundary
        private void setBlockedAfterInterrupt(final Node location, final Interrupter interrupter, Runnable beforeInterrupt, Consumer<Throwable> afterInterrupt) {
            if (beforeInterrupt != null) {
                beforeInterrupt.run();
            }
            Throwable t = null;
            try {
                setBlockedImpl(location, interrupter, true);
            } catch (Throwable e) {
                t = e;
                throw e;
            } finally {
                if (afterInterrupt != null) {
                    afterInterrupt.accept(t);
                }
            }
        }

        @TruffleBoundary
        private void setBlockedImpl(final Node location, final Interrupter interrupter, boolean processSafepoints) {
            List<HandshakeEntry> toProcess = null;
            lock.lock();
            try {
                if (processSafepoints) {
                    if (isPending()) {
                        toProcess = takeHandshakeImpl();
                    }
                }
                if (interrupted) {
                    assert this.blockedAction != null;
                    this.blockedAction.resetInterrupted();
                    this.interrupted = false;
                }
                this.blockedAction = interrupter;
            } finally {
                lock.unlock();
            }

            processHandshakes(location, toProcess);

            if (interrupter != null) {
                /*
                 * We can only process once. Now we need to continue running, but interrupt.
                 */
                interruptIfPending(interrupter);
            }
        }

        private void interruptIfPending(final Interrupter interrupter) {
            lock.lock();
            try {
                if (interrupter != null && isPending()) {
                    interrupted = true;
                    interrupter.interrupt(Thread.currentThread());
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Is a handshake really pending?
         */
        private boolean isPending() {
            assert lock.isHeldByCurrentThread();
            if (!enabled) {
                return false;
            }
            for (HandshakeEntry entry : this.handshakes) {
                if (isPending(entry)) {
                    return true;
                }
            }
            return false;
        }

        boolean setChangeAllowActions(boolean changeAllowActionsAllowed) {
            boolean prevChangeAllowActionsAllowed = this.changeAllowActionsAllowed;
            this.changeAllowActionsAllowed = changeAllowActionsAllowed;
            return prevChangeAllowActionsAllowed;
        }

        boolean isAllowActions() {
            return enabled;
        }

        @Override
        @TruffleBoundary
        public boolean setAllowActions(boolean enabled) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            lock.lock();
            try {
                if (!changeAllowActionsAllowed) {
                    throw new IllegalStateException("Using setAllowActions is only permitted during finalization of a language. See TruffleLanguage.finalizeContext(Object) for further details.");
                }
                boolean prev = this.enabled;
                this.enabled = enabled;
                updateFastPending();
                return prev;
            } finally {
                lock.unlock();
            }
        }

        @Override
        @TruffleBoundary
        public boolean setAllowSideEffects(boolean enabled) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            lock.lock();
            try {
                boolean prev = this.sideEffectsEnabled;
                this.sideEffectsEnabled = enabled;
                updateFastPending();
                return prev;
            } finally {
                lock.unlock();
            }
        }

        @Override
        @TruffleBoundary
        public boolean hasPendingSideEffectingActions() {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            lock.lock();
            try {
                return !sideEffectsEnabled && hasSideEffecting();
            } finally {
                lock.unlock();
            }
        }

        private boolean hasSideEffecting() {
            assert lock.isHeldByCurrentThread();

            for (HandshakeEntry entry : this.handshakes) {
                if (entry.handshake.sideEffecting) {
                    return true;
                }
            }
            return false;
        }

        private void updateFastPending() {
            if (isPending()) {
                setFastPendingAndInterrupt(Thread.currentThread());
            } else {
                if (fastPendingSet) {
                    fastPendingSet = false;
                    impl.clearFastPending();
                }
            }
        }
    }

}
