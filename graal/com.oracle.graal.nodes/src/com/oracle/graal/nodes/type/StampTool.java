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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Helper class that is used to keep all stamp-related operations in one place.
 */
public class StampTool {

    public static Stamp negate(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            int bits = integerStamp.getBits();
            if (integerStamp.lowerBound() != IntegerStamp.defaultMinValue(bits)) {
                // TODO(ls) check if the mask calculation is correct...
                return StampFactory.forInteger(bits, -integerStamp.upperBound(), -integerStamp.lowerBound());
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
            return new IntegerStamp(bits, ~integerStamp.upperBound(), ~integerStamp.lowerBound(), (~integerStamp.upMask()) & defaultMask, (~integerStamp.downMask()) & defaultMask);
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
        return StampFactory.illegal();
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
        return StampFactory.illegal();
    }

    public static Stamp div(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        if (stamp2.isStrictlyPositive()) {
            long lowerBound = stamp1.lowerBound() / stamp2.lowerBound();
            long upperBound = stamp1.upperBound() / stamp2.lowerBound();
            return StampFactory.forInteger(stamp1.getBits(), lowerBound, upperBound);
        }
        return stamp1.unrestricted();
    }

    public static Stamp rem(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return rem((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal();
    }

    public static Stamp rem(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        // zero is always possible
        long lowerBound = Math.min(stamp1.lowerBound(), 0);
        long upperBound = Math.max(stamp1.upperBound(), 0);

        long magnitude; // the maximum absolute value of the result, derived from stamp2
        if (stamp2.lowerBound() == IntegerStamp.defaultMinValue(stamp2.getBits())) {
            // Math.abs(...) - 1 does not work in this case
            magnitude = IntegerStamp.defaultMaxValue(stamp2.getBits());
        } else {
            magnitude = Math.max(Math.abs(stamp2.lowerBound()), Math.abs(stamp2.upperBound())) - 1;
        }
        lowerBound = Math.max(lowerBound, -magnitude);
        upperBound = Math.min(upperBound, magnitude);

        return StampFactory.forInteger(stamp1.getBits(), lowerBound, upperBound);
    }

    private static boolean addOverflowsPositively(long x, long y, int bits) {
        long result = x + y;
        if (bits == 64) {
            return (~x & ~y & result) < 0;
        } else {
            return result > IntegerStamp.defaultMaxValue(bits);
        }
    }

    private static boolean addOverflowsNegatively(long x, long y, int bits) {
        long result = x + y;
        if (bits == 64) {
            return (x & y & ~result) < 0;
        } else {
            return result < IntegerStamp.defaultMinValue(bits);
        }
    }

    public static IntegerStamp add(IntegerStamp stamp1, IntegerStamp stamp2) {
        int bits = stamp1.getBits();
        assert bits == stamp2.getBits();

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
        boolean lowerOverflowsPositively = addOverflowsPositively(stamp1.lowerBound(), stamp2.lowerBound(), bits);
        boolean upperOverflowsPositively = addOverflowsPositively(stamp1.upperBound(), stamp2.upperBound(), bits);
        boolean lowerOverflowsNegatively = addOverflowsNegatively(stamp1.lowerBound(), stamp2.lowerBound(), bits);
        boolean upperOverflowsNegatively = addOverflowsNegatively(stamp1.upperBound(), stamp2.upperBound(), bits);
        if ((lowerOverflowsNegatively && !upperOverflowsNegatively) || (!lowerOverflowsPositively && upperOverflowsPositively)) {
            lowerBound = IntegerStamp.defaultMinValue(bits);
            upperBound = IntegerStamp.defaultMaxValue(bits);
        } else {
            lowerBound = SignExtendNode.signExtend((stamp1.lowerBound() + stamp2.lowerBound()) & defaultMask, bits);
            upperBound = SignExtendNode.signExtend((stamp1.upperBound() + stamp2.upperBound()) & defaultMask, bits);
        }
        IntegerStamp limit = StampFactory.forInteger(bits, lowerBound, upperBound);
        newUpMask &= limit.upMask();
        upperBound = SignExtendNode.signExtend(upperBound & newUpMask, bits);
        newDownMask |= limit.downMask();
        lowerBound |= newDownMask;
        return new IntegerStamp(bits, lowerBound, upperBound, newDownMask, newUpMask);
    }

    public static Stamp sub(IntegerStamp stamp1, IntegerStamp stamp2) {
        if (stamp1.isUnrestricted() || stamp2.isUnrestricted()) {
            return stamp1.unrestricted();
        }
        return add(stamp1, (IntegerStamp) StampTool.negate(stamp2));
    }

    public static Stamp stampForMask(int bits, long downMask, long upMask) {
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
            upperBound = IntegerStamp.defaultMaxValue(bits) & upMask;
        }
        lowerBound = IntegerConvertNode.convert(lowerBound, bits, false);
        upperBound = IntegerConvertNode.convert(upperBound, bits, false);
        return new IntegerStamp(bits, lowerBound, upperBound, downMask, upMask);
    }

    public static Stamp and(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return and((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal();
    }

    public static Stamp and(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        return stampForMask(stamp1.getBits(), stamp1.downMask() & stamp2.downMask(), stamp1.upMask() & stamp2.upMask());
    }

    public static Stamp or(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return or((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal();
    }

    public static Stamp or(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        return stampForMask(stamp1.getBits(), stamp1.downMask() | stamp2.downMask(), stamp1.upMask() | stamp2.upMask());
    }

    public static Stamp xor(Stamp stamp1, Stamp stamp2) {
        if (stamp1 instanceof IntegerStamp && stamp2 instanceof IntegerStamp) {
            return xor((IntegerStamp) stamp1, (IntegerStamp) stamp2);
        }
        return StampFactory.illegal();
    }

    public static Stamp xor(IntegerStamp stamp1, IntegerStamp stamp2) {
        assert stamp1.getBits() == stamp2.getBits();
        long variableBits = (stamp1.downMask() ^ stamp1.upMask()) | (stamp2.downMask() ^ stamp2.upMask());
        long newDownMask = (stamp1.downMask() ^ stamp2.downMask()) & ~variableBits;
        long newUpMask = (stamp1.downMask() ^ stamp2.downMask()) | variableBits;
        return stampForMask(stamp1.getBits(), newDownMask, newUpMask);
    }

    public static Stamp rightShift(Stamp value, Stamp shift) {
        if (value instanceof IntegerStamp && shift instanceof IntegerStamp) {
            return rightShift((IntegerStamp) value, (IntegerStamp) shift);
        }
        return value.illegal();
    }

    public static Stamp rightShift(IntegerStamp value, IntegerStamp shift) {
        int bits = value.getBits();
        if (shift.lowerBound() == shift.upperBound()) {
            int extraBits = 64 - bits;
            long shiftMask = bits > 32 ? 0x3FL : 0x1FL;
            long shiftCount = shift.lowerBound() & shiftMask;
            long defaultMask = IntegerStamp.defaultMask(bits);
            // shifting back and forth performs sign extension
            long downMask = (value.downMask() << extraBits) >> (shiftCount + extraBits) & defaultMask;
            long upMask = (value.upMask() << extraBits) >> (shiftCount + extraBits) & defaultMask;
            return new IntegerStamp(bits, value.lowerBound() >> shiftCount, value.upperBound() >> shiftCount, downMask, upMask);
        }
        long mask = IntegerStamp.upMaskFor(bits, value.lowerBound(), value.upperBound());
        return stampForMask(bits, 0, mask);
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
            long downMask = value.downMask() >>> shiftCount;
            long upMask = value.upMask() >>> shiftCount;
            if (value.lowerBound() < 0) {
                return new IntegerStamp(bits, downMask, upMask, downMask, upMask);
            } else {
                return new IntegerStamp(bits, value.lowerBound() >>> shiftCount, value.upperBound() >>> shiftCount, downMask, upMask);
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
        if (shift.lowerBound() == shift.upperBound()) {
            int shiftAmount = (int) (shift.lowerBound() & shiftMask);
            if (shiftAmount == 0) {
                return value;
            }
            // the mask of bits that will be lost or shifted into the sign bit
            long removedBits = -1L << (bits - shiftAmount - 1);
            if ((value.lowerBound() & removedBits) == 0 && (value.upperBound() & removedBits) == 0) {
                // use a better stamp if neither lower nor upper bound can lose bits
                return new IntegerStamp(bits, value.lowerBound() << shiftAmount, value.upperBound() << shiftAmount, value.downMask() << shiftAmount, value.upMask() << shiftAmount);
            }
        }
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

            return new IntegerStamp(resultBits, inputStamp.lowerBound(), inputStamp.upperBound(), downMask, upMask);
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

            return new IntegerStamp(resultBits, lowerBound, upperBound, downMask, upMask);
        } else {
            return input.illegal();
        }
    }

    public static Stamp narrowingConversion(Stamp input, int resultBits) {
        if (input instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) input;
            int inputBits = inputStamp.getBits();
            assert resultBits <= inputBits;
            if (resultBits == inputBits) {
                return inputStamp;
            }

            final long upperBound;
            if (inputStamp.lowerBound() < IntegerStamp.defaultMinValue(resultBits)) {
                upperBound = IntegerStamp.defaultMaxValue(resultBits);
            } else {
                upperBound = saturate(inputStamp.upperBound(), resultBits);
            }
            final long lowerBound;
            if (inputStamp.upperBound() > IntegerStamp.defaultMaxValue(resultBits)) {
                lowerBound = IntegerStamp.defaultMinValue(resultBits);
            } else {
                lowerBound = saturate(inputStamp.lowerBound(), resultBits);
            }

            long defaultMask = IntegerStamp.defaultMask(resultBits);
            long newDownMask = inputStamp.downMask() & defaultMask;
            long newUpMask = inputStamp.upMask() & defaultMask;
            long newLowerBound = SignExtendNode.signExtend((lowerBound | newDownMask) & newUpMask, resultBits);
            long newUpperBound = SignExtendNode.signExtend((upperBound | newDownMask) & newUpMask, resultBits);
            return new IntegerStamp(resultBits, newLowerBound, newUpperBound, newDownMask, newUpMask);
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
        return new IntegerStamp(toKind.getStackKind().getBitCount(), (int) ((lowerBound | newDownMask) & newUpMask), (int) ((upperBound | newDownMask) & newUpMask), newDownMask, newUpMask);
    }

    private static long signExtend(long value, Kind valueKind) {
        if (valueKind != Kind.Char && valueKind != Kind.Long && (value >>> (valueKind.getBitCount() - 1) & 1) == 1) {
            return value | (-1L << valueKind.getBitCount());
        } else {
            return value;
        }
    }

    private static long saturate(long v, int bits) {
        if (bits < 64) {
            long max = IntegerStamp.defaultMaxValue(bits);
            if (v > max) {
                return max;
            }
            long min = IntegerStamp.defaultMinValue(bits);
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
                return StampFactory.forInteger(x.getBits(), x.lowerBound() + 1, y.upperBound());
            }
            return null;
        }
        // n <| c, where c is a strictly positive constant
        if (y.lowerBound() == y.upperBound() && y.isStrictlyPositive()) {
            // The test proves that n is positive and less than c, [0..c-1]
            return StampFactory.forInteger(y.getBits(), 0, y.lowerBound() - 1);
        }
        return null;
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#isLegal() legal} Object
     * value which is known to be always null.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value which is known to be always null
     */
    public static boolean isObjectAlwaysNull(ValueNode node) {
        return isObjectAlwaysNull(node.stamp());
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#isLegal() legal} Object
     * stamp whose values are known to be always null.
     *
     * @param stamp the stamp to check
     * @return true if this stamp represents a legal object stamp whose values are known to be
     *         always null
     */
    public static boolean isObjectAlwaysNull(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.isLegal()) {
            return ((AbstractObjectStamp) stamp).alwaysNull();
        }
        return false;
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#isLegal() legal} Object
     * value which is known to never be null.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value which is known to never be null
     */
    public static boolean isObjectNonNull(ValueNode node) {
        return isObjectNonNull(node.stamp());
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#isLegal() legal} Object
     * stamp whose values known to be always null.
     *
     * @param stamp the stamp to check
     * @return true if this stamp represents a legal object stamp whose values are known to be
     *         always null
     */
    public static boolean isObjectNonNull(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.isLegal()) {
            return ((AbstractObjectStamp) stamp).nonNull();
        }
        return false;
    }

    /**
     * Returns the {@linkplain ResolvedJavaType Java type} this {@linkplain ValueNode} has if it is
     * a {@linkplain Stamp#isLegal() legal} Object value.
     *
     * @param node the node to check
     * @return the Java type this value has if it is a legal Object type, null otherwise
     */
    public static ResolvedJavaType typeOrNull(ValueNode node) {
        return typeOrNull(node.stamp());
    }

    /**
     * Returns the {@linkplain ResolvedJavaType Java type} this {@linkplain Stamp} has if it is a
     * {@linkplain Stamp#isLegal() legal} Object stamp.
     *
     * @param stamp the stamp to check
     * @return the Java type this stamp has if it is a legal Object stamp, null otherwise
     */
    public static ResolvedJavaType typeOrNull(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.isLegal()) {
            return ((AbstractObjectStamp) stamp).type();
        }
        return null;
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#isLegal() legal} Object
     * value whose Java type is known exactly. If this method returns true then the
     * {@linkplain ResolvedJavaType Java type} returned by {@link #typeOrNull(ValueNode)} is the
     * concrete dynamic/runtime Java type of this value.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value whose Java type is known exactly
     */
    public static boolean isExactType(ValueNode node) {
        return isExactType(node.stamp());
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#isLegal() legal} Object
     * stamp whose {@linkplain ResolvedJavaType Java type} is known exactly. If this method returns
     * true then the Java type returned by {@link #typeOrNull(Stamp)} is the only concrete
     * dynamic/runtime Java type possible for values of this stamp.
     *
     * @param stamp the stamp to check
     * @return true if this node represents a legal object stamp whose Java type is known exactly
     */
    public static boolean isExactType(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.isLegal()) {
            return ((AbstractObjectStamp) stamp).isExactType();
        }
        return false;
    }
}
