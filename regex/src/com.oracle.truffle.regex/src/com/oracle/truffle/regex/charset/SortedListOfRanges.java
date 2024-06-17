/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.chardata.CharacterSet;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

/**
 * A storage-agnostic implementation of a sorted list of disjoint integer ranges with inclusive
 * lower and upper bounds. Holds the invariant {@link #rangesAreSortedNonAdjacentAndDisjoint()}.
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
     * {@link ImmutableSortedListOfRanges#createInverse(Encoding)}) of this list.
     */
    default int sizeOfInverse(Encoding encoding) {
        if (isEmpty()) {
            return 1;
        }
        return (getMin() <= encoding.getMinValue() ? 0 : 1) + size() - (getMax() >= encoding.getMaxValue() ? 1 : 0);
    }

    /**
     * Returns the smallest value contained in this set. Must not be called on empty sets.
     */
    default int getMin() {
        assert !isEmpty();
        return getLo(0);
    }

    /**
     * Returns the largest value contained in this set. Must not be called on empty sets.
     */
    default int getMax() {
        assert !isEmpty();
        return getHi(size() - 1);
    }

    /**
     * Returns the smallest value contained in the inverse of this set. Must not be called on empty
     * or full sets.
     */
    default int inverseGetMin(Encoding encoding) {
        assert !isEmpty() && !matchesEverything(encoding);
        return getMin() == encoding.getMinValue() ? getHi(0) + 1 : encoding.getMinValue();
    }

    /**
     * Returns the largest value contained in the inverse of this set. Must not be called on empty
     * or full sets.
     */
    default int inverseGetMax(Encoding encoding) {
        assert !isEmpty() && !matchesEverything(encoding);
        return getMax() == encoding.getMaxValue() ? getLo(size() - 1) - 1 : encoding.getMaxValue();
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
     * range b" - i.e. {code aHi &lt; bLo}.
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
     * Returns {@code true} if a given binary search result is equal to the range in list {@code o}
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
    default boolean rangesAreSortedNonAdjacentAndDisjoint() {
        if (size() > 0 && getLo(0) > getHi(0)) {
            return false;
        }
        for (int i = 1; i < size(); i++) {
            if (getLo(i) > getHi(i) || (!leftOf(i - 1, this, i)) || intersects(i - 1, this, i) || adjacent(i - 1, this, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this list is sorted and all of its ranges are disjoint. This property
     * must hold at all times.
     */
    default boolean rangesAreSortedAndDisjoint() {
        if (size() > 0 && getLo(0) > getHi(0)) {
            return false;
        }
        for (int i = 1; i < size(); i++) {
            if (getLo(i) > getHi(i) || (!leftOf(i - 1, this, i)) || intersects(i - 1, this, i)) {
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
        if (o.matchesNothing()) {
            return true;
        }
        if (matchesNothing()) {
            return o.matchesNothing();
        }
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
     * Returns {@code true} if this list intersects with {@code o}.
     */
    default boolean intersects(SortedListOfRanges o) {
        if (matchesNothing() || o.matchesNothing() || getHi(size() - 1) < o.getLo(0) || o.getHi(o.size() - 1) < getLo(0)) {
            return false;
        }
        SortedListOfRanges a = this;
        SortedListOfRanges b = o;
        if (size() > o.size()) {
            a = o;
            b = this;
        }
        for (int ia = 0; ia < a.size(); ia++) {
            int search = b.binarySearch(a.getLo(ia));
            if (b.binarySearchExactMatch(search, a, ia)) {
                return true;
            }
            int firstIntersection = b.binarySearchGetFirstIntersecting(search, a, ia);
            if (!(b.binarySearchNoIntersectingFound(firstIntersection) || b.rightOf(firstIntersection, a, ia))) {
                return true;
            }
        }
        return false;
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
     * Converts {@code target} to the intersection of {@code a} and {@code b}.
     */
    static void intersect(SortedListOfRanges a, SortedListOfRanges b, RangesBuffer target) {
        target.clear();
        for (int ia = 0; ia < a.size(); ia++) {
            int search = b.binarySearch(a.getLo(ia));
            if (b.binarySearchExactMatch(search, a, ia)) {
                a.addRangeTo(target, ia);
                continue;
            }
            int firstIntersection = b.binarySearchGetFirstIntersecting(search, a, ia);
            for (int ib = firstIntersection; ib < b.size(); ib++) {
                if (b.rightOf(ib, a, ia)) {
                    break;
                }
                assert a.intersects(ia, b, ib);
                target.appendRange(Math.max(a.getLo(ia), b.getLo(ib)), Math.min(a.getHi(ia), b.getHi(ib)));
            }
        }
    }

    static void invert(SortedListOfRanges a, Encoding encoding, RangesBuffer target) {
        target.clear();
        if (a.isEmpty()) {
            target.appendRange(encoding.getMinValue(), encoding.getMaxValue());
            return;
        }
        if (a.getMin() > encoding.getMinValue()) {
            target.appendRange(encoding.getMinValue(), a.getMin() - 1);
        }
        for (int i = 1; i < a.size(); i++) {
            target.appendRange(a.getHi(i - 1) + 1, a.getLo(i) - 1);
        }
        if (a.getMax() < encoding.getMaxValue()) {
            target.appendRange(a.getMax() + 1, encoding.getMaxValue());
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
     * Returns {@code true} iff this set contains {@link Encoding#getMinValue()} and
     * {@link Encoding#getMaxValue()}.
     */
    default boolean matchesMinAndMax(Encoding encoding) {
        return matchesSomething() && getMin() == encoding.getMinValue() && getMax() == encoding.getMaxValue();
    }

    /**
     * Returns {@code true} iff this code point set contains exactly two characters whose binary
     * representation differs in one bit only.
     */
    default boolean matches2CharsWith1BitDifference() {
        if (matchesNothing() || size() > 2 || !valueCountEquals(2)) {
            return false;
        }
        return Integer.bitCount(getMin() ^ getMax()) == 1;
    }

    /**
     * Returns the total number of values contained in this list.
     */
    default int valueCount() {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            count += size(i);
        }
        return count;
    }

    /**
     * Returns {@code true} iff the total number of values contained in this list is equal to
     * {@code cmp}.
     */
    default boolean valueCountEquals(int cmp) {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            count += size(i);
            if (count > cmp) {
                return false;
            }
        }
        return count == cmp;
    }

    /**
     * Returns {@code true} iff the total number of values contained in this list is less or equal
     * to {@code cmp}.
     */
    default boolean valueCountMax(int cmp) {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            count += size(i);
            if (count > cmp) {
                return false;
            }
        }
        return count <= cmp;
    }

    /**
     * Returns the total number of values (from {@link Encoding#getMinValue()} to
     * {@link Encoding#getMaxValue()}) <i>not</i> contained in this list.
     */
    default int inverseValueCount(Encoding encoding) {
        return (encoding.getMaxValue() - encoding.getMinValue()) + 1 - valueCount();
    }

    /**
     * Returns {@code true} if this list is equal to [{@link Encoding#getMinValue()}
     * {@link Encoding#getMaxValue()} ].
     */
    default boolean matchesEverything(Encoding encoding) {
        // ranges should be consolidated to one
        return size() == 1 && getLo(0) == encoding.getMinValue() && getHi(0) == encoding.getMaxValue();
    }

    default boolean equalsListOfRanges(SortedListOfRanges o) {
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
        if (matchesNothing()) {
            return "[]";
        }
        if (matchesSingleChar()) {
            return Range.toString(getLo(0), getHi(0));
        }
        return "[" + rangesToString() + "]";
    }

    @TruffleBoundary
    default String rangesToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(Range.toString(getLo(i), getHi(i)));
        }
        return sb.toString();
    }

    @TruffleBoundary
    default String inverseRangesToString(Encoding encoding) {
        StringBuilder sb = new StringBuilder();
        if (matchesNothing()) {
            sb.append(Range.toString(encoding.getMinValue(), encoding.getMaxValue()));
            return sb.toString();
        }
        if (getLo(0) > encoding.getMinValue()) {
            sb.append(Range.toString(encoding.getMinValue(), getLo(0) - 1));
        }
        for (int ia = 1; ia < size(); ia++) {
            sb.append(Range.toString(getHi(ia - 1) + 1, getLo(ia) - 1));
        }
        if (getHi(size() - 1) < encoding.getMaxValue()) {
            sb.append(Range.toString(getHi(size() - 1) + 1, encoding.getMaxValue()));
        }
        return sb.toString();
    }
}
