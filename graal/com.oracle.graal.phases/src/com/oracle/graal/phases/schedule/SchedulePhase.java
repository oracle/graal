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
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.nodes.cfg.ControlFlowGraph.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.LoopInfo;
import com.oracle.graal.phases.util.*;

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
        EARLIEST,
        LATEST,
        LATEST_OUT_OF_LOOPS
    }

    public static enum MemoryScheduling {
        NONE,
        OPTIMAL
    }

    private class KillSet implements Iterable<LocationIdentity> {
        private final Set<LocationIdentity> set;

        public KillSet() {
            this.set = new ArraySet<>();
        }

        public KillSet(KillSet other) {
            this.set = new HashSet<>(other.set);
        }

        public void add(LocationIdentity locationIdentity) {
            set.add(locationIdentity);
        }

        public void addAll(KillSet other) {
            set.addAll(other.set);
        }

        public Iterator<LocationIdentity> iterator() {
            return set.iterator();
        }

        public boolean isKilled(LocationIdentity locationIdentity) {
            return set.contains(locationIdentity);
        }

    }

    private class NewMemoryScheduleClosure extends BlockIteratorClosure<KillSet> {
        private Node excludeNode;
        private Block upperBoundBlock;

        public NewMemoryScheduleClosure(Node excludeNode, Block upperBoundBlock) {
            this.excludeNode = excludeNode;
            this.upperBoundBlock = upperBoundBlock;
        }

        public NewMemoryScheduleClosure() {
            this(null, null);
        }

        @Override
        protected KillSet getInitialState() {
            return cloneState(blockToKillSet.get(getCFG().getStartBlock()));
        }

        @Override
        protected KillSet processBlock(Block block, KillSet currentState) {
            assert block != null;
            currentState.addAll(computeKillSet(block, block == upperBoundBlock ? excludeNode : null));
            return currentState;
        }

        @Override
        protected KillSet merge(Block merge, List<KillSet> states) {
            assert merge.getBeginNode() instanceof MergeNode;

            KillSet initKillSet = new KillSet();
            for (KillSet state : states) {
                initKillSet.addAll(state);
            }

            return initKillSet;
        }

        @Override
        protected KillSet cloneState(KillSet state) {
            return new KillSet(state);
        }

        @Override
        protected List<KillSet> processLoop(Loop<Block> loop, KillSet state) {
            LoopInfo<KillSet> info = ReentrantBlockIterator.processLoop(this, loop, cloneState(state));

            assert loop.header.getBeginNode() instanceof LoopBeginNode;
            KillSet headerState = merge(loop.header, info.endStates);

            // second iteration, for propagating information to loop exits
            info = ReentrantBlockIterator.processLoop(this, loop, cloneState(headerState));

            return info.exitStates;
        }
    }

    /**
     * gather all kill locations by iterating trough the nodes assigned to a block.
     *
     * assumptions: {@link MemoryCheckpoint MemoryCheckPoints} are {@link FixedNode FixedNodes}.
     *
     * @param block block to analyze
     * @param excludeNode if null, compute normal set of kill locations. if != null, don't add kills
     *            until we reach excludeNode.
     * @return all killed locations
     */
    private KillSet computeKillSet(Block block, Node excludeNode) {
        // cache is only valid if we don't potentially exclude kills from the set
        if (excludeNode == null) {
            KillSet cachedSet = blockToKillSet.get(block);
            if (cachedSet != null) {
                return cachedSet;
            }
        }

        // add locations to excludedLocations until we reach the excluded node
        boolean foundExcludeNode = excludeNode == null;

        KillSet set = new KillSet();
        KillSet excludedLocations = new KillSet();
        if (block.getBeginNode() instanceof MergeNode) {
            MergeNode mergeNode = (MergeNode) block.getBeginNode();
            for (MemoryPhiNode phi : mergeNode.usages().filter(MemoryPhiNode.class)) {
                if (foundExcludeNode) {
                    set.add(phi.getLocationIdentity());
                } else {
                    excludedLocations.add(phi.getLocationIdentity());
                    foundExcludeNode = phi == excludeNode;
                }
            }
        }

        BeginNode startNode = cfg.getStartBlock().getBeginNode();
        assert startNode instanceof StartNode;

        KillSet accm = foundExcludeNode ? set : excludedLocations;
        for (Node node : block.getNodes()) {
            if (!foundExcludeNode && node == excludeNode) {
                foundExcludeNode = true;
            }
            if (node == startNode) {
                continue;
            }
            if (node instanceof MemoryCheckpoint.Single) {
                LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
                accm.add(identity);
            } else if (node instanceof MemoryCheckpoint.Multi) {
                for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                    accm.add(identity);
                }
            }
            assert MemoryCheckpoint.TypeAssertion.correctType(node);

            if (foundExcludeNode) {
                accm = set;
            }
        }

        // merge it for the cache entry
        excludedLocations.addAll(set);
        blockToKillSet.put(block, excludedLocations);

        return set;
    }

    private KillSet computeKillSet(Block block) {
        return computeKillSet(block, null);
    }

    private ControlFlowGraph cfg;
    private NodeMap<Block> earliestCache;

    /**
     * Map from blocks to the nodes in each block.
     */
    private BlockMap<List<ScheduledNode>> blockToNodesMap;
    private BlockMap<KillSet> blockToKillSet;
    private final SchedulingStrategy selectedStrategy;
    private final MemoryScheduling memsched;

    public SchedulePhase() {
        this(OptScheduleOutOfLoops.getValue() ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        this.memsched = MemoryAwareScheduling.getValue() ? MemoryScheduling.OPTIMAL : MemoryScheduling.NONE;
        this.selectedStrategy = strategy;
    }

    public SchedulePhase(SchedulingStrategy strategy, MemoryScheduling memsched) {
        this.selectedStrategy = strategy;
        this.memsched = memsched;
    }

    @Override
    protected void run(StructuredGraph graph) {
        assert GraphOrder.assertNonCyclicGraph(graph);
        cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        earliestCache = graph.createNodeMap();
        blockToNodesMap = new BlockMap<>(cfg);

        if (memsched == MemoryScheduling.OPTIMAL && selectedStrategy != SchedulingStrategy.EARLIEST && graph.getNodes(FloatingReadNode.class).isNotEmpty()) {
            blockToKillSet = new BlockMap<>(cfg);

            assignBlockToNodes(graph, selectedStrategy);
            printSchedule("after assign nodes to blocks");

            sortNodesWithinBlocks(graph, selectedStrategy);
            printSchedule("after sorting nodes within blocks");
        } else {
            assignBlockToNodes(graph, selectedStrategy);
            sortNodesWithinBlocks(graph, selectedStrategy);
        }
    }

    private Block blockForMemoryNode(MemoryNode memory) {
        MemoryNode current = memory;
        while (current instanceof MemoryProxy) {
            current = ((MemoryProxy) current).getOriginalMemoryNode();
        }
        Block b = cfg.getNodeToBlock().get(current.asNode());
        assert b != null : "all lastAccess locations should have a block assignment from CFG";
        return b;
    }

    private void printSchedule(String desc) {
        if (Debug.isLogEnabled()) {
            Formatter buf = new Formatter();
            buf.format("=== %s / %s / %s (%s) ===%n", getCFG().getStartBlock().getBeginNode().graph(), selectedStrategy, memsched, desc);
            for (Block b : getCFG().getBlocks()) {
                buf.format("==== b: %s (loopDepth: %s). ", b, b.getLoopDepth());
                buf.format("dom: %s. ", b.getDominator());
                buf.format("post-dom: %s. ", b.getPostdominator());
                buf.format("preds: %s. ", b.getPredecessors());
                buf.format("succs: %s ====%n", b.getSuccessors());
                BlockMap<KillSet> killSets = blockToKillSet;
                if (killSets != null) {
                    buf.format("X block kills: %n");
                    if (killSets.get(b) != null) {
                        for (LocationIdentity locId : killSets.get(b)) {
                            buf.format("X %s killed by %s%n", locId, "dunno anymore");
                        }
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
            buf.format("%n");
            Debug.log("%s", buf);
        }
    }

    private static void printNode(Node n) {
        Formatter buf = new Formatter();
        buf.format("%s", n);
        if (n instanceof MemoryCheckpoint.Single) {
            buf.format(" // kills %s", ((MemoryCheckpoint.Single) n).getLocationIdentity());
        } else if (n instanceof MemoryCheckpoint.Multi) {
            buf.format(" // kills ");
            for (LocationIdentity locid : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                buf.format("%s, ", locid);
            }
        } else if (n instanceof FloatingReadNode) {
            FloatingReadNode frn = (FloatingReadNode) n;
            buf.format(" // from %s", frn.location().getLocationIdentity());
            buf.format(", lastAccess: %s", frn.getLastLocationAccess());
            buf.format(", object: %s", frn.object());
        } else if (n instanceof GuardNode) {
            buf.format(", anchor: %s", ((GuardNode) n).getAnchor());
        }
        Debug.log("%s", buf);
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

        if (cfg.getNodeToBlock().containsKey(node)) {
            return;
        }
        // PhiNodes, ProxyNodes and FixedNodes should already have been placed in blocks by
        // ControlFlowGraph.identifyBlocks
        if (node instanceof PhiNode || node instanceof ProxyNode || node instanceof FixedNode) {
            throw new SchedulingError("%s should already have been placed in a block", node);
        }

        Block earliestBlock = earliestBlock(node);
        Block block = null;
        Block latest = null;
        switch (strategy) {
            case EARLIEST:
                block = earliestBlock;
                break;
            case LATEST:
            case LATEST_OUT_OF_LOOPS:
                boolean scheduleRead = memsched == MemoryScheduling.OPTIMAL && node instanceof FloatingReadNode && ((FloatingReadNode) node).location().getLocationIdentity() != FINAL_LOCATION;
                if (scheduleRead) {
                    FloatingReadNode read = (FloatingReadNode) node;
                    block = optimalBlock(read, strategy);
                    Debug.log("schedule for %s: %s", read, block);
                    assert earliestBlock.dominates(block) : String.format("%s (%s) cannot be scheduled before earliest schedule (%s). location: %s", read, block, earliestBlock,
                                    read.getLocationIdentity());
                } else {
                    block = latestBlock(node, strategy);
                }
                if (block == null) {
                    // handle nodes without usages
                    block = earliestBlock;
                } else if (strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS && !(node instanceof VirtualObjectNode)) {
                    // schedule at the latest position possible in the outermost loop possible
                    latest = block;
                    block = scheduleOutOfLoops(node, block, earliestBlock);
                }

                if (assertionEnabled()) {
                    if (scheduleRead) {
                        FloatingReadNode read = (FloatingReadNode) node;
                        MemoryNode lastLocationAccess = read.getLastLocationAccess();
                        Block upperBound = blockForMemoryNode(lastLocationAccess);
                        assert upperBound.dominates(block) : String.format(
                                        "out of loop movement voilated memory semantics for %s (location %s). moved to %s but upper bound is %s (earliest: %s, latest: %s)", read,
                                        read.getLocationIdentity(), block, upperBound, earliestBlock, latest);
                    }
                }
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

    @SuppressWarnings("all")
    private static boolean assertionEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    /**
     * this method tries to find the "optimal" schedule for a read, by pushing it down towards its
     * latest schedule starting by the earliest schedule. By doing this, it takes care of memory
     * dependencies using kill sets.
     *
     * In terms of domination relation, it looks like this:
     *
     * <pre>
     *    U      upperbound block, defined by last access location of the floating read
     *    &and;
     *    E      earliest block
     *    &and;
     *    O      optimal block, first block that contains a kill of the read's location
     *    &and;
     *    L      latest block
     * </pre>
     *
     * i.e. <code>upperbound `dom` earliest `dom` optimal `dom` latest</code>.
     *
     */
    private Block optimalBlock(FloatingReadNode n, SchedulingStrategy strategy) {
        assert memsched == MemoryScheduling.OPTIMAL;

        LocationIdentity locid = n.location().getLocationIdentity();
        assert locid != FINAL_LOCATION;

        Block upperBoundBlock = blockForMemoryNode(n.getLastLocationAccess());
        Block earliestBlock = earliestBlock(n);
        assert upperBoundBlock.dominates(earliestBlock) : "upper bound (" + upperBoundBlock + ") should dominate earliest (" + earliestBlock + ")";

        Block latestBlock = latestBlock(n, strategy);
        assert latestBlock != null && earliestBlock.dominates(latestBlock) : "earliest (" + earliestBlock + ") should dominate latest block (" + latestBlock + ")";

        Debug.log("processing %s (accessing %s): latest %s, earliest %s, upper bound %s (%s)", n, locid, latestBlock, earliestBlock, upperBoundBlock, n.getLastLocationAccess());
        if (earliestBlock == latestBlock) {
            // read is fixed to this block, nothing to schedule
            return latestBlock;
        }

        Deque<Block> path = computePathInDominatorTree(earliestBlock, latestBlock);
        Debug.log("|path| is %d: %s", path.size(), path);

        // follow path, start at earliest schedule
        while (path.size() > 0) {
            Block currentBlock = path.pop();
            Block dominatedBlock = path.size() == 0 ? null : path.peek();
            if (dominatedBlock != null && !currentBlock.getSuccessors().contains(dominatedBlock)) {
                // the dominated block is not a successor -> we have a split
                assert dominatedBlock.getBeginNode() instanceof MergeNode;

                HashSet<Block> region = computeRegion(currentBlock, dominatedBlock);
                Debug.log("> merge.  %s: region for %s -> %s: %s", n, currentBlock, dominatedBlock, region);

                NewMemoryScheduleClosure closure = null;
                if (currentBlock == upperBoundBlock) {
                    assert earliestBlock == upperBoundBlock;
                    // don't treat lastLocationAccess node as a kill for this read.
                    closure = new NewMemoryScheduleClosure(ValueNodeUtil.asNode(n.getLastLocationAccess()), upperBoundBlock);
                } else {
                    closure = new NewMemoryScheduleClosure();
                }
                Map<FixedNode, KillSet> states;
                states = ReentrantBlockIterator.apply(closure, currentBlock, new KillSet(), region);

                KillSet mergeState = states.get(dominatedBlock.getBeginNode());
                if (mergeState.isKilled(locid)) {
                    // location got killed somewhere in the branches,
                    // thus we've to move the read above it
                    return currentBlock;
                }
            } else {
                if (currentBlock == upperBoundBlock) {
                    assert earliestBlock == upperBoundBlock;
                    KillSet ks = computeKillSet(upperBoundBlock, ValueNodeUtil.asNode(n.getLastLocationAccess()));
                    if (ks.isKilled(locid)) {
                        return upperBoundBlock;
                    }
                } else if (dominatedBlock == null || computeKillSet(currentBlock).isKilled(locid)) {
                    return currentBlock;
                }
            }
        }
        throw new SchedulingError("should have found a block for " + n);
    }

    /**
     * compute path in dominator tree from earliest schedule to latest schedule.
     *
     * @return the order of the stack is such as the first element is the earliest schedule.
     */
    private static Deque<Block> computePathInDominatorTree(Block earliestBlock, Block latestBlock) {
        Deque<Block> path = new LinkedList<>();
        Block currentBlock = latestBlock;
        while (currentBlock != null && earliestBlock.dominates(currentBlock)) {
            path.push(currentBlock);
            currentBlock = currentBlock.getDominator();
        }
        assert path.peek() == earliestBlock;
        return path;
    }

    /**
     * compute a set that contains all blocks in a region spanned by dominatorBlock and
     * dominatedBlock (exclusive the dominatedBlock).
     */
    private static HashSet<Block> computeRegion(Block dominatorBlock, Block dominatedBlock) {
        HashSet<Block> region = new HashSet<>();
        Queue<Block> workList = new LinkedList<>();

        region.add(dominatorBlock);
        workList.addAll(dominatorBlock.getSuccessors());
        while (workList.size() > 0) {
            Block current = workList.poll();
            if (current != dominatedBlock) {
                region.add(current);
                for (Block b : current.getSuccessors()) {
                    if (!region.contains(b) && !workList.contains(b)) {
                        workList.offer(b);
                    }
                }
            }
        }
        assert !region.contains(dominatedBlock) && region.containsAll(dominatedBlock.getPredecessors());
        return region;
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

        if (assertionEnabled()) {
            if (cdbc.block != null && !earliestBlock(node).dominates(cdbc.block)) {
                throw new SchedulingError("failed to find correct latest schedule for %s. cdbc: %s, earliest: %s", node, cdbc.block, earliestBlock(node));
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
            this.block = commonDominator(this.block, newBlock);
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
         */

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
            if (earliest == null) {
                earliest = inputEarliest;
            } else if (earliest != inputEarliest) {
                // Find out whether earliest or inputEarliest is earlier.
                Block a = earliest.getDominator();
                Block b = inputEarliest;
                while (true) {
                    if (a == inputEarliest || b == null) {
                        // Nothing to change, the previous earliest block is still earliest.
                        break;
                    } else if (b == earliest || a == null) {
                        // New earliest is the earliest.
                        earliest = inputEarliest;
                        break;
                    }
                    a = a.getDominator();
                    b = b.getDominator();
                }
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
                } else if (unscheduledUsage instanceof BeginNode) {
                    // Only FrameStates can be connected to BeginNodes.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    if (unscheduledUsage instanceof StartNode) {
                        closure.apply(cfg.getNodeToBlock().get(unscheduledUsage));
                    } else {
                        // If a FrameState belongs to a BeginNode then it's inputs will be placed at
                        // the common dominator of all EndNodes.
                        for (Node pred : unscheduledUsage.cfgPredecessors()) {
                            closure.apply(cfg.getNodeToBlock().get(pred));
                        }
                    }
                } else {
                    // For the time being, FrameStates can only be connected to NodeWithState.
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

    private static class SortState {
        private Block block;
        private NodeBitMap visited;
        private NodeBitMap beforeLastLocation;
        private List<ScheduledNode> sortedInstructions;
        private List<FloatingReadNode> reads;

        SortState(Block block, NodeBitMap visited, NodeBitMap beforeLastLocation, List<ScheduledNode> sortedInstructions) {
            this.block = block;
            this.visited = visited;
            this.beforeLastLocation = beforeLastLocation;
            this.sortedInstructions = sortedInstructions;
            this.reads = null;
        }

        public Block currentBlock() {
            return block;
        }

        void markVisited(Node n) {
            visited.mark(n);
        }

        boolean isVisited(Node n) {
            return visited.isMarked(n);
        }

        void markBeforeLastLocation(FloatingReadNode n) {
            beforeLastLocation.mark(n);
        }

        void clearBeforeLastLocation(FloatingReadNode frn) {
            beforeLastLocation.clear(frn);
        }

        boolean isBeforeLastLocation(FloatingReadNode n) {
            return beforeLastLocation.isMarked(n);
        }

        void addRead(FloatingReadNode n) {
            if (reads == null) {
                reads = new ArrayList<>();
            }
            reads.add(n);
        }

        int readsSize() {
            if (reads == null) {
                return 0;
            }
            return reads.size();
        }

        void removeRead(ScheduledNode i) {
            assert reads != null;
            reads.remove(i);
        }

        List<FloatingReadNode> readsSnapshot() {
            assert reads != null;
            return new ArrayList<>(reads);
        }

        List<ScheduledNode> getSortedInstructions() {
            return sortedInstructions;
        }

        boolean containsInstruction(ScheduledNode i) {
            return sortedInstructions.contains(i);
        }

        void addInstruction(ScheduledNode i) {
            sortedInstructions.add(i);
        }
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all inputs. This means that a node is added to the list after all its inputs have been
     * processed.
     */
    private List<ScheduledNode> sortNodesWithinBlockLatest(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation) {
        SortState state = new SortState(b, visited, beforeLastLocation, new ArrayList<>(blockToNodesMap.get(b).size() + 2));
        List<ScheduledNode> instructions = blockToNodesMap.get(b);

        if (memsched == MemoryScheduling.OPTIMAL) {
            for (ScheduledNode i : instructions) {
                if (i instanceof FloatingReadNode) {
                    FloatingReadNode frn = (FloatingReadNode) i;
                    if (frn.location().getLocationIdentity() != FINAL_LOCATION) {
                        state.addRead(frn);
                        if (nodesFor(b).contains(frn.getLastLocationAccess())) {
                            assert !state.isBeforeLastLocation(frn);
                            state.markBeforeLastLocation(frn);
                        }
                    }
                }
            }
        }

        for (ScheduledNode i : instructions) {
            addToLatestSorting(i, state);
        }
        assert state.readsSize() == 0 : "not all reads are scheduled";

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off
        // it).
        List<ScheduledNode> sortedInstructions = state.getSortedInstructions();
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
                    throw new GraalGraphInternalError("Schedule is not possible : needs to move a node after the last node of the block which can not be move").addContext(lastSorted).addContext(
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

    private void processKillLocation(Node node, LocationIdentity identity, SortState state) {
        for (FloatingReadNode frn : state.readsSnapshot()) {
            LocationIdentity readLocation = frn.location().getLocationIdentity();
            assert readLocation != FINAL_LOCATION;
            if (frn.getLastLocationAccess() == node) {
                assert identity == ANY_LOCATION || readLocation == identity : "location doesn't match: " + readLocation + ", " + identity;
                state.clearBeforeLastLocation(frn);
            } else if (!state.isBeforeLastLocation(frn) && (readLocation == identity || (node != getCFG().graph.start() && ANY_LOCATION == identity))) {
                state.removeRead(frn);
                addToLatestSorting(frn, state);
            }
        }
    }

    private void addUnscheduledToLatestSorting(VirtualState state, SortState sortState) {
        if (state != null) {
            // UnscheduledNodes should never be marked as visited.
            if (sortState.isVisited(state)) {
                throw new SchedulingError();
            }

            for (Node input : state.inputs()) {
                if (input instanceof VirtualState) {
                    addUnscheduledToLatestSorting((VirtualState) input, sortState);
                } else {
                    addToLatestSorting((ScheduledNode) input, sortState);
                }
            }
        }
    }

    private void addToLatestSorting(ScheduledNode i, SortState state) {
        if (i == null || state.isVisited(i) || cfg.getNodeToBlock().get(i) != state.currentBlock() || i instanceof PhiNode) {
            return;
        }

        FrameState stateAfter = null;
        if (i instanceof StateSplit) {
            stateAfter = ((StateSplit) i).stateAfter();
        }

        if (i instanceof LoopExitNode) {
            for (ProxyNode proxy : ((LoopExitNode) i).proxies()) {
                addToLatestSorting(proxy, state);
            }
        }

        for (Node input : i.inputs()) {
            if (input instanceof FrameState) {
                if (input != stateAfter) {
                    addUnscheduledToLatestSorting((FrameState) input, state);
                }
            } else {
                if (!(i instanceof ProxyNode && input instanceof LoopExitNode)) {
                    addToLatestSorting((ScheduledNode) input, state);
                }
            }
        }

        if (memsched == MemoryScheduling.OPTIMAL && state.readsSize() != 0) {
            if (i instanceof MemoryCheckpoint.Single) {
                LocationIdentity identity = ((MemoryCheckpoint.Single) i).getLocationIdentity();
                processKillLocation(i, identity, state);
            } else if (i instanceof MemoryCheckpoint.Multi) {
                for (LocationIdentity identity : ((MemoryCheckpoint.Multi) i).getLocationIdentities()) {
                    processKillLocation(i, identity, state);
                }
            }
            assert MemoryCheckpoint.TypeAssertion.correctType(i);
        }

        addToLatestSorting((ScheduledNode) i.predecessor(), state);
        state.markVisited(i);
        addUnscheduledToLatestSorting(stateAfter, state);

        // Now predecessors and inputs are scheduled => we can add this node.
        if (!state.containsInstruction(i)) {
            state.addInstruction(i);
        }

        if (state.readsSize() != 0 && i instanceof FloatingReadNode) {
            state.removeRead(i);
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

            if (instruction instanceof BeginNode) {
                ArrayList<ProxyNode> proxies = (instruction instanceof LoopExitNode) ? new ArrayList<>() : null;
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
