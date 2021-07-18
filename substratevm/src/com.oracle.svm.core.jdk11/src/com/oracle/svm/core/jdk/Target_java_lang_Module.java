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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;

import javax.lang.model.SourceVersion;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@TargetClass(className = "java.lang.Module", onlyWith = JDK11OrLater.class)
public final class Target_java_lang_Module {

    @SuppressWarnings("static-method")
    @Substitute
    public InputStream getResourceAsStream(String name) {
        ResourceStorageEntry res = Resources.get(name);
        return res == null ? null : new ByteArrayInputStream(res.getData().get(0));
    }

    //Checkstyle: allow synchronization
    @Substitute
    private static void defineModule0(Module module, boolean isOpen, String version, String location, String[] pns) {
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

            synchronized (ModuleUtil.moduleLock) {
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
        synchronized (ModuleUtil.moduleLock) {
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
            Module moduleContainingDefinedPackage = ModuleUtil.getModuleContainingPackage(loader, definedPackage);
            if (moduleContainingDefinedPackage.isNamed()) {
                throw new IllegalStateException("Package " + definedPackage + " is already in another module, "
                        + moduleContainingDefinedPackage.getName() + ", defined to the class loader");
            } else {
                throw new IllegalStateException("Package " + definedPackage
                        + " is already in the unnamed module defined to the class loader");
            }
        }

        synchronized (ModuleUtil.moduleLock) {
            ModuleUtil.addDefinedModule(loader, module);
        }
    }

    @Substitute
    private static void addReads0(Module from, Module to) {
        if (Objects.isNull(from)) {
            throw new NullPointerException("from_module is null");
        }
    }

    @Substitute
    private static void addExports0(Module from, String pn, Module to) {
        if (Objects.isNull(to)) {
            throw new NullPointerException("to_module is null");
        }

        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        ModuleUtil.checkIsPackageContainedInModule(pn, from);
    }

    @Substitute
    private static void addExportsToAll0(Module from, String pn) {
        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        ModuleUtil.checkIsPackageContainedInModule(pn, from);
    }

    @Substitute
    private static void addExportsToAllUnnamed0(Module from, String pn) {
        ModuleUtil.checkFromModuleAndPackageNullability(from, pn);
        if (from.isNamed()) {
            ModuleUtil.checkIsPackageContainedInModule(pn, from);
        }
    }

    @TargetClass(className = "java.lang.Module", innerClass = "ReflectionData", onlyWith = JDK11OrLater.class) //
    private static final class Target_java_lang_Module_ReflectionData {
        @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.lang.WeakPairMap") //
        static Target_java_lang_WeakPairMap<Module, Class<?>, Boolean> uses;
    }
}

class ModuleUtil {

    static final Object moduleLock = new Object();
    static final Map<ClassLoader, Set<Module>> definedModules = new HashMap<>();

    static {
        for (Module module : ModuleLayer.boot().modules()) {
            addDefinedModule(module.getClassLoader(), module);
        }
    }

    static void checkFromModuleAndPackageNullability(Module from, String pn) {
        if (Objects.isNull(from)) {
            throw new NullPointerException("from_module is null");
        }

        if (Objects.isNull(pn)) {
            throw new NullPointerException("package is null");
        }
    }

    static void checkIsPackageContainedInModule(String pn, Module module) {
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

    static boolean isPackageNameForbidden(String pn) {
        if (!pn.startsWith("java")) {
            return false;
        }
        char trailingChar = pn.length() < 5 ? '.' : pn.charAt("java".length());
        return trailingChar == '.';
    }

    static boolean isValidPackageName(String pn) {
        // It is OK to use SourceVersion.isName here even though it calls String.split()
        // because pattern "\\." will take the fast path in the String.split() method
        return Objects.nonNull(pn) && SourceVersion.isName(pn);
    }

    static boolean isModuleDefinedToLoader(ClassLoader loader, String moduleName) {
        return definedModules.getOrDefault(loader, Set.of()).stream().anyMatch(m -> m.getName().equals(moduleName));
    }

    static void addDefinedModule(ClassLoader loader, Module module) {
        Set<Module> modules = definedModules.get(loader);
        if (Objects.isNull(modules)) {
            modules = new HashSet<>();
            modules.add(module);
            definedModules.put(loader, modules);
        } else {
            modules.add(module);
        }
    }

    static List<String> getPackagesDefinedToLoader(ClassLoader loader) {
        return definedModules.getOrDefault(loader, Set.of())
                .stream()
                .flatMap(m -> m.getPackages().stream())
                .collect(Collectors.toUnmodifiableList());
    }

    static Module getModuleContainingPackage(ClassLoader loader, String pn) {
        return definedModules.getOrDefault(loader, Set.of())
                .stream()
                .filter(m -> m.getPackages().contains(pn))
                .findFirst().orElse(null);
    }

    public static boolean bootLayerContainsModule(String name) {
        return ModuleLayer.boot().modules().stream().anyMatch(m -> m.getName().equals(name));
    }
}
