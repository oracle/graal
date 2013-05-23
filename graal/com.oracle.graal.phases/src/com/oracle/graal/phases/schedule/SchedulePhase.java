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

import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.LoopInfo;

public final class SchedulePhase extends Phase {

    /**
     * Error thrown when a graph cannot be scheduled.
     */
    public static class SchedulingError extends Error {

        private static final long serialVersionUID = 1621001069476473145L;

        public SchedulingError() {
            super();
        }

        /**
         * This constructor creates a {@link SchedulingError} with a message assembled via
         * {@link String#format(String, Object...)}.
         * 
         * @param format a {@linkplain Formatter format} string
         * @param args parameters to {@link String#format(String, Object...)}
         */
        public SchedulingError(String format, Object... args) {
            super(String.format(format, args));
        }

    }

    public static enum SchedulingStrategy {
        EARLIEST, LATEST, LATEST_OUT_OF_LOOPS
    }

    /**
     * This closure iterates over all nodes of a scheduled graph (it expects a
     * {@link SchedulingStrategy#EARLIEST} schedule) and keeps a list of "active" reads. Whenever it
     * encounters a read, it adds it to the active reads. Whenever it encounters a memory
     * checkpoint, it adds all reads that need to be committed before this checkpoint to the
     * "phantom" usages and inputs, so that the read is scheduled before the checkpoint afterwards.
     * 
     * At merges, the intersection of all sets of active reads is calculated. A read that was
     * committed within one predecessor branch cannot be scheduled after the merge anyway.
     * 
     * Similarly for loops, all reads that are killed somewhere within the loop are removed from the
     * exits' active reads, since they cannot be scheduled after the exit anyway.
     */
    private class MemoryScheduleClosure extends BlockIteratorClosure<HashSet<FloatingReadNode>> {

        @Override
        protected HashSet<FloatingReadNode> getInitialState() {
            return new HashSet<>();
        }

        @Override
        protected HashSet<FloatingReadNode> processBlock(Block block, HashSet<FloatingReadNode> currentState) {
            for (Node node : getBlockToNodesMap().get(block)) {
                if (node instanceof FloatingReadNode) {
                    currentState.add((FloatingReadNode) node);
                } else if (node instanceof MemoryCheckpoint) {
                    for (LocationIdentity identity : ((MemoryCheckpoint) node).getLocationIdentities()) {
                        for (Iterator<FloatingReadNode> iter = currentState.iterator(); iter.hasNext();) {
                            FloatingReadNode read = iter.next();
                            FixedNode fixed = (FixedNode) node;
                            if (identity == ANY_LOCATION || read.location().getLocationIdentity() == identity) {
                                addPhantomReference(read, fixed);
                                iter.remove();
                            }
                        }
                    }
                }
            }
            return currentState;
        }

        public void addPhantomReference(FloatingReadNode read, FixedNode fixed) {
            List<FixedNode> usageList = phantomUsages.get(read);
            if (usageList == null) {
                phantomUsages.put(read, usageList = new ArrayList<>());
            }
            usageList.add(fixed);
            List<FloatingNode> inputList = phantomInputs.get(fixed);
            if (inputList == null) {
                phantomInputs.put(fixed, inputList = new ArrayList<>());
            }
            inputList.add(read);
        }

        @Override
        protected HashSet<FloatingReadNode> merge(Block merge, List<HashSet<FloatingReadNode>> states) {
            HashSet<FloatingReadNode> state = new HashSet<>(states.get(0));
            for (int i = 1; i < states.size(); i++) {
                state.retainAll(states.get(i));
            }
            return state;
        }

        @Override
        protected HashSet<FloatingReadNode> cloneState(HashSet<FloatingReadNode> oldState) {
            return new HashSet<>(oldState);
        }

