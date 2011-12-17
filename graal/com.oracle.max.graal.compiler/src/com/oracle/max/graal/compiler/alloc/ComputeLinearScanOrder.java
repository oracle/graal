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

package com.oracle.max.graal.compiler.alloc;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.graph.*;

public final class ComputeLinearScanOrder {

    private final int maxBlockId; // the highest blockId of a block
    private int numBlocks; // total number of blocks (smaller than maxBlockId)

    List<LIRBlock> linearScanOrder; // the resulting list of blocks in correct order
    List<LIRBlock> codeEmittingOrder;

    final BitMap visitedBlocks; // used for recursive processing of blocks
    final BitMap activeBlocks; // used for recursive processing of blocks
    final BitMap dominatorBlocks; // temporary BitMap used for computation of dominator
    final int[] forwardBranches; // number of incoming forward branches for each block
    final List<LIRBlock> workList; // temporary list (used in markLoops and computeOrder)
    final LIRBlock[] loopHeaders;

    // accessors for visitedBlocks and activeBlocks
    void initVisited() {
        activeBlocks.clearAll();
        visitedBlocks.clearAll();
    }

    boolean isVisited(LIRBlock b) {
        return visitedBlocks.get(b.blockID());
    }

    boolean isActive(LIRBlock b) {
        return activeBlocks.get(b.blockID());
    }

    void setVisited(LIRBlock b) {
        assert !isVisited(b) : "already set";
        visitedBlocks.set(b.blockID());
    }

    void setActive(LIRBlock b) {
        assert !isActive(b) : "already set";
        activeBlocks.set(b.blockID());
    }

    void clearActive(LIRBlock b) {
        assert isActive(b) : "not already";
        activeBlocks.clear(b.blockID());
    }

    // accessors for forwardBranches
    void incForwardBranches(LIRBlock b) {
        forwardBranches[b.blockID()]++;
    }

    int decForwardBranches(LIRBlock b) {
        return --forwardBranches[b.blockID()];
    }

    // accessors for final result
    public List<LIRBlock> linearScanOrder() {
        return linearScanOrder;
    }

    public ComputeLinearScanOrder(int maxBlockId, int loopCount, LIRBlock startBlock) {
        loopHeaders = new LIRBlock[loopCount];

        this.maxBlockId = maxBlockId;
        visitedBlocks = new BitMap(maxBlockId);
        activeBlocks = new BitMap(maxBlockId);
        dominatorBlocks = new BitMap(maxBlockId);
        forwardBranches = new int[maxBlockId];
        workList = new ArrayList<LIRBlock>(8);

        countEdges(startBlock, null);
        computeOrder(startBlock);
        printBlocks();
    }

