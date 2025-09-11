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
package com.oracle.svm.core.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.traits.BuiltinTraits;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;

/**
 * This singleton stores the {@link ReflectionMetadata} of each {@link DynamicHub} across layers to
 * allow registering elements for reflection in extension layers too.
 */
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
@SingletonTraits(access = BuiltinTraits.AllAccess.class, layeredCallbacks = LayeredReflectionMetadataSingleton.LayeredCallbacks.class, layeredInstallationKind = SingletonLayeredInstallationKind.MultiLayer.class)
public class LayeredReflectionMetadataSingleton {
    private static final String LAYERED_REFLECTION_METADATA_HUBS = "layered reflection metadata hubs";
    private static final String LAYERED_REFLECTION_METADATA_CLASS_FLAGS = "layered reflection metadata classFlags";

    private final EconomicMap<Integer, ImageReflectionMetadata> reflectionMetadataMap = EconomicMap.create();

    /**
     * The class flags registered in previous layers. This map is used to check if the class flags
     * in the current layer are the same as the previous layer. If they are the same and the rest of
     * the reflection metadata is empty, the class can be skipped. If the class flags of the current
     * layer are not a subset of the previous layer class flags, the new class flags become the
     * combination of both class flags through an or statement.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<Integer, Integer> previousLayerClassFlags;

    LayeredReflectionMetadataSingleton() {
        this(Map.of());
    }

    LayeredReflectionMetadataSingleton(Map<Integer, Integer> previousLayerClassFlags) {
        this.previousLayerClassFlags = previousLayerClassFlags;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static LayeredReflectionMetadataSingleton currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(LayeredReflectionMetadataSingleton.class, false, true);
    }

    public static LayeredReflectionMetadataSingleton[] singletons() {
        return MultiLayeredImageSingleton.getAllLayers(LayeredReflectionMetadataSingleton.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReflectionMetadata(DynamicHub hub, ImageReflectionMetadata reflectionMetadata) {
        /* GR-63472: Two different classes could have the same name in different class loaders */
        assert !reflectionMetadataMap.containsKey(hub.getTypeID()) : "The hub %s was added twice in the same layered reflection metadata".formatted(hub);
        if (isClassFlagsSubsetOfPreviousLayer(hub.getTypeID(), reflectionMetadata) && isReflectionMetadataEmpty(reflectionMetadata)) {
            return;
        }
        reflectionMetadataMap.put(hub.getTypeID(), reflectionMetadata);
    }

    private boolean isClassFlagsSubsetOfPreviousLayer(int hub, ReflectionMetadata reflectionMetadata) {
        int previousLayerFlags = previousLayerClassFlags.getOrDefault(hub, 0);
        return getCombinedClassFlags(reflectionMetadata, previousLayerFlags) == previousLayerFlags;
    }

    private static int getCombinedClassFlags(ReflectionMetadata reflectionMetadata, int previousLayerFlags) {
        return previousLayerFlags | reflectionMetadata.getClassFlags();
    }

    private static boolean isReflectionMetadataEmpty(ImageReflectionMetadata reflectionMetadata) {
        return reflectionMetadata.fieldsEncodingIndex == -1 && reflectionMetadata.methodsEncodingIndex == -1 &&
                        reflectionMetadata.constructorsEncodingIndex == -1 && reflectionMetadata.recordComponentsEncodingIndex == -1;
    }

    public ImageReflectionMetadata getReflectionMetadata(DynamicHub hub) {
        return reflectionMetadataMap.get(hub.getTypeID());
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks() {

                @Override
                public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
                    LayeredReflectionMetadataSingleton metadata = (LayeredReflectionMetadataSingleton) singleton;
                    List<Integer> hubs = new ArrayList<>();
                    List<Integer> classFlagsList = new ArrayList<>();

                    var cursor = metadata.reflectionMetadataMap.getEntries();
                    while (cursor.advance()) {
                        int hub = cursor.getKey();
                        hubs.add(hub);
                        classFlagsList.add(getCombinedClassFlags(cursor.getValue(), metadata.previousLayerClassFlags.getOrDefault(hub, 0)));
                    }

                    for (var entry : metadata.previousLayerClassFlags.entrySet()) {
                        if (!hubs.contains(entry.getKey())) {
                            /*
                             * If new class flags were written in this layer, the class flags from
                             * previous layers need to be skipped.
                             */
                            hubs.add(entry.getKey());
                            classFlagsList.add(entry.getValue());
                        }
                    }

                    writer.writeIntList(LAYERED_REFLECTION_METADATA_HUBS, hubs);
                    writer.writeIntList(LAYERED_REFLECTION_METADATA_CLASS_FLAGS, classFlagsList);

                    return LayeredImageSingleton.PersistFlags.CREATE;
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator {
        @Override
        public Object createFromLoader(ImageSingletonLoader loader) {
            List<Integer> hubs = loader.readIntList(LAYERED_REFLECTION_METADATA_HUBS);
            List<Integer> previousLayerClassFlags = loader.readIntList(LAYERED_REFLECTION_METADATA_CLASS_FLAGS);

            Map<Integer, Integer> classDatas = new HashMap<>();
            for (int i = 0; i < hubs.size(); ++i) {
                classDatas.put(hubs.get(i), previousLayerClassFlags.get(i));
            }

            return new LayeredReflectionMetadataSingleton(Collections.unmodifiableMap(classDatas));
        }
    }
}
