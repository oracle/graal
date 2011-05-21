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
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;

/**
 * This class performs basic optimizations on the control flow graph after LIR generation.
 */
final class ControlFlowOptimizer {

    /**
     * Performs control flow optimizations on the given IR graph.
     * @param ir the IR graph that should be optimized
     */
    public static void optimize(IR ir) {
        ControlFlowOptimizer optimizer = new ControlFlowOptimizer(ir);
        List<LIRBlock> code = ir.linearScanOrder();
        optimizer.reorderShortLoops(code);
        optimizer.deleteEmptyBlocks(code);
        optimizer.deleteUnnecessaryJumps(code);
        optimizer.deleteJumpsToReturn(code);
    }

    private final IR ir;

    private ControlFlowOptimizer(IR ir) {
        this.ir = ir;
    }

    private void reorderShortLoop(List<LIRBlock> code, LIRBlock headerBlock, int headerIdx) {
        int i = headerIdx + 1;
        int maxEnd = Math.min(headerIdx + C1XOptions.MaximumShortLoopSize, code.size());
        while (i < maxEnd && code.get(i).loopDepth() >= headerBlock.loopDepth()) {
            i++;
        }

        if (i == code.size() || code.get(i).loopDepth() < headerBlock.loopDepth()) {
            int endIdx = i - 1;
            LIRBlock endBlock = code.get(endIdx);

            if (endBlock.numberOfSux() == 1 && endBlock.suxAt(0) == headerBlock) {
                // short loop from headerIdx to endIdx found . reorder blocks such that
                // the headerBlock is the last block instead of the first block of the loop

                for (int j = headerIdx; j < endIdx; j++) {
                    code.set(j, code.get(j + 1));
                }
                code.set(endIdx, headerBlock);
            }
        }
    }

    private void reorderShortLoops(List<LIRBlock> code) {
        for (int i = code.size() - 1; i >= 0; i--) {
            LIRBlock block = code.get(i);

            if (block.isLinearScanLoopHeader()) {
                reorderShortLoop(code, block, i);
            }
        }

        assert verify(code);
    }

    // only blocks with exactly one successor can be deleted. Such blocks
    // must always end with an unconditional branch to this successor
    private boolean canDeleteBlock(LIRBlock block) {
        if (block.numberOfSux() != 1 ||
            block == ir.startBlock ||
            block.suxAt(0) == block) {
            return false;
        }

        List<LIRInstruction> instructions = block.lir().instructionsList();

        assert instructions.size() >= 2 : "block must have label and branch";
        assert instructions.get(0).code == LIROpcode.Label : "first instruction must always be a label";
        assert instructions.get(instructions.size() - 1) instanceof LIRBranch : "last instruction must always be a branch";
        assert ((LIRBranch) instructions.get(instructions.size() - 1)).cond() == Condition.TRUE : "branch must be unconditional";
        assert ((LIRBranch) instructions.get(instructions.size() - 1)).block() == block.suxAt(0) : "branch target must be the successor";

        // block must have exactly one successor

        return instructions.size() == 2 && instructions.get(instructions.size() - 1).info == null;
    }

    private void deleteEmptyBlocks(List<LIRBlock> code) {
        int oldPos = 0;
        int newPos = 0;
        int numBlocks = code.size();

        while (oldPos < numBlocks) {
            LIRBlock block = code.get(oldPos);

            if (canDeleteBlock(block)) {
                LIRBlock newTarget = block.suxAt(0);

                // update the block references in any branching LIR instructions
                for (LIRBlock pred : block.blockPredecessors()) {
                    for (LIRInstruction instr : pred.lir().instructionsList()) {
                        if (instr instanceof LIRBranch) {
                            ((LIRBranch) instr).substitute(block, newTarget);
                        } else if (instr instanceof LIRTableSwitch) {
                            ((LIRTableSwitch) instr).substitute(block, newTarget);
                        }
                    }
                }

                // adjust successor and predecessor lists
                block.replaceWith(newTarget);
                C1XMetrics.BlocksDeleted++;
            } else {
                // adjust position of this block in the block list if blocks before
                // have been deleted
                if (newPos != oldPos) {
                    code.set(newPos, code.get(oldPos));
                }
                newPos++;
            }
            oldPos++;
        }
        assert verify(code);
        Util.truncate(code, newPos);

        assert verify(code);
    }

