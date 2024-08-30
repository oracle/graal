/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.debugentry.range.PrimaryRange;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFileInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocationInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * An abstract class which indexes the information presented by the DebugInfoProvider in an
 * organization suitable for use by subclasses targeting a specific binary format.
 *
 * This class provides support for iterating over records detailing all the types and compiled
 * methods presented via the DebugInfoProvider interface. The obvious hierarchical traversal order
 * when generating debug info output is:
 *
 * 1) by top level compiled method (and associated primary Range) n.b. these are always presented to
 * the generator in order of ascending address
 *
 * 2) by inlined method (sub range) within top level method, also ordered by ascending address
 *
 * This traversal ensures that debug records are generated in increasing address order
 *
 * An alternative hierarchical traversal order is
 *
 * 1) by top level class (unique ResolvedJavaType id) n.b. types are not guaranteed to be presented
 * to the generator in increasing address order of their method code ranges. In particular many
 * classes do not have top-level compiled methods and may not even have inlined methods.
 *
 * 2) by top level compiled method (and associated primary Range) within a class, which are ordered
 * by ascending address
 *
 * 3) by inlined method (sub range) within top level method, also ordered by ascending address
 *
 * Since clients may need to generate records for classes with no compiled methods, the second
 * traversal order is often employed.
 *
 * n.b. methods of a given class do not always appear in a single continuous address range. The
 * compiler choose to interleave intervening code from other classes or data values in order to get
 * better cache locality. It may also choose to generate deoptimized variants of methods in a
 * separate range from normal, optimized compiled code. This out of (code addess) order sorting may
 * make it difficult to use a class by class traversal to generate debug info in separate per-class
 * units.
 */
public abstract class DebugInfoBase {
    protected ByteOrder byteOrder;
    /**
     * A table listing all known strings, some of which may be marked for insertion into the
     * debug_str section.
     */
    private final StringTable stringTable = new StringTable();
    /**
     * List of dirs in which files are found to reside.
     */
    private final List<DirEntry> dirs = new ArrayList<>();
    /**
     * Index of all dirs in which files are found to reside either as part of substrate/compiler or
     * user code.
     */
    private final EconomicMap<Path, DirEntry> dirsIndex = EconomicMap.create();

    /**
     * List of all types present in the native image including instance classes, array classes,
     * primitive types and the one-off Java header struct.
     */
    private final List<TypeEntry> types = new ArrayList<>();
    /**
     * Index of already seen types keyed by the unique, associated, identifying ResolvedJavaType or,
     * in the single special case of the TypeEntry for the Java header structure, by key null.
     */
    private final Map<ResolvedJavaType, TypeEntry> typesIndex = new HashMap<>();
    /**
     * List of all instance classes found in debug info. These classes do not necessarily have top
     * level or inline compiled methods. This list includes interfaces and enum types.
     */
    private final List<ClassEntry> instanceClasses = new ArrayList<>();
    /**
     * Index of already seen classes.
     */
    private final EconomicMap<ResolvedJavaType, ClassEntry> instanceClassesIndex = EconomicMap.create();
    /**
     * Handle on type entry for header structure.
     */
    private HeaderTypeEntry headerType;
    /**
     * Handle on type entry for void type.
     */
    private TypeEntry voidType;
    /**
     * Handle on class entry for java.lang.Object.
     */
    private ClassEntry objectClass;
    /**
     * List of all top level compiled methods found in debug info. These ought to arrive via the
     * debug info API in ascending address range order.
     */
    private final List<CompiledMethodEntry> compiledMethods = new ArrayList<>();
    /**
     * List of of files which contain primary or secondary ranges.
     */
    private final List<FileEntry> files = new ArrayList<>();
    /**
     * Index of files which contain primary or secondary ranges keyed by path.
     */
    private final EconomicMap<Path, FileEntry> filesIndex = EconomicMap.create();

    /**
     * List of all loaders associated with classes included in the image.
     */
    private final List<LoaderEntry> loaders = new ArrayList<>();

