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
package com.oracle.svm.core.heap;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;

public abstract class RuntimeCodeInfoGCSupport {
    /**
     * Notify the GC that a code metadata object references Java heap objects from native-memory.
     */
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public abstract void registerObjectFields(CodeInfo codeInfo);

    /**
     * Notify the GC that run-time compiled code has embedded references to Java heap objects.
     */
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public abstract void registerCodeConstants(CodeInfo codeInfo);

    /**
     * Notify the GC about frame metadata for run-time compiled code that references Java heap
     * objects from native-memory.
     */
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public abstract void registerFrameMetadata(CodeInfo codeInfo);

    /**
     * Notify the GC about deoptimization metadata for run-time compiled code that references Java
     * heap objects from native-memory.
     */
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public abstract void registerDeoptMetadata(CodeInfo codeInfo);
}
