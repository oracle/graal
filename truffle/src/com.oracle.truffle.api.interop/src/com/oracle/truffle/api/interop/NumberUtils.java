/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

final class NumberUtils {

    private static final double DOUBLE_MAX_SAFE_INTEGER = 9007199254740991d; // 2 ** 53 - 1
    static final long LONG_MAX_SAFE_DOUBLE = 9007199254740991L; // 2 ** 53 - 1
    private static final float FLOAT_MAX_SAFE_INTEGER = 16777215f; // 2 ** 24 - 1
    static final int INT_MAX_SAFE_FLOAT = 16777215; // 2 ** 24 - 1

    private NumberUtils() {
        /* No instances */
    }

    static boolean inSafeIntegerRange(double d) {
        return d >= -DOUBLE_MAX_SAFE_INTEGER && d <= DOUBLE_MAX_SAFE_INTEGER;
    }

    static boolean inSafeDoubleRange(long l) {
        return l >= -LONG_MAX_SAFE_DOUBLE && l <= LONG_MAX_SAFE_DOUBLE;
    }

    static boolean inSafeFloatRange(int i) {
        return i >= -INT_MAX_SAFE_FLOAT && i <= INT_MAX_SAFE_FLOAT;
    }

    static boolean inSafeIntegerRange(float f) {
        return f >= -FLOAT_MAX_SAFE_INTEGER && f <= FLOAT_MAX_SAFE_INTEGER;
    }

    static boolean inSafeFloatRange(long l) {
        return l >= -INT_MAX_SAFE_FLOAT && l <= INT_MAX_SAFE_FLOAT;
    }

    static boolean isNegativeZero(double d) {
        return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0d);
    }

    static boolean isNegativeZero(float f) {
        return Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0f);
    }

}
