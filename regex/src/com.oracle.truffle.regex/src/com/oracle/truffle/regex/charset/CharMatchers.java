/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.charset;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Primitive-array representation of character matchers.
 * <p>
 * Each matcher is a record in one {@code int[]} and is addressed by its record offset. The first
 * word of every record is a header containing a matcher kind and flags; remaining words are the
 * payload for that kind.
 *
 * <pre>
 * matcher record:
 *   [ kindAndFlags, payload... ]
 *
 * empty:
 *   [ EMPTY | flags ]
 *
 * any:
 *   [ ANY | flags ]
 *
 * single-char:
 *   [ SINGLE_CHAR | flags | codePoint << 9 ]
 *
 * two-char:
 *   [ TWO_CHARS | flags | codePoint0 << 9, codePoint1 ]
 *
 * single-range:
 *   [ SINGLE_RANGE | flags | lo << 9, hi ]
 *
 * range-list:
 *   [ RANGE_LIST | flags, rangeCount, lo0, hi0, lo1, hi1, ... ]
 *
 * range-tree:
 *   [ RANGE_TREE | flags, rangeCount, lo0, hi0, lo1, hi1, ... ]
 *
 * bit-set:
 *   [ BIT_SET | flags, highByte, bits0, bits1, ..., bits7 ]
 *
 * null-high-byte-bit-set:
 *   [ NULL_HIGH_BYTE_BIT_SET | flags, bits0, bits1, ..., bits7 ]
 *
 * hybrid-bit-set:
 *   [ HYBRID_BIT_SET | flags, rangeCount,
 *     lo0, hi0, bitSetRecord0 | NO_BIT_SET,
 *     lo1, hi1, bitSetRecord1 | NO_BIT_SET,
 *     ...,
 *     bit-set records... ]
 *
 * embedded bit-set record:
 *   [ bits0, bits1, ..., bits7 ]
 * </pre>
 * <p>
 * {@code bitSetRecord} fields are absolute offsets into the same {@code matchers} array.
 * {@code NO_BIT_SET} marks full-range entries that do not need a bit-set payload.
 */
public final class CharMatchers {

    private static final int KIND_MASK = 0xff;
    private static final int FLAG_INVERT = 1 << 8;
    private static final int HEADER_PAYLOAD_SHIFT = 9;

    /**
     * A matcher that never matches.
     */
    private static final int KIND_EMPTY = 0;
    /**
     * A matcher that always matches.
     */
    private static final int KIND_ANY = 1;
    /**
     * A matcher that matches a single code point.
     */
    private static final int KIND_SINGLE_CHAR = 2;
    /**
     * A matcher that matches two code points. Used for things like dot (.) or ignore-case.
     */
    private static final int KIND_TWO_CHARS = 3;
    /**
     * A matcher for a single code point range.
     */
    private static final int KIND_SINGLE_RANGE = 4;
    /**
     * A matcher using a sorted linear scan of ranges.
     */
    private static final int KIND_RANGE_LIST = 5;
    /**
     * A matcher that uses a binary search over sorted ranges. The PE variant recursively expands
     * small trees.
     *
     * Example:<br>
     * Given a list of ranges [[1, 2], [4, 5], [10, 11], [20, 22]], this matcher will compile to
     * something similar to this:
     *
     * <pre>
     * match(char c) {
     *     if (c < 4) {
     *         return 1 <= c && c <= 2;
     *     } else if (c > 5) {
     *         if (c < 10) {
     *             return false;
     *         } else if (c > 11) {
     *             return 20 <= c && c <= 22;
     *         } else {
     *             return true;
     *         }
     *     } else {
     *         return true;
     *     }
     * }
     * </pre>
     */
    private static final int KIND_RANGE_TREE = 6;
    /**
     * A matcher that matches multiple code points with a common high byte using a bit set. Example:
     * code points {@code 0x1010}, {@code 0x1020}, {@code 0x1030} have a common high byte
     * {@code 0x10}, so they are matched by this high byte and a bit set that matches {@code 0x10},
     * {@code 0x20} and {@code 0x30}.
     */
    private static final int KIND_BIT_SET = 7;
    /**
     * A specialized bit-set matcher for code points whose high byte is {@code 0x00}. ASCII bit-set
     * matchers occur often, and this kind saves one comparison.
     */
    private static final int KIND_NULL_HIGH_BYTE_BIT_SET = 8;
    /**
     * A matcher that uses a binary search over sorted ranges, like {@link #KIND_RANGE_TREE}, but can
     * associate each range with a low-byte bit set. Specifically, if the character set contains many
     * short ranges that differ only in the lowest byte, those ranges are converted to a bit set,
     * which is checked when the binary search finds that the given character is in the range denoted
     * by the bit set. The threshold for bit-set conversion is defined by
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexRangeToBitSetConversionThreshold}.
     *
     * Example:
     *
     * <pre>
     * Character set:
     * [0x02, 0x04, 0x06, 0x1000, 0x1020-0x1030]
     *
     * Resulting hybrid matcher with threshold = 3:
     * - ranges:   [0x02-0x06,          0x1000, 0x1020-0x1030]
     * - bit-sets: [[0x02, 0x04, 0x06], null,   null         ]
     * </pre>
     *
     * @see CompressedCodePointSet
     */
    private static final int KIND_HYBRID_BIT_SET = 9;

