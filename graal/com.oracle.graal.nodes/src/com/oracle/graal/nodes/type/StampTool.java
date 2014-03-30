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
import com.oracle.graal.nodes.calc.*;

/**
 * Helper class that is used to keep all stamp-related operations in one place.
 */
public class StampTool {

    private static Kind joinKind(Kind a, Kind b) {
        if (a == b) {
            return a;
        }
        return Kind.Illegal;
    }

    /**
     * Create an {@link IllegalStamp} from two incompatible input stamps, joining the kind of the
     * input stamps if possible.
     */
    private static Stamp joinIllegal(Stamp a, Stamp b) {
        IllegalStamp ia = (IllegalStamp) a.illegal();
        IllegalStamp ib = (IllegalStamp) b.illegal();
        return StampFactory.illegal(joinKind(ia.kind(), ib.kind()));
    }

    public static Stamp negate(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            int bits = integerStamp.getBits();
            if (integerStamp.lowerBound() != IntegerStamp.defaultMinValue(bits, false)) {
                // TODO(ls) check if the mask calculation is correct...
                return StampFactory.forInteger(bits, false, -integerStamp.upperBound(), -integerStamp.lowerBound());
            }
        } else if (stamp instanceof FloatStamp) {
            FloatStamp floatStamp = (FloatStamp) stamp;
            return new FloatStamp(floatStamp.getBits(), -floatStamp.upperBound(), -floatStamp.lowerBound(), floatStamp.isNonNaN());
        }

