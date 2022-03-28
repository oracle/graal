/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.alloc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.CodeEmissionOrder.ComputationTime;
import org.graalvm.compiler.debug.GraalError;

public final class BasicBlockOrderUtils {

    /**
     * The initial capacities of the worklists used for iteratively finding the block order.
     */
    private static final int INITIAL_WORKLIST_CAPACITY = 10;

    /**
     * If the best successor for scheduling has a low successor probability, and its relative
     * frequency scaled by this factor is lower than the best block from the worklist, stop adding
     * blocks to the current path.
     */
    private static final int UNLIKELY_SUCCESSOR_STOP_FACTOR = 2;

    /**
     * Initializes the priority queue used for the work list of blocks and adds the start block.
     */
    protected static <T extends AbstractBlockBase<T>> PriorityQueue<T> initializeWorklist(T startBlock, BitSet visitedBlocks) {
        PriorityQueue<T> result = new PriorityQueue<>(INITIAL_WORKLIST_CAPACITY, new BlockOrderComparator<>());
        result.add(startBlock);
        visitedBlocks.set(startBlock.getId());
        return result;
    }

    /**
     * Checks that the ordering contains the expected number of blocks.
     */
    protected static boolean checkOrder(BlockList<? extends AbstractBlockBase<?>> order, int expectedBlockCount) {
        GraalError.guarantee(order.size() == expectedBlockCount, "Number of blocks in ordering (%d) does not match expected block count (%d)", order.size(), expectedBlockCount);
        return true;
    }

    /**
     * Checks that the ordering starts with the expected start block.
     */
    protected static <T extends AbstractBlockBase<T>> boolean checkStartBlock(BlockList<T> order, T startBlock) {
        GraalError.guarantee(order.first() == startBlock, "First block of ordering (%s) does not match expected start block %s", order.first(), startBlock);
        return true;
    }

    /**
     * Find the highest likely unvisited successor block of a given block.
     */
    protected static <T extends AbstractBlockBase<T>> T findAndMarkMostLikelySuccessor(T block, BlockList<T> order, BitSet visitedBlocks) {
        return findAndMarkMostLikelySuccessor(block, order, visitedBlocks, ComputationTime.BEFORE_CONTROL_FLOW_OPTIMIZATIONS, null);
    }

