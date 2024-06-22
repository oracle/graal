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

import static com.oracle.svm.hosted.imagelayer.LoadImageSingletonFeature.CROSS_LAYER_SINGLETON_TABLE_SYMBOL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.LoadImageSingletonNode;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.LoadImageSingletonFactory;
import com.oracle.svm.core.layeredimagesingleton.ApplicationLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tracks metdata {@link MultiLayeredImageSingleton} and {@link ApplicationLayerOnlyImageSingleton}
 * singletons so that they can be properly referenced as needed.
 */
@AutomaticallyRegisteredFeature
public class LoadImageSingletonFeature implements InternalFeature, FeatureSingleton, UnsavedSingleton {
    public static final String CROSS_LAYER_SINGLETON_TABLE_SYMBOL = "__layered_singleton_table_start";

    private static CrossLayerSingletonMappingInfo getCrossLayerSingletonMappingInfo() {
        return (CrossLayerSingletonMappingInfo) ImageSingletons.lookup(LoadImageSingletonFactory.class);
    }

    /*
     * Cache for objects created by the calls to getAllLayers within the application layer.
     */
    private final Map<Class<?>, JavaConstant> keyToMultiLayerConstantMap = new ConcurrentHashMap<>();
    /*
     * We need to cache this for the invocation plugin.
     */
    private SVMImageLayerLoader loader;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), MultiLayeredImageSingleton.class);
        r.register(new InvocationPlugin.RequiredInvocationPlugin("getAllLayers", Class.class) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {

                Class<?> key = b.getSnippetReflection().asObject(Class.class, classNode.asJavaConstant());

                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    /*
                     * Load reference to the proper slot within the cross-layer singleton table.
                     */
                    LoadImageSingletonNode layeredSingleton = LoadImageSingletonFactory.loadLayeredImageSingleton(key, b.getMetaAccess());
                    b.addPush(JavaKind.Object, layeredSingleton);
                    return true;
                } else {
                    /*
                     * Can directly load the array of all objects
                     */
                    JavaConstant multiLayerArray = keyToMultiLayerConstantMap.computeIfAbsent(key,
                                    k -> createMultiLayerArray(key, (AnalysisType) b.getMetaAccess().lookupJavaType(k.arrayType()), b.getSnippetReflection()));
                    var node = ConstantNode.forConstant(multiLayerArray, b.getMetaAccess());
                    b.addPush(JavaKind.Object, node);
                    return true;
                }
            }
        });
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        loader = (SVMImageLayerLoader) config.getUniverse().getImageLayerLoader();

        LayeredImageSingletonSupport layeredImageSingletonSupport = LayeredImageSingletonSupport.singleton();
        layeredImageSingletonSupport.freezeMultiLayeredImageSingletons();

        Consumer<Object[]> multiLayerEmbeddedRootsRegistration = (objArray) -> {
            var method = config.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(MultiLayeredImageSingleton.class, "getAllLayers", Class.class));
            var javaConstant = config.getUniverse().getSnippetReflection().forObject(objArray);
            config.getUniverse().registerEmbeddedRoot(javaConstant, new BytecodePosition(null, method, BytecodeFrame.UNKNOWN_BCI));
        };

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            /*
             * We must register all multi layered image singletons within shared layers as embedded.
             * Even if they are not referred to in this layer, it is possible for them to be
             * accessed via a multi-layer lookup in a subsequent layer, so they must be installed in
             * the heap.
             */
            Object[] multiLayeredSingletons = LayeredImageSingletonSupport.singleton().getMultiLayeredImageSingletonKeys().stream().map(layeredImageSingletonSupport::runtimeLookup).toArray();
            if (multiLayeredSingletons.length != 0) {
                multiLayerEmbeddedRootsRegistration.accept(multiLayeredSingletons);
            }
        }

        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(LoadImageSingletonFactory.class, new CrossLayerSingletonMappingInfo());

        } else if (ImageLayerBuildingSupport.buildingApplicationLayer()) {

            ArrayList<Object> applicationLayerEmbeddedRoots = new ArrayList<>();
            ArrayList<Object> multiLayerEmbeddedRoots = new ArrayList<>();
            for (var slotInfo : getCrossLayerSingletonMappingInfo().getPriorKeyToSlotInfoMap().values()) {
                switch (slotInfo.recordKind()) {
                    case APPLICATION_LAYER_SINGLETON -> {
                        /*
                         * We must register the current image application only singleton keys to
                         * ensure their types are part of the current analysis.
                         */
                        Class<?> key = slotInfo.keyClass();
                        var singleton = layeredImageSingletonSupport.runtimeLookup(key);
                        assert singleton.getClass().equals(key) : String.format("We currently require %s to match their key. Key %s, Singleton: %s", ApplicationLayerOnlyImageSingleton.class, key,
                                        singleton);
                        applicationLayerEmbeddedRoots.add(singleton);
                    }
                    case MULTI_LAYERED_SINGLETON -> {
                        /*
                         * Register multi-layer singletons of this layer if a getAllLayers call
                         * exists in a prior layer.
                         */
                        Class<?> key = slotInfo.keyClass();
                        if (ImageSingletons.contains(key)) {
                            multiLayerEmbeddedRoots.add(layeredImageSingletonSupport.runtimeLookup(key));
                        }
                        /*
                         * Within the application layer there will be an array created to hold all
                         * multi-layered image singletons. We must record this type is in the heap.
                         */
                        config.registerAsInHeap(slotInfo.keyClass().arrayType());
                        if (!getCrossLayerSingletonMappingInfo().getPriorLayerObjectIDs(slotInfo.keyClass()).isEmpty()) {
                            /*
                             * We also must ensure the type is registered as instantiated in this
                             * heap if we know the array will refer to a prior object.
                             */
                            config.registerAsInHeap(slotInfo.keyClass());
                        }
                    }
                }
            }

            if (!applicationLayerEmbeddedRoots.isEmpty()) {
                var method = config.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(ImageSingletons.class, "lookup", Class.class));
                var javaConstant = config.getUniverse().getSnippetReflection().forObject(applicationLayerEmbeddedRoots.toArray());
                config.getUniverse().registerEmbeddedRoot(javaConstant, new BytecodePosition(null, method, BytecodeFrame.UNKNOWN_BCI));
            }

            if (!multiLayerEmbeddedRoots.isEmpty()) {
                multiLayerEmbeddedRootsRegistration.accept(multiLayerEmbeddedRoots.toArray());
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        var config = (FeatureImpl.BeforeCompilationAccessImpl) access;
        getCrossLayerSingletonMappingInfo().assignSlots(config.getMetaAccess());
    }

    ImageHeapObjectArray createMultiLayerArray(Class<?> key, AnalysisType arrayType, SnippetReflectionProvider snippetReflectionProvider) {
        List<Integer> priorIds = getCrossLayerSingletonMappingInfo().getPriorLayerObjectIDs(key);
        Stream<JavaConstant> values = priorIds.stream().map(priorId -> loader.getOrCreateConstant(priorId));

        if (ImageSingletons.contains(key)) {
            var singleton = LayeredImageSingletonSupport.singleton().runtimeLookup(key);
            JavaConstant singletonConstant = snippetReflectionProvider.forObject(singleton);
            values = Stream.concat(values, Stream.of(singletonConstant));
        }

        Object[] elements = values.toArray();
        return ImageHeapObjectArray.createUnbackedImageHeapArray(arrayType, elements);
    }

    /**
     * Holds the values which need to be stored in the cross-layer singleton table.
     */
    private Map<JavaConstant, Integer> constantToTableSlotMap;

    public Map<JavaConstant, Integer> getConstantToTableSlotMap() {
        assert constantToTableSlotMap != null;
        return constantToTableSlotMap;
    }

    /**
     * Ensure all objects needed for {@link MultiLayeredImageSingleton}s and
     * {@link ApplicationLayerOnlyImageSingleton}s are installed in the heap.
     */
    public void addInitialObjects(NativeImageHeap heap, HostedUniverse hUniverse) {
        String addReason = "Read via the layered image singleton support";

        /*
         * We must add to the heap and record the id of all multilayered image singletons so that if
         * needed a table can be created of all layer references later.
         */
        LayeredImageSingletonSupport layeredImageSingletonSupport = LayeredImageSingletonSupport.singleton();
        for (var keyClass : layeredImageSingletonSupport.getMultiLayeredImageSingletonKeys()) {
            var singleton = layeredImageSingletonSupport.runtimeLookup(keyClass);
            ImageHeapConstant singletonConstant = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(singleton);
            heap.addConstant(singletonConstant, false, addReason);
            int id = ImageHeapConstant.getConstantID(singletonConstant);

            getCrossLayerSingletonMappingInfo().recordConstantID(keyClass, id);
        }

        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            Map<JavaConstant, Integer> mappingInfo = new HashMap<>();
            for (var slotInfo : getCrossLayerSingletonMappingInfo().getCurrentKeyToSlotInfoMap().values()) {

                JavaConstant createdConstant = switch (slotInfo.recordKind()) {
                    case APPLICATION_LAYER_SINGLETON -> {
                        /*
                         * Need to install the singleton.
                         */
                        var singleton = layeredImageSingletonSupport.runtimeLookup(slotInfo.keyClass());
                        JavaConstant singletonConstant = hUniverse.getSnippetReflection().forObject(singleton);
                        heap.addConstant(singletonConstant, false, addReason);

                        yield singletonConstant;
                    }
                    case MULTI_LAYERED_SINGLETON -> {
                        /*
                         * Check if we already created this object via an intrinsification.
                         */
                        JavaConstant multiLayerArray = keyToMultiLayerConstantMap.get(slotInfo.keyClass());
                        if (multiLayerArray == null) {
                            /*
                             * Need to install the array which points to all installed singletons.
                             */
                            ImageHeapObjectArray imageHeapArray = createMultiLayerArray(slotInfo.keyClass(), heap.hMetaAccess.lookupJavaType(slotInfo.keyClass().arrayType()).getWrapped(),
                                            hUniverse.getSnippetReflection());

                            heap.addConstant(imageHeapArray, true, addReason);

                            multiLayerArray = imageHeapArray;
                        }

                        yield multiLayerArray;
                    }
                };

                var previous = mappingInfo.put(createdConstant, slotInfo.slotNum());
                assert previous == null : previous;
            }
            constantToTableSlotMap = Map.copyOf(mappingInfo);

        } else {
            constantToTableSlotMap = Map.of();
        }

    }
}

