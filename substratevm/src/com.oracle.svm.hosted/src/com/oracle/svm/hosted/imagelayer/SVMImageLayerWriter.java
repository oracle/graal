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

import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.CONSTRUCTOR_NAME;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.GENERATED_SERIALIZATION;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_CONSTANT_ID;
import static com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil.UNDEFINED_FIELD_INDEX;
import static com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ClassInitializationInfo.Builder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.ImageLayerWriter;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.graal.code.CGlobalDataBasePointer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.traits.InjectedSingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageSingletonsSupportImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationMetadata;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.InitialLayerCGlobalTracking;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.AnnotationValue;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.DynamicHubInfo;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonKey;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonObject;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.KeyStoreEntry;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.EnumConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant.Object.Relinking.StringConstant;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
import com.oracle.svm.hosted.jni.JNIJavaCallVariantWrapperMethod;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.lambda.StableLambdaProxyNameFeature;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.hosted.methodhandles.MethodHandleFeature;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerSubstitutionType;
import com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxySubstitutionType;
import com.oracle.svm.hosted.substitute.PolymorphicSignatureWrapperMethod;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.shaded.org.capnproto.ListBuilder;
import com.oracle.svm.shaded.org.capnproto.MessageBuilder;
import com.oracle.svm.shaded.org.capnproto.PrimitiveList;
import com.oracle.svm.shaded.org.capnproto.Serialize;
import com.oracle.svm.shaded.org.capnproto.StructBuilder;
import com.oracle.svm.shaded.org.capnproto.StructList;
import com.oracle.svm.shaded.org.capnproto.Text;
import com.oracle.svm.shaded.org.capnproto.TextList;
import com.oracle.svm.shaded.org.capnproto.Void;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.NodeClassMap;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationType;

public class SVMImageLayerWriter extends ImageLayerWriter {
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageHeap imageHeap;
    private AnalysisUniverse aUniverse;
    private IdentityHashMap<String, String> internedStringsIdentityMap;

    private final MessageBuilder snapshotFileBuilder = new MessageBuilder();
    private final SharedLayerSnapshot.Builder snapshotBuilder = this.snapshotFileBuilder.initRoot(SharedLayerSnapshot.factory);
    private Map<ImageHeapConstant, ConstantParent> constantsMap;
    private final Map<AnalysisMethod, MethodGraphsInfo> methodsMap = new ConcurrentHashMap<>();
    /**
     * This map is only used for validation, to ensure that all method descriptors are unique. A
     * duplicate method descriptor would cause methods to be incorrectly matched across layers,
     * which is really hard to debug and can have unexpected consequences.
     */
    private final Map<String, AnalysisMethod> methodDescriptors = new HashMap<>();
    private final Map<AnalysisMethod, Set<AnalysisMethod>> polymorphicSignatureCallers = new ConcurrentHashMap<>();
    private final GraphsOutput graphsOutput;
    private final boolean useSharedLayerGraphs;
    private final boolean useSharedLayerStrengthenedGraphs;

    private NativeImageHeap nativeImageHeap;
    private HostedUniverse hUniverse;
    private final ClassInitializationSupport classInitializationSupport;
    private SimulateClassInitializerSupport simulateClassInitializerSupport;

    private boolean polymorphicSignatureSealed = false;

    /**
     * Used to encode {@link NodeClass} ids in {@link #persistGraph}.
     */
    private final NodeClassMap nodeClassMap = GraphEncoder.GLOBAL_NODE_CLASS_MAP;

    private record ConstantParent(int constantId, int index) {
        static ConstantParent NONE = new ConstantParent(UNDEFINED_CONSTANT_ID, UNDEFINED_FIELD_INDEX);
    }

    private record MethodGraphsInfo(String analysisGraphLocation, boolean analysisGraphIsIntrinsic,
                    String strengthenedGraphLocation) {

        static final MethodGraphsInfo NO_GRAPHS = new MethodGraphsInfo(null, false, null);

        MethodGraphsInfo withAnalysisGraph(AnalysisMethod method, String location, boolean isIntrinsic) {
            assert analysisGraphLocation == null && !analysisGraphIsIntrinsic : "Only one analysis graph can be persisted for a given method: " + method;
            return new MethodGraphsInfo(location, isIntrinsic, strengthenedGraphLocation);
        }

        MethodGraphsInfo withStrengthenedGraph(AnalysisMethod method, String location) {
            assert strengthenedGraphLocation == null : "Only one strengthened graph can be persisted for a given method: " + method;
            return new MethodGraphsInfo(analysisGraphLocation, analysisGraphIsIntrinsic, location);
        }
    }

    private static class GraphsOutput {
        private final FileChannel channel;

        private final AtomicLong currentOffset = new AtomicLong(0);

        GraphsOutput() {
            Path snapshotGraphsPath = HostedImageLayerBuildingSupport.singleton().getWriteLayerArchiveSupport().getSnapshotGraphsPath();
            try {
                Files.createFile(snapshotGraphsPath);
                channel = FileChannel.open(snapshotGraphsPath, EnumSet.of(StandardOpenOption.WRITE));
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Error opening temporary graphs file " + snapshotGraphsPath, e);
            }
        }

        String add(byte[] encodedGraph) {
            long offset = currentOffset.getAndAdd(encodedGraph.length);
            try {
                channel.write(ByteBuffer.wrap(encodedGraph), offset);
            } catch (Exception e) {
                throw GraalError.shouldNotReachHere(e, "Error during graphs file dumping.");
            }
            return new StringBuilder("@").append(offset).append("[").append(encodedGraph.length).append("]").toString();
        }

        void finish() {
            try {
                channel.close();
            } catch (Exception e) {
                throw VMError.shouldNotReachHere("Error during graphs file dumping.", e);
            }
        }
    }

