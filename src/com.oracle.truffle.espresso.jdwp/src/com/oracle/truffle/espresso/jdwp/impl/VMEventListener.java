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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.VMListener;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.concurrent.Callable;

public interface VMEventListener extends VMListener {
    void setConnection(SocketConnection connection);

    void classUnloaded(KlassRef klass);

    void breakpointHit(BreakpointInfo info, CallFrame frame, Object currentThread);

    void vmDied();

    void addClassUnloadRequestId(int id);

    void addThreadStartedRequestId(int id, byte suspendPolicy);

    void addThreadDiedRequestId(int id, byte suspendPolicy);

    void addVMStartRequest(int id);

    void addVMDeathRequest(int id);

    Callable<Void> addClassPrepareRequest(ClassPrepareRequest request);

    void removeClassPrepareRequest(int requestId);

    void addBreakpointRequest(int requestId, BreakpointInfo info);

    void removeBreakpointRequest(int requestId);

    void stepCompleted(int commandRequestId, byte suspendPolicy, Object guestThread, CallFrame currentFrame);

    void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, CallFrame[] callFrames);

    void increaseFieldBreakpointCount();

    void decreaseFieldBreakpointCount();

    void increaseMethodBreakpointCount();

    void decreaseMethodBreakpointCount();

    void fieldAccessBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame);

    void fieldModificationBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame);

    void clearAllBreakpointRequests();

    void removeThreadStartedRequestId();

    void removeThreadDiedRequestId();

    void methodBreakpointHit(MethodBreakpointEvent methodEvent, Object currentThread, CallFrame callFrame);
}
