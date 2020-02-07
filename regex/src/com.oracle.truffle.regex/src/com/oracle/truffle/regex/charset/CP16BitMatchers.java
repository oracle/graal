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
import com.oracle.truffle.regex.tregex.matchers.MultiBitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.ProfilingCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeListMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeTreeMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleRangeMatcher;
import com.oracle.truffle.regex.tregex.matchers.TwoCharMatcher;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

public class CP16BitMatchers {

    public static CharMatcher createMatcher(CodePointSet cps, CompilationBuffer compilationBuffer) {
        if (cps.sizeOfInverse() < cps.size16() || (cps.size16() > 1 && !allSameHighByte(cps) && highByte((char) (cps.getHi16(0) + 1)) == highByte((char) (cps.getLo16(cps.size16() - 1) - 1)))) {
            return createMatcher(cps.createInverse(), compilationBuffer, true, true);
        }
        return createMatcher(cps, compilationBuffer, false, true);
    }

    private static int highByte(char c) {
        return c >> Byte.SIZE;
    }

    private static int lowByte(char c) {
        return c & 0xff;
    }

    private static boolean allSameHighByte(CodePointSet cps) {
        if (cps.matchesNothing()) {
            return true;
        }
        int highByte = highByte(cps.getLo16(0));
        for (int i = 0; i < cps.size16(); i++) {
            if (highByte(cps.getLo16(i)) != highByte || highByte(cps.getHi16(i)) != highByte) {
                return false;
            }
        }
        return true;
    }

    private static CharMatcher createMatcher(CodePointSet cps, CompilationBuffer compilationBuffer, boolean inverse, boolean tryHybrid) {
        if (cps.matchesNothing()) {
            return EmptyMatcher.create(inverse);
        }
        if (cps.matchesEverything()) {
            return AnyMatcher.create(inverse);
        }
        int size = cps.size16();
        if (size == 1) {
            if (cps.isSingle(0)) {
                return SingleCharMatcher.create(inverse, cps.getLo16(0));
            }
            if (cps.size(0) == 1) {
                // two equality checks are cheaper than one range check
                return TwoCharMatcher.create(inverse, cps.getLo16(0), cps.getHi16(0));
            }
            return SingleRangeMatcher.create(inverse, cps.getLo16(0), cps.getHi16(0));
        }
        if (size == 2 && cps.isSingle(0) && cps.isSingle(1)) {
            return TwoCharMatcher.create(inverse, cps.getLo16(0), cps.getLo16(1));
        }
        if (preferRangeListMatcherOverBitSetMatcher(cps)) {
            return RangeListMatcher.create(inverse, toCharArray(cps));
        }
        if (allSameHighByte(cps)) {
            CompilationFinalBitSet bs = convertToBitSet(cps, 0, size);
            int highByte = highByte(cps.getLo16(0));
            return BitSetMatcher.create(inverse, highByte, bs);
        }
        CharMatcher charMatcher;
        if (size > 100) {
            charMatcher = MultiBitSetMatcher.fromCodePointSet(inverse, cps);
        } else if (tryHybrid) {
            charMatcher = createHybridMatcher(cps, compilationBuffer, inverse);
        } else {
            if (size <= 10) {
                charMatcher = RangeListMatcher.create(inverse, toCharArray(cps));
            } else {
                assert size <= 100;
                charMatcher = RangeTreeMatcher.fromRanges(inverse, toCharArray(cps));
            }
        }
        return ProfilingCharMatcher.create(createMatcher(cps.createIntersection(Constants.BYTE_RANGE, compilationBuffer), compilationBuffer, inverse, false), charMatcher);
    }

    private static boolean preferRangeListMatcherOverBitSetMatcher(CodePointSet cps) {
        // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
        // also, up to four single character checks are still faster than a bit set
        return cps.size16() <= 2 || cps.valueCount() <= 4;
    }

    private static CompilationFinalBitSet convertToBitSet(CodePointSet cps, int iMinArg, int iMaxArg) {
        assert iMaxArg - iMinArg > 1;
        int highByte = highByte(cps.getLo16(iMaxArg - 1));
        CompilationFinalBitSet bs;
        int iMax = iMaxArg;
        if (rangeCrossesPlanes(cps, iMaxArg - 1)) {
            bs = new CompilationFinalBitSet(256);
            iMax--;
            bs.setRange(lowByte(cps.getLo16(iMaxArg - 1)), 0xff);
        } else {
            bs = new CompilationFinalBitSet(Integer.highestOneBit(lowByte(cps.getHi16(iMaxArg - 1))) << 1);
        }
        int iMin = iMinArg;
        if (rangeCrossesPlanes(cps, iMinArg)) {
            assert highByte(cps.getHi16(iMinArg)) == highByte;
            iMin++;
            bs.setRange(0, lowByte(cps.getHi16(iMinArg)));
        }
        for (int i = iMin; i < iMax; i++) {
            assert highByte(cps.getLo16(i)) == highByte && highByte(cps.getHi16(i)) == highByte;
            bs.setRange(lowByte(cps.getLo16(i)), lowByte(cps.getHi16(i)));
        }
        return bs;
    }

