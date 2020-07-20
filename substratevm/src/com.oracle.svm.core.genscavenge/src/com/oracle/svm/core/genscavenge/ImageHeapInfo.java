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

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;

/**
 * Information on the multiple partitions that make up the image heap, which don't necessarily form
 * a contiguous block of memory (there can be holes in between), and their boundaries.
 */
public final class ImageHeapInfo {
    /** Indicates no chunk with {@link #initialize} chunk offset parameters. */
    public static final long NO_CHUNK = -1;

    @UnknownObjectField(types = Object.class) public Object firstReadOnlyPrimitiveObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyPrimitiveObject;

    @UnknownObjectField(types = Object.class) public Object firstReadOnlyReferenceObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyReferenceObject;

    @UnknownObjectField(types = Object.class) public Object firstReadOnlyRelocatableObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyRelocatableObject;

    @UnknownObjectField(types = Object.class) public Object firstWritablePrimitiveObject;
    @UnknownObjectField(types = Object.class) public Object lastWritablePrimitiveObject;

    @UnknownObjectField(types = Object.class) public Object firstWritableReferenceObject;
    @UnknownObjectField(types = Object.class) public Object lastWritableReferenceObject;

    @UnknownObjectField(types = Object.class) public Object firstWritableHugeObject;
    @UnknownObjectField(types = Object.class) public Object lastWritableHugeObject;

    @UnknownObjectField(types = Object.class) public Object firstReadOnlyHugeObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyHugeObject;

    @UnknownObjectField(types = Object.class) public Object firstObject;
    @UnknownObjectField(types = Object.class) public Object lastObject;

    @UnknownPrimitiveField public long offsetOfFirstAlignedChunkWithRememberedSet;
    @UnknownPrimitiveField public long offsetOfFirstUnalignedChunkWithRememberedSet;

    public ImageHeapInfo() {
    }

    @SuppressWarnings("hiding")
    public void initialize(Object firstReadOnlyPrimitiveObject, Object lastReadOnlyPrimitiveObject, Object firstReadOnlyReferenceObject, Object lastReadOnlyReferenceObject,
                    Object firstReadOnlyRelocatableObject, Object lastReadOnlyRelocatableObject, Object firstWritablePrimitiveObject, Object lastWritablePrimitiveObject,
                    Object firstWritableReferenceObject, Object lastWritableReferenceObject, Object firstWritableHugeObject, Object lastWritableHugeObject,
                    Object firstReadOnlyHugeObject, Object lastReadOnlyHugeObject, long offsetOfFirstAlignedChunkWithRememberedSet, long offsetOfFirstUnalignedChunkWithRememberedSet) {
        assert offsetOfFirstAlignedChunkWithRememberedSet == NO_CHUNK || offsetOfFirstAlignedChunkWithRememberedSet >= 0;
        assert offsetOfFirstUnalignedChunkWithRememberedSet == NO_CHUNK || offsetOfFirstUnalignedChunkWithRememberedSet >= 0;

        this.firstReadOnlyPrimitiveObject = firstReadOnlyPrimitiveObject;
        this.lastReadOnlyPrimitiveObject = lastReadOnlyPrimitiveObject;
        this.firstReadOnlyReferenceObject = firstReadOnlyReferenceObject;
        this.lastReadOnlyReferenceObject = lastReadOnlyReferenceObject;
        this.firstReadOnlyRelocatableObject = firstReadOnlyRelocatableObject;
        this.lastReadOnlyRelocatableObject = lastReadOnlyRelocatableObject;
        this.firstWritablePrimitiveObject = firstWritablePrimitiveObject;
        this.lastWritablePrimitiveObject = lastWritablePrimitiveObject;
        this.firstWritableReferenceObject = firstWritableReferenceObject;
        this.lastWritableReferenceObject = lastWritableReferenceObject;
        this.firstWritableHugeObject = firstWritableHugeObject;
        this.lastWritableHugeObject = lastWritableHugeObject;
        this.firstReadOnlyHugeObject = firstReadOnlyHugeObject;
        this.lastReadOnlyHugeObject = lastReadOnlyHugeObject;
        this.offsetOfFirstAlignedChunkWithRememberedSet = offsetOfFirstAlignedChunkWithRememberedSet;
        this.offsetOfFirstUnalignedChunkWithRememberedSet = offsetOfFirstUnalignedChunkWithRememberedSet;

        // Compute boundaries for checks considering partitions can be empty (first == last == null)
        Object firstReadOnlyObject = (firstReadOnlyPrimitiveObject != null) ? firstReadOnlyPrimitiveObject
                        : ((firstReadOnlyReferenceObject != null) ? firstReadOnlyReferenceObject : firstReadOnlyRelocatableObject);
        Object lastReadOnlyObject = (lastReadOnlyRelocatableObject != null) ? lastReadOnlyRelocatableObject
                        : ((lastReadOnlyReferenceObject != null) ? lastReadOnlyReferenceObject : lastReadOnlyPrimitiveObject);
        Object firstWritableObject = (firstWritablePrimitiveObject != null) ? firstWritablePrimitiveObject : firstWritableReferenceObject;
        Object lastWritableObject = (lastWritableReferenceObject != null) ? lastWritableReferenceObject : lastWritablePrimitiveObject;
        Object firstRegularObject = (firstReadOnlyObject != null) ? firstReadOnlyObject : firstWritableObject;
        Object lastRegularObject = (lastWritableObject != null) ? lastWritableObject : lastReadOnlyObject;
        Object firstHugeObject = (firstWritableHugeObject != null) ? firstWritableHugeObject : firstReadOnlyHugeObject;
        Object lastHugeObject = (lastReadOnlyHugeObject != null) ? lastReadOnlyHugeObject : lastWritableHugeObject;
        this.firstObject = (firstRegularObject != null) ? firstRegularObject : firstHugeObject;
        this.lastObject = (lastHugeObject != null) ? lastHugeObject : lastRegularObject;
    }

