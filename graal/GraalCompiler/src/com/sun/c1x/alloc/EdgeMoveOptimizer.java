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
package com.sun.c1x.alloc;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;

/**
 * This class optimizes moves, particularly those that result from eliminating SSA form.
 *
 * When a block has more than one predecessor, and all predecessors end with
 * the {@linkplain #same(LIRInstruction, LIRInstruction) same} sequence of
 * {@linkplain LIROpcode#Move move} instructions, then these sequences
 * can be replaced with a single copy of the sequence at the beginning of the block.
 *
 * Similarly, when a block has more than one successor, then same sequences of
 * moves at the beginning of the successors can be placed once at the end of
 * the block. But because the moves must be inserted before all branch
 * instructions, this works only when there is exactly one conditional branch
 * at the end of the block (because the moves must be inserted before all
 * branches, but after all compares).
 *
 * This optimization affects all kind of moves (reg->reg, reg->stack and
 * stack->reg). Because this optimization works best when a block contains only
 * a few moves, it has a huge impact on the number of blocks that are totally
 * empty.
 *
 * @author Christian Wimmer (original HotSpot implementation)
 * @author Thomas Wuerthinger
 * @author Doug Simon
 */
final class EdgeMoveOptimizer {

    /**
     * Optimizes moves on block edges.
     *
     * @param blockList a list of blocks whose moves should be optimized
     */
    public static void optimize(List<BlockBegin> blockList) {
        EdgeMoveOptimizer optimizer = new EdgeMoveOptimizer();

        // ignore the first block in the list (index 0 is not processed)
        for (int i = blockList.size() - 1; i >= 1; i--) {
            BlockBegin block = blockList.get(i);

            if (block.numberOfPreds() > 1 && !block.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry)) {
                optimizer.optimizeMovesAtBlockEnd(block);
            }
            if (block.numberOfSux() == 2) {
                optimizer.optimizeMovesAtBlockBegin(block);
            }
        }
    }

    private final List<List<LIRInstruction>> edgeInstructionSeqences;

    private EdgeMoveOptimizer() {
        edgeInstructionSeqences = new ArrayList<List<LIRInstruction>>(4);
    }

    /**
     * Determines if two operations are both {@linkplain LIROpcode#Move moves}
     * that have the same {@linkplain LIROp1#operand() source} and {@linkplain LIROp1#result() destination}
     * operands and they have the same {@linkplain LIRInstruction#info debug info}.
     *
     * @param op1 the first instruction to compare
     * @param op2 the second instruction to compare
     * @return {@code true} if {@code op1} and {@code op2} are the same by the above algorithm
     */
    private boolean same(LIRInstruction op1, LIRInstruction op2) {
        assert op1 != null;
        assert op2 != null;

        if (op1.code == LIROpcode.Move && op2.code == LIROpcode.Move) {
            assert op1 instanceof LIROp1 : "move must be LIROp1";
            assert op2 instanceof LIROp1 : "move must be LIROp1";
            LIROp1 move1 = (LIROp1) op1;
            LIROp1 move2 = (LIROp1) op2;
            if (move1.info == move2.info && move1.operand().equals(move2.operand()) && move1.result().equals(move2.result())) {
                // these moves are exactly equal and can be optimized
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the longest {@linkplain #same common} subsequence at the end all
     * predecessors of {@code block} to the start of {@code block}.
     */
    private void optimizeMovesAtBlockEnd(BlockBegin block) {
        if (block.isPredecessor(block)) {
            // currently we can't handle this correctly.
            return;
        }

        // clear all internal data structures
        edgeInstructionSeqences.clear();

        int numPreds = block.numberOfPreds();
        assert numPreds > 1 : "do not call otherwise";
        assert !block.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry) : "exception handlers not allowed";

        // setup a list with the LIR instructions of all predecessors
        for (int i = 0; i < numPreds; i++) {
            BlockBegin pred = block.predAt(i);
            List<LIRInstruction> predInstructions = pred.lir().instructionsList();

            if (pred.numberOfSux() != 1) {
                // this can happen with switch-statements where multiple edges are between
                // the same blocks.
                return;
            }

            assert pred.suxAt(0) == block : "invalid control flow";
            assert predInstructions.get(predInstructions.size() - 1).code == LIROpcode.Branch : "block with successor must end with branch";
            assert predInstructions.get(predInstructions.size() - 1) instanceof LIRBranch : "branch must be LIROpBranch";
            assert ((LIRBranch) predInstructions.get(predInstructions.size() - 1)).cond() == Condition.TRUE : "block must end with unconditional branch";

            if (predInstructions.get(predInstructions.size() - 1).info != null) {
                // can not optimize instructions that have debug info
                return;
            }

            // ignore the unconditional branch at the end of the block
            List<LIRInstruction> seq = predInstructions.subList(0, predInstructions.size() - 1);
            edgeInstructionSeqences.add(seq);
        }

        // process lir-instructions while all predecessors end with the same instruction
        while (true) {
            List<LIRInstruction> seq = edgeInstructionSeqences.get(0);
            if (seq.isEmpty()) {
                return;
            }

            LIRInstruction op = last(seq);
            for (int i = 1; i < numPreds; ++i) {
                List<LIRInstruction> otherSeq = edgeInstructionSeqences.get(i);
                if (otherSeq.isEmpty() || !same(op, last(otherSeq))) {
                    return;
                }
            }

            // insert the instruction at the beginning of the current block
            block.lir().insertBefore(1, op);

            // delete the instruction at the end of all predecessors
            for (int i = 0; i < numPreds; i++) {
                seq = edgeInstructionSeqences.get(i);
                removeLast(seq);
            }
        }
    }

    /**
     * Moves the longest {@linkplain #same common} subsequence at the start of all
     * successors of {@code block} to the end of {@code block} just prior to the
     * branch instruction ending {@code block}.
     */
    private void optimizeMovesAtBlockBegin(BlockBegin block) {

        edgeInstructionSeqences.clear();
        int numSux = block.numberOfSux();

        List<LIRInstruction> instructions = block.lir().instructionsList();

        assert numSux == 2 : "method should not be called otherwise";
        assert instructions.get(instructions.size() - 1).code == LIROpcode.Branch : "block with successor must end with branch";
        assert instructions.get(instructions.size() - 1) instanceof LIRBranch : "branch must be LIROpBranch";
        assert ((LIRBranch) instructions.get(instructions.size() - 1)).cond() == Condition.TRUE : "block must end with unconditional branch";

        if (instructions.get(instructions.size() - 1).info != null) {
            // cannot optimize instructions when debug info is needed
            return;
        }

        LIRInstruction branch = instructions.get(instructions.size() - 2);
        if (branch.info != null || (branch.code != LIROpcode.Branch && branch.code != LIROpcode.CondFloatBranch)) {
            // not a valid case for optimization
            // currently, only blocks that end with two branches (conditional branch followed
            // by unconditional branch) are optimized
            return;
        }

        // now it is guaranteed that the block ends with two branch instructions.
        // the instructions are inserted at the end of the block before these two branches
        int insertIdx = instructions.size() - 2;

        if (C1XOptions.DetailedAsserts) {
            for (int i = insertIdx - 1; i >= 0; i--) {
                LIRInstruction op = instructions.get(i);
                if ((op.code == LIROpcode.Branch || op.code == LIROpcode.CondFloatBranch) && ((LIRBranch) op).block() != null) {
                    throw new Error("block with two successors can have only two branch instructions");
                }
            }
        }

        // setup a list with the lir-instructions of all successors
        for (int i = 0; i < numSux; i++) {
            BlockBegin sux = block.suxAt(i);
            List<LIRInstruction> suxInstructions = sux.lir().instructionsList();

            assert suxInstructions.get(0).code == LIROpcode.Label : "block must start with label";

            if (sux.numberOfPreds() != 1) {
                // this can happen with switch-statements where multiple edges are between
                // the same blocks.
                return;
            }
            assert sux.predAt(0) == block : "invalid control flow";
            assert !sux.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry) : "exception handlers not allowed";

            // ignore the label at the beginning of the block
            List<LIRInstruction> seq = suxInstructions.subList(1, suxInstructions.size());
            edgeInstructionSeqences.add(seq);
        }

        // process LIR instructions while all successors begin with the same instruction
        while (true) {
            List<LIRInstruction> seq = edgeInstructionSeqences.get(0);
            if (seq.isEmpty()) {
                return;
            }

            LIRInstruction op = first(seq);
            for (int i = 1; i < numSux; i++) {
                List<LIRInstruction> otherSeq = edgeInstructionSeqences.get(i);
                if (otherSeq.isEmpty() || !same(op, first(otherSeq))) {
                    // these instructions are different and cannot be optimized .
                    // no further optimization possible
                    return;
                }
            }

            // insert instruction at end of current block
            block.lir().insertBefore(insertIdx, op);
            insertIdx++;

            // delete the instructions at the beginning of all successors
            for (int i = 0; i < numSux; i++) {
                seq = edgeInstructionSeqences.get(i);
                removeFirst(seq);
            }
        }
    }

    /**
     * Gets the first element from a LIR instruction sequence.
     */
    private static LIRInstruction first(List<LIRInstruction> seq) {
        return seq.get(0);
    }

    /**
     * Gets the last element from a LIR instruction sequence.
     */
    private static LIRInstruction last(List<LIRInstruction> seq) {
        return seq.get(seq.size() - 1);
    }

    /**
     * Removes the first element from a LIR instruction sequence.
     */
    private static void removeFirst(List<LIRInstruction> seq) {
        seq.remove(0);
    }

    /**
     * Removes the last element from a LIR instruction sequence.
     */
    private static void removeLast(List<LIRInstruction> seq) {
        seq.remove(seq.size() - 1);
    }
}
