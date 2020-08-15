/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.util.PointerUtils.roundUp;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.util.UnsignedUtils;

public abstract class AbstractCopyingImageHeapProvider implements ImageHeapProvider {
    @Override
    public boolean guaranteesHeapPreferredAddressSpaceAlignment() {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        int imageHeapOffsetInAddressSpace = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        Word imageHeapBegin = IMAGE_HEAP_BEGIN.get();
        Word imageHeapSizeInFile = IMAGE_HEAP_END.get().subtract(imageHeapBegin);

        Pointer heap;
        Pointer allocatedMemory = WordFactory.nullPointer();
        if (reservedAddressSpace.isNull()) {
            assert imageHeapOffsetInAddressSpace == 0;
            UnsignedWord alignment = WordFactory.unsigned(Heap.getHeap().getPreferredAddressSpaceAlignment());
            allocatedMemory = VirtualMemoryProvider.get().reserve(imageHeapSizeInFile, alignment);
            if (allocatedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
            heap = allocatedMemory;
        } else {
            Word requiredReservedSize = imageHeapSizeInFile.add(imageHeapOffsetInAddressSpace);
            if (reservedSize.belowThan(requiredReservedSize)) {
                return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
            }
            heap = reservedAddressSpace.add(imageHeapOffsetInAddressSpace);
        }

        heap = VirtualMemoryProvider.get().commit(heap, imageHeapSizeInFile, VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE);
        if (heap.isNull()) {
            freeImageHeap(allocatedMemory);
            return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
        }

        int copyResult = copyMemory(imageHeapBegin, imageHeapSizeInFile, heap);
        if (copyResult != CEntryPointErrors.NO_ERROR) {
            freeImageHeap(allocatedMemory);
            return copyResult;
        }

        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        UnsignedWord writableBeginPageOffset = UnsignedUtils.roundDown(IMAGE_HEAP_WRITABLE_BEGIN.get().subtract(imageHeapBegin), pageSize);
        if (writableBeginPageOffset.aboveThan(0)) {
            if (VirtualMemoryProvider.get().protect(heap, writableBeginPageOffset, VirtualMemoryProvider.Access.READ) != 0) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }
        UnsignedWord writableEndPageOffset = UnsignedUtils.roundUp(IMAGE_HEAP_WRITABLE_END.get().subtract(imageHeapBegin), pageSize);
        if (writableEndPageOffset.belowThan(imageHeapSizeInFile)) {
            Pointer afterWritableBoundary = heap.add(writableEndPageOffset);
            Word afterWritableSize = imageHeapSizeInFile.subtract(writableEndPageOffset);
            if (VirtualMemoryProvider.get().protect(afterWritableBoundary, afterWritableSize, VirtualMemoryProvider.Access.READ) != 0) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }

        basePointer.write(heap.subtract(imageHeapOffsetInAddressSpace));
        if (endPointer.isNonNull()) {
            endPointer.write(roundUp(heap.add(imageHeapSizeInFile), pageSize));
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    protected abstract int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap);

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int freeImageHeap(PointerBase imageHeap) {
        if (imageHeap.isNonNull()) {
            assert Heap.getHeap().getImageHeapOffsetInAddressSpace() == 0;
            Word imageHeapSizeInFile = IMAGE_HEAP_END.get().subtract(IMAGE_HEAP_BEGIN.get());
            if (VirtualMemoryProvider.get().free(imageHeap, imageHeapSizeInFile) != 0) {
                return CEntryPointErrors.FREE_IMAGE_HEAP_FAILED;
            }
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
