/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.RangesArrayBuffer;
import com.oracle.truffle.regex.chardata.CodePointRange;
import com.oracle.truffle.regex.chardata.CodePointSet;
import com.oracle.truffle.regex.chardata.Constants;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Arrays;
import java.util.List;

public final class MatcherBuilder implements Comparable<MatcherBuilder>, JsonConvertible {

    private static final MatcherBuilder CONSTANT_EMPTY = new MatcherBuilder(new char[0]);
    private static final MatcherBuilder CONSTANT_FULL = new MatcherBuilder(new char[]{Character.MIN_VALUE, Character.MAX_VALUE});

    private static final MatcherBuilder[] CONSTANT_ASCII = new MatcherBuilder[128];
    private static final MatcherBuilder[] CONSTANT_INVERSE_ASCII = new MatcherBuilder[128];
    private static final MatcherBuilder[] CONSTANT_CASE_FOLD_ASCII = new MatcherBuilder[26];

    private static final CodePointSet[] CONSTANT_CODE_POINT_SETS = new CodePointSet[]{
                    Constants.WORD_CHARS,
                    Constants.NON_WORD_CHARS,
                    Constants.WHITE_SPACE,
                    Constants.NON_WHITE_SPACE,
                    Constants.DIGITS,
                    Constants.NON_DIGITS,
                    Constants.LINE_TERMINATOR,
                    Constants.DOT,
                    Constants.HEX_CHARS
    };
    private static final MatcherBuilder[] CONSTANT_CODE_POINT_SETS_MB;

    private static final MatcherBuilder CONSTANT_TRAIL_SURROGATE_RANGE = new MatcherBuilder(new char[]{(char) Constants.TRAIL_SURROGATE_RANGE.lo, (char) Constants.TRAIL_SURROGATE_RANGE.hi});

    static {
        CONSTANT_ASCII[0] = new MatcherBuilder(new char[]{0, 0});
        CONSTANT_INVERSE_ASCII[0] = new MatcherBuilder(new char[]{1, Character.MAX_VALUE});
        for (char i = 1; i < 128; i++) {
            CONSTANT_ASCII[i] = new MatcherBuilder(new char[]{i, i});
            CONSTANT_INVERSE_ASCII[i] = new MatcherBuilder(new char[]{0, (char) (i - 1), (char) (i + 1), Character.MAX_VALUE});
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            CONSTANT_CASE_FOLD_ASCII[i - 'A'] = new MatcherBuilder(new char[]{i, i, Character.toLowerCase(i), Character.toLowerCase(i)});
        }
        CONSTANT_CODE_POINT_SETS_MB = new MatcherBuilder[CONSTANT_CODE_POINT_SETS.length];
        for (int i = 0; i < CONSTANT_CODE_POINT_SETS.length; i++) {
            CONSTANT_CODE_POINT_SETS_MB[i] = createTrimCodePointSet(CONSTANT_CODE_POINT_SETS[i]);
        }
    }

    private final char[] ranges;

    private MatcherBuilder(char[] ranges) {
        this.ranges = ranges;
        assert (ranges.length & 1) == 0 : "ranges array must have an even length!";
        assert rangesAreSortedAndDisjoint() : rangesToString(ranges, true);
    }

    public char[] getRanges() {
        return ranges;
    }

    public static MatcherBuilder createEmpty() {
        return CONSTANT_EMPTY;
    }

    public static MatcherBuilder createFull() {
        return CONSTANT_FULL;
    }

    public static MatcherBuilder createTrailSurrogateRange() {
        return CONSTANT_TRAIL_SURROGATE_RANGE;
    }

    public static MatcherBuilder create(char... ranges) {
        MatcherBuilder constant = checkConstants(ranges, ranges.length);
        if (constant == null) {
            return new MatcherBuilder(ranges);
        }
        return constant;
    }

    public static MatcherBuilder create(RangesArrayBuffer rangesArrayBuffer) {
        MatcherBuilder constant = checkConstants(rangesArrayBuffer.getBuffer(), rangesArrayBuffer.size());
        if (constant == null) {
            return new MatcherBuilder(rangesArrayBuffer.toArray());
        }
        return constant;
    }

