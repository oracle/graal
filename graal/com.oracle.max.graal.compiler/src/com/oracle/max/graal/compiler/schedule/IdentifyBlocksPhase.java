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

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.Verbosity;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.loop.*;
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;


public class IdentifyBlocksPhase extends Phase {

    public static class BlockFactory {
        public Block createBlock(int blockID) {
            return new Block(blockID);
        }
    }

    private final BlockFactory blockFactory;
    private final List<Block> blocks = new ArrayList<Block>();
    private NodeMap<Block> nodeToBlock;
    private NodeMap<Block> earliestCache;
    private Block startBlock;
    private StructuredGraph graph;
    private boolean scheduleAllNodes;
    private int loopCount;

    public IdentifyBlocksPhase(boolean scheduleAllNodes) {
        this(scheduleAllNodes, new BlockFactory());
    }

    public IdentifyBlocksPhase(boolean scheduleAllNodes, BlockFactory blockFactory) {
        super(scheduleAllNodes ? "FullSchedule" : "PartSchedule", false);
        this.blockFactory = blockFactory;
        this.scheduleAllNodes = scheduleAllNodes;
    }

    public void calculateAlwaysReachedBlock() {
        for (Block b : blocks) {
            calculateAlwaysReachedBlock(b);
        }
    }


    private void calculateAlwaysReachedBlock(Block b) {
        if (b.getSuccessors().size() == 1) {
            b.setAlwaysReachedBlock(b.getSuccessors().get(0));
        } else if (b.getSuccessors().size() > 1) {
            BitMap blockBitMap = new BitMap(blocks.size());
            List<Block> visitedBlocks = new ArrayList<Block>();

            // Do a fill starting at the dominated blocks and going upwards over predecessors.
            for (Block dominated : b.getDominated()) {
                for (Block pred : dominated.getPredecessors()) {
                    blockFill(blockBitMap, pred, b, visitedBlocks);
                }
            }

            boolean ok = true;

            // Find out if there is exactly 1 dominated block that is left unmarked (this is the potential merge block that is always reached).
            Block unmarkedDominated = null;
            for (Block dominated : b.getDominated()) {
                if (!blockBitMap.get(dominated.blockID())) {
                    if (unmarkedDominated != null) {
                        ok = false;
                        break;
                    }
                    assert unmarkedDominated == null : "b=" + b + ", unmarkedDominated=" + unmarkedDominated + ", dominated=" + dominated;
                    unmarkedDominated = dominated;
                }
            }

            if (ok) {
                // Check that there is no exit possible except for exception edges.
                for (Block visitedBlock : visitedBlocks) {
                    assert blockBitMap.get(visitedBlock.blockID());
                    for (Block succ : visitedBlock.getSuccessors()) {
                        if (succ != unmarkedDominated && !succ.isExceptionBlock() && !blockBitMap.get(succ.blockID())) {
                            ok = false;
                            break;
                        }
                    }

                    if (!ok) {
                        break;
                    }
                }
            }

            if (ok) {
                assert unmarkedDominated != null;
                b.setAlwaysReachedBlock(unmarkedDominated);
            }
        }
    }

