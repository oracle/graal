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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.heap.ObjectVisitor;

/** A walker over different kinds of allocated memory. */
public abstract class MemoryWalker {

    /** Get the implementation of the MemoryWalker. */
    public static MemoryWalker getMemoryWalker() {
        return ImageSingletons.lookup(MemoryWalker.class);
    }

    /**
     * Walk memory applying the visitor. Returns true if all visits returned true, else false when
     * any visit returns false.
     */
    public abstract boolean visitMemory(Visitor visitor);

    public interface ImageHeapRegionVisitor {
        /** Visit a region from the native image heap. */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting memory.")
        <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access);
    }

    public interface Visitor extends ImageHeapRegionVisitor {
        /**
         * Visit a heap chunk, using the provided access methods. Return true if visiting should
         * continue, else false.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting memory.")
        <T extends PointerBase> boolean visitHeapChunk(T heapChunk, HeapChunkAccess<T> access);

        /**
         * Visit compiled code, using the provided access methods. Return true if visiting should
         * continue, else false.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting memory.")
        <T extends CodeInfo> boolean visitCode(T codeInfo, CodeAccess<T> access);
    }

    /** A set of access methods for visiting regions of the native image heap. */
    public interface NativeImageHeapRegionAccess<T> {

        UnsignedWord getStart(T region);

        UnsignedWord getSize(T region);

        String getRegionName(T region);

        boolean containsReferences(T region);

        boolean isWritable(T region);

        boolean visitObjects(T region, ObjectVisitor visitor);
    }

    /** A set of access methods for visiting heap chunk memory. */
    public interface HeapChunkAccess<T extends PointerBase> {

        /** Return the start of the heap chunk. */
        UnsignedWord getStart(T heapChunk);

        /** Return the size of the heap chunk. */
        UnsignedWord getSize(T heapChunk);

        /** Return the address where allocation starts within the heap chunk. */
        UnsignedWord getAllocationStart(T heapChunk);

        /**
         * Return the address where allocation has ended within the heap chunk. This is the first
         * address past the end of allocated space within the heap chunk.
         */
        UnsignedWord getAllocationEnd(T heapChunk);

        /**
         * Return the name of the region that contains the heap chunk. E.g., "young", "old", "free",
         * etc.
         */
        String getRegion(T heapChunk);

        /** Return true if the heap chunk is an aligned heap chunk, else false. */
        boolean isAligned(T heapChunk);
    }

    /** A set of access methods for visiting code memory. */
    public interface CodeAccess<T extends CodeInfo> {

        UnsignedWord getStart(T codeInfo);

        UnsignedWord getSize(T codeInfo);

        UnsignedWord getMetadataSize(T codeInfo);

        String getName(T codeInfo);
    }
}
