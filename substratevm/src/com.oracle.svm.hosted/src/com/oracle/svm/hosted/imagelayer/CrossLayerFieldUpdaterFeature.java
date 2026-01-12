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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.heap.ImageHeapObjectAdder;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * This feature enables an object's field to be updated by a later layer. In particular, this
 * feature provides:
 * <ol>
 * <li>A method to register an updatable field {@link #markUpdatableField}</li>
 * <li>A method to register when a field is updated {@link #updateField}</li>
 * <li>Methods to calculate {@link #computeUpdatePatchesLength} and produce
 * {@link #generateUpdatePatchArray} a patch array used to perform this updates during startup</li>
 * </ol>
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = CrossLayerFieldUpdaterFeature.LayeredCallbacks.class, layeredInstallationKind = SingletonLayeredInstallationKind.Independent.class)
public class CrossLayerFieldUpdaterFeature implements InternalFeature {
    private static final int INVALID = -1;

    /**
     * Marks when it is no longer legal to call {@link #updateField}.
     */
    private boolean sealed = false;
    private byte[] updatePatches = null;
    private int updatePatchesLength = INVALID;
    private int updatePatchesHeaderSize = INVALID;

    private final boolean extensionLayer = ImageLayerBuildingSupport.buildingExtensionLayer();

    Map<UpdatableField, UpdatableFieldStatus> updateInfoMap = extensionLayer ? null : new ConcurrentHashMap<>();

    /**
     * Stores the {@link ImageHeapConstant#getConstantID} of the receiver and
     * {@link AnalysisField#getId()} of the {@link AnalysisField} for the value which may be
     * updated.
     */
    record UpdatableField(int receiverId, int fieldId) {
    }

    /**
     * Tracks the status of a field which can be updated.
     */
    private static class UpdatableFieldStatus {
        final UpdatableField fieldInfo;
        /**
         * Holds the offset of the location to update relative to the heap base pointer.
         */
        final int heapBaseRelativeOffset;
        final JavaKind kind;

        boolean updated;
        Object updatedValue;

        UpdatableFieldStatus(UpdatableField fieldInfo, int heapBaseRelativeOffset, JavaKind kind) {
            this.fieldInfo = fieldInfo;
            this.heapBaseRelativeOffset = heapBaseRelativeOffset;
            this.kind = kind;
        }
    }

    static CrossLayerFieldUpdaterFeature singleton() {
        return ImageSingletons.lookup(CrossLayerFieldUpdaterFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (extensionLayer) {
            ImageHeapObjectAdder.singleton().registerObjectAdder(this::addInitialObjects);
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (extensionLayer) {
            FeatureImpl.BeforeImageWriteAccessImpl config = (FeatureImpl.BeforeImageWriteAccessImpl) access;
            generateUpdatePatchArray(config.getImage().getHeap(), config.getHostedUniverse().getSnippetReflection());
        }
    }

    /**
     * Record a field which may be updated by a later layer.
     */
    void markUpdatableField(long heapOffset, ImageHeapConstant receiver, HostedField field, ImageHeapLayoutInfo heapLayout) {
        VMError.guarantee(heapLayout.isWritablePatched(heapOffset), "Field must be located in the writable patched section");
        int receiverId = ImageHeapConstant.getConstantID(receiver);
        int fieldId = field.getWrapped().getId();
        UpdatableField fieldInfo = new UpdatableField(receiverId, fieldId);
        JavaKind kind = JavaKind.fromJavaClass(field.getType().getJavaClass());
        UpdatableFieldStatus updateInfo = new UpdatableFieldStatus(fieldInfo, NumUtil.safeToInt(heapOffset), kind);
        var prev = updateInfoMap.put(fieldInfo, updateInfo);
        VMError.guarantee(prev == null);
    }

    /**
     * Records a field which has been updated. This field must have been marked via
     * {@link #markUpdatableField} in a prior layer.
     */
    void updateField(ImageHeapConstant receiver, AnalysisField field, Object value) {
        assert extensionLayer && !sealed : "Updates can only been performed in extension layers";
        int receiverId = ImageHeapConstant.getConstantID(receiver);
        int fieldId = field.getId();
        UpdatableField fieldInfo = new UpdatableField(receiverId, fieldId);
        UpdatableFieldStatus updateInfo = updateInfoMap.get(fieldInfo);
        VMError.guarantee(updateInfo != null && !updateInfo.updated, "Illegal update %s", updateInfo);

        updateInfo.updated = true;
        updateInfo.updatedValue = value;
    }

    /**
     * We must explicitly ensure all objects referenced by updated fields are added to the heap.
     * This is because these objects will not be scanned during the creation of the current layer's
     * image heap.
     */
    private void addInitialObjects(NativeImageHeap heap, HostedUniverse hUniverse) {
        assert extensionLayer && !sealed : "objects can only be added in extension layers";
        sealed = true;
        String addReason = "Registered as a required heap constant within the CrossLayerFieldUpdaterFeature";

        for (var info : updateInfoMap.values()) {
            if (patchFilter(info)) {
                ImageHeapConstant singletonConstant = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(info.updatedValue);
                heap.addConstant(singletonConstant, false, addReason);
            }
        }
    }

    /**
     * See {@link #generateUpdatePatchArray} for more details.
     *
     * @return byte size length of this array.
     */
    int computeUpdatePatchesLength() {
        assert sealed;
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        VMError.guarantee(updatePatchesLength == INVALID && updatePatchesHeaderSize == INVALID, "called multiple times");

        /*
         * Number of offsets which need to be patched. Each of these will be always of size
         * Integer.BYTES.
         */
        long numPatchOffsets = 0;
        /*
         * Total size of the values to be patched in. The size of each entry is dependent on the
         * field kind.
         */
        long patchEntriesSize = 0;
        EconomicSet<Integer> patchSizes = EconomicSet.create();
        for (var info : updateInfoMap.values()) {
            if (patchFilter(info)) {
                numPatchOffsets++;
                int patchSize = getPatchSize(info, referenceSize);
                patchEntriesSize += patchSize;
                patchSizes.add(patchSize);
            }
        }
        updatePatchesHeaderSize = 2 * patchSizes.size() * Integer.BYTES;
        long totalLength = updatePatchesHeaderSize + (numPatchOffsets * Integer.BYTES) + patchEntriesSize;
        updatePatchesLength = NumUtil.safeToInt(totalLength);
        return updatePatchesLength;
    }

    private static int getPatchSize(UpdatableFieldStatus info, int referenceSize) {
        if (info.kind.isObject()) {
            return referenceSize;
        } else {
            return info.kind.getByteCount();
        }
    }

    /**
     * Currently we patch all updated field values. However, we could in the future choose to
     * monitor the value written in the past and only perform the update if the value has changed.
     */
    private boolean patchFilter(UpdatableFieldStatus status) {
        return status.updated;
    }

    /**
     * The field update patch array contains a list of all field update patches which need to be
     * performed. Because the size of the value to patch in is dependent on the field type, not all
     * entries are of uniform size. Therefore, we first output a header containing the sizes of the
     * value patches and the number of entries with each value patch size.
     * <p>
     * After the header we then output the patching information. For each entry, we first store the
     * heap offset where the patch occurs and then the value to patch in. While the heap offset will
     * always be stored as a 4-byte integer, the value to patch in is stored in a slot size
     * according to the patch size described in the header.
     * <p>
     * Overall, this array has the following format:
     *
     * <pre>
     *     --------------------------------------------
     *     | Header Information                       |
     *     --------------------------------------------
     *     | offset          | description            |
     *     | 0               | patch size A           |
     *     | 4               | # patches with size A  |
     *     | 8               | patch size B           |
     *     | 12              | # patches with size B  |
     *     --------------------------------------------
     *     | Entries                                  |
     *     --------------------------------------------
     *     | offset          | description            |
     *     | 0               | heap offset            |
     *     | 4               | value to patch         |
     *     | 4 + sizeA       | heap offset            |
     *     | 8 + sizeA       | value to patch         |
     *     | 8 + 2*sizeA     | heap offset            |
     *     | 12 + 2*sizeA    | value to patch         |
     *     | ...                                      |
     *     | X               | heap offset            |
     *     | X + 4           | value to patch         |
     *     | X + 4 + sizeB   | heap offset            |
     *     | X + 8 + sizeB   | value to patch         |
     *     --------------------------------------------
     *
     * </pre>
     *
     * Note all patching is performed relative to the initial layer's
     * {@link com.oracle.svm.core.Isolates#IMAGE_HEAP_BEGIN}, so we must subtract this offset
     * (relative to the image heap start) away from all offsets to patch.
     * <p>
     * In addition, within the Image Layer Section we immediately store before the array the total
     * array size as a long and the header size as an int value.
     */
    private void generateUpdatePatchArray(NativeImageHeap heap, SnippetReflectionProvider snippetReflection) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        int shift = ImageSingletons.lookup(CompressEncoding.class).getShift();
        int heapBeginOffset = Heap.getHeap().getImageHeapOffsetInAddressSpace();

        Comparator<UpdatableFieldStatus> comparator = Comparator.comparingInt((ToIntFunction<UpdatableFieldStatus>) info -> getPatchSize(info, referenceSize))
                        .thenComparingInt(info -> info.heapBaseRelativeOffset);
        var sortedValues = updateInfoMap.values().stream().filter(this::patchFilter).sorted(comparator).toList();
        byte[] patchArray = new byte[updatePatchesLength];
        ByteBuffer buffer = ByteBuffer.wrap(patchArray).order(ByteOrder.LITTLE_ENDIAN);

        // The header counts are written last. Move to entries section.
        buffer.position(updatePatchesHeaderSize);
        Map<Integer, Integer> patchSizeCounts = new HashMap<>();
        for (var value : sortedValues) {
            /*
             * When performing patching, the offset to patch is relative to the start of the image
             * heap, not the heap base pointer.
             */
            buffer.putInt(value.heapBaseRelativeOffset - heapBeginOffset);
            switch (value.kind) {
                case Boolean -> buffer.put((byte) (((Boolean) value.updatedValue) ? 1 : 0));
                case Byte -> buffer.put((Byte) value.updatedValue);
                case Int -> buffer.putInt((Integer) value.updatedValue);
                case Long -> buffer.putLong((Long) value.updatedValue);
                case Object -> {
                    int encodedValue;
                    if (value.updatedValue == null) {
                        encodedValue = 0;
                    } else {
                        var newValue = (ImageHeapConstant) snippetReflection.forObject(value.updatedValue);
                        var objectInfo = heap.getConstantInfo(newValue);
                        long heapBaseRelativeOffset = objectInfo.getOffset();
                        encodedValue = NumUtil.safeToInt(heapBaseRelativeOffset) >>> shift;
                    }
                    if (referenceSize == Integer.BYTES) {
                        buffer.putInt(encodedValue);
                    } else {
                        assert referenceSize == Long.BYTES;
                        buffer.putLong(encodedValue);
                    }
                }
                default -> throw VMError.shouldNotReachHere("Unexpected value %s", value);
            }
            int patchSize = getPatchSize(value, referenceSize);
            patchSizeCounts.compute(patchSize, (_, v) -> v == null ? 1 : v + 1);
        }

        VMError.guarantee(buffer.position() == updatePatchesLength);

        // write the header now
        buffer.position(0);
        patchSizeCounts.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).forEachOrdered(e -> {
            buffer.putInt(e.getKey());
            buffer.putInt(e.getValue());
        });
        VMError.guarantee(buffer.position() == updatePatchesHeaderSize);
        updatePatches = patchArray;
    }

    byte[] getUpdatePatches() {
        VMError.guarantee(updatePatches != null);
        return updatePatches;
    }

    int getHeaderSize() {
        assert updatePatchesHeaderSize != INVALID;
        return updatePatchesHeaderSize;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<CrossLayerFieldUpdaterFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, CrossLayerFieldUpdaterFeature singleton) {
                    var updateInfoMap = singleton.updateInfoMap;
                    ArrayList<Integer> fieldIds = new ArrayList<>();
                    ArrayList<Integer> receiverIds = new ArrayList<>();
                    ArrayList<Integer> offsets = new ArrayList<>();
                    ArrayList<Integer> javaKindOrdinals = new ArrayList<>();
                    for (var info : updateInfoMap.values()) {
                        fieldIds.add(info.fieldInfo.fieldId());
                        receiverIds.add(info.fieldInfo.receiverId());
                        offsets.add(info.heapBaseRelativeOffset);
                        javaKindOrdinals.add(info.kind.ordinal());
                    }
                    writer.writeIntList("fieldIds", fieldIds);
                    writer.writeIntList("receiverIds", receiverIds);
                    writer.writeIntList("offsets", offsets);
                    writer.writeIntList("javaKindOrdinals", javaKindOrdinals);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, CrossLayerFieldUpdaterFeature singleton) {
                    Map<CrossLayerFieldUpdaterFeature.UpdatableField, CrossLayerFieldUpdaterFeature.UpdatableFieldStatus> map = new HashMap<>();
                    Iterator<Integer> fieldIds = loader.readIntList("fieldIds").iterator();
                    Iterator<Integer> receiverIds = loader.readIntList("receiverIds").iterator();
                    Iterator<Integer> offsets = loader.readIntList("offsets").iterator();
                    Iterator<Integer> javaKindOrdinals = loader.readIntList("javaKindOrdinals").iterator();
                    while (fieldIds.hasNext()) {
                        int fieldId = fieldIds.next();
                        int receiverId = receiverIds.next();
                        int offset = offsets.next();
                        JavaKind kind = JavaKind.values()[javaKindOrdinals.next()];
                        CrossLayerFieldUpdaterFeature.UpdatableField fieldInfo = new CrossLayerFieldUpdaterFeature.UpdatableField(receiverId, fieldId);
                        CrossLayerFieldUpdaterFeature.UpdatableFieldStatus updateInfo = new CrossLayerFieldUpdaterFeature.UpdatableFieldStatus(fieldInfo, offset, kind);
                        var prev = map.put(fieldInfo, updateInfo);
                        assert prev == null : prev;
                    }
                    assert !receiverIds.hasNext() && !offsets.hasNext() && !javaKindOrdinals.hasNext() : "information is not properly synced";
                    singleton.updateInfoMap = Collections.unmodifiableMap(map);
                }
            });
        }
    }
}
