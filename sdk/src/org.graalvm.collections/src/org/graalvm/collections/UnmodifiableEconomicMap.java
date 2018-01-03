/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections;

/**
 * Unmodifiable memory efficient map data structure.
 */
public interface UnmodifiableEconomicMap<K, V> {

    /**
     * @return the value to which {@code key} is mapped, or {@code null} if this map contains no
     *         mapping for {@code key}.
     */
    V get(K key);

    /**
     * @return the value to which {@code key} is mapped, or {@code defaultValue} if this map
     *         contains no mapping for {@code key}.
     */
    default V get(K key, V defaultValue) {
        V v = get(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    /**
     * @return {@code true} if this map contains a mapping for {@code key}.
     */
    boolean containsKey(K key);

    /**
     * @return the number of key-value mappings in this map.
     */
    int size();

    /**
     * @return {@code true} if this map contains no key-value mappings.
     */
    boolean isEmpty();

    /**
     * @return a {@link Iterable} view of the values contained in this map.
     */
    Iterable<V> getValues();

    /**
     * @return a {@link Iterable} view of the keys contained in this map.
     */
    Iterable<K> getKeys();

    /**
     * @return a {@link Iterable} view of the mappings contained in this map.
     */
    UnmodifiableMapCursor<K, V> getEntries();
}
