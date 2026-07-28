/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.jfr.traceid.JfrEpoch;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders.
 *
 * <p>
 * There are two separate paths:
 * <ul>
 * <li>Flush checkpoints use transient Java-side collections under the chunk writer lock.</li>
 * <li>Chunk close changes the epoch at a safepoint and prepares a native snapshot of the previous
 * epoch before the final checkpoint is written so that {@code write(false)} stays allocation-free
 * in the OOME path.</li>
 * </ul>
 */
public class JfrTypeRepository implements JfrRepository {
    private static final String BOOTSTRAP_NAME = "bootstrap";

    private final EconomicSet<Class<?>> flushedClasses;
    private final JfrPackageTable flushedPackages;
    private final Map<Module, Long> flushedModules;
    private final Map<ClassLoader, Long> flushedClassLoaders;
    private final PreviousEpochTypeSnapshot previousEpochSnapshot;

    private final JfrClassInfoTable epochTypeData0;
    private final JfrClassInfoTable epochTypeData1;
    private final VMMutex classIdMutex;

    private final UninterruptibleUtils.CharReplacer dotWithSlash;
    private long currentPackageId;
    private long currentModuleId;
    private long currentClassLoaderId;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository() {
        flushedClasses = EconomicSet.create();
        flushedPackages = new JfrPackageTable();
        flushedModules = new IdentityHashMap<>();
        flushedClassLoaders = new IdentityHashMap<>();
        previousEpochSnapshot = new PreviousEpochTypeSnapshot();

        epochTypeData0 = new JfrClassInfoTable();
        epochTypeData1 = new JfrClassInfoTable();
        classIdMutex = new VMMutex("jfrTypeRepositoryClassId");
        dotWithSlash = new ReplaceDotWithSlash();
    }

    public void teardown() {
        reset();
        previousEpochSnapshot.teardown();
        flushedPackages.teardown();
    }

