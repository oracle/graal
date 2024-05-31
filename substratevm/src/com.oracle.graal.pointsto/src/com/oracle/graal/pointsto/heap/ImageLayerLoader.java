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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_HEAP_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INSTANCE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTERFACES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_ENUM_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERFACE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_LINKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LOCATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHODS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.MODIFIERS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_FIELD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_METHOD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_TYPE_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NOT_MATERIALIZED_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NULL_POINTER_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_OFFSET_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PRIMITIVE_ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SIMULATED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SOURCE_FILE_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_OBJECT_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_PRIMITIVE_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SUPER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TYPES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.VALUE_TAG;
import static com.oracle.graal.pointsto.util.AnalysisError.guarantee;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Loads the base layer persisted by {@link ImageLayerWriter}. The format of the json file is the
 * following:
 *
 * <pre>
 * {
 *      "next type id": nextTypeId,
 *      "next method id": nextMethodId,
 *      "next field id": nextFieldId,
 *      "static primitive fields": staticPrimitiveFields.id,
 *      "static object fields": staticObjectFields.id,
 *      "image heap size": imageHeapSize,
 *      "types": {
 *          typeIdentifier: {
 *              "id": id,
 *              "fields": [ids...],
 *              "class java name": type.toJavaName(),
 *              "class name": type.getName(),
 *              "modifiers": modifiers,
 *              "is interface": isInterface,
 *              "source file name": sourceFileName,
 *              "enclosing type": enclosingTid,
 *              "component type": componentTid,
 *              "super class": superClassTid,
 *              "interfaces": [
 *                  interfaceTid,
 *                  ...
 *              ]
 *          },
 *          ...
 *      },
 *      "methods": {
 *          methodIdentifier: {
 *              "id": id
 *          },
 *          ...
 *      },
 *      "fields": {
 *          tid: {
 *              name: {
 *                  "id": id,
 *                  "accessed": accessed,
 *                  "read": read,
 *                  "written": written,
 *                  "folded": folded
 *                  (,"location": location)
 *              },
 *              ...
 *          },
 *          ...
 *      },
 *      "constants": {
 *          id: {
 *              "tid": tid,
 *              "identityHashCode": identityHashCode,
 *              "constant type": constantType,
 *              "data": [
 *                  [constantType, value],
 *                  ...
 *              ],
 *              "simulated": simulated
 *              (,"object offset": offset)
 *              (,"value": string)
 *              (,"enum class": enumClass)
 *              (,"enum name": enumValue)
 *              (,"class id": cid)
 *          }
 *      },
 *      "image singleton objects" : [
 *          objectID,
 *          "class name",
 *          { (key_value_store) }
 *      ],
 *      "image singleton keys" : [
 *          "key class name",
 *          persist_flags,
 *          objectID
 *      ]
 * }
 * </pre>
 *
 * For an {@link ImageHeapInstance} or an {@link ImageHeapObjectArray}, the "data" entry contains
 * constant ids, markers from {@link ImageLayerSnapshotUtil} or primitive value, stored in the form
 * of a two elements array. The first element is the constant type, which is the string
 * representation of the kind of the primitive value or a custom tag. The second element is the
 * primitive value, the constant id, the method id or a custom marker. For an
 * {@link ImageHeapPrimitiveArray} it contains the array itself. Interned {@link String} constants
 * are relinked in the extension image using their base layer "value". {@link Enum} values are
 * relinked in the extension image using their "enum class" and "enum name". {@link Class} constants
 * (DynamicHub) are relinked in the extension image using their type id.
 * <p>
 * Relinking a base layer {@link ImageHeapConstant} is finding the corresponding hosted object in
 * the extension image build process and storing it in the constant. This is only done for object
 * that can be created or found using a specific recipe. Some fields from those constant can then be
 * relinked using the value of the hosted object.
 * <p>
 * The "offset object" is the offset of the constant in the heap from the base layer.
 */
