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
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.OperandFlag;
import com.oracle.max.graal.compiler.lir.LIRInstruction.OperandMode;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.compiler.lir.LIRPhiMapping.PhiValueProcedure;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;

public final class LIRVerifier {
    private final LIR lir;
    private final FrameMap frameMap;

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
        return isRegister(value) && frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }

    public static boolean verify(final LIRInstruction op) {
        ValueProcedure allowedProc = new ValueProcedure() { @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return allowed(op, value, mode, flags); } };

        op.forEachInput(allowedProc);
        op.forEachAlive(allowedProc);
        op.forEachState(allowedProc);
        op.forEachTemp(allowedProc);
        op.forEachOutput(allowedProc);
        return true;
    }

    public static boolean verify(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        LIRVerifier verifier = new LIRVerifier(beforeRegisterAllocation, lir, frameMap);
        verifier.verify();
        return true;
    }


    private LIRVerifier(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        this.beforeRegisterAllocation = beforeRegisterAllocation;
        this.lir = lir;
        this.frameMap = frameMap;
        this.blockLiveOut = new BitSet[lir.linearScanOrder().size()];
        this.variableDefinitions = new Object[lir.numVariables()];
    }

    private BitSet curVariablesLive;
    private CiValue[] curRegistersLive;

    private LIRBlock curBlock;
    private Object curInstruction;
    private BitSet curRegistersDefined;

    private void verify() {
        PhiValueProcedure useProc = new PhiValueProcedure() { @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return use(value, mode, flags); } };
        ValueProcedure defProc =    new ValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return def(value, mode, flags); } };

        curRegistersDefined = new BitSet();
        for (LIRBlock block : lir.linearScanOrder()) {
            curBlock = block;
            curVariablesLive = new BitSet();
            curRegistersLive = new CiValue[maxRegisterNum()];

            if (block.dominator() != null) {
                curVariablesLive.or(liveOutFor(block.dominator()));
            }

            if (block.phis != null) {
                assert beforeRegisterAllocation;
                curInstruction = block.phis;
                block.phis.forEachOutput(defProc);
            }

            for (LIRInstruction op : block.lir()) {
                curInstruction = op;

                op.forEachInput(useProc);
                if (op.hasCall()) {
                    for (CiRegister register : frameMap.registerConfig.getCallerSaveRegisters()) {
                        curRegistersLive[register.number] = null;
                    }
                }
                curRegistersDefined.clear();
                op.forEachAlive(useProc);
                op.forEachState(useProc);
                op.forEachTemp(defProc);
                op.forEachOutput(defProc);

                curInstruction = null;
            }

            for (LIRBlock sux : block.getLIRSuccessors()) {
                if (sux.phis != null) {
                    assert beforeRegisterAllocation;
                    curInstruction = sux.phis;
                    sux.phis.forEachInput(block, useProc);
                }
            }

            setLiveOutFor(block, curVariablesLive);
        }
    }

    private CiValue use(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

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

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (mode == OperandMode.Alive) {
                curRegistersDefined.set(regNum);
            }

            if (beforeRegisterAllocation && curRegistersLive[regNum] != value) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live registers: %s", Arrays.toString(curRegistersLive));
                TTY.println("ERROR: Use of fixed register %s that is not defined in this block", value);
                throw Util.shouldNotReachHere();
            }
        }
        return value;
    }

    private CiValue def(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

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
            if (mode == OperandMode.Output) {
                curVariablesLive.set(variableIdx);
            }

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (curRegistersDefined.get(regNum)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("ERROR: Same register defined twice in the same instruction: %s", value);
                throw Util.shouldNotReachHere();
            }
            curRegistersDefined.set(regNum);

            if (beforeRegisterAllocation) {
                if (mode == OperandMode.Output) {
                    curRegistersLive[regNum] = value;
                } else {
                    curRegistersLive[regNum] = null;
                }
            }
        }
        return value;
    }

    private static CiValue allowed(Object op, CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        if ((isVariable(value)  && flags.contains(OperandFlag.Register)) ||
            (isRegister(value)  && flags.contains(OperandFlag.Register)) ||
            (isStackSlot(value) && flags.contains(OperandFlag.Stack)) ||
            (isConstant(value)  && flags.contains(OperandFlag.Constant) && mode != OperandMode.Output) ||
            (isIllegal(value)   && flags.contains(OperandFlag.Illegal))) {
            return value;
        }
        TTY.println("instruction %s", op);
        TTY.println("mode: %s  flags: %s", mode, flags);
        TTY.println("Unexpected value: %s %s", value.getClass().getSimpleName(), value);
        throw Util.shouldNotReachHere();
    }
}
