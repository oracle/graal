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

import static com.oracle.truffle.api.strings.TStringUnsafe.byteArrayBaseOffset;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.AnyMatchNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfAnyRangeNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfAnyValueNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfBitSetNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfRangesNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfStringNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.IndexOfTableNodeGen;
import com.oracle.truffle.api.strings.IndexOfCodePointSetFactory.NoMatchNodeGen;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

final class IndexOfCodePointSet {

    private static final int[] EMPTY_RANGES = {};
    private static final int[] ASCII_RANGE = {0, 0x7f};
    private static final int[] LATIN_RANGE = {0, 0xff};
    private static final int[] BMP_WITHOUT_SURROGATES = {0x0000, 0xd7ff, 0xe000, 0xffff};
    private static final int[] ALL_WITHOUT_SURROGATES = {0x0000, 0xd7ff, 0xe000, 0x10ffff};
    private static final int[] ALL = {0x0000, 0x10ffff};
    private static final int TABLE_SIZE = 16;

    static IndexOfNode[] fromRanges(int[] ranges, Encoding encoding) {
        checkRangesArray(ranges, encoding);
        return extractIndexOfNodes(ranges, encoding);
    }

    static void checkRangesArray(int[] ranges, Encoding encoding) {
        if ((ranges.length & 1) != 0) {
            throw new IllegalArgumentException("ranges must have an even number of elements");
        }
        int maxCodePoint = Encodings.maxCodePoint(encoding);
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
    }

    private static IndexOfNode[] extractIndexOfNodes(int[] ranges, Encoding encoding) {
        if (encoding == Encoding.US_ASCII || encoding == Encoding.ISO_8859_1 || encoding == Encoding.BYTES || getMax(ranges) <= 0x7f) {
            return extractIndexOfNodes1ByteEncoding(ranges);
        } else if (encoding == Encoding.UTF_8) {
            if (isSingleValue(ranges)) {
                int codepoint = getMin(ranges);
                byte[] encoded = Encodings.utf8Encode(codepoint);
                int codeRange = Encodings.isUTF16Surrogate(codepoint) ? TSCodeRange.getBrokenMultiByte() : TSCodeRange.getValidMultiByte();
                int codepointLength = Encodings.isUTF16Surrogate(codepoint) ? encoded.length : 1;
                return new IndexOfNode[]{IndexOfStringNodeGen.create(TSCodeRange.getBrokenMultiByte(),
                                TruffleString.createFromByteArray(encoded, encoded.length, 0, Encoding.UTF_8, codepointLength, codeRange))};
            } else {
                IndexOfNode ascii = extractIndexOfNodeFixedWidth(TSCodeRange.get7Bit(), ranges, ASCII_RANGE);
                IndexOfRangesNode nonAscii = IndexOfRangesNodeGen.create(TSCodeRange.getBrokenMultiByte(), ranges);
                return ascii.codeEquals(nonAscii) ? new IndexOfNode[]{nonAscii} : new IndexOfNode[]{ascii, nonAscii};
            }
        } else {
            ArrayList<IndexOfNode> nodes = new ArrayList<>();
            nodes.add(extractIndexOfNodeFixedWidth(TSCodeRange.get7Bit(), ranges, ASCII_RANGE));
            addOrReplaceLast(nodes, extractIndexOfNodeFixedWidth(TSCodeRange.get8Bit(), ranges, LATIN_RANGE));
            addOrReplaceLast(nodes, extractIndexOfNodeFixedWidth(TSCodeRange.get16Bit(), ranges, BMP_WITHOUT_SURROGATES));
            if (encoding == Encoding.UTF_16) {
                if (!Arrays.equals(intersect(ranges, BMP_WITHOUT_SURROGATES), ranges)) {
                    if (isSingleValue(ranges)) {
                        int codepoint = getMin(ranges);
                        if (Encodings.isUTF16Surrogate(codepoint)) {
                            addOrReplaceLast(nodes, IndexOfAnyValueNodeGen.create(TSCodeRange.getBrokenMultiByte(), new int[]{codepoint}));
                        } else {
                            assert codepoint > 0xffff;
                            byte[] encoded = Encodings.utf16Encode(codepoint);
                            addOrReplaceLast(nodes, IndexOfStringNodeGen.create(TSCodeRange.getBrokenMultiByte(),
                                            TruffleString.createFromByteArray(encoded, encoded.length >> 1, 1, Encoding.UTF_16, 1, TSCodeRange.getValidMultiByte())));
                        }
                    } else {
                        addOrReplaceLast(nodes, IndexOfRangesNodeGen.create(TSCodeRange.getBrokenMultiByte(), ranges));
                    }
                }

            } else if (encoding == Encoding.UTF_32) {
                addOrReplaceLast(nodes, extractIndexOfNodeFixedWidth(TSCodeRange.getValidFixedWidth(), ranges, ALL_WITHOUT_SURROGATES));
                addOrReplaceLast(nodes, extractIndexOfNodeFixedWidth(TSCodeRange.getBrokenFixedWidth(), ranges, ALL));
            } else {
                throw new UnsupportedOperationException();
            }
            return nodes.toArray(IndexOfNode[]::new);
        }
    }