public class ImageLayerLoader {
    private final Set<Integer> processedFieldsIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, AnalysisType> types = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    protected final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();
    private final List<Path> loadPaths;
    private final Map<Integer, BaseLayerType> baseLayerTypes = new ConcurrentHashMap<>();
    /**
     * Map from a missing method id to all the constants that depend on it. A method is missing when
     * a constant contains a method pointer and the corresponding {@link AnalysisMethod} was not
     * created yet. In this case, an {@link AnalysisFuture} that looks up the method and creates the
     * missing constant is created and stored in this map.
     */
    protected final ConcurrentHashMap<Integer, Set<AnalysisFuture<JavaConstant>>> missingMethodTasks = new ConcurrentHashMap<>();
    private final Map<Integer, String> typeToIdentifier = new HashMap<>();
    protected final Set<AnalysisFuture<Void>> heapScannerTasks = ConcurrentHashMap.newKeySet();
    private final ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    protected final Map<Integer, Integer> typeToConstant = new ConcurrentHashMap<>();
    protected final Map<String, Integer> stringToConstant = new ConcurrentHashMap<>();
    protected final Map<Enum<?>, Integer> enumToConstant = new ConcurrentHashMap<>();
    protected final Map<Integer, Long> objectOffsets = new ConcurrentHashMap<>();
    protected final Map<AnalysisField, Integer> fieldLocations = new ConcurrentHashMap<>();
    protected AnalysisUniverse universe;
    protected AnalysisMetaAccess metaAccess;
    protected HostedValuesProvider hostedValuesProvider;

    protected EconomicMap<String, Object> jsonMap;

    private long imageHeapSize;

    public ImageLayerLoader() {
        this(new ImageLayerSnapshotUtil(), List.of());
    }

