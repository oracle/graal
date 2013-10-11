/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.util;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;

/**
 * The {@code Util} class contains a motley collection of utility methods used throughout the
 * compiler.
 */
public class Util {

    public static final int PRINTING_LINE_WIDTH = 40;
    public static final char SECTION_CHARACTER = '*';
    public static final char SUB_SECTION_CHARACTER = '=';
    public static final char SEPERATOR_CHARACTER = '-';

    public static <T> boolean replaceInList(T a, T b, List<T> list) {
        final int max = list.size();
        for (int i = 0; i < max; i++) {
            if (list.get(i) == a) {
                list.set(i, b);
                return true;
            }
        }
        return false;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(@SuppressWarnings("unused") Class<T> type, Object object) {
        return (T) object;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     * 
     * @param hash the base hash
     * @param x the object to add to the hash
     * @return the combined hash
     */
    public static int hash1(int hash, Object x) {
        // always set at least one bit in case the hash wraps to zero
        return 0x10000000 | (hash + 7 * System.identityHashCode(x));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     * 
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @return the combined hash
     */
    public static int hash2(int hash, Object x, Object y) {
        // always set at least one bit in case the hash wraps to zero
        return 0x20000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     * 
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @return the combined hash
     */
    public static int hash3(int hash, Object x, Object y, Object z) {
        // always set at least one bit in case the hash wraps to zero
        return 0x30000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     * 
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @param w the fourth object to add to the hash
     * @return the combined hash
     */
    public static int hash4(int hash, Object x, Object y, Object z, Object w) {
        // always set at least one bit in case the hash wraps to zero
        return 0x40000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z) + 17 * System.identityHashCode(w));
    }

    static {
        assert CodeUtil.log2(2) == 1;
        assert CodeUtil.log2(4) == 2;
        assert CodeUtil.log2(8) == 3;
        assert CodeUtil.log2(16) == 4;
        assert CodeUtil.log2(32) == 5;
        assert CodeUtil.log2(0x40000000) == 30;

        assert CodeUtil.log2(2L) == 1;
        assert CodeUtil.log2(4L) == 2;
        assert CodeUtil.log2(8L) == 3;
        assert CodeUtil.log2(16L) == 4;
        assert CodeUtil.log2(32L) == 5;
        assert CodeUtil.log2(0x4000000000000000L) == 62;

        assert !CodeUtil.isPowerOf2(3);
        assert !CodeUtil.isPowerOf2(5);
        assert !CodeUtil.isPowerOf2(7);
        assert !CodeUtil.isPowerOf2(-1);

        assert CodeUtil.isPowerOf2(2);
        assert CodeUtil.isPowerOf2(4);
        assert CodeUtil.isPowerOf2(8);
        assert CodeUtil.isPowerOf2(16);
        assert CodeUtil.isPowerOf2(32);
        assert CodeUtil.isPowerOf2(64);
    }

    /**
     * Sets the element at a given position of a list and ensures that this position exists. If the
     * list is current shorter than the position, intermediate positions are filled with a given
     * value.
     * 
     * @param list the list to put the element into
     * @param pos the position at which to insert the element
     * @param x the element that should be inserted
     * @param filler the filler element that is used for the intermediate positions in case the list
     *            is shorter than pos
     */
    public static <T> void atPutGrow(List<T> list, int pos, T x, T filler) {
        if (list.size() < pos + 1) {
            while (list.size() < pos + 1) {
                list.add(filler);
            }
            assert list.size() == pos + 1;
        }

        assert list.size() >= pos + 1;
        list.set(pos, x);
    }

    public static void breakpoint() {
        // do nothing.
    }

    public static void guarantee(boolean b, String string) {
        if (!b) {
            throw new BailoutException(string);
        }
    }

    public static void warning(String string) {
        TTY.println("WARNING: " + string);
    }

    public static int safeToInt(long l) {
        assert (int) l == l;
        return (int) l;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static void printSection(String name, char sectionCharacter) {

        String header = " " + name + " ";
        int remainingCharacters = PRINTING_LINE_WIDTH - header.length();
        int leftPart = remainingCharacters / 2;
        int rightPart = remainingCharacters - leftPart;
        for (int i = 0; i < leftPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.print(header);

        for (int i = 0; i < rightPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.println();
    }

    /**
     * Prints entries in a byte array as space separated hex values to {@link TTY}.
     * 
     * @param address an address at which the bytes are located. This is used to print an address
     *            prefix per line of output.
     * @param array the array containing all the bytes to print
     * @param bytesPerLine the number of values to print per line of output
     */
    public static void printBytes(long address, byte[] array, int bytesPerLine) {
        printBytes(address, array, 0, array.length, bytesPerLine);
    }

    /**
     * Prints entries in a byte array as space separated hex values to {@link TTY}.
     * 
     * @param address an address at which the bytes are located. This is used to print an address
     *            prefix per line of output.
     * @param array the array containing the bytes to print
     * @param offset the offset in {@code array} of the values to print
     * @param length the number of values from {@code array} print
     * @param bytesPerLine the number of values to print per line of output
     */
    public static void printBytes(long address, byte[] array, int offset, int length, int bytesPerLine) {
        assert bytesPerLine > 0;
        boolean newLine = true;
        for (int i = 0; i < length; i++) {
            if (newLine) {
                TTY.print("%08x: ", address + i);
                newLine = false;
            }
            TTY.print("%02x ", array[i]);
            if (i % bytesPerLine == bytesPerLine - 1) {
                TTY.println();
                newLine = true;
            }
        }

        if (length % bytesPerLine != bytesPerLine) {
            TTY.println();
        }
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

    public static boolean is32bit(long x) {
        return -0x80000000L <= x && x < 0x80000000L;
    }

    public static short safeToShort(int v) {
        assert isShort(v);
        return (short) v;
    }

    /**
     * Creates an array of integers of length "size", in which each number from 0 to (size - 1)
     * occurs exactly once. The integers are sorted using the given comparator. This can be used to
     * create a sorting for arrays that cannot be modified directly.
     * 
     * @param size The size of the range to be sorted.
     * @param comparator A comparator that is used to compare indexes.
     * @return An array of integers that contains each number from 0 to (size - 1) exactly once,
     *         sorted using the comparator.
     */
    public static Integer[] createSortedPermutation(int size, Comparator<Integer> comparator) {
        Integer[] indexes = new Integer[size];
        for (int i = 0; i < size; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, comparator);
        return indexes;
    }
}
