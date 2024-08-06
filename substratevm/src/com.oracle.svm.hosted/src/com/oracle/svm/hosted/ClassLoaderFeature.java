/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;
import com.oracle.svm.hosted.jdk.HostedClassLoaderPackageManagement;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.loader.ClassLoaders;

@AutomaticallyRegisteredFeature
public class ClassLoaderFeature implements InternalFeature {

    private static final String APP_KEY_NAME = "ClassLoader#App";
    private static final String PLATFORM_KEY_NAME = "ClassLoader#Platform";
    private static final String BOOT_KEY_NAME = "ClassLoader#Boot";

    private static final NativeImageSystemClassLoader nativeImageSystemClassLoader = NativeImageSystemClassLoader.singleton();

    private static final ClassLoader bootClassLoader;
    private static final ClassLoader platformClassLoader;

    static {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            platformClassLoader = ClassLoaders.platformClassLoader();
            bootClassLoader = BootLoaderSupport.getBootLoader();
        } else {
            platformClassLoader = null;
            bootClassLoader = null;
        }
    }

    public static ClassLoader getRuntimeClassLoader(ClassLoader original) {
        if (replaceWithAppClassLoader(original)) {
            return nativeImageSystemClassLoader.defaultSystemClassLoader;
        }

        return original;
    }

    private static boolean replaceWithAppClassLoader(ClassLoader loader) {
        if (loader == nativeImageSystemClassLoader) {
            return true;
        }
        if (nativeImageSystemClassLoader.isNativeImageClassLoader(loader)) {
            return true;
        }
        return false;
    }

    private Object runtimeClassLoaderObjectReplacer(Object replaceCandidate) {
        if (replaceCandidate instanceof ClassLoader loader) {
            return getRuntimeClassLoader(loader);
        }
        return replaceCandidate;
    }

    ImageHeapConstant replaceClassLoadersWithLayerConstant(CrossLayerConstantRegistry registry, Object object) {
        if (object instanceof ClassLoader loader) {
            if (replaceWithAppClassLoader(loader) || loader == nativeImageSystemClassLoader.defaultSystemClassLoader) {
                return registry.getConstant(APP_KEY_NAME);
            } else if (loader == platformClassLoader) {
                return registry.getConstant(PLATFORM_KEY_NAME);
            } else if (loader == bootClassLoader) {
                return registry.getConstant(BOOT_KEY_NAME);
            } else {
                throw VMError.shouldNotReachHere("Currently unhandled class loader seen in extension layer: %s", loader);
            }
        }

        return null;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        var packageManager = HostedClassLoaderPackageManagement.singleton();
        var registry = CrossLayerConstantRegistry.singletonOrNull();
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            packageManager.initialize(nativeImageSystemClassLoader.defaultSystemClassLoader, registry);
        }

        if (ImageLayerBuildingSupport.firstImageBuild()) {
            access.registerObjectReplacer(this::runtimeClassLoaderObjectReplacer);
        } else {
            var config = (FeatureImpl.DuringSetupAccessImpl) access;
            config.registerObjectToConstantReplacer(obj -> replaceClassLoadersWithLayerConstant(registry, obj));
            // relink packages defined in the prior layers
            config.registerObjectToConstantReplacer(packageManager::replaceWithPriorLayerPackage);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var packagesField = ReflectionUtil.lookupField(ClassLoader.class, "packages");
        access.registerFieldValueTransformer(packagesField, new FieldValueTransformerWithAvailability() {

            @Override
            public ValueAvailability valueAvailability() {
                return ValueAvailability.AfterAnalysis;
            }

            @Override
            public Object transform(Object receiver, Object originalValue) {
                assert receiver instanceof ClassLoader : receiver;
                assert originalValue instanceof ConcurrentHashMap : "Underlying representation has changed: " + originalValue;

                /* Retrieving initial package state for this class loader. */
                ConcurrentHashMap<String, Package> packages = HostedClassLoaderPackageManagement.singleton().getRegisteredPackages((ClassLoader) receiver);
                /* If no package state is available then we must create a clean state. */
                return packages == null ? new ConcurrentHashMap<>() : packages;
            }
        });

        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            /*
             * Note we cannot register these heap constants until the field value transformer has
             * been registered. Otherwise there is a race between this feature and
             * ObservableImageHeapMapProviderImpl#beforeAnalysis, as during heap scanning all
             * fieldValueInterceptors will be computed for the scanned objects.
             */
            var registry = CrossLayerConstantRegistry.singletonOrNull();
            registry.registerHeapConstant(APP_KEY_NAME, nativeImageSystemClassLoader.defaultSystemClassLoader);
            registry.registerHeapConstant(PLATFORM_KEY_NAME, platformClassLoader);
            registry.registerHeapConstant(BOOT_KEY_NAME, bootClassLoader);
        }
    }
}
