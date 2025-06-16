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

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.Pointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.memory.NullableNativeMemory;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

/**
 * Repository that collects and writes used classes, packages, modules, and classloaders.
 */
public class JfrTypeRepository implements JfrRepository {
    private static final String BOOTSTRAP_NAME = "bootstrap";
    // The following tables are only used by the flushing/rotating thread
    private final JfrClassInfoTable flushedClasses;
    private final JfrPackageInfoTable flushedPackages;
    private final JfrModuleInfoTable flushedModules;
    private final JfrClassLoaderInfoTable flushedClassLoaders;
    private final TypeInfo typeInfo;

    // epochTypeData tables are written to from threads emitting events and read from the
    // flushing/rotating thread
    // Their purpose is to lazily collect tagged JFR classes, which may later be serialized during
    // flush/rotation.
    private final JfrClassInfoTable epochTypeData0;
    private final JfrClassInfoTable epochTypeData1;

    private final UninterruptibleUtils.CharReplacer dotWithSlash;
    private long currentPackageId = 0;
    private long currentModuleId = 0;
    private long currentClassLoaderId = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository() {
        flushedClasses = new JfrClassInfoTable();
        flushedPackages = new JfrPackageInfoTable();
        flushedModules = new JfrModuleInfoTable();
        flushedClassLoaders = new JfrClassLoaderInfoTable();

        typeInfo = new TypeInfo();
        dotWithSlash = new ReplaceDotWithSlash();

        epochTypeData0 = new JfrClassInfoTable();
        epochTypeData1 = new JfrClassInfoTable();
    }

