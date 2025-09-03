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
package com.oracle.svm.core.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.util.ImageHeapMap.HostedImageHeapMap;

/**
 * {@link HostedImageHeapMap} are stored in {@code ImageHeapCollectionFeature#allMaps} through an
 * object replacer, meaning that only maps reachable at run time are tracked and rescanned. In the
 * extension layers, some maps can be extended at build time, but not be reachable from run time
 * code of the current layer. So all the maps reachable in the base layer need to be tracked and
 * when the map is created in an extension layer, it needs to be manually added to
 * {@code ImageHeapCollectionFeature#allMaps} to ensure it is always rescanned and reachable.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class LayeredHostedImageHeapMapCollector implements LayeredImageSingleton {
    /**
     * Map keys of maps reachable in the current layer.
     */
    private final List<String> currentLayerReachableMapsKeys = new ArrayList<>();
    /**
     * Map keys of maps reachable in the previous layers.
     */
    private final List<String> previousLayerReachableMapKeys;
    /**
     * Maps reachable in the previous layers.
     */
    private final List<HostedImageHeapMap<?, ?>> previousLayerReachableMaps = ImageLayerBuildingSupport.buildingExtensionLayer() ? new ArrayList<>() : null;

    public LayeredHostedImageHeapMapCollector() {
        this(null);
    }

    private LayeredHostedImageHeapMapCollector(List<String> previousLayerReachableMapKeys) {
        this.previousLayerReachableMapKeys = previousLayerReachableMapKeys;
    }

    public static LayeredHostedImageHeapMapCollector singleton() {
        return ImageSingletons.lookup(LayeredHostedImageHeapMapCollector.class);
    }

    public void registerReachableHostedImageHeapMap(LayeredImageHeapMap<Object, Object> layeredImageHeapMap) {
        currentLayerReachableMapsKeys.add(layeredImageHeapMap.getMapKey());
    }

    public boolean isMapKeyReachableInPreviousLayer(String mapKey) {
        return previousLayerReachableMapKeys.contains(mapKey);
    }

    public void registerPreviousLayerHostedImageHeapMap(HostedImageHeapMap<?, ?> hostedImageHeapMap) {
        previousLayerReachableMaps.add(hostedImageHeapMap);
    }

    public List<HostedImageHeapMap<?, ?>> getPreviousLayerReachableMaps() {
        return previousLayerReachableMaps;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        Set<String> reachableMapKeys = new HashSet<>(currentLayerReachableMapsKeys);
        if (previousLayerReachableMapKeys != null) {
            reachableMapKeys.addAll(previousLayerReachableMapKeys);
        }
        writer.writeStringList("reachableMapKeys", reachableMapKeys.stream().toList());
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        List<String> previousLayerReachableMapKeys = loader.readStringList("reachableMapKeys");
        return new LayeredHostedImageHeapMapCollector(previousLayerReachableMapKeys);
    }
}