    protected static <T extends AbstractBlockBase<T>> T findAndMarkMostLikelySuccessor(T block, BlockList<T> order, BitSet visitedBlocks, ComputationTime computationTime,
                    PriorityQueue<T> worklist) {
        T result = null;
        double maxSuccFrequency = -1.0;
        double maxScheduledSuccProbability = -1.0;
        boolean isTriangle = false;
        if (block.getSuccessorCount() == 2 && computationTime == ComputationTime.AFTER_CONTROL_FLOW_OPTIMIZATIONS) {
            double thisFrequency = block.getRelativeFrequency();
            T left = block.getSuccessors()[0];
            T right = block.getSuccessors()[1];
            // Check if we have a control flow triangle merging up at one of these successors. See
            // usage of isTriangle below for explanation.
            if (Math.abs(left.getRelativeFrequency() - thisFrequency) < 0.0001 && right.getPredecessorCount() == 1) {
                isTriangle = true;
            } else if (Math.abs(right.getRelativeFrequency() - thisFrequency) < 0.0001 && left.getPredecessorCount() == 1) {
                isTriangle = true;
            }
        }
        for (int i = 0; i < block.getSuccessorCount(); i++) {
            T successor = block.getSuccessors()[i];
            double succProbability = block.getSuccessorProbabilities()[i];
            double succFrequency = successor.getRelativeFrequency();
            assert succFrequency >= 0.0 : "Relative frequencies must be positive";
            if (computationTime == ComputationTime.AFTER_CONTROL_FLOW_OPTIMIZATIONS) {
                /**
                 * Consider the following CFG fragment, with frequencies on blocks and probabilities
                 * on edges:
                 *
                 * <pre>
                 *         A(0.9)
                 *          /  \
                 *     0.9 /    \ 0.1
                 *        /      \
                 *   B(0.81)     C(0.09)
                 *                 \
                 *              1.0 \  .....
                 *                   \ | | /
                 *                   D(0.95) (many predecessors)
                 * </pre>
                 *
                 * We want to select B as the most likely successor for A based on its relative
                 * frequency of 0.81.
                 *
                 * Now assume C is an empty block. It will be removed by control flow optimization,
                 * giving the following CFG:
                 *
                 * <pre>
                 *         A(0.9)
                 *          /  \
                 *     0.9 /    \ 0.1
                 *        /      \
                 *   B(0.81)      \  .....
                 *                 \ | | /
                 *                 D(0.95) (many predecessors)
                 * </pre>
                 *
                 * Now D is the "most likely" successor based purely on its relative frequency, but
                 * it's unlikely that we will actually take the A -> D edge. We still want to select
                 * B as the most likely successor. Therefore, after control flow optimizations, we
                 * scale the successor block's frequency by the corresponding edge's probability.
                 */
                succFrequency *= succProbability;

                if (isTriangle) {
                    /**
                     * Control flow diamonds that are optimized to triangles are special. For
                     * example:
                     *
                     * <pre>
                     *        A(1.0)
                     *        /    \
                     *   0.4 /      \ 0.6
                     *      /        \
                     *   B(0.4)     C(0.6)
                     *      \        /
                     *   1.0 \      / 1.0
                     *        \    /
                     *        D(1.0)
                     * </pre>
                     *
                     * Before control flow optimizations we will always choose the successor with
                     * the higher edge probability (in this case, C) because the successor
                     * frequencies are proportional to the edge probabilities.
                     *
                     * Assume control flow optimization eliminates the less likely successor:
                     *
                     * <pre>
                     *        A(1.0)
                     *        |    \
                     *        |     \ 0.6
                     *        |      \
                     *    0.4 |     C(0.6)
                     *        |      /
                     *        |     / 1.0
                     *        |    /
                     *        D(1.0)
                     * </pre>
                     *
                     * Now we still want to select C as the most likely successor. But based either
                     * on block frequencies, or block frequencies scaled by edge probabilities, we
                     * would choose D instead. For the most likely path (A -> C -> D) this would
                     * place C out of line and involve jumping there and back to D.
                     *
                     * Therefore, if we are looking at a triangle, use the pure successor edge
                     * probability as the "successor frequency".
                     */
                    succFrequency = succProbability;
                }
            }
            boolean scheduled = order.isScheduled(successor);
            if (successor.getLoopDepth() >= block.getLoopDepth() && succFrequency >= maxSuccFrequency) {
                if (!scheduled) {
                    result = successor;
                    maxSuccFrequency = succFrequency;
                } else {
                    maxScheduledSuccProbability = Math.max(maxScheduledSuccProbability, succProbability);
                }
            }
        }
        if (result != null && computationTime == ComputationTime.AFTER_CONTROL_FLOW_OPTIMIZATIONS) {
            /**
             * Consider the example above with A's successor probabilities reversed:
             *
             * <pre>
             *         A(0.9)
             *          /  \
             *     0.1 /    \ 0.9
             *        /      \
             *   B(0.09)     C(0.81)
             *                 \
             *              1.0 \  .....
             *                   \ | | /
             *                   D(0.95) (many predecessors)
             * </pre>
             *
             * Before control flow optimizations we will select C as the most likely successor. Its
             * successor D, having many predecessors and a high frequency, may well have been
             * scheduled already. If that is the case, this path terminates with C. We will then
             * continue with some other higher frequency path from the worklist, not with B.
             *
             * After control flow optimizations, D will be a direct successor of A, but if it's
             * already been scheduled, we cannot select it, so we would select B instead. This may
             * cause us to insert some very unlikely blocks in a relatively likely program region.
             * Therefore, if the current block has a dominant successor (accounting for at least
             * half the successor probability) that has already been scheduled, and the selected
             * successor is unlikely compared to the first unscheduled block on the worklist, don't
             * select this unlikely successor.
             */
            if (maxScheduledSuccProbability >= 0.5) {
                T worklistHead = null;
                if (worklist != null) {
                    for (T w : worklist) {
                        if (!order.isScheduled(w)) {
                            worklistHead = w;
                            break;
                        }
                    }
                }
                if (worklistHead != null && worklistHead.getRelativeFrequency() >= UNLIKELY_SUCCESSOR_STOP_FACTOR * result.getRelativeFrequency()) {
                    result = null;
                }
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
    protected static <T extends AbstractBlockBase<T>> void enqueueSuccessors(T block, PriorityQueue<T> worklist, BitSet visitedBlocks) {
        for (T successor : block.getSuccessors()) {
            if (!visitedBlocks.get(successor.getId())) {
                visitedBlocks.set(successor.getId());
                worklist.add(successor);
            }
        }
    }

    /**
     * Comparator for sorting blocks based on loop depth and probability.
     */
    public static class BlockOrderComparator<T extends AbstractBlockBase<T>> implements Comparator<T> {
        private static final double EPSILON = 1E-6;

        @Override
        public int compare(T a, T b) {
            // Loop blocks before any loop exit block. The only exception are blocks that are
            // (almost) impossible to reach.
            if (a.getRelativeFrequency() > EPSILON && b.getRelativeFrequency() > EPSILON) {
                int diff = b.getLoopDepth() - a.getLoopDepth();
                if (diff != 0) {
                    return diff;
                }
            }

            // Blocks with high probability before blocks with low probability.
            if (a.getRelativeFrequency() > b.getRelativeFrequency()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * A data structure combining an append-only list of blocks and a bit set for efficiently
     * checking which blocks have been added.
     */
    protected static class BlockList<T extends AbstractBlockBase<T>> {
        private final ArrayList<T> order;
        private final BitSet scheduledBlocks;

        public BlockList(int capacity) {
            this.order = new ArrayList<>(capacity);
            this.scheduledBlocks = new BitSet(capacity);
        }

        public void add(T block) {
            GraalError.guarantee(!scheduledBlocks.get(block.getId()), "Cannot insert block twice: ", block);
            order.add(block);
            scheduledBlocks.set(block.getId());
        }

        public int size() {
            return order.size();
        }

        public T first() {
            return order.get(0);
        }

        public boolean isScheduled(T block) {
            return scheduledBlocks.get(block.getId());
        }

        public AbstractBlockBase<?>[] toArray() {
            return order.toArray(new AbstractBlockBase<?>[0]);
        }
    }
}