enum SlotRecordKind {
    APPLICATION_LAYER_SINGLETON,
    MULTI_LAYERED_SINGLETON

}

record SlotInfo(Class<?> keyClass,
                int slotNum,
                SlotRecordKind recordKind) {

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        SlotInfo slotInfo = (SlotInfo) object;
        return slotNum == slotInfo.slotNum && Objects.equals(keyClass, slotInfo.keyClass) && recordKind == slotInfo.recordKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyClass, slotNum, recordKind);
    }
}

class CrossLayerSingletonMappingInfo extends LoadImageSingletonFactory implements LayeredImageSingleton {
    /**
     * Map of slot infos created in prior layers.
     */
    private final Map<Class<?>, SlotInfo> priorKeyToSlotInfoMap;

    /**
     * Map of all MultiLayer objects created in prior layers.
     */
    private final Map<Class<?>, List<Integer>> priorKeyToSingletonObjectIDsMap;

    /**
     * Map of all slot infos (past & present). Is created in {@link #assignSlots}.
     */
    private Map<Class<?>, SlotInfo> currentKeyToSlotInfoMap;

    /**
     * Map of constant identifiers for MultiLayer objects installed in this layer.
     */
    private final Map<Class<?>, Integer> layerKeyToObjectIDMap = new HashMap<>();

