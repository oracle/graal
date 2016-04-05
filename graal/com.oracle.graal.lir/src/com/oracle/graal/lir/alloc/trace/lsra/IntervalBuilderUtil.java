/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace.lsra;

import static com.oracle.graal.lir.LIRValueUtil.asVariable;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.ValueUtil.asRegisterValue;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.EnumSet;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.LoadConstantOp;
import com.oracle.graal.lir.StandardOp.ValueMoveOp;
import com.oracle.graal.lir.ValueProcedure;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.alloc.trace.lsra.TraceInterval.RegisterPriority;
import com.oracle.graal.lir.alloc.trace.lsra.TraceInterval.SpillState;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

public final class IntervalBuilderUtil {

    protected static void setHint(final LIRInstruction op, TraceInterval to, IntervalHint from) {
        IntervalHint currentHint = to.locationHint(false);
        if (currentHint == null) {
            /*
             * Update hint if there was none or if the hint interval starts after the hinted
             * interval.
             */
            to.setLocationHint(from);
            if (Debug.isLogEnabled()) {
                Debug.log("operation at opId %d: added hint from interval %s to %s", op.id(), from, to);
            }
        }
    }

    protected static void numberInstruction(IntervalData intervalData, AbstractBlockBase<?> block, LIRInstruction op, int index) {
        int opId = index << 1;
        assert op.id() == -1 || op.id() == opId : "must match";
        op.setId(opId);
        intervalData.putOpIdMaps(index, op, block);
        assert intervalData.instructionForId(opId) == op : "must match";
    }

    protected static void visitOutput(IntervalData intervalData, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags, boolean neverSpillConstants,
                    MoveFactory spillMoveFactory) {
        if (TraceLinearScan.isVariableOrRegister(operand)) {
            addDef(intervalData, (AllocatableValue) operand, op, registerPriorityOfOutputOperand(op), operand.getLIRKind(), neverSpillConstants, spillMoveFactory);
            addRegisterHint(intervalData, op, operand, mode, flags, true);
        }
    }

    protected static void visitTemp(IntervalData intervalData, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (TraceLinearScan.isVariableOrRegister(operand)) {
            addTemp(intervalData, (AllocatableValue) operand, op.id(), RegisterPriority.MustHaveRegister, operand.getLIRKind());
            addRegisterHint(intervalData, op, operand, mode, flags, false);
        }
    }

    protected static void visitAlive(IntervalData intervalData, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (TraceLinearScan.isVariableOrRegister(operand)) {
            RegisterPriority p = registerPriorityOfInputOperand(flags);
            int opId = op.id();
            int blockFrom = 0;
            addUse(intervalData, (AllocatableValue) operand, blockFrom, opId + 1, p, operand.getLIRKind());
            addRegisterHint(intervalData, op, operand, mode, flags, false);
        }
    }

    protected static void visitInput(IntervalData intervalData, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (TraceLinearScan.isVariableOrRegister(operand)) {
            int opId = op.id();
            RegisterPriority p = registerPriorityOfInputOperand(flags);
            int blockFrom = 0;
            addUse(intervalData, (AllocatableValue) operand, blockFrom, opId, p, operand.getLIRKind());
            addRegisterHint(intervalData, op, operand, mode, flags, false);
        }
    }

    protected static void visitState(IntervalData intervalData, LIRInstruction op, Value operand) {
        if (TraceLinearScan.isVariableOrRegister(operand)) {
            int opId = op.id();
            int blockFrom = 0;
            addUse(intervalData, (AllocatableValue) operand, blockFrom, opId + 1, RegisterPriority.None, operand.getLIRKind());
        }
    }

    protected static void visitCallerSavedRegisters(IntervalData intervalData, Register[] callerSaveRegs, final int opId) {
        for (Register r : callerSaveRegs) {
            if (intervalData.attributes(r).isAllocatable()) {
                addTemp(intervalData, r.asValue(), opId, RegisterPriority.None, LIRKind.Illegal);
            }
        }
        if (Debug.isLogEnabled()) {
            Debug.log("operation destroys all caller-save registers");
        }
    }

