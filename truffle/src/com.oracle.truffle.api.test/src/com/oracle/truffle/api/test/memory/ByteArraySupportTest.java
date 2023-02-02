/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
