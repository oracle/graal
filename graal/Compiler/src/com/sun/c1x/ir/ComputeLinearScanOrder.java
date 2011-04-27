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

/**
 * @author Thomas Wuerthinger
 *
 */
public final class ComputeLinearScanOrder {

    private final int maxBlockId; // the highest blockId of a block
    private int numBlocks; // total number of blocks (smaller than maxBlockId)
    private int numLoops; // total number of loops
    private boolean iterativeDominators; // method requires iterative computation of dominators

    List<BlockBegin> linearScanOrder; // the resulting list of blocks in correct order

    final CiBitMap visitedBlocks; // used for recursive processing of blocks
    final CiBitMap activeBlocks; // used for recursive processing of blocks
    final CiBitMap dominatorBlocks; // temporary BitMap used for computation of dominator
    final int[] forwardBranches; // number of incoming forward branches for each block
    final List<BlockBegin> loopEndBlocks; // list of all loop end blocks collected during countEdges
    BitMap2D loopMap; // two-dimensional bit set: a bit is set if a block is contained in a loop
    final List<BlockBegin> workList; // temporary list (used in markLoops and computeOrder)

    // accessors for visitedBlocks and activeBlocks
    void initVisited() {
        activeBlocks.clearAll();
        visitedBlocks.clearAll();
    }

    boolean isVisited(BlockBegin b) {
        return visitedBlocks.get(b.blockID);
    }

    boolean isActive(BlockBegin b) {
        return activeBlocks.get(b.blockID);
    }

    void setVisited(BlockBegin b) {
        assert !isVisited(b) : "already set";
        visitedBlocks.set(b.blockID);
    }

    void setActive(BlockBegin b) {
        assert !isActive(b) : "already set";
        activeBlocks.set(b.blockID);
    }

    void clearActive(BlockBegin b) {
        assert isActive(b) : "not already";
        activeBlocks.clear(b.blockID);
    }

    // accessors for forwardBranches
    void incForwardBranches(BlockBegin b) {
        forwardBranches[b.blockID]++;
    }

    int decForwardBranches(BlockBegin b) {
        return --forwardBranches[b.blockID];
    }

    // accessors for loopMap
    boolean isBlockInLoop(int loopIdx, BlockBegin b) {
        return loopMap.at(loopIdx, b.blockID);
    }

    void setBlockInLoop(int loopIdx, BlockBegin b) {
        loopMap.setBit(loopIdx, b.blockID);
    }

    void clearBlockInLoop(int loopIdx, int blockId) {
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
        dominatorBlocks = new CiBitMap(maxBlockId);
        forwardBranches = new int[maxBlockId];
        loopEndBlocks = new ArrayList<BlockBegin>(8);
        workList = new ArrayList<BlockBegin>(8);

        splitCriticalEdges();

        countEdges(startBlock, null);

        if (numLoops > 0) {
            markLoops();
            clearNonNaturalLoops(startBlock);
            assignLoopDepth(startBlock);
        }

        computeOrder(startBlock);
        computeDominators();

        printBlocks();
        assert verify();
    }

    void splitCriticalEdges() {
        // TODO: move critical edge splitting from IR to here
    }

    /**
     * Traverses the CFG to analyze block and edge info. The analysis performed is:
     *
     * 1. Count of total number of blocks.
     * 2. Count of all incoming edges and backward incoming edges.
     * 3. Number loop header blocks.
     * 4. Create a list with all loop end blocks.
     */
    void countEdges(BlockBegin cur, BlockBegin parent) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Counting edges for block B%d%s", cur.blockID, parent == null ? "" : " coming from B" + parent.blockID);
        }
        assert cur.dominator() == null : "dominator already initialized";

        if (isActive(cur)) {
            if (C1XOptions.TraceLinearScanLevel >= 3) {
                TTY.println("backward branch");
            }
            assert isVisited(cur) : "block must be visited when block is active";
            assert parent != null : "must have parent";

            cur.setBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader);
            cur.setBlockFlag(BlockBegin.BlockFlag.BackwardBranchTarget);

            parent.setBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd);

            // When a loop header is also the start of an exception handler, then the backward branch is
            // an exception edge. Because such edges are usually critical edges which cannot be split, the
            // loop must be excluded here from processing.
            if (cur.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry)) {
                // Make sure that dominators are correct in this weird situation
                iterativeDominators = true;
                return;
            }
