/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_A_RELOCATABLE_POINTER;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_END;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_ANY_RELOCATABLE_POINTER;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static com.oracle.svm.core.posix.linux.ProcFSSupport.findMapping;
import static com.oracle.svm.core.util.PointerUtils.roundDown;
import static com.oracle.svm.core.util.UnsignedUtils.isAMultiple;
import static com.oracle.svm.core.util.UnsignedUtils.roundUp;
import static org.graalvm.word.WordFactory.signed;

import java.util.concurrent.ThreadLocalRandom;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.os.AbstractImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * An optimal image heap provider for Linux which creates isolate image heaps that retain the
 * copy-on-write, lazy loading and reclamation semantics provided by the original heap's backing
 * resource.
 *
 * This is accomplished by discovering the backing executable or shared object file the kernel has
 * mmapped to the original heap image virtual address, as well as the location in the file storing
 * the original heap. A new memory map is created to a new virtual range pointing to this same
 * location. This allows the kernel to share the same physical pages between multiple heaps that
 * have not been modified, as well as lazily load them only when needed.
 *
 * The implementation avoids dirtying the pages of the original, and only referencing what is
 * strictly required.
 */
public class LinuxImageHeapProvider extends AbstractImageHeapProvider {
    /** Magic value to verify that a located image file matches our loaded image. */
    public static final CGlobalData<Pointer> MAGIC = CGlobalDataFactory.createWord(WordFactory.<Word> signed(ThreadLocalRandom.current().nextLong()));

    private static final CGlobalData<CCharPointer> PROC_SELF_MAPS = CGlobalDataFactory.createCString("/proc/self/maps");

    private static final SignedWord UNASSIGNED_FD = signed(-1);
    private static final SignedWord CANNOT_OPEN_FD = signed(-2);

    private static final CGlobalData<WordPointer> CACHED_IMAGE_FDS = CGlobalDataFactory.createWord(UNASSIGNED_FD);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_HEAP_OFFSETS = CGlobalDataFactory.createWord();

    private static final int MAX_PATHLEN = 4096;