    /**
     * Cache for created LoadImageSingletonDataImpl objects within the current layer.
     */
    private final Map<Class<?>, LoadImageSingletonDataImpl> layerKeyToSingletonDataMap = new ConcurrentHashMap<>();

    boolean sealedSingletonLookup = false;
    private CGlobalData<Pointer> singletonTableStart;
    int referenceSize = 0;

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    CrossLayerSingletonMappingInfo() {
        priorKeyToSlotInfoMap = Map.of();
        priorKeyToSingletonObjectIDsMap = Map.of();
    }

    CrossLayerSingletonMappingInfo(Map<Class<?>, SlotInfo> priorKeyToSlotInfoMap, Map<Class<?>, List<Integer>> priorKeyToSingletonObjectIDsMap) {
        this.priorKeyToSlotInfoMap = priorKeyToSlotInfoMap;
        this.priorKeyToSingletonObjectIDsMap = priorKeyToSingletonObjectIDsMap;
    }

    void recordConstantID(Class<?> keyClass, int objectID) {
        var previous = layerKeyToObjectIDMap.put(keyClass, objectID);
        assert previous == null : previous;
    }

    Map<Class<?>, SlotInfo> getCurrentKeyToSlotInfoMap() {
        assert currentKeyToSlotInfoMap != null;
        return currentKeyToSlotInfoMap;
    }

