/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.Arrays;
import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Read-only bitset designed for partial evaluation. The implementation is partially re-used from
 * {@link BitSet}.
 *
 * @see BitSet
 * @since 20.0
 */
public final class FinalBitSet {

    /**
     * An empty bit set of size 0.
     *
     * @since 20.0
     */
    public static final FinalBitSet EMPTY = new FinalBitSet(new long[0]);

    /*
     * BitSets are packed into arrays of "words." Currently a word is a long, which consists of 64
     * bits, requiring 6 address bits. The choice of word size is determined purely by performance
     * concerns.
     */
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xffffffffffffffffL;

    /**
     * The internal field corresponding to the serialField "bits".
     */
    @CompilationFinal(dimensions = 1) private long[] words;

    /**
     * Given a bit index, return word index containing it.
     */
    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    /**
     * Creates a bit set using words as the internal representation. The last word (if there is one)
     * must be non-zero.
     */
    private FinalBitSet(long[] words) {
        this.words = words;
    }

    /**
     * Returns a new long array containing all the bits in this bit set. This method is designed for
     * partial evaluation.
     *
     * <p>
     * More precisely, if <br>
     * {@code long[] longs = s.toLongArray();} <br>
     * then {@code longs.length == (s.length()+63)/64} and <br>
     * {@code s.get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)} <br>
     * for all {@code n < 64 * longs.length}.
     *
     * @return a long array containing a little-endian representation of all the bits in this bit
     *         set
     * @since 20.0
     */
    public long[] toLongArray() {
        return Arrays.copyOf(words, words.length);
    }

    /**
     * Returns the value of the bit with the specified index. The value is {@code true} if the bit
     * with the index {@code bitIndex} is currently set in this {@code FinalBitSet}; otherwise, the
     * result is {@code false}. This method is designed for partial evaluation and is guaranteed to
     * fold if the receiver and the bitIndex parameter are constant.
     *
     * @param bitIndex the bit index
     * @return the value of the bit with the specified index
     * @throws IndexOutOfBoundsException if the specified index is negative
     *
     * @since 20.0
     */
    public boolean get(int bitIndex) {
        if (bitIndex < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException(String.format("bitIndex < 0: %s", bitIndex));
        }
        int wordIndex = wordIndex(bitIndex);
        if (wordIndex >= words.length) {
            return false;
        }
        return ((words[wordIndex] & (1L << bitIndex)) != 0);
    }

    /**
     * Returns the "logical size" of this {@code FinalBitSet}: the index of the highest set bit in
     * the {@code FinalBitSet} plus one. Returns zero if the {@code FinalBitSet} contains no set
     * bits. This method is designed for partial evaluation and is guaranteed to fold if the
     * receiver is constant.
     *
     * @return the logical size of this {@code FinalBitSet}
     * @since 20.0
     */
    public int length() {
        if (words.length == 0) {
            return 0;
        }
        return BITS_PER_WORD * (words.length - 1) +
                        (BITS_PER_WORD - Long.numberOfLeadingZeros(words[words.length - 1]));
    }

    /**
     * Returns true if this {@code FinalBitSet} contains no bits that are set to {@code true}. This
     * method is designed for partial evaluation and is guaranteed to fold if the receiver is
     * constant.
     *
     * @return boolean indicating whether this {@code FinalBitSet} is empty
     * @since 20.0
     */
    public boolean isEmpty() {
        return words.length == 0;
    }

