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

public class LLVM80BitConstantsTest extends LLVM80BitTest {

    @Test
    public void testPositiveInt() {
        byte[] test = {0x40, 0x09, (byte) 0x9A, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertEquals(val(1234), LLVM80BitFloat.fromBytesBigEndian(test));
    }

    @Test
    public void testNegativeInt() {
        byte[] test = {(byte) 0xC0, 0x09, (byte) 0x9A, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertEquals(val(-1234), LLVM80BitFloat.fromBytesBigEndian(test));
    }

    @Test
    public void testZero() {
        byte[] test = new byte[10];
        assertEquals(zero(), LLVM80BitFloat.fromBytesBigEndian(test));
    }

    @Test
    public void testMinusZero() {
        byte[] test = {(byte) 0xBF, (byte) 0xFF, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertEquals(minusOne(), LLVM80BitFloat.fromBytesBigEndian(test));
    }

    @Test
    public void testPi() {
        byte[] test = {0x40, 0x00, (byte) 0xC9, 0x0F, (byte) 0xDA, (byte) 0xA2, 0x21, 0x68, (byte) 0xC2, 0x35};
        assertEquals(LLVM80BitFloat.fromRawValues(false, 0x4000, 0xc90fdaa22168c235L), LLVM80BitFloat.fromBytesBigEndian(test));
    }

    @Test
    public void testMinusOne() {
        byte[] test = {(byte) 0xBF, (byte) 0xFF, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertEquals(LLVM80BitFloat.fromRawValues(true, 0x3fff, 0x8000000000000000L), LLVM80BitFloat.fromBytesBigEndian(test));
    }
}
