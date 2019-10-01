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

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.impl.ObjectKlass;

import java.util.HashSet;

public class VMEventListeners {

    private static final VMEventListeners DEFAULT = new VMEventListeners();

    // TODO(Gregersen) - change to array or even a single listener for now
    private HashSet<VMEventListener> listeners = new HashSet<>();

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

    public void breakpointHit(BreakpointInfo info) {
        for (VMEventListener listener : listeners) {
            listener.breakpointHIt(info);
        }
    }
}
