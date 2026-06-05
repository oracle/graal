/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.pointsto.util.AnalysisError.guarantee;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.ENUM;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.STRING;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.BaseLayerField;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.LayeredImageOptions;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.hosted.snapshot.c.CEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData.EnumConstant;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData.StringConstant;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.util.PrimitiveValueData;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class ImageLayerConstantLoader {
    private static final ResolvedJavaType IMAGE_LAYER_CONSTANT_LOADER = GuestAccess.get().lookupType(ImageLayerConstantLoader.class);
    private static final ResolvedJavaMethod CHECK_STRING_METHOD = JVMCIReflectionUtil.getUniqueDeclaredMethod(IMAGE_LAYER_CONSTANT_LOADER, "checkString", STRING);
    private static final ResolvedJavaMethod INTERN_METHOD = JVMCIReflectionUtil.getUniqueDeclaredMethod(STRING, "intern");
    private static final ResolvedJavaMethod VALUE_OF_METHOD = JVMCIReflectionUtil.getUniqueDeclaredMethod(ENUM, "valueOf", GuestAccess.get().lookupType(Class.class), STRING);

    private final SVMImageLayerLoader loader;
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;
    private final SharedLayerSnapshotData.Loader snapshot;

    private final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> typeToConstant = new ConcurrentHashMap<>();
    private final Map<JavaConstant, Integer> stringToConstant = new ConcurrentHashMap<>();
    private final Map<JavaConstant, Integer> enumToConstant = new ConcurrentHashMap<>();
    private final Map<Integer, Long> objectOffsets = new ConcurrentHashMap<>();

    ImageLayerConstantLoader(SVMImageLayerLoader loader, SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, HostedImageLayerBuildingSupport imageLayerBuildingSupport,
                    SharedLayerSnapshotData.Loader snapshot) {
        this.loader = loader;
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.imageLayerBuildingSupport = imageLayerBuildingSupport;
        this.snapshot = snapshot;
    }

    void prepareConstantRelinking() {
        SnapshotAdapters.forEach(snapshot.getConstantsToRelink(), id -> prepareConstantRelinking(findConstant(id)));
    }

    /**
     * Load each constant with a known static final {@code AnalysisField origin} in the base layer,
     * create the corresponding {@code ImageHeapConstant baseLayerConstant}, and find its
     * corresponding {@code JavaConstant hostedValue} reachable from the same field in the current
     * hosted heap. Finally, register the {@code hostedValue->baseLayerConstant} mapping in the
     * shadow heap. This registration ensures that any future lookup of {@code hostedValue} in the
     * shadow heap will yield the same reconstructed constant. Relinking constants with known origin
     * eagerly is necessary to ensure that the {@code hostedValue} always maps to the same
     * reconstructed {@code baseLayerConstant}, even if the {@code hostedValue} is first reachable
     * from other fields than the one saved as its {@code origin} in the base layer, or if it is
     * reachable transitively from other constants.
     */
    void relinkStaticFinalFieldValues(boolean isLateLoading) {
        /*
         * Relinking can trigger hosted class initialization, which may execute arbitrary framework
         * code. Do not use the common pool here: if class initialization waits for common-pool work
         * while other relinking tasks are blocked on class-initialization state, the image build can
         * deadlock.
         */
        for (int i = 0; i < snapshot.getConstants().size(); i++) {
            var constantData = snapshot.getConstants().get(i);
            var relinking = constantData.getObject().getRelinking();
            if (relinking.isFieldConstant() && relinking.getFieldConstant().getRequiresLateLoading() == isLateLoading) {
                ImageHeapConstant constant = getOrCreateConstant(constantData.getId());
                /*
                 * If the field value cannot be read, the hosted object will not be relinked. If
                 * there's already an ImageHeapConstant registered for the same hosted value, the
                 * registration will fail. That could mean that we try to register too late.
                 */
                if (constant.getHostedObject() != null) {
                    loader.universe.getHeapScanner().registerBaseLayerValue(constant, PERSISTED);
                }
            }
        }
    }

    private PersistedConstantData.Loader findConstant(int id) {
        return SnapshotAdapters.binarySearchUnique(id, snapshot.getConstants(), PersistedConstantData.Loader::getId);
    }

    private void prepareConstantRelinking(PersistedConstantData.Loader constantData) {
        if (!constantData.isObject()) {
            return;
        }
        int id = constantData.getId();
        int identityHashCode = constantData.getIdentityHashCode();

        RelinkingData.Loader relinking = constantData.getObject().getRelinking();
        if (relinking.isClassConstant()) {
            int typeId = relinking.getClassConstant().getTypeId();
            typeToConstant.put(typeId, id);
        } else if (relinking.isStringConstant()) {
            String value = relinking.getStringConstant().getValue();
            JavaConstant constant = getStringConstant(value);
            constant = GuestAccess.get().invoke(INTERN_METHOD, constant);
            injectIdentityHashCode(constant, identityHashCode);
            stringToConstant.put(constant, id);
        } else if (relinking.isEnumConstant()) {
            EnumConstant.Loader enumConstant = relinking.getEnumConstant();
            JavaConstant enumValue = getEnumValue(enumConstant.getEnumClass(), enumConstant.getEnumName());
            injectIdentityHashCode(enumValue, identityHashCode);
            enumToConstant.put(enumValue, id);
        }
    }

    private static JavaConstant getStringConstant(String value) {
        return GuestAccess.get().asGuestString(value);
    }

    void loadMaterializedChildren(ImageHeapConstant constant) {
        if (constant instanceof ImageHeapInstance imageHeapInstance) {
            loadMaterializedChildren(constant, imageHeapInstance::getFieldValue, imageHeapInstance.getFieldValuesSize());
        } else if (constant instanceof ImageHeapObjectArray imageHeapObjectArray) {
            loadMaterializedChildren(constant, imageHeapObjectArray::getElement, imageHeapObjectArray.getLength());
        }
    }

    private void loadMaterializedChildren(ImageHeapConstant constant, IntFunction<Object> valuesFunction, int size) {
        PersistedConstantData.Loader baseLayerConstant = findConstant(ImageHeapConstant.getConstantID(constant));
        if (baseLayerConstant != null) {
            SnapshotStructList.Loader<ConstantReferenceData.Loader> data = baseLayerConstant.getObject().getData();
            assert size == data.size() : "The size of the constant in the base layer does not match the size in the application: %d != %d".formatted(data.size(), size);
            for (int i = 0; i < data.size(); ++i) {
                ConstantReferenceData.Loader childConstant = data.get(i);
                if (childConstant.isObjectConstant()) {
                    if (childConstant.isNotMaterialized()) {
                        continue;
                    }
                    loadMaterializedChild(valuesFunction.apply(i));
                }
            }
        }
    }

    private void loadMaterializedChild(Object child) {
        if (child instanceof AnalysisFuture<?> analysisFuture) {
            if (analysisFuture.ensureDone() instanceof ImageHeapConstant imageHeapConstant) {
                loadMaterializedChildren(imageHeapConstant);
            }
        }
    }

    boolean hasValueForConstant(JavaConstant javaConstant) {
        if (SVMImageLayerSnapshotUtil.DYNAMIC_HUB.isInstance(javaConstant)) {
            ConstantReflectionProvider constantReflectionProvider = loader.universe.getBigbang().getConstantReflectionProvider();
            return hasValueForType((AnalysisType) constantReflectionProvider.asJavaType(javaConstant));
        } else if (STRING.isInstance(javaConstant)) {
            return stringToConstant.containsKey(javaConstant) && GuestAccess.get().invoke(CHECK_STRING_METHOD, null, javaConstant).asBoolean();
        } else if (ENUM.isInstance(javaConstant)) {
            return enumToConstant.containsKey(javaConstant);
        } else {
            return false;
        }
    }

    @SuppressFBWarnings(value = "ES", justification = "Reference equality check needed to detect intern status")
    @SuppressWarnings("unused")
    private static boolean checkString(String string) {
        return string.intern() == string;
    }

    private boolean hasValueForType(AnalysisType type) {
        return typeToConstant.containsKey(type.getId());
    }

    boolean hasValueForHub(DynamicHub hub) {
        return hasValueForType(hubToType(hub));
    }

    ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        int constantId;
        if (SVMImageLayerSnapshotUtil.DYNAMIC_HUB.isInstance(javaConstant)) {
            ConstantReflectionProvider constantReflectionProvider = loader.universe.getBigbang().getConstantReflectionProvider();
            constantId = getConstantIdForType((AnalysisType) constantReflectionProvider.asJavaType(javaConstant));
        } else if (STRING.isInstance(javaConstant)) {
            constantId = stringToConstant.get(javaConstant);
        } else if (ENUM.isInstance(javaConstant)) {
            constantId = enumToConstant.get(javaConstant);
        } else {
            throw AnalysisError.shouldNotReachHere("The constant was not in the persisted heap.");
        }
        return getOrCreateConstant(constantId);
    }

    private int getConstantIdForType(AnalysisType type) {
        return typeToConstant.get(type.getId());
    }

    private ImageHeapConstant getValueForType(AnalysisType type) {
        return getOrCreateConstant(getConstantIdForType(type));
    }

    ImageHeapConstant getValueForHub(DynamicHub hub) {
        return getValueForType(hubToType(hub));
    }

    private AnalysisType hubToType(DynamicHub hub) {
        return ((SVMHost) loader.universe.hostVM()).lookupType(hub);
    }

    private DynamicHub typeToHub(AnalysisType type) {
        return ((SVMHost) loader.universe.hostVM()).dynamicHub(type);
    }

    ImageHeapConstant getOrCreateConstant(int id) {
        return getOrCreateConstant(id, null);
    }

    /* Retrieves the given constant iff it has already been relinked. */
    ImageHeapConstant getConstant(int id) {
        return constants.get(id);
    }

    /**
     * Get the {@link ImageHeapConstant} representation for a specific base layer constant id. If
     * known, the parentReachableHostedObject will point to the corresponding constant in the
     * underlying host VM, found by querying the parent object that made this constant reachable.
     */
    private ImageHeapConstant getOrCreateConstant(int id, JavaConstant parentReachableHostedObjectCandidate) {
        if (constants.containsKey(id)) {
            return constants.get(id);
        }
        PersistedConstantData.Loader baseLayerConstant = findConstant(id);
        if (baseLayerConstant == null) {
            throw GraalError.shouldNotReachHere("The constant was not reachable in the base image");
        }

        /*
         * Important: If this is a constant originating from a static final field ensure that the
         * field declaring type is initialized before the field type is accessed below. This is to
         * avoid issue with class initialization execution order in class initialization cycles.
         */
        if (baseLayerConstant.isObject() && !baseLayerConstant.getIsSimulated()) {
            RelinkingData.Loader relinking = baseLayerConstant.getObject().getRelinking();
            if (relinking.isFieldConstant()) {
                AnalysisField analysisField = loader.getAnalysisFieldForBaseLayerId(relinking.getFieldConstant().getOriginFieldId());
                VMError.guarantee(analysisField.getDeclaringClass().isInitialized());
            }
        }

        AnalysisType type = loader.getAnalysisTypeForBaseLayerId(baseLayerConstant.getTypeId());

        long objectOffset = baseLayerConstant.getObjectOffset();
        int identityHashCode = baseLayerConstant.getIdentityHashCode();

        JavaConstant parentReachableHostedObject;
        if (parentReachableHostedObjectCandidate == null) {
            int parentConstantId = baseLayerConstant.getParentConstantId();
            if (parentConstantId != 0) {
                ImageHeapConstant parentConstant = getOrCreateConstant(parentConstantId);
                int index = baseLayerConstant.getParentIndex();
                parentReachableHostedObject = getReachableHostedValue(parentConstant, index);
            } else {
                parentReachableHostedObject = null;
            }
        } else {
            parentReachableHostedObject = parentReachableHostedObjectCandidate;
        }

        if (parentReachableHostedObject != null && !type.getJavaClass().equals(Class.class)) {
            /*
             * The hash codes of DynamicHubs need to be injected before they are used in a map,
             * which happens right after their creation. The injection of their hash codes can be
             * found in SVMHost#registerType.
             *
             * Also, for DynamicHub constants, the identity hash code persisted is the hash code of
             * the Class object, which we do not want to inject in the DynamicHub.
             */
            injectIdentityHashCode(parentReachableHostedObject, identityHashCode);
        }
        switch (baseLayerConstant.kind()) {
            case OBJECT -> {
                switch (baseLayerConstant.getObject().kind()) {
                    case INSTANCE -> {
                        SnapshotStructList.Loader<ConstantReferenceData.Loader> instanceData = baseLayerConstant.getObject().getData();
                        JavaConstant foundHostedObject = lookupHostedObject(baseLayerConstant, type);
                        if (foundHostedObject != null && parentReachableHostedObject != null) {
                            guarantee(foundHostedObject.equals(parentReachableHostedObject), "Found discrepancy between recipe-found hosted value %s and parent-reachable hosted value %s.",
                                            foundHostedObject, parentReachableHostedObject);
                        }

                        addBaseLayerObject(id, objectOffset, () -> {
                            ImageHeapInstance imageHeapInstance = new ImageHeapInstance(type, foundHostedObject == null ? parentReachableHostedObject : foundHostedObject, identityHashCode, id);
                            if (instanceData != null) {
                                Object[] fieldValues = getReferencedValues(imageHeapInstance, instanceData, imageLayerSnapshotUtil.getRelinkedFields(type, loader.universe));
                                imageHeapInstance.setFieldValues(fieldValues);
                            }
                            return imageHeapInstance;
                        });
                    }
                    case OBJECT_ARRAY -> {
                        SnapshotStructList.Loader<ConstantReferenceData.Loader> arrayData = baseLayerConstant.getObject().getData();
                        addBaseLayerObject(id, objectOffset, () -> {
                            ImageHeapObjectArray imageHeapObjectArray = new ImageHeapObjectArray(type, null, arrayData.size(), identityHashCode, id);
                            Object[] elementsValues = getReferencedValues(imageHeapObjectArray, arrayData, Set.of());
                            imageHeapObjectArray.setElementValues(elementsValues);
                            return imageHeapObjectArray;
                        });
                    }
                    default -> throw GraalError.shouldNotReachHere("Unknown object  type: " + baseLayerConstant.getObject().kind());
                }
            }
            case PRIMITIVE_DATA -> {
                assert type.isArray() && type.getComponentType().isPrimitive() : type + " should be an array of primitives.";
                Object array = baseLayerConstant.getPrimitiveData().toArray();
                addBaseLayerObject(id, objectOffset,
                                () -> new ImageHeapPrimitiveArray(type, null, GuestAccess.get().getSnippetReflection().forObject(array),
                                                Array.getLength(array), identityHashCode, id));
            }
            case RELOCATABLE -> {
                String key = baseLayerConstant.getRelocatable().getKey();
                addBaseLayerObject(id, objectOffset, () -> ImageHeapRelocatableConstant.create(type, key, id));
            }
            default -> throw GraalError.shouldNotReachHere("Unknown constant type: " + baseLayerConstant.kind());
        }

        return constants.get(id);
    }

    private Object[] getReferencedValues(ImageHeapConstant parentConstant, SnapshotStructList.Loader<ConstantReferenceData.Loader> data, Set<Integer> positionsToRelink) {
        Object[] values = new Object[data.size()];
        for (int position = 0; position < data.size(); ++position) {
            ConstantReferenceData.Loader constantData = data.get(position);
            if (delegateProcessing(constantData, values, position)) {
                continue;
            }
            int finalPosition = position;
            values[position] = switch (constantData.kind()) {
                case OBJECT_CONSTANT -> {
                    int constantId = constantData.getObjectConstant().getConstantId();
                    boolean relink = positionsToRelink.contains(position);
                    yield new AnalysisFuture<>(() -> {
                        ensureHubInitialized(parentConstant);

                        JavaConstant hostedConstant = relink ? getReachableHostedValue(parentConstant, finalPosition) : null;
                        ImageHeapConstant baseLayerConstant = getOrCreateConstant(constantId, hostedConstant);
                        ensureHubInitialized(baseLayerConstant);

                        if (hostedConstant != null) {
                            addBaseLayerValueToImageHeap(baseLayerConstant, parentConstant, finalPosition);
                        }

                        /*
                         * The value needs to be published after the constant is added to the image
                         * heap, as a non-base layer constant could then be created.
                         */
                        values[finalPosition] = baseLayerConstant;
                        return baseLayerConstant;
                    });
                }
                case NULL_POINTER -> JavaConstant.NULL_POINTER;
                case NOT_MATERIALIZED ->
                    unsupportedReferencedConstant("Reading the value of a base layer constant which was not materialized in the base image", parentConstant, finalPosition);
                case PRIMITIVE_VALUE -> {
                    PrimitiveValueData.Loader pv = constantData.getPrimitiveValue();
                    yield JavaConstant.forPrimitive((char) pv.getTypeChar(), pv.getRawValue());
                }
                default -> throw GraalError.shouldNotReachHere("Unexpected constant reference: " + constantData.kind());
            };
        }
        return values;
    }

    private static AnalysisFuture<?> unsupportedReferencedConstant(String message, ImageHeapConstant parentConstant, int finalPosition) {
        return new AnalysisFuture<>(() -> {
            String errorMessage = message + ": ";
            if (parentConstant instanceof ImageHeapInstance instance) {
                AnalysisField field = getFieldFromIndex(instance, finalPosition);
                errorMessage += "reachable by reading field " + field + " of parent object constant: " + parentConstant;
            } else {
                errorMessage += "reachable by indexing at position " + finalPosition + " into parent array constant: " + parentConstant;
            }
            throw AnalysisError.shouldNotReachHere(errorMessage);
        });
    }

    private boolean delegateProcessing(ConstantReferenceData.Loader constantRef, Object[] values, int i) {
        if (constantRef.isMethodPointer() || constantRef.isMethodOffset()) {
            AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
                MethodRef ref;
                if (constantRef.isMethodPointer()) {
                    ConstantReferenceData.MethodPointer.Loader r = constantRef.getMethodPointer();
                    AnalysisMethod method = loader.getAnalysisMethodForBaseLayerId(r.getMethodId());
                    ref = new MethodPointer(method, r.getPermitsRewriteToPLT());
                } else {
                    int mid = constantRef.getMethodOffset().getMethodId();
                    ref = new MethodOffset(loader.getAnalysisMethodForBaseLayerId(mid));
                }
                AnalysisType refType = loader.metaAccess.lookupJavaType(ref.getClass());
                PatchedWordConstant constant = new PatchedWordConstant(ref, refType);
                values[i] = constant;
                return constant;
            });
            values[i] = task;
            return true;
        } else if (constantRef.isCEntryPointLiteralCodePointer()) {
            AnalysisType cEntryPointerLiteralPointerType = loader.metaAccess.lookupJavaType(CEntryPointLiteralCodePointer.class);
            CEntryPointLiteralReferenceData.Loader ref = constantRef.getCEntryPointLiteralCodePointer();
            String methodName = ref.getMethodName();
            Class<?> definingClass = OriginalClassProvider.getJavaClass(loader.lookupBaseLayerTypeInHostVM(ref.getDefiningClass()));
            Class<?>[] parameterTypes = SnapshotAdapters.toArray(ref.getParameterNames(), a -> OriginalClassProvider.getJavaClass(loader.lookupBaseLayerTypeInHostVM(a)), Class[]::new);
            values[i] = new PatchedWordConstant(new CEntryPointLiteralCodePointer(definingClass, methodName, parameterTypes), cEntryPointerLiteralPointerType);
            return true;
        } else if (constantRef.isCGlobalDataBasePointer()) {
            values[i] = new AnalysisFuture<>(() -> {
                throw AnalysisError.shouldNotReachHere("Reading the CGlobalData base address of the base image is not implemented.");
            });
            return true;
        }
        return false;
    }

    /**
     * For a parent constant return the referenced field-position or array-element-index value
     * corresponding to <code>index</code>.
     */
    private JavaConstant getReachableHostedValue(ImageHeapConstant parentConstant, int index) {
        if (parentConstant instanceof ImageHeapObjectArray array) {
            return getHostedElementValue(array, index);
        } else if (parentConstant instanceof ImageHeapInstance instance) {
            AnalysisField field = getFieldFromIndex(instance, index);
            return getHostedFieldValue(instance, field);
        } else {
            throw AnalysisError.shouldNotReachHere("unexpected constant: " + parentConstant);
        }
    }

    private JavaConstant getHostedElementValue(ImageHeapObjectArray array, int idx) {
        JavaConstant hostedArray = array.getHostedObject();
        JavaConstant rawElementValue = null;
        if (hostedArray != null) {
            rawElementValue = loader.hostedValuesProvider.readArrayElement(hostedArray, idx);
        }
        return rawElementValue;
    }

    private JavaConstant getHostedFieldValue(ImageHeapInstance instance, AnalysisField field) {
        ValueSupplier<JavaConstant> rawFieldValue;
        try {
            JavaConstant hostedInstance = instance.getHostedObject();
            AnalysisError.guarantee(hostedInstance != null);
            rawFieldValue = loader.hostedValuesProvider.readFieldValue(field, hostedInstance);
        } catch (InternalError | TypeNotPresentException | LinkageError e) {
            /* Ignore missing type errors. */
            return null;
        }
        return rawFieldValue.get();
    }

    private static AnalysisField getFieldFromIndex(ImageHeapInstance instance, int i) {
        return (AnalysisField) instance.getType().getInstanceFields(true)[i];
    }

    private void addBaseLayerObject(int id, long objectOffset, Supplier<ImageHeapConstant> imageHeapConstantSupplier) {
        constants.computeIfAbsent(id, _ -> {
            ImageHeapConstant heapObj = imageHeapConstantSupplier.get();
            heapObj.markInSharedLayer();
            /*
             * Packages are normally rescanned when the DynamicHub is initialized. However, since
             * they are not relinked, the packages from the base layer will never be marked as
             * reachable without doing so manually.
             */
            if (heapObj.getType().getJavaClass().equals(Package.class)) {
                ScanReason reason = new OtherReason("Object loaded from base layer");
                loader.universe.getHeapScanner().doScan(heapObj, reason);
            }
            if (objectOffset != -1) {
                objectOffsets.put(ImageHeapConstant.getConstantID(heapObj), objectOffset);
                heapObj.markWrittenInPreviousLayer();
            }
            return heapObj;
        });
    }

    /**
     * Look up an object in current hosted VM based on the recipe serialized from the base layer.
     */
    private JavaConstant lookupHostedObject(PersistedConstantData.Loader baseLayerConstant, AnalysisType analysisType) {
        if (baseLayerConstant.getIsSimulated()) {
            return null;
        }
        if (!baseLayerConstant.isObject()) {
            return null;
        }
        RelinkingData.Loader relinking = baseLayerConstant.getObject().getRelinking();
        if (relinking.isNotRelinked()) {
            return null;
        } else if (relinking.isFieldConstant()) {
            var fieldConstant = relinking.getFieldConstant();
            AnalysisField analysisField = loader.getAnalysisFieldForBaseLayerId(fieldConstant.getOriginFieldId());
            if (shouldRelinkField(analysisField)) {
                VMError.guarantee(!baseLayerConstant.getIsSimulated(), "Cannot relink simulated constants.");
                /*
                 * The declaring type of relinked fields was already initialized in the previous
                 * layer (see SVMImageLayerWriter#shouldRelinkField).
                 */
                VMError.guarantee(analysisField.getDeclaringClass().isInitialized());
                /* Read fields through the hostedValueProvider and apply object replacement. */
                JavaConstant javaConstant = loader.hostedValuesProvider.readFieldValueWithReplacement(analysisField, null);
                VMError.guarantee(javaConstant.isNonNull(), "Found NULL_CONSTANT when reading the hosted value of relinked field %s. " +
                                "Since relinked fields should have a concrete non-null value there may be a class initialization mismatch.", analysisField);
                return javaConstant;
            }
        } else if (loader.universe.getBigbang().getMetaAccess().lookupJavaType(Class.class).equals(analysisType)) {
            /* DynamicHub corresponding to $$TypeSwitch classes are not relinked */
            if (baseLayerConstant.isObject() && relinking.isClassConstant()) {
                int typeId = relinking.getClassConstant().getTypeId();
                return getDynamicHub(typeId);
            }
        } else if (loader.universe.getBigbang().getMetaAccess().lookupJavaType(String.class).equals(analysisType)) {
            assert relinking.isStringConstant();
            StringConstant.Loader stringConstant = relinking.getStringConstant();
            if (stringConstant.hasValue()) {
                String value = stringConstant.getValue();
                JavaConstant stringValue = getStringConstant(value);
                return GuestAccess.get().invoke(INTERN_METHOD, stringValue);
            }
        } else if (loader.universe.getBigbang().getMetaAccess().lookupJavaType(Enum.class).isAssignableFrom(analysisType)) {
            assert relinking.isEnumConstant();
            EnumConstant.Loader enumConstant = relinking.getEnumConstant();
            return getEnumValue(enumConstant.getEnumClass(), enumConstant.getEnumName());
        }
        return null;
    }

    private static boolean shouldRelinkField(AnalysisField field) {
        VMError.guarantee(field.isInSharedLayer());
        return !(field.getWrapped() instanceof BaseLayerField) && !AnnotationUtil.isAnnotationPresent(field, Delete.class);
    }

    private JavaConstant getEnumValue(String className, String name) {
        ResolvedJavaType enumType = imageLayerBuildingSupport.lookupType(false, className);
        return GuestAccess.get().invoke(VALUE_OF_METHOD, null, getClassConstant(enumType), getStringConstant(name));
    }

    private void addBaseLayerValueToImageHeap(ImageHeapConstant constant, ImageHeapConstant parentConstant, int i) {
        switch (parentConstant) {
            case ImageHeapInstance imageHeapInstance -> loader.universe.getHeapScanner().registerBaseLayerValue(constant, getFieldFromIndex(imageHeapInstance, i));
            case ImageHeapObjectArray _ -> loader.universe.getHeapScanner().registerBaseLayerValue(constant, i);
            case ImageHeapRelocatableConstant _ -> {
                // skip - nothing to do
            }
            case null, default -> throw AnalysisError.shouldNotReachHere("unexpected constant: " + constant);
        }
    }

    private void ensureHubInitialized(ImageHeapConstant constant) {
        if (constant instanceof ImageHeapRelocatableConstant) {
            // not a hub
            return;
        }

        if (loader.metaAccess.isInstanceOf(constant, DynamicHub.class)) {
            AnalysisType type = (AnalysisType) loader.universe.getBigbang().getConstantReflectionProvider().asJavaType(constant);
            ensureHubInitialized(type);
            /*
             * If the persisted hub has a non-null arrayHub, the corresponding DynamicHub must be
             * created and the initializeMetaDataTask needs to be executed to ensure the hosted
             * object matches the persisted constant.
             */
            PersistedAnalysisTypeData.Loader typeData = findType(getBaseLayerTypeId(type));
            if (typeData != null && typeData.getHasArrayType()) {
                AnalysisType arrayClass = type.getArrayClass();
                ensureHubInitialized(arrayClass);
            }
        }
    }

    private static void ensureHubInitialized(AnalysisType type) {
        type.registerAsReachable(PERSISTED);
        type.getInitializeMetaDataTask().ensureDone();
    }

    private PersistedAnalysisTypeData.Loader findType(int tid) {
        return SnapshotAdapters.binarySearchUnique(tid, snapshot.getTypes(), PersistedAnalysisTypeData.Loader::getId);
    }

    private static int getBaseLayerTypeId(AnalysisType type) {
        if (type.getWrapped() instanceof BaseLayerType baseLayerType) {
            return baseLayerType.getBaseLayerId();
        }
        return type.getId();
    }

    Long getObjectOffset(JavaConstant javaConstant) {
        ImageHeapConstant imageHeapConstant = (ImageHeapConstant) javaConstant;
        return objectOffsets.get(ImageHeapConstant.getConstantID(imageHeapConstant));
    }

    ImageHeapConstant getBaseLayerStaticPrimitiveFields() {
        return getOrCreateConstant(snapshot.getStaticPrimitiveFieldsConstantId());
    }

    ImageHeapConstant getBaseLayerStaticObjectFields() {
        return getOrCreateConstant(snapshot.getStaticObjectFieldsConstantId());
    }

    private JavaConstant getDynamicHub(int tid) {
        AnalysisType type = loader.getAnalysisTypeForBaseLayerId(tid);
        return loader.hostedValuesProvider.forObject(typeToHub(type));
    }

    private static JavaConstant getClassConstant(ResolvedJavaType type) {
        return GuestAccess.get().getProviders().getConstantReflection().asJavaClass(OriginalClassProvider.getOriginalType(type));
    }

    private static void injectIdentityHashCode(JavaConstant constant, Integer identityHashCode) {
        if (constant == null || identityHashCode == null) {
            return;
        }

        ConstantReflectionProvider constantReflection = GuestAccess.get().getProviders().getConstantReflection();
        int actualHashCode = constantReflection.makeIdentityHashCode(constant, identityHashCode);
        if (actualHashCode != identityHashCode) {
            if (LayeredImageOptions.LayeredImageDiagnosticOptions.LogHashCodeInjectionFailure.getValue()) {
                LogUtils.warning("Object %s already has identity hash code %d when trying to set it to %d", constant, actualHashCode, identityHashCode);
            }
        }
    }

    JavaConstant readConstant(ConstantReferenceData.Loader constantReference) {
        return switch (constantReference.kind()) {
            case OBJECT_CONSTANT -> {
                int id = constantReference.getObjectConstant().getConstantId();
                yield id == 0 ? null : getOrCreateConstant(id);
            }
            case NULL_POINTER -> JavaConstant.NULL_POINTER;
            case PRIMITIVE_VALUE -> {
                PrimitiveValueData.Loader pv = constantReference.getPrimitiveValue();
                yield JavaConstant.forPrimitive((char) pv.getTypeChar(), pv.getRawValue());
            }
            default -> throw GraalError.shouldNotReachHere("Unexpected constant reference: " + constantReference.kind());
        };
    }
}
