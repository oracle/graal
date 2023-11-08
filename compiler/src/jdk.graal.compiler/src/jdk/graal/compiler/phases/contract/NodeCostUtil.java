/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NodeCostUtil {

    private static final CounterKey sizeComputationCount = DebugContext.counter("GraphCostComputationCount_Size");
    private static final CounterKey sizeVerificationCount = DebugContext.counter("GraphCostVerificationCount_Size");

    public static int computeNodesSize(Iterable<Node> nodes) {
        int size = 0;
        for (Node n : nodes) {
            size += n.estimatedNodeSize().value;
        }
        assert NumUtil.assertNonNegativeInt(size);
        return size;
    }

    @SuppressWarnings("try")
    public static int computeGraphSize(StructuredGraph graph) {
        sizeComputationCount.increment(graph.getDebug());
        int size = 0;
        for (Node n : graph.getNodes()) {
            size += n.estimatedNodeSize().value;
        }
        assert NumUtil.assertNonNegativeInt(size);
        return size;
    }

    @SuppressWarnings("try")
    public static double computeGraphCycles(StructuredGraph graph, boolean fullSchedule) {
        Function<HIRBlock, Iterable<? extends Node>> blockToNodes;
        ControlFlowGraph cfg;
        if (fullSchedule) {
            SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
            cfg = graph.getLastSchedule().getCFG();
            blockToNodes = b -> graph.getLastSchedule().getBlockToNodesMap().get(b);
        } else {
            cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
            BlockMap<List<FixedNode>> nodes = new BlockMap<>(cfg);
            for (HIRBlock b : cfg.getBlocks()) {
                ArrayList<FixedNode> curNodes = new ArrayList<>();
                for (FixedNode node : b.getNodes()) {
                    curNodes.add(node);
                }
                nodes.put(b, curNodes);
            }
            blockToNodes = b -> nodes.get(b);
        }
        double weightedCycles = 0D;
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("NodeCostSummary")) {
            for (HIRBlock block : cfg.getBlocks()) {
                for (Node n : blockToNodes.apply(block)) {
                    double probWeighted = n.estimatedNodeCycles().value * block.getRelativeFrequency();
                    assert Double.isFinite(probWeighted);
                    weightedCycles += probWeighted;
                    if (debug.isLogEnabled()) {
                        debug.log("Node %s contributes cycles:%f size:%d to graph %s [block freq:%f]", n, n.estimatedNodeCycles().value * block.getRelativeFrequency(),
                                        n.estimatedNodeSize().value, graph, block.getRelativeFrequency());
                    }
                }
            }
        }
        assert NumUtil.assertNonNegativeDouble(weightedCycles);
        assert Double.isFinite(weightedCycles);
        return weightedCycles;
    }

    private static int deltaCompare(double a, double b, double delta) {
        if (Math.abs(a - b) <= delta) {
            return 0;
        }
        return Double.compare(a, b);
    }

    /**
     * Factor to control the "imprecision" of the before - after relation when verifying phase
     * effects. If the cost model is perfect the best theoretical value is 0.0D (Ignoring the fact
     * that profiling information is not reliable and thus the, probability based, profiling view on
     * a graph is different than the reality).
     */
    private static final double DELTA = 0.001D;

    public static void phaseFulfillsSizeContract(StructuredGraph graph, int codeSizeBefore, int codeSizeAfter, PhaseSizeContract contract) {
        /*
         * We use a minimal size in NodeSize before we start checking the node size increase of a
         * phase. This is to avoid reporting phase size increases for small graphs which are
         * irrelevant. The phase size checking is a means to find phases that explode graph sizes
         * for graphs which are already of a considerable size (this is subject to change in the
         * future).
         */
        if (codeSizeBefore > BasePhase.PhaseOptions.MinimalGraphNodeSizeCheckSize.getValue(graph.getOptions())) {
            sizeVerificationCount.increment(graph.getDebug());
            final double codeSizeIncrease = contract.codeSizeIncrease();
            final double graphSizeDelta = codeSizeBefore * DELTA;
            if (deltaCompare(codeSizeAfter, codeSizeBefore * codeSizeIncrease, graphSizeDelta) > 0) {
                ResolvedJavaMethod method = graph.method();
                double increase = codeSizeBefore == 0D ? codeSizeAfter : (double) codeSizeAfter / (double) codeSizeBefore;
                throw new GraalGraphError("Phase %s expects to increase code size by at most a factor of %.2f but an increase of %.2f was seen (code size before: %d, after: %d)%s",
                                contract.contractorName(), codeSizeIncrease, increase, codeSizeBefore, codeSizeAfter,
                                method != null ? " when compiling method " + method.format("%H.%n(%p)") + "." : ".");
            }
        }
    }

}
