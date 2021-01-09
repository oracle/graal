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

import sun.misc.DoubleConsts;

import static java.lang.Integer.compareUnsigned;

public final class WasmMath {
    /**
     * The number of logical bits in the significand of a <code>double</code> number,
     * <strong>excluding</strong> the implicit bit.
     */
    private static final long DOUBLE_SIGNIFICAND_WIDTH = DoubleConsts.SIGNIFICAND_WIDTH - 1;

    private WasmMath() {
    }

    public static int addExactUnsigned(int a, int b) {
        final int result = a + b;
        if (compareUnsigned(result, a) < 0) {
            throw new ArithmeticException("unsigned int overflow");
        }
        return result;
    }

    public static int minUnsigned(int a, int b) {
        return compareUnsigned(a, b) < 0 ? a : b;
    }

    public static float unsignedIntToFloat(int x) {
        return unsignedIntToLong(x);
    }

    public static double unsignedIntToDouble(int x) {
        return unsignedIntToLong(x);
    }

    public static long unsignedIntToLong(int x) {
        // See https://stackoverflow.com/a/22938125.
        return x & 0xFFFFFFFFL;
    }

    public static float unsignedLongToFloat(long x) {
        if (x >= 0) {
            return x;
        }
        final long shiftedX = x + Long.MIN_VALUE;
        final boolean roundUp = shiftedX % (long) Math.ulp(0x1p63f) > (long) Math.ulp(0x1p63f) / 2;
        final long offset = (shiftedX / (long) Math.ulp(0x1p63f)) + (roundUp ? 1 : 0);
        return 0x1p63f + offset * Math.ulp(0x1p63f);
    }

    public static double unsignedLongToDouble(long x) {
        if (x >= 0) {
            return x;
        }
        final long shiftedX = x + Long.MIN_VALUE;
        final boolean roundUp = shiftedX % (long) Math.ulp(0x1p63) > (long) Math.ulp(0x1p63) / 2;
        final long offset = (shiftedX / (long) Math.ulp(0x1p63)) + (roundUp ? 1 : 0);
        return 0x1p63 + offset * Math.ulp(0x1p63);
    }

    public static long roundDoubleUnsigned(double x) {
        return x < Long.MAX_VALUE ? Math.round(x) : truncDoubleUnsigned(Math.round(x));
    }

    public static long truncFloat(float x) {
        return truncDouble(x);
    }

    public static long truncDouble(double x) {
        return (long) (x < 0.0 ? Math.ceil(x) : Math.floor(x));
    }

    public static long truncFloatUnsigned(float x) {
        return truncDoubleUnsigned(x);
    }

    public static long truncDoubleUnsigned(double x) {
        if (x < Long.MAX_VALUE) {
            return truncDouble(x);
        }
        final long shift = Math.getExponent(x) - DOUBLE_SIGNIFICAND_WIDTH;
        final long xBits = Double.doubleToRawLongBits(x);
        final long base = (1L << DOUBLE_SIGNIFICAND_WIDTH) | (xBits & DoubleConsts.SIGNIF_BIT_MASK);
        if (shift >= Long.SIZE - DOUBLE_SIGNIFICAND_WIDTH) {
            return Long.MAX_VALUE;
        } else if (shift > 0) {
            return base << shift;
        } else if (shift >= -DOUBLE_SIGNIFICAND_WIDTH) {
            return base >> -shift;
        } else {
            return 0;
        }
    }

}
