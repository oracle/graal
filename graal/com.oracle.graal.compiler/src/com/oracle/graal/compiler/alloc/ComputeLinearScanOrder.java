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

package com.oracle.graal.compiler.alloc;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;

public final class ComputeLinearScanOrder {

    private int numBlocks; // total number of blocks (smaller than maxBlockId)

    List<Block> linearScanOrder; // the resulting list of blocks in correct order
    List<Block> codeEmittingOrder;

    final BitSet visitedBlocks; // used for recursive processing of blocks
    final BitSet activeBlocks; // used for recursive processing of blocks
    final BitSet dominatorBlocks; // temporary BitMap used for computation of dominator
    final int[] forwardBranches; // number of incoming forward branches for each block
    final List<Block> workList; // temporary list (used in markLoops and computeOrder)
    final Block[] loopHeaders;

    // accessors for visitedBlocks and activeBlocks
    void initVisited() {
        activeBlocks.clear();
        visitedBlocks.clear();
    }

    boolean isVisited(Block b) {
        return visitedBlocks.get(b.getId());
    }

    boolean isActive(Block b) {
        return activeBlocks.get(b.getId());
    }

    void setVisited(Block b) {
        assert !isVisited(b) : "already set";
        visitedBlocks.set(b.getId());
    }

    void setActive(Block b) {
        assert !isActive(b) : "already set";
        activeBlocks.set(b.getId());
    }

    void clearActive(Block b) {
        assert isActive(b) : "not already";
        activeBlocks.clear(b.getId());
    }

    // accessors for forwardBranches
    void incForwardBranches(Block b) {
        forwardBranches[b.getId()]++;
    }

    int decForwardBranches(Block b) {
        return --forwardBranches[b.getId()];
    }

    // accessors for final result
    public List<Block> linearScanOrder() {
        return linearScanOrder;
    }

    public ComputeLinearScanOrder(int maxBlockId, int loopCount, Block startBlock) {
        loopHeaders = new Block[loopCount];

        visitedBlocks = new BitSet(maxBlockId);
        activeBlocks = new BitSet(maxBlockId);
        dominatorBlocks = new BitSet(maxBlockId);
        forwardBranches = new int[maxBlockId];
        workList = new ArrayList<>(8);

        countEdges(startBlock, null);
        computeOrder(startBlock);
    }

