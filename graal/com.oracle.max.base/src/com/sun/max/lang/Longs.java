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

/**
 * Additional methods that one might want in java.lang.Long.
 */
public final class Longs {

    private Longs() {
    }

    public static final int SIZE = 8;
    public static final int WIDTH = 64;

    public static final long INT_MASK = 0xffffffffL;

    public static int compare(long greater, long lesser) {
        if (greater > lesser) {
            return 1;
        }
        if (greater == lesser) {
            return 0;
        }
        return -1;
    }

    public static int numberOfEffectiveSignedBits(long signed) {
        if (signed >= 0) {
            return 65 - Long.numberOfLeadingZeros(signed);
        }
        return 65 - Long.numberOfLeadingZeros(~signed);
    }

    public static int numberOfEffectiveUnsignedBits(long unsigned) {
        return 64 - Long.numberOfLeadingZeros(unsigned);
    }

    public static byte getByte(long value, int index) {
        return (byte) ((value >> (index * 8)) & 0xffL);
    }

    public static String toPaddedHexString(long n, char pad) {
        final String s = Long.toHexString(n);
        return Strings.times(pad, 16 - s.length()) + s;
    }

    /**
     * Determines if a given number is zero or a power of two.
     */
    public static boolean isPowerOfTwoOrZero(long n) {
        return Long.lowestOneBit(n) == n;
    }

    public static final long K = 1024;
    public static final long M = K * K;
    public static final long G = M * K;
    public static final long T = G * K;
    public static final long P = T * K;

    /**
     * Converts a positive number to a string using unit suffixes to reduce the
     * number of digits to three or less using base 2 for sizes.
     *
     * @param number the number to convert to a string
     * @param onlyPowerOfTwo if {@code true}, then a unit suffix is only used if {@code number} is an exact power of 2
     */
    public static String toUnitsString(long number, boolean onlyPowerOfTwo) {
        if (number < 0) {
            throw new IllegalArgumentException(String.valueOf(number));
        }
        if (onlyPowerOfTwo && !isPowerOfTwoOrZero(number)) {
            return String.valueOf(number);
        }
        if (number >= P) {
            return number / P + "P";
        }
        if (number >= T) {
            return number / T + "T";
        }
        if (number >= G) {
            return number / G + "G";
        }
        if (number >= M) {
            return number / M + "M";
        }
        if (number >= K) {
            return number / K + "K";
        }
        return Long.toString(number);
    }

    /**
     * Parse a size specification nX, where X := {K, M, G, T, P, k, m, g, t, p}.
     *
     * @param value a string containing a long number that can be parsed by {@link Long#parseLong(String)} followed by
     *            an optional scaling character
     * @return the scaled value
     * @throws NumberFormatException if {@code value} does not contain a parsable {@code long} or has an invalid scale
     *             suffix
     */
    public static long parseScaledValue(String value) throws NumberFormatException {
        char lastChar = value.charAt(value.length() - 1);
        if (!Character.isDigit(lastChar)) {
            long result = Long.parseLong(value.substring(0, value.length() - 1));
            switch (lastChar) {
                case 'K':
                case 'k': {
                    return result * Longs.K;
                }
                case 'M':
                case 'm': {
                    return result * Longs.M;
                }
                case 'G':
                case 'g': {
                    return result * Longs.G;
                }
                case 'T':
                case 't': {
                    return result * Longs.T;
                }
                case 'P':
                case 'p': {
                    return result * Longs.P;
                }
                default: {
                    throw new NumberFormatException("Number with unknown scale suffix: " + value);
                }
            }
        }
        return Long.parseLong(value);
    }
}
