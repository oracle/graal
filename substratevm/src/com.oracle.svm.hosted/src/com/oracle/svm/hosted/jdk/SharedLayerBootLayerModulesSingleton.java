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
package com.oracle.svm.hosted.jdk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.layeredimage.BuildingInitialLayerPredicate;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueLoader;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueWriter;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredPersistFlags;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTrait;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraitKind;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraits;
import com.oracle.svm.hosted.layeredimage.CapnProtoAdapters;
import com.oracle.svm.hosted.layeredimage.SVMImageLayerSingletonLoader;
import com.oracle.svm.hosted.layeredimage.SVMImageLayerWriter;

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingInitialLayerPredicate.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SharedLayerBootLayerModulesSingleton.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class SharedLayerBootLayerModulesSingleton {
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

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<SharedLayerBootLayerModulesSingleton>() {

                @Override
                public LayeredPersistFlags doPersist(KeyValueWriter writer, SharedLayerBootLayerModulesSingleton singleton) {
                    SVMImageLayerWriter.KeyValueWriterImpl writerImpl = (SVMImageLayerWriter.KeyValueWriterImpl) writer;
                    Stream<String> moduleNames = singleton.bootLayer.modules().stream().map(Module::getName);

                    if (singleton.sharedBootLayerModules != null) {
                        moduleNames = Stream.concat(moduleNames, singleton.sharedBootLayerModules.stream());
                    }

                    SVMImageLayerWriter.initStringList(writerImpl.getSnapshotBuilder()::initSharedLayerBootLayerModules, moduleNames);

                    return LayeredPersistFlags.CREATE;
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<SharedLayerBootLayerModulesSingleton> {
        @Override
        public SharedLayerBootLayerModulesSingleton createFromLoader(KeyValueLoader loader) {
            SVMImageLayerSingletonLoader.KeyValueLoaderImpl loaderImpl = (SVMImageLayerSingletonLoader.KeyValueLoaderImpl) loader;
            List<String> moduleNames = CapnProtoAdapters.toCollection(loaderImpl.getSnapshotReader().getSharedLayerBootLayerModules(), ArrayList::new);
            return new SharedLayerBootLayerModulesSingleton(moduleNames);
        }
    }
}
