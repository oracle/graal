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
}
