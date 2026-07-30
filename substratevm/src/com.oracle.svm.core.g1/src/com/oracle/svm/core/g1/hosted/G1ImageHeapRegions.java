/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.G1ImageHeapInfo;
import com.oracle.svm.core.g1.G1Options;
import com.oracle.svm.core.g1.G1RegionType;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.image.ImageHeapObjectSorter;

import jdk.graal.compiler.core.common.NumUtil;

public class G1ImageHeapRegions {
    private final ImageHeap imageHeap;
    private final int regionSize = G1Options.G1HeapRegionSize.getValue();
    private final ArrayList<G1ImageHeapRegion> regions = new ArrayList<>();
    private final ArrayList<G1ImageHeapRegion> regularRegionsWithFreeSpace = new ArrayList<>();
    private final int minimumObjectSize;

    private G1RegionType defaultRegionType;
    private G1RegionType startsHumongousRegionType;
    private G1RegionType continuesHumongousRegionType;

    public G1ImageHeapRegions(ImageHeap imageHeap) {
        this.imageHeap = imageHeap;
        this.minimumObjectSize = ObjectLayout.singleton().getMinImageHeapObjectSize();
    }

    public void setDefaultRegionType(G1RegionType value) {
        if (value == G1RegionType.ClosedImageHeap) {
            defaultRegionType = G1RegionType.ClosedImageHeap;
            startsHumongousRegionType = G1RegionType.ClosedImageHeapStartsHumongous;
            continuesHumongousRegionType = G1RegionType.ClosedImageHeapContinuesHumongous;
        } else {
            assert value == G1RegionType.OpenImageHeap;
            defaultRegionType = G1RegionType.OpenImageHeap;
            startsHumongousRegionType = G1RegionType.OpenImageHeapStartsHumongous;
            continuesHumongousRegionType = G1RegionType.OpenImageHeapContinuesHumongous;
        }
    }

    public void allocate(G1ImageHeapPartition partition, ImageHeapObjectSorter objectSorter, G1ImageHeapObjectComparator comparator) {
        partition.sortObjects(objectSorter, comparator);
        for (ImageHeapObject info : partition.getObjects()) {
            allocate(info);
        }
    }

    public void allocate(ImageHeapObject info) {
        if (info.getSize() > regionSize) {
            allocateHumongousObject(info);
        } else {
            allocateNormalObject(info);
        }
    }

    private void allocateHumongousObject(ImageHeapObject info) {
        // Humongous objects are always added in separate regions.
        G1ImageHeapRegion humongousStartRegion = new G1ImageHeapRegion(startsHumongousRegionType, getOffsetOfNextRegion());
        humongousStartRegion.allocate(info);
        regions.add(humongousStartRegion);

        long remainingObjectSize = info.getSize() - regionSize;
        do {
            int usedRegionSpace = remainingObjectSize > regionSize ? regionSize : NumUtil.safeToInt(remainingObjectSize);
            G1ImageHeapRegion continuesHumongousRegion = new G1ImageHeapRegion(continuesHumongousRegionType, getOffsetOfNextRegion());
            continuesHumongousRegion.increaseUsed(usedRegionSpace);
            regions.add(continuesHumongousRegion);

            remainingObjectSize -= usedRegionSpace;
        } while (remainingObjectSize > 0);
    }

    private void allocateNormalObject(ImageHeapObject info) {
        int regularRegionsWithFreeSpaceCount = regularRegionsWithFreeSpace.size();
        for (int i = 0; i < regularRegionsWithFreeSpaceCount; i++) {
            G1ImageHeapRegion region = regularRegionsWithFreeSpace.get(i);
            if (info.getSize() <= region.getRemainingSpace()) {
                assert region.getType() == defaultRegionType;
                region.allocate(info);
                if (region.getRemainingSpace() < minimumObjectSize) {
                    regularRegionsWithFreeSpace.remove(i);
                }
                return;
            }
        }

        /* No existing region had sufficient free space, so start a new one. */
        G1ImageHeapRegion region = new G1ImageHeapRegion(defaultRegionType, getOffsetOfNextRegion());
        region.allocate(info);
        regions.add(region);
        if (region.getRemainingSpace() >= minimumObjectSize) {
            regularRegionsWithFreeSpace.add(region);
        }
    }

