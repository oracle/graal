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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PERSISTED;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.capnproto.StructList;
import org.capnproto.Text;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.CEntryPointLiteralReference;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ConstantReference;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonKey;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.ImageSingletonObject;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.KeyStoreEntry;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.fieldfolding.StaticFinalFieldFoldingFeature;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.util.IdentityHashCodeUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import sun.reflect.annotation.AnnotationParser;

public class SVMImageLayerLoader extends ImageLayerLoader {

    private final Field dynamicHubArrayHubField;

    private HostedUniverse hostedUniverse;
    private final ImageClassLoader imageClassLoader;

    public SVMImageLayerLoader(List<FilePaths> loadPaths, ImageClassLoader imageClassLoader) {
        super(loadPaths);
        dynamicHubArrayHubField = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
        this.imageClassLoader = imageClassLoader;
    }

    public void setHostedUniverse(HostedUniverse hostedUniverse) {
        this.hostedUniverse = hostedUniverse;
    }

    public HostedUniverse getHostedUniverse() {
        return hostedUniverse;
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

    @Override
    public Class<?> lookupClass(boolean optional, String className) {
        TypeResult<Class<?>> typeResult = imageClassLoader.findClass(className);
        if (!typeResult.isPresent()) {
            if (optional) {
                return null;
            } else {
                throw AnalysisError.shouldNotReachHere("Class not found: " + className);
            }
        }
        return typeResult.get();
    }

    @Override
    protected Annotation[] getAnnotations(StructList.Reader<SharedLayerSnapshotCapnProtoSchemaHolder.Annotation.Reader> reader) {
        return IntStream.range(0, reader.size()).mapToObj(reader::get).map(a -> {
            String typeName = a.getTypeName().toString();
            Class<? extends Annotation> annotationType = lookupBaseLayerTypeInHostVM(typeName).asSubclass(Annotation.class);
            Map<String, Object> annotationValuesMap = new HashMap<>();
            a.getValues().forEach(v -> {
                Object value = switch (v.which()) {
                    case ENUM -> getEnumValue(v.getEnum().getClassName(), v.getEnum().getName());
                    case OTHER -> v.getOther().toString();
                    default -> throw AnalysisError.shouldNotReachHere("Unknown annotation value kind: " + v.which());
                };
                annotationValuesMap.put(v.getName().toString(), value);
            });
            return AnnotationParser.annotationForMap(annotationType, annotationValuesMap);
        }).toArray(Annotation[]::new);
    }

    @Override
    protected EncodedGraph getEncodedGraph(AnalysisMethod analysisMethod, Text.Reader location) {
        EncodedGraph encodedGraph = super.getEncodedGraph(analysisMethod, location);
        for (int i = 0; i < encodedGraph.getNumObjects(); ++i) {
            if (encodedGraph.getObject(i) instanceof CGlobalDataInfo cGlobalDataInfo) {
                encodedGraph.setObject(i, CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalDataInfo.getData()));
            }
        }
        return encodedGraph;
    }

    @Override
    public void initializeBaseLayerField(AnalysisField analysisField) {
        PersistedAnalysisField.Reader fieldData = getFieldData(analysisField);

        int fieldCheckIndex = fieldData.getFieldCheckIndex();
        if (fieldCheckIndex != -1) {
            StaticFinalFieldFoldingFeature.singleton().putBaseLayerFieldCheckIndex(analysisField.getId(), fieldCheckIndex);
        }

        super.initializeBaseLayerField(analysisField);
    }

    @Override
    protected void prepareConstantRelinking(PersistedConstant.Reader constantData, int identityHashCode, int id) {
        if (constantData.isObject() && constantData.getObject().getRelinking().isClassConstant()) {
            int typeId = constantData.getObject().getRelinking().getClassConstant().getTypeId();
            typeToConstant.put(typeId, id);
        } else {
            super.prepareConstantRelinking(constantData, identityHashCode, id);
        }
    }

    @Override
    protected boolean delegateProcessing(ConstantReference.Reader constantRef, Object[] values, int i) {
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
        return super.delegateProcessing(constantRef, values, i);
    }

