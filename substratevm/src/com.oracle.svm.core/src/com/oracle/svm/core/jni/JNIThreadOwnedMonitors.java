/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni;

import java.util.IdentityHashMap;
import java.util.function.BiConsumer;

import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

/**
 * Keeps track of the object monitors of each thread that are acquired via JNI.
 */
public class JNIThreadOwnedMonitors {

    @SuppressWarnings("rawtypes") //
    private static final FastThreadLocalObject<IdentityHashMap> ownedMonitors = FastThreadLocalFactory.createObject(IdentityHashMap.class, "JNIThreadOwnedMonitors.ownedMonitors");

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<Object, Integer> mutableMap() {
        if (ownedMonitors.get() == null) {
            ownedMonitors.set(new IdentityHashMap<Object, Integer>());
        }
        return ownedMonitors.get();
    }

    public static void entered(Object obj) {
        IdentityHashMap<Object, Integer> map = mutableMap();
        Integer depth = map.get(obj);
        int newDepth = (depth == null) ? 1 : (depth + 1);
        map.put(obj, newDepth);
    }

    public static void exited(Object obj) {
        IdentityHashMap<Object, Integer> map = mutableMap();
        int depth = map.remove(obj);
        if (depth > 1) {
            map.put(obj, depth - 1);
        }
    }

    /**
     * Performs the specified action for each monitor owned by this thread, with the recursive depth
     * (>= 1) as second input.
     */
    public static void forEach(BiConsumer<Object, Integer> action) {
        if (ownedMonitors.get() != null) {
            mutableMap().forEach(action);
        }
    }

    static int ownedMonitorsCount() {
        return (ownedMonitors.get() != null) ? ownedMonitors.get().size() : 0;
    }
}
