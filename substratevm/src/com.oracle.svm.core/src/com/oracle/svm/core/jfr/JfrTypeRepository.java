/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders. Fields
 * that store state are only used during flushes and chunk rotations (only one thread will use them
 * at a time). This means that the maps in this repository will be entirely used and cleared with
 * respect to the current epoch before they are used for the subsequent epoch.
 *
 * The "epoch" maps hold records with respect to a specific epoch and are reset at an epoch change.
 */
public class JfrTypeRepository implements JfrConstantPool {
    private final Set<Class<?>> epochClasses = new HashSet<>();
    private final Map<String, PackageInfo> epochPackages = new HashMap<>();
    private final Map<Module, Long> epochModules = new HashMap<>();
    private final Map<ClassLoader, Long> epochClassLoaders = new HashMap<>();
    private long currentPackageId = 0;
    private long currentModuleId = 0;
    private long currentClassLoaderId = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getClassId(Class<?> clazz) {
        return JfrTraceId.load(clazz);
    }

    @Override
    public int write(JfrChunkWriter writer, boolean flush) {
        // Visit all used classes, and collect their packages, modules, classloaders and possibly
        // referenced classes.
        TypeInfo typeInfo = collectTypeInfo(flush);

        // The order of writing matters as following types can be tagged during the write process
        int count = writeClasses(typeInfo, writer, flush);
        count += writePackages(typeInfo, writer, flush);
        count += writeModules(typeInfo, writer, flush);
        count += writeClassLoaders(typeInfo, writer, flush);

        if (flush) {
            epochClasses.addAll(typeInfo.classes);
            epochPackages.putAll(typeInfo.packages);
            epochModules.putAll(typeInfo.modules);
            epochClassLoaders.putAll(typeInfo.classLoaders);
        } else {
            clearEpochChange();
        }

        return count;
    }

