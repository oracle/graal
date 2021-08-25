/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.HiddenClassSupport;
import com.oracle.svm.jfr.traceid.JfrTraceId;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders.
 */
public class JfrTypeRepository implements JfrConstantPool {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getClassId(Class<?> clazz) {
        return JfrTraceId.load(clazz);
    }

    @Override
    public int write(JfrChunkWriter writer) {
        // Visit all used classes, and collect their packages, modules, classloaders and possibly
        // referenced classes.
        TypeInfo typeInfo = collectTypeInfo();

        // The order of writing matters as following types can be tagged during the write process
        int count = writeClasses(writer, typeInfo);
        count += writePackages(writer, typeInfo);
        count += writeModules(writer, typeInfo);
        count += writeClassLoaders(writer, typeInfo);
        return count;
    }

    private TypeInfo collectTypeInfo() {
        TypeInfo typeInfo = new TypeInfo();
        for (Class<?> clazz : Heap.getHeap().getLoadedClasses()) {
            if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                JfrTraceId.clearUsedPreviousEpoch(clazz);
                visitClass(typeInfo, clazz);
            }
        }
        return typeInfo;
    }

    private void visitClass(TypeInfo typeInfo, Class<?> clazz) {
        if (clazz != null && typeInfo.addClass(clazz)) {
            visitPackage(typeInfo, clazz.getPackage(), clazz.getModule());
            visitClass(typeInfo, clazz.getSuperclass());
        }
    }

    private void visitPackage(TypeInfo typeInfo, Package pkg, Module module) {
        if (pkg != null && typeInfo.addPackage(pkg, module)) {
            visitModule(typeInfo, module);
        }
    }

    private void visitModule(TypeInfo typeInfo, Module module) {
        if (module != null && typeInfo.addModule(module)) {
            visitClassLoader(typeInfo, module.getClassLoader());
        }
    }

    private void visitClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        // The null class-loader is serialized as the "bootstrap" class-loader.
        if (classLoader != null && typeInfo.addClassLoader(classLoader)) {
            visitClass(typeInfo, classLoader.getClass());
        }
    }

    public int writeClasses(JfrChunkWriter writer, TypeInfo typeInfo) {
        if (typeInfo.getClasses().isEmpty()) {
            return 0;
        }
        writer.writeCompressedLong(JfrTypes.Class.getId());
        writer.writeCompressedInt(typeInfo.getClasses().size());

        for (Class<?> clazz : typeInfo.getClasses()) {
            writeClass(writer, typeInfo, clazz);
        }
        return 1;
    }

    private static void writeClass(JfrChunkWriter writer, TypeInfo typeInfo, Class<?> clazz) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(JfrTraceId.getTraceId(clazz));  // key
        writer.writeCompressedLong(typeInfo.getClassLoaderId(clazz.getClassLoader()));
        writer.writeCompressedLong(symbolRepo.getSymbolId(clazz.getName(), true, true));
        writer.writeCompressedLong(typeInfo.getPackageId(clazz.getPackage()));
        writer.writeCompressedLong(clazz.getModifiers());
        if (HiddenClassSupport.singleton().isHiddenClassSupported()) {
            writer.writeCompressedLong(HiddenClassSupport.singleton().isHidden(clazz) ? 1 : 0);
        }
    }

    private static int writePackages(JfrChunkWriter writer, TypeInfo typeInfo) {
        Map<String, PackageInfo> packages = typeInfo.getPackages();
        if (packages.isEmpty()) {
            return 0;
        }
        writer.writeCompressedLong(JfrTypes.Package.getId());
        writer.writeCompressedInt(packages.size());

        for (Map.Entry<String, PackageInfo> pkgInfo : packages.entrySet()) {
            writePackage(writer, typeInfo, pkgInfo.getKey(), pkgInfo.getValue());
        }
        return 1;
    }

    private static void writePackage(JfrChunkWriter writer, TypeInfo typeInfo, String pkgName, PackageInfo pkgInfo) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(pkgInfo.id);  // id
        writer.writeCompressedLong(symbolRepo.getSymbolId(pkgName, true, true));
        writer.writeCompressedLong(typeInfo.getModuleId(pkgInfo.module));
        writer.writeBoolean(false); // exported
    }

    private static int writeModules(JfrChunkWriter writer, TypeInfo typeInfo) {
        Map<Module, Long> modules = typeInfo.getModules();
        if (modules.isEmpty()) {
            return 0;
        }
        writer.writeCompressedLong(JfrTypes.Module.getId());
        writer.writeCompressedInt(modules.size());

        for (Map.Entry<Module, Long> modInfo : modules.entrySet()) {
            writeModule(writer, typeInfo, modInfo.getKey(), modInfo.getValue());
        }
        return 1;
    }

    private static void writeModule(JfrChunkWriter writer, TypeInfo typeInfo, Module module, long id) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(symbolRepo.getSymbolId(module.getName(), true));
        writer.writeCompressedLong(0); // Version, e.g. "11.0.10-internal"
        writer.writeCompressedLong(0); // Location, e.g. "jrt:/java.base"
        writer.writeCompressedLong(typeInfo.getClassLoaderId(module.getClassLoader()));
    }

    private static int writeClassLoaders(JfrChunkWriter writer, TypeInfo typeInfo) {
        Map<ClassLoader, Long> classLoaders = typeInfo.getClassLoaders();
        if (classLoaders.isEmpty()) {
            return 0;
        }
        writer.writeCompressedLong(JfrTypes.ClassLoader.getId());
        writer.writeCompressedInt(classLoaders.size());

        for (Map.Entry<ClassLoader, Long> clInfo : classLoaders.entrySet()) {
            writeClassLoader(writer, clInfo.getKey(), clInfo.getValue());
        }
        return 1;
    }

    private static void writeClassLoader(JfrChunkWriter writer, ClassLoader cl, long id) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(id);
        if (cl == null) {
            writer.writeCompressedLong(0);
            writer.writeCompressedLong(symbolRepo.getSymbolId("bootstrap", true));
        } else {
            writer.writeCompressedLong(JfrTraceId.getTraceId(cl.getClass()));
            writer.writeCompressedLong(symbolRepo.getSymbolId(cl.getName(), true));
        }
    }

    private static class PackageInfo {
        private final long id;
        private final Module module;

        PackageInfo(long id, Module module) {
            this.id = id;
            this.module = module;
        }
    }

    private static class TypeInfo {
        private final Set<Class<?>> classes = new HashSet<>();
        private final Map<String, PackageInfo> packages = new HashMap<>();
        private final Map<Module, Long> modules = new HashMap<>();
        private final Map<ClassLoader, Long> classLoaders = new HashMap<>();
        private long currentPackageId = 0;
        private long currentModuleId = 0;
        private long currentClassLoaderId = 0;

        boolean addClass(Class<?> clazz) {
            return classes.add(clazz);
        }

        Set<Class<?>> getClasses() {
            return classes;
        }

        boolean addPackage(Package pkg, Module module) {
            if (!packages.containsKey(pkg.getName())) {
                // The empty package represented by "" is always traced with id 0
                long id = pkg.getName().isEmpty() ? 0 : ++currentPackageId;
                packages.put(pkg.getName(), new PackageInfo(id, module));
                return true;
            } else {
                assert module == packages.get(pkg.getName()).module;
                return false;
            }
        }

        Map<String, PackageInfo> getPackages() {
            return packages;
        }

        long getPackageId(Package pkg) {
            if (pkg != null) {
                return packages.get(pkg.getName()).id;
            } else {
                return 0;
            }
        }

        boolean addModule(Module module) {
            if (!modules.containsKey(module)) {
                modules.put(module, ++currentModuleId);
                return true;
            } else {
                return false;
            }
        }

        Map<Module, Long> getModules() {
            return modules;
        }

        long getModuleId(Module module) {
            if (module != null) {
                return modules.get(module);
            } else {
                return 0;
            }
        }

        boolean addClassLoader(ClassLoader classLoader) {
            if (!classLoaders.containsKey(classLoader)) {
                classLoaders.put(classLoader, ++currentClassLoaderId);
                return true;
            } else {
                return false;
            }
        }

        Map<ClassLoader, Long> getClassLoaders() {
            return classLoaders;
        }

        long getClassLoaderId(ClassLoader classLoader) {
            if (classLoader != null) {
                return classLoaders.get(classLoader);
            } else {
                return 0;
            }
        }
    }
}