    /*
     * Convenience methods for asking if a Pointer is in the various native image heap partitions.
     *
     * These test [first .. last] rather than [first .. last), because last is in the partition.
     * These do not test for Pointers *into* the last object in each partition, though methods would
     * be easy to write, but slower.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyPrimitivePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyReferencePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyRelocatablePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyRelocatableObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyRelocatableObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritablePrimitivePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritablePrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritableReferencePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritableReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritableReferenceObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritableHugePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstWritableHugeObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritableHugeObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyHugePartition(Pointer ptr) {
        assert ptr.isNonNull();
        return Word.objectToUntrackedPointer(firstReadOnlyHugeObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyHugeObject));
    }

    /**
     * This method only returns the correct result for pointers that point to the the start of an
     * object. This is sufficient for all our current use cases. This code must be as fast as
     * possible at the GC uses it for every visited reference.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer objectPointer) {
        boolean result;
        if (objectPointer.isNull()) {
            result = false;
        } else {
            result = objectPointer.aboveOrEqual(Word.objectToUntrackedPointer(firstObject)) && objectPointer.belowOrEqual(Word.objectToUntrackedPointer(lastObject));
        }
        assert result == isInImageHeapSlow(objectPointer);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeapSlow(Pointer objectPointer) {
        boolean result = false;
        if (objectPointer.isNonNull()) {
            result |= isInReadOnlyPrimitivePartition(objectPointer);
            result |= isInReadOnlyReferencePartition(objectPointer);
            result |= isInReadOnlyRelocatablePartition(objectPointer);
            result |= isInWritablePrimitivePartition(objectPointer);
            result |= isInWritableReferencePartition(objectPointer);
            result |= isInWritableHugePartition(objectPointer);
            result |= isInReadOnlyHugePartition(objectPointer);
        }
        return result;
    }
}
