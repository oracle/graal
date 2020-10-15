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

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Implementation of {@link ByteArraySupport} using {@link Unsafe}.
 * <p>
 * Bytes ordering is native endianness ({@link ByteOrder#nativeOrder}).
 */
final class UnsafeByteArraySupport extends ByteArraySupport {
    private static final Unsafe UNSAFE = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
        @Override
        public Unsafe run() {
            assert Unsafe.ARRAY_BYTE_INDEX_SCALE == 1 : "cannot use Unsafe for ByteArrayAccess if ARRAY_BYTE_INDEX_SCALE != 1";
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
            }
        }
    });

    @Override
    public byte getByte(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putByte(byte[] buffer, int index, byte value) throws IndexOutOfBoundsException {
        UNSAFE.putByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);

    }

    @Override
    public short getShort(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putShort(byte[] buffer, int index, short value) throws IndexOutOfBoundsException {
        UNSAFE.putShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);
    }

    @Override
    public int getInt(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putInt(byte[] buffer, int index, int value) throws IndexOutOfBoundsException {
        UNSAFE.putInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);
    }

    @Override
    public long getLong(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putLong(byte[] buffer, int index, long value) throws IndexOutOfBoundsException {
        UNSAFE.putLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);
    }

    @Override
    public float getFloat(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putFloat(byte[] buffer, int index, float value) throws IndexOutOfBoundsException {
        UNSAFE.putFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);
    }

    @Override
    public double getDouble(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return UNSAFE.getDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index));
    }

    @Override
    public void putDouble(byte[] buffer, int index, double value) throws IndexOutOfBoundsException {
        UNSAFE.putDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(index), value);
    }
}
