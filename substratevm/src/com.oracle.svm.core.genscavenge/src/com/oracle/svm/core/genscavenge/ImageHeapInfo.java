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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.graal.compiler.word.Word;

/**
 * Information on the multiple partitions that make up the image heap, which don't necessarily form
 * a contiguous block of memory (there can be holes in between), and their boundaries.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class ImageHeapInfo {
    /** Indicates no chunk with {@link #initialize} chunk offset parameters. */
    public static final long NO_CHUNK = -1;

    /* All read-only objects that are located in aligned chunks. */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstAlignedReadOnlyObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastAlignedReadOnlyObject;

    /*
     * The read-only objects that contain relocatable pointers. This is a subset of all the
     * read-only objects that are located in aligned chunks.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstAlignedReadOnlyRelocatableObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastAlignedReadOnlyRelocatableObject;

    /* All writable objects that are located in aligned chunks. */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstAlignedWritableObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastAlignedWritableObject;

    /*
     * The writable objects that need to be patched. This is a subset of all the writable objects
     * that are located in aligned chunks.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstAlignedWritablePatchedObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastAlignedWritablePatchedObject;

    /* All writable objects that are located in unaligned chunks. */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstUnalignedWritableObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastUnalignedWritableObject;

    /* All read-only objects that are located in unaligned chunks. */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstUnalignedReadOnlyObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastUnalignedReadOnlyObject;

    /* The first/last object in the image heap. */
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object firstObject;
    @UnknownObjectField(availability = AfterHeapLayout.class, canBeNull = true) public Object lastObject;

    /* All offsets are relative to the heap base. */
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfFirstWritableAlignedChunk;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfFirstWritableUnalignedChunk;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public long offsetOfLastWritableUnalignedChunk;

    @UnknownPrimitiveField(availability = AfterHeapLayout.class) public int dynamicHubCount;

    public ImageHeapInfo() {
    }

    @SuppressWarnings("hiding")
    public void initialize(Object firstAlignedReadOnlyObject, Object lastAlignedReadOnlyObject,
                    Object firstAlignedReadOnlyRelocatableObject, Object lastAlignedReadOnlyRelocatableObject,
                    Object firstAlignedWritableObject, Object lastAlignedWritableObject,
                    Object firstAlignedWritablePatchedObject, Object lastAlignedWritablePatchedObject,
                    Object firstUnalignedWritableObject, Object lastUnalignedWritableObject,
                    Object firstUnalignedReadOnlyObject, Object lastUnalignedReadOnlyObject,
                    long offsetOfFirstWritableAlignedChunk, long offsetOfFirstWritableUnalignedChunk, long offsetOfLastWritableUnalignedChunk,
                    int dynamicHubCount) {
        assert offsetOfFirstWritableAlignedChunk == NO_CHUNK || offsetOfFirstWritableAlignedChunk >= 0;
        assert offsetOfFirstWritableUnalignedChunk == NO_CHUNK || offsetOfFirstWritableUnalignedChunk >= 0;

        this.firstAlignedReadOnlyObject = firstAlignedReadOnlyObject;
        this.lastAlignedReadOnlyObject = lastAlignedReadOnlyObject;
        this.firstAlignedReadOnlyRelocatableObject = firstAlignedReadOnlyRelocatableObject;
        this.lastAlignedReadOnlyRelocatableObject = lastAlignedReadOnlyRelocatableObject;
        this.firstAlignedWritableObject = firstAlignedWritableObject;
        this.lastAlignedWritableObject = lastAlignedWritableObject;
        this.firstAlignedWritablePatchedObject = firstAlignedWritablePatchedObject;
        this.lastAlignedWritablePatchedObject = lastAlignedWritablePatchedObject;
        this.firstUnalignedWritableObject = firstUnalignedWritableObject;
        this.lastUnalignedWritableObject = lastUnalignedWritableObject;
        this.firstUnalignedReadOnlyObject = firstUnalignedReadOnlyObject;
        this.lastUnalignedReadOnlyObject = lastUnalignedReadOnlyObject;
        this.offsetOfFirstWritableAlignedChunk = offsetOfFirstWritableAlignedChunk;
        this.offsetOfFirstWritableUnalignedChunk = offsetOfFirstWritableUnalignedChunk;
        this.offsetOfLastWritableUnalignedChunk = offsetOfLastWritableUnalignedChunk;
        this.dynamicHubCount = dynamicHubCount;

        /*
         * Determine first and last objects. Note orderedObject is ordered based on the partition
         * layout. Empty partitions will have (first == last == null).
         */
        Object[] orderedObjects = {
                        firstAlignedReadOnlyObject,
                        lastAlignedReadOnlyObject,
                        firstAlignedReadOnlyRelocatableObject,
                        lastAlignedReadOnlyRelocatableObject,
                        firstAlignedWritableObject,
                        lastAlignedWritableObject,
                        firstUnalignedWritableObject,
                        lastUnalignedWritableObject,
                        firstUnalignedReadOnlyObject,
                        lastUnalignedReadOnlyObject
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
    public boolean isInAlignedReadOnly(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstAlignedReadOnlyObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastAlignedReadOnlyObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInAlignedReadOnlyRelocatable(Pointer ptr) {
        assert ptr.isNonNull();
        boolean result = Word.objectToUntrackedPointer(firstAlignedReadOnlyRelocatableObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastAlignedReadOnlyRelocatableObject));
        assert !result || isInAlignedReadOnly(ptr);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInAlignedWritable(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstAlignedWritableObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastAlignedWritableObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInAlignedWritablePatched(Pointer ptr) {
        assert ptr.isNonNull();
        boolean result = Word.objectToUntrackedPointer(firstAlignedWritablePatchedObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastAlignedWritablePatchedObject));
        assert !result || isInAlignedWritable(ptr);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInUnalignedWritable(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstUnalignedWritableObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastUnalignedWritableObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInUnalignedReadOnly(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstUnalignedReadOnlyObject).belowOrEqual(ptr) && ptr.belowThan(objEnd(lastUnalignedReadOnlyObject));
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
    private static Pointer objEnd(Object obj) {
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
        log.string("Objects in aligned chunks").indent(true);
        log.string("read-only: ").zhex(Word.objectToUntrackedPointer(firstAlignedReadOnlyObject)).string(" - ").zhex(objEnd(lastAlignedReadOnlyObject)).newline();
        log.string("read-only relocatables: ").zhex(Word.objectToUntrackedPointer(firstAlignedReadOnlyRelocatableObject)).string(" - ").zhex(objEnd(lastAlignedReadOnlyRelocatableObject)).newline();
        log.string("writable: ").zhex(Word.objectToUntrackedPointer(firstAlignedWritableObject)).string(" - ").zhex(objEnd(lastAlignedWritableObject)).newline();
        log.string("writeable patched: ").zhex(Word.objectToUntrackedPointer(firstAlignedWritablePatchedObject)).string(" - ").zhex(objEnd(lastAlignedWritablePatchedObject)).indent(false);

        log.string("Objects in unaligned chunks").indent(true);
        log.string("writable: ").zhex(Word.objectToUntrackedPointer(firstUnalignedWritableObject)).string(" - ").zhex(objEnd(lastUnalignedWritableObject)).newline();
        log.string("read-only: ").zhex(Word.objectToUntrackedPointer(firstUnalignedReadOnlyObject)).string(" - ").zhex(objEnd(lastUnalignedReadOnlyObject)).indent(false);
    }
}
