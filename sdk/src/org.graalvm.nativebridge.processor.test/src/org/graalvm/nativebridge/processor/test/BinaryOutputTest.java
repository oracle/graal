/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test;

import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class BinaryOutputTest {

    private static final Random RANDOM = new Random();

    @Test
    public void testWriteBoolean() {
        testWriteBooleanImpl(true);
        testWriteBooleanImpl(false);
    }

    private static void testWriteBooleanImpl(boolean expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeBoolean(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        boolean actual = input.readBoolean();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteByte() {
        testWriteByteImpl(Byte.MIN_VALUE);
        testWriteByteImpl(Byte.MAX_VALUE);
        testWriteByteImpl((byte) 0);
        testWriteByteImpl((byte) 42);
    }

    private static void testWriteByteImpl(byte expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeByte(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        byte actual = input.readByte();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteShort() {
        testWriteShortImpl(Short.MIN_VALUE);
        testWriteShortImpl(Short.MAX_VALUE);
        testWriteShortImpl((short) 0);
        testWriteShortImpl((short) 42);
        testWriteShortImpl((short) 1024);
    }

    private static void testWriteShortImpl(short expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeShort(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        short actual = input.readShort();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteChar() {
        testWriteCharImpl(Character.MIN_VALUE);
        testWriteCharImpl(Character.MAX_VALUE);
        testWriteCharImpl((char) 0);
        testWriteCharImpl((char) 42);
        testWriteCharImpl((char) 1024);
    }

    private static void testWriteCharImpl(char expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeChar(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        char actual = input.readChar();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteInt() {
        testWriteIntImpl(Integer.MIN_VALUE);
        testWriteIntImpl(Integer.MAX_VALUE);
        testWriteIntImpl(0);
        testWriteIntImpl(42);
        testWriteIntImpl(70_000);
    }

    private static void testWriteIntImpl(int expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeInt(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        int actual = input.readInt();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteLong() {
        testWriteLongImpl(Long.MIN_VALUE);
        testWriteLongImpl(Long.MAX_VALUE);
        testWriteLongImpl(0);
        testWriteLongImpl(42);
        testWriteLongImpl(4_294_967_297L);
    }

    private static void testWriteLongImpl(long expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeLong(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        long actual = input.readLong();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteFloat() {
        testWriteFloatImpl(Float.MIN_VALUE);
        testWriteFloatImpl(Float.MAX_VALUE);
        testWriteFloatImpl(Float.NEGATIVE_INFINITY);
        testWriteFloatImpl(Float.POSITIVE_INFINITY);
        testWriteFloatImpl(Float.NaN);
        testWriteFloatImpl(0);
        testWriteFloatImpl(42.42f);
    }

    private static void testWriteFloatImpl(float expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeFloat(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        float actual = input.readFloat();
        if (Float.isNaN(expected)) {
            Assert.assertTrue(Float.isNaN(actual));
        } else {
            Assert.assertEquals(expected, actual, 0);
        }
    }

    @Test
    public void testWriteDouble() {
        testWriteDoubleImpl(Double.MIN_VALUE);
        testWriteDoubleImpl(Double.MAX_VALUE);
        testWriteDoubleImpl(Double.NEGATIVE_INFINITY);
        testWriteDoubleImpl(Double.POSITIVE_INFINITY);
        testWriteDoubleImpl(Double.NaN);
        testWriteDoubleImpl(0);
        testWriteDoubleImpl(42.42f);
    }

    private static void testWriteDoubleImpl(double expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeDouble(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        double actual = input.readDouble();
        if (Double.isNaN(expected)) {
            Assert.assertTrue(Double.isNaN(actual));
        } else {
            Assert.assertEquals(expected, actual, 0);
        }
    }

    @Test
    public void testWriteUTF() {
        testWriteUTFImpl("");
        testWriteUTFImpl("1");
        testWriteUTFImpl("Short string");
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 1 << 20; i++) {
            stringBuilder.append('a' + RANDOM.nextInt(26));
        }
        testWriteUTFImpl(stringBuilder.toString());
    }

    private static void testWriteUTFImpl(String expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeUTF(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        String actual = input.readUTF();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testWriteTypedValue() {
        testWriteTypedValueImpl(null);
        testWriteTypedValueImpl(true);
        testWriteTypedValueImpl("test");
        testWriteTypedValueImpl((byte) 42);
        testWriteTypedValueImpl((short) 42);
        testWriteTypedValueImpl((char) 42);
        testWriteTypedValueImpl(42);
        testWriteTypedValueImpl(42L);
        testWriteTypedValueImpl(42f);
        testWriteTypedValueImpl(42.0);
        testWriteTypedValueImpl(new Object[0]);
        testWriteTypedValueImpl(new Object[]{
                        null, true, "test", (byte) 42, (short) 42, (char) 42,
                        42, 42L, 42f, 42.0
        });
        testWriteTypedValueImpl(new Object[]{
                        new Object[0],
                        new Object[]{true},
                        new Object[]{"test"}
        });
    }

    private static void testWriteTypedValueImpl(Object expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeTypedValue(expected);
        BinaryInput input = BinaryInput.create(output.getArray());
        Object actual = input.readTypedValue();
        if ((expected instanceof Object[]) && (actual instanceof Object[])) {
            Assert.assertArrayEquals((Object[]) expected, (Object[]) actual);
        } else {
            Assert.assertEquals(expected, actual);
        }
    }

    @Test
    public void testWriteBooleanArray() {
        testWriteBooleanArrayImpl(new boolean[0]);
        testWriteBooleanArrayImpl(new boolean[]{true});
        testWriteBooleanArrayImpl(new boolean[]{false, true});
        boolean[] largeArray = new boolean[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = RANDOM.nextBoolean();
        }
        testWriteBooleanArrayImpl(largeArray);
    }

    private static void testWriteBooleanArrayImpl(boolean[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        boolean[] actual = new boolean[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteByteArray() {
        testWriteByteArrayImpl(new byte[0]);
        testWriteByteArrayImpl(new byte[]{0});
        testWriteByteArrayImpl(new byte[]{Byte.MIN_VALUE, Byte.MAX_VALUE});
        byte[] largeArray = new byte[1 << 20];
        RANDOM.nextBytes(largeArray);
        testWriteByteArrayImpl(largeArray);
    }

    private static void testWriteByteArrayImpl(byte[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        byte[] actual = new byte[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteShortArray() {
        testWriteShortArrayImpl(new short[0]);
        testWriteShortArrayImpl(new short[]{0});
        testWriteShortArrayImpl(new short[]{Short.MIN_VALUE, Short.MAX_VALUE});
        short[] largeArray = new short[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = (short) RANDOM.nextInt(Short.MAX_VALUE);
        }
        testWriteShortArrayImpl(largeArray);
    }

    private static void testWriteShortArrayImpl(short[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        short[] actual = new short[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteCharArray() {
        testWriteCharArrayImpl(new char[0]);
        testWriteCharArrayImpl(new char[]{0});
        testWriteCharArrayImpl(new char[]{Character.MIN_VALUE, Character.MAX_VALUE});
        char[] largeArray = new char[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = (char) RANDOM.nextInt(Character.MAX_VALUE);
        }
        testWriteCharArrayImpl(largeArray);
    }

    private static void testWriteCharArrayImpl(char[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        char[] actual = new char[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteIntArray() {
        testWriteIntArrayImpl(new int[0]);
        testWriteIntArrayImpl(new int[]{0});
        testWriteIntArrayImpl(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
        int[] largeArray = new int[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = RANDOM.nextInt();
        }
        testWriteIntArrayImpl(largeArray);
    }

    private static void testWriteIntArrayImpl(int[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        int[] actual = new int[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteLongArray() {
        testWriteLongArrayImpl(new long[0]);
        testWriteLongArrayImpl(new long[]{0});
        testWriteLongArrayImpl(new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        long[] largeArray = new long[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = RANDOM.nextLong();
        }
        testWriteLongArrayImpl(largeArray);
    }

    private static void testWriteLongArrayImpl(long[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        long[] actual = new long[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWriteFloatArray() {
        testWriteFloatArrayImpl(new float[0]);
        testWriteFloatArrayImpl(new float[]{0});
        testWriteFloatArrayImpl(new float[]{Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY,
                        Float.POSITIVE_INFINITY, 0, 1});
        float[] largeArray = new float[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = RANDOM.nextFloat();
        }
        testWriteFloatArrayImpl(largeArray);
    }

    private static void testWriteFloatArrayImpl(float[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        float[] actual = new float[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testWriteDoubleArray() {
        testWriteDoubleArrayImpl(new double[0]);
        testWriteDoubleArrayImpl(new double[]{0});
        testWriteDoubleArrayImpl(new double[]{Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 0, 1});
        double[] largeArray = new double[1 << 20];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = RANDOM.nextDouble();
        }
        testWriteDoubleArrayImpl(largeArray);
    }

    private static void testWriteDoubleArrayImpl(double[] expected) {
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.write(expected, 0, expected.length);
        BinaryInput input = BinaryInput.create(output.getArray());
        double[] actual = new double[expected.length];
        input.read(actual, 0, actual.length);
        Assert.assertArrayEquals(expected, actual, 0);
    }
}
