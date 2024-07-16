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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.util.PointerUtils;

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
 * Objects in a AlignedHeapChunk have to be promoted by copying from their current HeapChunk to a
 * destination HeapChunk.
 * <p>
 * An AlignedHeapChunk is laid out:
 *
 * <pre>
 * +===============+-------+--------+----------------------+
 * | AlignedHeader | Card  | First  | Object ...           |
 * | Fields        | Table | Object |                      |
 * |               |       | Table  |                      |
 * +===============+-------+--------+----------------------+
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
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(AlignedHeader chunk, UnsignedWord chunkSize) {
        HeapChunk.initialize(chunk, AlignedHeapChunk.getObjectsStart(chunk), chunkSize);
    }

    public static void reset(AlignedHeader chunk) {
        assert HeapChunk.getEndOffset(chunk).rawValue() == SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue();
        initialize(chunk, WordFactory.unsigned(SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getObjectsStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getObjectsStartOffset());
    }

    public static Pointer getObjectsEnd(AlignedHeader that) {
        return HeapChunk.getEndPointer(that);
    }

    /** Allocate uninitialized memory within this AlignedHeapChunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Pointer allocateMemory(AlignedHeader that, UnsignedWord size) {
        Pointer result = WordFactory.nullPointer();
        UnsignedWord available = HeapChunk.availableObjectMemory(that);
        if (size.belowOrEqual(available)) {
            result = HeapChunk.getTopPointer(that);
            Pointer newTop = result.add(size);
            HeapChunk.setTopPointerCarefully(that, newTop);
        }
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static UnsignedWord getCommittedObjectMemory(AlignedHeader that) {
        return HeapChunk.getEndOffset(that).subtract(getObjectsStartOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static AlignedHeader getEnclosingChunk(Object obj) {
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(ptr);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static AlignedHeader getEnclosingChunkFromObjectPointer(Pointer ptr) {
        return (AlignedHeader) PointerUtils.roundDown(ptr, HeapParameters.getAlignedHeapChunkAlignment());
    }

    /** Return the offset of an object within the objects part of a chunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getObjectOffset(AlignedHeader that, Pointer objectPointer) {
        Pointer objectsStart = getObjectsStart(that);
        return objectPointer.subtract(objectsStart);
    }

    static boolean walkObjects(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFrom(that, getObjectsStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean walkObjectsInline(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFromInline(that, getObjectsStart(that), visitor);
    }

    @Fold
    public static UnsignedWord getObjectsStartOffset() {
        return RememberedSet.get().getHeaderSizeOfAlignedChunk();
    }

    @Fold
    static MemoryWalker.HeapChunkAccess<AlignedHeapChunk.AlignedHeader> getMemoryWalkerAccess() {
        return ImageSingletons.lookup(AlignedHeapChunk.MemoryWalkerAccessImpl.class);
    }

    /** Methods for a {@link MemoryWalker} to access an aligned heap chunk. */
    @AutomaticallyRegisteredImageSingleton(onlyWith = UseSerialOrEpsilonGC.class)
    static final class MemoryWalkerAccessImpl extends HeapChunk.MemoryWalkerAccessImpl<AlignedHeapChunk.AlignedHeader> {

        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
        }

        @Override
        public boolean isAligned(AlignedHeapChunk.AlignedHeader heapChunk) {
            return true;
        }

        @Override
        public UnsignedWord getAllocationStart(AlignedHeapChunk.AlignedHeader heapChunk) {
            return getObjectsStart(heapChunk);
        }
    }
}
