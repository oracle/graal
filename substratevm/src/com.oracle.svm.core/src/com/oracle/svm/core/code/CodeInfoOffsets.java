/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import com.oracle.svm.core.c.struct.OffsetOf;

/**
 * In a few places, we need direct access to the {@link CodeInfoImpl} offsets. This class provides
 * those offsets as the visibility of {@link CodeInfoImpl} is reduced on purpose (it may only be
 * accessed via {@link CodeInfoAccess}).
 */
public final class CodeInfoOffsets {
    private CodeInfoOffsets() {
    }

    public static int objectFields() {
        return OffsetOf.get(CodeInfoImpl.class, "ObjectFields");
    }

    public static int state() {
        return OffsetOf.get(CodeInfoImpl.class, "State");
    }

    public static int gcData() {
        return OffsetOf.get(CodeInfoImpl.class, "GCData");
    }

    public static long frameInfoObjectConstants() {
        return OffsetOf.get(CodeInfoImpl.class, "FrameInfoObjectConstants");
    }

    public static long frameInfoSourceClasses() {
        return OffsetOf.get(CodeInfoImpl.class, "FrameInfoSourceClasses");
    }

    public static long frameInfoSourceMethodNames() {
        return OffsetOf.get(CodeInfoImpl.class, "FrameInfoSourceMethodNames");
    }

    public static long deoptimizationObjectConstants() {
        return OffsetOf.get(CodeInfoImpl.class, "DeoptimizationObjectConstants");
    }

    public static long stackReferenceMapEncoding() {
        return OffsetOf.get(CodeInfoImpl.class, "StackReferenceMapEncoding");
    }

    public static long codeStart() {
        return OffsetOf.get(CodeInfoImpl.class, "CodeStart");
    }

    public static long codeConstantsReferenceMapEncoding() {
        return OffsetOf.get(CodeInfoImpl.class, "CodeConstantsReferenceMapEncoding");
    }

    public static long codeConstantsReferenceMapIndex() {
        return OffsetOf.get(CodeInfoImpl.class, "CodeConstantsReferenceMapIndex");
    }

    public static long areAllObjectsInImageHeap() {
        return OffsetOf.get(CodeInfoImpl.class, "AllObjectsAreInImageHeap");
    }
}
