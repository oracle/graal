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

import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;

/**
 * Mutable bit set of fixed size 128.
 */
public final class Mutable128BitSet extends Abstract128BitSet implements Iterable<Integer> {

    private long lo;
    private long hi;

    public Mutable128BitSet() {
    }

    public Mutable128BitSet(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public Mutable128BitSet copy() {
        return new Mutable128BitSet(lo, hi);
    }

    @Override
    public long getLo() {
        return lo;
    }

    @Override
    public long getHi() {
        return hi;
    }

    public void clear() {
        lo = 0;
        hi = 0;
    }

    public void set(int b) {
        assert b < 128;
        if (b < 64) {
            lo |= toBit(b);
        } else {
            hi |= toBit(b);
        }
    }

    public void clear(int b) {
        assert b < 128;
        if (b < 64) {
            lo &= ~toBit(b);
        } else {
            hi &= ~toBit(b);
        }
    }

    public boolean add(int b) {
        assert b < 128;
        if (b < 64) {
            long old = lo;
            lo |= toBit(b);
            return lo != old;
        } else {
            long old = hi;
            hi |= toBit(b);
            return hi != old;
        }
    }

    public boolean remove(int b) {
        assert b < 128;
        if (b < 64) {
            long old = lo;
            lo &= ~toBit(b);
            return lo != old;
        } else {
            long old = hi;
            hi &= ~toBit(b);
            return hi != old;
        }
    }

    public void setRange(int rangeLo, int rangeHi) {
        assert rangeLo < 128 && rangeHi < 128;
        long bitRangeLo = (~0L) << rangeLo;
        long bitRangeHi = (~0L) >>> (63 - (rangeHi & 0x3f));
        if (rangeLo < 64) {
            if (rangeHi < 64) {
                lo |= bitRangeLo & bitRangeHi;
            } else {
                lo |= bitRangeLo;
                hi |= bitRangeHi;
            }
        } else {
            assert rangeHi >= 64;
            hi |= bitRangeLo & bitRangeHi;
        }
    }

    public void invert() {
        lo = ~lo;
        hi = ~hi;
    }

    public void intersect(Mutable128BitSet other) {
        lo &= other.lo;
        hi &= other.hi;
    }

    public void subtract(Mutable128BitSet other) {
        lo &= ~other.lo;
        hi &= ~other.hi;
    }

    public void union(Immutable128BitSet other) {
        lo |= other.getLo();
        hi |= other.getHi();
    }

    public void union(Mutable128BitSet other) {
        lo |= other.lo;
        hi |= other.hi;
    }

    public boolean addAll(Mutable128BitSet other) {
        long oldLo = lo;
        long oldHi = hi;
        union(other);
        return lo != oldLo || hi != oldHi;
    }

    public boolean retainAll(Mutable128BitSet other) {
        long oldLo = lo;
        long oldHi = hi;
        intersect(other);
        return lo != oldLo || hi != oldHi;
    }

    public boolean removeAll(Mutable128BitSet other) {
        long oldLo = lo;
        long oldHi = hi;
        subtract(other);
        return lo != oldLo || hi != oldHi;
    }

    public Immutable128BitSet toImmutable() {
        return new Immutable128BitSet(lo, hi);
    }

    @Override
    public OfInt iterator() {
        return new Mutable128BitSetIterator(this, lo, hi);
    }

    static final class Mutable128BitSetIterator implements PrimitiveIterator.OfInt {

        private final Mutable128BitSet set;
        private long curWord;
        private long nextWord;
        private int i = -1;

        Mutable128BitSetIterator(Mutable128BitSet set, long lo, long hi) {
            this.set = set;
            this.curWord = lo;
            this.nextWord = hi;
        }

        @Override
        public boolean hasNext() {
            return curWord != 0 || nextWord != 0;
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

        @Override
        public void remove() {
            set.clear(i);
        }
    }
}
