/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

/**
 * A {@link Map} backed by an {@link EconomicMap}. The map preserves the insertion order of entries.
 * This collection can be used as a replacement for {@link java.util.HashMap},
 * {@link java.util.LinkedHashMap}, and {@link java.util.IdentityHashMap} with a low footprint and
 * stable iteration order.
 * <p>
 * Since the implementation aims for compatibility with {@link java.util.HashMap}, it supports
 * {@code null} keys and throws {@link ConcurrentModificationException} when invalid iterator usage
 * is detected. However, the API of {@link java.util.HashMap} is more flexible than the one of
 * {@link EconomicMap}, so there are minor restrictions on how this map can be used:
 * <ul>
 * <li>Only the last map entry returned by an iterator can be modified using
 * {@link Map.Entry#setValue}. If the caller attempts to set the value of a non-last entry, the
 * implementation throws {@link UnsupportedOperationException}. Setting the entry value also
 * invalidates all other iterators and the value getters and setters of their entries
 * ({@link Map.Entry#getValue} and {@link Map.Entry#setValue}). If the caller attempts to perform an
 * invalid operation, {@link ConcurrentModificationException} is thrown.</li>
 * <li>Operations that add or remove map entries (e.g., {@link #put}, {@link #remove},
 * {@link #clear}) invalidate the value getters and setters of all other entries
 * ({@link Map.Entry#getValue} and {@link Map.Entry#setValue}). If the caller attempts to perform an
 * invalid operation, {@link ConcurrentModificationException} is thrown.</li>
 * </ul>
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class EconomicHashMap<K, V> extends AbstractMap<K, V> {
    /**
     * Creates a new empty hash map that compares objects with {@code ==} and uses the
     * {@link System#identityHashCode}. The map preserves the insertion order of entries.
     * <p>
     * This collection is a {@link java.util.IdentityHashMap} replacement with a stable iteration
     * order.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return a new empty hash map
     */
    public static <K, V> Map<K, V> newIdentityMap() {
        return new EconomicHashMap<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
    }

    /**
     * The {@link EconomicMap} backing this {@link Map}.
     */
    private final EconomicMap<K, V> map;

    /**
     * The number of method calls that potentially performed a modification of the map (added
     * entries, removed entries, or changed values of entries).
     */
    private int modificationCount;

    /**
     * Constructs a new empty hash map.
     */
    public EconomicHashMap() {
        map = EconomicMap.create();
    }

    /**
     * Constructs a new empty hash map with an initial capacity.
     *
     * @param initialCapacity the initial capacity of the map
     */
    public EconomicHashMap(int initialCapacity) {
        map = EconomicMap.create(initialCapacity);
    }

    /**
     * Constructs a new hash map using the entries of another map.
     *
     * @param entries the entries to place in the map
     */
    @SuppressWarnings("this-escape")
    public EconomicHashMap(Map<? extends K, ? extends V> entries) {
        map = EconomicMap.create(entries.size());
        putAll(entries);
    }

    /**
     * Constructs a new empty hash map with an equivalence strategy.
     *
     * @param equivalence the equivalence strategy used by the map
     */
    public EconomicHashMap(Equivalence equivalence) {
        map = EconomicMap.create(equivalence);
    }

    /**
     * Constructs a new empty hash map with an equivalence strategy and an initial capacity.
     *
     * @param equivalence the equivalence strategy used by the map
     * @param initialCapacity the initial capacity of the map
     */
    public EconomicHashMap(Equivalence equivalence, int initialCapacity) {
        map = EconomicMap.create(equivalence, initialCapacity);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public V put(K key, V value) {
        ++modificationCount;
        return map.put(asEconomicMapKey(key), value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        return map.containsKey(asEconomicMapKey((K) key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        return map.get(asEconomicMapKey((K) key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        ++modificationCount;
        return map.removeKey(asEconomicMapKey((K) key));
    }

    @Override
    public void clear() {
        ++modificationCount;
        map.clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean contains(Object o) {
                if (o instanceof Map.Entry<?, ?> e) {
                    K key = asEconomicMapKey((K) e.getKey());
                    if (!map.containsKey(key)) {
                        return false;
                    }
                    return Objects.equals(map.get(key), e.getValue());
                } else {
                    return false;
                }
            }

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new BridgeIterator();
            }

            @Override
            public boolean add(Entry<K, V> e) {
                ++modificationCount;
                K key = asEconomicMapKey(e.getKey());
                boolean changed = !map.containsKey(key);
                map.put(key, e.getValue());
                return changed;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean remove(Object o) {
                ++modificationCount;
                if (o instanceof Map.Entry<?, ?> e) {
                    K key = asEconomicMapKey((K) e.getKey());
                    if (!map.containsKey(key) || !Objects.equals(map.get(key), e.getValue())) {
                        return false;
                    }
                    map.removeKey(key);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void clear() {
                ++modificationCount;
                map.clear();
            }
        };
    }

    /**
     * An iterator over the entries of the map backed by a {@link MapCursor}.
     */
    private final class BridgeIterator implements Iterator<Entry<K, V>> {
        /**
         * A mutable map entry associated with an iterator.
         */
        private final class BridgeEntry implements Entry<K, V> {
            /**
             * The key. Map modification cannot change a key.
             */
            private final K key;

            /**
             * The value. The value may become stale after a map is modified.
             */
            private V value;

            private BridgeEntry(K key, V value) {
                assert key != NullMarker.class : "the key must be null instead of NullMarker.class";
                this.key = key;
                this.value = value;
            }

            @Override
            public K getKey() {
                return key;
            }

            @Override
            public V getValue() {
                checkModificationCount();
                return value;
            }

            @Override
            public V setValue(V newValue) {
                if (currentEntry != this) {
                    throw new UnsupportedOperationException("only the latest entry instance can be modified");
                }
                // Cursor may be invalid after a modification.
                checkModificationCount();
                // Invalidate all other iterators and their entries.
                expectedModificationCount = ++modificationCount;
                V returnValue = cursor.setValue(newValue);
                value = newValue;
                return returnValue;
            }

            @Override
            public String toString() {
                checkModificationCount();
                return key + "=" + value;
            }

            @Override
            public int hashCode() {
                checkModificationCount();
                return Objects.hashCode(key) ^ Objects.hashCode(value);
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof Map.Entry<?, ?> e)) {
                    return false;
                }
                checkModificationCount();
                return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
            }
        }

        /**
         * The cursor that backs this iterator.
         */
        private final MapCursor<K, V> cursor;

        /**
         * The expected value of {@link #modificationCount}. If not equal, the index-based cursor
         * must not be used.
         */
        private int expectedModificationCount;

        /**
         * The cached value of {@link #hasNext()} or {@code null}.
         */
        private Boolean hasNext;

        /**
         * The last entry returned by {@link #next()} and the only entry that can be modified using
         * {@link Entry#setValue}.
         */
        private BridgeIterator.BridgeEntry currentEntry;

        private BridgeIterator() {
            cursor = map.getEntries();
            expectedModificationCount = modificationCount;
        }

        @Override
        public boolean hasNext() {
            checkModificationCount();
            if (hasNext != null) {
                return hasNext;
            }
            hasNext = cursor.advance();
            if (hasNext) {
                currentEntry = new BridgeIterator.BridgeEntry(fromEconomicMapKey(cursor.getKey()), cursor.getValue());
            } else {
                currentEntry = null;
            }
            return hasNext;
        }

        @Override
        public Entry<K, V> next() {
            checkModificationCount();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            hasNext = null;
            return currentEntry;
        }

        @Override
        public void remove() {
            checkModificationCount();
            if (currentEntry == null) {
                throw new IllegalStateException();
            }
            // Invalidate all iterators but this one.
            expectedModificationCount = ++modificationCount;
            cursor.remove();
            hasNext = null;
            currentEntry = null;
        }

        /**
         * Checks that this iterator was not invalidated by a structural modification of the map.
         */
        private void checkModificationCount() {
            if (modificationCount != expectedModificationCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * The marker to use in place of {@code null} as a key inside an {@link EconomicMap}. The null
     * marker itself cannot be used as a key of an {@link EconomicHashMap}. This is an
     * implementation detail of the {@link EconomicHashMap} and {@link EconomicHashSet}
     * implementations.
     */
    static final class NullMarker {
        private NullMarker() {
        }
    }

    /**
     * Encodes a key to be stored inside an {@link EconomicMap}, since {@link EconomicMap} does not
     * support null keys. This is an internal operation of the {@link EconomicHashMap} and
     * {@link EconomicHashSet} implementations.
     *
     * @param key the key which may be {@code null}
     * @return the key or null marker
     * @param <K> the key type
     */
    @SuppressWarnings("unchecked")
    static <K> K asEconomicMapKey(K key) {
        assert key != NullMarker.class : "the null marker cannot be used as a key";
        if (key == null) {
            return (K) NullMarker.class;
        } else {
            return key;
        }
    }

    /**
     * Decodes a key stored inside an {@link EconomicMap}, which was encoded with
     * {@link #asEconomicMapKey}. This is an internal operation of the {@link EconomicHashMap} and
     * {@link EconomicHashSet} implementations.
     *
     * @param key the key which may be a null marker
     * @return the decoded key
     * @param <K> the key type
     */
    static <K> K fromEconomicMapKey(K key) {
        if (key == NullMarker.class) {
            return null;
        } else {
            return key;
        }
    }
}
