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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.gen.*;

/**
 * A FrameMapBuilder that records allocation.
 */
public class DelayedFrameMapBuilder implements FrameMapBuilder {

    @FunctionalInterface
    public interface FrameMapFactory {
        FrameMap newFrameMap(RegisterConfig registerConfig);
    }

    private final RegisterConfig registerConfig;
    private final CodeCacheProvider codeCache;
    protected final FrameMap frameMap;
    private final List<VirtualStackSlot> stackSlots;
    private final List<CallingConvention> calls;

    public DelayedFrameMapBuilder(FrameMapFactory factory, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        this.registerConfig = registerConfig == null ? codeCache.getRegisterConfig() : registerConfig;
        this.codeCache = codeCache;
        this.frameMap = factory.newFrameMap(registerConfig);
        this.stackSlots = new ArrayList<>();
        this.calls = new ArrayList<>();
        this.mappables = new ArrayList<>();
    }

    private final List<FrameMappable> mappables;

    public VirtualStackSlot allocateSpillSlot(LIRKind kind) {
        SimpleVirtualStackSlot slot = new SimpleVirtualStackSlot(kind);
        stackSlots.add(slot);
        return slot;
    }

    static class SimpleVirtualStackSlot extends VirtualStackSlot {

        private static final long serialVersionUID = 7654295701165421750L;

        public SimpleVirtualStackSlot(LIRKind lirKind) {
            super(lirKind);
        }

    }

    static class VirtualStackSlotRange extends VirtualStackSlot {

        private static final long serialVersionUID = 5152592950118317121L;
        private final BitSet objects;
        private final int slots;

        public VirtualStackSlotRange(int slots, BitSet objects) {
            super(LIRKind.reference(Kind.Object));
            this.slots = slots;
            this.objects = (BitSet) objects.clone();
        }

        public int getSlots() {
            return slots;
        }

        public BitSet getObjects() {
            return (BitSet) objects.clone();
        }

    }

    public VirtualStackSlot allocateStackSlots(int slots, BitSet objects, List<VirtualStackSlot> outObjectStackSlots) {
        if (slots == 0) {
            return null;
        }
        if (outObjectStackSlots != null) {
            throw GraalInternalError.unimplemented();
        }
        VirtualStackSlotRange slot = new VirtualStackSlotRange(slots, objects);
        stackSlots.add(slot);
        return slot;
    }

    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    public void callsMethod(CallingConvention cc) {
        calls.add(cc);
    }

    public FrameMap buildFrameMap(LIRGenerationResult res) {
        HashMap<VirtualStackSlot, StackSlot> mapping = new HashMap<>();
        // fill
        FrameMappingToolImpl tool = new FrameMappingToolImpl(mapping, this);
        tool.mapStackSlots();
        for (CallingConvention cc : calls) {
            frameMap.callsMethod(cc);
        }
        // rewrite
        mappables.forEach(m -> m.map(tool));

        frameMap.finish();
        return frameMap;
    }

    public void requireMapping(FrameMappable mappable) {
        this.mappables.add(mappable);
    }

    List<VirtualStackSlot> getStackSlots() {
        return stackSlots;
    }

}
