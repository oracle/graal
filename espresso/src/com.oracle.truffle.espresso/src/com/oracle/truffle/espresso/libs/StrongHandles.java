/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

// todo(trachsel) GR-65608 polish Strong and WeakHandles 
public class StrongHandles<T> {
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private final HashMap<T, Integer> map;
    private final LinkedList<Integer> freeList = new LinkedList<>();

    // Non-empty.
    private T[] handles;

    /**
     * Creates a handle collection pre-allocated capacity.
     *
     * @param initialCapacity must be > 0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public StrongHandles(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }
        map = new HashMap<>(initialCapacity);
        handles = (T[]) new Object[initialCapacity]; // Casting
    }

    public StrongHandles() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a new handle for the given object or returns an existing handle if the object is
     * already in the collection.
     *
     * @return new or existing handle, provided handles are guanteed to be > 0
     */
    @TruffleBoundary
    public synchronized int handlify(T object) {
        Objects.requireNonNull(object);
        Integer handle = map.get(object);
        return handle != null
                        ? handle
                        : addHandle(object);
    }

    /**
     * Returns the object associated with a handle. This operation is performance-critical,
     * shouldn't block.
     *
     * @param indexJ handle, must be > 0 and fit in an integer
     * @return the object associated with the handle or null if was collected
     */
    @SuppressWarnings("unchecked")
    public T getObject(long indexJ) {
        int index = Math.toIntExact(indexJ);
        if (invalidIndex(index)) {
            return null;
        }
        return handles[index];

    }

    /**
     * Returns the handle associated with a given object.
     *
     * @return The handle associated with the given object or -1 if the object doesn't have a handle
     *         or the object was collected. A valid handle is guaranteed to be != 0.
     */
    @TruffleBoundary
    public synchronized long getIndex(T object) {
        Integer index = map.get(Objects.requireNonNull(object));
        return index != null
                        ? index
                        : -1;
    }

    @TruffleBoundary
    private int getFreeSlot() {
        if (!freeList.isEmpty()) {
            return freeList.removeFirst();
        }
        // 0 is a dummy entry, start at 1 to avoid NULL handles.
        for (int i = 1; i < handles.length; ++i) {
            if (handles[i] == null) {
                freeList.addLast(i);
            }
        }
        return freeList.isEmpty()
                        ? -1
                        : freeList.removeFirst();
    }

    @TruffleBoundary
    private synchronized int addHandle(T object) {
        Objects.requireNonNull(object);
        int index = getFreeSlot();
        if (index < 0) { // no slot available
            T[] newHandles = Arrays.copyOf(handles, 2 * handles.length);
            for (int i = handles.length; i < newHandles.length; ++i) {
                freeList.addLast(i);
            }
            handles = newHandles;
            index = freeList.removeFirst();
        }
        assert index >= 0;
        handles[index] = object;
        map.put(object, index);
        return index;
    }

    @TruffleBoundary
    public synchronized boolean clean(T object) {
        Objects.requireNonNull(object);
        Integer index = map.get(object);
        if (index == null) {
            return false;
        }
        cleanIndex(index);
        return true;
    }

    public synchronized boolean cleanIndex(long indexJ) {
        int index = Math.toIntExact(indexJ);
        if (invalidIndex(index)) {
            return false;
        }
        T object = handles[index];
        if (object != null) {
            handles[index] = null;
            map.remove(object);
            freeList.addLast(index);
        }
        return true;
    }

    private boolean invalidIndex(int index) {
        return (index <= 0 || index >= handles.length);
    }
}
