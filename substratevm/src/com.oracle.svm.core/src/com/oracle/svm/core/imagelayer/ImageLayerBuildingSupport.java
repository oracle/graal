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
package com.oracle.svm.core.imagelayer;

import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Support for tracking the image layer stage of this native-image build. When image layers are
 * used, executing a native-image will require multiple object files produced by different native
 * image builds to be linked and loaded. The "application layer" will be the final layer and will
 * depend on one or more "shared layer"s. Given the layer layout below, we classify the layers as
 * follows:
 * <ul>
 * <li>ImageLayers: A,B,C,D</li>
 * <li>InitialLayer: A</li>
 * <li>ApplicationLayer: D</li>
 * <li>ExtensionLayers: B,C,D</li>
 * <li>SharedLayers: A,B,C</li>
 * </ul>
 *
 * <pre>
 *     |------------------------|
 *     | (A) Initial Layer      |
 *     |------------------------|
 *     | (B) Intermediate Layer |
 *     |------------------------|
 *     | (C) Intermediate Layer |
 *     |------------------------|
 *     | (D) Application Layer  |
 *     |------------------------|
 * </pre>
 */
public abstract class ImageLayerBuildingSupport implements UnsavedSingleton {
    protected final boolean buildingImageLayer;
    private final boolean buildingInitialLayer;
    private final boolean buildingApplicationLayer;

    protected ImageLayerBuildingSupport(boolean buildingImageLayer, boolean buildingInitialLayer, boolean buildingApplicationLayer) {
        this.buildingImageLayer = buildingImageLayer;
        this.buildingInitialLayer = buildingInitialLayer;
        this.buildingApplicationLayer = buildingApplicationLayer;
    }

    private static ImageLayerBuildingSupport singleton() {
        return ImageSingletons.lookup(ImageLayerBuildingSupport.class);
    }

    @Fold
    public static boolean buildingImageLayer() {
        return singleton().buildingImageLayer;
    }

    @Fold
    public static boolean buildingInitialLayer() {
        return singleton().buildingInitialLayer;
    }

    @Fold
    public static boolean buildingApplicationLayer() {
        return singleton().buildingApplicationLayer;
    }

    @Fold
    public static boolean buildingExtensionLayer() {
        return singleton().buildingImageLayer && !singleton().buildingInitialLayer;
    }

    @Fold
    public static boolean buildingSharedLayer() {
        return singleton().buildingImageLayer && !singleton().buildingApplicationLayer;
    }

    @Override
    public final EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS_ALLOW_FOLDING;
    }
}
