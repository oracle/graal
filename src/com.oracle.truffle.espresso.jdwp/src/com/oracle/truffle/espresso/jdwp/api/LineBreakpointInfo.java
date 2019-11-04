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

import com.oracle.truffle.espresso.jdwp.impl.AbstractBreakpointInfo;
import com.oracle.truffle.espresso.jdwp.impl.RequestFilter;

/**
 * Class holding information about a breakpoint that cannot be obtained from
 * the Truffle Breakpoint instance.
 */
public class LineBreakpointInfo extends AbstractBreakpointInfo {

    private final RequestFilter filter;
    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long bci;

    public LineBreakpointInfo(RequestFilter filter, byte tag, long classId, long methodId, long bci) {
        super(filter.getRequestId());
        this.filter = filter;
        this.typeTag = tag;
        this.classId = classId;
        this.methodId = methodId;
        this.bci = bci;
    }

    public Object getThread() {
        return filter.getThread();
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

    @Override
    public boolean isLineBreakpoint() {
        return true;
    }
}
