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

import static java.lang.Integer.compareUnsigned;

public final class WasmMath {

    private static final long DOUBLE_MANTISSA_WIDTH = 52;
    private static final long DOUBLE_MANTISSA_MASK = 0b00000000_00001111_11111111_11111111_11111111_11111111_11111111_11111111L;
    private static final long DOUBLE_EXPONENT_MASK = 0b01111111_11110000_00000000_00000000_00000000_00000000_00000000_00000000L;
    private static final long DOUBLE_EXPONENT_BIAS = 1023;

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

    public static long unsignedInt32ToLong(int n) {
        // See https://stackoverflow.com/a/22938125.
        return n & 0xFFFFFFFFL;
    }

    public static long roundFloatUnsigned(float x) {
        return roundDoubleUnsigned(x);
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
        final long xBits = Double.doubleToRawLongBits(x);
        final long shift = ((xBits & DOUBLE_EXPONENT_MASK) >> DOUBLE_MANTISSA_WIDTH) - DOUBLE_EXPONENT_BIAS - DOUBLE_MANTISSA_WIDTH;
        final long base = (1L << DOUBLE_MANTISSA_WIDTH) | (xBits & DOUBLE_MANTISSA_MASK);
        if (shift >= Long.SIZE - DOUBLE_MANTISSA_WIDTH) {
            return Long.MAX_VALUE;
        } else if (shift > 0) {
            return base << shift;
        } else if (shift >= -DOUBLE_MANTISSA_WIDTH) {
            return base >> -shift;
        } else {
            return 0;
        }
    }

}
