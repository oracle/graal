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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.oracle.truffle.api.ThreadLocalAccess;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.Node;

final class PolyglotSafepointManager {

    private static final ThreadLocalHandshake TL_HANDSHAKE = EngineAccessor.ACCESSOR.runtimeSupport().getThreadLocalHandshake();

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    static Throwable handleEx(Throwable current, Throwable t) {
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

    static Future<Void> runThreadLocal(PolyglotContextImpl context,
                    Thread[] threads, Consumer<ThreadLocalAccess> action, boolean async) {

        // lock to stop new threads
        CountDownLatch doneLatch;
        synchronized (context) {
            // send enter/leave to slow-path
            context.setCachedThreadInfo(PolyglotThreadInfo.NULL);

            Set<Thread> filterThreads = null;
            if (threads != null) {
                filterThreads = new HashSet<>(Arrays.asList(threads));
            }

            List<Thread> activePolyglotThreads = new ArrayList<>();
            for (PolyglotThreadInfo info : context.getSeenThreads().values()) {
                if (info.isActiveNotCancelled() && (filterThreads == null || filterThreads.contains(info.getThread()))) {
                    activePolyglotThreads.add(info.getThread());
                }
            }

            Thread[] activeThreads = activePolyglotThreads.toArray(new Thread[0]);
            doneLatch = new CountDownLatch(activeThreads.length);
            AbstractTLHandshake handshake;
            if (async) {
                handshake = new AsyncEvent(context, action, doneLatch);
            } else {
                handshake = new SafepointEvent(context, activeThreads, action, doneLatch);
            }
            TL_HANDSHAKE.runThreadLocal(context.engine.getDummyCallTarget(context), activeThreads, handshake);
        }

        TL_HANDSHAKE.poll(null);
        return new ThreadLocalFuture(doneLatch);
    }

    static final class PolyglotTLAccess extends ThreadLocalAccess {

        final Thread thread;
        final Node location;
        volatile boolean invalid;

        PolyglotTLAccess(Thread thread, Node location) {
            super(PolyglotImpl.getInstance());
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

    private static class ThreadLocalFuture implements Future<Void> {

        final CountDownLatch latch;

        ThreadLocalFuture(CountDownLatch latch) {
            this.latch = latch;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        public Void get() throws InterruptedException, ExecutionException {
            latch.await();
            return null;
        }

        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
            return null;
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return latch.getCount() == 0;
        }
    }

    private abstract static class AbstractTLHandshake implements Consumer<Node> {

        protected final PolyglotContextImpl context;

        AbstractTLHandshake(PolyglotContextImpl context) {
            this.context = context;
        }

        public final void accept(Node location) {
            Object prev = context.engine.enterIfNeeded(context);
            try {
                PolyglotTLAccess access = new PolyglotTLAccess(Thread.currentThread(), location);
                try {
                    acceptImpl(access);
                } finally {
                    access.invalid = true;
                }
            } finally {
                context.engine.leaveIfNeeded(prev, context);
            }
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

        private final Consumer<ThreadLocalAccess> action;
        private final CountDownLatch doneLatch;

        AsyncEvent(PolyglotContextImpl context, Consumer<ThreadLocalAccess> action, CountDownLatch doneLatch) {
            super(context);
            this.action = action;
            this.doneLatch = doneLatch;
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            try {
                action.accept(access);
            } finally {
                doneLatch.countDown();
            }
        }
    }

    private static final class SafepointEvent extends AbstractTLHandshake {

        private final Consumer<ThreadLocalAccess> action;
        private final CountDownLatch awaitLatch;
        private final CountDownLatch doneLatch;

        SafepointEvent(PolyglotContextImpl context, Thread[] threads, Consumer<ThreadLocalAccess> action, CountDownLatch doneLatch) {
            super(context);
            this.action = action;
            this.awaitLatch = new CountDownLatch(threads.length);
            this.doneLatch = doneLatch;
        }

        @Override
        protected void acceptImpl(PolyglotTLAccess access) {
            PolyglotThreadInfo thread;
            synchronized (context) {
                thread = context.getCurrentThreadInfo();
            }
            if (thread.isSafepointActive()) {
                throw new AssertionError("Recursive synchronous safepoint detected.");
            }
            awaitLatch.countDown();

            try {
                awaitLatch.await();
            } catch (InterruptedException e1) {
            }

            thread.setSafepointActive(true);
            Throwable currentEx = null;
            try {
                action.accept(access);
            } catch (Throwable t) {
                currentEx = t;
            } finally {
                thread.setSafepointActive(false);
            }
            doneLatch.countDown();

            while (true) {
                try {
                    doneLatch.await();
                    break;
                } catch (InterruptedException e1) {
                }
            }
            if (currentEx != null) {
                sneakyThrow(currentEx);
            }
        }
    }

}
