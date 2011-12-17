/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.collect;

import java.util.*;

import com.sun.max.lang.*;

/**
 * A compact and efficient implementation of a {@link PoolSet} for pools with 128 objects or less.
 *
 * While it's possible to create an instance of this class directly, the preferred way to create a pool set is described
 * {@linkplain PoolSet here}.
 */
public class PoolSet128<T extends PoolObject> extends PoolSet<T> {

    public static final int MAX_POOL_SIZE = 128;

    /**
     * Bit vector representation of the low part of this set. The 2^k bit indicates the presence of _pool.get(k) in this set.
     */
    private long setLow;

    /**
     * Bit vector representation of the low part of this set. The 2^k bit indicates the presence of _pool.get(k + 64) in this set.
     */
    private long setHigh;

    /**
     * Creates an empty pool set for a pool with 128 objects or less.
     */
    public PoolSet128(Pool<T> pool) {
        super(pool);
        assert pool.length() <= MAX_POOL_SIZE : pool.length() + " > " + MAX_POOL_SIZE;
    }

    @Override
    public int size() {
        return Long.bitCount(setLow) + Long.bitCount(setHigh);
    }

    @Override
    public void clear() {
        setLow = 0L;
        setHigh = 0L;
    }

    @Override
    public boolean isEmpty() {
        return setLow == 0L && setHigh == 0L;
    }

    private static int bitToSerial(long bit) {
        assert bit != 0 && Longs.isPowerOfTwoOrZero(bit);
        return Long.numberOfTrailingZeros(bit);
    }

    private static long serialToBit(int serial) {
        assert serial >= 0 && serial < MAX_POOL_SIZE;
        return 1L << serial;
    }

    @Override
    public boolean contains(T value) {
        if (value == null) {
            return false;
        }

        final int serial = value.serial();
        assert pool.get(serial) == value;
        if (serial < 64) {
            return (serialToBit(serial) & setLow) != 0;
        }
        return (serialToBit(serial - 64) & setHigh) != 0;
    }

    @Override
    public void add(T value) {
        final int serial = value.serial();
        assert pool.get(serial) == value;
        if (serial < 64) {
            setLow |= serialToBit(serial);
        } else {
            setHigh |= serialToBit(serial - 64);
        }
    }

    @Override
    public PoolSet128<T> addAll() {
        final int poolLength = pool.length();
        if (poolLength == 0) {
            return this;
        }
        if (poolLength <= 64) {
            final long highestBit = 1L << poolLength - 1;
            setLow = highestBit | (highestBit - 1);
        } else {
            final long highestBit = 1L << (poolLength - 64) - 1;
            setHigh = highestBit | (highestBit - 1);
            setLow = -1L;
        }
        assert size() == poolLength;
        return this;
    }

    @Override
    public void or(PoolSet<T> others) {
        if (others instanceof PoolSet128) {
            final PoolSet128 poolSet128 = (PoolSet128) others;
            setLow |= poolSet128.setLow;
            setHigh |= poolSet128.setHigh;
        } else {
            if (!others.isEmpty()) {
                for (T element : others) {
                    add(element);
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        if (!isEmpty()) {
            final int serial = value.serial();
            assert pool.get(serial) == value;
            final boolean present;
            if (serial < 64) {
                final long bit = serialToBit(serial);
                present = (bit & setLow) != 0;
                setLow &= ~bit;
            } else {
                final long bit = serialToBit(serial - 64);
                present = (bit & setHigh) != 0;
                setHigh &= ~bit;
            }
            return present;
        }
        return false;
    }

    @Override
    public T removeOne() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        long bit = Long.lowestOneBit(setLow);
        if (bit != 0) {
            setLow &= ~bit;
            return pool.get(bitToSerial(bit));
        }
        bit = Long.lowestOneBit(setHigh);
        assert bit != 0;
        setHigh &= ~bit;
        return pool.get(bitToSerial(bit) + 64);
    }

    @Override
    public void and(PoolSet<T> others) {
        if (others instanceof PoolSet128) {
            final PoolSet128 poolSet128 = (PoolSet128) others;
            setLow &= poolSet128.setLow;
            setHigh &= poolSet128.setHigh;
        } else {
            long oldSetLow = this.setLow;
            while (oldSetLow != 0) {
                final long bit = Long.lowestOneBit(oldSetLow);
                final int serial = bitToSerial(bit);
                oldSetLow &= ~bit;
                if (!others.contains(pool.get(serial))) {
                    this.setLow &= ~bit;
                }
            }
            long oldSetHigh = this.setHigh;
            while (oldSetHigh != 0) {
                final long bit = Long.lowestOneBit(oldSetHigh);
                final int serial = bitToSerial(bit) + 64;
                oldSetHigh &= ~bit;
                if (!others.contains(pool.get(serial))) {
                    this.setHigh &= ~bit;
                }
            }
        }
    }

    @Override
    public boolean containsAll(PoolSet<T> others) {
        if (others instanceof PoolSet128) {
            final PoolSet128 poolSet128 = (PoolSet128) others;
            return (setLow & poolSet128.setLow) == poolSet128.setLow &&
                   (setHigh & poolSet128.setHigh) == poolSet128.setHigh;
        }
        return super.containsAll(others);
    }

    @Override
    public PoolSet<T> clone() {
        final PoolSet128<T> poolSet = new PoolSet128<T>(pool);
        poolSet.setLow = setLow;
        poolSet.setHigh = setHigh;
        return poolSet;
    }

    /**
     * Gets an iterator over all the values in this set.
     */
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            private int count = size();
            private boolean inHighSet;
            private long current = setLow;
            private long currentBit = -1L;
            private long nextSetBit = Long.lowestOneBit(setLow);

            public boolean hasNext() {
                return count != 0;
            }

            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                currentBit = Long.lowestOneBit(current);
                if (currentBit == 0) {
                    assert !inHighSet;
                    inHighSet = true;
                    current = setHigh;
                    currentBit = Long.lowestOneBit(current);
                }

                final int serial = bitToSerial(currentBit);
                current &= ~currentBit;
                count--;
                if (inHighSet) {
                    return pool.get(serial + 64);
                }
                return pool.get(serial);
            }

            public void remove() {
                if (currentBit == -1L) {
                    throw new IllegalStateException();
                }
                if (inHighSet) {
                    setHigh &= ~currentBit;
                } else {
                    setLow &= ~currentBit;
                }
                currentBit = -1;
            }
        };
    }
}
