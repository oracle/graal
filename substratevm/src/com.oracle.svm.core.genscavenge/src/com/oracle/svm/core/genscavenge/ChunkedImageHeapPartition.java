/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapAllocator.AlignedChunk;
import com.oracle.svm.core.image.ImageHeapLayouter.ImageHeapLayouterControl;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapObjectSorter;
import com.oracle.svm.core.image.ImageHeapPartition;

import jdk.graal.compiler.debug.Assertions;

/**
 * The image heap comes in partitions. Each partition holds objects with different properties
 * (read-only/writable, primitives/objects).
 */
final class ChunkedImageHeapPartition implements ImageHeapPartition {
    private final String name;
    private final boolean writable;
    private final boolean unalignedChunks;
    private final int minimumObjectSize;
    private final List<ImageHeapObject> objects = new ArrayList<>();

    Object firstObject;
    Object lastObject;

    private long startOffset = -1;
    private long endOffset = -1;

    ChunkedImageHeapPartition(String name, boolean writable, boolean unalignedChunks) {
        this.name = name;
        this.writable = writable;
        this.unalignedChunks = unalignedChunks;

        /* Cache to prevent frequent lookups of the object layout from ImageSingletons. */
        this.minimumObjectSize = ConfigurationValues.getObjectLayout().getMinImageHeapObjectSize();
    }

    void assign(ImageHeapObject obj) {
        assert obj.getPartition() == this : obj;
        objects.add(obj);
    }

    void layout(ChunkedImageHeapAllocator allocator, ImageHeapObjectSorter objectSorter, ImageHeapLayouterControl control) {
        if (objects.isEmpty()) {
            /*
             * Without objects, there is no need to start a new chunk, or to force finishing the
             * current chunk and therefore committing space for the rest of it. Another partition
             * might be able to continue filling it, or, if no more objects follow, we don't need to
             * dedicate space in the image at all.
             */
            startOffset = allocator.getPosition();
            endOffset = startOffset;
            return;
        }

        objectSorter.sort(objects);
        if (unalignedChunks) {
            layoutInUnalignedChunks(allocator, control);
        } else {
            layoutInAlignedChunks(allocator, control);
        }
    }

    private void layoutInUnalignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        allocator.finishAlignedChunk();
        startOffset = allocator.getPosition();

        for (ImageHeapObject info : objects) {
            setOffsetOfAllocatedObject(info, allocator.allocateUnalignedChunkForObject(info, isWritable()));
            control.poll();
        }
        firstObject = objects.getFirst().getWrapped();
        lastObject = objects.getLast().getWrapped();

        endOffset = allocator.getPosition();
    }

    private void layoutInAlignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        AlignedChunk firstChunk = allocator.maybeStartAlignedChunk();
        startOffset = allocator.getPosition();
        allocateObjectsInAlignedChunks(allocator, control, firstChunk);
        endOffset = allocator.getPosition();
    }

    /**
     * Allocates {@link ImageHeapObject} instances into aligned memory chunks using the provided
     * allocator and control.
     * <p>
     * NOTE: This method is invoked at runtime for building auxiliary images. For this reason,
     * iteration over chunks is intentionally performed by index rather than using Java iterators,
     * which avoids creating many short-lived Iterator objects at runtime.
     */
    private void allocateObjectsInAlignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control, AlignedChunk firstChunk) {
        int firstObjectIndex = firstChunk.getObjects().size();
        AlignedChunk lastChunk = firstChunk;
        ArrayList<AlignedChunk> allocationChunks = new ArrayList<>();
        allocationChunks.add(firstChunk);
        int objectCount = objects.size();
        for (int i = 0; i < objectCount; i++) {
            ImageHeapObject object = objects.get(i);
            long allocationOffset = -1;
            long objSize = object.getSize();
            assert objSize >= minimumObjectSize : Assertions.errorMessage(object, objSize);

            int chunksCount = allocationChunks.size();
            for (int j = 0; j < chunksCount; j++) {
                AlignedChunk chunk = allocationChunks.get(j);
                if (objSize <= chunk.getUnallocatedBytes()) {
                    allocationOffset = chunk.allocate(object, isWritable());
                    if (chunk.getUnallocatedBytes() < minimumObjectSize) {
                        allocationChunks.remove(j);
                    }
                    break;
                }
            }

            if (allocationOffset == -1) {
                lastChunk = allocator.startNewAlignedChunk();
                control.poll();

                allocationOffset = lastChunk.allocate(object, isWritable());
                if (lastChunk.getUnallocatedBytes() >= minimumObjectSize) {
                    allocationChunks.add(lastChunk);
                }
            }

            setOffsetOfAllocatedObject(object, allocationOffset);
        }

        if (firstChunk.getObjects().size() > firstObjectIndex) {
            firstObject = firstChunk.getObjects().get(firstObjectIndex).getWrapped();
        } else {
            List<AlignedChunk> alignedChunks = allocator.getAlignedChunks();
            AlignedChunk secondChunk = alignedChunks.get(alignedChunks.indexOf(firstChunk) + 1);
            firstObject = secondChunk.getObjects().getFirst().getWrapped();
        }
        lastObject = lastChunk.getObjects().getLast().getWrapped();
    }

    private void setOffsetOfAllocatedObject(ImageHeapObject info, long allocationOffset) {
        assert info.getPartition() == this;
        long offsetInPartition = allocationOffset - startOffset;
        assert ConfigurationValues.getObjectLayout().isAligned(offsetInPartition) : "start: " + offsetInPartition + " must be aligned.";
        info.setOffsetInPartition(offsetInPartition);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    boolean usesUnalignedChunks() {
        return unalignedChunks;
    }

    @Override
    public long getStartOffset() {
        assert startOffset >= 0 : "Start offset not yet set";
        return startOffset;
    }

    long getEndOffset() {
        assert endOffset >= 0 : "End offset not yet set";
        return endOffset;
    }

    @Override
    public long getSize() {
        return getEndOffset() - getStartOffset();
    }

    @Override
    public String toString() {
        return name;
    }
}
