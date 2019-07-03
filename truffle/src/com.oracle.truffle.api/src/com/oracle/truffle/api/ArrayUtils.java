/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * This class provides additional operations for {@link String} as well as character and byte
 * arrays, which may be intrinsified by a compiler.
 *
 * @since 19.0
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
     * @since 19.0
     */
    public static int indexOf(String haystack, int fromIndex, int maxIndex, char... needle) {
        checkArgs(haystack.length(), fromIndex, maxIndex, needle.length);
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
     * @since 19.0
     */
    public static int indexOf(char[] haystack, int fromIndex, int maxIndex, char... needle) {
        checkArgs(haystack.length, fromIndex, maxIndex, needle.length);
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
     * @since 19.0
     */
    public static int indexOf(byte[] haystack, int fromIndex, int maxIndex, byte... needle) {
        checkArgs(haystack.length, fromIndex, maxIndex, needle.length);
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

    private static void checkArgs(int length, int fromIndex, int maxIndex, int nValues) {
        if (fromIndex < 0) {
            throw new IllegalArgumentException("fromIndex must be positive");
        }
        if (maxIndex > length || maxIndex < fromIndex) {
            throw new IllegalArgumentException("maxIndex out of range");
        }
        if (nValues == 0) {
            throw new IllegalArgumentException("no search values provided");
        }
    }
}
