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
import static com.oracle.svm.core.posix.linux.ProcFSSupport.findMapping;
import static com.oracle.svm.core.util.PointerUtils.roundUp;
import static com.oracle.svm.core.util.UnsignedUtils.isAMultiple;
import static org.graalvm.word.WordFactory.signed;

import java.util.concurrent.ThreadLocalRandom;

import org.graalvm.compiler.word.Word;
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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.os.AbstractImageHeapProvider;
import com.oracle.svm.core.os.CopyingImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;

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

    private static final SignedWord FIRST_ISOLATE_FD = signed(-1);
    private static final SignedWord UNASSIGNED_FD = signed(-2);
    private static final SignedWord CANNOT_OPEN_FD = signed(-3);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_FD = CGlobalDataFactory.createWord(FIRST_ISOLATE_FD);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_HEAP_OFFSET = CGlobalDataFactory.createWord();

    private static final int MAX_PATHLEN = 4096;

    private static final CopyingImageHeapProvider fallbackCopyingProvider = new CopyingImageHeapProvider();

    @Override
    public boolean guaranteesHeapPreferredAddressSpaceAlignment() {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        // If we are the first isolate, we might be able to use the existing image heap (see below)
        SignedWord fd = CACHED_IMAGE_FD.get().read();
        boolean firstIsolate = false;
        if (fd.equal(FIRST_ISOLATE_FD)) {
            SignedWord previous = ((Pointer) CACHED_IMAGE_FD.get()).compareAndSwapWord(0, FIRST_ISOLATE_FD, UNASSIGNED_FD, LocationIdentity.ANY_LOCATION);
            firstIsolate = previous.equal(FIRST_ISOLATE_FD);
            fd = firstIsolate ? UNASSIGNED_FD : previous;
        }

        /*
         * If we haven't already, find and open the image file. Even if we are the first isolate,
         * this is necessary because if we fail, we need to leave the loaded image heap in pristine
         * condition so we can use it to spawn isolates by copying it.
         *
         * We cache the file descriptor and the determined offset in the file for subsequent isolate
         * initializations. We intentionally allow racing in this step to avoid stalling threads.
         */
        if (fd.equal(UNASSIGNED_FD) || firstIsolate) {
            int opened = openImageFile();
            /*
             * Pointer cas operations are volatile accesses and prevent code reorderings.
             */
            SignedWord previous = ((Pointer) CACHED_IMAGE_FD.get()).compareAndSwapWord(0, fd, signed(opened), LocationIdentity.ANY_LOCATION);
            if (previous.equal(fd)) {
                fd = signed(opened);
            } else {
                if (opened >= 0) {
                    Unistd.NoTransitions.close(opened);
                }
                fd = previous;
            }
        }

        // If we cannot find or open the image file, fall back to copy it from memory.
        if (fd.equal(CANNOT_OPEN_FD)) {
            return fallbackCopyingProvider.initialize(reservedAddressSpace, reservedSize, basePointer, endPointer);
        }

        // If we are the first isolate and can use the existing image heap, do it.
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        Word imageHeapBegin = IMAGE_HEAP_BEGIN.get();
        UnsignedWord imageHeapSizeInFile = getImageHeapSizeInFile();
        int imageHeapOffsetInAddressSpace = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        UnsignedWord alignment = WordFactory.unsigned(Heap.getHeap().getPreferredAddressSpaceAlignment());
        if (firstIsolate && reservedAddressSpace.isNull() && PointerUtils.isAMultiple(imageHeapBegin, alignment) && imageHeapOffsetInAddressSpace == 0) {
            // Mark the whole image heap as read only.
            if (VirtualMemoryProvider.get().protect(imageHeapBegin, imageHeapSizeInFile, Access.READ) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }

            // Unprotect writable pages.
            Pointer writableBegin = IMAGE_HEAP_WRITABLE_BEGIN.get();
            UnsignedWord writableSize = IMAGE_HEAP_WRITABLE_END.get().subtract(writableBegin);
            if (VirtualMemoryProvider.get().protect(writableBegin, writableSize, Access.READ | Access.WRITE) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }

            // Protect the null region.
            int nullRegionSize = Heap.getHeap().getImageHeapNullRegionSize();
            if (nullRegionSize > 0) {
                if (VirtualMemoryProvider.get().protect(imageHeapBegin, WordFactory.unsigned(nullRegionSize), Access.NONE) != 0) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }

            basePointer.write(imageHeapBegin);
            if (endPointer.isNonNull()) {
                endPointer.write(IMAGE_HEAP_END.get());
            }
            return CEntryPointErrors.NO_ERROR;
        }

        // Reserve an address space for the image heap if necessary.
        UnsignedWord imageHeapAddressSpaceSize = getImageHeapAddressSpaceSize();
        Pointer heapBase;
        Pointer allocatedMemory = WordFactory.nullPointer();
        if (reservedAddressSpace.isNull()) {
            heapBase = allocatedMemory = VirtualMemoryProvider.get().reserve(imageHeapAddressSpaceSize, alignment, false);
            if (allocatedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
        } else {
            if (reservedSize.belowThan(imageHeapAddressSpaceSize)) {
                return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
            }
            heapBase = reservedAddressSpace;
        }

        // Create memory mappings from the image file.
        UnsignedWord fileOffset = CACHED_IMAGE_HEAP_OFFSET.get().read();
        Pointer imageHeap = heapBase.add(imageHeapOffsetInAddressSpace);
        imageHeap = VirtualMemoryProvider.get().mapFile(imageHeap, imageHeapSizeInFile, fd, fileOffset, Access.READ);
        if (imageHeap.isNull()) {
            freeImageHeap(allocatedMemory);
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        Pointer relocPointer = IMAGE_HEAP_A_RELOCATABLE_POINTER.get();
        ComparableWord relocatedValue = relocPointer.readWord(0);
        ComparableWord mappedValue = imageHeap.readWord(relocPointer.subtract(imageHeapBegin));
        if (relocatedValue.notEqual(mappedValue)) {
            /*
             * Addresses were relocated by dynamic linker, so copy them, but first remap the pages
             * to avoid swapping them in from disk.
             */
            Pointer relocsBegin = imageHeap.add(IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(imageHeapBegin));
            UnsignedWord relocsSize = IMAGE_HEAP_RELOCATABLE_END.get().subtract(IMAGE_HEAP_RELOCATABLE_BEGIN.get());
            if (!isAMultiple(relocsSize, pageSize)) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
            Pointer committedRelocsBegin = VirtualMemoryProvider.get().commit(relocsBegin, relocsSize, Access.READ | Access.WRITE);
            if (committedRelocsBegin.isNull() || committedRelocsBegin != relocsBegin) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
            LibC.memcpy(relocsBegin, IMAGE_HEAP_RELOCATABLE_BEGIN.get(), relocsSize);
            if (VirtualMemoryProvider.get().protect(relocsBegin, relocsSize, Access.READ) != 0) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }

        // Unprotect writable pages.
        Pointer writableBegin = imageHeap.add(IMAGE_HEAP_WRITABLE_BEGIN.get().subtract(imageHeapBegin));
        UnsignedWord writableSize = IMAGE_HEAP_WRITABLE_END.get().subtract(IMAGE_HEAP_WRITABLE_BEGIN.get());
        if (VirtualMemoryProvider.get().protect(writableBegin, writableSize, Access.READ | Access.WRITE) != 0) {
            freeImageHeap(allocatedMemory);
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }

        basePointer.write(heapBase);
        if (endPointer.isNonNull()) {
            endPointer.write(roundUp(imageHeap.add(imageHeapSizeInFile), pageSize));
        }
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * Locate our image file, containing the image heap. Unfortunately we must open it by its path.
     *
     * NOTE: we look for the relocatables partition of the linker-mapped heap because it always
     * stays mapped, while the rest of the linker-mapped heap can be unmapped after tearing down the
     * first isolate. We do not use /proc/self/exe because it breaks with some tools like Valgrind.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int openImageFile() {
        final int failfd = (int) CANNOT_OPEN_FD.rawValue();
        int mapfd = Fcntl.NoTransitions.open(PROC_SELF_MAPS.get(), Fcntl.O_RDONLY(), 0);
        if (mapfd == -1) {
            return failfd;
        }
        final int bufferSize = MAX_PATHLEN;
        CCharPointer buffer = StackValue.get(bufferSize);

        // Find the offset of the magic word in the image file. We cannot reliably compute it from
        // the image heap offset below because it might be in a different file segment.
        Pointer magicAddress = MAGIC.get();
        int wordSize = ConfigurationValues.getTarget().wordSize;
        WordPointer magicMappingStart = StackValue.get(WordPointer.class);
        WordPointer magicMappingFileOffset = StackValue.get(WordPointer.class);
        boolean found = findMapping(mapfd, buffer, bufferSize, magicAddress, magicAddress.add(wordSize), magicMappingStart, magicMappingFileOffset, false);
        if (!found) {
            Unistd.NoTransitions.close(mapfd);
            return failfd;
        }
        Word magicFileOffset = (Word) magicAddress.subtract(magicMappingStart.read()).add(magicMappingFileOffset.read());

        if (Unistd.NoTransitions.lseek(mapfd, signed(0), Unistd.SEEK_SET()).notEqual(0)) {
            Unistd.NoTransitions.close(mapfd);
            return failfd;
        }
        // The relocatables partition might stretch over two adjacent mappings due to permission
        // differences, so only locate the mapping for the first page of relocatables
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        WordPointer relocsMappingStart = StackValue.get(WordPointer.class);
        WordPointer relocsMappingFileOffset = StackValue.get(WordPointer.class);
        found = findMapping(mapfd, buffer, bufferSize, IMAGE_HEAP_RELOCATABLE_BEGIN.get(),
                        IMAGE_HEAP_RELOCATABLE_BEGIN.get().add(pageSize), relocsMappingStart, relocsMappingFileOffset, true);
        Unistd.NoTransitions.close(mapfd);
        if (!found) {
            return failfd;
        }
        int opened = Fcntl.NoTransitions.open(buffer, Fcntl.O_RDONLY(), 0);
        if (opened < 0) {
            return failfd;
        }

        // Compare the magic word in memory with the magic word read from the file
        if (Unistd.NoTransitions.lseek(opened, magicFileOffset, Unistd.SEEK_SET()).notEqual(magicFileOffset)) {
            Unistd.NoTransitions.close(opened);
            return failfd;
        }
        if (PosixUtils.readBytes(opened, buffer, wordSize, 0) != wordSize) {
            Unistd.NoTransitions.close(opened);
            return failfd;
        }
        Word fileMagic = ((WordPointer) buffer).read();
        if (fileMagic.notEqual(magicAddress.readWord(0))) {
            return failfd; // magic number mismatch
        }

        Word imageHeapRelocsOffset = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get());
        Word imageHeapOffset = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(relocsMappingStart.read()).subtract(imageHeapRelocsOffset);
        UnsignedWord fileOffset = imageHeapOffset.add(relocsMappingFileOffset.read());
        CACHED_IMAGE_HEAP_OFFSET.get().write(fileOffset);
        return opened;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int freeImageHeap(PointerBase heapBase) {
        if (heapBase.isNonNull()) {
            if (heapBase.equal(IMAGE_HEAP_BEGIN.get())) {
                assert Heap.getHeap().getImageHeapOffsetInAddressSpace() == 0;
                /*
                 * This isolate uses the image heap mapped by the loader. We shouldn't unmap it in
                 * case we are a dynamic library and dlclose() is called on us and tries to access
                 * the pages. However, the heap need not stay resident, so we remap it as an
                 * anonymous mapping. For future isolates, we still need the read-only heap
                 * partition with relocatable addresses that were adjusted by the loader, so we
                 * leave it. (We have already checked that that partition is page-aligned)
                 */
                assert Heap.getHeap().getImageHeapOffsetInAddressSpace() == 0;
                UnsignedWord beforeRelocSize = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract((Pointer) heapBase);
                Pointer newHeapBase = VirtualMemoryProvider.get().commit(heapBase, beforeRelocSize, Access.READ);

                if (newHeapBase.isNull() || newHeapBase.notEqual(heapBase)) {
                    return CEntryPointErrors.MAP_HEAP_FAILED;
                }

                Word relocEnd = IMAGE_HEAP_RELOCATABLE_END.get();
                Word afterRelocSize = IMAGE_HEAP_END.get().subtract(relocEnd);
                Pointer newRelocEnd = VirtualMemoryProvider.get().commit(relocEnd, afterRelocSize, Access.READ);

                if (newRelocEnd.isNull() || newRelocEnd.notEqual(relocEnd)) {
                    return CEntryPointErrors.MAP_HEAP_FAILED;
                }
            } else {
                if (VirtualMemoryProvider.get().free(heapBase, getImageHeapAddressSpaceSize()) != 0) {
                    return CEntryPointErrors.MAP_HEAP_FAILED;
                }
            }
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
