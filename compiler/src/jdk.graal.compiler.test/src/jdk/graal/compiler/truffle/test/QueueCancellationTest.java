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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.CompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class QueueCancellationTest {

    @Test
    public void testDiscardedTargetCanCompileAgain() throws Exception {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        SubprocessTestUtils.newBuilder(QueueCancellationTest.class, () -> {
            try {
                testDiscardedTargetCanCompileAgainImpl();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).prefixVmOption("-Dpolyglot.engine.CompilerThreads=1").timeout(Duration.ofMinutes(5)).run();
    }

    private static void testDiscardedTargetCanCompileAgainImpl() throws Exception {
        OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        try (Context context = Context.newBuilder().allowExperimentalOptions(true) //
                        .option("engine.BackgroundCompilation", "true") //
                        .option("engine.CompilerThreads", "1") //
                        .option("engine.MultiTier", "false") //
                        .option("engine.TraversingCompilationQueue", "true").build()) {
            context.enter();
            try {
                OptimizedCallTarget blocker = (OptimizedCallTarget) RootNode.createConstantNode(1).getCallTarget();
                OptimizedCallTarget candidate = (OptimizedCallTarget) RootNode.createConstantNode(2).getCallTarget();
                // Initialize the compiler before blocking its only worker.
                OptimizedCallTarget warmup = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
                warmup.compile(true);
                runtime.waitForCompilation(warmup, 120_000);
                Assert.assertTrue(warmup.isValid());

                OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
                    @Override
                    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
                        if (target == blocker) {
                            blockerStarted.countDown();
                            try {
                                Assert.assertTrue("Blocker was not released", releaseBlocker.await(30, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                        }
                    }
                };
                runtime.addListener(listener);
                try {
                    blocker.compile(true);
                    Assert.assertTrue("Compiler did not pick up blocker", blockerStarted.await(30, TimeUnit.SECONDS));
                    candidate.compile(true);
                    Field taskField = OptimizedCallTarget.class.getDeclaredField("compilationTask");
                    ReflectionUtils.setAccessible(taskField, true);
                    CompilationTask task = (CompilationTask) taskField.get(candidate);
                    Assert.assertNotNull(task);
                    Field predicateField = CompilationTask.class.getDeclaredField("cancelledPredicate");
                    ReflectionUtils.setAccessible(predicateField, true);
                    predicateField.set(task, (BooleanSupplier) () -> true);

                    releaseBlocker.countDown();
                    task.awaitCompletion(30, TimeUnit.SECONDS);
                    Assert.assertTrue("Queued task was not discarded", task.getFuture().isCancelled());
                    Assert.assertFalse("Discarded target is still submitted", candidate.isSubmittedForCompilation());

                    candidate.compile(true);
                    runtime.waitForCompilation(candidate, 30_000);
                    Assert.assertTrue("Discarded target could not compile again", candidate.isValid());
                    Assert.assertFalse(candidate.isSubmittedForCompilation());
                } finally {
                    releaseBlocker.countDown();
                    runtime.removeListener(listener);
                }
            } finally {
                context.leave();
            }
        }
    }
}
