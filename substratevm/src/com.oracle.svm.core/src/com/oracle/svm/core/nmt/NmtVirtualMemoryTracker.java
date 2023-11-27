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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import org.graalvm.nativeimage.StackValue;
import com.oracle.svm.core.thread.JavaSpinLockUtils;

import jdk.internal.misc.Unsafe;

/**
 * {@link com.oracle.svm.core.nmt.NativeMemoryTracking} delegates virtual memory accounting to this
 * class. This class maintains a model of used virtual memory. The tracker maintains a sorted list
 * of reserved regions, and each reserved region maintains its own sorted list of internal committed
 * regions.
 */
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
    private boolean tornDown = false;

    private NmtReservedRegion reservedRegionListHead;

    @Platforms(Platform.HOSTED_ONLY.class)
    NmtVirtualMemoryTracker() {
    }

    /** Track a new reserved region. This region will not overlap existing reserved regions. */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackReserve(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            NmtReservedRegion newReservedRegion = NmtReservedRegionAccess.createReservedRegion(baseAddr, size, category);
            if (newReservedRegion.isNull()) {
                return;
            }
            reservedRegionListHead = (NmtReservedRegion) NmtMemoryRegionListAccess.addSorted(reservedRegionListHead, newReservedRegion);
            assert NmtReservedRegionAccess.verifyReservedList(reservedRegionListHead);
            NativeMemoryTracking.singleton().recordReserve(size, category);
        } finally {
            unlock();
        }
    }

    /**
     * Track a new committed region. This region may overlap multiple or zero existing committed
     * regions. The regions must be entirely within a single existing reserved region.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L434-L453")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackCommit(PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }
            /* Find the reserved region the committed region belongs to. */
            NmtMemoryRegion targetRegionOnStack = StackValue.get(NmtMemoryRegion.class);
            targetRegionOnStack.setSize(size);
            targetRegionOnStack.setBaseAddr(baseAddr);
            targetRegionOnStack.setCategory(category);
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findContainingRegion(reservedRegionListHead, targetRegionOnStack);

            /* Update the reserved region to include the new committed region. */
            NmtReservedRegionAccess.addCommittedRegion(reservedRegion, targetRegionOnStack);
        } finally {
            unlock();
        }
    }

    /** Uncommit a region that may overlap multiple or zero committed regions. */
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
            targetRegion.setSize(size);
            targetRegion.setBaseAddr(baseAddr);
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findContainingRegion(reservedRegionListHead, targetRegion);

            /* Uncommit the specified region from that reserved region. */
            NmtReservedRegionAccess.removeUncommittedRegion(reservedRegion, targetRegion);
        } finally {
            unlock();
        }
    }

    /**
     * Untrack a reserved region and all committed regions contained within. The entire reserved
     * region must be requested to be freed. See
     * {@link com.oracle.svm.core.os.VirtualMemoryProvider#free(PointerBase, UnsignedWord)}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+13/src/hotspot/share/nmt/virtualMemoryTracker.cpp#L491-L533")
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    void trackFree(PointerBase baseAddr) {
        lockNoTransition();
        try {
            if (tornDown) {
                return;
            }

            /* Find the reserved region, so we can access its committed list. */
            NmtReservedRegion reservedRegion = (NmtReservedRegion) NmtMemoryRegionListAccess.findRegionMatchingBase(reservedRegionListHead, baseAddr);

            /*
             * It's possible that malloc failed to initially create the reserved region.
             */
            if (reservedRegion.isNull()) {
                return;
            }
            assert baseAddr == reservedRegion.getBaseAddr();

            NmtReservedRegionAccess.removeUncommittedRegion(reservedRegion, reservedRegion);

            NativeMemoryTracking.singleton().recordFree(reservedRegion.getSize(), reservedRegion.getCategory());
            reservedRegionListHead = (NmtReservedRegion) NmtMemoryRegionListAccess.remove(reservedRegionListHead, reservedRegion);
            assert NmtReservedRegionAccess.verifyReservedList(reservedRegionListHead);

        } finally {
            unlock();
        }
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
            NmtReservedRegion current = reservedRegionListHead;

            while (current.isNonNull()) {
                NmtReservedRegion next = (NmtReservedRegion) current.getNext();
                NmtMemoryRegionListAccess.teardown(current.getCommittedRegions());
                current.setCommittedRegions(WordFactory.nullPointer());
                current = next;
            }
            NmtMemoryRegionListAccess.teardown(reservedRegionListHead);
            reservedRegionListHead = WordFactory.nullPointer();
            tornDown = true;
        } finally {
            unlock();
        }
    }
}
