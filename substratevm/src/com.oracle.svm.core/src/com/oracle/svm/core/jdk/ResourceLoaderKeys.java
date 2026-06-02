/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.util.VMError;

/**
 * Shared loader-key vocabulary used to record resource ownership across hosted registration and
 * image runtime.
 */
public final class ResourceLoaderKeys {
    public static final String APP = "app";
    public static final String PLATFORM = "platform";
    public static final String BOOT = "boot";
    public static final String SYNTHETIC_PREFIX = "synthetic-";

    @Platforms(Platform.HOSTED_ONLY.class) private static Hosted hosted;

    private ResourceLoaderKeys() {
    }

    public static String synthetic(int id) {
        assert id > 0 : "Synthetic resource loader ids must be positive.";
        return SYNTHETIC_PREFIX + id;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void initializeHostedResourceLoaderSupport(ClassLoader appClassLoader, ClassLoader platformClassLoader, ClassLoader bootClassLoader,
                    Function<ClassLoader, ClassLoader> runtimeClassLoaderMapper) {
        hosted = new Hosted(appClassLoader, platformClassLoader, bootClassLoader, runtimeClassLoaderMapper);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Hosted hosted() {
        VMError.guarantee(hosted != null, "Hosted resource loader support was not initialized.");
        return hosted;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class Hosted {
        private final ClassLoader hostedAppClassLoader;
        private final ClassLoader hostedPlatformClassLoader;
        private final ClassLoader hostedBootClassLoader;
        private final Function<ClassLoader, ClassLoader> hostedToRuntimeClassLoaderMapper;
        private final Map<ClassLoader, Integer> resourceLoaderIds;
        private final AtomicInteger nextSyntheticResourceLoaderId;

        private Hosted(ClassLoader appClassLoader, ClassLoader platformClassLoader, ClassLoader bootClassLoader,
                        Function<ClassLoader, ClassLoader> runtimeClassLoaderMapper) {
            hostedAppClassLoader = appClassLoader;
            hostedPlatformClassLoader = platformClassLoader;
            hostedBootClassLoader = bootClassLoader;
            hostedToRuntimeClassLoaderMapper = runtimeClassLoaderMapper;
            resourceLoaderIds = new IdentityHashMap<>();
            nextSyntheticResourceLoaderId = new AtomicInteger(1);
        }

        /**
         * Returns the stable hosted-time key used to record resource ownership for {@code original}.
         * The loader is normalized to the runtime loader that will survive into the image heap
         * before a key is chosen.
         */
        public String getResourceLoaderKey(ClassLoader original) {
            ClassLoader loader = getHostedRuntimeClassLoader(original);
            if (loader == null || loader == hostedBootClassLoader) {
                return BOOT;
            } else if (loader == hostedAppClassLoader) {
                return APP;
            } else if (loader == hostedPlatformClassLoader) {
                return PLATFORM;
            }
            return synthetic(getResourceLoaderIdForHostedRuntimeLoader(loader));
        }

        /**
         * Returns the stable hosted-time id for non-built-in resource-owning class loaders.
         * Built-in class loaders keep their identity-based resource keys and therefore return
         * {@code 0}.
         */
        public int getResourceLoaderId(ClassLoader original) {
            ClassLoader loader = getHostedRuntimeClassLoader(original);
            return getResourceLoaderIdForHostedRuntimeLoader(loader);
        }

        private int getResourceLoaderIdForHostedRuntimeLoader(ClassLoader loader) {
            if (loader == null || loader == hostedBootClassLoader || loader == hostedPlatformClassLoader || loader == hostedAppClassLoader) {
                return 0;
            }
            synchronized (resourceLoaderIds) {
                return resourceLoaderIds.computeIfAbsent(loader, _ -> nextSyntheticResourceLoaderId.getAndIncrement());
            }
        }

        private ClassLoader getHostedRuntimeClassLoader(ClassLoader original) {
            return hostedToRuntimeClassLoaderMapper.apply(original);
        }
    }
}
