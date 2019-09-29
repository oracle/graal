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
package com.oracle.truffle.espresso.debugger;

import com.oracle.truffle.espresso.impl.ObjectKlass;

import java.util.ArrayList;

public class VMEventListeners {

    private static final VMEventListeners DEFAULT = new VMEventListeners();
    private ArrayList<VMEventListener> listeners = new ArrayList<>();

    VMEventListeners() {

    }

    public static VMEventListeners getDefault() {
        return DEFAULT;
    }

    public void registerListener(VMEventListener listener) {
        listeners.add(listener);
    }

    public void classPrepared(ObjectKlass klass) {
        for (VMEventListener listener : listeners) {
            listener.classPrepared(klass);
        }
    }

    public void classUnloaded(ObjectKlass klass) {
        for (VMEventListener listener : listeners) {
            listener.classUnloaded(klass);
        }
    }

    public void threadStarted(Thread thread) {
        for (VMEventListener listener : listeners) {
            listener.threadStarted(thread);
        }
    }

    public void threadDied(Thread thread) {
        for (VMEventListener listener : listeners) {
            listener.threadDied(thread);
        }
    }
}
