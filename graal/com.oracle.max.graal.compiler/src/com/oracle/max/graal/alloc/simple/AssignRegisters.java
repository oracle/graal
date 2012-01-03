/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.alloc.simple;

import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.util.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;

public abstract class AssignRegisters {
    public final LIR lir;
    public final FrameMap frameMap;

    public AssignRegisters(LIR lir, FrameMap frameMap) {
        this.lir = lir;
        this.frameMap = frameMap;
    }

    private CiBitMap curRegisterRefMap;
    private CiBitMap curFrameRefMap;

    public void execute() {
        ValueProcedure useProc =          new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return use(value); } };
        ValueProcedure defProc =          new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return def(value); } };
        ValueProcedure setReferenceProc = new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return setReference(value); } };

        trace(1, "==== start assign registers ====");
        for (int i = lir.linearScanOrder().size() - 1; i >= 0; i--) {
            LIRBlock block = lir.linearScanOrder().get(i);
            trace(1, "start block %s", block);
            assert block.phis == null : "Register assignment must run after phi functions have been replaced by moves";

            curRegisterRefMap = frameMap.initRegisterRefMap();
            curFrameRefMap = frameMap.initFrameRefMap();

            // Put all values live at the end of the block into the reference map.
            locationsForBlockEnd(block).forEachLocation(setReferenceProc);

            for (int j = block.lir().size() - 1; j >= 0; j--) {
                LIRInstruction op = block.lir().get(j);
                trace(1, "  op %d %s", op.id(), op);

                op.forEachOutput(defProc);
                op.forEachTemp(defProc);
                op.forEachState(useProc);
                op.forEachAlive(useProc);

                if (op.info != null) {
                    trace(3, "    registerRefMap: %s  frameRefMap: %s", curRegisterRefMap, curFrameRefMap);
                    op.info.finish(new CiBitMap(curRegisterRefMap), new CiBitMap(curFrameRefMap), frameMap);

                    if (op instanceof LIRXirInstruction) {
                        LIRXirInstruction xir = (LIRXirInstruction) op;
                        if (xir.infoAfter != null) {
                            xir.infoAfter.finish(op.hasCall() ? null : new CiBitMap(curRegisterRefMap), new CiBitMap(curFrameRefMap), frameMap);
                        }
                    }
                }

                // Process input operands after assigning the reference map, so that input operands that are used
                // for the last time at this instruction are not part of the reference map.
                op.forEachInput(useProc);
            }
            trace(1, "end block %s", block);
        }
        trace(1, "==== end assign registers ====");
    }

    private CiValue use(CiValue value) {
        trace(3, "    use %s", value);
        if (isLocation(value)) {
            CiValue location = asLocation(value).location;
            frameMap.setReference(location, curRegisterRefMap, curFrameRefMap);
            return location;
        } else {
            frameMap.setReference(value, curRegisterRefMap, curFrameRefMap);
            return value;
        }
    }

    private CiValue def(CiValue value) {
        trace(3, "    def %s", value);
        if (isLocation(value)) {
            CiValue location = asLocation(value).location;
            frameMap.clearReference(location, curRegisterRefMap, curFrameRefMap);
            return location;
        } else {
            frameMap.clearReference(value, curRegisterRefMap, curFrameRefMap);
            return value;
        }
    }

    private CiValue setReference(CiValue value) {
        trace(3, "    setReference %s", value);
        frameMap.setReference(asLocation(value).location, curRegisterRefMap, curFrameRefMap);
        return value;
    }

    protected abstract LocationMap locationsForBlockEnd(LIRBlock block);

    private static void trace(int level, String format, Object...args) {
        if (GraalOptions.TraceRegisterAllocationLevel >= level) {
            TTY.println(format, args);
        }
    }
}
