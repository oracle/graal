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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.graal.pointsto.util.AnalysisError.guarantee;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.CLASS_INIT_NAME;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.capnproto.ListReader;
import org.capnproto.PrimitiveList;
import org.capnproto.StructList;
import org.capnproto.StructReader;
import org.capnproto.Text;
import org.capnproto.TextList;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.ImageLayerLoader;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
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
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.AnnotationValue;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod.WrappedMember;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType.SerializationGenerated;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.EnumConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.StringConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveValue;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.serialize.SerializationFeature;
import com.oracle.svm.hosted.util.IdentityHashCodeUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationParser;

public class SVMImageLayerLoader extends ImageLayerLoader {
    private final Field dynamicHubCompanionField;
    private final boolean useSharedLayerGraphs;
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;
    private final SharedLayerSnapshot.Reader snapshot;
    private final FileChannel graphsChannel;
    private final ClassInitializationSupport classInitializationSupport;

    private HostedUniverse hostedUniverse;

    protected final Map<Integer, AnalysisType> types = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisField> fields = new ConcurrentHashMap<>();
    protected final Map<Integer, ImageHeapConstant> constants = new ConcurrentHashMap<>();

    private final Map<Integer, BaseLayerType> baseLayerTypes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> typeToHubIdentityHashCode = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerMethod> baseLayerMethods = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerField> baseLayerFields = new ConcurrentHashMap<>();

    protected final Set<DebugContextRunnable> futureBigbangTasks = ConcurrentHashMap.newKeySet();
    protected final Map<Integer, Integer> typeToConstant = new ConcurrentHashMap<>();
    protected final Map<String, Integer> stringToConstant = new ConcurrentHashMap<>();
    protected final Map<Enum<?>, Integer> enumToConstant = new ConcurrentHashMap<>();
    protected final Map<Integer, Long> objectOffsets = new ConcurrentHashMap<>();
    protected final Map<AnalysisField, Integer> fieldLocations = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> capturingClasses = new ConcurrentHashMap<>();
    private final Map<ResolvedJavaMethod, Boolean> methodHandleCallers = new ConcurrentHashMap<>();

    /** Map from {@link SVMImageLayerSnapshotUtil#getTypeDescriptor} to base layer type ids. */
    private final Map<String, Integer> typeDescriptorToBaseLayerId = new HashMap<>();
    /** Map from {@link SVMImageLayerSnapshotUtil#getMethodDescriptor} to base layer method ids. */
    private final Map<String, Integer> methodDescriptorToBaseLayerId = new HashMap<>();

    protected AnalysisUniverse universe;
    protected AnalysisMetaAccess metaAccess;
    protected HostedValuesProvider hostedValuesProvider;

    public SVMImageLayerLoader(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, HostedImageLayerBuildingSupport imageLayerBuildingSupport, SharedLayerSnapshot.Reader snapshot,
                    FileChannel graphChannel, boolean useSharedLayerGraphs) {
        this.dynamicHubCompanionField = ReflectionUtil.lookupField(DynamicHub.class, "companion");
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.imageLayerBuildingSupport = imageLayerBuildingSupport;
        this.snapshot = snapshot;
        this.graphsChannel = graphChannel;
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        classInitializationSupport = ClassInitializationSupport.singleton();
    }

    public AnalysisUniverse getUniverse() {
        return universe;
    }

