/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ApplicationLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.RuntimeOnlyWrapper;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;

public final class ImageSingletonsSupportImpl extends ImageSingletonsSupport implements LayeredImageSingletonSupport {

    @Override
    public <T> void add(Class<T> key, T value) {
        HostedManagement.getAndAssertExists().doAdd(key, value);
    }

    @Override
    public <T> T lookup(Class<T> key) {
        return HostedManagement.getAndAssertExists().doLookup(key, false);
    }

    @Override
    public <T> T runtimeLookup(Class<T> key) {
        return HostedManagement.getAndAssertExists().doLookup(key, true);
    }

    @Override
    public Collection<Class<?>> getMultiLayeredImageSingletonKeys() {
        return HostedManagement.getAndAssertExists().getMultiLayeredImageSingletonKeys();
    }

    @Override
    public void freezeMultiLayeredImageSingletons() {
        HostedManagement.getAndAssertExists().freezeMultiLayeredImageSingletons();
    }

    @Override
    public boolean contains(Class<?> key) {
        HostedManagement hm = HostedManagement.get();
        if (hm == null) {
            return false;
        } else {
            return hm.doContains(key);
        }
    }

    /**
     * Management of the {@link ImageSingletons} registry during image generation.
     */
    public static final class HostedManagement {

        static {
            ImageSingletonsSupport.installSupport(new ImageSingletonsSupportImpl());
        }

        /**
         * Marker for ImageSingleton keys which cannot have a value installed. This can happen when
         * a {@link LayeredImageSingleton} specified {@link PersistFlags#FORBIDDEN}.
         */
        private static final Object SINGLETON_INSTALLATION_FORBIDDEN = new Object();

        /**
         * The {@link ImageSingletons} removes static state from the image generator, and in theory
         * would allow multiple image builds to run at the same time in the same HotSpot VM. But in
         * practice, this is not possible because JDK state would leak between image builds. So it
         * is OK (and better for performance) to store all the {@link ImageSingletons} in a static
         * field here.
         */
        private static HostedManagement singletonDuringImageBuild;

        private static HostedManagement getAndAssertExists() {
            HostedManagement result = get();
            assert result != null;
            return result;
        }

        public static HostedManagement get() {
            return singletonDuringImageBuild;
        }

        public static void install(HostedManagement vmConfig) {
            install(vmConfig, null);
        }

        public static void install(HostedManagement vmConfig, HostedImageLayerBuildingSupport support) {
            UserError.guarantee(singletonDuringImageBuild == null, "Only one native image build can run at a time");
            singletonDuringImageBuild = vmConfig;

            if (support != null) {
                /*
                 * Note we are intentionally adding this singleton early as build flags may depend
                 * on it. We also intentionally do not mark this singleton as a LayerImageSingleton
                 * to prevent circular dependency complications.
                 */
                singletonDuringImageBuild.doAddInternal(ImageLayerBuildingSupport.class, support);
            } else {
                /*
                 * Create a placeholder ImageLayerBuilding support to indicate this is not a layered
                 * build.
                 */
                singletonDuringImageBuild.doAddInternal(ImageLayerBuildingSupport.class, new ImageLayerBuildingSupport(false, false, false) {
                });
            }
            if (support != null && support.getLoader() != null) {
                /*
                 * Note eventually this may need to be moved to a later point after the Options
                 * Image Singleton is installed.
                 */
                singletonDuringImageBuild.installPriorSingletonInfo(support.getLoader());
            } else {
                singletonDuringImageBuild.doAddInternal(LoadedLayeredImageSingletonInfo.class, new LoadedLayeredImageSingletonInfo(Set.of()));
            }
        }

        private void installPriorSingletonInfo(SVMImageLayerLoader info) {
            var result = info.loadImageSingletons(SINGLETON_INSTALLATION_FORBIDDEN);
            Set<Class<?>> installedKeys = new HashSet<>();
            for (var entry : result.entrySet()) {
                Object singletonToInstall = entry.getKey();
                for (Class<?> key : entry.getValue()) {
                    doAddInternal(key, singletonToInstall);
                    installedKeys.add(key);
                }
            }

            // document what was installed during loading
            doAddInternal(LoadedLayeredImageSingletonInfo.class, new LoadedLayeredImageSingletonInfo(Set.copyOf(installedKeys)));
        }

