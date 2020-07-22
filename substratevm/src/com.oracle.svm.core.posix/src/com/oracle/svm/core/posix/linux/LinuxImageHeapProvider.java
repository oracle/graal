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
import static com.oracle.svm.core.posix.headers.LibC.memcpy;
import static com.oracle.svm.core.posix.linux.ProcFSSupport.findMapping;
import static com.oracle.svm.core.util.PointerUtils.roundUp;
import static com.oracle.svm.core.util.UnsignedUtils.isAMultiple;
import static org.graalvm.word.WordFactory.signed;

import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;

import jdk.vm.ci.code.MemoryBarriers;

@AutomaticFeature
class LinuxImageHeapProviderFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(ImageHeapProvider.class)) {
            ImageSingletons.add(ImageHeapProvider.class, new LinuxImageHeapProvider());
        }
    }
}

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
public class LinuxImageHeapProvider implements ImageHeapProvider {
    private static final CGlobalData<CCharPointer> PROC_SELF_MAPS = CGlobalDataFactory.createCString("/proc/self/maps");

    private static final SignedWord FIRST_ISOLATE_FD = signed(-2);
    private static final SignedWord UNASSIGNED_FD = signed(-1);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_FD = CGlobalDataFactory.createWord(FIRST_ISOLATE_FD);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_HEAP_OFFSET = CGlobalDataFactory.createWord();

    private static final int MAX_PATHLEN = 4096;

    @Override
    public boolean guaranteesHeapPreferredAddressSpaceAlignment() {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        int imageHeapOffsetInAddressSpace = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        UnsignedWord alignment = WordFactory.unsigned(Heap.getHeap().getPreferredAddressSpaceAlignment());
        Pointer imageHeapBegin = IMAGE_HEAP_BEGIN.get();
        UnsignedWord imageHeapSizeInFile = ((Pointer) IMAGE_HEAP_END.get()).subtract(imageHeapBegin);
        UnsignedWord requiredReservedSize = imageHeapSizeInFile.add(imageHeapOffsetInAddressSpace);
        if (reservedAddressSpace.isNonNull() && reservedSize.belowThan(requiredReservedSize)) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }

