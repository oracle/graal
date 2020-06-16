/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.AnyMatcher;
import com.oracle.truffle.regex.tregex.matchers.BitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.matchers.EmptyMatcher;
import com.oracle.truffle.regex.tregex.matchers.HybridBitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.InvertibleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.MultiBitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.ProfilingCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeListMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeTreeMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleRangeMatcher;
import com.oracle.truffle.regex.tregex.matchers.TwoCharMatcher;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Helper class for converting {@link CodePointSet}s to {@link CharMatcher}s.
 */
public class CharMatchers {

    /**
     * Create a new {@link CharMatcher} from the given code point set, based on {@code encoding}:
     * <ul>
     * <li>If {@code encoding} is {@link Encodings#UTF_16_RAW}, the given set must contain either
     * none or all of the code points above {@code 0xffff}. Code points above {@code 0xffff} are cut
     * off.</li>
     * <li>{@link Encodings#UTF_16} implies the full Unicode range, the code point set may contain
     * any valid (or even {@link Constants#SURROGATES invalid} codepoint.</li>
     * </ul>
     */
    public static CharMatcher createMatcher(CodePointSet cps, Encoding encoding, CompilationBuffer compilationBuffer) {
        if (encoding == Encodings.UTF_16) {
            return createMatcherIntl(cps, compilationBuffer);
        } else if (encoding == Encodings.UTF_16_RAW) {
            return createMatcherIntl(CodePointSetBMPView.create(cps), compilationBuffer);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static CharMatcher createMatcherIntl(ImmutableSortedListOfIntRanges cps, CompilationBuffer compilationBuffer) {
        if (cps.matchesMinAndMax() || cps.inverseIsSameHighByte()) {
            // the inverse of the given set is easier to match, generate inverted matcher
            return createMatcher(cps.createInverse(), compilationBuffer, true);
        }
        return createMatcher(cps, compilationBuffer, false);
    }

    public static int highByte(int c) {
        return c >> Byte.SIZE;
    }

    public static int lowByte(int c) {
        return c & 0xff;
    }

    private static CharMatcher createMatcher(ImmutableSortedListOfIntRanges cps, CompilationBuffer compilationBuffer, boolean inverse) {
        if (cps.isEmpty()) {
            return EmptyMatcher.create(inverse);
        }
        if (cps.matchesEverything()) {
            return AnyMatcher.create(inverse);
        }
        if (cps.matchesSingleChar()) {
            return SingleCharMatcher.create(inverse, cps.getMin());
        }
        if (cps.valueCountEquals(2)) {
            return TwoCharMatcher.create(inverse, cps.getMin(), cps.getMax());
        }
        int size = cps.size();
        if (size == 1) {
            return SingleRangeMatcher.create(inverse, cps.getMin(), cps.getMax());
        }
        if (preferRangeListMatcherOverBitSetMatcher(cps, size)) {
            return RangeListMatcher.create(inverse, cps.toArray());
        }
        InvertibleCharMatcher bitSetMatcher = convertToBitSetMatcher(cps, compilationBuffer, inverse);
        if (bitSetMatcher != null) {
            return bitSetMatcher;
        }
        CharMatcher charMatcher;
        if (size > 100 && cps.getMax() <= 0xffff) {
            charMatcher = MultiBitSetMatcher.fromRanges(inverse, cps);
        } else {
            charMatcher = createHybridMatcher(cps, compilationBuffer, inverse);
        }
        ImmutableSortedListOfIntRanges byteRange = cps instanceof CodePointSet ? Constants.BYTE_RANGE : CodePointSetBMPView.create(Constants.BYTE_RANGE);
        return ProfilingCharMatcher.create(createMatcher(cps.createIntersection(byteRange, compilationBuffer), compilationBuffer, inverse), charMatcher);
    }

    private static boolean preferRangeListMatcherOverBitSetMatcher(ImmutableSortedListOfIntRanges cps, int size) {
        // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
        // also, up to four single character checks are still faster than a bit set
        return size <= 2 || cps.valueCountMax(4);
    }

    private static InvertibleCharMatcher convertToBitSetMatcher(ImmutableSortedListOfIntRanges cps, CompilationBuffer compilationBuffer, boolean inverse) {
        int highByte = highByte(cps.getMin());
        if (highByte(cps.getMax()) != highByte) {
            return null;
        }
        CompilationFinalBitSet bs = compilationBuffer.getByteSizeBitSet();
        for (int i = 0; i < cps.size(); i++) {
            assert highByte(cps.getLo(i)) == highByte && highByte(cps.getHi(i)) == highByte;
            bs.setRange(lowByte(cps.getLo(i)), lowByte(cps.getHi(i)));
        }
        return BitSetMatcher.create(inverse, highByte, bs.copy());
    }

    private static CharMatcher createHybridMatcher(ImmutableSortedListOfIntRanges cps, CompilationBuffer compilationBuffer, boolean inverse) {
        int size = cps.size();
        assert size >= 1;
        IntRangesBuffer ranges = compilationBuffer.getIntRangesBuffer1();
        ObjectArrayBuffer<CompilationFinalBitSet> bitSets = compilationBuffer.getObjectBuffer1();
        // index of lowest range on current plane
        int lowestOCP = 0;
        int curPlane = highByte(cps.getHi(0));
        for (int i = 0; i < size; i++) {
            if (highByte(cps.getLo(i)) != curPlane) {
                processRanges(cps, ranges, bitSets, lowestOCP, i, i, curPlane);
                curPlane = highByte(cps.getLo(i));
                lowestOCP = i;
            }
            if (highByte(cps.getHi(i)) != curPlane) {
                if (lowestOCP != i) {
                    processRanges(cps, ranges, bitSets, lowestOCP, i, i + 1, curPlane);
                }
                curPlane = highByte(cps.getHi(i));
                lowestOCP = i;
            }
        }
        processRanges(cps, ranges, bitSets, lowestOCP, size, size, curPlane);
        if (bitSets.isEmpty()) {
            assert ranges.length() == size * 2;
            if (size <= 10) {
                return RangeListMatcher.create(inverse, cps.toArray());
            } else {
                return RangeTreeMatcher.fromRanges(inverse, cps.toArray());
            }
        }
        assert ranges.rangesAreSortedAndDisjoint();
        return HybridBitSetMatcher.create(inverse, ranges.toArray(), bitSets.toArray(new CompilationFinalBitSet[bitSets.length()]));
    }

    private static void processRanges(ImmutableSortedListOfIntRanges cps, IntRangesBuffer ranges, ObjectArrayBuffer<CompilationFinalBitSet> bitSets, int iMin, int iMax, int iMaxBS, int curPlane) {
        if (isOverBitSetConversionThreshold(iMaxBS - iMin)) {
            addBitSet(cps, bitSets, ranges, iMin, iMaxBS, curPlane);
        } else {
            addRanges(cps, bitSets, ranges, iMin, iMax);
        }
    }

    private static boolean isOverBitSetConversionThreshold(int nRanges) {
        return nRanges >= TRegexOptions.TRegexRangeToBitSetConversionThreshold;
    }

    private static void addRanges(ImmutableSortedListOfIntRanges cps, ObjectArrayBuffer<CompilationFinalBitSet> bitSets, IntRangesBuffer ranges, int iMinArg, int iMax) {
        if (iMinArg == iMax) {
            return;
        }
        int iMin = iMinArg;
        if (!bitSets.isEmpty() && bitSets.peek() != null && highByte(cps.getLo(iMin)) == highByte(ranges.getMax())) {
            ranges.appendRangeAllowAdjacent(ranges.getMax() + 1, cps.getHi(iMin));
            iMin++;
        }
        cps.appendRangesTo(ranges, iMin, iMax);
        for (int i = 0; i < iMax - iMinArg; i++) {
            bitSets.add(null);
        }
    }

    private static void addBitSet(ImmutableSortedListOfIntRanges cps, ObjectArrayBuffer<CompilationFinalBitSet> bitSets, IntRangesBuffer ranges, int iMin, int iMax, int curPlane) {
        assert iMax - iMin > 1;
        int curPlaneLo = curPlane << Byte.SIZE;
        if (rangeCrossesPlanes(cps, iMin)) {
            if (bitSets.isEmpty() || bitSets.peek() == null || highByte(cps.getLo(iMin)) != highByte(ranges.getMax())) {
                ranges.appendRangeAllowAdjacent(cps.getLo(iMin), curPlaneLo - 1);
                bitSets.add(null);
            } else if (highByte(cps.getHi(iMin)) - highByte(cps.getLo(iMin)) > 1) {
                ranges.appendRangeAllowAdjacent(ranges.getMax() + 1, curPlaneLo - 1);
                bitSets.add(null);
            }
        }
        CompilationFinalBitSet bs;
        int bsRangeLo;
        int bsRangeHi;
        int iMinBS = iMin;
        int iMaxBS = iMax;
        if (rangeCrossesPlanes(cps, iMax - 1)) {
            bs = new CompilationFinalBitSet(0xff);
            iMaxBS--;
            bs.setRange(lowByte(cps.getLo(iMax - 1)), 0xff);
            bsRangeHi = curPlaneLo | 0xff;
        } else {
            bs = new CompilationFinalBitSet(lowByte(cps.getHi(iMax - 1)));
            bsRangeHi = cps.getHi(iMax - 1);
        }
        if (rangeCrossesPlanes(cps, iMin)) {
            assert highByte(cps.getHi(iMin)) == curPlane;
            iMinBS++;
            bs.setRange(0, lowByte(cps.getHi(iMin)));
            bsRangeLo = curPlaneLo;
        } else {
            bsRangeLo = cps.getLo(iMin);
        }
        for (int i = iMinBS; i < iMaxBS; i++) {
            assert highByte(cps.getLo(i)) == curPlane && highByte(cps.getHi(i)) == curPlane;
            bs.setRange(lowByte(cps.getLo(i)), lowByte(cps.getHi(i)));
        }
        bitSets.add(bs);
        ranges.appendRangeAllowAdjacent(bsRangeLo, bsRangeHi);
    }

    private static boolean rangeCrossesPlanes(ImmutableSortedListOfIntRanges ranges, int i) {
        return highByte(ranges.getLo(i)) != highByte(ranges.getHi(i));
    }

    @TruffleBoundary
    public static String rangesToString(int[] ranges) {
        return rangesToString(ranges, false);
    }

    @TruffleBoundary
    public static String rangesToString(int[] ranges, boolean numeric) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.length; i += 2) {
            if (numeric) {
                sb.append("[").append(ranges[i]).append("-").append(ranges[i + 1]).append("]");
            } else {
                sb.append(Range.toString(ranges[i], ranges[i + 1]));
            }
        }
        return sb.toString();
    }
}
