/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.util;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

import org.graalvm.util.impl.EconomicMapImpl;

/**
 * Factory for creating map and set collection objects.
 */
public class CollectionFactory {

    /**
     * Creates a new map that guarantees insertion order on the key set with the the default
     * {@link CompareStrategy#EQUALS} comparison strategy for keys.
     */
    public static <K, V> EconomicMap<K, V> newMap() {
        return newMap(CompareStrategy.EQUALS);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the given comparison
     * strategy for keys.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy) {
        return EconomicMapImpl.create(strategy);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the the default
     * {@link CompareStrategy#EQUALS} comparison strategy for keys and initializes with a specified
     * capacity.
     */
    public static <K, V> EconomicMap<K, V> newMap(int initialCapacity) {
        return newMap(CompareStrategy.EQUALS, initialCapacity);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and initializes with a
     * specified capacity.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the the default
     * {@link CompareStrategy#EQUALS} comparison strategy for keys and copies all elements from the
     * specified existing map.
     */
    public static <K, V> EconomicMap<K, V> newMap(ImmutableEconomicMap<K, V> m) {
        return newMap(CompareStrategy.EQUALS, m);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and copies all elements from
     * the specified existing map.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy, ImmutableEconomicMap<K, V> m) {
        return EconomicMapImpl.create(strategy, m);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link CompareStrategy#EQUALS} comparison strategy.
     */
    public static <E> EconomicSet<E> newSet() {
        return newSet(CompareStrategy.EQUALS);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy) {
        return EconomicMapImpl.create(strategy);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link CompareStrategy#EQUALS} comparison strategy and inserts all elements of the
     * specified collection.
     */
    public static <E> EconomicSet<E> newSet(ImmutableEconomicSet<E> c) {
        return newSet(CompareStrategy.EQUALS, c);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and inserts
     * all elements of the specified collection.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy, ImmutableEconomicSet<E> c) {
        return EconomicMapImpl.create(strategy, c);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link CompareStrategy#EQUALS} comparison strategy and inserts all elements of the
     * specified collection.
     */
    public static <E> EconomicSet<E> newSet(int initialCapacity) {
        return newSet(CompareStrategy.EQUALS, initialCapacity);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and
     * initializes with the given capacity.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity);
    }

    /**
     * Wraps an existing {@link java.util.Map} as an {@link org.graalvm.util.EconomicMap}.
     */
    public static <K, V> EconomicMap<K, V> wrapMap(Map<K, V> map) {
        return new EconomicMap<K, V>() {

            @Override
            public V get(K key) {
                V result = map.get(key);
                return result;
            }

            @Override
            public V put(K key, V value) {
                V result = map.put(key, value);
                return result;
            }

            @Override
            public int size() {
                int result = map.size();
                return result;
            }

            @Override
            public boolean containsKey(K key) {
                return map.containsKey(key);
            }

            @Override
            public void clear() {
                map.clear();
            }

            @Override
            public V removeKey(K key) {
                V result = map.remove(key);
                return result;
            }

            @Override
            public Iterable<V> getValues() {
                return map.values();
            }

            @Override
            public Iterable<K> getKeys() {
                return map.keySet();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            public MapCursor<K, V> getEntries() {
                Iterator<java.util.Map.Entry<K, V>> iterator = map.entrySet().iterator();
                return new MapCursor<K, V>() {

                    private Map.Entry<K, V> current;

                    @Override
                    public boolean advance() {
                        boolean result = iterator.hasNext();
                        if (result) {
                            current = iterator.next();
                        }

                        return result;
                    }

                    @Override
                    public K getKey() {
                        return current.getKey();
                    }

                    @Override
                    public V getValue() {
                        return current.getValue();
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
                map.replaceAll(function);
            }
        };
    }

}
