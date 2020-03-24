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

import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMListener;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public final class EmptyListener implements VMListener {

    @Override
    public void vmStarted(boolean suspend) {

    }

    @Override
    public void classPrepared(KlassRef klass, Object prepareThread, boolean alreadyPrepared) {

    }

    @Override
    public void threadStarted(Object thread) {

    }

    @Override
    public void threadDied(Object thread) {

    }

    @Override
    public boolean hasFieldModificationBreakpoint(FieldRef field, Object receiver, Object value) {
        return false;
    }

    @Override
    public boolean hasFieldAccessBreakpoint(FieldRef field, Object receiver) {
        return false;
    }

    @Override
    public boolean hasMethodBreakpoint(MethodRef method, Object returnValue) {
        return false;
    }

    @Override
    public void monitorWait(Object monitor, long timeout) {

    }

    @Override
    public void monitorWaited(Object monitor, boolean timedOut) {

    }

    @Override
    public void onContendedMonitorEnter(Object monitor) {

    }

    @Override
    public void onContendedMonitorEntered(Object monitor) {

    }

    @Override
    public Object getCurrentContendedMonitor(Object guestThread) {
        return null;
    }

    @Override
    public Object getEarlyReturnValue() {
        return null;
    }

    @Override
    public Object getAndRemoveEarlyReturnValue() {
        return null;
    }

    @Override
    public void forceEarlyReturn(Object returnValue) {

    }

    @Override
    public void onMonitorEnter(Object monitor) {

    }

    @Override
    public void onMonitorExit(Object monitor) {

    }
}
