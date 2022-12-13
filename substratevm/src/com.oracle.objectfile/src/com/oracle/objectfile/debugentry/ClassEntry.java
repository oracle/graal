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
import java.util.List;
import java.util.stream.Stream;

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
    protected List<InterfaceClassEntry> interfaces;
    /**
     * Details of the associated file.
     */
    private FileEntry fileEntry;
    /**
     * Details of the associated loader.
     */
    private LoaderEntry loader;
    /**
     * Details of methods located in this instance.
     */
    protected List<MethodEntry> methods;
    /**
     * An index of all currently known methods keyed by the unique, associated, identifying
     * ResolvedJavaMethod.
     */
    private EconomicMap<ResolvedJavaMethod, MethodEntry> methodsIndex;
    /**
     * A list recording details of all normal compiled methods included in this class sorted by
     * ascending address range. Note that the associated address ranges are disjoint and contiguous.
     */
    private List<CompiledMethodEntry> normalCompiledEntries;
    /**
     * A list recording details of all deopt fallback compiled methods included in this class sorted
     * by ascending address range. Note that the associated address ranges are disjoint, contiguous
     * and above all ranges for normal compiled methods.
     */
    private List<CompiledMethodEntry> deoptCompiledEntries;
    /**
     * An index identifying ranges for compiled method which have already been encountered, whether
     * normal or deopt fallback methods.
     */
    private EconomicMap<Range, CompiledMethodEntry> compiledMethodIndex;
    /**
     * An index of all primary and secondary files referenced from this class's compilation unit.
     */
    private EconomicMap<FileEntry, Integer> localFilesIndex;
    /**
     * A list of the same files.
     */
    private List<FileEntry> localFiles;
    /**
     * An index of all primary and secondary dirs referenced from this class's compilation unit.
     */
    private EconomicMap<DirEntry, Integer> localDirsIndex;
    /**
     * A list of the same dirs.
     */
    private List<DirEntry> localDirs;

    public ClassEntry(String className, FileEntry fileEntry, int size) {
        super(className, size);
        this.interfaces = new ArrayList<>();
        this.fileEntry = fileEntry;
        this.loader = null;
        this.methods = new ArrayList<>();
        this.methodsIndex = EconomicMap.create();
        this.normalCompiledEntries = new ArrayList<>();
        // deopt methods list is created on demand
        this.deoptCompiledEntries = null;
        this.compiledMethodIndex = EconomicMap.create();
        this.localFiles = new ArrayList<>();
        this.localFilesIndex = EconomicMap.create();
        this.localDirs = new ArrayList<>();
        this.localDirsIndex = EconomicMap.create();
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
        super.addDebugInfo(debugInfoBase, debugTypeInfo, debugContext);
        assert debugTypeInfo.typeName().equals(typeName);
        DebugInstanceTypeInfo debugInstanceTypeInfo = (DebugInstanceTypeInfo) debugTypeInfo;
        /* Add details of super and interface classes */
        ResolvedJavaType superType = debugInstanceTypeInfo.superClass();
        String superName;
        if (superType != null) {
            superName = superType.toJavaName();
        } else {
            superName = "";
        }
        debugContext.log("typename %s adding super %s\n", typeName, superName);
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

    public void indexPrimary(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        if (compiledMethodIndex.get(primary) == null) {
            CompiledMethodEntry compiledEntry = new CompiledMethodEntry(primary, frameSizeInfos, frameSize, this);
            compiledMethodIndex.put(primary, compiledEntry);
            if (primary.isDeoptTarget()) {
                if (deoptCompiledEntries == null) {
                    deoptCompiledEntries = new ArrayList<>();
                }
                deoptCompiledEntries.add(compiledEntry);
            } else {
                normalCompiledEntries.add(compiledEntry);
                /* deopt targets should all come after normal methods */
                assert deoptCompiledEntries == null;
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
        CompiledMethodEntry compiledEntry = compiledMethodIndex.get(primary);
        /* We should already have seen the primary range. */
        assert compiledEntry != null;
        assert compiledEntry.getClassEntry() == this;
        FileEntry subFileEntry = subrange.getFileEntry();
        if (subFileEntry != null) {
            indexLocalFileEntry(subFileEntry);
        }
    }

    private void indexMethodEntry(MethodEntry methodEntry, ResolvedJavaMethod idMethod) {
        assert methodsIndex.get(idMethod) == null : methodEntry.getSymbolName();
        methods.add(methodEntry);
        methodsIndex.put(idMethod, methodEntry);
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

    public String getLoaderId() {
        return (loader != null ? loader.getLoaderId() : "");
    }

    /**
     * Retrieve a stream of all compiled method entries for this class, including both normal and
     * deopt fallback compiled methods.
     * 
     * @return a stream of all compiled method entries for this class.
     */
    public Stream<CompiledMethodEntry> compiledEntries() {
        Stream<CompiledMethodEntry> stream = normalCompiledEntries.stream();
        if (deoptCompiledEntries != null) {
            stream = Stream.concat(stream, deoptCompiledEntries.stream());
        }
        return stream;
    }

    /**
     * Retrieve a stream of all normal compiled method entries for this class, excluding deopt
     * fallback compiled methods.
     * 
     * @return a stream of all normal compiled method entries for this class.
     */
    public Stream<CompiledMethodEntry> normalCompiledEntries() {
        return normalCompiledEntries.stream();
    }

    /**
     * Retrieve a stream of all deopt fallback compiled method entries for this class.
     * 
     * @return a stream of all deopt fallback compiled method entries for this class.
     */
    public Stream<CompiledMethodEntry> deoptCompiledEntries() {
        if (hasDeoptCompiledEntries()) {
            return deoptCompiledEntries.stream();
        } else {
            return Stream.empty();
        }
    }

    public List<DirEntry> getLocalDirs() {
        return localDirs;
    }

    public List<FileEntry> getLocalFiles() {
        return localFiles;
    }

    public boolean hasDeoptCompiledEntries() {
        return deoptCompiledEntries != null;
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

    private void processInterface(ResolvedJavaType interfaceType, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String interfaceName = interfaceType.toJavaName();
        debugContext.log("typename %s adding interface %s\n", typeName, interfaceName);
        ClassEntry entry = debugInfoBase.lookupClassEntry(interfaceType);
        assert entry instanceof InterfaceClassEntry;
        InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) entry;
        interfaces.add(interfaceClassEntry);
        interfaceClassEntry.addImplementor(this, debugContext);
    }

    protected MethodEntry processMethod(DebugMethodInfo debugMethodInfo, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String methodName = debugMethodInfo.name();
        int line = debugMethodInfo.line();
        ResolvedJavaType resultType = debugMethodInfo.valueType();
        String resultTypeName = resultType.toJavaName();
        int modifiers = debugMethodInfo.modifiers();
        DebugLocalInfo[] paramInfos = debugMethodInfo.getParamInfo();
        DebugLocalInfo thisParam = debugMethodInfo.getThisParamInfo();
        int paramCount = paramInfos.length;
        debugContext.log("typename %s adding %s method %s %s(%s)\n",
                        typeName, memberModifiers(modifiers), resultTypeName, methodName, formatParams(paramInfos));
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
        FileEntry fieldFileEntry = fieldEntry.getFileEntry();
        if (fieldFileEntry != null) {
            indexLocalFileEntry(fieldFileEntry);
        }
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

    public boolean hasCompiledEntries() {
        return normalCompiledEntries.size() != 0;
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
        return normalCompiledEntries.get(0).getPrimary().getLo();
    }

    /**
     * Retrieve the lowest code section offset for compiled method code belonging to this class that
     * belongs to a deoptimization fallback compiled method. It is an error to call this for a class
     * entry which has no deoptimization fallback compiled methods.
     * 
     * @return the lowest code section offset for a deoptimization fallback compiled method
     *         belonging to this class.
     */
    public int lowpcDeopt() {
        assert hasCompiledEntries();
        assert hasDeoptCompiledEntries();
        return deoptCompiledEntries.get(0).getPrimary().getLo();
    }

    /**
     * Retrieve the highest code section offset for compiled method code belonging to this class
     * that does not belong to a deoptimization fallback compiled method. The returned value is the
     * offset of the first byte that succeeds the code for that method. It is an error to call this
     * for a class entry which has no compiled methods.
     * 
     * @return the highest code section offset for compiled method code belonging to this class
     */
    public int hipc() {
        assert hasCompiledEntries();
        return normalCompiledEntries.get(normalCompiledEntries.size() - 1).getPrimary().getHi();
    }

    /**
     * Retrieve the highest code section offset for compiled method code belonging to this class
     * that belongs to a deoptimization fallback compiled method. It is an error to call this for a
     * class entry which has no deoptimization fallback compiled methods.
     * 
     * @return the highest code section offset for a deoptimization fallback compiled method
     *         belonging to this class.
     */
    public int hipcDeopt() {
        assert hasCompiledEntries();
        assert hasDeoptCompiledEntries();
        return deoptCompiledEntries.get(deoptCompiledEntries.size() - 1).getPrimary().getHi();
    }
}
