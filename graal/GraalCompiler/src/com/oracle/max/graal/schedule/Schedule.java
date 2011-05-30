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
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;


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
        if (b.firstNode() == null) {
            b.setFirstNode(n);
            b.setLastNode(n);
        } else {
            if (b.lastNode() != null) {
                b.getInstructions().add(b.lastNode());
            }
            b.setLastNode(n);
        }
        b.setLastNode(n);
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

                if (n instanceof LoopBegin) {
                    // a LoopBegin is always a merge
                    assignBlock(n);
                    blockBeginNodes.add(n);
                    return true;
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

            if (n instanceof Merge) {
                for (Node usage : n.usages()) {
                    if (usage instanceof Phi) {
                        nodeToBlock.set(usage, block);
                    }
                }
            }
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof FrameState) {
                FrameState f = (FrameState) n;
                if (f.predecessors().size() == 1) {
                    Block predBlock = nodeToBlock.get(f.predecessors().get(0));
                    assert predBlock != null;
                    nodeToBlock.set(f, predBlock);
                    predBlock.getInstructions().add(f);
                } else {
                    assert f.predecessors().size() == 0;
                }
            }
        }

        computeDominators();



        // Add successors of loop end nodes. Makes the graph cyclic.
        for (Node n : blockBeginNodes) {
            Block block = nodeToBlock.get(n);
            if (n instanceof LoopBegin) {
                LoopBegin loopBegin = (LoopBegin) n;
                nodeToBlock.get(loopBegin.loopEnd()).addSuccessor(block);
            }
        }

        assignLatestPossibleBlockToNodes();
        sortNodesWithinBlocks();

        //print();
    }

    private void assignLatestPossibleBlockToNodes() {
        for (Node n : graph.getNodes()) {
            assignLatestPossibleBlockToNode(n);
        }
    }

    private Block assignLatestPossibleBlockToNode(Node n) {
        if (n == null) {
            return null;
        }

        Block prevBlock = nodeToBlock.get(n);
        if (prevBlock != null) {
            return prevBlock;
        }

        Block block = null;
        for (Node succ : n.successors()) {
            block = getCommonDominator(block, assignLatestPossibleBlockToNode(succ));
        }
        for (Node usage : n.usages()) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                Merge merge = phi.block();
                Block mergeBlock = nodeToBlock.get(merge);
                assert mergeBlock != null;
                for (int i = 0; i < phi.valueCount(); ++i) {
                    if (phi.valueAt(i) == n) {
                        if (mergeBlock.getPredecessors().size() == 0) {
                            TTY.println(merge.toString());
                            TTY.println(phi.toString());
                            TTY.println(merge.predecessors().toString());
                            TTY.println("value count: " + phi.valueCount());
                        }
                        block = getCommonDominator(block, mergeBlock.getPredecessors().get(i));
                    }
                }
            } else if (usage instanceof FrameState && ((FrameState) usage).block() != null) {
                Merge merge = ((FrameState) usage).block();
                for (Node pred : merge.predecessors()) {
                    if (isCFG(pred)) {
                        block = getCommonDominator(block, nodeToBlock.get(pred));
                    }
                }
            } else {
                block = getCommonDominator(block, assignLatestPossibleBlockToNode(usage));
            }
        }

        nodeToBlock.set(n, block);
        if (block != null) {
            block.getInstructions().add(n);
        }
        return block;
    }

    private Block getCommonDominator(Block a, Block b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return commonDominator(a, b);
    }

    private void sortNodesWithinBlocks() {
        NodeBitMap map = graph.createNodeBitMap();
        for (Block b : blocks) {
            sortNodesWithinBlocks(b, map);
        }
    }

    private void sortNodesWithinBlocks(Block b, NodeBitMap map) {
        List<Node> instructions = b.getInstructions();
        List<Node> sortedInstructions = new ArrayList<Node>();
        assert !map.isMarked(b.firstNode()) && nodeToBlock.get(b.firstNode()) == b;
        if (b.firstNode() != b.lastNode()) {
            addToSorting(b, b.firstNode(), sortedInstructions, map);
        }
        for (Node i : instructions) {
            addToSorting(b, i, sortedInstructions, map);
        }
        addToSorting(b, b.lastNode(), sortedInstructions, map);
        //assert b.firstNode() == sortedInstructions.get(0) : b.firstNode();
    //    assert b.lastNode() == sortedInstructions.get(sortedInstructions.size() - 1);
        b.setInstructions(sortedInstructions);
//        TTY.println("Block " + b);
//        for (Node n : sortedInstructions) {
//            TTY.println("Node: " + n);
//        }
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || nodeToBlock.get(i) != b || i instanceof Phi || i instanceof Local) {
            return;
        }

        for (Node input : i.inputs()) {
            addToSorting(b, input, sortedInstructions, map);
        }

        for (Node pred : i.predecessors()) {
            addToSorting(b, pred, sortedInstructions, map);
        }

        map.mark(i);

        for (Node succ : i.successors()) {
            if (succ instanceof FrameState) {
                addToSorting(b, succ, sortedInstructions, map);
            }
        }

        // Now predecessors and inputs are scheduled => we can add this node.
        if (!(i instanceof FrameState)) {
            sortedInstructions.add(i);
        }
    }

    private void computeDominators() {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        assert dominatorRoot.getPredecessors().size() == 0;
        CiBitMap visited = new CiBitMap(blocks.size());
        visited.set(dominatorRoot.blockID());
        LinkedList<Block> workList = new LinkedList<Block>();
        workList.add(dominatorRoot);

        while (!workList.isEmpty()) {
            Block b = workList.remove();

            List<Block> predecessors = b.getPredecessors();
            if (predecessors.size() == 1) {
                b.setDominator(predecessors.get(0));
            } else if (predecessors.size() > 0) {
                boolean delay = false;
                for (Block pred : predecessors) {
                    if (pred != dominatorRoot && pred.dominator() == null) {
                        delay = true;
                        break;
                    }
                }

                if (delay) {
                    workList.add(b);
                    continue;
                }

                Block dominator = null;
                for (Block pred : predecessors) {
                    if (dominator == null) {
                        dominator = pred;
                    } else {
                        dominator = commonDominator(dominator, pred);
                    }
                }
                b.setDominator(dominator);
            }

            for (Block succ : b.getSuccessors()) {
                if (!visited.get(succ.blockID())) {
                    visited.set(succ.blockID());
                    workList.add(succ);
                }
            }
        }
    }

    public Block commonDominator(Block a, Block b) {
        CiBitMap bitMap = new CiBitMap(blocks.size());
        Block cur = a;
        while (cur != null) {
            bitMap.set(cur.blockID());
            cur = cur.dominator();
        }

        cur = b;
        while (cur != null) {
            if (bitMap.get(cur.blockID())) {
                return cur;
            }
            cur = cur.dominator();
        }

        print();
        assert false : "no common dominator between " + a + " and " + b;
        return null;
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

           if (b.dominator() != null) {
               TTY.print(" dom=" + b.dominator());
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
