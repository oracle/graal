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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.layeredimage.BuildingInitialLayerPredicate;
import com.oracle.svm.core.jdk.LayeredModuleSingleton;
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
import com.oracle.svm.hosted.layeredimage.SharedLayerSnapshotCapnProtoSchemaHolder.ModulePackages;
import com.oracle.svm.shaded.org.capnproto.StructList;

@AutomaticallyRegisteredImageSingleton(value = LayeredModuleSingleton.class, onlyWith = BuildingInitialLayerPredicate.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = HostedLayeredModuleSingleton.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class HostedLayeredModuleSingleton extends LayeredModuleSingleton {
    public HostedLayeredModuleSingleton() {
        super();
    }

    public HostedLayeredModuleSingleton(Map<String, Map<String, Set<String>>> moduleOpenPackages, Map<String, Map<String, Set<String>>> moduleExportedPackages) {
        super(moduleOpenPackages, moduleExportedPackages);
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static void persistModulePackages(StructList.Builder<ModulePackages.Builder> modulePackagesBuilder, Map<String, Map<String, Set<String>>> modulePackages) {
            int i = 0;
            for (var entry : modulePackages.entrySet()) {
                var entryBuilder = modulePackagesBuilder.get(i);
                entryBuilder.setModuleKey(entry.getKey());
                Map<String, Set<String>> value = entry.getValue();
                var packagesBuilder = entryBuilder.initPackages(value.size());
                int j = 0;
                for (var packageEntry : value.entrySet()) {
                    var packageEntryBuilder = packagesBuilder.get(j);
                    packageEntryBuilder.setPackageKey(packageEntry.getKey());
                    SVMImageLayerWriter.initStringList(packageEntryBuilder::initModules, packageEntry.getValue().stream());
                    j++;
                }
                i++;
            }
        }

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<HostedLayeredModuleSingleton>() {

                @Override
                public LayeredPersistFlags doPersist(KeyValueWriter writer, HostedLayeredModuleSingleton singleton) {
                    SVMImageLayerWriter.KeyValueWriterImpl writerImpl = (SVMImageLayerWriter.KeyValueWriterImpl) writer;
                    var builder = writerImpl.getSnapshotBuilder().initLayeredModule();
                    persistModulePackages(builder.initOpenModulePackages(singleton.moduleOpenPackages.size()), singleton.moduleOpenPackages);
                    persistModulePackages(builder.initExportedModulePackages(singleton.moduleExportedPackages.size()), singleton.moduleExportedPackages);
                    return LayeredPersistFlags.CREATE;
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<HostedLayeredModuleSingleton> {
        private static Map<String, Map<String, Set<String>>> getModulePackages(StructList.Reader<ModulePackages.Reader> modulePackagesReader) {
            Map<String, Map<String, Set<String>>> modulePackages = new HashMap<>();
            for (int i = 0; i < modulePackagesReader.size(); ++i) {
                var entryReader = modulePackagesReader.get(i);
                var packagesReader = entryReader.getPackages();
                Map<String, Set<String>> packages = new HashMap<>();
                for (int j = 0; j < packagesReader.size(); ++j) {
                    var packageEntryReader = packagesReader.get(j);
                    Set<String> modules = CapnProtoAdapters.toCollection(packageEntryReader.getModules(), HashSet::new);
                    packages.put(packageEntryReader.getPackageKey().toString(), modules);
                }
                modulePackages.put(entryReader.getModuleKey().toString(), packages);
            }
            return modulePackages;
        }

        @Override
        public HostedLayeredModuleSingleton createFromLoader(KeyValueLoader loader) {
            SVMImageLayerSingletonLoader.KeyValueLoaderImpl loaderImpl = (SVMImageLayerSingletonLoader.KeyValueLoaderImpl) loader;
            var reader = loaderImpl.getSnapshotReader().getLayeredModule();

            Map<String, Map<String, Set<String>>> moduleOpenPackages = getModulePackages(reader.getOpenModulePackages());
            Map<String, Map<String, Set<String>>> moduleExportedPackages = getModulePackages(reader.getExportedModulePackages());

            return new HostedLayeredModuleSingleton(moduleOpenPackages, moduleExportedPackages);
        }
    }
}
