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
package com.oracle.max.graal.compiler.tests;

import java.util.*;

import org.junit.*;

import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public class GraphScheduleTest extends GraphTest {
    protected void assertOrderedAfterSchedule(StructuredGraph graph, Node a, Node b) {
        IdentifyBlocksPhase ibp = new IdentifyBlocksPhase(true);
        ibp.apply(graph);
        NodeMap<Block> nodeToBlock = ibp.getNodeToBlock();
        Block bBlock = nodeToBlock.get(b);
        Block aBlock = nodeToBlock.get(a);

        if (bBlock == aBlock) {
            List<Node> instructions = bBlock.getInstructions();
            Assert.assertTrue(instructions.indexOf(b) > instructions.indexOf(a));
        } else {
            Block block = bBlock;
            while (block != null) {
                if (block == aBlock) {
                    break;
                }
                block = block.dominator();
            }
            Assert.assertTrue(block == aBlock);
        }
    }
}