    public static MatcherBuilder create(CodePointSet codePointSet) {
        if (codePointSet.matchesNothing()) {
            return CONSTANT_EMPTY;
        }
        if (codePointSet.matchesEverything()) {
            return CONSTANT_FULL;
        }
        final List<CodePointRange> codePointRanges = codePointSet.getRanges();
        if (codePointSet.matchesSingleAscii()) {
            return CONSTANT_ASCII[codePointRanges.get(0).lo];
        }
        if (codePointRanges.size() == 2) {
            MatcherBuilder ret = checkInverseAndCaseFoldAscii(codePointRanges.get(0).lo, codePointRanges.get(0).hi, codePointRanges.get(1).lo, codePointRanges.get(1).hi);
            if (ret != null) {
                return ret;
            }
        }
        for (int i = 0; i < CONSTANT_CODE_POINT_SETS.length; i++) {
            if (codePointSet.equals(CONSTANT_CODE_POINT_SETS[i])) {
                return CONSTANT_CODE_POINT_SETS_MB[i];
            }
        }
        return createTrimCodePointSet(codePointSet);
    }

    private static MatcherBuilder checkConstants(char[] ranges, int length) {
        if (length == 0) {
            return CONSTANT_EMPTY;
        }
        if (length == 1) {
            if (ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            return new MatcherBuilder(new char[]{ranges[0], ranges[0]});
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
            MatcherBuilder ret = checkInverseAndCaseFoldAscii(ranges[0], ranges[1], ranges[2], ranges[3]);
            if (ret != null) {
                return ret;
            }
        }
        for (MatcherBuilder predefCC : CONSTANT_CODE_POINT_SETS_MB) {
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

    private static MatcherBuilder checkInverseAndCaseFoldAscii(int lo0, int hi0, int lo1, int hi1) {
        if (lo0 == Character.MIN_VALUE && hi1 == Character.MAX_VALUE && lo1 <= 128 && hi0 + 2 == lo1) {
            return CONSTANT_INVERSE_ASCII[hi0 + 1];
        }
        if (lo0 == hi0 && lo0 >= 'A' && lo0 <= 'Z' && lo1 == hi1 && lo1 == Character.toLowerCase(lo0)) {
            return CONSTANT_CASE_FOLD_ASCII[lo0 - 'A'];
        }
        return null;
    }

    private static MatcherBuilder createTrimCodePointSet(CodePointSet codePointSet) {
        int size = 0;
        for (CodePointRange range : codePointSet.getRanges()) {
            if (range.intersects(Constants.BMP_RANGE)) {
                size++;
            }
        }
        char[] ranges = new char[size * 2];
        int i = 0;
        for (CodePointRange range : codePointSet.getRanges()) {
            if (range.intersects(Constants.BMP_RANGE)) {
                ranges[i++] = (char) range.lo;
                ranges[i++] = (char) Math.min(range.hi, Constants.BMP_RANGE.hi);
            }
        }
        return new MatcherBuilder(ranges);
    }

    private boolean isSingle(int ia) {
        return getLo(ia) == getHi(ia);
    }

    private int size(int ia) {
        return getHi(ia) - getLo(ia);
    }

    private static boolean contains(char aLo, char aHi, char bLo, char bHi) {
        return aLo <= bLo && aHi >= bHi;
    }

    private boolean contains(int ia, MatcherBuilder o, int ib) {
        return contains(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    private boolean contains(int ia, char bLo, char bHi) {
        return contains(getLo(ia), getHi(ia), bLo, bHi);
    }

    private boolean containedBy(int ia, char bLo, char bHi) {
        return contains(bLo, bHi, getLo(ia), getHi(ia));
    }

    private static boolean intersects(char aLo, char aHi, char bLo, char bHi) {
        return aLo <= bHi && bLo <= aHi;
    }

    private boolean intersects(int ia, MatcherBuilder o, int ib) {
        return intersects(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    private boolean intersects(int ia, char bLo, char bHi) {
        return intersects(getLo(ia), getHi(ia), bLo, bHi);
    }

    @SuppressWarnings("unused")
    private static boolean leftOf(char aLo, char aHi, char bLo, char bHi) {
        return aHi < bLo;
    }

    private static boolean leftOf(char aHi, char bLo) {
        return aHi < bLo;
    }

    private boolean leftOf(int ia, MatcherBuilder o, int ib) {
        return leftOf(getHi(ia), o.getLo(ib));
    }

    @SuppressWarnings("unused")
    private boolean leftOf(int ia, char oLo, char oHi) {
        return leftOf(getHi(ia), oLo);
    }

    @SuppressWarnings("unused")
    private static boolean rightOf(char aLo, char aHi, char bLo, char bHi) {
        return aLo > bHi;
    }

    private boolean rightOf(int ia, MatcherBuilder o, int ib) {
        return rightOf(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    private boolean rightOf(int ia, char bLo, char bHi) {
        return rightOf(getLo(ia), getHi(ia), bLo, bHi);
    }

    private static boolean adjacent(char aLo, char aHi, char bLo, char bHi) {
        return aHi + 1 == bLo || aLo - 1 == bHi;
    }

    private boolean adjacent(int ia, MatcherBuilder o, int ib) {
        return adjacent(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    private boolean adjacent(int ia, char bLo, char bHi) {
        return adjacent(getLo(ia), getHi(ia), bLo, bHi);
    }

    private boolean equal(int ia, MatcherBuilder o, int ib) {
        return getLo(ia) == o.getLo(ib) && getHi(ia) == o.getHi(ib);
    }

    private void intersect(int ia, MatcherBuilder o, int ib, RangesArrayBuffer result) {
        assert intersects(ia, o, ib);
        result.addRange(Math.max(getLo(ia), o.getLo(ib)), Math.min(getHi(ia), o.getHi(ib)));
    }

    private int binarySearch(char key) {
        int low = 0;
        int high = size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            char midVal = getLo(mid);
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1);  // key not found.
    }

    private boolean binarySearchExactMatch(int ia, MatcherBuilder o, int searchResult) {
        return searchResult >= 0 && equal(ia, o, searchResult);
    }

    private int binarySearchGetFirstIntersecting(int ia, MatcherBuilder o, int searchResult) {
        assert o.rangesAreSortedAndDisjoint();
        if (searchResult >= 0) {
            assert !equal(ia, o, searchResult);
            return searchResult;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        if (insertionPoint > 0 && intersects(ia, o, insertionPoint - 1)) {
            return insertionPoint - 1;
        }
        return insertionPoint;
    }

    private boolean binarySearchNoIntersectingFound(int firstIntersecting) {
        return firstIntersecting == size();
    }

    private void addRangeTo(RangesArrayBuffer rangesArrayBuffer, int i) {
        rangesArrayBuffer.addRange(getLo(i), getHi(i));
    }

    private void addRangeBulkTo(RangesArrayBuffer rangesArrayBuffer, int startIndex, int endIndex) {
        int bulkLength = (endIndex - startIndex) * 2;
        if (bulkLength == 0) {
            return;
        }
        int newSize = rangesArrayBuffer.size() + bulkLength;
        rangesArrayBuffer.ensureCapacity(newSize);
        System.arraycopy(ranges, startIndex * 2, rangesArrayBuffer.getBuffer(), rangesArrayBuffer.size(), bulkLength);
        rangesArrayBuffer.setSize(newSize);
    }

    private boolean rangesAreSortedAndDisjoint() {
        for (int i = 1; i < size(); i++) {
            if ((!leftOf(i - 1, this, i)) || intersects(i - 1, this, i)) {
                return false;
            }
        }
        return true;
    }

    public char getLo(int i) {
        return ranges[i * 2];
    }

    public char getHi(int i) {
        return ranges[(i * 2) + 1];
    }

    public int size() {
        return ranges.length / 2;
    }

    public boolean contains(MatcherBuilder o) {
        assert !matchesNothing() && !o.matchesNothing();
        int ia = 0;
        int ib = 0;
        while (true) {
            while (leftOf(ia, o, ib)) {
                ia++;
                if (ia >= size()) {
                    return false;
                }
            }
            while (contains(ia, o, ib)) {
                ib++;
                if (ib >= o.size()) {
                    return true;
                }
            }
            if (o.leftOf(ib, this, ia) || intersects(ia, o, ib)) {
                return false;
            }
        }
    }

    public MatcherBuilder createIntersectionMatcher(MatcherBuilder o, CompilationBuffer compilationBuffer) {
        RangesArrayBuffer intersectionRanges = compilationBuffer.getRangesArrayBuffer1();
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (binarySearchExactMatch(ia, o, search)) {
                addRangeTo(intersectionRanges, ia);
                continue;
            }
            int firstIntersection = binarySearchGetFirstIntersecting(ia, o, search);
            for (int ib = firstIntersection; ib < o.size(); ib++) {
                if (o.rightOf(ib, this, ia)) {
                    break;
                }
                intersect(ia, o, ib, intersectionRanges);
            }
        }
        if (equalsRangesArrayBuffer(intersectionRanges)) {
            return this;
        }
        if (o.equalsRangesArrayBuffer(intersectionRanges)) {
            return o;
        }
        return MatcherBuilder.create(intersectionRanges);
    }

    public MatcherBuilder createInverse(CompilationBuffer compilationBuffer) {
        RangesArrayBuffer invRanges = compilationBuffer.getRangesArrayBuffer1();
        if (matchesNothing()) {
            return createFull();
        }
        if (getLo(0) > Character.MIN_VALUE) {
            invRanges.addRange(Character.MIN_VALUE, getLo(0) - 1);
        }
        for (int ia = 1; ia < size(); ia++) {
            invRanges.addRange(getHi(ia - 1) + 1, getLo(ia) - 1);
        }
        if (getHi(size() - 1) < Character.MAX_VALUE) {
            invRanges.addRange(getHi(size() - 1) + 1, Character.MAX_VALUE);
        }
        return MatcherBuilder.create(invRanges);
    }

    public MatcherBuilder subtract(MatcherBuilder o, CompilationBuffer compilationBuffer) {
        RangesArrayBuffer subtractionRanges = compilationBuffer.getRangesArrayBuffer1();
        char tmpLo;
        char tmpHi;
        boolean unchanged = true;
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (binarySearchExactMatch(ia, o, search)) {
                unchanged = false;
                continue;
            }
            int firstIntersection = binarySearchGetFirstIntersecting(ia, o, search);
            if (o.binarySearchNoIntersectingFound(firstIntersection)) {
                addRangeTo(subtractionRanges, ia);
                continue;
            }
            unchanged = false;
            tmpLo = getLo(ia);
            tmpHi = getHi(ia);
            boolean rest = true;
            for (int ib = firstIntersection; ib < o.size(); ib++) {
                if (o.rightOf(ib, tmpLo, tmpHi)) {
                    break;
                }
                if (o.intersects(ib, tmpLo, tmpHi)) {
                    if (o.contains(ib, tmpLo, tmpHi)) {
                        rest = false;
                        break;
                    } else if (o.containedBy(ib, tmpLo, tmpHi) && tmpLo != o.getLo(ib) && tmpHi != o.getHi(ib)) {
                        subtractionRanges.addRange(tmpLo, o.getLo(ib) - 1);
                        tmpLo = (char) (o.getHi(ib) + 1);
                    } else if (tmpLo < o.getLo(ib)) {
                        tmpHi = (char) (o.getLo(ib) - 1);
                    } else {
                        tmpLo = (char) (o.getHi(ib) + 1);
                    }
                }
            }
            if (rest) {
                subtractionRanges.addRange(tmpLo, tmpHi);
            }
        }
        if (unchanged) {
            assert equalsRangesArrayBuffer(subtractionRanges);
            return this;
        }
        return MatcherBuilder.create(subtractionRanges);
    }

    /**
     * Calculates the intersection and the "rest" of this and another {@link MatcherBuilder}.
     *
     * @param o MatcherBuilder to intersect with.
     * @param result Array of results, where index 0 is equal to this.subtract(intersection), index
     *            1 is equal to o.subtract(intersection) and index 2 is equal to
     *            this.createIntersection(o).
     */
    public void intersectAndSubtract(MatcherBuilder o, CompilationBuffer compilationBuffer, MatcherBuilder[] result) {
        if (matchesNothing() || o.matchesNothing()) {
            result[0] = this;
            result[1] = o;
            result[2] = createEmpty();
            return;
        }
        RangesArrayBuffer subtractedA = compilationBuffer.getRangesArrayBuffer1();
        RangesArrayBuffer subtractedB = compilationBuffer.getRangesArrayBuffer2();
        RangesArrayBuffer intersectionRanges = compilationBuffer.getRangesArrayBuffer3();
        int ia = 0;
        int ib = 0;
        boolean noIntersection = false;
        while (true) {
            if (leftOf(ia, o, ib)) {
                ia++;
                if (ia >= size()) {
                    noIntersection = true;
                    break;
                }
                continue;
            }
            if (o.leftOf(ib, this, ia)) {
                ib++;
                if (ib >= o.size()) {
                    noIntersection = true;
                    break;
                }
                continue;
            }
            break;
        }
        if (noIntersection) {
            result[0] = this;
            result[1] = o;
            result[2] = createEmpty();
            return;
        }
        addRangeBulkTo(subtractedA, 0, ia);
        o.addRangeBulkTo(subtractedB, 0, ib);
        char raLo = getLo(ia);
        char raHi = getHi(ia);
        char rbLo = o.getLo(ib);
        char rbHi = o.getHi(ib);
        assert intersects(raLo, raHi, rbLo, rbHi);
        ia++;
        ib++;
        boolean advanceA = false;
        boolean advanceB = false;
        boolean finish = false;
        while (true) {
            if (advanceA) {
                advanceA = false;
                if (ia < size()) {
                    raLo = getLo(ia);
                    raHi = getHi(ia);
                    ia++;
                } else {
                    if (!advanceB) {
                        subtractedB.addRange(rbLo, rbHi);
                    }
                    o.addRangeBulkTo(subtractedB, ib, o.size());
                    finish = true;
                }
            }
            if (advanceB) {
                advanceB = false;
                if (ib < o.size()) {
                    rbLo = o.getLo(ib);
                    rbHi = o.getHi(ib);
                    ib++;
                } else {
                    if (!finish) {
                        subtractedA.addRange(raLo, raHi);
                    }
                    addRangeBulkTo(subtractedA, ia, size());
                    finish = true;
                }
            }
            if (finish) {
                break;
            }
            if (leftOf(raLo, raHi, rbLo, rbHi)) {
                subtractedA.addRange(raLo, raHi);
                advanceA = true;
                continue;
            }
            if (leftOf(rbLo, rbHi, raLo, raHi)) {
                subtractedB.addRange(rbLo, rbHi);
                advanceB = true;
                continue;
            }
            assert intersects(raLo, raHi, rbLo, rbHi);
            char intersectionLo = (char) Math.max(raLo, rbLo);
            char intersectionHi = (char) Math.min(raHi, rbHi);
            intersectionRanges.addRange(intersectionLo, intersectionHi);
            if (raLo < intersectionLo) {
                subtractedA.addRange(raLo, intersectionLo - 1);
            }
            if (raHi > intersectionHi) {
                raLo = (char) (intersectionHi + 1);
            } else {
                advanceA = true;
            }
            if (rbLo < intersectionLo) {
                subtractedB.addRange(rbLo, intersectionLo - 1);
            }
            if (rbHi > intersectionHi) {
                rbLo = (char) (intersectionHi + 1);
            } else {
                advanceB = true;
            }
        }
        result[0] = MatcherBuilder.create(subtractedA);
        result[1] = MatcherBuilder.create(subtractedB);
        if (subtractedA.isEmpty()) {
            assert equalsRangesArrayBuffer(intersectionRanges);
            result[2] = this;
        } else if (subtractedB.isEmpty()) {
            assert o.equalsRangesArrayBuffer(intersectionRanges);
            result[2] = o;
        } else {
            result[2] = MatcherBuilder.create(intersectionRanges);
        }
    }

    public MatcherBuilder union(MatcherBuilder o) {
        return union(o, new RangesArrayBuffer());
    }

    public MatcherBuilder union(MatcherBuilder o, CompilationBuffer compilationBuffer) {
        return union(o, compilationBuffer.getRangesArrayBuffer1());
    }

    public MatcherBuilder union(MatcherBuilder o, RangesArrayBuffer unionRanges) {
        if (matchesNothing() || o.matchesEverything()) {
            return o;
        }
        if (matchesEverything() || o.matchesNothing()) {
            return this;
        }
        char tmpLo;
        char tmpHi;
        int ia = 0;
        int ib = 0;
        outer: while (ia < size() && ib < o.size()) {
            while (leftOf(ia, o, ib) && !adjacent(ia, o, ib)) {
                addRangeTo(unionRanges, ia);
                ia++;
                if (ia == size()) {
                    break outer;
                }
            }
            while (o.leftOf(ib, this, ia) && !adjacent(ia, o, ib)) {
                o.addRangeTo(unionRanges, ib);
                ib++;
                if (ib == o.size()) {
                    break outer;
                }
            }
            if (intersects(ia, o, ib) || adjacent(ia, o, ib)) {
                tmpLo = (char) Math.min(getLo(ia), o.getLo(ib));
                tmpHi = (char) Math.max(getHi(ia), o.getHi(ib));
                ia++;
                ib++;
                while (true) {
                    if (ia < size() && (intersects(ia, tmpLo, tmpHi) || adjacent(ia, tmpLo, tmpHi))) {
                        tmpLo = (char) Math.min(getLo(ia), tmpLo);
                        tmpHi = (char) Math.max(getHi(ia), tmpHi);
                        ia++;
                    } else if (ib < o.size() && (o.intersects(ib, tmpLo, tmpHi) || o.adjacent(ib, tmpLo, tmpHi))) {
                        tmpLo = (char) Math.min(o.getLo(ib), tmpLo);
                        tmpHi = (char) Math.max(o.getHi(ib), tmpHi);
                        ib++;
                    } else {
                        break;
                    }
                }
                unionRanges.addRange(tmpLo, tmpHi);
            } else {
                if (rightOf(ia, o, ib)) {
                    o.addRangeTo(unionRanges, ib);
                    ib++;
                } else {
                    assert o.rightOf(ib, this, ia);
                    addRangeTo(unionRanges, ia);
                    ia++;
                }
            }
        }
        if (ia < size()) {
            addRangeBulkTo(unionRanges, ia, size());
        }
        if (ib < o.size()) {
            o.addRangeBulkTo(unionRanges, ib, o.size());
        }
        if (equalsRangesArrayBuffer(unionRanges)) {
            return this;
        }
        if (o.equalsRangesArrayBuffer(unionRanges)) {
            return o;
        }
        return MatcherBuilder.create(unionRanges);
    }

    public boolean matchesNothing() {
        return size() == 0;
    }

    public boolean matchesSomething() {
        return !matchesNothing();
    }

    public boolean matchesSingleChar() {
        return size() == 1 && isSingle(0);
    }

    public int charCount() {
        int charSize = 0;
        for (int i = 0; i < size(); i++) {
            charSize += (getHi(i) - getLo(i)) + 1;
        }
        return charSize;
    }

    public int inverseCharCount() {
        return Character.MAX_VALUE + 1 - charCount();
    }

    public char[] inverseToCharArray() {
        char[] array = new char[inverseCharCount()];
        int index = 0;
        int lastHi = -1;
        for (int i = 0; i < size(); i++) {
            for (int j = lastHi + 1; j < getLo(i); j++) {
                array[index++] = (char) j;
            }
            lastHi = getHi(i);
        }
        for (int j = lastHi + 1; j <= Character.MAX_VALUE; j++) {
            array[index++] = (char) j;
        }
        return array;
    }

    public boolean matchesEverything() {
        // ranges should be consolidated to one
        return size() == 1 && getLo(0) == Character.MIN_VALUE && getHi(0) == Character.MAX_VALUE;
    }

    private static int highByte(char c) {
        return c >> Byte.SIZE;
    }

    private static int lowByte(char c) {
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
        MatcherBuilder inverse = createInverse(compilationBuffer);
        if (inverse.size() < size() || !allSameHighByte() && inverse.allSameHighByte()) {
            return inverse.createMatcher(compilationBuffer, true, true);
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
                return new SingleCharMatcher(inverse, getLo(0));
            }
            if (size(0) == 1) {
                // two equality checks are cheaper than one range check
                return new TwoCharMatcher(inverse, getLo(0), getHi(0));
            }
            return new SingleRangeMatcher(inverse, getLo(0), getHi(0));
        }
        if (size() == 2 && isSingle(0) && isSingle(1)) {
            return new TwoCharMatcher(inverse, getLo(0), getLo(1));
        }
        if (preferRangeListMatcherOverBitSetMatcher()) {
            return new RangeListMatcher(inverse, ranges);
        }
        if (allSameHighByte()) {
            CompilationFinalBitSet bs = convertToBitSet(0, size());
            int highByte = highByte(getLo(0));
            return BitSetMatcher.create(inverse, highByte, bs);
        }
        if (size() > 100) {
            return MultiBitSetMatcher.fromRanges(inverse, ranges);
        }
        if (tryHybrid) {
            return createHybridMatcher(compilationBuffer, inverse);
        } else {
            if (size() <= 10) {
                return new RangeListMatcher(inverse, ranges);
            } else {
                assert size() <= 100;
                return RangeTreeMatcher.fromRanges(inverse, ranges);
            }
        }
    }

    private boolean preferRangeListMatcherOverBitSetMatcher() {
        if (size() <= 2) {
            // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
            return true;
        }
        if (size() <= 4) {
            // up to four single character checks are still faster than a bit set
            for (int i = 0; i < size(); i++) {
                if (!isSingle(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
        RangesArrayBuffer rest = compilationBuffer.getRangesArrayBuffer1();
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
                    addRangeBulkTo(rest, lowestRangeOnCurPlane, i);
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
                        addRangeBulkTo(rest, lowestRangeOnCurPlane, i);
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
            addRangeBulkTo(rest, lowestRangeOnCurPlane, size());
        }
        if (highBytes.size() == 0) {
            assert rest.size() == ranges.length;
            return createMatcher(compilationBuffer, inverse, false);
        }
        CharMatcher restMatcher = MatcherBuilder.create(rest).createMatcher(compilationBuffer, false, false);
        return new HybridBitSetMatcher(inverse, highBytes.toArray(), bitSets.toArray(new CompilationFinalBitSet[bitSets.size()]), restMatcher);
    }

    private boolean rangeCrossesPlanes(int i) {
        return highByte(getLo(i)) != highByte(getHi(i));
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return toString(true);
    }

    @TruffleBoundary
    private String toString(boolean addBrackets) {
        if (equalsCodePointSet(Constants.DOT)) {
            return ".";
        }
        if (equalsCodePointSet(Constants.LINE_TERMINATOR)) {
            return "[\\r\\n\\u2028\\u2029]";
        }
        if (equalsCodePointSet(Constants.DIGITS)) {
            return "\\d";
        }
        if (equalsCodePointSet(Constants.NON_DIGITS)) {
            return "\\D";
        }
        if (equalsCodePointSet(Constants.WORD_CHARS)) {
            return "\\w";
        }
        if (equalsCodePointSet(Constants.NON_WORD_CHARS)) {
            return "\\W";
        }
        if (equalsCodePointSet(Constants.WHITE_SPACE)) {
            return "\\s";
        }
        if (equalsCodePointSet(Constants.NON_WHITE_SPACE)) {
            return "\\S";
        }
        if (matchesEverything()) {
            return "[\\s\\S]";
        }
        if (matchesNothing()) {
            return "[]";
        }
        if (matchesSingleChar()) {
            return rangeToString(ranges[0], ranges[1]);
        }
        MatcherBuilder inverse = createInverse(new CompilationBuffer());
        if (inverse.size() < size()) {
            return "[^" + inverse.toString(false) + "]";
        }
        if (addBrackets) {
            return "[" + rangesToString(ranges) + "]";
        } else {
            return rangesToString(ranges);
        }
    }

    @TruffleBoundary
    public static String rangeToString(char lo, char hi) {
        if (lo == hi) {
            return DebugUtil.charToString(lo);
        }
        return DebugUtil.charToString(lo) + "-" + DebugUtil.charToString(hi);
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
                sb.append(rangeToString(ranges[i], ranges[i + 1]));
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MatcherBuilder && Arrays.equals(ranges, ((MatcherBuilder) obj).ranges);
    }

    private boolean equalsCodePointSet(CodePointSet other) {
        List<CodePointRange> otherRanges = other.getRanges();
        if (size() != otherRanges.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (getLo(i) != otherRanges.get(i).lo || getHi(i) != otherRanges.get(i).hi) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsRangesArrayBuffer(RangesArrayBuffer buf) {
        return ranges.length == buf.size() && rangesEqual(ranges, buf.getBuffer(), ranges.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges);
    }

    @Override
    public int compareTo(MatcherBuilder o) {
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
