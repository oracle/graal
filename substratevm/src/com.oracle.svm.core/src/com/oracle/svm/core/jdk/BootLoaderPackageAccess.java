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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.shared.util.SubstrateUtil;

/// Exposes package-private boot loader substitution state to `BootClassRegistry`.
public final class BootLoaderPackageAccess {
    private BootLoaderPackageAccess() {
    }

    /// Lazily initialized map of a package name to the name of the
    /// boot-loader loaded module whose descriptor [includes the package][java.lang.module.ModuleDescriptor#packages()].
    private static volatile Map<String, String> bootPackageNameToModuleName;

    /// Finds the boot-loader loaded module whose descriptor [includes][java.lang.module.ModuleDescriptor#packages()] `internalPackageName`.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    /// @return `null` if `internalPackageName` is not listed in any boot-loader loaded module descriptor
    public static String bootModuleNameForPackage(String internalPackageName) {
        String packageName = internalPackageName.replace('/', '.');
        if (bootPackageNameToModuleName == null) {
            synchronized (BootLoaderPackageAccess.class) {
                if (bootPackageNameToModuleName == null) {
                    Map<String, String> result = new HashMap<>();
                    for (Module m : ModuleLayer.boot().modules()) {
                        if (m.getClassLoader() == null) {
                            for (String p : m.getDescriptor().packages()) {
                                result.put(p, m.getName());
                            }
                        }
                    }
                    // Create an immutable copy of the map
                    bootPackageNameToModuleName = Map.copyOf(result);
                }
            }
        }
        return bootPackageNameToModuleName.get(packageName);
    }

    /// Finds the named boot module for `internalPackageName` already defined to the boot loader.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    /// @return `null` if the boot loader has not yet loaded a class in the named package
    public static String definedBootModuleNameForPackage(String internalPackageName) {
        String packageName = internalPackageName.replace('/', '.');
        Target_jdk_internal_loader_BuiltinClassLoader bootLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader bootClassLoader = SubstrateUtil.cast(bootLoader, Target_java_lang_ClassLoader.class);
        if (!bootClassLoader.packages.containsKey(packageName)) {
            return null;
        }
        return bootModuleNameForPackage(internalPackageName);
    }

    /// Defines the boot module package `internalPackageName` after runtime class loading has defined a class in it.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    public static void defineBootModulePackageForPackage(String internalPackageName) {
        String packageName = internalPackageName.replace('/', '.');
        Target_jdk_internal_loader_BuiltinClassLoader bootLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader bootClassLoader = SubstrateUtil.cast(bootLoader, Target_java_lang_ClassLoader.class);
        if (bootClassLoader.getDefinedPackage(packageName) == null) {
            String moduleName = bootModuleNameForPackage(internalPackageName);
            if (moduleName != null) {
                Target_jdk_internal_loader_BootLoader_PackageHelper.definePackage(packageName.intern(), "jrt:/" + moduleName);
            }
        }
    }

    /// Adds package names already defined to the boot loader to `internalPackageNames`.
    ///
    /// @param internalPackageNames set of package names in internal format (e.g. `java/util`)
    public static void addSystemPackageNames(Set<String> internalPackageNames) {
        Target_jdk_internal_loader_BuiltinClassLoader bootLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader bootClassLoader = SubstrateUtil.cast(bootLoader, Target_java_lang_ClassLoader.class);
        for (String packageName : bootClassLoader.packages.keySet()) {
            internalPackageNames.add(packageName.replace('.', '/'));
        }
    }
}
