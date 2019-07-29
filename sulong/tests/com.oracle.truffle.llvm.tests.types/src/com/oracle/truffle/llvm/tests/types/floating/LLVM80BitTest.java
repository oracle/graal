/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.types.floating;

import org.junit.Assert;

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

public abstract class LLVM80BitTest {

    protected static LLVM80BitFloat val(int val) {
        return LLVM80BitFloat.fromInt(val);
    }

    protected static LLVM80BitFloat val(double val) {
        return LLVM80BitFloat.fromDouble(val);
    }

    protected static LLVM80BitFloat one() {
        return LLVM80BitFloat.fromInt(1);
    }

    protected static LLVM80BitFloat zero() {
        return LLVM80BitFloat.fromInt(0);
    }

    protected static LLVM80BitFloat minusZero() {
        return LLVM80BitFloat.fromDouble(-0.0);
    }

    protected static LLVM80BitFloat minusOne() {
        return LLVM80BitFloat.fromInt(-1);
    }

    protected static LLVM80BitFloat negativeInfinity() {
        return new LLVM80BitFloat(true, LLVM80BitFloat.ALL_ONE_EXPONENT, LLVM80BitFloat.bit(63L));
    }

    protected static LLVM80BitFloat positiveInfinity() {
        return new LLVM80BitFloat(false, LLVM80BitFloat.ALL_ONE_EXPONENT, LLVM80BitFloat.bit(63L));
    }

    protected static LLVM80BitFloat nan() {
        return LLVM80BitFloat.fromRawValues(false, 0b111111111111111, 1L << 62);
    }

    protected static void assertBitEquals(double expected, double actual) {
        Assert.assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    protected static void assertBitEquals(float expected, float actual) {
        Assert.assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
    }
}
