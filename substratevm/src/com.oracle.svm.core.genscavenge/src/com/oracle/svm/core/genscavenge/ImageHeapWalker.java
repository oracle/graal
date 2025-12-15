/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.word.Word;

public final class ImageHeapWalker {
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> ALIGNED_READ_ONLY_WALKER = new AlignedReadOnlyMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> ALIGNED_WRITABLE_WALKER = new AlignedWritableMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> UNALIGNED_WRITABLE_WALKER = new UnalignedWritableMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> UNALIGNED_READ_ONLY_WALKER = new UnalignedReadOnlyMemoryWalkerAccess();

    private ImageHeapWalker() {
    }

    public static void walkRegions(ImageHeapInfo heapInfo, MemoryWalker.ImageHeapRegionVisitor visitor) {
        visitor.visitNativeImageHeapRegion(heapInfo, ALIGNED_READ_ONLY_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, ALIGNED_WRITABLE_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, UNALIGNED_WRITABLE_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, UNALIGNED_READ_ONLY_WALKER);
    }

    public static void walkImageHeapObjects(ImageHeapInfo heapInfo, ObjectVisitor visitor) {
        walkPartition(heapInfo.firstAlignedReadOnlyObject, heapInfo.lastAlignedReadOnlyObject, visitor, true);
        walkPartition(heapInfo.firstAlignedWritableObject, heapInfo.lastAlignedWritableObject, visitor, true);
        walkPartition(heapInfo.firstUnalignedWritableObject, heapInfo.lastUnalignedWritableObject, visitor, false);
        walkPartition(heapInfo.firstUnalignedReadOnlyObject, heapInfo.lastUnalignedReadOnlyObject, visitor, false);
    }

    public static void walkImageHeapChunks(ImageHeapInfo heapInfo, HeapChunkVisitor visitor) {
        /* Walk all aligned chunks (can only be at the start of the image heap). */
        Object firstObject = heapInfo.firstObject;
        if (ObjectHeaderImpl.isAlignedObject(firstObject)) {
            HeapChunk.Header<?> alignedChunks = getImageHeapChunkForObject(firstObject, true);
            walkChunks(alignedChunks, visitor, true);
        }

        /* Walk all unaligned chunks (can only be at the end of the image heap). */
        Object firstUnalignedObject = heapInfo.firstUnalignedWritableObject;
        if (firstUnalignedObject == null) {
            firstUnalignedObject = heapInfo.firstUnalignedReadOnlyObject;
        }
        if (firstUnalignedObject != null) {
            HeapChunk.Header<?> unalignedChunks = getImageHeapChunkForObject(firstUnalignedObject, false);
            walkChunks(unalignedChunks, visitor, false);
        }
    }

    @NeverInline("Not performance critical")
    private static void walkChunks(HeapChunk.Header<?> firstChunk, HeapChunkVisitor visitor, boolean alignedChunks) {
        HeapChunk.Header<?> currentChunk = firstChunk;
        while (currentChunk.isNonNull()) {
            if (alignedChunks) {
                visitor.visitAlignedChunk((AlignedHeader) currentChunk);
            } else {
                visitor.visitUnalignedChunk((UnalignedHeader) currentChunk);
            }
            currentChunk = HeapChunk.getNext(currentChunk);
        }
    }

