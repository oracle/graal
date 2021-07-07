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

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.Arrays;

public abstract class AbstractBreakpointInfo implements BreakpointInfo {

    private final RequestFilter filter;
    private Breakpoint[] breakpoints = new Breakpoint[0];
    private byte suspendPolicy;

    public AbstractBreakpointInfo(RequestFilter filter) {
        this.filter = filter;
    }

    @Override
    public void addBreakpoint(Breakpoint bp) {
        breakpoints = Arrays.copyOf(breakpoints, breakpoints.length + 1);
        breakpoints[breakpoints.length - 1] = bp;
    }

    @Override
    public Breakpoint[] getBreakpoints() {
        return breakpoints;
    }

    @Override
    public RequestFilter getFilter() {
        return filter;
    }

    @Override
    public int getRequestId() {
        return filter.getRequestId();
    }

    @Override
    public byte getEventKind() {
        return filter.getEventKind();
    }

    @Override
    public void addSuspendPolicy(byte policy) {
        this.suspendPolicy = policy;
    }

    @Override
    public byte getSuspendPolicy() {
        return suspendPolicy;
    }

    @Override
    public KlassRef getKlass() {
        return null;
    }

    @Override
    public boolean isCaught() {
        return false;
    }

    @Override
    public boolean isUnCaught() {
        return false;
    }

    @Override
    public Object getThread() {
        return null;
    }

    @Override
    public long getClassId() {
        return 0;
    }

    @Override
    public long getMethodId() {
        return 0;
    }

    @Override
    public byte getTypeTag() {
        return 0;
    }

    @Override
    public long getBci() {
        return 0;
    }

    @Override
    public boolean isExceptionBreakpoint() {
        return false;
    }

    @Override
    public boolean isLineBreakpoint() {
        return false;
    }
}
