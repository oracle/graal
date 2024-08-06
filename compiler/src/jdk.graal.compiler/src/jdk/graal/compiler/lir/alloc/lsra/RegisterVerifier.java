/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.EnumSet;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.LIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 */
final class RegisterVerifier {

    LinearScan allocator;
    ArrayList<BasicBlock<?>> workList; // all blocks that must be processed
    BlockMap<Interval[]> savedStates; // saved information of previous check

    // simplified access to methods of LinearScan
    Interval intervalAt(Value operand) {
        return allocator.intervalFor(operand);
    }

    // currently, only registers are processed
    int stateSize() {
        return allocator.maxRegisterNumber() + 1;
    }

    // accessors
    Interval[] stateForBlock(BasicBlock<?> block) {
        return savedStates.get(block);
    }

    void setStateForBlock(BasicBlock<?> block, Interval[] savedState) {
        savedStates.put(block, savedState);
    }

    void addToWorkList(BasicBlock<?> block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    RegisterVerifier(LinearScan allocator) {
        this.allocator = allocator;
        workList = new ArrayList<>(16);
        this.savedStates = new BlockMap<>(allocator.getLIR().getControlFlowGraph());

    }

    @SuppressWarnings("try")
    void verify(BasicBlock<?> start) {
        DebugContext debug = allocator.getDebug();
        try (DebugContext.Scope s = debug.scope("RegisterVerifier")) {
            // setup input registers (method arguments) for first block
            Interval[] inputState = new Interval[stateSize()];
            setStateForBlock(start, inputState);
            addToWorkList(start);

            // main loop for verification
            do {
                BasicBlock<?> block = workList.get(0);
                workList.remove(0);

                processBlock(block);
            } while (!workList.isEmpty());
        }
    }

    @SuppressWarnings("try")
    private void processBlock(BasicBlock<?> block) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("processBlock B%d", block.getId())) {
            // must copy state because it is modified
            Interval[] inputState = copy(stateForBlock(block));

            try (Indent indent2 = debug.logAndIndent("Input-State of intervals:")) {
                printState(inputState);
            }

            // process all operations of the block
            processOperations(block, inputState);

            try (Indent indent2 = debug.logAndIndent("Output-State of intervals:")) {
                printState(inputState);
            }

            // iterate all successors
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BasicBlock<?> succ = block.getSuccessorAt(i);
                processSuccessor(succ, inputState);
            }
        }
    }

    protected void printState(Interval[] inputState) {
        DebugContext debug = allocator.getDebug();
        for (int i = 0; i < stateSize(); i++) {
            Register reg = allocator.getRegisters().get(i);
            assert reg.number == i : Assertions.errorMessage(reg, i);
            if (inputState[i] != null) {
                debug.log(" %6s %4d  --  %s", reg, inputState[i].operandNumber, inputState[i]);
            } else {
                debug.log(" %6s   __", reg);
            }
        }
    }

    private void processSuccessor(BasicBlock<?> block, Interval[] inputState) {
        DebugContext debug = allocator.getDebug();
        Interval[] savedState = stateForBlock(block);

        if (savedState != null) {
            // this block was already processed before.
            // check if new inputState is consistent with savedState

            boolean savedStateCorrect = true;
            for (int i = 0; i < stateSize(); i++) {
                if (inputState[i] != savedState[i]) {
                    // current inputState and previous savedState assume a different
                    // interval in this register . assume that this register is invalid
                    if (savedState[i] != null) {
                        // invalidate old calculation only if it assumed that
                        // register was valid. when the register was already invalid,
                        // then the old calculation was correct.
                        savedStateCorrect = false;
                        savedState[i] = null;

                        debug.log("processSuccessor B%d: invalidating slot %d", block.getId(), i);
                    }
                }
            }

            if (savedStateCorrect) {
                // already processed block with correct inputState
                debug.log("processSuccessor B%d: previous visit already correct", block.getId());
            } else {
                // must re-visit this block
                debug.log("processSuccessor B%d: must re-visit because input state changed", block.getId());
                addToWorkList(block);
            }

        } else {
            // block was not processed before, so set initial inputState
            debug.log("processSuccessor B%d: initial visit", block.getId());

            setStateForBlock(block, copy(inputState));
            addToWorkList(block);
        }
    }

    static Interval[] copy(Interval[] inputState) {
        return inputState.clone();
    }

    static void statePut(DebugContext debug, Interval[] inputState, Value location, Interval interval) {
        if (location != null && isRegister(location)) {
            Register reg = asRegister(location);
            int regNum = reg.number;
            if (interval != null) {
                debug.log("%s = %s", reg, interval.operand);
            } else if (inputState[regNum] != null) {
                debug.log("%s = null", reg);
            }

            inputState[regNum] = interval;
        }
    }

    static boolean checkState(BasicBlock<?> block, LIRInstruction op, Interval[] inputState, Value operand, Value reg, Interval interval) {
        if (reg != null && isRegister(reg)) {
            if (inputState[asRegister(reg).number] != interval) {
                throw new GraalError(
                                "Error in register allocation: operation (%s) in block %s expected register %s (operand %s) to contain the value of interval %s but data-flow says it contains interval %s",
                                op, block, reg, operand, interval, inputState[asRegister(reg).number]);
            }
        }
        return true;
    }

    void processOperations(BasicBlock<?> block, final Interval[] inputState) {
        ArrayList<LIRInstruction> ops = allocator.getLIR().getLIRforBlock(block);
        DebugContext debug = allocator.getDebug();
        InstructionValueConsumer useConsumer = new InstructionValueConsumer() {

            @Override
            public void visitValue(LIRInstruction op, Value operand, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                // we skip spill moves inserted by the spill position optimization
                if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand) && op.id() != LinearScan.DOMINATOR_SPILL_MOVE_ID) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                    }

                    assert checkState(block, op, inputState, interval.operand, interval.location(), interval.splitParent());
                }
            }
        };

        InstructionValueConsumer defConsumer = (op, operand, mode, flags) -> {
            if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand)) {
                Interval interval = intervalAt(operand);
                if (op.id() != -1) {
                    interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                }

                statePut(debug, inputState, interval.location(), interval.splitParent());
            }
        };

        // visit all instructions of the block
        for (int i = 0; i < ops.size(); i++) {
            final LIRInstruction op = ops.get(i);

            if (debug.isLogEnabled()) {
                debug.log("%s", op.toStringWithIdPrefix());
            }

            // check if input operands are correct
            op.visitEachInput(useConsumer);
            // invalidate all caller save registers at calls
            if (op.destroysCallerSavedRegisters()) {
                for (Register r : allocator.getRegisterAllocationConfig().getRegisterConfig().getCallerSaveRegisters()) {
                    statePut(debug, inputState, r.asValue(), null);
                }
            }
            op.visitEachAlive(useConsumer);
            // set temp operands (some operations use temp operands also as output operands, so
            // can't set them null)
            op.visitEachTemp(defConsumer);
            // set output operands
            op.visitEachOutput(defConsumer);
        }
    }
}