    public void setUniverse(AnalysisUniverse universe) {
        this.universe = universe;
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

    public HostedUniverse getHostedUniverse() {
        return hostedUniverse;
    }

    public void setHostedUniverse(HostedUniverse hostedUniverse) {
        this.hostedUniverse = hostedUniverse;
    }

    public HostedImageLayerBuildingSupport getImageLayerBuildingSupport() {
        return imageLayerBuildingSupport;
    }

    public void loadLayerAnalysis() {
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

    /**
     * The non-transformed field values are prepared earlier because some constants can be loaded
     * very early.
     */
    public void relinkNonTransformedStaticFinalFieldValues() {
        relinkStaticFinalFieldValues(false);
    }

    /**
     * The transformed field values need to be prepared after all the transformer are installed.
     */
    public void relinkTransformedStaticFinalFieldValues() {
        relinkStaticFinalFieldValues(true);
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
    private void relinkStaticFinalFieldValues(boolean isLateLoading) {
        IntStream.range(0, snapshot.getConstants().size()).parallel().forEach(i -> {
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
                    universe.getHeapScanner().registerBaseLayerValue(constant, PERSISTED);
                }
            }
        });
    }

    private static IntStream streamInts(PrimitiveList.Int.Reader reader) {
        return IntStream.range(0, reader.size()).map(reader::get);
    }

    public static Stream<String> streamStrings(TextList.Reader reader) {
        return IntStream.range(0, reader.size()).mapToObj(i -> reader.get(i).toString());
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

    private void prepareConstantRelinking(PersistedConstant.Reader constantData, int identityHashCode, int id) {
        if (!constantData.isObject()) {
            return;
        }

        Relinking.Reader relinking = constantData.getObject().getRelinking();
        if (relinking.isClassConstant()) {
            int typeId = relinking.getClassConstant().getTypeId();
            typeToConstant.put(typeId, id);
        } else if (relinking.isStringConstant()) {
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

    public void cleanupAfterCompilation() {
        if (graphsChannel != null) {
            try {
                graphsChannel.close();
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere(e);
            }
        }
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

    private PersistedAnalysisType.Reader findType(int tid) {
        return binarySearchUnique(tid, snapshot.getTypes(), PersistedAnalysisType.Reader::getId);
    }

    private void loadType(PersistedAnalysisType.Reader typeData) {
        int tid = typeData.getId();

        if (delegateLoadType(typeData)) {
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

    @SuppressWarnings("deprecation")
    protected boolean delegateLoadType(PersistedAnalysisType.Reader typeData) {
        WrappedType.Reader wrappedType = typeData.getWrappedType();
        if (wrappedType.isNone()) {
            return false;
        }
        if (wrappedType.isSerializationGenerated()) {
            SerializationGenerated.Reader sg = wrappedType.getSerializationGenerated();
            String rawDeclaringClassName = sg.getRawDeclaringClass().toString();
            String rawTargetConstructorClassName = sg.getRawTargetConstructor().toString();
            Class<?> rawDeclaringClass = imageLayerBuildingSupport.lookupClass(false, rawDeclaringClassName);
            Class<?> rawTargetConstructorClass = imageLayerBuildingSupport.lookupClass(false, rawTargetConstructorClassName);
            SerializationSupport serializationSupport = SerializationSupport.singleton();
            Constructor<?> rawTargetConstructor = ReflectionUtil.lookupConstructor(rawTargetConstructorClass);
            Constructor<?> constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(rawDeclaringClass, rawTargetConstructor);
            serializationSupport.addConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass, SerializationFeature.getConstructorAccessor(constructor));
            Class<?> constructorAccessor = serializationSupport.getSerializationConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass).getClass();
            metaAccess.lookupJavaType(constructorAccessor);
            return true;
        } else if (wrappedType.isLambda()) {
            String capturingClassName = wrappedType.getLambda().getCapturingClass().toString();
            Class<?> capturingClass = imageLayerBuildingSupport.lookupClass(false, capturingClassName);
            loadLambdaTypes(capturingClass);
            return types.containsKey(typeData.getId());
        } else if (wrappedType.isProxyType()) {
            Class<?>[] interfaces = Stream.of(typeData.getInterfaces()).flatMapToInt(r -> IntStream.range(0, r.size()).map(r::get))
                            .mapToObj(i -> getAnalysisTypeForBaseLayerId(i).getJavaClass()).toArray(Class<?>[]::new);
            /* GR-59854: The deprecation warning comes from this call to Proxy.getProxyClass. */
            Class<?> proxy = Proxy.getProxyClass(interfaces[0].getClassLoader(), interfaces);
            metaAccess.lookupJavaType(proxy);
            return true;
        }
        return false;
    }

    /**
     * Load all lambda types of the given capturing class. Each method of the capturing class is
     * parsed (see {@link LambdaParser#createMethodGraph(ResolvedJavaMethod, OptionValues)}). The
     * lambda types can then be found in the constant nodes of the graphs.
     */
    private void loadLambdaTypes(Class<?> capturingClass) {
        capturingClasses.computeIfAbsent(capturingClass, key -> {
            LambdaParser.allExecutablesDeclaredInClass(universe.getBigbang().getMetaAccess().lookupJavaType(capturingClass))
                            .filter(m -> m.getCode() != null)
                            .forEach(m -> loadLambdaTypes(((AnalysisMethod) m).getWrapped(), universe.getBigbang()));
            return true;
        });
    }

    private static void loadLambdaTypes(ResolvedJavaMethod m, BigBang bigBang) {
        StructuredGraph graph = getMethodGraph(m, bigBang);
        if (graph != null) {
            NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);

            for (ConstantNode cNode : constantNodes) {
                Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);

                if (lambdaClass != null) {
                    bigBang.getMetaAccess().lookupJavaType(lambdaClass);
                }
            }
        }
    }

    private void loadMethodHandleTargets(ResolvedJavaMethod m, BigBang bigBang) {
        methodHandleCallers.computeIfAbsent(m, method -> {
            StructuredGraph graph = getMethodGraph(m, bigBang);
            if (graph != null) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof MethodHandleNode methodHandleNode) {
                        bigBang.getUniverse().lookup(methodHandleNode.getTargetMethod());
                    }
                }
            }
            return true;
        });
    }

    private static StructuredGraph getMethodGraph(ResolvedJavaMethod m, BigBang bigBang) {
        if (m instanceof BaseLayerMethod) {
            return null;
        }
        StructuredGraph graph;
        try {
            graph = createMethodGraph(m, bigBang.getOptions());
        } catch (NoClassDefFoundError | BytecodeParser.BytecodeParserError e) {
            /* Skip the method if it refers to a missing class */
            return null;
        }
        return graph;
    }

    private ResolvedJavaType getResolvedJavaTypeForBaseLayerId(int tid) {
        return (tid == 0) ? null : getAnalysisTypeForBaseLayerId(tid).getWrapped();
    }

