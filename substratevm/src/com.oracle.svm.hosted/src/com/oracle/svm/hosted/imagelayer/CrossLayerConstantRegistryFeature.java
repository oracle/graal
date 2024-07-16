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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;

@AutomaticallyRegisteredFeature
public class CrossLayerConstantRegistryFeature implements InternalFeature, FeatureSingleton, CrossLayerConstantRegistry {

    ImageLayerIdTrackingSingleton tracker;
    boolean sealed = false;
    ImageLayerLoader loader;

    Map<String, Object> constantCandidates;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            tracker = new ImageLayerIdTrackingSingleton();
            ImageSingletons.add(ImageLayerIdTrackingSingleton.class, tracker);
        } else {
            tracker = ImageSingletons.lookup(ImageLayerIdTrackingSingleton.class);
        }

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            constantCandidates = new ConcurrentHashMap<>();
        }
        ImageSingletons.add(CrossLayerConstantRegistry.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        loader = ((FeatureImpl.DuringSetupAccessImpl) access).getUniverse().getImageLayerLoader();
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        sealed = true;
        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            /*
             * Register constant candidates installed in this layer.
             */

            var config = (FeatureImpl.BeforeImageWriteAccessImpl) access;
            var snippetReflection = config.getHostedUniverse().getSnippetReflection();
            var heap = config.getImage().getHeap();

            for (var entry : constantCandidates.entrySet()) {

                var optional = config.getHostedMetaAccess().optionalLookupJavaType(entry.getValue().getClass());
                if (optional.isPresent()) {
                    var constant = (ImageHeapConstant) snippetReflection.forObject(entry.getValue());
                    var objectInfo = heap.getConstantInfo(constant);
                    if (objectInfo != null && objectInfo.getOffset() >= 0) {
                        int id = ImageHeapConstant.getConstantID(constant);
                        tracker.registerKey(entry.getKey(), id);
                    }
                }
            }
        }
    }

    private void checkSealed() {
        VMError.guarantee(!sealed, "Id tracking is sealed");
    }

    @Override
    public void registerConstantCandidate(String keyName, Object obj) {
        checkSealed();
        VMError.guarantee(ImageLayerBuildingSupport.buildingSharedLayer(), "This only applies to shared layers");
        var previous = constantCandidates.putIfAbsent(keyName, obj);
        VMError.guarantee(previous == null && !constantExists(keyName), "This key has been registered before: %s", keyName);
    }

    @Override
    public ImageHeapConstant getConstant(String keyName) {
        checkSealed();
        Integer id = tracker.getId(keyName);
        VMError.guarantee(id != null, "Missing key: %s", keyName);
        return loader.getOrCreateConstant(id);
    }

    @Override
    public boolean constantExists(String keyName) {
        checkSealed();
        return tracker.getId(keyName) != null;
    }
}

class ImageLayerIdTrackingSingleton implements LayeredImageSingleton {
    private final Map<String, Integer> keyToIdMap = new ConcurrentHashMap<>();

    Integer getId(String key) {
        return keyToIdMap.get(key);
    }

    void registerKey(String key, int id) {
        var previous = keyToIdMap.putIfAbsent(key, id);
        VMError.guarantee(previous == null, "Two values are registered for this key %s", key);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        for (var entry : keyToIdMap.entrySet()) {
            keys.add(entry.getKey());
            ids.add(entry.getValue());
        }

        writer.writeStringList("keys", keys);
        writer.writeIntList("ids", ids);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        var tracker = new ImageLayerIdTrackingSingleton();

        Iterator<String> keys = loader.readStringList("keys").iterator();
        Iterator<Integer> ids = loader.readIntList("ids").iterator();

        while (keys.hasNext()) {
            tracker.registerKey(keys.next(), ids.next());
        }
        return tracker;
    }
}
