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

import static org.graalvm.word.WordFactory.signed;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfoFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.code.ObjectFileTransformer;

import jdk.vm.ci.code.Architecture;

/**
 * Creates a section with image information specific to the current layer.
 *
 * Presently the layout of this section is as follows:
 *
 * <pre>
 *  --------------------------------------------------------
 *  |  byte offset | name                                  |
 *  |  0           | heap begin                            |
 *  |  8           | heap end                              |
 *  |  16          | heap relocatable begin                |
 *  |  24          | heap relocatable end                  |
 *  |  32          | heap any relocatable pointer          |
 *  |  40          | heap writable begin                   |
 *  |  48          | heap writable end                     |
 *  |  56          | next layer section (0 if final layer) |
 *  --------------------------------------------------------
 * </pre>
 */
@AutomaticallyRegisteredFeature
public final class ImageLayerSectionFeature implements InternalFeature, FeatureSingleton, UnsavedSingleton, ObjectFileTransformer {
    private static final SectionName SVM_LAYER_SECTION = new SectionName.ProgbitsSectionName("svm_layer");

    private static final int HEAP_BEGIN_OFFSET = 0;
    private static final int HEAP_END_OFFSET = 8;
    private static final int HEAP_RELOCATABLE_BEGIN_OFFSET = 16;
    private static final int HEAP_RELOCATABLE_END_OFFSET = 24;
    private static final int HEAP_ANY_RELOCATABLE_POINTER_OFFSET = 32;
    private static final int HEAP_WRITABLE_BEGIN_OFFSET = 40;
    private static final int HEAP_WRITABLE_END_OFFSET = 48;
    private static final int NEXT_SECTION_OFFSET = 56;
    private static final int SECTION_SIZE = 64;

    private static final String CACHED_IMAGE_FDS_NAME = "__svm_layer_cached_image_fds";
    private static final String CACHED_IMAGE_HEAP_OFFSETS_NAME = "__svm_layer_cached_image_heap_offsets";

