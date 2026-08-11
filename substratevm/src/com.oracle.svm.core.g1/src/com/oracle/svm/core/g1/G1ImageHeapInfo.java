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
package com.oracle.svm.core.g1;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.g1.G1Options.G1HeapRegionSize;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;

/**
 * Stores some high-level information about the regions of the image heap. This data is passed to
 * the C++ code during VM startup.
 */
public class G1ImageHeapInfo {
    /*
     * The arrays below can be slightly larger than the total region count (elements beyond the
     * total number of image heap regions don't contain valid data).
     */
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) int closedImageHeapRegions;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) int openImageHeapRegions;
    @UnknownObjectField(availability = AfterHeapLayout.class) byte[] regionTypes;
    @UnknownObjectField(availability = AfterHeapLayout.class) int[] regionFreeSpaces;
    @UnknownPrimitiveField(availability = AfterHeapLayout.class) int dynamicHubCount;

    @Platforms(value = Platform.HOSTED_ONLY.class)
    public G1ImageHeapInfo() {
    }

    @SuppressWarnings("hiding")
    @Platforms(value = Platform.HOSTED_ONLY.class)
    public void initialize(int closedImageHeapRegions, int openImageHeapRegions, byte[] regionType, int[] regionFreeSpace, int dynamicHubCount) {
        this.closedImageHeapRegions = closedImageHeapRegions;
        this.openImageHeapRegions = openImageHeapRegions;
        this.regionTypes = regionType;
        this.regionFreeSpaces = regionFreeSpace;
        this.dynamicHubCount = dynamicHubCount;
    }

    @Platforms(value = Platform.HOSTED_ONLY.class)
    public void writeHeapRegion(int index, G1RegionType type, int freeSpace) {
        assert type != null;
        assert freeSpace >= 0 : freeSpace;
        regionTypes[index] = type.getTag();
        regionFreeSpaces[index] = freeSpace;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getNumClosedRegions() {
        return closedImageHeapRegions;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getNumOpenRegions() {
        return openImageHeapRegions;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getNumRegions() {
        return closedImageHeapRegions + openImageHeapRegions;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public byte[] getRegionTypes() {
        return regionTypes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int[] getRegionFreeSpaces() {
        return regionFreeSpaces;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer getImageHeapStart() {
        // skip the null regions before the image heap
        int nullRegionsSize = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        return KnownIntrinsics.heapBase().add(nullRegionsSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer getRegionStart(int regionIndex) {
        assert regionIndex < getNumRegions();
        return getImageHeapStart().add(Word.unsigned(regionIndex).multiply(G1HeapRegionSize.getValue()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer getRegionTop(int regionIndex) {
        assert regionIndex < getNumRegions();
        Pointer start = getRegionStart(regionIndex);
        return start.add(G1HeapRegionSize.getValue()).subtract(regionFreeSpaces[regionIndex]);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer getImageHeapEnd() {
        int nullRegionsSize = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        UnsignedWord imageHeapSize = Word.unsigned(G1HeapRegionSize.getValue()).multiply(closedImageHeapRegions + openImageHeapRegions);
        return KnownIntrinsics.heapBase().add(nullRegionsSize).add(imageHeapSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getDynamicHubCount() {
        return dynamicHubCount;
    }
}
