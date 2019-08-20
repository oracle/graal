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

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

public class LLVM80BitFromBytesTest extends LLVM80BitTest {

    @Test
    public void testMinusOne() {
        assertEquals(LLVM80BitFloat.fromBytes(minusOne().getBytes()), minusOne());
    }

    @Test
    public void testOne() {
        assertEquals(LLVM80BitFloat.fromBytes(one().getBytes()), one());
    }

    @Test
    public void testTwo() {
        assertEquals(LLVM80BitFloat.fromBytes(val(2.0).getBytes()), val(2));
    }

    @Test
    public void testZero() {
        assertEquals(LLVM80BitFloat.fromBytes(zero().getBytes()), zero());
    }

    @Test
    public void testPositiveInfinity() {
        assertEquals(LLVM80BitFloat.fromBytes(positiveInfinity().getBytes()), positiveInfinity());
    }

    @Test
    public void testNegativeInfinity() {
        assertEquals(LLVM80BitFloat.fromBytes(negativeInfinity().getBytes()), negativeInfinity());
    }

    @Test
    public void testQNaN() {
        assertEquals(LLVM80BitFloat.fromBytes(nan().getBytes()), nan());
    }

    @Test
    public void testPositiveValue() {
        assertEquals(LLVM80BitFloat.fromBytes(val(Long.MAX_VALUE).getBytes()), val(Long.MAX_VALUE));
    }

    @Test
    public void testNegativeValue() {
        assertEquals(LLVM80BitFloat.fromBytes(val(Long.MIN_VALUE).getBytes()), val(Long.MIN_VALUE));
    }
}
