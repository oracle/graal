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
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;

/**
 * This class represents garbage collection and deoptimization information attached to a LIR
 * instruction.
 */
public class LIRFrameState {

    public final BytecodeFrame topFrame;
    private final VirtualObject[] virtualObjects;
    public final LabelRef exceptionEdge;
    private DebugInfo debugInfo;

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
     * Iterates the frame state and calls the {@link ValueProcedure} for every variable.
     * 
     * @param proc The procedure called for variables.
     */
    public void forEachState(ValueProcedure proc) {
        for (BytecodeFrame cur = topFrame; cur != null; cur = cur.caller()) {
            processValues(cur.values, proc);
        }
        if (virtualObjects != null) {
            for (VirtualObject obj : virtualObjects) {
                processValues(obj.getValues(), proc);
            }
        }
    }

    /**
     * We filter out constant and illegal values ourself before calling the procedure, so
     * {@link OperandFlag#CONST} and {@link OperandFlag#ILLEGAL} need not be set.
     */
    protected static final EnumSet<OperandFlag> STATE_FLAGS = EnumSet.of(OperandFlag.REG, OperandFlag.STACK);

    protected void processValues(Value[] values, ValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            Value value = values[i];
            values[i] = processValue(proc, value);
        }
    }

    protected Value processValue(ValueProcedure proc, Value value) {
        if (processed(value)) {
            return proc.doValue(value, OperandMode.ALIVE, STATE_FLAGS);
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

    public void finish(BitSet registerRefMap, BitSet frameRefMap) {
        debugInfo = new DebugInfo(topFrame, registerRefMap, frameRefMap);
    }

    @Override
    public String toString() {
        return debugInfo != null ? debugInfo.toString() : topFrame != null ? topFrame.toString() : "<empty>";
    }
}