    private static final int PREFER_RANGE_LIST_VS_TREE_UP_TO = 6;

    private static final int NO_BIT_SET = -1;
    private static final int BYTE_RANGE = 256;
    private static final int BIT_SET_WORDS = BYTE_RANGE / Integer.SIZE;
    private static final int PE_EXPLODE_THRESHOLD = 16;

    private CharMatchers() {
    }

    public static boolean match(int[] matchers, int matcherRecord, int c) {
        int header = matchers[matcherRecord];
        boolean result = switch (header & KIND_MASK) {
            case KIND_EMPTY ->
                false;
            case KIND_ANY ->
                true;
            case KIND_SINGLE_CHAR ->
                getHeaderCodePoint(header) == c;
            case KIND_TWO_CHARS ->
                getHeaderCodePoint(header) == c || matchers[matcherRecord + 1] == c;
            case KIND_SINGLE_RANGE ->
                getHeaderCodePoint(header) <= c && c <= matchers[matcherRecord + 1];
            case KIND_RANGE_LIST ->
                matchRangeList(matchers, matcherRecord, c);
            case KIND_RANGE_TREE ->
                matchRangeTree(matchers, matcherRecord, c);
            case KIND_BIT_SET ->
                BitSets.highByte(c) == matchers[matcherRecord + 1] && BitSets.get(matchers, matcherRecord + 2, BitSets.lowByte(c));
            case KIND_NULL_HIGH_BYTE_BIT_SET ->
                c < BYTE_RANGE && BitSets.get(matchers, matcherRecord + 1, c);
            case KIND_HYBRID_BIT_SET ->
                matchHybridBitSet(matchers, matcherRecord, c);
            default ->
                throw CompilerDirectives.shouldNotReachHere("unknown encoded matcher kind");
        };
        return result(header, result);
    }

    public static boolean matchPE(int[] matchers, int matcherRecord, int c) {
        CompilerAsserts.partialEvaluationConstant(matchers);
        CompilerAsserts.partialEvaluationConstant(matcherRecord);
        int header = matchers[matcherRecord];
        CompilerAsserts.partialEvaluationConstant(header);
        boolean result = switch (header & KIND_MASK) {
            case KIND_EMPTY ->
                false;
            case KIND_ANY ->
                true;
            case KIND_SINGLE_CHAR ->
                getHeaderCodePoint(header) == c;
            case KIND_TWO_CHARS ->
                getHeaderCodePoint(header) == c || matchers[matcherRecord + 1] == c;
            case KIND_SINGLE_RANGE ->
                getHeaderCodePoint(header) <= c && c <= matchers[matcherRecord + 1];
            case KIND_RANGE_LIST ->
                matchRangeListPE(matchers, matcherRecord, c);
            case KIND_RANGE_TREE ->
                matchRangeTreePE(matchers, matcherRecord, c);
            case KIND_BIT_SET ->
                BitSets.highByte(c) == matchers[matcherRecord + 1] && BitSets.get(matchers, matcherRecord + 2, BitSets.lowByte(c));
            case KIND_NULL_HIGH_BYTE_BIT_SET ->
                c < BYTE_RANGE && BitSets.get(matchers, matcherRecord + 1, c);
            case KIND_HYBRID_BIT_SET ->
                matchHybridBitSetPE(matchers, matcherRecord, c);
            default ->
                throw CompilerDirectives.shouldNotReachHere("unknown encoded matcher kind");
        };
        return result(header, result);
    }

