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

import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ID;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
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

public class InnerAndOuterContextCancellationTest {

    private Engine engine;
    private Context context;
    private TruffleInstrument.Env instrumentEnv;

    private void setupSingleRun(boolean multiEngine) {
        setupSingleRun(multiEngine, false);
    }

    private void setupSingleRun(boolean multiEngine, boolean multiThreading) {
        if (!multiEngine) {
            if (engine == null) {
                engine = Engine.create();
                instrumentEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            }
            Context.Builder builder = Context.newBuilder(ID).engine(engine);
            if (multiThreading) {
                builder.allowCreateThread(true);
            }
            context = builder.build();
        } else {
            Context.Builder builder = Context.newBuilder(ID);
            if (multiThreading) {
                builder.allowCreateThread(true);
            }
            context = builder.build();
            instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
        }
    }

    private void teardownSingleRun() {
        if (context != null) {
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
        }
    }

    @Rule public TestName testNameRule = new TestName();

    @After
    public void engineClose() {
        if (engine != null) {
            engine.close();
        }
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testInnerContextWorking() {
        setupSingleRun(false, true);
        context.eval(ID, "CONTEXT(DEFINE(foo,STATEMENT),SPAWN(foo),JOIN())");
        teardownSingleRun();
    }

    @Test
    public void testCloseInnerContextOnly() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(false, true, 1, true);
    }

