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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
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

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.json.JSONParser;
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
    /**
     * The AnalysisUniverse.createType method can be called multiple times for same type. We need to
     * ensure the constants are only created once, so we keep track of the types that were already
     * processed. Some types can be created before loadLayerConstants, so some types can be
     * processed before they are force loaded.
     */
    private final Set<Integer> processedTypeIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> processedFieldsIds = ConcurrentHashMap.newKeySet();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    private final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();
    private final List<Path> loadPaths;
    /**
     * Map from a missing constant id to all the constants that depend on it. A constant is missing
     * if its {@link AnalysisType} was not created yet, but its parent constant was already created.
     * This can happen in both {@link ImageHeapInstance} and {@link ImageHeapObjectArray}, if the
     * type of one field or one of the objects was not yet created. In this case, an AnalysisFuture
     * that looks up the constants and assigns it in the missing place is created and stored in this
     * map.
     * <p>
     * This map could be removed if the constants were created recursively using a
     * {@link BaseLayerType}. However, it is easier to use this at the moment, as the types are
     * loaded before the analysis.
     */
    private final ConcurrentHashMap<Integer, Set<AnalysisFuture<JavaConstant>>> missingConstantTasks = new ConcurrentHashMap<>();
    /**
     * Map from a missing type id to a set of tasks that depends on it. These tasks are created when
     * a constant containing a DynamicHub cannot be relinked as the DynamicHub is not created yet.
     * All the tasks corresponding to the missing type are executed when the DynamicHub is created.
     */
    public final ConcurrentHashMap<Integer, Set<AnalysisFuture<Void>>> missingTypeTasks = new ConcurrentHashMap<>();
    private final Map<AnalysisType, Object> claims = new ConcurrentHashMap<>();
    /**
     * Map from a missing method id to all the constants that depend on it. A method is missing when
     * a constant contains a method pointer and the corresponding {@link AnalysisMethod} was not
     * created yet. In this case, an {@link AnalysisFuture} that looks up the method and creates the
     * missing constant is created and stored in this map.
     */
    protected final ConcurrentHashMap<Integer, Set<AnalysisFuture<JavaConstant>>> missingMethodTasks = new ConcurrentHashMap<>();
    private final Map<Integer, String> typeToIdentifier = new HashMap<>();
    protected final Set<AnalysisFuture<Void>> heapScannerTasks = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Set<Integer>> typeToConstants = new HashMap<>();
    private final ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final Map<AnalysisType, Set<ImageHeapConstant>> baseLayerImageHeap = new ConcurrentHashMap<>();
    protected final Map<JavaConstant, ImageHeapConstant> relinkedConstants = new ConcurrentHashMap<>();
    protected final Map<Integer, Long> objectOffsets = new ConcurrentHashMap<>();
    protected final Map<AnalysisField, Integer> fieldLocations = new ConcurrentHashMap<>();
    protected AnalysisUniverse universe;
    protected AnalysisMetaAccess metaAccess;
    protected HostedValuesProvider hostedValuesProvider;

    protected EconomicMap<String, Object> jsonMap;

    public ImageLayerLoader() {
        this(new ImageLayerSnapshotUtil(), List.of());
    }

    public ImageLayerLoader(ImageLayerSnapshotUtil imageLayerSnapshotUtil, List<Path> loadPaths) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.loadPaths = loadPaths;
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
                    Object json = new JSONParser(inputStreamReader).parse();
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

        /* This mapping allows one to get the base layer information from a type id */
        EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
        MapCursor<String, Object> typesCursor = typesMap.getEntries();
        while (typesCursor.advance()) {
            EconomicMap<String, Object> typeData = getValue(typesCursor);
            int tid = get(typeData, ID_TAG);
            typeToIdentifier.put(tid, typesCursor.getKey());
        }

        /*
         * The dependencies link between the constants is stored in typeToConstants allowing to
         * easily look it up when creating the constants.
         */
        EconomicMap<String, Object> constantsMap = get(jsonMap, CONSTANTS_TAG);
        MapCursor<String, Object> constantsCursor = constantsMap.getEntries();
        while (constantsCursor.advance()) {
            String stringId = constantsCursor.getKey();
            int id = Integer.parseInt(stringId);
            EconomicMap<String, Object> constantData = getValue(constantsCursor);
            int tid = get(constantData, TID_TAG);
            typeToConstants.computeIfAbsent(tid, k -> new HashSet<>()).add(id);
        }
    }

    /**
     * Creates all the constants from the base layer.
     * <p>
     * In the future, all the constants should not be eagerly created. All the constants of a given
     * type should still be created via
     * {@link ImageLayerLoader#loadAndRelinkTypeConstants(AnalysisType)} on the type creation, but
     * the types should not be forcibly loaded. This mixed approach is used at the moment as it is
     * easier to implement for the prototype, and it is useful for testing purposes.
     */
    public void loadLayerConstants() {
        EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
        MapCursor<String, Object> typesCursor = typesMap.getEntries();
        while (typesCursor.advance()) {
            EconomicMap<String, Object> typeData = getValue(typesCursor);
            int tid = get(typeData, ID_TAG);
            /*
             * Some types like Object or String are processed on demand (see
             * loadAndRelinkTypeConstants) because they are created early in the build process.
             */
            if (!processedTypeIds.contains(tid)) {
                loadTypeConstants(typeData, tid);
            }
        }
    }

    private void loadTypeConstants(EconomicMap<String, Object> typeData, int tid) {
        String name = get(typeData, CLASS_JAVA_NAME_TAG);
        Class<?> clazz = lookupBaseLayerTypeInHostVM(name);

        if (clazz != null) {
            /*
             * If the type can be looked up by name, the constants can be directly loaded and
             * relinked.
             */
            AnalysisType type = metaAccess.lookupJavaType(clazz);
            loadAndRelinkTypeConstants(type);
        } else {
            /*
             * If the type cannot be looked up by name, the constants are created using an
             * incomplete AnalysisType, which uses a BaseLayerType in its wrapped field.
             */
            String className = get(typeData, CLASS_NAME_TAG);
            int modifiers = get(typeData, MODIFIERS_TAG);
            boolean isInterface = get(typeData, IS_INTERFACE_TAG);
            boolean isEnum = get(typeData, IS_ENUM_TAG);
            boolean isInitialized = get(typeData, IS_INITIALIZED_TAG);
            boolean isLinked = get(typeData, IS_LINKED_TAG);
            String sourceFileName = get(typeData, SOURCE_FILE_NAME_TAG);

            Integer enclosingTid = get(typeData, ENCLOSING_TYPE_TAG);
            ResolvedJavaType enclosingType = processType(enclosingTid);

            Integer componentTid = get(typeData, COMPONENT_TYPE_TAG);
            ResolvedJavaType componentType = processType(componentTid);

            Integer superClassTid = get(typeData, SUPER_CLASS_TAG);
            ResolvedJavaType superClass = processType(superClassTid);

            List<Integer> interfacesIds = get(typeData, INTERFACES_TAG);
            ResolvedJavaType[] interfaces = interfacesIds.stream().map(this::processType).toList().toArray(new ResolvedJavaType[0]);

            ResolvedJavaType objectType = universe.getOriginalMetaAccess().lookupJavaType(Object.class);

            BaseLayerType baseLayerType = new BaseLayerType(className, tid, modifiers, isInterface, isEnum, isInitialized, isLinked, sourceFileName, enclosingType, componentType, superClass,
                            interfaces, objectType);
            AnalysisType type = universe.lookup(baseLayerType);

            /*
             * The constant cannot be relinked if the proper AnalysisType is not created yet. The
             * constants are only created and the type is tracked to relink the constants if it is
             * created later.
             */
            loadTypeConstants(type);
            processedTypeIds.add(tid);
        }
    }

    private ResolvedJavaType processType(Integer tid) {
        ResolvedJavaType type = null;
        if (tid != null) {
            if (!universe.isTypeCreated(tid)) {
                /*
                 * The type is loaded if it was not created yet. Calling loadTypeConstants does not
                 * cause issue about duplicated constants as it adds the type id in processedTypeIds
                 * and the set is queried before calling loadTypeConstants in other places.
                 */
                EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
                loadTypeConstants(get(typesMap, typeToIdentifier.get(tid)), tid);
            }
            guarantee(universe.isTypeCreated(tid));
            type = universe.getType(tid).getWrapped();
        }
        return type;
    }

    /**
     * Returns the type id of the given type in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine which constant
     * should be linked to this type.
     */
    public int lookupHostedTypeInBaseLayer(AnalysisType type) {
        int id = getBaseLayerTypeId(type);
        if (id == -1 || universe.isTypeCreated(id)) {
            /* A complete type is treated as a different type than its incomplete version */
            return -1;
        }
        return id;
    }

    public int getBaseLayerTypeId(AnalysisType type) {
        String typeIdentifier = imageLayerSnapshotUtil.getTypeIdentifier(type);
        if (type.getWrapped() instanceof BaseLayerType) {
            /*
             * If the type is from the base layer, we remove the BASE_LAYER_SUFFIX from the
             * typeIdentifier as the name would not be found in the map otherwise.
             */
            typeIdentifier = typeIdentifier.substring(0, typeIdentifier.length() - BaseLayerType.BASE_LAYER_SUFFIX.length() + 1);
        }
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

    public void loadAndRelinkTypeConstants(AnalysisType type) {
        if (!(type.getWrapped() instanceof BaseLayerType)) {
            /*
             * Types can be created before loadLayerConstants, in which case the constants have to
             * be loaded before new constants are created.
             */
            if (processedTypeIds.add(type.getId())) {
                loadTypeConstants(type);
            }
            relinkTypeConstants(type);
        }
    }

    /**
     * Creates all the constants for the given type.
     */
    private void loadTypeConstants(AnalysisType type) {
        EconomicMap<String, Object> constantsMap = get(jsonMap, CONSTANTS_TAG);
        for (int constantId : typeToConstants.getOrDefault(type.getId(), Set.of())) {
            createConstant(constantsMap, String.valueOf(constantId), type);
        }
    }

    /**
     * Re-links the constants for the given type.
     */
    private void relinkTypeConstants(AnalysisType analysisType) {
        int id = analysisType.getId();
        Set<AnalysisFuture<Void>> tasks = missingTypeTasks.getOrDefault(id, Set.of());
        if (!tasks.isEmpty()) {
            Object claim = claims.put(analysisType, Thread.currentThread().getName());
            if (claim == null || !claim.equals(Thread.currentThread().getName())) {
                for (AnalysisFuture<Void> task : tasks) {
                    if (claim == null) {
                        /*
                         * Only the thread that got the claim can execute the tasks. Some tasks can
                         * look up the AnalysisType, which causes this method to be executed again.
                         * The thread that got the claim will skip the recursive execution of the
                         * tasks, but another thread would try to execute them again and cause a
                         * deadlock.
                         */
                        task.ensureDone();
                    } else {
                        task.guardedGet();
                    }
                }
                missingTypeTasks.remove(id);
                claims.remove(analysisType);
            }
        }
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

    private void createConstant(EconomicMap<String, Object> constantsMap, String stringId, AnalysisType type) {
        int id = Integer.parseInt(stringId);
        EconomicMap<String, Object> baseLayerConstant = get(constantsMap, stringId);
        if (baseLayerConstant == null) {
            throw GraalError.shouldNotReachHere("The constant was not reachable in the base image");
        }
        String objectOffset = get(baseLayerConstant, OBJECT_OFFSET_TAG);
        String constantType = get(baseLayerConstant, CONSTANT_TYPE_TAG);
        switch (constantType) {
            case INSTANCE_TAG -> {
                List<List<Object>> instanceData = get(baseLayerConstant, DATA_TAG);
                ImageHeapInstance imageHeapInstance = new ImageHeapInstance(type, null);
                Object[] fieldValues = getReferencedValues(imageHeapInstance, instanceData, imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess));
                addBaseLayerObject(type, id, imageHeapInstance, objectOffset);
                imageHeapInstance.setFieldValues(fieldValues);
                relinkConstant(imageHeapInstance, baseLayerConstant, type);
                /*
                 * Packages are normally rescanned when the DynamicHub is initialized. However,
                 * since they are not relinked, the packages from the base layer will never be
                 * marked as reachable without doing so manually.
                 */
                if (imageHeapInstance.getType().getJavaClass().equals(Package.class)) {
                    universe.getHeapScanner().doScan(imageHeapInstance);
                }
            }
            case ARRAY_TAG -> {
                List<List<Object>> arrayData = get(baseLayerConstant, DATA_TAG);
                ImageHeapObjectArray imageHeapObjectArray = new ImageHeapObjectArray(type, null, arrayData.size());
                Object[] elementsValues = getReferencedValues(imageHeapObjectArray, arrayData, Set.of());
                addBaseLayerObject(type, id, imageHeapObjectArray, objectOffset);
                imageHeapObjectArray.setElementValues(elementsValues);
            }
            case PRIMITIVE_ARRAY_TAG -> {
                List<Object> primitiveData = get(baseLayerConstant, DATA_TAG);
                Object array = getArray(type.getComponentType().getJavaKind(), primitiveData);
                ImageHeapPrimitiveArray imageHeapPrimitiveArray = new ImageHeapPrimitiveArray(type, null, array, primitiveData.size());
                addBaseLayerObject(type, id, imageHeapPrimitiveArray, objectOffset);
            }
            default -> throw GraalError.shouldNotReachHere("Unknown constant type: " + constantType);
        }
        for (AnalysisFuture<JavaConstant> task : missingConstantTasks.getOrDefault(id, Set.of())) {
            task.ensureDone();
        }
        missingConstantTasks.remove(id);
    }

    protected void relinkConstant(ImageHeapInstance imageHeapInstance, EconomicMap<String, Object> baseLayerConstant, AnalysisType analysisType) {
        Class<?> clazz = analysisType.getJavaClass();
        boolean simulated = get(baseLayerConstant, SIMULATED_TAG);
        if (!simulated) {
            relinkConstant(imageHeapInstance, baseLayerConstant, clazz);
        }
    }

    @SuppressWarnings("unchecked")
    protected void relinkConstant(ImageHeapInstance imageHeapInstance, EconomicMap<String, Object> baseLayerConstant, Class<?> clazz) {
        if (clazz.equals(String.class)) {
            String value = get(baseLayerConstant, VALUE_TAG);
            if (value != null) {
                relinkConstant(value.intern(), imageHeapInstance);
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            String className = get(baseLayerConstant, ENUM_CLASS_TAG);
            Class<?> enumClass = ReflectionUtil.lookupClass(false, className);
            String name = get(baseLayerConstant, ENUM_NAME_TAG);
            /* asSubclass produces an "unchecked" warning */
            Enum<?> enumValue = Enum.valueOf(enumClass.asSubclass(Enum.class), name);
            relinkConstant(enumValue, imageHeapInstance);
        }
    }

    protected void relinkConstant(Object object, ImageHeapInstance imageHeapInstance) {
        JavaConstant hostedObject = hostedValuesProvider.forObject(object);
        imageHeapInstance.setHostedObject(hostedObject);
        relinkedConstants.put(hostedObject, imageHeapInstance);
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

    private Object[] getReferencedValues(ImageHeapConstant parentConstant, List<List<Object>> data, Set<Integer> positionsToRelink) {
        Object[] values = new Object[data.size()];
        for (int position = 0; position < data.size(); ++position) {
            int finalPosition = position;
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
                    ImageHeapConstant constant = constants.get(constantId);
                    if (constant != null) {
                        setConstant(parentConstant, values, position, constant, relink);
                    } else {
                        values[position] = new AnalysisFuture<JavaConstant>(() -> {
                            throw AnalysisError.shouldNotReachHere("The constant should be loaded before being accessed.");
                        });

                        AnalysisFuture<JavaConstant> linkingConstantTask = new AnalysisFuture<>(() -> {
                            ImageHeapConstant createdConstant = constants.get(constantId);
                            guarantee(createdConstant != null);
                            setConstant(parentConstant, values, finalPosition, createdConstant, relink);
                            return createdConstant;
                        });
                        missingConstantTasks.computeIfAbsent(constantId, unused -> ConcurrentHashMap.newKeySet()).add(linkingConstantTask);

                        /* If the constant was published in the meantime just set it. */
                        if (constants.containsKey(constantId)) {
                            linkingConstantTask.ensureDone();
                            /*
                             * It is safe to remove the entry from the map because the tasks that
                             * were added too late (e.g. after missingConstantTasks.getOrDefault)
                             * will all be executed here since the constant has been registered in
                             * the constants map at this point.
                             */
                            missingConstantTasks.remove(constantId);
                        }
                    }
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

    private void setConstant(ImageHeapConstant parentConstant, Object[] values, int i, ImageHeapConstant constant, boolean relink) {
        /*
         * Install future that knows how to relink the constant. At this point we can assume that
         * the parent is already linked if it will ever be linked.
         */
        values[i] = new AnalysisFuture<>(() -> {
            values[i] = constant;
            ensureHubInitialized(constant);
            ensureHubInitialized(parentConstant);
            if (relink) {
                universe.getHeapScanner().linkBaseLayerValue(parentConstant, i, constant);
            }
            return constant;
        });
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

    private void addBaseLayerObject(AnalysisType type, int id, ImageHeapConstant heapObj, String objectOffset) {
        heapObj.markInBaseLayer();
        constants.put(id, heapObj);
        baseLayerImageHeap.computeIfAbsent(type, t -> ConcurrentHashMap.newKeySet()).add(heapObj);
        if (objectOffset != null) {
            objectOffsets.put(heapObj.constantData.id, Long.parseLong(objectOffset));
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
        /*
         * Look up the type of the constant to ensure all the base layer constants of this type are
         * created as it could be missed otherwise.
         */
        try {
            universe.getBigbang().getMetaAccess().lookupJavaType(javaConstant);
        } catch (UnsupportedFeatureException e) {
            /* Ignore unsupported type errors. */
        }
        return relinkedConstants.containsKey(javaConstant);
    }

    public ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        return relinkedConstants.get(javaConstant);
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
        return constants.get(id);
    }
}