    private void blockFill(BitMap blockBitMap, Block cur, Block stop, List<Block> visitedBlocks) {
        if (blockBitMap.get(cur.blockID())) {
            return;
        }

        blockBitMap.set(cur.blockID());
        visitedBlocks.add(cur);

        if (cur != stop) {
            for (Block pred : cur.getPredecessors()) {
                blockFill(blockBitMap, pred, stop, visitedBlocks);
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        this.graph = graph;
        nodeToBlock = graph.createNodeMap();
        earliestCache = graph.createNodeMap();
        identifyBlocks();
    }

    public Block getStartBlock() {
        return startBlock;
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    private Block createBlock() {
        Block b = blockFactory.createBlock(blocks.size());
        blocks.add(b);
        return b;
    }

    public int loopCount() {
        return loopCount;
    }

    private Block assignBlockNew(Node n, Block b) {
        if (b == null) {
            b = createBlock();
        }

        assert nodeToBlock.get(n) == null;
        nodeToBlock.set(n, b);

        if (n == graph.start()) {
            startBlock = b;
        }

        if (n instanceof MergeNode) {
            for (Node usage : n.usages()) {
                if (usage instanceof PhiNode) {
                    nodeToBlock.set(usage, b);
                }
            }
            if (n instanceof LoopBeginNode) {
                for (InductionVariableNode iv : ((LoopBeginNode) n).inductionVariables()) {
                    nodeToBlock.set(iv, b);
                }
            }
        }
        if (n instanceof EndNode) {
            assert b.end() == null || n == b.end();
            b.setEnd((EndNode) n);
        }
        if (b.lastNode() == null) {
            b.setFirstNode(n);
            b.setLastNode(n);
            b.getInstructions().add(n);
        } else {
            b.getInstructions().add(0, n);
            b.setFirstNode(n);
        }

        return b;
    }

    public static boolean isFixed(Node n) {
        return n != null && n instanceof FixedNode && !(n instanceof AccessNode && n.predecessor() == null);
    }

    public static boolean isBlockEnd(Node n) {
        return trueSuccessorCount(n) > 1 || n instanceof ReturnNode || n instanceof UnwindNode || n instanceof DeoptimizeNode;
    }

    private void print(boolean instructions) {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        TTY.println("Root = " + dominatorRoot);
        TTY.println("nodeToBlock :");
        TTY.println(nodeToBlock.toString());
        TTY.println("Blocks :");
        for (Block b : blocks) {
            TTY.println(b + " [S:" + b.getSuccessors() + ", P:" + b.getPredecessors() + ", D:" + b.getDominated());
            if (instructions) {
                TTY.println("  f " + b.firstNode());
                for (Node n : b.getInstructions()) {
                    TTY.println("  - " + n);
                }
                TTY.println("  l " + b.lastNode());
            }
        }
    }

    private void identifyBlocks() {

        // Identify blocks.
        for (Node n : graph.getNodes()) {
            if (n instanceof EndNode || n instanceof ReturnNode || n instanceof UnwindNode || n instanceof LoopEndNode || n instanceof DeoptimizeNode) {
                Block block = null;
                Node currentNode = n;
                Node prev = null;
                while (nodeToBlock.get(currentNode) == null) {
                    block = assignBlockNew(currentNode, block);
                    if (currentNode instanceof FixedNode) {
                        block.setProbability(((FixedNode) currentNode).probability());
                    }
                    if (currentNode.predecessor() == null) {
                        // At a merge node => stop iteration.
                        assert currentNode instanceof MergeNode || currentNode == ((StructuredGraph) currentNode.graph()).start() : currentNode;
                        break;
                    }
                    if (block != null && currentNode instanceof BeginNode) {
                        // We are at a split node => start a new block.
                        block = null;
                    }
                    prev = currentNode;
                    currentNode = currentNode.predecessor();
                    assert !(currentNode instanceof AccessNode && ((AccessNode) currentNode).next() != prev) : currentNode;
                    assert !currentNode.isDeleted() : prev + " " + currentNode;
                }
            }
        }

        // Connect blocks.
        for (Block block : blocks) {
            Node n = block.firstNode();
            if (n instanceof MergeNode) {
                MergeNode m = (MergeNode) n;
                for (Node pred : m.cfgPredecessors()) {
                    Block predBlock = nodeToBlock.get(pred);
                    predBlock.addSuccessor(block);
                }
            } else {
                if (n.predecessor() != null) {
                    if (isFixed(n.predecessor())) {
                        Block predBlock = nodeToBlock.get(n.predecessor());
                        predBlock.addSuccessor(block);
                    }
                }
            }
        }

        computeDominators();

        if (scheduleAllNodes) {
            computeLoopInformation(); // Will make the graph cyclic.
            assignBlockToNodes();
            sortNodesWithinBlocks();
        }
    }

    private void computeLoopInformation() {
        // Add successors of loop end nodes. Makes the graph cyclic.
        for (Block block : blocks) {
            Node n = block.lastNode();
            if (n instanceof LoopEndNode) {
                LoopEndNode loopEnd = (LoopEndNode) n;
                assert loopEnd.loopBegin() != null;
                Block loopBeginBlock = nodeToBlock.get(loopEnd.loopBegin());
                block.addSuccessor(loopBeginBlock);
                BitMap map = new BitMap(blocks.size());
                markBlocks(block, loopBeginBlock, map, loopCount++, block.loopDepth());
                assert loopBeginBlock.loopDepth() == block.loopDepth() && loopBeginBlock.loopIndex() == block.loopIndex();
            }
        }
    }

    private void markBlocks(Block block, Block endBlock, BitMap map, int loopIndex, int initialDepth) {
        if (map.get(block.blockID())) {
            return;
        }

        map.set(block.blockID());
        if (block.loopDepth() <= initialDepth) {
            assert block.loopDepth() == initialDepth;
            block.setLoopIndex(loopIndex);
        }
        block.setLoopDepth(block.loopDepth() + 1);

        if (block == endBlock) {
            return;
        }

        for (Block pred : block.getPredecessors()) {
            markBlocks(pred, endBlock, map, loopIndex, initialDepth);
        }

        if (block.isLoopHeader()) {
            markBlocks(nodeToBlock.get(((LoopBeginNode) block.firstNode()).loopEnd()), endBlock, map, loopIndex, initialDepth);
        }
    }

    private void assignBlockToNodes() {
        for (Node n : graph.getNodes()) {
            assignBlockToNode(n);
        }
    }

    public void assignBlockToNode(Node n) {
        if (n == null) {
            return;
        }

        assert !n.isDeleted();

        Block prevBlock = nodeToBlock.get(n);
        if (prevBlock != null) {
            return;
        }
        assert !(n instanceof PhiNode) : n;
        // if in CFG, schedule at the latest position possible in the outermost loop possible
        Block latestBlock = latestBlock(n);
        Block block;
        if (latestBlock == null) {
            block = earliestBlock(n);
        } else if (GraalOptions.ScheduleOutOfLoops && !(n instanceof VirtualObjectFieldNode) && !(n instanceof VirtualObjectNode)) {
            Block earliestBlock = earliestBlock(n);
            block = scheduleOutOfLoops(n, latestBlock, earliestBlock);
        } else {
            block = latestBlock;
        }
        assert !(n instanceof MergeNode);
        nodeToBlock.set(n, block);
        block.getInstructions().add(n);
    }

    private Block latestBlock(Node n) {
        Block block = null;
        for (Node succ : n.successors()) {
            if (succ == null) {
                continue;
            }
            assignBlockToNode(succ);
            block = getCommonDominator(block, nodeToBlock.get(succ));
        }
        ensureScheduledUsages(n);
        CommonDominatorBlockClosure cdbc = new CommonDominatorBlockClosure(block);
        for (Node usage : n.usages()) {
            blocksForUsage(n, usage, cdbc);
        }
        return cdbc.block;
    }

    private class CommonDominatorBlockClosure implements BlockClosure {
        public Block block;
        public CommonDominatorBlockClosure(Block block) {
            this.block = block;
        }
        @Override
        public void apply(Block block) {
            this.block = getCommonDominator(this.block, block);
        }
    }

    private Block earliestBlock(Node n) {
        Block earliest = nodeToBlock.get(n);
        if (earliest != null) {
            return earliest;
        }
        earliest = earliestCache.get(n);
        if (earliest != null) {
            return earliest;
        }
        BitMap bits = new BitMap(blocks.size());
        ArrayList<Node> before = new ArrayList<Node>();
        if (n.predecessor() != null) {
            before.add(n.predecessor());
        }
        for (Node input : n.inputs()) {
            before.add(input);
        }
        for (Node pred : before) {
            if (pred == null) {
                continue;
            }
            Block b = earliestBlock(pred);
            if (!bits.get(b.blockID())) {
                earliest = b;
                do {
                    bits.set(b.blockID());
                    b = b.dominator();
                } while(b != null && !bits.get(b.blockID()));
            }
        }
        if (earliest == null) {
            Block start = nodeToBlock.get(graph.start());
            assert start != null;
            return start;
        }
        earliestCache.set(n, earliest);
        return earliest;
    }


    private Block scheduleOutOfLoops(Node n, Block latestBlock, Block earliest) {
        assert latestBlock != null : "no latest : " + n;
        Block cur = latestBlock;
        Block result = latestBlock;
        while (cur.loopDepth() != 0 && cur != earliest && cur.dominator() != null) {
            Block dom = cur.dominator();
            if (dom.loopDepth() < result.loopDepth()) {
                result = dom;
            }
            cur = dom;
        }
        return result;
    }

    private void blocksForUsage(Node node, Node usage, BlockClosure closure) {
        if (usage instanceof PhiNode) {
            PhiNode phi = (PhiNode) usage;
            MergeNode merge = phi.merge();
            Block mergeBlock = nodeToBlock.get(merge);
            assert mergeBlock != null : "no block for merge " + merge.toString(Verbosity.Id);
            for (int i = 0; i < phi.valueCount(); ++i) {
                if (phi.valueAt(i) == node) {
                    if (mergeBlock.getPredecessors().size() <= i) {
                        TTY.println(merge.toString());
                        TTY.println(phi.toString());
                        TTY.println(merge.cfgPredecessors().toString());
                        TTY.println(phi.inputs().toString());
                        TTY.println("value count: " + phi.valueCount());
                    }
                closure.apply(mergeBlock.getPredecessors().get(i));
                }
            }
        } else if (usage instanceof FrameState && ((FrameState) usage).block() != null) {
            MergeNode merge = ((FrameState) usage).block();
            Block block = null;
            for (Node pred : merge.cfgPredecessors()) {
                block = getCommonDominator(block, nodeToBlock.get(pred));
            }
            closure.apply(block);
        } else if (usage instanceof LinearInductionVariableNode) {
            LinearInductionVariableNode liv = (LinearInductionVariableNode) usage;
            if (liv.isLinearInductionVariableInput(node)) {
                Block mergeBlock = nodeToBlock.get(liv.loopBegin());
                closure.apply(mergeBlock.dominator());
            }
        } else {
            assignBlockToNode(usage);
            closure.apply(nodeToBlock.get(usage));
        }
    }

    private void ensureScheduledUsages(Node node) {
        for (Node usage : node.usages().snapshot()) {
            assignBlockToNode(usage);
        }
        // now true usages are ready
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
        List<Node> sortedInstructions = new ArrayList<Node>(instructions.size() + 2);

        assert !map.isMarked(b.firstNode()) && nodeToBlock.get(b.firstNode()) == b;
        assert !map.isMarked(b.lastNode()) && nodeToBlock.get(b.lastNode()) == b;

        for (Node i : instructions) {
            addToSorting(b, i, sortedInstructions, map);
        }

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off it).
        Node lastSorted = sortedInstructions.get(sortedInstructions.size() - 1);
        if (lastSorted != b.lastNode()) {
            int idx = sortedInstructions.indexOf(b.lastNode());
            boolean canNotMove = false;
            for (int i = idx + 1; i < sortedInstructions.size(); i++) {
                if (sortedInstructions.get(i).inputs().contains(b.lastNode())) {
                    canNotMove = true;
                    break;
                }
            }
            if (canNotMove) {
                // (cwi) this was the assertion commented out below.  However, it is failing frequently when the
                // scheduler is used for debug printing in early compiler phases. This was annoying during debugging
                // when an excpetion breakpoint is set for assertion errors, so I changed it to a bailout.
                if (b.lastNode() instanceof ControlSplitNode) {
                    throw new CiBailout("");
                }
                //assert !(b.lastNode() instanceof ControlSplitNode);

                //b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.lastNode());
                sortedInstructions.add(b.lastNode());
            }
        }
        b.setInstructions(sortedInstructions);
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || nodeToBlock.get(i) != b || i instanceof PhiNode || i instanceof LocalNode || i instanceof InductionVariableNode) {
            return;
        }

        if (i instanceof WriteNode) {
            // TODO(tw): Make sure every ReadNode that is connected to the same memory state is executed before every write node.
            // WriteNode wn = (WriteNode) i;
        }

        FrameState state = null;
        WriteNode writeNode = null;
        for (Node input : i.inputs()) {
            if (input instanceof WriteNode && !map.isMarked(input) && nodeToBlock.get(input) == b) {
                writeNode = (WriteNode) input;
            } else if (input instanceof FrameState) {
                state = (FrameState) input;
            } else {
                addToSorting(b, input, sortedInstructions, map);
            }
        }

        if (i.predecessor() != null) {
            addToSorting(b, i.predecessor(), sortedInstructions, map);
        }

        map.mark(i);

        addToSorting(b, state, sortedInstructions, map);
        assert writeNode == null || !map.isMarked(writeNode);
        addToSorting(b, writeNode, sortedInstructions, map);

        // Now predecessors and inputs are scheduled => we can add this node.
        sortedInstructions.add(i);
    }

    private void computeDominators() {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        assert dominatorRoot != null;
        assert dominatorRoot.getPredecessors().size() == 0;
        BitMap visited = new BitMap(blocks.size());
        visited.set(dominatorRoot.blockID());
        LinkedList<Block> workList = new LinkedList<Block>();
        workList.add(dominatorRoot);

        int cnt = 0;
        while (!workList.isEmpty()) {
            if (cnt++ > blocks.size() * 20) {
                throw new RuntimeException("(ls) endless loop in computeDominators?");
            }
            Block b = workList.remove();

            List<Block> predecessors = b.getPredecessors();
            if (predecessors.size() == 1) {
                b.setDominator(predecessors.get(0));
            } else if (predecessors.size() > 1) {
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
        BitMap bitMap = new BitMap(blocks.size());
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
