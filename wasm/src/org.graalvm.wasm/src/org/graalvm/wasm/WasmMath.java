/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm;

import com.oracle.truffle.api.ExactMath;

import static java.lang.Integer.compareUnsigned;

/**
 * The class {@code WasmMath} contains methods for performing specific numeric operations such as
 * unsigned arithmetic, which are not built in Java nor provided by the {@link Math} class.
 */
public final class WasmMath {

    /**
     * The number of logical bits in the significand of a {@code double}, <strong>excluding</strong>
     * the implicit bit.
     */
    private static final long DOUBLE_SIGNIFICAND_WIDTH = 52;

    /**
     * Bit mask to isolate the significand field of a <code>double</code>.
     */
    public static final long DOUBLE_SIGNIFICAND_BIT_MASK = 0x000FFFFFFFFFFFFFL;

    /**
     * The spacing between two consecutive {@code float} values (aka Unit in the Last Place) in the
     * range [2^31, 2^32): 2^40.
     */
    private static final long FLOAT_POWER_63_ULP = (long) Math.ulp(0x1p63f);

    /**
     * The spacing between two consecutive {@code double} values (aka Unit in the Last Place) in the
     * range [2^63, 2^64): 2^11.
     */
    private static final long DOUBLE_POWER_63_ULP = (long) Math.ulp(0x1p63);

    /**
     * Don't let anyone instantiate this class.
     */
    private WasmMath() {
    }

    /**
     * Returns the sum of two unsigned ints.
     *
     * @throws ArithmeticException if the operation overflows
     */
    public static int addExactUnsigned(int a, int b) {
        // See GR-28305 for more background and possible intrinsification of this method.
        final int result = a + b;
        if (compareUnsigned(result, a) < 0) {
            throw new ArithmeticException("unsigned int overflow");
        }
        return result;
    }

