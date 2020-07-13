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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.image.AbstractImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeap;

@Platforms(Platform.HOSTED_ONLY.class)
public class ChunkedImageHeapLayouter extends AbstractImageHeapLayouter<ChunkedImageHeapPartition> {
    private final ImageHeapInfo heapInfo;
    private final boolean compressedNullPadding;
    private final long hugeObjectThreshold;

    public ChunkedImageHeapLayouter(ImageHeapInfo heapInfo, boolean compressedNullPadding) {
        this.heapInfo = heapInfo;
        this.compressedNullPadding = compressedNullPadding;
        this.hugeObjectThreshold = HeapPolicy.getLargeArrayThreshold().rawValue();
    }

    @Override
    protected ChunkedImageHeapPartition[] createPartitionsArray(int count) {
        return new ChunkedImageHeapPartition[count];
    }

    @Override
    protected ChunkedImageHeapPartition createPartition(String name, boolean containsReferences, boolean writable, boolean hugeObjects) {
        return new ChunkedImageHeapPartition(name, writable, hugeObjects);
    }

    @Override
    protected long getHugeObjectThreshold() {
        return hugeObjectThreshold;
    }

    @Override
    protected void doLayout(ImageHeap imageHeap) {
        assert !compressedNullPadding || AlignedHeapChunk.getObjectsStartOffset().aboveThan(0) : "Expecting header to pad start so object offsets are strictly greater than 0";
        ChunkedImageHeapAllocator allocator = new ChunkedImageHeapAllocator(0);
        for (ChunkedImageHeapPartition partition : getPartitions()) {
            partition.layout(allocator);
        }
        initializeHeapInfo();
    }

    /**
     * Store which objects are at the boundaries of the image heap partitions. Here, we also merge
     * the read-only reference partition with the read-only relocatable partition.
     */
    private void initializeHeapInfo() {
        heapInfo.initialize(getReadOnlyPrimitive().firstObject, getReadOnlyPrimitive().lastObject, getReadOnlyReference().firstObject, getReadOnlyReference().lastObject,
                        getReadOnlyRelocatable().firstObject, getReadOnlyRelocatable().lastObject, getWritablePrimitive().firstObject, getWritablePrimitive().lastObject,
                        getWritableReference().firstObject, getWritableReference().lastObject, getWritableHuge().firstObject, getWritableHuge().lastObject,
                        getReadOnlyHuge().firstObject, getReadOnlyHuge().lastObject);
    }
}
