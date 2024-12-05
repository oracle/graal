/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.module.ServicesCatalog;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * <p>
 * Runtime support for {@link ClassLoaderValue} (CLV) mappings in the JDK.
 * {@link jdk.internal.loader.BootLoader} has a static CLV, whereas every {@link ClassLoader}
 * instance has its own {@link ClassLoaderValue} map. Most CLV mappings are cleared for runtime,
 * except for {@link ServicesCatalog} and {@link ModuleLayer} CLV mappings.
 * </p>
 * <p>
 * As these mappings contain {@link ModuleLayer} (and through it, also {@link Module}) references,
 * it is necessary that we do not capture hosted {@link Module} and {@link ModuleLayer} instances.
 * Since {@link ModuleLayer} synthesis occurs after analysis, this singleton is only populated after
 * analysis (hence the {@link UnknownObjectField} annotation). The fields in this singleton are used
 * inside {@link org.graalvm.nativeimage.hosted.FieldValueTransformer}s for CLV mappings (see
 * {@link ClassLoaderValueMapFieldValueTransformer}, {@link ModuleLayerCLVTransformer} and
 * {@link ServicesCatalogCLVTransformer}).
 * </p>
 */
@AutomaticallyRegisteredImageSingleton
public final class RuntimeClassLoaderValueSupport {

    public static RuntimeClassLoaderValueSupport instance() {
        return ImageSingletons.lookup(RuntimeClassLoaderValueSupport.class);
    }

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    private ConcurrentHashMap<?, ?> bootLoaderCLV = new ConcurrentHashMap<>();

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    private ConcurrentHashMap<ClassLoader, ConcurrentHashMap<?, ?>> classLoaderValueMaps = new ConcurrentHashMap<>();

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    ClassLoaderValue<ServicesCatalog> servicesCatalogCLV = new ClassLoaderValue<>();

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    ClassLoaderValue<List<ModuleLayer>> moduleLayerCLV = new ClassLoaderValue<>();

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void update(List<ModuleLayer> runtimeModuleLayers) {
        for (ModuleLayer runtimeLayer : runtimeModuleLayers) {
            Set<ClassLoader> loaders = runtimeLayer.modules().stream()
                            .map(Module::getClassLoader)
                            .collect(Collectors.toSet());
            for (ClassLoader loader : loaders) {
                bindRuntimeModuleLayerToLoader(runtimeLayer, loader);
                registerServicesCatalog(loader);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ClassLoader hostedBootLoader;

    @Platforms(Platform.HOSTED_ONLY.class) //
    ConcurrentHashMap<?, ?> getClassLoaderValueMapForLoader(ClassLoader loader) {
        if (loader == null) {
            return bootLoaderCLV;
        } else {
            return classLoaderValueMaps.computeIfAbsent(loader, k -> new ConcurrentHashMap<>());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private ClassLoader resolveClassLoader(ClassLoader loaderOrNull) {
        if (loaderOrNull == null) {
            if (hostedBootLoader != null) {
                return hostedBootLoader;
            }
            Method method = ReflectionUtil.lookupMethod(ClassLoaders.class, "bootLoader");
            hostedBootLoader = ReflectionUtil.invokeMethod(method, null);
            return hostedBootLoader;
        } else {
            return loaderOrNull;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) //
    @Platforms(Platform.HOSTED_ONLY.class) //
    private void bindRuntimeModuleLayerToLoader(ModuleLayer layer, ClassLoader loaderOrNull) {
        ClassLoader loader = resolveClassLoader(loaderOrNull);

        /**
         * Runtime {@link ModuleLayer}s are synthesized and bound to loaders after analysis. This
         * implementation is identical to {@link java.lang.ModuleLayer.bindToLoader}.
         */
        List<ModuleLayer> list = moduleLayerCLV.get(loader);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<ModuleLayer> previous = moduleLayerCLV.putIfAbsent(loader, list);
            if (previous != null) {
                list = previous;
            }
        }
        list.add(layer);

        /*
         * Register the module layer in the appropriate CLV for the given loader.
         */
        var classLoaderValueMap = getClassLoaderValueMapForLoader(loader);
        ((ConcurrentHashMap) classLoaderValueMap).put(moduleLayerCLV, list);
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) //
    @Platforms(Platform.HOSTED_ONLY.class) //
    private void registerServicesCatalog(ClassLoader loaderOrNull) {
        ClassLoader loader = resolveClassLoader(loaderOrNull);

        /*
         * Register the catalog in the ServicesCatalog CLV.
         */
        ServicesCatalog servicesCatalog = getHostedServiceCatalogForLoader(loader);
        if (servicesCatalog == null) {
            servicesCatalog = ServicesCatalog.create();
        }
        servicesCatalogCLV.putIfAbsent(loader, servicesCatalog);

        /*
         * Register the module layer in the appropriate CLV for the given loader.
         */
        var classLoaderValueMap = getClassLoaderValueMapForLoader(loader);
        ((ConcurrentHashMap) classLoaderValueMap).put(servicesCatalogCLV, servicesCatalog);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Field classLoaderCLVField = ReflectionUtil.lookupField(ClassLoader.class, "classLoaderValueMap");

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final ClassLoaderValue<ServicesCatalog> hostedServicesCatalogCLV = ReflectionUtil.readField(ServicesCatalog.class, "CLV", null);

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ServicesCatalog getHostedServiceCatalogForLoader(ClassLoader loader) {
        try {
            ConcurrentHashMap<?, ?> hostedLoaderCLV = (ConcurrentHashMap<?, ?>) classLoaderCLVField.get(loader);
            ServicesCatalog servicesCatalog = (ServicesCatalog) hostedLoaderCLV.get(hostedServicesCatalogCLV);
            return servicesCatalog;
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere("Failed to query ClassLoader.classLoaderValueMap", e);
        }
    }
}
