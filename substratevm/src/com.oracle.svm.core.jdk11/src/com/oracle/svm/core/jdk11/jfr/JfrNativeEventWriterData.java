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
package com.oracle.svm.core.jdk11.jfr;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

/**
 * A data structure that holds the mutable state of a {@link JfrNativeEventWriter}. Typically, it is
 * allocated on the stack.
 */
@RawStructure
public interface JfrNativeEventWriterData extends PointerBase {
    /**
     * Gets the JfrBuffer that data will be written to.
     */
    @RawField
    JfrBuffer getJfrBuffer();

    /**
     * Sets the JfrBuffer that data will be written to.
     */
    @RawField
    void setJfrBuffer(JfrBuffer value);

    /**
     * Gets the start position for the current event write.
     */
    @RawField
    Pointer getStartPos();

    /**
     * Sets the start position for the current event write.
     */
    @RawField
    void setStartPos(Pointer value);

    /**
     * Gets the current position of the event write. This position is moved forward as data is
     * written for an event
     */
    @RawField
    Pointer getCurrentPos();

    /**
     * Sets the current position of the event write.
     */
    @RawField
    void setCurrentPos(Pointer value);

    /**
     * Returns the end position for the current event write. Writing of data cannot exceed this
     * position.
     */
    @RawField
    Pointer getEndPos();

    /**
     * Sets the end position for the current event write.
     */
    @RawField
    void setEndPos(Pointer value);
}
