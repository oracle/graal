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

import java.io.*;

import com.sun.max.ide.*;
import com.sun.max.lang.*;

/**
 * Tests for {@link Endianness}.
 */

public class EndiannessTest extends MaxTestCase {

    public EndiannessTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(EndiannessTest.class);
    }

    //Tests for Endianness.LITTLE

    public void test_LITTLE_readShort() throws IOException {
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{127,  0})) == (short) 127);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{0, 127})) == (short) 32512);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{-128, 0})) == (short) 128);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{0, -128})) == (short) -32768);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{-127, 0})) == (short) 129);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{0, -127})) == (short) -32512);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{1, -1})) == (short) -255);
        assertTrue(Endianness.LITTLE.readShort(new ByteArrayInputStream(new byte[]{-1, 1})) == (short) 511);
    }

    public void test_LITTLE_readInt() throws IOException {
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{127, 0, 0, 0})) == 127);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, 127})) == 2130706432);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{-128, 0, 0, 0})) == 128);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, -128})) == -2147483648);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{-127, 0, 0, 0})) == 129);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, -127})) == -2130706432);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{1, 0, 0, -1})) == -16777215);
        assertTrue(Endianness.LITTLE.readInt(new ByteArrayInputStream(new byte[]{-1, 0, 0, 1})) == 16777471);
    }

    public void test_LITTLE_readLong() throws IOException {
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{127, 0, 0, 0, 0, 0, 0, 0})) == 127L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, 127})) == 9151314442816847872L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{-128, 0, 0, 0, 0, 0, 0, 0})) == 128L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, -128})) == -9223372036854775808L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{-127, 0, 0, 0, 0, 0, 0, 0})) == 129L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, -127})) == -9151314442816847872L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{1, 0, 0, 0, 0, 0, 0, -1})) == -72057594037927935L);
        assertTrue(Endianness.LITTLE.readLong(new ByteArrayInputStream(new byte[]{-1, 0, 0, 0, 0, 0, 0, 1})) == 72057594037928191L);
    }

    public void test_LITTLE_writeShort() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.LITTLE.writeShort(testStream, (short) 127);
        b = testStream.toByteArray();
        assertTrue(b[0] == 127);
        assertTrue(b[1] == 0);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) 32512);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 127);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) 128);
        b = testStream.toByteArray();
        assertTrue(b[0] == -128);
        assertTrue(b[1] == 0);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) -32768);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == -128);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) 129);
        b = testStream.toByteArray();
        assertTrue(b[0] == -127);
        assertTrue(b[1] == 0);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) -32512);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == -127);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) -255);
        b = testStream.toByteArray();
        assertTrue(b[0] == 1);
        assertTrue(b[1] == -1);
        testStream.reset();

        Endianness.LITTLE.writeShort(testStream, (short) 511);
        b = testStream.toByteArray();
        assertTrue(b[0] == -1);
        assertTrue(b[1] == 1);
        testStream.reset();
    }

    public void test_LITTLE_writeInt() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.LITTLE.writeInt(testStream, 127);
        b = testStream.toByteArray();
        assertTrue(b[0] == 127);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, 2130706432);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 127);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, 128);
        b = testStream.toByteArray();
        assertTrue(b[0] == -128);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, -2147483648);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == -128);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, 129);
        b = testStream.toByteArray();
        assertTrue(b[0] == -127);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, -2130706432);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == -127);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, -16777215);
        b = testStream.toByteArray();
        assertTrue(b[0] == 1);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == -1);
        testStream.reset();

        Endianness.LITTLE.writeInt(testStream, 16777471);
        b = testStream.toByteArray();
        assertTrue(b[0] == -1);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 1);
        testStream.reset();
    }

    public void test_LITTLE_writeLong() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.LITTLE.writeLong(testStream, 127L);
        b = testStream.toByteArray();
        assertTrue(b[0] == 127);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == 0);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, 9151314442816847872L);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == 127);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, 128L);
        b = testStream.toByteArray();
        assertTrue(b[0] == -128);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == 0);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, -9223372036854775808L);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == -128);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, 129L);
        b = testStream.toByteArray();
        assertTrue(b[0] == -127);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == 0);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, -9151314442816847872L);
        b = testStream.toByteArray();
        assertTrue(b[0] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == -127);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, -72057594037927935L);
        b = testStream.toByteArray();
        assertTrue(b[0] == 1);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == -1);
        testStream.reset();

        Endianness.LITTLE.writeLong(testStream, 72057594037928191L);
        b = testStream.toByteArray();
        assertTrue(b[0] == -1);
        assertTrue(b[1] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[7] == 1);
        testStream.reset();
    }

    //Tests for Endianness.BIG

    public void test_BIG_readShort() throws IOException {
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{0,  127})) == (short) 127);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{127, 0})) == (short) 32512);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{0, -128})) == (short) 128);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{-128, 0})) == (short) -32768);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{0, -127})) == (short) 129);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{-127, 0})) == (short) -32512);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{-1, 1})) == (short) -255);
        assertTrue(Endianness.BIG.readShort(new ByteArrayInputStream(new byte[]{1, -1})) == (short) 511);
    }

    public void test_BIG_readInt() throws IOException {
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, 127})) == 127);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{127, 0, 0, 0})) == 2130706432);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, -128})) == 128);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{-128, 0, 0, 0})) == -2147483648);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{0, 0, 0, -127})) == 129);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{-127, 0, 0, 0})) == -2130706432);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{-1, 0, 0, 1})) == -16777215);
        assertTrue(Endianness.BIG.readInt(new ByteArrayInputStream(new byte[]{1, 0, 0, -1})) == 16777471);
    }

    public void test_BIG_readLong() throws IOException {
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, 127})) == 127L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{127, 0, 0, 0, 0, 0, 0, 0})) == 9151314442816847872L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, -128})) == 128L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{-128, 0, 0, 0, 0, 0, 0, 0})) == -9223372036854775808L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 0, 0, 0, -127})) == 129L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{-127, 0, 0, 0, 0, 0, 0, 0})) == -9151314442816847872L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{-1, 0, 0, 0, 0, 0, 0, 1})) == -72057594037927935L);
        assertTrue(Endianness.BIG.readLong(new ByteArrayInputStream(new byte[]{1, 0, 0, 0, 0, 0, 0, -1})) == 72057594037928191L);
    }

    public void test_BIG_writeShort() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.BIG.writeShort(testStream, (short) 127);
        b = testStream.toByteArray();
        assertTrue(b[1] == 127);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) 32512);
        b = testStream.toByteArray();
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 127);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) 128);
        b = testStream.toByteArray();
        assertTrue(b[1] == -128);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) -32768);
        b = testStream.toByteArray();
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -128);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) 129);
        b = testStream.toByteArray();
        assertTrue(b[1] == -127);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) -32512);
        b = testStream.toByteArray();
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -127);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) -255);
        b = testStream.toByteArray();
        assertTrue(b[1] == 1);
        assertTrue(b[0] == -1);
        testStream.reset();

        Endianness.BIG.writeShort(testStream, (short) 511);
        b = testStream.toByteArray();
        assertTrue(b[1] == -1);
        assertTrue(b[0] == 1);
        testStream.reset();
    }

    public void test_BIG_writeInt() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.BIG.writeInt(testStream, 127);
        b = testStream.toByteArray();
        assertTrue(b[3] == 127);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, 2130706432);
        b = testStream.toByteArray();
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 127);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, 128);
        b = testStream.toByteArray();
        assertTrue(b[3] == -128);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, -2147483648);
        b = testStream.toByteArray();
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -128);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, 129);
        b = testStream.toByteArray();
        assertTrue(b[3] == -127);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, -2130706432);
        b = testStream.toByteArray();
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -127);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, -16777215);
        b = testStream.toByteArray();
        assertTrue(b[3] == 1);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -1);
        testStream.reset();

        Endianness.BIG.writeInt(testStream, 16777471);
        b = testStream.toByteArray();
        assertTrue(b[3] == -1);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 1);
        testStream.reset();
    }

    public void test_BIG_writeLong() throws IOException {
        final ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        byte[] b;

        Endianness.BIG.writeLong(testStream, 127L);
        b = testStream.toByteArray();
        assertTrue(b[7] == 127);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, 9151314442816847872L);
        b = testStream.toByteArray();
        assertTrue(b[7] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 127);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, 128L);
        b = testStream.toByteArray();
        assertTrue(b[7] == -128);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, -9223372036854775808L);
        b = testStream.toByteArray();
        assertTrue(b[7] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -128);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, 129L);
        b = testStream.toByteArray();
        assertTrue(b[7] == -127);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 0);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, -9151314442816847872L);
        b = testStream.toByteArray();
        assertTrue(b[7] == 0);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -127);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, -72057594037927935L);
        b = testStream.toByteArray();
        assertTrue(b[7] == 1);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == -1);
        testStream.reset();

        Endianness.BIG.writeLong(testStream, 72057594037928191L);
        b = testStream.toByteArray();
        assertTrue(b[7] == -1);
        assertTrue(b[6] == 0);
        assertTrue(b[5] == 0);
        assertTrue(b[4] == 0);
        assertTrue(b[3] == 0);
        assertTrue(b[2] == 0);
        assertTrue(b[1] == 0);
        assertTrue(b[0] == 1);
        testStream.reset();
    }
}
