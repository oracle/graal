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
 * Proxies another {@link ByteArraySupport} implementation, adding bounds checking to all accesses.
 */
final class CheckedByteArraySupport extends ByteArraySupport {
    final ByteArraySupport access;

    /**
     * @param access proxied {@link ByteArraySupport}
     */
    CheckedByteArraySupport(ByteArraySupport access) {
        this.access = access;
    }

    private void checkBounds(byte[] buffer, int startByteOffset, int length) {
        if (!inBounds(buffer, startByteOffset, length)) {
            throw new ByteArrayOutOfBoundsException();
        }
    }

    private void checkBounds(byte[] buffer, long startByteOffset, long length) {
        if (!inBounds(buffer, startByteOffset, length)) {
            throw new ByteArrayOutOfBoundsException();
        }
    }

    @Override
    public byte getByte(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getByte(buffer, byteOffset);
    }

    @Override
    public byte getByte(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getByte(buffer, byteOffset);
    }

    @Override
    public void putByte(byte[] buffer, int byteOffset, byte value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        access.putByte(buffer, byteOffset, value);
    }

    @Override
    public void putByte(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        access.putByte(buffer, byteOffset, value);
    }

    @Override
    public short getShort(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getShort(buffer, byteOffset);
    }

    @Override
    public short getShort(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getShort(buffer, byteOffset);
    }

    @Override
    public void putShort(byte[] buffer, int byteOffset, short value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        access.putShort(buffer, byteOffset, value);
    }

    @Override
    public void putShort(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        access.putShort(buffer, byteOffset, value);
    }

    @Override
    public int getInt(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getInt(buffer, byteOffset);
    }

    @Override
    public int getInt(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getInt(buffer, byteOffset);
    }

    @Override
    public void putInt(byte[] buffer, int byteOffset, int value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        access.putInt(buffer, byteOffset, value);
    }

    @Override
    public void putInt(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        access.putInt(buffer, byteOffset, value);
    }

    @Override
    public long getLong(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getLong(buffer, byteOffset);
    }

    @Override
    public long getLong(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getLong(buffer, byteOffset);
    }

    @Override
    public void putLong(byte[] buffer, int byteOffset, long value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        access.putLong(buffer, byteOffset, value);
    }

    @Override
    public void putLong(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        access.putLong(buffer, byteOffset, value);
    }

