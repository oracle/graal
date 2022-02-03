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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class TruffleSafepointTest {

    private static final Method SUBMIT_INTERNAL = ReflectionUtils.requireDeclaredMethod(TruffleLanguage.Env.class, "submitThreadLocalInternal", null);

    private static final int[] THREAD_CONFIGS = new int[]{1, 4, 16};
    private static final int[] ITERATION_CONFIGS = new int[]{1, 8, 32};
    private static ExecutorService service;
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();

    private static final int TIMEOUT_SECONDS = 300;
    private static final boolean VERBOSE = false;
    /*
     * Rerun all thread configurations asynchronously. This flag is intended to be used for
     * debugging failures in this class.
     */
    private static final boolean RERUN_THREAD_CONFIG_ASYNC = true;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void beforeClass() {
        service = Executors.newCachedThreadPool();
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

    private long testStarted;

    @Before
    public void before() throws ExecutionException, InterruptedException {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        boolean handshakesSupported = true;
        Future<Void> future = null;
        try (Context c = createTestContext()) {
            c.enter();
            try {
                c.initialize(ProxyLanguage.ID);
                Env env = LanguageContext.get(null).getEnv();
                try {
                    future = env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                        @Override
                        protected void perform(Access access) {

                        }
                    });
                } catch (UnsupportedOperationException e) {
                    if ("Thread local handshakes are not supported on this platform. A possible reason may be that the underlying JVMCI version is too old.".equals(e.getMessage())) {
                        handshakesSupported = false;
                    } else {
                        throw e;
                    }
                }
            } finally {
                c.leave();
            }
        }

        if (future != null) {
            future.get();
        }

        Assume.assumeTrue(handshakesSupported);

        if (VERBOSE) {
            System.out.println();
            System.out.print(name.getMethodName() + ":");
            testStarted = System.currentTimeMillis();
        }
    }

    @After
    public void after() {
        ProxyLanguage.setDelegate(new ProxyLanguage());
        if (VERBOSE) {
            System.out.println((System.currentTimeMillis() - testStarted) + "ms");
        }
    }

    /*
     * Non public version that can conifgure whether to enter.
     */
    private static Future<?> submitThreadLocalInternal(Env env, Thread[] threads, ThreadLocalAction action, boolean needEnter) {
        try {
            return (Future<?>) SUBMIT_INTERNAL.invoke(env, threads, action, needEnter);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEnterSlowPathFallback() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            for (int itNo = 0; itNo < 1000; itNo++) {
                CountDownLatch enterLeaveLoopLatch = new CountDownLatch(1);
                AtomicReference<Env> envAtomicReference = new AtomicReference<>();
                AtomicReference<Context> contextAtomicReference = new AtomicReference<>();
                Future<?> testFuture = executorService.submit(() -> {
                    /*
                     * The context is closed in main thread. Closing it here in try-with-resources
                     * block would only make the test more complex.
                     */
                    Context c = createTestContext();
                    contextAtomicReference.set(c);
                    c.initialize(ProxyLanguage.ID);
                    c.enter();
                    try {
                        envAtomicReference.set(LanguageContext.get(null).getEnv());
                    } finally {
                        c.leave();
                    }
                    enterLeaveLoopLatch.countDown();
                    TruffleContext truffleContext = envAtomicReference.get().getContext();
                    try {
                        for (int i = 0; i < 100000; i++) {
                            Object prev = truffleContext.enter(INVALID_NODE);
                            truffleContext.leave(INVALID_NODE, prev);
                        }
                    } catch (Throwable t) {
                        if (!"Context execution was cancelled.".equals(t.getMessage())) {
                            throw t;
                        }
                    }
                });
                enterLeaveLoopLatch.await();
                Context c = contextAtomicReference.get();
                Env env = envAtomicReference.get();
                TruffleContext truffleContext = env.getContext();
                /*
                 * The goal of this test is to check whether the slowpath fallback in thread enter
                 * guarantees that the thread local action is either polled when the context is
                 * entered, or the thread is deactivated.
                 */
                AtomicBoolean threadLocalActionPerformedWhenContextWasInactive = new AtomicBoolean();
                Future<?> future = submitThreadLocalInternal(env, null, new ThreadLocalAction(false, false) {
                    @Override
                    protected void perform(Access access) {
                        if (!truffleContext.isActive()) {
                            threadLocalActionPerformedWhenContextWasInactive.set(true);
                        }
                    }
                }, false);
                c.close(true);
                future.get();
                testFuture.get();
                assertFalse("Action was performed when inactive in iteration " + itNo, threadLocalActionPerformedWhenContextWasInactive.get());
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testNullArgs() {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            sleepNanosBoundary(50000);
            TruffleSafepoint.poll(node);
            return false;
        })) {
            assertFails(() -> setup.env.submitThreadLocal(new Thread[]{null}, new ThreadLocalAction(true, true) {
                @Override
                protected void perform(Access access) {
                }
            }), NullPointerException.class);
            assertFails(() -> setup.env.submitThreadLocal(null, null), NullPointerException.class);
        }
    }

    @Test
    public void testSynchronousRecursiveError() throws InterruptedException, AssertionError, ExecutionException {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            sleepNanosBoundary(50000);
            TruffleSafepoint.poll(node);
            return false;
        })) {
            Future<Boolean> future = setup.futures.get(0);
            AtomicBoolean error = new AtomicBoolean();
            setup.env.submitThreadLocal(null, new ThreadLocalAction(true, true) {
                @Override
                protected void perform(Access outer) {
                    ThreadLocalAction action = new ThreadLocalAction(true, true) {
                        @Override
                        protected void perform(Access inner) {
                        }
                    };
                    try {
                        setup.env.submitThreadLocal(null, action);
                        fail();
                    } catch (IllegalStateException e) {
                        /*
                         * Synchronous inner safe point scheduling is disallowed as this is prone
                         * for deadlocks.
                         */
                        assertTrue(e.getMessage(), e.getMessage().startsWith("Recursive synchronous thread local action detected."));
                        error.set(true);
                    }
                }
            });
            setup.stopped.set(true);
            future.get();
            assertTrue(isStopped(error));
        }
    }

    @Test
    public void testSynchronous() {
        forEachConfig((threads, events) -> {
            TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            });

            ActionCollector[] collectors = new ActionCollector[events];
            AtomicInteger eventCounter = new AtomicInteger();
            for (int i = 0; i < collectors.length; i++) {
                collectors[i] = new ActionCollector(setup, eventCounter, true, true);
            }

            for (int i = 0; i < events; i++) {
                setup.env.submitThreadLocal(null, collectors[i]);
            }

            setup.stopAndAwait();

            for (int i = 0; i < collectors.length; i++) {
                ActionCollector runnable = collectors[i];

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
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);

                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, runnable);
                }
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
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
    public void testSideEffecting() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);
            AtomicBoolean allowSideEffects = new AtomicBoolean(true);

            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint config = TruffleSafepoint.getCurrent();
                boolean prev = config.setAllowSideEffects(false);
                try {
                    while (true) {
                        TruffleSafepoint.poll(node);
                        if (isStopped(stopped)) {
                            return true;
                        }
                    }
                } finally {
                    config.setAllowSideEffects(prev);
                }
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, runnable);
                }
                assertEquals(0, eventCounter.get());

                allowSideEffects.set(false);
                stopped.set(true);
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
        });
    }

    @Test
    public void testHasPendingSideEffectingActions() {
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch waitSideEffectsDisabled = new CountDownLatch(1);
        CountDownLatch waitSubmitted = new CountDownLatch(1);

        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            testHasPendingSideEffectingActionsBoundary(thread, waitSideEffectsDisabled, waitSubmitted, node);
            return true;
        })) {
            waitSideEffectsDisabled.await();
            setup.env.submitThreadLocal(new Thread[]{thread.get()}, new ThreadLocalAction(true, false) {
                @Override
                protected void perform(Access access) {
                    throw new RuntimeException("interrupt");
                }
            });
            waitSubmitted.countDown();

            setup.stopAndAwait();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @TruffleBoundary
    private static void testHasPendingSideEffectingActionsBoundary(AtomicReference<Thread> thread, CountDownLatch waitSideEffectsDisabled, CountDownLatch waitSubmitted, TestRootNode node)
                    throws AssertionError {
        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        assertFalse(safepoint.hasPendingSideEffectingActions());

        boolean prev = safepoint.setAllowSideEffects(false);
        try {
            thread.set(Thread.currentThread());
            waitSideEffectsDisabled.countDown();
            try {
                waitSubmitted.await();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            assertTrue(safepoint.hasPendingSideEffectingActions());
        } finally {
            safepoint.setAllowSideEffects(prev);
        }

        assertFalse("always false when side effects enabled", safepoint.hasPendingSideEffectingActions());

        try {
            TruffleSafepoint.pollHere(node);
            fail();
        } catch (RuntimeException e) {
            assertEquals("interrupt", e.getMessage());
            assertFalse(safepoint.hasPendingSideEffectingActions());
        }
    }

    @TruffleBoundary
    private static boolean isStopped(AtomicBoolean stopped) {
        return stopped.get();
    }

    @Test
    public void testStackTrace() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {

                AtomicInteger eventCounter = new AtomicInteger();
                List<List<List<TruffleStackTraceElement>>> lists = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    List<List<TruffleStackTraceElement>> stackTraces = new ArrayList<>();
                    setup.env.submitThreadLocal(null,
                                    new ThreadLocalAction(true, true) {
                                        @Override
                                        protected void perform(Access access) {
                                            RuntimeException e = new RuntimeException();
                                            TruffleStackTrace.fillIn(e);
                                            synchronized (stackTraces) {
                                                stackTraces.add(TruffleStackTrace.getStackTrace(e));
                                            }
                                        }
                                    });
                    lists.add(stackTraces);
                }
                assertEquals(0, eventCounter.get());
                setup.stopAndAwait();

                for (List<List<TruffleStackTraceElement>> eventList : lists) {
                    assertEquals(threads, eventList.size());
                    for (List<TruffleStackTraceElement> list : eventList) {
                        assertEquals(1, list.size());
                        for (TruffleStackTraceElement element : list) {
                            assertSame(setup.target, element.getTarget());
                        }
                    }
                }
            }
        });
    }

    @TruffleBoundary
    private static void sleepNanosBoundary(int nanos) {
        try {
            Thread.sleep(0, nanos);
        } catch (InterruptedException ie) {
        }
    }

    @Test
    public void testException() {
        forEachConfig((threads, events) -> {

            AtomicReference<CountDownLatch> latchRef = new AtomicReference<>(null);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            }, (e) -> {
                exceptions.add(e);
                latchRef.get().countDown();
            })) {

                for (int i = 0; i < events; i++) {
                    CountDownLatch latch = new CountDownLatch(threads);
                    latchRef.set(latch);

                    String testId = "test " + i + " of " + events;
                    AtomicInteger counter = new AtomicInteger();
                    Future<?> await = setup.env.submitThreadLocal(null,
                                    new ThreadLocalAction(true, false) {

                                        @Override
                                        protected void perform(Access access) {
                                            counter.incrementAndGet();
                                            throw new RuntimeException(testId);
                                        }
                                    });
                    // wait until all tasks on all threads are done
                    waitOrFail(await);

                    // make sure we thrown the exception for all events
                    assertEquals(threads, counter.get());

                    try {
                        // wait until all exceptions are reported
                        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
            }
        });
    }

    private static final Node INVALID_NODE = new Node() {
    };

    /*
     * This test case is inspired by the ruby use case of disabling side-effects and block to wake
     * up to allow side-effecting effects a forced location.
     */
    @Test
    public void testBlockedAndSafepoints() {
        forEachConfig((threads, events) -> {
            Semaphore semaphore = new Semaphore(threads);
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                lockCooperativelySafepoint(semaphore, node, safepoint);
                return false;
            })) {
                try {
                    AtomicInteger eventCounter = new AtomicInteger();
                    List<Future<?>> threadLocals = new ArrayList<>();

                    for (int i = 0; i < events; i++) {
                        threadLocals.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(Access access) {
                                assertNotSame(INVALID_NODE, access.getLocation());
                                assertSame(setup.target.getRootNode(), access.getLocation());
                                eventCounter.incrementAndGet();
                            }
                        }));

                        // after half of the events we let them run into the semaphore
                        // this encourages race conditions between setting blocked and unlocking
                        if (i == Math.floorDiv(events, 2)) {
                            for (int j = 0; j < threads; j++) {
                                acquire(semaphore);
                            }
                        }
                    }

                    // wait for all events to complete so we can reliably assert the events
                    for (Future<?> f : threadLocals) {
                        waitOrFail(f);
                    }

                    assertEquals(events * threads, eventCounter.get());

                } finally {
                    // let the threads complete in an orderly fashing
                    // events can happen in any order
                    semaphore.release(threads);
                }
            }
        });
    }

    private static void lockCooperativelySafepoint(Semaphore semaphore, Node node, TruffleSafepoint safepoint) {
        boolean prevEffects = safepoint.setAllowSideEffects(false);
        try {
            TruffleSafepoint.getCurrent().setBlocked(node, Interrupter.THREAD_INTERRUPT,
                            (s) -> {
                                // we want to get woken up by side-effecting actions
                                boolean prevInner = safepoint.setAllowSideEffects(true);
                                try {
                                    s.acquire();
                                } finally {
                                    safepoint.setAllowSideEffects(prevInner);
                                }
                            }, semaphore, () -> {
                                boolean condDisabled = safepoint.setAllowSideEffects(true);
                                try {
                                    // All side-effecting events are forced to happen here.
                                    TruffleSafepoint.pollHere(node);
                                } finally {
                                    safepoint.setAllowSideEffects(condDisabled);
                                }
                            }, () -> {
                                boolean condDisabled = safepoint.setAllowSideEffects(true);
                                try {
                                    // All side-effecting events are forced to happen here.
                                    TruffleSafepoint.pollHere(node);
                                } finally {
                                    safepoint.setAllowSideEffects(condDisabled);
                                }
                            });
            releaseSemaphore(semaphore);
        } finally {
            safepoint.setAllowSideEffects(prevEffects);
        }
    }

    @Test
    public void testConditionAndSafepoints() {
        forEachConfig((threads, events) -> {
            ReentrantLock lock = new ReentrantLock();
            Condition condition = lock.newCondition();
            AtomicBoolean done = new AtomicBoolean(false);
            AtomicInteger inAwait = new AtomicInteger(0);

            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                // No lockInterruptibly()/setBlocked() here, `lock` is never held during a poll()
                // It can also be required by language semantics if only the await() should be
                // interruptible and not the lock().
                lockBoundary(lock);
                try {
                    while (!done.get()) {
                        safepoint.setBlocked(node, Interrupter.THREAD_INTERRUPT,
                                        (c) -> {
                                            // When await() is interrupted, it still needs to
                                            // reacquire the lock before the InterruptedException
                                            // can propagate. So we must unlock once we're out to
                                            // let other threads reach the safepoint too.
                                            inAwait.incrementAndGet();
                                            try {
                                                c.await();
                                            } finally {
                                                inAwait.decrementAndGet();
                                            }
                                        }, condition, lock::unlock, lock::lock);
                    }
                } finally {
                    unlockBoundary(lock);
                }
                return true; // only run once
            })) {
                AtomicInteger eventCounter = new AtomicInteger();

                // Wait all threads are inside await()
                while (inAwait.get() < threads || lock.isLocked()) {
                    Thread.yield();
                }

                List<Future<?>> threadLocals = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(false, true) {
                        @Override
                        protected void perform(Access access) {
                            Assert.assertFalse(lock.isHeldByCurrentThread());
                            eventCounter.incrementAndGet();
                        }
                    }));
                }

                // wait for all events to complete so we can reliably assert the events
                for (Future<?> f : threadLocals) {
                    waitOrFail(f);
                }

                assertEquals(events * threads, eventCounter.get());

                // Wait all threads are in condition.await(), otherwise signalAll() doesn't work
                while (inAwait.get() < threads || lock.isLocked()) {
                    Thread.yield();
                }

                lock.lock();
                try {
                    done.set(true);
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    @TruffleBoundary
    private static void unlockBoundary(ReentrantLock lock) {
        lock.unlock();
    }

    @TruffleBoundary
    private static void lockBoundary(ReentrantLock lock) {
        lock.lock();
    }

    @Test
    public void testBlocked() {
        forEachConfig((threads, events) -> {
            Semaphore semaphore = new Semaphore(threads);
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.setBlockedThreadInterruptible(node, Semaphore::acquire, semaphore);
                releaseSemaphore(semaphore);
                assert !Thread.interrupted() : "invalid trailing interrupted state";
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                List<Future<?>> threadLocals = new ArrayList<>();

                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.submitThreadLocal(null, runnable));

                    if (i == Math.floorDiv(events, 2)) {
                        // after half of the events we let them run into the semaphore
                        // this encourages contention conditions between setting blocked and
                        // unlocking
                        for (int j = 0; j < threads; j++) {
                            acquire(semaphore);
                        }
                    }
                }

                // wait for all events to complete so we can reliably assert the events
                for (Future<?> f : threadLocals) {
                    waitOrFail(f);
                }

                // events can happen in any order
                assertActionsAnyOrder(threads, events, runnable);
                semaphore.release(threads);
            }
        });
    }

    @TruffleBoundary
    private static void releaseSemaphore(Semaphore semaphore) {
        semaphore.release();
    }

    private static Semaphore[] createSemaphores(int count, int permits) {
        Semaphore[] semaphores = new Semaphore[count];
        for (int i = 0; i < semaphores.length; i++) {
            semaphores[i] = new Semaphore(permits);
        }
        return semaphores;
    }

    private static AtomicBoolean[] createBooleans(int count) {
        AtomicBoolean[] b = new AtomicBoolean[count];
        for (int i = 0; i < b.length; i++) {
            b[i] = new AtomicBoolean();
        }
        return b;
    }

    @Test
    public void testRecursiveBlockingProcessingSubmitAllBeforePoll() throws InterruptedException {
        /*
         * Specfies the number of recursive blocking actions.
         */
        int[] tests = new int[]{1, 2, 3, 7, 16, 128, 256};
        for (int blockingActions : tests) {
            Semaphore waitForSafepoint = new Semaphore(0);
            Semaphore[] leaveBlocked = createSemaphores(blockingActions, 0);

            try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
                acquire(waitForSafepoint);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicBoolean performed = new AtomicBoolean();
                AtomicBoolean[] inBlockingAction = createBooleans(blockingActions);
                Semaphore[] awaitBlocked = createSemaphores(blockingActions, 0);
                Future<?>[] blockingFutures = new Future<?>[blockingActions];

                for (int i = 0; i < blockingActions; i++) {
                    final int actionIndex = i;
                    blockingFutures[actionIndex] = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                        @Override
                        protected void perform(Access access) {
                            if (actionIndex > 0) {
                                assertTrue(inBlockingAction[actionIndex - 1].get());
                            }
                            inBlockingAction[actionIndex].set(true);
                            try {
                                awaitBlocked[actionIndex].release();
                                TruffleSafepoint.setBlockedThreadInterruptible(access.getLocation(), (e) -> {
                                    leaveBlocked[actionIndex].acquire();
                                }, null);
                            } finally {
                                inBlockingAction[actionIndex].set(false);
                            }
                        }
                    });
                }

                Future<?> f = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(Access innerAccess) {
                        assertTrue(inBlockingAction[blockingActions - 1].get());
                        if (performed.get()) {
                            throw new AssertionError("already performed");
                        }
                        performed.set(true);
                    }
                });

                // start processing safepoints now
                waitForSafepoint.release(Integer.MAX_VALUE);

                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    awaitBlocked[actionIndex].acquire();
                }
                waitOrFail(f);
                assertTrue(performed.get());
                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    leaveBlocked[actionIndex].release();
                }
                for (Future<?> blockingFuture : blockingFutures) {
                    waitOrFail(blockingFuture);
                }
            }
        }
    }

    @Test
    public void testRecursiveBlockingProcessingSubmitPollSubmit() throws InterruptedException {
        /*
         * Specfies the number of recursive blocking actions.
         */
        int[] tests = new int[]{1, 2, 3, 7, 16, 128, 256};
        for (int blockingActions : tests) {
            Semaphore waitForSafepoint = new Semaphore(0);
            Semaphore[] leaveBlocked = createSemaphores(blockingActions, 0);

            try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
                acquire(waitForSafepoint);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicBoolean performed = new AtomicBoolean();
                AtomicBoolean[] inBlockingAction = createBooleans(blockingActions);
                Semaphore[] awaitBlocked = createSemaphores(blockingActions, 0);
                Future<?>[] blockingFutures = new Future<?>[blockingActions];

                for (int i = 0; i < blockingActions; i++) {
                    final int actionIndex = i;
                    blockingFutures[actionIndex] = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                        @Override
                        protected void perform(Access access) {
                            if (actionIndex > 0) {
                                assertTrue(inBlockingAction[actionIndex - 1].get());
                            }
                            inBlockingAction[actionIndex].set(true);
                            try {
                                if (actionIndex < blockingActions - 1) {
                                    awaitBlocked[actionIndex].release();
                                }
                                TruffleSafepoint.setBlockedThreadInterruptible(access.getLocation(), (e) -> {
                                    /*
                                     * The last blocking action must be interrupted in order for the
                                     * subsequently submitted action to get processed.
                                     */
                                    if (actionIndex == blockingActions - 1) {
                                        awaitBlocked[actionIndex].release();
                                    }
                                    leaveBlocked[actionIndex].acquire();
                                }, null);
                            } finally {
                                inBlockingAction[actionIndex].set(false);
                            }
                        }
                    });
                }

                // start processing safepoints now
                waitForSafepoint.release(Integer.MAX_VALUE);

                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    awaitBlocked[actionIndex].acquire();
                }

                Future<?> f = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(Access innerAccess) {
                        assertTrue(inBlockingAction[blockingActions - 1].get());
                        if (performed.get()) {
                            throw new AssertionError("already performed");
                        }
                        performed.set(true);
                    }
                });

                waitOrFail(f);
                assertTrue(performed.get());
                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    leaveBlocked[actionIndex].release();
                }
                for (Future<?> blockingFuture : blockingFutures) {
                    waitOrFail(blockingFuture);
                }
            }
        }
    }

    @TruffleBoundary
    private static void acquire(Semaphore waitForSafepoint) {
        waitForSafepoint.acquireUninterruptibly();
    }

    /*
     * All events are cancelled or performed as soon as the context is closed, otherwise the context
     * close throws an error.
     */
    @Test
    public void testContextCancellation() {
        forEachConfig((threads, events) -> {
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    exceptions.add(throwable);
                }
            })) {
                AtomicBoolean closed = new AtomicBoolean();
                List<Future<?>> threadLocals = new ArrayList<>();

                Semaphore awaitClosing = new Semaphore(0);
                Future<?> closing = service.submit(() -> {
                    awaitClosing.release();
                    setup.context.close(true);
                    closed.set(true);
                });

                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                        @Override
                        protected void perform(Access innerAccess) {
                            if (closed.get()) {
                                fail("no event should ever be called if context is closed");
                            }
                        }
                    }));
                }
                acquire(awaitClosing);

                // we need to allow threads to complete before we cancel
                // this is necessary because we still use instrumentation for cancel and we are
                // not
                // using instrumented nodes here.
                setup.stopped.set(true);
                waitOrFail(closing);
            }
            for (Throwable exception : exceptions) {
                if (!(exception instanceof ThreadDeath)) {
                    throw new AssertionError(exception);
                }
            }
        });
    }

    /*
     * Test that future cancel actually cancels the event.
     */
    @Test
    public void testEventCancellation() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    Future<Void> f = (setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                        @Override
                        protected void perform(Access innerAccess) {
                        }
                    }));
                    if (f.cancel(false)) {
                        assertTrue(f.isDone());
                        assertTrue(f.isCancelled());
                    }
                    futures.add(f);
                }
                for (Future<Void> future : futures) {
                    waitOrFail(future);
                }
            }
        });
    }

    @TruffleBoundary
    private static void contextSafepoint() {
        Context.getCurrent().safepoint();
    }

    @Test
    public void testContextSafepoint() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean();

            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                while (!isStopped(stopped)) {
                    contextSafepoint();
                }
                return true;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector collector = new ActionCollector(setup, eventCounter, true, false);

                // host safepoints don't have a location
                collector.verifyLocation = false;

                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, collector);
                }
                stopped.set(true);
                setup.stopAndAwait();
                // events can happen in any order
                assertActionsAnyOrder(threads, events, collector);
            }
        });
    }

    @Test
    public void testSafepointALot() {
        safepointALot = true;
        try {
            testAsynchronous();
        } finally {
            safepointALot = false;
        }
    }

    @Test
    public void testNonSideEffectInvalidErrorThrown() throws InterruptedException {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            sleepNanosBoundary(50000);
            TruffleSafepoint.poll(node);
            return false;
        })) {
            setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                @Override
                protected void perform(Access outer) {
                    throw new GuestException();
                }
            });
            setup.stopped.set(true);
            setup.futures.get(0).get();
            fail();
        } catch (ExecutionException e) {
            assertTrue(e.getCause().toString(), e.getCause() instanceof AssertionError);
            assertEquals("Throwing Truffle exception is disallowed in non-side-effecting thread local actions.", e.getCause().getMessage());
        }
    }

    /*
     * This needs to be higher than graal.MaximumEscapeAnalysisArrayLength. However in this truffle
     * test the value is not easily accessible.
     */
    static int nonConstantValue = 1024;

    @Test
    public void testBigAllocationInLoop() {
        final int loopCount = 1024;
        Object[] values = new Object[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            for (int i = 0; i < loopCount; i++) {
                // perform an escaping allocation
                values[i] = new Object[nonConstantValue];
                TruffleSafepoint.poll(node);
            }
            return true;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            // now the loop runs and we should get at least one safepoint invocation for each loop
            // invocation. otherwise something is wrong with safepoint elimination

            setup.stopAndAwait();
            int count = counter.counter.get();
            assertTrue(String.valueOf(count), count >= 10);
        }
    }

    @Test
    public void testSimpleAllocationInLoop() {
        final int loopCount = 1024;
        Object[] values = new Object[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            for (int i = 0; i < loopCount; i++) {
                // perform an escaping allocation
                values[i] = new Object();
                TruffleSafepoint.poll(node);
            }
            return true;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            // now the loop runs and we should get at least one safepoint invocation for each loop
            // invocation. otherwise something is wrong with safepoint elimination

            setup.stopAndAwait();
            int count = counter.counter.get();
            assertTrue(String.valueOf(count), count >= 10);
        }
    }

    @Test
    public void testCountedSumLoop() {
        final int loopCount = 1024;
        int[] values = new int[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            int sum = 0;
            for (int i = 0; i < loopCount; i++) {
                sum += values[i];
            }
            // escape sum value
            values[0] = sum;
            return true;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            setup.stopAndAwait();
            int count = counter.counter.get();

            // for a sum loop the number of notifications should be below 1024
            // otherwise truffle loop safepoint elimination did not work.
            assertTrue(String.valueOf(count), count < loopCount);
        }
    }

    @TruffleBoundary
    private static void countDownAndAwait(CountDownLatch await) {
        await.countDown();
        try {
            await.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    static class CallInLoopNode extends Node implements NodeCallable {

        @Child IndirectCallNode indirectCall = IndirectCallNode.create();

        static final int LOOP_COUNT = 1024;

        CallTarget target;
        Semaphore awaitSubmit = new Semaphore(0);
        Object[] values = new Object[LOOP_COUNT];

        public boolean call(TestSetup setup, TestRootNode node) {
            initialize();
            for (int i = 0; i < LOOP_COUNT; i++) {
                // perform an escaping allocation
                values[i] = indirectCall.call(target);
            }
            return true;
        }

        @TruffleBoundary
        private void initialize() {
            target = RootNode.createConstantNode(42).getCallTarget();
            try {
                awaitSubmit.acquire();
            } catch (InterruptedException e) {
                // not expected to interrupt
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void testTruffleCallInLoop() {
        CallInLoopNode callInLoop = new CallInLoopNode();
        try (TestSetup setup = setupSafepointLoop(1, callInLoop)) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            callInLoop.awaitSubmit.release();

            setup.stopAndAwait();
            int count = counter.counter.get();

            // if there is an indirect truffle call in the loop
            // we can omit the safepoint. if that safepoint would
            if (!TruffleOptions.AOT) {
                // TODO GR-29998 safepoint elimination not supported on SVM for now.
                assertTrue(String.valueOf(count), count < CallInLoopNode.LOOP_COUNT * 2);
            }
        }
    }

    @SuppressWarnings("serial")
    static class GuestException extends AbstractTruffleException {

    }

    @Test
    public void testSubmitAsInstrument() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                for (int i = 0; i < events; i++) {
                    setup.instrumentEnv.submitThreadLocal(setup.env.getContext(), null, runnable);
                }
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
        });
    }

    @Test
    public void testSubmitRecurringWaitWithCancel() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false, true);
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    futures.add(setup.instrumentEnv.submitThreadLocal(setup.env.getContext(), null, runnable));
                }
                for (Future<Void> future : futures) {
                    waitOrFail(future);
                    future.cancel(false);
                }

                setup.stopAndAwait();
                assertTrue(runnable.ids.size() >= threads * events);
            }
        });
    }

    @Test
    public void testSubmitRecurringWait() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false, true);
                runnable.verifyLocation = false;
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    futures.add(setup.instrumentEnv.submitThreadLocal(setup.env.getContext(), null, runnable));
                }
                for (Future<Void> future : futures) {
                    waitOrFail(future);
                }

                setup.stopAndAwait();
                assertTrue(runnable.ids.size() >= threads * events);
            }
        });
    }

    @Test
    public void testSubmitRecurringCancel() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                sleepNanosBoundary(50000);
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false, true);
                runnable.verifyLocation = false;
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    futures.add(setup.instrumentEnv.submitThreadLocal(setup.env.getContext(), null, runnable));
                }
                for (Future<Void> future : futures) {
                    future.cancel(false);
                }
                setup.stopAndAwait();
            }
        });
    }

    @Test
    public void testNoSafepointAfterThreadDispose() {
        final ThreadLocal<Boolean> tl = new ThreadLocal<>();

        ProxyLanguage.setDelegate(new ProxyLanguage() {

            @Override
            @TruffleBoundary
            protected void initializeThread(LanguageContext context, Thread thread) {
                tl.set(Boolean.TRUE);
            }

            @Override
            @TruffleBoundary
            protected void disposeThread(LanguageContext context, Thread thread) {
                tl.set(Boolean.FALSE);
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

        });

        forEachConfig((threads, events) -> {
            CountDownLatch awaitThreadStart = new CountDownLatch(1);
            AtomicBoolean threadsStopped = new AtomicBoolean();
            try (TestSetup setup = setupSafepointLoop(threads, new NodeCallable() {

                @TruffleBoundary
                public boolean call(@SuppressWarnings("hiding") TestSetup setup, TestRootNode node) {
                    try {
                        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
                        List<Thread> polyglotThreads = new ArrayList<>();
                        try {
                            for (int i = 0; i < threads; i++) {
                                Thread t = node.setup.env.createThread(() -> {
                                    do {
                                        TruffleContext context = node.setup.env.getContext();
                                        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                                        boolean prevSideEffects = safepoint.setAllowSideEffects(false);
                                        try {
                                            context.leaveAndEnter(null, () -> {
                                                // nothing to do. the important bit is that enter
                                                // sets
                                                // the
                                                // cached thread local
                                                return null;
                                            });
                                        } finally {
                                            safepoint.setAllowSideEffects(prevSideEffects);
                                        }
                                    } while (!threadsStopped.get());
                                });
                                t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                                    public void uncaughtException(@SuppressWarnings("hiding") Thread t, Throwable e) {
                                        threadsStopped.set(true);
                                        e.printStackTrace();
                                        errors.add(e);
                                    }
                                });
                                t.start();
                                polyglotThreads.add(t);
                            }
                        } finally {
                            awaitThreadStart.countDown();
                        }

                        for (Thread thread : polyglotThreads) {
                            thread.join();
                        }
                        for (Throwable t : errors) {
                            throw new AssertionError("thread threw error ", t);
                        }

                        return true;
                    } catch (InterruptedException e1) {
                        throw new AssertionError(e1);
                    }
                }

            })) {

                try {
                    awaitThreadStart.await();

                    // important to let leaving and submitting race against each other
                    threadsStopped.set(true);

                    List<Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < events; i++) {
                        futures.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(Access access) {
                                assertEquals(Boolean.TRUE, tl.get());
                            }
                        }));
                    }
                    for (Future<?> future : futures) {
                        try {
                            future.get();
                        } catch (ExecutionException e) {
                            throw new AssertionError(e);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                setup.stopAndAwait();
            }
        });

        ProxyLanguage.setDelegate(new ProxyLanguage());
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
                if (VERBOSE) {
                    System.out.println("[" + threads + ", " + events + "]");
                }
                try {
                    run.run(threads, events);
                } catch (Throwable e) {
                    throw new AssertionError("Test config threads " + threads + " events " + events + " failed.", e);
                }
            }
        }

        // asynchronous execution of all configs
        if (RERUN_THREAD_CONFIG_ASYNC) {
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

    private TestSetup setupSafepointLoop(int threads, NodeCallable callable) {
        return setupSafepointLoop(threads, callable, null);
    }

    static class TestRootNode extends RootNode {

        final AtomicBoolean stopped;
        final TestSetup setup;
        final CountDownLatch latch;
        @CompilationFinal NodeCallable callable;

        TestRootNode(TruffleLanguage<?> language, AtomicBoolean stopped, TestSetup setup, CountDownLatch latch, NodeCallable callable) {
            super(language);
            this.stopped = stopped;
            this.setup = setup;
            this.latch = latch;
            if (callable instanceof Node) {
                this.callable = (NodeCallable) insert((Node) callable);
            } else {
                this.callable = callable;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object execute(VirtualFrame frame) {
            waitForLatch(latch);
            do {
                Boolean result = callable.call(setup, this);
                if (result) {
                    return result;
                }
                // we want to call at least once
            } while (!stopped.get());
            return null;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public String getName() {
            return "org.graalvm.TestRoot";
        }
    }

    @SuppressWarnings("unchecked")
    private TestSetup setupSafepointLoop(int threads, NodeCallable callable, Consumer<Throwable> exHandler) {
        Context c = createTestContext();
        TestSetup setup = null;
        try {
            c.enter();
            c.initialize(ProxyLanguage.ID);
            ProxyLanguage proxyLanguage = ProxyLanguage.get(null);
            Env env = LanguageContext.get(null).getEnv();
            TruffleInstrument.Env instrument = c.getEngine().getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class).getEnv();
            c.leave();
            CountDownLatch latch = new CountDownLatch(threads);
            Object targetEnter = env.getContext().enter(null);
            AtomicBoolean stopped = new AtomicBoolean();

            TestSetup finalSetup = setup = new TestSetup(c, env, instrument, stopped);
            setup.root = new TestRootNode(proxyLanguage, stopped, setup, latch, callable);
            setup.target = setup.root.getCallTarget();
            env.getContext().leave(null, targetEnter);
            setup.futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                setup.futures.add(service.submit(() -> {
                    Object prev = env.getContext().enter(finalSetup.target.getRootNode());
                    try {
                        do {
                            try {
                                return (Boolean) finalSetup.target.call(latch);
                            } catch (Throwable t) {
                                if (exHandler != null) {
                                    exHandler.accept(t);
                                } else {
                                    throw t;
                                }
                            }
                        } while (!stopped.get());
                        return true;
                    } finally {
                        env.getContext().leave(finalSetup.target.getRootNode(), prev);
                    }
                }));
            }
            try {
                if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    for (Future<Boolean> future : setup.futures) {
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
            return setup;
        } catch (Throwable t) {
            if (setup != null && setup.futures != null) {
                setup.close();
            } else {
                c.close();
            }
            throw t;
        }
    }

    @FunctionalInterface
    interface NodeCallable extends NodeInterface {

        boolean call(TestSetup setup, TestRootNode node);

    }

    static class TestSetup implements AutoCloseable {

        final Context context;
        final Env env;
        final TruffleInstrument.Env instrumentEnv;
        List<Future<Boolean>> futures;
        @CompilationFinal RootCallTarget target;
        @CompilationFinal TestRootNode root;
        final AtomicBoolean stopped;

        TestSetup(Context context, Env env, TruffleInstrument.Env instrumentEnv, AtomicBoolean stopped) {
            this.context = context;
            this.env = env;
            this.instrumentEnv = instrumentEnv;
            this.stopped = stopped;
        }

        void stopAndAwait() {
            stopped.set(true);
            awaitFutures(futures);
        }

        @Override
        public void close() {
            stopAndAwait();
            context.close();
        }
    }

    protected Context createTestContext() {
        Context.Builder b = Context.newBuilder();
        b.allowExperimentalOptions(true);
        b.allowCreateThread(true);
        if (AbstractPolyglotTest.isGraalRuntime()) {
            b.option("engine.CompileImmediately", "true");
            b.option("engine.BackgroundCompilation", "false");
        }
        if (safepointALot) {
            b.option("engine.SafepointALot", "true");
            b.logHandler(new ByteArrayOutputStream()); // discard output of safepoint a lot
        }
        return b.build();
    }

    private boolean safepointALot = false;

    @TruffleBoundary
    private static void waitForLatch(CountDownLatch latch) throws AssertionError {
        latch.countDown();
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    private static void awaitFutures(List<Future<Boolean>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

    static class SafepointCounter extends ThreadLocalAction {

        private final TestSetup setup;

        final AtomicInteger counter = new AtomicInteger();

        protected SafepointCounter(TestSetup setup) {
            super(false, false);
            this.setup = setup;
        }

        @Override
        protected void perform(Access access) {
            counter.incrementAndGet();

            // resubmit
            setup.env.submitThreadLocal(new Thread[]{Thread.currentThread()}, this);
        }
    }

    static class ActionCollector extends ThreadLocalAction {
        final List<Thread> actions = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> ids = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger counter;
        final TestSetup setup;

        boolean verifyLocation = true;

        ActionCollector(TestSetup setup, boolean sync) {
            this(setup, new AtomicInteger(), false, sync);
        }

        ActionCollector(TestSetup setup, AtomicInteger counter, boolean sideEffect, boolean sync) {
            this(setup, counter, sideEffect, sync, false);
        }

        ActionCollector(TestSetup setup, AtomicInteger counter, boolean sideEffect, boolean sync, boolean recurring) {
            super(sideEffect, sync, recurring);
            this.setup = setup;
            this.counter = counter;
        }

        @Override
        protected void perform(Access access) {
            assertSame(Thread.currentThread(), access.getThread());
            if (verifyLocation) {
                assertSame(setup.target.getRootNode(), access.getLocation());
            } else {
                assertNotNull(access.getLocation());
            }
            actions.add(access.getThread());
            ids.add(counter.incrementAndGet());
        }

        @Override
        public String toString() {
            return "ActionCollector@" + Integer.toHexString(hashCode());
        }
    }
}
