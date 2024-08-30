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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.objectfile.debugentry.range.PrimaryRange;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFieldInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugInstanceTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugMethodInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugRangeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;

/**
 * Track debug info associated with a Java class.
 */
public class ClassEntry extends StructureTypeEntry {
    /**
     * Details of this class's superclass.
     */
    protected ClassEntry superClass;
    /**
     * Details of this class's interfaces.
     */
    protected final List<InterfaceClassEntry> interfaces = new ArrayList<>();
    /**
     * Details of the associated file.
     */
    private final FileEntry fileEntry;
    /**
     * Details of the associated loader.
     */
    private LoaderEntry loader;
    /**
     * Details of methods located in this instance.
     */
    protected final List<MethodEntry> methods = new ArrayList<>();
    /**
     * An index of all currently known methods keyed by the unique, associated, identifying
     * ResolvedJavaMethod.
     */
    private final EconomicMap<ResolvedJavaMethod, MethodEntry> methodsIndex = EconomicMap.create();
    /**
     * A list recording details of all normal compiled methods included in this class sorted by
     * ascending address range. Note that the associated address ranges are disjoint and contiguous.
     */
    private final List<CompiledMethodEntry> compiledEntries = new ArrayList<>();
    /**
     * An index identifying ranges for compiled method which have already been encountered.
     */
    private final EconomicMap<Range, CompiledMethodEntry> compiledMethodIndex = EconomicMap.create();

    /**
     * A list of all files referenced from info associated with this class, including info detailing
     * inline method ranges.
     */
    private final ArrayList<FileEntry> files;
    /**
     * A list of all directories referenced from info associated with this class, including info
     * detailing inline method ranges.
     */
    private final ArrayList<DirEntry> dirs;
    /**
     * An index identifying the file table position of every file referenced from info associated
     * with this class, including info detailing inline method ranges.
     */
    private EconomicMap<FileEntry, Integer> fileIndex;
    /**
     * An index identifying the dir table position of every directory referenced from info
     * associated with this class, including info detailing inline method ranges.
     */
    private EconomicMap<DirEntry, Integer> dirIndex;

    public ClassEntry(String className, FileEntry fileEntry, int size) {
        super(className, size);
        this.fileEntry = fileEntry;
        this.loader = null;
        // file and dir lists/indexes are populated after all DebugInfo API input has
        // been received and are only created on demand
        files = new ArrayList<>();
        dirs = new ArrayList<>();
        // create these on demand using the size of the file and dir lists
        this.fileIndex = null;
        this.dirIndex = null;
    }

    @Override
    public DebugTypeKind typeKind() {
        return DebugTypeKind.INSTANCE;
    }

    @Override
    public void addDebugInfo(DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, DebugContext debugContext) {
        super.addDebugInfo(debugInfoBase, debugTypeInfo, debugContext);
        assert debugTypeInfo.typeName().equals(typeName);
        DebugInstanceTypeInfo debugInstanceTypeInfo = (DebugInstanceTypeInfo) debugTypeInfo;
        /* Add details of super and interface classes */
        ResolvedJavaType superType = debugInstanceTypeInfo.superClass();
        if (debugContext.isLogEnabled()) {
            debugContext.log("typename %s adding super %s%n", typeName, superType != null ? superType.toJavaName() : "");
        }
        if (superType != null) {
            this.superClass = debugInfoBase.lookupClassEntry(superType);
        }
        String loaderName = debugInstanceTypeInfo.loaderName();
        if (!loaderName.isEmpty()) {
            this.loader = debugInfoBase.ensureLoaderEntry(loaderName);
        }
        debugInstanceTypeInfo.interfaces().forEach(interfaceType -> processInterface(interfaceType, debugInfoBase, debugContext));
        /* Add details of fields and field types */
        debugInstanceTypeInfo.fieldInfoProvider().forEach(debugFieldInfo -> this.processField(debugFieldInfo, debugInfoBase, debugContext));
        /* Add details of methods and method types */
        debugInstanceTypeInfo.methodInfoProvider().forEach(debugMethodInfo -> this.processMethod(debugMethodInfo, debugInfoBase, debugContext));
    }

    public CompiledMethodEntry indexPrimary(PrimaryRange primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        assert compiledMethodIndex.get(primary) == null : "repeat of primary range [0x%x, 0x%x]!".formatted(primary.getLo(), primary.getHi());
        CompiledMethodEntry compiledEntry = new CompiledMethodEntry(primary, frameSizeInfos, frameSize, this);
        compiledMethodIndex.put(primary, compiledEntry);
        compiledEntries.add(compiledEntry);
        return compiledEntry;
    }

