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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import static com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import static com.oracle.svm.hosted.imagelayer.LayeredFieldValueTransformerSupport.LayeredCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layered.LayeredFieldValue;
import com.oracle.svm.guest.staging.layered.LayeredFieldValueTransformer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Support for managing {@link LayeredFieldValueTransformer}s and ensuring updatable values are
 * properly relayed to {@link CrossLayerFieldUpdaterFeature}.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredCallbacks.class)
public class LayeredFieldValueTransformerSupport implements InternalFeature {

    private final Map<AnalysisField, LayeredFieldValueTransformerImpl> fieldToLayeredTransformer = new ConcurrentHashMap<>();

    private final boolean extensionLayer = ImageLayerBuildingSupport.buildingExtensionLayer();

    /**
     * Contains the {@link AnalysisField#getId()}s of fields which have receivers with updatable
     * values.
     */
    private Set<Integer> fieldsWithUpdatableValues = Set.of();

    private List<UpdatableValueState> priorUpdatableValues;
    private final Set<Integer> fieldsWithInstalledUpdatableValueStates = ConcurrentHashMap.newKeySet();

    private CrossLayerFieldUpdaterFeature cachedFieldUpdater;

    private CrossLayerFieldUpdaterFeature getFieldUpdater() {
        if (cachedFieldUpdater == null) {
            cachedFieldUpdater = CrossLayerFieldUpdaterFeature.singleton();
        }
        return cachedFieldUpdater;
    }

    /**
     * Keeps track of the state of fields which have been marked as updatable in prior layer and may
     * be updated in the current layer.
     */
    private static class UpdatableValueState {
        final LayeredFieldValueTransformerImpl transformer;
        ImageHeapConstant receiver;
        final int receiverId;
        boolean updated = false;

        UpdatableValueState(LayeredFieldValueTransformerImpl transformer, int receiverId, ImageHeapConstant receiver) {
            this.transformer = transformer;
            this.receiverId = receiverId;
            this.receiver = receiver;
        }
    }

    public static LayeredFieldValueTransformerSupport singleton() {
        return ImageSingletons.lookup(LayeredFieldValueTransformerSupport.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(CrossLayerConstantRegistryFeature.class);
    }

    /**
     * In extension layers we must install {@link UpdatableValueState}s for potential field updates
     * within this layer. Note we cannot do this earlier as we need the analysis world to be
     * instantiated beforehand.
     */
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (extensionLayer) {
            SVMImageLayerLoader loader = HostedImageLayerBuildingSupport.singleton().getLoader();

            priorUpdatableValues = new ArrayList<>();
            for (var fieldId : fieldsWithUpdatableValues) {
                var aField = loader.getAnalysisFieldForBaseLayerId(fieldId);
                var proxy = fieldToLayeredTransformer.get(aField);
                if (proxy == null) {
                    LayeredFieldValue layeredFieldValue = AnnotationUtil.getAnnotation(aField, LayeredFieldValue.class);
                    if (layeredFieldValue != null) {
                        proxy = createTransformer(aField, layeredFieldValue, Set.copyOf(loader.getUpdatableFieldReceiverIds(fieldId)));
                    }
                }
                if (proxy != null) {
                    installPriorUpdatableValueStates(loader, aField, proxy);
                }
            }
        }
    }

