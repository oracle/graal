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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.ssa.SSAUtil.PhiValueVisitor;
import com.oracle.graal.lir.ssi.*;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
final class TraceLinearScanResolveDataFlowPhase extends AllocationPhase {

    protected final TraceLinearScan allocator;

    protected TraceLinearScanResolveDataFlowPhase(TraceLinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        resolveDataFlow();
    }

    protected void resolveCollectMappings0(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
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

            List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(fromBlock);
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
                assert allocator.getLIR().getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                /*
                 * Because the number of predecessor edges matches the number of successor edges,
                 * blocks which are reached by switch statements may have be more than one
                 * predecessor but it will be guaranteed that all predecessors will be the same.
                 */
                for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                    assert fromBlock == predecessor : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(allocator.getLIR().getLIRforBlock(toBlock), 1);
        }
    }

    /**
     * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals that
     * have been split.
     */
    protected void resolveDataFlow() {
        try (Indent indent = Debug.logAndIndent("resolve data flow")) {

            MoveResolver moveResolver = allocator.createMoveResolver();
            BitSet blockCompleted = new BitSet(allocator.blockCount());

            BitSet alreadyResolved = new BitSet(allocator.blockCount());
            for (AbstractBlockBase<?> fromBlock : allocator.sortedBlocks()) {
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

    protected void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        assert midBlock == null;
        if (containedInTrace(fromBlock) && containedInTrace(toBlock)) {
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
            SSIUtil.forEachValuePair(allocator.getLIR(), toBlock, fromBlock, new MyPhiValueVisitor(moveResolver, toBlock, fromBlock));
        }

    }

    private boolean containedInTrace(AbstractBlockBase<?> block) {
        return allocator.sortedBlocks().contains(block);
    }

    private static final DebugMetric numSSIResolutionMoves = Debug.metric("SSI LSRA[numSSIResolutionMoves]");
    private static final DebugMetric numStackToStackMoves = Debug.metric("SSI LSRA[numStackToStackMoves]");

    private class MyPhiValueVisitor implements PhiValueVisitor {
        final MoveResolver moveResolver;
        final int toId;
        final int fromId;

        public MyPhiValueVisitor(MoveResolver moveResolver, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock) {
            this.moveResolver = moveResolver;
            toId = allocator.getFirstLirInstructionId(toBlock);
            fromId = allocator.getLastLirInstructionId(fromBlock);
            assert fromId >= 0;
        }

        public void visit(Value phiIn, Value phiOut) {
            assert !isRegister(phiOut) : "Out is a register: " + phiOut;
            assert !isRegister(phiIn) : "In is a register: " + phiIn;
            if (Value.ILLEGAL.equals(phiIn)) {
                // The value not needed in this branch.
                return;
            }
            if (isVirtualStackSlot(phiIn) && isVirtualStackSlot(phiOut) && phiIn.equals(phiOut)) {
                // no need to handle virtual stack slots
                return;
            }
            Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toId, LIRInstruction.OperandMode.DEF);
            if (isConstantValue(phiOut)) {
                numSSIResolutionMoves.increment();
                moveResolver.addMapping(asConstant(phiOut), toInterval);
            } else {
                Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), fromId, LIRInstruction.OperandMode.DEF);
                if (fromInterval != toInterval) {
                    numSSIResolutionMoves.increment();
                    if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                        moveResolver.addMapping(fromInterval, toInterval);
                    } else {
                        numStackToStackMoves.increment();
                        moveResolver.addMapping(fromInterval, toInterval);
                    }
                }
            }
        }
    }

}
