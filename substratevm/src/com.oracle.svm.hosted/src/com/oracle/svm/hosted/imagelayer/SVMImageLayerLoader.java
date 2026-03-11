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
import static com.oracle.svm.core.classinitialization.ClassInitializationInfo.InitState.FullyInitialized;
import static com.oracle.svm.core.classinitialization.ClassInitializationInfo.InitState.InitializationError;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.CLASS_INIT_NAME;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.lang.reflect.Constructor;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.api.ImageLayerLoader;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
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
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.LayeredImageOptions;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.proxy.DynamicProxySupport;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.imagelayer.BaseLayerMethodResolver.BaseLayerProvider;
import com.oracle.svm.hosted.imagelayer.BaseLayerMethodResolver.PersistedMethod;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.serialize.SerializationFeature;
import com.oracle.svm.hosted.snapshot.c.CGlobalDataInfoData;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.dynamichub.DynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData.WrappedMethod;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData.WrappedMethod.WrappedMember;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData.WrappedType;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData.WrappedType.SerializationGenerated;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnnotationData;
import com.oracle.svm.hosted.snapshot.elements.PersistedHostedMethodData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.hosted.snapshot.util.SnapshotPrimitiveList;
import com.oracle.svm.hosted.snapshot.util.SnapshotStructList;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.sdk.staging.layeredimage.LayeredCompilationBehavior;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.NodeClassMap;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SVMImageLayerLoader extends ImageLayerLoader {
    private final boolean useSharedLayerGraphs;
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;
    private final SharedLayerSnapshotData.Loader snapshot;
    private final ImageLayerGraphStore graphStore;
    private final ClassInitializationSupport classInitializationSupport;
    private final boolean buildingApplicationLayer;

    private HostedUniverse hostedUniverse;
    private ImageLayerConstantLoader constantLoader;

    /** Maps from the previous layer element id to the linked elements in this layer. */
    protected final Map<Integer, AnalysisType> types = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisMethod> methods = new ConcurrentHashMap<>();
    protected final Map<Integer, AnalysisField> fields = new ConcurrentHashMap<>();

    private final Map<Integer, BaseLayerType> baseLayerTypes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> typeToHubIdentityHashCode = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerMethod> baseLayerMethods = new ConcurrentHashMap<>();
    private final Map<Integer, BaseLayerField> baseLayerFields = new ConcurrentHashMap<>();

    protected final Set<DebugContextRunnable> futureBigbangTasks = ConcurrentHashMap.newKeySet();
    private final Map<ResolvedJavaType, Boolean> capturingClasses = new ConcurrentHashMap<>();
    private final Map<ResolvedJavaMethod, Boolean> methodHandleCallers = new ConcurrentHashMap<>();

    /** Map from {@link SVMImageLayerSnapshotUtil#getTypeDescriptor} to base layer type ids. */
    private EconomicMap<String, Integer> typeDescriptorToBaseLayerId;
    /** Map from {@link SVMImageLayerSnapshotUtil#getMethodDescriptor} to base layer method ids. */
    private EconomicMap<String, Integer> methodDescriptorToBaseLayerId;
    /** Map from base layer type ids to base layer method ids. */
    private EconomicMap<Integer, List<Integer>> declaringTypeIdToMethodIds;
    protected AnalysisUniverse universe;
    protected AnalysisMetaAccess metaAccess;
    protected HostedValuesProvider hostedValuesProvider;
    private final LayeredStaticFieldSupport layeredStaticFieldSupport = LayeredStaticFieldSupport.singleton();

    /**
     * Used to decode {@link NodeClass} ids in {@link #getEncodedGraph}.
     */
    private NodeClassMap nodeClassMap;
    private final BaseLayerMethodResolver baseLayerMethodResolver = new BaseLayerMethodResolver(new LoaderBaseLayerProvider());

    public SVMImageLayerLoader(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, HostedImageLayerBuildingSupport imageLayerBuildingSupport, SharedLayerSnapshotData.Loader snapshot,
                    FileChannel graphChannel, boolean useSharedLayerGraphs) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.imageLayerBuildingSupport = imageLayerBuildingSupport;
        this.snapshot = snapshot;
        this.graphStore = graphChannel == null ? null : ImageLayerGraphStore.openForReading(graphChannel);
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        classInitializationSupport = ClassInitializationSupport.singleton();
        buildingApplicationLayer = ImageLayerBuildingSupport.buildingApplicationLayer();
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

    public void initNodeClassMap() {
        assert nodeClassMap == null : "cannot re-initialize the nodeClassMap";
        byte[] encodedGlobalNodeClassMap = graphStore.read(snapshot.getNodeClassMapLocation());
        SVMImageLayerSnapshotUtil.AbstractSVMGraphDecoder decoder = imageLayerSnapshotUtil.getGraphDecoder(this, null, universe.getSnippetReflection(), null);
        nodeClassMap = (NodeClassMap) ObjectCopier.decode(decoder, encodedGlobalNodeClassMap);
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

        SnapshotStructList.Loader<PersistedAnalysisTypeData.Loader> typesReader = snapshot.getTypes();
        typeDescriptorToBaseLayerId = EconomicMap.create(typesReader.size());
        for (PersistedAnalysisTypeData.Loader typeData : typesReader) {
            String descriptor = typeData.getDescriptor();
            typeDescriptorToBaseLayerId.put(descriptor, typeData.getId());
        }

        SnapshotStructList.Loader<PersistedAnalysisMethodData.Loader> methodsReader = snapshot.getMethods();
        methodDescriptorToBaseLayerId = EconomicMap.create(methodsReader.size());
        declaringTypeIdToMethodIds = EconomicMap.create();
        for (PersistedAnalysisMethodData.Loader methodData : methodsReader) {
            String descriptor = methodData.getDescriptor();
            methodDescriptorToBaseLayerId.put(descriptor, methodData.getId());
            List<Integer> methodIds = declaringTypeIdToMethodIds.computeIfAbsent(methodData.getDeclaringTypeId(), _ -> new ArrayList<>());
            methodIds.add(methodData.getId());
        }

        constantLoader = new ImageLayerConstantLoader(this, imageLayerSnapshotUtil, imageLayerBuildingSupport, snapshot);
        constantLoader.prepareConstantRelinking();
    }

    /**
     * The non-transformed field values are prepared earlier because some constants can be loaded
     * very early.
     */
    public void relinkNonTransformedStaticFinalFieldValues() {
        constantLoader.relinkStaticFinalFieldValues(false);
    }

    /**
     * The transformed field values need to be prepared after all the transformer are installed.
     */
    public void relinkTransformedStaticFinalFieldValues() {
        constantLoader.relinkStaticFinalFieldValues(true);
    }

    public void cleanupAfterCompilation() {
        if (graphStore != null) {
            graphStore.close();
        }
    }

    public AnalysisType getAnalysisTypeForBaseLayerId(int tid) {
        if (!types.containsKey(tid)) {
            loadType(findType(tid));
        }
        guarantee(types.containsKey(tid), "Type with id %d was not correctly loaded.", tid);
        /*
         * The type needs to be looked up because it ensures the type is completely created, as the
         * types map is populated before the type is created.
         */
        return universe.lookup(types.get(tid).getWrapped());
    }

    private PersistedAnalysisTypeData.Loader findType(int tid) {
        return SnapshotAdapters.binarySearchUnique(tid, snapshot.getTypes(), PersistedAnalysisTypeData.Loader::getId);
    }

    /**
     * Load the host VM type corresponding to the serialized type data.
     * <p>
     * First, find the corresponding host VM {@link Class} object by name using
     * {@link #lookupBaseLayerTypeInHostVM(String)}.
     * <p>
     * Then, lookup the hosted {@link Class} in the {@link AnalysisMetaAccess} which will trigger
     * creation of the corresponding {@link AnalysisType} object.
     * <p>
     * The {@link AnalysisType} constructor calls {@link #lookupHostedTypeInBaseLayer(AnalysisType)}
     * to check if the newly created type already exists in the base layer. If that's the case, the
     * {@link AnalysisType} object takes the same {@code id} as the corresponding base layer type
     * and this mapping is also registered in the {@link #types} map.
     */
    private void loadType(PersistedAnalysisTypeData.Loader typeData) {
        int tid = typeData.getId();

        if (delegateLoadType(typeData)) {
            return;
        }

        ResolvedJavaType clazz = lookupBaseLayerTypeInHostVM(typeData.getClassJavaName());

        ResolvedJavaType superClass = getResolvedJavaTypeForBaseLayerId(typeData.getSuperClassTypeId());
        ResolvedJavaType[] interfaces = SnapshotAdapters.toArray(typeData.getInterfaces(), this::getResolvedJavaTypeForBaseLayerId, ResolvedJavaType[]::new);

        if (clazz != null) {
            /* Lookup the host VM type and create the analysis type. */
            universe.lookup(clazz);
        }

        if (!types.containsKey(tid)) {
            /*
             * If the type cannot be looked up by name, an incomplete AnalysisType, which uses a
             * BaseLayerType in its wrapped field, has to be created
             */
            BaseLayerType baseLayerType = getBaseLayerType(typeData, tid, superClass, interfaces);

            baseLayerType.setInstanceFields(SnapshotAdapters.toArray(typeData.getInstanceFieldIds(), this::getBaseLayerField, ResolvedJavaField[]::new));
            baseLayerType.setInstanceFieldsWithSuper(SnapshotAdapters.toArray(typeData.getInstanceFieldIdsWithSuper(), this::getBaseLayerField, ResolvedJavaField[]::new));

            AnalysisType type = universe.lookup(baseLayerType);
            guarantee(getBaseLayerTypeId(type) == tid, "The base layer type %s is not correctly matched to the id %d", type, tid);
        }
    }

    protected boolean delegateLoadType(PersistedAnalysisTypeData.Loader typeData) {
        WrappedType.Loader wrappedType = typeData.getWrappedType();
        if (wrappedType.isNone()) {
            return false;
        }
        if (wrappedType.isSerializationGenerated()) {
            SerializationGenerated.Loader sg = wrappedType.getSerializationGenerated();
            int rawDeclaringClassId = sg.getRawDeclaringClassId();
            int rawTargetConstructorClassId = sg.getRawTargetConstructorId();
            AnalysisType rawDeclaringType = getAnalysisTypeForBaseLayerId(rawDeclaringClassId);
            AnalysisType rawTargetConstructorType = getAnalysisTypeForBaseLayerId(rawTargetConstructorClassId);
            Class<?> rawDeclaringClass = rawDeclaringType.getJavaClass();
            Class<?> rawTargetConstructorClass = rawTargetConstructorType.getJavaClass();
            Constructor<?> rawTargetConstructor = ReflectionUtil.lookupConstructor(rawTargetConstructorClass);
            Constructor<?> constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(rawDeclaringClass, rawTargetConstructor);
            DynamicHub rawDeclaringHub = typeToHub(rawDeclaringType);
            DynamicHub rawTargetConstructorHub = typeToHub(rawTargetConstructorType);
            SerializationSupport.currentLayer().addConstructorAccessor(
                            AccessCondition.unconditional(),
                            false,
                            rawDeclaringHub,
                            rawTargetConstructorHub,
                            SerializationFeature.getConstructorAccessor(constructor));
            Class<?> constructorAccessor = SerializationSupport.getHostedSerializationConstructorAccessor(rawDeclaringHub, rawTargetConstructorHub).getClass();
            metaAccess.lookupJavaType(constructorAccessor);
            return true;
        } else if (wrappedType.isLambda()) {
            String capturingClassName = wrappedType.getLambda().getCapturingClass();
            ResolvedJavaType capturingClass = imageLayerBuildingSupport.lookupType(false, capturingClassName);
            loadLambdaTypes(capturingClass);
            return types.containsKey(typeData.getId());
        } else if (wrappedType.isProxyType()) {
            Class<?>[] interfaces = SnapshotAdapters.toArray(typeData.getInterfaces(), tid -> getAnalysisTypeForBaseLayerId(tid).getJavaClass(), Class[]::new);
            Class<?> proxy = DynamicProxySupport.singleton().getProxyClassHosted(interfaces);
            metaAccess.lookupJavaType(proxy);
            return true;
        }
        return false;
    }

    /**
     * The {@link SubstitutionMethod} contains less information than the original
     * {@link ResolvedJavaMethod} and trying to access it can result in an exception.
     */
    private static ResolvedJavaMethod getOriginalWrapped(AnalysisMethod method) {
        ResolvedJavaMethod wrapped = method.getWrapped();
        if (wrapped instanceof SubstitutionMethod subst) {
            return subst.getAnnotated();
        }
        return wrapped;
    }

    /**
     * Load all lambda types of the given capturing class. Each method of the capturing class is
     * parsed (see {@link LambdaParser#createMethodGraph(ResolvedJavaMethod, OptionValues)}). The
     * lambda types can then be found in the constant nodes of the graphs.
     */
    private void loadLambdaTypes(ResolvedJavaType capturingClass) {
        capturingClasses.computeIfAbsent(capturingClass, _ -> {
            /*
             * Getting the original wrapped method is important to avoid getting exceptions that
             * would be ignored otherwise.
             */
            LambdaParser.allExecutablesDeclaredInClass(universe.lookup(capturingClass))
                            .filter(m -> m.getCode() != null)
                            .forEach(m -> loadLambdaTypes(getOriginalWrapped((AnalysisMethod) m), universe.getBigbang()));
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
        methodHandleCallers.computeIfAbsent(m, _ -> {
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
    protected ResolvedJavaType lookupBaseLayerTypeInHostVM(String type) {
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
        ResolvedJavaType clazz = lookupPrimitiveClass(componentType);
        if (clazz == null) {
            clazz = imageLayerBuildingSupport.lookupType(true, componentType);
        }
        if (clazz == null) {
            return null;
        }
        while (arrayType > 0) {
            clazz = clazz.getArrayClass();
            arrayType--;
        }
        return clazz;
    }

    private static ResolvedJavaType lookupPrimitiveClass(String typeName) {
        Class<?> type = switch (typeName) {
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
        return type == null ? null : GuestAccess.get().lookupType(type);
    }

    private BaseLayerType getBaseLayerType(int tid) {
        PersistedAnalysisTypeData.Loader typeData = findType(tid);
        ResolvedJavaType superClass = getResolvedJavaTypeForBaseLayerId(typeData.getSuperClassTypeId());

        ResolvedJavaType[] interfaces = SnapshotAdapters.toArray(typeData.getInterfaces(), this::getResolvedJavaTypeForBaseLayerId, ResolvedJavaType[]::new);
        return getBaseLayerType(typeData, tid, superClass, interfaces);
    }

    private BaseLayerType getBaseLayerType(PersistedAnalysisTypeData.Loader td, int tid, ResolvedJavaType superClass, ResolvedJavaType[] interfaces) {
        return baseLayerTypes.computeIfAbsent(tid, _ -> {
            String className = td.getClassName();
            String sourceFileName = td.hasSourceFileName() ? td.getSourceFileName() : null;
            ResolvedJavaType enclosingType = getResolvedJavaTypeForBaseLayerId(td.getEnclosingTypeId());
            ResolvedJavaType componentType = getResolvedJavaTypeForBaseLayerId(td.getComponentTypeId());
            ResolvedJavaType objectType = GuestAccess.get().lookupType(Object.class);
            AnnotationValue[] annotations = getAnnotations(td.getAnnotationList());

            return new BaseLayerType(className, tid, td.getModifiers(), td.getIsInterface(), td.getIsEnum(), td.getIsRecord(), td.getIsInitialized(), td.getIsLinked(), sourceFileName,
                            enclosingType, componentType, superClass, interfaces, objectType, annotations, baseLayerMethodResolver);
        });
    }

    private final class LoaderBaseLayerProvider implements BaseLayerProvider {
        @Override
        public List<PersistedMethod> getDeclaredMethods(BaseLayerType declaringType) {
            List<Integer> methodIds = declaringTypeIdToMethodIds.get(declaringType.getBaseLayerId());
            if (methodIds == null) {
                return List.of();
            }

            List<PersistedMethod> typeMethods = new ArrayList<>(methodIds.size());
            for (int methodId : methodIds) {
                PersistedAnalysisMethodData.Loader methodData = findMethod(methodId);
                int[] argumentTypeIds = new int[methodData.getArgumentTypeIds().size()];
                for (int i = 0; i < argumentTypeIds.length; i++) {
                    argumentTypeIds[i] = methodData.getArgumentTypeIds().get(i);
                }
                typeMethods.add(new PersistedMethod(methodData.getName(), methodData.getModifiers(), methodData.getReturnTypeId(), argumentTypeIds,
                                () -> getAnalysisMethodForBaseLayerId(methodId).getWrapped()));
            }
            return typeMethods;
        }

        @Override
        public int getBaseLayerTypeId(JavaType type) {
            return SVMImageLayerLoader.getBaseLayerTypeId(universe.lookup(type));
        }
    }

    private AnnotationValue[] getAnnotations(SnapshotStructList.Loader<PersistedAnnotationData.Loader> reader) {
        return AnnotationSnapshotCodec.readAnnotations(reader, this::lookupBaseLayerTypeInHostVM, name -> imageLayerBuildingSupport.lookupClass(false, name));
    }

    @Override
    public int lookupHostedTypeInBaseLayer(AnalysisType type) {
        /* Assume that the type was not reachable in the base image. */
        int id = -1;
        if (type.getWrapped() instanceof BaseLayerType baseLayerType) {
            id = baseLayerType.getBaseLayerId();
        } else {
            String typeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(type);
            Integer typeId = typeDescriptorToBaseLayerId.get(typeDescriptor);
            if (typeId != null) {
                id = typeId;
                initializeBaseLayerTypeBeforePublishing(type, findType(typeId));
            }
        }
        if (id == -1 || types.putIfAbsent(id, type) != null) {
            /* A complete type is treated as a different type than its incomplete version */
            return -1;
        }
        return id;
    }

    private static int getBaseLayerTypeId(AnalysisType type) {
        VMError.guarantee(type.isInSharedLayer());
        if (type.getWrapped() instanceof BaseLayerType baseLayerType) {
            return baseLayerType.getBaseLayerId();
        }
        return type.getId();
    }

    /**
     * This method is invoked *before* the {@link AnalysisType} is published in the
     * {@link AnalysisUniverse}. The side effects of this method are visible to other threads that
     * are consuming the {@link AnalysisType} object.
     */
    private void initializeBaseLayerTypeBeforePublishing(AnalysisType type, PersistedAnalysisTypeData.Loader typeData) {
        assert !(type.getWrapped() instanceof BaseLayerType);
        VMError.guarantee(type.isLinked() == typeData.getIsLinked());
        /*
         * For types reachable in this layer register the *computed* initialization kind extracted
         * from the previous layer. This will cause base layer types to have a *strict*
         * initialization kind in this layer which will prevent further changes to the
         * initialization kind, even in ways that would otherwise be considered compatible, e.g.,
         * RUN_TIME -> BUILD_TIME. Similarly, if a different initialization kind was already
         * registered in this layer registration will fail.
         *
         * Note that this is done after the app-layer class initialization specification is applied,
         * so we don't have to traverse all types. Moreover, for package-level specification this
         * should also be OK, because package-level specification is only a suggestion and the
         * base-layer will always win as it is going over user classes.
         */
        if (typeData.getIsInitialized()) {
            classInitializationSupport.withUnsealedConfiguration(() -> classInitializationSupport.initializeAtBuildTime(type, "computed in a previous layer"));
        } else if (typeData.getIsFailedInitialization()) {
            /*
             * In the previous layer this class was configured with --initialize-at-build-time but
             * its initialization failed so it was registered as run time initialized. We attempt to
             * init it again in this layer and verify that it fails. This will allow the class to be
             * configured again in this layer with --initialize-at-build-time, either before or
             * after this step.
             */
            classInitializationSupport.withUnsealedConfiguration(() -> classInitializationSupport.initializeAtBuildTime(type, "computed in a previous layer"));
            VMError.guarantee(classInitializationSupport.isFailedInitialization(type), "Expected the initialization to fail for %s, as it has failed in a previous layer.", type);
        } else if (typeData.getIsSuccessfulSimulation() || typeData.getIsFailedSimulation()) {
            /*
             * Simulation for this type was tried in a previous layer, and regardless whether it
             * succeeded or failed there's nothing to do here. We'll record the result in the
             * simulation registry when its simulation state is queried. We can do this lazily since
             * there is no API to modify simulation state, unlike for initialization.
             */
        } else {
            classInitializationSupport.withUnsealedConfiguration(() -> classInitializationSupport.initializeAtRunTime(type, "computed in a previous layer"));
        }

        /* Extract and record the base layer identity hashcode for this type. */
        int hubIdentityHashCode = typeData.getHubIdentityHashCode();
        typeToHubIdentityHashCode.put(typeData.getId(), hubIdentityHashCode);
    }

    /**
     * This method is invoked *after* the {@link AnalysisType} is published in the
     * {@link AnalysisUniverse} and it may execute concurrently with other threads using the type.
     */
    @Override
    public void initializeBaseLayerType(AnalysisType type) {
        VMError.guarantee(type.isInSharedLayer());
        PersistedAnalysisTypeData.Loader td = findType(getBaseLayerTypeId(type));
        postTask(td.getIsInstantiated(), _ -> type.registerAsInstantiated(PERSISTED));
        postTask(td.getIsUnsafeAllocated(), _ -> type.registerAsUnsafeAllocated(PERSISTED));
        postTask(td.getIsReachable(), _ -> type.registerAsReachable(PERSISTED));

        if (td.getIsAnySubtypeInstantiated()) {
            /*
             * Once a base layer type is loaded, loading all its instantiated subtypes ensures that
             * the application layer typestate is coherent with the base layer typestate. Otherwise,
             * unwanted optimizations could occur as the typestate would not contain some missed
             * types from the base layer.
             */
            var subTypesReader = td.getSubTypes();
            for (int i = 0; i < subTypesReader.size(); ++i) {
                int tid = subTypesReader.get(i);
                var subTypeReader = findType(tid);
                /* Only load instantiated subtypes. */
                postTask(subTypeReader.getIsInstantiated(), _ -> getAnalysisTypeForBaseLayerId(subTypeReader.getId()));
            }
        }
    }

    private void postTask(boolean condition, DebugContextRunnable task) {
        if (condition) {
            if (universe.getBigbang() != null) {
                universe.getBigbang().postTask(task);
            } else {
                futureBigbangTasks.add(task);
            }
        }
    }

    public AnalysisMethod getAnalysisMethodForBaseLayerId(int mid) {
        if (!methods.containsKey(mid)) {
            PersistedAnalysisMethodData.Loader methodData = findMethod(mid);
            loadMethod(methodData);
        }

        AnalysisMethod analysisMethod = methods.get(mid);
        AnalysisError.guarantee(analysisMethod != null, "Method with id %d was not correctly loaded.", mid);
        return analysisMethod;
    }

    private PersistedAnalysisMethodData.Loader findMethod(int mid) {
        return SnapshotAdapters.binarySearchUnique(mid, snapshot.getMethods(), PersistedAnalysisMethodData.Loader::getId);
    }

    private void loadMethod(PersistedAnalysisMethodData.Loader methodData) {
        int mid = methodData.getId();

        if (delegateLoadMethod(methodData)) {
            return;
        }

        int tid = methodData.getDeclaringTypeId();
        AnalysisType type = getAnalysisTypeForBaseLayerId(tid);

        AnalysisType[] parameterTypes = SnapshotAdapters.toArray(methodData.getArgumentTypeIds(), this::getAnalysisTypeForBaseLayerId, AnalysisType[]::new);

        AnalysisType returnType = getAnalysisTypeForBaseLayerId(methodData.getReturnTypeId());

        /*
         * First try to retrieve host method via reflection.
         *
         * Because we are using reflection to access the hosted universe, for substitution methods
         * we must use the Class<?> values of the substitution class (i.e. class with
         * the @TargetClass) and not the target of the substitution itself.
         */
        String name = methodData.getName();
        boolean maybeReachableViaReflection = methodData.getIsConstructor() || methodData.getIsDeclared();
        if (maybeReachableViaReflection) {
            ResolvedJavaType clazz = null;
            if (methodData.hasClassName()) {
                String className = methodData.getClassName();
                clazz = lookupBaseLayerTypeInHostVM(className);
            }
            if (clazz == null && !(type.getWrapped() instanceof BaseLayerType)) {
                /*
                 * BaseLayerTypes will always return java.lang.Object, which is not correct for
                 * reflective lookup.
                 */
                clazz = OriginalClassProvider.getOriginalType(type);
            }
            if (clazz != null) {
                ResolvedJavaType[] argumentClasses;
                if (methodData.hasArgumentClassNames()) {
                    argumentClasses = SnapshotAdapters.toArray(methodData.getArgumentClassNames(), this::lookupBaseLayerTypeInHostVM, ResolvedJavaType[]::new);
                } else {
                    argumentClasses = Arrays.stream(parameterTypes).map(AnalysisType::getWrapped).toArray(ResolvedJavaType[]::new);
                }
                if (Arrays.stream(argumentClasses).noneMatch(Objects::isNull)) {
                    var result = lookupMethodByJVMCIReflection(name, clazz, argumentClasses);
                    if (result != null) {
                        universe.lookup(result);
                        /*
                         * Note even if we found a method via reflection, it is not guaranteed it is
                         * the matching method. This is because, in a given class, reflection will
                         * not find all methods; one example it will not find is bridge methods
                         * inserted for covariant overrides.
                         */
                        if (methods.containsKey(mid)) {
                            return;
                        }
                    }
                } else if (LayeredImageOptions.LayeredImageDiagnosticOptions.LogLoadingFailures.getValue()) {
                    LogUtils.warning("Arguments reflectively loading %s. %s could not be found: %s", methodData.getClassName(), methodData.getName(), Arrays.toString(parameterTypes));
                }
            }
        }

        /*
         * Either the method cannot be looked up via reflection or looking up the method via
         * reflection failed. Now try to find the matching method.
         */
        if (!(type.getWrapped() instanceof BaseLayerType)) {
            if (name.equals(CLASS_INIT_NAME)) {
                type.getClassInitializer();
            } else {
                ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromArray(parameterTypes, returnType);
                tryLoadMethod(type, name, signature);
            }
        }

        if (!methods.containsKey(mid)) {
            createBaseLayerMethod(methodData, mid, name, parameterTypes, returnType);
        }
    }

    /**
     * Iterate through all methods to try to find and load one with a matching signature.
     *
     * We need this because sometimes JVMCI will expose to analysis special methods HotSpot
     * introduces into vtables, such as miranda and overpass methods, which cannot be accessed via
     * reflection.
     *
     * We also need this because reflection cannot find all declared methods within class; one
     * example it will not find is bridge methods inserted for covariant overrides.
     */
    private void tryLoadMethod(AnalysisType type, String name, ResolvedSignature<AnalysisType> signature) {
        ResolvedJavaType wrapped = type.getWrapped();
        assert !(wrapped instanceof BaseLayerType) : type;
        for (ResolvedJavaMethod method : wrapped.getAllMethods(false)) {
            /*
             * Filter to limit the number of universe lookups needed.
             */
            if (method.getName().equals(name)) {
                try {
                    ResolvedSignature<?> m = universe.lookup(method.getSignature(), method.getDeclaringClass());
                    if (m.equals(signature)) {
                        universe.lookup(method);
                        return;
                    }
                } catch (UnsupportedFeatureException t) {
                    /*
                     * Methods which are deleted or not available on this platform will throw an
                     * error during lookup - ignore and continue execution
                     *
                     * Note it is not simple to create a check to determine whether calling
                     * universe#lookup will trigger an error by creating an analysis object for a
                     * type not supported on this platform, as creating a method requires, in
                     * addition to the types of its return type and parameters, all of the super
                     * types of its return and parameters to be created as well.
                     */
                }
            }
        }
    }

    protected boolean delegateLoadMethod(PersistedAnalysisMethodData.Loader methodData) {
        WrappedMethod.Loader wrappedMethod = methodData.getWrappedMethod();
        if (wrappedMethod.isNone()) {
            return false;
        }
        if (wrappedMethod.isFactoryMethod()) {
            WrappedMethod.FactoryMethod.Loader fm = wrappedMethod.getFactoryMethod();
            AnalysisMethod analysisMethod = getAnalysisMethodForBaseLayerId(fm.getTargetConstructorId());
            if (analysisMethod.wrapped instanceof BaseLayerMethod) {
                return false;
            }
            AnalysisType instantiatedType = getAnalysisTypeForBaseLayerId(fm.getInstantiatedTypeId());
            FactoryMethodSupport.singleton().lookup(metaAccess, analysisMethod, instantiatedType, fm.getThrowAllocatedObject());
            return true;
        } else if (wrappedMethod.isCEntryPointCallStub()) {
            WrappedMethod.CEntryPointCallStub.Loader stub = wrappedMethod.getCEntryPointCallStub();
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
            WrappedMember.Loader wm = wrappedMethod.getWrappedMember();
            ResolvedJavaMethod member = getWrappedMember(wm);
            if (member == null) {
                return false;
            }
            AnalysisMethod analysisMethod = universe.lookup(member);
            if (wm.isReflectionExpandSignature()) {
                ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(analysisMethod.getJavaMethod());
            } else if (wm.isJavaCallVariantWrapper()) {
                JNIAccessFeature.singleton().addMethod(analysisMethod.getJavaMethod(), false, (FeatureImpl.DuringAnalysisAccessImpl) universe.getConcurrentAnalysisAccess());
            }
            return true;
        } else if (wrappedMethod.isPolymorphicSignature()) {
            int id = methodData.getId();
            WrappedMethod.PolymorphicSignature.Loader ps = wrappedMethod.getPolymorphicSignature();
            var callers = ps.getCallers();
            for (int i = 0; i < callers.size(); ++i) {
                loadMethodHandleTargets(getAnalysisMethodForBaseLayerId(callers.get(i)).wrapped, universe.getBigbang());
                if (methods.containsKey(id)) {
                    return true;
                }
            }
            if (LayeredImageOptions.LayeredImageDiagnosticOptions.LogLoadingFailures.getValue()) {
                LogUtils.warning("The PolymorphicSignature method %s.%s could not get loaded", methodData.getClassName(), methodData.getName());
            }
            return false;
        }
        return false;
    }

    private ResolvedJavaMethod getWrappedMember(WrappedMethod.WrappedMember.Loader memberData) {
        String className = memberData.getDeclaringClassName();
        ResolvedJavaType declaringClass = imageLayerBuildingSupport.lookupType(true, className);
        if (declaringClass == null) {
            return null;
        }
        String name = memberData.getName();
        ResolvedJavaType[] parameters = SnapshotAdapters.toArray(memberData.getArgumentTypeNames(), c -> imageLayerBuildingSupport.lookupType(false, c), ResolvedJavaType[]::new);
        return lookupMethodByJVMCIReflection(name, declaringClass, parameters);
    }

    private static ResolvedJavaMethod lookupMethodByJVMCIReflection(String name, ResolvedJavaType declaringClass, ResolvedJavaType[] argumentClasses) {
        try {
            ResolvedJavaMethod method;
            if (name.equals(CONSTRUCTOR_NAME)) {
                method = JVMCIReflectionUtil.getDeclaredConstructor(true, declaringClass, argumentClasses);
            } else {
                method = JVMCIReflectionUtil.getUniqueDeclaredMethod(true, declaringClass, name, argumentClasses);
            }
            return method;
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private void createBaseLayerMethod(PersistedAnalysisMethodData.Loader md, int mid, String name, AnalysisType[] parameterTypes, AnalysisType returnType) {
        AnalysisType type = getAnalysisTypeForBaseLayerId(md.getDeclaringTypeId());
        ResolvedSignature<AnalysisType> signature = ResolvedSignature.fromArray(parameterTypes, returnType);
        byte[] code = md.hasBytecode() ? md.getBytecode() : null;
        IntrinsicMethod methodHandleIntrinsic = !md.hasMethodHandleIntrinsicName() ? null
                        : IntrinsicMethod.valueOf(md.getMethodHandleIntrinsicName());
        AnnotationValue[] annotations = getAnnotations(md.getAnnotationList());

        baseLayerMethods.computeIfAbsent(mid,
                        _ -> new BaseLayerMethod(mid, type, name, md.getIsVarArgs(), md.getIsBridge(), signature, md.getCanBeStaticallyBound(), md.getIsConstructor(),
                                        md.getModifiers(), md.getIsSynthetic(), code, md.getBytecodeSize(), methodHandleIntrinsic, annotations));
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
        PersistedAnalysisMethodData.Loader methodData = getMethodData(analysisMethod);
        if (methodData == null) {
            /* The method was not reachable in the base image */
            return -1;
        }
        return methodData.getId();
    }

    @Override
    public void addBaseLayerMethod(AnalysisMethod analysisMethod) {
        methods.putIfAbsent(analysisMethod.getId(), analysisMethod);

        PersistedAnalysisMethodData.Loader md = getMethodData(analysisMethod);
        postTask(md.getIsVirtualRootMethod(), _ -> analysisMethod.registerAsVirtualRootMethod(PERSISTED));
        postTask(md.getIsDirectRootMethod(), _ -> analysisMethod.registerAsDirectRootMethod(PERSISTED));
        postTask(md.getIsInvoked(), _ -> analysisMethod.registerAsInvoked(PERSISTED));
        postTask(md.getIsImplementationInvoked(), _ -> analysisMethod.registerAsImplementationInvoked(PERSISTED));
        postTask(md.getIsIntrinsicMethod(), _ -> analysisMethod.registerAsIntrinsicMethod(PERSISTED));

        LayeredCompilationBehavior.Behavior compilationBehavior = LayeredCompilationBehavior.Behavior.values()[md.getCompilationBehaviorOrdinal()];
        analysisMethod.setCompilationBehavior(compilationBehavior);
    }

    private PersistedAnalysisMethodData.Loader getMethodData(AnalysisMethod analysisMethod) {
        if (analysisMethod.getWrapped() instanceof BaseLayerMethod m) {
            return findMethod(m.getBaseLayerId());
        }
        String descriptor = imageLayerSnapshotUtil.getMethodDescriptor(analysisMethod);
        Integer id = methodDescriptorToBaseLayerId.get(descriptor);
        return (id != null) ? findMethod(id) : null;
    }

    public SnapshotStructList.Loader<DynamicHubInfoData.Loader> getDynamicHubInfos() {
        return snapshot.getDynamicHubInfos();
    }

    public SnapshotStructList.Loader<CGlobalDataInfoData.Loader> getCGlobals() {
        return snapshot.getCGlobals();
    }

    public DynamicHubInfoData.Loader getDynamicHubInfo(AnalysisType aType) {
        DynamicHubInfoData.Loader result = SnapshotAdapters.binarySearchUnique(getBaseLayerTypeId(aType), snapshot.getDynamicHubInfos(), DynamicHubInfoData.Loader::getTypeId);
        assert result != null : aType;
        return result;
    }

    public SnapshotStructList.Loader<PersistedHostedMethodData.Loader> getHostedMethods() {
        return snapshot.getHostedMethods();
    }

    public PersistedHostedMethodData.Loader getHostedMethodData(int hMethodIndex) {
        var reader = snapshot.getHostedMethods().get(hMethodIndex);
        assert reader.getIndex() == hMethodIndex;
        return reader;
    }

    public PersistedHostedMethodData.Loader getHostedMethodData(AnalysisMethod aMethod) {
        var aMethodData = getMethodData(aMethod);
        return getHostedMethodData(aMethodData.getHostedMethodIndex());
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
        return hasGraph(analysisMethod, PersistedAnalysisMethodData.Loader::hasAnalysisGraphLocation);
    }

    @Override
    public AnalysisParsedGraph getAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethodData.Loader methodData = getMethodData(analysisMethod);
        boolean intrinsic = methodData.getAnalysisGraphIsIntrinsic();
        EncodedGraph analyzedGraph = getEncodedGraph(analysisMethod, methodData.getAnalysisGraphLocation());
        return new AnalysisParsedGraph(analyzedGraph, intrinsic);
    }

    public boolean hasStrengthenedGraph(AnalysisMethod analysisMethod) {
        return hasGraph(analysisMethod, PersistedAnalysisMethodData.Loader::hasStrengthenedGraphLocation);
    }

    public EncodedGraph getStrengthenedGraph(AnalysisMethod analysisMethod) {
        PersistedAnalysisMethodData.Loader methodData = getMethodData(analysisMethod);
        return getEncodedGraph(analysisMethod, methodData.getStrengthenedGraphLocation());
    }

    private boolean hasGraph(AnalysisMethod analysisMethod, Function<PersistedAnalysisMethodData.Loader, Boolean> hasGraphFunction) {
        var methodData = getMethodData(analysisMethod);
        if (methodData == null) {
            return false;
        }
        return hasGraphFunction.apply(methodData);
    }

    private EncodedGraph getEncodedGraph(AnalysisMethod analysisMethod, String location) {
        byte[] encodedAnalyzedGraph = graphStore.read(location);
        SVMImageLayerSnapshotUtil.AbstractSVMGraphDecoder decoder = imageLayerSnapshotUtil.getGraphDecoder(this, analysisMethod, universe.getSnippetReflection(), nodeClassMap);
        EncodedGraph encodedGraph = (EncodedGraph) ObjectCopier.decode(decoder, encodedAnalyzedGraph);
        for (int i = 0; i < encodedGraph.getNumObjects(); ++i) {
            if (buildingApplicationLayer && encodedGraph.getObject(i) instanceof LoadImageSingletonDataImpl data) {
                data.setApplicationLayerConstant();
            }
        }
        return encodedGraph;
    }

    /**
     * This method is needed to ensure all the base layer analysis elements from the strengthened
     * graph are created early enough and seen by the analysis. This is done by decoding the graph
     * using a decoder that loads analysis elements instead of hosted elements.
     */
    @Override
    public void loadPriorStrengthenedGraphAnalysisElements(AnalysisMethod analysisMethod) {
        if (hasStrengthenedGraph(analysisMethod)) {
            PersistedAnalysisMethodData.Loader methodData = getMethodData(analysisMethod);
            byte[] encodedAnalyzedGraph = graphStore.read(methodData.getStrengthenedGraphLocation());
            SnippetReflectionProvider snippetReflection = universe.getSnippetReflection();
            SVMImageLayerSnapshotUtil.AbstractSVMGraphDecoder decoder = imageLayerSnapshotUtil.getGraphHostedToAnalysisElementsDecoder(this, analysisMethod, snippetReflection, nodeClassMap);
            EncodedGraph graph = (EncodedGraph) ObjectCopier.decode(decoder,
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
                    constantLoader.loadMaterializedChildren(constant);
                }
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

    private PersistedAnalysisFieldData.Loader findField(int fid) {
        return SnapshotAdapters.binarySearchUnique(fid, snapshot.getFields(), PersistedAnalysisFieldData.Loader::getId);
    }

    private void loadField(PersistedAnalysisFieldData.Loader fieldData) {
        AnalysisType declaringClass = getAnalysisTypeForBaseLayerId(fieldData.getDeclaringTypeId());
        String className = fieldData.hasClassName() ? fieldData.getClassName() : null;
        int id = fieldData.getId();

        ResolvedJavaType clazz = className != null ? lookupBaseLayerTypeInHostVM(className) : declaringClass.getWrapped();
        if (clazz == null) {
            clazz = declaringClass.getWrapped();
        }

        ResolvedJavaField field;
        try {
            field = JVMCIReflectionUtil.getUniqueDeclaredField(true, clazz, fieldData.getName());
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
            universe.lookup(field);
        }
    }

    private BaseLayerField getBaseLayerField(int id) {
        PersistedAnalysisFieldData.Loader fieldData = findField(id);

        BaseLayerType declaringClass = getBaseLayerType(fieldData.getDeclaringTypeId());
        ResolvedJavaType type = getResolvedJavaTypeForBaseLayerId(fieldData.getTypeId());

        return getBaseLayerField(fieldData, id, declaringClass, type);
    }

    private BaseLayerField getBaseLayerField(PersistedAnalysisFieldData.Loader fd, int id, ResolvedJavaType declaringClass, ResolvedJavaType type) {
        return baseLayerFields.computeIfAbsent(id,
                        _ -> new BaseLayerField(id, fd.getName(), declaringClass, type, fd.getIsInternal(),
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
        PersistedAnalysisFieldData.Loader fieldData = getFieldData(analysisField);
        if (fieldData == null) {
            /* The field was not reachable in the base image */
            return -1;
        }
        return fieldData.getId();
    }

    @Override
    public void addBaseLayerField(AnalysisField analysisField) {
        fields.putIfAbsent(analysisField.getId(), analysisField);
        if (analysisField.isStatic()) {
            PersistedAnalysisFieldData.Loader fieldData = getFieldData(analysisField);
            assert fieldData != null : "The field should be in the base layer";
            layeredStaticFieldSupport.ensureInitializedFromFieldData(analysisField, fieldData);
        }
    }

    @Override
    public void initializeBaseLayerField(AnalysisField analysisField) {
        PersistedAnalysisFieldData.Loader fieldData = getFieldData(analysisField);
        assert fieldData != null : "The field should be in the base layer";

        boolean isAccessed = fieldData.getIsAccessed();
        boolean isRead = fieldData.getIsRead();
        if (!analysisField.isStatic() && (isAccessed || isRead)) {
            analysisField.getDeclaringClass().getInstanceFields(true);
        }
        postTask(isAccessed, _ -> {
            analysisField.injectDeclaredType();
            analysisField.registerAsAccessed(PERSISTED);
        });
        postTask(isRead, _ -> analysisField.registerAsRead(PERSISTED));
        postTask(fieldData.getIsWritten(), _ -> {
            analysisField.injectDeclaredType();
            analysisField.registerAsWritten(PERSISTED);
        });
        postTask(fieldData.getIsFolded(), _ -> analysisField.registerAsFolded(PERSISTED));
        postTask(fieldData.getIsUnsafeAccessed(), _ -> analysisField.registerAsUnsafeAccessed(PERSISTED));

        /*
         * Inject the base layer position. If the position computed for this layer, either before
         * this step or later, is different this will result in a failed guarantee.
         */
        analysisField.setPosition(fieldData.getPosition());
    }

    private PersistedAnalysisFieldData.Loader getFieldData(AnalysisField analysisField) {
        if (analysisField.wrapped instanceof BaseLayerField baseLayerField) {
            return findField(baseLayerField.getBaseLayerId());
        }
        String declTypeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(analysisField.getDeclaringClass());
        Integer declTypeId = typeDescriptorToBaseLayerId.get(declTypeDescriptor);
        if (declTypeId == null) {
            return null;
        }
        PersistedAnalysisTypeData.Loader typeData = findType(declTypeId);
        SnapshotPrimitiveList.Int.Loader fieldIds;
        if (analysisField.isStatic()) {
            fieldIds = typeData.getStaticFieldIds();
        } else {
            fieldIds = typeData.getInstanceFieldIds();
        }
        for (int i = 0; i < fieldIds.size(); i++) {
            PersistedAnalysisFieldData.Loader fieldData = findField(fieldIds.get(i));
            if (fieldData != null && analysisField.getName().equals(fieldData.getName())) {
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
        return constantLoader.hasValueForConstant(javaConstant);
    }

    @Override
    public ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        return constantLoader.getValueForConstant(javaConstant);
    }

    private DynamicHub typeToHub(AnalysisType type) {
        return ((SVMHost) universe.hostVM()).dynamicHub(type);
    }

    @Override
    public Set<Integer> getRelinkedFields(AnalysisType type) {
        return imageLayerSnapshotUtil.getRelinkedFields(type, universe);
    }

    public ImageHeapConstant getOrCreateConstant(int id) {
        return constantLoader.getOrCreateConstant(id);
    }

    /* Retrieves the given constant iff it has already been relinked. */
    public ImageHeapConstant getConstant(int id) {
        return constantLoader.getConstant(id);
    }

    public Long getObjectOffset(JavaConstant javaConstant) {
        return constantLoader.getObjectOffset(javaConstant);
    }

    public ImageHeapConstant getBaseLayerStaticPrimitiveFields() {
        return constantLoader.getBaseLayerStaticPrimitiveFields();
    }

    public ImageHeapConstant getBaseLayerStaticObjectFields() {
        return constantLoader.getBaseLayerStaticObjectFields();
    }

    public int getMaxTypeId() {
        return snapshot.getNextTypeId() - 1;
    }

    public long getImageHeapEndOffset() {
        return snapshot.getImageHeapEndOffset();
    }

    @Override
    public boolean hasDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.containsKey(tid);
    }

    @Override
    public int getDynamicHubIdentityHashCode(int tid) {
        return typeToHubIdentityHashCode.get(tid);
    }

    public void rescanHub(AnalysisType type, DynamicHub hub) {
        if (constantLoader.hasValueForHub(hub)) {
            ScanReason reason = new OtherReason("Manual hub rescan for " + hub.getName() + " triggered from " + SVMImageLayerLoader.class);
            universe.getHeapScanner().rescanObject(hub, reason);
            scanCompanionField(hub);
            universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.CLASS_INITIALIZATION_INFO, reason);
            if (type.getJavaKind() == JavaKind.Object) {
                if (type.isArray()) {
                    DynamicHub componentHub = hub.getComponentHub();
                    scanCompanionField(componentHub);
                    universe.getHeapScanner().rescanField(componentHub.getCompanion(), SVMImageLayerSnapshotUtil.ARRAY_HUB, reason);
                }
                universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.INTERFACES_ENCODING, reason);
                if (type.isEnum()) {
                    universe.getHeapScanner().rescanField(hub.getCompanion(), SVMImageLayerSnapshotUtil.ENUM_CONSTANTS_REFERENCE, reason);
                }
            }
        }
    }

    private void scanCompanionField(DynamicHub hub) {
        var instance = (ImageHeapInstance) constantLoader.getValueForHub(hub);
        instance.readFieldValue(universe.lookup(SVMImageLayerSnapshotUtil.COMPANION));
    }

    public boolean isReachableInPreviousLayer(AnalysisType type) {
        return getPropertyInPreviousLayer(type, PersistedAnalysisTypeData.Loader::getIsReachable);
    }

    public boolean isInstantiatedInPreviousLayer(AnalysisType type) {
        return getPropertyInPreviousLayer(type, PersistedAnalysisTypeData.Loader::getIsInstantiated);
    }

    private boolean getPropertyInPreviousLayer(AnalysisType type, Function<PersistedAnalysisTypeData.Loader, Boolean> propertyGetter) {
        Integer typeId;
        if (type.getWrapped() instanceof BaseLayerType baseLayerType) {
            typeId = baseLayerType.getBaseLayerId();
        } else {
            /*
             * Types that cannot be loaded manually can be duplicated and can get a new type id even
             * if they were in a shared layer. In this case, using the type identifier can still
             * retrieve the id from the shared layer.
             */
            String typeDescriptor = imageLayerSnapshotUtil.getTypeDescriptor(type);
            typeId = typeDescriptorToBaseLayerId.get(typeDescriptor);
        }
        if (typeId != null) {
            var typeInfo = findType(typeId);
            if (typeInfo != null) {
                return propertyGetter.apply(typeInfo);
            }
        }
        return false;
    }

    public record LayeredSimulationResult(boolean successful, EconomicMap<AnalysisField, JavaConstant> staticFieldValues) {
    }

    public LayeredSimulationResult getSimulationResult(AnalysisType type) {
        PersistedAnalysisTypeData.Loader typeData = findType(getBaseLayerTypeId(type));

        if (typeData.getIsSuccessfulSimulation()) {
            EconomicMap<AnalysisField, JavaConstant> staticFieldValues = EconomicMap.create();
            for (ResolvedJavaField field : type.getStaticFields()) {
                AnalysisField aField = (AnalysisField) field;
                PersistedAnalysisFieldData.Loader fieldData = getFieldData(aField);
                if (fieldData.hasSimulatedFieldValue()) {
                    JavaConstant simulatedFieldValue = readConstant(fieldData.getSimulatedFieldValue());
                    staticFieldValues.put(aField, simulatedFieldValue);
                }
            }
            return new LayeredSimulationResult(true, staticFieldValues);
        } else if (typeData.getIsFailedSimulation()) {
            return new LayeredSimulationResult(false, null);
        }
        return null;
    }

    public ClassInitializationInfo getClassInitializationInfo(AnalysisType aType) {
        PersistedAnalysisTypeData.Loader typeData = findType(getBaseLayerTypeId(aType));
        if (!typeData.getHasClassInitInfo()) {
            /* Type metadata was not initialized in base layer. */
            return null;
        }
        var initInfo = typeData.getClassInitializationInfo();
        if (initInfo.getIsInitialized() || initInfo.getIsInErrorState()) {
            ClassInitializationInfo.InitState initState = initInfo.getIsInitialized() ? FullyInitialized : InitializationError;
            return ClassInitializationInfo.forBuildTimeInitializedClass(initState, initInfo.getHasInitializer(), initInfo.getIsTracked());
        } else {
            assert initInfo.getIsLinked() : "Invalid state";
            int classInitializerId = initInfo.getInitializerMethodId();
            MethodPointer classInitializer = (classInitializerId == 0) ? null : new MethodPointer(getAnalysisMethodForBaseLayerId(classInitializerId));
            return ClassInitializationInfo.forRuntimeTimeInitializedClass(classInitializer, initInfo.getIsTracked());
        }
    }

    /**
     * Check that the class initialization info reconstructed from the loaded metadata matches the
     * info created in this layer. This doesn't do a complete equality check between
     * {@link ClassInitializationInfo} objects, just of fields related to the state.
     */
    public boolean isInitializationInfoStable(AnalysisType type, ClassInitializationInfo newInfo) {
        ClassInitializationInfo previousInfo = getClassInitializationInfo(type);
        if (previousInfo == null) {
            /* Type metadata was not initialized in base layer. */
            return true;
        }
        boolean equal = newInfo.getInitState() == previousInfo.getInitState() &&
                        newInfo.isBuildTimeInitialized() == previousInfo.isBuildTimeInitialized() &&
                        newInfo.isSlowPathRequired() == previousInfo.isSlowPathRequired() &&
                        newInfo.hasInitializer() == previousInfo.hasInitializer() &&
                        newInfo.getTypeReached() == previousInfo.getTypeReached();
        if (!equal) {
            Function<ClassInitializationInfo, String> asString = (info) -> "ClassInitializationInfo {" +
                            ", initState = " + info.getInitState() +
                            ", buildTimeInit = " + info.isBuildTimeInitialized() +
                            ", slowPathRequired = " + info.isSlowPathRequired() +
                            ", hasInitializer = " + info.hasInitializer() +
                            ", typeReached = " + info.getTypeReached() + '}';
            throw VMError.shouldNotReachHere("Class initialization info not stable between layers for type %s.\nPrevious info: %s.\nNew info: %s",
                            type, asString.apply(previousInfo), asString.apply(newInfo));
        }
        return true;
    }

    private JavaConstant readConstant(ConstantReferenceData.Loader constantReference) {
        return constantLoader.readConstant(constantReference);
    }

    public static class JavaConstantSupplier {
        private final ConstantReferenceData.Loader constantReference;

        JavaConstantSupplier(ConstantReferenceData.Loader constantReference) {
            this.constantReference = constantReference;
        }

        public JavaConstant get(SVMImageLayerLoader imageLayerLoader) {
            return imageLayerLoader.readConstant(constantReference);
        }

    }

    public static JavaConstantSupplier getConstant(ConstantReferenceData.Loader constantReference) {
        return new JavaConstantSupplier(constantReference);
    }

    public List<Integer> getUpdatableFieldReceiverIds(int fid) {
        var updatableReceivers = findField(fid).getUpdatableReceivers();
        return IntStream.range(0, updatableReceivers.size()).map(updatableReceivers::get).boxed().toList();
    }
}
