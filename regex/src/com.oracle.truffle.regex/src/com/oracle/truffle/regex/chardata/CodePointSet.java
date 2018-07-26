/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.chardata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class CodePointSet implements JsonConvertible {

    private List<CodePointRange> ranges;
    private boolean normalized;
    private boolean frozen;

    private CodePointSet(List<CodePointRange> ranges, boolean normalized) {
        this.ranges = ranges;
        this.normalized = normalized;
        this.frozen = false;
    }

    public List<CodePointRange> getRanges() {
        normalize();
        return Collections.unmodifiableList(ranges);
    }

    public static CodePointSet createEmpty() {
        return new CodePointSet(new ArrayList<>(), true);
    }

    public static CodePointSet create(Collection<CodePointRange> ranges) {
        List<CodePointRange> list = new ArrayList<>(ranges);
        return new CodePointSet(list, CodePointRange.rangesAreSortedAndDisjoint(list));
    }

    public static CodePointSet create(CodePointRange... ranges) {
        List<CodePointRange> list = new ArrayList<>();
        Collections.addAll(list, ranges);
        return new CodePointSet(list, CodePointRange.rangesAreSortedAndDisjoint(list));
    }

    public static CodePointSet create(int... codePoints) {
        List<CodePointRange> list = new ArrayList<>();
        for (int c : codePoints) {
            list.add(new CodePointRange(c));
        }
        return new CodePointSet(list, CodePointRange.rangesAreSortedAndDisjoint(list));
    }

    public CodePointSet addRange(CodePointRange range) {
        if (frozen) {
            throw new UnsupportedOperationException("Object marked as immutable");
        }
        ranges.add(range);
        normalized = false;
        return this;
    }

    public CodePointSet addSet(CodePointSet other) {
        if (frozen) {
            throw new UnsupportedOperationException("Object marked as immutable");
        }
        ranges.addAll(other.getRanges());
        normalized = false;
        return this;
    }

    public CodePointSet copy() {
        return new CodePointSet(new ArrayList<>(ranges), normalized);
    }

    public CodePointSet createInverse() {
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
        return new CodePointSet(invRanges, true);
    }

    public CodePointSet createIntersection(CodePointSet other) {
        normalize();

        List<CodePointRange> otherRanges = other.getRanges();
        List<CodePointRange> intersectionRanges = new ArrayList<>();
        for (CodePointRange r : ranges) {
            int search = Collections.binarySearch(otherRanges, r);
            if (CodePointRange.binarySearchExactMatch(otherRanges, r, search)) {
                intersectionRanges.add(r);
                continue;
            }
            int firstIntersection = CodePointRange.binarySearchGetFirstIntersecting(otherRanges, r, search);
            for (int i = firstIntersection; i < otherRanges.size(); i++) {
                CodePointRange o = otherRanges.get(i);
                if (o.rightOf(r)) {
                    break;
                }
                CodePointRange intersection = r.createIntersection(o);
                if (intersection != null) {
                    intersectionRanges.add(intersection);
                }
            }
        }
        return new CodePointSet(intersectionRanges, true);
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
     * {@link #addSet(CodePointSet) addSet} will throw an {@link UnsupportedOperationException}.
     * 
     * @return this, now immutable
     */
    public CodePointSet freeze() {
        frozen = true;
        return this;
    }

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
        if (equals(Constants.DOT)) {
            return ".";
        }
        if (equals(Constants.LINE_TERMINATOR)) {
            return "[\\r\\n]";
        }
        if (equals(Constants.DIGITS)) {
            return "\\d";
        }
        if (equals(Constants.NON_DIGITS)) {
            return "\\D";
        }
        if (equals(Constants.WORD_CHARS)) {
            return "\\w";
        }
        if (equals(Constants.NON_WORD_CHARS)) {
            return "\\W";
        }
        if (equals(Constants.WHITE_SPACE)) {
            return "\\s";
        }
        if (equals(Constants.NON_WHITE_SPACE)) {
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
        CodePointSet inverse = createInverse();
        if (inverse.ranges.size() < ranges.size()) {
            return "!" + inverse.toString();
        }
        return ranges.stream().map(CodePointRange::toString).collect(Collectors.joining("", "[", "]"));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CodePointSet && this.getRanges().equals(((CodePointSet) obj).getRanges());
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
}
