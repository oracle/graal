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

public final class HotSpotReferenceMap implements ReferenceMap, Serializable {

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

    private HotSpotReferenceMap(HotSpotReferenceMap other) {
        this.registerRefMap = (BitSet) other.registerRefMap.clone();
        this.frameRefMap = (BitSet) other.frameRefMap.clone();
        this.target = other.target;
    }

    @Override
    public ReferenceMap clone() {
        return new HotSpotReferenceMap(this);
    }

    // setters

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

    // clear

    private static void clearOop(BitSet map, int startIdx, LIRKind kind) {
        int length = kind.getPlatformKind().getVectorLength();
        map.clear(BITS_PER_WORD * startIdx, BITS_PER_WORD * (startIdx + length) - 1);
    }

    private static void clearNarrowOop(BitSet map, int idx, LIRKind kind) {
        int length = kind.getPlatformKind().getVectorLength();
        int nextIdx = idx + (length + 1) / 2;
        map.clear(BITS_PER_WORD * idx, BITS_PER_WORD * nextIdx - 1);
    }

    public void clearRegister(int idx, LIRKind kind) {

        PlatformKind platformKind = kind.getPlatformKind();
        int bytesPerElement = target.getSizeInBytes(platformKind) / platformKind.getVectorLength();

        if (bytesPerElement == target.wordSize) {
            clearOop(registerRefMap, idx, kind);
        } else if (bytesPerElement == target.wordSize / 2) {
            clearNarrowOop(registerRefMap, idx, kind);
        } else {
            assert kind.isValue() : "unsupported reference kind " + kind;
        }
    }

    public void clearStackSlot(int offset, LIRKind kind) {

        PlatformKind platformKind = kind.getPlatformKind();
        int bytesPerElement = target.getSizeInBytes(platformKind) / platformKind.getVectorLength();
        assert offset % bytesPerElement == 0 : "unaligned value in ReferenceMap";

        if (bytesPerElement == target.wordSize) {
            clearOop(frameRefMap, offset / target.wordSize, kind);
        } else if (bytesPerElement == target.wordSize / 2) {
            if (platformKind.getVectorLength() > 1) {
                clearNarrowOop(frameRefMap, offset / target.wordSize, kind);
            } else {
                // in this case, offset / target.wordSize may not divide evenly
                // so setNarrowOop won't work correctly
                int idx = offset / target.wordSize;
                if (kind.isReference(0)) {
                    if (offset % target.wordSize == 0) {
                        frameRefMap.clear(BITS_PER_WORD * idx + 1);
                        if (!frameRefMap.get(BITS_PER_WORD * idx + 2)) {
                            // only reset the first bit if there is no other narrow oop
                            frameRefMap.clear(BITS_PER_WORD * idx);
                        }
                    } else {
                        frameRefMap.clear(BITS_PER_WORD * idx + 2);
                        if (!frameRefMap.get(BITS_PER_WORD * idx + 1)) {
                            // only reset the first bit if there is no other narrow oop
                            frameRefMap.clear(BITS_PER_WORD * idx);
                        }
                    }
                }
            }
        } else {
            assert kind.isValue() : "unknown reference kind " + kind;
        }
    }

    public void updateUnion(ReferenceMap otherArg) {
        HotSpotReferenceMap other = (HotSpotReferenceMap) otherArg;
        if (registerRefMap != null) {
            assert other.registerRefMap != null;
            updateUnionBitSetRaw(registerRefMap, other.registerRefMap);
        } else {
            assert other.registerRefMap == null || other.registerRefMap.cardinality() == 0 : "Target register reference map is empty but the source is not: " + other.registerRefMap;
        }
        updateUnionBitSetRaw(frameRefMap, other.frameRefMap);
    }

    /**
     * Update {@code src} with the union of {@code src} and {@code dst}.
     *
     * @see HotSpotReferenceMap#registerRefMap
     * @see HotSpotReferenceMap#frameRefMap
     */
    private static void updateUnionBitSetRaw(BitSet dst, BitSet src) {
        assert dst.size() == src.size();
        assert UpdateUnionVerifier.verifyUpate(dst, src);
        dst.or(src);
    }

    private enum UpdateUnionVerifier {
        NoReference,
        WideOop,
        NarrowOopLowerHalf,
        NarrowOopUpperHalf,
        TwoNarrowOops,
        Illegal;

        /**
         * Create enum values from BitSet.
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
         *
         * @see HotSpotReferenceMap#registerRefMap
         * @see HotSpotReferenceMap#frameRefMap
         */
        static UpdateUnionVerifier getFromBits(int idx, BitSet set) {
            int n = (set.get(idx) ? 1 : 0) << 0 | (set.get(idx + 1) ? 1 : 0) << 1 | (set.get(idx + 2) ? 1 : 0) << 2;
            switch (n) {
                case 0:
                    return NoReference;
                case 1:
                    return WideOop;
                case 3:
                    return NarrowOopLowerHalf;
                case 5:
                    return NarrowOopUpperHalf;
                case 7:
                    return TwoNarrowOops;
                default:
                    return Illegal;
            }
        }

        String toBitString() {
            int bits = toBit(this);
            if (bits == -1) {
                return "---";
            }
            return String.format("%3s", Integer.toBinaryString(bits)).replace(' ', '0');
        }

        static int toBit(UpdateUnionVerifier type) {
            switch (type) {
                case NoReference:
                    return 0;
                case WideOop:
                    return 1;
                case NarrowOopLowerHalf:
                    return 3;
                case NarrowOopUpperHalf:
                    return 5;
                case TwoNarrowOops:
                    return 7;
                default:
                    return -1;
            }
        }

        private static boolean verifyUpate(BitSet dst, BitSet src) {
            for (int idx = 0; idx < dst.size(); idx += BITS_PER_WORD) {
                if (!verifyUpdateEntry(idx, dst, src)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean verifyUpdateEntry(int idx, BitSet dst, BitSet src) {
            UpdateUnionVerifier dstType = UpdateUnionVerifier.getFromBits(idx, dst);
            UpdateUnionVerifier srcType = UpdateUnionVerifier.getFromBits(idx, src);

            if (dstType == UpdateUnionVerifier.Illegal || srcType == UpdateUnionVerifier.Illegal) {
                assert false : String.format("Illegal RefMap bit pattern: %s (0b%s), %s (0b%s)", dstType, dstType.toBitString(), srcType, srcType.toBitString());
                return false;
            }
            switch (dstType) {
                case NoReference:
                    return true;
                case WideOop:
                    switch (srcType) {
                        case NoReference:
                        case WideOop:
                            return true;
                        default:
                            assert false : String.format("Illegal RefMap combination: %s (0b%s), %s (0b%s)", dstType, dstType.toBitString(), srcType, srcType.toBitString());
                            return false;
                    }
                default:
                    return true;
            }
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotReferenceMap) {
            HotSpotReferenceMap that = (HotSpotReferenceMap) obj;
            if (this.frameRefMap.equals(that.frameRefMap) && Objects.equals(this.registerRefMap, that.registerRefMap) && this.target.equals(that.target)) {
                return true;
            }
        }
        return false;
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
