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
 * A compact and efficient implementation of a {@link PoolSet} for pools with 64 objects or less.
 *
 * While it's possible to create an instance of this class directly, the preferred way to create a pool set is described
 * {@linkplain PoolSet here}.
 */
public class PoolSet64<T extends PoolObject> extends PoolSet<T> {

    public static final int MAX_POOL_SIZE = 64;

    /**
     * Bit vector representation of this set. The 2^k bit indicates the presence of _pool.get(k) in this set.
     */
    private long set;

    /**
     * Creates an empty pool set for a pool with 64objects or less.
     */
    public PoolSet64(Pool<T> pool) {
        super(pool);
        assert pool.length() <= MAX_POOL_SIZE : pool.length() + " > " + MAX_POOL_SIZE;
    }

    @Override
    public int size() {
        return Long.bitCount(set);
    }

    @Override
    public void clear() {
        set = 0L;
    }

    @Override
    public boolean isEmpty() {
        return set == 0L;
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
        return (serialToBit(serial) & set) != 0;
    }

    @Override
    public void add(T value) {
        final int serial = value.serial();
        assert pool.get(serial) == value;
        set |= serialToBit(serial);
    }

    @Override
    public PoolSet64<T> addAll() {
        final int poolLength = pool.length();
        if (poolLength != 0) {
            final long highestBit = 1L << poolLength - 1;
            set = highestBit | (highestBit - 1);
            assert size() == poolLength;
        }
        return this;
    }

    @Override
    public void or(PoolSet<T> others) {
        if (others instanceof PoolSet64) {
            final PoolSet64 poolSet64 = (PoolSet64) others;
            set |= poolSet64.set;
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
            final long bit = serialToBit(serial);
            final boolean present = (set & bit) != 0;
            set &= ~bit;
            return present;
        }
        return false;
    }

    @Override
    public T removeOne() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        final long bit = Long.lowestOneBit(set);
        set &= ~bit;
        return pool.get(bitToSerial(bit));
    }

    @Override
    public void and(PoolSet<T> others) {
        if (others instanceof PoolSet64) {
            final PoolSet64 poolSet64 = (PoolSet64) others;
            set &= poolSet64.set;
        } else {
            long oldSet = this.set;
            while (oldSet != 0) {
                final long bit = Long.lowestOneBit(oldSet);
                final int serial = bitToSerial(bit);
                oldSet &= ~bit;
                if (!others.contains(pool.get(serial))) {
                    this.set &= ~bit;
                }
            }
        }
    }

    @Override
    public boolean containsAll(PoolSet<T> others) {
        if (others instanceof PoolSet64) {
            final PoolSet64 poolSet64 = (PoolSet64) others;
            return (set & poolSet64.set) == poolSet64.set;
        }
        return super.containsAll(others);
    }

    @Override
    public PoolSet<T> clone() {
        final PoolSet64<T> clone = new PoolSet64<T>(pool);
        clone.set = set;
        return clone;
    }

    /**
     * Gets an iterator over all the values in this set.
     */
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            private long current = set;
            private long currentBit = -1L;
            private long nextSetBit = Long.lowestOneBit(set);

            public boolean hasNext() {
                return current != 0;
            }

            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                currentBit = Long.lowestOneBit(current);
                final int serial = bitToSerial(currentBit);
                current &= ~currentBit;
                return pool.get(serial);
            }

            public void remove() {
                if (currentBit == -1L) {
                    throw new IllegalStateException();
                }

                set &= ~currentBit;
                currentBit = -1;
            }
        };
    }
}
