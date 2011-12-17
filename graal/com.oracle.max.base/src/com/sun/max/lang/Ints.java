/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.lang;

import com.sun.max.util.*;

/**
 * Additional methods that one might want in java.lang.Integer
 * and int array stuff.
 */
public final class Ints {

    // Utility classes should not be instantiated.
    private Ints() {
    }

    public static final int SIZE = 4;
    public static final int WIDTH = 32;

    public static final Range VALUE_RANGE = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final int K = 1024;
    public static final int M = K * K;

    public static int compare(int greater, int lesser) {
        if (greater > lesser) {
            return 1;
        }
        if (greater == lesser) {
            return 0;
        }
        return -1;
    }

    public static int numberOfEffectiveSignedBits(int signed) {
        if (signed >= 0) {
            return 33 - Integer.numberOfLeadingZeros(signed);
        }
        return 33 - Integer.numberOfLeadingZeros(~signed);
    }

    public static int numberOfEffectiveUnsignedBits(int unsigned) {
        return 32 - Integer.numberOfLeadingZeros(unsigned);
    }

    /**
     * Returns an integer with all the bits in its two's complement binary representation that are at index {@code
     * highestBitIndex} or lower set to 1.
     *
     * @param highestBitIndex the index of the highest bit to be set in the returned value. Only the low 5 bits of {@code
     *            highestBitIndex} are used. That is, if {@code highestBitIndex > 31} or {@code highestBitIndex < 0} then
     *            the highest bit to be set is given by {@code highestBitSet & 0x1F}.
     */
    public static int lowBitsSet(int highestBitIndex) {
        final int n = highestBitIndex & 0x1f;
        return (1 << n) | ((1 << n) - 1);
    }

    /**
     * Returns an integer with all the bits in its two's complement binary representation that are at index {@code
     * lowestBitIndex} or higher set to 1.
     *
     * @param lowestBitIndex the index of the lowest bit to be set in the returned value. Only the low 5 bits of {@code
     *            lowestBitIndex} are used. That is, if {@code lowestBitIndex > 31} or {@code lowestBitIndex < 0} then
     *            the lowest bit to be set is given by {@code lowestBitSet & 0x1F}.
     */
    public static int highBitsSet(int lowestBitIndex) {
        return ~((1 << lowestBitIndex) - 1);
    }

    /**
     * Determines if a given number is zero or a power of two.
     */
    public static boolean isPowerOfTwoOrZero(int n) {
        return Integer.lowestOneBit(n) == n;
    }

    public static int log2(int n) {
        if (n <= 0) {
            throw new ArithmeticException();
        }
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public static int roundUp(int value, int by) {
        final int rest = value % by;
        if (rest == 0) {
            return value;
        }
        if (value < 0) {
            return value - rest;
        }
        return value + (by - rest);
    }

    /**
     * Calculates an unsigned integer which is greater than or equal to {@code value} and
     * is a multiple of {@code by}.  Results are undefined if {@code by} is not
     * a power of two.
     * @param value the unsigned integer which is to be rounded upwards.
     * @param by a positive power of two.
     * @return the unsigned integer calculated by rounding upwards to a multiple of {@code by}.
     */
    public static int roundUnsignedUpByPowerOfTwo(int value, int by) {
        assert isPowerOfTwoOrZero(by);
        final int mask = by - 1;
        return (value + mask) & ~mask;
    }

    /**
     * Returns the hexadecimal string representation of the given value with at least 8 digits, e.g. 0x0000CAFE.
     */
    public static String toHexLiteral(int value) {
        return "0x" + toPaddedHexString(value, '0');
    }

    /**
     * Returns the given value as a hexadecimal number with at least 8 digits, e.g. 0000CAFE.
     */
    public static String toPaddedHexString(int n, char pad) {
        final String s = Integer.toHexString(n).toUpperCase();
        return Strings.times(pad, 8 - s.length()) + s;
    }

    public static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static int[] append(int[] array, int element) {
        final int resultLength = array.length + 1;
        final int[] result = new int[resultLength];
        System.arraycopy(array, 0, result, 0, array.length);
        result[array.length] = element;
        return result;
    }

    public static int[] append(int[] head, int[] tail) {
        final int[] result = new int[head.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    public static int[] createRange(int first, int last) {
        if (first > last) {
            throw new IllegalArgumentException();
        }
        final int n = last + 1 - first;
        final int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = first + i;
        }
        return result;
    }

    public static void copyAll(int[] fromArray, int[] toArray) {
        for (int i = 0; i < fromArray.length; i++) {
            toArray[i] = fromArray[i];
        }
    }

    /**
     * Returns a string representation of the contents of the specified array.
     * Adjacent elements are separated by the specified separator. Elements are
     * converted to strings as by <tt>String.valueOf(int)</tt>.
     *
     * @param array     the array whose string representation to return
     * @param separator the separator to use
     * @return a string representation of <tt>array</tt>
     * @throws NullPointerException if {@code array} or {@code separator} is null
     */
    public static String toString(int[] array, String separator) {
        if (array == null || separator == null) {
            throw new NullPointerException();
        }
        if (array.length == 0) {
            return "";
        }

        final StringBuilder buf = new StringBuilder();
        buf.append(array[0]);

        for (int i = 1; i < array.length; i++) {
            buf.append(separator);
            buf.append(array[i]);
        }

        return buf.toString();
    }

    private static final int [] sizeBase10Table = {
        9,
        99,
        999,
        9999,
        99999,
        999999,
        9999999,
        99999999,
        999999999, Integer.MAX_VALUE
    };

    /**
     * Computes the numbers of characters in the base-10 string representation of a given integer, including the '-'
     * prefix for a negative integer. That is, this method computes the length of the String returned by
     * {@link Integer#toString(int)} without requiring a String object to be created.
     *
     * @param i an integer
     * @return the length of the string that would be returned by calling {@link Integer#toString(int)} with {@code i}
     *         as the argument
     */
    public static int sizeOfBase10String(int x) {
        if (x == Integer.MIN_VALUE) {
            return "-2147483648".length();
        }
        final int posX = x < 0 ? -x : x;
        for (int i = 0;; i++) {
            if (posX <= sizeBase10Table[i]) {
                if (x < 0) {
                    return i + 2;
                }
                return i + 1;
            }
        }
    }

    /**
     * @see Longs#toUnitsString(long, boolean)
     */
    public static String toUnitsString(long number, boolean onlyPowerOfTwo) {
        return Longs.toUnitsString(number, onlyPowerOfTwo);
    }

    /**
     * Computes the minimum value in an array of integers.
     *
     * @param ints the array of integers from which the minimum is computed. This array must have at least one element.
     * @return the minimum value in {@code ints}
     * @throws ArrayIndexOutOfBoundsException if {@code ints.length == 0}
     */
    public static int min(int[] ints) {
        int min = ints[0];
        for (int n : ints) {
            if (n < min) {
                min = n;
            }
        }
        return min;
    }

    /**
     * Computes the maximum value in an array of integers.
     *
     * @param ints the array of integers from which the maximum is computed. This array must have at least one element.
     * @return the maximum value in {@code ints}
     * @throws ArrayIndexOutOfBoundsException if {@code ints.length == 0}
     */
    public static int max(int[] ints) {
        int max = ints[0];
        for (int n : ints) {
            if (n > max) {
                max = n;
            }
        }
        return max;
    }

}
