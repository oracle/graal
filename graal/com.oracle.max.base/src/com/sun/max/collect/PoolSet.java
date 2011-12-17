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
 * A representation for a subset of objects in a {@linkplain Pool pool}. The recommended mechanism for creating a pool
 * set is by calling {@link #noneOf(Pool)}, {@link #allOf(Pool)}, {@link #of(Pool, PoolObject[])} or
 * {@link #of(Pool, PoolObject, PoolObject...)}. These methods ensure that the most efficient and compact pool set
 * object is created based on the length of the underlying pool.
 */
public abstract class PoolSet<T extends PoolObject> implements Cloneable, Iterable<T> {

    protected final Pool<T> pool;

    protected PoolSet(Pool<T> pool) {
        this.pool = pool;
    }

    /**
     * Gets the number of objects in this set.
     */
    public abstract int size();

    /**
     * Adds a value to this set. The value must be in the {@linkplain #pool() underlying pool}.
     */
    public abstract void add(T value);

    /**
     * Determines if a given value is in this set. The value must be in the {@linkplain #pool() underlying pool}.
     */
    public abstract boolean contains(T value);

    /**
     * Determines if this set contains all the values present in a given set.
     */
    public boolean containsAll(PoolSet<T> others) {
        for (T value : others) {
            if (!contains(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a given value from this set. The value must be in the {@linkplain #pool() underlying pool}.
     *
     * @return true if this set contained {@code value}
     */
    public abstract boolean remove(T value);

    /**
     * Removes an arbitrary value from this set. The value must be in the {@linkplain #pool() underlying pool}.
     *
     * @throws NoSuchElementException if this set is empty
     */
    public abstract T removeOne() throws NoSuchElementException;

    /**
     * Gets the pool containing the values that are in or may be added to this set.
     */
    public Pool<T> pool() {
        return pool;
    }

    /**
     * Clears all entries from this set.
     */
    public abstract void clear();

    /**
     * Adds all entries from the pool to this set.
     *
     * @return this set
     */
    public abstract PoolSet<T> addAll();

    /**
     * Adds all entries from another pool set to this set.
     */
    public void or(PoolSet<T> others) {
        for (T element : others) {
            add(element);
        }
    }

    /**
     * Removes all the entries from this set that are not in a given set.
     */
    public abstract void and(PoolSet<T> others);

    /**
     * Creates a copy of this pool set. The copy has the same pool object as this pool set.
     */
    @Override
    public abstract PoolSet<T> clone();

    /**
     * @return true if there are no values in this set
     */
    public abstract boolean isEmpty();

    /**
     * @see #toString(PoolSet)
     */
    @Override
    public String toString() {
        return toString(this);
    }

    public T[] toArray(T[] a) {
        assert a.length == size();
        int i = 0;
        for (T element : this) {
            a[i++] = element;
        }
        return a;
    }

    /**
     * Creates an empty pool set for a given pool.
     *
     * @param <T> the type of objects in {@code pool}
     * @param pool the pool of objects that the returned set provides a view upon
     * @return an empty pool set that can be subsequently modified to contain objects from {@code pool}
     */
    public static <T extends PoolObject> PoolSet<T> noneOf(Pool<T> pool) {
        if (pool.length() <= PoolSet64.MAX_POOL_SIZE) {
            return new PoolSet64<T>(pool);
        }
        if (pool.length() <= PoolSet128.MAX_POOL_SIZE) {
            return new PoolSet128<T>(pool);
        }
        return new PoolBitSet<T>(pool);
    }

    /**
     * Creates a pool set initially containing all the objects in a given pool.
     *
     * @param <T> the type of objects in {@code pool}
     * @param pool the pool of objects that the returned set provides a view upon
     * @return a pool set containing all the objects in {@code pool}
     */
    public static <T extends PoolObject> PoolSet<T> allOf(Pool<T> pool) {
        return noneOf(pool).addAll();
    }

    /**
     * Creates a pool set initially containing one or more objects from a given pool.
     *
     * @param <T> the type of objects in {@code pool}
     * @param <S> the type of objects that can be added to the pool by this method
     * @param pool the pool of objects that the returned set provides a view upon
     * @param first an object that will be in the returned set
     * @param rest zero or more objects that will be in the returned set
     * @return a pool set containing {@code first} and all the objects in {@code rest}
     */
    public static <T extends PoolObject, S extends T> PoolSet<T> of(Pool<T> pool, S first, S... rest) {
        final PoolSet<T> poolSet = noneOf(pool);
        poolSet.add(first);
        for (T object : rest) {
            poolSet.add(object);
        }
        return poolSet;
    }

    /**
     * Creates a pool set initially containing all the objects specified by a given array that are also in a given pool.
     *
     * @param <T> the type of objects in {@code pool}
     * @param <S> the type of objects that can be added to the pool by this method
     * @param pool the pool of objects that the returned set provides a view upon
     * @param objects zero or more objects that will be in the returned set
     * @return a pool set containing all the objects in {@code objects}
     */
    public static <T extends PoolObject, S extends T> PoolSet<T> of(Pool<T> pool, S[] objects) {
        final PoolSet<T> poolSet = noneOf(pool);
        for (T object : objects) {
            poolSet.add(object);
        }
        return poolSet;
    }

    /**
     * Adds all objects returned by a given iterable's iterator to a given pool set.
     *
     * @param <T> the type of objects in {@code poolSet}
     * @param poolSet the set to which the objects are added
     * @param elements a collection of objects
     */
    public static <T extends PoolObject> void addAll(PoolSet<T> poolSet, Iterable<T> elements) {
        for (T element : elements) {
            poolSet.add(element);
        }
    }

    public static <T extends PoolObject> boolean match(PoolSet<T> poolSet1, PoolSet<T> poolSet2) {
        if (!poolSet1.pool().equals(poolSet2.pool())) {
            return false;
        }
        final Iterator<T> iterator1 = poolSet1.iterator();
        final Iterator<T> iterator2 = poolSet2.iterator();
        while (iterator1.hasNext() && iterator2.hasNext()) {
            if (!iterator1.next().equals(iterator2.next())) {
                return false;
            }
        }
        return iterator1.hasNext() == iterator2.hasNext();
    }

    /**
     * Gets a string representation of a given pool set. The returned string is delimited by "{" and "}". For each
     * object in the given pool set, its {@code toString()} representation is added to the string followed by ", " if it
     * is not the last object in the set.
     *
     * @param poolSet a pool set for which a string representation is required
     */
    public static String toString(PoolSet poolSet) {
        String s = "{";
        String delimiter = "";
        for (Object value : poolSet) {
            s += delimiter + value;
            delimiter = ", ";
        }
        s += "}";
        return s;
    }
}
