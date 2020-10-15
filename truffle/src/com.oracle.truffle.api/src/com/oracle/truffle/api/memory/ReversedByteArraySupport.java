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
 * Proxies another {@link ByteArraySupport} implementation, reversing the order of accessed bytes.
 * In other terms, if the proxied implemention has little-endian order, wrapping it with
 * {@link ReversedByteArraySupport} allows reading and writing in big-endian order, and vice versa.
 */
final class ReversedByteArraySupport extends ByteArraySupport {
    final ByteArraySupport access;

    ReversedByteArraySupport(ByteArraySupport access) {
        this.access = access;
    }

    @Override
    public byte getByte(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return access.getByte(buffer, index);
    }

    @Override
    public void putByte(byte[] buffer, int index, byte value) throws IndexOutOfBoundsException {
        access.putByte(buffer, index, value);
    }

    @Override
    public short getShort(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Short.reverseBytes(access.getShort(buffer, index));
    }

    @Override
    public void putShort(byte[] buffer, int index, short value) throws IndexOutOfBoundsException {
        access.putShort(buffer, index, Short.reverseBytes(value));
    }

    @Override
    public int getInt(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Integer.reverseBytes(access.getInt(buffer, index));
    }

    @Override
    public void putInt(byte[] buffer, int index, int value) throws IndexOutOfBoundsException {
        access.putInt(buffer, index, Integer.reverseBytes(value));
    }

    @Override
    public long getLong(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Long.reverseBytes(access.getLong(buffer, index));
    }

    @Override
    public void putLong(byte[] buffer, int index, long value) throws IndexOutOfBoundsException {
        access.putLong(buffer, index, Long.reverseBytes(value));
    }

    @Override
    public float getFloat(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Float.intBitsToFloat(Integer.reverseBytes(access.getInt(buffer, index)));
    }

    @Override
    public void putFloat(byte[] buffer, int index, float value) throws IndexOutOfBoundsException {
        access.putInt(buffer, index, Integer.reverseBytes(Float.floatToIntBits(value)));
    }

    @Override
    public double getDouble(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return Double.longBitsToDouble(Long.reverseBytes(access.getLong(buffer, index)));
    }

    @Override
    public void putDouble(byte[] buffer, int index, double value) throws IndexOutOfBoundsException {
        access.putLong(buffer, index, Long.reverseBytes(Double.doubleToLongBits(value)));
    }
}
