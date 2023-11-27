/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.nmt;

import com.oracle.svm.core.Uninterruptible;

import org.graalvm.word.WordFactory;
import com.oracle.svm.core.jdk.UninterruptibleUtils.Math;

public class NmtMemoryRegionAccess {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isEqual(NmtMemoryRegion a, NmtMemoryRegion b) {
        if (a.isNonNull() && b.isNonNull() && a.getSize() == b.getSize() && a.getBaseAddr() == b.getBaseAddr()) {
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean contains(NmtMemoryRegion outer, NmtMemoryRegion inner) {
        long innerBase = inner.getBaseAddr().rawValue();
        long innerEnd = innerBase + inner.getSize();
        if (containsAddress(outer, innerBase) && containsAddress(outer, innerEnd)) {
            return true;
        }
        return false;
    }

    /** "Contains" also includes the outer bounds. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean containsAddress(NmtMemoryRegion region, long addr) {
        long regionBase = region.getBaseAddr().rawValue();
        long regionEnd = regionBase + region.getSize();

        if (regionBase <= addr && addr <= regionEnd) {
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isOverlapping(NmtMemoryRegion region1, NmtMemoryRegion region2) {
        if (getOverlapSize(region1, region2) > 0) { // TODO Should this be <=?? [NO! end is not
                                                    // inclusive]
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static long getOverlapSize(NmtMemoryRegion region1, NmtMemoryRegion region2) {
        long region1Base = region1.getBaseAddr().rawValue();
        long region1End = region1Base + region1.getSize();
        long region2Base = region2.getBaseAddr().rawValue();
        long region2End = region2Base + region2.getSize();
        return Math.min(region1End, region2End) - Math.max(region1Base, region2Base);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isAdjacent(NmtMemoryRegion region1, NmtMemoryRegion region2) {
        if (region1.isNull() || region2.isNull()) {
            return false;
        }
        long region1Base = region1.getBaseAddr().rawValue();
        long region1End = region1Base + region1.getSize();
        long region2Base = region2.getBaseAddr().rawValue();
        long region2End = region2Base + region2.getSize();
        return region1End == region2Base || region2End == region1Base; // *** seems like end marker
                                                                       // is not inclusive. (bc end
                                                                       // = start+size)
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void excludeRegion(NmtMemoryRegion outerRegion, long start, long size) {
        long newSize = outerRegion.getSize() - size;
        if (outerRegion.getBaseAddr().rawValue() == start) {
            outerRegion.setBaseAddr(WordFactory.pointer(start + size));
        }
        outerRegion.setSize(newSize);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void expandRegion(NmtMemoryRegion regionToExpand, long start, long size) {
        // If we are expanding backwards, we must update the base pointer.
        if (regionToExpand.getBaseAddr().rawValue() == start + size) {
            regionToExpand.setBaseAddr(WordFactory.pointer(start));
        }
        long newSize = regionToExpand.getSize() + size;
        regionToExpand.setSize(newSize);
    }
}
