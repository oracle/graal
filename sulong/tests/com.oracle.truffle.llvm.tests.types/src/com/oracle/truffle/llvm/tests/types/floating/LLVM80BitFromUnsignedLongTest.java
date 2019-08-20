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

public class LLVM80BitFromUnsignedLongTest extends LLVM80BitTest {

    @Test
    public void testZero() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(0);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0, 0);
        assertEquals(expected, val);
    }

    @Test
    public void testMinusOne() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(-1);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0x403e, 0xffffffffffffffffL);
        assertEquals(expected, val);
    }

    @Test
    public void testMinusTwo() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(-2);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0x403e, 0xfffffffffffffffeL);
        assertEquals(expected, val);
    }

    @Test
    public void testHighNegative() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(1L << 63);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0x403e, 0x8000000000000000L);
        assertEquals(expected, val);
    }

    @Test
    public void testMinValue() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(Long.MIN_VALUE);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0x403e, 0x8000000000000000L);
        assertEquals(expected, val);
    }

    @Test
    public void testMaxValue() {
        LLVM80BitFloat val = LLVM80BitFloat.fromUnsignedLong(Long.MAX_VALUE);
        LLVM80BitFloat expected = LLVM80BitFloat.fromRawValues(false, 0x403d, 0xfffffffffffffffeL);
        assertEquals(expected, val);
    }
}
