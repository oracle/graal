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

import java.lang.reflect.Method;

import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.contract.NodeCostUtil;

public class CountedStripMiningBudgetTest extends GraalCompilerTest {

    public static long threeLoops(int n) {
        long result = 0;
        for (int i = 0; i < n; i++) {
            result = result * 31 + i;
        }
        for (int i = 0; i < n; i++) {
            result = result * 17 + i;
        }
        for (int i = 0; i < n; i++) {
            result = result * 7 + i;
        }
        return result;
    }

    @Test
    public void testBudget() throws ReflectiveOperationException {
        OptionValues options = new OptionValues(getInitialOptions(), CountedStripMiningPhase.Options.CountedStripMiningNodeSizeSmallGraphs, 0);
        StructuredGraph graph = parseEager("threeLoops", AllowAssumptions.YES, options);
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        EconomicSet<LoopBeginNode> candidates = EconomicSet.create();
        for (LoopBeginNode loop : graph.getNodes(LoopBeginNode.TYPE)) {
            candidates.add(loop);
        }
        Assert.assertEquals(3, candidates.size());
        EconomicSet<LoopBeginNode> fewCandidates = EconomicSet.create();
        Assert.assertTrue(CountedStripMiningPhase.pruneStripMiningCandidates(fewCandidates, cfg, 0).isEmpty());
        LoopBeginNode onlyCandidate = candidates.iterator().next();
        fewCandidates.add(onlyCandidate);
        EconomicSet<LoopBeginNode> selectedSingle = CountedStripMiningPhase.pruneStripMiningCandidates(fewCandidates, cfg, 0);
        Assert.assertEquals(1, selectedSingle.size());
        Assert.assertTrue(selectedSingle.contains(onlyCandidate));
        Method estimate = CountedStripMiningPhase.class.getDeclaredMethod("estimateCodeSizeIncrease", LoopBeginNode.class);
        estimate.setAccessible(true);
        int cost = (int) estimate.invoke(null, candidates.iterator().next());
        Assert.assertTrue(cost > 0);
        for (LoopBeginNode loop : candidates) {
            Assert.assertEquals(cost, estimate.invoke(null, loop));
        }
        double[] budgets = {0, 2 * cost, 3 * cost + 0.25};
        int[] expectedCounts = {1, 2, 3};
        for (int i = 0; i < budgets.length; i++) {
            double factor = budgets[i] / NodeCostUtil.computeGraphSize(graph);
            EconomicSet<LoopBeginNode> selected = CountedStripMiningPhase.pruneStripMiningCandidates(candidates, cfg, factor);
            Assert.assertEquals("Selected loops for budget " + budgets[i], expectedCounts[i], selected.size());
        }
    }
}
