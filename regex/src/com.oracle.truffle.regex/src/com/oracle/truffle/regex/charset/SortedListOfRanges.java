/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.chardata.CharacterSet;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

/**
 * A storage-agnostic implementation of a sorted list of disjoint integer ranges with inclusive
 * lower and upper bounds. Holds the invariant {@link #rangesAreSortedAndDisjoint()}.
 */
public interface SortedListOfRanges extends CharacterSet {

    /**
     * Returns the inclusive lower bound of the range stored at index {@code i}.
     */
    int getLo(int i);

    /**
     * Returns the inclusive upper bound of the range stored at index {@code i}.
     */
    int getHi(int i);

    /**
     * Returns the number of disjoint ranges contained in this list.
     */
    int size();

    /**
     * Returns the minimum value that may be contained in an instance of this list.
     */
    int getMinValue();

    /**
     * Returns the maximum value that may be contained in an instance of this list.
     */
    int getMaxValue();

    /**
     * Append all ranges from {@code startIndex} (inclusive) to {@code endIndex} (exclusive) to the
     * given {@code buffer}. The caller is responsible for not violating the target buffer's
     * sortedness; This effectively means that the range at {@code startIndex} must be
     * {@link #rightOf(int, int, int) rightOf} the buffer's last range.
     */
    void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex);

    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns {@code true} if the range at index {@code i} consists of a single value, i.e.
     * {@code getLo(i) == getHi(i)}.
     */
    default boolean isSingle(int i) {
        return getLo(i) == getHi(i);
    }

    /**
     * Returns the number of values contained in the range at index {@code i}.
     */
    default int size(int i) {
        return (getHi(i) - getLo(i)) + 1;
    }

    /**
     * Returns the number of disjoint ranges contained in the inverse (as defined by
     * {@link ImmutableSortedListOfRanges#createInverse()}) of this list.
     */
    default int sizeOfInverse() {
        if (isEmpty()) {
            return 1;
        }
        return (getLo(0) == getMinValue() ? 0 : 1) + size() - (getHi(size() - 1) == getMaxValue() ? 1 : 0);
    }

    /**
     * Returns {@code true} if the range {@code [aLo, aHi]} contains the range {@code [bLo, bHi]}.
     */
    static boolean contains(int aLo, int aHi, int bLo, int bHi) {
        return aLo <= bLo && aHi >= bHi;
    }

    /**
     * Returns {@code true} if the range at index {@code ia} contains the range in list {@code o} at
     * index {@code ib}.
     */
    default boolean contains(int ia, SortedListOfRanges o, int ib) {
        return contains(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} contains the range {@code [bLo, bHi]}.
     */
    default boolean contains(int ia, int bLo, int bHi) {
        return contains(getLo(ia), getHi(ia), bLo, bHi);
    }

    /**
     * Returns {@code true} if the range {@code [bLo, bHi]} contains the range at index {@code ia}.
     */
    default boolean containedBy(int ia, int bLo, int bHi) {
        return contains(bLo, bHi, getLo(ia), getHi(ia));
    }

    /**
     * Returns {@code true} if the range {@code [aLo, aHi]} intersects with the range
     * {@code [bLo, bHi]}.
     */
    static boolean intersects(int aLo, int aHi, int bLo, int bHi) {
        return aLo <= bHi && bLo <= aHi;
    }

    /**
     * Returns {@code true} if the range at index {@code ia} intersects with the range in list
     * {@code o} at index {@code ib}.
     */
    default boolean intersects(int ia, SortedListOfRanges o, int ib) {
        return intersects(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} intersects with the range
     * {@code [bLo, bHi]}.
     */
    default boolean intersects(int ia, int bLo, int bHi) {
        return intersects(getLo(ia), getHi(ia), bLo, bHi);
    }

    /**
     * Returns {@code true} if the range {@code [aLo, aHi]} is "left of" the range
     * {@code [bLo, bHi]}, where "left of" means "all values of range a are less than all values of
     * range b" - i.e. {code aHi < bLo}.
     */
    @SuppressWarnings("unused")
    static boolean leftOf(int aLo, int aHi, int bLo, int bHi) {
        return aHi < bLo;
    }

    /**
     * Variant of {@link #leftOf(int, int, int, int)} without the unnecessary parameters.
     */
    static boolean leftOf(int aHi, int bLo) {
        return aHi < bLo;
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is "left of" the range in list
     * {@code o} at index {@code ib}.
     *
     * @see #leftOf(int, int, int, int)
     */
    default boolean leftOf(int ia, SortedListOfRanges o, int ib) {
        return leftOf(getHi(ia), o.getLo(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is "left of" the range
     * {@code [bLo, bHi]}.
     *
     * @see #leftOf(int, int, int, int)
     */
    @SuppressWarnings("unused")
    default boolean leftOf(int ia, int bLo, int bHi) {
        return leftOf(getHi(ia), bLo);
    }

    /**
     * Returns {@code true} if the range {@code [aLo, aHi]} is "right of" the range
     * {@code [bLo, bHi]}, where "right of" means "all values of range a are greater than all values
     * of range b" - i.e. {code aLo > bHi}.
     */
    @SuppressWarnings("unused")
    static boolean rightOf(int aLo, int aHi, int bLo, int bHi) {
        return aLo > bHi;
    }

    /**
     * Variant of {@link #rightOf(int, int, int, int)} without the unnecessary parameters.
     */
    static boolean rightOf(int aLo, int bHi) {
        return aLo > bHi;
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is "right of" the range in list
     * {@code o} at index {@code ib}.
     *
     * @see #rightOf(int, int, int, int)
     */
    default boolean rightOf(int ia, SortedListOfRanges o, int ib) {
        return rightOf(getLo(ia), o.getHi(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is "right of" the range
     * {@code [bLo, bHi]}.
     *
     * @see #rightOf(int, int, int, int)
     */
    @SuppressWarnings("unused")
    default boolean rightOf(int ia, int bLo, int bHi) {
        return rightOf(getLo(ia), bHi);
    }

    /**
     * Returns {@code true} if the ranges {@code [aLo, aHi]} and {@code [bLo, bHi]} are adjacent to
     * each other, meaning that the lower bound of one range immediately follows the upper bound of
     * the other.
     */
    static boolean adjacent(int aLo, int aHi, int bLo, int bHi) {
        return aHi + 1 == bLo || aLo - 1 == bHi;
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is adjacent to the range in list
     * {@code o} at index {@code ib}.
     *
     * @see #adjacent(int, int, int, int)
     */
    default boolean adjacent(int ia, SortedListOfRanges o, int ib) {
        return adjacent(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is adjacent to the range
     * {@code [bLo, bHi]}.
     *
     * @see #adjacent(int, int, int, int)
     */
    default boolean adjacent(int ia, int bLo, int bHi) {
        return adjacent(getLo(ia), getHi(ia), bLo, bHi);
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is equal to the range in list {@code o}
     * at index {@code ib}.
     */
    default boolean equal(int ia, SortedListOfRanges o, int ib) {
        return equal(ia, o.getLo(ib), o.getHi(ib));
    }

    /**
     * Returns {@code true} if the range at index {@code ia} is equal to the range
     * {@code [bLo, bHi]}.
     */
    default boolean equal(int ia, int bLo, int bHi) {
        return getLo(ia) == bLo && getHi(ia) == bHi;
    }

    /**
     * Performs a binary search for a range with the given lower bound ({@code keyLo}), in the same
     * way as {@link java.util.Arrays#binarySearch(int[], int)} would behave on an array containing
     * only the lower bounds of all ranges in this list.
     */
    default int binarySearch(int keyLo) {
        int low = 0;
        int high = size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = getLo(mid);
            if (midVal < keyLo) {
                low = mid + 1;
            } else if (midVal > keyLo) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1);  // key not found.
    }

    /**
     * Returns {@code true} if a given binary search result is equals to the range in list {@code o}
     * at index {@code ib}.
     *
     * @param searchResult the result of a call to {@link #binarySearch(int)} with
     *            {@code o.getLo(ib)} as the parameter.
     */
    default boolean binarySearchExactMatch(int searchResult, SortedListOfRanges o, int ib) {
        return binarySearchExactMatch(searchResult, o.getLo(ib), o.getHi(ib));
    }

    /**
     * Returns {@code true} if a given binary search result is equals to the range
     * {@code [bLo, bHi]}.
     *
     * @param searchResult the result of a call to {@link #binarySearch(int)} with {@code bLo} as
     *            the parameter.
     */
    default boolean binarySearchExactMatch(int searchResult, int bLo, int bHi) {
        return searchResult >= 0 && equal(searchResult, bLo, bHi);
    }

    /**
     * If there was no {@link #binarySearchExactMatch(int, int, int) exact match} in a
     * {@link #binarySearch(int) binary search}, this method will return the index of the first
     * range that intersects with the range in {@code o} at index {@code ib}, or {@link #size()}.
     *
     * @param searchResult the result of a call to {@link #binarySearch(int)} with
     *            {@code o.getLo(ib)} as the parameter.
     */
    default int binarySearchGetFirstIntersecting(int searchResult, SortedListOfRanges o, int ib) {
        return binarySearchGetFirstIntersecting(searchResult, o.getLo(ib), o.getHi(ib));
    }

    /**
     * If there was no {@link #binarySearchExactMatch(int, int, int) exact match} in a
     * {@link #binarySearch(int) binary search}, this method will return the index of the first
     * range that intersects with the range {@code [bLo, bHi]}, or {@link #size()}.
     *
     * @param searchResult the result of a call to {@link #binarySearch(int)} with {@code bLo} as
     *            the parameter.
     */
    default int binarySearchGetFirstIntersecting(int searchResult, int bLo, int bHi) {
        return binarySearchGetFirstIntersectingOrAdjacent(searchResult, bLo, bHi, false);
    }

    /**
     * If there was no {@link #binarySearchExactMatch(int, int, int) exact match} in a
     * {@link #binarySearch(int) binary search}, this method will return the index of the first
     * range that intersects with or is adjacent to the range {@code [bLo, bHi]}, or {@link #size()}
     * .
     *
     * @param searchResult the result of a call to {@link #binarySearch(int)} with {@code bLo} as
     *            the parameter.
     */
    default int binarySearchGetFirstIntersectingOrAdjacent(int searchResult, int bLo, int bHi) {
        return binarySearchGetFirstIntersectingOrAdjacent(searchResult, bLo, bHi, true);
    }

    default int binarySearchGetFirstIntersectingOrAdjacent(int searchResult, int oLo, int oHi, boolean includeAdjacent) {
        if (searchResult >= 0) {
            assert !equal(searchResult, oLo, oHi);
            return searchResult;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        if (insertionPoint > 0 && (intersects(insertionPoint - 1, oLo, oHi) || (includeAdjacent && adjacent(insertionPoint - 1, oLo, oHi)))) {
            return insertionPoint - 1;
        }
        return insertionPoint;
    }

    /**
     * Returns {@code true} if no intersecting range was found by a call to
     * {@link #binarySearchGetFirstIntersecting(int, int, int)} or one if its variants.
     *
     * @param firstIntersecting the result of a call to
     *            {@link #binarySearchGetFirstIntersecting(int, int, int)} or one if its variants.
     */
    default boolean binarySearchNoIntersectingFound(int firstIntersecting) {
        return firstIntersecting == size();
    }

    /**
     * Appends the range at index {@code i} to the given {@code buffer}.
     */
    default void addRangeTo(RangesBuffer buffer, int i) {
        buffer.appendRange(getLo(i), getHi(i));
    }

    /**
     * Returns {@code true} if this list is sorted and all of its ranges are disjoint and
     * non-adjacent. This property must hold at all times.
     */
    default boolean rangesAreSortedAndDisjoint() {
        for (int i = 1; i < size(); i++) {
            if ((!leftOf(i - 1, this, i)) || intersects(i - 1, this, i) || adjacent(i - 1, this, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this list contains the given {@code codePoint}.
     */
    @Override
    default boolean contains(int codePoint) {
        int low = 0;
        int high = size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (codePoint < getLo(mid)) {
                high = mid - 1;
            } else if (codePoint > getHi(mid)) {
                low = mid + 1;
            } else { // codePoint >= midRange.lo && codePoint <= midRange.hi
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if this list contains all values of {@code o}.
     */
    default boolean contains(SortedListOfRanges o) {
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

    /**
     * Converts {@code target} to the union of {@code a} and {@code b}.
     */
    static void union(SortedListOfRanges a, SortedListOfRanges b, RangesBuffer target) {
        target.clear();
        int tmpLo;
        int tmpHi;
        int ia = 0;
        int ib = 0;
        while (ia < a.size() && ib < b.size()) {
            int iaInit = ia;
            while (ia < a.size() && a.leftOf(ia, b, ib) && !a.adjacent(ia, b, ib)) {
                ia++;
            }
            a.appendRangesTo(target, iaInit, ia);
            if (ia == a.size()) {
                break;
            }
            int ibInit = ib;
            while (ib < b.size() && b.leftOf(ib, a, ia) && !a.adjacent(ia, b, ib)) {
                ib++;
            }
            b.appendRangesTo(target, ibInit, ib);
            if (ib == b.size()) {
                break;
            }
            if (a.intersects(ia, b, ib) || a.adjacent(ia, b, ib)) {
                tmpLo = Math.min(a.getLo(ia), b.getLo(ib));
                tmpHi = Math.max(a.getHi(ia), b.getHi(ib));
                ia++;
                ib++;
                while (true) {
                    if (ia < a.size() && (a.intersects(ia, tmpLo, tmpHi) || a.adjacent(ia, tmpLo, tmpHi))) {
                        tmpLo = Math.min(a.getLo(ia), tmpLo);
                        tmpHi = Math.max(a.getHi(ia), tmpHi);
                        ia++;
                    } else if (ib < b.size() && (b.intersects(ib, tmpLo, tmpHi) || b.adjacent(ib, tmpLo, tmpHi))) {
                        tmpLo = Math.min(b.getLo(ib), tmpLo);
                        tmpHi = Math.max(b.getHi(ib), tmpHi);
                        ib++;
                    } else {
                        break;
                    }
                }
                target.appendRange(tmpLo, tmpHi);
            } else {
                if (a.rightOf(ia, b, ib)) {
                    b.addRangeTo(target, ib);
                    ib++;
                } else {
                    assert b.rightOf(ib, a, ia);
                    a.addRangeTo(target, ia);
                    ia++;
                }
            }
        }
        if (ia < a.size()) {
            a.appendRangesTo(target, ia, a.size());
        }
        if (ib < b.size()) {
            b.appendRangesTo(target, ib, b.size());
        }
    }

    /**
     * Returns {@code true} if this list is empty.
     */
    default boolean matchesNothing() {
        return size() == 0;
    }

    /**
     * Returns {@code true} if this list is non-empty.
     */
    default boolean matchesSomething() {
        return !matchesNothing();
    }

    /**
     * Returns {@code true} if this list contains just one single value.
     */
    default boolean matchesSingleChar() {
        return size() == 1 && isSingle(0);
    }

    /**
     * Returns {@code true} if this list contains just one single value which is less than 128.
     */
    default boolean matchesSingleAscii() {
        return matchesSingleChar() && getLo(0) < 128;
    }

    /**
     * Returns {@code true} if this list consists of two values whose binary representations differ
     * in only a single bit.
     */
    default boolean matches2CharsWith1BitDifference() {
        if (matchesNothing() || size() > 2 || valueCount() != 2) {
            return false;
        }
        int c1 = getLo(0);
        int c2 = size() == 1 ? getHi(0) : getLo(1);
        return Integer.bitCount(c1 ^ c2) == 1;
    }

    /**
     * Returns the total number of values contained in this list.
     */
    default int valueCount() {
        int charSize = 0;
        for (int i = 0; i < size(); i++) {
            charSize += size(i);
        }
        return charSize;
    }

    /**
     * Returns the total number of values (from {@link #getMinValue()} to {@link #getMaxValue()})
     * <i>not</i> contained in this list.
     */
    default int inverseValueCount() {
        return (getMaxValue() - getMinValue()) + 1 - valueCount();
    }

    /**
     * Returns {@code true} if this list is equal to [{@link #getMinValue()} {@link #getMaxValue()}
     * ].
     */
    default boolean matchesEverything() {
        // ranges should be consolidated to one
        return size() == 1 && getLo(0) == getMinValue() && getHi(0) == getMaxValue();
    }

    default boolean equals(SortedListOfRanges o) {
        if (o == null || size() != o.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!equal(i, o, i)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    default String defaultToString() {
        if (equals(Constants.DOT)) {
            return ".";
        }
        if (equals(Constants.LINE_TERMINATOR)) {
            return "[\\r\\n\\u2028\\u2029]";
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
            return "[\\s\\S]";
        }
        if (matchesNothing()) {
            return "[]";
        }
        if (matchesSingleChar()) {
            return rangeToString(getLo(0), getHi(0));
        }
        if (getLo(0) == getMinValue() || getHi(size() - 1) == getMaxValue()) {
            return "[^" + inverseRangesToString() + "]";
        } else {
            return "[" + rangesToString() + "]";
        }
    }

    @TruffleBoundary
    static String rangeToString(int lo, int hi) {
        if (lo == hi) {
            return DebugUtil.charToString(lo);
        }
        return DebugUtil.charToString(lo) + "-" + DebugUtil.charToString(hi);
    }

    @TruffleBoundary
    default String rangesToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(rangeToString(getLo(i), getHi(i)));
        }
        return sb.toString();
    }

    @TruffleBoundary
    default String inverseRangesToString() {
        StringBuilder sb = new StringBuilder();
        if (matchesNothing()) {
            sb.append(rangeToString(getMinValue(), getMaxValue()));
            return sb.toString();
        }
        if (getLo(0) > getMinValue()) {
            sb.append(rangeToString(getMinValue(), getLo(0) - 1));
        }
        for (int ia = 1; ia < size(); ia++) {
            sb.append(rangeToString(getHi(ia - 1) + 1, getLo(ia) - 1));
        }
        if (getHi(size() - 1) < getMaxValue()) {
            sb.append(rangeToString(getHi(size() - 1) + 1, getMaxValue()));
        }
        return sb.toString();
    }
}
