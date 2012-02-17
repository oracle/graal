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
import com.oracle.max.graal.lir.cfg.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.virtual.*;

public class SchedulePhase extends Phase {
    private ControlFlowGraph cfg;
    private NodeMap<Block> earliestCache;

    private BlockMap<List<Node>> nodesFor;

    public SchedulePhase() {
        super("Schedule");
    }

    @Override
    protected void run(StructuredGraph graph) {
        cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        earliestCache = graph.createNodeMap();
        nodesFor = new BlockMap<>(cfg);

        assignBlockToNodes(graph);
        sortNodesWithinBlocks(graph);
    }

    public ControlFlowGraph getCFG() {
        return cfg;
    }

    public BlockMap<List<Node>> getNodesFor() {
        return nodesFor;
    }

    public List<Node> nodesFor(Block block) {
        return nodesFor.get(block);
    }

    private void assignBlockToNodes(StructuredGraph graph) {
        for (Block block : cfg.getBlocks()) {
            List<Node> nodes = new ArrayList<>();
            assert nodesFor.get(block) == null;
            nodesFor.put(block, nodes);
            for (Node node : block.getNodes()) {
                nodes.add(node);
            }
        }

        for (Node n : graph.getNodes()) {
            assignBlockToNode(n);
        }
    }

    private void assignBlockToNode(Node n) {
        if (n == null) {
            return;
        }

        assert !n.isDeleted();

        Block prevBlock = cfg.getNodeToBlock().get(n);
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
            assert earliestBlock.dominates(block) : "Graph can not be scheduled : inconsisitant for " + n;
        } else {
            block = latestBlock;
        }
        assert !(n instanceof MergeNode);
        cfg.getNodeToBlock().set(n, block);
        nodesFor.get(block).add(n);
    }

    private Block latestBlock(Node n) {
        Block block = null;
        for (Node succ : n.successors()) {
            if (succ == null) {
                continue;
            }
            assignBlockToNode(succ);
            block = getCommonDominator(block, cfg.getNodeToBlock().get(succ));
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
        public void apply(Block newBlock) {
            this.block = getCommonDominator(this.block, newBlock);
        }
    }

    private Block earliestBlock(Node n) {
        Block earliest = cfg.getNodeToBlock().get(n);
        if (earliest != null) {
            return earliest;
        }
        earliest = earliestCache.get(n);
        if (earliest != null) {
            return earliest;
        }
        BitMap bits = new BitMap(cfg.getBlocks().length);
        ArrayList<Node> before = new ArrayList<>();
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
            if (!bits.get(b.getId())) {
                earliest = b;
                do {
                    bits.set(b.getId());
                    b = b.getDominator();
                } while(b != null && !bits.get(b.getId()));
            }
        }
        if (earliest == null) {
            Block start = cfg.getNodeToBlock().get(((StructuredGraph) n.graph()).start());
            assert start != null;
            return start;
        }
        earliestCache.set(n, earliest);
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

    private void blocksForUsage(Node node, Node usage, BlockClosure closure) {
        if (usage instanceof PhiNode) {
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
        } else if (usage instanceof FrameState && ((FrameState) usage).block() != null) {
            MergeNode merge = ((FrameState) usage).block();
            Block block = null;
            for (Node pred : merge.cfgPredecessors()) {
                block = getCommonDominator(block, cfg.getNodeToBlock().get(pred));
            }
            closure.apply(block);
        } else {
            assignBlockToNode(usage);
            closure.apply(cfg.getNodeToBlock().get(usage));
        }
    }

    private void ensureScheduledUsages(Node node) {
        for (Node usage : node.usages().snapshot()) {
            assignBlockToNode(usage);
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
        NodeBitMap map = graph.createNodeBitMap();
        for (Block b : cfg.getBlocks()) {
            sortNodesWithinBlocks(b, map);
        }
    }

    private void sortNodesWithinBlocks(Block b, NodeBitMap map) {
        List<Node> instructions = nodesFor.get(b);
        List<Node> sortedInstructions = new ArrayList<>(instructions.size() + 2);

        assert !map.isMarked(b.getBeginNode()) && cfg.blockFor(b.getBeginNode()) == b;
        assert !map.isMarked(b.getEndNode()) && cfg.blockFor(b.getEndNode()) == b;

        for (Node i : instructions) {
            addToSorting(b, i, sortedInstructions, map);
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
                // (cwi) this was the assertion commented out below.  However, it is failing frequently when the
                // scheduler is used for debug printing in early compiler phases. This was annoying during debugging
                // when an excpetion breakpoint is set for assertion errors, so I changed it to a bailout.
                if (b.getEndNode() instanceof ControlSplitNode) {
                    throw new GraalInternalError("Schedule is not possible : needs to move a node after the last node of the block whcih can not be move").
                    addContext(lastSorted).
                    addContext(b.getEndNode());
                }
                //assert !(b.lastNode() instanceof ControlSplitNode);

                //b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.getEndNode());
                sortedInstructions.add(b.getEndNode());
            }
        }
        nodesFor.put(b, sortedInstructions);
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || cfg.getNodeToBlock().get(i) != b || i instanceof PhiNode || i instanceof LocalNode) {
            return;
        }

        if (i instanceof WriteNode) {
            // TODO(tw): Make sure every ReadNode that is connected to the same memory state is executed before every write node.
            // WriteNode wn = (WriteNode) i;
        }

        FrameState state = null;
        WriteNode writeNode = null;
        for (Node input : i.inputs()) {
            if (input instanceof WriteNode && !map.isMarked(input) && cfg.getNodeToBlock().get(input) == b) {
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
}
