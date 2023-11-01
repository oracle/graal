/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.common.alloc;

import java.util.BitSet;
import java.util.PriorityQueue;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.CodeEmissionOrder;
import jdk.graal.compiler.core.common.cfg.Loop;
import jdk.graal.compiler.options.OptionValues;

/**
 * Computes an ordering of the blocks that can be used by the machine code generator. The machine
 * code generation order will start with the first block and produce a straight sequence always
 * following the most likely successor. Then it will continue with the most likely path that was
 * left out during this process. The process iteratively continues until all blocks are scheduled.
 * Additionally, it is guaranteed that all blocks of a loop are scheduled before any block following
 * the loop is scheduled.
 *
 * The machine code generator order includes reordering of loop headers such that the backward jump
 * is a conditional jump if there is only one loop end block. Additionally, the target of loop
 * backward jumps are always marked as aligned. Aligning the target of conditional jumps does not
 * bring a measurable benefit and is therefore avoided to keep the code size small.
 */
public class DefaultCodeEmissionOrder<T extends BasicBlock<T>> implements CodeEmissionOrder<T> {
    protected int originalBlockCount;
    protected T startBlock;

    public DefaultCodeEmissionOrder(int originalBlockCount, T startBlock) {
        this.originalBlockCount = originalBlockCount;
        this.startBlock = startBlock;
    }

    /**
     * Computes the block order used for code emission.
     *
     * @return sorted list of ids of basic blocks, see {@link AbstractControlFlowGraph} for details
     *         about the data structures
     */
    @Override
    public int[] computeCodeEmittingOrder(OptionValues options, ComputationTime computationTime) {
        BasicBlockOrderUtils.BlockList<T> order = new BasicBlockOrderUtils.BlockList<>(originalBlockCount);
        BitSet visitedBlocks = new BitSet(originalBlockCount);
        PriorityQueue<T> worklist = BasicBlockOrderUtils.initializeWorklist(startBlock, visitedBlocks);
        computeCodeEmittingOrder(order, worklist, visitedBlocks, computationTime);
        BasicBlockOrderUtils.checkStartBlock(order, startBlock);
        return order.toIdArray();
    }

    /**
     * Iteratively adds paths to the code emission block order.
     */
    private static <T extends BasicBlock<T>> void computeCodeEmittingOrder(BasicBlockOrderUtils.BlockList<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks, ComputationTime computationTime) {
        while (!worklist.isEmpty()) {
            T nextImportantPath = worklist.poll();
            addPathToCodeEmittingOrder(nextImportantPath, order, worklist, visitedBlocks, computationTime);
        }
    }

    /**
     * Add a linear path to the code emission order greedily following the most likely successor.
     */
    private static <T extends BasicBlock<T>> void addPathToCodeEmittingOrder(T initialBlock, BasicBlockOrderUtils.BlockList<T> order, PriorityQueue<T> worklist, BitSet visitedBlocks,
                    ComputationTime computationTime) {
        T block = initialBlock;
        while (block != null) {
            if (order.isScheduled(block)) {
                /**
                 * We may be revisiting a block that has already been scheduled. This can happen for
                 * triangles:
                 *
                 * <pre>
                 *     A
                 *     |\
                 *     | B
                 *     |/
                 *     C
                 * </pre>
                 *
                 * C will be added to the worklist twice: once when A is scheduled, and once when B
                 * is scheduled.
                 */
                break;
            }
            if (!skipLoopHeader(block)) {
                // Align unskipped loop headers as they are the target of the backward jump.
                if (block.isLoopHeader()) {
                    block.setAlign(true);
                }
                order.add(block);
            }

            if (block.isLoopEnd()) {
                Loop<T> blockLoop = block.getLoop();

                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    T succ = block.getSuccessorAt(i);
                    if (order.isScheduled(succ)) {
                        continue;
                    }
                    Loop<T> loop = succ.getLoop();
                    if (loop == blockLoop && succ == loop.getHeader() && skipLoopHeader(succ)) {
                        // This is the only loop end of a skipped loop header.
                        // Add the header immediately afterwards.
                        order.add(loop.getHeader());

                        // For inverted loops (they always have a single loop end) do not align
                        // the header successor block if it's a trivial loop, since that's the loop
                        // end again.
                        boolean alignSucc = true;
                        if (loop.isInverted() && loop.getBlocks().size() < 2) {
                            alignSucc = false;
                        }

                        if (alignSucc) {
                            // Make sure the loop successors of the loop header are aligned
                            // as they are the target of the backward jump.
                            for (int j = 0; j < loop.getHeader().getSuccessorCount(); j++) {
                                T successor = loop.getHeader().getSuccessorAt(j);
                                if (successor.getLoopDepth() == block.getLoopDepth()) {
                                    successor.setAlign(true);
                                }
                            }
                        }
                    }
                }
            }

            T mostLikelySuccessor = BasicBlockOrderUtils.findAndMarkMostLikelySuccessor(block, order, visitedBlocks, computationTime, worklist);
            BasicBlockOrderUtils.enqueueSuccessors(block, worklist, visitedBlocks);
            block = mostLikelySuccessor;
        }
    }

    /**
     * Skip the loop header block if the loop consists of more than one block and it has only a
     * single loop end block in the same loop (not a backedge from a nested loop).
     */
    protected static <T extends BasicBlock<T>> boolean skipLoopHeader(BasicBlock<T> block) {
        if (block.isLoopHeader() && !block.isLoopEnd() && block.numBackedges() == 1) {
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                T pred = block.getPredecessorAt(i);
                if (pred.isLoopEnd() && pred.getLoop().getHeader() == block) {
                    return true;
                }
            }
        }
        return false;
    }
}