        /*
         * If we don't need a contiguous address space, we use the image heap loaded by the loader
         * for the first isolate. For creating isolates after that (or if we need a contiguous
         * address space), we create copy-on-write mappings from our image file. We cache the file
         * descriptor and determined offset in the file for subsequent isolate initializations. To
         * avoid stalling threads, we intentionally allow for racing during first-time
         * initialization.
         */
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        SignedWord fd = CACHED_IMAGE_FD.get().read();
        if (reservedAddressSpace.isNull() && FIRST_ISOLATE_FD.equal(fd)) {
            assert imageHeapOffsetInAddressSpace == 0 : "the image heap that was loaded by the loader does not support a heap address space offset";
            SignedWord previous = ((Pointer) CACHED_IMAGE_FD.get()).compareAndSwapWord(0, FIRST_ISOLATE_FD, UNASSIGNED_FD, LocationIdentity.ANY_LOCATION);
            if (FIRST_ISOLATE_FD.equal(previous) && PointerUtils.isAMultiple(imageHeapBegin, alignment)) {
                // We are the first isolate, so use the existing heap if it has the required
                // alignment (the loader might not align to more than page boundaries)
                if (VirtualMemoryProvider.get().protect(imageHeapBegin, imageHeapSizeInFile, Access.READ) != 0) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }

                Pointer writableBegin = IMAGE_HEAP_WRITABLE_BEGIN.get();
                UnsignedWord writableSize = IMAGE_HEAP_WRITABLE_END.get().subtract(writableBegin);
                if (VirtualMemoryProvider.get().protect(writableBegin, writableSize, Access.READ | Access.WRITE) != 0) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
                basePointer.write(imageHeapBegin);
                if (endPointer.isNonNull()) {
                    endPointer.write(IMAGE_HEAP_END.get());
                }
                return CEntryPointErrors.NO_ERROR;
            }
            fd = CACHED_IMAGE_FD.get().read();
        }
        if (UNASSIGNED_FD.equal(fd) || (reservedAddressSpace.isNonNull() && fd.equal(FIRST_ISOLATE_FD))) {
            /*
             * Locate the backing file of the image heap. Unfortunately, we must open the file by
             * its path. As a precaution against unlink races, we verify the file we open matches
             * the inode associated with the mapping.
             *
             * NOTE: we look for the relocatables partition of the linker-mapped heap because it
             * always stays mapped, while the rest of the linker-mapped heap can be unmapped after
             * tearing down the first isolate.
             *
             * NOTE: we do not use /proc/self/exe because it breaks with some tools like Valgrind.
             */
            int mapfd = Fcntl.NoTransitions.open(PROC_SELF_MAPS.get(), Fcntl.O_RDONLY(), 0);
            if (mapfd == -1) {
                return CEntryPointErrors.LOCATE_IMAGE_FAILED;
            }
            CCharPointer buffer = StackValue.get(MAX_PATHLEN);
            WordPointer startAddr = StackValue.get(WordPointer.class);
            WordPointer offset = StackValue.get(WordPointer.class);
            // The relocatables partition might stretch over two adjacent mappings due to permission
            // differences, so only locate the mapping for the first page of relocatables
            boolean found = findMapping(mapfd, buffer, MAX_PATHLEN, IMAGE_HEAP_RELOCATABLE_BEGIN.get(),
                            IMAGE_HEAP_RELOCATABLE_BEGIN.get().add(pageSize), startAddr, offset, true);
            Unistd.NoTransitions.close(mapfd);
            if (!found) {
                return CEntryPointErrors.LOCATE_IMAGE_FAILED;
            }
            int opened = Fcntl.NoTransitions.open(buffer, Fcntl.O_RDONLY(), 0);
            if (opened < 0) {
                return CEntryPointErrors.OPEN_IMAGE_FAILED;
            }
            Word imageHeapRelocsOffset = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get());
            Word imageHeapOffset = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(startAddr.read()).subtract(imageHeapRelocsOffset);
            UnsignedWord fileOffset = imageHeapOffset.add(offset.read());
            CACHED_IMAGE_HEAP_OFFSET.get().write(fileOffset);
            MembarNode.memoryBarrier(MemoryBarriers.STORE_STORE);
            SignedWord previous = ((Pointer) CACHED_IMAGE_FD.get()).compareAndSwapWord(0, fd, signed(opened), LocationIdentity.ANY_LOCATION);
            if (previous.equal(fd)) {
                fd = signed(opened);
            } else {
                Unistd.NoTransitions.close(opened);
                fd = previous;
            }
        }

        Pointer heap;
        Pointer allocatedMemory = WordFactory.nullPointer();
        if (reservedAddressSpace.isNull()) {
            assert imageHeapOffsetInAddressSpace == 0;
            allocatedMemory = VirtualMemoryProvider.get().reserve(imageHeapSizeInFile, alignment);
            if (allocatedMemory.isNull()) {
                return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
            }
            heap = allocatedMemory;
        } else {
            heap = reservedAddressSpace.add(imageHeapOffsetInAddressSpace);
        }

        UnsignedWord fileOffset = CACHED_IMAGE_HEAP_OFFSET.get().read();
        heap = VirtualMemoryProvider.get().mapFile(heap, imageHeapSizeInFile, fd, fileOffset, Access.READ);
        if (heap.isNull()) {
            freeImageHeap(allocatedMemory);
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        Pointer relocPointer = IMAGE_HEAP_A_RELOCATABLE_POINTER.get();
        ComparableWord relocatedValue = relocPointer.readWord(0);
        ComparableWord mappedValue = heap.readWord(relocPointer.subtract(imageHeapBegin));
        if (relocatedValue.notEqual(mappedValue)) {
            /*
             * Addresses were relocated by dynamic linker, so copy them, but first remap the pages
             * to avoid swapping them in from disk.
             */
            Pointer relocsBegin = heap.add(IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(imageHeapBegin));
            UnsignedWord relocsSize = IMAGE_HEAP_RELOCATABLE_END.get().subtract(IMAGE_HEAP_RELOCATABLE_BEGIN.get());
            if (!isAMultiple(relocsSize, pageSize)) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
            if (VirtualMemoryProvider.get().commit(relocsBegin, relocsSize, Access.READ | Access.WRITE).isNull()) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
            memcpy(relocsBegin, IMAGE_HEAP_RELOCATABLE_BEGIN.get(), relocsSize);
            if (VirtualMemoryProvider.get().protect(relocsBegin, relocsSize, Access.READ) != 0) {
                freeImageHeap(allocatedMemory);
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }

        // Unprotect writable pages
        Pointer writableBegin = heap.add(IMAGE_HEAP_WRITABLE_BEGIN.get().subtract(imageHeapBegin));
        UnsignedWord writableSize = IMAGE_HEAP_WRITABLE_END.get().subtract(IMAGE_HEAP_WRITABLE_BEGIN.get());
        if (VirtualMemoryProvider.get().protect(writableBegin, writableSize, Access.READ | Access.WRITE) != 0) {
            freeImageHeap(allocatedMemory);
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }

        basePointer.write(heap.subtract(imageHeapOffsetInAddressSpace));
        if (endPointer.isNonNull()) {
            endPointer.write(roundUp(heap.add(imageHeapSizeInFile), pageSize));
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int freeImageHeap(PointerBase imageHeap) {
        if (imageHeap.isNonNull()) {
            assert Heap.getHeap().getImageHeapOffsetInAddressSpace() == 0;
            if (imageHeap.equal(IMAGE_HEAP_BEGIN.get())) {
                /*
                 * This isolate uses the image heap mapped by the loader. We shouldn't unmap it in
                 * case we are a dynamic library and dlclose() is called on us and tries to access
                 * the pages. However, the heap need not stay resident, so we remap it as an
                 * anonymous mapping. For future isolates, we still need the read-only heap
                 * partition with relocatable addresses that were adjusted by the loader, so we
                 * leave it. (We have already checked that that partition is page-aligned)
                 */
                assert Heap.getHeap().getImageHeapOffsetInAddressSpace() == 0;
                UnsignedWord beforeRelocSize = IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get());
                Pointer beforeRecommit = VirtualMemoryProvider.get().commit(IMAGE_HEAP_BEGIN.get(), beforeRelocSize, Access.READ);

                Word afterRelocSize = IMAGE_HEAP_END.get().subtract(IMAGE_HEAP_RELOCATABLE_END.get());
                Pointer afterRecommit = VirtualMemoryProvider.get().commit(IMAGE_HEAP_RELOCATABLE_END.get(), afterRelocSize, Access.READ);

                if (beforeRecommit.isNull() || afterRecommit.isNull()) {
                    return CEntryPointErrors.MAP_HEAP_FAILED;
                }
            } else {
                Word imageHeapSizeInFile = IMAGE_HEAP_END.get().subtract(IMAGE_HEAP_BEGIN.get());
                if (VirtualMemoryProvider.get().free(imageHeap, imageHeapSizeInFile) != 0) {
                    return CEntryPointErrors.MAP_HEAP_FAILED;
                }
            }
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
