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

import java.util.Arrays;
import java.util.PrimitiveIterator;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BitSets {

    public static int highByte(int c) {
        return c >> Byte.SIZE;
    }

    public static int lowByte(int c) {
        return c & 0xff;
    }

    public static long[] createBitSetArray(int nbits) {
        return new long[wordIndex(nbits - 1) + 1];
    }

    public static int wordIndex(int i) {
        return i >> 6;
    }

    public static long toBit(int index) {
        return 1L << index;
    }

    public static boolean isEmpty(long[] bs) {
        for (long word : bs) {
            if (word != 0L) {
                return false;
            }
        }
        return true;
    }

    public static boolean isFull(long[] bs) {
        for (long word : bs) {
            if (word != ~0L) {
                return false;
            }
        }
        return true;
    }

    public static int size(long[] bs) {
        int ret = 0;
        for (long w : bs) {
            ret += Long.bitCount(w);
        }
        return ret;
    }

    public static boolean get(long[] bs, int index) {
        return wordIndex(index) < bs.length && (bs[wordIndex(index)] & toBit(index)) != 0;
    }

    public static void set(long[] bs, int index) {
        bs[wordIndex(index)] |= toBit(index);
    }

    public static boolean add(long[] bs, int index) {
        long old = bs[wordIndex(index)];
        set(bs, index);
        return bs[wordIndex(index)] != old;
    }

    public static void setRange(long[] bs, int lo, int hi) {
        int wordIndexLo = wordIndex(lo);
        int wordIndexHi = wordIndex(hi);
        long rangeLo = (~0L) << lo;
        long rangeHi = (~0L) >>> (63 - (hi & 0x3f));
        if (wordIndexLo == wordIndexHi) {
            bs[wordIndexLo] |= rangeLo & rangeHi;
            return;
        }
        bs[wordIndexLo] |= rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            bs[i] = ~0L;
        }
        bs[wordIndexHi] |= rangeHi;
    }

    public static void clearRange(long[] bs, int lo, int hi) {
        int wordIndexLo = wordIndex(lo);
        int wordIndexHi = wordIndex(hi);
        long rangeLo = (~0L) << lo;
        long rangeHi = (~0L) >>> (63 - (hi & 0x3f));
        if (wordIndexLo == wordIndexHi) {
            bs[wordIndexLo] &= ~(rangeLo & rangeHi);
            return;
        }
        bs[wordIndexLo] &= ~rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            bs[i] = 0L;
        }
        bs[wordIndexHi] &= ~rangeHi;
    }

    public static void clear(long[] bs) {
        Arrays.fill(bs, 0L);
    }

    public static void clear(long[] bs, int index) {
        bs[wordIndex(index)] &= ~toBit(index);
    }

    public static boolean remove(long[] bs, int index) {
        long old = bs[wordIndex(index)];
        clear(bs, index);
        return bs[wordIndex(index)] != old;
    }

    public static void invert(long[] bs) {
        for (int i = 0; i < bs.length; i++) {
            bs[i] = ~bs[i];
        }
    }

    public static long[] createInverse(long[] bs) {
        long[] ret = new long[bs.length];
        for (int i = 0; i < bs.length; i++) {
            ret[i] = ~bs[i];
        }
        return ret;
    }

    public static void intersect(long[] bs1, long[] bs2) {
        int i = 0;
        for (; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] &= bs2[i];
        }
        for (; i < bs1.length; i++) {
            bs1[i] = 0;
        }
    }

    public static int retainAll(long[] bs1, long[] bs2) {
        int size = 0;
        int i = 0;
        for (; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] &= bs2[i];
            size += Long.bitCount(bs1[i]);
        }
        for (; i < bs1.length; i++) {
            bs1[i] = 0;
        }
        return size;
    }

    public static void subtract(long[] bs1, long[] bs2) {
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] &= ~bs2[i];
        }
    }

    public static int removeAll(long[] bs1, long[] bs2) {
        int size = 0;
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] &= ~bs2[i];
            size += Long.bitCount(bs1[i]);
        }
        return size;
    }

    public static void union(long[] bs1, long[] bs2) {
        assert bs1.length >= bs2.length;
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] |= bs2[i];
        }
    }

    public static int addAll(long[] bs1, long[] bs2) {
        assert bs1.length >= bs2.length;
        int size = 0;
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            bs1[i] |= bs2[i];
            size += Long.bitCount(bs1[i]);
        }
        return size;
    }

    public static boolean isDisjoint(long[] bs1, long[] bs2) {
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            if ((bs1[i] & bs2[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean contains(long[] bs1, long[] bs2) {
        for (int i = 0; i < bs2.length; i++) {
            if (i >= bs1.length) {
                if (bs2[i] != 0) {
                    return false;
                }
            } else if ((bs1[i] & bs2[i]) != bs2[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(long[] bs1, long[] bs2) {
        if (bs1.length == bs2.length) {
            return Arrays.equals(bs1, bs2);
        }
        for (int i = 0; i < Math.min(bs1.length, bs2.length); i++) {
            if (bs1[i] != bs2[i]) {
                return false;
            }
        }
        for (int i = bs1.length; i < bs2.length; i++) {
            if (bs1[i] != 0) {
                return false;
            }
        }
        for (int i = bs2.length; i < bs1.length; i++) {
            if (bs2[i] != 0) {
                return false;
            }
        }
        return true;
    }

    public static int hashCode(long[] bs) {
        long h = 1234;
        for (int i = bs.length; --i >= 0;) {
            h ^= bs[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    public static PrimitiveIterator.OfInt iterator(long[] bs) {
        return new BitSetIterator(bs);
    }

    private static final class BitSetIterator implements PrimitiveIterator.OfInt {

        private final long[] words;
        private int wordIndex = 0;
        private byte bitIndex = 0;
        private long curWord;
        private int last;

        private BitSetIterator(long[] words) {
            this.words = words;
            if (hasNext()) {
                curWord = words[0];
            }
            findNext();
        }

        private void findNext() {
            while (curWord == 0) {
                wordIndex++;
                bitIndex = 0;
                if (hasNext()) {
                    curWord = words[wordIndex];
                } else {
                    return;
                }
            }
            assert hasNext();
            assert curWord != 0;
            int trailingZeros = Long.numberOfTrailingZeros(curWord);
            curWord >>>= trailingZeros;
            bitIndex += trailingZeros;
        }

        @Override
        public boolean hasNext() {
            return wordIndex < words.length;
        }

        @Override
        public int nextInt() {
            assert hasNext();
            last = (wordIndex * 64) + bitIndex;
            curWord >>>= 1;
            bitIndex++;
            findNext();
            return last;
        }

        @Override
        public void remove() {
            clear(words, last);
        }
    }

    @TruffleBoundary
    public static String toString(long[] bs) {
        StringBuilder sb = new StringBuilder("[ ");
        int last = -2;
        int rangeBegin = -2;
        PrimitiveIterator.OfInt it = new BitSetIterator(bs);
        while (it.hasNext()) {
            int b = it.nextInt();
            if (b != last + 1) {
                appendRange(sb, rangeBegin, last);
                rangeBegin = b;
            }
            last = b;
        }
        appendRange(sb, rangeBegin, last);
        sb.append(']');
        return sb.toString();
    }

    @TruffleBoundary
    public static String toString(Iterable<Integer> bs) {
        StringBuilder sb = new StringBuilder("[ ");
        int last = -2;
        int rangeBegin = -2;
        for (int b : bs) {
            if (b != last + 1) {
                appendRange(sb, rangeBegin, last);
                rangeBegin = b;
            }
            last = b;
        }
        appendRange(sb, rangeBegin, last);
        sb.append(']');
        return sb.toString();
    }

    @TruffleBoundary
    private static void appendRange(StringBuilder sb, int rangeBegin, int last) {
        if (rangeBegin >= 0 && rangeBegin < last) {
            sb.append(String.format("0x%02x", rangeBegin));
            if (rangeBegin + 1 < last) {
                sb.append("-");
            } else {
                sb.append(" ");
            }
        }
        if (last >= 0) {
            sb.append(String.format("0x%02x ", last));
        }
    }
}
