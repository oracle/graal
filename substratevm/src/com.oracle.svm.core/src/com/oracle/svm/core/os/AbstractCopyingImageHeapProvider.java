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
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;

public abstract class AbstractCopyingImageHeapProvider extends AbstractImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer heapBaseOut, WordPointer imageHeapEndOut) {
        Pointer selfReservedMemory = Word.nullPointer();
        UnsignedWord requiredSize = getTotalRequiredAddressSpaceSize();
        if (reservedAddressSpace.isNull()) {
            UnsignedWord alignment = Word.unsigned(Heap.getHeap().getHeapBaseAlignment());
            selfReservedMemory = VirtualMemoryProvider.get().reserve(requiredSize, alignment, false);
            if (selfReservedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
        } else if (reservedSize.belowThan(requiredSize)) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }

        Pointer heapBase;
        Pointer selfReservedHeapBase;
        if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
            UnsignedWord preHeapRequiredBytes = getPreHeapAlignedSizeForDynamicMethodAddressResolver();
            if (selfReservedMemory.isNonNull()) {
                selfReservedHeapBase = selfReservedMemory.add(preHeapRequiredBytes);
                heapBase = selfReservedHeapBase;
            } else {
                heapBase = reservedAddressSpace.add(preHeapRequiredBytes);
                selfReservedHeapBase = Word.nullPointer();
            }

            int error = DynamicMethodAddressResolutionHeapSupport.get().initialize();
            if (error != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return error;
            }

            error = DynamicMethodAddressResolutionHeapSupport.get().install(heapBase);
            if (error != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return error;
            }
        } else {
            heapBase = selfReservedMemory.isNonNull() ? selfReservedMemory : reservedAddressSpace;
            selfReservedHeapBase = selfReservedMemory;
        }

        Pointer imageHeap = getImageHeapBegin(heapBase);
        Pointer imageHeapEnd = getImageHeapEnd(heapBase);
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        assert PointerUtils.isAMultiple(imageHeapEnd, pageSize);

        int result;
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            result = initializeLayeredImageByCopying(imageHeap, imageHeapEnd, pageSize);
        } else {
            result = initializeImageHeapByCopying(imageHeap, getImageHeapSizeInFile(), pageSize, IMAGE_HEAP_BEGIN.get(), IMAGE_HEAP_WRITABLE_BEGIN.get(), IMAGE_HEAP_WRITABLE_END.get());
        }
        if (result != CEntryPointErrors.NO_ERROR) {
            freeImageHeap(selfReservedHeapBase);
            return result;
        }

        /* Update heap base and image heap end. */
        assert PointerUtils.isAMultiple(heapBase, Word.unsigned(Heap.getHeap().getHeapBaseAlignment()));
        heapBaseOut.write(heapBase);

        imageHeapEndOut.write(imageHeapEnd);

        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private int initializeLayeredImageByCopying(Pointer imageHeapStart, Pointer imageHeapEnd, UnsignedWord pageSize) {
        UnsignedWord imageHeapAlignment = Word.unsigned(Heap.getHeap().getImageHeapAlignment());
        assert PointerUtils.isAMultiple(imageHeapStart, imageHeapAlignment);

        LayeredImageHeapSupport.patchLayeredImageHeap();

        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer currentHeapStart = imageHeapStart;

        while (currentSection.isNonNull()) {
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            Word heapWritableBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_BEGIN));
            Word heapWritableEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_END));
            assert heapBegin.belowOrEqual(heapWritableBegin) && heapWritableBegin.belowOrEqual(heapWritableEnd) && heapWritableEnd.belowOrEqual(heapEnd);

            currentHeapStart = PointerUtils.roundUp(currentHeapStart, imageHeapAlignment);

            UnsignedWord imageHeapSize = getImageHeapSizeInFile(heapBegin, heapEnd);
            int result = initializeImageHeapByCopying(currentHeapStart, imageHeapSize, pageSize, heapBegin, heapWritableBegin, heapWritableEnd);
            if (result != CEntryPointErrors.NO_ERROR) {
                return result;
            }

            currentHeapStart = currentHeapStart.add(imageHeapSize);
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
        assert imageHeapEnd.equal(currentHeapStart);
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private int initializeImageHeapByCopying(Pointer imageHeap, UnsignedWord imageHeapSize, UnsignedWord pageSize, Word heapBeginSym, Word heapWritableSym, Word heapWritableEndSym) {
        int result = commitAndCopyMemory(heapBeginSym, imageHeapSize, imageHeap);
        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }

        UnsignedWord readOnlyBytesAtBegin = UnsignedUtils.roundDown(heapWritableSym.subtract(heapBeginSym), pageSize);
        if (readOnlyBytesAtBegin.aboveThan(0) && VirtualMemoryProvider.get().protect(imageHeap, readOnlyBytesAtBegin, Access.READ) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }

        Pointer writableEnd = imageHeap.add(heapWritableEndSym.subtract(heapBeginSym));
        writableEnd = PointerUtils.roundUp(writableEnd, pageSize);
        UnsignedWord readOnlyBytesAtEnd = imageHeap.add(imageHeapSize).subtract(writableEnd);
        readOnlyBytesAtEnd = UnsignedUtils.roundUp(readOnlyBytesAtEnd, pageSize);
        if (readOnlyBytesAtEnd.aboveThan(0) && VirtualMemoryProvider.get().protect(writableEnd, readOnlyBytesAtEnd, Access.READ) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int commitAndCopyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        Pointer actualNewImageHeap = VirtualMemoryProvider.get().commit(newImageHeap, imageHeapSize, Access.READ | Access.WRITE);
        if (actualNewImageHeap.isNull() || actualNewImageHeap.notEqual(newImageHeap)) {
            return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
        }
        return copyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    protected abstract int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap);

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int freeImageHeap(PointerBase heapBase) {
        if (heapBase.isNonNull()) {
            Pointer addressSpaceStart = (Pointer) heapBase;
            if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
                addressSpaceStart = addressSpaceStart.subtract(getPreHeapAlignedSizeForDynamicMethodAddressResolver());
            }

            if (VirtualMemoryProvider.get().free(addressSpaceStart, getTotalRequiredAddressSpaceSize()) != 0) {
                return CEntryPointErrors.FREE_IMAGE_HEAP_FAILED;
            }
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
