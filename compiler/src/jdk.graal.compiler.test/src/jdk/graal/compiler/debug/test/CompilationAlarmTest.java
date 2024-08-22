/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug.test;

import java.util.Optional;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.GraalCompiler.Request;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CompilationAlarmTest extends GraalCompilerTest {

    /**
     * Creates a suite with a single phase that loops for {@code workSeconds}.
     *
     * @param withProgressCounterEvents if true, a graph event is generated each loop iteration
     */
    private Suites getSuites(TestThread testThread, double workSeconds, boolean withProgressCounterEvents, OptionValues opt) {
        assert workSeconds >= 0.001D;
        Suites s = createSuites(opt).copy();
        s.getLowTier().appendPhase(new BasePhase<>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, LowTierContext context) {
                long end = (long) (workSeconds * 1000);
                long start = System.currentTimeMillis();
                try {
                    while (System.currentTimeMillis() - start < end) {
                        CompilationAlarm.checkProgress(graph);
                        if (withProgressCounterEvents) {
                            CompilationAlarm.checkProgress(graph);
                            testThread.events++;
                        }
                    }
                } finally {
                    if (CompilationAlarm.LOG_PROGRESS_DETECTION) {
                        System.out.printf("CompilationAlarmTest: %d events after %d ms of work%n", testThread.events, System.currentTimeMillis() - start);
                    }
                }
            }
        });
        return s;
    }

    private static class TestThread extends Thread {
        TestThread(Runnable runnable) {
            super(runnable);
        }

        AssertionError failure;
        int events;

        void check() {
            if (failure != null) {
                throw new AssertionError(failure);
            }
        }

    }

    private TestThread newCompilationThread(double workSeconds, boolean withProgressCounterEvents, String snippet, OptionValues opt, String expectedExceptionText) {
        TestThread t = new TestThread(new Runnable() {

            @Override
            public void run() {
                TestThread thread = (TestThread) Thread.currentThread();
                StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.YES, opt);
                ResolvedJavaMethod codeOwner = graph.method();
                CompilationIdentifier compilationId = getOrCreateCompilationId(codeOwner, graph);
                Request<CompilationResult> request = new Request<>(graph, codeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations(),
                                graph.getProfilingInfo(), getSuites(thread, workSeconds, withProgressCounterEvents, opt), createLIRSuites(opt), new CompilationResult(compilationId),
                                CompilationResultBuilderFactory.Default, null, null, true);
                try {
                    GraalCompiler.compile(request);
                    if (expectedExceptionText != null) {
                        thread.failure = new AssertionError(String.format("[events: %d] Expected an exception", thread.events));
                    }
                } catch (Throwable t1) {
                    if (expectedExceptionText == null) {
                        thread.failure = new AssertionError(String.format("[events: %d] Unexpected exception", thread.events), t1);
                    } else if (!t1.getMessage().contains(expectedExceptionText)) {
                        thread.failure = new AssertionError(String.format("[events: %d] Expected exception message to contain \"%s\"", thread.events, expectedExceptionText), t1);
                    }
                }
            }
        });
        return t;
    }

    public static void snippet() {
        GraalDirectives.sideEffect();
    }

    @Test
    public void testSingleThreadAlarmExpiration() throws InterruptedException {
        OptionValues opt = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationExpirationPeriod, 0.5D);
        TestThread t1 = newCompilationThread(5, false, "snippet", opt, "Compilation exceeded");
        t1.start();
        t1.join();

        t1.check();
    }

    /**
     * Gets options with {@code CompilationAlarm.Options#CompilationNoProgressPeriod} set to
     * {@code seconds} and {@code CompilationNoProgressStartTrackingProgressPeriod} set to 1
     * millisecond.
     */
    private static OptionValues withNoProgress(double seconds) {
        OptionValues opt = new OptionValues(getInitialOptions(),
                        CompilationAlarm.Options.CompilationNoProgressPeriod, seconds,
                        CompilationAlarm.Options.CompilationNoProgressStartTrackingProgressPeriod, 0.001);
        return opt;
    }

    @Test
    public void testSingleThreadNoTimeout() throws InterruptedException {
        TestThread t1 = newCompilationThread(0.5D, true, "snippet", withNoProgress(3), null);
        t1.start();
        t1.join();

        t1.check();
    }

    @Test
    public void testSingleThreadTimeOut() throws InterruptedException {
        TestThread t1 = newCompilationThread(2D, true, "snippet", withNoProgress(0.001), "Observed identical stack traces for");
        t1.start();
        t1.join();

        t1.check();
    }

    @Test
    public void testMultiThreadNoTimeout() throws InterruptedException {
        TestThread t1 = newCompilationThread(0.5D, true, "snippet", withNoProgress(3), null);
        TestThread t2 = newCompilationThread(0.5D, true, "snippet", withNoProgress(3), null);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        t1.check();
        t2.check();
    }

    @Test
    public void testMultiThreadOneTimeout() throws InterruptedException {
        TestThread t1 = newCompilationThread(0.5D, true, "snippet", withNoProgress(3), null);
        t1.start();
        t1.join();

        t1.check();

        TestThread t2 = newCompilationThread(2D, true, "snippet", withNoProgress(0.001), "Observed identical stack traces for");
        t2.start();
        t2.join();

        t2.check();
    }

    @Test
    public void testMultiThreadMultiTimeout() throws InterruptedException {
        TestThread t1 = newCompilationThread(2D, true, "snippet", withNoProgress(0.001), "Observed identical stack traces for");
        t1.start();
        t1.join();
        t1.check();

        TestThread t2 = newCompilationThread(2D, true, "snippet", withNoProgress(0.001), "Observed identical stack traces for");
        t2.start();
        t2.join();
        t2.check();
    }
}
