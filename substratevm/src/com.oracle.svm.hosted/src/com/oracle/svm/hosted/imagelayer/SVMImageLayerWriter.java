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
import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initInts;
import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initSortedArray;
import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initStringList;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.ImageLayerWriter;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.LayeredImageOptions;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.InitialLayerCGlobalTracking;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.jni.JNIJavaCallVariantWrapperMethod;
import com.oracle.svm.hosted.lambda.LambdaProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerSubstitutionType;
import com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxySubstitutionType;
import com.oracle.svm.hosted.snapshot.capnproto.CapnProtoSharedLayerSnapshotFormat;
import com.oracle.svm.hosted.snapshot.constant.ConstantReferenceData;
import com.oracle.svm.hosted.snapshot.dynamichub.ClassInitializationInfoData;
import com.oracle.svm.hosted.snapshot.dynamichub.DynamicHubInfoData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisFieldData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData.WrappedMethod;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisTypeData.WrappedType;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotFormat;
import com.oracle.svm.hosted.snapshot.util.SnapshotStringList;
import com.oracle.svm.hosted.substitute.PolymorphicSignatureWrapperMethod;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredCompilationSupport;
import com.oracle.svm.sdk.staging.layeredimage.LayeredCompilationBehavior;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl.SingletonInfo;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;

public class SVMImageLayerWriter extends ImageLayerWriter {
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private ImageHeap imageHeap;
    private AnalysisUniverse aUniverse;
    private IdentityHashMap<String, String> internedStringsIdentityMap;

    private final SharedLayerSnapshotFormat sharedLayerSnapshotFormat = new CapnProtoSharedLayerSnapshotFormat();
    private final SharedLayerSnapshotData.Writer snapshotWriter = sharedLayerSnapshotFormat.createWriter();
    private SVMImageConstantSnapshotWriter constantSnapshotWriter;
    private final ImageLayerGraphWriter graphWriter;
    /**
     * This map is only used for validation, to ensure that all method descriptors are unique. A
     * duplicate method descriptor would cause methods to be incorrectly matched across layers,
     * which is really hard to debug and can have unexpected consequences.
     */
    private final Map<String, AnalysisMethod> methodDescriptors = new HashMap<>();
    private final Map<AnalysisMethod, Set<AnalysisMethod>> polymorphicSignatureCallers = new ConcurrentHashMap<>();

    private NativeImageHeap nativeImageHeap;
    private HostedUniverse hUniverse;
    private final ClassInitializationSupport classInitializationSupport;
    private SimulateClassInitializerSupport simulateClassInitializerSupport;

    private boolean polymorphicSignatureSealed = false;

    public SVMImageLayerWriter(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, boolean useSharedLayerGraphs, boolean useSharedLayerStrengthenedGraphs) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        Path snapshotGraphsPath = HostedImageLayerBuildingSupport.singleton().getWriteLayerArchiveSupport().getSnapshotGraphsPath();
        graphWriter = new ImageLayerGraphWriter(imageLayerSnapshotUtil, ImageLayerGraphStore.openForWriting(snapshotGraphsPath), useSharedLayerGraphs, useSharedLayerStrengthenedGraphs);
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
        graphWriter.writeNodeClassMap(snapshotWriter);

