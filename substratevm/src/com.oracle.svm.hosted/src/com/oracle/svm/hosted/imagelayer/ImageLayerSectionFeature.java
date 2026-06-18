/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import static org.graalvm.word.impl.Word.signed;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.AppLayerCGlobalTracking;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Creates a {@linkplain ImageLayerSection section} with information specific to the current layer.
 *
 * Presently the layout of this section is as follows:
 *
 * <pre>
 *  ---------------------------------------------------------
 *  |  byte offset  | name                                  |
 *  |  0            | heap begin                            |
 *  |  8            | heap end                              |
 *  |  16           | heap relocatable begin                |
 *  |  24           | heap relocatable end                  |
 *  |  32           | heap writable begin                   |
 *  |  40           | heap writable end                     |
 *  |  48           | heap writable patched begin           |
 *  |  56           | heap writable patched end             |
 *  |  64           | code (text) start                     |
 *  |  72           | next layer section (0 if final layer) |________________________________
 *  |  80           | cross-layer singleton table size      | variably-sized data starts here
 *  |  88..         | cross-layer singleton table entries   |
 *  |  ..           | bitmap of code offsets to patch       |  \  for how these are gathered, see
 *  |  ..           | bitmap of code addresses to patch     |  /  {@link LayeredDispatchTableFeature}
 *  |  ..           | image heap reference patches          |
 *  |  ..           | image heap field update patches       |
 *  ---------------------------------------------------------
 * </pre>
 *
 * On Windows, the PE/COFF runtime fixup table pointer is stored at offset 80 and variably-sized
 * data starts at offset 88. This keeps the existing layout unchanged for other platforms.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class ImageLayerSectionFeature implements InternalFeature {

    private static final SectionName SVM_LAYER_SECTION = new SectionName.ProgbitsSectionName("svm_layer");
    private static final String LAYER_NAME_PREFIX = "__svm_vm_layer";

    private static final int HEAP_BEGIN_OFFSET = 0;
    private static final int HEAP_END_OFFSET = 8;
    private static final int HEAP_RELOCATABLE_BEGIN_OFFSET = 16;
    private static final int HEAP_RELOCATABLE_END_OFFSET = 24;
    private static final int HEAP_WRITABLE_BEGIN_OFFSET = 32;
    private static final int HEAP_WRITABLE_END_OFFSET = 40;
    private static final int HEAP_WRITABLE_PATCHED_BEGIN_OFFSET = 48;
    private static final int HEAP_WRITABLE_PATCHED_END_OFFSET = 56;
    private static final int CODE_START_OFFSET = 64;
    private static final int NEXT_SECTION_OFFSET = 72;
    private static final int VARIABLY_SIZED_DATA_OFFSET = 80;
    private static final int PECOFF_FIXUP_TABLE_OFFSET = VARIABLY_SIZED_DATA_OFFSET;

    private static final String CACHED_IMAGE_FDS_NAME = "__svm_layer_cached_image_fds";
    private static final String CACHED_IMAGE_HEAP_OFFSETS_NAME = "__svm_layer_cached_image_heap_offsets";
    private static final String CACHED_IMAGE_HEAP_RELOCATIONS_NAME = "__svm_layer_cached_image_heap_relocations";
    public static final String PECOFF_FORWARD_SYMBOL_FIXUP_TABLE_SYMBOL_NAME = "__svm_pecoff_forward_symbol_fixup_table";

    private static final SignedWord UNASSIGNED_FD = signed(-1);

    private ObjectFile.ProgbitsSectionImpl layeredSectionData;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(LoadImageSingletonFeature.class, CrossLayerConstantRegistryFeature.class, CGlobalDataFeature.class, CrossLayerFieldUpdaterFeature.class);
    }

    @Override
    public void onRegistration(OnRegistrationAccess access) {
        ImageSingletons.add(ImageLayerSectionFeature.class, this);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (Platform.includedIn(Platform.WINDOWS.class) && ImageLayerBuildingSupport.buildingSharedLayer() && ImageLayerBuildingSupport.buildingExtensionLayer()) {
            throw UserError.abort("PE/COFF image layers currently support only one shared layer before the final application layer.");
        }

        CGlobalData<Pointer> initialSectionStart = ImageLayerBuildingSupport.buildingInitialLayer() ? CGlobalDataFactory.forSymbol(getLayerName(DynamicImageLayerInfo.getCurrentLayerNumber())) : null;

        CGlobalData<WordPointer> cachedImageFDs;
        CGlobalData<WordPointer> cachedImageHeapOffsets;
        CGlobalData<WordPointer> cachedImageHeapRelocations;

        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            cachedImageFDs = CGlobalDataFactory.forApplicationLayerSymbol(CACHED_IMAGE_FDS_NAME);
            cachedImageHeapOffsets = CGlobalDataFactory.forApplicationLayerSymbol(CACHED_IMAGE_HEAP_OFFSETS_NAME);
            cachedImageHeapRelocations = CGlobalDataFactory.forApplicationLayerSymbol(CACHED_IMAGE_HEAP_RELOCATIONS_NAME);
        } else if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            cachedImageFDs = CGlobalDataFactory.createBytes(() -> createWords(DynamicImageLayerInfo.singleton().numLayers, UNASSIGNED_FD), CACHED_IMAGE_FDS_NAME);
            cachedImageHeapOffsets = CGlobalDataFactory.createBytes(() -> createWords(DynamicImageLayerInfo.singleton().numLayers, Word.zero()), CACHED_IMAGE_HEAP_OFFSETS_NAME);
            cachedImageHeapRelocations = CGlobalDataFactory.createBytes(() -> createWords(DynamicImageLayerInfo.singleton().numLayers, Word.zero()), CACHED_IMAGE_HEAP_RELOCATIONS_NAME);
            AppLayerCGlobalTracking appLayerTracking = CGlobalDataFeature.singleton().getAppLayerCGlobalTracking();
            appLayerTracking.registerCGlobalWithPriorLayerReference(cachedImageFDs);
            appLayerTracking.registerCGlobalWithPriorLayerReference(cachedImageHeapOffsets);
            appLayerTracking.registerCGlobalWithPriorLayerReference(cachedImageHeapRelocations);
        } else {
            cachedImageFDs = null;
            cachedImageHeapOffsets = null;
            cachedImageHeapRelocations = null;
        }

        CGlobalData<CCharPointer> nextLayerSymbolNameData = null;
        if (ImageLayerBuildingSupport.buildingSharedLayer() && Platform.includedIn(Platform.WINDOWS.class)) {
            nextLayerSymbolNameData = CGlobalDataFactory.createCString(getLayerName(DynamicImageLayerInfo.singleton().nextLayerNumber));
        }

        ImageSingletons.add(ImageLayerSection.class, new ImageLayerSectionImpl(initialSectionStart, cachedImageFDs, cachedImageHeapOffsets, cachedImageHeapRelocations, nextLayerSymbolNameData));
    }

    private static byte[] createWords(int count, WordBase initialValue) {
        Architecture arch = SubstrateTarget.getArchitecture();
        assert arch.getWordSize() == Long.BYTES : "currently hard-coded for 8 byte words";
        ByteBuffer buffer = ByteBuffer.allocate(count * Long.BYTES).order(arch.getByteOrder());
        for (int i = 0; i < count; i++) {
            buffer.putLong(initialValue.rawValue());
        }
        return buffer.array();
    }

    private static String getLayerName(int layerNumber) {
        return String.format("%s_%s", LAYER_NAME_PREFIX, layerNumber);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(ImageLayerSection.getCachedImageFDs());
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(ImageLayerSection.getCachedImageHeapOffsets());
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(ImageLayerSection.getCachedImageHeapRelocations());
        }
    }

    /**
     * Creates the SVM layer section and define all symbols within the section. Note it is necessary
     * to define symbols in this section before the normal build process to ensure the CGlobals
     * referring to them are able to pick up this symbol and not refer to an undefined symbol.
     */
    public void createSection(ObjectFile objectFile, ImageHeapLayoutInfo heapLayout) {
        /*
         * We need to allocate bytes for the section early. We don't know how large it needs to be
         * yet, so we reserve the maximum that we might need and truncate it later.
         */
        int sectionMaxSize = getVariablySizedDataOffset();

        int numSingletonSlots = ImageSingletons.lookup(LoadImageSingletonFeature.class).getConstantToTableSlotMap().size();
        int referenceSize = ObjectLayout.singleton().getReferenceSize();
        int singletonsTableSize = NumUtil.roundUp(numSingletonSlots * referenceSize, Long.BYTES);
        sectionMaxSize += Long.BYTES + singletonsTableSize;

        /* For now, reserve bitmaps large enough to cover the entire relocatables heap partition. */
        int relocsBitmapMaxSize = NumUtil.divideAndRoundUp(NumUtil.safeToInt(heapLayout.getReadOnlyRelocatableSize()), Long.BYTES);
        sectionMaxSize += Long.BYTES + relocsBitmapMaxSize; // offsets to patch
        sectionMaxSize += Long.BYTES + relocsBitmapMaxSize; // addresses to patch

        /* Account for the length of the image heap reference and field update arrays. */
        sectionMaxSize += 2 * Long.BYTES;
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * Patches for object references in image heaps of other layers. For a description of
             * the table layout, see CrossLayerConstantRegistryFeature#generateRelocationPatchArray.
             */
            sectionMaxSize += Integer.BYTES * CrossLayerConstantRegistryFeature.singleton().computeRelocationPatchesLength();
            /*
             * Patches for updated fields. For a description of the table layout see
             * CrossLayerFieldUpdatersFeature#generateUpdatePatchArray.
             */
            int patchesLength = CrossLayerFieldUpdaterFeature.singleton().computeUpdatePatchesLength();
            if (patchesLength > 0) {
                // When the patch array is non-zero, then we must also store the header length
                int headerLength = Integer.BYTES;
                sectionMaxSize += headerLength + patchesLength;
            }
        }

        layeredSectionData = new BasicProgbitsSectionImpl(new byte[sectionMaxSize]);

        // since relocations are present the section it is considered writable
        ObjectFile.Section layeredImageSection = objectFile.newProgbitsSection(SVM_LAYER_SECTION.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), true, false,
                        layeredSectionData);

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            String nextLayerSymbolName = getLayerName(DynamicImageLayerInfo.singleton().nextLayerNumber);
            if (Platform.includedIn(Platform.WINDOWS.class)) {
                /*
                 * PE/COFF needs a link-time definition; runtime patches the initial layer section
                 * with the final application layer section.
                 *
                 * GR-58631: Middle shared layers are rejected above. Add per-layer section
                 * metadata/exporting to patch the full chain.
                 */
                objectFile.createDefinedSymbol(nextLayerSymbolName, layeredImageSection, 0, 0, false, false, false);
            } else {
                // this symbol will be defined in the next layer's layer section
                objectFile.createUndefinedSymbol(nextLayerSymbolName, false);
            }
            layeredSectionData.markRelocationSite(NEXT_SECTION_OFFSET, ObjectFile.RelocationKind.DIRECT_8, nextLayerSymbolName, 0);
        } else {
            /*
             * Note because we provide a byte buffer initialized to zeros nothing needs to be done.
             * Otherwise, the NEXT_SECTION field entry would need to be cleared within the
             * application layer.
             */
        }

        // this symbol must be global when it will be read by the prior section
        boolean layerNameIsGlobal = ImageLayerBuildingSupport.buildingExtensionLayer();
        boolean layerNameIsExported = ImageLayerBuildingSupport.buildingApplicationLayer();
        objectFile.createDefinedSymbol(getLayerName(DynamicImageLayerInfo.getCurrentLayerNumber()), layeredImageSection, 0, 0, false, layerNameIsGlobal, layerNameIsExported);

        if (numSingletonSlots != 0) {
            assert ImageLayerBuildingSupport.buildingApplicationLayer() : "Currently only application layer is supported";
            objectFile.createDefinedSymbol(LoadImageSingletonFeature.CROSS_LAYER_SINGLETON_TABLE_SYMBOL, layeredImageSection, getFirstSingletonOffset(), 0, false, true, true);
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        /*
         * Note this is after all "normal" symbols, such as the heap symbols we use here, have been
         * defined. At this point we can mark all relocations used within this section.
         */
        layeredSectionData.markRelocationSite(HEAP_BEGIN_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_BEGIN_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_END_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_END_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_RELOCATABLE_BEGIN_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_RELOCATABLE_END_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_BEGIN_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_END_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_PATCHED_BEGIN_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_PATCHED_BEGIN_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_PATCHED_END_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_PATCHED_END_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(CODE_START_OFFSET, ObjectFile.RelocationKind.DIRECT_8, NativeImage.getTextSectionStartSymbol(), 0);
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            layeredSectionData.markRelocationSite(PECOFF_FIXUP_TABLE_OFFSET, ObjectFile.RelocationKind.DIRECT_8, PECOFF_FORWARD_SYMBOL_FIXUP_TABLE_SYMBOL_NAME, 0);
        }

        var config = (FeatureImpl.BeforeImageWriteAccessImpl) access;

        Map<JavaConstant, Integer> singletonToSlotMap = ImageSingletons.lookup(LoadImageSingletonFeature.class).getConstantToTableSlotMap();
        long[] singletonTableInfo = new long[singletonToSlotMap.size()];
        int shift = ImageSingletons.lookup(CompressEncoding.class).getShift();
        for (var entry : singletonToSlotMap.entrySet()) {
            var objectInfo = config.getImage().getHeap().getConstantInfo(entry.getKey());
            singletonTableInfo[entry.getValue()] = objectInfo.getOffset() >>> shift;
        }

        ByteBuffer buffer = ByteBuffer.wrap(layeredSectionData.getContent()).order(ByteOrder.LITTLE_ENDIAN);

        int referenceSize = ObjectLayout.singleton().getReferenceSize();
        buffer.position(getVariablySizedDataOffset());
        buffer.putLong(singletonTableInfo.length);
        for (long imageSingletonOffset : singletonTableInfo) {
            if (referenceSize == 4) {
                buffer.putInt(NumUtil.safeToInt(imageSingletonOffset));
            } else {
                assert referenceSize == 8 : referenceSize;
                buffer.putLong(imageSingletonOffset);
            }
        }
        buffer.position(NumUtil.roundUp(buffer.position(), Long.BYTES));

        BitSet offsetsToPatchInHeapRelocs = LayeredDispatchTableFeature.singleton().offsetsToPatchInHeapRelocs;
        assert !ImageLayerBuildingSupport.buildingInitialLayer() || offsetsToPatchInHeapRelocs.isEmpty() : "should have no offsets to patch";
        putBitmap(buffer, offsetsToPatchInHeapRelocs);

        BitSet addressesToPatchInHeapRelocs = LayeredDispatchTableFeature.singleton().addressesToPatchInHeapRelocs;
        putBitmap(buffer, addressesToPatchInHeapRelocs);

        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * Currently we place the heap relocation patches exclusively in the application layer.
             *
             * Note the patch information is always written as an array of ints regardless of the
             * reference size. This is allowed because we require the image heap to be less than 2GB
             * in size.
             *
             * See CrossLayerConstantRegistryFeature#generateRelocationPatchArray for a thorough
             * description of the patching table layout.
             */
            int[] relocationPatches = CrossLayerConstantRegistryFeature.singleton().getRelocationPatches();
            buffer.putLong(relocationPatches.length);
            for (int value : relocationPatches) {
                buffer.putInt(value);
            }

            /*
             * Currently we place field update patches exclusively in the application layer.
             *
             * See CrossLayerFieldUpdatersFeature#generateUpdatePatchArray for a thorough
             * description of the field update patch info layout.
             */
            byte[] fieldUpdatePatches = CrossLayerFieldUpdaterFeature.singleton().getUpdatePatches();
            buffer.putLong(fieldUpdatePatches.length);
            if (fieldUpdatePatches.length != 0) {
                buffer.putInt(CrossLayerFieldUpdaterFeature.singleton().getHeaderSize());
                buffer.put(fieldUpdatePatches);
            }
        } else {
            buffer.putLong(0); // no image heap reference patches
            buffer.putLong(0); // no image heap field update patches
        }

        int size = buffer.position();
        byte[] truncated = new byte[size];
        buffer.get(0, truncated, 0, truncated.length);
        layeredSectionData.setContent(truncated);

        if (SubstrateUtil.assertionsEnabled()) {
            Arrays.fill(buffer.array(), (byte) 0xfe); // poison original array in case it escaped
        }
    }

    private static void putBitmap(ByteBuffer buffer, BitSet bitset) {
        assert buffer.order() == ByteOrder.LITTLE_ENDIAN;
        byte[] bitmap = bitset.toByteArray();
        int longsCount = NumUtil.divideAndRoundUp(bitmap.length, Long.BYTES);
        buffer.putLong(longsCount);
        buffer.put(buffer.position(), bitmap);
        buffer.position(buffer.position() + longsCount * Long.BYTES);
    }

    /**
     * On PE/COFF, the runtime needs a fixed slot for the forward-symbol fixup table pointer. Keep
     * the common fixed entries unchanged and place the Windows-only slot at the first otherwise
     * variably-sized-data word.
     */
    private static int getVariablySizedDataOffset() {
        return Platform.includedIn(Platform.WINDOWS.class) ? VARIABLY_SIZED_DATA_OFFSET + Long.BYTES : VARIABLY_SIZED_DATA_OFFSET;
    }

    private static int getFirstSingletonOffset() {
        return getVariablySizedDataOffset() + Long.BYTES;
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
    private static class ImageLayerSectionImpl extends ImageLayerSection {

        ImageLayerSectionImpl(CGlobalData<Pointer> initialSectionStart, CGlobalData<WordPointer> cachedImageFDs, CGlobalData<WordPointer> cachedImageHeapOffsets,
                        CGlobalData<WordPointer> cachedImageHeapRelocations, CGlobalData<CCharPointer> nextLayerSectionSymbolName) {
            super(initialSectionStart, cachedImageFDs, cachedImageHeapOffsets, cachedImageHeapRelocations, nextLayerSectionSymbolName);
        }

        @Override
        public int getEntryOffsetInternal(SectionEntries entry) {
            return switch (entry) {
                case HEAP_BEGIN -> HEAP_BEGIN_OFFSET;
                case HEAP_END -> HEAP_END_OFFSET;
                case HEAP_RELOCATABLE_BEGIN -> HEAP_RELOCATABLE_BEGIN_OFFSET;
                case HEAP_RELOCATABLE_END -> HEAP_RELOCATABLE_END_OFFSET;
                case HEAP_WRITEABLE_BEGIN -> HEAP_WRITABLE_BEGIN_OFFSET;
                case HEAP_WRITEABLE_END -> HEAP_WRITABLE_END_OFFSET;
                case HEAP_WRITEABLE_PATCHED_BEGIN -> HEAP_WRITABLE_PATCHED_BEGIN_OFFSET;
                case HEAP_WRITEABLE_PATCHED_END -> HEAP_WRITABLE_PATCHED_END_OFFSET;
                case CODE_START -> CODE_START_OFFSET;
                case NEXT_SECTION -> NEXT_SECTION_OFFSET;
                case FIXUP_TABLE -> PECOFF_FIXUP_TABLE_OFFSET;
                case VARIABLY_SIZED_DATA -> getVariablySizedDataOffset();
                case FIRST_SINGLETON -> getFirstSingletonOffset();
            };
        }
    }
}
