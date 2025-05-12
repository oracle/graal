/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;

/** A walker over different kinds of allocated memory. */
public final class MemoryWalker {
    public interface ImageHeapRegionVisitor {
        /** Visit a region from the native image heap. */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting memory.")
        <T> void visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access);
    }

    /** A set of access methods for visiting regions of the native image heap. */
    public interface NativeImageHeapRegionAccess<T> {

        Object getFirstObject(T region);

        Object getLastObject(T region);

        UnsignedWord getSize(T region);

        String getRegionName(T region);

        boolean isWritable(T region);

        boolean consistsOfHugeObjects(T region);

        void visitObjects(T region, ObjectVisitor visitor);
    }

}
