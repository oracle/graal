/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.guest.staging.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.os.AbstractCopyingImageHeapProvider;
import com.oracle.svm.core.os.LayeredImageHeapSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

import org.graalvm.word.impl.Word;

/**
 * Creates image heaps on Darwin that are copy-on-write clones of the loaded image heap. Supports
 * both single-layer and layered image builds. For layered builds, each layer's image heap is
 * individually copied and patched using the platform-independent {@link LayeredImageHeapSupport}.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class DarwinImageHeapProvider extends AbstractCopyingImageHeapProvider {

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer heapBaseOut, WordPointer imageHeapEndOut) {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            /* Non-layered: delegate to the standard copying path. */
            return super.initialize(reservedAddressSpace, reservedSize, heapBaseOut, imageHeapEndOut);
        }

        /* Layered build: initialize each layer's image heap separately. */
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

        /* Update heap base and image heap end. */
        assert PointerUtils.isAMultiple(heapBase, Word.unsigned(Heap.getHeap().getHeapBaseAlignment()));
        heapBaseOut.write(heapBase);

        Pointer imageHeapEnd = getImageHeapEnd(heapBase);
        assert PointerUtils.isAMultiple(imageHeapEnd, VirtualMemoryProvider.get().getGranularity());
        imageHeapEndOut.write(imageHeapEnd);

        Pointer imageHeapStart = getImageHeapBegin(heapBase);
        int result = initializeLayeredImage(imageHeapStart, imageHeapEnd, selfReservedHeapBase);
        if (result != CEntryPointErrors.NO_ERROR) {
            freeImageHeap(selfReservedHeapBase);
        }
        return result;
    }

    /**
     * Initialize a layered image by copying each layer's image heap using vm_copy and then
     * applying cross-layer patches.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private int initializeLayeredImage(Pointer imageHeapStart, Pointer imageHeapEnd, Pointer selfReservedHeapBase) {
        UnsignedWord imageHeapAlignment = Word.unsigned(Heap.getHeap().getImageHeapAlignment());
        assert PointerUtils.isAMultiple(imageHeapStart, imageHeapAlignment);

        /* Apply cross-layer patches (code pointer relocation, heap ref patches, field updates). */
        LayeredImageHeapSupport.patchLayeredImageHeap();

        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer currentHeapStart = imageHeapStart;

        while (currentSection.isNonNull()) {
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            Word heapWritableBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_BEGIN));
            Word heapWritableEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_END));

            /* Each layer's image heap starts at an aligned offset. */
            currentHeapStart = PointerUtils.roundUp(currentHeapStart, imageHeapAlignment);

            UnsignedWord imageHeapSize = getImageHeapSizeInFile(heapBegin, heapEnd);

            /* Commit memory and copy this layer's image heap via vm_copy. */
            int result = commitAndCopyMemory(heapBegin, imageHeapSize, currentHeapStart);
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return result;
            }

            /* Protect read-only parts before the writable section. */
            UnsignedWord writableBeginPageOffset = UnsignedUtils.roundDown(heapWritableBegin.subtract(heapBegin), pageSize);
            if (writableBeginPageOffset.aboveThan(0)) {
                if (VirtualMemoryProvider.get().protect(currentHeapStart, writableBeginPageOffset, Access.READ) != 0) {
                    freeImageHeap(selfReservedHeapBase);
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }

            /* Protect read-only parts after the writable section. */
            UnsignedWord writableEndPageOffset = UnsignedUtils.roundUp(heapWritableEnd.subtract(heapBegin), pageSize);
            if (writableEndPageOffset.belowThan(imageHeapSize)) {
                Pointer afterWritableBoundary = currentHeapStart.add(writableEndPageOffset);
                UnsignedWord afterWritableSize = imageHeapSize.subtract(writableEndPageOffset);
                if (VirtualMemoryProvider.get().protect(afterWritableBoundary, afterWritableSize, Access.READ) != 0) {
                    freeImageHeap(selfReservedHeapBase);
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }

            currentHeapStart = currentHeapStart.add(imageHeapSize);

            /* Advance to the next layer. */
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
        assert imageHeapEnd.equal(currentHeapStart);
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        // Note: virtual memory must have been committed for vm_copy()
        if (DarwinVirtualMemory.vm_copy(DarwinVirtualMemory.mach_task_self(), loadedImageHeap, imageHeapSize, newImageHeap) != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
