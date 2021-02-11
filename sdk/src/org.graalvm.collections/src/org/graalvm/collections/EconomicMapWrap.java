/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Wraps an existing {@link Map} as an {@link EconomicMap}.
 *
 * @since 21.1
 */
public class EconomicMapWrap<K, V> implements EconomicMap<K, V> {

    private final Map<K, V> map;

    /** @since 21.1 */
    public EconomicMapWrap(Map<K, V> map) {
        this.map = map;
    }

    /** @since 21.1 */
    @Override
    public V get(K key) {
        V result = map.get(key);
        return result;
    }

    /** @since 21.1 */
    @Override
    public V put(K key, V value) {
        V result = map.put(key, value);
        return result;
    }

    /** @since 21.1 */
    @Override
    public V putIfAbsent(K key, V value) {
        V result = map.putIfAbsent(key, value);
        return result;
    }

    /** @since 21.1 */
    @Override
    public int size() {
        int result = map.size();
        return result;
    }

    /** @since 21.1 */
    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /** @since 21.1 */
    @Override
    public void clear() {
        map.clear();
    }

    /** @since 21.1 */
    @Override
    public V removeKey(K key) {
        V result = map.remove(key);
        return result;
    }

    /** @since 21.1 */
    @Override
    public Iterable<V> getValues() {
        return map.values();
    }

    /** @since 21.1 */
    @Override
    public Iterable<K> getKeys() {
        return map.keySet();
    }

    /** @since 21.1 */
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /** @since 21.1 */
    @Override
    public MapCursor<K, V> getEntries() {
        Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
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

    /** @since 21.1 */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        map.replaceAll(function);
    }
}
