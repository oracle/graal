/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

/** A Generation is a collection of one or more Spaces. */
abstract class Generation {
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    Generation(String name) {
        this.name = name;
    }

    /**
     * Walk the Objects in this Space, passing each to a Visitor.
     *
     * @param visitor An ObjectVisitor.
     * @return True if all visits returned true, false otherwise.
     */
    public abstract boolean walkObjects(ObjectVisitor visitor);

    public String getName() {
        return name;
    }

    /** Report some statistics about the Generation to a Log. */
    public abstract Log report(Log log, boolean traceHeapChunks);

    /**
     * Promote an Object to this Generation, typically by copying and leaving a forwarding pointer
     * to the new Object in place of the original Object.
     *
     * This turns an Object from white to grey: the object is in this Generation, but has not yet
     * had its interior pointers visited.
     *
     * @return a reference to the promoted object, which is different to the original reference if
     *         promotion was done by copying.
     */
    protected abstract Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace);

    /**
     * Promote an Object to this Generation, typically by HeapChunk motion.
     *
     * This turns an Object from white to grey: the object is in this Generation, but has not yet
     * had its interior pointers visited.
     *
     * @return a reference to the promoted object, which is the same as the original if the object
     *         was promoted through HeapChunk motion.
     */
    protected abstract Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace);

    /**
     * Promote a HeapChunk from its original space to this Space.
     *
     * This turns all the Objects in the chunk from white to grey: the objects are in this Space,
     * but have not yet had their interior pointers visited.
     */
    protected abstract void promoteChunk(HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace);
}
