/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import static com.oracle.truffle.api.object.DebugCounters.shapeCacheExpunged;
import static com.oracle.truffle.api.object.DebugCounters.shapeCacheWeakKeys;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A hash map with weakly referenced values used for shape transitions. Keys may be strongly or
 * weakly referenced (via {@link WeakKey}) and are compared by equality ({@link Object#equals}).
 * Cleared value weak references are expunged only when the map is mutated.
 *
 * Note: Weak keys are not registered with a reference queue. It is assumed that these keys are held
 * strongly by the weak value (i.e. the shape), so that the key will stay alive as long at least as
 * long as the value, and that they will eventually be expunged together after the weak value dies.
 */
abstract sealed class TransitionMap<K, V> permits EconomicTransitionMap, TrieTransitionMap {

    protected final ReferenceQueue<V> queue;

    TransitionMap() {
        this.queue = new ReferenceQueue<>();
    }

    public static <K, V> TransitionMap<K, V> create() {
        if (ObjectStorageOptions.TrieTransitionMap) {
            return new TrieTransitionMap<>();
        } else {
            return new EconomicTransitionMap<>();
        }
    }

    public final boolean containsKey(Object key) {
        return get(key) != null;
    }

    /**
     * Get value from {@link Map.Entry}, if not null.
     */
    protected final V getValue(Map.Entry<? super K, V> entry) {
        return entry == null ? null : entry.getValue();
    }

    public abstract V get(Object key);

    protected abstract V putAnyKey(Object key, V value);

    protected abstract V putAnyKeyIfAbsent(Object key, V value);

    /**
     * Insert with strongly referenced key.
     */
    public final V put(K key, V value) {
        return putAnyKey(key, value);
    }

    /**
     * Insert with strongly referenced key, if absent.
     */
    public final V putIfAbsent(K key, V value) {
        return putAnyKeyIfAbsent(key, value);
    }

    /**
     * Insert with weakly referenced key.
     */
    public final V putWeakKey(K key, V value) {
        shapeCacheWeakKeys.inc();
        WeakKey<K> weakKey = new WeakKey<>(key);
        return putAnyKey(weakKey, value);
    }

    /**
     * Insert with weakly referenced key, if absent.
     */
    public final V putWeakKeyIfAbsent(K key, V value) {
        shapeCacheWeakKeys.inc();
        WeakKey<K> weakKey = new WeakKey<>(key);
        return putAnyKeyIfAbsent(weakKey, value);
    }

    public abstract V remove(Object key);

    @SuppressWarnings("unchecked")
    protected final void expungeStaleEntries() {
        for (Reference<? extends V> r; (r = queue.poll()) != null;) {
            if (r instanceof StrongKeyWeakValueEntry<?, ?> entry) {
                expungeStaleEntry((StrongKeyWeakValueEntry<Object, V>) entry);
                shapeCacheExpunged.inc();
            }
        }
    }

    protected abstract void expungeStaleEntry(StrongKeyWeakValueEntry<Object, V> entry);

    public abstract void forEach(BiConsumer<? super K, ? super V> consumer);

    @SuppressWarnings("unchecked")
    protected final K unwrapKey(Object key) {
        if (key instanceof WeakKey<?>) {
            return ((WeakKey<K>) key).get();
        }
        return (K) key;
    }
}
