/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.Range;

public abstract class Abstract128BitSet implements Iterable<Integer> {

    public abstract long getLo();

    public abstract long getHi();

    static long toBit(int b) {
        return 1L << b;
    }

    public boolean isEmpty() {
        return getLo() == 0L && getHi() == 0L;
    }

    public boolean isFull() {
        return getLo() == ~0L && getHi() == ~0L;
    }

    public int size() {
        return Long.bitCount(getLo()) + Long.bitCount(getHi());
    }

    public boolean get(int b) {
        return b < 128 && ((b < 64 ? getLo() : getHi()) & toBit(b)) != 0L;
    }

    public boolean intersects(Abstract128BitSet other) {
        return !isDisjoint(other);
    }

    public boolean isDisjoint(Abstract128BitSet other) {
        return ((getLo() & other.getLo()) | (getHi() & other.getHi())) == 0L;
    }

    public boolean contains(Abstract128BitSet other) {
        return (getLo() & other.getLo()) == other.getLo() && (getHi() & other.getHi()) == other.getHi();
    }

    /**
     * Compatible with {@link BitSets#hashCode(long[])}.
     */
    @Override
    public int hashCode() {
        long h = (1234 ^ getLo()) ^ (getHi() << 1);
        return (int) ((h >> 32) ^ h);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Abstract128BitSet && getLo() == ((Abstract128BitSet) obj).getLo() && getHi() == ((Abstract128BitSet) obj).getHi();
    }

    public boolean matches2CharsWith1BitDifference() {
        if (size() != 2) {
            return false;
        }
        int c1 = getMin();
        int c2 = getMax();
        return Integer.bitCount(c1 ^ c2) == 1;
    }

    public boolean isSingleRange() {
        if (isEmpty()) {
            return false;
        }
        if (getHi() == 0) {
            return isSingleRange(getLo());
        }
        if (getLo() == 0) {
            return isSingleRange(getHi());
        }
        int rangeLo = Long.numberOfTrailingZeros(getLo());
        int rangeHi = Long.numberOfLeadingZeros(getHi());
        return getLo() == ((~0L) << rangeLo) && getHi() == ((~0L) >>> rangeHi);
    }

    private static boolean isSingleRange(long bs) {
        int rangeLo = Long.numberOfTrailingZeros(bs);
        int rangeHi = Long.numberOfLeadingZeros(bs);
        long bitRange = ((~0L) << rangeLo) & ((~0L) >>> rangeHi);
        return bs == bitRange;
    }

    /**
     * Returns the minimum value currently contained in this set. Must not be called on empty sets.
     */
    public int getMin() {
        assert !isEmpty();
        return getLo() == 0L ? Long.numberOfTrailingZeros(getHi()) + 64 : Long.numberOfTrailingZeros(getLo());
    }

    /**
     * Returns the maximum value currently contained in this set. Must not be called on empty sets.
     */
    public int getMax() {
        assert !isEmpty();
        return getHi() == 0L ? 63 - Long.numberOfLeadingZeros(getLo()) : 127 - Long.numberOfLeadingZeros(getHi());
    }

    /**
     * Returns the minimum value contained in the inverse of this set. Must not be called on full
     * sets.
     */
    public int getMinInverse() {
        assert !isFull();
        return getLo() == ~0L ? Long.numberOfTrailingZeros(~getHi()) + 64 : Long.numberOfTrailingZeros(~getLo());
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new Abstract128BitSetIterator(getLo(), getHi());
    }

    static final class Abstract128BitSetIterator implements PrimitiveIterator.OfInt {

        private long curWord;
        private long nextWord;
        private int i = -1;

        Abstract128BitSetIterator(long lo, long hi) {
            this.curWord = lo;
            this.nextWord = hi;
        }

        @Override
        public boolean hasNext() {
            return (curWord | nextWord) != 0;
        }

        @Override
        public int nextInt() {
            assert hasNext();
            if (curWord == 0) {
                i = 63;
                curWord = nextWord;
                nextWord = 0;
            }
            int trailingZeros = Long.numberOfTrailingZeros(curWord);
            curWord >>>= trailingZeros;
            curWord >>>= 1;
            i += trailingZeros + 1;
            return i;
        }
    }

    public int numberOfRanges() {
        int n = 0;
        Iterator<Range> it = rangesIterator();
        while (it.hasNext()) {
            it.next();
            n++;
        }
        return n;
    }

    public Iterator<Range> rangesIterator() {
        return new Abstract128BitSetRangesIterator(getLo(), getHi());
    }

    static final class Abstract128BitSetRangesIterator implements Iterator<Range> {

        private long curWord;
        private long nextWord;
        private int lastHi = 0;

        Abstract128BitSetRangesIterator(long lo, long hi) {
            if (lo == 0) {
                lastHi = 64;
                curWord = hi;
                nextWord = 0;
            } else {
                curWord = lo;
                nextWord = hi;
            }
        }

        @Override
        public boolean hasNext() {
            return curWord != 0;
        }

        @Override
        public Range next() {
            assert curWord != 0;
            int trailingZeros = Long.numberOfTrailingZeros(curWord);
            curWord >>>= trailingZeros;
            int lo = lastHi + trailingZeros;
            int trailingOnes = Long.numberOfTrailingZeros(~curWord);
            // avoid shift by 64
            curWord >>>= trailingOnes - 1;
            curWord >>>= 1;
            int hi = lo + trailingOnes;
            lastHi = hi;
            if (curWord == 0) {
                lastHi = 64;
                curWord = nextWord;
                nextWord = 0;
                if (hi == 64 && (curWord & 1) != 0) {
                    int trailingOnesNext = Long.numberOfTrailingZeros(~curWord);
                    curWord >>>= trailingOnesNext - 1;
                    curWord >>>= 1;
                    hi += trailingOnesNext;
                    lastHi = hi;
                }
            }
            return new Range(lo, hi - 1);
        }
    }

    @TruffleBoundary
    @Override
    public Spliterator.OfInt spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED | Spliterator.NONNULL);
    }

    @TruffleBoundary
    public IntStream stream() {
        return StreamSupport.intStream(spliterator(), false);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return BitSets.toString(this);
    }

    @TruffleBoundary
    public String dumpRaw() {
        return isEmpty() ? "Immutable128BitSet.getEmpty()" : String.format("0x%016xL, 0x%016xL", getLo(), getHi());
    }
}