    private static boolean matchRangeList(int[] matchers, int matcherRecord, int c) {
        int rangeCount = matchers[matcherRecord + 1];
        int ranges = matcherRecord + 2;
        for (int i = 0; i < rangeCount; i++) {
            int lo = matchers[ranges + (i << 1)];
            int hi = matchers[ranges + (i << 1) + 1];
            if (lo == hi) {
                if (lo == c) {
                    return true;
                }
            } else if (lo + 1 == hi) {
                if (c == lo || c == hi) {
                    return true;
                }
            } else if (lo <= c) {
                if (hi >= c) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    @ExplodeLoop
    private static boolean matchRangeListPE(int[] matchers, int matcherRecord, int c) {
        int rangeCount = matchers[matcherRecord + 1];
        int ranges = matcherRecord + 2;
        CompilerAsserts.partialEvaluationConstant(rangeCount);
        for (int i = 0; i < rangeCount; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            int lo = matchers[ranges + (i << 1)];
            int hi = matchers[ranges + (i << 1) + 1];
            CompilerAsserts.partialEvaluationConstant(lo);
            CompilerAsserts.partialEvaluationConstant(hi);
            if (lo == hi) {
                if (lo == c) {
                    return true;
                }
            } else if (lo + 1 == hi) {
                if (c == lo || c == hi) {
                    return true;
                }
            } else if (lo <= c) {
                if (hi >= c) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    private static boolean matchRangeTree(int[] matchers, int matcherRecord, int c) {
        int fromIndex = 0;
        int toIndex = matchers[matcherRecord + 1] - 1;
        int ranges = matcherRecord + 2;
        while (fromIndex <= toIndex) {
            int mid = (fromIndex + toIndex) >>> 1;
            int lo = matchers[ranges + (mid << 1)];
            int hi = matchers[ranges + (mid << 1) + 1];
            if (c < lo) {
                toIndex = mid - 1;
            } else if (c > hi) {
                fromIndex = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private static boolean matchRangeTreePE(int[] matchers, int matcherRecord, int c) {
        int rangeCount = matchers[matcherRecord + 1];
        CompilerAsserts.partialEvaluationConstant(rangeCount);
        if (rangeCount > PE_EXPLODE_THRESHOLD) {
            return matchRangeTree(matchers, matcherRecord, c);
        } else {
            return matchRangeTreePE(matchers, matcherRecord + 2, 0, rangeCount - 1, c);
        }
    }

    private static boolean matchRangeTreePE(int[] matchers, int ranges, int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            return false;
        }
        int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        int lo = matchers[ranges + (mid << 1)];
        int hi = matchers[ranges + (mid << 1) + 1];
        CompilerAsserts.partialEvaluationConstant(lo);
        CompilerAsserts.partialEvaluationConstant(hi);
        if (c < lo) {
            return matchRangeTreePE(matchers, ranges, fromIndex, mid - 1, c);
        } else if (c > hi) {
            return matchRangeTreePE(matchers, ranges, mid + 1, toIndex, c);
        } else {
            return true;
        }
    }

    private static boolean matchHybridBitSet(int[] matchers, int matcherRecord, int c) {
        int fromIndex = 0;
        int toIndex = matchers[matcherRecord + 1] - 1;
        int entries = matcherRecord + 2;
        while (fromIndex <= toIndex) {
            int mid = (fromIndex + toIndex) >>> 1;
            int entry = entries + mid * 3;
            if (c < matchers[entry]) {
                toIndex = mid - 1;
            } else if (c > matchers[entry + 1]) {
                fromIndex = mid + 1;
            } else {
                int bitSetRecord = matchers[entry + 2];
                return bitSetRecord == NO_BIT_SET || BitSets.get(matchers, bitSetRecord, BitSets.lowByte(c));
            }
        }
        return false;
    }

    private static boolean matchHybridBitSetPE(int[] matchers, int matcherRecord, int c) {
        int rangeCount = matchers[matcherRecord + 1];
        CompilerAsserts.partialEvaluationConstant(rangeCount);
        if (rangeCount > PE_EXPLODE_THRESHOLD) {
            return matchHybridBitSet(matchers, matcherRecord, c);
        } else {
            return matchHybridBitSetPE(matchers, matcherRecord + 2, 0, rangeCount - 1, c);
        }
    }

    private static boolean matchHybridBitSetPE(int[] matchers, int entries, int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            return false;
        }
        int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        int entry = entries + mid * 3;
        int lo = matchers[entry];
        int hi = matchers[entry + 1];
        CompilerAsserts.partialEvaluationConstant(lo);
        CompilerAsserts.partialEvaluationConstant(hi);
        if (c < lo) {
            return matchHybridBitSetPE(matchers, entries, fromIndex, mid - 1, c);
        } else if (c > hi) {
            return matchHybridBitSetPE(matchers, entries, mid + 1, toIndex, c);
        } else {
            int bitSetRecord = matchers[entry + 2];
            CompilerAsserts.partialEvaluationConstant(bitSetRecord);
            return bitSetRecord == NO_BIT_SET || BitSets.get(matchers, bitSetRecord, BitSets.lowByte(c));
        }
    }

    private static boolean result(int header, boolean result) {
        return ((header & FLAG_INVERT) == 0) == result;
    }

    private static int header(int kind, boolean inverse, int payload) {
        assert payload >>> (Integer.SIZE - HEADER_PAYLOAD_SHIFT) == 0;
        return kind | (inverse ? FLAG_INVERT : 0) | (payload << HEADER_PAYLOAD_SHIFT);
    }

    private static int getHeaderCodePoint(int header) {
        return header >>> HEADER_PAYLOAD_SHIFT;
    }

    /**
     * Conservatively estimate the equivalent number of integer comparisons of calling
     * {@link #match}/{@link #matchPE}.
     *
     * @return the number of integer comparisons one call to {@link #match} is roughly
     *         equivalent to. Array loads are treated as two comparisons.
     */
    public static int estimatedCost(int[] matchers, int matcherRecord) {
        return switch (matchers[matcherRecord] & KIND_MASK) {
            case KIND_EMPTY, KIND_ANY ->
                0;
            case KIND_SINGLE_CHAR ->
                1;
            case KIND_TWO_CHARS, KIND_SINGLE_RANGE ->
                2;
            case KIND_RANGE_LIST ->
                // rangeCount * 2
                matchers[matcherRecord + 1] << 1;
            case KIND_RANGE_TREE, KIND_HYBRID_BIT_SET ->
                // In every node of the binary search, we perform two int comparisons (2). If the
                // tree has n leaves, its depth is log2ceil(n), and the average traversal depth is
                // approximated by depth - 1.
                2 * (MathUtil.log2ceil(matchers[matcherRecord + 1]) - 1);
            case KIND_BIT_SET ->
                // 4 for the bit set check + 1 for the high byte check.
                5;
            case KIND_NULL_HIGH_BYTE_BIT_SET ->
                // 4 for the bit set check.
                4;
            default -> throw new IllegalStateException("unknown encoded matcher kind");
        };
    }

    @TruffleBoundary
    public static String toString(int[] matchers, int matcherRecord) {
        int header = matchers[matcherRecord];
        String modifiers = (header & FLAG_INVERT) == 0 ? "" : "!";
        return modifiers + switch (header & KIND_MASK) {
            case KIND_EMPTY ->
                "[]";
            case KIND_ANY ->
                "*";
            case KIND_SINGLE_CHAR ->
                DebugUtil.charToString(getHeaderCodePoint(header));
            case KIND_TWO_CHARS ->
                DebugUtil.charToString(getHeaderCodePoint(header)) + DebugUtil.charToString(matchers[matcherRecord + 1]);
            case KIND_SINGLE_RANGE ->
                DebugUtil.charToString(getHeaderCodePoint(header)) + "-" + DebugUtil.charToString(matchers[matcherRecord + 1]);
            case KIND_RANGE_LIST ->
                "list[" + Range.rangesToString(matchers, matcherRecord + 2, matchers[matcherRecord + 1]) + "]";
            case KIND_RANGE_TREE ->
                "tree[" + Range.rangesToString(matchers, matcherRecord + 2, matchers[matcherRecord + 1]) + "]";
            case KIND_BIT_SET ->
                "{hi " + DebugUtil.charToString(matchers[matcherRecord + 1]) + " lo " + bitSetToString(matchers, matcherRecord + 2) + "}";
            case KIND_NULL_HIGH_BYTE_BIT_SET ->
                "{ascii " + bitSetToString(matchers, matcherRecord + 1) + "}";
            case KIND_HYBRID_BIT_SET ->
                hybridBitSetToString(matchers, matcherRecord);
            default -> throw new IllegalStateException("unknown encoded matcher kind");
        };
    }

    private static long bitSetWordToLong(int[] matchers, int bitSetRecord, int wordIndex) {
        return Integer.toUnsignedLong(matchers[bitSetRecord + (wordIndex << 1)]) | (Integer.toUnsignedLong(matchers[bitSetRecord + (wordIndex << 1) + 1]) << 32);
    }

    @TruffleBoundary
    private static String bitSetToString(int[] matchers, int bitSetRecord) {
        long[] bitSet = new long[4];
        for (int i = 0; i < bitSet.length; i++) {
            bitSet[i] = bitSetWordToLong(matchers, bitSetRecord, i);
        }
        return BitSets.toString(bitSet);
    }

    @TruffleBoundary
    private static String hybridBitSetToString(int[] matchers, int matcherRecord) {
        StringBuilder sb = new StringBuilder("hybrid[");
        int rangeCount = matchers[matcherRecord + 1];
        int entries = matcherRecord + 2;
        for (int i = 0; i < rangeCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            int entry = entries + i * 3;
            int bitSetRecord = matchers[entry + 2];
            if (bitSetRecord == NO_BIT_SET) {
                sb.append(Range.toString(matchers[entry], matchers[entry + 1]));
            } else {
                sb.append("[range: ").append(Range.toString(matchers[entry], matchers[entry + 1])).append(", bs: ").append(bitSetToString(matchers, bitSetRecord)).append(']');
            }
        }
        return sb.append(']').toString();
    }

    public static final class Builder {

        private final EconomicMap<CodePointSet, Integer> matcherMap = EconomicMap.create();
        private final IntArrayBuffer buffer = new IntArrayBuffer();

        public int getOrCreateMatcher(CodePointSet cps, CompilationBuffer compilationBuffer) {
            Integer record = matcherMap.get(cps);
            if (record == null) {
                record = appendMatcher(cps, compilationBuffer, false);
                matcherMap.put(cps, record);
            }
            return record;
        }

        public int[] toArray() {
            return buffer.toArray();
        }

        public int estimatedCost(int matcherRecord) {
            return CharMatchers.estimatedCost(buffer.getBuffer(), matcherRecord);
        }

        private int appendMatcher(CodePointSet cps, CompilationBuffer compilationBuffer, boolean inverse) {
            if (!inverse && (cps.matchesMinAndMax(compilationBuffer.getEncoding()) || cps.inverseIsSameHighByte(compilationBuffer.getEncoding()))) {
                return appendMatcher(cps.createInverse(compilationBuffer.getEncoding()), compilationBuffer, true);
            }
            if (cps.isEmpty()) {
                return appendHeader(KIND_EMPTY, inverse);
            }
            if (cps.matchesEverything(compilationBuffer.getEncoding())) {
                return appendHeader(KIND_ANY, inverse);
            }
            if (cps.matchesSingleChar()) {
                return appendHeader(KIND_SINGLE_CHAR, inverse, cps.getMin());
            }
            if (cps.valueCountEquals(2)) {
                int record = appendHeader(KIND_TWO_CHARS, inverse, cps.getMin());
                buffer.add(cps.getMax());
                return record;
            }
            int size = cps.size();
            if (size == 1) {
                int record = appendHeader(KIND_SINGLE_RANGE, inverse, cps.getMin());
                buffer.add(cps.getMax());
                return record;
            }
            if (preferRangeListMatcherOverBitSetMatcher(cps, size)) {
                return appendRanges(KIND_RANGE_LIST, inverse, cps.toArray(), size);
            }
            if (BitSets.highByte(cps.getMin()) == BitSets.highByte(cps.getMax())) {
                return appendBitSetMatcher(cps, inverse);
            }
            CompressedCodePointSet ccps = CompressedCodePointSet.create(cps, compilationBuffer);
            if (ccps.hasBitSets()) {
                return appendHybridBitSet(ccps, inverse);
            }
            if (ccps.size() <= PREFER_RANGE_LIST_VS_TREE_UP_TO) {
                return appendRanges(KIND_RANGE_LIST, inverse, ccps.getRanges(), ccps.size());
            }
            return appendRanges(KIND_RANGE_TREE, inverse, ccps.getRanges(), ccps.size());
        }

        private static boolean preferRangeListMatcherOverBitSetMatcher(CodePointSet cps, int size) {
            return size <= 2 || cps.valueCountMax(4);
        }

        private int appendHeader(int kind, boolean inverse) {
            return appendHeader(kind, inverse, 0);
        }

        private int appendHeader(int kind, boolean inverse, int payload) {
            int record = buffer.length();
            buffer.add(header(kind, inverse, payload));
            return record;
        }

        private int appendRanges(int kind, boolean inverse, int[] ranges, int rangeCount) {
            int record = appendHeader(kind, inverse);
            buffer.add(rangeCount);
            buffer.addAll(ranges);
            return record;
        }

        private int appendBitSetMatcher(CodePointSet cps, boolean inverse) {
            int highByte = BitSets.highByte(cps.getMin());
            int record = appendHeader(highByte == 0 ? KIND_NULL_HIGH_BYTE_BIT_SET : KIND_BIT_SET, inverse);
            if (highByte != 0) {
                buffer.add(highByte);
            }
            int[] bitSet = new int[BIT_SET_WORDS];
            for (int i = 0; i < cps.size(); i++) {
                assert BitSets.highByte(cps.getLo(i)) == highByte && BitSets.highByte(cps.getHi(i)) == highByte;
                BitSets.setRange(bitSet, BitSets.lowByte(cps.getLo(i)), BitSets.lowByte(cps.getHi(i)));
            }
            buffer.addAll(bitSet);
            return record;
        }

        private int appendHybridBitSet(CompressedCodePointSet ccps, boolean inverse) {
            int record = appendHeader(KIND_HYBRID_BIT_SET, inverse);
            buffer.add(ccps.size());
            int[] bitSetOffsetPositions = new int[ccps.size()];
            for (int i = 0; i < ccps.size(); i++) {
                buffer.add(ccps.getLo(i));
                buffer.add(ccps.getHi(i));
                bitSetOffsetPositions[i] = buffer.length();
                buffer.add(NO_BIT_SET);
            }
            for (int i = 0; i < ccps.size(); i++) {
                if (ccps.hasBitSet(i)) {
                    buffer.set(bitSetOffsetPositions[i], appendBitSet(ccps.getBitSet(i)));
                }
            }
            return record;
        }

        private int appendBitSet(long[] bitSet) {
            int record = buffer.length();
            for (int i = 0; i < 4; i++) {
                long word = i < bitSet.length ? bitSet[i] : 0;
                buffer.add((int) word);
                buffer.add((int) (word >>> 32));
            }
            return record;
        }

    }
}