        public static void clear() {
            singletonDuringImageBuild = null;
        }

        public static void persist() {
            var list = singletonDuringImageBuild.configObjects.entrySet().stream().filter(e -> e.getValue() instanceof LayeredImageSingleton || e.getValue() instanceof RuntimeOnlyWrapper)
                            .sorted(Comparator.comparing(e -> e.getKey().getName()))
                            .toList();
            HostedImageLayerBuildingSupport.singleton().getWriter().writeImageSingletonInfo(list);
        }

        private final Map<Class<?>, Object> configObjects;
        private final boolean checkUnsupported;
        private Set<Class<?>> multiLayeredImageSingletonKeys;

        public HostedManagement() {
            this(false);
        }

        public HostedManagement(boolean checkUnsupported) {
            this.configObjects = new ConcurrentHashMap<>();
            this.multiLayeredImageSingletonKeys = ConcurrentHashMap.newKeySet();
            this.checkUnsupported = checkUnsupported;
        }

        <T> void doAdd(Class<T> key, T value) {
            doAddInternal(key, value);
        }

        private void doAddInternal(Class<?> key, Object value) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            Object storedValue = value;
            if (value instanceof LayeredImageSingleton singleton) {
                assert LayeredImageSingletonBuilderFlags.verifyImageBuilderFlags(singleton);

                if (checkUnsupported && singleton.getImageBuilderFlags().contains(LayeredImageSingletonBuilderFlags.UNSUPPORTED)) {
                    throw UserError.abort("Unsupported image singleton is being installed %s %s", key.getTypeName(), singleton);
                }

                if (singleton instanceof MultiLayeredImageSingleton || ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton)) {

                    if (!key.equals(singleton.getClass())) {
                        throw UserError.abort("The implementation class must be the same as the key class. key: %s, singleton: %s", key, singleton);
                    }

                    if (singleton instanceof MultiLayeredImageSingleton && ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton)) {
                        throw UserError.abort("Singleton cannot implement both %s and %s. singleton: %s", MultiLayeredImageSingleton.class, ApplicationLayerOnlyImageSingleton.class, singleton);
                    }

                    if (ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton) && !ImageLayerBuildingSupport.lastImageBuild()) {
                        throw UserError.abort("Application layer only image singleton can only be installed in the final layer: %s", singleton);
                    }

                    if (singleton instanceof MultiLayeredImageSingleton) {
                        multiLayeredImageSingletonKeys.add(key);
                    }
                }

                if (!singleton.getImageBuilderFlags().contains(LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS)) {
                    storedValue = new RuntimeOnlyWrapper(singleton);
                }
            }

            Object prevValue = configObjects.putIfAbsent(key, storedValue);

            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
            }
        }

        Collection<Class<?>> getMultiLayeredImageSingletonKeys() {
            return multiLayeredImageSingletonKeys;
        }

        void freezeMultiLayeredImageSingletons() {
            multiLayeredImageSingletonKeys = Set.copyOf(multiLayeredImageSingletonKeys);
        }

        <T> T doLookup(Class<T> key, boolean stripRuntimeOnly) {
            checkKey(key);
            Object result = configObjects.get(key);
            if (result == null) {
                throw UserError.abort("ImageSingletons do not contain key %s", key.getTypeName());
            } else if (result == SINGLETON_INSTALLATION_FORBIDDEN) {
                throw UserError.abort("A LayeredImageSingleton was installed in a prior layer which forbids creating the singleton in a subsequent layer. Key %s", key.getTypeName());
            } else if (result instanceof RuntimeOnlyWrapper wrapper) {
                if (!stripRuntimeOnly) {
                    throw UserError.abort("A LayeredImageSingleton was accessed during image building which does not have %s access. Key: %s, object %s",
                                    LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS, key, wrapper.wrappedObject());
                }
                result = wrapper.wrappedObject();

            }
            return key.cast(result);
        }

        boolean doContains(Class<?> key) {
            checkKey(key);
            var value = configObjects.get(key);
            return value != null && value != SINGLETON_INSTALLATION_FORBIDDEN;
        }

        private static void checkKey(Class<?> key) {
            if (key == null) {
                throw UserError.abort("ImageSingletons do not allow null keys");
            }
        }
    }
}
