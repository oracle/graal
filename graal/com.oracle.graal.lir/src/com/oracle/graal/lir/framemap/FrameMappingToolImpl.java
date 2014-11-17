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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.framemap.DelayedFrameMapBuilder.SimpleVirtualStackSlot;
import com.oracle.graal.lir.framemap.DelayedFrameMapBuilder.VirtualStackSlotRange;

public class FrameMappingToolImpl implements FrameMappingTool {

    private final Map<VirtualStackSlot, StackSlot> mapping;
    private final DelayedFrameMapBuilder builder;

    public FrameMappingToolImpl(Map<VirtualStackSlot, StackSlot> mapping, DelayedFrameMapBuilder builder) {
        this.mapping = mapping;
        this.builder = builder;
    }

    public StackSlot getStackSlot(VirtualStackSlot slot) {
        return mapping.get(slot);
    }

    public void mapStackSlots() {
        for (VirtualStackSlot virtualSlot : builder.getStackSlots()) {
            final StackSlot slot;
            if (virtualSlot instanceof SimpleVirtualStackSlot) {
                slot = mapSimpleVirtualStackSlot((SimpleVirtualStackSlot) virtualSlot);
            } else if (virtualSlot instanceof VirtualStackSlotRange) {
                slot = mapVirtualStackSlotRange((VirtualStackSlotRange) virtualSlot);
            } else {
                throw GraalInternalError.shouldNotReachHere("Unknown VirtualStackSlot: " + virtualSlot);
            }
            mapping.put(virtualSlot, slot);
        }
    }

    protected StackSlot mapSimpleVirtualStackSlot(SimpleVirtualStackSlot virtualStackSlot) {
        return builder.frameMap.allocateSpillSlot(virtualStackSlot.getLIRKind());
    }

    protected StackSlot mapVirtualStackSlotRange(VirtualStackSlotRange virtualStackSlot) {
        return builder.frameMap.allocateStackSlots(virtualStackSlot.getSlots(), virtualStackSlot.getObjects());
    }
}
