/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.compiler.graal.lir.stackslotalloc;

import static jdk.compiler.graal.lir.LIRValueUtil.asVirtualStackSlot;
import static jdk.compiler.graal.lir.LIRValueUtil.isVirtualStackSlot;

import jdk.compiler.graal.core.common.cfg.BasicBlock;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.debug.Indent;
import jdk.compiler.graal.lir.LIRInstruction;
import jdk.compiler.graal.lir.ValueProcedure;
import jdk.compiler.graal.lir.VirtualStackSlot;
import jdk.compiler.graal.lir.framemap.FrameMapBuilderTool;
import jdk.compiler.graal.lir.framemap.SimpleVirtualStackSlot;
import jdk.compiler.graal.lir.framemap.SimpleVirtualStackSlotAlias;
import jdk.compiler.graal.lir.framemap.VirtualStackSlotRange;
import jdk.compiler.graal.lir.gen.LIRGenerationResult;
import jdk.compiler.graal.lir.phases.AllocationPhase;

import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ValueKind;

public class SimpleStackSlotAllocator extends AllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        allocateStackSlots((FrameMapBuilderTool) lirGenRes.getFrameMapBuilder(), lirGenRes);
        lirGenRes.buildFrameMap();
    }

    public void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res) {
        DebugContext debug = res.getLIR().getDebug();
        StackSlot[] mapping = new StackSlot[builder.getNumberOfStackSlots()];
        boolean allocatedFramesizeEnabled = StackSlotAllocatorUtil.allocatedFramesize.isEnabled(debug);
        long currentFrameSize = allocatedFramesizeEnabled ? builder.getFrameMap().currentFrameSize() : 0;
        for (VirtualStackSlot virtualSlot : builder.getStackSlots()) {
            final StackSlot slot;
            if (virtualSlot instanceof SimpleVirtualStackSlot) {
                ValueKind<?> slotKind = virtualSlot.getValueKind();
                slot = builder.getFrameMap().allocateSpillSlot(slotKind);
                StackSlotAllocatorUtil.virtualFramesize.add(debug, builder.getFrameMap().spillSlotSize(slotKind));
            } else if (virtualSlot instanceof SimpleVirtualStackSlotAlias) {
                ValueKind<?> slotKind = ((SimpleVirtualStackSlotAlias) virtualSlot).getAliasedSlot().getValueKind();
                slot = builder.getFrameMap().allocateSpillSlot(slotKind);
                StackSlotAllocatorUtil.virtualFramesize.add(debug, builder.getFrameMap().spillSlotSize(slotKind));
            } else if (virtualSlot instanceof VirtualStackSlotRange) {
                VirtualStackSlotRange slotRange = (VirtualStackSlotRange) virtualSlot;
                slot = builder.getFrameMap().allocateStackMemory(slotRange.getSizeInBytes(), slotRange.getAlignmentInBytes());
                StackSlotAllocatorUtil.virtualFramesize.add(debug, slotRange.getSizeInBytes());
            } else {
                throw GraalError.shouldNotReachHere("Unknown VirtualStackSlot: " + virtualSlot); // ExcludeFromJacocoGeneratedReport
            }
            StackSlotAllocatorUtil.allocatedSlots.increment(debug);
            mapping[virtualSlot.getId()] = slot;
        }
        updateLIR(res, mapping);
        if (allocatedFramesizeEnabled) {
            StackSlotAllocatorUtil.allocatedFramesize.add(debug, builder.getFrameMap().currentFrameSize() - currentFrameSize);
        }
    }

    @SuppressWarnings("try")
    protected void updateLIR(LIRGenerationResult res, StackSlot[] mapping) {
        DebugContext debug = res.getLIR().getDebug();
        try (DebugContext.Scope scope = debug.scope("StackSlotMappingLIR")) {
            ValueProcedure updateProc = (value, mode, flags) -> {
                if (isVirtualStackSlot(value)) {
                    StackSlot stackSlot = mapping[asVirtualStackSlot(value).getId()];
                    if (value instanceof SimpleVirtualStackSlotAlias) {
                        GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of SimpleVirtualStackSlotAlias");
                        // return the same slot, but with the alias's kind.
                        stackSlot = StackSlot.get(value.getValueKind(), stackSlot.getRawOffset(), stackSlot.getRawAddFrameSize());
                    }
                    debug.log("map %s -> %s", value, stackSlot);
                    return stackSlot;
                }
                return value;
            };
            for (BasicBlock<?> block : res.getLIR().getControlFlowGraph().getBlocks()) {
                try (Indent indent0 = debug.logAndIndent("block: %s", block)) {
                    for (LIRInstruction inst : res.getLIR().getLIRforBlock(block)) {
                        try (Indent indent1 = debug.logAndIndent("Inst: %d: %s", inst.id(), inst)) {
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
}
