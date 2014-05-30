/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.*;

import org.junit.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public class SimpleCFGTest extends GraalCompilerTest {

    private static void dumpGraph(final StructuredGraph graph) {
        Debug.dump(graph, "Graph");
    }

    @Test
    public void testImplies() {
        StructuredGraph graph = new StructuredGraph();

        AbstractEndNode trueEnd = graph.add(new EndNode());
        AbstractEndNode falseEnd = graph.add(new EndNode());

        BeginNode trueBegin = graph.add(new BeginNode());
        trueBegin.setNext(trueEnd);
        BeginNode falseBegin = graph.add(new BeginNode());
        falseBegin.setNext(falseEnd);

        IfNode ifNode = graph.add(new IfNode(null, trueBegin, falseBegin, 0.5));
        graph.start().setNext(ifNode);

        MergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(trueEnd);
        merge.addForwardEnd(falseEnd);
        ReturnNode returnNode = graph.add(new ReturnNode(null));
        merge.setNext(returnNode);

        dumpGraph(graph);

        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);

        List<Block> blocks = cfg.getBlocks();
        // check number of blocks
        assertDeepEquals(4, blocks.size());

        // check block - node assignment
        assertDeepEquals(blocks.get(0), cfg.blockFor(graph.start()));
        assertDeepEquals(blocks.get(0), cfg.blockFor(ifNode));
        assertDeepEquals(blocks.get(1), cfg.blockFor(trueBegin));
        assertDeepEquals(blocks.get(1), cfg.blockFor(trueEnd));
        assertDeepEquals(blocks.get(2), cfg.blockFor(falseBegin));
        assertDeepEquals(blocks.get(2), cfg.blockFor(falseEnd));
        assertDeepEquals(blocks.get(3), cfg.blockFor(merge));
        assertDeepEquals(blocks.get(3), cfg.blockFor(returnNode));

        // check postOrder
        Iterator<Block> it = cfg.postOrder().iterator();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            assertTrue(it.hasNext());
            Block b = it.next();
            assertDeepEquals(blocks.get(i), b);
        }

        // check dominators
        assertDominator(blocks.get(0), null);
        assertDominator(blocks.get(1), blocks.get(0));
        assertDominator(blocks.get(2), blocks.get(0));
        assertDominator(blocks.get(3), blocks.get(0));

        // check dominated
        assertDominatedSize(blocks.get(0), 3);
        assertDominatedSize(blocks.get(1), 0);
        assertDominatedSize(blocks.get(2), 0);
        assertDominatedSize(blocks.get(3), 0);

        // check postdominators
        assertPostdominator(blocks.get(0), blocks.get(3));
        assertPostdominator(blocks.get(1), blocks.get(3));
        assertPostdominator(blocks.get(2), blocks.get(3));
        assertPostdominator(blocks.get(3), null);
    }

    public static void assertDominator(Block block, Block expectedDominator) {
        Assert.assertEquals("dominator of " + block, expectedDominator, block.getDominator());
    }

    public static void assertDominatedSize(Block block, int size) {
        Assert.assertEquals("number of dominated blocks of " + block, size, block.getDominated().size());
    }

    public static void assertPostdominator(Block block, Block expectedPostdominator) {
        Assert.assertEquals("postdominator of " + block, expectedPostdominator, block.getPostdominator());
    }

}