    @Override
    public float getFloat(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Float.BYTES);
        return access.getFloat(buffer, byteOffset);
    }

    @Override
    public float getFloat(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Float.BYTES);
        return access.getFloat(buffer, byteOffset);
    }

    @Override
    public void putFloat(byte[] buffer, int byteOffset, float value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Float.BYTES);
        access.putFloat(buffer, byteOffset, value);
    }

    @Override
    public void putFloat(byte[] buffer, long byteOffset, float value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Float.BYTES);
        access.putFloat(buffer, byteOffset, value);
    }

    @Override
    public double getDouble(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Double.BYTES);
        return access.getDouble(buffer, byteOffset);
    }

    @Override
    public double getDouble(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Double.BYTES);
        return access.getDouble(buffer, byteOffset);
    }

    @Override
    public void putDouble(byte[] buffer, int byteOffset, double value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Double.BYTES);
        access.putDouble(buffer, byteOffset, value);
    }

    @Override
    public void putDouble(byte[] buffer, long byteOffset, double value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Double.BYTES);
        access.putDouble(buffer, byteOffset, value);
    }

    @Override
    public byte getByteVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getByteVolatile(buffer, byteOffset);
    }

    @Override
    public void putByteVolatile(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        access.putByteVolatile(buffer, byteOffset, value);
    }

    @Override
    public short getShortVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getShortVolatile(buffer, byteOffset);
    }

    @Override
    public void putShortVolatile(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        access.putShortVolatile(buffer, byteOffset, value);
    }

    @Override
    public int getIntVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getIntVolatile(buffer, byteOffset);
    }

    @Override
    public void putIntVolatile(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        access.putIntVolatile(buffer, byteOffset, value);
    }

    @Override
    public long getLongVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getLongVolatile(buffer, byteOffset);
    }

    @Override
    public void putLongVolatile(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        access.putLongVolatile(buffer, byteOffset, value);
    }

    @Override
    public byte getAndAddByte(byte[] buffer, long byteOffset, byte delta) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getAndAddByte(buffer, byteOffset, delta);
    }

    @Override
    public short getAndAddShort(byte[] buffer, long byteOffset, short delta) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getAndAddShort(buffer, byteOffset, delta);
    }

    @Override
    public int getAndAddInt(byte[] buffer, long byteOffset, int delta) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getAndAddInt(buffer, byteOffset, delta);
    }

    @Override
    public long getAndAddLong(byte[] buffer, long byteOffset, long delta) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getAndAddLong(buffer, byteOffset, delta);
    }

    @Override
    public byte getAndBitwiseAndByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getAndBitwiseAndByte(buffer, byteOffset, mask);
    }

    @Override
    public short getAndBitwiseAndShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getAndBitwiseAndShort(buffer, byteOffset, mask);
    }

    @Override
    public int getAndBitwiseAndInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getAndBitwiseAndInt(buffer, byteOffset, mask);
    }

    @Override
    public long getAndBitwiseAndLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getAndBitwiseAndLong(buffer, byteOffset, mask);
    }

    @Override
    public byte getAndBitwiseOrByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getAndBitwiseOrByte(buffer, byteOffset, mask);
    }

    @Override
    public short getAndBitwiseOrShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getAndBitwiseOrShort(buffer, byteOffset, mask);
    }

    @Override
    public int getAndBitwiseOrInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getAndBitwiseOrInt(buffer, byteOffset, mask);
    }

    @Override
    public long getAndBitwiseOrLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getAndBitwiseOrLong(buffer, byteOffset, mask);
    }

    @Override
    public byte getAndBitwiseXorByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getAndBitwiseXorByte(buffer, byteOffset, mask);
    }

    @Override
    public short getAndBitwiseXorShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getAndBitwiseXorShort(buffer, byteOffset, mask);
    }

    @Override
    public int getAndBitwiseXorInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getAndBitwiseXorInt(buffer, byteOffset, mask);
    }

    @Override
    public long getAndBitwiseXorLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getAndBitwiseXorLong(buffer, byteOffset, mask);
    }

    @Override
    public byte getAndSetByte(byte[] buffer, long byteOffset, byte newValue) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.getAndSetByte(buffer, byteOffset, newValue);
    }

    @Override
    public short getAndSetShort(byte[] buffer, long byteOffset, short newValue) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.getAndSetShort(buffer, byteOffset, newValue);
    }

    @Override
    public int getAndSetInt(byte[] buffer, long byteOffset, int newValue) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.getAndSetInt(buffer, byteOffset, newValue);
    }

    @Override
    public long getAndSetLong(byte[] buffer, long byteOffset, long newValue) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.getAndSetLong(buffer, byteOffset, newValue);
    }

    @Override
    public byte compareAndExchangeByte(byte[] buffer, long byteOffset, byte expected, byte x) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Byte.BYTES);
        return access.compareAndExchangeByte(buffer, byteOffset, expected, x);
    }

    @Override
    public short compareAndExchangeShort(byte[] buffer, long byteOffset, short expected, short x) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Short.BYTES);
        return access.compareAndExchangeShort(buffer, byteOffset, expected, x);
    }

    @Override
    public int compareAndExchangeInt(byte[] buffer, long byteOffset, int expected, int x) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Integer.BYTES);
        return access.compareAndExchangeInt(buffer, byteOffset, expected, x);
    }

    @Override
    public long compareAndExchangeLong(byte[] buffer, long byteOffset, long expected, long x) throws IndexOutOfBoundsException {
        checkBounds(buffer, byteOffset, Long.BYTES);
        return access.compareAndExchangeLong(buffer, byteOffset, expected, x);
    }
}
