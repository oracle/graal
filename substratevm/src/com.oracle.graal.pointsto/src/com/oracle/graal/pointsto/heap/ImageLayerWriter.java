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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.UNDEFINED_CONSTANT_ID;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.UNDEFINED_FIELD_INDEX;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.capnproto.ListBuilder;
import org.capnproto.MessageBuilder;
import org.capnproto.PrimitiveList;
import org.capnproto.Serialize;
import org.capnproto.StructBuilder;
import org.capnproto.StructList;
import org.capnproto.Text;
import org.capnproto.TextList;
import org.capnproto.Void;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.EnumConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.StringConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveValue;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.FileDumpingUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.PrimitiveConstant;

public class ImageLayerWriter {
    protected ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageLayerWriterHelper imageLayerWriterHelper;
    private ImageHeap imageHeap;
    protected AnalysisUniverse aUniverse;
    private IdentityHashMap<String, String> internedStringsIdentityMap;

    private final MessageBuilder snapshotFileBuilder = new MessageBuilder();
    protected final SharedLayerSnapshot.Builder snapshotBuilder = this.snapshotFileBuilder.initRoot(SharedLayerSnapshot.factory);
    private Map<ImageHeapConstant, ConstantParent> constantsMap;
    private final Map<String, MethodGraphsInfo> methodsMap = new ConcurrentHashMap<>();
    private FileInfo fileInfo;
    private GraphsOutput graphsOutput;
    private final boolean useSharedLayerGraphs;
    private final boolean useSharedLayerStrengthenedGraphs;

    /*
     * Types, members and constants to persist even when they are not considered reachable by the
     * analysis, or referenced from the image heap. Typically, these elements would be reachable
     * from a persisted graph.
     */
    private boolean sealed = false;
    private final Set<AnalysisType> typesToPersist = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> methodsToPersist = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisField> fieldsToPersist = ConcurrentHashMap.newKeySet();
    private final Set<ImageHeapConstant> constantsToPersist = ConcurrentHashMap.newKeySet();

    public void ensureTypePersisted(AnalysisType type) {
        assert !sealed;
        if (typesToPersist.add(type)) {
            afterTypeAdded(type);
        }
    }

    public void ensureMethodPersisted(AnalysisMethod method) {
        assert !sealed;
        if (methodsToPersist.add(method)) {
            afterMethodAdded(method);
        }
    }

    public void ensureFieldPersisted(AnalysisField field) {
        assert !sealed;
        fieldsToPersist.add(field);
    }

    public void ensureConstantPersisted(ImageHeapConstant constant) {
        assert !sealed;
        constantsToPersist.add(constant);
    }

    protected record ConstantParent(int constantId, int index) {
        static ConstantParent NONE = new ConstantParent(UNDEFINED_CONSTANT_ID, UNDEFINED_FIELD_INDEX);
    }

    private record FileInfo(Path layerFilePath, String fileName, String suffix) {
    }

    protected record MethodGraphsInfo(String analysisGraphLocation, boolean analysisGraphIsIntrinsic, String strengthenedGraphLocation) {

        static final MethodGraphsInfo NO_GRAPHS = new MethodGraphsInfo(null, false, null);

        MethodGraphsInfo withAnalysisGraph(String location, boolean isIntrinsic) {
            assert analysisGraphLocation == null && !analysisGraphIsIntrinsic;
            return new MethodGraphsInfo(location, isIntrinsic, strengthenedGraphLocation);
        }

        MethodGraphsInfo withStrengthenedGraph(String location) {
            assert strengthenedGraphLocation == null;
            return new MethodGraphsInfo(analysisGraphLocation, analysisGraphIsIntrinsic, location);
        }
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

