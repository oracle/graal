/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Memory efficient map data structure.
 *
 * @since 19.0
 */
public interface EconomicMap<K, V> extends UnmodifiableEconomicMap<K, V> {

    /**
     * Associates {@code value} with {@code key} in this map. If the map previously contained a
     * mapping for {@code key}, the old value is replaced by {@code value}.
     *
     * @return the previous value associated with {@code key}, or {@code null} if there was no
     *         mapping for {@code key}.
     * @since 19.0
     */
    V put(K key, V value);

    /**
     * Copies all of the mappings from {@code other} to this map.
     *
     * @since 19.0
     */
    default void putAll(EconomicMap<K, V> other) {
        MapCursor<K, V> e = other.getEntries();
        while (e.advance()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Copies all of the mappings from {@code other} to this map.
     *
     * @since 19.0
     */
    default void putAll(UnmodifiableEconomicMap<? extends K, ? extends V> other) {
        UnmodifiableMapCursor<? extends K, ? extends V> entry = other.getEntries();
        while (entry.advance()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes all of the mappings from this map. The map will be empty after this call returns.
     *
     * @since 19.0
     */
    void clear();

    /**
     * Removes the mapping for {@code key} from this map if it is present. The map will not contain
     * a mapping for {@code key} once the call returns.
     *
     * @return the previous value associated with {@code key}, or {@code null} if there was no
     *         mapping for {@code key}.
     * @since 19.0
     */
    V removeKey(K key);

    /**
     * Returns a {@link MapCursor} view of the mappings contained in this map.
     *
     * @since 19.0
     */
    @Override
    MapCursor<K, V> getEntries();

    /**
     * Replaces each entry's value with the result of invoking {@code function} on that entry until
     * all entries have been processed or the function throws an exception. Exceptions thrown by the
     * function are relayed to the caller.
     *
     * @since 19.0
     */
    void replaceAll(BiFunction<? super K, ? super V, ? extends V> function);

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create() {
        return EconomicMap.create(Equivalence.DEFAULT);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys and initializes with a specified
     * capacity.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create(int initialCapacity) {
        return EconomicMap.create(Equivalence.DEFAULT, initialCapacity);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the given comparison
     * strategy for keys.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy) {
        return EconomicMapImpl.create(strategy, false);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys and copies all elements from the
     * specified existing map.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create(UnmodifiableEconomicMap<K, V> m) {
        return EconomicMap.create(Equivalence.DEFAULT, m);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and copies all elements from
     * the specified existing map.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy, UnmodifiableEconomicMap<K, V> m) {
        return EconomicMapImpl.create(strategy, m, false);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and initializes with a
     * specified capacity.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity, false);
    }

    /**
     * Wraps an existing {@link Map} as an {@link EconomicMap}.
     *
     * @since 19.0
     */
    static <K, V> EconomicMap<K, V> wrapMap(Map<K, V> map) {
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
