/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.elf.dwarf;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.elf.ELFObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_INTEGRAL;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_NUMERIC;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_SIGNED;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_data_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_builtin_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_unit1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_unit2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration3;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_header_field;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_implementor;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_declaration1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1;
// import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration3;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_object_header;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_static_field_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_super_reference;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_void_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ACCESS_private;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ACCESS_protected;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ACCESS_public;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ATE_boolean;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ATE_float;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ATE_signed;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ATE_signed_char;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ATE_unsigned;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FLAG_true;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_INFO_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_LANG_Java;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_OP_addr;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_OP_breg0;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_VERSION_4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.TEXT_SECTION_NAME;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * The name of a special DWARF struct type used to model an object header.
     */
    public static final String OBJECT_HEADER_STRUCT_NAME = "_objhdr";

    /**
     * The name of a special DWARF struct type used to model an array header.
     */
    public static final String ARRAY_HEADER_STRUCT_NAME = "_arrhdr";

    /**
     * an info header section always contains a fixed number of bytes.
     */
    private static final int DW_DIE_HEADER_SIZE = 11;

    public DwarfInfoSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_INFO_SECTION_NAME;
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        // order all content decisions after all size decisions by
        // making info section content depend on abbrev section size
        String abbrevSectionName = dwarfSections.getAbbrevSectionImpl().getSectionName();
        ELFObjectFile.ELFSection abbrevSection = (ELFObjectFile.ELFSection) getElement().getOwner().elementForName(abbrevSectionName);
        LayoutDecision sizeDecision = decisions.get(abbrevSection).getDecision(LayoutDecision.Kind.SIZE);
        deps.add(BuildDependency.createOrGet(ourContent, sizeDecision));
        return deps;
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

    byte computeEncoding(int flags, int bitCount) {
        assert bitCount > 0;
        if ((flags & FLAG_NUMERIC) != 0) {
            if (((flags & FLAG_INTEGRAL) != 0)) {
                if ((flags & FLAG_SIGNED) != 0) {
                    switch (bitCount) {
                        case 8:
                            return DW_ATE_signed_char;
                        default:
                            assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                            return DW_ATE_signed;
                    }
                } else {
                    assert bitCount == 16;
                    return DW_ATE_unsigned; // should be UTF???
                }
            } else {
                assert bitCount == 32 || bitCount == 64;
                return DW_ATE_float;
            }
        } else {
            assert bitCount == 1;
            return DW_ATE_boolean;
        }
    }

    public int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;
        pos = writeBuiltInUnit(context, buffer, pos);

        // write entries for all the types known to the generator

        // write class units for non-primary classes i.e. ones which
        // don't have associated methods

        pos = writeNonPrimaryClasses(context, buffer, pos);

        // write class units for primary classes in increasing order
        // of method address

        pos = writePrimaryClasses(context, buffer, pos);

        // write class units for array types

        pos = writeArrayTypes(context, buffer, pos);

        /*
         * write extra special CUs for deopt targets. these are written out of line from the class
         * because they are compiled later and hence inhabit a range that extends beyond the normal
         * method address range.
         */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.includesDeoptTarget()) {
                /*
                 * Save the offset of this file's CU so it can be used when writing the aranges
                 * section.
                 */
                setDeoptCUIndex(classEntry, pos);
                int lengthPos = pos;
                pos = writeCUHeader(buffer, pos);
                log(context, "  [0x%08x] Compilation Unit (deopt targets)", pos);
                assert pos == lengthPos + DW_DIE_HEADER_SIZE;
                pos = writeDeoptMethodsCU(context, classEntry, buffer, pos);
                /*
                 * Backpatch length at lengthPos (excluding length field).
                 */
                patchLength(lengthPos, buffer, pos);
            }
        }

        return pos;
    }

    public int writeBuiltInUnit(DebugContext context, byte[] buffer, int pos) {
        int lengthPos = pos;
        log(context, "  [0x%08x] <0> builtin unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        int abbrevCode = DW_ABBREV_CODE_builtin_unit;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);

        // write child entries for basic Java types

        pos = getTypes().filter(TypeEntry::isPrimitive).reduce(pos,
                        (p, typeEntry) -> {
                            PrimitiveTypeEntry primitiveTypeEntry = (PrimitiveTypeEntry) typeEntry;
                            if (primitiveTypeEntry.getBitCount() > 0) {
                                return writePrimitiveType(context, primitiveTypeEntry, buffer, p);
                            } else {
                                return writeVoidType(context, primitiveTypeEntry, buffer, p);
                            }
                        },
                        (oldpos, newpos) -> newpos);

        // write child entries for object header and array header structs

        pos = getTypes().filter(TypeEntry::isHeader).reduce(pos,
                        (p, typeEntry) -> {
                            HeaderTypeEntry headerTypeEntry = (HeaderTypeEntry) typeEntry;
                            return writeHeaderType(context, headerTypeEntry, buffer, p);
                        },
                        (oldpos, newpos) -> newpos);

        // terminate with null entry

        pos = writeAttrNull(buffer, pos);

        // fix up the CU length

        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    public int writePrimitiveType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() > 0;
        int pos = p;
        log(context, "  [0x%08x] primitive type %s", pos, primitiveTypeEntry.getTypeName());
        // record the location of this type entry
        setTypeIndex(primitiveTypeEntry, pos);
        int abbrevCode = DW_ABBREV_CODE_primitive_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        byte byteSize = (byte) primitiveTypeEntry.getSize();
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) primitiveTypeEntry.getBitCount();
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        byte encoding = computeEncoding(primitiveTypeEntry.getFlags(), bitCount);
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding);
        pos = writeAttrData1(encoding, buffer, pos);
        String name = primitiveTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeAttrStrp(name, buffer, pos);
    }

    public int writeVoidType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() == 0;
        int pos = p;
        log(context, "  [0x%08x] primitive type void", pos);
        // record the location of this type entry
        setTypeIndex(primitiveTypeEntry, pos);
        int abbrevCode = DW_ABBREV_CODE_void_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = primitiveTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeAttrStrp(name, buffer, pos);
    }

    public int writeHeaderType(DebugContext context, HeaderTypeEntry headerTypeEntry, byte[] buffer, int p) {
        int pos = p;
        String name = headerTypeEntry.getTypeName();
        byte size = (byte) headerTypeEntry.getSize();
        log(context, "  [0x%08x] header type %s", pos, name);
        // record the location of this type entry
        setTypeIndex(headerTypeEntry, pos);
        int abbrevCode = DW_ABBREV_CODE_object_header;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData1(size, buffer, pos);

        pos = writeHeaderFields(context, headerTypeEntry, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeHeaderFields(DebugContext context, HeaderTypeEntry headerTypeEntry, byte[] buffer, int p) {
        return headerTypeEntry.fields().reduce(p,
                        (pos, fieldEntry) -> writeHeaderField(context, fieldEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private int writeHeaderField(DebugContext context, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        String fieldName = fieldEntry.fieldName();
        TypeEntry valueType = fieldEntry.getValueType();
        String valueTypeName = valueType.getTypeName();
        int valueTypeIdx = getTypeIndex(valueTypeName);
        log(context, "  [0x%08x] header field", pos);
        int abbrevCode = DW_ABBREV_CODE_header_field;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeAttrStrp(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, valueTypeIdx, valueTypeName);
        pos = writeAttrRefAddr(valueTypeIdx, buffer, pos);
        byte offset = (byte) fieldEntry.getOffset();
        int size = fieldEntry.getSize();
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData1(offset, buffer, pos);
        int modifiers = fieldEntry.getModifiers();
        log(context, "  [0x%08x]     modifiers %s", pos, fieldEntry.getModifiersString());
        return writeAttrAccessibility(modifiers, buffer, pos);
    }

    private int writeNonPrimaryClasses(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] non primary classes", pos);
        return getTypes().filter(TypeEntry::isClass).reduce(pos,
                        (p, typeEntry) -> {
                            ClassEntry classEntry = (ClassEntry) typeEntry;
                            return (classEntry.isPrimary() ? p : writeNonPrimaryClassUnit(context, classEntry, buffer, p));
                        },
                        (oldpos, newpos) -> newpos);

    }

    private int writeNonPrimaryClassUnit(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        setCUIndex(classEntry, pos);
        int lengthPos = pos;
        log(context, "  [0x%08x] non primary class unit %s", pos, classEntry.getTypeName());
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        // non-primary classes have no compiled methods so they also have no line section entry
        int abbrevCode = DW_ABBREV_CODE_class_unit2;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        // lo and hi should really be optional
        int lo = 0;
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        int hi = 0;
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        // n.b. there is no need to write a stmt_list (line section idx) for this class unit
        // as the class has no code

        // now write the child DIEs starting with the layout and pointer type

        if (classEntry.isInterface()) {
            InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) classEntry;
            pos = writeInterfaceLayout(context, interfaceClassEntry, buffer, pos);
            pos = writeInterfaceType(context, interfaceClassEntry, buffer, pos);
        } else {
            pos = writeClassLayout(context, classEntry, buffer, pos);
            pos = writeClassType(context, classEntry, buffer, pos);
        }

        // for a non-primary there are no method definitions to write

        // write all static field definitions

        pos = writeStaticFieldLocations(context, classEntry, buffer, pos);

        // terminate children with null entry

        pos = writeAttrNull(buffer, pos);

        // fix up the CU length

        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    private int writePrimaryClasses(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] primary classes", pos);
        return getTypes().filter(TypeEntry::isClass).reduce(pos,
                        (p, typeEntry) -> {
                            ClassEntry classEntry = (ClassEntry) typeEntry;
                            return (classEntry.isPrimary() ? writePrimaryClassUnit(context, classEntry, buffer, p) : p);
                        },
                        (oldpos, newpos) -> newpos);
    }

    private int writePrimaryClassUnit(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        int lineIndex = getLineIndex(classEntry);
        String fileName = classEntry.getFileName();
        // primary classes only have a line section entry if they have an associated file
        int abbrevCode = (fileName.length() > 0 ? DW_ABBREV_CODE_class_unit1 : DW_ABBREV_CODE_class_unit2);
        setCUIndex(classEntry, pos);
        int lengthPos = pos;
        log(context, "  [0x%08x] primary class unit %s", pos, classEntry.getTypeName());
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fileName), fileName);
        pos = writeAttrStrp(fileName, buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        // specify hi and lo for the compile unit which means we also need to ensure methods
        // within it are listed in ascending address order
        int lo = findLo(classPrimaryEntries, false);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        int hi = findHi(classPrimaryEntries, classEntry.includesDeoptTarget(), false);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        // only write stmt_list if the entry actually has line number info
        if (abbrevCode == DW_ABBREV_CODE_class_unit1) {
            log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
            pos = writeAttrData4(lineIndex, buffer, pos);
        }

        // now write the child DIEs starting with the layout and pointer type

        if (classEntry.isInterface()) {
            InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) classEntry;
            pos = writeInterfaceLayout(context, interfaceClassEntry, buffer, pos);
            pos = writeInterfaceType(context, interfaceClassEntry, buffer, pos);
        } else {
            pos = writeClassLayout(context, classEntry, buffer, pos);
            pos = writeClassType(context, classEntry, buffer, pos);
        }

        // write all method locations

        pos = writeMethodLocations(context, classEntry, buffer, pos);

        // write all static field definitions

        pos = writeStaticFieldLocations(context, classEntry, buffer, pos);

        // terminate children with null entry

        pos = writeAttrNull(buffer, pos);

        // fix up the CU length

        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    private int writeClassLayout(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        setLayoutIndex(classEntry, pos);
        log(context, "  [0x%08x] class layout", pos);
        int abbrevCode = DW_ABBREV_CODE_class_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString("_" + classEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        int size = classEntry.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        int fileIdx = classEntry.localFilesIdx(classEntry.getFileEntry());
        log(context, "  [0x%08x]     file  0x%x (%s)", pos, fileIdx, classEntry.getFileName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        ClassEntry superClassEntry = classEntry.getSuperClass();
        // n.b. the containing_type attribute is not strict DWARF but gdb expects it
        // we also add an inheritance member with the same info
        int superTypeOffset;
        String superName;
        if (superClassEntry != null) {
            // inherit layout from super class
            superName = superClassEntry.getTypeName();
            superTypeOffset = getLayoutIndex(superClassEntry);
        } else {
            // inherit layout from object header
            superName = OBJECT_HEADER_STRUCT_NAME;
            superTypeOffset = getTypeIndex(superName);
        }
        log(context, "  [0x%08x]     containing_type  0x%x (%s)", pos, superTypeOffset, superName);
        pos = writeAttrRefAddr(superTypeOffset, buffer, pos);
        pos = writeSuperReference(context, superTypeOffset, superName, buffer, pos);
        pos = writeFields(context, classEntry, buffer, pos);
        pos = writeMethodDeclarations(context, classEntry, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeSuperReference(DebugContext context, int superTypeOffset, String superName, byte[] buffer, int pos) {
        log(context, "  [0x%08x] super reference", pos);
        int abbrevCode = DW_ABBREV_CODE_super_reference;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, superTypeOffset, superName);
        pos = writeAttrRefAddr(superTypeOffset, buffer, pos);
        // parent layout is embedded at start of object
        log(context, "  [0x%08x]     data_member_location (super) 0x%x", pos, 0);
        pos = writeAttrData1((byte) 0, buffer, pos);
        log(context, "  [0x%08x]     modifiers public", pos);
        int modifiers = Modifier.PUBLIC;
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeFields(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        return classEntry.fields().filter(DwarfInfoSectionImpl::isManifestedField).reduce(p,
                        (pos, fieldEntry) -> writeField(context, classEntry, fieldEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private static boolean isManifestedField(FieldEntry fieldEntry) {
        return fieldEntry.getOffset() >= 0;
    }

    private int writeField(DebugContext context, ClassEntry classEntry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        int modifiers = fieldEntry.getModifiers();
        boolean hasFile = classEntry.getFileName().length() > 0;
        log(context, "  [0x%08x] field definition", pos);
        int abbrevCode;
        boolean isStatic = Modifier.isStatic(modifiers);
        if (!isStatic) {
            if (!hasFile) {
                abbrevCode = DW_ABBREV_CODE_field_declaration1;
            } else {
                abbrevCode = DW_ABBREV_CODE_field_declaration2;
            }
        } else {
            if (!hasFile) {
                abbrevCode = DW_ABBREV_CODE_field_declaration3;
            } else {
                abbrevCode = DW_ABBREV_CODE_field_declaration4;
            }
            // record the position of the declaration to use when we write the definition
            setFieldDeclarationIndex(classEntry, fieldEntry.fieldName(), pos);
        }
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = fieldEntry.fieldName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        // we may not have a file and line for a field
        if (hasFile) {
            int fileIdx = classEntry.localFilesIdx(classEntry.getFileEntry());
            log(context, "  [0x%08x]     filename  0x%x (%s)", pos, fileIdx, classEntry.getFileName());
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            // at present we definitely don't have line numbers
        }
        String valueTypeName = fieldEntry.getValueType().getTypeName();
        // static fields never store compressed values instance fields may do
        int typeIdx = getTypeIndex(valueTypeName);
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeIdx, valueTypeName);
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (!isStatic) {
            int memberOffset = fieldEntry.getOffset();
            log(context, "  [0x%08x]     member offset 0x%x", pos, memberOffset);
            pos = writeAttrData2((short) memberOffset, buffer, pos);
        }
        log(context, "  [0x%08x]     accessibility %s", pos, fieldEntry.getModifiersString());
        pos = writeAttrAccessibility(fieldEntry.getModifiers(), buffer, pos);
        // static fields are only declared here and are external
        if (isStatic) {
            log(context, "  [0x%08x]     external(true)", pos);
            pos = writeFlag((byte) 1, buffer, pos);
            log(context, "  [0x%08x]     definition(true)", pos);
            pos = writeFlag((byte) 1, buffer, pos);
        }
        return pos;
    }

    private int writeMethodDeclarations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            Range range = primaryEntry.getPrimary();
            // declare all methods including deopt targets even though they are written in separate
            // CUs.
            pos = writeMethodDeclaration(context, classEntry, range, buffer, pos);
        }

        return pos;
    }

    private int writeMethodDeclaration(DebugContext context, ClassEntry classEntry, Range range, byte[] buffer, int p) {
        int pos = p;
        String methodKey = range.getFullMethodNameWithParamsAndReturnType();
        setMethodDeclarationIndex(classEntry, methodKey, pos);
        int modifiers = range.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        log(context, "  [0x%08x] method declaration %s", pos, methodKey);
        int abbrevCode = (isStatic ? DW_ABBREV_CODE_method_declaration2 : DW_ABBREV_CODE_method_declaration1);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        String name = uniqueDebugString(range.getMethodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        int fileIdx = classEntry.localFilesIdx(range.getFileEntry());
        log(context, "  [0x%08x]     file 0x%x (%s)", pos, fileIdx, range.getFileEntry().getFullName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        String returnTypeName = range.getMethodReturnTypeName();
        int retTypeIdx = getTypeIndex(returnTypeName);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, returnTypeName);
        pos = writeAttrRefAddr(retTypeIdx, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, range.isDeoptTarget() ? "true" : "false");
        pos = writeFlag((range.isDeoptTarget() ? (byte) 1 : (byte) 0), buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        int typeIdx = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     containing_type 0x%x (%s)", pos, typeIdx, classEntry.getTypeName());
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_method_declaration1) {
            // record the current position so we can back patch the object pointer
            int objectPointerIndex = pos;
            // write a dummy ref address to move pos on to where the first parameter gets written
            pos = writeAttrRefAddr(0, buffer, pos);
            // now backpatch object pointer slot with current pos, identifying the first parameter
            log(context, "  [0x%08x]     object_pointer 0x%x", objectPointerIndex, pos);
            writeAttrRefAddr(pos, buffer, objectPointerIndex);
        }
        // write method parameter declarations
        pos = writeMethodParameterDeclarations(context, classEntry, range, true, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodParameterDeclarations(DebugContext context, ClassEntry classEntry, Range range, boolean isSpecification, byte[] buffer, int p) {
        int pos = p;
        if (!Modifier.isStatic(range.getModifiers())) {
            pos = writeMethodParameterDeclaration(context, "this", classEntry.getTypeName(), true, isSpecification, buffer, pos);
        }
        String paramsString = range.getParamSignature();
        if (!paramsString.isEmpty()) {
            String[] paramTypes = paramsString.split(",");
            for (int i = 0; i < paramTypes.length; i++) {
                String paramName = uniqueDebugString("");
                String paramTypeName = paramTypes[i].trim();
                FileEntry fileEntry = range.getFileEntry();
                if (fileEntry != null) {
                    pos = writeMethodParameterDeclaration(context, paramName, paramTypeName, false, isSpecification, buffer, pos);
                } else {
                    pos = writeMethodParameterDeclaration(context, paramTypeName, paramTypeName, false, isSpecification, buffer, pos);
                }
            }
        }
        return pos;
    }

    private int writeMethodParameterDeclaration(DebugContext context, String paramName, String paramTypeName, boolean artificial, boolean isSpecification, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        int abbrevCode;
        int level = (isSpecification ? 3 : 2);
        if (artificial) {
            abbrevCode = DW_ABBREV_CODE_method_parameter_declaration1;
        } else {
            abbrevCode = DW_ABBREV_CODE_method_parameter_declaration3;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // an artificial 'this' parameter has to be typed using the raw pointer type.
        // other parameters can be typed using the typedef that retains the Java type name
        int typeIdx = (artificial ? getPointerIndex(paramTypeName) : getTypeIndex(paramTypeName));
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeIdx, paramTypeName);
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_method_parameter_declaration1) {
            log(context, "  [0x%08x]     artificial true", pos);
            pos = writeFlag((byte) 1, buffer, pos);
        }
        return pos;
    }

    private int writeInterfaceLayout(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        int pos = p;
        int layoutOffset = pos;
        setLayoutIndex(interfaceClassEntry, layoutOffset);
        log(context, "  [0x%08x] interface layout", pos);
        int abbrevCode = DW_ABBREV_CODE_interface_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString("_" + interfaceClassEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);

        /*
         * now write references to all class layouts that implement this interface
         */
        pos = writeInterfaceImplementors(context, interfaceClassEntry, buffer, pos);
        pos = writeMethodDeclarations(context, interfaceClassEntry, buffer, pos);

        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeInterfaceImplementors(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        return interfaceClassEntry.implementors().reduce(p,
                        (pos, classEntry) -> writeInterfaceImplementor(context, classEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private int writeInterfaceImplementor(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] interface implementor", pos);
        int abbrevCode = DW_ABBREV_CODE_interface_implementor;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString("_" + classEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        int layoutOffset = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, layoutOffset, classEntry.getTypeName());
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);
        int modifiers = Modifier.PUBLIC;
        log(context, "  [0x%08x]     modifiers %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeClassType(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        String name = classEntry.getTypeName();
        int pointerTypeOffset = pos;

        // define a pointer type referring to the underlying layout
        setPointerIndex(classEntry, pos);
        log(context, "  [0x%08x] class pointer type", pos);
        int abbrevCode = DW_ABBREV_CODE_class_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     byte_size 0x%x", pos, 8);
        pos = writeAttrData1((byte) 8, buffer, pos);
        int layoutOffset = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);

        // now write a typedef to name the pointer type and use it as the defining type
        // for the Java class type name
        setTypeIndex(classEntry, pos);
        log(context, "  [0x%08x] class pointer typedef", pos);
        abbrevCode = DW_ABBREV_CODE_class_typedef;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        log(context, "  [0x%08x]     type  (typedef) 0x%x (%s)", pos, pointerTypeOffset, name);
        pos = writeAttrRefAddr(pointerTypeOffset, buffer, pos);

        return pos;
    }

    private int writeInterfaceType(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        int pos = p;
        String name = interfaceClassEntry.getTypeName();
        int pointerTypeOffset = pos;

        // define a pointer type referring to the underlying layout
        setPointerIndex(interfaceClassEntry, pos);
        log(context, "  [0x%08x] interface pointer type", pos);
        int abbrevCode = DW_ABBREV_CODE_interface_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     byte_size 0x%x", pos, 8);
        pos = writeAttrData1((byte) 8, buffer, pos);
        int layoutOffset = getLayoutIndex(interfaceClassEntry);
        log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);
        // now write a typedef to name the pointer type and use it as the defining type
        // for the Java array type name

        setTypeIndex(interfaceClassEntry, pos);
        log(context, "  [0x%08x] interface pointer typedef", pos);
        abbrevCode = DW_ABBREV_CODE_interface_typedef;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        log(context, "  [0x%08x]     type  (typedef) 0x%x (%s)", pos, pointerTypeOffset, name);
        pos = writeAttrRefAddr(pointerTypeOffset, buffer, pos);

        return pos;
    }

    private int writeMethodLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            Range range = primaryEntry.getPrimary();
            if (!range.isDeoptTarget()) {
                pos = writeMethodLocation(context, classEntry, range, buffer, pos);
            }
        }

        return pos;
    }

    private int writeStaticFieldLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        // only write locations for static fields that have an offset greater than 0.
        // a negative offset indicates that the field has been folded into code as
        // an unmaterialized constant.
        return classEntry.fields().filter(DwarfInfoSectionImpl::isManifestedStaticField).reduce(p,
                        (pos, fieldEntry) -> writeStaticFieldLocation(context, classEntry, fieldEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private static boolean isManifestedStaticField(FieldEntry fieldEntry) {
        return Modifier.isStatic(fieldEntry.getModifiers()) && fieldEntry.getOffset() >= 0;
    }

    private int writeStaticFieldLocation(DebugContext context, ClassEntry classEntry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        String fieldName = fieldEntry.fieldName();
        int fieldDefinitionOffset = getFieldDeclarationIndex(classEntry, fieldName);
        log(context, "  [0x%08x] static field location %s.%s", pos, classEntry.getTypeName(), fieldName);
        int abbrevCode = DW_ABBREV_CODE_static_field_location;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     specification  0x%x", pos, fieldDefinitionOffset);
        pos = writeAttrRefAddr(fieldDefinitionOffset, buffer, pos);
        // field offset needs to be relocated relative to static primitive or static object base
        int offset = fieldEntry.getOffset();
        log(context, "  [0x%08x]     location  heapbase + 0x%x (%s)", pos, offset, (fieldEntry.getValueType().isPrimitive() ? "primitive" : "object"));
        pos = writeHeapLocation(offset, buffer, pos);
        return pos;
    }

    private int writeHeapLocation(int offset, byte[] buffer, int p) {
        int pos = p;
        if (dwarfSections.useHeapBase()) {
            // write a location rebasing the offset relative to the heapbase register
            byte regOp = (byte) (DW_OP_breg0 + dwarfSections.getHeapbaseRegister());
            // we have to size the DWARF expression by writing it to the scratch buffer
            // so we can write its size as a ULEB before the expression itself
            int size = putByte(regOp, scratch, 0) + putSLEB(offset, scratch, 0);
            if (buffer == null) {
                // add ULEB size to the expression size
                return pos + putULEB(size, scratch, 0) + size;
            } else {
                // write the size and expression into the output buffer
                pos = putULEB(size, buffer, pos);
                pos = putByte(regOp, buffer, pos);
                return putSLEB(offset, buffer, pos);
            }
        } else {
            // write a relocatable address relative to the heap section start
            byte regOp = DW_OP_addr;
            int size = 9;
            // write the size and expression into the output buffer
            if (buffer == null) {
                return pos + putULEB(size, scratch, 0) + size;
            } else {
                pos = putULEB(size, buffer, pos);
                pos = putByte(regOp, buffer, pos);
                return putRelocatableHeapOffset(offset, buffer, pos);
            }
        }
    }

    private int writeArrayTypes(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] array classes", pos);
        return getTypes().filter(TypeEntry::isArray).reduce(pos,
                        (p, typeEntry) -> {
                            ArrayTypeEntry arrayTypeEntry = (ArrayTypeEntry) typeEntry;
                            return writeArrayTypeUnit(context, arrayTypeEntry, buffer, p);
                        },
                        (oldpos, newpos) -> newpos);
    }

    private int writeArrayTypeUnit(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        int lengthPos = pos;
        log(context, "  [0x%08x] array class unit %s", pos, arrayTypeEntry.getTypeName());
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        int abbrevCode = DW_ABBREV_CODE_array_unit;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        String name = arrayTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        // write the array layout and array reference DIEs
        int layouIdx = pos;
        pos = writeArrayLayout(context, arrayTypeEntry, buffer, pos);
        pos = writeArrayType(context, arrayTypeEntry, layouIdx, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        // fix up the CU length
        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    private int writeArrayLayout(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        TypeEntry elementType = arrayTypeEntry.getElementType();
        StructureTypeEntry headerType;
        if (elementType.isPrimitive()) {
            PrimitiveTypeEntry primitiveTypeEntry = (PrimitiveTypeEntry) elementType;
            headerType = (StructureTypeEntry) lookupType(ARRAY_HEADER_STRUCT_NAME + primitiveTypeEntry.getTypeChar());
        } else {
            headerType = (StructureTypeEntry) lookupType(ARRAY_HEADER_STRUCT_NAME + "A");
        }
        log(context, "  [0x%08x] array layout", pos);
        int abbrevCode = DW_ABBREV_CODE_array_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString("_" + arrayTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        int size = headerType.getSize();
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        // now the child DIEs
        // write a type definition for the element array field
        int arrayDataTypeIdx = pos;
        pos = writeArrayDataType(context, elementType, buffer, pos);
        // write a zero length element array field
        pos = writeArrayElementField(context, size, arrayDataTypeIdx, buffer, pos);
        pos = writeArraySuperReference(context, arrayTypeEntry, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeArrayDataType(DebugContext context, TypeEntry elementType, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data type", pos);
        int abbrevCode = DW_ABBREV_CODE_array_data_type;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int size = (elementType.isPrimitive() ? elementType.getSize() : 8);
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData1((byte) size, buffer, pos);
        String elementTypeName = elementType.getTypeName();
        int elementTypeIdx = getTypeIndex(elementTypeName);
        log(context, "  [0x%08x]     type idx 0x%x (%s)", pos, elementTypeIdx, elementTypeName);
        pos = writeAttrRefAddr(elementTypeIdx, buffer, pos);
        return pos;
    }

    private int writeArrayElementField(DebugContext context, int offset, int arrayDataTypeIdx, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data field", pos);
        int abbrevCode = DW_ABBREV_CODE_header_field;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String fieldName = uniqueDebugString("data");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeAttrStrp(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type idx 0x%x", pos, arrayDataTypeIdx);
        pos = writeAttrRefAddr(arrayDataTypeIdx, buffer, pos);
        int size = 0;
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData1((byte) offset, buffer, pos);
        int modifiers = Modifier.PUBLIC;
        log(context, "  [0x%08x]     modifiers %s", pos, "public");
        return writeAttrAccessibility(modifiers, buffer, pos);

    }

    private int writeArraySuperReference(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int pos) {
        String headerName;
        TypeEntry elementType = arrayTypeEntry.getElementType();
        if (elementType.isPrimitive()) {
            headerName = ARRAY_HEADER_STRUCT_NAME + ((PrimitiveTypeEntry) elementType).getTypeChar();
        } else {
            headerName = ARRAY_HEADER_STRUCT_NAME + "A";
        }
        int headerTypeOffset = getTypeIndex(headerName);
        log(context, "  [0x%08x] super reference", pos);
        int abbrevCode = DW_ABBREV_CODE_super_reference;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, headerTypeOffset, headerName);
        pos = writeAttrRefAddr(headerTypeOffset, buffer, pos);
        // parent layout is embedded at start of object
        log(context, "  [0x%08x]     data_member_location (super) 0x%x", pos, 0);
        pos = writeAttrData1((byte) 0, buffer, pos);
        log(context, "  [0x%08x]     modifiers public", pos);
        int modifiers = Modifier.PUBLIC;
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeArrayType(DebugContext context, ArrayTypeEntry arrayTypeEntry, int layoutOffset, byte[] buffer, int p) {
        int pos = p;
        int pointerTypeOffset = pos;
        String name = uniqueDebugString(arrayTypeEntry.getTypeName());

        // define a pointer type referring to the underlying layout
        log(context, "  [0x%08x] array pointer type", pos);
        int abbrevCode = DW_ABBREV_CODE_array_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, 8);
        pos = writeAttrData1((byte) 8, buffer, pos);
        log(context, "  [0x%08x]     type (pointer) 0x%x (%s)", pos, layoutOffset, name);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);

        // now write a typedef to name the pointer type and use it as the defining type
        // for the Java array type name
        setTypeIndex(arrayTypeEntry, pos);
        log(context, "  [0x%08x] array pointer typedef", pos);
        abbrevCode = DW_ABBREV_CODE_array_typedef;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        log(context, "  [0x%08x]     type  (typedef) 0x%x (%s)", pos, pointerTypeOffset, name);
        pos = writeAttrRefAddr(pointerTypeOffset, buffer, pos);

        return pos;
    }

    private int writeDeoptMethodsCU(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        assert classEntry.includesDeoptTarget();
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        String fileName = classEntry.getFileName();
        int lineIndex = getLineIndex(classEntry);
        int abbrevCode = (fileName.length() > 0 ? DW_ABBREV_CODE_class_unit1 : DW_ABBREV_CODE_class_unit2);
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        int lo = findLo(classPrimaryEntries, true);
        int hi = findHi(classPrimaryEntries, true, true);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_class_unit1) {
            log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
            pos = writeAttrData4(lineIndex, buffer, pos);
        }

        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            Range range = primaryEntry.getPrimary();
            if (range.isDeoptTarget()) {
                pos = writeMethodLocation(context, classEntry, range, buffer, pos);
            }
        }
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodLocation(DebugContext context, ClassEntry classEntry, Range range, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] method location", pos);
        int abbrevCode = DW_ABBREV_CODE_method_location;
        log(context, "  [0x%08x] <1> Abbrev Number  %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, range.getLo());
        pos = writeAttrAddress(range.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, range.getHi());
        pos = writeAttrAddress(range.getHi(), buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DW_FLAG_true, buffer, pos);
        String methodKey = range.getFullMethodNameWithParamsAndReturnType();
        int methodSpecOffset = getMethodDeclarationIndex(classEntry, methodKey);
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeAttrRefAddr(methodSpecOffset, buffer, pos);
        pos = writeMethodParameterDeclarations(context, classEntry, range, false, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            /* CU length. */
            pos += putInt(0, scratch, 0);
            /* DWARF version. */
            pos += putShort(DW_VERSION_4, scratch, 0);
            /* Abbrev offset. */
            pos += putInt(0, scratch, 0);
            /* Address size. */
            return pos + putByte((byte) 8, scratch, 0);
        } else {
            /* CU length. */
            pos = putInt(0, buffer, pos);
            /* DWARF version. */
            pos = putShort(DW_VERSION_4, buffer, pos);
            /* Abbrev offset. */
            pos = putInt(0, buffer, pos);
            /* Address size. */
            return putByte((byte) 8, buffer, pos);
        }
    }

    private static int findLo(LinkedList<PrimaryEntry> classPrimaryEntries, boolean isDeoptTargetCU) {
        if (!isDeoptTargetCU) {
            /* First entry is the one we want. */
            return classPrimaryEntries.getFirst().getPrimary().getLo();
        } else {
            /* Need the first entry which is a deopt target. */
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                Range range = primaryEntry.getPrimary();
                if (range.isDeoptTarget()) {
                    return range.getLo();
                }
            }
        }
        // we should never get here
        assert false;
        return 0;
    }

    private static int findHi(LinkedList<PrimaryEntry> classPrimaryEntries, boolean includesDeoptTarget, boolean isDeoptTargetCU) {
        if (isDeoptTargetCU || !includesDeoptTarget) {
            /* Either way the last entry is the one we want. */
            return classPrimaryEntries.getLast().getPrimary().getHi();
        } else {
            /* Need the last entry which is not a deopt target. */
            int hi = 0;
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                Range range = primaryEntry.getPrimary();
                if (!range.isDeoptTarget()) {
                    hi = range.getHi();
                } else {
                    return hi;
                }
            }
        }
        // should never get here
        assert false;
        return 0;
    }

    private int writeAttrStrp(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putInt(0, scratch, 0);
        } else {
            int idx = debugStringIndex(value);
            return putInt(idx, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    public int writeAttrString(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + value.length() + 1;
        } else {
            return putAsciiStringBytes(value, buffer, pos);
        }
    }

    public int writeAttrAccessibility(int modifiers, byte[] buffer, int p) {
        byte access;
        if (Modifier.isPublic(modifiers)) {
            access = DW_ACCESS_public;
        } else if (Modifier.isProtected(modifiers)) {
            access = DW_ACCESS_protected;
        } else if (Modifier.isPrivate(modifiers)) {
            access = DW_ACCESS_private;
        } else {
            // package private -- make it public for now
            access = DW_ACCESS_public;
        }
        return writeAttrData1(access, buffer, p);
    }

    /**
     * The debug_info section depends on abbrev section.
     */
    protected static final String TARGET_SECTION_NAME = TEXT_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE,
                    /* Add this so we can use the text section base address for debug. */
                    LayoutDecision.Kind.VADDR
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
