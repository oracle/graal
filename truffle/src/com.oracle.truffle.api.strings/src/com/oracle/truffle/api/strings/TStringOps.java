/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings;

import static com.oracle.truffle.api.strings.Encodings.isUTF16LowSurrogate;
import static com.oracle.truffle.api.strings.Encodings.isUTF8ContinuationByte;

import java.lang.ref.Reference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import sun.misc.Unsafe;

final class TStringOps {

    static int readFromByteArray(byte[] array, int stride, int i) {
        return readValue(array, 0, array.length >> stride, stride, i);
    }

    static void writeToByteArray(byte[] array, int stride, int i, int value) {
        writeValue(array, 0, array.length >> stride, stride, i, value);
    }

    static int readValue(AbstractTruffleString a, Object arrayA, int stride, int i) {
        return readValue(arrayA, a.offset(), a.length(), stride, i);
    }

    static int readS0(AbstractTruffleString a, Object arrayA, int i) {
        return readS0(arrayA, a.offset(), a.length(), i);
    }

    static char readS1(AbstractTruffleString a, Object arrayA, int i) {
        return readS1(arrayA, a.offset(), a.length(), i);
    }

    static int readS2(AbstractTruffleString a, Object arrayA, int i) {
        return readS2(arrayA, a.offset(), a.length(), i);
    }

    static int readS0(Object array, int offset, int length, int i) {
        return readValue(array, offset, length, 0, i);
    }

    static char readS1(Object array, int offset, int length, int i) {
        return (char) readValue(array, offset, length, 1, i);
    }

    static int readS2(Object array, int offset, int length, int i) {
        return readValue(array, offset, length, 2, i);
    }