    public void indexSubRange(SubRange subrange) {
        Range primary = subrange.getPrimary();
        /* The subrange should belong to a primary range. */
        assert primary != null;
        CompiledMethodEntry compiledEntry = compiledMethodIndex.get(primary);
        /* We should already have seen the primary range. */
        assert compiledEntry != null;
        assert compiledEntry.getClassEntry() == this;
    }

    private void indexMethodEntry(MethodEntry methodEntry, ResolvedJavaMethod idMethod) {
        assert methodsIndex.get(idMethod) == null : methodEntry.getSymbolName();
        methods.add(methodEntry);
        methodsIndex.put(idMethod, methodEntry);
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.getFileName();
        } else {
            return "";
        }
    }

    public String getFullFileName() {
        if (fileEntry != null) {
            return fileEntry.getFullName();
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    String getDirName() {
        if (fileEntry != null) {
            return fileEntry.getPathName();
        } else {
            return "";
        }
    }

    public FileEntry getFileEntry() {
        return fileEntry;
    }

    public int getFileIdx() {
        return getFileIdx(this.getFileEntry());
    }

    public int getFileIdx(FileEntry file) {
        if (file == null || fileIndex == null) {
            return 0;
        }
        return fileIndex.get(file);
    }

    public DirEntry getDirEntry(FileEntry file) {
        if (file == null) {
            return null;
        }
        return file.getDirEntry();
    }

    public int getDirIdx(FileEntry file) {
        DirEntry dirEntry = getDirEntry(file);
        return getDirIdx(dirEntry);
    }

    public int getDirIdx(DirEntry dir) {
        if (dir == null || dir.getPathString().isEmpty() || dirIndex == null) {
            return 0;
        }
        return dirIndex.get(dir);
    }

    public String getLoaderId() {
        return (loader != null ? loader.getLoaderId() : "");
    }

    /**
     * Retrieve a stream of all compiled method entries for this class.
     *
     * @return a stream of all compiled method entries for this class.
     */
    public Stream<CompiledMethodEntry> compiledEntries() {
        return compiledEntries.stream();
    }

    protected void processInterface(ResolvedJavaType interfaceType, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        if (debugContext.isLogEnabled()) {
            debugContext.log("typename %s adding interface %s%n", typeName, interfaceType.toJavaName());
        }
        ClassEntry entry = debugInfoBase.lookupClassEntry(interfaceType);
        assert entry instanceof InterfaceClassEntry || (entry instanceof ForeignTypeEntry && this instanceof ForeignTypeEntry);
        InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) entry;
        interfaces.add(interfaceClassEntry);
        interfaceClassEntry.addImplementor(this, debugContext);
    }

    protected MethodEntry processMethod(DebugMethodInfo debugMethodInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String methodName = debugMethodInfo.name();
        int line = debugMethodInfo.line();
        ResolvedJavaType resultType = debugMethodInfo.valueType();
        int modifiers = debugMethodInfo.modifiers();
        DebugLocalInfo[] paramInfos = debugMethodInfo.getParamInfo();
        DebugLocalInfo thisParam = debugMethodInfo.getThisParamInfo();
        int paramCount = paramInfos.length;
        if (debugContext.isLogEnabled()) {
            String resultTypeName = resultType.toJavaName();
            debugContext.log("typename %s adding %s method %s %s(%s)%n",
                            typeName, memberModifiers(modifiers), resultTypeName, methodName, formatParams(paramInfos));
        }
        TypeEntry resultTypeEntry = debugInfoBase.lookupTypeEntry(resultType);
        TypeEntry[] typeEntries = new TypeEntry[paramCount];
        for (int i = 0; i < paramCount; i++) {
            typeEntries[i] = debugInfoBase.lookupTypeEntry(paramInfos[i].valueType());
        }
        /*
         * n.b. the method file may differ from the owning class file when the method is a
         * substitution
         */
        FileEntry methodFileEntry = debugInfoBase.ensureFileEntry(debugMethodInfo);
        MethodEntry methodEntry = new MethodEntry(debugInfoBase, debugMethodInfo, methodFileEntry, line, methodName,
                        this, resultTypeEntry, typeEntries, paramInfos, thisParam);
        indexMethodEntry(methodEntry, debugMethodInfo.idMethod());

        return methodEntry;
    }

    @Override
    protected FieldEntry addField(DebugFieldInfo debugFieldInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        FieldEntry fieldEntry = super.addField(debugFieldInfo, debugInfoBase, debugContext);
        return fieldEntry;
    }

    private static String formatParams(DebugLocalInfo[] paramInfo) {
        if (paramInfo.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paramInfo.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(paramInfo[i].typeName());
            builder.append(' ');
            builder.append(paramInfo[i].name());
        }

        return builder.toString();
    }

    public int compiledEntryCount() {
        return compiledEntries.size();
    }

    public boolean hasCompiledEntries() {
        return compiledEntryCount() != 0;
    }

    public int compiledEntriesBase() {
        assert hasCompiledEntries();
        return compiledEntries.get(0).getPrimary().getLo();
    }

    public ClassEntry getSuperClass() {
        return superClass;
    }

    public MethodEntry ensureMethodEntryForDebugRangeInfo(DebugRangeInfo debugRangeInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {

        MethodEntry methodEntry = methodsIndex.get(debugRangeInfo.idMethod());
        if (methodEntry == null) {
            methodEntry = processMethod(debugRangeInfo, debugInfoBase, debugContext);
        } else {
            methodEntry.updateRangeInfo(debugInfoBase, debugRangeInfo);
        }
        return methodEntry;
    }

    public List<MethodEntry> getMethods() {
        return methods;
    }

    /*
     * Accessors for lo and hi bounds of this class's compiled method code ranges. See comments in
     * class DebugInfoBase for an explanation of the layout of compiled method code.
     */

    /**
     * Retrieve the lowest code section offset for compiled method code belonging to this class. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the lowest code section offset for compiled method code belonging to this class
     */
    public int lowpc() {
        assert hasCompiledEntries();
        return compiledEntries.get(0).getPrimary().getLo();
    }

    /**
     * Retrieve the highest code section offset for compiled method code belonging to this class.
     * The returned value is the offset of the first byte that succeeds the code for that method. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the highest code section offset for compiled method code belonging to this class
     */
    public int hipc() {
        assert hasCompiledEntries();
        return compiledEntries.get(compiledEntries.size() - 1).getPrimary().getHi();
    }

    /**
     * Add a file to the list of files referenced from info associated with this class.
     * 
     * @param file The file to be added.
     */
    public void includeFile(FileEntry file) {
        assert !files.contains(file) : "caller should ensure file is only included once";
        assert fileIndex == null : "cannot include files after index has been created";
        files.add(file);
    }

    /**
     * Add a directory to the list of firectories referenced from info associated with this class.
     * 
     * @param dirEntry The directory to be added.
     */
    public void includeDir(DirEntry dirEntry) {
        assert !dirs.contains(dirEntry) : "caller should ensure dir is only included once";
        assert dirIndex == null : "cannot include dirs after index has been created";
        dirs.add(dirEntry);
    }

    /**
     * Populate the file and directory indexes that track positions in the file and dir tables for
     * this class's line info section.
     */
    public void buildFileAndDirIndexes() {
        // this is a one-off operation
        assert fileIndex == null && dirIndex == null : "file and indexes can only be generated once";
        if (files.isEmpty()) {
            assert dirs.isEmpty() : "should not have included any dirs if we have no files";
        }
        int idx = 1;
        fileIndex = EconomicMap.create(files.size());
        for (FileEntry file : files) {
            fileIndex.put(file, idx++);
        }
        dirIndex = EconomicMap.create(dirs.size());
        idx = 1;
        for (DirEntry dir : dirs) {
            if (!dir.getPathString().isEmpty()) {
                dirIndex.put(dir, idx++);
            } else {
                assert idx == 1;
            }
        }
    }

    /**
     * Retrieve a stream of all files referenced from debug info for this class in line info file
     * table order, starting with the file at index 1.
     * 
     * @return a stream of all referenced files
     */
    public Stream<FileEntry> fileStream() {
        if (!files.isEmpty()) {
            return files.stream();
        } else {
            return Stream.empty();
        }
    }

    /**
     * Retrieve a stream of all directories referenced from debug info for this class in line info
     * directory table order, starting with the directory at index 1.
     *
     * @return a stream of all referenced directories
     */
    public Stream<DirEntry> dirStream() {
        if (!dirs.isEmpty()) {
            return dirs.stream();
        } else {
            return Stream.empty();
        }
    }
}
