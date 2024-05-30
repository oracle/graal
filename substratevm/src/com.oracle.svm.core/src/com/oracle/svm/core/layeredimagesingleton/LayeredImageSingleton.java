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

/**
 * In additional to the traditional singleton model, i.e. a key-value map whose lookups are constant
 * folded within generated code, we provide two additional options:
 *
 * <ul>
 * <li>{@link ApplicationLayerOnlyImageSingleton}: Instead of having a per-layer singleton, all
 * {@link ImageSingletons#lookup} calls refer to a single singleton which will be created in the
 * application layer.</li>
 *
 * <li>{@link MultiLayeredImageSingleton}: {@link ImageSingletons#lookup} calls continue to refer to
 * the appropriate per layer image singleton, but there is also an additional method
 * {@link MultiLayeredImageSingleton#getAllLayers} which returns an array with the image singletons
 * corresponding to this key in all layers they were created.</li>
 * </ul>
 *
 * Note the unique behavior of {@link ApplicationLayerOnlyImageSingleton} and
 * {@link MultiLayeredImageSingleton} apply only when building a layered image. During a traditional
 * build these flags do not have an impact.
 *
 * Currently, when using these special singleton types there are additional restrictions:
 *
 * <ol>
 * <li>The key class type must match the implementation type</li>
 * <li>The same object cannot be mapped into multiple keys, i.e. there is a one-to-one mapping
 * between Class<->singleton object</li>
 * <li>{@link ImageSingletons#add} must be called before the analysis phase (i.e. these image
 * singletons cannot be added at a later point)</li>
 * <li>{@link ApplicationLayerOnlyImageSingleton}s can only be installed in the application
 * layer</li>
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
