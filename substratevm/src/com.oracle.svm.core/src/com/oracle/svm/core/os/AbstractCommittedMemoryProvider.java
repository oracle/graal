/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_END;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_END;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.util.UnsignedUtils;

public abstract class AbstractCommittedMemoryProvider implements CommittedMemoryProvider {
    @Fold
    @Override
    public boolean guaranteesHeapPreferredAddressSpaceAlignment() {
        return SubstrateOptions.SpawnIsolates.getValue() && ImageHeapProvider.get().guaranteesHeapPreferredAddressSpaceAlignment();
    }

    @Uninterruptible(reason = "Still being initialized.")
    protected static int protectSingleIsolateImageHeap() {
        assert !SubstrateOptions.SpawnIsolates.getValue() : "Must be handled by ImageHeapProvider when SpawnIsolates is enabled";
        assert Heap.getHeap().getImageHeapNullRegionSize() == 0 : "A null region only makes sense with a heap base.";
        Pointer heapBegin = IMAGE_HEAP_BEGIN.get();
        if (Heap.getHeap().getImageHeapOffsetInAddressSpace() != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        if (!SubstrateOptions.ForceNoROSectionRelocations.getValue()) {
            /*
             * Set strict read-only and read+write permissions for the image heap (the entire image
             * heap should already be read-only, but the linker/loader can place it in a segment
             * that has the executable bit set unnecessarily)
             *
             * If ForceNoROSectionRelocations is set, however, the image heap is writable and should
             * remain so.
             */
            UnsignedWord heapSize = IMAGE_HEAP_END.get().subtract(heapBegin);
            if (VirtualMemoryProvider.get().protect(heapBegin, heapSize, VirtualMemoryProvider.Access.READ) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
            Pointer writableBegin = IMAGE_HEAP_WRITABLE_BEGIN.get();
            UnsignedWord writableSize = IMAGE_HEAP_WRITABLE_END.get().subtract(writableBegin);
            if (VirtualMemoryProvider.get().protect(writableBegin, writableSize, VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }

        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    public Pointer allocateExecutableMemory(UnsignedWord nbytes, UnsignedWord alignment) {
        return allocate(nbytes, alignment, true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected Pointer allocate(UnsignedWord size, UnsignedWord alignment, boolean executable) {
        Pointer reserved = WordFactory.nullPointer();
        if (!UnsignedUtils.isAMultiple(getGranularity(), alignment)) {
            reserved = VirtualMemoryProvider.get().reserve(size, alignment, executable);
            if (reserved.isNull()) {
                return nullPointer();
            }
        }
        int access = VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE;
        if (executable) {
            access |= VirtualMemoryProvider.Access.FUTURE_EXECUTE;
        }
        Pointer committed = VirtualMemoryProvider.get().commit(reserved, size, access);
        if (committed.isNull()) {
            if (reserved.isNonNull()) {
                VirtualMemoryProvider.get().free(reserved, size);
            }
            return nullPointer();
        }
        assert reserved.isNull() || reserved.equal(committed);
        tracker.track(size);
        return committed;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void freeExecutableMemory(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment) {
        free(start, nbytes);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void free(PointerBase start, UnsignedWord nbytes) {
        if (VirtualMemoryProvider.get().free(start, nbytes) == 0) {
            tracker.untrack(nbytes);
        }
    }

    private final VirtualMemoryTracker tracker = new VirtualMemoryTracker();

    public static class VirtualMemoryTracker {

        private UnsignedWord totalAllocated;

        public VirtualMemoryTracker() {
            this.totalAllocated = WordFactory.zero();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void track(UnsignedWord size) {
            totalAllocated = totalAllocated.add(size);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void untrack(UnsignedWord size) {
            totalAllocated = totalAllocated.subtract(size);
        }
    }
}
