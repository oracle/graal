/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Extensions of {@link SortedListOfRanges} specific to mutable implementations.
 */
public interface RangesBuffer extends SortedListOfRanges {

    /**
     * Removes all ranges from this list.
     */
    void clear();

    /**
     * Appends {@code [lo hi]} to this list. The given range must be {@link #rightOf(int, int, int)
     * right of} and non-adjacent to the last range in this list.
     */
    void appendRange(int lo, int hi);

    /**
     * Insert {@code [lo hi]} at the given index. The given range must be
     * {@link #rightOf(int, int, int) right of} and non-adjacent to the range at {@code index - 1},
     * and {@link #leftOf(int, int, int) left of} and non-adjacent to the range at {@code index}.
     */
    void insertRange(int index, int lo, int hi);

    /**
     * Replace all ranges from {@code fromIndex} (inclusive) to {@code toIndex} (exclusive) with the
     * single range {@code [lo hi]}. The given range must be {@link #rightOf(int, int, int) right
     * of} and non-adjacent to the range at {@code fromIndex - 1}, and {@link #leftOf(int, int, int)
     * left of} and non-adjacent to the range at {@code toIndex}.
     */
    void replaceRanges(int fromIndex, int toIndex, int lo, int hi);

    /**
     * Create a new instance of this class.
     */
    <T extends RangesBuffer> T create();

    /**
     * Add {@code [lo hi]} to this list. The list is altered such that all values of the given range
     * are included and {@link #rangesAreSortedNonAdjacentAndDisjoint()} holds after the operation.
     */
    default void addRange(int lo, int hi) {
        int search = binarySearch(lo);
        if (binarySearchExactMatch(search, lo, hi)) {
            return;
        }
        int firstIntersection = binarySearchGetFirstIntersectingOrAdjacent(search, lo, hi);
        if (binarySearchNoIntersectingFound(firstIntersection)) {
            appendRange(lo, hi);
        } else if (rightOf(firstIntersection, lo, hi) && !adjacent(firstIntersection, lo, hi)) {
            insertRange(firstIntersection, lo, hi);
        } else {
            int newLo = Math.min(lo, getLo(firstIntersection));
            int lastIntersection = firstIntersection + 1;
            while (lastIntersection < size() && (intersects(lastIntersection, lo, hi) || adjacent(lastIntersection, lo, hi))) {
                lastIntersection++;
            }
            int newHi = Math.max(hi, getHi(lastIntersection - 1));
            replaceRanges(firstIntersection, lastIntersection, newLo, newHi);
        }
    }
}
