/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.graph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Implements a bitmap that stores a single bit for a range of integers (0-n).
 */
public final class BitMap implements Serializable {

    public static final long serialVersionUID = 0L;

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

    public static final int DEFAULT_LENGTH = BITS_PER_WORD;

    public static int roundUpLength(int length) {
        return ((length + (BITS_PER_WORD - 1)) >> ADDRESS_BITS_PER_WORD) << ADDRESS_BITS_PER_WORD;
    }

    private int size;
    private long low;
    private long[] extra;

    /**
     * Constructs a new bit map with the {@linkplain #DEFAULT_LENGTH default length}.
     */
    public BitMap() {
        this(DEFAULT_LENGTH);
    }

    /**
     * Constructs a new bit map from a byte array encoded bit map.
     *
     * @param bitmap the bit map to convert
     */
    public BitMap(byte[] bitmap) {
        this(bitmap, 0, bitmap.length);
    }

    /**
     * Constructs a new bit map from a byte array encoded bit map.
     *
     * @param arr the byte array containing the bit map to convert
     * @param off the byte index in {@code arr} at which the bit map starts
     * @param numberOfBytes the number of bytes worth of bits to copy from {@code arr}
     */
    public BitMap(byte[] arr, int off, int numberOfBytes) {
        this(numberOfBytes * 8);
        int byteIndex = off;
        int end = off + numberOfBytes;
        assert end <= arr.length;
        while (byteIndex < end && (byteIndex - off) < 8) {
            long bite = (long) arr[byteIndex] & 0xff;
            low |= bite << ((byteIndex - off) * 8);
            byteIndex++;
        }
        if (byteIndex < end) {
            assert (byteIndex - off) == 8;
            int remBytes = end - byteIndex;
            int remWords = (remBytes + 7) / 8;
            for (int word = 0; word < remWords; word++) {
                long w = 0L;
                for (int i = 0; i < 8 && byteIndex < end; i++) {
                    long bite = (long) arr[byteIndex] & 0xff;
                    w |= bite << (i * 8);
                    byteIndex++;
                }
                extra[word] = w;
            }
        }
    }

    /**
     * Converts a {@code long} to a {@link BitMap}.
     */
    public static BitMap fromLong(long bitmap) {
        BitMap bm = new BitMap(64);
        bm.low = bitmap;
        return bm;
    }

    /**
     * Constructs a new bit map with the specified length.
     *
     * @param length the length of the bitmap
     */
    public BitMap(int length) {
        assert length >= 0;
        this.size = length;
        if (length > BITS_PER_WORD) {
            extra = new long[length >> ADDRESS_BITS_PER_WORD];
        }
    }

    /**
     * Sets the bit at the specified index.
     *
     * @param i the index of the bit to set
     */
    public void set(int i) {
        if (checkIndex(i) < BITS_PER_WORD) {
            low |= 1L << i;
        } else {
            int pos = wordIndex(i);
            int index = bitInWord(i);
            extra[pos] |= 1L << index;
        }
    }

    /**
     * Grows this bitmap to a new size, appending necessary zero bits.
     *
     * @param newLength the new length of the bitmap
     */
    public void grow(int newLength) {
        if (newLength > size) {
            // grow this bitmap to the new length
            int newSize = newLength >> ADDRESS_BITS_PER_WORD;
            if (newLength > 0) {
                if (extra == null) {
                    // extra just needs to be allocated now
                    extra = new long[newSize];
                } else {
                    if (extra.length < newSize) {
                        // extra needs to be copied
                        long[] newExtra = new long[newSize];
                        for (int i = 0; i < extra.length; i++) {
                            newExtra[i] = extra[i];
                        }
                        extra = newExtra;
                    } else {
                        // nothing to do, extra is already the right size
                    }
                }
            }
            size = newLength;
        }
    }

    private int bitInWord(int i) {
        return i & BIT_INDEX_MASK;
    }

    private int wordIndex(int i) {
        return (i >> ADDRESS_BITS_PER_WORD) - 1;
    }

    /**
     * Clears the bit at the specified index.
     * @param i the index of the bit to clear
     */
    public void clear(int i) {
        if (checkIndex(i) < BITS_PER_WORD) {
            low &= ~(1L << i);
        } else {
            int pos = wordIndex(i);
            int index = bitInWord(i);
            extra[pos] &= ~(1L << index);
        }
    }

