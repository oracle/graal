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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ANNOTATIONS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.C_ENTRY_POINT_LITERAL_CODE_POINTER;
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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_INITIALIZED_NO_TRACKING_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.IS_NO_INITIALIZER_NO_TRACKING_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_POINTER_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PERSISTED;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TYPES_TAG;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.imagelayer.HostedDynamicLayerInfo;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.thread.LayeredVMThreadLocalCollector;
import com.oracle.svm.hosted.thread.VMThreadLocalCollector;
import com.oracle.svm.hosted.util.IdentityHashCodeUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;

public class SVMImageLayerLoader extends ImageLayerLoader {

    private final Field dynamicHubArrayHubField;

    private HostedUniverse hostedUniverse;
    private ImageClassLoader imageClassLoader;

    public SVMImageLayerLoader(List<FilePaths> loadPaths) {
        super(new SVMImageLayerSnapshotUtil(), loadPaths);
        dynamicHubArrayHubField = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
    }

    public void setHostedUniverse(HostedUniverse hostedUniverse) {
        this.hostedUniverse = hostedUniverse;
    }

    public void setImageClassLoader(ImageClassLoader imageClassLoader) {
        this.imageClassLoader = imageClassLoader;
    }

    public HostedUniverse getHostedUniverse() {
        return hostedUniverse;
    }