        @Override
        protected List<HashSet<FloatingReadNode>> processLoop(Loop loop, HashSet<FloatingReadNode> state) {
            LoopInfo<HashSet<FloatingReadNode>> info = ReentrantBlockIterator.processLoop(this, loop, new HashSet<>(state));

            List<HashSet<FloatingReadNode>> loopEndStates = info.endStates;

            // collect all reads that were killed in some branch within the loop
            Set<FloatingReadNode> killedReads = new HashSet<>(state);
            Set<FloatingReadNode> survivingReads = new HashSet<>(loopEndStates.get(0));
            for (int i = 1; i < loopEndStates.size(); i++) {
                survivingReads.retainAll(loopEndStates.get(i));
            }
            killedReads.removeAll(survivingReads);

            // reads that were killed within the loop cannot be scheduled after the loop anyway
            for (HashSet<FloatingReadNode> exitState : info.exitStates) {
                exitState.removeAll(killedReads);
            }
            return info.exitStates;
        }
    }

    private ControlFlowGraph cfg;
    private NodeMap<Block> earliestCache;

    /**
     * Map from blocks to the nodes in each block.
     */
    private BlockMap<List<ScheduledNode>> blockToNodesMap;
    private final Map<FloatingNode, List<FixedNode>> phantomUsages = new IdentityHashMap<>();
    private final Map<FixedNode, List<FloatingNode>> phantomInputs = new IdentityHashMap<>();
    private final SchedulingStrategy selectedStrategy;

