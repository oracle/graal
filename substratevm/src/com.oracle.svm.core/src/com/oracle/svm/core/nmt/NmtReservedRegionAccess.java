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

import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.util.UnsignedUtils;
import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

class NmtReservedRegionAccess {
    @Fold
    static UnsignedWord getReservedRegionSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(NmtReservedRegion.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static NmtReservedRegion createReservedRegion(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        NmtReservedRegion result = NullableNativeMemory.malloc(getReservedRegionSize(), NmtCategory.NMT);
        if (result.isNonNull()) {
            result.setCommittedRegions(WordFactory.nullPointer());
            result.setCategory(category);
            result.setNext(WordFactory.nullPointer());
            result.setSize(size);
            result.setBaseAddr(baseAddr);
        }
        return result;
    }

    /**
     * This method adds a committed region to a reserved region and updates committed virtual memory
     * accounting.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L116-L167")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void addCommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegionOnStack) {
        if (reservedRegion.isNull()) {
            return;
        }

        NmtMemoryRegion prev = NmtMemoryRegionListAccess.findPrecedingRegion(reservedRegion.getCommittedRegions(), targetRegionOnStack);
        NmtMemoryRegion next = prev.isNonNull() ? prev.getNext() : reservedRegion.getCommittedRegions();

        /* Deal with overlap if target is before last region in list. */
        if (next.isNonNull()) {
            /* Do nothing if the region already exists. */
            if (NmtMemoryRegionAccess.isEqual(targetRegionOnStack, next)) {
                return;
            }
            /* Remove overlap, so we can later add the full committed region. */
            if (NmtMemoryRegionAccess.isOverlapping(next, targetRegionOnStack)) {
                removeUncommittedRegion(reservedRegion, targetRegionOnStack);
                /* Refresh prev and next pointers */
                prev = NmtMemoryRegionListAccess.findPrecedingRegion(reservedRegion.getCommittedRegions(), targetRegionOnStack);
                next = prev.isNonNull() ? prev.getNext() : reservedRegion.getCommittedRegions();
            }
        }

        /*
         * Now, the new region should not overlap any existing regions. Record the full committed
         * region.
         */
        assert NmtMemoryRegionListAccess.findContainingRegion(reservedRegion.getCommittedRegions(), targetRegionOnStack).isNull();

        if (NmtMemoryRegionAccess.tryMergeWith(prev, targetRegionOnStack)) {
            /* We were able to merge prev with target. Try to further coalesce prev with next. */
            if (NmtMemoryRegionAccess.tryMergeWith(prev, next)) {
                /*
                 * prev has been merged with target and next. Remove next from the committed region
                 * list.
                 */
                removeCommittedRegion(reservedRegion, next);
            }
        } else if (NmtMemoryRegionAccess.tryMergeWith(targetRegionOnStack, next)) {
            /* We couldn't merge with prev, but we were able to merge with next. */
        } else {
            /* We are unable to merge with existing regions. So insert an entirely new one. */
            NmtMemoryRegion newCommittedRegion = NmtMemoryRegionAccess.allocate(targetRegionOnStack.getBaseAddr(), targetRegionOnStack.getSize(), targetRegionOnStack.getCategory());
            if (newCommittedRegion.isNull()) {
                /* Allocation of the new committed region failed. Do not record the commit. */
                return;
            }

            /* Add region to committed region list maintaining sorted ordering. */
            if (prev.isNull()) {
                reservedRegion.setCommittedRegions(newCommittedRegion);
            } else {
                prev.setNext(newCommittedRegion);
            }
            newCommittedRegion.setNext(next);
        }
        NativeMemoryTracking.singleton().recordCommit(targetRegionOnStack.getSize(), targetRegionOnStack.getCategory());
    }

    /**
     * Only remove an entire existing committed region from the reserved region's list of committed
     * regions and update the list head. No NMT accounting.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void removeCommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegion) {
        NmtMemoryRegion newHead = NmtMemoryRegionListAccess.remove(reservedRegion.getCommittedRegions(), targetRegion);
        reservedRegion.setCommittedRegions(newHead);
    }

    /**
     * This method removes a specified region from a reserved region's committed list. The target
     * region may span multiple existing committed regions. The boundaries of the target region may
     * not align with the boundaries of any existing committed region. Any committed memory that is
     * uncommitted, will be accounted in NMT.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L202-L257")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void removeUncommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegion) {
        if (reservedRegion.isNull()) {
            return;
        }

        NmtMemoryRegion current = reservedRegion.getCommittedRegions();
        while (current.isNonNull()) {
            UnsignedWord currentBase = NmtMemoryRegionAccess.baseAddr(current);
            UnsignedWord currentEnd = NmtMemoryRegionAccess.endAddr(current);
            UnsignedWord targetBase = NmtMemoryRegionAccess.baseAddr(targetRegion);
            UnsignedWord targetEnd = NmtMemoryRegionAccess.endAddr(targetRegion);

            /* Exact match. */
            if (NmtMemoryRegionAccess.isEqual(current, targetRegion)) {
                removeCommittedRegion(reservedRegion, current);
                NativeMemoryTracking.singleton().recordUncommit(current.getSize(), current.getCategory());
                return;
            }

