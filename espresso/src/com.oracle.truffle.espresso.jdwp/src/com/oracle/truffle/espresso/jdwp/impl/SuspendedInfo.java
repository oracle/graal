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
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public class SuspendedInfo extends EventInfo {

    protected final JDWPContext context;
    private final SuspendedEvent event;
    private final CallFrame[] stackFrames;
    private final Object thread;
    private boolean forceEarlyInProgress;

    SuspendedInfo(JDWPContext context, SuspendedEvent event, CallFrame[] stackFrames, Object thread) {
        this.context = context;
        this.event = event;
        this.stackFrames = stackFrames;
        this.thread = thread;
    }

    // used for pre-collected thread suspension data, before the thread
    // disappears to native code
    protected SuspendedInfo(JDWPContext context, CallFrame[] stackFrames, Object thread) {
        this.context = context;
        this.event = null;
        this.stackFrames = stackFrames;
        this.thread = thread;
    }

    public SuspendedEvent getEvent() {
        return event;
    }

    public CallFrame[] getStackFrames() {
        return stackFrames;
    }

    @Override
    public Object getThread() {
        return thread;
    }

    public void setForceEarlyReturnInProgress() {
        forceEarlyInProgress = true;
    }

    public boolean isForceEarlyReturnInProgress() {
        return forceEarlyInProgress;
    }

    @Override
    public KlassRef getType() {
        return (KlassRef) context.getIds().fromId((int) stackFrames[0].getClassId());
    }

    @Override
    public long getThisId() {
        Object thisObject = stackFrames[0].getThisValue();
        if (thisObject == null) {
            return 0;
        }
        return context.getIds().getId(thisObject);
    }

}
