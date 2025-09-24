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
package com.oracle.svm.core;

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.code.CGlobalDataBasePointer;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;

/**
 * This singleton contains the {@link CGlobalDataBasePointer} of the current layer. In layered
 * images, there is one {@link com.oracle.svm.core.c.CGlobalData} memory space for each layer, so
 * when reading a {@link com.oracle.svm.core.c.CGlobalData}, the corresponding base needs to be
 * used.
 */
@AutomaticallyRegisteredImageSingleton
public class CGlobalDataPointerSingleton implements MultiLayeredImageSingleton {

    /**
     * Image heap object storing the base address of CGlobalData memory using a relocation. Before
     * the image heap is set up, CGlobalData must be accessed via relocations in the code instead.
     */
    private final BoxedRelocatedPointer cGlobalDataRuntimeBaseAddress = new BoxedRelocatedPointer(CGlobalDataBasePointer.INSTANCE);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static CGlobalDataPointerSingleton currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(CGlobalDataPointerSingleton.class, false, true);
    }

    public static CGlobalDataPointerSingleton[] allLayers() {
        return MultiLayeredImageSingleton.getAllLayers(CGlobalDataPointerSingleton.class);
    }

    public BoxedRelocatedPointer getRuntimeBaseAddress() {
        return cGlobalDataRuntimeBaseAddress;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
