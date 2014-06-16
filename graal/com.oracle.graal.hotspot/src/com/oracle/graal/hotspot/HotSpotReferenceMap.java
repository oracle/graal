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
import com.oracle.graal.compiler.common.*;

public class HotSpotReferenceMap implements ReferenceMap, Serializable {

    private static final long serialVersionUID = -1052183095979496819L;

    private static final int BITS_PER_WORD = 3;

    /**
     * Contains 3 bits per scalar register, and n*3 bits per n-word vector register (e.g., on a
     * 64-bit system, a 256-bit vector register requires 12 reference map bits).
     * <p>
     * These bits can have the following values (LSB first):
     *
     * <pre>
     * 000 - contains no references
     * 100 - contains a wide oop
     * 110 - contains a narrow oop in the lower half
     * 101 - contains a narrow oop in the upper half
     * 111 - contains two narrow oops
     * </pre>
     */
    private final BitSet registerRefMap;

    /**
     * Contains 3 bits per stack word.
     * <p>
     * These bits can have the following values (LSB first):
     *
     * <pre>
     * 000 - contains no references
     * 100 - contains a wide oop
     * 110 - contains a narrow oop in the lower half
     * 101 - contains a narrow oop in the upper half
     * 111 - contains two narrow oops
     * </pre>
     */
    private final BitSet frameRefMap;

    private final TargetDescription target;

    public HotSpotReferenceMap(int registerCount, int frameSlotCount, TargetDescription target) {
        if (registerCount > 0) {
            this.registerRefMap = new BitSet(registerCount * BITS_PER_WORD);
        } else {
            this.registerRefMap = null;
        }
        this.frameRefMap = new BitSet(frameSlotCount * BITS_PER_WORD);
        this.target = target;
    }

    private static void setOop(BitSet map, int startIdx, LIRKind kind) {
        int length = kind.getPlatformKind().getVectorLength();
        map.clear(BITS_PER_WORD * startIdx, BITS_PER_WORD * (startIdx + length) - 1);
        for (int i = 0, idx = BITS_PER_WORD * startIdx; i < length; i++, idx += BITS_PER_WORD) {
            if (kind.isReference(i)) {
                map.set(idx);
            }
        }
    }

    private static void setNarrowOop(BitSet map, int idx, LIRKind kind) {
        int length = kind.getPlatformKind().getVectorLength();
        int nextIdx = idx + (length + 1) / 2;
        map.clear(BITS_PER_WORD * idx, BITS_PER_WORD * nextIdx - 1);
        for (int i = 0, regIdx = BITS_PER_WORD * idx; i < length; i += 2, regIdx += BITS_PER_WORD) {
            if (kind.isReference(i)) {
                map.set(regIdx);
                map.set(regIdx + 1);
            }
            if ((i + 1) < length && kind.isReference(i + 1)) {
                map.set(regIdx);
                map.set(regIdx + 2);
            }
        }
    }

    public void setRegister(int idx, LIRKind kind) {
        if (kind.isDerivedReference()) {
            throw GraalInternalError.shouldNotReachHere("derived reference cannot be inserted in ReferenceMap");
        }

        PlatformKind platformKind = kind.getPlatformKind();
        int bytesPerElement = target.getSizeInBytes(platformKind) / platformKind.getVectorLength();

        if (bytesPerElement == target.wordSize) {
            setOop(registerRefMap, idx, kind);
        } else if (bytesPerElement == target.wordSize / 2) {
            setNarrowOop(registerRefMap, idx, kind);
        } else {
            assert kind.isValue() : "unsupported reference kind " + kind;
        }
    }

    public void setStackSlot(int offset, LIRKind kind) {
        if (kind.isDerivedReference()) {
            throw GraalInternalError.shouldNotReachHere("derived reference cannot be inserted in ReferenceMap");
        }

        PlatformKind platformKind = kind.getPlatformKind();
        int bytesPerElement = target.getSizeInBytes(platformKind) / platformKind.getVectorLength();
        assert offset % bytesPerElement == 0 : "unaligned value in ReferenceMap";

        if (bytesPerElement == target.wordSize) {
            setOop(frameRefMap, offset / target.wordSize, kind);
        } else if (bytesPerElement == target.wordSize / 2) {
            if (platformKind.getVectorLength() > 1) {
                setNarrowOop(frameRefMap, offset / target.wordSize, kind);
            } else {
                // in this case, offset / target.wordSize may not divide evenly
                // so setNarrowOop won't work correctly
                int idx = offset / target.wordSize;
                if (kind.isReference(0)) {
                    frameRefMap.set(BITS_PER_WORD * idx);
                    if (offset % target.wordSize == 0) {
                        frameRefMap.set(BITS_PER_WORD * idx + 1);
                    } else {
                        frameRefMap.set(BITS_PER_WORD * idx + 2);
                    }
                }
            }
        } else {
            assert kind.isValue() : "unknown reference kind " + kind;
        }
    }

    public boolean hasRegisterRefMap() {
        return registerRefMap != null && registerRefMap.size() > 0;
    }

    public boolean hasFrameRefMap() {
        return frameRefMap != null && frameRefMap.size() > 0;
    }

    public void appendRegisterMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int reg = registerRefMap.nextSetBit(0); reg >= 0; reg = registerRefMap.nextSetBit(reg + BITS_PER_WORD)) {
            sb.append(' ').append(formatter.formatRegister(reg / BITS_PER_WORD));
        }
    }

    public void appendFrameMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int slot = frameRefMap.nextSetBit(0); slot >= 0; slot = frameRefMap.nextSetBit(slot + BITS_PER_WORD)) {
            sb.append(' ').append(formatter.formatStackSlot(slot / BITS_PER_WORD));
        }
    }
}
