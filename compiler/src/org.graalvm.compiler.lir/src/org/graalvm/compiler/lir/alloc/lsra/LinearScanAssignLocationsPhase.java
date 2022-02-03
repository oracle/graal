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
package org.graalvm.compiler.lir.alloc.lsra;

import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isCast;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.CastValue;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.MoveOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlot;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlotAlias;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Phase 7: Assign register numbers back to LIR.
 */
public class LinearScanAssignLocationsPhase extends LinearScanAllocationPhase {

    protected final LinearScan allocator;

    public LinearScanAssignLocationsPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
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
    protected Value colorLirOperand(LIRInstruction op, Variable operand, OperandMode mode) {
        int opId = op.id();
        Interval interval = allocator.intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            if (allocator.detailedAsserts) {
                AbstractBlockBase<?> block = allocator.blockForId(opId);
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
            assert mode != OperandMode.DEF;
            return new ConstantValue(interval.kind(), interval.getMaterializedValue());
        }
        return interval.location();
    }

    private Value debugInfoProcedure(LIRInstruction op, Value operand) {
        if (isVirtualStackSlot(operand) || ValueUtil.isRegister(operand)) {
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
            final LIRInstruction instr = allocator.getLIR().getLIRforBlock(block).get(allocator.getLIR().getLIRforBlock(block).size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                if (allocator.getBlockData(block).liveOut.get(allocator.operandNumber(operand))) {
                    tempOpId = allocator.getFirstLirInstructionId(block.getSuccessors()[0]);
                    mode = OperandMode.DEF;
                }
            }
        }

        /*
         * Get current location of operand. The operand must be live because debug information is
         * considered when building the intervals if the interval is not live, colorLirOperand will
         * cause an assert on failure.
         */
        Value result = colorLirOperand(op, asVariable(operand), mode);
        assert !allocator.hasCall(tempOpId) || isStackSlotValue(result) || isJavaConstant(result) || !allocator.isCallerSave(result) : "cannot have caller-save register operands at calls";
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

    /**
     * Returns new value with the provided ValueKind.
     */
    private static Value changeValueKind(Value value, ValueKind<?> newKind, boolean allowVirtual) {
        if (isRegister(value)) {
            return ((RegisterValue) value).getRegister().asValue(newKind);
        } else if (value instanceof StackSlot) {
            StackSlot stackSlot = (StackSlot) value;
            return StackSlot.get(newKind, stackSlot.getRawOffset(), stackSlot.getRawAddFrameSize());
        } else if (allowVirtual && value instanceof SimpleVirtualStackSlot) {
            SimpleVirtualStackSlot stackSlot = (SimpleVirtualStackSlot) value;
            return new SimpleVirtualStackSlotAlias(newKind, stackSlot);
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    private final InstructionValueProcedure assignProc = new InstructionValueProcedure() {
        @Override
        public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isVariable(value)) {
                Value location = colorLirOperand(instruction, asVariable(value), mode);
                if (isCast(value)) {
                    GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of CastValue");
                    // return the same location, but with the cast's kind.
                    CastValue cast = (CastValue) value;
                    return changeValueKind(location, cast.getValueKind(), true);
                }
                return location;
            } else if (isCast(value)) {
                GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of CastValue");
                // strip CastValue: return underlying value, but with the cast's kind.
                CastValue cast = (CastValue) value;
                return changeValueKind(cast.underlyingValue(), cast.getValueKind(), false);
            }
            return value;
        }
    };
    private final InstructionValueProcedure debugInfoProc = new InstructionValueProcedure() {
        @Override
        public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
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
        if (MoveOp.isMoveOp(op)) {
            AllocatableValue result = MoveOp.asMoveOp(op).getResult();
            if (isVariable(result) && allocator.isMaterialized(result, op.id(), OperandMode.DEF)) {
                /*
                 * This happens if a materializable interval is originally not spilled but then
                 * kicked out in LinearScanWalker.splitForSpilling(). When kicking out such an
                 * interval this move operation was already generated.
                 */
                return null;
            }
        }

        if (ValueMoveOp.isValueMoveOp(op)) {
            ValueMoveOp valueMoveOp = ValueMoveOp.asValueMoveOp(op);
            AllocatableValue input = valueMoveOp.getInput();
            if (isVariable(input)) {
                Value inputOperand = colorLirOperand(op, asVariable(input), OperandMode.USE);
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
        if (ValueMoveOp.isValueMoveOp(op)) {
            ValueMoveOp move = ValueMoveOp.asValueMoveOp(op);
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
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                try (Indent indent2 = debug.logAndIndent("assign locations in block B%d", block.getId())) {
                    assignLocations(allocator.getLIR().getLIRforBlock(block));
                }
            }
        }
    }
}
