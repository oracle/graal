/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.gc.shenandoah;

import static com.oracle.svm.core.gc.shenandoah.ShenandoahOptions.ShenandoahRegionSize;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.gc.shenandoah.ShenandoahHeap;
import com.oracle.svm.core.gc.shenandoah.ShenandoahImageHeapInfo;
import com.oracle.svm.core.gc.shenandoah.ShenandoahRegionType;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;

/**
 * Layouts the heap in a way that it matches the expectations of the C++ code. Multiple image heap
 * partitions can live in the same heap region. Partition alignment requirements are ensured via
 * filler objects.
 */
public class ShenandoahImageHeapLayouter implements ImageHeapLayouter {
    private final ShenandoahImageHeapPartition closedImageHeapReadOnly;
    private final ShenandoahImageHeapPartition closedImageHeapRelocatable;
    private final ShenandoahImageHeapPartition closedImageHeapWritable;
    private final ShenandoahImageHeapPartition openImageHeap;
    private final ShenandoahImageHeapPartition[] partitions;

    public ShenandoahImageHeapLayouter() {
        assert SubstrateOptions.SpawnIsolates.getValue();
        this.closedImageHeapReadOnly = new ShenandoahImageHeapPartition("closedImageHeapReadOnly", false);
        this.closedImageHeapRelocatable = new ShenandoahImageHeapPartition("closedImageHeapRelocatable", false);
        this.closedImageHeapWritable = new ShenandoahImageHeapPartition("closedImageHeapWritable", true);
        this.openImageHeap = new ShenandoahImageHeapPartition("openImageHeap", true);

        this.partitions = new ShenandoahImageHeapPartition[]{closedImageHeapReadOnly, closedImageHeapRelocatable, closedImageHeapWritable, openImageHeap};
    }

    @Override
    public ImageHeapPartition[] getPartitions() {
        return partitions;
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable, boolean patched) {
        VMError.guarantee(!patched, "Layered native images are not supported at the moment.");

        ShenandoahImageHeapPartition partition = choosePartition(immutable, references, relocatable);
        partition.add(info);
    }

    private ShenandoahImageHeapPartition choosePartition(boolean immutable, boolean hasReferences, boolean hasRelocatables) {
        if (immutable) {
            return hasRelocatables ? closedImageHeapRelocatable : closedImageHeapReadOnly;
        } else {
            assert !hasRelocatables;
            return hasReferences ? openImageHeap : closedImageHeapWritable;
        }
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize, ImageHeapLayouterCallback callback) {
        int regionSize = ShenandoahRegionSize.getValue();
        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        ShenandoahImageHeapObjectComparator humongousObjectsFirst = new ShenandoahImageHeapObjectComparator(regionSize, true);
        ShenandoahImageHeapObjectComparator humongousObjectsLast = new ShenandoahImageHeapObjectComparator(regionSize, false);
        ShenandoahImageHeapRegions regions = new ShenandoahImageHeapRegions(imageHeap);

        /* Closed image heap regions. */
        regions.setDefaultRegionType(ShenandoahRegionType.ClosedImageHeap);

        regions.allocate(closedImageHeapReadOnly, humongousObjectsFirst);
        regions.endPartition(closedImageHeapReadOnly, objectAlignment);

        regions.allocate(closedImageHeapRelocatable, humongousObjectsLast);
        regions.endPartition(closedImageHeapRelocatable, objectAlignment);

        regions.allocate(closedImageHeapWritable, humongousObjectsLast);
        regions.endPartition(closedImageHeapWritable, regionSize);

        /* Open image heap regions. */
        regions.setDefaultRegionType(ShenandoahRegionType.OpenImageHeap);

        regions.allocate(openImageHeap, humongousObjectsFirst);
        ShenandoahImageHeapInfo imageHeapInfo = initializeImageHeapInfo(imageHeap, regions);
        regions.endPartition(openImageHeap, regionSize);
        /* Done with the layouting, no further objects may be added to the image heap. */

        regions.fillImageHeapInfo(imageHeapInfo);

        /* Compute the memory layout of the image heap (partitions can be empty). */
        long startOffset = ShenandoahHeap.get().getImageHeapOffsetInAddressSpace();
        long imageHeapSize = NumUtil.roundUp(regions.getSize(), SubstrateOptions.getPageSize());
        long endOffset = startOffset + imageHeapSize;
        long openImageHeapBegin = getOffsetOfFirstObject(openImageHeap, endOffset);
        long closedImageHeapWritableBegin = getOffsetOfFirstObject(closedImageHeapWritable, openImageHeapBegin);
        long closedImageHeapRelocatableBegin = getOffsetOfFirstObject(closedImageHeapRelocatable, closedImageHeapWritableBegin);
        long closedImageHeapReadOnlyBegin = getOffsetOfFirstObject(closedImageHeapReadOnly, closedImageHeapRelocatableBegin);

        assert startOffset == closedImageHeapReadOnlyBegin;

        openImageHeap.setSize(openImageHeapBegin, endOffset);
        closedImageHeapWritable.setSize(closedImageHeapWritableBegin, openImageHeapBegin);
        closedImageHeapRelocatable.setSize(closedImageHeapRelocatableBegin, closedImageHeapWritableBegin);
        closedImageHeapReadOnly.setSize(closedImageHeapReadOnlyBegin, closedImageHeapRelocatableBegin);

        /*
         * Align the writable part of the image heap to the build-time page size. As a side-effect,
         * a few read-only objects may end up in the closed but writable part of the image heap.
         */
        long writableBegin = UnsignedUtils.roundDown(Word.unsigned(closedImageHeapWritableBegin), Word.unsigned(pageSize)).rawValue();
        long writableEnd = endOffset;
        long writableSize = writableEnd - writableBegin;
        /* Layered images are not supported yet, so there is no writable-patched section. */
        long writablePatchedBegin = closedImageHeapWritableBegin;
        long writablePatchedSize = 0;

        assert writableBegin % pageSize == 0;
        assert openImageHeapBegin % regionSize == 0;

        return new ImageHeapLayoutInfo(startOffset, endOffset, writableBegin, writableSize, closedImageHeapRelocatableBegin, closedImageHeapRelocatable.getSize(), writablePatchedBegin,
                        writablePatchedSize);
    }

