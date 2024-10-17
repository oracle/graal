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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.meta.JavaKind;

public class ImageLayerSnapshotUtil {
    public static final String FILE_NAME_PREFIX = "layer-snapshot-";
    public static final String GRAPHS_FILE_NAME_PREFIX = "layer-snapshot-graphs-";
    public static final String FILE_EXTENSION = ".json";

    public static final String CONSTRUCTOR_NAME = "<init>";

    public static final String PERSISTED = "persisted";

    public static final int NULL_POINTER_CONSTANT = -1;
    public static final int NOT_MATERIALIZED_CONSTANT = -2;
    public static final String OBJECT_TAG = "A";
    public static final String METHOD_POINTER_TAG = "M";
    public static final String C_ENTRY_POINT_LITERAL_CODE_POINTER = "CONSTANT";
    public static final String TYPES_TAG = "types";
    public static final String METHODS_TAG = "methods";
    public static final String FIELDS_TAG = "fields";
    public static final String IS_INTERNAL_TAG = "is internal";
    public static final String FIELD_TYPE_TAG = "field type";
    public static final String CLASS_JAVA_NAME_TAG = "class java name";
    public static final String CAN_BE_STATICALLY_BOUND_TAG = "can be statically bound";
    public static final String IS_CONSTRUCTOR_TAG = "is constructor";
    public static final String IS_SYNTHETIC_TAG = "is synthetic";
    public static final String CODE_TAG = "code";
    public static final String CODE_SIZE_TAG = "code size";
    public static final String METHOD_HANDLE_INTRINSIC_TAG = "method handle intrinsic";
    public static final String IS_VIRTUAL_ROOT_METHOD = "is virtual root method";
    public static final String IS_DIRECT_ROOT_METHOD = "is direct root method";
    public static final String IS_INVOKED = "is invoked";
    public static final String IS_IMPLEMENTATION_INVOKED = "is implementation invoked";
    public static final String IS_INTRINSIC_METHOD = "is intrinsic method";
    public static final String ANNOTATIONS_TAG = "annotations";
    public static final String IS_INSTANTIATED = "is instantiated";
    public static final String IS_UNSAFE_ALLOCATED = "is unsafe allocated";
    public static final String IS_REACHABLE = "is reachable";
    public static final String CLASS_NAME_TAG = "class name";
    public static final String MODIFIERS_TAG = "modifiers";
    public static final String POSITION_TAG = "position";
    public static final String IS_INTERFACE_TAG = "is interface";
    public static final String IS_ENUM_TAG = "is enum";
    public static final String IS_INITIALIZED_TAG = "is initialized";
    public static final String IS_LINKED_TAG = "is linked";
    public static final String SOURCE_FILE_NAME_TAG = "source file name";
    public static final String ENCLOSING_TYPE_TAG = "enclosing type";
    public static final String COMPONENT_TYPE_TAG = "component type";
    public static final String SUPER_CLASS_TAG = "super class";
    public static final String INTERFACES_TAG = "interfaces";
    public static final String WRAPPED_TYPE_TAG = "wrapped type";
    public static final String GENERATED_SERIALIZATION_TAG = "generated serialization";
    public static final String LAMBDA_TYPE_TAG = "lambda type";
    public static final String HOLDER_CLASS_TAG = "holder class";
    public static final String RAW_DECLARING_CLASS_TAG = "raw declaring class";
    public static final String RAW_TARGET_CONSTRUCTOR_CLASS_TAG = "raw target constructor class";
    public static final String CONSTANTS_TAG = "constants";
    public static final String CONSTANTS_TO_RELINK_TAG = "constants to relink";
    public static final String TID_TAG = "tid";
    public static final String NAME_TAG = "name";
    public static final String ARGUMENTS_TAG = "arguments";
    public static final String ARGUMENT_IDS_TAG = "argument ids";
    public static final String RETURN_TYPE_TAG = "return type";
    public static final String IS_VAR_ARGS_TAG = "is varArg";
    public static final String WRAPPED_METHOD_TAG = "wrapped method";
    public static final String METHOD_TYPE_PARAMETERS_TAG = "method type parameters";
    public static final String METHOD_TYPE_RETURN_TAG = "method type return";
    public static final String FACTORY_TAG = "factory";
    public static final String OUTLINED_SB_TAG = "outlinedSB";
    public static final String TARGET_CONSTRUCTOR_TAG = "target constructor";
    public static final String THROW_ALLOCATED_OBJECT_TAG = "throw allocated object";
    public static final String IDENTITY_HASH_CODE_TAG = "identityHashCode";
    public static final String HUB_IDENTITY_HASH_CODE_TAG = "hub identityHashCode";
    public static final String IS_INITIALIZED_AT_BUILD_TIME_TAG = "is initialized at build time";
    public static final String IS_NO_INITIALIZER_NO_TRACKING_TAG = "in no initializer no tracking";
    public static final String IS_INITIALIZED_NO_TRACKING_TAG = "is initialized no tracking";
    public static final String IS_FAILED_NO_TRACKING_TAG = "is failed no tracking";
    public static final String INFO_IS_INITIALIZED_TAG = "info is initialized";
    public static final String INFO_IS_IN_ERROR_STATE_TAG = "info is in error state";
    public static final String INFO_IS_LINKED_TAG = "info is linked";
    public static final String INFO_HAS_INITIALIZER_TAG = "info has initializer";
    public static final String INFO_IS_BUILD_TIME_INITIALIZED_TAG = "info is build time initialized";
    public static final String INFO_IS_TRACKED_TAG = "info is tracked";
    public static final String INFO_CLASS_INITIALIZER_TAG = "info class initializer";
    public static final String ID_TAG = "id";
    public static final String ANALYSIS_PARSED_GRAPH_TAG = "analysis parsed graph";
    public static final String STRENGTHENED_GRAPH_TAG = "strengthened graph";
    public static final String INTRINSIC_TAG = "intrinsic";
    public static final String CONSTANT_TYPE_TAG = "constant type";
    public static final String DATA_TAG = "data";
    public static final String INSTANCE_TAG = "instance";
    public static final String ARRAY_TAG = "array";
    public static final String PRIMITIVE_ARRAY_TAG = "primitive array";
    public static final String FIELD_ACCESSED_TAG = "accessed";
    public static final String FIELD_READ_TAG = "read";
    public static final String FIELD_WRITTEN_TAG = "written";
    public static final String FIELD_FOLDED_TAG = "folded";
    public static final String LOCATION_TAG = "location";
    public static final String NEXT_TYPE_ID_TAG = "next type id";
    public static final String NEXT_METHOD_ID_TAG = "next method id";
    public static final String NEXT_FIELD_ID_TAG = "next field id";
    public static final String IMAGE_HEAP_SIZE_TAG = "image heap size";
    public static final String VALUE_TAG = "value";
    public static final String ENUM_CLASS_TAG = "enum class";
    public static final String ENUM_NAME_TAG = "enum name";
    public static final String CLASS_ID_TAG = "class id";
    public static final String SIMULATED_TAG = "simulated";
    public static final String OBJECT_OFFSET_TAG = "object offset";
    public static final String STATIC_PRIMITIVE_FIELDS_TAG = "static primitive fields";
    public static final String STATIC_OBJECT_FIELDS_TAG = "static object fields";
    public static final String IMAGE_SINGLETON_KEYS = "image singleton keys";
    public static final String IMAGE_SINGLETON_OBJECTS = "image singleton objects";

