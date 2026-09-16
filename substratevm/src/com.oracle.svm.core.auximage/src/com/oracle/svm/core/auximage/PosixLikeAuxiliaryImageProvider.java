/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.AuxiliaryImageIOProvider;
import com.oracle.svm.core.os.AuxiliaryImageIOProvider.FileDesc;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.UnsignedUtils;

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class PosixLikeAuxiliaryImageProvider extends AbstractAuxiliaryImageProvider {
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static AuxiliaryImageIOProvider io() {
        return ImageSingletons.lookup(AuxiliaryImageIOProvider.class);
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int loadAuxiliaryImage(Pointer reservedAddressSpace, UnsignedWord reservedSize, CCharPointer filePath, WordPointer basePointer, WordPointer endPointer) {
        // NOTE: the caller reserved the address space and must also eventually unmap it
        FileDesc fd = io().open(filePath);
        if (fd.equal(Word.zero())) {
            return CEntryPointErrors.OPEN_AUX_IMAGE_FAILED;
        }
        ComparableWord primaryImageId = readWordAtOffset(fd, AUXILIARY_IMAGE_HEADER_OFFSET + AuxiliaryImageHeader.offsetOfPrimaryImageId(), Word.zero());
        if (primaryImageId.notEqual(AuxiliaryImageLoader.getPrimaryImageId())) {
            io().close(fd);
            if (primaryImageId.equal(Word.zero())) {
                return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
            }
            return CEntryPointErrors.AUX_IMAGE_PRIMARY_IMAGE_MISMATCH;
        }
        UnsignedWord auxImageHeapOffsetInAddressSpace = readWordAtOffset(
                        fd, AUXILIARY_IMAGE_HEADER_OFFSET + AuxiliaryImageHeader.offsetOfAuxImageHeapOffsetInAddressSpace(), UnsignedUtils.MAX_VALUE);
        if (auxImageHeapOffsetInAddressSpace.equal(UnsignedUtils.MAX_VALUE)) {
            io().close(fd);
            return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
        }
        UnsignedWord auxImageHeapFileOffset = readWordAtOffset(fd, AUXILIARY_IMAGE_HEAP_OFFSET_IN_FILE_OFFSET, UnsignedUtils.MAX_VALUE);
        if (auxImageHeapFileOffset.equal(UnsignedUtils.MAX_VALUE)) {
            io().close(fd);
            return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
        }
        UnsignedWord sizeInFile = readWordAtOffset(fd, AUXILIARY_IMAGE_HEAP_SIZE_IN_FILE_OFFSET, Word.zero());
        if (sizeInFile.equal(0)) {
            io().close(fd);
            return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
        }
        UnsignedWord sizeInMemory = readWordAtOffset(fd, AUXILIARY_IMAGE_HEAP_SIZE_IN_MEMORY_OFFSET, Word.zero());
        if (sizeInMemory.equal(0) || sizeInMemory.belowThan(sizeInFile)) {
            io().close(fd);
            return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
        }
        Pointer base = KnownIntrinsics.heapBase().add(auxImageHeapOffsetInAddressSpace);
        Pointer end = base.add(sizeInMemory);
        if (base.belowThan(reservedAddressSpace) || end.aboveThan(reservedAddressSpace.add(reservedSize))) {
            io().close(fd);
            return CEntryPointErrors.MAP_AUX_IMAGE_FAILED;
        }
        UnsignedWord imageHeapToOriginObjectOffset = readWordAtOffset(fd, AUXILIARY_IMAGE_HEADER_OFFSET + AuxiliaryImageHeader.offsetOfImageHeapToOriginObjectOffset(), UnsignedUtils.MAX_VALUE);
        if (imageHeapToOriginObjectOffset.equal(UnsignedUtils.MAX_VALUE)) {
            io().close(fd);
            return CEntryPointErrors.READ_AUX_IMAGE_META_FAILED;
        }
        final int memoryAccess = VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE;
        if (sizeInMemory.notEqual(sizeInFile)) {
            if (base.notEqual(VirtualMemoryProvider.get().commit(base, sizeInMemory, memoryAccess))) {
                io().close(fd);
                return CEntryPointErrors.MAP_AUX_IMAGE_FAILED;
            }
        }
        Pointer mapping = io().mapFile(base, sizeInFile, fd, auxImageHeapFileOffset, memoryAccess);
        io().close(fd);
        if (mapping.isNull() || mapping.notEqual(base)) {
            VirtualMemoryProvider.get().uncommit(base, sizeInMemory);
            return CEntryPointErrors.MAP_AUX_IMAGE_FAILED;
        }

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            if (!AuxiliaryImageLoader.hasAuxImageReservedSpace()) {
                NativeMemoryTracking.singleton().trackReserve(sizeInFile, NmtCategory.AuxiliaryImage);
            }
            NativeMemoryTracking.singleton().trackCommit(sizeInFile, NmtCategory.AuxiliaryImage);
        }

        basePointer.write(base);
        endPointer.write(end);
        AuxiliaryImageLoader.setAuxImageLocation(base, end, imageHeapToOriginObjectOffset);
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static <T extends WordBase> T readWordAtOffset(FileDesc fd, int offsetInFile, T failureResult) {
        UnsignedWord size = SizeOf.unsigned(WordPointer.class);
        Pointer p = (Pointer) StackValue.get(WordPointer.class);
        UnsignedWord readBytes = Word.zero();
        do {
            SignedWord result = io().pread(fd, p.add(readBytes), size.subtract(readBytes), (SignedWord) readBytes.add(offsetInFile));
            if (result.lessOrEqual(0)) {
                io().close(fd);
                return failureResult;
            }
            readBytes = readBytes.add((UnsignedWord) result);
        } while (readBytes.belowThan(size));
        return ((WordPointer) p).read();
    }

    @Override
    public int unloadAuxiliaryImage(Pointer base, Pointer end) {
        int result = VirtualMemoryProvider.get().uncommit(base, end.subtract(base));
        AuxiliaryImageLoader.setAuxImageLocation(Word.nullPointer(), Word.nullPointer(), Word.zero());
        return result;
    }

}
