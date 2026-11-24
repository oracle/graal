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
package jdk.graal.compiler.duplication.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.duplication.phases.simulation.HighTierDuplicationSimulationPhase;
import jdk.graal.compiler.duplication.phases.simulation.SimulationEndInfo;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

/**
 * Tests candidate isolation when simulation shares entrance facts.
 */
public class DuplicationSimulationStateTest extends GraalCompilerTest {

    private StructuredGraph prepareGraph() {
        StructuredGraph graph = parseEager(getResolvedJavaMethod(DuplicationSimulationUsageDetectionTest.class, "testUsageDetectionLinearRegion"), AllowAssumptions.YES,
                        DuplicationTestOptions.initial());
        new DisableOverflownCountedLoopsPhase().apply(graph);
        return graph;
    }

    private SimulationEndInfo[] simulate(StructuredGraph graph) {
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerTool tool = GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), graph.getOptions());
        HighTierDuplicationSimulationPhase phase = new HighTierDuplicationSimulationPhase(graph.getOptions(), FixedDuplicationSimulationConfig.defaultForDepth(4), tool,
                        (loop, g, providers) -> false);
        phase.apply(graph, context);
        return phase.getImprovements();
    }

    @Test
    public void testRepeatedCandidatesPreserveGraphAndFacts() {
        StructuredGraph graph = prepareGraph();
        int nodeCount = graph.getNodeCount();
        SimulationEndInfo[] first = simulate(graph);
        Assert.assertEquals(4, first.length);
        SimulationEndInfo[] second = simulate(graph);
        Assert.assertEquals(nodeCount, graph.getNodeCount());
        Assert.assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertSameOpportunity(first[i], second[i]);
        }
    }

    public static int branchFactsSnippet(int x, int y, boolean flag) {
        int value;
        if (flag) {
            value = x < 0 ? 0 : x;
        } else {
            value = y < 0 ? 1 : y;
        }
        GraalDirectives.controlFlowAnchor();
        if (value < 0) {
            return -1;
        }
        return value == 0 ? x : y;
    }

    @Test
    public void testCompiledBranchFacts() {
        int[] values = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
        for (int x : values) {
            for (int y : values) {
                test("branchFactsSnippet", x, y, false);
                test("branchFactsSnippet", x, y, true);
            }
        }
    }

    private static void assertSameOpportunity(SimulationEndInfo expected, SimulationEndInfo actual) {
        Assert.assertSame(expected.getEnd(), actual.getEnd());
        Assert.assertSame(expected.getOriginalMerge(), actual.getOriginalMerge());
        Assert.assertSame(expected.lastOptimizationAnchor(), actual.lastOptimizationAnchor());
        Assert.assertEquals(expected.getKilledGuards(), actual.getKilledGuards());
        Assert.assertEquals(expected.getKilledBranches(), actual.getKilledBranches());
        Assert.assertEquals(expected.getCanonicalizationImprovements(), actual.getCanonicalizationImprovements());
        Assert.assertEquals(expected.getCodeSize(), actual.getCodeSize());
        Assert.assertEquals(expected.getCyclesSaved(), actual.getCyclesSaved());
        Assert.assertEquals(expected.getCycles(), actual.getCycles());
        Assert.assertEquals(expected.getProbabilityAfter(), actual.getProbabilityAfter(), 0);
        Assert.assertEquals(expected.sinks(), actual.sinks());
        Assert.assertEquals(expected.splits(), actual.splits());
        Assert.assertEquals(expected.getRegionNodes().size(), actual.getRegionNodes().size());
        for (var node : expected.getRegionNodes()) {
            Assert.assertTrue(actual.getRegionNodes().contains(node));
        }
    }
}
