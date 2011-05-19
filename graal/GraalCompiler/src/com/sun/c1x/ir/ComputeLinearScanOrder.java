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

package com.sun.c1x.ir;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;

public final class ComputeLinearScanOrder {

    private final int maxBlockId; // the highest blockId of a block
    private int numBlocks; // total number of blocks (smaller than maxBlockId)
    private int numLoops; // total number of loops
    private boolean iterativeDominators; // method requires iterative computation of dominators

    List<BlockBegin> linearScanOrder; // the resulting list of blocks in correct order

    final CiBitMap visitedBlocks; // used for recursive processing of blocks
    final CiBitMap activeBlocks; // used for recursive processing of blocks
    final int[] forwardBranches; // number of incoming forward branches for each block
    final List<BlockBegin> loopEndBlocks; // list of all loop end blocks collected during countEdges
    BitMap2D loopMap; // two-dimensional bit set: a bit is set if a block is contained in a loop
    final List<BlockBegin> workList; // temporary list (used in markLoops and computeOrder)

    // accessors for visitedBlocks and activeBlocks
    private void initVisited() {
        activeBlocks.clearAll();
        visitedBlocks.clearAll();
    }

    private boolean isVisited(BlockBegin b) {
        return visitedBlocks.get(b.blockID);
    }

    private boolean isActive(BlockBegin b) {
        return activeBlocks.get(b.blockID);
    }

    private void setVisited(BlockBegin b) {
        assert !isVisited(b) : "already set";
        visitedBlocks.set(b.blockID);
    }

    private void setActive(BlockBegin b) {
        assert !isActive(b) : "already set";
        activeBlocks.set(b.blockID);
    }

    private void clearActive(BlockBegin b) {
        assert isActive(b) : "not already";
        activeBlocks.clear(b.blockID);
    }

    // accessors for forwardBranches
    private void incForwardBranches(BlockBegin b) {
        forwardBranches[b.blockID]++;
    }

    private int decForwardBranches(BlockBegin b) {
        return --forwardBranches[b.blockID];
    }

    // accessors for loopMap
    private boolean isBlockInLoop(int loopIdx, BlockBegin b) {
        return loopMap.at(loopIdx, b.blockID);
    }

    private void setBlockInLoop(int loopIdx, BlockBegin b) {
        loopMap.setBit(loopIdx, b.blockID);
    }

    private void clearBlockInLoop(int loopIdx, int blockId) {
        loopMap.clearBit(loopIdx, blockId);
    }

    // accessors for final result
    public List<BlockBegin> linearScanOrder() {
        return linearScanOrder;
    }

    public int numLoops() {
        return numLoops;
    }

    public ComputeLinearScanOrder(int maxBlockId, BlockBegin startBlock) {

        this.maxBlockId = maxBlockId;
        visitedBlocks = new CiBitMap(maxBlockId);
        activeBlocks = new CiBitMap(maxBlockId);
        forwardBranches = new int[maxBlockId];
        loopEndBlocks = new ArrayList<BlockBegin>(8);
        workList = new ArrayList<BlockBegin>(8);

        countEdges(startBlock, null);

        computeOrder(startBlock);

        printBlocks();
        assert verify();
    }

