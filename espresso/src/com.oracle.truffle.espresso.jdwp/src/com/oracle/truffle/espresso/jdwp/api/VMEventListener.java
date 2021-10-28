/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.jdwp.impl.ClassPrepareRequest;
import com.oracle.truffle.espresso.jdwp.impl.FieldBreakpointEvent;
import com.oracle.truffle.espresso.jdwp.impl.MethodBreakpointEvent;
import com.oracle.truffle.espresso.jdwp.impl.RequestFilter;
import com.oracle.truffle.espresso.jdwp.impl.SocketConnection;
import com.oracle.truffle.espresso.jdwp.impl.SteppingInfo;

public interface VMEventListener extends VMListener {
    void setConnection(SocketConnection connection);

    void classUnloaded(KlassRef klass);

    void breakpointHit(BreakpointInfo info, CallFrame frame, Object currentThread);

    boolean vmDied();

    void addClassUnloadRequestId(int id);

    void addThreadStartedRequestId(int id, byte suspendPolicy);

    void addThreadDiedRequestId(int id, byte suspendPolicy);

    void addVMStartRequest(int id);

    void addVMDeathRequest(int id, byte suspendPolicy);

    void addClassPrepareRequest(ClassPrepareRequest request);

    void removeClassPrepareRequest(int requestId);

    void addBreakpointRequest(int requestId, BreakpointInfo info);

    void removeBreakpointRequest(int requestId);

    void stepCompleted(SteppingInfo info, CallFrame currentFrame);

    void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, CallFrame[] callFrames);

    void fieldAccessBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame);

    void fieldModificationBreakpointHit(FieldBreakpointEvent event, Object currentThread, CallFrame callFrame);

    void clearAllBreakpointRequests();

    void removeThreadStartedRequestId();

    void removeThreadDiedRequestId();

    void methodBreakpointHit(MethodBreakpointEvent methodEvent, Object currentThread, CallFrame callFrame);

    void addMonitorContendedEnterRequest(int requestId, RequestFilter filter);

    void removeMonitorContendedEnterRequest(int requestId);

    void addMonitorContendedEnteredRequest(int requestId, RequestFilter filter);

    void removeMonitorContendedEnteredRequest(int requestId);

    void addMonitorWaitRequest(int requestId, RequestFilter filter);

    void removeMonitorWaitRequest(int requestId);

    void addMonitorWaitedRequest(int requestId, RequestFilter filter);

    void removeMonitorWaitedRequest(int requestId);

    void holdEvents();

    void releaseEvents();
}
