/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

/**
 * Extensions of {@link SortedListOfRanges} specific to immutable implementations. Any methods of
 * this interface that return a list instance may return references to existing objects.
 */
public interface ImmutableSortedListOfRanges extends SortedListOfRanges, Iterable<Range> {

    /**
     * Returns an empty list.
     */
    <T extends SortedListOfRanges> T createEmpty();

    /**
     * Returns an immutable equivalent of the given {@code buffer}.
     */
    <T extends SortedListOfRanges> T create(RangesBuffer buffer);

    /**
     * Returns a list containing all values of [{@link Encoding#getMinValue()}
     * {@link Encoding#getMaxValue()}] <i>not</i> contained in this list.
     */
    <T extends SortedListOfRanges> T createInverse(Encoding encoding);

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
     * Returns the intersection of this list and {@code o}, using {@code tmp} as working buffer.
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> T createIntersection(T o, RangesBuffer tmp) {
        if (isEmpty() || o.isEmpty()) {
            return createEmpty();
        }
        if (size() == 1) {
            return createIntersectionSingleRange(o);
        }
        if (o.size() == 1) {
            return o.createIntersectionSingleRange((T) this);
        }
        tmp.clear();
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (o.binarySearchExactMatch(search, this, ia)) {
                addRangeTo(tmp, ia);
                continue;
            }
            int firstIntersection = o.binarySearchGetFirstIntersecting(search, this, ia);
            for (int ib = firstIntersection; ib < o.size(); ib++) {
                if (o.rightOf(ib, this, ia)) {
                    break;
                }
                assert intersects(ia, o, ib);
                tmp.appendRange(Math.max(getLo(ia), o.getLo(ib)), Math.min(getHi(ia), o.getHi(ib)));
            }
        }
        if (equalsBuffer(tmp)) {
            return (T) this;
        }
        if (o.equalsBuffer(tmp)) {
            return o;
        }
        return create(tmp);
    }

    <T extends ImmutableSortedListOfRanges> T createIntersectionSingleRange(T o);

    @SuppressWarnings("unchecked")
    default <T extends SortedListOfRanges> T subtract(T o) {
        if (o.isEmpty()) {
            return (T) this;
        }
        return subtract(o, createTempBuffer());
    }

    /**
     * Returns the result of the subtraction of {@code o} from this list. Uses
     * {@link #getBuffer1(CompilationBuffer)}.
     */
    default <T extends SortedListOfRanges> T subtract(T o, CompilationBuffer compilationBuffer) {
        return subtract(o, getBuffer1(compilationBuffer));
    }

    /**
     * Returns the result of the subtraction of {@code o} from this list.
     */
    @SuppressWarnings("unchecked")
    default <T extends SortedListOfRanges> T subtract(T o, RangesBuffer subtractionRanges) {
        if (o.isEmpty()) {
            return (T) this;
        }
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

    final class IntersectAndSubtractResult<T extends ImmutableSortedListOfRanges> {

        public final T subtractedA;
        public final T subtractedB;
        public final T intersection;

        public IntersectAndSubtractResult(T subtractedA, T subtractedB, T intersected) {
            this.subtractedA = subtractedA;
            this.subtractedB = subtractedB;
            this.intersection = intersected;
        }
    }

    /**
     * Calculates the intersection and the "rest" of this and {@code o}. Uses
     * {@link #getBuffer1(CompilationBuffer)}, {@link #getBuffer2(CompilationBuffer)} and
     * {@link #getBuffer3(CompilationBuffer)}.
     *
     * @param o MatcherBuilder to intersect with.
     * @return a new {@link IntersectAndSubtractResult}, where field {@code subtractedA} is equal to
     *         {@code this.subtract(intersection)}, {@code subtractedB} is equal to
     *         {@code o.subtract(intersection)} and {@code intersected} is equal to
     *         {@code this.createIntersection(o)}
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> IntersectAndSubtractResult<T> intersectAndSubtract(T o, CompilationBuffer compilationBuffer) {
        if (matchesNothing() || o.matchesNothing() || getMin() > o.getMax() || o.getMin() > getMax()) {
            // no intersection possible
            return new IntersectAndSubtractResult<>((T) this, o, createEmpty());
        }
        if (matchesEverything(compilationBuffer.getEncoding())) {
            return new IntersectAndSubtractResult<>(o.createInverse(compilationBuffer.getEncoding()), createEmpty(), o);
        }
        if (o.matchesEverything(compilationBuffer.getEncoding())) {
            return new IntersectAndSubtractResult<>(createEmpty(), createInverse(compilationBuffer.getEncoding()), (T) this);
        }
        if (equals(o)) {
            return new IntersectAndSubtractResult<>(createEmpty(), createEmpty(), (T) this);
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
            } else if (o.leftOf(ib, this, ia)) {
                ib++;
                if (ib >= o.size()) {
                    noIntersection = true;
                    break;
                }
            } else {
                break;
            }
        }
        if (noIntersection) {
            return new IntersectAndSubtractResult<>((T) this, o, createEmpty());
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
        while (true) {
            if (SortedListOfRanges.leftOf(raLo, raHi, rbLo, rbHi)) {
                subtractedA.appendRange(raLo, raHi);
                if (ia < size()) {
                    raLo = getLo(ia);
                    raHi = getHi(ia);
                    ia++;
                    continue;
                } else {
                    subtractedB.appendRange(rbLo, rbHi);
                    o.appendRangesTo(subtractedB, ib, o.size());
                    break;
                }
            }
            if (SortedListOfRanges.leftOf(rbLo, rbHi, raLo, raHi)) {
                subtractedB.appendRange(rbLo, rbHi);
                if (ib < o.size()) {
                    rbLo = o.getLo(ib);
                    rbHi = o.getHi(ib);
                    ib++;
                    continue;
                } else {
                    subtractedA.appendRange(raLo, raHi);
                    appendRangesTo(subtractedA, ia, size());
                    break;
                }
            }
            assert SortedListOfRanges.intersects(raLo, raHi, rbLo, rbHi);
            int intersectionLo = raLo;
            if (raLo < rbLo) {
                intersectionLo = rbLo;
                subtractedA.appendRange(raLo, intersectionLo - 1);
            } else if (raLo != rbLo) {
                subtractedB.appendRange(rbLo, intersectionLo - 1);
            }
            int intersectionHi = raHi;
            if (raHi > rbHi) {
                intersectionHi = rbHi;
                intersectionRanges.appendRange(intersectionLo, intersectionHi);
                raLo = intersectionHi + 1;
                if (ib < o.size()) {
                    rbLo = o.getLo(ib);
                    rbHi = o.getHi(ib);
                    ib++;
                } else {
                    subtractedA.appendRange(raLo, raHi);
                    appendRangesTo(subtractedA, ia, size());
                    break;
                }
            } else if (raHi < rbHi) {
                intersectionRanges.appendRange(intersectionLo, intersectionHi);
                rbLo = intersectionHi + 1;
                if (ia < size()) {
                    raLo = getLo(ia);
                    raHi = getHi(ia);
                    ia++;
                } else {
                    subtractedB.appendRange(rbLo, rbHi);
                    o.appendRangesTo(subtractedB, ib, o.size());
                    break;
                }
            } else {
                assert raHi == rbHi;
                intersectionRanges.appendRange(intersectionLo, intersectionHi);
                if (ia < size()) {
                    raLo = getLo(ia);
                    raHi = getHi(ia);
                    ia++;
                } else {
                    o.appendRangesTo(subtractedB, ib, o.size());
                    break;
                }
                if (ib < o.size()) {
                    rbLo = o.getLo(ib);
                    rbHi = o.getHi(ib);
                    ib++;
                } else {
                    subtractedA.appendRange(raLo, raHi);
                    appendRangesTo(subtractedA, ia, size());
                    break;
                }
            }
        }
        if (subtractedA.isEmpty()) {
            assert equalsBuffer(intersectionRanges);
            return new IntersectAndSubtractResult<>(createEmpty(), create(subtractedB), (T) this);
        } else if (subtractedB.isEmpty()) {
            assert o.equalsBuffer(intersectionRanges);
            return new IntersectAndSubtractResult<>(create(subtractedA), createEmpty(), o);
        } else {
            return new IntersectAndSubtractResult<>(create(subtractedA), create(subtractedB), create(intersectionRanges));
        }
    }

    /**
     * Returns the union of this list and {@code o}. Creates a temporary buffer.
     */
    @SuppressWarnings("unchecked")
    default <T extends ImmutableSortedListOfRanges> T union(T o) {
        if (o.isEmpty()) {
            return (T) this;
        }
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
        if (matchesNothing() || o.size() == 1 && o.getMin() <= getMin() && o.getMax() >= getMax()) {
            return o;
        }
        if (o.matchesNothing() || size() == 1 && getMin() <= o.getMin() && getMax() >= o.getMax()) {
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

    @Override
    default Iterator<Range> iterator() {
        return new ImmutableSortedListOfRangesIterator(this);
    }

    final class ImmutableSortedListOfRangesIterator implements Iterator<Range> {

        private final ImmutableSortedListOfRanges ranges;
        private int i = 0;

        private ImmutableSortedListOfRangesIterator(ImmutableSortedListOfRanges ranges) {
            this.ranges = ranges;
        }

        @Override
        public boolean hasNext() {
            return i < ranges.size();
        }

        @Override
        public Range next() {
            Range ret = new Range(ranges.getLo(i), ranges.getHi(i));
            i++;
            return ret;
        }
    }
}