    public ClassInitializationInfo getClassInitializationInfo(AnalysisType type) {
        int tid = type.getId();
        EconomicMap<String, Object> typesMap = get(jsonMap, TYPES_TAG);
        EconomicMap<String, Object> typeMap = get(typesMap, typeIdToIdentifier.get(tid));

        boolean isNoInitializerNoTracking = get(typeMap, IS_NO_INITIALIZER_NO_TRACKING_TAG);
        boolean isInitializedNoTracking = get(typeMap, IS_INITIALIZED_NO_TRACKING_TAG);
        boolean isFailedNoTracking = get(typeMap, IS_FAILED_NO_TRACKING_TAG);

        if (isNoInitializerNoTracking) {
            return ClassInitializationInfo.forNoInitializerInfo(false);
        } else if (isInitializedNoTracking) {
            return ClassInitializationInfo.forInitializedInfo(false);
        } else if (isFailedNoTracking) {
            return ClassInitializationInfo.forFailedInfo(false);
        } else {
            boolean isInitialized = get(typeMap, INFO_IS_INITIALIZED_TAG);
            boolean isInErrorState = get(typeMap, INFO_IS_IN_ERROR_STATE_TAG);
            boolean isLinked = get(typeMap, INFO_IS_LINKED_TAG);
            boolean hasInitializer = get(typeMap, INFO_HAS_INITIALIZER_TAG);
            boolean isBuildTimeInitialized = get(typeMap, INFO_IS_BUILD_TIME_INITIALIZED_TAG);
            boolean isTracked = get(typeMap, INFO_IS_TRACKED_TAG);

            ClassInitializationInfo.InitState initState;
            if (isInitialized) {
                initState = ClassInitializationInfo.InitState.FullyInitialized;
            } else if (isInErrorState) {
                initState = ClassInitializationInfo.InitState.InitializationError;
            } else {
                assert isLinked : "Invalid state";
                Integer classInitializerId = get(typeMap, INFO_CLASS_INITIALIZER_TAG);
                MethodPointer classInitializer = classInitializerId == null ? null : new MethodPointer(getAnalysisMethod(classInitializerId));
                return new ClassInitializationInfo(classInitializer, isTracked);
            }

            return new ClassInitializationInfo(initState, hasInitializer, isBuildTimeInitialized, isTracked);
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
    protected Annotation[] getAnnotations(EconomicMap<String, Object> elementData) {
        List<String> annotationNames = get(elementData, ANNOTATIONS_TAG);

        return annotationNames.stream().map(s -> {
            Class<? extends Annotation> annotationType = cast(lookupBaseLayerTypeInHostVM(s));
            return AnnotationParser.annotationForMap(annotationType, AnnotationType.getInstance(annotationType).memberDefaults());
        }).toList().toArray(new Annotation[0]);
    }

    @Override
    protected void initializeBaseLayerMethod(AnalysisMethod analysisMethod, EconomicMap<String, Object> methodData) {
        if (!HostedDynamicLayerInfo.singleton().compiledInPriorLayer(analysisMethod) && hasAnalysisParsedGraph(analysisMethod)) {
            /*
             * GR-55294: When the analysis elements from the base layer will be able to be
             * materialized after the analysis, this will not be needed anymore.
             */
            analysisMethod.ensureGraphParsed(universe.getBigbang());
            analysisMethod.setAnalyzedGraph(((AnalysisParsedGraph) analysisMethod.getGraph()).getEncodedGraph());
        }
        super.initializeBaseLayerMethod(analysisMethod, methodData);
    }

    @Override
    protected void processGraph(EncodedGraph encodedGraph) {
        super.processGraph(encodedGraph);
        Object[] objects = encodedGraph.getObjects();
        for (Object object : objects) {
            if (object instanceof VMThreadLocalInfo vmThreadLocalInfo) {
                LayeredVMThreadLocalCollector layeredVMThreadLocalCollector = (LayeredVMThreadLocalCollector) ImageSingletons.lookup(VMThreadLocalCollector.class);
                layeredVMThreadLocalCollector.registerPriorThreadLocalInfo(vmThreadLocalInfo);
            }
        }
    }

    @Override
    protected void loadEncodedGraphLineAnalysisElements(String line) {
        if (line.contains(HostedInstanceClass.class.getName()) || line.contains(HostedPrimitiveType.class.getName()) || line.contains(HostedArrayClass.class.getName()) ||
                        line.contains(HostedInterface.class.getName())) {
            getAnalysisType(getId(line));
        } else if (line.contains(HostedMethod.class.getName())) {
            getAnalysisMethod(getId(line));
        } else if (line.contains(HostedField.class.getName())) {
            getAnalysisField(getId(line));
        } else {
            super.loadEncodedGraphLineAnalysisElements(line);
        }
    }

    @Override
    protected void prepareConstantRelinking(EconomicMap<String, Object> constantData, int identityHashCode, int id) {
        Integer tid = get(constantData, CLASS_ID_TAG);
        if (tid != null) {
            typeToConstant.put(tid, id);
        } else {
            super.prepareConstantRelinking(constantData, identityHashCode, id);
        }
    }

    @Override
    protected boolean delegateProcessing(String constantType, Object constantValue, List<Object> constantData, Object[] values, int i) {
        if (constantType.equals(METHOD_POINTER_TAG)) {
            AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
                AnalysisType methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
                int mid = (int) constantValue;
                AnalysisMethod method = getAnalysisMethod(mid);
                RelocatableConstant constant = new RelocatableConstant(new MethodPointer(method), methodPointerType);
                values[i] = constant;
                return constant;
            });
            values[i] = task;
            return true;
        } else if (constantType.equals(C_ENTRY_POINT_LITERAL_CODE_POINTER)) {
            AnalysisType cEntryPointerLiteralPointerType = metaAccess.lookupJavaType(CEntryPointLiteralCodePointer.class);
            String methodName = (String) constantValue;
            Class<?> definingClass = lookupBaseLayerTypeInHostVM((String) constantData.get(2));
            List<String> parameters = cast(constantData.get(3));
            Class<?>[] parameterTypes = parameters.stream().map(this::lookupBaseLayerTypeInHostVM).toList().toArray(new Class<?>[0]);
            values[i] = new RelocatableConstant(new CEntryPointLiteralCodePointer(definingClass, methodName, parameterTypes), cEntryPointerLiteralPointerType);
            return true;
        }
        return super.delegateProcessing(constantType, constantValue, constantData, values, i);
    }

