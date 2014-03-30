/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.cfg;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public class ControlFlowGraph implements AbstractControlFlowGraph<Block> {

    public final StructuredGraph graph;

    private final NodeMap<Block> nodeToBlock;
    private Block[] reversePostOrder;
    private Loop[] loops;

    public static ControlFlowGraph compute(StructuredGraph graph, boolean connectBlocks, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
        ControlFlowGraph cfg = new ControlFlowGraph(graph);
        cfg.identifyBlocks();

        if (connectBlocks || computeLoops || computeDominators || computePostdominators) {
            cfg.connectBlocks();
        }
        if (computeLoops) {
            cfg.computeLoopInformation();
        }
        if (computeDominators) {
            cfg.computeDominators();
        }
        if (computePostdominators) {
            cfg.computePostdominators();
        }
        // there's not much to verify when connectBlocks == false
        assert !(connectBlocks || computeLoops || computeDominators || computePostdominators) || CFGVerifier.verify(cfg);
        return cfg;
    }

    protected ControlFlowGraph(StructuredGraph graph) {
        this.graph = graph;
        this.nodeToBlock = graph.createNodeMap(true);
    }

    public Block[] getBlocks() {
        return reversePostOrder;
    }

    public Block getStartBlock() {
        return reversePostOrder[0];
    }

    public Iterable<Block> postOrder() {
        return new Iterable<Block>() {

            @Override
            public Iterator<Block> iterator() {
                return new Iterator<Block>() {

                    private int nextIndex = reversePostOrder.length - 1;

                    @Override
                    public boolean hasNext() {
                        return nextIndex >= 0;
                    }

                    @Override
                    public Block next() {
                        return reversePostOrder[nextIndex--];
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    public Block blockFor(Node node) {
        return nodeToBlock.get(node);
    }

    public Loop[] getLoops() {
        return loops;
    }

    public void clearNodeToBlock() {
        nodeToBlock.clear();
        for (Block block : reversePostOrder) {
            identifyBlock(block);
        }
    }

    protected static final int BLOCK_ID_INITIAL = -1;
    protected static final int BLOCK_ID_VISITED = -2;

    private void identifyBlock(Block block) {
        Node cur = block.getBeginNode();
        Node last;

        // assign proxies of a loop exit to this block
        if (cur instanceof AbstractBeginNode) {
            for (Node usage : cur.usages()) {
                if (usage instanceof ProxyNode) {
                    nodeToBlock.set(usage, block);
                }
            }
        }

        do {
            assert !cur.isDeleted();

            assert nodeToBlock.get(cur) == null;
            nodeToBlock.set(cur, block);
            if (cur instanceof MergeNode) {
                for (PhiNode phi : ((MergeNode) cur).phis()) {
                    nodeToBlock.set(phi, block);
                }
            }

            last = cur;
            cur = cur.successors().first();
        } while (cur != null && !(cur instanceof AbstractBeginNode));

        block.endNode = (FixedNode) last;
    }

    private void identifyBlocks() {
        // Find all block headers
        int numBlocks = 0;
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.class)) {
            Block block = new Block(begin);
            numBlocks++;
            identifyBlock(block);
        }

        // Compute postorder.
        ArrayList<Block> postOrder = new ArrayList<>(numBlocks);
        ArrayList<Block> stack = new ArrayList<>();
        stack.add(blockFor(graph.start()));

        do {
            Block block = stack.get(stack.size() - 1);
            if (block.id == BLOCK_ID_INITIAL) {
                // First time we see this block: push all successors.
                for (Node suxNode : block.getEndNode().cfgSuccessors()) {
                    Block suxBlock = blockFor(suxNode);
                    if (suxBlock.id == BLOCK_ID_INITIAL) {
                        stack.add(suxBlock);
                    }
                }
                block.id = BLOCK_ID_VISITED;
            } else if (block.id == BLOCK_ID_VISITED) {
                // Second time we see this block: All successors have been processed, so add block
                // to postorder list.
                stack.remove(stack.size() - 1);
                postOrder.add(block);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } while (!stack.isEmpty());

        // Compute reverse postorder and number blocks.
        assert postOrder.size() <= numBlocks : "some blocks originally created can be unreachable, so actual block list can be shorter";
        numBlocks = postOrder.size();
        reversePostOrder = new Block[numBlocks];
        for (int i = 0; i < numBlocks; i++) {
            Block block = postOrder.get(numBlocks - i - 1);
            block.id = i;
            reversePostOrder[i] = block;
        }
    }

    // Connect blocks (including loop backward edges), but ignoring dead code (blocks with id < 0).
    private void connectBlocks() {
        for (Block block : reversePostOrder) {
            List<Block> predecessors = new ArrayList<>(4);
            for (Node predNode : block.getBeginNode().cfgPredecessors()) {
                Block predBlock = nodeToBlock.get(predNode);
                if (predBlock.id >= 0) {
                    predecessors.add(predBlock);
                }
            }
            if (block.getBeginNode() instanceof LoopBeginNode) {
                for (LoopEndNode predNode : ((LoopBeginNode) block.getBeginNode()).orderedLoopEnds()) {
                    Block predBlock = nodeToBlock.get(predNode);
                    if (predBlock.id >= 0) {
                        predecessors.add(predBlock);
                    }
                }
            }
            block.predecessors = predecessors;

            List<Block> successors = new ArrayList<>(4);
            for (Node suxNode : block.getEndNode().cfgSuccessors()) {
                Block suxBlock = nodeToBlock.get(suxNode);
                assert suxBlock.id >= 0;
                successors.add(suxBlock);
            }
            if (block.getEndNode() instanceof LoopEndNode) {
                Block suxBlock = nodeToBlock.get(((LoopEndNode) block.getEndNode()).loopBegin());
                assert suxBlock.id >= 0;
                successors.add(suxBlock);
            }
            block.successors = successors;
        }
    }

    private void computeLoopInformation() {
        ArrayList<Loop> loopsList = new ArrayList<>();
        for (Block block : reversePostOrder) {
            Node beginNode = block.getBeginNode();
            if (beginNode instanceof LoopBeginNode) {
                Loop loop = new Loop(block.getLoop(), loopsList.size(), block);
                loopsList.add(loop);

                LoopBeginNode loopBegin = (LoopBeginNode) beginNode;
                for (LoopEndNode end : loopBegin.loopEnds()) {
                    Block endBlock = nodeToBlock.get(end);
                    computeLoopBlocks(endBlock, loop);
                }

                for (LoopExitNode exit : loopBegin.loopExits()) {
                    Block exitBlock = nodeToBlock.get(exit);
                    assert exitBlock.getPredecessorCount() == 1;
                    computeLoopBlocks(exitBlock.getFirstPredecessor(), loop);
                    loop.exits.add(exitBlock);
                }
                List<Block> unexpected = new LinkedList<>();
                for (Block b : loop.blocks) {
                    for (Block sux : b.getSuccessors()) {
                        if (sux.loop != loop) {
                            AbstractBeginNode begin = sux.getBeginNode();
                            if (!(begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == loopBegin)) {
                                Debug.log("Unexpected loop exit with %s, including whole branch in the loop", sux);
                                unexpected.add(sux);
                            }
                        }
                    }
                }
                for (Block b : unexpected) {
                    addBranchToLoop(loop, b);
                }
            }
        }
        loops = loopsList.toArray(new Loop[loopsList.size()]);
    }

    private static void addBranchToLoop(Loop l, Block b) {
        if (l.blocks.contains(b)) {
            return;
        }
        l.blocks.add(b);
        b.loop = l;
        for (Block sux : b.getSuccessors()) {
            addBranchToLoop(l, sux);
        }
    }

    private static void computeLoopBlocks(Block block, Loop loop) {
        if (block.getLoop() == loop) {
            return;
        }
        assert block.loop == loop.parent;
        block.loop = loop;

        assert !loop.blocks.contains(block);
        loop.blocks.add(block);

        if (block != loop.header) {
            for (Block pred : block.getPredecessors()) {
                computeLoopBlocks(pred, loop);
            }
        }
    }

    private void computeDominators() {
        assert reversePostOrder[0].getPredecessorCount() == 0 : "start block has no predecessor and therefore no dominator";
        for (int i = 1; i < reversePostOrder.length; i++) {
            Block block = reversePostOrder[i];
            assert block.getPredecessorCount() > 0;
            Block dominator = null;
            for (Block pred : block.getPredecessors()) {
                if (!pred.isLoopEnd()) {
                    dominator = commonDominator(dominator, pred);
                }
            }
            setDominator(block, dominator);
        }
    }

    private static void setDominator(Block block, Block dominator) {
        block.dominator = dominator;
        if (dominator.dominated == null) {
            dominator.dominated = new ArrayList<>();
        }
        dominator.dominated.add(block);
    }

    public static Block commonDominator(Block a, Block b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            if (iterA.getId() > iterB.getId()) {
                iterA = iterA.getDominator();
            } else {
                assert iterB.getId() > iterA.getId();
                iterB = iterB.getDominator();
            }
        }
        return iterA;
    }

    private void computePostdominators() {
        outer: for (Block block : postOrder()) {
            if (block.isLoopEnd()) {
                // We do not want the loop header registered as the postdominator of the loop end.
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                // No successors => no postdominator.
                continue;
            }
            Block firstSucc = block.getSuccessors().get(0);
            if (block.getSuccessorCount() == 1) {
                block.postdominator = firstSucc;
                continue;
            }
            Block postdominator = firstSucc;
            for (Block sux : block.getSuccessors()) {
                postdominator = commonPostdominator(postdominator, sux);
                if (postdominator == null) {
                    // There is a dead end => no postdominator available.
                    continue outer;
                }
            }
            assert !block.getSuccessors().contains(postdominator) : "Block " + block + " has a wrong post dominator: " + postdominator;
            block.postdominator = postdominator;
        }
    }

    private static Block commonPostdominator(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            if (iterA.getId() < iterB.getId()) {
                iterA = iterA.getPostdominator();
                if (iterA == null) {
                    return null;
                }
            } else {
                assert iterB.getId() < iterA.getId();
                iterB = iterB.getPostdominator();
                if (iterB == null) {
                    return null;
                }
            }
        }
        return iterA;
    }
}
