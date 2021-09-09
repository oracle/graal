/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFileInfo;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo;

/**
 * An abstract class which indexes the information presented by the DebugInfoProvider in an
 * organization suitable for use by subclasses targeting a specific binary format.
 */
public abstract class DebugInfoBase {
    protected ByteOrder byteOrder;
    /**
     * A table listing all known strings, some of which may be marked for insertion into the
     * debug_str section.
     */
    private StringTable stringTable = new StringTable();
    /**
     * Index of all dirs in which files are found to reside either as part of substrate/compiler or
     * user code.
     */
    private Map<Path, DirEntry> dirsIndex = new HashMap<>();

    /*
     * The obvious traversal structure for debug records is:
     *
     * 1) by top level compiled method (primary Range) ordered by ascending address
     *
     * 2) by inlined method (sub range) within top level method ordered by ascending address
     *
     * These can be used to ensure that all debug records are generated in increasing address order
     *
     * An alternative traversal option is
     *
     * 1) by top level class (String id)
     *
     * 2) by top level compiled method (primary Range) within a class ordered by ascending address
     *
     * 3) by inlined method (sub range) within top level method ordered by ascending address
     *
     * This relies on the (current) fact that methods of a given class always appear in a single
     * continuous address range with no intervening code from other methods or data values. this
     * means we can treat each class as a compilation unit, allowing data common to all methods of
     * the class to be shared.
     *
     * A third option appears to be to traverse via files, then top level class within file etc.
     * Unfortunately, files cannot be treated as the compilation unit. A file F may contain multiple
     * classes, say C1 and C2. There is no guarantee that methods for some other class C' in file F'
     * will not be compiled into the address space interleaved between methods of C1 and C2. That is
     * a shame because generating debug info records one file at a time would allow more sharing
     * e.g. enabling all classes in a file to share a single copy of the file and dir tables.
     */

    /**
     * List of class entries detailing class info for primary ranges.
     */
    private List<TypeEntry> types = new ArrayList<>();
    /**
     * index of already seen classes.
     */
    private Map<String, TypeEntry> typesIndex = new HashMap<>();
    /**
     * List of class entries detailing class info for primary ranges.
     */
    private List<ClassEntry> primaryClasses = new ArrayList<>();
    /**
     * index of already seen classes.
     */
    private Map<ResolvedJavaType, ClassEntry> primaryClassesIndex = new HashMap<>();
    /**
     * Index of files which contain primary or secondary ranges.
     */
    private Map<Path, FileEntry> filesIndex = new HashMap<>();
    /**
     * List of of files which contain primary or secondary ranges.
     */
    private List<FileEntry> files = new ArrayList<>();
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

    public DebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.useHeapBase = true;
        this.oopTagsCount = 0;
        this.oopCompressShift = 0;
        this.oopReferenceSize = 0;
        this.pointerSize = 0;
        this.oopAlignment = 0;
        this.oopAlignShift = 0;
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

        /* Tag bits must be between 1 and 32 for us to emit as DW_OP_lit<n>. */
        assert oopTagsMask > 0 && oopTagsMask < 32;
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

        /* Ensure we have a null string in the string section. */
        stringTable.uniqueDebugString("");

        /* Create all the types. */
        debugInfoProvider.typeInfoProvider().forEach(debugTypeInfo -> debugTypeInfo.debugContext((debugContext) -> {
            String typeName = TypeEntry.canonicalize(debugTypeInfo.typeName());
            typeName = stringTable.uniqueDebugString(typeName);
            DebugTypeKind typeKind = debugTypeInfo.typeKind();
            int byteSize = debugTypeInfo.size();

            debugContext.log(DebugContext.INFO_LEVEL, "Register %s type %s ", typeKind.toString(), typeName);
            String fileName = debugTypeInfo.fileName();
            Path filePath = debugTypeInfo.filePath();
            Path cachePath = debugTypeInfo.cachePath();
            addTypeEntry(typeName, fileName, filePath, cachePath, byteSize, typeKind);
        }));

