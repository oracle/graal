/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

/**
 * A synchronized hash map with weakly referenced values. Cleared value references are expunged only
 * when the map is mutated. Keys may be strongly or weakly referenced.
 */
final class EconomicTransitionMap<K, V> extends TransitionMap<K, V> {

    /** Key is either {@code K} or {@code WeakKey<K>}. */
    private final EconomicMap<Object, StrongKeyWeakValueEntry<Object, V>> map;

    private static final Equivalence WEAK_KEY_EQUIVALENCE = new WeakKeyEquivalence();

    EconomicTransitionMap() {
        this.map = EconomicMap.create(WEAK_KEY_EQUIVALENCE);
    }

    private V getValue(StrongKeyWeakValueEntry<? super K, V> entry) {
        return entry == null ? null : entry.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        synchronized (queue) {
            return getValue(map.get(key));
        }
    }

    @Override
    protected V putAnyKey(Object key, V value) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.put(key, new StrongKeyWeakValueEntry<>(key, value, queue)));
        }
    }

    @Override
    protected V putAnyKeyIfAbsent(Object key, V value) {
        synchronized (queue) {
            expungeStaleEntries();
            /*
             * Note: We need to also consider stale weak entries as absent, so we cannot use the
             * map's own putIfAbsent here. The reference may have not been enqueued yet either.
             */
            var prevValue = getValue(map.get(key));
            if (prevValue != null) {
                return prevValue;
            } else {
                map.put(key, new StrongKeyWeakValueEntry<>(key, value, queue));
                return null;
            }
        }
    }

    @Override
    public V remove(Object key) {
        synchronized (queue) {
            expungeStaleEntries();
            return getValue(map.removeKey(key));
        }
    }

    @Override
    protected void expungeStaleEntry(StrongKeyWeakValueEntry<Object, V> entry) {
        if (map.get(entry.getKey()) == entry) {
            map.removeKey(entry.getKey());
        }
    }

    @Override
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
