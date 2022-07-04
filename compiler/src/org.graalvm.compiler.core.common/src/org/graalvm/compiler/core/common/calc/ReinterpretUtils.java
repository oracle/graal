/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.core.common.calc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.type.ArithmeticStamp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * Utilities for bitwise reinterpret operations on primitive types.
 */
public class ReinterpretUtils {
    /**
     * Reinterpret the constant as a constant matching the given stamp.
     */
    public static Constant foldConstant(Stamp resultStamp, Constant constant) {
        SerializableConstant c = (SerializableConstant) constant;
        /*
         * We don't care about byte order here. Either would produce the correct result.
         */
        ByteBuffer buffer = ByteBuffer.wrap(new byte[c.getSerializedSize()]).order(ByteOrder.nativeOrder());
        c.serialize(buffer);

        buffer.rewind();
        SerializableConstant ret = ((ArithmeticStamp) resultStamp).deserialize(buffer);

        assert !buffer.hasRemaining();
        return ret;
    }

    /**
     * Compute the {@link IntegerStamp} from a {@link FloatStamp}, losing as little information as
     * possible.
     *
     * Sorting by their bit pattern reinterpreted as signed integers gives the following order of
     * floating point numbers:
     *
     * -0 | negative numbers | -Inf | NaNs | 0 | positive numbers | +Inf | NaNs
     *
     * So we can compute a better integer range if we know that the input is positive, negative,
     * finite, non-zero and/or not NaN.
     */
    public static IntegerStamp floatToInt(FloatStamp stamp) {
        int bits = stamp.getBits();

        long signBit = 1L << (bits - 1);
        long exponentMask;
        if (bits == 64) {
            exponentMask = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
        } else {
            assert bits == 32;
            exponentMask = Float.floatToRawIntBits(Float.POSITIVE_INFINITY);
        }

        long positiveInfinity = exponentMask;
        long negativeInfinity = CodeUtil.signExtend(signBit | positiveInfinity, bits);
        long negativeZero = CodeUtil.signExtend(signBit | 0, bits);

        if (stamp.isNaN()) {
            // special case: in addition to the range, we know NaN has all exponent bits set
            return IntegerStamp.create(bits, negativeInfinity + 1, CodeUtil.maxValue(bits), exponentMask, CodeUtil.mask(bits));
        }

        long upperBound;
        if (stamp.isNonNaN()) {
            if (stamp.upperBound() < 0.0) {
                if (stamp.lowerBound() > Double.NEGATIVE_INFINITY) {
                    upperBound = negativeInfinity - 1;
                } else {
                    upperBound = negativeInfinity;
                }
            } else if (stamp.upperBound() == 0.0) {
                upperBound = 0;
            } else if (stamp.upperBound() < Double.POSITIVE_INFINITY) {
                upperBound = positiveInfinity - 1;
            } else {
                upperBound = positiveInfinity;
            }
        } else {
            upperBound = CodeUtil.maxValue(bits);
        }

        long lowerBound;
        if (stamp.lowerBound() > 0.0) {
            if (stamp.isNonNaN()) {
                lowerBound = 1;
            } else {
                lowerBound = negativeInfinity + 1;
            }
        } else if (stamp.upperBound() == Double.NEGATIVE_INFINITY) {
            lowerBound = negativeInfinity;
        } else if (stamp.upperBound() < 0.0) {
            lowerBound = negativeZero + 1;
        } else {
            lowerBound = negativeZero;
        }

        return StampFactory.forInteger(bits, lowerBound, upperBound);
    }

    /**
     * Compute the {@link IntegerStamp} from a {@link FloatStamp}, losing as little information as
     * possible.
     *
     * Sorting by their bit pattern reinterpreted as signed integers gives the following order of
     * floating point numbers:
     *
     * -0 | negative numbers | -Inf | NaNs | 0 | positive numbers | +Inf | NaNs
     *
     * So from certain integer ranges we may be able to infer something about the sign, finiteness
     * or NaN-ness of the result.
     */
    public static FloatStamp intToFloat(IntegerStamp stamp) {
        int bits = stamp.getBits();

        double minPositive;
        double maxPositive;

        long signBit = 1L << (bits - 1);
        long exponentMask;
        if (bits == 64) {
            exponentMask = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
            minPositive = Double.MIN_VALUE;
            maxPositive = Double.MAX_VALUE;
        } else {
            assert bits == 32;
            exponentMask = Float.floatToRawIntBits(Float.POSITIVE_INFINITY);
            minPositive = Float.MIN_VALUE;
            maxPositive = Float.MAX_VALUE;
        }

        long significandMask = CodeUtil.mask(bits) & ~(signBit | exponentMask);

        long positiveInfinity = exponentMask;
        long negativeInfinity = CodeUtil.signExtend(signBit | positiveInfinity, bits);
        long negativeZero = CodeUtil.signExtend(signBit | 0, bits);

        if ((stamp.downMask() & exponentMask) == exponentMask && (stamp.downMask() & significandMask) != 0) {
            // if all exponent bits and at least one significand bit are set, the result is NaN
            return new FloatStamp(bits, Double.NaN, Double.NaN, false);
        }

        double upperBound;
        if (stamp.upperBound() < negativeInfinity) {
            if (stamp.lowerBound() > negativeZero) {
                upperBound = -minPositive;
            } else {
                upperBound = -0.0;
            }
        } else if (stamp.upperBound() < 0) {
            if (stamp.lowerBound() > negativeInfinity) {
                return new FloatStamp(bits, Double.NaN, Double.NaN, false);
            } else if (stamp.lowerBound() == negativeInfinity) {
                upperBound = Double.NEGATIVE_INFINITY;
            } else if (stamp.lowerBound() > negativeZero) {
                upperBound = -minPositive;
            } else {
                upperBound = -0.0;
            }
        } else if (stamp.upperBound() == 0) {
            upperBound = 0.0;
        } else if (stamp.upperBound() < positiveInfinity) {
            upperBound = maxPositive;
        } else {
            upperBound = Double.POSITIVE_INFINITY;
        }

        double lowerBound;
        if (stamp.lowerBound() > positiveInfinity) {
            return new FloatStamp(bits, Double.NaN, Double.NaN, false);
        } else if (stamp.lowerBound() == positiveInfinity) {
            lowerBound = Double.POSITIVE_INFINITY;
        } else if (stamp.lowerBound() > 0) {
            lowerBound = minPositive;
        } else if (stamp.lowerBound() > negativeInfinity) {
            lowerBound = 0.0;
        } else {
            lowerBound = Double.NEGATIVE_INFINITY;
        }

        boolean nonNaN;
        if ((stamp.upMask() & exponentMask) != exponentMask) {
            // NaN has all exponent bits set
            nonNaN = true;
        } else {
            boolean negativeNaNBlock = stamp.lowerBound() < 0 && stamp.upperBound() > negativeInfinity;
            boolean positiveNaNBlock = stamp.upperBound() > positiveInfinity;
            nonNaN = !negativeNaNBlock && !positiveNaNBlock;
        }

        return new FloatStamp(bits, lowerBound, upperBound, nonNaN);
    }
}
