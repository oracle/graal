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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.TreeMap;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.graal.compiler.debug.Assertions;

/**
 * The image heap comes in partitions. Each partition holds objects with different properties
 * (read-only/writable, primitives/objects).
 */
public class ChunkedImageHeapPartition implements ImageHeapPartition {
    private final String name;
    private final boolean writable;
    private final boolean hugeObjects;
    private final int minimumObjectSize;
    private final List<ImageHeapObject> objects = new ArrayList<>();

    Object firstObject;
    Object lastObject;

    long startOffset = -1;
    long endOffset = -1;

    private int startAlignment = -1;
    private int endAlignment = -1;

    ChunkedImageHeapPartition(String name, boolean writable, boolean hugeObjects) {
        this.name = name;
        this.writable = writable;
        this.hugeObjects = hugeObjects;

        /* Cache to prevent frequent lookups of the object layout from ImageSingletons. */
        this.minimumObjectSize = ConfigurationValues.getObjectLayout().getMinImageHeapObjectSize();
    }

    void assign(ImageHeapObject obj) {
        assert obj.getPartition() == this : obj;
        objects.add(obj);
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

        for (ImageHeapObject info : objects) { // No need to sort by size
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
        NavigableMap<Long, Queue<ImageHeapObject>> sortedObjects = createSortedObjectsMap();
        while (!sortedObjects.isEmpty()) {
            ImageHeapObject info = dequeueBestFit(sortedObjects, allocator.getRemainingBytesInAlignedChunk());
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

    private NavigableMap<Long, Queue<ImageHeapObject>> createSortedObjectsMap() {
        ImageHeapObject[] sorted = objects.toArray(new ImageHeapObject[0]);
        Arrays.sort(sorted, new SizeComparator());

        NavigableMap<Long, Queue<ImageHeapObject>> map = new TreeMap<>();
        Queue<ImageHeapObject> currentQueue = null;
        long currentObjectsSize = -1;
        for (ImageHeapObject obj : sorted) {
            long objSize = obj.getSize();
            if (objSize != currentObjectsSize) {
                assert objSize > currentObjectsSize && objSize >= ConfigurationValues.getObjectLayout().getMinImageHeapObjectSize() : Assertions.errorMessage(obj, objSize);
                currentObjectsSize = objSize;
                currentQueue = new ArrayDeque<>();
                map.put(currentObjectsSize, currentQueue);
            }
            assert currentQueue != null;
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
    public String getName() {
        return name;
    }

    boolean isWritable() {
        return writable;
    }

    boolean usesUnalignedObjects() {
        return hugeObjects;
    }

    final int getStartAlignment() {
        assert startAlignment >= 0 : "Start alignment not yet assigned";
        return startAlignment;
    }

    void setStartAlignment(int alignment) {
        assert this.startAlignment == -1 : "Start alignment already assigned: " + this.startAlignment;
        this.startAlignment = alignment;
    }

    final int getEndAlignment() {
        assert endAlignment >= 0 : "End alignment not yet assigned";
        return endAlignment;
    }

    void setEndAlignment(int endAlignment) {
        assert this.endAlignment == -1 : "End alignment already assigned: " + this.endAlignment;
        this.endAlignment = endAlignment;
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

    public String toString() {
        return name;
    }

    private static class SizeComparator implements Comparator<ImageHeapObject> {
        @Override
        public int compare(ImageHeapObject o1, ImageHeapObject o2) {
            return Long.signum(o1.getSize() - o2.getSize());
        }
    }
}
