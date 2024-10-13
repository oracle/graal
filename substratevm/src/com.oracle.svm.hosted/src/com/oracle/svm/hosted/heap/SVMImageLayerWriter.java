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
package com.oracle.svm.hosted.heap;

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.C_ENTRY_POINT_LITERAL_CODE_POINTER;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.HUB_IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_SINGLETON_KEYS;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_SINGLETON_OBJECTS;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_CLASS_INITIALIZER_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_HAS_INITIALIZER_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_IS_BUILD_TIME_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_IS_INITIALIZED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_IS_IN_ERROR_STATE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_IS_LINKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INFO_IS_TRACKED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_FAILED_NO_TRACKING_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_AT_BUILD_TIME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_NO_TRACKING_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_NO_INITIALIZER_NO_TRACKING_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LOCATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_POINTER_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_OFFSET_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_OBJECT_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_PRIMITIVE_FIELDS_TAG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.RuntimeOnlyWrapper;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.imagelayer.HostedDynamicLayerInfo;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.lambda.StableLambdaProxyNameFeature;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.methodhandles.MethodHandleFeature;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerSubstitutionType;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxySubstitutionType;
import com.oracle.svm.util.LogUtils;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SVMImageLayerWriter extends ImageLayerWriter {
    private NativeImageHeap nativeImageHeap;
    private HostedUniverse hUniverse;

    public SVMImageLayerWriter(boolean useSharedLayerGraphs) {
        super(useSharedLayerGraphs, new SVMImageLayerSnapshotUtil());
    }

    public void setNativeImageHeap(NativeImageHeap nativeImageHeap) {
        this.nativeImageHeap = nativeImageHeap;
    }

    public void setHostedUniverse(HostedUniverse hUniverse) {
        this.hUniverse = hUniverse;
    }

    @Override
    protected void persistHook() {
        ImageHeapConstant staticPrimitiveFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticPrimitiveFields());
        ImageHeapConstant staticObjectFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticObjectFields());

        jsonMap.put(STATIC_PRIMITIVE_FIELDS_TAG, getConstantId(staticPrimitiveFields));
        jsonMap.put(STATIC_OBJECT_FIELDS_TAG, getConstantId(staticObjectFields));
    }

    @Override
    protected void persistType(AnalysisType type, EconomicMap<String, Object> typeMap) {
        HostVM hostVM = aUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        typeMap.put(HUB_IDENTITY_HASH_CODE_TAG, System.identityHashCode(hub));

        typeMap.put(IS_INITIALIZED_AT_BUILD_TIME_TAG, ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(type));

        ClassInitializationInfo info = hub.getClassInitializationInfo();
        if (info != null) {
            typeMap.put(IS_NO_INITIALIZER_NO_TRACKING_TAG, info == ClassInitializationInfo.forNoInitializerInfo(false));
            typeMap.put(IS_INITIALIZED_NO_TRACKING_TAG, info == ClassInitializationInfo.forInitializedInfo(false));
            typeMap.put(IS_FAILED_NO_TRACKING_TAG, info == ClassInitializationInfo.forFailedInfo(false));
            typeMap.put(INFO_IS_INITIALIZED_TAG, info.isInitialized());
            typeMap.put(INFO_IS_IN_ERROR_STATE_TAG, info.isInErrorState());
            typeMap.put(INFO_IS_LINKED_TAG, info.isLinked());
            typeMap.put(INFO_HAS_INITIALIZER_TAG, info.hasInitializer());
            typeMap.put(INFO_IS_BUILD_TIME_INITIALIZED_TAG, info.isBuildTimeInitialized());
            typeMap.put(INFO_IS_TRACKED_TAG, info.isTracked());
            FunctionPointerHolder classInitializer = info.getClassInitializer();
            if (classInitializer != null) {
                MethodPointer methodPointer = (MethodPointer) classInitializer.functionPointer;
                AnalysisMethod classInitializerMethod = (AnalysisMethod) methodPointer.getMethod();
                typeMap.put(INFO_CLASS_INITIALIZER_TAG, classInitializerMethod.getId());
            }
        }

        super.persistType(type, typeMap);
    }

    @Override
    public void checkTypeStability(AnalysisType type) {
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

    @Override
    public void persistMethod(AnalysisMethod method, EconomicMap<String, Object> methodMap) {
        super.persistMethod(method, methodMap);

        // register this method as persisted for name resolution
        HostedDynamicLayerInfo.singleton().recordPersistedMethod(hUniverse.lookup(method));
    }

    @Override
    protected void persistField(AnalysisField field, EconomicMap<String, Object> fieldMap) {
        HostedField hostedField = hUniverse.lookup(field);
        int location = hostedField.getLocation();
        if (hostedField.isStatic() && location > 0) {
            fieldMap.put(LOCATION_TAG, location);
        }
        super.persistField(field, fieldMap);
    }

    @Override
    protected void persistConstant(ImageHeapConstant imageHeapConstant, EconomicMap<String, Object> constantMap) {
        ObjectInfo objectInfo = nativeImageHeap.getConstantInfo(imageHeapConstant);
        if (objectInfo != null) {
            constantMap.put(OBJECT_OFFSET_TAG, String.valueOf(objectInfo.getOffset()));
        }
        super.persistConstant(imageHeapConstant, constantMap);
    }

    @Override
    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id) {
        ResolvedJavaType type = bb.getConstantReflectionProvider().asJavaType(hostedObject);
        if (type instanceof AnalysisType analysisType) {
            constantMap.put(CLASS_ID_TAG, analysisType.getId());
            constantsToRelink.add(id);
        } else {
            super.persistConstantRelinkingInfo(constantMap, bb, clazz, hostedObject, id);
        }
    }

    @Override
    protected boolean delegateProcessing(List<List<Object>> data, Object constant) {
        if (constant instanceof RelocatableConstant relocatableConstant) {
            RelocatedPointer pointer = relocatableConstant.getPointer();
            if (pointer instanceof MethodPointer methodPointer) {
                data.add(List.of(METHOD_POINTER_TAG, getRelocatableConstantMethodId(methodPointer)));
                return true;
            } else if (pointer instanceof CEntryPointLiteralCodePointer cEntryPointLiteralCodePointer) {
                data.add(List.of(C_ENTRY_POINT_LITERAL_CODE_POINTER, cEntryPointLiteralCodePointer.methodName, cEntryPointLiteralCodePointer.definingClass.getName(),
                                Arrays.stream(cEntryPointLiteralCodePointer.parameterTypes).map(Class::getName)));
                return true;
            }
        }
        return super.delegateProcessing(data, constant);
    }

    private static int getRelocatableConstantMethodId(MethodPointer methodPointer) {
        ResolvedJavaMethod method = methodPointer.getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return getMethodId(hostedMethod.wrapped);
        } else {
            return getMethodId((AnalysisMethod) method);
        }
    }

    private static int getMethodId(AnalysisMethod analysisMethod) {
        if (!analysisMethod.isReachable()) {
            /*
             * At the moment, only reachable methods are persisted, so the method will not be loaded
             * in the extension image.
             */
            return -1;
        } else {
            return analysisMethod.getId();
        }
    }

    record SingletonPersistInfo(LayeredImageSingleton.PersistFlags flags, int id, EconomicMap<String, Object> keyStore) {
    }

    public void writeImageSingletonInfo(List<Map.Entry<Class<?>, Object>> layeredImageSingletons) {
        List<Object> singletonsList = new ArrayList<>();
        Map<LayeredImageSingleton, SingletonPersistInfo> singletonInfoMap = new HashMap<>();
        int nextID = 1;
        for (var singletonInfo : layeredImageSingletons) {
            LayeredImageSingleton singleton;
            if (singletonInfo.getValue() instanceof RuntimeOnlyWrapper wrapper) {
                singleton = wrapper.wrappedObject();
            } else {
                singleton = (LayeredImageSingleton) singletonInfo.getValue();
            }
            String key = singletonInfo.getKey().getName();
            if (!singletonInfoMap.containsKey(singleton)) {
                var writer = new ImageSingletonWriterImpl();
                var flags = singleton.preparePersist(writer);
                boolean persistData = flags == LayeredImageSingleton.PersistFlags.CREATE;
                var info = new SingletonPersistInfo(flags, persistData ? nextID++ : -1, persistData ? writer.getKeyValueStore() : null);
                singletonInfoMap.put(singleton, info);
            }
            var info = singletonInfoMap.get(singleton);
            singletonsList.add(List.of(key, info.flags.ordinal(), info.id));
        }
        jsonMap.put(IMAGE_SINGLETON_KEYS, singletonsList);

        List<Object> objectList = new ArrayList<>();
        var sortedByIDs = singletonInfoMap.entrySet().stream().filter(e -> e.getValue().flags == LayeredImageSingleton.PersistFlags.CREATE).sorted(Comparator.comparingInt(e -> e.getValue().id))
                        .toList();
        for (var entry : sortedByIDs) {
            var info = entry.getValue();
            objectList.add(List.of(info.id, entry.getKey().getClass().getName(), info.keyStore));
        }
        jsonMap.put(IMAGE_SINGLETON_OBJECTS, objectList);
    }
}

class ImageSingletonWriterImpl implements ImageSingletonWriter {
    private final EconomicMap<String, Object> keyValueStore = EconomicMap.create();

    EconomicMap<String, Object> getKeyValueStore() {
        return keyValueStore;
    }

    @Override
    public void writeInt(String keyName, int value) {
        var previous = keyValueStore.put(keyName, List.of("I", value));
        assert previous == null : previous;
    }

    @Override
    public void writeIntList(String keyName, List<Integer> value) {
        var previous = keyValueStore.put(keyName, List.of("I(", value));
        assert previous == null : previous;
    }

    @Override
    public void writeLong(String keyName, long value) {
        var previous = keyValueStore.put(keyName, List.of("L", value));
        assert previous == null : previous;
    }

    @Override
    public void writeString(String keyName, String value) {
        var previous = keyValueStore.put(keyName, List.of("S", value));
        assert previous == null : previous;
    }

    @Override
    public void writeStringList(String keyName, List<String> value) {
        var previous = keyValueStore.put(keyName, List.of("S(", value));
        assert previous == null : previous;
    }
}
