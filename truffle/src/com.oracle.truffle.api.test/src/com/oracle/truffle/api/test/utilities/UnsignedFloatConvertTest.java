/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.utilities;

import static java.util.Map.entry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.ExactMath;

/**
 * Tests unsigned integer from and to floating point conversion methods.
 *
 * @see ExactMath
 */
public class UnsignedFloatConvertTest {

    /**
     * Signaling NaN values.
     */
    static final double DOUBLE_SNAN = Double.longBitsToDouble(0x7ff0000000000001L);
    static final float FLOAT_SNAN = Float.intBitsToFloat(0x7f800001);

    private static long minUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) < 0 ? a : b;
    }

    /**
     * Clamps uint64 values to uint32 range.
     */
    private static int clampU64ToU32(long value) {
        return (int) minUnsigned(value, 0xffff_ffffL);
    }

    /**
     * Intersperse values with flipped sign bit.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Number> Iterable<Map.Entry<T, Long>> withNegatives(Collection<Map.Entry<T, Long>> positives) {
        return () -> positives.stream().flatMap(e -> Stream.of(e,
                        entry((T) (e.getKey() instanceof Double d ? Double.longBitsToDouble(Double.doubleToRawLongBits(d) ^ (1L << 63))
                                        : e.getKey() instanceof Float f ? Float.intBitsToFloat(Float.floatToRawIntBits(f) ^ (1 << 31))
                                                        : e.getKey()),
                                        0L))).iterator();
    }

    /**
     * @see ExactMath#truncateToUnsignedInt(float)
     * @see ExactMath#truncateToUnsignedLong(float)
     */
    @Test
    public void floatToUnsignedInteger() {
        for (var pair : withNegatives(List.of(
                        entry(0.0f, 0L),

                        entry(Float.NaN, 0L),
                        entry(FLOAT_SNAN, 0L),

                        entry(Float.POSITIVE_INFINITY, ~0L),

                        entry(0x1p-149f, 0L),
                        entry(0x1.ccccccp-1f, 0L),
                        entry(0x1.fffffep-1f, 0L),

                        entry(1.0f, 1L),
                        entry(1.1f, 1L),
                        entry(1.5f, 1L),
                        entry(1.9f, 1L),
                        entry(2.0f, 2L),

                        entry(0x1.fffffep+31f, 0xffffff00L),
                        entry(0x1.0p+31f, 1L << 31),
                        entry(0x1.0p+32f, 1L << 32),

                        entry(1e+8f, 100_000_000L),
                        entry(1e+16f, 10_000_000_272_564_224L),

                        entry(0x1.fffffcp+22f, 0x00000000_007fffffL),
                        // largest representable non-integral value (0x1p23 - 0.5)
                        entry(0x1.fffffep+22f, 0x00000000_007fffffL),
                        entry(0x1.000000p+23f, 0x00000000_00800000L),
                        entry(0x1.000002p+23f, 0x00000000_00800001L),

                        entry(0x1.000000p+24f, 0x00000000_01000000L),
                        entry(0x1.000002p+24f, 0x00000000_01000002L),
                        entry(0x1.000004p+24f, 0x00000000_01000004L),

                        entry(0x1.000000p+53f, 0x00200000_00000000L),
                        entry(0x1.000001p+53f, 0x00200000_00000000L),
                        entry(0x1.000002p+53f, 0x00200000_40000000L),
                        entry(0x1.fffffep+62f, 0x7fffff80_00000000L),
                        entry(0x1.000000p+63f, Long.MIN_VALUE),
                        entry(0x1.000002p+63f, 0x80000100_00000000L),
                        entry(0x1.ffc000p+63f, 0xffe00000_00000000L),
                        entry(0x1.fffffcp+63f, 0xfffffe00_00000000L),
                        entry(0x1.fffffep+63f, 0xffffff00_00000000L),

                        entry(0x1.0p+64f, ~0L),
                        entry(Float.MAX_VALUE, ~0L)))) {
            final float x = pair.getKey();
            long expected = pair.getValue();
            long actual = ExactMath.truncateToUnsignedLong(x);

            Assert.assertEquals("truncateFloatToUnsignedLong(%s, %s) = expected: 0x%s, actual: 0x%s".formatted(x, Float.toHexString(x),
                            Long.toUnsignedString(expected, 16), Long.toUnsignedString(actual, 16)),
                            expected, actual);

            int expectedInt = clampU64ToU32(expected);
            int actualInt = ExactMath.truncateToUnsignedInt(x);

            Assert.assertEquals("truncateFloatToUnsignedInt(%s, %s) = expected: 0x%s, actual: 0x%s".formatted(x, Float.toHexString(x),
                            Integer.toUnsignedString(expectedInt, 16), Integer.toUnsignedString(actualInt, 16)),
                            expectedInt, actualInt);
        }
    }

    /**
     * @see ExactMath#truncateToUnsignedInt(double)
     * @see ExactMath#truncateToUnsignedLong(double)
     */
    @Test
    public void doubleToUnsignedInteger() {
        for (Map.Entry<Double, Long> pair : withNegatives(List.of(
                        entry(0.0, 0L),

                        entry(Double.NaN, 0L),
                        entry(DOUBLE_SNAN, 0L),

                        entry(Double.POSITIVE_INFINITY, ~0L),

                        entry(0x1p-149, 0L),
                        entry(0x1.ccccccccccccdp-1, 0L),
                        entry(0x1.fffffffffffffp-1, 0L),

                        entry(1.0, 1L),
                        entry(1.1, 1L),
                        entry(1.5, 1L),
                        entry(1.9, 1L),
                        entry(2.0, 2L),

                        entry(1e+8, 100_000_000L),
                        entry(1e+16, 10_000_000_000_000_000L),

                        entry(0x1.0p+31, 1L << 31),
                        entry(0x1.fffffe0000000p+31, 0x00000000_ffffff00L),
                        entry(0x1.fffffffc00000p+31, 0x00000000_fffffffeL),
                        entry(0x1.ffffffffffffep+31, 0x00000000_ffffffffL),
                        entry(0x1.fffffffffffffp+31, 0x00000000_ffffffffL),
                        entry(0x1.0p+32, 1L << 32),

                        entry(0x1.ffffffffffffep+51, 0x000fffff_ffffffffL),
                        // largest representable non-integral value (0x1p52 - 0.5)
                        entry(0x1.fffffffffffffp+51, 0x000fffff_ffffffffL),
                        entry(0x1.0000000000000p+52, 0x00100000_00000000L),

                        entry(0x1.0000000000000p+53, 0x00200000_00000000L),
                        entry(0x1.0000000000001p+53, 0x00200000_00000002L),
                        entry(0x1.0000000000002p+53, 0x00200000_00000004L),
                        entry(0x1.0000010000000p+53, 0x00200000_20000000L),
                        entry(0x1.0000020000000p+53, 0x00200000_40000000L),
                        entry(0x1.fffffe0000000p+62, 0x7fffff80_00000000L),
                        entry(0x1.fffffffffffffp+62, 0x7fffffff_fffffc00L),
                        entry(0x1.0000000000000p+63, Long.MIN_VALUE),
                        entry(0x1.0000000000001p+63, 0x80000000_00000800L),
                        entry(0x1.0000000000002p+63, 0x80000000_00001000L),
                        entry(0x1.0000020000000p+63, 0x80000100_00000000L),
                        entry(0x1.ffc0000000000p+63, 0xffe00000_00000000L),
                        entry(0x1.fffffe0000000p+63, 0xffffff00_00000000L),
                        entry(0x1.fffffffffffffp+63, 0xffffffff_fffff800L),

                        entry(0x1.0p+64, ~0L),
                        entry((double) Float.MAX_VALUE, ~0L),
                        entry(Double.MAX_VALUE, ~0L)))) {
            final double x = pair.getKey();
            long expected = pair.getValue();
            long actual = ExactMath.truncateToUnsignedLong(x);
            Assert.assertEquals("truncateDoubleToUnsignedLong(%s, %s) = expected: 0x%s, actual: 0x%s".formatted(x, Double.toHexString(x),
                            Long.toUnsignedString(expected, 16), Long.toUnsignedString(actual, 16)),
                            expected, actual);

            int expectedInt = clampU64ToU32(expected);
            int actualInt = ExactMath.truncateToUnsignedInt(x);
            Assert.assertEquals("truncateDoubleToUnsignedInt(%s, %s) = expected: 0x%s, actual: 0x%s".formatted(x, Double.toHexString(x),
                            Integer.toUnsignedString(expectedInt, 16), Integer.toUnsignedString(actualInt, 16)),
                            expectedInt, actualInt);
        }
    }

    /**
     * @see ExactMath#unsignedToFloat(long)
     */
    @Test
    public void unsignedToFloat() {
        for (var pair : List.of(
                        entry(0L, 0.0f),
                        entry(1L, 1.0f),

                        entry(0x00000000_007fffffL, 0x1.fffffcp+22f),
                        entry(0x00000000_00800000L, 0x1.0p+23f),

                        entry(0x00000000_01000000L, 0x1.0p+24f),
                        entry(0x00000000_01000001L, 0x1.0p+24f),
                        entry(0x00000000_01000002L, 0x1.000002p+24f),
                        entry(0x00000000_01000003L, 0x1.000004p+24f),

                        entry(0x00000000_ffffff00L, 0x1.fffffep+31f),
                        entry(0x00000000_ffffff7fL, 0x1.fffffep+31f),
                        entry(0x00000000_ffffff80L, 0x1.0p+32f),
                        entry(0x00000000_ffffffd1L, 0x1.0p+32f),
                        entry(0x00000001_00000000L, 0x1.0p+32f),

                        entry(0x00200000_00000000L, 0x1.0p+53f),
                        entry(0x00200000_20000000L, 0x1.0p+53f),
                        entry(0x00200000_20000001L, 0x1.000002p+53f),

                        entry(0x7fffff40_00000000L, 0x1.fffffcp+62f),
                        entry(0x7fffff40_00000001L, 0x1.fffffep+62f),
                        entry(0x7fffffbf_ffffffffL, 0x1.fffffep+62f),
                        entry(0x7fffffc0_00000000L, 0x1.0p+63f),
                        entry(0x7fffffff_ffffffffL, 0x1.0p+63f), // Long.MAX_VALUE
                        entry(0x80000000_00000000L, 0x1.0p+63f), // Long.MIN_VALUE
                        entry(0x80000080_00000000L, 0x1.0p+63f),
                        entry(0x80000080_00000001L, 0x1.000002p+63f),
                        entry(0x80000100_00000000L, 0x1.000002p+63f),
                        entry(0xffdfffff_dfffffffL, 0x1.ffc000p+63f),
                        entry(0xfffffe80_00000000L, 0x1.fffffcp+63f),
                        entry(0xfffffe80_00000001L, 0x1.fffffep+63f),
                        entry(0xfffffe80_00000002L, 0x1.fffffep+63f),
                        entry(0xffffff00_00000000L, 0x1.fffffep+63f),
                        entry(0xffffff7f_ffffffffL, 0x1.fffffep+63f),
                        entry(0xffffff80_00000000L, 0x1.0p+64f),
                        entry(0xffffffff_dfffffffL, 0x1.0p+64f),
                        entry(0xffffffff_ffffffffL, 0x1.0p+64f))) {
            final long x = pair.getKey();
            float expected = pair.getValue();
            float actual = ExactMath.unsignedToFloat(x);
            Assert.assertEquals("unsignedLongToFloat(%s, 0x%s) = expected: %s, actual: %s".formatted(
                            Long.toUnsignedString(x), Long.toUnsignedString(x, 16),
                            Float.toHexString(expected), Float.toHexString(actual)),
                            expected, actual, 0.0f);
        }
    }

    /**
     * @see ExactMath#unsignedToDouble(long)
     */
    @Test
    public void unsignedToDouble() {
        for (var pair : List.of(
                        entry(0L, 0.0),
                        entry(1L, 1.0),

                        entry(0x00000000_007fffffL, 0x1.fffffcp+22),
                        entry(0x00000000_00800000L, 0x1.0p+23),

                        entry(0x00000000_fffffffeL, 0x1.fffffffcp+31),
                        entry(0x00000000_ffffffffL, 0x1.fffffffep+31),
                        entry(0x00000001_00000000L, 0x1.0p+32),

                        entry(0x000fffff_ffffffffL, 0x1.ffffffffffffep+51),
                        entry(0x00100000_00000000L, 0x1.0p+52),

                        entry(0x00200000_00000000L, 0x1.0p+53),
                        entry(0x00200000_00000001L, 0x1.0p+53),
                        entry(0x00200000_00000002L, 0x1.0000000000001p+53),
                        entry(0x00200000_00000003L, 0x1.0000000000002p+53),

                        entry(0x7fffffff_fffffa00L, 0x1.ffffffffffffep+62),
                        entry(0x7fffffff_fffffa01L, 0x1.fffffffffffffp+62),
                        entry(0x7fffffff_fffffdffL, 0x1.fffffffffffffp+62),
                        entry(0x7fffffff_fffffe00L, 0x1.0p+63),
                        entry(0x7fffffff_ffffffffL, 0x1.0p+63), // Long.MAX_VALUE
                        entry(0x80000000_00000000L, 0x1.0p+63), // Long.MIN_VALUE
                        entry(0x80000000_00000400L, 0x1.0p+63),
                        entry(0x80000000_00000401L, 0x1.0000000000001p+63),
                        entry(0x80000000_00000402L, 0x1.0000000000001p+63),
                        entry(0x80000000_00000800L, 0x1.0000000000001p+63),
                        entry(0x80000000_00000bffL, 0x1.0000000000001p+63),
                        entry(0x80000000_00000c00L, 0x1.0000000000002p+63),
                        entry(0x80000000_00001000L, 0x1.0000000000002p+63),
                        entry(0xffffffff_fffff400L, 0x1.ffffffffffffep+63),
                        entry(0xffffffff_fffff401L, 0x1.fffffffffffffp+63),
                        entry(0xffffffff_fffff402L, 0x1.fffffffffffffp+63),
                        entry(0xffffffff_fffffbffL, 0x1.fffffffffffffp+63),
                        entry(0xffffffff_fffffc00L, 0x1.0p+64),
                        entry(0xffffffff_ffffffffL, 0x1.0p+64))) {
            final long x = pair.getKey();
            double expected = pair.getValue();
            double actual = ExactMath.unsignedToDouble(x);
            Assert.assertEquals("unsignedLongToDouble(%s, 0x%s) = expected: %s, actual: %s".formatted(
                            Long.toUnsignedString(x), Long.toUnsignedString(x, 16),
                            Double.toHexString(expected), Double.toHexString(actual)),
                            expected, actual, 0.0);
        }
    }
}
