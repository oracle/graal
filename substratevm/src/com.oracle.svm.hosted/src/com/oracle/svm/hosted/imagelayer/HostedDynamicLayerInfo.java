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

import static com.oracle.svm.hosted.image.NativeImage.localSymbolNameForMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedMethodNameFactory.MethodNameInfo;

@AutomaticallyRegisteredImageSingleton(value = DynamicImageLayerInfo.class, onlyWith = BuildingImageLayerPredicate.class)
public class HostedDynamicLayerInfo extends DynamicImageLayerInfo implements LayeredImageSingleton {
    private final CGlobalData<PointerBase> cGlobalData;
    private final Set<String> priorLayerMethodSymbols = new HashSet<>();
    private final List<String> libNames;
    private final Map<AnalysisMethod, Integer> priorInstalledOffsetCache = ImageLayerBuildingSupport.buildingExtensionLayer() ? new ConcurrentHashMap<>() : null;

    HostedDynamicLayerInfo() {
        this(0, null, new ArrayList<>());
    }

    public static HostedDynamicLayerInfo singleton() {
        return (HostedDynamicLayerInfo) ImageSingletons.lookup(DynamicImageLayerInfo.class);
    }

    private HostedDynamicLayerInfo(int layerNumber, String codeSectionStartSymbol,
                    List<String> libNames) {
        super(layerNumber);
        this.libNames = new ArrayList<>(libNames);
        this.cGlobalData = codeSectionStartSymbol == null ? null : CGlobalDataFactory.forSymbol(codeSectionStartSymbol);
    }

    @Override
    public PriorLayerMethodLocation getPriorLayerMethodLocation(SharedMethod sMethod) {
        assert ImageLayerBuildingSupport.buildingExtensionLayer() : "This should only be called within extension images. Within the initial layer the direct calls can be performed";
        HostedMethod hMethod = (HostedMethod) sMethod;
        int compiledOffset = getPriorInstalledOffset(hMethod.getWrapped());
        assert hMethod.wrapped.isInBaseLayer() && compiledOffset != HostedMethod.INVALID_CODE_ADDRESS_OFFSET;

        var basePointer = CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalData);
        return new PriorLayerMethodLocation(basePointer, compiledOffset);
    }

    public boolean compiledInPriorLayer(AnalysisMethod aMethod) {
        assert !BuildPhaseProvider.isCompileQueueFinished();
        return getPriorInstalledOffset(aMethod) != HostedMethod.INVALID_CODE_ADDRESS_OFFSET;
    }

    private int getPriorInstalledOffset(AnalysisMethod aMethod) {
        if (aMethod.isInBaseLayer()) {
            return priorInstalledOffsetCache.computeIfAbsent(aMethod, method -> {
                var methodData = HostedImageLayerBuildingSupport.singleton().getLoader();
                return methodData.getHostedMethodData(aMethod).getInstalledOffset();
            });
        } else {
            return HostedMethod.INVALID_CODE_ADDRESS_OFFSET;
        }
    }

    public static MethodNameInfo loadMethodNameInfo(AnalysisMethod aMethod) {
        if (aMethod.isInBaseLayer()) {
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            var methodData = loader.getHostedMethodData(aMethod);
            return new MethodNameInfo(methodData.getHostedMethodName().toString(), methodData.getHostedMethodUniqueName().toString());
        } else {
            return null;
        }
    }

    public Set<String> getReservedNames() {
        /*
         * Note we only need to ensure method names for persisted analysis methods are reserved.
         */
        Set<String> reservedNames = new HashSet<>();
        var methods = HostedImageLayerBuildingSupport.singleton().getLoader().getHostedMethods();
        for (var methodData : methods) {
            if (methodData.getMethodId() != LayeredDispatchTableFeature.PriorDispatchMethod.UNPERSISTED_METHOD_ID) {
                reservedNames.add(methodData.getHostedMethodUniqueName().toString());
            }
        }
        return Collections.unmodifiableSet(reservedNames);
    }

    public void registerHostedMethod(HostedMethod hMethod) {
        assert !BuildPhaseProvider.isHostedUniverseBuilt();
        AnalysisMethod aMethod = hMethod.getWrapped();
        if (compiledInPriorLayer(aMethod)) {
            assert aMethod.isInBaseLayer() : hMethod;
            priorLayerMethodSymbols.add(localSymbolNameForMethod(hMethod));
            hMethod.setCompiledInPriorLayer();
            hMethod.setCodeAddressOffset(getPriorInstalledOffset(aMethod));
        }
    }

    public void defineSymbolsForPriorLayerMethods(ObjectFile objectFile) {
        assert BuildPhaseProvider.isHeapLayoutFinished();
        /*
         * In vtables, we can typically reference methods from the initial layer via their known
         * offsets from the code base, without using symbols. Only in some cases, such as
         * CFunctionPointer/MethodPointer, we still use symbols. Therefore, not all these symbol
         * entries are needed, but the command-line linker should remove any unnecessary ones.
         */
        priorLayerMethodSymbols.forEach(symbol -> objectFile.createUndefinedSymbol(symbol, 0, true));
    }

    public void registerLibName(String lib) {
        libNames.add(lib);
    }

    public boolean isImageLayerLib(String lib) {
        return libNames.contains(lib);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
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
        var snapshotBuilder = ((SVMImageLayerWriter.ImageSingletonWriterImpl) writer).getSnapshotBuilder();
        snapshotBuilder.setNextLayerNumber(nextLayerNumber);

        /*
         * Next write the start of the code section
         */
        writer.writeString("codeSectionStartSymbol", NativeImage.getTextSectionStartSymbol());

        writer.writeStringList("libNames", libNames);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {

        var snapshotReader = ((SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl) loader).getSnapshotReader();
        int layerNumber = snapshotReader.getNextLayerNumber();

        String codeSectionStartSymbol = loader.readString("codeSectionStartSymbol");

        var libNames = loader.readStringList("libNames");

        return new HostedDynamicLayerInfo(layerNumber, codeSectionStartSymbol, libNames);
    }
}
