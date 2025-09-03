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
package com.oracle.svm.hosted.jdk;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.PriorLayerMarker;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.BootLoaderSupport;
import com.oracle.svm.hosted.ClassLoaderFeature;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.internal.loader.ClassLoaders;

/**
 * This class stores information about which packages need to be stored within each ClassLoader.
 * This information is used by {@link Target_java_lang_ClassLoader} to reset the package
 * information. The package information must be reset because:
 * <ol>
 * <li>Many more classes may be loaded during native-image generation time than will be reachable
 * during a normal execution.</li>
 * <li>Each ClassLoader should only initially store packages for classes which are initialized at
 * build-time. Classes (re)initialized during runtime should have their respective package linked
 * then.</li>
 * <li>During native-image generation time, a custom system class loader is used
 * (NativeImageSystemClassLoader) to load application classes. However, before heap creation,
 * classes which were loaded by NativeImageSystemClassLoader are updated to point to the default
 * system class loader via ClassLoaderFeature. Hence, the default system class loader must be
 * updated to contain references to all appropriate packages.</li>
 * </ol>
 *
 * For layered images, we record the packages stored in earlier layers so that references in
 * subsequent layers can be appropriately relinked. Currently in extension layers we expect only the
 * system class loader to have new packages registered.
 */
@AutomaticallyRegisteredImageSingleton
public class HostedClassLoaderPackageManagement implements LayeredImageSingleton {

    private static final String APP_KEY = "AppPackageNames";
    private static final String PLATFORM_KEY = "PlatformPackageNames";
    private static final String BOOT_KEY = "BootPackageNames";
    private static final String GENERATED_SERIALIZATION_KEY = "GeneratedSerializationNames";

    private static final String GENERATED_SERIALIZATION_PACKAGE = "jdk.internal.reflect";

    private static final Method packageGetPackageInfo = ReflectionUtil.lookupMethod(Package.class, "getPackageInfo");

    private static final ClassLoader platformClassLoader;
    private static final ClassLoader bootClassLoader;
    private static final Method moduleLookup;

    private final boolean inExtensionLayer;
    private final boolean inSharedLayer;

    /**
     * Contains package names seen in the prior layers.
     */
    private final Set<String> priorAppPackageNames;
    private final Set<String> priorPlatformPackageNames;
    private final Set<String> priorBootPackageNames;

    private final ConcurrentMap<ClassLoader, ConcurrentHashMap<String, Package>> registeredPackages = new ConcurrentHashMap<>();

    private ClassLoader appClassLoader;
    private CrossLayerConstantRegistry registry;

