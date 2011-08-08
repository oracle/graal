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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.NodeClass.*;
import com.oracle.max.graal.graph.collections.*;


public class IdentifyBlocksPhase extends Phase {
    private static final double LIVE_RANGE_COST_FACTOR = 0.05;
    private static final double MATERIALIZATION_COST_FACTOR = 1.0 - LIVE_RANGE_COST_FACTOR;

    private final List<Block> blocks = new ArrayList<Block>();
    private NodeMap<Block> nodeToBlock;
    private NodeMap<Block> earliestCache;
    private Graph graph;
    private boolean scheduleAllNodes;
    private boolean splitMaterialization;
    private int loopCount;

    public IdentifyBlocksPhase(boolean scheduleAllNodes) {
        this(scheduleAllNodes, GraalOptions.SplitMaterialization && filter());
    }

    public static boolean filter() {
        return true; //GraalCompilation.compilation().method.name().contains("mergeOrClone");
    }

    public IdentifyBlocksPhase(boolean scheduleAllNodes, boolean splitMaterialization) {
        super(scheduleAllNodes ? "FullSchedule" : "PartSchedule", false);
        this.scheduleAllNodes = scheduleAllNodes;
        this.splitMaterialization = splitMaterialization;
    }


    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        nodeToBlock = graph.createNodeMap();
        earliestCache = graph.createNodeMap();
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

    public int loopCount() {
        return loopCount;
    }

