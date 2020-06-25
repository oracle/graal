/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Unmodifiable memory efficient map. See {@link EconomicMap} for the underlying data structure and
 * its properties.
 *
 * @since 19.0
 */
public interface UnmodifiableEconomicMap<K, V> {

    /**
     * Returns the value to which {@code key} is mapped, or {@code null} if this map contains no
     * mapping for {@code key}. The {@code key} must not be {@code null}.
     *
     * @since 19.0
     */
    V get(K key);

    /**
     * Returns the value to which {@code key} is mapped, or {@code defaultValue} if this map
     * contains no mapping for {@code key}. The {@code key} must not be {@code null}.
     *
     * @since 19.0
     */
    default V get(K key, V defaultValue) {
        V v = get(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    /**
     * Returns {@code true} if this map contains a mapping for {@code key}. Returns always
     * {@code false} if the {@code key} is {@code null}.
     *
     * @since 19.0
     */
    boolean containsKey(K key);

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @since 19.0
     */
    int size();

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @since 19.0
     */
    boolean isEmpty();

    /**
     * Returns a {@link Iterable} view of the values contained in this map.
     *
     * @since 19.0
     */
    Iterable<V> getValues();

    /**
     * Returns a {@link Iterable} view of the keys contained in this map.
     *
     * @since 19.0
     */
    Iterable<K> getKeys();

    /**
     * Returns a {@link UnmodifiableMapCursor} view of the mappings contained in this map.
     *
     * @since 19.0
     */
    UnmodifiableMapCursor<K, V> getEntries();
}
