/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;

/**
 * Implementation of {@link ByteArraySupport} using {@link Unsafe}.
 * <p>
 * Bytes ordering is native endianness ({@link ByteOrder#nativeOrder}).
 */
final class UnsafeByteArraySupport extends ByteArraySupport {

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
        if (CompilerDirectives.inCompiledCode()) {
            return unsafeGetShortUnaligned(buffer, byteOffset);
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
        if (CompilerDirectives.inCompiledCode()) {
            return unsafeGetIntUnaligned(buffer, byteOffset);
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
        if (CompilerDirectives.inCompiledCode()) {
            return unsafeGetLongUnaligned(buffer, byteOffset);
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

    /**
     * Intrinsic candidate.
     *
     * Partial evaluation does not constant-fold unaligned accesses, so in compiled code we
     * decompose unaligned accesses into multiple aligned accesses that can be constant-folded.
     */
    private static short unsafeGetShortUnaligned(byte[] buffer, long byteOffset) {
        if (byteOffset % Short.BYTES == 0) {
            return UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
        } else {
            return makeShort(UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 1));
        }
    }

    /**
     * Intrinsic candidate.
     *
     * Partial evaluation does not constant-fold unaligned accesses, so in compiled code we
     * decompose unaligned accesses into multiple aligned accesses that can be constant-folded.
     */
    private static int unsafeGetIntUnaligned(byte[] buffer, long byteOffset) {
        if (byteOffset % Integer.BYTES == 0) {
            return UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
        } else if (byteOffset % Short.BYTES == 0) {
            return makeInt(UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + Short.BYTES));
        } else {
            return makeInt(UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 1),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 2),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 3));
        }
    }

    /**
     * Intrinsic candidate.
     *
     * Partial evaluation does not constant-fold unaligned accesses, so in compiled code we
     * decompose unaligned accesses into multiple aligned accesses that can be constant-folded.
     */
    private static long unsafeGetLongUnaligned(byte[] buffer, long byteOffset) {
        if (byteOffset % Long.BYTES == 0) {
            return UNSAFE.getLong(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset);
        } else if (byteOffset % Integer.BYTES == 0) {
            return makeLong(UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getInt(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 4));
        } else if (byteOffset % Short.BYTES == 0) {
            return makeLong(UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + Short.BYTES),
                            UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + Short.BYTES * 2),
                            UNSAFE.getShort(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + Short.BYTES * 3));
        } else {
            return makeLong(UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 1),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 2),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 3),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 4),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 5),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 6),
                            UNSAFE.getByte(buffer, Unsafe.ARRAY_BYTE_BASE_OFFSET + byteOffset + 7));
        }
    }

    private static long makeLong(byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (i0 & 0xffL) |
                            ((i1 & 0xffL) << Byte.SIZE) |
                            ((i2 & 0xffL) << Byte.SIZE * 2) |
                            ((i3 & 0xffL) << Byte.SIZE * 3) |
                            ((i4 & 0xffL) << Byte.SIZE * 4) |
                            ((i5 & 0xffL) << Byte.SIZE * 5) |
                            ((i6 & 0xffL) << Byte.SIZE * 6) |
                            ((i7 & 0xffL) << Byte.SIZE * 7);
        } else {
            return ((i0 & 0xffL) << Byte.SIZE * 7) |
                            ((i1 & 0xffL) << Byte.SIZE * 6) |
                            ((i2 & 0xffL) << Byte.SIZE * 5) |
                            ((i3 & 0xffL) << Byte.SIZE * 4) |
                            ((i4 & 0xffL) << Byte.SIZE * 3) |
                            ((i5 & 0xffL) << Byte.SIZE * 2) |
                            ((i6 & 0xffL) << Byte.SIZE) |
                            (i7 & 0xffL);
        }
    }

    private static long makeLong(short i0, short i1, short i2, short i3) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (i0 & 0xffffL) |
                            ((i1 & 0xffffL) << Short.SIZE) |
                            ((i2 & 0xffffL) << Short.SIZE * 2) |
                            ((i3 & 0xffffL) << Short.SIZE * 3);
        } else {
            return ((i0 & 0xffffL) << Short.SIZE * 3) |
                            ((i1 & 0xffffL) << Short.SIZE * 2) |
                            ((i2 & 0xffffL) << Short.SIZE) |
                            (i3 & 0xffffL);
        }
    }

    private static long makeLong(int i0, int i1) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (i0 & 0xffffffffL) |
                            ((i1 & 0xffffffffL) << Integer.SIZE);
        } else {
            return ((i0 & 0xffffffffL) << Integer.SIZE) |
                            (i1 & 0xffffffffL);
        }
    }

    private static int makeInt(short i0, short i1) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (i0 & 0xffff) |
                            ((i1 & 0xffff) << Short.SIZE);
        } else {
            return ((i0 & 0xffff) << Short.SIZE) |
                            (i1 & 0xffff);
        }
    }

    private static int makeInt(byte i0, byte i1, byte i2, byte i3) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (i0 & 0xff) |
                            ((i1 & 0xff) << Byte.SIZE) |
                            ((i2 & 0xff) << Byte.SIZE * 2) |
                            ((i3 & 0xff) << Byte.SIZE * 3);
        } else {
            return ((i0 & 0xff) << Byte.SIZE * 3) |
                            ((i1 & 0xff) << Byte.SIZE * 2) |
                            ((i2 & 0xff) << Byte.SIZE) |
                            (i3 & 0xff);
        }
    }

    private static short makeShort(byte i0, byte i1) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (short) ((i0 & 0xff) |
                            (i1 & 0xff) << Byte.SIZE);
        } else {
            return (short) ((i0 & 0xff) << Byte.SIZE |
                            (i1 & 0xff));
        }
    }
}
