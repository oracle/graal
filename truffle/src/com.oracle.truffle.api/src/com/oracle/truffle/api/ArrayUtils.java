/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import sun.misc.Unsafe;

/**
 * This class provides additional operations for {@link String} as well as character and byte
 * arrays, which may be intrinsified by a compiler.
 *
 * @since 19.0
 */
public final class ArrayUtils {

    private ArrayUtils() {
    }

    @SuppressWarnings("deprecation"/* JDK-8277863 */)
    static long getObjectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long javaStringValueFieldOffset;
    private static final long javaStringCoderFieldOffset;

    static {
        Field valueField = getStringDeclaredField("value");
        javaStringValueFieldOffset = getObjectFieldOffset(valueField);
        Field coderField = getStringDeclaredField("coder");
        javaStringCoderFieldOffset = getObjectFieldOffset(coderField);
    }

    private static Field getStringDeclaredField(String name) {
        try {
            return String.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("failed to get " + name + " field offset", e);
        }
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e1) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
    }

    private static byte[] getJavaStringArray(String str) {
        Object value = UNSAFE.getObject(str, javaStringValueFieldOffset);
        assert value instanceof byte[];
        return (byte[]) value;
    }

    private static boolean isCompactString(String s) {
        return UNSAFE.getByte(s, javaStringCoderFieldOffset) == 0;
    }

    /**
     * Returns the index of the first occurrence of any character contained in {@code values} in
     * {@code string}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any character contained in {@code values} in
     *         {@code string} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the characters occur.
     * @since 19.0
     */
    public static int indexOf(String string, int fromIndex, int maxIndex, char... values) {
        checkArgs(string.length(), fromIndex, maxIndex, values.length);
        if (fromIndex >= string.length()) {
            return -1;
        }
        if (values.length <= 4) {
            if (isCompactString(string)) {
                int valuesInt = 0;
                int nValues = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0xff) {
                        valuesInt = (valuesInt << 8) | values[i];
                        nValues++;
                    }
                }
                return nValues == 0 ? -1 : indexOfBS1(getJavaStringArray(string), fromIndex, maxIndex, valuesInt, nValues);
            } else {
                return indexOfBS2(getJavaStringArray(string), fromIndex, maxIndex, values, values.length);
            }
        }
        return runIndexOfS2(string, fromIndex, maxIndex, values);
    }

    /**
     * Returns the index of the first occurrence of any character contained in {@code values} in
     * {@code array}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any character contained in {@code values} in
     *         {@code array} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the characters occur.
     * @since 19.0
     */
    public static int indexOf(char[] array, int fromIndex, int maxIndex, char... values) {
        checkArgs(array.length, fromIndex, maxIndex, values.length);
        if (fromIndex >= array.length) {
            return -1;
        }
        if (values.length <= 4) {
            return indexOfCS2(array, fromIndex, maxIndex, values, values.length);
        }
        return runIndexOfS2(array, fromIndex, maxIndex, values);
    }

    /**
     * Returns the index of the first occurrence of any byte contained in {@code values} in
     * {@code array}, bounded by {@code fromIndex} (inclusive) and {@code maxIndex} (exclusive).
     *
     * @return the index of the first occurrence of any byte contained in {@code values} in
     *         {@code array} that is greater than or equal to {@code fromIndex} and less than
     *         {@code maxIndex}, or {@code -1} if none of the values occur.
     * @since 19.0
     */
    public static int indexOf(byte[] array, int fromIndex, int maxIndex, byte... values) {
        checkArgs(array.length, fromIndex, maxIndex, values.length);
        if (fromIndex >= array.length) {
            return -1;
        }
        if (values.length <= 4) {
            return indexOfBS1(array, fromIndex, maxIndex, values, values.length);
        }
        return runIndexOfS1(array, fromIndex, maxIndex, values);
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code length}.
     * Performs best if {@code needle} and {@code mask} are {@link CompilationFinal} with
     * {@code dimensions = 1}.
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle} {@code (haystack[index + i] | mask[i]) == needle[i]} holds, and
     *         {@code fromIndex <= index && index + needle.length <= fromIndex + length} holds, or
     *         {@code -1} if no such region is found.
     * @since 19.3
     */
    public static int indexOfWithOrMask(byte[] haystack, int fromIndex, int length, byte[] needle, byte[] mask) {
        checkArgsIndexOf(haystack.length, fromIndex, length);
        if (mask != null) {
            checkMaskLengthIndexOf(needle.length, mask.length);
        }
        if (needle.length == 0) {
            return fromIndex;
        }
        if (length - needle.length < 0) {
            return -1;
        } else if (needle.length == 1) {
            if (mask == null) {
                return stubIndexOfB1S1(haystack, fromIndex, fromIndex + length, Byte.toUnsignedInt(needle[0]));
            }
            return stubIndexOfWithOrMaskS1(haystack, fromIndex, fromIndex + length, Byte.toUnsignedInt(needle[0]), Byte.toUnsignedInt(mask[0]));
        } else {
            int max = fromIndex + length - (needle.length - 2);
            int index = fromIndex;
            if (mask == null) {
                while (index < max - 1) {
                    index = stubIndexOf2ConsecutiveS1(haystack, index, max, Byte.toUnsignedInt(needle[0]), Byte.toUnsignedInt(needle[1]));
                    if (index < 0) {
                        return -1;
                    }
                    if (needle.length == 2 || stubRegionEqualsS1(haystack, index, needle, 0, needle.length)) {
                        return index;
                    }
                    index++;
                }
            } else {
                while (index < max - 1) {
                    index = stubIndexOf2ConsecutiveWithOrMaskS1(haystack, index, max, Byte.toUnsignedInt(needle[0]), Byte.toUnsignedInt(needle[1]), Byte.toUnsignedInt(mask[0]),
                                    Byte.toUnsignedInt(mask[1]));
                    if (index < 0) {
                        return -1;
                    }
                    if (needle.length == 2 || stubRegionEqualsWithOrMaskS1(haystack, index, needle, 0, mask, mask.length)) {
                        return index;
                    }
                    index++;
                }
            }
            return -1;
        }
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code length}.
     * Performs best if {@code needle} and {@code mask} are {@link CompilationFinal} with
     * {@code dimensions = 1}.
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle} {@code (haystack[index + i] | mask[i]) == needle[i]} holds, and
     *         {@code fromIndex <= index && index + needle.length <= fromIndex + length} holds, or
     *         {@code -1} if no such region is found.
     * @since 19.3
     */
    public static int indexOfWithOrMask(char[] haystack, int fromIndex, int length, char[] needle, char[] mask) {
        checkArgsIndexOf(haystack.length, fromIndex, length);
        if (mask != null) {
            checkMaskLengthIndexOf(needle.length, mask.length);
        }
        if (needle.length == 0) {
            return fromIndex;
        }
        if (length - needle.length < 0) {
            return -1;
        } else if (needle.length == 1) {
            if (mask == null) {
                return stubIndexOfC1S2(haystack, fromIndex, fromIndex + length, needle[0]);
            }
            return stubIndexOfWithOrMaskS2(haystack, fromIndex, fromIndex + length, needle[0], mask[0]);
        } else {
            int max = fromIndex + length - (needle.length - 2);
            int index = fromIndex;
            if (mask == null) {
                while (index < max - 1) {
                    index = stubIndexOf2ConsecutiveS2(haystack, index, max, needle[0], needle[1]);
                    if (index < 0) {
                        return -1;
                    }
                    if (needle.length == 2 || stubRegionEqualsS2(haystack, index, needle, 0, needle.length)) {
                        return index;
                    }
                    index++;
                }
            } else {
                while (index < max - 1) {
                    index = stubIndexOf2ConsecutiveWithOrMaskS2(haystack, index, max, needle[0], needle[1], mask[0], mask[1]);
                    if (index < 0) {
                        return -1;
                    }
                    if (needle.length == 2 || stubRegionEqualsWithOrMaskS2(haystack, index, needle, 0, mask, mask.length)) {
                        return index;
                    }
                    index++;
                }
            }
            return -1;
        }
    }

    /**
     * Returns the index of the first region of {@code haystack} that equals {@code needle} after
     * being OR'ed with {@code mask}, bounded by {@code fromIndex} (inclusive) and {@code length}.
     * Performs best if {@code needle} and {@code mask} are {@link CompilationFinal}.
     *
     * @return the index of the first region of {@code haystack} where for all indices {@code i} of
     *         {@code needle}
     *         {@code (haystack.charAt(index + i) | mask.charAt(i)) == needle.charAt(i)} holds, and
     *         {@code fromIndex <= index && index + needle.length() <= fromIndex + length} holds, or
     *         {@code -1} if no such region is found.
     * @since 19.3
     */
    public static int indexOfWithOrMask(String haystack, int fromIndex, int length, String needle, String mask) {
        checkArgsIndexOf(haystack.length(), fromIndex, length);
        if (mask != null) {
            checkMaskLengthIndexOf(needle.length(), mask.length());
        }
        if (needle.isEmpty()) {
            return fromIndex;
        }
        if (length - needle.length() < 0) {
            return -1;
        } else if (needle.length() == 1) {
            return indexOfWithOrMaskJLString(haystack, fromIndex, length, needle, mask);
        } else {
            int max = fromIndex + length - (needle.length() - 2);
            int index = fromIndex;
            while (index < max - 1) {
                index = indexOf2ConsecutiveWithOrMaskJLString(haystack, index, needle, mask, max);
                if (index < 0) {
                    return -1;
                }
                if (needle.length() == 2 || regionEqualsWithOrMask(haystack, index, needle, 0, needle.length(), mask)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }

    private static int indexOfWithOrMaskJLString(String haystack, int fromIndex, int length, String needle, String mask) {
        int maxIndex = fromIndex + length;
        int v0 = needle.charAt(0);
        byte[] array = getJavaStringArray(haystack);
        if (mask == null) {
            if (isCompactString(haystack)) {
                return v0 <= 0xff ? stubIndexOfB1S1(array, fromIndex, maxIndex, v0) : -1;
            } else {
                return stubIndexOfB1S2(array, fromIndex, maxIndex, v0);
            }
        } else {
            int mask0 = mask.charAt(0);
            if (isCompactString(haystack)) {
                return (v0 ^ mask0) <= 0xff ? stubIndexOfWithOrMaskS1(array, fromIndex, maxIndex, v0, mask0) : -1;
            } else {
                return stubIndexOfWithOrMaskS2(array, fromIndex, maxIndex, v0, mask0);
            }
        }
    }

    private static int indexOf2ConsecutiveWithOrMaskJLString(String haystack, int fromIndex, String needle, String mask, int max) {
        char v0 = needle.charAt(0);
        char v1 = needle.charAt(1);
        byte[] array = getJavaStringArray(haystack);
        if (mask == null) {
            if (isCompactString(haystack)) {
                return v0 <= 0xff && v1 <= 0xff ? stubIndexOf2ConsecutiveS1(array, fromIndex, max, v0, v1) : -1;
            } else {
                return stubIndexOf2ConsecutiveS2(array, fromIndex, max, v0, v1);
            }
        } else {
            char mask0 = mask.charAt(0);
            char mask1 = mask.charAt(1);
            if (isCompactString(haystack)) {
                return (v0 ^ mask0) <= 0xff && (v1 ^ mask1) <= 0xff ? stubIndexOf2ConsecutiveWithOrMaskS1(array, fromIndex, max, v0, v1, mask0, mask1) : -1;
            } else {
                return stubIndexOf2ConsecutiveWithOrMaskS2(array, fromIndex, max, v0, v1, mask0, mask1);
            }
        }
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive), {@code (a[offsetA + i] | mask[i]) == b[offsetB + i]} holds.
     * Performs best if {@code length} and {@code mask} are {@link CompilationFinal} with
     * {@code dimensions = 1}. If {@code mask} is {@code null}, it is treated as if it was filled
     * with zeroes.
     *
     * @since 19.3
     */
    public static boolean regionEqualsWithOrMask(byte[] a, int offsetA, byte[] b, int offsetB, int length, byte[] mask) {
        requireNonNull(a);
        requireNonNull(b);
        checkArgsRegionEquals(offsetA, offsetB, length);
        if (regionEqualsOutOfBounds(a.length, offsetA, b.length, offsetB, length)) {
            return false;
        }
        if (mask == null) {
            return stubRegionEqualsS1(a, offsetA, b, offsetB, length);
        }
        checkMaskLengthRegionEquals(length, mask.length);
        return stubRegionEqualsWithOrMaskS1(a, offsetA, b, offsetB, mask, mask.length);
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive), {@code (a[offsetA + i] | mask[i]) == b[offsetB + i]} holds.
     * Performs best if {@code length} and {@code mask} are {@link CompilationFinal} with
     * {@code dimensions = 1}. If {@code mask} is {@code null}, it is treated as if it was filled
     * with zeroes.
     *
     * @since 19.3
     */
    public static boolean regionEqualsWithOrMask(char[] a, int offsetA, char[] b, int offsetB, int length, char[] mask) {
        requireNonNull(a);
        requireNonNull(b);
        checkArgsRegionEquals(offsetA, offsetB, length);
        if (regionEqualsOutOfBounds(a.length, offsetA, b.length, offsetB, length)) {
            return false;
        }
        if (mask == null) {
            return stubRegionEqualsS2(a, offsetA, b, offsetB, length);
        }
        checkMaskLengthRegionEquals(length, mask.length);
        return stubRegionEqualsWithOrMaskS2(a, offsetA, b, offsetB, mask, mask.length);
    }

    /**
     * Returns {@code true} iff for all indices {@code i} from {@code 0} (inclusive) to
     * {@code length} (exclusive),
     * {@code (a.charAt(offsetA + i) | mask.charAt(i)) == b.charAt(offsetB + i)} holds. Performs
     * best if {@code length} and {@code mask} are {@link CompilationFinal} with
     * {@code dimensions = 1}. If {@code mask} is {@code null}, it is treated as if it was filled
     * with zeroes.
     *
     * @since 19.3
     */
    public static boolean regionEqualsWithOrMask(String a, int offsetA, String b, int offsetB, int length, String mask) {
        requireNonNull(a);
        requireNonNull(b);
        checkArgsRegionEquals(offsetA, offsetB, length);
        if (regionEqualsOutOfBounds(a.length(), offsetA, b.length(), offsetB, length)) {
            return false;
        }
        if (mask == null) {
            byte[] arrayA = getJavaStringArray(a);
            byte[] arrayB = getJavaStringArray(b);
            boolean compactA = isCompactString(a);
            if (compactA != isCompactString(b)) {
                return stubRegionEqualsS2S1(
                                compactA ? arrayB : arrayA, compactA ? offsetB : offsetA,
                                compactA ? arrayA : arrayB, compactA ? offsetA : offsetB, length);
            } else {
                final int byteOffsetA;
                final int byteOffsetB;
                final int byteLength;
                if (compactA) {
                    byteOffsetA = offsetA;
                    byteOffsetB = offsetB;
                    byteLength = length;
                } else {
                    byteOffsetA = offsetA << 1;
                    byteOffsetB = offsetB << 1;
                    byteLength = length << 1;
                }
                return stubRegionEqualsS1(arrayA, byteOffsetA, arrayB, byteOffsetB, byteLength);
            }
        } else {
            checkMaskLengthRegionEquals(length, mask.length());
            byte[] arrayA = getJavaStringArray(a);
            byte[] arrayB = getJavaStringArray(b);
            byte[] arrayM = getJavaStringArray(mask);
            boolean compact1 = isCompactString(a);
            boolean compact2 = isCompactString(b);
            boolean compactMask = isCompactString(mask);
            if (compact2) {
                if (compactMask) {
                    if (compact1) {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), true, true, true);
                    } else {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), false, true, true);
                    }
                } else {
                    return false;
                }
            } else {
                if (compactMask) {
                    if (compact1) {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), true, false, true);
                    } else {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), false, false, true);
                    }
                } else {
                    if (compact1) {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), true, false, false);
                    } else {
                        return stubRegionEqualsWithOrMaskCompactStrings(arrayA, offsetA, arrayB, offsetB, arrayM, mask.length(), false, false, false);
                    }
                }
            }
        }
    }

    private static boolean regionEqualsOutOfBounds(int lengthA, int offsetA, int lengthB, int offsetB, int length) {
        return lengthA - offsetA < length || lengthB - offsetB < length;
    }

    private static void checkArgsRegionEquals(int offsetA, int offsetB, int length) {
        if (offsetA < 0 || offsetB < 0 || length < 0) {
            illegalArgumentException("length, offsetA and offsetB must be positive");
        }
    }

    private static void checkMaskLengthRegionEquals(int length, int maskLength) {
        if (length > maskLength) {
            illegalArgumentException("mask length must be greater or equal to length");
        }
    }

    private static void checkArgs(int length, int fromIndex, int maxIndex, int nValues) {
        if (fromIndex < 0) {
            illegalArgumentException("fromIndex must be positive");
        }
        if (maxIndex > length || maxIndex < fromIndex) {
            illegalArgumentException("maxIndex out of range");
        }
        if (nValues == 0) {
            illegalArgumentException("no search values provided");
        }
    }

    private static void checkArgsIndexOf(int hayStackLength, int fromIndex, int length) {
        if (fromIndex < 0 || length < 0) {
            illegalArgumentException("fromIndex and length must be positive");
        }
        if (fromIndex + length > hayStackLength) {
            illegalArgumentException("length out of range");
        }
    }

    private static void checkMaskLengthIndexOf(int lengthB, int maskLength) {
        if (lengthB != maskLength) {
            illegalArgumentException("mask and needle length must be equal");
        }
    }

    private static void requireNonNull(Object obj) {
        if (obj == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new NullPointerException();
        }
    }

    private static void illegalArgumentException(String msg) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalArgumentException(msg);
    }

    private static int indexOfBS1(byte[] array, int fromIndex, int maxIndex, int values, int nValues) {
        if (nValues == 1) {
            return stubIndexOfB1S1(array, fromIndex, maxIndex, values & 0xff);
        } else if (nValues == 2) {
            return stubIndexOfB2S1(array, fromIndex, maxIndex, values & 0xff, (values >>> 8) & 0xff);
        } else if (nValues == 3) {
            return stubIndexOfB3S1(array, fromIndex, maxIndex, values & 0xff, (values >>> 8) & 0xff, (values >>> 16) & 0xff);
        } else {
            return stubIndexOfB4S1(array, fromIndex, maxIndex, values & 0xff, (values >>> 8) & 0xff, (values >>> 16) & 0xff, (values >>> 24) & 0xff);
        }
    }

    private static int indexOfBS1(byte[] array, int fromIndex, int maxIndex, byte[] bytes, int nValues) {
        if (nValues == 1) {
            return stubIndexOfB1S1(array, fromIndex, maxIndex, Byte.toUnsignedInt(bytes[0]));
        } else if (nValues == 2) {
            return stubIndexOfB2S1(array, fromIndex, maxIndex, Byte.toUnsignedInt(bytes[0]), Byte.toUnsignedInt(bytes[1]));
        } else if (nValues == 3) {
            return stubIndexOfB3S1(array, fromIndex, maxIndex, Byte.toUnsignedInt(bytes[0]), Byte.toUnsignedInt(bytes[1]), Byte.toUnsignedInt(bytes[2]));
        } else {
            return stubIndexOfB4S1(array, fromIndex, maxIndex, Byte.toUnsignedInt(bytes[0]), Byte.toUnsignedInt(bytes[1]), Byte.toUnsignedInt(bytes[2]), Byte.toUnsignedInt(bytes[3]));
        }
    }

    private static int indexOfBS2(byte[] array, int fromIndex, int maxIndex, char[] chars, int nValues) {
        if (nValues == 1) {
            return stubIndexOfB1S2(array, fromIndex, maxIndex, chars[0]);
        } else if (nValues == 2) {
            return stubIndexOfB2S2(array, fromIndex, maxIndex, chars[0], chars[1]);
        } else if (nValues == 3) {
            return stubIndexOfB3S2(array, fromIndex, maxIndex, chars[0], chars[1], chars[2]);
        } else {
            return stubIndexOfB4S2(array, fromIndex, maxIndex, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int indexOfCS2(char[] array, int fromIndex, int maxIndex, char[] chars, int nValues) {
        if (nValues == 1) {
            return stubIndexOfC1S2(array, fromIndex, maxIndex, chars[0]);
        } else if (nValues == 2) {
            return stubIndexOfC2S2(array, fromIndex, maxIndex, chars[0], chars[1]);
        } else if (nValues == 3) {
            return stubIndexOfC3S2(array, fromIndex, maxIndex, chars[0], chars[1], chars[2]);
        } else {
            return stubIndexOfC4S2(array, fromIndex, maxIndex, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int stubIndexOfB1S1(byte[] array, int fromIndex, int maxIndex, int v1) {
        return runIndexOfS1(array, fromIndex, maxIndex, v1);
    }

    private static int stubIndexOfB2S1(byte[] array, int fromIndex, int maxIndex, int v1, int v2) {
        return runIndexOfS1(array, fromIndex, maxIndex, v1, v2);
    }

    private static int stubIndexOfB3S1(byte[] array, int fromIndex, int maxIndex, int v1, int v2, int v3) {
        return runIndexOfS1(array, fromIndex, maxIndex, v1, v2, v3);
    }

    private static int stubIndexOfB4S1(byte[] array, int fromIndex, int maxIndex, int v1, int v2, int v3, int v4) {
        return runIndexOfS1(array, fromIndex, maxIndex, v1, v2, v3, v4);
    }

    private static int stubIndexOfB1S2(byte[] array, int fromIndex, int maxIndex, int v1) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1);
    }

    private static int stubIndexOfB2S2(byte[] array, int fromIndex, int maxIndex, int v1, int v2) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2);
    }

    private static int stubIndexOfB3S2(byte[] array, int fromIndex, int maxIndex, int v1, int v2, int v3) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2, v3);
    }

    private static int stubIndexOfB4S2(byte[] array, int fromIndex, int maxIndex, int v1, int v2, int v3, int v4) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2, v3, v4);
    }

    private static int stubIndexOfC1S2(char[] array, int fromIndex, int maxIndex, int v1) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1);
    }

    private static int stubIndexOfC2S2(char[] array, int fromIndex, int maxIndex, int v1, int v2) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2);
    }

    private static int stubIndexOfC3S2(char[] array, int fromIndex, int maxIndex, int v1, int v2, int v3) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2, v3);
    }

    private static int stubIndexOfC4S2(char[] array, int fromIndex, int maxIndex, int v1, int v2, int v3, int v4) {
        return runIndexOfS2(array, fromIndex, maxIndex, v1, v2, v3, v4);
    }

    private static int stubIndexOfWithOrMaskS1(byte[] haystack, int fromIndex, int maxIndex, int needle, int mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((Byte.toUnsignedInt(haystack[i]) | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    private static int stubIndexOfWithOrMaskS2(byte[] haystack, int fromIndex, int maxIndex, int needle, int mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((readChar(haystack, i) | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    private static int stubIndexOfWithOrMaskS2(char[] haystack, int fromIndex, int maxIndex, int needle, int mask) {
        for (int i = fromIndex; i < maxIndex; i++) {
            if ((haystack[i] | mask) == needle) {
                return i;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveS1(byte[] haystack, int fromIndex, int maxIndex, int c1, int c2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if (Byte.toUnsignedInt(haystack[i - 1]) == c1 && Byte.toUnsignedInt(haystack[i]) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveS2(byte[] haystack, int fromIndex, int maxIndex, int c1, int c2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if (readChar(haystack, i - 1) == c1 && readChar(haystack, i) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveS2(char[] haystack, int fromIndex, int maxIndex, int c1, int c2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if (haystack[i - 1] == c1 && haystack[i] == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveWithOrMaskS1(byte[] haystack, int fromIndex, int maxIndex, int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((Byte.toUnsignedInt(haystack[i - 1]) | mask1) == c1 && (Byte.toUnsignedInt(haystack[i]) | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveWithOrMaskS2(byte[] haystack, int fromIndex, int maxIndex, int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((readChar(haystack, i - 1) | mask1) == c1 && (readChar(haystack, i) | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static int stubIndexOf2ConsecutiveWithOrMaskS2(char[] haystack, int fromIndex, int maxIndex, int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex + 1; i < maxIndex; i++) {
            if ((haystack[i - 1] | mask1) == c1 && (haystack[i] | mask2) == c2) {
                return i - 1;
            }
        }
        return -1;
    }

    private static boolean stubRegionEqualsS1(byte[] a, long offsetA, byte[] b, long offsetB, int length) {
        for (int i = 0; i < length; i++) {
            if (a[(int) offsetA + i] != b[(int) offsetB + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean stubRegionEqualsS2S1(byte[] a, long offsetA, byte[] b, long offsetB, int length) {
        for (int i = 0; i < length; i++) {
            if (readChar(a, (int) offsetA + i) != Byte.toUnsignedInt(b[(int) offsetB + i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean stubRegionEqualsS2(char[] a, long offsetA, char[] b, long offsetB, int length) {
        for (int i = 0; i < length; i++) {
            if (a[(int) offsetA + i] != b[(int) offsetB + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean stubRegionEqualsWithOrMaskS1(byte[] a, long offsetA, byte[] b, long offsetB, byte[] mask, int length) {
        for (int i = 0; i < length; i++) {
            if ((a[(int) offsetA + i] | mask[i]) != b[(int) offsetB + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean stubRegionEqualsWithOrMaskS2(char[] a, long offsetA, char[] b, long offsetB, char[] mask, int length) {
        for (int i = 0; i < length; i++) {
            if ((a[(int) offsetA + i] | mask[i]) != b[(int) offsetB + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean stubRegionEqualsWithOrMaskCompactStrings(byte[] a, long offsetA, byte[] b, long offsetB, byte[] mask, int length, boolean compactA, boolean compactB, boolean compactM) {
        for (int i = 0; i < length; i++) {
            int vA = compactA ? Byte.toUnsignedInt(a[(int) offsetA + i]) : readChar(a, (int) offsetA + i);
            int vB = compactB ? Byte.toUnsignedInt(b[(int) offsetB + i]) : readChar(b, (int) offsetB + i);
            int vM = compactM ? Byte.toUnsignedInt(mask[i]) : readChar(mask, i);
            if ((vA | vM) != vB) {
                return false;
            }
        }
        return true;
    }

    private static int readChar(byte[] array, int i) {
        int byte0 = Byte.toUnsignedInt(array[i << 1]);
        int byte1 = Byte.toUnsignedInt(array[(i << 1) + 1]);
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? byte0 | (byte1 << 8) : (byte0 << 8) | byte1;
    }

    private static int runIndexOfS1(byte[] array, int fromIndex, int maxIndex, int... values) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (int v : values) {
                if (Byte.toUnsignedInt(array[i]) == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int runIndexOfS1(byte[] array, int fromIndex, int maxIndex, byte... values) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (int v : values) {
                if (array[i] == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int runIndexOfS2(byte[] array, int fromIndex, int maxIndex, int... values) {
        for (int i = fromIndex; i < maxIndex; i++) {
            int c = readChar(array, i);
            for (int v : values) {
                if (c == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int runIndexOfS2(char[] array, int fromIndex, int maxIndex, int... values) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (int v : values) {
                if (array[i] == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int runIndexOfS2(char[] array, int fromIndex, int maxIndex, char... values) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (int v : values) {
                if (array[i] == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int runIndexOfS2(String haystack, int fromIndex, int maxIndex, char[] needle) {
        for (int i = fromIndex; i < maxIndex; i++) {
            for (char c : needle) {
                if (haystack.charAt(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }
}