    public SchedulePhase() {
        this(GraalOptions.OptScheduleOutOfLoops ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        this.selectedStrategy = strategy;
    }

    @Override
    protected void run(StructuredGraph graph) {
        cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        earliestCache = graph.createNodeMap();
        blockToNodesMap = new BlockMap<>(cfg);

        if (GraalOptions.MemoryAwareScheduling && selectedStrategy != SchedulingStrategy.EARLIEST && graph.getNodes(FloatingReadNode.class).isNotEmpty()) {

            assignBlockToNodes(graph, SchedulingStrategy.EARLIEST);
            sortNodesWithinBlocks(graph, SchedulingStrategy.EARLIEST);

            MemoryScheduleClosure closure = new MemoryScheduleClosure();
            ReentrantBlockIterator.apply(closure, getCFG().getStartBlock());

            cfg.clearNodeToBlock();
            blockToNodesMap = new BlockMap<>(cfg);
        }

        assignBlockToNodes(graph, selectedStrategy);
        sortNodesWithinBlocks(graph, selectedStrategy);
    }

    /**
     * Sets {@link ScheduledNode#scheduledNext} on all scheduled nodes in all blocks using the
     * scheduling built by {@link #run(StructuredGraph)}. This method should thus only be called
     * when run has been successfully executed.
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

    private void assignBlockToNodes(StructuredGraph graph, SchedulingStrategy strategy) {
        for (Block block : cfg.getBlocks()) {
            List<ScheduledNode> nodes = new ArrayList<>();
            if (blockToNodesMap.get(block) != null) {
                throw new SchedulingError();
            }
            blockToNodesMap.put(block, nodes);
            for (FixedNode node : block.getNodes()) {
                nodes.add(node);
            }
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof ScheduledNode) {
                assignBlockToNode((ScheduledNode) n, strategy);
            }
        }
    }

    /**
     * Assigns a block to the given node. This method expects that PhiNodes and FixedNodes are
     * already assigned to a block.
     */
    private void assignBlockToNode(ScheduledNode node, SchedulingStrategy strategy) {
        assert !node.isDeleted();

        Block prevBlock = cfg.getNodeToBlock().get(node);
        if (prevBlock != null) {
            return;
        }
        // PhiNodes and FixedNodes should already have been placed in blocks by
        // ControlFlowGraph.identifyBlocks
        if (node instanceof PhiNode || node instanceof FixedNode) {
            throw new SchedulingError("%s should already have been placed in a block", node);
        }

        Block block;
        switch (strategy) {
            case EARLIEST:
                block = earliestBlock(node);
                break;
            case LATEST:
            case LATEST_OUT_OF_LOOPS:
                block = latestBlock(node, strategy);
                if (block == null) {
                    block = earliestBlock(node);
                } else if (strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS && !(node instanceof VirtualObjectNode)) {
                    // schedule at the latest position possible in the outermost loop possible
                    Block earliestBlock = earliestBlock(node);
                    block = scheduleOutOfLoops(node, block, earliestBlock);
                    if (!earliestBlock.dominates(block)) {
                        throw new SchedulingError("%s: Graph cannot be scheduled : inconsistent for %s, %d usages, (%s needs to dominate %s)", node.graph(), node, node.usages().count(),
                                        earliestBlock, block);
                    }
                }
                break;
            default:
                throw new GraalInternalError("unknown scheduling strategy");
        }
        cfg.getNodeToBlock().set(node, block);
        blockToNodesMap.get(block).add(node);
    }

    /**
     * Calculates the last block that the given node could be scheduled in, i.e., the common
     * dominator of all usages. To do so all usages are also assigned to blocks.
     * 
     * @param strategy
     */
    private Block latestBlock(ScheduledNode node, SchedulingStrategy strategy) {
        CommonDominatorBlockClosure cdbc = new CommonDominatorBlockClosure(null);
        for (Node succ : node.successors().nonNull()) {
            if (cfg.getNodeToBlock().get(succ) == null) {
                throw new SchedulingError();
            }
            cdbc.apply(cfg.getNodeToBlock().get(succ));
        }
        ensureScheduledUsages(node, strategy);
        for (Node usage : node.usages()) {
            blocksForUsage(node, usage, cdbc, strategy);
        }
        List<FixedNode> usages = phantomUsages.get(node);
        if (usages != null) {
            for (FixedNode usage : usages) {
                if (cfg.getNodeToBlock().get(usage) == null) {
                    throw new SchedulingError();
                }
                cdbc.apply(cfg.getNodeToBlock().get(usage));
            }
        }

        return cdbc.block;
    }

    /**
     * A closure that will calculate the common dominator of all blocks passed to its
     * {@link #apply(Block)} method.
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
         * All inputs must be in a dominating block, otherwise the graph cannot be scheduled. This
         * implies that the inputs' blocks have a total ordering via their dominance relation. So in
         * order to find the earliest block placement for this node we need to find the input block
         * that is dominated by all other input blocks.
         * 
         * While iterating over the inputs a set of dominator blocks of the current earliest
         * placement is maintained. When the block of an input is not within this set, it becomes
         * the current earliest placement and the list of dominator blocks is updated.
         */
        BitSet dominators = new BitSet(cfg.getBlocks().length);

        if (node.predecessor() != null) {
            throw new SchedulingError();
        }
        for (Node input : node.inputs().nonNull()) {
            assert input instanceof ValueNode;
            Block inputEarliest;
            if (input instanceof InvokeWithExceptionNode) {
                inputEarliest = cfg.getNodeToBlock().get(((InvokeWithExceptionNode) input).next());
            } else {
                inputEarliest = earliestBlock(input);
            }
            if (!dominators.get(inputEarliest.getId())) {
                earliest = inputEarliest;
                do {
                    dominators.set(inputEarliest.getId());
                    inputEarliest = inputEarliest.getDominator();
                } while (inputEarliest != null && !dominators.get(inputEarliest.getId()));
            }
        }
        if (earliest == null) {
            earliest = cfg.getStartBlock();
        }
        earliestCache.set(node, earliest);
        return earliest;
    }

