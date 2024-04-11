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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;

public final class ImageSingletonsSupportImpl extends ImageSingletonsSupport {

    @Override
    public <T> void add(Class<T> key, T value) {
        HostedManagement.getAndAssertExists().doAdd(key, value);
    }

    @Override
    public <T> T lookup(Class<T> key) {
        return HostedManagement.getAndAssertExists().doLookup(key);
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
        private static final Object SINGLETON_INSTALLATION_FOBIDDEN = new Object();

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

        public static void install(HostedManagement vmConfig, SVMImageLayerSupport support) {
            UserError.guarantee(singletonDuringImageBuild == null, "Only one native image build can run at a time");
            singletonDuringImageBuild = vmConfig;
            if (support != null && support.loadImageSingletons()) {
                singletonDuringImageBuild.installPriorSingletonInfo(support.getLoader());
            } else {
                singletonDuringImageBuild.doAddInternal(LoadedLayeredImageSingletonInfo.class, new LoadedLayeredImageSingletonInfo(Set.of()));
            }
        }

        private void installPriorSingletonInfo(SVMImageLayerLoader info) {
            var result = info.loadImageSingletons(SINGLETON_INSTALLATION_FOBIDDEN);
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
            var list = singletonDuringImageBuild.configObjects.entrySet().stream().filter(e -> e.getValue() instanceof LayeredImageSingleton).sorted(Comparator.comparing(e -> e.getKey().getName()))
                            .toList();
            SVMImageLayerSupport.singleton().getWriter().writeImageSingletonInfo(list);
        }

        private final Map<Class<?>, Object> configObjects;

        public HostedManagement() {
            this.configObjects = new ConcurrentHashMap<>();
        }

        <T> void doAdd(Class<T> key, T value) {
            doAddInternal(key, value);
        }

        private void doAddInternal(Class<?> key, Object value) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            if (value instanceof LayeredImageSingleton singleton) {
                assert singleton.verifyImageBuilderFlags();

                if (singleton.getImageBuilderFlags().contains(LayeredImageSingleton.ImageBuilderFlags.UNSUPPORTED)) {
                    throw UserError.abort("Unsupported image singleton is being installed %s %s", key.getTypeName(), singleton);
                }
            }

            Object prevValue = configObjects.putIfAbsent(key, value);

            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
            }
        }

        <T> T doLookup(Class<T> key) {
            checkKey(key);
            Object result = configObjects.get(key);
            if (result == null) {
                throw UserError.abort("ImageSingletons do not contain key %s", key.getTypeName());
            } else if (result == SINGLETON_INSTALLATION_FOBIDDEN) {
                throw UserError.abort("A LayeredImageSingleton was installed in a prior layer which forbids creating the singleton in a subsequent layer. Key %s", key.getTypeName());
            }
            return key.cast(result);
        }

        boolean doContains(Class<?> key) {
            checkKey(key);
            var value = configObjects.get(key);
            return value != null && value != SINGLETON_INSTALLATION_FOBIDDEN;
        }

        private static void checkKey(Class<?> key) {
            if (key == null) {
                throw UserError.abort("ImageSingletons do not allow null keys");
            }
        }
    }
}
