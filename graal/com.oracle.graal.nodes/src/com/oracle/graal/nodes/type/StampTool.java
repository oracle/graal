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
// TODO(ls) maybe move the contents into IntegerStamp
public class StampTool {

    public static Stamp negate(Stamp stamp) {
        Kind kind = stamp.kind();
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            if (integerStamp.lowerBound() != kind.getMinValue()) {
                // TODO(ls) check if the mask calculation is correct...
                return new IntegerStamp(kind, -integerStamp.upperBound(), -integerStamp.lowerBound(), IntegerStamp.defaultMask(kind) & (integerStamp.mask() | -integerStamp.mask()));
            }
        }
        return StampFactory.forKind(kind);
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

    public static Stamp add(IntegerStamp stamp1, IntegerStamp stamp2) {
        Kind kind = stamp1.kind();
        assert kind == stamp2.kind();
        if (addOverflow(stamp1.lowerBound(), stamp2.lowerBound(), kind)) {
            return StampFactory.forKind(kind);
        }
        if (addOverflow(stamp1.upperBound(), stamp2.upperBound(), kind)) {
            return StampFactory.forKind(kind);
        }
        long lowerBound = stamp1.lowerBound() + stamp2.lowerBound();
        long upperBound = stamp1.upperBound() + stamp2.upperBound();
        long mask = IntegerStamp.maskFor(kind, lowerBound, upperBound) & (stamp1.mask() | stamp2.mask());

        return StampFactory.forInteger(kind, lowerBound, upperBound, mask);
    }

    public static Stamp sub(IntegerStamp stamp1, IntegerStamp stamp2) {
        return add(stamp1, (IntegerStamp) StampTool.negate(stamp2));
    }

    public static Stamp div(IntegerStamp stamp1, IntegerStamp stamp2) {
        Kind kind = stamp1.kind();
        if (stamp2.isStrictlyPositive()) {
            long lowerBound = stamp1.lowerBound() / stamp2.lowerBound();
            long upperBound = stamp1.upperBound() / stamp2.lowerBound();
            return StampFactory.forInteger(kind, lowerBound, upperBound, IntegerStamp.maskFor(kind, lowerBound, upperBound));
        }
        return StampFactory.forKind(kind);
    }

    private static boolean addOverflow(long x, long y, Kind kind) {
        long result = x + y;
        if (kind == Kind.Long) {
            return ((x ^ result) & (y ^ result)) < 0;
        } else {
            assert kind == Kind.Int;
            return result > Integer.MAX_VALUE || result < Integer.MIN_VALUE;
        }
    }

    private static final long INTEGER_SIGN_BIT = 0x80000000L;
    private static final long LONG_SIGN_BIT = 0x8000000000000000L;

    private static Stamp stampForMask(Kind kind, long mask) {
        return stampForMask(kind, mask, 0);
    }

    private static Stamp stampForMask(Kind kind, long mask, long alwaysSetBits) {
        long lowerBound;
        long upperBound;
        if (kind == Kind.Int && (mask & INTEGER_SIGN_BIT) != 0) {
            // the mask is negative
            lowerBound = Integer.MIN_VALUE;
            upperBound = mask ^ INTEGER_SIGN_BIT;
        } else if (kind == Kind.Long && (mask & LONG_SIGN_BIT) != 0) {
            // the mask is negative
            lowerBound = Long.MIN_VALUE;
            upperBound = mask ^ LONG_SIGN_BIT;
        } else {
            lowerBound = alwaysSetBits;
            upperBound = mask;
        }
        return StampFactory.forInteger(kind, lowerBound, upperBound, mask);
    }

    public static Stamp and(IntegerStamp stamp1, IntegerStamp stamp2) {
        Kind kind = stamp1.kind();
        long mask = stamp1.mask() & stamp2.mask();
        return stampForMask(kind, mask);
    }

