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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders.
 */
public class JfrTypeRepository implements JfrRepository {
    private final Set<Class<?>> flushedClasses = new HashSet<>();
    private final Map<String, PackageInfo> flushedPackages = new HashMap<>();
    private final Map<Module, Long> flushedModules = new HashMap<>();
    private final Map<ClassLoader, Long> flushedClassLoaders = new HashMap<>();
    private long currentPackageId = 0;
    private long currentModuleId = 0;
    private long currentClassLoaderId = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository() {
    }

    public void teardown() {
        clearEpochData();
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    public long getClassId(Class<?> clazz) {
        return JfrTraceId.load(clazz);
    }

    @Override
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        TypeInfo typeInfo = collectTypeInfo(flushpoint);
        int count = writeClasses(writer, typeInfo, flushpoint);
        count += writePackages(writer, typeInfo, flushpoint);
        count += writeModules(writer, typeInfo, flushpoint);
        count += writeClassLoaders(writer, typeInfo, flushpoint);

        if (flushpoint) {
            flushedClasses.addAll(typeInfo.classes);
            flushedPackages.putAll(typeInfo.packages);
            flushedModules.putAll(typeInfo.modules);
            flushedClassLoaders.putAll(typeInfo.classLoaders);
        } else {
            clearEpochData();
        }

        return count;
    }

    /**
     * Visit all used classes, and collect their packages, modules, classloaders and possibly
     * referenced classes.
     */
    private TypeInfo collectTypeInfo(boolean flushpoint) {
        TypeInfo typeInfo = new TypeInfo();
        Heap.getHeap().visitLoadedClasses((clazz) -> {
            if (flushpoint) {
                if (JfrTraceId.isUsedCurrentEpoch(clazz)) {
                    visitClass(typeInfo, clazz);
                }
            } else {
                if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                    JfrTraceId.clearUsedPreviousEpoch(clazz);
                    visitClass(typeInfo, clazz);
                }
            }
        });
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

