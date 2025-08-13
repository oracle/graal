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

import static com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistryFeature.INVALID;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.PriorLayerMarker;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.heap.ImageHeapObjectAdder;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaConstant;

@AutomaticallyRegisteredFeature
public class CrossLayerConstantRegistryFeature implements InternalFeature, FeatureSingleton, CrossLayerConstantRegistry {
    static final int INVALID = -1;
    private static final Object NULL_CONSTANT_MARKER = new Object();

    private record FutureConstantCandidateInfo(ImageHeapRelocatableConstant constant) {
    }

    ImageLayerIdTrackingSingleton tracker;
    boolean candidateRegistrySealed = false;
    boolean patchingSealed = false;
    SVMImageLayerLoader loader;

    Map<String, Object> constantCandidates;
    Map<String, Object> requiredConstants;
    Map<String, Object> finalizedFutureConstants;

    private int[] relocationPatches;
    int relocationPatchesLength = -1;

    public static CrossLayerConstantRegistryFeature singleton() {
        return (CrossLayerConstantRegistryFeature) ImageSingletons.lookup(CrossLayerConstantRegistry.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            tracker = new ImageLayerIdTrackingSingleton();
            ImageSingletons.add(ImageLayerIdTrackingSingleton.class, tracker);
        } else {
            tracker = ImageSingletons.lookup(ImageLayerIdTrackingSingleton.class);
        }

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            constantCandidates = new ConcurrentHashMap<>();
            requiredConstants = ObservableImageHeapMapProvider.create();
        } else {
            constantCandidates = Map.of();
            requiredConstants = Map.of();
        }
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            finalizedFutureConstants = ObservableImageHeapMapProvider.create();
        } else {
            finalizedFutureConstants = Map.of();
        }
        ImageSingletons.add(CrossLayerConstantRegistry.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        var config = (FeatureImpl.DuringSetupAccessImpl) access;
        loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        ImageHeapObjectAdder.singleton().registerObjectAdder(this::addInitialObjects);
        var registry = CrossLayerConstantRegistry.singletonOrNull();
        config.registerObjectToConstantReplacer(obj -> replacePriorMarkersWithConstant(registry, obj));
    }

    /**
     * Replace prior layer markers with {@link ImageHeapConstant}s.
     */
    ImageHeapConstant replacePriorMarkersWithConstant(CrossLayerConstantRegistry registry, Object object) {
        if (object instanceof PriorLayerMarker priorLayerMarker) {
            return (ImageHeapConstant) registry.getConstant(priorLayerMarker.getKey());
        }

        return null;
    }

    private void addInitialObjects(NativeImageHeap heap, HostedUniverse hUniverse) {
        String addReason = "Registered as a required heap constant within the CrossLayerConstantRegistry";

        for (Object constant : requiredConstants.values()) {
            ImageHeapConstant singletonConstant = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(constant);
            heap.addConstant(singletonConstant, false, addReason);
        }

        for (Object futureConstant : finalizedFutureConstants.values()) {
            /*
             * Note we could optimize this to only add the object in the application layer if it was
             * used by a prior layer. However, currently it is not worth introducing this
             * complication to the code.
             */
            JavaConstant singletonConstant = futureConstant == NULL_CONSTANT_MARKER ? JavaConstant.NULL_POINTER : (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(futureConstant);
            heap.addConstant(singletonConstant, false, addReason);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        candidateRegistrySealed = true;

        /*
         * Register all future constants seen in this layer.
         */
        constantCandidates.entrySet().stream().filter(e -> e.getValue() instanceof FutureConstantCandidateInfo).forEach(entry -> {
            String key = entry.getKey();
            FutureConstantCandidateInfo futureConstant = (FutureConstantCandidateInfo) entry.getValue();
            var constant = futureConstant.constant();
            AnalysisType type = constant.getType();
            tracker.registerFutureTrackingInfo(new FutureTrackingInfo(key, FutureTrackingInfo.State.Type, type.getId(), INVALID));

        });
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        patchingSealed = true;
        var config = (FeatureImpl.BeforeImageWriteAccessImpl) access;
        var heap = config.getImage().getHeap();
        var snippetReflection = config.getHostedUniverse().getSnippetReflection();

        /*
         * Register constant candidates installed in this layer.
         */
        constantCandidates.entrySet().stream().filter(e -> !(e.getValue() instanceof FutureConstantCandidateInfo)).forEach(entry -> {
            Object object = entry.getValue();
            var optional = config.getHostedMetaAccess().optionalLookupJavaType(object.getClass());
            if (optional.isPresent()) {
                var constant = (ImageHeapConstant) snippetReflection.forObject(object);
                var objectInfo = heap.getConstantInfo(constant);
                if (objectInfo != null && objectInfo.getOffset() >= 0) {
                    int id = ImageHeapConstant.getConstantID(constant);
                    tracker.registerPriorTrackingInfo(entry.getKey(), id);
                }
            }
        });

        assert verifyConstantsInstalled(config);

        /*
         * Record the finalized constants in the tracker
         */
        for (var entry : finalizedFutureConstants.entrySet()) {
            // We know these constants have been installed via addInitialObjects
            Object value = entry.getValue();
            if (value == NULL_CONSTANT_MARKER) {
                FutureTrackingInfo info = (FutureTrackingInfo) tracker.getTrackingInfo(entry.getKey());
                tracker.updateFutureTrackingInfo(new FutureTrackingInfo(info.key(), FutureTrackingInfo.State.Final, INVALID, INVALID));
            } else {
                var futureConstant = (ImageHeapConstant) snippetReflection.forObject(value);
                var objectInfo = heap.getConstantInfo(futureConstant);
                int id = ImageHeapConstant.getConstantID(futureConstant);
                FutureTrackingInfo info = (FutureTrackingInfo) tracker.getTrackingInfo(entry.getKey());
                tracker.updateFutureTrackingInfo(new FutureTrackingInfo(info.key(), FutureTrackingInfo.State.Final, id, NumUtil.safeToInt(objectInfo.getOffset())));
            }
        }

        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            generateRelocationPatchArray();
        }
    }

    /**
     * The relocation patch array contains in integer pairs the (heap offset, reference encoding)
     * which need to be patched. The reference encoding uses the appropriate compress encoding
     * format. Both the heap offset and reference can be stored in 4-byte integers due to the length
     * restrictions of the native-image heap.
     *
     * has the following format:
     *
     * <pre>
     *     ---------------------------------
     *     | offset | description          |
     *     | 0      | length               |
     *     | 4      | heap offset 1        |
     *     | 8      | reference encoding 1 |
     *     | 12     | heap offset 2        |
     *     | 16     | reference encoding 2 |
     *     | <remaining pairs>             |
     *     ---------------------------------
     *
     * </pre>
     *
     * All patching is performed relative to the initial layer's
     * {@link com.oracle.svm.core.Isolates#IMAGE_HEAP_BEGIN}, so we must subtract this offset
     * (relative to the image heap start) away from all offsets to patch.
     */
    private void generateRelocationPatchArray() {
        int shift = ImageSingletons.lookup(CompressEncoding.class).getShift();
        List<Integer> patchArray = new ArrayList<>();
        int heapBeginOffset = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        assert heapBeginOffset >= 0 : "invalid image heap begin offset " + heapBeginOffset;
        for (var entry : tracker.futureKeyToPatchingOffsetsMap.entrySet()) {
            List<Integer> offsetsToPatch = entry.getValue();
            FutureTrackingInfo info = (FutureTrackingInfo) tracker.getTrackingInfo(entry.getKey());
            VMError.guarantee(info.state() == FutureTrackingInfo.State.Final, "Invalid future %s", info);

            int offset = info.offset();
            int referenceEncoding = offset == INVALID ? 0 : offset >>> shift;
            for (int heapOffset : offsetsToPatch) {
                patchArray.add(heapOffset - heapBeginOffset);
                patchArray.add(referenceEncoding);
            }
        }

        relocationPatches = patchArray.stream().mapToInt(Integer::intValue).toArray();
        VMError.guarantee(relocationPatchesLength == relocationPatches.length, "%s %s", relocationPatchesLength, relocationPatches);
    }

    private boolean verifyConstantsInstalled(FeatureImpl.BeforeImageWriteAccessImpl config) {
        var snippetReflection = config.getHostedUniverse().getSnippetReflection();
        var heap = config.getImage().getHeap();

        for (var requiredConstant : requiredConstants.values()) {
            var constant = (ImageHeapConstant) snippetReflection.forObject(requiredConstant);
            var objectInfo = heap.getConstantInfo(constant);
            assert objectInfo != null && objectInfo.getOffset() >= 0 : "Constant is required to be in heap " + requiredConstant;
        }

        return true;
    }

    @Override
    public boolean constantExists(String keyName) {
        return tracker.getTrackingInfo(keyName) != null;
    }

    @Override
    public JavaConstant getConstant(String keyName) {
        TrackingInfo idInfo = tracker.getTrackingInfo(keyName);
        if (idInfo instanceof PriorTrackingInfo prior) {
            return loader.getOrCreateConstant(prior.constantId());
        }

        // Check if a future constant candidate exists
        var obj = constantCandidates.get(keyName);
        if (obj instanceof FutureConstantCandidateInfo info) {
            return info.constant();
        }

        // Retrieve or create constant from FutureTrackingInfo
        if (idInfo instanceof FutureTrackingInfo future) {
            VMError.guarantee(!finalizedFutureConstants.containsKey(keyName), "Future was finalized in this layer: %s", future);

            if (future.loaderId() == INVALID) {
                return JavaConstant.NULL_POINTER;
            }

            if (future.state() != FutureTrackingInfo.State.Type) {
                return loader.getOrCreateConstant(future.loaderId());
            }

            // A constant has not been stored in the heap yet. Create and cache a constant candidate
            FutureConstantCandidateInfo info = (FutureConstantCandidateInfo) constantCandidates.computeIfAbsent(keyName, (k) -> {
                AnalysisType type = loader.getAnalysisTypeForBaseLayerId(future.loaderId());
                return new FutureConstantCandidateInfo(ImageHeapRelocatableConstant.create(type, k));
            });
            return info.constant();
        }

        throw VMError.shouldNotReachHere("Missing key: %s", keyName);
    }

    private void checkCandidateRegistry() {
        VMError.guarantee(!candidateRegistrySealed, "cross layer registry is sealed");
    }

    @Override
    public void registerConstantCandidate(String keyName, Object obj) {
        VMError.guarantee(keyName != null && obj != null, "CrossLayer constants are expected to be non-null. %s %s", keyName, obj);
        checkCandidateRegistry();
        var previous = constantCandidates.putIfAbsent(keyName, obj);
        VMError.guarantee(previous == null && !constantExists(keyName), "This key has been registered before: %s", keyName);
    }

    public boolean isConstantRegistered(Object obj) {
        return constantCandidates.containsValue(obj);
    }

    @Override
    public void registerHeapConstant(String keyName, Object obj) {
        registerConstantCandidate(keyName, obj);
        var previous = requiredConstants.putIfAbsent(keyName, obj);
        VMError.guarantee(previous == null, "This key has been registered before: %s", keyName);
    }

    @Override
    public ImageHeapConstant registerFutureHeapConstant(String keyName, AnalysisType futureType) {
        assert futureType != null;
        var imageHeapConstant = ImageHeapRelocatableConstant.create(futureType, keyName);
        var constantInfo = new FutureConstantCandidateInfo(imageHeapConstant);
        registerConstantCandidate(keyName, constantInfo);
        return constantInfo.constant();
    }

    @Override
    public void finalizeFutureHeapConstant(String keyName, Object obj) {
        checkCandidateRegistry();
        VMError.guarantee(tracker.getTrackingInfo(keyName) instanceof FutureTrackingInfo, "This key was not registered as a future constant %s", keyName);

        Object object = obj == null ? NULL_CONSTANT_MARKER : obj;
        var previous = finalizedFutureConstants.putIfAbsent(keyName, object);
        VMError.guarantee(previous == null, "This key has been registered before: %s", keyName);
    }

    /**
     * Records a spot which at startup time will need to patched to refer to an object defined in a
     * subsequent layer.
     */
    public void markFutureHeapConstantPatchSite(ImageHeapRelocatableConstant constant, int heapOffset) {
        VMError.guarantee(!patchingSealed, "Cross layer patching is sealed");
        var data = constant.getConstantData();
        tracker.registerPatchSite(data.key, heapOffset);
        tracker.updateFutureTrackingInfo(new FutureTrackingInfo(data.key, FutureTrackingInfo.State.Relocatable, ImageHeapConstant.getConstantID(constant), INVALID));
    }

    /**
     * See {@link #generateRelocationPatchArray} for layout description.
     */
    public int computeRelocationPatchesLength() {
        patchingSealed = true;
        VMError.guarantee(relocationPatchesLength == -1, "called multiple times");

        int numRelocations = 0;
        for (var offsetsToPatch : tracker.futureKeyToPatchingOffsetsMap.values()) {
            numRelocations += offsetsToPatch.size() * 2;
        }

        relocationPatchesLength = numRelocations;
        return relocationPatchesLength;
    }

    public int[] getRelocationPatches() {
        VMError.guarantee(relocationPatches != null);
        return relocationPatches;
    }

}

