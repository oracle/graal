/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.UniverseBuilder;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This class keeps track of the location of static fields assigned in previous layers as well as
 * what fields have been registered as having their installation deferred until the application
 * layer.
 */
@AutomaticallyRegisteredImageSingleton(value = LayeredClassInitialization.class, onlyWith = BuildingImageLayerPredicate.class)
public class LayeredStaticFieldSupport extends LayeredClassInitialization implements LayeredImageSingleton {
    /**
     * In the initial layer, this refers to fields which must wait until the app layer to be
     * installed.
     *
     * In the app layer, this refers to fields which were referenced in the prior layer (i.e. have
     * status {@link LayerAssignmentStatus#APP_LAYER_DEFERRED}).
     */
    final Set<Object> appLayerFields;

    final Map<AnalysisField, LayerAssignmentStatus> assignmentStatusMap;
    final Map<AnalysisField, Integer> priorInstalledLayerMap;
    final Map<AnalysisField, Integer> priorInstalledLocationMap;

    public static final String appLayerPrimitiveStaticFieldsBaseName = "APPLAYER_PRIMITIVE_STATICFIELDSBASE";
    public static final String appLayerObjectStaticFieldsBaseName = "APPLAYER_OBJECT_STATICFIELDSBASE";

    private volatile ImageHeapRelocatableConstant appLayerPrimitiveStaticFieldsBase;
    private volatile ImageHeapRelocatableConstant appLayerObjectStaticFieldsBase;

    final UniverseBuilder.StaticFieldOffsets appLayerStaticFieldOffsets;

    private final boolean inAppLayer;

    LayeredStaticFieldSupport() {
        this(ConcurrentHashMap.newKeySet(), new UniverseBuilder.StaticFieldOffsets());
    }

    private LayeredStaticFieldSupport(Set<Object> appLayerFields, UniverseBuilder.StaticFieldOffsets appLayerStaticFieldOffsets) {
        this.appLayerFields = appLayerFields;
        assignmentStatusMap = new ConcurrentHashMap<>();
        inAppLayer = ImageLayerBuildingSupport.buildingApplicationLayer();
        priorInstalledLayerMap = inAppLayer ? new ConcurrentHashMap<>() : null;
        priorInstalledLocationMap = inAppLayer ? new ConcurrentHashMap<>() : null;
        this.appLayerStaticFieldOffsets = appLayerStaticFieldOffsets;
    }

    /**
     * Tracks to what layer a static field has been installed/assigned.
     */
    public enum LayerAssignmentStatus {
        /**
         * This field has yet to be assigned a layer.
         */
        UNDECIDED,
        /**
         * Was installed in a prior layer.
         */
        PRIOR_LAYER,
        /**
         * This field has been registered as being deferred to the app layer, but has yet to be
         * referenced.
         */
        APP_LAYER_REQUESTED,
        /**
         * This field has both been registered as being deferred to the app layer and also accessed
         * in a shared layer. Hence, it must be installed in the app layer.
         */
        APP_LAYER_DEFERRED,
    }

    public static LayeredStaticFieldSupport singleton() {
        return (LayeredStaticFieldSupport) ImageSingletons.lookup(LayeredClassInitialization.class);
    }

    @SuppressWarnings("unchecked")
    private static AnalysisField getAnalysisField(Object obj) {
        if (obj instanceof AnalysisField aField) {
            return aField;
        } else {
            var supplier = (Supplier<AnalysisField>) obj;
            return supplier.get();
        }
    }

    public void ensureInitializedFromFieldData(AnalysisField aField, SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisField.Reader fieldData) {
        Integer priorInstalledLayerNum = fieldData.getPriorInstalledLayerNum();
        Object result = priorInstalledLayerMap.computeIfAbsent(aField, f -> priorInstalledLayerNum);
        assert priorInstalledLayerNum.equals(result) : result;
        LayerAssignmentStatus assignmentStatus = LayerAssignmentStatus.values()[fieldData.getAssignmentStatus()];
        result = assignmentStatusMap.computeIfAbsent(aField, f -> assignmentStatus);
        assert assignmentStatus.equals(result);
        Integer priorInstalledLocation = fieldData.getLocation();
        result = priorInstalledLocationMap.computeIfAbsent(aField, f -> priorInstalledLocation);
        assert priorInstalledLocation.equals(result);
    }

