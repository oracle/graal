/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public class SuspendedInfo {

    protected final JDWPContext context;
    private final SuspendedEvent event;
    private final CallFrame[] stackFrames;
    private final Object thread;
    private final RootNode callerRootNode;
    private boolean forceEarlyInProgress;

    SuspendedInfo(JDWPContext context, SuspendedEvent event, CallFrame[] stackFrames, Object thread, RootNode callerRootNode) {
        this.context = context;
        this.event = event;
        this.stackFrames = stackFrames;
        this.thread = thread;
        this.callerRootNode = callerRootNode;
    }

    // used for pre-collected thread suspension data, before the thread
    // disappears to native code
    SuspendedInfo(JDWPContext context, CallFrame[] stackFrames, Object thread) {
        this.context = context;
        this.event = null;
        this.stackFrames = stackFrames;
        this.thread = thread;
        this.callerRootNode = null;
    }

    public SuspendedEvent getEvent() {
        return event;
    }

    public CallFrame[] getStackFrames() {
        return stackFrames;
    }

    public Object getThread() {
        return thread;
    }

    public RootNode getCallerRootNode() {
        return callerRootNode;
    }

    public Frame getCallerFrame() {
        return stackFrames.length > 1 ? stackFrames[1].getFrame() : null;
    }

    public void setForceEarlyReturnInProgress() {
        forceEarlyInProgress = true;
    }

    public boolean isForceEarlyReturnInProgress() {
        return forceEarlyInProgress;
    }
}