    private static void addUse(IntervalData intervalData, AllocatableValue operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
        if (!intervalData.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            addFixedUse(intervalData, asRegisterValue(operand), from, to);
        } else {
            assert isVariable(operand) : operand;
            addVariableUse(intervalData, asVariable(operand), from, to, registerPriority, kind);
        }
    }

    private static void addFixedUse(IntervalData intervalData, RegisterValue reg, int from, int to) {
        FixedInterval interval = intervalData.getOrCreateFixedInterval(reg);
        interval.addRange(from, to);
        if (Debug.isLogEnabled()) {
            Debug.log("add fixed use: %s, at %d", interval, to);
        }
    }

    private static void addVariableUse(IntervalData intervalData, Variable operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
        TraceInterval interval = intervalData.getOrCreateInterval(operand);

        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        interval.addRange(from, to);

        // Register use position at even instruction id.
        interval.addUsePos(to & ~1, registerPriority);

        if (Debug.isLogEnabled()) {
            Debug.log("add use: %s, at %d (%s)", interval, to, registerPriority.name());
        }
    }

    private static void addTemp(IntervalData intervalData, AllocatableValue operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
        if (!intervalData.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            addFixedTemp(intervalData, asRegisterValue(operand), tempPos);
        } else {
            assert isVariable(operand) : operand;
            addVariableTemp(intervalData, asVariable(operand), tempPos, registerPriority, kind);
        }
    }

    private static void addFixedTemp(IntervalData intervalData, RegisterValue reg, int tempPos) {
        FixedInterval interval = intervalData.getOrCreateFixedInterval(reg);
        interval.addRange(tempPos, tempPos + 1);
        if (Debug.isLogEnabled()) {
            Debug.log("add fixed temp: %s, at %d", interval, tempPos);
        }
    }

    private static void addVariableTemp(IntervalData intervalData, Variable operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
        TraceInterval interval = intervalData.getOrCreateInterval(operand);

        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        if (interval.isEmpty()) {
            interval.addRange(tempPos, tempPos + 1);
        } else if (interval.from() > tempPos) {
            interval.setFrom(tempPos);
        }

        interval.addUsePos(tempPos, registerPriority);
        interval.addMaterializationValue(null);

        if (Debug.isLogEnabled()) {
            Debug.log("add temp: %s tempPos %d (%s)", interval, tempPos, RegisterPriority.MustHaveRegister.name());
        }
    }

    private static void addDef(IntervalData intervalData, AllocatableValue operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind, boolean neverSpillConstants,
                    MoveFactory spillMoveFactory) {
        if (!intervalData.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            addFixedDef(intervalData, asRegisterValue(operand), op);
        } else {
            assert isVariable(operand) : operand;
            addVariableDef(intervalData, asVariable(operand), op, registerPriority, kind, neverSpillConstants, spillMoveFactory);
        }
    }

    private static void addFixedDef(IntervalData intervalData, RegisterValue reg, LIRInstruction op) {
        FixedInterval interval = intervalData.getOrCreateFixedInterval(reg);
        int defPos = op.id();
        if (interval.from() <= defPos) {
            /*
             * Update the starting point (when a range is first created for a use, its start is the
             * beginning of the current block until a def is encountered).
             */
            interval.setFrom(defPos);

        } else {
            /*
             * Dead value - make vacuous interval also add register priority for dead intervals
             */
            interval.addRange(defPos, defPos + 1);
            if (Debug.isLogEnabled()) {
                Debug.log("Warning: def of operand %s at %d occurs without use", reg, defPos);
            }
        }
        if (Debug.isLogEnabled()) {
            Debug.log("add fixed def: %s, at %d", interval, defPos);
        }
    }

