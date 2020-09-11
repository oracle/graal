/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.memory;

/**
 * Implementation of {@link ByteArraySupport} by indexing individual bytes.
 * <p>
 * Bytes ordering is big-endian.
 */
@SuppressWarnings("PointlessArithmeticExpression")
final class SimpleByteArraySupport extends ByteArraySupport {
    @Override
    public byte getByte(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return buffer[index];
    }

    @Override
    public void putByte(byte[] buffer, int index, byte value) throws IndexOutOfBoundsException {
        buffer[index] = value;
    }

    @Override
    public short getShort(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return (short) (((buffer[index] & 0xFF) << Byte.SIZE) |
                        (buffer[index + 1] & 0xFF));
    }

    @Override
    public void putShort(byte[] buffer, int index, short value) throws IndexOutOfBoundsException {
        buffer[index + 0] = (byte) (value >> Byte.SIZE);
        buffer[index + 1] = (byte) (value);
    }

    @Override
    public int getInt(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return ((buffer[index + 0] & 0xFF) << Byte.SIZE * 3) |
                        ((buffer[index + 1] & 0xFF) << Byte.SIZE * 2) |
                        ((buffer[index + 2] & 0xFF) << Byte.SIZE) |
                        ((buffer[index + 3] & 0xFF));
    }

    @Override
    public void putInt(byte[] buffer, int index, int value) throws IndexOutOfBoundsException {
        buffer[index + 0] = (byte) (value >> Byte.SIZE * 3);
        buffer[index + 1] = (byte) (value >> Byte.SIZE * 2);
        buffer[index + 2] = (byte) (value >> Byte.SIZE);
        buffer[index + 3] = (byte) (value);
    }

    @Override
    public long getLong(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return ((buffer[index + 0] & 0xFFL) << (Byte.SIZE * 7)) |
                        ((buffer[index + 1] & 0xFFL) << (Byte.SIZE * 6)) |
                        ((buffer[index + 2] & 0xFFL) << (Byte.SIZE * 5)) |
                        ((buffer[index + 3] & 0xFFL) << (Byte.SIZE * 4)) |
                        ((buffer[index + 4] & 0xFFL) << (Byte.SIZE * 3)) |
                        ((buffer[index + 5] & 0xFFL) << (Byte.SIZE * 2)) |
                        ((buffer[index + 6] & 0xFFL) << (Byte.SIZE)) |
                        ((buffer[index + 7] & 0xFFL));
    }

    @Override
    public void putLong(byte[] buffer, int index, long value) throws IndexOutOfBoundsException {
        buffer[index + 0] = (byte) (value >> (Byte.SIZE * 7));
        buffer[index + 1] = (byte) (value >> (Byte.SIZE * 6));
        buffer[index + 2] = (byte) (value >> (Byte.SIZE * 5));
        buffer[index + 3] = (byte) (value >> (Byte.SIZE * 4));
        buffer[index + 4] = (byte) (value >> (Byte.SIZE * 3));
        buffer[index + 5] = (byte) (value >> (Byte.SIZE * 2));
        buffer[index + 6] = (byte) (value >> (Byte.SIZE));
        buffer[index + 7] = (byte) (value);
    }

    @Override
    public float getFloat(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Float.intBitsToFloat(getInt(buffer, index));
    }

    @Override
    public void putFloat(byte[] buffer, int index, float value) throws IndexOutOfBoundsException {
        putInt(buffer, index, Float.floatToRawIntBits(value));
    }

    @Override
    public double getDouble(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Double.longBitsToDouble(getLong(buffer, index));
    }

    @Override
    public void putDouble(byte[] buffer, int index, double value) throws IndexOutOfBoundsException {
        putLong(buffer, index, Double.doubleToRawLongBits(value));
    }
}
