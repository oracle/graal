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
import com.oracle.svm.core.util.UnsignedUtils;
import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import org.graalvm.nativeimage.StackValue;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.memory.NullableNativeMemory;

import jdk.internal.misc.Unsafe;

public class NmtVirtualMemoryTracker {
    private static final Unsafe U = Unsafe.getUnsafe();
    /*
     * Can't use VmMutex because it tracks owners and this class may be used during isolate
     * creation. Cannot reuse lock from NativeMemoryTracking because this class may malloc and the
     * lock is not reentrant.
     */
    private static final long VMEM_LOCK_OFFSET = U.objectFieldOffset(NmtVirtualMemoryTracker.class, "vMemLock");
    @SuppressWarnings("unused") private volatile int vMemLock;
    /*
     * It may be possible that virtual memory is freed after the teardown is run. We should avoid
     * accounting in that case.
     */
    private volatile boolean tornDown = false;

    private NmtVirtualMemorySnapshot virtualMemorySnapshot;
    /** The list of reserved regions is unsorted. */
    private volatile NmtReservedRegion reservedRegionListHead;

    @Platforms(Platform.HOSTED_ONLY.class)
    NmtVirtualMemoryTracker(NmtVirtualMemorySnapshot virtualMemorySnapshot) {
        this.virtualMemorySnapshot = virtualMemorySnapshot;
    }

