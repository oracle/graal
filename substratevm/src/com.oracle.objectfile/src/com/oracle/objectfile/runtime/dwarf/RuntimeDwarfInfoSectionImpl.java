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

package com.oracle.objectfile.runtime.dwarf;

import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugPrimitiveTypeInfo;
import com.oracle.objectfile.runtime.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.runtime.debugentry.FileEntry;
import com.oracle.objectfile.runtime.debugentry.MethodEntry;
import com.oracle.objectfile.runtime.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.runtime.debugentry.TypeEntry;
import com.oracle.objectfile.runtime.debugentry.TypedefEntry;
import com.oracle.objectfile.runtime.debugentry.range.PrimaryRange;
import com.oracle.objectfile.runtime.debugentry.range.Range;
import com.oracle.objectfile.runtime.debugentry.range.SubRange;
import com.oracle.objectfile.runtime.dwarf.RuntimeDwarfDebugInfo.AbbrevCode;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfAccess;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfEncoding;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfExpressionOpcode;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfFlag;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfInline;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfLanguage;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfVersion;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.oracle.objectfile.runtime.dwarf.RuntimeDwarfDebugInfo.LANG_ENCODING;

/**
 * Section generator for debug_info section.
 */
public class RuntimeDwarfInfoSectionImpl extends RuntimeDwarfSectionImpl {

    /**
     * An info header section always contains a fixed number of bytes.
     */
    private static final int DIE_HEADER_SIZE = 11;

    /**
     * Normally the offset of DWARF type declarations are tracked using the type/class entry
     * properties but that means they are only available to be read during the second pass when
     * filling in type cross-references. However, we need to use the offset of the void type during
     * the first pass as the target of later-generated foreign pointer types. So, this field saves
     * it up front.
     */
    private int cuStart;

    public RuntimeDwarfInfoSectionImpl(RuntimeDwarfDebugInfo dwarfSections) {
        // debug_info section depends on loc section
        super(dwarfSections, DwarfSectionName.DW_INFO_SECTION, DwarfSectionName.DW_LOC_SECTION);
        // initialize CU start to an invalid value
        cuStart = -1;
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

    DwarfEncoding computeEncoding(int flags, int bitCount) {
        assert bitCount > 0;
        if ((flags & DebugPrimitiveTypeInfo.FLAG_NUMERIC) != 0) {
            if (((flags & DebugPrimitiveTypeInfo.FLAG_INTEGRAL) != 0)) {
                if ((flags & DebugPrimitiveTypeInfo.FLAG_SIGNED) != 0) {
                    switch (bitCount) {
                        case 8:
                            return DwarfEncoding.DW_ATE_signed_char;
                        default:
                            assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                            return DwarfEncoding.DW_ATE_signed;
                    }
                } else {
                    assert bitCount == 16;
                    return DwarfEncoding.DW_ATE_unsigned;
                }
            } else {
                assert bitCount == 32 || bitCount == 64;
                return DwarfEncoding.DW_ATE_float;
            }
        } else {
            assert bitCount == 1;
            return DwarfEncoding.DW_ATE_boolean;
        }
    }

    // generate a single CU for the runtime compiled method
    public int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        pos = writeCU(context, buffer, pos);

        /* Write all primitive types and types referenced from build time debuginfo */
        pos = writeTypes(context, buffer, pos);

        /* Write the runtime compiled method */
        pos = writeMethod(context, compiledMethod(), buffer, pos);

        /* Write abstract inlined methods */
        pos = writeAbstractInlineMethods(context, compiledMethod(), buffer, pos);

        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        patchLength(0, buffer, pos);
        return pos;
    }

    public int writeCU(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeCUHeader(buffer, pos);
        assert pos == p + DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.METHOD_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString(dwarfSections.cuName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String compilationDirectory = uniqueDebugString(dwarfSections.getCachePath());
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);
        long lo = compiledMethod().getPrimary().getLo();
        log(context, "  [0x%08x]     low_pc  0x%x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        long hi = compiledMethod().getPrimary().getHi();
        log(context, "  [0x%08x]     high_pc  0x%x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        int lineIndex = 0; // getLineIndex(compiledMethodEntry);
        log(context, "  [0x%08x]     stmt_list  0x%x", pos, lineIndex);
        return writeLineSectionOffset(lineIndex, buffer, pos);
    }

    public int writeTypes(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        // write primitives
        for (PrimitiveTypeEntry primitiveType : primitiveTypes()) {
            pos = writePrimitiveType(context, primitiveType, buffer, pos);
        }

        // write typedefs
        for (TypedefEntry typedef : typedefs()) {
            pos = writeTypedef(context, typedef, buffer, pos);
        }
        return pos;
    }

    public int writePrimitiveType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() > 0;
        int pos = p;
        log(context, "  [0x%08x] primitive type %s", pos, primitiveTypeEntry.getTypeName());
        /* Record the location of this type entry. */
        setTypeIndex(primitiveTypeEntry, pos);

        AbbrevCode abbrevCode = AbbrevCode.PRIMITIVE_TYPE;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        byte byteSize = (byte) primitiveTypeEntry.getSize();
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) primitiveTypeEntry.getBitCount();
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        DwarfEncoding encoding = computeEncoding(primitiveTypeEntry.getFlags(), bitCount);
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding.value());
        pos = writeAttrEncoding(encoding, buffer, pos);
        String name = uniqueDebugString(primitiveTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeStrSectionOffset(name, buffer, pos);
    }

