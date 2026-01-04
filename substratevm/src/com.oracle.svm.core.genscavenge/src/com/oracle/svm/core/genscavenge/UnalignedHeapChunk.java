/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectVisitor;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.word.Word;

/**
 * An UnalignedHeapChunk holds exactly one Object.
 * <p>
 * An UnalignedHeapChunk does not have a way to map from a Pointer to (or into) the Object they
 * contain to the UnalignedHeapChunk that contains them.
 * <p>
 * An Object in a UnalignedHeapChunk needs to have a bit set in its DynamicHub to identify it as an
 * Object in a UnalignedHeapChunk, so things like write-barriers don't try to update meta-data. Also
 * so things like the getEnclosingHeapChunk(Object) can tell that the object is in an
 * UnalignedHeapChunk.
 * <p>
 * Only a slow-path allocation method is available for UnalignedHeapChunks. This is acceptable
 * because UnalignedHeapChunks are for large objects, so the cost of initializing the object dwarfs
 * the cost of slow-path allocation.
 * <p>
 * The Object in an UnalignedHeapChunk can be promoted from one Space to another by moving the
 * UnalignedHeapChunk from one Space to the other, rather than copying the Object out of the
 * HeapChunk in one Space and into a destination HeapChunk in the other Space. That saves some
 * amount of copying cost for these large objects.
 *
 * An UnalignedHeapChunk is laid out:
 *
 * <pre>
 * +=================+-------+--------------+-------------------------------------+
 * | UnalignedHeader | Card  | Object Start | Object                              |
 * | Fields          | Table | Offset       |                                     |
 * +=================+-------+--------------+-------------------------------------+
 * </pre>
 *
 * The HeapChunk fields can be accessed as declared fields. The card table and the field for the
 * object start offset are optional. Whether these fields are present depends on the used
 * {@link RememberedSet} implementation. The size of the card table also depends on the used
 * {@link RememberedSet} implementation and may even be zero. If present, the object start offset is
 * properly aligned and stored as {@link UnsignedWord}. It is needed to get the chunk address from
 * the object's address.
 */
public final class UnalignedHeapChunk {
    private UnalignedHeapChunk() { // all static
    }

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card table or the object start offset. See the comment on
     * the class level above. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface UnalignedHeader extends HeapChunk.Header<UnalignedHeader> {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void initialize(HostedByteBufferPointer chunk, UnsignedWord objectSize) {
        UnsignedWord objectStartOffset = calculateObjectStartOffset(objectSize);
        RememberedSet.get().setObjectStartOffsetOfUnalignedChunk(chunk, objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void initialize(UnalignedHeader chunk, UnsignedWord chunkSize, UnsignedWord objectSize) {
        assert chunk.isNonNull();
        UnsignedWord objectStartOffset = calculateObjectStartOffset(objectSize);
        HeapChunk.initialize(chunk, HeapChunk.asPointer(chunk).add(objectStartOffset), chunkSize);
        setObjectStartOffset(chunk, objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getObjectStart(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getObjectStartOffset(that));
    }

    public static Pointer getObjectEnd(UnalignedHeader that) {
        return HeapChunk.getEndPointer(that);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord getChunkSizeForObject(UnsignedWord objectSize) {
        UnsignedWord objectStart = RememberedSet.get().getHeaderSizeOfUnalignedChunk(objectSize);
        UnsignedWord alignment = Word.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(objectStart.add(objectSize), alignment);
    }

    /** Allocate uninitialized memory within this UnalignedHeapChunk. */
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public static Pointer allocateMemory(UnalignedHeader that, UnsignedWord size) {
        UnsignedWord available = HeapChunk.availableObjectMemory(that);
        Pointer result = Word.nullPointer();
        if (size.belowOrEqual(available)) {
            result = HeapChunk.getTopPointer(that);
            Pointer newTop = result.add(size);
            HeapChunk.setTopPointerCarefully(that, newTop);
        }
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnalignedHeader getEnclosingChunk(Object obj) {
        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(objPointer);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnalignedHeader getEnclosingChunkFromObjectPointer(Pointer ptr) {
        if (!GraalDirectives.inIntrinsic()) {
            assert HeapImpl.isImageHeapAligned() || !HeapImpl.getHeapImpl().isInImageHeap(ptr) : "can't be used for the image heap because the image heap is not aligned to the chunk size";
        }
        Pointer chunkPointer = ptr.subtract(getOffsetForObject(ptr));
        return (UnalignedHeader) chunkPointer;
    }

    public static void initializeObjectStartOffset(UnalignedHeader that, UnsignedWord objectSize) {
        UnsignedWord objectStartOffset = calculateObjectStartOffset(objectSize);
        setObjectStartOffset(that, objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord calculateObjectStartOffset(UnsignedWord objectSize) {
        return RememberedSet.get().getHeaderSizeOfUnalignedChunk(objectSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setObjectStartOffset(UnalignedHeader that, UnsignedWord objectStartOffset) {
        RememberedSet.get().setObjectStartOffsetOfUnalignedChunk(that, objectStartOffset);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getObjectStartOffset(UnalignedHeader that) {
        return RememberedSet.get().getObjectStartOffsetOfUnalignedChunk(that);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getOffsetForObject(Pointer objPtr) {
        return RememberedSet.get().getOffsetForObjectInUnalignedChunk(objPtr);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void walkObjects(UnalignedHeader that, ObjectVisitor visitor) {
        HeapChunk.walkObjectsFrom(that, getObjectStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void walkObjectsInline(UnalignedHeader that, UninterruptibleObjectVisitor visitor) {
        HeapChunk.walkObjectsFromInline(that, getObjectStart(that), visitor);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getCommittedObjectMemory(UnalignedHeader that) {
        return HeapChunk.getEndOffset(that).subtract(getObjectStartOffset(that));
    }
}
