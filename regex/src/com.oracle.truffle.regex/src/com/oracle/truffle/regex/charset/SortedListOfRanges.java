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
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

public abstract class SortedListOfRanges implements ListOfRanges, CharacterSet {

    protected abstract <T extends SortedListOfRanges> T createEmpty();

    protected abstract <T extends SortedListOfRanges> T createFull();

    protected abstract <T extends SortedListOfRanges> T create(RangesBuffer buffer);

    protected abstract int getMinValue();

    protected abstract int getMaxValue();

    protected abstract RangesBuffer getBuffer1(CompilationBuffer compilationBuffer);

    protected abstract RangesBuffer getBuffer2(CompilationBuffer compilationBuffer);

    protected abstract RangesBuffer getBuffer3(CompilationBuffer compilationBuffer);

    protected abstract RangesBuffer createTempBuffer();

    protected abstract void addRangeBulkTo(RangesBuffer buffer, int startIndex, int endIndex);

    protected abstract boolean equalsBuffer(RangesBuffer buffer);

    protected boolean isSingle(int ia) {
        return getLo(ia) == getHi(ia);
    }

    protected int size(int ia) {
        return (getHi(ia) - getLo(ia)) + 1;
    }

    protected static boolean contains(int aLo, int aHi, int bLo, int bHi) {
        return aLo <= bLo && aHi >= bHi;
    }

