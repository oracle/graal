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
package com.oracle.svm.test;

import java.util.HashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

// Checkstyle: allow Class.getSimpleName

/**
 * Tests a workaround for {@linkplain ServiceLoader services} without a {@linkplain ServiceLoader
 * provider constructor} (nullary constructor) [GR-19958]. The workaround completely ignores
 * services without a provider constructor, instead of throwing an {@link ServiceConfigurationError}
 * when iterating the services. See the Github issue
 * <a href="https://github.com/oracle/graal/issues/2652">"Spring Service Registry native-image
 * failure due to service loader handling in jersey #2652"</a> for more details.
 */
public class NoProviderConstructorServiceLoaderTest {

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeClassInitialization.initializeAtBuildTime(NoProviderConstructorServiceLoaderTest.class);
            RuntimeClassInitialization.initializeAtBuildTime(NoProviderConstructorServiceLoaderTest.class.getClasses());
        }
    }

    interface ServiceInterface {
    }

    public static class ProperService implements ServiceInterface {
    }

    public abstract static class NoProviderConstructorService implements ServiceInterface {
        /**
         * Not a provider constructor. This violates the contract of {@link ServiceLoader}.
         */
        NoProviderConstructorService(@SuppressWarnings("unused") Class<?> clazz) {
        }
    }

    private static final Set<String> EXPECTED = Set.of(ProperService.class.getSimpleName());

    /**
     * This should actually throw an {@link ServiceConfigurationError}.
     *
     * @see #testLazyStreamHotspot()
     */
    @Test
    public void testLazyStreamNativeImage() {
        Assume.assumeTrue("native image specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.type().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    /**
     * This should actually throw an {@link ServiceConfigurationError}.
     *
     * @see #testEagerStreamHotspot()
     */
    @Test
    public void testEagerStreamNativeImage() {
        Assume.assumeTrue("native image specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.get().getClass().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    /**
     * This should actually throw an {@link ServiceConfigurationError}.
     *
     * @see #testEagerIteratorHotspot()
     */
    @Test
    public void testEagerIteratorNativeImage() {
        Assume.assumeTrue("native image specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = new HashSet<>();
        ServiceLoader.load(ServiceInterface.class).iterator()
                        .forEachRemaining(s -> simpleNames.add(s.getClass().getSimpleName()));
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    /**
     * @see #testLazyStreamNativeImage()
     */
    @Test(expected = ServiceConfigurationError.class)
    public void testLazyStreamHotspot() {
        Assume.assumeFalse("hotspot specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.type().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertNull("should not reach", simpleNames);
    }

    /**
     * @see #testEagerStreamNativeImage()
     */
    @Test(expected = ServiceConfigurationError.class)
    public void testEagerStreamHotspot() {
        Assume.assumeFalse("hotspot specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.get().getClass().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertNull("should not reach", simpleNames);
    }

    /**
     * @see #testEagerIteratorNativeImage()
     */
    @Test(expected = ServiceConfigurationError.class)
    public void testEagerIteratorHotspot() {
        Assume.assumeFalse("hotspot specific behavior", ImageInfo.inImageRuntimeCode());
        Set<String> simpleNames = new HashSet<>();
        ServiceLoader.load(ServiceInterface.class).iterator()
                        .forEachRemaining(s -> simpleNames.add(s.getClass().getSimpleName()));
        Assert.assertNull("should not reach", simpleNames);
    }
}
