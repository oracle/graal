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

import static com.oracle.graal.lir.LIRValueUtil.asVariable;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static com.oracle.graal.lir.alloc.trace.TraceLinearScan.isVariableOrRegister;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options.TraceRAshareSpillInformation;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options.TraceRAuseInterTraceHints;
import static jdk.vm.ci.code.ValueUtil.asRegisterValue;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.alloc.ComputeBlockOrder;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.InstructionValueConsumer;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.LoadConstantOp;
import com.oracle.graal.lir.StandardOp.ValueMoveOp;
import com.oracle.graal.lir.ValueConsumer;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.alloc.trace.TraceInterval.RegisterPriority;
import com.oracle.graal.lir.alloc.trace.TraceInterval.SpillState;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.ssi.SSIUtil;

final class TraceLinearScanLifetimeAnalysisPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, MoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig, TraceBuilderResult<?> traceBuilderResult, TraceLinearScan allocator) {
        new Analyser(allocator, traceBuilderResult).analyze();
    }

    private static final class Analyser {
        private static final int DUMP_DURING_ANALYSIS_LEVEL = 4;
        private final TraceLinearScan allocator;
        private final TraceBuilderResult<?> traceBuilderResult;

        /**
         * @param linearScan
         * @param traceBuilderResult
         */
        private Analyser(TraceLinearScan linearScan, TraceBuilderResult<?> traceBuilderResult) {
            allocator = linearScan;
            this.traceBuilderResult = traceBuilderResult;
        }

        private void analyze() {
            numberInstructions();
            allocator.printLir("Before register allocation", true);
            buildIntervals();
        }

        private boolean sameTrace(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
            return traceBuilderResult.getTraceForBlock(b) == traceBuilderResult.getTraceForBlock(a);
        }

        private boolean isAllocatedOrCurrent(AbstractBlockBase<?> currentBlock, AbstractBlockBase<?> other) {
            return traceBuilderResult.getTraceForBlock(other) <= traceBuilderResult.getTraceForBlock(currentBlock);
        }

        private static void setHint(final LIRInstruction op, TraceInterval to, IntervalHint from) {
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

        /**
         * Numbers all instructions in all blocks. The numbering follows the
         * {@linkplain ComputeBlockOrder linear scan order}.
         */
        private void numberInstructions() {

            allocator.initIntervals();

            ValueConsumer setVariableConsumer = (value, mode, flags) -> {
                if (isVariable(value)) {
                    allocator.getOrCreateInterval(asVariable(value));
                }
            };

            // Assign IDs to LIR nodes and build a mapping, lirOps, from ID to LIRInstruction node.
            int numInstructions = 0;
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                numInstructions += allocator.getLIR().getLIRforBlock(block).size();
            }

            // initialize with correct length
            allocator.initOpIdMaps(numInstructions);

            int opId = 0;
            int index = 0;
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                allocator.initBlockData(block);

                List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);

                int numInst = instructions.size();
                for (int j = 0; j < numInst; j++) {
                    LIRInstruction op = instructions.get(j);
                    op.setId(opId);

                    allocator.putOpIdMaps(index, op, block);
                    assert allocator.instructionForId(opId) == op : "must match";

                    op.visitEachTemp(setVariableConsumer);
                    op.visitEachOutput(setVariableConsumer);

                    index++;
                    opId += 2; // numbering of lirOps by two
                }
            }
            assert index == numInstructions : "must match";
            assert (index << 1) == opId : "must match: " + (index << 1);
        }

        private void addUse(AllocatableValue operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
            if (!allocator.isProcessed(operand)) {
                return;
            }
            if (isRegister(operand)) {
                addFixedUse(asRegisterValue(operand), from, to);
            } else {
                assert isVariable(operand) : operand;
                addVariableUse(asVariable(operand), from, to, registerPriority, kind);
            }
        }

        private void addFixedUse(RegisterValue reg, int from, int to) {
            FixedInterval interval = allocator.getOrCreateFixedInterval(reg);
            interval.addRange(from, to);
            if (Debug.isLogEnabled()) {
                Debug.log("add fixed use: %s, at %d", interval, to);
            }
        }

        private void addVariableUse(Variable operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
            TraceInterval interval = allocator.getOrCreateInterval(operand);

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

        private void addTemp(AllocatableValue operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
            if (!allocator.isProcessed(operand)) {
                return;
            }
            if (isRegister(operand)) {
                addFixedTemp(asRegisterValue(operand), tempPos);
            } else {
                assert isVariable(operand) : operand;
                addVariableTemp(asVariable(operand), tempPos, registerPriority, kind);
            }
        }

        private void addFixedTemp(RegisterValue reg, int tempPos) {
            FixedInterval interval = allocator.getOrCreateFixedInterval(reg);
            interval.addRange(tempPos, tempPos + 1);
            if (Debug.isLogEnabled()) {
                Debug.log("add fixed temp: %s, at %d", interval, tempPos);
            }
        }

        private void addVariableTemp(Variable operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
            TraceInterval interval = allocator.getOrCreateInterval(operand);

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

        private void addDef(AllocatableValue operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind) {
            if (!allocator.isProcessed(operand)) {
                return;
            }
            if (isRegister(operand)) {
                addFixedDef(asRegisterValue(operand), op);
            } else {
                assert isVariable(operand) : operand;
                addVariableDef(asVariable(operand), op, registerPriority, kind);
            }
        }

        private void addFixedDef(RegisterValue reg, LIRInstruction op) {
            FixedInterval interval = allocator.getOrCreateFixedInterval(reg);
            int defPos = op.id();
            if (interval.from() <= defPos) {
                /*
                 * Update the starting point (when a range is first created for a use, its start is
                 * the beginning of the current block until a def is encountered).
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

        private void addVariableDef(Variable operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind) {
            int defPos = op.id();

            TraceInterval interval = allocator.getOrCreateInterval(operand);

            if (!kind.equals(LIRKind.Illegal)) {
                interval.setKind(kind);
            }

            if (interval.isEmpty()) {
                /*
                 * Dead value - make vacuous interval also add register priority for dead intervals
                 */
                interval.addRange(defPos, defPos + 1);
                interval.addUsePos(defPos, registerPriority);
                if (Debug.isLogEnabled()) {
                    Debug.log("Warning: def of operand %s at %d occurs without use", operand, defPos);
                }
            } else {
                /*
                 * Update the starting point (when a range is first created for a use, its start is
                 * the beginning of the current block until a def is encountered).
                 */
                interval.setFrom(defPos);
                interval.addUsePos(defPos, registerPriority);
            }

            changeSpillDefinitionPos(op, operand, interval, defPos);
            if (registerPriority == RegisterPriority.None && interval.spillState().ordinal() <= SpillState.StartInMemory.ordinal() && isStackSlot(operand)) {
                // detection of method-parameters and roundfp-results
                interval.setSpillState(SpillState.StartInMemory);
            }
            interval.addMaterializationValue(getMaterializedValue(op, operand, interval));

            if (Debug.isLogEnabled()) {
                Debug.log("add def: %s defPos %d (%s)", interval, defPos, registerPriority.name());
            }
        }

        private void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
            if (flags.contains(OperandFlag.HINT) && TraceLinearScan.isVariableOrRegister(targetValue)) {

                op.forEachRegisterHint(targetValue, mode, (registerHint, valueMode, valueFlags) -> {
                    if (TraceLinearScan.isVariableOrRegister(registerHint)) {
                        AllocatableValue fromValue;
                        AllocatableValue toValue;
                        /* hints always point from def to use */
                        if (hintAtDef) {
                            fromValue = (AllocatableValue) registerHint;
                            toValue = (AllocatableValue) targetValue;
                        } else {
                            fromValue = (AllocatableValue) targetValue;
                            toValue = (AllocatableValue) registerHint;
                        }
                        if (isRegister(toValue)) {
                            /* fixed register: no need for a hint */
                            return null;
                        }

                        TraceInterval to = allocator.getOrCreateInterval(toValue);
                        IntervalHint from = getIntervalHint(fromValue);

                        to.setLocationHint(from);
                        if (Debug.isLogEnabled()) {
                            Debug.log("operation at opId %d: added hint from interval %s to %s", op.id(), from, to);
                        }

                        return registerHint;
                    }
                    return null;
                });
            }
        }

        private IntervalHint getIntervalHint(AllocatableValue from) {
            if (isRegister(from)) {
                return allocator.getOrCreateFixedInterval(asRegisterValue(from));
            }
            return allocator.getOrCreateInterval(from);
        }

        /**
         * Eliminates moves from register to stack if the stack slot is known to be correct.
         *
         * @param op
         * @param operand
         */
        private void changeSpillDefinitionPos(LIRInstruction op, AllocatableValue operand, TraceInterval interval, int defPos) {
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
                         * Second definition found, so no spill optimization possible for this
                         * interval.
                         */
                        interval.setSpillState(SpillState.NoOptimization);
                    } else {
                        // two consecutive definitions (because of two-operand LIR form)
                        assert allocator.blockForId(defPos) == allocator.blockForId(interval.spillDefinitionPos()) : "block must be equal";
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
             * Object method arguments that are passed on the stack are currently not optimized
             * because this requires that the runtime visits method arguments during stack walking.
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

        @SuppressWarnings("try")
        private void buildIntervals() {

            try (Indent indent = Debug.logAndIndent("build intervals")) {
                InstructionValueConsumer outputConsumer = (op, operand, mode, flags) -> {
                    if (TraceLinearScan.isVariableOrRegister(operand)) {
                        addDef((AllocatableValue) operand, op, registerPriorityOfOutputOperand(op), operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, true);
                    }
                };

                InstructionValueConsumer tempConsumer = (op, operand, mode, flags) -> {
                    if (TraceLinearScan.isVariableOrRegister(operand)) {
                        addTemp((AllocatableValue) operand, op.id(), RegisterPriority.MustHaveRegister, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                };

                InstructionValueConsumer aliveConsumer = (op, operand, mode, flags) -> {
                    if (TraceLinearScan.isVariableOrRegister(operand)) {
                        RegisterPriority p = registerPriorityOfInputOperand(flags);
                        int opId = op.id();
                        int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                        addUse((AllocatableValue) operand, blockFrom, opId + 1, p, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                };

                InstructionValueConsumer inputConsumer = (op, operand, mode, flags) -> {
                    if (TraceLinearScan.isVariableOrRegister(operand)) {
                        int opId = op.id();
                        RegisterPriority p = registerPriorityOfInputOperand(flags);
                        int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                        addUse((AllocatableValue) operand, blockFrom, opId, p, operand.getLIRKind());
                        addRegisterHint(op, operand, mode, flags, false);
                    }
                };

                InstructionValueConsumer stateProc = (op, operand, mode, flags) -> {
                    if (TraceLinearScan.isVariableOrRegister(operand)) {
                        int opId = op.id();
                        int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                        addUse((AllocatableValue) operand, blockFrom, opId + 1, RegisterPriority.None, operand.getLIRKind());
                    }
                };

                // create a list with all caller-save registers (cpu, fpu, xmm)
                Register[] callerSaveRegs = allocator.getRegisterAllocationConfig().getRegisterConfig().getCallerSaveRegisters();

                // iterate all blocks in reverse order
                for (int i = allocator.blockCount() - 1; i >= 0; i--) {

                    AbstractBlockBase<?> block = allocator.blockAt(i);
                    // TODO (je) make empty bitset - remove
                    allocator.getBlockData(block).liveIn = new BitSet();
                    allocator.getBlockData(block).liveOut = new BitSet();
                    try (Indent indent2 = Debug.logAndIndent("handle block %d", block.getId())) {

                        List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);

                        /*
                         * Iterate all instructions of the block in reverse order. definitions of
                         * intervals are processed before uses.
                         */
                        for (int j = instructions.size() - 1; j >= 0; j--) {
                            final LIRInstruction op = instructions.get(j);
                            final int opId = op.id();

                            try (Indent indent3 = Debug.logAndIndent("handle inst %d: %s", opId, op)) {

                                // add a temp range for each register if operation destroys
                                // caller-save registers
                                if (op.destroysCallerSavedRegisters()) {
                                    for (Register r : callerSaveRegs) {
                                        if (allocator.attributes(r).isAllocatable()) {
                                            addTemp(r.asValue(), opId, RegisterPriority.None, LIRKind.Illegal);
                                        }
                                    }
                                    if (Debug.isLogEnabled()) {
                                        Debug.log("operation destroys all caller-save registers");
                                    }
                                }

                                op.visitEachOutput(outputConsumer);
                                op.visitEachTemp(tempConsumer);
                                op.visitEachAlive(aliveConsumer);
                                op.visitEachInput(inputConsumer);

                                /*
                                 * Add uses of live locals from interpreter's point of view for
                                 * proper debug information generation. Treat these operands as temp
                                 * values (if the live range is extended to a call site, the value
                                 * would be in a register at the call otherwise).
                                 */
                                op.visitEachState(stateProc);
                            }

                        } // end of instruction iteration
                    }
                    if (Debug.isDumpEnabled(DUMP_DURING_ANALYSIS_LEVEL)) {
                        allocator.printIntervals("After Block " + block);
                    }
                } // end of block iteration

                // fix spill state for phi/sigma intervals
                for (TraceInterval interval : allocator.intervals()) {
                    if (interval != null && interval.spillState().equals(SpillState.NoDefinitionFound) && interval.spillDefinitionPos() != -1) {
                        // there was a definition in a phi/sigma
                        interval.setSpillState(SpillState.NoSpillStore);
                    }
                }
                if (TraceRAuseInterTraceHints.getValue()) {
                    addInterTraceHints();
                }
                /*
                 * Add the range [-1, 0] to all fixed intervals. the register allocator need not
                 * handle unhandled fixed intervals.
                 */
                for (FixedInterval interval : allocator.fixedIntervals()) {
                    if (interval != null) {
                        /* We use [-1, 0] to avoid intersection with incoming values. */
                        interval.addRange(-1, 0);
                    }
                }
            }
        }

        private void addInterTraceHints() {
            // set hints for phi/sigma intervals
            LIR lir = allocator.getLIR();
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                LabelOp label = SSIUtil.incoming(lir, block);
                for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                    if (isAllocatedOrCurrent(block, pred)) {
                        BlockEndOp outgoing = SSIUtil.outgoing(lir, pred);
                        for (int i = 0; i < outgoing.getOutgoingSize(); i++) {
                            Value toValue = label.getIncomingValue(i);
                            if (!isIllegal(toValue) && !isRegister(toValue)) {
                                Value fromValue = outgoing.getOutgoingValue(i);
                                assert sameTrace(block, pred) || !isVariable(fromValue) : "Unallocated variable: " + fromValue;

                                if (isVariableOrRegister(fromValue)) {
                                    IntervalHint from = getIntervalHint((AllocatableValue) fromValue);
                                    TraceInterval to = allocator.getOrCreateInterval((AllocatableValue) toValue);
                                    setHint(label, to, from);
                                } else if (TraceRAshareSpillInformation.getValue() && TraceUtil.isShadowedRegisterValue(fromValue)) {
                                    ShadowedRegisterValue shadowedRegisterValue = TraceUtil.asShadowedRegisterValue(fromValue);
                                    IntervalHint from = getIntervalHint(shadowedRegisterValue.getRegister());
                                    TraceInterval to = allocator.getOrCreateInterval((AllocatableValue) toValue);
                                    setHint(label, to, from);
                                    to.setSpillSlot(shadowedRegisterValue.getStackSlot());
                                    to.setSpillState(SpillState.StartInMemory);
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Returns a value for a interval definition, which can be used for re-materialization.
         *
         * @param op An instruction which defines a value
         * @param operand The destination operand of the instruction
         * @param interval The interval for this defined value.
         * @return Returns the value which is moved to the instruction and which can be reused at
         *         all reload-locations in case the interval of this instruction is spilled.
         *         Currently this can only be a {@link JavaConstant}.
         */
        private JavaConstant getMaterializedValue(LIRInstruction op, Value operand, TraceInterval interval) {
            if (op instanceof LoadConstantOp) {
                LoadConstantOp move = (LoadConstantOp) op;
                if (move.getConstant() instanceof JavaConstant) {
                    if (!allocator.neverSpillConstants()) {
                        if (!allocator.getSpillMoveFactory().allowConstantToStackMove(move.getConstant())) {
                            return null;
                        }
                        /*
                         * Check if the interval has any uses which would accept an stack location
                         * (priority == ShouldHaveRegister). Rematerialization of such intervals can
                         * result in a degradation, because rematerialization always inserts a
                         * constant load, even if the value is not needed in a register.
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
    }
}
