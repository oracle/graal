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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool;

/**
 * This singleton provides the {@link Target_jdk_internal_reflect_ConstantPool} for the requested
 * layer. Native Image normally does not use the constant pool, but in the context of Layered Image,
 * the constant pool needs to be associated with a layer number. More information can be found in
 * {@link Target_jdk_internal_reflect_ConstantPool}.
 */
public class ConstantPoolProvider implements MultiLayeredImageSingleton, UnsavedSingleton {
    private final Target_jdk_internal_reflect_ConstantPool constantPool = new Target_jdk_internal_reflect_ConstantPool(DynamicImageLayerInfo.getCurrentLayerNumber());

    public static ConstantPoolProvider[] singletons() {
        return MultiLayeredImageSingleton.getAllLayers(ConstantPoolProvider.class);
    }

    public Target_jdk_internal_reflect_ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.RUNTIME_ACCESS_ONLY;
    }
}

@AutomaticallyRegisteredFeature
class ConstantPoolProviderFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * The ConstantPoolProvider needs to be created after the afterRegistration hook as it
         * depends on the DynamicImageLayerInfo.
         */
        ImageSingletons.add(ConstantPoolProvider.class, new ConstantPoolProvider());
    }
}
