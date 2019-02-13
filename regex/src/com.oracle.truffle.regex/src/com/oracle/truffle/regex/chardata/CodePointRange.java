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
import com.oracle.truffle.regex.tregex.parser.ContainsRange;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.List;

public class CodePointRange implements Comparable<CodePointRange>, ContainsRange, JsonConvertible {

    public final int lo;
    public final int hi;

    public CodePointRange(int lo, int hi) {
        assert hi >= lo;
        this.lo = lo;
        this.hi = hi;
    }

    public CodePointRange(int c) {
        this(c, c);
    }

    public CodePointRange(char lo, char hi) {
        this((int) lo, (int) hi);
    }

    public CodePointRange(char c) {
        this((int) c);
    }

    public static CodePointRange fromUnordered(int c1, int c2) {
        return new CodePointRange(Math.min(c1, c2), Math.max(c1, c2));
    }

    @Override
    public CodePointRange getRange() {
        return this;
    }

    public CodePointRange move(int delta) {
        return new CodePointRange(lo + delta, hi + delta);
    }

    public CodePointRange expand(CodePointRange o) {
        assert intersects(o) || adjacent(o);
        return new CodePointRange(Math.min(lo, o.lo), Math.max(hi, o.hi));
    }

    public CodePointRange createIntersection(CodePointRange o) {
        return intersects(o) ? new CodePointRange(Math.max(lo, o.lo), Math.min(hi, o.hi)) : null;
    }

    public boolean isSingle() {
        return lo == hi;
    }

    public boolean contains(CodePointRange o) {
        return lo <= o.lo && hi >= o.hi;
    }

    public boolean intersects(CodePointRange o) {
        return lo <= o.hi && o.lo <= hi;
    }

    public boolean rightOf(CodePointRange o) {
        return lo > o.hi;
    }

    public boolean adjacent(CodePointRange o) {
        return hi + 1 == o.lo || lo - 1 == o.hi;
    }

    @Override
    public int compareTo(CodePointRange o) {
        return lo - o.lo;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof CodePointRange && lo == ((CodePointRange) obj).lo && hi == ((CodePointRange) obj).hi;
    }

    @Override
    public int hashCode() {
        return (31 * lo) + (31 * hi);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (isSingle()) {
            return DebugUtil.charToString(lo);
        }
        return DebugUtil.charToString(lo) + "-" + DebugUtil.charToString(hi);
    }

    public static boolean binarySearchExactMatch(List<? extends ContainsRange> ranges, ContainsRange range, int searchResult) {
        return searchResult >= 0 && ranges.get(searchResult).getRange().equals(range.getRange());
    }

    public static boolean binarySearchExactMatch(ContainsRange[] ranges, ContainsRange range, int searchResult) {
        return searchResult >= 0 && ranges[searchResult].getRange().equals(range.getRange());
    }

    public static int binarySearchGetFirstIntersecting(List<? extends ContainsRange> ranges, ContainsRange range, int searchResult) {
        assert CodePointRange.rangesAreSortedAndDisjoint(ranges);
        if (searchResult >= 0) {
            assert !ranges.get(searchResult).getRange().equals(range.getRange());
            return searchResult;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        if (insertionPoint > 0 && ranges.get(insertionPoint - 1).getRange().intersects(range.getRange())) {
            return insertionPoint - 1;
        }
        return insertionPoint;
    }

    public static int binarySearchGetFirstIntersecting(ContainsRange[] ranges, ContainsRange range, int searchResult) {
        assert CodePointRange.rangesAreSortedAndDisjoint(ranges);
        if (searchResult >= 0) {
            assert !ranges[searchResult].getRange().equals(range.getRange());
            return searchResult;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        if (insertionPoint > 0 && ranges[insertionPoint - 1].getRange().intersects(range.getRange())) {
            return insertionPoint - 1;
        }
        return insertionPoint;
    }

    public static boolean binarySearchNoIntersectingFound(ContainsRange[] ranges, int firstIntersecting) {
        return firstIntersecting == ranges.length;
    }

    public static boolean rangesAreSortedAndDisjoint(List<? extends ContainsRange> ranges) {
        for (int i = 1; i < ranges.size(); i++) {
            if ((ranges.get(i - 1).getRange().lo > ranges.get(i).getRange().lo) ||
                            ranges.get(i - 1).getRange().intersects(ranges.get(i).getRange())) {
                return false;
            }
        }
        return true;
    }

    public static boolean rangesAreSortedAndDisjoint(ContainsRange[] ranges) {
        for (int i = 1; i < ranges.length; i++) {
            if ((ranges[i - 1].getRange().lo > ranges[i].getRange().lo) ||
                            ranges[i - 1].getRange().intersects(ranges[i].getRange())) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("hi", hi),
                        Json.prop("lo", lo));
    }
}