    private Block assignBlockNew(Node n, Block b) {
        if (b == null) {
            b = createBlock();
        }

        assert nodeToBlock.get(n) == null;
        nodeToBlock.set(n, b);

        if (n instanceof Merge) {
            for (Node usage : n.usages()) {

                if (usage instanceof Phi) {
                    nodeToBlock.set(usage, b);
                }

                if (usage instanceof LoopCounter) {
                    nodeToBlock.set(usage, b);
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
        return n != null && ((n instanceof FixedNode) || n == n.graph().start()) && !(n instanceof AccessNode && n.predecessor() == null);
    }

    public static boolean isBlockEnd(Node n) {
        return trueSuccessorCount(n) > 1 || n instanceof Return || n instanceof Unwind || n instanceof Deoptimize;
    }

    private void print() {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        System.out.println("Root = " + dominatorRoot);
        System.out.println("nodeToBlock :");
        System.out.println(nodeToBlock);
        System.out.println("Blocks :");
        for (Block b : blocks) {
            System.out.println(b + " [S:" + b.getSuccessors() + ", P:" + b.getPredecessors() + ", D:" + b.getDominated());
            System.out.println("  f " + b.firstNode());
            for (Node n : b.getInstructions()) {
                System.out.println("  - " + n);
            }
            System.out.println("  l " + b.lastNode());
        }
    }

    private void identifyBlocks() {

        // Identify blocks.
        for (Node n : graph.getNodes()) {
            if (n instanceof EndNode || n instanceof Return || n instanceof Unwind || n instanceof LoopEnd || n instanceof Deoptimize) {
                Block block = null;
                Node currentNode = n;
                while (nodeToBlock.get(currentNode) == null) {
                    if (block != null && (currentNode instanceof ControlSplit || trueSuccessorCount(currentNode) > 1)) {
                        // We are at a split node => start a new block.
                        block = null;
                    }
                    block = assignBlockNew(currentNode, block);
                    if (currentNode instanceof FixedNode) {
                        block.setProbability(((FixedNode) currentNode).probability());
                    }
                    if (currentNode.predecessor() == null) {
                        // Either dead code or at a merge node => stop iteration.
                        break;
                    }
                    Node prev = currentNode;
                    currentNode = currentNode.predecessor();
                    assert !(currentNode instanceof AccessNode && ((AccessNode) currentNode).next() != prev) : currentNode;
                    assert !currentNode.isDeleted() : prev + " " + currentNode;
                }
            }
        }

        // Connect blocks.
        for (Block block : blocks) {
            Node n = block.firstNode();
            if (n instanceof Merge) {
                Merge m = (Merge) n;
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
        } else {
            computeJavaBlocks();
        }
    }

    private void computeLoopInformation() {

        // Add successors of loop end nodes. Makes the graph cyclic.
        for (Block block : blocks) {
            Node n = block.lastNode();
            if (n instanceof LoopEnd) {
                LoopEnd loopEnd = (LoopEnd) n;
                assert loopEnd.loopBegin() != null;
                Block loopBeginBlock = nodeToBlock.get(loopEnd.loopBegin());
                block.addSuccessor(loopBeginBlock);
                BitMap map = new BitMap(blocks.size());
                markBlocks(block, loopBeginBlock, map, loopCount++, block.loopDepth());
                assert loopBeginBlock.loopDepth() == block.loopDepth() && loopBeginBlock.loopIndex() == block.loopIndex();
            }
        }

//        for (Block block : blocks) {
//            TTY.println("Block B" + block.blockID() + " loopIndex=" + block.loopIndex() + ", loopDepth=" + block.loopDepth());
//        }
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
            markBlocks(nodeToBlock.get(((LoopBegin) block.firstNode()).loopEnd()), endBlock, map, loopIndex, initialDepth);
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
                BitMap blockMap = new BitMap(blocks.size());
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

    private void markPredecessors(Block b, Block stopBlock, BitMap blockMap) {
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
        //TTY.println("Earliest for " + n + " : " + earliest);
        // if in CFG, schedule at the latest position possible in the outermost loop possible
        // if floating, use rematerialization to place the node, it tries to compute the value only when it will be used,
        // in the block with the smallest probability (outside of loops), while minimizing live ranges
        if (!splitMaterialization || noRematerialization(n) || n.predecessor() != null || nonNullSuccessorCount(n) > 0) {
            Block latestBlock = latestBlock(n);
            //TTY.println("Latest for " + n + " : " + latestBlock);
            Block block;
            if (latestBlock == null) {
                block = earliestBlock(n);
            } else if (GraalOptions.ScheduleOutOfLoops && !(n instanceof VirtualObjectField) && !(n instanceof VirtualObject)) {
                block = scheduleOutOfLoops(n, latestBlock, earliestBlock(n));
            } else {
                block = latestBlock;
            }
            nodeToBlock.set(n, block);
            block.getInstructions().add(n);
        } else {
            GraalTimers timer = GraalTimers.get("earliest");
            timer.start();
            Block earliest = earliestBlock(n);
            timer.stop();
            Map<Block, Set<Usage>> usages = computeUsages(n, earliest);
            if (usages.isEmpty()) {
                nodeToBlock.set(n, earliest);
                earliest.getInstructions().add(n);
            } else {
                maybeMaterialize(n, earliest, usages, false);
            }
        }
    }

    private int nonNullSuccessorCount(Node n) {
        int suxCount = 0;
        for (Node sux : n.successors()) {
            if (sux != null) {
                suxCount++;
            }
        }
        return suxCount;
    }


    private void maybeMaterialize(Node node, Block block, Map<Block, Set<Usage>> usages, boolean materializedInDominator) {
        Set<Usage> blockUsages = usages.get(block);
        if (blockUsages == null || blockUsages.isEmpty()) {
            return;
        }
        boolean forced = false;
        if (!materializedInDominator) {
            for (Usage usage : blockUsages) {
                if (usage.block == block) {
                    forced = true;
                    break;
                }
            }
        }
        //TODO (gd) if materializedInDominator, compare cost of materialization to cost of current live range instead
        if (forced || materializationCost(block, blockUsages) < materializationCostAtChildren(block, usages)) {
            Node n;
            if (nodeToBlock.get(node) == null) {
                n = node;
            } else {
                n = node.clone(node.graph());
                for (NodeClassIterator iter = node.inputs().iterator(); iter.hasNext();) {
                    Position pos = iter.nextPosition();
                    n.getNodeClass().set(n, pos, node.getNodeClass().get(node, pos));
                }
                for (Usage usage : blockUsages) {
                    patch(usage, node, n);
                }
                //TTY.println("> Rematerialized " + node + " (new node id = " + n.id() + ") in " + block);
                GraalMetrics.Rematerializations++;
                nodeToBlock.grow(n);
            }
            materializedInDominator = true;
            nodeToBlock.set(n, block);
            block.getInstructions().add(n);
        }
        if (!materializedInDominator || GraalOptions.Rematerialize) {
            for (Block child : block.getDominated()) {
                maybeMaterialize(node, child, usages, materializedInDominator);
            }
        }
    }

    private double materializationCostAtChildren(Block block, Map<Block, Set<Usage>> usages) {
        double cost = 0.0;
        for (Block child : block.getDominated()) {
            Set<Usage> blockUsages = usages.get(child);
            if (blockUsages != null && !blockUsages.isEmpty()) { // XXX should never be empty if not null
                cost += materializationCost(child, blockUsages);
            }
        }
        return cost;
    }

    private double materializationCost(Block block, Set<Usage> usages) {
        //TODO node cost
        return /*LIVE_RANGE_COST_FACTOR * liveRange(block, usages) + MATERIALIZATION_COST_FACTOR **/ block.probability() * 1.0;
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
        Block prevDepth = latestBlock;
        while (cur.loopDepth() != 0 && cur != earliest && cur.dominator() != null && cur.dominator().loopDepth() <= cur.loopDepth()) {
            Block dom = cur.dominator();
            if (dom.loopDepth() < prevDepth.loopDepth()) {
                prevDepth = dom;
            }
            cur = dom;
        }
        return prevDepth;
    }

    private void blocksForUsage(Node node, Node usage, BlockClosure closure) {
        if (usage instanceof Phi) {
            Phi phi = (Phi) usage;
            Merge merge = phi.merge();
            Block mergeBlock = nodeToBlock.get(merge);
            assert mergeBlock != null : "no block for merge " + merge.id();
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
            Merge merge = ((FrameState) usage).block();
            Block block = null;
            for (Node pred : merge.cfgPredecessors()) {
                block = getCommonDominator(block, nodeToBlock.get(pred));
            }
            closure.apply(block);
        } else if (usage instanceof LoopCounter) {
            LoopCounter counter = (LoopCounter) usage;
            if (node == counter.init() || node == counter.stride()) {
                LoopBegin loopBegin = counter.loopBegin();
                Block mergeBlock = nodeToBlock.get(loopBegin);
                closure.apply(mergeBlock.dominator());
            }
        } else {
            assignBlockToNode(usage);
            closure.apply(nodeToBlock.get(usage));
        }
    }

    private void patch(Usage usage, Node original, Node patch) {
        if (usage.node instanceof Phi) {
            Phi phi = (Phi) usage.node;
            Node pred;
            Block phiBlock = nodeToBlock.get(phi);
            if (phiBlock.isLoopHeader()) {
                LoopBegin loopBegin = (LoopBegin) phiBlock.firstNode();
                if (usage.block == phiBlock.dominator()) {
                    pred = loopBegin.forwardEdge();
                } else {
                    assert usage.block == nodeToBlock.get(loopBegin.loopEnd());
                    pred = loopBegin.loopEnd();
                }
            } else {
                pred = usage.block.end();
            }
            int index = phi.merge().phiPredecessorIndex(pred);
            phi.setValueAt(index, (Value) patch);
        } else {
            usage.node.replaceFirstInput(original, patch);
        }
    }

    private void ensureScheduledUsages(Node node) {
        //  assign block to all usages to ensure that any splitting/rematerialization is done on them
        for (Node usage : node.usages().snapshot()) {
            assignBlockToNode(usage);
        }
        // now true usages are ready
    }

    private Map<Block, Set<Usage>> computeUsages(Node node, final Block from) { //TODO use a List instead of a Set here ?
        final Map<Block, Set<Usage>> blockUsages = new HashMap<Block, Set<Usage>>();
        ensureScheduledUsages(node);
        for (final Node usage : node.dataUsages()) {
            blocksForUsage(node, usage, new BlockClosure() {
                @Override
                public void apply(Block block) {
                    addUsageToTree(usage, block, from, blockUsages);
                }
            });
        }
        /*TTY.println("Usages for " + node + " from " + from);
        for (Entry<Block, Set<Usage>> entry : blockUsages.entrySet()) {
            TTY.println(" - " + entry.getKey() + " :");
            for (Usage usage : entry.getValue()) {
                TTY.println(" + " + usage.node + " in  " + usage.block);
            }
        }*/
        return blockUsages;
    }

    private void addUsageToTree(Node usage, Block usageBlock, Block from, Map<Block, Set<Usage>> blockUsages) {
        Block current = usageBlock;
        while (current != null) {
            Set<Usage> usages = blockUsages.get(current);
            if (usages == null) {
                usages = new HashSet<Usage>();
                blockUsages.put(current, usages);
            }
            usages.add(new Usage(usageBlock, usage));
            if (current == from) {
                current = null;
            } else {
                current = current.dominator();
            }
        }
    }

    private static class Usage {
        public final Block block;
        public final Node node;
        public Usage(Block block, Node node) {
            this.block = block;
            this.node = node;
        }
    }

    private boolean noRematerialization(Node n) {
        return n instanceof Local || n instanceof LocationNode || n instanceof Constant || n instanceof StateSplit || n instanceof FrameState || n instanceof VirtualObject ||
                        n instanceof VirtualObjectField;
    }

    private double liveRange(Block from, Set<Usage> usages) {
        BitMap subTree = new BitMap(blocks.size());
        markSubTree(from, subTree);
        double range = 0.0;
        for (Usage usage : usages) {
            range += markRangeUp(usage.block, subTree);
        }
        return range;
    }

    private void markSubTree(Block from, BitMap subTree) {
        subTree.set(from.blockID());
        for (Block b : from.getDominated()) {
            markSubTree(b, subTree);
        }
    }

    private double markRangeUp(Block from, BitMap subTree) {
        int blockID = from.blockID();
        if (!subTree.get(blockID)) {
            return 0.0;
        }
        subTree.clear(blockID);
        double range = from.probability() * (from.getInstructions().size() + 2);
        for (Block pred : from.getPredecessors()) {
            range += markRangeUp(pred, subTree);
        }
        return range;
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
                assert !(b.lastNode() instanceof ControlSplit);
                //b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.lastNode());
                sortedInstructions.add(b.lastNode());
            }
        }
        b.setInstructions(sortedInstructions);
//        TTY.println();
//        TTY.println("B" + b.blockID());
//        for (Node n : sortedInstructions) {
//            TTY.println("n=" + n);
//        }
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || nodeToBlock.get(i) != b || i instanceof Phi || i instanceof Local || i instanceof LoopCounter) {
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
        assert dominatorRoot.getPredecessors().size() == 0;
        BitMap visited = new BitMap(blocks.size());
        visited.set(dominatorRoot.blockID());
        LinkedList<Block> workList = new LinkedList<Block>();
        for (Block block : blocks) {
            if (block.getPredecessors().size() == 0) {
                workList.add(block);
            }
        }

        int cnt = 0;
        while (!workList.isEmpty()) {
            if (cnt++ > blocks.size() * 20) {
                throw new RuntimeException("(ls) endless loop in computeDominators?");
            }
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
