/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.nodes.serial;

import java.nio.*;

import org.junit.*;

import com.oracle.truffle.api.nodes.serial.*;

public class VariableLengthIntBufferTest {

    private VariableLengthIntBuffer buf;

    @Before
    public void setUp() {
        buf = new VariableLengthIntBuffer(ByteBuffer.allocate(512));
    }

    @After
    public void tearDown() {
        buf = null;
    }

    @Test
    public void testPutNull() {
        buf.put(VariableLengthIntBuffer.NULL);
        assertBytes(0xFF);
    }

    @Test
    public void testPutByteCornerCase0() {
        buf.put(0x00); // 0
        assertBytes(0x00);
    }

    @Test
    public void testPutByteCornerCase1() {
        buf.put(0x7F); // 127
        assertBytes(0x7F);
    }

    @Test
    public void testPutByteCornerCase2() {
        buf.put(0x3FFF_FFFF);
        assertBytes(0xBF, 0xFF, 0xFF, 0xFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutByteCornerCase3() {
        buf.put(0x4000_0000); // out of encodeable
    }

    @Test
    public void testGetNull() {
        create(0xFF);
        assertGet(VariableLengthIntBuffer.NULL);
    }

    @Test
    public void testGetCornerCase0() {
        create(0x00);
        assertGet(0x00);
    }

    @Test
    public void testGetCornerCase1() {
        create(0x7F);
        assertGet(0x7F);
    }

    @Test
    public void testGetCornerCase2() {
        create(0xBF, 0xFF, 0xFF, 0xFF);
        assertGet(0x3FFF_FFFF);
    }

    @Test(expected = AssertionError.class)
    public void testGetCornerCase3() {
        create(0xFF, 0xFF, 0xFF, 0xFF);
        assertGet(0x0);
    }

    private void create(int... bytes) {
        byte[] convBytes = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            convBytes[i] = (byte) bytes[i];
        }
        buf = new VariableLengthIntBuffer(convBytes);
    }

    private void assertGet(int expected) {
        Assert.assertEquals(expected, buf.get());
    }

    private void assertBytes(int... expectedBytes) {
        byte[] actualBytes = buf.getBytes();
        Assert.assertEquals(expectedBytes.length, actualBytes.length);
        for (int i = 0; i < expectedBytes.length; i++) {
            Assert.assertTrue(actualBytes[i] == (byte) expectedBytes[i]);
        }
    }

}
