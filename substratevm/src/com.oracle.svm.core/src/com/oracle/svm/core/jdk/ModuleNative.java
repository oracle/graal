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
package com.oracle.svm.core.jdk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.SourceVersion;

import com.oracle.svm.core.SubstrateUtil;

public final class ModuleNative {
    private ModuleNative() {
    }

    /*
     * Re-implementations of native methods from {@code src/hotspot/share/classfile/modules.cpp}.
     * See {@link Target_java_lang_Module} for more information on module system native
     * substitutions.
     */

    /**
     * {@code Modules::define_module}.
     */
    public static void defineModule(Module module, boolean isOpen, Object[] pns) {
        if (Objects.isNull(module)) {
            throw new NullPointerException("Null module object");
        }

        if (Objects.isNull(module.getName())) {
            throw new IllegalArgumentException("Module name cannot be null");
        }

        if (module.getName().equals("java.base")) {
            if (isOpen) {
                // Checkstyle: stop
                throw new AssertionError("java.base module cannot be open");
                // Checkstyle: resume
            }

            for (Object pn : pns) {
                checkPackageNameForModule(pn, "java.base");
            }

            if (module.getClassLoader() != null) {
                throw new IllegalArgumentException("Class loader must be the boot class loader");
            }

            synchronized (moduleLock) {
                boolean duplicateJavaBase = bootLayerContainsModule("java.base");
                if (duplicateJavaBase) {
                    throw new InternalError("Module java.base is already defined");
                }
            }

            return;
        }

        ClassLoader loader = module.getClassLoader();
        if (Objects.nonNull(loader) && loader.getClass().getName().equals("jdk.internal.reflect.DelegatingClassLoader")) {
            throw new IllegalArgumentException("Class loader is an invalid delegating class loader");
        }

        boolean javaPkgDisallowed = !Objects.isNull(loader) && !Objects.equals(loader, ClassLoader.getPlatformClassLoader());
        for (Object pn : pns) {
            checkPackageNameForModule(pn, module.getName());
            if (javaPkgDisallowed && isPackageNameForbidden(pn.toString())) {
                throw new IllegalArgumentException("Class loader (instance of): " + loader.getClass().getName() +
                                " tried to define prohibited package name: " + pn);
            }
        }

        String definedPackage = null;
        boolean moduleAlreadyDefined;
        synchronized (moduleLock) {
            moduleAlreadyDefined = isModuleDefinedToLoader(loader, module.getName());
            if (!moduleAlreadyDefined) {
                List<String> definedPackages = getPackagesDefinedToLoader(loader);
                for (Object pn : pns) {
                    String pnString = pn.toString();
                    if (definedPackages.contains(pnString)) {
                        definedPackage = pnString;
                        break;
                    }
                }
            }
        }

        if (moduleAlreadyDefined) {
            throw new IllegalStateException("Module " + module.getName() + " is already defined");
        } else if (Objects.nonNull(definedPackage)) {
            Module moduleContainingDefinedPackage = SubstrateUtil.cast(getModuleContainingPackage(loader, definedPackage), Module.class);
            if (moduleContainingDefinedPackage.isNamed()) {
                throw new IllegalStateException("Package " + definedPackage + " is already in another module, " + moduleContainingDefinedPackage.getName() + ", defined to the class loader");
            } else {
                throw new IllegalStateException("Package " + definedPackage + " is already in the unnamed module defined to the class loader");
            }
        }

        synchronized (moduleLock) {
            addDefinedModule(loader, module);
        }
    }

    /**
     * {@code Modules::add_reads_module}.
     */
    public static void addReads(Module from, @SuppressWarnings("unused") Module to) {
        checkIsNull(from, FROM_MODULE_TAG);
    }

    /**
     * {@code Modules::add_module_exports_qualified}.
     */
    public static void addExports(Module from, String pn, Module to) {
        checkIsNull(to, TO_MODULE_TAG);
        addExportsToAll(from, pn);
    }