//            assert parent.numberOfSux() == 1 && parent.suxAt(0) == cur : "loop end blocks must have one successor (critical edges are split)";

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
        int i;
        for (i = cur.numberOfSux() - 1; i >= 0; i--) {
            countEdges(cur.suxAt(i), cur);
        }
        for (i = cur.numberOfExceptionHandlers() - 1; i >= 0; i--) {
            countEdges(cur.exceptionHandlerAt(i), cur);
        }

        clearActive(cur);

        // Each loop has a unique number.
        // When multiple loops are nested, assignLoopDepth assumes that the
        // innermost loop has the lowest number. This is guaranteed by setting
        // the loop number after the recursive calls for the successors above
        // have returned.
        if (cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader)) {
            assert cur.loopIndex() == -1 : "cannot set loop-index twice";
            if (C1XOptions.TraceLinearScanLevel >= 3) {
                TTY.println("Block B%d is loop header of loop %d", cur.blockID, numLoops);
            }

            cur.setLoopIndex(numLoops);
            numLoops++;
        }

        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("Finished counting edges for block B%d", cur.blockID);
        }
    }

    void markLoops() {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- marking loops");
        }

        loopMap = new BitMap2D(numLoops, maxBlockId);

        for (int i = loopEndBlocks.size() - 1; i >= 0; i--) {
            BlockBegin loopEnd = loopEndBlocks.get(i);
            BlockBegin loopStart = loopEnd.suxAt(0);
            int loopIdx = loopStart.loopIndex();

            if (C1XOptions.TraceLinearScanLevel >= 3) {
                TTY.println("Processing loop from B%d to B%d (loop %d):", loopStart.blockID, loopEnd.blockID, loopIdx);
            }
            assert loopEnd.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd) : "loop end flag must be set";
