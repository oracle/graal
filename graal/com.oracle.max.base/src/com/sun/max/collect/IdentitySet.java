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

import com.sun.max.*;

/**
 * Similar to IdentityHashSet, but not recording 'null' as a set member
 * and not providing any element removal operations.
 *
 * (IdentityHashSet's iterator seems to return 'null' as a member even if it has never been entered.)
 */
public class IdentitySet<T> implements Iterable<T> {

    /**
     * The default initial capacity - MUST be a power of two.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    public static final int MAXIMUM_CAPACITY = 1 << 30;

    // Note: this implementation is partly derived from java.util.HashMap in the standard JDK
    // In particular, it uses a table whose length is guaranteed to be a power of 2.

    /**
     * The number of key-value mappings contained in this map.
     */
    private int numberOfElements;

    /**
     * Gets the number of key-value mappings contained in this map.
     */
    public int numberOfElements() {
        return numberOfElements;
    }

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    private T[] table;

    /**
     * The next size value at which to resize (capacity * load factor).
     */
    private int threshold;

    private void setThreshold() {
        if (table.length == 0) {
            threshold = -1;
        } else {
            threshold = (table.length >> 2) * 3;
        }
    }

    /**
     * Constructs a new, empty IdentitySet.
     */
    public IdentitySet() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs a new, empty IdentitySet.
     * @param initialCapacity  the initial capacity of the Set
     * @throws  {@code IllegalArgumentException} if {@code initialCapacity} is less than zero}
     */
    public IdentitySet(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException();
        }
        // Find a power of 2 >= initialCapacity
        int capacity = Integer.highestOneBit(Math.max(Math.min(MAXIMUM_CAPACITY, initialCapacity), 1));
        if (capacity < initialCapacity) {
            capacity <<= 1;
        }

        final Class<T[]> type = null;
        table = Utils.cast(type, new Object[capacity]);
        setThreshold();
    }

    private void resize(int newTableLength) {
        final T[] oldTable = table;
        final Class<T[]> type = null;
        table = Utils.cast(type, new Object[newTableLength]);
        setThreshold();
        numberOfElements = 0;
        for (int i = 0; i < oldTable.length; i++) {
            final T oldValue = oldTable[i];
            if (oldValue != null) {
                add(oldValue);
            }
        }
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because BuckHashMapping uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits.
     */
    static int hash(int hash) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        final int h = hash ^ ((hash >>> 20) ^ (hash >>> 12));
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Returns index for hash code h.
     */
    static int indexFor(int h, int length) {
        return h & (length - 1);
    }

    /**
     * Adds a given element to this set.
     *
     * @param element the element to add
     * @throws IllegalArgumentException if {@code element == null}
     */
    public void add(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        if (numberOfElements > threshold) {
            resize(table.length * 2);
        }
        final int start = indexFor(System.identityHashCode(element), table.length);
        int i = start;
        do {
            final T entry = table[i];
            if (entry == null) {
                table[i] = element;
                numberOfElements++;
                return;
            }
            if (entry == element) {
                return;
            }
            if (++i == table.length) {
                i = 0;
            }
        } while (i != start);
    }

    /**
     * Determines if a given element is a member of this set.
     *
     * @param element the element to test for membership in this set
     * @return {@code true} if {@code element} is in this set, {@code false} otherwise
     * @throws IllegalArgumentException if {@code element == null}
     */
    public boolean contains(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        if (numberOfElements == 0) {
            return false;
        }
        final int start = indexFor(System.identityHashCode(element), table.length);
        int i = start;
        while (true) {
            final T entry = table[i];
            if (entry == element) {
                return true;
            }
            if (entry == null) {
                return false;
            }
            if (++i == table.length) {
                i = 0;
            }
            assert i != start;
        }
    }

    public Iterator<T> iterator() {
        final Class<T[]> type = null;
        final T[] array = Utils.cast(type, new Object[numberOfElements()]);
        int j = 0;
        for (int i = 0; i < table.length; i++) {
            final T element = table[i];
            if (element != null) {
                array[j++] = element;
            }
        }
        return Arrays.asList(array).iterator();
    }
}
