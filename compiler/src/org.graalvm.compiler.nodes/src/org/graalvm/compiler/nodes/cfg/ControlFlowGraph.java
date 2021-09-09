/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.nodes.cfg;

import static org.graalvm.compiler.core.common.cfg.AbstractBlockBase.BLOCK_ID_COMPARATOR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.CFGVerifier;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;

public final class ControlFlowGraph implements AbstractControlFlowGraph<Block> {
    /**
     * Don't allow relative frequency values to be become too small or too high as this makes
     * frequency calculations over- or underflow the range of a double. This commonly happens with
     * infinite loops within infinite loops. The value is chosen a bit lower than half the maximum
     * exponent supported by double. That way we can never overflow to infinity when multiplying two
     * relative frequency values.
     */
    public static final double MIN_RELATIVE_FREQUENCY = 0x1.0p-500;
    public static final double MAX_RELATIVE_FREQUENCY = 1 / MIN_RELATIVE_FREQUENCY;

    public final StructuredGraph graph;

    private NodeMap<Block> nodeToBlock;
    private Block[] reversePostOrder;
    private List<Loop<Block>> loops;
    private int maxDominatorDepth;

    public interface RecursiveVisitor<V> {
        V enter(Block b);

        void exit(Block b, V value);
    }

    public static ControlFlowGraph compute(StructuredGraph graph, boolean connectBlocks, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
        ControlFlowGraph cfg = new ControlFlowGraph(graph);
        cfg.identifyBlocks();
        cfg.computeFrequencies();

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

    public String dominatorTreeString() {
        return dominatorTreeString(getStartBlock());
    }

    private static String dominatorTreeString(Block b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b);
        sb.append("(");
        Block firstDominated = b.getFirstDominated();
        while (firstDominated != null) {
            if (firstDominated.getDominator().getPostdominator() == firstDominated) {
                sb.append("!");
            }
            sb.append(dominatorTreeString(firstDominated));
            firstDominated = firstDominated.getDominatedSibling();
        }
        sb.append(") ");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public <V> void visitDominatorTreeDefault(RecursiveVisitor<V> visitor) {

        Block[] stack = new Block[maxDominatorDepth + 1];
        Block current = getStartBlock();
        int tos = 0;
        Object[] values = null;
        int valuesTOS = 0;

        while (tos >= 0) {
            Block state = stack[tos];
            if (state == null || state.getDominator() == null || state.getDominator().getPostdominator() != state) {
                if (state == null) {
                    // We enter this block for the first time.
                    V value = visitor.enter(current);
                    if (value != null || values != null) {
                        if (values == null) {
                            values = new Object[maxDominatorDepth + 1];
                        }
                        values[valuesTOS++] = value;
                    }

                    Block dominated = skipPostDom(current.getFirstDominated());
                    if (dominated != null) {
                        // Descend into dominated.
                        stack[tos] = dominated;
                        current = dominated;
                        stack[++tos] = null;
                        continue;
                    }
                } else {
                    Block next = skipPostDom(state.getDominatedSibling());
                    if (next != null) {
                        // Descend into dominated.
                        stack[tos] = next;
                        current = next;
                        stack[++tos] = null;
                        continue;
                    }
                }

                // Finished processing all normal dominators.
                Block postDom = current.getPostdominator();
                if (postDom != null && postDom.getDominator() == current) {
                    // Descend into post dominator.
                    stack[tos] = postDom;
                    current = postDom;
                    stack[++tos] = null;
                    continue;
                }
            }

            // Finished processing this node, exit and pop from stack.
            V value = null;
            if (values != null && valuesTOS > 0) {
                value = (V) values[--valuesTOS];
            }
            visitor.exit(current, value);
            current = current.getDominator();
            --tos;
        }
    }

    private static Block skipPostDom(Block block) {
        if (block != null && block.getDominator().getPostdominator() == block) {
            // This is an always reached block.
            return block.getDominatedSibling();
        }
        return block;
    }

    public static final class DeferredExit {

        public DeferredExit(Block block, DeferredExit next) {
            this.block = block;
            this.next = next;
        }

        private final Block block;
        private final DeferredExit next;

        public Block getBlock() {
            return block;
        }

        public DeferredExit getNext() {
            return next;
        }

    }

    public static void addDeferredExit(DeferredExit[] deferredExits, Block b) {
        Loop<Block> outermostExited = b.getDominator().getLoop();
        Loop<Block> exitBlockLoop = b.getLoop();
        assert outermostExited != null : "Dominator must be in a loop. Possible cause is a missing loop exit node.";
        while (outermostExited.getParent() != null && outermostExited.getParent() != exitBlockLoop) {
            outermostExited = outermostExited.getParent();
        }
        int loopIndex = outermostExited.getIndex();
        deferredExits[loopIndex] = new DeferredExit(b, deferredExits[loopIndex]);
    }

    @SuppressWarnings({"unchecked"})
    public <V> void visitDominatorTreeDeferLoopExits(RecursiveVisitor<V> visitor) {
        Block[] stack = new Block[getBlocks().length];
        int tos = 0;
        BitSet visited = new BitSet(getBlocks().length);
        int loopCount = getLoops().size();
        DeferredExit[] deferredExits = new DeferredExit[loopCount];
        Object[] values = null;
        int valuesTOS = 0;
        stack[0] = getStartBlock();

        while (tos >= 0) {
            Block cur = stack[tos];
            int curId = cur.getId();
            if (visited.get(curId)) {
                V value = null;
                if (values != null && valuesTOS > 0) {
                    value = (V) values[--valuesTOS];
                }
                visitor.exit(cur, value);
                --tos;
                if (cur.isLoopHeader()) {
                    int loopIndex = cur.getLoop().getIndex();
                    DeferredExit deferredExit = deferredExits[loopIndex];
                    if (deferredExit != null) {
                        while (deferredExit != null) {
                            stack[++tos] = deferredExit.block;
                            deferredExit = deferredExit.next;
                        }
                        deferredExits[loopIndex] = null;
                    }
                }
            } else {
                visited.set(curId);
                V value = visitor.enter(cur);
                if (value != null || values != null) {
                    if (values == null) {
                        values = new Object[maxDominatorDepth + 1];
                    }
                    values[valuesTOS++] = value;
                }

                Block alwaysReached = cur.getPostdominator();
                if (alwaysReached != null) {
                    if (alwaysReached.getDominator() != cur) {
                        alwaysReached = null;
                    } else if (isDominatorTreeLoopExit(alwaysReached)) {
                        addDeferredExit(deferredExits, alwaysReached);
                    } else {
                        stack[++tos] = alwaysReached;
                    }
                }

                Block b = cur.getFirstDominated();
                while (b != null) {
                    if (b != alwaysReached) {
                        if (isDominatorTreeLoopExit(b)) {
                            addDeferredExit(deferredExits, b);
                        } else {
                            stack[++tos] = b;
                        }
                    }
                    b = b.getDominatedSibling();
                }
            }
        }
    }

    public <V> void visitDominatorTree(RecursiveVisitor<V> visitor, boolean deferLoopExits) {
        if (deferLoopExits && this.getLoops().size() > 0) {
            visitDominatorTreeDeferLoopExits(visitor);
        } else {
            visitDominatorTreeDefault(visitor);
        }
    }

    public static boolean isDominatorTreeLoopExit(Block b) {
        Block dominator = b.getDominator();
        return dominator != null && b.getLoop() != dominator.getLoop() && (!b.isLoopHeader() || dominator.getLoopDepth() >= b.getLoopDepth());
    }

    private ControlFlowGraph(StructuredGraph graph) {
        this.graph = graph;
        this.nodeToBlock = graph.createNodeMap();
    }

    private void computeDominators() {
        assert reversePostOrder[0].getPredecessorCount() == 0 : "start block has no predecessor and therefore no dominator";
        Block[] blocks = reversePostOrder;
        int curMaxDominatorDepth = 0;
        for (int i = 1; i < blocks.length; i++) {
            Block block = blocks[i];
            assert block.getPredecessorCount() > 0;
            Block dominator = null;
            for (Block pred : block.getPredecessors()) {
                if (!pred.isLoopEnd()) {
                    dominator = ((dominator == null) ? pred : commonDominatorRaw(dominator, pred));
                }
            }
            // Fortify: Suppress Null Dereference false positive (every block apart from the first
            // is guaranteed to have a predecessor)
            assert dominator != null;

            // Set dominator.
            block.setDominator(dominator);

            // Keep dominated linked list sorted by block ID such that predecessor blocks are always
            // before successor blocks.
            Block currentDominated = dominator.getFirstDominated();
            if (currentDominated != null && currentDominated.getId() < block.getId()) {
                while (currentDominated.getDominatedSibling() != null && currentDominated.getDominatedSibling().getId() < block.getId()) {
                    currentDominated = currentDominated.getDominatedSibling();
                }
                block.setDominatedSibling(currentDominated.getDominatedSibling());
                currentDominated.setDominatedSibling(block);
            } else {
                block.setDominatedSibling(dominator.getFirstDominated());
                dominator.setFirstDominated(block);
            }

            curMaxDominatorDepth = Math.max(curMaxDominatorDepth, block.getDominatorDepth());
        }
        this.maxDominatorDepth = curMaxDominatorDepth;
        calcDominatorRanges(getStartBlock(), reversePostOrder.length);
    }

    private static void calcDominatorRanges(Block block, int size) {
        Block[] stack = new Block[size];
        stack[0] = block;
        int tos = 0;
        int myNumber = 0;

        do {
            Block cur = stack[tos];
            Block dominated = cur.getFirstDominated();

            if (cur.getDominatorNumber() == -1) {
                cur.setDominatorNumber(myNumber);
                if (dominated != null) {
                    // Push children onto stack.
                    do {
                        stack[++tos] = dominated;
                        dominated = dominated.getDominatedSibling();
                    } while (dominated != null);
                } else {
                    cur.setMaxChildDomNumber(myNumber);
                    --tos;
                }
                ++myNumber;
            } else {
                cur.setMaxChildDomNumber(dominated.getMaxChildDominatorNumber());
                --tos;
            }
        } while (tos >= 0);
    }

    private static Block commonDominatorRaw(Block a, Block b) {
        int aDomDepth = a.getDominatorDepth();
        int bDomDepth = b.getDominatorDepth();
        if (aDomDepth > bDomDepth) {
            return commonDominatorRawSameDepth(a.getDominator(aDomDepth - bDomDepth), b);
        } else {
            return commonDominatorRawSameDepth(a, b.getDominator(bDomDepth - aDomDepth));
        }
    }

    private static Block commonDominatorRawSameDepth(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            iterA = iterA.getDominator();
            iterB = iterB.getDominator();
        }
        return iterA;
    }