    /**
     * Traverses the CFG to analyze block and edge info. The analysis performed is:
     *
     * 1. Count of total number of blocks.
     * 2. Count of all incoming edges and backward incoming edges.
     * 3. Number loop header blocks.
     * 4. Create a list with all loop end blocks.
     */
    void countEdges(LIRBlock cur, LIRBlock parent) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Counting edges for block B%d%s", cur.blockID(), parent == null ? "" : " coming from B" + parent.blockID());
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
            TTY.println("Finished counting edges for block B%d", cur.blockID());
        }
    }

    int computeWeight(LIRBlock cur) {

        // limit loop-depth to 15 bit (only for security reason, it will never be so big)
        int weight = (cur.loopDepth() & 0x7FFF) << 16;

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

    private boolean readyForProcessing(LIRBlock cur) {
        // Discount the edge just traveled.
        // When the number drops to zero, all forward branches were processed
        if (decForwardBranches(cur) != 0) {
            return false;
        }

        assert !linearScanOrder.contains(cur) : "block already processed (block can be ready only once)";
        assert !workList.contains(cur) : "block already in work-list (block can be ready only once)";
        return true;
    }

    private void sortIntoWorkList(LIRBlock cur) {
        assert !workList.contains(cur) : "block already in work list";

        int curWeight = computeWeight(cur);

        // the linearScanNumber is used to cache the weight of a block
        cur.setLinearScanNumber(curWeight);

        if (GraalOptions.StressLinearScan) {
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

        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Sorted B%d into worklist. new worklist:", cur.blockID());
            for (int i = 0; i < workList.size(); i++) {
                TTY.println(String.format("%8d B%02d  weight:%6x", i, workList.get(i).blockID(), workList.get(i).linearScanNumber()));
            }
        }

        for (int i = 0; i < workList.size(); i++) {
            assert workList.get(i).linearScanNumber() > 0 : "weight not set";
            assert i == 0 || workList.get(i - 1).linearScanNumber() <= workList.get(i).linearScanNumber() : "incorrect order in worklist";
        }
    }

    private void appendBlock(LIRBlock cur) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("appending block B%d (weight 0x%06x) to linear-scan order", cur.blockID(), cur.linearScanNumber());
        }
        assert !linearScanOrder.contains(cur) : "cannot add the same block twice";

        // currently, the linear scan order and code emit order are equal.
        // therefore the linearScanNumber and the weight of a block must also
        // be equal.
        cur.setLinearScanNumber(linearScanOrder.size());
        linearScanOrder.add(cur);

        if (cur.isLoopEnd() && cur.isLoopHeader()) {
            codeEmittingOrder.add(cur);
        } else {
            if (!cur.isLoopHeader() || !GraalOptions.OptReorderLoops) {
                codeEmittingOrder.add(cur);

                if (cur.isLoopEnd() && GraalOptions.OptReorderLoops) {
                    LIRBlock loopHeader = loopHeaders[cur.loopIndex()];
                    assert loopHeader != null;
                    codeEmittingOrder.add(loopHeader);

                    for (int i = 0; i < loopHeader.numberOfSux(); i++) {
                        LIRBlock succ = loopHeader.suxAt(i);
                        if (succ.loopDepth() == loopHeader.loopDepth()) {
                            succ.setAlign(true);
                        }
                    }
                }
            } else {
                loopHeaders[cur.loopIndex()] = cur;
            }
        }
    }

    private void computeOrder(LIRBlock startBlock) {
        if (GraalOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing final block order");
        }

        // the start block is always the first block in the linear scan order
        linearScanOrder = new ArrayList<LIRBlock>(numBlocks);

        codeEmittingOrder = new ArrayList<LIRBlock>(numBlocks);

        // start processing with standard entry block
        assert workList.isEmpty() : "list must be empty before processing";

        assert readyForProcessing(startBlock);
        sortIntoWorkList(startBlock);

        do {
            LIRBlock cur = workList.remove(workList.size() - 1);
            appendBlock(cur);

            int i;
            int numSux = cur.numberOfSux();
            // changed loop order to get "intuitive" order of if- and else-blocks
            for (i = 0; i < numSux; i++) {
                LIRBlock sux = cur.suxAt(i);
                if (readyForProcessing(sux)) {
                    sortIntoWorkList(sux);
                }
            }
        } while (workList.size() > 0);
    }

    public void printBlocks() {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("----- loop information:");
            for (LIRBlock cur : linearScanOrder) {
                TTY.print(String.format("%4d: B%02d: ", cur.linearScanNumber(), cur.blockID()));
                TTY.println(String.format(" . loopIndex: %2d, loopDepth: %2d", cur.loopIndex(), cur.loopDepth()));
            }
        }

        if (GraalOptions.TraceLinearScanLevel >= 1) {
            TTY.println("----- linear-scan block order:");
            for (LIRBlock cur : linearScanOrder) {
                TTY.print(String.format("%4d: B%02d    loop: %2d  depth: %2d", cur.linearScanNumber(), cur.blockID(), cur.loopIndex(), cur.loopDepth()));

                TTY.print(cur.isLoopHeader() ? " lh" : "   ");
                TTY.print(cur.isLoopEnd() ? " le" : "   ");

                TTY.print("    dom: null ");


                if (cur.numberOfPreds() > 0) {
                    TTY.print("    preds: ");
                    for (int j = 0; j < cur.numberOfPreds(); j++) {
                        LIRBlock pred = cur.predAt(j);
                        TTY.print("B%d ", pred.blockID());
                    }
                }
                if (cur.numberOfSux() > 0) {
                    TTY.print("    sux: ");
                    for (int j = 0; j < cur.numberOfSux(); j++) {
                        LIRBlock sux = cur.suxAt(j);
                        TTY.print("B%d ", sux.blockID());
                    }
                }
                TTY.println();
            }
        }
    }

    public List<LIRBlock> codeEmittingOrder() {
        return codeEmittingOrder;
    }
}
