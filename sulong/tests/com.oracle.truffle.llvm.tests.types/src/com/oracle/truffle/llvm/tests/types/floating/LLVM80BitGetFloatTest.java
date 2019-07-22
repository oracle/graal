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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LLVM80BitGetFloatTest extends LLVM80BitTest {

    @Test
    public void testZero() {
        assertBitEquals(0f, zero().getFloatValue());
    }

    @Test
    public void testMinusZero() {
        assertBitEquals(-0.0f, minusZero().getFloatValue());
    }

    @Test
    public void testOne() {
        float val = one().getFloatValue();
        assertEquals(0b111111100000000000000000000000, Float.floatToRawIntBits(val));
    }

    @Test
    public void testValue() {
        float val = val(3.5).getFloatValue();
        assertBitEquals(3.5, val);
    }

    @Test
    public void testPositiveInfinity() {
        assertBitEquals(Float.POSITIVE_INFINITY, positiveInfinity().getFloatValue());
    }

    @Test
    public void testNegativeInfinity() {
        assertBitEquals(Float.NEGATIVE_INFINITY, negativeInfinity().getFloatValue());
    }

    @Test
    public void testQNaN() {
        assertBitEquals(Float.NaN, nan().getFloatValue());
    }
}
