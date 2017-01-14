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
package org.graalvm.compiler.core.common;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Factory for creating collection objects used during compilation.
 */
public class CollectionsFactory {

    private static final boolean DEBUG_MAPS = false;

    /**
     * Creates a new map that guarnatees insertion order on the key set.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy) {
        if (DEBUG_MAPS) {
            return debugNewMap();
        }
        return new EconomicMapImpl<>(strategy);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and initializes with a
     * specified capacity.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy, int initialCapacity) {
        if (DEBUG_MAPS) {
            return debugNewMap();
        } else {
            return new EconomicMapImpl<>(strategy, initialCapacity);
        }
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and copies all elements from
     * the specified existing map.
     */
    public static <K, V> EconomicMap<K, V> newMap(CompareStrategy strategy, ImmutableEconomicMap<K, V> m) {
        EconomicMap<K, V> result;
        if (DEBUG_MAPS) {
            result = debugNewMap(m);
        } else {
            result = new EconomicMapImpl<>(strategy, m);
        }

        return result;
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy) {
        if (DEBUG_MAPS) {
            return newDebugSet();
        }
        return new EconomicMapImpl<E, E>(strategy);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and inserts
     * all elements of the specified collection.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy, EconomicSet<E> c) {
        return new EconomicMapImpl<E, E>(strategy, c);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and inserts
     * all elements of the specified collection.
     */
    public static <E> EconomicSet<E> newSet(CompareStrategy strategy, Iterable<? extends E> c) {
        EconomicSet<E> result = newSet(strategy);
        for (E element : c) {
            result.add(element);
        }
        return result;
    }

    public static <E> EconomicSet<E> newSet(CompareStrategy strategy, int initialCapacity) {
        if (DEBUG_MAPS) {
            return newDebugSet();
        }
        return new EconomicMapImpl<E, E>(strategy, initialCapacity);
    }

    /**
     * Creates a reference set used for debugging only.
     */
    private static <E> EconomicSet<E> newDebugSet() {
        LinkedHashSet<E> linkedSet = new LinkedHashSet<>();
        return new EconomicSet<E>() {

            @Override
            public Iterator<E> iterator() {
                return linkedSet.iterator();
            }

            @Override
            public boolean contains(E element) {
                return linkedSet.contains(element);
            }

            @Override
            public void addAll(Iterable<E> values) {
                for (E e : values) {
                    linkedSet.add(e);
                }
            }

            @Override
            public int size() {
                return linkedSet.size();
            }

            @Override
            public boolean add(E element) {
                return linkedSet.add(element);
            }

            @Override
            public void remove(E element) {
                linkedSet.remove(element);
            }

            @Override
            public void clear() {
                linkedSet.clear();
            }

            @Override
            public boolean isEmpty() {
                return linkedSet.isEmpty();
            }

            @Override
            public void retainAll(EconomicSet<E> values) {
                Iterator<E> iterator = linkedSet.iterator();
                while (iterator.hasNext()) {
                    E element = iterator.next();
                    if (!values.contains(element)) {
                        iterator.remove();
                    }
                }
            }

        };
    }

    public static <K, V> EconomicMap<K, V> debugNewMap(ImmutableEconomicMap<K, V> m) {
        EconomicMap<K, V> result = debugNewMap();
        ImmutableMapCursor<K, V> cursor = m.getEntries();
        while (cursor.advance()) {
            result.put(cursor.getKey(), cursor.getValue());
        }
        return result;
    }

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

    /**
     * Creates a new map for debugging purposes only.
     */
    public static <K, V> EconomicMap<K, V> debugNewMap() {
        final LinkedHashMap<K, V> linkedMap = new LinkedHashMap<>();
        final EconomicMapImpl<K, V> sparseMap = new EconomicMapImpl<>(CompareStrategy.EQUALS);
        return new EconomicMap<K, V>() {

            @Override
            public V get(K key) {
                V result = linkedMap.get(key);
                V sparseResult = sparseMap.get(key);
                assert Objects.equals(result, sparseResult);
                return result;
            }

            @Override
            public V put(K key, V value) {
                V result = linkedMap.put(key, value);
                assert Objects.equals(result, sparseMap.put(key, value));
                return result;
            }

            @Override
            public int size() {
                int result = linkedMap.size();
                assert result == sparseMap.size();
                return result;
            }

            @Override
            public boolean containsKey(K key) {
                boolean result = linkedMap.containsKey(key);
                assert result == sparseMap.containsKey(key);
                return result;
            }

            @Override
            public void clear() {
                linkedMap.clear();
                sparseMap.clear();
            }

            @Override
            public V removeKey(K key) {
                V result = linkedMap.remove(key);
                assert Objects.equals(result, sparseMap.removeKey(key));
                return result;
            }

            @Override
            public Iterable<V> getValues() {

                Iterator<V> iterator = linkedMap.values().iterator();
                Iterator<V> sparseIterator = sparseMap.getValues().iterator();
                return new Iterable<V>() {

                    @Override
                    public Iterator<V> iterator() {
                        return new Iterator<V>() {

                            @Override
                            public boolean hasNext() {
                                boolean result = iterator.hasNext();
                                boolean otherResult = sparseIterator.hasNext();
                                assert result == otherResult;
                                return result;
                            }

                            @Override
                            public V next() {
                                V sparseNext = sparseIterator.next();
                                V next = iterator.next();
                                assert Objects.equals(sparseNext, next);
                                return next;
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                sparseIterator.remove();
                            }
                        };
                    }

                };
            }

            @Override
            public Iterable<K> getKeys() {

                Iterator<K> iterator = linkedMap.keySet().iterator();
                Iterator<K> sparseIterator = sparseMap.getKeys().iterator();
                return new Iterable<K>() {

                    @Override
                    public Iterator<K> iterator() {
                        return new Iterator<K>() {

                            @Override
                            public boolean hasNext() {
                                boolean result = iterator.hasNext();
                                boolean otherResult = sparseIterator.hasNext();
                                assert result == otherResult;
                                return result;
                            }

                            @Override
                            public K next() {
                                K sparseNext = sparseIterator.next();
                                K next = iterator.next();
                                assert Objects.equals(sparseNext, next);
                                return next;
                            }

                            @Override
                            public void remove() {
                                iterator.remove();
                                sparseIterator.remove();
                            }
                        };
                    }

                };
            }

            @Override
            public boolean isEmpty() {
                boolean result = linkedMap.isEmpty();
                assert result == sparseMap.isEmpty();
                return result;
            }

            @Override
            public MapCursor<K, V> getEntries() {
                Iterator<java.util.Map.Entry<K, V>> iterator = linkedMap.entrySet().iterator();
                MapCursor<K, V> cursor = sparseMap.getEntries();
                return new MapCursor<K, V>() {

                    private Map.Entry<K, V> current;

                    @Override
                    public boolean advance() {
                        boolean result = iterator.hasNext();
                        boolean otherResult = cursor.advance();
                        assert result == otherResult;
                        if (result) {
                            current = iterator.next();
                        }

                        return result;
                    }

                    @Override
                    public K getKey() {
                        K key = current.getKey();
                        assert key == cursor.getKey();
                        return key;
                    }

                    @Override
                    public V getValue() {
                        V value = current.getValue();
                        assert Objects.equals(value, cursor.getValue());
                        return value;
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                        cursor.remove();
                    }
                };
            }

            @Override
            public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
                linkedMap.replaceAll(function);
                sparseMap.replaceAll(function);
            }

        };
    }

}
