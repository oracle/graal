/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.instrumentation.test.ContextInterruptStandaloneTest.attachListener;
import static com.oracle.truffle.api.instrumentation.test.ContextInterruptStandaloneTest.getInstrumentEnv;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@RunWith(Parameterized.class)
public class ContextInterruptParameterizedTest {
    @Parameterized.Parameters(name = "{index}: {0} (nThreads: {2}, multiContext: {3}, multiEngine: {4}, closeAfterInterrupt: {5})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false, false},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 1, false, false, false},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false, false},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 1, false, false, false},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 1, false, false, false},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false, true},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 1, false, false, true},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false, true},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 1, false, false, true},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 1, false, false, true},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false, false},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, false, false, false},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false, false},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, false, false, false},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, false, false, false},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false, true},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, false, false, true},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false, true},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, false, false, true},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, false, false, true},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, false, false},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, true, false, false},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, false, false},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, false, false},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, true, false, false},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, false, true},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, true, false, true},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, false, true},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, false, true},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, true, false, true},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, true, false},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, true, true, false},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, true, false},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, true, false},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, true, true, false},
                        {"infiniteLoop", "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, true, true},
                        {"infiniteLoopWithSleep", "BLOCK(CONSTANT(42),LOOP(infinity, SLEEP(1000)))", 10, true, true, true},
                        {"infiniteLoopInInnerContext", "CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, true, true},
                        {"infiniteLoopWithPolyglotThreads", "BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, true, true},
                        {"infiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered",
                                        "BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 10, true, true, true},
        });
    }

    @Parameterized.Parameter public String testName;
    @Parameterized.Parameter(1) public String code;
    @Parameterized.Parameter(2) public int nThreads;
    @Parameterized.Parameter(3) public boolean multiContext;
    @Parameterized.Parameter(4) public boolean multiEngine;
    @Parameterized.Parameter(5) public boolean closeAfterInterrupt;

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testInterrupt() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        CountDownLatch allCancelledLatch = new CountDownLatch(nThreads + 1);
        List<Context> contexts = new ArrayList<>();
        Engine engine = multiEngine ? null : Engine.create();
        Context.Builder builder = Context.newBuilder().allowCreateThread(true);
        CountDownLatch passLatch = new CountDownLatch(nThreads);
        if (engine != null) {
            builder.engine(engine);
            TruffleInstrument.Env instrumentEnv = getInstrumentEnv(engine);
            attachListener(passLatch::countDown, instrumentEnv);
        }
        for (int i = 0; i < nThreads; i++) {
            if (multiContext || i == 0) {
                Context ctx = builder.build();
                contexts.add(ctx);
                if (engine == null) {
                    TruffleInstrument.Env instrumentEnv = getInstrumentEnv(ctx.getEngine());
                    attachListener(passLatch::countDown, instrumentEnv);
                }
            } else {
                contexts.add(contexts.get(i - 1));
            }
        }
        try {
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "InfiniteLoop").build();
            if (!multiContext && nThreads > 1) {
                /*
                 * Prevent multiple definition of the foo function in case of single-context and
                 * multi-threading.
                 */
                contexts.get(0).parse(source);
            }
            Source checkSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "CheckAlive").build();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                int j = i;
                futures.add(executorService.submit(() -> {
                    Context context = contexts.get(j);
                    try {
                        context.initialize(InstrumentationTestLanguage.ID);
                        context.eval(source);
                        Assert.fail();
                    } catch (PolyglotException pe) {
                        if (!pe.isInterrupted()) {
                            throw pe;
                        }
                    }
                    if (closeAfterInterrupt) {
                        try {
                            context.close();
                        } catch (IllegalStateException ise) {
                            /*
                             * Even though the interrupt is successful,
                             * PolyglotImpl#guestToHostException enters the context again. If this
                             * happens when the close method checks whether all threads are not
                             * active, it throws an exception with the following message.
                             */
                            if (!"The context is currently executing on another thread. Set cancelIfExecuting to true to stop the execution on this thread.".equals(ise.getMessage())) {
                                throw ise;
                            }
                        }
                    } else {
                        allCancelledLatch.countDown();
                        try {
                            allCancelledLatch.await();
                        } catch (InterruptedException ie) {
                        }
                        /*
                         * Verify the context is still alive in thread that was cancelled. The
                         * cancellation must be done by now or the verification could get cancelled.
                         */
                        Assert.assertEquals(42, context.eval(checkSource).asInt());
                    }
                    Assert.assertNull("Truffle stack frames not cleared", getThreadLocalStackTraceIfAvailable());
                }));
            }
            passLatch.await();
            for (int i = 0; i < nThreads; i++) {
                if (multiContext || i == 0) {
                    Context context = contexts.get(i);
                    context.interrupt(Duration.ofSeconds(50));
                }
            }
            allCancelledLatch.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
            if (!closeAfterInterrupt) {
                /*
                 * Verify the context is still alive in the thread that does the interrupt.
                 */
                for (int i = 0; i < nThreads; i++) {
                    if (multiContext || i == 0) {
                        Assert.assertEquals(42, contexts.get(i).eval(checkSource).asInt());
                    }
                }
            }
        } finally {
            for (int i = 0; i < nThreads; i++) {
                if (multiContext || i == 0) {
                    contexts.get(i).close();
                }
            }
        }
    }

    private static Object getThreadLocalStackTraceIfAvailable() {
        if (TruffleTestAssumptions.isFallbackRuntime() && !TruffleTestAssumptions.isClassLoaderEncapsulation()) {
            DefaultTruffleRuntime runtime = (DefaultTruffleRuntime) (Truffle.getRuntime());
            try {
                Method method = runtime.getClass().getDeclaredMethod("getThreadLocalStackTrace");
                method.setAccessible(true);
                return method.invoke(runtime);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError(e);
            }
        } else {
            return null;
        }
    }
}
