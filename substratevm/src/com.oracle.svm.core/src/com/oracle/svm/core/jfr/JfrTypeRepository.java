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

import com.oracle.svm.core.heap.VMOperationInfos;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders. Only one
 * thread should ever access this repository at a time. It is only used during flushes and chunk
 * rotations. This means that the maps in this repository will be entirely used and cleared with
 * respect to the current epoch before they are used for the subsequent epoch.
 *
 * The "old" maps hold records with respect to and entire epoch, while the "new" maps are with
 * respect to the current flush / chunk rotation.
 */
public class JfrTypeRepository implements JfrConstantPool {
    private static final Set<Class<?>> oldClasses = new HashSet<>();
    private static final Map<String, PackageInfo> oldPackages = new HashMap<>();
    private static final Map<Module, Long> oldModules = new HashMap<>();
    private static final Map<ClassLoader, Long> oldClassLoaders = new HashMap<>();
    private static final Set<Class<?>> newClasses = new HashSet<>();
    private static final Map<String, PackageInfo> newPackages = new HashMap<>();
    private static final Map<Module, Long> newModules = new HashMap<>();
    private static final Map<ClassLoader, Long> newClassLoaders = new HashMap<>();
    private static long currentPackageId = 0;
    private static long currentModuleId = 0;
    private static long currentClassLoaderId = 0;

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
        collectTypeInfo(flush);

        // The order of writing matters as following types can be tagged during the write process
        int count = writeClasses(writer, flush);
        count += writePackages(writer, flush);
        count += writeModules(writer, flush);
        count += writeClassLoaders(writer, flush);
        count += writeGCCauses(writer);
        count += writeGCNames(writer);
        count += writeVMOperations(writer);

