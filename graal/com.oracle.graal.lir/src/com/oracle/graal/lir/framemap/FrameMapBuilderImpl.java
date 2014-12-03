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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.*;

/**
 * A FrameMapBuilder that records allocation.
 */
public class FrameMapBuilderImpl implements FrameMapBuilder {

    private final RegisterConfig registerConfig;
    private final CodeCacheProvider codeCache;
    protected final FrameMap frameMap;
    private final List<VirtualStackSlot> stackSlots;
    private final List<CallingConvention> calls;
    private int numStackSlots;

    public FrameMapBuilderImpl(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        assert registerConfig != null : "No register config!";
        this.registerConfig = registerConfig == null ? codeCache.getRegisterConfig() : registerConfig;
        this.codeCache = codeCache;
        this.frameMap = frameMap;
        this.stackSlots = new ArrayList<>();
        this.calls = new ArrayList<>();
        this.numStackSlots = 0;
    }

    public VirtualStackSlot allocateSpillSlot(LIRKind kind) {
        SimpleVirtualStackSlot slot = new SimpleVirtualStackSlot(numStackSlots++, kind);
        stackSlots.add(slot);
        return slot;
    }

    public VirtualStackSlot allocateStackSlots(int slots, BitSet objects, List<VirtualStackSlot> outObjectStackSlots) {
        if (slots == 0) {
            return null;
        }
        if (outObjectStackSlots != null) {
            throw GraalInternalError.unimplemented();
        }
        VirtualStackSlotRange slot = new VirtualStackSlotRange(numStackSlots++, slots, objects);
        stackSlots.add(slot);
        return slot;
    }

    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    /**
     * Returns the number of {@link VirtualStackSlot}s created by this {@link FrameMapBuilder}. Can
     * be used as an upper bound for an array indexed by {@link VirtualStackSlot#getId()}.
     */
    public int getNumberOfStackSlots() {
        return numStackSlots;
    }

    public void callsMethod(CallingConvention cc) {
        calls.add(cc);
    }

    public FrameMap buildFrameMap(LIRGenerationResult res) {
        FrameMappingTool mapper = new SimpleStackSlotAllocator().allocateStackSlots(this);
        for (CallingConvention cc : calls) {
            frameMap.callsMethod(cc);
        }
        // update LIR
        try (Scope scope = Debug.scope("StackSlotMappingLIR")) {
            ValueProcedure updateProc = (value, mode, flags) -> {
                if (isVirtualStackSlot(value)) {
                    StackSlot stackSlot = mapper.getStackSlot(asVirtualStackSlot(value));
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

        frameMap.finish();
        return frameMap;
    }

    List<VirtualStackSlot> getStackSlots() {
        return stackSlots;
    }

}
