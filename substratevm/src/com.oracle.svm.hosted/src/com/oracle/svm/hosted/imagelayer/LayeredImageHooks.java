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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.meta.MethodRef;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Class containing hooks which can only be registered and executed during layered image builds.
 */
@AutomaticallyRegisteredFeature
public class LayeredImageHooks implements InternalFeature, FeatureSingleton {
    private final Set<DynamicHubWrittenCallback> hubWrittenCallbacks = ConcurrentHashMap.newKeySet();
    private final Set<PatchedWordWrittenCallback> patchedWordWrittenCallbacks = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Fold
    public static LayeredImageHooks singleton() {
        return ImageSingletons.lookup(LayeredImageHooks.class);
    }

    @FunctionalInterface
    public interface DynamicHubWrittenCallback {
        void afterDynamicHubWritten(DynamicHub hub, MethodRef[] vtable);
    }

    public void registerDynamicHubWrittenCallback(DynamicHubWrittenCallback callback) {
        hubWrittenCallbacks.add(Objects.requireNonNull(callback));
    }

    public void processDynamicHubWritten(DynamicHub object, MethodRef[] vTable) {
        for (var callback : hubWrittenCallbacks) {
            callback.afterDynamicHubWritten(object, vTable);
        }
    }

    @FunctionalInterface
    public interface PatchedWordWrittenCallback {
        void afterPatchedWordWritten(WordBase word, int offsetInHeap, ImageHeapLayoutInfo heapLayout);
    }

    public void registerPatchedWordWrittenCallback(PatchedWordWrittenCallback callback) {
        patchedWordWrittenCallbacks.add(Objects.requireNonNull(callback));
    }

    public void processPatchedWordWritten(WordBase word, int offsetInHeap, ImageHeapLayoutInfo heapLayout) {
        for (var callback : patchedWordWrittenCallbacks) {
            callback.afterPatchedWordWritten(word, offsetInHeap, heapLayout);
        }
    }
}
