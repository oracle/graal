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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.api.test.common.TestUtils;

public class ContextExitTest {
    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testInnerContext() throws Exception {
        try (Context context = Context.create()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONTEXT(CONTEXT(STATEMENT))", "TestInnerContext").build();
            context.eval(source);
        }
    }

    @Test
    public void testExitFromMainThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,EXIT(1)),CALL(foo))", "ExitFromMainThread").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMultipleMainThreads() throws Exception {
        int nThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                int j = i;
                futures.add(executorService.submit(() -> {
                    try {
                        String code;
                        if (j % 2 == 0) {
                            code = "ROOT(SLEEP(100),EXIT(1))";
                        } else {
                            code = "LOOP(infinity,STATEMENT)";
                        }
                        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "ExitFromMainThread" + j).build();
                        context.eval(source);
                        Assert.fail();
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    } catch (PolyglotException pe) {
                        if (!pe.isExit()) {
                            throw pe;
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testExitFromMainThreadWithOnePolyglotThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo),EXIT(1))", "ExitFromMainThread1PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithOnePolyglotThreadExplicitEnter() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            context.enter();
            try {
                Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo),SLEEP(100),EXIT(1))", "ExitFromMainThread1PT").build();
                context.eval(source);
                Assert.fail();
            } finally {
                context.leave();
            }
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithTwoPolyglotThreads() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo),SPAWN(foo),EXIT(1))", "ExitFromMainThread2PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithTenPolyglotThreads() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),LOOP(10,SPAWN(foo)),EXIT(1))", "ExitFromMainThread10PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithOnePolyglotThreadSleepBeforeExit() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo),SLEEP(100),EXIT(1))", "ExitFromMainThread1PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithTwoPolyglotThreadsSleepBeforeExit() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),SPAWN(foo),SPAWN(foo),SLEEP(100),EXIT(1))", "ExitFromMainThread2PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMainThreadWithTenPolyglotThreadsSleepBeforeExit() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,LOOP(infinity,STATEMENT)),LOOP(10,SPAWN(foo)),SLEEP(100),EXIT(1))", "ExitFromMainThread10PT").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMultipleThreads() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,EXIT(1)),LOOP(10,SPAWN(foo)),EXIT(1))", "ExitFromMultipleThreads").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMultipleThreadsWithSleepInMainThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,EXIT(1)),LOOP(10,SPAWN(foo)),SLEEP(100),EXIT(1))", "ExitFromMultipleThreadsSleepInMain").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMultipleInnerContextThreadsWithNoExitInMainThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(CONTEXT(DEFINE(foo,EXIT(1)),LOOP(10,SPAWN(foo)),JOIN()))",
                            "ExitFromMultipleInnerContextThreadsSleepAndNoExitInMain").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit() && !pe.isInterrupted()) {
                /*
                 * Inner context is the one being exited, hence the exiting property of the outer
                 * context is not set, and so if the exit happens during JOIN() the final polyglot
                 * exception has only the interrupted property.
                 */
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromMultipleInnerContextThreadsWithNoExitInMainThread2() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID,
                            "ROOT(DEFINE(foo,CONTEXT(DEFINE(foo,EXIT(1)),LOOP(10,SPAWN(foo)),LOOP(infinity,STATEMENT))),SPAWN(foo),JOIN())",
                            "ExitFromMultipleInnerContextThreadsSleepAndNoExitInMain").build();
            context.eval(source);
            /*
             * Here the exit happens completely outside of the main thread of the outer context, and
             * so the outer context finishes normally.
             */
        }
    }

    @Test
    public void testExitFromMultipleThreadsWithSleepInPolyglotThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,SLEEP(100),EXIT(1)),LOOP(10,SPAWN(foo)),EXIT(1))", "ExitFromMultipleThreadsSleepInPolyglot").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromSpawnedThread() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,EXIT(1)),SPAWN(foo),JOIN())", "ExitFromSpawnedThread").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExitFromSpawnedThread2() throws Exception {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(InstrumentationTestLanguage.ID);
            Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(foo,EXIT(1)),SPAWN(foo),LOOP(infinity,STATEMENT))", "ExitFromSpawnedThread2").build();
            context.eval(source);
            Assert.fail();
        } catch (PolyglotException pe) {
            if (!pe.isExit()) {
                throw pe;
            }
        }
    }

    @Test
    public void testMultipleContextDeadlockOnExit() throws InterruptedException, ExecutionException {
        /*
         * Parallel exit of two context where in one thread context2 enters and exits when also
         * context1 is entered and in a second thread context1 enters and exits when also context2
         * is entered.
         */
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.newBuilder().build()) {
            try (Context context1 = Context.newBuilder().engine(engine).allowCreateThread(true).build(); Context context2 = Context.newBuilder().engine(engine).allowCreateThread(true).build()) {
                TruffleInstrument.Env instrumentEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                TruffleContext truffleContext1;
                TruffleContext truffleContext2;
                context1.enter();
                truffleContext1 = instrumentEnv.getEnteredContext();
                context1.leave();
                context2.enter();
                truffleContext2 = instrumentEnv.getEnteredContext();
                context2.leave();
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(),
                                new ExecutionEventListener() {
                                    @Override
                                    public void onEnter(EventContext ctx, VirtualFrame frame) {
                                    }

                                    @Override
                                    public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                                        if (result instanceof Integer) {
                                            if ((Integer) result == 1) {
                                                callExit(truffleContext2, ctx.getInstrumentedNode());
                                            }
                                            if ((Integer) result == 2) {
                                                callExit(truffleContext1, ctx.getInstrumentedNode());
                                            }
                                        }
                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callExit(TruffleContext ctx, Node node) {
                                        Object prev = ctx.enter(node);
                                        try {
                                            CallTarget callTarget = instrumentEnv.parse(
                                                            com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID, "EXIT(1)", "ExitDirectly").build());
                                            callTarget.call();
                                        } catch (IOException io) {
                                            throw new RuntimeException(io);
                                        } finally {
                                            ctx.leave(node, prev);
                                        }
                                    }

                                    @Override
                                    public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                                    }
                                });
                Future<?> future1 = executorService.submit(() -> {
                    try {
                        context1.eval(InstrumentationTestLanguage.ID, "CONSTANT(1)");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit()) {
                            throw pe;
                        }
                    }
                });
                Future<?> future2 = executorService.submit(() -> {
                    try {
                        context2.eval(InstrumentationTestLanguage.ID, "CONSTANT(2)");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit()) {
                            throw pe;
                        }
                    }
                });
                future1.get();
                future2.get();
            } catch (PolyglotException pe) {
                if (!pe.isExit()) {
                    throw pe;
                }
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void testMultipleContextDeadlockOnExit2() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.create()) {
            try (Context context1 = Context.newBuilder().engine(engine).allowCreateThread(true).build(); Context context2 = Context.newBuilder().engine(engine).allowCreateThread(true).build()) {
                TruffleInstrument.Env instrumentEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                TruffleContext truffleContext1;
                TruffleContext truffleContext2;
                context1.enter();
                truffleContext1 = instrumentEnv.getEnteredContext();
                context1.leave();
                context2.enter();
                truffleContext2 = instrumentEnv.getEnteredContext();
                context2.leave();
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(),
                                new ExecutionEventListener() {
                                    @Override
                                    public void onEnter(EventContext ctx, VirtualFrame frame) {
                                    }

                                    @Override
                                    public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                                        if (result instanceof Integer) {
                                            if ((Integer) result == 1) {
                                                callExit(truffleContext2, ctx.getInstrumentedNode());
                                            }
                                            if ((Integer) result == 2) {
                                                callExit(truffleContext1, ctx.getInstrumentedNode());
                                            }
                                        }
                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callExit(TruffleContext ctx, Node node) {
                                        Object prev = ctx.enter(node);
                                        try {
                                            CallTarget callTarget = instrumentEnv.parse(com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID,
                                                            "ROOT(DEFINE(foo,EXIT(1)),SPAWN(foo),JOIN())", "ExitFromSpawnedThread").build());
                                            callTarget.call();
                                        } catch (IOException io) {
                                            throw new RuntimeException(io);
                                        } finally {
                                            ctx.leave(node, prev);
                                        }
                                    }

                                    @Override
                                    public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                                    }
                                });
                Future<?> future1 = executorService.submit(() -> {
                    try {
                        context1.eval(InstrumentationTestLanguage.ID, "CONSTANT(1)");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit() && !pe.isInterrupted()) {
                            /*
                             * context1 can interrupt context2 while it is not exiting yet, because
                             * it shares one thread with it, and vice versa.
                             */
                            throw pe;
                        }
                    }
                });
                Future<?> future2 = executorService.submit(() -> {
                    try {
                        context2.eval(InstrumentationTestLanguage.ID, "CONSTANT(2)");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit() && !pe.isInterrupted()) {
                            /*
                             * context1 can interrupt context2 while it is not exiting yet, because
                             * it shares one thread with it, and vice versa.
                             */
                            throw pe;
                        }
                    }
                });
                future1.get();
                future2.get();
            } catch (PolyglotException pe) {
                if (!pe.isExit()) {
                    throw pe;
                }
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void testMultipleContextDeadlockOnExit3() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.newBuilder().build()) {
            try (Context context1 = Context.newBuilder().engine(engine).allowCreateThread(true).build(); Context context2 = Context.newBuilder().engine(engine).allowCreateThread(true).build()) {
                TruffleInstrument.Env instrumentEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                TruffleContext truffleContext1;
                TruffleContext truffleContext2;
                context1.enter();
                truffleContext1 = instrumentEnv.getEnteredContext();
                context1.leave();
                context2.enter();
                truffleContext2 = instrumentEnv.getEnteredContext();
                context2.leave();
                AtomicBoolean callExit = new AtomicBoolean(true);
                AtomicBoolean callExitToBeCalledForContext1 = new AtomicBoolean();
                AtomicBoolean callExitToBeCalledForContext2 = new AtomicBoolean();
                CountDownLatch callExitForContext1Latch = new CountDownLatch(1);
                CountDownLatch callExitForContext2Latch = new CountDownLatch(1);
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.ConstantTag.class).build(),
                                new ExecutionEventListener() {
                                    @Override
                                    public void onEnter(EventContext ctx, VirtualFrame frame) {
                                    }

                                    @Override
                                    public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                                        if (result instanceof Integer) {
                                            if ((Integer) result == 1) {
                                                callExitForContext2(ctx.getInstrumentedNode());
                                            }
                                            if ((Integer) result == 2) {
                                                callExitForContext1(ctx.getInstrumentedNode());
                                            }
                                        }
                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callExitForContext1(Node node) {
                                        callExitToBeCalledForContext1.set(true);
                                        try {
                                            callExit(truffleContext1, node);
                                        } finally {
                                            callExitForContext1Latch.countDown();
                                        }

                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callExitForContext2(Node node) {
                                        callExitToBeCalledForContext2.set(true);
                                        try {
                                            callExit(truffleContext2, node);
                                        } finally {
                                            callExitForContext2Latch.countDown();
                                        }

                                    }

                                    @CompilerDirectives.TruffleBoundary
                                    private void callExit(TruffleContext ctx, Node node) {
                                        Object prev = ctx.enter(node);
                                        try {
                                            CallTarget callTarget = instrumentEnv.parse(
                                                            com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID, "EXIT(1)", "ExitDirectly").build());
                                            callTarget.call();
                                        } catch (IOException io) {
                                            throw new RuntimeException(io);
                                        } finally {
                                            ctx.leave(node, prev);
                                        }
                                    }

                                    @Override
                                    public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                                    }
                                });
                Future<?> future1 = executorService.submit(() -> {
                    try {
                        context1.eval(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,CONSTANT(1)),SPAWN(foo),JOIN())");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit()) {
                            throw pe;
                        }
                    }
                });
                Future<?> future2 = executorService.submit(() -> {
                    try {
                        context2.eval(InstrumentationTestLanguage.ID, "CONTEXT(DEFINE(foo,CONSTANT(2)),SPAWN(foo),JOIN())");
                    } catch (PolyglotException pe) {
                        if (!pe.isExit()) {
                            throw pe;
                        }
                    }
                });
                future1.get();
                future2.get();
                /*
                 * Each exit is executed by a separate call to Context#eval in a polyglot thread of
                 * the other context, so waiting for the futures to finish is not sufficient as it
                 * does not wait for the polyglot threads. InstrumentationTestLanguage waits for
                 * polyglot threads in finalizeContext executed by close, but in order for this to
                 * work, we would have to execute the closes in a particular order which is not
                 * deterministic as it depends on interleaving of the two threads. So a close could
                 * fail because a polyglot thread of one context might initiate a main thread of the
                 * other context (it is the same thread but from one context's view it is a polyglot
                 * thread and from the other context's view it is a main thread) which makes
                 * standard close throw IllegalStateException saying the context is still active in
                 * other threads.
                 */
                callExit.set(false);
                if (callExitToBeCalledForContext1.get()) {
                    /*
                     * callExitToBeCalledForContext1 might be set after this check evaluates to
                     * false, but then the exit is not actually executed because callExit is false.
                     */
                    callExitForContext1Latch.await();
                }
                if (callExitToBeCalledForContext2.get()) {
                    /*
                     * callExitToBeCalledForContext2 might be set after this check evaluates to
                     * false, but then the exit is not actually executed because callExit is false.
                     */
                    callExitForContext2Latch.await();
                }
            } catch (PolyglotException pe) {
                if (!pe.isExit()) {
                    throw pe;
                }
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    @TruffleLanguage.Registration
    static class HardExitFromNaturalExitTestLanguage extends TruffleLanguage<HardExitFromNaturalExitTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(HardExitFromNaturalExitTestLanguage.class);

        static class Context {
            private final Env env;
            private Thread t;

            Context(Env env) {
                this.env = env;
            }
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            boolean spawnPolyglotThread = "SPAWNPOLYGLOTTHREAD".contentEquals(request.getSource().getCharacters());
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @CompilerDirectives.TruffleBoundary
                private Object evalBoundary() {
                    if (spawnPolyglotThread) {
                        Context ctx = CONTEXT_REF.get(null);
                        ctx.t = ctx.env.newTruffleThreadBuilder(new Runnable() {
                            @Override
                            @CompilerDirectives.TruffleBoundary
                            public void run() {
                                synchronized (this) {
                                    try {
                                        wait();
                                        Assert.fail();
                                    } catch (InterruptedException ie) {
                                    }
                                }
                            }
                        }).build();
                        ctx.t.start();
                    }
                    return NullObject.SINGLETON;
                }

            }.getCallTarget();
        }

        @Override
        protected void finalizeContext(Context ctx) {
            if (ctx.t != null) {
                try {
                    ctx.t.join();
                } catch (InterruptedException ie) {
                }
            }
        }

        @Override
        protected void exitContext(Context ctx, ExitMode exitMode, int exitCode) {
            if (exitMode == ExitMode.NATURAL) {
                ctx.env.getContext().closeExited(null, 42);
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        private static final ContextReference<Context> CONTEXT_REF = ContextReference.create(HardExitFromNaturalExitTestLanguage.class);
    }

    @Test
    public void testHardExitFromNaturalExit() {
        boolean exceptionThrown = false;
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.eval(HardExitFromNaturalExitTestLanguage.ID, "");
        } catch (PolyglotException pe) {
            exceptionThrown = true;
            if (!pe.isExit() || pe.getExitStatus() != 42) {
                throw pe;
            }
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testHardExitFromNaturalExitWithPolyglotThreadRunning() {
        for (int i = 0; i < 100; i++) {
            boolean exceptionThrown = false;
            try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
                context.eval(HardExitFromNaturalExitTestLanguage.ID, "SPAWNPOLYGLOTTHREAD");
            } catch (PolyglotException pe) {
                exceptionThrown = true;
                if (!pe.isExit() || pe.getExitStatus() != 42) {
                    throw pe;
                }
            }
            assertTrue(exceptionThrown);
        }
    }

    @TruffleLanguage.Registration
    static class HardExitDuringNaturalExitFromOtherThreadTestLanguage extends TruffleLanguage<HardExitDuringNaturalExitFromOtherThreadTestLanguage.Context> {
        static final String ID = TestUtils.getDefaultLanguageId(HardExitDuringNaturalExitFromOtherThreadTestLanguage.class);

        static class Context {
            private final Env env;
            private Thread t;
            private final CountDownLatch tStartedLatch = new CountDownLatch(1);
            private final CountDownLatch exitContextStartedLatch = new CountDownLatch(1);

            Context(Env env) {
                this.env = env;
            }
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return evalBoundary();
                }

                @CompilerDirectives.TruffleBoundary
                private Object evalBoundary() {
                    Context ctx = CONTEXT_REF.get(null);
                    ctx.t = ctx.env.newTruffleThreadBuilder(new Runnable() {
                        @Override
                        @CompilerDirectives.TruffleBoundary
                        public void run() {
                            Context threadCtx = CONTEXT_REF.get(null);
                            threadCtx.tStartedLatch.countDown();
                            try {
                                threadCtx.exitContextStartedLatch.await();
                            } catch (InterruptedException ie) {
                                throw new AssertionError(ie);
                            }
                            threadCtx.env.getContext().closeExited(null, 42);

                        }
                    }).build();
                    ctx.t.start();
                    return NullObject.SINGLETON;
                }

            }.getCallTarget();
        }

        @Override
        protected void finalizeContext(Context ctx) {
            try {
                ctx.t.join();
            } catch (InterruptedException ie) {
            }
        }

        @Override
        protected void exitContext(Context ctx, ExitMode exitMode, int exitCode) {
            if (exitMode == ExitMode.NATURAL) {
                ctx.exitContextStartedLatch.countDown();
                try {
                    ctx.tStartedLatch.await();
                    ctx.t.join();
                } catch (InterruptedException ie) {
                    throw new AssertionError(ie);
                }
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        private static final ContextReference<Context> CONTEXT_REF = TruffleLanguage.ContextReference.create(HardExitDuringNaturalExitFromOtherThreadTestLanguage.class);
    }

    @Test
    public void testHardExitDuringNaturalExitFromOtherThread() {
        for (int i = 0; i < 100; i++) {
            boolean exceptionThrown = false;
            try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
                context.eval(HardExitDuringNaturalExitFromOtherThreadTestLanguage.ID, "");
            } catch (PolyglotException pe) {
                exceptionThrown = true;
                if (!pe.isExit() || pe.getExitStatus() != 42) {
                    throw pe;
                }
            }
            assertTrue(exceptionThrown);
        }
    }
}
