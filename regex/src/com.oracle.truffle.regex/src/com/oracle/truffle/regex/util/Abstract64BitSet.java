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

public final class Abstract64BitSet {

    private static long toBit(int b) {
        return 1L << b;
    }

    public static boolean isEmpty(long bs) {
        return bs == 0;
    }

    public static boolean isFull(long bs) {
        return bs == ~0L;
    }

    public static int size(long bs) {
        return Long.bitCount(bs);
    }

    public static boolean get(long bs, int b) {
        return b < 64 && (bs & toBit(b)) != 0;
    }

    public static long set(long bs, int b) {
        assert b < 64;
        return bs | toBit(b);
    }

    public static long clear(long bs, int b) {
        assert b < 64;
        return bs & ~toBit(b);
    }

    public static boolean intersects(long bs1, long bs2) {
        return !isDisjoint(bs1, bs2);
    }

    public static boolean isDisjoint(long bs1, long bs2) {
        return (bs1 & bs2) == 0;
    }

    public static boolean contains(long bs1, long bs2) {
        return (bs1 & bs2) == bs2;
    }

    /**
     * Compatible with {@link BitSets#hashCode(long[])}.
     */
    public static int hashCode(long bs) {
        long h = 1234 ^ bs;
        return (int) ((h >> 32) ^ h);
    }

    public static boolean equals(long bs1, long bs2) {
        return bs1 == bs2;
    }

    public static PrimitiveIterator.OfInt iterator(long bs) {
        return new Abstract64BitSetIterator(bs);
    }

    private static final class Abstract64BitSetIterator implements PrimitiveIterator.OfInt {

        private long bs;
        private int i = -1;

        Abstract64BitSetIterator(long bs) {
            this.bs = bs;
        }

        @Override
        public boolean hasNext() {
            return bs != 0;
        }

        @Override
        public int nextInt() {
            assert hasNext();
            int trailingZeros = Long.numberOfTrailingZeros(bs);
            bs >>>= trailingZeros;
            bs >>>= 1;
            i += trailingZeros + 1;
            return i;
        }
    }
}
