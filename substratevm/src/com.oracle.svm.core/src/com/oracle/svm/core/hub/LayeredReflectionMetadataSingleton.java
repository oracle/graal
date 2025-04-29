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

import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.DynamicHub.ReflectionMetadata;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;

/**
 * This singleton stores the {@link ReflectionMetadata} of each {@link DynamicHub} across layers to
 * allow registering elements for reflection in extension layers too.
 */
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
public class LayeredReflectionMetadataSingleton implements MultiLayeredImageSingleton, UnsavedSingleton {
    private final EconomicMap<Integer, ReflectionMetadata> reflectionMetadataMap = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static LayeredReflectionMetadataSingleton currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(LayeredReflectionMetadataSingleton.class, false, true);
    }

    public static LayeredReflectionMetadataSingleton[] singletons() {
        return MultiLayeredImageSingleton.getAllLayers(LayeredReflectionMetadataSingleton.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReflectionMetadata(DynamicHub hub, ReflectionMetadata reflectionMetadata) {
        /* GR-63472: Two different classes could have the same name in different class loaders */
        assert !reflectionMetadataMap.containsKey(hub.getTypeID()) : "The hub %s was added twice in the same layered reflection metadata".formatted(hub);
        reflectionMetadataMap.put(hub.getTypeID(), reflectionMetadata);
    }

    public ReflectionMetadata getReflectionMetadata(DynamicHub hub) {
        return reflectionMetadataMap.get(hub.getTypeID());
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
