/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * An unordered immutable hash map based on an array mapped trie.
 *
 * Keys and values must not be null. Entries are stored as {@link Map.Entry}.
 */
final class UnorderedTrieMap<K, V> implements ImmutableMap<K, V> {

    private static final UnorderedTrieMap<?, ?> EMPTY = new UnorderedTrieMap<>(0, TrieNode.empty());

    /* Enables stricter assertions for debugging. */
    private static final boolean VERIFY = false;

    private final int size;
    private final TrieNode<K, V, Map.Entry<K, V>> root;

    static int hash(Object key) {
        return key.hashCode();
    }

    private UnorderedTrieMap(int size, TrieNode<K, V, Map.Entry<K, V>> root) {
        this.size = size;
        this.root = root;
        assert verify();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> UnorderedTrieMap<K, V> empty() {
        return (UnorderedTrieMap<K, V>) EMPTY;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(Object key) {
        return getEntry((K) key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (var entry : entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        var entry = getEntry((K) key);
        return entry == null ? null : entry.getValue();
    }

    Map.Entry<K, V> getEntry(K key) {
        var entry = root.find(key, hash(key));
        assert entry == null || entry.getKey().equals(key) : Arrays.asList(entry, key);
        return entry;
    }

    @Override
    public UnorderedTrieMap<K, V> copyAndPut(K key, V value) {
        return copyAndPutImpl(key, value, Map::entry, false);
    }

    UnorderedTrieMap<K, V> copyAndPut(K key, V value, BiFunction<K, V, Map.Entry<K, V>> entryFactory) {
        return copyAndPutImpl(key, value, entryFactory, false);
    }

    UnorderedTrieMap<K, V> copyAndPutIfAbsent(K key, V value, BiFunction<K, V, Map.Entry<K, V>> entryFactory) {
        return copyAndPutImpl(key, value, entryFactory, true);
    }

    private UnorderedTrieMap<K, V> copyAndPutImpl(K key, V value, BiFunction<K, V, Map.Entry<K, V>> entryFactory, boolean ifAbsent) {
        int hash = hash(key);
        Map.Entry<K, V> existing = root.find(key, hash);
        var newRoot = root;
        final int newSize;
        final Map.Entry<K, V> newEntry;
        if (existing == null) {
            newSize = size + 1;
            // inserting a new entry
            newEntry = newEntry(key, value, entryFactory);
        } else {
            V existingValue = existing.getValue();
            // null values are treated as absent (handles weakly referenced values)
            if (existingValue != null && (ifAbsent || existingValue.equals(value))) {
                return this;
            } else {
                // replacing an existing entry
                newSize = size;
                newEntry = newEntry(key, value, entryFactory);
                assert !newEntry.equals(existing) : Arrays.asList(newEntry, existing);
            }
        }
        newRoot = newRoot.put(key, hash, newEntry);
        return new UnorderedTrieMap<>(newSize, newRoot);
    }

    private Map.Entry<K, V> newEntry(K key, V newValue, BiFunction<K, V, Map.Entry<K, V>> entryFactory) {
        return entryFactory.apply(key, newValue);
    }

    @Override
    public UnorderedTrieMap<K, V> copyAndRemove(K key) {
        int hash = hash(key);
        var existing = root.find(key, hash);
        if (existing == null) {
            return this;
        } else {
            if (size == 1) {
                return empty();
            }
            var newRoot = root;
            newRoot = newRoot.remove(key, hash);
            return new UnorderedTrieMap<>(size - 1, newRoot);
        }
    }

    UnorderedTrieMap<K, V> copyAndRemoveEntry(Map.Entry<K, V> entry) {
        K key = entry.getKey();
        int hash = hash(key);
        var existing = root.find(key, hash);
        if (existing != entry) {
            return this;
        } else {
            if (size == 1) {
                return empty();
            }
            var newRoot = root;
            newRoot = newRoot.remove(key, hash);
            return new UnorderedTrieMap<>(size - 1, newRoot);
        }
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        forEachEntry(new Consumer<Entry<K, V>>() {
            @Override
            public void accept(Entry<K, V> e) {
                action.accept(e.getKey(), e.getValue());
            }
        });
    }

    void forEachEntry(Consumer<? super Map.Entry<K, V>> consumer) {
        root.forEachEntry(consumer);
    }

    Iterator<Map.Entry<K, V>> entryIterator() {
        return root.entryIterator();
    }

    Iterator<K> keyIterator() {
        return new Iterator<>() {
            private final Iterator<Map.Entry<K, V>> entryIterator = entryIterator();

            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public K next() {
                return entryIterator.next().getKey();
            }
        };
    }

    Iterator<V> valueIterator() {
        return new Iterator<>() {
            private final Iterator<Map.Entry<K, V>> entryIterator = entryIterator();

            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public V next() {
                return entryIterator.next().getValue();
            }
        };
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return UnorderedTrieMap.this.entryIterator();
            }

            @Override
            public int size() {
                return UnorderedTrieMap.this.size();
            }
        };
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<K> iterator() {
                return UnorderedTrieMap.this.keyIterator();
            }

            @Override
            public int size() {
                return UnorderedTrieMap.this.size();
            }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractSet<>() {
            @Override
            public Iterator<V> iterator() {
                return UnorderedTrieMap.this.valueIterator();
            }

            @Override
            public int size() {
                return UnorderedTrieMap.this.size();
            }
        };
    }

    @Override
    public String toString() {
        return entrySet().toString();
    }

    private boolean verify() {
        assert size >= 0;
        assert root != null;
        if (VERIFY) {
            assert root.count() == size : root.count() + " != " + size;
            assert root.verify(0);
            int count = 0;
            for (var iterator = entrySet().iterator(); iterator.hasNext();) {
                var e = iterator.next();
                assert e == getEntry(e.getKey());
                count++;
            }
            assert count == size : count + " != " + size;
        }
        return true;
    }
}