    private static void addOrReplaceLast(ArrayList<IndexOfNode> nodes, IndexOfNode node) {
        if (nodes.get(nodes.size() - 1).codeEquals(node)) {
            assert TSCodeRange.isMoreRestrictiveThan(nodes.get(nodes.size() - 1).maxCodeRange, node.maxCodeRange);
            nodes.remove(nodes.size() - 1);
        }
        nodes.add(node);
    }

    private static void checkIllegalCodepoint(int c, int maxCodePoint) {
        if (Integer.toUnsignedLong(c) > maxCodePoint) {
            throw new IllegalArgumentException(String.format("illegal codepoint value 0x%x", c));
        }
    }

    private static IndexOfNode[] extractIndexOfNodes1ByteEncoding(int[] ranges) {
        IndexOfNode ascii = extractIndexOfNodeFixedWidth(TSCodeRange.get7Bit(), ranges, ASCII_RANGE);
        IndexOfNode latin = extractIndexOfNodeFixedWidth(TSCodeRange.get8Bit(), ranges, LATIN_RANGE);
        return ascii.codeEquals(latin) ? new IndexOfNode[]{latin} : new IndexOfNode[]{ascii, latin};
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

    private static IndexOfNode extractIndexOfNodeFixedWidth(int maxCodeRange, int[] ranges, int[] bounds) {
        int[] intersection = intersect(ranges, bounds);
        if (intersection.length == 0) {
            return NoMatchNodeGen.create(maxCodeRange);
        }
        if (Arrays.equals(intersection, bounds)) {
            return AnyMatchNodeGen.create(maxCodeRange);
        }
        int valueCount = valueCount(intersection);
        if (valueCount <= 4) {
            return IndexOfAnyValueNodeGen.create(maxCodeRange, toValues(intersection, valueCount));
        } else if (size(intersection) <= 2) {
            return IndexOfAnyRangeNodeGen.create(maxCodeRange, intersection);
        } else if (getMax(intersection) <= 0xff) {
            byte[] tables = generateTable(intersection);
            if (tables != null) {
                return IndexOfTableNodeGen.create(maxCodeRange, tables);
            } else {
                return IndexOfBitSetNode.fromRanges(maxCodeRange, intersection);
            }
        }
        return IndexOfRangesNodeGen.create(maxCodeRange, intersection);
    }

    private static boolean isEmpty(int[] ranges) {
        return ranges.length == 0;
    }

    /**
     * Returns the number of ranges in the given list of ranges.
     */
    private static int size(int[] ranges) {
        return ranges.length >> 1;
    }

    /**
     * Returns the lower bound of range {@code i}.
     */
    private static int getLo(int[] ranges, int i) {
        return ranges[i << 1];
    }

    /**
     * Returns the upper bound of range {@code i}.
     */
    private static int getHi(int[] ranges, int i) {
        return ranges[(i << 1) + 1];
    }

    /**
     * Returns the minimum value contained in the given list of ranges.
     */
    private static int getMin(int[] ranges) {
        return ranges[0];
    }

    /**
     * Returns the maximum value contained in the given list of ranges.
     */
    private static int getMax(int[] ranges) {
        return ranges[ranges.length - 1];
    }

    /**
     * Returns {@code true} if the given list of range contains only one value, i.e. it consists of
     * only one single-value range.
     */
    private static boolean isSingleValue(int[] ranges) {
        return ranges.length == 2 && ranges[0] == ranges[1];
    }

    /**
     * Returns the number of values contained in the given list of ranges.
     */
    private static int valueCount(int[] ranges) {
        int count = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            count += (ranges[i + 1] - ranges[i]) + 1;
        }
        return count;
    }

