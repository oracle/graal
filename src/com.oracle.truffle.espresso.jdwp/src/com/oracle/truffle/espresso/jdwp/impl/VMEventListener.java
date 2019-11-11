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

import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.concurrent.Callable;

public interface VMEventListener {
    void classPrepared(KlassRef klass, Object currentThread);
    void classUnloaded(KlassRef klass);
    void threadStarted(Object thread);
    void threadDied(Object thread);
    void breakpointHIt(BreakpointInfo info, Object currentThread);

    void addClassUnloadRequestId(int id);
    void addThreadStartedRequestId(int id);
    void addThreadDiedRequestId(int id);

    Callable<Void> addClassPrepareRequest(ClassPrepareRequest request);
    void removeClassPrepareRequest(int requestId);

    void addBreakpointRequest(int requestId, BreakpointInfo info);
    void removeBreakpointRequest(int requestId);

    void stepCompleted(int commandRequestId, JDWPCallFrame currentFrame);

    void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, JDWPCallFrame callFrame);

    void addFieldBreakpointRequest(FieldBreakpointInfo info);
    boolean hasFieldModificationBreakpoint(FieldRef field, Object receiver, Object value);
    boolean hasFieldAccessBreakpoint(FieldRef field, Object receiver);
    void removedFieldBreakpoint(FieldRef field);

    void fieldAccessBreakpointHit(FieldBreakpointInfo info, Object currentThread, JDWPCallFrame callFrame);
    void fieldModificationBreakpointHit(FieldBreakpointInfo info, Object currentThread, JDWPCallFrame callFrame);
}
