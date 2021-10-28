/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Memory efficient map data structure that dynamically changes its representation depending on the
 * number of entries and is specially optimized for small number of entries. It keeps elements in a
 * linear list without any hashing when the number of entries is small. Should an actual hash data
 * structure be necessary, it tries to fit the hash value into as few bytes as possible. In contrast
 * to {@link java.util.HashMap}, it avoids allocating an extra node object per entry and rather
 * keeps values always in a plain array. See {@link EconomicMapImpl} for implementation details and
 * exact thresholds when its representation changes.
 *
 * It supports a {@code null} value, but it does not support adding or looking up a {@code null}
 * key. Operations {@code get} and {@code put} provide constant-time performance on average if
 * repeatedly performed. They can however trigger an operation growing or compressing the data
 * structure, which is linear in the number of elements. Iteration is also linear in the number of
 * elements.
 *
 * The implementation is not synchronized. If multiple threads want to access the data structure, it
 * requires manual synchronization, for example using {@link java.util.Collections#synchronizedMap}.
 * There is also no extra precaution to detect concurrent modification while iterating.
 *
 * Different strategies for the equality comparison can be configured by providing a
 * {@link Equivalence} configuration object.
 *
 * @since 19.0
 */
public interface EconomicMap<K, V> extends UnmodifiableEconomicMap<K, V> {

    /**
     * Associates {@code value} with {@code key} in this map. If the map previously contained a
     * mapping for {@code key}, the old value is replaced by {@code value}. While the {@code value}
     * may be {@code null}, the {@code key} must not be {code null}.
     *
     * @return the previous value associated with {@code key}, or {@code null} if there was no
     *         mapping for {@code key}.
     * @since 19.0
     */
    V put(K key, V value);

    /**
     * If the specified key is not already associated with a value (or is mapped to {@code null})
     * associates it with the given value and returns {@code null}, else returns the current value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return the previous value associated with the specified key, or {@code null} if there was no
     *         mapping for the key. (A {@code null} return can also indicate that the map previously
     *         associated {@code null} with the key, if the implementation supports null values.)
     *
     * @since 20.2
     */
    default V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
        }

        return v;
    }

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
     * a mapping for {@code key} once the call returns. The {@code key} must not be {@code null}.
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
        return new EconomicMapWrap<>(map);
    }

    /**
     * Return an empty {@link MapCursor}.
     *
     * @since 22.0
     */
    static <K, V> MapCursor<K, V> emptyCursor() {
        return new MapCursor<K, V>() {
            @Override
            public void remove() {
            }

            @Override
            public boolean advance() {
                return false;
            }

            @Override
            public K getKey() {
                return null;
            }

            @Override
            public V getValue() {
                return null;
            }
        };
    }
}