    private static void addVariableDef(IntervalData intervalData, Variable operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind, boolean neverSpillConstants,
                    MoveFactory spillMoveFactory) {
        int defPos = op.id();

        TraceInterval interval = intervalData.getOrCreateInterval(operand);

        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        if (interval.isEmpty()) {
            /*
             * Dead value - make vacuous interval also add register priority for dead intervals
             */
            interval.addRange(defPos, defPos + 1);
            if (Debug.isLogEnabled()) {
                Debug.log("Warning: def of operand %s at %d occurs without use", operand, defPos);
            }
        } else {
            /*
             * Update the starting point (when a range is first created for a use, its start is the
             * beginning of the current block until a def is encountered).
             */
            interval.setFrom(defPos);
        }
        if (!(op instanceof LabelOp)) {
            // no use positions for labels
            interval.addUsePos(defPos, registerPriority);
        }

        changeSpillDefinitionPos(intervalData, op, operand, interval, defPos);
        if (registerPriority == RegisterPriority.None && interval.spillState().ordinal() <= SpillState.StartInMemory.ordinal() && isStackSlot(operand)) {
            // detection of method-parameters and roundfp-results
            interval.setSpillState(SpillState.StartInMemory);
        }
        interval.addMaterializationValue(getMaterializedValue(op, operand, interval, neverSpillConstants, spillMoveFactory));

        if (Debug.isLogEnabled()) {
            Debug.log("add def: %s defPos %d (%s)", interval, defPos, registerPriority.name());
        }
    }

    private static void addRegisterHint(IntervalData intervalData, final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        if (flags.contains(OperandFlag.HINT) && TraceLinearScan.isVariableOrRegister(targetValue)) {

            ValueProcedure registerHintProc = new ValueProcedure() {
                @Override
                public Value doValue(Value registerHint, OperandMode valueMode, EnumSet<OperandFlag> valueFlags) {
                    if (TraceLinearScan.isVariableOrRegister(registerHint)) {
                        /*
                         * TODO (je): clean up
                         */
                        final AllocatableValue fromValue;
                        final AllocatableValue toValue;
                        /* hints always point from def to use */
                        if (hintAtDef) {
                            fromValue = (AllocatableValue) registerHint;
                            toValue = (AllocatableValue) targetValue;
                        } else {
                            fromValue = (AllocatableValue) targetValue;
                            toValue = (AllocatableValue) registerHint;
                        }
                        Debug.log("addRegisterHint %s to %s", fromValue, toValue);
                        final TraceInterval to;
                        final IntervalHint from;
                        if (isRegister(toValue)) {
                            if (isRegister(fromValue)) {
                                // fixed to fixed move
                                return null;
                            }
                            from = getIntervalHint(intervalData, toValue);
                            to = intervalData.getOrCreateInterval(fromValue);
                        } else {
                            to = intervalData.getOrCreateInterval(toValue);
                            from = getIntervalHint(intervalData, fromValue);
                        }

                        to.setLocationHint(from);
                        if (Debug.isLogEnabled()) {
                            Debug.log("operation at opId %d: added hint from interval %s to %s", op.id(), from, to);
                        }

                        return registerHint;
                    }
                    return null;
                }
            };
            op.forEachRegisterHint(targetValue, mode, registerHintProc);
        }
    }

    protected static IntervalHint getIntervalHint(IntervalData intervalData, AllocatableValue from) {
        if (isRegister(from)) {
            return intervalData.getOrCreateFixedInterval(asRegisterValue(from));
        }
        return intervalData.getOrCreateInterval(from);
    }

