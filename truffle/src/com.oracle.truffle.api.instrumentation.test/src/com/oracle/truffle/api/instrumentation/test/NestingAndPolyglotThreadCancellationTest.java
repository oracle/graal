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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
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
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;

public class NestingAndPolyglotThreadCancellationTest {
    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testCancelSpawnedThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo))", "CancelSpawnedThread").build();
            context.eval(source);
            context.close(true);
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testCancelSpawnedThreadFromAnotherThread() throws Exception {
        /*
         * This test verifies that cancelling of context from another thread does not cancel the
         * main thread too soon when the polyglot thread is still running which would cause the
         * implicit close method to throw illegal state exception.
         */
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<?> future = null;
            try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
                context.initialize(InstrumentationTestLanguage.ID);
                Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,BLOCK(SLEEP(100),STATEMENT))),SPAWN(foo),LOOP(infinity,STATEMENT),JOIN())",
                                "CancelSpawnedThread").build();
                TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                CountDownLatch cancelLatch = new CountDownLatch(1);
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                    @Override
                    public void onEnter(EventContext ctx, VirtualFrame frame) {
                        onEnterBoundary();
                    }

                    @CompilerDirectives.TruffleBoundary
                    private void onEnterBoundary() {
                        cancelLatch.countDown();
                    }

                    @Override
                    public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {

                    }

                    @Override
                    public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                    }
                });
                future = executorService.submit(() -> {
                    try {
                        cancelLatch.await();
                    } catch (InterruptedException ie) {
                    }
                    context.close(true);
                });
                try {
                    context.eval(source);
                    fail();
                } catch (PolyglotException pe) {
                    /*
                     * We need to catch the cancelled exception here, if we did that in the main
                     * try-with-resources block, then the potential IllegalStateException caused by
                     * not all polyglot threads being finished would be suppressed by the cancelled
                     * exception.
                     */
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            future.get();
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testCancelNestedContext() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<?> future;
            CountDownLatch mainThreadLatch = new CountDownLatch(2);
            Context[] context = new Context[1];
            context[0] = Context.newBuilder().allowExperimentalOptions(true).build();
            TruffleInstrument.Env instrumentEnv = context[0].getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext c, VirtualFrame frame) {
                }

                @Override
                public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                    onReturnValueBoundary(result);
                }

                @CompilerDirectives.TruffleBoundary
                private void onReturnValueBoundary(Object result) {
                    mainThreadLatch.countDown();
                    if (Integer.valueOf(42).equals(result)) {
                        context[0].enter();
                        try {
                            /*
                             * Eval does not enter the polyglot thread for the second time, the
                             * explicit enter does. In any case, here the wrapped ExitException can
                             * be caught, or actually must be caught, because PolyglotException must
                             * not be thrown from inside guest language code. If the host does not
                             * allow the exit to continue, then we have a problem, but is this
                             * really a problem? In any case we need some place to catch the
                             * ExitException in the main thread (as opposed to PolyglotThread) and
                             * cancel the other threads if context is not entered in the main
                             * thread. We should probably set exiting only just before we call the
                             * close.
                             */
                            context[0].eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(1),LOOP(infinity, STATEMENT))");
                            Assert.fail();
                        } catch (PolyglotException pe) {
                            if (!pe.isCancelled()) {
                                throw pe;
                            }
                        } finally {
                            context[0].leave();
                        }
                    }
                }

                @Override
                public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                }
            });
            try {
                context[0].initialize(InstrumentationTestLanguage.ID);
                future = executorService.submit(() -> {
                    try {
                        context[0].eval(InstrumentationTestLanguage.ID, "CONSTANT(42)");
                        Assert.fail();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                });
                mainThreadLatch.await();
                context[0].close(true);
                future.get();
            } finally {
                try {
                    context[0].close();
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            }
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }

    private static final Node DUMMY_NODE = new Node() {
    };

    @Test
    public void testMultipleContextDeadlock() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.create()) {
            try (Context context1 = Context.newBuilder().engine(engine).allowCreateThread(true).build(); Context context2 = Context.newBuilder().engine(engine).allowCreateThread(true).build()) {
                Phaser cancelSignal = new Phaser(2);
                Future<?> future1 = executorService.submit(() -> {
                    context1.enter();
                    TruffleInstrument.Env instrumentEnv = context1.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                    TruffleContext truffleContext = instrumentEnv.getEnteredContext();
                    context1.leave();
                    /*
                     * use internal enter to prevent automatic leave on close
                     */
                    Object prev = truffleContext.enter(DUMMY_NODE);
                    try {
                        cancelSignal.arriveAndAwaitAdvance();
                        /*
                         * Since we are entered in the current thread and multi-threading is
                         * enabled. Close should spawn a new thread where the close actually happens
                         * and that prevents deadlock.
                         */
                        context1.close(true);
                        cancelSignal.awaitAdvance(cancelSignal.arriveAndDeregister());
                        context2.enter();
                        context2.leave();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    } finally {
                        truffleContext.leave(DUMMY_NODE, prev);
                    }
                });
                Future<?> future2 = executorService.submit(() -> {
                    context1.enter();
                    try {
                        cancelSignal.arriveAndAwaitAdvance();
                        cancelSignal.awaitAdvance(cancelSignal.arriveAndDeregister());
                        context2.enter();
                        context2.leave();
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    } finally {
                        context1.leave();
                    }

                });
                future1.get();
                future2.get();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            } finally {
                executorService.shutdownNow();
                assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void testThreadInterruptInsideClose() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        /*
         * This test verifies that Context.close(true) does not swallow thread interrupt and so it
         * can still be used to terminate the thread that uses Context.close(true).
         */
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(STATEMENT)",
                            "InfiniteLoop").build();
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            CountDownLatch cancelLatch = new CountDownLatch(1);
            CountDownLatch cancelCalledLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(1);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext ctx, VirtualFrame frame) {
                    onEnterBoundary();
                }

                @CompilerDirectives.TruffleBoundary
                private void onEnterBoundary() {
                    cancelLatch.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try {
                            finishLatch.await();
                            break;
                        } catch (InterruptedException ie) {
                            interrupted = true;
                            cancelCalledLatch.countDown();
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                }
            });
            Future<?> infiniteLoopFuture = executorService.submit(() -> {
                try {
                    context.eval(source);
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            });
            cancelLatch.await();
            AtomicReference<Thread> cancelContextThread = new AtomicReference<>();
            Future<?> cancelContextThreadFuture = executorService.submit(() -> {
                cancelContextThread.set(Thread.currentThread());
                while (!Thread.interrupted()) {
                    context.close(true);
                }
            });
            cancelCalledLatch.await();
            cancelContextThread.get().interrupt();
            finishLatch.countDown();
            infiniteLoopFuture.get();
            cancelContextThreadFuture.get(100, TimeUnit.SECONDS);
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        }
    }

}
