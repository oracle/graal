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
import static com.oracle.graal.compiler.common.cfg.AbstractControlFlowGraph.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
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

    static int created;

    private class LocationSet {
        private LocationIdentity firstLocation;
        private List<LocationIdentity> list;

        public LocationSet() {
            list = null;
        }

        public LocationSet(LocationSet other) {
            this.firstLocation = other.firstLocation;
            if (other.list != null && other.list.size() > 0) {
                list = new ArrayList<>(other.list);
            }
        }

        private void initList() {
            if (list == null) {
                list = new ArrayList<>(4);
            }
        }

        public void add(LocationIdentity location) {
            if (LocationIdentity.ANY_LOCATION.equals(location)) {
                firstLocation = location;
                list = null;
            } else {
                if (firstLocation == null) {
                    firstLocation = location;
                } else if (LocationIdentity.ANY_LOCATION.equals(firstLocation)) {
                    return;
                } else if (location.equals(firstLocation)) {
                    return;
                } else {
                    initList();
                    for (int i = 0; i < list.size(); ++i) {
                        LocationIdentity value = list.get(i);
                        if (location.equals(value)) {
                            return;
                        }
                    }
                    list.add(location);
                }
            }
        }

        public void addAll(LocationSet other) {
            if (other.firstLocation != null) {
                add(other.firstLocation);
            }
            List<LocationIdentity> otherList = other.list;
            if (otherList != null) {
                for (LocationIdentity l : otherList) {
                    add(l);
                }
            }
        }

        public boolean contains(LocationIdentity locationIdentity) {
            assert locationIdentity != null;
            assert !locationIdentity.equals(LocationIdentity.ANY_LOCATION);
            assert !locationIdentity.equals(LocationIdentity.FINAL_LOCATION);
            if (LocationIdentity.ANY_LOCATION.equals(firstLocation)) {
                return true;
            }
            if (locationIdentity.equals(firstLocation)) {
                return true;
            }
            if (list != null) {
                for (int i = 0; i < list.size(); ++i) {
                    LocationIdentity value = list.get(i);
                    if (locationIdentity.equals(value)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public List<LocationIdentity> getCopyAsList() {
            ArrayList<LocationIdentity> result = new ArrayList<>();
            if (firstLocation != null) {
                result.add(firstLocation);
            }
            if (list != null) {
                result.addAll(list);
            }
            return result;
        }
    }

    private class NewMemoryScheduleClosure extends BlockIteratorClosure<LocationSet> {
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
        protected LocationSet getInitialState() {
            return cloneState(blockToKillSet.get(getCFG().getStartBlock()));
        }

        @Override
        protected LocationSet processBlock(Block block, LocationSet currentState) {
            assert block != null;
            currentState.addAll(computeKillSet(block, block == upperBoundBlock ? excludeNode : null));
            return currentState;
        }

        @Override
        protected LocationSet merge(Block merge, List<LocationSet> states) {
            assert merge.getBeginNode() instanceof AbstractMergeNode;

            LocationSet initKillSet = new LocationSet();
            for (LocationSet state : states) {
                initKillSet.addAll(state);
            }

            return initKillSet;
        }

        @Override
        protected LocationSet cloneState(LocationSet state) {
            return new LocationSet(state);
        }

        @Override
        protected List<LocationSet> processLoop(Loop<Block> loop, LocationSet state) {
            LoopInfo<LocationSet> info = ReentrantBlockIterator.processLoop(this, loop, cloneState(state));

            assert loop.getHeader().getBeginNode() instanceof LoopBeginNode;
            LocationSet headerState = merge(loop.getHeader(), info.endStates);

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
    private LocationSet computeKillSet(Block block, Node excludeNode) {
        // cache is only valid if we don't potentially exclude kills from the set
        if (excludeNode == null) {
            LocationSet cachedSet = blockToKillSet.get(block);
            if (cachedSet != null) {
                return cachedSet;
            }
        }

        // add locations to excludedLocations until we reach the excluded node
        boolean foundExcludeNode = excludeNode == null;

        LocationSet set = new LocationSet();
        LocationSet excludedLocations = new LocationSet();
        if (block.getBeginNode() instanceof AbstractMergeNode) {
            AbstractMergeNode mergeNode = (AbstractMergeNode) block.getBeginNode();
            for (MemoryPhiNode phi : mergeNode.usages().filter(MemoryPhiNode.class)) {
                if (foundExcludeNode) {
                    set.add(phi.getLocationIdentity());
                } else {
                    excludedLocations.add(phi.getLocationIdentity());
                    foundExcludeNode = phi == excludeNode;
                }
            }
        }

        AbstractBeginNode startNode = cfg.getStartBlock().getBeginNode();
        assert startNode instanceof StartNode;

        LocationSet accm = foundExcludeNode ? set : excludedLocations;
        for (Node node : block.getNodes()) {
            if (!foundExcludeNode && node == excludeNode) {
                foundExcludeNode = true;
            }
            if (node != startNode) {
                if (node instanceof MemoryCheckpoint.Single) {
                    LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
                    accm.add(identity);
                } else if (node instanceof MemoryCheckpoint.Multi) {
                    for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                        accm.add(identity);
                    }
                }
                assert MemoryCheckpoint.TypeAssertion.correctType(node);
            }

            if (foundExcludeNode) {
                accm = set;
            }
        }

        // merge it for the cache entry
        excludedLocations.addAll(set);
        blockToKillSet.put(block, excludedLocations);

        return set;
    }

    private LocationSet computeKillSet(Block block) {
        return computeKillSet(block, null);
    }

    private ControlFlowGraph cfg;
    private NodeMap<Block> earliestCache;

    /**
     * Map from blocks to the nodes in each block.
     */
    private BlockMap<List<ValueNode>> blockToNodesMap;
    private BlockMap<LocationSet> blockToKillSet;
    private final SchedulingStrategy selectedStrategy;
    private boolean scheduleConstants;

    public SchedulePhase() {
        this(OptScheduleOutOfLoops.getValue() ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        this.selectedStrategy = strategy;
    }

    public void setScheduleConstants(boolean value) {
        scheduleConstants = value;
    }

    @Override
    protected void run(StructuredGraph graph) {
        assert GraphOrder.assertNonCyclicGraph(graph);
        cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        earliestCache = graph.createNodeMap();
        blockToNodesMap = new BlockMap<>(cfg);

        if (selectedStrategy != SchedulingStrategy.EARLIEST) {
            blockToKillSet = new BlockMap<>(cfg);
        }

        assignBlockToNodes(graph, selectedStrategy);
        printSchedule("after assign nodes to blocks");

        sortNodesWithinBlocks(graph, selectedStrategy);
        printSchedule("after sorting nodes within blocks");
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
            printScheduleHelper(desc);
        }
    }

    private void printScheduleHelper(String desc) {
        Formatter buf = new Formatter();
        buf.format("=== %s / %s / %s ===%n", getCFG().getStartBlock().getBeginNode().graph(), selectedStrategy, desc);
        for (Block b : getCFG().getBlocks()) {
            buf.format("==== b: %s (loopDepth: %s). ", b, b.getLoopDepth());
            buf.format("dom: %s. ", b.getDominator());
            buf.format("post-dom: %s. ", b.getPostdominator());
            buf.format("preds: %s. ", b.getPredecessors());
            buf.format("succs: %s ====%n", b.getSuccessors());
            BlockMap<LocationSet> killSets = blockToKillSet;
            if (killSets != null) {
                buf.format("X block kills: %n");
                if (killSets.get(b) != null) {
                    for (LocationIdentity locId : killSets.get(b).getCopyAsList()) {
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
    public BlockMap<List<ValueNode>> getBlockToNodesMap() {
        return blockToNodesMap;
    }

    /**
     * Gets the nodes in a given block.
     */
    public List<ValueNode> nodesFor(Block block) {
        return blockToNodesMap.get(block);
    }

    private void assignBlockToNodes(StructuredGraph graph, SchedulingStrategy strategy) {
        for (Block block : cfg.getBlocks()) {
            List<ValueNode> nodes = new ArrayList<>();
            if (blockToNodesMap.get(block) != null) {
                throw new SchedulingError();
            }
            blockToNodesMap.put(block, nodes);
            for (FixedNode node : block.getNodes()) {
                nodes.add(node);
            }
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof ValueNode) {
                assignBlockToNode((ValueNode) n, strategy);
            }
        }
    }

    /**
     * Assigns a block to the given node. This method expects that PhiNodes and FixedNodes are
     * already assigned to a block.
     */
    private void assignBlockToNode(ValueNode node, SchedulingStrategy strategy) {
        assert !node.isDeleted();

        if (cfg.getNodeToBlock().containsKey(node)) {
            return;
        }
        if (!scheduleConstants && node instanceof ConstantNode) {
            return;
        }
        if (node instanceof VirtualObjectNode) {
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
                boolean scheduleRead = node instanceof FloatingReadNode && !((FloatingReadNode) node).location().getLocationIdentity().isImmutable();
                if (scheduleRead) {
                    FloatingReadNode read = (FloatingReadNode) node;
                    block = optimalBlock(read, strategy);
                    Debug.log("schedule for %s: %s", read, block);
                    assert dominates(earliestBlock, block) : String.format("%s (%s) cannot be scheduled before earliest schedule (%s). location: %s", read, block, earliestBlock,
                                    read.getLocationIdentity());
                } else {
                    block = latestBlock(node, strategy, earliestBlock);
                }
                if (block == null) {
                    // handle nodes without usages
                    block = earliestBlock;
                } else if (strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS && !(node instanceof VirtualObjectNode)) {
                    // schedule at the latest position possible in the outermost loop possible
                    latest = block;
                    block = scheduleOutOfLoops(node, block, earliestBlock);
                }

                assert assertReadSchedule(node, earliestBlock, block, latest, scheduleRead);
                break;
            default:
                throw new GraalInternalError("unknown scheduling strategy");
        }
        if (!dominates(earliestBlock, block)) {
            throw new SchedulingError("%s: Graph cannot be scheduled : inconsistent for %s, %d usages, (%s needs to dominate %s)", node.graph(), node, node.getUsageCount(), earliestBlock, block);
        }
        cfg.getNodeToBlock().set(node, block);
        blockToNodesMap.get(block).add(node);
    }

    private boolean assertReadSchedule(ValueNode node, Block earliestBlock, Block block, Block latest, boolean scheduleRead) {
        if (scheduleRead) {
            FloatingReadNode read = (FloatingReadNode) node;
            MemoryNode lastLocationAccess = read.getLastLocationAccess();
            Block upperBound = blockForMemoryNode(lastLocationAccess);
            assert dominates(upperBound, block) : String.format("out of loop movement voilated memory semantics for %s (location %s). moved to %s but upper bound is %s (earliest: %s, latest: %s)",
                            read, read.getLocationIdentity(), block, upperBound, earliestBlock, latest);
        }
        return true;
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
        LocationIdentity locid = n.location().getLocationIdentity();
        assert !locid.isImmutable();

        Block upperBoundBlock = blockForMemoryNode(n.getLastLocationAccess());
        Block earliestBlock = earliestBlock(n);
        assert dominates(upperBoundBlock, earliestBlock) : "upper bound (" + upperBoundBlock + ") should dominate earliest (" + earliestBlock + ")";

        Block latestBlock = latestBlock(n, strategy, earliestBlock);
        assert latestBlock != null && dominates(earliestBlock, latestBlock) : "earliest (" + earliestBlock + ") should dominate latest block (" + latestBlock + ")";

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
                assert dominatedBlock.getBeginNode() instanceof AbstractMergeNode;

                NewMemoryScheduleClosure closure = null;
                if (currentBlock == upperBoundBlock) {
                    assert earliestBlock == upperBoundBlock;
                    // don't treat lastLocationAccess node as a kill for this read.
                    closure = new NewMemoryScheduleClosure(ValueNodeUtil.asNode(n.getLastLocationAccess()), upperBoundBlock);
                } else {
                    closure = new NewMemoryScheduleClosure();
                }
                Map<FixedNode, LocationSet> states;
                states = ReentrantBlockIterator.apply(closure, currentBlock, new LocationSet(), block -> block == dominatedBlock);

                LocationSet mergeState = states.get(dominatedBlock.getBeginNode());
                if (mergeState.contains(locid)) {
                    // location got killed somewhere in the branches,
                    // thus we've to move the read above it
                    return currentBlock;
                }
            } else {
                if (currentBlock == upperBoundBlock) {
                    assert earliestBlock == upperBoundBlock;
                    LocationSet ks = computeKillSet(upperBoundBlock, ValueNodeUtil.asNode(n.getLastLocationAccess()));
                    if (ks.contains(locid)) {
                        return upperBoundBlock;
                    }
                } else if (dominatedBlock == null || computeKillSet(currentBlock).contains(locid)) {
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
        while (currentBlock != null && dominates(earliestBlock, currentBlock)) {
            path.push(currentBlock);
            currentBlock = currentBlock.getDominator();
        }
        assert path.peek() == earliestBlock;
        return path;
    }

    /**
     * Calculates the last block that the given node could be scheduled in, i.e., the common
     * dominator of all usages. To do so all usages are also assigned to blocks.
     *
     * @param strategy
     * @param earliestBlock
     */
    private Block latestBlock(ValueNode node, SchedulingStrategy strategy, Block earliestBlock) {
        Block block = null;
        for (Node usage : node.usages()) {
            block = blocksForUsage(node, usage, block, earliestBlock, strategy);
            if (block == earliestBlock) {
                break;
            }
        }

        assert assertLatestBlockResult(node, block);
        return block;
    }

    private boolean assertLatestBlockResult(ValueNode node, Block block) throws SchedulingError {
        if (block != null && !dominates(earliestBlock(node), block)) {
            throw new SchedulingError("failed to find correct latest schedule for %s. cdbc: %s, earliest: %s", node, block, earliestBlock(node));
        }
        return true;
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
        return earliestBlockHelper(node, earliest);
    }

    private Block earliestBlockHelper(Node node, Block earliestStart) throws SchedulingError {
        /*
         * All inputs must be in a dominating block, otherwise the graph cannot be scheduled. This
         * implies that the inputs' blocks have a total ordering via their dominance relation. So in
         * order to find the earliest block placement for this node we need to find the input block
         * that is dominated by all other input blocks.
         */
        Block earliest = earliestStart;

        if (node.predecessor() != null) {
            throw new SchedulingError();
        }
        for (Node input : node.inputs()) {
            if (input != null) {
                assert input instanceof ValueNode;
                Block inputEarliest;
                if (input instanceof InvokeWithExceptionNode) {
                    inputEarliest = cfg.getNodeToBlock().get(((InvokeWithExceptionNode) input).next());
                } else {
                    inputEarliest = earliestBlock(input);
                }
                if (earliest == null || earliest.getDominatorDepth() < inputEarliest.getDominatorDepth()) {
                    earliest = inputEarliest;
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
        Loop<?> earliestLoop = earliest.getLoop();
        while (true) {
            Loop<?> curLoop = cur.getLoop();
            if (curLoop == earliestLoop) {
                return result;
            } else {
                Block dom = cur.getDominator();
                if (dom.getLoopDepth() < result.getLoopDepth()) {
                    result = dom;
                }
                cur = dom;
            }
        }
    }

    /**
     * Passes all blocks that a specific usage of a node is in to a given closure. This is more
     * complex than just taking the usage's block because of of PhiNodes and FrameStates.
     *
     * @param node the node that needs to be scheduled
     * @param usage the usage whose blocks need to be considered
     * @param earliestBlock
     */
    private Block blocksForUsage(ValueNode node, Node usage, Block startCurrentBlock, Block earliestBlock, SchedulingStrategy strategy) {
        assert !(node instanceof PhiNode);
        Block currentBlock = startCurrentBlock;
        if (usage instanceof PhiNode) {
            // An input to a PhiNode is used at the end of the predecessor block that corresponds to
            // the PhiNode input.
            // One PhiNode can use an input multiple times, the closure will be called for each
            // usage.
            PhiNode phi = (PhiNode) usage;
            AbstractMergeNode merge = phi.merge();
            Block mergeBlock = cfg.getNodeToBlock().get(merge);
            for (int i = 0; i < phi.valueCount(); ++i) {
                if (phi.valueAt(i) == node) {
                    currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, mergeBlock.getPredecessors().get(i));
                    if (currentBlock == earliestBlock) {
                        break;
                    }
                }
            }
        } else if (usage instanceof VirtualState) {
            // The following logic does not work if node is a PhiNode, but this method is never
            // called for PhiNodes.
            for (Node unscheduledUsage : usage.usages()) {
                if (unscheduledUsage instanceof VirtualState) {
                    // If a FrameState is an outer FrameState this method behaves as if the inner
                    // FrameState was the actual usage, by recursing.
                    currentBlock = blocksForUsage(node, unscheduledUsage, currentBlock, earliestBlock, strategy);
                } else if (unscheduledUsage instanceof AbstractBeginNode) {
                    // Only FrameStates can be connected to BeginNodes.
                    if (!(usage instanceof FrameState)) {
                        throw new SchedulingError(usage.toString());
                    }
                    if (unscheduledUsage instanceof StartNode) {
                        currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, cfg.getNodeToBlock().get(unscheduledUsage));
                    } else {
                        // If a FrameState belongs to a BeginNode then it's inputs will be placed at
                        // the common dominator of all EndNodes.
                        for (Node pred : unscheduledUsage.cfgPredecessors()) {
                            currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, cfg.getNodeToBlock().get(pred));
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
                    assignBlockToNode((ValueNode) unscheduledUsage, strategy);
                    currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, cfg.getNodeToBlock().get(unscheduledUsage));
                }
                if (currentBlock == earliestBlock) {
                    break;
                }
            }
        } else {
            // All other types of usages: Put the input into the same block as the usage.
            assignBlockToNode((ValueNode) usage, strategy);
            currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, cfg.getNodeToBlock().get(usage));
        }
        return currentBlock;
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
        List<ValueNode> list = blockToNodesMap.get(b);
        Set<ValueNode> hashset = Node.newSet(list);
        return list.size() == hashset.size();
    }

    private void sortNodesWithinBlock(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation, SchedulingStrategy strategy) {
        if (visited.isMarked(b.getBeginNode()) || cfg.blockFor(b.getBeginNode()) != b) {
            throw new SchedulingError();
        }
        if (visited.isMarked(b.getEndNode()) || cfg.blockFor(b.getEndNode()) != b) {
            throw new SchedulingError();
        }

        List<ValueNode> sortedInstructions;
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

    private static List<ValueNode> removeProxies(List<ValueNode> list) {
        List<ValueNode> result = new ArrayList<>();
        for (ValueNode n : list) {
            if (!(n instanceof ProxyNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static List<ValueNode> filterSchedulableNodes(List<ValueNode> list) {
        List<ValueNode> result = new ArrayList<>();
        for (ValueNode n : list) {
            if (!(n instanceof PhiNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static boolean sameOrderForFixedNodes(List<ValueNode> fixed, List<ValueNode> sorted) {
        Iterator<ValueNode> fixedIterator = fixed.iterator();
        Iterator<ValueNode> sortedIterator = sorted.iterator();

        while (sortedIterator.hasNext()) {
            ValueNode sortedCurrent = sortedIterator.next();
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
        private List<ValueNode> sortedInstructions;
        private List<FloatingReadNode> reads;

        SortState(Block block, NodeBitMap visited, NodeBitMap beforeLastLocation, List<ValueNode> sortedInstructions) {
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

        void removeRead(ValueNode i) {
            assert reads != null;
            reads.remove(i);
        }

        List<FloatingReadNode> readsSnapshot() {
            assert reads != null;
            return new ArrayList<>(reads);
        }

        List<ValueNode> getSortedInstructions() {
            return sortedInstructions;
        }

        boolean containsInstruction(ValueNode i) {
            return sortedInstructions.contains(i);
        }

        void addInstruction(ValueNode i) {
            sortedInstructions.add(i);
        }
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all inputs. This means that a node is added to the list after all its inputs have been
     * processed.
     */
    private List<ValueNode> sortNodesWithinBlockLatest(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation) {
        SortState state = new SortState(b, visited, beforeLastLocation, new ArrayList<>(blockToNodesMap.get(b).size() + 2));
        List<ValueNode> instructions = blockToNodesMap.get(b);

        for (ValueNode i : instructions) {
            if (i instanceof FloatingReadNode) {
                FloatingReadNode frn = (FloatingReadNode) i;
                if (!frn.location().getLocationIdentity().isImmutable()) {
                    state.addRead(frn);
                    if (nodesFor(b).contains(frn.getLastLocationAccess())) {
                        assert !state.isBeforeLastLocation(frn);
                        state.markBeforeLastLocation(frn);
                    }
                }
            }
        }

        for (ValueNode i : instructions) {
            addToLatestSorting(i, state);
        }
        assert state.readsSize() == 0 : "not all reads are scheduled";

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off
        // it).
        List<ValueNode> sortedInstructions = state.getSortedInstructions();
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
            assert !readLocation.isImmutable();
            if (frn.getLastLocationAccess() == node) {
                assert identity.equals(ANY_LOCATION) || readLocation.equals(identity) || node instanceof MemoryCheckpoint.Multi : "location doesn't match: " + readLocation + ", " + identity;
                state.clearBeforeLastLocation(frn);
            } else if (!state.isBeforeLastLocation(frn) && (readLocation.equals(identity) || (node != getCFG().graph.start() && ANY_LOCATION.equals(identity)))) {
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
                    addToLatestSorting((ValueNode) input, sortState);
                }
            }
        }
    }

    private void addToLatestSorting(ValueNode i, SortState state) {
        if (i == null || state.isVisited(i) || cfg.getNodeToBlock().get(i) != state.currentBlock() || i instanceof PhiNode) {
            return;
        }

        if (i instanceof ProxyNode) {
            ProxyNode proxyNode = (ProxyNode) i;
            addToLatestSorting(proxyNode.value(), state);
            return;
        }

        if (i instanceof LoopExitNode) {
            LoopExitNode loopExitNode = (LoopExitNode) i;
            for (ProxyNode proxy : loopExitNode.proxies()) {
                addToLatestSorting(proxy, state);
            }
        }

        addToLatestSortingHelper(i, state);
    }

    private void addToLatestSortingHelper(ValueNode i, SortState state) {
        FrameState stateAfter = null;
        if (i instanceof StateSplit) {
            stateAfter = ((StateSplit) i).stateAfter();
        }

        addInputsToLatestSorting(i, state, stateAfter);

        if (state.readsSize() != 0) {
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

        addToLatestSorting((ValueNode) i.predecessor(), state);
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

    private void addInputsToLatestSorting(ValueNode i, SortState state, FrameState stateAfter) {
        for (Node input : i.inputs()) {
            if (input instanceof FrameState) {
                if (input != stateAfter) {
                    addUnscheduledToLatestSorting((FrameState) input, state);
                }
            } else {
                addToLatestSorting((ValueNode) input, state);
            }
        }
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all usages. The resulting list is reversed to create an earliest-possible scheduling of
     * nodes.
     */
    private List<ValueNode> sortNodesWithinBlockEarliest(Block b, NodeBitMap visited) {
        List<ValueNode> sortedInstructions = new ArrayList<>(blockToNodesMap.get(b).size() + 2);
        addToEarliestSorting(b, b.getEndNode(), sortedInstructions, visited);
        Collections.reverse(sortedInstructions);
        return sortedInstructions;
    }

    private void addToEarliestSorting(Block b, ValueNode i, List<ValueNode> sortedInstructions, NodeBitMap visited) {
        ValueNode instruction = i;
        while (true) {
            if (instruction == null || visited.isMarked(instruction) || cfg.getNodeToBlock().get(instruction) != b || instruction instanceof PhiNode || instruction instanceof ProxyNode) {
                return;
            }

            visited.mark(instruction);
            for (Node usage : instruction.usages()) {
                if (usage instanceof VirtualState) {
                    // only fixed nodes can have VirtualState -> no need to schedule them
                } else {
                    addToEarliestSorting(b, (ValueNode) usage, sortedInstructions, visited);
                }
            }

            if (instruction instanceof AbstractBeginNode) {
                for (ValueNode inBlock : blockToNodesMap.get(b)) {
                    if (!visited.isMarked(inBlock)) {
                        addToEarliestSorting(b, inBlock, sortedInstructions, visited);
                    }
                }
                sortedInstructions.add(instruction);
                break;
            } else {
                sortedInstructions.add(instruction);
                instruction = (ValueNode) instruction.predecessor();
            }
        }
    }
}
