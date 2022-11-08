/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Self-optimizing parameter object for {@link TruffleString.ByteIndexOfCodePointSetNode} and
 * {@link JavaStringUtils.CharIndexOfCodePointSetNode}.
 * 
 * @since 23.0
 */
public final class CodePointSetParameter {

    @CompilationFinal(dimensions = 1) final IndexOfCall[] indexOfCalls;
    final TruffleString.Encoding encoding;

    private CodePointSetParameter(IndexOfCall[] indexOfCalls, TruffleString.Encoding encoding) {
        this.indexOfCalls = indexOfCalls;
        this.encoding = encoding;
        assert containsIndexOfCallForAllApplicableCodeRanges();
    }

    private boolean containsIndexOfCallForAllApplicableCodeRanges() {
        assert getIndexOfCall(TruffleString.CodeRange.ASCII) != null;
        if (encoding == TruffleString.Encoding.UTF_16 || encoding == TruffleString.Encoding.UTF_32 || encoding == TruffleString.Encoding.ISO_8859_1) {
            assert getIndexOfCall(TruffleString.CodeRange.LATIN_1) != null;
        }
        if (encoding == TruffleString.Encoding.UTF_16 || encoding == TruffleString.Encoding.UTF_32) {
            assert getIndexOfCall(TruffleString.CodeRange.BMP) != null;
        }
        assert getIndexOfCall(TruffleString.CodeRange.VALID) != null;
        assert getIndexOfCall(TruffleString.CodeRange.BROKEN) != null;
        return true;
    }

    TruffleString.Encoding getEncoding() {
        return encoding;
    }

    /**
     * Returns {@code true} if the search for this codepoint set may be accelerated with SIMD
     * instructions on strings with the given code range.
     * 
     * @since 23.0
     */
    public boolean isFast(TruffleString.CodeRange codeRange) {
        IndexOfCall indexOfCall = getIndexOfCall(codeRange);
        return indexOfCall != null && indexOfCall.isFast();
    }

    private IndexOfCall getIndexOfCall(TruffleString.CodeRange codeRange) {
        return indexOfCalls[codeRange.ordinal()];
    }

    /**
     * Creates a new {@link CodePointSetParameter} object from a given sorted list of codepoint
     * ranges. This operation may be expensive, it is recommended to cache the result.
     * 
     * @param ranges a sorted list of non-adjacent codepoint ranges. For every two consecutive array
     *            elements, the first is interpreted as the range's inclusive lower bound, and the
     *            second element is the range's inclusive upper bound. Example: an array
     *            {@code [1, 4, 8, 10]} represents the inclusive ranges {@code [1-4]} and
     *            {@code [8-10]}.
     * @param encoding The encoding of strings the codepoint set will be used with. The
     *            {@link CodePointSetParameter} object is specialized to this encoding and cannot be
     *            used with strings of other encodings.
     * @return a new {@link CodePointSetParameter} object.
     * 
     * @since 23.0
     */
    @TruffleBoundary
    public static CodePointSetParameter fromRanges(int[] ranges, TruffleString.Encoding encoding) {
        if ((ranges.length & 1) != 0) {
            throw new IllegalArgumentException("ranges must have an even number of elements");
        }
        int maxCodePoint = maxCodePoint(encoding);
        int lastHi = -2;
        for (int i = 0; i < ranges.length; i += 2) {
            int lo = ranges[i];
            int hi = ranges[i + 1];
            checkIllegalCodepoint(lo, maxCodePoint);
            checkIllegalCodepoint(hi, maxCodePoint);
            if (lo > hi) {
                throw new IllegalArgumentException(String.format("range [0x%x - 0x%x] out of order", lo, hi));
            }
            if (lo == lastHi + 1) {
                throw new IllegalArgumentException(String.format("ranges [0x%x - 0x%x] and [0x%x - 0x%x] are directly adjacent and must be merged into one", ranges[i - 2], lastHi, lo, hi));
            }
            if (lastHi >= lo) {
                throw new IllegalArgumentException("ranges are not sorted");
            }
            lastHi = hi;
        }
        return new CodePointSetParameter(extractIndexOfCalls(ranges, encoding), encoding);
    }