    /**
     * Used for caching heap address space size when using layered images. Within layered images
     * calculating this value requires iterating through multiple sections.
     */
    static final CGlobalData<WordPointer> CACHED_LAYERED_IMAGE_HEAP_ADDRESS_SPACE_SIZE = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getLayeredImageHeapAddressSpaceSize() {
        // check if value is cached
        Word currentValue = CACHED_LAYERED_IMAGE_HEAP_ADDRESS_SPACE_SIZE.get().read();
        if (currentValue.isNonNull()) {
            return currentValue;
        }
        int imageHeapOffset = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        assert imageHeapOffset >= 0;
        UnsignedWord size = WordFactory.unsigned(imageHeapOffset);
        UnsignedWord granularity = VirtualMemoryProvider.get().getGranularity();
        assert isAMultiple(size, granularity);

        /*
         * Walk through the sections and add up the layer image heap sizes.
         */

        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        while (currentSection.isNonNull()) {
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            size = size.add(getImageHeapSizeInFile(heapBegin, heapEnd));
            size = roundUp(size, granularity);
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }

        // cache the value
        CACHED_LAYERED_IMAGE_HEAP_ADDRESS_SPACE_SIZE.get().write(size);
        return size;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UnsignedWord getImageHeapAddressSpaceSize() {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return getLayeredImageHeapAddressSpaceSize();
        } else {
            return super.getImageHeapAddressSpaceSize();
        }
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int initializeLayeredImage(Pointer firstHeapStart, Pointer selfReservedHeapBase, UnsignedWord initialRemainingSize, WordPointer endPointer) {
        int result = -1;
        UnsignedWord remainingSize = initialRemainingSize;

        int layerCount = 0;
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer currentHeapStart = firstHeapStart;
        while (currentSection.isNonNull()) {
            var cachedFDPointer = ImageLayerSection.getCachedImageFDs().get().addressOf(layerCount);
            var cachedOffsetsPointer = ImageLayerSection.getCachedImageHeapOffsets().get().addressOf(layerCount);
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            Word heapRelocBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_RELOCATABLE_BEGIN));
            Word heapRelocEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_RELOCATABLE_END));
            Word heapAnyRelocPointer = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_ANY_RELOCATABLE_POINTER));
            Word heapWritableBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_BEGIN));
            Word heapWritableEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_END));

            result = initializeImageHeap(currentHeapStart, remainingSize, endPointer,
                            cachedFDPointer, cachedOffsetsPointer, MAGIC.get(),
                            heapBegin, heapEnd,
                            heapRelocBegin, heapAnyRelocPointer, heapRelocEnd,
                            heapWritableBegin, heapWritableEnd);
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return result;
            }
            Pointer newHeapStart = endPointer.read(); // aligned
            remainingSize = remainingSize.subtract(newHeapStart.subtract(currentHeapStart));
            currentHeapStart = newHeapStart;

            // read the next layer
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
            layerCount++;
        }
        return result;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        Pointer selfReservedMemory = WordFactory.nullPointer();
        UnsignedWord requiredSize = getTotalRequiredAddressSpaceSize();
        if (reservedAddressSpace.isNull()) {
            UnsignedWord alignment = WordFactory.unsigned(Heap.getHeap().getPreferredAddressSpaceAlignment());
            selfReservedMemory = VirtualMemoryProvider.get().reserve(requiredSize, alignment, false);
            if (selfReservedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
        } else if (reservedSize.belowThan(requiredSize)) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }
        UnsignedWord remainingSize = requiredSize;

        Pointer heapBase;
        Pointer selfReservedHeapBase;
        if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
            UnsignedWord preHeapRequiredBytes = getPreHeapAlignedSizeForDynamicMethodAddressResolver();
            if (selfReservedMemory.isNonNull()) {
                selfReservedHeapBase = selfReservedMemory.add(preHeapRequiredBytes);
                heapBase = selfReservedHeapBase;
            } else {
                heapBase = reservedAddressSpace.add(preHeapRequiredBytes);
                selfReservedHeapBase = WordFactory.nullPointer();
            }
            remainingSize = remainingSize.subtract(preHeapRequiredBytes);

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

        int imageHeapOffsetInAddressSpace = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        basePointer.write(heapBase);
        Pointer imageHeapStart = heapBase.add(imageHeapOffsetInAddressSpace);
        remainingSize = remainingSize.subtract(imageHeapOffsetInAddressSpace);
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            int result = initializeImageHeap(imageHeapStart, remainingSize, endPointer,
                            CACHED_IMAGE_FDS.get(), CACHED_IMAGE_HEAP_OFFSETS.get(), MAGIC.get(),
                            IMAGE_HEAP_BEGIN.get(), IMAGE_HEAP_END.get(),
                            IMAGE_HEAP_RELOCATABLE_BEGIN.get(), IMAGE_HEAP_A_RELOCATABLE_POINTER.get(), IMAGE_HEAP_RELOCATABLE_END.get(),
                            IMAGE_HEAP_WRITABLE_BEGIN.get(), IMAGE_HEAP_WRITABLE_END.get());
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
            }
            return result;
        } else {
            return initializeLayeredImage(imageHeapStart, selfReservedHeapBase, remainingSize, endPointer);
        }
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int initializeImageHeap(Pointer imageHeap, UnsignedWord reservedSize, WordPointer endPointer, WordPointer cachedFd, WordPointer cachedOffsetInFile,
                    Pointer magicAddress, Word heapBeginSym, Word heapEndSym, Word heapRelocsSym, Pointer heapAnyRelocPointer, Word heapRelocsEndSym, Word heapWritableSym, Word heapWritableEndSym) {
        assert heapBeginSym.belowOrEqual(heapWritableSym) && heapWritableSym.belowOrEqual(heapWritableEndSym) && heapWritableEndSym.belowOrEqual(heapEndSym);
        assert heapBeginSym.belowOrEqual(heapRelocsSym) && heapRelocsSym.belowOrEqual(heapRelocsEndSym) && heapRelocsEndSym.belowOrEqual(heapEndSym);
        assert heapAnyRelocPointer.isNull() || (heapRelocsSym.belowOrEqual(heapAnyRelocPointer) && heapAnyRelocPointer.belowThan(heapRelocsEndSym));

        SignedWord fd = cachedFd.read();

        /*
         * Find and open the image file. We cache the file descriptor and the determined offset in
         * the file for subsequent isolate initializations. We intentionally allow racing in this
         * step to avoid stalling threads.
         */
        if (fd.equal(UNASSIGNED_FD)) {
            int opened = openImageFile(heapBeginSym, magicAddress, cachedOffsetInFile);
            SignedWord previous = ((Pointer) cachedFd).compareAndSwapWord(0, fd, signed(opened), LocationIdentity.ANY_LOCATION);
            if (previous.equal(fd)) {
                fd = signed(opened);
            } else {
                if (opened >= 0) {
                    Unistd.NoTransitions.close(opened);
                }
                fd = previous;
            }
        }

        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        UnsignedWord imageHeapSize = getImageHeapSizeInFile(heapBeginSym, heapEndSym);
        assert reservedSize.aboveOrEqual(imageHeapSize);
        if (endPointer.isNonNull()) {
            endPointer.write(roundUp(imageHeap.add(imageHeapSize), pageSize));
        }

        /*
         * If we cannot find or open the image file, fall back to copy it from memory (the image
         * heap must be in pristine condition for that).
         */
        if (fd.equal(CANNOT_OPEN_FD)) {
            return initializeImageHeapByCopying(imageHeap, imageHeapSize, pageSize, heapBeginSym, heapWritableSym, heapWritableEndSym);
        }

        // Create memory mappings from the image file.
        UnsignedWord fileOffset = cachedOffsetInFile.read();
        Pointer mappedImageHeap = VirtualMemoryProvider.get().mapFile(imageHeap, imageHeapSize, fd, fileOffset, Access.READ);
        if (mappedImageHeap.isNull() || mappedImageHeap != imageHeap) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        if (heapAnyRelocPointer.isNonNull()) {
            ComparableWord relocatedValue = heapAnyRelocPointer.readWord(0);
            ComparableWord mappedValue = imageHeap.readWord(heapAnyRelocPointer.subtract(heapBeginSym));
            if (relocatedValue.notEqual(mappedValue)) {
                Pointer linkedRelocsBoundary = roundDown(heapRelocsSym, pageSize);
                UnsignedWord relocsAlignedSize = roundUp(heapRelocsEndSym.subtract(linkedRelocsBoundary), pageSize);
                Pointer relocsBoundary = imageHeap.add(linkedRelocsBoundary.subtract(heapBeginSym));
                /*
                 * Addresses were relocated by the dynamic linker, so copy them, but first remap the
                 * pages to avoid swapping them in from disk. We need to round to page boundaries,
                 * and so we copy some extra data.
                 *
                 * NOTE: while objects with relocations are considered read-only, some of them might
                 * be part of a chunk with writable objects, in which case the chunk header must
                 * also be writable, and all the chunk's pages will be unprotected below.
                 */
                Pointer committedRelocsBegin = VirtualMemoryProvider.get().commit(relocsBoundary, relocsAlignedSize, Access.READ | Access.WRITE);
                if (committedRelocsBegin.isNull() || committedRelocsBegin != relocsBoundary) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
                LibC.memcpy(relocsBoundary, linkedRelocsBoundary, relocsAlignedSize);
                if (VirtualMemoryProvider.get().protect(relocsBoundary, relocsAlignedSize, Access.READ) != 0) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }
        }

        /*
         * Unprotect writable pages.
         *
         * The last page might be shared with the subsequent read-only huge objects partition, in
         * which case we make some of its data writable, which we consider acceptable.
         */
        Pointer writableBegin = imageHeap.add(heapWritableSym.subtract(heapBeginSym));
        UnsignedWord writableSize = heapWritableEndSym.subtract(heapWritableSym);
        UnsignedWord alignedWritableSize = roundUp(writableSize, pageSize);
        if (VirtualMemoryProvider.get().protect(writableBegin, alignedWritableSize, Access.READ | Access.WRITE) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }

        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int initializeImageHeapByCopying(Pointer imageHeap, UnsignedWord imageHeapSize, UnsignedWord pageSize, Word heapBeginSym, Word heapWritableSym, Word heapWritableEndSym) {
        Pointer committedBegin = VirtualMemoryProvider.get().commit(imageHeap, imageHeapSize, Access.READ | Access.WRITE);
        if (committedBegin.isNull()) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        LibC.memcpy(imageHeap, heapBeginSym, imageHeapSize);

        UnsignedWord readOnlyBytesAtBegin = heapWritableSym.subtract(heapBeginSym);
        readOnlyBytesAtBegin = UnsignedUtils.roundDown(readOnlyBytesAtBegin, pageSize);
        if (readOnlyBytesAtBegin.aboveThan(0) && VirtualMemoryProvider.get().protect(imageHeap, readOnlyBytesAtBegin, Access.READ) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }
        Pointer writableEnd = imageHeap.add(heapWritableEndSym.subtract(heapBeginSym));
        writableEnd = PointerUtils.roundUp(writableEnd, pageSize);
        UnsignedWord readOnlyBytesAtEnd = imageHeap.add(imageHeapSize).subtract(writableEnd);
        readOnlyBytesAtEnd = roundUp(readOnlyBytesAtEnd, pageSize);
        if (readOnlyBytesAtEnd.aboveThan(0) && VirtualMemoryProvider.get().protect(writableEnd, readOnlyBytesAtEnd, Access.READ) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * Locate our image file, containing the image heap. Unfortunately we must open it by its path.
     * We do not use /proc/self/exe because it breaks with some tools like Valgrind.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int openImageFile(Word heapBeginSym, Pointer magicAddress, WordPointer cachedImageHeapOffsetInFile) {
        final int failfd = (int) CANNOT_OPEN_FD.rawValue();
        int mapfd = Fcntl.NoTransitions.open(PROC_SELF_MAPS.get(), Fcntl.O_RDONLY(), 0);
        if (mapfd == -1) {
            return failfd;
        }
        final int bufferSize = MAX_PATHLEN;
        CCharPointer buffer = StackValue.get(bufferSize);

        WordPointer imageHeapMappingStart = StackValue.get(WordPointer.class);
        WordPointer imageHeapMappingFileOffset = StackValue.get(WordPointer.class);
        boolean found = findMapping(mapfd, buffer, bufferSize, heapBeginSym, heapBeginSym.add(1), imageHeapMappingStart, imageHeapMappingFileOffset, true);
        if (!found) {
            Unistd.NoTransitions.close(mapfd);
            return failfd;
        }
        int opened = Fcntl.NoTransitions.open(buffer, Fcntl.O_RDONLY(), 0);
        if (opened < 0) {
            Unistd.NoTransitions.close(mapfd);
            return failfd;
        }

        boolean valid = magicAddress.isNull() || checkImageFileMagic(mapfd, opened, buffer, bufferSize, magicAddress);
        Unistd.NoTransitions.close(mapfd);
        if (!valid) {
            Unistd.NoTransitions.close(opened);
            return failfd;
        }

        Word imageHeapOffsetInMapping = heapBeginSym.subtract(imageHeapMappingStart.read());
        UnsignedWord imageHeapOffsetInFile = imageHeapOffsetInMapping.add(imageHeapMappingFileOffset.read());
        cachedImageHeapOffsetInFile.write(imageHeapOffsetInFile);
        return opened;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static boolean checkImageFileMagic(int mapfd, int imagefd, CCharPointer buffer, int bufferSize, Pointer magicAddress) {
        if (Unistd.NoTransitions.lseek(mapfd, signed(0), Unistd.SEEK_SET()).notEqual(0)) {
            return false;
        }

        // Find the offset of the magic word in the image file. We cannot reliably compute it
        // from the image heap offset below because it might be in a different file segment.
        int wordSize = ConfigurationValues.getTarget().wordSize;
        WordPointer magicMappingStart = StackValue.get(WordPointer.class);
        WordPointer magicMappingFileOffset = StackValue.get(WordPointer.class);
        boolean found = findMapping(mapfd, buffer, bufferSize, magicAddress, magicAddress.add(wordSize), magicMappingStart, magicMappingFileOffset, false);
        if (!found) {
            return false;
        }
        Word magicFileOffset = (Word) magicAddress.subtract(magicMappingStart.read()).add(magicMappingFileOffset.read());

        // Compare the magic word in memory with the magic word read from the file
        if (Unistd.NoTransitions.lseek(imagefd, magicFileOffset, Unistd.SEEK_SET()).notEqual(magicFileOffset)) {
            return false;
        }

        if (PosixUtils.readBytes(imagefd, buffer, wordSize, 0) != wordSize) {
            return false;
        }
        Word fileMagic = ((WordPointer) buffer).read();
        return fileMagic.equal(magicAddress.readWord(0));
    }

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int freeImageHeap(PointerBase heapBase) {
        if (heapBase.isNull()) { // no memory allocated
            return CEntryPointErrors.NO_ERROR;
        }
        VMError.guarantee(heapBase.notEqual(IMAGE_HEAP_BEGIN.get()), "reusing the image heap is no longer supported");

        Pointer addressSpaceStart = (Pointer) heapBase;
        if (DynamicMethodAddressResolutionHeapSupport.isEnabled()) {
            addressSpaceStart = addressSpaceStart.subtract(getPreHeapAlignedSizeForDynamicMethodAddressResolver());
        }
        if (VirtualMemoryProvider.get().free(addressSpaceStart, getTotalRequiredAddressSpaceSize()) != 0) {
            return CEntryPointErrors.FREE_IMAGE_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
