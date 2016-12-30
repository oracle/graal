/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating collection objects used during compilation.
 */
public class CollectionsFactory {

    /**
     * Creates a new map without guaranteeing insertion order on the key set. Do <b>not</b> use and
     * use {@link #newLinkedMap()} instead if
     * <ul>
     * <li>key iteration order is required in insertion order,
     * <li>or if key iteration order makes any different to the program and the hash codes of the
     * keys are derived from the random {@link System#identityHashCode(Object)}.</li>
     * </ul>
     */
    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    /**
     * Creates a new map without guaranteeing insertion order on the key set and initializes with a
     * specified capacity. Do <b>not</b> use and use {@link #newLinkedMap(int)} instead if
     * <ul>
     * <li>key iteration order is required in insertion order,
     * <li>or if key iteration order makes any different to the program and the hash codes of the
     * keys are derived from the random {@link System#identityHashCode(Object)}.</li>
     * </ul>
     */
    public static <K, V> Map<K, V> newMap(int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }

    /**
     * Creates a new map without guaranteeing insertion order on the key set and copies all elements
     * from the specified existing map. Do <b>not</b> use and use {@link #newLinkedMap(Map)} instead
     * if
     * <ul>
     * <li>key iteration order is required in insertion order,
     * <li>or if key iteration order makes any different to the program and the hash codes of the
     * keys are derived from the random {@link System#identityHashCode(Object)}.</li>
     * </ul>
     */
    public static <K, V> Map<K, V> newMap(Map<K, V> m) {
        return new HashMap<>(m);
    }

    /**
     * Creates a new map that guarantees insertion order when iterating over its keys.
     */
    public static <K, V> Map<K, V> newLinkedMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Creates a new map that guarantees insertion order when iterating over its keys and
     * initializes with a specified capacity.
     */
    public static <K, V> Map<K, V> newLinkedMap(int initialCapacity) {
        return new LinkedHashMap<>(initialCapacity);
    }

    /**
     * Creates a new map that guarantees insertion order when iterating over its keys and copies all
     * elements from the specified existing map.
     */
    public static <K, V> Map<K, V> newLinkedMap(Map<K, V> m) {
        return new LinkedHashMap<>(m);
    }

    /**
     * Creates a new set without guaranteeing insertion order when iterating over its elements. Do
     * <b>not</b> use and use {@link #newLinkedSet()} instead if
     * <ul>
     * <li>element iteration order is required in insertion order,
     * <li>or if element iteration order makes any different to the program and the hash codes of
     * the elements are derived from the random {@link System#identityHashCode(Object)}.</li>
     * </ul>
     */
    public static <E> Set<E> newSet() {
        return new HashSet<>();
    }

    /**
     * Creates a new set without guaranteeing insertion order when iterating over its elements and
     * inserts all elements of the specified collection. Do <b>not</b> use and use
     * {@link #newLinkedSet(Collection)} instead if
     * <ul>
     * <li>element iteration order is required in insertion order,
     * <li>or if element iteration order makes any different to the program and the hash codes of
     * the elements are derived from the random {@link System#identityHashCode(Object)}.</li>
     * </ul>
     */
    public static <E> Set<E> newSet(Collection<? extends E> c) {
        return new HashSet<>(c);
    }

    /**
     * Creates a new set that guarantees insertion order when iterating over its elements.
     */
    public static <E> Set<E> newLinkedSet() {
        return new LinkedHashSet<>();
    }

    /**
     * Creates a new set that guarantees insertion order when iterating over its elements and adds
     * all elements of the specified collectin.
     */
    public static <E> Set<E> newLinkedSet(Collection<? extends E> c) {
        return new LinkedHashSet<>(c);
    }

}