    private void installFieldInAppLayer(Field field, MetaAccessProvider meta) {
        assert !inAppLayer && !BuildPhaseProvider.isAnalysisFinished();

        AnalysisField aField = (AnalysisField) meta.lookupJavaField(field);
        var added = appLayerFields.add(aField);
        assert added;

        /*
         * Trigger build-time initialization of the class if it was not already initialized (and the
         * class is registered as build-time initialized).
         */
        boolean initialized = aField.getDeclaringClass().isInitialized();
        AnalysisError.guarantee(initialized, "Only fields for classes which are build-time initialized can be declared as deferred to application layer: %s", aField);

        // register this field as requiring app layer
        var previous = assignmentStatusMap.put(aField, LayerAssignmentStatus.APP_LAYER_REQUESTED);
        if (previous != null) {
            throw AnalysisError.userError(String.format("Field has a prior assignment. This is due to registering an app layer deferred field too late. Field: %s. Previous: %s", field, previous));
        }

        // ensure the appropriate future layer constant exists
        var registry = CrossLayerConstantRegistry.singletonOrNull();
        if (field.getType().isPrimitive()) {
            if (appLayerPrimitiveStaticFieldsBase == null) {
                AnalysisType futureType = (AnalysisType) meta.lookupJavaType(byte[].class);
                synchronized (this) {
                    if (appLayerPrimitiveStaticFieldsBase == null) {
                        appLayerPrimitiveStaticFieldsBase = (ImageHeapRelocatableConstant) registry.registerFutureHeapConstant(appLayerPrimitiveStaticFieldsBaseName, futureType);
                        ImageHeapRelocatableConstantSupport.singleton().registerLoadableConstant(appLayerPrimitiveStaticFieldsBase);
                    }
                }
            }
        } else if (appLayerObjectStaticFieldsBase == null) {
            AnalysisType futureType = (AnalysisType) meta.lookupJavaType(Object[].class);
            synchronized (this) {
                if (appLayerObjectStaticFieldsBase == null) {
                    appLayerObjectStaticFieldsBase = (ImageHeapRelocatableConstant) registry.registerFutureHeapConstant(appLayerObjectStaticFieldsBaseName, futureType);
                    ImageHeapRelocatableConstantSupport.singleton().registerLoadableConstant(appLayerObjectStaticFieldsBase);
                }
            }
        }
    }

