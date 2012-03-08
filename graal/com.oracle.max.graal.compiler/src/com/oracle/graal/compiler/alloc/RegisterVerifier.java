/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.*;
import com.oracle.max.graal.lir.LIRInstruction.*;
import com.oracle.max.graal.lir.cfg.*;

/**
 */
final class RegisterVerifier {

    LinearScan allocator;
    List<Block> workList; // all blocks that must be processed
    ArrayMap<Interval[]> savedStates; // saved information of previous check

    // simplified access to methods of LinearScan
    Interval intervalAt(CiValue operand) {
        return allocator.intervalFor(operand);
    }

    // currently, only registers are processed
    int stateSize() {
        return allocator.maxRegisterNumber() + 1;
    }

    // accessors
    Interval[] stateForBlock(Block block) {
        return savedStates.get(block.getId());
    }

    void setStateForBlock(Block block, Interval[] savedState) {
        savedStates.put(block.getId(), savedState);
    }

    void addToWorkList(Block block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    RegisterVerifier(LinearScan allocator) {
        this.allocator = allocator;
        workList = new ArrayList<>(16);
        this.savedStates = new ArrayMap<>();

    }

    void verify(Block start) {
        // setup input registers (method arguments) for first block
        Interval[] inputState = new Interval[stateSize()];
        setStateForBlock(start, inputState);
        addToWorkList(start);

        // main loop for verification
        do {
            Block block = workList.get(0);
            workList.remove(0);

            processBlock(block);
        } while (!workList.isEmpty());
    }

    private void processBlock(Block block) {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println();
            TTY.println("processBlock B%d", block.getId());
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
        processOperations(block.lir, inputState);

        // iterate all successors
        for (int i = 0; i < block.numberOfSux(); i++) {
            Block succ = block.suxAt(i);
            processSuccessor(succ, inputState);
        }
    }

    private void processSuccessor(Block block, Interval[] inputState) {
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
                            TTY.println("processSuccessor B%d: invalidating slot %d", block.getId(), i);
                        }
                    }
                }
            }

            if (savedStateCorrect) {
                // already processed block with correct inputState
                if (GraalOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("processSuccessor B%d: previous visit already correct", block.getId());
                }
            } else {
                // must re-visit this block
                if (GraalOptions.TraceLinearScanLevel >= 2) {
                    TTY.println("processSuccessor B%d: must re-visit because input state changed", block.getId());
                }
                addToWorkList(block);
            }

        } else {
            // block was not processed before, so set initial inputState
            if (GraalOptions.TraceLinearScanLevel >= 2) {
                TTY.println("processSuccessor B%d: initial visit", block.getId());
            }

            setStateForBlock(block, copy(inputState));
            addToWorkList(block);
        }
    }

    static Interval[] copy(Interval[] inputState) {
        return inputState.clone();
    }

    static void statePut(Interval[] inputState, CiValue location, Interval interval) {
        if (location != null && isRegister(location)) {
            CiRegister reg = asRegister(location);
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

    static boolean checkState(Interval[] inputState, CiValue reg, Interval interval) {
        if (reg != null && isRegister(reg)) {
            if (inputState[asRegister(reg).number] != interval) {
                throw new GraalInternalError("!! Error in register allocation: register %s does not contain interval %s but interval %s", reg, interval.operand, inputState[asRegister(reg).number]);
            }
        }
        return true;
    }

    void processOperations(List<LIRInstruction> ops, final Interval[] inputState) {
        // visit all instructions of the block
        for (int i = 0; i < ops.size(); i++) {
            final LIRInstruction op = ops.get(i);

            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println(op.toStringWithIdPrefix());
            }

            ValueProcedure useProc = new ValueProcedure() {
                @Override
                public CiValue doValue(CiValue operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand)) {
                        Interval interval = intervalAt(operand);
                        if (op.id() != -1) {
                            interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                        }

                        assert checkState(inputState, interval.location(), interval.splitParent());
                    }
                    return operand;
                }
            };

            ValueProcedure defProc = new ValueProcedure() {
                @Override
                public CiValue doValue(CiValue operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand)) {
                        Interval interval = intervalAt(operand);
                        if (op.id() != -1) {
                            interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                        }

                        statePut(inputState, interval.location(), interval.splitParent());
                    }
                    return operand;
                }
            };

            // check if input operands are correct
            op.forEachInput(useProc);
            // invalidate all caller save registers at calls
            if (op.hasCall()) {
                for (CiRegister r : allocator.frameMap.registerConfig.getCallerSaveRegisters()) {
                    statePut(inputState, r.asValue(), null);
                }
            }
            op.forEachAlive(useProc);
            // set temp operands (some operations use temp operands also as output operands, so can't set them null)
            op.forEachTemp(defProc);
            // set output operands
            op.forEachOutput(defProc);
        }
    }
}