    @Override
    public void afterLayout(ImageHeap imageHeap) {
        if (imageHeap instanceof NativeImageHeap nativeImageHeap) {
            /* Update the arrays in the image heap info, now that the layouting is done. */
            ShenandoahImageHeapInfo imageHeapInfo = ShenandoahHeap.getImageHeapInfo();
            ImageHeapScanner heapScanner = nativeImageHeap.aUniverse.getHeapScanner();
            ScanReason reason = new OtherReason("Manual rescan triggered from " + ShenandoahImageHeapLayouter.class);
            heapScanner.rescanField(imageHeapInfo, ReflectionUtil.lookupField(ShenandoahImageHeapInfo.class, "regionTypes"), reason);
            heapScanner.rescanField(imageHeapInfo, ReflectionUtil.lookupField(ShenandoahImageHeapInfo.class, "regionFreeSpaces"), reason);
        }
    }

    private ShenandoahImageHeapInfo initializeImageHeapInfo(ImageHeap imageHeap, ShenandoahImageHeapRegions regions) {
        // Below, we are adding objects to the image heap. Those objects could be placed in a new
        // region, so we need one extra region.
        int regionCount = regions.getCount();
        byte[] regionType = new byte[regionCount + 1];
        int[] regionFreeSpace = new int[regionCount + 1];

        addLateToImageHeap(imageHeap, regionType, "heap metadata", openImageHeap, regions);
        addLateToImageHeap(imageHeap, regionFreeSpace, "heap metadata", openImageHeap, regions);
        assert regions.getCount() <= regionCount + 1;

        // After adding all the objects, we can obtain the final region count.
        int closedImageHeapRegions = regions.countClosedImageHeapRegions();
        int openImageHeapRegions = regions.getCount() - closedImageHeapRegions;

        ShenandoahImageHeapInfo info = ShenandoahHeap.getImageHeapInfo();
        info.initialize(closedImageHeapRegions, openImageHeapRegions, regionType, regionFreeSpace, imageHeap.countPatchAndVerifyDynamicHubs());
        return info;
    }

    private static void addLateToImageHeap(ImageHeap imageHeap, Object object, String reason, ShenandoahImageHeapPartition partition, ShenandoahImageHeapRegions regions) {
        ImageHeapObject objectInfo = imageHeap.addLateToImageHeap(object, reason);
        partition.add(objectInfo);
        regions.allocate(objectInfo);
    }

    private static long getOffsetOfFirstObject(ShenandoahImageHeapPartition partition, long defaultValue) {
        ArrayList<ImageHeapObject> objects = partition.getObjects();
        if (objects.isEmpty()) {
            return defaultValue;
        }

        /*
         * Any arbitrary object in the partition could have the lowest offset as we use first fit
         * decreasing bin packing and place multiple partitions in one image heap region.
         */
        long minOffset = Long.MAX_VALUE;
        for (ImageHeapObject o : objects) {
            minOffset = Math.min(minOffset, o.getOffset());
        }
        return minOffset;
    }

    @Override
    public void writeMetadata(ByteBuffer imageHeapBytes, long imageHeapOffsetInBuffer) {
        // nothing to do
    }
}
