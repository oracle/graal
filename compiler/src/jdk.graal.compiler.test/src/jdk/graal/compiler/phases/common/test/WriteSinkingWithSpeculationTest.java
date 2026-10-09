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

package jdk.graal.compiler.phases.common.test;

import static jdk.graal.compiler.phases.common.test.WriteSinkingConsistencyTest.getWriteSinkingTestOptions;
import static jdk.graal.compiler.phases.common.test.WriteSinkingConsistencyTest.mergeOptionValues;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;

/**
 * Additional write sinking unit tests that require active speculation.
 */
public class WriteSinkingWithSpeculationTest extends GraalCompilerTest {

    // Based on GR-6109, but using a field as write target.
    static int x;
    static int sideEffect;

    public static int fn5(int[] p1, WriteSinkingConsistencyTest.Bla bla) {
        int i = 0;
        while (GraalDirectives.injectIterationCount(100, i < x)) {
            bla.a = p1[i];
            i++;
        }
        return bla.a;
    }

    public static void runLastIterationLoop(int bound, WriteSinkingConsistencyTest.Bla bla) {
        x = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(100, i < bound); i++) {
            sideEffect = fn5(new int[i], bla);
            x = i + 1;
        }
    }

    @Test
    public void testLastIterationLoop() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(LoopVectorizationPhase.Options.VectorizeLoops, false);

        testInnerLoopRemoval("runLastIterationLoop", new OptionValues(map), 16, new WriteSinkingConsistencyTest.Bla());
    }

    /**
     * Compiles a given method using the write sinking optimization. Ensures that the graph remains
     * schedulable after low-tier write sinking when speculation is active, and executes the compiled
     * method to verify normal production behavior.
     */
    public void testInnerLoopRemoval(String snippet, OptionValues additionalOptions, Object... args) {
        OptionValues completeOptions = mergeOptionValues(getWriteSinkingTestOptions(), additionalOptions);
        doTest(snippet, completeOptions);

        Result result = test(completeOptions, snippet, args);
        Assert.assertNotNull(result);
        Assert.assertNull(result.exception);
    }

    /**
     * Performs a compilation of the given snippet and checks that the graph remains schedulable
     * after low-tier write sinking.
     */
    public void doTest(String snippet, OptionValues completeOptions) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO, completeOptions);
        Suites suites = createSuites(completeOptions);
        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        suites.getMidTier().apply(graph, getDefaultMidTierContext());
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();

        LowTierContext lowTierContext = getDefaultLowTierContext();
        lowTier.apply(graph, lowTierContext);
        new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph, lowTierContext);
    }
}
