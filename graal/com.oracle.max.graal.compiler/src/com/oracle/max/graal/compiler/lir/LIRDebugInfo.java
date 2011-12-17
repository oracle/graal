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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.graal.compiler.lir.FrameMap.StackBlock;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

/**
 * This class represents debugging and deoptimization information attached to a LIR instruction.
 */
public class LIRDebugInfo {
    public final CiFrame topFrame;
    private final CiVirtualObject[] virtualObjects;
    private final List<CiStackSlot> pointerSlots;
    public final LabelRef exceptionEdge;
    private CiDebugInfo debugInfo;

    public LIRDebugInfo(CiFrame topFrame, CiVirtualObject[] virtualObjects, List<CiStackSlot> pointerSlots, LabelRef exceptionEdge) {
        this.topFrame = topFrame;
        this.virtualObjects = virtualObjects;
        this.pointerSlots = pointerSlots;
        this.exceptionEdge = exceptionEdge;
    }

    public CiDebugInfo debugInfo() {
        assert debugInfo != null : "debug info not allocated yet";
        return debugInfo;
    }

    public boolean hasDebugInfo() {
        return debugInfo != null;
    }


    /**
     * Iterator interface for iterating all variables of a frame state.
     */
    public interface ValueProcedure {
        /**
         * The iterator method.
         *
         * @param value The variable that is iterated.
         * @return The new value that should replace variable, or {@code null} if the variable should remain unchanged.
         */
        CiValue doValue(CiValue value);
    }


    /**
     * Iterates the frame state and calls the {@link ValueProcedure} for every variable.
     *
     * @param proc The procedure called for variables.
     */
    public void forEachLiveStateValue(ValueProcedure proc) {
        for (CiFrame cur = topFrame; cur != null; cur = cur.caller()) {
            processValues(cur.values, proc);
        }
        if (virtualObjects != null) {
            for (CiVirtualObject obj : virtualObjects) {
                processValues(obj.values(), proc);
            }
        }
    }

    private void processValues(CiValue[] values, ValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            CiValue value = values[i];
            if (value instanceof CiMonitorValue) {
                CiMonitorValue monitor = (CiMonitorValue) value;

                if (monitor.owner instanceof CiVariable) {
                    CiValue newValue = proc.doValue(monitor.owner);
                    if (newValue != null) {
                        values[i] = new CiMonitorValue(newValue, monitor.lockData, monitor.eliminated);
                    }
                }

            } else {
                if (value instanceof CiVariable) {
                    CiValue newValue = proc.doValue(value);
                    if (newValue != null) {
                        values[i] = newValue;
                    }
                } else {
                    // Nothing to do for these types.
                    assert value == CiValue.IllegalValue || value instanceof CiConstant || value instanceof CiStackSlot || (value instanceof CiVirtualObject && Arrays.asList(virtualObjects).contains(value));
                }
            }
        }
    }

    /**
     * Create the initial {@link CiDebugInfo} object. This initializes the reference maps.
     * This method requires the size of the stack frame to be known, i.e., this method must be called
     * after the register allocator has allocated all spill slots and finalized the frame.
     *
     * @param op The instruction that contains this debug info.
     * @param frameMap The frame map used for the compilation.
     */
    public void initDebugInfo(LIRInstruction op, FrameMap frameMap) {
        CiBitMap frameRefMap = frameMap.initFrameRefMap();
        CiBitMap regRefMap = op.hasCall() ? null : new CiBitMap(frameMap.target.arch.registerReferenceMapBitCount);

        debugInfo = new CiDebugInfo(topFrame, regRefMap, frameRefMap);

        // Add locks that are in the designated frame area.
        for (CiFrame cur = topFrame; cur != null; cur = cur.caller()) {
            for (int i = 0; i < cur.numLocks; i++) {
                CiMonitorValue lock = (CiMonitorValue) cur.values[i + cur.numLocals + cur.numStack];
                if (lock.lockData != null) {
                    cur.values[i + cur.numLocals + cur.numStack] = new CiMonitorValue(lock.owner, frameMap.toStackSlot((StackBlock) lock.lockData), lock.eliminated);
                }
            }
        }

        // Add additional stack slots for outgoing method parameters.
        if (pointerSlots != null) {
            for (CiStackSlot v : pointerSlots) {
                setReference(v, frameMap);
            }
        }
    }

    /**
     * Marks the specified location as a reference in the reference map of the debug information.
     * The location must be a {@link CiRegisterValue} or a {@link CiStackSlot}. Note that a {@link CiAddress}
     * cannot be tracked by reference maps, and that a {@link CiConstant} does not have to be added
     * manually because it is automatically tracked.
     *
     * @param location The stack slot or register to be added to the reference map.
     * @param frameMap The frame map used for the compilation.
     */
    public void setReference(CiValue location, FrameMap frameMap) {
        if (location instanceof CiStackSlot) {
            CiStackSlot stackSlot = (CiStackSlot) location;
            int offset = frameMap.offsetForStackSlot(stackSlot);
            assert offset % frameMap.target.wordSize == 0 : "must be aligned";
            setBit(debugInfo.frameRefMap, offset / frameMap.target.wordSize);

        } else if (location instanceof CiRegisterValue) {
            CiRegister register = ((CiRegisterValue) location).reg;
            setBit(debugInfo.registerRefMap, register.number);

        } else {
            throw Util.shouldNotReachHere();
        }
    }

    private static void setBit(CiBitMap refMap, int bit) {
        assert bit >= 0 && bit < refMap.size() : "register out of range";
        assert !refMap.get(bit) : "Ref map entry already set";
        refMap.set(bit);
    }

    @Override
    public String toString() {
        return debugInfo != null ? debugInfo.toString() : topFrame.toString();
    }
}
