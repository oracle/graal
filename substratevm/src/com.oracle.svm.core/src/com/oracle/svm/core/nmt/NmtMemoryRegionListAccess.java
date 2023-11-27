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
import org.graalvm.word.WordFactory;
import com.oracle.svm.core.memory.NullableNativeMemory;

/** Static methods for operating on memory region lists. */
public class NmtMemoryRegionListAccess {
    /**
     * Adds a region to the provided list, returns the new list head. Maintains sorted nature of
     * list.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static NmtMemoryRegion addSorted(NmtMemoryRegion listHead, NmtMemoryRegion newRegion) {
        // If adding an invalid region, do nothing.
        if (newRegion.isNull()) {
            return listHead;
        }

        NmtMemoryRegion current = listHead;
        NmtMemoryRegion prev = WordFactory.nullPointer();
        while (current.isNonNull()) {
            if (newRegion.getBaseAddr().rawValue() < current.getBaseAddr().rawValue()) {
                assert !NmtMemoryRegionAccess.isOverlapping(newRegion, current);
                newRegion.setNext(current);
                if (prev.isNonNull()) {
                    // New region is in middle of the list
                    assert !NmtMemoryRegionAccess.isOverlapping(newRegion, prev);
                    prev.setNext(newRegion);
                    return listHead;
                }
                // New region is the new head of the list
                return newRegion;
            }
            prev = current;
            current = current.getNext();
        }

        // New region is the new tail of the list
        if (prev.isNonNull()) {
            assert !NmtMemoryRegionAccess.isEqual(newRegion, prev);
            assert !NmtMemoryRegionAccess.isOverlapping(newRegion, prev);
            prev.setNext(newRegion);
            return listHead;
        }

        // List was previously empty
        return newRegion;
    }

    /**
     * Removes a region from the provided list. Assumes the list is sorted (since it relies on
     * findPrecedingRegion). Returns the new list head.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static NmtMemoryRegion remove(NmtMemoryRegion listHead, NmtMemoryRegion targetRegion) {
        assert listHead.isNonNull();
        NmtMemoryRegion prev = findPrecedingRegion(listHead, targetRegion);

        /* Handle removing the head. */
        if (prev.isNull()) {
            assert NmtMemoryRegionAccess.isEqual(listHead, targetRegion);
            NmtMemoryRegion newHead = listHead.getNext();
            NullableNativeMemory.free(listHead);
            return newHead;
        }

        /* We are removing a node that is not head. */
        NmtMemoryRegion current = prev.getNext();
        assert NmtMemoryRegionAccess.isEqual(current, targetRegion);
        assert !NmtMemoryRegionAccess.isEqual(listHead, targetRegion);
        prev.setNext(current.getNext());
        NullableNativeMemory.free(current);
        return listHead;
    }

    /**
     * Finds the region that contains or matches the target region. Or nullPointer if unsuccessful.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static NmtMemoryRegion findContainingRegion(NmtMemoryRegion listHead, NmtMemoryRegion targetRegion) {
        NmtMemoryRegion current = listHead;
        while (current.isNonNull()) {
            if (NmtMemoryRegionAccess.contains(current, targetRegion)) {
                return current;
            }
            current = current.getNext();
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static NmtMemoryRegion findRegionMatchingBase(NmtMemoryRegion listHead, org.graalvm.word.PointerBase baseAddr) {
        NmtMemoryRegion current = listHead;
        while (current.isNonNull()) {
            if (current.getBaseAddr().rawValue() == baseAddr.rawValue()) {
                return current;
            }
            current = current.getNext();
        }
        return WordFactory.nullPointer();
    }

    /** Assumes the list is sorted. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static NmtMemoryRegion findPrecedingRegion(NmtMemoryRegion listHead, NmtMemoryRegion targetRegion) {
        NmtMemoryRegion current = listHead;
        NmtMemoryRegion preceding = WordFactory.nullPointer();
        while (current.isNonNull()) {
            long currentEnd = current.getBaseAddr().rawValue() + current.getSize().rawValue();
            if (currentEnd > targetRegion.getBaseAddr().rawValue()) {
                return preceding;
            }
            preceding = current;
            current = current.getNext();
        }
        // tail of the list precedes the target region
        return preceding;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void teardown(NmtMemoryRegion listHead) {
        NmtMemoryRegion current = listHead;
        while (current.isNonNull()) {
            NmtMemoryRegion next = current.getNext();
            NullableNativeMemory.free(current);
            current = next;
        }
    }
}
