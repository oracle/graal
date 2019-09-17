/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Immutable Bit Set implementation, with a lot of code shamelessly ripped from
 * {@link java.util.BitSet}.
 */
public class CompilationFinalBitSet implements Iterable<Integer> {

    private static final int BYTE_RANGE = 256;

    private static int wordIndex(int i) {
        return i >> 6;
    }

    public static CompilationFinalBitSet valueOf(int... values) {
        CompilationFinalBitSet bs = new CompilationFinalBitSet(BYTE_RANGE);
        for (int v : values) {
            bs.set(v);
        }
        return bs;
    }

    @CompilationFinal(dimensions = 1) private long[] words;

    public CompilationFinalBitSet(int nbits) {
        this.words = new long[wordIndex(nbits - 1) + 1];
    }

    public CompilationFinalBitSet(long[] words) {
        this.words = words;
    }

    private CompilationFinalBitSet(CompilationFinalBitSet copy) {
        this.words = Arrays.copyOf(copy.words, copy.words.length);
    }

    public CompilationFinalBitSet copy() {
        return new CompilationFinalBitSet(this);
    }

    public long[] toLongArray() {
        return Arrays.copyOf(words, words.length);
    }

    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    public int numberOfSetBits() {
        int ret = 0;
        for (long w : words) {
            ret += Long.bitCount(w);
        }
        return ret;
    }

    public boolean get(int b) {
        return wordIndex(b) < words.length && (words[wordIndex(b)] & (1L << b)) != 0;
    }

    private void ensureCapacity(int nWords) {
        if (words.length < nWords) {
            words = Arrays.copyOf(words, Math.max(2 * words.length, nWords));
        }
    }

    public void set(int b) {
        final int wordIndex = wordIndex(b);
        ensureCapacity(wordIndex + 1);
        words[wordIndex] |= 1L << b;
    }

    public void setRange(int lo, int hi) {
        int wordIndexLo = wordIndex(lo);
        int wordIndexHi = wordIndex(hi);
        ensureCapacity(wordIndexHi + 1);
        long rangeLo = (~0L) << lo;
        long rangeHi = (~0L) >>> (63 - (hi & 0x3f));
        if (wordIndexLo == wordIndexHi) {
            words[wordIndexLo] |= rangeLo & rangeHi;
            return;
        }
        words[wordIndexLo] |= rangeLo;
        for (int i = wordIndexLo + 1; i < wordIndexHi; i++) {
            words[i] = ~0L;
        }
        words[wordIndexHi] |= rangeHi;
    }

    public void clear() {
        Arrays.fill(words, 0L);
    }

    public void clear(int b) {
        final int wordIndex = wordIndex(b);
        ensureCapacity(wordIndex + 1);
        words[wordIndex] &= ~(1L << b);
    }

    public void invert() {
        for (int i = 0; i < words.length; i++) {
            words[i] = ~words[i];
        }
    }

    public void intersect(CompilationFinalBitSet other) {
        int i = 0;
        for (; i < Math.min(words.length, other.words.length); i++) {
            words[i] &= other.words[i];
        }
        for (; i < words.length; i++) {
            words[i] = 0;
        }
    }

    public void subtract(CompilationFinalBitSet other) {
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            words[i] &= ~other.words[i];
        }
    }

    public void union(CompilationFinalBitSet other) {
        ensureCapacity(other.words.length);
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            words[i] |= other.words[i];
        }
    }

    public boolean isDisjoint(CompilationFinalBitSet other) {
        for (int i = 0; i < Math.min(words.length, other.words.length); i++) {
            if ((words[i] & other.words[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CompilationFinalBitSet && Arrays.equals(words, ((CompilationFinalBitSet) obj).words));
    }

    @Override
    public int hashCode() {
        long h = 1234;
        for (int i = words.length; --i >= 0;) {
            h ^= words[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new CompilationFinalBitSetIterator();
    }

    private final class CompilationFinalBitSetIterator implements PrimitiveIterator.OfInt {

        private int wordIndex = 0;
        private byte bitIndex = 0;
        private long curWord;
        private int last;

        private CompilationFinalBitSetIterator() {
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
            clear(last);
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
        StringBuilder sb = new StringBuilder("[ ");
        int b = 0;
        while (b < BYTE_RANGE) {
            if (get(b)) {
                sb.append(DebugUtil.charToString(b));
                int seq = 0;
                while (b + 1 < BYTE_RANGE && get(b + 1)) {
                    b++;
                    seq++;
                }
                if (seq > 0) { // ABC -> [A-C]
                    sb.append('-');
                    sb.append(DebugUtil.charToString(b));
                }
                sb.append(" ");
            }
            b++;
        }
        sb.append(']');
        return sb.toString();
    }
}
