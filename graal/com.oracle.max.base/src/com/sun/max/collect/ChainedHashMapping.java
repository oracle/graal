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
 * Hash table based implementation of the {@link Mapping} interface. Compared to {@link HashMap}, this data
 * structure can provide better space utilization (the {@link DefaultEntry} chained entry type has one less field than
 * the chained entry type in HashMap) and extensibility (subclasses can override how keys produce a
 * {@link #hashCode(Object) hash code} and how they are tested for {@linkplain #equivalent(Object, Object) equality}).
 * Other differences include:
 * <ul>
 * <li>{@code null} keys are illegal in ChainedHashMapping.</li>
 * <li>Adding a {@code null} value in {@link #put(Object, Object) put} is equivalent to calling
 * {@link #remove(Object) remove} with the given key.</li>
 * <li>The number of buckets used shrinks when the load (i.e. {@linkplain #length() number of entries} / {@linkplain #capacity() capacity}) falls below
 * {@value #MIN_LOAD_FACTOR} after {@link ChainedHashMapping#remove(Object) removing} an entry.</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ChainedHashMapping<K, V> extends HashMapping<K, V> implements Mapping<K, V> {

    // Note: this implementation is partly derived from java.util.HashMap in the standard JDK
    // In particular, it uses a table whose length is guaranteed to be a power of 2.

    /**
     * The default initial capacity - MUST be a power of two.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    public static final int MAXIMUM_CAPACITY = 1 << 30;

    public static final float MAX_LOAD_FACTOR = 0.75f;
    public static final float MIN_LOAD_FACTOR = 0.25f;

    /**
     * The interface for the chained entries of a bucket in a {@link ChainedHashMapping}.
     */
    public interface Entry<K, V> {
        K key();
        V value();
        void setValue(V value);
        Entry<K, V> next();
        void setNext(Entry<K, V> next);
    }

    /**
     * The default chained entry type used by {@link ChainedHashMapping}. This type of chained entry does not cache the hash value of the key. If the
     * computing this hash code is an expensive operation, then using a {@link HashEntryChainedHashMapping} may provide better performance.
     */
    public static class DefaultEntry<K, V> implements Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;

        public DefaultEntry(K key, V value, Entry<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K key() {
            return key;
        }

        public final Entry<K, V> next() {
            return next;
        }

        public void setNext(Entry<K, V> next) {
            this.next = next;
        }

        public void setValue(V value) {
            this.value = value;
        }

        public final V value() {
            return value;
        }

        @Override
        public String toString() {
            return key() + "=" + value();
        }
    }

    private Entry<K, V>[] table;

    private int numberOfEntries;
    private int growThreshold;
    private int shrinkThreshold;

    private void setThreshold() {
        growThreshold = (int) (table.length * MAX_LOAD_FACTOR);
        shrinkThreshold = (int) (table.length * MIN_LOAD_FACTOR);
    }

    /**
     * Creates a chained hash table with {@linkplain HashEquality equality} key semantics and an initial capacity of
     * {@value ChainedHashMapping#DEFAULT_INITIAL_CAPACITY} whose entries record the hash of the key.
     */
    public ChainedHashMapping() {
        this(null);
    }

    /**
     * Creates a chained hash table with a default initial capacity of {@value DEFAULT_INITIAL_CAPACITY}.
     *
     * @param equivalence
     *            the semantics of key comparison and hashing. If {@code null}, then {@link HashEquality} is used.
     */
    public ChainedHashMapping(HashEquivalence<K> equivalence) {
        this(equivalence, DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a chained hash table with {@linkplain HashEquality equality} key semantics.
     *
     * @param initialCapacity
     *            the initial capacity of the table
     */
    public ChainedHashMapping(int initialCapacity) {
        this(null, initialCapacity);
    }

    /**
     * Creates a chained hash table.
     *
     * @param equivalence
     *            the semantics of key comparison and hashing. If {@code null}, then {@link HashEquality} is used.
     * @param initialCapacity
     *            the initial capacity of the table
     */
    public ChainedHashMapping(HashEquivalence<K> equivalence, int initialCapacity) {
        super(equivalence);
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }

        // Find a power of 2 >= initialCapacity
        int capacity = Integer.highestOneBit(Math.max(Math.min(MAXIMUM_CAPACITY, initialCapacity), 1));
        if (capacity < initialCapacity) {
            capacity <<= 1;
        }

        final Class<Entry<K, V>[]> type = null;
        table = Utils.cast(type, new Entry[capacity]);
        setThreshold();
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
        return h & (length - 1);
    }

    public int length() {
        return numberOfEntries;
    }

    /**
     * Determines if a given entry matches a given key.
     * <p>
     * This method exists primarily for the benefit of subclasses who can override this implementation with a more
     * efficient one that uses {@code hashForKey}. For example, the {@linkplain HashEntryChainedHashMapping} subclass uses bucket
     * entries that record the hash of a key to avoid re-computing it every time an entry is compared.
     *
     * @param entry
     *            the entry to test
     * @param key
     *            the key to test
     * @param hashForKey
     *            the hash value for {@code key}
     */
    protected boolean matches(Entry<K, V> entry, K key, int hashForKey) {
        return equivalent(entry.key(), key);
    }

    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        final int hashForKey = hash(hashCode(key));
        final int index = indexFor(hashForKey, table.length);
        for (Entry<K, V> entry = table[index]; entry != null; entry = entry.next()) {
            if (matches(entry, key, hashForKey)) {
                return entry.value();
            }
        }
        return null;
    }

    private void resize(int newTableLength) {
        final Class<Entry<K, V>[]> type = null;
        final Entry<K, V>[] newTable = Utils.cast(type, new Entry[newTableLength]);
        transfer(newTable);
        table = newTable;
        setThreshold();
    }

    /**
     * Transfers all entries from current table to newTable.
     */
    private void transfer(Entry<K, V>[] newTable) {
        final Entry<K, V>[] src = table;
        final int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
            Entry<K, V> entry = src[j];
            if (entry != null) {
                do {
                    final Entry<K, V> next = entry.next();
                    final int hash = hash(hashCode(entry.key()));
                    final int index = indexFor(hash, newCapacity);
                    entry.setNext(newTable[index]);
                    newTable[index] = entry;
                    entry = next;
                } while (entry != null);
            }
        }
    }

    public V remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        final int hashForKey = hash(hashCode(key));
        final int index = indexFor(hashForKey, table.length);
        Entry<K, V> prev = table[index];
        Entry<K, V> entry = prev;

        while (entry != null) {
            final Entry<K, V> next = entry.next();
            if (matches(entry, key, hashForKey)) {
                if (prev == entry) {
                    table[index] = next;
                } else {
                    prev.setNext(next);
                }
                if (--numberOfEntries < shrinkThreshold) {
                    resize(table.length >> 1);
                }
                entry.setNext(null);
                return entry.value();
            }
            prev = entry;
            entry = next;
        }
        return null;
    }

    public V put(K key, V value) {
        if (value == null) {
            return remove(key);
        }
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        final int hashForKey = hash(hashCode(key));
        final int index = indexFor(hashForKey, table.length);
        for (Entry<K, V> entry = table[index]; entry != null; entry = entry.next()) {
            if (matches(entry, key, hashForKey)) {
                final V oldValue = entry.value();
                entry.setValue(value);
                return oldValue;
            }
        }

        table[index] = createEntry(hashForKey, key, value, table[index]);
        if (numberOfEntries++ >= growThreshold) {
            resize(2 * table.length);
        }
        return null;
    }

    public void clear() {
        final Class<Entry<K, V>[]> type = null;
        table = Utils.cast(type, new Entry[1]);
        numberOfEntries = 0;
        setThreshold();
    }

    protected Entry<K, V> createEntry(int hashOfKey, K key, V value, Entry<K, V> next) {
        return new DefaultEntry<K, V>(key, value, next);
    }

    protected abstract class HashIterator<Type> implements Iterator<Type> {

        /**
         * Next entry to return.
         */
        Entry<K, V> nextEntry;

        /**
         * Index at which to start searching for the next entry.
         */
        int index;

        HashIterator() {
            // advance to first entry
            if (numberOfEntries > 0) {
                final Entry<K, V>[] t = table;
                while (index < t.length && nextEntry == null) {
                    nextEntry = t[index++];
                }
            }
        }

        public final boolean hasNext() {
            return nextEntry != null;
        }

        final Entry<K, V> nextEntry() {
            final Entry<K, V> entry = nextEntry;
            if (entry == null) {
                throw new NoSuchElementException();
            }

            nextEntry = entry.next();
            if (nextEntry == null) {
                final Entry<K, V>[] t = table;
                while (index < t.length && nextEntry == null) {
                    nextEntry = t[index++];
                }
            }
            return entry;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    protected final class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value();
        }
    }

    protected final class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().key();
        }
    }

    protected final class EntryIterator extends HashIterator<Entry<K, V>> {
        public Entry<K, V> next() {
            return nextEntry();
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

    /**
     * Gets the capacity of this chained hash table. The capacity is size of the array holding the head of each entry
     * chain.
     * <p>
     * Note that this value may change over time as entries are added and removed from the table. As such, the returned
     * value should only be used for diagnostic purposes.
     */
    public int capacity() {
        return table.length;
    }

    /**
     * Computes the number of entries in the longest chain in the table. A chain longer than about 10 probably indicates
     * a poor hashing function being used for the keys.
     * <p>
     * Note that this value may change over time as entries are added and removed from the table. As such, the returned
     * value should only be used for diagnostic purposes.
     */
    public int computeLongestChain() {
        int max = 0;
        for (Entry head : table) {
            if (head != null) {
                int total = 0;
                for (Entry e = head; e != null; e = e.next()) {
                    ++total;
                }
                max = Math.max(max, total);
            }

        }
        return max;
    }
}
