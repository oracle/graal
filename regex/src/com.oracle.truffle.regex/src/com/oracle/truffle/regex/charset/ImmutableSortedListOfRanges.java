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

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;

/**
 * Extensions of {@link SortedListOfRanges} specific to immutable implementations. Any methods of
 * this interface that return a list instance may return references to existing objects.
 */
public interface ImmutableSortedListOfRanges extends SortedListOfRanges {

    /**
     * Returns an empty list.
     */
    <T extends SortedListOfRanges> T createEmpty();

    /**
     * Returns [{@link #getMinValue()} {@link #getMaxValue()}].
     */
    <T extends SortedListOfRanges> T createFull();

    /**
     * Returns an immutable equivalent of the given {@code buffer}.
     */
    <T extends SortedListOfRanges> T create(RangesBuffer buffer);

    /**
     * Returns a list containing all values of [{@link #getMinValue()} {@link #getMaxValue()}]
     * <i>not</i> contained in this list.
     */
    <T extends SortedListOfRanges> T createInverse();

    /**
     * Returns a buffer from the given {@code compilationBuffer} that is compatible with this list's
     * storage implementation.
     */
    RangesBuffer getBuffer1(CompilationBuffer compilationBuffer);

    /**
     * Returns a buffer from the given {@code compilationBuffer} that is compatible with this list's
     * storage implementation.
     */
    RangesBuffer getBuffer2(CompilationBuffer compilationBuffer);

    /**
     * Returns a buffer from the given {@code compilationBuffer} that is compatible with this list's
     * storage implementation.
     */
    RangesBuffer getBuffer3(CompilationBuffer compilationBuffer);

    /**
     * Creates a new buffer that is compatible with this list's storage implementation.
     */
    RangesBuffer createTempBuffer();

    /**
     * Returns {@code true} if this list equals {@code buffer}.
     */
    boolean equalsBuffer(RangesBuffer buffer);

    /**
     * Returns the intersection of this list and {@code o}. Uses
     * {@link #getBuffer1(CompilationBuffer)}.
     */
    default <T extends ImmutableSortedListOfRanges> T createIntersection(T o, CompilationBuffer compilationBuffer) {
        return createIntersection(o, getBuffer1(compilationBuffer));
    }