    /**
     * Returns {@code true} if the given list of ranges contains value {@code v}.
     */
    private static boolean contains(int[] ranges, int v) {
        for (int i = 0; i < ranges.length; i += 2) {
            if (ranges[i] <= v && v <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts the given list of ranges to an array of values, e.g.
     * {@code [1-3, 5-6] -> [1, 2, 3, 5, 6]}.
     */
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

    abstract static class IndexOfNode extends Node {

        final byte maxCodeRange;

        IndexOfNode(int maxCodeRange) {
            assert TSCodeRange.isCodeRange(maxCodeRange);
            this.maxCodeRange = (byte) maxCodeRange;
        }

        abstract int execute(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding);

        @Specialization
        int doWithConditionProfile(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding,
                        @Cached InlinedBranchProfile branchProfile) {
            branchProfile.enter(this);
            return runSearch(location, arrayA, offsetA, lengthA, strideA, codeRangeA, fromIndex, toIndex, encoding);
        }

        @SuppressWarnings("unused")
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @SuppressWarnings("unused")
        boolean codeEquals(IndexOfNode other) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @SuppressWarnings("unused")
        IndexOfNode shallowCopy() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final byte getMaxCodeRange() {
            return maxCodeRange;
        }

        final boolean isFast() {
            return this instanceof OptimizedIndexOfNode;
        }
    }

    abstract static class OptimizedIndexOfNode extends IndexOfNode {
        OptimizedIndexOfNode(int maxCodeRange) {
            super(maxCodeRange);
        }
    }

    abstract static class ScalarIndexOfNode extends IndexOfNode {

        ScalarIndexOfNode(int maxCodeRange) {
            super(maxCodeRange);
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            CompilerAsserts.partialEvaluationConstant(this);
            CompilerAsserts.partialEvaluationConstant(encoding);
            int codepointLength = 1;
            // iterate codepoints
            for (int i = fromIndex; i < toIndex; i += codepointLength) {
                final int codepoint;
                if (encoding == Encoding.US_ASCII || encoding == Encoding.ISO_8859_1 || encoding == Encoding.BYTES || TSCodeRange.isFixedWidth(codeRangeA)) {
                    // fixed-width encoding: just read the next array element
                    codepoint = TStringOps.readValue(arrayA, offsetA, lengthA, strideA, i);
                } else if (encoding == Encoding.UTF_8) {
                    // utf-8 decode
                    if (TSCodeRange.isValid(codeRangeA)) {
                        int firstByte = TStringOps.readS0(arrayA, offsetA, lengthA, i);
                        codepointLength = firstByte <= 0x7f ? 1 : Encodings.utf8CodePointLength(firstByte);
                        codepoint = Encodings.utf8DecodeValid(arrayA, offsetA, lengthA, i);
                    } else {
                        codepointLength = Encodings.utf8GetCodePointLength(arrayA, offsetA, lengthA, i, DecodingErrorHandler.DEFAULT);
                        codepoint = Encodings.utf8DecodeBroken(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT);
                    }
                } else {
                    // utf-16 decode
                    assert encoding == Encoding.UTF_16;
                    if (TSCodeRange.isValid(codeRangeA)) {
                        codepointLength = Encodings.isUTF16HighSurrogate(TStringOps.readS1(arrayA, offsetA, lengthA, i)) ? 2 : 1;
                        codepoint = Encodings.utf16DecodeValid(arrayA, offsetA, lengthA, i);
                    } else {
                        codepointLength = Encodings.utf16BrokenGetCodePointByteLength(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT) >> 1;
                        codepoint = Encodings.utf16DecodeBroken(arrayA, offsetA, lengthA, i, TruffleString.ErrorHandling.BEST_EFFORT);
                    }
                }
                // check if the decoded codepoint is contained in the codepoint set
                if (match(codepoint)) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unused")
        boolean match(int codepoint) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * No match possible.
     */
    abstract static class NoMatch extends OptimizedIndexOfNode {

        NoMatch(int maxCodeRange) {
            super(maxCodeRange);
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            return -1;
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof NoMatch;
        }

        @Override
        IndexOfNode shallowCopy() {
            return NoMatchNodeGen.create(maxCodeRange);
        }
    }

    /**
     * Will always match immediately.
     */
    abstract static class AnyMatch extends OptimizedIndexOfNode {

        AnyMatch(int maxCodeRange) {
            super(maxCodeRange);
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            return fromIndex;
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof AnyMatch;
        }

        @Override
        IndexOfNode shallowCopy() {
            return AnyMatchNodeGen.create(maxCodeRange);
        }
    }

    /**
     * Match any of up to four values, without decoding.
     */
    abstract static class IndexOfAnyValueNode extends OptimizedIndexOfNode {

        @CompilationFinal(dimensions = 1) final int[] values;

        IndexOfAnyValueNode(int maxCodeRange, int[] values) {
            super(maxCodeRange);
            this.values = values;
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            return TStringOps.indexOfAnyInt(location, arrayA, offsetA, strideA, fromIndex, toIndex, values);
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfAnyValueNode && Arrays.equals(values, ((IndexOfAnyValueNode) other).values);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfAnyValueNodeGen.create(maxCodeRange, values);
        }
    }

    /**
     * Match any of up to two ranges, without decoding.
     */
    abstract static class IndexOfAnyRangeNode extends OptimizedIndexOfNode {

        @CompilationFinal(dimensions = 1) final int[] ranges;

        IndexOfAnyRangeNode(int maxCodeRange, int[] ranges) {
            super(maxCodeRange);
            this.ranges = ranges;
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            return TStringOps.indexOfAnyIntRange(location, arrayA, offsetA, strideA, fromIndex, toIndex, ranges);
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfAnyRangeNode && Arrays.equals(ranges, ((IndexOfAnyRangeNode) other).ranges);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfAnyRangeNodeGen.create(maxCodeRange, ranges);
        }
    }

    /**
     * Optimized search for bit set.
     */
    abstract static class IndexOfTableNode extends OptimizedIndexOfNode {

        @CompilationFinal(dimensions = 1) final byte[] tables;

        IndexOfTableNode(int maxCodeRange, byte[] tables) {
            super(maxCodeRange);
            assert tables.length == TABLE_SIZE * 2;
            this.tables = tables;
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            return TStringOps.indexOfTable(location, arrayA, offsetA, strideA, fromIndex, toIndex, tables);
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfTableNode && Arrays.equals(tables, ((IndexOfTableNode) other).tables);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfTableNodeGen.create(maxCodeRange, tables);
        }
    }

    abstract static class IndexOfStringNode extends OptimizedIndexOfNode {

        final TruffleString str;

        IndexOfStringNode(int maxCodeRange, TruffleString string) {
            super(maxCodeRange);
            this.str = string;
        }

        @Override
        int runSearch(Node location, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, Encoding encoding) {
            assert str.isManaged() && str.isMaterialized() && str.offset() == 0;
            return TStringOps.indexOfStringWithOrMaskWithStride(location, arrayA, offsetA, lengthA, strideA,
                            (byte[]) str.data(), byteArrayBaseOffset(), str.length(), str.stride(), fromIndex, toIndex, null);
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfStringNode && str.equals(((IndexOfStringNode) other).str);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfStringNodeGen.create(maxCodeRange, str);
        }
    }

    abstract static class IndexOfBitSetNode extends ScalarIndexOfNode {

        @CompilationFinal(dimensions = 1) final long[] bitSet;

        IndexOfBitSetNode(int maxCodeRange, long[] bitSet) {
            super(maxCodeRange);
            this.bitSet = bitSet;
        }

        @Override
        boolean match(int codepoint) {
            int wordIndex = codepoint >> 6;
            return wordIndex < bitSet.length && (bitSet[wordIndex] & 1L << (codepoint & 63)) != 0;
        }

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfBitSetNode && Arrays.equals(bitSet, ((IndexOfBitSetNode) other).bitSet);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfBitSetNodeGen.create(maxCodeRange, bitSet);
        }

        static IndexOfBitSetNode fromRanges(int maxCodeRange, int[] ranges) {
            assert getMax(ranges) <= 0xff;
            long[] bitSet = new long[4];
            for (int i = 0; i < ranges.length; i += 2) {
                setRange(bitSet, ranges[i], ranges[i + 1]);
            }
            return IndexOfBitSetNodeGen.create(maxCodeRange, bitSet);
        }

        /**
         * Sets all values contained in range {@code [lo-hi]} (inclusive) to {@code 1} in the given
         * long-array based bit set.
         */
        private static void setRange(long[] bitSet, int lo, int hi) {
            int wordIndexLo = lo >> 6;
            int wordIndexHi = hi >> 6;
            long rangeLo = (~0L) << lo;
            long rangeHi = (~0L) >>> (63 - (hi & 63));
            if (wordIndexLo == wordIndexHi) {
                bitSet[wordIndexLo] |= rangeLo & rangeHi;
                return;
            }
            bitSet[wordIndexLo] |= rangeLo;
            for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
                bitSet[i] = ~0L;
            }
            bitSet[wordIndexHi] |= rangeHi;
        }
    }

    abstract static class IndexOfRangesNode extends ScalarIndexOfNode {

        @CompilationFinal(dimensions = 1) final int[] ranges;

        IndexOfRangesNode(int maxCodeRange, int[] ranges) {
            super(maxCodeRange);
            this.ranges = ranges;
        }

        @Override
        boolean match(int c) {
            return rangesContain(ranges, c);
        }

        static boolean rangesContain(int[] ranges, int c) {
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

        @Override
        boolean codeEquals(IndexOfNode other) {
            return other instanceof IndexOfRangesNode && Arrays.equals(ranges, ((IndexOfRangesNode) other).ranges);
        }

        @Override
        IndexOfNode shallowCopy() {
            return IndexOfRangesNodeGen.create(maxCodeRange, ranges);
        }
    }

    /**
     * Converts a given list of ranges to a lookup table suitable for {@link IndexOfTableNode}.
     *
     * @return the lookup table, or {@code null} if no suitable lookup table could be generated.
     */
    private static byte[] generateTable(int[] ranges) {
        assert getMax(ranges) <= 0xff;
        /*
         * Convert ranges to a 16x16 bit set. Matching a byte with this bit set would work like
         * this:
         *
         * byte v = readByte(...);
         *
         * boolean match = (bitSet[v >>> 4] & (1 << (v & 0xf)) != 0;
         *
         * Now we have to transform this bit set to a 32-byte lookup table that can be matched like
         * this:
         *
         * boolean match = (table[v >>> 4] & table[16 + (v & 0xf)]) != 0;
         *
         * In the following (v >>> 4) is referred to as the "upper nibble" and (v & 0xf) is referred
         * to as the "lower nibble".
         */
        char[] bitSet = new char[16];
        for (int i = 0; i < ranges.length; i += 2) {
            setRange(bitSet, ranges[i], ranges[i + 1]);
        }
        // find equal 16-bit values in the 16x16 bit set
        char[] uniqueValues = new char[16];
        int nUniqueValues = 0;
        for (char c : bitSet) {
            if (c != 0 && ArrayUtils.indexOf(uniqueValues, 0, uniqueValues.length, c) < 0) {
                uniqueValues[nUniqueValues++] = c;
            }
        }
        if (nUniqueValues <= 8) {
            return generateTableDirectMapping(ranges, bitSet, uniqueValues, nUniqueValues);
        } else {
            return generateTableTryDecomposition(ranges, bitSet, uniqueValues, nUniqueValues);
        }
    }

    private static byte[] generateTableDirectMapping(int[] ranges, char[] bitSet, char[] uniqueValues, int nUniqueValues) {
        byte[] tables = new byte[TABLE_SIZE * 2];
        // If there are no more than 8 unique values, we can assign one unique bit per upper
        // nibble values:
        // iterate all possible upper nibble values
        for (int upperNibble = 0; upperNibble < TABLE_SIZE; upperNibble++) {
            if (bitSet[upperNibble] != 0) {
                // get the unique bit corresponding to the current upper nibble value
                byte uniqueBit = (byte) (1 << ArrayUtils.indexOf(uniqueValues, 0, nUniqueValues, bitSet[upperNibble]));
                // set upper nibble entry
                tables[upperNibble] = uniqueBit;
                // add the unique bit to all lower nibble entries that should match in
                // conjunction with the current upper nibble
                for (int lowerNibble = 0; lowerNibble < TABLE_SIZE; lowerNibble++) {
                    if ((bitSet[upperNibble] & (1 << lowerNibble)) != 0) {
                        tables[TABLE_SIZE + lowerNibble] |= uniqueBit;
                    }
                }
            }
        }
        verifyTable(ranges, tables);
        return tables;
    }

    private static byte[] generateTableTryDecomposition(int[] ranges, char[] bitSet, char[] uniqueValues, int nUniqueValues) {
        assert nUniqueValues > 8;
        byte[] tables = new byte[TABLE_SIZE * 2];
        // if we have more than 8 unique bit set values, try to reduce them by decomposition, i.e.
        // try to find values that can be expressed as a union of other values in the bit set
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
                    // uniqueValues[j] is a subset of cur, add it to the list of components
                    compositeValue |= uniqueValues[j];
                    components.add(bitSets[j]);
                }
            }
            if (compositeValue == cur) {
                // we found a list of components whose union is exactly _cur_, save it
                bitSets[i].components = components.toArray(CompositeBitSet[]::new);
                nComponents--;
            }
        }
        if (nComponents > 8) {
            // if there are still more than 8 unique bit set values after decomposition, give up.
            return null;
        }
        byte uniqueBit = 1;
        for (int i = 0; i < bitSets.length; i++) {
            CompositeBitSet cbs = bitSets[i];
            if (cbs.components == null) {
                assert uniqueBit != 0;
                // assign one unique bit per component that could _not_ be decomposed.
                cbs.uniqueBit = uniqueBit;
                // add the unique bit to all lower nibble entries that should match in
                // conjunction with the current upper nibble
                for (int lowerNibble = 0; lowerNibble < TABLE_SIZE; lowerNibble++) {
                    if ((uniqueValues[i] & (1 << lowerNibble)) != 0) {
                        tables[TABLE_SIZE + lowerNibble] |= uniqueBit;
                    }
                }
                uniqueBit <<= 1;
            }
        }
        for (CompositeBitSet cbs : bitSets) {
            if (cbs.components != null) {
                // assign union of subcomponent's unique bits to decomposed values
                for (CompositeBitSet component : cbs.components) {
                    cbs.uniqueBit |= component.uniqueBit;
                }
            }
        }
        // write upper nibble mapping to table
        for (int upperNibble = 0; upperNibble < TABLE_SIZE; upperNibble++) {
            if (bitSet[upperNibble] != 0) {
                tables[upperNibble] = bitSets[ArrayUtils.indexOf(uniqueValues, 0, nUniqueValues, bitSet[upperNibble])].uniqueBit;
            }
        }
        verifyTable(ranges, tables);
        return tables;
    }

    private static final class CompositeBitSet {
        private byte uniqueBit;
        private CompositeBitSet[] components;
    }

    /**
     * Sets all values contained in range {@code [lo-hi]} (inclusive) to {@code 1} in the given
     * char-array based bit set.
     */
    private static void setRange(char[] bitSet, int lo, int hi) {
        int wordIndexLo = lo >> 4;
        int wordIndexHi = hi >> 4;
        char rangeLo = (char) (0xffff << (lo & 0xf));
        char rangeHi = (char) (0xffff >>> (15 - (hi & 0xf)));
        if (wordIndexLo == wordIndexHi) {
            bitSet[wordIndexLo] |= (char) (rangeLo & rangeHi);
            return;
        }
        bitSet[wordIndexLo] |= rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            bitSet[i] = (char) ~0;
        }
        bitSet[wordIndexHi] |= rangeHi;
    }

    private static void verifyTable(int[] expectedRanges, byte[] tables) {
        assert verifyTableInner(expectedRanges, tables);
    }

    private static boolean verifyTableInner(int[] expectedRanges, byte[] tables) {
        for (int i = 0; i <= 0xff; i++) {
            assert contains(expectedRanges, i) == ((tables[(i >>> 4) & 0xf] & tables[TABLE_SIZE + (i & 0xf)]) != 0);
        }
        return true;
    }

}
