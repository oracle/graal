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

import com.oracle.svm.core.Uninterruptible;

/**
 * Epoch-based storage for metadata. Switching the epoch may only be done at a safepoint. All
 * methods that manipulate data in the constant pool must be {@link Uninterruptible} to guarantee
 * that a safepoint always sees a consistent state. Otherwise, other JFR code could see partially
 * added data when it tries to iterate the data at a safepoint.
 *
 * Some repositories (e.g., {@link JfrTypeRepository}) return stable JFR trace IDs (i.e., the trace
 * ID does not change if the epoch changes). However, the corresponding data (e.g., the type) is
 * only marked as used in a certain epoch, so callers must always be aware that the returned trace
 * ID is only valid for a specific epoch, no matter if the trace ID is stable or not.
 */
public interface JfrRepository {

    /**
     * If constant pool is empty, the {@link JfrRepository#write(JfrChunkWriter, boolean)} function
     * returns this value.
     */
    int EMPTY = 0;

    /**
     * If constant pool is not empty, the {@link JfrRepository#write(JfrChunkWriter, boolean)}
     * function returns this value.
     */
    int NON_EMPTY = 1;

    /**
     * Persists the data of the previous/current epoch.
     * 
     * @param flushpoint Determines whether the current or previous epoch is used.
     */
    int write(JfrChunkWriter writer, boolean flushpoint);
}