    Map<Class<?>, SlotInfo> getPriorKeyToSlotInfoMap() {
        return priorKeyToSlotInfoMap;
    }

    List<Integer> getPriorLayerObjectIDs(Class<?> keyClass) {
        return priorKeyToSingletonObjectIDsMap.getOrDefault(keyClass, List.of());
    }

    private LoadImageSingletonData getImageSingletonInfo(Class<?> keyClass, SlotRecordKind kind) {
        assert !sealedSingletonLookup;
        assert !ImageLayerBuildingSupport.buildingApplicationLayer() : "Singletons can always be directly folded in the application layer";

        /*
         * First check to see if something is already cached.
         */
        LoadImageSingletonDataImpl result = layerKeyToSingletonDataMap.get(keyClass);
        if (result != null) {
            return result;
        }

        LoadImageSingletonDataImpl newInfo = new LoadImageSingletonDataImpl(keyClass, kind);
        result = layerKeyToSingletonDataMap.computeIfAbsent(keyClass, k -> newInfo);
        if (result != newInfo) {
            /*
             * A different thread added this singleton in the meantime.
             */
            return result;
        }

        SlotInfo priorSlotInfo = priorKeyToSlotInfoMap.get(keyClass);
        if (priorSlotInfo != null && priorSlotInfo.recordKind() != kind) {
            VMError.shouldNotReachHere("A singleton cannot implement both %s and %s", priorSlotInfo.recordKind(), kind);
        }

        return newInfo;
    }

    @Override
    protected LoadImageSingletonData getApplicationLayerOnlyImageSingletonInfo(Class<?> keyClass) {
        assert !ImageLayerBuildingSupport.buildingApplicationLayer() : "In the application layer one can directly load the constant";
        return getImageSingletonInfo(keyClass, SlotRecordKind.APPLICATION_LAYER_SINGLETON);
    }

    @Override
    protected LoadImageSingletonData getLayeredImageSingletonInfo(Class<?> keyClass) {
        return getImageSingletonInfo(keyClass, SlotRecordKind.MULTI_LAYERED_SINGLETON);
    }

    void assignSlots(HostedMetaAccess metaAccess) {
        sealedSingletonLookup = true;

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            // within the application layer we directly load the constant
            referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            singletonTableStart = CGlobalDataFactory.forSymbol(CROSS_LAYER_SINGLETON_TABLE_SYMBOL);
        }

        var priorMax = priorKeyToSlotInfoMap.values().stream().mapToInt(SlotInfo::slotNum).max();

