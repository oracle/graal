/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The class {@code WasmMath} contains methods for performing specific numeric operations such as
 * unsigned arithmetic, which are not built in Java nor provided by the {@link Math} class.
 */
public final class WasmMath {

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

    public static long minUnsigned(long a, long b) {
        return Long.compareUnsigned(a, b) < 0 ? a : b;
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
        return Integer.toUnsignedLong(x);
    }

    /**
     * Converts the given unsigned {@code int} to the closest {@code double} value.
     */
    public static double unsignedIntToDouble(int x) {
        return Integer.toUnsignedLong(x);
    }

}
