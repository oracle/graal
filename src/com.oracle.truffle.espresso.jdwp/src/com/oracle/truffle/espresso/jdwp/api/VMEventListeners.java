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

import com.oracle.truffle.espresso.jdwp.impl.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.impl.JDWPCallFrame;
import com.oracle.truffle.espresso.jdwp.impl.VMEventListener;

public class VMEventListeners {

    private static final VMEventListeners DEFAULT = new VMEventListeners();

    private VMEventListener listener;

    public static VMEventListeners getDefault() {
        return DEFAULT;
    }

    public void registerListener(VMEventListener listener) {
        this.listener = listener;
    }

    public void classPrepared(KlassRef klass, Object currentThread) {
        if (listener != null) {
            listener.classPrepared(klass, currentThread);
        }
    }

    public void classUnloaded(KlassRef klass) {
        if (listener != null) {
            listener.classUnloaded(klass);
        }
    }

    public void threadStarted(Object thread) {
        if (listener != null) {
            listener.threadStarted(thread);
        }
    }

    public void threadDied(Object thread) {
        if (listener != null) {
            listener.threadDied(thread);
        }
    }

    public void breakpointHit(BreakpointInfo info, Object currentThread) {
        if (listener != null) {
            listener.breakpointHIt(info, currentThread);
        }
    }

    public void stepCompleted(int commandRequestId, JDWPCallFrame currentFrame) {
        if (listener != null) {
            listener.stepCompleted(commandRequestId, currentFrame);
        }
    }
}
