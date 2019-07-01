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
     * are included and {@link #rangesAreSortedAndDisjoint()} holds after the operation.
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
