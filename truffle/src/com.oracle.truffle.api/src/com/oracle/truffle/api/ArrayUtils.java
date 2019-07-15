/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

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

    /**
     * Returns the index of the first element of {@code haystack} where
     * {@code (haystack[index] | mask) == needle}, bounded by {@code fromIndex} (inclusive) and
     * {@code maxIndex} (exclusive).
     *
     * @return the index of the first element of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex && (haystack[index] | mask) == needle}
     *         holds, or {@code -1} if no such element is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(byte[] haystack, int fromIndex, int maxIndex, byte needle, byte mask) {
        checkArgs(haystack.length, fromIndex, maxIndex, 1);
        return runIndexOfWithORMask(haystack, fromIndex, maxIndex, needle, mask);
    }

    private static int runIndexOfWithORMask(byte[] haystack, int fromIndex, int maxIndex, byte needle, byte mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((haystack[i] | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first element of {@code haystack} where
     * {@code (haystack[index] | mask) == needle}, bounded by {@code fromIndex} (inclusive) and
     * {@code maxIndex} (exclusive).
     *
     * @return the index of the first element of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex && (haystack[index] | mask) == needle}
     *         holds, or {@code -1} if no such element is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(char[] haystack, int fromIndex, int maxIndex, char needle, char mask) {
        checkArgs(haystack.length, fromIndex, maxIndex, 1);
        return runIndexOfWithORMask(haystack, fromIndex, maxIndex, needle, mask);
    }

    private static int runIndexOfWithORMask(char[] haystack, int fromIndex, int maxIndex, char needle, char mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((haystack[i] | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first element of {@code haystack} where
     * {@code (haystack.charAt(index) | mask) == needle}, bounded by {@code fromIndex} (inclusive)
     * and {@code maxIndex} (exclusive).
     *
     * @return the index of the first element of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex && (haystack.charAt(index) | mask) == needle}
     *         holds, or {@code -1} if no such element is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(String haystack, int fromIndex, int maxIndex, char needle, char mask) {
        checkArgs(haystack.length(), fromIndex, maxIndex, 1);
        return runIndexOfWithORMask(haystack, fromIndex, maxIndex, needle, mask);
    }

    private static int runIndexOfWithORMask(String haystack, int fromIndex, int maxIndex, char needle, char mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((haystack.charAt(i) | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first two consecutive elements of {@code haystack} where
     * {@code (haystack[index] | mask1) == c1 && (haystack[index + 1] | mask2) == c2}, bounded by
     * {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first two consecutive elements of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex - 1 && (haystack[index] | mask1) == c1 && (haystack[index + 1] | mask2) == c2}
     *         holds, or {@code -1} if no such elements are found.
     * @since 19.2
     */
    public static int indexOf2ConsecutiveWithORMask(byte[] haystack, int fromIndex, int maxIndex, byte c1, byte c2, byte mask1, byte mask2) {
        checkArgs(haystack.length, fromIndex, maxIndex, 1);
        return runIndexOf2ConsecutiveWithORMask(haystack, fromIndex, maxIndex, c1, c2, mask1, mask2);
    }

    private static int runIndexOf2ConsecutiveWithORMask(byte[] haystack, int fromIndex, int maxIndex, byte c1, byte c2, byte mask1, byte mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((haystack[i - 1] | mask1) == c1 && (haystack[i] | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first two consecutive elements of {@code haystack} where
     * {@code (haystack[index] | mask1) == c1 && (haystack[index + 1] | mask2) == c2}, bounded by
     * {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first two consecutive elements of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex - 1 && (haystack[index] | mask1) == c1 && (haystack[index + 1] | mask2) == c2}
     *         holds, or {@code -1} if no such elements are found.
     * @since 19.2
     */
    public static int indexOf2ConsecutiveWithORMask(char[] haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        checkArgs(haystack.length, fromIndex, maxIndex, 1);
        return runIndexOf2ConsecutiveWithORMask(haystack, fromIndex, maxIndex, c1, c2, mask1, mask2);
    }

    private static int runIndexOf2ConsecutiveWithORMask(char[] haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((haystack[i - 1] | mask1) == c1 && (haystack[i] | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first two consecutive elements of {@code haystack} where
     * {@code (haystack.charAt(index) | mask1) == c1 && (haystack.charAt(index + 1) | mask2) == c2},
     * bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first two consecutive elements of {@code haystack} where
     *         {@code fromIndex <= index && index < maxIndex - 1 && (haystack.charAt(index) | mask1) == c1 && (haystack.charAt(index + 1) | mask2) == c2}
     *         holds, or {@code -1} if no such elements are found.
     * @since 19.2
     */
    public static int indexOf2ConsecutiveWithORMask(String haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        checkArgs(haystack.length(), fromIndex, maxIndex, 1);
        return runIndexOf2ConsecutiveWithORMask(haystack, fromIndex, maxIndex, c1, c2, mask1, mask2);
    }

    private static int runIndexOf2ConsecutiveWithORMask(String haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((haystack.charAt(i - 1) | mask1) == c1 && (haystack.charAt(i) | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex}
     * (exclusive).
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle} {@code (haystack[index + i] | mask[i]) == needle[i]} holds, and
     *         {@code fromIndex <= index && index < maxIndex} holds, or {@code -1} if no such region
     *         is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(byte[] haystack, int fromIndex, int maxIndex, byte[] needle, byte[] mask) {
        checkArgsIndexOf(haystack.length, fromIndex, maxIndex, needle.length, mask.length);
        if (needle.length == 0) {
            return fromIndex;
        }
        if (maxIndex - fromIndex - needle.length < 0) {
            return -1;
        } else if (needle.length == 1) {
            return indexOfWithORMask(haystack, fromIndex, maxIndex, needle[0], mask[0]);
        } else {
            int max = maxIndex - (needle.length - 2);
            int index = fromIndex;
            while (index < max) {
                index = indexOf2ConsecutiveWithORMask(haystack, index, max, needle[0], needle[1], mask[0], mask[1]);
                if (index < 0) {
                    return -1;
                }
                if (mask.length == 2 || regionEqualsWithORMask(haystack, index, needle, 0, mask)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex}
     * (exclusive).
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle} {@code (haystack[index + i] | mask[i]) == needle[i]} holds, and
     *         {@code fromIndex <= index && index < maxIndex} holds, or {@code -1} if no such region
     *         is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(char[] haystack, int fromIndex, int maxIndex, char[] needle, char[] mask) {
        checkArgsIndexOf(haystack.length, fromIndex, maxIndex, needle.length, mask.length);
        if (needle.length == 0) {
            return fromIndex;
        }
        if (maxIndex - fromIndex - needle.length < 0) {
            return -1;
        } else if (needle.length == 1) {
            return indexOfWithORMask(haystack, fromIndex, maxIndex, needle[0], mask[0]);
        } else {
            int max = maxIndex - (needle.length - 2);
            int index = fromIndex;
            while (index < max) {
                index = indexOf2ConsecutiveWithORMask(haystack, index, max, needle[0], needle[1], mask[0], mask[1]);
                if (index < 0) {
                    return -1;
                }
                if (mask.length == 2 || regionEqualsWithORMask(haystack, index, needle, 0, mask)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex}
     * (exclusive).
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle}
     *         {@code (haystack.charAt(index + i) | mask.charAt(i)) == needle.charAt(i)} holds, and
     *         {@code fromIndex <= index && index < maxIndex} holds, or {@code -1} if no such region
     *         is found.
     * @since 19.2
     */
    public static int indexOfWithORMask(String haystack, int fromIndex, int maxIndex, String needle, String mask) {
        checkArgsIndexOf(haystack.length(), fromIndex, maxIndex, needle.length(), mask.length());
        if (needle.isEmpty()) {
            return fromIndex;
        }
        if (maxIndex - fromIndex - needle.length() < 0) {
            return -1;
        } else if (needle.length() == 1) {
            return indexOfWithORMask(haystack, fromIndex, maxIndex, needle.charAt(0), mask.charAt(0));
        } else {
            int max = maxIndex - (needle.length() - 2);
            int index = fromIndex;
            while (index < max) {
                index = indexOf2ConsecutiveWithORMask(haystack, index, max, needle.charAt(0), needle.charAt(1), mask.charAt(0), mask.charAt(1));
                if (index < 0) {
                    return -1;
                }
                if (mask.length() == 2 || regionEqualsWithORMask(haystack, index, needle, 0, mask)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }

    private static void checkArgsIndexOf(int hayStackLength, int fromIndex, int maxIndex, int needleLength, int maskLength) {
        if (fromIndex < 0) {
            throw new IllegalArgumentException("fromIndex must be positive");
        }
        if (maxIndex > hayStackLength || maxIndex < fromIndex) {
            throw new IllegalArgumentException("maxIndex out of range");
        }
        if (needleLength != maskLength) {
            throw new IllegalArgumentException("mask and needle length must be equal");
        }
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive), {@code a1[fromIndex1 + i] == a2[fromIndex2 + i]} holds.
     *
     * @since 19.2
     */
    public static boolean regionEquals(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, length);
        if (regionEqualsOutOfBounds(a1.length, fromIndex1, a2.length, fromIndex2, length)) {
            return false;
        }
        return runRegionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }

    private static boolean runRegionEquals(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length) {
        for (int i = 0; i < length; i++) {
            if (a1[fromIndex1 + i] != a2[fromIndex2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive), {@code a1[fromIndex1 + i] == a2[fromIndex2 + i]} holds.
     *
     * @since 19.2
     */
    public static boolean regionEquals(char[] a1, int fromIndex1, char[] a2, int fromIndex2, int length) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, length);
        if (regionEqualsOutOfBounds(a1.length, fromIndex1, a2.length, fromIndex2, length)) {
            return false;
        }
        return runRegionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }

    private static boolean runRegionEquals(char[] a1, int fromIndex1, char[] a2, int fromIndex2, int length) {
        for (int i = 0; i < length; i++) {
            if (a1[fromIndex1 + i] != a2[fromIndex2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive), {@code a1.charAt(fromIndex1 + i) == a2.charAt(fromIndex2 + i)}
     * holds.
     *
     * @since 19.2
     */
    public static boolean regionEquals(String a1, int fromIndex1, String a2, int fromIndex2, int length) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, length);
        if (regionEqualsOutOfBounds(a1.length(), fromIndex1, a2.length(), fromIndex2, length)) {
            return false;
        }
        return runRegionEquals(a1, fromIndex1, a2, fromIndex2, length);
    }

    private static boolean runRegionEquals(String a1, int fromIndex1, String a2, int fromIndex2, int length) {
        for (int i = 0; i < length; i++) {
            if (a1.charAt(fromIndex1 + i) != a2.charAt(fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code mask.length} (exclusive), {@code (a1[fromIndex1 + i] | mask[i]) == a2[fromIndex2 + i]}
     * holds.
     *
     * @since 19.2
     */
    public static boolean regionEqualsWithORMask(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, byte[] mask) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, mask.length);
        if (regionEqualsOutOfBounds(a1.length, fromIndex1, a2.length, fromIndex2, mask.length)) {
            return false;
        }
        return runRegionEqualsWithORMask(a1, fromIndex1, a2, fromIndex2, mask);
    }

    private static boolean runRegionEqualsWithORMask(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, byte[] mask) {
        for (int i = 0; i < mask.length; i++) {
            if ((a1[fromIndex1 + i] | mask[i]) != a2[fromIndex2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code mask.length} (exclusive), {@code (a1[fromIndex1 + i] | mask[i]) == a2[fromIndex2 + i]}
     * holds.
     *
     * @since 19.2
     */
    public static boolean regionEqualsWithORMask(char[] a1, int fromIndex1, char[] a2, int fromIndex2, char[] mask) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, mask.length);
        if (regionEqualsOutOfBounds(a1.length, fromIndex1, a2.length, fromIndex2, mask.length)) {
            return false;
        }
        return runRegionEqualsWithORMask(a1, fromIndex1, a2, fromIndex2, mask);
    }

    private static boolean runRegionEqualsWithORMask(char[] a1, int fromIndex1, char[] a2, int fromIndex2, char[] mask) {
        for (int i = 0; i < mask.length; i++) {
            if ((a1[fromIndex1 + i] | mask[i]) != a2[fromIndex2 + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code mask.length} (exclusive),
     * {@code (a1.charAt(fromIndex1 + i) | mask.charAt(i)) == a2.charAt(fromIndex2 + i)} holds.
     *
     * @since 19.2
     */
    public static boolean regionEqualsWithORMask(String a1, int fromIndex1, String a2, int fromIndex2, String mask) {
        Objects.requireNonNull(a1);
        Objects.requireNonNull(a2);
        checkArgsRegionEquals(fromIndex1, fromIndex2, mask.length());
        if (regionEqualsOutOfBounds(a1.length(), fromIndex1, a2.length(), fromIndex2, mask.length())) {
            return false;
        }
        return runRegionEqualsWithORMask(a1, fromIndex1, a2, fromIndex2, mask);
    }

    private static boolean runRegionEqualsWithORMask(String a1, int fromIndex1, String a2, int fromIndex2, String mask) {
        for (int i = 0; i < mask.length(); i++) {
            if ((a1.charAt(fromIndex1 + i) | mask.charAt(i)) != a2.charAt(fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }

    private static void checkArgsRegionEquals(int fromIndex1, int fromIndex2, int length) {
        if (fromIndex1 < 0 || fromIndex2 < 0 || length < 0) {
            throw new IllegalArgumentException("length, fromIndex1 and fromIndex2 must be positive");
        }
    }

    private static boolean regionEqualsOutOfBounds(int a1Length, int fromIndex1, int a2Length, int fromIndex2, int length) {
        return a1Length - fromIndex1 < length || a2Length - fromIndex2 < length;
    }
}
