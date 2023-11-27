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
    /** Adds to list, returns list head. Maintains sorted nature of list. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NmtMemoryRegion addSorted(NmtMemoryRegion listHead, NmtMemoryRegion newRegion) {
        // If adding an invalid region, do nothing.
        if (newRegion.isNull()) {
            return listHead;
        }

        NmtMemoryRegion current = listHead;
        NmtMemoryRegion prev = WordFactory.nullPointer();
        while (current.isNonNull()) {
            if (newRegion.getBaseAddr().rawValue() < current.getBaseAddr().rawValue()) {
// if (NmtMemoryRegionAccess.isOverlapping(newRegion, current)){
// fail42(newRegion, current);
// }
                assert !NmtMemoryRegionAccess.isOverlapping(newRegion, current);
                newRegion.setNext(current);
                if (prev.isNonNull()) {
                    // New region is in middle of the list
// if (NmtMemoryRegionAccess.isOverlapping(newRegion, prev)){
// fail42(newRegion, prev);
// }
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
// if (NmtMemoryRegionAccess.isOverlapping(newRegion, prev)){
// fail42(newRegion, prev);
// }
            assert !NmtMemoryRegionAccess.isOverlapping(newRegion, prev);
            prev.setNext(newRegion);
            return listHead;
        }

        // List was previously empty
        return newRegion;
    }

// @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
// private static void fail42(NmtMemoryRegion newRegion, NmtMemoryRegion prev){
// long size = NmtMemoryRegionAccess.getOverlapSize(newRegion, prev);
// int flag = prev.getCategory();
// int flagMult = flag*flag;
// long mult = size*size;
// assert mult <0;
// assert flag<100;
// }

    /** Assumes list is sorted (since it relies on findPrecedingRegion). Returns new head. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NmtMemoryRegion remove(NmtMemoryRegion listHead, NmtMemoryRegion targetRegionOnStack) {
        assert listHead.isNonNull(); // There must be at least one node to remove
        NmtMemoryRegion prev = findPrecedingRegion(listHead, targetRegionOnStack);

        // Are we removing the head?
        if (prev.isNull()) {
            assert NmtMemoryRegionAccess.isEqual(listHead, targetRegionOnStack);
            NmtMemoryRegion newHead = listHead.getNext();
            NullableNativeMemory.free(listHead);
            return newHead;
        }

        // We are removing a node that is not head.
        NmtMemoryRegion current = prev.getNext();
// assert NmtMemoryRegionAccess.isEqual(current, targetRegionOnStack);
        assert current.getBaseAddr() == targetRegionOnStack.getBaseAddr();
        assert !NmtMemoryRegionAccess.isEqual(listHead, targetRegionOnStack);
        prev.setNext(current.getNext());
        NullableNativeMemory.free(current);
        return listHead;
    }

    /**
     * Finds the region allocated on the heap that contains or matches the target region. Or nullPtr
     * if unsuccessful. This is useful for searching for a reserved region that a committed region
     * may belong to.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static NmtMemoryRegion findContainingRegion(NmtMemoryRegion listHead, NmtMemoryRegion targetRegionOnStack) {
        NmtMemoryRegion current = listHead;
        while (current.isNonNull()) {
            if (NmtMemoryRegionAccess.contains(current, targetRegionOnStack)) {
                return current;
            }
            current = current.getNext();
        }
        return WordFactory.nullPointer();
    }

    /** Assumes list is sorted. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static NmtMemoryRegion findPrecedingRegion(NmtMemoryRegion listHead, NmtMemoryRegion targetRegionOnStack) {
        NmtMemoryRegion current = listHead;
        NmtMemoryRegion preceding = WordFactory.nullPointer();
        while (current.isNonNull()) {
            long currentEnd = current.getBaseAddr().rawValue() + current.getSize();
            if (currentEnd > targetRegionOnStack.getBaseAddr().rawValue()) { // *** should this be
                                                                             // >=??? [NO. Since end
                                                                             // is non-inclusive.
                                                                             // draw it out. This
                                                                             // fails!]
                return preceding;
            }
            preceding = current;
            current = current.getNext();
        }
        // tail of the list preceeds the target region
        return preceding;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void teardown(NmtMemoryRegion listHead) {
        NmtMemoryRegion current = listHead;
        while (current.isNonNull()) {
            NmtMemoryRegion next = current.getNext();
            NullableNativeMemory.free(current);
            current = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean verifyReservedList(NmtMemoryRegion listHead) {
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