    /**
     * Index of all loaders associated with classes included in the image.
     */
    private final EconomicMap<String, LoaderEntry> loaderIndex = EconomicMap.create();

    /**
     * Flag set to true if heap references are stored as addresses relative to a heap base register
     * otherwise false.
     */
    private boolean useHeapBase;
    /**
     * Number of bits oops are left shifted by when using compressed oops.
     */
    private int oopCompressShift;
    /**
     * Number of low order bits used for tagging oops.
     */
    private int oopTagsCount;
    /**
     * Number of bytes used to store an oop reference.
     */
    private int oopReferenceSize;
    /**
     * Number of bytes used to store a raw pointer.
     */
    private int pointerSize;
    /**
     * Alignment of object memory area (and, therefore, of any oop) in bytes.
     */
    private int oopAlignment;
    /**
     * Number of bits in oop which are guaranteed 0 by virtue of alignment.
     */
    private int oopAlignShift;
    /**
     * The compilation directory in which to look for source files as a {@link String}.
     */
    private String cachePath;

    /**
     * The offset of the first byte beyond the end of the Java compiled code address range.
     */
    private int compiledCodeMax;

    /**
     * The type entry for java.lang.Class.
     */
    private ClassEntry hubClassEntry;

    @SuppressWarnings("this-escape")
    public DebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.useHeapBase = true;
        this.oopTagsCount = 0;
        this.oopCompressShift = 0;
        this.oopReferenceSize = 0;
        this.pointerSize = 0;
        this.oopAlignment = 0;
        this.oopAlignShift = 0;
        this.hubClassEntry = null;
        this.compiledCodeMax = 0;
        // create and index an empty dir with index 0.
        ensureDirEntry(EMPTY_PATH);
    }

    public int compiledCodeMax() {
        return compiledCodeMax;
    }

    /**
     * Entry point allowing ELFObjectFile to pass on information about types, code and heap data.
     *
     * @param debugInfoProvider provider instance passed by ObjectFile client.
     */
    @SuppressWarnings("try")
    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {
        /*
         * This will be needed once we add support for type info:
         *
         * DebugTypeInfoProvider typeInfoProvider = debugInfoProvider.typeInfoProvider(); for
         * (DebugTypeInfo debugTypeInfo : typeInfoProvider) { install types }
         */

        /*
         * Track whether we need to use a heap base register.
         */
        useHeapBase = debugInfoProvider.useHeapBase();

        /*
         * Save count of low order tag bits that may appear in references.
         */
        int oopTagsMask = debugInfoProvider.oopTagsMask();

        /* Tag bits must be between 0 and 32 for us to emit as DW_OP_lit<n>. */
        assert oopTagsMask >= 0 && oopTagsMask < 32;
        /* Mask must be contiguous from bit 0. */
        assert ((oopTagsMask + 1) & oopTagsMask) == 0;

        oopTagsCount = Integer.bitCount(oopTagsMask);

        /* Save amount we need to shift references by when loading from an object field. */
        oopCompressShift = debugInfoProvider.oopCompressShift();

        /* shift bit count must be either 0 or 3 */
        assert (oopCompressShift == 0 || oopCompressShift == 3);

        /* Save number of bytes in a reference field. */
        oopReferenceSize = debugInfoProvider.oopReferenceSize();

        /* Save pointer size of current target. */
        pointerSize = debugInfoProvider.pointerSize();

        /* Save alignment of a reference. */
        oopAlignment = debugInfoProvider.oopAlignment();

        /* Save alignment of a reference. */
        oopAlignShift = Integer.bitCount(oopAlignment - 1);

        /* Reference alignment must be 8 bytes. */
        assert oopAlignment == 8;

        /* retrieve limit for Java code address range */
        compiledCodeMax = debugInfoProvider.compiledCodeMax();

        /* Ensure we have a null string and cachePath in the string section. */
        String uniqueNullString = stringTable.uniqueDebugString("");
        if (debugInfoProvider.getCachePath() != null) {
            cachePath = stringTable.uniqueDebugString(debugInfoProvider.getCachePath().toString());
        } else {
            cachePath = uniqueNullString; // fall back to null string
        }

        /* Create all the types. */
        debugInfoProvider.typeInfoProvider().forEach(debugTypeInfo -> debugTypeInfo.debugContext((debugContext) -> {
            ResolvedJavaType idType = debugTypeInfo.idType();
            String typeName = debugTypeInfo.typeName();
            typeName = stringTable.uniqueDebugString(typeName);
            DebugTypeKind typeKind = debugTypeInfo.typeKind();
            int byteSize = debugTypeInfo.size();

            if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                debugContext.log(DebugContext.INFO_LEVEL, "Register %s type %s ", typeKind.toString(), typeName);
            }
            String fileName = debugTypeInfo.fileName();
            Path filePath = debugTypeInfo.filePath();
            addTypeEntry(idType, typeName, fileName, filePath, byteSize, typeKind);
        }));
        debugInfoProvider.recordActivity();

        /* Now we can cross reference static and instance field details. */
        debugInfoProvider.typeInfoProvider().forEach(debugTypeInfo -> debugTypeInfo.debugContext((debugContext) -> {
            ResolvedJavaType idType = debugTypeInfo.idType();
            String typeName = debugTypeInfo.typeName();
            DebugTypeKind typeKind = debugTypeInfo.typeKind();

            if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                debugContext.log(DebugContext.INFO_LEVEL, "Process %s type %s ", typeKind.toString(), typeName);
            }
            TypeEntry typeEntry = (idType != null ? lookupTypeEntry(idType) : lookupHeaderType());
            typeEntry.addDebugInfo(this, debugTypeInfo, debugContext);
        }));
        debugInfoProvider.recordActivity();

        debugInfoProvider.codeInfoProvider().forEach(debugCodeInfo -> debugCodeInfo.debugContext((debugContext) -> {
            /*
             * Primary file name and full method name need to be written to the debug_str section.
             */
            String fileName = debugCodeInfo.fileName();
            Path filePath = debugCodeInfo.filePath();
            ResolvedJavaType ownerType = debugCodeInfo.ownerType();
            String methodName = debugCodeInfo.name();
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();

            /* Search for a method defining this primary range. */
            ClassEntry classEntry = lookupClassEntry(ownerType);
            MethodEntry methodEntry = classEntry.ensureMethodEntryForDebugRangeInfo(debugCodeInfo, this, debugContext);
            PrimaryRange primaryRange = Range.createPrimary(methodEntry, lo, hi, primaryLine);
            if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                debugContext.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", ownerType.toJavaName(), methodName, filePath, fileName, primaryLine, lo, hi);
            }
            addPrimaryRange(primaryRange, debugCodeInfo, classEntry);
            /*
             * Record all subranges even if they have no line or file so we at least get a symbol
             * for them and don't see a break in the address range.
             */
            EconomicMap<DebugLocationInfo, SubRange> subRangeIndex = EconomicMap.create();
            debugCodeInfo.locationInfoProvider().forEach(debugLocationInfo -> addSubrange(debugLocationInfo, primaryRange, classEntry, subRangeIndex, debugContext));
            debugInfoProvider.recordActivity();
        }));

        debugInfoProvider.dataInfoProvider().forEach(debugDataInfo -> debugDataInfo.debugContext((debugContext) -> {
            if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                String provenance = debugDataInfo.getProvenance();
                String typeName = debugDataInfo.getTypeName();
                String partitionName = debugDataInfo.getPartition();
                /* Address is heap-register relative pointer. */
                long address = debugDataInfo.getAddress();
                long size = debugDataInfo.getSize();
                debugContext.log(DebugContext.INFO_LEVEL, "Data: address 0x%x size 0x%x type %s partition %s provenance %s ", address, size, typeName, partitionName, provenance);
            }
        }));
        // populate a file and dir list and associated index for each class entry
        getInstanceClasses().forEach(classEntry -> {
            collectFilesAndDirs(classEntry);
        });
    }

    private TypeEntry createTypeEntry(String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = null;
        switch (typeKind) {
            case INSTANCE: {
                FileEntry fileEntry = addFileEntry(fileName, filePath);
                typeEntry = new ClassEntry(typeName, fileEntry, size);
                if (typeEntry.getTypeName().equals(DwarfDebugInfo.HUB_TYPE_NAME)) {
                    hubClassEntry = (ClassEntry) typeEntry;
                }
                break;
            }
            case INTERFACE: {
                FileEntry fileEntry = addFileEntry(fileName, filePath);
                typeEntry = new InterfaceClassEntry(typeName, fileEntry, size);
                break;
            }
            case ENUM: {
                FileEntry fileEntry = addFileEntry(fileName, filePath);
                typeEntry = new EnumClassEntry(typeName, fileEntry, size);
                break;
            }
            case PRIMITIVE:
                assert fileName.length() == 0;
                assert filePath == null;
                typeEntry = new PrimitiveTypeEntry(typeName, size);
                break;
            case ARRAY:
                assert fileName.length() == 0;
                assert filePath == null;
                typeEntry = new ArrayTypeEntry(typeName, size);
                break;
            case HEADER:
                assert fileName.length() == 0;
                assert filePath == null;
                typeEntry = new HeaderTypeEntry(typeName, size);
                break;
            case FOREIGN: {
                FileEntry fileEntry = addFileEntry(fileName, filePath);
                typeEntry = new ForeignTypeEntry(typeName, fileEntry, size);
                break;
            }
        }
        return typeEntry;
    }

    private TypeEntry addTypeEntry(ResolvedJavaType idType, String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = (idType != null ? typesIndex.get(idType) : null);
        if (typeEntry == null) {
            typeEntry = createTypeEntry(typeName, fileName, filePath, size, typeKind);
            types.add(typeEntry);
            if (idType != null) {
                typesIndex.put(idType, typeEntry);
            }
            // track object type and header struct
            if (idType == null) {
                headerType = (HeaderTypeEntry) typeEntry;
            }
            if (typeName.equals("java.lang.Object")) {
                objectClass = (ClassEntry) typeEntry;
            }
            if (typeName.equals("void")) {
                voidType = typeEntry;
            }
            if (typeEntry instanceof ClassEntry) {
                indexInstanceClass(idType, (ClassEntry) typeEntry);
            }
        } else {
            if (!(typeEntry.isClass())) {
                assert ((ClassEntry) typeEntry).getFileName().equals(fileName);
            }
        }
        return typeEntry;
    }

    public TypeEntry lookupTypeEntry(ResolvedJavaType type) {
        TypeEntry typeEntry = typesIndex.get(type);
        if (typeEntry == null) {
            throw new RuntimeException("Type entry not found " + type.getName());
        }
        return typeEntry;
    }

    ClassEntry lookupClassEntry(ResolvedJavaType type) {
        // lookup key should advertise itself as a resolved instance class or interface
        assert type.isInstanceClass() || type.isInterface();
        // lookup target should already be included in the index
        ClassEntry classEntry = instanceClassesIndex.get(type);
        if (classEntry == null || !(classEntry.isClass())) {
            throw new RuntimeException("Class entry not found " + type.getName());
        }
        // lookup target should also be indexed in the types index
        assert typesIndex.get(type) != null;
        return classEntry;
    }

    public HeaderTypeEntry lookupHeaderType() {
        // this should only be looked up after all types have been notified
        assert headerType != null;
        return headerType;
    }

    public TypeEntry lookupVoidType() {
        // this should only be looked up after all types have been notified
        assert voidType != null;
        return voidType;
    }

    public ClassEntry lookupObjectClass() {
        // this should only be looked up after all types have been notified
        assert objectClass != null;
        return objectClass;
    }

    private void addPrimaryRange(PrimaryRange primaryRange, DebugCodeInfo debugCodeInfo, ClassEntry classEntry) {
        CompiledMethodEntry compiledMethod = classEntry.indexPrimary(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
        indexCompiledMethod(compiledMethod);
    }

    /**
     * Recursively creates subranges based on DebugLocationInfo including, and appropriately
     * linking, nested inline subranges.
     *
     * @param locationInfo
     * @param primaryRange
     * @param classEntry
     * @param subRangeIndex
     * @param debugContext
     * @return the subrange for {@code locationInfo} linked with all its caller subranges up to the
     *         primaryRange
     */
    @SuppressWarnings("try")
    private Range addSubrange(DebugLocationInfo locationInfo, PrimaryRange primaryRange, ClassEntry classEntry, EconomicMap<DebugLocationInfo, SubRange> subRangeIndex, DebugContext debugContext) {
        /*
         * We still insert subranges for the primary method but they don't actually count as inline.
         * we only need a range so that subranges for inline code can refer to the top level line
         * number.
         */
        DebugLocationInfo callerLocationInfo = locationInfo.getCaller();
        boolean isTopLevel = callerLocationInfo == null;
        assert (!isTopLevel || (locationInfo.name().equals(primaryRange.getMethodName()) &&
                        locationInfo.ownerType().toJavaName().equals(primaryRange.getClassName())));
        Range caller = (isTopLevel ? primaryRange : subRangeIndex.get(callerLocationInfo));
        // the frame tree is walked topdown so inline ranges should always have a caller range
        assert caller != null;

        final String fileName = locationInfo.fileName();
        final Path filePath = locationInfo.filePath();
        final String fullPath = (filePath == null ? "" : filePath.toString() + "/") + fileName;
        final ResolvedJavaType ownerType = locationInfo.ownerType();
        final String methodName = locationInfo.name();
        final int loOff = locationInfo.addressLo();
        final int hiOff = locationInfo.addressHi() - 1;
        final int lo = primaryRange.getLo() + locationInfo.addressLo();
        final int hi = primaryRange.getLo() + locationInfo.addressHi();
        final int line = locationInfo.line();
        ClassEntry subRangeClassEntry = lookupClassEntry(ownerType);
        MethodEntry subRangeMethodEntry = subRangeClassEntry.ensureMethodEntryForDebugRangeInfo(locationInfo, this, debugContext);
        SubRange subRange = Range.createSubrange(subRangeMethodEntry, lo, hi, line, primaryRange, caller, locationInfo.isLeaf());
        classEntry.indexSubRange(subRange);
        subRangeIndex.put(locationInfo, subRange);
        if (debugContext.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
            debugContext.log(DebugContext.DETAILED_LEVEL, "SubRange %s.%s %d %s:%d [0x%x, 0x%x] (%d, %d)",
                            ownerType.toJavaName(), methodName, subRange.getDepth(), fullPath, line, lo, hi, loOff, hiOff);
        }
        assert (callerLocationInfo == null || (callerLocationInfo.addressLo() <= loOff && callerLocationInfo.addressHi() >= hiOff)) : "parent range should enclose subrange!";
        DebugLocalValueInfo[] localValueInfos = locationInfo.getLocalValueInfo();
        for (int i = 0; i < localValueInfos.length; i++) {
            DebugLocalValueInfo localValueInfo = localValueInfos[i];
            if (debugContext.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                debugContext.log(DebugContext.DETAILED_LEVEL, "  locals[%d] %s:%s = %s", localValueInfo.slot(), localValueInfo.name(), localValueInfo.typeName(), localValueInfo);
            }
        }
        subRange.setLocalValueInfo(localValueInfos);
        return subRange;
    }

    private void indexInstanceClass(ResolvedJavaType idType, ClassEntry classEntry) {
        instanceClasses.add(classEntry);
        instanceClassesIndex.put(idType, classEntry);
    }

    private void indexCompiledMethod(CompiledMethodEntry compiledMethod) {
        assert verifyMethodOrder(compiledMethod);
        compiledMethods.add(compiledMethod);
    }

    private boolean verifyMethodOrder(CompiledMethodEntry next) {
        int size = compiledMethods.size();
        if (size > 0) {
            CompiledMethodEntry last = compiledMethods.get(size - 1);
            PrimaryRange lastRange = last.getPrimary();
            PrimaryRange nextRange = next.getPrimary();
            if (lastRange.getHi() > nextRange.getLo()) {
                assert false : "methods %s [0x%x, 0x%x] and %s [0x%x, 0x%x] presented out of order".formatted(lastRange.getFullMethodName(), lastRange.getLo(), lastRange.getHi(),
                                nextRange.getFullMethodName(), nextRange.getLo(), nextRange.getHi());
                return false;
            }
        }
        return true;
    }

    static final Path EMPTY_PATH = Paths.get("");

    private FileEntry addFileEntry(String fileName, Path filePath) {
        assert fileName != null;
        Path dirPath = filePath;
        Path fileAsPath;
        if (filePath != null) {
            fileAsPath = dirPath.resolve(fileName);
        } else {
            fileAsPath = Paths.get(fileName);
            dirPath = EMPTY_PATH;
        }
        FileEntry fileEntry = filesIndex.get(fileAsPath);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(dirPath);
            /* Ensure file and cachepath are added to the debug_str section. */
            uniqueDebugString(fileName);
            uniqueDebugString(cachePath);
            fileEntry = new FileEntry(fileName, dirEntry);
            files.add(fileEntry);
            /* Index the file entry by file path. */
            filesIndex.put(fileAsPath, fileEntry);
        } else {
            assert fileEntry.getDirEntry().getPath().equals(dirPath);
        }
        return fileEntry;
    }

    protected FileEntry ensureFileEntry(DebugFileInfo debugFileInfo) {
        String fileName = debugFileInfo.fileName();
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        Path filePath = debugFileInfo.filePath();
        Path fileAsPath;
        if (filePath == null) {
            fileAsPath = Paths.get(fileName);
        } else {
            fileAsPath = filePath.resolve(fileName);
        }
        /* Reuse any existing entry. */
        FileEntry fileEntry = findFile(fileAsPath);
        if (fileEntry == null) {
            fileEntry = addFileEntry(fileName, filePath);
        }
        return fileEntry;
    }

    private DirEntry ensureDirEntry(Path filePath) {
        if (filePath == null) {
            return null;
        }
        DirEntry dirEntry = dirsIndex.get(filePath);
        if (dirEntry == null) {
            /* Ensure dir path is entered into the debug_str section. */
            uniqueDebugString(filePath.toString());
            dirEntry = new DirEntry(filePath);
            dirsIndex.put(filePath, dirEntry);
            dirs.add(dirEntry);
        }
        return dirEntry;
    }

    protected LoaderEntry ensureLoaderEntry(String loaderId) {
        LoaderEntry loaderEntry = loaderIndex.get(loaderId);
        if (loaderEntry == null) {
            loaderEntry = new LoaderEntry(uniqueDebugString(loaderId));
            loaderIndex.put(loaderEntry.getLoaderId(), loaderEntry);
        }
        return loaderEntry;
    }

    /* Accessors to query the debug info model. */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public List<TypeEntry> getTypes() {
        return types;
    }

    public List<ClassEntry> getInstanceClasses() {
        return instanceClasses;
    }

    public List<CompiledMethodEntry> getCompiledMethods() {
        return compiledMethods;
    }

    public List<FileEntry> getFiles() {
        return files;
    }

    public List<DirEntry> getDirs() {
        return dirs;
    }

    @SuppressWarnings("unused")
    public FileEntry findFile(Path fullFileName) {
        return filesIndex.get(fullFileName);
    }

    public List<LoaderEntry> getLoaders() {
        return loaders;
    }

    @SuppressWarnings("unused")
    public LoaderEntry findLoader(String id) {
        return loaderIndex.get(id);
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     */
    public String uniqueDebugString(String string) {
        return stringTable.uniqueDebugString(string);
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     * @return the offset of the string in the .debug_str section.
     */
    public int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }

    public boolean useHeapBase() {
        return useHeapBase;
    }

    public byte oopTagsMask() {
        return (byte) ((1 << oopTagsCount) - 1);
    }

    public byte oopTagsShift() {
        return (byte) oopTagsCount;
    }

    public int oopCompressShift() {
        return oopCompressShift;
    }

    public int oopReferenceSize() {
        return oopReferenceSize;
    }

    public int pointerSize() {
        return pointerSize;
    }

    public int oopAlignment() {
        return oopAlignment;
    }

    public int oopAlignShift() {
        return oopAlignShift;
    }

    public String getCachePath() {
        return cachePath;
    }

    public boolean isHubClassEntry(ClassEntry classEntry) {
        return classEntry.getTypeName().equals(DwarfDebugInfo.HUB_TYPE_NAME);
    }

    public ClassEntry getHubClassEntry() {
        return hubClassEntry;
    }

    private static void collectFilesAndDirs(ClassEntry classEntry) {
        // track files and dirs we have already seen so that we only add them once
        EconomicSet<FileEntry> visitedFiles = EconomicSet.create();
        EconomicSet<DirEntry> visitedDirs = EconomicSet.create();
        // add the class's file and dir
        includeOnce(classEntry, classEntry.getFileEntry(), visitedFiles, visitedDirs);
        // add files for fields (may differ from class file if we have a substitution)
        for (FieldEntry fieldEntry : classEntry.fields) {
            includeOnce(classEntry, fieldEntry.getFileEntry(), visitedFiles, visitedDirs);
        }
        // add files for declared methods (may differ from class file if we have a substitution)
        for (MethodEntry methodEntry : classEntry.getMethods()) {
            includeOnce(classEntry, methodEntry.getFileEntry(), visitedFiles, visitedDirs);
        }
        // add files for top level compiled and inline methods
        classEntry.compiledEntries().forEachOrdered(compiledMethodEntry -> {
            includeOnce(classEntry, compiledMethodEntry.getPrimary().getFileEntry(), visitedFiles, visitedDirs);
            // we need files for leaf ranges and for inline caller ranges
            //
            // add leaf range files first because they get searched for linearly
            // during line info processing
            compiledMethodEntry.leafRangeIterator().forEachRemaining(subRange -> {
                includeOnce(classEntry, subRange.getFileEntry(), visitedFiles, visitedDirs);
            });
            // now the non-leaf range files
            compiledMethodEntry.topDownRangeIterator().forEachRemaining(subRange -> {
                if (!subRange.isLeaf()) {
                    includeOnce(classEntry, subRange.getFileEntry(), visitedFiles, visitedDirs);
                }
            });
        });
        // now all files and dirs are known build an index for them
        classEntry.buildFileAndDirIndexes();
    }

    /**
     * Ensure the supplied file entry and associated directory entry are included, but only once, in
     * a class entry's file and dir list.
     * 
     * @param classEntry the class entry whose file and dir list may need to be updated
     * @param fileEntry a file entry which may need to be added to the class entry's file list or
     *            whose dir may need adding to the class entry's dir list
     * @param visitedFiles a set tracking current file list entries, updated if a file is added
     * @param visitedDirs a set tracking current dir list entries, updated if a dir is added
     */
    private static void includeOnce(ClassEntry classEntry, FileEntry fileEntry, EconomicSet<FileEntry> visitedFiles, EconomicSet<DirEntry> visitedDirs) {
        if (fileEntry != null && !visitedFiles.contains(fileEntry)) {
            visitedFiles.add(fileEntry);
            classEntry.includeFile(fileEntry);
            DirEntry dirEntry = fileEntry.getDirEntry();
            if (dirEntry != null && !dirEntry.getPathString().isEmpty() && !visitedDirs.contains(dirEntry)) {
                visitedDirs.add(dirEntry);
                classEntry.includeDir(dirEntry);
            }
        }
    }
}
