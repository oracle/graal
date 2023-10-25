/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link ConcurrentMap} that supports registering observers which will be notified for each
 * mapping. Each new observer will be notified for all the existing entries. The implementation
 * doesn't guarantee that the observer is notified only once for each mapping. Internally this map
 * stores data in a {@link ConcurrentHashMap}.
 */
public final class ObservableMap<K, V> implements ConcurrentMap<K, V> {

    public interface MappingObserver {
        void notify(Object key, Object value);
    }

    private final ConcurrentHashMap<K, V> wrapped = new ConcurrentHashMap<>();
    private final List<MappingObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(MappingObserver observer) {
        observers.add(observer);
        /* Notify already registered values. */
        forEach(observer::notify);
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
        return wrapped.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return wrapped.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return wrapped.get(key);
    }

    @Override
    public V put(K key, V value) {
        V previous = wrapped.put(key, value);
        if (previous != value) {
            observers.forEach(o -> o.notify(key, value));
        }
        return previous;
    }

    @Override
    public V remove(Object key) {
        return wrapped.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        wrapped.putAll(m);
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            observers.forEach(o -> o.notify(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public void clear() {
        wrapped.clear();
    }

    @Override
    public Set<K> keySet() {
        return wrapped.keySet();
    }

    @Override
    public Collection<V> values() {
        return wrapped.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return wrapped.entrySet();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V previous = wrapped.putIfAbsent(key, value);
        if (previous == null) {
            observers.forEach(o -> o.notify(key, value));
        }
        return previous;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return wrapped.remove(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        boolean replaced = wrapped.replace(key, oldValue, newValue);
        if (replaced) {
            observers.forEach(o -> o.notify(key, newValue));
        }
        return replaced;
    }

    @Override
    public V replace(K key, V value) {
        V previous = wrapped.replace(key, value);
        if (previous != null) {
            observers.forEach(o -> o.notify(key, value));
        }
        return previous;
    }
}