        /* Now we can cross reference static and instance field details. */
        debugInfoProvider.typeInfoProvider().forEach(debugTypeInfo -> debugTypeInfo.debugContext((debugContext) -> {
            String typeName = TypeEntry.canonicalize(debugTypeInfo.typeName());
            DebugTypeKind typeKind = debugTypeInfo.typeKind();

            debugContext.log(DebugContext.INFO_LEVEL, "Process %s type %s ", typeKind.toString(), typeName);
            TypeEntry typeEntry = lookupTypeEntry(typeName);
            typeEntry.addDebugInfo(this, debugTypeInfo, debugContext);
        }));

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
            ClassEntry classEntry = ensureClassEntry(ownerType);
            MethodEntry methodEntry = classEntry.ensureMethodEntryForDebugRangeInfo(debugCodeInfo, this, debugContext);
            Range primaryRange = new Range(stringTable, methodEntry, lo, hi, primaryLine);
            debugContext.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", ownerType.toJavaName(), methodName, filePath, fileName, primaryLine, lo, hi);
            classEntry.indexPrimary(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
            /*
             * Record all subranges even if they have no line or file so we at least get a symbol
             * for them and don't see a break in the address range.
             */
            debugCodeInfo.lineInfoProvider().forEach(debugLineInfo -> recursivelyAddSubRanges(debugLineInfo, primaryRange, classEntry, debugContext));
            primaryRange.mergeSubranges(debugContext);
        }));

        debugInfoProvider.dataInfoProvider().forEach(debugDataInfo -> debugDataInfo.debugContext((debugContext) -> {
            String provenance = debugDataInfo.getProvenance();
            String typeName = debugDataInfo.getTypeName();
            String partitionName = debugDataInfo.getPartition();
            /* Address is heap-register relative pointer. */
            long address = debugDataInfo.getAddress();
            long size = debugDataInfo.getSize();
            debugContext.log(DebugContext.INFO_LEVEL, "Data: address 0x%x size 0x%x type %s partition %s provenance %s ", address, size, typeName, partitionName, provenance);
        }));
    }

    private TypeEntry createTypeEntry(String typeName, String fileName, Path filePath, Path cachePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = null;
        switch (typeKind) {
            case INSTANCE: {
                FileEntry fileEntry = addFileEntry(fileName, filePath, cachePath);
                typeEntry = new ClassEntry(typeName, fileEntry, size);
                break;
            }
            case INTERFACE: {
                FileEntry fileEntry = addFileEntry(fileName, filePath, cachePath);
                typeEntry = new InterfaceClassEntry(typeName, fileEntry, size);
                break;
            }
            case ENUM: {
                FileEntry fileEntry = addFileEntry(fileName, filePath, cachePath);
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
        }
        return typeEntry;
    }

    private TypeEntry addTypeEntry(String typeName, String fileName, Path filePath, Path cachePath, int size, DebugTypeKind typeKind) {
        TypeEntry typeEntry = typesIndex.get(typeName);
        if (typeEntry == null) {
            typeEntry = createTypeEntry(typeName, fileName, filePath, cachePath, size, typeKind);
            types.add(typeEntry);
            typesIndex.put(typeName, typeEntry);
        } else {
            if (!(typeEntry.isClass())) {
                assert ((ClassEntry) typeEntry).getFileName().equals(fileName);
            }
        }
        return typeEntry;
    }

    public TypeEntry lookupTypeEntry(String typeName) {
        TypeEntry typeEntry = typesIndex.get(typeName);
        if (typeEntry == null) {
            throw new RuntimeException("type entry not found " + typeName);
        }
        return typeEntry;
    }

    ClassEntry lookupClassEntry(String typeName) {
        TypeEntry typeEntry = typesIndex.get(typeName);
        if (typeEntry == null || !(typeEntry.isClass())) {
            throw new RuntimeException("class entry not found " + typeName);
        }
        return (ClassEntry) typeEntry;
    }

    /**
     * Recursively creates subranges based on DebugLineInfo including, and appropriately linking,
     * nested inline subranges.
     *
     * @param lineInfo
     * @param primaryRange
     * @param classEntry
     * @param debugContext
     * @return the subrange for {@code lineInfo} linked with all its caller subranges up to the
     *         primaryRange
     */
    @SuppressWarnings("try")
    private Range recursivelyAddSubRanges(DebugInfoProvider.DebugLineInfo lineInfo, Range primaryRange, ClassEntry classEntry, DebugContext debugContext) {
        if (lineInfo == null) {
            return primaryRange;
        }
        /*
         * We still insert subranges for the primary method but they don't actually count as inline.
         * we only need a range so that subranges for inline code can refer to the top level line
         * number
         */
        boolean isInline = lineInfo.getCaller() != null;
        assert (isInline || (lineInfo.name().equals(primaryRange.getMethodName()) && TypeEntry.canonicalize(lineInfo.ownerType().toJavaName()).equals(primaryRange.getClassName())));

        Range caller = recursivelyAddSubRanges(lineInfo.getCaller(), primaryRange, classEntry, debugContext);
        final String fileName = lineInfo.fileName();
        final Path filePath = lineInfo.filePath();
        final ResolvedJavaType ownerType = lineInfo.ownerType();
        final String methodName = lineInfo.name();
        final int lo = primaryRange.getLo() + lineInfo.addressLo();
        final int hi = primaryRange.getLo() + lineInfo.addressHi();
        final int line = lineInfo.line();
        ClassEntry subRangeClassEntry = ensureClassEntry(ownerType);
        MethodEntry subRangeMethodEntry = subRangeClassEntry.ensureMethodEntryForDebugRangeInfo(lineInfo, this, debugContext);
        Range subRange = new Range(stringTable, subRangeMethodEntry, lo, hi, line, primaryRange, isInline, caller);
        classEntry.indexSubRange(subRange);
        try (DebugContext.Scope s = debugContext.scope("Subranges")) {
            debugContext.log(DebugContext.VERBOSE_LEVEL, "SubRange %s.%s %s %s:%d 0x%x, 0x%x]",
                            ownerType.toJavaName(), methodName, filePath, fileName, line, lo, hi);
        }
        return subRange;
    }

    private ClassEntry ensureClassEntry(ResolvedJavaType type) {
        /* See if we already have an entry. */
        ClassEntry classEntry = primaryClassesIndex.get(type);
        if (classEntry == null) {
            TypeEntry typeEntry = typesIndex.get(TypeEntry.canonicalize(type.toJavaName()));
            assert (typeEntry != null && typeEntry.isClass());
            classEntry = (ClassEntry) typeEntry;
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(type, classEntry);
        }
        assert (classEntry.getTypeName().equals(TypeEntry.canonicalize(type.toJavaName())));
        return classEntry;
    }

    private FileEntry addFileEntry(String fileName, Path filePath, Path cachePath) {
        assert fileName != null;
        Path fileAsPath;
        if (filePath != null) {
            fileAsPath = filePath.resolve(fileName);
        } else {
            fileAsPath = Paths.get(fileName);
        }
        FileEntry fileEntry = filesIndex.get(fileAsPath);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(filePath);
            /* Ensure file and cachepath are added to the debug_str section. */
            uniqueDebugString(fileName);
            uniqueDebugString(cachePath.toString());
            fileEntry = new FileEntry(fileName, dirEntry, cachePath);
            files.add(fileEntry);
            /* Index the file entry by file path. */
            filesIndex.put(fileAsPath, fileEntry);
        } else {
            assert (filePath == null ||
                            fileEntry.getDirEntry().getPath().equals(filePath));
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
            fileEntry = addFileEntry(fileName, filePath, debugFileInfo.cachePath());
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
        }
        return dirEntry;
    }

    /* Accessors to query the debug info model. */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public List<TypeEntry> getTypes() {
        return types;
    }

    public List<ClassEntry> getPrimaryClasses() {
        return primaryClasses;
    }

    @SuppressWarnings("unused")
    public List<FileEntry> getFiles() {
        return files;
    }

    @SuppressWarnings("unused")
    public FileEntry findFile(Path fullFileName) {
        return filesIndex.get(fullFileName);
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
     *
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

    public boolean isHubClassEntry(ClassEntry classEntry) {
        return classEntry.getTypeName().equals(DwarfDebugInfo.HUB_TYPE_NAME);
    }

    public int classLayoutAbbrevCode(ClassEntry classEntry) {
        if (useHeapBase & isHubClassEntry(classEntry)) {
            /*
             * This layout adds special logic to remove tag bits from indirect pointers to this
             * type.
             */
            return DwarfDebugInfo.DW_ABBREV_CODE_class_layout2;
        }
        return DwarfDebugInfo.DW_ABBREV_CODE_class_layout1;
    }
}
