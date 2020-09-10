/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

public class ContextInterruptTest {
    @Test
    public void testInterruptDuringInfiniteLoop() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopInInnerContext() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 1, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopWithPolyglotThreads() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 1, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopWithPolyglotThreadWhereParentContextIsNotEntered() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(CONTEXT(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN()),LOOP(infinity, STATEMENT))", 1, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreaded() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopInInnerContextMultiThreaded() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("CONTEXT(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreadedWithPolyglotThreads() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, false, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreadedMultiContext() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreadedMultiContextWithPolyglotThreads() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, false);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreadedMultiContextMultiEngine() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", 10, true, true);
    }

    @Test
    public void testInterruptDuringInfiniteLoopMultiThreadedMultiContextMultiEngineWithPolyglotThreads() throws InterruptedException, IOException, ExecutionException {
        testInterruptDuringInfiniteLoop("BLOCK(DEFINE(foo,CONSTANT(42),LOOP(infinity, STATEMENT)),SPAWN(foo),JOIN(),LOOP(infinity, STATEMENT))", 10, true, true);
    }

    static class PassCounter implements Runnable {
        int passCount;

        @Override
        public void run() {
            synchronized (this) {
                passCount++;
                notifyAll();
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void testInterruptDuringInfiniteLoop(String code, int nThreads, boolean multiContext, boolean multiEngine) throws InterruptedException, IOException, ExecutionException {
        Thread mainThread = Thread.currentThread();
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        CountDownLatch threadsStarted = new CountDownLatch(nThreads);
        CountDownLatch allCancelledLatch = new CountDownLatch(nThreads + 1);
        List<Context> contexts = new ArrayList<>();
        Engine engine = multiEngine ? null : Engine.create();
        Context.Builder builder = Context.newBuilder().allowCreateThread(true);
        final PassCounter passCounter = new PassCounter();
        if (engine != null) {
            builder.engine(engine);
            TruffleInstrument.Env instrumentEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            attachListener(passCounter, instrumentEnv);
        }
        for (int i = 0; i < nThreads; i++) {
            if (multiContext || i == 0) {
                Context ctx = builder.build();
                contexts.add(ctx);
                if (engine == null) {
                    TruffleInstrument.Env instrumentEnv = ctx.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                    attachListener(passCounter, instrumentEnv);
                }
            } else {
                contexts.add(contexts.get(i - 1));
            }
        }
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                int j = i;
                futures.add(executorService.submit(() -> {
                    Context context = contexts.get(j);
                    try {
                        context.initialize(InstrumentationTestLanguage.ID);
                        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "InfiniteLoop").build();
                        threadsStarted.countDown();
                        context.eval(source);
                        Assert.fail();
                    } catch (PolyglotException pe) {
                        if (!pe.isInterrupted()) {
                            throw pe;
                        }
                        mainThread.interrupt();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    allCancelledLatch.countDown();
                    try {
                        allCancelledLatch.await();
                    } catch (InterruptedException ie) {
                    }
                    /*
                     * Verify the context is still alive in thread that was cancelled. The
                     * cancellation must be done by now or the verification could get cancelled.
                     */
                    try {
                        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "CheckAlive1").build();
                        Assert.assertEquals(42, context.eval(source).asInt());
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }

                }));
            }
            threadsStarted.await();
            synchronized (passCounter) {
                while (passCounter.passCount != nThreads) {
                    passCounter.wait(1000);
                }
            }
            for (int i = 0; i < nThreads; i++) {
                if (multiContext || i == 0) {
                    Context context = contexts.get(i);
                    context.interrupt(Duration.ofSeconds(100));
                }
            }
            allCancelledLatch.countDown();
            for (Future<?> future : futures) {
                boolean finished = false;
                do {
                    try {
                        future.get();
                        finished = true;
                    } catch (InterruptedException e) {
                    }
                } while (!finished);
            }
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
            /*
             * Verify the context is still alive in the cancelling thread.
             */
            for (int i = 0; i < nThreads; i++) {
                if (multiContext || i == 0) {
                    Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "CheckAlive2").build();
                    Assert.assertEquals(42, contexts.get(i).eval(source).asInt());
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

    private static void attachListener(Runnable runnable, TruffleInstrument.Env instrumentEnv) {
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(), new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                executeSideEffect();
            }

            @CompilerDirectives.TruffleBoundary
            void executeSideEffect() {
                runnable.run();
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

            }
        });
    }

    @Test
    public void testInterruptCurrentThreadEntered() throws IOException {
        Context[] context = new Context[1];
        context[0] = Context.create();
        try {
            attachListener(() -> context[0].interrupt(Duration.ofSeconds(100)),
                            context[0].getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class));
            context[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(infinity,CONSTANT(42))", "SelfInterruptingScript").build();
            context[0].eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            Assert.assertEquals("java.lang.IllegalStateException: Cannot interrupt context from a thread where the context is entered.", pe.getMessage());
        } finally {
            context[0].close();
        }
    }

    @Test
    public void testInterruptCurrentThreadEnteredByChild() {
        Context[] context = new Context[1];
        context[0] = Context.newBuilder().allowCreateThread(true).build();
        Exception[] polyglotThreadException = new Exception[1];
        try {
            attachListener(() -> {
                try {
                    context[0].interrupt(Duration.ofSeconds(100));
                } catch (Exception e) {
                    polyglotThreadException[0] = e;
                    throw e;
                }
            }, context[0].getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class));
            context[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,LOOP(infinity,CONSTANT(42))),SPAWN(foo),JOIN())", "SelfInterruptingScript").build();
            context[0].eval(source);
            if (polyglotThreadException[0] != null) {
                throw polyglotThreadException[0];
            }
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalStateException);
            Assert.assertEquals("Cannot interrupt context from a thread where its child context is entered.", e.getMessage());
        } finally {
            context[0].close();
        }
    }

    @Test
    public void testInterruptCurrentThreadNotEntered() {
        try (Context context = Context.create()) {
            context.interrupt(Duration.ofSeconds(100));
        }
    }

    @Test
    public void testInnerContextNotEnteredOuterContext() throws IOException, InterruptedException {
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            AtomicReference<TruffleLanguage.Env> envReference = new AtomicReference<>();
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext c, VirtualFrame frame) {
                    envReference.set(InstrumentationTestLanguage.currentEnv());
                }

                @Override
                public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                }
            });
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT", "OneStatement").build();
            context.eval(source);
            Thread t = new Thread(() -> {
                TruffleContext truffleContext = envReference.get().newContextBuilder().build();
                Object prev = truffleContext.enter();
                truffleContext.leave(prev);
            });
            t.start();
            t.join();
        }
    }

    @Test
    public void testInnerContextPolyglotThreadNotEnteredOuterContext() throws IOException {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext c, VirtualFrame frame) {

                }

                @Override
                public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                }
            });
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,STATEMENT),SPAWN(foo),JOIN())", "SpawnedStatementInInnerContext").build();
            context.eval(source);
        }
    }
}
