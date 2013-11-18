/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.nodes.cfg.ControlFlowGraph.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
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

    public static enum MemoryScheduling {
        NONE, CONSERVATIVE, OPTIMAL
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
            for (Node node : blockToNodesMap.get(block)) {
                if (node instanceof FloatingReadNode) {
                    currentState.add((FloatingReadNode) node);
                } else if (node instanceof MemoryCheckpoint.Single) {
                    LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
                    processIdentity(currentState, (FixedNode) node, identity);
                } else if (node instanceof MemoryCheckpoint.Multi) {
                    for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                        processIdentity(currentState, (FixedNode) node, identity);
                    }
                }
                assert MemoryCheckpoint.TypeAssertion.correctType(node);
            }
            return currentState;
        }

        private void processIdentity(HashSet<FloatingReadNode> currentState, FixedNode fixed, LocationIdentity identity) {
            for (Iterator<FloatingReadNode> iter = currentState.iterator(); iter.hasNext();) {
                FloatingReadNode read = iter.next();
                if (identity == ANY_LOCATION || read.location().getLocationIdentity() == identity) {
                    addPhantomReference(read, fixed);
                    iter.remove();
                }
            }
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

    private class NewMemoryScheduleClosure extends BlockIteratorClosure<Map<LocationIdentity, Node>> {

        @Override
        protected Map<LocationIdentity, Node> getInitialState() {
            return cloneState(blockToKillMap.get(getCFG().getStartBlock()));
        }

        @Override
        protected Map<LocationIdentity, Node> processBlock(Block block, Map<LocationIdentity, Node> currentState) {

            if (block.getBeginNode() instanceof MergeNode) {
                MergeNode mergeNode = (MergeNode) block.getBeginNode();
                for (PhiNode phi : mergeNode.usages().filter(PhiNode.class)) {
                    if (phi.type() == PhiType.Memory) {
                        LocationIdentity identity = phi.getIdentity();
                        locationKilledBy(identity, phi, currentState);
                    }
                }
            }
            currentState.putAll(blockToKillMapInit.get(block));

            for (Node node : block.getNodes()) {
                if (node instanceof MemoryCheckpoint.Single) {
                    LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
                    locationKilledBy(identity, node, currentState);
                } else if (node instanceof MemoryCheckpoint.Multi) {
                    for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                        locationKilledBy(identity, node, currentState);
                    }
                }
                assert MemoryCheckpoint.TypeAssertion.correctType(node);
            }

            blockToKillMap.put(block, currentState);
            return cloneState(currentState);
        }

        private void locationKilledBy(LocationIdentity identity, Node checkpoint, Map<LocationIdentity, Node> state) {
            state.put(identity, checkpoint);
            if (identity == ANY_LOCATION) {
                for (LocationIdentity locid : state.keySet()) {
                    state.put(locid, checkpoint);
                }
            }
        }

        @Override
        protected Map<LocationIdentity, Node> merge(Block merge, List<Map<LocationIdentity, Node>> states) {
            assert merge.getBeginNode() instanceof MergeNode;
            MergeNode mergeNode = (MergeNode) merge.getBeginNode();

            Map<LocationIdentity, Node> initKillMap = new HashMap<>();
            for (Map<LocationIdentity, Node> state : states) {
                for (LocationIdentity locid : state.keySet()) {
                    if (initKillMap.containsKey(locid)) {
                        if (!initKillMap.get(locid).equals(state.get(locid))) {
                            initKillMap.put(locid, mergeNode);
                        }
                    } else {
                        initKillMap.put(locid, state.get(locid));
                    }
                }
            }

            mergeToKillMap.set(mergeNode, cloneState(initKillMap));
            return initKillMap;
        }

        @Override
        protected Map<LocationIdentity, Node> cloneState(Map<LocationIdentity, Node> state) {
            return new HashMap<>(state);
        }

        @Override
        protected List<Map<LocationIdentity, Node>> processLoop(Loop loop, Map<LocationIdentity, Node> state) {
            LoopInfo<Map<LocationIdentity, Node>> info = ReentrantBlockIterator.processLoop(this, loop, cloneState(state));

            assert loop.header.getBeginNode() instanceof LoopBeginNode;
            Map<LocationIdentity, Node> headerState = merge(loop.header, info.endStates);
            // second iteration, for computing information at loop exits
            info = ReentrantBlockIterator.processLoop(this, loop, cloneState(headerState));

            int i = 0;
            for (Block exit : loop.exits) {
                Map<LocationIdentity, Node> exitState = info.exitStates.get(i++);

                Node begin = exit.getBeginNode();
                assert begin instanceof LoopExitNode;
                for (Node usage : begin.usages()) {
                    if (usage instanceof ProxyNode && ((ProxyNode) usage).type() == PhiType.Memory) {
                        ProxyNode proxy = (ProxyNode) usage;
                        LocationIdentity identity = proxy.getIdentity();
                        locationKilledBy(identity, proxy, exitState);
                    }
                }
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
    private BlockMap<Map<LocationIdentity, Node>> blockToKillMapInit;
    private BlockMap<Map<LocationIdentity, Node>> blockToKillMap;
    private NodeMap<Map<LocationIdentity, Node>> mergeToKillMap;
    private final Map<FloatingNode, List<FixedNode>> phantomUsages = new IdentityHashMap<>();
    private final Map<FixedNode, List<FloatingNode>> phantomInputs = new IdentityHashMap<>();
    private final SchedulingStrategy selectedStrategy;
    private final MemoryScheduling memsched;

    public SchedulePhase() {
        this(OptScheduleOutOfLoops.getValue() ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        if (MemoryAwareScheduling.getValue() && NewMemoryAwareScheduling.getValue()) {
            throw new SchedulingError("cannot enable both: MemoryAware- and NewMemoryAwareScheduling");
        }
        if (MemoryAwareScheduling.getValue()) {
            this.memsched = MemoryScheduling.CONSERVATIVE;
        } else if (NewMemoryAwareScheduling.getValue()) {
            this.memsched = MemoryScheduling.OPTIMAL;
        } else {
            this.memsched = MemoryScheduling.NONE;
        }
        this.selectedStrategy = strategy;
    }

    public SchedulePhase(SchedulingStrategy strategy, MemoryScheduling memsched) {
        this.selectedStrategy = strategy;
        this.memsched = memsched;
    }

    @Override
    protected void run(StructuredGraph graph) {
        cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        earliestCache = graph.createNodeMap();
        blockToNodesMap = new BlockMap<>(cfg);

        if (memsched == MemoryScheduling.CONSERVATIVE && selectedStrategy != SchedulingStrategy.EARLIEST && graph.getNodes(FloatingReadNode.class).isNotEmpty()) {
            assignBlockToNodes(graph, SchedulingStrategy.EARLIEST);
            sortNodesWithinBlocks(graph, SchedulingStrategy.EARLIEST);

            MemoryScheduleClosure closure = new MemoryScheduleClosure();
            ReentrantBlockIterator.apply(closure, getCFG().getStartBlock());

            cfg.clearNodeToBlock();
            blockToNodesMap = new BlockMap<>(cfg);

            assignBlockToNodes(graph, selectedStrategy);
            sortNodesWithinBlocks(graph, selectedStrategy);
            printSchedule("after sorting nodes within blocks");
        } else if (memsched == MemoryScheduling.OPTIMAL && selectedStrategy != SchedulingStrategy.EARLIEST && graph.getNodes(FloatingReadNode.class).isNotEmpty()) {
            mergeToKillMap = graph.createNodeMap();

            blockToKillMapInit = new BlockMap<>(cfg);
            blockToKillMap = new BlockMap<>(cfg);
            for (Block b : cfg.getBlocks()) {
                blockToKillMapInit.put(b, new HashMap<LocationIdentity, Node>());
                blockToKillMap.put(b, new HashMap<LocationIdentity, Node>());
            }

            // initialize killMaps with lastLocationAccess
            for (FloatingReadNode n : graph.getNodes(FloatingReadNode.class)) {
                if (n.location().getLocationIdentity() == FINAL_LOCATION) {
                    continue;
                }
                Node first = n.getLastLocationAccess();
                assert first != null;

                Map<LocationIdentity, Node> killMap = blockToKillMapInit.get(forKillLocation(first));
                killMap.put(n.location().getLocationIdentity(), first);
            }

            // distribute and compute killMaps for all blocks
            NewMemoryScheduleClosure closure = new NewMemoryScheduleClosure();
            ReentrantBlockIterator.apply(closure, getCFG().getStartBlock());
            printSchedule("after computing killMaps");

            assignBlockToNodes(graph, selectedStrategy);
            printSchedule("after assign nodes to blocks");

            sortNodesWithinBlocks(graph, selectedStrategy);
            printSchedule("after sorting nodes within blocks");
        } else {
            assignBlockToNodes(graph, selectedStrategy);
            sortNodesWithinBlocks(graph, selectedStrategy);
        }
    }

    private Block forKillLocation(Node n) {
        Block b = cfg.getNodeToBlock().get(n);
        assert b != null : "all lastAccess locations should have a block assignment from CFG";
        return b;
    }

    private void printSchedule(String desc) {
        Debug.printf("=== %s / %s / %s (%s) ===\n", getCFG().getStartBlock().getBeginNode().graph(), selectedStrategy, memsched, desc);
        for (Block b : getCFG().getBlocks()) {
            Debug.printf("==== b: %s (loopDepth: %s). ", b, b.getLoopDepth());
            Debug.printf("dom: %s. ", b.getDominator());
            Debug.printf("post-dom: %s. ", b.getPostdominator());
            Debug.printf("preds: %s. ", b.getPredecessors());
            Debug.printf("succs: %s ====\n", b.getSuccessors());
            BlockMap<Map<LocationIdentity, Node>> killMaps = blockToKillMap;
            if (killMaps != null) {
                if (b.getBeginNode() instanceof MergeNode) {
                    MergeNode merge = (MergeNode) b.getBeginNode();
                    Debug.printf("M merge kills: \n");
                    for (LocationIdentity locId : mergeToKillMap.get(merge).keySet()) {
                        Debug.printf("M %s killed by %s\n", locId, mergeToKillMap.get(merge).get(locId));
                    }
                }
                Debug.printf("X block kills: \n");
                for (LocationIdentity locId : killMaps.get(b).keySet()) {
                    Debug.printf("X %s killed by %s\n", locId, killMaps.get(b).get(locId));
                }
            }

            if (blockToNodesMap.get(b) != null) {
                for (Node n : nodesFor(b)) {
                    printNode(n);
                }
            } else {
                for (Node n : b.getNodes()) {
                    printNode(n);
                }
            }
        }
        Debug.printf("\n\n");
    }

    private static void printNode(Node n) {
        Debug.printf("%s", n);
        if (n instanceof MemoryCheckpoint.Single) {
            Debug.printf(" // kills %s", ((MemoryCheckpoint.Single) n).getLocationIdentity());
        } else if (n instanceof MemoryCheckpoint.Multi) {
            Debug.printf(" // kills ");
            for (LocationIdentity locid : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                Debug.printf("%s, ", locid);
            }
        } else if (n instanceof FloatingReadNode) {
            FloatingReadNode frn = (FloatingReadNode) n;
            Debug.printf(" // from %s", frn.location().getLocationIdentity());
            Debug.printf(", lastAccess: %s", frn.getLastLocationAccess());
            Debug.printf(", object: %s", frn.object());
        } else if (n instanceof GuardNode) {
            Debug.printf(", guard: %s", ((GuardNode) n).getGuard());
        }
        Debug.printf("\n");
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
        // PhiNodes, ProxyNodes and FixedNodes should already have been placed in blocks by
        // ControlFlowGraph.identifyBlocks
        if (node instanceof PhiNode || node instanceof ProxyNode || node instanceof FixedNode) {
            throw new SchedulingError("%s should already have been placed in a block", node);
        }

        Block earliestBlock = earliestBlock(node);
        Block block;
        switch (strategy) {
            case EARLIEST:
                block = earliestBlock;
                break;
            case LATEST:
            case LATEST_OUT_OF_LOOPS:
                boolean scheduleRead = memsched == MemoryScheduling.OPTIMAL && node instanceof FloatingReadNode && ((FloatingReadNode) node).location().getLocationIdentity() != FINAL_LOCATION;
                if (scheduleRead) {
                    block = optimalBlock((FloatingReadNode) node, strategy);
                } else {
                    block = latestBlock(node, strategy);
                }
                if (block == null) {
                    block = earliestBlock;
                } else if (strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS && !(node instanceof VirtualObjectNode)) {
                    // schedule at the latest position possible in the outermost loop possible
                    block = scheduleOutOfLoops(node, block, earliestBlock);
                }

                assert !scheduleRead || forKillLocation(((FloatingReadNode) node).getLastLocationAccess()).dominates(block) : "out of loop violated memory semantics for " + node + ". moved to " +
                                block + ", but upper bound is " + forKillLocation(((FloatingReadNode) node).getLastLocationAccess());
                break;
            default:
                throw new GraalInternalError("unknown scheduling strategy");
        }
        if (!earliestBlock.dominates(block)) {
            throw new SchedulingError("%s: Graph cannot be scheduled : inconsistent for %s, %d usages, (%s needs to dominate %s)", node.graph(), node, node.usages().count(), earliestBlock, block);
        }
        cfg.getNodeToBlock().set(node, block);
        blockToNodesMap.get(block).add(node);
    }

    /**
     * this method tries to find the latest position for a read, by taking the information gathered
     * by {@link NewMemoryScheduleClosure} into account.
     * 
     * The idea is to iterate the dominator tree starting with the latest schedule of the read.
     * 
     * <pre>
     *    U      upperbound block, defined by last access location of the floating read
     *    &#9650;
     *    E      earliest block
     *    &#9650;
     *    O      optimal block, first block that contains a kill of the read's location
     *    &#9650;
     *    L      latest block
     * </pre>
     * 
     * i.e. <code>upperbound `dom` earliest `dom` optimal `dom` latest</code>. However, there're
     * cases where <code>earliest `dom` optimal</code> is not true, because the position is
     * (impliclitly) bounded by an anchor of the read's guard. In such cases, the earliest schedule
     * is taken.
     * 
     */
    private Block optimalBlock(FloatingReadNode n, SchedulingStrategy strategy) {
        assert memsched == MemoryScheduling.OPTIMAL;

        LocationIdentity locid = n.location().getLocationIdentity();
        assert locid != FINAL_LOCATION;

        Node upperBound = n.getLastLocationAccess();
        Block upperBoundBlock = forKillLocation(upperBound);
        Block earliestBlock = earliestBlock(n);
        assert upperBoundBlock.dominates(earliestBlock) : "upper bound (" + upperBoundBlock + ") should dominate earliest (" + earliestBlock + ")";

        Block currentBlock = latestBlock(n, strategy);
        assert currentBlock != null && earliestBlock.dominates(currentBlock) : "earliest (" + earliestBlock + ") should dominate latest block (" + currentBlock + ")";
        Block previousBlock = currentBlock;

        Debug.printf("processing %s (accessing %s): latest %s, earliest %s, upper bound %s (%s)\n", n, locid, currentBlock, earliestBlock, upperBoundBlock, upperBound);

        int iterations = 0;
        // iterate the dominator tree
        while (true) {
            iterations++;
            Node lastKill = blockToKillMap.get(currentBlock).get(locid);
            assert lastKill != null : "should be never null, due to init of killMaps: " + currentBlock + ", location: " + locid;

            if (lastKill.equals(upperBound)) {
                // assign node to the block which kills the location

                if (previousBlock.getBeginNode() instanceof MergeNode) {
                    // merges kill locations right at the beginning of a block. if a merge is the
                    // killing node, we assign it to the dominating block.

                    MergeNode merge = (MergeNode) previousBlock.getBeginNode();
                    Node killer = mergeToKillMap.get(merge).get(locid);

                    if (killer != null && killer == merge) {
                        printIterations(iterations, "kill by merge: " + currentBlock);
                        return currentBlock;
                    }
                }

                // current block matches last access, that means the previous (dominated) block
                // kills the location, therefore schedule read to previous block.
                printIterations(iterations, "regular kill: " + previousBlock);
                return previousBlock;
            }

            if (upperBoundBlock == currentBlock) {
                printIterations(iterations, "upper bound: " + currentBlock + ", previous: " + previousBlock);
                return currentBlock;
            }

            previousBlock = currentBlock;
            currentBlock = currentBlock.getDominator();
            assert currentBlock != null;
        }
    }

    private static void printIterations(int iterations, String desc) {
        Debug.printf("iterations: %d,  %s\n", iterations, desc);
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
        if (node.recordsUsages()) {
            for (Node usage : node.usages()) {
                blocksForUsage(node, usage, cdbc, strategy);
            }
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

        assert cdbc.block == null || earliestBlock(node).dominates(cdbc.block) : "failed to find correct latest schedule for " + node + ". cdbc: " + cdbc.block + ", earliest: " + earliestBlock(node);
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
            this.block = commonDominator(this.block, newBlock);
        }
    }

    /**
     * Determines the earliest block in which the given node can be scheduled.
     */
    private Block earliestBlock(Node node) {
        if (node.isExternal()) {
            return cfg.getStartBlock();
        }

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

    /**
     * Schedules a node out of loop based on its earliest schedule. Note that this movement is only
     * valid if it's done for <b>every</b> other node in the schedule, otherwise this movement is
     * not valid.
     * 
     * @param n Node to schedule
     * @param latestBlock latest possible schedule for {@code n}
     * @param earliest earliest possible schedule for {@code n}
     * @return block schedule for {@code n} which is not inside a loop (if possible)
     */
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
                } else if (unscheduledUsage instanceof AbstractBeginNode) {
                    // Only FrameStates can be connected to BeginNodes.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    // If a FrameState belongs to a BeginNode then it's inputs will be placed at the
                    // common dominator of all EndNodes.
                    for (Node pred : unscheduledUsage.cfgPredecessors()) {
                        closure.apply(cfg.getNodeToBlock().get(pred));
                    }
                } else {
                    // For the time being, only FrameStates can be connected to StateSplits.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    if (!(unscheduledUsage instanceof NodeWithState)) {
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
        if (node.recordsUsages()) {
            for (Node usage : node.usages().filter(ScheduledNode.class)) {
                assignBlockToNode((ScheduledNode) usage, strategy);
            }
        }
        // now true usages are ready
    }

    private void sortNodesWithinBlocks(StructuredGraph graph, SchedulingStrategy strategy) {
        NodeBitMap visited = graph.createNodeBitMap();
        NodeBitMap beforeLastLocation = graph.createNodeBitMap();
        for (Block b : cfg.getBlocks()) {
            sortNodesWithinBlock(b, visited, beforeLastLocation, strategy);
            assert noDuplicatedNodesInBlock(b) : "duplicated nodes in " + b;
        }
    }

    private boolean noDuplicatedNodesInBlock(Block b) {
        List<ScheduledNode> list = blockToNodesMap.get(b);
        HashSet<ScheduledNode> hashset = new HashSet<>(list);
        return list.size() == hashset.size();
    }

    private void sortNodesWithinBlock(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation, SchedulingStrategy strategy) {
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
                sortedInstructions = sortNodesWithinBlockLatest(b, visited, beforeLastLocation);
                break;
            default:
                throw new GraalInternalError("unknown scheduling strategy");
        }
        assert filterSchedulableNodes(blockToNodesMap.get(b)).size() == removeProxies(sortedInstructions).size() : "sorted block does not contain the same amount of nodes: " +
                        filterSchedulableNodes(blockToNodesMap.get(b)) + " vs. " + removeProxies(sortedInstructions);
        assert sameOrderForFixedNodes(blockToNodesMap.get(b), sortedInstructions) : "fixed nodes in sorted block are not in the same order";
        blockToNodesMap.put(b, sortedInstructions);
    }

    private static List<ScheduledNode> removeProxies(List<ScheduledNode> list) {
        List<ScheduledNode> result = new ArrayList<>();
        for (ScheduledNode n : list) {
            if (!(n instanceof ProxyNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static List<ScheduledNode> filterSchedulableNodes(List<ScheduledNode> list) {
        List<ScheduledNode> result = new ArrayList<>();
        for (ScheduledNode n : list) {
            if (!(n instanceof PhiNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static boolean sameOrderForFixedNodes(List<ScheduledNode> fixed, List<ScheduledNode> sorted) {
        Iterator<ScheduledNode> fixedIterator = fixed.iterator();
        Iterator<ScheduledNode> sortedIterator = sorted.iterator();

        while (sortedIterator.hasNext()) {
            ScheduledNode sortedCurrent = sortedIterator.next();
            if (sortedCurrent instanceof FixedNode) {
                if (!(fixedIterator.hasNext() && fixedIterator.next() == sortedCurrent)) {
                    return false;
                }
            }
        }

        while (fixedIterator.hasNext()) {
            if (fixedIterator.next() instanceof FixedNode) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all inputs. This means that a node is added to the list after all its inputs have been
     * processed.
     */
    private List<ScheduledNode> sortNodesWithinBlockLatest(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation) {
        List<ScheduledNode> instructions = blockToNodesMap.get(b);
        List<ScheduledNode> sortedInstructions = new ArrayList<>(blockToNodesMap.get(b).size() + 2);
        List<FloatingReadNode> reads = new ArrayList<>();

        if (memsched == MemoryScheduling.OPTIMAL) {
            /*
             * TODO: add assert for invariant
             * "floatingreads occur always after memory checkpoints in unsorted list"
             */
            for (ScheduledNode i : instructions) {
                if (i instanceof FloatingReadNode) {
                    FloatingReadNode frn = (FloatingReadNode) i;
                    if (frn.location().getLocationIdentity() != FINAL_LOCATION) {
                        reads.add(frn);
                        if (nodesFor(b).contains(frn.getLastLocationAccess())) {
                            assert !beforeLastLocation.isMarked(frn);
                            beforeLastLocation.mark(frn);
                        }
                    }
                }
            }
        }
        for (ScheduledNode i : instructions) {
            addToLatestSorting(b, i, sortedInstructions, visited, reads, beforeLastLocation);
        }
        assert reads.size() == 0 : "not all reads are scheduled";

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

    private void processKillLocation(Block b, Node node, LocationIdentity identity, List<ScheduledNode> sortedInstructions, NodeBitMap visited, List<FloatingReadNode> reads,
                    NodeBitMap beforeLastLocation) {
        for (FloatingReadNode frn : new ArrayList<>(reads)) { // TODO: change to iterator?
            LocationIdentity readLocation = frn.location().getLocationIdentity();
            assert readLocation != FINAL_LOCATION;
            if (frn.getLastLocationAccess() == node) {
                assert identity == ANY_LOCATION || readLocation == identity : "location doesn't match: " + readLocation + ", " + identity;
                beforeLastLocation.clear(frn);
            } else if (!beforeLastLocation.isMarked(frn) && (readLocation == identity || (!(node instanceof StartNode) && ANY_LOCATION == identity))) {
                // TODO: replace instanceof check with object identity check
                reads.remove(frn);
                addToLatestSorting(b, frn, sortedInstructions, visited, reads, beforeLastLocation);
            }
        }
    }

    private void addUnscheduledToLatestSorting(Block b, VirtualState state, List<ScheduledNode> sortedInstructions, NodeBitMap visited, List<FloatingReadNode> reads, NodeBitMap beforeLastLocation) {
        if (state != null) {
            // UnscheduledNodes should never be marked as visited.
            if (visited.isMarked(state)) {
                throw new SchedulingError();
            }

            for (Node input : state.inputs()) {
                if (input instanceof VirtualState) {
                    addUnscheduledToLatestSorting(b, (VirtualState) input, sortedInstructions, visited, reads, beforeLastLocation);
                } else if (!input.isExternal()) {
                    addToLatestSorting(b, (ScheduledNode) input, sortedInstructions, visited, reads, beforeLastLocation);
                }
            }
        }
    }

    private void addToLatestSorting(Block b, ScheduledNode i, List<ScheduledNode> sortedInstructions, NodeBitMap visited, List<FloatingReadNode> reads, NodeBitMap beforeLastLocation) {
        if (i == null || visited.isMarked(i) || cfg.getNodeToBlock().get(i) != b || i instanceof PhiNode) {
            return;
        }

        FrameState state = null;
        for (Node input : i.inputs()) {
            if (input instanceof FrameState) {
                assert state == null;
                state = (FrameState) input;
            } else if (!input.isExternal()) {
                addToLatestSorting(b, (ScheduledNode) input, sortedInstructions, visited, reads, beforeLastLocation);

            }
        }
        List<FloatingNode> inputs = phantomInputs.get(i);
        if (inputs != null) {
            for (FloatingNode input : inputs) {
                addToLatestSorting(b, input, sortedInstructions, visited, reads, beforeLastLocation);
            }
        }

        if (memsched == MemoryScheduling.OPTIMAL && reads.size() != 0) {
            if (i instanceof MemoryCheckpoint.Single) {
                LocationIdentity identity = ((MemoryCheckpoint.Single) i).getLocationIdentity();
                processKillLocation(b, i, identity, sortedInstructions, visited, reads, beforeLastLocation);
            } else if (i instanceof MemoryCheckpoint.Multi) {
                for (LocationIdentity identity : ((MemoryCheckpoint.Multi) i).getLocationIdentities()) {
                    processKillLocation(b, i, identity, sortedInstructions, visited, reads, beforeLastLocation);
                }
            }
            assert MemoryCheckpoint.TypeAssertion.correctType(i);
        }

        addToLatestSorting(b, (ScheduledNode) i.predecessor(), sortedInstructions, visited, reads, beforeLastLocation);
        visited.mark(i);
        addUnscheduledToLatestSorting(b, state, sortedInstructions, visited, reads, beforeLastLocation);

        // Now predecessors and inputs are scheduled => we can add this node.
        if (!sortedInstructions.contains(i)) {
            sortedInstructions.add(i);
        }

        if (i instanceof FloatingReadNode) {
            reads.remove(i);
        }
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
            if (instruction == null || visited.isMarked(instruction) || cfg.getNodeToBlock().get(instruction) != b || instruction instanceof PhiNode) {
                return;
            }

            visited.mark(instruction);
            if (instruction.recordsUsages()) {
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