    @Override
    protected JavaConstant lookupHostedObject(PersistedConstant.Reader baseLayerConstant, Class<?> clazz) {
        if (clazz.equals(Class.class)) {
            /* DynamicHub corresponding to $$TypeSwitch classes are not relinked */
            if (baseLayerConstant.isObject() && baseLayerConstant.getObject().getRelinking().isClassConstant()) {
                int typeId = baseLayerConstant.getObject().getRelinking().getClassConstant().getTypeId();
                return getDynamicHub(typeId);
            }
        }
        return super.lookupHostedObject(baseLayerConstant, clazz);
    }

    private JavaConstant getDynamicHub(int tid) {
        AnalysisType type = getAnalysisTypeForBaseLayerId(tid);
        DynamicHub hub = ((SVMHost) universe.hostVM()).dynamicHub(type);
        return hostedValuesProvider.forObject(hub);
    }

    @Override
    protected void injectIdentityHashCode(Object object, Integer identityHashCode) {
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

    @Override
    public void ensureHubInitialized(ImageHeapConstant constant) {
        JavaConstant javaConstant = constant.getHostedObject();
        if (constant.getType().getJavaClass().equals(Class.class)) {
            DynamicHub hub = universe.getHostedValuesProvider().asObject(DynamicHub.class, javaConstant);
            AnalysisType type = ((SVMHost) universe.hostVM()).lookupType(hub);
            ensureHubInitialized(type);
            /*
             * If the persisted constant contains a non-null arrayHub, the corresponding DynamicHub
             * must be created and the initializeMetaDataTask needs to be executed to ensure the
             * hosted object matches the persisted constant.
             */
            if (((ImageHeapInstance) constant).getFieldValue(metaAccess.lookupJavaField(dynamicHubArrayHubField)) != JavaConstant.NULL_POINTER && hub.getArrayHub() == null) {
                AnalysisType arrayClass = type.getArrayClass();
                ensureHubInitialized(arrayClass);
            }
        }
    }

    private static void ensureHubInitialized(AnalysisType type) {
        type.registerAsReachable(PERSISTED);
        type.getInitializeMetaDataTask().ensureDone();
    }

    @Override
    public void rescanHub(AnalysisType type, Object hubObject) {
        DynamicHub hub = (DynamicHub) hubObject;
        universe.getHeapScanner().rescanObject(hub);
        universe.getHeapScanner().rescanField(hub, SVMImageLayerSnapshotUtil.classInitializationInfo);
        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray()) {
                universe.getHeapScanner().rescanField(hub.getComponentHub(), SVMImageLayerSnapshotUtil.arrayHub);
            }
            universe.getHeapScanner().rescanField(hub, SVMImageLayerSnapshotUtil.interfacesEncoding);
            if (type.isEnum()) {
                universe.getHeapScanner().rescanField(hub, SVMImageLayerSnapshotUtil.enumConstantsReference);
            }
        }
    }

    @Override
    protected boolean hasValueForObject(Object object) {
        if (object instanceof DynamicHub dynamicHub) {
            AnalysisType type = ((SVMHost) universe.hostVM()).lookupType(dynamicHub);
            return typeToConstant.containsKey(type.getId());
        }
        return super.hasValueForObject(object);
    }

    @Override
    protected ImageHeapConstant getValueForObject(Object object) {
        if (object instanceof DynamicHub dynamicHub) {
            AnalysisType type = ((SVMHost) universe.hostVM()).lookupType(dynamicHub);
            int id = typeToConstant.get(type.getId());
            return getOrCreateConstant(id);
        }
        return super.getValueForObject(object);
    }

    public Map<Object, Set<Class<?>>> loadImageSingletons(Object forbiddenObject) {
        openFilesAndLoadJsonMap();
        return loadImageSingletons0(forbiddenObject);
    }

    private Map<Object, Set<Class<?>>> loadImageSingletons0(Object forbiddenObject) {
        Map<Integer, Object> idToObjectMap = new HashMap<>();
        for (ImageSingletonObject.Reader obj : snapshot.getSingletonObjects()) {
            String className = obj.getClassName().toString();

            EconomicMap<String, Object> keyStore = EconomicMap.create();
            for (KeyStoreEntry.Reader entry : obj.getStore()) {
                KeyStoreEntry.Value.Reader v = entry.getValue();
                Object value = switch (v.which()) {
                    case I -> v.getI();
                    case J -> v.getJ();
                    case STR -> v.getStr().toString();
                    case IL -> Stream.of(v.getIl()).flatMapToInt(r -> IntStream.range(0, r.size()).map(r::get)).toArray();
                    case ZL -> getBooleans(v.getZl());
                    case STRL -> StreamSupport.stream(v.getStrl().spliterator(), false).map(Text.Reader::toString).toArray(String[]::new);
                    case _NOT_IN_SCHEMA -> throw new IllegalStateException("Unexpected value: " + v.which());
                };
                keyStore.put(entry.getKey().toString(), value);
            }

            // create singleton object instance
            Object result;
            try {
                Class<?> clazz = lookupClass(false, className);
                Method createMethod = ReflectionUtil.lookupMethod(clazz, "createFromLoader", ImageSingletonLoader.class);
                result = createMethod.invoke(null, new ImageSingletonLoaderImpl(keyStore, imageClassLoader));
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Failed to recreate image singleton", t);
            }

            idToObjectMap.put(obj.getId(), result);
        }

        Map<Object, Set<Class<?>>> singletonInitializationMap = new HashMap<>();
        for (ImageSingletonKey.Reader entry : snapshot.getSingletonKeys()) {
            String className = entry.getKeyClassName().toString();
            PersistFlags persistInfo = PersistFlags.values()[entry.getPersistFlag()];
            int id = entry.getObjectId();
            if (persistInfo == PersistFlags.CREATE) {
                assert id != -1 : "Create image singletons should be linked to an object";
                Object singletonObject = idToObjectMap.get(id);
                Class<?> clazz = lookupClass(false, className);
                singletonInitializationMap.computeIfAbsent(singletonObject, (k) -> new HashSet<>());
                singletonInitializationMap.get(singletonObject).add(clazz);
            } else if (persistInfo == PersistFlags.FORBIDDEN) {
                assert id == -1 : "Unrestored image singleton should not be linked to an object";
                Class<?> clazz = lookupClass(false, className);
                singletonInitializationMap.computeIfAbsent(forbiddenObject, (k) -> new HashSet<>());
                singletonInitializationMap.get(forbiddenObject).add(clazz);
            } else {
                assert persistInfo == PersistFlags.NOTHING : "Unexpected PersistFlags value: " + persistInfo;
                assert id == -1 : "Unrestored image singleton should not be linked to an object";
            }
        }

        return singletonInitializationMap;
    }

}