    /**
     * Compares this object against the specified object. The result is {@code true} if and only if
     * the argument is not {@code null} and is a {@code FinalBitSet} object that has exactly the
     * same set of bits set to {@code true} as this bit set. That is, for every nonnegative
     * {@code int} index {@code k},
     *
     * <pre>
     * ((BitSet) obj).get(k) == this.get(k)
     * </pre>
     *
     * must be true. The current sizes of the two bit sets are not compared.
     *
     * @param obj the object to compare with
     * @return {@code true} if the objects are the same; {@code false} otherwise
     * @see #size()
     *
     * @since 20.0
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FinalBitSet)) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        FinalBitSet set = (FinalBitSet) obj;
        if (words.length != set.words.length) {
            return false;
        }

        for (int i = 0; i < words.length; i++) {
            if (words[i] != set.words[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the hash code value for this bit set. The hash code depends only on which bits are
     * set within this {@code FinalBitSet}.
     * <p>
     * The hash code is defined to be the result of the following calculation:
     *
     * <pre>
     *  {@code
     * public int hashCode() {
     *     long h = 1234;
     *     long[] words = toLongArray();
     *     for (int i = words.length; --i >= 0; )
     *         h ^= words[i] * (i + 1);
     *     return (int)((h >> 32) ^ h);
     * }}
     * </pre>
     *
     * Note that the hash code changes if the set of bits is altered.
     *
     * @return the hash code value for this bit set
     * @since 20.0
     */
    @Override
    public int hashCode() {
        long h = 1234;
        for (int i = words.length; --i >= 0;) {
            h ^= words[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    /**
     * Returns the number of bits of space actually in use by this {@code FinalBitSet} to represent
     * bit values. The maximum element in the set is the size - 1st element. This method is designed
     * for partial evaluation and is guaranteed to fold if the receiver is constant.
     *
     * @return the number of bits currently in this bit set
     * @since 20.0
     */
    public int size() {
        return words.length * BITS_PER_WORD;
    }

    /**
     * Returns the number of bits set to {@code true} in this {@code BitSet}.
     *
     * @return the number of bits set to {@code true} in this {@code BitSet}
     * @since 20.0
     */
    public int cardinality() {
        int sum = 0;
        for (int i = 0; i < words.length; i++) {
            sum += Long.bitCount(words[i]);
        }
        return sum;
    }

    /**
     * Returns the index of the first bit that is set to {@code true} that occurs on or after the
     * specified starting index. If no such bit exists then {@code -1} is returned.
     *
     * <p>
     * To iterate over the {@code true} bits in a {@code FinalBitSet}, use the following loop:
     *
     * <pre>
     *  {@code
     * for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
     *     // operate on index i here
     *     if (i == Integer.MAX_VALUE) {
     *         break; // or (i+1) would overflow
     *     }
     * }}
     * </pre>
     *
     * @param fromIndex the index to start checking from (inclusive)
     * @return the index of the next set bit, or {@code -1} if there is no such bit
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since 20.0
     */
    public int nextSetBit(int fromIndex) {
        if (fromIndex < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }

        int u = wordIndex(fromIndex);
        if (u >= words.length) {
            return -1;
        }

        long word = words[u] & (WORD_MASK << fromIndex);
        while (true) {
            if (word != 0) {
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            }
            if (++u == words.length) {
                return -1;
            }
            word = words[u];
        }
    }

    /**
     * Returns the index of the first bit that is set to {@code false} that occurs on or after the
     * specified starting index.
     *
     * @param fromIndex the index to start checking from (inclusive)
     * @return the index of the next clear bit
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since 20.0
     */
    public int nextClearBit(int fromIndex) {
        if (fromIndex < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }

        int u = wordIndex(fromIndex);
        if (u >= words.length) {
            return fromIndex;
        }

        long word = ~words[u] & (WORD_MASK << fromIndex);
        while (true) {
            if (word != 0) {
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            }
            if (++u == words.length) {
                return words.length * BITS_PER_WORD;
            }
            word = ~words[u];
        }
    }

    /**
     * Returns a string representation of this bit set. For every index for which this
     * {@code BitSet} contains a bit in the set state, the decimal representation of that index is
     * included in the result. Such indices are listed in order from lowest to highest, separated by
     * ",&nbsp;" (a comma and a space) and surrounded by braces, resulting in the usual mathematical
     * notation for a set of integers.
     *
     * @return a string representation of this bit set
     * @see BitSet#toString()
     * @since 20.0
     */
    @Override
    @TruffleBoundary
    public String toString() {
        int numBits = (words.length > 128) ? cardinality() : words.length * BITS_PER_WORD;
        StringBuilder b = new StringBuilder(6 * numBits + 2);
        b.append('{');

        int i = nextSetBit(0);
        if (i != -1) {
            b.append(i);
            while (true) {
                if (++i < 0) {
                    break;
                }
                if ((i = nextSetBit(i)) < 0) {
                    break;
                }
                int endOfRun = nextClearBit(i);
                do {
                    b.append(", ").append(i);
                } while (++i != endOfRun);
            }
        }

        b.append('}');
        return b.toString();
    }

    /**
     * Returns a new bit set containing all the bits in the given long array.
     * <p>
     * More precisely, <br>
     * {@code FinalBitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)} <br>
     * for all {@code n < 64 * longs.length}.
     * <p>
     *
     * @param longs a long array containing a little-endian representation of a sequence of bits to
     *            be used as the initial bits of the new bit set
     * @return a {@code FinalBitSet} containing all the bits in the long array
     * @since 20.0
     */
    public static FinalBitSet valueOf(long[] longs) {
        int n;
        for (n = longs.length; n > 0 && longs[n - 1] == 0; n--) {
        }
        if (n == 0) {
            return EMPTY;
        }
        return new FinalBitSet(Arrays.copyOf(longs, n));
    }

    /**
     * Returns a new final bit set from a Java {@link BitSet} instance. The original bit set will be
     * copied.
     *
     * @since 20.0
     */
    @TruffleBoundary
    public static FinalBitSet valueOf(BitSet originalBitSet) {
        long[] array = originalBitSet.toLongArray();
        if (array.length == 0) {
            return EMPTY;
        }
        return new FinalBitSet(originalBitSet.toLongArray());
    }

}