    /**
     * Returns the minimum of two unsigned ints.
     */
    public static int minUnsigned(int a, int b) {
        return compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Returns the maximum of two unsigned ints.
     */
    public static int maxUnsigned(int a, int b) {
        return compareUnsigned(a, b) > 0 ? a : b;
    }

    /**
     * Returns the value of the {@code long} argument as an {@code int}; throwing an exception if
     * the value overflows an unsigned {@code int}.
     *
     * @throws ArithmeticException if the argument is outside of the unsigned int32 range
     * @since 1.8
     */
    public static int toUnsignedIntExact(long value) {
        if (value < 0 || value > 0xffff_ffffL) {
            throw new ArithmeticException("unsigned int overflow");
        }
        return (int) value;
    }

    /**
     * Converts the given unsigned {@code int} to the closest {@code float} value.
     */
    public static float unsignedIntToFloat(int x) {
        return unsignedIntToLong(x);
    }

    /**
     * Converts the given unsigned {@code int} to the closest {@code double} value.
     */
    public static double unsignedIntToDouble(int x) {
        return unsignedIntToLong(x);
    }

    /**
     * Converts the given unsigned {@code int} to the corresponding {@code long}.
     */
    public static long unsignedIntToLong(int x) {
        // See https://stackoverflow.com/a/22938125.
        return x & 0xFFFFFFFFL;
    }

    /**
     * Converts the given unsigned {@code long} to the closest {@code float} value.
     */
    public static float unsignedLongToFloat(long x) {
        if (x >= 0) {
            // If the first bit is not set, then we can simply cast which is faster.
            return x;
        }

        // Transpose x from [Integer.MIN_VALUE,-1] to [0, Integer.MAX_VALUE]
        final long shiftedX = x + Long.MIN_VALUE;

        // We cannot simply compute 0x1p63f + shiftedX because it yields incorrect results in some
        // edge cases due to rounding twice (conversion to long, and addition). See
        // https://github.com/WebAssembly/spec/issues/421 and mentioned test cases for more context.
        // Instead, we manually compute the offset from 0x1p63f.
        final boolean roundUp = shiftedX % FLOAT_POWER_63_ULP > FLOAT_POWER_63_ULP / 2;
        final long offset = (shiftedX / FLOAT_POWER_63_ULP) + (roundUp ? 1 : 0);

        // Return the offset-nth next floating-point value starting from 2^63. This is equivalent to
        // incrementing the significand (as Math.nextUp would do) offset times.
        return 0x1p63f + (offset * (float) FLOAT_POWER_63_ULP);
    }

    /**
     * Converts the given unsigned {@code long} to the closest {@code double} value.
     */
    public static double unsignedLongToDouble(long x) {
        if (x >= 0) {
            // If the first bit is not set, then we can simply cast which is faster.
            return x;
        }

        // Transpose x from [Long.MIN_VALUE,-1] to [0, Long.MAX_VALUE].
        final long shiftedX = x + Long.MIN_VALUE;

        // We cannot simply compute 0x1p63 + shiftedX because it yields incorrect results in some
        // edge cases due to rounding twice (conversion to long, and addition). See
        // https://github.com/WebAssembly/spec/issues/421 and mentioned test cases for more context.
        // Instead, we manually compute the offset from 0x1p63.
        final boolean roundUp = shiftedX % DOUBLE_POWER_63_ULP > DOUBLE_POWER_63_ULP / 2;
        final long offset = (shiftedX / DOUBLE_POWER_63_ULP) + (roundUp ? 1 : 0);

        // Return the offset-nth next floating-point value starting form 2^63. This is equivalent to
        // incrementing the significand (as Math.nextUp would do) offset times.
        return 0x1p63 + (offset * (double) DOUBLE_POWER_63_ULP);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given float and
     * converts it to a <strong>signed</strong> long.
     * <p>
     * The operation is saturating: if the float is smaller than {@link Integer#MIN_VALUE} or larger
     * than {@link Integer#MAX_VALUE}, then respectively {@link Integer#MIN_VALUE} or
     * {@link Integer#MAX_VALUE} is returned.
     */
    public static long truncFloatToLong(float x) {
        return truncDoubleToLong(x);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given double and
     * converts it to a <strong>signed</strong> long.
     * <p>
     * The operation is saturating: if the double is smaller than {@link Long#MIN_VALUE} or larger
     * than {@link Long#MAX_VALUE}, then respectively {@link Long#MIN_VALUE} or
     * {@link Long#MAX_VALUE} is returned.
     */
    public static long truncDoubleToLong(double x) {
        return (long) ExactMath.truncate(x);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given float and
     * converts it to an <strong>unsigned</strong> long.
     * <p>
     * The operation is saturating: if the float is smaller than 0 or larger than 2^32 - 1, then
     * respectively 0 or 2^32 - 1 is returned.
     */
    public static long truncFloatToUnsignedLong(float x) {
        return truncDoubleToUnsignedLong(x);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given double and
     * converts it to an <strong>unsigned</strong> long.
     * <p>
     * The operation is saturating: if the double is smaller than 0 or larger than 2^64 - 1, then
     * respectively 0 or 2^64 - 1 is returned.
     */
    public static long truncDoubleToUnsignedLong(double x) {
        if (x < Long.MAX_VALUE) {
            // If the first bit is not set, then we use the signed variant which is faster.
            return truncDoubleToLong(x);
        }

        // There is no direct way to convert a double to an _unsigned_ long in Java. Therefore we
        // manually split the binary representation of x into significand (aka base or mantissa) and
        // exponent, and compute the resulting long by shifting the significand.

        final long shift = Math.getExponent(x) - DOUBLE_SIGNIFICAND_WIDTH;
        final long xBits = Double.doubleToRawLongBits(x);
        final long significand = (1L << DOUBLE_SIGNIFICAND_WIDTH) | (xBits & DOUBLE_SIGNIFICAND_BIT_MASK);

        if (shift >= Long.SIZE - DOUBLE_SIGNIFICAND_WIDTH) {
            // Saturation: if x is too large to convert to a long, we return the highest possible
            // value (all bits set).
            return 0xffff_ffff_ffff_ffffL;
        } else if (shift > 0) {
            // Multiply significand by 2^shift.
            return significand << shift;
        }

        // Should not reach here because x >= Long.MAX_VALUE, so shift >=
        // (Math.getExponent(Long.MAX_VALUE) - DOUBLE_SIGNIFICAND_WIDTH) == 11.

        if (shift >= -DOUBLE_SIGNIFICAND_WIDTH) {
            // Multiply significand by 2^shift == divide significand by 2^(-shift).
            return significand >> -shift;
        } else {
            // Saturation: if x is too small to convert to a long, we return 0.
            return 0;
        }
    }

}
