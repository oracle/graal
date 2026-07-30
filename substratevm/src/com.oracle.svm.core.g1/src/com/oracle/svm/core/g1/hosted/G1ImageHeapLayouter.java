/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1.hosted;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.G1ImageHeapInfo;
import com.oracle.svm.core.g1.G1Options;
import com.oracle.svm.core.g1.G1RegionType;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapObjectSorter;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.image.ImageHeapReasonSupport;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Layouts the heap in a way that it matches the expectations of the C++ code:
 * <ul>
 * <li>Humongous objects are in humongous regions.</li>
 * <li>Non-humongous objects must not span multiple regions.</li>
 * </ul>
 *
 * Multiple image heap partitions can live in the same heap region. Partition alignment requirements
 * are ensured via filler objects.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public class G1ImageHeapLayouter implements ImageHeapLayouter {
    private final G1ImageHeapPartition closedImageHeapReadOnly;
    private final G1ImageHeapPartition closedImageHeapRelocatable;
    private final G1ImageHeapPartition closedImageHeapWritable;
    private final G1ImageHeapPartition openImageHeap;
    private final G1ImageHeapPartition[] partitions;

    public G1ImageHeapLayouter() {
        this.closedImageHeapReadOnly = new G1ImageHeapPartition("closedImageHeapReadOnly", false);
        this.closedImageHeapRelocatable = new G1ImageHeapPartition("closedImageHeapRelocatable", false);
        this.closedImageHeapWritable = new G1ImageHeapPartition("closedImageHeapWritable", true);
        this.openImageHeap = new G1ImageHeapPartition("openImageHeap", true);

        this.partitions = new G1ImageHeapPartition[]{closedImageHeapReadOnly, closedImageHeapRelocatable, closedImageHeapWritable, openImageHeap};
    }

    @Override
    public ImageHeapPartition[] getPartitions() {
        return partitions;
    }

    G1ImageHeapPartition getOpenImageHeapPartition() {
        return openImageHeap;
    }

    @Override
    public void assignObjectToPartition(ImageHeapObject info, boolean immutable, boolean references, boolean relocatable, boolean patched) {
        VMError.guarantee(!patched, "Layered native images are not supported at the moment.");

        G1ImageHeapPartition partition = choosePartition(immutable, references, relocatable);
        partition.assign(info);
    }

    private G1ImageHeapPartition choosePartition(boolean immutable, boolean hasReferences, boolean hasRelocatables) {
        if (immutable) {
            return hasRelocatables ? closedImageHeapRelocatable : closedImageHeapReadOnly;
        } else {
            assert !hasRelocatables;
            return hasReferences ? openImageHeap : closedImageHeapWritable;
        }
    }

    @Override
    public ImageHeapLayoutInfo layout(ImageHeap imageHeap, int pageSize, ImageHeapObjectSorter objectSorter, ImageHeapLayouterCallback callback) {
        int regionSize = G1Options.G1HeapRegionSize.getValue();
        int objectAlignment = ObjectLayout.singleton().getAlignment();
        G1ImageHeapObjectComparator humongousObjectsFirst = new G1ImageHeapObjectComparator(regionSize, true);
        G1ImageHeapObjectComparator humongousObjectsLast = new G1ImageHeapObjectComparator(regionSize, false);
        G1ImageHeapRegions regions = new G1ImageHeapRegions(imageHeap);

        /* Closed image heap regions */
        regions.setDefaultRegionType(G1RegionType.ClosedImageHeap);

        regions.allocate(closedImageHeapReadOnly, objectSorter, humongousObjectsFirst);
        regions.endPartition(closedImageHeapReadOnly, objectAlignment);

        regions.allocate(closedImageHeapRelocatable, objectSorter, humongousObjectsLast);
        regions.endPartition(closedImageHeapRelocatable, objectAlignment);

        regions.allocate(closedImageHeapWritable, objectSorter, humongousObjectsLast);
        regions.endPartition(closedImageHeapWritable, regionSize);

        /* Open image heap regions */
        regions.setDefaultRegionType(G1RegionType.OpenImageHeap);

        regions.allocate(openImageHeap, objectSorter, humongousObjectsFirst);
        G1ImageHeapInfo imageHeapInfo = initializeImageHeapInfo(imageHeap, regions);
        regions.endPartition(openImageHeap, regionSize);
        /* Done with the layouting, no further objects may be added to the image heap. */

        regions.fillImageHeapInfo(imageHeapInfo);

        /* Compute the memory layout of the image heap (partitions can be empty). */
        long startOffset = G1Heap.get().getImageHeapOffsetInAddressSpace();
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
                        writablePatchedSize, pageSize);
    }

    private G1ImageHeapInfo initializeImageHeapInfo(ImageHeap imageHeap, G1ImageHeapRegions regions) {
        // Below, we are adding objects to the image heap. Those objects could be placed in a new
        // region, so we need one extra region.
        int regionCount = regions.getCount();
        byte[] regionType = new byte[regionCount + 1];
        int[] regionFreeSpace = new int[regionCount + 1];

        Object heapMetadataReason = ImageHeapReasonSupport.singleton().description("heap metadata");
        addLateToImageHeap(imageHeap, regionType, heapMetadataReason, openImageHeap, regions);
        addLateToImageHeap(imageHeap, regionFreeSpace, heapMetadataReason, openImageHeap, regions);
        assert regions.getCount() <= regionCount + 1;

        // After adding all the objects, we can obtain the final region count.
        int closedImageHeapRegions = regions.countClosedImageHeapRegions();
        int openImageHeapRegions = regions.getCount() - closedImageHeapRegions;

        G1ImageHeapInfo info = G1Heap.getImageHeapInfo();
        info.initialize(closedImageHeapRegions, openImageHeapRegions, regionType, regionFreeSpace, imageHeap.countPatchAndVerifyDynamicHubs());
        return info;
    }

    private static void addLateToImageHeap(ImageHeap imageHeap, Object object, Object reason, G1ImageHeapPartition partition, G1ImageHeapRegions regions) {
        ImageHeapObject objectInfo = imageHeap.addLateToImageHeap(object, reason);
        partition.assign(objectInfo);
        regions.allocate(objectInfo);
    }

    private static long getOffsetOfFirstObject(G1ImageHeapPartition partition, long defaultValue) {
        ArrayList<ImageHeapObject> objects = partition.getObjects();
        if (objects.isEmpty()) {
            return defaultValue;
        }

        // Any arbitrary object in the partition could have the lowest offset as we use first fit
        // decreasing bin packing and place multiple partitions in one image heap region.
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
