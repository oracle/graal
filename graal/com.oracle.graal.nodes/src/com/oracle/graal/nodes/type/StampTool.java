/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.type;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Helper class that is used to keep all stamp-related operations in one place.
 */
public class StampTool {

    public static Kind joinKind(Kind a, Kind b) {
        if (a == b) {
            return a;
        }
        return Kind.Illegal;
    }

    public static Kind joinKind(Stamp a, Stamp b) {
        return joinKind(a.kind(), b.kind());
    }

    public static Stamp negate(Stamp stamp) {
        Kind kind = stamp.kind();
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            if (integerStamp.lowerBound() != kind.getMinValue()) {
                // TODO(ls) check if the mask calculation is correct...
                return StampFactory.forInteger(kind, -integerStamp.upperBound(), -integerStamp.lowerBound());
            }
        } else if (stamp instanceof FloatStamp) {
            FloatStamp floatStamp = (FloatStamp) stamp;
            return new FloatStamp(kind, -floatStamp.upperBound(), -floatStamp.lowerBound(), floatStamp.isNonNaN());
        }

        return StampFactory.forKind(kind);
    }

    public static Stamp not(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            assert stamp.kind() == Kind.Int || stamp.kind() == Kind.Long;
            long defaultMask = IntegerStamp.defaultMask(stamp.kind());
            return new IntegerStamp(stamp.kind(), ~integerStamp.upperBound(), ~integerStamp.lowerBound(), (~integerStamp.upMask()) & defaultMask, (~integerStamp.downMask()) & defaultMask);
        }
        return StampFactory.forKind(stamp.kind());
    }

    public static Stamp meet(Collection<? extends StampProvider> values) {
        Iterator<? extends StampProvider> iterator = values.iterator();
        if (iterator.hasNext()) {
            Stamp stamp = iterator.next().stamp();
            while (iterator.hasNext()) {
                stamp = stamp.meet(iterator.next().stamp());
            }
            return stamp;
        } else {
            return StampFactory.forVoid();
        }
    }

    public static Stamp add(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return add((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal(joinKind(stamp1, stamp2));
    }

    private static long carryBits(long x, long y) {
        return (x + y) ^ x ^ y;
    }

    public static Stamp sub(Stamp stamp1, Stamp stamp2) {
        return add(stamp1, StampTool.negate(stamp2));
    }

    public static Stamp div(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return div((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal(joinKind(stamp1, stamp2));
    }

    public static Stamp div(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.kind() == stamp2.kind();
        Kind kind = stamp1.kind();
        if (stamp2.isStrictlyPositive()) {
            long lowerBound = stamp1.lowerBound() / stamp2.lowerBound();
            long upperBound = stamp1.upperBound() / stamp2.lowerBound();
            return StampFactory.forInteger(kind, lowerBound, upperBound);
        }
        return StampFactory.forKind(kind);
    }

    private static boolean addOverflowsPositively(long x, long y, Kind kind) {
        long result = x + y;
        if (kind == Kind.Long) {
            return (~x & ~y & result) < 0;
        } else {
            assert kind == Kind.Int;
            return result > Integer.MAX_VALUE;
        }
    }

    private static boolean addOverflowsNegatively(long x, long y, Kind kind) {
        long result = x + y;
        if (kind == Kind.Long) {
            return (x & y & ~result) < 0;
        } else {
            assert kind == Kind.Int;
            return result < Integer.MIN_VALUE;
        }
    }

    public static IntegerStamp add(IntegerStamp stamp1, IntegerStamp stamp2) {
        if (stamp1.isUnrestricted() || stamp2.isUnrestricted()) {
            return (IntegerStamp) StampFactory.forKind(stamp1.kind());
        }
        Kind kind = stamp1.kind();
        assert stamp1.kind() == stamp2.kind();
        long defaultMask = IntegerStamp.defaultMask(kind);
        long variableBits = (stamp1.downMask() ^ stamp1.upMask()) | (stamp2.downMask() ^ stamp2.upMask());
        long variableBitsWithCarry = variableBits | (carryBits(stamp1.downMask(), stamp2.downMask()) ^ carryBits(stamp1.upMask(), stamp2.upMask()));
        long newDownMask = (stamp1.downMask() + stamp2.downMask()) & ~variableBitsWithCarry;
        long newUpMask = (stamp1.downMask() + stamp2.downMask()) | variableBitsWithCarry;

        newDownMask &= defaultMask;
        newUpMask &= defaultMask;

        long lowerBound;
        long upperBound;
        boolean lowerOverflowsPositively = addOverflowsPositively(stamp1.lowerBound(), stamp2.lowerBound(), kind);
        boolean upperOverflowsPositively = addOverflowsPositively(stamp1.upperBound(), stamp2.upperBound(), kind);
        boolean lowerOverflowsNegatively = addOverflowsNegatively(stamp1.lowerBound(), stamp2.lowerBound(), kind);
        boolean upperOverflowsNegatively = addOverflowsNegatively(stamp1.upperBound(), stamp2.upperBound(), kind);
        if ((lowerOverflowsNegatively && !upperOverflowsNegatively) || (!lowerOverflowsPositively && upperOverflowsPositively)) {
            lowerBound = kind.getMinValue();
            upperBound = kind.getMaxValue();
        } else {
            lowerBound = signExtend((stamp1.lowerBound() + stamp2.lowerBound()) & defaultMask, kind);
            upperBound = signExtend((stamp1.upperBound() + stamp2.upperBound()) & defaultMask, kind);
        }
        IntegerStamp limit = StampFactory.forInteger(kind, lowerBound, upperBound);
        newUpMask &= limit.upMask();
        upperBound = signExtend(upperBound & newUpMask, kind);
        newDownMask |= limit.downMask();
        lowerBound |= newDownMask;
        return new IntegerStamp(kind, lowerBound, upperBound, newDownMask, newUpMask);
    }

    public static Stamp sub(IntegerStamp stamp1, IntegerStamp stamp2) {
        if (stamp1.isUnrestricted() || stamp2.isUnrestricted()) {
            return StampFactory.forKind(stamp1.kind());
        }
        return add(stamp1, (IntegerStamp) StampTool.negate(stamp2));
    }

    private static Stamp stampForMask(Kind kind, long downMask, long upMask) {
        long lowerBound;
        long upperBound;
        if (((upMask >>> (kind.getBitCount() - 1)) & 1) == 0) {
            lowerBound = downMask;
            upperBound = upMask;
        } else if (((downMask >>> (kind.getBitCount() - 1)) & 1) == 1) {
            lowerBound = downMask;
            upperBound = upMask;
        } else {
            lowerBound = downMask | (-1L << (kind.getBitCount() - 1));
            upperBound = kind.getMaxValue() & upMask;
        }
        if (kind == Kind.Int) {
            return StampFactory.forInteger(kind, (int) lowerBound, (int) upperBound, downMask, upMask);
        } else {
            return StampFactory.forInteger(kind, lowerBound, upperBound, downMask, upMask);
        }
    }

    public static Stamp and(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return and((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal(joinKind(stamp1, stamp2));
    }

    public static Stamp and(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.kind() == stamp2.kind();
        return stampForMask(stamp1.kind(), stamp1.downMask() & stamp2.downMask(), stamp1.upMask() & stamp2.upMask());
    }

    public static Stamp or(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return or((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal(joinKind(stamp1, stamp2));
    }

    public static Stamp or(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.kind() == stamp2.kind();
        return stampForMask(stamp1.kind(), stamp1.downMask() | stamp2.downMask(), stamp1.upMask() | stamp2.upMask());
    }

    public static Stamp xor(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return xor((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal(joinKind(stamp1, stamp2));
    }

    public static Stamp xor(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.kind() == stamp2.kind();
        long variableBits = (stamp1.downMask() ^ stamp1.upMask()) | (stamp2.downMask() ^ stamp2.upMask());
        long newDownMask = (stamp1.downMask() ^ stamp2.downMask()) & ~variableBits;
        long newUpMask = (stamp1.downMask() ^ stamp2.downMask()) | variableBits;
        return stampForMask(stamp1.kind(), newDownMask, newUpMask);
    }

    public static Stamp unsignedRightShift(Stamp value, Stamp shift) {
        if (value instanceof IntegerStamp && shift instanceof IntegerStamp) {
            return unsignedRightShift((IntegerStamp) value, (IntegerStamp) shift);
        }
        return StampFactory.illegal(value.kind());
    }

    public static Stamp unsignedRightShift(IntegerStamp value, IntegerStamp shift) {
        Kind kind = value.kind();
        if (shift.lowerBound() == shift.upperBound()) {
            long shiftMask = kind == Kind.Int ? 0x1FL : 0x3FL;
            long shiftCount = shift.lowerBound() & shiftMask;
            if (shiftCount != 0) {
                long lowerBound;
                long upperBound;
                long downMask = value.downMask() >>> shiftCount;
                long upMask = value.upMask() >>> shiftCount;
                if (value.lowerBound() < 0) {
                    lowerBound = downMask;
                    upperBound = upMask;
                } else {
                    lowerBound = value.lowerBound() >>> shiftCount;
                    upperBound = value.upperBound() >>> shiftCount;
                }
                return new IntegerStamp(kind, lowerBound, upperBound, downMask, upMask);
            }
        }
        long mask = IntegerStamp.upMaskFor(kind, value.lowerBound(), value.upperBound());
        return stampForMask(kind, 0, mask);
    }

    public static Stamp leftShift(Stamp value, Stamp shift) {
        if (value instanceof IntegerStamp && shift instanceof IntegerStamp) {
            return leftShift((IntegerStamp) value, (IntegerStamp) shift);
        }
        return StampFactory.illegal(value.kind());
    }

    public static Stamp leftShift(IntegerStamp value, IntegerStamp shift) {
        Kind kind = value.kind();
        long defaultMask = IntegerStamp.defaultMask(kind);
        if (value.upMask() == 0) {
            return value;
        }
        int shiftBits = kind == Kind.Int ? 5 : 6;
        long shiftMask = kind == Kind.Int ? 0x1FL : 0x3FL;
        if ((shift.lowerBound() >>> shiftBits) == (shift.upperBound() >>> shiftBits)) {
            long downMask = defaultMask;
            long upMask = 0;
            for (long i = shift.lowerBound(); i <= shift.upperBound(); i++) {
                if (shift.contains(i)) {
                    downMask &= value.downMask() << (i & shiftMask);
                    upMask |= value.upMask() << (i & shiftMask);
                }
            }
            Stamp result = stampForMask(kind, downMask, upMask & IntegerStamp.defaultMask(kind));
            return result;
        }
        return StampFactory.forKind(kind);
    }

    public static Stamp intToLong(IntegerStamp intStamp) {
        return StampFactory.forInteger(Kind.Long, intStamp.lowerBound(), intStamp.upperBound(), signExtend(intStamp.downMask(), Kind.Int), signExtend(intStamp.upMask(), Kind.Int));
    }

    public static IntegerStamp narrowingKindConversion(IntegerStamp fromStamp, Kind toKind) {
        assert toKind == Kind.Byte || toKind == Kind.Char || toKind == Kind.Short || toKind == Kind.Int;
        final long upperBound;
        if (fromStamp.lowerBound() < toKind.getMinValue()) {
            upperBound = toKind.getMaxValue();
        } else {
            upperBound = saturate(fromStamp.upperBound(), toKind);
        }
        final long lowerBound;
        if (fromStamp.upperBound() > toKind.getMaxValue()) {
            lowerBound = toKind.getMinValue();
        } else {
            lowerBound = saturate(fromStamp.lowerBound(), toKind);
        }

        long defaultMask = IntegerStamp.defaultMask(toKind);
        long intMask = IntegerStamp.defaultMask(Kind.Int);
        long newUpMask = signExtend(fromStamp.upMask() & defaultMask, toKind) & intMask;
        long newDownMask = signExtend(fromStamp.downMask() & defaultMask, toKind) & intMask;
        return new IntegerStamp(toKind.getStackKind(), (int) ((lowerBound | newDownMask) & newUpMask), (int) ((upperBound | newDownMask) & newUpMask), newDownMask, newUpMask);
    }

    private static long signExtend(long value, Kind valueKind) {
        if (valueKind != Kind.Char && valueKind != Kind.Long && (value >>> (valueKind.getBitCount() - 1) & 1) == 1) {
            return value | (-1L << valueKind.getBitCount());
        } else {
            return value;
        }
    }

    public static long saturate(long v, Kind kind) {
        long max = kind.getMaxValue();
        if (v > max) {
            return max;
        }
        long min = kind.getMinValue();
        if (v < min) {
            return min;
        }
        return v;
    }
}
