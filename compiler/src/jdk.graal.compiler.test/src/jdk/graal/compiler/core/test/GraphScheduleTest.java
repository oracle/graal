/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.List;

import org.junit.Assert;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

public class GraphScheduleTest extends GraalCompilerTest {

    protected void assertOrderedAfterSchedule(StructuredGraph graph, Node a, Node b) {
        assertOrderedAfterSchedule(graph, SchedulePhase.SchedulingStrategy.LATEST, a, b);
    }

    protected void assertOrderedAfterSchedule(StructuredGraph graph, SchedulePhase.SchedulingStrategy strategy, Node a, Node b) {
        SchedulePhase.runWithoutContextOptimizations(graph, strategy);
        assertOrderedAfterLastSchedule(graph, a, b);
    }

    protected void assertOrderedAfterLastSchedule(StructuredGraph graph, Node a, Node b) {
        assertOrderedAfterSchedule(graph.getLastSchedule(), a, b);
    }

    protected void assertOrderedAfterSchedule(ScheduleResult ibp, Node a, Node b) {
        NodeMap<HIRBlock> nodeToBlock = ibp.getNodeToBlockMap();
        HIRBlock bBlock = nodeToBlock.get(b);
        HIRBlock aBlock = nodeToBlock.get(a);

        if (bBlock == aBlock) {
            List<Node> instructions = ibp.nodesFor(bBlock);
            Assert.assertTrue(a + " should be before " + b, instructions.indexOf(b) > instructions.indexOf(a));
        } else {
            HIRBlock block = bBlock;
            while (block != null) {
                if (block == aBlock) {
                    return;
                }
                block = block.getDominator();
            }
            Assert.fail("block of " + a + " doesn't dominate the block of " + b);
        }
    }
}
