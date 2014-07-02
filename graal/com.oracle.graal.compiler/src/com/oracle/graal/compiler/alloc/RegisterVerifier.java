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
package com.oracle.graal.compiler.alloc;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.InstructionValueProcedure;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.phases.util.*;

/**
 */
final class RegisterVerifier {

    LinearScan allocator;
    List<AbstractBlock<?>> workList; // all blocks that must be processed
    ArrayMap<Interval[]> savedStates; // saved information of previous check

    // simplified access to methods of LinearScan
    Interval intervalAt(Value operand) {
        return allocator.intervalFor(operand);
    }

    // currently, only registers are processed
    int stateSize() {
        return allocator.maxRegisterNumber() + 1;
    }

    // accessors
    Interval[] stateForBlock(AbstractBlock<?> block) {
        return savedStates.get(block.getId());
    }

    void setStateForBlock(AbstractBlock<?> block, Interval[] savedState) {
        savedStates.put(block.getId(), savedState);
    }

    void addToWorkList(AbstractBlock<?> block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    RegisterVerifier(LinearScan allocator) {
        this.allocator = allocator;
        workList = new ArrayList<>(16);
        this.savedStates = new ArrayMap<>();

    }

    void verify(AbstractBlock<?> start) {
        // setup input registers (method arguments) for first block
        Interval[] inputState = new Interval[stateSize()];
        setStateForBlock(start, inputState);
        addToWorkList(start);

        // main loop for verification
        do {
            AbstractBlock<?> block = workList.get(0);
            workList.remove(0);

            processBlock(block);
        } while (!workList.isEmpty());
    }

    private void processBlock(AbstractBlock<?> block) {
        try (Indent indent = Debug.logAndIndent("processBlock B%d", block.getId())) {
            // must copy state because it is modified
            Interval[] inputState = copy(stateForBlock(block));

            try (Indent indent2 = Debug.logAndIndent("Input-State of intervals:")) {
                for (int i = 0; i < stateSize(); i++) {
                    if (inputState[i] != null) {
                        Debug.log(" %4d", inputState[i].operandNumber);
                    } else {
                        Debug.log("   __");
                    }
                }
            }

            // process all operations of the block
            processOperations(allocator.ir.getLIRforBlock(block), inputState);

            // iterate all successors
            for (AbstractBlock<?> succ : block.getSuccessors()) {
                processSuccessor(succ, inputState);
            }
        }
    }

    private void processSuccessor(AbstractBlock<?> block, Interval[] inputState) {
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

                        Debug.log("processSuccessor B%d: invalidating slot %d", block.getId(), i);
                    }
                }
            }

            if (savedStateCorrect) {
                // already processed block with correct inputState
                Debug.log("processSuccessor B%d: previous visit already correct", block.getId());
            } else {
                // must re-visit this block
                Debug.log("processSuccessor B%d: must re-visit because input state changed", block.getId());
                addToWorkList(block);
            }

        } else {
            // block was not processed before, so set initial inputState
            Debug.log("processSuccessor B%d: initial visit", block.getId());

            setStateForBlock(block, copy(inputState));
            addToWorkList(block);
        }
    }

    static Interval[] copy(Interval[] inputState) {
        return inputState.clone();
    }

    static void statePut(Interval[] inputState, Value location, Interval interval) {
        if (location != null && isRegister(location)) {
            Register reg = asRegister(location);
            int regNum = reg.number;
            if (interval != null) {
                Debug.log("%s = %s", reg, interval.operand);
            } else if (inputState[regNum] != null) {
                Debug.log("%s = null", reg);
            }

            inputState[regNum] = interval;
        }
    }

    static boolean checkState(Interval[] inputState, Value reg, Interval interval) {
        if (reg != null && isRegister(reg)) {
            if (inputState[asRegister(reg).number] != interval) {
                throw new GraalInternalError("!! Error in register allocation: register %s does not contain interval %s but interval %s", reg, interval.operand, inputState[asRegister(reg).number]);
            }
        }
        return true;
    }

    void processOperations(List<LIRInstruction> ops, final Interval[] inputState) {
        InstructionValueProcedure useProc = new InstructionValueProcedure() {

            @Override
            public Value doValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                // we skip spill moves inserted by the spill position optimization
                if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand) && op.id() != LinearScan.DOMINATOR_SPILL_MOVE_ID) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                    }

                    assert checkState(inputState, interval.location(), interval.splitParent());
                }
                return operand;
            }
        };

        InstructionValueProcedure defProc = new InstructionValueProcedure() {

            @Override
            public Value doValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
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

        // visit all instructions of the block
        for (int i = 0; i < ops.size(); i++) {
            final LIRInstruction op = ops.get(i);

            if (Debug.isLogEnabled()) {
                Debug.log("%s", op.toStringWithIdPrefix());
            }

            // check if input operands are correct
            op.forEachInput(useProc);
            // invalidate all caller save registers at calls
            if (op.destroysCallerSavedRegisters()) {
                for (Register r : allocator.frameMap.registerConfig.getCallerSaveRegisters()) {
                    statePut(inputState, r.asValue(), null);
                }
            }
            op.forEachAlive(useProc);
            // set temp operands (some operations use temp operands also as output operands, so
            // can't set them null)
            op.forEachTemp(defProc);
            // set output operands
            op.forEachOutput(defProc);
        }
    }
}
