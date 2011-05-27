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
import com.sun.c1x.ir.*;


public class Schedule {
    private final List<Block> blocks = new ArrayList<Block>();
    private final NodeMap<Block> nodeToBlock;
    private final Graph graph;

    public Schedule(Graph graph) {
        this.graph = graph;
        nodeToBlock = graph.createNodeMap();
        identifyBlocks();
    }
    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
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
            return assignBlock(n, curBlock);
        }
        return curBlock;
    }


    private Block assignBlock(Node n, Block b) {
        assert nodeToBlock.get(n) == null;
        nodeToBlock.set(n, b);
        if (n != n.graph().start()) {
            b.getInstructions().add((Instruction) n);
        }
        return b;
    }

    private boolean isCFG(Node n) {
        return n != null && ((n instanceof Instruction) || n == n.graph().start());
    }

    private void identifyBlocks() {
        // Identify blocks.
        final ArrayList<Node> blockBeginNodes = new ArrayList<Node>();
        NodeIterator.iterate(EdgeType.SUCCESSORS, graph.start(), null, new NodeVisitor() {
            @Override
            public boolean visit(Node n) {
                if (!isCFG(n)) {
                    return false;
                }

                Node singlePred = null;
                for (Node pred : n.predecessors()) {
                    if (isCFG(pred)) {
                        if (singlePred == null) {
                            singlePred = pred;
                        } else {
                            // We have more than one predecessor => we are a merge block.
                            assignBlock(n);
                            blockBeginNodes.add(n);
                            return true;
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
                        if (isCFG(succ)) {
                            successorCount++;
                            if (successorCount > 1) {
                                // Our predecessor is a split => we need a new block.
                                if (singlePred instanceof ExceptionEdgeInstruction) {
                                    ExceptionEdgeInstruction e = (ExceptionEdgeInstruction) singlePred;
                                    if (e.exceptionEdge() != n) {
                                        break;
                                    }
                                }
                                Block b = assignBlock(n);
                                b.setExceptionEntry(singlePred instanceof ExceptionEdgeInstruction);
                                blockBeginNodes.add(n);
                                return true;
                            }
                        }
                    }

                    if (singlePred instanceof BlockEnd) {
                        Block b = assignBlock(n);
                        b.setExceptionEntry(singlePred instanceof Throw);
                        blockBeginNodes.add(n);
                    } else {
                        assignBlock(n, nodeToBlock.get(singlePred));
                    }
                }
                return true;
            }}
        );

        // Connect blocks.
        for (Node n : blockBeginNodes) {
            Block block = nodeToBlock.get(n);
            for (Node pred : n.predecessors()) {
                if (isCFG(pred)) {
                    Block predBlock = nodeToBlock.get(pred);
                    predBlock.addSuccessor(block);
                }
            }
        }

        orderBlocks();
        //print();
    }

    private void orderBlocks() {
       /* List<Block> orderedBlocks = new ArrayList<Block>();
        Block startBlock = nodeToBlock.get(graph.start().start());
        List<Block> toSchedule = new ArrayList<Block>();
        toSchedule.add(startBlock);

        while (toSchedule.size() != 0) {


        }*/
    }

    private void print() {
        TTY.println("============================================");
        TTY.println("%d blocks", blocks.size());

        for (Block b : blocks) {
           TTY.println();
           TTY.print(b.toString());
           if (b.isExceptionEntry()) {
               TTY.print(" (ex)");
           }

           TTY.print(" succs=");
           for (Block succ : b.getSuccessors()) {
               TTY.print(succ + ";");
           }

           TTY.print(" preds=");
           for (Block pred : b.getPredecessors()) {
               TTY.print(pred + ";");
           }
           TTY.println();

           if (b.getInstructions().size() > 0) {
               TTY.print("first instr: " + b.getInstructions().get(0));
               TTY.print("last instr: " + b.getInstructions().get(b.getInstructions().size() - 1));
           }
        }

/*
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
        }*/
    }
}
