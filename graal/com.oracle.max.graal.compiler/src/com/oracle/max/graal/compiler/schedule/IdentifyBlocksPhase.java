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
package com.oracle.max.graal.compiler.schedule;

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public class IdentifyBlocksPhase extends Phase {
    private final List<Block> blocks = new ArrayList<Block>();
    private NodeMap<Block> nodeToBlock;
    private Graph graph;
    private boolean scheduleAllNodes;

    public IdentifyBlocksPhase(boolean scheduleAllNodes) {
        super(scheduleAllNodes ? "FullSchedule" : "PartSchedule");
        this.scheduleAllNodes = scheduleAllNodes;
    }


    @Override
    protected void run(Graph graph) {
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
        for (Node input : n.inputs()) {
            if (input instanceof FrameState) {
                assert nodeToBlock.get(n) == null;
                nodeToBlock.set(n, b);
            }
        }

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

    private Block assignBlockNew(Node n, Block b) {
        if (b == null) {
            b = createBlock();
        }

        assert nodeToBlock.get(n) == null;
        nodeToBlock.set(n, b);
        if (b.lastNode() == null) {
            b.setFirstNode(n);
            b.setLastNode(n);
        } else {
            if (b.firstNode() != b.lastNode()) {
                b.getInstructions().add(0, b.firstNode());
            }
            b.setFirstNode(n);
        }

        return b;
    }

    public static boolean isFixed(Node n) {
        return n != null && ((n instanceof FixedNode) || n == n.graph().start());
    }

    public static boolean isBlockEnd(Node n) {
        return trueSuccessorCount(n) > 1 || n instanceof Anchor || n instanceof Return || n instanceof Unwind;
    }

    private void identifyBlocks() {

        // Identify blocks.
        for (Node n : graph.getNodes()) {
            if (n != null) {
                if (n instanceof EndNode || n instanceof Return || n instanceof Unwind || n instanceof LoopEnd || n instanceof Deoptimize) {
                    Block block = null;
                    while (nodeToBlock.get(n) == null) {
                        if (block != null && IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                            // We are at a split node => start a new block.
                            block = null;
                        }
                        block = assignBlockNew(n, block);
                        if (n.predecessors().size() == 0) {
                            // Either dead code or at a merge node => stop iteration.
                            break;
                        }
                        assert n.predecessors().size() == 1 : "preds: " + n;
                        n = n.predecessors().get(0);
                    }
                }
            }
        }

        // Connect blocks.
        for (Block block : blocks) {
            Node n = block.firstNode();
            if (n instanceof Merge) {
                for (Node usage : n.usages()) {
                    if (usage instanceof Phi || usage instanceof LoopCounter) {
                        nodeToBlock.set(usage, block);
                    }
                }
                Merge m = (Merge) n;
                for (int i = 0; i < m.endCount(); ++i) {
                    EndNode end = m.endAt(i);
                    Block predBlock = nodeToBlock.get(end);
                    predBlock.addSuccessor(block);
                }
            } else {
                for (Node pred : n.predecessors()) {
                    if (isFixed(pred)) {
                        Block predBlock = nodeToBlock.get(pred);
                        predBlock.addSuccessor(block);
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


        if (scheduleAllNodes) {

            // Add successors of loop end nodes. Makes the graph cyclic.
            for (Block block : blocks) {
                Node n = block.lastNode();
                if (n instanceof LoopEnd) {
                    LoopEnd loopEnd = (LoopEnd) n;
                    assert loopEnd.loopBegin() != null;
                    block.addSuccessor(nodeToBlock.get(loopEnd.loopBegin()));
                }
            }

            assignLatestPossibleBlockToNodes();
            sortNodesWithinBlocks();
        } else {
            computeJavaBlocks();
        }
    }

    private void computeJavaBlocks() {

        for (Block b : blocks) {
            computeJavaBlock(b);
        }
    }

    private Block computeJavaBlock(Block b) {
        if (b.javaBlock() == null) {
            if (b.getPredecessors().size() == 0) {
                b.setJavaBlock(b);
            } else if (b.getPredecessors().size() == 1) {
                Block pred = b.getPredecessors().get(0);
                if (pred.getSuccessors().size() > 1) {
                    b.setJavaBlock(b);
                } else {
                    b.setJavaBlock(computeJavaBlock(pred));
                }
            } else {
                Block dominatorBlock = b.getPredecessors().get(0);
                for (int i = 1; i < b.getPredecessors().size(); ++i) {
                    dominatorBlock = getCommonDominator(dominatorBlock, b.getPredecessors().get(i));
                }
                CiBitMap blockMap = new CiBitMap(blocks.size());
                markPredecessors(b, dominatorBlock, blockMap);

                Block result = dominatorBlock;
                L1: for (Block curBlock : blocks) {
                    if (curBlock != b && blockMap.get(curBlock.blockID())) {
                        for (Block succ : curBlock.getSuccessors()) {
                            if (!blockMap.get(succ.blockID())) {
                                result = b;
                                break L1;
                            }
                        }
                    }
                }
                b.setJavaBlock(result);
            }
        }
        return b.javaBlock();
    }

    private void markPredecessors(Block b, Block stopBlock, CiBitMap blockMap) {
        if (blockMap.get(b.blockID())) {
            return;
        }
        blockMap.set(b.blockID());
        if (b != stopBlock) {
            for (Block pred : b.getPredecessors()) {
                markPredecessors(pred, stopBlock, blockMap);
            }
        }
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
                Merge merge = phi.merge();
                Block mergeBlock = nodeToBlock.get(merge);
                assert mergeBlock != null : "no block for merge " + merge.id();
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
                for (int i = 0; i < merge.endCount(); ++i) {
                    EndNode pred = merge.endAt(i);
                    block = getCommonDominator(block, nodeToBlock.get(pred));
                }
            /*} else if (usage instanceof LoopCounter) { //TODO gd
                LoopCounter counter = (LoopCounter) usage;
                if (n == counter.init()) {
                    LoopBegin loopBegin = counter.loopBegin();
                    Block mergeBlock = nodeToBlock.get(loopBegin);
                    block = getCommonDominator(block, mergeBlock.dominator());
                }*/
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

        boolean scheduleFirst = true;
        assert !instructions.contains(b.lastNode());
        assert !instructions.contains(b.firstNode());

        if (b.firstNode() == b.lastNode()) {
            Node node = b.firstNode();
            if (!(node instanceof Merge) || node instanceof LoopEnd) {
                scheduleFirst = false;
            }
        }
        if (scheduleFirst) {
            addToSorting(b, b.firstNode(), sortedInstructions, map);
        }
        for (Node i : instructions) {
            addToSorting(b, i, sortedInstructions, map);
        }
        addToSorting(b, b.lastNode(), sortedInstructions, map);
        assert sortedInstructions.get(sortedInstructions.size() - 1) == b.lastNode() : "lastNode=" + b.lastNode() + ", firstNode=" + b.firstNode() + ", sorted(sz-1)=" + sortedInstructions.get(sortedInstructions.size() - 1);
        b.setInstructions(sortedInstructions);
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || nodeToBlock.get(i) != b || i instanceof Phi || i instanceof Local || i instanceof LoopCounter) {
            return;
        }

        FrameState state = null;
        for (Node input : i.inputs()) {
//            if (input instanceof FrameState) {
//               state = (FrameState) input;
//            } else {
                addToSorting(b, input, sortedInstructions, map);
//            }
        }

        for (Node pred : i.predecessors()) {
            addToSorting(b, pred, sortedInstructions, map);
        }

        map.mark(i);

        if (state != null) {
            addToSorting(b, state, sortedInstructions, map);
        }

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

        throw new IllegalStateException("no common dominator between " + a + " and " + b);
    }

    public static int trueSuccessorCount(Node n) {
        if (n == null) {
            return 0;
        }
        int i = 0;
        for (Node s : n.successors()) {
            if (isFixed(s)) {
                i++;
            }
        }
        return i;
    }
}
