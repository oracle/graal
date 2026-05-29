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

import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.DYNAMIC_HUB;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.ENUM;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.STRING;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_CONSTANT_ID;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_FIELD_INDEX;
import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initInts;
import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initSortedArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.graal.code.CGlobalDataBasePointer;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocal;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.hosted.snapshot.c.CEntryPointLiteralReferenceData;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData;
import com.oracle.svm.hosted.snapshot.constant.PersistedConstantData.ObjectValue;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData.EnumConstant;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData.FieldConstant;
import com.oracle.svm.hosted.snapshot.constant.RelinkingData.StringConstant;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class SVMImageConstantSnapshotWriter {
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final NativeImageHeap nativeImageHeap;
    private final AnalysisUniverse aUniverse;
    private final IdentityHashMap<String, String> internedStringsIdentityMap;
    private Map<ImageHeapConstant, ConstantParent> constantsMap;

    private record ConstantParent(int constantId, int index) {
        static ConstantParent NONE = new ConstantParent(UNDEFINED_CONSTANT_ID, UNDEFINED_FIELD_INDEX);
    }

    SVMImageConstantSnapshotWriter(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, NativeImageHeap nativeImageHeap, AnalysisUniverse aUniverse,
                    IdentityHashMap<String, String> internedStringsIdentityMap) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.nativeImageHeap = nativeImageHeap;
        this.aUniverse = aUniverse;
        this.internedStringsIdentityMap = internedStringsIdentityMap;
    }

    void collectConstants(ImageHeap imageHeap) {
        List<ImageHeapConstant> constantsToScan = new ArrayList<>();
        imageHeap.getReachableObjects().values().forEach(constantsToScan::addAll);
        constantsMap = HashMap.newHashMap(constantsToScan.size());
        constantsToScan.forEach(c -> constantsMap.put(c, ConstantParent.NONE));
        /*
         * Some child constants of reachable constants are not reachable because they are only used
         * in snippets, but still need to be persisted.
         */
        while (!constantsToScan.isEmpty()) {
            List<ImageHeapConstant> discoveredConstants = new ArrayList<>();
            constantsToScan.forEach(con -> scanConstantReferencedObjects(con, discoveredConstants));
            constantsToScan = discoveredConstants;
        }
    }

    void writeConstants(SharedLayerSnapshotData.Writer snapshotWriter) {
        @SuppressWarnings({"unchecked", "cast"})
        Entry<ImageHeapConstant, ConstantParent>[] constantsToPersist = (Entry<ImageHeapConstant, ConstantParent>[]) constantsMap.entrySet().stream()
                        .sorted(Comparator.comparingInt(a -> ImageHeapConstant.getConstantID(a.getKey())))
                        .toArray(Entry[]::new);
        Set<Integer> constantsToRelink = new HashSet<>(); // noEconomicSet(streaming)
        initSortedArray(snapshotWriter::initConstants, constantsToPersist,
                        (entry, builderSupplier) -> persistConstant(entry.getKey(), entry.getValue(), builderSupplier.get(), constantsToRelink));
        initInts(snapshotWriter::initConstantsToRelink, constantsToRelink.stream().mapToInt(i -> i).sorted());
    }

    void writeConstant(JavaConstant constant, ConstantReferenceData.Writer builder) {
        if (constant == null) {
            return;
        }
        if (!maybeWriteConstant(constant, builder)) {
            throw VMError.shouldNotReachHere("Unexpected constant: " + constant);
        }
    }

    private void persistConstant(ImageHeapConstant imageHeapConstant, ConstantParent parent, PersistedConstantData.Writer builder, Set<Integer> constantsToRelink) {
        ObjectInfo objectInfo = nativeImageHeap.getConstantInfo(imageHeapConstant);
        builder.setObjectOffset((objectInfo == null) ? -1 : objectInfo.getOffset());

        int id = ImageHeapConstant.getConstantID(imageHeapConstant);
        builder.setId(id);
        AnalysisType type = imageHeapConstant.getType();
        AnalysisError.guarantee(type.isTrackedAcrossLayers(), "Type %s from constant %s should have been marked as trackedAcrossLayers, but was not", type, imageHeapConstant);
        builder.setTypeId(type.getId());

        ConstantReflectionProvider constantReflection = aUniverse.getBigbang().getConstantReflectionProvider();
        int identityHashCode = constantReflection.identityHashCode(imageHeapConstant);
        builder.setIdentityHashCode(identityHashCode);

        switch (imageHeapConstant) {
            case ImageHeapInstance imageHeapInstance -> {
                builder.initObject().setInstance();
                persistConstantObjectData(builder.getObject(), imageHeapInstance::getFieldValue, imageHeapInstance.getFieldValuesSize());
                persistConstantRelinkingInfo(builder, imageHeapConstant, constantsToRelink, aUniverse.getBigbang());
            }
            case ImageHeapObjectArray imageHeapObjectArray -> {
                builder.initObject().setObjectArray();
                persistConstantObjectData(builder.getObject(), imageHeapObjectArray::getElement, imageHeapObjectArray.getLength());
            }
            case ImageHeapPrimitiveArray imageHeapPrimitiveArray ->
                SnapshotPrimitiveArrays.write(builder.initPrimitiveData(), imageHeapPrimitiveArray.getType().getComponentType().getJavaKind(), imageHeapPrimitiveArray.getArray());
            case ImageHeapRelocatableConstant relocatableConstant ->
                builder.initRelocatable().setKey(relocatableConstant.getConstantData().key);
            default -> throw AnalysisError.shouldNotReachHere("Unexpected constant type " + imageHeapConstant);
        }

        if (!constantsToRelink.contains(id) && parent != ConstantParent.NONE) {
            builder.setParentConstantId(parent.constantId);
            assert parent.index != UNDEFINED_FIELD_INDEX : "Tried to persist child constant %s from parent constant %d, but got index %d".formatted(imageHeapConstant, parent.constantId, parent.index);
            builder.setParentIndex(parent.index);
        }
    }

    private void persistConstantRelinkingInfo(PersistedConstantData.Writer builder, ImageHeapConstant imageHeapConstant, Set<Integer> constantsToRelink, BigBang bb) {
        AnalysisType type = imageHeapConstant.getType();
        JavaConstant hostedObject = imageHeapConstant.getHostedObject();
        boolean simulated = hostedObject == null;
        builder.setIsSimulated(simulated);
        if (!simulated) {
            RelinkingData.Writer relinkingBuilder = builder.getObject().getRelinking();
            int id = ImageHeapConstant.getConstantID(imageHeapConstant);
            boolean tryStaticFinalFieldRelink = true;
            if (aUniverse.lookup(DYNAMIC_HUB).equals(type)) {
                AnalysisType constantType = (AnalysisType) bb.getConstantReflectionProvider().asJavaType(hostedObject);
                relinkingBuilder.initClassConstant().setTypeId(constantType.getId());
                constantsToRelink.add(id);
                tryStaticFinalFieldRelink = false;
            } else if (aUniverse.lookup(STRING).equals(type)) {
                StringConstant.Writer stringConstantBuilder = relinkingBuilder.initStringConstant();
                String value = bb.getSnippetReflectionProvider().asObject(String.class, hostedObject);
                if (internedStringsIdentityMap.containsKey(value)) {
                    /*
                     * Interned strings must be relinked.
                     */
                    stringConstantBuilder.setValue(value);
                    constantsToRelink.add(id);
                    tryStaticFinalFieldRelink = false;
                }
            } else if (aUniverse.lookup(ENUM).isAssignableFrom(type)) {
                EnumConstant.Writer enumBuilder = relinkingBuilder.initEnumConstant();
                Enum<?> value = bb.getSnippetReflectionProvider().asObject(Enum.class, hostedObject);
                enumBuilder.setEnumClass(value.getDeclaringClass().getName());
                enumBuilder.setEnumName(value.name());
                constantsToRelink.add(id);
                tryStaticFinalFieldRelink = false;
            }
            if (tryStaticFinalFieldRelink && shouldRelinkConstant(imageHeapConstant) && imageHeapConstant.getOrigin() != null) {
                AnalysisField field = imageHeapConstant.getOrigin();
                if (shouldRelinkField(field)) {
                    FieldConstant.Writer fieldConstantBuilder = relinkingBuilder.initFieldConstant();
                    fieldConstantBuilder.setOriginFieldId(field.getId());
                    fieldConstantBuilder.setRequiresLateLoading(requiresLateLoading(imageHeapConstant, field));
                }
            }
        }
    }

    private boolean shouldRelinkConstant(ImageHeapConstant heapConstant) {
        /*
         * FastThreadLocals need to be registered by the object replacer and relinking constants
         * from the CrossLayerRegistry would skip the custom code associated.
         */
        Object o = aUniverse.getHostedValuesProvider().asObject(Object.class, heapConstant.getHostedObject());
        return !(o instanceof FastThreadLocal) && !CrossLayerConstantRegistryFeature.singleton().isConstantRegistered(o);
    }

    private static boolean shouldRelinkField(AnalysisField field) {
        return !AnnotationUtil.isAnnotationPresent(field, Delete.class) &&
                        ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(field.getDeclaringClass()) &&
                        field.isStatic() && field.isFinal() && field.isTrackedAcrossLayers() && field.installableInLayer();
    }

    private static boolean requiresLateLoading(ImageHeapConstant imageHeapConstant, AnalysisField field) {
        /*
         * CustomSubstitutionTypes need to be loaded after the substitution are installed.
         *
         * Intercepted fields need to be loaded after the interceptor is installed.
         */
        return imageHeapConstant.getType().getWrapped() instanceof CustomSubstitutionType ||
                        FieldValueInterceptionSupport.hasFieldValueInterceptor(field);
    }

    private void persistConstantObjectData(ObjectValue.Writer builder, IntFunction<Object> valuesFunction, int size) {
        SnapshotStructList.Writer<ConstantReferenceData.Writer> refsBuilder = builder.initData(size);
        for (int i = 0; i < size; ++i) {
            Object object = valuesFunction.apply(i);
            ConstantReferenceData.Writer referenceBuilder = refsBuilder.get(i);
            if (delegateProcessing(referenceBuilder, object)) {
                /* The object was already persisted */
                continue;
            }
            if (object instanceof JavaConstant javaConstant && maybeWriteConstant(javaConstant, referenceBuilder)) {
                continue;
            }
            AnalysisError.guarantee(object instanceof AnalysisFuture<?>, "Unexpected constant %s", object);
            referenceBuilder.setNotMaterialized();
        }
    }

    private boolean maybeWriteConstant(JavaConstant constant, ConstantReferenceData.Writer builder) {
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            assert constantsMap.containsKey(imageHeapConstant) : imageHeapConstant;
            var objectConstantBuilder = builder.initObjectConstant();
            objectConstantBuilder.setConstantId(ImageHeapConstant.getConstantID(imageHeapConstant));
        } else if (constant instanceof PrimitiveConstant primitiveConstant) {
            var primitiveValueBuilder = builder.initPrimitiveValue();
            primitiveValueBuilder.setTypeChar(NumUtil.safeToUByte(primitiveConstant.getJavaKind().getTypeChar()));
            primitiveValueBuilder.setRawValue(primitiveConstant.getRawValue());
        } else if (constant.equals(JavaConstant.NULL_POINTER)) {
            builder.setNullPointer();
        } else {
            return false;
        }
        return true;
    }

    private static boolean delegateProcessing(ConstantReferenceData.Writer builder, Object constant) {
        if (constant instanceof PatchedWordConstant patchedWordConstant) {
            WordBase word = patchedWordConstant.getWord();
            if (word instanceof MethodRef methodRef) {
                AnalysisMethod method = getRelocatableConstantMethod(methodRef);
                switch (methodRef) {
                    case MethodOffset _ -> builder.initMethodOffset().setMethodId(method.getId());
                    case MethodPointer methodPointer -> {
                        ConstantReferenceData.MethodPointer.Writer methodPointerBuilder = builder.initMethodPointer();
                        methodPointerBuilder.setMethodId(method.getId());
                        methodPointerBuilder.setPermitsRewriteToPLT(methodPointer.permitsRewriteToPLT());
                    }
                    default -> throw VMError.shouldNotReachHere("Unsupported method ref: " + methodRef);
                }
                return true;
            } else if (word instanceof CEntryPointLiteralCodePointer codePointer) {
                CEntryPointLiteralReferenceData.Writer codePointerBuilder = builder.initCEntryPointLiteralCodePointer();
                codePointerBuilder.setMethodName(codePointer.methodName);
                codePointerBuilder.setDefiningClass(codePointer.definingClass.getName());
                codePointerBuilder.initParameterNames(codePointer.parameterTypes.length);
                for (int i = 0; i < codePointer.parameterTypes.length; i++) {
                    codePointerBuilder.getParameterNames().set(i, codePointer.parameterTypes[i].getName());
                }
                return true;
            } else if (word instanceof CGlobalDataBasePointer) {
                builder.setCGlobalDataBasePointer();
                return true;
            }
        }
        return false;
    }

    private void scanConstantReferencedObjects(ImageHeapConstant constant, Collection<ImageHeapConstant> discoveredConstants) {
        if (Objects.requireNonNull(constant) instanceof ImageHeapInstance instance) {
            scanConstantReferencedObjects(constant, instance::getFieldValue, instance.getFieldValuesSize(), discoveredConstants);
        } else if (constant instanceof ImageHeapObjectArray objArray) {
            scanConstantReferencedObjects(constant, objArray::getElement, objArray.getLength(), discoveredConstants);
        }
    }

    private void scanConstantReferencedObjects(ImageHeapConstant constant, IntFunction<Object> referencedObjectFunction, int size, Collection<ImageHeapConstant> discoveredConstants) {
        for (int i = 0; i < size; i++) {
            AnalysisType parentType = constant.getType();
            Object obj = referencedObjectFunction.apply(i);
            if (obj instanceof ImageHeapConstant childConstant && !constantsMap.containsKey(childConstant)) {
                /*
                 * Some constants are not in imageHeap#reachableObjects, but are still created in
                 * reachable constants. They can be created in the extension image, but should not
                 * be used.
                 */
                Set<Integer> relinkedFields = imageLayerSnapshotUtil.getRelinkedFields(parentType, aUniverse);
                ConstantParent parent = relinkedFields.contains(i) ? new ConstantParent(ImageHeapConstant.getConstantID(constant), i) : ConstantParent.NONE;

                discoveredConstants.add(childConstant);
                constantsMap.put(childConstant, parent);
            } else if (obj instanceof MethodRef methodRef) {
                getRelocatableConstantMethod(methodRef).registerAsTrackedAcrossLayers("In method ref");
            }
        }
    }

    private static AnalysisMethod getRelocatableConstantMethod(MethodRef methodRef) {
        ResolvedJavaMethod method = methodRef.getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return hostedMethod.wrapped;
        } else {
            return (AnalysisMethod) method;
        }
    }
}
