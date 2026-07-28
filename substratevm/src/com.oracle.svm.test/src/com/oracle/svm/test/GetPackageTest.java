/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+ClassForNameRespectsClassLoader",
                "--add-modules=java.sql"
})
public class GetPackageTest {
    /**
     * Identifies a platform-loader module that keeps a non-boot module package available.
     */
    private static final String PLATFORM_MODULE_NAME = "java.sql";

    /**
     * Identifies a package defined by {@link #PLATFORM_MODULE_NAME}.
     */
    private static final String PLATFORM_MODULE_PACKAGE_NAME = "java.sql";

    /**
     * Checks that class package metadata is available for an included JDK class.
     */
    @Test
    public void testGetPackage() {
        try {
            ArrayList.class.getPackage();
        } catch (Throwable t) {
            Assert.fail("Unexpected exception: " + t);
        }
    }

    /**
     * Checks that {@code Package.getPackage} only returns packages defined by the boot loader.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testGetDefinedPackage() {
        Assert.assertNotNull(Package.getPackage("java.lang"));
        assertNonBootModulePackageNotDefinedByBootLoader();
        Assert.assertNull(Package.getPackage(""));
        Assert.assertNull(Package.getPackage("com.oracle.svm.test.package.does.not.exist"));
    }

    /**
     * Checks that package enumeration follows the boot loader's defined packages.
     */
    @Test
    public void testGetPackages() {
        assertPackageVisibleToGetPackages("java.lang");
        assertUnmaterializedBootModulePackageNotVisible();
    }

    /// Checks that the global JDK package table does not make platform-module packages look like
    /// packages defined to the boot loader.
    @SuppressWarnings("deprecation")
    private static void assertNonBootModulePackageNotDefinedByBootLoader() {
        String packageName = findPlatformModulePackageNotDefinedToPlatformLoader();
        Assert.assertNull(Package.getPackage(packageName));
    }

    /// Finds a platform module package that can only be returned if boot lookup claims it.
    private static String findPlatformModulePackageNotDefinedToPlatformLoader() {
        ClassLoader platformClassLoader = ClassLoader.getPlatformClassLoader();
        Module platformModule = ModuleLayer.boot().findModule(PLATFORM_MODULE_NAME).orElseThrow(() -> new AssertionError("Missing module " + PLATFORM_MODULE_NAME));
        Assert.assertSame(platformClassLoader, platformModule.getClassLoader());
        Assert.assertTrue(platformModule.getPackages().contains(PLATFORM_MODULE_PACKAGE_NAME));
        if (platformClassLoader.getDefinedPackage(PLATFORM_MODULE_PACKAGE_NAME) == null) {
            return PLATFORM_MODULE_PACKAGE_NAME;
        }
        return ModuleLayer.boot().modules().stream()
                        .filter(GetPackageTest::isSystemModule)
                        .filter(module -> module.getClassLoader() == platformClassLoader)
                        .flatMap(module -> module.getPackages().stream())
                        .filter(packageName -> !packageName.isEmpty())
                        .filter(packageName -> platformClassLoader.getDefinedPackage(packageName) == null)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No unmaterialized platform module package could be checked"));
    }

    /// Checks that a boot module descriptor package does not become visible before it is defined.
    @SuppressWarnings("deprecation")
    private static void assertUnmaterializedBootModulePackageNotVisible() {
        String packageName = findUnmaterializedBootModulePackage();
        Assert.assertFalse(visiblePackageNames().contains(packageName));
        Assert.assertNull(Package.getPackage(packageName));
    }

    /// Finds a boot module package that has not been defined to the boot loader.
    private static String findUnmaterializedBootModulePackage() {
        Set<String> unmaterializedBootPackages = new TreeSet<>(bootModulePackages());
        unmaterializedBootPackages.removeAll(visiblePackageNames());
        Assert.assertFalse("No unmaterialized boot module package could be checked", unmaterializedBootPackages.isEmpty());
        return unmaterializedBootPackages.iterator().next();
    }

    /// Verifies that package enumeration includes `packageName`.
    private static void assertPackageVisibleToGetPackages(String packageName) {
        Assert.assertTrue(visiblePackageNames().contains(packageName));
    }

    /// Returns package names listed by system modules owned by the boot loader.
    private static Set<String> bootModulePackages() {
        return ModuleLayer.boot().modules().stream()
                        .filter(GetPackageTest::isSystemModule)
                        .filter(module -> module.getClassLoader() == null)
                        .flatMap(module -> module.getPackages().stream())
                        .filter(packageName -> !packageName.isEmpty())
                        .collect(Collectors.toSet());
    }

    /// Checks whether `module` is backed by a system module image entry.
    private static boolean isSystemModule(Module module) {
        return module.getLayer().configuration().findModule(module.getName())
                        .flatMap(resolvedModule -> resolvedModule.reference().location())
                        .map(location -> "jrt".equals(location.getScheme()))
                        .orElse(false);
    }

    /// Returns package names visible to `Package.getPackages`.
    private static Set<String> visiblePackageNames() {
        return Arrays.stream(Package.getPackages()).map(Package::getName).collect(Collectors.toSet());
    }
}
