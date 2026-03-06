/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.truffle.test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * End-to-end tests for stale task handling in the traversing compilation queue.
 *
 * We run each scenario in a subprocess so that it starts with a fresh runtime where
 * {@code CompilerThreads=1} is deterministic. This keeps the test simple and avoids cross-test
 * interference from the process-global background compile queue.
 */
public class StaleCompilationTaskTest {

    private static final int THRESHOLD = 200;
    private static final int STALE_DELAY_MS = 50;
    private static final int EXTRA_WAIT_MS = 120;
    private static final int TIMEOUT_SECONDS = 15;
    private static final Duration SUBPROCESS_TIMEOUT = Duration.ofMinutes(5);

    private static final class ScenarioState {
        final CountDownLatch blockerStarted = new CountDownLatch(1);
        final CountDownLatch releaseBlocker = new CountDownLatch(1);
        final CountDownLatch candidateQueued = new CountDownLatch(1);
        final CountDownLatch candidateCompiled = new CountDownLatch(1);
        final CountDownLatch candidateDequeued = new CountDownLatch(1);

        final AtomicInteger candidateQueuedCount = new AtomicInteger();
        final AtomicInteger candidateCompiledCount = new AtomicInteger();
        final AtomicInteger candidateDequeuedCount = new AtomicInteger();
        final AtomicReference<String> candidateDequeueReason = new AtomicReference<>();
    }

    private static boolean matchesName(OptimizedCallTarget target, String expectedName) {
        SourceSection section = target.getRootNode().getSourceSection();
        return section != null && section.getSource() != null && expectedName.equals(section.getSource().getName());
    }

    private static OptimizedTruffleRuntimeListener createListener(ScenarioState state, String blockerName, String candidateName) {
        return new OptimizedTruffleRuntimeListener() {
            @Override
            public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
                if (matchesName(target, blockerName)) {
                    state.blockerStarted.countDown();
                    try {
                        Assert.assertTrue("Timed out while waiting to release blocker compilation", state.releaseBlocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ie);
                    }
                }
            }

            @Override
            public void onCompilationQueued(OptimizedCallTarget target, int tier) {
                if (matchesName(target, candidateName)) {
                    state.candidateQueuedCount.incrementAndGet();
                    state.candidateQueued.countDown();
                }
            }

            @Override
            public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                            TruffleCompilerListener.CompilationResultInfo result) {
                if (matchesName(target, candidateName)) {
                    state.candidateCompiledCount.incrementAndGet();
                    state.candidateCompiled.countDown();
                }
            }

