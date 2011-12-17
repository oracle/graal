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

import com.sun.max.*;

/**
 * Similar to java.util.HashMap, but with plain primitive ints as keys.
 * Unsynchronized.
 */
public class IntHashMap<T> {

    private static final int INITIAL_SIZE = 4;

    private int[] keys;
    private T[] values;
    private int numberOfValues;
    private int threshold;
    private static final float LOAD_FACTOR = 0.75f;

    public IntHashMap() {
    }

    /**
     * Constructs an empty {@code IntHashMap} with the specified initial capacity.
     *
     * @param  initialCapacity the initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public IntHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }

        // Find a power of 2 >= initialCapacity
        int capacity = Integer.highestOneBit(Math.max(initialCapacity, 1));
        if (capacity < initialCapacity) {
            capacity <<= 1;
        }

        keys = new int[capacity];
        Class<T[]> type = null;
        values = Utils.newArray(type, capacity);
        setThreshold();
    }

    /**
     * Applies a hash function to a given integer key. This is critical
     * because ArrayHashMapping uses power-of-two length hash tables, that
     * otherwise encounter collisions for integer keys that do not differ
     * in lower bits.
     */
    static int hash(int key) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        final int hash = key ^ ((key >>> 20) ^ (key >>> 12));
        return hash ^ (hash >>> 7) ^ (hash >>> 4);
    }

    /**
     * Returns index for an integer key.
     */
    static int indexFor(int key, int length) {
        return key & (length - 1);
    }

    public T get(int key) {
        if (keys == null) {
            return null;
        }
        int index = key % keys.length;
        if (index < 0) {
            index *= -1;
        }
        final int start = index;
        do {
            final T value = values[index];
            if (value == null) {
                return null;
            }
            if (keys[index] == key) {
                return values[index];
            }
            index++;
            index %= keys.length;
        } while (index != start);
        return null;
    }

    private void setThreshold() {
        assert keys.length == values.length;
        threshold = (int) (keys.length * LOAD_FACTOR);
    }

    public void grow() {
        if (keys == null) {
            keys = new int[INITIAL_SIZE];
            Class<T[]> type = null;
            values = Utils.newArray(type, INITIAL_SIZE);
            setThreshold();
        } else {
            final int[] ks = this.keys;
            final T[] vs = this.values;
            final int length = ks.length * 2;
            this.keys = new int[length];
            Class<T[]> type = null;
            this.values = Utils.newArray(type, length);
            numberOfValues = 0;
            setThreshold();
            for (int i = 0; i < ks.length; i++) {
                final T value = vs[i];
                if (value != null) {
                    put(ks[i], value);
                }
            }
        }
    }

    public T put(int key, T value) {
        assert value != null;
        if (numberOfValues >= threshold) {
            grow();
        }
        int index = key % keys.length;
        if (index < 0) {
            index *= -1;
        }
        final int start = index;
        while (values[index] != null) {
            if (keys[index] == key) {
                T oldValue = values[index];
                values[index] = value;
                return oldValue;
            }
            index++;
            index %= keys.length;
            assert index != start;
        }
        keys[index] = key;
        values[index] = value;
        numberOfValues++;
        return null;
    }

    public List<T> toList() {
        if (values == null) {
            return Collections.emptyList();
        }
        final List<T> list = new ArrayList<T>(numberOfValues);
        for (int i = 0; i < values.length; i++) {
            final T value = values[i];
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    public int count() {
        return numberOfValues;
    }
}