        return stamp.unrestricted();
    }

    public static Stamp not(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            int bits = integerStamp.getBits();
            long defaultMask = IntegerStamp.defaultMask(bits);
            return new IntegerStamp(bits, integerStamp.isUnsigned(), ~integerStamp.upperBound(), ~integerStamp.lowerBound(), (~integerStamp.upMask()) & defaultMask, (~integerStamp.downMask()) &
                            defaultMask);
        }
        return stamp.unrestricted();
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
        return joinIllegal(stamp1, stamp2);
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
        return joinIllegal(stamp1, stamp2);
    }

    public static Stamp div(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits() && stamp1.isUnsigned() == stamp2.isUnsigned();
        if (stamp2.isStrictlyPositive()) {
            long lowerBound = stamp1.lowerBound() / stamp2.lowerBound();
            long upperBound = stamp1.upperBound() / stamp2.lowerBound();
            return StampFactory.forInteger(stamp1.getBits(), stamp1.isUnsigned(), lowerBound, upperBound);
        }
        return stamp1.unrestricted();
    }

    private static boolean addOverflowsPositively(long x, long y, int bits, boolean unsigned) {
        long result = x + y;
        if (bits == 64) {
            if (unsigned) {
                return ((x | y) & ~result) < 0;
            } else {
                return (~x & ~y & result) < 0;
            }
        } else {
            return result > IntegerStamp.defaultMaxValue(bits, unsigned);
        }
    }

    private static boolean addOverflowsNegatively(long x, long y, int bits, boolean unsigned) {
        if (unsigned) {
            return false;
        }

        long result = x + y;
        if (bits == 64) {
            return (x & y & ~result) < 0;
        } else {
            return result < IntegerStamp.defaultMinValue(bits, unsigned);
        }
    }

    public static IntegerStamp add(IntegerStamp stamp1, IntegerStamp stamp2) {
        int bits = stamp1.getBits();
        boolean unsigned = stamp1.isUnsigned();
        assert bits == stamp2.getBits() && unsigned == stamp2.isUnsigned();

        if (stamp1.isUnrestricted()) {
            return stamp1;
        } else if (stamp2.isUnrestricted()) {
            return stamp2;
        }
        long defaultMask = IntegerStamp.defaultMask(bits);
        long variableBits = (stamp1.downMask() ^ stamp1.upMask()) | (stamp2.downMask() ^ stamp2.upMask());
        long variableBitsWithCarry = variableBits | (carryBits(stamp1.downMask(), stamp2.downMask()) ^ carryBits(stamp1.upMask(), stamp2.upMask()));
        long newDownMask = (stamp1.downMask() + stamp2.downMask()) & ~variableBitsWithCarry;
        long newUpMask = (stamp1.downMask() + stamp2.downMask()) | variableBitsWithCarry;

        newDownMask &= defaultMask;
        newUpMask &= defaultMask;

        long lowerBound;
        long upperBound;
        boolean lowerOverflowsPositively = addOverflowsPositively(stamp1.lowerBound(), stamp2.lowerBound(), bits, unsigned);
        boolean upperOverflowsPositively = addOverflowsPositively(stamp1.upperBound(), stamp2.upperBound(), bits, unsigned);
        boolean lowerOverflowsNegatively = addOverflowsNegatively(stamp1.lowerBound(), stamp2.lowerBound(), bits, unsigned);
        boolean upperOverflowsNegatively = addOverflowsNegatively(stamp1.upperBound(), stamp2.upperBound(), bits, unsigned);
        if ((lowerOverflowsNegatively && !upperOverflowsNegatively) || (!lowerOverflowsPositively && upperOverflowsPositively)) {
            lowerBound = IntegerStamp.defaultMinValue(bits, unsigned);
            upperBound = IntegerStamp.defaultMaxValue(bits, unsigned);
        } else {
            lowerBound = (stamp1.lowerBound() + stamp2.lowerBound()) & defaultMask;
            upperBound = (stamp1.upperBound() + stamp2.upperBound()) & defaultMask;
            if (!unsigned) {
                lowerBound = SignExtendNode.signExtend(lowerBound, bits);
                upperBound = SignExtendNode.signExtend(upperBound, bits);
            }
        }
        IntegerStamp limit = StampFactory.forInteger(bits, unsigned, lowerBound, upperBound);
        newUpMask &= limit.upMask();
        upperBound &= newUpMask;
        if (!unsigned) {
            upperBound = SignExtendNode.signExtend(upperBound, bits);
        }
        newDownMask |= limit.downMask();
        lowerBound |= newDownMask;
        return new IntegerStamp(bits, unsigned, lowerBound, upperBound, newDownMask, newUpMask);
    }

    public static Stamp sub(IntegerStamp stamp1, IntegerStamp stamp2) {
        if (stamp1.isUnrestricted() || stamp2.isUnrestricted()) {
            return stamp1.unrestricted();
        }
        return add(stamp1, (IntegerStamp) StampTool.negate(stamp2));
    }

    private static Stamp stampForMask(int bits, long downMask, long upMask) {
        long lowerBound;
        long upperBound;
        if (((upMask >>> (bits - 1)) & 1) == 0) {
            lowerBound = downMask;
            upperBound = upMask;
        } else if (((downMask >>> (bits - 1)) & 1) == 1) {
            lowerBound = downMask;
            upperBound = upMask;
        } else {
            lowerBound = downMask | (-1L << (bits - 1));
            upperBound = IntegerStamp.defaultMaxValue(bits, false) & upMask;
        }
        lowerBound = IntegerConvertNode.convert(lowerBound, bits, false);
        upperBound = IntegerConvertNode.convert(upperBound, bits, false);
        return new IntegerStamp(bits, false, lowerBound, upperBound, downMask, upMask);
    }

    public static Stamp and(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return and((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return joinIllegal(stamp1, stamp2);
    }

    public static Stamp and(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        return stampForMask(stamp1.getBits(), stamp1.downMask() & stamp2.downMask(), stamp1.upMask() & stamp2.upMask());
    }

    public static Stamp or(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return or((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return joinIllegal(stamp1, stamp2);
    }

    public static Stamp or(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        return stampForMask(stamp1.getBits(), stamp1.downMask() | stamp2.downMask(), stamp1.upMask() | stamp2.upMask());
    }

    public static Stamp xor(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return xor((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return joinIllegal(stamp1, stamp2);
    }

    public static Stamp xor(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        long variableBits = (stamp1.downMask() ^ stamp1.upMask()) | (stamp2.downMask() ^ stamp2.upMask());
        long newDownMask = (stamp1.downMask() ^ stamp2.downMask()) & ~variableBits;
        long newUpMask = (stamp1.downMask() ^ stamp2.downMask()) | variableBits;
        return stampForMask(stamp1.getBits(), newDownMask, newUpMask);
    }

    public static Stamp unsignedRightShift(Stamp value, Stamp shift) {
        if (value instanceof IntegerStamp && shift instanceof IntegerStamp) {
            return unsignedRightShift((IntegerStamp) value, (IntegerStamp) shift);
        }
        return value.illegal();
    }

    public static Stamp unsignedRightShift(IntegerStamp value, IntegerStamp shift) {
        int bits = value.getBits();
        if (shift.lowerBound() == shift.upperBound()) {
            long shiftMask = bits > 32 ? 0x3FL : 0x1FL;
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
                return new IntegerStamp(bits, value.isUnsigned(), lowerBound, upperBound, downMask, upMask);
            }
        }
        long mask = IntegerStamp.upMaskFor(bits, value.lowerBound(), value.upperBound());
        return stampForMask(bits, 0, mask);
    }

    public static Stamp leftShift(Stamp value, Stamp shift) {
        if (value instanceof IntegerStamp && shift instanceof IntegerStamp) {
            return leftShift((IntegerStamp) value, (IntegerStamp) shift);
        }
        return value.illegal();
    }

    public static Stamp leftShift(IntegerStamp value, IntegerStamp shift) {
        int bits = value.getBits();
        long defaultMask = IntegerStamp.defaultMask(bits);
        if (value.upMask() == 0) {
            return value;
        }
        int shiftBits = bits > 32 ? 6 : 5;
        long shiftMask = bits > 32 ? 0x3FL : 0x1FL;
        if ((shift.lowerBound() >>> shiftBits) == (shift.upperBound() >>> shiftBits)) {
            long downMask = defaultMask;
            long upMask = 0;
            for (long i = shift.lowerBound(); i <= shift.upperBound(); i++) {
                if (shift.contains(i)) {
                    downMask &= value.downMask() << (i & shiftMask);
                    upMask |= value.upMask() << (i & shiftMask);
                }
            }
            Stamp result = stampForMask(bits, downMask, upMask & defaultMask);
            return result;
        }
        return value.unrestricted();
    }

    public static Stamp signExtend(Stamp input, int resultBits) {
        if (input instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) input;
            int inputBits = inputStamp.getBits();
            assert inputBits <= resultBits;

            long defaultMask = IntegerStamp.defaultMask(resultBits);
            long downMask = SignExtendNode.signExtend(inputStamp.downMask(), inputBits) & defaultMask;
            long upMask = SignExtendNode.signExtend(inputStamp.upMask(), inputBits) & defaultMask;

            long lowerBound;
            long upperBound;
            if (inputStamp.isUnsigned()) {
                lowerBound = SignExtendNode.signExtend(inputStamp.lowerBound(), inputBits) & defaultMask;
                upperBound = SignExtendNode.signExtend(inputStamp.upperBound(), inputBits) & defaultMask;
            } else {
                lowerBound = inputStamp.lowerBound();
                upperBound = inputStamp.upperBound();
            }

            return new IntegerStamp(resultBits, inputStamp.isUnsigned(), lowerBound, upperBound, downMask, upMask);
        } else {
            return input.illegal();
        }
    }

    public static Stamp zeroExtend(Stamp input, int resultBits) {
        if (input instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) input;
            int inputBits = inputStamp.getBits();
            assert inputBits <= resultBits;

            long downMask = ZeroExtendNode.zeroExtend(inputStamp.downMask(), inputBits);
            long upMask = ZeroExtendNode.zeroExtend(inputStamp.upMask(), inputBits);

            if (inputStamp.lowerBound() < 0 && inputStamp.upperBound() >= 0) {
                // signed range including 0 and -1
                // after sign extension, the whole range from 0 to MAX_INT is possible
                return stampForMask(resultBits, downMask, upMask);
            }

            long lowerBound = ZeroExtendNode.zeroExtend(inputStamp.lowerBound(), inputBits);
            long upperBound = ZeroExtendNode.zeroExtend(inputStamp.upperBound(), inputBits);

            return new IntegerStamp(resultBits, inputStamp.isUnsigned(), lowerBound, upperBound, downMask, upMask);
        } else {
            return input.illegal();
        }
    }

    public static Stamp narrowingConversion(Stamp input, int resultBits) {
        if (input instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) input;
            boolean unsigned = inputStamp.isUnsigned();
            int inputBits = inputStamp.getBits();
            assert resultBits <= inputBits;
            if (resultBits == inputBits) {
                return inputStamp;
            }

            final long upperBound;
            if (inputStamp.lowerBound() < IntegerStamp.defaultMinValue(resultBits, unsigned)) {
                upperBound = IntegerStamp.defaultMaxValue(resultBits, unsigned);
            } else {
                upperBound = saturate(inputStamp.upperBound(), resultBits, unsigned);
            }
            final long lowerBound;
            if (inputStamp.upperBound() > IntegerStamp.defaultMaxValue(resultBits, unsigned)) {
                lowerBound = IntegerStamp.defaultMinValue(resultBits, unsigned);
            } else {
                lowerBound = saturate(inputStamp.lowerBound(), resultBits, unsigned);
            }

            long defaultMask = IntegerStamp.defaultMask(resultBits);
            long newDownMask = inputStamp.downMask() & defaultMask;
            long newUpMask = inputStamp.upMask() & defaultMask;
            long newLowerBound = (lowerBound | newDownMask) & newUpMask;
            long newUpperBound = (upperBound | newDownMask) & newUpMask;
            if (!unsigned) {
                newLowerBound = SignExtendNode.signExtend(newLowerBound, resultBits);
                newUpperBound = SignExtendNode.signExtend(newUpperBound, resultBits);
            }
            return new IntegerStamp(resultBits, unsigned, newLowerBound, newUpperBound, newDownMask, newUpMask);
        } else {
            return input.illegal();
        }
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

        long defaultMask = IntegerStamp.defaultMask(toKind.getBitCount());
        long intMask = IntegerStamp.defaultMask(32);
        long newUpMask = signExtend(fromStamp.upMask() & defaultMask, toKind) & intMask;
        long newDownMask = signExtend(fromStamp.downMask() & defaultMask, toKind) & intMask;
        return new IntegerStamp(toKind.getStackKind().getBitCount(), false, (int) ((lowerBound | newDownMask) & newUpMask), (int) ((upperBound | newDownMask) & newUpMask), newDownMask, newUpMask);
    }

    private static long signExtend(long value, Kind valueKind) {
        if (valueKind != Kind.Char && valueKind != Kind.Long && (value >>> (valueKind.getBitCount() - 1) & 1) == 1) {
            return value | (-1L << valueKind.getBitCount());
        } else {
            return value;
        }
    }

    private static long saturate(long v, int bits, boolean unsigned) {
        if (bits < 64) {
            long max = IntegerStamp.defaultMaxValue(bits, unsigned);
            if (v > max) {
                return max;
            }
            long min = IntegerStamp.defaultMinValue(bits, unsigned);
            if (v < min) {
                return min;
            }
        }
        return v;
    }

    private static long saturate(long v, Kind kind) {
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

    /**
     * Compute the stamp resulting from the unsigned comparison being true.
     * 
     * @return null if it's can't be true or it nothing useful can be encoded.
     */
    public static Stamp unsignedCompare(Stamp stamp, Stamp stamp2) {
        IntegerStamp x = (IntegerStamp) stamp;
        IntegerStamp y = (IntegerStamp) stamp2;
        if (x == x.unrestricted() && y == y.unrestricted()) {
            // Don't know anything.
            return null;
        }
        // c <| n, where c is a constant and n is known to be positive.
        if (x.lowerBound() == x.upperBound()) {
            if (y.isPositive()) {
                if (x.lowerBound() == (1 << x.getBits()) - 1) {
                    // Constant is MAX_VALUE which must fail.
                    return null;
                }
                if (x.lowerBound() <= y.lowerBound()) {
                    // Test will fail. Return illegalStamp instead?
                    return null;
                }
                // If the test succeeds then this proves that n is at greater than c so the bounds
                // are [c+1..-n.upperBound)].
                return StampFactory.forInteger(x.getBits(), false, x.lowerBound() + 1, y.upperBound());
            }
            return null;
        }
        // n <| c, where c is a strictly positive constant
        if (y.lowerBound() == y.upperBound() && y.isStrictlyPositive()) {
            // The test proves that n is positive and less than c, [0..c-1]
            return StampFactory.forInteger(y.getBits(), false, 0, y.lowerBound() - 1);
        }
        return null;
    }
}
