/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRValueUtil.isStackSlotValue;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options.TraceRAshareSpillInformation;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options.TraceRAuseInterTraceHints;
import static com.oracle.graal.lir.alloc.trace.TraceUtil.asShadowedRegisterValue;
import static com.oracle.graal.lir.alloc.trace.TraceUtil.isShadowedRegisterValue;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.finalizeFixedIntervals;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.getIntervalHint;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.numberInstruction;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.setHint;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.setSpillSlot;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitAlive;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitCallerSavedRegisters;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitInput;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitOutput;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitState;
import static com.oracle.graal.lir.alloc.trace.lsra.IntervalBuilderUtil.visitTemp;
import static com.oracle.graal.lir.alloc.trace.lsra.TraceLinearScan.isVariableOrRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;

import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.InstructionValueConsumer;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.LIRValueUtil;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.alloc.trace.ShadowedRegisterValue;
import com.oracle.graal.lir.alloc.trace.lsra.TraceInterval.SpillState;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.ssi.SSIUtil;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

final class TraceLinearScanLifetimeAnalysisPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    TraceLinearScanAllocationContext context) {
        TraceBuilderResult<?> traceBuilderResult = context.traceBuilderResult;
        TraceLinearScan allocator = context.allocator;
        new Analyser(allocator.getIntervalData(), traceBuilderResult, allocator.sortedBlocks(), lirGenRes.getLIR(), allocator.neverSpillConstants(), allocator.getSpillMoveFactory(),
                        allocator.getRegisterAllocationConfig().getRegisterConfig().getCallerSaveRegisters()).analyze();
    }

    static final class Analyser {
        private static final int DUMP_DURING_ANALYSIS_LEVEL = 4;
        private final IntervalData intervalData;
        private final TraceBuilderResult<?> traceBuilderResult;
        private int numInstructions;
        private final List<? extends AbstractBlockBase<?>> sortedBlocks;
        private final LIR lir;
        private final boolean neverSpillConstants;
        private final MoveFactory spillMoveFactory;
        private final Register[] callerSaveRegisters;

        private Analyser(IntervalData intervalData, TraceBuilderResult<?> traceBuilderResult, List<? extends AbstractBlockBase<?>> sortedBlocks, LIR lir, boolean neverSpillConstants,
                        MoveFactory moveFactory, Register[] callerSaveRegisters) {
            this.intervalData = intervalData;
            this.traceBuilderResult = traceBuilderResult;
            this.sortedBlocks = sortedBlocks;
            this.lir = lir;
            this.neverSpillConstants = neverSpillConstants;
            this.spillMoveFactory = moveFactory;
            this.callerSaveRegisters = callerSaveRegisters;
        }

        private List<? extends AbstractBlockBase<?>> sortedBlocks() {
            return sortedBlocks;
        }

        private LIR getLIR() {
            return lir;
        }

        private Register[] getCallerSavedRegisters() {
            return callerSaveRegisters;
        }

        private void analyze() {
            countInstructions();
            buildIntervals();
        }

        private boolean sameTrace(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
            return traceBuilderResult.getTraceForBlock(b) == traceBuilderResult.getTraceForBlock(a);
        }

        private boolean isAllocatedOrCurrent(AbstractBlockBase<?> currentBlock, AbstractBlockBase<?> other) {
            return traceBuilderResult.getTraceForBlock(other).getId() <= traceBuilderResult.getTraceForBlock(currentBlock).getId();
        }

        /**
         * Count instructions in all blocks. The numbering follows the
         * {@linkplain TraceLinearScan#sortedBlocks() register allocation order}.
         */
        private void countInstructions() {

            intervalData.initIntervals();

            int numberInstructions = 0;
            for (AbstractBlockBase<?> block : sortedBlocks()) {
                numberInstructions += getLIR().getLIRforBlock(block).size();
            }
            numInstructions = numberInstructions;

            // initialize with correct length
            intervalData.initOpIdMaps(numberInstructions);
        }

        private final InstructionValueConsumer outputConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                visitOutput(intervalData, op, operand, mode, flags, neverSpillConstants, spillMoveFactory);
            }
        };

        private final InstructionValueConsumer tempConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                visitTemp(intervalData, op, operand, mode, flags);
            }
        };
        private final InstructionValueConsumer aliveConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                visitAlive(intervalData, op, operand, mode, flags);
            }
        };

        private final InstructionValueConsumer inputConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                visitInput(intervalData, op, operand, mode, flags);
            }

        };

        private final InstructionValueConsumer stateProc = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                visitState(intervalData, op, operand);
            }
        };

        @SuppressWarnings("try")
        private void buildIntervals() {

            try (Indent indent = Debug.logAndIndent("build intervals")) {

                // create a list with all caller-save registers (cpu, fpu, xmm)
                Register[] callerSaveRegs = getCallerSavedRegisters();
                int instructionIndex = numInstructions;

                // iterate all blocks in reverse order
                List<? extends AbstractBlockBase<?>> blocks = sortedBlocks();
                ListIterator<? extends AbstractBlockBase<?>> blockIt = blocks.listIterator(blocks.size());
                while (blockIt.hasPrevious()) {
                    final AbstractBlockBase<?> block = blockIt.previous();

                    try (Indent indent2 = Debug.logAndIndent("handle block %d", block.getId())) {

                        /*
                         * Iterate all instructions of the block in reverse order. definitions of
                         * intervals are processed before uses.
                         */
                        List<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
                        ListIterator<LIRInstruction> instIt = instructions.listIterator(instructions.size());
                        while (instIt.hasPrevious()) {
                            final LIRInstruction op = instIt.previous();
                            // number instruction
                            instructionIndex--;
                            final int opId = instructionIndex << 1;
                            numberInstruction(intervalData, block, op, instructionIndex);

                            try (Indent indent3 = Debug.logAndIndent("handle inst %d: %s", opId, op)) {

                                /*
                                 * Add a temp range for each register if operation destroys
                                 * caller-save registers.
                                 */
                                if (op.destroysCallerSavedRegisters()) {
                                    visitCallerSavedRegisters(intervalData, callerSaveRegs, opId);
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
                        intervalData.printIntervals("After Block " + block);
                    }
                } // end of block iteration
                assert instructionIndex == 0 : "not at start?" + instructionIndex;

                // fix spill state for phi/sigma intervals
                for (TraceInterval interval : intervalData.intervals()) {
                    if (interval != null && interval.spillState().equals(SpillState.NoDefinitionFound) && interval.spillDefinitionPos() != -1) {
                        // there was a definition in a phi/sigma
                        interval.setSpillState(SpillState.NoSpillStore);
                    }
                }
                if (TraceRAuseInterTraceHints.getValue()) {
                    addInterTraceHints();
                }
                finalizeFixedIntervals(intervalData);
            }
        }

        @SuppressWarnings("try")
        private void addInterTraceHints() {
            try (Scope s = Debug.scope("InterTraceHints", intervalData)) {
                // set hints for phi/sigma intervals
                for (AbstractBlockBase<?> block : sortedBlocks()) {
                    LabelOp label = SSIUtil.incoming(lir, block);
                    for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                        if (isAllocatedOrCurrent(block, pred)) {
                            BlockEndOp outgoing = SSIUtil.outgoing(lir, pred);
                            // do not look at phi variables as they are not same value!
                            for (int i = outgoing.getPhiSize(); i < outgoing.getOutgoingSize(); i++) {
                                Value toValue = label.getIncomingValue(i);
                                assert !isShadowedRegisterValue(toValue) : "Shadowed Registers are not allowed here: " + toValue;
                                if (isVariable(toValue)) {
                                    Value fromValue = outgoing.getOutgoingValue(i);
                                    assert sameTrace(block, pred) || !isVariable(fromValue) : "Unallocated variable: " + fromValue;
                                    if (!LIRValueUtil.isConstantValue(fromValue)) {
                                        addInterTraceHint(label, (AllocatableValue) toValue, fromValue);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }

        private void addInterTraceHint(LabelOp label, AllocatableValue toValue, Value fromValue) {
            assert isVariable(toValue) : "Wrong toValue: " + toValue;
            assert isRegister(fromValue) || isVariable(fromValue) || isStackSlotValue(fromValue) || isShadowedRegisterValue(fromValue) : "Wrong fromValue: " + fromValue;
            TraceInterval to = intervalData.getOrCreateInterval(toValue);
            if (isVariableOrRegister(fromValue)) {
                IntervalHint from = getIntervalHint(intervalData, (AllocatableValue) fromValue);
                setHint(label, to, from);
            } else if (isStackSlotValue(fromValue)) {
                setSpillSlot(label, to, (AllocatableValue) fromValue);
            } else if (TraceRAshareSpillInformation.getValue() && isShadowedRegisterValue(fromValue)) {
                ShadowedRegisterValue shadowedRegisterValue = asShadowedRegisterValue(fromValue);
                IntervalHint from = getIntervalHint(intervalData, shadowedRegisterValue.getRegister());
                setHint(label, to, from);
                setSpillSlot(label, to, shadowedRegisterValue.getStackSlot());
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
