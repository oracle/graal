/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

public class GraphScheduleTest extends GraalCompilerTest {

    protected void assertOrderedAfterSchedule(StructuredGraph graph, Node a, Node b) {
        SchedulePhase ibp = new SchedulePhase();
        ibp.apply(graph);
        assertOrderedAfterSchedule(ibp, a, b);
    }

    protected void assertOrderedAfterSchedule(SchedulePhase ibp, Node a, Node b) {
        NodeMap<Block> nodeToBlock = ibp.getCFG().getNodeToBlock();
        Block bBlock = nodeToBlock.get(b);
        Block aBlock = nodeToBlock.get(a);

        if (bBlock == aBlock) {
            List<ScheduledNode> instructions = ibp.nodesFor(bBlock);
            Assert.assertTrue(instructions.indexOf(b) > instructions.indexOf(a));
        } else {
            Block block = bBlock;
            while (block != null) {
                if (block == aBlock) {
                    return;
                }
                block = block.getDominator();
            }
            Assert.fail("block of A doesn't dominate the block of B");
        }
    }
}
