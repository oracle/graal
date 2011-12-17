/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.collect;

import java.util.*;

/**
 * An object that maps keys to values. A map cannot contain duplicate keys; each key can map to at most one value.
 * <p>
 * The implementations of this interface provide the following behavior be default:
 * <ul>
 * <li>{@code null} keys are illegal in Mapping.</li>
 * <li>The iterators derived from {@link #keys()} and {@link #values()} do not support
 * {@linkplain Iterator#remove() removal} and are <b>not</b> fail-fast (see {@link HashMap} for a description of fail-fast).
 * </ul>
 */
public interface Mapping<K, V> {

    V get(K key);

    V put(K key, V value);

    V remove(K key);

    void clear();

    boolean containsKey(K key);

    int length();

    /**
     * Gets an iterable view of the keys in this map.
     */
    IterableWithLength<K> keys();

    /**
     * Gets an iterable view of the values in this map.
     */
    IterableWithLength<V> values();

}
