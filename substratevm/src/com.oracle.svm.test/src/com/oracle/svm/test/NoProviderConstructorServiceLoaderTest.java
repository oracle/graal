/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Test both JCA-compliant services and non-JCA-compliant services (without a nullary constructor),
 * and compare the behavior between Native Image and Hotspot.
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

    @Test(expected = ServiceConfigurationError.class)
    public void testLazyStreamNativeImage() {
        assumeEnvironment(true);
        Set<String> simpleNames = loadLazyStreamNames();
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testEagerStreamNativeImage() {
        assumeEnvironment(true);
        Set<String> simpleNames = loadEagerStreamNames();
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testEagerIteratorNativeImage() {
        assumeEnvironment(true);
        Set<String> simpleNames = loadEagerIteratorNames();
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testLazyStreamHotspot() {
        assumeEnvironment(false);
        Set<String> simpleNames = loadLazyStreamNames();
        Assert.assertNull("should not reach", simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testEagerIteratorHotspot() {
        assumeEnvironment(false);
        Set<String> simpleNames = loadEagerIteratorNames();
        Assert.assertNull("should not reach", simpleNames);
    }

    /**
     * Helper method to assume the environment (hotspot/native image).
     */
    private static void assumeEnvironment(boolean isNativeImage) {
        if (isNativeImage) {
            Assume.assumeTrue("native image specific behavior", ImageInfo.inImageRuntimeCode());
        } else {
            Assume.assumeFalse("hotspot specific behavior", ImageInfo.inImageRuntimeCode());
        }
    }

    /**
     * Helper method for lazy stream tests.
     */
    private static Set<String> loadLazyStreamNames() {
        return ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.type().getSimpleName())
                        .collect(Collectors.toSet());
    }

    /**
     * Helper method for eager stream tests.
     */
    private static Set<String> loadEagerStreamNames() {
        return ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.get().getClass().getSimpleName())
                        .collect(Collectors.toSet());
    }

    /**
     * Helper method for eager iterator tests.
     */
    private static Set<String> loadEagerIteratorNames() {
        Set<String> simpleNames = new HashSet<>();
        ServiceLoader.load(ServiceInterface.class).iterator()
                        .forEachRemaining(s -> simpleNames.add(s.getClass().getSimpleName()));
        return simpleNames;
    }
}
