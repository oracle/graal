/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.test.parser;


import org.junit.Assert;
import org.junit.Test;

public class UtilityTests {

    @Test
    public void testUnsignedLEB128() {
        checkUnsignedLEB128(new byte[] {(byte)0x00}, 0);
        checkUnsignedLEB128(new byte[] {(byte)0x01}, 1);
        checkUnsignedLEB128(new byte[] {(byte)0x02}, 2);

        checkUnsignedLEB128(new byte[] {(byte)0x82, (byte)0x01}, 130);
        checkUnsignedLEB128(new byte[] {(byte)0x8A, (byte)0x02}, 266);
    }

    @Test
    public void testFloat32() {
        checkFloat32(new byte[] {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00}, 0.0f);
        checkFloat32(new byte[] {(byte)0xc3, (byte)0xf5, (byte)0x48, (byte)0x40}, 3.14f);
        checkFloat32(new byte[] {(byte)0x00, (byte)0x60, (byte)0xaa, (byte)0xc3}, -340.75f);
    }

    @Test
    public void testFloat64() {
        checkFloat64(new byte[] {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00}, 0.0);
        checkFloat64(new byte[] {(byte)0x94, (byte)0x87, (byte)0x85, (byte)0xda, (byte)0x92, (byte)0x3f, (byte)0x0f, (byte)0x41}, 255_986.3567);
        checkFloat64(new byte[] {(byte)0xd3, (byte)0xa4, (byte)0x14, (byte)0xa8, (byte)0xf0, (byte)0xa7, (byte)0x37, (byte)0xc1}, -1_550_320.656565);
    }

    private void checkUnsignedLEB128(byte[] data, int expectedValue) {
        TestStreamReader reader = new TestStreamReader(data);
        int result = reader.readUnsignedInt32();
        Assert.assertEquals(expectedValue, result);
    }

    private void checkFloat32(byte[] data, float expectedValue) {
        TestStreamReader reader = new TestStreamReader(data);
        float result = reader.readFloatAsInt32();
        Assert.assertEquals(expectedValue, result, 1e5);
    }

    private void checkFloat64(byte[] data, double expectedValue) {
        TestStreamReader reader = new TestStreamReader(data);
        double result = reader.readFloatAsInt64();
        Assert.assertEquals(expectedValue, result, 1e5);
    }

}
