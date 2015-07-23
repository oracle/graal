/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import java.util.*;

import com.oracle.graal.debug.*;

import org.junit.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.DeoptimizingNode.DeoptDuring;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import com.oracle.graal.phases.tiers.*;

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
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        BeginNode beginNode = graph.add(new BeginNode());
        returnNode.replaceAtPredecessor(beginNode);
        beginNode.setNext(returnNode);
        Debug.dump(graph, "Graph");
        SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        BlockMap<List<Node>> blockToNodesMap = schedule.getBlockToNodesMap();
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        assertDeepEquals(2, schedule.getCFG().getBlocks().size());
        for (BinaryArithmeticNode<?> node : graph.getNodes().filter(BinaryArithmeticNode.class)) {
            if (node instanceof AddNode) {
                assertTrue(node.toString() + " expected: " + nodeToBlock.get(beginNode) + " but was: " + nodeToBlock.get(node), nodeToBlock.get(node) != nodeToBlock.get(beginNode));
            }
        }

        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            Block block = nodeToBlock.get(fs);
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

        PhaseContext context = new PhaseContext(getProviders());
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, context);
        MidTierContext midContext = new MidTierContext(getProviders(), getCodeCache().getTarget(), OptimisticOptimizations.ALL, graph.method().getProfilingInfo());

        new GuardLoweringPhase().apply(graph, midContext);
        FrameStateAssignmentPhase phase = new FrameStateAssignmentPhase();
        phase.apply(graph);

        schedule = new SchedulePhase(SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        blockToNodesMap = schedule.getBlockToNodesMap();
        nodeToBlock = schedule.getNodeToBlockMap();
        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            Block block = nodeToBlock.get(fs);
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
