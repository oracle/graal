/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.HeapDumpSupport;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.encoder.SymbolEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.dump.HProfType;
import com.oracle.svm.core.heap.dump.HeapDumpMetadata;
import com.oracle.svm.core.heap.dump.HeapDumpShutdownHook;
import com.oracle.svm.core.heap.dump.HeapDumpStartupHook;
import com.oracle.svm.core.heap.dump.HeapDumpSupportImpl;
import com.oracle.svm.core.heap.dump.HeapDumpWriter;
import com.oracle.svm.core.heap.dump.HeapDumping;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Heap dumping on Native Image needs some extra metadata about all the classes and fields that are
 * present in the image. The necessary information is encoded as binary data at image build time
 * (see {@link #encodeMetadata}}). When the heap dumping is triggered at run-time, the metadata is
 * decoded on the fly (see {@link HeapDumpMetadata}) and used for writing the heap dump (see
 * {@link HeapDumpWriter}).
 */
@AutomaticallyRegisteredFeature
public class HeapDumpFeature implements InternalFeature {
    private boolean isDataFieldReachable;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        /*
         * Include the feature unconditionally (all platforms, even unknown platforms). The static
         * analysis ensures that the code and all its data are only present in the final image if
         * the heap dumping infrastructure is actually called by any code (e.g.,
         * VMRuntime.dumpHeap(...) or --enable-monitoring=heapdump).
         */
        return true;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        HeapDumpSupportImpl heapDumpSupport = new HeapDumpSupportImpl();
        ImageSingletons.add(HeapDumpSupport.class, heapDumpSupport);
        ImageSingletons.add(HeapDumping.class, heapDumpSupport);

        if (ImageLayerBuildingSupport.firstImageBuild()) {
            ImageSingletons.add(HeapDumpMetadata.class, new HeapDumpMetadata());
        }
        ImageSingletons.add(HeapDumpMetadata.HeapDumpEncodedData.class, new HeapDumpMetadata.HeapDumpEncodedData());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* Heap dumping on signal and on OutOfMemoryError are opt-in features. */
        if (VMInspectionOptions.hasHeapDumpSupport()) {
            RuntimeSupport.getRuntimeSupport().addStartupHook(new HeapDumpStartupHook());
            RuntimeSupport.getRuntimeSupport().addShutdownHook(new HeapDumpShutdownHook());
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        Field dataField = ReflectionUtil.lookupField(HeapDumpMetadata.HeapDumpEncodedData.class, "data");
        isDataFieldReachable = access.isReachable(dataField);
    }

    @Override
    public void afterCompilation(Feature.AfterCompilationAccess access) {
        if (isDataFieldReachable) {
            AfterCompilationAccessImpl accessImpl = (AfterCompilationAccessImpl) access;
            byte[] metadata = encodeMetadata(accessImpl.getTypes());
            HeapDumpMetadata.HeapDumpEncodedData.setData(metadata);
            access.registerAsImmutable(metadata);
        }
    }

    /**
     * This method writes the metadata that is needed for heap dumping into one large byte[] (see
     * {@link HeapDumpMetadata} for more details).
     */
    private static byte[] encodeMetadata(Collection<? extends SharedType> types) {
        UnsafeArrayTypeWriter output = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        encodeMetadata(output, types);

        int length = TypeConversion.asS4(output.getBytesWritten());
        return output.toArray(new byte[length]);
    }

    private static void encodeMetadata(UnsafeArrayTypeWriter output, Collection<? extends SharedType> types) {
        /* Write header. */
        long totalFieldCountOffset = output.getBytesWritten();
        output.putS4(0); // total field count (patched later on)
        long classCountOffset = output.getBytesWritten();
        output.putS4(0); // class count (patched later on)
        long fieldNameCountOffset = output.getBytesWritten();
        output.putS4(0); // field name count (patched later on)

        EconomicMap<String, Integer> fieldNames = EconomicMap.create();
        int priorFieldNamesSize = 0;
        int prevLayersMaxTypeId = -1;
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            LayeredHeapDumpEncodedTypesTracker layeredTracking = ImageSingletons.lookup(LayeredHeapDumpEncodedTypesTracker.class);
            prevLayersMaxTypeId = DynamicImageLayerInfo.singleton().getPreviousMaxTypeId();
            /*
             * We only need to encode newly added field names within this layer's metadata. So, we
             * pre-populate the map with all the field names of previous layers.
             */
            for (String fieldName : layeredTracking.getPriorFieldNames()) {
                fieldNames.put(fieldName, priorFieldNamesSize);
                priorFieldNamesSize++;
            }
        }

        /* Write the class and field information. */
        int totalFieldCount = 0;
        int classCount = 0;
        SymbolEncoder symbolEncoder = SymbolEncoder.singleton();
        for (SharedType type : types) {
            if (type.isInstanceClass()) {
                int typeId = type.getHub().getTypeID();
                if (typeId <= prevLayersMaxTypeId) {
                    /* This type's information has been installed in a prior layer. */
                    continue;
                }
                ArrayList<SharedField> instanceFields = collectFields(type.getInstanceFields(false));
                ArrayList<SharedField> staticFields = collectFields(type.getStaticFields());
                if (instanceFields.isEmpty() && staticFields.isEmpty()) {
                    continue;
                }

                classCount++;
                totalFieldCount += instanceFields.size() + staticFields.size();

                output.putUV(typeId);
                output.putUV(instanceFields.size());
                output.putUV(staticFields.size());

                /* Write direct instance fields. */
                for (SharedField field : instanceFields) {
                    encodeField(field, output, fieldNames, symbolEncoder);
                }

                /* Write static fields. */
                for (SharedField field : staticFields) {
                    encodeField(field, output, fieldNames, symbolEncoder);
                }
            }
        }

        /* Patch the header. */
        output.patchS4(totalFieldCount, totalFieldCountOffset);
        output.patchS4(classCount, classCountOffset);
        output.patchS4(fieldNames.size() - priorFieldNamesSize, fieldNameCountOffset);

        /* Write the newly added field names. */
        boolean recordFieldNames = !ImageLayerBuildingSupport.lastImageBuild();
        List<String> encodedFieldNames = recordFieldNames ? new ArrayList<>() : null;
        int index = 0;
        MapCursor<String, Integer> cursor = fieldNames.getEntries();
        while (cursor.advance()) {
            assert cursor.getValue() == index;
            if (index >= priorFieldNamesSize) {
                byte[] utf8 = cursor.getKey().getBytes(StandardCharsets.UTF_8);
                output.putUV(utf8.length);
                for (byte b : utf8) {
                    output.putS1(b);
                }
            }
            if (recordFieldNames) {
                encodedFieldNames.add(cursor.getKey());
            }
            index++;
        }
        if (recordFieldNames) {
            ImageSingletons.lookup(LayeredHeapDumpEncodedTypesTracker.class).recordEncodedFieldNames(encodedFieldNames);
        }
    }

    private static ArrayList<SharedField> collectFields(ResolvedJavaField[] input) {
        /* Collect all fields that have a location. */
        ArrayList<SharedField> result = new ArrayList<>();
        for (ResolvedJavaField f : input) {
            if (f instanceof SharedField field) {
                if (field.getLocation() >= 0) {
                    result.add(field);
                }
            }
        }

        /* Sort fields by their location. */
        result.sort(Comparator.comparingInt(SharedField::getLocation));
        return result;
    }

    private static void encodeField(SharedField field, UnsafeArrayTypeWriter output, EconomicMap<String, Integer> fieldNames, SymbolEncoder symbolEncoder) {
        int location = field.getLocation();
        assert location >= 0;
        output.putU1(getType(field).ordinal());
        String encodedFieldName = symbolEncoder.encodeField(field.getName(), field.getDeclaringClass().getClass());
        output.putUV(addFieldName(encodedFieldName, fieldNames));
        output.putUV(location);
    }

    private static int addFieldName(String fieldName, EconomicMap<String, Integer> fieldNames) {
        Integer fieldNameIndex = fieldNames.get(fieldName);
        if (fieldNameIndex != null) {
            return fieldNameIndex;
        }

        int result = fieldNames.size();
        fieldNames.put(fieldName, result);
        return result;
    }

    private static HProfType getType(SharedField field) {
        return switch (field.getStorageKind()) {
            case Object -> HProfType.NORMAL_OBJECT;
            case Boolean -> HProfType.BOOLEAN;
            case Char -> HProfType.CHAR;
            case Float -> HProfType.FLOAT;
            case Double -> HProfType.DOUBLE;
            case Byte -> HProfType.BYTE;
            case Short -> HProfType.SHORT;
            case Int -> HProfType.INT;
            case Long -> HProfType.LONG;
            default -> throw VMError.shouldNotReachHere("Unexpected storage kind.");
        };
    }
}

@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
class LayeredHeapDumpEncodedTypesTracker implements LayeredImageSingleton {
    private List<String> encodedFieldNames;
    private final List<String> priorFieldNames;

    LayeredHeapDumpEncodedTypesTracker() {
        this(List.of());
    }

    LayeredHeapDumpEncodedTypesTracker(List<String> priorFieldNames) {
        this.priorFieldNames = priorFieldNames;
    }

    void recordEncodedFieldNames(List<String> nameList) {
        this.encodedFieldNames = nameList;
    }

    public List<String> getPriorFieldNames() {
        return priorFieldNames;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        writer.writeStringList("encodedFieldNames", encodedFieldNames);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        List<String> encodedFieldNames = Collections.unmodifiableList(loader.readStringList("encodedFieldNames"));
        return new LayeredHeapDumpEncodedTypesTracker(encodedFieldNames);
    }
}
