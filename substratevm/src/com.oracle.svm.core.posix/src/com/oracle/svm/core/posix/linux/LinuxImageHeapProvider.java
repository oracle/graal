/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_PATCHED_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_PATCHED_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.CODE_START;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_PATCHED_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_PATCHED_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.VARIABLY_SIZED_DATA;
import static com.oracle.svm.core.posix.linux.ProcFSSupport.findMapping;
import static com.oracle.svm.core.util.PointerUtils.roundDown;
import static com.oracle.svm.core.util.UnsignedUtils.roundUp;
import static jdk.graal.compiler.word.Word.signed;
import static jdk.graal.compiler.word.Word.unsigned;

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

import com.oracle.svm.core.SubstrateOptions;
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
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.os.AbstractImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.ImageLayerSectionFeature;
import com.oracle.svm.hosted.imagelayer.LayeredDispatchTableFeature;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PauseNode;
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
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class LinuxImageHeapProvider extends AbstractImageHeapProvider {
    /** Magic value to verify that a located image file matches our loaded image. */
    public static final CGlobalData<Pointer> MAGIC = CGlobalDataFactory.createWord(Word.<Word> signed(ThreadLocalRandom.current().nextLong()));

    private static final CGlobalData<CCharPointer> PROC_SELF_MAPS = CGlobalDataFactory.createCString("/proc/self/maps");

    private static final SignedWord UNASSIGNED_FD = signed(-1);
    private static final SignedWord CANNOT_OPEN_FD = signed(-2);

    private static final SignedWord COPY_RELOCATIONS_IN_PROGRESS = signed(-1);

    private static final CGlobalData<WordPointer> CACHED_IMAGE_FD = CGlobalDataFactory.createWord(UNASSIGNED_FD);
    private static final CGlobalData<WordPointer> CACHED_IMAGE_HEAP_OFFSET = CGlobalDataFactory.createWord();
    private static final CGlobalData<WordPointer> CACHED_IMAGE_HEAP_RELOCATIONS = CGlobalDataFactory.createWord();

    private static final int MAX_PATHLEN = 4096;

    private static final class ImageHeapPatchingState {
        static final Word UNINITIALIZED = Word.zero();
        static final Word IN_PROGRESS = Word.unsigned(1);
        static final Word SUCCESSFUL = Word.unsigned(2);
    }

    private static final CGlobalData<Word> IMAGE_HEAP_PATCHING_STATE = CGlobalDataFactory.createWord(ImageHeapPatchingState.UNINITIALIZED);

    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int initializeLayeredImage(Pointer imageHeapStart, Pointer imageHeapEnd, Pointer selfReservedHeapBase) {
        UnsignedWord imageHeapAlignment = unsigned(Heap.getHeap().getImageHeapAlignment());
        assert PointerUtils.isAMultiple(imageHeapStart, imageHeapAlignment);

        patchLayeredImageHeap();

        int result = -1;
        int layerCount = 0;
        Pointer currentSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer currentHeapStart = imageHeapStart;
        while (currentSection.isNonNull()) {
            var cachedFDPointer = ImageLayerSection.getCachedImageFDs().get().addressOf(layerCount);
            var cachedOffsetsPointer = ImageLayerSection.getCachedImageHeapOffsets().get().addressOf(layerCount);
            var cachedImageHeapRelocationsPtr = ImageLayerSection.getCachedImageHeapRelocations().get().addressOf(layerCount);
            Word heapBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
            Word heapEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_END));
            Word heapRelocBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_RELOCATABLE_BEGIN));
            Word heapRelocEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_RELOCATABLE_END));
            /* This value is not used for layered images. */
            Word heapAnyRelocPointer = Word.nullPointer();
            Word heapWritableBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_BEGIN));
            Word heapWritableEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_END));
            Word heapWritablePatchedBegin = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_PATCHED_BEGIN));
            Word heapWritablePatchedEnd = currentSection.readWord(ImageLayerSection.getEntryOffset(HEAP_WRITEABLE_PATCHED_END));

            /* Each layer's image heap starts at an aligned offset. */
            currentHeapStart = PointerUtils.roundUp(currentHeapStart, imageHeapAlignment);

            UnsignedWord imageHeapSize = getImageHeapSizeInFile(heapBegin, heapEnd);
            result = initializeImageHeap(currentHeapStart, imageHeapSize,
                            cachedFDPointer, cachedOffsetsPointer, cachedImageHeapRelocationsPtr, MAGIC.get(),
                            heapBegin, heapEnd,
                            heapRelocBegin, heapAnyRelocPointer, heapRelocEnd,
                            heapWritablePatchedBegin, heapWritablePatchedEnd, heapWritableBegin, heapWritableEnd);
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
                return result;
            }

            currentHeapStart = currentHeapStart.add(imageHeapSize);

            // read the next layer
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
            layerCount++;
        }
        assert imageHeapEnd == currentHeapStart;
        return result;
    }

    /**
     * Apply patches to the image heap as specified by each layer. See {@link ImageLayerSection} and
     * {@link ImageLayerSectionFeature} for the layout of the section that contains the patches and
     * {@link LayeredDispatchTableFeature} where code patches are gathered.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    public static void patchLayeredImageHeap() {
        Word heapPatchStateAddr = IMAGE_HEAP_PATCHING_STATE.get();
        boolean firstIsolate = heapPatchStateAddr.logicCompareAndSwapWord(0, ImageHeapPatchingState.UNINITIALIZED, ImageHeapPatchingState.IN_PROGRESS, NamedLocationIdentity.OFF_HEAP_LOCATION);

        if (!firstIsolate) {
            // spin-wait for first isolate
            Word state = heapPatchStateAddr.readWordVolatile(0, NamedLocationIdentity.OFF_HEAP_LOCATION);
            while (state.equal(ImageHeapPatchingState.IN_PROGRESS)) {
                PauseNode.pause();
                state = heapPatchStateAddr.readWordVolatile(0, NamedLocationIdentity.OFF_HEAP_LOCATION);
            }

            /* Patching has already been successfully completed, nothing needs to be done. */
            return;
        }

        Pointer layerSection = ImageLayerSection.getInitialLayerSection().get();
        Pointer initialLayerImageHeap = layerSection.readWord(ImageLayerSection.getEntryOffset(HEAP_BEGIN));
        Pointer codeBase = layerSection.readWord(ImageLayerSection.getEntryOffset(CODE_START));

        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        while (layerSection.isNonNull()) {
            Pointer data = layerSection.add(ImageLayerSection.getEntryOffset(VARIABLY_SIZED_DATA));
            int offset = 0;

            offset = skipSingletonsTable(data, offset, referenceSize);

            /* Patch code offsets to become relative to the code base. */
            Pointer layerHeapRelocs = layerSection.readWord(ImageLayerSection.getEntryOffset(HEAP_RELOCATABLE_BEGIN));
            Pointer layerCode = layerSection.readWord(ImageLayerSection.getEntryOffset(CODE_START));
            /*
             * Note that the code base can be above the layer's code section, in which case the
             * subtraction underflows and the additions of code address computations overflow,
             * giving the correct result.
             */
            Word layerCodeOffsetToBase = (Word) layerCode.subtract(codeBase);
            offset = applyLayerCodePointerPatches(data, offset, layerHeapRelocs, layerCodeOffsetToBase);

            /* Patch absolute addresses to become relative to the code base. */
            Word negativeCodeBase = Word.<Word> zero().subtract(codeBase);
            offset = applyLayerCodePointerPatches(data, offset, layerHeapRelocs, negativeCodeBase);

            /* Patch references in the image heap. */
            applyLayerImageHeapRefPatches(data.add(offset), initialLayerImageHeap);

            layerSection = layerSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }

        heapPatchStateAddr.writeWordVolatile(0, ImageHeapPatchingState.SUCCESSFUL);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int skipSingletonsTable(Pointer data, int offset, int referenceSize) {
        long singletonTableEntryCount = data.readLong(offset);
        UnsignedWord singletonTableAlignedSize = roundUp(unsigned(singletonTableEntryCount * referenceSize), unsigned(Long.BYTES));
        return offset + Long.BYTES + UnsignedUtils.safeToInt(singletonTableAlignedSize);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int applyLayerCodePointerPatches(Pointer data, int startOffset, Pointer layerHeapRelocs, Word addend) {
        int wordSize = ConfigurationValues.getTarget().wordSize;

        int offset = startOffset;
        long bitmapWordCountAsLong = data.readLong(offset);
        int bitmapWordCount = UninterruptibleUtils.NumUtil.safeToInt(bitmapWordCountAsLong);
        offset += Long.BYTES;
        if (addend.equal(0)) {
            /* Nothing to do. */
            offset += bitmapWordCount * Long.BYTES;
            return offset;
        }

        for (int i = 0; i < bitmapWordCount; i++) {
            long bits = data.readLong(offset);
            offset += Long.BYTES;
            int j = 0; // index of a 1-bit
            while (bits != 0) {
                int ntz = UninterruptibleUtils.Long.countTrailingZeros(bits);
                j += ntz;

                int at = (i * 64 + j) * wordSize;
                Word w = layerHeapRelocs.readWord(at);
                w = w.add(addend);
                layerHeapRelocs.writeWord(at, w);

                /*
                 * Note that we must not shift by ntz+1 here because it can be 64, which would be a
                 * no-op according to the Java Language Specification, 15.19. Shift Operators.
                 */
                bits = (bits >>> ntz) >>> 1;
                j++;
            }
        }
        return offset;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    private static void applyLayerImageHeapRefPatches(Pointer patches, Pointer layerImageHeap) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        long countAsLong = patches.readLong(0);
        int count = UninterruptibleUtils.NumUtil.safeToInt(countAsLong);
        int offset = Long.BYTES;
        int endOffset = offset + count * Integer.BYTES;
        while (offset < endOffset) {
            int heapOffset = patches.readInt(offset);
            int referenceEncoding = patches.readInt(offset + Integer.BYTES);
            offset += 2 * Integer.BYTES;
            if (referenceSize == 4) {
                layerImageHeap.writeInt(heapOffset, referenceEncoding);
            } else {
                layerImageHeap.writeLong(heapOffset, referenceEncoding);
            }
        }
    }

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

        /* Update heap base and image heap end. */
        assert PointerUtils.isAMultiple(heapBase, Word.unsigned(Heap.getHeap().getHeapBaseAlignment()));
        heapBaseOut.write(heapBase);

        Pointer imageHeapEnd = getImageHeapEnd(heapBase);
        assert PointerUtils.isAMultiple(imageHeapEnd, VirtualMemoryProvider.get().getGranularity());
        imageHeapEndOut.write(imageHeapEnd);

        /* Map the image heap. */
        Pointer imageHeapStart = getImageHeapBegin(heapBase);
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return initializeLayeredImage(imageHeapStart, imageHeapEnd, selfReservedHeapBase);
        } else {
            int result = initializeImageHeap(imageHeapStart, getImageHeapSizeInFile(),
                            CACHED_IMAGE_FD.get(), CACHED_IMAGE_HEAP_OFFSET.get(), CACHED_IMAGE_HEAP_RELOCATIONS.get(), MAGIC.get(),
                            IMAGE_HEAP_BEGIN.get(), IMAGE_HEAP_END.get(),
                            IMAGE_HEAP_RELOCATABLE_BEGIN.get(), IMAGE_HEAP_A_RELOCATABLE_POINTER.get(), IMAGE_HEAP_RELOCATABLE_END.get(),
                            IMAGE_HEAP_WRITABLE_PATCHED_BEGIN.get(), IMAGE_HEAP_WRITABLE_PATCHED_END.get(), IMAGE_HEAP_WRITABLE_BEGIN.get(), IMAGE_HEAP_WRITABLE_END.get());
            if (result != CEntryPointErrors.NO_ERROR) {
                freeImageHeap(selfReservedHeapBase);
            }
            return result;
        }
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int initializeImageHeap(Pointer imageHeap, UnsignedWord imageHeapSize, WordPointer cachedFd, WordPointer cachedOffsetInFile, WordPointer cachedImageHeapRelocationsPtr,
                    Pointer magicAddress, Word heapBeginSym, Word heapEndSym, Word heapRelocsSym, Pointer heapAnyRelocPointer, Word heapRelocsEndSym, Word heapWritablePatchedSym,
                    Word heapWritablePatchedEndSym, Word heapWritableSym, Word heapWritableEndSym) {
        assert heapBeginSym.belowOrEqual(heapWritableSym) && heapWritableSym.belowOrEqual(heapWritableEndSym) && heapWritableEndSym.belowOrEqual(heapEndSym);
        assert heapBeginSym.belowOrEqual(heapRelocsSym) && heapRelocsSym.belowOrEqual(heapRelocsEndSym) && heapRelocsEndSym.belowOrEqual(heapEndSym);
        assert ImageLayerBuildingSupport.buildingImageLayer() || heapRelocsSym.belowOrEqual(heapAnyRelocPointer) && heapAnyRelocPointer.belowThan(heapRelocsEndSym);
        assert heapRelocsSym.belowOrEqual(heapRelocsEndSym) && heapRelocsEndSym.belowOrEqual(heapWritablePatchedSym) && heapWritablePatchedSym.belowOrEqual(heapWritableEndSym);

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

        /*
         * If we cannot find or open the image file, fall back to copy it from memory (the image
         * heap must be in pristine condition for that).
         */
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        if (fd.equal(CANNOT_OPEN_FD)) {
            int result = initializeImageHeapWithMremap(imageHeap, imageHeapSize, pageSize, cachedImageHeapRelocationsPtr, heapBeginSym, heapRelocsSym, heapAnyRelocPointer, heapRelocsEndSym,
                            heapWritablePatchedSym, heapWritablePatchedEndSym, heapWritableSym, heapWritableEndSym);
            if (result == CEntryPointErrors.MREMAP_NOT_SUPPORTED) {
                /*
                 * MREMAP_DONTUNMAP is not supported, fall back to copying it from memory (the image
                 * heap must be in pristine condition for that).
                 */
                return initializeImageHeapByCopying(imageHeap, imageHeapSize, pageSize, heapBeginSym, heapWritableSym, heapWritableEndSym);
            }
            return result;
        }

        // Create memory mappings from the image file.
        UnsignedWord fileOffset = cachedOffsetInFile.read();
        Pointer mappedImageHeap = VirtualMemoryProvider.get().mapFile(imageHeap, imageHeapSize, fd, fileOffset, Access.READ);
        if (mappedImageHeap.isNull() || mappedImageHeap != imageHeap) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        int result = copyRelocations(imageHeap, pageSize, heapBeginSym, heapRelocsSym, heapAnyRelocPointer, heapRelocsEndSym, heapWritablePatchedSym, heapWritablePatchedEndSym,
                        Word.nullPointer());
        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }

        return unprotectWritablePages(imageHeap, pageSize, heapBeginSym, heapWritableSym, heapWritableEndSym);
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int initializeImageHeapWithMremap(Pointer imageHeap, UnsignedWord imageHeapSizeInFile, UnsignedWord pageSize, WordPointer cachedImageHeapRelocationsPtr, Word heapBeginSym,
                    Word heapRelocsSym, Pointer heapAnyRelocPointer, Word heapRelocsEndSym, Word heapWritablePatchedSym, Word heapWritablePatchedEndSym, Word heapWritableSym,
                    Word heapWritableEndSym) {
        if (!SubstrateOptions.MremapImageHeap.getValue()) {
            return CEntryPointErrors.MREMAP_NOT_SUPPORTED;
        }

        Pointer cachedImageHeapRelocations = getCachedImageHeapRelocations((Pointer) cachedImageHeapRelocationsPtr, pageSize, heapRelocsSym,
                        heapWritablePatchedEndSym);
        assert cachedImageHeapRelocations.notEqual(0);
        if (cachedImageHeapRelocations.rawValue() < 0) {
            return (int) -cachedImageHeapRelocations.rawValue(); // value is a negated error code
        }

        // Map the image heap for the new isolate from the template
        int mremapFlags = LinuxLibCHelper.MREMAP_FIXED() | LinuxLibCHelper.MREMAP_MAYMOVE() | LinuxLibCHelper.MREMAP_DONTUNMAP();
        PointerBase res = LinuxLibCHelper.NoTransitions.mremapP(heapBeginSym, imageHeapSizeInFile, imageHeapSizeInFile, mremapFlags, imageHeap);
        if (res.notEqual(imageHeap)) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        int result = copyRelocations(imageHeap, pageSize, heapBeginSym, heapRelocsSym, heapAnyRelocPointer, heapRelocsEndSym, heapWritablePatchedSym, heapWritablePatchedEndSym,
                        cachedImageHeapRelocations);
        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }

        if (VirtualMemoryProvider.get().protect(imageHeap, imageHeapSizeInFile, Access.READ) != 0) {
            return CEntryPointErrors.PROTECT_HEAP_FAILED;
        }

        return unprotectWritablePages(imageHeap, pageSize, heapBeginSym, heapWritableSym, heapWritableEndSym);
    }

    /**
     * Returns a valid pointer if successful; otherwise, returns a negated
     * {@linkplain CEntryPointErrors error code}.
     *
     * It is necessary to cache the image heap relocations because when we remap a file-backed
     * memory region, we observe that the "old" virtual address space is reinitialized to its
     * original state w/o relocations.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static Pointer getCachedImageHeapRelocations(Pointer cachedImageHeapRelocationsPtr, UnsignedWord pageSize, Word heapRelocsSym, Word heapWritablePatchedEndSym) {
        Pointer imageHeapRelocations = cachedImageHeapRelocationsPtr.readWord(0, LocationIdentity.ANY_LOCATION);
        if (imageHeapRelocations.isNull() || imageHeapRelocations.equal(COPY_RELOCATIONS_IN_PROGRESS)) {
            if (!cachedImageHeapRelocationsPtr.logicCompareAndSwapWord(0, Word.nullPointer(), COPY_RELOCATIONS_IN_PROGRESS, LocationIdentity.ANY_LOCATION)) {
                /* Wait for other thread to initialize heap relocations. */
                while ((imageHeapRelocations = cachedImageHeapRelocationsPtr.readWordVolatile(0, LocationIdentity.ANY_LOCATION)).equal(COPY_RELOCATIONS_IN_PROGRESS)) {
                    PauseNode.pause();
                }
            } else {
                /*
                 * This is the first time mapping the heap. Create a private copy of the relocated
                 * image heap symbols, as these may be reverted during subsequent mremaps.
                 */

                Pointer linkedRelocsBoundary = roundDown(heapRelocsSym, pageSize);
                UnsignedWord heapRelocsLength = roundUp(heapWritablePatchedEndSym.subtract(linkedRelocsBoundary), pageSize);
                int mremapFlags = LinuxLibCHelper.MREMAP_MAYMOVE() | LinuxLibCHelper.MREMAP_DONTUNMAP();
                imageHeapRelocations = LinuxLibCHelper.NoTransitions.mremapP(linkedRelocsBoundary, heapRelocsLength, heapRelocsLength, mremapFlags, Word.nullPointer());

                if (imageHeapRelocations.equal(-1)) {
                    if (LibC.errno() == Errno.EINVAL()) {
                        /*
                         * MREMAP_DONTUNMAP with non-anonymous mappings is only supported from
                         * kernel version 5.13 onwards, and fails with EINVAL otherwise.
                         *
                         * https://github.com/torvalds/linux/commit/
                         * a4609387859f0281951f5e476d9f76d7fb9ab321
                         */
                        imageHeapRelocations = Word.pointer(-CEntryPointErrors.MREMAP_NOT_SUPPORTED);
                    } else {
                        imageHeapRelocations = Word.pointer(-CEntryPointErrors.MAP_HEAP_FAILED);
                    }
                } else {
                    if (VirtualMemoryProvider.get().protect(imageHeapRelocations, heapRelocsLength, Access.READ) != 0) {
                        imageHeapRelocations = Word.pointer(-CEntryPointErrors.PROTECT_HEAP_FAILED);
                    }
                }

                cachedImageHeapRelocationsPtr.writeWordVolatile(0, imageHeapRelocations);
            }
        }

        assert imageHeapRelocations.isNonNull() && imageHeapRelocations.notEqual(COPY_RELOCATIONS_IN_PROGRESS);
        return imageHeapRelocations;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int copyRelocations(Pointer imageHeap, UnsignedWord pageSize, Word heapBeginSym, Word heapRelocsSym, Pointer heapAnyRelocPointer, Word heapRelocsEndSym,
                    Word heapWritablePatchedSym, Word heapWritablePatchedEndSym, Pointer cachedRelocsBoundary) {
        Pointer linkedRelocsBoundary = roundDown(heapRelocsSym, pageSize);
        Pointer sourceRelocsBoundary = cachedRelocsBoundary.isNonNull() ? cachedRelocsBoundary : linkedRelocsBoundary;
        Pointer linkedCopyStart = Word.nullPointer();
        if (heapRelocsEndSym.subtract(heapRelocsSym).isNonNull()) {
            boolean copyRequired;
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                assert heapAnyRelocPointer.isNull();
                /*
                 * The relocatable section with layered images will normally contain relocations
                 * referring to both the same and different layers. Hence, it is not possible to use
                 * a representative pointer to determine whether copying is necessary.
                 */
                copyRequired = true;
            } else {
                /*
                 * Use a representative pointer to determine whether it is necessary to copy over
                 * the relocations from the original image, or if it has already been performed
                 * during build-link time.
                 */
                ComparableWord relocatedValue = sourceRelocsBoundary.readWord(heapAnyRelocPointer.subtract(linkedRelocsBoundary));
                ComparableWord mappedValue = imageHeap.readWord(heapAnyRelocPointer.subtract(heapBeginSym));
                copyRequired = relocatedValue.notEqual(mappedValue);
            }
            if (copyRequired) {
                linkedCopyStart = heapRelocsSym;
            }
        }
        if (linkedCopyStart.isNull() && heapWritablePatchedEndSym.subtract(heapWritablePatchedSym).isNonNull()) {
            /*
             * When they exist, patched entries always need to be copied over from the original
             * image.
             */
            linkedCopyStart = heapWritablePatchedSym;
        }
        if (linkedCopyStart.isNonNull()) {
            /*
             * A portion of the image heap needs to be manually copied over from the original image.
             * This is needed whenever:
             *
             * 1. Relocations resolved by the dynamic linker are present.
             *
             * 2. (For layered images only): Heap relative relocations are present (i.e. the
             * heapWritablePatched section is non-zero).
             *
             * To preserve this information, we must copy over this data from the original image
             * heap and not reload it from disk. Note this copying must be performed at a page
             * granularity, and hence may copy some extra data which could be copy-on-write.
             */
            Pointer linkedCopyStartBoundary = roundDown(linkedCopyStart, pageSize);
            UnsignedWord copyAlignedSize = roundUp(heapWritablePatchedEndSym.subtract(linkedCopyStartBoundary), pageSize);
            Pointer destCopyStartBoundary = imageHeap.add(linkedCopyStartBoundary.subtract(heapBeginSym));

            Pointer committedCopyBegin = VirtualMemoryProvider.get().commit(destCopyStartBoundary, copyAlignedSize, Access.READ | Access.WRITE);
            if (committedCopyBegin.isNull() || committedCopyBegin != destCopyStartBoundary) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }

            Pointer sourceCopyStartBoundary = sourceRelocsBoundary.add(linkedCopyStartBoundary.subtract(linkedRelocsBoundary));
            LibC.memcpy(destCopyStartBoundary, sourceCopyStartBoundary, copyAlignedSize);

            // make the relocations read-only again
            if (linkedRelocsBoundary.belowOrEqual(heapRelocsEndSym) && heapRelocsEndSym.subtract(heapRelocsSym).isNonNull()) {
                UnsignedWord relocsAlignedSize = roundUp(heapRelocsEndSym.subtract(linkedRelocsBoundary), pageSize);
                if (VirtualMemoryProvider.get().protect(destCopyStartBoundary, relocsAlignedSize, Access.READ) != 0) {
                    return CEntryPointErrors.PROTECT_HEAP_FAILED;
                }
            }
        }

        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static int unprotectWritablePages(Pointer imageHeap, UnsignedWord pageSize, Word heapBeginSym, Word heapWritableSym, Word heapWritableEndSym) {
        /*
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

        if (PosixUtils.readUninterruptibly(imagefd, (Pointer) buffer, wordSize) != wordSize) {
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