        int nextFreeSlot = priorMax.isPresent() ? priorMax.getAsInt() + 1 : 0;
        currentKeyToSlotInfoMap = new HashMap<>(priorKeyToSlotInfoMap);
        var sortedEntries = layerKeyToSingletonDataMap.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getName())).toList();
        for (var entry : sortedEntries) {
            int slotAssignment;
            LoadImageSingletonDataImpl info = entry.getValue();
            var hType = metaAccess.lookupJavaType(info.getLoadType());
            if (hType.isInstantiated()) {
                Class<?> keyClass = entry.getKey();
                SlotInfo slotInfo = priorKeyToSlotInfoMap.get(entry.getKey());

                if (slotInfo == null) {
                    /*
                     * Singleton was not assigned a slot in the prior layer. Need to assign
                     * singleton a slot now.
                     */
                    slotAssignment = nextFreeSlot++;
                    slotInfo = new SlotInfo(keyClass, slotAssignment, info.kind);
                }
                var prior = currentKeyToSlotInfoMap.put(keyClass, slotInfo);
                assert prior == null : prior;
            }
        }
    }

    private static String getKeyClassName(Class<?> clazz) {
        return clazz.getName();
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        /*
         * Write out all relevant information.
         */
        List<String> keyClasses = new ArrayList<>();
        List<Integer> slotAssignments = new ArrayList<>();
        List<String> slotKinds = new ArrayList<>();

        /*
         * Write out information about the assigned slots
         */
        for (var info : currentKeyToSlotInfoMap.values()) {
            String keyName = getKeyClassName(info.keyClass());

            keyClasses.add(keyName);
            slotAssignments.add(info.slotNum());
            slotKinds.add(info.recordKind().name());
        }

        writer.writeStringList("keyClasses", keyClasses);
        writer.writeIntList("slotAssignments", slotAssignments);
        writer.writeStringList("slotKinds", slotKinds);

        /*
         * Write out all multi-layered image singletons seen.
         */

        Map<Class<?>, List<Integer>> currentKeyToSingletonObjectIDsMap = new HashMap<>(priorKeyToSingletonObjectIDsMap);
        for (var keyClass : LayeredImageSingletonSupport.singleton().getMultiLayeredImageSingletonKeys()) {
            Integer id = layerKeyToObjectIDMap.get(keyClass);
            assert id != null : "Missing multiLayerKey " + keyClass;
            currentKeyToSingletonObjectIDsMap.compute(keyClass, (k, v) -> {
                if (v == null) {
                    return List.of(id);
                } else {
                    // don't want to affect the list created before
                    var newList = new ArrayList<>(v);
                    newList.add(id);
                    return newList;
                }
            });
        }

        List<String> multiLayerKeyNames = new ArrayList<>();
        List<String> multiLayerKeyClasses = new ArrayList<>();
        int count = 0;
        for (var entry : currentKeyToSingletonObjectIDsMap.entrySet()) {
            String keyClassName = getKeyClassName(entry.getKey());

            String idListKey = String.format("priorObjectIds-%s", count++);
            writer.writeIntList(idListKey, entry.getValue());

            multiLayerKeyNames.add(idListKey);
            multiLayerKeyClasses.add(keyClassName);
        }

        writer.writeStringList("multiLayerClassNames", multiLayerKeyClasses);
        writer.writeStringList("multiLayerKeyNames", multiLayerKeyNames);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        Iterator<String> keyClasses = loader.readStringList("keyClasses").iterator();
        Iterator<Integer> slotAssignments = loader.readIntList("slotAssignments").iterator();
        Iterator<String> slotKinds = loader.readStringList("slotKinds").iterator();

        Map<Class<?>, SlotInfo> keyClassToSlotInfoMap = new HashMap<>();

        while (keyClasses.hasNext()) {
            String keyName = keyClasses.next();
            Class<?> keyClass = ReflectionUtil.lookupClass(false, keyName);
            int slotAssignment = slotAssignments.next();
            SlotRecordKind slotKind = SlotRecordKind.valueOf(slotKinds.next());

            Object previous = keyClassToSlotInfoMap.put(keyClass, new SlotInfo(keyClass, slotAssignment, slotKind));
            assert previous == null : previous;
        }

        Map<Class<?>, List<Integer>> keyClassToObjectIDListMap = new HashMap<>();
        keyClasses = loader.readStringList("multiLayerClassNames").iterator();
        Iterator<String> idKeyNames = loader.readStringList("multiLayerKeyNames").iterator();
        while (keyClasses.hasNext()) {
            String keyClassName = keyClasses.next();
            Class<?> keyClass = ReflectionUtil.lookupClass(false, keyClassName);
            String idKeyName = idKeyNames.next();
            var list = loader.readIntList(idKeyName);
            assert list != null;
            Object previous = keyClassToObjectIDListMap.put(keyClass, list);
            assert previous == null;
        }

        return new CrossLayerSingletonMappingInfo(Map.copyOf(keyClassToSlotInfoMap), Map.copyOf(keyClassToObjectIDListMap));
    }

    class LoadImageSingletonDataImpl implements LoadImageSingletonData {

        private final Class<?> key;
        private final SlotRecordKind kind;

        LoadImageSingletonDataImpl(Class<?> key, SlotRecordKind kind) {
            this.key = key;
            this.kind = kind;
        }

        @Override
        public Class<?> getLoadType() {
            return kind == SlotRecordKind.APPLICATION_LAYER_SINGLETON ? key : key.arrayType();
        }

        @Override
        public SingletonAccessInfo getAccessInfo() {
            assert singletonTableStart != null;
            CGlobalDataInfo cglobal = CGlobalDataFeature.singleton().registerAsAccessedOrGet(singletonTableStart);
            int slotNum = currentKeyToSlotInfoMap.get(key).slotNum();
            return new SingletonAccessInfo(cglobal, slotNum * referenceSize);
        }
    }
}
