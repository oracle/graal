/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.alloc.simple;

import static com.oracle.graal.alloc.util.LocationUtil.*;

import java.util.*;

import com.oracle.graal.alloc.util.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.cfg.*;

public abstract class AssignRegisters {
    public final LIR lir;
    public final FrameMap frameMap;

    public AssignRegisters(LIR lir, FrameMap frameMap) {
        this.lir = lir;
        this.frameMap = frameMap;
    }

    private BitSet curRegisterRefMap;
    private BitSet curFrameRefMap;

    public void execute() {
        ValueProcedure useProc =          new ValueProcedure() { @Override public Value doValue(Value value) { return use(value); } };
        ValueProcedure defProc =          new ValueProcedure() { @Override public Value doValue(Value value) { return def(value); } };
        ValueProcedure setReferenceProc = new ValueProcedure() { @Override public Value doValue(Value value) { return setReference(value); } };
        StateProcedure finishProc =       new StateProcedure() { @Override public void doState(LIRFrameState state) { state.finish((BitSet) (curRegisterRefMap.clone()), (BitSet) (curFrameRefMap.clone()), frameMap); } };

        Debug.log("==== start assign registers ====");
        for (int i = lir.linearScanOrder().size() - 1; i >= 0; i--) {
            Block block = lir.linearScanOrder().get(i);
            Debug.log("start block %s", block);

            curRegisterRefMap = frameMap.initRegisterRefMap();
            curFrameRefMap = frameMap.initFrameRefMap();

            // Put all values live at the end of the block into the reference map.
            locationsForBlockEnd(block).forEachLocation(setReferenceProc);

            for (int j = block.lir.size() - 1; j >= 0; j--) {
                LIRInstruction op = block.lir.get(j);
                Debug.log("  op %d %s", op.id(), op);

                op.forEachOutput(defProc);
                op.forEachTemp(defProc);
                op.forEachState(useProc);
                op.forEachAlive(useProc);

                // Build the reference map for the GC.
                op.forEachState(finishProc);

                // Process input operands after assigning the reference map, so that input operands that are used
                // for the last time at this instruction are not part of the reference map.
                op.forEachInput(useProc);
            }
            Debug.log("end block %s", block);
        }
        Debug.log("==== end assign registers ====");
    }

    private Value use(Value value) {
        Debug.log("    use %s", value);
        if (isLocation(value)) {
            Value location = asLocation(value).location;
            frameMap.setReference(location, curRegisterRefMap, curFrameRefMap);
            return location;
        } else {
            frameMap.setReference(value, curRegisterRefMap, curFrameRefMap);
            return value;
        }
    }

    private Value def(Value value) {
        Debug.log("    def %s", value);
        if (isLocation(value)) {
            Value location = asLocation(value).location;
            frameMap.clearReference(location, curRegisterRefMap, curFrameRefMap);
            return location;
        } else {
            frameMap.clearReference(value, curRegisterRefMap, curFrameRefMap);
            return value;
        }
    }

    private Value setReference(Value value) {
        Debug.log("    setReference %s", value);
        frameMap.setReference(asLocation(value).location, curRegisterRefMap, curFrameRefMap);
        return value;
    }

    protected abstract LocationMap locationsForBlockEnd(Block block);
}