    public static Stamp or(IntegerStamp stamp1, IntegerStamp stamp2) {
        Kind kind = stamp1.kind();
        long mask = stamp1.mask() | stamp2.mask();
        if (stamp1.lowerBound() >= 0 && stamp2.lowerBound() >= 0) {
            return stampForMask(kind, mask, stamp1.lowerBound() | stamp2.lowerBound());
        } else {
            return stampForMask(kind, mask);
        }
    }

    public static Stamp xor(IntegerStamp stamp1, IntegerStamp stamp2) {
        Kind kind = stamp1.kind();
        long mask = stamp1.mask() | stamp2.mask();
        return stampForMask(kind, mask);
    }

    public static Stamp unsignedRightShift(IntegerStamp value, IntegerStamp shift) {
        Kind kind = value.kind();
        if (shift.lowerBound() == shift.upperBound()) {
            long shiftMask = kind == Kind.Int ? 0x1FL : 0x3FL;
            long shiftCount = shift.lowerBound() & shiftMask;
            if (shiftCount != 0) {
                long lowerBound;
                long upperBound;
                if (value.lowerBound() < 0) {
                    lowerBound = 0;
                    upperBound = IntegerStamp.defaultMask(kind) >>> shiftCount;
                } else {
                    lowerBound = value.lowerBound() >>> shiftCount;
                    upperBound = value.upperBound() >>> shiftCount;
                }
                long mask = value.mask() >>> shiftCount;
                return StampFactory.forInteger(kind, lowerBound, upperBound, mask);
            }
        }
        long mask = IntegerStamp.maskFor(kind, value.lowerBound(), value.upperBound());
        return stampForMask(kind, mask);
    }

    public static Stamp leftShift(IntegerStamp value, IntegerStamp shift) {
        Kind kind = value.kind();
        int shiftBits = kind == Kind.Int ? 5 : 6;
        long shiftMask = kind == Kind.Int ? 0x1FL : 0x3FL;
        if ((shift.lowerBound() >>> shiftBits) == (shift.upperBound() >>> shiftBits)) {
            long mask = 0;
            for (long i = shift.lowerBound() & shiftMask; i <= (shift.upperBound() & shiftMask); i++) {
                mask |= value.mask() << i;
            }
            mask &= IntegerStamp.defaultMask(kind);
            return stampForMask(kind, mask);
        }
        return StampFactory.forKind(kind);
    }

    public static Stamp intToLong(IntegerStamp intStamp) {
        long mask;
        if (intStamp.isPositive()) {
            mask = intStamp.mask();
        } else {
            mask = 0xffffffff00000000L | intStamp.mask();
        }
        return StampFactory.forInteger(Kind.Long, intStamp.lowerBound(), intStamp.upperBound(), mask);
    }

    private static Stamp narrowingKindConvertion(IntegerStamp fromStamp, Kind toKind) {
        long mask = fromStamp.mask() & IntegerStamp.defaultMask(toKind);
        long lowerBound = saturate(fromStamp.lowerBound(), toKind);
        long upperBound = saturate(fromStamp.upperBound(), toKind);
        if (fromStamp.lowerBound() < toKind.getMinValue()) {
            upperBound = toKind.getMaxValue();
        }
        if (fromStamp.upperBound() > toKind.getMaxValue()) {
            lowerBound = toKind.getMinValue();
        }
        return StampFactory.forInteger(toKind.getStackKind(), lowerBound, upperBound, mask);
    }

    public static Stamp intToByte(IntegerStamp intStamp) {
        return narrowingKindConvertion(intStamp, Kind.Byte);
    }

    public static Stamp intToShort(IntegerStamp intStamp) {
        return narrowingKindConvertion(intStamp, Kind.Short);
    }

    public static Stamp intToChar(IntegerStamp intStamp) {
        return narrowingKindConvertion(intStamp, Kind.Char);
    }

    public static Stamp longToInt(IntegerStamp longStamp) {
        return narrowingKindConvertion(longStamp, Kind.Int);
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
