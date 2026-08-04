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

import java.util.Iterator;

import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.duplication.phases.simulation.HighTierDuplicationSimulationPhase;
import jdk.graal.compiler.duplication.phases.simulation.SimulationEndInfo;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

public class DuplicationSimulationUsageDetectionTest extends GraalCompilerTest {

    public static int SideEffect1;
    public static int SideEffect2;
    public static int SideEffect3;
    public static int SideEffect4;
    public static int SideEffect5;

    public static int testUsageDetectionLinearRegion(int a, int b, int c, int d) {
        int phi1;
        int phi2;
        int optimizablePhi;

        int result;
        if (c > d) {
            if (a > b) {
                phi1 = SideEffect1;
                phi2 = SideEffect2;
                optimizablePhi = 1;
            } else {
                phi1 = SideEffect2;
                phi2 = SideEffect1;
                optimizablePhi = 2;
            }
            SideEffect1 = phi1 + c;
            SideEffect2 = phi2 - d;
            int val1 = SideEffect3 * optimizablePhi;
            int val2 = SideEffect4 * optimizablePhi;
            int val3 = SideEffect5 * optimizablePhi;
            // do not duplicate further
            result = val1 * val2 * val3;
        } else {
            result = a;
        }
        return result;
    }

    @Test
    public void test0() {
        testGraph("testUsageDetectionLinearRegion");
    }

    private void testGraph(String snippet) {
        OptionValues options = DuplicationTestOptions.initial();
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, options);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        HighTierContext context = getDefaultHighTierContext();
        new DisableOverflownCountedLoopsPhase().apply(graph);
        CanonicalizerTool canonicalizerTool = GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), options);
        HighTierDuplicationSimulationPhase newDBDS = new HighTierDuplicationSimulationPhase(options, FixedDuplicationSimulationConfig.defaultForDepth(4), canonicalizerTool);
        newDBDS.apply(graph, context);
        SimulationEndInfo[] opportunities = newDBDS.getImprovements();
        Assert.assertEquals(4, opportunities.length);
        for (SimulationEndInfo s : opportunities) {
            if (s.getOriginalMerge().next() instanceof ReturnNode) {
                // before return now usages outside
                Assert.assertEquals(s.usagesOutside().toString(), 0, filterFS(s.usagesOutside()));
            } else {
                // intermediate merge, 3 usages outside the multiplications and the return
                // transitively
                Assert.assertEquals(s.usagesOutside().toString(), 4, filterFS(s.usagesOutside()));
            }
        }
        options = new OptionValues(options, DuplicationOptions.DuplicationBudgetFactor, 16);
        new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(16), true, true, DuplicationPhase.FACTORS_INCLUDING_PEA,
                        canonicalizer, options).apply(graph, context);
    }

    private static int filterFS(EconomicSet<Node> outSideUsages) {
        int i = 0;
        Iterator<Node> it = outSideUsages.iterator();
        while (it.hasNext()) {
            if (!(it.next() instanceof VirtualState)) {
                i++;
            }
        }
        return i;
    }
}
