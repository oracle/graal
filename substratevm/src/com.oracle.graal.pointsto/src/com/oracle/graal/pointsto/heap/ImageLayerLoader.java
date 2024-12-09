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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_INIT_NAME;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.graal.pointsto.util.AnalysisError.guarantee;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.capnproto.ListReader;
import org.capnproto.PrimitiveList;
import org.capnproto.ReaderOptions;
import org.capnproto.Serialize;
import org.capnproto.StructList;
import org.capnproto.StructReader;
import org.capnproto.Text;
import org.capnproto.TextList;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.EnumConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.StringConstant;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
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
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ImageLayerLoader {
    private final Map<Integer, AnalysisType> types = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisField> fields = new ConcurrentHashMap<>();
    protected final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();
    private final List<FilePaths> loadPaths;
    private final Map<Integer, BaseLayerType> baseLayerTypes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> typeToHubIdentityHashCode = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerMethod> baseLayerMethods = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerField> baseLayerFields = new ConcurrentHashMap<>();

    /** Map from {@link ImageLayerSnapshotUtil#getTypeDescriptor} to base layer type ids. */
    private final Map<String, Integer> typeDescriptorToBaseLayerId = new HashMap<>();
    /** Map from {@link ImageLayerSnapshotUtil#getMethodDescriptor} to base layer method ids. */
    private final Map<String, Integer> methodDescriptorToBaseLayerId = new HashMap<>();

    protected final Set<AnalysisFuture<Void>> heapScannerTasks = ConcurrentHashMap.newKeySet();
    private ImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageLayerLoaderHelper imageLayerLoaderHelper;
    protected final Map<Integer, Integer> typeToConstant = new ConcurrentHashMap<>();
    protected final Map<String, Integer> stringToConstant = new ConcurrentHashMap<>();
    protected final Map<Enum<?>, Integer> enumToConstant = new ConcurrentHashMap<>();
    protected final Map<Integer, Long> objectOffsets = new ConcurrentHashMap<>();
    protected final Map<AnalysisField, Integer> fieldLocations = new ConcurrentHashMap<>();
    protected AnalysisUniverse universe;
    protected AnalysisMetaAccess metaAccess;
    protected HostedValuesProvider hostedValuesProvider;

    protected SharedLayerSnapshot.Reader snapshot;
    protected FileChannel graphsChannel;

    public record FilePaths(Path snapshot, Path snapshotGraphs) {
    }

    public ImageLayerLoader() {
        this(List.of());
    }

    public ImageLayerLoader(List<FilePaths> loadPaths) {
        this.loadPaths = loadPaths;
    }

    public void setImageLayerSnapshotUtil(ImageLayerSnapshotUtil imageLayerSnapshotUtil) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
    }

    public AnalysisUniverse getUniverse() {
        return universe;
    }

    public void setUniverse(AnalysisUniverse newUniverse) {
        this.universe = newUniverse;
    }

    public void setImageLayerLoaderHelper(ImageLayerLoaderHelper imageLayerLoaderHelper) {
        this.imageLayerLoaderHelper = imageLayerLoaderHelper;
    }

    /** This code is not thread safe. */
    protected void openFilesAndLoadJsonMap() {
        assert loadPaths.size() == 1 : "Currently only one path is supported for image layer loading " + loadPaths;
        if (snapshot == null) {
            for (FilePaths paths : loadPaths) {
                try {
                    graphsChannel = FileChannel.open(paths.snapshotGraphs);

                    try (FileChannel ch = FileChannel.open(paths.snapshot)) {
                        MappedByteBuffer bb = ch.map(FileChannel.MapMode.READ_ONLY, ch.position(), ch.size());
                        ReaderOptions opt = new ReaderOptions(Long.MAX_VALUE, ReaderOptions.DEFAULT_READER_OPTIONS.nestingLimit);
                        snapshot = Serialize.read(bb, opt).getRoot(SharedLayerSnapshot.factory);
                        // NOTE: buffer is never unmapped, but is read-only and pages can be evicted
                    }
                } catch (IOException e) {
                    throw AnalysisError.shouldNotReachHere("Error during image layer snapshot loading", e);
                }
            }
        }
    }

    public void loadLayerAnalysis() {
        openFilesAndLoadJsonMap();
        loadLayerAnalysis0();
    }

    public void cleanupAfterAnalysis() {
        if (graphsChannel != null) {
            try {
                graphsChannel.close();
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere(e);
            }
        }
    }

    /**
     * Initializes the {@link ImageLayerLoader}.
     */
    private void loadLayerAnalysis0() {
        /*
         * The new ids of the extension image need to be different from the ones from the base
         * layer. The start id is set to the next id of the base layer.
         */
        universe.setStartTypeId(snapshot.getNextTypeId());
        universe.setStartMethodId(snapshot.getNextMethodId());
        universe.setStartFieldId(snapshot.getNextFieldId());
        ImageHeapConstant.setCurrentId(snapshot.getNextConstantId());

        for (PersistedAnalysisType.Reader typeData : snapshot.getTypes()) {
            String descriptor = typeData.getDescriptor().toString();
            typeDescriptorToBaseLayerId.put(descriptor, typeData.getId());
        }

        for (PersistedAnalysisMethod.Reader methodData : snapshot.getMethods()) {
            String descriptor = methodData.getDescriptor().toString();
            methodDescriptorToBaseLayerId.put(descriptor, methodData.getId());
        }

        streamInts(snapshot.getConstantsToRelink()).mapToObj(this::findConstant)
                        .forEach(c -> prepareConstantRelinking(c, c.getIdentityHashCode(), c.getId()));
    }

    private PersistedConstant.Reader findConstant(int id) {
        return binarySearchUnique(id, snapshot.getConstants(), PersistedConstant.Reader::getId);
    }

    private static <T extends StructReader> T binarySearchUnique(int key, StructList.Reader<T> sortedList, ToIntFunction<T> keyExtractor) {
        int low = 0;
        int high = sortedList.size() - 1;

        int prevMid = -1;
        int prevKey = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midStruct = sortedList.get(mid);
            int midKey = keyExtractor.applyAsInt(midStruct);

            assert prevMid == -1 || (mid < prevMid && midKey < prevKey) || (mid > prevMid && midKey > prevKey) : "unsorted or contains duplicates";

            if (midKey < key) {
                low = mid + 1;
            } else if (midKey > key) {
                high = mid - 1;
            } else {
                return midStruct;
            }

            prevMid = mid;
            prevKey = midKey;
        }
        return null;
    }

    protected void prepareConstantRelinking(PersistedConstant.Reader constantData, int identityHashCode, int id) {
        if (!constantData.isObject()) {
            return;
        }

        PersistedConstant.Object.Relinking.Reader relinking = constantData.getObject().getRelinking();
        if (relinking.isStringConstant()) {
            String value = relinking.getStringConstant().getValue().toString();
            injectIdentityHashCode(value.intern(), identityHashCode);
            stringToConstant.put(value, id);
        } else if (relinking.isEnumConstant()) {
            EnumConstant.Reader enumConstant = relinking.getEnumConstant();
            Enum<?> enumValue = getEnumValue(enumConstant.getEnumClass(), enumConstant.getEnumName());
            injectIdentityHashCode(enumValue, identityHashCode);
            enumToConstant.put(enumValue, id);
        }
    }

    private void loadType(PersistedAnalysisType.Reader typeData) {
        int tid = typeData.getId();

        if (imageLayerLoaderHelper.loadType(typeData, tid)) {
            return;
        }

        String name = typeData.getClassJavaName().toString();
        Class<?> clazz = lookupBaseLayerTypeInHostVM(name);

        ResolvedJavaType superClass = getResolvedJavaTypeForBaseLayerId(typeData.getSuperClassTypeId());

        ResolvedJavaType[] interfaces = streamInts(typeData.getInterfaces())
                        .mapToObj(this::getResolvedJavaTypeForBaseLayerId).toArray(ResolvedJavaType[]::new);

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
            BaseLayerType baseLayerType = getBaseLayerType(typeData, tid, superClass, interfaces);

            baseLayerType.setInstanceFields(streamInts(typeData.getInstanceFieldIds())
                            .mapToObj(this::getBaseLayerField).toArray(ResolvedJavaField[]::new));
            baseLayerType.setInstanceFieldsWithSuper(streamInts(typeData.getInstanceFieldIdsWithSuper())
                            .mapToObj(this::getBaseLayerField).toArray(ResolvedJavaField[]::new));

            AnalysisType type = universe.lookup(baseLayerType);
            guarantee(getBaseLayerTypeId(type) == tid, "The base layer type %s is not correctly matched to the id %d", type, tid);
        }
    }

    private BaseLayerType getBaseLayerType(int tid) {
        PersistedAnalysisType.Reader typeData = findType(tid);
        ResolvedJavaType superClass = getResolvedJavaTypeForBaseLayerId(typeData.getSuperClassTypeId());
        ResolvedJavaType[] interfaces = streamInts(typeData.getInterfaces()).mapToObj(this::getResolvedJavaTypeForBaseLayerId).toArray(ResolvedJavaType[]::new);
        return getBaseLayerType(typeData, tid, superClass, interfaces);
    }

    private BaseLayerType getBaseLayerType(PersistedAnalysisType.Reader td, int tid, ResolvedJavaType superClass, ResolvedJavaType[] interfaces) {
        return baseLayerTypes.computeIfAbsent(tid, (typeId) -> {
            String className = td.getClassName().toString();
            String sourceFileName = td.hasSourceFileName() ? td.getSourceFileName().toString() : null;
            ResolvedJavaType enclosingType = getResolvedJavaTypeForBaseLayerId(td.getEnclosingTypeId());
            ResolvedJavaType componentType = getResolvedJavaTypeForBaseLayerId(td.getComponentTypeId());
            ResolvedJavaType objectType = universe.getOriginalMetaAccess().lookupJavaType(Object.class);
            Annotation[] annotations = getAnnotations(td.getAnnotationList());

            return new BaseLayerType(className, tid, td.getModifiers(), td.getIsInterface(), td.getIsEnum(), td.getIsInitialized(), td.getIsInitializedAtBuildTime(), td.getIsLinked(), sourceFileName,
                            enclosingType, componentType, superClass, interfaces, objectType, annotations);
        });
    }

    private static IntStream streamInts(PrimitiveList.Int.Reader reader) {
        return IntStream.range(0, reader.size()).map(reader::get);
    }

    private static Stream<String> streamStrings(TextList.Reader reader) {
        return IntStream.range(0, reader.size()).mapToObj(i -> reader.get(i).toString());
    }

    protected Annotation[] getAnnotations(@SuppressWarnings("unused") StructList.Reader<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Reader> elementData) {
        return new Annotation[0];
    }

    private ResolvedJavaType getResolvedJavaTypeForBaseLayerId(int tid) {
        return (tid == 0) ? null : getAnalysisTypeForBaseLayerId(tid).getWrapped();
    }

    public AnalysisType getAnalysisTypeForBaseLayerId(int tid) {
        if (!types.containsKey(tid)) {
            loadType(findType(tid));
        }
        guarantee(types.containsKey(tid), "Type with id %d was not correctly loaded.", tid);
        /*
         * The type needs to be looked up because it ensures the type is completely created, as the
         * types Map is populated before the type is created.
         */
        return universe.lookup(types.get(tid).getWrapped());
    }

    protected PersistedAnalysisType.Reader findType(int tid) {
        return binarySearchUnique(tid, snapshot.getTypes(), PersistedAnalysisType.Reader::getId);
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
        String typeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(type);
        Integer typeId = typeDescriptorToBaseLayerId.get(typeDescriptor);
        if (typeId == null) {
            /* The type was not reachable in the base image */
            return -1;
        }
        PersistedAnalysisType.Reader typeData = findType(typeId);
        int id = typeData.getId();
        int hubIdentityHashCode = typeData.getHubIdentityHashCode();
        typeToHubIdentityHashCode.put(id, hubIdentityHashCode);
        return id;
    }

    public void initializeBaseLayerType(AnalysisType type) {
        int id = getBaseLayerTypeId(type);
        if (id == -1) {
            return;
        }
        PersistedAnalysisType.Reader td = findType(id);
        registerFlag(td.getIsInstantiated(), true, () -> type.registerAsInstantiated(PERSISTED));
        registerFlag(td.getIsUnsafeAllocated(), true, () -> type.registerAsUnsafeAllocated(PERSISTED));
        registerFlag(td.getIsReachable(), true, () -> type.registerAsReachable(PERSISTED));
    }

    /**
     * Tries to look up the base layer type in the current VM. Some types cannot be looked up by
     * name (for example $$Lambda types), so this method can return null.
     */
    public Class<?> lookupBaseLayerTypeInHostVM(String type) {
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
            clazz = lookupClass(true, componentType);
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

    private void loadMethod(PersistedAnalysisMethod.Reader methodData) {
        int mid = methodData.getId();

        if (imageLayerLoaderHelper.loadMethod(methodData, mid)) {
            return;
        }

        int tid = methodData.getDeclaringTypeId();
        AnalysisType type = getAnalysisTypeForBaseLayerId(tid);

        AnalysisType[] parameterTypes = streamInts(methodData.getArgumentTypeIds()).mapToObj(this::getAnalysisTypeForBaseLayerId).toArray(AnalysisType[]::new);

        AnalysisType returnType = getAnalysisTypeForBaseLayerId(methodData.getReturnTypeId());

        String name = methodData.getName().toString();
        if (methodData.hasClassName()) {
            String className = methodData.getClassName().toString();

            Executable method = null;
            Class<?> clazz = lookupBaseLayerTypeInHostVM(className);
            if (clazz != null) {
                Class<?>[] argumentClasses = streamStrings(methodData.getArgumentClassNames()).map(this::lookupBaseLayerTypeInHostVM).toArray(Class[]::new);
                method = lookupMethodByReflection(name, clazz, argumentClasses);
            }

            if (method != null) {
                metaAccess.lookupJavaMethod(method);
                if (methods.containsKey(mid)) {
                    return;
                }
            }
        }

        Class<?>[] argumentClasses = Arrays.stream(parameterTypes).map(AnalysisType::getJavaClass).toArray(Class[]::new);
        Executable method = lookupMethodByReflection(name, type.getJavaClass(), argumentClasses);

        if (method != null) {
            metaAccess.lookupJavaMethod(method);
            if (methods.containsKey(mid)) {
                return;
            }
        }

        ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromList(Arrays.stream(parameterTypes).toList(), returnType);

        if (name.equals(CONSTRUCTOR_NAME)) {
            type.findConstructor(signature);
        } else if (name.equals(CLASS_INIT_NAME)) {
            type.getClassInitializer();
        } else {
            type.findMethod(name, signature);
        }

        if (!methods.containsKey(mid)) {
            createBaseLayerMethod(methodData, mid, name, parameterTypes, returnType);
        }
    }

    public static Executable lookupMethodByReflection(String name, Class<?> clazz, Class<?>[] argumentClasses) {
        try {
            Executable method;
            if (name.equals(CONSTRUCTOR_NAME)) {
                method = ReflectionUtil.lookupConstructor(true, clazz, argumentClasses);
            } else {
                method = ReflectionUtil.lookupMethod(true, clazz, name, argumentClasses);
            }
            return method;
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private void createBaseLayerMethod(PersistedAnalysisMethod.Reader md, int mid, String name, AnalysisType[] parameterTypes, AnalysisType returnType) {
        AnalysisType type = getAnalysisTypeForBaseLayerId(md.getDeclaringTypeId());
        ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromArray(parameterTypes, returnType);
        byte[] code = md.hasCode() ? md.getCode().toArray() : null;
        IntrinsicMethod methodHandleIntrinsic = !md.hasMethodHandleIntrinsicName() ? null
                        : IntrinsicMethod.valueOf(md.getMethodHandleIntrinsicName().toString());
        Annotation[] annotations = getAnnotations(md.getAnnotationList());

        baseLayerMethods.computeIfAbsent(mid,
                        methodId -> new BaseLayerMethod(mid, type, name, md.getIsVarArgs(), signature, md.getCanBeStaticallyBound(), md.getIsConstructor(),
                                        md.getModifiers(), md.getIsSynthetic(), code, md.getCodeSize(), methodHandleIntrinsic, annotations));
        BaseLayerMethod baseLayerMethod = baseLayerMethods.get(mid);

        universe.lookup(baseLayerMethod);
    }

    public AnalysisMethod getAnalysisMethodForBaseLayerId(int mid) {
        if (!methods.containsKey(mid)) {
            PersistedAnalysisMethod.Reader methodData = findMethod(mid);
            loadMethod(methodData);
        }

        AnalysisMethod analysisMethod = methods.get(mid);
        AnalysisError.guarantee(analysisMethod != null, "Method with id %d was not correctly loaded.", mid);
        return analysisMethod;
    }

    private PersistedAnalysisMethod.Reader findMethod(int mid) {
        return binarySearchUnique(mid, snapshot.getMethods(), PersistedAnalysisMethod.Reader::getId);
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
        if (methods.containsKey(analysisMethod.getId())) {
            return -1;
        }
        PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
        if (methodData == null) {
            /* The method was not reachable in the base image */
            return -1;
        }
        return methodData.getId();
    }

    public void addBaseLayerMethod(AnalysisMethod analysisMethod) {
        methods.putIfAbsent(analysisMethod.getId(), analysisMethod);
    }

    public void initializeBaseLayerMethod(AnalysisMethod analysisMethod) {
        initializeBaseLayerMethod(analysisMethod, getMethodData(analysisMethod));
    }

    protected void initializeBaseLayerMethod(AnalysisMethod analysisMethod, PersistedAnalysisMethod.Reader md) {
        registerFlag(md.getIsVirtualRootMethod(), true, () -> analysisMethod.registerAsVirtualRootMethod(PERSISTED));
        registerFlag(md.getIsDirectRootMethod(), true, () -> analysisMethod.registerAsDirectRootMethod(PERSISTED));
        registerFlag(md.getIsInvoked(), true, () -> analysisMethod.registerAsInvoked(PERSISTED));
        registerFlag(md.getIsImplementationInvoked(), true, () -> analysisMethod.registerAsImplementationInvoked(PERSISTED));
        registerFlag(md.getIsIntrinsicMethod(), true, () -> analysisMethod.registerAsIntrinsicMethod(PERSISTED));
    }

    /**
     * Currently we save analysis parsed graphs for methods considered
     * {@link AnalysisMethod#isReachable}. See {@link ImageLayerWriter#persistMethodGraphs} for
     * implementation.
     */
    public boolean hasAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        return getMethodData(analysisMethod).hasAnalysisGraphLocation();
    }

    public AnalysisParsedGraph getAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
        byte[] encodedAnalyzedGraph = readEncodedGraph(methodData.getAnalysisGraphLocation().toString());
        boolean intrinsic = methodData.getAnalysisGraphIsIntrinsic();
        EncodedGraph analyzedGraph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphDecoder(this, analysisMethod, universe.getSnippetReflection()), encodedAnalyzedGraph);
        if (hasStrengthenedGraph(analysisMethod)) {
            throw AnalysisError.shouldNotReachHere("Strengthened graphs are not supported until late loading is implemented.");
        }
        afterGraphDecodeHook(analyzedGraph);
        return new AnalysisParsedGraph(analyzedGraph, intrinsic);
    }

    private byte[] readEncodedGraph(String location) {
        int closingBracketAt = location.length() - 1;
        AnalysisError.guarantee(location.charAt(0) == '@' && location.charAt(closingBracketAt) == ']', "Location must start with '@' and end with ']': %s", location);
        int openingBracketAt = location.indexOf('[', 1, closingBracketAt);
        AnalysisError.guarantee(openingBracketAt < closingBracketAt, "Location does not contain '[' at expected location: %s", location);
        long offset;
        long nbytes;
        try {
            offset = Long.parseUnsignedLong(location.substring(1, openingBracketAt));
            nbytes = Long.parseUnsignedLong(location.substring(openingBracketAt + 1, closingBracketAt));
        } catch (NumberFormatException e) {
            throw AnalysisError.shouldNotReachHere("Location contains invalid positive integer(s): " + location);
        }
        ByteBuffer bb = ByteBuffer.allocate(NumUtil.safeToInt(nbytes));
        try {
            graphsChannel.read(bb, offset);
        } catch (IOException e) {
            throw AnalysisError.shouldNotReachHere("Failed reading a graph from location: " + location, e);
        }
        return bb.array();
    }

    public boolean hasStrengthenedGraph(AnalysisMethod analysisMethod) {
        return getMethodData(analysisMethod).hasStrengthenedGraphLocation();
    }

    public void setStrengthenedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
        byte[] encodedAnalyzedGraph = readEncodedGraph(methodData.getStrengthenedGraphLocation().toString());
        EncodedGraph analyzedGraph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphDecoder(this, analysisMethod, universe.getSnippetReflection()), encodedAnalyzedGraph);
        afterGraphDecodeHook(analyzedGraph);
        analysisMethod.setAnalyzedGraph(analyzedGraph);
    }

    @SuppressWarnings("unused")
    protected void afterGraphDecodeHook(EncodedGraph encodedGraph) {

    }

    protected static int getId(String line) {
        return Integer.parseInt(line.split(" = ")[1]);
    }

    private PersistedAnalysisMethod.Reader getMethodData(AnalysisMethod analysisMethod) {
        if (analysisMethod.getWrapped() instanceof BaseLayerMethod m) {
            return findMethod(m.getBaseLayerId());
        }
        String descriptor = imageLayerSnapshotUtil.getMethodDescriptor(analysisMethod);
        Integer id = methodDescriptorToBaseLayerId.get(descriptor);
        return (id != null) ? findMethod(id) : null;
    }

    private void loadField(PersistedAnalysisField.Reader fieldData) {
        AnalysisType declaringClass = getAnalysisTypeForBaseLayerId(fieldData.getDeclaringTypeId());
        String className = fieldData.hasClassName() ? fieldData.getClassName().toString() : null;
        int id = fieldData.getId();

        Class<?> clazz = className != null ? lookupBaseLayerTypeInHostVM(className) : declaringClass.getJavaClass();
        if (clazz == null) {
            clazz = declaringClass.getJavaClass();
        }

        Field field;
        try {
            field = ReflectionUtil.lookupField(true, clazz, fieldData.getName().toString());
        } catch (Throwable e) {
            field = null;
        }

        if (field == null && !(declaringClass.getWrapped() instanceof BaseLayerType)) {
            if (fieldData.getIsStatic()) {
                declaringClass.getStaticFields();
            } else {
                declaringClass.getInstanceFields(true);
            }

            if (fields.containsKey(id)) {
                return;
            }
        }

        if (field == null) {
            AnalysisType type = getAnalysisTypeForBaseLayerId(fieldData.getTypeId());
            BaseLayerField baseLayerField = getBaseLayerField(fieldData, id, declaringClass.getWrapped(), type.getWrapped());
            universe.lookup(baseLayerField);
        } else {
            metaAccess.lookupJavaField(field);
        }
    }

    private BaseLayerField getBaseLayerField(int id) {
        PersistedAnalysisField.Reader fieldData = findField(id);

        BaseLayerType declaringClass = getBaseLayerType(fieldData.getDeclaringTypeId());
        ResolvedJavaType type = getResolvedJavaTypeForBaseLayerId(fieldData.getTypeId());

        return getBaseLayerField(fieldData, id, declaringClass, type);
    }

    private BaseLayerField getBaseLayerField(PersistedAnalysisField.Reader fd, int id, ResolvedJavaType declaringClass, ResolvedJavaType type) {
        return baseLayerFields.computeIfAbsent(id,
                        fid -> new BaseLayerField(id, fd.getName().toString(), declaringClass, type, fd.getIsInternal(),
                                        fd.getIsSynthetic(), fd.getModifiers(), getAnnotations(fd.getAnnotationList())));
    }

    public AnalysisField getAnalysisFieldForBaseLayerId(int fid) {
        if (!fields.containsKey(fid)) {
            loadField(findField(fid));
        }

        AnalysisField analysisField = fields.get(fid);
        AnalysisError.guarantee(analysisField != null, "Field with id %d was not correctly loaded.", fid);
        return analysisField;
    }

    private PersistedAnalysisField.Reader findField(int fid) {
        return binarySearchUnique(fid, snapshot.getFields(), PersistedAnalysisField.Reader::getId);
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
        PersistedAnalysisField.Reader fieldData = getFieldData(analysisField);
        if (fieldData == null) {
            /* The field was not reachable in the base image */
            return -1;
        }
        return fieldData.getId();
    }

    public void addBaseLayerField(AnalysisField analysisField) {
        fields.putIfAbsent(analysisField.getId(), analysisField);
    }

    public void initializeBaseLayerField(AnalysisField analysisField) {
        PersistedAnalysisField.Reader fieldData = getFieldData(analysisField);

        assert fieldData != null : "The field should be in the base layer";
        int location = fieldData.getLocation();
        if (location != 0) {
            fieldLocations.put(analysisField, location);
        }

        boolean isAccessed = fieldData.getIsAccessed();
        boolean isRead = fieldData.getIsRead();
        if (!analysisField.isStatic() && (isAccessed || isRead)) {
            analysisField.getDeclaringClass().getInstanceFields(true);
        }
        registerFlag(isAccessed, true, () -> analysisField.registerAsAccessed(PERSISTED));
        registerFlag(isRead, true, () -> analysisField.registerAsRead(PERSISTED));
        registerFlag(fieldData.getIsWritten(), true, () -> analysisField.registerAsWritten(PERSISTED));
        registerFlag(fieldData.getIsFolded(), true, () -> analysisField.registerAsFolded(PERSISTED));
    }

    protected PersistedAnalysisField.Reader getFieldData(AnalysisField analysisField) {
        if (analysisField.wrapped instanceof BaseLayerField baseLayerField) {
            return findField(baseLayerField.getBaseLayerId());
        }
        String declTypeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(analysisField.getDeclaringClass());
        Integer declTypeId = typeDescriptorToBaseLayerId.get(declTypeDescriptor);
        if (declTypeId == null) {
            return null;
        }
        PersistedAnalysisType.Reader typeData = findType(declTypeId);
        PrimitiveList.Int.Reader fieldIds;
        if (analysisField.isStatic()) {
            fieldIds = typeData.getStaticFieldIds();
        } else {
            fieldIds = typeData.getInstanceFieldIds();
        }
        for (int i = 0; i < fieldIds.size(); i++) {
            PersistedAnalysisField.Reader fieldData = findField(fieldIds.get(i));
            if (fieldData != null && analysisField.getName().equals(fieldData.getName().toString())) {
                return fieldData;
            }
        }
        return null;
    }

    private void registerFlag(boolean flag, boolean post, Runnable runnable) {
        if (flag) {
            if (universe.getBigbang() != null) {
                if (post) {
                    universe.getBigbang().postTask(debug -> runnable.run());
                } else {
                    runnable.run();
                }
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

    /**
     * Get the {@link ImageHeapConstant} representation for a specific base layer constant id. If
     * known, the parentReachableHostedObject will point to the corresponding constant in the
     * underlying host VM, found by querying the parent object that made this constant reachable
     * (see {@link ImageLayerLoader#getReachableHostedValue(ImageHeapConstant, int)}).
     */
    protected ImageHeapConstant getOrCreateConstant(int id, JavaConstant parentReachableHostedObjectCandidate) {
        if (constants.containsKey(id)) {
            return constants.get(id);
        }
        PersistedConstant.Reader baseLayerConstant = findConstant(id);
        if (baseLayerConstant == null) {
            throw GraalError.shouldNotReachHere("The constant was not reachable in the base image");
        }

        AnalysisType type = getAnalysisTypeForBaseLayerId(baseLayerConstant.getTypeId());

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
            injectIdentityHashCode(hostedValuesProvider.asObject(Object.class, parentReachableHostedObject), identityHashCode);
        }
        switch (baseLayerConstant.which()) {
            case OBJECT -> {
                switch (baseLayerConstant.getObject().which()) {
                    case INSTANCE -> {
                        StructList.Reader<ConstantReference.Reader> instanceData = baseLayerConstant.getObject().getData();
                        JavaConstant foundHostedObject = lookupHostedObject(baseLayerConstant, type);
                        if (foundHostedObject != null && parentReachableHostedObject != null) {
                            Object foundObject = hostedValuesProvider.asObject(Object.class, foundHostedObject);
                            Object reachableObject = hostedValuesProvider.asObject(Object.class, parentReachableHostedObject);
                            guarantee(foundObject == reachableObject, "Found discrepancy between recipe-found hosted value %s and parent-reachable hosted value %s.", foundObject,
                                            reachableObject);
                        }

                        addBaseLayerObject(id, objectOffset, () -> {
                            ImageHeapInstance imageHeapInstance = new ImageHeapInstance(type, foundHostedObject == null ? parentReachableHostedObject : foundHostedObject, identityHashCode, id);
                            if (instanceData != null) {
                                Object[] fieldValues = getReferencedValues(imageHeapInstance, instanceData, imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess));
                                imageHeapInstance.setFieldValues(fieldValues);
                            }
                            return imageHeapInstance;
                        });
                    }
                    case OBJECT_ARRAY -> {
                        StructList.Reader<ConstantReference.Reader> arrayData = baseLayerConstant.getObject().getData();
                        addBaseLayerObject(id, objectOffset, () -> {
                            ImageHeapObjectArray imageHeapObjectArray = new ImageHeapObjectArray(type, null, arrayData.size(), identityHashCode, id);
                            Object[] elementsValues = getReferencedValues(imageHeapObjectArray, arrayData, Set.of());
                            imageHeapObjectArray.setElementValues(elementsValues);
                            return imageHeapObjectArray;
                        });
                    }
                    default -> throw GraalError.shouldNotReachHere("Unknown object  type: " + baseLayerConstant.getObject().which());
                }
            }
            case PRIMITIVE_DATA -> {
                Object array = getArray(baseLayerConstant.getPrimitiveData());
                addBaseLayerObject(id, objectOffset, () -> new ImageHeapPrimitiveArray(type, null, array, Array.getLength(array), identityHashCode, id));
            }
            case RELOCATABLE -> {
                String key = baseLayerConstant.getRelocatable().getKey().toString();
                addBaseLayerObject(id, objectOffset, () -> ImageHeapRelocatableConstant.create(type, key, id));
            }
            default -> throw GraalError.shouldNotReachHere("Unknown constant type: " + baseLayerConstant.which());
        }

        return constants.get(id);
    }

    /**
     * Look up an object in current hosted VM based on the recipe serialized from the base layer.
     */
    protected JavaConstant lookupHostedObject(PersistedConstant.Reader baseLayerConstant, AnalysisType analysisType) {
        if (!baseLayerConstant.getIsSimulated()) {
            Class<?> clazz = analysisType.getJavaClass();
            return lookupHostedObject(baseLayerConstant, clazz);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected JavaConstant lookupHostedObject(PersistedConstant.Reader baseLayerConstant, Class<?> clazz) {
        if (!baseLayerConstant.isObject() || baseLayerConstant.getObject().getRelinking().isNotRelinked()) {
            return null;
        }
        PersistedConstant.Object.Relinking.Reader relinking = baseLayerConstant.getObject().getRelinking();
        if (clazz.equals(String.class)) {
            assert relinking.isStringConstant();
            StringConstant.Reader stringConstant = relinking.getStringConstant();
            if (stringConstant.hasValue()) {
                String value = stringConstant.getValue().toString();
                Object object = value.intern();
                return hostedValuesProvider.forObject(object);
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            assert relinking.isEnumConstant();
            EnumConstant.Reader enumConstant = relinking.getEnumConstant();
            Enum<?> enumValue = getEnumValue(enumConstant.getEnumClass(), enumConstant.getEnumName());
            return hostedValuesProvider.forObject(enumValue);
        }
        return null;
    }

    @SuppressWarnings("unused")
    protected void injectIdentityHashCode(Object object, Integer identityHashCode) {
        /* The hash code can only be injected in the SVM context. */
    }

    private static Object getArray(PersistedConstant.PrimitiveData.Reader reader) {
        return switch (reader.which()) {
            case Z -> getBooleans(reader.getZ());
            case B -> toArray(reader.getB(), r -> IntStream.range(0, r.size()).collect(() -> new byte[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case S -> toArray(reader.getS(), r -> IntStream.range(0, r.size()).collect(() -> new short[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case C -> toArray(reader.getC(), r -> IntStream.range(0, r.size()).collect(() -> new char[r.size()], (a, i) -> a[i] = (char) r.get(i), combineUnsupported()));
            case I -> toArray(reader.getI(), r -> IntStream.range(0, r.size()).collect(() -> new int[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case F -> toArray(reader.getF(), r -> IntStream.range(0, r.size()).collect(() -> new float[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case J -> toArray(reader.getJ(), r -> IntStream.range(0, r.size()).collect(() -> new long[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case D -> toArray(reader.getD(), r -> IntStream.range(0, r.size()).collect(() -> new double[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported()));
            case _NOT_IN_SCHEMA -> throw new IllegalArgumentException("Unsupported kind: " + reader.which());
        };
    }

    protected static boolean[] getBooleans(PrimitiveList.Boolean.Reader r) {
        return IntStream.range(0, r.size()).collect(() -> new boolean[r.size()], (a, i) -> a[i] = r.get(i), combineUnsupported());
    }

    /** Enables concise one-liners without explicit types in {@link #getArray}. */
    private static <T extends ListReader, A> A toArray(T reader, Function<T, A> fun) {
        return fun.apply(reader);
    }

    private static <A> BiConsumer<A, A> combineUnsupported() {
        return (u, v) -> {
            throw new UnsupportedOperationException("Combining partial results not supported, streams must be sequential");
        };
    }

    private Object[] getReferencedValues(ImageHeapConstant parentConstant, StructList.Reader<ConstantReference.Reader> data, Set<Integer> positionsToRelink) {
        Object[] values = new Object[data.size()];
        for (int position = 0; position < data.size(); ++position) {
            ConstantReference.Reader constantData = data.get(position);
            if (delegateProcessing(constantData, values, position)) {
                continue;
            }
            values[position] = switch (constantData.which()) {
                case OBJECT_CONSTANT -> {
                    int constantId = constantData.getObjectConstant().getConstantId();
                    boolean relink = positionsToRelink.contains(position);
                    int finalPosition = position;
                    yield new AnalysisFuture<>(() -> {
                        ensureHubInitialized(parentConstant);

                        JavaConstant hostedConstant = relink ? getReachableHostedValue(parentConstant, finalPosition) : null;
                        ImageHeapConstant baseLayerConstant = getOrCreateConstant(constantId, hostedConstant);
                        values[finalPosition] = baseLayerConstant;

                        ensureHubInitialized(baseLayerConstant);

                        if (hostedConstant != null) {
                            addBaseLayerValueToImageHeap(baseLayerConstant, parentConstant, finalPosition);
                        }

                        return baseLayerConstant;
                    });
                }
                case NULL_POINTER -> JavaConstant.NULL_POINTER;
                case NOT_MATERIALIZED ->
                    /*
                     * This constant is a field value or an object value that was not materialized
                     * in the base image.
                     */
                    new AnalysisFuture<>(() -> {
                        throw AnalysisError.shouldNotReachHere("This constant was not materialized in the base image.");
                    });
                case PRIMITIVE_VALUE -> {
                    ConstantReference.PrimitiveValue.Reader pv = constantData.getPrimitiveValue();
                    yield JavaConstant.forPrimitive((char) pv.getTypeChar(), pv.getRawValue());
                }
                default -> throw GraalError.shouldNotReachHere("Unexpected constant reference: " + constantData.which());
            };
        }
        return values;
    }

    /**
     * Hook for subclasses to do their own processing.
     */
    @SuppressWarnings("unused")
    protected boolean delegateProcessing(ConstantReference.Reader constantRef, Object[] values, int i) {
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

    private static AnalysisField getFieldFromIndex(ImageHeapInstance instance, int i) {
        return (AnalysisField) instance.getType().getInstanceFields(true)[i];
    }

    private JavaConstant getHostedElementValue(ImageHeapObjectArray array, int idx) {
        JavaConstant hostedArray = array.getHostedObject();
        JavaConstant rawElementValue = null;
        if (hostedArray != null) {
            rawElementValue = hostedValuesProvider.readArrayElement(hostedArray, idx);
        }
        return rawElementValue;
    }

    private JavaConstant getHostedFieldValue(ImageHeapInstance instance, AnalysisField field) {
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

    public void addBaseLayerValueToImageHeap(ImageHeapConstant constant, ImageHeapConstant parentConstant, int i) {
        if (parentConstant instanceof ImageHeapInstance imageHeapInstance) {
            universe.getHeapScanner().registerBaseLayerValue(constant, getFieldFromIndex(imageHeapInstance, i));
        } else if (parentConstant instanceof ImageHeapObjectArray) {
            universe.getHeapScanner().registerBaseLayerValue(constant, i);
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

    private void addBaseLayerObject(int id, long objectOffset, Supplier<ImageHeapConstant> imageHeapConstantSupplier) {
        constants.computeIfAbsent(id, key -> {
            ImageHeapConstant heapObj = imageHeapConstantSupplier.get();
            heapObj.markInBaseLayer();
            /*
             * Packages are normally rescanned when the DynamicHub is initialized. However, since
             * they are not relinked, the packages from the base layer will never be marked as
             * reachable without doing so manually.
             */
            if (heapObj.getType().getJavaClass().equals(Package.class)) {
                universe.getHeapScanner().doScan(heapObj);
            }
            if (objectOffset != -1) {
                objectOffsets.put(heapObj.constantData.id, objectOffset);
            }
            return heapObj;
        });
    }

    @SuppressWarnings("unchecked")
    protected final Enum<?> getEnumValue(Text.Reader className, Text.Reader name) {
        Class<?> enumClass = lookupClass(false, className.toString());
        /* asSubclass produces an "unchecked" warning */
        return Enum.valueOf(enumClass.asSubclass(Enum.class), name.toString());
    }

    public Class<?> lookupClass(boolean optional, String className) {
        return ReflectionUtil.lookupClass(optional, className);
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
        return getOrCreateConstant(id, null);
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
        return getOrCreateConstant(snapshot.getStaticPrimitiveFieldsConstantId());
    }

    public ImageHeapConstant getBaseLayerStaticObjectFields() {
        return getOrCreateConstant(snapshot.getStaticObjectFieldsConstantId());
    }

    public long getImageHeapSize() {
        return snapshot.getImageHeapSize();
    }

    public boolean hasDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.containsKey(tid);
    }

    public int getDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.get(tid);
    }
}
