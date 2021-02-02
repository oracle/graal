/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAccess;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interruptable;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class TruffleSafepointTest extends AbstractPolyglotTest {

    private static final int[] THREAD_CONFIGS = new int[]{1, 4, 16};
    private static final int[] ITERATION_CONFIGS = new int[]{1, 8, 32};
    private static ExecutorService service;
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();

    private static final int TIMEOUT_SECONDS = 60;

    @BeforeClass
    public static void beforeClass() {
        service = Executors.newFixedThreadPool(Integer.MAX_VALUE);
        CANCELLED.set(false);
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        if (service != null) {
            CANCELLED.set(true);
            service.shutdown();
            if (!service.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw failTimeout(null);
            }
        }
    }

    @Before
    public void before() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        enterContext = false;
    }

    @After
    public void after() {
        ProxyLanguage.setDelegate(new ProxyLanguage());
    }

    @Test
    public void testNullArgs() {
        setupEnv();

        assertFails(() -> languageEnv.runThreadLocalSynchronous(new Thread[]{null}, (t) -> {
        }), NullPointerException.class);
        assertFails(() -> languageEnv.runThreadLocalAsynchronous(new Thread[]{null}, (t) -> {
        }), NullPointerException.class);

        assertFails(() -> languageEnv.runThreadLocalSynchronous(null, null), NullPointerException.class);
        assertFails(() -> languageEnv.runThreadLocalAsynchronous(null, null), NullPointerException.class);
    }

    @Test
    public void testSynchronousRecursiveError() throws InterruptedException, AssertionError, ExecutionException {
        AtomicBoolean stopped = new AtomicBoolean();
        TestSetup<Object> setup = setupSafepointLoop(1, (node) -> {
            TruffleSafepoint.poll(node);
            return stopped.get();
        });
        Future<Object> future = setup.futures.get(0);

        AtomicBoolean error = new AtomicBoolean();
        Consumer<ThreadLocalAccess> sync = new Consumer<ThreadLocalAccess>() {
            @Override
            public void accept(ThreadLocalAccess outer) {
                /*
                 * Synchronous inner safe point scheduling is disallowed as this is prone for
                 * deadlocks.
                 */
                try {
                    setup.env.runThreadLocalSynchronous(null, (inner) -> {
                    });
                    fail();
                } catch (AssertionError e) {
                    assertEquals("Recursive synchronous safepoint detected.", e.getMessage());
                    error.set(true);
                }
            }
        };
        setup.env.runThreadLocalSynchronous(null, sync);
        stopped.set(true);
        future.get();
        assertTrue(error.get());
    }

    @Test
    public void testSynchronous() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean();

            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint.poll(node);
                return stopped.get();
            });

            List<Future<Object>> futures = setup.futures;

            ActionCollector[] collectors = new ActionCollector[events];
            AtomicInteger eventCounter = new AtomicInteger();
            for (int i = 0; i < collectors.length; i++) {
                collectors[i] = new ActionCollector(eventCounter);
            }

            for (int i = 0; i < events; i++) {
                setup.env.runThreadLocalSynchronous(null, collectors[i]);
            }

            stopped.set(true);

            for (int i = 0; i < collectors.length; i++) {
                ActionCollector runnable = collectors[i];
                awaitFutures(futures);

                // verify that events were happening in the right order#
                assertEquals(threads, runnable.ids.size());

                for (int concurrentId : runnable.ids) {
                    int priorEvents = threads * i;
                    int doneBy = priorEvents + threads;

                    assertTrue(concurrentId + ">=" + priorEvents, concurrentId >= priorEvents);
                    assertTrue(concurrentId + "<=" + doneBy, concurrentId <= doneBy);
                }

                assertEquals(threads, runnable.actions.size());

                // verify that every thread is seen exactly once
                Set<Thread> seenThreads = new HashSet<>();
                for (Thread t : runnable.actions) {
                    if (seenThreads.contains(t)) {
                        throw new AssertionError("Did not expect to see thread twice.");
                    }
                    seenThreads.add(t);
                }
            }
        });
    }

    @Test
    public void testAsynchronous() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean();

            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint.poll(node);
                return stopped.get();
            });

            List<Future<Object>> futures = setup.futures;

            AtomicInteger eventCounter = new AtomicInteger();
            ActionCollector runnable = new ActionCollector(eventCounter);

            for (int i = 0; i < events; i++) {
                setup.env.runThreadLocalSynchronous(null, runnable);
            }
            stopped.set(true);
            awaitFutures(futures);
            // events can happen in any order
            assertActionsAnyOrder(threads, events, runnable);
        });
    }

    private static void assertActionsAnyOrder(int threads, int events, ActionCollector runnable) {
        assertEquals(threads * events, runnable.ids.size());
        for (int concurrentId : runnable.ids) {
            int priorEvents = 0;
            int doneBy = events * threads;

            assertTrue(concurrentId + ">=" + priorEvents, concurrentId >= priorEvents);
            assertTrue(concurrentId + "<=" + doneBy, concurrentId <= doneBy);
        }

        assertEquals(threads * events, runnable.actions.size());

        // verify that every thread is seen events time
        Map<Thread, Integer> seenThreads = new HashMap<>();
        for (Thread t : runnable.actions) {
            seenThreads.compute(t, (k, p) -> {
                if (p == null) {
                    return 1;
                } else {
                    return p + 1;
                }
            });
        }
        for (Entry<Thread, Integer> counters : seenThreads.entrySet()) {
            assertEquals(counters.getKey().toString(), (int) counters.getValue(), events);
        }
    }

    @Test
    public void testDisabled() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);
            AtomicBoolean disabled = new AtomicBoolean(true);

            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint config = TruffleSafepoint.getCurrent();
                boolean prev = config.setEnabled(false);
                try {
                    while (true) {
                        TruffleSafepoint.poll(node);
                        if (isStopped(stopped)) {
                            return true;
                        }
                    }
                } finally {
                    config.setEnabled(prev);
                }
            });

            List<Future<Object>> futures = setup.futures;

            AtomicInteger eventCounter = new AtomicInteger();
            ActionCollector runnable = new ActionCollector(eventCounter);
            for (int i = 0; i < events; i++) {
                if (i % 2 == 0) {
                    setup.env.runThreadLocalSynchronous(null, runnable);
                } else {
                    setup.env.runThreadLocalAsynchronous(null, runnable);
                }
            }
            assertEquals(0, eventCounter.get());

            disabled.set(false);
            stopped.set(true);

            awaitFutures(futures);
            assertActionsAnyOrder(threads, events, runnable);
        });
    }

    @TruffleBoundary
    private static boolean isStopped(AtomicBoolean stopped) {
        return stopped.get();
    }

    @Test
    public void testStackTrace() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);

            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint.poll(node);
                return stopped.get();
            });

            List<Future<Object>> futures = setup.futures;
            AtomicInteger eventCounter = new AtomicInteger();
            List<List<List<TruffleStackTraceElement>>> lists = new ArrayList<>();
            for (int i = 0; i < events; i++) {
                List<List<TruffleStackTraceElement>> stackTraces = new ArrayList<>();
                setup.env.runThreadLocalSynchronous(null, (access) -> {
                    RuntimeException e = new RuntimeException();
                    TruffleStackTrace.fillIn(e);
                    synchronized (stackTraces) {
                        stackTraces.add(TruffleStackTrace.getStackTrace(e));
                    }
                });
                lists.add(stackTraces);
            }
            assertEquals(0, eventCounter.get());
            stopped.set(true);
            awaitFutures(futures);

            for (List<List<TruffleStackTraceElement>> eventList : lists) {
                assertEquals(threads, eventList.size());
                for (List<TruffleStackTraceElement> list : eventList) {
                    assertEquals(1, list.size());
                    for (TruffleStackTraceElement element : list) {
                        assertSame(setup.target, element.getTarget());
                    }
                }
            }
        });
    }

    @Test
    public void testException() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);

            AtomicReference<CountDownLatch> latchRef = new AtomicReference<>(null);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint.poll(node);
                return stopped.get();
            }, (e) -> {
                exceptions.add(e);
                latchRef.get().countDown();
            });

            List<Future<Object>> futures = setup.futures;
            for (int i = 0; i < events; i++) {
                CountDownLatch latch = new CountDownLatch(threads);
                latchRef.set(latch);

                String testId = "test " + i + " of " + events;

                setup.env.runThreadLocalSynchronous(null, (access) -> {
                    // the only exception that is allowed to be thrown and not just logged
                    throw new RuntimeException(testId);
                });

                try {
                    if (!latch.await(10, TimeUnit.SECONDS)) {
                        throw failTimeout(null);
                    }
                } catch (InterruptedException e) {
                    fail();
                }
                assertEquals(threads, exceptions.size());
                for (Throwable t : exceptions) {
                    assertTrue(t instanceof RuntimeException);
                    assertEquals(testId, t.getMessage());
                }
                exceptions.clear();
            }
            stopped.set(true);
            awaitFutures(futures);
        });
    }

    @Test
    public void testInterrupting() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);
            Semaphore semaphore = new Semaphore(threads);
            TestSetup<Object> setup = setupSafepointLoop(threads, (node) -> {
                TruffleSafepoint config = TruffleSafepoint.getCurrent();
                Interruptable prev = config.setBlocked(TruffleSafepoint.THREAD_INTERRUPTION);
                try {
                    lockCooperatively(node, semaphore);
                } finally {
                    config.setBlocked(prev);
                }
                assert !Thread.interrupted() : "invalid trailing interrupted state";
                return stopped.get();
            });

            try {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(eventCounter);
                List<Future<?>> threadLocals = new ArrayList<>();

                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.runThreadLocalAsynchronous(null, runnable));

                    // after half of the events we let them run into the semaphore
                    // this encourages race conditions between setting blocked and unlocking
                    if (i == Math.floorDiv(events, 2)) {
                        semaphore.acquireUninterruptibly(threads);
                    }
                }

                // wait for all events to complete so we can reliably assert the events
                for (Future<?> f : threadLocals) {
                    try {
                        f.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new AssertionError(e);
                    }
                }

                // events can happen in any order
                assertActionsAnyOrder(threads, events, runnable);
                semaphore.release(threads);

            } finally {
                // let the threads complete in an orderly fashing
                stopped.set(true);
                awaitFutures(setup.futures);
            }
        });
    }

    @TruffleBoundary
    private static void lockCooperatively(Node node, Semaphore semaphore) {
        while (true) {
            try {
                semaphore.acquire();
                break;
            } catch (InterruptedException e) {
                TruffleSafepoint.poll(node);
                continue;
            }
        }
        semaphore.release();
    }

    @FunctionalInterface
    interface TestRunner {

        void run(int threads, int events);

    }

    void forEachConfig(TestRunner run) {
        // synchronous execution of all configs
        for (int threadConfig = 0; threadConfig < THREAD_CONFIGS.length; threadConfig++) {
            int threads = THREAD_CONFIGS[threadConfig];
            for (int iterationConfig = 0; iterationConfig < ITERATION_CONFIGS.length; iterationConfig++) {
                int events = ITERATION_CONFIGS[iterationConfig];
                try {
                    run.run(threads, events);
                } catch (Throwable e) {
                    throw new AssertionError("Test config threads " + threads + " events " + events + " failed.", e);
                }
            }
        }

        // asynchronous execution of all configs
        List<Future<?>> futures = new ArrayList<>();
        for (int threadConfig = 0; threadConfig < THREAD_CONFIGS.length; threadConfig++) {
            int threads = THREAD_CONFIGS[threadConfig];
            for (int iterationConfig = 0; iterationConfig < ITERATION_CONFIGS.length; iterationConfig++) {
                int events = ITERATION_CONFIGS[iterationConfig];
                try {
                    if (futures.size() >= 64) {
                        for (Future<?> future : futures) {
                            waitOrFail(future);
                        }
                        futures.clear();
                    }
                    futures.add(service.submit(() -> run.run(threads, events)));
                } catch (AssertionError e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }
        for (Future<?> future : futures) {
            waitOrFail(future);
        }
    }

    private static void waitOrFail(Future<?> future) throws AssertionError {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        } catch (TimeoutException e) {
            throw failTimeout(e);
        }
    }

    private static RuntimeException failTimeout(TimeoutException e) throws AssertionError {
        System.out.println("Timeout detected. Printing all threads: ");
        for (Entry<Thread, StackTraceElement[]> elements : Thread.getAllStackTraces().entrySet()) {
            Exception ex = new Exception(elements.getKey().toString());
            ex.setStackTrace(elements.setValue(elements.getValue()));
            ex.printStackTrace();
        }
        throw new AssertionError("Timed out waiting for threads", e);
    }

    private <T> TestSetup<T> setupSafepointLoop(int threads, NodeCallable callable) {
        return setupSafepointLoop(threads, callable, null);
    }

    @SuppressWarnings("unchecked")
    private <T> TestSetup<T> setupSafepointLoop(int threads, NodeCallable callable, Consumer<Throwable> exHandler) {
        Env env = createTestContext();
        CountDownLatch latch = new CountDownLatch(threads);
        CallTarget target = createSafepointLoopTarget(env, callable, latch);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(service.submit(() -> {
                Object prev = env.getContext().enter(null);
                try {
                    while (true) {
                        try {
                            return (T) target.call(latch);
                        } catch (Throwable t) {
                            if (exHandler != null) {
                                exHandler.accept(t);
                            } else {
                                throw t;
                            }
                        }
                    }
                } finally {
                    env.getContext().leave(null, prev);
                }
            }));
        }
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                for (Future<T> future : futures) {
                    if (future.isDone()) {
                        try {
                            future.get();
                        } catch (ExecutionException e) {
                            throw new AssertionError(e.getCause());
                        }
                    }
                }
                throw new AssertionError();
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return new TestSetup<>(env, futures, target);
    }

    @FunctionalInterface
    interface NodeCallable {

        boolean call(Node node);

    }

    private static CallTarget createSafepointLoopTarget(Env env, NodeCallable callable, CountDownLatch latch) {
        Object targetEnter = env.getContext().enter(null);
        CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @SuppressWarnings("unchecked")
            @Override
            public Object execute(VirtualFrame frame) {
                waitForLatch(latch);
                while (true) {
                    if (isCancelled()) {
                        return null;
                    }
                    Boolean result;
                    try {
                        result = callable.call(this);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    if (result) {
                        return result;
                    }
                }
            }

            @TruffleBoundary
            private boolean isCancelled() {
                return CANCELLED.get();
            }
        });
        env.getContext().leave(null, targetEnter);
        return target;
    }

    static class TestSetup<T> {

        final Env env;
        final List<Future<T>> futures;
        final CallTarget target;

        TestSetup(Env env, List<Future<T>> futures, CallTarget target) {
            this.env = env;
            this.futures = futures;
            this.target = target;
        }
    }

    protected Env createTestContext() {
        Context.Builder b = Context.newBuilder();
        b.allowExperimentalOptions(true);
        if (Truffle.getRuntime().getName().contains("Graal")) {
            b.option("engine.CompileImmediately", "true");
            b.option("engine.BackgroundCompilation", "false");
        }
        Context c = b.build();
        c.enter();
        c.initialize(ProxyLanguage.ID);
        Env env = ProxyLanguage.getCurrentContext().getEnv();
        c.leave();
        return env;
    }

    @TruffleBoundary
    private static void waitForLatch(CountDownLatch latch) throws AssertionError {
        latch.countDown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static void awaitFutures(List<Future<Object>> futures) {
        for (Future<?> future : futures) {
            try {
                Object result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertEquals(Boolean.TRUE, result);
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            } catch (InterruptedException e) {
                throw new AssertionError(2);
            } catch (TimeoutException e) {
                throw failTimeout(e);
            }
        }
    }

    @SuppressWarnings("serial")
    static class SafepointPerformed extends RuntimeException {
    }

    static class ActionCollector implements Consumer<ThreadLocalAccess> {
        final List<Thread> actions = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> ids = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger counter;

        ActionCollector() {
            this.counter = new AtomicInteger();
        }

        ActionCollector(AtomicInteger counter) {
            this.counter = counter;
        }

        public void accept(ThreadLocalAccess t) {
            assertSame(Thread.currentThread(), t.getThread());
            actions.add(t.getThread());
            ids.add(counter.incrementAndGet());
        }
    }

}
