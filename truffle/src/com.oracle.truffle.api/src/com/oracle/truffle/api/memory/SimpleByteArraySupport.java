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

package com.oracle.truffle.api.memory;

/**
 * Implementation of {@link ByteArraySupport} by byteOffseting individual bytes.
 * <p>
 * Bytes ordering is big-endian.
 */
@SuppressWarnings("PointlessArithmeticExpression")
final class SimpleByteArraySupport extends ByteArraySupport {
    private final SimpleByteArraySupportLock lock = new SimpleByteArraySupportLock();

    @Override
    public byte getByte(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return buffer[byteOffset];
    }

    @Override
    public byte getByte(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getByte(buffer, (int) byteOffset);
    }

    @Override
    public void putByte(byte[] buffer, int byteOffset, byte value) throws IndexOutOfBoundsException {
        buffer[byteOffset] = value;
    }

    @Override
    public void putByte(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putByte(buffer, (int) byteOffset, value);
    }

    @Override
    public short getShort(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return (short) (((buffer[byteOffset] & 0xFF) << Byte.SIZE) |
                        (buffer[byteOffset + 1] & 0xFF));
    }

    @Override
    public short getShort(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getShort(buffer, (int) byteOffset);
    }

    @Override
    public void putShort(byte[] buffer, int byteOffset, short value) throws IndexOutOfBoundsException {
        buffer[byteOffset + 0] = (byte) (value >> Byte.SIZE);
        buffer[byteOffset + 1] = (byte) (value);
    }

    @Override
    public void putShort(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putShort(buffer, (int) byteOffset, value);
    }

    @Override
    public int getInt(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return ((buffer[byteOffset + 0] & 0xFF) << Byte.SIZE * 3) |
                        ((buffer[byteOffset + 1] & 0xFF) << Byte.SIZE * 2) |
                        ((buffer[byteOffset + 2] & 0xFF) << Byte.SIZE) |
                        ((buffer[byteOffset + 3] & 0xFF));
    }

    @Override
    public int getInt(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getInt(buffer, (int) byteOffset);
    }

    @Override
    public void putInt(byte[] buffer, int byteOffset, int value) throws IndexOutOfBoundsException {
        buffer[byteOffset + 0] = (byte) (value >> Byte.SIZE * 3);
        buffer[byteOffset + 1] = (byte) (value >> Byte.SIZE * 2);
        buffer[byteOffset + 2] = (byte) (value >> Byte.SIZE);
        buffer[byteOffset + 3] = (byte) (value);
    }

    @Override
    public void putInt(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putInt(buffer, (int) byteOffset, value);
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
    public long getLong(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getLong(buffer, (int) byteOffset);
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
    public void putLong(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putLong(buffer, (int) byteOffset, value);
    }

    @Override
    public float getFloat(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return Float.intBitsToFloat(getInt(buffer, byteOffset));
    }

    @Override
    public float getFloat(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getFloat(buffer, (int) byteOffset);
    }

    @Override
    public void putFloat(byte[] buffer, int byteOffset, float value) throws IndexOutOfBoundsException {
        putInt(buffer, byteOffset, Float.floatToRawIntBits(value));
    }

    @Override
    public void putFloat(byte[] buffer, long byteOffset, float value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putFloat(buffer, (int) byteOffset, value);
    }

    @Override
    public double getDouble(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return Double.longBitsToDouble(getLong(buffer, byteOffset));
    }

    @Override
    public double getDouble(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        return getDouble(buffer, (int) byteOffset);
    }

    @Override
    public void putDouble(byte[] buffer, int byteOffset, double value) throws IndexOutOfBoundsException {
        putLong(buffer, byteOffset, Double.doubleToRawLongBits(value));
    }

    @Override
    public void putDouble(byte[] buffer, long byteOffset, double value) throws IndexOutOfBoundsException {
        assert byteOffset < Integer.MAX_VALUE;
        putDouble(buffer, (int) byteOffset, value);
    }

    @Override
    public short getShortUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getShort(buffer, byteOffset);
    }

    @Override
    public short getShortUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return getShort(buffer, byteOffset);
    }