    @Test
    public void testCloseOuterContextOnly() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, false, 1, true);
    }

    @Test
    public void testCloseInnerAndOuterContext() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, true, 1, true);
    }

    @Test
    public void testCloseInnerContextOnlyRepeatWithSeparateEngines() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(false, true, 100, true);
    }

    @Test
    public void testCloseOuterContextOnlyRepeatWithSeparateEngines() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, false, 100, true);
    }

    @Test
    public void testCloseInnerAndOuterContextRepeatWithSeparateEngines() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, true, 100, true);
    }

    @Test
    public void testCloseInnerContextOnlyRepeatWithSingleEngine() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(false, true, 100, false);
    }

    @Test
    public void testCloseOuterContextOnlyRepeatWithSingleEngine() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, false, 100, false);
    }

    @Test
    public void testCloseInnerAndOuterContextRepeatWithSingleEngine() throws IOException, ExecutionException, InterruptedException {
        testCloseContext(true, true, 100, false);
    }

    private void testCloseContext(boolean outer, boolean inner, int repeatCount, boolean multiEngine) throws IOException, ExecutionException, InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            setupSingleRun(multiEngine);
            int nThreads = 1;
            if (inner) {
                nThreads++;
            }
            if (outer) {
                nThreads++;
            }
            context.initialize(ID);
            Source source = Source.newBuilder(ID, "CONTEXT(LOOP(infinity,STATEMENT))", this.getClass().getSimpleName()).build();
            CountDownLatch cancelLatch = new CountDownLatch(1);
            AtomicReference<TruffleContext> innerTruffleContext = new AtomicReference<>();
            captureInnerContext(cancelLatch, innerTruffleContext);
            ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
            List<Future<?>> futures = new ArrayList<>();
            if (inner) {
                futures.add(executorService.submit(() -> {
                    try {
                        cancelLatch.await();
                    } catch (InterruptedException ie) {
                    }
                    Context innerCreatorApi = truffleContextToCreatorApi(innerTruffleContext.get());
                    innerCreatorApi.close(true);
                }));
            }
            if (outer) {
                futures.add(executorService.submit(() -> {
                    try {
                        cancelLatch.await();
                    } catch (InterruptedException ie) {
                    }
                    context.close(true);
                }));
            }
            futures.add(executorService.submit(() -> {
                try {
                    context.eval(source);
                    Assert.fail();
                } catch (PolyglotException e) {
                    if (!e.isCancelled()) {
                        throw e;
                    }
                }
            }));
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
            teardownSingleRun();
        }
    }

    private void captureInnerContext(CountDownLatch cancelLatch, AtomicReference<TruffleContext> innerTruffleContext) {
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                onEnterBoundary();
            }

            @CompilerDirectives.TruffleBoundary
            private void onEnterBoundary() {
                innerTruffleContext.set(InstrumentContext.get(null).env.getContext());
                cancelLatch.countDown();
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

            }
        });
    }

    private static Context truffleContextToCreatorApi(TruffleContext tc) {
        try {
            Field polyglotContextField = tc.getClass().getDeclaredField("polyglotContext");
            polyglotContextField.setAccessible(true);
            Object polyglotContext = polyglotContextField.get(tc);
            Field creatorApiField = polyglotContext.getClass().getDeclaredField("api");
            creatorApiField.setAccessible(true);
            return (Context) creatorApiField.get(polyglotContext);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static class CancelInfiniteJob implements Callable<Void> {
        private final Context context;
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final Thread thread;

        CancelInfiniteJob(Context context, Thread thread) {
            this.context = context;
            this.thread = thread;
        }

        @Override
        public Void call() throws Exception {
            startLatch.await();
            context.close(true);
            return null;
        }
    }

    static class InfiniteJob implements Callable<Void> {
        private final Engine engine;
        private final ExecutorService cancelExecutorService;

        InfiniteJob(Engine engine, ExecutorService cancelExecutorService) {
            this.engine = engine;
            this.cancelExecutorService = cancelExecutorService;
        }

        @Override
        public Void call() {
            Context.Builder builder = Context.newBuilder().engine(engine);

            try (Context context = builder.build()) {
                context.initialize(InstrumentationTestLanguage.ID);
                CancelInfiniteJob cancelInfiniteJob = new CancelInfiniteJob(context, Thread.currentThread());
                cancelExecutorService.submit(cancelInfiniteJob);
                try {
                    TruffleInstrument.Env instrEnv = engine.getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                    instrEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                        @Override
                        public void onEnter(EventContext c, VirtualFrame frame) {
                            if (cancelInfiniteJob.thread == Thread.currentThread()) {
                                cancelInfiniteJob.startLatch.countDown();
                            }
                        }

                        @Override
                        public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                        }

                        @Override
                        public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                        }
                    });
                    context.eval(statements(Integer.MAX_VALUE));
                    fail();
                } catch (PolyglotException e) {
                    if (!e.isCancelled()) {
                        throw e;
                    }
                }
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            return null;
        }
    }

    @Test
    public void testSerialCancel() throws Exception {
        testCancel(1, 20);
    }

    @Test
    public void testParallelCancel() throws Exception {
        testCancel(10, 50);
    }

    private static void testCancel(int threads, int jobs) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        ExecutorService cancelExecutorService = Executors.newFixedThreadPool(jobs);
        List<Future<?>> futures = new ArrayList<>();
        try (Engine engine = Engine.create()) {
            for (int i = 0; i < jobs; i++) {
                futures.add(executorService.submit(new InfiniteJob(engine, cancelExecutorService)));
            }

            for (Future<?> future : futures) {
                future.get();
            }

            executorService.shutdownNow();
            cancelExecutorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
            cancelExecutorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancelInfiniteCountOfFiniteLoops() throws InterruptedException, ExecutionException {
        try (Engine engn = Engine.create()) {
            Context.Builder builder = Context.newBuilder().engine(engn);
            int nThreads = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch cancelLatch = new CountDownLatch(nThreads);
            List<Context> contextList = new CopyOnWriteArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                futures.add(executorService.submit(() -> {
                    try (Context c = builder.build()) {
                        contextList.add(c);
                        try {
                            for (int j = 0; j < 100; j++) {
                                c.eval(statements(1000));
                            }
                            cancelLatch.countDown();
                            while (true) {
                                c.eval(statements(1000));
                            }
                        } catch (PolyglotException e) {
                            if (!e.isCancelled()) {
                                throw e;
                            }
                        }
                    } catch (PolyglotException pe) {
                        if (!pe.isCancelled()) {
                            throw pe;
                        }
                    }
                }));
            }
            cancelLatch.await();
            for (Context c : contextList) {
                c.close(true);
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    private static Source statements(int count) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, "LOOP(" + (count == Integer.MAX_VALUE ? "infinity" : count) + ", STATEMENT)",
                        InnerAndOuterContextCancellationTest.class.getSimpleName()).buildLiteral();
    }
}