        Path snapshotFile = HostedImageLayerBuildingSupport.singleton().getWriteLayerArchiveSupport().getSnapshotPath();
        try {
            sharedLayerSnapshotFormat.write(snapshotFile, snapshotWriter);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Unable to write " + snapshotFile, e);
        }
    }

    public void initializeExternalValues() {
        imageLayerSnapshotUtil.initializeExternalValues();
    }

    public void setEndOffset(long endOffset) {
        snapshotWriter.setImageHeapEndOffset(endOffset);
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

        snapshotWriter.setStaticPrimitiveFieldsConstantId(ImageHeapConstant.getConstantID(staticPrimitiveFields));
        snapshotWriter.setStaticObjectFieldsConstantId(ImageHeapConstant.getConstantID(staticObjectFields));

        // Late constant scan so all of them are known with values available (readers installed)
        constantSnapshotWriter = new SVMImageConstantSnapshotWriter(imageLayerSnapshotUtil, nativeImageHeap, aUniverse, internedStringsIdentityMap);
        constantSnapshotWriter.collectConstants(imageHeap);

        snapshotWriter.setNextTypeId(aUniverse.getNextTypeId());
        snapshotWriter.setNextMethodId(aUniverse.getNextMethodId());
        snapshotWriter.setNextFieldId(aUniverse.getNextFieldId());
        snapshotWriter.setNextConstantId(ImageHeapConstant.getCurrentId());

        polymorphicSignatureSealed = true;

        AnalysisType[] typesToPersist = aUniverse.getTypes().stream().filter(AnalysisType::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisType::getId))
                        .toArray(AnalysisType[]::new);
        initSortedArray(snapshotWriter::initTypes, typesToPersist, this::persistType);
        var dispatchTableSingleton = LayeredDispatchTableFeature.singleton();
        initSortedArray(snapshotWriter::initDynamicHubInfos, typesToPersist,
                        (AnalysisType aType, Supplier<DynamicHubInfoData.Writer> builderSupplier) -> dispatchTableSingleton
                                        .persistDynamicHubInfo(hUniverse.lookup(aType), builderSupplier));

        AnalysisMethod[] methodsToPersist = aUniverse.getMethods().stream().filter(AnalysisMethod::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisMethod::getId))
                        .toArray(AnalysisMethod[]::new);
        initSortedArray(snapshotWriter::initMethods, methodsToPersist, this::persistMethod);
        methodDescriptors.clear();

        AnalysisField[] fieldsToPersist = aUniverse.getFields().stream().filter(AnalysisField::isTrackedAcrossLayers).sorted(Comparator.comparingInt(AnalysisField::getId))
                        .toArray(AnalysisField[]::new);
        initSortedArray(snapshotWriter::initFields, fieldsToPersist, this::persistField);

        InitialLayerCGlobalTracking initialLayerCGlobalTracking = CGlobalDataFeature.singleton().getInitialLayerCGlobalTracking();
        initSortedArray(snapshotWriter::initCGlobals, initialLayerCGlobalTracking.getInfosOrderedByIndex(), initialLayerCGlobalTracking::persistCGlobalInfo);

        /*
         * Note the set of elements within the hosted method array are created as a side effect of
         * persisting methods and dynamic hubs, so it must persisted after these operations.
         */
        HostedMethod[] hMethodsToPersist = dispatchTableSingleton.acquireHostedMethodArray();
        initSortedArray(snapshotWriter::initHostedMethods, hMethodsToPersist, dispatchTableSingleton::persistHostedMethod);
        dispatchTableSingleton.releaseHostedMethodArray();

        constantSnapshotWriter.writeConstants(snapshotWriter);
    }

    private void persistType(AnalysisType type, Supplier<PersistedAnalysisTypeData.Writer> builderSupplier) {
        PersistedAnalysisTypeData.Writer builder = builderSupplier.get();
        HostVM hostVM = aUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        builder.setHubIdentityHashCode(System.identityHashCode(hub));
        builder.setHasArrayType(hub.getArrayHub() != null);

        ClassInitializationInfo info = hub.getClassInitializationInfo();
        if (info == null) {
            /* Type metadata was not initialized. */
            assert !type.isReachable() : type;
            builder.setHasClassInitInfo(false);
        } else {
            builder.setHasClassInitInfo(true);
            ClassInitializationInfoData.Writer b = builder.initClassInitializationInfo();
            b.setIsInitialized(info.isInitialized());
            b.setIsInErrorState(info.isInErrorState());
            b.setIsLinked(info.isExactlyLinked());
            b.setHasInitializer(info.hasInitializer());
            b.setIsBuildTimeInitialized(info.isBuildTimeInitialized());
            b.setIsTracked(info.isTracked());
            FunctionPointerHolder classInitializer = info.getRuntimeClassInitializer();
            if (classInitializer != null) {
                MethodPointer methodPointer = (MethodPointer) classInitializer.functionPointer;
                VMError.guarantee(methodPointer.permitsRewriteToPLT() == MethodPointer.DEFAULT_PERMIT_REWRITE_TO_PLT, "Non-default value currently not supported, add in schema if needed");
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
        builder.setIsRecord(type.isRecord());
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
        AnnotationSnapshotCodec.writeAnnotations(type, builder::initAnnotationList);

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

    protected void delegatePersistType(AnalysisType type, PersistedAnalysisTypeData.Writer builder) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            WrappedType.SerializationGenerated.Writer b = builder.getWrappedType().initSerializationGenerated();
            var key = SerializationSupport.currentLayer().getKeyFromConstructorAccessorClass(type.getJavaClass());
            b.setRawDeclaringClassId(key.declaringClassId());
            b.setRawTargetConstructorId(key.targetConstructorClassId());
        } else if (LambdaUtils.isLambdaType(type)) {
            WrappedType.Lambda.Writer b = builder.getWrappedType().initLambda();
            b.setCapturingClass(LambdaUtils.capturingClass(type.toJavaName()));
            b.setCaptureSite(LambdaParser.findLambdaCaptureSite(OriginalClassProvider.getJavaClass(type)));
        } else if (ProxyRenamingSubstitutionProcessor.isProxyType(type)) {
            builder.getWrappedType().setProxyType();
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
            if (!LambdaProxyRenamingSubstitutionProcessor.singleton().isNameAlwaysStable(lambdaSubstitutionType.getName())) {
                String message = "The lambda method " + lambdaSubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }
        /*
         * Method handle with the same inner method handles will return the same hash. They will
         * still have a different name, but in a multi threading context, the names can be switched.
         */
        if (type.getWrapped() instanceof MethodHandleInvokerSubstitutionType methodHandleSubstitutionType) {
            if (!MethodHandleInvokerRenamingSubstitutionProcessor.singleton().isNameAlwaysStable(methodHandleSubstitutionType.getName())) {
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
        if (LayeredImageOptions.LayeredImageDiagnosticOptions.AbortOnNameConflict.getValue()) {
            throw VMError.shouldNotReachHere(message);
        } else if (LayeredImageOptions.LayeredImageDiagnosticOptions.LogOnNameConflict.getValue()) {
            LogUtils.warning(message);
        }
    }

    private void persistMethod(AnalysisMethod method, Supplier<PersistedAnalysisMethodData.Writer> builderSupplier) {
        PersistedAnalysisMethodData.Writer builder = builderSupplier.get();
        Executable executable = method.getJavaMethod();

        if (executable != null) {
            initStringList(builder::initArgumentClassNames, Arrays.stream(executable.getParameterTypes()).map(Class::getName));
            builder.setClassName(executable.getDeclaringClass().getName());
        }

        String methodDescriptor = imageLayerSnapshotUtil.getMethodDescriptor(method);
        if (methodDescriptors.putIfAbsent(methodDescriptor, method) != null) {
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
        AnnotationSnapshotCodec.writeAnnotations(method, builder::initAnnotationList);

        builder.setIsVirtualRootMethod(method.isVirtualRootMethod());
        builder.setIsDirectRootMethod(method.isDirectRootMethod());
        builder.setIsInvoked(method.isSimplyInvoked());
        builder.setIsImplementationInvoked(method.isSimplyImplementationInvoked());
        builder.setIsIntrinsicMethod(method.isIntrinsicMethod());

        var compilationBehavior = method.getCompilationBehavior();
        if (compilationBehavior == LayeredCompilationBehavior.Behavior.PINNED_TO_INITIAL_LAYER) {
            UserError.guarantee(hUniverse.lookup(method).isCompiled(), "User methods with layered compilation behavior %s must be registered via %s in the initial layer",
                            LayeredCompilationBehavior.Behavior.PINNED_TO_INITIAL_LAYER, LayeredCompilationSupport.class);
        }
        builder.setCompilationBehaviorOrdinal((byte) compilationBehavior.ordinal());

        graphWriter.writeMethodGraphs(method, builder);

        delegatePersistMethod(method, builder);

        HostedMethod hMethod = hUniverse.lookup(method);
        builder.setHostedMethodIndex(LayeredDispatchTableFeature.singleton().getPersistedHostedMethodIndex(hMethod));
    }

    protected void delegatePersistMethod(AnalysisMethod method, PersistedAnalysisMethodData.Writer builder) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            WrappedMethod.FactoryMethod.Writer b = builder.getWrappedMethod().initFactoryMethod();
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            b.setTargetConstructorId(targetConstructor.getId());
            b.setThrowAllocatedObject(factoryMethod.throwAllocatedObject());
            AnalysisType instantiatedType = method.getUniverse().lookup(factoryMethod.getInstantiatedType());
            b.setInstantiatedTypeId(instantiatedType.getId());
        } else if (method.wrapped instanceof CEntryPointCallStubMethod cEntryPointCallStubMethod) {
            WrappedMethod.CEntryPointCallStub.Writer b = builder.getWrappedMethod().initCEntryPointCallStub();
            AnalysisMethod originalMethod = CEntryPointCallStubSupport.singleton().getMethodForStub(cEntryPointCallStubMethod);
            b.setOriginalMethodId(originalMethod.getId());
            b.setNotPublished(cEntryPointCallStubMethod.isNotPublished());
        } else if (method.wrapped instanceof ReflectionExpandSignatureMethod reflectionExpandSignatureMethod) {
            WrappedMethod.WrappedMember.Writer b = builder.getWrappedMethod().initWrappedMember();
            b.setReflectionExpandSignature();
            Executable member = reflectionExpandSignatureMethod.getMember();
            persistMethodWrappedMember(b, member);
        } else if (method.wrapped instanceof JNIJavaCallVariantWrapperMethod jniJavaCallVariantWrapperMethod) {
            WrappedMethod.WrappedMember.Writer b = builder.getWrappedMethod().initWrappedMember();
            b.setJavaCallVariantWrapper();
            Executable executable = jniJavaCallVariantWrapperMethod.getMember();
            persistMethodWrappedMember(b, executable);
        } else if (method.wrapped instanceof SubstitutionMethod substitutionMethod && substitutionMethod.getAnnotated() instanceof PolymorphicSignatureWrapperMethod) {
            WrappedMethod.PolymorphicSignature.Writer b = builder.getWrappedMethod().initPolymorphicSignature();
            Set<AnalysisMethod> callers = polymorphicSignatureCallers.get(method);
            var callersBuilder = b.initCallers(callers.size());
            int i = 0;
            for (AnalysisMethod caller : callers) {
                callersBuilder.set(i, caller.getId());
                i++;
            }
        }
    }

    private static void persistMethodWrappedMember(PersistedAnalysisMethodData.WrappedMethod.WrappedMember.Writer b, Executable member) {
        b.setName(member instanceof Constructor<?> ? CONSTRUCTOR_NAME : member.getName());
        b.setDeclaringClassName(member.getDeclaringClass().getName());
        Parameter[] params = member.getParameters();
        SnapshotStringList.Writer atb = b.initArgumentTypeNames(params.length);
        for (int i = 0; i < params.length; i++) {
            atb.set(i, params[i].getType().getName());
        }
    }

    private void persistField(AnalysisField field, Supplier<PersistedAnalysisFieldData.Writer> fieldBuilderSupplier) {
        PersistedAnalysisFieldData.Writer builder = fieldBuilderSupplier.get();

        builder.setId(field.getId());
        builder.setDeclaringTypeId(field.getDeclaringClass().getId());
        builder.setName(field.getName());
        builder.setIsAccessed(field.getAccessedReason() != null);
        builder.setIsRead(field.getReadReason() != null);
        builder.setIsWritten(field.getWrittenReason() != null);
        builder.setIsFolded(field.getFoldedReason() != null);
        builder.setIsUnsafeAccessed(field.isUnsafeAccessed());
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
            if (assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.UNSPECIFIED) {
                assignmentStatus = LayeredStaticFieldSupport.LayerAssignmentStatus.PRIOR_LAYER;
            } else {
                assert assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.APP_LAYER_REQUESTED ||
                                assignmentStatus == LayeredStaticFieldSupport.LayerAssignmentStatus.APP_LAYER_DEFERRED : assignmentStatus;
            }
        }
        builder.setPriorInstalledLayerNum(fieldInstalledNum);
        builder.setAssignmentStatus(assignmentStatus.ordinal());

        var updatableReceivers = LayeredFieldValueTransformerSupport.singleton().getUpdatableReceivers(field);
        var receivers = builder.initUpdatableReceivers(updatableReceivers.size());
        int idx = 0;
        for (var receiver : updatableReceivers) {
            receivers.set(idx, ImageHeapConstant.getConstantID(receiver));
            idx++;
        }

        AnnotationSnapshotCodec.writeAnnotations(field, builder::initAnnotationList);

        JavaConstant simulatedFieldValue = simulateClassInitializerSupport.getSimulatedFieldValue(field);
        constantSnapshotWriter.writeConstant(simulatedFieldValue, builder.initSimulatedFieldValue());
    }

    @Override
    public void persistAnalysisParsedGraph(AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        graphWriter.persistAnalysisParsedGraph(method, analysisParsedGraph);
    }

    public void persistMethodStrengthenedGraph(AnalysisMethod method) {
        graphWriter.persistMethodStrengthenedGraph(method);
    }

    public void addPolymorphicSignatureCaller(AnalysisMethod polymorphicSignature, AnalysisMethod caller) {
        AnalysisError.guarantee(!polymorphicSignatureSealed, "The caller %s for method %s was added after the methods were persisted", caller, polymorphicSignature);
        polymorphicSignatureCallers.computeIfAbsent(polymorphicSignature, _ -> ConcurrentHashMap.newKeySet()).add(caller);
    }

    public void writeImageSingletonInfo(List<Entry<Class<?>, SingletonInfo>> layeredImageSingletons) {
        SVMImageSingletonSnapshotWriter.writeImageSingletonInfo(layeredImageSingletons, snapshotWriter, aUniverse, hUniverse);
    }

    public void writeConstant(JavaConstant constant, ConstantReferenceData.Writer builder) {
        constantSnapshotWriter.writeConstant(constant, builder);
    }

}
