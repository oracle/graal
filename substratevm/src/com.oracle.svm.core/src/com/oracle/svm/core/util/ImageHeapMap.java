/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapWrap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;

/**
 * A thread-safe map implementation that optimizes its run time representation for space efficiency.
 * 
 * At image build time the map is implemented as a {@link ConcurrentHashMap} wrapped into an
 * {@link EconomicMap}. The ImageHeapMapFeature intercepts each build time map and transforms it
 * into an actual memory-efficient {@link EconomicMap} backed by a flat object array during
 * analysis. The later image building stages see only the {@link EconomicMap}.
 * 
 * This map implementation allows thread-safe collection of data at image build time and storing it
 * into a space efficient data structure at run time.
 */
@Platforms(Platform.HOSTED_ONLY.class) //
public final class ImageHeapMap {

    private ImageHeapMap() {
    }

    /**
     * Create a hosted representation of an {@link ImageHeapMap}: a {@link ConcurrentHashMap}
     * wrapped into an {@link EconomicMap}. At run time this will be an actual memory-efficient
     * {@link EconomicMap} backed by a flat object array.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <K, V> EconomicMap<K, V> create(String key) {
        return create(Equivalence.DEFAULT, key);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <K, V> EconomicMap<K, V> createNonLayeredMap() {
        return createNonLayeredMap(Equivalence.DEFAULT);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <K, V> EconomicMap<K, V> create(Equivalence strategy, String key) {
        assert key != null : "The key should not be null if the map needs to be automatically layered";
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Trying to create an ImageHeapMap after analysis.");
        return HostedImageHeapMap.create(strategy, key, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <K, V> EconomicMap<K, V> createNonLayeredMap(Equivalence strategy) {
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Trying to create an ImageHeapMap after analysis.");
        return HostedImageHeapMap.create(strategy, null, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final class HostedImageHeapMap<K, V> extends EconomicMapWrap<K, V> {
        private final EconomicMap<Object, Object> currentLayerMap;
        private final EconomicMap<Object, Object> runtimeMap;

        /**
         * The {code key} is only used in the Layered Image context, to link the maps across each
         * layer. If an {@link ImageHeapMap} is in a singleton that is already layer aware, there is
         * no need to use a {@link LayeredImageHeapMap}, as the singleton should already handle the
         * link across layers. In this case, {@code isAlreadyLayered} should be true and the
         * {@code key} can be {@code null}.
         */
        public HostedImageHeapMap(Map<K, V> hostedMap, EconomicMap<Object, Object> currentLayerMap, EconomicMap<Object, Object> runtimeMap) {
            super(hostedMap);
            this.currentLayerMap = currentLayerMap;
            this.runtimeMap = runtimeMap;
        }

        @Platforms(Platform.HOSTED_ONLY.class) //
        public EconomicMap<Object, Object> getCurrentLayerMap() {
            return currentLayerMap;
        }

        /**
         * Returns the run time representation of this map. In a non-layered build this is the same
         * as the {@link #currentLayerMap} object returned by {@link #getCurrentLayerMap()}, i.e.,
         * an {@link EconomicMap}. In a layered build this is a special {@link LayeredImageHeapMap}
         * object that retrieves the layered key-value pairs by accessing the
         * {@link #currentLayerMap} installed in every layer.
         */
        public EconomicMap<Object, Object> getRuntimeMap() {
            return runtimeMap;
        }

        public static <K, V> HostedImageHeapMap<K, V> create(Equivalence strategy, String key, boolean needLayeredMap) {
            Map<K, V> hostedMap = (strategy == Equivalence.IDENTITY) ? new ConcurrentIdentityHashMap<>() : new ConcurrentHashMap<>();
            EconomicMap<Object, Object> currentLayerMap = EconomicMap.create(strategy);
            if (!needLayeredMap || !ImageLayerBuildingSupport.buildingImageLayer()) {
                return new HostedImageHeapMap<>(hostedMap, currentLayerMap, currentLayerMap);
            } else {
                LayeredImageHeapMap<Object, Object> runtimeMap = new LayeredImageHeapMap<>(strategy, key);
                var previousMap = LayeredImageHeapMapStore.currentLayer().getImageHeapMapStore().put(key, currentLayerMap);
                if (previousMap != null) {
                    throw VMError.shouldNotReachHere("The LayeredImageHeapMap with key %s was added twice", key);
                }
                HostedImageHeapMap<K, V> hostedImageHeapMap = new HostedImageHeapMap<>(hostedMap, currentLayerMap, runtimeMap);
                LayeredHostedImageHeapMapCollector singleton = LayeredHostedImageHeapMapCollector.singleton();
                if (ImageLayerBuildingSupport.buildingExtensionLayer() && singleton.isMapKeyReachableInPreviousLayer(key)) {
                    singleton.registerPreviousLayerHostedImageHeapMap(hostedImageHeapMap);
                }
                return hostedImageHeapMap;
            }
        }
    }
}
