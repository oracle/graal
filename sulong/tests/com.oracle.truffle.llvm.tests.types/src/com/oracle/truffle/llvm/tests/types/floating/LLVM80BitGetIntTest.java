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

import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

public class LLVM80BitGetIntTest extends LLVM80BitTest {

    private static final int NR_RANDOM_NUMBERS = 10000;

    @Test
    public void testPositiveInfinty() {
        int positiveInfity = positiveInfinity().getIntValue();
        assertEquals(-2147483648, positiveInfity);
    }

    @Test
    public void testNegativeInfinty() {
        int negativeInfinity = negativeInfinity().getIntValue();
        assertEquals(-2147483648, negativeInfinity);
    }

    @Test
    public void testQNAN() {
        int nan = nan().getIntValue();
        assertEquals(-2147483648, nan);
    }

    @Test
    public void testZero() {
        int zero = LLVM80BitFloat.fromInt(0).getIntValue();
        assertEquals(0, zero);
    }

    @Test
    public void testOne() {
        int one = LLVM80BitFloat.fromInt(1).getIntValue();
        assertEquals(1, one);
    }

    @Test
    public void testMinusOne() {
        int oneInt = LLVM80BitFloat.fromInt(-1).getIntValue();
        long oneLong = LLVM80BitFloat.fromInt(-1).getLongValue();
        assertEquals(-1, oneInt);
        assertEquals(-1, oneLong);
    }

    @Test
    public void testRandomInt() {
        for (int i = 0; i < NR_RANDOM_NUMBERS; i++) {
            int nextInt = ThreadLocalRandom.current().nextInt();
            assertEquals(nextInt, LLVM80BitFloat.fromInt(nextInt).getIntValue());
            assertEquals(nextInt, LLVM80BitFloat.fromInt(nextInt).getLongValue());
        }
    }
}
