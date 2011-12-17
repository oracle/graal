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

import java.io.*;

import com.sun.max.io.*;
import com.sun.max.util.*;

/**
 * Byte and byte array operations.
 */
public final class Bytes {

    private Bytes() {
    }

    public static final int SIZE = 1;
    public static final int WIDTH = 8;
    public static final int MASK = 0xff;

    public static final Range VALUE_RANGE = new Range(Byte.MIN_VALUE, Byte.MAX_VALUE);

    public static int zeroExtendedToInt(byte b) {
        final char ch = (char) b;
        return ch;
    }

    /**
     * Returns the number of zero bits following the lowest-order ("rightmost")
     * one-bit in the two's complement binary representation of the specified
     * {@code byte} value.  Returns 8 if the specified value has no
     * one-bits in its two's complement representation, in other words if it is
     * equal to zero.
     *
     * @return the number of zero bits following the lowest-order ("rightmost")
     *     one-bit in the two's complement binary representation of the
     *     specified {@code byte} value, or 8 if the value is equal
     *     to zero.
     */
    public static int numberOfTrailingZeros(byte b) {
        // HD, Figure 5-14
        int y;
        int i = b & 0xFF;
        if (i == 0) {
            return 8;
        }
        int n = 7;
        y = (i << 4) & 0xFF;
        if (y != 0) {
            n = n - 4;
            i = y;
        }
        y = (i << 2) & 0xFF;
        if (y != 0) {
            n = n - 2;
            i = y;
        }
        return n - (((i << 1) & 0xFF) >>> 7);
    }

    /**
     * Are the bytes of an array, starting at some position, equal to the contents
     * of a second array.
     * @param array1 An array of bytes
     * @param startIndex1  Index in {@code array1} at which to start comparison
     * @param array2 An array of bytes to be compared
     * @return tree iff the entire contents of {@code array2} are equal to
     * the contents of {@code array1}, starting at {@code startIndex1}.
     */
    public static boolean equals(byte[] array1, int startIndex1, byte[] array2) {
        if (array1.length < startIndex1 + array2.length) {
            return false;
        }
        for (int i = 0; i < array2.length; i++) {
            if (array1[startIndex1 + i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the first {@code length} bytes in two byte arrays
     * for equality.
     */
    public static boolean equals(byte[] array1, byte[] array2, int length) {
        if (array1.length < length || array2.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the contents of two byte arrays for equality.
     */
    public static boolean equals(byte[] array1, byte[] array2) {
        if (array1 == array2) {
            return true;
        }
        if (array1.length != array2.length) {
            return false;
        }
        return equals(array1, array2, array1.length);
    }

    public static void copy(byte[] fromArray, int fromStartIndex, byte[] toArray,
                            final int toStartIndex, int nBytes) {
        for (int i = 0; i < nBytes; i++) {
            toArray[toStartIndex + i] = fromArray[fromStartIndex + i];
        }
    }

    public static void copy(byte[] fromArray, byte[] toArray, int nBytes) {
        copy(fromArray, 0, toArray, 0, nBytes);
    }

    public static void copyAll(byte[] fromArray, byte[] toArray, int toStartIndex) {
        copy(fromArray, 0, toArray, toStartIndex, fromArray.length);
    }

    public static void copyAll(byte[] fromArray, byte[] toArray) {
        copy(fromArray, 0, toArray, 0, fromArray.length);
    }

    public static byte[] withLength(byte[] array, int length) {
        final byte[] result = new byte[length];
        if (length >= array.length) {
            copyAll(array, result);
        } else {
            copy(array, result, length);
        }
        return result;
    }

    public static byte[] getSection(byte[] fromArray, int startIndex, int endIndex) {
        final int length = endIndex - startIndex;
        final byte[] result = new byte[length];
        copy(fromArray, startIndex, result, 0, length);
        return result;
    }

    /**
     * Assigns zero to every byte in an array.
     */
    public static void clear(byte[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) 0;
        }
    }

    public static boolean areClear(byte[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] != (byte) 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param value  a byte
     * @return A string representation of the byte in hexadecimal, e.g. "0x0A"
     */
    public static String toHexLiteral(byte value) {
        String hexString = Integer.toHexString(value).toUpperCase();
        if (hexString.length() == 1) {
            hexString = "0" + hexString;
        } else if (hexString.length() > 2) {
            hexString = hexString.substring(hexString.length() - 2);
        }
        return "0x" + hexString;
    }

    /**
     * @param values an array of bytes
     * @return A string representation of the bytes in hexadecimal, e.g. "0x01A203"
     */
    public static String toHexLiteral(byte[] values) {
        String s = "0x";
        for (byte value : values) {
            String hexString = Integer.toHexString(value).toUpperCase();
            if (hexString.length() == 1) {
                hexString = "0" + hexString;
            }
            s += hexString;
        }
        return s;
    }

    /**
     * Returns a string representation of the contents of the specified array.
     * Adjacent elements are separated by the specified separator. Elements are
     * converted to strings with {@link #toHexLiteral(byte)}.
     *
     * @param array     the array whose string representation to return
     * @param separator the separator to use
     * @return a string representation of <tt>array</tt>
     * @throws NullPointerException if {@code array} or {@code separator} is null
     */
    public static String toHexString(byte[] array, String separator) {
        if (array == null || separator == null) {
            throw new NullPointerException();
        }
        if (array.length == 0) {
            return "";
        }

        final StringBuilder buf = new StringBuilder();
        buf.append(toHexLiteral(array[0]));

        for (int i = 1; i < array.length; i++) {
            buf.append(separator);
            buf.append(toHexLiteral(array[i]));
        }

        return buf.toString();
    }

    public static byte[] fromInputStream(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Streams.copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }

}
