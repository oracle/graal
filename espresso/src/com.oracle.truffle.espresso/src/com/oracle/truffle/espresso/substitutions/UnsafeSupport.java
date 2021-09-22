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

package com.oracle.truffle.espresso.substitutions;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
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

    static boolean compareAndSetByte(
                    Object o, long offset,
                    byte expected,
                    byte x) {
        return compareAndExchangeByte(o, offset, expected, x) == expected;
    }

    static boolean compareAndSetBoolean(
                    Object o, long offset,
                    boolean expected,
                    boolean x) {
        byte byteExpected = expected ? (byte) 1 : (byte) 0;
        byte byteX = x ? (byte) 1 : (byte) 0;
        return compareAndSetByte(o, offset, byteExpected, byteX);
    }

    static boolean compareAndSetShort(
                    Object o, long offset,
                    short expected,
                    short x) {
        return compareAndExchangeShort(o, offset, expected, x) == expected;
    }

    static boolean compareAndSetChar(
                    Object o, long offset,
                    char expected,
                    char x) {
        return compareAndSetShort(o, offset, (short) expected, (short) x);
    }

    static boolean compareAndSetFloat(
                    Object o, long offset,
                    float expected,
                    float x) {
        return UNSAFE.compareAndSwapInt(o, offset,
                        Float.floatToRawIntBits(expected),
                        Float.floatToRawIntBits(x));
    }

    static boolean compareAndSetDouble(
                    Object o, long offset,
                    double expected,
                    double x) {
        return UNSAFE.compareAndSwapLong(o, offset,
                        Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(x));
    }

    static byte compareAndExchangeByte(
                    Object o, long offset,
                    byte expected,
                    byte x) {
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
            fullWord = UNSAFE.getIntVolatile(o, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (byte) ((fullWord & mask) >> shift);
            }
        } while (!UNSAFE.compareAndSwapInt(o, wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    static boolean compareAndExchangeBoolean(
                    Object o, long offset,
                    boolean expected,
                    boolean x) {
        byte byteExpected = expected ? (byte) 1 : (byte) 0;
        byte byteX = x ? (byte) 1 : (byte) 0;
        return compareAndExchangeByte(o, offset, byteExpected, byteX) != 0;
    }

    static short compareAndExchangeShort(
                    Object o, long offset,
                    short expected,
                    short x) {
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
            fullWord = UNSAFE.getIntVolatile(o, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (short) ((fullWord & mask) >> shift);
            }
        } while (!UNSAFE.compareAndSwapInt(o, wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    static char compareAndExchangeChar(
                    Object o, long offset,
                    char expected,
                    char x) {
        return (char) compareAndExchangeShort(o, offset, (short) expected, (short) x);
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

    static float compareAndExchangeFloat(
                    Object o, long offset,
                    float expected,
                    float x) {
        return Float.intBitsToFloat(compareAndExchangeInt(o, offset,
                        Float.floatToRawIntBits(expected),
                        Float.floatToRawIntBits(x)));
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

    static double compareAndExchangeDouble(
                    Object o, long offset,
                    double expected,
                    double x) {
        return Double.longBitsToDouble(compareAndExchangeLong(o, offset,
                        Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(x)));
    }

    static void copySwapMemory(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize) {
        switch ((int) elemSize) {
            case 2:
                CSMHelper.do2(src, srcOffset, dst, destOffset, bytes, elemSize);
                return;
            case 4:
                CSMHelper.do4(src, srcOffset, dst, destOffset, bytes, elemSize);
                return;
            case 8:
                CSMHelper.do8(src, srcOffset, dst, destOffset, bytes, elemSize);
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

        static void do2(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize) {
            assert elemSize == 2 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            char tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = UNSAFE.getChar(src, srcOffset + pos);
                tmp = swap(tmp);
                UNSAFE.putChar(dst, destOffset + pos, tmp);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }

        static void do4(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize) {
            assert elemSize == 4 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            int tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = swap(UNSAFE.getInt(src, srcOffset + pos));
                UNSAFE.putInt(dst, destOffset + pos, tmp);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }

        static void do8(Object src, long srcOffset, Object dst, long destOffset, long bytes, long elemSize) {
            assert elemSize == 8 : elemSize;
            Direction d = getDirection(src, srcOffset, dst, destOffset, bytes);
            long tmp;

            long copied = 0;
            long pos = d.initPos(bytes, elemSize);

            while (copied < bytes) {

                tmp = swap(UNSAFE.getLong(src, srcOffset + pos));
                UNSAFE.putLong(dst, destOffset + pos, tmp);

                pos += d.inc(elemSize);
                copied += elemSize;
            }
        }
    }

    // For 11:
    /*-
    static boolean isBigEndian() {
        return UNSAFE.isBigEndian();
    }
    
    static boolean compareAndSetByte(
                    Object o, long offset,
                    byte expected,
                    byte x) {
        return UNSAFE.compareAndSetByte(o, offset, expected, x);
    }
    
    static boolean compareAndSetBoolean(
                    Object o, long offset,
                    boolean expected,
                    boolean x) {
        return UNSAFE.compareAndSetBoolean(o, offset, expected, x);
    }
    
    static boolean compareAndSetShort(
                    Object o, long offset,
                    short expected,
                    short x) {
        return UNSAFE.compareAndSetShort(o, offset, expected, x);
    }
    
    static boolean compareAndSetChar(
                    Object o, long offset,
                    char expected,
                    char x) {
        return UNSAFE.compareAndSetChar(o, offset, expected, x);
    }
    
    static boolean compareAndSetFloat(
                    Object o, long offset,
                    float expected,
                    float x) {
        return UNSAFE.compareAndSetFloat(o, offset, expected, x);
    }
    
    static boolean compareAndSetDouble(
                    Object o, long offset,
                    double expected,
                    double x) {
        return UNSAFE.compareAndSetDouble(o, offset, expected, x);
    }
    
    static byte compareAndExchangeByte(
                    Object o, long offset,
                    byte expected,
                    byte x) {
        return UNSAFE.compareAndExchangeByte(o, offset, expected, x);
    }
    
    static boolean compareAndExchangeBoolean(
                    Object o, long offset,
                    boolean expected,
                    boolean x) {
        return UNSAFE.compareAndExchangeBoolean(o, offset, expected, x);
    }
    
    static short compareAndExchangeShort(
                    Object o, long offset,
                    short expected,
                    short x) {
        return UNSAFE.compareAndExchangeShort(o, offset, expected, x);
    }
    
    static char compareAndExchangeChar(
                    Object o, long offset,
                    char expected,
                    char x) {
        return UNSAFE.compareAndExchangeChar(o, offset, expected, x);
    }
    
    static int compareAndExchangeInt(
                    Object o, long offset,
                    int expected,
                    int x) {
        return UNSAFE.compareAndExchangeInt(o, offset, expected, x);
    }
    
    static Object compareAndExchangeObject(
                    Object o, long offset,
                    Object expected,
                    Object x) {
        return UNSAFE.compareAndExchangeObject(o, offset, expected, x);
    }
    
    static float compareAndExchangeFloat(
                    Object o, long offset,
                    float expected,
                    float x) {
        return UNSAFE.compareAndExchangeFloat(o, offset, expected, x);
    }
    
    static long compareAndExchangeLong(
                    Object o, long offset,
                    long expected,
                    long x) {
        return UNSAFE.compareAndExchangeLong(o, offset, expected, x);
    }
    
    static double compareAndExchangeDouble(
                    Object o, long offset,
                    double expected,
                    double x) {
        return UNSAFE.compareAndExchangeDouble(o, offset, expected, x);
    }
    */
}