    public SVMImageLayerWriter(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, boolean useSharedLayerGraphs, boolean useSharedLayerStrengthenedGraphs) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        this.useSharedLayerStrengthenedGraphs = useSharedLayerStrengthenedGraphs;
        graphsOutput = new GraphsOutput();
        this.classInitializationSupport = ClassInitializationSupport.singleton();
    }

    public void setInternedStringsIdentityMap(IdentityHashMap<String, String> map) {
        this.internedStringsIdentityMap = map;
    }

    public void setImageHeap(ImageHeap heap) {
        this.imageHeap = heap;
    }

    public void setAnalysisUniverse(AnalysisUniverse aUniverse) {
        this.aUniverse = aUniverse;
    }

    public void setSimulateClassInitializerSupport(SimulateClassInitializerSupport simulateClassInitializerSupport) {
        this.simulateClassInitializerSupport = simulateClassInitializerSupport;
    }

    public void setNativeImageHeap(NativeImageHeap nativeImageHeap) {
        this.nativeImageHeap = nativeImageHeap;
    }

    public void setHostedUniverse(HostedUniverse hUniverse) {
        this.hUniverse = hUniverse;
    }

    public void dumpFiles() {
        SVMImageLayerSnapshotUtil.SVMGraphEncoder graphEncoder = imageLayerSnapshotUtil.getGraphEncoder(null);
        byte[] encodedNodeClassMap = ObjectCopier.encode(graphEncoder, nodeClassMap);
        String location = graphsOutput.add(encodedNodeClassMap);
        snapshotBuilder.setNodeClassMapLocation(location);
        graphsOutput.finish();

        Path snapshotFile = HostedImageLayerBuildingSupport.singleton().getWriteLayerArchiveSupport().getSnapshotPath();
        try (FileOutputStream outputStream = new FileOutputStream(snapshotFile.toFile())) {
            Serialize.write(Channels.newChannel(outputStream), snapshotFileBuilder);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Unable to write " + snapshotFile, e);
        }
    }

    public void initializeExternalValues() {
        imageLayerSnapshotUtil.initializeExternalValues();
    }

    public void setEndOffset(long endOffset) {
        snapshotBuilder.setImageHeapEndOffset(endOffset);
    }

    @Override
    public void onTrackedAcrossLayer(AnalysisMethod method, Object reason) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            targetConstructor.registerAsTrackedAcrossLayers(reason);
        }
    }

    public void persistAnalysisInfo() {
        ImageHeapConstant staticPrimitiveFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields());
        ImageHeapConstant staticObjectFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getCurrentLayerStaticObjectFields());

        snapshotBuilder.setStaticPrimitiveFieldsConstantId(ImageHeapConstant.getConstantID(staticPrimitiveFields));
        snapshotBuilder.setStaticObjectFieldsConstantId(ImageHeapConstant.getConstantID(staticObjectFields));

        // Late constant scan so all of them are known with values available (readers installed)
        List<ImageHeapConstant> constantsToScan = new ArrayList<>();
        imageHeap.getReachableObjects().values().forEach(constantsToScan::addAll);
        constantsMap = HashMap.newHashMap(constantsToScan.size());
        constantsToScan.forEach(c -> constantsMap.put(c, ConstantParent.NONE));
        /*
         * Some child constants of reachable constants are not reachable because they are only used
         * in snippets, but still need to be persisted.
         */
        while (!constantsToScan.isEmpty()) {
            List<ImageHeapConstant> discoveredConstants = new ArrayList<>();
            constantsToScan.forEach(con -> scanConstantReferencedObjects(con, discoveredConstants));
            constantsToScan = discoveredConstants;
        }

        snapshotBuilder.setNextTypeId(aUniverse.getNextTypeId());
        snapshotBuilder.setNextMethodId(aUniverse.getNextMethodId());
        snapshotBuilder.setNextFieldId(aUniverse.getNextFieldId());
        snapshotBuilder.setNextConstantId(ImageHeapConstant.getCurrentId());

        polymorphicSignatureSealed = true;

        AnalysisType[] typesToPersist = aUniverse.getTypes().stream().filter(AnalysisType::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisType::getId))
                        .toArray(AnalysisType[]::new);
        initSortedArray(snapshotBuilder::initTypes, typesToPersist, this::persistType);
        var dispatchTableSingleton = LayeredDispatchTableFeature.singleton();
        initSortedArray(snapshotBuilder::initDynamicHubInfos, typesToPersist,
                        (AnalysisType aType, Supplier<DynamicHubInfo.Builder> builderSupplier) -> dispatchTableSingleton
                                        .persistDynamicHubInfo(hUniverse.lookup(aType), builderSupplier));

        AnalysisMethod[] methodsToPersist = aUniverse.getMethods().stream().filter(AnalysisMethod::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisMethod::getId))
                        .toArray(AnalysisMethod[]::new);
        initSortedArray(snapshotBuilder::initMethods, methodsToPersist, this::persistMethod);
        methodDescriptors.clear();

        AnalysisField[] fieldsToPersist = aUniverse.getFields().stream().filter(AnalysisField::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisField::getId))
                        .toArray(AnalysisField[]::new);
        initSortedArray(snapshotBuilder::initFields, fieldsToPersist, this::persistField);

        InitialLayerCGlobalTracking initialLayerCGlobalTracking = CGlobalDataFeature.singleton().getInitialLayerCGlobalTracking();
        initSortedArray(snapshotBuilder::initCGlobals, initialLayerCGlobalTracking.getInfosOrderedByIndex(), initialLayerCGlobalTracking::persistCGlobalInfo);

        /*
         * Note the set of elements within the hosted method array are created as a side effect of
         * persisting methods and dynamic hubs, so it must persisted after these operations.
         */
        HostedMethod[] hMethodsToPersist = dispatchTableSingleton.acquireHostedMethodArray();
        initSortedArray(snapshotBuilder::initHostedMethods, hMethodsToPersist, dispatchTableSingleton::persistHostedMethod);
        dispatchTableSingleton.releaseHostedMethodArray();

        @SuppressWarnings({"unchecked", "cast"})
        Map.Entry<ImageHeapConstant, ConstantParent>[] constantsToPersist = (Map.Entry<ImageHeapConstant, ConstantParent>[]) constantsMap.entrySet().stream()
                        .sorted(Comparator.comparingInt(a -> ImageHeapConstant.getConstantID(a.getKey())))
                        .toArray(Map.Entry[]::new);
        Set<Integer> constantsToRelink = new HashSet<>();
        initSortedArray(snapshotBuilder::initConstants, constantsToPersist,
                        (entry, bsupplier) -> persistConstant(entry.getKey(), entry.getValue(), bsupplier.get(), constantsToRelink));
        initInts(snapshotBuilder::initConstantsToRelink, constantsToRelink.stream().mapToInt(i -> i).sorted());
    }

    public static void initInts(IntFunction<PrimitiveList.Int.Builder> builderSupplier, IntStream ids) {
        int[] values = ids.toArray();
        PrimitiveList.Int.Builder builder = builderSupplier.apply(values.length);
        for (int i = 0; i < values.length; i++) {
            builder.set(i, values[i]);
        }
    }

    public static void initStringList(IntFunction<TextList.Builder> builderSupplier, Stream<String> strings) {
        Object[] array = strings.toArray();
        TextList.Builder builder = builderSupplier.apply(array.length);
        for (int i = 0; i < array.length; i++) {
            builder.set(i, new Text.Reader(array[i].toString()));
        }
    }

    public static <S extends StructBuilder, T> void initSortedArray(IntFunction<StructList.Builder<S>> init, T[] sortedArray, BiConsumer<T, Supplier<S>> action) {
        StructList.Builder<S> builder = init.apply(sortedArray.length);
        Iterator<S> iterator = builder.iterator();
        for (T t : sortedArray) {
            action.accept(t, iterator::next);
        }
        AnalysisError.guarantee(!iterator.hasNext(), "all created struct builders must have been used");
    }

    private void persistType(AnalysisType type, Supplier<PersistedAnalysisType.Builder> builderSupplier) {
        PersistedAnalysisType.Builder builder = builderSupplier.get();
        HostVM hostVM = aUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        builder.setHubIdentityHashCode(System.identityHashCode(hub));
        builder.setHasArrayType(hub.getArrayHub() != null);

        ClassInitializationInfo info = hub.getClassInitializationInfo();
        if (info == null) {
            /* Type metadata was not initialized. */
            assert !type.isReachable();
            builder.setHasClassInitInfo(false);
        } else {
            builder.setHasClassInitInfo(true);
            Builder b = builder.initClassInitializationInfo();
            b.setIsNoInitializerNoTracking(info == ClassInitializationInfo.forNoInitializerInfo(false));
            b.setIsInitializedNoTracking(info == ClassInitializationInfo.forInitializedInfo(false));
            b.setIsFailedNoTracking(info == ClassInitializationInfo.forFailedInfo(false));
            b.setIsInitialized(info.isInitialized());
            b.setIsInErrorState(info.isInErrorState());
            b.setIsLinked(info.isLinked());
            b.setHasInitializer(info.hasInitializer());
            b.setIsBuildTimeInitialized(info.isBuildTimeInitialized());
            b.setIsTracked(info.isTracked());
            FunctionPointerHolder classInitializer = info.getClassInitializer();
            if (classInitializer != null) {
                MethodPointer methodPointer = (MethodPointer) classInitializer.functionPointer;
                AnalysisMethod classInitializerMethod = (AnalysisMethod) methodPointer.getMethod();
                b.setInitializerMethodId(classInitializerMethod.getId());
            }
        }

        builder.setId(type.getId());
        builder.setDescriptor(imageLayerSnapshotUtil.getTypeDescriptor(type));

        initInts(builder::initFields, Arrays.stream(type.getInstanceFields(true)).mapToInt(f -> ((AnalysisField) f).getId()));
        builder.setClassJavaName(type.toJavaName());
        builder.setClassName(type.getName());
        builder.setModifiers(type.getModifiers());
        builder.setIsInterface(type.isInterface());
        builder.setIsEnum(type.isEnum());
        builder.setIsInitialized(type.isInitialized());
        boolean successfulSimulation = simulateClassInitializerSupport.isSuccessfulSimulation(type);
        boolean failedSimulation = simulateClassInitializerSupport.isFailedSimulation(type);
        VMError.guarantee(!(successfulSimulation && failedSimulation), "Class init simulation cannot be both successful and failed.");
        builder.setIsSuccessfulSimulation(successfulSimulation);
        builder.setIsFailedSimulation(failedSimulation);
        builder.setIsFailedInitialization(classInitializationSupport.isFailedInitialization(type.getJavaClass()));
        builder.setIsLinked(type.isLinked());
        if (type.getSourceFileName() != null) {
            builder.setSourceFileName(type.getSourceFileName());
        }
        try {
            AnalysisType enclosingType = type.getEnclosingType();
            if (enclosingType != null) {
                builder.setEnclosingTypeId(enclosingType.getId());
            }
        } catch (InternalError | TypeNotPresentException | LinkageError e) {
            /* Ignore missing type errors. */
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

        delegatePersistType(type, builder);

        Set<AnalysisType> subTypes = type.getSubTypes().stream().filter(AnalysisElement::isTrackedAcrossLayers).collect(Collectors.toSet());
        var subTypesBuilder = builder.initSubTypes(subTypes.size());
        int i = 0;
        for (AnalysisType subType : subTypes) {
            subTypesBuilder.set(i, subType.getId());
            i++;
        }
        builder.setIsAnySubtypeInstantiated(type.isAnySubtypeInstantiated());

        afterTypeAdded(type);
    }

    protected void delegatePersistType(AnalysisType type, PersistedAnalysisType.Builder builder) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            WrappedType.SerializationGenerated.Builder b = builder.getWrappedType().initSerializationGenerated();
            var key = SerializationSupport.currentLayer().getKeyFromConstructorAccessorClass(type.getJavaClass());
            b.setRawDeclaringClass(key.getDeclaringClass().getName());
            b.setRawTargetConstructor(key.getTargetConstructorClass().getName());
        } else if (LambdaUtils.isLambdaType(type)) {
            WrappedType.Lambda.Builder b = builder.getWrappedType().initLambda();
            b.setCapturingClass(LambdaUtils.capturingClass(type.toJavaName()));
        } else if (ProxyRenamingSubstitutionProcessor.isProxyType(type)) {
            builder.getWrappedType().setProxyType(Void.VOID);
        }
    }

    /**
     * Some types can have an unstable name between two different image builds. To avoid producing
     * wrong results, a warning should be printed if such types exist in the resulting image.
     */
    private static void afterTypeAdded(AnalysisType type) {
        /*
         * Lambda functions containing the same method invocations will return the same hash. They
         * will still have a different name, but in a multi threading context, the names can be
         * switched.
         */
        if (type.getWrapped() instanceof LambdaSubstitutionType lambdaSubstitutionType) {
            StableLambdaProxyNameFeature stableLambdaProxyNameFeature = ImageSingletons.lookup(StableLambdaProxyNameFeature.class);
            if (!stableLambdaProxyNameFeature.getLambdaSubstitutionProcessor().isNameAlwaysStable(lambdaSubstitutionType.getName())) {
                String message = "The lambda method " + lambdaSubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }
        /*
         * Method handle with the same inner method handles will return the same hash. They will
         * still have a different name, but in a multi threading context, the names can be switched.
         */
        if (type.getWrapped() instanceof MethodHandleInvokerSubstitutionType methodHandleSubstitutionType) {
            MethodHandleFeature methodHandleFeature = ImageSingletons.lookup(MethodHandleFeature.class);
            if (!methodHandleFeature.getMethodHandleSubstitutionProcessor().isNameAlwaysStable(methodHandleSubstitutionType.getName())) {
                String message = "The method handle " + methodHandleSubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }

        if (type.getWrapped() instanceof ProxySubstitutionType proxySubstitutionType) {
            if (!ProxyRenamingSubstitutionProcessor.isNameAlwaysStable(proxySubstitutionType.getName())) {
                String message = "The Proxy type " + proxySubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }
    }

    private static void handleNameConflict(String message) {
        if (SubstrateOptions.AbortOnNameConflict.getValue()) {
            throw VMError.shouldNotReachHere(message);
        } else {
            LogUtils.warning(message);
        }
    }

    private void persistMethod(AnalysisMethod method, Supplier<PersistedAnalysisMethod.Builder> builderSupplier) {
        PersistedAnalysisMethod.Builder builder = builderSupplier.get();
        MethodGraphsInfo graphsInfo = methodsMap.putIfAbsent(method, MethodGraphsInfo.NO_GRAPHS);
        Executable executable = method.getJavaMethod();

        if (executable != null) {
            initStringList(builder::initArgumentClassNames, Arrays.stream(executable.getParameterTypes()).map(Class::getName));
            builder.setClassName(executable.getDeclaringClass().getName());
        }

        String methodDescriptor = imageLayerSnapshotUtil.getMethodDescriptor(method);
        if (methodDescriptors.put(methodDescriptor, method) != null) {
            throw GraalError.shouldNotReachHere("The method descriptor should be unique, but %s got added twice.\nThe first method is %s and the second is %s."
                            .formatted(methodDescriptor, methodDescriptors.get(methodDescriptor), method));
        }
        builder.setDescriptor(methodDescriptor);
        builder.setDeclaringTypeId(method.getDeclaringClass().getId());
        initInts(builder::initArgumentTypeIds, method.getSignature().toParameterList(null).stream().mapToInt(AnalysisType::getId));
        builder.setId(method.getId());
        builder.setName(method.getName());
        builder.setReturnTypeId(method.getSignature().getReturnType().getId());
        builder.setIsVarArgs(method.isVarArgs());
        builder.setIsBridge(method.isBridge());
        builder.setCanBeStaticallyBound(method.canBeStaticallyBound());
        builder.setModifiers(method.getModifiers());
        builder.setIsConstructor(method.isConstructor());
        builder.setIsSynthetic(method.isSynthetic());
        builder.setIsDeclared(method.isDeclared());
        byte[] code = method.getCode();
        if (code != null) {
            builder.setBytecode(code);
        }
        builder.setBytecodeSize(method.getCodeSize());
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

        builder.setCompilationBehaviorOrdinal((byte) method.getCompilationBehavior().ordinal());

        if (graphsInfo != null && graphsInfo.analysisGraphLocation != null) {
            assert !method.isDelayed() : "The method " + method + " has an analysis graph, but is delayed to the application layer";
            builder.setAnalysisGraphLocation(graphsInfo.analysisGraphLocation);
            builder.setAnalysisGraphIsIntrinsic(graphsInfo.analysisGraphIsIntrinsic);
        }
        if (graphsInfo != null && graphsInfo.strengthenedGraphLocation != null) {
            assert !method.isDelayed() : "The method " + method + " has a strengthened graph, but is delayed to the application layer";
            builder.setStrengthenedGraphLocation(graphsInfo.strengthenedGraphLocation);
        }

        delegatePersistMethod(method, builder);

        HostedMethod hMethod = hUniverse.lookup(method);
        builder.setHostedMethodIndex(LayeredDispatchTableFeature.singleton().getPersistedHostedMethodIndex(hMethod));
    }

    protected void delegatePersistMethod(AnalysisMethod method, PersistedAnalysisMethod.Builder builder) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            WrappedMethod.FactoryMethod.Builder b = builder.getWrappedMethod().initFactoryMethod();
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            b.setTargetConstructorId(targetConstructor.getId());
            b.setThrowAllocatedObject(factoryMethod.throwAllocatedObject());
            AnalysisType instantiatedType = method.getUniverse().lookup(factoryMethod.getInstantiatedType());
            b.setInstantiatedTypeId(instantiatedType.getId());
        } else if (method.wrapped instanceof CEntryPointCallStubMethod cEntryPointCallStubMethod) {
            WrappedMethod.CEntryPointCallStub.Builder b = builder.getWrappedMethod().initCEntryPointCallStub();
            AnalysisMethod originalMethod = CEntryPointCallStubSupport.singleton().getMethodForStub(cEntryPointCallStubMethod);
            b.setOriginalMethodId(originalMethod.getId());
            b.setNotPublished(cEntryPointCallStubMethod.isNotPublished());
        } else if (method.wrapped instanceof ReflectionExpandSignatureMethod reflectionExpandSignatureMethod) {
            WrappedMethod.WrappedMember.Builder b = builder.getWrappedMethod().initWrappedMember();
            b.setReflectionExpandSignature(Void.VOID);
            Executable member = reflectionExpandSignatureMethod.getMember();
            persistMethodWrappedMember(b, member);
        } else if (method.wrapped instanceof JNIJavaCallVariantWrapperMethod jniJavaCallVariantWrapperMethod) {
            WrappedMethod.WrappedMember.Builder b = builder.getWrappedMethod().initWrappedMember();
            b.setJavaCallVariantWrapper(Void.VOID);
            Executable executable = jniJavaCallVariantWrapperMethod.getMember();
            persistMethodWrappedMember(b, executable);
        } else if (method.wrapped instanceof SubstitutionMethod substitutionMethod && substitutionMethod.getAnnotated() instanceof PolymorphicSignatureWrapperMethod) {
            WrappedMethod.PolymorphicSignature.Builder b = builder.getWrappedMethod().initPolymorphicSignature();
            Set<AnalysisMethod> callers = polymorphicSignatureCallers.get(method);
            var callersBuilder = b.initCallers(callers.size());
            int i = 0;
            for (AnalysisMethod caller : callers) {
                callersBuilder.set(i, caller.getId());
                i++;
            }
        }
    }

    private static void persistMethodWrappedMember(PersistedAnalysisMethod.WrappedMethod.WrappedMember.Builder b, Executable member) {
        b.setName(member instanceof Constructor<?> ? CONSTRUCTOR_NAME : member.getName());
        b.setDeclaringClassName(member.getDeclaringClass().getName());
        Parameter[] params = member.getParameters();
        TextList.Builder atb = b.initArgumentTypeNames(params.length);
        for (int i = 0; i < params.length; i++) {
            atb.set(i, new Text.Reader(params[i].getType().getName()));
        }
    }

    private void persistField(AnalysisField field, Supplier<PersistedAnalysisField.Builder> fieldBuilderSupplier) {
        PersistedAnalysisField.Builder builder = fieldBuilderSupplier.get();

        builder.setId(field.getId());
        builder.setDeclaringTypeId(field.getDeclaringClass().getId());
        builder.setName(field.getName());
        builder.setIsAccessed(field.getAccessedReason() != null);
        builder.setIsRead(field.getReadReason() != null);
        builder.setIsWritten(field.getWrittenReason() != null);
        builder.setIsFolded(field.getFoldedReason() != null);
        builder.setIsUnsafeAccessed(field.isUnsafeAccessed());

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

        HostedField hostedField = hUniverse.lookup(field);
        builder.setLocation(hostedField.getLocation());
        int fieldInstalledNum = MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;
        LayeredStaticFieldSupport.LayerAssignmentStatus assignmentStatus = LayeredStaticFieldSupport.singleton().getAssignmentStatus(field);
        if (hostedField.hasInstalledLayerNum()) {
            fieldInstalledNum = hostedField.getInstalledLayerNum();
            if (assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.UNDECIDED) {
                assignmentStatus = LayeredStaticFieldSupport.LayerAssignmentStatus.PRIOR_LAYER;
            } else {
                assert assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.APP_LAYER_REQUESTED ||
                                assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.APP_LAYER_DEFERRED : assignmentStatus;
            }
        }
        builder.setPriorInstalledLayerNum(fieldInstalledNum);
        builder.setAssignmentStatus(assignmentStatus.ordinal());

        persistAnnotations(field, builder::initAnnotationList);

        JavaConstant simulatedFieldValue = simulateClassInitializerSupport.getSimulatedFieldValue(field);
        writeConstant(simulatedFieldValue, builder.initSimulatedFieldValue());
    }

    private void persistAnnotations(AnnotatedElement annotatedElement, IntFunction<StructList.Builder<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Builder>> builder) {
        Class<? extends Annotation>[] annotationTypes = AnnotationAccess.getAnnotationTypes(annotatedElement);
        persistAnnotations(annotatedElement, annotationTypes, builder);
    }

    private void persistAnnotations(AnnotatedElement annotatedElement, Class<? extends Annotation>[] annotationTypes,
                    IntFunction<StructList.Builder<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Builder>> builder) {
        var b = builder.apply(annotationTypes.length);
        for (int i = 0; i < annotationTypes.length; i++) {
            Class<? extends Annotation> annotationClass = annotationTypes[i];
            SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Builder annotationBuilder = b.get(i);
            annotationBuilder.setTypeName(annotationClass.getName());
            Annotation annotation = AnnotationAccess.getAnnotation(annotatedElement, annotationClass);
            persistAnnotationValues(annotation, annotationClass, annotationBuilder::initValues);
        }
    }

    private void persistAnnotationValues(Annotation annotation, Class<? extends Annotation> annotationClass, IntFunction<StructList.Builder<AnnotationValue.Builder>> builder) {
        AnnotationType annotationType = AnnotationType.getInstance(annotationClass);
        EconomicMap<String, Object> members = EconomicMap.create();
        annotationType.members().forEach((memberName, memberAccessor) -> {
            try {
                String moduleName = memberAccessor.getDeclaringClass().getModule().getName();
                if (moduleName != null) {
                    ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, SVMImageLayerWriter.class, false, moduleName);
                }
                AnnotationMemberValue memberValue = AnnotationMemberValue.getMemberValue(annotation, memberName, memberAccessor, annotationType);
                Object value = memberValue.get(annotationType.memberTypes().get(memberName));
                members.put(memberName, value);
            } catch (AnnotationMetadata.AnnotationExtractionError e) {
                /* We skip the incorrect annotation */
            }
        });
        if (!members.isEmpty()) {
            var list = builder.apply(members.size());
            MapCursor<String, Object> cursor = members.getEntries();
            for (int i = 0; cursor.advance(); i++) {
                var b = list.get(i);
                b.setName(cursor.getKey());
                Object v = cursor.getValue();
                persistAnnotationValue(v, b);
            }
        }
    }

    private void persistAnnotationValue(Object v, AnnotationValue.Builder b) {
        if (v.getClass().isArray()) {
            if (v instanceof Object[] array) {
                var ba = b.initMembers();
                ba.setClassName(v.getClass().getComponentType().getName());
                var bav = ba.initMemberValues(array.length);
                for (int i = 0; i < array.length; ++i) {
                    persistAnnotationValue(array[i], bav.get(i));
                }
            } else {
                Class<?> componentType = v.getClass().getComponentType();
                assert componentType.isPrimitive() : v + " should be a primitive array";
                persistConstantPrimitiveArray(b.initPrimitiveArray(), JavaKind.fromJavaClass(componentType), v);
            }
        } else {
            switch (v) {
                case Boolean z -> setAnnotationPrimitiveValue(b, JavaKind.Boolean, z ? 1L : 0L);
                case Byte z -> setAnnotationPrimitiveValue(b, JavaKind.Byte, z);
                case Short s -> setAnnotationPrimitiveValue(b, JavaKind.Short, s);
                case Character c -> setAnnotationPrimitiveValue(b, JavaKind.Char, c);
                case Integer i -> setAnnotationPrimitiveValue(b, JavaKind.Int, i);
                case Float f -> setAnnotationPrimitiveValue(b, JavaKind.Float, Float.floatToRawIntBits(f));
                case Long j -> setAnnotationPrimitiveValue(b, JavaKind.Long, j);
                case Double d -> setAnnotationPrimitiveValue(b, JavaKind.Double, Double.doubleToRawLongBits(d));
                case Class<?> clazz -> b.setClassName(clazz.getName());
                case Annotation innerAnnotation ->
                    persistAnnotationValues(innerAnnotation, innerAnnotation.annotationType(), b.initAnnotation()::initValues);
                case String s -> b.setString(s);
                case Enum<?> e -> {
                    var ba = b.initEnum();
                    ba.setClassName(e.getDeclaringClass().getName());
                    ba.setName(e.name());
                }
                default -> throw AnalysisError.shouldNotReachHere("Unknown annotation value: " + v);
            }
        }
    }

    private static void setAnnotationPrimitiveValue(AnnotationValue.Builder b, JavaKind kind, long rawValue) {
        var pv = b.initPrimitive();
        pv.setTypeChar(NumUtil.safeToUByte(kind.getTypeChar()));
        pv.setRawValue(rawValue);
    }

    private void persistConstant(ImageHeapConstant imageHeapConstant, ConstantParent parent, PersistedConstant.Builder builder, Set<Integer> constantsToRelink) {
        ObjectInfo objectInfo = nativeImageHeap.getConstantInfo(imageHeapConstant);
        builder.setObjectOffset((objectInfo == null) ? -1 : objectInfo.getOffset());

        int id = ImageHeapConstant.getConstantID(imageHeapConstant);
        builder.setId(id);
        AnalysisType type = imageHeapConstant.getType();
        AnalysisError.guarantee(type.isTrackedAcrossLayers(), "Type %s from constant %s should have been marked as trackedAcrossLayers, but was not", type, imageHeapConstant);
        builder.setTypeId(type.getId());

        IdentityHashCodeProvider identityHashCodeProvider = (IdentityHashCodeProvider) aUniverse.getBigbang().getConstantReflectionProvider();
        int identityHashCode = identityHashCodeProvider.identityHashCode(imageHeapConstant);
        builder.setIdentityHashCode(identityHashCode);

        switch (imageHeapConstant) {
            case ImageHeapInstance imageHeapInstance -> {
                builder.initObject().setInstance(Void.VOID);
                persistConstantObjectData(builder.getObject(), imageHeapInstance::getFieldValue, imageHeapInstance.getFieldValuesSize());
                persistConstantRelinkingInfo(builder, imageHeapConstant, constantsToRelink, aUniverse.getBigbang());
            }
            case ImageHeapObjectArray imageHeapObjectArray -> {
                builder.initObject().setObjectArray(Void.VOID);
                persistConstantObjectData(builder.getObject(), imageHeapObjectArray::getElement, imageHeapObjectArray.getLength());
            }
            case ImageHeapPrimitiveArray imageHeapPrimitiveArray ->
                persistConstantPrimitiveArray(builder.initPrimitiveData(), imageHeapPrimitiveArray.getType().getComponentType().getJavaKind(), imageHeapPrimitiveArray.getArray());
            case ImageHeapRelocatableConstant relocatableConstant ->
                builder.initRelocatable().setKey(relocatableConstant.getConstantData().key);
            default -> throw AnalysisError.shouldNotReachHere("Unexpected constant type " + imageHeapConstant);
        }

        if (!constantsToRelink.contains(id) && parent != ConstantParent.NONE) {
            builder.setParentConstantId(parent.constantId);
            assert parent.index != UNDEFINED_FIELD_INDEX : "Tried to persist child constant %s from parent constant %d, but got index %d".formatted(imageHeapConstant, parent.constantId, parent.index);
            builder.setParentIndex(parent.index);
        }
    }

    private void persistConstantRelinkingInfo(PersistedConstant.Builder builder, ImageHeapConstant imageHeapConstant, Set<Integer> constantsToRelink, BigBang bb) {
        Class<?> clazz = imageHeapConstant.getType().getJavaClass();
        JavaConstant hostedObject = imageHeapConstant.getHostedObject();
        boolean simulated = hostedObject == null;
        builder.setIsSimulated(simulated);
        if (!simulated) {
            Relinking.Builder relinkingBuilder = builder.getObject().getRelinking();
            int id = ImageHeapConstant.getConstantID(imageHeapConstant);
            ResolvedJavaType type = bb.getConstantReflectionProvider().asJavaType(hostedObject);
            boolean tryStaticFinalFieldRelink = true;
            if (type instanceof AnalysisType analysisType) {
                relinkingBuilder.initClassConstant().setTypeId(analysisType.getId());
                constantsToRelink.add(id);
                tryStaticFinalFieldRelink = false;
            } else if (clazz.equals(String.class)) {
                StringConstant.Builder stringConstantBuilder = relinkingBuilder.initStringConstant();
                String value = bb.getSnippetReflectionProvider().asObject(String.class, hostedObject);
                if (internedStringsIdentityMap.containsKey(value)) {
                    /*
                     * Interned strings must be relinked.
                     */
                    stringConstantBuilder.setValue(value);
                    constantsToRelink.add(id);
                    tryStaticFinalFieldRelink = false;
                }
            } else if (Enum.class.isAssignableFrom(clazz)) {
                EnumConstant.Builder enumBuilder = relinkingBuilder.initEnumConstant();
                Enum<?> value = bb.getSnippetReflectionProvider().asObject(Enum.class, hostedObject);
                enumBuilder.setEnumClass(value.getDeclaringClass().getName());
                enumBuilder.setEnumName(value.name());
                constantsToRelink.add(id);
                tryStaticFinalFieldRelink = false;
            }
            if (tryStaticFinalFieldRelink && shouldRelinkConstant(imageHeapConstant) && imageHeapConstant.getOrigin() != null) {
                AnalysisField field = imageHeapConstant.getOrigin();
                if (shouldRelinkField(field)) {
                    Relinking.FieldConstant.Builder fieldConstantBuilder = relinkingBuilder.initFieldConstant();
                    fieldConstantBuilder.setOriginFieldId(field.getId());
                    fieldConstantBuilder.setRequiresLateLoading(requiresLateLoading(imageHeapConstant, field));
                }
            }
        }
    }

    private boolean shouldRelinkConstant(ImageHeapConstant heapConstant) {
        /*
         * FastThreadLocals need to be registered by the object replacer and relinking constants
         * from the CrossLayerRegistry would skip the custom code associated.
         */
        Object o = aUniverse.getHostedValuesProvider().asObject(Object.class, heapConstant.getHostedObject());
        return !(o instanceof FastThreadLocal) && !CrossLayerConstantRegistryFeature.singleton().isConstantRegistered(o);
    }

    private static boolean shouldRelinkField(AnalysisField field) {
        return !AnnotationAccess.isAnnotationPresent(field, Delete.class) &&
                        ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(field.getDeclaringClass()) &&
                        field.isStatic() && field.isFinal() && field.isTrackedAcrossLayers() && field.installableInLayer();
    }

    private static boolean requiresLateLoading(ImageHeapConstant imageHeapConstant, AnalysisField field) {
        /*
         * CustomSubstitutionTypes need to be loaded after the substitution are installed.
         *
         * Intercepted fields need to be loaded after the interceptor is installed.
         */
        return imageHeapConstant.getType().getWrapped() instanceof CustomSubstitutionType ||
                        FieldValueInterceptionSupport.hasFieldValueInterceptor(field);
    }

    private static void persistConstantPrimitiveArray(PrimitiveArray.Builder builder, JavaKind componentKind, Object array) {
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

    private void persistConstantObjectData(PersistedConstant.Object.Builder builder, IntFunction<Object> valuesFunction, int size) {
        StructList.Builder<ConstantReference.Builder> refsBuilder = builder.initData(size);
        for (int i = 0; i < size; ++i) {
            Object object = valuesFunction.apply(i);
            ConstantReference.Builder b = refsBuilder.get(i);
            if (delegateProcessing(b, object)) {
                /* The object was already persisted */
                continue;
            }
            if (object instanceof JavaConstant javaConstant && maybeWriteConstant(javaConstant, b)) {
                continue;
            }
            AnalysisError.guarantee(object instanceof AnalysisFuture<?>, "Unexpected constant %s", object);
            b.setNotMaterialized(Void.VOID);
        }
    }

    private boolean maybeWriteConstant(JavaConstant constant, ConstantReference.Builder builder) {
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            assert constantsMap.containsKey(imageHeapConstant);
            var ocb = builder.initObjectConstant();
            ocb.setConstantId(ImageHeapConstant.getConstantID(imageHeapConstant));
        } else if (constant instanceof PrimitiveConstant primitiveConstant) {
            var pb = builder.initPrimitiveValue();
            pb.setTypeChar(NumUtil.safeToUByte(primitiveConstant.getJavaKind().getTypeChar()));
            pb.setRawValue(primitiveConstant.getRawValue());
        } else if (constant == JavaConstant.NULL_POINTER) {
            builder.setNullPointer(Void.VOID);
        } else {
            return false;
        }
        return true;
    }

    private static boolean delegateProcessing(ConstantReference.Builder builder, Object constant) {
        if (constant instanceof PatchedWordConstant patchedWordConstant) {
            WordBase word = patchedWordConstant.getWord();
            if (word instanceof MethodRef methodRef) {
                AnalysisMethod method = getRelocatableConstantMethod(methodRef);
                switch (methodRef) {
                    case MethodOffset mo -> builder.initMethodOffset().setMethodId(method.getId());
                    case MethodPointer mp -> builder.initMethodPointer().setMethodId(method.getId());
                    default -> throw VMError.shouldNotReachHere("Unsupported method ref: " + methodRef);
                }
                return true;
            } else if (word instanceof CEntryPointLiteralCodePointer cp) {
                CEntryPointLiteralReference.Builder b = builder.initCEntryPointLiteralCodePointer();
                b.setMethodName(cp.methodName);
                b.setDefiningClass(cp.definingClass.getName());
                b.initParameterNames(cp.parameterTypes.length);
                for (int i = 0; i < cp.parameterTypes.length; i++) {
                    b.getParameterNames().set(i, new Text.Reader(cp.parameterTypes[i].getName()));
                }
                return true;
            } else if (word instanceof CGlobalDataBasePointer) {
                builder.setCGlobalDataBasePointer(Void.VOID);
                return true;
            }
        }
        return false;
    }

    private void scanConstantReferencedObjects(ImageHeapConstant constant, Collection<ImageHeapConstant> discoveredConstants) {
        if (Objects.requireNonNull(constant) instanceof ImageHeapInstance instance) {
            scanConstantReferencedObjects(constant, instance::getFieldValue, instance.getFieldValuesSize(), discoveredConstants);
        } else if (constant instanceof ImageHeapObjectArray objArray) {
            scanConstantReferencedObjects(constant, objArray::getElement, objArray.getLength(), discoveredConstants);
        }
    }

    private void scanConstantReferencedObjects(ImageHeapConstant constant, IntFunction<Object> referencedObjectFunction, int size, Collection<ImageHeapConstant> discoveredConstants) {
        for (int i = 0; i < size; i++) {
            AnalysisType parentType = constant.getType();
            Object obj = referencedObjectFunction.apply(i);
            if (obj instanceof ImageHeapConstant con && !constantsMap.containsKey(con)) {
                /*
                 * Some constants are not in imageHeap#reachableObjects, but are still created in
                 * reachable constants. They can be created in the extension image, but should not
                 * be used.
                 */
                Set<Integer> relinkedFields = imageLayerSnapshotUtil.getRelinkedFields(parentType, aUniverse.getBigbang().getMetaAccess());
                ConstantParent parent = relinkedFields.contains(i) ? new ConstantParent(ImageHeapConstant.getConstantID(constant), i) : ConstantParent.NONE;

                discoveredConstants.add(con);
                constantsMap.put(con, parent);
            } else if (obj instanceof MethodRef mr) {
                getRelocatableConstantMethod(mr).registerAsTrackedAcrossLayers("In method ref");
            }
        }
    }

    private static AnalysisMethod getRelocatableConstantMethod(MethodRef methodRef) {
        ResolvedJavaMethod method = methodRef.getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return hostedMethod.wrapped;
        } else {
            return (AnalysisMethod) method;
        }
    }

    @Override
    public void persistAnalysisParsedGraph(AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        /*
         * A copy of the encoded graph is needed here because the nodeStartOffsets can be
         * concurrently updated otherwise, which causes the ObjectCopier to fail.
         */
        String location = persistGraph(method, new EncodedGraph(analysisParsedGraph.getEncodedGraph()));
        if (location != null) {
            /*
             * This method should only be called once for each method. This check is performed by
             * withAnalysisGraph as it will throw if the MethodGraphsInfo already has an analysis
             * graph.
             */
            methodsMap.compute(method, (n, mgi) -> (mgi != null ? mgi : MethodGraphsInfo.NO_GRAPHS)
                            .withAnalysisGraph(method, location, analysisParsedGraph.isIntrinsic()));
        }
    }

    public void persistMethodStrengthenedGraph(AnalysisMethod method) {
        if (!useSharedLayerStrengthenedGraphs) {
            return;
        }

        EncodedGraph analyzedGraph = method.getAnalyzedGraph();
        String location = persistGraph(method, analyzedGraph);
        /*
         * This method should only be called once for each method. This check is performed by
         * withStrengthenedGraph as it will throw if the MethodGraphsInfo already has a strengthened
         * graph.
         */
        methodsMap.compute(method, (n, mgi) -> (mgi != null ? mgi : MethodGraphsInfo.NO_GRAPHS).withStrengthenedGraph(method, location));
    }

    private String persistGraph(AnalysisMethod method, EncodedGraph analyzedGraph) {
        if (!useSharedLayerGraphs) {
            return null;
        }
        if (Arrays.stream(analyzedGraph.getObjects()).anyMatch(o -> o instanceof AnalysisFuture<?>)) {
            /*
             * GR-61103: After the AnalysisFuture in this node is handled, this check can be
             * removed.
             */
            return null;
        }
        byte[] encodedGraph = ObjectCopier.encode(imageLayerSnapshotUtil.getGraphEncoder(nodeClassMap), analyzedGraph);
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

    public void addPolymorphicSignatureCaller(AnalysisMethod polymorphicSignature, AnalysisMethod caller) {
        AnalysisError.guarantee(!polymorphicSignatureSealed, "The caller %s for method %s was added after the methods were persisted", caller, polymorphicSignature);
        polymorphicSignatureCallers.computeIfAbsent(polymorphicSignature, (m) -> ConcurrentHashMap.newKeySet()).add(caller);
    }

    record SingletonPersistInfo(LayeredImageSingleton.PersistFlags flags, int id, RecreateInfo recreateInfo, EconomicMap<String, Object> keyStore) {
    }

    // GR-66792 remove once no custom persist actions exist
    record RecreateInfo(String clazz, String method) {
    }

    RecreateInfo createRecreateInfo(SingletonLayeredCallbacks action) {
        if (action instanceof InjectedSingletonLayeredCallbacks injectAction) {
            // GR-66792 remove once no custom persist actions exist
            Class<?> singletonClass = injectAction.getSingletonClass();
            String recreateName = "createFromLoader";
            Method loaderMethod = ReflectionUtil.lookupMethod(true, singletonClass, recreateName, ImageSingletonLoader.class);
            if (loaderMethod == null) {
                throw VMError.shouldNotReachHere("Unable to find createFromLoader for %s", singletonClass);
            }
            return new RecreateInfo(singletonClass.getName(), recreateName);

        } else {
            return new RecreateInfo(action.getSingletonInstantiator().getName(), "");
        }
    }

    public void writeImageSingletonInfo(List<Map.Entry<Class<?>, ImageSingletonsSupportImpl.SingletonInfo>> layeredImageSingletons) {
        StructList.Builder<ImageSingletonKey.Builder> singletonsBuilder = snapshotBuilder.initSingletonKeys(layeredImageSingletons.size());
        Map<Object, SingletonPersistInfo> singletonPersistInfoMap = new HashMap<>();
        int nextID = 1;
        Set<Object> initialLayerSingletons = LayeredImageSingletonSupport.singleton().getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind.INITIAL_LAYER_ONLY);
        for (int i = 0; i < layeredImageSingletons.size(); i++) {
            var singletonEntry = layeredImageSingletons.get(i);
            String key = singletonEntry.getKey().getName();
            Object singleton = singletonEntry.getValue().singleton();
            boolean initialLayerOnly = initialLayerSingletons.contains(singleton);
            if (!singletonPersistInfoMap.containsKey(singleton)) {
                var writer = new ImageSingletonWriterImpl(snapshotBuilder, hUniverse);
                SingletonLayeredCallbacks action = (SingletonLayeredCallbacks) singletonEntry.getValue().traitMap().getTrait(SingletonTraitKind.LAYERED_CALLBACKS).get().metadata();
                var flags = action.doPersist(writer, singleton);
                boolean persistData = flags == LayeredImageSingleton.PersistFlags.CREATE;
                if (initialLayerOnly) {
                    VMError.guarantee(flags == LayeredImageSingleton.PersistFlags.FORBIDDEN, "InitialLayer Singleton's persist action must return %s %s", LayeredImageSingleton.PersistFlags.FORBIDDEN,
                                    singleton);
                }
                int id = -1;
                RecreateInfo recreateInfo = null;
                EconomicMap<String, Object> keyValueStore = null;
                if (persistData) {
                    id = nextID++;
                    recreateInfo = createRecreateInfo(action);
                    keyValueStore = writer.getKeyValueStore();
                }

                var info = new SingletonPersistInfo(flags, id, recreateInfo, keyValueStore);
                singletonPersistInfoMap.put(singleton, info);
            }
            var info = singletonPersistInfoMap.get(singleton);

            ImageSingletonKey.Builder sb = singletonsBuilder.get(i);
            sb.setKeyClassName(key);
            sb.setObjectId(info.id);
            sb.setPersistFlag(info.flags.ordinal());
            int constantId = -1;
            if (initialLayerOnly) {
                ImageHeapConstant imageHeapConstant = (ImageHeapConstant) aUniverse.getSnippetReflection().forObject(singleton);
                constantId = ImageHeapConstant.getConstantID(imageHeapConstant);
            }
            sb.setConstantId(constantId);
            sb.setIsInitialLayerOnly(initialLayerOnly);
        }

        var sortedByIDs = singletonPersistInfoMap.entrySet().stream()
                        .filter(e -> e.getValue().flags == LayeredImageSingleton.PersistFlags.CREATE)
                        .sorted(Comparator.comparingInt(e -> e.getValue().id))
                        .toList();
        StructList.Builder<ImageSingletonObject.Builder> objectsBuilder = snapshotBuilder.initSingletonObjects(sortedByIDs.size());
        for (int i = 0; i < sortedByIDs.size(); i++) {
            var entry = sortedByIDs.get(i);
            var info = entry.getValue();

            ImageSingletonObject.Builder ob = objectsBuilder.get(i);
            ob.setId(info.id);
            ob.setClassName(entry.getKey().getClass().getName());
            ob.setRecreateClass(info.recreateInfo().clazz());
            ob.setRecreateMethod(info.recreateInfo().method());
            writeImageSingletonKeyStore(ob, info.keyStore);
        }
    }

    private static void writeImageSingletonKeyStore(ImageSingletonObject.Builder objectData, EconomicMap<String, Object> keyStore) {
        StructList.Builder<KeyStoreEntry.Builder> lb = objectData.initStore(keyStore.size());
        MapCursor<String, Object> cursor = keyStore.getEntries();
        for (int i = 0; cursor.advance(); i++) {
            KeyStoreEntry.Builder b = lb.get(i);
            b.setKey(cursor.getKey());
            switch (cursor.getValue()) {
                case Integer iv -> b.getValue().setI(iv);
                case Long jv -> b.getValue().setJ(jv);
                case String str -> b.getValue().setStr(str);
                case int[] il -> {
                    PrimitiveList.Int.Builder ilb = b.getValue().initIl(il.length);
                    for (int j = 0; j < il.length; j++) {
                        ilb.set(j, il[j]);
                    }
                }
                case String[] strl -> {
                    TextList.Builder strlb = b.getValue().initStrl(strl.length);
                    for (int j = 0; j < strl.length; j++) {
                        strlb.set(j, new Text.Reader(strl[j]));
                    }
                }
                case boolean[] zl -> {
                    PrimitiveList.Boolean.Builder zlb = b.getValue().initZl(zl.length);
                    for (int j = 0; j < zl.length; j++) {
                        zlb.set(j, zl[j]);
                    }
                }
                default -> throw new IllegalStateException("Unexpected type: " + cursor.getValue());
            }
        }
    }

    public void writeConstant(JavaConstant constant, ConstantReference.Builder builder) {
        if (constant == null) {
            return;
        }
        if (!maybeWriteConstant(constant, builder)) {
            throw VMError.shouldNotReachHere("Unexpected constant: " + constant);
        }
    }

    public static class ImageSingletonWriterImpl implements ImageSingletonWriter {
        private final EconomicMap<String, Object> keyValueStore = EconomicMap.create();
        private final SharedLayerSnapshot.Builder snapshotBuilder;
        private final HostedUniverse hUniverse;

        ImageSingletonWriterImpl(SharedLayerSnapshot.Builder snapshotBuilder, HostedUniverse hUniverse) {
            this.snapshotBuilder = snapshotBuilder;
            this.hUniverse = hUniverse;
        }

        EconomicMap<String, Object> getKeyValueStore() {
            return keyValueStore;
        }

        public HostedUniverse getHostedUniverse() {
            return hUniverse;
        }

        private static boolean nonNullEntries(List<?> list) {
            return list.stream().filter(Objects::isNull).findAny().isEmpty();
        }

        @Override
        public void writeBoolList(String keyName, List<Boolean> value) {
            assert nonNullEntries(value);
            boolean[] b = new boolean[value.size()];
            for (int i = 0; i < value.size(); i++) {
                b[i] = value.get(i);
            }
            var previous = keyValueStore.put(keyName, b);
            assert previous == null : Assertions.errorMessage(keyName, previous);
        }

        @Override
        public void writeInt(String keyName, int value) {
            var previous = keyValueStore.put(keyName, value);
            assert previous == null : previous;
        }

        @Override
        public void writeIntList(String keyName, List<Integer> value) {
            assert nonNullEntries(value);
            var previous = keyValueStore.put(keyName, value.stream().mapToInt(i -> i).toArray());
            assert previous == null : Assertions.errorMessage(keyName, previous);
        }

        @Override
        public void writeLong(String keyName, long value) {
            var previous = keyValueStore.put(keyName, value);
            assert previous == null : Assertions.errorMessage(keyName, previous);
        }

        @Override
        public void writeString(String keyName, String value) {
            var previous = keyValueStore.put(keyName, value);
            assert previous == null : Assertions.errorMessage(keyName, previous);
        }

        @Override
        public void writeStringList(String keyName, List<String> value) {
            assert nonNullEntries(value);
            var previous = keyValueStore.put(keyName, value.toArray(String[]::new));
            assert previous == null : Assertions.errorMessage(keyName, previous);
        }

        public SharedLayerSnapshot.Builder getSnapshotBuilder() {
            return snapshotBuilder;
        }
    }
}
