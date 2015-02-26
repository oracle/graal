/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * Extends {@link LIRFrameState} to handle {@link StackLockValue}s correctly.
 */
class HotSpotLIRFrameState extends LIRFrameState {

    public HotSpotLIRFrameState(BytecodeFrame topFrame, VirtualObject[] virtualObjects, LabelRef exceptionEdge) {
        super(topFrame, virtualObjects, exceptionEdge);
    }

    @Override
    protected Value processValue(LIRInstruction inst, InstructionValueProcedure proc, Value value) {
        if (value instanceof StackLockValue) {
            StackLockValue monitor = (StackLockValue) value;
            if (monitor.getOwner() instanceof Value) {
                Value owner = (Value) monitor.getOwner();
                if (processed(owner)) {
                    monitor.setOwner((JavaValue) proc.doValue(inst, owner, OperandMode.ALIVE, STATE_FLAGS));
                }
            }
            Value slot = monitor.getSlot();
            if (isVirtualStackSlot(slot) && processed(slot)) {
                monitor.setSlot(asStackSlotValue(proc.doValue(inst, slot, OperandMode.ALIVE, STATE_FLAGS)));
            }
            return value;
        } else {
            return super.processValue(inst, proc, value);
        }
    }
}
