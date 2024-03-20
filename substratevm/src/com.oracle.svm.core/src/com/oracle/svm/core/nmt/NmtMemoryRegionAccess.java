/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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
import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.word.WordFactory;
import com.oracle.svm.core.jdk.UninterruptibleUtils.Math;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.UnsignedUtils;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

class NmtMemoryRegionAccess {

    @Fold
    static UnsignedWord getSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(NmtMemoryRegion.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static NmtMemoryRegion allocate(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        NmtMemoryRegion result = NullableNativeMemory.malloc(getSize(), NmtCategory.NMT);
        initialize(result, baseAddr, size, category);
        return result;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void initialize(NmtMemoryRegion memoryRegion, PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        if (memoryRegion.isNonNull()) {
            memoryRegion.setCategory(category);
            memoryRegion.setNext(WordFactory.nullPointer());
            memoryRegion.setSize(size);
            memoryRegion.setBaseAddr(baseAddr);
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isEqual(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        if (r1.isNonNull() && r2.isNonNull() && r1.getSize().equal(r2.getSize()) && baseAddr(r1).equal(baseAddr(r2))) {
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean contains(NmtMemoryRegion outer, NmtMemoryRegion inner) {
        if (containsAddress(outer, baseAddr(inner)) && containsAddress(outer, endAddr(inner))) {
            return true;
        }
        return false;
    }

    /** "Contains" also includes the outer bounds. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean containsAddress(NmtMemoryRegion region, UnsignedWord addr) {
        if (baseAddr(region).belowOrEqual(addr) && addr.belowOrEqual(endAddr(region))) {
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isOverlapping(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        if (getOverlapSize(r1, r2) > 0) {
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static long getOverlapSize(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        long region1Base = baseAddr(r1).rawValue();
        long region1End = endAddr(r1).rawValue();
        long region2Base = baseAddr(r2).rawValue();
        long region2End = endAddr(r2).rawValue();
        return Math.min(region1End, region2End) - Math.max(region1Base, region2Base);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void excludeRegion(NmtMemoryRegion outerRegion, UnsignedWord start, UnsignedWord size) {
        UnsignedWord newSize = outerRegion.getSize().subtract(size);
        if (baseAddr(outerRegion).equal(start)) {
            outerRegion.setBaseAddr(WordFactory.pointer(start.add(size).rawValue()));
        }
        outerRegion.setSize(newSize);
    }

    /** r1 is the region to potentially expand, r2 is the expansion. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean tryMergeWith(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        if (isAdjacent(r1, r2)) {
            merge(r1, r2);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isAdjacent(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        if (r1.isNull() || r2.isNull()) {
            return false;
        }
        return endAddr(r1).equal(baseAddr(r2)) || endAddr(r2).equal(baseAddr(r1));
    }

    /** r1 is the region to expand, r2 is the expansion. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void merge(NmtMemoryRegion r1, NmtMemoryRegion r2) {
        // If we are expanding backwards, we must update the base pointer.
        if (baseAddr(r1).equal(endAddr(r2))) {
            r1.setBaseAddr(r2.getBaseAddr());
        }
        UnsignedWord newSize = r1.getSize().add(r2.getSize());
        r1.setSize(newSize);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord baseAddr(NmtMemoryRegion region) {
        return WordFactory.unsigned(region.getBaseAddr().rawValue());
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static UnsignedWord endAddr(NmtMemoryRegion region) {
        return baseAddr(region).add(region.getSize());
    }
}
