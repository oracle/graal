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

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ContextInterruptCloseOrCancelTest extends AbstractPolyglotTest {
    private void runOnContextClose(TruffleContext triggerCtx, Runnable r) {
        instrumentEnv.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext ctx) {

            }

            @Override
            public void onLanguageContextCreated(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onLanguageContextInitialized(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onLanguageContextFinalized(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onLanguageContextDisposed(TruffleContext ctx, LanguageInfo lang) {

            }

            @Override
            public void onContextClosed(TruffleContext ctx) {
                if (ctx == triggerCtx) {
                    r.run();
                }
            }
        }, false);
    }

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testInterruptPolyglotThreadWhileClosing() throws ExecutionException, InterruptedException, IOException {
        enterContext = false;
        setupEnv(Context.newBuilder().allowCreateThread(true), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
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
            TruffleContext innerContext = languageEnv.newContextBuilder().build();
            AtomicReference<Future<?>> interruptFutureReference = new AtomicReference<>();
            runOnContextClose(innerContext, () -> {
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
                Assert.assertTrue(interruptedExceptionSwallowed);
                Assert.assertFalse(interruptFuture.isDone());
            });
            context.close();
            assertContextState("CLOSED");
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
        setupEnv(Context.newBuilder().allowCreateThread(true), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
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
            TruffleContext innerContext = languageEnv.newContextBuilder().build();
            runOnContextClose(innerContext, () -> {
                /*
                 * Cancel must work even when entered.
                 */
                context.close(true);
            });
            context.close();
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
}