    protected final List<Field> externalValues;

    @SuppressWarnings("this-escape")
    public ImageLayerSnapshotUtil() {
        externalValues = new ArrayList<>();

        addExternalValues(LocationIdentity.class);
        addExternalValues(NamedLocationIdentity.class);
    }

    protected void addExternalValues(Class<?> clazz) {
        Arrays.stream(clazz.getDeclaredFields()).filter(this::shouldAddExternalValue).forEach(this::addExternalValue);
    }

    private void addExternalValue(Field f) {
        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ImageLayerSnapshotUtil.class, f.getDeclaringClass());
        f.setAccessible(true);
        externalValues.add(f);
    }

    private boolean shouldAddExternalValue(Field f) {
        Class<?> type = f.getType();
        return Modifier.isStatic(f.getModifiers()) && shouldAddExternalValue(type);
    }

    protected boolean shouldAddExternalValue(Class<?> type) {
        return LocationIdentity.class.isAssignableFrom(type);
    }

    public static String snapshotFileName(String imageName) {
        return FILE_NAME_PREFIX + imageName + FILE_EXTENSION;
    }

    public static String snapshotGraphsFileName(String imageName) {
        return GRAPHS_FILE_NAME_PREFIX + imageName + FILE_EXTENSION;
    }

    public String getTypeIdentifier(AnalysisType type) {
        String javaName = type.toJavaName(true);
        return addModuleName(javaName, type.getJavaClass().getModule().getName());
    }

    public String getMethodIdentifier(AnalysisMethod method) {
        AnalysisType declaringClass = method.getDeclaringClass();
        Executable originalMethod = OriginalMethodProvider.getJavaMethod(method);
        String moduleName = declaringClass.getJavaClass().getModule().getName();
        if (originalMethod != null) {
            return addModuleName(originalMethod.toString(), moduleName);
        }
        return addModuleName(getQualifiedName(method), moduleName);
    }

    protected static String addModuleName(String elementName, String moduleName) {
        return moduleName + ":" + elementName;
    }

    protected static String getQualifiedName(AnalysisMethod method) {
        return method.getSignature().getReturnType().toJavaName(true) + " " + method.getQualifiedName();
    }

    @SuppressWarnings("unused")
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        return Set.of();
    }

    public GraphEncoder getGraphEncoder(ImageLayerWriter imageLayerWriter) {
        return new GraphEncoder(externalValues, imageLayerWriter);
    }

    @SuppressWarnings("unused")
    public GraphDecoder getGraphDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new GraphDecoder(EncodedGraph.class.getClassLoader(), imageLayerLoader, analysisMethod);
    }

    public static class GraphEncoder extends ObjectCopier.Encoder {
        @SuppressWarnings("this-escape")
        public GraphEncoder(List<Field> externalValues, ImageLayerWriter imageLayerWriter) {
            super(externalValues);
            addBuiltin(new NodeClassBuiltIn());
            addBuiltin(new ImageHeapConstantBuiltIn(imageLayerWriter, null));
            addBuiltin(new AnalysisTypeBuiltIn(imageLayerWriter, null));
            addBuiltin(new AnalysisMethodBuiltIn(imageLayerWriter, null, null));
            addBuiltin(new AnalysisFieldBuiltIn(imageLayerWriter, null));
            addBuiltin(new FieldLocationIdentityBuiltIn(imageLayerWriter, null));
            addBuiltin(new NamedLocationIdentityArrayBuiltIn());
        }
    }

    public static class GraphDecoder extends ObjectCopier.Decoder {
        private final ImageLayerLoader imageLayerLoader;

        @SuppressWarnings("this-escape")
        public GraphDecoder(ClassLoader classLoader, ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod) {
            super(classLoader);
            this.imageLayerLoader = imageLayerLoader;
            addBuiltin(new NodeClassBuiltIn());
            addBuiltin(new ImageHeapConstantBuiltIn(null, imageLayerLoader));
            addBuiltin(new AnalysisTypeBuiltIn(null, imageLayerLoader));
            addBuiltin(new AnalysisMethodBuiltIn(null, imageLayerLoader, analysisMethod));
            addBuiltin(new AnalysisFieldBuiltIn(null, imageLayerLoader));
            addBuiltin(new FieldLocationIdentityBuiltIn(null, imageLayerLoader));
            addBuiltin(new NamedLocationIdentityArrayBuiltIn());
        }

        @Override
        public Class<?> loadClass(String className) {
            return imageLayerLoader.lookupClass(false, className);
        }
    }

    public static class NodeClassBuiltIn extends ObjectCopier.Builtin {
        protected NodeClassBuiltIn() {
            super(NodeClass.class);
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            return ((NodeClass<?>) obj).getClazz().getName();
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            Class<?> holder = ReflectionUtil.lookupClass(false, encoded);
            return ReflectionUtil.readField(holder, "TYPE", null);
        }
    }

    public static class ImageHeapConstantBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;

        protected ImageHeapConstantBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader) {
            super(ImageHeapConstant.class, ImageHeapInstance.class, ImageHeapObjectArray.class, ImageHeapPrimitiveArray.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            ImageHeapConstant imageHeapConstant = (ImageHeapConstant) obj;
            imageLayerWriter.elementsToPersist.add(new AnalysisFuture<>(() -> imageLayerWriter.persistConstant(imageHeapConstant)));
            return String.valueOf(imageHeapConstant.getConstantData().id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return imageLayerLoader.getOrCreateConstant(Integer.parseInt(encoded));
        }
    }

    public static class AnalysisTypeBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;

        protected AnalysisTypeBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader) {
            super(AnalysisType.class, PointsToAnalysisType.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            AnalysisType type = (AnalysisType) obj;
            imageLayerWriter.persistType(type);
            return String.valueOf(type.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return imageLayerLoader.getAnalysisType(Integer.parseInt(encoded));
        }
    }

    public static class AnalysisMethodBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;
        private final AnalysisMethod analysisMethod;

        protected AnalysisMethodBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod) {
            super(AnalysisMethod.class, PointsToAnalysisMethod.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
            this.analysisMethod = analysisMethod;
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            AnalysisMethod method = (AnalysisMethod) obj;
            AnalysisType declaringClass = method.getDeclaringClass();
            imageLayerWriter.elementsToPersist.add(new AnalysisFuture<>(() -> {
                imageLayerWriter.persistAnalysisParsedGraph(method);
                imageLayerWriter.persistMethod(method);
            }));
            for (AnalysisType parameter : method.toParameterList()) {
                imageLayerWriter.persistType(parameter);
            }
            imageLayerWriter.persistType(declaringClass);
            return String.valueOf(method.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            int id = Integer.parseInt(encoded);
            if (id == analysisMethod.getId()) {
                return analysisMethod;
            }
            return imageLayerLoader.getAnalysisMethod(id);
        }
    }

    public static class AnalysisFieldBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;

        protected AnalysisFieldBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader) {
            super(AnalysisField.class, PointsToAnalysisField.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            AnalysisField field = (AnalysisField) obj;
            return encodeField(field, imageLayerWriter);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return decodeField(imageLayerLoader, encoded);
        }
    }

    public static class FieldLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;

        protected FieldLocationIdentityBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader) {
            super(FieldLocationIdentity.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) obj;
            AnalysisField field = (AnalysisField) fieldLocationIdentity.getField();
            return encodeField(field, imageLayerWriter);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return new FieldLocationIdentity(decodeField(imageLayerLoader, encoded));
        }
    }

    private static String encodeField(AnalysisField field, ImageLayerWriter imageLayerWriter) {
        String declaringClassId = String.valueOf(field.getDeclaringClass().getId());
        if (!imageLayerWriter.fieldsMap.containsKey(declaringClassId) || !imageLayerWriter.fieldsMap.get(declaringClassId).containsKey(field.getName())) {
            imageLayerWriter.persistField(field);
        }
        return String.valueOf(field.getId());
    }

    private static AnalysisField decodeField(ImageLayerLoader imageLayerLoader, String encoded) {
        return imageLayerLoader.getAnalysisField(Integer.parseInt(encoded));
    }

    public static class NamedLocationIdentityArrayBuiltIn extends ObjectCopier.Builtin {
        protected NamedLocationIdentityArrayBuiltIn() {
            super(NamedLocationIdentity.class);
        }

        @Override
        public String encode(ObjectCopier.Encoder encoder, Object obj) {
            NamedLocationIdentity namedLocationIdentity = (NamedLocationIdentity) obj;
            AnalysisError.guarantee(NamedLocationIdentity.isArrayLocation(namedLocationIdentity),
                            "The named location identity %s should be encoded using an external value.", namedLocationIdentity);
            String name = namedLocationIdentity.toString().split("Array: ")[1];
            /* Capitalizing the first letter gets the name of the Enum value */
            return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return NamedLocationIdentity.getArrayLocation(JavaKind.valueOf(encoded));
        }
    }
}
