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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;

public abstract class ThreadLocalHandshake {

    /*
     * This map contains all state objects for all threads accessible for other threads. Since the
     * thread needs to be weak and synchronized is less efficient to access and is only used when
     * accessing the state of other threads.
     */
    private static final Map<Thread, TruffleSafepointImpl> SAFEPOINTS = Collections.synchronizedMap(new WeakHashMap<>());

    protected ThreadLocalHandshake() {
    }

    public abstract void poll(Node enclosingNode);

    public abstract TruffleSafepointImpl getCurrent();

    /**
     * If this method is invoked the thread must be guaranteed to be polled. If the thread dies and
     * {@link #poll(Node)} was not invoked then an {@link IllegalStateException} is thrown;
     *
     * @param target
     * @param threads
     * @param run
     */
    @TruffleBoundary
    public final void runThreadLocal(CallTarget target, Thread[] threads, Consumer<Node> run) {
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            if (!t.isAlive()) {
                throw new IllegalStateException("Thread no longer alive with pending handshake.");
            }
            getThreadState(t).putHandshake(t, run);
        }
    }

    protected abstract void setPending(Thread t);

    @TruffleBoundary
    protected final void processHandshake(Node node) {
        Throwable ex = null;
        TruffleSafepointImpl s = getCurrent();
        while (true) {
            HandshakeEntry handshake = null;
            if (s.isProcessHandshake()) {
                handshake = s.takeHandshake();
            }
            if (handshake == null) {
                break;
            }
            ex = combineThrowable(ex, handshake.process(node));
        }
        if (ex != null) {
            throw sneakyThrow(ex);
        }
    }

    protected abstract void clearPending();

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

    static final class HandshakeEntry {

        final Consumer<Node> action;
        final HandshakeEntry next;

        private volatile HandshakeEntry prev;

        HandshakeEntry(Consumer<Node> action, HandshakeEntry next) {
            this.action = action;
            if (next != null) {
                next.prev = this;
            }
            this.next = next;
        }

        Throwable process(Node enclosingNode) {
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
                    current.action.accept(enclosingNode);
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
        private volatile boolean pending;
        private volatile boolean enabled = true;
        private volatile Interruptable blockedAction;
        private boolean interrupted;
        private HandshakeEntry handshakes;

        TruffleSafepointImpl(ThreadLocalHandshake handshake) {
            super(DefaultRuntimeAccessor.ENGINE);
            this.impl = handshake;
        }

        void putHandshake(Thread t, Consumer<Node> run) {
            lock.lock();
            try {
                if (!pending) {
                    pending = true;
                    if (enabled) {
                        setPendingAndInterrupt(t);
                    }
                }
                handshakes = new HandshakeEntry(run, handshakes);
            } finally {
                lock.unlock();
            }
        }

        private void setPendingAndInterrupt(Thread t) {
            assert lock.isHeldByCurrentThread();
            impl.setPending(t);
            Interruptable action = this.blockedAction;
            if (action != null) {
                action.interrupt(t);
                interrupted = true;
            }
        }

        boolean isProcessHandshake() {
            return pending && enabled;
        }

        HandshakeEntry takeHandshake() {
            lock.lock();
            try {
                if (isProcessHandshake()) {
                    pending = false;
                    impl.clearPending();
                    HandshakeEntry taken = handshakes;
                    handshakes = null;
                    return taken;
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        @Override
        @TruffleBoundary
        public Interruptable setBlocked(Interruptable interruptable) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            lock.lock();
            try {
                Interruptable prev = this.blockedAction;
                if (interruptable != null && this.pending && this.enabled) {
                    interruptable.interrupt(Thread.currentThread());
                    interrupted = true;
                }
                this.blockedAction = interruptable;
                if (prev != null && interrupted) {
                    prev.interrupted();
                    interrupted = false;
                }
                return prev;
            } finally {
                lock.unlock();
            }
        }

        // TODO can we get rid of the synchronized block here?
        @Override
        @TruffleBoundary
        public synchronized boolean setEnabled(boolean enabled) {
            assert impl.getCurrent() == this : "Cannot be used from a different thread.";
            boolean prev = this.enabled;
            this.enabled = enabled;
            if (this.pending) {
                updatePending(enabled);
            }
            return prev;
        }

        @TruffleBoundary
        private void updatePending(boolean newEnabled) {
            lock.lock();
            try {
                if (this.pending) {
                    if (newEnabled) {
                        setPendingAndInterrupt(Thread.currentThread());
                    } else {
                        impl.clearPending();
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
