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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;


public class Block {

    private int blockID;
    protected final List<Block> successors = new ArrayList<Block>();
    protected final List<Block> predecessors = new ArrayList<Block>();
    private final List<Block> dominated = new ArrayList<Block>();
    private List<Node> instructions = new ArrayList<Node>();
    private Block alwaysReachedBlock;
    private Block dominator;
    private FixedNode anchor;
    private EndNode end;
    private int loopDepth = 0;
    private int loopIndex = -1;
    private double probability;

    private Node firstNode;
    private Node lastNode;

    public Node firstNode() {
        return firstNode;
    }

    public void setFirstNode(Node node) {
        this.firstNode = node;
        this.anchor = null;
    }

    public Block alwaysReachedBlock() {
        return alwaysReachedBlock;
    }

    public boolean isExceptionBlock() {
        return firstNode() instanceof ExceptionObjectNode;
    }

    public void setAlwaysReachedBlock(Block alwaysReachedBlock) {
        assert alwaysReachedBlock != this;
        this.alwaysReachedBlock = alwaysReachedBlock;
    }

    public EndNode end() {
        return end;
    }

    public void setEnd(EndNode end) {
        this.end = end;
    }

    public Node lastNode() {
        return lastNode;
    }

    public int loopDepth() {
        return loopDepth;
    }

    public void setLoopDepth(int i) {
        loopDepth = i;
    }

    public int loopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int i) {
        loopIndex = i;
    }

    public double probability() {
        return probability;
    }


    public void setProbability(double probability) {
        if (probability > this.probability) {
            this.probability = probability;
        }
    }

    public BeginNode createAnchor() {
        return (BeginNode) firstNode;
    }

    public void setLastNode(Node node) {
        this.lastNode = node;
    }

    public List<Block> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    public void setDominator(Block dominator) {
        assert this.dominator == null;
        assert dominator != null;
        this.dominator = dominator;
        dominator.dominated.add(this);
    }

    public List<Block> getDominated() {
        return Collections.unmodifiableList(dominated);
    }

    public List<Node> getInstructions() {
        return instructions;
    }

    public List<Block> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Block(int blockID) {
        this.blockID = blockID;
    }

    public void addSuccessor(Block other) {
        successors.add(other);
        other.predecessors.add(this);
    }

    public int blockID() {
        return blockID;
    }

    /**
     * Iterate over this block, its exception handlers, and its successors, in that order.
     *
     * @param closure the closure to apply to each block
     */
    public void iteratePreOrder(BlockClosure closure) {
        // TODO: identity hash map might be too slow, consider a boolean array or a mark field
        iterate(new IdentityHashMap<Block, Block>(), closure);
    }

    private void iterate(IdentityHashMap<Block, Block> mark, BlockClosure closure) {
        if (!mark.containsKey(this)) {
            mark.put(this, this);
            closure.apply(this);

            iterateReverse(mark, closure, this.successors);
        }
    }

    private void iterateReverse(IdentityHashMap<Block, Block> mark, BlockClosure closure, List<Block> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            list.get(i).iterate(mark, closure);
        }
    }

    @Override
    public String toString() {
        return "B" + blockID;
    }

    public boolean isLoopHeader() {
        return firstNode instanceof LoopBeginNode;
    }

    public boolean isLoopEnd() {
        return lastNode instanceof LoopEndNode;
    }

    public Block dominator() {
        return dominator;
    }

    public void setInstructions(List<Node> instructions) {
        this.instructions = instructions;
    }

    public static void iteratePostOrder(List<Block> blocks, BlockClosure closure) {
        ArrayList<Block> startBlocks = new ArrayList<Block>();
        for (Block block : blocks) {
            if (block.getPredecessors().size() == 0) {
                startBlocks.add(block);
            }
        }
        iteratePostOrder(blocks, closure, startBlocks.toArray(new Block[startBlocks.size()]));
    }

    public static void iteratePostOrder(List<Block> blocks, BlockClosure closure, Block... startBlocks) {
        BitMap visited = new BitMap(blocks.size());
        LinkedList<Block> workList = new LinkedList<Block>();
        for (Block block : startBlocks) {
            workList.add(block);
            visited.set(block.blockID());
        }

        while (!workList.isEmpty()) {
            Block b = workList.remove();

            closure.apply(b);

            for (Block succ : b.getSuccessors()) {
                if (!visited.get(succ.blockID())) {
                    boolean delay = false;
                    for (Block pred : succ.getPredecessors()) {
                        if (!visited.get(pred.blockID()) && !(pred.lastNode instanceof LoopEndNode)) {
                            delay = true;
                            break;
                        }
                    }

                    if (!delay) {
                        visited.set(succ.blockID());
                        workList.add(succ);
                    }
                }
            }
        }
    }

    public String name() {
        return "B" + blockID;
    }
}
