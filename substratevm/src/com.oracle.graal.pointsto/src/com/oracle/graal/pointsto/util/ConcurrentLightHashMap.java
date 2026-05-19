/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements a hash map that is concurrent, backed by a concurrent hash map, and memory efficient.
 * The memory efficiency comes from the fact that the underlying map is initialized only when it
 * contains more than two entries. When it contains a single entry it is simply stored in a field as
 * a {@link SimpleImmutableEntry}. When it contains two entries they are stored in a compact pair
 * object. When the map is empty the field is null. In situations where is likely that the map will
 * contain no, one, or two entries there is no memory overhead incurred by allocating the map.
 *
 * The value is not referenced by objects of this class but all operations use a
 * {@link AtomicReferenceFieldUpdater} to a storage location of type {@link Object}. This location
 * is then populated with four possible values:
 * <ol>
 * <li>No entries: the location is set to {@code null}
 * <li>One entry: the entry is stored directly in this location as a {@link SimpleImmutableEntry}.
 * <li>Two entries: the two entries are stored in a compact pair.
 * <li>Multiple entries: the location points to a {@link ConcurrentHashMap} storing the entries
 * </ol>
 *
 * Most state dispatches check the compact states first, in storage-size order: empty, single, pair,
 * and finally the backing map. The common case for this data structure is a small map, and the map
 * state is the only representation that requires another data-structure access.
 *
 * This map doesn't allow {@code null} keys or values.
 */
public final class ConcurrentLightHashMap {

    private ConcurrentLightHashMap() {

    }

