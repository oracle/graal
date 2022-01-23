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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFieldInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugInstanceTypeInfo;
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
    protected List<InterfaceClassEntry> interfaces;
    /**
     * Details of the associated file.
     */
    private FileEntry fileEntry;
    /**
     * Details of methods located in this instance.
     */
    protected List<MethodEntry> methods;
    /**
     * An index of all currently known methods keyed by the unique local symbol name of the method.
     */
    private Map<String, MethodEntry> methodsIndex;
    /**
     * A list recording details of all primary ranges included in this class sorted by ascending
     * address range.
     */
    private List<PrimaryEntry> primaryEntries;
    /**
     * An index identifying primary ranges which have already been encountered.
     */
    private Map<Range, PrimaryEntry> primaryIndex;
    /**
     * An index of all primary and secondary files referenced from this class's compilation unit.
     */
    private Map<FileEntry, Integer> localFilesIndex;
    /**
     * A list of the same files.
     */
    private List<FileEntry> localFiles;
    /**
     * An index of all primary and secondary dirs referenced from this class's compilation unit.
     */
    private HashMap<DirEntry, Integer> localDirsIndex;
    /**
     * A list of the same dirs.
     */
    private List<DirEntry> localDirs;
    /**
     * This flag is true iff the entry includes methods that are deopt targets.
     */
    private boolean includesDeoptTarget;

    public ClassEntry(String className, FileEntry fileEntry, int size) {
        super(className, size);
        this.interfaces = new ArrayList<>();
        this.fileEntry = fileEntry;
        this.methods = new ArrayList<>();
        this.methodsIndex = new HashMap<>();
        this.primaryEntries = new ArrayList<>();
        this.primaryIndex = new HashMap<>();
        this.localFiles = new ArrayList<>();
        this.localFilesIndex = new HashMap<>();
        this.localDirs = new ArrayList<>();
        this.localDirsIndex = new HashMap<>();
        if (fileEntry != null) {
            localFiles.add(fileEntry);
            localFilesIndex.put(fileEntry, localFiles.size());
            DirEntry dirEntry = fileEntry.getDirEntry();
            if (dirEntry != null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
        }
    }

    @Override
    public DebugTypeKind typeKind() {
        return DebugTypeKind.INSTANCE;
    }

    @Override
    public void addDebugInfo(DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, DebugContext debugContext) {
        assert TypeEntry.canonicalize(debugTypeInfo.typeName()).equals(typeName);
        DebugInstanceTypeInfo debugInstanceTypeInfo = (DebugInstanceTypeInfo) debugTypeInfo;
        /* Add details of super and interface classes */
        String superName = debugInstanceTypeInfo.superName();
        if (superName != null) {
            superName = TypeEntry.canonicalize(superName);
        }
        debugContext.log("typename %s adding super %s\n", typeName, superName);
        if (superName != null) {
            this.superClass = debugInfoBase.lookupClassEntry(superName);
        }
        debugInstanceTypeInfo.interfaces().forEach(interfaceName -> processInterface(interfaceName, debugInfoBase, debugContext));
        /* Add details of fields and field types */
        debugInstanceTypeInfo.fieldInfoProvider().forEach(debugFieldInfo -> this.processField(debugFieldInfo, debugInfoBase, debugContext));
        /* Add details of methods and method types */
        debugInstanceTypeInfo.methodInfoProvider().forEach(debugMethodInfo -> this.processMethod(debugMethodInfo, debugInfoBase, debugContext));
    }

    public void indexPrimary(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        if (primaryIndex.get(primary) == null) {
            PrimaryEntry primaryEntry = new PrimaryEntry(primary, frameSizeInfos, frameSize, this);
            primaryEntries.add(primaryEntry);
            primaryIndex.put(primary, primaryEntry);
            if (primary.isDeoptTarget()) {
                includesDeoptTarget = true;
            } else {
                /* deopt targets should all come after normal methods */
                assert includesDeoptTarget == false;
            }
            FileEntry primaryFileEntry = primary.getFileEntry();
            if (primaryFileEntry != null) {
                indexLocalFileEntry(primaryFileEntry);
            }
        }
    }

    public void indexSubRange(Range subrange) {
        Range primary = subrange.getPrimary();
        /* The subrange should belong to a primary range. */
        assert primary != null;
        PrimaryEntry primaryEntry = primaryIndex.get(primary);
        /* We should already have seen the primary range. */
        assert primaryEntry != null;
        assert primaryEntry.getClassEntry() == this;
        FileEntry subFileEntry = subrange.getFileEntry();
        if (subFileEntry != null) {
            indexLocalFileEntry(subFileEntry);
        }
    }

    private void indexMethodEntry(MethodEntry methodEntry) {
        String methodName = methodEntry.getSymbolName();
        assert methodsIndex.get(methodName) == null : methodName;
        methods.add(methodEntry);
        methodsIndex.put(methodName, methodEntry);
    }

    private void indexLocalFileEntry(FileEntry localFileEntry) {
        if (localFilesIndex.get(localFileEntry) == null) {
            localFiles.add(localFileEntry);
            localFilesIndex.put(localFileEntry, localFiles.size());
            DirEntry dirEntry = localFileEntry.getDirEntry();
            if (dirEntry != null && localDirsIndex.get(dirEntry) == null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
        }
    }

    public int localDirsIdx(DirEntry dirEntry) {
        if (dirEntry != null) {
            return localDirsIndex.get(dirEntry);
        } else {
            return 0;
        }
    }

    public int localFilesIdx() {
        return localFilesIndex.get(fileEntry);
    }

    public int localFilesIdx(@SuppressWarnings("hiding") FileEntry fileEntry) {
        return localFilesIndex.get(fileEntry);
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.getFileName();
        } else {
            return "";
        }
    }

    @SuppressWarnings("unused")
    String getFullFileName() {
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

    public List<PrimaryEntry> getPrimaryEntries() {
        return primaryEntries;
    }

    @SuppressWarnings("unused")
    public Object primaryIndexFor(Range primaryRange) {
        return primaryIndex.get(primaryRange);
    }

    public List<DirEntry> getLocalDirs() {
        return localDirs;
    }

    public List<FileEntry> getLocalFiles() {
        return localFiles;
    }

    public boolean includesDeoptTarget() {
        return includesDeoptTarget;
    }

    public String getCachePath() {
        if (fileEntry != null) {
            Path cachePath = fileEntry.getCachePath();
            if (cachePath != null) {
                return cachePath.toString();
            }
        }
        return "";
    }

    private void processInterface(String interfaceName, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        debugContext.log("typename %s adding interface %s\n", typeName, interfaceName);
        ClassEntry entry = debugInfoBase.lookupClassEntry(TypeEntry.canonicalize(interfaceName));
        assert entry instanceof InterfaceClassEntry;
        InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) entry;
        interfaces.add(interfaceClassEntry);
        interfaceClassEntry.addImplementor(this, debugContext);
    }

    protected MethodEntry processMethod(DebugMethodInfo debugMethodInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String methodName = debugMethodInfo.name();
        String resultTypeName = TypeEntry.canonicalize(debugMethodInfo.valueType());
        int modifiers = debugMethodInfo.modifiers();
        List<String> paramTypes = debugMethodInfo.paramTypes();
        List<String> paramNames = debugMethodInfo.paramNames();
        assert paramTypes.size() == paramNames.size();
        int paramCount = paramTypes.size();
        debugContext.log("typename %s adding %s method %s %s(%s)\n",
                        typeName, memberModifiers(modifiers), resultTypeName, methodName, formatParams(paramTypes, paramNames));
        TypeEntry resultType = debugInfoBase.lookupTypeEntry(resultTypeName);
        TypeEntry[] paramTypeArray = null;
        String[] paramNameArray = null;
        if (paramCount != 0) {
            paramTypeArray = new TypeEntry[paramCount];
            paramNameArray = new String[paramCount];
            int idx = 0;
            for (String paramTypeName : paramTypes) {
                TypeEntry paramType = debugInfoBase.lookupTypeEntry(TypeEntry.canonicalize(paramTypeName));
                paramTypeArray[idx++] = paramType;
            }
            paramNameArray = paramNames.toArray(paramNameArray);
        }
        /*
         * n.b. the method file may differ from the owning class file when the method is a
         * substitution
         */
        FileEntry methodFileEntry = debugInfoBase.ensureFileEntry(debugMethodInfo);
        MethodEntry methodEntry = new MethodEntry(debugInfoBase, debugMethodInfo, methodFileEntry, methodName,
                        this, resultType, paramTypeArray, paramNameArray);
        indexMethodEntry(methodEntry);

        return methodEntry;
    }

    @Override
    protected FieldEntry addField(DebugFieldInfo debugFieldInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        FieldEntry fieldEntry = super.addField(debugFieldInfo, debugInfoBase, debugContext);
        FileEntry fieldFileEntry = fieldEntry.getFileEntry();
        if (fieldFileEntry != null) {
            indexLocalFileEntry(fieldFileEntry);
        }
        return fieldEntry;
    }

    private static String formatParams(List<String> paramTypes, List<String> paramNames) {
        if (paramNames.size() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String separator = "";
        for (int i = 0; i < paramNames.size(); i++) {
            builder.append(separator);
            builder.append(paramTypes.get(i));
            String paramName = paramNames.get(i);
            if (paramName.length() > 0) {
                builder.append(' ');
                builder.append(paramName);
            }
            separator = ", ";
        }

        return builder.toString();
    }

    public boolean isPrimary() {
        return primaryEntries.size() != 0;
    }

    public ClassEntry getSuperClass() {
        return superClass;
    }

    public MethodEntry ensureMethodEntryForDebugRangeInfo(DebugRangeInfo debugRangeInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {

        MethodEntry methodEntry = methodsIndex.get(debugRangeInfo.symbolNameForMethod());
        if (methodEntry == null) {
            methodEntry = processMethod(debugRangeInfo, debugInfoBase, debugContext);
        } else {
            methodEntry.updateRangeInfo(debugInfoBase, debugRangeInfo);
            /* Ensure that the methodEntry's fileEntry is present in the localsFileIndex */
            FileEntry methodFileEntry = methodEntry.fileEntry;
            if (methodFileEntry != null) {
                indexLocalFileEntry(methodFileEntry);
            }
        }
        return methodEntry;
    }

    public List<MethodEntry> getMethods() {
        return methods;
    }
}