    public int writeTypedef(DebugContext context, TypedefEntry typedefEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] typedef %s", pos, typedefEntry.getTypeName());
        int typedefIndex = pos;
        AbbrevCode abbrevCode = AbbrevCode.TYPEDEF;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(typedefEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);


        log(context, "  [0x%08x] typedef pointer type %s", pos, typedefEntry.getTypeName());
        /* Record the location of this type entry. */
        setTypeIndex(typedefEntry, pos);
        abbrevCode = AbbrevCode.TYPEDEF_POINTER;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, typedefIndex);
        return writeInfoSectionOffset(typedefIndex, buffer, pos);
    }

    public int writeMethod(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;

        MethodEntry methodEntry = compiledEntry.getPrimary().getMethodEntry();
        String methodKey = uniqueDebugString(methodEntry.getSymbolName());
        setMethodDeclarationIndex(methodEntry, pos);
        int modifiers = methodEntry.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);

        log(context, "  [0x%08x] method location %s::%s", pos, methodEntry.ownerType().getTypeName(), methodEntry.methodName());
        AbbrevCode abbrevCode = (isStatic ? AbbrevCode.METHOD_LOCATION_STATIC : AbbrevCode.METHOD_LOCATION);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString(methodEntry.methodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        Range primary = compiledEntry.getPrimary();
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, primary.getLo());
        pos = writeAttrAddress(primary.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, primary.getHi());
        pos = writeAttrAddress(primary.getHi(), buffer, pos);
        TypeEntry returnType = methodEntry.getValueType();
        int retTypeIdx = getTypeIndex(returnType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, returnType.getTypeName());
        pos = writeInfoSectionOffset(retTypeIdx, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, methodEntry.isDeopt() ? "true" : "false");
        pos = writeFlag((methodEntry.isDeopt() ? DwarfFlag.DW_FLAG_true : DwarfFlag.DW_FLAG_false), buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        int typeIdx = getTypeIndex(methodEntry.ownerType());
        log(context, "  [0x%08x]     containing_type 0x%x (%s)", pos, typeIdx, methodEntry.ownerType().getTypeName());
        pos = writeInfoSectionOffset(typeIdx, buffer, pos); //writeAttrRef4(typeIdx, buffer, pos);
        if (!isStatic) {
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

        HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = primary.getVarRangeMap();
        /* Write method parameter declarations. */
        pos = writeMethodParameterLocations(context, compiledEntry, varRangeMap, primary, 2, buffer, pos);
        /* write method local declarations */
        pos = writeMethodLocalLocations(context, compiledEntry, varRangeMap, primary, 2, buffer, pos);

        if (primary.includesInlineRanges()) {
            /*
             * the method has inlined ranges so write concrete inlined method entries as its
             * children
             */
            pos = generateConcreteInlinedMethods(context, compiledEntry, buffer, pos);
        }

        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }


    private int writeMethodParameterLocations(DebugContext context, CompiledMethodEntry compiledEntry, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
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
            List<SubRange> ranges = varRangeMap.get(thisParamInfo);
            pos = writeMethodLocalLocation(context, range, thisParamInfo, ranges, depth, true, buffer, pos);
        }
        for (int i = 0; i < methodEntry.getParamCount(); i++) {
            DebugLocalInfo paramInfo = methodEntry.getParam(i);
            List<SubRange> ranges = varRangeMap.get(paramInfo);
            pos = writeMethodLocalLocation(context, range, paramInfo, ranges, depth, true, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocations(DebugContext context, CompiledMethodEntry compiledEntry, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
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
            List<SubRange> ranges = varRangeMap.get(localInfo);
            pos = writeMethodLocalLocation(context, range, localInfo, ranges, depth, false, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocation(DebugContext context, Range range, DebugLocalInfo localInfo, List<SubRange> ranges, int depth, boolean isParam, byte[] buffer,
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


        String name = uniqueDebugString(localInfo.name());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        TypeEntry valueType = lookupType(localInfo.valueType());
        int retTypeIdx = getTypeIndex(valueType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, valueType.getTypeName());
        pos = writeInfoSectionOffset(retTypeIdx, buffer, pos);

        if (!localValues.isEmpty()) {
            int locRefAddr = getRangeLocalIndex(range, localInfo);
            log(context, "  [0x%08x]     loc list  0x%x", pos, locRefAddr);
            pos = writeLocSectionOffset(locRefAddr, buffer, pos);
        }
        return pos;
    }

    /**
     * Go through the subranges and generate concrete debug entries for inlined methods.
     */
    private int generateConcreteInlinedMethods(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
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
            pos = writeInlineSubroutine(context, compiledEntry, subrange, depth + 2, buffer, pos);
            HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = subrange.getVarRangeMap();
            // increment depth to account for parameter and method locations
            depth++;
            pos = writeMethodParameterLocations(context, compiledEntry, varRangeMap, subrange, depth + 2, buffer, pos);
            pos = writeMethodLocalLocations(context, compiledEntry, varRangeMap, subrange, depth + 2, buffer, pos);
        }
        // if we just stepped out of a child range write nulls for each step up
        while (depth > 0) {
            pos = writeAttrNull(buffer, pos);
            depth--;
        }
        return pos;
    }

    private int writeInlineSubroutine(DebugContext context, CompiledMethodEntry compiledEntry, SubRange caller, int depth, byte[] buffer, int p) {
        assert !caller.isLeaf();
        // the supplied range covers an inline call and references the caller method entry. its
        // child ranges all reference the same inlined called method. leaf children cover code for
        // that inlined method. non-leaf children cover code for recursively inlined methods.
        // identify the inlined method by looking at the first callee
        Range callee = caller.getFirstCallee();
        MethodEntry methodEntry = callee.getMethodEntry();
        String methodKey = uniqueDebugString(methodEntry.getSymbolName());
        /* the abstract index was written in the method's class entry */
        int abstractOriginIndex = getAbstractInlineMethodIndex(compiledEntry, methodEntry); // getMethodDeclarationIndex(methodEntry);

        int pos = p;
        log(context, "  [0x%08x] concrete inline subroutine [0x%x, 0x%x] %s", pos, caller.getLo(), caller.getHi(), methodKey);

        int callLine = caller.getLine();
        assert callLine >= -1 : callLine;
        int fileIndex = 0;
        final AbbrevCode abbrevCode = AbbrevCode.INLINED_SUBROUTINE;
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, depth, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     abstract_origin  0x%x", pos, abstractOriginIndex);
        pos = writeAttrRef4(abstractOriginIndex, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, caller.getLo());
        pos = writeAttrAddress(caller.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, caller.getHi());
        pos = writeAttrAddress(caller.getHi(), buffer, pos);
        log(context, "  [0x%08x]     call_file  %d", pos, fileIndex);
        pos = writeAttrData4(fileIndex, buffer, pos);
        log(context, "  [0x%08x]     call_line  %d", pos, callLine);
        pos = writeAttrData4(callLine, buffer, pos);
        return pos;
    }

    private int writeAbstractInlineMethods(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        PrimaryRange primary = compiledEntry.getPrimary();
        if (primary.isLeaf()) {
            return pos;
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

            // n.b. class entry used to index the method belongs to the inlining method
            // not the inlined method
            setAbstractInlineMethodIndex(compiledEntry, methodEntry, pos);
            pos = writeAbstractInlineMethod(context, compiledEntry, methodEntry, buffer, pos);
        }
        return pos;
    }

    private int writeAbstractInlineMethod(DebugContext context, CompiledMethodEntry compiledEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] abstract inline method %s", pos, method.methodName());
        AbbrevCode abbrevCode = AbbrevCode.ABSTRACT_INLINE_METHOD;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     inline  0x%x", pos, DwarfInline.DW_INL_inlined.value());
        pos = writeAttrInline(DwarfInline.DW_INL_inlined, buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        String name = uniqueDebugString(method.methodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        TypeEntry returnType = method.getValueType();
        int retTypeIdx = getTypeIndex(returnType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, returnType.getTypeName());
        pos = writeInfoSectionOffset(retTypeIdx, buffer, pos);

        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeAttrRef4(int reference, byte[] buffer, int p) {
        // make sure we have actually started writing a CU
        assert cuStart >= 0;
        // writes a CU-relative offset
        return writeAttrData4(reference - cuStart, buffer, p);
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        /* CU length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_4, buffer, pos);
        /* Abbrev offset. */
        pos = writeAbbrevSectionOffset(0, buffer, pos);
        /* Address size. */
        return writeByte((byte) 8, buffer, pos);
    }

    @SuppressWarnings("unused")
    public int writeAttrString(String value, byte[] buffer, int p) {
        int pos = p;
        return writeUTF8StringBytes(value, buffer, pos);
    }

    public int writeAttrLanguage(DwarfLanguage language, byte[] buffer, int p) {
        int pos = p;
        return writeByte(language.value(), buffer, pos);
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
