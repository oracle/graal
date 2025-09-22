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
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.sun.management.HotSpotDiagnosticMXBean;

public class ColdCallTargetDetectionTest {
    private static final Pattern TARGET_NAME_PATTERN = Pattern.compile("ColdCallTargetDetection(\\d+)");
    private static final int TARGET_COUNT = 100;
    private static final int COMPILATION_THRESHOLD = 100;
    private static final int MAX_TRIAL_COUNT = 10;
    public static final String PROFILE_RESET_EVENT_STR = "[engine] opt reprof";

    @Test
    public void testColdCallTargetDetection() throws IOException, InterruptedException {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mx = ManagementFactory.newPlatformMXBeanProxy(
                        server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        /*
         * With libgraal, we don't have to JIT the graal compiler, and so we have to decrease the
         * size of the code cache to achieve the same pressure.
         */
        boolean usingNativeJVMCILibrary = Boolean.parseBoolean(mx.getVMOption("UseJVMCINativeLibrary").getValue());
        Runnable test = () -> {
            OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
            CountDownLatch compilationFinishedLatch = new CountDownLatch(TARGET_COUNT);
            OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
                @Override
                public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                                TruffleCompilerListener.CompilationResultInfo result) {
                    countDown(target);
                }

                @Override
                public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
                    countDown(target);
                }

                private void countDown(OptimizedCallTarget target) {
                    if (target.getRootNode().getSourceSection() instanceof SourceSection section && section.getSource() != null) {
                        Matcher matcher = TARGET_NAME_PATTERN.matcher(section.getSource().getName());
                        if (matcher.find()) {
                            compilationFinishedLatch.countDown();
                        }
                    }
                }
            };
            optimizedTruffleRuntime.addListener(listener);
            try (Context context = Context.newBuilder().allowExperimentalOptions(true) //
                            .option("engine.CompileImmediately", "false") //
                            .option("engine.BackgroundCompilation", "true") //
                            .option("engine.MultiTier", "false") //
                            .option("engine.SingleTierCompilationThreshold", String.valueOf(COMPILATION_THRESHOLD)) //
                            .option("engine.DynamicCompilationThresholds", "false") //
                            .option("engine.CompilationFailureAction", "Silent") //
                            .option("engine.TraceCompilationDetails", "true").build()) {
                Map<Integer, Source> sourceMap = new LinkedHashMap<>();
                for (int i = 0; i < TARGET_COUNT; i++) {
                    Source source = Source.newBuilder(InstrumentationTestLanguage.ID, String.format("LOOP(%d, STATEMENT)", i), "ColdCallTargetDetection" + i).build();
                    sourceMap.put(i, source);
                    for (int j = 0; j < COMPILATION_THRESHOLD; j++) {
                        context.eval(source);
                    }
                }
                if (!compilationFinishedLatch.await(5, TimeUnit.MINUTES)) {
                    throw new AssertionError("Not all compilations have finished");
                }
                Thread.sleep(2000);
                for (int i = 0; i < TARGET_COUNT; i++) {
                    Source source = sourceMap.get(i);
                    for (int j = 0; j < COMPILATION_THRESHOLD; j++) {
                        context.eval(source);
                    }
                }
            } catch (InterruptedException | IOException e) {
                throw new AssertionError();
            } finally {
                optimizedTruffleRuntime.removeListener(listener);
            }
        };
        AtomicBoolean profileResetEventFound = new AtomicBoolean();
        AtomicInteger trialCounter = new AtomicInteger();
        while (trialCounter.get() < MAX_TRIAL_COUNT && !profileResetEventFound.get()) {
            trialCounter.incrementAndGet();
            SubprocessTestUtils.newBuilder(ColdCallTargetDetectionTest.class, test).prefixVmOption("-XX:ReservedCodeCacheSize=" + (usingNativeJVMCILibrary ? "16" : "32") + "m",
                            "-XX:SweeperThreshold=90.0", "-XX:StartAggressiveSweepingAt=80").postfixVmOption("-Djdk.graal.CompilationFailureAction=Silent").onExit(
                                            new Consumer<SubprocessTestUtils.Subprocess>() {
                                                @Override
                                                public void accept(SubprocessTestUtils.Subprocess subprocess) {
                                                    for (String line : subprocess.output) {
                                                        if (line.contains(PROFILE_RESET_EVENT_STR)) {
                                                            profileResetEventFound.set(true);
                                                            break;
                                                        }
                                                    }
                                                    if (!profileResetEventFound.get() && trialCounter.get() >= MAX_TRIAL_COUNT) {
                                                        throw new AssertionError("There was no profile reset event");
                                                    }
                                                }
                                            }).run();
            if (SubprocessTestUtils.isSubprocess()) {
                profileResetEventFound.set(true);
            }
        }
    }
}
