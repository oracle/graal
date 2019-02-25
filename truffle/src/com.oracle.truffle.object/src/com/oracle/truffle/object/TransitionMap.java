/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

/**
 * A synchronized hash map with weakly referenced values. Cleared value references are expunged only
 * when the map is mutated.
 */
final class TransitionMap<K, V> implements Map<K, V> {
    private final EconomicMap<K, StrongKeyWeakValueEntry<K, V>> map;
    private final ReferenceQueue<V> queue;

    TransitionMap() {
        this.map = EconomicMap.create();
        this.queue = new ReferenceQueue<>();
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    private V getValue(StrongKeyWeakValueEntry<K, V> entry) {
        return entry == null ? null : entry.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        synchronized (queue) {
            return getValue(map.get((K) key));
        }
    }

    @Override
    public V put(K key, V value) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.put(key, new StrongKeyWeakValueEntry<>(key, value, queue)));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.removeKey((K) key));
        }
    }

    @SuppressWarnings("unchecked")
    private void expungeStaleEntries() {
        for (Reference<? extends V> x; (x = queue.poll()) != null;) {
            StrongKeyWeakValueEntry<K, V> ex = (StrongKeyWeakValueEntry<K, V>) x;
            if (map.get(ex.getKey()) == ex) {
                map.removeKey(ex.getKey());
                ShapeImpl.shapeCacheExpunged.inc();
            }
        }
    }

    @Override
    public void clear() {
        synchronized (queue) {
            while (queue.poll() != null) {
                // clear out ref queue.
            }
            map.clear();
        }
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> consumer) {
        synchronized (queue) {
            MapCursor<K, StrongKeyWeakValueEntry<K, V>> cursor = map.getEntries();
            while (cursor.advance()) {
                V value = cursor.getValue().get();
                if (value != null) {
                    consumer.accept(cursor.getKey(), value);
                }
            }
        }
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
