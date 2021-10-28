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
package com.oracle.truffle.api.instrumentation.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class ContextPauseTest {

    private static final String TEST_EXECUTION_STOPPED = "Test execution stopped!";

    private interface OnEnterAction {
        void execute(EventContext c, CountDownLatch pauseLatch, AtomicBoolean stop);
    }

    private interface GuestAction {
        void execute(Context context, CountDownLatch pauseLatch);
    }

    private interface AfterPauseAction {
        void execute(Context context, TruffleContext truffleContext, AtomicBoolean stop, List<Future<Void>> pauseFutures, List<Future<?>> guestActionFutures);
    }

    private static void testCommon(OnEnterAction onEnterAction, GuestAction guestAction, boolean waitForPause, AfterPauseAction afterPauseAction) throws ExecutionException, InterruptedException {
        for (int nThreads = 1; nThreads <= 10; nThreads += 3) {
            for (int nPauses = 1; nPauses <= 3; nPauses++) {
                ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
                try (Context context = Context.create()) {
                    context.initialize(InstrumentationTestLanguage.ID);
                    TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                    CountDownLatch pauseLatch = new CountDownLatch(nThreads);
                    AtomicBoolean stop = new AtomicBoolean();
                    if (onEnterAction != null) {
                        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                            @Override
                            @CompilerDirectives.TruffleBoundary
                            public void onEnter(EventContext c, VirtualFrame frame) {
                                onEnterAction.execute(c, pauseLatch, stop);
                            }

                            @Override
                            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                            }

                            @Override
                            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                            }
                        });
                    }
                    TruffleContext truffleContext;
                    context.enter();
                    try {
                        truffleContext = instrumentEnv.getEnteredContext();
                    } finally {
                        context.leave();
                    }
                    List<Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < nThreads; i++) {
                        futures.add(executorService.submit(() -> {
                            guestAction.execute(context, pauseLatch);
                        }));
                    }
                    pauseLatch.await();
                    List<Future<Void>> pauseFutures = new ArrayList<>();
                    for (int i = 0; i < nPauses; i++) {
                        pauseFutures.add(truffleContext.pause());
                    }
                    if (waitForPause) {
                        for (Future<Void> pauseFuture : pauseFutures) {
                            pauseFuture.get();
                        }
                    }
                    afterPauseAction.execute(context, truffleContext, stop, pauseFutures, futures);
                    for (Future<?> future : futures) {
                        future.get();
                    }
                    executorService.shutdownNow();
                    Assert.assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));

                } catch (Throwable t) {
                    executorService.shutdownNow();

                    // shorter timeout to show errors more quickly
                    executorService.awaitTermination(1, TimeUnit.SECONDS);
                    throw t;
                }
            }
        }
    }

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testPause() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> {
                            if (c.hasTag(InstrumentationTestLanguage.ConstantTag.class)) {
                                pauseLatch.countDown();
                            }
                            if (stop.get()) {
                                throw new RuntimeException(TEST_EXECUTION_STOPPED);
                            }
                        },
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!("java.lang.RuntimeException: " + TEST_EXECUTION_STOPPED).equals(e.getMessage())) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> {
                            stop.set(true);
                            for (Future<Void> pauseFuture : pauseFutures) {
                                for (Future<?> future : guestActionFutures) {
                                    AbstractPolyglotTest.assertFails(() -> future.get(100, TimeUnit.MILLISECONDS), TimeoutException.class);
                                }
                                truffleContext.resume(pauseFuture);
                            }
                        });
    }

    @Test
    public void testPauseFiniteCountOfInifiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> {
                            if (stop.get()) {
                                throw new RuntimeException(TEST_EXECUTION_STOPPED);
                            }
                        },
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                }
                            } catch (PolyglotException e) {
                                if (!("java.lang.RuntimeException: " + TEST_EXECUTION_STOPPED).equals(e.getMessage())) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> {
                            stop.set(true);
                            for (Future<Void> pauseFuture : pauseFutures) {
                                for (Future<?> future : guestActionFutures) {
                                    AbstractPolyglotTest.assertFails(() -> future.get(100, TimeUnit.MILLISECONDS), TimeoutException.class);
                                }
                                truffleContext.resume(pauseFuture);
                            }
                        });
    }

    @Test
    public void testCancelWhilePaused() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> pauseLatch.countDown(),
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausedFiniteCountOfInifiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        null,
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                }
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }

                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausing() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> pauseLatch.countDown(),
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }
                        },
                        false,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausingFiniteCountOfInifiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        null,
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                }
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }

                        },
                        false,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }
}
