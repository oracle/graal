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
import static com.oracle.truffle.api.strings.TStringUnsafe.byteArrayBaseOffset;

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import sun.misc.Unsafe;

final class TStringOps {

    static int readFromByteArray(byte[] array, int stride, int i) {
        final long offset = byteArrayBaseOffset();
        assert validateRegionIndex(array, 0, array.length >> stride, stride, i);
        return switch (stride) {
            case 0 -> uInt(array[i]);
            case 1 -> TStringUnsafe.getChar(array, offset + ((long) i << 1));
            default -> TStringUnsafe.getInt(array, offset + ((long) i << 2));
        };
    }

    static int readFromByteArrayS1(byte[] array, int i) {
        final long offset = byteArrayBaseOffset();
        assert validateRegionIndex(array, 0, array.length >> 1, 1, i);
        return TStringUnsafe.getChar(array, offset + ((long) i << 1));
    }

    static void writeToByteArrayS1(byte[] array, int i, int value) {
        final long offset = byteArrayBaseOffset();
        assert validateRegionIndex(array, 0, array.length >> 1, 1, i);
        TStringUnsafe.putChar(array, offset + ((long) i << 1), (char) value);
    }

    static void writeToByteArrayS2(byte[] array, int i, int value) {
        final long offset = byteArrayBaseOffset();
        assert validateRegionIndex(array, 0, array.length >> 2, 2, i);
        TStringUnsafe.putInt(array, offset + ((long) i << 2), value);
    }

    static void writeToByteArray(byte[] array, int stride, int i, int value) {
        switch (stride) {
            case 0 -> array[i] = (byte) value;
            case 1 -> writeToByteArrayS1(array, i, value);
            default -> writeToByteArrayS2(array, i, value);
        }
    }

    static int readValue(AbstractTruffleString a, byte[] arrayA, long offsetA, int stride, int i) {
        return readValue(arrayA, offsetA, a.length(), stride, i);
    }

    static int readS0(AbstractTruffleString a, byte[] arrayA, long offsetA, int i) {
        assert validateRegionIndexWithBaseOffset(arrayA, offsetA, a.length(), 0, i);
        return uInt(TStringUnsafe.getByte(arrayA, offsetA + i));
    }

    static char readS1(AbstractTruffleString a, byte[] arrayA, long offsetA, int i) {
        assert validateRegionIndexWithBaseOffset(arrayA, offsetA, a.length(), 1, i);
        return TStringUnsafe.getChar(arrayA, offsetA + ((long) i << 1));
    }

    static int readS2(AbstractTruffleString a, byte[] arrayA, long offsetA, int i) {
        assert validateRegionIndexWithBaseOffset(arrayA, offsetA, a.length(), 2, i);
        return TStringUnsafe.getInt(arrayA, offsetA + ((long) i << 2));
    }

    static int readS0(byte[] array, long offset, int length, int i) {
        assert validateRegionIndexWithBaseOffset(array, offset, length, 0, i);
        return uInt(TStringUnsafe.getByte(array, offset + i));
    }

    static char readS1(byte[] array, long offset, int length, int i) {
        assert validateRegionIndexWithBaseOffset(array, offset, length, 1, i);
        return TStringUnsafe.getChar(array, offset + ((long) i << 1));
    }

    static int readS2(byte[] array, long offset, int length, int i) {
        assert validateRegionIndexWithBaseOffset(array, offset, length, 2, i);
        return TStringUnsafe.getInt(array, offset + ((long) i << 2));
    }

    static long readS3(byte[] array, long offset, int length) {
        assert validateRegionWithBaseOffset(array, offset, length, 3);
        return TStringUnsafe.getLong(array, offset);
    }

    static void writeS0(byte[] array, long offset, int length, int i, byte value) {
        assert validateRegionIndexWithBaseOffset(array, offset, length, 0, i);
        TStringUnsafe.putByte(array, offset + i, value);
    }