    public ImageLayerLoader(ImageLayerSnapshotUtil imageLayerSnapshotUtil, List<Path> loadPaths) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.loadPaths = loadPaths;
    }

    public List<Path> getLoadPaths() {
        return loadPaths;
    }

    public void setUniverse(AnalysisUniverse newUniverse) {
        this.universe = newUniverse;
    }

    /**
     * Note this code is not thread safe.
     */
    protected void loadJsonMap() {
        assert loadPaths.size() == 1 : "Currently only one path is supported for image layer loading " + loadPaths;
        if (jsonMap == null) {
            for (Path layerPath : loadPaths) {
                try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(layerPath.toFile()))) {
                    Object json = new JsonParser(inputStreamReader).parse();
                    jsonMap = cast(json);
                } catch (IOException e) {
                    throw AnalysisError.shouldNotReachHere("Error during image layer snapshot loading", e);
                }
            }
        }
    }

    public void loadLayerAnalysis() {
        loadJsonMap();
        loadLayerAnalysis0();
    }

    /**
     * Initializes the {@link ImageLayerLoader}.
     */
    private void loadLayerAnalysis0() {
        /*
         * The new ids of the extension image need to be different from the ones from the base
         * layer. The start id is set to the next id of the base layer.
         */
        int nextTypeId = get(jsonMap, NEXT_TYPE_ID_TAG);
        universe.setStartTypeId(nextTypeId);

        int nextMethodId = get(jsonMap, NEXT_METHOD_ID_TAG);
        universe.setStartMethodId(nextMethodId);

        int nextFieldId = get(jsonMap, NEXT_FIELD_ID_TAG);
        universe.setStartFieldId(nextFieldId);

        imageHeapSize = Long.parseLong(get(jsonMap, IMAGE_HEAP_SIZE_TAG));

        /* This mapping allows one to get the base layer information from a type id */
        EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
        MapCursor<String, Object> typesCursor = typesMap.getEntries();
        while (typesCursor.advance()) {
            EconomicMap<String, Object> typeData = getValue(typesCursor);
            int tid = get(typeData, ID_TAG);
            typeToIdentifier.put(tid, typesCursor.getKey());
        }

        EconomicMap<String, Object> constantsMap = get(jsonMap, CONSTANTS_TAG);
        List<Integer> constantsToRelink = get(jsonMap, CONSTANTS_TO_RELINK_TAG);
        for (int id : constantsToRelink) {
            EconomicMap<String, Object> constantData = get(constantsMap, String.valueOf(id));
            prepareConstantRelinking(constantData, id);
        }
    }

    @SuppressWarnings("unchecked")
    protected void prepareConstantRelinking(EconomicMap<String, Object> constantData, int id) {
        String value = get(constantData, VALUE_TAG);
        if (value != null) {
            stringToConstant.put(value, id);
        }

        String className = get(constantData, ENUM_CLASS_TAG);
        if (className != null) {
            Class<?> enumClass = ReflectionUtil.lookupClass(false, className);
            String name = get(constantData, ENUM_NAME_TAG);
            /* asSubclass produces an "unchecked" warning */
            Enum<?> enumValue = Enum.valueOf(enumClass.asSubclass(Enum.class), name);
            enumToConstant.put(enumValue, id);
        }
    }

    private void loadType(EconomicMap<String, Object> typeData) {
        int tid = get(typeData, ID_TAG);

        String name = get(typeData, CLASS_JAVA_NAME_TAG);
        Class<?> clazz = lookupBaseLayerTypeInHostVM(name);

        if (clazz != null) {
            /*
             * When looking up the class by name, the host VM will create the corresponding
             * AnalysisType. During this process, the method lookupHostedTypeInBaseLayer will be
             * called to see if the type already exists in the base layer. If it is the case, the id
             * from the base layer will be reused and the ImageLayerLoader#types map will be
             * populated.
             */
            metaAccess.lookupJavaType(clazz);
        }

        if (!types.containsKey(tid)) {
            /*
             * If the type cannot be looked up by name, an incomplete AnalysisType, which uses a
             * BaseLayerType in its wrapped field, has to be created
             */
            baseLayerTypes.computeIfAbsent(tid, (typeId) -> {
                String className = get(typeData, CLASS_NAME_TAG);
                int modifiers = get(typeData, MODIFIERS_TAG);
                boolean isInterface = get(typeData, IS_INTERFACE_TAG);
                boolean isEnum = get(typeData, IS_ENUM_TAG);
                boolean isInitialized = get(typeData, IS_INITIALIZED_TAG);
                boolean isLinked = get(typeData, IS_LINKED_TAG);
                String sourceFileName = get(typeData, SOURCE_FILE_NAME_TAG);

                Integer enclosingTid = get(typeData, ENCLOSING_TYPE_TAG);
                ResolvedJavaType enclosingType = getResolvedJavaType(enclosingTid);

                Integer componentTid = get(typeData, COMPONENT_TYPE_TAG);
                ResolvedJavaType componentType = getResolvedJavaType(componentTid);

                Integer superClassTid = get(typeData, SUPER_CLASS_TAG);
                ResolvedJavaType superClass = getResolvedJavaType(superClassTid);

                List<Integer> interfacesIds = get(typeData, INTERFACES_TAG);
                ResolvedJavaType[] interfaces = interfacesIds.stream().map(this::getResolvedJavaType).toList().toArray(new ResolvedJavaType[0]);

                ResolvedJavaType objectType = universe.getOriginalMetaAccess().lookupJavaType(Object.class);

                return new BaseLayerType(className, tid, modifiers, isInterface, isEnum, isInitialized, isLinked, sourceFileName, enclosingType, componentType, superClass, interfaces, objectType);
            });
            BaseLayerType baseLayerType = baseLayerTypes.get(tid);
            AnalysisType type = universe.lookup(baseLayerType);
            guarantee(getBaseLayerTypeId(type) == tid, "The base layer type %s is not correctly matched to the id %d", type, tid);
        }
    }

    private ResolvedJavaType getResolvedJavaType(Integer tid) {
        return tid == null ? null : getAnalysisType(tid).getWrapped();
    }

    protected AnalysisType getAnalysisType(Integer tid) {
        if (!types.containsKey(tid)) {
            EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
            loadType(get(typesMap, typeToIdentifier.get(tid)));
        }
        guarantee(types.containsKey(tid), "Type with id %d was not correctly loaded.", tid);
        /*
         * The type needs to be looked up because it ensures the type is completely created, as the
         * types Map is populated before the type is created.
         */
        return universe.lookup(types.get(tid).getWrapped());
    }

    /**
     * Returns the type id of the given type in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine which constant
     * should be linked to this type.
     */
    public int lookupHostedTypeInBaseLayer(AnalysisType type) {
        int id = getBaseLayerTypeId(type);
        if (id == -1 || types.putIfAbsent(id, type) != null) {
            /* A complete type is treated as a different type than its incomplete version */
            return -1;
        }
        return id;
    }

    private int getBaseLayerTypeId(AnalysisType type) {
        if (type.getWrapped() instanceof BaseLayerType baseLayerType) {
            return baseLayerType.getBaseLayerId();
        }
        String typeIdentifier = imageLayerSnapshotUtil.getTypeIdentifier(type);
        EconomicMap<String, Object> typeData = getElementData(TYPES_TAG, typeIdentifier);
        if (typeData == null) {
            /* The type was not reachable in the base image */
            return -1;
        }
        return get(typeData, ID_TAG);
    }

    /**
     * Tries to look up the base layer type in the current VM. Some types cannot be looked up by
     * name (for example $$Lambda types), so this method can return null.
     */
    private static Class<?> lookupBaseLayerTypeInHostVM(String type) {
        int arrayType = 0;
        String componentType = type;
        /*
         * We cannot look up an array type directly. We have to look up the component type and then
         * go back to the array type.
         */
        while (componentType.endsWith("[]")) {
            componentType = componentType.substring(0, componentType.length() - 2);
            arrayType++;
        }
        Class<?> clazz = lookupPrimitiveClass(componentType);
        if (clazz == null) {
            clazz = ReflectionUtil.lookupClass(true, componentType);
        }
        if (clazz == null) {
            return null;
        }
        while (arrayType > 0) {
            assert clazz != null;
            clazz = clazz.arrayType();
            arrayType--;
        }
        return clazz;
    }

    private static Class<?> lookupPrimitiveClass(String type) {
        return switch (type) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> null;
        };
    }

    /**
     * Returns the method id of the given method in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine the method used
     * in RelocatableConstants.
     */
    public int lookupHostedMethodInBaseLayer(AnalysisMethod analysisMethod) {
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        if (methodData == null) {
            /* The method was not reachable in the base image */
            return -1;
        }
        return get(methodData, ID_TAG);
    }

    /**
     * Executes the tasks waiting on a missing method.
     * <p>
     * Creates the RelocatableConstant waiting on the method or replaces the {@link BaseLayerMethod}
     * by the complete {@link AnalysisMethod}.
     */
    public void patchBaseLayerMethod(AnalysisMethod analysisMethod) {
        int id = analysisMethod.getId();
        methods.putIfAbsent(id, analysisMethod);

        /* Put the method reference in the RelocatableConstants that use a BaseLayerMethod */
        for (AnalysisFuture<JavaConstant> task : missingMethodTasks.getOrDefault(id, Set.of())) {
            task.ensureDone();
        }
        missingMethodTasks.remove(id);
    }

    private EconomicMap<String, Object> getMethodData(AnalysisMethod analysisMethod) {
        String name = imageLayerSnapshotUtil.getMethodIdentifier(analysisMethod);
        return getElementData(METHODS_TAG, name);
    }

    /**
     * Returns the field id of the given field in the base layer if it exists. This makes the link
     * between the base layer and the extension image as the id allows to set the flags of the
     * fields in the extension image.
     */
    public int lookupHostedFieldInBaseLayer(AnalysisField analysisField) {
        EconomicMap<String, Object> fieldData = getFieldData(analysisField);
        if (fieldData == null) {
            /* The field was not reachable in the base image */
            return -1;
        }
        return get(fieldData, ID_TAG);
    }

    public void loadFieldFlags(AnalysisField analysisField) {
        if (processedFieldsIds.add(analysisField.getId())) {
            EconomicMap<String, Object> fieldData = getFieldData(analysisField);

            if (fieldData == null) {
                /* The field was not reachable in the base image */
                return;
            }

            Integer location = get(fieldData, LOCATION_TAG);
            if (location != null) {
                fieldLocations.put(analysisField, location);
            }

            boolean isAccessed = get(fieldData, FIELD_ACCESSED_TAG);
            boolean isRead = get(fieldData, FIELD_READ_TAG);
            boolean isWritten = get(fieldData, FIELD_WRITTEN_TAG);
            boolean isFolded = get(fieldData, FIELD_FOLDED_TAG);

            if (!analysisField.isStatic() && (isAccessed || isRead)) {
                analysisField.getDeclaringClass().getInstanceFields(true);
            }
            registerFieldFlag(isAccessed, () -> analysisField.registerAsAccessed(PERSISTED));
            registerFieldFlag(isRead, () -> analysisField.registerAsRead(PERSISTED));
            registerFieldFlag(isWritten, () -> analysisField.registerAsWritten(PERSISTED));
            registerFieldFlag(isFolded, () -> analysisField.registerAsFolded(PERSISTED));
        }
    }

    private EconomicMap<String, Object> getFieldData(AnalysisField analysisField) {
        int tid = analysisField.getDeclaringClass().getId();
        EconomicMap<String, Object> typeFieldsMap = getElementData(FIELDS_TAG, Integer.toString(tid));
        if (typeFieldsMap == null) {
            /* The type has no reachable field */
            return null;
        }
        return get(typeFieldsMap, analysisField.getName());
    }

    private void registerFieldFlag(boolean flag, Runnable runnable) {
        if (flag) {
            if (universe.getBigbang() != null) {
                universe.getBigbang().postTask(debug -> runnable.run());
            } else {
                heapScannerTasks.add(new AnalysisFuture<>(runnable));
            }
        }
    }

    public void executeHeapScannerTasks() {
        guarantee(universe.getHeapScanner() != null, "Those tasks should only be executed when the bigbang is not null.");
        for (AnalysisFuture<Void> task : heapScannerTasks) {
            task.ensureDone();
        }
    }

    protected ImageHeapConstant getOrCreateConstant(EconomicMap<String, Object> constantsMap, int id) {
        if (constants.containsKey(id)) {
            return constants.get(id);
        }
        EconomicMap<String, Object> baseLayerConstant = get(constantsMap, Integer.toString(id));
        if (baseLayerConstant == null) {
            throw GraalError.shouldNotReachHere("The constant was not reachable in the base image");
        }

        int tid = get(baseLayerConstant, TID_TAG);
        AnalysisType type = getAnalysisType(tid);

        String objectOffset = get(baseLayerConstant, OBJECT_OFFSET_TAG);
        String constantType = get(baseLayerConstant, CONSTANT_TYPE_TAG);
        switch (constantType) {
            case INSTANCE_TAG -> {
                List<List<Object>> instanceData = get(baseLayerConstant, DATA_TAG);
                JavaConstant hostedObject = getHostedObject(baseLayerConstant, type);
                ImageHeapInstance imageHeapInstance = new ImageHeapInstance(type, hostedObject);
                Object[] fieldValues = getReferencedValues(constantsMap, imageHeapInstance, instanceData, imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess));
                imageHeapInstance.setFieldValues(fieldValues);
                addBaseLayerObject(id, imageHeapInstance, objectOffset);
            }
            case ARRAY_TAG -> {
                List<List<Object>> arrayData = get(baseLayerConstant, DATA_TAG);
                ImageHeapObjectArray imageHeapObjectArray = new ImageHeapObjectArray(type, null, arrayData.size());
                Object[] elementsValues = getReferencedValues(constantsMap, imageHeapObjectArray, arrayData, Set.of());
                imageHeapObjectArray.setElementValues(elementsValues);
                addBaseLayerObject(id, imageHeapObjectArray, objectOffset);
            }
            case PRIMITIVE_ARRAY_TAG -> {
                List<Object> primitiveData = get(baseLayerConstant, DATA_TAG);
                Object array = getArray(type.getComponentType().getJavaKind(), primitiveData);
                ImageHeapPrimitiveArray imageHeapPrimitiveArray = new ImageHeapPrimitiveArray(type, null, array, primitiveData.size());
                addBaseLayerObject(id, imageHeapPrimitiveArray, objectOffset);
            }
            default -> throw GraalError.shouldNotReachHere("Unknown constant type: " + constantType);
        }

        return constants.get(id);
    }

    protected JavaConstant getHostedObject(EconomicMap<String, Object> baseLayerConstant, AnalysisType analysisType) {
        Class<?> clazz = analysisType.getJavaClass();
        boolean simulated = get(baseLayerConstant, SIMULATED_TAG);
        if (!simulated) {
            return getHostedObject(baseLayerConstant, clazz);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected JavaConstant getHostedObject(EconomicMap<String, Object> baseLayerConstant, Class<?> clazz) {
        if (clazz.equals(String.class)) {
            String value = get(baseLayerConstant, VALUE_TAG);
            if (value != null) {
                return getHostedObject(value.intern());
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            String className = get(baseLayerConstant, ENUM_CLASS_TAG);
            Class<?> enumClass = ReflectionUtil.lookupClass(false, className);
            String name = get(baseLayerConstant, ENUM_NAME_TAG);
            /* asSubclass produces an "unchecked" warning */
            Enum<?> enumValue = Enum.valueOf(enumClass.asSubclass(Enum.class), name);
            return getHostedObject(enumValue);
        }
        return null;
    }

    protected JavaConstant getHostedObject(Object object) {
        return hostedValuesProvider.forObject(object);
    }

    @SuppressWarnings("unchecked")
    private static Object getArray(JavaKind kind, Object listObject) {
        return switch (kind) {
            case Boolean -> getBooleans((List<Boolean>) listObject);
            case Byte -> getBytes((List<Integer>) listObject);
            case Short -> getShorts((List<Integer>) listObject);
            case Char -> ((List<Integer>) listObject).stream().mapToInt(i -> i).mapToObj(i -> Character.toString((char) i)).collect(Collectors.joining()).toCharArray();
            case Int -> ((List<Integer>) listObject).stream().mapToInt(i -> i).toArray();
            case Long -> ((List<String>) listObject).stream().mapToLong(Long::parseLong).toArray();
            case Float -> getFloats((List<String>) listObject);
            case Double -> ((List<String>) listObject).stream().mapToDouble(Double::parseDouble).toArray();
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    private static float[] getFloats(List<String> listObject) {
        float[] primitiveFloats = new float[listObject.size()];
        for (int i = 0; i < listObject.size(); ++i) {
            primitiveFloats[i] = Float.parseFloat(listObject.get(i));
        }
        return primitiveFloats;
    }

    private static byte[] getBytes(List<Integer> listObject) {
        byte[] primitiveBytes = new byte[listObject.size()];
        for (int i = 0; i < listObject.size(); ++i) {
            primitiveBytes[i] = (byte) (int) listObject.get(i);
        }
        return primitiveBytes;
    }

    private static short[] getShorts(List<Integer> listObject) {
        short[] primitiveShorts = new short[listObject.size()];
        for (int i = 0; i < listObject.size(); ++i) {
            primitiveShorts[i] = (short) (int) listObject.get(i);
        }
        return primitiveShorts;
    }

    private static boolean[] getBooleans(List<Boolean> listObject) {
        boolean[] primitiveBooleans = new boolean[listObject.size()];
        for (int i = 0; i < listObject.size(); ++i) {
            primitiveBooleans[i] = listObject.get(i);
        }
        return primitiveBooleans;
    }

    private Object[] getReferencedValues(EconomicMap<String, Object> constantsMap, ImageHeapConstant parentConstant, List<List<Object>> data, Set<Integer> positionsToRelink) {
        Object[] values = new Object[data.size()];
        for (int position = 0; position < data.size(); ++position) {
            List<Object> constantData = data.get(position);
            String constantKind = (String) constantData.get(0);
            Object constantValue = constantData.get(1);
            if (delegateProcessing(constantKind, constantValue, values, position)) {
                continue;
            }
            if (constantKind.equals(OBJECT_TAG)) {
                int constantId = (int) constantValue;
                if (constantId >= 0) {
                    boolean relink = positionsToRelink.contains(position);
                    int finalPosition = position;
                    values[position] = new AnalysisFuture<>(() -> {
                        ImageHeapConstant constant = getOrCreateConstant(constantsMap, constantId);
                        setReferencedConstant(parentConstant, values, finalPosition, constant, relink);
                        return constant;
                    });
                } else if (constantId == NULL_POINTER_CONSTANT) {
                    values[position] = JavaConstant.NULL_POINTER;
                } else {
                    /*
                     * This constant is a field value or an object value that was not materialized
                     * in the base image.
                     */
                    guarantee(constantId == NOT_MATERIALIZED_CONSTANT);
                    values[position] = new AnalysisFuture<>(() -> {
                        throw AnalysisError.shouldNotReachHere("This constant was not materialized in the base image.");
                    });
                }
            } else {
                JavaKind kind = JavaKind.fromTypeString(constantKind);
                values[position] = getPrimitiveValue(kind, constantValue);
            }
        }
        return values;
    }

    /**
     * Hook for subclasses to do their own processing.
     */
    @SuppressWarnings("unused")
    protected boolean delegateProcessing(String constantType, Object constantValue, Object[] values, int i) {
        return false;
    }

    private void setReferencedConstant(ImageHeapConstant parentConstant, Object[] values, int i, ImageHeapConstant constant, boolean relink) {
        /*
         * At this point we can assume that the parent is already linked if it will ever be linked.
         */
        values[i] = constant;
        ensureHubInitialized(constant);
        ensureHubInitialized(parentConstant);
        if (relink) {
            universe.getHeapScanner().linkBaseLayerValue(parentConstant, i, constant);
        }
    }

    /**
     * Ensures the DynamicHub is consistent with the base layer value.
     */
    public void ensureHubInitialized(@SuppressWarnings("unused") ImageHeapConstant constant) {
        /* DynamicHub only exists in SVM, so the method does not need to do anything here. */
    }

    @SuppressWarnings("unused")
    public void rescanHub(AnalysisType type, Object hubObject) {
        /* DynamicHub only exists in SVM, so the method does not need to do anything here. */
    }

    private static PrimitiveConstant getPrimitiveValue(JavaKind kind, Object value) {
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean((int) value != 0);
            case Byte -> JavaConstant.forByte((byte) (int) value);
            case Short -> JavaConstant.forShort((short) (int) value);
            case Char -> JavaConstant.forChar((char) Integer.parseInt((String) value));
            case Int -> JavaConstant.forInt((int) value);
            case Long -> JavaConstant.forLong(Long.parseLong((String) value));
            case Float -> JavaConstant.forFloat(Float.parseFloat((String) value));
            case Double -> JavaConstant.forDouble(getDouble(value));
            default -> throw AnalysisError.shouldNotReachHere("Unexpected kind: " + kind);
        };
    }

    private static double getDouble(Object value) {
        if (value instanceof Integer integer) {
            guarantee(integer == 0);
            return 0;
        }
        return Double.longBitsToDouble((long) value);
    }

    private void addBaseLayerObject(int id, ImageHeapConstant heapObj, String objectOffset) {
        heapObj.markInBaseLayer();
        ImageHeapConstant constant = constants.putIfAbsent(id, heapObj);
        if (constant == null) {
            /*
             * Packages are normally rescanned when the DynamicHub is initialized. However, since
             * they are not relinked, the packages from the base layer will never be marked as
             * reachable without doing so manually.
             */
            if (heapObj.getType().getJavaClass().equals(Package.class)) {
                universe.getHeapScanner().doScan(heapObj);
            }
            if (objectOffset != null) {
                objectOffsets.put(heapObj.constantData.id, Long.parseLong(objectOffset));
            }
        }
    }

    private EconomicMap<String, Object> getElementData(String registry, String elementIdentifier) {
        EconomicMap<String, Object> innerMap = get(jsonMap, registry);
        if (innerMap == null) {
            return null;
        }
        return get(innerMap, elementIdentifier);
    }

    protected static <T> T get(EconomicMap<String, Object> innerMap, String elementIdentifier) {
        return cast(innerMap.get(elementIdentifier));
    }

    private static <T> T getValue(MapCursor<String, Object> mapCursor) {
        return cast(mapCursor.getValue());
    }

    @SuppressWarnings("unchecked")
    protected static <T> T cast(Object object) {
        return (T) object;
    }

    public boolean hasValueForConstant(JavaConstant javaConstant) {
        Object object = hostedValuesProvider.asObject(Object.class, javaConstant);
        return hasValueForObject(object);
    }

    @SuppressFBWarnings(value = "ES", justification = "Reference equality check needed to detect intern status")
    protected boolean hasValueForObject(Object object) {
        if (object instanceof String string) {
            return stringToConstant.containsKey(string) && string.intern() == string;
        } else if (object instanceof Enum<?>) {
            return enumToConstant.containsKey(object);
        }
        return false;
    }

    public ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        Object object = hostedValuesProvider.asObject(Object.class, javaConstant);
        return getValueForObject(object);
    }

    protected ImageHeapConstant getValueForObject(Object object) {
        if (object instanceof String string) {
            int id = stringToConstant.get(string);
            return getOrCreateConstant(id);
        } else if (object instanceof Enum<?>) {
            int id = enumToConstant.get(object);
            return getOrCreateConstant(id);
        }
        throw AnalysisError.shouldNotReachHere("The constant was not in the persisted heap.");
    }

    protected ImageHeapConstant getOrCreateConstant(int id) {
        return getOrCreateConstant(get(jsonMap, CONSTANTS_TAG), id);
    }

    public void setMetaAccess(AnalysisMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
    }

    public void setHostedValuesProvider(HostedValuesProvider hostedValuesProvider) {
        this.hostedValuesProvider = hostedValuesProvider;
    }

    public Set<Integer> getRelinkedFields(AnalysisType type) {
        return imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess);
    }

    public Long getObjectOffset(JavaConstant javaConstant) {
        ImageHeapConstant imageHeapConstant = (ImageHeapConstant) javaConstant;
        return objectOffsets.get(imageHeapConstant.constantData.id);
    }

    public int getFieldLocation(AnalysisField field) {
        return fieldLocations.get(field);
    }

    public ImageHeapConstant getBaseLayerStaticPrimitiveFields() {
        return getTaggedImageHeapConstant(STATIC_PRIMITIVE_FIELDS_TAG);
    }

    public ImageHeapConstant getBaseLayerStaticObjectFields() {
        return getTaggedImageHeapConstant(STATIC_OBJECT_FIELDS_TAG);
    }

    private ImageHeapConstant getTaggedImageHeapConstant(String tag) {
        int id = get(jsonMap, tag);
        return getOrCreateConstant(id);
    }

    public long getImageHeapSize() {
        return imageHeapSize;
    }
}
