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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.*;
import com.oracle.graal.lir.phases.*;

/** Phase 7: assign register numbers back to LIR */
final class AssignLocations extends AllocationPhase {

    private final LinearScan allocator;

    AssignLocations(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory) {
        assignLocations();
    }

    /**
     * Assigns the allocated location for an LIR instruction operand back into the instruction.
     *
     * @param operand an LIR instruction operand
     * @param opId the id of the LIR instruction using {@code operand}
     * @param mode the usage mode for {@code operand} by the instruction
     * @return the location assigned for the operand
     */
    private Value colorLirOperand(Variable operand, int opId, OperandMode mode) {
        Interval interval = allocator.intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            if (DetailedAsserts.getValue()) {
                AbstractBlockBase<?> block = allocator.blockForId(opId);
                if (block.getSuccessorCount() <= 1 && opId == allocator.getLastLirInstructionId(block)) {
                    /*
                     * Check if spill moves could have been appended at the end of this block, but
                     * before the branch instruction. So the split child information for this branch
                     * would be incorrect.
                     */
                    LIRInstruction instr = allocator.ir.getLIRforBlock(block).get(allocator.ir.getLIRforBlock(block).size() - 1);
                    if (instr instanceof StandardOp.JumpOp) {
                        if (allocator.getBlockData(block).liveOut.get(allocator.operandNumber(operand))) {
                            assert false : String.format(
                                            "can't get split child for the last branch of a block because the information would be incorrect (moves are inserted before the branch in resolveDataFlow) block=%s, instruction=%s, operand=%s",
                                            block, instr, operand);
                        }
                    }
                }
            }

            /*
             * Operands are not changed when an interval is split during allocation, so search the
             * right interval here.
             */
            interval = allocator.splitChildAtOpId(interval, opId, mode);
        }

        if (isIllegal(interval.location()) && interval.canMaterialize()) {
            assert mode != OperandMode.DEF;
            return interval.getMaterializedValue();
        }
        return interval.location();
    }

    /**
     * @param op
     * @param operand
     * @param valueMode
     * @param flags
     * @see InstructionValueProcedure#doValue(LIRInstruction, Value, OperandMode, EnumSet)
     */
    private Value debugInfoProcedure(LIRInstruction op, Value operand, OperandMode valueMode, EnumSet<OperandFlag> flags) {
        if (isVirtualStackSlot(operand)) {
            return operand;
        }
        int tempOpId = op.id();
        OperandMode mode = OperandMode.USE;
        AbstractBlockBase<?> block = allocator.blockForId(tempOpId);
        if (block.getSuccessorCount() == 1 && tempOpId == allocator.getLastLirInstructionId(block)) {
            /*
             * Generating debug information for the last instruction of a block. If this instruction
             * is a branch, spill moves are inserted before this branch and so the wrong operand
             * would be returned (spill moves at block boundaries are not considered in the live
             * ranges of intervals).
             * 
             * Solution: use the first opId of the branch target block instead.
             */
            final LIRInstruction instr = allocator.ir.getLIRforBlock(block).get(allocator.ir.getLIRforBlock(block).size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                if (allocator.getBlockData(block).liveOut.get(allocator.operandNumber(operand))) {
                    tempOpId = allocator.getFirstLirInstructionId(block.getSuccessors().iterator().next());
                    mode = OperandMode.DEF;
                }
            }
        }

        /*
         * Get current location of operand. The operand must be live because debug information is
         * considered when building the intervals if the interval is not live, colorLirOperand will
         * cause an assert on failure.
         */
        Value result = colorLirOperand((Variable) operand, tempOpId, mode);
        assert !allocator.hasCall(tempOpId) || isStackSlotValue(result) || isConstant(result) || !allocator.isCallerSave(result) : "cannot have caller-save register operands at calls";
        return result;
    }

    private void computeDebugInfo(final LIRInstruction op, LIRFrameState info) {
        info.forEachState(op, this::debugInfoProcedure);
    }

    private void assignLocations(List<LIRInstruction> instructions) {
        int numInst = instructions.size();
        boolean hasDead = false;

        InstructionValueProcedure assignProc = (op, operand, mode, flags) -> isVariable(operand) ? colorLirOperand((Variable) operand, op.id(), mode) : operand;
        for (int j = 0; j < numInst; j++) {
            final LIRInstruction op = instructions.get(j);
            if (op == null) {
                /*
                 * this can happen when spill-moves are removed in eliminateSpillMoves
                 */
                hasDead = true;
                continue;
            }

            // remove useless moves
            MoveOp move = null;
            if (op instanceof MoveOp) {
                move = (MoveOp) op;
                AllocatableValue result = move.getResult();
                if (isVariable(result) && allocator.isMaterialized(result, op.id(), OperandMode.DEF)) {
                    /*
                     * This happens if a materializable interval is originally not spilled but then
                     * kicked out in LinearScanWalker.splitForSpilling(). When kicking out such an
                     * interval this move operation was already generated.
                     */
                    instructions.set(j, null);
                    hasDead = true;
                    continue;
                }
            }

            op.forEachInput(assignProc);
            op.forEachAlive(assignProc);
            op.forEachTemp(assignProc);
            op.forEachOutput(assignProc);

            // compute reference map and debug information
            op.forEachState((inst, state) -> computeDebugInfo(inst, state));

            // remove useless moves
            if (move != null) {
                if (move.getInput().equals(move.getResult())) {
                    instructions.set(j, null);
                    hasDead = true;
                }
            }
        }

        if (hasDead) {
            // Remove null values from the list.
            instructions.removeAll(Collections.singleton(null));
        }
    }

    private void assignLocations() {
        try (Indent indent = Debug.logAndIndent("assign locations")) {
            for (AbstractBlockBase<?> block : allocator.sortedBlocks) {
                try (Indent indent2 = Debug.logAndIndent("assign locations in block B%d", block.getId())) {
                    assignLocations(allocator.ir.getLIRforBlock(block));
                }
            }
        }
    }
}