    @Override
    void initializeClassInAppLayer(Class<?> c, MetaAccessProvider meta) {
        for (var field : c.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                installFieldInAppLayer(field, meta);
            }
        }
    }

    public LayerAssignmentStatus getAssignmentStatus(AnalysisField analysisField) {
        return assignmentStatusMap.computeIfAbsent(analysisField, (f) -> {
            if (!(inAppLayer && analysisField.isInBaseLayer())) {
                return LayerAssignmentStatus.UNDECIDED;
            }
            throw VMError.shouldNotReachHere(String.format("Base analysis field assignment status queried before it is initialized: %s", analysisField));
        });
    }

    public int getPriorInstalledLayerNum(AnalysisField analysisField) {
        if (!(inAppLayer && analysisField.isInBaseLayer())) {
            return MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;
        }

        assert priorInstalledLayerMap.containsKey(analysisField);
        return priorInstalledLayerMap.get(analysisField);
    }

    public boolean preventConstantFolding(AnalysisField aField) {
        var state = getAssignmentStatus(aField);
        return switch (state) {
            case UNDECIDED, PRIOR_LAYER -> false;
            case APP_LAYER_REQUESTED, APP_LAYER_DEFERRED -> !inAppLayer;
        };
    }

    public boolean installableInLayer(AnalysisField aField) {
        var state = getAssignmentStatus(aField);
        return switch (state) {
            case UNDECIDED -> {
                assert getPriorInstalledLayerNum(aField) == MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;
                yield true;
            }
            case PRIOR_LAYER -> {
                assert aField.isInBaseLayer();
                yield false;
            }
            case APP_LAYER_REQUESTED, APP_LAYER_DEFERRED -> inAppLayer;
        };
    }

    public UniverseBuilder.StaticFieldOffsets getAppLayerStaticFieldOffsets() {
        assert inAppLayer;
        return appLayerStaticFieldOffsets;
    }

    public void reinitializeKnownFields(List<HostedField> staticFields) {
        assert ImageLayerBuildingSupport.buildingExtensionLayer();
        int currentLayerNum = DynamicImageLayerInfo.getCurrentLayerNumber();
        for (var hField : staticFields) {
            AnalysisField aField = hField.getWrapped();
            LayerAssignmentStatus state = getAssignmentStatus(aField);
            if (state == LayerAssignmentStatus.PRIOR_LAYER) {
                int layerNum = getPriorInstalledLayerNum(aField);
                assert priorInstalledLocationMap.containsKey(aField);
                int location = priorInstalledLocationMap.get(aField);
                hField.setLocation(location, layerNum);
            } else if (state == LayerAssignmentStatus.APP_LAYER_DEFERRED) {
                assert inAppLayer;
                assert priorInstalledLocationMap.containsKey(aField);
                int location = priorInstalledLocationMap.get(aField);
                hField.setLocation(location, currentLayerNum);
            }
        }
    }

    public boolean skipStaticField(HostedField field, Function<HostedField, Boolean> traditionalSkipFieldLogic) {
        AnalysisField aField = field.getWrapped();
        LayerAssignmentStatus state = getAssignmentStatus(aField);
        return switch (state) {
            case UNDECIDED -> traditionalSkipFieldLogic.apply(field);
            /* This field's location has already been decided. */
            case PRIOR_LAYER -> false;
            case APP_LAYER_REQUESTED -> {
                if (inAppLayer) {
                    /*
                     * If the value was requested in the prior layers but was never used, then the
                     * regular logic can proceed.
                     */
                    yield traditionalSkipFieldLogic.apply(field);
                } else {
                    /*
                     * If a field is accessed, then we do not constant fold the value, for we need
                     * to ensure the value created in the app layer is used.
                     */
                    boolean isAccessed = aField.isAccessed();
                    if (isAccessed) {
                        var previous = assignmentStatusMap.put(aField, LayerAssignmentStatus.APP_LAYER_DEFERRED);
                        assert previous == LayerAssignmentStatus.APP_LAYER_REQUESTED;
                    }
                    yield !isAccessed;
                }
            }
            /* This field must be assigned a location. */
            case APP_LAYER_DEFERRED -> false;
        };
    }

    public boolean wasReinitialized(HostedField field) {
        var state = getAssignmentStatus(field.getWrapped());
        assert state == LayerAssignmentStatus.PRIOR_LAYER || state == LayerAssignmentStatus.APP_LAYER_DEFERRED;

        return true;
    }

    /*
     * Since we currently are limited to 2 layers, we know the app layer will always be the 2nd
     * layer (layerNum === 1). In the future we will need to add a marker id to the hosted field and
     * add additional branching logic when reading the hosted field to return this value.
     */
    public static int getAppLayerNumber() {
        return 1;
    }

    public UniverseBuilder.StaticFieldOffsets getFutureLayerOffsets(HostedField field, int layerNum) {
        assert ImageLayerBuildingSupport.buildingSharedLayer();
        AnalysisField aField = field.getWrapped();
        VMError.guarantee(getAssignmentStatus(aField) == LayerAssignmentStatus.APP_LAYER_DEFERRED);
        assert layerNum == getAppLayerNumber() : layerNum;

        return appLayerStaticFieldOffsets;
    }

    public FloatingNode getAppLayerStaticFieldsBaseReplacement(boolean primitive, LoweringTool tool, StructuredGraph graph) {
        ImageHeapRelocatableConstant constant = primitive ? appLayerPrimitiveStaticFieldsBase : appLayerObjectStaticFieldsBase;
        assert constant != null;
        return ImageHeapRelocatableConstantSupport.singleton().emitLoadConstant(graph, tool.getMetaAccess(), constant);
    }

    public JavaConstant getAppLayerStaticFieldBaseConstant(boolean primitive) {
        var result = primitive ? appLayerPrimitiveStaticFieldsBase : appLayerObjectStaticFieldsBase;
        assert result != null;
        return result;
    }

    void loadAllAppLayerFields() {
        appLayerFields.forEach(LayeredStaticFieldSupport::getAnalysisField);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        writer.writeInt("appLayerPrimitiveFieldStartingOffset", appLayerStaticFieldOffsets.nextPrimitiveField);
        writer.writeInt("appLayerObjectFieldStartingOffset", appLayerStaticFieldOffsets.nextObjectField);

        HostedUniverse hUniverse = ((SVMImageLayerWriter.ImageSingletonWriterImpl) writer).getHostedUniverse();
        List<Integer> knownLocations = new ArrayList<>();
        appLayerFields.forEach(obj -> {
            AnalysisField aField = getAnalysisField(obj);
            HostedField hField = hUniverse.lookup(aField);
            if (hField.hasLocation()) {
                assert getAssignmentStatus(aField) == LayerAssignmentStatus.APP_LAYER_DEFERRED;
                knownLocations.add(aField.getId());
            }
        });

        writer.writeIntList("appLayerFieldsWithKnownLocations", knownLocations);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {

        Set<Object> appLayerFieldsWithKnownLocations = new HashSet<>();
        for (int id : loader.readIntList("appLayerFieldsWithKnownLocations")) {
            Supplier<AnalysisField> aFieldSupplier = () -> HostedImageLayerBuildingSupport.singleton().getLoader().getAnalysisFieldForBaseLayerId(id);
            appLayerFieldsWithKnownLocations.add(aFieldSupplier);
        }

        var appLayerStaticFieldsOffsets = new UniverseBuilder.StaticFieldOffsets();
        appLayerStaticFieldsOffsets.nextPrimitiveField = loader.readInt("appLayerPrimitiveFieldStartingOffset");
        appLayerStaticFieldsOffsets.nextObjectField = loader.readInt("appLayerObjectFieldStartingOffset");
        return new LayeredStaticFieldSupport(Collections.unmodifiableSet(appLayerFieldsWithKnownLocations), appLayerStaticFieldsOffsets);
    }
}

