/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;

public class SchedulingTest extends GraphScheduleTest {

    public static int testValueProxyInputsSnippet(int s) {
        int i = 0;
        while (true) {
            i++;
            int v = i - s * 2;
            if (i == s) {
                return v;
            }
        }
    }

    @Test
    public void testValueProxyInputs() {
        StructuredGraph graph = parseEager("testValueProxyInputsSnippet", AllowAssumptions.YES);
        for (FrameState fs : graph.getNodes().filter(FrameState.class).snapshot()) {
            fs.replaceAtUsages(null);
            GraphUtil.killWithUnusedFloatingInputs(fs);
        }
        SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.LATEST);
        schedule.apply(graph);
        NodeMap<Block> nodeToBlock = schedule.getCFG().getNodeToBlock();
        assertTrue(graph.getNodes().filter(LoopExitNode.class).count() == 1);
        LoopExitNode loopExit = graph.getNodes().filter(LoopExitNode.class).first();
        List<Node> list = schedule.nodesFor(nodeToBlock.get(loopExit));
        for (BinaryArithmeticNode<?> node : graph.getNodes().filter(BinaryArithmeticNode.class)) {
            if (!(node instanceof AddNode)) {
                assertTrue(node.toString(), nodeToBlock.get(node) == nodeToBlock.get(loopExit));
                assertTrue(list.indexOf(node) + " < " + list.indexOf(loopExit) + ", " + node + ", " + loopExit, list.indexOf(node) < list.indexOf(loopExit));
            }
        }
    }
}