    /**
     * Tries to look up the base layer type in the current VM. Some types cannot be looked up by
     * name (for example $$Lambda types), so this method can return null.
     */
    protected Class<?> lookupBaseLayerTypeInHostVM(String type) {
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
            clazz = imageLayerBuildingSupport.lookupClass(true, componentType);
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

    private Annotation[] getAnnotations(StructList.Reader<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Reader> reader) {
        return IntStream.range(0, reader.size()).mapToObj(reader::get).map(this::getAnnotation).toArray(Annotation[]::new);
    }

    private Annotation getAnnotation(SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Reader a) {
        String typeName = a.getTypeName().toString();
        Class<? extends Annotation> annotationType = lookupBaseLayerTypeInHostVM(typeName).asSubclass(Annotation.class);
        Map<String, Object> annotationValuesMap = new HashMap<>();
        a.getValues().forEach(v -> {
            Object value = getAnnotationValue(v);
            annotationValuesMap.put(v.getName().toString(), value);
        });
        return AnnotationParser.annotationForMap(annotationType, annotationValuesMap);
    }

    private Object getAnnotationValue(AnnotationValue.Reader v) {
        return switch (v.which()) {
            case STRING -> v.getString().toString();
            case ENUM -> getEnumValue(v.getEnum().getClassName(), v.getEnum().getName());
            case PRIMITIVE -> {
                var p = v.getPrimitive();
                long rawValue = p.getRawValue();
                char typeChar = (char) p.getTypeChar();
                yield switch (JavaKind.fromPrimitiveOrVoidTypeChar(typeChar)) {
                    case Boolean -> rawValue != 0;
                    case Byte -> (byte) rawValue;
                    case Char -> (char) rawValue;
                    case Short -> (short) rawValue;
                    case Int -> (int) rawValue;
                    case Long -> rawValue;
                    case Float -> Float.intBitsToFloat((int) rawValue);
                    case Double -> Double.longBitsToDouble(rawValue);
                    default -> throw AnalysisError.shouldNotReachHere("Unknown annotation value type: " + typeChar);
                };
            }
            case PRIMITIVE_ARRAY -> getArray(v.getPrimitiveArray());
            case CLASS_NAME -> imageLayerBuildingSupport.lookupClass(false, v.getClassName().toString());
            case ANNOTATION -> getAnnotation(v.getAnnotation());
            case MEMBERS -> {
                var m = v.getMembers();
                var mv = m.getMemberValues();
                Class<?> membersClass = imageLayerBuildingSupport.lookupClass(false, m.getClassName().toString());
                var array = Array.newInstance(membersClass, mv.size());
                for (int i = 0; i < mv.size(); ++i) {
                    Array.set(array, i, getAnnotationValue(mv.get(i)));
                }
                yield array;
            }
            case _NOT_IN_SCHEMA -> throw AnalysisError.shouldNotReachHere("Unknown annotation value kind: " + v.which());
        };
    }

    @Override
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
        PersistedAnalysisType.Reader typeData = findBaseLayerType(type);
        if (typeData == null) {
            /* The type was not reachable in the base image */
            return -1;
        }
        int id = typeData.getId();
        int hubIdentityHashCode = typeData.getHubIdentityHashCode();
        typeToHubIdentityHashCode.put(id, hubIdentityHashCode);
        return id;
    }

    protected PersistedAnalysisType.Reader findBaseLayerType(AnalysisType type) {
        assert !(type.getWrapped() instanceof BaseLayerType);
        String typeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(type);
        Integer typeId = typeDescriptorToBaseLayerId.get(typeDescriptor);
        if (typeId == null) {
            /* The type was not reachable in the base image */
            return null;
        }
        return findType(typeId);
    }

    @Override
    public void initializeBaseLayerType(AnalysisType type) {
        int id = getBaseLayerTypeId(type);
        if (id == -1) {
            return;
        }
        PersistedAnalysisType.Reader td = findType(id);
        registerFlag(td.getIsInstantiated(), debug -> type.registerAsInstantiated(PERSISTED));
        registerFlag(td.getIsUnsafeAllocated(), debug -> type.registerAsUnsafeAllocated(PERSISTED));
        registerFlag(td.getIsReachable(), debug -> type.registerAsReachable(PERSISTED));

        if (!td.getIsInstantiated() && td.getIsAnySubtypeInstantiated()) {
            var subTypesReader = td.getSubTypes();
            for (int i = 0; i < subTypesReader.size(); ++i) {
                int tid = subTypesReader.get(i);
                var subTypeReader = findType(tid);
                if (subTypeReader.getIsInstantiated()) {
                    registerFlag(true, debug -> getAnalysisTypeForBaseLayerId(subTypeReader.getId()));
                }
            }
        }
    }