    /**
     * Traverses the CFG to analyze block and edge info. The analysis performed is:
     *
     * 1. Count of total number of blocks.
     * 2. Count of all incoming edges and backward incoming edges.
     * 3. Number loop header blocks.
     * 4. Create a list with all loop end blocks.
     */
    private void countEdges(final BlockBegin cur, BlockBegin parent) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Counting edges for block B%d%s", cur.blockID, parent == null ? "" : " coming from B" + parent.blockID);
        }

        if (isActive(cur)) {
            if (C1XOptions.TraceLinearScanLevel >= 3) {
                TTY.println("backward branch");
            }
            assert isVisited(cur) : "block must be visited when block is active";
            assert parent != null : "must have parent";

            //cur.setBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader);

            //parent.setBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd);

            loopEndBlocks.add(parent);
            return;
        }

        // increment number of incoming forward branches
        incForwardBranches(cur);

        if (isVisited(cur)) {
            if (C1XOptions.TraceLinearScanLevel >= 3) {
                TTY.println("block already visited");
            }
            return;
        }

        numBlocks++;
        setVisited(cur);
        setActive(cur);

        // recursive call for all successors
        cur.allSuccessorsDo(true, new BlockClosure() {
            public void apply(BlockBegin block) {
                countEdges(block, cur);
            }
        });

        clearActive(cur);

        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Finished counting edges for block B%d", cur.blockID);
        }
    }

    private int computeWeight(BlockBegin cur) {
        BlockBegin singleSux = null;
        if (cur.numberOfSux() == 1) {
            singleSux = cur.suxAt(0);
        }

        // limit loop-depth to 15 bit (only for security reason, it will never be so big)
        int loopDepth = 0; // TODO(tw): Assign loop depth
        int weight = (loopDepth & 0x7FFF) << 16;

        int curBit = 15;

        // exceptions should not be thrown in normal control flow, so these blocks
        // are added as late as possible
        if (!(cur.end() instanceof Throw) && (singleSux == null || !(singleSux.end() instanceof Throw))) {
            weight |= (1 << curBit);
        }
        curBit--;
        if (!(cur.end() instanceof Return) && (singleSux == null || !(singleSux.end() instanceof Return))) {
            weight |= (1 << curBit);
        }
        curBit--;

        // guarantee that weight is > 0
        weight |= 1;

        assert curBit >= 0 : "too many flags";
        assert weight > 0 : "weight cannot become negative";

        return weight;
    }

    private boolean readyForProcessing(BlockBegin cur) {
        // Discount the edge just traveled.
        // When the number drops to zero, all forward branches were processed
        if (decForwardBranches(cur) != 0) {
            return false;
        }

        assert !linearScanOrder.contains(cur) : "block already processed (block can be ready only once)";
        assert !workList.contains(cur) : "block already in work-list (block can be ready only once)";
        return true;
    }

    private void sortIntoWorkList(BlockBegin cur) {
        assert !workList.contains(cur) : "block already in work list";

        int curWeight = computeWeight(cur);

        // the linearScanNumber is used to cache the weight of a block
        cur.setLinearScanNumber(curWeight);

        if (C1XOptions.StressLinearScan) {
            workList.add(0, cur);
            return;
        }

        workList.add(null); // provide space for new element

        int insertIdx = workList.size() - 1;
        while (insertIdx > 0 && workList.get(insertIdx - 1).linearScanNumber() > curWeight) {
            workList.set(insertIdx, workList.get(insertIdx - 1));
            insertIdx--;
        }
        workList.set(insertIdx, cur);

        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Sorted B%d into worklist. new worklist:", cur.blockID);
            for (int i = 0; i < workList.size(); i++) {
                TTY.println(String.format("%8d B%02d  weight:%6x", i, workList.get(i).blockID, workList.get(i).linearScanNumber()));
            }
        }

        for (int i = 0; i < workList.size(); i++) {
            assert workList.get(i).linearScanNumber() > 0 : "weight not set";
            assert i == 0 || workList.get(i - 1).linearScanNumber() <= workList.get(i).linearScanNumber() : "incorrect order in worklist";
        }
    }

    private void appendBlock(BlockBegin cur) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("appending block B%d (weight 0x%06x) to linear-scan order", cur.blockID, cur.linearScanNumber());
        }
        assert !linearScanOrder.contains(cur) : "cannot add the same block twice";

        // currently, the linear scan order and code emit order are equal.
        // therefore the linearScanNumber and the weight of a block must also
        // be equal.
        cur.setLinearScanNumber(linearScanOrder.size());
        linearScanOrder.add(cur);
    }

    private void computeOrder(BlockBegin startBlock) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing final block order");
        }

        // the start block is always the first block in the linear scan order
        linearScanOrder = new ArrayList<BlockBegin>(numBlocks);

        // start processing with standard entry block
        assert workList.isEmpty() : "list must be empty before processing";

        if (readyForProcessing(startBlock)) {
            sortIntoWorkList(startBlock);
        } else {
            throw new CiBailout("the stdEntry must be ready for processing (otherwise, the method has no start block)");
        }

        do {
            final BlockBegin cur = workList.remove(workList.size() - 1);
            appendBlock(cur);

            cur.allSuccessorsDo(false, new BlockClosure() {
                public void apply(BlockBegin block) {
                    if (readyForProcessing(block)) {
                        sortIntoWorkList(block);
                    }
                }
            });
        } while (workList.size() > 0);
    }

    public void printBlocks() {
        if (C1XOptions.TraceLinearScanLevel >= 1) {
            TTY.println("----- linear-scan block order:");
            for (BlockBegin cur : linearScanOrder) {
                TTY.print(String.format("%4d: B%02d    loop: %2d  depth: %2d", cur.linearScanNumber(), cur.blockID, -1, -1));

                if (cur.numberOfPreds() > 0) {
                    TTY.print("    preds: ");
                    for (int j = 0; j < cur.numberOfPreds(); j++) {
                        BlockBegin pred = cur.predAt(j).block();
                        TTY.print("B%d ", pred.blockID);
                    }
                }
                if (cur.numberOfSux() > 0) {
                    TTY.print("    sux: ");
                    for (int j = 0; j < cur.numberOfSux(); j++) {
                        BlockBegin sux = cur.suxAt(j);
                        TTY.print("B%d ", sux.blockID);
                    }
                }
                TTY.println();
            }
        }
    }

    private boolean verify() {
        assert linearScanOrder.size() == numBlocks : "wrong number of blocks in list";

        if (C1XOptions.StressLinearScan) {
            // blocks are scrambled when StressLinearScan is used
            return true;
        }

        // check that all successors of a block have a higher linear-scan-number
        // and that all predecessors of a block have a lower linear-scan-number
        // (only backward branches of loops are ignored)
        int i;
        for (i = 0; i < linearScanOrder.size(); i++) {
            BlockBegin cur = linearScanOrder.get(i);

            assert cur.linearScanNumber() == i : "incorrect linearScanNumber";
            assert cur.linearScanNumber() >= 0 && cur.linearScanNumber() == linearScanOrder.indexOf(cur) : "incorrect linearScanNumber";

            for (BlockBegin sux : cur.end().blockSuccessors()) {
                assert sux.linearScanNumber() >= 0 && sux.linearScanNumber() == linearScanOrder.indexOf(sux) : "incorrect linearScanNumber";
            }

            for (Instruction pred : cur.blockPredecessors()) {
                BlockBegin begin = pred.block();
                assert begin.linearScanNumber() >= 0 && begin.linearScanNumber() == linearScanOrder.indexOf(begin) : "incorrect linearScanNumber";
            }
        }

        return true;
    }
}
