/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.charset;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CharRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
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
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

public final class CharSet implements ImmutableSortedListOfRanges, Comparable<CharSet>, JsonConvertible {

    private static final CharSet BYTE_RANGE = new CharSet(new char[]{0x00, 0xff});
    private static final CharSet CONSTANT_EMPTY = new CharSet(new char[0]);
    private static final CharSet CONSTANT_FULL = new CharSet(new char[]{Character.MIN_VALUE, Character.MAX_VALUE});

    private static final CharSet[] CONSTANT_ASCII = new CharSet[128];
    private static final CharSet[] CONSTANT_INVERSE_ASCII = new CharSet[128];
    private static final CharSet[] CONSTANT_CASE_FOLD_ASCII = new CharSet[26];

    private static final CharSet[] CONSTANT_CODE_POINT_SETS_MB;

    private static final CharSet CONSTANT_TRAIL_SURROGATE_RANGE = new CharSet(new char[]{(char) Constants.TRAIL_SURROGATE_RANGE.getLo(0), (char) Constants.TRAIL_SURROGATE_RANGE.getHi(0)});

    static {
        CONSTANT_ASCII[0] = new CharSet(new char[]{0, 0});
        CONSTANT_INVERSE_ASCII[0] = new CharSet(new char[]{1, Character.MAX_VALUE});
        for (char i = 1; i < 128; i++) {
            CONSTANT_ASCII[i] = new CharSet(new char[]{i, i});
            CONSTANT_INVERSE_ASCII[i] = new CharSet(new char[]{0, (char) (i - 1), (char) (i + 1), Character.MAX_VALUE});
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            CONSTANT_CASE_FOLD_ASCII[i - 'A'] = new CharSet(new char[]{i, i, Character.toLowerCase(i), Character.toLowerCase(i)});
        }
        CONSTANT_CODE_POINT_SETS_MB = new CharSet[Constants.CONSTANT_CODE_POINT_SETS.length];
        for (int i = 0; i < Constants.CONSTANT_CODE_POINT_SETS.length; i++) {
            CONSTANT_CODE_POINT_SETS_MB[i] = createTrimCodePointSet(Constants.CONSTANT_CODE_POINT_SETS[i]);
        }
    }

    private final char[] ranges;

    private CharSet(char[] ranges) {
        this.ranges = ranges;
        assert (ranges.length & 1) == 0 : "ranges array must have an even length!";
        assert rangesAreSortedAndDisjoint() : rangesToString(ranges, true);
    }

    public char[] getRanges() {
        return ranges;
    }

    public static CharSet getEmpty() {
        return CONSTANT_EMPTY;
    }

    public static CharSet getFull() {
        return CONSTANT_FULL;
    }

    public static CharSet getTrailSurrogateRange() {
        return CONSTANT_TRAIL_SURROGATE_RANGE;
    }

    public static CharSet create(char... ranges) {
        CharSet constant = checkConstants(ranges, ranges.length);
        if (constant == null) {
            return new CharSet(ranges);
        }
        return constant;
    }

    public static CharSet fromSortedRanges(SortedListOfRanges codePointSet) {
        if (codePointSet.matchesNothing()) {
            return CONSTANT_EMPTY;
        }
        if (codePointSet.matchesEverything()) {
            return CONSTANT_FULL;
        }
        if (codePointSet.matchesSingleAscii()) {
            return CONSTANT_ASCII[codePointSet.getLo(0)];
        }
        if (codePointSet.size() == 2) {
            CharSet ret = checkInverseAndCaseFoldAscii(codePointSet.getLo(0), codePointSet.getHi(0), codePointSet.getLo(1), codePointSet.getHi(1));
            if (ret != null) {
                return ret;
            }
        }
        for (int i = 0; i < Constants.CONSTANT_CODE_POINT_SETS.length; i++) {
            if (codePointSet.equals(Constants.CONSTANT_CODE_POINT_SETS[i])) {
                return CONSTANT_CODE_POINT_SETS_MB[i];
            }
        }
        return createTrimCodePointSet(codePointSet);
    }

