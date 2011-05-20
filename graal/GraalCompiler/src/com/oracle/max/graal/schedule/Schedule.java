/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.schedule;

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.debug.*;


public class Schedule {
    private final List<Block> blocks = new ArrayList<Block>();
    private final NodeMap<Block> nodeToBlock;
    private final Graph graph;

    public Schedule(Graph graph) {
        this.graph = graph;
        nodeToBlock = graph.createNodeMap();
        identifyBlocks();
    }

    private Block createBlock() {
        Block b = new Block(blocks.size());
        blocks.add(b);
        return b;
    }

    private Block assignBlock(Node n) {
        Block curBlock = nodeToBlock.get(n);
        if (curBlock == null) {
            curBlock = createBlock();
            nodeToBlock.set(n, curBlock);
        }
        return curBlock;
    }

    private void identifyBlocks() {

        // Identify nodes that form the control flow.
        final NodeBitMap topDownBitMap = NodeIterator.iterate(EdgeType.SUCCESSORS, graph.start(), null, null);
        final NodeBitMap combinedBitMap = NodeIterator.iterate(EdgeType.PREDECESSORS, graph.end(), topDownBitMap, null);

        // Identify blocks.
        final ArrayList<Node> blockBeginNodes = new ArrayList<Node>();
        NodeIterator.iterate(EdgeType.SUCCESSORS, graph.start(), combinedBitMap, new NodeVisitor() {
            @Override
            public void visit(Node n) {
                Node singlePred = null;
                for (Node pred : n.predecessors()) {
                    if (pred != null && combinedBitMap.isMarked(pred)) {
                        if (singlePred == null) {
                            singlePred = pred;
                        } else {
                            // We have more than one predecessor => we are a merge block.
                            assignBlock(n);
                            blockBeginNodes.add(n);
                            return;
                        }
                    }
                }

                if (singlePred == null) {
                    // We have no predecessor => we are the start block.
                    assignBlock(n);
                    blockBeginNodes.add(n);
                } else {
                    // We have a single predecessor => check its successor count.
                    int successorCount = 0;
                    for (Node succ : singlePred.successors()) {
                        if (succ != null && combinedBitMap.isMarked(succ)) {
                            successorCount++;
                            if (successorCount > 1) {
                                // Our predecessor is a split => we need a new block.
                                assignBlock(n);
                                blockBeginNodes.add(n);
                                return;
                            }
                        }
                    }
                    nodeToBlock.set(n, nodeToBlock.get(singlePred));
                }
            }}
        );

        // Connect blocks.
        for (Node n : blockBeginNodes) {
            Block block = nodeToBlock.get(n);
            for (Node pred : n.predecessors()) {
                Block predBlock = nodeToBlock.get(pred);
                predBlock.addSuccessor(block);
            }
        }
    }

    private void print() {
        TTY.println("============================================");
        TTY.println("%d blocks", blocks.size());
        for (Block b : blocks) {
           TTY.print(b.toString());

           TTY.print(" succs=");
           for (Block succ : b.getSuccessors()) {
               TTY.print(succ + ";");
           }

           TTY.print(" preds=");
           for (Block pred : b.getPredecessors()) {
               TTY.print(pred + ";");
           }
           TTY.println();
        }


        TTY.println("============================================");
        TTY.println("%d nodes", nodeToBlock.size());
        for (Node n : graph.getNodes()) {
            if (n != null) {
                TTY.print("Node %d: %s", n.id(), n.getClass().toString());
                Block curBlock = nodeToBlock.get(n);
                if (curBlock != null) {
                    TTY.print(" %s", curBlock);
                }
                TTY.println();
            }
        }
    }
}