    private TypeInfo collectTypeInfo(boolean flush) {
        TypeInfo typeInfo = new TypeInfo();
        for (Class<?> clazz : Heap.getHeap().getLoadedClasses()) {
            if (flush) {
                if (JfrTraceId.isUsedCurrentEpoch(clazz)) {
                    visitClass(typeInfo, clazz);
                }
            } else if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                JfrTraceId.clearUsedPreviousEpoch(clazz);
                visitClass(typeInfo, clazz);
            }
        }
        return typeInfo;
    }

    private void visitClass(TypeInfo typeInfo, Class<?> clazz) {
        if (clazz != null && addClass(typeInfo, clazz)) {
            visitPackage(typeInfo, clazz.getPackage(), clazz.getModule());
            visitClass(typeInfo, clazz.getSuperclass());
        }
    }

    private void visitPackage(TypeInfo typeInfo, Package pkg, Module module) {
        if (pkg != null && addPackage(typeInfo, pkg, module)) {
            visitModule(typeInfo, module);
        }
    }

    private void visitModule(TypeInfo typeInfo, Module module) {
        if (module != null && addModule(typeInfo, module)) {
            visitClassLoader(typeInfo, module.getClassLoader());
        }
    }

    private void visitClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        // The null class-loader is serialized as the "bootstrap" class-loader.
        if (classLoader != null && addClassLoader(typeInfo, classLoader)) {
            visitClass(typeInfo, classLoader.getClass());
        }
    }

    private int writeClasses(TypeInfo typeInfo, JfrChunkWriter writer, boolean flush) {
        if (typeInfo.classes.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(typeInfo.classes.size());

        for (Class<?> clazz : typeInfo.classes) {
            writeClass(typeInfo, writer, clazz, flush);
        }
        return NON_EMPTY;
    }

    private void writeClass(TypeInfo typeInfo, JfrChunkWriter writer, Class<?> clazz, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(JfrTraceId.getTraceId(clazz));  // key
        writer.writeCompressedLong(getClassLoaderId(typeInfo, clazz.getClassLoader()));
        writer.writeCompressedLong(symbolRepo.getSymbolId(clazz.getName(), !flush, true));
        writer.writeCompressedLong(getPackageId(typeInfo, clazz.getPackage()));
        writer.writeCompressedLong(clazz.getModifiers());
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            writer.writeBoolean(SubstrateUtil.isHiddenClass(clazz));
        }
    }

    private int writePackages(TypeInfo typeInfo, JfrChunkWriter writer, boolean flush) {
        if (typeInfo.packages.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(typeInfo.packages.size());

        for (Map.Entry<String, PackageInfo> pkgInfo : typeInfo.packages.entrySet()) {
            writePackage(typeInfo, writer, pkgInfo.getKey(), pkgInfo.getValue(), flush);
        }
        return NON_EMPTY;
    }

    private void writePackage(TypeInfo typeInfo, JfrChunkWriter writer, String pkgName, PackageInfo pkgInfo, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(pkgInfo.id);  // id
        writer.writeCompressedLong(symbolRepo.getSymbolId(pkgName, !flush, true));
        writer.writeCompressedLong(getModuleId(typeInfo, pkgInfo.module));
        writer.writeBoolean(false); // exported
    }

    private int writeModules(TypeInfo typeInfo, JfrChunkWriter writer, boolean flush) {
        if (typeInfo.modules.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(typeInfo.modules.size());

        for (Map.Entry<Module, Long> modInfo : typeInfo.modules.entrySet()) {
            writeModule(typeInfo, writer, modInfo.getKey(), modInfo.getValue(), flush);
        }
        return NON_EMPTY;
    }

    private void writeModule(TypeInfo typeInfo, JfrChunkWriter writer, Module module, long id, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(symbolRepo.getSymbolId(module.getName(), !flush));
        writer.writeCompressedLong(0); // Version, e.g. "11.0.10-internal"
        writer.writeCompressedLong(0); // Location, e.g. "jrt:/java.base"
        writer.writeCompressedLong(getClassLoaderId(typeInfo, module.getClassLoader()));
    }

    private int writeClassLoaders(TypeInfo typeInfo, JfrChunkWriter writer, boolean flush) {
        if (typeInfo.classLoaders.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(typeInfo.classLoaders.size());

        for (Map.Entry<ClassLoader, Long> clInfo : typeInfo.classLoaders.entrySet()) {
            writeClassLoader(writer, clInfo.getKey(), clInfo.getValue(), flush);
        }
        return NON_EMPTY;
    }

    private void writeClassLoader(JfrChunkWriter writer, ClassLoader cl, long id, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(id);
        if (cl == null) {
            writer.writeCompressedLong(0);
            writer.writeCompressedLong(symbolRepo.getSymbolId("bootstrap", !flush));
        } else {
            writer.writeCompressedLong(JfrTraceId.getTraceId(cl.getClass()));
            writer.writeCompressedLong(symbolRepo.getSymbolId(cl.getName(), !flush));
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

    private boolean addClass(TypeInfo typeInfo, Class<?> clazz) {
        if (isClassVisited(typeInfo, clazz)) {
            return false;
        }
        return typeInfo.classes.add(clazz);
    }

    private boolean isClassVisited(TypeInfo typeInfo, Class<?> clazz) {
        return typeInfo.classes.contains(clazz) || epochClasses.contains(clazz);
    }

    private boolean addPackage(TypeInfo typeInfo, Package pkg, Module module) {
        if (!isPackageVisited(typeInfo, pkg)) {
            // The empty package represented by "" is always traced with id 0
            long id = pkg.getName().isEmpty() ? 0 : ++currentPackageId;
            typeInfo.packages.put(pkg.getName(), new PackageInfo(id, module));
            return true;
        } else {
            assert epochPackages.containsKey(pkg.getName()) ? module == epochPackages.get(pkg.getName()).module : module == typeInfo.packages.get(pkg.getName()).module;
            return false;
        }
    }

    private boolean isPackageVisited(TypeInfo typeInfo, Package pkg) {
        return epochPackages.containsKey(pkg.getName()) || typeInfo.packages.containsKey(pkg.getName());
    }

    private long getPackageId(TypeInfo typeInfo, Package pkg) {
        if (pkg != null) {
            if (epochPackages.containsKey(pkg.getName())) {
                return epochPackages.get(pkg.getName()).id;
            }
            return typeInfo.packages.get(pkg.getName()).id;
        } else {
            return 0;
        }
    }

    private boolean addModule(TypeInfo typeInfo, Module module) {
        if (!isModuleVisited(typeInfo, module)) {
            typeInfo.modules.put(module, ++currentModuleId);
            return true;
        } else {
            return false;
        }
    }

    private boolean isModuleVisited(TypeInfo typeInfo, Module module) {
        return typeInfo.modules.containsKey(module) || epochModules.containsKey(module);
    }

    private long getModuleId(TypeInfo typeInfo, Module module) {
        if (module != null) {
            if (epochModules.containsKey(module)) {
                return epochModules.get(module);
            }
            return typeInfo.modules.get(module);
        } else {
            return 0;
        }
    }

    private boolean addClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        if (!isClassLoaderVisited(typeInfo, classLoader)) {
            typeInfo.classLoaders.put(classLoader, ++currentClassLoaderId);
            return true;
        } else {
            return false;
        }
    }

    private boolean isClassLoaderVisited(TypeInfo typeInfo, ClassLoader classLoader) {
        return epochClassLoaders.containsKey(classLoader) || typeInfo.classLoaders.containsKey(classLoader);
    }

    private long getClassLoaderId(TypeInfo typeInfo, ClassLoader classLoader) {
        if (classLoader != null) {
            if (epochClassLoaders.containsKey(classLoader)) {
                return epochClassLoaders.get(classLoader);
            }
            return typeInfo.classLoaders.get(classLoader);
        } else {
            return 0;
        }
    }

    private void clearEpochChange() {
        epochClasses.clear();
        epochPackages.clear();
        epochModules.clear();
        epochClassLoaders.clear();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
    }

    private static class TypeInfo {
        public final Set<Class<?>> classes = new HashSet<>();
        public final Map<String, PackageInfo> packages = new HashMap<>();
        public final Map<Module, Long> modules = new HashMap<>();
        public final Map<ClassLoader, Long> classLoaders = new HashMap<>();
    }
}
