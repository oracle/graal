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
package com.oracle.graal.phases.schedule;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.phases.*;

public class SchedulePhase extends Phase {
    private ControlFlowGraph cfg;
    private NodeMap<Block> earliestCache;

    /**
     * Map from blocks to the nodes in each block.
     */
    private BlockMap<List<ScheduledNode>> blockToNodesMap;

    public SchedulePhase() {
        super("Schedule");
    }

    @Override
    protected void run(StructuredGraph graph) {
        cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        earliestCache = graph.createNodeMap();
        blockToNodesMap = new BlockMap<>(cfg);

        assignBlockToNodes(graph);
        sortNodesWithinBlocks(graph);
    }

    /**
     * Sets {@link ScheduledNode#scheduledNext} on all scheduled nodes in all blocks using the scheduling built by @link {@link #run(StructuredGraph)}.
     * This method should thus only be called when run has been successfully executed.
     */
    public void scheduleGraph() {
        assert blockToNodesMap != null : "cannot set scheduledNext before run has been executed";
        for (Block block : cfg.getBlocks()) {
            List<ScheduledNode> nodeList = blockToNodesMap.get(block);
            ScheduledNode last = null;
            for (ScheduledNode node : nodeList) {
                if (last != null) {
                    last.setScheduledNext(node);
                }
                last = node;
            }
        }
    }

    public ControlFlowGraph getCFG() {
        return cfg;
    }

    /**
     * Gets the map from each block to the nodes in the block.
     */
    public BlockMap<List<ScheduledNode>> getBlockToNodesMap() {
        return blockToNodesMap;
    }

    /**
     * Gets the nodes in a given block.
     */
    public List<ScheduledNode> nodesFor(Block block) {
        return blockToNodesMap.get(block);
    }

