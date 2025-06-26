/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.CodeUtil;

/**
 * A collection of static utility functions that check ranges of numbers.
 */
public class NumUtil {

    public enum Signedness {
        SIGNED,
        UNSIGNED
    }

    public static boolean isShiftCount(int x) {
        return 0 <= x && x < 32;
    }

    /**
     * Determines if a given {@code int} value is the range of unsigned byte values.
     */
    public static boolean isUByte(int x) {
        return (x & 0xff) == x;
    }

    /**
     * Determines if a given {@code int} value is the range of signed byte values.
     */
    public static boolean isByte(int x) {
        return (byte) x == x;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned byte values.
     */
    public static boolean isUByte(long x) {
        return (x & 0xffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed byte values.
     */
    public static boolean isByte(long l) {
        return (byte) l == l;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned int values.
     */
    public static boolean isUInt(long x) {
        return (x & 0xffffffffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed int values.
     */
    public static boolean isInt(long l) {
        return (int) l == l;
    }

    /**
     * Determines if a given {@code int} value is the range of signed short values.
     */
    public static boolean isShort(int x) {
        return (short) x == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed short values.
     */
    public static boolean isShort(long x) {
        return (short) x == x;
    }

    public static boolean isUShort(int s) {
        return s == (s & 0xFFFF);
    }

    public static boolean is32bit(long x) {
        return -0x80000000L <= x && x < 0x80000000L;
    }

    public static byte safeToUByte(int v) {
        assert isUByte(v);
        return (byte) v;
    }

    public static byte safeToByte(long v) {
        assert isByte(v);
        return (byte) v;
    }

    public static byte safeToByteAE(long v) {
        if (!isByte(v)) {
            throw new ArithmeticException(String.format("%s is not a byte", v));
        }
        return (byte) v;
    }

    public static short safeToUShort(int v) {
        assert isUShort(v);
        return (short) v;
    }

    public static short safeToShort(long v) {
        assert isShort(v);
        return (short) v;

    }

    public static short safeToShortAE(long v) {
        if (!isShort(v)) {
            throw new ArithmeticException(String.format("%s is not a short", v));
        }
        return (short) v;
    }

    public static int safeToUInt(long v) {
        assert isUInt(v);
        return (int) v;
    }

    public static int safeToInt(long v) {
        assert isInt(v);
        return (int) v;
    }

    public static int safeToIntAE(long v) {
        if (!isInt(v)) {
            throw new ArithmeticException(String.format("%s is not an int", v));
        }
        return (int) v;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static long roundUp(long number, long mod) {
        return ((number + mod - 1L) / mod) * mod;
    }

    public static int divideAndRoundUp(int number, int divisor) {
        return (number + divisor - 1) / divisor;
    }

    public static boolean isUnsignedNbit(int n, int value) {
        assert n > 0 && n < 32 : n;
        return 32 - Integer.numberOfLeadingZeros(value) <= n;
    }

    public static boolean isUnsignedNbit(int n, long value) {
        assert n > 0 && n < 64 : n;
        return 64 - Long.numberOfLeadingZeros(value) <= n;
    }

    public static boolean isSignedNbit(int n, int value) {
        assert n > 0 && n < 32 : n;
        int min = -(1 << (n - 1));
        int max = (1 << (n - 1)) - 1;
        return value >= min && value <= max;
    }

    public static boolean isSignedNbit(int n, long value) {
        assert n > 0 && n < 64 : n;
        long min = -(1L << (n - 1));
        long max = (1L << (n - 1)) - 1;
        return value >= min && value <= max;
    }

    /**
     *
     * @param n Number of bits that should be set to 1. Must be between 0 and 32 (inclusive).
     * @return A number with n bits set to 1.
     */
    public static int getNbitNumberInt(int n) {
        assert n >= 0 && n <= 32 : "0 <= n <= 32; instead: " + n;
        if (n < 32) {
            return (1 << n) - 1;
        } else {
            return 0xFFFFFFFF;
        }
    }

    /**
     *
     * @param n Number of bits that should be set to 1. Must be between 0 and 64 (inclusive).
     * @return A number with n bits set to 1.
     */
    public static long getNbitNumberLong(int n) {
        assert n >= 0 && n <= 64 : n;
        if (n < 64) {
            return (1L << n) - 1;
        } else {
            return 0xFFFFFFFFFFFFFFFFL;
        }
    }

    /**
     * Get the minimum value representable in a {@code bits} bit signed integer.
     */
    public static long minValue(int bits) {
        return CodeUtil.minValue(bits);
    }

    /**
     * Get the maximum value representable in a {@code bits} bit signed integer.
     */
    public static long maxValue(int bits) {
        return CodeUtil.maxValue(bits);
    }

    /**
     * Get the maximum value representable in a {@code bits} bit unsigned integer.
     */
    public static long maxValueUnsigned(int bits) {
        return getNbitNumberLong(bits);
    }

    public static long maxUnsigned(long a, long b) {
        if (Long.compareUnsigned(a, b) < 0) {
            return b;
        }
        return a;
    }

    public static long minUnsigned(long a, long b) {
        if (Long.compareUnsigned(a, b) < 0) {
            return a;
        }
        return b;
    }

    public static boolean sameSign(long a, long b) {
        return a < 0 == b < 0;
    }

    /**
     * Determines if the input, when interpreted as an unsigned integer, is a power of 2. Compare
     * {@link CodeUtil#isPowerOf2(long)} which does the same check but interprets the input as
     * signed and requires it to be positive.
     */
    public static boolean isUnsignedPowerOf2(long n) {
        return n != 0 && (n & n - 1) == 0;
    }

    /**
     * Computes the log (base 2) of the input interpreted as an unsigned integer, rounding down
     * (e.g., {@code log2(8) = 3}, {@code log2(21) = 4}). Compare {@link CodeUtil#log2(long)} which
     * does the same computation but interprets the input as signed and requires it to be positive.
     *
     * @param val the value
     * @return the log base 2 of the value
     */
    public static int unsignedLog2(long val) {
        assert val != 0;
        return (Long.SIZE - 1) - Long.numberOfLeadingZeros(val);
    }

    /**
     * Converts a hex string to a byte array. Two characters are converted to a byte at a time.
     *
     * @param hex the hex string
     * @return byte array
     */
    public static byte[] hexStringToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            // need to parse as int, because parseByte will throw on values > 127
            int val = Integer.parseInt(hex.substring(i << 1, (i << 1) + 2), 16);
            if (val < 0 || val > 255) {
                throw new NumberFormatException("Value out of range: " + val);
            }
            bytes[i] = (byte) val;
        }
        return bytes;
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    /**
     * Converts a byte array to a hex string. Each byte is represented by two characters.
     */
    public static String bytesToHexString(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * Ensure the supplied double is a finite, positive number.
     */
    public static boolean assertPositiveDouble(double d) {
        assert Double.isFinite(d) && d >= 0 : "expected finite positive double, got " + d;
        return true;
    }

    public static boolean assertFiniteDouble(double d) {
        assert Double.isFinite(d) : "expected finite  double, got " + d;
        return true;
    }

    public static boolean assertNonNegativeInt(int i) {
        assert i >= 0 : "expected positive int, got " + i;
        return true;
    }

    public static boolean assertPositiveInt(int i) {
        assert i > 0 : "expected positive int, got " + i;
        return true;
    }

    public static boolean assertNonNegativeLong(long l) {
        assert l >= 0L : "expected positive long, got " + l;
        return true;
    }

    public static boolean assertNonNegativeDouble(double d) {
        assert d >= 0D : "expected  positive double, got " + d;
        return true;
    }

    public static final int ASSERTION_TRUNCATE_ARR_LENGTH = 1024;

    public static boolean assertArrayLength(Object[] o, int length) {
        assert o.length == length : "Length of array " + arrayToString(o, ASSERTION_TRUNCATE_ARR_LENGTH) + " !=" + length;
        return true;
    }

    public static boolean assertArrayLength(Object[] o, int minLength, int maxLength) {
        assert o.length >= minLength && o.length <= maxLength : "Length of array " + arrayToString(o, ASSERTION_TRUNCATE_ARR_LENGTH) + " is not in [" + minLength + ":" + maxLength + "]";
        return true;
    }

    public static String arrayToString(Object[] a, int truncateAfter) {
        if (a.length <= truncateAfter) {
            return Arrays.toString(a);
        }
        String s = Arrays.toString(Arrays.copyOf(a, truncateAfter));
        // Replace trailing "]" with ", ...]"
        return s.substring(0, s.length() - 1) + ", ...]";
    }

    /**
     * Determines if the absolute value of {@code l} overflows. The bit size is derived from the
     * {@link Stamp} of {@code v}.
     */
    public static boolean absOverflows(long l, ValueNode v) {
        return absOverflows(l, IntegerStamp.getBits(v.stamp(NodeView.DEFAULT)));
    }

    /**
     * Determines if the absolute value of {@code l} overflows.
     */
    public static boolean absOverflows(long l, int bits) {
        if (bits == 32) {
            final int i = (int) l;
            return i == Integer.MIN_VALUE;
        } else if (bits == 64) {
            return l == Long.MIN_VALUE;
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes but is " + bits);
        }
    }

    public static long safeAbs(long l, ValueNode v) throws ArithmeticException {
        return safeAbs(l, IntegerStamp.getBits(v.stamp(NodeView.DEFAULT)));
    }

    public static long safeAbs(long l) throws ArithmeticException {
        return safeAbs(l, 64);
    }

    public static int safeAbs(int i) throws ArithmeticException {
        return NumUtil.safeToInt(safeAbs(i, 32));
    }

    /**
     * Returns {@link Math#absExact} for {@code l}, throws an {@link ArithmeticException} in case of
     * overflow.
     */
    public static long safeAbs(long l, int bits) throws ArithmeticException {
        if (bits == 32) {
            final int i = (int) l;
            return Math.absExact(i);
        } else if (bits == 64) {
            return Math.absExact(l);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes but is " + bits);
        }
    }

    /**
     * Returns {@link Math#abs} for {@code i}. Does NOT throw an {@link ArithmeticException} in case
     * of overflow. Only use this method if overflow is handled/accepted by the caller.
     */
    public static int unsafeAbs(int i) {
        return Math.abs(i);
    }

    /**
     * Returns {@link Math#abs} for {@code i}. Does NOT throw an {@link ArithmeticException} in case
     * of overflow. Only use this method if overflow is handled/accepted by the caller.
     */
    public static long unsafeAbs(long l) {
        return Math.abs(l);
    }

    /**
     * Converts the double value to unsigned long, rounding toward zero.
     */
    public static long toUnsignedLong(double value) {
        if (value >= 0x1p63) {
            return (long) (value - 0x1p63) + Long.MIN_VALUE;
        } else if (value >= 0) {
            return (long) value;
        } else {
            return 0;
        }
    }

    /**
     * Converts the unsigned long value to the nearest double value.
     */
    public static double unsignedToDouble(long value) {
        if (value >= 0) {
            return value;
        } else {
            return ((value >>> 1) | (value & 1)) * 2.0;
        }
    }

    /**
     * Converts the unsigned long value to the nearest float value.
     */
    public static float unsignedToFloat(long value) {
        if (value >= 0) {
            return value;
        } else {
            return ((value >>> 1) | (value & 1)) * 2.0f;
        }
    }
}
