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

public class BreakpointInfo {

    private final int requestId;
    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long bci;

    private Breakpoint breakpoint;

    public BreakpointInfo(int requestId, byte tag, long classId, long methodId, long bci) {
        this.requestId = requestId;
        this.typeTag = tag;
        this.classId = classId;
        this.methodId = methodId;
        this.bci = bci;
    }

    public int getRequestId() {
        return requestId;
    }

    public long getClassId() {
        return classId;
    }

    public long getMethodId() {
        return methodId;
    }

    public byte getTypeTag() {
        return typeTag;
    }

    public long getBci() {
        return bci;
    }

    @Override
    public String toString() {
        return "typeTag: " + typeTag + ", classId: " + classId + ", methodId: " + methodId + ", bci: " + bci;
    }

    public void setBreakpoint(Breakpoint bp) {
        this.breakpoint = bp;
    }

    public Breakpoint getBreakpoint() {
        return breakpoint;
    }
}