    private void assignBlockToNodes(StructuredGraph graph) {
        for (Block block : cfg.getBlocks()) {
            List<ScheduledNode> nodes = new ArrayList<>();
            assert blockToNodesMap.get(block) == null;
            blockToNodesMap.put(block, nodes);
            for (FixedNode node : block.getNodes()) {
                nodes.add(node);
            }
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof ScheduledNode) {
                assignBlockToNode((ScheduledNode) n);
            }
        }
    }

    /**
     * Assigns a block to the given node. This method expects that PhiNodes and FixedNodes are already assigned to a block.
     */
    private void assignBlockToNode(ScheduledNode node) {
        assert !node.isDeleted();

        Block prevBlock = cfg.getNodeToBlock().get(node);
        if (prevBlock != null) {
            return;
        }
        // PhiNodes and FixedNodes should already have been placed in blocks by ControlFlowGraph.identifyBlocks
        assert !(node instanceof PhiNode) : node;
        assert !(node instanceof FixedNode) : node;
        // if in CFG, schedule at the latest position possible in the outermost loop possible
        Block latestBlock = latestBlock(node);
        Block block;
        if (latestBlock == null) {
            block = earliestBlock(node);
        } else if (GraalOptions.ScheduleOutOfLoops && !(node instanceof VirtualObjectNode)) {
            Block earliestBlock = earliestBlock(node);
            block = scheduleOutOfLoops(node, latestBlock, earliestBlock);
            assert earliestBlock.dominates(block) : "Graph can not be scheduled : inconsistent for " + node + " (" + earliestBlock + " needs to dominate " + block + ")";
        } else {
            block = latestBlock;
        }
        cfg.getNodeToBlock().set(node, block);
        blockToNodesMap.get(block).add(node);
    }

    /**
     * Calculates the last block that the given node could be scheduled in, i.e., the common dominator of all usages.
     * To do so all usages are also assigned to blocks.
     */
    private Block latestBlock(ScheduledNode node) {
        CommonDominatorBlockClosure cdbc = new CommonDominatorBlockClosure(null);
        for (Node succ : node.successors().nonNull()) {
            assert cfg.getNodeToBlock().get(succ) != null;
            cdbc.apply(cfg.getNodeToBlock().get(succ));
        }
        ensureScheduledUsages(node);
        for (Node usage : node.usages()) {
            blocksForUsage(node, usage, cdbc);
        }
        return cdbc.block;
    }

    /**
     * A closure that will calculate the common dominator of all blocks passed to its {@link #apply(Block)} method.
     */
    private static class CommonDominatorBlockClosure implements BlockClosure {
        public Block block;
        public CommonDominatorBlockClosure(Block block) {
            this.block = block;
        }
        @Override
        public void apply(Block newBlock) {
            this.block = getCommonDominator(this.block, newBlock);
        }
    }

    /**
     * Determines the earliest block in which the given node can be scheduled.
     */
    private Block earliestBlock(Node node) {
        Block earliest = cfg.getNodeToBlock().get(node);
        if (earliest != null) {
            return earliest;
        }
        earliest = earliestCache.get(node);
        if (earliest != null) {
            return earliest;
        }
        /*
         * All inputs must be in a dominating block, otherwise the graph cannot be scheduled. This implies that the
         * inputs' blocks have a total ordering via their dominance relation. So in order to find the earliest block
         * placement for this node we need to find the input block that is dominated by all other input blocks.
         *
         * While iterating over the inputs a set of dominator blocks of the current earliest placement is maintained.
         * When the block of an input is not within this set, it becomes the current earliest placement and the list of
         * dominator blocks is updated.
         */
        BitSet dominators = new BitSet(cfg.getBlocks().length);

        assert node.predecessor() == null;
        for (Node input : node.inputs().nonNull()) {
            assert input instanceof ValueNode;
            Block inputEarliest = earliestBlock(input);
            if (!dominators.get(inputEarliest.getId())) {
                earliest = inputEarliest;
                do {
                    dominators.set(inputEarliest.getId());
                    inputEarliest = inputEarliest.getDominator();
                } while(inputEarliest != null && !dominators.get(inputEarliest.getId()));
            }
        }
        if (earliest == null) {
            earliest = cfg.getStartBlock();
        }
        earliestCache.set(node, earliest);
        return earliest;
    }


    private static Block scheduleOutOfLoops(Node n, Block latestBlock, Block earliest) {
        assert latestBlock != null : "no latest : " + n;
        Block cur = latestBlock;
        Block result = latestBlock;
        while (cur.getLoop() != null && cur != earliest && cur.getDominator() != null) {
            Block dom = cur.getDominator();
            if (dom.getLoopDepth() < result.getLoopDepth()) {
                result = dom;
            }
            cur = dom;
        }
        return result;
    }

    /**
     * Passes all blocks that a specific usage of a node is in to a given closure.
     * This is more complex than just taking the usage's block because of of PhiNodes and FrameStates.
     *
     * @param node the node that needs to be scheduled
     * @param usage the usage whose blocks need to be considered
     * @param closure the closure that will be called for each block
     */
    private void blocksForUsage(ScheduledNode node, Node usage, BlockClosure closure) {
        assert !(node instanceof PhiNode);

        if (usage instanceof PhiNode) {
            // An input to a PhiNode is used at the end of the predecessor block that corresponds to the PhiNode input.
            // One PhiNode can use an input multiple times, the closure will be called for each usage.
            PhiNode phi = (PhiNode) usage;
            MergeNode merge = phi.merge();
            Block mergeBlock = cfg.getNodeToBlock().get(merge);
            assert mergeBlock != null : "no block for merge " + merge.toString(Verbosity.Id);
            for (int i = 0; i < phi.valueCount(); ++i) {
                if (phi.valueAt(i) == node) {
                    if (mergeBlock.getPredecessors().size() <= i) {
                        TTY.println(merge.toString());
                        TTY.println(phi.toString());
                        TTY.println(merge.cfgPredecessors().toString());
                        TTY.println(mergeBlock.getPredecessors().toString());
                        TTY.println(phi.inputs().toString());
                        TTY.println("value count: " + phi.valueCount());
                    }
                    closure.apply(mergeBlock.getPredecessors().get(i));
                }
            }
        } else if (usage instanceof VirtualState) {
            // The following logic does not work if node is a PhiNode, but this method is never called for PhiNodes.
            for (Node unscheduledUsage : usage.usages()) {
                if (unscheduledUsage instanceof VirtualState) {
                    // If a FrameState is an outer FrameState this method behaves as if the inner FrameState was the actual usage, by recursing.
                    blocksForUsage(node, unscheduledUsage, closure);
                } else if (unscheduledUsage instanceof MergeNode) {
                    // Only FrameStates can be connected to MergeNodes.
                    assert usage instanceof FrameState;
                    // If a FrameState belongs to a MergeNode then it's inputs will be placed at the common dominator of all EndNodes.
                    for (Node pred : unscheduledUsage.cfgPredecessors()) {
                        closure.apply(cfg.getNodeToBlock().get(pred));
                    }
                } else {
                    // For the time being, only FrameStates can be connected to StateSplits.
                    assert usage instanceof FrameState;
                    assert unscheduledUsage instanceof StateSplit;
                    // Otherwise: Put the input into the same block as the usage.
                    assignBlockToNode((ScheduledNode) unscheduledUsage);
                    closure.apply(cfg.getNodeToBlock().get(unscheduledUsage));
                }
            }
        } else {
            // All other types of usages: Put the input into the same block as the usage.
            assignBlockToNode((ScheduledNode) usage);
            closure.apply(cfg.getNodeToBlock().get(usage));
        }
    }

    private void ensureScheduledUsages(Node node) {
        for (Node usage : node.usages().filter(ScheduledNode.class)) {
            assignBlockToNode((ScheduledNode) usage);
        }
        // now true usages are ready
    }

    private static Block getCommonDominator(Block a, Block b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return ControlFlowGraph.commonDominator(a, b);
    }

    private void sortNodesWithinBlocks(StructuredGraph graph) {
        NodeBitMap visited = graph.createNodeBitMap();
        for (Block b : cfg.getBlocks()) {
            sortNodesWithinBlock(b, visited);
        }
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over all inputs.
     * This means that a node is added to the list after all its inputs have been processed.
     */
    private void sortNodesWithinBlock(Block b, NodeBitMap visited) {
        List<ScheduledNode> instructions = blockToNodesMap.get(b);
        List<ScheduledNode> sortedInstructions = new ArrayList<>(instructions.size() + 2);

        assert !visited.isMarked(b.getBeginNode()) && cfg.blockFor(b.getBeginNode()) == b;
        assert !visited.isMarked(b.getEndNode()) && cfg.blockFor(b.getEndNode()) == b;

        for (ScheduledNode i : instructions) {
            addToSorting(b, i, sortedInstructions, visited);
        }

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off it).
        Node lastSorted = sortedInstructions.get(sortedInstructions.size() - 1);
        if (lastSorted != b.getEndNode()) {
            int idx = sortedInstructions.indexOf(b.getEndNode());
            boolean canNotMove = false;
            for (int i = idx + 1; i < sortedInstructions.size(); i++) {
                if (sortedInstructions.get(i).inputs().contains(b.getEndNode())) {
                    canNotMove = true;
                    break;
                }
            }
            if (canNotMove) {
                if (b.getEndNode() instanceof ControlSplitNode) {
                    throw new GraalInternalError("Schedule is not possible : needs to move a node after the last node of the block which can not be move").
                    addContext(lastSorted).
                    addContext(b.getEndNode());
                }

                //b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.getEndNode());
                sortedInstructions.add(b.getEndNode());
            }
        }
        blockToNodesMap.put(b, sortedInstructions);
    }

    private void addUnscheduledToSorting(Block b, VirtualState state, List<ScheduledNode> sortedInstructions, NodeBitMap visited) {
        if (state != null) {
            // UnscheduledNodes should never be marked as visited.
            assert !visited.isMarked(state);

            for (Node input : state.inputs()) {
                if (input instanceof VirtualState) {
                    addUnscheduledToSorting(b, (VirtualState) input, sortedInstructions, visited);
                } else {
                    addToSorting(b, (ScheduledNode) input, sortedInstructions, visited);
                }
            }
        }
    }

    private void addToSorting(Block b, ScheduledNode i, List<ScheduledNode> sortedInstructions, NodeBitMap visited) {
        if (i == null || visited.isMarked(i) || cfg.getNodeToBlock().get(i) != b || i instanceof PhiNode || i instanceof LocalNode) {
            return;
        }

        FrameState state = null;
        WriteNode write = null;
        for (Node input : i.inputs()) {
            if (input instanceof WriteNode && !visited.isMarked(input) && cfg.getNodeToBlock().get(input) == b) {
                assert write == null;
                write = (WriteNode) input;
            } else if (input instanceof FrameState) {
                assert state == null;
                state = (FrameState) input;
            } else {
                addToSorting(b, (ScheduledNode) input, sortedInstructions, visited);
            }
        }

        addToSorting(b, (ScheduledNode) i.predecessor(), sortedInstructions, visited);
        visited.mark(i);
        addUnscheduledToSorting(b, state, sortedInstructions, visited);
        assert write == null || !visited.isMarked(write);
        addToSorting(b, write, sortedInstructions, visited);

        // Now predecessors and inputs are scheduled => we can add this node.
        sortedInstructions.add(i);
    }
}
