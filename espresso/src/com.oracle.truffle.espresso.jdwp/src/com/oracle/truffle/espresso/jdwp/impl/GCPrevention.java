/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public final class GCPrevention {

    // simply hold a strong reference to all objects for which
    // GC should be disabled
    private final HashSet<Object> prevent = new HashSet<>();
    private final HashMap<Object, ArrayList<Object>> activeWhileSuspended = new HashMap<>();

    public void disableGC(Object object) {
        prevent.add(object);
    }

    public void enableGC(Object object) {
        prevent.remove(object);
    }

    public void clearAll() {
        prevent.clear();
    }

    public synchronized void setActiveWhileSuspended(Object guestThread, Object obj) {
        activeWhileSuspended.putIfAbsent(guestThread, new ArrayList<>());
        activeWhileSuspended.get(guestThread).add(obj);
    }

    public synchronized void releaseActiveWhileSuspended(Object guestThread) {
        activeWhileSuspended.remove(guestThread);
    }

}
