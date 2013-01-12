/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.alloc;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * Computes an ordering of the block that can be used by the linear scan register allocator
 * and the machine code generator.
 */
public final class ComputeBlockOrder {
    private int blockCount;
    private List<Block> linearScanOrder;
    private List<Block> codeEmittingOrder;
    private final BitSet visitedBlocks; // used for recursive processing of blocks
    private final BitSet activeBlocks; // used for recursive processing of blocks
    private final int[] forwardBranches; // number of incoming forward branches for each block
    private final List<Block> workList; // temporary list (used in markLoops and computeOrder)
    private final List<Block> loopHeaders;
    private final boolean reorderLoops;

    private Comparator<Block> blockComparator = new Comparator<Block>() {
        @Override
        public int compare(Block o1, Block o2) {
            // Loop blocks before any loop exit block.
            int diff = o2.getLoopDepth() - o1.getLoopDepth();
            if (diff != 0) {
                return diff;
            }

            // Blocks with high probability before blocks with low probability.
            if (o1.getBeginNode().probability() > o2.getBeginNode().probability()) {
                return -1;
            } else {
                return 1;
            }
        }};

    public ComputeBlockOrder(int maxBlockId, int loopCount, Block startBlock, boolean reorderLoops) {
        loopHeaders = new ArrayList<>(loopCount);
        while (loopHeaders.size() < loopCount) {
            loopHeaders.add(null);
        }
        visitedBlocks = new BitSet(maxBlockId);
        activeBlocks = new BitSet(maxBlockId);
        forwardBranches = new int[maxBlockId];
        workList = new ArrayList<>(8);
        this.reorderLoops = reorderLoops;

        countEdges(startBlock, null);
        computeOrder(startBlock);

        List<Block> order = new ArrayList<>();
        PriorityQueue<Block> worklist = new PriorityQueue<>(10, blockComparator);
        BitSet orderedBlocks = new BitSet(maxBlockId);
        orderedBlocks.set(startBlock.getId());
        worklist.add(startBlock);
        computeCodeEmittingOrder(order, worklist, orderedBlocks);
        assert codeEmittingOrder.size() == order.size();
        codeEmittingOrder = order;
    }

    private void computeCodeEmittingOrder(List<Block> order, PriorityQueue<Block> worklist, BitSet orderedBlocks) {
        while (!worklist.isEmpty()) {
            Block nextImportantPath = worklist.poll();
            addImportantPath(nextImportantPath, order, worklist, orderedBlocks);
        }
    }

    private void addImportantPath(Block block, List<Block> order, PriorityQueue<Block> worklist, BitSet orderedBlocks) {
        if (!skipLoopHeader(block)) {
            if (block.isLoopHeader()) {
                block.align = true;
            }
            order.add(block);
        }
        if (block.isLoopEnd() && skipLoopHeader(block.getLoop().header)) {
            order.add(block.getLoop().header);
            for (Block succ : block.getLoop().header.getSuccessors()) {
                if (succ.getLoopDepth() == block.getLoopDepth()) {
                    succ.align = true;
                }
            }
        }
        Block bestSucc = null;
        double bestSuccProb = 0;

        for (Block succ : block.getSuccessors()) {
            if (!orderedBlocks.get(succ.getId()) && succ.getLoopDepth() >= block.getLoopDepth()) {
                double curProb = succ.getBeginNode().probability();
                if (curProb >= bestSuccProb) {
                    bestSuccProb = curProb;
                    bestSucc = succ;
                }
                assert curProb >= 0 : curProb;
            }
        }

        for (Block succ : block.getSuccessors()) {
            if (!orderedBlocks.get(succ.getId())) {
                if (succ != bestSucc) {
                    orderedBlocks.set(succ.getId());
                    worklist.add(succ);
                }
            }
        }

        if (bestSucc != null) {
            orderedBlocks.set(bestSucc.getId());
            addImportantPath(bestSucc, order, worklist, orderedBlocks);
        }
    }

    private boolean skipLoopHeader(Block bestSucc) {
        return (reorderLoops && bestSucc.isLoopHeader() && !bestSucc.isLoopEnd() && bestSucc.getLoop().loopBegin().loopEnds().count() == 1);
    }

    /**
     * Returns the block order used for the linear scan register allocator.
     * @return list of sorted blocks
     */
    public List<Block> linearScanOrder() {
        return linearScanOrder;
    }

    /**
     * Returns the block order used for machine code generation.
     * @return list of sorted blocks2222
     */
    public List<Block> codeEmittingOrder() {
        return codeEmittingOrder;
    }

    private boolean isVisited(Block b) {
        return visitedBlocks.get(b.getId());
    }

    private boolean isActive(Block b) {
        return activeBlocks.get(b.getId());
    }

    private void setVisited(Block b) {
        assert !isVisited(b);
        visitedBlocks.set(b.getId());
    }

    private void setActive(Block b) {
        assert !isActive(b);
        activeBlocks.set(b.getId());
    }

    private void clearActive(Block b) {
        assert isActive(b);
        activeBlocks.clear(b.getId());
    }

    private void incForwardBranches(Block b) {
        forwardBranches[b.getId()]++;
    }

    private int decForwardBranches(Block b) {
        return --forwardBranches[b.getId()];
    }