    @Override
    public int getIntUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getInt(buffer, byteOffset);
    }

    @Override
    public int getIntUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return getInt(buffer, byteOffset);
    }

    @Override
    public long getLongUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getLong(buffer, byteOffset);
    }

    @Override
    public long getLongUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return getLong(buffer, byteOffset);
    }

    @Override
    public byte getByteVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        synchronized (lock) {
            return getByte(buffer, byteOffset);
        }
    }

    @Override
    public void putByteVolatile(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        synchronized (lock) {
            putByte(buffer, byteOffset, value);
        }
    }

    @Override
    public short getShortVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        synchronized (lock) {
            return getShort(buffer, byteOffset);
        }
    }

    @Override
    public void putShortVolatile(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        synchronized (lock) {
            putShort(buffer, byteOffset, value);
        }
    }

    @Override
    public int getIntVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        synchronized (lock) {
            return getInt(buffer, byteOffset);
        }
    }

    @Override
    public void putIntVolatile(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        synchronized (lock) {
            putInt(buffer, byteOffset, value);
        }
    }

    @Override
    public long getLongVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        synchronized (lock) {
            return getLong(buffer, byteOffset);
        }
    }

    @Override
    public void putLongVolatile(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        synchronized (lock) {
            putLong(buffer, byteOffset, value);
        }
    }

    @Override
    public byte getAndAddByte(byte[] buffer, long byteOffset, byte delta) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            putByte(buffer, byteOffset, (byte) (v + delta));
            return v;
        }
    }

    @Override
    public short getAndAddShort(byte[] buffer, long byteOffset, short delta) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            putShort(buffer, byteOffset, (short) (v + delta));
            return v;
        }
    }

    @Override
    public int getAndAddInt(byte[] buffer, long byteOffset, int delta) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            putInt(buffer, byteOffset, v + delta);
            return v;
        }
    }

    @Override
    public long getAndAddLong(byte[] buffer, long byteOffset, long delta) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            putLong(buffer, byteOffset, v + delta);
            return v;
        }
    }

    @Override
    public byte getAndBitwiseAndByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            putByte(buffer, byteOffset, (byte) (v & mask));
            return v;
        }
    }

    @Override
    public short getAndBitwiseAndShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            putShort(buffer, byteOffset, (short) (v & mask));
            return v;
        }
    }

    @Override
    public int getAndBitwiseAndInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            putInt(buffer, byteOffset, v & mask);
            return v;
        }
    }

    @Override
    public long getAndBitwiseAndLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            putLong(buffer, byteOffset, v & mask);
            return v;
        }
    }

    @Override
    public byte getAndBitwiseOrByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            putByte(buffer, byteOffset, (byte) (v | mask));
            return v;
        }
    }

    @Override
    public short getAndBitwiseOrShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            putShort(buffer, byteOffset, (short) (v | mask));
            return v;
        }
    }

    @Override
    public int getAndBitwiseOrInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            putInt(buffer, byteOffset, v | mask);
            return v;
        }
    }

    @Override
    public long getAndBitwiseOrLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            putLong(buffer, byteOffset, v | mask);
            return v;
        }
    }

    @Override
    public byte getAndBitwiseXorByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            putByte(buffer, byteOffset, (byte) (v ^ mask));
            return v;
        }
    }

    @Override
    public short getAndBitwiseXorShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            putShort(buffer, byteOffset, (short) (v ^ mask));
            return v;
        }
    }

    @Override
    public int getAndBitwiseXorInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            putInt(buffer, byteOffset, v ^ mask);
            return v;
        }
    }

    @Override
    public long getAndBitwiseXorLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            putLong(buffer, byteOffset, v ^ mask);
            return v;
        }
    }

    @Override
    public byte getAndSetByte(byte[] buffer, long byteOffset, byte newValue) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            putByte(buffer, byteOffset, newValue);
            return v;
        }
    }

    @Override
    public short getAndSetShort(byte[] buffer, long byteOffset, short newValue) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            putShort(buffer, byteOffset, newValue);
            return v;
        }
    }

    @Override
    public int getAndSetInt(byte[] buffer, long byteOffset, int newValue) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            putInt(buffer, byteOffset, newValue);
            return v;
        }
    }

    @Override
    public long getAndSetLong(byte[] buffer, long byteOffset, long newValue) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            putLong(buffer, byteOffset, newValue);
            return v;
        }
    }

    @Override
    public byte compareAndExchangeByte(byte[] buffer, long byteOffset, byte expected, byte x) throws IndexOutOfBoundsException {
        synchronized (lock) {
            byte v = getByte(buffer, byteOffset);
            if (v == expected) {
                putByte(buffer, byteOffset, x);
            }
            return v;
        }
    }

    @Override
    public short compareAndExchangeShort(byte[] buffer, long byteOffset, short expected, short x) throws IndexOutOfBoundsException {
        synchronized (lock) {
            short v = getShort(buffer, byteOffset);
            if (v == expected) {
                putShort(buffer, byteOffset, x);
            }
            return v;
        }
    }

    @Override
    public int compareAndExchangeInt(byte[] buffer, long byteOffset, int expected, int x) throws IndexOutOfBoundsException {
        synchronized (lock) {
            int v = getInt(buffer, byteOffset);
            if (v == expected) {
                putInt(buffer, byteOffset, x);
            }
            return v;
        }
    }

    @Override
    public long compareAndExchangeLong(byte[] buffer, long byteOffset, long expected, long x) throws IndexOutOfBoundsException {
        synchronized (lock) {
            long v = getLong(buffer, byteOffset);
            if (v == expected) {
                putLong(buffer, byteOffset, x);
            }
            return v;
        }
    }
}
