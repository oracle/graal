/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import com.oracle.graal.debug.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
class LinearScanResolveDataFlowPhase extends AllocationPhase {

    protected final LinearScan allocator;

    LinearScanResolveDataFlowPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        resolveDataFlow();
    }

    void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        assert moveResolver.checkEmpty();
        assert midBlock == null ||
                        (midBlock.getPredecessorCount() == 1 && midBlock.getSuccessorCount() == 1 && midBlock.getPredecessors().get(0).equals(fromBlock) && midBlock.getSuccessors().get(0).equals(
                                        toBlock));

        int toBlockFirstInstructionId = allocator.getFirstLirInstructionId(toBlock);
        int fromBlockLastInstructionId = allocator.getLastLirInstructionId(fromBlock) + 1;
        int numOperands = allocator.operandSize();
        BitSet liveAtEdge = allocator.getBlockData(toBlock).liveIn;

        // visit all variables for which the liveAtEdge bit is set
        for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
            assert operandNum < numOperands : "live information set for not exisiting interval";
            assert allocator.getBlockData(fromBlock).liveOut.get(operandNum) && allocator.getBlockData(toBlock).liveIn.get(operandNum) : "interval not live at this edge";

            Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(operandNum), fromBlockLastInstructionId, LIRInstruction.OperandMode.DEF);
            Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(operandNum), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);

            if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                // need to insert move instruction
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

    void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, MoveResolver moveResolver) {
        if (fromBlock.getSuccessorCount() <= 1) {
            if (Debug.isLogEnabled()) {
                Debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
            }

            List<LIRInstruction> instructions = allocator.ir.getLIRforBlock(fromBlock);
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                // insert moves before branch
                moveResolver.setInsertPosition(instructions, instructions.size() - 1);
            } else {
                moveResolver.setInsertPosition(instructions, instructions.size());
            }

        } else {
            if (Debug.isLogEnabled()) {
                Debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());
            }

            if (DetailedAsserts.getValue()) {
                assert allocator.ir.getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                /*
                 * Because the number of predecessor edges matches the number of successor edges,
                 * blocks which are reached by switch statements may have be more than one
                 * predecessor but it will be guaranteed that all predecessors will be the same.
                 */
                for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                    assert fromBlock == predecessor : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(allocator.ir.getLIRforBlock(toBlock), 1);
        }
    }

    /**
     * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals that
     * have been split.
     */
    void resolveDataFlow() {
        try (Indent indent = Debug.logAndIndent("resolve data flow")) {

            int numBlocks = allocator.blockCount();
            MoveResolver moveResolver = allocator.createMoveResolver();
            BitSet blockCompleted = new BitSet(numBlocks);
            BitSet alreadyResolved = new BitSet(numBlocks);

            for (AbstractBlockBase<?> block : allocator.sortedBlocks) {

                // check if block has only one predecessor and only one successor
                if (block.getPredecessorCount() == 1 && block.getSuccessorCount() == 1) {
                    List<LIRInstruction> instructions = allocator.ir.getLIRforBlock(block);
                    assert instructions.get(0) instanceof StandardOp.LabelOp : "block must start with label";
                    assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";

                    // check if block is empty (only label and branch)
                    if (instructions.size() == 2) {
                        AbstractBlockBase<?> pred = block.getPredecessors().iterator().next();
                        AbstractBlockBase<?> sux = block.getSuccessors().iterator().next();

                        // prevent optimization of two consecutive blocks
                        if (!blockCompleted.get(pred.getLinearScanNumber()) && !blockCompleted.get(sux.getLinearScanNumber())) {
                            if (Debug.isLogEnabled()) {
                                Debug.log(" optimizing empty block B%d (pred: B%d, sux: B%d)", block.getId(), pred.getId(), sux.getId());
                            }

                            blockCompleted.set(block.getLinearScanNumber());

                            /*
                             * Directly resolve between pred and sux (without looking at the empty
                             * block between).
                             */
                            resolveCollectMappings(pred, sux, block, moveResolver);
                            if (moveResolver.hasMappings()) {
                                moveResolver.setInsertPosition(instructions, 1);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }

            for (AbstractBlockBase<?> fromBlock : allocator.sortedBlocks) {
                if (!blockCompleted.get(fromBlock.getLinearScanNumber())) {
                    alreadyResolved.clear();
                    alreadyResolved.or(blockCompleted);

                    for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {

                        /*
                         * Check for duplicate edges between the same blocks (can happen with switch
                         * blocks).
                         */
                        if (!alreadyResolved.get(toBlock.getLinearScanNumber())) {
                            if (Debug.isLogEnabled()) {
                                Debug.log("processing edge between B%d and B%d", fromBlock.getId(), toBlock.getId());
                            }

                            alreadyResolved.set(toBlock.getLinearScanNumber());

                            // collect all intervals that have been split between
                            // fromBlock and toBlock
                            resolveCollectMappings(fromBlock, toBlock, null, moveResolver);
                            if (moveResolver.hasMappings()) {
                                resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }

        }
    }
}
