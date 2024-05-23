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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.debug.Assertions;

public class HostedDynamicLayerInfo extends DynamicImageLayerInfo implements LayeredImageSingleton {
    private final Map<Integer, Integer> methodIdToOffsetMap;
    private final CGlobalData<PointerBase> cGlobalData;

    HostedDynamicLayerInfo() {
        this(0, null, new HashMap<>());
    }

    public static HostedDynamicLayerInfo singleton() {
        return (HostedDynamicLayerInfo) ImageSingletons.lookup(DynamicImageLayerInfo.class);
    }

    private HostedDynamicLayerInfo(int layerNumber, String codeSectionStartSymbol, Map<Integer, Integer> methodIdToOffsetMap) {
        super(layerNumber);
        this.methodIdToOffsetMap = methodIdToOffsetMap;
        cGlobalData = codeSectionStartSymbol == null ? null : CGlobalDataFactory.forSymbol(codeSectionStartSymbol);
    }

    @Override
    public Pair<CGlobalDataInfo, Integer> getPriorLayerMethodLocation(SharedMethod sMethod) {
        assert ImageLayerBuildingSupport.buildingExtensionLayer() : "This should only be called within extension images. Within the initial layer the direct calls can be performed";
        HostedMethod method = (HostedMethod) sMethod;
        assert method.wrapped.isInBaseLayer() && methodIdToOffsetMap.containsKey(method.getWrapped().getId()) : method;

        var basePointer = CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalData);
        var offset = methodIdToOffsetMap.get(method.getWrapped().getId());
        return Pair.create(basePointer, offset);
    }

    void registerOffset(HostedMethod method) {
        int offset = method.getCodeAddressOffset();
        int methodID = method.getWrapped().getId();

        assert !methodIdToOffsetMap.containsKey(methodID) : Assertions.errorMessage("Duplicate entry", methodID, offset);
        methodIdToOffsetMap.put(methodID, offset);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    /**
     * Verifies each method has been mapped to a unique offset.
     */
    boolean verifyUniqueOffsets(Collection<? extends SharedMethod> methods) {
        BitSet seenOffsets = new BitSet();
        for (var entry : methodIdToOffsetMap.entrySet()) {
            if (seenOffsets.get(entry.getValue())) {
                var method = methods.stream().filter(m -> ((HostedMethod) m).getWrapped().getId() == entry.getKey()).findAny();
                assert false : Assertions.errorMessage("Value has already been found", method, entry.getKey(), entry.getValue());
            }

            seenOffsets.set(entry.getValue());
        }

        return true;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        /*
         * When there are multiple shared layers we will need to store the starting code offset of
         * each layer.
         */
        assert ImageLayerBuildingSupport.buildingInitialLayer() : "This code must be adjusted to support multiple shared layers";

        /*
         * First write out next layer number.
         */
        writer.writeInt("nextLayerNumber", nextLayerNumber);

        /*
         * Next write the start of the code section
         */
        writer.writeString("codeSectionStartSymbol", NativeImage.getTextSectionStartSymbol());

        /*
         * Write out all method offsets.
         */
        List<Integer> offsets = new ArrayList<>(methodIdToOffsetMap.size());
        List<Integer> methodIDs = new ArrayList<>(methodIdToOffsetMap.size());
        methodIdToOffsetMap.forEach((key, value) -> {
            methodIDs.add(key);
            offsets.add(value);
        });
        writer.writeIntList("methodIDs", methodIDs);
        writer.writeIntList("offsets", offsets);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        assert loader.readIntList("offsets").size() == loader.readIntList("methodIDs").size() : Assertions.errorMessage("Offsets and methodIDs are incompatible", loader.readIntList("offsets"),
                        loader.readIntList("methodIDs"));

        int layerNumber = loader.readInt("nextLayerNumber");

        String codeSectionStartSymbol = loader.readString("codeSectionStartSymbol");

        /*
         * Load the offsets of all methods in the prior layers.
         */
        var offsets = loader.readIntList("offsets").iterator();
        var methodIDs = loader.readIntList("methodIDs").iterator();
        Map<Integer, Integer> initialMethodIdToOffsetMap = new HashMap<>();

        while (offsets.hasNext()) {
            int methodId = methodIDs.next();
            int offset = offsets.next();
            initialMethodIdToOffsetMap.put(methodId, offset);
        }

        return new HostedDynamicLayerInfo(layerNumber, codeSectionStartSymbol, initialMethodIdToOffsetMap);
    }
}

@AutomaticallyRegisteredFeature
class HostedDynamicLayerInfoFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(DynamicImageLayerInfo.class, new HostedDynamicLayerInfo());
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        /*
         * Store all compiled method offsets into the singleton.
         */

        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            // This is the last layer; no need to store anything
            return;
        }

        var config = (FeatureImpl.AfterCompilationAccessImpl) access;

        assert HostedDynamicLayerInfo.singleton().verifyUniqueOffsets(config.getMethods());

        for (var entry : config.getCodeCache().getOrderedCompilations()) {
            HostedDynamicLayerInfo.singleton().registerOffset(entry.getLeft());
        }
    }
}