    private static IndexOfCall[] extractIndexOfCalls(int[] ranges, TruffleString.Encoding encoding) {
        if (encoding == TruffleString.Encoding.US_ASCII || encoding == TruffleString.Encoding.ISO_8859_1 || encoding == TruffleString.Encoding.BYTES || getMax(ranges) <= 0x7f) {
            return extractIndexOfCalls1ByteEncoding(ranges);
        } else if (encoding == TruffleString.Encoding.UTF_8) {
            if (isSingleValue(ranges)) {
                int codepoint = getMin(ranges);
                byte[] encoded = Encodings.utf8Encode(codepoint);
                int codeRange = Encodings.isUTF16Surrogate(codepoint) ? TSCodeRange.getBrokenMultiByte() : TSCodeRange.getValidMultiByte();
                int codepointLength = Encodings.isUTF16Surrogate(codepoint) ? encoded.length : 1;
                return createIndexOfCallArray(new IndexOfStringCall(TruffleString.createFromByteArray(encoded, encoded.length, 0, TruffleString.Encoding.UTF_8, codepointLength, codeRange)));
            } else {
                IndexOfCall[] ret = createIndexOfCallArray(new IndexOfRangesCall(ranges));
                ret[TSCodeRange.get7Bit()] = extractIndexOfCallFixedWidth(ranges, ASCII_RANGE);
                return ret;
            }
        } else if (encoding == TruffleString.Encoding.UTF_16) {
            IndexOfCall[] ret = createIndexOfCallArray();
            IndexOfCall bmpCall = extractIndexOfCallFixedWidth(ranges, BMP_WITHOUT_SURROGATES);
            ret[TSCodeRange.get7Bit()] = extractIndexOfCallFixedWidth(ranges, ASCII_RANGE);
            ret[TSCodeRange.get8Bit()] = extractIndexOfCallFixedWidth(ranges, LATIN_RANGE);
            ret[TSCodeRange.get16Bit()] = bmpCall;
            if (Arrays.equals(intersect(ranges, BMP_WITHOUT_SURROGATES), ranges)) {
                ret[TSCodeRange.getValidMultiByte()] = bmpCall;
                ret[TSCodeRange.getBrokenMultiByte()] = bmpCall;
            } else if (isSingleValue(ranges)) {
                int codepoint = getMin(ranges);
                final IndexOfCall astralCall;
                if (Encodings.isUTF16Surrogate(codepoint)) {
                    astralCall = new IndexOfAnyValueCall(new int[]{codepoint});
                } else {
                    assert codepoint > 0xffff;
                    byte[] encoded = Encodings.utf16Encode(codepoint);
                    astralCall = new IndexOfStringCall(TruffleString.createFromByteArray(encoded, encoded.length >> 1, 1, TruffleString.Encoding.UTF_16, 1, TSCodeRange.getValidMultiByte()));
                }
                ret[TSCodeRange.getValidMultiByte()] = astralCall;
                ret[TSCodeRange.getBrokenMultiByte()] = astralCall;
            } else {
                IndexOfRangesCall astralCall = new IndexOfRangesCall(ranges);
                ret[TSCodeRange.getValidMultiByte()] = astralCall;
                ret[TSCodeRange.getBrokenMultiByte()] = astralCall;
            }
            // for #isFast(TruffleString.CodeRange)
            ret[TSCodeRange.getValidFixedWidth()] = ret[TSCodeRange.getValidMultiByte()];
            ret[TSCodeRange.getBrokenFixedWidth()] = ret[TSCodeRange.getBrokenMultiByte()];
            return ret;

        } else if (encoding == TruffleString.Encoding.UTF_32) {
            IndexOfCall[] ret = createIndexOfCallArray();
            ret[TSCodeRange.get7Bit()] = extractIndexOfCallFixedWidth(ranges, ASCII_RANGE);
            ret[TSCodeRange.get8Bit()] = extractIndexOfCallFixedWidth(ranges, LATIN_RANGE);
            ret[TSCodeRange.get16Bit()] = extractIndexOfCallFixedWidth(ranges, BMP_WITHOUT_SURROGATES);
            ret[TSCodeRange.getValidFixedWidth()] = extractIndexOfCallFixedWidth(ranges, ALL_WITHOUT_SURROGATES);
            ret[TSCodeRange.getBrokenFixedWidth()] = extractIndexOfCallFixedWidth(ranges, ALL);
            return ret;
        }
        throw new UnsupportedOperationException();
    }