        if (flush) {
            clearFlush();
        } else {
            clearEpochChange();
        }
        return count;
    }

    private void collectTypeInfo(boolean flush) {

        for (Class<?> clazz : Heap.getHeap().getLoadedClasses()) {
            if (flush) {
                if (JfrTraceId.isUsedCurrentEpoch(clazz)) {
                    visitClass(clazz);
                }
            } else if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                JfrTraceId.clearUsedPreviousEpoch(clazz);
                visitClass(clazz);
            }
        }
    }

    private static void visitClass(Class<?> clazz) {
        if (clazz != null && addClass(clazz)) {
            visitPackage(clazz.getPackage(), clazz.getModule());
            visitClass(clazz.getSuperclass());
        }
    }

    private static void visitPackage(Package pkg, Module module) {
        if (pkg != null && addPackage(pkg, module)) {
            visitModule(module);
        }
    }

    private static void visitModule(Module module) {
        if (module != null && addModule(module)) {
            visitClassLoader(module.getClassLoader());
        }
    }

    private static void visitClassLoader(ClassLoader classLoader) {
        // The null class-loader is serialized as the "bootstrap" class-loader.
        if (classLoader != null && addClassLoader(classLoader)) {
            visitClass(classLoader.getClass());
        }
    }

    public static int writeClasses(JfrChunkWriter writer, boolean flush) {
        if (newClasses.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(newClasses.size());

        for (Class<?> clazz : newClasses) {
            writeClass(writer, clazz, flush);
            oldClasses.add(clazz);
        }
        return NON_EMPTY;
    }

    private static void writeClass(JfrChunkWriter writer, Class<?> clazz, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(JfrTraceId.getTraceId(clazz));  // key
        writer.writeCompressedLong(getClassLoaderId(clazz.getClassLoader()));
        writer.writeCompressedLong(symbolRepo.getSymbolId(clazz.getName(), !flush, true));
        writer.writeCompressedLong(getPackageId(clazz.getPackage()));
        writer.writeCompressedLong(clazz.getModifiers());
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            writer.writeBoolean(SubstrateUtil.isHiddenClass(clazz));
        }
    }

    private static int writePackages(JfrChunkWriter writer, boolean flush) {
        if (newPackages.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(newPackages.size());

        for (Map.Entry<String, PackageInfo> pkgInfo : newPackages.entrySet()) {
            writePackage(writer, pkgInfo.getKey(), pkgInfo.getValue(), flush);
            oldPackages.put(pkgInfo.getKey(), pkgInfo.getValue());
        }
        return NON_EMPTY;
    }

    private static void writePackage(JfrChunkWriter writer, String pkgName, PackageInfo pkgInfo, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(pkgInfo.id);  // id
        writer.writeCompressedLong(symbolRepo.getSymbolId(pkgName, !flush, true));
        writer.writeCompressedLong(getModuleId(pkgInfo.module));
        writer.writeBoolean(false); // exported
    }

    private static int writeModules(JfrChunkWriter writer, boolean flush) {
        if (newModules.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(newModules.size());

        for (Map.Entry<Module, Long> modInfo : newModules.entrySet()) {
            writeModule(writer, modInfo.getKey(), modInfo.getValue(), flush);
            oldModules.put(modInfo.getKey(), modInfo.getValue());
        }
        return NON_EMPTY;
    }

    private static void writeModule(JfrChunkWriter writer, Module module, long id, boolean flush) {
        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(symbolRepo.getSymbolId(module.getName(), !flush));
        writer.writeCompressedLong(0); // Version, e.g. "11.0.10-internal"
        writer.writeCompressedLong(0); // Location, e.g. "jrt:/java.base"
        writer.writeCompressedLong(getClassLoaderId(module.getClassLoader()));
    }

    private static int writeClassLoaders(JfrChunkWriter writer, boolean flush) {
        if (newClassLoaders.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(newClassLoaders.size());

        for (Map.Entry<ClassLoader, Long> clInfo : newClassLoaders.entrySet()) {
            writeClassLoader(writer, clInfo.getKey(), clInfo.getValue(), flush);
            oldClassLoaders.put(clInfo.getKey(), clInfo.getValue());
        }
        return NON_EMPTY;
    }

    private static void writeClassLoader(JfrChunkWriter writer, ClassLoader cl, long id, boolean flush) {
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

    private static int writeGCCauses(JfrChunkWriter writer) {
        // GCCauses has null entries
        GCCause[] causes = GCCause.getGCCauses();
        int nonNullItems = 0;
        for (int index = 0; index < causes.length; index++) {
            if (causes[index] != null) {
                nonNullItems++;
            }
        }

        assert nonNullItems > 0;

        writer.writeCompressedLong(JfrType.GCCause.getId());
        writer.writeCompressedLong(nonNullItems);
        for (GCCause cause : causes) {
            if (cause != null) {
                writer.writeCompressedLong(cause.getId());
                writer.writeString(cause.getName());
            }
        }
        return NON_EMPTY;
    }

    private static int writeGCNames(JfrChunkWriter writer) {
        JfrGCName[] gcNames = JfrGCNames.singleton().getNames();
        assert gcNames != null && gcNames.length > 0;

        writer.writeCompressedLong(JfrType.GCName.getId());
        writer.writeCompressedLong(gcNames.length);
        for (JfrGCName name : gcNames) {
            writer.writeCompressedLong(name.getId());
            writer.writeString(name.getName());
        }
        return NON_EMPTY;
    }

    private static int writeVMOperations(JfrChunkWriter writer) {
        String[] vmOperationNames = VMOperationInfos.getNames();
        assert vmOperationNames.length > 0;
        writer.writeCompressedLong(JfrType.VMOperation.getId());
        writer.writeCompressedLong(vmOperationNames.length);
        for (int id = 0; id < vmOperationNames.length; id++) {
            writer.writeCompressedLong(id + 1); // id starts with 1
            writer.writeString(vmOperationNames[id]);
        }

        return NON_EMPTY;
    }

    private static class PackageInfo {
        private final long id;
        private final Module module;

        PackageInfo(long id, Module module) {
            this.id = id;
            this.module = module;
        }
    }

    static boolean addClass(Class<?> clazz) {
        if (isClassVisited(clazz)) {
            return false;
        }
        return newClasses.add(clazz);
    }

    static boolean isClassVisited(Class<?> clazz) {
        return newClasses.contains(clazz) || oldClasses.contains(clazz);
    }

    static boolean addPackage(Package pkg, Module module) {
        if (!isPackageVisited(pkg)) {
            // The empty package represented by "" is always traced with id 0
            long id = pkg.getName().isEmpty() ? 0 : ++currentPackageId;
            newPackages.put(pkg.getName(), new PackageInfo(id, module));
            return true;
        } else {
            assert oldPackages.containsKey(pkg.getName()) ? module == oldPackages.get(pkg.getName()).module : module == newPackages.get(pkg.getName()).module;
            return false;
        }
    }

    static boolean isPackageVisited(Package pkg) {
        return oldPackages.containsKey(pkg.getName()) || newPackages.containsKey(pkg.getName());
    }

    static long getPackageId(Package pkg) {
        if (pkg != null) {
            if (oldPackages.containsKey(pkg.getName())) {
                return oldPackages.get(pkg.getName()).id;
            }
            return newPackages.get(pkg.getName()).id;
        } else {
            return 0;
        }
    }

    static boolean addModule(Module module) {
        if (!isModuleVisited(module)) {
            newModules.put(module, ++currentModuleId);
            return true;
        } else {
            return false;
        }
    }

    static boolean isModuleVisited(Module module) {
        return newModules.containsKey(module) || oldModules.containsKey(module);
    }

    static long getModuleId(Module module) {
        if (module != null) {
            if (oldModules.containsKey(module)) {
                return oldModules.get(module);
            }
            return newModules.get(module);
        } else {
            return 0;
        }
    }

    static boolean addClassLoader(ClassLoader classLoader) {
        if (!isClassLoaderVisited(classLoader)) {
            newClassLoaders.put(classLoader, ++currentClassLoaderId);
            return true;
        } else {
            return false;
        }
    }

    static boolean isClassLoaderVisited(ClassLoader classLoader) {
        return oldClassLoaders.containsKey(classLoader) || newClassLoaders.containsKey(classLoader);
    }

    static long getClassLoaderId(ClassLoader classLoader) {
        if (classLoader != null) {
            if (oldClassLoaders.containsKey(classLoader)) {
                return oldClassLoaders.get(classLoader);
            }
            return newClassLoaders.get(classLoader);
        } else {
            return 0;
        }
    }

    private static void clearFlush() {
        newModules.clear();
        newPackages.clear();
        newClassLoaders.clear();
        newClasses.clear();
    }

    private static void clearEpochChange() {
        clearFlush();
        oldClasses.clear();
        oldPackages.clear();
        oldModules.clear();
        oldClassLoaders.clear();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
    }
}