class ImageSingletonLoaderImpl implements ImageSingletonLoader {
    private final UnmodifiableEconomicMap<String, Object> keyStore;
    private final ImageClassLoader imageClassLoader;

    ImageSingletonLoaderImpl(UnmodifiableEconomicMap<String, Object> keyStore, ImageClassLoader imageClassLoader) {
        this.keyStore = keyStore;
        this.imageClassLoader = imageClassLoader;
    }

    @Override
    public List<Boolean> readBoolList(String keyName) {
        boolean[] l = (boolean[]) keyStore.get(keyName);
        return IntStream.range(0, l.length).mapToObj(i -> l[i]).toList();
    }

    @Override
    public int readInt(String keyName) {
        return (int) keyStore.get(keyName);
    }

    @Override
    public List<Integer> readIntList(String keyName) {
        int[] l = (int[]) keyStore.get(keyName);
        return IntStream.of(l).boxed().toList();
    }

    @Override
    public long readLong(String keyName) {
        return (long) keyStore.get(keyName);
    }

    @Override
    public String readString(String keyName) {
        return (String) keyStore.get(keyName);
    }

    @Override
    public List<String> readStringList(String keyName) {
        return List.of((String[]) keyStore.get(keyName));
    }

    @Override
    public Class<?> lookupClass(String className) {
        return imageClassLoader.findClass(className).get();
    }
}
