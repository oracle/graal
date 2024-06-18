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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.HUB_IDENTITY_HASH_CODE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_SINGLETON_KEYS;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IMAGE_SINGLETON_OBJECTS;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LOCATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_POINTER_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.OBJECT_OFFSET_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_OBJECT_FIELDS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.STATIC_PRIMITIVE_FIELDS_TAG;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.RuntimeOnlyWrapper;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
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

    public SVMImageLayerWriter() {
        super(new SVMImageLayerSnapshotUtil());
    }

    public void setNativeImageHeap(NativeImageHeap nativeImageHeap) {
        this.nativeImageHeap = nativeImageHeap;
    }

    @Override
    protected void persistHook(Universe universe, AnalysisUniverse analysisUniverse) {
        HostedUniverse hostedUniverse = (HostedUniverse) universe;

        ImageHeapConstant staticPrimitiveFields = (ImageHeapConstant) hostedUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticPrimitiveFields());
        ImageHeapConstant staticObjectFields = (ImageHeapConstant) hostedUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticObjectFields());

        jsonMap.put(STATIC_PRIMITIVE_FIELDS_TAG, getConstantId(staticPrimitiveFields));
        jsonMap.put(STATIC_OBJECT_FIELDS_TAG, getConstantId(staticObjectFields));
    }

    @Override
    protected void persistType(AnalysisType type, AnalysisUniverse analysisUniverse, EconomicMap<String, Object> typeMap) {
        HostVM hostVM = analysisUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        typeMap.put(HUB_IDENTITY_HASH_CODE_TAG, System.identityHashCode(hub));

        super.persistType(type, analysisUniverse, typeMap);
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
    protected void persistField(AnalysisField field, Universe universe, EconomicMap<String, Object> fieldMap) {
        HostedUniverse hostedUniverse = (HostedUniverse) universe;
        HostedField hostedField = hostedUniverse.lookup(field);
        int location = hostedField.getLocation();
        if (hostedField.isStatic() && location > 0) {
            fieldMap.put(LOCATION_TAG, location);
        }
        super.persistField(field, universe, fieldMap);
    }

    @Override
    protected void persistConstant(AnalysisUniverse analysisUniverse, ImageHeapConstant imageHeapConstant, EconomicMap<String, Object> constantMap, EconomicMap<String, Object> constantsMap) {
        ObjectInfo objectInfo = nativeImageHeap.getConstantInfo(imageHeapConstant);
        if (objectInfo != null) {
            constantMap.put(OBJECT_OFFSET_TAG, String.valueOf(objectInfo.getOffset()));
        }
        super.persistConstant(analysisUniverse, imageHeapConstant, constantMap, constantsMap);
    }

    @Override
    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id) {
        ResolvedJavaType type = bb.getConstantReflectionProvider().asJavaType(hostedObject);
        if (type instanceof AnalysisType analysisType) {
            /*
             * Until another solution for implementing a stable name for $$TypeSwitch classes is
             * found, the constant containing a DynamicHub corresponding to a $$TypeSwitch class is
             * not persisted as it would not be possible to relink it. Considering that those
             * classes are only used as a container for a static method, recreating the constant in
             * the extension image alongside the class should not cause too much issues.
             */
            if (!isTypeSwitch(analysisType)) {
                constantMap.put(CLASS_ID_TAG, analysisType.getId());
                constantsToRelink.add(id);
            }
        } else {
            super.persistConstantRelinkingInfo(constantMap, bb, clazz, hostedObject, id);
        }
    }

    @Override
    protected boolean delegateProcessing(List<List<Object>> data, Object constant) {
        if (constant instanceof RelocatableConstant relocatableConstant) {
            data.add(List.of(METHOD_POINTER_TAG, getRelocatableConstantMethodId(relocatableConstant)));
            return true;
        }
        return super.delegateProcessing(data, constant);
    }

    private static int getRelocatableConstantMethodId(RelocatableConstant relocatableConstant) {
        ResolvedJavaMethod method = ((MethodPointer) relocatableConstant.getPointer()).getMethod();
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
