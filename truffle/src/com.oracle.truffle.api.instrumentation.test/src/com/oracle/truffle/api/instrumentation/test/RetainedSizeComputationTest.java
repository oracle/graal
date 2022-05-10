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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;

public class RetainedSizeComputationTest {
    @Before
    public void setup() {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
    }

    @Test
    public void testRetainedSizeSingleThreaded() throws IOException {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            context.enter();
            try {
                for (int i = 0; i < 10000; i++) {
                    defineFoobarFunction(context, i);
                }
                long retainedSize = instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(false));
                Assert.assertTrue(retainedSize > 1024L * 1024L);
                Assert.assertTrue(retainedSize < 16L * 1024L * 1024L);
            } finally {
                context.leave();
            }
        }
    }

    @Test
    public void testRetainedSizeWithStatementLimit() {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        try (Engine engine = Engine.create()) {
            Context.newBuilder().engine(engine).build().close();
            ResourceLimits resourceLimits = ResourceLimits.newBuilder().statementLimit(5, source -> true).build();
            try (Context context = Context.newBuilder().engine(engine).resourceLimits(resourceLimits).build()) {
                TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                context.initialize(InstrumentationTestLanguage.ID);
                instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                    @Override
                    public void onEnter(EventContext ctx, VirtualFrame frame) {

                    }

                    @Override
                    public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                        long retainedSize = instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(false));
                        Assert.assertTrue(retainedSize > 0L);
                        Assert.assertTrue(retainedSize < 16L * 1024L * 1024L);
                    }

                    @Override
                    public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {

                    }
                });
                /*
                 * StatementIncrementNode stores PolyglotContextImpl in frames. The retained size
                 * computation should stop on PolyglotContextImpl and don't fail.
                 */
                context.eval(InstrumentationTestLanguage.ID, "ROOT(STATEMENT)");
            }
        }
    }

    @Test
    public void testRetainedSizeCanceledAtStart() throws IOException {
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            context.enter();
            try {
                for (int i = 0; i < 10000; i++) {
                    defineFoobarFunction(context, i);
                }
                try {
                    instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(true));
                    Assert.fail();
                } catch (CancellationException e) {
                    Assert.assertEquals("cancelled at 0 bytes", e.getMessage());
                }
            } finally {
                context.leave();
            }
        } catch (UnsupportedOperationException e) {
            if (!TruffleOptions.AOT && !(Truffle.getRuntime() instanceof DefaultTruffleRuntime)) {
                throw e;
            } else {
                Assert.assertEquals("Polyglot context heap size calculation is not supported on this platform.", e.getMessage());
            }
        }
    }

    private static void defineFoobarFunction(Context context, int i) throws IOException {
        /*
         * Defining a function is a reliable method to increase retained size for
         * InstrumentationTestLanguage.
         */
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "DEFINE(foobar" + i + ",STATEMENT)", RetainedSizeComputationTest.class.getSimpleName() + i).build();
        context.eval(source);
    }

    @Test
    public void testRetainedSizeGradual() throws IOException, InterruptedException, ExecutionException {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        List<Long> retainedSizesList = Collections.synchronizedList(new ArrayList<>());
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            List<Future<?>> futures = new ArrayList<>();
            Object waiter = new Object();
            AtomicInteger defineCount = new AtomicInteger();
            int targetDefineCount = 1000000;
            int notificationStep = 1000;
            for (int i = 0; i < 100; i++) {
                int taskId = i;
                futures.add(executor.submit(() -> {
                    synchronized (waiter) {
                        while (defineCount.get() < taskId * notificationStep * 10) {
                            try {
                                waiter.wait();
                            } catch (InterruptedException ie) {
                            }
                        }
                        /*
                         * Wait until at least 1000 further define calls.
                         */
                        int currentDefineCount = defineCount.get();
                        if (currentDefineCount <= (targetDefineCount - 2 * notificationStep)) {
                            int waitUntilDefineCount = ((currentDefineCount + notificationStep - 1) / notificationStep) * notificationStep + notificationStep;
                            while (defineCount.get() < waitUntilDefineCount) {
                                try {
                                    waiter.wait();
                                } catch (InterruptedException ie) {
                                }
                            }
                        } else {
                            return;
                        }
                    }
                    context.enter();
                    try {
                        retainedSizesList.add(instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(false)));
                    } finally {
                        context.leave();
                    }
                }));
            }
            /*
             * Consume memory by defining a million functions. At the start of each block of 10000
             * functions, initiate retained size computation in another thread. The result should be
             * gradually increasing retained size. This is also a performance test. Retained size
             * computation should start as soon as it is allowed to start and don't take longer than
             * the computation started after it.
             */
            for (int i = 0; i < targetDefineCount; i++) {
                defineFoobarFunction(context, i);
                defineCount.incrementAndGet();
                if (i % notificationStep == 0) {
                    synchronized (waiter) {
                        waiter.notifyAll();
                    }
                }
            }
            for (Future<?> future : futures) {
                future.get();
            }
            long previousResult = 0;
            int cnt = 0;
            for (long calculationResult : retainedSizesList) {
                cnt++;
                if (previousResult > 1024L * 1024L) {
                    Assert.assertTrue(String.format("previousCalculationResult = %d, calculationResult = %d, cnt = %d", previousResult, calculationResult, cnt),
                                    previousResult > 16L * 1024 * 1024L || previousResult <= calculationResult);
                }
                previousResult = calculationResult;
            }
            Assert.assertTrue(previousResult > 1024L * 1024L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(100L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRetainedSizeCanceledDuringCalculation() throws IOException, InterruptedException, ExecutionException {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        List<Long> retainedSizesList = Collections.synchronizedList(new ArrayList<>());
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            List<Future<?>> futures = new ArrayList<>();
            AtomicBoolean cancelled = new AtomicBoolean();
            Object waiter = new Object();
            AtomicInteger defineCount = new AtomicInteger();
            int targetDefineCount = 1000000;
            int notificationStep = 1000;
            for (int i = 0; i < 100; i++) {
                int taskId = i;
                futures.add(executor.submit(() -> {
                    synchronized (waiter) {
                        while (defineCount.get() < taskId * notificationStep * 10) {
                            try {
                                waiter.wait();
                            } catch (InterruptedException ie) {
                            }
                        }
                        /*
                         * Wait until at least 1000 further define calls.
                         */
                        int currentDefineCount = defineCount.get();
                        if (currentDefineCount <= (targetDefineCount - 2 * notificationStep)) {
                            int waitUntilDefineCount = ((currentDefineCount + notificationStep - 1) / notificationStep) * notificationStep + notificationStep;
                            while (defineCount.get() < waitUntilDefineCount) {
                                try {
                                    waiter.wait();
                                } catch (InterruptedException ie) {
                                }
                            }
                        } else {
                            return;
                        }
                    }
                    context.enter();
                    try {
                        try {
                            retainedSizesList.add(instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, cancelled));
                        } catch (CancellationException e) {
                            retainedSizesList.add(-1L);
                        }
                    } finally {
                        context.leave();
                    }
                }));
            }
            /*
             * Consume memory by defining a million functions. At the start of each block of 10000
             * functions, initiate retained size computation in another thread. Additionally, cancel
             * the computations after defining roughly half of the functions. The result should be
             * gradually increasing retained size up to the point where the computations are
             * cancelled. This is also a performance test. Retained size computation should start as
             * soon as it is allowed to start and don't take longer than the computation started
             * after it unless the next computation is cancelled.
             */
            for (int i = 0; i < targetDefineCount; i++) {
                defineFoobarFunction(context, i);
                defineCount.incrementAndGet();
                if (i % notificationStep == 0) {
                    synchronized (waiter) {
                        waiter.notifyAll();
                    }
                }
                if (i == targetDefineCount / 2 + notificationStep / 2) {
                    cancelled.set(true);
                }
            }
            for (Future<?> future : futures) {
                future.get();
            }
            long previousResult = 0;
            for (long calculationResult : retainedSizesList) {
                if (previousResult > 1024L * 1024L || previousResult == -1L) {
                    Assert.assertTrue(String.format("previousCalculationResult = %d, calculationResult = %d", previousResult, calculationResult),
                                    previousResult > 16L * 1024 * 1024L || previousResult <= calculationResult ||
                                                    (previousResult >= 0 && calculationResult == -1L));
                }
                previousResult = calculationResult;
            }
            Assert.assertEquals(-1L, (long) retainedSizesList.get(retainedSizesList.size() - 1));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(100L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRetainedSizeMultiThreaded() throws IOException, InterruptedException, ExecutionException {
        Assume.assumeFalse(TruffleOptions.AOT);
        Assume.assumeFalse(Truffle.getRuntime() instanceof DefaultTruffleRuntime);
        Random rnd = new Random();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Long> retainedSizesList = Collections.synchronizedList(new ArrayList<>());
        try (Context context = Context.create()) {
            TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            context.initialize(InstrumentationTestLanguage.ID);
            for (int i = 0; i < 10000; i++) {
                defineFoobarFunction(context, i);
            }
            /*
             * Compute retained size for the same context in 10 parallel threads. The context is not
             * executing any code during retained size computation, only the computation threads
             * enter the context to be able to obtain TruffleContext instance easily.
             */
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(rnd.nextInt(10));
                } catch (InterruptedException ie) {
                }
                futures.add(executor.submit(() -> {
                    context.enter();
                    try {
                        retainedSizesList.add(instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), 16L * 1024L * 1024L, new AtomicBoolean(false)));
                    } finally {
                        context.leave();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            Assert.assertEquals(10, retainedSizesList.size());
            retainedSizesList.sort(Comparator.naturalOrder());
            long minRetainedSize = retainedSizesList.get(0);
            long maxRetainedSize = retainedSizesList.get(retainedSizesList.size() - 1);
            /*
             * The difference stems from different count of context thread locals arrays. These are
             * object arrays of length 0 and since we have 10 extra threads entering the context to
             * compute the retained size the difference can be at most 9 and each would take at most
             * 48 bytes in case the object alignment was be 16 bytes.
             */
            Assert.assertTrue(maxRetainedSize - minRetainedSize <= 48L * 9L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(100L, TimeUnit.SECONDS);
        }
    }
}
