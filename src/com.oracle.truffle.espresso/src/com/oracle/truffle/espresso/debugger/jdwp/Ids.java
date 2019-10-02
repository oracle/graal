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

import java.lang.ref.WeakReference;

public class Ids {

    private static volatile long uniqueID = 1;
    private static WeakReference[] objects = new WeakReference[1];
    public static Object UNKNOWN = new Object();

    public static long getIdAsLong(Object object) {
        // lookup in cache
        for (int i = 1; i < objects.length; i++) {
            // really slow lookup path
            Object obj = objects[i].get();
            if (obj == object) {
                return i;
            }
        }
        // cache miss
        return generateUniqueId(object);
    }

    public static byte[] getId(Object object) {
        return toByteArray(getIdAsLong(object));
    }

    public static Object fromId(int id) {
        WeakReference ref = objects[id];
        Object o = ref.get();
        if (o == null) {
            return UNKNOWN;
        } else {
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
        return id;
    }

    public static byte[] toByteArray(long l) {
        byte[] b = new byte[8];
        b[7] = (byte) (l);
        l >>>= 8;
        b[6] = (byte) (l);
        l >>>= 8;
        b[5] = (byte) (l);
        l >>>= 8;
        b[4] = (byte) (l);
        l >>>= 8;
        b[3] = (byte) (l);
        l >>>= 8;
        b[2] = (byte) (l);
        l >>>= 8;
        b[1] = (byte) (l);
        l >>>= 8;
        b[0] = (byte) (l);
        return b;
    }
}