    private void registerFlag(boolean flag, DebugContextRunnable task) {
        if (flag) {
            if (universe.getBigbang() != null) {
                universe.getBigbang().postTask(task);
            } else {
                futureBigbangTasks.add(task);
            }
        }
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

    private void loadMethod(PersistedAnalysisMethod.Reader methodData) {
        int mid = methodData.getId();

        if (delegateLoadMethod(methodData)) {
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

    protected boolean delegateLoadMethod(PersistedAnalysisMethod.Reader methodData) {
        WrappedMethod.Reader wrappedMethod = methodData.getWrappedMethod();
        if (wrappedMethod.isNone()) {
            return false;
        }
        if (wrappedMethod.isFactoryMethod()) {
            WrappedMethod.FactoryMethod.Reader fm = wrappedMethod.getFactoryMethod();
            AnalysisMethod analysisMethod = getAnalysisMethodForBaseLayerId(fm.getTargetConstructorId());
            if (analysisMethod.wrapped instanceof BaseLayerMethod) {
                return false;
            }
            AnalysisType instantiatedType = getAnalysisTypeForBaseLayerId(fm.getInstantiatedTypeId());
            FactoryMethodSupport.singleton().lookup(metaAccess, analysisMethod, instantiatedType, fm.getThrowAllocatedObject());
            return true;
        } else if (wrappedMethod.isCEntryPointCallStub()) {
            WrappedMethod.CEntryPointCallStub.Reader stub = wrappedMethod.getCEntryPointCallStub();
            boolean asNotPublished = stub.getNotPublished();
            AnalysisMethod originalMethod = getAnalysisMethodForBaseLayerId(stub.getOriginalMethodId());
            CEntryPointCallStubSupport.singleton().registerStubForMethod(originalMethod, () -> {
                CEntryPointData data = CEntryPointData.create(originalMethod);
                if (asNotPublished) {
                    data = data.copyWithPublishAs(CEntryPoint.Publish.NotPublished);
                }
                return data;
            });
            return true;
        } else if (wrappedMethod.isWrappedMember()) {
            WrappedMember.Reader wm = wrappedMethod.getWrappedMember();
            Executable member = getWrappedMember(wm);
            if (member == null) {
                return false;
            }
            if (wm.isReflectionExpandSignature()) {
                ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(member);
            } else if (wm.isJavaCallVariantWrapper()) {
                JNIAccessFeature.singleton().addMethod(member, (FeatureImpl.DuringAnalysisAccessImpl) universe.getConcurrentAnalysisAccess());
            }
            return true;
        } else if (wrappedMethod.isPolymorphicSignature()) {
            int id = methodData.getId();
            WrappedMethod.PolymorphicSignature.Reader ps = wrappedMethod.getPolymorphicSignature();
            var callers = ps.getCallers();
            for (int i = 0; i < callers.size(); ++i) {
                loadMethodHandleTargets(getAnalysisMethodForBaseLayerId(callers.get(i)).wrapped, universe.getBigbang());
                if (methods.containsKey(id)) {
                    return true;
                }
            }
            LogUtils.warning("The PolymorphicSignature method %s.%s could not get loaded", methodData.getClassName().toString(), methodData.getName().toString());
            return false;
        }
        return false;
    }

    private Executable getWrappedMember(WrappedMethod.WrappedMember.Reader memberData) {
        String className = memberData.getDeclaringClassName().toString();
        Class<?> declaringClass = imageLayerBuildingSupport.lookupClass(true, className);
        if (declaringClass == null) {
            return null;
        }
        String name = memberData.getName().toString();
        Class<?>[] parameters = StreamSupport.stream(memberData.getArgumentTypeNames().spliterator(), false).map(Text.Reader::toString)
                        .map(c -> imageLayerBuildingSupport.lookupClass(false, c)).toArray(Class<?>[]::new);
        return lookupMethodByReflection(name, declaringClass, parameters);
    }

    private static Executable lookupMethodByReflection(String name, Class<?> clazz, Class<?>[] argumentClasses) {
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
                        methodId -> new BaseLayerMethod(mid, type, name, md.getIsVarArgs(), md.getIsBridge(), signature, md.getCanBeStaticallyBound(), md.getIsConstructor(),
                                        md.getModifiers(), md.getIsSynthetic(), code, md.getCodeSize(), methodHandleIntrinsic, annotations));
        BaseLayerMethod baseLayerMethod = baseLayerMethods.get(mid);

        universe.lookup(baseLayerMethod);
    }

    @Override
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

    @Override
    public void addBaseLayerMethod(AnalysisMethod analysisMethod) {
        methods.putIfAbsent(analysisMethod.getId(), analysisMethod);

        PersistedAnalysisMethod.Reader md = getMethodData(analysisMethod);
        registerFlag(md.getIsVirtualRootMethod(), debug -> analysisMethod.registerAsVirtualRootMethod(PERSISTED));
        registerFlag(md.getIsDirectRootMethod(), debug -> analysisMethod.registerAsDirectRootMethod(PERSISTED));
        registerFlag(md.getIsInvoked(), debug -> analysisMethod.registerAsInvoked(PERSISTED));
        registerFlag(md.getIsImplementationInvoked(), debug -> analysisMethod.registerAsImplementationInvoked(PERSISTED));
        registerFlag(md.getIsIntrinsicMethod(), debug -> analysisMethod.registerAsIntrinsicMethod(PERSISTED));
    }

    private PersistedAnalysisMethod.Reader getMethodData(AnalysisMethod analysisMethod) {
        if (analysisMethod.getWrapped() instanceof BaseLayerMethod m) {
            return findMethod(m.getBaseLayerId());
        }
        String descriptor = imageLayerSnapshotUtil.getMethodDescriptor(analysisMethod);
        Integer id = methodDescriptorToBaseLayerId.get(descriptor);
        return (id != null) ? findMethod(id) : null;
    }

    /**
     * See
     * {@link SVMImageLayerWriter#persistAnalysisParsedGraph(AnalysisMethod, AnalysisParsedGraph)}
     * for implementation.
     */
    @Override
    public boolean hasAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        if (!useSharedLayerGraphs) {
            return false;
        }
        return hasGraph(analysisMethod, PersistedAnalysisMethod.Reader::hasAnalysisGraphLocation);
    }

    @Override
    public AnalysisParsedGraph getAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
        boolean intrinsic = methodData.getAnalysisGraphIsIntrinsic();
        EncodedGraph analyzedGraph = getEncodedGraph(analysisMethod, methodData.getAnalysisGraphLocation());
        return new AnalysisParsedGraph(analyzedGraph, intrinsic);
    }

