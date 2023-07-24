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

    public ClassEntry(String className, FileEntry fileEntry, int size) {
        super(className, size);
        this.fileEntry = fileEntry;
        this.loader = null;
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
        debugContext.log("typename %s adding super %s%n", typeName, superName);
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
        return fileEntry.getIdx();
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
        return compiledEntries.stream();
    }

    /**
     * Retrieve a stream of all normal compiled method entries for this class, excluding deopt
     * fallback compiled methods.
     *
     * @return a stream of all normal compiled method entries for this class.
     */
    public Stream<CompiledMethodEntry> normalCompiledEntries() {
        return compiledEntries();
    }

    protected void processInterface(ResolvedJavaType interfaceType, DebugInfoBase debugInfoBase, DebugContext debugContext) {
        String interfaceName = interfaceType.toJavaName();
        debugContext.log("typename %s adding interface %s%n", typeName, interfaceName);
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
        String resultTypeName = resultType.toJavaName();
        int modifiers = debugMethodInfo.modifiers();
        DebugLocalInfo[] paramInfos = debugMethodInfo.getParamInfo();
        DebugLocalInfo thisParam = debugMethodInfo.getThisParamInfo();
        int paramCount = paramInfos.length;
        debugContext.log("typename %s adding %s method %s %s(%s)%n",
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
        return compiledEntries.size() != 0;
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
}