    private static int maxCodePoint(TruffleString.Encoding encoding) {
        switch (encoding) {
            case US_ASCII:
                return 0x7f;
            case ISO_8859_1:
            case BYTES:
                return 0xff;
            case UTF_8:
            case UTF_16BE:
            case UTF_16LE:
            case UTF_32BE:
            case UTF_32LE:
                return Character.MAX_CODE_POINT;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private static final int[] EMPTY_RANGES = {};
    private static final int[] ASCII_RANGE = {0, 0x7f};
    private static final int[] LATIN_RANGE = {0, 0xff};
    private static final int[] BMP_WITHOUT_SURROGATES = {0x0000, 0xd7ff, 0xe000, 0xffff};
    private static final int[] ALL_WITHOUT_SURROGATES = {0x0000, 0xd7ff, 0xe000, 0x10ffff};
    private static final int[] ALL = {0x0000, 0x10ffff};

    private static void checkIllegalCodepoint(int c, int maxCodePoint) {
        if (Integer.toUnsignedLong(c) > maxCodePoint) {
            throw new IllegalArgumentException(String.format("illegal codepoint value 0x%x", c));
        }
    }

    private static IndexOfCall[] extractIndexOfCalls1ByteEncoding(int[] ranges) {
        IndexOfCall ascii = extractIndexOfCallFixedWidth(ranges, ASCII_RANGE);
        IndexOfCall latin = extractIndexOfCallFixedWidth(ranges, LATIN_RANGE);
        IndexOfCall[] ret = createIndexOfCallArray(latin);
        ret[TSCodeRange.get7Bit()] = ascii;
        return ret;
    }

    private static int[] intersect(int[] rangesA, int[] rangesB) {
        if (isEmpty(rangesA) || getMin(rangesB) <= getMin(rangesA) && getHi(rangesB, 0) >= getMax(rangesA)) {
            return rangesA;
        }
        if (size(rangesB) == 1) {
            return intersectSingleRange(rangesA, rangesB[0], rangesB[1]);
        }
        assert size(rangesB) == 2;
        return intersectTwoRanges(rangesA, rangesB[0], rangesB[1], rangesB[2], rangesB[3]);
    }

    private static int[] intersectSingleRange(int[] ranges, int lo, int hi) {
        int size = size(ranges);
        int iLo = findFirstIntersection(ranges, lo, 0);
        int iHi = findLastIntersection(ranges, hi, size - 1);
        if (iHi < iLo) {
            return EMPTY_RANGES;
        }
        int[] intersection = Arrays.copyOfRange(ranges, iLo << 1, (iHi + 1) << 1);
        intersection[0] = Math.max(intersection[0], lo);
        intersection[intersection.length - 1] = Math.min(intersection[intersection.length - 1], hi);
        return intersection;
    }

    private static int findFirstIntersection(int[] ranges, int lo, int startIndex) {
        int iLo = startIndex;
        while (iLo < size(ranges) && getHi(ranges, iLo) < lo) {
            iLo++;
        }
        return iLo;
    }

    private static int findLastIntersection(int[] ranges, int hi, int startIndex) {
        int iHi = startIndex;
        while (iHi >= 0 && getLo(ranges, iHi) > hi) {
            iHi--;
        }
        return iHi;
    }

    private static int[] intersectTwoRanges(int[] ranges, int lo0, int hi0, int lo1, int hi1) {
        if (hi1 < getMin(ranges) || lo0 > getMax(ranges)) {
            return EMPTY_RANGES;
        }
        int size = size(ranges);
        int iLo0 = findFirstIntersection(ranges, lo0, 0);
        int iLo1 = findFirstIntersection(ranges, lo1, 0);
        int iHi0 = findLastIntersection(ranges, hi0, size - 1);
        int iHi1 = findLastIntersection(ranges, hi1, size - 1);
        int size0 = Math.max(0, iHi0 + 1 - iLo0);
        int size1 = Math.max(0, iHi1 + 1 - iLo1);
        int intersectionSize = size0 + size1;
        if (intersectionSize == 0) {
            return EMPTY_RANGES;
        }
        int[] intersection = new int[intersectionSize << 1];
        System.arraycopy(ranges, iLo0 << 1, intersection, 0, size0 << 1);
        System.arraycopy(ranges, iLo1 << 1, intersection, size0 << 1, size1 << 1);
        if (size0 != 0) {
            intersection[0] = Math.max(intersection[0], lo0);
            intersection[(size0 << 1) - 1] = Math.min(intersection[(size0 << 1) - 1], hi0);
        }
        if (size1 != 0) {
            intersection[(size0 << 1)] = Math.max(intersection[(size0 << 1)], lo1);
            intersection[intersection.length - 1] = Math.min(intersection[intersection.length - 1], hi1);
        }
        return intersection;
    }

    private static IndexOfCall extractIndexOfCallFixedWidth(int[] ranges, int[] bounds) {
        int[] intersection = intersect(ranges, bounds);
        if (intersection.length == 0) {
            return NoMatch.INSTANCE;
        }
        if (Arrays.equals(intersection, bounds)) {
            return AnyMatch.INSTANCE;
        }
        int valueCount = valueCount(intersection);
        if (valueCount <= 4) {
            return new IndexOfAnyValueCall(toValues(intersection, valueCount));
        } else if (size(intersection) <= 2) {
            return new IndexOfAnyRangeCall(intersection);
        } else if (getMax(intersection) <= 0xff) {
            byte[] tables = generateTable(intersection);
            if (tables != null) {
                return new IndexOfTableCall(tables);
            } else {
                return IndexOfBitSetCall.fromRanges(intersection);
            }
        }
        return new IndexOfRangesCall(intersection);
    }

    private static boolean isEmpty(int[] ranges) {
        return ranges.length == 0;
    }

    private static int size(int[] ranges) {
        return ranges.length >> 1;
    }

    private static int getLo(int[] ranges, int i) {
        return ranges[i << 1];
    }

    private static int getHi(int[] ranges, int i) {
        return ranges[(i << 1) + 1];
    }

    private static int getMin(int[] ranges) {
        return ranges[0];
    }

    private static int getMax(int[] ranges) {
        return ranges[ranges.length - 1];
    }

    private static boolean isSingleValue(int[] ranges) {
        return ranges.length == 2 && ranges[0] == ranges[1];
    }

    private static int valueCount(int[] ranges) {
        int count = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            count += (ranges[i + 1] - ranges[i]) + 1;
        }
        return count;
    }

    private static int[] toValues(int[] ranges, int valueCount) {
        int[] values = new int[valueCount];
        int index = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            for (int j = ranges[i]; j <= ranges[i + 1]; j++) {
                values[index++] = j;
            }
        }
        return values;
    }

