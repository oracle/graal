/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.substitutions.standard;

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;
import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.MemoryAccessMode;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.meta.EspressoError;

import sun.misc.Unsafe;

/**
 * Class to enable support for compare and swap/exchange for sub-word fields.
 * <p>
 * This class will be overlayed, in favor of overlay classes: one for version &lt=8 and &gt= 9. This
 * class implements the &lt=8 version, which works for 9+, but is far from optimal.
 * <p>
 * The version for &gt=9 should be able to call directly into host internal Unsafe methods to get
 * better performance.
 */
final class UnsafeSupport {
    private static final Unsafe UNSAFE;

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new InternalError();
        }
    }

    private UnsafeSupport() {
    }

    static boolean isBigEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    static byte compareAndExchangeByte(
                    Object o, long offset,
                    byte expected,
                    byte x, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        long wordOffset = offset & ~3;
        int shift = (int) (offset & 3) << 3;
        if (isBigEndian()) {
            shift = 24 - shift;
        }
        int mask = 0xFF << shift;
        int maskedExpected = (expected & 0xFF) << shift;
        int maskedX = (x & 0xFF) << shift;
        int fullWord;
        do {
            fullWord = doGetIntVolatile(o, wordOffset, nativeMemory);
            if ((fullWord & mask) != maskedExpected) {
                return (byte) ((fullWord & mask) >> shift);
            }
        } while (!doCAS(o, wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX, nativeMemory));
        return expected;
    }

    private static int doGetIntVolatile(Object o, long offset, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if (nativeMemory != null && o == null) {
            return nativeMemory.getInt(offset, MemoryAccessMode.VOLATILE);
        }
        return UNSAFE.getIntVolatile(o, offset);
    }

    private static boolean doCAS(Object o, long offset, int expected, int value, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if (nativeMemory != null && o == null) {
            return nativeMemory.compareAndExchangeInt(offset, expected, value) == expected;
        }
        return UNSAFE.compareAndSwapInt(o, offset, expected, value);
    }

    static short compareAndExchangeShort(
                    Object o, long offset,
                    short expected,
                    short x, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if ((offset & 3) == 3) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = offset & ~3;
        int shift = (int) (offset & 3) << 3;
        if (isBigEndian()) {
            shift = 16 - shift;
        }
        int mask = 0xFFFF << shift;
        int maskedExpected = (expected & 0xFFFF) << shift;
        int maskedX = (x & 0xFFFF) << shift;
        int fullWord;
        do {
            fullWord = doGetIntVolatile(o, wordOffset, nativeMemory);
            if ((fullWord & mask) != maskedExpected) {
                return (short) ((fullWord & mask) >> shift);
            }
        } while (!doCAS(o, wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX, nativeMemory));
        return expected;
    }

    static int compareAndExchangeInt(
                    Object o, long offset,
                    int expected,
                    int x) {
        int result;
        do {
            result = UNSAFE.getIntVolatile(o, offset);
            if (result != expected) {
                return result;
            }
        } while (!UNSAFE.compareAndSwapInt(o, offset, expected, x));
        return expected;
    }

    static Object compareAndExchangeObject(
                    Object o, long offset,
                    Object expected,
                    Object x) {
        Object result;
        do {
            result = UNSAFE.getObjectVolatile(o, offset);
            if (result != expected) {
                return result;
            }
        } while (!UNSAFE.compareAndSwapObject(o, offset, expected, x));
        return expected;
    }

    static long compareAndExchangeLong(
                    Object o, long offset,
                    long expected,
                    long x) {
        long result;
        do {
            result = UNSAFE.getLongVolatile(o, offset);
            if (result != expected) {
                return result;
            }
        } while (!UNSAFE.compareAndSwapLong(o, offset, expected, x));
        return expected;
    }

    static void copySwapMemory(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        switch ((int) elemSize) {
            case 2:
                CSMHelper.do2(src, srcOffset, dst, destOffset, bytes, elemSize, nativeMemory);
                return;
            case 4:
                CSMHelper.do4(src, srcOffset, dst, destOffset, bytes, elemSize, nativeMemory);
                return;
            case 8:
                CSMHelper.do8(src, srcOffset, dst, destOffset, bytes, elemSize, nativeMemory);
                return;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
    }

    private static final class CSMHelper {
        private enum Direction {
            Right(1),
            Left(-1);

            final int dir;

            Direction(int dir) {
                this.dir = dir;
            }

            long initPos(long bytes, long elemSize) {
                return (this == Right) ? 0 : (bytes - elemSize);
            }

            long inc(long elemSize) {
                return (dir * elemSize);
            }
        }

        private static Direction getDirection(Object src, long srcOffset, Object dst, long destOffset, long bytes) {
            long srcEnd = srcOffset + bytes;
            if (src == dst) { // Covers case of both are null
                return destOffset <= srcOffset || destOffset >= srcEnd ? Direction.Right : Direction.Left;
            }
            return Direction.Right;
        }

        private static char swap(char c) {
            return Character.reverseBytes(c);
        }

        private static int swap(int i) {
            return Integer.reverseBytes(i);
        }

        private static long swap(long l) {
            return Long.reverseBytes(l);
        }

        static void do2(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            assert elemSize == 2 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            char tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = doGetChar(src, srcOffset + pos, nativeMemory);
                tmp = swap(tmp);
                doPutChar(dst, destOffset + pos, tmp, nativeMemory);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }

        private static char doGetChar(Object o, long offSet, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                return nativeMemory.getChar(offSet, MemoryAccessMode.PLAIN);
            }
            return UNSAFE.getChar(o, offSet);
        }

        private static void doPutChar(Object o, long offSet, char x, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                nativeMemory.putChar(offSet, x, MemoryAccessMode.PLAIN);
                return;
            }
            UNSAFE.putChar(o, offSet, x);
        }

        static void do4(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            assert elemSize == 4 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            int tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = swap(doGetInt(src, srcOffset + pos, nativeMemory));
                doPutInt(dst, destOffset + pos, tmp, nativeMemory);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }

        private static int doGetInt(Object o, long offSet, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                return nativeMemory.getInt(offSet, MemoryAccessMode.PLAIN);
            }
            return UNSAFE.getInt(o, offSet);
        }

        private static void doPutInt(Object o, long offSet, int x, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                nativeMemory.putInt(offSet, x, MemoryAccessMode.PLAIN);
                return;
            }
            UNSAFE.putInt(o, offSet, x);
        }

        static void do8(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            assert elemSize == 8 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            long tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = swap(doGetLong(src, srcOffset + pos, nativeMemory));
                doPutLong(dst, destOffset + pos, tmp, nativeMemory);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }

        private static long doGetLong(Object o, long offSet, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                return nativeMemory.getLong(offSet, MemoryAccessMode.PLAIN);
            }
            return UNSAFE.getLong(o, offSet);
        }

        private static void doPutLong(Object o, long offSet, long x, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
            if (o == null) {
                nativeMemory.putLong(offSet, x, MemoryAccessMode.PLAIN);
                return;
            }
            UNSAFE.putLong(o, offSet, x);
        }
    }
}
