/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Field;
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
import java.util.concurrent.atomic.AtomicReference;

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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ContextInterruptCloseCancelOrExitTest extends AbstractPolyglotTest {

    @Rule public TestName testNameRule = new TestName();

    public ContextInterruptCloseCancelOrExitTest() {
        needsInstrumentEnv = true;
        ignoreCancelOnClose = true;
        ignoreExitOnClose = true;
    }

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testInterruptPolyglotThreadWhileClosing() throws ExecutionException, InterruptedException, IOException {
        enterContext = false;
        AtomicReference<Runnable> runOnNaturalContextExit = new AtomicReference<>();
        ProxyLanguage proxyLanguage = new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
                if (exitMode == ExitMode.NATURAL && runOnNaturalContextExit.get() != null) {
                    runOnNaturalContextExit.get().run();
                }
            }
        };
        setupEnv(Context.newBuilder().allowCreateThread(true), proxyLanguage);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch interruptLatch = new CountDownLatch(1);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,CONSTANT(42),LOOP(infinity,STATEMENT)),SPAWN(foo))", "InfiniteLoop").build();
            attachListener(interruptLatch::countDown, instrumentEnv);
            Future<?> future = executorService.submit(() -> {
                context.eval(source);
            });
            interruptLatch.await();
            future.get();
            AtomicReference<Future<?>> interruptFutureReference = new AtomicReference<>();
            runOnNaturalContextExit.set(() -> {
                /*
                 * When inner context is closed we know that the parent context is being closed.
                 * Parent context is entered in the current thread, so we must interrupt in a
                 * different thread.
                 */
                Future<?> interruptFuture = executorService.submit(() -> {
                    try {
                        context.interrupt(Duration.ofSeconds(5));
                    } catch (TimeoutException te) {
                        throw new AssertionError(te);
                    }
                });
                interruptFutureReference.set(interruptFuture);
                boolean interruptedExceptionSwallowed = false;
                try {
                    /*
                     * The parent context is entered in the current thread, so the interrupt will
                     * try to interrupt it. Therefore, we have to ignore the InterruptedException in
                     * order for the close operation to be able to successfully continue. We cannot
                     * wait for the interrupt future here though, because it waits also for the
                     * current entered thread.
                     */
                    interruptFuture.get();
                } catch (ExecutionException e) {
                    throw new AssertionError(e);
                } catch (InterruptedException ie) {
                    interruptedExceptionSwallowed = true;
                }
                assertTrue(interruptedExceptionSwallowed);
                Assert.assertFalse(interruptFuture.isDone());
            });
            context.close();
            assertContextState("CLOSED_INTERRUPTED");
            Assert.assertNotNull(interruptFutureReference.get());
            interruptFutureReference.get().get();
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancelPolyglotThreadWhileClosing() throws ExecutionException, InterruptedException, IOException {
        enterContext = false;
        AtomicReference<Runnable> runOnNaturalContextExit = new AtomicReference<>();
        ProxyLanguage proxyLanguage = new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
                if (exitMode == ExitMode.NATURAL && runOnNaturalContextExit.get() != null) {
                    runOnNaturalContextExit.get().run();
                }
            }
        };
        setupEnv(Context.newBuilder().allowCreateThread(true), proxyLanguage);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch cancelLatch = new CountDownLatch(1);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,CONSTANT(42),LOOP(infinity,STATEMENT)),SPAWN(foo))", "InfiniteLoop").build();
            attachListener(cancelLatch::countDown, instrumentEnv);
            Future<?> future = executorService.submit(() -> {
                context.eval(source);
            });
            cancelLatch.await();
            future.get();
            runOnNaturalContextExit.set(() -> {
                /*
                 * Cancel must work even when entered.
                 */
                context.close(true);
            });
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            assertContextState("CLOSED_CANCELLED");
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    private void assertContextState(String expectedState) {
        Object contextState;
        try {
            Field implField = context.getClass().getDeclaredField("receiver");
            implField.setAccessible(true);
            Object contextImpl = implField.get(context);
            Field stateField = contextImpl.getClass().getDeclaredField("state");
            stateField.setAccessible(true);
            contextState = stateField.get(contextImpl);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(expectedState, ((Enum<?>) contextState).name());
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

    static TruffleInstrument.Env getInstrumentEnv(Engine engine) {
        return engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
    }

    static Context staticCtx;

    public static void callExit() {
        try {
            staticCtx.eval(InstrumentationTestLanguage.ID, "EXIT(1)");
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testNestedExitOnHost() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, InstrumentationTestLanguage.ID).allowHostClassLoading(true).allowHostClassLookup((s) -> true).allowHostAccess(HostAccess.ALL),
                        new ProxyLanguage() {
                            @Override
                            protected CallTarget parse(ParsingRequest request) {
                                return new RootNode(languageInstance) {
                                    @Override
                                    public Object execute(VirtualFrame frame) {
                                        LanguageContext languageContext = LanguageContext.get(this);
                                        Object javaClass = languageContext.getEnv().lookupHostSymbol(ContextInterruptCloseCancelOrExitTest.class.getName());
                                        try {
                                            InteropLibrary.getUncached().invokeMember(javaClass, "callExit");
                                        } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                                            throw new RuntimeException(e);
                                        }
                                        return 0;
                                    }
                                }.getCallTarget();
                            }

                            @Override
                            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                                return true;
                            }

                        });
        staticCtx = context;
        try {
            context.eval(ProxyLanguage.ID, "");
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
        try {
            context.close();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testCancelDuringExitNotification() throws ExecutionException, InterruptedException {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        enterContext = false;
        Object waitObject = new Object();
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowHostClassLoading(true).allowHostClassLookup((s) -> true).allowHostAccess(HostAccess.ALL),
                        getWaitOnExitLanguage(cancelLatch, waitObject));

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executorService.submit(() -> {
                try {
                    context.eval(ProxyLanguage.ID, "");
                    Assert.fail();
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                }
            });
            cancelLatch.await();
            context.close(true);
            future.get();
            assertContextState("CLOSED_CANCELLED");
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Test
    public void testInterruptDuringExitNotification() throws ExecutionException, InterruptedException {
        CountDownLatch interruptLatch = new CountDownLatch(1);
        Object waitObject = new Object();
        enterContext = false;
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowHostClassLoading(true).allowHostClassLookup((s) -> true).allowHostAccess(HostAccess.ALL),
                        getWaitOnExitLanguage(interruptLatch, waitObject));

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executorService.submit(() -> {
                try {
                    context.eval(ProxyLanguage.ID, "");
                    Assert.fail();
                } catch (PolyglotException pe) {
                    if (!pe.isExit()) {
                        throw pe;
                    }
                }
            });
            interruptLatch.await();
            try {
                context.interrupt(Duration.ofSeconds(1));
                Assert.fail();
            } catch (TimeoutException e) {
            }
            synchronized (waitObject) {
                waitObject.notifyAll();
            }
            future.get();
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isExit()) {
                    throw pe;
                }
            }
            assertContextState("CLOSED_EXITED");
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    private static ProxyLanguage getWaitOnExitLanguage(CountDownLatch signalWaitingStarted, Object waitObject) {
        return new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) {
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        LanguageContext languageContext = LanguageContext.get(this);
                        languageContext.getEnv().getContext().closeExited(this, 1);
                        return 0;
                    }
                }.getCallTarget();
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
                waitIndefinitely();
            }

            @CompilerDirectives.TruffleBoundary
            private void waitIndefinitely() {
                try {
                    synchronized (waitObject) {
                        signalWaitingStarted.countDown();
                        waitObject.wait();
                    }
                } catch (InterruptedException ie) {
                    throw new AssertionError(ie);
                }
            }
        };
    }

    @Test
    public void testExitWhileClosing() throws ExecutionException, InterruptedException, IOException {
        enterContext = false;
        AtomicReference<Runnable> runOnNaturalContextExit = new AtomicReference<>();
        ProxyLanguage proxyLanguage = new ProxyLanguage() {

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }

            @Override
            protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
                if (exitMode == ExitMode.NATURAL && runOnNaturalContextExit.get() != null) {
                    runOnNaturalContextExit.get().run();
                }
            }
        };
        setupEnv(Context.newBuilder().allowCreateThread(true), proxyLanguage);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch exitLatch = new CountDownLatch(1);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,CONSTANT(42),LOOP(infinity,STATEMENT)),SPAWN(foo))", "InfiniteLoop").build();
            attachListener(exitLatch::countDown, instrumentEnv);
            Future<?> future = executorService.submit(() -> {
                context.eval(source);
            });
            exitLatch.await();
            future.get();
            runOnNaturalContextExit.set(() -> {
                try {
                    context.eval(InstrumentationTestLanguage.ID, "EXIT(1)");
                } catch (PolyglotException pe) {
                    if (!pe.isExit()) {
                        throw pe;
                    }
                }
            });
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isExit()) {
                    throw pe;
                }
            }
            assertContextState("CLOSED_EXITED");
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    static AtomicReference<Runnable> runOnFinalizeContext;

    @After
    public void clearRunOnFinalizeContext() {
        runOnFinalizeContext = null;
    }

    @TruffleLanguage.Registration(dependentLanguages = InstrumentationTestLanguage.ID)
    static class ExecuteCustomCodeWhileClosingTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = TestUtils.getDefaultLanguageId(ExecuteCustomCodeWhileClosingTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected void finalizeContext(TruffleLanguage.Env context) {
            if (runOnFinalizeContext != null && runOnFinalizeContext.get() != null) {
                runOnFinalizeContext.get().run();
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testExitWhileFinalizing() throws ExecutionException, InterruptedException, IOException {
        enterContext = false;
        runOnFinalizeContext = new AtomicReference<>();
        ProxyLanguage proxyLanguage = new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        };
        setupEnv(Context.newBuilder().allowCreateThread(true), proxyLanguage);
        context.initialize(ExecuteCustomCodeWhileClosingTestLanguage.ID);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch exitLatch = new CountDownLatch(1);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,CONSTANT(42),LOOP(infinity,STATEMENT)),SPAWN(foo))", "InfiniteLoop").build();
            attachListener(exitLatch::countDown, instrumentEnv);
            Future<?> future = executorService.submit(() -> {
                context.eval(source);
            });
            exitLatch.await();
            future.get();
            runOnFinalizeContext.set(() -> {
                // No effect calling context exit during closing finalization
                context.eval(InstrumentationTestLanguage.ID, "EXIT(1)");
                context.close(true);
            });
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            assertContextState("CLOSED_CANCELLED");
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testParallelCloseCancelExitAndInterrupt() throws InterruptedException, IOException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(31);
        Context ctx = Context.newBuilder().build();
        try {
            ctx.initialize(InstrumentationTestLanguage.ID);
            CountDownLatch passLatch = new CountDownLatch(10);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "BLOCK(CONSTANT(42),LOOP(infinity, STATEMENT))", "InfiniteLoop").build();
            TruffleInstrument.Env instrEnv = getInstrumentEnv(ctx.getEngine());
            attachListener(passLatch::countDown, instrEnv);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executorService.submit(() -> {
                    try {
                        ctx.eval(source);
                        Assert.fail();
                    } catch (PolyglotException pe) {
                        if (!pe.isInterrupted() && !pe.isCancelled() && !pe.isExit()) {
                            throw pe;
                        }
                    }
                }));
            }
            passLatch.await();
            Random rnd = new Random();
            AtomicBoolean exitOrCancel = new AtomicBoolean();
            CountDownLatch safetyGuardLatch = new CountDownLatch(20);
            for (int i = 0; i < 20; i++) {
                futures.add(executorService.submit(() -> {
                    int randInt = rnd.nextInt(4);
                    switch (randInt) {
                        case 0:
                            exitOrCancel.set(true);
                            safetyGuardLatch.countDown();
                            ctx.close(true);
                            break;
                        case 1:
                            safetyGuardLatch.countDown();
                            try {
                                ctx.interrupt(Duration.ofSeconds(50));
                            } catch (TimeoutException te) {
                                throw new AssertionError(te);
                            }
                            break;
                        case 2:
                            safetyGuardLatch.countDown();
                            try {
                                ctx.close();
                            } catch (IllegalStateException ise) {
                                if (!"The context is currently executing on another thread. Set cancelIfExecuting to true to stop the execution on this thread.".equals(ise.getMessage()) &&
                                                !"Another main thread was started while closing a polyglot context!".equals(ise.getMessage())) {
                                    throw ise;
                                }
                            } catch (PolyglotException pe) {
                                /*
                                 * CLOSING_CANCELLING and CLOSING_EXITING states can be set when
                                 * normal close initiated for non-invalid context is in progress. In
                                 * such case, the enter operation done as a part of normal close can
                                 * throw exit or cancel exception.
                                 */
                                if (!pe.isCancelled() && !pe.isExit()) {
                                    throw pe;
                                }
                            }
                            break;
                        case 3:
                            exitOrCancel.set(true);
                            safetyGuardLatch.countDown();
                            try {
                                ctx.eval(InstrumentationTestLanguage.ID, "EXIT(1)");
                            } catch (PolyglotException pe) {
                                if (!pe.isCancelled() && !pe.isExit() && !pe.isInterrupted()) {
                                    throw pe;
                                }
                            } catch (IllegalStateException ise) {
                                if (!"The Context is already closed.".equals(ise.getMessage())) {
                                    throw ise;
                                }
                            }
                            break;
                    }
                }));
            }
            futures.add(executorService.submit(() -> {
                try {
                    safetyGuardLatch.await();
                } catch (InterruptedException ie) {
                }
                if (!exitOrCancel.get()) {
                    ctx.close(true);
                }
            }));
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            try {
                ctx.close();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled() && !pe.isExit()) {
                    throw pe;
                }
            } finally {
                executorService.shutdownNow();
                assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
            }
        }
    }

    private static final Node DUMMY_NODE = new Node() {
    };

    @Test
    public void testExitFromExitNotification() {
        setupEnv(Context.newBuilder(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) {
                return new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        LanguageContext languageContext = LanguageContext.get(this);
                        languageContext.getEnv().getContext().closeExited(this, 1);
                        return 0;
                    }
                }.getCallTarget();
            }

            @Override
            protected void exitContext(LanguageContext ctx, ExitMode exitMode, int exitCode) {
                ctx.getEnv().getContext().closeExited(DUMMY_NODE, 5);
            }

        });
        try {
            context.eval(ProxyLanguage.ID, "");
        } catch (PolyglotException pe) {
            if (!pe.isExit() || pe.getExitStatus() != 1) {
                throw pe;
            }
        }
    }
}
