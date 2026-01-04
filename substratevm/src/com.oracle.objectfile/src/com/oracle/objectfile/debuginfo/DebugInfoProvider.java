/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debuginfo;

import java.util.List;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.TypeEntry;

/**
 * Interfaces used to allow a native image to communicate details of types, code and data to the
 * underlying object file so that the latter can insert appropriate debug info.
 */
public interface DebugInfoProvider {

    void installDebugInfo();

    boolean useHeapBase();

    boolean isRuntimeCompilation();

    /**
     * Number of bits oops are left shifted by when using compressed oops.
     */
    int compressionShift();

    /**
     * Mask selecting low order bits used for tagging oops.
     */
    int reservedHubBitsMask();

    /**
     * Number of bytes used to store an oop reference.
     */
    int referenceSize();

    /**
     * Number of bytes used to store a raw pointer.
     */
    int pointerSize();

    /**
     * Alignment of object memory area (and, therefore, of any oop) in bytes.
     */
    int objectAlignment();

    List<TypeEntry> typeEntries();

    List<CompiledMethodEntry> compiledMethodEntries();

    String cachePath();

    enum FrameSizeChangeType {
        EXTEND,
        CONTRACT;
    }
}
