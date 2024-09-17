/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.runtime.dwarf;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicSet;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.runtime.dwarf.RuntimeDwarfDebugInfo.AbbrevCode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfAccess;
import com.oracle.objectfile.elf.dwarf.constants.DwarfEncoding;
import com.oracle.objectfile.elf.dwarf.constants.DwarfFlag;
import com.oracle.objectfile.elf.dwarf.constants.DwarfInline;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLanguage;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfUnitHeader;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;

import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Section generator for debug_info section.
 */
public class RuntimeDwarfInfoSectionImpl extends RuntimeDwarfSectionImpl {
    /**
     * An info header section always contains a fixed number of bytes.
     */
    private static final int CU_DIE_HEADER_SIZE = 12;

    private int unitStart;

    public RuntimeDwarfInfoSectionImpl(RuntimeDwarfDebugInfo dwarfSections) {
        // debug_info section depends on loc section
        super(dwarfSections, DwarfSectionName.DW_INFO_SECTION, DwarfSectionName.DW_LOCLISTS_SECTION);
        // initialize CU start to an invalid value
        unitStart = -1;
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        byte[] buffer = null;
        int len = generateContent(null, buffer);

        buffer = new byte[len];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);
        log(context, "  [0x%08x] DEBUG_INFO", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);

        pos = generateContent(context, buffer);
        assert pos == size;
    }

    public int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        CompiledMethodEntry compiledMethodEntry = compiledMethod();
        ClassEntry classEntry = compiledMethodEntry.getClassEntry();

        // Write a Java class unit header
        int lengthPos = pos;
        log(context, "  [0x%08x] Instance class unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + CU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.CLASS_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(RuntimeDwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = classEntry.getFullFileName();
        if (name == null) {
            name = classEntry.getTypeName().replace('.', '/') + ".java";
        }
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(uniqueDebugString(name), buffer, pos);
        String compilationDirectory = dwarfSections.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);
        long lo = classEntry.lowpc();
        long hi = classEntry.hipc();
        log(context, "  [0x%08x]     low_pc  0x%x", pos, lo);
        pos = writeLong(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%x", pos, hi);
        pos = writeLong(hi, buffer, pos);
        int lineIndex = getLineIndex(classEntry);
        log(context, "  [0x%08x]     stmt_list  0x%x", pos, lineIndex);
        pos = writeLineSectionOffset(lineIndex, buffer, pos);
        int locationListIndex = getLocationListIndex(classEntry);
        log(context, "  [0x%08x]     loclists_base  0x%x", pos, locationListIndex);
        pos = writeLocSectionOffset(locationListIndex, buffer, pos);
        /* if the class has a loader then embed the children in a namespace */
        String loaderId = classEntry.getLoaderId();
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /* Now write the child DIEs starting with the layout and pointer type. */

        // this works for interfaces, foreign types and classes, entry kind specifics are in the
        // type units
        pos = writeSkeletonClassLayout(context, classEntry, compiledMethodEntry, buffer, pos);

        /* Write all compiled code locations */

        pos = writeMethodLocation(context, classEntry, compiledMethodEntry, buffer, pos);

        /* Write abstract inline methods. */

        pos = writeAbstractInlineMethods(context, classEntry, compiledMethodEntry, buffer, pos);

        /* if we opened a namespace then terminate its children */
        if (!loaderId.isEmpty()) {
            pos = writeAttrNull(buffer, pos);
        }

        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeSkeletonClassLayout(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledMethodEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] class layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.CLASS_LAYOUT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = classEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        long typeSignature = classEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type specification 0x%x", pos, typeSignature);
        pos = writeTypeSignature(typeSignature, buffer, pos);

        pos = writeMethodDeclaration(context, classEntry, compiledMethodEntry.getPrimary().getMethodEntry(), false, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeNameSpace(DebugContext context, String id, byte[] buffer, int p) {
        int pos = p;
        String name = uniqueDebugString(id);
        assert !id.isEmpty();
        log(context, "  [0x%08x] namespace %s", pos, name);
        AbbrevCode abbrevCode = AbbrevCode.NAMESPACE;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        return pos;
    }

    private int writeMethodDeclaration(DebugContext context, ClassEntry classEntry, MethodEntry method, boolean isInlined, byte[] buffer, int p) {
        int pos = p;
        String methodKey = method.getSymbolName();
        String linkageName = uniqueDebugString(methodKey);
        setMethodDeclarationIndex(method, pos);
        int modifiers = method.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        log(context, "  [0x%08x] method declaration %s::%s", pos, classEntry.getTypeName(), method.methodName());
        AbbrevCode abbrevCode;
        if (isInlined) {
            abbrevCode = (isStatic ? AbbrevCode.METHOD_DECLARATION_INLINE_STATIC : AbbrevCode.METHOD_DECLARATION_INLINE);
        } else {
            abbrevCode = (isStatic ? AbbrevCode.METHOD_DECLARATION_STATIC : AbbrevCode.METHOD_DECLARATION);
        }
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        if (isInlined) {
            log(context, "  [0x%08x]     inline  0x%x", pos, DwarfInline.DW_INL_inlined.value());
            pos = writeAttrInline(DwarfInline.DW_INL_inlined, buffer, pos);
        }
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString(method.methodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        FileEntry fileEntry = method.getFileEntry();
        if (fileEntry == null) {
            fileEntry = classEntry.getFileEntry();
        }
        assert fileEntry != null;
        int fileIdx = classEntry.getFileIdx(fileEntry);
        log(context, "  [0x%08x]     file 0x%x (%s)", pos, fileIdx, fileEntry.getFullName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        int line = method.getLine();
        log(context, "  [0x%08x]     line 0x%x", pos, line);
        pos = writeAttrData2((short) line, buffer, pos);
        log(context, "  [0x%08x]     linkage_name %s", pos, linkageName);
        pos = writeStrSectionOffset(linkageName, buffer, pos);
        TypeEntry returnType = method.getValueType();
        long retTypeSignature = returnType.getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeSignature, returnType.getTypeName());
        pos = writeTypeSignature(retTypeSignature, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, method.isDeopt() ? "true" : "false");
        pos = writeFlag((method.isDeopt() ? DwarfFlag.DW_FLAG_true : DwarfFlag.DW_FLAG_false), buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        long typeSignature = classEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     containing_type 0x%x (%s)", pos, typeSignature, classEntry.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_DECLARATION || abbrevCode == AbbrevCode.METHOD_DECLARATION_INLINE) {
            /* Record the current position so we can back patch the object pointer. */
            int objectPointerIndex = pos;
            /*
             * Write a dummy ref address to move pos on to where the first parameter gets written.
             */
            pos = writeAttrRef4(0, null, pos);
            /*
             * Now backpatch object pointer slot with current pos, identifying the first parameter.
             */
            log(context, "  [0x%08x]     object_pointer 0x%x", objectPointerIndex, pos);
            writeAttrRef4(pos, buffer, objectPointerIndex);
        }
        /* Write method parameter declarations. */
        pos = writeMethodParameterDeclarations(context, classEntry, method, fileIdx, 3, buffer, pos);
        /* write method local declarations */
        pos = writeMethodLocalDeclarations(context, classEntry, method, fileIdx, 3, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodParameterDeclarations(DebugContext context, ClassEntry classEntry, MethodEntry method, int fileIdx, int level, byte[] buffer, int p) {
        int pos = p;
        int refAddr;
        if (!Modifier.isStatic(method.getModifiers())) {
            refAddr = pos;
            DebugLocalInfo paramInfo = method.getThisParam();
            setMethodLocalIndex(classEntry, method, paramInfo, refAddr);
            pos = writeMethodParameterDeclaration(context, paramInfo, fileIdx, true, level, buffer, pos);
        }
        for (int i = 0; i < method.getParamCount(); i++) {
            refAddr = pos;
            DebugLocalInfo paramInfo = method.getParam(i);
            setMethodLocalIndex(classEntry, method, paramInfo, refAddr);
            pos = writeMethodParameterDeclaration(context, paramInfo, fileIdx, false, level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodParameterDeclaration(DebugContext context, DebugLocalInfo paramInfo, int fileIdx, boolean artificial, int level, byte[] buffer,
                                                int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        AbbrevCode abbrevCode;
        String paramName = paramInfo.name();
        TypeEntry paramType = lookupType(paramInfo.valueType());
        int line = paramInfo.line();
        if (artificial) {
            abbrevCode = AbbrevCode.METHOD_PARAMETER_DECLARATION_1;
        } else if (line >= 0) {
            abbrevCode = AbbrevCode.METHOD_PARAMETER_DECLARATION_2;
        } else {
            abbrevCode = AbbrevCode.METHOD_PARAMETER_DECLARATION_3;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name %s", pos, paramName);
        pos = writeStrSectionOffset(uniqueDebugString(paramName), buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_PARAMETER_DECLARATION_2) {
            log(context, "  [0x%08x]     file 0x%x", pos, fileIdx);
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            log(context, "  [0x%08x]     line 0x%x", pos, line);
            pos = writeAttrData2((short) line, buffer, pos);
        }
        long typeSignature = paramType.getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeSignature, paramType.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_PARAMETER_DECLARATION_1) {
            log(context, "  [0x%08x]     artificial true", pos);
            pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        }
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        return pos;
    }

    private int writeMethodLocalDeclarations(DebugContext context, ClassEntry classEntry, MethodEntry method, int fileIdx, int level, byte[] buffer, int p) {
        int pos = p;
        int refAddr;
        for (int i = 0; i < method.getLocalCount(); i++) {
            refAddr = pos;
            DebugLocalInfo localInfo = method.getLocal(i);
            setMethodLocalIndex(classEntry, method, localInfo, refAddr);
            pos = writeMethodLocalDeclaration(context, localInfo, fileIdx, level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalDeclaration(DebugContext context, DebugLocalInfo paramInfo, int fileIdx, int level, byte[] buffer,
                                            int p) {
        int pos = p;
        log(context, "  [0x%08x] method local declaration", pos);
        AbbrevCode abbrevCode;
        String paramName = paramInfo.name();
        TypeEntry paramType = lookupType(paramInfo.valueType());
        int line = paramInfo.line();
        if (line >= 0) {
            abbrevCode = AbbrevCode.METHOD_LOCAL_DECLARATION_1;
        } else {
            abbrevCode = AbbrevCode.METHOD_LOCAL_DECLARATION_2;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name %s", pos, paramName);
        pos = writeStrSectionOffset(uniqueDebugString(paramName), buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_LOCAL_DECLARATION_1) {
            log(context, "  [0x%08x]     file 0x%x", pos, fileIdx);
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            log(context, "  [0x%08x]     line 0x%x", pos, line);
            pos = writeAttrData2((short) line, buffer, pos);
        }
        long typeSignature = paramType.getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeSignature, paramType.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        return pos;
    }

    private int writeMethodLocation(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledEntry.getPrimary();
        log(context, "  [0x%08x] method location", pos);
        AbbrevCode abbrevCode = AbbrevCode.METHOD_LOCATION;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, primary.getLo());
        pos = writeLong(primary.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, primary.getHi());
        pos = writeLong(primary.getHi(), buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String methodKey = primary.getSymbolName();
        int methodSpecOffset = getMethodDeclarationIndex(primary.getMethodEntry());
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeInfoSectionOffset(methodSpecOffset, buffer, pos);
        HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = primary.getVarRangeMap();
        pos = writeMethodParameterLocations(context, classEntry, varRangeMap, primary, 2, buffer, pos);
        pos = writeMethodLocalLocations(context, classEntry, varRangeMap, primary, 2, buffer, pos);
        if (primary.includesInlineRanges()) {
            /*
             * the method has inlined ranges so write concrete inlined method entries as its
             * children
             */
            pos = generateConcreteInlinedMethods(context, classEntry, compiledEntry, buffer, pos);
        }
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodParameterLocations(DebugContext context, ClassEntry classEntry, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
        int pos = p;
        MethodEntry methodEntry;
        if (range.isPrimary()) {
            methodEntry = range.getMethodEntry();
        } else {
            assert !range.isLeaf() : "should only be looking up var ranges for inlined calls";
            methodEntry = range.getFirstCallee().getMethodEntry();
        }
        if (!Modifier.isStatic(methodEntry.getModifiers())) {
            DebugLocalInfo thisParamInfo = methodEntry.getThisParam();
            int refAddr = getMethodLocalIndex(classEntry, methodEntry, thisParamInfo);
            List<SubRange> ranges = varRangeMap.get(thisParamInfo);
            pos = writeMethodLocalLocation(context, range, thisParamInfo, refAddr, ranges, depth, true, buffer, pos);
        }
        for (int i = 0; i < methodEntry.getParamCount(); i++) {
            DebugLocalInfo paramInfo = methodEntry.getParam(i);
            int refAddr = getMethodLocalIndex(classEntry, methodEntry, paramInfo);
            List<SubRange> ranges = varRangeMap.get(paramInfo);
            pos = writeMethodLocalLocation(context, range, paramInfo, refAddr, ranges, depth, true, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocations(DebugContext context, ClassEntry classEntry, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
        int pos = p;
        MethodEntry methodEntry;
        if (range.isPrimary()) {
            methodEntry = range.getMethodEntry();
        } else {
            assert !range.isLeaf() : "should only be looking up var ranges for inlined calls";
            methodEntry = range.getFirstCallee().getMethodEntry();
        }
        int count = methodEntry.getLocalCount();
        for (int i = 0; i < count; i++) {
            DebugLocalInfo localInfo = methodEntry.getLocal(i);
            int refAddr = getMethodLocalIndex(classEntry, methodEntry, localInfo);
            List<SubRange> ranges = varRangeMap.get(localInfo);
            pos = writeMethodLocalLocation(context, range, localInfo, refAddr, ranges, depth, false, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocation(DebugContext context, Range range, DebugLocalInfo localInfo, int refAddr, List<SubRange> ranges, int depth, boolean isParam, byte[] buffer,
                                         int p) {
        int pos = p;
        log(context, "  [0x%08x] method %s location %s:%s", pos, (isParam ? "parameter" : "local"), localInfo.name(), localInfo.typeName());
        List<DebugLocalValueInfo> localValues = new ArrayList<>();
        for (SubRange subrange : ranges) {
            DebugLocalValueInfo value = subrange.lookupValue(localInfo);
            if (value != null) {
                log(context, "  [0x%08x]     local  %s:%s [0x%x, 0x%x] = %s", pos, value.name(), value.typeName(), subrange.getLo(), subrange.getHi(), formatValue(value));
                switch (value.localKind()) {
                    case REGISTER:
                    case STACKSLOT:
                        localValues.add(value);
                        break;
                    case CONSTANT:
                        JavaConstant constant = value.constantValue();
                        // can only handle primitive or null constants just now
                        if (constant instanceof PrimitiveConstant || constant.getJavaKind() == JavaKind.Object) {
                            localValues.add(value);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        AbbrevCode abbrevCode;
        if (localValues.isEmpty()) {
            abbrevCode = (isParam ? AbbrevCode.METHOD_PARAMETER_LOCATION_1 : AbbrevCode.METHOD_LOCAL_LOCATION_1);
        } else {
            abbrevCode = (isParam ? AbbrevCode.METHOD_PARAMETER_LOCATION_2 : AbbrevCode.METHOD_LOCAL_LOCATION_2);
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, depth, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     specification  0x%x", pos, refAddr);
        pos = writeInfoSectionOffset(refAddr, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_LOCAL_LOCATION_2 ||
                abbrevCode == AbbrevCode.METHOD_PARAMETER_LOCATION_2) {
            int locRefOffset = getRangeLocalIndex(range, localInfo);
            log(context, "  [0x%08x]     loc list  0x%x", pos, locRefOffset);
            pos = writeULEB(locRefOffset, buffer, pos);
        }
        return pos;
    }

    /**
     * Go through the subranges and generate concrete debug entries for inlined methods.
     */
    private int generateConcreteInlinedMethods(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        Range primary = compiledEntry.getPrimary();
        if (primary.isLeaf()) {
            return p;
        }
        int pos = p;
        log(context, "  [0x%08x] concrete entries [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodName());
        int depth = 0;
        Iterator<SubRange> iterator = compiledEntry.topDownRangeIterator();
        while (iterator.hasNext()) {
            SubRange subrange = iterator.next();
            if (subrange.isLeaf()) {
                // we only generate concrete methods for non-leaf entries
                continue;
            }
            // if we just stepped out of a child range write nulls for each step up
            while (depth > subrange.getDepth()) {
                pos = writeAttrNull(buffer, pos);
                depth--;
            }
            depth = subrange.getDepth();
            pos = writeInlineSubroutine(context, classEntry, subrange, depth + 2, buffer, pos);
            HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = subrange.getVarRangeMap();
            // increment depth to account for parameter and method locations
            depth++;
            pos = writeMethodParameterLocations(context, classEntry, varRangeMap, subrange, depth + 2, buffer, pos);
            pos = writeMethodLocalLocations(context, classEntry, varRangeMap, subrange, depth + 2, buffer, pos);
        }
        // if we just stepped out of a child range write nulls for each step up
        while (depth > 0) {
            pos = writeAttrNull(buffer, pos);
            depth--;
        }
        return pos;
    }

    private int writeInlineSubroutine(DebugContext context, ClassEntry classEntry, SubRange caller, int depth, byte[] buffer, int p) {
        assert !caller.isLeaf();
        // the supplied range covers an inline call and references the caller method entry. its
        // child ranges all reference the same inlined called method. leaf children cover code for
        // that inlined method. non-leaf children cover code for recursively inlined methods.
        // identify the inlined method by looking at the first callee
        Range callee = caller.getFirstCallee();
        MethodEntry methodEntry = callee.getMethodEntry();
        String methodKey = methodEntry.getSymbolName();
        /* the abstract index was written in the method's class entry */
        int abstractOriginIndex = getAbstractInlineMethodIndex(classEntry, methodEntry);

        int pos = p;
        log(context, "  [0x%08x] concrete inline subroutine [0x%x, 0x%x] %s", pos, caller.getLo(), caller.getHi(), methodKey);

        int callLine = caller.getLine();
        assert callLine >= -1 : callLine;
        int fileIndex;
        if (callLine == -1) {
            log(context, "  Unable to retrieve call line for inlined method %s", callee.getFullMethodName());
            /* continue with line 0 and fileIndex 1 as we must insert a tree node */
            callLine = 0;
            fileIndex = classEntry.getFileIdx();
        } else {
            FileEntry subFileEntry = caller.getFileEntry();
            if (subFileEntry != null) {
                fileIndex = classEntry.getFileIdx(subFileEntry);
            } else {
                log(context, "  Unable to retrieve caller FileEntry for inlined method %s (caller method %s)", callee.getFullMethodName(), caller.getFullMethodName());
                fileIndex = classEntry.getFileIdx();
            }
        }
        final AbbrevCode abbrevCode = AbbrevCode.INLINED_SUBROUTINE_WITH_CHILDREN;
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, depth, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     abstract_origin  0x%x", pos, abstractOriginIndex);
        pos = writeAttrRef4(abstractOriginIndex, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, caller.getLo());
        pos = writeLong(caller.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, caller.getHi());
        pos = writeLong(caller.getHi(), buffer, pos);
        log(context, "  [0x%08x]     call_file  %d", pos, fileIndex);
        pos = writeAttrData4(fileIndex, buffer, pos);
        log(context, "  [0x%08x]     call_line  %d", pos, callLine);
        pos = writeAttrData4(callLine, buffer, pos);
        return pos;
    }

    private int writeAbstractInlineMethods(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledMethodEntry, byte[] buffer, int p) {
        EconomicSet<MethodEntry> inlinedMethods = collectInlinedMethods(context, compiledMethodEntry, p);
        int pos = p;
        for (MethodEntry methodEntry : inlinedMethods) {
            // n.b. class entry used to index the method belongs to the inlining method
            // not the inlined method
            setAbstractInlineMethodIndex(classEntry, methodEntry, pos);
            // we need the full method declaration for inlined methods
            pos = writeMethodDeclaration(context, classEntry, methodEntry, true, buffer, pos);
            //writeAbstractInlineMethod(context, classEntry, methodEntry, buffer, pos);
        }
        return pos;
    }

    private EconomicSet<MethodEntry> collectInlinedMethods(DebugContext context, CompiledMethodEntry compiledMethodEntry, int p) {
        final EconomicSet<MethodEntry> methods = EconomicSet.create();
        addInlinedMethods(context, compiledMethodEntry, compiledMethodEntry.getPrimary(), methods, p);
        return methods;
    }

    private void addInlinedMethods(DebugContext context, CompiledMethodEntry compiledEntry, Range primary, EconomicSet<MethodEntry> hashSet, int p) {
        if (primary.isLeaf()) {
            return;
        }
        verboseLog(context, "  [0x%08x] collect abstract inlined methods %s", p, primary.getFullMethodName());
        Iterator<SubRange> iterator = compiledEntry.topDownRangeIterator();
        while (iterator.hasNext()) {
            SubRange subrange = iterator.next();
            if (subrange.isLeaf()) {
                // we only generate abstract inline methods for non-leaf entries
                continue;
            }
            // the subrange covers an inline call and references the caller method entry. its
            // child ranges all reference the same inlined called method. leaf children cover code
            // for
            // that inlined method. non-leaf children cover code for recursively inlined methods.
            // identify the inlined method by looking at the first callee
            Range callee = subrange.getFirstCallee();
            MethodEntry methodEntry = callee.getMethodEntry();
            if (hashSet.add(methodEntry)) {
                verboseLog(context, "  [0x%08x]   add abstract inlined method %s", p, methodEntry.getSymbolName());
            }
        }
    }

    private int writeAbstractInlineMethod(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] abstract inline method %s::%s", pos, classEntry.getTypeName(), method.methodName());
        AbbrevCode abbrevCode = AbbrevCode.NULL;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     inline  0x%x", pos, DwarfInline.DW_INL_inlined.value());
        pos = writeAttrInline(DwarfInline.DW_INL_inlined, buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        int methodSpecOffset = getMethodDeclarationIndex(method);
        log(context, "  [0x%08x]     specification  0x%x", pos, methodSpecOffset);
        pos = writeInfoSectionOffset(methodSpecOffset, buffer, pos);
        FileEntry fileEntry = method.getFileEntry();
        if (fileEntry == null) {
            fileEntry = classEntry.getFileEntry();
        }
        assert fileEntry != null;
        int fileIdx = classEntry.getFileIdx(fileEntry);
        int level = 3;
        // n.b. class entry used to index the params belongs to the inlining method
        // not the inlined method
        pos = writeMethodParameterDeclarations(context, classEntry, method, fileIdx, level, buffer, pos);
        pos = writeMethodLocalDeclarations(context, classEntry, method, fileIdx, level, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeAttrRef4(int reference, byte[] buffer, int p) {
        // make sure we have actually started writing a CU
        assert unitStart >= 0;
        // writes a CU-relative offset
        return writeAttrData4(reference - unitStart, buffer, p);
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        unitStart = pos;
        /* CU length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_5, buffer, pos);
        /* unit type */
        pos = writeDwarfUnitHeader(DwarfUnitHeader.DW_UT_compile, buffer, pos);
        /* Address size. */
        pos = writeByte((byte) 8, buffer, pos);
        /* Abbrev offset. */
        return writeAbbrevSectionOffset(0, buffer, pos);
    }

    @SuppressWarnings("unused")
    public int writeAttrString(String value, byte[] buffer, int p) {
        return writeUTF8StringBytes(value, buffer, p);
    }

    public int writeAttrLanguage(DwarfLanguage language, byte[] buffer, int p) {
        return writeByte(language.value(), buffer, p);
    }

    public int writeAttrEncoding(DwarfEncoding encoding, byte[] buffer, int p) {
        return writeByte(encoding.value(), buffer, p);
    }

    public int writeAttrInline(DwarfInline inline, byte[] buffer, int p) {
        return writeByte(inline.value(), buffer, p);
    }

    public int writeAttrAccessibility(int modifiers, byte[] buffer, int p) {
        DwarfAccess access;
        if (Modifier.isPublic(modifiers)) {
            access = DwarfAccess.DW_ACCESS_public;
        } else if (Modifier.isProtected(modifiers)) {
            access = DwarfAccess.DW_ACCESS_protected;
        } else if (Modifier.isPrivate(modifiers)) {
            access = DwarfAccess.DW_ACCESS_private;
        } else {
            /* Actually package private -- make it public for now. */
            access = DwarfAccess.DW_ACCESS_public;
        }
        return writeByte(access.value(), buffer, p);
    }
}
