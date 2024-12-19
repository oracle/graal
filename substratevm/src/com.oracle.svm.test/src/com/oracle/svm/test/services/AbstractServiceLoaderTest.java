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
package com.oracle.svm.test.services;

import java.util.HashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.junit.Assert;
import org.junit.Test;

// Checkstyle: allow Class.getSimpleName

/**
 * Tests a corner case with respect to an abstract class being registered as a
 * {@linkplain ServiceLoader service}. Although abstract service class cannot be instantiated, it is
 * possible to get its {@link Class} because {@link ServiceLoader#stream()} does not force
 * instantiation as it is a {@link java.util.stream.Stream} of {@link Provider}. This is in contrast
 * to {@link ServiceLoader#iterator()}, which iterates the instances.
 */
public class AbstractServiceLoaderTest {

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeClassInitialization.initializeAtBuildTime(AbstractServiceLoaderTest.class);
            RuntimeClassInitialization.initializeAtBuildTime(AbstractServiceLoaderTest.class.getClasses());
        }
    }

    interface ServiceInterface {
    }

    public static class ConcreteService implements ServiceInterface {
    }

    public abstract static class AbstractService implements ServiceInterface {
    }

    private static final Set<String> EXPECTED = Set.of(ConcreteService.class.getSimpleName(), AbstractService.class.getSimpleName());

    @Test
    public void testLazyStream() {
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.type().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertEquals(EXPECTED, simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testEagerStream() {
        Set<String> simpleNames = ServiceLoader.load(ServiceInterface.class).stream()
                        .map(provider -> provider.get().getClass().getSimpleName())
                        .collect(Collectors.toSet());
        Assert.assertNull("should not reach", simpleNames);
    }

    @Test(expected = ServiceConfigurationError.class)
    public void testEagerIterator() {
        Set<String> simpleNames = new HashSet<>();
        ServiceLoader.load(ServiceInterface.class).iterator()
                        .forEachRemaining(s -> simpleNames.add(s.getClass().getSimpleName()));
        Assert.assertNull("should not reach", simpleNames);
    }
}
