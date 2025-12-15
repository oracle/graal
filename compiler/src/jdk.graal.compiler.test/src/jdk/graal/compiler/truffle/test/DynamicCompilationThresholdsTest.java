/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.FixedPointMath;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

public class DynamicCompilationThresholdsTest {
    private static final Pattern TARGET_NAME_PATTERN = Pattern.compile("DynamicCompilationThresholdsTest(\\d+)");
    static AtomicInteger ID = new AtomicInteger();

    static class SourceCompilation {
        private final int id;
        private final Source source;
        private final CountDownLatch compilationDoneLatch = new CountDownLatch(1);
        private final CountDownLatch compilationGoLatch = new CountDownLatch(1);

        SourceCompilation(int id, Source source) {
            this.id = id;
            this.source = source;
        }
    }

    Map<Integer, SourceCompilation> compilationMap = new ConcurrentHashMap<>();

    @Test
    public void testDynamicCompilationThreshods() throws IOException, InterruptedException {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        Runnable test = () -> {
            OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
            BlockingQueue<SourceCompilation> startedCompilationsQueue = new ArrayBlockingQueue<>(30);
            OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
                @Override
                public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                                TruffleCompilerListener.CompilationResultInfo result) {
                    if (getSourceCompilation(target) instanceof SourceCompilation compilation) {
                        compilation.compilationDoneLatch.countDown();
                    }
                }

                private SourceCompilation getSourceCompilation(OptimizedCallTarget target) {
                    if (target.getRootNode().getSourceSection() instanceof SourceSection section && section.getSource() != null) {
                        Matcher matcher = TARGET_NAME_PATTERN.matcher(section.getSource().getName());
                        if (matcher.find()) {
                            return compilationMap.get(Integer.parseInt(matcher.group(1)));
                        }
                    }
                    return null;
                }

                @Override
                public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
                    if (getSourceCompilation(target) instanceof SourceCompilation compilation) {
                        startedCompilationsQueue.add(compilation);
                        try {
                            compilation.compilationGoLatch.await();
                        } catch (InterruptedException ie) {
                            throw new AssertionError(ie);
                        }
                    }
                }
            };
            optimizedTruffleRuntime.addListener(listener);
            try (Context context = Context.newBuilder().allowExperimentalOptions(true) //
                            .option("engine.BackgroundCompilation", "true") //
                            .option("engine.CompileImmediately", "false") //
                            .option("engine.SingleTierCompilationThreshold", "1") //
                            .option("engine.MultiTier", "false") //
                            .option("engine.DynamicCompilationThresholds", "true") //
                            .option("engine.DynamicCompilationThresholdsMaxNormalLoad", "20") //
                            .option("engine.DynamicCompilationThresholdsMinNormalLoad", "10") //
                            .option("engine.DynamicCompilationThresholdsMinScale", "0.1") //
                            .option("engine.CompilerThreads", "1").build()) {
                int firstCompilationId = submitCompilation(context);
                SourceCompilation firstCompilation = startedCompilationsQueue.take();
                Assert.assertEquals(firstCompilationId, firstCompilation.id);
                LinkedList<Integer> scales = new LinkedList<>();
                int firstScale = FixedPointMath.toFixedPoint(0.1);
                Assert.assertEquals(firstScale, optimizedTruffleRuntime.compilationThresholdScale());
                scales.push(firstScale);
                for (int i = 1; i <= 10; i++) {
                    submitCompilation(context);
                    int scale = FixedPointMath.toFixedPoint(0.1 + 0.9 * i / 10);
                    Assert.assertEquals(scale, optimizedTruffleRuntime.compilationThresholdScale());
                    scales.push(scale);
                }
                for (int i = 1; i <= 10; i++) {
                    submitCompilation(context);
                    int scale = FixedPointMath.toFixedPoint(1.0);
                    Assert.assertEquals(scale, optimizedTruffleRuntime.compilationThresholdScale());
                    scales.push(scale);
                }
                for (int i = 1; i <= 10; i++) {
                    submitCompilation(context);
                    int scale = FixedPointMath.toFixedPoint(1.0 + 0.9 * i / 10);
                    Assert.assertEquals(scale, optimizedTruffleRuntime.compilationThresholdScale());
                    scales.push(scale);
                }
                allowCompilationToProceed(firstCompilationId);
                waitForCompilationDone(firstCompilationId);
                scales.pop();
                for (int i = firstCompilationId + 1; i <= 30; i++) {
                    SourceCompilation compilation = startedCompilationsQueue.take();
                    Assert.assertEquals((int) scales.pop(), optimizedTruffleRuntime.compilationThresholdScale());
                    allowCompilationToProceed(compilation.id);
                    waitForCompilationDone(compilation.id);
                }
                Assert.assertTrue(scales.isEmpty());
            } catch (IOException | InterruptedException e) {
                throw new AssertionError(e);
            } finally {
                optimizedTruffleRuntime.removeListener(listener);
            }
        };
        SubprocessTestUtils.newBuilder(DynamicCompilationThresholdsTest.class, test).run();
    }

    private void allowCompilationToProceed(int id) throws InterruptedException {
        compilationMap.get(id).compilationGoLatch.countDown();
    }

    private void waitForCompilationDone(int id) throws InterruptedException {
        if (!compilationMap.get(id).compilationDoneLatch.await(5, TimeUnit.MINUTES)) {
            throw new AssertionError("Compilation of source " + id + " did not finish in time");
        }
    }

    int submitCompilation(Context context) throws IOException {
        int id = ID.getAndIncrement();
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "CONSTANT(" + id + ")", "DynamicCompilationThresholdsTest" + id).build();
        SourceCompilation compilation = new SourceCompilation(id, source);
        compilationMap.put(id, compilation);
        context.eval(source);
        return id;
    }
}
