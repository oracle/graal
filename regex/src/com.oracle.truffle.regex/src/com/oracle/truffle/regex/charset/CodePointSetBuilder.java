/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.chardata.CharacterSet;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class CodePointSetBuilder implements CharacterSet, JsonConvertible {

    private List<CodePointRange> ranges;
    private boolean normalized;
    private boolean frozen;

    private CodePointSetBuilder(List<CodePointRange> ranges, boolean normalized) {
        this.ranges = ranges;
        this.normalized = normalized;
        this.frozen = false;
    }

    public List<CodePointRange> getRanges() {
        normalize();
        return Collections.unmodifiableList(ranges);
    }

    public static CodePointSetBuilder createEmpty() {
        return new CodePointSetBuilder(new ArrayList<>(), true);
    }

    public static CodePointSetBuilder create(CodePointRange... ranges) {
        List<CodePointRange> list = new ArrayList<>();
        Collections.addAll(list, ranges);
        return new CodePointSetBuilder(list, rangesAreSortedAndDisjoint(list));
    }

    public static CodePointSetBuilder create(int... codePoints) {
        List<CodePointRange> list = new ArrayList<>();
        for (int c : codePoints) {
            list.add(new CodePointRange(c));
        }
        return new CodePointSetBuilder(list, rangesAreSortedAndDisjoint(list));
    }

    public static CodePointSetBuilder create(CodePointSet codePointSet) {
        List<CodePointRange> list = new ArrayList<>();
        for (int i = 0; i < codePointSet.size(); i++) {
            list.add(new CodePointRange(codePointSet.getLo(i), codePointSet.getHi(i)));
        }
        assert rangesAreSortedAndDisjoint(list);
        return new CodePointSetBuilder(list, true);
    }

    public CodePointSetBuilder addRange(CodePointRange range) {
        if (frozen) {
            throw new UnsupportedOperationException("Object marked as immutable");
        }
        ranges.add(range);
        normalized = false;
        return this;
    }

    public CodePointSetBuilder addSet(CodePointSetBuilder other) {
        if (frozen) {
            throw new UnsupportedOperationException("Object marked as immutable");
        }
        ranges.addAll(other.getRanges());
        normalized = false;
        return this;
    }

    public CodePointSetBuilder copy() {
        return new CodePointSetBuilder(new ArrayList<>(ranges), normalized);
    }

    public CodePointSetBuilder createInverse() {
        normalize();

        List<CodePointRange> invRanges = new ArrayList<>();
        CodePointRange prev = null;
        for (CodePointRange r : ranges) {
            if (prev == null) {
                if (r.lo > Constants.MIN_CODEPOINT) {
                    invRanges.add(new CodePointRange(Constants.MIN_CODEPOINT, r.lo - 1));
                }
            } else {
                invRanges.add(new CodePointRange(prev.hi + 1, r.lo - 1));
            }
            prev = r;
        }
        if (prev == null) {
            invRanges.add(new CodePointRange(Constants.MIN_CODEPOINT, Constants.MAX_CODEPOINT));
        } else if (prev.hi < Constants.MAX_CODEPOINT) {
            invRanges.add(new CodePointRange(prev.hi + 1, Constants.MAX_CODEPOINT));
        }
        return new CodePointSetBuilder(invRanges, true);
    }

    public CodePointSetBuilder createIntersection(CodePointSet o) {
        normalize();

        List<CodePointRange> intersectionRanges = new ArrayList<>();
        for (CodePointRange r : ranges) {
            int search = o.binarySearch(r.lo);
            if (o.binarySearchExactMatch(search, r.lo, r.hi)) {
                intersectionRanges.add(r);
                continue;
            }
            int firstIntersection = o.binarySearchGetFirstIntersecting(search, r.lo, r.hi);
            for (int i = firstIntersection; i < o.size(); i++) {
                if (o.rightOf(i, r.lo, r.hi)) {
                    break;
                }
                assert o.intersects(i, r.lo, r.hi);
                int intersectionLo = Math.max(o.getLo(i), r.lo);
                int intersectionHi = Math.min(o.getHi(i), r.hi);
                intersectionRanges.add(new CodePointRange(intersectionLo, intersectionHi));
            }
        }
        return new CodePointSetBuilder(intersectionRanges, true);
    }

    /**
     * Normalizes the list of ranges: a normalized list of ranges is sorted, all the ranges are
     * disjoint and there are no adjacent ranges. Two normalized CodePointSets represent the same
     * set of characters if and only if their lists of ranges are equal. This method should be
     * called at the start of any method which either exposes the list of ranges or relies on the
     * list of ranges being normalized.
     */
    private void normalize() {
        if (normalized) {
            return;
        }

        Collections.sort(ranges);
        List<CodePointRange> normalizedRanges = new ArrayList<>();
        CodePointRange curRange = null;
        for (CodePointRange nextRange : ranges) {
            if (curRange == null) {
                curRange = nextRange;
            } else if (curRange.intersects(nextRange) || curRange.adjacent(nextRange)) {
                curRange = curRange.expand(nextRange);
            } else {
                normalizedRanges.add(curRange);
                curRange = nextRange;
            }
        }
        if (curRange != null) {
            normalizedRanges.add(curRange);
        }

        ranges = normalizedRanges;
        normalized = true;
    }

    /**
     * Makes this CodePointSet immutable. Any calls to {@link #addRange(CodePointRange) addRange} or
     * {@link #addSet(CodePointSetBuilder) addSet} will throw an
     * {@link UnsupportedOperationException}.
     *
     * @return this, now immutable
     */
    public CodePointSetBuilder freeze() {
        frozen = true;
        return this;
    }

    @Override
    public boolean contains(int codePoint) {
        normalize();
        int low = 0;
        int high = ranges.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            CodePointRange midRange = ranges.get(mid);
            if (codePoint < midRange.lo) {
                high = mid - 1;
            } else if (codePoint > midRange.hi) {
                low = mid + 1;
            } else { // codePoint >= midRange.lo && codePoint <= midRange.hi
                return true;
            }
        }
        return false;
    }

    public boolean matchesNothing() {
        normalize();
        return ranges.isEmpty();
    }

    public boolean matchesSomething() {
        return !matchesNothing();
    }

    public boolean matchesSingleChar() {
        normalize();
        return ranges.size() == 1 && ranges.get(0).isSingle();
    }

    public boolean matchesSingleAscii() {
        normalize();
        return matchesSingleChar() && ranges.get(0).lo < 128;
    }

    public boolean matchesEverything() {
        normalize();
        return ranges.size() == 1 && ranges.get(0).lo == Constants.MIN_CODEPOINT && ranges.get(0).hi == Constants.MAX_CODEPOINT;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        normalize();
        if (equalsCodePointSet(Constants.DOT)) {
            return ".";
        }
        if (equalsCodePointSet(Constants.LINE_TERMINATOR)) {
            return "[\\r\\n]";
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
            return "[_any_]";
        }
        if (matchesNothing()) {
            return "[_none_]";
        }
        if (matchesSingleChar()) {
            return ranges.get(0).toString();
        }
        CodePointSetBuilder inverse = createInverse();
        if (inverse.ranges.size() < ranges.size()) {
            return "!" + inverse.toString();
        }
        return ranges.stream().map(CodePointRange::toString).collect(Collectors.joining("", "[", "]"));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CodePointSetBuilder && this.getRanges().equals(((CodePointSetBuilder) obj).getRanges());
    }

    public boolean equalsCodePointSet(CodePointSet o) {
        if (ranges.size() != o.size()) {
            return false;
        }
        for (int i = 0; i < ranges.size(); i++) {
            CodePointRange r = ranges.get(i);
            if (!o.equal(i, r.lo, r.hi)) {
                return false;
            }
        }
        return true;
    }

    public CodePointSet toCodePointSet() {
        normalize();
        int[] cpsRanges = new int[ranges.size() * 2];
        for (int i = 0; i < ranges.size(); i++) {
            cpsRanges[i * 2] = ranges.get(i).lo;
            cpsRanges[i * 2 + 1] = ranges.get(i).hi;
        }
        return CodePointSet.create(cpsRanges);
    }

    @Override
    public int hashCode() {
        return getRanges().hashCode();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        normalize();
        return Json.array(ranges);
    }

    private static boolean rangesAreSortedAndDisjoint(List<CodePointRange> ranges) {
        for (int i = 1; i < ranges.size(); i++) {
            if ((ranges.get(i - 1).lo > ranges.get(i).lo) ||
                            ranges.get(i - 1).intersects(ranges.get(i))) {
                return false;
            }
        }
        return true;
    }
}
