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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layered.LayeredFieldValue;
import com.oracle.svm.core.layered.LayeredFieldValueTransformer;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Support for managing {@link LayeredFieldValueTransformer}s and ensuring updatable values are
 * properly relayed to {@link CrossLayerFieldUpdaterFeature}.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredFieldValueTransformerSupport.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class LayeredFieldValueTransformerSupport implements InternalFeature {

    private final Map<AnalysisField, LayeredFieldValueTransformerImpl> fieldToLayeredTransformer = new ConcurrentHashMap<>();

    private final boolean extensionLayer = ImageLayerBuildingSupport.buildingExtensionLayer();

    /**
     * Contains the {@link AnalysisField#getId()}s of fields which have receivers with updatable
     * values.
     */
    private Set<Integer> fieldsWithUpdatableValues = Set.of();

    private List<UpdatableValueState> priorUpdatableValues;

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

            List<UpdatableValueState> newPriorUpdatableValues = new ArrayList<>();
            for (var fieldId : fieldsWithUpdatableValues) {
                var aField = loader.getAnalysisFieldForBaseLayerId(fieldId);
                List<Integer> receiverIds = loader.getUpdatableFieldReceiverIds(fieldId);
                var proxy = createTransformer(aField, aField.getAnnotation(LayeredFieldValue.class), Set.copyOf(receiverIds));

                for (int receiverId : receiverIds) {
                    ImageHeapConstant constant = loader.getConstant(receiverId);
                    var state = new UpdatableValueState(proxy, receiverId, constant);
                    newPriorUpdatableValues.add(state);
                }
            }
            priorUpdatableValues = Collections.unmodifiableList(newPriorUpdatableValues);
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
            boolean changed = processUpdatableValues((FeatureImpl.DuringAnalysisAccessImpl) access);
            if (changed) {
                // new objects were added which need to be scanned
                access.requireAnalysisIteration();
            }
        }
    }

    /**
     * Because these fields have already been installed in the heap, these objects with updatable
     * field will not be reached while laying out the current layer's image heap. Hence, we call
     * {@link LayeredFieldValueTransformerSupport#processUpdatableValues} once more before
     * performing the heap layout.
     */
    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        if (extensionLayer) {
            processUpdatableValues(null);
        }
    }

    /**
     * Go through all potential updates to find any new updates which need to processed.
     */
    public boolean processUpdatableValues(FeatureImpl.DuringAnalysisAccessImpl access) {
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
                    var result = updatableValue.transformer.updateAndGetResult(updatableValue.receiver);
                    if (result != null) {
                        /*
                         * As part of updating process we must record the field update and also, if
                         * during analysis, ensure the object is scanned.
                         */
                        VMError.guarantee(!result.updatable(), "Currently values can only be updated once.");
                        updatableValue.updated = true;
                        var newValue = result.value();
                        getFieldUpdater().updateField(updatableValue.receiver, updatableValue.transformer.aField, newValue);
                        if (access != null) {
                            access.rescanObject(newValue, reason);
                        }
                        updated = true;
                    }
                }
            }
        }
        return updated;
    }

    public LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, LayeredFieldValue layeredFieldValue) {
        var result = fieldToLayeredTransformer.get(aField);
        if (result != null) {
            return result;
        }
        VMError.guarantee(!aField.isInBaseLayer() || !fieldsWithUpdatableValues.contains(aField.getId()),
                        "Field value transformer should have already been installed via setupUpdatableValueTransformers.");
        return createTransformer(aField, layeredFieldValue, Set.of());
    }

    private LayeredFieldValueTransformerImpl createTransformer(AnalysisField aField, LayeredFieldValue layeredFieldValue, Set<Integer> delayedValueReceivers) {
        return fieldToLayeredTransformer.computeIfAbsent(aField, _ -> {
            var transformer = ReflectionUtil.newInstance(layeredFieldValue.transformer());
            return new LayeredFieldValueTransformerImpl(aField, transformer, delayedValueReceivers);
        });
    }

    /**
     * Called on all field values before heap layout. Note, that because we cannot fold updatable
     * values, we need to have an explicit call to signal that it is safe to expose updatable
     * values.
     * 
     * @return whether this receiver needs to be patched.
     */
    public boolean finalizeFieldValue(HostedField hField, JavaConstant receiver) {
        AnalysisField aField = hField.getWrapped();
        ImageHeapConstant ihc = (ImageHeapConstant) receiver;
        var transformer = fieldToLayeredTransformer.get(aField);
        if (transformer != null) {
            return transformer.finalizeFieldValue(ihc);
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
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<LayeredFieldValueTransformerSupport>() {
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