    public void reset() {
        flushedClasses.clear();
        flushedModules.clear();
        flushedClassLoaders.clear();
        flushedPackages.clear();
        previousEpochSnapshot.reset();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
        epochTypeData0.clear();
        epochTypeData1.clear();
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrClassInfoTable getEpochData(boolean previousEpoch) {
        return getEpochData0(previousEpoch);
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.")
    private JfrClassInfoTable getEpochData0(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrEpoch.getInstance().previousEpoch() : JfrEpoch.getInstance().currentEpoch();
        return epoch ? epochTypeData0 : epochTypeData1;
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public long getClassId(Class<?> clazz) {
        long classId = JfrTraceId.getTraceId(clazz);
        ClassInfoRaw classInfoRaw = StackValue.get(ClassInfoRaw.class);
        classInfoRaw.setId(classId);
        classInfoRaw.setHash(getIdHash(classId));
        classInfoRaw.setClazz(clazz);

        classIdMutex.lockNoTransition();
        try {
            if (getEpochData(false).getOrPut(classInfoRaw).isNull()) {
                return 0L;
            }
        } finally {
            classIdMutex.unlock();
        }
        return JfrTraceId.load(clazz);
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Used on OOME for emergency dumps")
    public void preparePreviousEpochSnapshot() {
        previousEpochSnapshot.reset();
        buildPreviousEpochSnapshot();
    }

    @Override
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        if (flushpoint) {
            TypeInfo typeInfo = collectCurrentTypeInfo();
            int count = writeClasses(writer, typeInfo);
            count += writePackages(writer, typeInfo);
            count += writeModules(writer, typeInfo);
            count += writeClassLoaders(writer, typeInfo);

            if (count != 0) {
                flushedClasses.addAll(typeInfo.classes);
                flushedModules.putAll(typeInfo.modules);
                flushedClassLoaders.putAll(typeInfo.classLoaders);
                flushedPackages.putAll(typeInfo, this);
            }
            return count;
        }

        return writeAndClearPreviousEpoch(writer);
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Used on OOME for emergency dumps")
    int writeAndClearPreviousEpoch(JfrChunkWriter writer) {
        int count = writePreviousEpochSnapshot(writer);
        flushedClasses.clear();
        flushedModules.clear();
        flushedClassLoaders.clear();
        flushedPackages.clear();
        previousEpochSnapshot.reset();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
        getEpochData0(true).clear();
        return count;
    }

    private TypeInfo collectCurrentTypeInfo() {
        TypeInfo typeInfo = new TypeInfo();
        ClassInfoRaw[] table = (ClassInfoRaw[]) getEpochData0(false).getTable();
        for (int i = 0; i < table.length; i++) {
            ClassInfoRaw entry = table[i];
            while (entry.isNonNull()) {
                Class<?> clazz = entry.getClazz();
                assert DynamicHub.fromClass(clazz).isLoaded();
                if (JfrTraceId.isUsedCurrentEpoch(clazz)) {
                    visitClass(typeInfo, clazz);
                }
                entry = entry.getNext();
            }
        }
        return typeInfo;
    }

    private void buildPreviousEpochSnapshot() {
        ClassInfoRaw[] table = (ClassInfoRaw[]) getEpochData0(true).getTable();
        for (int i = 0; i < table.length; i++) {
            ClassInfoRaw entry = table[i];
            while (entry.isNonNull()) {
                Class<?> clazz = entry.getClazz();
                assert DynamicHub.fromClass(clazz).isLoaded();
                if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                    JfrTraceId.clearUsedPreviousEpoch(clazz);
                    visitClass(previousEpochSnapshot, clazz);
                }
                entry = entry.getNext();
            }
        }
    }

    private void visitClass(TypeInfo typeInfo, Class<?> clazz) {
        if (clazz != null && addClass(typeInfo, clazz)) {
            visitClassLoader(typeInfo, clazz.getClassLoader());
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
        if (addClassLoader(typeInfo, classLoader) && classLoader != null) {
            visitClass(typeInfo, classLoader.getClass());
        }
    }

    private void visitClass(PreviousEpochTypeSnapshot snapshot, Class<?> clazz) {
        if (clazz != null && addClass(snapshot, clazz)) {
            visitClassLoader(snapshot, clazz.getClassLoader());
            visitClass(snapshot, clazz.getSuperclass());
        }
    }

    private void visitClassLoader(PreviousEpochTypeSnapshot snapshot, ClassLoader classLoader) {
        if (getClassLoaderId(snapshot, classLoader) != 0L && classLoader != null) {
            visitClass(snapshot, classLoader.getClass());
        }
    }

    private int writeClasses(JfrChunkWriter writer, TypeInfo typeInfo) {
        if (typeInfo.classes.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(typeInfo.classes.size());
        for (Class<?> clazz : typeInfo.classes) {
            writeClass(typeInfo, writer, clazz);
        }
        return NON_EMPTY;
    }

    private void writeClass(TypeInfo typeInfo, JfrChunkWriter writer, Class<?> clazz) {
        writer.writeCompressedLong(JfrTraceId.getTraceId(clazz));
        writer.writeCompressedLong(getClassLoaderId(typeInfo, clazz.getClassLoader()));
        writer.writeCompressedLong(getSymbolId(writer, clazz.getName(), true));
        writer.writeCompressedLong(getPackageId(typeInfo, clazz));
        writer.writeCompressedLong(clazz.getModifiers());
        writer.writeBoolean(clazz.isHidden());
    }

    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private long getSymbolId(JfrChunkWriter writer, String symbol, boolean replaceDotWithSlash) {
        assert writer.isLockedByCurrentThread();
        return getSymbolId(symbol, false, replaceDotWithSlash);
    }

    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private long getSymbolId(String symbol, boolean previousEpoch, boolean replaceDotWithSlash) {
        if (symbol == null) {
            return 0L;
        }
        int encodedLength = UninterruptibleUtils.String.utf8Length(symbol, replaceDotWithSlash ? dotWithSlash : null);

        Pointer buffer = NullableNativeMemory.malloc(encodedLength, NmtCategory.JFR);
        if (buffer.isNull()) {
            return 0L;
        }
        UninterruptibleUtils.String.toUTF8(symbol, symbol.length(), buffer, buffer.add(encodedLength), replaceDotWithSlash ? dotWithSlash : null);
        return SubstrateJVM.getSymbolRepository().getSymbolId(buffer, Word.unsigned(encodedLength), previousEpoch);
    }

    @Uninterruptible(reason = "Needed for OOME-safe symbol serialization.")
    private static long getSymbolId(Pointer source, UnsignedWord length, boolean hasName, boolean previousEpoch) {
        if (!hasName) {
            return 0L;
        }

        Pointer destination = NullableNativeMemory.malloc(length, NmtCategory.JFR);
        if (destination.isNull()) {
            return 0L;
        }
        UnmanagedMemoryUtil.copy(source, destination, length);
        return SubstrateJVM.getSymbolRepository().getSymbolId(destination, length, previousEpoch);
    }

    private int writePackages(JfrChunkWriter writer, TypeInfo typeInfo) {
        if (typeInfo.packages.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(typeInfo.packages.size());
        for (Map.Entry<PackageKey, PackageInfo> pkgInfo : typeInfo.packages.entrySet()) {
            writePackage(typeInfo, writer, pkgInfo.getKey(), pkgInfo.getValue());
        }
        return NON_EMPTY;
    }

    private void writePackage(TypeInfo typeInfo, JfrChunkWriter writer, PackageKey pkgKey, PackageInfo pkgInfo) {
        writer.writeCompressedLong(pkgInfo.id);
        writer.writeCompressedLong(getSymbolId(writer, pkgKey.name, true));
        writer.writeCompressedLong(getModuleId(typeInfo, pkgKey.module));
        writer.writeBoolean(false); // exported
    }

    private int writeModules(JfrChunkWriter writer, TypeInfo typeInfo) {
        if (typeInfo.modules.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(typeInfo.modules.size());
        for (Map.Entry<Module, Long> modInfo : typeInfo.modules.entrySet()) {
            writeModule(typeInfo, writer, modInfo.getKey(), modInfo.getValue());
        }
        return NON_EMPTY;
    }

    private void writeModule(TypeInfo typeInfo, JfrChunkWriter writer, Module module, long id) {
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(getSymbolId(writer, module.getName(), false));
        writer.writeCompressedLong(0); // Version
        writer.writeCompressedLong(0); // Location
        writer.writeCompressedLong(getClassLoaderId(typeInfo, module.getClassLoader()));
    }

    private int writeClassLoaders(JfrChunkWriter writer, TypeInfo typeInfo) {
        if (typeInfo.classLoaders.isEmpty()) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(typeInfo.classLoaders.size());
        for (Map.Entry<ClassLoader, Long> clInfo : typeInfo.classLoaders.entrySet()) {
            writeClassLoader(writer, clInfo.getKey(), clInfo.getValue());
        }
        return NON_EMPTY;
    }

    private void writeClassLoader(JfrChunkWriter writer, ClassLoader cl, long id) {
        writer.writeCompressedLong(id);
        writer.writeCompressedLong(cl == null ? 0L : JfrTraceId.getTraceId(cl.getClass()));
        writer.writeCompressedLong(getSymbolId(writer, cl == null ? BOOTSTRAP_NAME : cl.getName(), false));
    }

    private int writePreviousEpochSnapshot(JfrChunkWriter writer) {
        int count = writePreviousEpochClasses(writer);
        count += writePreviousEpochPackages(writer);
        count += writePreviousEpochModules(writer);
        count += writePreviousEpochClassLoaders(writer);
        return count;
    }

    private int writePreviousEpochClasses(JfrChunkWriter writer) {
        if (previousEpochSnapshot.classes.getSize() == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(previousEpochSnapshot.classes.getSize());
        SnapshotClassEntry[] table = previousEpochSnapshot.classes.getTable();
        for (int i = 0; i < table.length; i++) {
            SnapshotClassEntry entry = table[i];
            while (entry.isNonNull()) {
                writer.writeCompressedLong(entry.getClassId());
                writer.writeCompressedLong(entry.getClassLoaderId());
                writer.writeCompressedLong(entry.getNameSymbolId());
                writer.writeCompressedLong(entry.getPackageId());
                writer.writeCompressedLong(entry.getModifiers());
                writer.writeBoolean(entry.getHidden());
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private int writePreviousEpochPackages(JfrChunkWriter writer) {
        if (previousEpochSnapshot.packages.getSize() == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(previousEpochSnapshot.packages.getSize());
        PackageEntry[] table = previousEpochSnapshot.packages.getTable();
        for (int i = 0; i < table.length; i++) {
            PackageEntry entry = table[i];
            while (entry.isNonNull()) {
                writer.writeCompressedLong(entry.getId());
                writer.writeCompressedLong(entry.getNameSymbolId());
                writer.writeCompressedLong(entry.getModuleId());
                writer.writeBoolean(false); // exported
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private int writePreviousEpochModules(JfrChunkWriter writer) {
        if (previousEpochSnapshot.modules.getSize() == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(previousEpochSnapshot.modules.getSize());
        SnapshotModuleEntry[] table = previousEpochSnapshot.modules.getTable();
        for (int i = 0; i < table.length; i++) {
            SnapshotModuleEntry entry = table[i];
            while (entry.isNonNull()) {
                writer.writeCompressedLong(entry.getId());
                writer.writeCompressedLong(entry.getNameSymbolId());
                writer.writeCompressedLong(0); // Version
                writer.writeCompressedLong(0); // Location
                writer.writeCompressedLong(entry.getClassLoaderId());
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private int writePreviousEpochClassLoaders(JfrChunkWriter writer) {
        int size = previousEpochSnapshot.classLoaders.getSize();
        if (!previousEpochSnapshot.hasBootstrapClassLoader && size == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(size + (previousEpochSnapshot.hasBootstrapClassLoader ? 1 : 0));
        if (previousEpochSnapshot.hasBootstrapClassLoader) {
            writer.writeCompressedLong(0L);
            writer.writeCompressedLong(0L);
            writer.writeCompressedLong(previousEpochSnapshot.bootstrapNameSymbolId);
        }
        SnapshotClassLoaderEntry[] table = previousEpochSnapshot.classLoaders.getTable();
        for (int i = 0; i < table.length; i++) {
            SnapshotClassLoaderEntry entry = table[i];
            while (entry.isNonNull()) {
                writer.writeCompressedLong(entry.getId());
                writer.writeCompressedLong(entry.getClassTraceId());
                writer.writeCompressedLong(entry.getNameSymbolId());
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private boolean addClass(TypeInfo typeInfo, Class<?> clazz) {
        if (typeInfo.classes.contains(clazz) || flushedClasses.contains(clazz)) {
            return false;
        }
        return typeInfo.classes.add(clazz);
    }

    private boolean addPackage(TypeInfo typeInfo, Package pkg, Module module) {
        PackageKey key = new PackageKey(pkg.getName(), module);
        if (typeInfo.packages.containsKey(key) || flushedPackagesContains(typeInfo, key)) {
            return false;
        }
        long id = key.name.isEmpty() ? 0 : ++currentPackageId;
        typeInfo.packages.put(key, new PackageInfo(id));
        return true;
    }

    private boolean flushedPackagesContains(TypeInfo typeInfo, PackageKey key) {
        if (key.name.isEmpty()) {
            return true;
        }

        long moduleId = getModuleId(typeInfo, key.module);
        PackageEntry packageEntry = StackValue.get(PackageEntry.class);
        if (!setUtf8Name(key.name, packageEntry, true)) {
            return false;
        }
        packageEntry.setModuleId(moduleId);
        packageEntry.setHash(getPackageHash(moduleId, packageEntry.getUtf8Name(), packageEntry.getNameLength()));
        boolean result = flushedPackages.contains(packageEntry);
        if (packageEntry.getUtf8Name().isNonNull()) {
            NullableNativeMemory.free(packageEntry.getUtf8Name());
        }
        return result;
    }

    private long getPackageId(TypeInfo typeInfo, Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null || pkg.getName().isEmpty()) {
            return 0;
        }

        PackageKey key = new PackageKey(pkg.getName(), clazz.getModule());
        PackageInfo info = typeInfo.packages.get(key);
        if (info != null) {
            return info.id;
        }

        long moduleId = getModuleId(typeInfo, key.module);
        PackageEntry packageEntry = StackValue.get(PackageEntry.class);
        if (!setUtf8Name(key.name, packageEntry, true)) {
            return 0;
        }
        packageEntry.setModuleId(moduleId);
        packageEntry.setHash(getPackageHash(moduleId, packageEntry.getUtf8Name(), packageEntry.getNameLength()));
        PackageEntry existing = (PackageEntry) flushedPackages.get(packageEntry);
        if (packageEntry.getUtf8Name().isNonNull()) {
            NullableNativeMemory.free(packageEntry.getUtf8Name());
        }
        return existing.isNonNull() ? existing.getId() : 0;
    }

    private boolean addModule(TypeInfo typeInfo, Module module) {
        if (typeInfo.modules.containsKey(module) || flushedModules.containsKey(module)) {
            return false;
        }
        typeInfo.modules.put(module, ++currentModuleId);
        return true;
    }

    private long getModuleId(TypeInfo typeInfo, Module module) {
        if (module == null) {
            return 0;
        }
        Long flushed = flushedModules.get(module);
        if (flushed != null) {
            return flushed;
        }
        Long current = typeInfo.modules.get(module);
        return current != null ? current : 0;
    }

    private boolean addClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        if (typeInfo.classLoaders.containsKey(classLoader) || flushedClassLoaders.containsKey(classLoader)) {
            return false;
        }
        typeInfo.classLoaders.put(classLoader, classLoader == null ? 0L : ++currentClassLoaderId);
        return true;
    }

    private long getClassLoaderId(TypeInfo typeInfo, ClassLoader classLoader) {
        if (classLoader == null) {
            return 0;
        }
        Long flushed = flushedClassLoaders.get(classLoader);
        if (flushed != null) {
            return flushed;
        }
        Long current = typeInfo.classLoaders.get(classLoader);
        return current != null ? current : 0;
    }

    private boolean addClass(PreviousEpochTypeSnapshot snapshot, Class<?> clazz) {
        long classId = JfrTraceId.getTraceId(clazz);
        SnapshotClassEntry classEntry = StackValue.get(SnapshotClassEntry.class);
        classEntry.setClassId(classId);
        classEntry.setHash(getIdHash(classId));
        if (snapshot.classes.contains(classEntry)) {
            return false;
        }

        classEntry.setClassLoaderId(getClassLoaderId(snapshot, clazz.getClassLoader()));
        classEntry.setNameSymbolId(getSymbolId(clazz.getName(), true, true));
        classEntry.setPackageId(getPackageId(snapshot, clazz));
        classEntry.setModifiers(clazz.getModifiers());
        classEntry.setHidden(clazz.isHidden());
        return snapshot.classes.putNew(classEntry).isNonNull();
    }

    private long getClassLoaderId(PreviousEpochTypeSnapshot snapshot, ClassLoader classLoader) {
        if (classLoader == null) {
            snapshot.markBootstrapClassLoader();
            return 0;
        }

        ObjectIdEntry objectIdEntry = StackValue.get(ObjectIdEntry.class);
        objectIdEntry.setObjectRawValue(Word.objectToUntrackedPointer(classLoader).rawValue());
        objectIdEntry.setHash(getObjectHash(classLoader));
        ObjectIdEntry existing = (ObjectIdEntry) snapshot.classLoaderIds.get(objectIdEntry);
        if (existing.isNonNull()) {
            ensureClassLoaderClassTraceId(snapshot, classLoader, existing.getId());
            return existing.getId();
        }

        SnapshotClassLoaderEntry classLoaderEntry = StackValue.get(SnapshotClassLoaderEntry.class);
        classLoaderEntry.setId(++currentClassLoaderId);
        classLoaderEntry.setClassTraceId(0L);
        classLoaderEntry.setNameSymbolId(getSymbolId(classLoader.getName(), true, false));
        classLoaderEntry.setHash(getIdHash(classLoaderEntry.getId()));
        if (snapshot.classLoaders.putNew(classLoaderEntry).isNull()) {
            currentClassLoaderId--;
            return 0;
        }

        objectIdEntry.setId(classLoaderEntry.getId());
        if (snapshot.classLoaderIds.putNew(objectIdEntry).isNull()) {
            snapshot.classLoaders.remove(classLoaderEntry);
            currentClassLoaderId--;
            return 0;
        }
        ensureClassLoaderClassTraceId(snapshot, classLoader, classLoaderEntry.getId());
        return classLoaderEntry.getId();
    }

    private void ensureClassLoaderClassTraceId(PreviousEpochTypeSnapshot snapshot, ClassLoader classLoader, long classLoaderId) {
        Class<?> classLoaderClass = classLoader.getClass();
        if (!canReferenceClass(snapshot, classLoaderClass)) {
            /*
             * The class loader entry may be created before its implementation class is added to the
             * previous-epoch class pool. Reserve the loader id first so recursive loader lookups
             * can see it, then materialize the class if possible. If that still fails under memory
             * pressure, leave the class reference empty rather than writing a dangling class id.
             */
            visitClass(snapshot, classLoaderClass);
        }

        SnapshotClassLoaderEntry entry = getSnapshotClassLoaderEntry(snapshot, classLoaderId);
        if (entry.isNonNull()) {
            entry.setClassTraceId(canReferenceClass(snapshot, classLoaderClass) ? JfrTraceId.getTraceId(classLoaderClass) : 0L);
        }
    }

    private static boolean canReferenceClass(PreviousEpochTypeSnapshot snapshot, Class<?> clazz) {
        return clazz != null && snapshotContainsClass(snapshot, clazz);
    }

    private static boolean snapshotContainsClass(PreviousEpochTypeSnapshot snapshot, Class<?> clazz) {
        long classId = JfrTraceId.getTraceId(clazz);
        SnapshotClassEntry classEntry = StackValue.get(SnapshotClassEntry.class);
        classEntry.setClassId(classId);
        classEntry.setHash(getIdHash(classId));
        return snapshot.classes.contains(classEntry);
    }

    private static SnapshotClassLoaderEntry getSnapshotClassLoaderEntry(PreviousEpochTypeSnapshot snapshot, long classLoaderId) {
        SnapshotClassLoaderEntry classLoaderEntry = StackValue.get(SnapshotClassLoaderEntry.class);
        classLoaderEntry.setId(classLoaderId);
        classLoaderEntry.setHash(getIdHash(classLoaderId));
        return (SnapshotClassLoaderEntry) snapshot.classLoaders.get(classLoaderEntry);
    }

    private long getModuleId(PreviousEpochTypeSnapshot snapshot, Module module) {
        if (module == null) {
            return 0;
        }

        ObjectIdEntry objectIdEntry = StackValue.get(ObjectIdEntry.class);
        objectIdEntry.setObjectRawValue(Word.objectToUntrackedPointer(module).rawValue());
        objectIdEntry.setHash(getObjectHash(module));
        ObjectIdEntry existing = (ObjectIdEntry) snapshot.moduleIds.get(objectIdEntry);
        if (existing.isNonNull()) {
            return existing.getId();
        }

        SnapshotModuleEntry moduleEntry = StackValue.get(SnapshotModuleEntry.class);
        moduleEntry.setId(++currentModuleId);
        moduleEntry.setClassLoaderId(getClassLoaderId(snapshot, module.getClassLoader()));
        moduleEntry.setNameSymbolId(getSymbolId(module.getName(), true, false));
        moduleEntry.setHash(getIdHash(moduleEntry.getId()));
        if (snapshot.modules.putNew(moduleEntry).isNull()) {
            currentModuleId--;
            return 0;
        }

        objectIdEntry.setId(moduleEntry.getId());
        if (snapshot.moduleIds.putNew(objectIdEntry).isNull()) {
            snapshot.modules.remove(moduleEntry);
            currentModuleId--;
            return 0;
        }
        return moduleEntry.getId();
    }

    /** We cannot call getPackage() or getPackageName() when building the OOME snapshot. */
    private long getPackageId(PreviousEpochTypeSnapshot snapshot, Class<?> clazz) {
        PackageEntry packageEntry = StackValue.get(PackageEntry.class);
        packageEntry.setModuleId(getModuleId(snapshot, clazz.getModule()));
        setPackageNameAndLength(clazz, packageEntry);
        if (packageEntry.getNameLength().equal(0)) {
            if (packageEntry.getUtf8Name().isNonNull()) {
                NullableNativeMemory.free(packageEntry.getUtf8Name());
            }
            return 0;
        }

        packageEntry.setId(0);
        packageEntry.setNameSymbolId(0L);
        packageEntry.setHash(getPackageHash(packageEntry.getModuleId(), packageEntry.getUtf8Name(), packageEntry.getNameLength()));

        PackageEntry flushed = (PackageEntry) flushedPackages.get(packageEntry);
        if (flushed.isNonNull()) {
            NullableNativeMemory.free(packageEntry.getUtf8Name());
            return flushed.getId();
        }

        PackageEntry existing = (PackageEntry) snapshot.packages.get(packageEntry);
        if (existing.isNonNull()) {
            NullableNativeMemory.free(packageEntry.getUtf8Name());
            return existing.getId();
        }

        packageEntry.setId(++currentPackageId);
        packageEntry.setNameSymbolId(getSymbolId(packageEntry.getUtf8Name(), packageEntry.getNameLength(), packageEntry.getHasName(), true));
        if (snapshot.packages.putNew(packageEntry).isNull()) {
            NullableNativeMemory.free(packageEntry.getUtf8Name());
            currentPackageId--;
            return 0;
        }
        return packageEntry.getId();
    }

    /**
     * This method sets the package name and length. The target may be stack or heap allocated.
     */
    private void setPackageNameAndLength(Class<?> clazz, PackageEntry target) {
        DynamicHub hub = DynamicHub.fromClass(clazz);
        if (!LayoutEncoding.isHybrid(hub.getLayoutEncoding())) {
            while (hub.hubIsArray()) {
                hub = hub.getComponentType();
            }
        }

        if (hub.isPrimitive()) {
            target.setHasName(false);
            target.setUtf8Name(Word.nullPointer());
            target.setNameLength(Word.unsigned(0));
            return;
        }

        String name = hub.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            dot = 0;
        }

        int encodedLength = UninterruptibleUtils.String.utf8Length(name, dot, dotWithSlash);
        if (encodedLength == 0) {
            target.setHasName(true);
            target.setUtf8Name(Word.nullPointer());
            target.setNameLength(Word.unsigned(0));
            return;
        }

        Pointer buffer = NullableNativeMemory.malloc(encodedLength, NmtCategory.JFR);
        if (buffer.isNull()) {
            target.setHasName(false);
            target.setUtf8Name(Word.nullPointer());
            target.setNameLength(Word.unsigned(0));
            return;
        }

        Pointer end = UninterruptibleUtils.String.toUTF8(name, dot, buffer, buffer.add(encodedLength), dotWithSlash);
        target.setHasName(true);
        target.setUtf8Name(buffer);
        target.setNameLength(end.subtract(buffer));
    }

    private boolean setUtf8Name(String string, NullableNameEntry target, boolean replaceDotWithSlash) {
        if (string == null) {
            target.setHasName(false);
            target.setUtf8Name(Word.nullPointer());
            target.setNameLength(Word.unsigned(0));
            return true;
        }

        int encodedLength = UninterruptibleUtils.String.utf8Length(string, replaceDotWithSlash ? dotWithSlash : null);
        target.setHasName(true);
        target.setNameLength(Word.unsigned(encodedLength));
        if (encodedLength == 0) {
            target.setUtf8Name(Word.nullPointer());
            return true;
        }

        Pointer buffer = NullableNativeMemory.malloc(encodedLength, NmtCategory.JFR);
        if (buffer.isNull()) {
            target.setHasName(false);
            target.setUtf8Name(Word.nullPointer());
            target.setNameLength(Word.unsigned(0));
            return false;
        }
        UninterruptibleUtils.String.toUTF8(string, string.length(), buffer, buffer.add(encodedLength), replaceDotWithSlash ? dotWithSlash : null);
        target.setUtf8Name(buffer);
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getIdHash(long value) {
        return UninterruptibleUtils.Long.hashCode(value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getObjectHash(Object object) {
        return object == null ? 0 : UninterruptibleUtils.Long.hashCode(Word.objectToUntrackedPointer(object).rawValue());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getPackageHash(long moduleId, Pointer buffer, UnsignedWord length) {
        long sum = 0;
        for (int i = 0; length.aboveThan(i); i++) {
            sum += buffer.readByte(i);
        }
        return 31 * UninterruptibleUtils.Long.hashCode(sum) + getIdHash(moduleId);
    }

    private static final class PackageKey {
        private final String name;
        private final Module module;

        PackageKey(String name, Module module) {
            this.name = name;
            this.module = module;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PackageKey other)) {
                return false;
            }
            return module == other.module && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + System.identityHashCode(module);
        }
    }

    private static final class PackageInfo {
        private final long id;

        PackageInfo(long id) {
            this.id = id;
        }
    }

    private static final class TypeInfo {
        final EconomicSet<Class<?>> classes = EconomicSet.create();
        final Map<PackageKey, PackageInfo> packages = new HashMap<>();
        final Map<Module, Long> modules = new IdentityHashMap<>();
        final Map<ClassLoader, Long> classLoaders = new IdentityHashMap<>();
    }

    private final class PreviousEpochTypeSnapshot {
        final SnapshotClassTable classes = new SnapshotClassTable();
        final JfrPackageTable packages = new JfrPackageTable();
        final SnapshotModuleTable modules = new SnapshotModuleTable();
        final SnapshotClassLoaderTable classLoaders = new SnapshotClassLoaderTable();
        final ObjectIdTable moduleIds = new ObjectIdTable();
        final ObjectIdTable classLoaderIds = new ObjectIdTable();
        boolean hasBootstrapClassLoader;
        long bootstrapNameSymbolId;

        void reset() {
            classes.clear();
            packages.clear();
            modules.clear();
            classLoaders.clear();
            moduleIds.clear();
            classLoaderIds.clear();
            hasBootstrapClassLoader = false;
            bootstrapNameSymbolId = 0L;
        }

        void teardown() {
            classes.teardown();
            packages.teardown();
            modules.teardown();
            classLoaders.teardown();
            moduleIds.teardown();
            classLoaderIds.teardown();
            hasBootstrapClassLoader = false;
            bootstrapNameSymbolId = 0L;
        }

        void markBootstrapClassLoader() {
            hasBootstrapClassLoader = true;
            if (bootstrapNameSymbolId == 0L) {
                bootstrapNameSymbolId = getSymbolId(BOOTSTRAP_NAME, true, false);
            }
        }
    }

    @RawStructure
    private interface ClassInfoRaw extends UninterruptibleEntry {
        @RawField
        void setId(long value);

        @RawField
        long getId();

        @PinnedObjectField
        @RawField
        void setClazz(Class<?> value);

        @PinnedObjectField
        @RawField
        Class<?> getClazz();
    }

    @RawStructure
    private interface SnapshotClassEntry extends UninterruptibleEntry {
        @RawField
        void setClassId(long value);

        @RawField
        long getClassId();

        @RawField
        void setClassLoaderId(long value);

        @RawField
        long getClassLoaderId();

        @RawField
        void setNameSymbolId(long value);

        @RawField
        long getNameSymbolId();

        @RawField
        void setPackageId(long value);

        @RawField
        long getPackageId();

        @RawField
        void setModifiers(int value);

        @RawField
        int getModifiers();

        @RawField
        void setHidden(boolean value);

        @RawField
        boolean getHidden();
    }

    @RawStructure
    private interface NullableNameEntry extends UninterruptibleEntry {
        @RawField
        void setHasName(boolean value);

        @RawField
        boolean getHasName();

        @RawField
        void setUtf8Name(Pointer value);

        @RawField
        Pointer getUtf8Name();

        @RawField
        void setNameLength(UnsignedWord value);

        @RawField
        UnsignedWord getNameLength();
    }

    @RawStructure
    private interface PackageEntry extends NullableNameEntry {
        @RawField
        void setId(long value);

        @RawField
        long getId();

        @RawField
        void setModuleId(long value);

        @RawField
        long getModuleId();

        @RawField
        void setNameSymbolId(long value);

        @RawField
        long getNameSymbolId();
    }

    @RawStructure
    private interface SnapshotModuleEntry extends UninterruptibleEntry {
        @RawField
        void setId(long value);

        @RawField
        long getId();

        @RawField
        void setClassLoaderId(long value);

        @RawField
        long getClassLoaderId();

        @RawField
        void setNameSymbolId(long value);

        @RawField
        long getNameSymbolId();
    }

    @RawStructure
    private interface SnapshotClassLoaderEntry extends UninterruptibleEntry {
        @RawField
        void setId(long value);

        @RawField
        long getId();

        @RawField
        void setClassTraceId(long value);

        @RawField
        long getClassTraceId();

        @RawField
        void setNameSymbolId(long value);

        @RawField
        long getNameSymbolId();
    }

    @RawStructure
    private interface ObjectIdEntry extends UninterruptibleEntry {
        @RawField
        void setObjectRawValue(long value);

        @RawField
        long getObjectRawValue();

        @RawField
        void setId(long value);

        @RawField
        long getId();
    }

    private abstract static class NullableNameTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        NullableNameTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        protected void free(UninterruptibleEntry entry) {
            NullableNameEntry nameEntry = (NullableNameEntry) entry;
            if (nameEntry.getUtf8Name().isNonNull()) {
                NullableNativeMemory.free(nameEntry.getUtf8Name());
                nameEntry.setUtf8Name(Word.nullPointer());
            }
            super.free(entry);
        }
    }

    private static final class JfrClassInfoTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        JfrClassInfoTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected ClassInfoRaw[] createTable(int size) {
            return new ClassInfoRaw[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            return ((ClassInfoRaw) v0).getId() == ((ClassInfoRaw) v1).getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(ClassInfoRaw.class));
        }
    }

    private static final class SnapshotClassTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        SnapshotClassTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected SnapshotClassEntry[] createTable(int size) {
            return new SnapshotClassEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            return ((SnapshotClassEntry) v0).getClassId() == ((SnapshotClassEntry) v1).getClassId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(SnapshotClassEntry.class));
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public SnapshotClassEntry[] getTable() {
            return (SnapshotClassEntry[]) super.getTable();
        }
    }

    private static final class JfrPackageTable extends NullableNameTable {
        @Platforms(Platform.HOSTED_ONLY.class)
        JfrPackageTable() {
            super();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected PackageEntry[] createTable(int size) {
            return new PackageEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            PackageEntry a = (PackageEntry) v0;
            PackageEntry b = (PackageEntry) v1;
            return a.getModuleId() == b.getModuleId() &&
                            a.getNameLength().equal(b.getNameLength()) &&
                            LibC.memcmp(a.getUtf8Name(), b.getUtf8Name(), a.getNameLength()) == 0;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(PackageEntry.class));
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public PackageEntry[] getTable() {
            return (PackageEntry[]) super.getTable();
        }

        void putAll(TypeInfo typeInfo, JfrTypeRepository repository) {
            for (Map.Entry<PackageKey, PackageInfo> entry : typeInfo.packages.entrySet()) {
                PackageEntry packageEntry = StackValue.get(PackageEntry.class);
                if (!repository.setUtf8Name(entry.getKey().name, packageEntry, true)) {
                    continue;
                }
                packageEntry.setModuleId(repository.getModuleId(typeInfo, entry.getKey().module));
                packageEntry.setId(entry.getValue().id);
                packageEntry.setNameSymbolId(0L);
                packageEntry.setHash(getPackageHash(packageEntry.getModuleId(), packageEntry.getUtf8Name(), packageEntry.getNameLength()));
                if (packageEntry.getId() != 0 && !contains(packageEntry) && putNew(packageEntry).isNull() && packageEntry.getUtf8Name().isNonNull()) {
                    NullableNativeMemory.free(packageEntry.getUtf8Name());
                }
            }
        }
    }

    private static final class SnapshotModuleTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        SnapshotModuleTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected SnapshotModuleEntry[] createTable(int size) {
            return new SnapshotModuleEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            return ((SnapshotModuleEntry) v0).getId() == ((SnapshotModuleEntry) v1).getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(SnapshotModuleEntry.class));
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public SnapshotModuleEntry[] getTable() {
            return (SnapshotModuleEntry[]) super.getTable();
        }
    }

    private static final class SnapshotClassLoaderTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        SnapshotClassLoaderTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected SnapshotClassLoaderEntry[] createTable(int size) {
            return new SnapshotClassLoaderEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            return ((SnapshotClassLoaderEntry) v0).getId() == ((SnapshotClassLoaderEntry) v1).getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(SnapshotClassLoaderEntry.class));
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public SnapshotClassLoaderEntry[] getTable() {
            return (SnapshotClassLoaderEntry[]) super.getTable();
        }
    }

    private static final class ObjectIdTable extends AbstractUninterruptibleHashtable {
        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectIdTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected ObjectIdEntry[] createTable(int size) {
            return new ObjectIdEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            return ((ObjectIdEntry) v0).getObjectRawValue() == ((ObjectIdEntry) v1).getObjectRawValue();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(ObjectIdEntry.class));
        }
    }

    private static final class ReplaceDotWithSlash implements UninterruptibleUtils.CharReplacer {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public char replace(char ch) {
            return ch == '.' ? '/' : ch;
        }
    }
}
