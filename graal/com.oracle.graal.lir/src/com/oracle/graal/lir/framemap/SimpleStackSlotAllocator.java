/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.framemap;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.*;

public class SimpleStackSlotAllocator implements StackSlotAllocator {

    public void allocateStackSlots(FrameMapBuilderImpl builder, LIRGenerationResult res) {
        StackSlot[] mapping = new StackSlot[builder.getNumberOfStackSlots()];
        for (VirtualStackSlot virtualSlot : builder.getStackSlots()) {
            final StackSlot slot;
            if (virtualSlot instanceof SimpleVirtualStackSlot) {
                slot = mapSimpleVirtualStackSlot(builder, (SimpleVirtualStackSlot) virtualSlot);
            } else if (virtualSlot instanceof VirtualStackSlotRange) {
                slot = mapVirtualStackSlotRange(builder, (VirtualStackSlotRange) virtualSlot);
            } else {
                throw GraalInternalError.shouldNotReachHere("Unknown VirtualStackSlot: " + virtualSlot);
            }
            mapping[virtualSlot.getId()] = slot;
        }
        updateLIR(res, mapping);
    }

    protected void updateLIR(LIRGenerationResult res, StackSlot[] mapping) {
        try (Scope scope = Debug.scope("StackSlotMappingLIR")) {
            ValueProcedure updateProc = (value, mode, flags) -> {
                if (isVirtualStackSlot(value)) {
                    StackSlot stackSlot = mapping[asVirtualStackSlot(value).getId()];
                    Debug.log("map %s -> %s", value, stackSlot);
                    return stackSlot;
                }
                return value;
            };
            for (AbstractBlock<?> block : res.getLIR().getControlFlowGraph().getBlocks()) {
                try (Indent indent0 = Debug.logAndIndent("block: %s", block)) {
                    for (LIRInstruction inst : res.getLIR().getLIRforBlock(block)) {
                        try (Indent indent1 = Debug.logAndIndent("Inst: %d: %s", inst.id(), inst)) {
                            inst.forEachAlive(updateProc);
                            inst.forEachInput(updateProc);
                            inst.forEachOutput(updateProc);
                            inst.forEachTemp(updateProc);
                            inst.forEachState(updateProc);
                        }
                    }
                }
            }
        }
    }

    protected StackSlot mapSimpleVirtualStackSlot(FrameMapBuilderImpl builder, SimpleVirtualStackSlot virtualStackSlot) {
        return builder.getFrameMap().allocateSpillSlot(virtualStackSlot.getLIRKind());
    }

    protected StackSlot mapVirtualStackSlotRange(FrameMapBuilderImpl builder, VirtualStackSlotRange virtualStackSlot) {
        return builder.getFrameMap().allocateStackSlots(virtualStackSlot.getSlots(), virtualStackSlot.getObjects());
    }
}
