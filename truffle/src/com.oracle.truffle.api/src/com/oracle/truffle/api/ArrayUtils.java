/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * This class provides additional operations for {@link String} as well as character and byte
 * arrays, which may be intrinsified by a compiler.
 *
 * @since 1.0
 */
public final class ArrayUtils {

    private ArrayUtils() {
    }

    /**
     * Returns the index of the first occurrence of any character contained in {@code needle} in
     * {@code haystack}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any character contained in {@code needle} in
     *         {@code haystack} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the characters occur.
     * @since 1.0
     */
    public static int indexOf(String haystack, int fromIndex, int maxIndex, char... needle) {
        checkArgs(haystack.length(), fromIndex, maxIndex);
        return runIndexOf(haystack, fromIndex, maxIndex, needle);
    }

    private static int runIndexOf(String haystack, int fromIndex, int maxIndex, char[] needle) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (char c : needle) {
                if (haystack.charAt(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first occurrence of any character contained in {@code needle} in
     * {@code haystack}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any character contained in {@code needle} in
     *         {@code haystack} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the characters occur.
     * @since 1.0
     */
    public static int indexOf(char[] haystack, int fromIndex, int maxIndex, char... needle) {
        checkArgs(haystack.length, fromIndex, maxIndex);
        return runIndexOf(haystack, fromIndex, maxIndex, needle);
    }

    private static int runIndexOf(char[] haystack, int fromIndex, int maxIndex, char[] needle) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (char c : needle) {
                if (haystack[i] == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first occurrence of any byte contained in {@code needle} in
     * {@code haystack}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any byte contained in {@code needle} in
     *         {@code haystack} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the needle occur.
     * @since 1.0
     */
    public static int indexOf(byte[] haystack, int fromIndex, int maxIndex, byte... needle) {
        checkArgs(haystack.length, fromIndex, maxIndex);
        return runIndexOf(haystack, fromIndex, maxIndex, needle);
    }

    private static int runIndexOf(byte[] haystack, int fromIndex, int maxIndex, byte[] needle) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (byte c : needle) {
                if (haystack[i] == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void checkArgs(int length, int fromIndex, int maxIndex) {
        if (fromIndex < 0) {
            throw new IllegalArgumentException("fromIndex must be positive");
        }
        if (maxIndex > length || maxIndex < fromIndex) {
            throw new IllegalArgumentException("maxIndex out of range");
        }
    }
}
