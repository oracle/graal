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
import com.sun.max.lang.*;

/**
 * An open addressing hash table with liner probe hash collision resolution.
 */
public class OpenAddressingHashMapping<K, V> extends HashMapping<K, V> implements Mapping<K, V> {

    // Note: this implementation is partly derived from java.util.IdentityHashMap in the standard JDK

    /**
     * The initial capacity used by the no-args constructor.
     * MUST be a power of two.  The value 32 corresponds to the
     * (specified) expected maximum size of 21, given a load factor
     * of 2/3.
     */
    private static final int DEFAULT_CAPACITY = 32;

    /**
     * The minimum capacity, used if a lower value is implicitly specified
     * by either of the constructors with arguments.  The value 4 corresponds
     * to an expected maximum size of 2, given a load factor of 2/3.
     * MUST be a power of two.
     */
    private static final int MINIMUM_CAPACITY = 4;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<29.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 29;

    /**
     * The table, resized as necessary. Length MUST always be a power of two.
     */
    private Object[] table;

    /**
     * Returns the appropriate capacity for the specified expected maximum
     * size.  Returns the smallest power of two between MINIMUM_CAPACITY
     * and MAXIMUM_CAPACITY, inclusive, that is greater than
     * (3 * expectedMaxSize)/2, if such a number exists.  Otherwise
     * returns MAXIMUM_CAPACITY.  If (3 * expectedMaxSize)/2 is negative, it
     * is assumed that overflow has occurred, and MAXIMUM_CAPACITY is returned.
     */
    private static int capacity(int expectedMaxSize) {
        // Compute minimum capacity for expectedMaxSize given a load factor of 2/3
        final int minimumCapacity = (3 * expectedMaxSize) >> 1;

        // Compute the appropriate capacity
        int result;
        if (minimumCapacity > MAXIMUM_CAPACITY || minimumCapacity < 0) {
            result = MAXIMUM_CAPACITY;
        } else {
            result = MINIMUM_CAPACITY;
            while (result < minimumCapacity) {
                result <<= 1;
            }
        }
        return result;
    }

    /**
     * Constructs a new, empty open addressing hash table with {@linkplain HashEquality equality} key semantics and a
     * default expected maximum size of 21.
     */
    public OpenAddressingHashMapping() {
        this(null);
    }

    /**
     * Constructs a new, empty open addressing hash table with a default expected maximum size of 21.
     *
     * @param equivalence
     *            the semantics to be used for comparing keys. If {@code null} is provided, then {@link HashEquality} is
     *            used.
     */
    public OpenAddressingHashMapping(HashEquivalence<K> equivalence) {
        this(equivalence, (DEFAULT_CAPACITY * 2) / 3);
    }

    /**
     * Constructs a new, empty open addressing hash table with {@linkplain HashEquality equality} key semantics.
     *
     * @param expectedMaximumSize
     *            the expected maximum size of the map
     */
    public OpenAddressingHashMapping(int expectedMaximumSize) {
        this(null, expectedMaximumSize);
    }

    /**
     * Constructs a new, empty open addressing hash table with the specified expected maximum size. Putting more than
     * the expected number of key-value mappings into the map may cause the internal data structure to grow, which may
     * be somewhat time-consuming.
     *
     * @param equivalence
     *            the semantics to be used for comparing keys. If {@code null} is provided, then {@link HashEquality} is
     *            used.
     * @param expectedMaximumSize
     *            the expected maximum size of the map
     * @throws IllegalArgumentException
     *             if {@code expectedMaximumSize} is negative
     */
    public OpenAddressingHashMapping(HashEquivalence<K> equivalence, int expectedMaximumSize) {
        super(equivalence);
        if (expectedMaximumSize < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + expectedMaximumSize);
        }

