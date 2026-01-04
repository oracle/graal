/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static com.oracle.svm.core.util.PointerUtils.roundUp;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public abstract class AbstractImageHeapProvider implements ImageHeapProvider {
    private static final CGlobalData<WordPointer> CACHED_RESERVED_IMAGE_HEAP_SIZE = CGlobalDataFactory.createWord();
    private static final CGlobalData<WordPointer> CACHED_COMMITTED_IMAGE_HEAP_SIZE = CGlobalDataFactory.createWord();

    /**
     * The number of address space bytes that are needed for all data up to the end of the image
     * heap (excluding any auxiliary images). This value is a multiple of the build-time page size.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected UnsignedWord getTotalRequiredAddressSpaceSize() {
        UnsignedWord size = getImageHeapEndOffsetInAddressSpace();
        if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
            size = size.add(getPreHeapAlignedSizeForDynamicMethodAddressResolver());
        }
        assert UnsignedUtils.isAMultiple(size, Word.unsigned(SubstrateOptions.getPageSize()));
        return size;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getImageHeapEndOffsetInAddressSpace() {
        UnsignedWord imageHeapOffset = Word.unsigned(Heap.getHeap().getImageHeapOffsetInAddressSpace());
        UnsignedWord result = imageHeapOffset.add(getImageHeapAddressSpaceSize());
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }

    @Override
    public UnsignedWord getImageHeapReservedBytes() {
        /*
         * This assumes that all image heaps are mapped directly after each other, with no other
         * data in between (except for gaps that are needed for alignment reasons).
         */
        UnsignedWord result = getImageHeapEndOffsetInAddressSpace().subtract(Heap.getHeap().getImageHeapOffsetInAddressSpace());
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }

    @Override
    public UnsignedWord getImageHeapMappedBytes() {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            return getImageHeapSizeInFile();
        }

        /* Check if value is cached. */
        Word currentValue = CACHED_COMMITTED_IMAGE_HEAP_SIZE.get().read();
        if (currentValue.notEqual(Word.zero())) {
            return currentValue;
        }

        /* Walk through the sections and add up the layer image heap sizes. */
        UnsignedWord result = Word.zero();
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        while (currentSection.isNonNull()) {
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            UnsignedWord size = getImageHeapSizeInFile(heapBegin, heapEnd);
            result = result.add(size);

            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
        CACHED_COMMITTED_IMAGE_HEAP_SIZE.get().write(result);
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static UnsignedWord getImageHeapSizeInFile(Word beginAddress, Word endAddress) {
        assert endAddress.aboveOrEqual(endAddress);
        UnsignedWord result = endAddress.subtract(beginAddress);
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getImageHeapSizeInFile() {
        VMError.guarantee(!ImageLayerBuildingSupport.buildingImageLayer());
        UnsignedWord result = getImageHeapSizeInFile(IMAGE_HEAP_BEGIN.get(), IMAGE_HEAP_END.get());
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static Pointer getImageHeapBegin(Pointer heapBase) {
        return heapBase.add(Heap.getHeap().getImageHeapOffsetInAddressSpace());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected Pointer getImageHeapEnd(Pointer heapBase) {
        return heapBase.add(getImageHeapEndOffsetInAddressSpace());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static UnsignedWord getPreHeapAlignedSizeForDynamicMethodAddressResolver() {
        UnsignedWord requiredPreHeapMemoryInBytes = DynamicMethodAddressResolutionHeapSupport.get().getRequiredPreHeapMemoryInBytes();
        /* Ensure there is enough space to properly align the heap base. */
        UnsignedWord heapBaseAlignment = Word.unsigned(Heap.getHeap().getHeapBaseAlignment());
        return roundUp((PointerBase) requiredPreHeapMemoryInBytes, heapBaseAlignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getImageHeapAddressSpaceSize() {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            return getImageHeapSizeInFile();
        }

        /* Check if value is cached. */
        Word currentValue = CACHED_RESERVED_IMAGE_HEAP_SIZE.get().read();
        if (currentValue.notEqual(Word.zero())) {
            return currentValue;
        }

        /* Walk through the sections, align the start of each image heap, and add up the sizes. */
        UnsignedWord result = Word.zero();
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        while (currentSection.isNonNull()) {
            result = UnsignedUtils.roundUp(result, Word.unsigned(Heap.getHeap().getImageHeapAlignment()));

            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            UnsignedWord size = getImageHeapSizeInFile(heapBegin, heapEnd);
            result = result.add(size);

            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
        CACHED_RESERVED_IMAGE_HEAP_SIZE.get().write(result);
        assert UnsignedUtils.isAMultiple(result, Word.unsigned(SubstrateOptions.getPageSize()));
        return result;
    }
}
