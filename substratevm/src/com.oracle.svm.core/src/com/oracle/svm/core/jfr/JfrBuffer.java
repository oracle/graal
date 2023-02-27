/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.IsolateThread;

/**
 * A {@link JfrBuffer} is a block of native memory (either thread-local or global) into which JFR
 * events are written. The flushedPos is the point up to which data has been flushed. The
 * committedPos is the point up to which data has been committed. This means that data between the
 * flushedPos and committedPos is unflushed data that is ready to be flushed. This also means that
 * flushedPos should never exceed committedPos. New emitted events are written after the commit
 * position. The new events are committed by advancing the committedPos.
 */
@RawStructure
public interface JfrBuffer extends PointerBase {

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
     * Returns the committed position. Any data before this position is valid event data.
     */
    @RawField
    Pointer getCommittedPos();

    /**
     * Sets the committed position.
     */
    @RawField
    void setCommittedPos(Pointer value);

    @RawFieldOffset
    static int offsetOfCommittedPos() {
        throw VMError.unimplemented(); // replaced
    }

    /**
     * Returns the position of unflushed data. Any data before this position was already flushed to
     * some other buffer or to the disk.
     */
    @RawField
    Pointer getFlushedPos();

    /**
     * Sets the position of unflushed data.
     */
    @RawField
    void setFlushedPos(Pointer value);

    /**
     * Returns the type of the buffer.
     */
    @RawField
    @PinnedObjectField
    JfrBufferType getBufferType();

    /**
     * Sets the type of the buffer.
     */
    @RawField
    @PinnedObjectField
    void setBufferType(JfrBufferType bufferType);

    @RawField
    void setLockOwner(IsolateThread thread);

    @RawField
    IsolateThread getLockOwner();

    @RawFieldOffset
    static int offsetOfLockOwner() {
        throw VMError.unimplemented(); // replaced
    }
}
