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
package com.oracle.svm.core.layeredimagesingleton;

import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;

/**
 * In additional to the traditional singleton model, i.e. a key-value map whose lookups are constant
 * folded within generated code, we provide one additional option:
 *
 * <ul>
 * <li>{@link MultiLayeredImageSingleton}: {@link ImageSingletons#lookup} should no longer be used.
 * Instead, there is the method {@link MultiLayeredImageSingleton#getAllLayers} which returns an
 * array with the image singletons corresponding to this key for all layers. The length of this
 * array will always be the total number of layers. If a singleton corresponding to this key was not
 * installed in a given layer (and this is allowed), then the array will contain null for the given
 * index. See {@link MultiLayeredAllowNullEntries} for more details. Within the array, the
 * singletons will be arranged so that index [0] corresponds to the singleton originating from the
 * initial layer and index [length - 1] holds the singleton from the application layer. See
 * {@link ImageLayerBuildingSupport} for a description of the different layer names.</li>
 * </ul>
 *
 * Calling {@link MultiLayeredImageSingleton#getAllLayers} during a traditional build requires the
 * singleton to be installed in the build and will return an array of length 1 containing that
 * singleton.
 *
 * Currently, when using these special singleton types there are additional restrictions:
 *
 * <ol>
 * <li>The key class type must match the implementation type</li>
 * <li>The same object cannot be mapped into multiple keys, i.e. there is a one-to-one mapping
 * between Class<->singleton object</li>
 * <li>{@link ImageSingletons#add} must be called before the analysis phase (i.e. these image
 * singletons cannot be added at a later point)</li>
 * </ol>
 */
public interface LayeredImageSingleton {

    enum PersistFlags {
        /**
         * Indicates nothing should be persisted for this singleton. A different singleton can be
         * linked to this key in a subsequent image layer.
         */
        NOTHING,
        /**
         * Indicates nothing should be persisted for this singleton and that a singleton cannot be
         * linked to this key in a subsequent image layer.
         */
        FORBIDDEN,
        /**
         * Indicates in a subsequent image a new singleton should be created and linked via calling
         * {@code Object createFromLoader(ImageSingletonLoader)}.
         */
        CREATE
    }

    /*
     * Returns how this singleton should be handled for the current build. The returned value must
     * not change throughout execution (i.e., the returned results can be cached).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags();

    @Platforms(Platform.HOSTED_ONLY.class)
    PersistFlags preparePersist(ImageSingletonWriter writer);

}
