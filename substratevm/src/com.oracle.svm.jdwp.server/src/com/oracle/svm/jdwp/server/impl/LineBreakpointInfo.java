/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.jdwp.server.impl;

/**
 * Breakpoint info based on a BCI location.
 */
public final class LineBreakpointInfo extends AbstractBreakpointInfo {

    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long bci;

    public LineBreakpointInfo(RequestFilter filter, Breakpoints breakpoints, byte tag, long classId, long methodId, long bci) {
        super(filter, breakpoints);
        this.typeTag = tag;
        this.classId = classId;
        this.methodId = methodId;
        this.bci = bci;
    }

    @Override
    public boolean matches(long matchingClassId, long matchingMethodId, int matchingBci) {
        return this.methodId == matchingMethodId && this.bci == matchingBci;
    }

    @Override
    public long getClassId() {
        return classId;
    }

    @Override
    public long getMethodId() {
        return methodId;
    }

    @Override
    public long getBci() {
        return bci;
    }

    @Override
    public String toString() {
        return "typeTag: " + typeTag + ", classId: " + classId + ", methodId: " + methodId + ", bci: " + bci;
    }
}