class ImageLayerIdTrackingSingleton implements LayeredImageSingleton {
    private final Map<String, TrackingInfo> keyToTrackingInfoMap = new HashMap<>();
    final Map<String, List<Integer>> futureKeyToPatchingOffsetsMap = new ConcurrentHashMap<>();

    ImageLayerIdTrackingSingleton() {
    }

    TrackingInfo getTrackingInfo(String key) {
        return keyToTrackingInfoMap.get(key);
    }

    void registerPriorTrackingInfo(String key, int constantId) {
        assert key != null && constantId > 0 : Assertions.errorMessage(key, constantId);
        var previous = keyToTrackingInfoMap.putIfAbsent(key, new PriorTrackingInfo(constantId));
        VMError.guarantee(previous == null, "Two values are registered for this key %s", key);
    }

    public void registerFutureTrackingInfo(FutureTrackingInfo info) {
        updateFutureTrackingInfo0(info, false);
    }

    public void updateFutureTrackingInfo(FutureTrackingInfo info) {
        updateFutureTrackingInfo0(info, true);
    }

    private void updateFutureTrackingInfo0(FutureTrackingInfo info, boolean expectPrevious) {
        String key = info.key();
        assert key != null;
        var previous = (FutureTrackingInfo) keyToTrackingInfoMap.get(key);
        boolean hasPrevious = previous != null;
        VMError.guarantee(expectPrevious == hasPrevious, "Mismatch with expectPrevious: %s %s %s", expectPrevious, info, previous);
        if (previous != null) {
            boolean validState = false;
            if (info.state().ordinal() > previous.state().ordinal()) {
                validState = previous.key().equals(info.key());
            } else if (info.state() == previous.state()) {
                validState = previous.key().equals(info.key()) && info.state() != FutureTrackingInfo.State.Final && previous.loaderId() == info.loaderId();
            }
            VMError.guarantee(validState, "Invalid update %s %s", previous, info);
        }

        keyToTrackingInfoMap.put(key, info);
    }

