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

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.BuildingInitialLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.debug.Assertions;

/**
 * Tracks information about {@link DynamicHub} which should be consistent across builds.
 */
@AutomaticallyRegisteredFeature
public class LayeredDynamicHubFeature implements InternalFeature, FeatureSingleton {

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            LayeredImageHooks.singleton().registerDynamicHubWrittenCallback(this::onDynamicHubWritten);
        }
    }

    private void onDynamicHubWritten(DynamicHub hub, @SuppressWarnings("unused") MethodRef[] vTable) {
        if (hub.getArrayHub() == null) {
            DynamicHubMetadataTracking.singleton().recordMissingArrayHub(hub);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * Scan all DynamicHubs to see if new missing array hubs have been installed. Currently
             * we must wait to do this until after typeIDs have been assigned.
             */
            DynamicHubMetadataTracking tracking = DynamicHubMetadataTracking.singleton();
            FeatureImpl.BeforeCompilationAccessImpl config = (FeatureImpl.BeforeCompilationAccessImpl) access;
            config.getUniverse().getTypes().stream().filter(tracking::missingArrayHub).forEach(hType -> {
                if (hType.getHub().getArrayHub() != null) {
                    var missingArrayName = hType.getHub().getArrayHub().getName();
                    String message = String.format("New array type seen in application layer which was not installed within the dynamic hub.%nHub: %s%nArrayType: %s", hType.getHub().getName(),
                                    missingArrayName);
                    LogUtils.warning(message);
                }
            });
        }
    }
}

@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingInitialLayerPredicate.class)
class DynamicHubMetadataTracking implements LayeredImageSingleton {
    private final Set<Integer> typeIDsWithMissingHubs;

    DynamicHubMetadataTracking() {
        this.typeIDsWithMissingHubs = ConcurrentHashMap.newKeySet();
    }

    DynamicHubMetadataTracking(Set<Integer> missingHubSet) {
        this.typeIDsWithMissingHubs = missingHubSet;
    }

    static DynamicHubMetadataTracking singleton() {
        return ImageSingletons.lookup(DynamicHubMetadataTracking.class);
    }

    boolean missingArrayHub(HostedType hType) {
        return typeIDsWithMissingHubs.contains(hType.getTypeID());
    }

    void recordMissingArrayHub(DynamicHub hub) {
        var added = typeIDsWithMissingHubs.add(hub.getTypeID());
        assert added : Assertions.errorMessage("type recorded twice:", hub);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        writer.writeIntList("typeIDsWithMissingArrayHubs", typeIDsWithMissingHubs.stream().toList());
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        Set<Integer> missingHubs = Set.copyOf(loader.readIntList("typeIDsWithMissingArrayHubs"));

        return new DynamicHubMetadataTracking(missingHubs);
    }
}