    public void teardown() {
        clearEpochData();
        getEpochData(false).clear();
        typeInfo.teardown();
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.")
    private JfrClassInfoTable getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochTypeData0 : epochTypeData1;
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    public long getClassId(Class<?> clazz) {
        JfrClassInfoTable classInfoTable = getEpochData(false);
        ClassInfoRaw classInfoRaw = StackValue.get(ClassInfoRaw.class);
        classInfoRaw.setId(JfrTraceId.getTraceId(clazz));
        classInfoRaw.setHash(getHash(clazz.getName()));
        classInfoRaw.setName(clazz.getName());
        classInfoRaw.setInstance(clazz);
        classInfoTable.putIfAbsent(classInfoRaw);

        return JfrTraceId.load(clazz);
    }

    @Override
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        typeInfo.reset();
        collectTypeInfo(flushpoint);
        int count = writeClasses(writer, typeInfo, flushpoint);
        count += writePackages(writer, typeInfo, flushpoint);
        count += writeModules(writer, typeInfo, flushpoint);
        count += writeClassLoaders(writer, typeInfo, flushpoint);
        if (flushpoint) {
            flushedClasses.putAll(typeInfo.classes);
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
     *
     * This method does not need to be marked uninterruptible since the epoch cannot change while
     * the chunkwriter lock is held. Unlike other JFR repositories, locking is not needed to protect
     * a data buffer. Similar to other constant repositories, writes/reads to the current epochData
     * are allowed to race at flushpoints. There is no risk of separating events from constant data
     * due to the write order (constants before events during emission, and events before constants
     * during flush). The epochData tables are only cleared at rotation safepoints.
     */
    private void collectTypeInfo(boolean flushpoint) {
        JfrClassInfoTable classInfoTable = getEpochData(!flushpoint);
        ClassInfoRaw[] table = (ClassInfoRaw[]) classInfoTable.getTable();

        for (int i = 0; i < table.length; i++) {
            ClassInfoRaw entry = table[i];
            while (entry.isNonNull()) {
                Class<?> clazz = entry.getInstance();
                assert DynamicHub.fromClass(clazz).isLoaded();
                if (flushpoint) {
                    // Still must check the bit is set since we don't clear the epoch data tables
                    // until safepoint.
                    if (JfrTraceId.isUsedCurrentEpoch(clazz)) {
                        visitClass(typeInfo, clazz);
                    }
                } else {
                    if (JfrTraceId.isUsedPreviousEpoch(clazz)) {
                        JfrTraceId.clearUsedPreviousEpoch(clazz);
                        visitClass(typeInfo, clazz);
                    }
                }
                entry = entry.getNext();
            }
        }
    }

    private void visitClass(TypeInfo typeInfo, Class<?> clazz) {
        if (clazz != null && addClass(typeInfo, clazz)) {
            visitClassLoader(typeInfo, clazz.getClassLoader());
            visitPackage(typeInfo, clazz);
            visitClass(typeInfo, clazz.getSuperclass());
        }
    }

    private void visitPackage(TypeInfo typeInfo, Class<?> clazz) {
        if (addPackage(typeInfo, clazz)) {
            visitModule(typeInfo, clazz);
        }
    }

    private void visitModule(TypeInfo typeInfo, Class<?> clazz) {
        Module module = clazz.getModule();
        if (module != null && addModule(typeInfo, module)) {
            visitClassLoader(typeInfo, module.getClassLoader());
        }
    }

    private void visitClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        // The null class-loader is serialized as the "bootstrap" class-loader.
        if (addClassLoader(typeInfo, classLoader)) {
            if (classLoader != null) {
                visitClass(typeInfo, classLoader.getClass());
            }
        }
    }

    private int writeClasses(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        int size = typeInfo.classes.getSize();
        ClassInfoRaw[] table = (ClassInfoRaw[]) typeInfo.classes.getTable();
        if (size == 0) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Class.getId());
        writer.writeCompressedInt(size);

        // Nested loops since the visitor pattern may allocate.
        for (int i = 0; i < table.length; i++) {
            ClassInfoRaw entry = table[i];
            while (entry.isNonNull()) {
                writeClass(typeInfo, writer, entry, flushpoint);
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private void writeClass(TypeInfo typeInfo, JfrChunkWriter writer, ClassInfoRaw classInfoRaw, boolean flushpoint) {
        assert classInfoRaw.getHash() != 0;
        Class<?> clazz = classInfoRaw.getInstance();
        PackageInfoRaw packageInfoRaw = StackValue.get(PackageInfoRaw.class);
        setPackageNameAndLength(clazz, packageInfoRaw);
        packageInfoRaw.setHash(getHash(packageInfoRaw));

        boolean hasClassLoader = clazz.getClassLoader() != null;
        writer.writeCompressedLong(classInfoRaw.getId());
        writer.writeCompressedLong(getClassLoaderId(typeInfo, hasClassLoader ? clazz.getClassLoader().getName() : null, hasClassLoader));
        writer.writeCompressedLong(getSymbolId(writer, clazz.getName(), flushpoint, true));
        writer.writeCompressedLong(getPackageId(typeInfo, packageInfoRaw));
        writer.writeCompressedLong(clazz.getModifiers());
        writer.writeBoolean(clazz.isHidden());

        // We no longer need the buffer.
        if (packageInfoRaw.getModifiedUTF8Name().isNonNull()) {
            NullableNativeMemory.free(packageInfoRaw.getModifiedUTF8Name());
        }
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

    // Copy to a new buffer so each table has its own copy of the data. This simplifies cleanup and
    // mitigates double frees.
    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private static long getSymbolId(JfrChunkWriter writer, PointerBase source, UnsignedWord length, int hash, boolean flushpoint) {
        Pointer destination = NullableNativeMemory.malloc(length, NmtCategory.JFR);
        if (destination.isNull()) {
            return 0L;
        }
        UnmanagedMemoryUtil.copy((Pointer) source, destination, length);

        assert writer.isLockedByCurrentThread();
        return SubstrateJVM.getSymbolRepository().getSymbolId(destination, length, hash, !flushpoint);
    }

    private int writePackages(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        int size = typeInfo.packages.getSize();
        PackageInfoRaw[] table = (PackageInfoRaw[]) typeInfo.packages.getTable();
        if (size == 0) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Package.getId());
        writer.writeCompressedInt(size);

        for (int i = 0; i < table.length; i++) {
            PackageInfoRaw packageInfoRaw = table[i];
            while (packageInfoRaw.isNonNull()) {
                writePackage(typeInfo, writer, packageInfoRaw, flushpoint);
                packageInfoRaw = packageInfoRaw.getNext();
            }
        }
        return NON_EMPTY;
    }

    private void writePackage(TypeInfo typeInfo, JfrChunkWriter writer, PackageInfoRaw packageInfoRaw, boolean flushpoint) {
        assert packageInfoRaw.getHash() != 0;
        writer.writeCompressedLong(packageInfoRaw.getId());  // id
        // Packages with the same name use the same buffer for the whole epoch so it's fine to use
        // that address as the symbol repo hash. No further need to deduplicate.
        writer.writeCompressedLong(getSymbolId(writer, packageInfoRaw.getModifiedUTF8Name(), packageInfoRaw.getNameLength(),
                        UninterruptibleUtils.Long.hashCode(packageInfoRaw.getModifiedUTF8Name().rawValue()), flushpoint));
        writer.writeCompressedLong(getModuleId(typeInfo, packageInfoRaw.getModuleName(), packageInfoRaw.getHasModule()));
        writer.writeBoolean(false); // exported
    }

    private int writeModules(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        int size = typeInfo.modules.getSize();
        ModuleInfoRaw[] table = (ModuleInfoRaw[]) typeInfo.modules.getTable();
        if (size == 0) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.Module.getId());
        writer.writeCompressedInt(size);

        for (int i = 0; i < table.length; i++) {
            ModuleInfoRaw entry = table[i];
            while (entry.isNonNull()) {
                writeModule(typeInfo, writer, entry, flushpoint);
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private void writeModule(TypeInfo typeInfo, JfrChunkWriter writer, ModuleInfoRaw moduleInfoRaw, boolean flushpoint) {
        writer.writeCompressedLong(moduleInfoRaw.getId());
        writer.writeCompressedLong(getSymbolId(writer, moduleInfoRaw.getName(), flushpoint, false));
        writer.writeCompressedLong(0); // Version, e.g. "11.0.10-internal"
        writer.writeCompressedLong(0); // Location, e.g. "jrt:/java.base"
        writer.writeCompressedLong(getClassLoaderId(typeInfo, moduleInfoRaw.getClassLoaderName(), moduleInfoRaw.getHasClassLoader()));
    }

    private static int writeClassLoaders(JfrChunkWriter writer, TypeInfo typeInfo, boolean flushpoint) {
        if (typeInfo.classLoaders.getSize() == 0) {
            return EMPTY;
        }
        writer.writeCompressedLong(JfrType.ClassLoader.getId());
        writer.writeCompressedInt(typeInfo.classLoaders.getSize());

        for (int i = 0; i < typeInfo.classLoaders.getTable().length; i++) {
            ClassLoaderInfoRaw entry = (ClassLoaderInfoRaw) typeInfo.classLoaders.getTable()[i];
            while (entry.isNonNull()) {
                writeClassLoader(writer, entry, flushpoint);
                entry = entry.getNext();
            }
        }
        return NON_EMPTY;
    }

    private static void writeClassLoader(JfrChunkWriter writer, ClassLoaderInfoRaw classLoaderInfoRaw, boolean flushpoint) {
        writer.writeCompressedLong(classLoaderInfoRaw.getId());
        writer.writeCompressedLong(classLoaderInfoRaw.getClassTraceId());
        writer.writeCompressedLong(getSymbolId(writer, classLoaderInfoRaw.getName(), flushpoint, false));
    }

    private boolean addClass(TypeInfo typeInfo, Class<?> clazz) {
        ClassInfoRaw classInfoRaw = StackValue.get(ClassInfoRaw.class);
        classInfoRaw.setId(JfrTraceId.getTraceId(clazz));
        classInfoRaw.setHash(getHash(clazz.getName()));

        // Once the traceID is set, we can do a look-up.
        if (isClassVisited(typeInfo, classInfoRaw)) {
            return false;
        }

        classInfoRaw.setName(clazz.getName());
        classInfoRaw.setInstance(clazz);
        assert !typeInfo.classes.contains(classInfoRaw);
        typeInfo.classes.putNew(classInfoRaw);
        assert typeInfo.classes.contains(classInfoRaw);
        return true;
    }

    private boolean isClassVisited(TypeInfo typeInfo, ClassInfoRaw classInfoRaw) {
        return typeInfo.classes.contains(classInfoRaw) || flushedClasses.contains(classInfoRaw);
    }

    /** We cannot directly call getPackage() or getPackageName() since that may allocate. */
    private boolean addPackage(TypeInfo typeInfo, Class<?> clazz) {
        boolean hasModule = clazz.getModule() != null;
        String moduleName = hasModule ? clazz.getModule().getName() : null;

        PackageInfoRaw packageInfoRaw = StackValue.get(PackageInfoRaw.class);
        packageInfoRaw.setName(null); // No allocation free way to get the name String.
        setPackageNameAndLength(clazz, packageInfoRaw);

        /*
         * The empty/null package represented by "" is always traced with id 0. The id 0 is reserved
         * and does not need to be serialized.
         */
        if (packageInfoRaw.getNameLength().equal(0)) {
            return false;
        }
        packageInfoRaw.setHash(getHash(packageInfoRaw));
        if (isPackageVisited(typeInfo, packageInfoRaw)) {
            assert moduleName == (flushedPackages.contains(packageInfoRaw) ? ((PackageInfoRaw) flushedPackages.get(packageInfoRaw)).getModuleName()
                            : ((PackageInfoRaw) typeInfo.packages.get(packageInfoRaw)).getModuleName());
            NullableNativeMemory.free(packageInfoRaw.getModifiedUTF8Name());
            return false;
        }

        packageInfoRaw.setId(++currentPackageId);
        packageInfoRaw.setHasModule(hasModule);
        packageInfoRaw.setModuleName(moduleName);
        assert !typeInfo.packages.contains(packageInfoRaw); // *** remove later
        typeInfo.packages.putNew(packageInfoRaw); // *** Do not free the buffer. A pointer to it is
                                                  // shallow copied into the hash map.
        assert typeInfo.packages.contains(packageInfoRaw);
        return true;
    }

    private boolean isPackageVisited(TypeInfo typeInfo, PackageInfoRaw packageInfoRaw) {
        return flushedPackages.contains(packageInfoRaw) || typeInfo.packages.contains(packageInfoRaw);
    }

    private long getPackageId(TypeInfo typeInfo, PackageInfoRaw packageInfoRaw) {
        if (packageInfoRaw.getModifiedUTF8Name().isNonNull() && packageInfoRaw.getNameLength().aboveOrEqual(1)) {
            if (flushedPackages.contains(packageInfoRaw)) {
                return ((PackageInfoRaw) flushedPackages.get(packageInfoRaw)).getId();
            }
            return ((PackageInfoRaw) typeInfo.packages.get(packageInfoRaw)).getId();
        } else {
            // Empty package has reserved ID 0
            return 0;
        }
    }

    private boolean addModule(TypeInfo typeInfo, Module module) {
        ModuleInfoRaw moduleInfoRaw = StackValue.get(ModuleInfoRaw.class);
        moduleInfoRaw.setName(module.getName());
        moduleInfoRaw.setHash(getHash(module.getName()));
        if (isModuleVisited(typeInfo, moduleInfoRaw)) {
            return false;
        }
        moduleInfoRaw.setId(++currentModuleId);
        moduleInfoRaw.setHasClassLoader(module.getClassLoader() != null);
        moduleInfoRaw.setClassLoaderName(moduleInfoRaw.getHasClassLoader() ? module.getClassLoader().getName() : null);
        typeInfo.modules.putNew(moduleInfoRaw);
        return true;
    }

    private boolean isModuleVisited(TypeInfo typeInfo, ModuleInfoRaw moduleInfoRaw) {
        return typeInfo.modules.contains(moduleInfoRaw) || flushedModules.contains(moduleInfoRaw);
    }

    private long getModuleId(TypeInfo typeInfo, String moduleName, boolean hasModule) {
        if (hasModule) {
            ModuleInfoRaw moduleInfoRaw = StackValue.get(ModuleInfoRaw.class);
            moduleInfoRaw.setName(moduleName);
            moduleInfoRaw.setHash(getHash(moduleName));
            if (flushedModules.contains(moduleInfoRaw)) {
                return ((ModuleInfoRaw) flushedModules.get(moduleInfoRaw)).getId();
            }
            return ((ModuleInfoRaw) typeInfo.modules.get(moduleInfoRaw)).getId();
        } else {
            return 0;
        }
    }

    private boolean addClassLoader(TypeInfo typeInfo, ClassLoader classLoader) {
        ClassLoaderInfoRaw classLoaderInfoRaw = StackValue.get(ClassLoaderInfoRaw.class);
        if (classLoader == null) {
            classLoaderInfoRaw.setName(BOOTSTRAP_NAME);
        } else {
            classLoaderInfoRaw.setName(classLoader.getName());
        }

        classLoaderInfoRaw.setHash(getHash(classLoaderInfoRaw.getName()));
        if (isClassLoaderVisited(typeInfo, classLoaderInfoRaw)) {
            return false;
        }

        if (classLoader == null) {
            // Bootstrap loader has reserved ID of 0
            classLoaderInfoRaw.setId(0);
            classLoaderInfoRaw.setClassTraceId(0);
        } else {
            classLoaderInfoRaw.setId(++currentClassLoaderId);
            classLoaderInfoRaw.setClassTraceId(JfrTraceId.getTraceId(classLoader.getClass()));
        }

        typeInfo.classLoaders.putNew(classLoaderInfoRaw);
        return true;
    }

    private boolean isClassLoaderVisited(TypeInfo typeInfo, ClassLoaderInfoRaw classLoaderInfoRaw) {
        return flushedClassLoaders.contains(classLoaderInfoRaw) || typeInfo.classLoaders.contains(classLoaderInfoRaw);
    }

    private long getClassLoaderId(TypeInfo typeInfo, String classLoaderName, boolean hasClassLoader) {
        if (hasClassLoader) {
            ClassLoaderInfoRaw classLoaderInfoRaw = StackValue.get(ClassLoaderInfoRaw.class);
            classLoaderInfoRaw.setName(classLoaderName);
            classLoaderInfoRaw.setHash(getHash(classLoaderName));
            if (flushedClassLoaders.contains(classLoaderInfoRaw)) {
                return ((ClassLoaderInfoRaw) flushedClassLoaders.get(classLoaderInfoRaw)).getId();
            }
            return ((ClassLoaderInfoRaw) typeInfo.classLoaders.get(classLoaderInfoRaw)).getId();
        }
        // Bootstrap classloader
        return 0;
    }

    private void clearEpochData() {
        flushedClasses.clear();
        flushedPackages.clear();
        flushedModules.clear();
        flushedClassLoaders.clear();
        currentPackageId = 0;
        currentModuleId = 0;
        currentClassLoaderId = 0;
        getEpochData(true).clear();
    }

    private final class TypeInfo {
        final JfrClassInfoTable classes = new JfrClassInfoTable();
        final JfrPackageInfoTable packages = new JfrPackageInfoTable();
        final JfrModuleInfoTable modules = new JfrModuleInfoTable();
        final JfrClassLoaderInfoTable classLoaders = new JfrClassLoaderInfoTable();

        void reset() {
            classes.clear();
            packages.clear();
            modules.clear();
            classLoaders.clear();
        }

        void teardown() {
            classes.teardown();
            packages.teardown();
            modules.teardown();
            classLoaders.teardown();
        }
    }

    /**
     * This method sets the package name and length. packageInfoRaw may be on the stack or native
     * memory.
     */
    private void setPackageNameAndLength(Class<?> clazz, PackageInfoRaw packageInfoRaw) {
        DynamicHub hub = DynamicHub.fromClass(clazz);
        if (!LayoutEncoding.isHybrid(hub.getLayoutEncoding())) {
            while (hub.hubIsArray()) {
                hub = hub.getComponentType();
            }
        }

        /*
         * Primitives have the null package, which technically has the name "java.lang", but JFR
         * still assigns these the reserved 0 id.
         */
        if (hub.isPrimitive()) {
            packageInfoRaw.setModifiedUTF8Name(WordFactory.nullPointer());
            packageInfoRaw.setNameLength(WordFactory.unsigned(0));
            return;
        }

        String str = hub.getName();
        int dot = str.lastIndexOf('.');
        if (dot == -1) {
            dot = 0;
        }

        int utf8Length = UninterruptibleUtils.String.modifiedUTF8Length(str, false, dotWithSlash);
        Pointer buffer = NullableNativeMemory.malloc(utf8Length, NmtCategory.JFR);
        Pointer bufferEnd = buffer.add(utf8Length);

        // If malloc fails, set a blank package name.
        if (buffer.isNull()) {
            packageInfoRaw.setModifiedUTF8Name(WordFactory.nullPointer());
            packageInfoRaw.setNameLength(WordFactory.unsigned(0));
            return;
        }

        assert buffer.add(dot).belowOrEqual(bufferEnd);

        // Since we're serializing now, we must do replacements here, instead of the symbol
        // repository.
        Pointer packageNameEnd = UninterruptibleUtils.String.toModifiedUTF8(str, dot, buffer, bufferEnd, false, dotWithSlash);
        packageInfoRaw.setModifiedUTF8Name(buffer);

        UnsignedWord packageNameLength = packageNameEnd.subtract(buffer); // end - start
        packageInfoRaw.setNameLength(packageNameLength);
        if (dot == 0) {
            assert packageNameLength.equal(0);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getHash(String imageHeapString) {
        // It's possible the type exists, but has no name.
        if (imageHeapString == null) {
            return 0;
        }
        long rawPointerValue = Word.objectToUntrackedPointer(imageHeapString).rawValue();
        return UninterruptibleUtils.Long.hashCode(rawPointerValue);
    }

    /**
     * We do not have access to an image heap String for packages. Instead, sum the value of the
     * serialized String and use that for the hash.
     */
    private static int getHash(PackageInfoRaw packageInfoRaw) {
        if (packageInfoRaw.getModifiedUTF8Name().isNull() || packageInfoRaw.getNameLength().belowOrEqual(0)) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; packageInfoRaw.getNameLength().aboveThan(i); i++) {
            sum += ((Pointer) packageInfoRaw.getModifiedUTF8Name()).readByte(i);
        }
        return UninterruptibleUtils.Long.hashCode(sum);
    }

    @RawStructure
    private interface JfrTypeInfo extends UninterruptibleEntry {
        @PinnedObjectField
        @RawField
        void setName(String value);

        @PinnedObjectField
        @RawField
        String getName();

        @RawField
        void setId(long value);

        @RawField
        long getId();
    }

    @RawStructure
    private interface ClassInfoRaw extends JfrTypeInfo {
        @PinnedObjectField
        @RawField
        void setInstance(Class<?> value);

        @PinnedObjectField
        @RawField
        Class<?> getInstance();
    }

    @RawStructure
    private interface PackageInfoRaw extends JfrTypeInfo {
        @PinnedObjectField
        @RawField
        void setModuleName(String value);

        @PinnedObjectField
        @RawField
        String getModuleName();

        @RawField
        void setHasModule(boolean value);

        @RawField
        boolean getHasModule();

        @RawField
        void setNameLength(UnsignedWord value);

        @RawField
        UnsignedWord getNameLength();

        @RawField
        void setModifiedUTF8Name(PointerBase value);

        @RawField
        PointerBase getModifiedUTF8Name();
    }

    @RawStructure
    private interface ModuleInfoRaw extends JfrTypeInfo {
        @PinnedObjectField
        @RawField
        void setClassLoaderName(String value);

        @PinnedObjectField
        @RawField
        String getClassLoaderName();

        @RawField
        void setHasClassLoader(boolean value); // Needed because CL name may be empty or null, even
                                               // if CL is non-null

        @RawField
        boolean getHasClassLoader();
    }

    @RawStructure
    private interface ClassLoaderInfoRaw extends JfrTypeInfo {
        @RawField
        void setClassTraceId(long value);

        @RawField
        long getClassTraceId();
    }

    private abstract class JfrTypeInfoTable extends AbstractUninterruptibleHashtable {
        JfrTypeInfoTable(NmtCategory nmtCategory) {
            super(nmtCategory);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            JfrTypeInfo a = (JfrTypeInfo) v0;
            JfrTypeInfo b = (JfrTypeInfo) v1;
            return a.getName() == b.getName();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void putAll(JfrTypeInfoTable sourceTable) {
            for (int i = 0; i < sourceTable.getTable().length; i++) {
                JfrTypeInfo entry = (JfrTypeInfo) sourceTable.getTable()[i];
                while (entry.isNonNull()) {
                    putNew(entry);
                    entry = entry.getNext();
                }
            }
        }
    }

    private final class JfrClassInfoTable extends JfrTypeInfoTable {
        @Platforms(Platform.HOSTED_ONLY.class)
        public JfrClassInfoTable() {
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
            JfrTypeInfo a = (JfrTypeInfo) v0;
            JfrTypeInfo b = (JfrTypeInfo) v1;
            return a.getId() == b.getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
            return copyToHeap(visitedOnStack, SizeOf.unsigned(ClassInfoRaw.class));
        }
    }

    private final class JfrPackageInfoTable extends JfrTypeInfoTable {
        @Platforms(Platform.HOSTED_ONLY.class)
        JfrPackageInfoTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected PackageInfoRaw[] createTable(int size) {
            return new PackageInfoRaw[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
            return copyToHeap(visitedOnStack, SizeOf.unsigned(PackageInfoRaw.class));
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            // IDs cannot be compared since they are only assigned after checking the table.
            PackageInfoRaw entry1 = (PackageInfoRaw) v0;
            PackageInfoRaw entry2 = (PackageInfoRaw) v1;
            return entry1.getNameLength().equal(entry2.getNameLength()) && LibC.memcmp(entry1.getModifiedUTF8Name(), entry2.getModifiedUTF8Name(), entry1.getNameLength()) == 0;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void free(UninterruptibleEntry entry) {
            PackageInfoRaw packageInfoRaw = (PackageInfoRaw) entry;
            /* The base method will free only the entry itself, not the utf8 data. */
            NullableNativeMemory.free(packageInfoRaw.getModifiedUTF8Name());
            packageInfoRaw.setModifiedUTF8Name(WordFactory.nullPointer());
            super.free(entry);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void putAll(JfrPackageInfoTable sourceTable) {
            for (int i = 0; i < sourceTable.getTable().length; i++) {
                PackageInfoRaw sourceInfo = (PackageInfoRaw) sourceTable.getTable()[i];
                while (sourceInfo.isNonNull()) {
                    if (!contains(sourceInfo)) {
                        // Put if not already there.
                        PackageInfoRaw destinationInfo = (PackageInfoRaw) putNew(sourceInfo);
                        // allocate a new buffer.
                        PointerBase newUtf8Name = NullableNativeMemory.malloc(sourceInfo.getNameLength(), NmtCategory.JFR);
                        // set the buffer ptr.
                        destinationInfo.setModifiedUTF8Name(newUtf8Name);
                        // Copy source buffer contents over to new buffer.
                        if (newUtf8Name.isNonNull()) {
                            UnmanagedMemoryUtil.copy((Pointer) sourceInfo.getModifiedUTF8Name(), (Pointer) newUtf8Name, sourceInfo.getNameLength());
                        }
                    }
                    sourceInfo = sourceInfo.getNext();
                }
            }
        }
    }

    private final class JfrModuleInfoTable extends JfrTypeInfoTable {
        @Platforms(Platform.HOSTED_ONLY.class)
        JfrModuleInfoTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected ModuleInfoRaw[] createTable(int size) {
            return new ModuleInfoRaw[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
            return copyToHeap(visitedOnStack, SizeOf.unsigned(ModuleInfoRaw.class));
        }
    }

    private final class JfrClassLoaderInfoTable extends JfrTypeInfoTable {
        @Platforms(Platform.HOSTED_ONLY.class)
        JfrClassLoaderInfoTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected ClassLoaderInfoRaw[] createTable(int size) {
            return new ClassLoaderInfoRaw[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
            return copyToHeap(visitedOnStack, SizeOf.unsigned(ClassLoaderInfoRaw.class));
        }
    }

    private static final class ReplaceDotWithSlash implements UninterruptibleUtils.CharReplacer {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public char replace(char ch) {
            if (ch == '.') {
                return '/';
            }
            return ch;
        }
    }
}
