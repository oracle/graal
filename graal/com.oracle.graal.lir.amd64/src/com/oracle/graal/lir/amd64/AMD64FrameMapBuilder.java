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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;

public class AMD64FrameMapBuilder extends DelayedFrameMapBuilder {

    private TrackedVirtualStackSlot rbpSpillSlot;

    public AMD64FrameMapBuilder(FrameMapFactory factory, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        super(factory, codeCache, registerConfig);
    }

    /**
     * For non-leaf methods, RBP is preserved in the special stack slot required by the HotSpot
     * runtime for walking/inspecting frames of such methods.
     */
    public VirtualStackSlot allocateRBPSpillSlot() {
        rbpSpillSlot = (TrackedVirtualStackSlot) allocateSpillSlot(LIRKind.value(Kind.Long));
        return rbpSpillSlot;
    }

    @Override
    public void freeSpillSlot(VirtualStackSlot slot) {
        assert slot != null;
        if (slot.equals(rbpSpillSlot)) {
            rbpSpillSlot = null;
        } else {
            super.freeSpillSlot(slot);
        }
    }

    @Override
    protected void mapStackSlots(FrameMap frameMap, HashMap<VirtualStackSlot, StackSlot> mapping) {
        if (rbpSpillSlot != null) {
            StackSlot reservedSlot = rbpSpillSlot.transform(frameMap);
            assert asStackSlot(reservedSlot).getRawOffset() == -16 : asStackSlot(reservedSlot).getRawOffset();
            mapping.put(rbpSpillSlot, reservedSlot);
        }
        super.mapStackSlots(frameMap, mapping);
    }
}
