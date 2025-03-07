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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
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
import java.util.stream.Collectors;
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
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

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
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.InitialLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.RuntimeOnlyWrapper;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationMetadata;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.AnnotationValue;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
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
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.methodhandles.MethodHandleFeature;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerSubstitutionType;
import com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxySubstitutionType;
import com.oracle.svm.hosted.substitute.PolymorphicSignatureWrapperMethod;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.util.FileDumpingUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.EncodedGraph;
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
    private final Map<String, MethodGraphsInfo> methodsMap = new ConcurrentHashMap<>();
    private final Map<InitialLayerOnlyImageSingleton, Integer> initialLayerOnlySingletonMap = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, Set<AnalysisMethod>> polymorphicSignatureCallers = new ConcurrentHashMap<>();
    private FileInfo fileInfo;
    private GraphsOutput graphsOutput;
    private final boolean useSharedLayerGraphs;
    private final boolean useSharedLayerStrengthenedGraphs;

    private NativeImageHeap nativeImageHeap;
    private HostedUniverse hUniverse;

    private boolean polymorphicSignatureSealed = false;

    private record ConstantParent(int constantId, int index) {
        static ConstantParent NONE = new ConstantParent(UNDEFINED_CONSTANT_ID, UNDEFINED_FIELD_INDEX);
    }

    private record FileInfo(Path layerFilePath, String fileName, String suffix) {
    }

    private record MethodGraphsInfo(String analysisGraphLocation, boolean analysisGraphIsIntrinsic,
                    String strengthenedGraphLocation) {

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

    public SVMImageLayerWriter(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, boolean useSharedLayerGraphs, boolean useSharedLayerStrengthenedGraphs) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        this.useSharedLayerStrengthenedGraphs = useSharedLayerStrengthenedGraphs;
    }

    public void setInternedStringsIdentityMap(IdentityHashMap<String, String> map) {
        this.internedStringsIdentityMap = map;
    }

    public void setImageHeap(ImageHeap heap) {
        this.imageHeap = heap;
    }

    public void setSnapshotFileInfo(Path layerSnapshotPath, String fileName, String suffix) {
        fileInfo = new FileInfo(layerSnapshotPath, fileName, suffix);
    }

    public void setAnalysisUniverse(AnalysisUniverse aUniverse) {
        this.aUniverse = aUniverse;
    }

    public void setNativeImageHeap(NativeImageHeap nativeImageHeap) {
        this.nativeImageHeap = nativeImageHeap;
    }

    public void setHostedUniverse(HostedUniverse hUniverse) {
        this.hUniverse = hUniverse;
    }

    public void openGraphsOutput(Path layerGraphsPath, String fileName, String suffix) {
        AnalysisError.guarantee(graphsOutput == null, "Graphs file has already been opened");
        graphsOutput = new GraphsOutput(layerGraphsPath, fileName, suffix);
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

        List<AnalysisType> typesToPersist = aUniverse.getTypes().stream().filter(AnalysisType::isTrackedAcrossLayers).toList();
        List<AnalysisMethod> methodsToPersist = aUniverse.getMethods().stream().filter(AnalysisMethod::isTrackedAcrossLayers).toList();
        List<AnalysisField> fieldsToPersist = aUniverse.getFields().stream().filter(AnalysisField::isTrackedAcrossLayers).toList();

        initSortedList(snapshotBuilder::initTypes, typesToPersist, Comparator.comparingInt(AnalysisType::getId), this::persistType);
        initSortedList(snapshotBuilder::initMethods, methodsToPersist, Comparator.comparingInt(AnalysisMethod::getId), this::persistMethod);
        initSortedList(snapshotBuilder::initFields, fieldsToPersist, Comparator.comparingInt(AnalysisField::getId), this::persistField);

        Set<Integer> constantsToRelink = new HashSet<>();
        initSortedList(snapshotBuilder::initConstants, constantsMap.entrySet(),
                        Comparator.comparingInt(a -> ImageHeapConstant.getConstantID(a.getKey())),
                        (entry, bsupplier) -> persistConstant(entry.getKey(), entry.getValue(), bsupplier.get(), constantsToRelink));
        initInts(snapshotBuilder::initConstantsToRelink, constantsToRelink.stream().mapToInt(i -> i).sorted());
    }

    private static void initInts(IntFunction<PrimitiveList.Int.Builder> builderSupplier, IntStream ids) {
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

    private static <S extends StructBuilder, T> void initSortedList(IntFunction<StructList.Builder<S>> init, Collection<T> objects, Comparator<T> comparator, BiConsumer<T, Supplier<S>> action) {
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

    private void persistType(AnalysisType type, Supplier<PersistedAnalysisType.Builder> builderSupplier) {
        PersistedAnalysisType.Builder builder = builderSupplier.get();
        HostVM hostVM = aUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        builder.setHubIdentityHashCode(System.identityHashCode(hub));
        builder.setHasArrayType(hub.getArrayHub() != null);

        builder.setIsInitializedAtBuildTime(ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(type));

        ClassInitializationInfo info = hub.getClassInitializationInfo();
        if (info != null) {
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
            var key = SerializationSupport.singleton().getKeyFromConstructorAccessorClass(type.getJavaClass());
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
        builder.setIsBridge(method.isBridge());
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

        delegatePersistMethod(method, builder);

        // register this method as persisted for name resolution
        HostedDynamicLayerInfo.singleton().recordPersistedMethod(hUniverse.lookup(method));
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

        HostedField hostedField = hUniverse.lookup(field);
        int location = hostedField.getLocation();
        if (location > 0) {
            builder.setLocation(location);
        }

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

        if (imageHeapConstant.isBackedByHostedObject() && InitialLayerOnlyImageSingleton.class.isAssignableFrom(type.getJavaClass())) {
            InitialLayerOnlyImageSingleton singleton = aUniverse.getBigbang().getSnippetReflectionProvider().asObject(InitialLayerOnlyImageSingleton.class, imageHeapConstant.getHostedObject());
            if (singleton.accessibleInFutureLayers()) {
                initialLayerOnlySingletonMap.put(singleton, id);
            }
        }

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
        return ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(field.getDeclaringClass()) &&
                        field.isStatic() && field.isFinal() && field.isTrackedAcrossLayers();
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
        if (constant instanceof RelocatableConstant relocatableConstant) {
            RelocatedPointer pointer = relocatableConstant.getPointer();
            if (pointer instanceof MethodPointer methodPointer) {
                AnalysisMethod method = getRelocatableConstantMethod(methodPointer);
                builder.initMethodPointer().setMethodId(method.getId());
                return true;
            } else if (pointer instanceof CEntryPointLiteralCodePointer cp) {
                CEntryPointLiteralReference.Builder b = builder.initCEntryPointLiteralCodePointer();
                b.setMethodName(cp.methodName);
                b.setDefiningClass(cp.definingClass.getName());
                b.initParameterNames(cp.parameterTypes.length);
                for (int i = 0; i < cp.parameterTypes.length; i++) {
                    b.getParameterNames().set(i, new Text.Reader(cp.parameterTypes[i].getName()));
                }
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
            } else if (obj instanceof MethodPointer mp) {
                getRelocatableConstantMethod(mp).registerAsTrackedAcrossLayers("In method pointer");
            }
        }
    }

    private static AnalysisMethod getRelocatableConstantMethod(MethodPointer methodPointer) {
        ResolvedJavaMethod method = methodPointer.getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return hostedMethod.wrapped;
        } else {
            return (AnalysisMethod) method;
        }
    }

    @Override
    public void persistAnalysisParsedGraph(AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        String name = imageLayerSnapshotUtil.getMethodDescriptor(method);
        MethodGraphsInfo graphsInfo = methodsMap.get(name);
        if (graphsInfo == null || graphsInfo.analysisGraphLocation == null) {
            /*
             * A copy of the encoded graph is needed here because the nodeStartOffsets can be
             * concurrently updated otherwise, which causes the ObjectCopier to fail.
             */
            String location = persistGraph(method, new EncodedGraph(analysisParsedGraph.getEncodedGraph()));
            if (location != null) {
                methodsMap.compute(name, (n, mgi) -> (mgi != null ? mgi : MethodGraphsInfo.NO_GRAPHS)
                                .withAnalysisGraph(location, analysisParsedGraph.isIntrinsic()));
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
        if (Arrays.stream(analyzedGraph.getObjects()).anyMatch(o -> o instanceof AnalysisFuture<?>)) {
            /*
             * GR-61103: After the AnalysisFuture in this node is handled, this check can be
             * removed.
             */
            return null;
        }
        byte[] encodedGraph = ObjectCopier.encode(imageLayerSnapshotUtil.getGraphEncoder(), analyzedGraph);
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

    record SingletonPersistInfo(LayeredImageSingleton.PersistFlags flags, int id, EconomicMap<String, Object> keyStore) {
    }

    public void writeImageSingletonInfo(List<Map.Entry<Class<?>, Object>> layeredImageSingletons) {
        StructList.Builder<ImageSingletonKey.Builder> singletonsBuilder = snapshotBuilder.initSingletonKeys(layeredImageSingletons.size());
        Map<LayeredImageSingleton, SingletonPersistInfo> singletonInfoMap = new HashMap<>();
        int nextID = 1;
        for (int i = 0; i < layeredImageSingletons.size(); i++) {
            var singletonInfo = layeredImageSingletons.get(i);
            LayeredImageSingleton singleton;
            if (singletonInfo.getValue() instanceof RuntimeOnlyWrapper wrapper) {
                singleton = wrapper.wrappedObject();
            } else {
                singleton = (LayeredImageSingleton) singletonInfo.getValue();
            }
            String key = singletonInfo.getKey().getName();
            if (!singletonInfoMap.containsKey(singleton)) {
                var writer = new ImageSingletonWriterImpl(snapshotBuilder);
                var flags = singleton.preparePersist(writer);
                boolean persistData = flags == LayeredImageSingleton.PersistFlags.CREATE;
                var info = new SingletonPersistInfo(flags, persistData ? nextID++ : -1, persistData ? writer.getKeyValueStore() : null);
                singletonInfoMap.put(singleton, info);
            }
            var info = singletonInfoMap.get(singleton);

            ImageSingletonKey.Builder sb = singletonsBuilder.get(i);
            sb.setKeyClassName(key);
            sb.setObjectId(info.id);
            sb.setPersistFlag(info.flags.ordinal());
            int constantId = -1;
            if (singleton instanceof InitialLayerOnlyImageSingleton initialLayerOnlyImageSingleton && initialLayerOnlyImageSingleton.accessibleInFutureLayers()) {
                constantId = initialLayerOnlySingletonMap.getOrDefault(initialLayerOnlyImageSingleton, -1);
            }
            sb.setConstantId(constantId);
        }

        var sortedByIDs = singletonInfoMap.entrySet().stream()
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

        ImageSingletonWriterImpl(SharedLayerSnapshot.Builder snapshotBuilder) {
            this.snapshotBuilder = snapshotBuilder;
        }

        EconomicMap<String, Object> getKeyValueStore() {
            return keyValueStore;
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
