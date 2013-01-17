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
package com.oracle.graal.lir;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * This class performs basic optimizations on the control flow graph after LIR generation.
 */
public final class ControlFlowOptimizer {

    /**
     * Performs control flow optimizations on the given LIR graph.
     */
    public static void optimize(LIR ir) {
        List<Block> blocks = ir.codeEmittingOrder();
        ControlFlowOptimizer.deleteEmptyBlocks(ir, blocks);
        ControlFlowOptimizer.deleteUnnecessaryJumps(ir, blocks);
    }

    private ControlFlowOptimizer() {
    }

    /**
     * Checks whether a block can be deleted. Only blocks with exactly one successor and an
     * unconditional branch to this successor are eligable.
     * @param block the block checked for deletion
     * @return whether the block can be deleted
     */
    private static boolean canDeleteBlock(LIR ir, Block block) {
        if (block.getSuccessorCount() != 1 ||
            block.getPredecessorCount() == 0 ||
            block.getFirstSuccessor() == block) {
            return false;
        }

        List<LIRInstruction> instructions = ir.lir(block);

        assert instructions.size() >= 2 : "block must have label and branch";
        assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
        assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "last instruction must always be a branch";
        assert ((StandardOp.JumpOp) instructions.get(instructions.size() - 1)).destination().label() == ((StandardOp.LabelOp) ir.lir(block.getFirstSuccessor()).get(0)).getLabel() : "branch target must be the successor";

        // Block must have exactly one successor.
        return instructions.size() == 2 && !instructions.get(instructions.size() - 1).hasState();
    }

    private static void deleteEmptyBlocks(LIR ir, List<Block> blocks) {
        assert verify(blocks);
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (canDeleteBlock(ir, block)) {
                // adjust successor and predecessor lists
                Block other = block.getFirstSuccessor();
                for (Block pred : block.getPredecessors()) {
                    Collections.replaceAll(pred.getSuccessors(), block, other);
                }
                for (int i = 0; i < other.getPredecessors().size(); i++) {
                    if (other.getPredecessors().get(i) == block) {
                        other.getPredecessors().remove(i);
                        other.getPredecessors().addAll(i, block.getPredecessors());
                    }
                }
                block.getSuccessors().clear();
                block.getPredecessors().clear();
                Debug.metric("BlocksDeleted").increment();
                iterator.remove();
            }
        }
        assert verify(blocks);
    }

    private static void deleteUnnecessaryJumps(LIR ir, List<Block> blocks) {
        // skip the last block because there a branch is always necessary
        for (int i = blocks.size() - 2; i >= 0; i--) {
            Block block = blocks.get(i);
            List<LIRInstruction> instructions = ir.lir(block);

            LIRInstruction lastOp = instructions.get(instructions.size() - 1);
            if (lastOp instanceof StandardOp.JumpOp) {
                StandardOp.JumpOp lastJump = (StandardOp.JumpOp) lastOp;

                if (!lastOp.hasState()) {
                    if (lastJump.destination().label() == ((StandardOp.LabelOp) ir.lir(blocks.get(i + 1)).get(0)).getLabel()) {
                        // delete last branch instruction
                        instructions.remove(instructions.size() - 1);
                    } else {
                        LIRInstruction prevOp = instructions.get(instructions.size() - 2);
                        if (prevOp instanceof StandardOp.BranchOp) {
                            StandardOp.BranchOp prevBranch = (StandardOp.BranchOp) prevOp;

                            if (prevBranch.destination().label() == ((StandardOp.LabelOp) ir.lir(blocks.get(i + 1)).get(0)).getLabel() && !prevOp.hasState()) {
                                // eliminate a conditional branch to the immediate successor
                                prevBranch.negate(lastJump.destination());
                                instructions.remove(instructions.size() - 1);
                            }
                        }
                    }
                }
            }
        }
        assert verify(blocks);
    }

    private static boolean verify(List<Block> code) {
        for (Block block : code) {
            for (Block sux : block.getSuccessors()) {
                assert code.contains(sux) : "missing successor from: " + block + "to: " + sux;
            }
            for (Block pred : block.getPredecessors()) {
                assert code.contains(pred) : "missing predecessor from: " + block + "to: " + pred;
            }
        }

        return true;
    }
}