    protected boolean contains(int ia, SortedListOfRanges o, int ib) {
        return contains(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    protected boolean contains(int ia, int bLo, int bHi) {
        return contains(getLo(ia), getHi(ia), bLo, bHi);
    }

    protected boolean containedBy(int ia, int bLo, int bHi) {
        return contains(bLo, bHi, getLo(ia), getHi(ia));
    }

    protected static boolean intersects(int aLo, int aHi, int bLo, int bHi) {
        return aLo <= bHi && bLo <= aHi;
    }

    protected boolean intersects(int ia, SortedListOfRanges o, int ib) {
        return intersects(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    protected boolean intersects(int ia, int bLo, int bHi) {
        return intersects(getLo(ia), getHi(ia), bLo, bHi);
    }

    @SuppressWarnings("unused")
    protected static boolean leftOf(int aLo, int aHi, int bLo, int bHi) {
        return aHi < bLo;
    }

    protected static boolean leftOf(int aHi, int bLo) {
        return aHi < bLo;
    }

    protected boolean leftOf(int ia, SortedListOfRanges o, int ib) {
        return leftOf(getHi(ia), o.getLo(ib));
    }

    @SuppressWarnings("unused")
    protected boolean leftOf(int ia, int oLo, int oHi) {
        return leftOf(getHi(ia), oLo);
    }

    @SuppressWarnings("unused")
    protected static boolean rightOf(int aLo, int aHi, int bLo, int bHi) {
        return aLo > bHi;
    }

    protected boolean rightOf(int ia, SortedListOfRanges o, int ib) {
        return rightOf(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    protected boolean rightOf(int ia, int bLo, int bHi) {
        return rightOf(getLo(ia), getHi(ia), bLo, bHi);
    }

    protected static boolean adjacent(int aLo, int aHi, int bLo, int bHi) {
        return aHi + 1 == bLo || aLo - 1 == bHi;
    }

    protected boolean adjacent(int ia, SortedListOfRanges o, int ib) {
        return adjacent(getLo(ia), getHi(ia), o.getLo(ib), o.getHi(ib));
    }

    protected boolean adjacent(int ia, int bLo, int bHi) {
        return adjacent(getLo(ia), getHi(ia), bLo, bHi);
    }

    protected boolean equal(int ia, ListOfRanges o, int ib) {
        return equal(ia, o.getLo(ib), o.getHi(ib));
    }

    protected boolean equal(int ia, int oLo, int oHi) {
        return getLo(ia) == oLo && getHi(ia) == oHi;
    }

    protected void intersect(int ia, SortedListOfRanges o, int ib, RangesBuffer result) {
        assert intersects(ia, o, ib);
        result.addRange(Math.max(getLo(ia), o.getLo(ib)), Math.min(getHi(ia), o.getHi(ib)));
    }

    protected int binarySearch(int key) {
        int low = 0;
        int high = size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = getLo(mid);
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

    protected boolean binarySearchExactMatch(int searchResult, SortedListOfRanges o, int oI) {
        return binarySearchExactMatch(searchResult, o.getLo(oI), o.getHi(oI));
    }

    protected boolean binarySearchExactMatch(int searchResult, int oLo, int oHi) {
        return searchResult >= 0 && equal(searchResult, oLo, oHi);
    }

    protected int binarySearchGetFirstIntersecting(int searchResult, SortedListOfRanges o, int oI) {
        assert o.rangesAreSortedAndDisjoint();
        return binarySearchGetFirstIntersecting(searchResult, o.getLo(oI), o.getHi(oI));
    }

    protected int binarySearchGetFirstIntersecting(int searchResult, int oLo, int oHi) {
        if (searchResult >= 0) {
            assert !equal(searchResult, oLo, oHi);
            return searchResult;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        if (insertionPoint > 0 && intersects(insertionPoint - 1, oLo, oHi)) {
            return insertionPoint - 1;
        }
        return insertionPoint;
    }

    protected boolean binarySearchNoIntersectingFound(int firstIntersecting) {
        return firstIntersecting == size();
    }

    protected void addRangeTo(RangesBuffer buffer, int i) {
        buffer.addRange(getLo(i), getHi(i));
    }

    protected boolean rangesAreSortedAndDisjoint() {
        for (int i = 1; i < size(); i++) {
            if ((!leftOf(i - 1, this, i)) || intersects(i - 1, this, i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(int codePoint) {
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

    public boolean contains(SortedListOfRanges o) {
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

    @SuppressWarnings("unchecked")
    public <T extends SortedListOfRanges> T createIntersection(T o, CompilationBuffer compilationBuffer) {
        RangesBuffer intersectionRanges = getBuffer1(compilationBuffer);
        for (int ia = 0; ia < size(); ia++) {
            int search = o.binarySearch(getLo(ia));
            if (o.binarySearchExactMatch(search, this, ia)) {
                addRangeTo(intersectionRanges, ia);
                continue;
            }
            int firstIntersection = o.binarySearchGetFirstIntersecting(search, this, ia);
            for (int ib = firstIntersection; ib < o.size(); ib++) {
                if (o.rightOf(ib, this, ia)) {
                    break;
                }
                intersect(ia, o, ib, intersectionRanges);
            }
        }
        if (equalsBuffer(intersectionRanges)) {
            return (T) this;
        }
        if (o.equalsBuffer(intersectionRanges)) {
            return o;
        }
        return create(intersectionRanges);
    }

    public <T extends SortedListOfRanges> T createInverse() {
        return createInverse(createTempBuffer());
    }

    public <T extends SortedListOfRanges> T createInverse(CompilationBuffer compilationBuffer) {
        return createInverse(getBuffer1(compilationBuffer));
    }

    private <T extends SortedListOfRanges> T createInverse(RangesBuffer invRanges) {
        if (matchesNothing()) {
            return createFull();
        }
        if (getLo(0) > getMinValue()) {
            invRanges.addRange(getMinValue(), getLo(0) - 1);
        }
        for (int ia = 1; ia < size(); ia++) {
            invRanges.addRange(getHi(ia - 1) + 1, getLo(ia) - 1);
        }
        if (getHi(size() - 1) < getMaxValue()) {
            invRanges.addRange(getHi(size() - 1) + 1, getMaxValue());
        }
        return create(invRanges);
    }

    @SuppressWarnings("unchecked")
    public <T extends SortedListOfRanges> T subtract(T o, CompilationBuffer compilationBuffer) {
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
                        subtractionRanges.addRange(tmpLo, o.getLo(ib) - 1);
                        tmpLo = o.getHi(ib) + 1;
                    } else if (tmpLo < o.getLo(ib)) {
                        tmpHi = o.getLo(ib) - 1;
                    } else {
                        tmpLo = o.getHi(ib) + 1;
                    }
                }
            }
            if (rest) {
                subtractionRanges.addRange(tmpLo, tmpHi);
            }
        }
        if (unchanged) {
            assert equalsBuffer(subtractionRanges);
            return (T) this;
        }
        return create(subtractionRanges);
    }

    /**
     * Calculates the intersection and the "rest" of this and another {@link SortedListOfRanges}.
     *
     * @param o MatcherBuilder to intersect with.
     * @param result Array of results, where index 0 is equal to this.subtract(intersection), index
     *            1 is equal to o.subtract(intersection) and index 2 is equal to
     *            this.createIntersection(o).
     */
    @SuppressWarnings("unchecked")
    public <T extends SortedListOfRanges> void intersectAndSubtract(T o, CompilationBuffer compilationBuffer, T[] result) {
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
        addRangeBulkTo(subtractedA, 0, ia);
        o.addRangeBulkTo(subtractedB, 0, ib);
        int raLo = getLo(ia);
        int raHi = getHi(ia);
        int rbLo = o.getLo(ib);
        int rbHi = o.getHi(ib);
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
            int intersectionLo = Math.max(raLo, rbLo);
            int intersectionHi = Math.min(raHi, rbHi);
            intersectionRanges.addRange(intersectionLo, intersectionHi);
            if (raLo < intersectionLo) {
                subtractedA.addRange(raLo, intersectionLo - 1);
            }
            if (raHi > intersectionHi) {
                raLo = intersectionHi + 1;
            } else {
                advanceA = true;
            }
            if (rbLo < intersectionLo) {
                subtractedB.addRange(rbLo, intersectionLo - 1);
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

    public <T extends SortedListOfRanges> T union(T o) {
        return union(o, createTempBuffer());
    }

    public <T extends SortedListOfRanges> T union(T o, CompilationBuffer compilationBuffer) {
        return union(o, getBuffer1(compilationBuffer));
    }

    @SuppressWarnings("unchecked")
    public <T extends SortedListOfRanges> T union(T o, RangesBuffer unionRanges) {
        if (matchesNothing() || o.matchesEverything()) {
            return o;
        }
        if (matchesEverything() || o.matchesNothing()) {
            return (T) this;
        }
        int tmpLo;
        int tmpHi;
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
                tmpLo = Math.min(getLo(ia), o.getLo(ib));
                tmpHi = Math.max(getHi(ia), o.getHi(ib));
                ia++;
                ib++;
                while (true) {
                    if (ia < size() && (intersects(ia, tmpLo, tmpHi) || adjacent(ia, tmpLo, tmpHi))) {
                        tmpLo = Math.min(getLo(ia), tmpLo);
                        tmpHi = Math.max(getHi(ia), tmpHi);
                        ia++;
                    } else if (ib < o.size() && (o.intersects(ib, tmpLo, tmpHi) || o.adjacent(ib, tmpLo, tmpHi))) {
                        tmpLo = Math.min(o.getLo(ib), tmpLo);
                        tmpHi = Math.max(o.getHi(ib), tmpHi);
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
        if (equalsBuffer(unionRanges)) {
            return (T) this;
        }
        if (o.equalsBuffer(unionRanges)) {
            return o;
        }
        return create(unionRanges);
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
            charSize += size(i);
        }
        return charSize;
    }

    public int inverseCharCount() {
        return getMaxValue() + 1 - charCount();
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
        for (int j = lastHi + 1; j <= getMaxValue(); j++) {
            array[index++] = (char) j;
        }
        return array;
    }

    public boolean matchesEverything() {
        // ranges should be consolidated to one
        return size() == 1 && getLo(0) == getMinValue() && getHi(0) == getMaxValue();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ListOfRanges && equals((ListOfRanges) obj);
    }

    public boolean equals(ListOfRanges o) {
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

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size(); i++) {
            result = 31 * result + getLo(i);
            result = 31 * result + getHi(i);
        }
        return result;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return toString(true);
    }

    @TruffleBoundary
    protected String toString(boolean addBrackets) {
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
        SortedListOfRanges inverse = createInverse(new CompilationBuffer());
        if (inverse.size() < size()) {
            return "[^" + inverse.toString(false) + "]";
        }
        if (addBrackets) {
            return "[" + rangesToString() + "]";
        } else {
            return rangesToString();
        }
    }

    @TruffleBoundary
    public static String rangeToString(int lo, int hi) {
        if (lo == hi) {
            return DebugUtil.charToString(lo);
        }
        return DebugUtil.charToString(lo) + "-" + DebugUtil.charToString(hi);
    }

    @TruffleBoundary
    private String rangesToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            sb.append(rangeToString(getLo(i), getHi(i)));
        }
        return sb.toString();
    }
}
