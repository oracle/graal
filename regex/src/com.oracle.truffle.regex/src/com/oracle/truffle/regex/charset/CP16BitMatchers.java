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
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
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
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Helper class for converting 16-bit code point sets to {@link CharMatcher}s.
 */
public class CP16BitMatchers {

    /**
     * Create a new {@link CharMatcher} from the given code point set. The given set must contain
     * either none or all of the code points above {@code 0xffff}. Code points above {@code 0xffff}
     * are cut off.
     */
    public static CharMatcher createMatcher16Bit(CodePointSet cps, CompilationBuffer compilationBuffer) {
        return createMatcher(CodePointSetBMPView.create(cps), compilationBuffer);
    }

    private static CharMatcher createMatcher(CodePointSetBMPView cps, CompilationBuffer compilationBuffer) {
        if (cps.matchesMinAndMax() || cps.inverseIsSameHighByte()) {
            return createMatcher(cps.createInverse(), compilationBuffer, true, true);
        }
        return createMatcher(cps, compilationBuffer, false, true);
    }

    public static int highByte(int c) {
        return (c >> Byte.SIZE) & 0xff;
    }

    public static int lowByte(int c) {
        return c & 0xff;
    }

    private static CharMatcher createMatcher(CodePointSetBMPView cps, CompilationBuffer compilationBuffer, boolean inverse, boolean tryHybrid) {
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
        // TODO: introduce a matcher for huge non-BMP sets
        if (size > 100 && cps.getMax() <= 0xffff) {
            charMatcher = MultiBitSetMatcher.fromCodePointSet(inverse, cps);
        } else if (tryHybrid) {
            charMatcher = createHybridMatcher(cps, compilationBuffer, inverse);
        } else {
            if (size <= 10) {
                charMatcher = RangeListMatcher.create(inverse, cps.toArray());
            } else {
                // TODO: fix this
                // assert size <= 100;
                charMatcher = RangeTreeMatcher.fromRanges(inverse, cps.toArray());
            }
        }
        return ProfilingCharMatcher.create(createMatcher(cps.createIntersection(CodePointSetBMPView.create(Constants.BYTE_RANGE), compilationBuffer), compilationBuffer, inverse, false), charMatcher);
    }

    private static boolean preferRangeListMatcherOverBitSetMatcher(CodePointSetBMPView cps, int size) {
        // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
        // also, up to four single character checks are still faster than a bit set
        return size <= 2 || cps.valueCountMax(4);
    }

    private static InvertibleCharMatcher convertToBitSetMatcher(CodePointSetBMPView cps, CompilationBuffer compilationBuffer, boolean inverse) {
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

    private static CharMatcher createHybridMatcher(CodePointSetBMPView cps, CompilationBuffer compilationBuffer, boolean inverse) {
        int size = cps.size();
        assert size >= 1;
        IntRangesBuffer rest = compilationBuffer.getIntRangesBuffer1();
        ByteArrayBuffer highBytes = compilationBuffer.getByteArrayBuffer();
        ObjectArrayBuffer<CompilationFinalBitSet> bitSets = compilationBuffer.getObjectBuffer1();
        // index of lowest range on current plane
        int lowestOCP = 0;
        boolean lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, 0);
        int curPlane = highByte(cps.getHi(0));
        for (int i = 0; i < size; i++) {
            if (highByte(cps.getLo(i)) != curPlane) {
                if (isOverBitSetConversionThreshold(i - lowestOCP)) {
                    addBitSet(cps, rest, highBytes, bitSets, curPlane, lowestOCP, i, lowestRangeCanBeDeleted);
                } else {
                    cps.appendRangesTo(rest, lowestOCP, i);
                }
                curPlane = highByte(cps.getLo(i));
                lowestOCP = i;
                lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
            }
            if (highByte(cps.getHi(i)) != curPlane) {
                if (lowestOCP != i) {
                    if (isOverBitSetConversionThreshold((i + 1) - lowestOCP)) {
                        addBitSet(cps, rest, highBytes, bitSets, curPlane, lowestOCP, i + 1, lowestRangeCanBeDeleted);
                        lowestRangeCanBeDeleted = highByte(cps.getHi(i)) - highByte(cps.getLo(i)) == 1;
                    } else {
                        cps.appendRangesTo(rest, lowestOCP, i);
                        lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
                    }
                } else {
                    lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
                }
                curPlane = highByte(cps.getHi(i));
                lowestOCP = i;
            }
        }
        if (isOverBitSetConversionThreshold(size - lowestOCP)) {
            addBitSet(cps, rest, highBytes, bitSets, curPlane, lowestOCP, size, lowestRangeCanBeDeleted);
        } else {
            cps.appendRangesTo(rest, lowestOCP, size);
        }
        if (highBytes.length() == 0) {
            assert rest.length() == size * 2;
            return createMatcher(cps, compilationBuffer, inverse, false);
        }
        CharMatcher restMatcher = createMatcher(CodePointSetBMPView.create(rest), compilationBuffer, false, false);
        return HybridBitSetMatcher.create(inverse, highBytes.toArray(), bitSets.toArray(new CompilationFinalBitSet[bitSets.length()]), restMatcher);
    }

    private static boolean isOverBitSetConversionThreshold(int nRanges) {
        return nRanges >= TRegexOptions.TRegexRangeToBitSetConversionThreshold;
    }

    private static void addBitSet(CodePointSetBMPView ranges, IntRangesBuffer rest, ByteArrayBuffer highBytes, ObjectArrayBuffer<CompilationFinalBitSet> bitSets,
                    int curPlane, int lowestOCP, int i, boolean lowestRangeCanBeDeleted) {
        highBytes.add((byte) curPlane);
        bitSets.add(convertToBitSet(ranges, curPlane, lowestOCP, i));
        if (!lowestRangeCanBeDeleted) {
            ranges.addRangeTo(rest, lowestOCP);
        }
    }

    private static boolean rangeCrossesPlanes(CodePointSetBMPView ranges, int i) {
        return highByte(ranges.getLo(i)) != highByte(ranges.getHi(i));
    }

    private static CompilationFinalBitSet convertToBitSet(CodePointSetBMPView ranges, int highByte, int iMinArg, int iMaxArg) {
        assert iMaxArg - iMinArg > 1;
        CompilationFinalBitSet bs;
        int iMax = iMaxArg;
        if (rangeCrossesPlanes(ranges, iMaxArg - 1)) {
            bs = new CompilationFinalBitSet(0xff);
            iMax--;
            bs.setRange(lowByte(ranges.getLo(iMaxArg - 1)), 0xff);
        } else {
            bs = new CompilationFinalBitSet(lowByte(ranges.getHi(iMaxArg - 1)));
        }
        int iMin = iMinArg;
        if (rangeCrossesPlanes(ranges, iMinArg)) {
            assert highByte(ranges.getHi(iMinArg)) == highByte;
            iMin++;
            bs.setRange(0, lowByte(ranges.getHi(iMinArg)));
        }
        for (int i = iMin; i < iMax; i++) {
            assert highByte(ranges.getLo(i)) == highByte && highByte(ranges.getHi(i)) == highByte;
            bs.setRange(lowByte(ranges.getLo(i)), lowByte(ranges.getHi(i)));
        }
        return bs;
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