    /**
     * Sets all the bits in this bitmap.
     */
    public void setAll() {
        low = -1;
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = -1;
            }
        }
    }

    /**
     * Clears all the bits in this bitmap.
     */
    public void clearAll() {
        low = 0;
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = 0;
            }
        }
    }

    /**
     * Gets the value of the bit at the specified index.
     *
     * @param i the index of the bit to get
     * @return {@code true} if the bit at the specified position is {@code 1}
     */
    public boolean get(int i) {
        if (checkIndex(i) < BITS_PER_WORD) {
            return ((low >> i) & 1) != 0;
        }
        int pos = wordIndex(i);
        int index = bitInWord(i);
        long bits = extra[pos];
        return ((bits >> index) & 1) != 0;
    }

    /**
     * Gets the value of the bit at the specified index, returning {@code false} if the
     * bitmap does not cover the specified index.
     *
     * @param i the index of the bit to get
     * @return {@code true} if the bit at the specified position is {@code 1}
     */
    public boolean getDefault(int i) {
        if (i < 0 || i >= size) {
            return false;
        }
        if (i < BITS_PER_WORD) {
            return ((low >> i) & 1) != 0;
        }
        int pos = wordIndex(i);
        int index = bitInWord(i);
        long bits = extra[pos];
        return ((bits >> index) & 1) != 0;
    }

    /**
     * Performs the union operation on this bitmap with the specified bitmap. That is, all bits set in either of the two
     * bitmaps will be set in this bitmap following this operation.
     *
     * @param other the other bitmap for the union operation
     */
    public void setUnion(BitMap other) {
        low |= other.low;
        if (extra != null && other.extra != null) {
            for (int i = 0; i < extra.length && i < other.extra.length; i++) {
                extra[i] |= other.extra[i];
            }
        }
    }

    /**
     * Performs the union operation on this bitmap with the specified bitmap. That is, a bit is set in this
     * bitmap if and only if it is set in both this bitmap and the specified bitmap.
     *
     * @param other the other bitmap for this operation
     * @return {@code true} if any bits were cleared as a result of this operation
     */
    public boolean setIntersect(BitMap other) {
        boolean same = true;
        long intx = low & other.low;
        if (low != intx) {
            same = false;
            low = intx;
        }
        long[] oxtra = other.extra;
        if (extra != null && oxtra != null) {
            for (int i = 0; i < extra.length; i++) {
                long a = extra[i];
                if (i < oxtra.length) {
                    // zero bits out of this map
                    long ax = a & oxtra[i];
                    if (a != ax) {
                        same = false;
                        extra[i] = ax;
                    }
                } else {
                    // this bitmap is larger than the specified bitmap; zero remaining bits
                    if (a != 0) {
                        same = false;
                        extra[i] = 0;
                    }
                }
            }
        }
        return !same;
    }

    /**
     * Gets the number of addressable bits in this bitmap.
     *
     * @return the size of this bitmap
     */
    public int size() {
        return size;
    }

    private int checkIndex(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("Index " + i + " is out of bounds (size=" + size + ")");
        }
        return i;
    }

    public void setFrom(BitMap other) {
        assert this.size == other.size : "must have same size";

        low = other.low;
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = other.extra[i];
            }
        }
    }

    public void setDifference(BitMap other) {
        assert this.size == other.size : "must have same size";

        low &= ~other.low;
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] &= ~other.extra[i];
            }
        }
    }

    public void negate() {
        low = ~low;
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = ~extra[i];
            }
        }
    }

    public boolean isSame(BitMap other) {
        if (this.size != other.size || this.low != other.low) {
            return false;
        }

        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                if (extra[i] != other.extra[i]) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns the index of the first set bit that occurs on or after a specified start index.
     * If no such bit exists then -1 is returned.
     * <p>
     * To iterate over the set bits in a {@code BitMap}, use the following loop:
     *
     * <pre>
     * for (int i = bitMap.nextSetBit(0); i &gt;= 0; i = bitMap.nextSetBit(i + 1)) {
     *     // operate on index i here
     * }
     * </pre>
     *
     * @param fromIndex the index to start checking from (inclusive)
     * @return the index of the lowest set bit between {@code [fromIndex .. size())} or -1 if there is no set bit in this range
     * @throws IndexOutOfBoundsException if the specified index is negative.
     */
    public int nextSetBit(int fromIndex) {
        return nextSetBit(fromIndex, size());
    }

    /**
     * Returns the index of the first set bit that occurs on or after a specified start index
     * and before a specified end index. If no such bit exists then -1 is returned.
     * <p>
     * To iterate over the set bits in a {@code BitMap}, use the following loop:
     *
     * <pre>
     * for (int i = bitMap.nextSetBit(0, bitMap.size()); i &gt;= 0; i = bitMap.nextSetBit(i + 1, bitMap.size())) {
     *     // operate on index i here
     * }
     * </pre>
     *
     * @param fromIndex the index to start checking from (inclusive)
     * @param toIndex the index at which to stop checking (exclusive)
     * @return the index of the lowest set bit between {@code [fromIndex .. toIndex)} or -1 if there is no set bit in this range
     * @throws IndexOutOfBoundsException if the specified index is negative.
     */
    public int nextSetBit(int fromIndex, int toIndex) {
        assert fromIndex <= size() : "index out of bounds";
        assert toIndex <= size() : "index out of bounds";
        assert fromIndex <= toIndex : "fromIndex > toIndex";

        if (fromIndex == toIndex) {
            return -1;
        }
        int fromWordIndex = wordIndex(fromIndex);
        int toWordIndex = wordIndex(toIndex - 1) + 1;
        int resultIndex = fromIndex;

        // check bits including and to the left_ of offset's position
        int pos = bitInWord(resultIndex);
        long res = map(fromWordIndex) >> pos;
        if (res != 0) {
            resultIndex += Long.numberOfTrailingZeros(res);
            assert resultIndex >= fromIndex && resultIndex < toIndex : "just checking";
            if (resultIndex < toIndex) {
                return resultIndex;
            }
            return -1;
        }
        // skip over all word length 0-bit runs
        for (fromWordIndex++; fromWordIndex < toWordIndex; fromWordIndex++) {
            res = map(fromWordIndex);
            if (res != 0) {
                // found a 1, return the offset
                resultIndex = bitIndex(fromWordIndex) + Long.numberOfTrailingZeros(res);
                assert resultIndex >= fromIndex : "just checking";
                if (resultIndex < toIndex) {
                    return resultIndex;
                }
                return -1;
            }
        }
        return -1;
    }

    private int bitIndex(int index) {
        return (index + 1) << ADDRESS_BITS_PER_WORD;
    }

    private long map(int index) {
        if (index == -1) {
            return low;
        }
        return extra[index];
    }

    private static boolean allZeros(int start, long[] arr) {
        for (int i = start; i < arr.length; i++) {
            if (arr[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if {@code obj} is
     * not {@code null} and is a {@code CiBitMap} object that has
     * exactly the same set of bits set to {@code true} as this bit
     * set.
     *
     * @param   obj   the object to compare with.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BitMap) {
            BitMap bm = (BitMap) obj;
            if (bm.low == low) {
                if (bm.extra == null) {
                    if (extra == null) {
                        // Common case
                        return true;
                    }
                    return allZeros(0, extra);
                }
                if (extra == null) {
                    return allZeros(0, bm.extra);
                }
                // both 'extra' array non null:
                int i = 0;
                int length = Math.min(extra.length, bm.extra.length);
                while (i < length) {
                    if (extra[i] != bm.extra[i]) {
                        return false;
                    }
                    i++;
                }
                if (extra.length > bm.extra.length) {
                    return allZeros(length, extra);
                }
                if (extra.length < bm.extra.length) {
                    return allZeros(length, bm.extra);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a string representation of this bit map
     * that is the same as the string returned by {@link BitSet#toString()}
     * for a bit set with the same bits set as this bit map.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(size * 2);
        sb.append('{');

        int bit = nextSetBit(0);
        if (bit != -1) {
            sb.append(bit);
            for (bit = nextSetBit(bit + 1); bit >= 0; bit = nextSetBit(bit + 1)) {
                sb.append(", ").append(bit);
            }
        }

        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(extra);
        result = prime * result + (int) (low ^ (low >>> 32));
        result = prime * result + size;
        return result;
    }

    public static int highestOneBitIndex(long value) {
        int bit = Long.numberOfTrailingZeros(Long.highestOneBit(value));
        if (bit == 64) {
            return -1;
        }
        return bit;
    }

    /**
     * Returns the number of bits set to {@code true} in this bit map.
     */
    public int cardinality() {
        int sum = Long.bitCount(low);
        if (extra != null) {
            for (long word : extra) {
                sum += Long.bitCount(word);
            }
        }
        return sum;
    }

    /**
     * Returns the "logical size" of this bit map: the index of
     * the highest set bit in the bit map plus one. Returns zero
     * if the bit map contains no set bits.
     *
     * @return  the logical size of this bit map
     */
    public int length() {
        if (extra != null) {
            for (int i = extra.length - 1; i >= 0; i--) {
                if (extra[i] != 0) {
                    return (highestOneBitIndex(extra[i]) + ((i + 1) * 64)) + 1;
                }
            }
        }
        return highestOneBitIndex(low) + 1;
    }

    /**
     * Returns a string representation of this bit map with every set bit represented as {@code '1'}
     * and every unset bit represented as {@code '0'}. The first character in the returned string represents
     * bit 0 in this bit map.
     *
     * @param length the number of bits represented in the returned string. If {@code length < 0 || length > size()},
     *            then the value of {@link #length()} is used.
     */
    public String toBinaryString(int length) {
        if (length < 0 || length > size) {
            length = length();
        }
        if (length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            sb.append(get(i) ? '1' : '0');
        }
        return sb.toString();
    }

    static final char[] hexDigits = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Returns a string representation of this bit map in hex.
     */
    public String toHexString() {
        if (size == 0) {
            return "";
        }
        int size = align(this.size, 4);
        StringBuilder sb = new StringBuilder(size / 4);
        for (int i = 0; i < size; i += 4) {
            int nibble = get(i) ? 1 : 0;
            if (get(i + 1)) {
                nibble |= 2;
            }
            if (get(i + 2)) {
                nibble |= 4;
            }
            if (get(i + 3)) {
                nibble |= 8;
            }

            sb.append(hexDigits[nibble]);
        }
        return sb.toString();
    }

    private static int align(int size, int align) {
        return (size + align - 1) & ~(align - 1);
    }

    public BitMap copy() {
        BitMap n = new BitMap(BITS_PER_WORD);
        n.low = low;
        if (extra != null) {
            n.extra = Arrays.copyOf(extra, extra.length);
        }
        n.size = size;
        return n;
    }

    /**
     * Copies this bit map into a given byte array.
     *
     * @param arr the destination
     * @param off the byte index in {@code arr} at which to start writing
     * @param numberOfBytes the number of bytes worth of bits to copy from this bit map.
     *        The number of bits copied is {@code numberOfBytes * 8}. If {@code numberOfBytes}
     *        is -1, then {@code ((size() + 7) / 8)} is used instead.
     * @return the number of bytes written to {@code arr}
     */
    public int copyTo(byte[] arr, int off, int numberOfBytes) {
        if (numberOfBytes < 0) {
            numberOfBytes = (size + 7) / 8;
        }
        for (int i = 0; i < numberOfBytes; ++i) {
            long word = low;
            int byteInWord;
            if (i >= 8) {
                int wordIndex = (i - 8) / 8;
                word = extra[wordIndex];
                byteInWord = i & 0x7;
            } else {
                byteInWord = i;
            }
            assert byteInWord < 8;
            byte b = (byte) (word >> (byteInWord * 8));
            arr[off + i] = b;
        }
        return numberOfBytes;
    }

    /**
     * Converts this bit map to a byte array. The length of the returned
     * byte array is {@code ((size() + 7) / 8)}.
     */
    public byte[] toByteArray() {
        byte[] arr = new byte[(size + 7) / 8];
        copyTo(arr, 0, arr.length);
        return arr;
    }

    /**
     * Converts this bit map to a long.
     *
     * @throws IllegalArgumentException if {@code (size() > 64)}
     */
    public long toLong() {
        if (size > 64) {
            throw new IllegalArgumentException("bit map of size " + size + " cannot be converted to long");
        }
        return low;
    }

    public boolean containsAll(BitMap other) {
        assert this.size == other.size : "must have same size";
        if ((low & other.low) != other.low) {
            return false;
        }
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                if ((extra[i] & other.extra[i]) != other.extra[i]) {
                    return false;
                }
            }
        }
        return true;
    }
}
