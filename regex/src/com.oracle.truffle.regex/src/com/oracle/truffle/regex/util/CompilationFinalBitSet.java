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
package com.oracle.truffle.regex.util;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Immutable Bit Set implementation, with a lot of code shamelessly ripped from
 * {@link java.util.BitSet}.
 */
public class CompilationFinalBitSet implements Iterable<Integer> {

    private static final CompilationFinalBitSet[] STATIC_INSTANCES = new CompilationFinalBitSet[16];

    static {
        for (int i = 0; i < STATIC_INSTANCES.length; i++) {
            STATIC_INSTANCES[i] = new CompilationFinalBitSet(new long[]{i});
        }
    }

    public static CompilationFinalBitSet valueOf(int... values) {
        assert values.length > 0;
        CompilationFinalBitSet bs = new CompilationFinalBitSet(values[values.length - 1]);
        for (int v : values) {
            bs.set(v);
        }
        return bs;
    }

    @CompilationFinal(dimensions = 1) private long[] words;

    public CompilationFinalBitSet(int nbits) {
        this.words = BitSets.createBitSetArray(nbits);
    }

    private CompilationFinalBitSet(long[] words) {
        this.words = words;
    }

    private CompilationFinalBitSet(CompilationFinalBitSet copy) {
        this.words = Arrays.copyOf(copy.words, copy.words.length);
    }

    public static CompilationFinalBitSet getEmptyInstance() {
        return STATIC_INSTANCES[0];
    }

    /**
     * Static shared instances for deduplication of common immutable bit sets.
     *
     * @param i The integer value of the static bit set's content, i.e. 0 is the empty bit set, 1
     *            has words <code>{0x0..., 0x1}</code>, 2 has <code>{0x0..., 0x2}</code>, and so on.
     */
    public static CompilationFinalBitSet getStaticInstance(int i) {
        return STATIC_INSTANCES[i];
    }

    public static int getNumberOfStaticInstances() {
        return STATIC_INSTANCES.length;
    }

    public int getStaticCacheKey() {
        for (int i = 1; i < words.length; i++) {
            if (words[i] != 0) {
                return -1;
            }
        }
        if (words.length == 0) {
            return 0;
        }
        return 0 <= words[0] && words[0] < STATIC_INSTANCES.length ? (int) words[0] : -1;
    }

    public CompilationFinalBitSet copy() {
        return new CompilationFinalBitSet(this);
    }

    public long[] toLongArray() {
        return Arrays.copyOf(words, words.length);
    }

    private void ensureCapacity(int nWords) {
        if (words.length < nWords) {
            words = Arrays.copyOf(words, Math.max(2 * words.length, nWords));
        }
    }

    public boolean isEmpty() {
        return BitSets.isEmpty(words);
    }

    public boolean isFull() {
        return BitSets.isFull(words);
    }

    public int numberOfSetBits() {
        return BitSets.size(words);
    }

    public boolean get(int b) {
        return BitSets.get(words, b);
    }

    public void set(int b) {
        ensureCapacity(BitSets.wordIndex(b) + 1);
        BitSets.set(words, b);
    }

    public void setRange(int lo, int hi) {
        ensureCapacity(BitSets.wordIndex(hi) + 1);
        BitSets.setRange(words, lo, hi);
    }

    public void clearRange(int lo, int hi) {
        ensureCapacity(BitSets.wordIndex(hi) + 1);
        BitSets.clearRange(words, lo, hi);
    }

    public void clear() {
        BitSets.clear(words);
    }

    public void clear(int index) {
        ensureCapacity(BitSets.wordIndex(index) + 1);
        BitSets.clear(words, index);
    }

    public void invert() {
        BitSets.invert(words);
    }

    public void intersect(CompilationFinalBitSet other) {
        BitSets.intersect(words, other.words);
    }

    public void subtract(CompilationFinalBitSet other) {
        BitSets.subtract(words, other.words);
    }

    public void union(CompilationFinalBitSet other) {
        ensureCapacity(other.words.length);
        BitSets.union(words, other.words);
    }

    public void union(Abstract128BitSet bs) {
        ensureCapacity(2);
        words[0] |= bs.getLo();
        words[1] |= bs.getHi();
    }

    public boolean isDisjoint(CompilationFinalBitSet other) {
        return BitSets.isDisjoint(words, other.words);
    }

    public boolean contains(CompilationFinalBitSet other) {
        return BitSets.contains(words, other.words);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CompilationFinalBitSet)) {
            return false;
        }
        CompilationFinalBitSet o = (CompilationFinalBitSet) obj;
        return BitSets.equals(words, o.words);
    }

    @Override
    public int hashCode() {
        return BitSets.hashCode(words);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return BitSets.iterator(words);
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
}