    @Override
    public Block[] getBlocks() {
        return reversePostOrder;
    }

    @Override
    public Block getStartBlock() {
        return reversePostOrder[0];
    }

    public Block[] reversePostOrder() {
        return reversePostOrder;
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    public Block blockFor(Node node) {
        return nodeToBlock.get(node);
    }

    public Block commonDominatorFor(NodeIterable<? extends Node> nodes) {
        Block commonDom = null;
        for (Node n : nodes) {
            Block b = blockFor(n);
            commonDom = (Block) AbstractControlFlowGraph.commonDominator(commonDom, b);
        }
        return commonDom;
    }

    @Override
    public List<Loop<Block>> getLoops() {
        return loops;
    }

    public int getMaxDominatorDepth() {
        return maxDominatorDepth;
    }

    private void identifyBlock(Block block) {
        FixedWithNextNode cur = block.getBeginNode();
        while (true) {
            assert cur.isAlive() : cur;
            assert nodeToBlock.get(cur) == null;
            nodeToBlock.set(cur, block);
            FixedNode next = cur.next();
            assert next != null : cur;
            if (next instanceof AbstractBeginNode) {
                block.endNode = cur;
                return;
            } else if (next instanceof FixedWithNextNode) {
                cur = (FixedWithNextNode) next;
            } else {
                nodeToBlock.set(next, block);
                block.endNode = next;
                return;
            }
        }
    }

    /**
     * Identify and connect blocks (including loop backward edges). Predecessors need to be in the
     * order expected when iterating phi inputs.
     */
    private void identifyBlocks() {
        // Find all block headers.
        int numBlocks = 0;
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            Block block = new Block(begin);
            identifyBlock(block);
            numBlocks++;
        }

        // Compute reverse post order.
        int count = 0;
        NodeMap<Block> nodeMap = this.nodeToBlock;
        Block[] stack = new Block[numBlocks];
        int tos = 0;
        Block startBlock = blockFor(graph.start());
        stack[0] = startBlock;
        startBlock.setPredecessors(Block.EMPTY_ARRAY);
        do {
            Block block = stack[tos];
            int id = block.getId();
            if (id == BLOCK_ID_INITIAL) {
                // First time we see this block: push all successors.
                FixedNode last = block.getEndNode();
                if (last instanceof EndNode) {
                    EndNode endNode = (EndNode) last;
                    Block suxBlock = nodeMap.get(endNode.merge());
                    if (suxBlock.getId() == BLOCK_ID_INITIAL) {
                        stack[++tos] = suxBlock;
                    }
                    block.setSuccessors(new Block[]{suxBlock});
                } else if (last instanceof IfNode) {
                    IfNode ifNode = (IfNode) last;
                    Block trueSucc = nodeMap.get(ifNode.trueSuccessor());
                    stack[++tos] = trueSucc;
                    Block falseSucc = nodeMap.get(ifNode.falseSuccessor());
                    stack[++tos] = falseSucc;
                    block.setSuccessors(new Block[]{trueSucc, falseSucc});
                    Block[] ifPred = new Block[]{block};
                    trueSucc.setPredecessors(ifPred);
                    falseSucc.setPredecessors(ifPred);
                } else if (last instanceof LoopEndNode) {
                    LoopEndNode loopEndNode = (LoopEndNode) last;
                    block.setSuccessors(new Block[]{nodeMap.get(loopEndNode.loopBegin())});
                    // Nothing to do push onto the stack.
                } else if (last instanceof ControlSinkNode) {
                    block.setSuccessors(Block.EMPTY_ARRAY);
                } else {
                    assert !(last instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                    int startTos = tos;
                    Block[] ifPred = new Block[]{block};
                    for (Node suxNode : last.successors()) {
                        Block sux = nodeMap.get(suxNode);
                        stack[++tos] = sux;
                        sux.setPredecessors(ifPred);
                    }
                    int suxCount = tos - startTos;
                    Block[] successors = new Block[suxCount];
                    System.arraycopy(stack, startTos + 1, successors, 0, suxCount);
                    block.setSuccessors(successors);
                }
                block.setId(BLOCK_ID_VISITED);
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    computeLoopPredecessors(nodeMap, block, (LoopBeginNode) beginNode);
                } else if (beginNode instanceof AbstractMergeNode) {
                    AbstractMergeNode mergeNode = (AbstractMergeNode) beginNode;
                    int forwardEndCount = mergeNode.forwardEndCount();
                    Block[] predecessors = new Block[forwardEndCount];
                    for (int i = 0; i < forwardEndCount; ++i) {
                        predecessors[i] = nodeMap.get(mergeNode.forwardEndAt(i));
                    }
                    block.setPredecessors(predecessors);
                }

            } else if (id == BLOCK_ID_VISITED) {
                // Second time we see this block: All successors have been processed, so add block
                // to result list. Can safely reuse the stack for this.
                --tos;
                count++;
                int index = numBlocks - count;
                stack[index] = block;
                block.setId(index);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } while (tos >= 0);

        // Compute reverse postorder and number blocks.
        assert count == numBlocks : "all blocks must be reachable";
        this.reversePostOrder = stack;
    }

    private static void computeLoopPredecessors(NodeMap<Block> nodeMap, Block block, LoopBeginNode loopBeginNode) {
        int forwardEndCount = loopBeginNode.forwardEndCount();
        LoopEndNode[] loopEnds = loopBeginNode.orderedLoopEnds();
        Block[] predecessors = new Block[forwardEndCount + loopEnds.length];
        for (int i = 0; i < forwardEndCount; ++i) {
            predecessors[i] = nodeMap.get(loopBeginNode.forwardEndAt(i));
        }
        for (int i = 0; i < loopEnds.length; ++i) {
            predecessors[i + forwardEndCount] = nodeMap.get(loopEnds[i]);
        }
        block.setPredecessors(predecessors);
    }

    /**
     * Computes the frequencies of all blocks relative to the start block. It uses the probability
     * information attached to control flow splits to calculate the frequency of a block based on
     * the frequency of its predecessor and the probability of its incoming control flow branch.
     */
    private void computeFrequencies() {

        for (Block block : reversePostOrder) {
            Block[] predecessors = block.getPredecessors();

            double relativeFrequency;
            if (predecessors.length == 0) {
                relativeFrequency = 1D;
            } else if (predecessors.length == 1) {
                Block pred = predecessors[0];
                relativeFrequency = pred.relativeFrequency;
                if (pred.getSuccessorCount() > 1) {
                    assert pred.getEndNode() instanceof ControlSplitNode;
                    ControlSplitNode controlSplit = (ControlSplitNode) pred.getEndNode();
                    relativeFrequency = multiplyRelativeFrequencies(relativeFrequency, controlSplit.probability(block.getBeginNode()));
                }
            } else {
                relativeFrequency = predecessors[0].relativeFrequency;
                for (int i = 1; i < predecessors.length; ++i) {
                    relativeFrequency += predecessors[i].relativeFrequency;
                }

                if (block.getBeginNode() instanceof LoopBeginNode) {
                    LoopBeginNode loopBegin = (LoopBeginNode) block.getBeginNode();
                    relativeFrequency = multiplyRelativeFrequencies(relativeFrequency, loopBegin.loopFrequency());
                }
            }
            if (relativeFrequency < MIN_RELATIVE_FREQUENCY) {
                relativeFrequency = MIN_RELATIVE_FREQUENCY;
            } else if (relativeFrequency > MAX_RELATIVE_FREQUENCY) {
                relativeFrequency = MAX_RELATIVE_FREQUENCY;
            }
            block.setRelativeFrequency(relativeFrequency);
        }

    }

    private void computeLoopInformation() {
        loops = new ArrayList<>();
        if (graph.hasLoops()) {
            Block[] stack = new Block[this.reversePostOrder.length];
            for (Block block : reversePostOrder) {
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    Loop<Block> parent = block.getLoop();
                    Loop<Block> loop = new HIRLoop(parent, loops.size(), block);
                    if (((LoopBeginNode) beginNode).isCompilerInverted()) {
                        loop.setInverted(true);
                    }
                    if (parent != null) {
                        parent.getChildren().add(loop);
                    }
                    loops.add(loop);
                    block.setLoop(loop);
                    loop.getBlocks().add(block);

                    LoopBeginNode loopBegin = (LoopBeginNode) beginNode;
                    for (LoopEndNode end : loopBegin.loopEnds()) {
                        Block endBlock = nodeToBlock.get(end);
                        computeLoopBlocks(endBlock, loop, stack, true);
                    }

                    // Note that at this point, due to traversal order, child loops of `loop` have
                    // not been discovered yet.
                    for (Block b : loop.getBlocks()) {
                        for (Block sux : b.getSuccessors()) {
                            if (sux.getLoop() != loop) {
                                assert sux.getLoopDepth() < loop.getDepth();
                                loop.getNaturalExits().add(sux);
                            }
                        }
                    }
                    loop.getNaturalExits().sort(BLOCK_ID_COMPARATOR);

                    if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                        for (LoopExitNode exit : loopBegin.loopExits()) {
                            Block exitBlock = nodeToBlock.get(exit);
                            assert exitBlock.getPredecessorCount() == 1;
                            computeLoopBlocks(exitBlock.getFirstPredecessor(), loop, stack, true);
                            loop.getLoopExits().add(exitBlock);
                        }
                        loop.getLoopExits().sort(BLOCK_ID_COMPARATOR);

                        // The following loop can add new blocks to the end of the loop's block
                        // list.
                        int size = loop.getBlocks().size();
                        for (int i = 0; i < size; ++i) {
                            Block b = loop.getBlocks().get(i);
                            for (Block sux : b.getSuccessors()) {
                                if (sux.getLoop() != loop) {
                                    AbstractBeginNode begin = sux.getBeginNode();
                                    if (!loopBegin.isLoopExit(begin)) {
                                        assert !(begin instanceof LoopBeginNode);
                                        assert sux.getLoopDepth() < loop.getDepth();
                                        graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Unexpected loop exit with %s, including whole branch in the loop", sux);
                                        computeLoopBlocks(sux, loop, stack, false);
                                    }
                                }
                            }
                        }
                    } else {
                        loop.getLoopExits().addAll(loop.getNaturalExits());
                    }
                }
            }
        }
    }

    private static void computeLoopBlocks(Block start, Loop<Block> loop, Block[] stack, boolean usePred) {
        if (start.getLoop() != loop) {
            start.setLoop(loop);
            stack[0] = start;
            loop.getBlocks().add(start);
            int tos = 0;
            do {
                Block block = stack[tos--];

                // Add predecessors or successors to the loop.
                for (Block b : (usePred ? block.getPredecessors() : block.getSuccessors())) {
                    if (b.getLoop() != loop) {
                        stack[++tos] = b;
                        b.setLoop(loop);
                        loop.getBlocks().add(b);
                    }
                }
            } while (tos >= 0);
        }
    }

    public void computePostdominators() {

        Block[] reversePostOrderTmp = this.reversePostOrder;
        outer: for (int j = reversePostOrderTmp.length - 1; j >= 0; --j) {
            Block block = reversePostOrderTmp[j];
            if (block.isLoopEnd()) {
                // We do not want the loop header registered as the postdominator of the loop end.
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                // No successors => no postdominator.
                continue;
            }
            Block firstSucc = block.getSuccessors()[0];
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
            assert !Arrays.asList(block.getSuccessors()).contains(postdominator) : "Block " + block + " has a wrong post dominator: " + postdominator;
            block.setPostDominator(postdominator);
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

    public void setNodeToBlock(NodeMap<Block> nodeMap) {
        this.nodeToBlock = nodeMap;
    }

    /**
     * Multiplies a and b and clamps the between {@link ControlFlowGraph#MIN_RELATIVE_FREQUENCY} and
     * {@link ControlFlowGraph#MAX_RELATIVE_FREQUENCY}.
     */
    public static double multiplyRelativeFrequencies(double a, double b) {
        assert !Double.isNaN(a) && !Double.isNaN(b) && Double.isFinite(a) && Double.isFinite(b) : a + " " + b;
        double r = a * b;
        if (r > MAX_RELATIVE_FREQUENCY) {
            return MAX_RELATIVE_FREQUENCY;
        }
        if (r < MIN_RELATIVE_FREQUENCY) {
            return MIN_RELATIVE_FREQUENCY;
        }
        return r;
    }
}