    /**
     * If the specified key is not already associated with a value, associates it with the given
     * value.
     *
     * @return the previous value associated with the specified key, or null if there was no mapping
     *         for the key.
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public static <K, V, U> V putIfAbsent(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K newKey, V newValue) {
        if (newKey == null || newValue == null) {
            throw new NullPointerException();
        }
        while (true) {
            Object oldEntries = updater.get(holder);
            switch (oldEntries) {
                case null -> {
                    /*
                     * We set the first entry. Try to install the entry directly in the field. No
                     * ConcurrentHashMap is necessary yet.
                     */
                    if (updater.compareAndSet(holder, null, new SimpleImmutableEntry<>(newKey, newValue))) {
                        /* Return the previous value associated with newKey. */
                        return null;
                    }
                }
                case SimpleImmutableEntry<?, ?> oldEntryRaw -> {
                    /* The value must be a single entry. */
                    var oldEntry = (SimpleImmutableEntry<K, V>) oldEntryRaw;
                    if (!oldEntry.getKey().equals(newKey)) {
                        /*
                         * We add the second entry. The first entry is directly in the field, so we
                         * update the field to a compact pair.
                         */
                        EntriesPair newEntries = new EntriesPair(oldEntry.getKey(), oldEntry.getValue(), newKey, newValue);
                        if (updater.compareAndSet(holder, oldEntries, newEntries)) {
                            /* Return the previous value associated with newKey. */
                            return null;
                        }

                    } else {
                        /*
                         * Corner case: setting the first key again, so nothing to do since
                         * semantics is put-if-absent.
                         */
                        assert oldEntry.getKey().equals(newKey) : newKey;
                        /* Return the current value associated with newKey. */
                        return oldEntry.getValue();
                    }
                }
                case EntriesPair oldPair -> {
                    V oldValue = (V) oldPair.get(newKey);
                    if (oldValue != null) {
                        return oldValue;
                    }
                    ConcurrentHashMap<K, V> newMap = new ConcurrentHashMap<>();
                    newMap.put((K) oldPair.firstKey(), (V) oldPair.firstValue());
                    newMap.put((K) oldPair.secondKey(), (V) oldPair.secondValue());
                    newMap.put(newKey, newValue);
                    if (updater.compareAndSet(holder, oldEntries, newMap)) {
                        /* Return the previous value associated with newKey. */
                        return null;
                    }
                }
                case ConcurrentHashMap<?, ?> oldMap -> {
                    /*
                     * We already have multiple entries, the ConcurrentHashMap takes care of all
                     * concurrency issues, so we cannot fail.
                     */
                    return ((ConcurrentHashMap<K, V>) oldMap).putIfAbsent(newKey, newValue);
                }
                default -> throw new IllegalStateException("Unknown entries state: " + oldEntries.getClass());
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Sets the key to the value computed by the mapping function if there is no mapping for the
     * key. If the mapping function returns null, no mapping is recorded. This doesn't make any
     * guarantees that the mapping function is only executed once.
     *
     * @return the current (existing or computed) value associated with the specified key, or null
     *         if the computed value is null
     * @throws NullPointerException if the specified key is null, since this map does not support
     *             null keys, or the mappingFunction is null
     */
    public static <K, V, U> V computeIfAbsent(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V oldValue = get(holder, updater, key);
        if (oldValue == null) {
            V newValue = mappingFunction.apply(key);
            if (newValue != null) {
                oldValue = putIfAbsent(holder, updater, key, newValue);
                if (oldValue == null) {
                    return newValue;
                }
            }
        }
        return oldValue;
    }

    /**
     * Returns the value associated with {@code key}, or {@code defaultValue} if there is no
     * mapping.
     */
    public static <K, V, U> V getOrDefault(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key, V defaultValue) {
        V v = get(holder, updater, key);
        return v == null ? defaultValue : v;
    }

    /**
     * Returns the value associated with {@code key}, or {@code null} if there is no mapping.
     */
    @SuppressWarnings("unchecked")
    public static <K, V, U> V get(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key) {
        Object u = updater.get(holder);
        switch (u) {
            case null -> {
                /* No entries. */
                return null;
            }
            case SimpleImmutableEntry<?, ?> entryRaw -> {
                /* Single entry. */
                var entry = (SimpleImmutableEntry<K, V>) entryRaw;
                if (key.equals(entry.getKey())) {
                    return entry.getValue();
                }
                return null;
            }
            case EntriesPair pair -> {
                /* Two entries. */
                return (V) pair.get(key);
            }
            case ConcurrentHashMap<?, ?> map -> {
                /* Multiple entries. */
                return ((ConcurrentHashMap<K, V>) map).get(key);
            }
            default -> throw new IllegalStateException("Unknown entries state: " + u.getClass());
        }
    }

    /**
     * Returns a map view of the entries currently stored in the holder.
     */
    @SuppressWarnings("unchecked")
    public static <K, V, U> Map<K, V> getEntries(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object u = updater.get(holder);
        switch (u) {
            case null -> {
                /* No entries. */
                return Collections.emptyMap();
            }
            case SimpleImmutableEntry<?, ?> entryRaw -> {
                /* Single entry. */
                var entry = (SimpleImmutableEntry<K, V>) entryRaw;
                return Collections.singletonMap(entry.getKey(), entry.getValue());
            }
            case EntriesPair pair -> {
                /* Two entries. */
                return (Map<K, V>) pair;
            }
            case ConcurrentHashMap<?, ?> map -> {
                /* Multiple entries. */
                return (ConcurrentHashMap<K, V>) map;
            }
            default -> throw new IllegalStateException("Unknown entries state: " + u.getClass());
        }
    }

    /**
     * Applies {@code action} to each entry currently stored in the holder.
     */
    @SuppressWarnings("unchecked")
    public static <K, V, U> void forEach(U holder, AtomicReferenceFieldUpdater<U, Object> updater, BiConsumer<? super K, ? super V> action) {
        Object u = updater.get(holder);
        switch (u) {
            case null -> {
                /* No entries. */
                return;
            }
            case SimpleImmutableEntry<?, ?> entryRaw -> {
                /* Single entry. */
                var entry = (SimpleImmutableEntry<K, V>) entryRaw;
                action.accept(entry.getKey(), entry.getValue());
            }
            case EntriesPair pair -> {
                /* Two entries. */
                action.accept((K) pair.firstKey(), (V) pair.firstValue());
                action.accept((K) pair.secondKey(), (V) pair.secondValue());
            }
            case ConcurrentHashMap<?, ?> map -> {
                /* Multiple entries. */
                ((ConcurrentHashMap<K, V>) map).forEach(action);
            }
            default -> throw new IllegalStateException("Unknown entries state: " + u.getClass());
        }
    }

    /**
     * Removes the mapping for {@code key} if it is present.
     *
     * @return {@code true} when the holder changed, or {@code false} when no mapping was present
     */
    @SuppressWarnings("unchecked")
    public static <K, V, U> boolean remove(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key) {
        while (true) {
            Object e = updater.get(holder);
            switch (e) {
                case null -> {
                    /* No entries. Nothing to remove. */
                    return false;
                }
                case SimpleImmutableEntry<?, ?> entryRaw -> {
                    /* The value must be a single entry. */
                    var entry = (SimpleImmutableEntry<K, V>) entryRaw;
                    if (key.equals(entry.getKey())) {
                        /*
                         * We have a match for the single entry key. Try to update the field
                         * directly to null to remove that entry.
                         */
                        if (updater.compareAndSet(holder, e, null)) {
                            return true;
                        }
                    } else {
                        /* We have no match on the single entry. Nothing to remove. */
                        return false;
                    }
                }
                case EntriesPair pair -> {
                    SimpleImmutableEntry<K, V> newEntries;
                    if (key.equals(pair.firstKey())) {
                        newEntries = pair.secondEntry();
                    } else if (key.equals(pair.secondKey())) {
                        newEntries = pair.firstEntry();
                    } else {
                        return false;
                    }
                    if (updater.compareAndSet(holder, e, newEntries)) {
                        return true;
                    }
                }
                case ConcurrentHashMap<?, ?> map -> {
                    /*
                     * We already have multiple entries, the ConcurrentHashMap takes care of all
                     * concurrency issues, so we cannot fail.
                     */
                    return ((ConcurrentHashMap<K, V>) map).remove(key) != null;
                }
                default -> throw new IllegalStateException("Unknown entries state: " + e.getClass());
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Removes every mapping matching {@code filter}.
     *
     * @return {@code true} when at least one mapping was removed
     */
    @SuppressWarnings({"unchecked", "raw"})
    public static <K, V, U> boolean removeIf(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Predicate<Map.Entry<K, V>> filter) {
        while (true) {
            Object e = updater.get(holder);
            switch (e) {
                case null -> {
                    /* No entries. Nothing to remove. */
                    return false;
                }
                case SimpleImmutableEntry<?, ?> entryRaw -> {
                    /* The value must be a single entry. */
                    var entry = (SimpleImmutableEntry<K, V>) entryRaw;
                    if (filter.test(entry)) {
                        /*
                         * We have a match for the single entry. Try to update the field directly to
                         * null to remove that entry.
                         */
                        if (updater.compareAndSet(holder, e, null)) {
                            return true;
                        }

                    } else {
                        /* We have no match on the single entry. Nothing to remove. */
                        return false;
                    }
                }
                case EntriesPair pair -> {
                    boolean removeFirst = filter.test(pair.firstEntry());
                    boolean removeSecond = filter.test(pair.secondEntry());
                    Object newEntries;
                    if (removeFirst && removeSecond) {
                        newEntries = null;
                    } else if (removeFirst) {
                        newEntries = pair.secondEntry();
                    } else if (removeSecond) {
                        newEntries = pair.firstEntry();
                    } else {
                        return false;
                    }
                    if (updater.compareAndSet(holder, e, newEntries)) {
                        return true;
                    }
                }
                case ConcurrentHashMap<?, ?> map -> {
                    /*
                     * We already have multiple entries, the ConcurrentHashMap takes care of all
                     * concurrency issues, so we cannot fail.
                     */
                    return ((ConcurrentHashMap<K, V>) map).entrySet().removeIf(filter);
                }
                default -> throw new IllegalStateException("Unknown entries state: " + e.getClass());
            }
            /* We lost the race with another thread, just try again. */
        }
    }

    /**
     * Removes all entries from the holder.
     */
    public static <U> void clear(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        updater.set(holder, null);
    }

    /**
     * Compact immutable representation used when the holder contains exactly two mappings.
     *
     * This object also implements the immutable {@link Map} view returned by
     * {@link #getEntries(Object, AtomicReferenceFieldUpdater)} for the two-entry state. Reusing the
     * pair object as the view avoids allocating a temporary map when callers only need to observe
     * the current contents.
     */
    private static final class EntriesPair extends AbstractMap<Object, Object> {

        private final Object firstKey;
        private final Object firstValue;
        private final Object secondKey;
        private final Object secondValue;

        EntriesPair(Object firstKey, Object firstValue, Object secondKey, Object secondValue) {
            this.firstKey = firstKey;
            this.firstValue = firstValue;
            this.secondKey = secondKey;
            this.secondValue = secondValue;
        }

        Object firstKey() {
            return firstKey;
        }

        Object firstValue() {
            return firstValue;
        }

        Object secondKey() {
            return secondKey;
        }

        Object secondValue() {
            return secondValue;
        }

        /**
         * Returns the value associated with {@code key}, or {@code null} if neither stored key
         * matches.
         */
        @Override
        public Object get(Object key) {
            if (key.equals(firstKey)) {
                return firstValue;
            }
            if (key.equals(secondKey)) {
                return secondValue;
            }
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<Object, Object>> iterator() {
                    return new Iterator<>() {
                        private int index;

                        @Override
                        public boolean hasNext() {
                            return index < 2;
                        }

                        @Override
                        public Entry<Object, Object> next() {
                            return switch (index++) {
                                case 0 -> firstEntry();
                                case 1 -> secondEntry();
                                default -> throw new NoSuchElementException();
                            };
                        }
                    };
                }

                @Override
                public int size() {
                    return 2;
                }
            };
        }

        /**
         * Returns the first stored mapping as an immutable entry.
         */
        @SuppressWarnings("unchecked")
        <K, V> SimpleImmutableEntry<K, V> firstEntry() {
            return new SimpleImmutableEntry<>((K) firstKey, (V) firstValue);
        }

        /**
         * Returns the second stored mapping as an immutable entry.
         */
        @SuppressWarnings("unchecked")
        <K, V> SimpleImmutableEntry<K, V> secondEntry() {
            return new SimpleImmutableEntry<>((K) secondKey, (V) secondValue);
        }
    }
}
