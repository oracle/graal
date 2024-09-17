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

package com.oracle.objectfile.runtime;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.DebugInfoBase;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.EnumClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignTypeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StringTable;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.objectfile.debugentry.range.PrimaryRange;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFileInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocationInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo;

import jdk.graal.compiler.debug.DebugContext;
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
public abstract class RuntimeDebugInfoBase extends DebugInfoBase {
    protected RuntimeDebugInfoProvider debugInfoProvider;

    private String cuName;

    @SuppressWarnings("this-escape")
    public RuntimeDebugInfoBase(ByteOrder byteOrder) {
        super(byteOrder);
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

    /**
     * Entry point allowing ELFObjectFile to pass on information about types, code and heap data.
     *
     * @param debugInfoProvider provider instance passed by ObjectFile client.
     */
    @SuppressWarnings("try")
    public void installDebugInfo(RuntimeDebugInfoProvider debugInfoProvider) {
        this.debugInfoProvider = debugInfoProvider;

        /*
         * Track whether we need to use a heap base register.
         */
        useHeapBase = debugInfoProvider.useHeapBase();

        cuName = debugInfoProvider.getCompilationUnitName();

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

        DebugCodeInfo debugCodeInfo = debugInfoProvider.codeInfoProvider();
        debugCodeInfo.debugContext((debugContext) -> {
            /*
             * Primary file name and full method name need to be written to the debug_str section.
             */
            ResolvedJavaType ownerType = debugCodeInfo.ownerType();

            DebugInfoProvider.DebugTypeInfo debugTypeInfo = debugInfoProvider.createDebugTypeInfo(ownerType);
            String typeName = debugTypeInfo.typeName();
            typeName = stringTable.uniqueDebugString(typeName);
            DebugTypeKind typeKind = debugTypeInfo.typeKind();
            int byteSize = debugTypeInfo.size();

            if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                debugContext.log(DebugContext.INFO_LEVEL, "Register %s type %s ", typeKind.toString(), typeName);
            }
            String fileName = debugTypeInfo.fileName();
            Path filePath = debugTypeInfo.filePath();
            TypeEntry typeEntry = addTypeEntry(ownerType, typeName, fileName, filePath, byteSize, typeKind);
            typeEntry.addDebugInfo(this, debugTypeInfo, debugContext);

            fileName = debugCodeInfo.fileName();
            filePath = debugCodeInfo.filePath();
            String methodName = debugCodeInfo.name();
            long lo = debugCodeInfo.addressLo();
            long hi = debugCodeInfo.addressHi();
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

            collectFilesAndDirs(classEntry);

            debugInfoProvider.recordActivity();
        });

        // populate a file and dir list and associated index for each class entry
        // getInstanceClasses().forEach(DebugInfoBase::collectFilesAndDirs);
    }

    @Override
    protected TypeEntry createTypeEntry(String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
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

    @Override
    protected TypeEntry addTypeEntry(ResolvedJavaType idType, String typeName, String fileName, Path filePath, int size, DebugTypeKind typeKind) {
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

    @Override
    public TypeEntry lookupTypeEntry(ResolvedJavaType type) {
        TypeEntry typeEntry = typesIndex.get(type);
        if (typeEntry == null) {
            DebugInfoProvider.DebugTypeInfo debugTypeInfo = debugInfoProvider.createDebugTypeInfo(type);
            debugTypeInfo.debugContext((debugContext) -> {
                String typeName = debugTypeInfo.typeName();
                typeName = stringTable.uniqueDebugString(typeName);
                DebugTypeKind typeKind = debugTypeInfo.typeKind();
                int byteSize = debugTypeInfo.size();

                if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                    debugContext.log(DebugContext.INFO_LEVEL, "Register %s type %s ", typeKind.toString(), typeName);
                }
                String fileName = debugTypeInfo.fileName();
                Path filePath = debugTypeInfo.filePath();
                TypeEntry newTypeEntry = addTypeEntry(type, typeName, fileName, filePath, byteSize, typeKind);
                newTypeEntry.addDebugInfo(this, debugTypeInfo, debugContext);
            });

            typeEntry = typesIndex.get(type);
        }

        if (typeEntry == null) {
            throw new RuntimeException("Type entry not found " + type.getName());
        }
        return typeEntry;
    }

    @Override
    public ClassEntry lookupClassEntry(ResolvedJavaType type) {
        // lookup key should advertise itself as a resolved instance class or interface
        assert type.isInstanceClass() || type.isInterface();
        // lookup target should already be included in the index
        ClassEntry classEntry = instanceClassesIndex.get(type);
        if (classEntry == null) {
            DebugInfoProvider.DebugTypeInfo debugTypeInfo = debugInfoProvider.createDebugTypeInfo(type);
            debugTypeInfo.debugContext((debugContext) -> {
                String typeName = debugTypeInfo.typeName();
                typeName = stringTable.uniqueDebugString(typeName);
                DebugTypeKind typeKind = debugTypeInfo.typeKind();
                int byteSize = debugTypeInfo.size();

                if (debugContext.isLogEnabled(DebugContext.INFO_LEVEL)) {
                    debugContext.log(DebugContext.INFO_LEVEL, "Register %s type %s ", typeKind.toString(), typeName);
                }
                String fileName = debugTypeInfo.fileName();
                Path filePath = debugTypeInfo.filePath();
                TypeEntry newTypeEntry = addTypeEntry(type, typeName, fileName, filePath, byteSize, typeKind);
                newTypeEntry.addDebugInfo(this, debugTypeInfo, debugContext);
            });

            classEntry = instanceClassesIndex.get(type);
        }

        if (classEntry == null || !(classEntry.isClass())) {
            throw new RuntimeException("Class entry not found " + type.getName());
        }
        // lookup target should also be indexed in the types index
        assert typesIndex.get(type) != null;
        return classEntry;
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
    @Override
    protected Range addSubrange(DebugLocationInfo locationInfo, PrimaryRange primaryRange, ClassEntry classEntry, EconomicMap<DebugLocationInfo, SubRange> subRangeIndex, DebugContext debugContext) {
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
        final long loOff = locationInfo.addressLo();
        final long hiOff = locationInfo.addressHi() - 1;
        final long lo = primaryRange.getLo() + locationInfo.addressLo();
        final long hi = primaryRange.getLo() + locationInfo.addressHi();
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

    static final Path EMPTY_PATH = Paths.get("");

    @Override
    protected FileEntry addFileEntry(String fileName, Path filePath) {
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

    @Override
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

    public CompiledMethodEntry getCompiledMethod() {
        return compiledMethods.getFirst();
    }
}