    public ImageLayerWriter(boolean useSharedLayerGraphs) {
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        this.useSharedLayerStrengthenedGraphs = false;
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
            try {
                Serialize.write(Channels.newChannel(outputStream), snapshotFileBuilder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void initializeExternalValues() {
        imageLayerSnapshotUtil.initializeExternalValues();
    }

    public void setImageHeapSize(long imageHeapSize) {
        snapshotBuilder.setImageHeapSize(imageHeapSize);
    }

    public void persistAnalysisInfo() {
        persistHook();

        // Late constant scan so all of them are known with values available (readers installed)
        List<ImageHeapConstant> constantsToScan = new ArrayList<>(constantsToPersist);
        imageHeap.getReachableObjects().values().forEach(constantsToScan::addAll);
        constantsMap = HashMap.newHashMap(constantsToScan.size());
        constantsToScan.forEach(c -> constantsMap.put(c, ConstantParent.NONE));
        while (!constantsToScan.isEmpty()) {
            List<ImageHeapConstant> discoveredConstants = new ArrayList<>();
            constantsToScan.forEach(con -> scanConstantReferencedObjects(con, discoveredConstants));
            constantsToScan = discoveredConstants;
        }

        snapshotBuilder.setNextTypeId(aUniverse.getNextTypeId());
        snapshotBuilder.setNextMethodId(aUniverse.getNextMethodId());
        snapshotBuilder.setNextFieldId(aUniverse.getNextFieldId());
        snapshotBuilder.setNextConstantId(ImageHeapConstant.getCurrentId());

        initSortedList(snapshotBuilder::initTypes, typesToPersist, Comparator.comparingInt(AnalysisType::getId), this::persistType);
        initSortedList(snapshotBuilder::initMethods, methodsToPersist, Comparator.comparingInt(AnalysisMethod::getId), this::persistMethod);
        initSortedList(snapshotBuilder::initFields, fieldsToPersist, Comparator.comparingInt(AnalysisField::getId), this::persistField);

        Set<Integer> constantsToRelink = new HashSet<>();
        initSortedList(snapshotBuilder::initConstants, constantsMap.entrySet(),
                        (a, b) -> Integer.compare(getConstantId(a.getKey()), getConstantId(b.getKey())),
                        (entry, bsupplier) -> persistConstant(entry.getKey(), entry.getValue(), bsupplier.get(), constantsToRelink));
        initInts(snapshotBuilder::initConstantsToRelink, constantsToRelink.stream().mapToInt(i -> i).sorted());
    }

    protected static <S extends StructBuilder, T> void initSortedList(IntFunction<StructList.Builder<S>> init, Collection<T> objects, Comparator<T> comparator, BiConsumer<T, Supplier<S>> action) {
        @SuppressWarnings("unchecked")
        T[] array = (T[]) objects.toArray();
        Arrays.sort(array, comparator);

        StructList.Builder<S> builder = init.apply(objects.size());
        Iterator<S> iterator = builder.iterator();
        for (T t : array) {
            action.accept(t, iterator::next);
        }
        AnalysisError.guarantee(!iterator.hasNext(), "all created struct builders must have been used");
    }

    private void persistAnnotations(AnnotatedElement annotatedElement, IntFunction<StructList.Builder<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Builder>> builder) {
        Class<? extends Annotation>[] annotationTypes = AnnotationAccess.getAnnotationTypes(annotatedElement);
        persistAnnotations(annotatedElement, annotationTypes, builder);
    }

    @SuppressWarnings("unused")
    protected void persistAnnotations(AnnotatedElement annotatedElement, Class<? extends Annotation>[] annotationTypes,
                    IntFunction<StructList.Builder<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Builder>> builder) {
        var b = builder.apply(annotationTypes.length);
        for (int i = 0; i < annotationTypes.length; i++) {
            b.get(i).setTypeName(annotationTypes[i].getName());
            persistAnnotationValues(annotatedElement, annotationTypes[i], b.get(i)::initValues);
        }
    }

    @SuppressWarnings("unused")
    protected void persistAnnotationValues(AnnotatedElement annotatedElement, Class<? extends Annotation> annotationType,
                    IntFunction<StructList.Builder<SharedLayerSnapshotCapnProtoSchemaHolder.AnnotationValue.Builder>> builder) {
    }

    /**
     * A hook used to persist more general information about the base layer not accessible in
     * pointsto.
     */
    @SuppressWarnings("unused")
    protected void persistHook() {

    }

    public boolean isTypePersisted(AnalysisType type) {
        return typesToPersist.contains(type);
    }

    private void persistType(AnalysisType type, Supplier<PersistedAnalysisType.Builder> builderSupplier) {
        String typeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(type);
        persistType(type, typeDescriptor, builderSupplier.get());
    }

    protected void persistType(AnalysisType type, String typeDescriptor, PersistedAnalysisType.Builder builder) {
        builder.setId(type.getId());
        builder.setDescriptor(typeDescriptor);

        initInts(builder::initFields, Arrays.stream(type.getInstanceFields(true)).mapToInt(f -> ((AnalysisField) f).getId()));
        builder.setClassJavaName(type.toJavaName());
        builder.setClassName(type.getName());
        builder.setModifiers(type.getModifiers());
        builder.setIsInterface(type.isInterface());
        builder.setIsEnum(type.isEnum());
        builder.setIsInitialized(type.isInitialized());
        builder.setIsLinked(type.isLinked());
        if (type.getSourceFileName() != null) {
            builder.setSourceFileName(type.getSourceFileName());
        }
        try {
            AnalysisType enclosingType = type.getEnclosingType();
            if (enclosingType != null) {
                builder.setEnclosingTypeId(enclosingType.getId());
            }
        } catch (AnalysisError.TypeNotFoundError e) {
            /*
             * GR-59571: The enclosing type is not automatically created when the inner type is
             * created. If the enclosing type is missing, it is ignored for now. This try/catch
             * block could be removed after the trackAcrossLayers is fully implemented.
             */
        }
        if (type.isArray()) {
            builder.setComponentTypeId(type.getComponentType().getId());
        }
        if (type.getSuperclass() != null) {
            builder.setSuperClassTypeId(type.getSuperclass().getId());
        }
        initInts(builder::initInterfaces, Arrays.stream(type.getInterfaces()).mapToInt(AnalysisType::getId));
        initInts(builder::initInstanceFieldIds, Arrays.stream(type.getInstanceFields(false)).mapToInt(f -> ((AnalysisField) f).getId()));
        initInts(builder::initInstanceFieldIdsWithSuper, Arrays.stream(type.getInstanceFields(true)).mapToInt(f -> ((AnalysisField) f).getId()));
        initInts(builder::initStaticFieldIds, Arrays.stream(type.getStaticFields()).mapToInt(f -> ((AnalysisField) f).getId()));
        persistAnnotations(type, builder::initAnnotationList);

        builder.setIsInstantiated(type.isInstantiated());
        builder.setIsUnsafeAllocated(type.isUnsafeAllocated());
        builder.setIsReachable(type.isReachable());

        imageLayerWriterHelper.persistType(type, builder);
    }

    protected static void initInts(IntFunction<PrimitiveList.Int.Builder> builderSupplier, IntStream ids) {
        int[] values = ids.toArray();
        PrimitiveList.Int.Builder builder = builderSupplier.apply(values.length);
        for (int i = 0; i < values.length; i++) {
            builder.set(i, values[i]);
        }
    }

    protected static void initStringList(IntFunction<TextList.Builder> builderSupplier, Stream<String> strings) {
        Object[] array = strings.toArray();
        TextList.Builder builder = builderSupplier.apply(array.length);
        for (int i = 0; i < array.length; i++) {
            builder.set(i, new Text.Reader(array[i].toString()));
        }
    }

    protected void afterTypeAdded(AnalysisType type) {
        /*
         * Some persisted types are not reachable. In this case, the super class and interfaces have
         * to be persisted manually as well.
         */
        if (type.getSuperclass() != null) {
            ensureTypePersisted(type.getSuperclass());
        }
        for (AnalysisType iface : type.getInterfaces()) {
            ensureTypePersisted(iface);
        }
    }

    protected void afterMethodAdded(AnalysisMethod method) {
        ensureTypePersisted(method.getSignature().getReturnType());
        imageLayerWriterHelper.afterMethodAdded(method);
    }

    private void scanConstantReferencedObjects(ImageHeapConstant constant, Collection<ImageHeapConstant> discoveredConstants) {
        if (Objects.requireNonNull(constant) instanceof ImageHeapInstance instance) {
            if (instance.isReaderInstalled()) {
                scanConstantReferencedObjects(constant, instance.getFieldValues(), discoveredConstants);
            }
        } else if (constant instanceof ImageHeapObjectArray objArray) {
            scanConstantReferencedObjects(constant, objArray.getElementValues(), discoveredConstants);
        }
    }

    protected void scanConstantReferencedObjects(ImageHeapConstant constant, Object[] referencedObjects, Collection<ImageHeapConstant> discoveredConstants) {
        if (referencedObjects != null) {
            for (int i = 0; i < referencedObjects.length; i++) {
                AnalysisType parentType = constant.getType();
                if (referencedObjects[i] instanceof ImageHeapConstant con && !constantsMap.containsKey(con)) {
                    /*
                     * Some constants are not in imageHeap#reachableObjects, but are still created
                     * in reachable constants. They can be created in the extension image, but
                     * should not be used.
                     */
                    Set<Integer> relinkedFields = imageLayerSnapshotUtil.getRelinkedFields(parentType, aUniverse.getBigbang().getMetaAccess());
                    ConstantParent parent = relinkedFields.contains(i) ? new ConstantParent(getConstantId(constant), i) : ConstantParent.NONE;

                    discoveredConstants.add(con);
                    constantsMap.put(con, parent);
                }
            }
        }
    }

    private void persistMethod(AnalysisMethod method, Supplier<PersistedAnalysisMethod.Builder> builderSupplier) {
        persistMethod(method, builderSupplier.get());
    }

    protected void persistMethod(AnalysisMethod method, PersistedAnalysisMethod.Builder builder) {
        MethodGraphsInfo graphsInfo = methodsMap.putIfAbsent(imageLayerSnapshotUtil.getMethodDescriptor(method), MethodGraphsInfo.NO_GRAPHS);
        Executable executable = method.getJavaMethod();

        if (builder.getId() != 0) {
            throw GraalError.shouldNotReachHere("The method descriptor should be unique, but " + imageLayerSnapshotUtil.getMethodDescriptor(method) + " got added twice.");
        }
        if (executable != null) {
            initStringList(builder::initArgumentClassNames, Arrays.stream(executable.getParameterTypes()).map(Class::getName));
            builder.setClassName(executable.getDeclaringClass().getName());
        }

        builder.setDescriptor(imageLayerSnapshotUtil.getMethodDescriptor(method));
        builder.setDeclaringTypeId(method.getDeclaringClass().getId());
        initInts(builder::initArgumentTypeIds, method.getSignature().toParameterList(null).stream().mapToInt(AnalysisType::getId));
        builder.setId(method.getId());
        builder.setName(method.getName());
        builder.setReturnTypeId(method.getSignature().getReturnType().getId());
        builder.setIsVarArgs(method.isVarArgs());
        builder.setCanBeStaticallyBound(method.canBeStaticallyBound());
        builder.setModifiers(method.getModifiers());
        builder.setIsConstructor(method.isConstructor());
        builder.setIsSynthetic(method.isSynthetic());
        byte[] code = method.getCode();
        if (code != null) {
            builder.setCode(code);
        }
        builder.setCodeSize(method.getCodeSize());
        IntrinsicMethod intrinsicMethod = aUniverse.getBigbang().getConstantReflectionProvider().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
        if (intrinsicMethod != null) {
            builder.setMethodHandleIntrinsicName(intrinsicMethod.name());
        }
        persistAnnotations(method, builder::initAnnotationList);

        builder.setIsVirtualRootMethod(method.isVirtualRootMethod());
        builder.setIsDirectRootMethod(method.isDirectRootMethod());
        builder.setIsInvoked(method.isSimplyInvoked());
        builder.setIsImplementationInvoked(method.isSimplyImplementationInvoked());
        builder.setIsIntrinsicMethod(method.isIntrinsicMethod());

        if (graphsInfo != null && graphsInfo.analysisGraphLocation != null) {
            builder.setAnalysisGraphLocation(graphsInfo.analysisGraphLocation);
            builder.setAnalysisGraphIsIntrinsic(graphsInfo.analysisGraphIsIntrinsic);
        }
        if (graphsInfo != null && graphsInfo.strengthenedGraphLocation != null) {
            builder.setStrengthenedGraphLocation(graphsInfo.strengthenedGraphLocation);
        }

        imageLayerWriterHelper.persistMethod(method, builder);
    }

    public boolean isMethodPersisted(AnalysisMethod method) {
        String name = imageLayerSnapshotUtil.getMethodDescriptor(method);
        return methodsMap.containsKey(name);
    }

    public void persistMethodGraphs() {
        assert aUniverse.sealed();

        aUniverse.getTypes().stream().filter(AnalysisType::isTrackedAcrossLayers)
                        .forEach(this::ensureTypePersisted);

        aUniverse.getMethods().stream().filter(AnalysisMethod::isTrackedAcrossLayers)
                        .forEach(this::ensureMethodPersisted);

        aUniverse.getFields().stream().filter(AnalysisField::isTrackedAcrossLayers)
                        .forEach(this::ensureFieldPersisted);

        // Persisting graphs discovers additional types, members and constants that need persisting
        Set<AnalysisMethod> persistedGraphMethods = new HashSet<>();
        do {
            for (AnalysisMethod method : methodsToPersist) {
                if (persistedGraphMethods.add(method)) {
                    persistAnalysisParsedGraph(method);
                }
            }
        } while (!persistedGraphMethods.equals(methodsToPersist));

        // Note that constants are scanned late so all values are available.

        sealed = true;
    }

    private void persistAnalysisParsedGraph(AnalysisMethod method) {
        Object analyzedGraph = method.getGraph();
        if (analyzedGraph instanceof AnalysisParsedGraph analysisParsedGraph) {
            String name = imageLayerSnapshotUtil.getMethodDescriptor(method);
            MethodGraphsInfo graphsInfo = methodsMap.get(name);
            if (graphsInfo == null || graphsInfo.analysisGraphLocation == null) {
                String location = persistGraph(method, analysisParsedGraph.getEncodedGraph());
                if (location != null) {
                    methodsMap.compute(name, (n, mgi) -> (mgi != null ? mgi : MethodGraphsInfo.NO_GRAPHS)
                                    .withAnalysisGraph(location, analysisParsedGraph.isIntrinsic()));
                }
            }
        }
    }

    public void persistMethodStrengthenedGraph(AnalysisMethod method) {
        if (!useSharedLayerStrengthenedGraphs) {
            return;
        }

        String name = imageLayerSnapshotUtil.getMethodDescriptor(method);
        MethodGraphsInfo graphsInfo = methodsMap.get(name);

        if (graphsInfo == null || graphsInfo.strengthenedGraphLocation == null) {
            EncodedGraph analyzedGraph = method.getAnalyzedGraph();
            String location = persistGraph(method, analyzedGraph);
            methodsMap.compute(name, (n, mgi) -> (mgi != null ? mgi : MethodGraphsInfo.NO_GRAPHS).withStrengthenedGraph(location));
        }
    }

    private String persistGraph(AnalysisMethod method, EncodedGraph analyzedGraph) {
        if (!useSharedLayerGraphs) {
            return null;
        }
        byte[] encodedGraph = ObjectCopier.encode(imageLayerSnapshotUtil.getGraphEncoder(this), analyzedGraph);
        if (contains(encodedGraph, LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING.getBytes(StandardCharsets.UTF_8))) {
            throw AnalysisError.shouldNotReachHere("The graph for the method %s contains a reference to a lambda type, which cannot be decoded: %s".formatted(method, encodedGraph));
        }
        return graphsOutput.add(encodedGraph);
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

    private void persistField(AnalysisField field, Supplier<PersistedAnalysisField.Builder> fieldBuilderSupplier) {
        PersistedAnalysisField.Builder builder = fieldBuilderSupplier.get();

        persistField(field, builder);

        Field originalField = OriginalFieldProvider.getJavaField(field);
        if (originalField != null && !originalField.getDeclaringClass().equals(field.getDeclaringClass().getJavaClass())) {
            builder.setClassName(originalField.getDeclaringClass().getName());
        }
        builder.setIsStatic(field.isStatic());
        builder.setIsInternal(field.isInternal());
        builder.setIsSynthetic(field.isSynthetic());
        builder.setTypeId(field.getType().getId());
        builder.setModifiers(field.getModifiers());
        builder.setPosition(field.getPosition());

        persistAnnotations(field, builder::initAnnotationList);
    }

    protected void persistField(AnalysisField field, PersistedAnalysisField.Builder builder) {
        builder.setId(field.getId());
        builder.setDeclaringTypeId(field.getDeclaringClass().getId());
        builder.setName(field.getName());
        builder.setIsAccessed(field.getAccessedReason() != null);
        builder.setIsRead(field.getReadReason() != null);
        builder.setIsWritten(field.getWrittenReason() != null);
        builder.setIsFolded(field.getFoldedReason() != null);
    }

    protected void persistConstant(ImageHeapConstant imageHeapConstant, ConstantParent parent, PersistedConstant.Builder builder, Set<Integer> constantsToRelink) {
        int id = getConstantId(imageHeapConstant);
        builder.setId(id);
        builder.setTypeId(imageHeapConstant.getType().getId());

        IdentityHashCodeProvider identityHashCodeProvider = (IdentityHashCodeProvider) aUniverse.getBigbang().getConstantReflectionProvider();
        int identityHashCode = identityHashCodeProvider.identityHashCode(imageHeapConstant);
        builder.setIdentityHashCode(identityHashCode);

        switch (imageHeapConstant) {
            case ImageHeapInstance imageHeapInstance -> {
                builder.initObject().setInstance(Void.VOID);
                Object[] fieldValues = imageHeapInstance.isReaderInstalled() ? imageHeapInstance.getFieldValues() : null;
                persistConstantObjectData(builder.getObject(), fieldValues);
                persistConstantRelinkingInfo(builder, imageHeapConstant, constantsToRelink, aUniverse.getBigbang());
            }
            case ImageHeapObjectArray imageHeapObjectArray -> {
                builder.initObject().setObjectArray(Void.VOID);
                persistConstantObjectData(builder.getObject(), imageHeapObjectArray.getElementValues());
            }
            case ImageHeapPrimitiveArray imageHeapPrimitiveArray ->
                persistConstantPrimitiveArray(builder.initPrimitiveData(), imageHeapPrimitiveArray.getType().getComponentType().getJavaKind(), imageHeapPrimitiveArray.getArray());
            case ImageHeapRelocatableConstant relocatableConstant -> {
                builder.initRelocatable().setKey(relocatableConstant.getConstantData().key);
            }
            default -> throw AnalysisError.shouldNotReachHere("Unexpected constant type " + imageHeapConstant);
        }

        if (!constantsToRelink.contains(id) && parent != ConstantParent.NONE) {
            builder.setParentConstantId(parent.constantId);
            assert parent.index != UNDEFINED_FIELD_INDEX : "Tried to persist child constant %s from parent constant %d, but got index %d".formatted(imageHeapConstant, parent.constantId, parent.index);
            builder.setParentIndex(parent.index);
        }
    }

    protected int getConstantId(ImageHeapConstant imageHeapConstant) {
        return imageHeapConstant.constantData.id;
    }

    private void persistConstantRelinkingInfo(PersistedConstant.Builder builder, ImageHeapConstant imageHeapConstant, Set<Integer> constantsToRelink, BigBang bb) {
        Class<?> clazz = imageHeapConstant.getType().getJavaClass();
        JavaConstant hostedObject = imageHeapConstant.getHostedObject();
        boolean simulated = hostedObject == null;
        builder.setIsSimulated(simulated);
        if (!simulated) {
            persistConstantRelinkingInfo(builder.getObject().getRelinking(), bb, clazz, hostedObject, imageHeapConstant.constantData.id, constantsToRelink);
        }
    }

    protected void persistConstantRelinkingInfo(PersistedConstant.Object.Relinking.Builder builder, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id, Set<Integer> constantsToRelink) {
        if (clazz.equals(String.class)) {
            StringConstant.Builder stringConstantBuilder = builder.initStringConstant();
            String value = bb.getSnippetReflectionProvider().asObject(String.class, hostedObject);
            if (internedStringsIdentityMap.containsKey(value)) {
                /*
                 * Interned strings must be relinked.
                 */
                stringConstantBuilder.setValue(value);
                constantsToRelink.add(id);
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            EnumConstant.Builder enumBuilder = builder.initEnumConstant();
            Enum<?> value = bb.getSnippetReflectionProvider().asObject(Enum.class, hostedObject);
            enumBuilder.setEnumClass(value.getDeclaringClass().getName());
            enumBuilder.setEnumName(value.name());
            constantsToRelink.add(id);
        }
    }

    protected static void persistConstantPrimitiveArray(PrimitiveArray.Builder builder, JavaKind componentKind, Object array) {
        assert componentKind.toJavaClass().equals(array.getClass().getComponentType());
        switch (array) {
            case boolean[] a -> persistArray(a, builder::initZ, (b, i) -> b.set(i, a[i]));
            case byte[] a -> persistArray(a, builder::initB, (b, i) -> b.set(i, a[i]));
            case short[] a -> persistArray(a, builder::initS, (b, i) -> b.set(i, a[i]));
            case char[] a -> persistArray(a, builder::initC, (b, i) -> b.set(i, (short) a[i]));
            case int[] a -> persistArray(a, builder::initI, (b, i) -> b.set(i, a[i]));
            case long[] a -> persistArray(a, builder::initJ, (b, i) -> b.set(i, a[i]));
            case float[] a -> persistArray(a, builder::initF, (b, i) -> b.set(i, a[i]));
            case double[] a -> persistArray(a, builder::initD, (b, i) -> b.set(i, a[i]));
            default -> throw new IllegalArgumentException("Unsupported kind: " + componentKind);
        }
    }

    /** Enables concise one-liners in {@link #persistConstantPrimitiveArray}. */
    private static <A, T extends ListBuilder> void persistArray(A array, IntFunction<T> init, ObjIntConsumer<T> setter) {
        int length = Array.getLength(array);
        T builder = init.apply(length);
        for (int i = 0; i < length; i++) {
            setter.accept(builder, i);
        }
    }

    private void persistConstantObjectData(PersistedConstant.Object.Builder builder, Object[] values) {
        if (values != null) {
            StructList.Builder<ConstantReference.Builder> refsBuilder = builder.initData(values.length);
            for (int i = 0; i < values.length; ++i) {
                Object object = values[i];
                ConstantReference.Builder b = refsBuilder.get(i);
                if (delegateProcessing(b, object)) {
                    /* The object was already persisted */
                } else if (object instanceof ImageHeapConstant imageHeapConstant) {
                    assert constantsMap.containsKey(imageHeapConstant);
                    b.initObjectConstant().setConstantId(getConstantId(imageHeapConstant));
                } else if (object == JavaConstant.NULL_POINTER) {
                    b.setNullPointer(Void.VOID);
                } else if (object instanceof PrimitiveConstant pc) {
                    PrimitiveValue.Builder pb = b.initPrimitiveValue();
                    pb.setTypeChar(NumUtil.safeToUByte(pc.getJavaKind().getTypeChar()));
                    pb.setRawValue(pc.getRawValue());
                } else {
                    AnalysisError.guarantee(object instanceof AnalysisFuture<?>, "Unexpected constant %s", object);
                    b.setNotMaterialized(Void.VOID);
                }
            }
        }
    }

    /**
     * Hook for subclasses to do their own processing.
     */
    @SuppressWarnings("unused")
    protected boolean delegateProcessing(ConstantReference.Builder builder, Object constant) {
        return false;
    }
}
