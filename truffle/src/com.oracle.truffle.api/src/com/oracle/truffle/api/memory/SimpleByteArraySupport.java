/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Implementation of {@link ByteArraySupport} by byteOffseting individual bytes.
 * <p>
 * Bytes ordering is big-endian.
 */
@SuppressWarnings("PointlessArithmeticExpression")
final class SimpleByteArraySupport extends ByteArraySupport {
    @Override
    public byte getByte(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return buffer[byteOffset];
    }

    @Override
    public void putByte(byte[] buffer, int byteOffset, byte value) throws IndexOutOfBoundsException {
        buffer[byteOffset] = value;
    }

    @Override
    public short getShort(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return (short) (((buffer[byteOffset] & 0xFF) << Byte.SIZE) |
                        (buffer[byteOffset + 1] & 0xFF));
    }

    @Override
    public void putShort(byte[] buffer, int byteOffset, short value) throws IndexOutOfBoundsException {
        buffer[byteOffset + 0] = (byte) (value >> Byte.SIZE);
        buffer[byteOffset + 1] = (byte) (value);
    }

    @Override
    public int getInt(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return ((buffer[byteOffset + 0] & 0xFF) << Byte.SIZE * 3) |
                        ((buffer[byteOffset + 1] & 0xFF) << Byte.SIZE * 2) |
                        ((buffer[byteOffset + 2] & 0xFF) << Byte.SIZE) |
                        ((buffer[byteOffset + 3] & 0xFF));
    }

    @Override
    public void putInt(byte[] buffer, int byteOffset, int value) throws IndexOutOfBoundsException {
        buffer[byteOffset + 0] = (byte) (value >> Byte.SIZE * 3);
        buffer[byteOffset + 1] = (byte) (value >> Byte.SIZE * 2);
        buffer[byteOffset + 2] = (byte) (value >> Byte.SIZE);
        buffer[byteOffset + 3] = (byte) (value);
    }

    @Override
    public long getLong(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return ((buffer[byteOffset + 0] & 0xFFL) << (Byte.SIZE * 7)) |
                        ((buffer[byteOffset + 1] & 0xFFL) << (Byte.SIZE * 6)) |
                        ((buffer[byteOffset + 2] & 0xFFL) << (Byte.SIZE * 5)) |
                        ((buffer[byteOffset + 3] & 0xFFL) << (Byte.SIZE * 4)) |
                        ((buffer[byteOffset + 4] & 0xFFL) << (Byte.SIZE * 3)) |
                        ((buffer[byteOffset + 5] & 0xFFL) << (Byte.SIZE * 2)) |
                        ((buffer[byteOffset + 6] & 0xFFL) << (Byte.SIZE)) |
                        ((buffer[byteOffset + 7] & 0xFFL));
    }

    @Override
    public void putLong(byte[] buffer, int byteOffset, long value) throws IndexOutOfBoundsException {
        buffer[byteOffset + 0] = (byte) (value >> (Byte.SIZE * 7));
        buffer[byteOffset + 1] = (byte) (value >> (Byte.SIZE * 6));
        buffer[byteOffset + 2] = (byte) (value >> (Byte.SIZE * 5));
        buffer[byteOffset + 3] = (byte) (value >> (Byte.SIZE * 4));
        buffer[byteOffset + 4] = (byte) (value >> (Byte.SIZE * 3));
        buffer[byteOffset + 5] = (byte) (value >> (Byte.SIZE * 2));
        buffer[byteOffset + 6] = (byte) (value >> (Byte.SIZE));
        buffer[byteOffset + 7] = (byte) (value);
    }

    @Override
    public float getFloat(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return Float.intBitsToFloat(getInt(buffer, byteOffset));
    }

    @Override
    public void putFloat(byte[] buffer, int byteOffset, float value) throws IndexOutOfBoundsException {
        putInt(buffer, byteOffset, Float.floatToRawIntBits(value));
    }

    @Override
    public double getDouble(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return Double.longBitsToDouble(getLong(buffer, byteOffset));
    }

    @Override
    public void putDouble(byte[] buffer, int byteOffset, double value) throws IndexOutOfBoundsException {
        putLong(buffer, byteOffset, Double.doubleToRawLongBits(value));
    }
}