    void registerPatchSite(String futureKey, int heapIndex) {
        List<Integer> indexes = futureKeyToPatchingOffsetsMap.computeIfAbsent(futureKey, id -> new ArrayList<>());
        indexes.add(heapIndex);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    private static String futureKeyPatchKey(String key) {
        return String.format("futureOffsetPatches:%s", key);
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        ArrayList<String> priorKeys = new ArrayList<>();
        ArrayList<Integer> priorIds = new ArrayList<>();
        ArrayList<String> futureKeys = new ArrayList<>();
        ArrayList<Integer> futureStates = new ArrayList<>();
        ArrayList<Integer> futureLoaderIds = new ArrayList<>();
        ArrayList<Integer> futureOffsets = new ArrayList<>();

        for (var entry : keyToTrackingInfoMap.entrySet()) {
            String key = entry.getKey();
            TrackingInfo trackingInfo = entry.getValue();
            if (trackingInfo instanceof PriorTrackingInfo prior) {
                priorKeys.add(key);
                priorIds.add(prior.constantId());
            } else {
                FutureTrackingInfo future = (FutureTrackingInfo) trackingInfo;
                futureKeys.add(key);
                futureStates.add(future.state().ordinal());
                futureLoaderIds.add(future.loaderId());
                if (future.state() == FutureTrackingInfo.State.Final) {
                    futureOffsets.add(future.offset());
                }

                writer.writeIntList(futureKeyPatchKey(key), futureKeyToPatchingOffsetsMap.getOrDefault(key, List.of()));
            }
        }

        writer.writeStringList("priorKeys", priorKeys);
        writer.writeIntList("priorIds", priorIds);

        writer.writeStringList("futureKeys", futureKeys);
        writer.writeIntList("futureStates", futureStates);
        writer.writeIntList("futureLoaderIds", futureLoaderIds);
        writer.writeIntList("futureOffsets", futureOffsets);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        var tracker = new ImageLayerIdTrackingSingleton();

        Iterator<String> priorKeys = loader.readStringList("priorKeys").iterator();
        Iterator<Integer> priorIds = loader.readIntList("priorIds").iterator();

        while (priorKeys.hasNext()) {
            tracker.registerPriorTrackingInfo(priorKeys.next(), priorIds.next());
        }

        Iterator<String> futureKeys = loader.readStringList("futureKeys").iterator();
        Iterator<Integer> futureStates = loader.readIntList("futureStates").iterator();
        Iterator<Integer> futureLoaderIds = loader.readIntList("futureLoaderIds").iterator();
        Iterator<Integer> futureOffsets = loader.readIntList("futureOffsets").iterator();
        while (futureKeys.hasNext()) {
            String key = futureKeys.next();
            FutureTrackingInfo.State state = FutureTrackingInfo.State.values()[futureStates.next()];
            int loaderId = futureLoaderIds.next();
            int offset = state == FutureTrackingInfo.State.Final ? futureOffsets.next() : INVALID;
            tracker.registerFutureTrackingInfo(new FutureTrackingInfo(key, state, loaderId, offset));

            List<Integer> offsetsToPatch = loader.readIntList(futureKeyPatchKey(key));
            offsetsToPatch.forEach(heapOffset -> tracker.registerPatchSite(key, heapOffset));
        }

        return tracker;
    }
}

interface TrackingInfo {
}

record PriorTrackingInfo(int constantId) implements TrackingInfo {
}

record FutureTrackingInfo(String key, State state, int loaderId, int offset) implements TrackingInfo {
    enum State {
        Type,
        Relocatable,
        Final
    }

    public FutureTrackingInfo {
        assert key != null && loaderId >= INVALID : Assertions.errorMessage(key, loaderId);
        switch (state) {
            case Type:
            case Relocatable:
                assert offset == INVALID : Assertions.errorMessage(state, offset);
                break;
            case Final:
                assert offset > 0 || (offset == INVALID && loaderId == INVALID) : Assertions.errorMessage(state, offset);
        }
    }
}
