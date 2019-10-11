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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.espresso.runtime.StaticObject;
import java.lang.ref.WeakReference;

public class Ids {

    private static volatile long uniqueID = 1;
    private static WeakReference[] objects = new WeakReference[1];

    static {
        // initialize with StaticObject.NULL
        getIdAsLong(StaticObject.NULL);
    }

    public static long getIdAsLong(Object object) {
        // lookup in cache
        for (int i = 1; i < objects.length; i++) {
            // really slow lookup path
            Object obj = objects[i].get();
            if (obj == object) {
                //System.out.println("returning ID: " + i + " from cache for object: " + object);
                return i;
            }
        }
        // cache miss
        return generateUniqueId(object);
    }

    public static Object fromId(int id) {
        WeakReference ref = objects[id];
        Object o = ref.get();
        if (o == null) {
            return StaticObject.NULL;
        } else {
            //System.out.println("getting object: " + o + " from ID: " + id);
            return o;
        }
    }

    private synchronized static long generateUniqueId(Object object) {
        long id = uniqueID++;
        assert objects.length == id - 1;

        WeakReference[] expandedArray = new WeakReference[objects.length + 1];
        System.arraycopy(objects, 1, expandedArray, 1, objects.length - 1);
        expandedArray[objects.length] = new WeakReference<>(object);
        objects = expandedArray;
        //System.out.println("ID: " + id + " for object: " + object);
        return id;
    }
}