    private static IndexOfCall[] createIndexOfCallArray(IndexOfCall fill) {
        IndexOfCall[] ret = createIndexOfCallArray();
        Arrays.fill(ret, fill);
        return ret;
    }

    private static IndexOfCall[] createIndexOfCallArray() {
        return new IndexOfCall[TSCodeRange.valueCount()];
    }

    abstract static class IndexOfCall {
        abstract int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding);

        final boolean isFast() {
            return this instanceof OptimizedIndexOfCall;
        }
    }

    abstract static class OptimizedIndexOfCall extends IndexOfCall {
    }

    abstract static class ScalarIndexOfCall extends IndexOfCall {

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            CompilerAsserts.partialEvaluationConstant(this);
            CompilerAsserts.partialEvaluationConstant(encoding);
            int codepointLength = 1;
            for (int i = fromIndex; i < toIndex; i += codepointLength) {
                final int codepoint;
                if (encoding == TruffleString.Encoding.US_ASCII || encoding == TruffleString.Encoding.ISO_8859_1 || encoding == TruffleString.Encoding.BYTES || TSCodeRange.isFixedWidth(codeRangeA)) {
                    codepoint = TStringOps.readValue(arrayA, offsetA, lengthA, strideA, i);
                } else if (encoding == TruffleString.Encoding.UTF_8) {
                    if (TSCodeRange.isValidMultiByte(codeRangeA)) {
                        int firstByte = TStringOps.readS0(arrayA, offsetA, lengthA, i);
                        codepointLength = firstByte <= 0x7f ? 1 : Encodings.utf8CodePointLength(firstByte);
                        codepoint = Encodings.utf8DecodeValid(arrayA, offsetA, lengthA, i);
                    } else {
                        codepointLength = Encodings.utf8GetCodePointLength(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT);
                        codepoint = Encodings.utf8DecodeBroken(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT);
                    }
                } else {
                    assert encoding == TruffleString.Encoding.UTF_16;
                    if (TSCodeRange.isValidMultiByte(codeRangeA)) {
                        codepointLength = Encodings.isUTF16HighSurrogate(TStringOps.readS1(arrayA, offsetA, lengthA, i)) ? 2 : 1;
                        codepoint = Encodings.utf16DecodeValid(arrayA, offsetA, lengthA, i);
                    } else {
                        codepointLength = Encodings.utf16BrokenGetCodePointByteLength(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT) >> 1;
                        codepoint = Encodings.utf16DecodeBroken(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT);
                    }
                }
                if (match(codepoint)) {
                    return i;
                }
            }
            return -1;
        }

        abstract boolean match(int codepoint);
    }

    /**
     * No match possible.
     */
    static final class NoMatch extends OptimizedIndexOfCall {

        static final NoMatch INSTANCE = new NoMatch();

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return -1;
        }
    }

    /**
     * Will always match immediately.
     */
    static final class AnyMatch extends OptimizedIndexOfCall {

        static final AnyMatch INSTANCE = new AnyMatch();

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return fromIndex;
        }
    }

    /**
     * Match any of up to four values, without decoding.
     */
    static final class IndexOfAnyValueCall extends OptimizedIndexOfCall {

        @CompilationFinal(dimensions = 1) final int[] values;

        IndexOfAnyValueCall(int[] values) {
            this.values = values;
        }

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return TStringOps.indexOfAnyInt(location, arrayA, offsetA, strideA, fromIndex, toIndex, values);
        }
    }

    /**
     * Match any of up to two ranges, without decoding.
     */
    static final class IndexOfAnyRangeCall extends OptimizedIndexOfCall {

        @CompilationFinal(dimensions = 1) final int[] values;

        IndexOfAnyRangeCall(int[] values) {
            this.values = values;
        }

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return TStringOps.indexOfAnyIntRange(location, arrayA, offsetA, strideA, fromIndex, toIndex, values);
        }
    }

    private static final int TABLE_SIZE = 16;

    /**
     * Optimized search for bit set.
     */
    static final class IndexOfTableCall extends OptimizedIndexOfCall {

        @CompilationFinal(dimensions = 1) final byte[] tables;

        IndexOfTableCall(byte[] tables) {
            assert tables.length == TABLE_SIZE * 2;
            this.tables = tables;
        }

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return TStringOps.indexOfTable(location, arrayA, offsetA, strideA, fromIndex, toIndex, tables);
        }
    }

    static final class IndexOfStringCall extends OptimizedIndexOfCall {

        final TruffleString b;

        IndexOfStringCall(TruffleString string) {
            this.b = string;
        }

        @Override
        int execute(Node location, Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.Encoding encoding) {
            return TStringOps.indexOfStringWithOrMaskWithStride(location, arrayA, offsetA, lengthA, strideA, b.data(), b.offset(), b.length(), b.stride(), fromIndex, toIndex, null);
        }
    }

    static final class IndexOfBitSetCall extends ScalarIndexOfCall {

        @CompilationFinal(dimensions = 1) final long[] bitSet;

        IndexOfBitSetCall(long[] bitSet) {
            this.bitSet = bitSet;
        }

        @Override
        boolean match(int codepoint) {
            int wordIndex = codepoint >> 6;
            return wordIndex < bitSet.length && (bitSet[wordIndex] & 1L << (codepoint & 63)) != 0;
        }

        static IndexOfBitSetCall fromRanges(int[] ranges) {
            assert getMax(ranges) <= 0xff;
            long[] bitSet = new long[4];
            for (int i = 0; i < ranges.length; i += 2) {
                setRange(bitSet, ranges[i], ranges[i + 1]);
            }
            return new IndexOfBitSetCall(bitSet);
        }

        private static void setRange(long[] bs, int lo, int hi) {
            int wordIndexLo = lo >> 6;
            int wordIndexHi = hi >> 6;
            long rangeLo = (~0L) << lo;
            long rangeHi = (~0L) >>> (63 - (hi & 63));
            if (wordIndexLo == wordIndexHi) {
                bs[wordIndexLo] |= rangeLo & rangeHi;
                return;
            }
            bs[wordIndexLo] |= rangeLo;
            for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
                bs[i] = ~0L;
            }
            bs[wordIndexHi] |= rangeHi;
        }
    }

    static final class IndexOfRangesCall extends ScalarIndexOfCall {

        @CompilationFinal(dimensions = 1) final int[] ranges;

        IndexOfRangesCall(int[] ranges) {
            this.ranges = ranges;
        }

        @Override
        boolean match(int c) {
            int fromIndex = 0;
            int toIndex = (ranges.length >>> 1) - 1;
            while (fromIndex <= toIndex) {
                final int mid = (fromIndex + toIndex) >>> 1;
                if (c < ranges[mid << 1]) {
                    toIndex = mid - 1;
                } else if (c > ranges[(mid << 1) + 1]) {
                    fromIndex = mid + 1;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    private static byte[] generateTable(int[] ranges) {
        byte[] tables = new byte[TABLE_SIZE * 2];
        assert getMax(ranges) <= 0xff;
        // convert set to a 16x16 bit set
        char[] bitSet = new char[16];
        for (int i = 0; i < ranges.length; i += 2) {
            setRange(bitSet, ranges[i], ranges[i + 1]);
        }
        // find equal bit sets
        char[] uniqueValues = new char[16];
        int nUniqueValues = 0;
        for (char c : bitSet) {
            if (c != 0 && ArrayUtils.indexOf(uniqueValues, 0, uniqueValues.length, c) < 0) {
                uniqueValues[nUniqueValues++] = c;
            }
        }
        if (nUniqueValues <= 8) {
            for (int i = 0; i < TABLE_SIZE; i++) {
                if (bitSet[i] != 0) {
                    byte value = (byte) (1 << ArrayUtils.indexOf(uniqueValues, 0, nUniqueValues, bitSet[i]));
                    tables[i] = value;
                    for (int j = 0; j < TABLE_SIZE; j++) {
                        if ((bitSet[i] & (1 << j)) != 0) {
                            tables[TABLE_SIZE + j] |= value;
                        }
                    }
                }
            }
        } else {
            // if we still have more than 8 bit sets, try to reduce them further by decomposition
            CompositeBitSet[] bitSets = new CompositeBitSet[nUniqueValues];
            for (int i = 0; i < nUniqueValues; i++) {
                bitSets[i] = new CompositeBitSet();
            }
            int nComponents = nUniqueValues;
            ArrayList<CompositeBitSet> components = new ArrayList<>();
            for (int i = 0; i < bitSets.length; i++) {
                char cur = uniqueValues[i];
                char compositeValue = 0;
                components.clear();
                for (int j = 0; j < bitSets.length; j++) {
                    if (j == i) {
                        continue;
                    }
                    if ((cur | uniqueValues[j]) == cur) {
                        compositeValue |= uniqueValues[j];
                        components.add(bitSets[j]);
                    }
                }
                if (compositeValue == cur) {
                    bitSets[i].components = components.toArray(CompositeBitSet[]::new);
                    nComponents--;
                }
            }
            if (nComponents > 8) {
                // if there are still more than 8 bit sets after decomposition, give up.
                return null;
            }
            byte value = 1;
            for (int i = 0; i < bitSets.length; i++) {
                CompositeBitSet cbs = bitSets[i];
                if (cbs.components == null) {
                    assert value != 0;
                    cbs.value = value;
                    for (int j = 0; j < TABLE_SIZE; j++) {
                        if ((uniqueValues[i] & (1 << j)) != 0) {
                            tables[TABLE_SIZE + j] |= value;
                        }
                    }
                    value <<= 1;
                }
            }
            for (CompositeBitSet cbs : bitSets) {
                if (cbs.components != null) {
                    for (CompositeBitSet component : cbs.components) {
                        cbs.value |= component.value;
                    }
                }
            }
            for (int i = 0; i < TABLE_SIZE; i++) {
                if (bitSet[i] != 0) {
                    tables[i] = bitSets[ArrayUtils.indexOf(uniqueValues, 0, nUniqueValues, bitSet[i])].value;
                }
            }
        }
        verify1ByteTable(ranges, tables);
        return tables;
    }

    private static final class CompositeBitSet {
        private byte value;
        private CompositeBitSet[] components;
    }

    private static void setRange(char[] bs, int lo, int hi) {
        int wordIndexLo = lo >> 4;
        int wordIndexHi = hi >> 4;
        char rangeLo = (char) (0xffff << (lo & 0xf));
        char rangeHi = (char) (0xffff >>> (15 - (hi & 0xf)));
        if (wordIndexLo == wordIndexHi) {
            bs[wordIndexLo] |= (char) (rangeLo & rangeHi);
            return;
        }
        bs[wordIndexLo] |= rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            bs[i] = (char) ~0;
        }
        bs[wordIndexHi] |= rangeHi;
    }

    private static void verify1ByteTable(int[] expectedRanges, byte[] tables) {
        for (int i = 0; i <= 0xff; i++) {
            assert contains(expectedRanges, i) == match1Byte(tables, (byte) i);
        }
    }

    private static boolean contains(int[] ranges, int v) {
        for (int i = 0; i < ranges.length; i += 2) {
            if (ranges[i] <= v && v <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean match1Byte(byte[] tables, byte b) {
        return performLookup(tables, (byte) 0xff, 0, b) != 0;
    }

    private static byte performLookup(byte[] tables, byte match, int iByte, byte currentByte) {
        byte tableHi = tables[iByte * TABLE_SIZE * 2 + ((currentByte >>> 4) & 0xf)];
        byte tableLo = tables[iByte * TABLE_SIZE * 2 + TABLE_SIZE + (currentByte & 0xf)];
        return (byte) (match & tableHi & tableLo);
    }
}