    private int writeClasses(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        if (typeInfo.classes.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(typeInfo.classes.size());

        for (Class<?> clazz : typeInfo.classes) {
            writeClass(typeInfo, writer, clazz, flushpoint);
        }
        return NON_EMPTY;
    }

    private void writeClass(TypeInfo typeInfo, JfrChunkWriter writer, Class<?> clazz, boolean flushpoint) {
        writer.writeCompressedLong(JfrTraceId.getTraceId(clazz));
        writer.writeCompressedLong(getClassLoaderId(typeInfo, clazz.getClassLoader()));
        writer.writeCompressedLong(getSymbolId(writer, clazz.getName(), flushpoint, true));
        writer.writeCompressedLong(getPackageId(typeInfo, clazz.getPackage()));
        writer.writeCompressedLong(clazz.getModifiers());
        writer.writeBoolean(clazz.isHidden());
    }

    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private static long getSymbolId(JfrChunkWriter writer, String symbol, boolean flushpoint, boolean replaceDotWithSlash) {
        /*
         * The result is only valid for the current epoch, but the epoch can't change while the
         * current thread holds the JfrChunkWriter lock.
         */
        assert writer.isLockedByCurrentThread();
        return SubstrateJVM.getSymbolRepository().getSymbolId(symbol, !flushpoint, replaceDotWithSlash);
    }

    private int writePackages(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        if (typeInfo.packages.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(typeInfo.packages.size());

        for (Map.Entry<String, PackageInfo> pkgInfo : typeInfo.packages.entrySet()) {
            writePackage(typeInfo, writer, pkgInfo.getKey(), pkgInfo.getValue(), flushpoint);
        }
        return NON_EMPTY;
    }

    private void writePackage(TypeInfo typeInfo, JfrChunkWriter writer, String pkgName, PackageInfo pkgInfo, boolean flushpoint) {
        writer.writeCompressedLong(pkgInfo.id);  // id
        writer.writeCompressedLong(getSymbolId(writer, pkgName, flushpoint, true));
        writer.writeCompressedLong(getModuleId(typeInfo, pkgInfo.module));
        writer.writeBoolean(false); // exported
    }

    private int writeModules(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        if (typeInfo.modules.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(typeInfo.modules.size());

        for (Map.Entry<Module, Long> modInfo : typeInfo.modules.entrySet()) {
            writeModule(typeInfo, writer, modInfo.getKey(), modInfo.getValue(), flushpoint);
        }
        return NON_EMPTY;
    }

    private void writeModule(TypeInfo typeInfo, JfrChunkWriter writer, Module module, long id, boolean flushpoint) {
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(getSymbolId(writer, module.getName(), flushpoint, false));
        writer.writeCompressedLong(0); // Version, e.g. "11.0.10-internal"
        writer.writeCompressedLong(0); // Location, e.g. "jrt:/java.base"
        writer.writeCompressedLong(getClassLoaderId(typeInfo, module.getClassLoader()));
    }

    private static int writeClassLoaders(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        if (typeInfo.classLoaders.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(typeInfo.classLoaders.size());

        for (Map.Entry<ClassLoader, Long> clInfo : typeInfo.classLoaders.entrySet()) {
            writeClassLoader(writer, clInfo.getKey(), clInfo.getValue(), flushpoint);
        }
        return NON_EMPTY;
    }

    private static void writeClassLoader(JfrChunkWriter writer, ClassLoader cl, long id, boolean flushpoint) {
        writer.writeCompressedLong(id);
        if (cl == null) {
            writer.writeCompressedLong(0);
            writer.writeCompressedLong(getSymbolId(writer, "bootstrap", flushpoint, false));
        } else {
            writer.writeCompressedLong(JfrTraceId.getTraceId(cl.getClass()));
            writer.writeCompressedLong(getSymbolId(writer, cl.getName(), flushpoint, false));
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
        return typeInfo.classes.contains(clazz) || flushedClasses.contains(clazz);
    }

    private boolean addPackage(TypeInfo typeInfo, Package pkg, Module module) {
        if (isPackageVisited(typeInfo, pkg)) {
            assert module == (flushedPackages.containsKey(pkg.getName()) ? flushedPackages.get(pkg.getName()).module : typeInfo.packages.get(pkg.getName()).module);
            return false;
        }
        // The empty package represented by "" is always traced with id 0
        long id = pkg.getName().isEmpty() ? 0 : ++currentPackageId;
        typeInfo.packages.put(pkg.getName(), new PackageInfo(id, module));
        return true;
    }

    private boolean isPackageVisited(TypeInfo typeInfo, Package pkg) {
        return flushedPackages.containsKey(pkg.getName()) || typeInfo.packages.containsKey(pkg.getName());
    }

    private long getPackageId(TypeInfo typeInfo, Package pkg) {
        if (pkg != null) {
            if (flushedPackages.containsKey(pkg.getName())) {
                return flushedPackages.get(pkg.getName()).id;
            }
            return typeInfo.packages.get(pkg.getName()).id;
        } else {
            return 0;
        }
    }

    private boolean addModule(TypeInfo typeInfo, Module module) {
        if (isModuleVisited(typeInfo, module)) {
            return false;
        }
        typeInfo.modules.put(module, ++currentModuleId);
        return true;
    }

    private boolean isModuleVisited(TypeInfo typeInfo, Module module) {
        return typeInfo.modules.containsKey(module) || flushedModules.containsKey(module);
    }

    private long getModuleId(TypeInfo typeInfo, Module module) {
        if (module != null) {
            if (flushedModules.containsKey(module)) {
                return flushedModules.get(module);
            }
            return typeInfo.modules.get(module);
        } else {
            return 0;
        }
    }

    private boolean addClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        if (isClassLoaderVisited(typeInfo, classLoader)) {
            return false;
        }
        typeInfo.classLoaders.put(classLoader, ++currentClassLoaderId);
        return true;
    }

    private boolean isClassLoaderVisited(TypeInfo typeInfo, ClassLoader classLoader) {
        return flushedClassLoaders.containsKey(classLoader) || typeInfo.classLoaders.containsKey(classLoader);
    }

    private long getClassLoaderId(TypeInfo typeInfo, ClassLoader classLoader) {
        if (classLoader != null) {
            if (flushedClassLoaders.containsKey(classLoader)) {
                return flushedClassLoaders.get(classLoader);
            }
            return typeInfo.classLoaders.get(classLoader);
        } else {
            return 0;
        }
    }

    private void clearEpochData() {
        flushedClasses.clear();
        flushedPackages.clear();
        flushedModules.clear();
        flushedClassLoaders.clear();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
    }

    private static class TypeInfo {
        final Set<Class<?>> classes = new HashSet<>();
        final Map<String, PackageInfo> packages = new HashMap<>();
        final Map<Module, Long> modules = new HashMap<>();
        final Map<ClassLoader, Long> classLoaders = new HashMap<>();
    }
}