    static {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            platformClassLoader = ClassLoaders.platformClassLoader();
            bootClassLoader = BootLoaderSupport.getBootLoader();
            moduleLookup = ImageLayerBuildingSupport.buildingExtensionLayer() ? ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.NamedPackage"), "module") : null;
        } else {
            platformClassLoader = null;
            bootClassLoader = null;
            moduleLookup = null;
        }
    }

    public HostedClassLoaderPackageManagement() {
        inExtensionLayer = false;
        inSharedLayer = ImageLayerBuildingSupport.buildingSharedLayer();
        priorAppPackageNames = null;
        priorPlatformPackageNames = null;
        priorBootPackageNames = null;
    }

    private HostedClassLoaderPackageManagement(Set<String> appPackages, Set<String> platformPackages, Set<String> bootPackages) {
        VMError.guarantee(ImageLayerBuildingSupport.buildingExtensionLayer(), "Unexpected invocation");
        inExtensionLayer = true;
        inSharedLayer = ImageLayerBuildingSupport.buildingSharedLayer();
        priorAppPackageNames = appPackages;
        priorPlatformPackageNames = platformPackages;
        priorBootPackageNames = bootPackages;
    }

    public static HostedClassLoaderPackageManagement singleton() {
        return ImageSingletons.lookup(HostedClassLoaderPackageManagement.class);
    }

    private boolean isPackageRegistered(ClassLoader classLoader, String packageName, Package packageValue) {
        if (inExtensionLayer) {
            if (classLoader == bootClassLoader) {
                var result = priorBootPackageNames.contains(packageName);
                VMError.guarantee(result, "Newly seen boot package %s", packageValue);
                return true;
            } else if (classLoader == platformClassLoader) {
                var result = priorPlatformPackageNames.contains(packageName);
                VMError.guarantee(result, "Newly seen platform package %s", packageValue);
                return true;
            } else if (classLoader == appClassLoader) {
                if (priorAppPackageNames.contains(packageName)) {
                    return true;
                }
            } else if (isGeneratedSerializationClassLoader(classLoader)) {
                VMError.guarantee(packageName.equals(GENERATED_SERIALIZATION_PACKAGE), "Unexpected package %s in generated serialization class loader", packageValue);
                return true;
            } else {
                throw VMError.shouldNotReachHere("Currently unhandled class loader seen in extension layer: %s", classLoader);
            }
        }

        var loaderPackages = registeredPackages.get(classLoader);
        return loaderPackages != null && loaderPackages.containsKey(packageName);
    }

    public static boolean isGeneratedSerializationClassLoader(ClassLoader classLoader) {
        return SerializationSupport.currentLayer().isGeneratedSerializationClassLoader(classLoader);
    }

    public static String getClassLoaderSerializationLookupKey(ClassLoader classLoader) {
        return GENERATED_SERIALIZATION_KEY + SerializationSupport.currentLayer().getClassLoaderSerializationLookupKey(classLoader);
    }

    /**
     * Register the package with its runtimeClassLoader.
     */
    public void registerPackage(ClassLoader runtimeClassLoader, String packageName, Package packageValue, Consumer<Object> objectScanner) {
        assert runtimeClassLoader != null;
        assert packageName != null;
        assert packageValue != null;
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Packages should be collected and registered during analysis.");

        if (isPackageRegistered(runtimeClassLoader, packageName, packageValue)) {
            // already registered; nothing to do.
            return;
        }

        /*
         * Eagerly initialize the field Package.packageInfo, which stores the .package-info class
         * (if present) with the annotations for the package. We want that class to be available
         * without having to register it manually in the reflection configuration file.
         *
         * Note that we either need to eagerly initialize that field (the approach chosen) or
         * force-reset it to null for all packages, otherwise there can be transient problems when
         * the lazy initialization happens in the image builder after the static analysis.
         *
         * Note in extension layers we know the class loader is only allowed to be the app class
         * loader.
         */
        try {
            packageGetPackageInfo.invoke(packageValue);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
        }

        var loaderPackages = registeredPackages.computeIfAbsent(runtimeClassLoader, k -> new ConcurrentHashMap<>());
        Object previous = loaderPackages.putIfAbsent(packageName, packageValue);
        if (previous == null) {
            /* Scan the class loader packages if the new package was missing. */
            objectScanner.accept(loaderPackages);
            if (inSharedLayer && runtimeClassLoader == appClassLoader) {
                VMError.guarantee(packageValue.getName().equals(packageName), "Package name is different from package value's name: %s %s", packageName, packageValue);

                /*
                 * We must register this package so that it can be relinked in subsequent layers.
                 */
                registry.registerHeapConstant(generateKeyName(packageValue.getName()), packageValue);
            }
        }
    }

    public ImageHeapConstant replaceWithPriorLayerPackage(Object obj) {
        if (obj instanceof Package hostedPackage) {
            /*
             * Determine the package's ClassLoader
             */
            Module module = ReflectionUtil.invokeMethod(moduleLookup, hostedPackage);
            ClassLoader classLoader = ClassLoaderFeature.getRuntimeClassLoader(module.getClassLoader());
            VMError.guarantee(classLoader == appClassLoader, "Currently unhandled class loader seen in extension layer: %s", classLoader);

            var keyName = generateKeyName(hostedPackage.getName());
            if (registry.constantExists(keyName)) {
                return (ImageHeapConstant) registry.getConstant(keyName);
            }
        }

        return null;
    }

    private static String generateKeyName(String packageName) {
        return String.format("AppClassLoaderPackage#%s", packageName);
    }

    public ConcurrentHashMap<String, Package> getRegisteredPackages(ClassLoader classLoader) {
        VMError.guarantee(BuildPhaseProvider.isAnalysisFinished(), "Packages are stable only after analysis.");
        VMError.guarantee(ImageLayerBuildingSupport.firstImageBuild(), "All classloaders should be transformed in the first layer %s", classLoader);

        return registeredPackages.get(classLoader);
    }

    /**
     * Returns a map containing all packages installed by prior layers, as well as the entries
     * installed in this layer.
     */
    public ConcurrentHashMap<String, Object> getAppClassLoaderPackages() {
        VMError.guarantee(BuildPhaseProvider.isAnalysisFinished(), "Packages are stable only after analysis.");
        VMError.guarantee(ImageLayerBuildingSupport.lastImageBuild(), "AppClassLoader's Packages are only fully available in the application layer");

        var finalAppMap = getPriorAppClassLoaderPackages();
        var currentPackages = registeredPackages.get(appClassLoader);
        if (currentPackages != null) {
            for (var entry : currentPackages.entrySet()) {
                var previous = finalAppMap.put(entry.getKey(), entry.getValue());
                assert previous == null : Assertions.errorMessage(entry.getKey(), previous);
            }
        }

        return finalAppMap;
    }

    /**
     * Returns a concurrent hashmap containing the package map produced via the prior layer. Note at
     * runtime this map's keys will all be of type {@link Package}. We use a
     * {@link PriorLayerMarker} to perform this transformation.
     */
    public ConcurrentHashMap<String, Object> getPriorAppClassLoaderPackages() {
        ConcurrentHashMap<String, Object> finalAppMap = new ConcurrentHashMap<>();
        for (String name : priorAppPackageNames) {
            var previous = finalAppMap.put(name, new PriorLayerPackageReference(generateKeyName(name)));
            assert previous == null : Assertions.errorMessage(name, previous);
        }
        return finalAppMap;
    }

    private record PriorLayerPackageReference(String key) implements PriorLayerMarker {

        @Override
        public String getKey() {
            return key;
        }

    }

    public void initialize(ClassLoader newAppClassLoader, CrossLayerConstantRegistry newRegistry) {
        assert appClassLoader == null && newAppClassLoader != null : Assertions.errorMessage(appClassLoader, newAppClassLoader);
        assert registry == null && newRegistry != null : Assertions.errorMessage(registry, newRegistry);

        appClassLoader = newAppClassLoader;
        registry = newRegistry;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    private List<String> collectPackageNames(ClassLoader classLoader, Set<String> priorPackageNames) {
        var map = registeredPackages.get(classLoader);
        if (priorPackageNames != null && map != null) {
            return Stream.concat(map.keySet().stream(), priorPackageNames.stream()).toList();
        } else if (priorPackageNames == null && map != null) {
            return map.keySet().stream().toList();
        } else if (priorPackageNames != null && map == null) {
            return priorPackageNames.stream().toList();
        } else {
            throw VMError.shouldNotReachHere("Bad state: %s %s", priorPackageNames, map);
        }
    }

    @Override
    public LayeredImageSingleton.PersistFlags preparePersist(ImageSingletonWriter writer) {
        writer.writeStringList(APP_KEY, collectPackageNames(appClassLoader, priorAppPackageNames));
        writer.writeStringList(PLATFORM_KEY, collectPackageNames(platformClassLoader, priorPlatformPackageNames));
        writer.writeStringList(BOOT_KEY, collectPackageNames(bootClassLoader, priorBootPackageNames));

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        var appSet = loader.readStringList(APP_KEY).stream().collect(Collectors.toUnmodifiableSet());
        var platformSet = loader.readStringList(PLATFORM_KEY).stream().collect(Collectors.toUnmodifiableSet());
        var bootSet = loader.readStringList(BOOT_KEY).stream().collect(Collectors.toUnmodifiableSet());

        return new HostedClassLoaderPackageManagement(appSet, platformSet, bootSet);
    }
}
