/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

import java.lang.ref.WeakReference;

/**
 * Class that keeps an ID representation of all entities when
 * communicating with a debugger through JDWP.
 * Each entity will be assigned a unique ID.
 * Only weak references are kept for entities.
 */
public class Ids<T> {

    private volatile long uniqueID = 1;

    /**
     * All entities stored while communicating with the debugger.
     * The array will be expanded whenever an ID for a new entity
     * is requested.
     */
    @SuppressWarnings("unchecked")
    private WeakReference<T>[] objects = new WeakReference[1];

    /**
     * A special object representing the null value.
     * This object must be passed on by the implementing language.
     */
    private final T nullObject;

    public Ids(T nullObject) {
        getIdAsLong(nullObject);
        this.nullObject = nullObject;
    }

    /**
     * Returns the unique ID representing the input object.
     * @param object
     * @return the ID of the object
     */
    public long getIdAsLong(T object) {
        // lookup in cache
        for (int i = 1; i < objects.length; i++) {
            // really slow lookup path
            Object obj = objects[i].get();
            if (obj == object) {
                return i;
            }
        }
        // cache miss, so generate a new ID
        return generateUniqueId(object);
    }

    /**
     * Returns the object that is stored under the input ID.
     * @param id the ID assigned to a object by {@code getIdAsLong()}
     * @return the object stored under the ID
     */
    public T fromId(int id) {
        WeakReference<T> ref = objects[id];
        T o = ref.get();
        if (o == null) {
            return nullObject;
        } else {
            return o;
        }
    }

    /*
    Generate a unique ID for a given object. Expand the underlying array and
    insert the object at the last index in the new array.
     */
    @SuppressWarnings("unchecked")
    private synchronized long generateUniqueId(T object) {
        long id = uniqueID++;
        assert objects.length == id - 1;

        WeakReference<T>[] expandedArray = new WeakReference[objects.length + 1];
        System.arraycopy(objects, 1, expandedArray, 1, objects.length - 1);
        expandedArray[objects.length] = new WeakReference<>(object);
        objects = expandedArray;
        return id;
    }
}


