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

import java.util.ArrayList;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.gc.shenandoah.ShenandoahHeap;
import com.oracle.svm.core.gc.shenandoah.ShenandoahImageHeapInfo;
import com.oracle.svm.core.gc.shenandoah.ShenandoahRegionType;
import com.oracle.svm.core.image.ImageHeap;
import com.oracle.svm.core.image.ImageHeapObject;

import jdk.graal.compiler.core.common.NumUtil;

public class ShenandoahImageHeapRegions {
    private final ImageHeap imageHeap;
    private final int regionSize = ShenandoahRegionSize.getValue();
    private final ArrayList<ShenandoahImageHeapRegion> regions = new ArrayList<>();

    private int firstRegionWithFreeSpace;
    private ShenandoahRegionType defaultRegionType;
    private ShenandoahRegionType startsHumongousRegionType;
    private ShenandoahRegionType continuesHumongousRegionType;

    public ShenandoahImageHeapRegions(ImageHeap imageHeap) {
        this.imageHeap = imageHeap;
    }

    public void setDefaultRegionType(ShenandoahRegionType value) {
        if (value == ShenandoahRegionType.ClosedImageHeap) {
            defaultRegionType = ShenandoahRegionType.ClosedImageHeap;
            startsHumongousRegionType = ShenandoahRegionType.ClosedImageHeapStartsHumongous;
            continuesHumongousRegionType = ShenandoahRegionType.ClosedImageHeapContinuesHumongous;
        } else {
            assert value == ShenandoahRegionType.OpenImageHeap;
            defaultRegionType = ShenandoahRegionType.OpenImageHeap;
            startsHumongousRegionType = ShenandoahRegionType.OpenImageHeapStartsHumongous;
            continuesHumongousRegionType = ShenandoahRegionType.OpenImageHeapContinuesHumongous;
        }
    }

    public void allocate(ShenandoahImageHeapPartition partition, ShenandoahImageHeapObjectComparator comparator) {
        /* Use first-fit decreasing bin packing for assigning objects to heap regions. */
        ArrayList<ImageHeapObject> objects = partition.getObjects();
        objects.sort(comparator);
        for (ImageHeapObject info : objects) {
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
        /* Humongous objects are always added in separate regions. */
        ShenandoahImageHeapRegion humongousStartRegion = new ShenandoahImageHeapRegion(startsHumongousRegionType, getOffsetOfNextRegion());
        humongousStartRegion.allocate(info);
        regions.add(humongousStartRegion);

        long remainingObjectSize = info.getSize() - regionSize;
        do {
            int usedRegionSpace = remainingObjectSize > regionSize ? regionSize : NumUtil.safeToInt(remainingObjectSize);
            ShenandoahImageHeapRegion continuesHumongousRegion = new ShenandoahImageHeapRegion(continuesHumongousRegionType, getOffsetOfNextRegion());
            continuesHumongousRegion.increaseUsed(usedRegionSpace);
            regions.add(continuesHumongousRegion);

            remainingObjectSize -= usedRegionSpace;
        } while (remainingObjectSize > 0);
    }

    private void allocateNormalObject(ImageHeapObject info) {
        for (int i = firstRegionWithFreeSpace; i < regions.size(); i++) {
            ShenandoahImageHeapRegion region = regions.get(i);
            if (!region.getType().isHumongous() && region.getRemainingSpace() >= info.getSize()) {
                assert region.getType() == defaultRegionType;
                region.allocate(info);
                return;
            }
        }

        /* No existing region had sufficient free space, so start a new one. */
        ShenandoahImageHeapRegion region = new ShenandoahImageHeapRegion(defaultRegionType, getOffsetOfNextRegion());
        region.allocate(info);
        regions.add(region);
    }

    public void endPartition(ShenandoahImageHeapPartition partition, int alignment) {
        ensureAlignment(partition, alignment);
        assert firstRegionWithFreeSpace == regions.size() || firstRegionWithFreeSpace == regions.size() - 1 && regions.getLast().getUsed() % alignment == 0;
    }

    private void ensureAlignment(ShenandoahImageHeapPartition partition, int alignment) {
        assert alignment > 0 && alignment <= regionSize;
        assert regionSize % alignment == 0 : "we assume that region starts are always aligned";
        assert alignment % ConfigurationValues.getObjectLayout().getAlignment() == 0 : "alignment must be a multiple of the object alignment";

        ShenandoahImageHeapRegion lastRegion = regions.getLast();
        if (alignment == regionSize || lastRegion.getType().isHumongous()) {
            /* Mark all regions, including the last region, as full. */
            firstRegionWithFreeSpace = regions.size();
            return;
        }

        /* Check if the end of the current region is already aligned. */
        int used = lastRegion.getUsed();
        int availableBytes = lastRegion.getRemainingSpace();
        int bytesToFill = NumUtil.roundUp(used, alignment) - used;
        if (bytesToFill == 0) {
            /* Mark all regions, except the last region, as full. */
            firstRegionWithFreeSpace = regions.size() - 1;
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
                partition.add(objectInfo);
                lastRegion.allocate(objectInfo);
                assert (objectInfo.getOffset() + objectInfo.getSize()) % alignment == 0;

                /* Filler object was added - mark all regions, except the last region, as full. */
                firstRegionWithFreeSpace = regions.size() - 1;
                return;
            }
        }

        /*
         * The filler object was too large for the last region. Mark all regions, including the last
         * region, as full.
         */
        assert availableBytes < 2 * alignment;
        firstRegionWithFreeSpace = regions.size();
    }

    private long getOffsetOfNextRegion() {
        return regions.size() * ((long) regionSize) + ShenandoahHeap.get().getImageHeapOffsetInAddressSpace();
    }

    public void fillImageHeapInfo(ShenandoahImageHeapInfo info) {
        for (int i = 0; i < regions.size(); i++) {
            ShenandoahImageHeapRegion region = regions.get(i);
            info.writeHeapRegion(i, region.getType(), region.getRemainingSpace());
        }
    }

    public int getCount() {
        return regions.size();
    }

    public long getSize() {
        /* The last region is not necessarily full. */
        long sizeExceptLastRegion = Math.max(0, regions.size() - 1) * ((long) regionSize);
        ShenandoahImageHeapRegion lastRegion = regions.getLast();
        return sizeExceptLastRegion + lastRegion.getUsed();
    }

    public int countClosedImageHeapRegions() {
        int count = 0;
        for (ShenandoahImageHeapRegion region : regions) {
            if (region.getType().isClosedImageHeap()) {
                count++;
            }
        }
        return count;
    }
}
