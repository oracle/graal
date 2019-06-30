/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.CodeReferenceMapEncoder;
import com.oracle.svm.core.util.VMError;

/**
 * Information about an instruction pointer (IP), created and returned by methods in
 * {@link CodeInfoTable}.
 */
public class CodeInfoQueryResult {

    /**
     * Marker value for the frame size of entry points that is used by {@link #isEntryPoint()}.
     */
    public static final int ENTRY_POINT_FRAME_SIZE = 1;

    /**
     * Marker value returned by {@link #getExceptionOffset()} when no exception handler is
     * registered for the {@link #getIP() IP}.
     */
    public static final int NO_EXCEPTION_OFFSET = 0;

    /**
     * Marker value returned by {@link #getReferenceMapIndex()} when no reference map is registered
     * for the {@link #getIP() IP}.
     */

    public static final int NO_REFERENCE_MAP = -1;

    /**
     * Marker value returned by {@link #getReferenceMapIndex()} when the reference map is empty for
     * the {@link #getIP() IP}.
     */
    public static final int EMPTY_REFERENCE_MAP = 0;

    /**
     * Marker value of {@link #getFrameInfo()} when no frame information is available for the
     * {@link #getIP() IP}.
     */
    protected static final FrameInfoQueryResult NO_FRAME_INFO = null;

    protected CodePointer ip;
    protected long totalFrameSize;
    protected long exceptionOffset;
    protected long referenceMapIndex;
    protected FrameInfoQueryResult frameInfo;

    /**
     * Returns the instruction pointer that was queried.
     */
    public CodePointer getIP() {
        return ip;
    }

    /**
     * Indicates if the method containing the IP is an entry point method.
     */
    public boolean isEntryPoint() {
        return totalFrameSize == ENTRY_POINT_FRAME_SIZE;
    }

    /**
     * Returns the frame size of the method containing the IP.
     */
    public long getTotalFrameSize() {
        VMError.guarantee(totalFrameSize != ENTRY_POINT_FRAME_SIZE, "Entry point method: no valid frame size");
        return totalFrameSize;
    }

    /**
     * Returns the exception handler offset, i.e., the IP difference between the regular return
     * address and the exception handler entry point, for the IP.
     */
    public long getExceptionOffset() {
        return exceptionOffset;
    }

    /**
     * Index into the {@link CodeInfoAccess#getReferenceMapEncoding(CodeInfo)} encoded reference
     * map} for the code. Encoding is handled by {@link CodeReferenceMapEncoder}, decoding is
     * handled by {@link CodeReferenceMapDecoder}.
     */
    public long getReferenceMapIndex() {
        return referenceMapIndex;
    }

    /**
     * Stack frame information used, e.g., for deoptimization and printing of stack frames in debug
     * builds.
     */
    public FrameInfoQueryResult getFrameInfo() {
        return frameInfo;
    }
}
