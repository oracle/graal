/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.test.polyglot.MultiThreadedLanguage.LanguageContext;
import com.oracle.truffle.api.test.polyglot.MultiThreadedLanguage.ThreadRequest;

public class MultiThreadedLanguageTest {

    static volatile LanguageContext langContext;

    private static Value eval(Context context, Function<Env, Object> f) {
        MultiThreadedLanguage.runinside.set(f);
        try {
            return context.eval(MultiThreadedLanguage.ID, "");
        } finally {
            MultiThreadedLanguage.runinside.set(null);
        }
    }

    @Test
    public void testNoThreadAllowed() {
        Context context = Context.create(MultiThreadedLanguage.ID);

        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return false;
        };

        try {
            context.initialize(MultiThreadedLanguage.ID);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Single threaded access requested by thread "));
        }

        try {
            eval(context, (env) -> null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Single threaded access requested by thread "));
        }

        try {
            eval(context, (env) -> null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Single threaded access requested by thread "));
        }

        // allow again so we can close
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return true;
        };
        context.close();
    }

    private static void assertMultiThreadedError(Value value, Consumer<Value> valueConsumer) {
        try {
            valueConsumer.accept(value);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Multi threaded access requested by thread "));
        }
    }

    private static void assertUnsupportedOrNoError(Value value, Consumer<Value> valueConsumer) {
        try {
            valueConsumer.accept(value);
        } catch (UnsupportedOperationException e) {
        } catch (ClassCastException e) {
        } catch (IllegalArgumentException e) {
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testSingleThreading() throws InterruptedException, ExecutionException {
        Context context = Context.create(MultiThreadedLanguage.ID);

        AtomicReference<ThreadRequest> lastIsAllowedRequest = new AtomicReference<>(null);

        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            lastIsAllowedRequest.set(req);
            return req.singleThreaded;
        };

        ExecutorService executor = createExecutor(1);

        assertEquals(0, initializeCount.get());
        assertNull(lastInitializeRequest.get());

        Value value = eval(context, (env) -> new Object());

        assertEquals(1, initializeCount.get());
        assertEquals(true, lastIsAllowedRequest.get().singleThreaded);
        assertSame(Thread.currentThread(), lastInitializeRequest.get().thread);
        assertSame(Thread.currentThread(), lastIsAllowedRequest.get().thread);

        CountDownLatch latch = new CountDownLatch(1);

        Completable<Value> future = evalAndWait(executor, context, true, latch, (ev) -> MultiThreadedLanguage.getContext());
        latch.await();

        assertEquals(2, initializeCount.get());
        assertEquals(true, lastIsAllowedRequest.get().singleThreaded);
        assertSame(threads.iterator().next(), lastInitializeRequest.get().thread);
        assertSame(threads.iterator().next(), lastIsAllowedRequest.get().thread);

        assertEquals(0, disposeCount.get());
        assertNull(lastDisposeRequest.get());

        try {
            eval(context, (env) -> null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Multi threaded access requested by thread "));
        }

        assertMultiThreadedError(value, Value::execute);
        assertMultiThreadedError(value, Value::isBoolean);
        // assertMultiThreadedError(value, Value::isHostObject);
        assertMultiThreadedError(value, Value::isNativePointer);
        assertMultiThreadedError(value, Value::isNull);
        assertMultiThreadedError(value, Value::isNumber);
        assertMultiThreadedError(value, Value::isString);
        assertMultiThreadedError(value, Value::isMetaObject);
        assertMultiThreadedError(value, Value::isTime);
        assertMultiThreadedError(value, Value::isTimeZone);
        assertMultiThreadedError(value, Value::isDate);
        assertMultiThreadedError(value, Value::isInstant);
        assertMultiThreadedError(value, Value::isException);
        assertMultiThreadedError(value, Value::fitsInByte);
        assertMultiThreadedError(value, Value::fitsInShort);
        assertMultiThreadedError(value, Value::fitsInInt);
        assertMultiThreadedError(value, Value::fitsInLong);
        assertMultiThreadedError(value, Value::fitsInFloat);
        assertMultiThreadedError(value, Value::fitsInDouble);
        // assertMultiThreadedError(value, Value::asHostObject);
        assertMultiThreadedError(value, Value::asBoolean);
        assertMultiThreadedError(value, Value::asByte);
        assertMultiThreadedError(value, Value::asShort);
        assertMultiThreadedError(value, Value::asInt);
        assertMultiThreadedError(value, Value::asLong);
        assertMultiThreadedError(value, Value::asFloat);
        assertMultiThreadedError(value, Value::asDouble);
        assertMultiThreadedError(value, Value::asNativePointer);
        assertMultiThreadedError(value, Value::asString);
        assertMultiThreadedError(value, Value::getArraySize);
        assertMultiThreadedError(value, Value::getMemberKeys);
        assertMultiThreadedError(value, Value::getMetaObject);
        assertMultiThreadedError(value, Value::asInstant);
        assertMultiThreadedError(value, Value::asDate);
        assertMultiThreadedError(value, Value::asTimeZone);
        assertMultiThreadedError(value, Value::asTime);
        assertMultiThreadedError(value, Value::getMetaQualifiedName);
        assertMultiThreadedError(value, Value::getMetaSimpleName);
        assertMultiThreadedError(value, Value::throwException);
        assertMultiThreadedError(value, (v) -> v.isMetaInstance(""));
        assertMultiThreadedError(value, (v) -> v.getMember(""));
        assertMultiThreadedError(value, (v) -> v.putMember("", null));
        assertMultiThreadedError(value, (v) -> v.removeMember(""));
        assertMultiThreadedError(value, (v) -> v.hasMember(""));
        assertMultiThreadedError(value, (v) -> v.canExecute());
        assertMultiThreadedError(value, (v) -> v.getArraySize());
        assertMultiThreadedError(value, (v) -> v.getArrayElement(0));
        assertMultiThreadedError(value, (v) -> v.removeArrayElement(0));
        assertMultiThreadedError(value, (v) -> v.setArrayElement(0, null));

        assertEquals(2, initializeCount.get());
        assertSame(Thread.currentThread(), lastIsAllowedRequest.get().thread);
        assertEquals(false, lastIsAllowedRequest.get().singleThreaded);

        assertEquals(0, disposeCount.get());
        assertNull(lastDisposeRequest.get());

        // cannot close still running
        try {
            context.close();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("The context is currently executing on another thread. Set cancelIfExecuting to true to stop the execution on this thread."));
        }

        assertSame(Thread.currentThread(), lastIsAllowedRequest.get().thread);
        assertEquals(false, lastIsAllowedRequest.get().singleThreaded);

        future.complete();

        assertNotNull(future.get());

        // closing the context must call dispose for the one thread
        // that returned true on initialize.
        context.close();

        assertEquals(2, initializeCount.get());
        assertEquals(2, disposeCount.get());
        assertTrue(lastDisposeRequest.get().thread == Thread.currentThread() || lastDisposeRequest.get().thread == threads.iterator().next());

        // cleanup executor
        assertTrue(executor.shutdownNow().isEmpty());
    }

    @Test
    public void testMultiThreading() throws InterruptedException, ExecutionException {
        AtomicReference<ThreadRequest> lastIsAllowedRequest = new AtomicReference<>(null);
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            lastIsAllowedRequest.set(req);
            return true;
        };

        final int threadCount = 10;
        final int outerLoop = 10;
        final int innerLoop = 100;
        ExecutorService executor = createExecutor(threadCount);
        for (int outerIter = 0; outerIter < outerLoop; outerIter++) {
            resetData();

            Context context = Context.create(MultiThreadedLanguage.ID);

            assertEquals(0, initializeCount.get());
            assertNull(lastInitializeRequest.get());

            List<Completable<Value>> results = new ArrayList<>();
            for (int iteration = 0; iteration < innerLoop; iteration++) {
                CountDownLatch latch = iteration == 0 ? new CountDownLatch(threadCount) : null;

                Value value = eval(context, (env) -> new Object());

                for (int i = 0; i < threadCount; i++) {
                    results.add(evalAndWait(executor, context, iteration == 0, latch, (ev) -> MultiThreadedLanguage.getContext()));
                }

                assertUnsupportedOrNoError(value, Value::execute);
                assertUnsupportedOrNoError(value, Value::isBoolean);
                assertUnsupportedOrNoError(value, Value::isHostObject);
                assertUnsupportedOrNoError(value, Value::isNativePointer);
                assertUnsupportedOrNoError(value, Value::isNull);
                assertUnsupportedOrNoError(value, Value::isNumber);
                assertUnsupportedOrNoError(value, Value::isString);
                assertUnsupportedOrNoError(value, Value::fitsInByte);
                assertUnsupportedOrNoError(value, Value::fitsInDouble);
                assertUnsupportedOrNoError(value, Value::fitsInFloat);
                assertUnsupportedOrNoError(value, Value::fitsInInt);
                assertUnsupportedOrNoError(value, Value::fitsInLong);
                assertUnsupportedOrNoError(value, Value::asBoolean);
                assertUnsupportedOrNoError(value, Value::asByte);
                assertUnsupportedOrNoError(value, Value::asDouble);
                assertUnsupportedOrNoError(value, Value::asFloat);
                assertUnsupportedOrNoError(value, Value::asHostObject);
                assertUnsupportedOrNoError(value, Value::asInt);
                assertUnsupportedOrNoError(value, Value::asLong);
                assertUnsupportedOrNoError(value, Value::asNativePointer);
                assertUnsupportedOrNoError(value, Value::asString);
                assertUnsupportedOrNoError(value, Value::getArraySize);
                assertUnsupportedOrNoError(value, Value::getMemberKeys);
                assertUnsupportedOrNoError(value, Value::getMetaObject);
                assertUnsupportedOrNoError(value, (v) -> v.getMember(""));
                assertUnsupportedOrNoError(value, (v) -> v.putMember("", null));
                assertUnsupportedOrNoError(value, (v) -> v.hasMember(""));
                assertUnsupportedOrNoError(value, (v) -> v.canExecute());
                assertUnsupportedOrNoError(value, (v) -> v.getArraySize());
                assertUnsupportedOrNoError(value, (v) -> v.getArrayElement(0));
                assertUnsupportedOrNoError(value, (v) -> v.setArrayElement(0, null));

                // we need to wait for them once to ensure every thread is initialized
                if (iteration == 0) {
                    latch.await();
                    for (Completable<Value> future : results) {
                        future.complete();
                    }
                }
            }

            for (Completable<Value> future : results) {
                Object languageContext = future.get().asHostObject();
                assertSame(MultiThreadedLanguage.langContext, languageContext);
            }

            assertEquals(threadCount + 1, initializeCount.get());
            assertEquals(1, initializeMultiThreadingCount.get());
            assertEquals(false, lastIsAllowedRequest.get().singleThreaded);

            context.close();

            // main thread gets initialized after close as well
            assertEquals(false, lastIsAllowedRequest.get().singleThreaded);
            assertEquals(1, initializeMultiThreadingCount.get());
            assertEquals(threadCount + 1, initializeCount.get());
            assertEquals(threadCount + 1, disposeCount.get());
        }

        assertTrue(executor.shutdownNow().isEmpty());
    }