    private static CharMatcher createHybridMatcher(CodePointSet cps, CompilationBuffer compilationBuffer, boolean inverse) {
        int size = cps.size16();
        assert size > 1;
        IntRangesBuffer rest = compilationBuffer.getIntRangesBuffer1();
        ByteArrayBuffer highBytes = compilationBuffer.getByteArrayBuffer();
        ObjectArrayBuffer<CompilationFinalBitSet> bitSets = compilationBuffer.getObjectBuffer1();
        int lowestRangeOnCurPlane = 0;
        boolean lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, 0);
        int curPlane = highByte(cps.getHi16(0));
        for (int i = 1; i < size; i++) {
            if (highByte(cps.getLo16(i)) != curPlane) {
                if (i - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
                    highBytes.add((byte) curPlane);
                    bitSets.add(convertToBitSet(cps, lowestRangeOnCurPlane, i));
                    if (!lowestRangeCanBeDeleted) {
                        cps.addRangeTo(rest, lowestRangeOnCurPlane);
                    }
                } else {
                    cps.appendRangesTo(rest, lowestRangeOnCurPlane, i);
                }
                curPlane = highByte(cps.getLo16(i));
                lowestRangeOnCurPlane = i;
                lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
            }
            if (highByte(cps.getHi16(i)) != curPlane) {
                if (lowestRangeOnCurPlane != i) {
                    if ((i + 1) - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
                        highBytes.add((byte) curPlane);
                        bitSets.add(convertToBitSet(cps, lowestRangeOnCurPlane, i + 1));
                        if (!lowestRangeCanBeDeleted) {
                            cps.addRangeTo(rest, lowestRangeOnCurPlane);
                        }
                        lowestRangeCanBeDeleted = highByte(cps.getHi16(i)) - highByte(cps.getLo16(i)) == 1;
                    } else {
                        cps.appendRangesTo(rest, lowestRangeOnCurPlane, i);
                        lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
                    }
                } else {
                    lowestRangeCanBeDeleted = !rangeCrossesPlanes(cps, i);
                }
                curPlane = highByte(cps.getHi16(i));
                lowestRangeOnCurPlane = i;
            }
        }
        if (size - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
            highBytes.add((byte) curPlane);
            bitSets.add(convertToBitSet(cps, lowestRangeOnCurPlane, size));
            if (!lowestRangeCanBeDeleted) {
                cps.addRangeTo(rest, lowestRangeOnCurPlane);
            }
        } else {
            cps.appendRangesTo(rest, lowestRangeOnCurPlane, size);
        }
        if (highBytes.length() == 0) {
            assert rest.length() == size * 2;
            return createMatcher(cps, compilationBuffer, inverse, false);
        }
        CharMatcher restMatcher = createMatcher(CodePointSet.create(rest), compilationBuffer, false, false);
        return HybridBitSetMatcher.create(inverse, highBytes.toArray(), bitSets.toArray(new CompilationFinalBitSet[bitSets.length()]), restMatcher);
    }

    private static boolean rangeCrossesPlanes(CodePointSet cps, int i) {
        return highByte(cps.getLo16(i)) != highByte(cps.getHi16(i));
    }

    private static char[] toCharArray(CodePointSet cps) {
        int length = cps.size16() * 2;
        int[] ranges = cps.getRanges();
        char[] arr = new char[length];
        for (int i = 0; i < length; i++) {
            assert ranges[i] <= Character.MAX_VALUE || ranges[i] == Character.MAX_CODE_POINT;
            arr[i] = (char) ranges[i];
        }
        return arr;
    }

    @TruffleBoundary
    public static String rangesToString(char[] ranges) {
        return rangesToString(ranges, false);
    }

    @TruffleBoundary
    public static String rangesToString(char[] ranges, boolean numeric) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.length; i += 2) {
            if (numeric) {
                sb.append("[").append((int) ranges[i]).append("-").append((int) ranges[i + 1]).append("]");
            } else {
                sb.append(SortedListOfRanges.rangeToString(ranges[i], ranges[i + 1]));
            }
        }
        return sb.toString();
    }
}