    static int readValue(byte[] array, long offset, int length, int stride, int i) {
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, i);
        return readValue(array, offset, stride, i);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS0(byte[] array, long offset, int i) {
        return uInt(TStringUnsafe.getByte(array, offset + i));
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static char readValueS1(byte[] array, long offset, int i) {
        return TStringUnsafe.getChar(array, offset + ((long) i << 1));
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS2(byte[] array, long offset, int i) {
        return TStringUnsafe.getInt(array, offset + ((long) i << 2));
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

    static int indexOfAnyByte(Node location, AbstractTruffleString a, byte[] arrayA, long offsetA, int fromIndex, int toIndex, byte[] values) {
        assert a.stride() == 0;
        return indexOfAnyByteIntl(location, arrayA, offsetA, toIndex, fromIndex, values);
    }

    private static int indexOfAnyByteIntl(Node location, byte[] array, long offset, int length, int fromIndex, byte[] values) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, 0, fromIndex);
        switch (values.length) {
            case 1:
                return runIndexOfAny1(location, array, offset, length, 0, isNative, fromIndex, uInt(values[0]));
            case 2:
                return runIndexOfAny2(location, array, offset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]));
            case 3:
                return runIndexOfAny3(location, array, offset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]), uInt(values[2]));
            case 4:
                return runIndexOfAny4(location, array, offset, length, 0, isNative, fromIndex, uInt(values[0]), uInt(values[1]), uInt(values[2]), uInt(values[3]));
            default:
                return runIndexOfAnyByte(location, array, offset, length, fromIndex, values);
        }
    }

    static int indexOfAnyChar(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, char[] values) {
        assert stride == 0 || stride == 1;
        return indexOfAnyCharIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, values);
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOfAnyCharIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, char[] values) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        if (stride == 0) {
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, array, offset, length, 0, isNative, fromIndex, values[0]);
                case 2:
                    return runIndexOfAny2(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1]);
                case 3:
                    return runIndexOfAny3(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1], values[2]);
                case 4:
                    return runIndexOfAny4(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1], values[2], values[3]);
            }
        } else {
            assert stride == 1;
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, array, offset, length, 1, isNative, fromIndex, values[0]);
                case 2:
                    return runIndexOfAny2(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1]);
                case 3:
                    return runIndexOfAny3(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1], values[2]);
                case 4:
                    return runIndexOfAny4(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1], values[2], values[3]);
            }
        }
        return runIndexOfAnyChar(location, array, offset, length, stride, fromIndex, values);
    }

    static int indexOfAnyInt(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, int[] values) {
        return indexOfAnyIntIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, values);
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOfAnyIntIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int[] values) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        if (stride == 0) {
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, array, offset, length, 0, isNative, fromIndex, values[0]);
                case 2:
                    return runIndexOfAny2(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1]);
                case 3:
                    return runIndexOfAny3(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1], values[2]);
                case 4:
                    return runIndexOfAny4(location, array, offset, length, 0, isNative, fromIndex, values[0], values[1], values[2], values[3]);
            }
        } else if (stride == 1) {
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, array, offset, length, 1, isNative, fromIndex, values[0]);
                case 2:
                    return runIndexOfAny2(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1]);
                case 3:
                    return runIndexOfAny3(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1], values[2]);
                case 4:
                    return runIndexOfAny4(location, array, offset, length, 1, isNative, fromIndex, values[0], values[1], values[2], values[3]);
            }
        } else {
            assert stride == 2;
            switch (values.length) {
                case 1:
                    return runIndexOfAny1(location, array, offset, length, 2, isNative, fromIndex, values[0]);
                case 2:
                    return runIndexOfAny2(location, array, offset, length, 2, isNative, fromIndex, values[0], values[1]);
                case 3:
                    return runIndexOfAny3(location, array, offset, length, 2, isNative, fromIndex, values[0], values[1], values[2]);
                case 4:
                    return runIndexOfAny4(location, array, offset, length, 2, isNative, fromIndex, values[0], values[1], values[2], values[3]);
            }
        }
        return runIndexOfAnyInt(location, array, offset, length, stride, fromIndex, values);
    }

    static int indexOfAnyIntRange(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, int[] ranges) {
        return indexOfAnyIntRangeIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, ranges);
    }

    private static int indexOfAnyIntRangeIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int[] ranges) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        if (stride == 0) {
            if (ranges.length == 2) {
                return runIndexOfRange1(location, array, offset, length, 0, isNative, fromIndex, ranges[0], ranges[1]);
            } else if (ranges.length == 4) {
                return runIndexOfRange2(location, array, offset, length, 0, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
            }
        } else if (stride == 1) {
            if (ranges.length == 2) {
                return runIndexOfRange1(location, array, offset, length, 1, isNative, fromIndex, ranges[0], ranges[1]);
            } else if (ranges.length == 4) {
                return runIndexOfRange2(location, array, offset, length, 1, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
            }
        } else {
            assert stride == 2;
            if (ranges.length == 2) {
                return runIndexOfRange1(location, array, offset, length, 2, isNative, fromIndex, ranges[0], ranges[1]);
            } else if (ranges.length == 4) {
                return runIndexOfRange2(location, array, offset, length, 2, isNative, fromIndex, ranges[0], ranges[1], ranges[2], ranges[3]);
            }
        }
        return runIndexOfAnyIntRange(location, array, offset, length, stride, fromIndex, ranges);
    }

    static int indexOfTable(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, byte[] tables) {
        return indexOfTableIntl(location, arrayA, offsetA, toIndex, stride, fromIndex, tables);
    }

    private static int indexOfTableIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, byte[] tables) {
        assert tables.length == 32;
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        if (stride == 0) {
            return runIndexOfTable(location, array, offset, length, 0, isNative, fromIndex, tables);
        } else if (stride == 1) {
            return runIndexOfTable(location, array, offset, length, 1, isNative, fromIndex, tables);
        } else {
            assert stride == 2;
            return runIndexOfTable(location, array, offset, length, 2, isNative, fromIndex, tables);
        }
    }

    static int indexOfCodePointWithStride(Node location, byte[] arrayA, long offsetA, int strideA, int fromIndex, int toIndex, int codepoint) {
        return indexOfCodePointWithStrideIntl(location, arrayA, offsetA, toIndex, strideA, fromIndex, codepoint);
    }

    private static int indexOfCodePointWithStrideIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int v1) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        switch (stride) {
            case 0:
                return runIndexOfAny1(location, array, offset, length, 0, isNative, fromIndex, v1);
            case 1:
                return runIndexOfAny1(location, array, offset, length, 1, isNative, fromIndex, v1);
            default:
                assert stride == 2;
                return runIndexOfAny1(location, array, offset, length, 2, isNative, fromIndex, v1);
        }
    }

    static int indexOfCodePointWithOrMaskWithStride(Node location, byte[] arrayA, long offsetA, int strideA, int fromIndex, int toIndex, int codepoint, int maskA) {
        return indexOfCodePointWithMaskWithStrideIntl(location, arrayA, offsetA, toIndex, strideA, fromIndex, codepoint, maskA);
    }

    static int indexOfCodePointWithMaskWithStrideIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int v1, int mask1) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        switch (stride) {
            case 0:
                return (v1 ^ mask1) <= 0xff ? runIndexOfWithOrMaskWithStride(location, array, offset, length, 0, isNative, fromIndex, v1, mask1) : -1;
            case 1:
                return (v1 ^ mask1) <= 0xffff ? runIndexOfWithOrMaskWithStride(location, array, offset, length, 1, isNative, fromIndex, v1, mask1) : -1;
            default:
                assert stride == 2;
                return runIndexOfWithOrMaskWithStride(location, array, offset, length, 2, isNative, fromIndex, v1, mask1);
        }
    }

    static int indexOf2ConsecutiveWithStride(Node location, byte[] arrayA, long offsetA, int strideA, int fromIndex, int toIndex, int v1, int v2) {
        return indexOf2ConsecutiveWithStrideIntl(location, arrayA, offsetA, toIndex, strideA, fromIndex, v1, v2);
    }

    private static int indexOf2ConsecutiveWithStrideIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int v1, int v2) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        switch (stride) {
            case 0:
                return (v1 | v2) <= 0xff ? runIndexOf2ConsecutiveWithStride(location, array, offset, length, 0, isNative, fromIndex, v1, v2) : -1;
            case 1:
                return (v1 | v2) <= 0xffff ? runIndexOf2ConsecutiveWithStride(location, array, offset, length, 1, isNative, fromIndex, v1, v2) : -1;
            default:
                assert stride == 2;
                return runIndexOf2ConsecutiveWithStride(location, array, offset, length, 2, isNative, fromIndex, v1, v2);
        }
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, byte[] array, long offset, int length, int stride, int fromIndex, int v1, int v2, int mask1, int mask2) {
        final boolean isNative = array == null;
        assert validateRegionIndexWithBaseOffset(array, offset, length, stride, fromIndex);
        switch (stride) {
            case 0:
                return ((v1 ^ mask1) | (v2 ^ mask2)) <= 0xff ? runIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, length, 0, isNative, fromIndex, v1, v2, mask1, mask2) : -1;
            case 1:
                return ((v1 ^ mask1) | (v2 ^ mask2)) <= 0xffff ? runIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, length, 1, isNative, fromIndex, v1, v2, mask1, mask2) : -1;
            default:
                assert stride == 2;
                return runIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, length, 2, isNative, fromIndex, v1, v2, mask1, mask2);
        }
    }

    static int indexOfStringWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, byte[] arrayA, long offsetA, int strideA,
                    AbstractTruffleString b, byte[] arrayB, long offsetB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        return indexOfStringWithOrMaskWithStride(location,
                        arrayA, offsetA, a.length(), strideA,
                        arrayB, offsetB, b.length(), strideB, fromIndex, toIndex, maskB);
    }

    static int indexOfStringWithOrMaskWithStride(Node location,
                    byte[] arrayA, long offsetA, int lengthA, int strideA,
                    byte[] arrayB, long offsetB, int lengthB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        int offsetMask = byteArrayBaseOffset();
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

    static int lastIndexOfCodePointWithOrMaskWithStride(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, int codepoint, int mask) {
        return lastIndexOfCodePointWithOrMaskWithStrideIntl(location, arrayA, offsetA, stride, fromIndex, toIndex, codepoint, mask);
    }

    private static int lastIndexOfCodePointWithOrMaskWithStrideIntl(Node location, byte[] array, long offset, int stride, int fromIndex, int toIndex, int codepoint, int mask) {
        assert validateRegionIndexWithBaseOffset(array, offset, fromIndex, stride, toIndex);
        switch (stride) {
            case 0:
                return runLastIndexOfWithOrMaskWithStride(location, array, offset, 0, fromIndex, toIndex, codepoint, mask);
            case 1:
                return runLastIndexOfWithOrMaskWithStride(location, array, offset, 1, fromIndex, toIndex, codepoint, mask);
            default:
                assert stride == 2;
                return runLastIndexOfWithOrMaskWithStride(location, array, offset, 2, fromIndex, toIndex, codepoint, mask);
        }
    }

    static int lastIndexOf2ConsecutiveWithOrMaskWithStride(Node location, byte[] arrayA, long offsetA, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        return lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, offsetA, stride, fromIndex, toIndex, v1, v2, mask1, mask2);
    }

    private static int lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, byte[] array, long offset, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        assert validateRegionIndexWithBaseOffset(array, offset, fromIndex, stride, toIndex);
        switch (stride) {
            case 0:
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, 0, fromIndex, toIndex, v1, v2, mask1, mask2);
            case 1:
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, 1, fromIndex, toIndex, v1, v2, mask1, mask2);
            default:
                assert stride == 2;
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, array, offset, 2, fromIndex, toIndex, v1, v2, mask1, mask2);
        }
    }

    static int lastIndexOfStringWithOrMaskWithStride(Node location,
                    byte[] arrayA, long offsetA, int lengthA, int strideA,
                    byte[] arrayB, long offsetB, int lengthB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        int offsetMask = byteArrayBaseOffset();
        assert lengthB > 1;
        assert lengthA >= lengthB;
        int index = fromIndex;
        final int b0 = readValue(arrayB, offsetB, lengthB, strideB, lengthB - 2);
        final int b1 = readValue(arrayB, offsetB, lengthB, strideB, lengthB - 1);
        final int mask0 = maskB == null ? 0 : readValue(maskB, offsetMask, lengthB, strideB, lengthB - 2);
        final int mask1 = maskB == null ? 0 : readValue(maskB, offsetMask, lengthB, strideB, lengthB - 1);
        final int toIndex2Consecutive = toIndex + lengthB - 2;
        while (index > toIndex2Consecutive) {
            index = lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, offsetA, strideA, index, toIndex2Consecutive, b0, b1, mask0, mask1);
            if (index < 0) {
                return -1;
            }
            index += 2;
            if (lengthB == 2 || regionEqualsWithOrMaskWithStrideIntl(location,
                            arrayA, offsetA, lengthA, strideA, index - lengthB,
                            arrayB, offsetB, lengthB, strideB, 0, maskB, lengthB)) {
                return index - lengthB;
            }
            index--;
            TStringConstants.truffleSafePointPoll(location, index);
        }
        return -1;
    }

    static boolean regionEqualsWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, byte[] arrayA, long offsetA, int strideA, int fromIndexA,
                    AbstractTruffleString b, byte[] arrayB, long offsetB, int strideB, int fromIndexB,
                    byte[] maskB, int lengthCMP) {
        return regionEqualsWithOrMaskWithStrideIntl(location,
                        arrayA, offsetA, a.length(), strideA, fromIndexA,
                        arrayB, offsetB, b.length(), strideB, fromIndexB, maskB, lengthCMP);
    }

    private static boolean regionEqualsWithOrMaskWithStrideIntl(Node location,
                    byte[] arrayA, long offsetA, int lengthA, int strideA, int fromIndexA,
                    byte[] arrayB, long offsetB, int lengthB, int strideB, int fromIndexB, byte[] maskB, int lengthCMP) {
        if (!rangeInBounds(fromIndexA, lengthCMP, lengthA) || !rangeInBounds(fromIndexB, lengthCMP, lengthB)) {
            return false;
        }
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        final long combinedOffsetA = offsetA + ((long) fromIndexA << strideA);
        final long combinedOffsetB = offsetB + ((long) fromIndexB << strideB);
        assert validateRegionWithBaseOffset(arrayA, combinedOffsetA, lengthCMP, strideA);
        assert validateRegionWithBaseOffset(arrayB, combinedOffsetB, lengthCMP, strideB);
        final int stubStride = stubStride(strideA, strideB);
        if (maskB == null) {
            return runRegionEqualsWithStride(location,
                            arrayA, combinedOffsetA, isNativeA,
                            arrayB, combinedOffsetB, isNativeB, lengthCMP, stubStride);

        } else {
            assert validateRegion(maskB, 0, lengthCMP, strideB);
            return runRegionEqualsWithOrMaskWithStride(location,
                            arrayA, combinedOffsetA, isNativeA,
                            arrayB, combinedOffsetB, isNativeB, maskB, lengthCMP, stubStride);
        }
    }

    static int memcmpWithStride(Node location,
                    AbstractTruffleString a, byte[] arrayA, long offsetA, int strideA,
                    AbstractTruffleString b, byte[] arrayB, long offsetB, int strideB, int lengthCMP) {
        assert lengthCMP <= a.length();
        assert lengthCMP <= b.length();
        return memcmpWithStrideIntl(location, arrayA, offsetA, strideA, arrayB, offsetB, strideB, lengthCMP);
    }

    private static int memcmpWithStrideIntl(Node location,
                    byte[] arrayA, long offsetA, int strideA,
                    byte[] arrayB, long offsetB, int strideB, int lengthCMP) {
        if (lengthCMP == 0) {
            return 0;
        }
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCMP, strideA);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCMP, strideB);
        return runMemCmp(location,
                        arrayA, offsetA, isNativeA,
                        arrayB, offsetB, isNativeB, lengthCMP, stubStride(strideA, strideB));
    }

    static int memcmpBytesWithStride(Node location,
                    AbstractTruffleString a, byte[] arrayA, long offsetA, int strideA,
                    AbstractTruffleString b, byte[] arrayB, long offsetB, int strideB, int lengthCMP) {
        assert lengthCMP <= a.length();
        assert lengthCMP <= b.length();
        return memcmpBytesWithStrideIntl(location, arrayA, offsetA, strideA, arrayB, offsetB, strideB, lengthCMP);
    }

    private static int memcmpBytesWithStrideIntl(Node location,
                    byte[] arrayA, long offsetA, int strideA,
                    byte[] arrayB, long offsetB, int strideB, int lengthCMP) {
        if (lengthCMP == 0) {
            return 0;
        }
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCMP, strideA);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCMP, strideB);
        if (strideA == strideB) {
            switch (strideA) {
                case 0:
                    return runMemCmp(location,
                                    arrayA, offsetA, isNativeA,
                                    arrayB, offsetB, isNativeB, lengthCMP, 0);
                case 1:
                    return runMemCmpBytes(location,
                                    arrayA, offsetA, 1, isNativeA,
                                    arrayB, offsetB, 1, isNativeB, lengthCMP);
                default:
                    assert strideA == 2;
                    return runMemCmpBytes(location,
                                    arrayA, offsetA, 2, isNativeA,
                                    arrayB, offsetB, 2, isNativeB, lengthCMP);
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
            swappedArrayA = arrayB;
            swappedArrayB = arrayA;
            swappedOffsetA = offsetB;
            swappedOffsetB = offsetA;
            swappedIsNativeA = isNativeB;
            swappedIsNativeB = isNativeA;
            swappedResult = -1;
        } else {
            swappedStrideA = strideA;
            swappedStrideB = strideB;
            swappedArrayA = arrayA;
            swappedArrayB = arrayB;
            swappedOffsetA = offsetA;
            swappedOffsetB = offsetB;
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
    }

    static int hashCodeWithStride(Node location, AbstractTruffleString a, byte[] arrayA, long offsetA, int stride) {
        int length = a.length();
        return hashCodeWithStrideIntl(location, arrayA, offsetA, length, stride);
    }

    private static int hashCodeWithStrideIntl(Node location, byte[] array, long offset, int length, int stride) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, stride);
        switch (stride) {
            case 0:
                return runHashCode(location, array, offset, length, 0, isNative);
            case 1:
                return runHashCode(location, array, offset, length, 1, isNative);
            default:
                assert stride == 2;
                return runHashCode(location, array, offset, length, 2, isNative);
        }
    }

    // arrayB is returned for testing purposes, do not remove
    static byte[] arraycopyWithStrideCB(Node location,
                    char[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int strideB, int lengthCPY) {
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCPY);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCPY, strideB);
        runArrayCopy(location,
                        arrayA, offsetA,
                        arrayB, offsetB, lengthCPY, stubStride(1, strideB));
        return arrayB;
    }

    // arrayB is returned for testing purposes, do not remove
    static byte[] arraycopyWithStrideIB(Node location,
                    int[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int strideB, int lengthCPY) {
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCPY);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCPY, strideB);
        runArrayCopy(location,
                        arrayA, offsetA,
                        arrayB, offsetB, lengthCPY, stubStride(2, strideB));
        return arrayB;
    }

    // arrayB is returned for testing purposes, do not remove
    static byte[] arraycopyWithStride(Node location,
                    byte[] arrayA, long offsetA, int strideA, int fromIndexA,
                    byte[] arrayB, long offsetB, int strideB, int fromIndexB, int lengthCPY) {
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        final long combinedOffsetA = offsetA + ((long) fromIndexA << strideA);
        final long combinedOffsetB = offsetB + ((long) fromIndexB << strideB);
        assert validateRegionWithBaseOffset(arrayA, combinedOffsetA, lengthCPY, strideA);
        assert validateRegionWithBaseOffset(arrayB, combinedOffsetB, lengthCPY, strideB);
        runArrayCopy(location,
                        arrayA, combinedOffsetA, isNativeA,
                        arrayB, combinedOffsetB, isNativeB, lengthCPY, stubStride(strideA, strideB));
        return arrayB;
    }

    static byte[] arraycopyOfWithStride(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int lengthB, int strideB) {
        byte[] dst = new byte[lengthB << strideB];
        arraycopyWithStride(location, arrayA, offsetA, strideA, 0, dst, byteArrayBaseOffset(), strideB, 0, lengthA);
        return dst;
    }

    // arrayB is returned for testing purposes, do not remove
    static Object byteSwapS1(Node location,
                    byte[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int lengthCPY) {
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCPY, 1);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCPY, 1);
        runByteSwapS1(location,
                        arrayA, offsetA, isNativeA,
                        arrayB, offsetB, isNativeB, lengthCPY);
        return arrayB;
    }

    // arrayB is returned for testing purposes, do not remove
    static Object byteSwapS2(Node location,
                    byte[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int lengthCPY) {
        final boolean isNativeA = arrayA == null;
        final boolean isNativeB = arrayB == null;
        assert validateRegionWithBaseOffset(arrayA, offsetA, lengthCPY, 2);
        assert validateRegionWithBaseOffset(arrayB, offsetB, lengthCPY, 2);
        runByteSwapS2(location,
                        arrayA, offsetA, isNativeA,
                        arrayB, offsetB, isNativeB, lengthCPY);
        return arrayB;
    }

    static int calcStringAttributesLatin1(Node location, byte[] array, long offset, int length) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 0);
        return runCalcStringAttributesLatin1(location, array, offset, length, isNative);
    }

    static int calcStringAttributesBMP(Node location, byte[] array, long offset, int length) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 1);
        return runCalcStringAttributesBMP(location, array, offset, length, isNative);
    }

    static long calcStringAttributesUTF8(Node location, byte[] array, long offset, int length, boolean assumeValid, boolean isAtEnd, InlinedConditionProfile brokenProfile) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 0);
        if (assumeValid && !Encodings.isUTF8ContinuationByte(readS0(array, offset, length, 0)) && (isAtEnd || !Encodings.isUTF8ContinuationByte(readS0(array, offset, length + 1, length)))) {
            return runCalcStringAttributesUTF8(location, array, offset, length, isNative, true);
        } else {
            return calcStringAttributesUTF8Invalid(location, array, offset, length, brokenProfile, isNative);
        }
    }

    @InliningCutoff
    private static long calcStringAttributesUTF8Invalid(Node location, byte[] array, long offset, int length, InlinedConditionProfile brokenProfile, boolean isNative) {
        long attrs = runCalcStringAttributesUTF8(location, array, offset, length, isNative, false);
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

    static long calcStringAttributesUTF16C(Node location, char[] array, long offset, int length) {
        assert validateRegionWithBaseOffset(array, offset, length);
        return runCalcStringAttributesUTF16C(location, array, offset, length);
    }

    static long calcStringAttributesUTF16(Node location, byte[] array, long offset, int length, boolean assumeValid) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 1);
        long attrs;
        if (assumeValid) {
            attrs = runCalcStringAttributesUTF16(location, array, offset, length, isNative, true);
        } else {
            attrs = runCalcStringAttributesUTF16(location, array, offset, length, isNative, false);
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
    }

    @InliningCutoff
    static long calcStringAttributesUTF16FE(Node location, byte[] array, long offset, int length) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 1);
        return runCalcStringAttributesUTF16FE(location, array, offset, length, isNative);
    }

    static int calcStringAttributesUTF32I(Node location, int[] array, long offset, int length) {
        assert validateRegionWithBaseOffset(array, offset, length);
        return runCalcStringAttributesUTF32I(location, array, offset, length);
    }

    static int calcStringAttributesUTF32(Node location, byte[] array, long offset, int length) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 2);
        return runCalcStringAttributesUTF32(location, array, offset, length, isNative);
    }

    @InliningCutoff
    static int calcStringAttributesUTF32FE(Node location, byte[] array, long offset, int length) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 2);
        return runCalcStringAttributesUTF32FE(location, array, offset, length, isNative);
    }

    static int codePointIndexToByteIndexUTF8Valid(Node location, byte[] array, long offset, int length, int index) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 0);
        return runCodePointIndexToByteIndexUTF8Valid(location, array, offset, length, index, isNative);
    }

    static int codePointIndexToByteIndexUTF16Valid(Node location, byte[] array, long offset, int length, int index) {
        final boolean isNative = array == null;
        assert validateRegionWithBaseOffset(array, offset, length, 1);
        return runCodePointIndexToByteIndexUTF16Valid(location, array, offset, length, index, isNative);
    }

    private static int runIndexOfAnyByte(Node location, byte[] array, long offset, int length, int fromIndex, byte... needle) {
        for (int i = fromIndex; i < length; i++) {
            int value = readValueS0(array, offset, i);
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
                    char[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int lengthCPY, int stubStride) {
        int strideB = stubStrideToStrideB(stubStride);
        int charOffsetA = (int) (offsetA - Unsafe.ARRAY_CHAR_BASE_OFFSET >> 1);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(arrayB, offsetB, strideB, i, arrayA[charOffsetA + i]);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    int[] arrayA, long offsetA,
                    byte[] arrayB, long offsetB, int lengthCPY, int stubStride) {
        int strideB = stubStrideToStrideB(stubStride);
        int intOffsetA = (int) (offsetA - Unsafe.ARRAY_INT_BASE_OFFSET >> 2);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(arrayB, offsetB, strideB, i, arrayA[intOffsetA + i]);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, int lengthCPY, int stubStride) {
        int strideA = stubStrideToStrideA(stubStride);
        int strideB = stubStrideToStrideB(stubStride);
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(arrayB, offsetB, strideB, i, readValue(arrayA, offsetA, strideA, i));
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runByteSwapS1(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, int length) {
        for (int i = 0; i < length; i++) {
            writeValue(arrayB, offsetB, 1, i, Character.reverseBytes(readValueS1(arrayA, offsetA, i)));
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static void runByteSwapS2(Node location,
                    byte[] arrayA, long offsetA, @SuppressWarnings("unused") boolean isNativeA,
                    byte[] arrayB, long offsetB, @SuppressWarnings("unused") boolean isNativeB, int length) {
        for (int i = 0; i < length; i++) {
            writeValue(arrayB, offsetB, 2, i, Integer.reverseBytes(readValueS2(arrayA, offsetA, i)));
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
            if (!Encodings.isValidUnicodeCodepoint(value)) {
                return TSCodeRange.getBrokenFixedWidth();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.getValidFixedWidth();
    }

    private static int runCalcStringAttributesUTF32FE(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative) {
        int i = 0;
        for (; i < length; i++) {
            int value = Integer.reverseBytes(readValueS2(array, offset, i));
            if (!Encodings.isValidUnicodeCodepoint(value)) {
                return TSCodeRange.getBrokenMultiByte();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.getValidMultiByte();
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
        return readValueS1((byte[]) array, offset, i);
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
    private static long runCalcStringAttributesUTF16FE(Node location, byte[] array, long offset, int length, @SuppressWarnings("unused") boolean isNative) {
        int codeRange = TSCodeRange.getValidMultiByte();
        int i = 0;
        int nCodePoints = length;
        for (; i < length; i++) {
            char c = Character.reverseBytes(readValueS1(array, offset, i));
            if (Encodings.isUTF16Surrogate(c)) {
                if (Encodings.isUTF16LowSurrogate(c) || !(i + 1 < length && Encodings.isUTF16LowSurrogate(Character.reverseBytes(readValueS1(array, offset, i + 1))))) {
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

    private static boolean rangeInBounds(int rangeStart, int rangeLength, int arrayLength) {
        return Integer.toUnsignedLong(rangeStart) + Integer.toUnsignedLong(rangeLength) <= arrayLength;
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

    static boolean validateRegion(byte[] array, long offset, int length, int stride) {
        assert validRegion(array, offset, length, stride) : String.format("array.length: %d, offset: %d, length: %d, stride: %d", array.length, offset, length, stride);
        return true;
    }

    static boolean validateRegionWithBaseOffset(byte[] array, long offset, int length, int stride) {
        assert validRegionWithBaseOffset(array, offset, length, stride) : String.format("array.length: %d, offset: %d, length: %d, stride: %d", array.length, offset, length, stride);
        return true;
    }

    private static boolean validateRegionWithBaseOffset(char[] array, long offset, int length) {
        long charOffset = (offset - Unsafe.ARRAY_CHAR_BASE_OFFSET) >> 1;
        assert Long.compareUnsigned((charOffset + (Integer.toUnsignedLong(length))), array.length) <= 0 : String.format("array.length: %d, offset: %d, length: %d", array.length, offset, length);
        return true;
    }

    private static boolean validateRegionWithBaseOffset(int[] array, long offset, int length) {
        long intOffset = (offset - Unsafe.ARRAY_INT_BASE_OFFSET) >> 2;
        assert Long.compareUnsigned((intOffset + (Integer.toUnsignedLong(length))), array.length) <= 0 : String.format("array.length: %d, offset: %d, length: %d", array.length, offset, length);
        return true;
    }

    private static boolean validateRegionIndex(byte[] array, long offset, int length, int stride, int i) {
        assert validRegionIndex(array, offset, length, stride, i) : String.format("array.length: %d, offset: %d, length: %d, stride: %d, i: %d", array.length, offset, length, stride, i);
        return true;
    }

    private static boolean validateRegionIndexWithBaseOffset(byte[] array, long offset, int length, int stride, int i) {
        assert validRegionIndexWithBaseOffset(array, offset, length, stride, i) : String.format("array.length: %d, offset: %d, length: %d, stride: %d, i: %d", array.length, offset, length, stride, i);
        return true;
    }

    private static boolean validRegion(byte[] array, long offset, int length, int stride) {
        return validOffsetOrLength(array, offset, length, stride);
    }

    private static boolean validRegionWithBaseOffset(byte[] array, long offset, int length, int stride) {
        return validOffsetOrLengthWithBaseOffset(array, offset, length, stride);
    }

    private static boolean validRegionIndex(byte[] array, long offset, int length, int stride, int i) {
        return validOffsetOrLength(array, offset, length, stride) && validIndex(length, i);
    }

    private static boolean validRegionIndexWithBaseOffset(byte[] array, long offset, int length, int stride, int i) {
        return validOffsetOrLengthWithBaseOffset(array, offset, length, stride) && validIndex(length, i);
    }

    private static boolean validOffsetOrLength(byte[] array, long offset, int length, int stride) {
        if (array == null) {
            return offset >= 0 && length >= 0;
        } else {
            return Long.compareUnsigned(offset + (Integer.toUnsignedLong(length) << stride), array.length) <= 0;
        }
    }

    private static boolean validOffsetOrLengthWithBaseOffset(byte[] array, long offset, int length, int stride) {
        if (array == null) {
            return offset >= 0 && length >= 0;
        } else {
            return Long.compareUnsigned((offset - byteArrayBaseOffset()) + (Integer.toUnsignedLong(length) << stride), array.length) <= 0;
        }
    }

    private static boolean validIndex(int length, int i) {
        return Integer.compareUnsigned(i, length) < 0;
    }
}
