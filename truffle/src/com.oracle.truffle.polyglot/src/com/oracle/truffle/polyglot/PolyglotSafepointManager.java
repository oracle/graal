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

import com.oracle.truffle.api.impl.ThreadLocalHandshake;

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
                    Thread[] threads, Consumer<Thread> action, boolean async) {

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

        TL_HANDSHAKE.poll();
        return new ThreadLocalFuture(doneLatch);
    }

    /*
     * We only allow carefully white-listed exceptions to be thrown from thread local handshakes.
     * They might be dangerous as certain exception handles can be skipped. We therefore only allow
     * cancellation exceptions to be thrown which guarantee that the context is immediately closed.
     *
     * This is different to the deprecated Thread.stop() as we can guarantee that no values from
     * this context can be used from now on.
     */
    private static boolean isAllowedException(Throwable t) {
        return true;
// return t instanceof CancelExecution;
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

    private abstract static class AbstractTLHandshake implements Runnable {

        protected final PolyglotContextImpl context;

        AbstractTLHandshake(PolyglotContextImpl context) {
            this.context = context;
        }

        @Override
        public void run() {
            Object prev = context.engine.enterIfNeeded(context);
            try {
                runImpl();
            } finally {
                context.engine.leaveIfNeeded(prev, context);
            }
        }

        protected abstract void runImpl();
    }

    private static final class AsyncEvent extends AbstractTLHandshake {

        private final Consumer<Thread> action;
        private final CountDownLatch doneLatch;

        AsyncEvent(PolyglotContextImpl context, Consumer<Thread> action, CountDownLatch doneLatch) {
            super(context);
            this.action = action;
            this.doneLatch = doneLatch;
        }

        @Override
        protected void runImpl() {
            try {
                action.accept(Thread.currentThread());
            } finally {
                doneLatch.countDown();
            }
        }
    }

    private static final class SafepointEvent extends AbstractTLHandshake {

        private final Consumer<Thread> action;
        private final CountDownLatch awaitLatch;
        private final CountDownLatch doneLatch;

        SafepointEvent(PolyglotContextImpl context, Thread[] threads, Consumer<Thread> action, CountDownLatch doneLatch) {
            super(context);
            this.action = action;
            this.awaitLatch = new CountDownLatch(threads.length);
            this.doneLatch = doneLatch;
        }

        @Override
        protected void runImpl() {
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
                action.accept(Thread.currentThread());
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
