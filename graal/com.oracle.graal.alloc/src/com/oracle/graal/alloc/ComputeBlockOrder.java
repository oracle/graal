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

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * Computes an ordering of the block that can be used by the linear scan register allocator and the
 * machine code generator. The machine code generation order will start with the first block and
 * produce a straight sequence always following the most likely successor. Then it will continue
 * with the most likely path that was left out during this process. The process iteratively
 * continues until all blocks are scheduled. Additionally, it is guaranteed that all blocks of a
 * loop are scheduled before any block following the loop is scheduled.
 *
 * The machine code generator order includes reordering of loop headers such that the backward jump
 * is a conditional jump if there is only one loop end block. Additionally, the target of loop
 * backward jumps are always marked as aligned. Aligning the target of conditional jumps does not
 * bring a measurable benefit and is therefore avoided to keep the code size small.
 *
 * The linear scan register allocator order has an additional mechanism that prevents merge nodes
 * from being scheduled if there is at least one highly likely predecessor still unscheduled. This
 * increases the probability that the merge node and the corresponding predecessor are more closely
 * together in the schedule thus decreasing the probability for inserted phi moves. Also, the
 * algorithm sets the linear scan order number of the block that corresponds to its index in the
 * linear scan order.
 */
public final class ComputeBlockOrder {

    /**
     * The initial capacities of the worklists used for iteratively finding the block order.
     */
    private static final int INITIAL_WORKLIST_CAPACITY = 10;

    /**
     * Divisor used for degrading the probability of the current path versus unscheduled paths at a
     * merge node when calculating the linear scan order. A high value means that predecessors of
     * merge nodes are more likely to be scheduled before the merge node.
     */
    private static final int PENALTY_VERSUS_UNSCHEDULED = 10;

    /**
     * Computes the block order used for the linear scan register allocator.
     *
     * @return sorted list of blocks
     */
    public static <T extends AbstractBlock<T>> List<T> computeLinearScanOrder(int blockCount, T startBlock, BlocksToDoubles blockProbabilities) {
        List<T> order = new ArrayList<>();
        BitSet visitedBlocks = new BitSet(blockCount);
        PriorityQueue<T> worklist = initializeWorklist(startBlock, visitedBlocks, blockProbabilities);
        computeLinearScanOrder(order, worklist, visitedBlocks, blockProbabilities);
        assert checkOrder(order, blockCount);
        return order;
    }

    /**
     * Computes the block order used for code emission.
     *
     * @return sorted list of blocks
     */
    public static <T extends AbstractBlock<T>> List<T> computeCodeEmittingOrder(int blockCount, T startBlock, BlocksToDoubles blockProbabilities) {
        List<T> order = new ArrayList<>();
        BitSet visitedBlocks = new BitSet(blockCount);
        PriorityQueue<T> worklist = initializeWorklist(startBlock, visitedBlocks, blockProbabilities);
        computeCodeEmittingOrder(order, worklist, visitedBlocks, blockProbabilities);
        assert checkOrder(order, blockCount);
        return order;
    }

    /**
     * Iteratively adds paths to the code emission block order.
     */
    private static <T extends AbstractBlock<T>> void computeCodeEmittingOrder(List<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        while (!worklist.isEmpty()) {
            T nextImportantPath = worklist.poll();
            addPathToCodeEmittingOrder(nextImportantPath, order, worklist, visitedBlocks, blockProbabilities);
        }
    }

    /**
     * Iteratively adds paths to the linear scan block order.
     */
    private static <T extends AbstractBlock<T>> void computeLinearScanOrder(List<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        while (!worklist.isEmpty()) {
            T nextImportantPath = worklist.poll();
            addPathToLinearScanOrder(nextImportantPath, order, worklist, visitedBlocks, blockProbabilities);
        }
    }

    /**
     * Initializes the priority queue used for the work list of blocks and adds the start block.
     */
    private static <T extends AbstractBlock<T>> PriorityQueue<T> initializeWorklist(T startBlock, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        PriorityQueue<T> result = new PriorityQueue<>(INITIAL_WORKLIST_CAPACITY, new BlockOrderComparator<T>(blockProbabilities));
        result.add(startBlock);
        visitedBlocks.set(startBlock.getId());
        return result;
    }

