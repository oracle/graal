/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;

/**
 * Test to ensure that {@code HotSpotSpeculationLog} yields consistent results with respect to
 * failed speculations during the course of a compilation. This is necessary for overflow
 * speculations used by Graal in {@link DisableOverflownCountedLoopsPhase}.
 */
public class HotSpotLoopOverflowSpeculationTest extends GraalCompilerTest {

    @Before
    public void checkJDKVersion() {
        Assume.assumeTrue("inconsistent speculation log fixed in 23+5", Runtime.version().compareToIgnoreOptional(Runtime.Version.parse("23+5")) >= 0);
    }

    public static final boolean LOG_STDOUT = false;

    // Snippet with a loop that can easily overflow
    public static void snippetUp(int start, int end, int stride) {
        int i = start;
        int step = ((stride - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        while (true) {
            if (i >= end) {
                break;
            }
            GraalDirectives.sideEffect();
            if (GraalDirectives.sideEffect(i) < 0) {
                if (LOG_STDOUT) {
                    TTY.printf("Overflow happened with values %s %s %s %n", start, end, stride);
                }
                return;
            }
            i += step;
        }
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL;
    }

    static volatile boolean loopCompileUntilErrorHit;
    static volatile boolean loopingStarted;

    public void startThreadAndWaitUntilLoopingStarted() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                if (LOG_STDOUT) {
                    TTY.printf("[OverflowThread] Starting waiting until compiler is at the right position...%n");
                }
                while (!loopingStarted) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw GraalError.shouldNotReachHere(e);
                    }
                }
                if (LOG_STDOUT) {
                    TTY.printf("[OverflowThread] Sleeping over, starting overflow trigger%n");
                }
                snippetUp(Integer.MAX_VALUE - 100, Integer.MAX_VALUE - 1, 7);
                if (LOG_STDOUT) {
                    TTY.printf("[OverflowThread] Sleeping over, overflow happened now - speculation can be asked again%n");
                }
                loopCompileUntilErrorHit = false;
            }
        }).start();
    }

    static AtomicInteger compiles = new AtomicInteger();

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts);
        s = s.copy();
        s.getHighTier().appendPhase(new BasePhase<HighTierContext>() {

            static CountedLoopInfo queryOverflowGuardCounted(StructuredGraph graph, HighTierContext context) {
                LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
                ld.detectCountedLoops();
                List<Loop> countedLoops = ld.countedLoops();
                GraalError.guarantee(countedLoops.size() == 1, "Must have one counted loop " + countedLoops);
                return countedLoops.get(0).counted();
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                compiles.incrementAndGet();
                HotSpotSpeculationLog hsLog = (HotSpotSpeculationLog) graph.getSpeculationLog();
                if (LOG_STDOUT) {
                    TTY.printf("[Compiler Thread compileId=%s] Initial failed speculation adr %s and data %s%n", compiles.get(), hsLog.getFailedSpeculationsAddress(),
                                    UNSAFE.getLong(hsLog.getFailedSpeculationsAddress()));
                }
                if (!loopCompileUntilErrorHit) {
                    return;
                }
                // the fact that the counted loop info below exists is enough proof that passing did
                // not find any overflow guards that failed for that loop
                CountedLoopInfo cli = queryOverflowGuardCounted(graph, context);
                if (LOG_STDOUT) {
                    TTY.printf("[Compiler Thread compileId=%s] Asking for the overflow guard - not a problem, starting looping and waiting %n", compiles.get());
                }
                loopingStarted = true;
                while (loopCompileUntilErrorHit) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw GraalError.shouldNotReachHere(e);
                    }
                }
                if (LOG_STDOUT) {
                    TTY.printf("[Compiler Thread compileId=%s] Looping over, asking again %n", compiles.get());
                    TTY.printf("[Compiler Thread compileId=%s] Failed speculation adr %s and data %s%n", compiles.get(), hsLog.getFailedSpeculationsAddress(),
                                    UNSAFE.getLong(hsLog.getFailedSpeculationsAddress()));
                }
                // ask again - this time we should fail
                cli.createOverFlowGuard();
            }

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

        });

        return s;
    }

    @Test
    public void testUpOverflow() {
        // install code
        getCode(getResolvedJavaMethod("snippetUp"), null, true, true, getInitialOptions());
        // run the code
        snippetUp(0, 1000, 3);
        // start the thread that waits until the compiler thread fails
        loopCompileUntilErrorHit = true;
        startThreadAndWaitUntilLoopingStarted();
        // force compile again, for some reason another compile is enqueued and the existing one is
        // not yet deopted
        getCode(getResolvedJavaMethod("snippetUp"), null, true, true, getInitialOptions());
    }
}
