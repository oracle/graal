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
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.module.Modules;

public final class ModuleSupport {
    private ModuleSupport() {
    }

    public static List<String> getModuleResources(Collection<String> names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            Optional<ModuleReference> moduleReference = ModuleFinder.ofSystem().find(name);
            if (!moduleReference.isPresent()) {
                throw new RuntimeException("Unable find ModuleReference for module " + name);
            }
            try (ModuleReader moduleReader = moduleReference.get().open()) {
                result.addAll(moduleReader.list().collect(Collectors.toList()));
            } catch (IOException e) {
                throw new RuntimeException("Unable get list of resources in module" + name, e);
            }
        }
        return result;
    }

    static void openModule(Class<?> declaringClass, Class<?> accessingClass) {
        Module declaringModule = declaringClass.getModule();
        String packageName = declaringClass.getPackageName();
        Module accessingModule = accessingClass.getModule();
        if (!declaringModule.isOpen(packageName, accessingModule)) {
            Modules.addOpens(declaringModule, packageName, accessingModule);
        }
    }

    public static ClassLoader getPlatformClassLoader() {
        return ClassLoader.getPlatformClassLoader();
    }

    /**
     * Exports and opens all packages in the module named {@code name} to all unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name, boolean optional) {
        Optional<Module> value = ModuleLayer.boot().findModule(name);
        if (value.isEmpty()) {
            if (!optional) {
                throw new NoSuchElementException(name);
            }
            return;
        }
        Module module = value.get();
        Set<String> packages = module.getPackages();
        for (String pkg : packages) {
            Modules.addExportsToAllUnnamed(module, pkg);
            Modules.addOpensToAllUnnamed(module, pkg);
        }
    }

    public static String getModuleName(Class<?> clazz) {
        return clazz.getModule().getName();
    }
}
