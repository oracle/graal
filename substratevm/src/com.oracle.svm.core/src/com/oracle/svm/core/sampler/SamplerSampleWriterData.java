/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

/**
 * A data structure that holds the mutable state of a {@link SamplerSampleWriter}. Typically, it is
 * allocated on the stack.
 */
@RawStructure
public interface SamplerSampleWriterData extends PointerBase {
    /**
     * Gets the buffer that data will be written to.
     */
    @RawField
    SamplerBuffer getSamplerBuffer();

    /**
     * Sets the buffer that data will be written to.
     */
    @RawField
    void setSamplerBuffer(SamplerBuffer buffer);

    /**
     * Gets the start position for the current sample write.
     */
    @RawField
    Pointer getStartPos();

    /**
     * Sets the start position for the current sample write.
     */
    @RawField
    void setStartPos(Pointer value);

    /**
     * Gets the current position of the sample write. This position is moved forward as data is
     * written for a sample.
     */
    @RawField
    Pointer getCurrentPos();

    /**
     * Sets the current position of the sample write.
     */
    @RawField
    void setCurrentPos(Pointer value);

    /**
     * Returns the position where the buffer ends. Writing of data cannot exceed this position.
     */
    @RawField
    Pointer getEndPos();

    /**
     * Sets the position where the buffer ends.
     */
    @RawField
    void setEndPos(Pointer value);

    /**
     * Returns the hash code of sample.
     */
    @RawField
    int getHashCode();

    /**
     * Sets the hash code of sample.
     */
    @RawField
    void setHashCode(int value);

    /**
     * Returns the number of frames that should be skipped during stack walk.
     */
    @RawField
    int getSkipCount();

    /**
     * Sets the number of frames that should be skipped during stack walk.
     */
    @RawField
    void setSkipCount(int value);

    /**
     * Returns the max depth of stack walk.
     */
    @RawField
    int getMaxDepth();

    /**
     * Sets the max depth of stack walk.
     */
    @RawField
    void setMaxDepth(int value);

    /**
     * Returns the number of frames that were visited or skipped.
     */
    @RawField
    int getSeenFrames();

    /**
     * Sets the number of frames that were visited or skipped.
     */
    @RawField
    void setSeenFrames(int value);

    /**
     * Returns {@code true} if the stack size exceeds {@link #getMaxDepth()}.
     */
    @RawField
    boolean getTruncated();

    /**
     * Sets the truncation status of stack walking.
     */
    @RawField
    void setTruncated(boolean value);

    /**
     * Returns {@code true} if it is allowed to allocate new buffers and {@code false} otherwise.
     */
    @RawField
    boolean getAllowBufferAllocation();

    /**
     * Determines if allocating new buffers is allowed.
     */
    @RawField
    void setAllowBufferAllocation(boolean allowBufferAllocation);
}
