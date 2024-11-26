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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.AbstractThreadedPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@SuppressWarnings("hiding")
@RunWith(Parameterized.class)
public class ContextPauseTest extends AbstractThreadedPolyglotTest {

    private static final String TEST_EXECUTION_STOPPED = "Test execution stopped!";

    private interface OnEnterAction {
        void execute(EventContext c, CountDownLatch pauseLatch, AtomicBoolean stop);
    }

    private interface GuestAction {
        void execute(Context context, CountDownLatch pauseLatch);
    }

    private interface AfterPauseAction {
        void execute(Context context, TruffleContext truffleContext, AtomicBoolean stop, List<Future<Void>> pauseFutures, List<Future<?>> guestActionFutures);
    }

    private static void testCommon(OnEnterAction onEnterAction, GuestAction guestAction, boolean waitForPause, AfterPauseAction afterPauseAction) throws ExecutionException, InterruptedException {
        int processors = Runtime.getRuntime().availableProcessors();
        for (int nThreads = 1; nThreads <= 10; nThreads += 3) {
            if (vthreads && nThreads > processors) {
                // Can hang, see https://bugs.openjdk.org/browse/JDK-8334304
                continue;
            }

            for (int nPauses = 1; nPauses <= 3; nPauses++) {
                ExecutorService executorService = threadPool(nThreads, vthreads);
                try (Context context = Context.create()) {
                    context.initialize(InstrumentationTestLanguage.ID);
                    TruffleInstrument.Env instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
                    CountDownLatch pauseLatch = new CountDownLatch(nThreads);
                    AtomicBoolean stop = new AtomicBoolean();
                    if (onEnterAction != null) {
                        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                            @Override
                            public void onEnter(EventContext c, VirtualFrame frame) {
                                onEnterSlowPath(c);
                            }

                            @TruffleBoundary
                            private void onEnterSlowPath(EventContext c) {
                                onEnterAction.execute(c, pauseLatch, stop);
                            }

                            @Override
                            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                            }

                            @Override
                            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                            }
                        });
                    }
                    TruffleContext truffleContext;
                    context.enter();
                    try {
                        truffleContext = instrumentEnv.getEnteredContext();
                    } finally {
                        context.leave();
                    }
                    List<Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < nThreads; i++) {
                        futures.add(executorService.submit(() -> {
                            guestAction.execute(context, pauseLatch);
                        }));
                    }
                    pauseLatch.await();
                    List<Future<Void>> pauseFutures = new ArrayList<>();
                    for (int i = 0; i < nPauses; i++) {
                        pauseFutures.add(truffleContext.pause());
                    }
                    if (waitForPause) {
                        for (Future<Void> pauseFuture : pauseFutures) {
                            pauseFuture.get();
                        }
                    }
                    afterPauseAction.execute(context, truffleContext, stop, pauseFutures, futures);
                    for (Future<?> future : futures) {
                        future.get();
                    }
                    executorService.shutdownNow();
                    Assert.assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
                } catch (PolyglotException pe) {
                    if (!pe.isCancelled()) {
                        throw pe;
                    }
                } finally {
                    executorService.shutdownNow();
                    // shorter timeout to show errors more quickly
                    executorService.awaitTermination(1, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Rule public TestName testNameRule = new TestName();

    @After
    public void checkInterrupted() {
        Assert.assertFalse("Interrupted flag was left set by test: " + testNameRule.getMethodName(), Thread.interrupted());
    }

    @Test
    public void testPause() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> {
                            if (c.hasTag(InstrumentationTestLanguage.ConstantTag.class)) {
                                pauseLatch.countDown();
                            }
                            if (stop.get()) {
                                throw new RuntimeException(TEST_EXECUTION_STOPPED);
                            }
                            Thread.yield();
                        },
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!("java.lang.RuntimeException: " + TEST_EXECUTION_STOPPED).equals(e.getMessage())) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> {
                            stop.set(true);
                            for (Future<Void> pauseFuture : pauseFutures) {
                                for (Future<?> future : guestActionFutures) {
                                    AbstractPolyglotTest.assertFails(() -> future.get(10, TimeUnit.MILLISECONDS), TimeoutException.class);
                                }
                                truffleContext.resume(pauseFuture);
                            }
                        });
    }

    @Test
    public void testPauseFiniteCountOfInfiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> {
                            if (stop.get()) {
                                throw new RuntimeException(TEST_EXECUTION_STOPPED);
                            }
                            Thread.yield();
                        },
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                }
                            } catch (PolyglotException e) {
                                if (!("java.lang.RuntimeException: " + TEST_EXECUTION_STOPPED).equals(e.getMessage())) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> {
                            stop.set(true);
                            for (Future<Void> pauseFuture : pauseFutures) {
                                for (Future<?> future : guestActionFutures) {
                                    AbstractPolyglotTest.assertFails(() -> future.get(10, TimeUnit.MILLISECONDS), TimeoutException.class);
                                }
                                truffleContext.resume(pauseFuture);
                            }
                        });
    }

    @Test
    public void testCancelWhilePaused() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> pauseLatch.countDown(),
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }
                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausedFiniteCountOfInifiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        null,
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                    Thread.yield();
                                }
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }

                        },
                        true,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausing() throws ExecutionException, InterruptedException {
        testCommon(
                        (c, pauseLatch, stop) -> pauseLatch.countDown(),
                        (context, pauseLatch) -> {
                            try {
                                context.eval(InstrumentationTestLanguage.ID, "ROOT(CONSTANT(42),LOOP(infinity,STATEMENT))");
                                Assert.fail();
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }
                        },
                        false,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    @Test
    public void testCancelWhilePausingFiniteCountOfInifiniteLoops() throws ExecutionException, InterruptedException {
        testCommon(
                        null,
                        (context, pauseLatch) -> {
                            context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                            pauseLatch.countDown();
                            try {
                                while (true) {
                                    context.eval(InstrumentationTestLanguage.ID, "ROOT(LOOP(100,STATEMENT))");
                                    Thread.yield();
                                }
                            } catch (PolyglotException e) {
                                if (!e.isCancelled()) {
                                    throw e;
                                }
                            }

                        },
                        false,
                        (context, truffleContext, stop, pauseFutures, guestActionFutures) -> context.close(true));
    }

    public static class CountingBoolean {
        boolean value;
        long operationCount;

        public CountingBoolean(boolean value) {
            this.value = value;
        }

        public synchronized boolean get() {
            operationCount++;
            notifyAll();
            return value;
        }

        public synchronized void set(boolean b) {
            operationCount++;
            notifyAll();
            value = b;
        }

        public synchronized void waitUntilCount(long count) {
            boolean interrupted = false;
            while (operationCount < count) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    public void testPauseManyThreads() throws ExecutionException, InterruptedException, IOException {
        int nThreads = Runtime.getRuntime().availableProcessors() + 1;
        ExecutorService executorService = threadPool(nThreads, vthreads);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Engine.Builder engineBuilder = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SynchronousThreadLocalActionMaxWait", "1").out(outputStream).err(outputStream);
        if (TruffleTestAssumptions.isOptimizingRuntime()) {
            engineBuilder.option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false");
        }
        try (Engine engine = engineBuilder.build();
                        Context context1 = Context.newBuilder().engine(engine).allowHostAccess(HostAccess.ALL).build();
                        Context context2 = Context.newBuilder().engine(engine).allowHostAccess(HostAccess.ALL).build()) {
            context1.initialize(InstrumentationTestLanguage.ID);
            context2.initialize(InstrumentationTestLanguage.ID);
            Source conditionLoopSource = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(ARGUMENT(a), COND_LOOP(INVOKE_MEMBER(get, READ_VAR(a)), STATEMENT))", "ConditionLoop").build();
            Value context1Loop = context1.parse(conditionLoopSource);
            Value context2Loop = context2.parse(conditionLoopSource);
            CountingBoolean looping = new CountingBoolean(true);
            TruffleInstrument.Env instrumentEnv = context1.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
            CountDownLatch pauseLatch = new CountDownLatch(nThreads + 1);
            CountDownLatch pauseFirstLatch = new CountDownLatch(2);
            instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext c, VirtualFrame frame) {
                    onEnterSlowPath(c);
                }

                @TruffleBoundary
                private void onEnterSlowPath(EventContext c) {
                    if (c.hasTag(StandardTags.RootTag.class)) {
                        pauseFirstLatch.countDown();
                        pauseLatch.countDown();
                    }
                    Thread.yield();
                }

                @Override
                public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                }
            });
            TruffleContext truffleContext1;
            context1.enter();
            try {
                truffleContext1 = instrumentEnv.getEnteredContext();
            } finally {
                context1.leave();
            }
            TruffleContext truffleContext2;
            context2.enter();
            try {
                truffleContext2 = instrumentEnv.getEnteredContext();
            } finally {
                context2.leave();
            }
            CountDownLatch resumeLatch = new CountDownLatch(nThreads + 1);
            CountDownLatch resumeLastLatch = new CountDownLatch(1);
            Future<?> future = executorService.submit(() -> {
                context1Loop.execute(looping);
                resumeLatch.countDown();
            });
            // Let the loop do a few iterations
            looping.waitUntilCount(10);
            looping.set(false);
            future.get();
            /*
             * First execution immediately deopts, so we need to repeat it. The code needs to be
             * compiled in order to cause pinning due to the truffle thread local handshake stub
             * calls in case virtual threads are used.
             */
            looping.set(true);
            executorService.submit(() -> {
                context1Loop.execute(looping);
                resumeLatch.countDown();
            });
            pauseFirstLatch.await();
            // One thread is executing a loop in context1 using the looping boolean condition
            Future<Void> pauseFuture1 = truffleContext1.pause();
            try {
                pauseFuture1.get();
            } catch (CancellationException e) {
                //
            }
            Assert.assertFalse(pauseFuture1.isCancelled());
            // The one thread executing context1 is now paused
            /*
             * Execute the same code in context2 in availableProcessors (nThreads - 1) threads.
             */
            for (int i = 1; i < nThreads; i++) {
                executorService.submit(() -> {
                    context2Loop.execute(looping);
                    resumeLatch.countDown();
                });
            }
            // Wait until the code is executing in all threads.
            pauseLatch.await();
            // Pause context2
            Future<Void> pauseFuture2 = truffleContext2.pause();
            try {
                pauseFuture2.get();
            } catch (CancellationException e) {
                //
            }
            /*
             * Since the total number of the threads that cause pinning is availableProcessors + 1,
             * the synchronous pause thread local action may not reach the synchronization point in
             * case virtual threads are used and is automatically cancelled. The reason for not
             * reaching the synchronization point is that when availableProcessors of these threads
             * are pinned and waiting for the start sync of the pause thread local action using
             * LockSupport.parkNanos (or already paused in case of the already paused context1
             * thread), the (availableProcessors + 1)-th thread cannot run because the parallelism
             * of the default virtual thread scheduler is only availableProcessors and none of the
             * "parked" threads, though waiting, can be compensated for due to the pinning. Also,
             * the timeout for the pause thread local action sync is pretty small (one second) so
             * the cancellation might occur even for platform threads in case some unusual slowdown.
             */
            boolean pause2Cancelled = pauseFuture2.isCancelled();
            // If context2 pausing is cancelled, new thread in context2 should not be paused even
            // though context2 was not explicitly resumed.
            executorService.submit(() -> {
                context2Loop.execute(looping);
                resumeLastLatch.countDown();
            });
            // If the pause of context2 is cancelled, we just need to unpause context1.
            truffleContext1.resume(pauseFuture1);
            if (!pause2Cancelled) {
                truffleContext2.resume(pauseFuture2);
            }
            // Finish the loop after unpausing
            looping.set(false);
            resumeLatch.await();
            resumeLastLatch.await();
            if (pause2Cancelled) {
                MatcherAssert.assertThat(outputStream.toString(), CoreMatchers.containsString("did not reach the synchronous ThreadLocalAction com.oracle.truffle.polyglot.PauseThreadLocalAction"));
            }
            /*
             * In case somebody turns on compilation tracing, they might notice an extra deopt
             * before the test ends. It is caused by the fact that the last task does not get to
             * execute the loop body because looping is already false at that point. That results in
             * a different return value type which in turn causes transfer to interpreter. In any
             * case, it does not affect the test.
             */
        } finally {
            executorService.shutdownNow();
            // shorter timeout to show errors more quickly
            Assert.assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        }

    }
}
