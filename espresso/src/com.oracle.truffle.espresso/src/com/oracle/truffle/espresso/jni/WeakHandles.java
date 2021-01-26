/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Manages a collection of weak references associated to handles.
 */
public class WeakHandles<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private final WeakHashMap<T, Integer> map;
    private final LinkedList<Integer> freeList = new LinkedList<>();

    // Non-empty.
    private WeakReference<T>[] handles;

    /**
     * Creates a handle collection pre-allocated capacity.
     *
     * @param initialCapacity must be > 0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public WeakHandles(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }
        map = new WeakHashMap<>(initialCapacity);
        handles = new WeakReference[initialCapacity];
    }

    public WeakHandles() {
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
     * @param index handle, must be > 0 and fit in an integer
     * @return the object associated with the handle or null if was collected
     */
    @SuppressWarnings("unchecked")
    public T getObject(long index) {
        if (index <= 0) {
            throw new IllegalArgumentException("index");
        }
        WeakReference<T> weakRef = CompilerDirectives.castExact(handles[Math.toIntExact(index)], WeakReference.class);
        return weakRef != null
                        ? weakRef.get()
                        : null;
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
            if (handles[i] == null || handles[i].get() == null) {
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
            WeakReference<T>[] newHandles = Arrays.copyOf(handles, 2 * handles.length);
            for (int i = handles.length; i < newHandles.length; ++i) {
                freeList.addLast(i);
            }
            handles = newHandles;
            index = freeList.removeFirst();
        }
        assert index >= 0;
        handles[index] = new WeakReference<>(object);
        map.put(object, index);
        return index;
    }
}
