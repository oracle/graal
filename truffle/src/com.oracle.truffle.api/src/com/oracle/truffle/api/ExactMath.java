/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * This class contains exact math related methods that are generally useful for dynamic language
 * implementations.
 *
 * @since 0.8 or earlier
 */
public final class ExactMath {

    private ExactMath() {
    }

    /**
     * @since 0.8 or earlier
     */
    public static int multiplyHigh(int x, int y) {
        long r = (long) x * (long) y;
        return (int) (r >> 32);
    }

    /**
     * @since 0.8 or earlier
     */
    public static int multiplyHighUnsigned(int x, int y) {
        long xl = x & 0xFFFFFFFFL;
        long yl = y & 0xFFFFFFFFL;
        long r = xl * yl;
        return (int) (r >> 32);
    }

    /**
     * @since 0.8 or earlier
     */
    public static long multiplyHigh(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >> 32);
    }

    /**
     * @since 0.8 or earlier
     */
    public static long multiplyHighUnsigned(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >>> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >>> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >>> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >>> 32);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given float.
     * <p>
     * This corresponds to the IEEE 754 {@code roundToIntegralTowardZero} operation (IEEE Std
     * 754-2008, section 5.9, page 41).
     *
     * @since 21.1
     */
    public static float truncate(float x) {
        return (float) truncate((double) x);
    }

    /**
     * Removes the decimal part (aka truncation or rounds towards zero) of the given double.
     * <p>
     * This corresponds to the IEEE 754 {@code roundToIntegralTowardZero} operation (IEEE Std
     * 754-2008, section 5.9, page 41).
     *
     * @since 21.1
     */
    public static double truncate(double x) {
        return x < 0.0 ? Math.ceil(x) : Math.floor(x);
    }

    /**
     * Converts the double value to unsigned long with truncation (i.e. rounding towards zero) and
     * saturation of <code>NaN</code> and <code>x &lt;= -1</code> to <code>0</code>, and
     * <code>x >= 2<sup>64</sup></code> to <code>2<sup>63</sup>-1</code> (<code>-1</code>).
     * <p>
     * Non-saturating (e.g. trapping) behavior can be implemented by checking the input for NaN,
     * underflow (<code>x &lt;= -1</code>) and overflow (<code>x >= 0x1p64</code>) first.
     *
     * @param x input value
     * @return the unsigned integer result, wrapped in a signed integer
     * @since 25.0
     */
    public static long truncateToUnsignedLong(double x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, x >= 0x1p63)) {
            // x >= 2**63
            long signedResult = (long) (x - 0x1p63);
            return signedResult | (1L << 63);
        } else {
            // x < 2**63 or NaN
            long signedResult = (long) x;
            return signedResult & ~(signedResult >> 63); // max(result, 0)
        }
    }

    /**
     * Converts the float value to unsigned long with truncation (i.e. rounding towards zero) and
     * saturation of <code>NaN</code> and <code>x &lt;= -1</code> to <code>0</code>, and
     * <code>x >= 2<sup>64</sup></code> to <code>2<sup>63</sup>-1</code> (<code>-1</code>).
     * <p>
     * Non-saturating (e.g. trapping) behavior can be implemented by checking the input for NaN,
     * underflow (<code>x &lt;= -1f</code>) and overflow (<code>x >= 0x1p64f</code>) first.
     *
     * @param x input value
     * @return the unsigned integer result, wrapped in a signed integer
     * @since 25.0
     */
    public static long truncateToUnsignedLong(float x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, x >= 0x1p63f)) {
            // x >= 2**63
            long signedResult = (long) (x - 0x1p63f);
            return signedResult | (1L << 63);
        } else {
            // x < 2**31 or NaN
            long signedResult = (long) x;
            return (signedResult & ~(signedResult >> 63)); // max(result, 0)
        }
    }

    /**
     * Converts the double value to unsigned long with truncation (i.e. rounding towards zero) and
     * saturation of <code>NaN</code> and <code>x &lt;= -1</code> to <code>0</code>, and
     * <code>x >= 2<sup>32</sup></code> to <code>2<sup>31</sup>-1</code> (<code>-1</code>).
     * <p>
     * Non-saturating (e.g. trapping) behavior can be implemented by checking the input for NaN,
     * underflow (<code>x &lt;= -1</code>) and overflow (<code>x >= 0x1p32</code>) first.
     *
     * @param x input value
     * @return the unsigned integer result, wrapped in a signed integer
     * @since 25.0
     */
    public static int truncateToUnsignedInt(double x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, x >= 0x1p31)) {
            // x >= 2**31
            int signedResult = (int) (x - 0x1p31);
            return signedResult | (1 << 31);
        } else {
            // x < 2**31 or NaN
            int signedResult = (int) x;
            return (signedResult & ~(signedResult >> 31)); // max(result, 0)
        }
    }

    /**
     * Converts the float value to unsigned int with truncation (i.e. rounding towards zero) and
     * saturation of <code>NaN</code> and <code>x &lt;= -1</code> to <code>0</code>, and
     * <code>x >= 2<sup>32</sup></code> to <code>2<sup>31</sup>-1</code> (<code>-1</code>).
     * <p>
     * Non-saturating (e.g. trapping) behavior can be implemented by checking the input for NaN,
     * underflow (<code>x &lt;= -1</code>) and overflow (<code>x >= 0x1p64</code>) first.
     *
     * @param x input value
     * @return the unsigned integer result, wrapped in a signed integer
     * @since 25.0
     */
    public static int truncateToUnsignedInt(float x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, x >= 0x1p31f)) {
            // x >= 2**31
            int signedResult = (int) (x - 0x1p31f);
            return signedResult | (1 << 31);
        } else {
            // x < 2**31 or NaN
            int signedResult = (int) x;
            return (signedResult & ~(signedResult >> 31)); // max(result, 0)
        }
    }

    /**
     * Converts the given unsigned {@code long} to the closest {@code double} value.
     *
     * @param x unsigned integer input, wrapped in a signed integer
     * @return the {@code double} result
     * @since 25.0
     */
    public static double unsignedToDouble(long x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, x >= 0)) {
            return x;
        } else {
            // unsigned ceil div by 2, convert to double, and multiply by 2.
            // the lsb is needed to ensure correct rounding to nearest even.
            double halfRoundUp = ((x >>> 1) | (x & 1));
            return halfRoundUp + halfRoundUp;
        }
    }

    /**
     * Converts the given unsigned {@code long} to the closest {@code float} value.
     *
     * @param x unsigned integer input, wrapped in a signed integer
     * @return the {@code float} result
     * @since 25.0
     */
    public static float unsignedToFloat(long x) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, x >= 0)) {
            return x;
        } else {
            // unsigned ceil div by 2, convert to float, and multiply by 2.
            // the lsb is needed to ensure correct rounding to nearest even.
            float halfRoundUp = ((x >>> 1) | (x & 1));
            return halfRoundUp + halfRoundUp;
        }
    }
}
