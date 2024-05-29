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
package com.oracle.graal.pointsto.heap;

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_JAVA_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.COMPONENT_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANTS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANTS_TO_RELINK_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANT_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.DATA_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENCLOSING_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENUM_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENUM_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_ACCESSED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_FOLDED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_READ_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_WRITTEN_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_HEAP_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INSTANCE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTERFACES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_ENUM_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERFACE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_LINKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHODS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.MODIFIERS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_FIELD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_METHOD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_TYPE_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NOT_MATERIALIZED_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NULL_POINTER_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PRIMITIVE_ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SIMULATED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SOURCE_FILE_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SUPER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TYPES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.VALUE_TAG;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.FileDumpingUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.json.JSONFormatter;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public class ImageLayerWriter {
    public static final String TYPE_SWITCH_SUBSTRING = "$$TypeSwitch";
    private final ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageHeap imageHeap;
    /**
     * Contains the same array as StringInternSupport#imageInternedStrings, which is sorted.
     */
    private String[] imageInternedStrings;

    protected EconomicMap<String, Object> jsonMap = EconomicMap.create();
    protected List<Integer> constantsToRelink;
    FileInfo fileInfo;

    private record FileInfo(Path layerSnapshotPath, String fileName, String suffix) {
    }

    public ImageLayerWriter() {
        this.imageLayerSnapshotUtil = new ImageLayerSnapshotUtil();
    }

    public ImageLayerWriter(ImageLayerSnapshotUtil imageLayerSnapshotUtil) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.constantsToRelink = new ArrayList<>();
    }

    public void setImageInternedStrings(String[] imageInternedStrings) {
        this.imageInternedStrings = imageInternedStrings;
    }

    public void setImageHeap(ImageHeap heap) {
        this.imageHeap = heap;
    }

    /**
     * $$TypeSwitch classes do not have a stable name between different JVM instances, which makes
     * it hard to match them in two image build processes.
     * <p>
     * Those classes are only created to be a container for the static typeSwitch method used in the
     * method handle for the corresponding switch. The only way to distinguish two $$TypeSwitch
     * classes is to look at the code of the generated method and the class in which the hidden
     * class is defined. Since the class is not cached, doing so might not be enough as if two
     * identical switches are in the same class, they would create two $$TypeSwitch classes that
     * would not be distinguishable.
     */
    protected static boolean isTypeSwitch(AnalysisType type) {
        return type.toJavaName().contains(TYPE_SWITCH_SUBSTRING);
    }

    public void setFileInfo(Path layerSnapshotPath, String fileName, String suffix) {
        fileInfo = new FileInfo(layerSnapshotPath, fileName, suffix);
    }

    public void dumpFile() {
        FileDumpingUtil.dumpFile(fileInfo.layerSnapshotPath, fileInfo.fileName, fileInfo.suffix, writer -> JSONFormatter.printJSON(jsonMap, writer));
    }

    public void persistImageHeapSize(long imageHeapSize) {
        jsonMap.put(IMAGE_HEAP_SIZE_TAG, String.valueOf(imageHeapSize));
    }

    public void persistAnalysisInfo(Universe hostedUniverse, AnalysisUniverse analysisUniverse) {
        persistHook(hostedUniverse, analysisUniverse);

        jsonMap.put(NEXT_TYPE_ID_TAG, analysisUniverse.getNextTypeId());
        jsonMap.put(NEXT_METHOD_ID_TAG, analysisUniverse.getNextMethodId());
        jsonMap.put(NEXT_FIELD_ID_TAG, analysisUniverse.getNextFieldId());

        /*
         * $$TypeSwitch classes should not be instantiated as they are only used as a container for
         * a static method, so no constant of those types should be created. This filter can be
         * removed after a mechanism for determining which types have to be persisted is added, or
         * if a stable name is implemented for them.
         */
        EconomicMap<String, Object> typesMap = EconomicMap.create();
        for (AnalysisType type : analysisUniverse.getTypes().stream().filter(t -> t.isReachable() && !isTypeSwitch(t)).toList()) {
            checkTypeStability(type);
            String typeIdentifier = imageLayerSnapshotUtil.getTypeIdentifier(type);
            persistType(typesMap, type, typeIdentifier);
        }
        jsonMap.put(TYPES_TAG, typesMap);

        EconomicMap<String, Object> methodsMap = EconomicMap.create();
        for (AnalysisMethod method : analysisUniverse.getMethods().stream().filter(m -> m.isReachable() && !isTypeSwitch(m.getDeclaringClass())).toList()) {
            persistMethod(methodsMap, method);
        }
        jsonMap.put(METHODS_TAG, methodsMap);

        EconomicMap<String, EconomicMap<String, Object>> fieldsMap = EconomicMap.create();
        for (AnalysisField field : analysisUniverse.getFields().stream().filter(AnalysisField::isReachable).toList()) {
            persistField(fieldsMap, field, hostedUniverse);
        }
        jsonMap.put(FIELDS_TAG, fieldsMap);

        EconomicMap<String, Object> constantsMap = EconomicMap.create();
        for (Map.Entry<AnalysisType, Set<ImageHeapConstant>> entry : imageHeap.getReachableObjects().entrySet()) {
            for (ImageHeapConstant imageHeapConstant : entry.getValue()) {
                persistConstant(analysisUniverse, imageHeapConstant, constantsMap);
            }
        }
        jsonMap.put(CONSTANTS_TAG, constantsMap);
        jsonMap.put(CONSTANTS_TO_RELINK_TAG, constantsToRelink);
    }

    /**
     * A hook used to persist more general information about the base layer not accessible in
     * pointsto.
     */
    @SuppressWarnings("unused")
    protected void persistHook(Universe hostedUniverse, AnalysisUniverse analysisUniverse) {

    }

    private static void persistType(EconomicMap<String, Object> typesMap, AnalysisType type, String typeIdentifier) {
        EconomicMap<String, Object> typeMap = EconomicMap.create();
        typeMap.put(ID_TAG, type.getId());
        List<Integer> fields = new ArrayList<>();
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            fields.add(((AnalysisField) field).getId());
        }
        typeMap.put(FIELDS_TAG, fields);
        typeMap.put(CLASS_JAVA_NAME_TAG, type.toJavaName());
        typeMap.put(CLASS_NAME_TAG, type.getName());
        typeMap.put(MODIFIERS_TAG, type.getModifiers());
        typeMap.put(IS_INTERFACE_TAG, type.isInterface());
        typeMap.put(IS_ENUM_TAG, type.isEnum());
        typeMap.put(IS_INITIALIZED_TAG, type.isInitialized());
        typeMap.put(IS_LINKED_TAG, type.isLinked());
        typeMap.put(SOURCE_FILE_NAME_TAG, type.getSourceFileName());
        if (type.getEnclosingType() != null) {
            typeMap.put(ENCLOSING_TYPE_TAG, type.getEnclosingType().getId());
        }
        if (type.isArray()) {
            typeMap.put(COMPONENT_TYPE_TAG, type.getComponentType().getId());
        }
        if (type.getSuperclass() != null) {
            typeMap.put(SUPER_CLASS_TAG, type.getSuperclass().getId());
        }
        typeMap.put(INTERFACES_TAG, Arrays.stream(type.getInterfaces()).map(AnalysisType::getId).toList());
        if (typesMap.containsKey(typeIdentifier)) {
            throw GraalError.shouldNotReachHere("The type identifier should be unique, but " + typeIdentifier + " got added twice.");
        }
        typesMap.put(typeIdentifier, typeMap);
    }

    /**
     * Some types can have an unstable name between two different image builds. To avoid producing
     * wrong results, a warning should be printed if such types exist in the resulting image.
     */
    @SuppressWarnings("unused")
    public void checkTypeStability(AnalysisType type) {
        /* Do not need to check anything here */
    }

    public void persistMethod(EconomicMap<String, Object> methodsMap, AnalysisMethod method) {
        EconomicMap<String, Object> methodMap = EconomicMap.create();
        methodMap.put(ID_TAG, method.getId());
        String name = imageLayerSnapshotUtil.getMethodIdentifier(method);
        if (methodsMap.containsKey(name)) {
            throw GraalError.shouldNotReachHere("The method identifier should be unique, but " + name + " got added twice.");
        }
        methodsMap.put(name, methodMap);
    }

    private void persistField(EconomicMap<String, EconomicMap<String, Object>> fieldsMap, AnalysisField field, Universe hostedUniverse) {
        EconomicMap<String, Object> fieldMap = EconomicMap.create();
        fieldMap.put(ID_TAG, field.getId());
        fieldMap.put(FIELD_ACCESSED_TAG, field.getAccessedReason() != null);
        fieldMap.put(FIELD_READ_TAG, field.getReadReason() != null);
        fieldMap.put(FIELD_WRITTEN_TAG, field.getWrittenReason() != null);
        fieldMap.put(FIELD_FOLDED_TAG, field.getFoldedReason() != null);

        persistFieldHook(fieldMap, field, hostedUniverse);

        String tid = String.valueOf(field.getDeclaringClass().getId());
        if (fieldsMap.containsKey(tid)) {
            fieldsMap.get(tid).put(field.getName(), fieldMap);
        } else {
            EconomicMap<String, Object> typeFieldsMap = EconomicMap.create();
            typeFieldsMap.put(field.getName(), fieldMap);
            fieldsMap.put(tid, typeFieldsMap);
        }
    }

    /**
     * A hook used to persist more field information not accessible in pointsto.
     */
    @SuppressWarnings("unused")
    protected void persistFieldHook(EconomicMap<String, Object> fieldMap, AnalysisField field, Universe hostedUniverse) {

    }

    private void persistConstant(AnalysisUniverse analysisUniverse, ImageHeapConstant imageHeapConstant, EconomicMap<String, Object> constantsMap) {
        if (imageHeapConstant.isReaderInstalled() && !constantsMap.containsKey(Integer.toString(getConstantId(imageHeapConstant)))) {
            EconomicMap<String, Object> constantMap = EconomicMap.create();
            persistConstant(analysisUniverse, imageHeapConstant, constantMap, constantsMap);
        }
    }

    protected void persistConstant(AnalysisUniverse analysisUniverse, ImageHeapConstant imageHeapConstant, EconomicMap<String, Object> constantMap, EconomicMap<String, Object> constantsMap) {
        constantsMap.put(Integer.toString(getConstantId(imageHeapConstant)), constantMap);
        constantMap.put(TID_TAG, imageHeapConstant.getType().getId());
        if (imageHeapConstant.hasIdentityHashCode()) {
            constantMap.put(IDENTITY_HASH_CODE_TAG, imageHeapConstant.getIdentityHashCode());
        }

        switch (imageHeapConstant) {
            case ImageHeapInstance imageHeapInstance -> {
                persistConstant(analysisUniverse, constantsMap, constantMap, INSTANCE_TAG, imageHeapInstance.getFieldValues());
                persistConstantRelinkingInfo(constantMap, imageHeapConstant, analysisUniverse.getBigbang());
            }
            case ImageHeapObjectArray imageHeapObjectArray ->
                persistConstant(analysisUniverse, constantsMap, constantMap, ARRAY_TAG, imageHeapObjectArray.getElementValues());
            case ImageHeapPrimitiveArray imageHeapPrimitiveArray -> {
                constantMap.put(CONSTANT_TYPE_TAG, PRIMITIVE_ARRAY_TAG);
                constantMap.put(DATA_TAG, getString(imageHeapPrimitiveArray.getType().getComponentType().getJavaKind(), imageHeapPrimitiveArray.getArray()));
            }
            default -> throw AnalysisError.shouldNotReachHere("Unexpected constant type " + imageHeapConstant);
        }
    }

    protected int getConstantId(ImageHeapConstant imageHeapConstant) {
        return imageHeapConstant.constantData.id;
    }

    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, ImageHeapConstant imageHeapConstant, BigBang bb) {
        Class<?> clazz = imageHeapConstant.getType().getJavaClass();
        JavaConstant hostedObject = imageHeapConstant.getHostedObject();
        boolean simulated = hostedObject == null;
        constantMap.put(SIMULATED_TAG, simulated);
        if (!simulated) {
            persistConstantRelinkingInfo(constantMap, bb, clazz, hostedObject, imageHeapConstant.constantData.id);
        }
    }

    @SuppressFBWarnings(value = "ES", justification = "Reference equality check needed to detect intern status")
    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id) {
        if (clazz.equals(String.class)) {
            String value = bb.getSnippetReflectionProvider().asObject(String.class, hostedObject);
            int stringIndex = Arrays.binarySearch(imageInternedStrings, value);
            /*
             * Arrays.binarySearch compares the strings by value. A comparison by reference is
             * needed here as only interned strings are relinked.
             */
            if (stringIndex >= 0 && imageInternedStrings[stringIndex] == value) {
                constantMap.put(VALUE_TAG, value);
                constantsToRelink.add(id);
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            Enum<?> value = bb.getSnippetReflectionProvider().asObject(Enum.class, hostedObject);
            constantMap.put(ENUM_CLASS_TAG, value.getDeclaringClass().getName());
            constantMap.put(ENUM_NAME_TAG, value.name());
            constantsToRelink.add(id);
        }
    }

    private static List<?> getString(JavaKind kind, Object arrayObject) {
        return switch (kind) {
            case Boolean -> IntStream.range(0, ((boolean[]) arrayObject).length).mapToObj(idx -> ((boolean[]) arrayObject)[idx]).toList();
            case Byte -> IntStream.range(0, ((byte[]) arrayObject).length).mapToObj(idx -> ((byte[]) arrayObject)[idx]).toList();
            case Short -> IntStream.range(0, ((short[]) arrayObject).length).mapToObj(idx -> ((short[]) arrayObject)[idx]).toList();
            case Char -> new String((char[]) arrayObject).chars().boxed().toList();
            case Int -> Arrays.stream((int[]) arrayObject).boxed().toList();
            /* Have to persist it as a String as it would be converted to an Integer otherwise */
            case Long -> Arrays.stream(((long[]) arrayObject)).mapToObj(String::valueOf).toList();
            /* Have to persist it as a String as it would be converted to a Double otherwise */
            case Float -> IntStream.range(0, ((float[]) arrayObject).length).mapToObj(idx -> String.valueOf(((float[]) arrayObject)[idx])).toList();
            case Double -> Arrays.stream(((double[]) arrayObject)).mapToObj(String::valueOf).toList();
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    protected void persistConstant(AnalysisUniverse analysisUniverse, EconomicMap<String, Object> constantsMap, EconomicMap<String, Object> constantMap, String constantType, Object[] values) {
        constantMap.put(CONSTANT_TYPE_TAG, constantType);
        List<List<Object>> data = new ArrayList<>();
        for (Object object : values) {
            if (delegateProcessing(data, object)) {
                /* The object was already persisted */
            } else if (object instanceof ImageHeapConstant imageHeapConstant) {
                data.add(List.of(OBJECT_TAG, getConstantId(imageHeapConstant)));
                /*
                 * Some constants are not in imageHeap#reachableObjects, but are still created in
                 * reachable constants. They can be created in the extension image, but should not
                 * be used.
                 */
                persistConstant(analysisUniverse, imageHeapConstant, constantsMap);
            } else if (object == JavaConstant.NULL_POINTER) {
                data.add(List.of(OBJECT_TAG, NULL_POINTER_CONSTANT));
            } else if (object instanceof PrimitiveConstant primitiveConstant) {
                JavaKind kind = primitiveConstant.getJavaKind();
                data.add(List.of(kind.getTypeChar(), getPrimitiveConstantValue(primitiveConstant, kind)));
            } else {
                AnalysisError.guarantee(object instanceof AnalysisFuture<?>, "Unexpected constant %s", object);
                data.add(List.of(OBJECT_TAG, NOT_MATERIALIZED_CONSTANT));
            }
        }
        constantMap.put(DATA_TAG, data);
    }

    private static Object getPrimitiveConstantValue(PrimitiveConstant primitiveConstant, JavaKind kind) {
        return switch (kind) {
            case Boolean, Byte, Short, Int, Double -> primitiveConstant.getRawValue();
            /*
             * Have to persist it as a String as it would be converted to an Integer or a Double
             * otherwise
             */
            case Char, Long, Float -> String.valueOf(primitiveConstant.getRawValue());
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    /**
     * Hook for subclasses to do their own processing.
     */
    @SuppressWarnings("unused")
    protected boolean delegateProcessing(List<List<Object>> data, Object constant) {
        return false;
    }
}