//            assert loopEnd.numberOfSux() == 1 : "incorrect number of successors";
            assert loopStart.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader) : "loop header flag must be set";
            assert loopIdx >= 0 && loopIdx < numLoops : "loop index not set";
            assert workList.isEmpty() : "work list must be empty before processing";

            // add the end-block of the loop to the working list
            workList.add(loopEnd);
            setBlockInLoop(loopIdx, loopEnd);
            do {
                BlockBegin cur = workList.remove(workList.size() - 1);

                if (C1XOptions.TraceLinearScanLevel >= 3) {
                    TTY.println("    processing B%d", cur.blockID);
                }
                assert isBlockInLoop(loopIdx, cur) : "bit in loop map must be set when block is in work list";

                // recursive processing of all predecessors ends when start block of loop is reached
                if (cur != loopStart && !cur.checkBlockFlag(BlockBegin.BlockFlag.OsrEntry)) {
                    for (int j = cur.numberOfPreds() - 1; j >= 0; j--) {
                        BlockBegin pred = cur.predAt(j);

                        if (!isBlockInLoop(loopIdx, pred)) {
                            // this predecessor has not been processed yet, so add it to work list
                            if (C1XOptions.TraceLinearScanLevel >= 3) {
                                TTY.println("    pushing B%d", pred.blockID);
                            }
                            workList.add(pred);
                            setBlockInLoop(loopIdx, pred);
                        }
                    }
                }
            } while (!workList.isEmpty());
        }
    }

    // check for non-natural loops (loops where the loop header does not dominate
    // all other loop blocks = loops with multiple entries).
    // such loops are ignored
    void clearNonNaturalLoops(BlockBegin startBlock) {
        for (int i = numLoops - 1; i >= 0; i--) {
            if (isBlockInLoop(i, startBlock)) {
                // loop i contains the entry block of the method.
                // this is not a natural loop, so ignore it
                if (C1XOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("Loop %d is non-natural, so it is ignored", i);
                }

                for (int blockId = maxBlockId - 1; blockId >= 0; blockId--) {
                    clearBlockInLoop(i, blockId);
                }
                iterativeDominators = true;
            }
        }
    }

    void assignLoopDepth(BlockBegin startBlock) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing loop-depth and weight");
        }
        initVisited();

        assert workList.isEmpty() : "work list must be empty before processing";
        workList.add(startBlock);

        do {
            BlockBegin cur = workList.remove(workList.size() - 1);

            if (!isVisited(cur)) {
                setVisited(cur);
                if (C1XOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("Computing loop depth for block B%d", cur.blockID);
                }

                // compute loop-depth and loop-index for the block
                assert cur.loopDepth() == 0 : "cannot set loop-depth twice";
                int i;
                int loopDepth = 0;
                int minLoopIdx = -1;
                for (i = numLoops - 1; i >= 0; i--) {
                    if (isBlockInLoop(i, cur)) {
                        loopDepth++;
                        minLoopIdx = i;
                    }
                }
                cur.setLoopDepth(loopDepth);
                cur.setLoopIndex(minLoopIdx);

                // append all unvisited successors to work list
                for (i = cur.numberOfSux() - 1; i >= 0; i--) {
                    workList.add(cur.suxAt(i));
                }
                for (i = cur.numberOfExceptionHandlers() - 1; i >= 0; i--) {
                    workList.add(cur.exceptionHandlerAt(i));
                }
            }
        } while (!workList.isEmpty());
    }

    BlockBegin commonDominator(BlockBegin a, BlockBegin b) {
        assert a != null && b != null : "must have input blocks";

        dominatorBlocks.clearAll();
        while (a != null) {
            dominatorBlocks.set(a.blockID);
            assert a.dominator() != null || a == linearScanOrder.get(0) : "dominator must be initialized";
            a = a.dominator();
        }
        while (b != null && !dominatorBlocks.get(b.blockID)) {
            assert b.dominator() != null || b == linearScanOrder.get(0) : "dominator must be initialized";
            b = b.dominator();
        }

        assert b != null : "could not find dominator";
        return b;
    }

    void computeDominator(BlockBegin cur, BlockBegin parent) {
        if (cur.dominator() == null) {
            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("DOM: initializing dominator of B%d to B%d", cur.blockID, parent.blockID);
            }
            if (cur.isExceptionEntry()) {
                assert parent.dominator() != null;
                cur.setDominator(parent.dominator());
            } else {
                cur.setDominator(parent);
            }

        } else if (!(cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader) && parent.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd))) {
            if (C1XOptions.TraceLinearScanLevel >= 4) {
                TTY.println("DOM: computing dominator of B%d: common dominator of B%d and B%d is B%d", cur.blockID, parent.blockID, cur.dominator().blockID, commonDominator(cur.dominator(), parent).blockID);
            }
            assert cur.numberOfPreds() > 1 : "";
            cur.setDominator(commonDominator(cur.dominator(), parent));
        }
    }

    int computeWeight(BlockBegin cur) {
        BlockBegin singleSux = null;
        if (cur.numberOfSux() == 1) {
            singleSux = cur.suxAt(0);
        }

        // limit loop-depth to 15 bit (only for security reason, it will never be so big)
        int weight = (cur.loopDepth() & 0x7FFF) << 16;

        int curBit = 15;

        // this is necessary for the (very rare) case that two successive blocks have
        // the same loop depth, but a different loop index (can happen for endless loops
        // with exception handlers)
        if (!cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader)) {
            weight |= (1 << curBit);
        }
        curBit--;

        // loop end blocks (blocks that end with a backward branch) are added
        // after all other blocks of the loop.
        if (!cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd)) {
            weight |= (1 << curBit);
        }
        curBit--;

        // critical edge split blocks are preferred because then they have a greater
        // probability to be completely empty
        if (cur.isCriticalEdgeSplit()) {
            weight |= (1 << curBit);
        }
        curBit--;

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

        // exceptions handlers are added as late as possible
        if (!cur.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry)) {
            weight |= (1 << curBit);
        }
        curBit--;

        // guarantee that weight is > 0
        weight |= 1;

        assert curBit >= 0 : "too many flags";
        assert weight > 0 : "weight cannot become negative";

        return weight;
    }

    boolean readyForProcessing(BlockBegin cur) {
        // Discount the edge just traveled.
        // When the number drops to zero, all forward branches were processed
        if (decForwardBranches(cur) != 0) {
            return false;
        }

        assert !linearScanOrder.contains(cur) : "block already processed (block can be ready only once)";
        assert !workList.contains(cur) : "block already in work-list (block can be ready only once)";
        return true;
    }

    void sortIntoWorkList(BlockBegin cur) {
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

    void appendBlock(BlockBegin cur) {
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

    void computeOrder(BlockBegin startBlock) {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing final block order");
        }

        // the start block is always the first block in the linear scan order
        linearScanOrder = new ArrayList<BlockBegin>(numBlocks);
        appendBlock(startBlock);

        assert startBlock.end() instanceof Base : "start block must end with Base-instruction";
        BlockBegin stdEntry = ((Base) startBlock.end()).standardEntry();
        BlockBegin osrEntry = ((Base) startBlock.end()).osrEntry();

        BlockBegin suxOfOsrEntry = null;
        if (osrEntry != null) {
            // special handling for osr entry:
            // ignore the edge between the osr entry and its successor for processing
            // the osr entry block is added manually below
            assert osrEntry.numberOfSux() == 1 : "osr entry must have exactly one successor";
            assert osrEntry.suxAt(0).numberOfPreds() >= 2 : "sucessor of osr entry must have two predecessors (otherwise it is not present in normal control flow)";

            suxOfOsrEntry = osrEntry.suxAt(0);
            decForwardBranches(suxOfOsrEntry);

            computeDominator(osrEntry, startBlock);
            iterativeDominators = true;
        }
        computeDominator(stdEntry, startBlock);

        // start processing with standard entry block
        assert workList.isEmpty() : "list must be empty before processing";

        if (readyForProcessing(stdEntry)) {
            sortIntoWorkList(stdEntry);
        } else {
            throw new CiBailout("the stdEntry must be ready for processing (otherwise, the method has no start block)");
        }

        do {
            BlockBegin cur = workList.remove(workList.size() - 1);

            if (cur == suxOfOsrEntry) {
                // the osr entry block is ignored in normal processing : it is never added to the
                // work list. Instead : it is added as late as possible manually here.
                appendBlock(osrEntry);
                computeDominator(cur, osrEntry);
            }
            appendBlock(cur);

            int i;
            int numSux = cur.numberOfSux();
            // changed loop order to get "intuitive" order of if- and else-blocks
            for (i = 0; i < numSux; i++) {
                BlockBegin sux = cur.suxAt(i);
                computeDominator(sux, cur);
                if (readyForProcessing(sux)) {
                    sortIntoWorkList(sux);
                }
            }
            numSux = cur.numberOfExceptionHandlers();
            for (i = 0; i < numSux; i++) {
                BlockBegin sux = cur.exceptionHandlerAt(i);
                computeDominator(sux, cur);
                if (readyForProcessing(sux)) {
                    sortIntoWorkList(sux);
                }
            }
        } while (workList.size() > 0);
    }

    boolean computeDominatorsIter() {
        boolean changed = false;
        int numBlocks = linearScanOrder.size();

        assert linearScanOrder.get(0).dominator() == null : "must not have dominator";
        assert linearScanOrder.get(0).numberOfPreds() == 0 : "must not have predecessors";
        for (int i = 1; i < numBlocks; i++) {
            BlockBegin block = linearScanOrder.get(i);

            assert block.numberOfPreds() > 0;
            BlockBegin dominator = block.predAt(0);
            if (block.isExceptionEntry()) {
                dominator = dominator.dominator();
            }

            int numPreds = block.numberOfPreds();
            for (int j = 1; j < numPreds; j++) {
                BlockBegin curPred = block.predAt(j);
                if (block.isExceptionEntry()) {
                    curPred = curPred.dominator();
                }
                dominator = commonDominator(dominator, curPred);
            }

            if (dominator != block.dominator()) {
                if (C1XOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("DOM: updating dominator of B%d from B%d to B%d", block.blockID, block.dominator().blockID, dominator.blockID);
                }
                block.setDominator(dominator);
                changed = true;
            }
        }
        return changed;
    }

    void computeDominators() {
        if (C1XOptions.TraceLinearScanLevel >= 3) {
            TTY.println("----- computing dominators (iterative computation reqired: %b)", iterativeDominators);
        }

        // iterative computation of dominators is only required for methods with non-natural loops
        // and OSR-methods. For all other methods : the dominators computed when generating the
        // linear scan block order are correct.
        if (iterativeDominators) {
            do {
                if (C1XOptions.TraceLinearScanLevel >= 1) {
                    TTY.println("DOM: next iteration of fix-point calculation");
                }
            } while (computeDominatorsIter());
        }

        // check that dominators are correct
        assert !computeDominatorsIter() : "fix point not reached";
    }

    public void printBlocks() {
        if (C1XOptions.TraceLinearScanLevel >= 2) {
            TTY.println("----- loop information:");
            for (BlockBegin cur : linearScanOrder) {
                TTY.print(String.format("%4d: B%02d: ", cur.linearScanNumber(), cur.blockID));
                for (int loopIdx = 0; loopIdx < numLoops; loopIdx++) {
                    TTY.print(String.format("%d = %b ", loopIdx, isBlockInLoop(loopIdx, cur)));
                }
                TTY.println(String.format(" . loopIndex: %2d, loopDepth: %2d", cur.loopIndex(), cur.loopDepth()));
            }
        }

        if (C1XOptions.TraceLinearScanLevel >= 1) {
            TTY.println("----- linear-scan block order:");
            for (BlockBegin cur : linearScanOrder) {
                TTY.print(String.format("%4d: B%02d    loop: %2d  depth: %2d", cur.linearScanNumber(), cur.blockID, cur.loopIndex(), cur.loopDepth()));

                TTY.print(cur.isExceptionEntry() ? " ex" : "   ");
                TTY.print(cur.isCriticalEdgeSplit() ? " ce" : "   ");
                TTY.print(cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader) ? " lh" : "   ");
                TTY.print(cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd) ? " le" : "   ");

                if (cur.dominator() != null) {
                    TTY.print("    dom: B%d ", cur.dominator().blockID);
                } else {
                    TTY.print("    dom: null ");
                }

                if (cur.numberOfPreds() > 0) {
                    TTY.print("    preds: ");
                    for (int j = 0; j < cur.numberOfPreds(); j++) {
                        BlockBegin pred = cur.predAt(j);
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
                if (cur.numberOfExceptionHandlers() > 0) {
                    TTY.print("    ex: ");
                    for (int j = 0; j < cur.numberOfExceptionHandlers(); j++) {
                        BlockBegin ex = cur.exceptionHandlerAt(j);
                        TTY.print("B%d ", ex.blockID);
                    }
                }
                TTY.println();
            }
        }
    }

    boolean verify() {
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

            for (BlockBegin sux : cur.end().successors()) {
                assert sux.linearScanNumber() >= 0 && sux.linearScanNumber() == linearScanOrder.indexOf(sux) : "incorrect linearScanNumber";
                if (!cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopEnd)) {
                    assert cur.linearScanNumber() < sux.linearScanNumber() : "invalid order";
                }
                if (cur.loopDepth() == sux.loopDepth()) {
                    assert cur.loopIndex() == sux.loopIndex() || sux.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader) : "successing blocks with same loop depth must have same loop index";
                }
            }

            for (BlockBegin pred : cur.predecessors()) {
                assert pred.linearScanNumber() >= 0 && pred.linearScanNumber() == linearScanOrder.indexOf(pred) : "incorrect linearScanNumber";
                if (!cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader)) {
                    assert cur.linearScanNumber() > pred.linearScanNumber() : "invalid order";
                }
                if (cur.loopDepth() == pred.loopDepth()) {
                    assert cur.loopIndex() == pred.loopIndex() || cur.checkBlockFlag(BlockBegin.BlockFlag.LinearScanLoopHeader) : "successing blocks with same loop depth must have same loop index";
                }

                assert cur.dominator().linearScanNumber() <= pred.linearScanNumber() : "dominator must be before predecessors";
            }

            // check dominator
            if (i == 0) {
                assert cur.dominator() == null : "first block has no dominator";
            } else {
                assert cur.dominator() != null : "all but first block must have dominator";
            }
            assert cur.numberOfPreds() != 1 || cur.dominator() == cur.predAt(0) || cur.isExceptionEntry() : "Single predecessor must also be dominator";
        }

        // check that all loops are continuous
        for (int loopIdx = 0; loopIdx < numLoops; loopIdx++) {
            int blockIdx = 0;
            assert !isBlockInLoop(loopIdx, linearScanOrder.get(blockIdx)) : "the first block must not be present in any loop";

            // skip blocks before the loop
            while (blockIdx < numBlocks && !isBlockInLoop(loopIdx, linearScanOrder.get(blockIdx))) {
                blockIdx++;
            }
            // skip blocks of loop
            while (blockIdx < numBlocks && isBlockInLoop(loopIdx, linearScanOrder.get(blockIdx))) {
                blockIdx++;
            }
            // after the first non-loop block : there must not be another loop-block
            while (blockIdx < numBlocks) {
                assert !isBlockInLoop(loopIdx, linearScanOrder.get(blockIdx)) : "loop not continuous in linear-scan order";
                blockIdx++;
            }
        }

        return true;
    }
}