    private void deleteUnnecessaryJumps(List<LIRBlock> code) {
        // skip the last block because there a branch is always necessary
        for (int i = code.size() - 2; i >= 0; i--) {
            LIRBlock block = code.get(i);
            List<LIRInstruction> instructions = block.lir().instructionsList();

            LIRInstruction lastOp = instructions.get(instructions.size() - 1);
            if (lastOp.code == LIROpcode.Branch) {
                assert lastOp instanceof LIRBranch : "branch must be of type LIRBranch";
                LIRBranch lastBranch = (LIRBranch) lastOp;

                assert lastBranch.block() != null : "last branch must always have a block as target";
                assert lastBranch.label() == lastBranch.block().label() : "must be equal";

                if (lastBranch.info == null) {
                    if (lastBranch.block() == code.get(i + 1)) {
                        // delete last branch instruction
                        Util.truncate(instructions, instructions.size() - 1);

                    } else {
                        LIRInstruction prevOp = instructions.get(instructions.size() - 2);
                        if (prevOp.code == LIROpcode.Branch || prevOp.code == LIROpcode.CondFloatBranch) {
                            assert prevOp instanceof LIRBranch : "branch must be of type LIRBranch";
                            LIRBranch prevBranch = (LIRBranch) prevOp;

                            if (prevBranch.block() == code.get(i + 1) && prevBranch.info == null) {
                                // eliminate a conditional branch to the immediate successor
                                prevBranch.changeBlock(lastBranch.block());
                                prevBranch.negateCondition();
                                Util.truncate(instructions, instructions.size() - 1);
                            }
                        }
                    }
                }
            }
        }

        assert verify(code);
    }

    private void deleteJumpsToReturn(List<LIRBlock> code) {
        for (int i = code.size() - 1; i >= 0; i--) {
            LIRBlock block = code.get(i);
            List<LIRInstruction> curInstructions = block.lir().instructionsList();
            LIRInstruction curLastOp = curInstructions.get(curInstructions.size() - 1);

            assert curInstructions.get(0).code == LIROpcode.Label : "first instruction must always be a label";
            if (curInstructions.size() == 2 && curLastOp.code == LIROpcode.Return) {
                // the block contains only a label and a return
                // if a predecessor ends with an unconditional jump to this block, then the jump
                // can be replaced with a return instruction
                //
                // Note: the original block with only a return statement cannot be deleted completely
                // because the predecessors might have other (conditional) jumps to this block.
                // this may lead to unnecesary return instructions in the final code

                assert curLastOp.info == null : "return instructions do not have debug information";

                assert curLastOp instanceof LIROp1 : "return must be LIROp1";
                CiValue returnOpr = ((LIROp1) curLastOp).operand();

                for (int j = block.numberOfPreds() - 1; j >= 0; j--) {
                    LIRBlock pred = block.predAt(j);
                    List<LIRInstruction> predInstructions = pred.lir().instructionsList();
                    LIRInstruction predLastOp = predInstructions.get(predInstructions.size() - 1);

                    if (predLastOp.code == LIROpcode.Branch) {
                        assert predLastOp instanceof LIRBranch : "branch must be LIRBranch";
                        LIRBranch predLastBranch = (LIRBranch) predLastOp;

                        if (predLastBranch.block() == block && predLastBranch.cond() == Condition.TRUE && predLastBranch.info == null) {
                            // replace the jump to a return with a direct return
                            // Note: currently the edge between the blocks is not deleted
                            predInstructions.set(predInstructions.size() - 1, new LIROp1(LIROpcode.Return, returnOpr));
                        }
                    }
                }
            }
        }
    }

    private boolean verify(List<LIRBlock> code) {
        for (LIRBlock block : code) {
            List<LIRInstruction> instructions = block.lir().instructionsList();

            for (LIRInstruction instr : instructions) {
                if (instr instanceof LIRBranch) {
                    LIRBranch opBranch = (LIRBranch) instr;
                    assert opBranch.block() == null || code.contains(opBranch.block()) : "missing successor branch from: " + block + " to: " + opBranch.block();
                    assert opBranch.unorderedBlock() == null || code.contains(opBranch.unorderedBlock()) : "missing successor branch from: " + block + " to: " + opBranch.unorderedBlock();
                }
            }

            for (LIRBlock sux : block.blockSuccessors()) {
                assert code.contains(sux) : "missing successor from: " + block + "to: " + sux;
            }

            for (LIRBlock pred : block.blockPredecessors()) {
                assert code.contains(pred) : "missing predecessor from: " + block + "to: " + pred;
            }
        }

        return true;
    }
}
