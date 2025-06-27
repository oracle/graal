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
package com.oracle.svm.hosted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.svm.hosted.imagelayer.CapnProtoAdapters;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.BuildingInitialLayerPredicate;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSingletonLoader;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerWriter;

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingInitialLayerPredicate.class)
public class SharedLayerBootLayerModulesSingleton implements LayeredImageSingleton {
    private final Collection<String> sharedBootLayerModules;
    private ModuleLayer bootLayer;

    public SharedLayerBootLayerModulesSingleton() {
        this.sharedBootLayerModules = null;
    }

    private SharedLayerBootLayerModulesSingleton(Collection<String> baseBootLayerModules) {
        this.sharedBootLayerModules = baseBootLayerModules;
    }

    public static SharedLayerBootLayerModulesSingleton singleton() {
        return ImageSingletons.lookup(SharedLayerBootLayerModulesSingleton.class);
    }

    public void setBootLayer(ModuleLayer bootLayer) {
        this.bootLayer = bootLayer;
    }

    public Collection<String> getSharedBootLayerModules() {
        return sharedBootLayerModules;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        SVMImageLayerWriter.ImageSingletonWriterImpl writerImpl = (SVMImageLayerWriter.ImageSingletonWriterImpl) writer;
        Stream<String> moduleNames = bootLayer.modules().stream().map(Module::getName);

        if (sharedBootLayerModules != null) {
            moduleNames = Stream.concat(moduleNames, sharedBootLayerModules.stream());
        }

        SVMImageLayerWriter.initStringList(writerImpl.getSnapshotBuilder()::initSharedLayerBootLayerModules, moduleNames);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl loaderImpl = (SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl) loader;
        List<String> moduleNames = CapnProtoAdapters.toCollection(loaderImpl.getSnapshotReader().getSharedLayerBootLayerModules(), ArrayList::new);
        return new SharedLayerBootLayerModulesSingleton(moduleNames);
    }
}
