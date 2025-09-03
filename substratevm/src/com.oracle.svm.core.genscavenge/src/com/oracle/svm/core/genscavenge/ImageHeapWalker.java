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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.word.Word;

public final class ImageHeapWalker {
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> READ_ONLY_REGULAR_WALKER = new ReadOnlyRegularMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> READ_ONLY_RELOCATABLE_WALKER = new ReadOnlyRelocatableMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> WRITABLE_REGULAR_WALKER = new WritableRegularMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> WRITABLE_HUGE_WALKER = new WritableHugeMemoryWalkerAccess();
    private static final MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> READ_ONLY_HUGE_WALKER = new ReadOnlyHugeMemoryWalkerAccess();

    private ImageHeapWalker() {
    }

    public static void walkRegions(ImageHeapInfo heapInfo, MemoryWalker.ImageHeapRegionVisitor visitor) {
        visitor.visitNativeImageHeapRegion(heapInfo, READ_ONLY_REGULAR_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, READ_ONLY_RELOCATABLE_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, WRITABLE_REGULAR_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, WRITABLE_HUGE_WALKER);
        visitor.visitNativeImageHeapRegion(heapInfo, READ_ONLY_HUGE_WALKER);
    }

    public static void walkImageHeapObjects(ImageHeapInfo heapInfo, ObjectVisitor visitor) {
        walkPartition(heapInfo.firstReadOnlyRegularObject, heapInfo.lastReadOnlyRegularObject, visitor, true);
        walkPartition(heapInfo.firstReadOnlyRelocatableObject, heapInfo.lastReadOnlyRelocatableObject, visitor, true);
        walkPartition(heapInfo.firstWritableRegularObject, heapInfo.lastWritableRegularObject, visitor, true);
        walkPartition(heapInfo.firstWritableHugeObject, heapInfo.lastWritableHugeObject, visitor, false);
        walkPartition(heapInfo.firstReadOnlyHugeObject, heapInfo.lastReadOnlyHugeObject, visitor, false);
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

        /* Compute the enclosing chunk without assuming that the image heap is aligned. */
        Pointer base = Heap.getHeap().getImageHeapStart();
        Pointer offset = current.subtract(base);
        UnsignedWord chunkOffset = alignedChunks ? UnsignedUtils.roundDown(offset, HeapParameters.getAlignedHeapChunkAlignment())
                        : offset.subtract(UnalignedHeapChunk.getOffsetForObject(current));
        HeapChunk.Header<?> currentChunk = (HeapChunk.Header<?>) chunkOffset.add(base);

        // Assumption: the order of chunks in their linked list is the same order as in memory,
        // and objects are laid out as a continuous sequence without any gaps.

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
                current = alignedChunks ? AlignedHeapChunk.getObjectsStart((AlignedHeapChunk.AlignedHeader) currentChunk)
                                : UnalignedHeapChunk.getObjectStart((UnalignedHeapChunk.UnalignedHeader) currentChunk);
                // Note: current can be equal to lastPointer now, despite not having visited it yet
            }
        } while (current.belowOrEqual(lastPointer));
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void visitObjectInline(ObjectVisitor visitor, Object currentObject) {
        visitor.visitObject(currentObject);
    }
}

abstract class MemoryWalkerAccessBase implements MemoryWalker.NativeImageHeapRegionAccess<ImageHeapInfo> {
    private final String regionName;
    private final boolean isWritable;
    private final boolean consistsOfHugeObjects;

    @Platforms(Platform.HOSTED_ONLY.class)
    MemoryWalkerAccessBase(String regionName, boolean isWritable, boolean consistsOfHugeObjects) {
        this.regionName = regionName;
        this.isWritable = isWritable;
        this.consistsOfHugeObjects = consistsOfHugeObjects;
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
    public String getRegionName(ImageHeapInfo region) {
        return regionName;
    }

    @Override
    public boolean isWritable(ImageHeapInfo region) {
        return isWritable;
    }

    @Override
    public boolean consistsOfHugeObjects(ImageHeapInfo region) {
        return consistsOfHugeObjects;
    }

    @Override
    public final void visitObjects(ImageHeapInfo region, ObjectVisitor visitor) {
        boolean alignedChunks = !consistsOfHugeObjects;
        ImageHeapWalker.walkPartition(getFirstObject(region), getLastObject(region), visitor, alignedChunks);
    }
}

final class ReadOnlyRegularMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    ReadOnlyRegularMemoryWalkerAccess() {
        super("read-only", false, false);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstReadOnlyRegularObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastReadOnlyRegularObject;
    }
}

final class ReadOnlyRelocatableMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    ReadOnlyRelocatableMemoryWalkerAccess() {
        super("read-only relocatables", false, false);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstReadOnlyRelocatableObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastReadOnlyRelocatableObject;
    }
}

final class WritableRegularMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    WritableRegularMemoryWalkerAccess() {
        super("writable", true, false);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstWritableRegularObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastWritableRegularObject;
    }
}

final class WritableHugeMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    WritableHugeMemoryWalkerAccess() {
        super("writable huge", true, true);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstWritableHugeObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastWritableHugeObject;
    }
}

final class ReadOnlyHugeMemoryWalkerAccess extends MemoryWalkerAccessBase {
    @Platforms(Platform.HOSTED_ONLY.class)
    ReadOnlyHugeMemoryWalkerAccess() {
        super("read-only huge", false, true);
    }

    @Override
    public Object getFirstObject(ImageHeapInfo info) {
        return info.firstReadOnlyHugeObject;
    }

    @Override
    public Object getLastObject(ImageHeapInfo info) {
        return info.lastReadOnlyHugeObject;
    }
}
