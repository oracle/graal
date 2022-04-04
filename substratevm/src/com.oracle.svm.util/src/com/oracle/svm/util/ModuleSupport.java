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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.internal.module.Modules;

@Platforms(Platform.HOSTED_ONLY.class)
public final class ModuleSupport extends ModuleSupportBase {
    private ModuleSupport() {
    }

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

    public static void accessModuleByClass(Access access, Class<?> accessingClass, Class<?> declaringClass) {
        accessModuleByClass(access, accessingClass, declaringClass.getModule(), declaringClass.getPackageName());
    }

    /**
     * Open or export packages {@code packageNames} in the module named {@code moduleName} to module
     * of given {@code accessingClass}. If {@code accessingClass} is null packages are opened or
     * exported to ALL-UNNAMED. If no packages are given, all packages of the module are opened or
     * exported.
     */
    public static void accessPackagesToClass(Access access, Class<?> accessingClass, boolean optional, String moduleName, String... packageNames) {
        Module declaringModule = getModule(moduleName, optional);
        if (declaringModule == null) {
            return;
        }
        Objects.requireNonNull(packageNames);
        Set<String> packages = packageNames.length > 0 ? Set.of(packageNames) : declaringModule.getPackages();
        for (String packageName : packages) {
            accessModuleByClass(access, accessingClass, declaringModule, packageName);
        }
    }

    private static Module getModule(String moduleName, boolean optional) {
        Objects.requireNonNull(moduleName);
        Optional<Module> declaringModuleOpt = ModuleLayer.boot().findModule(moduleName);
        if (declaringModuleOpt.isEmpty()) {
            if (optional) {
                return null;
            }
            throw new NoSuchElementException(moduleName);
        }
        return declaringModuleOpt.get();
    }

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
}