    private static final SignedWord UNASSIGNED_FD = signed(-1);

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(DynamicImageLayerInfoFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ImageLayerSection.class, createImageLayerSection());
        ImageSingletons.add(ObjectFileTransformer.class, this);
    }

    private static byte[] createWords(int count, WordBase initialValue) {
        Architecture arch = ConfigurationValues.getTarget().arch;
        assert arch.getWordSize() == Long.BYTES : "currently hard-coded for 8 byte words";
        ByteBuffer buffer = ByteBuffer.allocate(count * Long.BYTES).order(arch.getByteOrder());
        for (int i = 0; i < count; i++) {
            buffer.putLong(initialValue.rawValue());
        }
        return buffer.array();
    }

    private static String getLayerName(int layerNumber) {
        return String.format("__svm_layer_%s", layerNumber);
    }

    private static ImageLayerSectionImpl createImageLayerSection() {
        CGlobalData<Pointer> initialSectionStart = ImageLayerBuildingSupport.buildingInitialLayer() ? CGlobalDataFactory.forSymbol(getLayerName(DynamicImageLayerInfo.singleton().layerNumber)) : null;
        CGlobalData<WordPointer> cachedImageFDs;
        CGlobalData<WordPointer> cachedImageHeapOffsets;

        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            cachedImageFDs = CGlobalDataFactory.forSymbol(CACHED_IMAGE_FDS_NAME);
            cachedImageHeapOffsets = CGlobalDataFactory.forSymbol(CACHED_IMAGE_HEAP_OFFSETS_NAME);
        } else if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            cachedImageFDs = CGlobalDataFactory.createBytes(() -> createWords(DynamicImageLayerInfo.singleton().numLayers, UNASSIGNED_FD), CACHED_IMAGE_FDS_NAME);
            cachedImageHeapOffsets = CGlobalDataFactory.createBytes(() -> createWords(DynamicImageLayerInfo.singleton().numLayers, WordFactory.zero()), CACHED_IMAGE_HEAP_OFFSETS_NAME);
        } else {
            cachedImageFDs = null;
            cachedImageHeapOffsets = null;
        }

        return new ImageLayerSectionImpl(initialSectionStart, cachedImageFDs, cachedImageHeapOffsets);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(ImageLayerSection.getCachedImageFDs());
            CGlobalDataFeature.singleton().registerWithGlobalSymbol(ImageLayerSection.getCachedImageHeapOffsets());
        }
    }

    private ObjectFile.ProgbitsSectionImpl layeredSectionData;

    /**
     * Creates the SVM layer section and define all symbols within the section. Note it is necessary
     * to define symbols in this section before the normal build process to ensure the CGlobals
     * referring to them are able to pick up this symbol and not refer to an undefined symbol.
     */
    @Override
    public void afterAbstractImageCreation(ObjectFile objectFile) {
        byte[] sectionBytes = new byte[SECTION_SIZE];
        layeredSectionData = new BasicProgbitsSectionImpl(sectionBytes);

        // since relocations are present the section it is considered writable
        ObjectFile.Section layeredImageSection = objectFile.newProgbitsSection(SVM_LAYER_SECTION.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), true, false,
                        layeredSectionData);

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            String nextLayerSymbolName = getLayerName(DynamicImageLayerInfo.singleton().nextLayerNumber);
            // this symbol will be defined in the next layer's layer section
            objectFile.createUndefinedSymbol(nextLayerSymbolName, 0, false);
            layeredSectionData.markRelocationSite(NEXT_SECTION_OFFSET, ObjectFile.RelocationKind.DIRECT_8, nextLayerSymbolName, 0);
        } else {
            /*
             * Note because we provide a byte buffer initialized to zeros nothing needs to be done.
             * Otherwise, the NEXT_SECTION field entry would need to be cleared within the
             * application layer.
             */
        }

        // this symbol must be global when it will be read by the prior section
        objectFile.createDefinedSymbol(getLayerName(DynamicImageLayerInfo.singleton().layerNumber), layeredImageSection, 0, 0, false, ImageLayerBuildingSupport.buildingExtensionLayer());
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
        layeredSectionData.markRelocationSite(HEAP_ANY_RELOCATABLE_POINTER_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_BEGIN_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME, 0);
        layeredSectionData.markRelocationSite(HEAP_WRITABLE_END_OFFSET, ObjectFile.RelocationKind.DIRECT_8, Isolates.IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME, 0);
    }

    private static class ImageLayerSectionImpl extends ImageLayerSection implements UnsavedSingleton {

        ImageLayerSectionImpl(CGlobalData<Pointer> initialSectionStart, CGlobalData<WordPointer> cachedImageFDs, CGlobalData<WordPointer> cachedImageHeapOffsets) {
            super(initialSectionStart, cachedImageFDs, cachedImageHeapOffsets);
        }

        @Override
        public int getEntryOffsetInternal(SectionEntries entry) {
            return switch (entry) {
                case HEAP_BEGIN -> HEAP_BEGIN_OFFSET;
                case HEAP_END -> HEAP_END_OFFSET;
                case HEAP_RELOCATABLE_BEGIN -> HEAP_RELOCATABLE_BEGIN_OFFSET;
                case HEAP_RELOCATABLE_END -> HEAP_RELOCATABLE_END_OFFSET;
                case HEAP_ANY_RELOCATABLE_POINTER -> HEAP_ANY_RELOCATABLE_POINTER_OFFSET;
                case HEAP_WRITEABLE_BEGIN -> HEAP_WRITABLE_BEGIN_OFFSET;
                case HEAP_WRITEABLE_END -> HEAP_WRITABLE_END_OFFSET;
                case NEXT_SECTION -> NEXT_SECTION_OFFSET;
            };
        }

        @Override
        public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
            return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
        }
    }
}
