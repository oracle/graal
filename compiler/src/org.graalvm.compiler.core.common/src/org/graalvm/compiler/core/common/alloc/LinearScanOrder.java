package org.graalvm.compiler.core.common.alloc;

import java.util.BitSet;
import java.util.PriorityQueue;

import org.graalvm.compiler.core.common.alloc.BasicBlockOrderUtils.BlockList;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;

/**
 * Computes an ordering of the blocks that can be used by the linear scan register allocator.
 *
 * The linear scan register allocator order has an mechanism that prevents merge nodes from being
 * scheduled if there is at least one highly likely predecessor still unscheduled. This increases
 * the probability that the merge node and the corresponding predecessor are more closely together
 * in the schedule thus decreasing the probability for inserted phi moves. Also, the algorithm sets
 * the linear scan order number of the block that corresponds to its index in the linear scan order.
 */
public final class LinearScanOrder {
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
    public static <T extends AbstractBlockBase<T>> AbstractBlockBase<?>[] computeLinearScanOrder(int originalBlockCount, T startBlock) {
        BlockList<T> order = new BlockList<>(originalBlockCount);
        BitSet visitedBlocks = new BitSet(originalBlockCount);
        PriorityQueue<T> worklist = BasicBlockOrderUtils.initializeWorklist(startBlock, visitedBlocks);
        computeLinearScanOrder(order, worklist, visitedBlocks);
        BasicBlockOrderUtils.checkOrder(order, originalBlockCount);
        BasicBlockOrderUtils.checkStartBlock(order, startBlock);
        return order.toArray();
    }

    /**
     * Iteratively adds paths to the linear scan block order.
     */
    private static <T extends AbstractBlockBase<T>> void computeLinearScanOrder(BlockList<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks) {
        while (!worklist.isEmpty()) {
            T nextImportantPath = worklist.poll();
            do {
                nextImportantPath = addPathToLinearScanOrder(nextImportantPath, order, worklist, visitedBlocks);
            } while (nextImportantPath != null);
        }
    }

    /**
     * Add a linear path to the linear scan order greedily following the most likely successor.
     */
    private static <T extends AbstractBlockBase<T>> T addPathToLinearScanOrder(T block, BlockList<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks) {
        block.setLinearScanNumber(order.size());
        order.add(block);
        T mostLikelySuccessor = BasicBlockOrderUtils.findAndMarkMostLikelySuccessor(block, order, visitedBlocks);
        BasicBlockOrderUtils.enqueueSuccessors(block, worklist, visitedBlocks);
        if (mostLikelySuccessor != null) {
            if (!mostLikelySuccessor.isLoopHeader() && mostLikelySuccessor.getPredecessorCount() > 1) {
                // We are at a merge. Check probabilities of predecessors that are not yet
                // scheduled.
                double unscheduledSum = 0.0;
                for (T pred : mostLikelySuccessor.getPredecessors()) {
                    if (pred.getLinearScanNumber() == -1) {
                        unscheduledSum += pred.getRelativeFrequency();
                    }
                }

                if (unscheduledSum > block.getRelativeFrequency() / PENALTY_VERSUS_UNSCHEDULED) {
                    // Add this merge only after at least one additional predecessor gets scheduled.
                    visitedBlocks.clear(mostLikelySuccessor.getId());
                    return null;
                }
            }
            return mostLikelySuccessor;
        }
        return null;
    }
}
