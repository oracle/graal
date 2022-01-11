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

package com.oracle.objectfile.elf.dwarf;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.debugentry.MethodEntry;
import org.graalvm.compiler.debug.DebugContext;

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
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo;
import com.oracle.objectfile.elf.ELFObjectFile;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * The name of a special DWARF struct type used to model an object header.
     */
    public static final String OBJECT_HEADER_STRUCT_NAME = "_objhdr";

    /**
     * An info header section always contains a fixed number of bytes.
     */
    private static final int DW_DIE_HEADER_SIZE = 11;

    public DwarfInfoSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DwarfDebugInfo.DW_INFO_SECTION_NAME;
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        /*
         * Order all content decisions after all size decisions by making info section content
         * depend on abbrev section size.
         */
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
        if ((flags & DebugPrimitiveTypeInfo.FLAG_NUMERIC) != 0) {
            if (((flags & DebugPrimitiveTypeInfo.FLAG_INTEGRAL) != 0)) {
                if ((flags & DebugPrimitiveTypeInfo.FLAG_SIGNED) != 0) {
                    switch (bitCount) {
                        case 8:
                            return DwarfDebugInfo.DW_ATE_signed_char;
                        default:
                            assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                            return DwarfDebugInfo.DW_ATE_signed;
                    }
                } else {
                    assert bitCount == 16;
                    return DwarfDebugInfo.DW_ATE_unsigned;
                }
            } else {
                assert bitCount == 32 || bitCount == 64;
                return DwarfDebugInfo.DW_ATE_float;
            }
        } else {
            assert bitCount == 1;
            return DwarfDebugInfo.DW_ATE_boolean;
        }
    }

    public int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        /* Write entries for all the types known to the generator. */

        pos = writeBuiltInUnit(context, buffer, pos);

        /*
         * Write class units for non-primary classes i.e. ones which don't have associated methods.
         */

        pos = writeNonPrimaryClasses(context, buffer, pos);

        /*
         * Write class units for primary classes in increasing order of method address.
         */

        pos = writePrimaryClasses(context, buffer, pos);

        /* Write class units for array types. */

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

    public int writeBuiltInUnit(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        int lengthPos = pos;
        log(context, "  [0x%08x] <0> builtin unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_builtin_unit;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.DW_LANG_Java, buffer, pos);

        /* Write child entries for basic Java types. */

        pos = getTypes().filter(TypeEntry::isPrimitive).reduce(pos,
                        (pos1, typeEntry) -> {
                            PrimitiveTypeEntry primitiveTypeEntry = (PrimitiveTypeEntry) typeEntry;
                            if (primitiveTypeEntry.getBitCount() > 0) {
                                return writePrimitiveType(context, primitiveTypeEntry, buffer, pos1);
                            } else {
                                return writeVoidType(context, primitiveTypeEntry, buffer, pos1);
                            }
                        },
                        (oldpos, newpos) -> newpos);

        /* Write child entries for object header and array header structs. */

        pos = getTypes().filter(TypeEntry::isHeader).reduce(pos,
                        (pos1, typeEntry) -> {
                            HeaderTypeEntry headerTypeEntry = (HeaderTypeEntry) typeEntry;
                            return writeHeaderType(context, headerTypeEntry, buffer, pos1);
                        },
                        (oldpos, newpos) -> newpos);

        /* Terminate with null entry. */

        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */

        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    public int writePrimitiveType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() > 0;
        int pos = p;
        log(context, "  [0x%08x] primitive type %s", pos, primitiveTypeEntry.getTypeName());
        /* Record the location of this type entry. */
        setTypeIndex(primitiveTypeEntry, pos);
        /*
         * primitive fields never need an indirection so use the same index for places where we
         * might want an indirect type
         */
        setIndirectTypeIndex(primitiveTypeEntry, pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
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
        /* Record the location of this type entry. */
        setTypeIndex(primitiveTypeEntry, pos);
        /*
         * Type void never needs an indirection so use the same index for places where we might want
         * an indirect type.
         */
        setIndirectTypeIndex(primitiveTypeEntry, pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_void_type;
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
        /* Record the location of this type entry. */
        setTypeIndex(headerTypeEntry, pos);
        /*
         * Header records don't need an indirection so use the same index for places where we might
         * want an indirect type.
         */
        setIndirectTypeIndex(headerTypeEntry, pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_object_header;
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
        /* use the indirect type for the field so pointers get translated */
        int valueTypeIdx = getIndirectTypeIndex(valueTypeName);
        log(context, "  [0x%08x] header field", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_header_field;
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
        /* Non-primary classes have no compiled methods so they also have no line section entry. */
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_unit3;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);

        /* Now write the child DIEs starting with the layout and pointer type. */

        if (classEntry.isInterface()) {
            InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) classEntry;
            pos = writeInterfaceLayout(context, interfaceClassEntry, buffer, pos);
            pos = writeInterfaceType(context, interfaceClassEntry, buffer, pos);
        } else {
            pos = writeClassLayout(context, classEntry, buffer, pos);
            pos = writeClassType(context, classEntry, buffer, pos);
        }

        /* Note, for a non-primary there are no method definitions to write. */

        /* Write abstract inline methods. */

        pos = writeAbstractInlineMethods(context, classEntry, buffer, pos);

        /* Write all static field definitions. */

        pos = writeStaticFieldLocations(context, classEntry, buffer, pos);

        /* Terminate children with null entry. */

        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */

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
        /*
         * Primary classes only have a line section entry if they have method and an associated
         * file.
         */
        List<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        int lo = findLo(classPrimaryEntries, false);
        int hi = findHi(classPrimaryEntries, classEntry.includesDeoptTarget(), false);
        // we must have at least one compiled method
        assert hi > 0;
        int abbrevCode = (fileName.length() > 0 ? DwarfDebugInfo.DW_ABBREV_CODE_class_unit1 : DwarfDebugInfo.DW_ABBREV_CODE_class_unit2);
        setCUIndex(classEntry, pos);
        int lengthPos = pos;
        log(context, "  [0x%08x] primary class unit %s", pos, classEntry.getTypeName());
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fileName), fileName);
        pos = writeAttrStrp(fileName, buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        /*
         * Specify hi and lo for the compile unit which means we also need to ensure methods within
         * it are listed in ascending address order.
         */
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        /* Only write stmt_list if the entry actually has line number info. */
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_unit1) {
            log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
            pos = writeAttrSecOffset(lineIndex, buffer, pos);
        }

        /* Now write the child DIEs starting with the layout and pointer type. */

        if (classEntry.isInterface()) {
            InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) classEntry;
            pos = writeInterfaceLayout(context, interfaceClassEntry, buffer, pos);
            pos = writeInterfaceType(context, interfaceClassEntry, buffer, pos);
        } else {
            pos = writeClassLayout(context, classEntry, buffer, pos);
            pos = writeClassType(context, classEntry, buffer, pos);
        }

        /* Write all method locations. */

        pos = writeMethodLocations(context, classEntry, false, buffer, pos);

        /* Write abstract inline method locations. */

        pos = writeAbstractInlineMethods(context, classEntry, buffer, pos);

        /* Write all static field definitions. */

        pos = writeStaticFieldLocations(context, classEntry, buffer, pos);

        /* Terminate children with null entry. */

        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */

        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    private int writeClassLayout(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        int layoutIndex = pos;
        setLayoutIndex(classEntry, layoutIndex);
        log(context, "  [0x%08x] class layout", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_layout1;
        /*
         * when we don't have a separate indirect type then hub layouts need an extra data_location
         * attribute
         */
        if (!dwarfSections.useHeapBase() && dwarfSections.isHubClassEntry(classEntry)) {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_layout2;
        }
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = classEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        int size = classEntry.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        int fileIdx = classEntry.localFilesIdx();
        log(context, "  [0x%08x]     file  0x%x (%s)", pos, fileIdx, classEntry.getFileName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_layout2) {
            /* Write a data location expression to mask and/or rebase oop pointers. */
            log(context, "  [0x%08x]     data_location", pos);
            pos = writeIndirectOopConversionExpression(true, buffer, pos);
        }
        int superTypeOffset;
        String superName;
        ClassEntry superClassEntry = classEntry.getSuperClass();
        if (superClassEntry != null) {
            /* Inherit layout from super class. */
            superName = superClassEntry.getTypeName();
            superTypeOffset = getLayoutIndex(superClassEntry);
        } else {
            /* Inherit layout from object header. */
            superName = OBJECT_HEADER_STRUCT_NAME;
            superTypeOffset = getTypeIndex(superName);
        }
        /* Now write the child fields. */
        pos = writeSuperReference(context, superTypeOffset, superName, buffer, pos);
        pos = writeFields(context, classEntry, buffer, pos);
        pos = writeMethodDeclarations(context, classEntry, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        if (dwarfSections.useHeapBase()) {
            /*
             * Write a wrapper type with a data_location attribute that can act as a target for an
             * indirect pointer.
             */
            setIndirectLayoutIndex(classEntry, pos);
            log(context, "  [0x%08x] indirect class layout", pos);
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_layout;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            String indirectName = uniqueDebugString(DwarfDebugInfo.INDIRECT_PREFIX + classEntry.getTypeName());
            log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(indirectName), name);
            pos = writeAttrStrp(indirectName, buffer, pos);
            log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
            pos = writeAttrData2((short) size, buffer, pos);
            /* Write a data location expression to mask and/or rebase oop pointers. */
            log(context, "  [0x%08x]     data_location", pos);
            pos = writeIndirectOopConversionExpression(dwarfSections.isHubClassEntry(classEntry), buffer, pos);
            superTypeOffset = layoutIndex;
            /* Now write the child field. */
            pos = writeSuperReference(context, superTypeOffset, superName, buffer, pos);
            /*
             * Write a terminating null attribute.
             */
            pos = writeAttrNull(buffer, pos);
        }

        return pos;
    }

    private int writeSuperReference(DebugContext context, int superTypeOffset, String superName, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] super reference", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_super_reference;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, superTypeOffset, superName);
        pos = writeAttrRefAddr(superTypeOffset, buffer, pos);
        /* Parent layout is embedded at start of object. */
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

    private int writeField(DebugContext context, StructureTypeEntry entry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        int modifiers = fieldEntry.getModifiers();
        boolean hasFile = fieldEntry.getFileName().length() > 0;
        log(context, "  [0x%08x] field definition", pos);
        int abbrevCode;
        boolean isStatic = Modifier.isStatic(modifiers);
        if (!isStatic) {
            if (!hasFile) {
                abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_field_declaration1;
            } else {
                abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2;
            }
        } else {
            if (!hasFile) {
                abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_field_declaration3;
            } else {
                abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4;
            }
            /* Record the position of the declaration to use when we write the definition. */
            setFieldDeclarationIndex(entry, fieldEntry.fieldName(), pos);
        }
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = fieldEntry.fieldName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        /* We may not have a file and line for a field. */
        if (hasFile) {
            assert entry instanceof ClassEntry;
            int fileIdx = ((ClassEntry) entry).localFilesIdx(fieldEntry.getFileEntry());
            assert fileIdx > 0;
            log(context, "  [0x%08x]     filename  0x%x (%s)", pos, fileIdx, fieldEntry.getFileName());
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            /* At present we definitely don't have line numbers. */
        }
        String valueTypeName = fieldEntry.getValueType().getTypeName();
        /* use the indirect type for the field so pointers get translated if needed */
        int typeIdx = getIndirectTypeIndex(valueTypeName);
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeIdx, valueTypeName);
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (!isStatic) {
            int memberOffset = fieldEntry.getOffset();
            log(context, "  [0x%08x]     member offset 0x%x", pos, memberOffset);
            pos = writeAttrData2((short) memberOffset, buffer, pos);
        }
        log(context, "  [0x%08x]     accessibility %s", pos, fieldEntry.getModifiersString());
        pos = writeAttrAccessibility(fieldEntry.getModifiers(), buffer, pos);
        /* Static fields are only declared here and are external. */
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
        for (MethodEntry method : classEntry.getMethods()) {
            if (method.isInRange() || method.isInlined()) {
                /*
                 * Declare all methods including deopt targets even though they are written in
                 * separate CUs.
                 */
                pos = writeMethodDeclaration(context, classEntry, method, buffer, pos);
            }
        }

        return pos;
    }

    private int writeMethodDeclaration(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        String methodKey = method.getSymbolName();
        setMethodDeclarationIndex(classEntry, methodKey, pos);
        int modifiers = method.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        log(context, "  [0x%08x] method declaration %s", pos, methodKey);
        int abbrevCode = (isStatic ? DwarfDebugInfo.DW_ABBREV_CODE_method_declaration_static : DwarfDebugInfo.DW_ABBREV_CODE_method_declaration);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        String name = uniqueDebugString(method.methodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        FileEntry fileEntry = method.getFileEntry();
        if (fileEntry == null) {
            fileEntry = classEntry.getFileEntry();
        }
        assert fileEntry != null;
        int fileIdx = classEntry.localFilesIdx(fileEntry);
        log(context, "  [0x%08x]     file 0x%x (%s)", pos, fileIdx, fileEntry.getFullName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        String returnTypeName = method.getValueType().getTypeName();
        int retTypeIdx = getTypeIndex(returnTypeName);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, returnTypeName);
        pos = writeAttrRefAddr(retTypeIdx, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, method.isDeopt() ? "true" : "false");
        pos = writeFlag((method.isDeopt() ? (byte) 1 : (byte) 0), buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        int typeIdx = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     containing_type 0x%x (%s)", pos, typeIdx, classEntry.getTypeName());
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_declaration) {
            /* Record the current position so we can back patch the object pointer. */
            int objectPointerIndex = pos;
            /*
             * Write a dummy ref address to move pos on to where the first parameter gets written.
             */
            pos = writeAttrRefAddr(0, buffer, pos);
            /*
             * Now backpatch object pointer slot with current pos, identifying the first parameter.
             */
            log(context, "  [0x%08x]     object_pointer 0x%x", objectPointerIndex, pos);
            writeAttrRefAddr(pos, buffer, objectPointerIndex);
        }
        /* Write method parameter declarations. */
        pos = writeMethodParameterDeclarations(context, classEntry, method, true, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodParameterDeclarations(DebugContext context, ClassEntry classEntry, MethodEntry method, boolean isSpecification, byte[] buffer, int p) {
        int pos = p;
        if (!Modifier.isStatic(method.getModifiers())) {
            pos = writeMethodParameterDeclaration(context, "this", classEntry.getTypeName(), true, isSpecification, buffer, pos);
        }
        if (method.getParamTypes() == null) {
            return pos;
        }
        for (TypeEntry paramType : method.getParamTypes()) {
            String paramTypeName = paramType.getTypeName();
            String paramName = uniqueDebugString("");
            FileEntry fileEntry = method.getFileEntry();
            if (fileEntry != null) {
                pos = writeMethodParameterDeclaration(context, paramName, paramTypeName, false, isSpecification, buffer, pos);
            } else {
                pos = writeMethodParameterDeclaration(context, paramTypeName, paramTypeName, false, isSpecification, buffer, pos);
            }
        }
        return pos;
    }

    private int writeMethodParameterDeclaration(DebugContext context, @SuppressWarnings("unused") String paramName, String paramTypeName, boolean artificial, boolean isSpecification, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        int abbrevCode;
        int level = (isSpecification ? 3 : 2);
        if (artificial) {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1;
        } else {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration3;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        /* We don't have parameter names at present. */
        int typeIdx = getTypeIndex(paramTypeName);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeIdx, paramTypeName);
        pos = writeAttrRefAddr(typeIdx, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1) {
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
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_interface_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = interfaceClassEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        /*
         * Now write references to all class layouts that implement this interface.
         */
        pos = writeInterfaceImplementors(context, interfaceClassEntry, buffer, pos);
        pos = writeMethodDeclarations(context, interfaceClassEntry, buffer, pos);

        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        if (dwarfSections.useHeapBase()) {
            /*
             * Write a wrapper type with a data_location attribute that can act as a target for an
             * indirect pointer.
             */
            setIndirectLayoutIndex(interfaceClassEntry, pos);
            log(context, "  [0x%08x] indirect class layout", pos);
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_layout;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            String indirectName = uniqueDebugString(DwarfDebugInfo.INDIRECT_PREFIX + interfaceClassEntry.getTypeName());
            log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(indirectName), name);
            pos = writeAttrStrp(indirectName, buffer, pos);
            int size = interfaceClassEntry.getSize();
            log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
            pos = writeAttrData2((short) size, buffer, pos);
            /* Write a data location expression to mask and/or rebase oop pointers. */
            log(context, "  [0x%08x]     data_location", pos);
            pos = writeIndirectOopConversionExpression(false, buffer, pos);
            /* Now write the child field. */
            pos = writeSuperReference(context, layoutOffset, name, buffer, pos);
            /*
             * Write a terminating null attribute.
             */
            pos = writeAttrNull(buffer, pos);
        }

        return pos;
    }

    private int writeInterfaceImplementors(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        return interfaceClassEntry.implementors().reduce(p,
                        (pos, classEntry) -> writeInterfaceImplementor(context, classEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private int writeInterfaceImplementor(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] interface implementor", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_interface_implementor;
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

        /* Define a pointer type referring to the underlying layout. */
        int typeIdx = pos;
        setTypeIndex(classEntry, typeIdx);
        log(context, "  [0x%08x] class pointer type", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        int layoutOffset = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);

        if (dwarfSections.useHeapBase()) {
            /* Define an indirect pointer type referring to the indirect layout. */
            setIndirectTypeIndex(classEntry, pos);
            log(context, "  [0x%08x] class indirect pointer type", pos);
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_pointer;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            int oopReferenceSize = dwarfSections.oopReferenceSize();
            log(context, "  [0x%08x]     byte_size 0x%x", pos, oopReferenceSize);
            pos = writeAttrData1((byte) oopReferenceSize, buffer, pos);
            layoutOffset = getIndirectLayoutIndex(classEntry);
            log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
            pos = writeAttrRefAddr(layoutOffset, buffer, pos);
        } else {
            setIndirectTypeIndex(classEntry, typeIdx);
        }

        return pos;
    }

    private int writeInterfaceType(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        int pos = p;

        /* Define a pointer type referring to the underlying layout. */
        int typeIdx = pos;
        setTypeIndex(interfaceClassEntry, typeIdx);
        log(context, "  [0x%08x] interface pointer type", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_interface_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        int layoutOffset = getLayoutIndex(interfaceClassEntry);
        log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);

        if (dwarfSections.useHeapBase()) {
            /* Define an indirect pointer type referring to the indirect layout. */
            setIndirectTypeIndex(interfaceClassEntry, pos);
            log(context, "  [0x%08x] interface indirect pointer type", pos);
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_pointer;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            int byteSize = dwarfSections.oopReferenceSize();
            log(context, "  [0x%08x]     byte_size 0x%x", pos, byteSize);
            pos = writeAttrData1((byte) byteSize, buffer, pos);
            layoutOffset = getIndirectLayoutIndex(interfaceClassEntry);
            log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
            pos = writeAttrRefAddr(layoutOffset, buffer, pos);
        } else {
            setIndirectTypeIndex(interfaceClassEntry, typeIdx);
        }

        return pos;
    }

    private int writeMethodLocations(DebugContext context, ClassEntry classEntry, boolean deoptTargets, byte[] buffer, int p) {
        int pos = p;
        List<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();

        /* The primary file entry should always be first in the local files list. */
        assert classEntry.localFilesIdx(classEntry.getFileEntry()) == 1;

        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            Range primary = primaryEntry.getPrimary();
            if (primary.isDeoptTarget() != deoptTargets) {
                continue;
            }
            pos = writeMethodLocation(context, classEntry, primaryEntry, buffer, pos);
        }
        return pos;
    }

    private int writeAbstractInlineMethods(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        for (MethodEntry method : classEntry.getMethods()) {
            if (method.isInlined()) {
                String methodKey = method.getSymbolName();
                setAbstractInlineMethodIndex(classEntry, methodKey, pos);
                pos = writeAbstractInlineMethod(context, classEntry, method, buffer, pos);
            }
        }
        return pos;
    }

    /**
     * Go through the subranges and generate concrete debug entries for inlined methods.
     */
    private int generateConcreteInlinedMethods(DebugContext context, ClassEntry classEntry,
                    PrimaryEntry primaryEntry, byte[] buffer, int p) {
        Range primary = primaryEntry.getPrimary();
        if (primary.isLeaf()) {
            return p;
        }
        int pos = p;
        log(context, "  [0x%08x] concrete entries [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodName());
        int depth = 1;
        Iterator<Range> iterator = primaryEntry.topDownRangeIterator();
        while (iterator.hasNext()) {
            Range subrange = iterator.next();
            /*
             * Top level subranges don't need concrete methods. They just provide a file and line
             * for their callee.
             */
            if (!subrange.isInlined()) {
                // only happens if the subrange is for the top-level compiled method
                assert subrange.getCaller() == primaryEntry.getPrimary();
                assert subrange.getDepth() == 0;
                continue;
            }
            // if we just stepped out of a child range write nulls for each step up
            while (depth > subrange.getDepth()) {
                pos = writeAttrNull(buffer, pos);
                depth--;
            }
            MethodEntry method = subrange.getMethodEntry();
            ClassEntry methodClassEntry = method.ownerType();
            String methodKey = method.getSymbolName();
            /* the abstract index was written in the method's class entry */
            int specificationIndex = getAbstractInlineMethodIndex(methodClassEntry, methodKey);
            pos = writeInlineSubroutine(context, classEntry, subrange, specificationIndex, depth, buffer, pos);
            if (!subrange.isLeaf()) {
                // increment depth before writing the children
                depth++;
            }
        }
        // if we just stepped out of a child range write nulls for each step up
        while (depth > 1) {
            pos = writeAttrNull(buffer, pos);
            depth--;
        }
        return pos;
    }

    private int writeStaticFieldLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        /*
         * Only write locations for static fields that have an offset greater than 0. A negative
         * offset indicates that the field has been folded into code as an unmaterialized constant.
         */
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
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_static_field_location;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     specification  0x%x", pos, fieldDefinitionOffset);
        pos = writeAttrRefAddr(fieldDefinitionOffset, buffer, pos);
        /* Field offset needs to be relocated relative to static primitive or static object base. */
        int offset = fieldEntry.getOffset();
        log(context, "  [0x%08x]     location  heapbase + 0x%x (%s)", pos, offset, (fieldEntry.getValueType().isPrimitive() ? "primitive" : "object"));
        pos = writeHeapLocation(offset, buffer, pos);
        return pos;
    }

    private int writeHeapLocation(int offset, byte[] buffer, int p) {
        int pos = p;
        if (dwarfSections.useHeapBase()) {
            /* Write a location rebasing the offset relative to the heapbase register. */
            byte regOp = (byte) (DwarfDebugInfo.DW_OP_breg0 + dwarfSections.getHeapbaseRegister());
            /*
             * We have to size the DWARF expression by writing it to the scratch buffer so we can
             * write its size as a ULEB before the expression itself.
             */
            int size = putByte(regOp, scratch, 0) + putSLEB(offset, scratch, 0);
            if (buffer == null) {
                /* Add ULEB size to the expression size. */
                return pos + putULEB(size, scratch, 0) + size;
            } else {
                /* Write the size and expression into the output buffer. */
                pos = putULEB(size, buffer, pos);
                pos = putByte(regOp, buffer, pos);
                return putSLEB(offset, buffer, pos);
            }
        } else {
            /* Write a relocatable address relative to the heap section start. */
            byte regOp = DwarfDebugInfo.DW_OP_addr;
            int size = 9;
            /* Write the size and expression into the output buffer. */
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
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_array_unit;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DwarfDebugInfo.DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.DW_LANG_Java, buffer, pos);
        String name = arrayTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        /* Write the array layout and array reference DIEs. */
        TypeEntry elementType = arrayTypeEntry.getElementType();
        int size = arrayTypeEntry.getSize();
        int layoutIdx = pos;
        pos = writeArrayLayout(context, arrayTypeEntry, elementType, size, buffer, pos);
        int indirectLayoutIdx = pos;
        if (dwarfSections.useHeapBase()) {
            pos = writeIndirectArrayLayout(context, arrayTypeEntry, size, layoutIdx, buffer, pos);
        }
        pos = writeArrayTypes(context, arrayTypeEntry, layoutIdx, indirectLayoutIdx, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */
        patchLength(lengthPos, buffer, pos);

        return pos;
    }

    private int writeArrayLayout(DebugContext context, ArrayTypeEntry arrayTypeEntry, TypeEntry elementType, int size, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array layout", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_array_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = arrayTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeAttrStrp(name, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);

        /* Now the child DIEs. */

        /* write a type definition for the element array field. */
        int arrayDataTypeIdx = pos;
        pos = writeArrayDataType(context, elementType, buffer, pos);
        pos = writeFields(context, arrayTypeEntry, buffer, pos);
        /* Write a zero length element array field. */
        pos = writeArrayElementField(context, size, arrayDataTypeIdx, buffer, pos);
        pos = writeArraySuperReference(context, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeFields(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        return arrayTypeEntry.fields().filter(DwarfInfoSectionImpl::isManifestedField).reduce(p,
                        (pos, fieldEntry) -> writeField(context, arrayTypeEntry, fieldEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private int writeIndirectArrayLayout(DebugContext context, ArrayTypeEntry arrayTypeEntry, int size, int layoutOffset, byte[] buffer, int p) {
        int pos = p;

        /*
         * write a wrapper type with a data_location attribute that can act as a target for an
         * indirect pointer
         */
        log(context, "  [0x%08x] indirect class layout", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_layout;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = arrayTypeEntry.getTypeName();
        String indirectName = uniqueDebugString(DwarfDebugInfo.INDIRECT_PREFIX + name);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(indirectName), name);
        pos = writeAttrStrp(indirectName, buffer, pos);
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        /* Write a data location expression to mask and/or rebase oop pointers. */
        log(context, "  [0x%08x]     data_location", pos);
        pos = writeIndirectOopConversionExpression(false, buffer, pos);
        /* Now write the child field. */
        pos = writeSuperReference(context, layoutOffset, name, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeArrayDataType(DebugContext context, TypeEntry elementType, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data type", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_array_data_type;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int size = (elementType.isPrimitive() ? elementType.getSize() : 8);
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData1((byte) size, buffer, pos);
        String elementTypeName = elementType.getTypeName();
        /* use the indirect type for the element type so pointers get translated */
        int elementTypeIdx = getIndirectTypeIndex(elementTypeName);
        log(context, "  [0x%08x]     type idx 0x%x (%s)", pos, elementTypeIdx, elementTypeName);
        pos = writeAttrRefAddr(elementTypeIdx, buffer, pos);
        return pos;
    }

    private int writeArrayElementField(DebugContext context, int offset, int arrayDataTypeIdx, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data field", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_header_field;
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

    private int writeArraySuperReference(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /* Arrays all inherit from java.lang.Object */
        String superName = "java.lang.Object";
        TypeEntry objectType = lookupType(superName);
        assert objectType instanceof ClassEntry;
        int superOffset = getLayoutIndex((ClassEntry) objectType);
        return writeSuperReference(context, superOffset, superName, buffer, pos);
    }

    private int writeArrayTypes(DebugContext context, ArrayTypeEntry arrayTypeEntry, int layoutOffset, int indirectLayoutOffset, byte[] buffer, int p) {
        int pos = p;
        String name = uniqueDebugString(arrayTypeEntry.getTypeName());

        int typeIdx = pos;
        setTypeIndex(arrayTypeEntry, pos);
        /* Define a pointer type referring to the underlying layout. */
        log(context, "  [0x%08x] array pointer type", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_array_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size  0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        log(context, "  [0x%08x]     type (pointer) 0x%x (%s)", pos, layoutOffset, name);
        pos = writeAttrRefAddr(layoutOffset, buffer, pos);

        if (dwarfSections.useHeapBase()) {
            setIndirectTypeIndex(arrayTypeEntry, pos);
            /* Define an indirect pointer type referring to the underlying indirect layout. */
            log(context, "  [0x%08x] array indirect pointer type", pos);
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_indirect_pointer;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            int byteSize = dwarfSections.oopReferenceSize();
            log(context, "  [0x%08x]     byte_size  0x%x", pos, byteSize);
            pos = writeAttrData1((byte) byteSize, buffer, pos);
            log(context, "  [0x%08x]     type (pointer) 0x%x (%s)", pos, indirectLayoutOffset, name);
            pos = writeAttrRefAddr(indirectLayoutOffset, buffer, pos);
        } else {
            setIndirectTypeIndex(arrayTypeEntry, typeIdx);
        }

        return pos;
    }

    private int writeDeoptMethodsCU(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        assert classEntry.includesDeoptTarget();
        List<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        assert !classPrimaryEntries.isEmpty();
        String fileName = classEntry.getFileName();
        int lineIndex = getLineIndex(classEntry);
        int lo = findLo(classPrimaryEntries, true);
        int hi = findHi(classPrimaryEntries, true, true);
        // we must have at least one compiled deopt method
        assert hi > 0 : hi;
        int abbrevCode = (fileName.length() > 0 ? DwarfDebugInfo.DW_ABBREV_CODE_class_unit1 : DwarfDebugInfo.DW_ABBREV_CODE_class_unit2);
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DwarfDebugInfo.DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_unit1) {
            log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
            pos = writeAttrData4(lineIndex, buffer, pos);
        }

        pos = writeMethodLocations(context, classEntry, true, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodLocation(DebugContext context, ClassEntry classEntry, PrimaryEntry primaryEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = primaryEntry.getPrimary();
        log(context, "  [0x%08x] method location", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_location;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, primary.getLo());
        pos = writeAttrAddress(primary.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, primary.getHi());
        pos = writeAttrAddress(primary.getHi(), buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfDebugInfo.DW_FLAG_true, buffer, pos);
        String methodKey = primary.getSymbolName();
        int methodSpecOffset = getMethodDeclarationIndex(classEntry, methodKey);
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeAttrRefAddr(methodSpecOffset, buffer, pos);
        pos = writeMethodParameterDeclarations(context, classEntry, primary.getMethodEntry(), false, buffer, pos);
        if (!primary.isLeaf()) {
            /*
             * the method has inlined ranges so write concrete inlined method entries as its
             * children
             */
            pos = generateConcreteInlinedMethods(context, classEntry, primaryEntry, buffer, pos);
        }
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeAbstractInlineMethod(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        String methodKey = method.getSymbolName();
        log(context, "  [0x%08x] abstract inline method %s", pos, method.getSymbolName());
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_abstract_inline_method;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     inline  0x%x", pos, DwarfDebugInfo.DW_INL_inlined);
        pos = writeAttrData1(DwarfDebugInfo.DW_INL_inlined, buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfDebugInfo.DW_FLAG_true, buffer, pos);
        int methodSpecOffset = getMethodDeclarationIndex(classEntry, methodKey);
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeAttrRefAddr(methodSpecOffset, buffer, pos);
        pos = writeMethodParameterDeclarations(context, classEntry, method, false, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeInlineSubroutine(DebugContext context, ClassEntry classEntry, Range range, int subprogramOffset, int depth, byte[] buffer, int p) {
        assert range.isInlined();
        int pos = p;
        log(context, "  [0x%08x] concrete inline subroutine [0x%x, 0x%x] %s", pos, range.getLo(), range.getHi(), range.getSymbolName());
        final Range callerSubrange = range.getCaller();
        assert callerSubrange != null;
        int callLine = callerSubrange.getLine();
        assert callLine >= -1 : callLine;
        Integer fileIndex;
        if (callLine == -1) {
            log(context, "  Unable to retrieve call line for inlined method %s", range.getFullMethodName());
            /* continue with line 0 and fileIndex 1 as we must insert a tree node */
            callLine = 0;
            fileIndex = 1;
        } else {
            if (callerSubrange == range) {
                fileIndex = 1;
            } else {
                FileEntry subFileEntry = callerSubrange.getFileEntry();
                assert subFileEntry != null : callerSubrange.getClassName() + "." + callerSubrange.getMethodName() + "(" + callerSubrange.getFileName() + ":" + callLine + ")";
                fileIndex = classEntry.localFilesIdx(subFileEntry);
                assert fileIndex != null;
            }
        }
        final int code;
        if (range.isLeaf()) {
            code = DwarfDebugInfo.DW_ABBREV_CODE_inlined_subroutine;
        } else {
            code = DwarfDebugInfo.DW_ABBREV_CODE_inlined_subroutine_with_children;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number  %d", pos, depth + 1, code);
        pos = writeAbbrevCode(code, buffer, pos);
        log(context, "  [0x%08x]     abstract_origin  0x%x", pos, subprogramOffset);
        pos = writeAttrRef4(subprogramOffset, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, range.getLo());
        pos = writeAttrAddress(range.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, range.getHi());
        pos = writeAttrAddress(range.getHi(), buffer, pos);
        log(context, "  [0x%08x]     call_file  %d", pos, fileIndex);
        pos = writeAttrData4(fileIndex, buffer, pos);
        log(context, "  [0x%08x]     call_line  %d", pos, callLine);
        pos = writeAttrData4(callLine, buffer, pos);
        return pos;
    }

    private int writeAttrRef4(int reference, byte[] buffer, int p) {
        return writeAttrData4(reference, buffer, p);
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            /* CU length. */
            pos += putInt(0, scratch, 0);
            /* DWARF version. */
            pos += putShort(DwarfDebugInfo.DW_VERSION_4, scratch, 0);
            /* Abbrev offset. */
            pos += putInt(0, scratch, 0);
            /* Address size. */
            return pos + putByte((byte) 8, scratch, 0);
        } else {
            /* CU length. */
            pos = putInt(0, buffer, pos);
            /* DWARF version. */
            pos = putShort(DwarfDebugInfo.DW_VERSION_4, buffer, pos);
            /* Abbrev offset. */
            pos = putInt(0, buffer, pos);
            /* Address size. */
            return putByte((byte) 8, buffer, pos);
        }
    }

    private static int findLo(List<PrimaryEntry> classPrimaryEntries, boolean isDeoptTargetCU) {
        if (!isDeoptTargetCU) {
            /* First entry is the one we want. */
            return classPrimaryEntries.get(0).getPrimary().getLo();
        } else {
            /* Need the first entry which is a deopt target. */
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                Range range = primaryEntry.getPrimary();
                if (range.isDeoptTarget()) {
                    return range.getLo();
                }
            }
        }
        /* We should never get here. */
        assert false : "should not reach";
        return 0;
    }

    private static int findHi(List<PrimaryEntry> classPrimaryEntries, boolean includesDeoptTarget, boolean isDeoptTargetCU) {
        if (isDeoptTargetCU || !includesDeoptTarget) {
            assert classPrimaryEntries.size() > 0 : "expected to find primary methods";
            /* Either way the last entry is the one we want. */
            return classPrimaryEntries.get(classPrimaryEntries.size() - 1).getPrimary().getHi();
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
        /* We should never get here. */
        assert false : "should not reach";
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
            return putUTF8StringBytes(value, buffer, pos);
        }
    }

    public int writeAttrAccessibility(int modifiers, byte[] buffer, int p) {
        byte access;
        if (Modifier.isPublic(modifiers)) {
            access = DwarfDebugInfo.DW_ACCESS_public;
        } else if (Modifier.isProtected(modifiers)) {
            access = DwarfDebugInfo.DW_ACCESS_protected;
        } else if (Modifier.isPrivate(modifiers)) {
            access = DwarfDebugInfo.DW_ACCESS_private;
        } else {
            /* Actually package private -- make it public for now. */
            access = DwarfDebugInfo.DW_ACCESS_public;
        }
        return writeAttrData1(access, buffer, p);
    }

    public int writeIndirectOopConversionExpression(boolean isHub, byte[] buffer, int p) {
        int pos = p;
        /*
         * For an explanation of the conversion rules @see com.oracle.svm.core.heap.ReferenceAccess
         *
         * n.b.
         *
         * The setting for option -H:+/-SpawnIsolates is determined by useHeapBase == true/false.
         *
         * The setting for option -H:+/-UseCompressedReferences is determined by oopShiftCount ==
         * zero/non-zero
         */

        boolean useHeapBase = dwarfSections.useHeapBase();
        int oopCompressShift = dwarfSections.oopCompressShift();
        int oopTagsShift = dwarfSections.oopTagsShift();
        int oopAlignShift = dwarfSections.oopAlignShift();
        /* we may be able to use a mask or a right shift then a left shift or just a left shift */
        int mask = 0;
        int rightShift = 0;
        int leftShift = 0;
        int exprSize = 0;

        /*
         * First we compute the size of the locexpr and decide how to do any required bit-twiddling
         */
        if (!useHeapBase) {
            /* We must be compressing for a hub otherwise this call would not be needed. */
            assert isHub == true;
            mask = dwarfSections.oopTagsMask();
            assert mask != 0;
            /*-
             * We don't need to care about zero oops just mask off the tag bits.
             *
             * required expression is
             *
             *  .... push object address .. (1 byte) ..... [tagged oop]
             *  .... push mask ............ (1 byte) ..... [tagged oop, mask]
             *  .... NOT .................. (1 byte) ..... [tagged oop, ~mask]
             *  .... AND .................. (1 byte) ..... [raw oop]
             */
            exprSize += 4;
        } else {
            /*-
             * required expression will be one of these paths
             *
             *  .... push object address .. (1 byte) ..... [offset]
             *  .... duplicate object base  (1 byte) ..... [offset, offset]
             *  .... push 0 ............... (1 byte) ..... [offset, offset, 0]
             *  .... eq ................... (1 byte) ..... [offset]
             *  .... brtrue end ........... (3 bytes) .... [offset == oop == 0 if taken]
             *  IF mask != 0
             *  .... push mask ............ (1 byte) ..... [offset, mask]
             *  .... NOT .................. (1 byte) ..... [offset, ~mask]
             *  .... AND .................. (1 byte) ..... [offset]
             *  ELSE
             *    IF rightShift != 0
             *  .... push rightShift ...... (1 byte) ..... [offset, right shift]
             *  .... LSHR ................. (1 byte) ..... [offset]
             *    END IF
             *    IF leftShift != 0
             *  .... push leftShift ....... (1 byte) ..... [offset, left shift]
             *  .... LSHL ................. (1 byte) ..... [offset]
             *    END IF
             *  END IF
             *  .... push rheap+0 ......... (2 bytes) .... [offset, rheap]
             *  .... ADD .................. (1 byte) ..... [oop]
             * end: ...................................... [oop]
             *
             */
            /* Count all bytes in common path */
            exprSize += 10;
            if (isHub) {
                if (oopCompressShift == 0) {
                    /* We need to use oopAlignment for the shift. */
                    oopCompressShift = oopAlignShift;
                }
                if (oopCompressShift == oopTagsShift) {
                    /* We can use a mask to remove the bits. */
                    mask = dwarfSections.oopTagsMask();
                    exprSize += 3;
                } else {
                    /* We need two shifts to remove the bits. */
                    rightShift = oopTagsShift;
                    leftShift = oopCompressShift;
                    exprSize += 4;
                }
            } else {
                /* No flags to deal with, so we need either an uncompress or nothing. */
                if (oopCompressShift != 0) {
                    leftShift = oopCompressShift;
                    exprSize += 2;
                }
            }
        }
        if (buffer == null) {
            /* We need to write size as a ULEB then leave space for size instructions. */
            return pos + putULEB(exprSize, scratch, 0) + exprSize;

        } else {
            /* Write size followed by the expression and check the size comes out correct. */
            pos = putULEB(exprSize, buffer, pos);
            int exprStart = pos;
            if (!useHeapBase) {
                pos = putByte(DwarfDebugInfo.DW_OP_push_object_address, buffer, pos);
                pos = putByte((byte) (DwarfDebugInfo.DW_OP_lit0 + mask), buffer, pos);
                pos = putByte(DwarfDebugInfo.DW_OP_not, buffer, pos);
                pos = putByte(DwarfDebugInfo.DW_OP_and, buffer, pos);
            } else {
                pos = putByte(DwarfDebugInfo.DW_OP_push_object_address, buffer, pos);
                /* skip to end if oop is null */
                pos = putByte(DwarfDebugInfo.DW_OP_dup, buffer, pos);
                pos = putByte(DwarfDebugInfo.DW_OP_lit0, buffer, pos);
                pos = putByte(DwarfDebugInfo.DW_OP_eq, buffer, pos);
                int skipStart = pos + 3; /* offset excludes BR op + 2 operand bytes */
                short offsetToEnd = (short) (exprSize - (skipStart - exprStart));
                pos = putByte(DwarfDebugInfo.DW_OP_bra, buffer, pos);
                pos = putShort(offsetToEnd, buffer, pos);
                /* insert mask or shifts as necessary */
                if (mask != 0) {
                    pos = putByte((byte) (DwarfDebugInfo.DW_OP_lit0 + mask), buffer, pos);
                    pos = putByte(DwarfDebugInfo.DW_OP_not, buffer, pos);
                    pos = putByte(DwarfDebugInfo.DW_OP_and, buffer, pos);
                } else {
                    if (rightShift != 0) {
                        pos = putByte((byte) (DwarfDebugInfo.DW_OP_lit0 + rightShift), buffer, pos);
                        pos = putByte(DwarfDebugInfo.DW_OP_shr, buffer, pos);
                    }
                    if (leftShift != 0) {
                        pos = putByte((byte) (DwarfDebugInfo.DW_OP_lit0 + leftShift), buffer, pos);
                        pos = putByte(DwarfDebugInfo.DW_OP_shl, buffer, pos);
                    }
                }
                /* add the resulting offset to the heapbase register */
                byte regOp = (byte) (DwarfDebugInfo.DW_OP_breg0 + dwarfSections.getHeapbaseRegister());
                pos = putByte(regOp, buffer, pos);
                pos = putSLEB(0, buffer, pos); /* 1 byte. */
                pos = putByte(DwarfDebugInfo.DW_OP_plus, buffer, pos);
                assert pos == skipStart + offsetToEnd;
            }
            /* make sure we added up correctly */
            assert pos == exprStart + exprSize;
        }
        return pos;
    }

    /**
     * The debug_info section depends on abbrev section.
     */
    protected static final String TARGET_SECTION_NAME = DwarfDebugInfo.TEXT_SECTION_NAME;

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
