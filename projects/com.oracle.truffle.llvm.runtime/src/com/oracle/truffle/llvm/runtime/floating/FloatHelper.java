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

final class FloatHelper {

    static final int FLOAT_SIGN_POS = 31;

    static final int FLOAT_FRACTION_BIT_WIDTH = 23;

    static final float POSITIVE_ZERO = 0;

    static final float NEGATIVE_ZERO = -0.0f;

    static final float NaN = Float.NaN;

    static final float NEGATIVE_INFINITY = Float.NEGATIVE_INFINITY;

    static final float POSITIVE_INFINITY = Float.POSITIVE_INFINITY;

    static final int FRACTION_MASK = BinaryHelper.getBitMask(FloatHelper.FLOAT_FRACTION_BIT_WIDTH);

    static boolean isPositiveZero(float val) {
        return Float.floatToIntBits(POSITIVE_ZERO) == Float.floatToIntBits(val);
    }

    static boolean isNegativeZero(float val) {
        return Float.floatToIntBits(NEGATIVE_ZERO) == Float.floatToIntBits(val);
    }

    static boolean isPositiveInfinty(float val) {
        return val == Float.POSITIVE_INFINITY;
    }

    static boolean isNegativeInfinity(float val) {
        return val == Float.NEGATIVE_INFINITY;
    }

    static boolean isNaN(float val) {
        return Float.isNaN(val);
    }

    static int getUnbiasedExponent(float val) {
        return Math.getExponent(val);
    }
}
