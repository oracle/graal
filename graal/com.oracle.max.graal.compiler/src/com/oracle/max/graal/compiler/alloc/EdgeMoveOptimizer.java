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

import com.oracle.max.graal.compiler.lir.*;

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
 */
final class EdgeMoveOptimizer {

    /**
     * Optimizes moves on block edges.
     *
     * @param blockList a list of blocks whose moves should be optimized
     */
    public static void optimize(List<LIRBlock> blockList) {
        EdgeMoveOptimizer optimizer = new EdgeMoveOptimizer();

        // ignore the first block in the list (index 0 is not processed)
        for (int i = blockList.size() - 1; i >= 1; i--) {
            LIRBlock block = blockList.get(i);

            if (block.numberOfPreds() > 1) {
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
     * that have the same {@linkplain LIRInstruction#operand() source} and {@linkplain LIRInstruction#result() destination}
     * operands and they have the same {@linkplain LIRInstruction#info debug info}.
     *
     * @param op1 the first instruction to compare
     * @param op2 the second instruction to compare
     * @return {@code true} if {@code op1} and {@code op2} are the same by the above algorithm
     */
    private boolean same(LIRInstruction op1, LIRInstruction op2) {
        assert op1 != null;
        assert op2 != null;

        if (op1.code == StandardOpcode.MOVE && op2.code == StandardOpcode.MOVE) {
            if (op1.info == op2.info && op1.input(0).equals(op2.input(0)) && op1.result().equals(op2.result())) {
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
    private void optimizeMovesAtBlockEnd(LIRBlock block) {
        if (block.isPredecessor(block)) {
            // currently we can't handle this correctly.
            return;
        }

        // clear all internal data structures
        edgeInstructionSeqences.clear();

        int numPreds = block.numberOfPreds();
        assert numPreds > 1 : "do not call otherwise";

        // setup a list with the LIR instructions of all predecessors
        for (int i = 0; i < numPreds; i++) {
            LIRBlock pred = block.predAt(i);
            assert pred != null;
            assert pred.lir() != null;
            List<LIRInstruction> predInstructions = pred.lir();

            if (pred.numberOfSux() != 1) {
                // this can happen with switch-statements where multiple edges are between
                // the same blocks.
                return;
            }

            if (predInstructions.get(predInstructions.size() - 1).code == StandardOpcode.XIR) {
                return;
            }

            assert pred.suxAt(0) == block : "invalid control flow";
            assert predInstructions.get(predInstructions.size() - 1).code == StandardOpcode.JUMP : "block must end with unconditional jump";

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
            block.lir().add(1, op);

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
    private void optimizeMovesAtBlockBegin(LIRBlock block) {

        edgeInstructionSeqences.clear();
        int numSux = block.numberOfSux();

        List<LIRInstruction> instructions = block.lir();

        assert numSux == 2 : "method should not be called otherwise";

        if (instructions.get(instructions.size() - 1).code == StandardOpcode.XIR) {
            // cannot optimize when last instruction is Xir.
            return;
        }

        assert instructions.get(instructions.size() - 1).code == StandardOpcode.JUMP : "block must end with unconditional jump";

        if (instructions.get(instructions.size() - 1).info != null) {
            // cannot optimize instructions when debug info is needed
            return;
        }

        LIRInstruction branch = instructions.get(instructions.size() - 2);
        if (!(branch instanceof LIRBranch) || branch.info != null) {
            // not a valid case for optimization
            // currently, only blocks that end with two branches (conditional branch followed
            // by unconditional branch) are optimized
            return;
        }

        // now it is guaranteed that the block ends with two branch instructions.
        // the instructions are inserted at the end of the block before these two branches
        int insertIdx = instructions.size() - 2;

        // setup a list with the lir-instructions of all successors
        for (int i = 0; i < numSux; i++) {
            LIRBlock sux = block.suxAt(i);
            List<LIRInstruction> suxInstructions = sux.lir();

            assert suxInstructions.get(0).code == StandardOpcode.LABEL : "block must start with label";

            if (sux.numberOfPreds() != 1) {
                // this can happen with switch-statements where multiple edges are between
                // the same blocks.
                return;
            }
            assert sux.predAt(0) == block : "invalid control flow";

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
            block.lir().add(insertIdx, op);
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
