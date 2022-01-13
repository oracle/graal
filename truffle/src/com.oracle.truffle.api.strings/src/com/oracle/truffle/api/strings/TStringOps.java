/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

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
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 3, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        if (isNative) {
            return runReadS3Native(stubOffset);
        } else {
            return runReadS3Managed(stubArray, stubOffset);
        }
    }

    static void writeS0(Object array, int offset, int length, int i, byte value) {
        writeValue(array, offset, length, 0, i, uInt(value));
    }

    private static int readValue(Object array, int offset, int length, int stride, int i) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, i, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        return readValue(stubArray, stubOffset, stride, i, isNative);
    }

    private static void writeValue(Object array, int offset, int length, int stride, int i, int value) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, i, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        writeValue(stubArray, stubOffset, stride, i, isNative, value);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS0(Object array, long offset, int i, boolean isNative) {
        return readValue(array, offset, 0, i, isNative);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS1(Object array, long offset, int i, boolean isNative) {
        return readValue(array, offset, 1, i, isNative);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValueS2(Object array, long offset, int i, boolean isNative) {
        return readValue(array, offset, 2, i, isNative);
    }

    /**
     * Does NOT perform bounds checks, use from within intrinsic candidate methods only!
     */
    private static int readValue(Object array, long offset, int stride, int i, boolean isNative) {
        if (isNative) {
            assert array == null;
            switch (stride) {
                case 0:
                    return runReadS0Native(offset, i);
                case 1:
                    return runReadS1Native(offset, (long) i << 1);
                default:
                    return runReadS2Native(offset, (long) i << 2);
            }
        } else {
            if (array == null) {
                // make sure that array has a non-null stamp
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere();
            }
            switch (stride) {
                case 0:
                    return runReadS0Managed(array, offset + i);
                case 1:
                    return runReadS1Managed(array, offset + ((long) i << 1));
                default:
                    return runReadS2Managed(array, offset + ((long) i << 2));
            }
        }
    }

    private static void writeValue(Object array, long offset, int stride, int i, boolean isNative, int value) {
        if (isNative) {
            switch (stride) {
                case 0:
                    runWriteS0Native(offset, i, (byte) value);
                    return;
                case 1:
                    runWriteS1Native(offset, (long) i << 1, (char) value);
                    return;
                default:
                    runWriteS2Native(offset, (long) i << 2, value);
            }
        } else {
            if (array == null) {
                // make sure that array has a non-null stamp
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere();
            }
            switch (stride) {
                case 0:
                    runWriteS0Managed((byte[]) array, offset + i, (byte) value);
                    return;
                case 1:
                    runWriteS1Managed((byte[]) array, offset + ((long) i << 1), (char) value);
                    return;
                default:
                    runWriteS2Managed((byte[]) array, offset + ((long) i << 2), value);
            }
        }
    }

    static int indexOfAnyByte(Node location, AbstractTruffleString a, Object arrayA, int fromIndex, int toIndex, byte[] values) {
        assert a.stride() == 0;
        return indexOfAnyByteIntl(location, arrayA, a.offset(), toIndex, fromIndex, values);
    }

    private static int indexOfAnyByteIntl(Node location, Object array, int offset, int length, int fromIndex, byte[] values) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, 0, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
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
                return runIndexOfAnyByte(location, stubArray, stubOffset, length, isNative, fromIndex, values);
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
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
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
        return runIndexOfAnyChar(location, stubArray, stubOffset, length, stride, isNative, fromIndex, values);
    }

    static int indexOfAnyInt(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, int[] values) {
        return indexOfAnyIntIntl(location, arrayA, a.offset(), toIndex, stride, fromIndex, values);
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOfAnyIntIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int[] values) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
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
        return runIndexOfAnyInt(location, stubArray, stubOffset, length, stride, isNative, fromIndex, values);
    }

    static int indexOfCodePointWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int codepoint) {
        return indexOfCodePointWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, codepoint);
    }

    private static int indexOfCodePointWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runIndexOfAny1(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1);
            case 1:
                return runIndexOfAny1(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1);
            default:
                assert stride == 2;
                return runIndexOfAny1(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1);
        }
    }

    static int indexOfCodePointWithOrMaskWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int codepoint, int maskA) {
        return indexOfCodePointWithMaskWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, codepoint, maskA);
    }

    private static int indexOfCodePointWithMaskWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int mask1) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, mask1);
            case 1:
                return runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, mask1);
            default:
                assert stride == 2;
                return runIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, mask1);
        }
    }

    static int indexOf2ConsecutiveWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, int fromIndex, int toIndex, int v1, int v2) {
        return indexOf2ConsecutiveWithStrideIntl(location, arrayA, a.offset(), toIndex, strideA, fromIndex, v1, v2);
    }

    private static int indexOf2ConsecutiveWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int v2) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, v2);
            case 1:
                return runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, v2);
            default:
                assert stride == 2;
                return runIndexOf2ConsecutiveWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, v2);
        }
    }

    /**
     * This redundant method is used to simplify compiler tests for the intrinsic it is calling.
     */
    private static int indexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, Object array, int offset, int length, int stride, int fromIndex, int v1, int v2, int mask1, int mask2) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, length, stride, fromIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 0, isNative, fromIndex, v1, v2, mask1, mask2);
            case 1:
                return runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 1, isNative, fromIndex, v1, v2, mask1, mask2);
            default:
                assert stride == 2;
                return runIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, length, 2, isNative, fromIndex, v1, v2, mask1, mask2);
        }
    }

    static int indexOfStringWithOrMaskWithStride(Node location,
                    AbstractTruffleString a, Object arrayA, int strideA,
                    AbstractTruffleString b, Object arrayB, int strideB, int fromIndex, int toIndex, byte[] maskB) {
        int offsetMask = 0;
        assert b.length() > 1;
        assert a.length() >= b.length();
        final int max = toIndex - (b.length() - 2);
        int index = fromIndex;
        final int b0 = readValue(b, arrayB, strideB, 0);
        final int b1 = readValue(b, arrayB, strideB, 1);
        final int mask0 = maskB == null ? 0 : readValue(maskB, offsetMask, b.length(), strideB, 0);
        final int mask1 = maskB == null ? 0 : readValue(maskB, offsetMask, b.length(), strideB, 1);
        while (index < max - 1) {
            if (maskB == null) {
                index = indexOf2ConsecutiveWithStrideIntl(location, arrayA, a.offset(), max, strideA, index, b0, b1);
            } else {
                index = indexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, a.offset(), max, strideA, index, b0, b1, mask0, mask1);
            }
            if (index < 0) {
                return -1;
            }
            if (b.length() == 2 || regionEqualsWithOrMaskWithStride(location, a, arrayA, strideA, index, b, arrayB, strideB, 0, maskB, b.length())) {
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
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, fromIndex, stride, toIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 0, isNative, fromIndex, toIndex, codepoint, mask);
            case 1:
                return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 1, isNative, fromIndex, toIndex, codepoint, mask);
            default:
                assert stride == 2;
                return runLastIndexOfWithOrMaskWithStride(location, stubArray, stubOffset, 2, isNative, fromIndex, toIndex, codepoint, mask);
        }
    }

    static int lastIndexOf2ConsecutiveWithOrMaskWithStride(Node location, AbstractTruffleString a, Object arrayA, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        return lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, a.offset(), stride, fromIndex, toIndex, v1, v2, mask1, mask2);
    }

    private static int lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(Node location, Object array, int offset, int stride, int fromIndex, int toIndex, int v1, int v2, int mask1, int mask2) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegionIndex(stubArray, offset, fromIndex, stride, toIndex, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 0, isNative, fromIndex, toIndex, v1, v2, mask1, mask2);
            case 1:
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 1, isNative, fromIndex, toIndex, v1, v2, mask1, mask2);
            default:
                assert stride == 2;
                return runLastIndexOf2ConsecutiveWithOrMaskWithStride(location, stubArray, stubOffset, 2, isNative, fromIndex, toIndex, v1, v2, mask1, mask2);
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
        while (index > toIndex) {
            index = lastIndexOf2ConsecutiveWithOrMaskWithStrideIntl(location, arrayA, a.offset(), strideA, index, b.length() - 2, b0, b1, mask0, mask1);
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
        if (!rangeInBounds(fromIndexA, lengthCMP, lengthA) || !rangeInBounds(fromIndexB, lengthCMP, lengthB)) {
            return false;
        }
        boolean isNativeA = isNativePointer(arrayA);
        boolean isNativeB = isNativePointer(arrayB);
        final Object stubArrayA = stubArray(arrayA, isNativeA);
        final Object stubArrayB = stubArray(arrayB, isNativeB);
        validateRegion(stubArrayA, offsetA, lengthCMP, strideA, isNativeA);
        validateRegion(stubArrayB, offsetB, lengthCMP, strideB, isNativeB);
        final long stubOffsetA = stubOffset(arrayA, offsetA, strideA, fromIndexA, isNativeA);
        final long stubOffsetB = stubOffset(arrayB, offsetB, strideB, fromIndexB, isNativeB);
        if (maskB == null) {
            if (strideA == strideB) {
                return runRegionEqualsWithStride(location,
                                stubArrayA, stubOffsetA, 0, isNativeA,
                                stubArrayB, stubOffsetB, 0, isNativeB, lengthCMP << strideA);
            }
            final int swappedStrideA;
            final int swappedStrideB;
            final Object swappedArrayA;
            final Object swappedArrayB;
            final long swappedOffsetA;
            final long swappedOffsetB;
            final boolean swappedIsNativeA;
            final boolean swappedIsNativeB;
            if (strideA < strideB) {
                swappedStrideA = strideB;
                swappedStrideB = strideA;
                swappedArrayA = stubArrayB;
                swappedArrayB = stubArrayA;
                swappedOffsetA = stubOffsetB;
                swappedOffsetB = stubOffsetA;
                swappedIsNativeA = isNativeB;
                swappedIsNativeB = isNativeA;
            } else {
                swappedStrideA = strideA;
                swappedStrideB = strideB;
                swappedArrayA = stubArrayA;
                swappedArrayB = stubArrayB;
                swappedOffsetA = stubOffsetA;
                swappedOffsetB = stubOffsetB;
                swappedIsNativeA = isNativeA;
                swappedIsNativeB = isNativeB;
            }
            if (swappedStrideA == 1) {
                assert swappedStrideB == 0;
                return runRegionEqualsWithStride(location,
                                swappedArrayA, swappedOffsetA, 1, swappedIsNativeA,
                                swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
            } else {
                assert swappedStrideA == 2;
                if (swappedStrideB == 0) {
                    return runRegionEqualsWithStride(location,
                                    swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                    swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
                } else {
                    assert swappedStrideB == 1;
                    return runRegionEqualsWithStride(location,
                                    swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                    swappedArrayB, swappedOffsetB, 1, swappedIsNativeB, lengthCMP);
                }
            }
        } else {
            validateRegion(maskB, 0, lengthCMP, strideB, false);
            if (strideA == strideB) {
                return runRegionEqualsWithOrMaskWithStride(
                                location, stubArrayA, stubOffsetA, 0, isNativeA,
                                stubArrayB, stubOffsetB, 0, isNativeB, maskB, lengthCMP << strideA);
            }
            switch (strideA) {
                case 0:
                    if (strideB == 1) {
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 0, isNativeA,
                                        stubArrayB, stubOffsetB, 1, isNativeB, maskB, lengthCMP);
                    } else {
                        assert strideB == 2;
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 0, isNativeA,
                                        stubArrayB, stubOffsetB, 2, isNativeB, maskB, lengthCMP);
                    }
                case 1:
                    if (strideB == 0) {
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 1, isNativeA,
                                        stubArrayB, stubOffsetB, 0, isNativeB, maskB, lengthCMP);
                    } else {
                        assert strideB == 2;
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 1, isNativeA,
                                        stubArrayB, stubOffsetB, 2, isNativeB, maskB, lengthCMP);
                    }
                default:
                    if (strideB == 0) {
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 2, isNativeA,
                                        stubArrayB, stubOffsetB, 0, isNativeB, maskB, lengthCMP);
                    } else {
                        assert strideB == 1;
                        return runRegionEqualsWithOrMaskWithStride(location,
                                        stubArrayA, stubOffsetA, 2, isNativeA,
                                        stubArrayB, stubOffsetB, 1, isNativeB, maskB, lengthCMP);
                    }
            }
        }
    }

    static int memcmpWithStride(Node location, AbstractTruffleString a, Object arrayA, int strideA, AbstractTruffleString b, Object arrayB, int strideB, int lengthCMP) {
        assert lengthCMP <= a.length();
        assert lengthCMP <= b.length();
        return memcmpWithStrideIntl(location, arrayA, a.offset(), strideA, arrayB, b.offset(), strideB, lengthCMP);
    }

    private static int memcmpWithStrideIntl(
                    Node location, Object arrayA, int offsetA, int strideA,
                    Object arrayB, int offsetB, int strideB, int lengthCMP) {
        if (lengthCMP == 0) {
            return 0;
        }
        final boolean isNativeA = isNativePointer(arrayA);
        final boolean isNativeB = isNativePointer(arrayB);
        final Object stubArrayA = stubArray(arrayA, isNativeA);
        final Object stubArrayB = stubArray(arrayB, isNativeB);
        validateRegion(stubArrayA, offsetA, lengthCMP, strideA, isNativeA);
        validateRegion(stubArrayB, offsetB, lengthCMP, strideB, isNativeB);
        final long stubOffsetA = stubOffset(arrayA, offsetA, isNativeA);
        final long stubOffsetB = stubOffset(arrayB, offsetB, isNativeB);
        if (strideA == strideB) {
            switch (strideA) {
                case 0:
                    return runMemCmp(location,
                                    stubArrayA, stubOffsetA, 0, isNativeA,
                                    stubArrayB, stubOffsetB, 0, isNativeB, lengthCMP);
                case 1:
                    return runMemCmp(location,
                                    stubArrayA, stubOffsetA, 1, isNativeA,
                                    stubArrayB, stubOffsetB, 1, isNativeB, lengthCMP);
                default:
                    assert strideA == 2;
                    return runMemCmp(location,
                                    stubArrayA, stubOffsetA, 2, isNativeA,
                                    stubArrayB, stubOffsetB, 2, isNativeB, lengthCMP);
            }
        }
        final int swappedStrideA;
        final int swappedStrideB;
        final Object swappedArrayA;
        final Object swappedArrayB;
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
            return swappedResult * runMemCmp(location,
                            swappedArrayA, swappedOffsetA, 1, swappedIsNativeA,
                            swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
        } else {
            assert swappedStrideA == 2;
            if (swappedStrideB == 0) {
                return swappedResult * runMemCmp(location,
                                swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                swappedArrayB, swappedOffsetB, 0, swappedIsNativeB, lengthCMP);
            } else {
                assert swappedStrideB == 1;
                return swappedResult * runMemCmp(location,
                                swappedArrayA, swappedOffsetA, 2, swappedIsNativeA,
                                swappedArrayB, swappedOffsetB, 1, swappedIsNativeB, lengthCMP);
            }
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
        if (lengthCMP == 0) {
            return 0;
        }
        final boolean isNativeA = isNativePointer(arrayA);
        final boolean isNativeB = isNativePointer(arrayB);
        final Object stubArrayA = stubArray(arrayA, isNativeA);
        final Object stubArrayB = stubArray(arrayB, isNativeB);
        validateRegion(stubArrayA, offsetA, lengthCMP, strideA, isNativeA);
        validateRegion(stubArrayB, offsetB, lengthCMP, strideB, isNativeB);
        final long stubOffsetA = stubOffset(arrayA, offsetA, isNativeA);
        final long stubOffsetB = stubOffset(arrayB, offsetB, isNativeB);
        if (strideA == strideB) {
            switch (strideA) {
                case 0:
                    return runMemCmp(location,
                                    stubArrayA, stubOffsetA, 0, isNativeA,
                                    stubArrayB, stubOffsetB, 0, isNativeB, lengthCMP);
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
        final Object swappedArrayA;
        final Object swappedArrayB;
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
    }

    static int hashCodeWithStride(Node location, AbstractTruffleString a, Object arrayA, int stride) {
        return hashCodeWithStrideIntl(location, arrayA, a.offset(), a.length(), stride);
    }

    private static int hashCodeWithStrideIntl(Node location, Object array, int offset, int length, int stride) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, stride, isNative);
        final long stubOffset = stubOffset(array, offset, isNative);
        switch (stride) {
            case 0:
                return runHashCode(location, stubArray, stubOffset, length, 0, isNative);
            case 1:
                return runHashCode(location, stubArray, stubOffset, length, 1, isNative);
            default:
                assert stride == 2;
                return runHashCode(location, stubArray, stubOffset, length, 2, isNative);
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
        return arraycopyWithStrideIntl(location,
                        arrayA, stubOffsetA, 1, false,
                        arrayB, stubOffsetB, strideB, false, lengthCPY);
    }

    // arrayB is returned for testing purposes, do not remove
    static Object arraycopyWithStrideIB(Node location,
                    int[] arrayA, int offsetA,
                    byte[] arrayB, int offsetB, int strideB, int lengthCPY) {
        validateRegion(arrayA, offsetA, lengthCPY);
        validateRegion(arrayB, offsetB, lengthCPY, strideB);
        int stubOffsetA = Unsafe.ARRAY_INT_BASE_OFFSET + offsetA;
        int stubOffsetB = Unsafe.ARRAY_BYTE_BASE_OFFSET + offsetB;
        return arraycopyWithStrideIntl(location,
                        arrayA, stubOffsetA, 2, false,
                        arrayB, stubOffsetB, strideB, false, lengthCPY);
    }

    // arrayB is returned for testing purposes, do not remove
    static Object arraycopyWithStride(Node location,
                    Object arrayA, int offsetA, int strideA, int fromIndexA,
                    Object arrayB, int offsetB, int strideB, int fromIndexB, int lengthCPY) {
        final boolean isNativeA = isNativePointer(arrayA);
        final boolean isNativeB = isNativePointer(arrayB);
        final Object stubArrayA = stubArray(arrayA, isNativeA);
        final Object stubArrayB = stubArray(arrayB, isNativeB);
        validateRegion(stubArrayA, offsetA, lengthCPY, strideA, isNativeA);
        validateRegion(stubArrayB, offsetB, lengthCPY, strideB, isNativeB);
        final long stubOffsetA = stubOffset(arrayA, offsetA, strideA, fromIndexA, isNativeA);
        final long stubOffsetB = stubOffset(arrayB, offsetB, strideB, fromIndexB, isNativeB);
        return arraycopyWithStrideIntl(location,
                        stubArrayA, stubOffsetA, strideA, isNativeA,
                        stubArrayB, stubOffsetB, strideB, isNativeB, lengthCPY);
    }

    private static Object arraycopyWithStrideIntl(
                    Node location, Object stubArrayA, long stubOffsetA, int strideA, boolean isNativeA,
                    Object stubArrayB, long stubOffsetB, int strideB, boolean isNativeB, int lengthCPY) {
        if (strideA == strideB) {
            int byteLength = lengthCPY << strideA;
            runArrayCopy(
                            location, stubArrayA, stubOffsetA, 0, isNativeA,
                            stubArrayB, stubOffsetB, 0, isNativeB, byteLength);
            return stubArrayB;
        }
        switch (strideA) {
            case 0:
                if (strideB == 1) {
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 0, isNativeA,
                                    stubArrayB, stubOffsetB, 1, isNativeB, lengthCPY);
                } else {
                    assert strideB == 2;
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 0, isNativeA,
                                    stubArrayB, stubOffsetB, 2, isNativeB, lengthCPY);
                }
                break;
            case 1:
                if (strideB == 0) {
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 1, isNativeA,
                                    stubArrayB, stubOffsetB, 0, isNativeB, lengthCPY);
                } else {
                    assert strideB == 2;
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 1, isNativeA,
                                    stubArrayB, stubOffsetB, 2, isNativeB, lengthCPY);
                }
                break;
            default:
                if (strideB == 0) {
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 2, isNativeA,
                                    stubArrayB, stubOffsetB, 0, isNativeB, lengthCPY);
                } else {
                    assert strideB == 1;
                    runArrayCopy(location,
                                    stubArrayA, stubOffsetA, 2, isNativeA,
                                    stubArrayB, stubOffsetB, 1, isNativeB, lengthCPY);
                }
        }
        return stubArrayB;
    }

    static byte[] arraycopyOfWithStride(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int lengthB, int strideB) {
        byte[] dst = new byte[lengthB << strideB];
        arraycopyWithStride(location, arrayA, offsetA, strideA, 0, dst, 0, strideB, 0, lengthA);
        return dst;
    }

    static int calcStringAttributesLatin1(Node location, Object array, int offset, int length) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 0, isNative);
        final long stubOffset = stubOffset(array, offset, 0, 0, isNative);
        return runCalcStringAttributesLatin1(location, stubArray, stubOffset, length, isNative);
    }

    static int calcStringAttributesBMP(Node location, Object array, int offset, int length) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 1, isNative);
        final long stubOffset = stubOffset(array, offset, 1, 0, isNative);
        return runCalcStringAttributesBMP(location, stubArray, stubOffset, length, isNative);
    }

    static long calcStringAttributesUTF8(Node location, Object array, int offset, int length, boolean assumeValid) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 0, isNative);
        final long stubOffset = stubOffset(array, offset, 0, 0, isNative);
        if (assumeValid) {
            return runCalcStringAttributesUTF8(location, stubArray, stubOffset, length, isNative, true);
        } else {
            long attrs = runCalcStringAttributesUTF8(location, stubArray, stubOffset, length, isNative, false);
            if (TStringGuards.isBrokenMultiByte(StringAttributes.getCodeRange(attrs))) {
                int codePointLength = 0;
                for (int i = 0; i < length; i += Encodings.utf8GetCodePointLength(array, offset, length, i)) {
                    codePointLength++;
                    TStringConstants.truffleSafePointPoll(location, codePointLength);
                }
                return StringAttributes.create(codePointLength, StringAttributes.getCodeRange(attrs));
            }
            return attrs;
        }
    }

    static long calcStringAttributesUTF16C(Node location, char[] array, int offset, int length) {
        validateRegion(array, offset, length);
        int stubOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET + offset;
        return runCalcStringAttributesUTF16C(location, array, stubOffset, length);
    }

    static long calcStringAttributesUTF16(Node location, Object array, int offset, int length, boolean assumeValid) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 1, isNative);
        final long stubOffset = stubOffset(array, offset, 1, 0, isNative);
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
    }

    static int calcStringAttributesUTF32I(Node location, int[] array, int offset, int length) {
        validateRegion(array, offset, length);
        int stubOffset = Unsafe.ARRAY_INT_BASE_OFFSET + offset;
        return runCalcStringAttributesUTF32I(location, array, stubOffset, length);
    }

    static int calcStringAttributesUTF32(Node location, Object array, int offset, int length) {
        final boolean isNative = isNativePointer(array);
        final Object stubArray = stubArray(array, isNative);
        validateRegion(stubArray, offset, length, 2, isNative);
        final long stubOffset = stubOffset(array, offset, 2, 0, isNative);
        return runCalcStringAttributesUTF32(location, stubArray, stubOffset, length, isNative);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS0Managed(Object array, long byteOffset) {
        return uInt(TStringUnsafe.getByteManaged(array, byteOffset));
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS0Native(long array, long byteOffset) {
        return uInt(TStringUnsafe.getByteNative(array, byteOffset));
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS1Managed(Object array, long byteOffset) {
        return TStringUnsafe.getCharManaged(array, byteOffset);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS1Native(long array, long byteOffset) {
        return TStringUnsafe.getCharNative(array, byteOffset);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS2Managed(Object array, long byteOffset) {
        return TStringUnsafe.getIntManaged(array, byteOffset);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runReadS2Native(long array, long byteOffset) {
        return TStringUnsafe.getIntNative(array, byteOffset);
    }

    /**
     * Intrinsic candidate.
     */
    private static long runReadS3Managed(Object array, long byteOffset) {
        return TStringUnsafe.getLongManaged(array, byteOffset);
    }

    /**
     * Intrinsic candidate.
     */
    private static long runReadS3Native(long array) {
        return TStringUnsafe.getLongNative(array);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS0Managed(byte[] array, long byteOffset, byte value) {
        TStringUnsafe.putByteManaged(array, byteOffset, value);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS0Native(long array, long byteOffset, byte value) {
        TStringUnsafe.putByteNative(array, byteOffset, value);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS1Managed(byte[] array, long byteOffset, char value) {
        TStringUnsafe.putCharManaged(array, byteOffset, value);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS1Native(long array, long byteOffset, char value) {
        TStringUnsafe.putCharNative(array, byteOffset, value);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS2Managed(byte[] array, long byteOffset, int value) {
        TStringUnsafe.putIntManaged(array, byteOffset, value);
    }

    /**
     * Intrinsic candidate.
     */
    private static void runWriteS2Native(long array, long byteOffset, int value) {
        TStringUnsafe.putIntNative(array, byteOffset, value);
    }

    private static int runIndexOfAnyByte(Node location, Object array, long offset, int length, boolean isNative, int fromIndex, byte... needle) {
        for (int i = fromIndex; i < length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (readValue(array, offset, 0, i, isNative) == uInt(needle[j])) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runIndexOfAnyChar(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, char... needle) {
        for (int i = fromIndex; i < length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (readValue(array, offset, stride, i, isNative) == needle[j]) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runIndexOfAnyInt(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int... needle) {
        for (int i = fromIndex; i < length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (readValue(array, offset, stride, i, isNative) == needle[j]) {
                    return i;
                }
                TStringConstants.truffleSafePointPoll(location, j + 1);
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny1(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int v0) {
        return runIndexOfAnyInt(location, array, offset, length, stride, isNative, fromIndex, v0);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny2(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int v0, int v1) {
        return runIndexOfAnyInt(location, array, offset, length, stride, isNative, fromIndex, v0, v1);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny3(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int v0, int v1, int v2) {
        return runIndexOfAnyInt(location, array, offset, length, stride, isNative, fromIndex, v0, v1, v2);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfAny4(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int v0, int v1, int v2, int v3) {
        return runIndexOfAnyInt(location, array, offset, length, stride, isNative, fromIndex, v0, v1, v2, v3);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOfWithOrMaskWithStride(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int needle, int mask) {
        for (int i = fromIndex; i < length; i++) {
            if ((readValue(array, offset, stride, i, isNative) | mask) == needle) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return -1;
    }

    private static int runLastIndexOfWithOrMaskWithStride(Node location, Object array, long offset, int stride, boolean isNative, int fromIndex, int toIndex, int needle, int mask) {
        for (int i = fromIndex - 1; i >= toIndex; i--) {
            if ((readValue(array, offset, stride, i, isNative) | mask) == needle) {
                return i;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOf2ConsecutiveWithStride(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex, int c1, int c2) {
        for (int i = fromIndex + 1; i < length; i++) {
            if (readValue(array, offset, stride, i - 1, isNative) == c1 && readValue(array, offset, stride, i, isNative) == c2) {
                return i - 1;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    /**
     * Intrinsic candidate.
     */
    private static int runIndexOf2ConsecutiveWithOrMaskWithStride(Node location, Object array, long offset, int length, int stride, boolean isNative, int fromIndex,
                    int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex + 1; i < length; i++) {
            if ((readValue(array, offset, stride, i - 1, isNative) | mask1) == c1 && (readValue(array, offset, stride, i, isNative) | mask2) == c2) {
                return i - 1;
            }
            TStringConstants.truffleSafePointPoll(location, i);
        }
        return -1;
    }

    private static int runLastIndexOf2ConsecutiveWithOrMaskWithStride(Node location, Object array, long offset, int stride, boolean isNative, int fromIndex, int toIndex,
                    int c1, int c2, int mask1, int mask2) {
        for (int i = fromIndex - 1; i >= toIndex; i--) {
            if ((readValue(array, offset, stride, i - 1, isNative) | mask1) == c1 && (readValue(array, offset, stride, i, isNative) | mask2) == c2) {
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
                    Object arrayA, long offsetA, int strideA, boolean isNativeA,
                    Object arrayB, long offsetB, int strideB, boolean isNativeB, int length) {
        for (int i = 0; i < length; i++) {
            if (readValue(arrayA, offsetA, strideA, i, isNativeA) != readValue(arrayB, offsetB, strideB, i, isNativeB)) {
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
                    Object arrayA, long offsetA, int strideA, boolean isNativeA,
                    Object arrayB, long offsetB, int strideB, boolean isNativeB, byte[] arrayMask, int lengthCMP) {
        for (int i = 0; i < lengthCMP; i++) {
            if ((readValue(arrayA, offsetA, strideA, i, isNativeA) | readFromByteArray(arrayMask, strideB, i)) != readValue(arrayB, offsetB, strideB, i, isNativeB)) {
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
                    Object arrayA, long offsetA, int strideA, boolean isNativeA,
                    Object arrayB, long offsetB, int strideB, boolean isNativeB, int lengthCMP) {
        for (int i = 0; i < lengthCMP; i++) {
            int cmp = readValue(arrayA, offsetA, strideA, i, isNativeA) - readValue(arrayB, offsetB, strideB, i, isNativeB);
            if (cmp != 0) {
                return cmp;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return 0;
    }

    private static int runMemCmpBytes(Node location,
                    Object arrayA, long offsetA, int strideA, boolean isNativeA,
                    Object arrayB, long offsetB, int strideB, boolean isNativeB, int lengthCMP) {
        assert strideA >= strideB;
        for (int i = 0; i < lengthCMP; i++) {
            int valueA = readValue(arrayA, offsetA, strideA, i, isNativeA);
            int valueB = readValue(arrayB, offsetB, strideB, i, isNativeB);
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

    private static int runHashCode(Node location, Object array, long offset, int length, int stride, boolean isNative) {
        int hash = 0;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + readValue(array, offset, stride, i, isNative);
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return hash;
    }

    /**
     * Intrinsic candidate.
     */
    private static void runArrayCopy(Node location,
                    Object arrayA, long offsetA, int strideA, boolean isNativeA,
                    Object arrayB, long offsetB, int strideB, boolean isNativeB, int lengthCPY) {
        for (int i = 0; i < lengthCPY; i++) {
            writeValue(arrayB, offsetB, strideB, i, isNativeB, readValue(arrayA, offsetA, strideA, i, isNativeA));
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesLatin1(Node location, Object array, long offset, int length, boolean isNative) {
        for (int i = 0; i < length; i++) {
            if (readValueS0(array, offset, i, isNative) > 0x7f) {
                return TSCodeRange.get8Bit();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.get7Bit();
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesBMP(Node location, Object array, long offset, int length, boolean isNative) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS1(array, offset, i, isNative) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return TSCodeRange.get7Bit();
        }
        for (; i < length; i++) {
            if (readValueS1(array, offset, i, isNative) > 0xff) {
                return TSCodeRange.get16Bit();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.get8Bit();
    }

    private static int runCalcStringAttributesUTF32I(Node location, int[] array, long offset, int length) {
        return runCalcStringAttributesUTF32(location, array, offset, length, false);
    }

    /**
     * Intrinsic candidate.
     */
    private static int runCalcStringAttributesUTF32(Node location, Object array, long offset, int length, boolean isNative) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (Integer.toUnsignedLong(readValueS2(array, offset, i, isNative)) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return TSCodeRange.get7Bit();
        }
        for (; i < length; i++) {
            if (Integer.toUnsignedLong(readValueS2(array, offset, i, isNative)) > 0xff) {
                codeRange = TSCodeRange.get16Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is16Bit(codeRange)) {
            return TSCodeRange.get8Bit();
        }
        for (; i < length; i++) {
            int value = readValueS2(array, offset, i, isNative);
            if (Integer.toUnsignedLong(value) > 0xffff) {
                codeRange = TSCodeRange.getValidFixedWidth();
                break;
            }
            if (Encodings.isUTF16Surrogate(value)) {
                return TSCodeRange.getBrokenFixedWidth();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.isValidFixedWidth(codeRange)) {
            return TSCodeRange.get16Bit();
        }
        for (; i < length; i++) {
            int value = readValueS2(array, offset, i, isNative);
            if (Integer.toUnsignedLong(value) > 0x10ffff || Encodings.isUTF16Surrogate(value)) {
                return TSCodeRange.getBrokenFixedWidth();
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        return TSCodeRange.getValidFixedWidth();
    }

    /**
     * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de> See
     * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
     *
     * LICENCE: MIT
     */
    @CompilationFinal(dimensions = 1) private static final byte[] UTF_8_STATE_MACHINE = {
                    // The first part of the table maps bytes to character classes
                    // to reduce the size of the transition table and create bitmasks.
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    // The second part is a transition table that maps a combination
                    // of a state of the automaton and a character class to a state.
                    0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
                    12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };
    private static final byte UTF8_ACCEPT = 0;

    /**
     * Intrinsic candidate.
     */
    private static long runCalcStringAttributesUTF8(Node location, Object array, long offset, int length, boolean isNative, boolean assumeValid) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS0(array, offset, i, isNative) > 0x7f) {
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
                if (!Encodings.isUTF8ContinuationByte(readValueS0(array, offset, i, isNative))) {
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
            int state = UTF8_ACCEPT;
            // int codepoint = 0;
            for (; i < length; i++) {
                int b = readValueS0(array, offset, i, isNative);
                if (!Encodings.isUTF8ContinuationByte(b)) {
                    nCodePoints++;
                }
                int type = UTF_8_STATE_MACHINE[b];
                // codepoint = (state != UTF8_ACCEPT) ? (b & 0x3f) | (codepoint << 6) : (0xff >>
                // type) & (b);
                state = UTF_8_STATE_MACHINE[256 + state + type];
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            if (state != UTF8_ACCEPT) {
                codeRange = TSCodeRange.getBrokenMultiByte();
            }
            return StringAttributes.create(nCodePoints, codeRange);
        }
    }

    private static long runCalcStringAttributesUTF16C(Node location, char[] array, long offset, int length) {
        return runCalcStringAttributesUTF16(location, array, offset, length, false, false);
    }

    /**
     * Intrinsic candidate.
     */
    private static long runCalcStringAttributesUTF16(Node location, Object array, long offset, int length, boolean isNative, boolean assumeValid) {
        int codeRange = TSCodeRange.get7Bit();
        int i = 0;
        for (; i < length; i++) {
            if (readValueS1(array, offset, i, isNative) > 0x7f) {
                codeRange = TSCodeRange.get8Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is8Bit(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }
        for (; i < length; i++) {
            if (readValueS1(array, offset, i, isNative) > 0xff) {
                codeRange = TSCodeRange.get16Bit();
                break;
            }
            TStringConstants.truffleSafePointPoll(location, i + 1);
        }
        if (!TSCodeRange.is16Bit(codeRange)) {
            return StringAttributes.create(length, TSCodeRange.get8Bit());
        }
        for (; i < length; i++) {
            char c = (char) readValueS1(array, offset, i, isNative);
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
                if (Encodings.isUTF16HighSurrogate(readValueS1(array, offset, i, isNative))) {
                    nCodePoints--;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return StringAttributes.create(nCodePoints, TSCodeRange.getValidMultiByte());
        } else {
            for (; i < length; i++) {
                char c = (char) readValueS1(array, offset, i, isNative);
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16LowSurrogate(c) || !(i + 1 < length && Encodings.isUTF16LowSurrogate(readValueS1(array, offset, i + 1, isNative)))) {
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

    static long byteLength(Object array) {
        CompilerAsserts.neverPartOfCompilation();
        if (array instanceof byte[]) {
            return ((byte[]) array).length;
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    private static boolean rangeInBounds(int rangeStart, int rangeLength, int arrayLength) {
        return Integer.toUnsignedLong(rangeStart + rangeLength) <= arrayLength;
    }

    private static boolean isNativePointer(Object arrayB) {
        return arrayB instanceof AbstractTruffleString.NativePointer;
    }

    private static long nativePointer(Object array) {
        return ((AbstractTruffleString.NativePointer) array).pointer;
    }

    private static Object stubArray(Object arrayA, boolean isNativeA) {
        return isNativeA ? null : arrayA;
    }

    private static long stubOffset(Object arrayA, int offsetA, boolean isNativeA) {
        assert isNativeA || arrayA instanceof byte[];
        return offsetA + (isNativeA ? nativePointer(arrayA) : Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    private static long stubOffset(Object arrayA, int offsetA, int strideA, long fromIndexA, boolean isNativeA) {
        return stubOffset(arrayA, offsetA, isNativeA) + (fromIndexA << strideA);
    }

    private static int uInt(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static void validateRegion(byte[] array, int offset, int length, int stride) {
        validateRegion(array, offset, length, stride, false);
    }

    private static void validateRegion(Object stubArray, int offset, int length, int stride, boolean isNative) {
        if (invalidOffsetOrLength(stubArray, offset, length, stride, isNative)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegion(char[] array, int offset, int length) {
        long charOffset = offset >> 1;
        if (length < 0 || charOffset < 0 || charOffset + length > array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegion(int[] array, int offset, int length) {
        long intOffset = offset >> 2;
        if (length < 0 || intOffset < 0 || intOffset + length > array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateRegionIndex(Object stubArray, int offset, int length, int stride, int i, boolean isNative) {
        if (invalidOffsetOrLength(stubArray, offset, length, stride, isNative) || invalidIndex(length, i)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static boolean invalidOffsetOrLength(Object stubArray, int offset, int length, int stride, boolean isNative) {
        if (isNative) {
            return stubArray != null || offset < 0 || length < 0;
        } else {
            return offset < 0 || length < 0 || offset + ((long) length << stride) > ((byte[]) stubArray).length;
        }
    }

    private static boolean invalidIndex(int length, int i) {
        return i < 0 || i >= length;
    }
}
