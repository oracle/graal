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
import org.graalvm.word.UnsignedWord;

/**
 * A {@link SamplerBuffer} is a block of native memory into which the results of stack walks are
 * written.
 */
@RawStructure
public interface SamplerBuffer extends PointerBase {

    /**
     * Returns the buffer that is next in the {@link SamplerBufferStack}, otherwise null.
     */
    @RawField
    SamplerBuffer getNext();

    /**
     * Sets the successor to this buffer in the {@link SamplerBufferStack}.
     */
    @RawField
    void setNext(SamplerBuffer buffer);

    /**
     * Returns the JFR id of the thread that owns this buffer.
     */
    @RawField
    long getOwner();

    /**
     * Sets the JFR id of the thread that owns this buffer.
     */
    @RawField
    void setOwner(long threadId);

    /**
     * Returns the current position. Any data before this position is valid sample data.
     */
    @RawField
    Pointer getPos();

    /**
     * Sets the current position.
     */
    @RawField
    void setPos(Pointer pos);

    /**
     * Returns the size of the buffer. This excludes the header of the buffer.
     */
    @RawField
    UnsignedWord getSize();

    /**
     * Sets the size of the buffer.
     */
    @RawField
    void setSize(UnsignedWord value);

    /**
     * Should this buffer be freed after processing the data in it.
     */
    @RawField
    boolean getFreeable();

    /**
     * Sets the freeable status of the buffer.
     */
    @RawField
    void setFreeable(boolean freeable);
}
