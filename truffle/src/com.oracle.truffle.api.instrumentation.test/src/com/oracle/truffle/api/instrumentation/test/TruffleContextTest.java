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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.ThreadsActivationListener;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.LanguageSPIOrderTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

public class TruffleContextTest extends AbstractPolyglotTest {

    @Rule public TestName testNameRule = new TestName();

    public TruffleContextTest() {
        needsLanguageEnv = true;
        needsInstrumentEnv = true;
    }

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testCreate() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();
        assertNotEquals(tc, languageEnv.getContext());
        assertFalse(tc.isEntered());
        assertFalse(tc.isClosed());
        assertFalse(tc.isCancelling());
        assertFalse(tc.isExiting());
        assertNotNull(tc.toString());
        assertEquals(tc.getParent(), languageEnv.getContext());

        Object prev = tc.enter(null);
        assertTrue(tc.isEntered());
        assertFalse(tc.isClosed());
        tc.leave(null, prev);

        assertFalse(tc.isEntered());
        assertFalse(tc.isClosed());
        tc.close();
    }

    @Test
    public void testSimpleForceClose() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();
        assertFalse(tc.isClosed());
        assertFalse(tc.isCancelling());
        tc.closeCancelled(null, "testreason");
        assertTrue(tc.isClosed());
        assertFalse(tc.isCancelling());
    }

    @Test
    public void testParallelForceClose() throws InterruptedException {
        setupEnv(Context.newBuilder().allowAllAccess(true).option("engine.TriggerUncaughtExceptionHandlerForCancel", "true").build(),
                        new ProxyLanguage() {

                            @Override
                            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                                return true;
                            }
                        });

        TruffleContext tc = languageEnv.newContextBuilder().build();
        List<Thread> threads = new ArrayList<>();
        List<AtomicReference<Throwable>> exceptions = new ArrayList<>();
        Semaphore waitUntilStart = new Semaphore(0);
        for (int i = 0; i < 100; i++) {
            Thread t = languageEnv.createThread(() -> {
                com.oracle.truffle.api.source.Source s = com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID, "EXPRESSION", "").build();
                CallTarget target = LanguageContext.get(null).getEnv().parsePublic(s);
                while (true) {
                    target.call();

                    // at least one thread should have started execution
                    waitUntilStart.release();
                }
            }, tc);
            AtomicReference<Throwable> exception = new AtomicReference<>();
            t.setUncaughtExceptionHandler((thread, e) -> {
                exception.set(e);
            });
            exceptions.add(exception);
            t.start();
            threads.add(t);
        }
        // 10s ought to be enough for anybody
        if (!waitUntilStart.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
            for (AtomicReference<Throwable> e : exceptions) {
                if (e.get() != null) {
                    throw new AssertionError(e.get());
                }
            }
            throw new AssertionError("failed to wait for execution");
        }

        assertFalse(tc.isClosed());
        for (int i = 0; i < threads.size(); i++) {
            assertNull(exceptions.get(i).get());
        }
        tc.closeCancelled(null, "testreason");

        for (int i = 0; i < threads.size(); i++) {
            threads.get(i).join();
        }

        for (int i = 0; i < threads.size(); i++) {
            Throwable e = exceptions.get(i).get();
            assertNotNull(e);
            assertEquals(getCancelExecutionClass(), e.getClass());
            assertEquals("testreason", e.getMessage());
            assertTrue(tc.isClosed());
        }

    }

    @Test
    public void testCloseInEntered() {
        setupEnv();

        TruffleContext tc = languageEnv.newContextBuilder().build();

        Node node = new Node() {
        };

        Object prev = tc.enter(null);

        assertFails(() -> tc.close(), IllegalStateException.class);

        assertFails(() -> tc.closeCancelled(node, "testreason"), getCancelExecutionClass(), (e) -> {
            assertSame(getCancelExecutionLocation(e), node);
            assertEquals("testreason", ((Throwable) e).getMessage());
        });

        assertFails(() -> tc.closeResourceExhausted(node, "testreason"), getCancelExecutionClass(), (e) -> {
            assertSame(getCancelExecutionLocation(e), node);
            assertEquals("testreason", ((Throwable) e).getMessage());
        });

        tc.leave(null, prev);
    }

    @Test
    public void testCancelledAndResourceExhausted() throws InterruptedException {
        setupEnv();

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                context.eval(InstrumentationTestLanguage.ID, "LOOP(infinity, STATEMENT)");
            } catch (Throwable e) {
                error.set(e);
            }
        });
        context.leave(); // avoid need for multi-threading

        AtomicReference<TruffleContext> enter = new AtomicReference<>();
        Semaphore waitUntilEntered = new Semaphore(0);
        instrumentEnv.getInstrumenter().attachThreadsActivationListener(new ThreadsActivationListener() {
            @TruffleBoundary
            public void onEnterThread(TruffleContext tc) {
                enter.set(tc);
                waitUntilEntered.release();
            }

            public void onLeaveThread(TruffleContext tc) {
            }
        });
        t.start();

        if (!waitUntilEntered.tryAcquire(10000, TimeUnit.MILLISECONDS)) {
            throw new AssertionError(error.get());
        }

        TruffleContext tc = enter.get();
        tc.closeResourceExhausted(null, "testError");
        t.join();

        assertNotNull(error.get());
        assertTrue(error.get().toString(), error.get() instanceof PolyglotException);
        PolyglotException e = (PolyglotException) error.get();
        assertEquals("testError", e.getMessage());
        assertTrue(e.isCancelled());
        assertTrue(e.isResourceExhausted());
    }

    @Test
    public void testCancelling() throws ExecutionException, InterruptedException {
        setupEnv();

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            context.leave(); // avoid need for multi-threading

            AtomicReference<TruffleContext> entered = new AtomicReference<>();
            CountDownLatch waitUntilStatementExecuted = new CountDownLatch(1);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @TruffleBoundary
                @Override
                public void onEnter(EventContext ctx, VirtualFrame frame) {
                    entered.set(instrumentEnv.getEnteredContext());
                    waitUntilStatementExecuted.countDown();
                }

                @Override
                public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                }
            });
            Future<?> future = executorService.submit(() -> {
                context.enter();
                try {
                    context.eval(InstrumentationTestLanguage.ID, "LOOP(infinity, STATEMENT)");
                    fail();
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled() || !pe.isResourceExhausted()) {
                        throw pe;
                    }
                    assertEquals("testError", pe.getMessage());
                    assertTrue(entered.get().isCancelling());
                } finally {
                    context.leave();
                }
            });

            waitUntilStatementExecuted.await();

            TruffleContext tc = entered.get();
            assertFalse(tc.isCancelling());
            tc.closeResourceExhausted(null, "testError");

            future.get();
            assertFalse(tc.isCancelling());
            assertTrue(tc.isClosed());
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testExiting() throws ExecutionException, InterruptedException {
        setupEnv(Context.newBuilder(), new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            AtomicReference<TruffleContext> entered = new AtomicReference<>();
            CountDownLatch waitUntilExited = new CountDownLatch(1);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @TruffleBoundary
                @Override
                public void onEnter(EventContext ctx, VirtualFrame frame) {
                    entered.set(instrumentEnv.getEnteredContext());
                }

                @Override
                public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                }
            });
            Future<?> future = executorService.submit(() -> {
                context.enter();
                try {
                    context.eval(InstrumentationTestLanguage.ID, "ROOT(STATEMENT,EXIT(1))");
                    fail();
                } catch (PolyglotException pe) {
                    if (!pe.isExit()) {
                        throw pe;
                    }
                    assertEquals(1, pe.getExitStatus());
                    assertTrue(entered.get().isExiting());
                } finally {
                    context.leave();
                }
                waitUntilExited.countDown();
            });

            boolean othrerThreadExited = false;
            while (!othrerThreadExited) {
                try {
                    waitUntilExited.await();
                    othrerThreadExited = true;
                } catch (InterruptedException ie) {
                }
            }
            /*
             * Multi-threading is necessary, otherwise the context is closed while entered and we
             * cannot check isExiting().
             */
            context.leave();
            TruffleContext tc = entered.get();
            tc.close();

            future.get();
            assertFalse(tc.isExiting());
            assertTrue(tc.isClosed());
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancellingUncaughtExceptionHandler() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        setupEnv(Context.newBuilder().allowAllAccess(true).err(out).build(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) {
                RootNode rootNode;
                String command = request.getSource().getCharacters().toString();
                switch (command) {
                    case "controller":
                        rootNode = new ControllerNode(languageInstance);
                        break;
                    case "worker":
                        rootNode = new WorkerNode(languageInstance);
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("Unknown request: " + command);
                }
                return rootNode.getCallTarget();
            }
        });
        context.eval(Source.newBuilder(ProxyLanguage.ID, "controller", "test").buildLiteral());
        assertFalse(out.toString().contains(getCancelExecutionClass().getName()));
    }

    @Test
    public void testContextHierarchy() {
        setupEnv();

        TruffleContext tc1 = languageEnv.newContextBuilder().build();
        TruffleContext tc2 = languageEnv.newContextBuilder().build();

        assertFalse(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        Object prev1 = tc1.enter(null);

        assertTrue(tc1.isActive());
        assertTrue(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        Object prev2 = tc2.enter(null);

        assertTrue(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertTrue(tc2.isActive());
        assertTrue(tc2.isEntered());
        assertFails(() -> tc1.close(), IllegalStateException.class);
        assertFails(() -> tc1.closeCancelled(null, ""), IllegalStateException.class);
        assertFails(() -> tc1.closeResourceExhausted(null, ""), IllegalStateException.class);

        tc2.leave(null, prev2);

        assertTrue(tc1.isActive());
        assertTrue(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        tc1.leave(null, prev1);

        assertFalse(tc1.isActive());
        assertFalse(tc1.isEntered());
        assertFalse(tc2.isActive());
        assertFalse(tc2.isEntered());

        prev1 = tc1.enter(null);
        prev2 = tc2.enter(null);
        Object prev3 = tc1.enter(null);

        assertFails(() -> tc1.close(), IllegalStateException.class);

        // we allow cancel in this case. the error will be propagated an the caller
        // need to make sure to either propagate the cancel the parent context
        assertFails(() -> tc1.closeCancelled(null, ""), getCancelExecutionClass());
        assertFails(() -> tc1.closeResourceExhausted(null, ""), getCancelExecutionClass());

        tc1.leave(null, prev3);
        tc2.leave(null, prev2);
        tc1.leave(null, prev1);

        tc2.close();
        tc1.close();
    }

    @Test
    public void testLeaveAndEnter() {
        setupEnv();

        TruffleContext tc = languageEnv.getContext();
        assertTrue(tc.isEntered());

        int value = tc.leaveAndEnter(null, () -> {
            assertFalse(tc.isEntered());
            assertFalse(tc.isClosed());
            return 42;
        });
        assertEquals(42, value);

        assertTrue(tc.isEntered());
        assertFalse(tc.isClosed());
    }

    @Test
    public void testInitializeCreatorContext() {
        setupEnv();
        TruffleContext innerContext = languageEnv.newContextBuilder().initializeCreatorContext(false).build();
        Object prev = innerContext.enter(null);
        try {
            assertNull(ProxyLanguage.LanguageContext.get(null));
        } finally {
            innerContext.leave(null, prev);
        }
        innerContext.close();

        innerContext = languageEnv.newContextBuilder().initializeCreatorContext(true).build();
        prev = innerContext.enter(null);
        try {
            assertNotNull(ProxyLanguage.LanguageContext.get(null));
        } finally {
            innerContext.leave(null, prev);
        }
        innerContext.close();
    }

    @Test
    public void testEvalInnerContextEvalErrors() {
        setupEnv();

        // regualar context must not be used
        TruffleContext currentContext = languageEnv.getContext();
        assertFails(() -> currentContext.evalInternal(null, newTruffleSource()), IllegalStateException.class, (e) -> {
            assertEquals("Only created inner contexts can be used to evaluate sources. " +
                            "Use TruffleLanguage.Env.parseInternal(Source) or TruffleInstrument.Env.parse(Source) instead.", e.getMessage());
        });

        TruffleContext innerContext = languageEnv.newContextBuilder().build();

        // inner context must not be entered for eval
        Object prev = innerContext.enter(null);
        assertFails(() -> innerContext.evalInternal(null, newTruffleSource()), IllegalStateException.class, (e) -> {
            assertEquals("Invalid parent context entered. " +
                            "The parent creator context or no context must be entered to evaluate code in an inner context.", e.getMessage());
        });
        innerContext.leave(null, prev);

        assertFails(() -> innerContext.evalInternal(null, com.oracle.truffle.api.source.Source.newBuilder("foobarbazz$_", "", "").build()), IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().startsWith("A language with id 'foobarbazz$_' is not installed. Installed languages are:"));
        });
        innerContext.close();
    }

    @Test
    public void testEvalInnerContextError() throws InteropException {
        EvalContextTestException innerException = new EvalContextTestException();
        EvalContextTestObject outerObject = new EvalContextTestObject();
        setupLanguageThatReturns(() -> {
            throw innerException;
        });

        TruffleContext innerContext = languageEnv.newContextBuilder().build();
        innerException.expectedContext = innerContext;
        outerObject.expectedContext = languageEnv.getContext();

        try {
            innerContext.evalInternal(null, newTruffleSource());
            fail();
        } catch (AbstractTruffleException e) {

            // arguments of the parent context are entered in the outer context
            Object result = InteropLibrary.getUncached().execute(e, outerObject);

            // and return values are entered again in the inner context
            result = InteropLibrary.getUncached().execute(result, outerObject);

            try {
                InteropLibrary.getUncached().throwException(result);
                fail();
            } catch (AbstractTruffleException innerEx) {
                result = InteropLibrary.getUncached().execute(innerEx, outerObject);
            }
        }
        assertEquals(3, innerException.executeCount);
        assertEquals(3, outerObject.executeCount);
        innerContext.close();
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static class EvalContextTestException extends AbstractTruffleException {

        TruffleContext expectedContext;
        int executeCount = 0;

        @ExportMessage
        @TruffleBoundary
        final boolean isException() {
            assertTrue(expectedContext.isEntered());
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final RuntimeException throwException() {
            assertTrue(expectedContext.isEntered());
            throw this;
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isExecutable() {
            assertTrue(expectedContext.isEntered());
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final Object execute(Object[] args) {
            assertTrue(expectedContext.isEntered());
            for (Object object : args) {
                try {
                    InteropLibrary.getUncached().execute(object);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            executeCount++;
            return this;
        }
    }

    @Test
    public void testPublicEvalInnerContext() {
        // test that primitive values can just be passed through
        setupLanguageThatReturns(() -> 42);
        TruffleContext innerContext = languageEnv.newContextBuilder().build();
        Object result = innerContext.evalPublic(null, newTruffleSource());
        assertEquals(42, result);

        com.oracle.truffle.api.source.Source internal = com.oracle.truffle.api.source.Source.newBuilder(LanguageSPIOrderTest.INTERNAL, "", "test").build();
        assertFails(() -> innerContext.evalPublic(null, internal), IllegalArgumentException.class);
        innerContext.close();
    }

    @Test
    public void testEvalInnerContext() throws InteropException {
        // test that primitive values can just be passed through
        setupLanguageThatReturns(() -> 42);
        TruffleContext innerContext = languageEnv.newContextBuilder().build();
        Object result = innerContext.evalInternal(null, newTruffleSource());
        assertEquals(42, result);

        // test that objects that cross the boundary are entered in the inner context
        EvalContextTestObject innerObject = new EvalContextTestObject();
        EvalContextTestObject outerObject = new EvalContextTestObject();
        innerContext.close();
        setupLanguageThatReturns(() -> innerObject);
        innerContext = languageEnv.newContextBuilder().build();
        innerObject.expectedContext = innerContext;
        outerObject.expectedContext = this.languageEnv.getContext();

        result = innerContext.evalInternal(null, newTruffleSource());
        assertNotEquals("must be wrapped", result, innerObject);

        // arguments of the parent context are entered in the outer context
        result = InteropLibrary.getUncached().execute(result, outerObject);

        // and return values are entered again in the inner context
        result = InteropLibrary.getUncached().execute(result, outerObject);

        assertEquals(2, innerObject.executeCount);
        assertEquals(2, outerObject.executeCount);
        innerContext.close();
    }

    @ExportLibrary(InteropLibrary.class)
    static class EvalContextTestObject implements TruffleObject {

        TruffleContext expectedContext;
        int executeCount = 0;

        @ExportMessage
        @TruffleBoundary
        final boolean isExecutable() {
            assertTrue(expectedContext.isEntered());
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final Object execute(Object[] args) {
            assertTrue(expectedContext.isEntered());
            for (Object object : args) {
                try {
                    InteropLibrary.getUncached().execute(object);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            executeCount++;
            return this;
        }

    }

    @Test
    public void testNoInitializeMultiContextForInnerContext() {
        AtomicBoolean multiContextInitialized = new AtomicBoolean(false);
        setupEnv(Context.create(), new ProxyLanguage() {

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }

            @Override
            protected void initializeMultipleContexts() {
                multiContextInitialized.set(true);
            }

            @Override
            protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
                return true;
            }
        });
        TruffleContext internalContext = languageEnv.newContextBuilder().initializeCreatorContext(false).build();
        internalContext.evalInternal(null, com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "", "").build());
        assertFalse(multiContextInitialized.get());
        internalContext.close();
    }

    @Test
    public void testInitializeMultiContextForInnerContext() {
        AtomicBoolean multiContextInitialized = new AtomicBoolean(false);
        setupEnv(Context.newBuilder().engine(Engine.create()).build(), new ProxyLanguage() {

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(42).getCallTarget();
            }

            @Override
            protected void initializeMultipleContexts() {
                multiContextInitialized.set(true);
            }

            @Override
            protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
                return true;
            }
        });
        TruffleContext ic = languageEnv.newContextBuilder().initializeCreatorContext(true).build();
        assertTrue(multiContextInitialized.get());
        ic.close();
    }

    private void setupLanguageThatReturns(Supplier<Object> supplier) {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return new RootNode(ProxyLanguage.get(null)) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return get();
                    }

                    @TruffleBoundary
                    private Object get() {
                        return supplier.get();
                    }
                }.getCallTarget();
            }
        });
    }

    private static com.oracle.truffle.api.source.Source newTruffleSource() {
        return com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "", "test").build();
    }

    @Test
    public void testLeaveAndEnterInnerContext() {
        setupEnv();

        TruffleContext parent = languageEnv.getContext();
        TruffleContext tc = languageEnv.newContextBuilder().build();
        assertFalse(tc.isEntered());
        assertEquals(parent, tc.getParent());

        try {
            tc.leaveAndEnter(null, () -> {
                fail();
                return true;
            });
            fail();
        } catch (AssertionError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cannot leave context that is currently not entered"));
        }

        assertTrue(parent.isEntered());
        Object prev = tc.enter(null);
        try {
            assertFalse(parent.isEntered());
            assertTrue(parent.isActive());
            int value = tc.leaveAndEnter(null, () -> {
                assertFalse(tc.isEntered());
                assertFalse(parent.isEntered());
                assertTrue(parent.isActive());
                return 42;
            });
            assertEquals(42, value);
            assertTrue(tc.isEntered());
        } finally {
            tc.leave(null, prev);
        }
        tc.close();
    }

    private static Class<? extends Throwable> getCancelExecutionClass() {
        try {
            return Class.forName("com.oracle.truffle.polyglot.PolyglotEngineImpl$CancelExecution").asSubclass(Throwable.class);
        } catch (ClassNotFoundException cnf) {
            throw new AssertionError("Cannot load CancelExecution class.", cnf);
        }
    }

    private static Node getCancelExecutionLocation(Throwable t) {
        try {
            Method m = t.getClass().getDeclaredMethod("getLocation");
            m.setAccessible(true);
            return (Node) m.invoke(t);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke CancelExecution.getLocation.", e);
        }
    }

    private static final class ControllerNode extends RootNode {

        ControllerNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return executeImpl();
        }

        @TruffleBoundary
        private Object executeImpl() {
            TruffleLanguage.Env env = LanguageContext.get(this).getEnv();
            TruffleContext creatorContext = env.newContextBuilder().build();
            CountDownLatch running = new CountDownLatch(1);
            Thread t = env.createThread(() -> {
                CallTarget target = LanguageContext.get(null).getEnv().parsePublic(com.oracle.truffle.api.source.Source.newBuilder(
                                ProxyLanguage.ID, "worker", "worker").build());
                running.countDown();
                target.call();
            }, creatorContext);
            try {
                t.start();
                running.await();
                creatorContext.closeCancelled(this, "Stopping");
                t.join();
                return true;
            } catch (InterruptedException ie) {
                return false;
            }
        }
    }

    private static final class WorkerNode extends RootNode {

        WorkerNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return executeImpl();
        }

        @TruffleBoundary
        private Object executeImpl() {
            while (true) {
                try {
                    Thread.sleep(1_000);
                    TruffleSafepoint.poll(this);
                } catch (InterruptedException ie) {
                    // Ignore InterruptedException, wait for ThreadDeath.
                }
            }
        }
    }
}
