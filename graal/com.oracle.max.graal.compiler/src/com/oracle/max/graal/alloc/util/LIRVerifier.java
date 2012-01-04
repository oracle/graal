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
package com.oracle.max.graal.alloc.util;

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;

public final class LIRVerifier {
    private final LIR lir;
    private final FrameMap frameMap;
    private final RiRegisterConfig registerConfig;

    private final boolean beforeRegisterAllocation;

    private final BitSet[] blockLiveOut;
    private final Object[] variableDefinitions;

    private BitSet liveOutFor(Block block) {
        return blockLiveOut[block.blockID()];
    }
    private void setLiveOutFor(Block block, BitSet liveOut) {
        blockLiveOut[block.blockID()] = liveOut;
    }

    private int maxRegisterNum() {
        return frameMap.target.arch.registers.length;
    }

    private boolean isAllocatableRegister(CiValue value) {
        return isRegister(value) && registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }


    public static boolean verify(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap, RiRegisterConfig registerConfig) {
        LIRVerifier verifier = new LIRVerifier(beforeRegisterAllocation, lir, frameMap, registerConfig);
        verifier.verify();
        return true;
    }

    private LIRVerifier(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap, RiRegisterConfig registerConfig) {
        this.beforeRegisterAllocation = beforeRegisterAllocation;
        this.lir = lir;
        this.frameMap = frameMap;
        this.registerConfig = registerConfig;
        this.blockLiveOut = new BitSet[lir.linearScanOrder().size()];
        this.variableDefinitions = new Object[lir.numVariables()];
    }

    private BitSet curVariablesLive;
    private CiValue[] curRegistersLive;

    private LIRBlock curBlock;
    private Object curInstruction;

    private void verify() {
        ValueProcedure useProc =    new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return use(value); } };
        ValueProcedure tempProc =   new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return def(value, true); } };
        ValueProcedure outputProc = new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return def(value, false); } };

        for (LIRBlock block : lir.linearScanOrder()) {
            curBlock = block;
            curVariablesLive = new BitSet();
            curRegistersLive = new CiValue[maxRegisterNum()];

            if (block.dominator() != null) {
                curVariablesLive.or(liveOutFor(block.dominator()));
            }

            if (beforeRegisterAllocation) {
                if (block.phis != null) {
                    curInstruction = block.phis;
                    block.phis.forEachOutput(outputProc);
                }
            } else {
                assert block.phis == null;
            }

            for (LIRInstruction op : block.lir()) {
                curInstruction = op;

                op.forEachInput(useProc);
                if (op.hasCall()) {
                    for (CiRegister register : registerConfig.getCallerSaveRegisters()) {
                        curRegistersLive[register.number] = null;
                    }
                }
                op.forEachAlive(useProc);
                op.forEachState(useProc);
                op.forEachTemp(tempProc);
                op.forEachOutput(outputProc);

                curInstruction = null;
            }

            setLiveOutFor(block, curVariablesLive);
        }
    }

    private CiValue use(CiValue value) {
        if (beforeRegisterAllocation && isVariable(value)) {
            int variableIdx = asVariable(value).index;
            if (!curVariablesLive.get(variableIdx)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                if (variableDefinitions[variableIdx] != null) {
                    TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                }
                TTY.println("ERROR: Use of variable %s that is not defined in dominator", value);
                throw Util.shouldNotReachHere();
            }

        } else if (beforeRegisterAllocation && isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (curRegistersLive[regNum] != value) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live registers: %s", Arrays.toString(curRegistersLive));
                TTY.println("ERROR: Use of fixed register %s that is not defined in this block", value);
                throw Util.shouldNotReachHere();
            }
        } else if (isRegister(value)) {
            // Register usage cannot be checked.
        } else if (isStackSlot(value)) {
            // TODO check if stack slot is allowed for this operand.
        } else if (isConstant(value)) {
            // TODO check if constant is allowed for this operand.
        } else if (value == CiValue.IllegalValue) {
            // TODO check if illegal is allowed for this operand.
        } else {
            TTY.println("block %s  instruction %s", curBlock, curInstruction);
            TTY.println("Unexpected value: %s %s", value.getClass().getSimpleName(), value);
            throw Util.shouldNotReachHere();
        }
        return value;
    }

    private CiValue def(CiValue value, boolean isTemp) {
        if (beforeRegisterAllocation && isVariable(value)) {
            int variableIdx = asVariable(value).index;
            if (variableDefinitions[variableIdx] != null) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                TTY.println("ERROR: Variable %s defined multiple times", value);
                throw Util.shouldNotReachHere();
            }
            assert curInstruction != null;
            variableDefinitions[variableIdx] = curInstruction;
            assert !curVariablesLive.get(variableIdx);
            if (!isTemp) {
                curVariablesLive.set(variableIdx);
            }

        } else if (beforeRegisterAllocation && isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (isTemp) {
                curRegistersLive[regNum] = null;
            } else {
                curRegistersLive[regNum] = value;
            }
        } else if (isRegister(value)) {
            // Register definition cannot be checked.
        } else if (isStackSlot(value)) {
            // TODO check if stack slot is allowed for this operand.
        } else {
            TTY.println("block %s  instruction %s", curBlock, curInstruction);
            TTY.println("Unexpected value: %s %s", value.getClass().getSimpleName(), value);
            throw Util.shouldNotReachHere();
        }
        return value;
    }
}
