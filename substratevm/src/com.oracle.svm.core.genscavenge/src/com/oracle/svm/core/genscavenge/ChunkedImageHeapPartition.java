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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.TreeMap;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AbstractImageHeapLayouter.AbstractImageHeapPartition;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

/**
 * An unstructured image heap partition that just contains a linear sequence of image heap objects.
 */
public class ChunkedImageHeapPartition extends AbstractImageHeapPartition {
    private final boolean hugeObjects;

    Object firstObject;
    Object lastObject;

    long startOffset = -1;
    long endOffset = -1;

    private final int minimumObjectSize;

    ChunkedImageHeapPartition(String name, boolean writable, boolean hugeObjects) {
        super(name, writable);
        this.hugeObjects = hugeObjects;

        /* Cache to prevent frequent lookups of the object layout from ImageSingletons. */
        minimumObjectSize = ConfigurationValues.getObjectLayout().getMinimumObjectSize();
    }

    boolean usesUnalignedObjects() {
        return hugeObjects;
    }

    void layout(ChunkedImageHeapAllocator allocator) {
        if (hugeObjects) {
            layoutInUnalignedChunks(allocator);
        } else {
            layoutInAlignedChunks(allocator);
        }
    }

    private void layoutInUnalignedChunks(ChunkedImageHeapAllocator allocator) {
        allocator.finishAlignedChunk();
        allocator.alignBetweenChunks(getStartAlignment());
        startOffset = allocator.getPosition();

        for (ImageHeapObject info : getObjects()) { // No need to sort by size
            appendAllocatedObject(info, allocator.allocateUnalignedChunkForObject(info, isWritable()));
        }

        allocator.alignBetweenChunks(getEndAlignment());
        endOffset = allocator.getPosition();
    }

    private void layoutInAlignedChunks(ChunkedImageHeapAllocator allocator) {
        allocator.maybeStartAlignedChunk();
        allocator.alignInAlignedChunk(getStartAlignment());
        startOffset = allocator.getPosition();

        allocateObjectsInAlignedChunks(allocator);

        allocator.alignInAlignedChunk(getEndAlignment());
        endOffset = allocator.getPosition();
    }

    private void allocateObjectsInAlignedChunks(ChunkedImageHeapAllocator allocator) {
        NavigableMap<Long, Queue<ImageHeapObject>> objects = createSortedObjectsMap(getObjects());
        while (!objects.isEmpty()) {
            ImageHeapObject info = dequeueBestFit(objects, allocator.getRemainingBytesInAlignedChunk());
            if (info == null) {
                allocator.startNewAlignedChunk();
            } else {
                appendAllocatedObject(info, allocator.allocateObjectInAlignedChunk(info, isWritable()));
            }
        }
    }

    private ImageHeapObject dequeueBestFit(NavigableMap<Long, Queue<ImageHeapObject>> objects, long nbytes) {
        if (nbytes < minimumObjectSize) {
            return null;
        }
        Map.Entry<Long, Queue<ImageHeapObject>> entry = objects.floorEntry(nbytes);
        if (entry == null) {
            return null;
        }
        Queue<ImageHeapObject> queue = entry.getValue();
        ImageHeapObject info = queue.remove();
        if (queue.isEmpty()) {
            objects.remove(entry.getKey());
        }
        return info;
    }

    private static NavigableMap<Long, Queue<ImageHeapObject>> createSortedObjectsMap(List<ImageHeapObject> objects) {
        ImageHeapObject[] sorted = objects.toArray(new ImageHeapObject[0]);
        Arrays.sort(sorted, new SizeComparator());

        NavigableMap<Long, Queue<ImageHeapObject>> map = new TreeMap<>();
        Queue<ImageHeapObject> currentQueue = null;
        long currentObjectsSize = -1;
        for (ImageHeapObject obj : sorted) {
            long objSize = obj.getSize();
            if (objSize != currentObjectsSize) {
                assert objSize > currentObjectsSize && objSize >= ConfigurationValues.getObjectLayout().getMinimumObjectSize();
                currentObjectsSize = objSize;
                currentQueue = new ArrayDeque<>();
                map.put(currentObjectsSize, currentQueue);
            }
            currentQueue.add(obj);
        }
        return map;
    }

    private void appendAllocatedObject(ImageHeapObject info, long allocationOffset) {
        if (firstObject == null) {
            firstObject = extractObject(info);
        }
        assert info.getPartition() == this;
        long offsetInPartition = allocationOffset - startOffset;
        assert ConfigurationValues.getObjectLayout().isAligned(offsetInPartition) : "start: " + offsetInPartition + " must be aligned.";
        info.setOffsetInPartition(offsetInPartition);
        lastObject = extractObject(info);
    }

    private static Object extractObject(ImageHeapObject info) {
        if (info.getConstant() instanceof SubstrateObjectConstant) {
            return info.getObject();
        } else {
            /*
             * The info wraps an ImageHeapObject, i.e., a build time representation of an object
             * that is not backed by a raw hosted object. We set the partition limit to the actual
             * constant. The constant reflection provider knows that this is a build time value, and
             * it will not wrap it in a JavaConstant when reading it. This case is not different
             * from normal objects referencing simulated objects.
             */
            return info.getConstant();
        }
    }

    @Override
    public long getStartOffset() {
        assert startOffset >= 0 : "Start offset not yet set";
        return startOffset;
    }

    public long getEndOffset() {
        assert endOffset >= 0 : "End offset not yet set";
        return endOffset;
    }

    @Override
    public long getSize() {
        return getEndOffset() - getStartOffset();
    }

    private static class SizeComparator implements Comparator<ImageHeapObject> {
        @Override
        public int compare(ImageHeapObject o1, ImageHeapObject o2) {
            return Long.signum(o1.getSize() - o2.getSize());
        }
    }
}
