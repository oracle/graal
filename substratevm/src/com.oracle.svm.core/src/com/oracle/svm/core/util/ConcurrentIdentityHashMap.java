/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Wraps a ConcurrentHashMap using reference-equality in place of object-equality when comparing
 * keys.
 */
public final class ConcurrentIdentityHashMap<K, V> implements ConcurrentMap<K, V> {

    private final ConcurrentHashMap<Identity<K>, V> wrapped;

    public ConcurrentIdentityHashMap() {
        wrapped = new ConcurrentHashMap<>();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return wrapped.putIfAbsent(Identity.of(key), value);
    }

    @Override
    public V get(Object key) {
        return wrapped.get(Identity.of(key));
    }

    @Override
    public V put(K key, V value) {
        return wrapped.put(Identity.of(key), value);
    }

    @Override
    public int size() {
        return wrapped.size();
    }

    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return wrapped.containsKey(Identity.of(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return wrapped.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        return wrapped.remove(Identity.of(key));
    }

    @Override
    public boolean remove(Object key, Object value) {
        return wrapped.remove(Identity.of(key), value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return wrapped.replace(Identity.of(key), oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        return wrapped.replace(Identity.of(key), value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            wrapped.put(Identity.of(e.getKey()), e.getValue());
        }
    }

    @Override
    public void clear() {
        wrapped.clear();
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<K> iterator() {
                return new Iterator<>() {
                    private final Iterator<Identity<K>> underlying = wrapped.keySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return underlying.hasNext();
                    }

                    @Override
                    public K next() {
                        return underlying.next().get();
                    }
                };
            }

            @Override
            public boolean contains(Object o) {
                return ConcurrentIdentityHashMap.this.containsKey(o);
            }

            @Override
            public int size() {
                return wrapped.size();
            }
        };
    }

    @Override
    public Collection<V> values() {
        return wrapped.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new Iterator<>() {
                    private final Iterator<Entry<Identity<K>, V>> underlying = wrapped.entrySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return underlying.hasNext();
                    }

                    @Override
                    public Entry<K, V> next() {
                        final Entry<Identity<K>, V> entry = underlying.next();
                        return new SimpleEntry<>(entry.getKey().get(), entry.getValue());
                    }
                };
            }

            @Override
            public int size() {
                return wrapped.size();
            }
        };
    }

    private static final class Identity<K> {

        private static <K> Identity<K> of(K key) {
            return new Identity<>(key);
        }

        private final K key;

        private Identity(K key) {
            this.key = key;
        }

        public K get() {
            return key;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof Identity) {
                return key == ((Identity<?>) other).key;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(key);
        }
    }
}
