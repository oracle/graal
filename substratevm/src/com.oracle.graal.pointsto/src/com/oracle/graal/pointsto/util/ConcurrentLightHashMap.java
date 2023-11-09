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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements a hash map that is concurrent, backed by a concurrent hash map, and memory efficient.
 * The memory efficiency comes from the fact that the underlying map is initialized only when it
 * contains more than one entry. When it contains a single entry it is simply stored in a field as a
 * {@link SimpleImmutableEntry}. When the map is empty the field is null. In situations where is
 * likely that the map will contain no or only one entry there is no memory overhead incurred by
 * allocating the map.
 *
 * The value is not referenced by objects of this class but all operations use a
 * {@link AtomicReferenceFieldUpdater} to a storage location of type {@link Object}. This location
 * is then populated with three possible values:
 * <ol>
 * <li>No entries: the location is set to {@code null}
 * <li>One entry: the entry is stored directly in this location as a {@link SimpleImmutableEntry}.
 * <li>Multiple entries: the location points to a {@link ConcurrentHashMap} storing the entries
 * </ol>
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
            if (oldEntries == null) {
                /*
                 * We set the first entry. Try to install the entry directly in the field. No
                 * ConcurrentHashMap is necessary yet.
                 */
                if (updater.compareAndSet(holder, null, new SimpleImmutableEntry<>(newKey, newValue))) {
                    /* Return the previous value associated with newKey. */
                    return null;
                }

            } else if (oldEntries instanceof ConcurrentHashMap) {
                /*
                 * We already have multiple entries, the ConcurrentHashMap takes care of all
                 * concurrency issues, so we cannot fail.
                 */
                return ((ConcurrentHashMap<K, V>) oldEntries).putIfAbsent(newKey, newValue);

            } else {
                /* The value must be a single entry. */
                var oldEntry = (SimpleImmutableEntry<K, V>) oldEntries;
                if (!oldEntry.getKey().equals(newKey)) {
                    /*
                     * We add the second entry. The first entry is directly in the field, so we
                     * update the field to a ConcurrentHashMap with two entries.
                     */
                    ConcurrentHashMap<K, V> newMap = new ConcurrentHashMap<>();
                    newMap.put(oldEntry.getKey(), oldEntry.getValue());
                    newMap.put(newKey, newValue);
                    if (updater.compareAndSet(holder, oldEntries, newMap)) {
                        /* Return the previous value associated with newKey. */
                        return null;
                    }

                } else {
                    /*
                     * Corner case: setting the first key again, so nothing to do since semantics is
                     * put-if-absent.
                     */
                    assert oldEntry.getKey() == newKey : newKey;
                    /* Return the current value associated with newKey. */
                    return oldEntry.getValue();
                }
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

    public static <K, V, U> V getOrDefault(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key, V defaultValue) {
        V v = get(holder, updater, key);
        return v == null ? defaultValue : v;
    }

    @SuppressWarnings("unchecked")
    public static <K, V, U> V get(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key) {
        Object u = updater.get(holder);
        if (u == null) {
            /* No entries. */
            return null;
        } else if (u instanceof ConcurrentHashMap) {
            /* Multiple entries. */
            return ((ConcurrentHashMap<K, V>) u).get(key);
        } else {
            /* Single entry. */
            var entry = (SimpleImmutableEntry<K, V>) u;
            if (key.equals(entry.getKey())) {
                return entry.getValue();
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V, U> Map<K, V> getEntries(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        Object u = updater.get(holder);
        if (u == null) {
            /* No entries. */
            return Collections.emptyMap();
        } else if (u instanceof ConcurrentHashMap) {
            /* Multiple entries. */
            return (ConcurrentHashMap<K, V>) u;
        } else {
            /* Single entry. */
            var entry = (SimpleImmutableEntry<K, V>) u;
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V, U> void forEach(U holder, AtomicReferenceFieldUpdater<U, Object> updater, BiConsumer<? super K, ? super V> action) {
        Object u = updater.get(holder);
        if (u == null) {
            /* No entries. */
            return;
        }
        if (u instanceof ConcurrentHashMap) {
            /* Multiple entries. */
            ((ConcurrentHashMap<K, V>) u).forEach(action);
        } else {
            /* Single entry. */
            var entry = (SimpleImmutableEntry<K, V>) u;
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V, U> boolean remove(U holder, AtomicReferenceFieldUpdater<U, Object> updater, K key) {
        while (true) {
            Object e = updater.get(holder);
            if (e == null) {
                /* No entries. Nothing to remove. */
                return false;
            }
            if (e instanceof ConcurrentHashMap) {
                /*
                 * We already have multiple entries, the ConcurrentHashMap takes care of all
                 * concurrency issues, so we cannot fail.
                 */
                return ((ConcurrentHashMap<K, V>) e).remove(key) != null;
            } else {
                /* The value must be a single entry. */
                var entry = (SimpleImmutableEntry<K, V>) e;
                if (key.equals(entry.getKey())) {
                    /*
                     * We have a match for the single entry key. Try to update the field directly to
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
            /* We lost the race with another thread, just try again. */
        }
    }

    @SuppressWarnings({"unchecked", "raw"})
    public static <K, V, U> boolean removeIf(U holder, AtomicReferenceFieldUpdater<U, Object> updater, Predicate<Map.Entry<K, V>> filter) {
        while (true) {
            Object e = updater.get(holder);
            if (e == null) {
                /* No entries. Nothing to remove. */
                return false;
            }
            if (e instanceof ConcurrentHashMap) {
                /*
                 * We already have multiple entries, the ConcurrentHashMap takes care of all
                 * concurrency issues, so we cannot fail.
                 */
                return ((ConcurrentHashMap<K, V>) e).entrySet().removeIf(filter);
            } else {
                /* The value must be a single entry. */
                var entry = (SimpleImmutableEntry<K, V>) e;
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
            /* We lost the race with another thread, just try again. */
        }
    }

    public static <U> void clear(U holder, AtomicReferenceFieldUpdater<U, Object> updater) {
        updater.set(holder, null);
    }
}