            @Override
            public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
                if (matchesName(target, candidateName)) {
                    state.candidateDequeuedCount.incrementAndGet();
                    state.candidateDequeueReason.set(String.valueOf(reason));
                    state.candidateDequeued.countDown();
                }
            }
        };
    }

    private static Context.Builder contextBuilder(long staleDelayMs) {
        return Context.newBuilder(InstrumentationTestLanguage.ID) //
                        .allowExperimentalOptions(true) //
                        .option("engine.BackgroundCompilation", "true") //
                        .option("engine.CompilerThreads", "1") //
                        .option("engine.MultiTier", "false") //
                        .option("engine.SingleTierCompilationThreshold", String.valueOf(THRESHOLD)) //
                        .option("engine.DynamicCompilationThresholds", "false") //
                        .option("engine.TraversingCompilationQueue", "true") //
                        .option("engine.TraversingQueueStaleTaskDelay", String.valueOf(staleDelayMs)) //
                        .option("engine.OSR", "false") //
                        .option("engine.CompilationFailureAction", "Silent");
    }

    private static void evalN(Context context, Source source, int n) {
        for (int i = 0; i < n; i++) {
            context.eval(source);
        }
    }

    private static void awaitCompilationStarted(Context context, Source source, CountDownLatch startedLatch, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (startedLatch.await(1, TimeUnit.MILLISECONDS)) {
                return;
            }
            context.eval(source);
            Thread.sleep(1L);
        }
        Assert.fail(failureMessage);
    }

    private static void queueCandidateBehindBlockedCompiler(Context context, Source blockerSource, Source candidateSource, ScenarioState state) throws Exception {
        evalN(context, blockerSource, THRESHOLD);
        awaitCompilationStarted(context, blockerSource, state.blockerStarted, "Blocker target was not picked up for compilation in time");
        evalN(context, candidateSource, THRESHOLD);
        Assert.assertTrue("Candidate target was not queued in time", state.candidateQueued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static void keepCandidateActive(Context context, Source source, long durationMs) throws Exception {
        long keepActiveUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        while (System.nanoTime() < keepActiveUntil) {
            context.eval(source);
            Thread.sleep(1L);
        }
    }

    private static void assertNoCandidateDequeue(ScenarioState state, String message) throws Exception {
        Assert.assertFalse(message, state.candidateDequeued.await(300, TimeUnit.MILLISECONDS));
        Assert.assertEquals("Candidate should not be dequeued", 0, state.candidateDequeuedCount.get());
        Assert.assertNull("Candidate should not have dequeue reason", state.candidateDequeueReason.get());
    }

    private static void runInSubprocess(Runnable test) {
        try {
            SubprocessTestUtils.newBuilder(StaleCompilationTaskTest.class, test).prefixVmOption("-Dpolyglot.engine.CompilerThreads=1").timeout(SUBPROCESS_TIMEOUT).run();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void assumeQueuedBackgroundCompilation() {
        Assume.assumeFalse("Stale queue tests are not meaningful with CompileImmediately enabled",
                        CompileImmediatelyCheck.isCompileImmediately());
    }

    private static OptimizedTruffleRuntime getOptimizedRuntime() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        return (OptimizedTruffleRuntime) Truffle.getRuntime();
    }

    @Test
    public void testInactiveTaskIsDequeuedAsStale() {
        assumeQueuedBackgroundCompilation();
        runInSubprocess(() -> {
            try {
                testInactiveTaskIsDequeuedAsStaleImpl();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void testInactiveTaskIsDequeuedAsStaleImpl() throws Exception {
        OptimizedTruffleRuntime runtime = getOptimizedRuntime();

        ScenarioState state = new ScenarioState();
        String blockerName = "blocker-stale";
        String candidateName = "stale-candidate";

        OptimizedTruffleRuntimeListener listener = createListener(state, blockerName, candidateName);
        runtime.addListener(listener);
        try (Context context = contextBuilder(STALE_DELAY_MS).build()) {
            Source blockerSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", blockerName).build();
            Source staleSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(7)", candidateName).build();

            queueCandidateBehindBlockedCompiler(context, blockerSource, staleSource, state);

            Thread.sleep(STALE_DELAY_MS + EXTRA_WAIT_MS);
            state.releaseBlocker.countDown();

            Assert.assertTrue("Inactive candidate was not dequeued in time", state.candidateDequeued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assert.assertEquals("Stale compilation task", state.candidateDequeueReason.get());
            Assert.assertEquals("Candidate should be queued once", 1, state.candidateQueuedCount.get());
            Assert.assertEquals("Candidate should be dequeued once", 1, state.candidateDequeuedCount.get());
            Assert.assertEquals("Inactive candidate should not be compiled", 0, state.candidateCompiledCount.get());
            Assert.assertFalse("Inactive candidate unexpectedly compiled", state.candidateCompiled.await(250, TimeUnit.MILLISECONDS));
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testActiveTaskCompilesWithStaleDetectionEnabled() {
        assumeQueuedBackgroundCompilation();
        runInSubprocess(() -> {
            try {
                testActiveTaskCompilesWithStaleDetectionEnabledImpl();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void testActiveTaskCompilesWithStaleDetectionEnabledImpl() throws Exception {
        OptimizedTruffleRuntime runtime = getOptimizedRuntime();

        ScenarioState state = new ScenarioState();
        String blockerName = "blocker-active";
        String candidateName = "active-candidate";

        OptimizedTruffleRuntimeListener listener = createListener(state, blockerName, candidateName);
        runtime.addListener(listener);
        try (Context context = contextBuilder(STALE_DELAY_MS).build()) {
            Source blockerSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", blockerName).build();
            Source activeSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(7)", candidateName).build();

            queueCandidateBehindBlockedCompiler(context, blockerSource, activeSource, state);

            keepCandidateActive(context, activeSource, STALE_DELAY_MS + EXTRA_WAIT_MS);
            state.releaseBlocker.countDown();

            Assert.assertTrue("Active candidate should have been compiled", state.candidateCompiled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assert.assertEquals("Active candidate should be queued once", 1, state.candidateQueuedCount.get());
            Assert.assertEquals("Active candidate should compile exactly once", 1, state.candidateCompiledCount.get());
            assertNoCandidateDequeue(state, "Active candidate should not be dequeued as stale");
        } finally {
            runtime.removeListener(listener);
        }
    }

    @Test
    public void testInactiveTaskCompilesWhenStaleDetectionIsDisabled() {
        assumeQueuedBackgroundCompilation();
        runInSubprocess(() -> {
            try {
                testInactiveTaskCompilesWhenStaleDetectionIsDisabledImpl();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void testInactiveTaskCompilesWhenStaleDetectionIsDisabledImpl() throws Exception {
        OptimizedTruffleRuntime runtime = getOptimizedRuntime();

        ScenarioState state = new ScenarioState();
        String blockerName = "blocker-disabled";
        String candidateName = "inactive-no-stale-candidate";

        OptimizedTruffleRuntimeListener listener = createListener(state, blockerName, candidateName);
        runtime.addListener(listener);
        try (Context context = contextBuilder(0L).build()) {
            Source blockerSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(42)", blockerName).build();
            Source inactiveSource = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(7)", candidateName).build();

            queueCandidateBehindBlockedCompiler(context, blockerSource, inactiveSource, state);

            Thread.sleep(STALE_DELAY_MS + EXTRA_WAIT_MS);
            state.releaseBlocker.countDown();

            Assert.assertTrue("Inactive candidate should compile when stale detection is disabled", state.candidateCompiled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assert.assertEquals("Candidate should be queued once", 1, state.candidateQueuedCount.get());
            Assert.assertEquals("Candidate should compile exactly once", 1, state.candidateCompiledCount.get());
            assertNoCandidateDequeue(state, "Inactive candidate should not be dequeued when stale detection is disabled");
        } finally {
            runtime.removeListener(listener);
        }
    }
}