    public boolean hasStrengthenedGraph(AnalysisMethod analysisMethod) {
        return hasGraph(analysisMethod, PersistedAnalysisMethod.Reader::hasStrengthenedGraphLocation);
    }

    public EncodedGraph getStrengthenedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
        return getEncodedGraph(analysisMethod, methodData.getStrengthenedGraphLocation());
    }

    private boolean hasGraph(AnalysisMethod analysisMethod, Function<PersistedAnalysisMethod.Reader, Boolean> hasGraphFunction) {
        var methodData = getMethodData(analysisMethod);
        if (methodData == null) {
            return false;
        }
        return hasGraphFunction.apply(methodData);
    }

    private EncodedGraph getEncodedGraph(AnalysisMethod analysisMethod, Text.Reader location) {
        byte[] encodedAnalyzedGraph = readEncodedGraph(location.toString());
        EncodedGraph encodedGraph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphDecoder(this, analysisMethod, universe.getSnippetReflection()), encodedAnalyzedGraph);
        for (int i = 0; i < encodedGraph.getNumObjects(); ++i) {
            if (encodedGraph.getObject(i) instanceof CGlobalDataInfo cGlobalDataInfo) {
                encodedGraph.setObject(i, CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalDataInfo.getData()));
            }
        }
        return encodedGraph;
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

    /**
     * This method is needed to ensure all the base layer analysis elements from the strengthened
     * graph are created early enough and seen by the analysis. This is done by decoding the graph
     * using a decoder that loads analysis elements instead of hosted elements.
     */
    @Override
    public void loadPriorStrengthenedGraphAnalysisElements(AnalysisMethod analysisMethod) {
        if (hasStrengthenedGraph(analysisMethod)) {
            PersistedAnalysisMethod.Reader methodData = getMethodData(analysisMethod);
            byte[] encodedAnalyzedGraph = readEncodedGraph(methodData.getStrengthenedGraphLocation().toString());
            EncodedGraph graph = (EncodedGraph) ObjectCopier.decode(imageLayerSnapshotUtil.getGraphHostedToAnalysisElementsDecoder(this, analysisMethod, universe.getSnippetReflection()),
                            encodedAnalyzedGraph);
            for (Object o : graph.getObjects()) {
                if (o instanceof AnalysisMethod m) {
                    m.setReachableInCurrentLayer();
                } else if (o instanceof JavaMethodProfile javaMethodProfile) {
                    for (var m : javaMethodProfile.getMethods()) {
                        if (m.getMethod() instanceof AnalysisMethod aMethod) {
                            aMethod.setReachableInCurrentLayer();
                        }
                    }
                } else if (o instanceof ImageHeapConstant constant) {
                    loadMaterializedChildren(constant);
                }
            }
        }
    }

    private void loadMaterializedChildren(ImageHeapConstant constant) {
        if (constant instanceof ImageHeapInstance imageHeapInstance) {
            loadMaterializedChildren(constant, imageHeapInstance::getFieldValue, imageHeapInstance.getFieldValuesSize());
        } else if (constant instanceof ImageHeapObjectArray imageHeapObjectArray) {
            loadMaterializedChildren(constant, imageHeapObjectArray::getElement, imageHeapObjectArray.getLength());
        }
    }

    private void loadMaterializedChildren(ImageHeapConstant constant, IntFunction<Object> valuesFunction, int size) {
        PersistedConstant.Reader baseLayerConstant = findConstant(ImageHeapConstant.getConstantID(constant));
        if (baseLayerConstant != null) {
            StructList.Reader<ConstantReference.Reader> data = baseLayerConstant.getObject().getData();
            assert size == data.size() : "The size of the constant in the base layer does not match the size in the application: %d != %d".formatted(data.size(), size);
            for (int i = 0; i < data.size(); ++i) {
                ConstantReference.Reader childConstant = data.get(i);
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

    @Override
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

    @Override
    public void addBaseLayerField(AnalysisField analysisField) {
        fields.putIfAbsent(analysisField.getId(), analysisField);
    }

    @Override
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
        registerFlag(isAccessed, debug -> {
            analysisField.injectDeclaredType();
            analysisField.registerAsAccessed(PERSISTED);
        });
        registerFlag(isRead, debug -> analysisField.registerAsRead(PERSISTED));
        registerFlag(fieldData.getIsWritten(), debug -> {
            analysisField.injectDeclaredType();
            analysisField.registerAsWritten(PERSISTED);
        });
        registerFlag(fieldData.getIsFolded(), debug -> analysisField.registerAsFolded(PERSISTED));
    }

    private PersistedAnalysisField.Reader getFieldData(AnalysisField analysisField) {
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

    public void postFutureBigbangTasks() {
        BigBang bigbang = universe.getBigbang();
        guarantee(bigbang != null, "Those tasks should only be executed when the bigbang is not null.");
        for (DebugContextRunnable task : futureBigbangTasks) {
            bigbang.postTask(task);
        }
    }

    @Override
    public boolean hasValueForConstant(JavaConstant javaConstant) {
        Object object = hostedValuesProvider.asObject(Object.class, javaConstant);
        return hasValueForObject(object);
    }

    @SuppressFBWarnings(value = "ES", justification = "Reference equality check needed to detect intern status")
    private boolean hasValueForObject(Object object) {
        return switch (object) {
            case DynamicHub dynamicHub -> typeToConstant.containsKey(((SVMHost) universe.hostVM()).lookupType(dynamicHub).getId());
            case String string -> stringToConstant.containsKey(string) && string.intern() == string;
            case Enum<?> e -> enumToConstant.containsKey(e);
            default -> false;
        };
    }

    @Override
    public ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        Object object = hostedValuesProvider.asObject(Object.class, javaConstant);
        return getValueForObject(object);
    }

    private ImageHeapConstant getValueForObject(Object object) {
        return switch (object) {
            case DynamicHub dynamicHub ->
                getOrCreateConstant(typeToConstant.get(((SVMHost) universe.hostVM()).lookupType(dynamicHub).getId()));
            case String string -> getOrCreateConstant(stringToConstant.get(string));
            case Enum<?> e -> getOrCreateConstant(enumToConstant.get(e));
            default -> throw AnalysisError.shouldNotReachHere("The constant was not in the persisted heap.");
        };
    }

    @Override
    public Set<Integer> getRelinkedFields(AnalysisType type) {
        return imageLayerSnapshotUtil.getRelinkedFields(type, metaAccess);
    }

    public ImageHeapConstant getOrCreateConstant(int id) {
        return getOrCreateConstant(id, null);
    }

    /**
     * Get the {@link ImageHeapConstant} representation for a specific base layer constant id. If
     * known, the parentReachableHostedObject will point to the corresponding constant in the
     * underlying host VM, found by querying the parent object that made this constant reachable
     * (see {@link SVMImageLayerLoader#getReachableHostedValue(ImageHeapConstant, int)}).
     */
    private ImageHeapConstant getOrCreateConstant(int id, JavaConstant parentReachableHostedObjectCandidate) {
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

    private Object[] getReferencedValues(ImageHeapConstant parentConstant, StructList.Reader<ConstantReference.Reader> data, Set<Integer> positionsToRelink) {
        Object[] values = new Object[data.size()];
        for (int position = 0; position < data.size(); ++position) {
            ConstantReference.Reader constantData = data.get(position);
            if (delegateProcessing(constantData, values, position)) {
                continue;
            }
            int finalPosition = position;
            values[position] = switch (constantData.which()) {
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
                    /*
                     * This constant is a field value or an object value that was not materialized
                     * in the base image.
                     */
                    new AnalysisFuture<>(() -> {
                        String errorMessage = "Reading the value of a base layer constant which was not materialized in the base image, ";
                        if (parentConstant instanceof ImageHeapInstance instance) {
                            AnalysisField field = getFieldFromIndex(instance, finalPosition);
                            errorMessage += "reachable by reading field " + field + " of parent object constant: " + parentConstant;
                        } else {
                            errorMessage += "reachable by indexing at position " + finalPosition + " into parent array constant: " + parentConstant;
                        }
                        throw AnalysisError.shouldNotReachHere(errorMessage);
                    });
                case PRIMITIVE_VALUE -> {
                    PrimitiveValue.Reader pv = constantData.getPrimitiveValue();
                    yield JavaConstant.forPrimitive((char) pv.getTypeChar(), pv.getRawValue());
                }
                default -> throw GraalError.shouldNotReachHere("Unexpected constant reference: " + constantData.which());
            };
        }
        return values;
    }

    private boolean delegateProcessing(ConstantReference.Reader constantRef, Object[] values, int i) {
        if (constantRef.isMethodPointer()) {
            AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
                AnalysisType methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
                int mid = constantRef.getMethodPointer().getMethodId();
                AnalysisMethod method = getAnalysisMethodForBaseLayerId(mid);
                RelocatableConstant constant = new RelocatableConstant(new MethodPointer(method), methodPointerType);
                values[i] = constant;
                return constant;
            });
            values[i] = task;
            return true;
        } else if (constantRef.isCEntryPointLiteralCodePointer()) {
            AnalysisType cEntryPointerLiteralPointerType = metaAccess.lookupJavaType(CEntryPointLiteralCodePointer.class);
            CEntryPointLiteralReference.Reader ref = constantRef.getCEntryPointLiteralCodePointer();
            String methodName = ref.getMethodName().toString();
            Class<?> definingClass = lookupBaseLayerTypeInHostVM(ref.getDefiningClass().toString());
            Class<?>[] parameterTypes = IntStream.range(0, ref.getParameterNames().size())
                            .mapToObj(j -> ref.getParameterNames().get(j).toString())
                            .map(this::lookupBaseLayerTypeInHostVM).toArray(Class[]::new);
            values[i] = new RelocatableConstant(new CEntryPointLiteralCodePointer(definingClass, methodName, parameterTypes), cEntryPointerLiteralPointerType);
            return true;
        }
        return false;
    }

    private static Object getArray(PrimitiveArray.Reader reader) {
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
            rawElementValue = hostedValuesProvider.readArrayElement(hostedArray, idx);
        }
        return rawElementValue;
    }

    private JavaConstant getHostedFieldValue(ImageHeapInstance instance, AnalysisField field) {
        ValueSupplier<JavaConstant> rawFieldValue;
        try {
            JavaConstant hostedInstance = instance.getHostedObject();
            AnalysisError.guarantee(hostedInstance != null);
            rawFieldValue = hostedValuesProvider.readFieldValue(field, hostedInstance);
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
                objectOffsets.put(ImageHeapConstant.getConstantID(heapObj), objectOffset);
                heapObj.markWrittenInPreviousLayer();
            }
            return heapObj;
        });
    }

    /**
     * Look up an object in current hosted VM based on the recipe serialized from the base layer.
     */
    private JavaConstant lookupHostedObject(PersistedConstant.Reader baseLayerConstant, AnalysisType analysisType) {
        if (!baseLayerConstant.getIsSimulated()) {
            Class<?> clazz = analysisType.getJavaClass();
            return lookupHostedObject(baseLayerConstant, clazz);
        }
        return null;
    }

    private JavaConstant lookupHostedObject(PersistedConstant.Reader baseLayerConstant, Class<?> clazz) {
        if (!baseLayerConstant.isObject()) {
            return null;
        }
        Relinking.Reader relinking = baseLayerConstant.getObject().getRelinking();
        if (relinking.isNotRelinked()) {
            return null;
        } else if (relinking.isFieldConstant()) {
            var fieldConstant = relinking.getFieldConstant();
            AnalysisField analysisField = getAnalysisFieldForBaseLayerId(fieldConstant.getOriginFieldId());
            if (!(analysisField.getWrapped() instanceof BaseLayerField)) {
                VMError.guarantee(!baseLayerConstant.getIsSimulated(), "Should not alter the initialization status for simulated constants.");
                /*
                 * The declaring type of relinked fields was already initialized in the previous
                 * layer (see SVMImageLayerWriter#shouldRelinkField).
                 */
                if (fieldConstant.getRequiresLateLoading()) {
                    /*
                     * Fields with a field value transformer are relinked later, after all possible
                     * transformers have been registered. *Guarantee* that the declaring type has
                     * been initialized by now. Note that reading the field below will prevent a
                     * transformer to be installed at a later time.
                     */
                    VMError.guarantee(analysisField.getDeclaringClass().isInitialized());
                } else {
                    /*
                     * All other fields are relinked earlier, before the constant is needed. *Force*
                     * the build time initialization of the declaring type before reading the field
                     * value.
                     */
                    Class<?> fieldDeclaringClass = analysisField.getDeclaringClass().getJavaClass();
                    classInitializationSupport.initializeAtBuildTime(fieldDeclaringClass, "Already initialized in base layer.");
                }
                /* Read fields through the hostedValueProvider and apply object replacement. */
                return hostedValuesProvider.readFieldValueWithReplacement(analysisField, null);
            }
        } else if (clazz.equals(Class.class)) {
            /* DynamicHub corresponding to $$TypeSwitch classes are not relinked */
            if (baseLayerConstant.isObject() && relinking.isClassConstant()) {
                int typeId = relinking.getClassConstant().getTypeId();
                return getDynamicHub(typeId);
            }
        } else if (clazz.equals(String.class)) {
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

    @SuppressWarnings("unchecked")
    private Enum<?> getEnumValue(Text.Reader className, Text.Reader name) {
        Class<?> enumClass = imageLayerBuildingSupport.lookupClass(false, className.toString());
        /* asSubclass produces an "unchecked" warning */
        return Enum.valueOf(enumClass.asSubclass(Enum.class), name.toString());
    }

    private void addBaseLayerValueToImageHeap(ImageHeapConstant constant, ImageHeapConstant parentConstant, int i) {
        if (parentConstant instanceof ImageHeapInstance imageHeapInstance) {
            universe.getHeapScanner().registerBaseLayerValue(constant, getFieldFromIndex(imageHeapInstance, i));
        } else if (parentConstant instanceof ImageHeapObjectArray) {
            universe.getHeapScanner().registerBaseLayerValue(constant, i);
        } else {
            throw AnalysisError.shouldNotReachHere("unexpected constant: " + constant);
        }
    }

    private void ensureHubInitialized(ImageHeapConstant constant) {
        JavaConstant javaConstant = constant.getHostedObject();
        if (constant.getType().getJavaClass().equals(Class.class)) {
            DynamicHub hub = universe.getHostedValuesProvider().asObject(DynamicHub.class, javaConstant);
            AnalysisType type = ((SVMHost) universe.hostVM()).lookupType(hub);
            ensureHubInitialized(type);
            /*
             * If the persisted hub has a non-null arrayHub, the corresponding DynamicHub must be
             * created and the initializeMetaDataTask needs to be executed to ensure the hosted
             * object matches the persisted constant.
             */
            PersistedAnalysisType.Reader typeData = findType(getBaseLayerTypeId(type));
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

    public Long getObjectOffset(JavaConstant javaConstant) {
        ImageHeapConstant imageHeapConstant = (ImageHeapConstant) javaConstant;
        return objectOffsets.get(ImageHeapConstant.getConstantID(imageHeapConstant));
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

    @Override
    public boolean hasDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.containsKey(tid);
    }

    @Override
    public int getDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.get(tid);
    }

    private JavaConstant getDynamicHub(int tid) {
        AnalysisType type = getAnalysisTypeForBaseLayerId(tid);
        DynamicHub hub = ((SVMHost) universe.hostVM()).dynamicHub(type);
        return hostedValuesProvider.forObject(hub);
    }

    private static void injectIdentityHashCode(Object object, Integer identityHashCode) {
        if (object == null || identityHashCode == null) {
            return;
        }
        boolean result = IdentityHashCodeUtil.injectIdentityHashCode(object, identityHashCode);
        if (!result) {
            if (SubstrateOptions.LoggingHashCodeInjection.getValue()) {
                LogUtils.warning("Object of type %s already had an hash code: %s", object.getClass(), object);
            }
        }
    }

    public void rescanHub(AnalysisType type, DynamicHub hub) {
        if (hasValueForObject(hub)) {
            universe.getHeapScanner().rescanObject(hub);
            scanCompanionField(hub);
            universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.classInitializationInfo);
            if (type.getJavaKind() == JavaKind.Object) {
                if (type.isArray()) {
                    DynamicHub componentHub = hub.getComponentHub();
                    scanCompanionField(componentHub);
                    universe.getHeapScanner().rescanField(componentHub.getCompanion(), SVMImageLayerSnapshotUtil.arrayHub);
                }
                universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.interfacesEncoding);
                if (type.isEnum()) {
                    universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.enumConstantsReference);
                }
            }
        }
    }

    private void scanCompanionField(DynamicHub hub) {
        var instance = (ImageHeapInstance) getValueForObject(hub);
        instance.readFieldValue(metaAccess.lookupJavaField(dynamicHubCompanionField));
    }

    public ClassInitializationInfo getClassInitializationInfo(AnalysisType type) {
        PersistedAnalysisType.Reader typeMap = findType(type.getId());

        var initInfo = typeMap.getClassInitializationInfo();
        if (initInfo.getIsNoInitializerNoTracking()) {
            return ClassInitializationInfo.forNoInitializerInfo(false);
        } else if (initInfo.getIsInitializedNoTracking()) {
            return ClassInitializationInfo.forInitializedInfo(false);
        } else if (initInfo.getIsFailedNoTracking()) {
            return ClassInitializationInfo.forFailedInfo(false);
        } else {
            boolean isTracked = initInfo.getIsTracked();

            ClassInitializationInfo.InitState initState;
            if (initInfo.getIsInitialized()) {
                initState = ClassInitializationInfo.InitState.FullyInitialized;
            } else if (initInfo.getIsInErrorState()) {
                initState = ClassInitializationInfo.InitState.InitializationError;
            } else {
                assert initInfo.getIsLinked() : "Invalid state";
                int classInitializerId = initInfo.getInitializerMethodId();
                MethodPointer classInitializer = (classInitializerId == 0) ? null : new MethodPointer(getAnalysisMethodForBaseLayerId(classInitializerId));
                return new ClassInitializationInfo(classInitializer, isTracked);
            }

            return new ClassInitializationInfo(initState, initInfo.getHasInitializer(), initInfo.getIsBuildTimeInitialized(), isTracked);
        }
    }

    public static class JavaConstantSupplier {
        private final ConstantReference.Reader constantReference;

        JavaConstantSupplier(ConstantReference.Reader constantReference) {
            this.constantReference = constantReference;
        }

        public JavaConstant get(SVMImageLayerLoader imageLayerLoader) {
            return switch (constantReference.which()) {
                case OBJECT_CONSTANT -> {
                    int id = constantReference.getObjectConstant().getConstantId();
                    yield id == 0 ? null : imageLayerLoader.getOrCreateConstant(id);
                }
                case NULL_POINTER -> JavaConstant.NULL_POINTER;
                case PRIMITIVE_VALUE -> {
                    PrimitiveValue.Reader pv = constantReference.getPrimitiveValue();
                    yield JavaConstant.forPrimitive((char) pv.getTypeChar(), pv.getRawValue());
                }
                default -> throw GraalError.shouldNotReachHere("Unexpected constant reference: " + constantReference.which());
            };
        }
    }

    public static JavaConstantSupplier getConstant(ConstantReference.Reader constantReference) {
        return new JavaConstantSupplier(constantReference);
    }
}