    private static Block scheduleOutOfLoops(Node n, Block latestBlock, Block earliest) {
        if (latestBlock == null) {
            throw new SchedulingError("no latest : %s", n);
        }
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
     * Passes all blocks that a specific usage of a node is in to a given closure. This is more
     * complex than just taking the usage's block because of of PhiNodes and FrameStates.
     * 
     * @param node the node that needs to be scheduled
     * @param usage the usage whose blocks need to be considered
     * @param closure the closure that will be called for each block
     */
    private void blocksForUsage(ScheduledNode node, Node usage, BlockClosure closure, SchedulingStrategy strategy) {
        if (node instanceof PhiNode) {
            throw new SchedulingError(node.toString());
        }

        if (usage instanceof PhiNode) {
            // An input to a PhiNode is used at the end of the predecessor block that corresponds to
            // the PhiNode input.
            // One PhiNode can use an input multiple times, the closure will be called for each
            // usage.
            PhiNode phi = (PhiNode) usage;
            MergeNode merge = phi.merge();
            Block mergeBlock = cfg.getNodeToBlock().get(merge);
            if (mergeBlock == null) {
                throw new SchedulingError("no block for merge %s", merge.toString(Verbosity.Id));
            }
            for (int i = 0; i < phi.valueCount(); ++i) {
                if (phi.valueAt(i) == node) {
                    if (mergeBlock.getPredecessorCount() <= i) {
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
            // The following logic does not work if node is a PhiNode, but this method is never
            // called for PhiNodes.
            for (Node unscheduledUsage : usage.usages()) {
                if (unscheduledUsage instanceof VirtualState) {
                    // If a FrameState is an outer FrameState this method behaves as if the inner
                    // FrameState was the actual usage, by recursing.
                    blocksForUsage(node, unscheduledUsage, closure, strategy);
                } else if (unscheduledUsage instanceof MergeNode) {
                    // Only FrameStates can be connected to MergeNodes.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    // If a FrameState belongs to a MergeNode then it's inputs will be placed at the
                    // common dominator of all EndNodes.
                    for (Node pred : unscheduledUsage.cfgPredecessors()) {
                        closure.apply(cfg.getNodeToBlock().get(pred));
                    }
                } else {
                    // For the time being, only FrameStates can be connected to StateSplits.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    if (!(unscheduledUsage instanceof StateSplit || unscheduledUsage instanceof DeoptimizingNode)) {
                        throw new SchedulingError(unscheduledUsage.toString());
                    }
                    // Otherwise: Put the input into the same block as the usage.
                    assignBlockToNode((ScheduledNode) unscheduledUsage, strategy);
                    closure.apply(cfg.getNodeToBlock().get(unscheduledUsage));
                }
            }
        } else {
            // All other types of usages: Put the input into the same block as the usage.
            assignBlockToNode((ScheduledNode) usage, strategy);
            closure.apply(cfg.getNodeToBlock().get(usage));
        }
    }

    private void ensureScheduledUsages(Node node, SchedulingStrategy strategy) {
        for (Node usage : node.usages().filter(ScheduledNode.class)) {
            assignBlockToNode((ScheduledNode) usage, strategy);
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

    private void sortNodesWithinBlocks(StructuredGraph graph, SchedulingStrategy strategy) {
        NodeBitMap visited = graph.createNodeBitMap();
        for (Block b : cfg.getBlocks()) {
            sortNodesWithinBlock(b, visited, strategy);
        }
    }

    private void sortNodesWithinBlock(Block b, NodeBitMap visited, SchedulingStrategy strategy) {
        if (visited.isMarked(b.getBeginNode()) || cfg.blockFor(b.getBeginNode()) != b) {
            throw new SchedulingError();
        }
        if (visited.isMarked(b.getEndNode()) || cfg.blockFor(b.getEndNode()) != b) {
            throw new SchedulingError();
        }

        List<ScheduledNode> sortedInstructions;
        switch (strategy) {
            case EARLIEST:
                sortedInstructions = sortNodesWithinBlockEarliest(b, visited);
                break;
            case LATEST:
            case LATEST_OUT_OF_LOOPS:
                sortedInstructions = sortNodesWithinBlockLatest(b, visited);
                break;
            default:
                throw new GraalInternalError("unknown scheduling strategy");
        }
        blockToNodesMap.put(b, sortedInstructions);
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all inputs. This means that a node is added to the list after all its inputs have been
     * processed.
     */
    private List<ScheduledNode> sortNodesWithinBlockLatest(Block b, NodeBitMap visited) {
        List<ScheduledNode> instructions = blockToNodesMap.get(b);
        List<ScheduledNode> sortedInstructions = new ArrayList<>(blockToNodesMap.get(b).size() + 2);

        for (ScheduledNode i : instructions) {
            addToLatestSorting(b, i, sortedInstructions, visited);
        }

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off
        // it).
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
                    throw new GraalInternalError("Schedule is not possible : needs to move a node after the last node of the block which can not be move").addContext(lastSorted).addContext(
                                    b.getEndNode());
                }

                // b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.getEndNode());
                sortedInstructions.add(b.getEndNode());
            }
        }
        return sortedInstructions;
    }

    private void addUnscheduledToLatestSorting(Block b, VirtualState state, List<ScheduledNode> sortedInstructions, NodeBitMap visited) {
        if (state != null) {
            // UnscheduledNodes should never be marked as visited.
            if (visited.isMarked(state)) {
                throw new SchedulingError();
            }

            for (Node input : state.inputs()) {
                if (input instanceof VirtualState) {
                    addUnscheduledToLatestSorting(b, (VirtualState) input, sortedInstructions, visited);
                } else {
                    addToLatestSorting(b, (ScheduledNode) input, sortedInstructions, visited);
                }
            }
        }
    }

    private void addToLatestSorting(Block b, ScheduledNode i, List<ScheduledNode> sortedInstructions, NodeBitMap visited) {
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
                addToLatestSorting(b, (ScheduledNode) input, sortedInstructions, visited);
            }
        }
        List<FloatingNode> inputs = phantomInputs.get(i);
        if (inputs != null) {
            for (FloatingNode input : inputs) {
                addToLatestSorting(b, input, sortedInstructions, visited);
            }
        }

        addToLatestSorting(b, (ScheduledNode) i.predecessor(), sortedInstructions, visited);
        visited.mark(i);
        addUnscheduledToLatestSorting(b, state, sortedInstructions, visited);
        assert write == null || !visited.isMarked(write);
        addToLatestSorting(b, write, sortedInstructions, visited);

        // Now predecessors and inputs are scheduled => we can add this node.
        sortedInstructions.add(i);
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all usages. The resulting list is reversed to create an earliest-possible scheduling of
     * nodes.
     */
    private List<ScheduledNode> sortNodesWithinBlockEarliest(Block b, NodeBitMap visited) {
        List<ScheduledNode> sortedInstructions = new ArrayList<>(blockToNodesMap.get(b).size() + 2);
        addToEarliestSorting(b, b.getEndNode(), sortedInstructions, visited);
        Collections.reverse(sortedInstructions);
        return sortedInstructions;
    }

    private void addToEarliestSorting(Block b, ScheduledNode i, List<ScheduledNode> sortedInstructions, NodeBitMap visited) {
        ScheduledNode instruction = i;
        while (true) {
            if (instruction == null || visited.isMarked(instruction) || cfg.getNodeToBlock().get(instruction) != b || instruction instanceof PhiNode || instruction instanceof LocalNode) {
                return;
            }

            visited.mark(instruction);
            for (Node usage : instruction.usages()) {
                if (usage instanceof VirtualState) {
                    // only fixed nodes can have VirtualState -> no need to schedule them
                } else {
                    if (instruction instanceof LoopExitNode && usage instanceof ProxyNode) {
                        // value proxies should be scheduled before the loopexit, not after
                    } else {
                        addToEarliestSorting(b, (ScheduledNode) usage, sortedInstructions, visited);
                    }
                }
            }

            if (instruction instanceof AbstractBeginNode) {
                ArrayList<ProxyNode> proxies = (instruction instanceof LoopExitNode) ? new ArrayList<ProxyNode>() : null;
                for (ScheduledNode inBlock : blockToNodesMap.get(b)) {
                    if (!visited.isMarked(inBlock)) {
                        if (inBlock instanceof ProxyNode) {
                            proxies.add((ProxyNode) inBlock);
                        } else {
                            addToEarliestSorting(b, inBlock, sortedInstructions, visited);
                        }
                    }
                }
                sortedInstructions.add(instruction);
                if (proxies != null) {
                    sortedInstructions.addAll(proxies);
                }
                break;
            } else {
                sortedInstructions.add(instruction);
                instruction = (ScheduledNode) instruction.predecessor();
            }
        }
    }
}
