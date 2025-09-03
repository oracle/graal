/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.word.Word;

/**
 * Information on the multiple partitions that make up the image heap, which don't necessarily form
 * a contiguous block of memory (there can be holes in between), and their boundaries.
 */
public final class ImageHeapInfo implements MultiLayeredImageSingleton, UnsavedSingleton {
    /** Indicates no chunk with {@link #initialize} chunk offset parameters. */
    public static final long NO_CHUNK = -1;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstReadOnlyRegularObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastReadOnlyRegularObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstReadOnlyRelocatableObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastReadOnlyRelocatableObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstWritablePatchedObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastWritablePatchedObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstWritableRegularObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastWritableRegularObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstWritableHugeObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastWritableHugeObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstReadOnlyHugeObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastReadOnlyHugeObject;

    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastObject;

    // All offsets are relative to the heap base.
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfFirstWritableAlignedChunk;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfFirstWritableUnalignedChunk;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfLastWritableUnalignedChunk;

    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public int dynamicHubCount;

    public ImageHeapInfo() {
    }

    @SuppressWarnings("hiding")
    public void initialize(Object firstReadOnlyRegularObject, Object lastReadOnlyRegularObject, Object firstReadOnlyRelocatableObject, Object lastReadOnlyRelocatableObject,
                    Object firstWritablePatchedObject, Object lastWritablePatchedObject,
                    Object firstWritableRegularObject, Object lastWritableRegularObject, Object firstWritableHugeObject, Object lastWritableHugeObject,
                    Object firstReadOnlyHugeObject, Object lastReadOnlyHugeObject, long offsetOfFirstWritableAlignedChunk, long offsetOfFirstWritableUnalignedChunk,
                    long offsetOfLastWritableUnalignedChunk, int dynamicHubCount) {
        assert offsetOfFirstWritableAlignedChunk == NO_CHUNK || offsetOfFirstWritableAlignedChunk >= 0;
        assert offsetOfFirstWritableUnalignedChunk == NO_CHUNK || offsetOfFirstWritableUnalignedChunk >= 0;

        this.firstReadOnlyRegularObject = firstReadOnlyRegularObject;
        this.lastReadOnlyRegularObject = lastReadOnlyRegularObject;
        this.firstReadOnlyRelocatableObject = firstReadOnlyRelocatableObject;
        this.lastReadOnlyRelocatableObject = lastReadOnlyRelocatableObject;
        this.firstWritablePatchedObject = firstWritablePatchedObject;
        this.lastWritablePatchedObject = lastWritablePatchedObject;
        this.firstWritableRegularObject = firstWritableRegularObject;
        this.lastWritableRegularObject = lastWritableRegularObject;
        this.firstWritableHugeObject = firstWritableHugeObject;
        this.lastWritableHugeObject = lastWritableHugeObject;
        this.firstReadOnlyHugeObject = firstReadOnlyHugeObject;
        this.lastReadOnlyHugeObject = lastReadOnlyHugeObject;
        this.offsetOfFirstWritableAlignedChunk = offsetOfFirstWritableAlignedChunk;
        this.offsetOfFirstWritableUnalignedChunk = offsetOfFirstWritableUnalignedChunk;
        this.offsetOfLastWritableUnalignedChunk = offsetOfLastWritableUnalignedChunk;
        this.dynamicHubCount = dynamicHubCount;

        /*
         * Determine first and last objects. Note orderedObject is ordered based on the partition
         * layout. Empty partitions will have (first == last == null).
         */
        Object[] orderedObjects = {
                        firstReadOnlyRegularObject,
                        lastReadOnlyRegularObject,
                        firstReadOnlyRelocatableObject,
                        lastReadOnlyRelocatableObject,
                        firstWritablePatchedObject,
                        lastWritablePatchedObject,
                        firstWritableRegularObject,
                        lastWritableRegularObject,
                        firstWritableHugeObject,
                        lastWritableHugeObject,
                        firstReadOnlyHugeObject,
                        lastReadOnlyHugeObject
        };
        for (Object cur : orderedObjects) {
            if (cur != null) {
                if (firstObject == null) {
                    firstObject = cur;
                }
                lastObject = cur;
            }
        }
    }

    /*
     * Convenience methods for asking if a Pointer is in the various native image heap partitions.
     *
     * These test [first .. last] rather than [first .. last), because last is in the partition.
     * These do not test for Pointers *into* the last object in each partition, though methods would
     * be easy to write, but slower.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyRegularPartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyRegularObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastReadOnlyRegularObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyRelocatablePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyRelocatableObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastReadOnlyRelocatableObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritablePatchedPartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritablePatchedObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastWritablePatchedObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritableRegularPartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritableRegularObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastWritableRegularObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritableHugePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritableHugeObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastWritableHugeObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyHugePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyHugeObject).belowOrEqual(ptr) && ptr.belowThan(getObjectEnd(lastReadOnlyHugeObject));
    }

    /**
     * This method only returns the correct result for pointers that point to the start of an
     * object. This is sufficient for all our current use cases. This code must be as fast as
     * possible as the GC uses it for every visited reference.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer objectPointer) {
        return objectPointer.aboveOrEqual(Word.objectToUntrackedPointer(firstObject)) && objectPointer.belowOrEqual(Word.objectToUntrackedPointer(lastObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public AlignedHeader getFirstWritableAlignedChunk() {
        return asImageHeapChunk(offsetOfFirstWritableAlignedChunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnalignedHeader getFirstWritableUnalignedChunk() {
        return asImageHeapChunk(offsetOfFirstWritableUnalignedChunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnalignedHeader getLastWritableUnalignedChunk() {
        return asImageHeapChunk(offsetOfLastWritableUnalignedChunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getObjectEnd(Object obj) {
        if (obj == null) {
            return Word.nullPointer();
        }
        return LayoutEncoding.getImageHeapObjectEnd(obj);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends HeapChunk.Header<T>> T asImageHeapChunk(long offsetInImageHeap) {
        if (offsetInImageHeap < 0) {
            return (T) Word.nullPointer();
        }
        UnsignedWord offset = Word.unsigned(offsetInImageHeap);
        return (T) KnownIntrinsics.heapBase().add(offset);
    }

    public void print(Log log) {
        log.string("ReadOnly: ").zhex(Word.objectToUntrackedPointer(firstReadOnlyRegularObject)).string(" - ").zhex(getObjectEnd(lastReadOnlyRegularObject)).newline();
        log.string("ReadOnly Relocatables: ").zhex(Word.objectToUntrackedPointer(firstReadOnlyRelocatableObject)).string(" - ").zhex(getObjectEnd(lastReadOnlyRelocatableObject)).newline();
        log.string("Writeable Patched: ").zhex(Word.objectToUntrackedPointer(firstWritablePatchedObject)).string(" - ").zhex(getObjectEnd(lastWritablePatchedObject)).newline();
        log.string("Writable: ").zhex(Word.objectToUntrackedPointer(firstWritableRegularObject)).string(" - ").zhex(getObjectEnd(lastWritableRegularObject)).newline();
        log.string("Writable Huge: ").zhex(Word.objectToUntrackedPointer(firstWritableHugeObject)).string(" - ").zhex(getObjectEnd(lastWritableHugeObject)).newline();
        log.string("ReadOnly Huge: ").zhex(Word.objectToUntrackedPointer(firstReadOnlyHugeObject)).string(" - ").zhex(getObjectEnd(lastReadOnlyHugeObject)).newline();
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
