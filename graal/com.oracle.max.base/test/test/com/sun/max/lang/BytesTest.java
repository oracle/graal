/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.lang;

import com.sun.max.ide.*;
import com.sun.max.lang.*;

/**
 * Tests for com.sun.max.util.Bytes.
 */
public class BytesTest extends MaxTestCase {

    public BytesTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(BytesTest.class);
    }

    public static byte[] makeByteArray(int length) {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 127);
        }
        return bytes;
    }

    public static final int TEST_LENGTH = 98;

    public void test_numberOfTrailingZeros() {
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; ++i) {
            final byte b = (byte) i;
            final int expected = b == 0 ? 8 : Integer.numberOfTrailingZeros(b);
            final int actual = Bytes.numberOfTrailingZeros(b);
            assertEquals(expected, actual);
        }
    }

    public void test_equals() {
        final byte[] bytes1 = makeByteArray(TEST_LENGTH);
        final byte[] bytes2 = makeByteArray(TEST_LENGTH);
        assertTrue(Bytes.equals(bytes1, bytes2));
        assertTrue(Bytes.equals(bytes1, bytes2, 0));
        assertTrue(Bytes.equals(bytes1, bytes2, 59));
        assertTrue(Bytes.equals(bytes1, bytes2, TEST_LENGTH));
        assertTrue(Bytes.equals(bytes1, 0, bytes2));
        bytes2[2] = 99;
        assertTrue(Bytes.equals(bytes1, bytes2, 0));
        assertTrue(Bytes.equals(bytes1, bytes2, 1));
        assertTrue(Bytes.equals(bytes1, bytes2, 2));
        assertFalse(Bytes.equals(bytes1, bytes2, 3));
        assertFalse(Bytes.equals(bytes1, bytes2, 4));
        final byte[] bytes3 = new byte[TEST_LENGTH - 10];
        for (int i = 0; i < bytes3.length; i++) {
            bytes3[i] = (byte) (i + 10);
        }
        assertFalse(Bytes.equals(bytes1, bytes3));
        assertFalse(Bytes.equals(bytes1, 0, bytes3));
        assertFalse(Bytes.equals(bytes1, 9, bytes3));
        assertTrue(Bytes.equals(bytes1, 10, bytes3));
        assertFalse(Bytes.equals(bytes1, 11, bytes3));
    }

    public void test_copy() {
        final byte[] bytes1 = makeByteArray(TEST_LENGTH);
        final byte[] bytes2 = new byte[TEST_LENGTH];
        final byte[] bytes3 = new byte[TEST_LENGTH];
        final byte[] bytes4 = new byte[TEST_LENGTH];
        Bytes.copy(bytes1, 0, bytes2, 0, TEST_LENGTH);
        for (int i = 0; i < TEST_LENGTH; i++) {
            assertTrue(bytes1[i] == bytes2[i]);
        }
        Bytes.copy(bytes1, TEST_LENGTH / 2, bytes3, TEST_LENGTH / 2, TEST_LENGTH / 4);
        for (int i = TEST_LENGTH / 2; i < TEST_LENGTH / 4; i++) {
            assertTrue(bytes1[i] == bytes3[i]);
        }
        Bytes.copy(bytes1, bytes4, TEST_LENGTH);
        for (int i = 0; i < TEST_LENGTH; i++) {
            assertTrue(bytes1[i] == bytes4[i]);
        }
    }

    public void test_copyAll() {
        final byte[] bytes1 = makeByteArray(TEST_LENGTH);
        final byte[] bytes2 = new byte[TEST_LENGTH];
        final byte[] bytes3 = new byte[TEST_LENGTH + 100];
        Bytes.copyAll(bytes1, bytes2);
        for (int i = 0; i < TEST_LENGTH; i++) {
            assertTrue(bytes1[i] == bytes2[i]);
        }
        Bytes.copyAll(bytes1, bytes3, 100);
        for (int i = 0; i < TEST_LENGTH; i++) {
            assertTrue(bytes1[i] == bytes3[i + 100]);
        }
    }

    public void test_getSection() {
        final byte[] bytes1 = makeByteArray(TEST_LENGTH);
        final byte[] bytes2 = Bytes.getSection(bytes1, 0, TEST_LENGTH);
        for (int i = 0; i < TEST_LENGTH; i++) {
            assertTrue(bytes1[i] == bytes2[i]);
        }
        final byte[] bytes3 = Bytes.getSection(bytes1, TEST_LENGTH / 8, 2 * TEST_LENGTH / 3);
        for (int i = 0; i < (13 * TEST_LENGTH) / 24; i++) {
            assertTrue(bytes1[i + TEST_LENGTH / 8] == bytes3[i]);
        }
    }

    public void test_toHexLiteral() {
        assertEquals(Bytes.toHexLiteral((byte) 0), "0x00");
        assertEquals(Bytes.toHexLiteral((byte) 15), "0x0F");
        assertEquals(Bytes.toHexLiteral(Byte.MAX_VALUE), "0x7F");
        assertEquals(Bytes.toHexLiteral(Byte.MIN_VALUE), "0x80");
        assertEquals(Bytes.toHexLiteral(makeByteArray(3)), "0x000102");
    }

}
