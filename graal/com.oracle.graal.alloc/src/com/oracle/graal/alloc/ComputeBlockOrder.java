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

import com.oracle.graal.nodes.cfg.*;

/**
 * Computes an ordering of the block that can be used by the linear scan register allocator
 * and the machine code generator.
 */
public final class ComputeBlockOrder {
    private List<Block> linearScanOrder;
    private List<Block> codeEmittingOrder;

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

    public ComputeBlockOrder(int maxBlockId, @SuppressWarnings("unused") int loopCount, Block startBlock, @SuppressWarnings("unused") boolean reorderLoops) {

        List<Block> newLinearScanOrder = new ArrayList<>();
        List<Block> order = new ArrayList<>();
        PriorityQueue<Block> worklist = new PriorityQueue<>(10, blockComparator);
        BitSet orderedBlocks = new BitSet(maxBlockId);
        orderedBlocks.set(startBlock.getId());
        worklist.add(startBlock);
        computeCodeEmittingOrder(order, worklist, orderedBlocks);
        codeEmittingOrder = order;

        orderedBlocks.clear();
        orderedBlocks.set(startBlock.getId());
        worklist.add(startBlock);
        computeNewLinearScanOrder(newLinearScanOrder, worklist, orderedBlocks);

        assert order.size() == newLinearScanOrder.size() : codeEmittingOrder.size() + " vs " + newLinearScanOrder.size();
        linearScanOrder = newLinearScanOrder;
    }

    private void computeCodeEmittingOrder(List<Block> order, PriorityQueue<Block> worklist, BitSet orderedBlocks) {
        while (!worklist.isEmpty()) {
            Block nextImportantPath = worklist.poll();
            addImportantPath(nextImportantPath, order, worklist, orderedBlocks);
        }
    }

    private void computeNewLinearScanOrder(List<Block> order, PriorityQueue<Block> worklist, BitSet orderedBlocks) {
        while (!worklist.isEmpty()) {
            Block nextImportantPath = worklist.poll();
            addImportantLinearScanOrderPath(nextImportantPath, order, worklist, orderedBlocks);
        }
    }

    private void addImportantLinearScanOrderPath(Block block, List<Block> order, PriorityQueue<Block> worklist, BitSet orderedBlocks) {
        order.add(block);

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
            if (!bestSucc.isLoopHeader() && bestSucc.getPredecessors().size() > 1) {
                // We are at a merge. Check probabilities of predecessors that are not yet scheduled.
                double unscheduledSum = 0.0;
                double scheduledSum = 0.0;
                for (Block pred : bestSucc.getPredecessors()) {
                    if (!orderedBlocks.get(pred.getId())) {
                        unscheduledSum += pred.getBeginNode().probability();
                    } else {
                        scheduledSum += pred.getBeginNode().probability();
                    }
                }

                if (unscheduledSum > 0.0 && unscheduledSum > scheduledSum / 10) {
                    return;
                }
            }
            orderedBlocks.set(bestSucc.getId());
            addImportantLinearScanOrderPath(bestSucc, order, worklist, orderedBlocks);
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

    private static boolean skipLoopHeader(Block bestSucc) {
        return (bestSucc.isLoopHeader() && !bestSucc.isLoopEnd() && bestSucc.getLoop().loopBegin().loopEnds().count() == 1);
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
}
