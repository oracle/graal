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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;

/**
 * Helper class that is used to keep all stamp-related operations in one place.
 */
public class StampTool {

    public static Stamp not(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            int bits = integerStamp.getBits();
            long defaultMask = CodeUtil.mask(bits);
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
            upperBound = CodeUtil.maxValue(bits) & upMask;
        }
        lowerBound = CodeUtil.convert(lowerBound, bits, false);
        upperBound = CodeUtil.convert(upperBound, bits, false);
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
            long defaultMask = CodeUtil.mask(bits);
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
        long defaultMask = CodeUtil.mask(bits);
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

            long defaultMask = CodeUtil.mask(resultBits);
            long downMask = CodeUtil.signExtend(inputStamp.downMask(), inputBits) & defaultMask;
            long upMask = CodeUtil.signExtend(inputStamp.upMask(), inputBits) & defaultMask;

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

            long downMask = CodeUtil.zeroExtend(inputStamp.downMask(), inputBits);
            long upMask = CodeUtil.zeroExtend(inputStamp.upMask(), inputBits);

            if (inputStamp.lowerBound() < 0 && inputStamp.upperBound() >= 0) {
                // signed range including 0 and -1
                // after sign extension, the whole range from 0 to MAX_INT is possible
                return stampForMask(resultBits, downMask, upMask);
            }

            long lowerBound = CodeUtil.zeroExtend(inputStamp.lowerBound(), inputBits);
            long upperBound = CodeUtil.zeroExtend(inputStamp.upperBound(), inputBits);

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
            if (inputStamp.lowerBound() < CodeUtil.minValue(resultBits)) {
                upperBound = CodeUtil.maxValue(resultBits);
            } else {
                upperBound = saturate(inputStamp.upperBound(), resultBits);
            }
            final long lowerBound;
            if (inputStamp.upperBound() > CodeUtil.maxValue(resultBits)) {
                lowerBound = CodeUtil.minValue(resultBits);
            } else {
                lowerBound = saturate(inputStamp.lowerBound(), resultBits);
            }

            long defaultMask = CodeUtil.mask(resultBits);
            long newDownMask = inputStamp.downMask() & defaultMask;
            long newUpMask = inputStamp.upMask() & defaultMask;
            long newLowerBound = CodeUtil.signExtend((lowerBound | newDownMask) & newUpMask, resultBits);
            long newUpperBound = CodeUtil.signExtend((upperBound | newDownMask) & newUpMask, resultBits);
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

        long defaultMask = CodeUtil.mask(toKind.getBitCount());
        long intMask = CodeUtil.mask(32);
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
            long max = CodeUtil.maxValue(bits);
            if (v > max) {
                return max;
            }
            long min = CodeUtil.minValue(bits);
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
