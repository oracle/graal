/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
    private BlockMap<List<Node>> blockToNodesMap;
    private BlockMap<LocationSet> blockToKillSet;
    private final SchedulingStrategy selectedStrategy;
    private boolean scheduleConstants;
    private NodeMap<Block> nodeToBlockMap;

    public SchedulePhase() {
        this(OptScheduleOutOfLoops.getValue() ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        this.selectedStrategy = strategy;
    }

    public void setScheduleConstants(boolean value) {
        scheduleConstants = value;
    }

    private static final boolean USE_NEW_STRATEGY = true;

    @Override
    protected void run(StructuredGraph graph) {
        // assert GraphOrder.assertNonCyclicGraph(graph);
        cfg = ControlFlowGraph.compute(graph, true, true, true, false);

        if (selectedStrategy == SchedulingStrategy.EARLIEST) {
            this.nodeToBlockMap = graph.createNodeMap();
            this.blockToNodesMap = new BlockMap<>(cfg);
            NodeBitMap visited = graph.createNodeBitMap();
            scheduleEarliestIterative(cfg, blockToNodesMap, nodeToBlockMap, visited, graph);
            return;
        } else if (USE_NEW_STRATEGY) {
            boolean isOutOfLoops = selectedStrategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS;
            if (selectedStrategy == SchedulingStrategy.LATEST || isOutOfLoops) {
                NodeMap<Block> currentNodeMap = graph.createNodeMap();
                BlockMap<List<Node>> earliestBlockToNodesMap = new BlockMap<>(cfg);
                NodeBitMap visited = graph.createNodeBitMap();
                scheduleEarliestIterative(cfg, earliestBlockToNodesMap, currentNodeMap, visited, graph);
                BlockMap<List<Node>> latestBlockToNodesMap = new BlockMap<>(cfg);

                for (Block b : cfg.getBlocks()) {
                    latestBlockToNodesMap.put(b, new ArrayList<Node>());
                }

                BlockMap<ArrayList<FloatingReadNode>> watchListMap = null;
                for (Block b : cfg.postOrder()) {
                    List<Node> blockToNodes = earliestBlockToNodesMap.get(b);
                    LocationSet killed = null;
                    int previousIndex = blockToNodes.size();
                    for (int i = blockToNodes.size() - 1; i >= 0; --i) {
                        Node currentNode = blockToNodes.get(i);
                        assert currentNodeMap.get(currentNode) == b;
                        assert !(currentNode instanceof PhiNode) && !(currentNode instanceof ProxyNode);
                        assert visited.isMarked(currentNode);
                        if (currentNode instanceof FixedNode) {
                            // For these nodes, the earliest is at the same time the latest block.
                        } else {
                            Block currentBlock = b;
                            assert currentBlock != null;
                            Block latestBlock = calcLatestBlock(b, isOutOfLoops, currentNode, currentNodeMap);
                            assert AbstractControlFlowGraph.dominates(currentBlock, latestBlock) || currentNode instanceof VirtualState : currentNode + " " + currentBlock + " " + latestBlock;
                            if (latestBlock != currentBlock) {
                                if (currentNode instanceof FloatingReadNode) {

                                    FloatingReadNode floatingReadNode = (FloatingReadNode) currentNode;
                                    LocationIdentity location = floatingReadNode.getLocationIdentity();
                                    if (location.isMutable()) {
                                        if (currentBlock.canKill(location)) {
                                            if (killed == null) {
                                                killed = new LocationSet();
                                            }
                                            fillKillSet(killed, blockToNodes.subList(i + 1, previousIndex));
                                            previousIndex = i;
                                            if (killed.contains(location)) {
                                                latestBlock = currentBlock;
                                            }
                                        }

                                        if (latestBlock != currentBlock) {
                                            // We are not constraint within currentBlock. Check if
                                            // we are contraint while walking down the dominator
                                            // line.
                                            Block newLatestBlock = adjustLatestForRead(currentBlock, latestBlock, location);
                                            assert dominates(newLatestBlock, latestBlock);
                                            assert dominates(currentBlock, newLatestBlock);
                                            latestBlock = newLatestBlock;

                                            if (newLatestBlock != currentBlock && latestBlock.canKill(location)) {
                                                if (watchListMap == null) {
                                                    watchListMap = new BlockMap<>(cfg);
                                                }
                                                if (watchListMap.get(latestBlock) == null) {
                                                    watchListMap.put(latestBlock, new ArrayList<>());
                                                }
                                                watchListMap.get(latestBlock).add(floatingReadNode);
                                            }
                                        }
                                    }
                                }
                                currentNodeMap.set(currentNode, latestBlock);
                                currentBlock = latestBlock;
                            }
                            latestBlockToNodesMap.get(currentBlock).add(currentNode);
                        }
                    }
                }

                sortNodesLatestWithinBlock(cfg, earliestBlockToNodesMap, latestBlockToNodesMap, currentNodeMap, watchListMap, visited);

                this.blockToNodesMap = latestBlockToNodesMap;
                this.nodeToBlockMap = currentNodeMap;

                assert verifySchedule(cfg, latestBlockToNodesMap, currentNodeMap);
                cfg.setNodeToBlock(currentNodeMap);
            }
        } else {

            earliestCache = graph.createNodeMap();
            blockToNodesMap = new BlockMap<>(cfg);

            if (selectedStrategy != SchedulingStrategy.EARLIEST && graph.isAfterFloatingReadPhase()) {
                blockToKillSet = new BlockMap<>(cfg);
            }

            assignBlockToNodes(graph, selectedStrategy);
            printSchedule("after assign nodes to blocks");

            sortNodesWithinBlocks(graph, selectedStrategy);
            printSchedule("after sorting nodes within blocks");
        }
    }

    private static boolean verifySchedule(ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodesMap, NodeMap<Block> nodeMap) {
        for (Block b : cfg.getBlocks()) {
            List<Node> nodes = blockToNodesMap.get(b);
            for (Node n : nodes) {
                assert n.isAlive();
                assert nodeMap.get(n) == b;
            }
        }
        return true;
    }

    private static Block adjustLatestForRead(Block earliestBlock, Block latestBlock, LocationIdentity location) {
        assert strictlyDominates(earliestBlock, latestBlock);
        Block current = latestBlock.getDominator();

        // Collect dominator chain that needs checking.
        List<Block> dominatorChain = new ArrayList<>();
        dominatorChain.add(latestBlock);
        while (current != earliestBlock) {
            // Current is an intermediate dominator between earliestBlock and latestBlock.
            assert strictlyDominates(earliestBlock, current) && strictlyDominates(current, latestBlock);
            if (current.canKill(location)) {
                dominatorChain.clear();
            }
            dominatorChain.add(current);
            current = current.getDominator();
        }

        // The first element of dominatorChain now contains the latest possible block.
        assert dominatorChain.size() >= 1;
        assert dominatorChain.get(dominatorChain.size() - 1).getDominator() == earliestBlock;

        Block lastBlock = earliestBlock;
        for (int i = dominatorChain.size() - 1; i >= 0; --i) {
            Block currentBlock = dominatorChain.get(i);
            if (lastBlock.getLoop() != currentBlock.getLoop()) {
                // We are crossing a loop boundary. Both loops must not kill the location for the
                // crossing to be safe.
                if (lastBlock.getLoop() != null && ((HIRLoop) lastBlock.getLoop()).canKill(location)) {
                    break;
                }
                if (currentBlock.getLoop() != null && ((HIRLoop) currentBlock.getLoop()).canKill(location)) {
                    break;
                }
            }

            if (currentBlock.canKillBetweenThisAndDominator(location)) {
                break;
            }
            lastBlock = currentBlock;
        }

        return lastBlock;
    }

    private static void fillKillSet(LocationSet killed, List<Node> subList) {
        if (!killed.isKillAll()) {
            for (Node n : subList) {
                // Check if this node kills a node in the watch list.
                if (n instanceof MemoryCheckpoint.Single) {
                    LocationIdentity identity = ((MemoryCheckpoint.Single) n).getLocationIdentity();
                    killed.add(identity);
                    if (killed.isKillAll()) {
                        return;
                    }
                } else if (n instanceof MemoryCheckpoint.Multi) {
                    for (LocationIdentity identity : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                        killed.add(identity);
                        if (killed.isKillAll()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void sortNodesLatestWithinBlock(ControlFlowGraph cfg, BlockMap<List<Node>> earliestBlockToNodesMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeMap<Block> currentNodeMap,
                    BlockMap<ArrayList<FloatingReadNode>> watchListMap, NodeBitMap visited) {
        for (Block b : cfg.getBlocks()) {
            sortNodesLatestWithinBlock(b, earliestBlockToNodesMap, latestBlockToNodesMap, currentNodeMap, watchListMap, visited);
        }
    }

    private static void sortNodesLatestWithinBlock(Block b, BlockMap<List<Node>> earliestBlockToNodesMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeMap<Block> nodeMap,
                    BlockMap<ArrayList<FloatingReadNode>> watchListMap, NodeBitMap unprocessed) {
        ArrayList<Node> result = new ArrayList<>();
        ArrayList<FloatingReadNode> watchList = null;
        if (watchListMap != null) {
            watchList = watchListMap.get(b);
            assert watchList == null || !b.getKillLocations().isKillNone();
        }
        AbstractBeginNode beginNode = b.getBeginNode();
        if (beginNode instanceof LoopExitNode) {
            LoopExitNode loopExitNode = (LoopExitNode) beginNode;
            for (ProxyNode proxy : loopExitNode.proxies()) {
                unprocessed.clear(proxy);
                ValueNode value = proxy.value();
                if (nodeMap.get(value) == b) {
                    sortIntoList(value, b, result, nodeMap, unprocessed, null);
                }
            }
        }
        FixedNode endNode = b.getEndNode();
        for (Node n : earliestBlockToNodesMap.get(b)) {
            if (n != endNode) {
                if (n instanceof FixedNode) {
                    assert nodeMap.get(n) == b;
                    checkWatchList(b, nodeMap, unprocessed, result, watchList, n);
                    sortIntoList(n, b, result, nodeMap, unprocessed, null);
                } else if (nodeMap.get(n) == b && n instanceof FloatingReadNode) {
                    FloatingReadNode floatingReadNode = (FloatingReadNode) n;
                    LocationIdentity location = floatingReadNode.getLocationIdentity();
                    if (b.canKill(location)) {
                        // This read can be killed in this block, add to watch list.
                        if (watchList == null) {
                            watchList = new ArrayList<>();
                        }
                        watchList.add(floatingReadNode);
                    }
                }
            }
        }

        for (Node n : latestBlockToNodesMap.get(b)) {
            assert nodeMap.get(n) == b;
            assert !(n instanceof FixedNode);
            if (unprocessed.isMarked(n)) {
                sortIntoList(n, b, result, nodeMap, unprocessed, endNode);
            }
        }

        if (unprocessed.isMarked(endNode)) {
            sortIntoList(endNode, b, result, nodeMap, unprocessed, null);
        }

        latestBlockToNodesMap.put(b, result);
    }

    private static void checkWatchList(Block b, NodeMap<Block> nodeMap, NodeBitMap unprocessed, ArrayList<Node> result, ArrayList<FloatingReadNode> watchList, Node n) {
        if (watchList != null && !watchList.isEmpty()) {
            // Check if this node kills a node in the watch list.
            if (n instanceof MemoryCheckpoint.Single) {
                LocationIdentity identity = ((MemoryCheckpoint.Single) n).getLocationIdentity();
                checkWatchList(watchList, identity, b, result, nodeMap, unprocessed);
            } else if (n instanceof MemoryCheckpoint.Multi) {
                for (LocationIdentity identity : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                    checkWatchList(watchList, identity, b, result, nodeMap, unprocessed);
                }
            }
        }
    }

    private static void checkWatchList(ArrayList<FloatingReadNode> watchList, LocationIdentity identity, Block b, ArrayList<Node> result, NodeMap<Block> nodeMap, NodeBitMap unprocessed) {
        assert identity.isMutable();
        if (LocationIdentity.ANY_LOCATION.equals(identity)) {
            for (FloatingReadNode r : watchList) {
                if (unprocessed.isMarked(r)) {
                    sortIntoList(r, b, result, nodeMap, unprocessed, null);
                }
            }
            watchList.clear();
        } else {
            int index = 0;
            while (index < watchList.size()) {
                FloatingReadNode r = watchList.get(index);
                LocationIdentity locationIdentity = r.getLocationIdentity();
                assert !LocationIdentity.FINAL_LOCATION.equals(locationIdentity);
                if (unprocessed.isMarked(r)) {
                    if (LocationIdentity.ANY_LOCATION.equals(identity) || LocationIdentity.ANY_LOCATION.equals(locationIdentity) || identity.equals(locationIdentity)) {
                        sortIntoList(r, b, result, nodeMap, unprocessed, null);
                    } else {
                        ++index;
                        continue;
                    }
                }
                int lastIndex = watchList.size() - 1;
                watchList.set(index, watchList.get(lastIndex));
                watchList.remove(lastIndex);
            }
        }
    }

    private static void sortIntoList(Node n, Block b, ArrayList<Node> result, NodeMap<Block> nodeMap, NodeBitMap unprocessed, Node excludeNode) {
        assert unprocessed.isMarked(n) : n;
        unprocessed.clear(n);

        assert nodeMap.get(n) == b;

        if (n instanceof PhiNode) {
            return;
        }

        for (Node input : n.inputs()) {
            if (nodeMap.get(input) == b && unprocessed.isMarked(input) && input != excludeNode) {
                sortIntoList(input, b, result, nodeMap, unprocessed, excludeNode);
            }
        }

        if (n instanceof ProxyNode) {
            // Skip proxy nodes.
        } else {
            result.add(n);
        }

    }

    private static Block calcLatestBlock(Block earliestBlock, boolean scheduleOutOfLoops, Node currentNode, NodeMap<Block> currentNodeMap) {
        Block block = null;
        assert currentNode.hasUsages();
        for (Node usage : currentNode.usages()) {
            block = calcBlockForUsage(currentNode, usage, block, currentNodeMap);
            if (scheduleOutOfLoops) {
                while (block.getLoopDepth() > earliestBlock.getLoopDepth()) {
                    block = block.getDominator();
                }
            }
            if (block == earliestBlock) {
                // No need to search further. The earliest block *must* be a valid schedule block.
                break;
            }
        }
        assert block != null : currentNode;
        return block;
    }

    private static Block calcBlockForUsage(Node node, Node usage, Block startCurrentBlock, NodeMap<Block> currentNodeMap) {
        assert !(node instanceof PhiNode);
        Block currentBlock = startCurrentBlock;
        if (usage instanceof PhiNode) {
            // An input to a PhiNode is used at the end of the predecessor block that corresponds to
            // the PhiNode input. One PhiNode can use an input multiple times.
            PhiNode phi = (PhiNode) usage;
            AbstractMergeNode merge = phi.merge();
            Block mergeBlock = currentNodeMap.get(merge);
            for (int i = 0; i < phi.valueCount(); ++i) {
                if (phi.valueAt(i) == node) {
                    currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, mergeBlock.getPredecessors().get(i));
                }
            }
        } else if (usage instanceof AbstractBeginNode) {
            AbstractBeginNode abstractBeginNode = (AbstractBeginNode) usage;
            if (abstractBeginNode instanceof StartNode) {
                currentBlock = currentNodeMap.get(abstractBeginNode);
            } else {
                currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, currentNodeMap.get(abstractBeginNode).getDominator());
            }
        } else {
            // All other types of usages: Put the input into the same block as the usage.
            currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, currentNodeMap.get(usage));
        }
        return currentBlock;
    }

    private static void scheduleEarliestIterative(ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodes, NodeMap<Block> nodeToBlock, NodeBitMap visited, StructuredGraph graph) {

        BlockMap<Boolean> floatingReads = new BlockMap<>(cfg);

        // Add begin nodes as the first entry and set the block for phi nodes.
        for (Block b : cfg.getBlocks()) {
            AbstractBeginNode beginNode = b.getBeginNode();
            ArrayList<Node> nodes = new ArrayList<>();
            nodeToBlock.set(beginNode, b);
            nodes.add(beginNode);
            blockToNodes.put(b, nodes);

            if (beginNode instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) beginNode;
                for (PhiNode phi : mergeNode.phis()) {
                    nodeToBlock.set(phi, b);
                }
            } else if (beginNode instanceof LoopExitNode) {
                LoopExitNode loopExitNode = (LoopExitNode) beginNode;
                for (ProxyNode proxy : loopExitNode.proxies()) {
                    nodeToBlock.set(proxy, b);
                }
            }
        }

        Stack<Node> stack = new Stack<>();

        // Start analysis with control flow ends.
        for (Block b : cfg.postOrder()) {
            FixedNode endNode = b.getEndNode();
            stack.push(endNode);
            nodeToBlock.set(endNode, b);
        }

        processStack(cfg, blockToNodes, nodeToBlock, visited, floatingReads, stack);

        // Visit back input edges of loop phis.
        boolean changed;
        boolean unmarkedPhi;
        do {
            changed = false;
            unmarkedPhi = false;
            for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
                for (PhiNode phi : loopBegin.phis()) {
                    if (visited.isMarked(phi)) {
                        for (int i = 0; i < loopBegin.getLoopEndCount(); ++i) {
                            Node node = phi.valueAt(i + loopBegin.forwardEndCount());
                            if (node != null && !visited.isMarked(node)) {
                                changed = true;
                                stack.push(node);
                                processStack(cfg, blockToNodes, nodeToBlock, visited, floatingReads, stack);
                            }
                        }
                    } else {
                        unmarkedPhi = true;
                    }
                }
            }

            /*
             * the processing of one loop phi could have marked a previously checked loop phi,
             * therefore this needs to be iterative.
             */
        } while (unmarkedPhi && changed);

        // Check for dead nodes.
        if (visited.getCounter() < graph.getNodeCount()) {
            for (Node n : graph.getNodes()) {
                if (!visited.isMarked(n)) {
                    n.clearInputs();
                    n.markDeleted();
                }
            }
        }

        // Add end nodes as the last nodes in each block.
        for (Block b : cfg.getBlocks()) {
            FixedNode endNode = b.getEndNode();
            if (endNode != b.getBeginNode()) {
                addNode(blockToNodes, b, endNode);
            }
        }

        for (Block b : cfg.getBlocks()) {
            if (floatingReads.get(b) == Boolean.TRUE) {
                resortEarliestWithinBlock(b, blockToNodes, nodeToBlock, visited);
            }
        }
    }

    private static void resortEarliestWithinBlock(Block b, BlockMap<List<Node>> blockToNodes, NodeMap<Block> nodeToBlock, NodeBitMap unprocessed) {
        ArrayList<FloatingReadNode> watchList = new ArrayList<>();
        List<Node> oldList = blockToNodes.get(b);
        AbstractBeginNode beginNode = b.getBeginNode();
        for (Node n : oldList) {
            if (n instanceof FloatingReadNode) {
                FloatingReadNode floatingReadNode = (FloatingReadNode) n;
                LocationIdentity locationIdentity = floatingReadNode.getLocationIdentity();
                if (locationIdentity.isMutable()) {
                    ValueNode lastAccessLocation = floatingReadNode.getLastLocationAccess().asNode();
                    if (nodeToBlock.get(lastAccessLocation) == b && lastAccessLocation != beginNode) {
                        // This node's last access location is within this block. Add to watch list
                        // when processing the last access location.
                    } else {
                        watchList.add(floatingReadNode);
                    }
                }
            }
        }

        ArrayList<Node> newList = new ArrayList<>();
        assert oldList.get(0) == beginNode;
        unprocessed.clear(beginNode);
        newList.add(beginNode);
        for (int i = 1; i < oldList.size(); ++i) {
            Node n = oldList.get(i);
            if (unprocessed.isMarked(n)) {
                if (n instanceof MemoryCheckpoint) {
                    assert n instanceof FixedNode;
                    if (watchList.size() > 0) {
                        // Check whether we need to commit reads from the watch list.
                        checkWatchList(b, nodeToBlock, unprocessed, newList, watchList, n);
                    }

                    // Add potential dependent reads to the watch list.
                    for (Node usage : n.usages()) {
                        if (usage instanceof FloatingReadNode) {
                            FloatingReadNode floatingReadNode = (FloatingReadNode) usage;
                            if (nodeToBlock.get(floatingReadNode) == b && floatingReadNode.getLastLocationAccess() == n) {
                                watchList.add(floatingReadNode);
                            }
                        }
                    }
                }
                assert unprocessed.isMarked(n);
                unprocessed.clear(n);
                newList.add(n);
            } else {
                // This node was pulled up.
                assert !(n instanceof FixedNode);
            }
        }

        for (Node n : newList) {
            unprocessed.mark(n);
        }

        assert newList.size() == oldList.size();
        blockToNodes.put(b, newList);
    }

    private static void addNode(BlockMap<List<Node>> blockToNodes, Block b, Node endNode) {
        assert !blockToNodes.get(b).contains(endNode) : endNode;
        blockToNodes.get(b).add(endNode);
    }

    private static void processStack(ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodes, NodeMap<Block> nodeToBlock, NodeBitMap visited, BlockMap<Boolean> floatingReads, Stack<Node> stack) {
        Block startBlock = cfg.getStartBlock();
        while (!stack.isEmpty()) {
            Node current = stack.peek();
            if (visited.checkAndMarkInc(current)) {

                // Push inputs and predecessor.
                Node predecessor = current.predecessor();
                if (predecessor != null) {
                    stack.push(predecessor);
                }

                if (current instanceof PhiNode) {
                    PhiNode phiNode = (PhiNode) current;
                    AbstractMergeNode merge = phiNode.merge();
                    for (int i = 0; i < merge.forwardEndCount(); ++i) {
                        Node input = phiNode.valueAt(i);
                        if (input != null) {
                            stack.push(input);
                        }
                    }
                } else {
                    for (Node input : current.inputs()) {
                        if (current instanceof FrameState && input instanceof StateSplit && ((StateSplit) input).stateAfter() == current) {
                            // Ignore the cycle.
                        } else {
                            stack.push(input);
                        }
                    }
                }
            } else {

                stack.pop();

                if (nodeToBlock.get(current) == null) {
                    Node predecessor = current.predecessor();
                    Block curBlock;
                    if (predecessor != null) {
                        // Predecessor determines block.
                        curBlock = nodeToBlock.get(predecessor);
                    } else {
                        Block earliest = startBlock;
                        for (Node input : current.inputs()) {
                            if (current instanceof FrameState && input instanceof StateSplit && ((StateSplit) input).stateAfter() == current) {
                                // ignore
                            } else {
                                Block inputEarliest;
                                if (input instanceof ControlSplitNode) {
                                    inputEarliest = nodeToBlock.get(((ControlSplitNode) input).getPrimarySuccessor());
                                } else {
                                    inputEarliest = nodeToBlock.get(input);
                                }
                                assert inputEarliest != null : current + " / " + input;
                                if (earliest.getDominatorDepth() < inputEarliest.getDominatorDepth()) {
                                    earliest = inputEarliest;
                                }
                            }
                        }
                        curBlock = earliest;
                    }
                    assert curBlock != null;
                    addNode(blockToNodes, curBlock, current);
                    nodeToBlock.set(current, curBlock);
                    if (current instanceof FloatingReadNode) {
                        FloatingReadNode floatingReadNode = (FloatingReadNode) current;
                        if (curBlock.canKill(floatingReadNode.getLocationIdentity())) {
                            floatingReads.put(curBlock, Boolean.TRUE);
                        }
                    }
                }
            }
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
            printScheduleHelper(desc);
        }
    }

    private void printScheduleHelper(String desc) {
        Formatter buf = new Formatter();
        buf.format("=== %s / %s / %s ===%n", getCFG().getStartBlock().getBeginNode().graph(), selectedStrategy, desc);
        for (Block b : getCFG().getBlocks()) {
            buf.format("==== b: %s (loopDepth: %s). ", b, b.getLoopDepth());
            buf.format("dom: %s. ", b.getDominator());
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
    public BlockMap<List<Node>> getBlockToNodesMap() {
        return blockToNodesMap;
    }

    public NodeMap<Block> getNodeToBlockMap() {
        return this.nodeToBlockMap;
    }

    /**
     * Gets the nodes in a given block.
     */
    public List<Node> nodesFor(Block block) {
        return blockToNodesMap.get(block);
    }

    private void assignBlockToNodes(StructuredGraph graph, SchedulingStrategy strategy) {
        for (Block block : cfg.getBlocks()) {
            List<Node> nodes = new ArrayList<>();
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
        List<Node> list = blockToNodesMap.get(b);
        Set<Node> hashset = Node.newSet(list);
        return list.size() == hashset.size();
    }

    private void sortNodesWithinBlock(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation, SchedulingStrategy strategy) {
        if (visited.isMarked(b.getBeginNode()) || cfg.blockFor(b.getBeginNode()) != b) {
            throw new SchedulingError();
        }
        if (visited.isMarked(b.getEndNode()) || cfg.blockFor(b.getEndNode()) != b) {
            throw new SchedulingError();
        }

        List<Node> sortedInstructions;
        assert strategy == SchedulingStrategy.LATEST || strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS;
        sortedInstructions = sortNodesWithinBlockLatest(b, visited, beforeLastLocation);
        assert filterSchedulableNodes(blockToNodesMap.get(b)).size() == removeProxies(sortedInstructions).size() : "sorted block does not contain the same amount of nodes: " +
                        filterSchedulableNodes(blockToNodesMap.get(b)) + " vs. " + removeProxies(sortedInstructions);
        assert sameOrderForFixedNodes(blockToNodesMap.get(b), sortedInstructions) : "fixed nodes in sorted block are not in the same order";
        blockToNodesMap.put(b, sortedInstructions);
    }

    private static List<Node> removeProxies(List<Node> list) {
        List<Node> result = new ArrayList<>();
        for (Node n : list) {
            if (!(n instanceof ProxyNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static List<Node> filterSchedulableNodes(List<Node> list) {
        List<Node> result = new ArrayList<>();
        for (Node n : list) {
            if (!(n instanceof PhiNode)) {
                result.add(n);
            }
        }
        return result;
    }

    private static boolean sameOrderForFixedNodes(List<Node> fixed, List<Node> sorted) {
        Iterator<Node> fixedIterator = fixed.iterator();
        Iterator<Node> sortedIterator = sorted.iterator();

        while (sortedIterator.hasNext()) {
            Node sortedCurrent = sortedIterator.next();
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
        private List<Node> sortedInstructions;
        private List<FloatingReadNode> reads;

        SortState(Block block, NodeBitMap visited, NodeBitMap beforeLastLocation, List<Node> sortedInstructions) {
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

        void removeRead(Node i) {
            assert reads != null;
            reads.remove(i);
        }

        List<FloatingReadNode> readsSnapshot() {
            assert reads != null;
            return new ArrayList<>(reads);
        }

        List<Node> getSortedInstructions() {
            return sortedInstructions;
        }

        boolean containsInstruction(Node i) {
            return sortedInstructions.contains(i);
        }

        void addInstruction(Node i) {
            sortedInstructions.add(i);
        }
    }

    /**
     * Sorts the nodes within a block by adding the nodes to a list in a post-order iteration over
     * all inputs. This means that a node is added to the list after all its inputs have been
     * processed.
     */
    private List<Node> sortNodesWithinBlockLatest(Block b, NodeBitMap visited, NodeBitMap beforeLastLocation) {
        SortState state = new SortState(b, visited, beforeLastLocation, new ArrayList<>(blockToNodesMap.get(b).size() + 2));
        List<Node> instructions = blockToNodesMap.get(b);

        for (Node i : instructions) {
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

        for (Node i : instructions) {
            addToLatestSorting(i, state);
        }
        assert state.readsSize() == 0 : "not all reads are scheduled";

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off
        // it).
        List<Node> sortedInstructions = state.getSortedInstructions();
        Node lastSorted = sortedInstructions.get(sortedInstructions.size() - 1);
        if (lastSorted != b.getEndNode()) {
            sortedInstructions.remove(b.getEndNode());
            sortedInstructions.add(b.getEndNode());
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
                    addToLatestSorting(input, sortState);
                }
            }
        }
    }

    private void addToLatestSorting(Node i, SortState state) {
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

    private void addToLatestSortingHelper(Node i, SortState state) {
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

        addToLatestSorting(i.predecessor(), state);
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

    private void addInputsToLatestSorting(Node i, SortState state, FrameState stateAfter) {
        for (Node input : i.inputs()) {
            if (input instanceof FrameState) {
                if (input != stateAfter) {
                    addUnscheduledToLatestSorting((FrameState) input, state);
                }
            } else {
                addToLatestSorting(input, state);
            }
        }
    }
}