    static long readS3(Object array, int offset, int length) {
        try {
            final byte[] stubArray;
            final long stubOffset;
            if (isNativePointer(array)) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 3);
            return TStringUnsafe.getLong(stubArray, stubOffset);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static void writeS0(Object array, int offset, int length, int i, byte value) {
        writeValue(array, offset, length, 0, i, uInt(value));
    }

    static int readValue(Object array, int offset, int length, int stride, int i) {
        try {
            final byte[] stubArray;
            final long stubOffset;
            if (isNativePointer(array)) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, i);
            return readValue(stubArray, stubOffset, stride, i);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    private static void writeValue(Object array, int offset, int length, int stride, int i, int value) {
        try {
            final byte[] stubArray;
            final long stubOffset;
            if (isNativePointer(array)) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, i);
            writeValue(stubArray, stubOffset, stride, i, value);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS0(byte[] array, long offset, int i) {
        return readValue(array, offset, 0, i);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS1(byte[] array, long offset, int i) {
        return readValue(array, offset, 1, i);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS2(byte[] array, long offset, int i) {
        return readValue(array, offset, 2, i);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValue(byte[] array, long offset, int stride, int i) {
        switch (stride) {
            case 0:
                return uInt(TStringUnsafe.getByte(array, offset + i));
            case 1:
                return TStringUnsafe.getChar(array, offset + ((long) i << 1));
            default:
                return TStringUnsafe.getInt(array, offset + ((long) i << 2));
        }
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static void writeValue(byte[] array, long offset, int stride, int i, int value) {
        switch (stride) {
            case 0:
                TStringUnsafe.putByte(array, offset + i, (byte) value);
                return;
            case 1:
                TStringUnsafe.putChar(array, offset + ((long) i << 1), (char) value);
                return;
            default:
                TStringUnsafe.putInt(array, offset + ((long) i << 2), value);
        }
    }

    static int indexOfAnyByte(Node location, AbstractTruffleString a, Object arrayA, int fromIndex, int toIndex, byte[] values) {
        assert a.stride() == 0;
        return indexOfAnyByteIntl(location, arrayA, a.offset(), toIndex, fromIndex, values);
    }

    private static int indexOfAnyByteIntl(Node location, Object array, int offset, int length, int fromIndex, byte[] values) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, 0, fromIndex);
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, uInt(values[0]));
                case 2:
                    return runIndexOfAny2(location, stubArray, stubOffset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]));
                case 3:
                    return runIndexOfAny3(location, stubArray, stubOffset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]), uInt(values[2]));
                case 4:
                    return runIndexOfAny4(location, stubArray, stubOffset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]), uInt(values[2]), uInt(values[3]));
                default:
                    return runIndexOfAnyByte(location, stubArray, stubOffset, length, fromIndex, values);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfAnyChar(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, char[] values) {
        assert stride == 0 || stride == 1;
        return indexOfAnyCharIntl(location, arrayA, a.offset(), toIndex, stride, fromIndex, values);
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOfAnyCharIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, char[] values) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            if (stride == 0) {
                switch (values.length) {
                    case 1:
                        return runIndexOfAny1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0]);
                    case 2:
                        return runIndexOfAny2(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1]);
                    case 3:
                        return runIndexOfAny3(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1], values[2]);
                    case 4:
                        return runIndexOfAny4(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1], values[2], values[3]);
                }
            } else {
                assert stride == 1;
                switch (values.length) {
                    case 1:
                        return runIndexOfAny1(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0]);
                    case 2:
                        return runIndexOfAny2(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1]);
                    case 3:
                        return runIndexOfAny3(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1], values[2]);
                    case 4:
                        return runIndexOfAny4(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1], values[2], values[3]);
                }
            }
            return runIndexOfAnyChar(location, stubArray, stubOffset, length, stride, fromIndex, values);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfAnyInt(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, int[] values) {
        return indexOfAnyIntIntl(location, arrayA, a.offset(), toIndex, stride, fromIndex, values);
    }

    static int indexOfAnyInt(Node location, Object arrayA, int offsetA, int stride, int fromIndex, int toIndex, int[] values) {
        return indexOfAnyIntIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, values);
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOfAnyIntIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int[] values) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            if (stride == 0) {
                switch (values.length) {
                    case 1:
                        return runIndexOfAny1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0]);
                    case 2:
                        return runIndexOfAny2(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1]);
                    case 3:
                        return runIndexOfAny3(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1], values[2]);
                    case 4:
                        return runIndexOfAny4(location, stubArray, stubOffset, length, 0, isNative, fromIndex, values[0], values[1], values[2], values[3]);
                }
            } else if (stride == 1) {
                switch (values.length) {
                    case 1:
                        return runIndexOfAny1(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0]);
                    case 2:
                        return runIndexOfAny2(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1]);
                    case 3:
                        return runIndexOfAny3(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1], values[2]);
                    case 4:
                        return runIndexOfAny4(location, stubArray, stubOffset, length, 1, isNative, fromIndex, values[0], values[1], values[2], values[3]);
                }
            } else {
                assert stride == 2;
                switch (values.length) {
                    case 1:
                        return runIndexOfAny1(location, stubArray, stubOffset, length, 2, isNative, fromIndex, values[0]);
                    case 2:
                        return runIndexOfAny2(location, stubArray, stubOffset, length, 2, isNative, fromIndex, values[0], values[1]);
                    case 3:
                        return runIndexOfAny3(location, stubArray, stubOffset, length, 2, isNative, fromIndex, values[0], values[1], values[2]);
                    case 4:
                        return runIndexOfAny4(location, stubArray, stubOffset, length, 2, isNative, fromIndex, values[0], values[1], values[2], values[3]);
                }
            }
            return runIndexOfAnyInt(location, stubArray, stubOffset, length, stride, fromIndex, values);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfAnyIntRange(Node location, Object arrayA, int offsetA, int stride, int fromIndex, int toIndex, int[] ranges) {
        return indexOfAnyIntRangeIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, ranges);
    }

    private static int indexOfAnyIntRangeIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int[] ranges) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            if (stride == 0) {
                if (ranges.length == 2) {
                    return runIndexOfRange1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, ranges[0], ranges[1]);
                } else if (ranges.length == 4) {
                    return runIndexOfRange2(location, stubArray, stubOffset, length, 0, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
                }
            } else if (stride == 1) {
                if (ranges.length == 2) {
                    return runIndexOfRange1(location, stubArray, stubOffset, length, 1, isNative, fromIndex, ranges[0], ranges[1]);
                } else if (ranges.length == 4) {
                    return runIndexOfRange2(location, stubArray, stubOffset, length, 1, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
                }
            } else {
                assert stride == 2;
                if (ranges.length == 2) {
                    return runIndexOfRange1(location, stubArray, stubOffset, length, 2, isNative, fromIndex, ranges[0], ranges[1]);
                } else if (ranges.length == 4) {
                    return runIndexOfRange2(location, stubArray, stubOffset, length, 2, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
                }
            }
            return runIndexOfAnyIntRange(location, stubArray, stubOffset, length, stride, fromIndex, ranges);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfTable(Node location, Object arrayA, int offsetA, int stride, int fromIndex, int toIndex, byte[] tables) {
        return indexOfTableIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, tables);
    }

    private static int indexOfTableIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, byte[] tables) {
        try {
            assert tables.length == 32;
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            if (stride == 0) {
                return runIndexOfTable(location, stubArray, stubOffset, length, 0, isNative, fromIndex, tables);
            } else if (stride == 1) {
                return runIndexOfTable(location, stubArray, stubOffset, length, 1, isNative, fromIndex, tables);
            } else {
                assert stride == 2;
                return runIndexOfTable(location, stubArray, stubOffset, length, 2, isNative, fromIndex, tables);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfCodePointWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int codepoint) {
        return indexOfCodePointWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, codepoint);
    }

    private static int indexOfCodePointWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            switch (stride) {
                case 0:
                    return runIndexOfAny1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1);
                case 1:
                    return runIndexOfAny1(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1);
                default:
                    assert stride == 2;
                    return runIndexOfAny1(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfCodePointWithOrMaskWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int codepoint, int maskA) {
        return indexOfCodePointWithMaskWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, codepoint, maskA);
    }

    static int indexOfCodePointWithMaskWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int mask1) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            switch (stride) {
                case 0:
                    return (v1 ^ mask1) <= 0xff ? runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, mask1) : -1;
                case 1:
                    return (v1 ^ mask1) <= 0xffff ? runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, mask1) : -1;
                default:
                    assert stride == 2;
                    return runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, mask1);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOf2ConsecutiveWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int v1, int v2) {
        return indexOf2ConsecutiveWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, v1, v2);
    }

    private static int indexOf2ConsecutiveWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int v2) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            switch (stride) {
                case 0:
                    return (v1 | v2) <= 0xff ? runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, v2) : -1;
                case 1:
                    return (v1 | v2) <= 0xffff ? runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, v2) : -1;
                default:
                    assert stride == 2;
                    return runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, v2);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int v2, int mask1, int mask2) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, length, stride, fromIndex);
            switch (stride) {
                case 0:
                    return ((v1 ^ mask1) | (v2 ^ mask2)) <= 0xff ? runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, v2, mask1, mask2)
                                    : -1;
                case 1:
                    return ((v1 ^ mask1) | (v2 ^ mask2)) <= 0xffff ? runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, v2, mask1, mask2)
                                    : -1;
                default:
                    assert stride == 2;
                    return runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, v2, mask1, mask2);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int indexOfStringWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, Object arrayA, int strideA,
                    AbstractTruffleString b, Object arrayB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        return indexOfStringWithOrMaskWithStride(location,
                        arrayA, a.offset(), a.length(), strideA,
                        arrayB, b.offset(), b.length(), strideB, fromIndex, toIndex, maskB);
    }

    static int indexOfStringWithOrMaskWithStride(Node location,
                    Object arrayA, int offsetA, int lengthA, int strideA,
                    Object arrayB, int offsetB, int lengthB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        int offsetMask = 0;
        assert lengthB > 1;
        assert lengthA >= lengthB;
        final int max = toIndex - (lengthB - 2);
        int index = fromIndex;
        final int b0 = readValue(arrayB, offsetB, lengthB, strideB, 0);
        final int b1 = readValue(arrayB, offsetB, lengthB, strideB, 1);
        final int mask0 = maskB == null ? 0 : readValue(maskB, offsetMask, lengthB, strideB, 0);
        final int mask1 = maskB == null ? 0 : readValue(maskB, offsetMask, lengthB, strideB, 1);
        while (index < max - 1) {
            if (maskB == null) {
                index = indexOf2ConsecutiveWithStrideIntl(location, arrayA, offsetA, max, strideA, index, b0, b1);
            } else {
                index = indexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, offsetA, max, strideA, index, b0, b1, mask0, mask1);
            }
            if (index < 0) {
                return -1;
            }
            if (lengthB == 2 || regionEqualsWithOrMaskWithStrideIntl(location,
                            arrayA, offsetA, lengthA, strideA, index,
                            arrayB, offsetB, lengthB, strideB, 0, maskB, lengthB)) {
                return index;
            }
            index++;
            TStringConstants.truffleSafePointPoll(location, index);
        }
        return -1;
    }

    static int lastIndexOfCodePointWithOrMaskWithStride(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, int codepoint, int mask) {
        return lastIndexOfCodePointWithOrMaskWithStrideIntl(location, arrayA, a.offset(), stride, fromIndex, toIndex, codepoint, mask);
    }

    private static int lastIndexOfCodePointWithOrMaskWithStrideIntl(Node location, Object array, int offset, int stride, int fromIndex, int toIndex, int codepoint, int mask) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, fromIndex, stride, toIndex);
            switch (stride) {
                case 0:
                    return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 0, fromIndex, toIndex, codepoint, mask);
                case 1:
                    return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 1, fromIndex, toIndex, codepoint, mask);
                default:
                    assert stride == 2;
                    return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 2, fromIndex, toIndex, codepoint, mask);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int lastIndexOf2ConsecutiveWithOrMaskWithStride(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        return lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, a.offset(), stride, fromIndex, toIndex, v1, v2, mask1, mask2);
    }

    private static int lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, Object array, int offset, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegionIndex(stubArray, offset, fromIndex, stride, toIndex);
            switch (stride) {
                case 0:
                    return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 0, fromIndex, toIndex, v1, v2, mask1, mask2);
                case 1:
                    return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 1, fromIndex, toIndex, v1, v2, mask1, mask2);
                default:
                    assert stride == 2;
                    return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 2, fromIndex, toIndex, v1, v2, mask1, mask2);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int lastIndexOfStringWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, Object arrayA, int strideA,
                    AbstractTruffleString b, Object arrayB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        int offsetMask = 0;
        assert b.length() > 1;
        assert a.length() >= b.length();
        int index = fromIndex;
        final int b0 = readValue(b, arrayB, strideB, b.length() - 2);
        final int b1 = readValue(b, arrayB, strideB, b.length() - 1);
        final int mask0 = maskB == null ? 0 : readValue(maskB, offsetMask, b.length(), strideB, b.length() - 2);
        final int mask1 = maskB == null ? 0 : readValue(maskB, offsetMask, b.length(), strideB, b.length() - 1);
        final int toIndex2Consecutive = toIndex + b.length() - 2;
        while (index > toIndex2Consecutive) {
            index = lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, a.offset(), strideA, index, toIndex2Consecutive, b0, b1, mask0, mask1);
            if (index < 0) {
                return -1;
            }
            index += 2;
            if (b.length() == 2 || regionEqualsWithOrMaskWithStride(location, a, arrayA, strideA, index - b.length(), b, arrayB, strideB, 0, maskB, b.length())) {
                return index - b.length();
            }
            index--;
            TStringConstants.truffleSafePointPoll(location, index);
        }
        return -1;
    }

    static boolean regionEqualsWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, Object arrayA, int strideA, int fromIndexA,
                    AbstractTruffleString b, Object arrayB, int strideB, int fromIndexB,
                    byte[] maskB, int lengthCMP) {
        return regionEqualsWithOrMaskWithStrideIntl(location,
                        arrayA, a.offset(), a.length(), strideA, fromIndexA,
                        arrayB, b.offset(), b.length(), strideB, fromIndexB, maskB, lengthCMP);
    }

    private static boolean regionEqualsWithOrMaskWithStrideIntl(Node location,
                    Object arrayA, int offsetA, int lengthA, int strideA, int fromIndexA,
                    Object arrayB, int offsetB, int lengthB, int strideB, int fromIndexB, byte[] maskB, int lengthCMP) {
        try {
            if (!rangeInBounds(fromIndexA, lengthCMP, lengthA) || !rangeInBounds(fromIndexB, lengthCMP, lengthB)) {
                return false;
            }
            final boolean isNativeA = isNativePointer(arrayA);
            final byte[] stubArrayA;
            final long stubOffsetA;
            if (isNativeA) {
                stubArrayA = null;
                stubOffsetA = offsetA + nativePointer(arrayA) + (fromIndexA << strideA);
            } else {
                stubArrayA = (byte[]) arrayA;
                stubOffsetA = offsetA + Unsafe.ARRAY_BYTE_BASE_OFFSET + (fromIndexA << strideA);
            }
            final boolean isNativeB = isNativePointer(arrayB);
            final byte[] stubArrayB;
            final long stubOffsetB;
            if (isNativeB) {
                stubArrayB = null;
                stubOffsetB = offsetB + nativePointer(arrayB) + (fromIndexB << strideB);
            } else {
                stubArrayB = (byte[]) arrayB;
                stubOffsetB = offsetB + Unsafe.ARRAY_BYTE_BASE_OFFSET + (fromIndexB << strideB);
            }
            validateRegion(stubArrayA, offsetA, lengthCMP, strideA);
            validateRegion(stubArrayB, offsetB, lengthCMP, strideB);
            final int stubStride = stubStride(strideA, strideB);
            if (maskB == null) {
                return runRegionEqualsWithStride(location,
                                stubArrayA, stubOffsetA, isNativeA,
                                stubArrayB, stubOffsetB, isNativeB, lengthCMP, stubStride);

            } else {
                validateRegion(maskB, 0, lengthCMP, strideB);
                return runRegionEqualsWithOrMaskWithStride(location,
                                stubArrayA, stubOffsetA, isNativeA,
                                stubArrayB, stubOffsetB, isNativeB, maskB, lengthCMP, stubStride);
            }
        } finally {
            Reference.reachabilityFence(arrayA);
            Reference.reachabilityFence(arrayB);
        }
    }

    static int memcmpWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, AbstractTruffleString b, Object arrayB, int strideB, int lengthCMP) {
        assert lengthCMP <= a.length();
        assert lengthCMP <= b.length();
        return memcmpWithStrideIntl(location, arrayA, a.offset(), strideA, arrayB, b.offset(), strideB, lengthCMP);
    }

    private static int memcmpWithStrideIntl(Node location,
                    Object arrayA, int offsetA, int strideA,
                    Object arrayB, int offsetB, int strideB, int lengthCMP) {
        try {
            if (lengthCMP == 0) {
                return 0;
            }
            final boolean isNativeA = isNativePointer(arrayA);
            final byte[] stubArrayA;
            final long stubOffsetA;
            if (isNativeA) {
                stubArrayA = null;
                stubOffsetA = offsetA + nativePointer(arrayA);
            } else {
                stubArrayA = (byte[]) arrayA;
                stubOffsetA = offsetA + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            final boolean isNativeB = isNativePointer(arrayB);
            final byte[] stubArrayB;
            final long stubOffsetB;
            if (isNativeB) {
                stubArrayB = null;
                stubOffsetB = offsetB + nativePointer(arrayB);
            } else {
                stubArrayB = (byte[]) arrayB;
                stubOffsetB = offsetB + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArrayA, offsetA, lengthCMP, strideA);
            validateRegion(stubArrayB, offsetB, lengthCMP, strideB);
            return runMemCmp(location,
                            stubArrayA, stubOffsetA, isNativeA,
                            stubArrayB, stubOffsetB, isNativeB, lengthCMP, stubStride(strideA, strideB));
        } finally {
            Reference.reachabilityFence(arrayA);
            Reference.reachabilityFence(arrayB);
        }
    }

    static int memcmpBytesWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, AbstractTruffleString b, Object arrayB, int strideB, int lengthCMP) {
        assert lengthCMP <= a.length();
        assert lengthCMP <= b.length();
        return memcmpBytesWithStrideIntl(location, arrayA, a.offset(), strideA, arrayB, b.offset(), strideB, lengthCMP);
    }

    private static int memcmpBytesWithStrideIntl(
                    Node location, Object arrayA, int offsetA, int strideA,
                    Object arrayB, int offsetB, int strideB, int lengthCMP) {
        try {
            if (lengthCMP == 0) {
                return 0;
            }
            final boolean isNativeA = isNativePointer(arrayA);
            final byte[] stubArrayA;
            final long stubOffsetA;
            if (isNativeA) {
                stubArrayA = null;
                stubOffsetA = offsetA + nativePointer(arrayA);
            } else {
                stubArrayA = (byte[]) arrayA;
                stubOffsetA = offsetA + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            final boolean isNativeB = isNativePointer(arrayB);
            final byte[] stubArrayB;
            final long stubOffsetB;
            if (isNativeB) {
                stubArrayB = null;
                stubOffsetB = offsetB + nativePointer(arrayB);
            } else {
                stubArrayB = (byte[]) arrayB;
                stubOffsetB = offsetB + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArrayA, offsetA, lengthCMP, strideA);
            validateRegion(stubArrayB, offsetB, lengthCMP, strideB);
            if (strideA == strideB) {
                switch (strideA) {
                    case 0:
                        return runMemCmp(location,
                                        stubArrayA, stubOffsetA, isNativeA,
                                        stubArrayB, stubOffsetB, isNativeB, lengthCMP, 0);
                    case 1:
                        return runMemCmpBytes(location,
                                        stubArrayA, stubOffsetA, 1, isNativeA,
                                        stubArrayB, stubOffsetB, 1, isNativeB, lengthCMP);
                    default:
                        assert strideA == 2;
                        return runMemCmpBytes(location,
                                        stubArrayA, stubOffsetA, 2, isNativeA,
                                        stubArrayB, stubOffsetB, 2, isNativeB, lengthCMP);
                }
            }
            final int swappedStrideA;
            final int swappedStrideB;
            final byte[] swappedArrayA;
            final byte[] swappedArrayB;
            final long swappedOffsetA;
            final long swappedOffsetB;
            final boolean swappedIsNativeA;
            final boolean swappedIsNativeB;
            final int swappedResult;
            if (strideA < strideB) {
                swappedStrideA = strideB;
                swappedStrideB = strideA;
                swappedArrayA = stubArrayB;
                swappedArrayB = stubArrayA;
                swappedOffsetA = stubOffsetB;
                swappedOffsetB = stubOffsetA;
                swappedIsNativeA = isNativeB;
                swappedIsNativeB = isNativeA;
                swappedResult = -1;
            } else {
                swappedStrideA = strideA;
                swappedStrideB = strideB;
                swappedArrayA = stubArrayA;
                swappedArrayB = stubArrayB;
                swappedOffsetA = stubOffsetA;
                swappedOffsetB = stubOffsetB;
                swappedIsNativeA = isNativeA;
                swappedIsNativeB = isNativeB;
                swappedResult = 1;
            }
            if (swappedStrideA == 1) {
                assert swappedStrideB == 0;
                return swappedResult * runMemCmpBytes(location,
                                swappedArrayA, swappedOffsetA, 1, swappedIsNativeA,
                                swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
            } else {
                assert swappedStrideA == 2;
                if (swappedStrideB == 0) {
                    return swappedResult * runMemCmpBytes(location,
                                    swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                    swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
                } else {
                    assert swappedStrideB == 1;
                    return swappedResult * runMemCmpBytes(location,
                                    swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                    swappedArrayB, swappedOffsetB, 1, swappedIsNativeB, lengthCMP);
                }
            }
        } finally {
            Reference.reachabilityFence(arrayA);
            Reference.reachabilityFence(arrayB);
        }
    }

    static int hashCodeWithStride(Node location, AbstractTruffleString a, Object arrayA, int stride) {
        return hashCodeWithStrideIntl(location, arrayA, a.offset(), a.length(), stride);
    }

    private static int hashCodeWithStrideIntl(Node location, Object array, int offset, int length, int stride) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, stride);
            switch (stride) {
                case 0:
                    return runHashCode(location, stubArray, stubOffset, length, 0, isNative);
                case 1:
                    return runHashCode(location, stubArray, stubOffset, length, 1, isNative);
                default:
                    assert stride == 2;
                    return runHashCode(location, stubArray, stubOffset, length, 2, isNative);
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    // arrayB is returned for testing purposes, do not remove
    static Object arraycopyWithStrideCB(Node location,
                    char[] arrayA, int offsetA,
                    byte[] arrayB, int offsetB, int strideB, int lengthCPY) {
        validateRegion(arrayA, offsetA, lengthCPY);
        validateRegion(arrayB, offsetB, lengthCPY, strideB);
        int stubOffsetA = Unsafe.ARRAY_CHAR_BASE_OFFSET + offsetA;
        int stubOffsetB = Unsafe.ARRAY_BYTE_BASE_OFFSET + offsetB;
        runArrayCopy(location,
                        arrayA, stubOffsetA,
                        arrayB, stubOffsetB, lengthCPY, stubStride(1, strideB));
        return arrayB;
    }

    // arrayB is returned for testing purposes, do not remove
    static Object arraycopyWithStrideIB(Node location,
                    int[] arrayA, int offsetA,
                    byte[] arrayB, int offsetB, int strideB, int lengthCPY) {
        validateRegion(arrayA, offsetA, lengthCPY);
        validateRegion(arrayB, offsetB, lengthCPY, strideB);
        int stubOffsetA = Unsafe.ARRAY_INT_BASE_OFFSET + offsetA;
        int stubOffsetB = Unsafe.ARRAY_BYTE_BASE_OFFSET + offsetB;
        runArrayCopy(location,
                        arrayA, stubOffsetA,
                        arrayB, stubOffsetB, lengthCPY, stubStride(2, strideB));
        return arrayB;
    }

    // arrayB is returned for testing purposes, do not remove
    static Object arraycopyWithStride(Node location,
                    Object arrayA, int offsetA, int strideA, int fromIndexA,
                    Object arrayB, int offsetB, int strideB, int fromIndexB, int lengthCPY) {
        try {
            final boolean isNativeA = isNativePointer(arrayA);
            final byte[] stubArrayA;
            final long stubOffsetA;
            if (isNativeA) {
                stubArrayA = null;
                stubOffsetA = offsetA + nativePointer(arrayA) + (fromIndexA << strideA);
            } else {
                stubArrayA = (byte[]) arrayA;
                stubOffsetA = offsetA + Unsafe.ARRAY_BYTE_BASE_OFFSET + (fromIndexA << strideA);
            }
            final boolean isNativeB = isNativePointer(arrayB);
            final byte[] stubArrayB;
            final long stubOffsetB;
            if (isNativeB) {
                stubArrayB = null;
                stubOffsetB = offsetB + nativePointer(arrayB) + (fromIndexB << strideB);
            } else {
                stubArrayB = (byte[]) arrayB;
                stubOffsetB = offsetB + Unsafe.ARRAY_BYTE_BASE_OFFSET + (fromIndexB << strideB);
            }
            validateRegion(stubArrayA, offsetA, lengthCPY, strideA);
            validateRegion(stubArrayB, offsetB, lengthCPY, strideB);
            runArrayCopy(location,
                            stubArrayA, stubOffsetA, isNativeA,
                            stubArrayB, stubOffsetB, isNativeB, lengthCPY, stubStride(strideA, strideB));
            return stubArrayB;
        } finally {
            Reference.reachabilityFence(arrayA);
            Reference.reachabilityFence(arrayB);
        }
    }

    static byte[] arraycopyOfWithStride(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int lengthB, int strideB) {
        byte[] dst = new byte[lengthB << strideB];
        arraycopyWithStride(location, arrayA, offsetA, strideA, 0, dst, 0, strideB, 0, lengthA);
        return dst;
    }

    static int calcStringAttributesLatin1(Node location, Object array, int offset, int length) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 0);
            return runCalcStringAttributesLatin1(location, stubArray, stubOffset, length, isNative);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int calcStringAttributesBMP(Node location, Object array, int offset, int length) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 1);
            return runCalcStringAttributesBMP(location, stubArray, stubOffset, length, isNative);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static long calcStringAttributesUTF8(Node location, Object array, int offset, int length, boolean assumeValid, boolean isAtEnd, InlinedConditionProfile brokenProfile) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 0);
            if (assumeValid && !Encodings.isUTF8ContinuationByte(readS0(array, offset, length, 0)) && (isAtEnd || !Encodings.isUTF8ContinuationByte(readS0(array, offset, length + 1, length)))) {
                return runCalcStringAttributesUTF8(location, stubArray, stubOffset, length, isNative, true);
            } else {
                long attrs = runCalcStringAttributesUTF8(location, stubArray, stubOffset, length, isNative, false);
                if (brokenProfile.profile(location, TStringGuards.isBrokenMultiByte(StringAttributes.getCodeRange(attrs)))) {
                    int codePointLength = 0;
                    for (int i = 0; i < length; i += Encodings.utf8GetCodePointLength(array, offset, length, i, DecodingErrorHandler.DEFAULT)) {
                        codePointLength++;
                        TStringConstants.truffleSafePointPoll(location, codePointLength);
                    }
                    return StringAttributes.create(codePointLength, StringAttributes.getCodeRange(attrs));
                }
                return attrs;
            }
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static long calcStringAttributesUTF16C(Node location, char[] array, int offset, int length) {
        validateRegion(array, offset, length);
        int stubOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + offset;
        return runCalcStringAttributesUTF16C(location, array, stubOffset, length);
    }

    static long calcStringAttributesUTF16(Node location, Object array, int offset, int length, boolean assumeValid) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 1);
            long attrs;
            if (assumeValid) {
                attrs = runCalcStringAttributesUTF16(location, stubArray, stubOffset, length, isNative, true);
            } else {
                attrs = runCalcStringAttributesUTF16(location, stubArray, stubOffset, length, isNative, false);
            }
            if (assumeValid && length > 0) {
                if (Encodings.isUTF16LowSurrogate(readS1(array, offset, length, 0))) {
                    attrs = StringAttributes.create(StringAttributes.getCodePointLength(attrs), TSCodeRange.getBrokenMultiByte());
                }
                if (Encodings.isUTF16HighSurrogate(readS1(array, offset, length, length - 1))) {
                    attrs = StringAttributes.create(StringAttributes.getCodePointLength(attrs) + 1, TSCodeRange.getBrokenMultiByte());
                }
            }
            return attrs;
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int calcStringAttributesUTF32I(Node location, int[] array, int offset, int length) {
        validateRegion(array, offset, length);
        int stubOffset = Unsafe.ARRAY_INT_BASE_OFFSET + offset;
        return runCalcStringAttributesUTF32I(location, array, stubOffset, length);
    }

    static int calcStringAttributesUTF32(Node location, Object array, int offset, int length) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 2);
            return runCalcStringAttributesUTF32(location, stubArray, stubOffset, length, isNative);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int codePointIndexToByteIndexUTF8Valid(Node location, Object array, int offset, int length, int index) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 0);
            return runCodePointIndexToByteIndexUTF8Valid(location, stubArray, stubOffset, length, index, isNative);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    static int codePointIndexToByteIndexUTF16Valid(Node location, Object array, int offset, int length, int index) {
        try {
            final boolean isNative = isNativePointer(array);
            final byte[] stubArray;
            final long stubOffset;
            if (isNative) {
                stubArray = null;
                stubOffset = offset + nativePointer(array);
            } else {
                stubArray = (byte[]) array;
                stubOffset = offset + Unsafe.ARRAY_BYTE_BASE_OFFSET;
            }
            validateRegion(stubArray, offset, length, 1);
            return runCodePointIndexToByteIndexUTF16Valid(location, stubArray, stubOffset, length, index, isNative);
        } finally {
            Reference.reachabilityFence(array);
        }
    }

    private static int runIndexOfAnyByte(Node location, byte[] array, long offset, int length, int fromIndex, byte... needle) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, 0, i);
            for (int j = 0; j < needle.length; j++) {
                if (value == uInt(needle[j])) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runIndexOfAnyChar(Node location, byte[] array, long offset, int length, int stride, int fromIndex, char... needle) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            for (int j = 0; j < needle.length; j++) {
                if (value == needle[j]) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runIndexOfAnyInt(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int... needle) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            for (int j = 0; j < needle.length; j++) {
                if (value == needle[j]) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runIndexOfAnyIntRange(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int... ranges) {
        for (int i = fromIndex; i < length; i++) {
            for (int j = 0; j < ranges.length; j += 2) {
                if (inRange(ranges[j], ranges[j + 1], readValue(array, offset, stride, i))) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static boolean inRange(int lo, int hi, int v) {
        return Integer.compareUnsigned(lo, v) <= 0 && Integer.compareUnsigned(v, hi) <= 0;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny1(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0) {
        for (int i = fromIndex; i < length; i++) {
            if (readValue(array, offset, stride, i) == v0) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny2(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0, int v1) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            if (value == v0 || value == v1) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny3(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0, int v1, int v2) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            if (value == v0 || value == v1 || value == v2) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny4(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0, int v1, int v2, int v3) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            if (value == v0 || value == v1 || value == v2 || value == v3) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfRange1(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0, int v1) {
        for (int i = fromIndex; i < length; i++) {
            if (inRange(v0, v1, readValue(array, offset, stride, i))) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfRange2(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int v0, int v1, int v2, int v3) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            if (inRange(v0, v1, value) || inRange(v2, v3, value)) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfTable(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, byte[] tables) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValue(array, offset, stride, i);
            if (value <= 0xff && performTableLookup(tables, value)) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static boolean performTableLookup(byte[] tables, int value) {
        int tableHi = uInt(tables[((value >>> 4) & 0xf)]);
        int tableLo = uInt(tables[16 + (value & 0xf)]);
        return (tableHi & tableLo) != 0;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfWithOrMaskWithStride(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int needle,
                    int mask) {
        for (int i = fromIndex; i < length; i++) {
            if ((readValue(array, offset, stride, i) | mask) == needle) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runLastIndexOfWithOrMaskWithStride(Node location, byte[] array, long offset, int stride, int fromIndex, int toIndex, int needle, int mask) {
        for (int i = fromIndex - 1; i >= toIndex; i--) {
            if ((readValue(array, offset, stride, i) | mask) == needle) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOf2ConsecutiveWithStride(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex, int c1, int c2) {
        for (int i = fromIndex + 1; i < length; i++) {
            if (readValue(array, offset, stride, i - 1) == c1 && readValue(array, offset, stride, i) == c2) {
                return i - 1;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOf2ConsecutiveWithOrMaskWithStride(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative, int fromIndex,
                    int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex + 1; i < length; i++) {
            if ((readValue(array, offset, stride, i - 1) | mask1) == c1 && (readValue(array, offset, stride, i) | mask2) == c2) {
                return i - 1;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    private static int runLastIndexOf2ConsecutiveWithOrMaskWithStride(Node location, byte[] array, long offset, int stride, int fromIndex, int toIndex, int c1,
                    int c2, int mask1, int mask2) {
        for (int i = fromIndex - 1; i > toIndex; i--) {
            if ((readValue(array, offset, stride, i - 1) | mask1) == c1 && (readValue(array, offset, stride, i) | mask2) == c2) {
                return i - 1;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static boolean runRegionEqualsWithStride(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, int length, int stubStride) {
        int strideA = stubStrideToStrideA(stubStride);
        int strideB = stubStrideToStrideB(stubStride);
        for (int i = 0; i < length; i++) {
            if (readValue(arrayA, offsetA, strideA, i) != readValue(arrayB, offsetB, strideB, i)) {
                return false;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return true;
    }

    /**
     * Intrinsic candidate.
     */
    private static boolean runRegionEqualsWithOrMaskWithStride(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, byte[] arrayMask, int lengthCMP, int stubStride) {
        int strideA = stubStrideToStrideA(stubStride);
        int strideB = stubStrideToStrideB(stubStride);
        for (int i = 0; i < lengthCMP; i++) {
            if ((readValue(arrayA, offsetA, strideA, i) | readFromByteArray(arrayMask, strideB, i)) != readValue(arrayB, offsetB, strideB, i)) {
                return false;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return true;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runMemCmp(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, int lengthCMP, int stubStride) {
        int strideA = stubStrideToStrideA(stubStride);
        int strideB = stubStrideToStrideB(stubStride);
        for (int i = 0; i < lengthCMP; i++) {
            int cmp = readValue(arrayA, offsetA, strideA, i) - readValue(arrayB, offsetB, strideB, i);
            if (cmp != 0) {
                return cmp;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return 0;
    }

    private static int runMemCmpBytes(Node location,
                    byte[] arrayA, long offsetA, int strideA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, int strideB, @SuppressWarnings("unused") boolean isNativeB, int lengthCMP) {
        assert strideA >= strideB;
        for (int i = 0; i < lengthCMP; i++) {
            int valueA = readValue(arrayA, offsetA, strideA, i);
            int valueB = readValue(arrayB, offsetB, strideB, i);
            for (int j = 0; j < 4; j++) {
                final int cmp;
                if (TStringGuards.littleEndian()) {
                    cmp = (valueA & 0xff) - (valueB & 0xff);
                    valueA >>= 8;
                    valueB >>= 8;
                } else {
                    cmp = ((valueA >> 24) & 0xff) - ((valueB >> 24) & 0xff);
                    valueA <<= 8;
                    valueB <<= 8;
                }
                if (cmp != 0) {
                    return cmp;
                }
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return 0;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runHashCode(Node location, byte[] array, long offset, int length, int stride, @SuppressWarnings("unused") boolean isNative) {
        int hash = 0;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + readValue(array, offset, stride, i);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return hash;
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    char[] stubArrayA, long stubOffsetA,
                    byte[] stubArrayB, long stubOffsetB, int lengthCPY, int stubStride) {
        int strideB = stubStrideToStrideB(stubStride);
        int offsetA = (int) (stubOffsetA - Unsafe.ARRAY_CHAR_BASE_OFFSET >> 1);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(stubArrayB, stubOffsetB, strideB, i, stubArrayA[offsetA + i]);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    int[] stubArrayA, long stubOffsetA,
                    byte[] stubArrayB, long stubOffsetB, int lengthCPY, int stubStride) {
        int strideB = stubStrideToStrideB(stubStride);
        int offsetA = (int) (stubOffsetA - Unsafe.ARRAY_INT_BASE_OFFSET >> 2);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(stubArrayB, stubOffsetB, strideB, i, stubArrayA[offsetA + i]);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    byte[] stubArrayA, long stubOffsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] stubArrayB, long stubOffsetB, @SuppressWarnings("unused") boolean isNativeB, int lengthCPY, int stubStride) {
        int strideA = stubStrideToStrideA(stubStride);
        int strideB = stubStrideToStrideB(stubStride);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(stubArrayB, stubOffsetB, strideB, i, readValue(stubArrayA, stubOffsetA, strideA, i));
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesLatin1(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative) {
        for (int i = 0; i < length; i++) {
            if (readValueS0(array, offset, i) > 0x7f) {
                return TSCodeRange.get8Bit();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.get7Bit();
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesBMP(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS1(array, offset, i) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return TSCodeRange.get7Bit();
        }
        for (; i < length; i++) {
            if (readValueS1(array, offset, i) > 0xff) {
                return TSCodeRange.get16Bit();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.get8Bit();
    }

    private static int runCalcStringAttributesUTF32I(Node location, int[] array, long offset, int length) {
        return runCalcStringAttributesUTF32AnyArray(location, array, offset, length);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesUTF32(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative) {
        return runCalcStringAttributesUTF32AnyArray(location, array, offset, length);
    }

    private static int readValueS2I(Object array, long offset, int i) {
        if (array instanceof int[]) {
            return ((int[]) array)[((int) ((offset - Unsafe.ARRAY_INT_BASE_OFFSET) >> 2)) + i];
        }
        return readValueS2((byte[]) array, offset, i);
    }

    private static int runCalcStringAttributesUTF32AnyArray(Node location, Object array, long offset, int length) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (Integer.toUnsignedLong(readValueS2I(array, offset, i)) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return TSCodeRange.get7Bit();
        }
        for (; i < length; i++) {
            if (Integer.toUnsignedLong(readValueS2I(array, offset, i)) > 0xff) {
                codeRange = TSCodeRange.get16Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is16Bit(codeRange)) {
            return TSCodeRange.get8Bit();
        }
        for (; i < length; i++) {
            int value = readValueS2I(array, offset, i);
            if (Integer.toUnsignedLong(value) > 0xffff) {
                codeRange = TSCodeRange.getValidFixedWidth();
                break;
            }
            if (Encodings.isUTF16Surrogate(value)) {
                return TSCodeRange.getBrokenFixedWidth();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.isValid(codeRange)) {
            return TSCodeRange.get16Bit();
        }
        for (; i < length; i++) {
            int value = readValueS2I(array, offset, i);
            if (Integer.toUnsignedLong(value) > 0x10ffff || Encodings.isUTF16Surrogate(value)) {
                return TSCodeRange.getBrokenFixedWidth();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.getValidFixedWidth();
    }

    /**
     * Intrinsic candidate.
     */
    private static long runCalcStringAttributesUTF8(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative, boolean assumeValid) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS0(array, offset, i) > 0x7f) {
                codeRange = TSCodeRange.getValidMultiByte();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.isValidMultiByte(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }
        int nCodePoints = i;
        if (assumeValid) {
            for (; i < length; i++) {
                if (!Encodings.isUTF8ContinuationByte(readValueS0(array, offset, i))) {
                    nCodePoints++;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return StringAttributes.create(nCodePoints, TSCodeRange.getValidMultiByte());
        } else {
            /*
             * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de> See
             * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
             */
            int state = Encodings.UTF8_ACCEPT;
            // int codepoint = 0;
            for (; i < length; i++) {
                int b = readValueS0(array, offset, i);
                if (!Encodings.isUTF8ContinuationByte(b)) {
                    nCodePoints++;
                }
                int type = Encodings.UTF_8_STATE_MACHINE[b];
                state = Encodings.UTF_8_STATE_MACHINE[256 + state + type];
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            if (state != Encodings.UTF8_ACCEPT) {
                codeRange = TSCodeRange.getBrokenMultiByte();
            }
            return StringAttributes.create(nCodePoints, codeRange);
        }
    }

    private static long runCalcStringAttributesUTF16C(Node location, char[] array, long offset, int length) {
        return runCalcStringAttributesUTF16AnyArray(location, array, offset, length, false);
    }

    /**
     * Intrinsic candidate.
     */
    private static long runCalcStringAttributesUTF16(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative, boolean assumeValid) {
        return runCalcStringAttributesUTF16AnyArray(location, array, offset, length, assumeValid);
    }

    private static char readValueS1C(Object array, long offset, int i) {
        if (array instanceof char[]) {
            return ((char[]) array)[((int) ((offset - Unsafe.ARRAY_CHAR_BASE_OFFSET) >> 1)) + i];
        }
        return (char) readValueS1((byte[]) array, offset, i);
    }

    private static long runCalcStringAttributesUTF16AnyArray(Node location, Object array, long offset, int length, boolean assumeValid) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS1C(array, offset, i) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }
        for (; i < length; i++) {
            if (readValueS1C(array, offset, i) > 0xff) {
                codeRange = TSCodeRange.get16Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is16Bit(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get8Bit());
        }
        for (; i < length; i++) {
            char c = readValueS1C(array, offset, i);
            if (assumeValid ? Encodings.isUTF16HighSurrogate(c) : Encodings.isUTF16Surrogate(c)) {
                codeRange = TSCodeRange.getValidMultiByte();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.isValidMultiByte(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get16Bit());
        }
        int nCodePoints = length;
        if (assumeValid) {
            for (; i < length; i++) {
                if (Encodings.isUTF16HighSurrogate(readValueS1C(array, offset, i))) {
                    nCodePoints--;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return StringAttributes.create(nCodePoints, TSCodeRange.getValidMultiByte());
        } else {
            for (; i < length; i++) {
                char c = readValueS1C(array, offset, i);
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16LowSurrogate(c) || !(i + 1 < length && Encodings.isUTF16LowSurrogate(readValueS1C(array, offset, i + 1)))) {
                        codeRange = TSCodeRange.getBrokenMultiByte();
                    } else {
                        i++;
                        nCodePoints--;
                    }
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return StringAttributes.create(nCodePoints, codeRange);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCodePointIndexToByteIndexUTF8Valid(Node location, byte[] array, long offset, int length, int index, @SuppressWarnings("unused") boolean isNative) {
        int cpi = index;
        for (int i = 0; i < length; i++) {
            if (!isUTF8ContinuationByte(readValueS0(array, offset, i))) {
                if (--cpi < 0) {
                    return i;
                }
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return cpi == 0 ? length : -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCodePointIndexToByteIndexUTF16Valid(Node location, byte[] array, long offset, int length, int index, @SuppressWarnings("unused") boolean isNative) {
        int cpi = index;
        for (int i = 0; i < length; i++) {
            if (!isUTF16LowSurrogate(readValueS1(array, offset, i))) {
                if (--cpi < 0) {
                    return i;
                }
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return cpi == 0 ? length : -1;
    }

    static long byteLength(Object array) {
        CompilerAsserts.neverPartOfCompilation();
        if (array instanceof byte[]) {
            return ((byte[]) array).length;
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    private static boolean rangeInBounds(int rangeStart, int rangeLength, int arrayLength) {
        return Integer.toUnsignedLong(rangeStart) + Integer.toUnsignedLong(rangeLength) <= arrayLength;
    }

    private static boolean isNativePointer(Object arrayB) {
        return arrayB instanceof AbstractTruffleString.NativePointer;
    }

    /**
     * Get raw native pointer from NativePointer object.
     * <p>
     * NOTE: any use of this pointer must be guarded by a reachability fence on the NativePointer
     * object!
     */
    private static long nativePointer(Object array) {
        return ((AbstractTruffleString.NativePointer) array).pointer;
    }

    private static int stubStride(int strideA, int strideB) {
        assert Stride.isStride(strideA);
        assert Stride.isStride(strideB);
        return (strideA * 3) + strideB;
    }

    private static int stubStrideToStrideA(int stubStride) {
        assert 0 <= stubStride && stubStride < 9 : stubStride;
        return stubStride / 3;
    }

    private static int stubStrideToStrideB(int stubStride) {
        assert 0 <= stubStride && stubStride < 9 : stubStride;
        return stubStride % 3;
    }

    private static int uInt(byte value) {
        return Byte.toUnsignedInt(value);
    }

    static void validateRegion(byte[] stubArray, int offset, int length, int stride) {
        if (!validRegion(stubArray, offset, length, stride)) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegion(char[] array, int offset, int length) {
        int charOffset = offset >> 1;
        if ((Integer.toUnsignedLong(charOffset) + (Integer.toUnsignedLong(length))) > array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegion(int[] array, int offset, int length) {
        int intOffset = offset >> 2;
        if ((Integer.toUnsignedLong(intOffset) + (Integer.toUnsignedLong(length))) > array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegionIndex(byte[] stubArray, int offset, int length, int stride, int i) {
        if (!validRegionIndex(stubArray, offset, length, stride, i)) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static boolean validRegion(byte[] stubArray, int offset, int length, int stride) {
        return validOffsetOrLength(stubArray, offset, length, stride);
    }

    private static boolean validRegionIndex(byte[] stubArray, int offset, int length, int stride, int i) {
        return validOffsetOrLength(stubArray, offset, length, stride) && validIndex(length, i);
    }

    private static boolean validOffsetOrLength(byte[] stubArray, int offset, int length, int stride) {
        if (stubArray == null) {
            return offset >= 0 && length >= 0;
        } else {
            return (Integer.toUnsignedLong(offset) + (Integer.toUnsignedLong(length) << stride)) <= stubArray.length;
        }
    }

    private static boolean validIndex(int length, int i) {
        return Integer.compareUnsigned(i, length) < 0;
    }
}
