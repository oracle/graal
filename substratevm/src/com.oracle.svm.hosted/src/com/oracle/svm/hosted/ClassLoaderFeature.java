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
import java.util.function.Function;

import org.graalvm.nativeimage.libgraal.hosted.LibGraalLoader;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.fieldvaluetransformer.ObjectToConstantFieldValueTransformer;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;
import com.oracle.svm.hosted.jdk.HostedClassLoaderPackageManagement;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.loader.ClassLoaders;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

@AutomaticallyRegisteredFeature
public class ClassLoaderFeature implements InternalFeature {

    private static final String APP_KEY_NAME = "ClassLoader#App";
    private static final String APP_PACKAGE_KEY_NAME = "ClassLoader.Packages#App";
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

    JavaConstant replaceClassLoadersWithLayerConstant(CrossLayerConstantRegistry registry, Object object) {
        if (object instanceof ClassLoader loader) {
            if (replaceWithAppClassLoader(loader) || loader == nativeImageSystemClassLoader.defaultSystemClassLoader) {
                return registry.getConstant(APP_KEY_NAME);
            } else if (loader == platformClassLoader) {
                return registry.getConstant(PLATFORM_KEY_NAME);
            } else if (loader == bootClassLoader) {
                return registry.getConstant(BOOT_KEY_NAME);
            } else if (HostedClassLoaderPackageManagement.isGeneratedSerializationClassLoader(loader)) {
                return registry.getConstant(HostedClassLoaderPackageManagement.getClassLoaderSerializationLookupKey(loader));
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

        var config = (FeatureImpl.DuringSetupAccessImpl) access;
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            LibGraalLoader libGraalLoader = ((DuringSetupAccessImpl) access).imageClassLoader.classLoaderSupport.getLibGraalLoader();
            if (libGraalLoader != null) {
                ClassLoader libGraalClassLoader = (ClassLoader) libGraalLoader;
                ClassForNameSupport.currentLayer().setLibGraalLoader(libGraalClassLoader);
            }
            access.registerObjectReplacer(this::runtimeClassLoaderObjectReplacer);
            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                config.registerObjectReachableCallback(ClassLoader.class, (a1, classLoader, reason) -> {
                    if (HostedClassLoaderPackageManagement.isGeneratedSerializationClassLoader(classLoader)) {
                        registry.registerHeapConstant(HostedClassLoaderPackageManagement.getClassLoaderSerializationLookupKey(classLoader), classLoader);
                    }
                });
            }
        } else {
            config.registerObjectToConstantReplacer(obj -> (ImageHeapConstant) replaceClassLoadersWithLayerConstant(registry, obj));
            // relink packages defined in the prior layers
            config.registerObjectToConstantReplacer(packageManager::replaceWithPriorLayerPackage);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var packagesField = ReflectionUtil.lookupField(ClassLoader.class, "packages");
        var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            access.registerFieldValueTransformer(packagesField, new TraditionalPackageMapTransformer());
        } else {
            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                config.registerFieldValueTransformer(packagesField, new InitialLayerPackageMapTransformer());
            } else {
                access.registerFieldValueTransformer(packagesField, new ExtensionLayerPackageMapTransformer());
            }
        }

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
            registry.registerFutureHeapConstant(APP_PACKAGE_KEY_NAME, config.getMetaAccess().lookupJavaType(ConcurrentHashMap.class));
        }

        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * We need to scan this because the final package info cannot be installed until after
             * analysis has completed.
             */
            config.rescanObject(HostedClassLoaderPackageManagement.singleton().getPriorAppClassLoaderPackages());
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        // Register the future constant
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            var registry = CrossLayerConstantRegistry.singletonOrNull();
            registry.finalizeFutureHeapConstant(APP_PACKAGE_KEY_NAME, HostedClassLoaderPackageManagement.singleton().getAppClassLoaderPackages());
        }
    }

    abstract static class PackageMapTransformer implements FieldValueTransformerWithAvailability {

        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        Object doTransform(Object receiver, Object originalValue) {
            assert receiver instanceof ClassLoader : receiver;
            assert originalValue instanceof ConcurrentHashMap : "Underlying representation has changed: " + originalValue;

            /* Retrieving initial package state for this class loader. */
            ConcurrentHashMap<String, Package> packages = HostedClassLoaderPackageManagement.singleton().getRegisteredPackages((ClassLoader) receiver);
            /* If no package state is available then we must create a clean state. */
            return packages == null ? new ConcurrentHashMap<>() : packages;
        }
    }

    static class TraditionalPackageMapTransformer extends PackageMapTransformer {

        @Override
        public Object transform(Object receiver, Object originalValue) {
            return doTransform(receiver, originalValue);
        }
    }

    static class InitialLayerPackageMapTransformer extends PackageMapTransformer implements ObjectToConstantFieldValueTransformer {
        final CrossLayerConstantRegistry registry = CrossLayerConstantRegistry.singletonOrNull();

        @Override
        public JavaConstant transformToConstant(ResolvedJavaField field, Object receiver, Object originalValue, Function<Object, JavaConstant> toConstant) {
            if (receiver == nativeImageSystemClassLoader.defaultSystemClassLoader) {
                /*
                 * This map will be assigned within the application layer. Within this layer we
                 * register a relocatable constant.
                 */
                return registry.getConstant(APP_PACKAGE_KEY_NAME);

            }

            return toConstant.apply(doTransform(receiver, originalValue));
        }
    }

    static class ExtensionLayerPackageMapTransformer implements FieldValueTransformerWithAvailability {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            throw VMError.shouldNotReachHere("No classloaders should be installed in extension layers: %s", receiver);
        }
    }
}
