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
package com.oracle.svm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@Platforms(Platform.HOSTED_ONLY.class)
public final class ModuleSupport extends ModuleSupportBase {
    private ModuleSupport() {
    }

    /**
     * Add the proper module opening to allow accesses from accessingClass to declaringClass.
     */
    @SuppressWarnings("unused")
    public static void openModuleByClass(Class<?> declaringClass, Class<?> accessingClass) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Exports and opens a single package {@code packageName} in the module named {@code moduleName}
     * to all unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenPackageToClass(String moduleName, String packageName, boolean optional, Class<?> accessingClass) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Exports and opens all packages in the module named {@code name} to all unnamed modules.
     *
     * @param optional if {@code false} and there is no module named {@code name},
     *            {@link NoSuchElementException} is thrown
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name, boolean optional) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Exports and opens {@code pkg} in the module named {@code name} to all unnamed modules.
     *
     * @param optional if {@code false} and there is no module named {@code name},
     *            {@link NoSuchElementException} is thrown
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenPackageToUnnamed(String name, String pkg, boolean optional) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Gets the name of the module containing {@code clazz}.
     */
    @SuppressWarnings("unused")
    public static String getModuleName(Class<?> clazz) {
        assert JavaVersionUtil.JAVA_SPEC <= 8;
        return null;
    }

    /**
     * In the modules of the boot module layer, filters all resources that match the given
     * predicate, and calls the operation on the matched resources. This is a temporary solution
     * until we fully support modules in native-image
     *
     * @param resourceNameFilter predicate applied to all resource names in the module
     * @param operation a function to process matched resources, it receives the name of the
     *            resources as the first argument and an open stream as the second argument
     */
    @SuppressWarnings("unused")
    public static void findResourcesInModules(Predicate<String> resourceNameFilter, BiConsumer<String, InputStream> operation) throws IOException {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
        throw new IOException("find resources in modules can not be called in java 8 or less");
    }
}