    /**
     * Traverses the CFG to analyze block and edge info. The analysis performed is:
     *
     * 1. Count of total number of blocks.
     * 2. Count of all incoming edges and backward incoming edges.
     * 3. Number loop header blocks.
     * 4. Create a list with all loop end blocks.
     */
    private void countEdges(Block cur, Block parent) {
        Debug.log("Counting edges for block B%d%s", cur.getId(), parent == null ? "" : " coming from B" + parent.getId());

        if (isActive(cur)) {
            return;
        }

        // increment number of incoming forward branches
        incForwardBranches(cur);

        if (isVisited(cur)) {
            return;
        }

        blockCount++;
        setVisited(cur);
        setActive(cur);

        // recursive call for all successors
        for (int i = cur.numberOfSux() - 1; i >= 0; i--) {
            countEdges(cur.suxAt(i), cur);
        }

        clearActive(cur);

        Debug.log("Finished counting edges for block B%d", cur.getId());
    }

    private static int computeWeight(Block cur) {

        // limit loop-depth to 15 bit (only for security reason, it will never be so big)
        int weight = (cur.getLoopDepth() & 0x7FFF) << 16;

        int curBit = 15;

        // loop end blocks (blocks that end with a backward branch) are added
        // after all other blocks of the loop.
        if (!cur.isLoopEnd()) {
            weight |= 1 << curBit;
        }
        curBit--;

        // exceptions handlers are added as late as possible
        if (!cur.isExceptionEntry()) {
            weight |= 1 << curBit;
        }
        curBit--;

        if (cur.getBeginNode().probability() > 0.5) {
            weight |= 1 << curBit;
        }
        curBit--;

        if (cur.getBeginNode().probability() > 0.05) {
            weight |= 1 << curBit;
        }
        curBit--;

        // guarantee that weight is > 0
        weight |= 1;

        assert curBit >= 0 : "too many flags";
        assert weight > 0 : "weight cannot become negative";

        return weight;
    }

    private boolean readyForProcessing(Block cur) {
        // Discount the edge just traveled.
        // When the number drops to zero, all forward branches were processed
        if (decForwardBranches(cur) != 0) {
            return false;
        }

        assert !linearScanOrder.contains(cur) : "block already processed (block can be ready only once)";
        assert !workList.contains(cur) : "block already in work-list (block can be ready only once)";
        return true;
    }

    private void sortIntoWorkList(Block cur) {
        assert !workList.contains(cur) : "block already in work list";

        int curWeight = computeWeight(cur);

        // the linearScanNumber is used to cache the weight of a block
        cur.linearScanNumber = curWeight;

        workList.add(null); // provide space for new element

        int insertIdx = workList.size() - 1;
        while (insertIdx > 0 && workList.get(insertIdx - 1).linearScanNumber > curWeight) {
            workList.set(insertIdx, workList.get(insertIdx - 1));
            insertIdx--;
        }
        workList.set(insertIdx, cur);

        if (Debug.isLogEnabled()) {
            Debug.log("Sorted B%d into worklist. new worklist:", cur.getId());
            for (int i = 0; i < workList.size(); i++) {
                Debug.log(String.format("%8d B%02d  weight:%6x", i, workList.get(i).getId(), workList.get(i).linearScanNumber));
            }
        }

        for (int i = 0; i < workList.size(); i++) {
            assert workList.get(i).linearScanNumber > 0 : "weight not set";
            assert i == 0 || workList.get(i - 1).linearScanNumber <= workList.get(i).linearScanNumber : "incorrect order in worklist";
        }
    }

    private void appendBlock(Block cur) {
        Debug.log("appending block B%d (weight 0x%06x) to linear-scan order", cur.getId(), cur.linearScanNumber);
        assert !linearScanOrder.contains(cur) : "cannot add the same block twice";

        cur.linearScanNumber = linearScanOrder.size();
        linearScanOrder.add(cur);

        if (cur.isLoopEnd() && cur.isLoopHeader()) {
            //cur.align = true;
            codeEmittingOrder.add(cur);
        } else {
            if (!cur.isLoopHeader() || ((LoopBeginNode) cur.getBeginNode()).loopEnds().count() > 1 || !reorderLoops) {
                if (cur.isLoopHeader()) {
                   // cur.align = true;
                }
                codeEmittingOrder.add(cur);

                if (cur.isLoopEnd() && reorderLoops) {
                    Block loopHeader = loopHeaders.get(cur.getLoop().index);
                    if (loopHeader != null) {
                        codeEmittingOrder.add(loopHeader);

                        for (int i = 0; i < loopHeader.numberOfSux(); i++) {
                            Block succ = loopHeader.suxAt(i);
                            if (succ.getLoopDepth() == loopHeader.getLoopDepth()) {
                              //  succ.align = true;
                            }
                        }
                    }
                }
            } else {
                loopHeaders.set(cur.getLoop().index, cur);
            }
        }
    }

    private void checkAndSortIntoWorkList(Block b) {
        if (readyForProcessing(b)) {
            sortIntoWorkList(b);
        }
    }

    private void computeOrder(Block startBlock) {
        // the start block is always the first block in the linear scan order
        linearScanOrder = new ArrayList<>(blockCount);

        codeEmittingOrder = new ArrayList<>(blockCount);

        // start processing with standard entry block
        assert workList.isEmpty() : "list must be empty before processing";

        sortIntoWorkList(startBlock);

        do {
            Block cur = workList.remove(workList.size() - 1);
            processBlock(cur);
        } while (workList.size() > 0);
    }

    private void processBlock(Block cur) {
        appendBlock(cur);

        Node endNode = cur.getEndNode();
        if (endNode instanceof IfNode && ((IfNode) endNode).probability() < 0.5) {
            assert cur.numberOfSux() == 2;
            checkAndSortIntoWorkList(cur.suxAt(1));
            checkAndSortIntoWorkList(cur.suxAt(0));
        } else {
            for (Block sux : cur.getSuccessors()) {
                checkAndSortIntoWorkList(sux);
            }
        }
    }
}
