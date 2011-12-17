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

import com.sun.max.*;

/**
 * Two arrays: one holding sorted long keys,
 * the parallel other one holding lookup values corresponding to those keys.
 * Insertion/deletion moderately costly, lookup by binary search pretty fast.
 */
public class SortedLongArrayMapping<V> {

    public SortedLongArrayMapping() {
    }

    private long[] keys = {};
    private Object[] values = {};

    private int findIndex(long key) {
        int left = 0;
        int right = keys.length;
        while (right > left) {
            final int middle = left + ((right - left) >> 1);
            final long middleKey = keys[middle];
            if (middleKey == key) {
                return middle;
            } else if (middleKey > key) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }
        return left;
    }

    /**
     * Binary search in the key array.
     * @return the value corresponding to the key or null if the key is not found
     */
    public V get(long key) {
        final int index = findIndex(key);
        if (index < keys.length && keys[index] == key) {
            final Class<V> type = null;
            return Utils.cast(type, values[index]);
        }
        return null;
    }

    public void put(long key, V value) {
        final int index = findIndex(key);
        if (index < keys.length && keys[index] == key) {
            values[index] = value;
        } else {
            long[] newKeys = new long[keys.length + 1];
            System.arraycopy(keys, 0, newKeys, 0, index);
            System.arraycopy(keys, index, newKeys, index + 1, keys.length - index);
            newKeys[index] = key;
            keys = newKeys;

            Object[] newValues = new Object[values.length + 1];
            System.arraycopy(values, 0, newValues, 0, index);
            System.arraycopy(values, index, newValues, index + 1, values.length - index);
            newValues[index] = value;
            values = newValues;
        }
    }

    public void remove(long key) {
        final int index = findIndex(key);
        if (keys[index] == key) {
            int newLength = keys.length - 1;
            long[] newKeys = new long[newLength];
            System.arraycopy(keys, 0, newKeys, 0, index);
            System.arraycopy(keys, index + 1, newKeys, index, newLength - index);
            keys = newKeys;

            Object[] newValues = new Object[newLength];
            System.arraycopy(values, 0, newValues, 0, index);
            System.arraycopy(values, index + 1, newValues, index, newLength - index);
            values = newValues;
        }
    }
}
