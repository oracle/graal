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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.internal.module.Modules;

public final class ModuleSupport {

    public static final String MODULE_SET_ALL_DEFAULT = "ALL-DEFAULT";
    public static final String MODULE_SET_ALL_SYSTEM = "ALL-SYSTEM";
    public static final String MODULE_SET_ALL_MODULE_PATH = "ALL-MODULE-PATH";
    public static final List<String> nonExplicitModules = List.of(MODULE_SET_ALL_DEFAULT, MODULE_SET_ALL_SYSTEM, MODULE_SET_ALL_MODULE_PATH);

    public static final String ENV_VAR_USE_MODULE_SYSTEM = "USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM";
    public static final String PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES = "org.graalvm.nativeimage.module.addmods";
    public static final String PROPERTY_IMAGE_EXPLICITLY_LIMITED_MODULES = "org.graalvm.nativeimage.module.limitmods";
    public static final boolean modulePathBuild = isModulePathBuild();

    private ModuleSupport() {
    }

    private static boolean isModulePathBuild() {
        return !"false".equalsIgnoreCase(System.getenv().get(ENV_VAR_USE_MODULE_SYSTEM));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public enum Access {
        OPEN {
            @Override
            void giveAccess(Module accessingModule, Module declaringModule, String packageName) {
                if (accessingModule != null) {
                    if (declaringModule.isOpen(packageName, accessingModule)) {
                        return;
                    }
                    Modules.addOpens(declaringModule, packageName, accessingModule);
                } else {
                    if (declaringModule.isOpen(packageName)) {
                        return;
                    }
                    Modules.addOpensToAllUnnamed(declaringModule, packageName);
                }
            }
        },
        EXPORT {
            @Override
            void giveAccess(Module accessingModule, Module declaringModule, String packageName) {
                if (accessingModule != null) {
                    if (declaringModule.isExported(packageName, accessingModule)) {
                        return;
                    }
                    Modules.addExports(declaringModule, packageName, accessingModule);
                } else {
                    if (declaringModule.isExported(packageName)) {
                        return;
                    }
                    Modules.addExportsToAllUnnamed(declaringModule, packageName);
                }
            }
        };

        abstract void giveAccess(Module accessingModule, Module declaringModule, String packageName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessModuleByClass(Access access, Class<?> accessingClass, Class<?> declaringClass) {
        accessModuleByClass(access, accessingClass, declaringClass.getModule(), declaringClass.getPackageName());
    }

    @SuppressWarnings("serial")
    public static final class ModuleSupportError extends Error {
        private ModuleSupportError(String message) {
            super(message);
        }
    }

    /**
     * Open or export packages {@code packageNames} in the module named {@code moduleName} to module
     * of given {@code accessingClass}. If {@code accessingClass} is null packages are opened or
     * exported to ALL-UNNAMED. If no packages are given, all packages of the module are opened or
     * exported.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessPackagesToClass(Access access, Class<?> accessingClass, boolean optional, String moduleName, String... packageNames) {
        Objects.requireNonNull(moduleName);
        Optional<Module> module = ModuleLayer.boot().findModule(moduleName);
        if (module.isEmpty()) {
            if (optional) {
                return;
            }
            String accessor = accessingClass != null ? "class " + accessingClass.getTypeName() : "ALL-UNNAMED";
            String message = access.name().toLowerCase() + " of packages from module " + moduleName + " to " +
                            accessor + " failed. No module named " + moduleName + " in boot layer.";
            throw new ModuleSupportError(message);
        }
        Module declaringModule = module.get();
        Objects.requireNonNull(packageNames);
        Set<String> packages = packageNames.length > 0 ? Set.of(packageNames) : declaringModule.getPackages();
        for (String packageName : packages) {
            accessModuleByClass(access, accessingClass, declaringModule, packageName);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void accessModuleByClass(Access access, Class<?> accessingClass, Module declaringModule, String packageName) {
        Module namedAccessingModule = null;
        if (accessingClass != null) {
            Module accessingModule = accessingClass.getModule();
            if (accessingModule.isNamed()) {
                namedAccessingModule = accessingModule;
            }
        }
        access.giveAccess(namedAccessingModule, declaringModule, packageName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessToUnnamedModule(Module from, Module to, String pn) {
        Modules.addOpens(from, pn, to);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessToAllUnnamedModule(Module from, String pn) {
        Modules.addOpensToAllUnnamed(from, pn);
    }
}
