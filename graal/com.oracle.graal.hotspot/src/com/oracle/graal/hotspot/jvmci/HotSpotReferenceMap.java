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
package com.oracle.graal.hotspot.jvmci;

import java.util.*;

import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

public final class HotSpotReferenceMap extends ReferenceMap {

    static final int OOP64 = 0b1010;
    static final int OOP32 = 0b01;
    static final int NARROW_LOW = OOP32;
    static final int NARROW_HIGH = OOP32 << 2;
    static final int NARROW_BOTH = NARROW_LOW | NARROW_HIGH;

    private enum MapEntry {
        NoReference(0),
        WideOop(OOP64),
        NarrowOopLowerHalf(NARROW_LOW),
        NarrowOopUpperHalf(NARROW_HIGH),
        TwoNarrowOops(NARROW_BOTH),
        Illegal(-1);

        MapEntry(int pattern) {
            this.pattern = pattern;
        }

        final int pattern;

        /**
         * Create enum values from OopMap.
         * <p>
         * These bits can have the following values (MSB first):
         *
         * <pre>
         * 0000 - contains no references
         * 1010 - contains a wide oop
         * 0001 - contains a narrow oop in the lower half
         * 0101 - contains a narrow oop in the upper half
         * 0101 - contains two narrow oops
         * </pre>
         *
         * @see HotSpotReferenceMap#registerRefMap
         * @see HotSpotReferenceMap#frameRefMap
         */
        static MapEntry getFromBits(int idx, HotSpotOopMap set) {
            int n = set.get(idx);
            switch (n) {
                case 0:
                    return NoReference;
                case OOP64:
                    return WideOop;
                case NARROW_LOW:
                    return NarrowOopLowerHalf;
                case NARROW_HIGH:
                    return NarrowOopUpperHalf;
                case NARROW_BOTH:
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

        static int toBit(MapEntry type) {
            return type.pattern;
        }
    }

    /**
     * A specialized bit set that represents both wide and narrow oops in an efficient manner. The
     * map consists of 4 bit entries that represent 8 bytes of memory.
     *
     */
    class HotSpotOopMap implements Cloneable {

        /**
         * Each entry is 4 bits long and covers 8 bytes of memory.
         */
        private static final int BITS_PER_ENTRY = 4;
        private static final int BITS_PER_ELEMENT = 64;

        public HotSpotOopMap(int i) {
            words = new long[(i * BITS_PER_ENTRY + BITS_PER_ELEMENT) / BITS_PER_ELEMENT];
        }

        public HotSpotOopMap(HotSpotOopMap other) {
            words = other.words.clone();
        }

        private long[] words;

        private int get(int i) {
            return getEntry(i);
        }

        public void or(HotSpotOopMap src) {
            if (words.length < src.words.length) {
                long[] newWords = new long[src.words.length];
                System.arraycopy(src.words, 0, newWords, 0, src.words.length);
                for (int i = 0; i < words.length; i++) {
                    newWords[i] |= words[i];
                }
                words = newWords;
            } else {
                for (int i = 0; i < src.words.length; i++) {
                    words[i] |= src.words[i];
                }
            }
        }

        private void setOop(int regIdx) {
            setEntry(regIdx, OOP64);
        }

        public int size() {
            return words.length * BITS_PER_ELEMENT / BITS_PER_ENTRY;
        }

        @Override
        public HotSpotOopMap clone() {
            return new HotSpotOopMap(this);
        }

        private void setNarrowOop(int offset) {
            setNarrowEntry(offset, OOP32);
        }

        private void setEntry(int regIdx, int value) {
            assert regIdx % 2 == 0 : "must be alinged";
            int bitIndex = (regIdx >> 1) * BITS_PER_ENTRY;
            int wordIndex = bitIndex / BITS_PER_ELEMENT;
            int shift = bitIndex - wordIndex * BITS_PER_ELEMENT;
            if (wordIndex >= words.length) {
                if (value == 0) {
                    // Nothing to do since bits are clear
                    return;
                }
                words = Arrays.copyOf(words, wordIndex + 1);
            }
            assert verifyUpdate(this, this);
            long orig = words[wordIndex];
            words[wordIndex] = (orig & (~(0b1111L << shift))) | ((long) value << shift);
            assert get(regIdx / 2) == value;
            assert verifyUpdate(this, this);
        }

        private void setNarrowEntry(int offset, int value) {
            int regIdx = offset >> 1;
            boolean low = offset % 2 == 0;
            int bitIndex = regIdx * BITS_PER_ENTRY;
            int wordIndex = bitIndex / BITS_PER_ELEMENT;
            int shift = bitIndex - wordIndex * BITS_PER_ELEMENT;
            if (wordIndex >= words.length) {
                if (value == 0) {
                    // Nothing to do since bits are clear
                    return;
                }
                words = Arrays.copyOf(words, wordIndex + 1);
            }
            long originalValue = words[wordIndex];
            int current = ((int) (originalValue >> shift)) & 0b1111;
            if (current == OOP64) {
                current = 0;
            }
            long newValue;
            if (value != 0) {
                newValue = current | (low ? value : (value << 2));
            } else {
                newValue = current & (low ? 0b1100 : 0b0011);
            }
            long masked = originalValue & (~(0b1111L << shift));
            words[wordIndex] = masked | (newValue << shift);
            assert verifyUpdate(this, this);
        }

        private int getEntry(int regIdx) {
            int bitIndex = regIdx * BITS_PER_ENTRY;
            int wordIndex = bitIndex / BITS_PER_ELEMENT;
            int shift = bitIndex - wordIndex * BITS_PER_ELEMENT;
            return ((int) (words[wordIndex] >>> shift)) & 0b1111;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (other instanceof HotSpotOopMap) {
                HotSpotOopMap otherMap = (HotSpotOopMap) other;
                int limit = Math.min(words.length, otherMap.words.length);
                for (int i = 0; i < limit; i++) {
                    if (words[i] != otherMap.words[i]) {
                        return false;
                    }
                }
                for (int i = limit; i < words.length; i++) {
                    if (words[i] != 0) {
                        return false;
                    }
                }
                for (int i = limit; i < otherMap.words.length; i++) {
                    if (otherMap.words[i] != 0) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            long h = 1234;
            for (int i = words.length; --i >= 0;) {
                h ^= words[i] * (i + 1);
            }
            return (int) ((h >> 32) ^ h);
        }

        @Override
        public String toString() {
            int count = 0;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int idx = 0; idx < size(); idx++) {
                MapEntry dstType = MapEntry.getFromBits(idx, this);
                if (dstType == MapEntry.NoReference) {
                    continue;
                }
                if (count > 0) {
                    sb.append(", ");
                }
                if (dstType == MapEntry.Illegal) {
                    int value = get(idx);
                    sb.append("0x");
                    sb.append(Integer.toHexString(value));
                } else {
                    sb.append(idx);
                    sb.append(':');
                    sb.append(dstType);
                }
                count++;
            }
            sb.append("]");
            return sb.toString();
        }
    }

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
    private final HotSpotOopMap registerRefMap;

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
    private final HotSpotOopMap frameRefMap;

    private final TargetDescription target;

    public HotSpotReferenceMap(int registerCount, int frameSlotCount, TargetDescription target) {
        if (registerCount > 0) {
            this.registerRefMap = new HotSpotOopMap(registerCount);
        } else {
            this.registerRefMap = null;
        }
        this.frameRefMap = new HotSpotOopMap(frameSlotCount);
        this.target = target;
    }

    private HotSpotReferenceMap(HotSpotReferenceMap other) {
        this.registerRefMap = other.registerRefMap.clone();
        this.frameRefMap = other.frameRefMap.clone();
        this.target = other.target;
    }

    @Override
    public ReferenceMap clone() {
        return new HotSpotReferenceMap(this);
    }

    // setters
    @Override
    public void setRegister(int idx, LIRKind kind) {
        set(registerRefMap, idx * 2, kind);
    }

    @Override
    public void setStackSlot(int offset, LIRKind kind) {
        assert offset % bytesPerElement(kind) == 0 : "unaligned value in ReferenceMap";
        set(frameRefMap, offset / 4, kind);
    }

    private void set(HotSpotOopMap refMap, int index, LIRKind kind) {
        if (kind.isDerivedReference()) {
            throw new InternalError("derived reference cannot be inserted in ReferenceMap");
        }

        int bytesPerElement = bytesPerElement(kind);
        int length = kind.getPlatformKind().getVectorLength();
        if (bytesPerElement == 8) {
            for (int i = 0; i < length; i++) {
                if (kind.isReference(i)) {
                    refMap.setOop(index + i * 2);
                }
            }
        } else if (bytesPerElement == 4) {
            for (int i = 0; i < length; i++) {
                if (kind.isReference(i)) {
                    refMap.setNarrowOop(index + i);
                }
            }
        } else {
            assert kind.isValue() : "unknown reference kind " + kind;
        }
    }

    private int bytesPerElement(LIRKind kind) {
        PlatformKind platformKind = kind.getPlatformKind();
        return target.getSizeInBytes(platformKind) / platformKind.getVectorLength();
    }

    public HotSpotOopMap getFrameMap() {
        return frameRefMap == null ? null : (HotSpotOopMap) frameRefMap.clone();
    }

    public HotSpotOopMap getRegisterMap() {
        return registerRefMap == null ? null : (HotSpotOopMap) registerRefMap.clone();
    }

    static MapEntry[] entries(HotSpotOopMap fixedMap) {
        MapEntry[] result = new MapEntry[fixedMap.size()];
        for (int idx = 0; idx < fixedMap.size(); idx++) {
            MapEntry dstType = MapEntry.getFromBits(idx, fixedMap);
            result[idx] = dstType;
        }
        return result;
    }

    private static boolean verifyUpdate(HotSpotOopMap dst, HotSpotOopMap src) {
        return verifyUpdate(dst, src, true);
    }

    private static boolean verifyUpdate(HotSpotOopMap dst, HotSpotOopMap src, boolean doAssert) {
        for (int idx = 0; idx < Math.min(src.size(), dst.size()); idx++) {
            if (!verifyUpdateEntry(idx, dst, src, doAssert)) {
                return false;
            }
        }
        return true;
    }

    private static boolean verifyUpdateEntry(int idx, HotSpotOopMap dst, HotSpotOopMap src, boolean doAssert) {
        MapEntry dstType = MapEntry.getFromBits(idx, dst);
        MapEntry srcType = MapEntry.getFromBits(idx, src);

        if (dstType == MapEntry.Illegal || srcType == MapEntry.Illegal) {
            assert !doAssert : String.format("Illegal RefMap bit pattern: %s (0b%s), %s (0b%s)", dstType, dstType.toBitString(), srcType, srcType.toBitString());
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
            case TwoNarrowOops:
            case NarrowOopLowerHalf:
            case NarrowOopUpperHalf:
                switch (srcType) {
                    case TwoNarrowOops:
                    case NarrowOopLowerHalf:
                    case NarrowOopUpperHalf:
                    case NoReference:
                        return true;
                    default:
                        assert false : String.format("Illegal RefMap combination: %s (0b%s), %s (0b%s)", dstType, dstType.toBitString(), srcType, srcType.toBitString());
                        return false;
                }
            default:
                return false;
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

    @Override
    public boolean hasRegisterRefMap() {
        return registerRefMap != null && registerRefMap.size() > 0;
    }

    @Override
    public boolean hasFrameRefMap() {
        return frameRefMap != null && frameRefMap.size() > 0;
    }

    @Override
    public void appendRegisterMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int idx = 0; idx < registerRefMap.size(); idx++) {
            MapEntry dstType = MapEntry.getFromBits(idx, registerRefMap);
            if (dstType != MapEntry.NoReference) {
                sb.append(' ').append(formatter.formatRegister(idx)).append(':').append(dstType);
            }
        }
    }

    @Override
    public void appendFrameMap(StringBuilder sb, RefMapFormatter formatter) {
        for (int idx = 0; idx < frameRefMap.size(); idx++) {
            MapEntry dstType = MapEntry.getFromBits(idx, frameRefMap);
            if (dstType != MapEntry.NoReference) {
                sb.append(' ').append(formatter.formatStackSlot(idx)).append(':').append(dstType);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (registerRefMap != null) {
            sb.append("Registers = ");
            sb.append(registerRefMap);
        }
        sb.append("Stack = ");
        sb.append(frameRefMap);
        return sb.toString();
    }

    public void verify() {
        assert verifyUpdate(frameRefMap, frameRefMap);
        assert registerRefMap == null || verifyUpdate(registerRefMap, registerRefMap);
    }
}
