/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.VerificationError;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.spi.NodeCostProvider;
import com.oracle.graal.phases.schedule.SchedulePhase;

public class NodeCostUtil {

    private static final DebugCounter sizeComputationCount = Debug.counter("GraphCostComputationCount_Size");
    private static final DebugCounter sizeVerificationCount = Debug.counter("GraphCostVerificationCount_Size");

    @SuppressWarnings("try")
    public static double computeGraphSize(StructuredGraph graph, NodeCostProvider nodeCostProvider) {
        sizeComputationCount.increment();
        double size = 0;
        try (Debug.Scope s = Debug.scope("NodeCostSummary")) {
            for (Node n : graph.getNodes()) {
                size += nodeCostProvider.sizeNumeric(n);
            }
        }
        assert size >= 0D;
        assert Double.isFinite(size);
        return size;
    }

    @SuppressWarnings("try")
    public static double computeGraphCycles(StructuredGraph graph, NodeCostProvider nodeCostProvider, boolean fullSchedule) {
        Function<Block, Iterable<? extends Node>> blockToNodes;
        ControlFlowGraph cfg;
        if (fullSchedule) {
            SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
            schedule.apply(graph);
            cfg = graph.getLastSchedule().getCFG();
            cfg.computePostdominators();
            blockToNodes = b -> graph.getLastSchedule().getBlockToNodesMap().get(b);
        } else {
            cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            BlockMap<List<FixedNode>> nodes = new BlockMap<>(cfg);
            for (Block b : cfg.getBlocks()) {
                ArrayList<FixedNode> curNodes = new ArrayList<>();
                for (FixedNode node : b.getNodes()) {
                    curNodes.add(node);
                }
                nodes.put(b, curNodes);
            }
            blockToNodes = b -> nodes.get(b);
        }
        double weightedCycles = 0D;
        try (Debug.Scope s = Debug.scope("NodeCostSummary")) {
            for (Block block : cfg.getBlocks()) {
                for (Node n : blockToNodes.apply(block)) {
                    double probWeighted = nodeCostProvider.cyclesNumeric(n) * block.probability();
                    assert Double.isFinite(probWeighted);
                    weightedCycles += probWeighted;
                    if (Debug.isLogEnabled()) {
                        Debug.log("Node %s contributes cycles:%f size:%d to graph %s [block prob:%f]", n, nodeCostProvider.cyclesNumeric(n) * block.probability(),
                                        nodeCostProvider.sizeNumeric(n), graph, block.probability());
                    }
                }
            }
        }
        assert weightedCycles >= 0D;
        assert Double.isFinite(weightedCycles);
        return weightedCycles;
    }

    private static int deltaCompare(double a, double b, double delta) {
        if (Math.abs(a - b) <= delta) {
            return 0;
        } else {
            if (a < b) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Factor to control the "imprecision" of the before - after relation when verifying phase
     * effects. If the cost model is perfect the best theoretical value is 0.0D (Ignoring the fact
     * that profiling information is not reliable and thus the, probability based, profiling view on
     * a graph is different than the reality).
     */
    private static final double DELTA = 0.001D;

    public static void phaseAdheresSizeContract(StructuredGraph graph, double codeSizeBefore, double codeSizeAfter, PhaseSizeContract contract, String phaseName) {
        sizeVerificationCount.increment();
        final double codeSizeIncrease = contract.codeSizeIncrease();
        final double graphSizeDelta = codeSizeBefore * DELTA;
        if (deltaCompare(codeSizeAfter, codeSizeBefore * codeSizeIncrease, graphSizeDelta) > 0) {
            throw new VerificationError("Phase %s specifies to increase/decrease code size by a factor of %f," +
                            " but codesize.before(%f)* increaseFactor(%f) >/=/< codesize.after(%f). Real factor is:%f [Delta:%f] [Method:%s]", phaseName, codeSizeIncrease,
                            codeSizeBefore, codeSizeIncrease,
                            codeSizeAfter,
                            (codeSizeAfter / codeSizeBefore), graphSizeDelta, graph.method());
        }
    }

}
