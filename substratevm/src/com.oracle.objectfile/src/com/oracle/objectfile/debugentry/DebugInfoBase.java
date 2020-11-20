/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import org.graalvm.compiler.debug.DebugContext;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

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
    private LinkedList<TypeEntry> types = new LinkedList<>();
    /**
     * index of already seen classes.
     */
    private Map<String, TypeEntry> typesIndex = new HashMap<>();
    /**
     * List of class entries detailing class info for primary ranges.
     */
    private LinkedList<ClassEntry> primaryClasses = new LinkedList<>();
    /**
     * index of already seen classes.
     */
    private Map<String, ClassEntry> primaryClassesIndex = new HashMap<>();
    /**
     * Index of files which contain primary or secondary ranges.
     */
    private Map<Path, FileEntry> filesIndex = new HashMap<>();
    /**
     * List of of files which contain primary or secondary ranges.
     */
    private LinkedList<FileEntry> files = new LinkedList<>();
    /**
     * Flag set to true if heap references are stored as addresses relative to a heap base register
     * otherwise false.
     */
    private boolean useHeapBase;
    private int oopShiftBitCount;
    private int oopFlagBitsMask;
    private int oopReferenceByteCount;

    public DebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.useHeapBase = true;
        this.oopFlagBitsMask = 0;
        this.oopShiftBitCount = 0;
        this.oopReferenceByteCount = 0;
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
         * track whether we need to use a heap base regsiter
         */
        useHeapBase = debugInfoProvider.useHeapBase();

        /*
         * save mask for low order flag bits
         */
        oopFlagBitsMask = debugInfoProvider.oopFlagBitsMask();
        /* flag bits be bewteen 1 and 32 for us to emit as DW_OP_lit<n> */
        assert oopFlagBitsMask > 0 && oopFlagBitsMask < 32;
        /* mask must be contiguous from bit 0 */
        assert ((oopFlagBitsMask + 1) & oopFlagBitsMask) == 0;

        /* Save amount we need to shift references by when loading from an object field. */
        oopShiftBitCount = debugInfoProvider.oopShiftBitCount();

        /* Save number of bytes in a reference field. */
        oopReferenceByteCount = debugInfoProvider.oopReferenceByteCount();

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
            Path cachePath = debugCodeInfo.cachePath();
            String className = TypeEntry.canonicalize(debugCodeInfo.className());
            String methodName = debugCodeInfo.methodName();
            String symbolName = debugCodeInfo.symbolNameForMethod();
            String paramSignature = debugCodeInfo.paramSignature();
            String returnTypeName = TypeEntry.canonicalize(debugCodeInfo.returnTypeName());
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();
            boolean isDeoptTarget = debugCodeInfo.isDeoptTarget();
            int modifiers = debugCodeInfo.getModifiers();

            /* Search for a method defining this primary range. */
            ClassEntry classEntry = ensureClassEntry(className);
            FileEntry fileEntry = ensureFileEntry(fileName, filePath, cachePath);
            Range primaryRange = classEntry.makePrimaryRange(methodName, symbolName, paramSignature, returnTypeName, stringTable, fileEntry, lo, hi, primaryLine, modifiers, isDeoptTarget);
            debugContext.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", className, methodName, filePath, fileName, primaryLine, lo, hi);
            classEntry.indexPrimary(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
            debugCodeInfo.lineInfoProvider().forEach(debugLineInfo -> {
                String fileNameAtLine = debugLineInfo.fileName();
                Path filePathAtLine = debugLineInfo.filePath();
                String classNameAtLine = TypeEntry.canonicalize(debugLineInfo.className());
                String methodNameAtLine = debugLineInfo.methodName();
                String symbolNameAtLine = debugLineInfo.symbolNameForMethod();
                int loAtLine = lo + debugLineInfo.addressLo();
                int hiAtLine = lo + debugLineInfo.addressHi();
                int line = debugLineInfo.line();
                Path cachePathAtLine = debugLineInfo.cachePath();
                /*
                 * Record all subranges even if they have no line or file so we at least get a
                 * symbol for them and don't see a break in the address range.
                 */
                FileEntry subFileEntry = ensureFileEntry(fileNameAtLine, filePathAtLine, cachePathAtLine);
                Range subRange = new Range(classNameAtLine, methodNameAtLine, symbolNameAtLine, stringTable, subFileEntry, loAtLine, hiAtLine, line, primaryRange);
                classEntry.indexSubRange(subRange);
                try (DebugContext.Scope s = debugContext.scope("Subranges")) {
                    debugContext.log(DebugContext.VERBOSE_LEVEL, "SubRange %s.%s %s %s:%d 0x%x, 0x%x]", classNameAtLine, methodNameAtLine, filePathAtLine, fileNameAtLine, line, loAtLine, hiAtLine);
                }
            });
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

    private ClassEntry ensureClassEntry(String className) {
        /* See if we already have an entry. */
        ClassEntry classEntry = primaryClassesIndex.get(className);
        if (classEntry == null) {
            TypeEntry typeEntry = typesIndex.get(className);
            assert (typeEntry != null && typeEntry.isClass());
            classEntry = (ClassEntry) typeEntry;
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(className, classEntry);
        }
        assert (classEntry.getTypeName().equals(className));
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

    protected FileEntry ensureFileEntry(String fileName, Path filePath, Path cachePath) {
        if (fileName == null || fileName.length() == 0) {
            return null;
        }
        Path fileAsPath;
        if (filePath == null) {
            fileAsPath = Paths.get(fileName);
        } else {
            fileAsPath = filePath.resolve(fileName);
        }
        /* Reuse any existing entry. */
        FileEntry fileEntry = findFile(fileAsPath);
        if (fileEntry == null) {
            fileEntry = addFileEntry(fileName, filePath, cachePath);
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

    public LinkedList<TypeEntry> getTypes() {
        return types;
    }

    public LinkedList<ClassEntry> getPrimaryClasses() {
        return primaryClasses;
    }

    @SuppressWarnings("unused")
    public LinkedList<FileEntry> getFiles() {
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

    public byte oopFlagBitsMask() {
        return (byte) oopFlagBitsMask;
    }

    public int oopShiftBitCount() {
        return oopShiftBitCount;
    }

    public int oopReferenceByteCount() {
        return oopReferenceByteCount;
    }
}
