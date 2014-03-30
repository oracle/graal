/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.type.*;

public class HotSpotReferenceMap implements ReferenceMap, Serializable {

    private static final long serialVersionUID = -1052183095979496819L;

    /**
     * Contains 2 bits per register.
     * <ul>
     * <li>bit0 = 0: contains no references</li>
     * <li>bit0 = 1, bit1 = 0: contains a wide oop</li>
     * <li>bit0 = 1, bit1 = 1: contains a narrow oop</li>
     * </ul>
     */
    private final BitSet registerRefMap;

    /**
     * Contains 3 bits per stack slot.
     * <ul>
     * <li>bit0 = 0: contains no references</li>
     * <li>bit0 = 1, bit1+2 = 0: contains a wide oop</li>
     * <li>bit0 = 1, bit1 = 1: contains a narrow oop in the lower half</li>
     * <li>bit0 = 1, bit2 = 1: contains a narrow oop in the upper half</li>
     * </ul>
     */
    private final BitSet frameRefMap;

    private final int frameSlotSize;

    public HotSpotReferenceMap(int registerCount, int frameSlotCount, int frameSlotSize) {
        if (registerCount > 0) {
            this.registerRefMap = new BitSet(registerCount * 2);
        } else {
            this.registerRefMap = null;
        }
        this.frameRefMap = new BitSet(frameSlotCount * 3);
        this.frameSlotSize = frameSlotSize;
    }

    public void setRegister(int idx, PlatformKind kind) {
        if (kind == Kind.Object) {
            registerRefMap.set(2 * idx);
        } else if (kind == NarrowOopStamp.NarrowOop) {
            registerRefMap.set(2 * idx);
            registerRefMap.set(2 * idx + 1);
        }
    }

    public PlatformKind getRegister(int idx) {
        int refMapIndex = idx * 2;
        if (registerRefMap.get(refMapIndex)) {
            if (registerRefMap.get(refMapIndex + 1)) {
                return NarrowOopStamp.NarrowOop;
            } else {
                return Kind.Object;
            }
        }
        return null;
    }

    public void setStackSlot(int offset, PlatformKind kind) {
        int idx = offset / frameSlotSize;
        if (kind == Kind.Object) {
            assert offset % frameSlotSize == 0;
            frameRefMap.set(3 * idx);
        } else if (kind == NarrowOopStamp.NarrowOop) {
            frameRefMap.set(3 * idx);
            if (offset % frameSlotSize == 0) {
                frameRefMap.set(3 * idx + 1);
            } else {
                assert offset % frameSlotSize == frameSlotSize / 2;
                frameRefMap.set(3 * idx + 2);
            }
        }
    }

    public boolean hasRegisterRefMap() {
        return registerRefMap != null && registerRefMap.size() > 0;
    }

    public boolean hasFrameRefMap() {
        return frameRefMap != null && frameRefMap.size() > 0;
    }

    public void appendRegisterMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int reg = registerRefMap.nextSetBit(0); reg >= 0; reg = registerRefMap.nextSetBit(reg + 2)) {
            sb.append(' ').append(formatter.formatRegister(reg / 2));
        }
    }

    public void appendFrameMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int slot = frameRefMap.nextSetBit(0); slot >= 0; slot = frameRefMap.nextSetBit(slot + 3)) {
            sb.append(' ').append(formatter.formatStackSlot(slot / 3));
        }
    }
}