@AutomaticallyRegisteredFeature
class LayeredStaticFieldSupportBaseLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingInitialLayer();
    }

    /**
     * Register all application layered fields declared via the commandline.
     */
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        var metaAccess = config.getMetaAccess();
        for (String className : SubstrateOptions.ApplicationLayerInitializedClasses.getValue().values()) {
            LayeredClassInitialization.singleton().initializeClassInAppLayer(config.getImageClassLoader().findClassOrFail(className), metaAccess);
        }
    }
}

@AutomaticallyRegisteredFeature
class LayeredStaticFieldSupportAppLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingApplicationLayer();
    }

    /**
     * We must ensure all static fields with a known location are loaded so that they are installed
     * during image heap writing.
     */
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        LayeredStaticFieldSupport.singleton().loadAllAppLayerFields();
    }

    /**
     * We must finalize the future constants used within the base layer to ensure they are linked.
     */
    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        var singleton = CrossLayerConstantRegistry.singletonOrNull();
        if (singleton.constantExists(LayeredStaticFieldSupport.appLayerPrimitiveStaticFieldsBaseName)) {
            singleton.finalizeFutureHeapConstant(LayeredStaticFieldSupport.appLayerPrimitiveStaticFieldsBaseName, StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields());
        }
        if (singleton.constantExists(LayeredStaticFieldSupport.appLayerObjectStaticFieldsBaseName)) {
            singleton.finalizeFutureHeapConstant(LayeredStaticFieldSupport.appLayerObjectStaticFieldsBaseName, StaticFieldsSupport.getCurrentLayerStaticObjectFields());
        }
    }
}