    /**
     * {@code Modules::add_module_exports}.
     */
    public static void addExportsToAll(Module from, String pn) {
        checkIsNull(pn, PACKAGE_TAG);
        checkIsNull(from, FROM_MODULE_TAG);
        checkIsPackageContainedInModule(pn, from, FROM_MODULE_TAG);
    }

    /**
     * {@code Modules::add_module_exports_to_all_unnamed}.
     */
    public static void addExportsToAllUnnamed(Module module, String pn) {
        checkIsNull(module, MODULE_TAG);
        checkIsNull(pn, PACKAGE_TAG);
        checkIsPackageContainedInModule(pn, module, MODULE_TAG);
    }

    /**
     * Module bookkeeping and utility methods used by substitutions.
     */

    private static final String PACKAGE_TAG = "module";
    private static final String MODULE_TAG = "module";
    private static final String FROM_MODULE_TAG = "from_" + MODULE_TAG;
    private static final String TO_MODULE_TAG = "to_" + MODULE_TAG;
    private static final Object moduleLock = new Object();
    private static final Map<ClassLoader, Set<Module>> definedModules = new HashMap<>();

    private static Map<ClassLoader, Set<Module>> getDefinedModules() {
        if (definedModules.isEmpty()) {
            for (Module module : ModuleLayer.boot().modules()) {
                Set<Module> modules = definedModules.get(module.getClassLoader());
                if (Objects.isNull(modules)) {
                    modules = new HashSet<>();
                    modules.add(module);
                    definedModules.put(module.getClassLoader(), modules);
                } else {
                    modules.add(module);
                }
            }
        }
        return definedModules;
    }

    private static void checkIsNull(Object o, String tag) {
        if (Objects.isNull(o)) {
            throw new NullPointerException(tag + " is null");
        }
    }

    private static boolean isPackageNameForbidden(String pn) {
        if (!pn.startsWith("java")) {
            return false;
        }
        char trailingChar = pn.length() < 5 ? '.' : pn.charAt("java".length());
        return trailingChar == '.';
    }

    private static void checkPackageNameForModule(Object pn, String module) {
        if (Objects.isNull(pn) || !(pn instanceof String pnString)) {
            throw new IllegalArgumentException("Bad package name");
        }

        // It is OK to use SourceVersion.isName here even though it calls String.split()
        // because pattern "\\." will take the fast path in the String.split() method
        if (!SourceVersion.isName(pnString)) {
            throw new IllegalArgumentException("Invalid package name: " + pnString + " for module: " + module);
        }
    }

    private static boolean isModuleDefinedToLoader(ClassLoader loader, String moduleName) {
        return getDefinedModules().getOrDefault(loader, Set.of()).stream().anyMatch(m -> m.getName().equals(moduleName));
    }

    private static void addDefinedModule(ClassLoader loader, Module module) {
        Set<Module> modules = getDefinedModules().get(loader);
        if (Objects.isNull(modules)) {
            modules = new HashSet<>();
            modules.add(module);
            getDefinedModules().put(loader, modules);
        } else {
            modules.add(module);
        }
    }

    private static void checkIsPackageContainedInModule(String pn, Module module, String tag) {
        if (!module.isNamed() || module.getDescriptor().isOpen()) {
            return;
        }
        if (!module.getPackages().contains(pn)) {
            throw new IllegalArgumentException("Package " + pn + " not found in " + tag + " " + module.getName());
        }
    }

    private static List<String> getPackagesDefinedToLoader(ClassLoader loader) {
        return getDefinedModules().getOrDefault(loader, Set.of())
                        .stream()
                        .flatMap(m -> m.getPackages().stream())
                        .toList();
    }

    private static Object getModuleContainingPackage(ClassLoader loader, String pn) {
        return getDefinedModules().getOrDefault(loader, Set.of())
                        .stream()
                        .filter(m -> m.getPackages().contains(pn))
                        .findFirst().orElse(null);
    }

    public static boolean bootLayerContainsModule(String name) {
        return ModuleLayer.boot().modules().stream().anyMatch(m -> m.getName().equals(name));
    }

}
