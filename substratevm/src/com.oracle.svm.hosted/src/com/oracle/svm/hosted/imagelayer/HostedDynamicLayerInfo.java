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
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
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
import com.oracle.svm.hosted.meta.HostedMethodNameFactory.MethodNameInfo;

import jdk.graal.compiler.debug.Assertions;

public class HostedDynamicLayerInfo extends DynamicImageLayerInfo implements LayeredImageSingleton {
    private final Map<Integer, Integer> methodIdToOffsetMap;
    private final ConcurrentHashMap<Integer, MethodNameInfo> methodIdToNameInfoMap;
    private final CGlobalData<PointerBase> cGlobalData;
    private final Set<String> priorLayerMethodSymbols = new HashSet<>();
    private final List<String> libNames;
    private boolean persisted = false;

    HostedDynamicLayerInfo() {
        this(0, null, new HashMap<>(), new ConcurrentHashMap<>(), new ArrayList<>());
    }

    public static HostedDynamicLayerInfo singleton() {
        return (HostedDynamicLayerInfo) ImageSingletons.lookup(DynamicImageLayerInfo.class);
    }

    private HostedDynamicLayerInfo(int layerNumber, String codeSectionStartSymbol, Map<Integer, Integer> methodIdToOffsetMap, ConcurrentHashMap<Integer, MethodNameInfo> methodIdToNameInfoMap,
                    List<String> libNames) {
        super(layerNumber);
        this.methodIdToOffsetMap = methodIdToOffsetMap;
        this.methodIdToNameInfoMap = methodIdToNameInfoMap;
        this.libNames = new ArrayList<>(libNames);
        this.cGlobalData = codeSectionStartSymbol == null ? null : CGlobalDataFactory.forSymbol(codeSectionStartSymbol);
    }

    @Override
    public PriorLayerMethodLocation getPriorLayerMethodLocation(SharedMethod sMethod) {
        assert ImageLayerBuildingSupport.buildingExtensionLayer() : "This should only be called within extension images. Within the initial layer the direct calls can be performed";
        HostedMethod method = (HostedMethod) sMethod;
        assert method.wrapped.isInBaseLayer() && methodIdToOffsetMap.containsKey(method.getWrapped().getId()) : method;

        var basePointer = CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalData);
        var offset = methodIdToOffsetMap.get(method.getWrapped().getId());
        return new PriorLayerMethodLocation(basePointer, offset);
    }

    public boolean compiledInPriorLayer(AnalysisMethod aMethod) {
        assert !BuildPhaseProvider.isCompileQueueFinished();
        return methodIdToOffsetMap.containsKey(aMethod.getId());
    }

    public MethodNameInfo loadMethodNameInfo(AnalysisMethod method) {
        return methodIdToNameInfoMap.get(method.getId());
    }

    public void recordPersistedMethod(HostedMethod hMethod) {
        assert !persisted : "Too late to record this information";
        MethodNameInfo info = new MethodNameInfo(hMethod.getName(), hMethod.getUniqueShortName());
        var prev = methodIdToNameInfoMap.put(hMethod.getWrapped().getId(), info);
        // will have to change for multiple layers
        assert prev == null : prev;
    }

    public Set<String> getReservedNames() {
        return methodIdToNameInfoMap.values().stream().map(MethodNameInfo::uniqueShortName).collect(Collectors.toUnmodifiableSet());
    }

    void registerCompilation(HostedMethod method) {
        assert BuildPhaseProvider.isCompileQueueFinished();
        int offset = method.getCodeAddressOffset();
        int methodID = method.getWrapped().getId();

        assert !methodIdToOffsetMap.containsKey(methodID) : Assertions.errorMessage("Duplicate entry", methodID, offset);
        methodIdToOffsetMap.put(methodID, offset);
    }

    public void registerHostedMethod(HostedMethod hMethod) {
        assert !BuildPhaseProvider.isHostedUniverseBuilt();
        AnalysisMethod aMethod = hMethod.getWrapped();
        if (compiledInPriorLayer(aMethod)) {
            assert aMethod.isInBaseLayer() : hMethod;
            priorLayerMethodSymbols.add(localSymbolNameForMethod(hMethod));
            hMethod.setCompiledInPriorLayer();
        }
    }

    public void defineSymbolsForPriorLayerMethods(ObjectFile objectFile) {
        assert BuildPhaseProvider.isHeapLayoutFinished();
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
        persisted = true;
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

        /*
         * Write out all method offsets.
         */
        List<Integer> offsets = new ArrayList<>(methodIdToOffsetMap.size());
        List<Integer> methodOffsetIds = new ArrayList<>(methodIdToOffsetMap.size());
        methodIdToOffsetMap.forEach((key, value) -> {
            methodOffsetIds.add(key);
            offsets.add(value);
        });
        writer.writeIntList("methodOffsetIDs", methodOffsetIds);
        writer.writeIntList("offsets", offsets);

        /*
         * Write out all persisted method names
         */
        List<Integer> methodNameIds = new ArrayList<>(methodIdToNameInfoMap.size());
        List<String> names = new ArrayList<>(methodIdToNameInfoMap.size() * 2);
        methodIdToNameInfoMap.forEach((key, value) -> {
            methodNameIds.add(key);
            names.add(value.name());
            names.add(value.uniqueShortName());
        });
        writer.writeIntList("methodNameIDs", methodNameIds);
        writer.writeStringList("names", names);

        writer.writeStringList("libNames", libNames);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        assert loader.readIntList("offsets").size() == loader.readIntList("methodOffsetIDs").size() : Assertions.errorMessage("Offsets and methodIDs are incompatible", loader.readIntList("offsets"),
                        loader.readIntList("methodIDs"));

        var snapshotReader = ((SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl) loader).getSnapshotReader();
        int layerNumber = snapshotReader.getNextLayerNumber();

        String codeSectionStartSymbol = loader.readString("codeSectionStartSymbol");

        /*
         * Load the offsets of all methods in the prior layers.
         */
        var offsets = loader.readIntList("offsets").iterator();
        var methodOffsetIds = loader.readIntList("methodOffsetIDs").iterator();
        Map<Integer, Integer> initialMethodIdToOffsetMap = new HashMap<>();

        while (offsets.hasNext()) {
            int methodId = methodOffsetIds.next();
            int offset = offsets.next();
            var prev = initialMethodIdToOffsetMap.put(methodId, offset);
            assert prev == null;
        }

        /*
         * Load the names of all methods in the prior layers.
         */
        var names = loader.readStringList("names").iterator();
        var methodNameIds = loader.readIntList("methodNameIDs").iterator();
        ConcurrentHashMap<Integer, MethodNameInfo> initialMethodIdToMethodNameMap = new ConcurrentHashMap<>();

        while (methodNameIds.hasNext()) {
            int methodId = methodNameIds.next();
            String name = names.next();
            String uniqueShortName = names.next();
            var prev = initialMethodIdToMethodNameMap.put(methodId, new MethodNameInfo(name, uniqueShortName));
            assert prev == null;
        }

        var libNames = loader.readStringList("libNames");

        return new HostedDynamicLayerInfo(layerNumber, codeSectionStartSymbol, initialMethodIdToOffsetMap, initialMethodIdToMethodNameMap, libNames);
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
            HostedDynamicLayerInfo.singleton().registerCompilation(entry.getLeft());
        }
    }
}
