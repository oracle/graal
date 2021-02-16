/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.regex.util.BitSets.highByte;
import static com.oracle.truffle.regex.util.BitSets.lowByte;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.HybridBitSetMatcher;
import com.oracle.truffle.regex.tregex.nodes.dfa.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Compressed variant of {@link CodePointSet}. Stores clusters of small ranges as bit sets, if
 * possible. Every range in this set's sorted list of ranges may be associated to a bit set, which
 * stores the more "fine-grained" ranges inside the larger range.
 *
 * Example:
 * 
 * <pre>
 * Character set:
 * [0x02, 0x04, 0x06, 0x1000, 0x1020-0x1030]
 *
 * Resulting compressed code point set:
 * - ranges:   [0x02-0x06,          0x1000, 0x1020-0x1030]
 * - bit-sets: [[0x02, 0x04, 0x06], null,   null         ]
 * </pre>
 *
 * @see HybridBitSetMatcher
 * @see AllTransitionsInOneTreeMatcher
 */
public final class CompressedCodePointSet {

    private static final long[][] NO_BITSETS = {};

    @CompilationFinal(dimensions = 1) private final int[] ranges;
    @CompilationFinal(dimensions = 2) private final long[][] bitSets;

    private CompressedCodePointSet(int[] ranges, long[][] bitSets) {
        this.ranges = ranges;
        this.bitSets = bitSets;
        assert this.bitSets == null || this.bitSets.length == this.ranges.length / 2;
    }

    public int[] getRanges() {
        return ranges;
    }

    public boolean hasBitSets() {
        return bitSets != null;
    }

    public long[][] getBitSets() {
        return bitSets;
    }

    public int getLo(int i) {
        return ranges[i * 2];
    }

    public int getHi(int i) {
        return ranges[(i * 2) + 1];
    }

    public int size() {
        return ranges.length / 2;
    }

    public boolean hasBitSet(int i) {
        return hasBitSets() && bitSets[i] != null;
    }

    public long[] getBitSet(int i) {
        return bitSets[i];
    }

    public static CompressedCodePointSet create(ImmutableSortedListOfIntRanges cps, CompilationBuffer compilationBuffer) {
        int size = cps.size();
        if (size < TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
            return new CompressedCodePointSet(cps.toArray(), null);
        }
        if (highByte(cps.getMin()) == highByte(cps.getMax())) {
            return convertToBitSet(cps);
        }
        assert size >= 1;
        IntRangesBuffer ranges = compilationBuffer.getIntRangesBuffer1();
        ObjectArrayBuffer<long[]> bitSets = compilationBuffer.getObjectBuffer1();
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
            return new CompressedCodePointSet(cps.toArray(), null);
        }
        assert ranges.rangesAreSortedAndDisjoint();
        return new CompressedCodePointSet(ranges.toArray(), bitSets.toArray(CompressedCodePointSet.NO_BITSETS));
    }

    private static CompressedCodePointSet convertToBitSet(ImmutableSortedListOfIntRanges cps) {
        int highByte = highByte(cps.getMin());
        long[] bs = BitSets.createBitSetArray(cps.getMax() + 1);
        for (int i = 0; i < cps.size(); i++) {
            assert highByte(cps.getLo(i)) == highByte && highByte(cps.getHi(i)) == highByte;
            BitSets.setRange(bs, lowByte(cps.getLo(i)), lowByte(cps.getHi(i)));
        }
        return new CompressedCodePointSet(new int[]{cps.getMin(), cps.getMax()}, new long[][]{bs});
    }

    private static void processRanges(ImmutableSortedListOfIntRanges cps, IntRangesBuffer ranges, ObjectArrayBuffer<long[]> bitSets, int iMin, int iMax, int iMaxBS, int curPlane) {
        if (iMaxBS - iMin >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
            addBitSet(cps, bitSets, ranges, iMin, iMaxBS, curPlane);
        } else {
            addRanges(cps, bitSets, ranges, iMin, iMax);
        }
    }

    private static void addRanges(ImmutableSortedListOfIntRanges cps, ObjectArrayBuffer<long[]> bitSets, IntRangesBuffer ranges, int iMinArg, int iMax) {
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

    private static void addBitSet(ImmutableSortedListOfIntRanges cps, ObjectArrayBuffer<long[]> bitSets, IntRangesBuffer ranges, int iMin, int iMax, int curPlane) {
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
        long[] bs;
        int bsRangeLo;
        int bsRangeHi;
        int iMinBS = iMin;
        int iMaxBS = iMax;
        if (rangeCrossesPlanes(cps, iMax - 1)) {
            bs = BitSets.createBitSetArray(0x100);
            iMaxBS--;
            BitSets.setRange(bs, lowByte(cps.getLo(iMax - 1)), 0xff);
            bsRangeHi = curPlaneLo | 0xff;
        } else {
            bs = BitSets.createBitSetArray(lowByte(cps.getHi(iMax - 1)) + 1);
            bsRangeHi = cps.getHi(iMax - 1);
        }
        if (rangeCrossesPlanes(cps, iMin)) {
            assert highByte(cps.getHi(iMin)) == curPlane;
            iMinBS++;
            BitSets.setRange(bs, 0, lowByte(cps.getHi(iMin)));
            bsRangeLo = curPlaneLo;
        } else {
            bsRangeLo = cps.getLo(iMin);
        }
        for (int i = iMinBS; i < iMaxBS; i++) {
            assert highByte(cps.getLo(i)) == curPlane && highByte(cps.getHi(i)) == curPlane;
            BitSets.setRange(bs, lowByte(cps.getLo(i)), lowByte(cps.getHi(i)));
        }
        bitSets.add(bs);
        ranges.appendRangeAllowAdjacent(bsRangeLo, bsRangeHi);
    }

    private static boolean rangeCrossesPlanes(ImmutableSortedListOfIntRanges ranges, int i) {
        return highByte(ranges.getLo(i)) != highByte(ranges.getHi(i));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof CompressedCodePointSet) {
            return Arrays.equals(ranges, ((CompressedCodePointSet) obj).ranges) && Arrays.deepEquals(bitSets, ((CompressedCodePointSet) obj).bitSets);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges) * 31 + Arrays.deepHashCode(bitSets);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (bitSets == null) {
            return "[" + CharMatchers.rangesToString(ranges) + "]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ranges.length; i += 2) {
            if (bitSets[i / 2] == null) {
                sb.append(Range.toString(ranges[i], ranges[i + 1]));
            } else {
                sb.append("[range: ").append(Range.toString(ranges[i], ranges[i + 1])).append(", bs: ").append(BitSets.toString(bitSets[i / 2])).append("]");
            }
        }
        return sb.append("]").toString();
    }
}
