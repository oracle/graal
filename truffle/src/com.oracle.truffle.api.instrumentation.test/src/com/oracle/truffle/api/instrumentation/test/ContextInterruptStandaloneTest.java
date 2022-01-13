/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ContextInterruptStandaloneTest extends AbstractPolyglotTest {

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testCancelDuringHostSleep() throws ExecutionException, InterruptedException {
        CountDownLatch beforeSleep = new CountDownLatch(1);
        enterContext = false;
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowHostClassLookup((s) -> true).allowHostAccess(HostAccess.ALL),
                        new ProxyLanguage() {
                            @Override
                            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                                return new RootNode(languageInstance) {
                                    @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(1);

                                    @Override
                                    public Object execute(VirtualFrame frame) {
                                        callHostSleep();
                                        return 0;
                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callHostSleep() {
                                        Object javaThread = LanguageContext.get(this).getEnv().lookupHostSymbol("java.lang.Thread");
                                        beforeSleep.countDown();
                                        try {
                                            library.invokeMember(javaThread, "sleep", 10000);
                                        } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                                            throw new AssertionError(e);
                                        }
                                    }
                                }.getCallTarget();
                            }

                            @Override
                            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                                return true;
                            }
                        });
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<?> future = executorService.submit(() -> {
                try {
                    context.eval(ProxyLanguage.ID, "");
                    Assert.fail();
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled() || pe.isInterrupted()) {
                        throw pe;
                    }
                }
            });
            beforeSleep.await();
            context.close(true);
            future.get();
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    private static Context staticContext;

    public static void callStaticContextCancel(boolean nestedContextEntered) {
        if (nestedContextEntered) {
            try (Context c = Context.create()) {
                c.enter();
                staticContext.close(true);
            }
        } else {
            staticContext.close(true);
        }
    }

    @Test
    public void testCancelFromHostCall() {
        testCancelFromHostCall(false);
    }

    @Test
    public void testCancelFromHostCallWithNestedContextEntered() {
        testCancelFromHostCall(true);
    }

    private void testCancelFromHostCall(boolean nestedContextEntered) {
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowHostClassLookup((s) -> true).allowHostAccess(HostAccess.ALL),
                        new ProxyLanguage() {
                            @Override
                            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                                return new RootNode(languageInstance) {
                                    @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(1);

                                    @Override
                                    public Object execute(VirtualFrame frame) {
                                        Object thisTestClass = ProxyLanguage.LanguageContext.get(this).getEnv().lookupHostSymbol(ContextInterruptStandaloneTest.class.getName());
                                        try {
                                            library.invokeMember(thisTestClass, "callStaticContextCancel", nestedContextEntered);
                                        } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                                            throw new AssertionError(e);
                                        }
                                        return 0;
                                    }
                                }.getCallTarget();
                            }
                        });
        try {
            staticContext = context;
            context.eval(ProxyLanguage.ID, "");
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        } finally {
            staticContext = null;
        }
    }

    @Test
    public void testListenerInterruptCausedByCancel() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try (Context ctx = Context.create()) {
            ctx.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch beforeSleep = new CountDownLatch(1);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "InfiniteLoop").build();
            TruffleInstrument.Env instrEnv = getInstrumentEnv(ctx.getEngine());
            attachListener(new Runnable() {
                @Override
                public void run() {
                    beforeSleep.countDown();
                    try {
                        Thread.sleep(10000);
                        Assert.fail();
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                }
            }, instrEnv);
            Future<?> future = executorService.submit(() -> {
                try {
                    ctx.eval(source);
                    Assert.fail();
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled() || pe.isInterrupted()) {
                        throw pe;
                    }
                }
            });
            beforeSleep.await();
            ctx.close(true);
            future.get();
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testParallelCloseAndInterrupt() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try (Context ctx = Context.create()) {
            ctx.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch passLatch = new CountDownLatch(5);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", "InfiniteLoop").build();
            TruffleInstrument.Env instrEnv = getInstrumentEnv(ctx.getEngine());
            attachListener(passLatch::countDown, instrEnv);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        ctx.eval(source);
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
                        ctx.close(true);
                    } else {
                        try {
                            ctx.interrupt(Duration.ofSeconds(50));
                        } catch (TimeoutException te) {
                            throw new AssertionError(te);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    private static final Node DUMMY_NODE = new Node() {
    };

    @Test
    public void testInterruptTimeout() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try (Context ctx = Context.create()) {
            ctx.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch passLatch = new CountDownLatch(1);
            CountDownLatch interruptFinishLatch = new CountDownLatch(1);
            AtomicBoolean interruptFinished = new AtomicBoolean();
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", "InfiniteLoop").build();
            TruffleInstrument.Env instrEnv = getInstrumentEnv(ctx.getEngine());
            attachListener(() -> {
                passLatch.countDown();
                while (!interruptFinished.get()) {
                    try {
                        TruffleSafepoint.setBlockedThreadInterruptible(DUMMY_NODE, new TruffleSafepoint.Interruptible<CountDownLatch>() {
                            @Override
                            public void apply(CountDownLatch arg) throws InterruptedException {
                                if (!interruptFinished.get()) {
                                    arg.await();
                                }
                            }
                        }, interruptFinishLatch);
                    } catch (Exception ie) {
                        if (InteropLibrary.getUncached().isException(ie)) {
                            try {
                                if (InteropLibrary.getUncached().getExceptionType(ie) != ExceptionType.INTERRUPT) {
                                    throw ie;
                                }
                            } catch (UnsupportedMessageException ume) {
                                throw new AssertionError(ume);
                            }
                        }
                    }
                }
            }, instrEnv);
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executorService.submit(() -> {
                ctx.eval(source);
            }));
            passLatch.await();
            try {
                ctx.interrupt(Duration.ofSeconds(1));
                Assert.fail();
            } catch (TimeoutException te) {
                Assert.assertEquals("Interrupt timed out.", te.getMessage());
            }
            interruptFinished.set(true);
            interruptFinishLatch.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testInterruptCurrentThreadEntered() throws IOException {
        Context[] ctx = new Context[1];
        ctx[0] = Context.create();
        try {
            attachListener(() -> {
                try {
                    ctx[0].interrupt(Duration.ofSeconds(100));
                } catch (TimeoutException te) {
                    throw new AssertionError(te);
                }
            }, getInstrumentEnv(ctx[0].getEngine()));
            ctx[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(infinity,CONSTANT(42))", "SelfInterruptingScript").build();
            ctx[0].eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            Assert.assertEquals("java.lang.IllegalStateException: Cannot interrupt context from a thread where the context is active.", pe.getMessage());
        } finally {
            ctx[0].close();
        }
    }

    @Test
    public void testInterruptCurrentThreadEnteredByChild() {
        Context[] ctx = new Context[1];
        ctx[0] = Context.newBuilder().allowCreateThread(true).build();
        Exception[] polyglotThreadException = new Exception[1];
        try {
            attachListener(() -> {
                try {
                    ctx[0].interrupt(Duration.ofSeconds(100));
                } catch (TimeoutException te) {
                    polyglotThreadException[0] = te;
                    throw new AssertionError(te);
                } catch (IllegalStateException e) {
                    polyglotThreadException[0] = e;
                    if (!"Cannot interrupt context from a thread where its child context is active.".equals(e.getMessage())) {
                        throw e;
                    } else {
                        throw new RuntimeException(new InterruptedException());
                    }
                }
            }, getInstrumentEnv(ctx[0].getEngine()));
            ctx[0].initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,LOOP(infinity,CONSTANT(42))),SPAWN(foo),JOIN())", "SelfInterruptingScript").build();
            ctx[0].eval(source);
            if (polyglotThreadException[0] != null) {
                throw polyglotThreadException[0];
            }
            Assert.fail();
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException) || !"Cannot interrupt context from a thread where its child context is active.".equals(e.getMessage())) {
                throw new AssertionError(e);
            }
        } finally {
            ctx[0].close();
        }
    }

    @Test
    public void testInterruptCurrentThreadNotEntered() throws TimeoutException {
        try (Context ctx = Context.create()) {
            ctx.interrupt(Duration.ofSeconds(100));
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
