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

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.util.PointerUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * An AlignedHeapChunk can hold many Objects.
 * <p>
 * This is the key to the chunk-allocated heap: Because these chunks are allocated on aligned
 * boundaries, I can map from a Pointer to (or into) an Object to the AlignedChunk that contains it.
 * From there I can get to the meta-data the AlignedChunk contains, without a table lookup on the
 * Pointer.
 * <p>
 * Most allocation within a AlignedHeapChunk is via fast-path allocation snippets, but a slow-path
 * allocation method is available.
 * <p>
 * An AlignedHeapChunk is laid out as follows:
 *
 * <pre>
 * +===============+-------+--------+-----------------+-----------------+
 * | AlignedHeader | Card  | First  | Initial Object  | Object ...      |
 * | Fields        | Table | Object | Move Info (only |                 |
 * |               |       | Table  | Compacting GC)  |                 |
 * +===============+-------+--------+-----------------+-----------------+
 * </pre>
 *
 * The size of both the CardTable and the FirstObjectTable depends on the used {@link RememberedSet}
 * implementation and may even be zero.
 */
public final class AlignedHeapChunk {
    private AlignedHeapChunk() { // all static
    }

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card table, or the first object table, and certainly does
     * not include the objects. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface AlignedHeader extends HeapChunk.Header<AlignedHeader> {
        @RawField
        boolean getShouldSweepInsteadOfCompact();

        @RawField
        void setShouldSweepInsteadOfCompact(boolean value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(AlignedHeader chunk, UnsignedWord chunkSize) {
        assert chunk.isNonNull();
        assert chunkSize.equal(HeapParameters.getAlignedHeapChunkSize()) : "expecting all aligned chunks to be the same size";
        HeapChunk.initialize(chunk, AlignedHeapChunk.getObjectsStart(chunk), chunkSize);
        chunk.setShouldSweepInsteadOfCompact(false);
    }

    public static void reset(AlignedHeader chunk) {
        long alignedChunkSize = SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue();
        assert HeapChunk.getEndOffset(chunk).rawValue() == alignedChunkSize;
        initialize(chunk, Word.unsigned(alignedChunkSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getObjectsStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getObjectsStartOffset());
    }

    public static Pointer getObjectsEnd(AlignedHeader that) {
        return HeapChunk.getEndPointer(that);
    }

    public static boolean isEmpty(AlignedHeader that) {
        return HeapChunk.getTopOffset(that).equal(getObjectsStartOffset());
    }

    /** Allocate uninitialized memory within this AlignedHeapChunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer tryAllocateMemory(AlignedHeader that, UnsignedWord size) {
        UnsignedWord available = HeapChunk.availableObjectMemory(that);
        if (size.aboveThan(available)) {
            return Word.nullPointer();
        }

        Pointer result = HeapChunk.getTopPointer(that);
        Pointer newTop = result.add(size);
        HeapChunk.setTopPointerCarefully(that, newTop);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static AlignedHeader getEnclosingChunk(Object obj) {
        assert ObjectHeaderImpl.isAlignedObject(obj);
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(ptr);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static AlignedHeader getEnclosingChunkFromObjectPointer(Pointer ptr) {
        if (!GraalDirectives.inIntrinsic()) {
            assert HeapImpl.isImageHeapAligned() || !HeapImpl.getHeapImpl().isInImageHeap(ptr) : "can't be used because the image heap is unaligned";
        }
        return (AlignedHeader) PointerUtils.roundDown(ptr, HeapParameters.getAlignedHeapChunkAlignment());
    }

    /** Return the offset of an object within the objects part of a chunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getObjectOffset(AlignedHeader that, Pointer objectPointer) {
        Pointer objectsStart = getObjectsStart(that);
        return objectPointer.subtract(objectsStart);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void walkObjects(AlignedHeader that, ObjectVisitor visitor) {
        HeapChunk.walkObjectsFrom(that, getObjectsStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void walkObjectsFromInline(AlignedHeader that, Pointer start, GreyToBlackObjectVisitor visitor) {
        HeapChunk.walkObjectsFromInline(that, start, visitor);
    }

    @Fold
    public static UnsignedWord getObjectsStartOffset() {
        return RememberedSet.get().getHeaderSizeOfAlignedChunk();
    }

    @Fold
    public static UnsignedWord getUsableSizeForObjects() {
        return HeapParameters.getAlignedHeapChunkSize().subtract(getObjectsStartOffset());
    }

    public interface Visitor {
        /**
         * Visit an {@link AlignedHeapChunk}.
         *
         * @param chunk The {@link AlignedHeapChunk} to be visited.
         * @return {@code true} if visiting should continue, {@code false} if visiting should stop.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting the heap.")
        boolean visitChunk(AlignedHeapChunk.AlignedHeader chunk);
    }
}
