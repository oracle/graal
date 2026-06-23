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

import java.util.Set;

import com.oracle.svm.shared.util.SubstrateUtil;

/// Exposes package-private boot loader substitution state to `BootClassRegistry`.
public final class BootLoaderPackageAccess {
    private BootLoaderPackageAccess() {
    }

    /// Finds the named boot module for `internalPackageName` already defined to the boot loader.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    /// @return `null` if the boot loader has not yet loaded a class in the named package
    public static String definedBootModuleNameForPackage(String internalPackageName) {
        String packageName = internalPackageName.replace('/', '.');
        Target_java_lang_ClassLoader bootLoader = SubstrateUtil.cast(Target_jdk_internal_loader_ClassLoaders.bootLoader(), Target_java_lang_ClassLoader.class);
        if (!bootLoader.packages.containsKey(packageName)) {
            return null;
        }
        return bootModuleNameForPackage(internalPackageName);
    }

    /// Finds the boot module that declares `internalPackageName`.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    /// @return `null` if the runtime boot module layer does not contain the package
    public static String bootModuleNameForPackage(String internalPackageName) {
        Target_jdk_internal_loader_BuiltinClassLoader_LoadedModule loadedModule = Target_jdk_internal_loader_BuiltinClassLoader.packageToModule.get(internalPackageName.replace('/', '.'));
        return loadedModule == null || loadedModule.loader() != Target_jdk_internal_loader_ClassLoaders.bootLoader() ? null : loadedModule.name();
    }

    /// Ensures a NamedPackage object for the package `internalPackageName`
    /// exists in the `ClassLoader.packages` field of the boot loader.
    ///
    /// @param internalPackageName package name in internal format (e.g. `java/util`)
    /// @param module the boot module containing a class in the package
    public static void ensureNamedPackageExists(String internalPackageName, Module module) {
        String packageName = internalPackageName.replace('/', '.');
        Target_java_lang_ClassLoader bootLoader = SubstrateUtil.cast(Target_jdk_internal_loader_ClassLoaders.bootLoader(), Target_java_lang_ClassLoader.class);
        bootLoader.getNamedPackage(packageName, module);
    }

    /// Adds package names already defined to the boot loader to `internalPackageNames`.
    ///
    /// @param internalPackageNames set of package names in internal format (e.g. `java/util`)
    public static void addSystemPackageNames(Set<String> internalPackageNames) {
        Target_java_lang_ClassLoader bootLoader = SubstrateUtil.cast(Target_jdk_internal_loader_ClassLoaders.bootLoader(), Target_java_lang_ClassLoader.class);
        for (String packageName : bootLoader.packages.keySet()) {
            internalPackageNames.add(packageName.replace('.', '/'));
        }
    }
}
