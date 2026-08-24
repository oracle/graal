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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;

import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.image.ImageHeapLayouter.ImageHeapLayouterControl;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;

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

    void layout(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        if (hugeObjects) {
            layoutInUnalignedChunks(allocator, control);
        } else {
            layoutInAlignedChunks(allocator, control);
        }
    }

    private void layoutInUnalignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        if (objects.isEmpty()) {
            /*
             * Without objects, don't force finishing the current chunk and therefore committing
             * space for the rest of it. Another partition might be able to continue filling it, or,
             * if no more objects follow, we don't need to dedicate space in the image at all.
             */
            startOffset = allocator.getPosition();
            endOffset = startOffset;
            return;
        }

        allocator.finishAlignedChunk();
        startOffset = allocator.getPosition();

        for (ImageHeapObject info : objects) { // No need to sort by size
            appendAllocatedObject(info, allocator.allocateUnalignedChunkForObject(info, isWritable()));
            control.poll();
        }

        endOffset = allocator.getPosition();
    }

    private void layoutInAlignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        allocator.maybeStartAlignedChunk();
        startOffset = allocator.getPosition();
        allocateObjectsInAlignedChunks(allocator, control);
        endOffset = allocator.getPosition();
    }

    private void allocateObjectsInAlignedChunks(ChunkedImageHeapAllocator allocator, ImageHeapLayouterControl control) {
        SortedObjects sorted = computeSortedObjects();

        var groups = sorted.grouped().values().iterator();
        var group = groups.hasNext() ? groups.next() : null;
        while (true) {
            while (group != null && group.isEmpty()) {
                group = groups.hasNext() ? groups.next() : null;
            }
            if (group == null && sorted.others().isEmpty()) {
                break;
            }

            ImageHeapObject info = null;
            if (group != null) {
                info = dequeueBestFit(group, allocator.getRemainingBytesInAlignedChunk());
            }
            if (info == null) {
                info = dequeueBestFit(sorted.others(), allocator.getRemainingBytesInAlignedChunk());
            }
            if (info == null) {
                allocator.startNewAlignedChunk();
                control.poll();
            } else {
                appendAllocatedObject(info, allocator.allocateObjectInAlignedChunk(info, isWritable()));
            }
        }
    }

    private ImageHeapObject dequeueBestFit(NavigableMap<Long, Queue<ImageHeapObject>> sortedObjects, long nbytes) {
        if (nbytes < minimumObjectSize) {
            return null;
        }

        /**
         * Find a floor entry. We are purposefully not calling {@link TreeMap#getFloorEntry(Object)}
         * as that method allocates a new entry object. Instead, we fetch the floor key and get the
         * value for the returned key.
         */
        Long floorKey = sortedObjects.floorKey(nbytes);
        if (floorKey == null) {
            return null;
        }
        Queue<ImageHeapObject> queue = sortedObjects.get(floorKey);
        ImageHeapObject obj = queue.remove();
        if (queue.isEmpty()) {
            sortedObjects.remove(floorKey);
        }
        return obj;
    }

    private record SortedObjects(SequencedMap<Class<?>, NavigableMap<Long, Queue<ImageHeapObject>>> grouped,
                                 NavigableMap<Long, Queue<ImageHeapObject>> others) {
    }

    private SortedObjects computeSortedObjects() {
        SortedObjects sorted = new SortedObjects(new LinkedHashMap<>(), new TreeMap<>());
        sorted.grouped.put(DynamicHubCompanion.class, new TreeMap<>());
        sorted.grouped.put(ClassInitializationInfo.class, new TreeMap<>());

        for (ImageHeapObject obj : objects) {
            long objSize = obj.getSize();
            assert objSize >= ConfigurationValues.getObjectLayout().getMinImageHeapObjectSize() : Assertions.errorMessage(obj, objSize);
            NavigableMap<Long, Queue<ImageHeapObject>> map = selectObjectMap(obj, sorted);
            Queue<ImageHeapObject> q = map.computeIfAbsent(objSize, k -> new ArrayDeque<>());
            q.add(obj);
        }
        return sorted;
    }

    private static NavigableMap<Long, Queue<ImageHeapObject>> selectObjectMap(ImageHeapObject obj, SortedObjects sorted) {
        Class<?> objectClass = obj.getObjectClass();
        if (objectClass == null) {
            return sorted.others;
        }
        boolean group = false;
        if (objectClass == DynamicHubCompanion.class) {
            group = true;
        } else if (objectClass == ClassInitializationInfo.class) {
            group = true;
        } else if (AbstractOwnableSynchronizer.class.isAssignableFrom(objectClass)) {
            group = true;
        }
        if (group) {
            return sorted.grouped.computeIfAbsent(objectClass, c -> new TreeMap<>());
        }
        return sorted.others;
    }

    private void appendAllocatedObject(ImageHeapObject info, long allocationOffset) {
        if (firstObject == null) {
            firstObject = info.getWrapped();
        }
        assert info.getPartition() == this;
        long offsetInPartition = allocationOffset - startOffset;
        assert ConfigurationValues.getObjectLayout().isAligned(offsetInPartition) : "start: " + offsetInPartition + " must be aligned.";
        info.setOffsetInPartition(offsetInPartition);
        lastObject = info.getWrapped();
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