    /**
     * Traverses the CFG to analyze block and edge info. The analysis performed is:
     *
     * 1. Count of total number of blocks.
     * 2. Count of all incoming edges and backward incoming edges.
     * 3. Number loop header blocks.
     * 4. Create a list with all loop end blocks.
     */
    void countEdges(Block cur, Block parent) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Counting edges for block B%d%s", cur.getId(), parent == null ? "" : " coming from B" + parent.getId());
        }

        if (isActive(cur)) {
            return;
        }

        // increment number of incoming forward branches
        incForwardBranches(cur);

        if (isVisited(cur)) {
            if (GraalOptions.TraceLinearScanLevel >= 3) {
                TTY.println("block already visited");
            }
            return;
        }

        numBlocks++;
        setVisited(cur);
        setActive(cur);

        // recursive call for all successors
        int i;
        for (i = cur.numberOfSux() - 1; i >= 0; i--) {
            countEdges(cur.suxAt(i), cur);
        }

        clearActive(cur);

        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Finished counting edges for block B%d", cur.getId());
        }
    }

    static int computeWeight(Block cur) {

        // limit loop-depth to 15 bit (only for security reason, it will never be so big)
        int weight = (cur.getLoopDepth() & 0x7FFF) << 16;

        int curBit = 15;

        // this is necessary for the (very rare) case that two successive blocks have
        // the same loop depth, but a different loop index (can happen for endless loops
        // with exception handlers)
//        if (!cur.isLinearScanLoopHeader()) {
//            weight |= 1 << curBit;
//        }
//        curBit--;

        // loop end blocks (blocks that end with a backward branch) are added
        // after all other blocks of the loop.
        if (!cur.isLoopEnd()) {
            weight |= 1 << curBit;
        }
        curBit--;

        // critical edge split blocks are preferred because then they have a greater
        // probability to be completely empty
        //if (cur.isCriticalEdgeSplit()) {
        //    weight |= 1 << curBit;
        //}
        //curBit--;

        // exceptions should not be thrown in normal control flow, so these blocks
        // are added as late as possible
//        if (!(cur.end() instanceof Throw) && (singleSux == null || !(singleSux.end() instanceof Throw))) {
//            weight |= 1 << curBit;
//        }
//        curBit--;
//        if (!(cur.end() instanceof Return) && (singleSux == null || !(singleSux.end() instanceof Return))) {
//            weight |= 1 << curBit;
//        }
//        curBit--;

        // exceptions handlers are added as late as possible
        if (!cur.isExceptionEntry()) {
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

        if (GraalOptions.StressLinearScan) {
            workList.add(0, cur);
            return;
        }

        workList.add(null); // provide space for new element

        int insertIdx = workList.size() - 1;
        while (insertIdx > 0 && workList.get(insertIdx - 1).linearScanNumber > curWeight) {
            workList.set(insertIdx, workList.get(insertIdx - 1));
            insertIdx--;
        }
        workList.set(insertIdx, cur);

        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Sorted B%d into worklist. new worklist:", cur.getId());
            for (int i = 0; i < workList.size(); i++) {
                TTY.println(String.format("%8d B%02d  weight:%6x", i, workList.get(i).getId(), workList.get(i).linearScanNumber));
            }
        }

        for (int i = 0; i < workList.size(); i++) {
            assert workList.get(i).linearScanNumber > 0 : "weight not set";
            assert i == 0 || workList.get(i - 1).linearScanNumber <= workList.get(i).linearScanNumber : "incorrect order in worklist";
        }
    }

    private void appendBlock(Block cur) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("appending block B%d (weight 0x%06x) to linear-scan order", cur.getId(), cur.linearScanNumber);
        }
        assert !linearScanOrder.contains(cur) : "cannot add the same block twice";

        // currently, the linear scan order and code emit order are equal.
        // therefore the linearScanNumber and the weight of a block must also
        // be equal.
        cur.linearScanNumber = linearScanOrder.size();
        linearScanOrder.add(cur);

        if (cur.isLoopEnd() && cur.isLoopHeader()) {
            codeEmittingOrder.add(cur);
        } else {
            if (!cur.isLoopHeader() || ((LoopBeginNode) cur.getBeginNode()).loopEnds().count() > 1 || !GraalOptions.OptReorderLoops) {
                codeEmittingOrder.add(cur);

                if (cur.isLoopEnd() && GraalOptions.OptReorderLoops) {
                    Block loopHeader = loopHeaders[cur.getLoop().index];
                    if (loopHeader != null) {
                        codeEmittingOrder.add(loopHeader);

                        for (int i = 0; i < loopHeader.numberOfSux(); i++) {
                            Block succ = loopHeader.suxAt(i);
                            if (succ.getLoopDepth() == loopHeader.getLoopDepth()) {
                                succ.align = true;
                            }
                        }
                    }
                }
            } else {
                loopHeaders[cur.getLoop().index] = cur;
            }
        }
    }

    private void computeOrder(Block startBlock) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing final block order");
        }

        // the start block is always the first block in the linear scan order
        linearScanOrder = new ArrayList<>(numBlocks);

        codeEmittingOrder = new ArrayList<>(numBlocks);

        // start processing with standard entry block
        assert workList.isEmpty() : "list must be empty before processing";

        assert readyForProcessing(startBlock);
        sortIntoWorkList(startBlock);

        do {
            Block cur = workList.remove(workList.size() - 1);
            appendBlock(cur);

            // make the most successor with the highest probability the immediate successor
            Node endNode = cur.getEndNode();
            if (endNode instanceof IfNode && ((IfNode) endNode).probability() < 0.5) {
                assert cur.numberOfSux() == 2;
                if (readyForProcessing(cur.suxAt(1))) {
                    sortIntoWorkList(cur.suxAt(1));
                }
                if (readyForProcessing(cur.suxAt(0))) {
                    sortIntoWorkList(cur.suxAt(0));
                }
            } else {
                for (Block sux : cur.getSuccessors()) {
                    if (readyForProcessing(sux)) {
                        sortIntoWorkList(sux);
                    }
                }
            }
        } while (workList.size() > 0);
    }

    public List<Block> codeEmittingOrder() {
        return codeEmittingOrder;
    }
}
