/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

/**
 * A {@link Set} backed by an {@link EconomicMap}. The set preserves the insertion order of
 * elements. Elements are added as the keys of the backing {@link EconomicMap}. This collection is a
 * replacement for {@link java.util.HashSet} and {@link java.util.LinkedHashSet} with a low
 * footprint and stable iteration order.
 * <p>
 * Since this implementation aims for compatibility with {@link java.util.HashSet}, it supports
 * {@code null} elements and throws {@link ConcurrentModificationException} when invalid iterator
 * usage is detected.
 *
 * @param <E> the element type
 */
public final class EconomicHashSet<E> extends AbstractSet<E> {
    /**
     * The object used as a value in the backing map.
     */
    private static final Object VALUE_MARKER = new Object();

    /**
     * The map backing this set.
     */
    private final EconomicMap<E, Object> map;

    /**
     * The number of method calls that potentially performed a structural modification of the set
     * (added or removed elements).
     */
    private int modificationCount;

    /**
     * Constructs a new empty hash set.
     */
    public EconomicHashSet() {
        map = EconomicMap.create();
    }

    /**
     * Constructs a new empty hash set with an initial capacity.
     *
     * @param initialCapacity the initial capacity of the set
     */
    public EconomicHashSet(int initialCapacity) {
        map = EconomicMap.create(initialCapacity);
    }

    /**
     * Constructs a new hash set using the elements from another collection.
     *
     * @param collection the elements to be placed in this hash set
     */
    @SuppressWarnings("this-escape")
    public EconomicHashSet(Collection<? extends E> collection) {
        map = EconomicMap.create(collection.size());
        addAll(collection);
    }

    /**
     * Constructs a new empty hash set with an equivalence strategy.
     *
     * @param equivalence the equivalence strategy used by the set
     */
    public EconomicHashSet(Equivalence equivalence) {
        map = EconomicMap.create(equivalence);
    }

    /**
     * Constructs a new empty hash set with an equivalence strategy and an initial capacity.
     *
     * @param equivalence the equivalence strategy used by the set
     * @param initialCapacity the initial capacity of the set
     */
    public EconomicHashSet(Equivalence equivalence, int initialCapacity) {
        map = EconomicMap.create(equivalence, initialCapacity);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return map.containsKey(EconomicHashMap.asEconomicMapKey((E) o));
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<>() {
            /**
             * The iterator over the keys of the backing map.
             */
            private final Iterator<E> iterator = map.getKeys().iterator();

            /**
             * The expected value of modification count.
             */
            private int expectedModificationCount = modificationCount;

            @Override
            public boolean hasNext() {
                checkModificationCount();
                return iterator.hasNext();
            }

            @Override
            public E next() {
                checkModificationCount();
                return EconomicHashMap.fromEconomicMapKey(iterator.next());
            }

            @Override
            public void remove() {
                checkModificationCount();
                iterator.remove();
                modificationCount = ++expectedModificationCount;
            }

            /**
             * Checks that this iterator was not invalidated by a structural modification of the
             * set.
             */
            private void checkModificationCount() {
                if (modificationCount != expectedModificationCount) {
                    throw new ConcurrentModificationException();
                }
            }
        };
    }

    @Override
    public boolean add(E e) {
        ++modificationCount;
        return map.put(EconomicHashMap.asEconomicMapKey(e), VALUE_MARKER) == null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        ++modificationCount;
        return map.removeKey(EconomicHashMap.asEconomicMapKey((E) o)) == VALUE_MARKER;
    }

    @Override
    public void clear() {
        ++modificationCount;
        map.clear();
    }
}
