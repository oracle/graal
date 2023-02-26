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
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import com.oracle.svm.core.SubstrateUtil;

public final class ModuleUtil {
    private ModuleUtil() {
    }

    private static final Object moduleLock = new Object();
    private static final Map<ClassLoader, Set<Module>> definedModules = new HashMap<>();

    public static Map<ClassLoader, Set<Module>> getDefinedModules() {
        if (definedModules.size() == 0) {
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

    public static void checkFromModuleAndPackageNullability(Module from, String pn) {
        if (Objects.isNull(from)) {
            throw new NullPointerException("from_module is null");
        }

        if (Objects.isNull(pn)) {
            throw new NullPointerException("package is null");
        }
    }

    public static boolean isPackageNameForbidden(String pn) {
        if (!pn.startsWith("java")) {
            return false;
        }
        char trailingChar = pn.length() < 5 ? '.' : pn.charAt("java".length());
        return trailingChar == '.';
    }

    public static boolean isValidPackageName(String pn) {
        // It is OK to use SourceVersion.isName here even though it calls String.split()
        // because pattern "\\." will take the fast path in the String.split() method
        return Objects.nonNull(pn) && SourceVersion.isName(pn);
    }

    public static boolean isModuleDefinedToLoader(ClassLoader loader, String moduleName) {
        return getDefinedModules().getOrDefault(loader, Set.of()).stream().anyMatch(m -> m.getName().equals(moduleName));
    }

    public static void addDefinedModule(ClassLoader loader, Module module) {
        Set<Module> modules = getDefinedModules().get(loader);
        if (Objects.isNull(modules)) {
            modules = new HashSet<>();
            modules.add(module);
            getDefinedModules().put(loader, modules);
        } else {
            modules.add(module);
        }
    }

    public static void checkIsPackageContainedInModule(String pn, Module module) {
        ClassLoader loader = module.getClassLoader() == null ? ClassLoader.getPlatformClassLoader() : module.getClassLoader();
        Package definedPackage = loader.getDefinedPackage(pn);
        if (definedPackage != null) {
            Target_java_lang_NamedPackage namedPackage = SubstrateUtil.cast(definedPackage, Target_java_lang_NamedPackage.class);
            Module actualModule = namedPackage.module;
            if (!actualModule.equals(module)) {
                throw new IllegalArgumentException("Package " + pn + " found in module " + actualModule.getName() +
                                ", not in module: " + module.getName());
            }
        }
        if (!module.getPackages().contains(pn)) {
            throw new IllegalArgumentException("Package " + pn + " not found in from_module " + module.getName());
        }
    }

    public static List<String> getPackagesDefinedToLoader(ClassLoader loader) {
        return getDefinedModules().getOrDefault(loader, Set.of())
                        .stream()
                        .flatMap(m -> m.getPackages().stream())
                        .collect(Collectors.toUnmodifiableList());
    }

    public static Object getModuleContainingPackage(ClassLoader loader, String pn) {
        return getDefinedModules().getOrDefault(loader, Set.of())
                        .stream()
                        .filter(m -> m.getPackages().contains(pn))
                        .findFirst().orElse(null);
    }

    public static boolean bootLayerContainsModule(String name) {
        return ModuleLayer.boot().modules().stream().anyMatch(m -> m.getName().equals(name));
    }

    public static void defineModule(Module module, boolean isOpen, List<String> pns) {
        if (Objects.isNull(module)) {
            throw new NullPointerException("Null module object");
        }

        if (Objects.isNull(module.getName())) {
            throw new IllegalArgumentException("Module name cannot be null");
        }

        if (module.getName().equals("java.base")) {
            if (isOpen) {
                throw new AssertionError("java.base module cannot be open");
            }

            for (String pn : pns) {
                if (!ModuleUtil.isValidPackageName(pn)) {
                    throw new IllegalArgumentException("Invalid package name: " + pn + " for module: java.base");
                }
            }

            if (module.getClassLoader() != null) {
                throw new IllegalArgumentException("Class loader must be the boot class loader");
            }

            synchronized (moduleLock) {
                boolean duplicateJavaBase = ModuleUtil.bootLayerContainsModule("java.base");
                if (duplicateJavaBase) {
                    throw new InternalError("Module java.base is already defined");
                }
            }

            return;
        }

        ClassLoader loader = module.getClassLoader();
        if (Objects.isNull(loader) || loader.getClass().getName().equals("jdk.internal.reflect.DelegatingClassLoader")) {
            throw new IllegalArgumentException("Class loader is an invalid delegating class loader");
        }

        for (String pn : pns) {
            if (!ModuleUtil.isValidPackageName(pn)) {
                throw new IllegalArgumentException("Invalid package name: " + pn + " for module: " + module.getName());
            }

            if (loader != ClassLoader.getPlatformClassLoader() && ModuleUtil.isPackageNameForbidden(pn)) {
                throw new IllegalArgumentException("Class loader (instance of): " + loader.getClass().getName() +
                                " tried to define prohibited package name: " + pn);
            }
        }

        String definedPackage = null;
        boolean moduleAlreadyDefined;
        synchronized (moduleLock) {
            moduleAlreadyDefined = ModuleUtil.isModuleDefinedToLoader(loader, module.getName());
            if (!moduleAlreadyDefined) {
                List<String> definedPackages = ModuleUtil.getPackagesDefinedToLoader(loader);
                for (String pn : pns) {
                    if (definedPackages.contains(pn)) {
                        definedPackage = pn;
                        break;
                    }
                }
            }
        }

        if (moduleAlreadyDefined) {
            throw new IllegalStateException("Module " + module.getName() + " is already defined");
        } else if (Objects.nonNull(definedPackage)) {
            Module moduleContainingDefinedPackage = SubstrateUtil.cast(ModuleUtil.getModuleContainingPackage(loader, definedPackage), Module.class);
            if (moduleContainingDefinedPackage.isNamed()) {
                throw new IllegalStateException("Package " + definedPackage + " is already in another module, " + moduleContainingDefinedPackage.getName() + ", defined to the class loader");
            } else {
                throw new IllegalStateException("Package " + definedPackage + " is already in the unnamed module defined to the class loader");
            }
        }

        synchronized (moduleLock) {
            ModuleUtil.addDefinedModule(loader, module);
        }
    }
}
