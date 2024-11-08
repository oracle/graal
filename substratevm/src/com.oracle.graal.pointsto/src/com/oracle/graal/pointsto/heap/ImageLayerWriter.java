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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ANNOTATIONS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARGUMENTS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARGUMENT_IDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CAN_BE_STATICALLY_BOUND_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_JAVA_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CODE_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CODE_TAG;
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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FIELD_WRITTEN_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_HEAP_SIZE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INSTANCE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTERFACES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTRINSIC_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_DIRECT_ROOT_METHOD;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_ENUM_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_IMPLEMENTATION_INVOKED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INSTANTIATED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERFACE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTERNAL_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INTRINSIC_METHOD;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INVOKED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_LINKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_REACHABLE;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_SYNTHETIC_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_UNSAFE_ALLOCATED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_VAR_ARGS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_VIRTUAL_ROOT_METHOD;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHODS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_HANDLE_INTRINSIC_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.MODIFIERS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_FIELD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_METHOD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NEXT_TYPE_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NOT_MATERIALIZED_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NULL_POINTER_CONSTANT;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PARENT_CONSTANT_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PARENT_CONSTANT_INDEX_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.POSITION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PRIMITIVE_ARRAY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RETURN_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RELOCATED_CONSTANT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SIMULATED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SOURCE_FILE_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STRENGTHENED_GRAPH_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.SUPER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TYPES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.UNDEFINED_CONSTANT_ID;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.UNDEFINED_FIELD_INDEX;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.VALUE_TAG;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.FileDumpingUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public class ImageLayerWriter {
    protected ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageLayerWriterHelper imageLayerWriterHelper;
    private ImageHeap imageHeap;
    protected AnalysisUniverse aUniverse;
    private IdentityHashMap<String, String> internedStringsIdentityMap;

    protected final EconomicMap<String, Object> jsonMap;
    protected final List<Integer> constantsToRelink;
    private final Set<Integer> persistedTypeIds;
    private final Set<Integer> persistedMethodIds;
    protected final Map<String, EconomicMap<String, Object>> typesMap;
    protected final Map<String, EconomicMap<String, Object>> methodsMap;
    protected final Map<String, Map<String, Object>> fieldsMap;
    private final Map<String, EconomicMap<String, Object>> constantsMap;
    private FileInfo fileInfo;
    private GraphsOutput graphsOutput;
    private final boolean useSharedLayerGraphs;
    private final boolean useSharedLayerStrengthenedGraphs;

    protected final Set<AnalysisFuture<Void>> elementsToPersist = ConcurrentHashMap.newKeySet();

    private record FileInfo(Path layerFilePath, String fileName, String suffix) {
    }

    private static class GraphsOutput {
        private final Path path;
        private final Path tempPath;
        private final FileChannel tempChannel;

        private final AtomicLong currentOffset = new AtomicLong(0);

        GraphsOutput(Path path, String fileName, String suffix) {
            this.path = path;
            this.tempPath = FileDumpingUtil.createTempFile(path.getParent(), fileName, suffix);
            try {
                this.tempChannel = FileChannel.open(this.tempPath, EnumSet.of(StandardOpenOption.WRITE));
            } catch (IOException e) {
                throw GraalError.shouldNotReachHere(e, "Error opening temporary graphs file.");
            }
        }

        String add(byte[] encodedGraph) {
            long offset = currentOffset.getAndAdd(encodedGraph.length);
            try {
                tempChannel.write(ByteBuffer.wrap(encodedGraph), offset);
            } catch (Exception e) {
                throw GraalError.shouldNotReachHere(e, "Error during graphs file dumping.");
            }
            return new StringBuilder("@").append(offset).append("[").append(encodedGraph.length).append("]").toString();
        }

        void finish() {
            try {
                tempChannel.close();
                FileDumpingUtil.moveTryAtomically(tempPath, path);
            } catch (Exception e) {
                throw GraalError.shouldNotReachHere(e, "Error during graphs file dumping.");
            }
        }
    }

    public ImageLayerWriter() {
        this(true);
    }

    @SuppressWarnings({"this-escape", "unused"})
    public ImageLayerWriter(boolean useSharedLayerGraphs) {
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        this.useSharedLayerStrengthenedGraphs = false;
        this.jsonMap = EconomicMap.create();
        this.constantsToRelink = new ArrayList<>();
        this.persistedTypeIds = ConcurrentHashMap.newKeySet();
        this.persistedMethodIds = ConcurrentHashMap.newKeySet();
        this.typesMap = new ConcurrentHashMap<>();
        this.methodsMap = new ConcurrentHashMap<>();
        this.fieldsMap = new ConcurrentHashMap<>();
        this.constantsMap = new ConcurrentHashMap<>();
    }

    public void setImageLayerSnapshotUtil(ImageLayerSnapshotUtil imageLayerSnapshotUtil) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
    }

    public void setInternedStringsIdentityMap(IdentityHashMap<String, String> map) {
        this.internedStringsIdentityMap = map;
    }

    public void setImageHeap(ImageHeap heap) {
        this.imageHeap = heap;
    }

    public void setImageLayerWriterHelper(ImageLayerWriterHelper imageLayerWriterHelper) {
        this.imageLayerWriterHelper = imageLayerWriterHelper;
    }

    public void setSnapshotFileInfo(Path layerSnapshotPath, String fileName, String suffix) {
        fileInfo = new FileInfo(layerSnapshotPath, fileName, suffix);
    }

    public void openGraphsOutput(Path layerGraphsPath, String fileName, String suffix) {
        AnalysisError.guarantee(graphsOutput == null, "Graphs file has already been opened");
        graphsOutput = new GraphsOutput(layerGraphsPath, fileName, suffix);
    }

    public void setAnalysisUniverse(AnalysisUniverse aUniverse) {
        this.aUniverse = aUniverse;
    }

    public void dumpFiles() {
        graphsOutput.finish();

        FileDumpingUtil.dumpFile(fileInfo.layerFilePath, fileInfo.fileName, fileInfo.suffix, outputStream -> {
            try (JsonWriter jw = new JsonWriter(new PrintWriter(outputStream))) {
                jw.print(jsonMap);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void initializeExternalValues() {
        imageLayerSnapshotUtil.initializeExternalValues();
    }

    public void persistImageHeapSize(long imageHeapSize) {
        jsonMap.put(IMAGE_HEAP_SIZE_TAG, String.valueOf(imageHeapSize));
    }

    public void persistAnalysisInfo() {
        persistHook();

        jsonMap.put(NEXT_TYPE_ID_TAG, aUniverse.getNextTypeId());
        jsonMap.put(NEXT_METHOD_ID_TAG, aUniverse.getNextMethodId());
        jsonMap.put(NEXT_FIELD_ID_TAG, aUniverse.getNextFieldId());

        /*
         * $$TypeSwitch classes should not be instantiated as they are only used as a container for
         * a static method, so no constant of those types should be created. This filter can be
         * removed after a mechanism for determining which types have to be persisted is added, or
         * if a stable name is implemented for them.
         */
        for (AnalysisType type : aUniverse.getTypes().stream().filter(AnalysisType::isTrackedAcrossLayers).toList()) {
            checkTypeStability(type);
            persistType(type);
        }
        jsonMap.put(TYPES_TAG, typesMap);

        for (AnalysisMethod method : aUniverse.getMethods().stream().filter(AnalysisMethod::isTrackedAcrossLayers).toList()) {
            persistMethod(method);
        }
        jsonMap.put(METHODS_TAG, methodsMap);

        for (AnalysisField field : aUniverse.getFields().stream().filter(AnalysisField::isTrackedAcrossLayers).toList()) {
            persistField(field);
        }
        jsonMap.put(FIELDS_TAG, fieldsMap);

        for (Map.Entry<AnalysisType, Set<ImageHeapConstant>> entry : imageHeap.getReachableObjects().entrySet()) {
            for (ImageHeapConstant imageHeapConstant : entry.getValue()) {
                persistConstant(UNDEFINED_CONSTANT_ID, UNDEFINED_FIELD_INDEX, imageHeapConstant);
            }
        }
        for (AnalysisFuture<Void> task : elementsToPersist) {
            task.ensureDone();
        }
        jsonMap.put(CONSTANTS_TAG, constantsMap);
        jsonMap.put(CONSTANTS_TO_RELINK_TAG, constantsToRelink);
    }

    /**
     * A hook used to persist more general information about the base layer not accessible in
     * pointsto.
     */
    @SuppressWarnings("unused")
    protected void persistHook() {

    }

    public boolean isTypePersisted(AnalysisType type) {
        return persistedTypeIds.contains(type.getId());
    }

    protected void persistType(AnalysisType type) {
        if (!persistedTypeIds.add(type.getId())) {
            return;
        }
        String typeIdentifier = imageLayerSnapshotUtil.getTypeIdentifier(type);
        AnalysisType superclass = type.getSuperclass();
        if (superclass != null) {
            /*
             * Some persisted types are not reachable. In this case, the super class has to be
             * persisted manually as well.
             */
            persistType(superclass);
        }
        EconomicMap<String, Object> typeMap = EconomicMap.create();

        persistType(type, typeMap);

        if (typesMap.containsKey(typeIdentifier)) {
            throw GraalError.shouldNotReachHere("The type identifier should be unique, but " + typeIdentifier + " got added twice.");
        }
        typesMap.put(typeIdentifier, typeMap);
    }

    protected void persistType(AnalysisType type, EconomicMap<String, Object> typeMap) {
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
        typeMap.put(ANNOTATIONS_TAG, Arrays.stream(AnnotationAccess.getAnnotationTypes(type)).map(Class::getName).toList());

        typeMap.put(IS_INSTANTIATED, type.isInstantiated());
        typeMap.put(IS_UNSAFE_ALLOCATED, type.isUnsafeAllocated());
        typeMap.put(IS_REACHABLE, type.isReachable());

        imageLayerWriterHelper.persistType(type, typeMap);
    }

    /**
     * Some types can have an unstable name between two different image builds. To avoid producing
     * wrong results, a warning should be printed if such types exist in the resulting image.
     */
    @SuppressWarnings("unused")
    public void checkTypeStability(AnalysisType type) {
        /* Do not need to check anything here */
    }

    public void persistMethod(AnalysisMethod method) {
        if (!persistedMethodIds.add(method.getId())) {
            return;
        }
        EconomicMap<String, Object> methodMap = getMethodMap(method);
        persistMethod(method, methodMap);
    }

    protected void persistMethod(AnalysisMethod method, EconomicMap<String, Object> methodMap) {
        Executable executable = method.getJavaMethod();

        if (methodMap.containsKey(ID_TAG)) {
            throw GraalError.shouldNotReachHere("The method identifier should be unique, but " + imageLayerSnapshotUtil.getMethodIdentifier(method) + " got added twice.");
        }
        if (executable != null) {
            methodMap.put(ARGUMENTS_TAG, Arrays.stream(executable.getParameterTypes()).map(Class::getName).toList());
            methodMap.put(CLASS_NAME_TAG, executable.getDeclaringClass().getName());
        }
        methodMap.put(TID_TAG, method.getDeclaringClass().getId());
        methodMap.put(ARGUMENT_IDS_TAG, method.getSignature().toParameterList(null).stream().map(AnalysisType::getId).toList());
        methodMap.put(ID_TAG, method.getId());
        methodMap.put(NAME_TAG, method.getName());
        methodMap.put(RETURN_TYPE_TAG, method.getSignature().getReturnType().getId());
        methodMap.put(IS_VAR_ARGS_TAG, method.isVarArgs());
        methodMap.put(CAN_BE_STATICALLY_BOUND_TAG, method.canBeStaticallyBound());
        methodMap.put(MODIFIERS_TAG, method.getModifiers());
        methodMap.put(IS_CONSTRUCTOR_TAG, method.isConstructor());
        methodMap.put(IS_SYNTHETIC_TAG, method.isSynthetic());
        byte[] code = method.getCode();
        if (code != null) {
            methodMap.put(CODE_TAG, getString(JavaKind.Byte, method.getCode()));
        }
        methodMap.put(CODE_SIZE_TAG, method.getCodeSize());
        IntrinsicMethod intrinsicMethod = aUniverse.getBigbang().getConstantReflectionProvider().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
        if (intrinsicMethod != null) {
            methodMap.put(METHOD_HANDLE_INTRINSIC_TAG, intrinsicMethod.name());
        }
        methodMap.put(ANNOTATIONS_TAG, Arrays.stream(AnnotationAccess.getAnnotationTypes(method)).map(Class::getName).toList());

        methodMap.put(IS_VIRTUAL_ROOT_METHOD, method.isVirtualRootMethod());
        methodMap.put(IS_DIRECT_ROOT_METHOD, method.isDirectRootMethod());
        methodMap.put(IS_INVOKED, method.isSimplyInvoked());
        methodMap.put(IS_IMPLEMENTATION_INVOKED, method.isSimplyImplementationInvoked());
        methodMap.put(IS_INTRINSIC_METHOD, method.isIntrinsicMethod());

        imageLayerWriterHelper.persistMethod(method, methodMap);
    }

    public boolean isMethodPersisted(AnalysisMethod method) {
        String name = imageLayerSnapshotUtil.getMethodIdentifier(method);
        return methodsMap.containsKey(name);
    }

    public void persistMethodGraphs() {
        for (AnalysisMethod method : aUniverse.getMethods()) {
            if (method.isTrackedAcrossLayers()) {
                persistAnalysisParsedGraph(method);
            }
        }
    }

    public void persistAnalysisParsedGraph(AnalysisMethod method) {
        EconomicMap<String, Object> methodMap = getMethodMap(method);

        Object analyzedGraph = method.getGraph();
        if (analyzedGraph instanceof AnalysisParsedGraph analysisParsedGraph) {
            if (!methodMap.containsKey(INTRINSIC_TAG)) {
                if (!persistGraph(method, analysisParsedGraph.getEncodedGraph(), methodMap, ANALYSIS_PARSED_GRAPH_TAG)) {
                    return;
                }
                methodMap.put(INTRINSIC_TAG, analysisParsedGraph.isIntrinsic());
            }
        }
    }

    public void persistMethodStrengthenedGraph(AnalysisMethod method) {
        if (!useSharedLayerStrengthenedGraphs) {
            return;
        }

        EconomicMap<String, Object> methodMap = getMethodMap(method);

        if (!methodMap.containsKey(STRENGTHENED_GRAPH_TAG)) {
            EncodedGraph analyzedGraph = method.getAnalyzedGraph();
            persistGraph(method, analyzedGraph, methodMap, STRENGTHENED_GRAPH_TAG);
        }
    }

    private boolean persistGraph(AnalysisMethod method, EncodedGraph analyzedGraph, EconomicMap<String, Object> methodMap, String graphTag) {
        if (!useSharedLayerGraphs) {
            return false;
        }
        byte[] encodedGraph = ObjectCopier.encode(imageLayerSnapshotUtil.getGraphEncoder(this), analyzedGraph);
        if (contains(encodedGraph, LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING.getBytes(StandardCharsets.UTF_8))) {
            throw AnalysisError.shouldNotReachHere("The graph for the method %s contains a reference to a lambda type, which cannot be decoded: %s".formatted(method, encodedGraph));
        }
        String location = graphsOutput.add(encodedGraph);
        methodMap.put(graphTag, location);
        return true;
    }

    private static boolean contains(byte[] data, byte[] seq) {
        outer: for (int i = 0; i <= data.length - seq.length; i++) {
            for (int j = 0; j < seq.length; j++) {
                if (data[i + j] != seq[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private EconomicMap<String, Object> getMethodMap(AnalysisMethod method) {
        String name = imageLayerSnapshotUtil.getMethodIdentifier(method);
        EconomicMap<String, Object> methodMap = methodsMap.get(name);
        if (methodMap == null) {
            methodMap = EconomicMap.create();
            methodsMap.put(name, methodMap);
        }
        return methodMap;
    }

    protected void persistField(AnalysisField field) {
        EconomicMap<String, Object> fieldMap = EconomicMap.create();

        persistField(field, fieldMap);

        Field originalField = OriginalFieldProvider.getJavaField(field);
        if (originalField != null && !originalField.getDeclaringClass().equals(field.getDeclaringClass().getJavaClass())) {
            fieldMap.put(CLASS_NAME_TAG, originalField.getDeclaringClass().getName());
        }
        fieldMap.put(IS_INTERNAL_TAG, field.isInternal());
        fieldMap.put(FIELD_TYPE_TAG, field.getType().getId());
        fieldMap.put(MODIFIERS_TAG, field.getModifiers());
        fieldMap.put(POSITION_TAG, field.getPosition());
        fieldMap.put(ANNOTATIONS_TAG, Arrays.stream(AnnotationAccess.getAnnotationTypes(field)).map(Class::getName).toList());

        String tid = String.valueOf(field.getDeclaringClass().getId());
        fieldsMap.computeIfAbsent(tid, key -> new ConcurrentHashMap<>()).put(field.getName(), fieldMap);
    }

    protected void persistField(AnalysisField field, EconomicMap<String, Object> fieldMap) {
        fieldMap.put(ID_TAG, field.getId());
        fieldMap.put(FIELD_ACCESSED_TAG, field.getAccessedReason() != null);
        fieldMap.put(FIELD_READ_TAG, field.getReadReason() != null);
        fieldMap.put(FIELD_WRITTEN_TAG, field.getWrittenReason() != null);
        fieldMap.put(FIELD_FOLDED_TAG, field.getFoldedReason() != null);
    }

    protected void persistConstant(int parentId, int index, ImageHeapConstant imageHeapConstant) {
        if (!constantsMap.containsKey(Integer.toString(getConstantId(imageHeapConstant)))) {
            EconomicMap<String, Object> constantMap = EconomicMap.create();
            persistConstant(parentId, index, imageHeapConstant, constantMap);
        }
    }

    protected void persistConstant(int parentId, int index, ImageHeapConstant imageHeapConstant, EconomicMap<String, Object> constantMap) {
        int id = getConstantId(imageHeapConstant);
        constantsMap.put(Integer.toString(id), constantMap);
        constantMap.put(TID_TAG, imageHeapConstant.getType().getId());

        IdentityHashCodeProvider identityHashCodeProvider = (IdentityHashCodeProvider) aUniverse.getBigbang().getConstantReflectionProvider();
        int identityHashCode = identityHashCodeProvider.identityHashCode(imageHeapConstant);
        constantMap.put(IDENTITY_HASH_CODE_TAG, identityHashCode);

        switch (imageHeapConstant) {
            case ImageHeapInstance imageHeapInstance -> {
                Object[] fieldValues = imageHeapInstance.isReaderInstalled() ? imageHeapInstance.getFieldValues() : null;
                persistConstant(id, imageHeapConstant.getType(), constantMap, INSTANCE_TAG, fieldValues);
                persistConstantRelinkingInfo(constantMap, imageHeapConstant, aUniverse.getBigbang());
            }
            case ImageHeapObjectArray imageHeapObjectArray ->
                persistConstant(id, imageHeapConstant.getType(), constantMap, ARRAY_TAG, imageHeapObjectArray.getElementValues());
            case ImageHeapPrimitiveArray imageHeapPrimitiveArray -> {
                constantMap.put(CONSTANT_TYPE_TAG, PRIMITIVE_ARRAY_TAG);
                constantMap.put(DATA_TAG, getString(imageHeapPrimitiveArray.getType().getComponentType().getJavaKind(), imageHeapPrimitiveArray.getArray()));
            }
            case ImageHeapRelocatableConstant relocatableConstant -> {
                constantMap.put(CONSTANT_TYPE_TAG, RELOCATED_CONSTANT_TAG);
                constantMap.put(DATA_TAG, relocatableConstant.getConstantData().key);
            }
            default -> throw AnalysisError.shouldNotReachHere("Unexpected constant type " + imageHeapConstant);
        }

        if (!constantsToRelink.contains(id) && parentId != UNDEFINED_CONSTANT_ID) {
            constantMap.put(PARENT_CONSTANT_ID_TAG, parentId);
            assert index != UNDEFINED_FIELD_INDEX : "Tried to persist child constant %s from parent constant %d, but got index %d".formatted(imageHeapConstant, parentId, index);
            constantMap.put(PARENT_CONSTANT_INDEX_TAG, index);
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

    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id) {
        if (clazz.equals(String.class)) {
            String value = bb.getSnippetReflectionProvider().asObject(String.class, hostedObject);
            if (internedStringsIdentityMap.containsKey(value)) {
                /*
                 * Interned strings must be relinked.
                 */
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

    protected void persistConstant(int id, AnalysisType type, EconomicMap<String, Object> constantMap, String constantType, Object[] values) {
        constantMap.put(CONSTANT_TYPE_TAG, constantType);
        if (values != null) {
            List<List<Object>> data = new ArrayList<>();
            for (int i = 0; i < values.length; ++i) {
                Object object = values[i];
                if (delegateProcessing(data, object)) {
                    /* The object was already persisted */
                } else if (object instanceof ImageHeapConstant imageHeapConstant) {
                    data.add(List.of(OBJECT_TAG, getConstantId(imageHeapConstant)));
                    /*
                     * Some constants are not in imageHeap#reachableObjects, but are still created
                     * in reachable constants. They can be created in the extension image, but
                     * should not be used.
                     */
                    persistConstant(imageLayerSnapshotUtil.getRelinkedFields(type, aUniverse.getBigbang().getMetaAccess()).contains(i) ? id : UNDEFINED_CONSTANT_ID, i, imageHeapConstant);
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