    /**
     * Add a linear path to the linear scan order greedily following the most likely successor.
     */
    private static <T extends AbstractBlock<T>> void addPathToLinearScanOrder(T block, List<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        block.setLinearScanNumber(order.size());
        order.add(block);
        T mostLikelySuccessor = findAndMarkMostLikelySuccessor(block, visitedBlocks, blockProbabilities);
        enqueueSuccessors(block, worklist, visitedBlocks);
        if (mostLikelySuccessor != null) {
            if (!mostLikelySuccessor.isLoopHeader() && mostLikelySuccessor.getPredecessorCount() > 1) {
                // We are at a merge. Check probabilities of predecessors that are not yet
                // scheduled.
                double unscheduledSum = 0.0;
                for (T pred : mostLikelySuccessor.getPredecessors()) {
                    if (pred.getLinearScanNumber() == -1) {
                        unscheduledSum += blockProbabilities.get(pred);
                    }
                }

                if (unscheduledSum > blockProbabilities.get(block) / PENALTY_VERSUS_UNSCHEDULED) {
                    // Add this merge only after at least one additional predecessor gets scheduled.
                    visitedBlocks.clear(mostLikelySuccessor.getId());
                    return;
                }
            }
            addPathToLinearScanOrder(mostLikelySuccessor, order, worklist, visitedBlocks, blockProbabilities);
        }
    }

    /**
     * Add a linear path to the code emission order greedily following the most likely successor.
     */
    private static <T extends AbstractBlock<T>> void addPathToCodeEmittingOrder(T initialBlock, List<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        T block = initialBlock;
        while (block != null) {
            // Skip loop headers if there is only a single loop end block to
            // make the backward jump be a conditional jump.
            if (!skipLoopHeader(block)) {

                // Align unskipped loop headers as they are the target of the backward jump.
                if (block.isLoopHeader()) {
                    block.setAlign(true);
                }
                addBlock(block, order);
            }

            Loop<T> loop = block.getLoop();
            if (block.isLoopEnd() && skipLoopHeader(loop.header)) {

                // This is the only loop end of a skipped loop header.
                // Add the header immediately afterwards.
                addBlock(loop.header, order);

                // Make sure the loop successors of the loop header are aligned
                // as they are the target
                // of the backward jump.
                for (T successor : loop.header.getSuccessors()) {
                    if (successor.getLoopDepth() == block.getLoopDepth()) {
                        successor.setAlign(true);
                    }
                }
            }

            T mostLikelySuccessor = findAndMarkMostLikelySuccessor(block, visitedBlocks, blockProbabilities);
            enqueueSuccessors(block, worklist, visitedBlocks);
            block = mostLikelySuccessor;
        }
    }

    /**
     * Adds a block to the ordering.
     */
    private static <T extends AbstractBlock<T>> void addBlock(T header, List<T> order) {
        assert !order.contains(header) : "Cannot insert block twice";
        order.add(header);
    }

    /**
     * Find the highest likely unvisited successor block of a given block.
     */
    private static <T extends AbstractBlock<T>> T findAndMarkMostLikelySuccessor(T block, BitSet visitedBlocks, BlocksToDoubles blockProbabilities) {
        T result = null;
        for (T successor : block.getSuccessors()) {
            assert blockProbabilities.get(successor) >= 0.0 : "Probabilities must be positive";
            if (!visitedBlocks.get(successor.getId()) && successor.getLoopDepth() >= block.getLoopDepth() && (result == null || blockProbabilities.get(successor) >= blockProbabilities.get(result))) {
                result = successor;
            }
        }
        if (result != null) {
            visitedBlocks.set(result.getId());
        }
        return result;
    }

    /**
     * Add successor blocks into the given work list if they are not already marked as visited.
     */
    private static <T extends AbstractBlock<T>> void enqueueSuccessors(T block, PriorityQueue<T> worklist, BitSet visitedBlocks) {
        for (T successor : block.getSuccessors()) {
            if (!visitedBlocks.get(successor.getId())) {
                visitedBlocks.set(successor.getId());
                worklist.add(successor);
            }
        }
    }

    /**
     * Skip the loop header block if the loop consists of more than one block and it has only a
     * single loop end block.
     */
    private static <T extends AbstractBlock<T>> boolean skipLoopHeader(AbstractBlock<T> block) {
        return (block.isLoopHeader() && !block.isLoopEnd() && block.getLoop().numBackedges() == 1);
    }

    /**
     * Checks that the ordering contains the expected number of blocks.
     */
    private static boolean checkOrder(List<? extends AbstractBlock<?>> order, int expectedBlockCount) {
        assert order.size() == expectedBlockCount : String.format("Number of blocks in ordering (%d) does not match expected block count (%d)", order.size(), expectedBlockCount);
        return true;
    }

    /**
     * Comparator for sorting blocks based on loop depth and probability.
     */
    private static class BlockOrderComparator<T extends AbstractBlock<T>> implements Comparator<T> {

        private final BlocksToDoubles probabilities;

        public BlockOrderComparator(BlocksToDoubles probabilities) {
            this.probabilities = probabilities;
        }

        @Override
        public int compare(T a, T b) {
            // Loop blocks before any loop exit block.
            int diff = b.getLoopDepth() - a.getLoopDepth();
            if (diff != 0) {
                return diff;
            }

            // Blocks with high probability before blocks with low probability.
            if (probabilities.get(a) > probabilities.get(b)) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
