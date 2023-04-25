/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir;

import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.INVALID_BLOCK_ID;
import static org.graalvm.compiler.lir.LIR.verifyBlocks;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.cfg.BasicBlock;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;

/**
 * This class performs basic optimizations on the control flow graph after LIR generation.
 */
public final class ControlFlowOptimizer extends PostAllocationOptimizationPhase {

    /**
     * Performs control flow optimizations on the given LIR graph.
     */
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR lir = lirGenRes.getLIR();
        new Optimizer(lir).deleteEmptyBlocks(lir.getBlocks());
    }

    private static final class Optimizer {

        private final LIR lir;

        private Optimizer(LIR lir) {
            this.lir = lir;
        }

        private static final CounterKey BLOCKS_DELETED = DebugContext.counter("BlocksDeleted");

        /**
         * Checks whether a block can be deleted. Only blocks with exactly one successor and an
         * unconditional branch to this successor are eligible.
         *
         * @param block the block checked for deletion
         * @return whether the block can be deleted
         */
        private boolean canDeleteBlock(BasicBlock<?> block) {
            if (block == null || block.getSuccessorCount() != 1 || block.getPredecessorCount() == 0 || block.getSuccessorAt(0) == block) {
                return false;
            }

            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            assert instructions.size() >= 2 : "block must have label and branch";
            assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
            assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "last instruction must always be a branch";
            assert ((StandardOp.JumpOp) instructions.get(instructions.size() - 1)).destination().label() == ((StandardOp.LabelOp) lir.getLIRforBlock(block.getSuccessorAt(0)).get(
                            0)).getLabel() : "branch target must be the successor";

            // Block must have exactly one successor.
            return instructions.size() == 2 && !instructions.get(instructions.size() - 1).hasState() && !block.isExceptionEntry();
        }

        private StandardOp.LabelOp getLabel(BasicBlock<?> block) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
            return (StandardOp.LabelOp) instructions.get(0);
        }

        private void copyAlignment(BasicBlock<?> from, BasicBlock<?> block) {
            if (from.isAligned() && !block.isAligned()) {
                block.setAlign(true);
                getLabel(block).setAlignment(getLabel(from).getAlignment());
            }
        }

        private void deleteEmptyBlocks(int[] blocks) {
            assert verifyBlocks(lir, blocks);
            for (int i = 0; i < blocks.length; i++) {
                BasicBlock<?> block = lir.getBlockById(blocks[i]);
                if (canDeleteBlock(block)) {

                    block.delete();
                    // adjust successor and predecessor lists
                    BasicBlock<?> other = block.getSuccessorAt(0);
                    copyAlignment(block, other);

                    BLOCKS_DELETED.increment(lir.getDebug());
                    blocks[i] = INVALID_BLOCK_ID;
                }
            }
            assert verifyBlocks(lir, blocks);
        }
    }
}
