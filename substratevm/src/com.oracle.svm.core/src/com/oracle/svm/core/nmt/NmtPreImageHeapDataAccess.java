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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.util.UnsignedUtils;
import org.graalvm.nativeimage.c.struct.SizeOf;

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * This class is used to access {@link com.oracle.svm.core.nmt.NmtPreImageHeapData}. It must use
 * untracked native memory for all its operations since it is meant to be used before the image heap
 * has been mapped.
 */
public class NmtPreImageHeapDataAccess {
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static NmtPreImageHeapData create() {
        NmtPreImageHeapData data = UntrackedNullableNativeMemory
                        .malloc(UnsignedUtils.roundUp(SizeOf.unsigned(NmtPreImageHeapData.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize)));
        if (data.isNonNull()) {
            data.setCommittedListHead(WordFactory.nullPointer());
            data.setReservedListHead(WordFactory.nullPointer());
        }
        return data;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void enqueueReserve(NmtPreImageHeapData data, PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        assert data.isNonNull();
        NmtMemoryRegion head = data.getReservedListHead();
        NmtMemoryRegion newRgn = UntrackedNullableNativeMemory.malloc(NmtMemoryRegionAccess.getSize());
        NmtMemoryRegionAccess.initialize(newRgn, baseAddr, size, category);
        newRgn.setNext(head);
        data.setReservedListHead(newRgn);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void enqueueCommit(NmtPreImageHeapData data, PointerBase baseAddr, UnsignedWord size, NmtCategory category) {
        assert data.isNonNull();
        NmtMemoryRegion head = data.getCommittedListHead();
        NmtMemoryRegion newRgn = UntrackedNullableNativeMemory.malloc(NmtMemoryRegionAccess.getSize());
        NmtMemoryRegionAccess.initialize(newRgn, baseAddr, size, category);
        newRgn.setNext(head);
        data.setCommittedListHead(newRgn);
    }

    /**
     * This method should be called after the image heap is ready. This method frees all allocated
     * memory and tracks all queued reserves and commits.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void trackAndTeardown(NmtPreImageHeapData data) {
        /* Account for reserves first since committed regions are dependent on them. */
        NmtMemoryRegion current = data.getReservedListHead();
        while (current.isNonNull()) {
            NmtMemoryRegion next = current.getNext();
            NativeMemoryTracking.singleton().trackReserve(current.getBaseAddr(), current.getSize(), current.getCategory());
            UntrackedNullableNativeMemory.free(current);
            current = next;
        }

        current = data.getCommittedListHead();

        while (current.isNonNull()) {
            NmtMemoryRegion next = current.getNext();
            NativeMemoryTracking.singleton().trackCommit(current.getBaseAddr(), current.getSize(), current.getCategory());
            UntrackedNullableNativeMemory.free(current);
            current = next;
        }

        UntrackedNullableNativeMemory.free(data);
    }
}
