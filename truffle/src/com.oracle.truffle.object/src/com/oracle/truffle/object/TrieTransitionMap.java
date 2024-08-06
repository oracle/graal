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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A thread-safe hash map based on an array mapped trie. Lookups are lock-free, other operations may
 * lock for reference queue processing, expunging stale values.
 */
final class TrieTransitionMap<K, V> extends TransitionMap<K, V> implements BiFunction<Object, V, Map.Entry<Object, V>> {

    /** Key is either {@code K} or {@code WeakKey<K>}. */
    private volatile UnorderedTrieMap<Object, V> map;

    @SuppressWarnings("rawtypes") //
    private static final AtomicReferenceFieldUpdater<TrieTransitionMap, UnorderedTrieMap> MAP_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(TrieTransitionMap.class, UnorderedTrieMap.class, "map");

    TrieTransitionMap() {
        this.map = UnorderedTrieMap.empty();
    }

    @Override
    public V get(Object key) {
        return getValue(map.getEntry(key));
    }

    @Override
    protected V putAnyKey(Object key, V value) {
        expungeStaleEntries();
        V prevValue = getValue(map.getEntry(key));
        UnorderedTrieMap<Object, V> oldMap;
        UnorderedTrieMap<Object, V> newMap;
        do {
            oldMap = map;
            newMap = oldMap.copyAndPut(key, value, this);
            if (newMap == oldMap) {
                break;
            }
        } while (!MAP_UPDATER.compareAndSet(this, oldMap, newMap));
        return prevValue;
    }

    @Override
    protected V putAnyKeyIfAbsent(Object key, V value) {
        expungeStaleEntries();
        /*
         * Note: We need to also consider stale weak values as absent (references of which may not
         * have been enqueued yet either). The implementation of copyAndPutIfAbsent already handles
         * this case by checking that the value is non-null, and replacing the entry otherwise.
         */
        UnorderedTrieMap<Object, V> oldMap;
        UnorderedTrieMap<Object, V> newMap;
        do {
            oldMap = map;
            newMap = oldMap.copyAndPutIfAbsent(key, value, this);
            if (newMap == oldMap) {
                return getValue(oldMap.getEntry(key));
            }
        } while (!MAP_UPDATER.compareAndSet(this, oldMap, newMap));
        return null;
    }

    @Override
    public V remove(Object key) {
        expungeStaleEntries();
        var prevValue = getValue(map.getEntry(key));
        UnorderedTrieMap<Object, V> oldMap;
        UnorderedTrieMap<Object, V> newMap;
        do {
            oldMap = map;
            newMap = oldMap.copyAndRemove(key);
            if (newMap == oldMap) {
                break;
            }
        } while (!MAP_UPDATER.compareAndSet(this, oldMap, newMap));
        return prevValue;
    }

    @Override
    protected void expungeStaleEntry(StrongKeyWeakValueEntry<Object, V> entry) {
        UnorderedTrieMap<Object, V> oldMap;
        UnorderedTrieMap<Object, V> newMap;
        do {
            oldMap = map;
            newMap = oldMap.copyAndRemoveEntry(entry);
            if (newMap == oldMap) {
                break;
            }
        } while (!MAP_UPDATER.compareAndSet(this, oldMap, newMap));
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> consumer) {
        expungeStaleEntries();
        map.forEachEntry(new Consumer<Entry<Object, V>>() {
            @Override
            public void accept(Entry<Object, V> entry) {
                V value = entry.getValue();
                if (value != null) {
                    K key = unwrapKey(entry.getKey());
                    if (key != null) {
                        consumer.accept(key, value);
                    }
                }
            }
        });
    }

    /**
     * Create a new {@link Map.Entry} for this transition map.
     */
    @Override
    public Map.Entry<Object, V> apply(Object k, V v) {
        return new StrongKeyWeakValueEntry<>(k, v, queue);
    }
}