    /**
     * Converts {@code target} to the intersection of this list and {@code o} and returns an
     * immutable equivalent.
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> T createIntersection(T o, RangesBuffer target) {
        target.clear();
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (o.binarySearchExactMatch(search, this, ia)) {
                addRangeTo(target, ia);
                continue;
            }
            int firstIntersection = o.binarySearchGetFirstIntersecting(search, this, ia);
            for (int ib = firstIntersection; ib < o.size(); ib++) {
                if (o.rightOf(ib, this, ia)) {
                    break;
                }
                assert intersects(ia, o, ib);
                target.appendRange(Math.max(getLo(ia), o.getLo(ib)), Math.min(getHi(ia), o.getHi(ib)));
            }
        }
        if (equalsBuffer(target)) {
            return (T) this;
        }
        if (o.equalsBuffer(target)) {
            return o;
        }
        return create(target);
    }

    /**
     * Returns the result of the subtraction of {@code o} from this list. Uses
     * {@link #getBuffer1(CompilationBuffer)}.
     */
    @SuppressWarnings("unchecked")
    default <T extends SortedListOfRanges> T subtract(T o, CompilationBuffer compilationBuffer) {
        RangesBuffer subtractionRanges = getBuffer1(compilationBuffer);
        int tmpLo;
        int tmpHi;
        boolean unchanged = true;
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (o.binarySearchExactMatch(search, this, ia)) {
                unchanged = false;
                continue;
            }
            int firstIntersection = o.binarySearchGetFirstIntersecting(search, this, ia);
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
                        subtractionRanges.appendRange(tmpLo, o.getLo(ib) - 1);
                        tmpLo = o.getHi(ib) + 1;
                    } else if (tmpLo < o.getLo(ib)) {
                        tmpHi = o.getLo(ib) - 1;
                    } else {
                        tmpLo = o.getHi(ib) + 1;
                    }
                }
            }
            if (rest) {
                subtractionRanges.appendRange(tmpLo, tmpHi);
            }
        }
        if (unchanged) {
            assert equalsBuffer(subtractionRanges);
            return (T) this;
        }
        return create(subtractionRanges);
    }

    /**
     * Calculates the intersection and the "rest" of this and {@code o}. Uses
     * {@link #getBuffer1(CompilationBuffer)}, {@link #getBuffer2(CompilationBuffer)} and
     * {@link #getBuffer3(CompilationBuffer)}.
     *
     * @param o MatcherBuilder to intersect with.
     * @param result Array of results, where index 0 is equal to {@code this.subtract(intersection)}
     *            , index 1 is equal to {@code o.subtract(intersection)} and index 2 is equal to
     *            {@code this.createIntersection(o)}.
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> void intersectAndSubtract(T o, CompilationBuffer compilationBuffer, T[] result) {
        if (matchesNothing() || o.matchesNothing()) {
            result[0] = (T) this;
            result[1] = o;
            result[2] = createEmpty();
            return;
        }
        RangesBuffer subtractedA = getBuffer1(compilationBuffer);
        RangesBuffer subtractedB = getBuffer2(compilationBuffer);
        RangesBuffer intersectionRanges = getBuffer3(compilationBuffer);
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
            result[0] = (T) this;
            result[1] = o;
            result[2] = createEmpty();
            return;
        }
        appendRangesTo(subtractedA, 0, ia);
        o.appendRangesTo(subtractedB, 0, ib);
        int raLo = getLo(ia);
        int raHi = getHi(ia);
        int rbLo = o.getLo(ib);
        int rbHi = o.getHi(ib);
        assert SortedListOfRanges.intersects(raLo, raHi, rbLo, rbHi);
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
                        subtractedB.appendRange(rbLo, rbHi);
                    }
                    o.appendRangesTo(subtractedB, ib, o.size());
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
                        subtractedA.appendRange(raLo, raHi);
                    }
                    appendRangesTo(subtractedA, ia, size());
                    finish = true;
                }
            }
            if (finish) {
                break;
            }
            if (SortedListOfRanges.leftOf(raLo, raHi, rbLo, rbHi)) {
                subtractedA.appendRange(raLo, raHi);
                advanceA = true;
                continue;
            }
            if (SortedListOfRanges.leftOf(rbLo, rbHi, raLo, raHi)) {
                subtractedB.appendRange(rbLo, rbHi);
                advanceB = true;
                continue;
            }
            assert SortedListOfRanges.intersects(raLo, raHi, rbLo, rbHi);
            int intersectionLo = Math.max(raLo, rbLo);
            int intersectionHi = Math.min(raHi, rbHi);
            intersectionRanges.appendRange(intersectionLo, intersectionHi);
            if (raLo < intersectionLo) {
                subtractedA.appendRange(raLo, intersectionLo - 1);
            }
            if (raHi > intersectionHi) {
                raLo = intersectionHi + 1;
            } else {
                advanceA = true;
            }
            if (rbLo < intersectionLo) {
                subtractedB.appendRange(rbLo, intersectionLo - 1);
            }
            if (rbHi > intersectionHi) {
                rbLo = intersectionHi + 1;
            } else {
                advanceB = true;
            }
        }
        result[0] = create(subtractedA);
        result[1] = create(subtractedB);
        if (subtractedA.isEmpty()) {
            assert equalsBuffer(intersectionRanges);
            result[2] = (T) this;
        } else if (subtractedB.isEmpty()) {
            assert o.equalsBuffer(intersectionRanges);
            result[2] = o;
        } else {
            result[2] = create(intersectionRanges);
        }
    }

    /**
     * Returns the union of this list and {@code o}. Creates a temporary buffer.
     */
    default <T extends ImmutableSortedListOfRanges> T union(T o) {
        return union(o, createTempBuffer());
    }

    /**
     * Returns the union of this list and {@code o}. Uses {@link #getBuffer1(CompilationBuffer)}.
     */
    default <T extends ImmutableSortedListOfRanges> T union(T o, CompilationBuffer compilationBuffer) {
        return union(o, getBuffer1(compilationBuffer));
    }

    /**
     * Converts {@code target} to the union of this list and {@code o} and returns an immutable
     * equivalent.
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> T union(T o, RangesBuffer target) {
        if (matchesNothing() || o.matchesEverything()) {
            return o;
        }
        if (matchesEverything() || o.matchesNothing()) {
            return (T) this;
        }
        SortedListOfRanges.union(this, o, target);
        if (equalsBuffer(target)) {
            return (T) this;
        }
        if (o.equalsBuffer(target)) {
            return o;
        }
        return create(target);
    }
}