            /* Target region contains current region. */
            if (NmtMemoryRegionAccess.contains(targetRegion, current)) {
                NativeMemoryTracking.singleton().recordUncommit(current.getSize(), current.getCategory());
                NmtMemoryRegion next = current.getNext();
                removeCommittedRegion(reservedRegion, current);
                current = next;
                continue;
            }

            if (NmtMemoryRegionAccess.containsAddress(current, targetBase)) {
                /* Target region's base address is within the current region. */
                if (NmtMemoryRegionAccess.containsAddress(current, targetEnd.subtract(1))) {
                    /* Target region is contained by current region. */
                    removeRegionFromExistingCommittedRegion(current, targetRegion);
                    return;
                } else {
                    /* Only start of target region is contained in current region. */
                    UnsignedWord exclusionSize = currentEnd.subtract(targetBase);
                    NmtMemoryRegionAccess.excludeRegion(current, targetBase, exclusionSize);
                    NativeMemoryTracking.singleton().recordUncommit(exclusionSize, current.getCategory());
                }
            } else if (NmtMemoryRegionAccess.containsAddress(current, targetEnd.subtract(1))) {
                /* Only end of target region is contained in current region. */
                UnsignedWord exclusionSize = targetEnd.subtract(currentBase);
                NmtMemoryRegionAccess.excludeRegion(current, currentBase, exclusionSize);
                NativeMemoryTracking.singleton().recordUncommit(exclusionSize, current.getCategory());
                return;
            }

            current = current.getNext();
        }
    }

    /**
     * This removes an uncommitted region from within an existing committed region. Uncommitted
     * memory, will be accounted in NMT.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L169-L200")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void removeRegionFromExistingCommittedRegion(NmtMemoryRegion outerRegion, NmtMemoryRegion innerRegion) {
        assert NmtMemoryRegionAccess.isOverlapping(outerRegion, innerRegion);
        UnsignedWord outerEnd = NmtMemoryRegionAccess.endAddr(outerRegion);
        UnsignedWord innerBase = NmtMemoryRegionAccess.baseAddr(innerRegion);
        UnsignedWord innerEnd = NmtMemoryRegionAccess.endAddr(innerRegion);

        /* Try to shorten the outer region. */
        if (outerRegion.getBaseAddr() == innerRegion.getBaseAddr() || outerEnd.equal(innerEnd)) {
            NmtMemoryRegionAccess.excludeRegion(outerRegion, innerBase, innerRegion.getSize());
        } else {
            /* Hollow out and split the outer region. */

            /* Exclude the whole upper part, only keeping the lower part. */
            UnsignedWord exclusionSize = outerEnd.subtract(innerBase);
            NmtMemoryRegionAccess.excludeRegion(outerRegion, innerBase, exclusionSize);

            /*
             * Add the upper part we intend to keep as a new committed region, effectively splitting
             * the original region.
             */
            UnsignedWord highBase = innerEnd;
            UnsignedWord highSize = outerEnd.subtract(innerEnd);
            NmtMemoryRegion newCommittedRegion = NmtMemoryRegionAccess.allocate(WordFactory.pointer(highBase.rawValue()), highSize, outerRegion.getCategory());
            if (newCommittedRegion.isNull()) {
                return;
            }
            /*
             * Add region to committed region list maintaining sorted ordering. List head remains
             * the same.
             */
            NmtMemoryRegion oldNext = outerRegion.getNext();
            outerRegion.setNext(newCommittedRegion);
            newCommittedRegion.setNext(oldNext);
        }
        NativeMemoryTracking.singleton().recordUncommit(innerRegion.getSize(), outerRegion.getCategory());

    }

    /** Ensures the provided list is sorted and no regions are overlapping. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean verifyReservedList(NmtMemoryRegion listHead) {
        if (listHead.isNull()) {
            return false;
        }
        NmtMemoryRegion current = listHead;
        NmtMemoryRegion prev = WordFactory.nullPointer();
        while (current.isNonNull()) {
            if (prev.isNonNull()) {
                if (prev.getBaseAddr().rawValue() >= current.getBaseAddr().rawValue() || NmtMemoryRegionAccess.isOverlapping(prev, current)) {
                    return false;
                }
            }
            prev = current;
            current = current.getNext();
        }
        return true;
    }
}