    @Test
    public void testAccessTruffleContextPolyglotThread() throws Throwable {
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return true;
        };
        Engine engine = Engine.create();
        AtomicReference<Throwable> seenError = new AtomicReference<>();
        Context context = Context.newBuilder().allowCreateThread(true).engine(engine).build();
        eval(context, new Function<Env, Object>() {
            public Object apply(Env env) {
                List<Thread> createdThreads = new ArrayList<>();
                ExecutorService service = Executors.newFixedThreadPool(10, (r) -> {
                    Thread t = env.createThread(r);
                    t.setUncaughtExceptionHandler((thread, e) -> seenError.set(e));
                    createdThreads.add(t);
                    return t;
                });
                TruffleContext innerContext = env.newContextBuilder().build();

                List<Future<LanguageContext>> futures = new ArrayList<>();
                List<Future<LanguageContext>> innerContextFutures = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    innerContextFutures.add(service.submit(() -> {
                        Object prev = innerContext.enter();
                        try {
                            return MultiThreadedLanguage.getContext();
                        } finally {
                            innerContext.leave(prev);
                        }
                    }));
                }
                for (int i = 0; i < 100; i++) {
                    futures.add(service.submit(() -> {
                        return MultiThreadedLanguage.getContext();
                    }));
                }

                try {
                    for (Future<LanguageContext> future : futures) {
                        assertSame(MultiThreadedLanguage.getContext(), future.get());
                    }
                    LanguageContext innerLanguageContext;
                    Object prev = innerContext.enter();
                    innerLanguageContext = MultiThreadedLanguage.getContext();
                    innerContext.leave(prev);
                    for (Future<LanguageContext> future : innerContextFutures) {
                        assertSame(innerLanguageContext, future.get());
                    }
                    innerContext.close();

                } catch (InterruptedException e1) {
                    throw new AssertionError(e1);
                } catch (ExecutionException e1) {
                    throw new AssertionError(e1);
                }
                service.shutdown();
                /*
                 * We need to join all threads as unfortunately the executor service does not
                 * guarantee that all threads are immediately shutdown.
                 */
                try {
                    for (Thread t : createdThreads) {
                        t.join(1000);
                    }
                } catch (InterruptedException e1) {
                    throw new AssertionError(e1);
                }

                return MultiThreadedLanguage.getContext();
            }
        });
        if (seenError.get() != null) {
            throw seenError.get();
        }
        engine.close();
    }

    @Test
    public void testAccessTruffleContextFromExclusivePolyglotThread() throws Throwable {
        // don't allow multi-threading in this test as every context
        // is used exclusively by one thread.
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return req.singleThreaded;
        };
        final int iterations = 10;
        final int innerIterations = 10;

        AtomicReference<Throwable> lastError = new AtomicReference<>();
        UncaughtExceptionHandler uncaughtHandler = (run, e) -> lastError.set(e);
        Context polyglotContext = Context.newBuilder().allowCreateThread(true).build();
        ConcurrentHashMap<LanguageContext, String> seenContexts = new ConcurrentHashMap<>();
        eval(polyglotContext, new Function<Env, Object>() {
            @SuppressWarnings("hiding")
            public Object apply(Env env) {
                List<Thread> threads = new ArrayList<>();
                List<TruffleContext> contexts = new ArrayList<>();
                for (int i = 0; i < iterations; i++) {
                    TruffleContext context = env.newContextBuilder().build();
                    Thread thread = env.createThread(() -> {
                        assertUniqueContext();
                        List<Thread> innerThreads = new ArrayList<>();
                        List<TruffleContext> innerContexts = new ArrayList<>();
                        for (int j = 0; j < innerIterations; j++) {
                            TruffleContext innerContext = env.newContextBuilder().build();
                            Thread innerThread = env.createThread(() -> {
                                assertUniqueContext();
                            }, innerContext);
                            innerThread.setUncaughtExceptionHandler(uncaughtHandler);
                            innerThread.start();

                            innerThreads.add(innerThread);
                            innerContexts.add(innerContext);
                        }
                        for (Thread innerThread : innerThreads) {
                            try {
                                innerThread.join();
                            } catch (InterruptedException e) {
                            }
                        }
                        for (TruffleContext innerContext : innerContexts) {
                            innerContext.close();
                        }

                    }, context);
                    thread.setUncaughtExceptionHandler(uncaughtHandler);
                    thread.start();
                    threads.add(thread);
                    contexts.add(context);
                }
                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                }
                for (TruffleContext context : contexts) {
                    context.close();
                }
                return null;
            }

            private LanguageContext assertUniqueContext() {
                LanguageContext languageContext = MultiThreadedLanguage.getContext();
                Assert.assertNotNull(languageContext);
                Assert.assertFalse(seenContexts.containsKey(languageContext));
                seenContexts.put(languageContext, "");
                return languageContext;
            }
        });
        Assert.assertEquals(221, initializeCount.get());
        Assert.assertEquals(initializeCount.get() - 1, disposeCount.get());
        Assert.assertEquals(0, initializeMultiThreadingCount.get());
        // Test that the same context is available in threads when created with Env.getContext()
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return true;
        };
        eval(polyglotContext, new Function<Env, Object>() {
            @SuppressWarnings("hiding")
            public Object apply(Env env) {
                List<Thread> threads = new ArrayList<>();
                LanguageContext languageContext = MultiThreadedLanguage.getContext();
                for (int i = 0; i < iterations; i++) {
                    Thread thread = env.createThread(() -> {
                        LanguageContext threadContext = MultiThreadedLanguage.getContext();
                        assertSame(languageContext, threadContext);
                        List<Thread> innerThreads = new ArrayList<>();
                        List<TruffleContext> innerContexts = new ArrayList<>();
                        for (int j = 0; j < innerIterations; j++) {
                            Thread innerThread = env.createThread(() -> {
                                LanguageContext innerThreadContext = MultiThreadedLanguage.getContext();
                                assertSame(languageContext, innerThreadContext);
                            }, env.getContext());
                            innerThread.setUncaughtExceptionHandler(uncaughtHandler);
                            innerThread.start();

                            innerThreads.add(innerThread);
                        }
                        for (Thread innerThread : innerThreads) {
                            try {
                                innerThread.join();
                            } catch (InterruptedException e) {
                            }
                        }
                        for (TruffleContext innerContext : innerContexts) {
                            innerContext.close();
                        }

                    }, env.getContext());
                    thread.setUncaughtExceptionHandler(uncaughtHandler);
                    thread.start();
                    threads.add(thread);
                }
                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                }
                return null;
            }
        });
        if (lastError.get() != null) {
            throw lastError.get();
        }
        polyglotContext.close();
        Assert.assertEquals(331, initializeCount.get());
        Assert.assertEquals(initializeCount.get(), disposeCount.get());
        Assert.assertEquals(1, initializeMultiThreadingCount.get());
    }

    @Test
    public void testAsssertionIfThreadStillActive() throws InterruptedException {
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return true;
        };
        Engine engine = Engine.create();
        Context context = Context.newBuilder().allowCreateThread(true).engine(engine).build();
        Semaphore wait = new Semaphore(0);
        Thread returnThread = eval(context, new Function<Env, Object>() {
            public Object apply(Env env) {
                Semaphore waitForEnter = new Semaphore(0);
                Thread t = env.createThread(() -> {
                    try {
                        waitForEnter.release();
                        wait.acquire();
                    } catch (InterruptedException e) {
                    }
                });
                t.start();
                try {
                    waitForEnter.acquire();
                } catch (InterruptedException e) {
                }
                return t;
            }
        }).asHostObject();
        try {
            engine.close();
            Assert.fail();
        } catch (PolyglotException e) {
            assertTrue(e.isInternalError());
            assertTrue(e.getMessage().contains("The language did not complete all polyglot threads but should have"));
        }
        wait.release(1);
        returnThread.join();
        engine.close();
    }

    @Test
    public void testInterruptPolyglotThread() throws Throwable {
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
            return true;
        };
        AtomicBoolean seenInterrupt = new AtomicBoolean(false);
        AtomicReference<Throwable> seenError = new AtomicReference<>();
        Engine engine = Engine.create();
        Context context = Context.newBuilder().allowCreateThread(true).engine(engine).build();
        Semaphore wait = new Semaphore(0);
        eval(context, new Function<Env, Object>() {
            public Object apply(Env env) {
                Semaphore waitForEnter = new Semaphore(0);
                Thread t = env.createThread(() -> {
                    try {
                        waitForEnter.release();
                        wait.acquire();
                    } catch (InterruptedException e) {
                        seenInterrupt.set(true);
                    }
                });
                t.setUncaughtExceptionHandler((thread, e) -> seenError.set(e));
                t.start();
                try {
                    waitForEnter.acquire();
                } catch (InterruptedException e) {
                }
                return t;
            }
        }).asHostObject();
        engine.close(true);
        if (seenError.get() != null) {
            throw seenError.get();
        }
        Assert.assertTrue(seenInterrupt.get());
    }

    @Test
    public void testMultiThreadedAccessExceptionThrownToCreator() throws Throwable {
        try (Context context = Context.newBuilder(MultiThreadedLanguage.ID).allowCreateThread(true).build()) {
            MultiThreadedLanguage.isThreadAccessAllowed = (req) -> {
                return req.singleThreaded;
            };
            eval(context, (env) -> {
                AbstractPolyglotTest.assertFails(() -> env.createThread(() -> {
                }), IllegalStateException.class, (ise) -> {
                    assertTrue(ise.getMessage().contains("Multi threaded access requested by thread"));
                });
                return null;
            });
        }
    }

    /*
     * Test infrastructure code.
     */
    private final List<Thread> threads = new ArrayList<>();
    private final List<ExecutorService> executors = new ArrayList<>();

    volatile AtomicInteger initializeCount;
    volatile AtomicInteger disposeCount;
    volatile AtomicInteger initializeMultiThreadingCount;
    volatile AtomicReference<ThreadRequest> lastInitializeRequest;
    volatile AtomicReference<ThreadRequest> lastDisposeRequest;
    private final Map<Object, Set<Thread>> initializedThreadsPerContext = Collections.synchronizedMap(new HashMap<>());

    @Before
    public void setup() {
        resetData();

        MultiThreadedLanguage.initializeMultiThreading = (req) -> {
            initializeMultiThreadingCount.incrementAndGet();
            Assert.assertSame(req.context, MultiThreadedLanguage.getContext());
            return null;
        };

        MultiThreadedLanguage.initializeThread = (req) -> {
            initializeCount.incrementAndGet();
            lastInitializeRequest.set(req);
            Assert.assertSame(req.context, MultiThreadedLanguage.getContext());
            Set<Thread> threadsPerContext = initializedThreadsPerContext.computeIfAbsent(req.context, (e) -> Collections.synchronizedSet(new HashSet<Thread>()));
            if (threadsPerContext.contains(req.thread)) {
                throw new AssertionError("Thread initialized twice for context " + req.context + " thread " + req.thread);
            }
            threadsPerContext.add(req.thread);
            return null;
        };

        MultiThreadedLanguage.disposeThread = (req) -> {
            disposeCount.incrementAndGet();
            lastDisposeRequest.set(req);
            Assert.assertSame(req.context, MultiThreadedLanguage.getContext());
            Set<Thread> threadsPerContext = initializedThreadsPerContext.get(req.context);
            if (!threadsPerContext.contains(req.thread)) {
                throw new AssertionError("Not initialized but disposed thread " + req.thread);
            }
            // should not be able to dispose twice.
            threadsPerContext.remove(req.thread);
            return null;
        };
    }

    private void resetData() {
        initializeCount = new AtomicInteger(0);
        disposeCount = new AtomicInteger(0);
        initializeMultiThreadingCount = new AtomicInteger(0);
        lastInitializeRequest = new AtomicReference<>(null);
        lastDisposeRequest = new AtomicReference<>(null);
        initializedThreadsPerContext.clear();
    }

    @After
    public void teardown() {
        threads.clear();
        for (ExecutorService executor : executors) {
            assertTrue(executor.shutdownNow().isEmpty());
        }
        executors.clear();
        for (Entry<Object, Set<Thread>> entry : initializedThreadsPerContext.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                throw new AssertionError("Threads initialized but not disposed for context " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private ExecutorService createExecutor(int noThreads) {
        threads.clear();
        ExecutorService service = Executors.newFixedThreadPool(noThreads, (r) -> {
            Thread t = new Thread(r);
            threads.add(t);
            return t;
        });
        executors.add(service);
        return service;
    }

    /**
     * Method evals code on another thread and waits until the function is finished evaluation. The
     * future then waits until Future#get is invoked to complete the operation.
     */
    private static Completable<Value> evalAndWait(ExecutorService executor, Context context, boolean manualComplete, CountDownLatch latch, Function<Env, Object> f) {
        Semaphore waitInEval = manualComplete ? new Semaphore(0) : null;
        Future<Value> valueResult = executor.<Value> submit(() -> {
            try {
                return eval(context, (env) -> {
                    Object result = f.apply(env);
                    try {
                        if (latch != null) {
                            latch.countDown();
                        }
                        if (waitInEval != null) {
                            waitInEval.acquire();
                        }
                    } catch (InterruptedException e) {
                        // cancelled
                    }
                    return result;
                });
            } catch (Throwable e) {
                if (latch != null) {
                    latch.countDown();
                }
                throw e;
            }
        });
        return new Completable<>(valueResult, waitInEval);
    }

    private static class Completable<V> implements Future<V> {

        private final Future<V> delegate;
        private final Semaphore waitInEval;

        Completable(Future<V> delegate, Semaphore waitInEval) {
            this.delegate = delegate;
            this.waitInEval = waitInEval;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        public boolean isDone() {
            return delegate.isDone();
        }

        public V get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }

        void complete() {
            waitInEval.release();
        }

    }

}
