/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.api.test.memory;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class ByteArraySupportTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void putByteBigEndian() {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, 0, (byte) 0x42);
        assertBytesEqual(buffer, "42");
    }

    @Test
    public void putByteBigEndianWithLongAddress() {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, 0L, (byte) 0x42);
        assertBytesEqual(buffer, "42");
    }

    @Test
    public void putByteLittleEndian() {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, 0, (byte) 0x42);
        assertBytesEqual(buffer, "42");
    }

    @Test
    public void putByteLittleEndianWithLongAddress() {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, 0L, (byte) 0x42);
        assertBytesEqual(buffer, "42");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, 1L, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, 1L, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteBigEndianOutOfBoundsNegative() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, -1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteBigEndianOutOfBoundsNegativeWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByte(buffer, -1L, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteLittleEndianOutOfBoundsNegative() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, -1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteLittleEndianOutOfBoundsNegativeWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByte(buffer, -1L, (byte) 1);
    }

    @Test
    public void getByteBigEndian() {
        byte[] buffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().getByte(buffer, 0));
    }

    @Test
    public void getByteBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().getByte(buffer, 0L));
    }

    @Test
    public void getByteLittleEndian() {
        byte[] buffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().getByte(buffer, 0));
    }

    @Test
    public void getByteLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().getByte(buffer, 0L));
    }

    @Test
    public void putShortBigEndian() {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().putShort(buffer, 0, (short) 0x4241);
        assertBytesEqual(buffer, "4241");
    }

    @Test
    public void putShortBigEndianWithLongAddress() {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().putShort(buffer, 0L, (short) 0x4241);
        assertBytesEqual(buffer, "4241");
    }

    @Test
    public void putShortLittleEndian() {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().putShort(buffer, 0, (short) 0x4241);
        assertBytesEqual(buffer, "4142");
    }

    @Test
    public void putShortLittleEndianWithLongAddress() {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().putShort(buffer, 0L, (short) 0x4241);
        assertBytesEqual(buffer, "4142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().putShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().putShort(buffer, 1L, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().putShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putShort(buffer, 1L, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortBigEndianOutOfBoundsNegative() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putShort(buffer, -1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortBigEndianOutOfBoundsNegativeWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putShort(buffer, -1L, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortLittleEndianOutOfBoundsNegative() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putShort(buffer, -1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortLittleEndianOutOfBoundsNegativeWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putShort(buffer, -1L, (short) 1);
    }

    @Test
    public void getShortBigEndian() {
        byte[] buffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().getShort(buffer, 0));
    }

    @Test
    public void getShortBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().getShort(buffer, 0L));
    }

    @Test
    public void getShortLittleEndian() {
        byte[] buffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().getShort(buffer, 0));
    }

    @Test
    public void getShortLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().getShort(buffer, 0L));
    }

    @Test
    public void intBigEndian() {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().putInt(buffer, 0, 0x42414039);
        assertBytesEqual(buffer, "42414039");
    }

    @Test
    public void intBigEndianWithLongAddress() {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().putInt(buffer, 0L, 0x42414039);
        assertBytesEqual(buffer, "42414039");
    }

    @Test
    public void intLittleEndian() {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().putInt(buffer, 0, 0x42414039);
        assertBytesEqual(buffer, "39404142");
    }

    @Test
    public void intLittleEndianWithLongAddress() {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().putInt(buffer, 0L, 0x42414039);
        assertBytesEqual(buffer, "39404142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void intBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().putInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void intBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().putInt(buffer, 1L, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void intLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().putInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void intLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().putInt(buffer, 1L, 1);
    }

    @Test
    public void getIntBigEndian() {
        byte[] buffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().getInt(buffer, 0));
    }

    @Test
    public void getIntBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().getInt(buffer, 0L));
    }

    @Test
    public void getIntLittleEndian() {
        byte[] buffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().getInt(buffer, 0));
    }

    @Test
    public void getIntLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().getInt(buffer, 0L));
    }

    @Test
    public void putLongBigEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putLong(buffer, 1, 0x4241403938373635L);
        assertBytesEqual(buffer, "00424140393837363500");
    }

    @Test
    public void putLongBigEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putLong(buffer, 1L, 0x4241403938373635L);
        assertBytesEqual(buffer, "00424140393837363500");
    }

    @Test
    public void putLongLittleEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putLong(buffer, 2, 0x4241403938373635L);
        assertBytesEqual(buffer, "00003536373839404142");
    }

    @Test
    public void putLongLittleEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putLong(buffer, 2L, 0x4241403938373635L);
        assertBytesEqual(buffer, "00003536373839404142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().putLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().putLong(buffer, 1L, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().putLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().putLong(buffer, 1L, 1);
    }

    @Test
    public void getLongBigEndian() {
        byte[] buffer = hexToBytes("004241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().getLong(buffer, 1));
    }

    @Test
    public void getLongBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("004241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().getLong(buffer, 1L));
    }

    @Test
    public void getLongLittleEndian() {
        byte[] buffer = hexToBytes("00003536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().getLong(buffer, 2));
    }

    @Test
    public void getLongLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("00003536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().getLong(buffer, 2L));
    }

    @Test
    public void putFloatBigEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putFloat(buffer, 3, Float.intBitsToFloat(0x42414039));
        assertBytesEqual(buffer, "00000042414039000000");
    }

    @Test
    public void putFloatBigEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putFloat(buffer, 3L, Float.intBitsToFloat(0x42414039));
        assertBytesEqual(buffer, "00000042414039000000");
    }

    @Test
    public void putFloatLittleEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putFloat(buffer, 2, Float.intBitsToFloat(0x42414039));
        assertBytesEqual(buffer, "00003940414200000000");
    }

    @Test
    public void putFloatLittleEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putFloat(buffer, 2L, Float.intBitsToFloat(0x42414039));
        assertBytesEqual(buffer, "00003940414200000000");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putFloatBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putFloat(buffer, 7, 1.7f);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putFloatBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putFloat(buffer, 7L, 1.7f);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putFloatLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putFloat(buffer, 9, 1.7f);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putFloatLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putFloat(buffer, 9L, 1.7f);
    }

    @Test
    public void getFloatBigEndian() {
        byte[] buffer = hexToBytes("42414039");
        Assert.assertEquals(Float.intBitsToFloat(0x42414039), ByteArraySupport.bigEndian().getFloat(buffer, 0), 0);
    }

    @Test
    public void getFloatBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("42414039");
        Assert.assertEquals(Float.intBitsToFloat(0x42414039), ByteArraySupport.bigEndian().getFloat(buffer, 0L), 0);
    }

    @Test
    public void getFloatLittleEndian() {
        byte[] buffer = hexToBytes("39404142");
        Assert.assertEquals(Float.intBitsToFloat(0x42414039), ByteArraySupport.littleEndian().getFloat(buffer, 0), 0);
    }

    @Test
    public void getFloatLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("39404142");
        Assert.assertEquals(Float.intBitsToFloat(0x42414039), ByteArraySupport.littleEndian().getFloat(buffer, 0L), 0);
    }

    @Test
    public void putDoubleBigEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putDouble(buffer, 1, Double.longBitsToDouble(0x4241403938373635L));
        assertBytesEqual(buffer, "00424140393837363500");
    }

    @Test
    public void putDoubleBigEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.bigEndian().putDouble(buffer, 1L, Double.longBitsToDouble(0x4241403938373635L));
        assertBytesEqual(buffer, "00424140393837363500");
    }

    @Test
    public void putDoubleLittleEndian() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putDouble(buffer, 2, Double.longBitsToDouble(0x4241403938373635L));
        assertBytesEqual(buffer, "00003536373839404142");
    }

    @Test
    public void putDoubleLittleEndianWithLongAddress() {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putDouble(buffer, 2L, Double.longBitsToDouble(0x4241403938373635L));
        assertBytesEqual(buffer, "00003536373839404142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putDoubleBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().putDouble(buffer, 1, 1.7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putDoubleBigEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().putDouble(buffer, 1L, 1.7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putDoubleLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putDouble(buffer, 3, 1.7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putDoubleLittleEndianOutOfBoundsWithLongAddress() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[10];
        ByteArraySupport.littleEndian().putDouble(buffer, 3L, 1.7);
    }

    @Test
    public void inBoundsNegativeLength() {
        Assert.assertFalse(ByteArraySupport.littleEndian().inBounds(new byte[5], 11, -5));
    }

    @Test
    public void inBoundsNegativeLengthWithLongAddress() {
        Assert.assertFalse(ByteArraySupport.littleEndian().inBounds(new byte[5], 11L, -5L));
    }

    @Test
    public void getDoubleBigEndian() {
        byte[] buffer = hexToBytes("004241403938373635");
        Assert.assertEquals(Double.longBitsToDouble(0x4241403938373635L), ByteArraySupport.bigEndian().getDouble(buffer, 1), 0);
    }

    @Test
    public void getDoubleBigEndianWithLongAddress() {
        byte[] buffer = hexToBytes("004241403938373635");
        Assert.assertEquals(Double.longBitsToDouble(0x4241403938373635L), ByteArraySupport.bigEndian().getDouble(buffer, 1L), 0);
    }

    @Test
    public void getDoubleLittleEndian() {
        byte[] buffer = hexToBytes("00003536373839404142");
        Assert.assertEquals(Double.longBitsToDouble(0x4241403938373635L), ByteArraySupport.littleEndian().getDouble(buffer, 2), 0);
    }

    @Test
    public void getDoubleLittleEndianWithLongAddress() {
        byte[] buffer = hexToBytes("00003536373839404142");
        Assert.assertEquals(Double.longBitsToDouble(0x4241403938373635L), ByteArraySupport.littleEndian().getDouble(buffer, 2L), 0);
    }

    @Test
    public void getByteVolatile() {
        byte[] bigEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test
    public void putByteVolatile() {
        byte[] bigEndianBuffer = new byte[1];
        ByteArraySupport.bigEndian().putByteVolatile(bigEndianBuffer, 0, (byte) 0x42);
        assertBytesEqual(bigEndianBuffer, "42");
        byte[] littleEndianBuffer = new byte[1];
        ByteArraySupport.littleEndian().putByteVolatile(littleEndianBuffer, 0, (byte) 0x42);
        assertBytesEqual(littleEndianBuffer, "42");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteVolatileBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().putByteVolatile(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putByteVolatileLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().putByteVolatile(buffer, 1, (byte) 1);
    }

    @Test
    public void getShortVolatile() {
        byte[] bigEndianBuffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test
    public void putShortVolatile() {
        byte[] bigEndianBuffer = new byte[2];
        ByteArraySupport.bigEndian().putShortVolatile(bigEndianBuffer, 0, (short) 0x4241);
        assertBytesEqual(bigEndianBuffer, "4241");
        byte[] littleEndianBuffer = new byte[2];
        ByteArraySupport.littleEndian().putShortVolatile(littleEndianBuffer, 0, (short) 0x4241);
        assertBytesEqual(littleEndianBuffer, "4142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortVolatileBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().putShortVolatile(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putShortVolatileLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().putShortVolatile(buffer, 1, (short) 1);
    }

    @Test
    public void getIntVolatile() {
        byte[] bigEndianBuffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test
    public void putIntVolatile() {
        byte[] bigEndianBuffer = new byte[4];
        ByteArraySupport.bigEndian().putIntVolatile(bigEndianBuffer, 0, 0x42414039);
        assertBytesEqual(bigEndianBuffer, "42414039");
        byte[] littleEndianBuffer = new byte[4];
        ByteArraySupport.littleEndian().putIntVolatile(littleEndianBuffer, 0, 0x42414039);
        assertBytesEqual(littleEndianBuffer, "39404142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putIntVolatileBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().putIntVolatile(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putIntVolatileLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().putIntVolatile(buffer, 1, 1);
    }

    @Test
    public void getLongVolatile() {
        byte[] bigEndianBuffer = hexToBytes("4241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("3536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test
    public void putLongVolatile() {
        byte[] bigEndianBuffer = new byte[8];
        ByteArraySupport.bigEndian().putLongVolatile(bigEndianBuffer, 0, 0x4241403938373635L);
        assertBytesEqual(bigEndianBuffer, "4241403938373635");
        byte[] littleEndianBuffer = new byte[8];
        ByteArraySupport.littleEndian().putLongVolatile(littleEndianBuffer, 0, 0x4241403938373635L);
        assertBytesEqual(littleEndianBuffer, "3536373839404142");
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongVolatileBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().putLongVolatile(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putLongVolatileLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().putLongVolatile(buffer, 1, 1);
    }

    @Test
    public void getAndAddByte() {
        byte[] bigEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().getAndAddByte(bigEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x43, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().getAndAddByte(littleEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x43, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().getAndAddByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().getAndAddByte(buffer, 1, (byte) 1);
    }

    @Test
    public void getAndAddShort() {
        byte[] bigEndianBuffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().getAndAddShort(bigEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x4242, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().getAndAddShort(littleEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x4242, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().getAndAddShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().getAndAddShort(buffer, 1, (short) 1);
    }

    @Test
    public void getAndAddInt() {
        byte[] bigEndianBuffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().getAndAddInt(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x4241403A, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().getAndAddInt(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x4241403A, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().getAndAddInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().getAndAddInt(buffer, 1, 1);
    }

    @Test
    public void getAndAddLong() {
        byte[] bigEndianBuffer = hexToBytes("4241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().getAndAddLong(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("3536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().getAndAddLong(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().getAndAddLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndAddLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().getAndAddLong(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseAndByte() {
        byte[] bigEndianBuffer = hexToBytes("7F");
        Assert.assertEquals(0x7F, ByteArraySupport.bigEndian().getAndBitwiseAndByte(bigEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x1, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("7F");
        Assert.assertEquals(0x7F, ByteArraySupport.littleEndian().getAndBitwiseAndByte(littleEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x1, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().getAndBitwiseAndByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().getAndBitwiseAndByte(buffer, 1, (byte) 1);
    }

    @Test
    public void getAndBitwiseAndShort() {
        byte[] bigEndianBuffer = hexToBytes("7FFF");
        Assert.assertEquals(0x7FFF, ByteArraySupport.bigEndian().getAndBitwiseAndShort(bigEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x1, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FF7F");
        Assert.assertEquals(0x7FFF, ByteArraySupport.littleEndian().getAndBitwiseAndShort(littleEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x1, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().getAndBitwiseAndShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().getAndBitwiseAndShort(buffer, 1, (short) 1);
    }

    @Test
    public void getAndBitwiseAndInt() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFFFF");
        Assert.assertEquals(0x7FFFFFFF, ByteArraySupport.bigEndian().getAndBitwiseAndInt(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x1, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FFFFFF7F");
        Assert.assertEquals(0x7FFFFFFF, ByteArraySupport.littleEndian().getAndBitwiseAndInt(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x1, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().getAndBitwiseAndInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().getAndBitwiseAndInt(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseAndLong() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFFFFFFFFFFFF");
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL, ByteArraySupport.bigEndian().getAndBitwiseAndLong(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x1L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FFFFFFFFFFFFFF7F");
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL, ByteArraySupport.littleEndian().getAndBitwiseAndLong(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x1L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().getAndBitwiseAndLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseAndLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().getAndBitwiseAndLong(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseOrByte() {
        byte[] bigEndianBuffer = hexToBytes("00");
        Assert.assertEquals(0x00, ByteArraySupport.bigEndian().getAndBitwiseOrByte(bigEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x01, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("00");
        Assert.assertEquals(0x00, ByteArraySupport.littleEndian().getAndBitwiseOrByte(littleEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x01, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().getAndBitwiseOrByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().getAndBitwiseOrByte(buffer, 1, (byte) 1);
    }

    @Test
    public void getAndBitwiseOrShort() {
        byte[] bigEndianBuffer = hexToBytes("7F00");
        Assert.assertEquals(0x7F00, ByteArraySupport.bigEndian().getAndBitwiseOrShort(bigEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x7F01, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("007F");
        Assert.assertEquals(0x7F00, ByteArraySupport.littleEndian().getAndBitwiseOrShort(littleEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x7F01, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().getAndBitwiseOrShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().getAndBitwiseOrShort(buffer, 1, (short) 1);
    }

    @Test
    public void getAndBitwiseOrInt() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFF00");
        Assert.assertEquals(0x7FFFFF00, ByteArraySupport.bigEndian().getAndBitwiseOrInt(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFF01, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("00FFFF7F");
        Assert.assertEquals(0x7FFFFF00, ByteArraySupport.littleEndian().getAndBitwiseOrInt(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFF01, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().getAndBitwiseOrInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().getAndBitwiseOrInt(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseOrLong() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFFFFFFFFFF00");
        Assert.assertEquals(0x7FFFFFFFFFFFFF00L, ByteArraySupport.bigEndian().getAndBitwiseOrLong(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFFFFFFFF01L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("00FFFFFFFFFFFF7F");
        Assert.assertEquals(0x7FFFFFFFFFFFFF00L, ByteArraySupport.littleEndian().getAndBitwiseOrLong(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFFFFFFFF01L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().getAndBitwiseOrLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseOrLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().getAndBitwiseOrLong(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseXorByte() {
        byte[] bigEndianBuffer = hexToBytes("7F");
        Assert.assertEquals(0x7F, ByteArraySupport.bigEndian().getAndBitwiseXorByte(bigEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x7E, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("7F");
        Assert.assertEquals(0x7F, ByteArraySupport.littleEndian().getAndBitwiseXorByte(littleEndianBuffer, 0, (byte) 1));
        Assert.assertEquals(0x7E, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().getAndBitwiseXorByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().getAndBitwiseXorByte(buffer, 1, (byte) 1);
    }

    @Test
    public void getAndBitwiseXorShort() {
        byte[] bigEndianBuffer = hexToBytes("7FFF");
        Assert.assertEquals(0x7FFF, ByteArraySupport.bigEndian().getAndBitwiseXorShort(bigEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x7FFE, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FF7F");
        Assert.assertEquals(0x7FFF, ByteArraySupport.littleEndian().getAndBitwiseXorShort(littleEndianBuffer, 0, (short) 1));
        Assert.assertEquals(0x7FFE, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().getAndBitwiseXorShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().getAndBitwiseXorShort(buffer, 1, (short) 1);
    }

    @Test
    public void getAndBitwiseXorInt() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFFFF");
        Assert.assertEquals(0x7FFFFFFF, ByteArraySupport.bigEndian().getAndBitwiseXorInt(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFE, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FFFFFF7F");
        Assert.assertEquals(0x7FFFFFFF, ByteArraySupport.littleEndian().getAndBitwiseXorInt(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFE, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().getAndBitwiseXorInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().getAndBitwiseXorInt(buffer, 1, 1);
    }

    @Test
    public void getAndBitwiseXorLong() {
        byte[] bigEndianBuffer = hexToBytes("7FFFFFFFFFFFFFFF");
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL, ByteArraySupport.bigEndian().getAndBitwiseXorLong(bigEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFFFFFFFFFEL, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("FFFFFFFFFFFFFF7F");
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL, ByteArraySupport.littleEndian().getAndBitwiseXorLong(littleEndianBuffer, 0, 1));
        Assert.assertEquals(0x7FFFFFFFFFFFFFFEL, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().getAndBitwiseXorLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndBitwiseXorLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().getAndBitwiseXorLong(buffer, 1, 1);
    }

    @Test
    public void getAndSetByte() {
        byte[] bigEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().getAndSetByte(bigEndianBuffer, 0, (byte) 0x43));
        Assert.assertEquals(0x43, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().getAndSetByte(littleEndianBuffer, 0, (byte) 0x43));
        Assert.assertEquals(0x43, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().getAndSetByte(buffer, 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().getAndSetByte(buffer, 1, (byte) 1);
    }

    @Test
    public void getAndSetShort() {
        byte[] bigEndianBuffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().getAndSetShort(bigEndianBuffer, 0, (short) 0x4242));
        Assert.assertEquals(0x4242, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().getAndSetShort(littleEndianBuffer, 0, (short) 0x4242));
        Assert.assertEquals(0x4242, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().getAndSetShort(buffer, 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().getAndSetShort(buffer, 1, (short) 1);
    }

    @Test
    public void getAndSetInt() {
        byte[] bigEndianBuffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().getAndSetInt(bigEndianBuffer, 0, 0x4241403A));
        Assert.assertEquals(0x4241403A, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().getAndSetInt(littleEndianBuffer, 0, 0x4241403A));
        Assert.assertEquals(0x4241403A, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().getAndSetInt(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().getAndSetInt(buffer, 1, 1);
    }

    @Test
    public void getAndSetLong() {
        byte[] bigEndianBuffer = hexToBytes("4241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().getAndSetLong(bigEndianBuffer, 0, 0x4241403938373636L));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("3536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().getAndSetLong(littleEndianBuffer, 0, 0x4241403938373636L));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().getAndSetLong(buffer, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getAndSetLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().getAndSetLong(buffer, 1, 1);
    }

    @Test
    public void compareAndExchangeByte() {
        byte[] bigEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.bigEndian().compareAndExchangeByte(bigEndianBuffer, 0, (byte) 0x42, (byte) 0x43));
        Assert.assertEquals(0x43, ByteArraySupport.bigEndian().getByteVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("42");
        Assert.assertEquals(0x42, ByteArraySupport.littleEndian().compareAndExchangeByte(littleEndianBuffer, 0, (byte) 0x42, (byte) 0x43));
        Assert.assertEquals(0x43, ByteArraySupport.littleEndian().getByteVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeByteBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.bigEndian().compareAndExchangeByte(buffer, 1, (byte) 1, (byte) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeByteLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[1];
        ByteArraySupport.littleEndian().compareAndExchangeByte(buffer, 1, (byte) 1, (byte) 1);
    }

    @Test
    public void compareAndExchangeShort() {
        byte[] bigEndianBuffer = hexToBytes("4241");
        Assert.assertEquals(0x4241, ByteArraySupport.bigEndian().compareAndExchangeShort(bigEndianBuffer, 0, (short) 0x4241, (short) 0x4242));
        Assert.assertEquals(0x4242, ByteArraySupport.bigEndian().getShortVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("4142");
        Assert.assertEquals(0x4241, ByteArraySupport.littleEndian().compareAndExchangeShort(littleEndianBuffer, 0, (short) 0x4241, (short) 0x4242));
        Assert.assertEquals(0x4242, ByteArraySupport.littleEndian().getShortVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeShortBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.bigEndian().compareAndExchangeShort(buffer, 1, (short) 1, (short) 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeShortLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[2];
        ByteArraySupport.littleEndian().compareAndExchangeShort(buffer, 1, (short) 1, (short) 1);
    }

    @Test
    public void compareAndExchangeInt() {
        byte[] bigEndianBuffer = hexToBytes("42414039");
        Assert.assertEquals(0x42414039, ByteArraySupport.bigEndian().compareAndExchangeInt(bigEndianBuffer, 0, 0x42414039, 0x4241403A));
        Assert.assertEquals(0x4241403A, ByteArraySupport.bigEndian().getIntVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("39404142");
        Assert.assertEquals(0x42414039, ByteArraySupport.littleEndian().compareAndExchangeInt(littleEndianBuffer, 0, 0x42414039, 0x4241403A));
        Assert.assertEquals(0x4241403A, ByteArraySupport.littleEndian().getIntVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeIntBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.bigEndian().compareAndExchangeInt(buffer, 1, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeIntLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[4];
        ByteArraySupport.littleEndian().compareAndExchangeInt(buffer, 1, 1, 1);
    }

    @Test
    public void compareAndExchangeLong() {
        byte[] bigEndianBuffer = hexToBytes("4241403938373635");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.bigEndian().compareAndExchangeLong(bigEndianBuffer, 0, 0x4241403938373635L, 0x4241403938373636L));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.bigEndian().getLongVolatile(bigEndianBuffer, 0));
        byte[] littleEndianBuffer = hexToBytes("3536373839404142");
        Assert.assertEquals(0x4241403938373635L, ByteArraySupport.littleEndian().compareAndExchangeLong(littleEndianBuffer, 0, 0x4241403938373635L, 0x4241403938373636L));
        Assert.assertEquals(0x4241403938373636L, ByteArraySupport.littleEndian().getLongVolatile(littleEndianBuffer, 0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeLongBigEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.bigEndian().compareAndExchangeLong(buffer, 1, 1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void compareAndExchangeLongLittleEndianOutOfBounds() throws IndexOutOfBoundsException {
        byte[] buffer = new byte[8];
        ByteArraySupport.littleEndian().compareAndExchangeLong(buffer, 1, 1, 1);
    }

    @Test
    public void incrementSharedCounter() throws InterruptedException {
        byte[] buffer = new byte[4];
        Runnable runnable = () -> {
            for (int i = 0; i < 10000000; i++) {
                ByteArraySupport.littleEndian().getAndAddInt(buffer, 0, 1);
            }
        };

        Thread thread1 = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        int finalValue = ByteArraySupport.littleEndian().getIntVolatile(buffer, 0);
        Assert.assertEquals(20000000, finalValue);
    }

    @Test
    public void incrementSharedCounterWithSpinLock() throws InterruptedException {
        // last byte is the lock
        byte[] buffer = new byte[5];
        Runnable runnable = () -> {
            for (int i = 0; i < 10000000; i++) {
                // acquire the spinlock
                while (ByteArraySupport.littleEndian().compareAndExchangeByte(buffer, 4, (byte) 0, (byte) 1) != 0) {
                    // spin
                }

                // critical section
                int oldValue = ByteArraySupport.littleEndian().getInt(buffer, 0);
                ByteArraySupport.littleEndian().putInt(buffer, 0, oldValue + 1);

                // release the spinlock
                ByteArraySupport.littleEndian().putByteVolatile(buffer, 4, (byte) 0);
            }
        };

        Thread thread1 = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        int finalValue = ByteArraySupport.littleEndian().getIntVolatile(buffer, 0);
        Assert.assertEquals(20000000, finalValue);
    }

    private static void assertBytesEqual(byte[] actual, String expected) {
        Assert.assertEquals(expected, bytesToHex(actual));
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
