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

import static com.oracle.svm.core.util.PointerUtils.roundUp;
import static org.graalvm.word.impl.Word.unsigned;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.IsolateArgumentAccess;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.AuxiliaryImageProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.UnsignedUtils;

public abstract class AbstractAuxiliaryImageProvider implements AuxiliaryImageProvider {
    /** Offset of {@link AuxiliaryImageHeader} in an auxiliary image file. */
    public static final int AUXILIARY_IMAGE_HEADER_OFFSET = AuxiliaryImageFileHeader.AUX_HEADER_STRUCTURE_OFFSET_IN_FILE;
    public static final int AUXILIARY_IMAGE_HEAP_OFFSET_IN_FILE_OFFSET = AuxiliaryImageFileHeader.AUX_IMAGE_HEAP_OFFSET_IN_FILE_WORD_OFFSET_IN_FILE;
    public static final int AUXILIARY_IMAGE_HEAP_SIZE_IN_FILE_OFFSET = AuxiliaryImageFileHeader.AUX_IMAGE_HEAP_SIZE_IN_FILE_WORD_OFFSET_IN_FILE;
    public static final int AUXILIARY_IMAGE_HEAP_SIZE_IN_MEMORY_OFFSET = AuxiliaryImageFileHeader.AUX_IMAGE_HEAP_SIZE_IN_MEMORY_WORD_OFFSET_IN_FILE;

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initializeHeapAddressRange(IsolateArguments arguments, Pointer reservedBegin, UnsignedWord reservedSize, Pointer imageHeapEnd, WordPointer collectedHeapBeginOut) {
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();

        CCharPointer auxImagePath = IsolateArgumentAccess.readCCharPointer(arguments, IsolateArgumentParser.getOptionIndex(SubstrateOptions.AuxiliaryImagePathIsolateArgument));

        /* Determine how much of the address space should be reserved for auxiliary images. */
        UnsignedWord auxImageReserved = unsigned(IsolateArgumentAccess.readLong(arguments, IsolateArgumentParser.getOptionIndex(SubstrateOptions.AuxiliaryImageBytesIsolateArgument)));
        if (auxImageReserved.equal(Word.zero())) {
            auxImageReserved = Word.unsigned(SubstrateOptions.ReservedAuxiliaryImageBytes.getValue());
        }
        auxImageReserved = UnsignedUtils.roundUp(auxImageReserved, pageSize);

        /* Return early if there is nothing to do. */
        if (auxImagePath.isNull() && auxImageReserved.equal(0)) {
            assert PointerUtils.isAMultiple(imageHeapEnd, pageSize);
            collectedHeapBeginOut.write(imageHeapEnd);
            return CEntryPointErrors.NO_ERROR;
        }

        /* Determine how many address space bytes can actually be used for the auxiliary images. */
        Pointer auxBegin = roundUp(imageHeapEnd, unsigned(Heap.getHeap().getImageHeapAlignment()));
        UnsignedWord auxAvailable = reservedSize.subtract(auxBegin.subtract(reservedBegin));
        assert UnsignedUtils.isAMultiple(auxAvailable, pageSize);
        if (auxImageReserved.aboveThan(0)) {
            if (auxImageReserved.aboveThan(auxAvailable)) {
                return CEntryPointErrors.INSUFFICIENT_AUX_IMAGE_MEMORY;
            }
            auxAvailable = auxImageReserved;

            /* Keep track of any reserved memory. */
            AuxiliaryImageLoader.setAuxImageReservedSpace(auxBegin, auxImageReserved);
            if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
                NativeMemoryTracking.singleton().trackReserve(auxImageReserved, NmtCategory.AuxiliaryImage);
            }
        }

        /* Try to load the auxiliary image right away. */
        WordPointer auxLoadedEndPtr = StackValue.get(WordPointer.class);
        auxLoadedEndPtr.write(Word.nullPointer());
        if (auxImagePath.isNonNull()) {
            WordPointer auxLoadedBeginPtr = StackValue.get(WordPointer.class);
            int errorCode = loadAuxiliaryImage(auxBegin, auxAvailable, auxImagePath, auxLoadedBeginPtr, auxLoadedEndPtr);
            if (errorCode != CEntryPointErrors.NO_ERROR) {
                return errorCode;
            }
        }

        /*
         * If a portion of the address space has been reserved for auxiliary images, the collected
         * heap begins at the end of that reserved region. If no memory was reserved, the collected
         * heap starts immediately after the mapped auxiliary image.
         */
        Pointer result = PointerUtils.max(auxLoadedEndPtr.read(), auxBegin.add(auxImageReserved));
        assert PointerUtils.isAMultiple(result, pageSize);
        collectedHeapBeginOut.write(result);
        return CEntryPointErrors.NO_ERROR;
    }
}