    /**
     * Because we are checking for updates on objects which have already been installed in the heap
     * in a prior layer, it is not guaranteed that these objects will be reached during the usual
     * analysis process. Hence, we must actively poll updatable values during analysis to see if an
     * update is available.
     */
    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (extensionLayer) {
            boolean changed = processUpdatableValues(((FeatureImpl.DuringAnalysisAccessImpl) access).getUniverse().getHeapScanner());
            if (changed) {
                // new objects were added which need to be scanned
                access.requireAnalysisIteration();
            }
        }
    }

    /**
     * Finalize current-layer updatable values while the image heap scanner is still open, and poll
     * prior-layer updatable values once more before heap layout starts.
     */
    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        ImageHeapScanner heapScanner = ((FeatureImpl.BeforeHeapLayoutAccessImpl) access).getHeapScanner();
        finalizeFieldValues(heapScanner);
        if (extensionLayer) {
            processUpdatableValues(heapScanner);
        }
    }

    /**
     * Go through all potential updates to find any new updates which need to processed.
     */
    public boolean processUpdatableValues(ImageHeapScanner heapScanner) {
        SVMImageLayerLoader loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        boolean updated = false;
        ScanReason reason = new OtherReason("Manual rescan triggered from " + LayeredFieldValueTransformerSupport.class);
        for (var updatableValue : priorUpdatableValues) {
            if (!updatableValue.updated) {
                if (updatableValue.receiver == null) {
                    // See if the constant is now available.
                    updatableValue.receiver = loader.getConstant(updatableValue.receiverId);
                }
                if (updatableValue.receiver != null) {
                    var state = updatableValue.transformer.updateAndGetResult(updatableValue.receiver);
                    if (!state.isUnresolved()) {
                        /*
                         * As part of updating process we must record the field update and also, if
                         * during analysis, ensure the object is scanned.
                         */
                        VMError.guarantee(!state.isUpdatable(), "Currently values can only be updated once.");
                        updatableValue.updated = true;
                        var newValue = state.getResultValue();
                        getFieldUpdater().updateField(updatableValue.receiver, updatableValue.transformer.aField, newValue);
                        heapScanner.doScan(newValue, reason);
                        updated = true;
                    }
                }
            }
        }
        return updated;
    }

    private void finalizeFieldValues(ImageHeapScanner heapScanner) {
        ScanReason reason = new OtherReason("Pre-heap-layout field value finalization triggered from " + LayeredFieldValueTransformerSupport.class);
        for (var transformer : fieldToLayeredTransformer.values()) {
            transformer.finalizeFieldValues(heapScanner, reason);
        }
    }

    public LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, LayeredFieldValue layeredFieldValue) {
        var result = fieldToLayeredTransformer.get(aField);
        if (result != null) {
            return result;
        }
        VMError.guarantee(!aField.isInSharedLayer() || !fieldsWithUpdatableValues.contains(aField.getId()),
                        "Field value transformer should have already been installed via setupUpdatableValueTransformers.");
        return createTransformer(aField, layeredFieldValue, Set.of());
    }

    public LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, LayeredFieldValueTransformer<?> transformer) {
        var result = createTransformer(aField, GuestAccess.get().getSnippetReflection().forObject(transformer), getUpdatableFieldReceiverIds(aField));
        if (extensionLayer && priorUpdatableValues != null) {
            installPriorUpdatableValueStates(HostedImageLayerBuildingSupport.singleton().getLoader(), aField, result);
        }
        return result;
    }

    private LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, LayeredFieldValue layeredFieldValue, Set<Integer> delayedValueReceivers) {
        return fieldToLayeredTransformer.computeIfAbsent(aField, _ -> {
            var transformer = JVMCIReflectionUtil.newInstance(GuestAccess.get().lookupType(layeredFieldValue.transformer()));
            return new LayeredFieldValueTransformerImpl(aField, transformer, delayedValueReceivers);
        });
    }

    private LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, JavaConstant transformer, Set<Integer> delayedValueReceivers) {
        return fieldToLayeredTransformer.computeIfAbsent(aField, _ -> new LayeredFieldValueTransformerImpl(aField, transformer, delayedValueReceivers));
    }

    private Set<Integer> getUpdatableFieldReceiverIds(AnalysisField aField) {
        if (extensionLayer) {
            SVMImageLayerLoader loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            int fieldId = loader.lookupHostedFieldInBaseLayer(aField);
            if (fieldsWithUpdatableValues.contains(fieldId)) {
                return Set.copyOf(loader.getUpdatableFieldReceiverIds(fieldId));
            }
        }
        return Set.of();
    }

    private void installPriorUpdatableValueStates(SVMImageLayerLoader loader, AnalysisField aField, LayeredFieldValueTransformerImpl transformer) {
        int fieldId = loader.lookupHostedFieldInBaseLayer(aField);
        if (!fieldsWithUpdatableValues.contains(fieldId)) {
            return;
        }
        if (fieldsWithInstalledUpdatableValueStates.add(fieldId)) {
            List<Integer> receiverIds = loader.getUpdatableFieldReceiverIds(fieldId);
            for (int receiverId : receiverIds) {
                ImageHeapConstant constant = loader.getConstant(receiverId);
                priorUpdatableValues.add(new UpdatableValueState(transformer, receiverId, constant));
            }
        }
    }

    /**
     * @return whether this field value was finalized as updatable before heap layout and therefore
     *         needs to be written into a patchable heap partition.
     */
    public boolean isFieldValueUpdatable(HostedField hField, JavaConstant receiver) {
        AnalysisField aField = hField.getWrapped();
        var transformer = fieldToLayeredTransformer.get(aField);
        if (transformer != null) {
            return transformer.isUpdatableReceiver(receiver);
        }
        return false;
    }

    /** Marks all updatable fields with objects installed within the heap. */
    public void recordWrittenField(HostedField hField, NativeImageHeap.ObjectInfo receiver, ImageHeapLayoutInfo heapLayout) {
        var transformer = fieldToLayeredTransformer.get(hField.getWrapped());
        if (transformer != null) {
            ImageHeapConstant ihc = receiver.getConstant();
            if (transformer.isUpdatableReceiver(ihc)) {
                long heapOffset = receiver.getOffset() + hField.getLocation();
                getFieldUpdater().markUpdatableField(heapOffset, ihc, hField, heapLayout);
            }
        }
    }

    /** @return all receivers which have an updatable value within this field */
    public List<ImageHeapConstant> getUpdatableReceivers(AnalysisField aField) {
        var transformer = fieldToLayeredTransformer.get(aField);
        if (transformer == null || !transformer.currentLayerHasUpdatableValues) {
            return List.of();
        }
        var updatableReceivers = transformer.computeUpdatableReceivers();
        assert !updatableReceivers.isEmpty();
        return updatableReceivers;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<LayeredFieldValueTransformerSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, LayeredFieldValueTransformerSupport singleton) {
                    var fieldsWithUpdatableValues = singleton.fieldToLayeredTransformer.entrySet().stream()
                                    .filter(e -> e.getValue().currentLayerHasUpdatableValues)
                                    .map(e -> e.getKey().getId()).toList();
                    writer.writeIntList("fieldsWithUpdatableValues", fieldsWithUpdatableValues);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, LayeredFieldValueTransformerSupport singleton) {
                    singleton.fieldsWithUpdatableValues = Set.copyOf(loader.readIntList("fieldsWithUpdatableValues"));
                }
            });
        }
    }
}
