/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.heap.StoredContinuation;

/**
 * An in-progress stack walk over physical Java frames. The size of this data structure can be
 * queried via {@link JavaStackWalker#sizeOfJavaStackWalk}. Only some fields of this data structure
 * may be accessed directly (see helper methods in {@link JavaStackWalker}).
 */
@RawStructure
public interface JavaStackWalk extends PointerBase {
}

/**
 * The actual implementation. Most stack-walk related fields may only be accessed in
 * {@link JavaStackWalker}.
 *
 * Note that this data structure stores some information about the current physical stack frame (see
 * {@link JavaFrame}) and also some state that is only needed for the stack walk.
 *
 * If interruptible code is executed while a stack walk is in progress, IP and code-related fields
 * in this data structure may contain stale/outdated values (code may get deoptimized).
 *
 * If interruptible code is executed while doing a stack walk for a {@link StoredContinuation}, all
 * SP-related fields may contain stale/outdated values (the GC may move the
 * {@link StoredContinuation}).
 */
@RawStructure
interface JavaStackWalkImpl extends JavaStackWalk {
    @RawField
    boolean getStarted();

    @RawField
    void setStarted(boolean value);

    @RawField
    Pointer getStartSP();

    @RawField
    void setStartSP(Pointer sp);

    @RawField
    Pointer getEndSP();

    @RawField
    void setEndSP(Pointer sp);

    @RawField
    CodePointer getStartIP();

    @RawField
    void setStartIP(CodePointer ip);

    @RawField
    JavaFrameAnchor getFrameAnchor();

    @RawField
    void setFrameAnchor(JavaFrameAnchor anchor);

    /*
     * Fields for the current Java frame - co-located in the same struct. Note that this data is
     * updated in-place when moving to a new frame.
     */
    /* JavaFrame frame; */
}
