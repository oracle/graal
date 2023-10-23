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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.GraalCompiler.Request;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CompilationAlarmTest extends GraalCompilerTest {

    public static final boolean LOG = false;

    private Suites getSuites(int waitSeconds, OptionValues opt) {
        if (waitSeconds == 0) {
            return createSuites(opt);
        } else {
            Suites s = createSuites(opt);
            s = s.copy();
            s.getLowTier().appendPhase(new BasePhase<LowTierContext>() {

                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph, LowTierContext context) {
                    int msWaited = 0;
                    int callsToCheckProgress = 0;
                    if (LOG) {
                        TTY.printf("Starting to wait %s seconds - graph event counter %s %n", waitSeconds * 1000, graph.getEventCounter());
                    }
                    while (true) {
                        if (msWaited >= waitSeconds * 1000) {
                            if (LOG) {
                                TTY.printf("Finished waiting %s seconds - graph event counter %s, calls to check progress %s %n", waitSeconds * 1000, graph.getEventCounter(), callsToCheckProgress);
                            }
                            return;
                        }
                        try {
                            Thread.sleep(1);
                            CompilationAlarm.checkProgress(graph);
                            callsToCheckProgress++;
                            msWaited += 1;
                        } catch (InterruptedException e) {
                            GraalError.shouldNotReachHere(e);
                        }
                    }
                }
            });
            return s;
        }
    }

    private static class TestThread extends Thread {
        TestThread(Runnable runnable) {
            super(runnable);
        }

        boolean success;

    }

    private TestThread getCompilationThreadWithWait(int waitSeconds, String snippet, OptionValues opt, String expectedExceptionText) {
        TestThread t = new TestThread(new Runnable() {

            @Override
            public void run() {
                try {
                    StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.YES, opt);
                    ResolvedJavaMethod codeOwner = graph.method();
                    CompilationIdentifier compilationId = getOrCreateCompilationId(codeOwner, graph);
                    Request<CompilationResult> request = new Request<>(graph, codeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations(),
                                    graph.getProfilingInfo(), getSuites(waitSeconds, opt), createLIRSuites(opt), new CompilationResult(compilationId), CompilationResultBuilderFactory.Default, null,
                                    true);
                    try {
                        GraalCompiler.compile(request);
                        if (expectedExceptionText != null) {
                            Assert.fail("Must throw exception");
                        }
                    } catch (Throwable t1) {
                        if (expectedExceptionText == null) {
                            Assert.fail("Must except exception but found no excepted exception but " + t1.getMessage());
                        }
                        if (!t1.getMessage().contains(expectedExceptionText)) {
                            Assert.fail("Excepted exception to contain text:" + expectedExceptionText + " but exception did not contain text " + t1.getMessage());
                            throw t1;
                        }
                    }
                } catch (Throwable tt) {
                    throw tt;
                }
                ((TestThread) Thread.currentThread()).success = true;
            }
        });
        return t;
    }

    public static void snippet() {
        GraalDirectives.sideEffect();
    }

    private static OptionValues getOptionsWithTimeOut(int seconds, int secondsStartDetection) {
        OptionValues opt = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationNoProgressPeriod, (double) seconds,
                        CompilationAlarm.Options.CompilationNoProgressStartTrackingProgressPeriod, (double) secondsStartDetection);
        return opt;
    }

    @Test
    public void testSingleThreadNoTimeout() throws InterruptedException {
        TestThread t1 = getCompilationThreadWithWait(1, "snippet", getOptionsWithTimeOut(3, 1), null);
        t1.start();
        t1.join();

        assert t1.success;
    }

    @Test
    public void testSingleThreadTimeOut() throws InterruptedException {
        TestThread t1 = getCompilationThreadWithWait(10 * 2, "snippet", getOptionsWithTimeOut(3, 1), "Observed identical stack traces for");
        t1.start();
        t1.join();

        assert t1.success;
    }

    @Test
    public void testMultiThreadNoTimeout() throws InterruptedException {
        TestThread t1 = getCompilationThreadWithWait(1, "snippet", getOptionsWithTimeOut(3, 1), null);
        TestThread t2 = getCompilationThreadWithWait(1, "snippet", getOptionsWithTimeOut(3, 1), null);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assert t1.success;
        assert t2.success;
    }

    @Test
    public void testMultiThreadOneTimeout() throws InterruptedException {
        TestThread t1 = getCompilationThreadWithWait(1, "snippet", getOptionsWithTimeOut(3, 1), null);
        t1.start();
        t1.join();

        assert t1.success;

        TestThread t2 = getCompilationThreadWithWait(10 * 2, "snippet", getOptionsWithTimeOut(3, 1), "Observed identical stack traces for");
        t2.start();
        t2.join();

        assert t2.success;
    }

    @Test
    public void testMultiThreadMultiTimeout() throws InterruptedException {
        TestThread t1 = getCompilationThreadWithWait(20 * 2, "snippet", getOptionsWithTimeOut(9, 3), "Observed identical stack traces for");
        t1.start();
        t1.join();
        assert t1.success;

        TestThread t2 = getCompilationThreadWithWait(20 * 2, "snippet", getOptionsWithTimeOut(5, 3), "Observed identical stack traces for");
        t2.start();
        t2.join();
        assert t2.success;
    }

}
