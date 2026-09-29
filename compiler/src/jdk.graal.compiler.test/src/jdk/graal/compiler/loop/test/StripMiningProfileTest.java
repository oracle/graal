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
package jdk.graal.compiler.loop.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.blackhole;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.LoopRotationPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;

/**
 * Checks body and exit execution counts after strip mining in the regular compiler pipeline.
 */
public class StripMiningProfileTest extends GraalCompilerTest {
    private double[] expected;
    private boolean expectedInverted;
    private double expectedOuterFrequency;
    private int expectedLoopEnds;

    /**
     * Compiles with the requested strip size and disables other loop transformations.
     */
    private void check(String snippet, int stripSize, double... counts) {
        check(snippet, stripSize, false, Double.NaN, counts);
    }

    /**
     * Checks marker frequencies and whether the inner loop is head- or tail-counted. Optionally
     * checks the outer loop's local frequency.
     */
    private void check(String snippet, int stripSize, boolean inverted, double outerFrequency, double... counts) {
        expected = counts;
        expectedInverted = inverted;
        expectedOuterFrequency = outerFrequency;
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.FullUnroll, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopUnswitch, false,
                        GraalOptions.OptDuplication, false,
                        LoopInversionPhase.Options.LoopInversion, false,
                        LoopRotationPhase.Options.LoopRotation, false,
                        MidTier.Options.StripMineCountedLoops, true,
                        MidTier.Options.StripMineNonCountedLoops, false,
                        LoopVectorizationPhase.Options.VectorizeLoops, false,
                        CountedStripMiningPhase.Options.StripMineALot, true,
                        CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips, stripSize);
        getFinalGraph(getResolvedJavaMethod(snippet), options);
    }

    /**
     * Checks marker frequencies before strip mining.
     */
    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        if (expectedLoopEnds != 0) {
            Assert.assertEquals("Expected one original loop", 1, graph.getNodes(LoopBeginNode.TYPE).count());
            Assert.assertEquals("Original backedges", expectedLoopEnds, graph.getNodes(LoopBeginNode.TYPE).first().getLoopEndCount());
        }
        checkMarkerFrequencies(graph, cfg, "Before strip mining");
    }

    /**
     * Requires strip mining to occur and checks the combined frequencies of each marker.
     */
    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        Assert.assertTrue("Missing strip-mined loop", graph.getNodes(LoopBeginNode.TYPE).filter(loop -> ((LoopBeginNode) loop).getStripMinedLimit() > 0).isNotEmpty());
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        LoopsData loops = new LoopsDataProviderImpl().getLoopsData(graph);
        loops.detectCountedLoops();
        boolean foundInner = false;
        boolean foundOuter = false;
        for (Loop loop : loops.loops()) {
            if (loop.loopBegin().isCountedStripMinedInner()) {
                foundInner = true;
                Assert.assertTrue("Inner loop must remain counted", loop.isCounted());
                Assert.assertEquals("Inner counted shape", expectedInverted, loop.counted().isInverted());
            }
            if (loop.loopBegin().isCountedStripMinedOuter()) {
                foundOuter = true;
                if (!Double.isNaN(expectedOuterFrequency)) {
                    Assert.assertEquals("Outer local frequency", expectedOuterFrequency, loop.localLoopFrequency(), 1e-7 * Math.max(1, expectedOuterFrequency));
                }
            }
        }
        Assert.assertTrue("Missing counted strip-mined inner loop", foundInner);
        Assert.assertTrue("Missing counted strip-mined outer loop", foundOuter);
        checkMarkerFrequencies(graph, cfg, "After strip mining");
    }

    /**
     * Compares method-relative marker frequencies with the specified counts.
     */
    private void checkMarkerFrequencies(StructuredGraph graph, ControlFlowGraph cfg, String stage) {
        double[] actual = new double[expected.length];
        for (BlackholeNode marker : graph.getNodes().filter(BlackholeNode.class)) {
            int id = marker.getValue().asJavaConstant().asInt();
            actual[id] += cfg.blockFor(marker).getRelativeFrequency();
        }
        for (int id = 0; id < expected.length; id++) {
            Assert.assertEquals(stage + ": marker " + id, expected[id], actual[id], 1e-7 * Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, expected[id]));
        }
    }

    /**
     * Has 100 body executions and one normal exit per invocation.
     */
    public static void headSnippet(int limit) {
        for (int i = 0; injectBranchProbability(100D / 101D, i < limit); i++) {
            blackhole(0);
        }
        blackhole(1);
    }

    /**
     * Checks head-counted loops with small and large strip sizes.
     */
    @Test
    public void testHead() {
        check("headSnippet", 16, 100, 1);
        check("headSnippet", 256, 100, 1);
    }

    /**
     * A continue skips an early-exit check reached by half the body visits.
     */
    public static void continueAndEarlyExitSnippet(int limit, int skip, int stop) {
        int i = 0;
        while (injectBranchProbability(100D / 101D, i < limit)) {
            blackhole(0);
            int value = GraalDirectives.sideEffect(i);
            i++;
            if (injectBranchProbability(0.5, value == skip)) {
                blackhole(2);
                continue;
            }
            blackhole(3);
            if (injectBranchProbability(0.02, value == stop)) {
                blackhole(4);
                return;
            }
            blackhole(5);
        }
        blackhole(1);
    }

    /**
     * Checks two original backedges and an early exit with both several strips and a single strip.
     * The head test enters the body with probability c = 100/101. Half the body visits skip the
     * early-exit check, so body survival is s = 1 - 0.5 * 0.02 = 0.99. Per method invocation, header
     * visits are F = 1/(1 - c*s) = 50.5 and body visits are B = c*F = 50.
     */
    @Test
    public void testContinueAndEarlyExit() {
        double bodyVisits = 50;
        double earlyContinueVisits = bodyVisits * 0.5;
        double earlyExitCheckVisits = bodyVisits - earlyContinueVisits;
        double earlyExitVisits = earlyExitCheckVisits * 0.02;
        double regularBackedgeVisits = earlyExitCheckVisits - earlyExitVisits;
        double countedExitVisits = 1 - earlyExitVisits;
        expectedLoopEnds = 2;
        try {
            for (int stripSize : new int[]{16, 256}) {
                check("continueAndEarlyExitSnippet", stripSize, bodyVisits, countedExitVisits, earlyContinueVisits, earlyExitCheckVisits, earlyExitVisits, regularBackedgeVisits);
            }
        } finally {
            expectedLoopEnds = 0;
        }
    }

    /**
     * Has 99 body executions, with an entry guard and the exit test at the bottom of the loop.
     */
    public static void tailSnippet(int limit) {
        int i = 0;
        if (injectBranchProbability(0.99, i < limit)) {
            do {
                blackhole(0);
                GraalDirectives.sideEffect(i);
            } while (injectBranchProbability(0.99, ++i < limit));
        }
        blackhole(1);
    }

    /**
     * Checks tail-counted loops with both several strips and a single strip.
     */
    @Test
    public void testTail() {
        check("tailSnippet", 16, true, Double.NaN, 99, 1);
        check("tailSnippet", 256, true, Double.NaN, 99, 1);
    }

    /**
     * Has 50 body executions and equal normal and early exit flow.
     */
    public static void earlyExitSnippet(int limit, int stop) {
        for (int i = 0; injectBranchProbability(100D / 101D, i < limit); i++) {
            blackhole(0);
            if (injectBranchProbability(0.01, i == stop)) {
                blackhole(2);
                return;
            }
        }
        blackhole(1);
    }

    /**
     * Checks body and exit counts for a head-counted loop with an early exit.
     */
    @Test
    public void testEarlyExit() {
        check("earlyExitSnippet", 16, 50, 0.5, 0.5);
        check("earlyExitSnippet", 256, 50, 0.5, 0.5);
    }

    /**
     * Has an early exit before the tail-counted test, so the test executes less often than the body.
     */
    public static void tailEarlyExitSnippet(int limit, int stop) {
        int i = 0;
        if (injectBranchProbability(0.99, i < limit)) {
            do {
                blackhole(0);
                GraalDirectives.sideEffect(i);
                if (injectBranchProbability(0.01, i == stop)) {
                    blackhole(2);
                    return;
                }
            } while (injectBranchProbability(0.99, ++i < limit));
        }
        blackhole(1);
    }

    /**
     * Checks body and exit counts for a tail-counted loop with an early exit.
     */
    @Test
    public void testTailEarlyExit() {
        double body = 0.99 / (1 - 0.99 * 0.99);
        check("tailEarlyExitSnippet", 16, true, Double.NaN, body, 1 - body * 0.01, body * 0.01);
        check("tailEarlyExitSnippet", 256, true, Double.NaN, body, 1 - body * 0.01, body * 0.01);
    }

    /**
     * Has 1.5 body visits per loop entry and equal normal and early exit flow.
     */
    public static void tailSingleIterationStripSnippet(int limit, int stop) {
        int i = 0;
        if (injectBranchProbability(0.99, i < limit)) {
            do {
                blackhole(0);
                GraalDirectives.sideEffect(i);
                if (injectBranchProbability(1D / 3D, i == stop)) {
                    blackhole(2);
                    return;
                }
            } while (injectBranchProbability(0.5, ++i < limit));
        }
        blackhole(1);
    }

    /**
     * Checks tail-counted loops with early exits and a strip size of one.
     */
    @Test
    public void testTailSingleIterationStrip() {
        check("tailSingleIterationStripSnippet", 1, true, Double.NaN, 0.99 * 1.5, 0.505, 0.495);
    }

    /**
     * Has a hot exceptional exit before the tail test, with markers on both successor paths.
     */
    public static void tailUnwindSnippet(int limit, int stop, RuntimeException exception) {
        int i = 0;
        if (injectBranchProbability(0.99, i < limit)) {
            do {
                blackhole(0);
                GraalDirectives.sideEffect(i);
                if (injectBranchProbability(0.9998, i == stop)) {
                    blackhole(2);
                    throw exception;
                }
                blackhole(3);
            } while (injectBranchProbability(0.5, ++i < limit));
        }
        blackhole(1);
    }

    /**
     * Checks the exceptional exit flow from a throw before the tail test.
     */
    @Test
    public void testTailUnwind() {
        double body = 0.99 / (1 - 0.0002 * 0.5);
        double exceptionalExit = body * 0.9998;
        check("tailUnwindSnippet", 4096, true, Double.NaN, body, 1 - exceptionalExit, exceptionalExit, body * 0.0002);
    }

    /**
     * Has 100 body visits although each induction-variable update advances by two.
     */
    public static void strideTwoSnippet(int limit) {
        for (int i = 0; injectBranchProbability(100D / 101D, i < limit); i += 2) {
            blackhole(0);
        }
        blackhole(1);
    }

    /**
     * Checks that strips are estimated in iterations rather than induction-variable distance.
     */
    @Test
    public void testStrideTwo() {
        check("strideTwoSnippet", 16, false, 8, 100, 1);
    }

    /**
     * Enters a hot loop through a cold edge.
     */
    public static void coldSnippet(int limit, boolean enter) {
        if (injectBranchProbability(0.1, enter)) {
            for (int i = 0; injectBranchProbability(100D / 101D, i < limit); i++) {
                blackhole(0);
            }
        }
        blackhole(1);
    }

    /**
     * Checks body and exit counts when only 10% of invocations enter the loop.
     */
    @Test
    public void testColdEntry() {
        check("coldSnippet", 16, 10, 1);
    }

    /**
     * Reaches the tail test with probability 2^-52 per body visit.
     */
    public static void tailTinyCheckFlowSnippet(int limit, int stop) {
        int i = 0;
        if (injectBranchProbability(0.99, i < limit)) {
            do {
                blackhole(0);
                GraalDirectives.sideEffect(i);
                if (injectBranchProbability(1 - 0x1.0p-52, i == stop)) {
                    blackhole(2);
                    return;
                }
                blackhole(3);
            } while (injectBranchProbability(0.75, ++i < limit));
            blackhole(4);
        }
        blackhole(1);
    }

    /**
     * Checks that strip mining preserves the tiny counts at the tail test and counted exit.
     */
    @Test
    public void testTailTinyCheckFlow() {
        double survival = 0x1.0p-52;
        /*
         * Each iteration exits either through the early return (1 - survival) or through the tail
         * test (survival * 0.25). Only 99% of invocations enter the loop, so its expected body count
         * is 0.99 / total exit probability.
         */
        double body = 0.99 / ((1 - survival) + survival * 0.25);
        double checks = body * survival;
        double exit = checks * 0.25;
        check("tailTinyCheckFlowSnippet", 1, true, Double.NaN, body, 0.01 + exit, body * (1 - survival), checks, exit);
        check("tailTinyCheckFlowSnippet", 16, true, Double.NaN, body, 0.01 + exit, body * (1 - survival), checks, exit);
    }
}