    private static CharSet checkConstants(char[] ranges, int length) {
        if (length == 0) {
            return CONSTANT_EMPTY;
        }
        if (length == 1) {
            if (ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            return new CharSet(new char[]{ranges[0], ranges[0]});
        }
        if (length == 2) {
            if (ranges[0] == ranges[1] && ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            if (ranges[0] == Character.MIN_VALUE && ranges[1] == Character.MAX_VALUE) {
                return CONSTANT_FULL;
            }
        }
        if (length == 4) {
            CharSet ret = checkInverseAndCaseFoldAscii(ranges[0], ranges[1], ranges[2], ranges[3]);
            if (ret != null) {
                return ret;
            }
        }
        for (CharSet predefCC : CONSTANT_CODE_POINT_SETS_MB) {
            if (predefCC.ranges.length == length && rangesEqual(predefCC.ranges, ranges, length)) {
                return predefCC;
            }
        }
        return null;
    }

    private static boolean rangesEqual(char[] a, char[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static CharSet checkInverseAndCaseFoldAscii(int lo0, int hi0, int lo1, int hi1) {
        if (lo0 == Character.MIN_VALUE && hi1 == Character.MAX_VALUE && lo1 <= 128 && hi0 + 2 == lo1) {
            return CONSTANT_INVERSE_ASCII[hi0 + 1];
        }
        if (lo0 == hi0 && lo0 >= 'A' && lo0 <= 'Z' && lo1 == hi1 && lo1 == Character.toLowerCase(lo0)) {
            return CONSTANT_CASE_FOLD_ASCII[lo0 - 'A'];
        }
        return null;
    }

    private static CharSet createTrimCodePointSet(SortedListOfRanges codePointSet) {
        int size = 0;
        for (int i = 0; i < codePointSet.size(); i++) {
            if (codePointSet.intersects(i, Constants.BMP_RANGE.getLo(0), Constants.BMP_RANGE.getHi(0))) {
                size++;
            }
        }
        char[] ranges = new char[size * 2];
        for (int i = 0; i < codePointSet.size(); i++) {
            if (codePointSet.intersects(i, Constants.BMP_RANGE.getLo(0), Constants.BMP_RANGE.getHi(0))) {
                setRange(ranges, i, codePointSet.getLo(i), Math.min(codePointSet.getHi(i), Constants.BMP_RANGE.getHi(0)));
            }
        }
        return new CharSet(ranges);
    }

    private static void setRange(char[] arr, int i, int lo, int hi) {
        arr[i * 2] = (char) lo;
        arr[i * 2 + 1] = (char) hi;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CharSet createEmpty() {
        return getEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CharSet createFull() {
        return getFull();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CharSet create(RangesBuffer buffer) {
        assert buffer instanceof CharRangesBuffer;
        CharRangesBuffer buf = (CharRangesBuffer) buffer;
        CharSet constant = checkConstants(buf.getBuffer(), buf.length());
        if (constant == null) {
            return new CharSet(buf.toArray());
        }
        return constant;
    }

    @Override
    public int getMinValue() {
        return Character.MIN_VALUE;
    }

    @Override
    public int getMaxValue() {
        return Character.MAX_VALUE;
    }

    @Override
    public int getLo(int i) {
        return ranges[i * 2];
    }

    @Override
    public int getHi(int i) {
        return ranges[(i * 2) + 1];
    }

    @Override
    public int size() {
        return ranges.length / 2;
    }

    @Override
    public CharRangesBuffer getBuffer1(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getCharRangesBuffer1();
    }

    @Override
    public CharRangesBuffer getBuffer2(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getCharRangesBuffer2();
    }

    @Override
    public CharRangesBuffer getBuffer3(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getCharRangesBuffer3();
    }

    @Override
    public CharRangesBuffer createTempBuffer() {
        return new CharRangesBuffer();
    }

    @Override
    public void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex) {
        assert buffer instanceof CharRangesBuffer;
        int bulkLength = (endIndex - startIndex) * 2;
        if (bulkLength == 0) {
            return;
        }
        CharRangesBuffer buf = (CharRangesBuffer) buffer;
        int newSize = buf.length() + bulkLength;
        buf.ensureCapacity(newSize);
        assert buf.isEmpty() || rightOf(startIndex, buf, buf.size() - 1);
        System.arraycopy(ranges, startIndex * 2, buf.getBuffer(), buf.length(), bulkLength);
        buf.setLength(newSize);
    }

    @Override
    public boolean equalsBuffer(RangesBuffer buffer) {
        assert buffer instanceof CharRangesBuffer;
        CharRangesBuffer buf = (CharRangesBuffer) buffer;
        return ranges.length == buf.length() && rangesEqual(ranges, buf.getBuffer(), ranges.length);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CharSet createInverse() {
        return createInverse(this);
    }

    public static CharSet createInverse(SortedListOfRanges src) {
        assert src.getMinValue() == Character.MIN_VALUE;
        assert src.getMaxValue() == Character.MAX_VALUE;
        if (src.matchesNothing()) {
            return getFull();
        }
        if (src.matchesSingleAscii()) {
            return CONSTANT_INVERSE_ASCII[src.getLo(0)];
        }
        char[] invRanges = new char[src.sizeOfInverse() * 2];
        int i = 0;
        if (src.getLo(0) > src.getMinValue()) {
            setRange(invRanges, i++, src.getMinValue(), src.getLo(0) - 1);
        }
        for (int ia = 1; ia < src.size(); ia++) {
            setRange(invRanges, i++, src.getHi(ia - 1) + 1, src.getLo(ia) - 1);
        }
        if (src.getHi(src.size() - 1) < src.getMaxValue()) {
            setRange(invRanges, i++, src.getHi(src.size() - 1) + 1, src.getMaxValue());
        }
        return new CharSet(invRanges);
    }

    private static int highByte(int c) {
        return c >> Byte.SIZE;
    }

    private static int lowByte(int c) {
        return c & 0xff;
    }

    private boolean allSameHighByte() {
        if (matchesNothing()) {
            return true;
        }
        int highByte = highByte(getLo(0));
        for (int i = 0; i < size(); i++) {
            if (highByte(getLo(i)) != highByte || highByte(getHi(i)) != highByte) {
                return false;
            }
        }
        return true;
    }

    public CharMatcher createMatcher(CompilationBuffer compilationBuffer) {
        if (sizeOfInverse() < size() || (size() > 1 && !allSameHighByte() && highByte(getHi(0) + 1) == highByte(getLo(size() - 1) - 1))) {
            return createInverse().createMatcher(compilationBuffer, true, true);
        }
        return createMatcher(compilationBuffer, false, true);
    }

    private CharMatcher createMatcher(CompilationBuffer compilationBuffer, boolean inverse, boolean tryHybrid) {
        if (matchesNothing()) {
            return EmptyMatcher.create(inverse);
        }
        if (matchesEverything()) {
            return AnyMatcher.create(inverse);
        }
        if (size() == 1) {
            if (isSingle(0)) {
                return SingleCharMatcher.create(inverse, (char) getLo(0));
            }
            if (size(0) == 1) {
                // two equality checks are cheaper than one range check
                return TwoCharMatcher.create(inverse, (char) getLo(0), (char) getHi(0));
            }
            return SingleRangeMatcher.create(inverse, (char) getLo(0), (char) getHi(0));
        }
        if (size() == 2 && isSingle(0) && isSingle(1)) {
            return TwoCharMatcher.create(inverse, (char) getLo(0), (char) getLo(1));
        }
        if (preferRangeListMatcherOverBitSetMatcher()) {
            return RangeListMatcher.create(inverse, ranges);
        }
        if (allSameHighByte()) {
            CompilationFinalBitSet bs = convertToBitSet(0, size());
            int highByte = highByte(getLo(0));
            return BitSetMatcher.create(inverse, highByte, bs);
        }
        CharMatcher charMatcher;
        if (size() > 100) {
            charMatcher = MultiBitSetMatcher.fromRanges(inverse, ranges);
        } else if (tryHybrid) {
            charMatcher = createHybridMatcher(compilationBuffer, inverse);
        } else {
            if (size() <= 10) {
                charMatcher = RangeListMatcher.create(inverse, ranges);
            } else {
                assert size() <= 100;
                charMatcher = RangeTreeMatcher.fromRanges(inverse, ranges);
            }
        }
        return ProfilingCharMatcher.create(createIntersection(BYTE_RANGE, compilationBuffer).createMatcher(compilationBuffer, inverse, false), charMatcher);
    }

    private boolean preferRangeListMatcherOverBitSetMatcher() {
        // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
        // also, up to four single character checks are still faster than a bit set
        return size() <= 2 || valueCount() <= 4;
    }

    private CompilationFinalBitSet convertToBitSet(int iMinArg, int iMaxArg) {
        assert iMaxArg - iMinArg > 1;
        int highByte = highByte(getLo(iMaxArg - 1));
        CompilationFinalBitSet bs;
        int iMax = iMaxArg;
        if (rangeCrossesPlanes(iMaxArg - 1)) {
            bs = new CompilationFinalBitSet(256);
            iMax--;
            bs.setRange(lowByte(getLo(iMaxArg - 1)), 0xff);
        } else {
            bs = new CompilationFinalBitSet(Integer.highestOneBit(lowByte(getHi(iMaxArg - 1))) << 1);
        }
        int iMin = iMinArg;
        if (rangeCrossesPlanes(iMinArg)) {
            assert highByte(getHi(iMinArg)) == highByte;
            iMin++;
            bs.setRange(0, lowByte(getHi(iMinArg)));
        }
        for (int i = iMin; i < iMax; i++) {
            assert highByte(getLo(i)) == highByte && highByte(getHi(i)) == highByte;
            bs.setRange(lowByte(getLo(i)), lowByte(getHi(i)));
        }
        return bs;
    }

    private CharMatcher createHybridMatcher(CompilationBuffer compilationBuffer, boolean inverse) {
        assert size() > 1;
        CharRangesBuffer rest = compilationBuffer.getCharRangesBuffer1();
        ByteArrayBuffer highBytes = compilationBuffer.getByteArrayBuffer();
        ObjectArrayBuffer bitSets = compilationBuffer.getObjectBuffer1();
        int lowestRangeOnCurPlane = 0;
        boolean lowestRangeCanBeDeleted = !rangeCrossesPlanes(0);
        int curPlane = highByte(getHi(0));
        for (int i = 1; i < size(); i++) {
            if (highByte(getLo(i)) != curPlane) {
                if (i - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
                    highBytes.add((byte) curPlane);
                    bitSets.add(convertToBitSet(lowestRangeOnCurPlane, i));
                    if (!lowestRangeCanBeDeleted) {
                        addRangeTo(rest, lowestRangeOnCurPlane);
                    }
                } else {
                    appendRangesTo(rest, lowestRangeOnCurPlane, i);
                }
                curPlane = highByte(getLo(i));
                lowestRangeOnCurPlane = i;
                lowestRangeCanBeDeleted = !rangeCrossesPlanes(i);
            }
            if (highByte(getHi(i)) != curPlane) {
                if (lowestRangeOnCurPlane != i) {
                    if ((i + 1) - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
                        highBytes.add((byte) curPlane);
                        bitSets.add(convertToBitSet(lowestRangeOnCurPlane, i + 1));
                        if (!lowestRangeCanBeDeleted) {
                            addRangeTo(rest, lowestRangeOnCurPlane);
                        }
                        lowestRangeCanBeDeleted = highByte(getHi(i)) - highByte(getLo(i)) == 1;
                    } else {
                        appendRangesTo(rest, lowestRangeOnCurPlane, i);
                        lowestRangeCanBeDeleted = !rangeCrossesPlanes(i);
                    }
                } else {
                    lowestRangeCanBeDeleted = !rangeCrossesPlanes(i);
                }
                curPlane = highByte(getHi(i));
                lowestRangeOnCurPlane = i;
            }
        }
        if (size() - lowestRangeOnCurPlane >= TRegexOptions.TRegexRangeToBitSetConversionThreshold) {
            highBytes.add((byte) curPlane);
            bitSets.add(convertToBitSet(lowestRangeOnCurPlane, size()));
            if (!lowestRangeCanBeDeleted) {
                addRangeTo(rest, lowestRangeOnCurPlane);
            }
        } else {
            appendRangesTo(rest, lowestRangeOnCurPlane, size());
        }
        if (highBytes.length() == 0) {
            assert rest.length() == ranges.length;
            return createMatcher(compilationBuffer, inverse, false);
        }
        CharMatcher restMatcher = create(rest).createMatcher(compilationBuffer, false, false);
        return HybridBitSetMatcher.create(inverse, highBytes.toArray(), bitSets.toArray(new CompilationFinalBitSet[bitSets.length()]), restMatcher);
    }

    private boolean rangeCrossesPlanes(int i) {
        return highByte(getLo(i)) != highByte(getHi(i));
    }

    public char[] inverseToCharArray() {
        char[] array = new char[inverseValueCount()];
        int index = 0;
        int lastHi = -1;
        for (int i = 0; i < size(); i++) {
            for (int j = lastHi + 1; j < getLo(i); j++) {
                array[index++] = (char) j;
            }
            lastHi = getHi(i);
        }
        for (int j = lastHi + 1; j <= getMaxValue(); j++) {
            array[index++] = (char) j;
        }
        return array;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CharSet) {
            return Arrays.equals(ranges, ((CharSet) obj).ranges);
        }
        if (obj instanceof SortedListOfRanges) {
            return equals((SortedListOfRanges) obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges);
    }

    @Override
    public int compareTo(CharSet o) {
        if (this == o) {
            return 0;
        }
        if (matchesEverything()) {
            if (o.matchesEverything()) {
                return 0;
            }
            return 1;
        }
        if (matchesNothing()) {
            if (o.matchesNothing()) {
                return 0;
            }
            return -1;
        }
        if (o.matchesEverything()) {
            return -1;
        }
        if (o.matchesNothing()) {
            return 1;
        }
        int cmp = size() - o.size();
        if (cmp != 0) {
            return cmp;
        }
        for (int i = 0; i < size(); i++) {
            cmp = getLo(i) - o.getLo(i);
            if (cmp != 0) {
                return cmp;
            }
        }
        return cmp;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(ranges);
    }
}
