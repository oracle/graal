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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ANALYSIS_PARSED_GRAPH_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARGUMENTS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARGUMENT_IDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CAN_BE_STATICALLY_BOUND_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_JAVA_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CODE_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.COMPONENT_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANTS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANTS_TO_RELINK_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTANT_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.DATA_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENCLOSING_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENUM_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ENUM_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_ACCESSED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_FOLDED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_READ_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_WRITTEN_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.HUB_IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_HEAP_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INSTANCE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTERFACES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTRINSIC_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_ENUM_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERFACE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERNAL_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_LINKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_SYNTHETIC_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_VAR_ARGS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LOCATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHODS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.MODIFIERS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_FIELD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_METHOD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_TYPE_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NOT_MATERIALIZED_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NULL_POINTER_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_OFFSET_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.POSITION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PRIMITIVE_ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RETURN_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SIMULATED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SOURCE_FILE_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_OBJECT_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_PRIMITIVE_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STRENGTHENED_GRAPH_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SUPER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TYPES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.VALUE_TAG;
import static com.oracle.graal.pointsto.util.AnalysisError.guarantee;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerField;
import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.util.ObjectCopier;
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
 *      "constants to relink": [ids...],
 *      "types": {
 *          typeIdentifier: {
 *              "id": id,
 *              "fields": [ids...],
 *              "hub identityHashCode": System.identityHashCode(hub),
 *              "class java name": type.toJavaName(),
 *              "class name": type.getName(),
 *              "modifiers": modifiers,
 *              "is interface": isInterface,
 *              "is enum": isEnum,
 *              "is initialized": isInitialized,
 *              "is linked": isLinked,
 *              "source file name": sourceFileName,
 *              "enclosing type": enclosingTid,
 *              "component type": componentTid,
 *              "super class": superClassTid,
 *              "interfaces": [
 *                  interfaceTid,
 *                  ...
 *              ],
 *              "annotations": [
 *                  annotationName,
 *                  ...
 *              ]
 *          },
 *          ...
 *      },
 *      "methods": {
 *          methodIdentifier: {
 *              "id": id,
 *              ("arguments": [
 *                  argumentName,
 *                  ...
 *              ],
 *              "class name": className,)
 *              "tid": tid,
 *              "argument ids": [
 *                  argumentId,
 *                  ...
 *              ],
 *              "id": id,
 *              "name": name,
 *              "return type": returnTypeId,
 *              "is varArg": isVarArg,
 *              "can be statically bound": canBeStaticallyBound,
 *              "modifiers": modifiers,
 *              "is constructor": isConstructor,
 *              "is synthetic": isSynthetic,
 *              "code size": codeSize,
 *              "compiled": compiled,
 *              "annotations": [
 *                  annotationName,
 *                  ...
 *              ]
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
 *                  "folded": folded,
 *                  "is internal": isInternal,
 *                  "field type": typeId,
 *                  "modifiers": modifiers,
 *                  "position": position,
 *                  "annotations": [
 *                      annotationName,
 *                      ...
 *                  ]
 *                  (,"class name": className)
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
    private final Map<Integer, AnalysisType> types = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisField> fields = new ConcurrentHashMap<>();
    protected final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();
    private final List<Path> loadPaths;
    private final Map<Integer, BaseLayerType> baseLayerTypes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> typeToHubIdentityHashCode = new HashMap<>();
    private final Map<Integer, BaseLayerMethod> baseLayerMethods = new ConcurrentHashMap<>();
    private final Map<Integer, String> typeIdToIdentifier = new HashMap<>();
    private final Map<Integer, String> methodIdToIdentifier = new HashMap<>();
    private final Map<Integer, FieldIdentifier> fieldIdToIdentifier = new HashMap<>();

    record FieldIdentifier(String tid, String name) {
    }

    protected final Set<AnalysisFuture<Void>> heapScannerTasks = ConcurrentHashMap.newKeySet();
    private final ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageLayerLoaderHelper imageLayerLoaderHelper;
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

    public void setImageLayerLoaderHelper(ImageLayerLoaderHelper imageLayerLoaderHelper) {
        this.imageLayerLoaderHelper = imageLayerLoaderHelper;
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

        /* Those mappings allow to get the base layer information from a type or method id */
        storeIdToIdentifier(TYPES_TAG, typeIdToIdentifier);
        storeIdToIdentifier(METHODS_TAG, methodIdToIdentifier);

        EconomicMap<String, Object> fieldsMap = get(jsonMap, FIELDS_TAG);
        MapCursor<String, Object> fieldsCursor = fieldsMap.getEntries();
        while (fieldsCursor.advance()) {
            EconomicMap<String, Object> typeData = getValue(fieldsCursor);
            MapCursor<String, Object> typeFieldsCursor = typeData.getEntries();
            while (typeFieldsCursor.advance()) {
                EconomicMap<String, Object> fieldData = getValue(typeFieldsCursor);
                int id = get(fieldData, ID_TAG);
                fieldIdToIdentifier.put(id, new FieldIdentifier(fieldsCursor.getKey(), typeFieldsCursor.getKey()));
            }
        }

        EconomicMap<String, Object> constantsMap = get(jsonMap, CONSTANTS_TAG);
        List<Integer> constantsToRelink = get(jsonMap, CONSTANTS_TO_RELINK_TAG);
        for (int id : constantsToRelink) {
            EconomicMap<String, Object> constantData = get(constantsMap, String.valueOf(id));
            int identityHashCode = get(constantData, IDENTITY_HASH_CODE_TAG);
            prepareConstantRelinking(constantData, identityHashCode, id);
        }
    }

    private void storeIdToIdentifier(String tag, Map<Integer, String> idToIdentifier) {
        EconomicMap<String, Object> elementsMap = get(jsonMap, tag);
        MapCursor<String, Object> cursor = elementsMap.getEntries();
        while (cursor.advance()) {
            EconomicMap<String, Object> data = getValue(cursor);
            int id = get(data, ID_TAG);
            idToIdentifier.put(id, cursor.getKey());
        }
    }

    protected void prepareConstantRelinking(EconomicMap<String, Object> constantData, int identityHashCode, int id) {
        String value = get(constantData, VALUE_TAG);
        if (value != null) {
            injectIdentityHashCode(value.intern(), identityHashCode);
            stringToConstant.put(value, id);
        }

        String className = get(constantData, ENUM_CLASS_TAG);
        if (className != null) {
            Enum<?> enumValue = getEnumValue(constantData);
            injectIdentityHashCode(enumValue, identityHashCode);
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

                Annotation[] annotations = getAnnotations(typeData);

                return new BaseLayerType(className, tid, modifiers, isInterface, isEnum, isInitialized, isLinked, sourceFileName, enclosingType, componentType, superClass, interfaces, objectType,
                                annotations);
            });
            BaseLayerType baseLayerType = baseLayerTypes.get(tid);
            AnalysisType type = universe.lookup(baseLayerType);
            guarantee(getBaseLayerTypeId(type) == tid, "The base layer type %s is not correctly matched to the id %d", type, tid);
        }
    }

    protected Annotation[] getAnnotations(@SuppressWarnings("unused") EconomicMap<String, Object> elementData) {
        return new Annotation[0];
    }

    private ResolvedJavaType getResolvedJavaType(Integer tid) {
        return tid == null ? null : getAnalysisType(tid).getWrapped();
    }

    public AnalysisType getAnalysisType(Integer tid) {
        if (!types.containsKey(tid)) {
            EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
            loadType(get(typesMap, typeIdToIdentifier.get(tid)));
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
        int id = get(typeData, ID_TAG);
        int hubIdentityHashCode = get(typeData, HUB_IDENTITY_HASH_CODE_TAG);
        typeToHubIdentityHashCode.put(id, hubIdentityHashCode);
        return id;
    }

    /**
     * Tries to look up the base layer type in the current VM. Some types cannot be looked up by
     * name (for example $$Lambda types), so this method can return null.
     */
    public static Class<?> lookupBaseLayerTypeInHostVM(String type) {
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

    private void loadMethod(EconomicMap<String, Object> methodData) {
        int mid = get(methodData, ID_TAG);

        if (imageLayerLoaderHelper.loadMethod(methodData, mid)) {
            return;
        }

        String name = get(methodData, NAME_TAG);
        String className = get(methodData, CLASS_NAME_TAG);
        if (className != null) {
            List<String> arguments = get(methodData, ARGUMENTS_TAG);

            Executable method = null;
            Class<?> clazz = lookupBaseLayerTypeInHostVM(className);
            if (clazz != null) {
                Class<?>[] argumentClasses = arguments.stream().map(ImageLayerLoader::lookupBaseLayerTypeInHostVM).toList().toArray(new Class<?>[0]);
                method = lookupMethodByReflection(name, clazz, argumentClasses);
            }

            if (method != null) {
                metaAccess.lookupJavaMethod(method);
                if (methods.containsKey(mid)) {
                    return;
                }
            }
        }

        int tid = get(methodData, TID_TAG);
        List<Integer> argumentIds = get(methodData, ARGUMENT_IDS_TAG);
        int returnTypeId = get(methodData, RETURN_TYPE_TAG);

        AnalysisType type = getAnalysisType(tid);
        List<AnalysisType> arguments = argumentIds.stream().map(this::getAnalysisType).toList();
        Class<?>[] argumentClasses = arguments.stream().map(AnalysisType::getJavaClass).toList().toArray(new Class<?>[0]);
        Executable method = lookupMethodByReflection(name, type.getJavaClass(), argumentClasses);

        if (method != null) {
            metaAccess.lookupJavaMethod(method);
            if (methods.containsKey(mid)) {
                return;
            }
        }

        AnalysisType returnType = getAnalysisType(returnTypeId);
        ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromList(arguments, returnType);

        if (name.equals(CONSTRUCTOR_NAME)) {
            type.findConstructor(signature);
        } else {
            type.findMethod(name, signature);
        }

        if (!methods.containsKey(mid)) {
            createBaseLayerMethod(methodData, mid, name);
        }
    }

    private static Executable lookupMethodByReflection(String name, Class<?> clazz, Class<?>[] argumentClasses) {
        Executable method;
        if (name.equals(CONSTRUCTOR_NAME)) {
            method = ReflectionUtil.lookupConstructor(true, clazz, argumentClasses);
        } else {
            method = ReflectionUtil.lookupMethod(true, clazz, name, argumentClasses);
        }
        return method;
    }

    private void createBaseLayerMethod(EconomicMap<String, Object> methodData, int mid, String name) {
        AnalysisType type = getAnalysisType(get(methodData, TID_TAG));
        List<Integer> parameterTypeIds = get(methodData, ARGUMENT_IDS_TAG);
        AnalysisType[] parameterTypes = parameterTypeIds.stream().map(this::getAnalysisType).toList().toArray(new AnalysisType[0]);
        AnalysisType returnType = getAnalysisType(get(methodData, RETURN_TYPE_TAG));
        ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromArray(parameterTypes, returnType);
        boolean canBeStaticallyBound = get(methodData, CAN_BE_STATICALLY_BOUND_TAG);
        boolean isConstructor = get(methodData, IS_CONSTRUCTOR_TAG);
        int modifiers = get(methodData, MODIFIERS_TAG);
        boolean isSynthetic = get(methodData, IS_SYNTHETIC_TAG);
        boolean isVarArgs = get(methodData, IS_VAR_ARGS_TAG);
        int codeSize = get(methodData, CODE_SIZE_TAG);
        Annotation[] annotations = getAnnotations(methodData);

        baseLayerMethods.computeIfAbsent(mid,
                        methodId -> new BaseLayerMethod(mid, type, name, isVarArgs, signature, canBeStaticallyBound, isConstructor, modifiers, isSynthetic, codeSize, annotations));
        BaseLayerMethod baseLayerMethod = baseLayerMethods.get(mid);

        universe.lookup(baseLayerMethod);
    }

    public AnalysisMethod getAnalysisMethod(int mid) {
        if (!methods.containsKey(mid)) {
            EconomicMap<String, Object> methodsMap = get(jsonMap, METHODS_TAG);
            loadMethod(get(methodsMap, methodIdToIdentifier.get(mid)));
        }

        AnalysisMethod analysisMethod = methods.get(mid);
        AnalysisError.guarantee(analysisMethod != null, "Method with id %d was not correctly loaded.", mid);
        return analysisMethod;
    }

    /**
     * Returns the method id of the given method in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine the method used
     * in RelocatableConstants.
     */
    public int lookupHostedMethodInBaseLayer(AnalysisMethod analysisMethod) {
        return getBaseLayerMethodId(analysisMethod);
    }

    private int getBaseLayerMethodId(AnalysisMethod analysisMethod) {
        if (analysisMethod.getWrapped() instanceof BaseLayerMethod baseLayerMethod) {
            return baseLayerMethod.getBaseLayerId();
        }
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        if (methodData == null || methods.containsKey(analysisMethod.getId())) {
            /* The method was not reachable in the base image */
            return -1;
        }
        return get(methodData, ID_TAG);
    }

    public void initializeBaseLayerMethod(AnalysisMethod analysisMethod) {
        int id = analysisMethod.getId();
        methods.putIfAbsent(id, analysisMethod);

        initializeBaseLayerMethod(analysisMethod, getMethodData(analysisMethod));
    }

    @SuppressWarnings("unused")
    protected void initializeBaseLayerMethod(AnalysisMethod analysisMethod, EconomicMap<String, Object> methodData) {
        /* No flags to load in the AnalysisMethod */
    }

    public boolean hasAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        return get(methodData, ANALYSIS_PARSED_GRAPH_TAG) != null;
    }

    public AnalysisParsedGraph getAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        String encodedAnalyzedGraph = get(methodData, ANALYSIS_PARSED_GRAPH_TAG);
        Boolean intrinsic = get(methodData, INTRINSIC_TAG);
        /*
         * Methods without a persisted graph are folded and static methods.
         *
         * GR-55278: graphs that contain a reference to a $$Lambda cannot be persisted as well.
         */
        if (encodedAnalyzedGraph != null) {
            EncodedGraph analyzedGraph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphDecoder(this, universe.getSnippetReflection()), encodedAnalyzedGraph);
            if (hasStrengthenedGraph(analysisMethod)) {
                loadAllAnalysisElements(get(methodData, STRENGTHENED_GRAPH_TAG));
            }
            return new AnalysisParsedGraph(analyzedGraph, intrinsic);
        }
        throw AnalysisError.shouldNotReachHere("The method " + analysisMethod + " does not have a graph from the base layer");
    }

    public boolean hasStrengthenedGraph(AnalysisMethod analysisMethod) {
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        return get(methodData, STRENGTHENED_GRAPH_TAG) != null;
    }

    public void setStrengthenedGraph(AnalysisMethod analysisMethod) {
        EconomicMap<String, Object> methodData = getMethodData(analysisMethod);
        String encodedAnalyzedGraph = get(methodData, STRENGTHENED_GRAPH_TAG);
        EncodedGraph analyzedGraph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphDecoder(this, universe.getSnippetReflection()), encodedAnalyzedGraph);
        processGraph(analyzedGraph);
        analysisMethod.setAnalyzedGraph(analyzedGraph);
    }

    @SuppressWarnings("unused")
    protected void processGraph(EncodedGraph encodedGraph) {

    }

    protected void loadAllAnalysisElements(String encoding) {
        for (String line : encoding.lines().toList()) {
            if (line.contains(PointsToAnalysisType.class.getName())) {
                getAnalysisType(getId(line));
            } else if (line.contains(PointsToAnalysisMethod.class.getName())) {
                getAnalysisMethod(getId(line));
            } else if (line.contains(PointsToAnalysisField.class.getName())) {
                getAnalysisField(getId(line));
            } else if (line.contains(ImageHeapInstance.class.getName()) || line.contains(ImageHeapObjectArray.class.getName()) || line.contains(ImageHeapPrimitiveArray.class.getName())) {
                getOrCreateConstant(getId(line));
            }
        }
    }

    protected static int getId(String line) {
        return Integer.parseInt(line.split(" = ")[1]);
    }

    private EconomicMap<String, Object> getMethodData(AnalysisMethod analysisMethod) {
        String name;
        int id = analysisMethod.getId();
        if (methodIdToIdentifier.containsKey(id)) {
            name = methodIdToIdentifier.get(id);
        } else {
            name = imageLayerSnapshotUtil.getMethodIdentifier(analysisMethod);
        }
        return getElementData(METHODS_TAG, name);
    }

    private void loadField(FieldIdentifier fieldIdentifier, EconomicMap<String, Object> fieldData) {
        AnalysisType declaringClass = getAnalysisType(Integer.parseInt(fieldIdentifier.tid));
        String className = get(fieldData, CLASS_NAME_TAG);

        Class<?> clazz = className != null ? lookupBaseLayerTypeInHostVM(className) : declaringClass.getJavaClass();
        if (clazz == null) {
            clazz = declaringClass.getJavaClass();
        }

        Field field = ReflectionUtil.lookupField(true, clazz, fieldIdentifier.name);
        if (field == null) {
            AnalysisType type = getAnalysisType(get(fieldData, FIELD_TYPE_TAG));
            BaseLayerField baseLayerField = new BaseLayerField(get(fieldData, ID_TAG), fieldIdentifier.name, declaringClass, type, get(fieldData, IS_INTERNAL_TAG), get(fieldData, MODIFIERS_TAG),
                            getAnnotations(fieldData));
            AnalysisField analysisField = universe.lookup(baseLayerField);
            analysisField.setPosition(get(fieldData, POSITION_TAG));
        } else {
            metaAccess.lookupJavaField(field);
        }
    }

    public AnalysisField getAnalysisField(int fid) {
        if (!fields.containsKey(fid)) {
            FieldIdentifier fieldIdentifier = fieldIdToIdentifier.get(fid);
            EconomicMap<String, Object> fieldsMap = get(jsonMap, FIELDS_TAG);
            loadField(fieldIdentifier, get(get(fieldsMap, fieldIdentifier.tid), fieldIdentifier.name));
        }

        AnalysisField analysisField = fields.get(fid);
        AnalysisError.guarantee(analysisField != null, "Field with id %d was not correctly loaded.", fid);
        return analysisField;
    }

    /**
     * Returns the field id of the given field in the base layer if it exists. This makes the link
     * between the base layer and the extension image as the id allows to set the flags of the
     * fields in the extension image.
     */
    public int lookupHostedFieldInBaseLayer(AnalysisField analysisField) {
        return getBaseLayerFieldId(analysisField);
    }

    private int getBaseLayerFieldId(AnalysisField analysisField) {
        if (analysisField.wrapped instanceof BaseLayerField baseLayerField) {
            return baseLayerField.getBaseLayerId();
        }
        EconomicMap<String, Object> fieldData = getFieldData(analysisField);
        if (fieldData == null) {
            /* The field was not reachable in the base image */
            return -1;
        }
        return get(fieldData, ID_TAG);
    }

    public void initializeBaseLayerField(AnalysisField analysisField) {
        if (fields.putIfAbsent(analysisField.getId(), analysisField) == null) {
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

    protected ImageHeapConstant getOrCreateConstant(EconomicMap<String, Object> constantsMap, int id, JavaConstant relinkedHostedObject) {
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
        int identityHashCode = get(baseLayerConstant, IDENTITY_HASH_CODE_TAG);
        if (relinkedHostedObject != null && !type.getJavaClass().equals(Class.class)) {
            /*
             * The hash codes of DynamicHubs need to be injected before they are used in a map,
             * which happens right after their creation. The injection of their hash codes can be
             * found in SVMHost#registerType.
             *
             * Also, for DynamicHub constants, the identity hash code persisted is the hash code of
             * the Class object, which we do not want to inject in the DynamicHub.
             */
            injectIdentityHashCode(hostedValuesProvider.asObject(Object.class, relinkedHostedObject), identityHashCode);
        }
        String constantType = get(baseLayerConstant, CONSTANT_TYPE_TAG);
        switch (constantType) {
            case INSTANCE_TAG -> {
                List<List<Object>> instanceData = get(baseLayerConstant, DATA_TAG);
                JavaConstant hostedObject = getHostedObject(baseLayerConstant, type);
                if (hostedObject != null && relinkedHostedObject != null) {
                    Object object = hostedValuesProvider.asObject(Object.class, hostedObject);
                    Object relinkedObject = hostedValuesProvider.asObject(Object.class, relinkedHostedObject);
                    AnalysisError.guarantee(object == relinkedObject, "Found discrepancy between hosted value %s and relinked value %s.", object, relinkedObject);
                }
                ImageHeapInstance imageHeapInstance = new ImageHeapInstance(type, hostedObject == null ? relinkedHostedObject : hostedObject, identityHashCode);
                if (instanceData != null) {
                    Object[] fieldValues = getReferencedValues(constantsMap, imageHeapInstance, instanceData, imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess));
                    imageHeapInstance.setFieldValues(fieldValues);
                }
                addBaseLayerObject(id, imageHeapInstance, objectOffset);
            }
            case ARRAY_TAG -> {
                List<List<Object>> arrayData = get(baseLayerConstant, DATA_TAG);
                ImageHeapObjectArray imageHeapObjectArray = new ImageHeapObjectArray(type, null, arrayData.size(), identityHashCode);
                Object[] elementsValues = getReferencedValues(constantsMap, imageHeapObjectArray, arrayData, Set.of());
                imageHeapObjectArray.setElementValues(elementsValues);
                addBaseLayerObject(id, imageHeapObjectArray, objectOffset);
            }
            case PRIMITIVE_ARRAY_TAG -> {
                List<Object> primitiveData = get(baseLayerConstant, DATA_TAG);
                Object array = getArray(type.getComponentType().getJavaKind(), primitiveData);
                ImageHeapPrimitiveArray imageHeapPrimitiveArray = new ImageHeapPrimitiveArray(type, null, array, primitiveData.size(), identityHashCode);
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
            Enum<?> enumValue = getEnumValue(baseLayerConstant);
            return getHostedObject(enumValue);
        }
        return null;
    }

    protected JavaConstant getHostedObject(Object object) {
        return hostedValuesProvider.forObject(object);
    }

    @SuppressWarnings("unused")
    protected void injectIdentityHashCode(Object object, Integer identityHashCode) {
        /* The hash code can only be injected in the SVM context. */
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
            if (delegateProcessing(constantKind, constantValue, constantData, values, position)) {
                continue;
            }
            if (constantKind.equals(OBJECT_TAG)) {
                int constantId = (int) constantValue;
                if (constantId >= 0) {
                    boolean relink = positionsToRelink.contains(position);
                    int finalPosition = position;
                    values[position] = new AnalysisFuture<>(() -> {
                        ensureHubInitialized(parentConstant);

                        JavaConstant hostedObject = relink ? getValueHostedObject(parentConstant, finalPosition) : null;
                        ImageHeapConstant constant = getOrCreateConstant(constantsMap, constantId, hostedObject);
                        values[finalPosition] = constant;

                        ensureHubInitialized(constant);

                        if (hostedObject != null) {
                            linkBaseLayerValue(constant, parentConstant, finalPosition);
                        }

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
    protected boolean delegateProcessing(String constantType, Object constantValue, List<Object> constantData, Object[] values, int i) {
        return false;
    }

    private JavaConstant getValueHostedObject(ImageHeapConstant parentConstant, int i) {
        if (parentConstant instanceof ImageHeapObjectArray array) {
            return getElementValueHostedObject(array, i);
        } else if (parentConstant instanceof ImageHeapInstance instance) {
            AnalysisField field = getFieldFromIndex(instance, i);
            return getFieldValueHostedObject(instance, field);
        } else {
            throw AnalysisError.shouldNotReachHere("unexpected constant: " + parentConstant);
        }
    }

    private static AnalysisField getFieldFromIndex(ImageHeapInstance instance, int i) {
        return (AnalysisField) instance.getType().getInstanceFields(true)[i];
    }

    private JavaConstant getElementValueHostedObject(ImageHeapObjectArray array, int idx) {
        JavaConstant hostedArray = array.getHostedObject();
        JavaConstant rawElementValue = null;
        if (hostedArray != null) {
            rawElementValue = hostedValuesProvider.readArrayElement(hostedArray, idx);
        }
        return rawElementValue;
    }

    private JavaConstant getFieldValueHostedObject(ImageHeapInstance instance, AnalysisField field) {
        ValueSupplier<JavaConstant> rawFieldValue;
        try {
            JavaConstant hostedInstance = instance.getHostedObject();
            AnalysisError.guarantee(hostedInstance != null);
            rawFieldValue = universe.getHeapScanner().readHostedFieldValue(field, hostedInstance);
        } catch (InternalError | TypeNotPresentException | LinkageError e) {
            /* Ignore missing type errors. */
            return null;
        }
        return rawFieldValue.get();
    }

    public void linkBaseLayerValue(ImageHeapConstant constant, ImageHeapConstant parentConstant, int i) {
        if (parentConstant instanceof ImageHeapInstance imageHeapInstance) {
            universe.getHeapScanner().linkBaseLayerValue(constant, getFieldFromIndex(imageHeapInstance, i));
        } else if (parentConstant instanceof ImageHeapObjectArray) {
            universe.getHeapScanner().linkBaseLayerValue(constant, i);
        } else {
            throw AnalysisError.shouldNotReachHere("unexpected constant: " + constant);
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

    @SuppressWarnings("unchecked")
    protected static Enum<?> getEnumValue(EconomicMap<String, Object> enumData) {
        String className = get(enumData, ENUM_CLASS_TAG);
        Class<?> enumClass = ReflectionUtil.lookupClass(false, className);
        String name = get(enumData, ENUM_NAME_TAG);
        /* asSubclass produces an "unchecked" warning */
        return Enum.valueOf(enumClass.asSubclass(Enum.class), name);
    }

    public static <T> T get(EconomicMap<String, Object> innerMap, String elementIdentifier) {
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

    public ImageHeapConstant getOrCreateConstant(int id) {
        return getOrCreateConstant(get(jsonMap, CONSTANTS_TAG), id, null);
    }

    public AnalysisMetaAccess getMetaAccess() {
        return metaAccess;
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

    public boolean hasDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.containsKey(tid);
    }

    public int getDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.get(tid);
    }
}
