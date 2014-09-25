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
package com.oracle.graal.lir;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * This class represents garbage collection and deoptimization information attached to a LIR
 * instruction.
 */
public class LIRFrameState {

    public final BytecodeFrame topFrame;
    private final VirtualObject[] virtualObjects;
    public final LabelRef exceptionEdge;
    protected DebugInfo debugInfo;

    public LIRFrameState(BytecodeFrame topFrame, VirtualObject[] virtualObjects, LabelRef exceptionEdge) {
        this.topFrame = topFrame;
        this.virtualObjects = virtualObjects;
        this.exceptionEdge = exceptionEdge;
    }

    public boolean hasDebugInfo() {
        return debugInfo != null;
    }

    public DebugInfo debugInfo() {
        assert debugInfo != null : "debug info not allocated yet";
        return debugInfo;
    }

    /**
     * Iterates the frame state and calls the {@link InstructionValueProcedure} for every variable.
     *
     * @param proc The procedure called for variables.
     */
    public void forEachState(LIRInstruction inst, InstructionValueProcedure proc) {
        forEachState(inst, InstructionValueProcedureBase.wrap(proc));
    }

    void forEachState(LIRInstruction inst, InstructionValueProcedureBase proc) {
        for (BytecodeFrame cur = topFrame; cur != null; cur = cur.caller()) {
            processValues(inst, cur.values, proc);
        }
        if (virtualObjects != null) {
            for (VirtualObject obj : virtualObjects) {
                processValues(inst, obj.getValues(), proc);
            }
        }
    }

    /**
     * We filter out constant and illegal values ourself before calling the procedure, so
     * {@link OperandFlag#CONST} and {@link OperandFlag#ILLEGAL} need not be set.
     */
    protected static final EnumSet<OperandFlag> STATE_FLAGS = EnumSet.of(OperandFlag.REG, OperandFlag.STACK);

    protected void processValues(LIRInstruction inst, Value[] values, InstructionValueProcedureBase proc) {
        for (int i = 0; i < values.length; i++) {
            Value value = values[i];
            values[i] = processValue(inst, proc, value);
        }
    }

    protected Value processValue(LIRInstruction inst, InstructionValueProcedureBase proc, Value value) {
        if (processed(value)) {
            return proc.processValue(inst, value, OperandMode.ALIVE, STATE_FLAGS);
        }
        return value;
    }

    protected boolean processed(Value value) {
        if (isIllegal(value)) {
            // Ignore dead local variables.
            return false;
        } else if (isConstant(value)) {
            // Ignore constants, the register allocator does not need to see them.
            return false;
        } else if (isVirtualObject(value)) {
            assert Arrays.asList(virtualObjects).contains(value);
            return false;
        } else {
            return true;
        }
    }

    /**
     * Called by the register allocator before {@link #markLocation} to initialize the frame state.
     *
     * @param frameMap The frame map.
     * @param canHaveRegisters True if there can be any register map entries.
     */
    public void initDebugInfo(FrameMap frameMap, boolean canHaveRegisters) {
        debugInfo = new DebugInfo(topFrame, frameMap.initReferenceMap(canHaveRegisters));
    }

    /**
     * Called by the register allocator to mark the specified location as a reference in the
     * reference map of the debug information. The tracked location can be a {@link RegisterValue}
     * or a {@link StackSlot}. Note that a {@link Constant} is automatically tracked.
     *
     * @param location The location to be added to the reference map.
     * @param frameMap The frame map.
     */
    public void markLocation(Value location, FrameMap frameMap) {
        frameMap.setReference(location, debugInfo.getReferenceMap());
    }

    /**
     * Called by the register allocator after all locations are marked.
     *
     * @param op The instruction to which this frame state belongs.
     * @param frameMap The frame map.
     */
    public void finish(LIRInstruction op, FrameMap frameMap) {
    }

    @Override
    public String toString() {
        return debugInfo != null ? debugInfo.toString() : topFrame != null ? topFrame.toString() : "<empty>";
    }
}