    public void endPartition(G1ImageHeapPartition partition, int alignment) {
        ensureAlignment(partition, alignment);
        assert regularRegionsWithFreeSpace.isEmpty() || regularRegionsWithFreeSpace.size() == 1 && regularRegionsWithFreeSpace.getFirst().getUsed() % alignment == 0;
    }

    private void ensureAlignment(G1ImageHeapPartition partition, int alignment) {
        assert alignment > 0 && alignment <= regionSize;
        assert regionSize % alignment == 0 : "we assume that region starts are always aligned";
        assert alignment % ObjectLayout.singleton().getAlignment() == 0 : "alignment must be a multiple of the object alignment";

        if (regularRegionsWithFreeSpace.isEmpty()) {
            /* All regions are full. */
            return;
        }

        G1ImageHeapRegion lastRegion = regularRegionsWithFreeSpace.getLast();
        regularRegionsWithFreeSpace.clear();
        if (alignment == regionSize || lastRegion.getType().isHumongous()) {
            /* Mark all regions, including the last region, as full. */
            assert regularRegionsWithFreeSpace.isEmpty();
            return;
        }

        /* Check if the end of the current region is already aligned. */
        int used = lastRegion.getUsed();
        int availableBytes = lastRegion.getRemainingSpace();
        int bytesToFill = NumUtil.roundUp(used, alignment) - used;
        if (bytesToFill == 0) {
            /* Mark all regions, except the last region, as full. */
            assert regularRegionsWithFreeSpace.isEmpty();
            regularRegionsWithFreeSpace.add(lastRegion);
            return;
        }

        /* Check if it makes sense to use a filler object to ensure the alignment. */
        if (bytesToFill < availableBytes) {
            ImageHeapObject objectInfo = imageHeap.addFillerObject(bytesToFill);
            if (objectInfo == null) {
                /* The gap may be too small for the filler. Make the gap larger and try again. */
                bytesToFill += alignment;
                if (bytesToFill < availableBytes) {
                    objectInfo = imageHeap.addFillerObject(bytesToFill);
                }
            }

            if (objectInfo != null) {
                partition.assign(objectInfo);
                lastRegion.allocate(objectInfo);
                assert (objectInfo.getOffset() + objectInfo.getSize()) % alignment == 0;

                /* Filler object was added - mark all regions, except the last region, as full. */
                assert regularRegionsWithFreeSpace.isEmpty();
                regularRegionsWithFreeSpace.add(lastRegion);
                return;
            }
        }

        /*
         * The filler object was too large for the last region. Mark all regions, including the last
         * region, as full.
         */
        assert availableBytes < 2 * alignment;
        assert regularRegionsWithFreeSpace.isEmpty();
    }

    private long getOffsetOfNextRegion() {
        return regions.size() * ((long) regionSize) + G1Heap.get().getImageHeapOffsetInAddressSpace();
    }

    public void fillImageHeapInfo(G1ImageHeapInfo info) {
        for (int i = 0; i < regions.size(); i++) {
            G1ImageHeapRegion region = regions.get(i);
            info.writeHeapRegion(i, region.getType(), region.getRemainingSpace());
        }
    }

    public int getCount() {
        return regions.size();
    }

    public long getSize() {
        // The last region is not necessarily full.
        long sizeExceptLastRegion = Math.max(0, regions.size() - 1) * ((long) regionSize);
        G1ImageHeapRegion lastRegion = regions.getLast();
        return sizeExceptLastRegion + lastRegion.getUsed();
    }

    public int countClosedImageHeapRegions() {
        int count = 0;
        for (G1ImageHeapRegion region : regions) {
            if (region.getType().isClosedImageHeap()) {
                count++;
            }
        }
        return count;
    }
}
