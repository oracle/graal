/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.CODE_START;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_PATCHED_BEGIN;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.HEAP_WRITEABLE_PATCHED_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.VARIABLY_SIZED_DATA;
import static com.oracle.svm.core.util.UnsignedUtils.roundUp;
import static jdk.graal.compiler.word.Word.unsigned;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.guest.staging.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PauseNode;
import org.graalvm.word.impl.Word;

/**
 * Platform-independent support for patching layered image heaps at runtime. The patching operations
 * (code pointer relocation, heap reference patching, field update patching) are pure memory
 * operations with no OS-specific dependencies.
 *
 * Both {@code LinuxImageHeapProvider} and {@code DarwinImageHeapProvider} delegate to this class
 * for layered image heap patching.
 */
public final class LayeredImageHeapSupport {

    private static final class ImageHeapPatchingState {
        static final Word UNINITIALIZED = Word.zero();
        static final Word IN_PROGRESS = Word.unsigned(1);
        static final Word SUCCESSFUL = Word.unsigned(2);
    }

    private static final CGlobalData<Word> IMAGE_HEAP_PATCHING_STATE = CGlobalDataFactory.createWord(ImageHeapPatchingState.UNINITIALIZED);

    /**
     * Apply patches to the image heap as specified by each layer. This method is thread-safe: the
     * first isolate to call it performs the patching while subsequent callers spin-wait until
     * patching is complete.
     *
     * See {@code ImageLayerSectionFeature} for the layout of the section that contains the patches
     * and {@code LayeredDispatchTableFeature} where code patches are gathered.
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
            offset = applyLayerImageHeapRefPatches(data, offset, initialLayerImageHeap);

            applyLayerImageHeapFieldUpdatePatches(data, offset, initialLayerImageHeap);

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

    /**
     * See {@code CrossLayerConstantRegistryFeature#generateRelocationPatchArray} for more details
     * about the layout.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int applyLayerImageHeapRefPatches(Pointer patches, int startOffset, Pointer layerImageHeap) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        int offset = startOffset;
        long countAsLong = patches.readLong(offset);
        int count = UninterruptibleUtils.NumUtil.safeToInt(countAsLong);
        offset += Long.BYTES;
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
        return endOffset;
    }

    /**
     * See {@code CrossLayerUpdaterFeature#generateUpdatePatchArray} for more details about the
     * layout.
     */
    @Uninterruptible(reason = "Thread state not yet set up.")
    private static int applyLayerImageHeapFieldUpdatePatches(Pointer patches, int startOffset, Pointer layerImageHeap) {
        long countAsLong = patches.readLong(startOffset);
        if (countAsLong == 0) {
            // empty - nothing to do
            return startOffset + Long.BYTES;
        }

        int headerSize = patches.readInt(startOffset + Long.BYTES);

        int headerOffset = startOffset + Long.BYTES + Integer.BYTES;
        int headerEndOffset = headerOffset + headerSize;

        // calculate entry offset start
        int entryOffset = headerEndOffset;

        /* Now update values. */
        while (headerOffset < headerEndOffset) {
            // read appropriate slot of header
            int valueSize = patches.readInt(headerOffset);
            headerOffset += Integer.BYTES;
            int numEntries = patches.readInt(headerOffset);
            headerOffset += Integer.BYTES;
            for (int j = 0; j < numEntries; j++) {
                int heapOffset = patches.readInt(entryOffset);
                entryOffset += Integer.BYTES;
                switch (valueSize) {
                    case 1 -> {
                        byte value = patches.readByte(entryOffset);
                        layerImageHeap.writeByte(heapOffset, value);
                        entryOffset += Byte.BYTES;
                    }
                    case 4 -> {
                        int value = patches.readInt(entryOffset);
                        layerImageHeap.writeInt(heapOffset, value);
                        entryOffset += Integer.BYTES;
                    }
                    case 8 -> {
                        long value = patches.readLong(entryOffset);
                        layerImageHeap.writeLong(heapOffset, value);
                        entryOffset += Long.BYTES;
                    }
                    default -> throw VMError.shouldNotReachHereAtRuntime();
                }
            }
        }

        VMError.guarantee((startOffset + Long.BYTES + Integer.BYTES + countAsLong) == entryOffset);
        return entryOffset;
    }
}
