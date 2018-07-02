/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.floating;

final class DoubleHelper {

    static final int DOUBLE_EXPONENT_BIAS = 1023;

    static final long DOUBLE_FRACTION_BIT_WIDTH = 52;

    static final int DOUBLE_SIGN_POS = 63;

    static final double POSITIVE_ZERO = 0;

    static final double NEGATIVE_ZERO = -0.0;

    static final double NaN = Double.NaN;

    static final double NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY;

    static final double POSITIVE_INFINITY = Double.POSITIVE_INFINITY;

    static final long FRACTION_MASK = BinaryHelper.getBitMask(DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH);

    static boolean isPositiveZero(double val) {
        return Double.doubleToLongBits(POSITIVE_ZERO) == Double.doubleToLongBits(val);
    }

    static boolean isNegativeZero(double val) {
        return Double.doubleToLongBits(NEGATIVE_ZERO) == Double.doubleToLongBits(val);
    }

    static boolean isPositiveInfinty(double val) {
        return val == Double.POSITIVE_INFINITY;
    }

    static boolean isNegativeInfinity(double val) {
        return val == Double.NEGATIVE_INFINITY;
    }

    static boolean isNaN(double val) {
        return Double.isNaN(val);
    }

    static int getUnbiasedExponent(double val) {
        return Math.getExponent(val);
    }
}
