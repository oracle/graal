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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignTypeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * The name of a special DWARF struct type used to model an object header.
     */
    private static final String OBJECT_HEADER_STRUCT_NAME = "_objhdr";

    /**
     * An info header section always contains a fixed number of bytes.
     */
    private static final int DW_DIE_HEADER_SIZE = 11;
    /**
     * Normally the offset of DWARF type declarations are tracked using the type/class entry properties
     * but that means they are only available to be read during the second pass when filling in type
     * cross-references. However, we need to use the offset of the void type during the first pass
     * as the target of later-generated  foreign pointer types. So, this field saves it up front.
     */
    private int voidOffset;

    public DwarfInfoSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
        // initialize to an invalid value
        voidOffset = -1;
    }

    @Override
    public String getSectionName() {
        return DwarfDebugInfo.DW_INFO_SECTION_NAME;
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
        // Write the single Java compile unit header
        int lengthPos = pos;
        log(context, "  [0x%08x] <0> Java Compile Unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + DW_DIE_HEADER_SIZE;
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_unit;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        String name = uniqueDebugString("JAVA");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String compilationDirectory = dwarfSections.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);
        /*
         * Code addresses start at offset 0 and range up to the limit defined by the code cache
         * size.
         */
        int lo = 0;
        int hi = dwarfSections.compiledCodeMax();
        // there is one line section starting at offset 0
        int lineIndex = 0;
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
        pos = writeLineSectionOffset(lineIndex, buffer, pos);

        /* Write DIEs for primitive types and header struct */

        pos = writeBuiltInTypes(context, buffer, pos);

        /*
         * Write DIEs for all instance classes, which includes interfaces and enums
         */

        pos = writeInstanceClasses(context, buffer, pos);

        /* Write DIEs for array types. */

        pos = writeArrays(context, buffer, pos);

        /* Write all compiled code locations */

        pos = writeMethodLocations(context, buffer, pos);

        /* Write all static field definitions -- in class order */

        pos = writeStaticFieldLocations(context, buffer, pos);

        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */

        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeBuiltInTypes(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] <1> Builtin Types:", pos);
        /* Write child entries for primitive Java types. */

        pos = primitiveTypeStream().reduce(pos,
                        (pos1, primitiveTypeEntry) -> {
                            if (primitiveTypeEntry.getBitCount() > 0) {
                                return writePrimitiveType(context, primitiveTypeEntry, buffer, pos1);
                            } else {
                                return writeVoidType(context, primitiveTypeEntry, buffer, pos1);
                            }
                        },
                        (oldpos, newpos) -> newpos);

        /* Write child entry for object/array header struct. */

        pos = writeHeaderType(context, headerType(), buffer, pos);

        /* write class constants for primitive type classes */

        pos = primitiveTypeStream().reduce(pos,
                        (pos1, primitiveTypeEntry) -> writeClassConstantDeclaration(context, primitiveTypeEntry, buffer, pos1),
                        (oldpos, newpos) -> newpos);

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
        return writeStrSectionOffset(name, buffer, pos);
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
        // specially record void type offset for immediate use during first pass of info generation
        // we need to use it as the base layout for foreign types
        assert voidOffset == -1 || voidOffset == pos;
        voidOffset = pos;
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_void_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = primitiveTypeEntry.getTypeName();
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeStrSectionOffset(name, buffer, pos);
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
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData1(size, buffer, pos);
        pos = writeStructFields(context, headerTypeEntry.fields(), buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeStructFields(DebugContext context, Stream<FieldEntry> fields, byte[] buffer, int p) {
        Cursor cursor = new Cursor(p);
        fields.forEach( fieldEntry -> {
            cursor.set(writeStructField(context, fieldEntry, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeStructField(DebugContext context, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        String fieldName = fieldEntry.fieldName();
        TypeEntry valueType = fieldEntry.getValueType();
        /* use the indirect type for the field so pointers get translated */
        int valueTypeIdx = getIndirectTypeIndex(valueType);
        log(context, "  [0x%08x] header field", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_header_field;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeStrSectionOffset(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, valueTypeIdx, valueType.getTypeName());
        pos = writeInfoSectionOffset(valueTypeIdx, buffer, pos);
        byte offset = (byte) fieldEntry.getOffset();
        int size = fieldEntry.getSize();
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData1(offset, buffer, pos);
        int modifiers = fieldEntry.getModifiers();
        log(context, "  [0x%08x]     modifiers %s", pos, fieldEntry.getModifiersString());
        return writeAttrAccessibility(modifiers, buffer, pos);
    }

    private int writeInstanceClasses(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] instance classes", pos);
        Cursor cursor = new Cursor(pos);
        instanceClassStream().forEach(classEntry -> {
            cursor.set(writeInstanceClassInfo(context, classEntry, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeInstanceClassInfo(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        /* if the class has a loader then embed the children in a namespace */
        String loaderId = classEntry.getLoaderId();
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /* Now write the child DIEs starting with the layout and pointer type. */

        if (classEntry.isInterface()) {
            InterfaceClassEntry interfaceClassEntry = (InterfaceClassEntry) classEntry;
            pos = writeInterfaceLayout(context, interfaceClassEntry, buffer, pos);
            pos = writeInterfaceType(context, interfaceClassEntry, buffer, pos);
        } else if (classEntry.isForeign()) {
            ForeignTypeEntry foreignTypeEntry = (ForeignTypeEntry) classEntry;
            pos = writeForeignLayout(context, foreignTypeEntry, buffer,pos);
            pos = writeForeignType(context, foreignTypeEntry, buffer,pos);
        } else {
            pos = writeClassLayout(context, classEntry, buffer, pos);
            pos = writeClassType(context, classEntry, buffer, pos);
        }

        /* Write a declaration for the special Class object pseudo-static field */
        pos = writeClassConstantDeclaration(context, classEntry, buffer, pos);

        /* if we opened a namespace then terminate its children */

        if (!loaderId.isEmpty()) {
            pos = writeAttrNull(buffer, pos);
        }

        return pos;
    }

    private int writeNameSpace(DebugContext context, String id, byte[] buffer, int p) {
        int pos = p;
        String name = uniqueDebugString(id);
        assert !id.isEmpty();
        log(context, "  [0x%08x] namespace %s", pos, name);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_namespace;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        return pos;
    }

    private int writeClassConstantDeclaration(DebugContext context, TypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] class constant", pos);
        long offset = typeEntry.getClassOffset();
        if (offset < 0) {
            return pos;
        }
        // Write a special static field declaration for the class object
        // we use the abbrev code for a static field with no file or line location
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_class_constant;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = uniqueDebugString(typeEntry.getTypeName() + ".class");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        /*
         * This is a direct reference to the object rather than a compressed oop reference. So, we
         * need to use the direct layout type for hub class to type it.
         */
        ClassEntry valueType = dwarfSections.getHubClassEntry();
        int typeIdx = (valueType == null ? -1 : getLayoutIndex(valueType));
        log(context, "  [0x%08x]     type  0x%x (<hub type>)", pos, typeIdx);
        pos = writeInfoSectionOffset(typeIdx, buffer, pos);
        log(context, "  [0x%08x]     accessibility public static final", pos);
        pos = writeAttrAccessibility(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, buffer, pos);
        log(context, "  [0x%08x]     external(true)", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        log(context, "  [0x%08x]     definition(true)", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        /*
         * We need to force encoding of this location as a heap base relative relocatable address
         * rather than an offset from the heapbase register.
         */
        log(context, "  [0x%08x]     location  heapbase + 0x%x (class constant)", pos, offset);
        pos = writeHeapLocationExprLoc(offset, false, buffer, pos);
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
        pos = writeStrSectionOffset(name, buffer, pos);
        int size = classEntry.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        int fileIdx = classEntry.getFileIdx();
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
            TypeEntry headerType = headerType();
            superTypeOffset = getTypeIndex(headerType);
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
            pos = writeStrSectionOffset(indirectName, buffer, pos);
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
        } else {
            setIndirectLayoutIndex(classEntry, layoutIndex);
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
        pos = writeInfoSectionOffset(superTypeOffset, buffer, pos);
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
        pos = writeStrSectionOffset(name, buffer, pos);
        /* We may not have a file and line for a field. */
        if (hasFile) {
            assert entry instanceof ClassEntry;
            int fileIdx = fieldEntry.getFileIdx();
            assert fileIdx > 0;
            log(context, "  [0x%08x]     filename  0x%x (%s)", pos, fileIdx, fieldEntry.getFileName());
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            /* At present we definitely don't have line numbers. */
        }
        TypeEntry valueType = fieldEntry.getValueType();
        /* use the indirect type for the field so pointers get translated if needed */
        int typeIdx = getIndirectTypeIndex(valueType);
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeIdx, valueType.getTypeName());
        pos = writeInfoSectionOffset(typeIdx, buffer, pos);
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
                 * Declare all methods whether or not they have been compiled or inlined.
                 */
                pos = writeMethodDeclaration(context, classEntry, method, buffer, pos);
            }
        }

        return pos;
    }

    private int writeMethodDeclaration(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        String methodKey = method.getSymbolName();
        String linkageName = uniqueDebugString(methodKey);
        setMethodDeclarationIndex(method, pos);
        int modifiers = method.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        log(context, "  [0x%08x] method declaration %s::%s", pos, classEntry.getTypeName(), method.methodName());
        int abbrevCode = (isStatic ? DwarfDebugInfo.DW_ABBREV_CODE_method_declaration_static : DwarfDebugInfo.DW_ABBREV_CODE_method_declaration);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        String name = uniqueDebugString(method.methodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        FileEntry fileEntry = method.getFileEntry();
        if (fileEntry == null) {
            fileEntry = classEntry.getFileEntry();
        }
        assert fileEntry != null;
        int fileIdx = fileEntry.getIdx();
        log(context, "  [0x%08x]     file 0x%x (%s)", pos, fileIdx, fileEntry.getFullName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);
        int line = method.getLine();
        log(context, "  [0x%08x]     line 0x%x", pos, line);
        pos = writeAttrData2((short) line, buffer, pos);
        log(context, "  [0x%08x]     linkage_name %s", pos, linkageName);
        pos = writeStrSectionOffset(linkageName, buffer, pos);
        TypeEntry returnType = method.getValueType();
        int retTypeIdx = getTypeIndex(returnType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeIdx, returnType.getTypeName());
        pos = writeInfoSectionOffset(retTypeIdx, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, method.isDeopt() ? "true" : "false");
        pos = writeFlag((method.isDeopt() ? (byte) 1 : (byte) 0), buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        int typeIdx = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     containing_type 0x%x (%s)", pos, typeIdx, classEntry.getTypeName());
        pos = writeInfoSectionOffset(typeIdx, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_declaration) {
            /* Record the current position so we can back patch the object pointer. */
            int objectPointerIndex = pos;
            /*
             * Write a dummy ref address to move pos on to where the first parameter gets written.
             *
             * n.b. buffer is passed as null so we don't attempt to create a reloc!
             */
            pos = writeInfoSectionOffset(0, null, pos);
            /*
             * Now backpatch object pointer slot with current pos, identifying the first parameter.
             */
            log(context, "  [0x%08x]     object_pointer 0x%x", objectPointerIndex, pos);
            writeInfoSectionOffset(pos, buffer, objectPointerIndex);
        }
        /* Write method parameter declarations. */
        pos = writeMethodParameterDeclarations(context, method, fileIdx, 3, buffer, pos);
        /* write method local declarations */
        pos = writeMethodLocalDeclarations(context, method, fileIdx, 3, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeMethodParameterDeclarations(DebugContext context, MethodEntry method, int fileIdx, int level, byte[] buffer, int p) {
        int pos = p;
        int refAddr;
        if (!Modifier.isStatic(method.getModifiers())) {
            refAddr = pos;
            DebugLocalInfo paramInfo = method.getThisParam();
            setMethodLocalIndex(method, paramInfo, refAddr);
            pos = writeMethodParameterDeclaration(context, paramInfo, fileIdx, true, level, buffer, pos);
        }
        for (int i = 0; i < method.getParamCount(); i++) {
            refAddr = pos;
            DebugLocalInfo paramInfo = method.getParam(i);
            setMethodLocalIndex(method, paramInfo, refAddr);
            pos = writeMethodParameterDeclaration(context, paramInfo, fileIdx, false, level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodParameterDeclaration(DebugContext context, DebugLocalInfo paramInfo, int fileIdx, boolean artificial, int level, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        int abbrevCode;
        String paramName = paramInfo.name();
        TypeEntry paramType = lookupType(paramInfo.valueType());
        int line = paramInfo.line();
        if (artificial) {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1;
        } else if (line >= 0) {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2;
        } else {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration3;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name %s", pos, paramName);
        pos = writeStrSectionOffset(uniqueDebugString(paramName), buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2) {
            log(context, "  [0x%08x]     file 0x%x", pos, fileIdx);
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            log(context, "  [0x%08x]     line 0x%x", pos, line);
            pos = writeAttrData2((short) line, buffer, pos);
        }
        int typeIdx = getTypeIndex(paramType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeIdx, paramType.getTypeName());
        pos = writeInfoSectionOffset(typeIdx, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1) {
            log(context, "  [0x%08x]     artificial true", pos);
            pos = writeFlag((byte) 1, buffer, pos);
        }
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
        return pos;
    }

    private int writeMethodLocalDeclarations(DebugContext context, MethodEntry method, int fileIdx, int level, byte[] buffer, int p) {
        int pos = p;
        int refAddr;
        for (int i = 0; i < method.getLocalCount(); i++) {
            refAddr = pos;
            DebugLocalInfo localInfo = method.getLocal(i);
            setMethodLocalIndex(method, localInfo, refAddr);
            pos = writeMethodLocalDeclaration(context, localInfo, fileIdx, level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalDeclaration(DebugContext context, DebugLocalInfo paramInfo, int fileIdx, int level, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method local declaration", pos);
        int abbrevCode;
        String paramName = paramInfo.name();
        TypeEntry paramType = lookupType(paramInfo.valueType());
        int line = paramInfo.line();
        if (line >= 0) {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_local_declaration1;
        } else {
            abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_method_local_declaration2;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name %s", pos, paramName);
        pos = writeStrSectionOffset(uniqueDebugString(paramName), buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_local_declaration1) {
            log(context, "  [0x%08x]     file 0x%x", pos, fileIdx);
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            log(context, "  [0x%08x]     line 0x%x", pos, line);
            pos = writeAttrData2((short) line, buffer, pos);
        }
        int typeIdx = getTypeIndex(paramType);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeIdx, paramType.getTypeName());
        pos = writeInfoSectionOffset(typeIdx, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag((byte) 1, buffer, pos);
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
        pos = writeStrSectionOffset(name, buffer, pos);
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
            pos = writeStrSectionOffset(indirectName, buffer, pos);
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
        } else {
            setIndirectLayoutIndex(interfaceClassEntry, layoutOffset);
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
        pos = writeStrSectionOffset(name, buffer, pos);
        int layoutOffset = getLayoutIndex(classEntry);
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, layoutOffset, classEntry.getTypeName());
        pos = writeInfoSectionOffset(layoutOffset, buffer, pos);
        int modifiers = Modifier.PUBLIC;
        log(context, "  [0x%08x]     modifiers %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeForeignLayout(DebugContext context, ForeignTypeEntry foreignTypeEntry, byte[] buffer, int p) {
        int pos = p;
        int size = foreignTypeEntry.getSize();
        int layoutOffset = pos;
        if (foreignTypeEntry.isWord()) {
            // define the type as a typedef for a signed or unsigned word i.e. we don't have a layout type
            pos = writeForeignWordLayout(context, foreignTypeEntry, size, foreignTypeEntry.isSigned(), buffer, pos);
        } else if (foreignTypeEntry.isIntegral()) {
            // use a suitably sized signed or unsigned integral type as the layout type
            pos = writeForeignIntegerLayout(context, foreignTypeEntry, size, foreignTypeEntry.isSigned(), buffer, pos);
        } else if (foreignTypeEntry.isFloat()) {
            // use a suitably sized signed or unsigned float type as the layout type
            pos = writeForeignFloatLayout(context, foreignTypeEntry, size, buffer, pos);
        } else {
            // pointer or unknown - layout id as a foreign stucture if we have fields otherwise use void
            if (foreignTypeEntry.fieldCount() > 0) {
                // define this type using a structure layout
                pos = writeForeignStructLayout(context, foreignTypeEntry, size, buffer, pos);
            } else {
                // by default the referent of the pointer type will be void
                layoutOffset = voidOffset;
                String referentName = "void";
                if (foreignTypeEntry.isPointer()) {
                    TypeEntry pointerTo = foreignTypeEntry.getPointerTo();
                    if (pointerTo != null) {
                        // define this type as a typedef for a pointer to the referent
                        layoutOffset = getTypeIndex(foreignTypeEntry.getPointerTo());
                        referentName = foreignTypeEntry.getTypeName();
                    }
                }
                log(context, "  [0x%08x] foreign pointer type %s referent 0x%x (%s)", pos, foreignTypeEntry.getTypeName(), layoutOffset, referentName);
            }
        }
        setLayoutIndex(foreignTypeEntry, layoutOffset);

        /*
         * Write declarations for methods of the foreign types as functions
         *
         * n.b. these appear as standalone declarations rather than as children of a
         * class layout DIE so we don't need a terminating  attribute.
         */
        pos = writeMethodDeclarations(context, foreignTypeEntry, buffer, pos);
        /*
         * We don't need an indirect type because foreign pointers are never compressed
         */
        setIndirectLayoutIndex(foreignTypeEntry, layoutOffset);

        return pos;
    }

    private int writeForeignStructLayout(DebugContext context, ForeignTypeEntry foreignTypeEntry, int size, byte[] buffer, int p) {
        // we should only arrive here if we have fields
        assert foreignTypeEntry.fieldCount() > 0;
        int pos = p;
        log(context, "  [0x%08x] foreign struct type for %s", pos, foreignTypeEntry.getTypeName());
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_foreign_struct;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String typedefName = foreignTypeEntry.getTypedefName();
        if (typedefName.startsWith("struct ")) {
            // log this before correcting it so we have some hope of clearing it up
            log(context, "  [0x%08x]     typedefName includes redundant keyword struct %s", pos, typedefName);
            typedefName = typedefName.substring("struct ".length());
        }
        if (typedefName == null) {
            typedefName = "_" + foreignTypeEntry.getTypeName();
            verboseLog(context, "  [0x%08x]   using synthetic typedef name %s", pos, typedefName);
        }
        typedefName = uniqueDebugString(typedefName);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(typedefName), typedefName);
        pos = writeStrSectionOffset(typedefName, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData1((byte) size, buffer, pos);
        pos = writeStructFields(context, foreignTypeEntry.fields(), buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeForeignWordLayout(DebugContext context, ForeignTypeEntry foreignTypeEntry, int size, boolean isSigned, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] foreign primitive word type for %s", pos, foreignTypeEntry.getTypeName());
        /* Record the location of this type entry. */
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        assert size >= 0;
        byte byteSize = (byte) (size > 0 ? size : dwarfSections.pointerSize());
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) (byteSize * 8);
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        // treat the layout as an unsigned word
        byte encoding = (isSigned ? DwarfDebugInfo.DW_ATE_signed : DwarfDebugInfo.DW_ATE_unsigned);
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding);
        pos = writeAttrData1(encoding, buffer, pos);
        String name = uniqueDebugString(integralTypeName(byteSize, isSigned));
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeStrSectionOffset(name, buffer, pos);
    }

    private int writeForeignIntegerLayout(DebugContext context, ForeignTypeEntry foreignTypeEntry, int size, boolean isSigned, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] foreign primitive integral type for %s", pos, foreignTypeEntry.getTypeName());
        /* Record the location of this type entry. */
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        assert size > 0;
        byte byteSize = (byte) size;
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) (byteSize * 8);
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        // treat the layout as an unsigned word
        byte encoding = (isSigned ? DwarfDebugInfo.DW_ATE_signed : DwarfDebugInfo.DW_ATE_unsigned);
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding);
        pos = writeAttrData1(encoding, buffer, pos);
        String name = uniqueDebugString(integralTypeName(byteSize, isSigned));
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeStrSectionOffset(name, buffer, pos);
    }

    private int writeForeignFloatLayout(DebugContext context, ForeignTypeEntry foreignTypeEntry, int size, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] foreign primitive float type for %s", pos, foreignTypeEntry.getTypeName());
        /* Record the location of this type entry. */
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        assert size > 0;
        byte byteSize = (byte) size;
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) (byteSize * 8);
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        // treat the layout as an unsigned word
        byte encoding = DwarfDebugInfo.DW_ATE_float;
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding);
        pos = writeAttrData1(encoding, buffer, pos);
        String name = uniqueDebugString(size == 4 ? "float" : (size == 8 ? "double" : "long double"));
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        return writeStrSectionOffset(name, buffer, pos);
    }

    private String integralTypeName(int byteSize, boolean isSigned) {
        assert (byteSize & (byteSize - 1)) == 0 : "expecting a power of 2!";
        StringBuilder stringBuilder = new StringBuilder();
        if (!isSigned) {
            stringBuilder.append('u');
        }
        stringBuilder.append("int");
        stringBuilder.append(8 * byteSize);
        stringBuilder.append("_t");
        return stringBuilder.toString();
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
        pos = writeInfoSectionOffset(layoutOffset, buffer, pos);

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
            pos = writeInfoSectionOffset(layoutOffset, buffer, pos);
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
        pos = writeInfoSectionOffset(layoutOffset, buffer, pos);

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
            pos = writeInfoSectionOffset(layoutOffset, buffer, pos);
        } else {
            setIndirectTypeIndex(interfaceClassEntry, typeIdx);
        }

        return pos;
    }

    private int writeForeignType(DebugContext context, ForeignTypeEntry foreignTypeEntry, byte[] buffer, int p) {
        int pos = p;
        int layoutOffset = getLayoutIndex(foreignTypeEntry);

        // Unlike with Java we use the Java name for the pointer type rather than the
        // underlying base type, or rather for a typedef that targets the pointer type.
        // That ensures that e.g. CCharPointer is a typedef for char*. 

        /* Define a pointer type referring to the base type */
        int refTypeIdx = pos;
        log(context, "  [0x%08x] foreign pointer type", pos);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_foreign_pointer;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, layoutOffset);
        pos = writeInfoSectionOffset(layoutOffset, buffer, pos);

        /* Define a typedef for the layout type using the Java name. */
        int typedefIdx = pos;
        log(context, "  [0x%08x] foreign typedef", pos);
        abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_foreign_typedef;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(foreignTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name %s", pos, name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
        pos = writeInfoSectionOffset(refTypeIdx, buffer, pos);

        setTypeIndex(foreignTypeEntry, typedefIdx);
        // foreign pointers are never stored compressed so don't need a separate indirect type
        setIndirectTypeIndex(foreignTypeEntry, typedefIdx);

        return pos;
    }
    private int writeStaticFieldLocations(DebugContext context, byte[] buffer, int p) {
        Cursor cursor = new Cursor(p);
        instanceClassStream().forEach(classEntry -> {
            cursor.set(writeClassStaticFieldLocations(context, classEntry, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeClassStaticFieldLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        /*
         * Only write locations for static fields that have an offset greater than 0. A negative
         * offset indicates that the field has been folded into code as an unmaterialized constant.
         */
        Cursor cursor = new Cursor(p);
        classEntry.fields().filter(DwarfInfoSectionImpl::isManifestedStaticField)
                        .forEach(fieldEntry -> {
                            cursor.set(writeClassStaticFieldLocation(context, classEntry, fieldEntry, buffer, cursor.get()));
                        });
        return cursor.get();
    }

    private static boolean isManifestedStaticField(FieldEntry fieldEntry) {
        return Modifier.isStatic(fieldEntry.getModifiers()) && fieldEntry.getOffset() >= 0;
    }

    private int writeClassStaticFieldLocation(DebugContext context, ClassEntry classEntry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        String fieldName = fieldEntry.fieldName();
        int fieldDefinitionOffset = getFieldDeclarationIndex(classEntry, fieldName);
        log(context, "  [0x%08x] static field location %s.%s", pos, classEntry.getTypeName(), fieldName);
        int abbrevCode = DwarfDebugInfo.DW_ABBREV_CODE_static_field_location;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // n.b. field definition offset gets written as a Ref4, relative to CU start
        log(context, "  [0x%08x]     specification  0x%x", pos, fieldDefinitionOffset);
        pos = writeAttrRef4(fieldDefinitionOffset, buffer, pos);
        /* Field offset needs to be relocated relative to static primitive or static object base. */
        int offset = fieldEntry.getOffset();
        log(context, "  [0x%08x]     location  heapbase + 0x%x (%s)", pos, offset, (fieldEntry.getValueType().isPrimitive() ? "primitive" : "object"));
        pos = writeHeapLocationExprLoc(offset, buffer, pos);
        return pos;
    }

    private int writeArrays(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] array classes", p);
        Cursor cursor = new Cursor(p);
        arrayTypeStream().forEach(arrayTypeEntry -> {
            cursor.set(writeArray(context, arrayTypeEntry, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeArray(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        /* if the array base type is a class with a loader then embed the children in a namespace */
        String loaderId = arrayTypeEntry.getLoaderId();
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

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

        /* Write a declaration for the special Class object pseudo-static field */
        pos = writeClassConstantDeclaration(context, arrayTypeEntry, buffer, pos);

        /* if we opened a namespace then terminate its children */
        if (!loaderId.isEmpty()) {
            pos = writeAttrNull(buffer, pos);
        }

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
        pos = writeStrSectionOffset(name, buffer, pos);
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
        Cursor cursor = new Cursor(p);
        arrayTypeEntry.fields().filter(DwarfInfoSectionImpl::isManifestedField)
                        .forEach(fieldEntry -> {
                            cursor.set(writeField(context, arrayTypeEntry, fieldEntry, buffer, cursor.get()));
                        });
        return cursor.get();
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
        pos = writeStrSectionOffset(indirectName, buffer, pos);
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
        int elementTypeIdx = getIndirectTypeIndex(elementType);
        log(context, "  [0x%08x]     type idx 0x%x (%s)", pos, elementTypeIdx, elementTypeName);
        pos = writeInfoSectionOffset(elementTypeIdx, buffer, pos);
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
        pos = writeStrSectionOffset(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type idx 0x%x", pos, arrayDataTypeIdx);
        pos = writeInfoSectionOffset(arrayDataTypeIdx, buffer, pos);
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
        TypeEntry objectType = lookupObjectClass();
        String superName = objectType.getTypeName();
        assert objectType != null;
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
        pos = writeInfoSectionOffset(layoutOffset, buffer, pos);

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
            pos = writeInfoSectionOffset(indirectLayoutOffset, buffer, pos);
        } else {
            setIndirectTypeIndex(arrayTypeEntry, typeIdx);
        }

        return pos;
    }

    private int writeMethodLocations(DebugContext context, byte[] buffer, int p) {
        Cursor cursor = new Cursor(p);
        compiledMethodsStream().forEach(compiledMethodEntry -> {
            cursor.set(writeMethodLocation(context, compiledMethodEntry, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeMethodLocation(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledEntry.getPrimary();
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
        int methodSpecOffset = getMethodDeclarationIndex(primary.getMethodEntry());
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeInfoSectionOffset(methodSpecOffset, buffer, pos);
        HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = primary.getVarRangeMap();
        pos = writeMethodParameterLocations(context, varRangeMap, primary, 2, buffer, pos);
        pos = writeMethodLocalLocations(context, varRangeMap, primary, 2, buffer, pos);
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

    private int writeMethodParameterLocations(DebugContext context, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
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
            int refAddr = getMethodLocalIndex(methodEntry, thisParamInfo);
            List<SubRange> ranges = varRangeMap.get(thisParamInfo);
            pos = writeMethodLocalLocation(context, range, thisParamInfo, refAddr, ranges, depth, true, buffer, pos);
        }
        for (int i = 0; i < methodEntry.getParamCount(); i++) {
            DebugLocalInfo paramInfo = methodEntry.getParam(i);
            int refAddr = getMethodLocalIndex(methodEntry, paramInfo);
            List<SubRange> ranges = varRangeMap.get(paramInfo);
            pos = writeMethodLocalLocation(context, range, paramInfo, refAddr, ranges, depth, true, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocations(DebugContext context, HashMap<DebugLocalInfo, List<SubRange>> varRangeMap, Range range, int depth, byte[] buffer, int p) {
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
            int refAddr = getMethodLocalIndex(methodEntry, localInfo);
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
        int abbrevCode;
        if (localValues.isEmpty()) {
            abbrevCode = (isParam ? DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_location1 : DwarfDebugInfo.DW_ABBREV_CODE_method_local_location1);
        } else {
            abbrevCode = (isParam ? DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_location2 : DwarfDebugInfo.DW_ABBREV_CODE_method_local_location2);
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, depth, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // n.b. specification offset gets written as a Ref4, relative to CU start
        log(context, "  [0x%08x]     specification  0x%x", pos, refAddr);
        pos = writeAttrRef4(refAddr, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_local_location2 ||
                        abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_location2) {
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
            pos = writeInlineSubroutine(context, subrange, depth + 2, buffer, pos);
            HashMap<DebugLocalInfo, List<SubRange>> varRangeMap = subrange.getVarRangeMap();
            // increment depth to account for parameter and method locations
            depth++;
            pos = writeMethodParameterLocations(context, varRangeMap, subrange, depth + 2, buffer, pos);
            pos = writeMethodLocalLocations(context, varRangeMap, subrange, depth + 2, buffer, pos);
        }
        // if we just stepped out of a child range write nulls for each step up
        while (depth > 0) {
            pos = writeAttrNull(buffer, pos);
            depth--;
        }
        return pos;
    }

    private int writeInlineSubroutine(DebugContext context, Range caller, int depth, byte[] buffer, int p) {
        assert !caller.isLeaf();
        // the supplied range covers an inline call and references the caller method entry. its
        // child ranges all reference the same inlined called method. leaf children cover code for
        // that inlined method. non-leaf children cover code for recursively inlined methods.
        // identify the inlined method by looking at the first callee
        Range callee = caller.getFirstCallee();
        MethodEntry methodEntry = callee.getMethodEntry();
        String methodKey = methodEntry.getSymbolName();
        /* the abstract index was written in the method's class entry */
        int abstractOriginIndex = getMethodDeclarationIndex(methodEntry);

        int pos = p;
        log(context, "  [0x%08x] concrete inline subroutine [0x%x, 0x%x] %s", pos, caller.getLo(), caller.getHi(), methodKey);

        int callLine = caller.getLine();
        assert callLine >= -1 : callLine;
        int fileIndex;
        if (callLine == -1) {
            log(context, "  Unable to retrieve call line for inlined method %s", callee.getFullMethodName());
            /* continue with line 0 and fileIndex 1 as we must insert a tree node */
            callLine = 0;
            fileIndex = 1;
        } else {
            FileEntry subFileEntry = caller.getFileEntry();
            if (subFileEntry != null) {
                fileIndex = subFileEntry.getIdx();
            } else {
                log(context, "  Unable to retrieve caller FileEntry for inlined method %s (caller method %s)", callee.getFullMethodName(), caller.getFullMethodName());
                fileIndex = 1;
            }
        }
        final int code;
        code = DwarfDebugInfo.DW_ABBREV_CODE_inlined_subroutine_with_children;
        log(context, "  [0x%08x] <%d> Abbrev Number  %d", pos, depth, code);
        pos = writeAbbrevCode(code, buffer, pos);
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

    private int writeAttrRef4(int reference, byte[] buffer, int p) {
        // writes a CU-relative offset but we only have one CU which starts at offset 0
        return writeAttrData4(reference, buffer, p);
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        /* CU length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeShort(DwarfDebugInfo.DW_VERSION_4, buffer, pos);
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
                    /* We need one or two shifts to remove the bits. */
                    if (oopTagsShift != 0) {
                        rightShift = oopTagsShift;
                        exprSize += 2;
                    }
                    leftShift = oopCompressShift;
                    exprSize += 2;
                }
            } else {
                /* No flags to deal with, so we need either an uncompress or nothing. */
                if (oopCompressShift != 0) {
                    leftShift = oopCompressShift;
                    exprSize += 2;
                }
            }
        }
        /* Write size followed by the expression and check the size comes out correct. */
        pos = writeULEB(exprSize, buffer, pos);
        int exprStart = pos;
        if (!useHeapBase) {
            pos = writeByte(DwarfDebugInfo.DW_OP_push_object_address, buffer, pos);
            pos = writeByte((byte) (DwarfDebugInfo.DW_OP_lit0 + mask), buffer, pos);
            pos = writeByte(DwarfDebugInfo.DW_OP_not, buffer, pos);
            pos = writeByte(DwarfDebugInfo.DW_OP_and, buffer, pos);
        } else {
            pos = writeByte(DwarfDebugInfo.DW_OP_push_object_address, buffer, pos);
            /* skip to end if oop is null */
            pos = writeByte(DwarfDebugInfo.DW_OP_dup, buffer, pos);
            pos = writeByte(DwarfDebugInfo.DW_OP_lit0, buffer, pos);
            pos = writeByte(DwarfDebugInfo.DW_OP_eq, buffer, pos);
            int skipStart = pos + 3; /* offset excludes BR op + 2 operand bytes */
            short offsetToEnd = (short) (exprSize - (skipStart - exprStart));
            pos = writeByte(DwarfDebugInfo.DW_OP_bra, buffer, pos);
            pos = writeShort(offsetToEnd, buffer, pos);
            /* insert mask or shifts as necessary */
            if (mask != 0) {
                pos = writeByte((byte) (DwarfDebugInfo.DW_OP_lit0 + mask), buffer, pos);
                pos = writeByte(DwarfDebugInfo.DW_OP_not, buffer, pos);
                pos = writeByte(DwarfDebugInfo.DW_OP_and, buffer, pos);
            } else {
                if (rightShift != 0) {
                    pos = writeByte((byte) (DwarfDebugInfo.DW_OP_lit0 + rightShift), buffer, pos);
                    pos = writeByte(DwarfDebugInfo.DW_OP_shr, buffer, pos);
                }
                if (leftShift != 0) {
                    pos = writeByte((byte) (DwarfDebugInfo.DW_OP_lit0 + leftShift), buffer, pos);
                    pos = writeByte(DwarfDebugInfo.DW_OP_shl, buffer, pos);
                }
            }
            /* add the resulting offset to the heapbase register */
            byte regOp = (byte) (DwarfDebugInfo.DW_OP_breg0 + dwarfSections.getHeapbaseRegister());
            pos = writeByte(regOp, buffer, pos);
            pos = writeSLEB(0, buffer, pos); /* 1 byte. */
            pos = writeByte(DwarfDebugInfo.DW_OP_plus, buffer, pos);
            assert pos == skipStart + offsetToEnd;

            /* make sure we added up correctly */
            assert pos == exprStart + exprSize;
        }
        return pos;
    }

    /**
     * The debug_info section depends on loc section.
     */
    protected static final String TARGET_SECTION_NAME = DwarfDebugInfo.DW_LOC_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