    @Fold
    static UnsignedWord getReservedRegionSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(NmtReservedRegion.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Fold
    static UnsignedWord getCommittedRegionSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(NmtMemoryRegion.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static NmtReservedRegion createReservedRegion(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        NmtReservedRegion result = NullableNativeMemory.malloc(getReservedRegionSize(), NmtCategory.NMT);
        if (result.isNonNull()) {
            result.setCommittedRegions(WordFactory.nullPointer());
            result.setCategory(category.ordinal());
            result.setNext(WordFactory.nullPointer());
            result.setSize(size.rawValue());
            result.setBaseAddr(baseAddr);
        }
        return result;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static NmtMemoryRegion createCommittedRegion(PointerBase baseAddr, long size, int category) {
        NmtMemoryRegion result = NullableNativeMemory.malloc(getCommittedRegionSize(), NmtCategory.NMT);
        if (result.isNonNull()) {
            result.setCategory(category);
            result.setNext(WordFactory.nullPointer());
            result.setSize(size);
            result.setBaseAddr(baseAddr);
        }
        return result;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackReserve(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            virtualMemorySnapshot.getInfoByCategory(category).trackReserved(size);
            virtualMemorySnapshot.getTotalInfo().trackReserved(size);
            reservedRegionListHead = (NmtReservedRegion) NmtMemoryRegionListAccess.addSorted(reservedRegionListHead, createReservedRegion(baseAddr, size, category));
            assert NmtMemoryRegionListAccess.verifyReservedList(reservedRegionListHead);
        } finally {
            unlock();
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L434-L453")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackCommit(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            // Find the reserved region the committed region belongs to.
            NmtMemoryRegion targetRegion = StackValue.get(NmtMemoryRegion.class);
            targetRegion.setSize(size.rawValue());
            targetRegion.setBaseAddr(baseAddr);
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findContainingRegion(reservedRegionListHead, targetRegion);
            assert reservedRegion.isNonNull();
            if (addCommittedRegion(reservedRegion, targetRegion, baseAddr, size, category)) {
                trackCommit0(size, category);
            }
        } finally {
            unlock();
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L116-L167")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean addCommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegion, PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        NmtMemoryRegion prev = NmtMemoryRegionListAccess.findPrecedingRegion(reservedRegion.getCommittedRegions(), targetRegion);
        NmtMemoryRegion next = prev.isNonNull() ? prev.getNext() : reservedRegion.getCommittedRegions();

        /* Deal with overlap if target is before last region in list. */
        if (next.isNonNull()) {
            /* Do nothing if the region already exists. */
            if (NmtMemoryRegionAccess.isEqual(targetRegion, next)) {
                return false;
            }
            /* Remove overlap so we can later add the full committed region. */
            if (NmtMemoryRegionAccess.isOverlapping(next, targetRegion)) {
                removeUncommittedRegion(reservedRegion, targetRegion);
                /* Refresh prev and next pointers */
                prev = NmtMemoryRegionListAccess.findPrecedingRegion(reservedRegion.getCommittedRegions(), targetRegion);
                next = prev.isNonNull() ? prev.getNext() : reservedRegion.getCommittedRegions();
            }
        }

        /*
         * Now the new region should not overlap any existing regions. Record the full committed
         * region.
         */
        assert NmtMemoryRegionListAccess.findContainingRegion(reservedRegion.getCommittedRegions(), targetRegion).isNull();

        /* Try to coalesce prev, target, and next. Update committed list regions. */
        if (NmtMemoryRegionAccess.isAdjacent(prev, targetRegion)) {
            NmtMemoryRegionAccess.expandRegion(prev, baseAddr.rawValue(), size.rawValue());
            /* Try to further coalesce prev with next. */
            if (NmtMemoryRegionAccess.isAdjacent(prev, next)) {
                NmtMemoryRegionAccess.expandRegion(prev, next.getBaseAddr().rawValue(), next.getSize());
                /* prev has absorbed target and next. Remove next from the committed region list. */
                removeCommittedRegion(reservedRegion, next);
            }
            return true;
        }

        /* We can't coalesce with prev, but maybe we can coalesce with next. */
        if (NmtMemoryRegionAccess.isAdjacent(targetRegion, next)) {
            NmtMemoryRegionAccess.expandRegion(next, baseAddr.rawValue(), size.rawValue());
            return true;
        }

        /* Unable to simply expand/coalesce existing regions. So insert a new one. */
        NmtMemoryRegion newCommittedRegion = createCommittedRegion(baseAddr, size.rawValue(), category.ordinal());
        /* Add region to committed region list maintaining sorted ordering. */
        if (prev.isNull()) {
            /* Set new head. We may be replacing the old head, or the list may be empty. */
            reservedRegion.setCommittedRegions(newCommittedRegion);
        } else {
            prev.setNext(newCommittedRegion);
        }
        newCommittedRegion.setNext(next);
        return true;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void trackCommit0(UnsignedWord size, NmtCategory category) {
        virtualMemorySnapshot.getInfoByCategory(category).trackCommitted(size);
        virtualMemorySnapshot.getTotalInfo().trackCommitted(size);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L455-L469")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackUncommit(PointerBase baseAddr, UnsignedWord size) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            /* Find the reserved region we are uncommitting from. */
            NmtMemoryRegion targetRegion = StackValue.get(NmtMemoryRegion.class);
            targetRegion.setSize(size.rawValue());
            targetRegion.setBaseAddr(baseAddr);
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findContainingRegion(reservedRegionListHead, targetRegion);
            /* Uncommit the specified region from that reserved region. */
            removeUncommittedRegion(reservedRegion, targetRegion);
        } finally {
            unlock();
        }
    }

    /**
     * This method removes a region from a reserved region's committed list. The target region may
     * span multiple existing committed regions. The boundaries of the target region may not align
     * with the boundaries of any existing committed region. Any committed memory that is
     * uncommitted, will be recorded.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L202-L257")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void removeUncommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegionOnStack) {
        NmtMemoryRegion current = reservedRegion.getCommittedRegions();
        while (current.isNonNull()) {
            long currentStart = current.getBaseAddr().rawValue();
            long currentEnd = currentStart + current.getSize();
            long targetStart = targetRegionOnStack.getBaseAddr().rawValue();
            long targetEnd = targetStart + targetRegionOnStack.getSize();

            if (NmtMemoryRegionAccess.isEqual(current, targetRegionOnStack)) {
                /* Exact match. */
                removeCommittedRegion(reservedRegion, current);
                trackUncommit0(current.getSize(), current.getCategory());
                return;
            } else if (NmtMemoryRegionAccess.contains(targetRegionOnStack, current)) {
                /* Target region contains current region. */
                NmtMemoryRegion next = current.getNext();
                removeCommittedRegion(reservedRegion, current);
                trackUncommit0(current.getSize(), current.getCategory());
                current = next;
                continue;
            } else if (NmtMemoryRegionAccess.contains(current, targetRegionOnStack)) {
                /* Target region is contained by current region. */
                removeRegionFromExistingCommittedRegion(current, targetRegionOnStack);
                trackUncommit0(targetRegionOnStack.getSize(), current.getCategory());
                return;
            } else if (NmtMemoryRegionAccess.containsAddress(current, targetStart)) {
                /* Only start of target region is contained in current region. */
                long exclusionSize = currentEnd - targetStart;
                NmtMemoryRegionAccess.excludeRegion(current, targetStart, exclusionSize);
                trackUncommit0(exclusionSize, current.getCategory());
            } else if (NmtMemoryRegionAccess.containsAddress(current, targetEnd - 1)) {
                /* Only end of target region is contained in current region. */
                long exclusionSize = targetEnd - currentStart;
                NmtMemoryRegionAccess.excludeRegion(current, currentStart, exclusionSize);
                trackUncommit0(exclusionSize, current.getCategory());
                return;
            }
            current = current.getNext();
        }
    }

    /**
     * Only remove an entire existing committed region from the list and update the list head. No
     * NMT accounting.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void removeCommittedRegion(NmtReservedRegion reservedRegion, NmtMemoryRegion targetRegionOnStack) {
        NmtMemoryRegion newHead = NmtMemoryRegionListAccess.remove(reservedRegion.getCommittedRegions(), targetRegionOnStack);
        reservedRegion.setCommittedRegions(newHead);
    }

    /**
     * This removes an uncommitted region from within an existing committed region. No NMT
     * accounting.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L169-L200")
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void removeRegionFromExistingCommittedRegion(NmtMemoryRegion outerRegion, NmtMemoryRegion innerRegionOnStack) {
        assert NmtMemoryRegionAccess.isOverlapping(outerRegion, innerRegionOnStack);
        long outerEnd = outerRegion.getBaseAddr().rawValue() + outerRegion.getSize();
        long innerStart = innerRegionOnStack.getBaseAddr().rawValue();
        long innerEnd = innerStart + innerRegionOnStack.getSize();

        /* Try to shorten the outer region. */
        if (outerRegion.getBaseAddr() == innerRegionOnStack.getBaseAddr() || outerEnd == innerEnd) {
            NmtMemoryRegionAccess.excludeRegion(outerRegion, innerStart, innerRegionOnStack.getSize());
            return;
        }
        /* Hollow out and split the outer region. */

        /* Exclude the whole upper part, only keeping the lower part. */
        long exclusionSize = outerEnd - innerStart;
        NmtMemoryRegionAccess.excludeRegion(outerRegion, innerStart, exclusionSize);

        /*
         * Add the upper part we intend to keep as a new committed region, effectively splitting the
         * original region.
         */
        long highBase = innerEnd;
        long highSize = outerEnd - innerEnd;
        NmtMemoryRegion newCommittedRegion = createCommittedRegion(WordFactory.pointer(highBase), highSize, outerRegion.getCategory());
        /*
         * Add region to committed region list maintaining sorted ordering. List head remains the
         * same.
         */
        NmtMemoryRegion oldNext = outerRegion.getNext();
        outerRegion.setNext(newCommittedRegion);
        newCommittedRegion.setNext(oldNext);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void trackUncommit0(long size, int category) {
        virtualMemorySnapshot.getInfoByCategory(category).trackUncommit(size);
        virtualMemorySnapshot.getTotalInfo().trackUncommit(size);
    }

    /**
     * It is guranteed that exactly the entire reserved region is freed. See
     * {@link com.oracle.svm.core.os.VirtualMemoryProvider#free(PointerBase, UnsignedWord)}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L491-L533")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackFree(PointerBase baseAddr, UnsignedWord size) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            NmtMemoryRegion targetRegion = StackValue.get(NmtMemoryRegion.class);
            targetRegion.setSize(size.rawValue());
            targetRegion.setBaseAddr(baseAddr);

            /* Find the reserved region so we can access its committed list. */
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findContainingRegion(reservedRegionListHead, targetRegion);

            assert reservedRegion.isNonNull();
            assert NmtMemoryRegionAccess.isEqual(reservedRegion, targetRegion);

            removeUncommittedRegion(reservedRegion, targetRegion);

            trackFree0(size.rawValue(), reservedRegion.getCategory());
            reservedRegionListHead = (NmtReservedRegion) NmtMemoryRegionListAccess.remove(reservedRegionListHead, targetRegion);
            assert NmtMemoryRegionListAccess.verifyReservedList(reservedRegionListHead);

        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void trackFree0(long size, int category) {
        virtualMemorySnapshot.getInfoByCategory(category).trackFree(size);
        virtualMemorySnapshot.getTotalInfo().trackFree(size);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void lockNoTransition() {
        JavaSpinLockUtils.lockNoTransition(this, VMEM_LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void unlock() {
        JavaSpinLockUtils.unlock(this, VMEM_LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void teardown() {
        lockNoTransition();
        try {
            NmtMemoryRegion current = reservedRegionListHead;

            while (current.isNonNull()) {
                NmtMemoryRegion next = current.getNext();
                NmtMemoryRegionListAccess.teardown(current);
                current = next;
            }
            NmtMemoryRegionListAccess.teardown(reservedRegionListHead);
            tornDown = true;
        } finally {
            unlock();
        }
    }
}
