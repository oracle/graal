/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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

    protected ThreadLocalHandshake() {
    }

    public abstract void poll(Node enclosingNode);

    public abstract TruffleSafepointImpl getCurrent();

    protected boolean isSupported() {
        return true;
    }

    /**
     * If this method is invoked the thread must be guaranteed to be polled. If the thread dies and
     * {@link #poll(Node)} was not invoked then an {@link IllegalStateException} is thrown;
     */
    @TruffleBoundary
    public final <T extends Consumer<Node>> Future<Void> runThreadLocal(Thread[] threads, T onThread, Consumer<T> onDone, boolean sideEffecting, boolean sync) {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Thread local handshakes are not supported on this platform. " +
                            "A possible reason may be that the underlying JVMCI version is too old.");
        }
        Handshake<T> handshake = new Handshake<>(threads, onThread, onDone, sideEffecting, threads.length, sync);
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            if (!t.isAlive()) {
                throw new IllegalStateException("Thread no longer alive with pending handshake.");
            }
            getThreadState(t).putHandshake(t, handshake);
        }
        return handshake;
    }

    @SuppressWarnings("static-method")
    public final void activateThread(TruffleSafepoint s, Future<?> f) {
        ((TruffleSafepointImpl) s).activateThread((Handshake<?>) f);
    }

    @SuppressWarnings("static-method")
    public final void deactivateThread(TruffleSafepoint s, Future<?> f) {
        ((TruffleSafepointImpl) s).deactivateThread((Handshake<?>) f);
    }

    public void ensureThreadInitialized() {
    }

    protected abstract void setFastPending(Thread t);

    @TruffleBoundary
    protected final void processHandshake(Node location) {
        Throwable ex = null;
        TruffleSafepointImpl s = getCurrent();
        HandshakeEntry handshake = null;
        if (s.fastPendingSet) {
            handshake = s.takeHandshake(location);
        }
        if (handshake != null) {
            try {
                ex = combineThrowable(ex, handshake.process(location));
                if (ex != null) {
                    throw sneakyThrow(ex);
                }
            } finally {
                s.doneProcessing(handshake, location);
            }
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

    public static final class Handshake<T extends Consumer<Node>> implements Future<Void> {

        private final boolean sideEffecting;
        private final Phaser phaser;
        private volatile boolean cancelled;
        private final T action;
        private final Consumer<T> onDone;
        private final boolean sync;
        // avoid rescheduling on the same thread again
        private final Set<Thread> threads;

        @SuppressWarnings("unchecked")
        Handshake(Thread[] initialThreads, T action, Consumer<T> onDone, boolean sideEffecting, int numberOfThreads, boolean sync) {
            this.action = action;
            this.onDone = onDone;
            this.sideEffecting = sideEffecting;
            this.sync = sync;
            this.phaser = new Phaser(numberOfThreads);
            this.threads = Collections.synchronizedSet(new HashSet<>(Arrays.asList(initialThreads)));
        }

        boolean isSideEffecting() {
            return sideEffecting;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        void perform(Node node) {
            try {
                if (sync) {
                    phaser.arriveAndAwaitAdvance();
                }
                if (!cancelled) {
                    action.accept(node);
                }
            } finally {
                if (sync) {
                    phaser.arriveAndDeregister();
                    phaser.awaitAdvance(1);
                } else {
                    phaser.arriveAndDeregister();
                }
                onDone.accept(action);
            }
        }

        boolean activateThread() {
            int result = phaser.register();
            if (result != 0) {
                // did not activate on time.
                phaser.arriveAndDeregister();
                return false;
            }
            return true;
        }

        void deactivateThread() {
            phaser.arriveAndDeregister();
        }

        @Override
        public Void get() throws InterruptedException {
            if (sync) {
                this.phaser.awaitAdvanceInterruptibly(1);
            } else {
                this.phaser.awaitAdvanceInterruptibly(0);
            }
            return null;
        }

        public boolean isDone() {
            return cancelled || phaser.getUnarrivedParties() == 0;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            if (phaser.getUnarrivedParties() > 0) {
                cancelled = true;
                return true;
            } else {
                return false;
            }
        }

        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            this.phaser.awaitAdvanceInterruptibly(0, timeout, unit);
            return null;
        }

    }

    static final class HandshakeEntry {

        final Handshake<?> handshake;
        final HandshakeEntry next;

        private volatile HandshakeEntry prev;
        boolean processed;
        boolean active = true;

        HandshakeEntry(Handshake<?> handshake, HandshakeEntry next) {
            this.handshake = handshake;
            if (next != null) {
                next.prev = this;
            }
            this.next = next;
        }

        Throwable process(Node location) {
            /*
             * Retain schedule order and process next first. Schedule order is important for events
             * that perform synchronization between multiple threads to avoid deadlocks.
             *
             * We use a prev pointer to avoid arbitrary deep recursive stacks processing handshakes.
             */
            HandshakeEntry current = this;
            while (current.next != null) {
                current = current.next;
            }

            assert current != null;
            Throwable ex = null;
            while (current != null) {
                try {
                    if (!current.processed && current.active) {
                        current.processed = true;
                        current.handshake.perform(location);
                    }
                } catch (Throwable e) {
                    ex = combineThrowable(ex, e);
                } finally {
                    current = current.prev;
                }
            }
            return ex;
        }
    }

    protected final TruffleSafepointImpl getThreadState(Thread thread) {
        return SAFEPOINTS.computeIfAbsent(thread, (t) -> new TruffleSafepointImpl(this));
    }

    protected static final class TruffleSafepointImpl extends TruffleSafepoint {

        private final ReentrantLock lock = new ReentrantLock();
        private final ThreadLocalHandshake impl;
        private volatile boolean fastPendingSet;
        private boolean sideEffectsEnabled = true;
        private Interrupter blockedAction;
        private boolean interrupted;
        private HandshakeEntry handshakes;
        private boolean hasSideEffects;
        private boolean hasNonSideEffects;
        private final ArrayDeque<HandshakeEntry> recursiveHandshakes = new ArrayDeque<>();
        private final ArrayDeque<Node> recursiveLocations = new ArrayDeque<>();

        TruffleSafepointImpl(ThreadLocalHandshake handshake) {
            super(DefaultRuntimeAccessor.ENGINE);
            this.impl = handshake;
        }

        public void deactivateThread(Handshake<?> handshake) {
            lock.lock();
            try {
                HandshakeEntry current = this.handshakes;
                while (current != null) {
                    if (current.handshake == handshake) {
                        if (!current.active) {
                            // already inactive
                            return;
                        }
                        break;
                    }
                    current = current.next;
                }

                if (current != null) {
                    // still active
                    assert current.active;
                    current.active = false;
                    handshake.deactivateThread();
                }
            } finally {
                lock.unlock();
            }
        }

        public void activateThread(Handshake<?> handshake) {
            if (handshake.isDone()) {
                return;
            }
            lock.lock();
            try {
                HandshakeEntry current = this.handshakes;
                while (current != null) {
                    if (current.handshake == handshake) {
                        /*
                         * The handshake has already been put to this thread and it is active or it
                         * is inactive and must not be re-activated.
                         */
                        return;
                    }
                    current = current.next;
                }
                // not yet put or already processed
                assert current == null;
                if (!handshake.threads.add(Thread.currentThread())) {
                    // already processed on that thread, we don't want to process twice.
                    return;
                }
                if (handshake.activateThread()) {
                    putHandshakeImpl(Thread.currentThread(), handshake);
                }
            } finally {
                lock.unlock();
            }
        }

        void putHandshake(Thread t, Handshake<?> handshake) {
            lock.lock();
            try {
                putHandshakeImpl(t, handshake);
            } finally {
                lock.unlock();
            }
        }

        private HandshakeEntry putHandshakeImpl(Thread t, Handshake<?> handshake) {
            handshakes = new HandshakeEntry(handshake, handshakes);
            hasSideEffects = hasSideEffects || handshake.sideEffecting;
            hasNonSideEffects = hasNonSideEffects || !handshake.sideEffecting;

            if (isPending() && !fastPendingSet) {
                fastPendingSet = true;
                setFastPendingAndInterrupt(t);
            }
            return handshakes;
        }

        private void setFastPendingAndInterrupt(Thread t) {
            assert lock.isHeldByCurrentThread();
            impl.setFastPending(t);
            Interrupter action = this.blockedAction;
            if (action != null) {
                action.interrupt(t);
                interrupted = true;
            }
        }

        HandshakeEntry takeHandshake(Node location) {
            lock.lock();
            try {
                if (isPending()) {
                    assert fastPendingSet : "invalid state";

                    fastPendingSet = false;
                    impl.clearFastPending();

                    HandshakeEntry taken = takeHandshakeImpl();
                    assert taken != null;

                    /*
                     * We need to remember the currently being processed handshake in case we end up
                     * in a setBlocked call inside of an action. If an event is blocking we need to
                     * continue the currently activly processed events.
                     *
                     * HandshakeEntry remembers whether it was already processed or not.
                     */
                    recursiveHandshakes.push(taken);
                    recursiveLocations.push(location);

                    if (this.interrupted) {
                        this.interrupted = false;
                        this.blockedAction.resetInterrupted();
                    }

                    return taken;
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        private HandshakeEntry takeHandshakeImpl() {
            HandshakeEntry taken;
            if (sideEffectsEnabled) {
                // just take them all -> fast-path
                taken = this.handshakes;
                this.handshakes = null;
                this.hasSideEffects = false;
                this.hasNonSideEffects = false;
            } else {
                if (hasSideEffects) {
                    assert this.hasNonSideEffects : "isPending() should not have returned true";

                    // we have side-effects and we don't process them
                    // so we need to split them into two lists
                    HandshakeEntry unprocessed = null;
                    HandshakeEntry processing = null;
                    HandshakeEntry current = this.handshakes;
                    while (current != null) {
                        if (current.handshake.sideEffecting) {
                            // do not process side-effecting events
                            unprocessed = new HandshakeEntry(current.handshake, unprocessed);
                            unprocessed.active = current.active;
                        } else {
                            processing = new HandshakeEntry(current.handshake, processing);
                        }
                        current = current.next;
                    }
                    taken = processing;
                    this.handshakes = unprocessed;
                    this.hasNonSideEffects = false;
                    assert this.hasSideEffects;
                } else {
                    // no side-effects scheduled just process
                    taken = this.handshakes;
                    this.handshakes = null;
                    this.hasNonSideEffects = false;
                    assert !this.hasSideEffects;
                }
            }
            return taken;
        }

        void doneProcessing(HandshakeEntry handshake, Node location) {
            HandshakeEntry done = recursiveHandshakes.pop();
            assert done == handshake : "illegal state";
            Node doneLocation = recursiveLocations.pop();
            assert doneLocation == location;
        }

        @Override
        @TruffleBoundary
        public Interrupter setBlocked(Interrupter interruptable) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            lock.lock();
            try {
                Interrupter prev = this.blockedAction;
                if (interruptable != null && isPending()) {
                    interruptable.interrupt(Thread.currentThread());
                    interrupted = true;
                }
                this.blockedAction = interruptable;
                if (prev != null && interrupted) {
                    prev.resetInterrupted();
                    interrupted = false;
                }
                return prev;
            } finally {
                lock.unlock();

                /*
                 * We try to process all recursive currently processing handshakes here as the
                 * current action seems to block. This allows other currently processing handshakes
                 * to complete independently.
                 */
                if (!recursiveHandshakes.isEmpty()) {
                    flushRecursiveHandshakes();
                }
            }
        }

        protected void flushRecursiveHandshakes() {
            Iterator<HandshakeEntry> entries = recursiveHandshakes.iterator();
            Iterator<Node> locations = recursiveLocations.iterator();
            Throwable t = null;
            while (entries.hasNext() && locations.hasNext()) {
                t = combineThrowable(entries.next().process(locations.next()), t);
            }
            if (t != null) {
                sneakyThrow(t);
            }
        }

        /**
         * Is a handshake really pending?
         */
        private boolean isPending() {
            assert lock.isHeldByCurrentThread();
            if (sideEffectsEnabled) {
                return hasNonSideEffects || hasSideEffects;
            } else {
                return hasNonSideEffects;
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
                return !sideEffectsEnabled && hasSideEffects;
            } finally {
                lock.unlock();
            }
        }

        private void updateFastPending() {
            if (isPending()) {
                if (!fastPendingSet) {
                    fastPendingSet = true;
                    setFastPendingAndInterrupt(Thread.currentThread());
                }
            } else {
                if (fastPendingSet) {
                    fastPendingSet = false;
                    impl.clearFastPending();
                }
            }
        }
    }

}
