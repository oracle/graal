/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

/**
 * A synchronized hash map with weakly referenced values. Cleared value references are expunged only
 * when the map is mutated. Keys may be strongly or weakly referenced.
 */
final class TransitionMap<K, V> {

    /** Key is either {@code K} or {@code WeakKey<K>}. */
    private final EconomicMap<Object, StrongKeyWeakValueEntry<Object, V>> map;
    private final ReferenceQueue<V> queue;

    private static final Equivalence WEAK_KEY_EQUIVALENCE = new WeakKeyEquivalence();

    TransitionMap() {
        this.map = EconomicMap.create(WEAK_KEY_EQUIVALENCE);
        this.queue = new ReferenceQueue<>();
    }

    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    private V getValue(StrongKeyWeakValueEntry<? super K, V> entry) {
        return entry == null ? null : entry.get();
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
        synchronized (queue) {
            return getValue(map.get(key));
        }
    }

    private V putAnyKey(Object key, V value) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.put(key, new StrongKeyWeakValueEntry<>(key, value, queue)));
        }
    }

    /**
     * Insert with strongly referenced key.
     */
    public V put(K key, V value) {
        return putAnyKey(key, value);
    }

    /**
     * Insert with weakly referenced key.
     */
    public V putWeakKey(K key, V value) {
        ShapeImpl.shapeCacheWeakKeys.inc();
        WeakKey<K> weakKey = new WeakKey<>(key);
        return putAnyKey(weakKey, value);
    }

    public V remove(Object key) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.removeKey(key));
        }
    }

    private void expungeStaleEntries() {
        for (Reference<? extends V> r; (r = queue.poll()) != null;) {
            if (r instanceof StrongKeyWeakValueEntry<?, ?>) {
                StrongKeyWeakValueEntry<?, ?> entry = (StrongKeyWeakValueEntry<?, ?>) r;
                if (map.get(entry.getKey()) == entry) {
                    map.removeKey(entry.getKey());
                    ShapeImpl.shapeCacheExpunged.inc();
                }
            }
        }
    }

    public void clear() {
        synchronized (queue) {
            while (queue.poll() != null) {
                // clear out ref queue.
            }
            map.clear();
        }
    }

    public void forEach(BiConsumer<? super K, ? super V> consumer) {
        synchronized (queue) {
            MapCursor<Object, StrongKeyWeakValueEntry<Object, V>> cursor = map.getEntries();
            while (cursor.advance()) {
                V value = cursor.getValue().get();
                if (value != null) {
                    K key = unwrapKey(cursor.getKey());
                    if (key != null) {
                        consumer.accept(key, value);
                    }
                }
            }
        }
    }

    public <R> R iterateEntries(BiFunction<? super K, ? super V, R> consumer) {
        synchronized (queue) {
            MapCursor<Object, StrongKeyWeakValueEntry<Object, V>> cursor = map.getEntries();
            while (cursor.advance()) {
                V value = cursor.getValue().get();
                if (value != null) {
                    K key = unwrapKey(cursor.getKey());
                    if (key != null) {
                        R result = consumer.apply(key, value);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private K unwrapKey(Object key) {
        if (key instanceof WeakKey<?>) {
            return ((WeakKey<K>) key).get();
        }
        return (K) key;
    }

    private static final class WeakKeyEquivalence extends Equivalence {

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }

        @Override
        public boolean equals(Object a, Object b) {
            boolean aIsWeak = a instanceof WeakKey<?>;
            boolean bIsWeak = b instanceof WeakKey<?>;
            if (aIsWeak && !bIsWeak) {
                return Objects.equals(((WeakKey<?>) a).get(), b);
            } else if (!aIsWeak && bIsWeak) {
                return Objects.equals(a, ((WeakKey<?>) b).get());
            }
            return a.equals(b);
        }

    }

}
