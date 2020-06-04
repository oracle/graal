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

/**
 * Immutable bit set of fixed size 128.
 */
public final class Immutable128BitSet extends Abstract128BitSet {

    public static Immutable128BitSet create(int... values) {
        long lo = 0;
        long hi = 0;
        for (int v : values) {
            assert v < 128;
            if (v >= 64) {
                hi |= 1L << v;
            } else {
                lo |= 1L << v;
            }
        }
        return new Immutable128BitSet(lo, hi);
    }

    public static Immutable128BitSet createDirect(long lo, long hi) {
        return new Immutable128BitSet(lo, hi);
    }

    private final long lo;
    private final long hi;

    Immutable128BitSet(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
    }

    @Override
    public long getLo() {
        return lo;
    }

    @Override
    public long getHi() {
        return hi;
    }

    public static Immutable128BitSet getEmpty() {
        return new Immutable128BitSet(0L, 0L);
    }

    public static Immutable128BitSet getFull() {
        return new Immutable128BitSet(~0L, ~0L);
    }

    public Immutable128BitSet set(int b) {
        assert b < 128;
        return b < 64 ? new Immutable128BitSet(lo | toBit(b), hi) : new Immutable128BitSet(lo, hi | toBit(b));
    }

    public Immutable128BitSet clear(int b) {
        assert b < 128;
        return b < 64 ? new Immutable128BitSet(lo & ~toBit(b), hi) : new Immutable128BitSet(lo, hi & ~toBit(b));
    }

    public Immutable128BitSet invert() {
        return new Immutable128BitSet(~lo, ~hi);
    }

    public Immutable128BitSet intersect(Immutable128BitSet other) {
        return new Immutable128BitSet(lo & other.lo, hi & other.hi);
    }

    public Immutable128BitSet subtract(Immutable128BitSet other) {
        return new Immutable128BitSet(lo & ~other.lo, hi & ~other.hi);
    }

    public Immutable128BitSet union(Immutable128BitSet other) {
        return new Immutable128BitSet(lo | other.lo, hi | other.hi);
    }

    public IntersectAndSubtractResult intersectAndSubtract(Immutable128BitSet o) {
        long intersectionLo = lo & o.lo;
        long intersectionHi = hi & o.hi;
        return new IntersectAndSubtractResult(
                        new Immutable128BitSet(lo & ~intersectionLo, hi & ~intersectionHi),
                        new Immutable128BitSet(o.lo & ~intersectionLo, o.hi & ~intersectionHi),
                        new Immutable128BitSet(intersectionLo, intersectionHi));
    }

    public static final class IntersectAndSubtractResult {
        public final Immutable128BitSet subtractedA;
        public final Immutable128BitSet subtractedB;
        public final Immutable128BitSet intersection;

        public IntersectAndSubtractResult(Immutable128BitSet subtractedA, Immutable128BitSet subtractedB, Immutable128BitSet intersection) {
            this.subtractedA = subtractedA;
            this.subtractedB = subtractedB;
            this.intersection = intersection;
        }
    }
}
