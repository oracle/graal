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

/**
 * A chained hash table whose {@linkplain HashEntry entries} record the hash of the key. This can provide better
 * performance improvement when the cost of computing the key's hash code is high.
 */
public class HashEntryChainedHashMapping<K, V> extends ChainedHashMapping<K, V> {

    public static class HashEntry<K, V> extends DefaultEntry<K, V> {

        final int hashOfKey;

        public HashEntry(int hashOfKey, K key, V value, Entry<K, V> next) {
            super(key, value, next);
            this.hashOfKey = hashOfKey;
        }
    }

    /**
     * Creates a chained hash table whose entries record the hash of the key.
     *
     * @param equivalence
     *            the semantics of key comparison and hashing. If {@code null}, then {@link HashEquality} is used.
     * @param initialCapacity
     *            the initial capacity of the table
     */
    public HashEntryChainedHashMapping(HashEquivalence<K> equivalence, int initialCapacity) {
        super(equivalence, initialCapacity);
    }

    /**
     * Creates a chained hash table with {@linkplain HashEquality equality} key semantics whose entries record the hash
     * of the key.
     *
     * @param initialCapacity
     *            the initial capacity of the table
     */
    public HashEntryChainedHashMapping(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Creates a chained hash table with an initial capacity of {@value ChainedHashMapping#DEFAULT_INITIAL_CAPACITY}
     * whose entries record the hash of the key.
     *
     * @param equivalence
     *            the semantics of key comparison and hashing. If {@code null}, then {@link HashEquality} is used.
     */
    public HashEntryChainedHashMapping(HashEquivalence<K> equivalence) {
        super(equivalence);
    }

    /**
     * Creates a chained hash table with {@linkplain HashEquality equality} key semantics and an initial capacity of
     * {@value ChainedHashMapping#DEFAULT_INITIAL_CAPACITY} whose entries record the hash of the key.
     */
    public HashEntryChainedHashMapping() {
        super();
    }

    @Override
    protected Entry<K, V> createEntry(int hashOfKey, K key, V value, Entry<K, V> next) {
        return new HashEntryChainedHashMapping.HashEntry<K, V>(hashOfKey, key, value, next);
    }

    @Override
    protected boolean matches(Entry<K, V> entry, K key, int hashForKey) {
        final K entryKey = entry.key();
        return entryKey == key || (hashForKey == ((HashEntryChainedHashMapping.HashEntry) entry).hashOfKey && key.equals(entryKey));
    }
}
