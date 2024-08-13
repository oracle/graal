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

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Implementation of {@link ByteArraySupport} using {@link Unsafe}.
 * <p>
 * Bytes ordering is native endianness ({@link ByteOrder#nativeOrder}).
 */
final class UnsafeByteArraySupport extends ByteArraySupport {
    /**
     * Partial evaluation does not constant-fold unaligned accesses, so in compiled code we
     * decompose unaligned accesses into multiple aligned accesses that can be constant-folded. This
     * optimization is only tested on little-endian platforms.
     */
    private static final boolean OPTIMIZED_UNALIGNED_SUPPORTED = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    @SuppressWarnings("deprecation") private static final Unsafe UNSAFE = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
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
    public byte getByte(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public byte getByte(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putByte(byte[] buffer, int byteOffset, byte value) throws IndexOutOfBoundsException {
        UNSAFE.putByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putByte(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        UNSAFE.putByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public short getShort(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public short getShort(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putShort(byte[] buffer, int byteOffset, short value) throws IndexOutOfBoundsException {
        UNSAFE.putShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putShort(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        UNSAFE.putShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public int getInt(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public int getInt(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putInt(byte[] buffer, int byteOffset, int value) throws IndexOutOfBoundsException {
        UNSAFE.putInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putInt(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        UNSAFE.putInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public long getLong(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public long getLong(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putLong(byte[] buffer, int byteOffset, long value) throws IndexOutOfBoundsException {
        UNSAFE.putLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putLong(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        UNSAFE.putLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public float getFloat(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public float getFloat(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putFloat(byte[] buffer, int byteOffset, float value) throws IndexOutOfBoundsException {
        UNSAFE.putFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putFloat(byte[] buffer, long byteOffset, float value) throws IndexOutOfBoundsException {
        UNSAFE.putFloat(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public double getDouble(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public double getDouble(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putDouble(byte[] buffer, int byteOffset, double value) throws IndexOutOfBoundsException {
        UNSAFE.putDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + Integer.toUnsignedLong(byteOffset), value);
    }

    @Override
    public void putDouble(byte[] buffer, long byteOffset, double value) throws IndexOutOfBoundsException {
        UNSAFE.putDouble(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public short getShortUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getShortUnaligned(buffer, Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public short getShortUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        if (CompilerDirectives.inCompiledCode() && OPTIMIZED_UNALIGNED_SUPPORTED) {
            if (byteOffset % Short.BYTES == 0) {
                return getShort(buffer, byteOffset);
            } else {
                return (short) ((getByte(buffer, byteOffset) & 0xFF) |
                                ((getByte(buffer, byteOffset + 1) & 0xFF) << Byte.SIZE));
            }
        } else {
            return getShort(buffer, byteOffset);
        }
    }

    @Override
    public int getIntUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getIntUnaligned(buffer, Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public int getIntUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        if (CompilerDirectives.inCompiledCode() && OPTIMIZED_UNALIGNED_SUPPORTED) {
            if (byteOffset % Integer.BYTES == 0) {
                return getInt(buffer, byteOffset);
            } else {
                return (getByte(buffer, byteOffset) & 0xFF) |
                                ((getByte(buffer, byteOffset + 1) & 0xFF) << Byte.SIZE) |
                                ((getByte(buffer, byteOffset + 2) & 0xFF) << Byte.SIZE * 2) |
                                ((getByte(buffer, byteOffset + 3) & 0xFF) << Byte.SIZE * 3);
            }
        } else {
            return getInt(buffer, byteOffset);
        }
    }

    @Override
    public long getLongUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException {
        return getLongUnaligned(buffer, Integer.toUnsignedLong(byteOffset));
    }

    @Override
    public long getLongUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        if (CompilerDirectives.inCompiledCode() && OPTIMIZED_UNALIGNED_SUPPORTED) {
            if (byteOffset % Long.BYTES == 0) {
                return getLong(buffer, byteOffset);
            } else {
                return (getByte(buffer, byteOffset) & 0xFFL) |
                                ((getByte(buffer, byteOffset + 1) & 0xFFL) << Byte.SIZE) |
                                ((getByte(buffer, byteOffset + 2) & 0xFFL) << Byte.SIZE * 2) |
                                ((getByte(buffer, byteOffset + 3) & 0xFFL) << Byte.SIZE * 3) |
                                ((getByte(buffer, byteOffset + 4) & 0xFFL) << Byte.SIZE * 4) |
                                ((getByte(buffer, byteOffset + 5) & 0xFFL) << Byte.SIZE * 5) |
                                ((getByte(buffer, byteOffset + 6) & 0xFFL) << Byte.SIZE * 6) |
                                ((getByte(buffer, byteOffset + 7) & 0xFFL) << Byte.SIZE * 7);
            }
        } else {
            return getLong(buffer, byteOffset);
        }
    }

    @Override
    public byte getByteVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getByteVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putByteVolatile(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException {
        UNSAFE.putByteVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public short getShortVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getShortVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putShortVolatile(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException {
        UNSAFE.putShortVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public int getIntVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getIntVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putIntVolatile(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException {
        UNSAFE.putIntVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public long getLongVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException {
        return UNSAFE.getLongVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
    }

    @Override
    public void putLongVolatile(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException {
        UNSAFE.putLongVolatile(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, value);
    }

    @Override
    public byte getAndAddByte(byte[] buffer, long byteOffset, byte delta) throws IndexOutOfBoundsException {
        byte v;
        do {
            v = getByteVolatile(buffer, byteOffset);
        } while (compareAndExchangeByte(buffer, byteOffset, v, (byte) (v + delta)) != v);
        return v;
    }

    @Override
    public short getAndAddShort(byte[] buffer, long byteOffset, short delta) throws IndexOutOfBoundsException {
        short v;
        do {
            v = getShortVolatile(buffer, byteOffset);
        } while (compareAndExchangeShort(buffer, byteOffset, v, (short) (v + delta)) != v);
        return v;
    }

    @Override
    public int getAndAddInt(byte[] buffer, long byteOffset, int delta) throws IndexOutOfBoundsException {
        return UNSAFE.getAndAddInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, delta);
    }

    @Override
    public long getAndAddLong(byte[] buffer, long byteOffset, long delta) throws IndexOutOfBoundsException {
        return UNSAFE.getAndAddLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, delta);
    }

    @Override
    public byte getAndBitwiseAndByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        byte v;
        do {
            v = getByteVolatile(buffer, byteOffset);
        } while (compareAndExchangeByte(buffer, byteOffset, v, (byte) (v & mask)) != v);
        return v;
    }

    @Override
    public short getAndBitwiseAndShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        short v;
        do {
            v = getShortVolatile(buffer, byteOffset);
        } while (compareAndExchangeShort(buffer, byteOffset, v, (short) (v & mask)) != v);
        return v;
    }

    @Override
    public int getAndBitwiseAndInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        int v;
        do {
            v = getIntVolatile(buffer, byteOffset);
        } while (compareAndExchangeInt(buffer, byteOffset, v, v & mask) != v);
        return v;
    }

    @Override
    public long getAndBitwiseAndLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        long v;
        do {
            v = getLongVolatile(buffer, byteOffset);
        } while (compareAndExchangeLong(buffer, byteOffset, v, v & mask) != v);
        return v;
    }

    @Override
    public byte getAndBitwiseOrByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        byte v;
        do {
            v = getByteVolatile(buffer, byteOffset);
        } while (compareAndExchangeByte(buffer, byteOffset, v, (byte) (v | mask)) != v);
        return v;
    }

    @Override
    public short getAndBitwiseOrShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        short v;
        do {
            v = getShortVolatile(buffer, byteOffset);
        } while (compareAndExchangeShort(buffer, byteOffset, v, (short) (v | mask)) != v);
        return v;
    }

    @Override
    public int getAndBitwiseOrInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        int v;
        do {
            v = getIntVolatile(buffer, byteOffset);
        } while (compareAndExchangeInt(buffer, byteOffset, v, v | mask) != v);
        return v;
    }

    @Override
    public long getAndBitwiseOrLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        long v;
        do {
            v = getLongVolatile(buffer, byteOffset);
        } while (compareAndExchangeLong(buffer, byteOffset, v, v | mask) != v);
        return v;
    }

    @Override
    public byte getAndBitwiseXorByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException {
        byte v;
        do {
            v = getByteVolatile(buffer, byteOffset);
        } while (compareAndExchangeByte(buffer, byteOffset, v, (byte) (v ^ mask)) != v);
        return v;
    }

    @Override
    public short getAndBitwiseXorShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException {
        short v;
        do {
            v = getShortVolatile(buffer, byteOffset);
        } while (compareAndExchangeShort(buffer, byteOffset, v, (short) (v ^ mask)) != v);
        return v;
    }

    @Override
    public int getAndBitwiseXorInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException {
        int v;
        do {
            v = getIntVolatile(buffer, byteOffset);
        } while (compareAndExchangeInt(buffer, byteOffset, v, v ^ mask) != v);
        return v;
    }

    @Override
    public long getAndBitwiseXorLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException {
        long v;
        do {
            v = getLongVolatile(buffer, byteOffset);
        } while (compareAndExchangeLong(buffer, byteOffset, v, v ^ mask) != v);
        return v;
    }

    @Override
    public byte getAndSetByte(byte[] buffer, long byteOffset, byte newValue) throws IndexOutOfBoundsException {
        byte v;
        do {
            v = getByteVolatile(buffer, byteOffset);
        } while (compareAndExchangeByte(buffer, byteOffset, v, newValue) != v);
        return v;
    }

    @Override
    public short getAndSetShort(byte[] buffer, long byteOffset, short newValue) throws IndexOutOfBoundsException {
        short v;
        do {
            v = getShortVolatile(buffer, byteOffset);
        } while (compareAndExchangeShort(buffer, byteOffset, v, newValue) != v);
        return v;
    }

    @Override
    public int getAndSetInt(byte[] buffer, long byteOffset, int newValue) throws IndexOutOfBoundsException {
        return UNSAFE.getAndSetInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, newValue);
    }

    @Override
    public long getAndSetLong(byte[] buffer, long byteOffset, long newValue) throws IndexOutOfBoundsException {
        return UNSAFE.getAndSetLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset, newValue);
    }

    @Override
    public byte compareAndExchangeByte(byte[] buffer, long byteOffset, byte expected, byte x) {
        long wordOffset = byteOffset & ~3;
        int shift = (int) (byteOffset & 3) << 3;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            shift = 24 - shift;
        }
        int mask = 0xFF << shift;
        int maskedExpected = (expected & 0xFF) << shift;
        int maskedX = (x & 0xFF) << shift;
        int fullWord;
        do {
            fullWord = getIntVolatile(buffer, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (byte) ((fullWord & mask) >> shift);
            }
        } while (!UNSAFE.compareAndSwapInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    @Override
    public short compareAndExchangeShort(byte[] buffer, long byteOffset, short expected, short x) {
        if ((byteOffset & 3) == 3) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~3;
        int shift = (int) (byteOffset & 3) << 3;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            shift = 16 - shift;
        }
        int mask = 0xFFFF << shift;
        int maskedExpected = (expected & 0xFFFF) << shift;
        int maskedX = (x & 0xFFFF) << shift;
        int fullWord;
        do {
            fullWord = getIntVolatile(buffer, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (short) ((fullWord & mask) >> shift);
            }
        } while (!UNSAFE.compareAndSwapInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    @Override
    public int compareAndExchangeInt(byte[] buffer, long byteOffset, int expected, int x) throws IndexOutOfBoundsException {
        if ((byteOffset & 3) != 0) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~3;
        int fullWord;
        do {
            fullWord = getIntVolatile(buffer, wordOffset);
            if (fullWord != expected) {
                return fullWord;
            }
        } while (!UNSAFE.compareAndSwapInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + wordOffset, fullWord, x));
        return expected;
    }

    @Override
    public long compareAndExchangeLong(byte[] buffer, long byteOffset, long expected, long x) throws IndexOutOfBoundsException {
        if ((byteOffset & 7) != 0) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~7;
        long fullWord;
        do {
            fullWord = getLongVolatile(buffer, wordOffset);
            if (fullWord != expected) {
                return fullWord;
            }
        } while (!UNSAFE.compareAndSwapLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + wordOffset, fullWord, x));
        return expected;
    }
}
