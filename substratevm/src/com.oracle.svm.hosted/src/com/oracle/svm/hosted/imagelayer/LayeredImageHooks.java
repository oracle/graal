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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.hosted.meta.HostedUniverse;

/**
 * Class containing hooks which can only be registered and executed during layered image builds.
 */
@AutomaticallyRegisteredFeature
public class LayeredImageHooks implements InternalFeature, FeatureSingleton {
    private final Set<Consumer<WrittenDynamicHubInfo>> hubWrittenCallbacks = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    private static LayeredImageHooks singleton() {
        return ImageSingletons.lookup(LayeredImageHooks.class);
    }

    public record WrittenDynamicHubInfo(DynamicHub hub, AnalysisUniverse aUniverse, HostedUniverse hUniverse, Object vTable) {

    }

    /**
     * Register a callback which will execute each time a new {@link DynamicHub} is installed in the
     * image heap.
     */
    public static void registerDynamicHubWrittenCallback(Consumer<WrittenDynamicHubInfo> consumer) {
        singleton().hubWrittenCallbacks.add(consumer);
    }

    public static void processWrittenDynamicHub(WrittenDynamicHubInfo info) {
        singleton().hubWrittenCallbacks.forEach(callback -> callback.accept(info));
    }
}