    @NeverInline("Not performance critical")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).")
    static void walkPartition(Object firstObject, Object lastObject, ObjectVisitor visitor, boolean alignedChunks) {
        walkPartitionInline(firstObject, lastObject, visitor, alignedChunks);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).", callerMustBe = true)
    static void walkPartitionInline(Object firstObject, Object lastObject, ObjectVisitor visitor, boolean alignedChunks) {
        if (firstObject == null || lastObject == null) {
            assert firstObject == null && lastObject == null;
            return;
        }
        Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        Pointer current = firstPointer;

        // Assumption: the order of chunks in their linked list is the same order as in memory,
        // and objects in a chunk are laid out as a continuous sequence without any gaps.
        HeapChunk.Header<?> currentChunk = getImageHeapChunkForObject(firstObject, alignedChunks);
        do {
            Pointer limit = lastPointer;
            Pointer chunkTop = HeapChunk.getTopPointer(currentChunk);
            if (lastPointer.aboveThan(chunkTop)) {
                limit = chunkTop.subtract(1); // lastObject in another chunk, visit all objects
            }
            while (current.belowOrEqual(limit)) {
                Object currentObject = current.toObjectNonNull();
                visitObjectInline(visitor, currentObject);
                current = LayoutEncoding.getImageHeapObjectEnd(currentObject);
            }
            if (current.belowThan(lastPointer)) {
                currentChunk = HeapChunk.getNext(currentChunk);
                current = alignedChunks ? AlignedHeapChunk.getObjectsStart((AlignedHeader) currentChunk)
                                : UnalignedHeapChunk.getObjectStart((UnalignedHeader) currentChunk);
                // Note: current can be equal to lastPointer now, despite not having visited it yet
            }
        } while (current.belowOrEqual(lastPointer));
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void visitObjectInline(ObjectVisitor visitor, Object currentObject) {
        visitor.visitObject(currentObject);
    }

    /** Computes the enclosing chunk without assuming that the image heap is aligned. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static HeapChunk.Header<?> getImageHeapChunkForObject(Object object, boolean alignedChunks) {
        Pointer objPtr = Word.objectToUntrackedPointer(object);
        Pointer base = Heap.getHeap().getImageHeapStart();
        Pointer offset = objPtr.subtract(base);
        UnsignedWord chunkOffset = alignedChunks ? UnsignedUtils.roundDown(offset, HeapParameters.getAlignedHeapChunkAlignment())
                        : offset.subtract(UnalignedHeapChunk.getOffsetForObject(objPtr));
        return (HeapChunk.Header<?>) chunkOffset.add(base);
    }
}

abstract class MemoryWalkerAccessBase implements MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> {
    private final boolean isWritable;
    private final boolean unalignedChunks;

    @Platforms(Platform.HOSTED_ONLY.class)
    MemoryWalkerAccessBase(boolean isWritable, boolean unalignedChunks) {
        this.isWritable = isWritable;
        this.unalignedChunks = unalignedChunks;
    }

    @Override
    public UnsignedWord getSize(ImageHeapInfo info) {
        Pointer firstStart = Word.objectToUntrackedPointer(getFirstObject(info));
        if (firstStart.isNull()) { // no objects
            return Word.zero();
        }
        Pointer lastEnd = LayoutEncoding.getImageHeapObjectEnd(getLastObject(info));
        return lastEnd.subtract(firstStart);
    }

    @Override
    public boolean isWritable(ImageHeapInfo region) {
        return isWritable;
    }

    @Override
    public boolean usesUnalignedChunks(ImageHeapInfo region) {
        return unalignedChunks;
    }

    @Override
    public final void visitObjects(ImageHeapInfo region, ObjectVisitor visitor) {
        boolean alignedChunks = !unalignedChunks;
        ImageHeapWalker.walkPartition(getFirstObject(region), getLastObject(region), visitor, alignedChunks);
    }
}

final class AlignedReadOnlyMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    AlignedReadOnlyMemoryWalkerAccess() {
        super(false, false);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstAlignedReadOnlyObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastAlignedReadOnlyObject;
    }
}

final class AlignedWritableMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    AlignedWritableMemoryWalkerAccess() {
        super(true, false);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstAlignedWritableObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastAlignedWritableObject;
    }
}

final class UnalignedWritableMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    UnalignedWritableMemoryWalkerAccess() {
        super(true, true);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstUnalignedWritableObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastUnalignedWritableObject;
    }
}

final class UnalignedReadOnlyMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    UnalignedReadOnlyMemoryWalkerAccess() {
        super(false, true);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstUnalignedReadOnlyObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastUnalignedReadOnlyObject;
    }
}
