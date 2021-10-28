/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;

public class LinearImageHeapLayouter extends AbstractImageHeapLayouter<LinearImageHeapPartition> {
    private final ImageHeapInfo heapInfo;
    private final long startOffset;
    private final int nullRegionSize;

    public LinearImageHeapLayouter(ImageHeapInfo heapInfo, long startOffset, int nullRegionSize) {
        assert startOffset >= 0;
        assert startOffset == 0 || startOffset >= Heap.getHeap().getImageHeapOffsetInAddressSpace() : "must be relative to the heap base";
        assert nullRegionSize >= 0;
        this.heapInfo = heapInfo;
        this.startOffset = startOffset;
        this.nullRegionSize = nullRegionSize;
    }

    @Override
    protected LinearImageHeapPartition[] createPartitionsArray(int count) {
        return new LinearImageHeapPartition[count];
    }

    @Override
    protected LinearImageHeapPartition createPartition(String name, boolean containsReferences, boolean writable, boolean hugeObjects) {
        return new LinearImageHeapPartition(name, writable);
    }

    @Override
    protected ImageHeapLayoutInfo doLayout(ImageHeap imageHeap) {
        long beginOffset = startOffset + spaceReservedForNull();
        assert beginOffset >= ConfigurationValues.getObjectLayout().getAlignment() : "Zero designates null";
        LinearImageHeapAllocator allocator = new LinearImageHeapAllocator(beginOffset);
        for (LinearImageHeapPartition partition : getPartitions()) {
            partition.allocateObjects(allocator);
        }
        initializeHeapInfo(imageHeap.countDynamicHubs());
        return createLayoutInfo(startOffset, getWritablePrimitive().getStartOffset());
    }

    private long spaceReservedForNull() {
        if (startOffset == 0 && nullRegionSize == 0) {
            return ConfigurationValues.getObjectLayout().getAlignment();
        }
        return nullRegionSize;
    }

    /**
     * Store which objects are at the boundaries of the image heap partitions. Here, we also merge
     * the read-only reference partition with the read-only relocatable partition.
     */
    private void initializeHeapInfo(int dynamicHubCount) {
        heapInfo.initialize(getReadOnlyPrimitive().firstObject, getReadOnlyPrimitive().lastObject, getReadOnlyReference().firstObject, getReadOnlyReference().lastObject,
                        getReadOnlyRelocatable().firstObject, getReadOnlyRelocatable().lastObject, getWritablePrimitive().firstObject, getWritablePrimitive().lastObject,
                        getWritableReference().firstObject, getWritableReference().lastObject, getWritableHuge().firstObject, getWritableHuge().lastObject,
                        getReadOnlyHuge().firstObject, getReadOnlyHuge().lastObject, ImageHeapInfo.NO_CHUNK, ImageHeapInfo.NO_CHUNK, dynamicHubCount);
    }
}