    /**
     * Eliminates moves from register to stack if the stack slot is known to be correct.
     *
     * @param op
     * @param operand
     */
    private static void changeSpillDefinitionPos(IntervalData intervalData, LIRInstruction op, AllocatableValue operand, TraceInterval interval, int defPos) {
        assert interval.isSplitParent() : "can only be called for split parents";

        switch (interval.spillState()) {
            case NoDefinitionFound:
                // assert interval.spillDefinitionPos() == -1 : "must no be set before";
                interval.setSpillDefinitionPos(defPos);
                if (!(op instanceof LabelOp)) {
                    // Do not update state for labels. This will be done afterwards.
                    interval.setSpillState(SpillState.NoSpillStore);
                }
                break;

            case NoSpillStore:
                assert defPos <= interval.spillDefinitionPos() : "positions are processed in reverse order when intervals are created";
                if (defPos < interval.spillDefinitionPos() - 2) {
                    /*
                     * Second definition found, so no spill optimization possible for this interval.
                     */
                    interval.setSpillState(SpillState.NoOptimization);
                } else {
                    // two consecutive definitions (because of two-operand LIR form)
                    assert intervalData.blockForId(defPos) == intervalData.blockForId(interval.spillDefinitionPos()) : "block must be equal";
                }
                break;

            case NoOptimization:
                // nothing to do
                break;

            default:
                throw new BailoutException("other states not allowed at this time");
        }
    }

    private static boolean optimizeMethodArgument(Value value) {
        /*
         * Object method arguments that are passed on the stack are currently not optimized because
         * this requires that the runtime visits method arguments during stack walking.
         */
        return isStackSlot(value) && asStackSlot(value).isInCallerFrame() && value.getLIRKind().isValue();
    }

    /**
     * Determines the register priority for an instruction's output/result operand.
     */
    private static RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof LabelOp) {
            // skip method header
            return RegisterPriority.None;
        }
        if (op instanceof ValueMoveOp) {
            ValueMoveOp move = (ValueMoveOp) op;
            if (optimizeMethodArgument(move.getInput())) {
                return RegisterPriority.None;
            }
        }

        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Determines the priority which with an instruction's input operand will be allocated a
     * register.
     */
    private static RegisterPriority registerPriorityOfInputOperand(EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.OUTGOING)) {
            return RegisterPriority.None;
        }
        if (flags.contains(OperandFlag.STACK)) {
            return RegisterPriority.ShouldHaveRegister;
        }
        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Returns a value for a interval definition, which can be used for re-materialization.
     *
     * @param op An instruction which defines a value
     * @param operand The destination operand of the instruction
     * @param interval The interval for this defined value.
     * @return Returns the value which is moved to the instruction and which can be reused at all
     *         reload-locations in case the interval of this instruction is spilled. Currently this
     *         can only be a {@link JavaConstant}.
     */
    private static JavaConstant getMaterializedValue(LIRInstruction op, Value operand, TraceInterval interval, boolean neverSpillConstants, MoveFactory spillMoveFactory) {
        if (op instanceof LoadConstantOp) {
            LoadConstantOp move = (LoadConstantOp) op;
            if (move.getConstant() instanceof JavaConstant) {
                if (!neverSpillConstants) {
                    if (!spillMoveFactory.allowConstantToStackMove(move.getConstant())) {
                        return null;
                    }
                    /*
                     * Check if the interval has any uses which would accept an stack location
                     * (priority == ShouldHaveRegister). Rematerialization of such intervals can
                     * result in a degradation, because rematerialization always inserts a constant
                     * load, even if the value is not needed in a register.
                     */
                    UsePosList usePosList = interval.usePosList();
                    int numUsePos = usePosList.size();
                    for (int useIdx = 0; useIdx < numUsePos; useIdx++) {
                        TraceInterval.RegisterPriority priority = usePosList.registerPriority(useIdx);
                        if (priority == TraceInterval.RegisterPriority.ShouldHaveRegister) {
                            return null;
                        }
                    }
                }
                return (JavaConstant) move.getConstant();
            }
        }
        return null;
    }

    /**
     * Adds the range [-1, 0] to all fixed intervals. the register allocator need not handle
     * unhandled fixed intervals.
     */
    protected static void finalizeFixedIntervals(IntervalData intervalData) {
        for (FixedInterval interval : intervalData.fixedIntervals()) {
            if (interval != null) {
                /* We use [-1, 0] to avoid intersection with incoming values. */
                interval.addRange(-1, 0);
            }
        }
    }
}
