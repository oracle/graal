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

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.alloc.lsra.Interval.RegisterPriority;
import com.oracle.graal.lir.alloc.lsra.Interval.SpillState;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;

public class TraceSimpleLifetimeAnalysisPhase extends TraceLinearScanLifetimeAnalysisPhase {

    public TraceSimpleLifetimeAnalysisPhase(LinearScan allocator, TraceBuilderResult<?> traceBuilderResult) {
        super(allocator, traceBuilderResult);
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        numberInstructions();
        allocator.printLir("Before register allocation", true);
        buildIntervals();
    }

    @Override
    protected void addUse(AllocatableValue operand, int from, int to, RegisterPriority registerPriority, LIRKind kind) {
        if (!allocator.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            super.addUse(operand, from, to, registerPriority, kind);
            return;
        }

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        Range r = interval.first();
        if (r == Range.EndMarker) {
            interval.addRange(from, to);
        } else if (r.from > from) {
            r.from = from;
        }

        // Register use position at even instruction id.
        interval.addUsePos(to & ~1, registerPriority);

        if (Debug.isLogEnabled()) {
            Debug.log("add use: %s, at %d (%s)", interval, to, registerPriority.name());
        }
    }

    @Override
    protected void addTemp(AllocatableValue operand, int tempPos, RegisterPriority registerPriority, LIRKind kind) {
        if (!allocator.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            super.addTemp(operand, tempPos, registerPriority, kind);
            return;
        }

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        Range r = interval.first();
        if (r == Range.EndMarker) {
            interval.addRange(tempPos, tempPos + 1);
        } else if (r.from > tempPos) {
            r.from = tempPos;
        }
        interval.addUsePos(tempPos, registerPriority);
        interval.addMaterializationValue(null);

        if (Debug.isLogEnabled()) {
            Debug.log("add temp: %s tempPos %d (%s)", interval, tempPos, RegisterPriority.MustHaveRegister.name());
        }
    }

    @Override
    protected void addDef(AllocatableValue operand, LIRInstruction op, RegisterPriority registerPriority, LIRKind kind) {
        if (!allocator.isProcessed(operand)) {
            return;
        }
        if (isRegister(operand)) {
            super.addDef(operand, op, registerPriority, kind);
            return;
        }
        int defPos = op.id();

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        Range r = interval.first();
        if (r == Range.EndMarker) {
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
             * Update the starting point (when a range is first created for a use, its start is the
             * beginning of the current block until a def is encountered).
             */
            r.from = defPos;
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

    @Override
    protected void buildIntervals() {

        try (Indent indent = Debug.logAndIndent("build intervals")) {
            InstructionValueConsumer outputConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    addDef((AllocatableValue) operand, op, registerPriorityOfOutputOperand(op), operand.getLIRKind());
                    addRegisterHint(op, operand, mode, flags, true);
                }
            };

            InstructionValueConsumer tempConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    addTemp((AllocatableValue) operand, op.id(), RegisterPriority.MustHaveRegister, operand.getLIRKind());
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer aliveConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    RegisterPriority p = registerPriorityOfInputOperand(flags);
                    int opId = op.id();
                    int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                    addUse((AllocatableValue) operand, blockFrom, opId + 1, p, operand.getLIRKind());
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer inputConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    int opId = op.id();
                    RegisterPriority p = registerPriorityOfInputOperand(flags);
                    int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                    addUse((AllocatableValue) operand, blockFrom, opId, p, operand.getLIRKind());
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer stateProc = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
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
                             * Add uses of live locals from interpreter's point of view for proper
                             * debug information generation. Treat these operands as temp values (if
                             * the live range is extended to a call site, the value would be in a
                             * register at the call otherwise).
                             */
                            op.visitEachState(stateProc);

                            // special steps for some instructions (especially moves)
                            handleMethodArguments(op);

                        }

                    } // end of instruction iteration
                }
                allocator.printIntervals("After Block " + block);
            } // end of block iteration

            /*
             * Add the range [0, 1] to all fixed intervals. the register allocator need not handle
             * unhandled fixed intervals.
             */
            for (Interval interval : allocator.intervals()) {
                if (interval != null && isRegister(interval.operand)) {
                    interval.addRange(0, 1);
                }
            }
        }
        postBuildIntervals();
    }
}