    @Override
    protected JavaConstant lookupHostedObject(EconomicMap<String, Object> baseLayerConstant, Class<?> clazz) {
        if (clazz.equals(Class.class)) {
            Integer tid = get(baseLayerConstant, CLASS_ID_TAG);
            /* DynamicHub corresponding to $$TypeSwitch classes are not relinked */
            if (tid != null) {
                return getDynamicHub(tid);
            }
        }
        return super.lookupHostedObject(baseLayerConstant, clazz);
    }

    private JavaConstant getDynamicHub(int tid) {
        AnalysisType type = getAnalysisType(tid);
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
        List<Object> singletonObjects = cast(jsonMap.get(IMAGE_SINGLETON_OBJECTS));
        Map<Integer, Object> idToObjectMap = new HashMap<>();
        for (Object entry : singletonObjects) {
            List<Object> list = cast(entry);
            Integer id = cast(list.get(0));
            String className = cast(list.get(1));
            EconomicMap<String, Object> keyStore = cast(list.get(2));

            // create singleton object instance
            Object result;
            try {
                Class<?> clazz = lookupClass(false, className);
                Method createMethod = ReflectionUtil.lookupMethod(clazz, "createFromLoader", ImageSingletonLoader.class);
                result = createMethod.invoke(null, new ImageSingletonLoaderImpl(keyStore, imageClassLoader));
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Failed to recreate image singleton", t);
            }

            idToObjectMap.put(id, result);
        }

        Map<Object, Set<Class<?>>> singletonInitializationMap = new HashMap<>();
        List<Object> singletonKeys = cast(jsonMap.get(IMAGE_SINGLETON_KEYS));
        for (Object entry : singletonKeys) {
            List<Object> list = cast(entry);
            String key = cast(list.get(0));
            LayeredImageSingleton.PersistFlags persistInfo = LayeredImageSingleton.PersistFlags.values()[(int) list.get(1)];
            int id = cast(list.get(2));
            if (persistInfo == PersistFlags.CREATE) {
                assert id != -1 : "Create image singletons should be linked to an object";
                Object singletonObject = idToObjectMap.get(id);
                Class<?> clazz = lookupClass(false, key);
                singletonInitializationMap.computeIfAbsent(singletonObject, (k) -> new HashSet<>());
                singletonInitializationMap.get(singletonObject).add(clazz);
            } else if (persistInfo == PersistFlags.FORBIDDEN) {
                assert id == -1 : "Unrestored image singleton should not be linked to an object";
                Class<?> clazz = lookupClass(false, key);
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

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object object) {
        return (T) object;
    }

    @Override
    public int readInt(String keyName) {
        List<Object> value = cast(keyStore.get(keyName));
        String type = cast(value.get(0));
        assert type.equals("I") : type;
        return cast(value.get(1));
    }

    @Override
    public List<Integer> readIntList(String keyName) {
        List<Object> value = cast(keyStore.get(keyName));
        String type = cast(value.get(0));
        assert type.equals("I(") : type;
        return cast(value.get(1));
    }

    @Override
    public long readLong(String keyName) {
        List<Object> value = cast(keyStore.get(keyName));
        String type = cast(value.get(0));
        assert type.equals("L") : type;
        Number number = cast(value.get(1));
        return number.longValue();
    }

    @Override
    public String readString(String keyName) {
        List<Object> value = cast(keyStore.get(keyName));
        String type = cast(value.get(0));
        assert type.equals("S") : type;
        return cast(value.get(1));
    }

    @Override
    public List<String> readStringList(String keyName) {
        List<Object> value = cast(keyStore.get(keyName));
        String type = cast(value.get(0));
        assert type.equals("S(") : type;
        return cast(value.get(1));
    }

    @Override
    public Class<?> lookupClass(String className) {
        return imageClassLoader.findClass(className).get();
    }
}
