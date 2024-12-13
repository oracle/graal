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

import static com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ClassInitializationInfo.Builder;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.capnproto.PrimitiveList;
import org.capnproto.StructList;
import org.capnproto.Text;
import org.capnproto.TextList;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.AnnotationValue;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonKey;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonObject;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.KeyStoreEntry;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
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
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationMetadata;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.fieldfolding.StaticFinalFieldFoldingFeature;
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
import com.oracle.svm.util.ModuleSupport;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationType;

public class SVMImageLayerWriter extends ImageLayerWriter {
    private NativeImageHeap nativeImageHeap;
    private HostedUniverse hUniverse;

    public SVMImageLayerWriter(boolean useSharedLayerGraphs) {
        super(useSharedLayerGraphs);
    }

    public void setNativeImageHeap(NativeImageHeap nativeImageHeap) {
        this.nativeImageHeap = nativeImageHeap;
    }

    public void setHostedUniverse(HostedUniverse hUniverse) {
        this.hUniverse = hUniverse;
    }

    @Override
    protected void persistAnnotationValues(AnnotatedElement annotatedElement, Class<? extends Annotation> annotationClass, IntFunction<StructList.Builder<AnnotationValue.Builder>> builder) {
        Annotation annotation = AnnotationAccess.getAnnotation(annotatedElement, annotationClass);
        persistAnnotationValues(annotation, annotationClass, builder);
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
        if (v.getClass().isEnum()) {
            var ba = b.initEnum();
            ba.setClassName(v.getClass().getName());
            ba.setName(v.toString());
        } else if (v.getClass().isArray()) {
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
                default -> throw AnalysisError.shouldNotReachHere("Unknown annotation value: " + v);
            }
        }
    }

    private static void setAnnotationPrimitiveValue(AnnotationValue.Builder b, JavaKind kind, long rawValue) {
        var pv = b.initPrimitive();
        pv.setTypeChar(NumUtil.safeToUByte(kind.getTypeChar()));
        pv.setRawValue(rawValue);
    }

    @Override
    protected void persistHook() {
        ImageHeapConstant staticPrimitiveFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticPrimitiveFields());
        ImageHeapConstant staticObjectFields = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(StaticFieldsSupport.getStaticObjectFields());

        snapshotBuilder.setStaticPrimitiveFieldsConstantId(getConstantId(staticPrimitiveFields));
        snapshotBuilder.setStaticObjectFieldsConstantId(getConstantId(staticObjectFields));
    }

    @Override
    protected void persistType(AnalysisType type, String typeDescriptor, PersistedAnalysisType.Builder builder) {
        HostVM hostVM = aUniverse.hostVM();
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);
        builder.setHubIdentityHashCode(System.identityHashCode(hub));

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

        super.persistType(type, typeDescriptor, builder);
    }

    /**
     * Some types can have an unstable name between two different image builds. To avoid producing
     * wrong results, a warning should be printed if such types exist in the resulting image.
     */
    @Override
    protected void afterTypeAdded(AnalysisType type) {
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

        super.afterTypeAdded(type);
    }

    private static void handleNameConflict(String message) {
        if (SubstrateOptions.AbortOnNameConflict.getValue()) {
            throw VMError.shouldNotReachHere(message);
        } else {
            LogUtils.warning(message);
        }
    }

    @Override
    protected void scanConstantReferencedObjects(ImageHeapConstant constant, Object[] referencedObjects, Collection<ImageHeapConstant> discoveredConstants) {
        if (referencedObjects != null) {
            for (Object obj : referencedObjects) {
                if (obj instanceof MethodPointer mp) {
                    ensureMethodPersisted(getRelocatableConstantMethod(mp));
                }
            }
        }
        super.scanConstantReferencedObjects(constant, referencedObjects, discoveredConstants);
    }

    @Override
    protected void persistMethod(AnalysisMethod method, PersistedAnalysisMethod.Builder builder) {
        super.persistMethod(method, builder);

        // register this method as persisted for name resolution
        HostedDynamicLayerInfo.singleton().recordPersistedMethod(hUniverse.lookup(method));
    }

    @Override
    protected void persistField(AnalysisField field, PersistedAnalysisField.Builder builder) {
        HostedField hostedField = hUniverse.lookup(field);
        int location = hostedField.getLocation();
        if (location > 0) {
            builder.setLocation(location);
        }
        Integer fieldCheckIndex = StaticFinalFieldFoldingFeature.singleton().getFieldCheckIndex(field);
        builder.setFieldCheckIndex((fieldCheckIndex != null) ? fieldCheckIndex : -1);
        super.persistField(field, builder);
    }

    @Override
    protected void persistConstant(ImageHeapConstant imageHeapConstant, ConstantParent parent, PersistedConstant.Builder builder, Set<Integer> constantsToRelink) {
        ObjectInfo objectInfo = nativeImageHeap.getConstantInfo(imageHeapConstant);
        builder.setObjectOffset((objectInfo == null) ? -1 : objectInfo.getOffset());
        super.persistConstant(imageHeapConstant, parent, builder, constantsToRelink);
    }

    @Override
    protected void persistConstantRelinkingInfo(PersistedConstant.Object.Relinking.Builder builder, BigBang bb, Class<?> clazz, JavaConstant hostedObject, int id, Set<Integer> constantsToRelink) {
        ResolvedJavaType type = bb.getConstantReflectionProvider().asJavaType(hostedObject);
        if (type instanceof AnalysisType analysisType) {
            builder.initClassConstant().setTypeId(analysisType.getId());
            constantsToRelink.add(id);
        } else {
            super.persistConstantRelinkingInfo(builder, bb, clazz, hostedObject, id, constantsToRelink);
        }
    }

    @Override
    protected boolean delegateProcessing(SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference.Builder builder, Object constant) {
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
        return super.delegateProcessing(builder, constant);
    }

    private static AnalysisMethod getRelocatableConstantMethod(MethodPointer methodPointer) {
        ResolvedJavaMethod method = methodPointer.getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return hostedMethod.wrapped;
        } else {
            return (AnalysisMethod) method;
        }
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
                var writer = new ImageSingletonWriterImpl();
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
}

class ImageSingletonWriterImpl implements ImageSingletonWriter {
    private final EconomicMap<String, Object> keyValueStore = EconomicMap.create();

    EconomicMap<String, Object> getKeyValueStore() {
        return keyValueStore;
    }

    @Override
    public void writeBoolList(String keyName, List<Boolean> value) {
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
        var previous = keyValueStore.put(keyName, value.toArray(String[]::new));
        assert previous == null : Assertions.errorMessage(keyName, previous);
    }
}