        // Find a power of 2 >= initialCapacity
        final int capacity = capacity(expectedMaximumSize);
        threshold = (capacity * 2) / 3;
        table = new Object[capacity * 2];
    }

    private int numberOfEntries;
    private int threshold;

    public int length() {
        return numberOfEntries;
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because BuckHashMapping uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits.
     */
    static int hash(int hash) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        final int h = hash ^ ((hash >>> 20) ^ (hash >>> 12));
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Returns index for hash code h.
     */
    static int indexFor(int h, int length) {
        // Multiply by -127, and left-shift to use least bit as part of hash
        final int index = ((h << 1) - (h << 8)) & (length - 1);
        assert (index & 1) == 0 : "index must be even";
        return index;
    }

    /**
     * Circularly traverses table of size {@code length}.
     */
    private static int nextKeyIndex(int i, int length) {
        return i + 2 < length ? i + 2 : 0;
    }

    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        final Object[] tbl = this.table;
        final int length = tbl.length;
        final int hash = hash(hashCode(key));
        int index = indexFor(hash, tbl.length);
        while (true) {
            final Object item = tbl[index];
            final Class<K> keyType = null;
            final K entryKey = Utils.cast(keyType, item);
            if (equivalent(entryKey, key)) {
                final Class<V> valueType = null;
                return Utils.cast(valueType, tbl[index + 1]);
            }
            if (item == null) {
                return null;
            }
            index = nextKeyIndex(index, length);
        }
    }

    public V put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        final Object[] tbl = this.table;
        final int length = tbl.length;
        final int hash = hash(hashCode(key));
        int index = indexFor(hash, tbl.length);

        Object item = tbl[index];
        while (item != null) {
            final Class<K> keyType = null;
            final K entryKey = Utils.cast(keyType, item);
            if (equivalent(entryKey, key)) {
                final Class<V> valueType = null;
                final V oldValue = Utils.cast(valueType, tbl[index + 1]);
                tbl[index + 1] = value;
                return oldValue;
            }
            index = nextKeyIndex(index, length);
            item = tbl[index];
        }

        tbl[index] = key;
        tbl[index + 1] = value;
        if (numberOfEntries++ >= threshold) {
            resize(length); // length == 2 * current capacity.
        }
        return null;

    }

    public V remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        final Object[] tbl = this.table;
        final int length = tbl.length;
        final int hash = hash(hashCode(key));
        int index = indexFor(hash, tbl.length);

        while (true) {
            final Object item = tbl[index];
            final Class<K> keyType = null;
            final K entryKey = Utils.cast(keyType, item);
            if (equivalent(entryKey, key)) {
                numberOfEntries--;
                final Class<V> valueType = null;
                final V oldValue =  Utils.cast(valueType, tbl[index + 1]);
                tbl[index + 1] = null;
                tbl[index] = null;
                return oldValue;
            }
            if (item == null) {
                return null;
            }
            index = nextKeyIndex(index, length);
        }
    }

    public void clear() {
        for (int i = 0; i != table.length; ++i) {
            table[i] = null;
        }
        numberOfEntries = 0;
    }

    /**
     * Resize the table to hold given capacity.
     *
     * @param newCapacity the new capacity, must be a power of two.
     */
    private void resize(int newCapacity) {
        assert Ints.isPowerOfTwoOrZero(newCapacity) : "newCapacity must be a power of 2";
        final int newLength = newCapacity * 2;

        final Object[] oldTable = table;
        final int oldLength = oldTable.length;
        if (oldLength == 2 * MAXIMUM_CAPACITY) { // can't expand any further
            if (threshold == MAXIMUM_CAPACITY - 1) {
                throw new IllegalStateException("Capacity exhausted.");
            }
            threshold = MAXIMUM_CAPACITY - 1;  // Gigantic map!
            return;
        }
        if (oldLength >= newLength) {
            return;
        }

        final Object[] newTable = new Object[newLength];
        threshold = newLength / 3;

        for (int i = 0; i < oldLength; i += 2) {
            final Class<K> keyType = null;
            final K key = Utils.cast(keyType, oldTable[i]);
            if (key != null) {
                final Object value = oldTable[i + 1];
                oldTable[i] = null;
                oldTable[i + 1] = null;
                final int hash = hash(hashCode(key));
                int index = indexFor(hash, newLength);
                while (newTable[index] != null) {
                    index = nextKeyIndex(index, newLength);
                }
                newTable[index] = key;
                newTable[index + 1] = value;
            }
        }
        table = newTable;
    }

    private abstract class HashIterator<Type> implements Iterator<Type> {

        /**
         * Current slot.
         */
        int index = numberOfEntries != 0 ? 0 : table.length;

        /**
         * To avoid unnecessary next computation.
         */
        boolean indexIsValid;

        public boolean hasNext() {
            for (int i = index; i < table.length; i += 2) {
                final Object key = table[i];
                if (key != null) {
                    index = i;
                    indexIsValid = true;
                    return true;
                }
            }
            index = table.length;
            return false;
        }

        protected int nextIndex() {
            if (!indexIsValid && !hasNext()) {
                throw new NoSuchElementException();
            }

            indexIsValid = false;
            final int lastReturnedIndex = index;
            index += 2;
            return lastReturnedIndex;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class KeyIterator extends HashIterator<K> {
        public K next() {
            final Class<K> keyType = null;
            return Utils.cast(keyType, table[nextIndex()]);
        }
    }

    private class ValueIterator extends HashIterator<V> {
        public V next() {
            final Class<V> valueType = null;
            return Utils.cast(valueType, table[nextIndex() + 1]);
        }
    }

    public IterableWithLength<K> keys() {
        return new HashMappingIterable<K>() {
            public Iterator<K> iterator() {
                return new KeyIterator();
            }
        };
    }

    @Override
    public IterableWithLength<V> values() {
        return new HashMappingIterable<V>() {
            public Iterator<V> iterator() {
                return new ValueIterator();
            }
        };
    }
}
