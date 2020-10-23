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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

public class ContextInterruptStandaloneTest {

    @Test
    public void testParallelCloseAndInterrupt() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try (Context context = Context.create()) {
            context.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch passLatch = new CountDownLatch(5);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", "InfiniteLoop").build();
            TruffleInstrument.Env instrumentEnv = getInstrumentEnv(context.getEngine());
            attachListener(passLatch::countDown, instrumentEnv);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        context.eval(source);
                        Assert.fail();
                    } catch (PolyglotException pe) {
                        if (!pe.isInterrupted() && !pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            passLatch.await();
            Random rnd = new Random();
            for (int i = 0; i < 5; i++) {
                futures.add(executorService.submit(() -> {
                    if (rnd.nextBoolean()) {
                        context.close(true);
                    } else {
                        try {
                            context.interrupt(Duration.ofSeconds(50));
                        } catch (TimeoutException te) {
                            throw new RuntimeException(te);
                        }
                    }
                }));
            }
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
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testInterruptTimeout() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try (Context context = Context.create()) {
            context.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch passLatch = new CountDownLatch(1);
            CountDownLatch interruptFinishLatch = new CountDownLatch(1);
            AtomicBoolean interruptFinished = new AtomicBoolean();
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "InfiniteLoop").build();
            TruffleInstrument.Env instrumentEnv = getInstrumentEnv(context.getEngine());
            attachListener(() -> {
                passLatch.countDown();
                while (!interruptFinished.get()) {
                    try {
                        interruptFinishLatch.await();
                    } catch (InterruptedException ie) {
                    }
                }
            }, instrumentEnv);
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executorService.submit(() -> {
                context.eval(source);
            }));
            passLatch.await();
            try {
                context.interrupt(Duration.ofSeconds(1));
                Assert.fail();
            } catch (TimeoutException te) {
                Assert.assertEquals("Interrupt timed out.", te.getMessage());
            }
            interruptFinished.set(true);
            interruptFinishLatch.countDown();
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
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testInterruptCurrentThreadEntered() throws IOException {
        Context[] context = new Context[1];
        context[0] = Context.create();
        try {
            attachListener(() -> {
                try {
                    context[0].interrupt(Duration.ofSeconds(100));
                } catch (TimeoutException te) {
                    throw new RuntimeException(te);
                }
            }, getInstrumentEnv(context[0].getEngine()));
            context[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(infinity,CONSTANT(42))", "SelfInterruptingScript").build();
            context[0].eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            Assert.assertEquals("java.lang.IllegalStateException: Cannot interrupt context from a thread where the context is active.", pe.getMessage());
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
                } catch (TimeoutException te) {
                    polyglotThreadException[0] = te;
                    throw new RuntimeException(te);
                } catch (IllegalStateException e) {
                    polyglotThreadException[0] = e;
                    if (!"Cannot interrupt context from a thread where its child context is active.".equals(e.getMessage())) {
                        throw e;
                    } else {
                        throw new RuntimeException(new InterruptedException());
                    }
                }
            }, getInstrumentEnv(context[0].getEngine()));
            context[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,LOOP(infinity,CONSTANT(42))),SPAWN(foo),JOIN())", "SelfInterruptingScript").build();
            context[0].eval(source);
            if (polyglotThreadException[0] != null) {
                throw polyglotThreadException[0];
            }
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalStateException);
            Assert.assertEquals("Cannot interrupt context from a thread where its child context is active.", e.getMessage());
        } finally {
            context[0].close();
        }
    }

    @Test
    public void testInterruptCurrentThreadNotEntered() throws TimeoutException {
        try (Context context = Context.create()) {
            context.interrupt(Duration.ofSeconds(100));
        }
    }

    static void attachListener(Runnable runnable, TruffleInstrument.Env instrumentEnv) {
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

    static TruffleInstrument.Env getInstrumentEnv(Engine engine) {
        return engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
    }
}
