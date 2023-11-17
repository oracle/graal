/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.lsra;

import static jdk.vm.ci.code.ValueUtil.isIllegal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Phase 7: Assign register numbers back to LIR.
 */
public class LinearScanAssignLocationsPhase extends LinearScanAllocationPhase {

    protected final LinearScan allocator;

    public LinearScanAssignLocationsPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationPhase.AllocationContext context) {
        assignLocations();
    }

    /**
     * Assigns the allocated location for an LIR instruction operand back into the instruction.
     *
     * @param op current {@link LIRInstruction}
     * @param operand an LIR instruction operand
     * @param mode the usage mode for {@code operand} by the instruction
     * @return the location assigned for the operand
     */
    protected Value colorLirOperand(LIRInstruction op, Variable operand, LIRInstruction.OperandMode mode) {
        int opId = op.id();
        Interval interval = allocator.intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            if (allocator.detailedAsserts) {
                BasicBlock<?> block = allocator.blockForId(opId);
                if (block.getSuccessorCount() <= 1 && opId == allocator.getLastLirInstructionId(block)) {
                    /*
                     * Check if spill moves could have been appended at the end of this block, but
                     * before the branch instruction. So the split child information for this branch
                     * would be incorrect.
                     */
                    LIRInstruction instr = allocator.getLIR().getLIRforBlock(block).get(allocator.getLIR().getLIRforBlock(block).size() - 1);
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
            assert mode != OperandMode.DEF : mode;
            return new ConstantValue(interval.kind(), interval.getMaterializedValue());
        }
        return interval.location();
    }

    private Value debugInfoProcedure(LIRInstruction op, Value operand) {
        if (LIRValueUtil.isVirtualStackSlot(operand) || ValueUtil.isRegister(operand)) {
            return operand;
        }
        int tempOpId = op.id();
        LIRInstruction.OperandMode mode = LIRInstruction.OperandMode.USE;
        BasicBlock<?> block = allocator.blockForId(tempOpId);
        if (block.getSuccessorCount() == 1 && tempOpId == allocator.getLastLirInstructionId(block)) {
            /*
             * Generating debug information for the last instruction of a block. If this instruction
             * is a branch, spill moves are inserted before this branch and so the wrong operand
             * would be returned (spill moves at block boundaries are not considered in the live
             * ranges of intervals).
             *
             * Solution: use the first opId of the branch target block instead.
             */
            final LIRInstruction instr = allocator.getLIR().getLIRforBlock(block).get(allocator.getLIR().getLIRforBlock(block).size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                if (allocator.getBlockData(block).liveOut.get(allocator.operandNumber(operand))) {
                    tempOpId = allocator.getFirstLirInstructionId(block.getSuccessorAt(0));
                    mode = LIRInstruction.OperandMode.DEF;
                }
            }
        }

        /*
         * Get current location of operand. The operand must be live because debug information is
         * considered when building the intervals if the interval is not live, colorLirOperand will
         * cause an assert on failure.
         */
        Value result = colorLirOperand(op, LIRValueUtil.asVariable(operand), mode);
        assert !allocator.hasCall(tempOpId) || LIRValueUtil.isStackSlotValue(result) || LIRValueUtil.isJavaConstant(result) ||
                        !allocator.isCallerSave(result) : "cannot have caller-save register operands at calls";
        return result;
    }

    private void assignLocations(ArrayList<LIRInstruction> instructions) {
        int numInst = instructions.size();
        boolean hasDead = false;

        for (int j = 0; j < numInst; j++) {
            final LIRInstruction op = instructions.get(j);
            if (op == null) {
                /*
                 * this can happen when spill-moves are removed in eliminateSpillMoves
                 */
                hasDead = true;
            } else {
                try {
                    LIRInstruction newOp = assignLocations(op);
                    if (op != newOp) {
                        instructions.set(j, newOp);
                    }
                    if (newOp == null) {
                        hasDead = true;
                    }
                } catch (GraalError e) {
                    throw e.addContext("lir instruction", "@" + op.id() + " " + op.getClass().getName() + " " + op);
                }
            }
        }

        if (hasDead) {
            // Remove null values from the list.
            instructions.removeAll(Collections.singleton(null));
        }
    }

    private final InstructionValueProcedure assignProc = new InstructionValueProcedure() {
        @Override
        public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            if (LIRValueUtil.isVariable(value)) {
                Value location = colorLirOperand(instruction, LIRValueUtil.asVariable(value), mode);
                if (LIRValueUtil.isCast(value)) {
                    GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of CastValue");
                    // return the same location, but with the cast's kind.
                    CastValue cast = (CastValue) value;
                    return LIRValueUtil.changeValueKind(location, cast.getValueKind(), true);
                }
                return location;
            } else if (LIRValueUtil.isCast(value)) {
                GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of CastValue");
                // strip CastValue: return underlying value, but with the cast's kind.
                CastValue cast = (CastValue) value;
                return LIRValueUtil.changeValueKind(cast.underlyingValue(), cast.getValueKind(), false);
            }
            return value;
        }
    };
    private final InstructionValueProcedure debugInfoProc = new InstructionValueProcedure() {
        @Override
        public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            return debugInfoProcedure(instruction, value);
        }
    };

    /**
     * Assigns the operand of an {@link LIRInstruction}.
     *
     * @param inputOp The {@link LIRInstruction} that should be colored.
     * @return the {@link LIRInstruction} that should be emitted. A {@code null} return deletes the
     *         instruction.
     */
    protected LIRInstruction assignLocations(LIRInstruction inputOp) {
        assert inputOp != null;
        LIRInstruction op = inputOp;

        // remove useless moves
        if (StandardOp.MoveOp.isMoveOp(op)) {
            AllocatableValue result = StandardOp.MoveOp.asMoveOp(op).getResult();
            if (LIRValueUtil.isVariable(result) && allocator.isMaterialized(result, op.id(), LIRInstruction.OperandMode.DEF)) {
                /*
                 * This happens if a materializable interval is originally not spilled but then
                 * kicked out in LinearScanWalker.splitForSpilling(). When kicking out such an
                 * interval this move operation was already generated.
                 */
                return null;
            }
        }

        if (StandardOp.ValueMoveOp.isValueMoveOp(op)) {
            StandardOp.ValueMoveOp valueMoveOp = StandardOp.ValueMoveOp.asValueMoveOp(op);
            AllocatableValue input = valueMoveOp.getInput();
            if (LIRValueUtil.isVariable(input)) {
                Value inputOperand = colorLirOperand(op, LIRValueUtil.asVariable(input), LIRInstruction.OperandMode.USE);
                if (inputOperand instanceof ConstantValue) {
                    // Replace the ValueMoveOp with a constant materialization
                    op = allocator.getSpillMoveFactory().createLoad(valueMoveOp.getResult(), ((ConstantValue) inputOperand).getConstant());
                }
            }
        }

        op.forEachInput(assignProc);
        op.forEachAlive(assignProc);
        op.forEachTemp(assignProc);
        op.forEachOutput(assignProc);

        // compute reference map and debug information
        op.forEachState(debugInfoProc);

        // remove useless moves
        if (StandardOp.ValueMoveOp.isValueMoveOp(op)) {
            StandardOp.ValueMoveOp move = StandardOp.ValueMoveOp.asValueMoveOp(op);
            if (move.getInput().equals(move.getResult())) {
                return null;
            }
        }
        return op;
    }

    @SuppressWarnings("try")
    private void assignLocations() {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("assign locations")) {
            for (int blockId : allocator.sortedBlocks()) {
                BasicBlock<?> block = allocator.getLIR().getBlockById(blockId);
                try (Indent indent2 = debug.logAndIndent("assign locations in block B%d", block.getId())) {
                    assignLocations(allocator.getLIR().getLIRforBlock(block));
                }
            }
        }
    }
}
