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

        Block[] blocks = cfg.getBlocks();
        // check number of blocks
        assertEquals(4, blocks.length);

        // check block - node assignment
        assertEquals(blocks[0], cfg.blockFor(graph.start()));
        assertEquals(blocks[0], cfg.blockFor(ifNode));
        assertEquals(blocks[1], cfg.blockFor(trueBegin));
        assertEquals(blocks[1], cfg.blockFor(trueEnd));
        assertEquals(blocks[2], cfg.blockFor(falseBegin));
        assertEquals(blocks[2], cfg.blockFor(falseEnd));
        assertEquals(blocks[3], cfg.blockFor(merge));
        assertEquals(blocks[3], cfg.blockFor(returnNode));

        // check dominators
        assertDominator(blocks[0], null);
        assertDominator(blocks[1], blocks[0]);
        assertDominator(blocks[2], blocks[0]);
        assertDominator(blocks[3], blocks[0]);

        // check dominated
        assertDominatedSize(blocks[0], 3);
        assertDominatedSize(blocks[1], 0);
        assertDominatedSize(blocks[2], 0);
        assertDominatedSize(blocks[3], 0);

        // check postdominators
        assertPostdominator(blocks[0], blocks[3]);
        assertPostdominator(blocks[1], blocks[3]);
        assertPostdominator(blocks[2], blocks[3]);
        assertPostdominator(blocks[3], null);
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
