/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.test;

import java.util.List;

import jdk.compiler.graal.core.common.cfg.BlockMap;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeMap;
import jdk.compiler.graal.nodes.BeginNode;
import jdk.compiler.graal.nodes.DeoptimizingNode.DeoptDuring;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.ReturnNode;
import jdk.compiler.graal.nodes.StateSplit;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.StructuredGraph.ScheduleResult;
import jdk.compiler.graal.nodes.calc.AddNode;
import jdk.compiler.graal.nodes.calc.BinaryArithmeticNode;
import jdk.compiler.graal.nodes.cfg.HIRBlock;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.common.FrameStateAssignmentPhase;
import jdk.compiler.graal.phases.common.GuardLoweringPhase;
import jdk.compiler.graal.phases.common.HighTierLoweringPhase;
import jdk.compiler.graal.phases.common.MidTierLoweringPhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.compiler.graal.phases.tiers.MidTierContext;
import org.junit.Test;

public class SchedulingTest2 extends GraphScheduleTest {

    public static int testSnippet() {
        return test() + 2;
    }

    public static int test() {
        return 40;
    }

    @Test
    public void testValueProxyInputs() {
        StructuredGraph graph = parseEager("testSnippet", AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        BeginNode beginNode = graph.add(new BeginNode());
        returnNode.replaceAtPredecessor(beginNode);
        beginNode.setNext(returnNode);
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        SchedulePhase schedulePhase = new SchedulePhase(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        schedulePhase.apply(graph, getDefaultHighTierContext());
        ScheduleResult schedule = graph.getLastSchedule();
        BlockMap<List<Node>> blockToNodesMap = schedule.getBlockToNodesMap();
        NodeMap<HIRBlock> nodeToBlock = schedule.getNodeToBlockMap();
        assertDeepEquals(2, schedule.getCFG().getBlocks().length);
        for (BinaryArithmeticNode<?> node : graph.getNodes().filter(BinaryArithmeticNode.class)) {
            if (node instanceof AddNode) {
                assertTrue(node.toString() + " expected: " + nodeToBlock.get(beginNode) + " but was: " + nodeToBlock.get(node), nodeToBlock.get(node) != nodeToBlock.get(beginNode));
            }
        }

        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            HIRBlock block = nodeToBlock.get(fs);
            assertTrue(fs.toString(), block == schedule.getCFG().getStartBlock());
            for (Node usage : fs.usages()) {
                if (usage instanceof StateSplit && ((StateSplit) usage).stateAfter() == fs) {
                    assertTrue(usage.toString(), nodeToBlock.get(usage) == block);
                    if (usage != block.getBeginNode()) {
                        List<Node> map = blockToNodesMap.get(block);
                        assertTrue(map.indexOf(fs) + " < " + map.indexOf(usage), map.indexOf(fs) < map.indexOf(usage));
                    }
                }
            }
        }

        CoreProviders context = getProviders();
        new HighTierLoweringPhase(createCanonicalizerPhase()).apply(graph, context);
        MidTierContext midContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());
        new GuardLoweringPhase().apply(graph, midContext);
        new MidTierLoweringPhase(createCanonicalizerPhase()).apply(graph, context);

        FrameStateAssignmentPhase phase = new FrameStateAssignmentPhase();
        phase.apply(graph);

        schedulePhase.apply(graph, midContext);
        schedule = graph.getLastSchedule();
        blockToNodesMap = schedule.getBlockToNodesMap();
        nodeToBlock = schedule.getNodeToBlockMap();
        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            HIRBlock block = nodeToBlock.get(fs);
            assertTrue(fs.toString(), block == schedule.getCFG().getStartBlock());
            for (Node usage : fs.usages()) {
                if ((usage instanceof StateSplit && ((StateSplit) usage).stateAfter() == fs) || (usage instanceof DeoptDuring && ((DeoptDuring) usage).stateDuring() == fs)) {
                    assertTrue(usage.toString(), nodeToBlock.get(usage) == block);
                    if (usage != block.getBeginNode()) {
                        List<Node> map = blockToNodesMap.get(block);
                        assertTrue(map.indexOf(fs) + " < " + map.indexOf(usage), map.indexOf(fs) < map.indexOf(usage));
                    }
                }
            }
        }
    }
}
