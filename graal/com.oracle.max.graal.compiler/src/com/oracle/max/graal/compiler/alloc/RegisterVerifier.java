/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.alloc;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

/**
 */
final class RegisterVerifier {

    LinearScan allocator;
    List<LIRBlock> workList; // all blocks that must be processed
    ArrayMap<Interval[]> savedStates; // saved information of previous check

    // simplified access to methods of LinearScan
    GraalCompilation compilation() {
        return allocator.compilation;
    }

    Interval intervalAt(CiValue operand) {
        return allocator.intervalFor(operand);
    }

    // currently, only registers are processed
    int stateSize() {
        return allocator.operands.maxRegisterNumber() + 1;
    }

    // accessors
    Interval[] stateForBlock(LIRBlock block) {
        return savedStates.get(block.blockID());
    }

    void setStateForBlock(LIRBlock block, Interval[] savedState) {
        savedStates.put(block.blockID(), savedState);
    }

    void addToWorkList(LIRBlock block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    RegisterVerifier(LinearScan allocator) {
        this.allocator = allocator;
        workList = new ArrayList<LIRBlock>(16);
        this.savedStates = new ArrayMap<Interval[]>();

    }

    void verify(LIRBlock start) {
        // setup input registers (method arguments) for first block
        Interval[] inputState = new Interval[stateSize()];
        CiCallingConvention args = compilation().frameMap().incomingArguments();
        for (int n = 0; n < args.locations.length; n++) {
            CiValue operand = args.locations[n];
            if (operand.isRegister()) {
                CiValue reg = operand;
                Interval interval = intervalAt(reg);
                inputState[reg.asRegister().number] = interval;
            }
        }

        setStateForBlock(start, inputState);
        addToWorkList(start);

        // main loop for verification
        do {
            LIRBlock block = workList.get(0);
            workList.remove(0);

            processBlock(block);
        } while (!workList.isEmpty());
    }

    private void processBlock(LIRBlock block) {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println();
            TTY.println("processBlock B%d", block.blockID());
        }

        // must copy state because it is modified
        Interval[] inputState = copy(stateForBlock(block));

        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("Input-State of intervals:");
            TTY.print("    ");
            for (int i = 0; i < stateSize(); i++) {
                if (inputState[i] != null) {
                    TTY.print(" %4d", inputState[i].operandNumber);
                } else {
                    TTY.print("   __");
                }
            }
            TTY.println();
            TTY.println();
        }

        // process all operations of the block
        processOperations(block.lir(), inputState);

        // iterate all successors
        for (int i = 0; i < block.numberOfSux(); i++) {
            LIRBlock succ = block.suxAt(i);
            processSuccessor(succ, inputState);
        }
    }

    private void processSuccessor(LIRBlock block, Interval[] inputState) {
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

                        if (GraalOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("processSuccessor B%d: invalidating slot %d", block.blockID(), i);
                        }
                    }
                }
            }

            if (savedStateCorrect) {
                // already processed block with correct inputState
                if (GraalOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("processSuccessor B%d: previous visit already correct", block.blockID());
                }
            } else {
                // must re-visit this block
                if (GraalOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("processSuccessor B%d: must re-visit because input state changed", block.blockID());
                }
                addToWorkList(block);
            }

        } else {
            // block was not processed before, so set initial inputState
            if (GraalOptions.TraceLinearScanLevel >= 2) {
                TTY.println("processSuccessor B%d: initial visit", block.blockID());
            }

            setStateForBlock(block, copy(inputState));
            addToWorkList(block);
        }
    }

    Interval[] copy(Interval[] inputState) {
        return inputState.clone();
    }

    void statePut(Interval[] inputState, CiValue location, Interval interval) {
        if (location != null && location.isRegister()) {
            CiRegister reg = location.asRegister();
            int regNum = reg.number;
            if (interval != null) {
                if (GraalOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("        %s = %s", reg, interval.operand);
                }
            } else if (inputState[regNum] != null) {
                if (GraalOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("        %s = null", reg);
                }
            }

            inputState[regNum] = interval;
        }
    }

    boolean checkState(Interval[] inputState, CiValue reg, Interval interval) {
        if (reg != null && reg.isRegister()) {
            if (inputState[reg.asRegister().number] != interval) {
                throw new CiBailout("!! Error in register allocation: register " + reg + " does not contain interval " + interval.operand + " but interval " + inputState[reg.asRegister().number]);
            }
        }
        return true;
    }

    void processOperations(List<LIRInstruction> ops, Interval[] inputState) {
        // visit all instructions of the block
        for (int i = 0; i < ops.size(); i++) {
            LIRInstruction op = ops.get(i);

            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println(op.toStringWithIdPrefix());
            }

            // check if input operands are correct
            int n = op.operandCount(LIRInstruction.OperandMode.Input);
            for (int j = 0; j < n; j++) {
                CiValue operand = op.operandAt(LIRInstruction.OperandMode.Input, j);
                if (operand.isVariableOrRegister() && allocator.isProcessed(operand)) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), LIRInstruction.OperandMode.Input, allocator);
                    }

                    assert checkState(inputState, interval.location(), interval.splitParent());
                }
            }
            n = op.operandCount(LIRInstruction.OperandMode.Alive);
            for (int j = 0; j < n; j++) {
                CiValue operand = op.operandAt(LIRInstruction.OperandMode.Alive, j);
                if (operand.isVariableOrRegister() && allocator.isProcessed(operand)) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), LIRInstruction.OperandMode.Input, allocator);
                    }

                    assert checkState(inputState, interval.location(), interval.splitParent());
                }
            }

            // invalidate all caller save registers at calls
            if (op.hasCall()) {
                for (CiRegister r : allocator.compilation.registerConfig.getCallerSaveRegisters()) {
                    statePut(inputState, r.asValue(), null);
                }
            }

            // set temp operands (some operations use temp operands also as output operands, so can't set them null)
            n = op.operandCount(LIRInstruction.OperandMode.Temp);
            for (int j = 0; j < n; j++) {
                CiValue operand = op.operandAt(LIRInstruction.OperandMode.Temp, j);
                if (operand.isVariableOrRegister() && allocator.isProcessed(operand)) {
                    Interval interval = intervalAt(operand);
                    assert interval != null : "Could not find interval for operand " + operand;
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), LIRInstruction.OperandMode.Temp, allocator);
                    }

                    statePut(inputState, interval.location(), interval.splitParent());
                }
            }

            // set output operands
            n = op.operandCount(LIRInstruction.OperandMode.Output);
            for (int j = 0; j < n; j++) {
                CiValue operand = op.operandAt(LIRInstruction.OperandMode.Output, j);
                if (operand.isVariableOrRegister() && allocator.isProcessed(operand)) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), LIRInstruction.OperandMode.Output, allocator);
                    }

                    statePut(inputState, interval.location(), interval.splitParent());
                }
            }
        }
    }
}
