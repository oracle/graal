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

package com.oracle.objectfile.elf.dwarf;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.EnumClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.AbbrevCode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfAccess;
import com.oracle.objectfile.elf.dwarf.constants.DwarfEncoding;
import com.oracle.objectfile.elf.dwarf.constants.DwarfExpressionOpcode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfFlag;
import com.oracle.objectfile.elf.dwarf.constants.DwarfInline;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLanguage;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfUnitHeader;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;

import jdk.graal.compiler.debug.DebugContext;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * An info header section always contains a fixed number of bytes.
     */
    private static final int CU_DIE_HEADER_SIZE = 12;
    private static final int TU_DIE_HEADER_SIZE = 24;

    private int unitStart;

    public DwarfInfoSectionImpl(DwarfDebugInfo dwarfSections) {
        // debug_info section depends on loc section
        super(dwarfSections, DwarfSectionName.DW_INFO_SECTION, DwarfSectionName.DW_LOCLISTS_SECTION);
        // initialize CU start to an invalid value
        unitStart = -1;
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        int len = generateContent(null, null);

        byte[] buffer = new byte[len];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context);
        log(context, "  [0x%08x] DEBUG_INFO", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);

        pos = generateContent(context, buffer);
        assert pos == size;
    }

    DwarfEncoding computeEncoding(PrimitiveTypeEntry type) {
        int bitCount = type.getBitCount();
        assert bitCount > 0;
        if (type.isNumericInteger()) {
            if (type.isUnsigned()) {
                if (bitCount == 1) {
                    return DwarfEncoding.DW_ATE_boolean;
                } else if (bitCount == 8) {
                    return DwarfEncoding.DW_ATE_unsigned_char;
                } else {
                    assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                    return DwarfEncoding.DW_ATE_unsigned;
                }
            } else if (bitCount == 8) {
                return DwarfEncoding.DW_ATE_signed_char;
            } else {
                assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                return DwarfEncoding.DW_ATE_signed;
            }
        } else {
            assert type.isNumericFloat();
            assert bitCount == 32 || bitCount == 64;
            return DwarfEncoding.DW_ATE_float;
        }
    }

    public int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        /*
         * Write TUs for primitive types and pointer to types. Required for AOT and run-time debug
         * info.
         */
        pos = writePrimitives(context, buffer, pos);
        pos = writePointerToTypes(context, buffer, pos);

        /*
         * Write CUs for all instance classes, which includes interfaces and enums. Additionally,
         * for AOT debug info this also writes TUs.
         */
        pos = writeInstanceClasses(context, buffer, pos);

        if (dwarfSections.isRuntimeCompilation()) {
            /*
             * All structured types are represented as opaque types. I.e. they refer to types
             * produced for the AOT debug info. The referred type must be in the AOT debug info and
             * GDB is able to resolve it by name.
             */
            pos = writeOpaqueTypes(context, buffer, pos);
        } else {
            /*
             * This is the AOT debug info, we write all gathered information into the debug info
             * object file. Most of this information is only produced at image build time.
             */

            /*
             * Write the header struct representing the object header o a Java object in the native
             * image.
             */
            pos = writeHeaderType(context, buffer, pos);

            /*
             * Write TUs for foreign structure types. No CUs, functions of foreign types are handled
             * as special instance class.
             */
            pos = writeForeignStructTypes(context, buffer, pos);
            /* Write TUs and CUs for array types. */
            pos = writeArrays(context, buffer, pos);
            /*
             * Write CU for class constant objects. This also contains class constant objects for
             * foreign types.
             */
            pos = writeClassConstantObjects(context, buffer, pos);
        }
        return pos;
    }

    private int writeSkeletonClassLayout(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] class layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.CLASS_LAYOUT_CU;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(classEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        long typeSignature = classEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type specification 0x%x", pos, typeSignature);
        pos = writeTypeSignature(typeSignature, buffer, pos);
        int fileIdx = classEntry.getFileIdx();
        log(context, "  [0x%08x]     file  0x%x (%s)", pos, fileIdx, classEntry.getFileName());
        pos = writeAttrData2((short) fileIdx, buffer, pos);

        pos = writeStaticFieldDeclarations(context, classEntry, buffer, pos);
        pos = writeMethodDeclarations(context, classEntry, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writePrimitives(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] primitive types", p);

        int pos = p;
        // write primitives
        // i.e. all Java primitives and foreign primitive types.
        for (PrimitiveTypeEntry primitiveTypeEntry : getPrimitiveTypes()) {
            if (primitiveTypeEntry.getBitCount() > 0) {
                pos = writePrimitiveType(context, primitiveTypeEntry, buffer, pos);
            } else {
                pos = writeVoidType(context, primitiveTypeEntry, buffer, pos);
            }
        }

        return pos;
    }

    private int writePointerToTypes(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] pointer to types", p);

        int pos = p;
        // write foreign pointer types
        for (PointerToTypeEntry pointerTypeEntry : getPointerTypes()) {
            pos = writePointerToType(context, pointerTypeEntry, buffer, pos);
        }

        return pos;
    }

    private int writeForeignStructTypes(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] foreign struct types", p);

        int pos = p;
        // write foreign pointer types
        for (ForeignStructTypeEntry foreignStructTypeEntry : getForeignStructTypes()) {
            pos = writeTypeUnits(context, foreignStructTypeEntry, buffer, pos);
        }

        return pos;
    }

    private int writeClassConstantObjects(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        // Write the single Java builtin unit header
        int lengthPos = pos;
        log(context, "  [0x%08x] <0> Class constants Compile Unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + CU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.CLASS_CONSTANT_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString("JAVA");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String compilationDirectory = uniqueDebugString(dwarfSections.getCachePath());
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);

        /* Write the location for the special Class object pseudo-static field for all types */
        for (TypeEntry typeEntry : getTypes()) {
            pos = writeClassConstantDeclaration(context, typeEntry, buffer, pos);
        }

        /*
         * Write a terminating null attribute.
         */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writePrimitiveType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() > 0;
        int pos = p;

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUPreamble(context, primitiveTypeEntry.getTypeSignature(), "", buffer, p);

        log(context, "  [0x%08x] primitive type %s", pos, primitiveTypeEntry.getTypeName());
        AbbrevCode abbrevCode = AbbrevCode.PRIMITIVE_TYPE;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        byte byteSize = (byte) primitiveTypeEntry.getSize();
        log(context, "  [0x%08x]     byte_size  %d", pos, byteSize);
        pos = writeAttrData1(byteSize, buffer, pos);
        byte bitCount = (byte) primitiveTypeEntry.getBitCount();
        log(context, "  [0x%08x]     bitCount  %d", pos, bitCount);
        pos = writeAttrData1(bitCount, buffer, pos);
        DwarfEncoding encoding = computeEncoding(primitiveTypeEntry);
        log(context, "  [0x%08x]     encoding  0x%x", pos, encoding.value());
        pos = writeAttrEncoding(encoding, buffer, pos);
        String name = uniqueDebugString(primitiveTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);

        /* Write a terminating null attribute. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the type offset. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeVoidType(DebugContext context, PrimitiveTypeEntry primitiveTypeEntry, byte[] buffer, int p) {
        assert primitiveTypeEntry.getBitCount() == 0;
        int pos = p;

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUPreamble(context, primitiveTypeEntry.getTypeSignature(), "", buffer, p);

        log(context, "  [0x%08x] primitive type void", pos);
        AbbrevCode abbrevCode = AbbrevCode.VOID_TYPE;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(primitiveTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);

        /* Write a terminating null attribute. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the type offset. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writePointerToType(DebugContext context, PointerToTypeEntry pointerTypeEntry, byte[] buffer, int p) {
        int pos = p;
        long typeSignature = pointerTypeEntry.getTypeSignature();

        // Unlike with Java we use the Java name for the pointer type rather than the
        // underlying base type, or rather for a typedef that targets the pointer type.
        // That ensures that e.g. CCharPointer is a typedef for char*.

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == lengthPos + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* Define a pointer type referring to the base type */
        int refTypeIdx = pos;
        log(context, "  [0x%08x] foreign type wrapper", pos);
        abbrevCode = AbbrevCode.TYPE_POINTER_SIG;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        long layoutTypeSignature = pointerTypeEntry.getPointerTo().getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x", pos, layoutTypeSignature);
        pos = writeTypeSignature(layoutTypeSignature, buffer, pos);

        /* Fix up the type offset. */
        writeInt(pos - lengthPos, buffer, typeOffsetPos);

        /* Define a typedef for the layout type using the Java name. */
        log(context, "  [0x%08x] foreign typedef", pos);
        abbrevCode = AbbrevCode.FOREIGN_TYPEDEF;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(pointerTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name %s", pos, name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
        pos = writeAttrRef4(refTypeIdx, buffer, pos);

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeHeaderType(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        log(context, "  [0x%08x] header type", pos);
        HeaderTypeEntry headerTypeEntry = headerType();

        long typeSignature = headerTypeEntry.getTypeSignature();
        FieldEntry hubField = headerTypeEntry.getHubField();
        ClassEntry hubType = (ClassEntry) hubField.getValueType();
        assert hubType.equals(dwarfSections.lookupClassClass());

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == lengthPos + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /*
         * Write a wrapper type with a data_location attribute that can act as a target for the hub
         * field in the object header. Reuse compressed layout as it accomplishes the same for
         * compressed types.
         */
        int hubLayoutTypeIdx = pos;
        log(context, "  [0x%08x] hub type layout", pos);
        abbrevCode = AbbrevCode.COMPRESSED_LAYOUT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(DwarfDebugInfo.HUB_TYPE_NAME);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        int hubTypeSize = hubType.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, hubTypeSize);
        pos = writeAttrData2((short) hubTypeSize, buffer, pos);
        /* Write a data location expression to mask and/or rebase hub pointers. */
        log(context, "  [0x%08x]     data_location", pos);
        pos = writeCompressedOopConversionExpression(true, buffer, pos);

        /* Now write the child field. */
        pos = writeSuperReference(context, hubType.getLayoutTypeSignature(), name, buffer, pos);

        /* Write a terminating null attribute for the compressed layout. */
        pos = writeAttrNull(buffer, pos);

        /*
         * Define a pointer type referring to the hub type layout. This is the actual type of the
         * hub field.
         */
        int hubTypeIdx = pos;
        log(context, "  [0x%08x] hub pointer type", pos);
        abbrevCode = AbbrevCode.TYPE_POINTER;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int hubSize = hubField.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, hubSize);
        pos = writeAttrData1((byte) hubSize, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, hubLayoutTypeIdx);
        pos = writeAttrRef4(hubLayoutTypeIdx, buffer, pos);

        /* Fix up the type offset. */
        writeInt(pos - lengthPos, buffer, typeOffsetPos);

        /* Write the type representing the object header. */
        name = uniqueDebugString(headerTypeEntry.getTypeName());
        int headerSize = headerTypeEntry.getSize();
        log(context, "  [0x%08x] header type %s", pos, name);
        abbrevCode = AbbrevCode.OBJECT_HEADER;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, headerSize);
        pos = writeAttrData1((byte) headerSize, buffer, pos);
        pos = writeHubField(context, hubField, hubTypeIdx, buffer, pos);
        pos = writeStructFields(context, headerTypeEntry.getFields(), buffer, pos);

        /* Write a terminating null attribute. */
        pos = writeAttrNull(buffer, pos);

        /* Write a terminating null attribute. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the type offset. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeHubField(DebugContext context, FieldEntry hubFieldEntry, int hubTypeIdx, byte[] buffer, int p) {
        int pos = p;

        AbbrevCode abbrevCode = AbbrevCode.STRUCT_FIELD;
        log(context, "  [0x%08x] hub field", pos);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String fieldName = uniqueDebugString(hubFieldEntry.fieldName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeStrSectionOffset(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, hubTypeIdx, DwarfDebugInfo.HUB_TYPE_NAME);
        pos = writeInfoSectionOffset(hubTypeIdx, buffer, pos);
        short offset = (short) hubFieldEntry.getOffset();
        int size = hubFieldEntry.getSize();
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData2(offset, buffer, pos);
        int modifiers = hubFieldEntry.getModifiers();
        log(context, "  [0x%08x]     modifiers %s", pos, hubFieldEntry.getModifiersString());
        return writeAttrAccessibility(modifiers, buffer, pos);
    }

    private int writeStructFields(DebugContext context, List<FieldEntry> fields, byte[] buffer, int p) {
        int pos = p;
        for (FieldEntry fieldEntry : fields) {
            pos = writeStructField(context, fieldEntry, buffer, pos);
        }
        return pos;
    }

    private int writeStructField(DebugContext context, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        String fieldName = uniqueDebugString(fieldEntry.fieldName());
        TypeEntry valueType = fieldEntry.getValueType();
        long typeSignature = 0;
        int typeIdx = 0;
        AbbrevCode abbrevCode = AbbrevCode.STRUCT_FIELD_SIG;
        if (fieldEntry.isEmbedded()) {
            // the field type must be a foreign type
            /* use the layout type for the field */
            /* handle special case when the field is an array */
            int fieldSize = fieldEntry.getSize();
            int valueSize = valueType.getSize();
            if (fieldSize != valueSize) {
                assert (fieldSize % valueSize == 0) : "embedded field size is not a multiple of value type size!";
                // declare a local array of the embedded type and use it as the value type
                typeIdx = pos;
                abbrevCode = AbbrevCode.STRUCT_FIELD;
                pos = writeEmbeddedArrayDataType(context, valueType, valueSize, fieldSize / valueSize, buffer, pos);
            } else {
                if (valueType instanceof PointerToTypeEntry pointerTypeEntry) {
                    TypeEntry pointerTo = pointerTypeEntry.getPointerTo();
                    assert pointerTo != null : "ADDRESS field pointer type must have a known target type";
                    // type the array using the referent of the pointer type
                    //
                    // n.b it is critical for correctness to use the index of the referent rather
                    // than the layout type of the referring type even though the latter will
                    // (eventually) be set to the same value. the type index of the referent is
                    // guaranteed to be set on the first sizing pass before it is consumed here
                    // on the second writing pass.
                    // However, if this embedded struct field definition precedes the definition
                    // of the referring type and the latter precedes the definition of the
                    // referent type then the layout index of the referring type may still be unset
                    // at this point.
                    typeSignature = pointerTo.getTypeSignature();
                } else if (valueType instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
                    typeSignature = foreignStructTypeEntry.getLayoutTypeSignature();
                } else {
                    typeSignature = valueType.getTypeSignature();
                }
            }
        } else {
            /* use the compressed type for the field so compressed pointers get translated */
            typeSignature = valueType.getTypeSignatureForCompressed();
        }
        log(context, "  [0x%08x] struct field", pos);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeStrSectionOffset(fieldName, buffer, pos);
        if (abbrevCode == AbbrevCode.STRUCT_FIELD_SIG) {
            log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeSignature, valueType.getTypeName());
            pos = writeTypeSignature(typeSignature, buffer, pos);
        } else {
            log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeIdx, valueType.getTypeName());
            pos = writeInfoSectionOffset(typeIdx, buffer, pos);
        }
        short offset = (short) fieldEntry.getOffset();
        int size = fieldEntry.getSize();
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData2(offset, buffer, pos);
        int modifiers = fieldEntry.getModifiers();
        log(context, "  [0x%08x]     modifiers %s", pos, fieldEntry.getModifiersString());
        return writeAttrAccessibility(modifiers, buffer, pos);
    }

    private int writeInstanceClasses(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] instance classes", p);
        int pos = p;
        for (ClassEntry classEntry : getInstanceClasses()) {
            /*
             * For run-time debug info, we create a type unit with the opaque type, so no need to
             * create a full type unit here. The foreign method list is no actual instance class,
             * but just needs a compilation unit to reference the compilation.
             */
            if (!dwarfSections.isRuntimeCompilation() && classEntry != dwarfSections.getForeignMethodListClassEntry()) {
                pos = writeTypeUnits(context, classEntry, buffer, pos);
            }
            /*
             * We only need to write a CU for a class entry if compilations or static fields are
             * available for this class. This includes inlined compilations as they refer to the
             * declaration in the owner type CU. Other information is already written to the
             * corresponding type units.
             */
            if (classEntry.getMethods().stream().anyMatch(m -> m.isInRange() || m.isInlined()) ||
                            classEntry.getFields().stream().anyMatch(DwarfInfoSectionImpl::isManifestedStaticField)) {
                setCUIndex(classEntry, pos);
                pos = writeInstanceClassInfo(context, classEntry, buffer, pos);
            }
        }
        return pos;
    }

    private int writeTypeUnits(DebugContext context, StructureTypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;

        if (typeEntry instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
            pos = writeForeignStructLayoutTypeUnit(context, foreignStructTypeEntry, buffer, pos);
            pos = writeForeignStructTypeUnit(context, foreignStructTypeEntry, buffer, pos);
        } else {
            if (typeEntry instanceof ArrayTypeEntry arrayTypeEntry) {
                pos = writeArrayLayoutTypeUnit(context, arrayTypeEntry, buffer, pos);
            } else if (typeEntry instanceof InterfaceClassEntry interfaceClassEntry) {
                pos = writeInterfaceLayoutTypeUnit(context, interfaceClassEntry, buffer, pos);
            } else {
                assert typeEntry instanceof ClassEntry;
                pos = writeClassLayoutTypeUnit(context, (ClassEntry) typeEntry, buffer, pos);
            }
            pos = writePointerTypeUnit(context, typeEntry, buffer, pos);
            if (dwarfSections.useHeapBase()) {
                pos = writePointerTypeUnitForCompressed(context, typeEntry, buffer, pos);
            }
        }
        return pos;
    }

    private int writeTUPreamble(DebugContext context, long typeSignature, String loaderId, byte[] buffer, int p) {
        int pos = p;

        // Write a type unit header
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == p + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* if the class has a loader then embed the children in a namespace */
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /* Fix up the type offset. */
        writeInt(pos - p, buffer, typeOffsetPos);
        return pos;
    }

    private int writePointerTypeUnit(DebugContext context, StructureTypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;

        String loaderId = "";
        if (typeEntry instanceof ArrayTypeEntry arrayTypeEntry) {
            loaderId = arrayTypeEntry.getLoaderId();
        } else if (typeEntry instanceof ClassEntry classEntry) {
            loaderId = classEntry.getLoaderId();
        }
        int lengthPos = pos;
        long typeSignature = typeEntry.getTypeSignature();
        pos = writeTUPreamble(context, typeSignature, loaderId, buffer, p);

        /* Define a pointer type referring to the underlying layout. */
        log(context, "  [0x%08x] %s pointer type", pos, typeEntry instanceof InterfaceClassEntry ? "interface" : "class");
        AbbrevCode abbrevCode = AbbrevCode.TYPE_POINTER_SIG;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        long layoutTypeSignature = typeEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type 0x%x", pos, layoutTypeSignature);
        pos = writeTypeSignature(layoutTypeSignature, buffer, pos);

        if (!loaderId.isEmpty()) {
            /* Write a terminating null attribute for the namespace. */
            pos = writeAttrNull(buffer, pos);
        }

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writePointerTypeUnitForCompressed(DebugContext context, StructureTypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;
        long typeSignature = typeEntry.getTypeSignatureForCompressed();

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == lengthPos + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* if the class has a loader then embed the children in a namespace */
        String loaderId = "";
        if (typeEntry instanceof ArrayTypeEntry arrayTypeEntry) {
            loaderId = arrayTypeEntry.getLoaderId();
        } else if (typeEntry instanceof ClassEntry classEntry) {
            loaderId = classEntry.getLoaderId();
        }
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /*
         * Define a wrapper type with a data_location attribute that can act as a target for
         * compressed oops
         */
        int refTypeIdx = pos;
        pos = writeLayoutTypeForCompressed(context, typeEntry, buffer, pos);

        /* Fix up the type offset. */
        writeInt(pos - lengthPos, buffer, typeOffsetPos);

        /* Define a pointer type referring to the underlying layout. */
        log(context, "  [0x%08x] %s compressed pointer type", pos, typeEntry instanceof InterfaceClassEntry ? "interface" : "class");
        abbrevCode = AbbrevCode.TYPE_POINTER;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.referenceSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
        pos = writeAttrRef4(refTypeIdx, buffer, pos);

        if (!loaderId.isEmpty()) {
            /* Write a terminating null attribute for the namespace. */
            pos = writeAttrNull(buffer, pos);
        }

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeLayoutTypeForCompressed(DebugContext context, StructureTypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;
        /*
         * Write a wrapper type with a data_location attribute that can act as a target for
         * compressed oops.
         */
        log(context, "  [0x%08x] compressed %s layout", pos, typeEntry instanceof InterfaceClassEntry ? "interface" : "class");
        AbbrevCode abbrevCode = AbbrevCode.COMPRESSED_LAYOUT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String compressedName = uniqueDebugString(DwarfDebugInfo.COMPRESSED_PREFIX + typeEntry.getTypeName());
        String name = uniqueDebugString(typeEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(compressedName), name);
        pos = writeStrSectionOffset(compressedName, buffer, pos);
        int size = typeEntry.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);
        /* Write a data location expression to mask and/or rebase oop pointers. */
        log(context, "  [0x%08x]     data_location", pos);
        pos = writeCompressedOopConversionExpression(false, buffer, pos);

        /* Now write the child field. */
        pos = writeSuperReference(context, typeEntry.getLayoutTypeSignature(), name, buffer, pos);

        /* Write a terminating null attribute for the compressed layout. */
        return writeAttrNull(buffer, pos);
    }

    private int writeInterfaceLayoutTypeUnit(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        int pos = p;

        String loaderId = interfaceClassEntry.getLoaderId();
        int lengthPos = pos;
        pos = writeTUPreamble(context, interfaceClassEntry.getLayoutTypeSignature(), loaderId, buffer, pos);

        log(context, "  [0x%08x] interface layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.INTERFACE_LAYOUT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(interfaceClassEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);

        /* Now write references to all class layouts that implement this interface. */
        pos = writeInterfaceImplementors(context, interfaceClassEntry, buffer, pos);
        pos = writeSkeletonMethodDeclarations(context, interfaceClassEntry, buffer, pos);

        /* Write a terminating null attribute for the interface layout. */
        pos = writeAttrNull(buffer, pos);

        if (!loaderId.isEmpty()) {
            /* Write a terminating null attribute for the namespace. */
            pos = writeAttrNull(buffer, pos);
        }

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeClassLayoutTypeUnit(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;

        String loaderId = classEntry.getLoaderId();
        int lengthPos = pos;
        pos = writeTUPreamble(context, classEntry.getLayoutTypeSignature(), loaderId, buffer, pos);

        int refTypeIdx = pos;
        log(context, "  [0x%08x] type layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.CLASS_LAYOUT_TU;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(classEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        int size = classEntry.getSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData2((short) size, buffer, pos);

        StructureTypeEntry superClassEntry = classEntry.getSuperClass();
        if (superClassEntry == null) {
            superClassEntry = headerType();
        }

        /* Now write the child fields. */
        pos = writeSuperReference(context, superClassEntry.getLayoutTypeSignature(), superClassEntry.getTypeName(), buffer, pos);
        pos = writeFields(context, classEntry, buffer, pos);
        pos = writeSkeletonMethodDeclarations(context, classEntry, buffer, pos);

        /* Write a terminating null attribute for the class layout. */
        pos = writeAttrNull(buffer, pos);

        if (!loaderId.isEmpty()) {
            /* Write a terminating null attribute for the namespace. */
            pos = writeAttrNull(buffer, pos);
        }

        if (classEntry instanceof EnumClassEntry enumClassEntry && !enumClassEntry.getTypedefName().isEmpty()) {
            /* Define a typedef c enum type. */
            log(context, "  [0x%08x] c enum typedef", pos);
            abbrevCode = AbbrevCode.FOREIGN_TYPEDEF;
            log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
            pos = writeAbbrevCode(abbrevCode, buffer, pos);
            name = uniqueDebugString(enumClassEntry.getTypedefName());
            log(context, "  [0x%08x]     name %s", pos, name);
            pos = writeStrSectionOffset(name, buffer, pos);
            log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
            pos = writeAttrRef4(refTypeIdx, buffer, pos);
        }

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeForeignStructTypeUnit(DebugContext context, ForeignStructTypeEntry foreignStructTypeEntry, byte[] buffer, int p) {
        int pos = p;
        long typeSignature = foreignStructTypeEntry.getTypeSignature();

        // Unlike with Java we use the Java name for the pointer type rather than the
        // underlying base type, or rather for a typedef that targets the pointer type.
        // That ensures that e.g. CCharPointer is a typedef for char*.

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == lengthPos + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* Define a pointer type referring to the base type */
        int refTypeIdx = pos;
        log(context, "  [0x%08x] foreign type wrapper", pos);
        abbrevCode = AbbrevCode.TYPE_POINTER_SIG;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        long layoutTypeSignature = foreignStructTypeEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type 0x%x", pos, layoutTypeSignature);
        pos = writeTypeSignature(layoutTypeSignature, buffer, pos);

        /* Fix up the type offset. */
        writeInt(pos - lengthPos, buffer, typeOffsetPos);

        /* Define a typedef for the layout type using the Java name. */
        log(context, "  [0x%08x] foreign typedef", pos);
        abbrevCode = AbbrevCode.FOREIGN_TYPEDEF;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(foreignStructTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name %s", pos, name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
        pos = writeAttrRef4(refTypeIdx, buffer, pos);

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeOpaqueTypes(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] opaque types", p);

        int pos = p;
        /*
         * Write all structured types as opaque types (except Foreign method list). With this
         * representation, types are resolved by name in gdb (at run-time we know the types must
         * already exist in the AOT debug info).
         */
        for (TypeEntry t : getTypes()) {
            if (t instanceof StructureTypeEntry && t != dwarfSections.getForeignMethodListClassEntry()) {
                pos = writeOpaqueType(context, t, buffer, pos);
            }
        }

        return pos;
    }

    private int writeOpaqueType(DebugContext context, TypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;
        long typeSignature = typeEntry.getTypeSignature();

        // Write a type unit header
        int lengthPos = pos;
        pos = writeTUHeader(typeSignature, buffer, pos);
        int typeOffsetPos = pos - 4;
        assert pos == lengthPos + TU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.TYPE_UNIT;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        int refTypeIdx = pos;
        log(context, "  [0x%08x] class layout", pos);
        abbrevCode = AbbrevCode.CLASS_LAYOUT_OPAQUE;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name;
        if (typeEntry instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
            name = uniqueDebugString(foreignStructTypeEntry.getTypedefName());
        } else {
            name = uniqueDebugString(typeEntry.getTypeName());
        }
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* Fix up the type offset. */
        writeInt(pos - lengthPos, buffer, typeOffsetPos);

        /* Define a pointer type referring to the underlying layout. */
        log(context, "  [0x%08x] %s dummy pointer type", pos, typeEntry instanceof InterfaceClassEntry ? "interface" : "class");
        abbrevCode = AbbrevCode.TYPE_POINTER;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        int pointerSize = dwarfSections.pointerSize();
        log(context, "  [0x%08x]     byte_size 0x%x", pos, pointerSize);
        pos = writeAttrData1((byte) pointerSize, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x", pos, refTypeIdx);
        pos = writeAttrRef4(refTypeIdx, buffer, pos);

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeForeignStructLayoutTypeUnit(DebugContext context, ForeignStructTypeEntry foreignStructTypeEntry, byte[] buffer, int p) {
        int pos = p;
        int lengthPos = pos;

        pos = writeTUPreamble(context, foreignStructTypeEntry.getLayoutTypeSignature(), "", buffer, pos);

        int size = foreignStructTypeEntry.getSize();
        // define this type using a structure layout
        log(context, "  [0x%08x] foreign struct type for %s", pos, foreignStructTypeEntry.getTypeName());
        AbbrevCode abbrevCode = AbbrevCode.FOREIGN_STRUCT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String typedefName = uniqueDebugString(foreignStructTypeEntry.getTypedefName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(typedefName), typedefName);
        pos = writeStrSectionOffset(typedefName, buffer, pos);
        log(context, "  [0x%08x]     byte_size  0x%x", pos, size);
        pos = writeAttrData1((byte) size, buffer, pos);
        // if we have a parent write a super attribute
        ForeignStructTypeEntry parent = foreignStructTypeEntry.getParent();
        if (parent != null) {
            long typeSignature = parent.getLayoutTypeSignature();
            pos = writeSuperReference(context, typeSignature, parent.getTypedefName(), buffer, pos);
        }

        pos = writeStructFields(context, foreignStructTypeEntry.getFields(), buffer, pos);

        /*
         * Write a terminating null attribute for the structure type.
         */
        pos = writeAttrNull(buffer, pos);

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the TU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeInstanceClassInfo(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        // Write a Java class unit header
        int lengthPos = pos;
        log(context, "  [0x%08x] Instance class unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + CU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode;
        if (classEntry.hasCompiledMethods()) {
            if (getLocationListIndex(classEntry) == 0) {
                abbrevCode = AbbrevCode.CLASS_UNIT_2;
            } else {
                abbrevCode = AbbrevCode.CLASS_UNIT_3;
            }
        } else {
            abbrevCode = AbbrevCode.CLASS_UNIT_1;
        }
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = classEntry.getFullFileName();
        if (name == null) {
            name = classEntry.getTypeName().replace('.', '/') + ".java";
        }
        name = uniqueDebugString(name);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String compilationDirectory = uniqueDebugString(dwarfSections.getCachePath());
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);
        if (abbrevCode == AbbrevCode.CLASS_UNIT_2 || abbrevCode == AbbrevCode.CLASS_UNIT_3) {
            int codeRangesIndex = getCodeRangesIndex(classEntry);
            log(context, "  [0x%08x]     ranges  0x%x", pos, codeRangesIndex);
            pos = writeRangeListsSectionOffset(codeRangesIndex, buffer, pos);
            // write low_pc as well as ranges so that location lists can default the base address
            long lo = classEntry.lowpc();
            log(context, "  [0x%08x]     low_pc  0x%x", pos, codeRangesIndex);
            pos = writeAttrAddress(lo, buffer, pos);
            int lineIndex = getLineIndex(classEntry);
            log(context, "  [0x%08x]     stmt_list  0x%x", pos, lineIndex);
            pos = writeLineSectionOffset(lineIndex, buffer, pos);
            if (abbrevCode == AbbrevCode.CLASS_UNIT_3) {
                int locationListIndex = getLocationListIndex(classEntry);
                log(context, "  [0x%08x]     loclists_base  0x%x", pos, locationListIndex);
                pos = writeLocSectionOffset(locationListIndex, buffer, pos);
            }
        }
        /* if the class has a loader then embed the children in a namespace */
        String loaderId = classEntry.getLoaderId();
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /* Now write the child DIEs starting with the layout and pointer type. */

        if (dwarfSections.getForeignMethodListClassEntry() != classEntry) {
            // This works for any structured type entry. Entry kind specifics are in the
            // type units.
            pos = writeSkeletonClassLayout(context, classEntry, buffer, pos);
        } else {
            // The foreign class list does not have a corresponding type unit, so we have to add
            // full declarations here.
            pos = writeMethodDeclarations(context, classEntry, buffer, pos);
        }

        /* Write all compiled code locations */
        pos = writeMethodLocations(context, classEntry, buffer, pos);

        /* Write abstract inline methods. */
        pos = writeAbstractInlineMethods(context, classEntry, buffer, pos);

        /* Write all static field definitions */
        pos = writeStaticFieldLocations(context, classEntry, buffer, pos);

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

    private int writeClassConstantDeclaration(DebugContext context, TypeEntry typeEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] class constant", pos);
        long offset = typeEntry.getClassOffset();
        if (offset < 0) {
            return pos;
        }
        // Write a special static field declaration for the class object
        // we use the abbrev code for a static field with no file or line location
        AbbrevCode abbrevCode = AbbrevCode.CLASS_CONSTANT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = uniqueDebugString(typeEntry.getTypeName() + ".class");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        /*
         * This is a direct reference to the object rather than a compressed oop reference. So, we
         * need to use the direct layout type.
         */
        long typeSignature = dwarfSections.lookupClassClass().getLayoutTypeSignature();
        log(context, "  [0x%08x]     type  0x%x (<hub type>)", pos, typeSignature);
        pos = writeTypeSignature(typeSignature, buffer, pos);
        log(context, "  [0x%08x]     accessibility public static final", pos);
        pos = writeAttrAccessibility(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, buffer, pos);
        log(context, "  [0x%08x]     external(true)", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        /*
         * Encode this location as a relative relocatable address or an offset from the heapbase
         * register.
         */
        log(context, "  [0x%08x]     location  heapbase + 0x%x (class constant)", pos, offset);
        pos = writeHeapLocationExprLoc(offset, buffer, pos);
        return pos;
    }

    private int writeSuperReference(DebugContext context, long typeSignature, String superName, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] super reference", pos);
        AbbrevCode abbrevCode = AbbrevCode.SUPER_REFERENCE;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeSignature, superName);
        pos = writeTypeSignature(typeSignature, buffer, pos);
        /* Parent layout is embedded at start of object. */
        log(context, "  [0x%08x]     data_member_location (super) 0x%x", pos, 0);
        pos = writeAttrData1((byte) 0, buffer, pos);
        log(context, "  [0x%08x]     modifiers public", pos);
        int modifiers = Modifier.PUBLIC;
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeFields(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        return classEntry.getFields().stream().filter(DwarfInfoSectionImpl::isManifestedField).reduce(p,
                        (pos, fieldEntry) -> writeField(context, classEntry, fieldEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private static boolean isManifestedField(FieldEntry fieldEntry) {
        return fieldEntry.getOffset() >= 0;
    }

    private int writeField(DebugContext context, StructureTypeEntry entry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        int modifiers = fieldEntry.getModifiers();
        boolean hasFile = !fieldEntry.getFileName().isEmpty();
        log(context, "  [0x%08x] field definition", pos);
        AbbrevCode abbrevCode;
        boolean isStatic = Modifier.isStatic(modifiers);
        if (!isStatic) {
            if (!hasFile) {
                abbrevCode = AbbrevCode.FIELD_DECLARATION_1;
            } else {
                abbrevCode = AbbrevCode.FIELD_DECLARATION_2;
            }
        } else {
            if (!hasFile) {
                abbrevCode = AbbrevCode.FIELD_DECLARATION_3;
            } else {
                abbrevCode = AbbrevCode.FIELD_DECLARATION_4;
            }
        }
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = uniqueDebugString(fieldEntry.fieldName());
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
        /* use the compressed type for the field so pointers get translated if needed */
        long typeSignature = valueType.getTypeSignatureForCompressed();
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeSignature, valueType.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
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
            pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
            log(context, "  [0x%08x]     definition(true)", pos);
            pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        }
        return pos;
    }

    private int writeSkeletonMethodDeclarations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        for (MethodEntry method : classEntry.getMethods()) {
            if (method.isInRange() || method.isInlined()) {
                /*
                 * Declare all methods whether or not they have been compiled or inlined.
                 */
                pos = writeSkeletonMethodDeclaration(context, classEntry, method, buffer, pos);
            }
        }

        return pos;
    }

    private int writeSkeletonMethodDeclaration(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] method declaration %s::%s", pos, classEntry.getTypeName(), method.getMethodName());
        AbbrevCode abbrevCode = AbbrevCode.METHOD_DECLARATION_SKELETON;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString(method.getMethodName());
        log(context, "  [0x%08x]     name 0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String linkageName = uniqueDebugString(method.getSymbolName());
        log(context, "  [0x%08x]     linkage_name %s", pos, linkageName);
        pos = writeStrSectionOffset(linkageName, buffer, pos);
        TypeEntry returnType = method.getValueType();
        long retTypeSignature = returnType.getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, retTypeSignature, returnType.getTypeName());
        pos = writeTypeSignature(retTypeSignature, buffer, pos);
        log(context, "  [0x%08x]     artificial %s", pos, method.isDeopt() ? "true" : "false");
        pos = writeFlag((method.isDeopt() ? DwarfFlag.DW_FLAG_true : DwarfFlag.DW_FLAG_false), buffer, pos);
        int modifiers = method.getModifiers();
        log(context, "  [0x%08x]     accessibility %s", pos, method.getModifiersString());
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);

        /* Write method parameter declarations. */
        pos = writeSkeletonMethodParameterDeclarations(context, method, buffer, pos);

        /* Write a terminating null attribute. */
        return writeAttrNull(buffer, pos);
    }

    private int writeSkeletonMethodParameterDeclarations(DebugContext context, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        for (LocalEntry paramInfo : method.getParams()) {
            pos = writeSkeletonMethodParameterDeclaration(context, paramInfo, method.isThisParam(paramInfo), buffer, pos);
        }
        return pos;
    }

    private int writeSkeletonMethodParameterDeclaration(DebugContext context, LocalEntry paramInfo, boolean artificial, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        AbbrevCode abbrevCode;
        TypeEntry paramType = paramInfo.type();
        if (artificial) {
            abbrevCode = AbbrevCode.METHOD_PARAMETER_DECLARATION_4;
        } else {
            abbrevCode = AbbrevCode.METHOD_PARAMETER_DECLARATION_5;
        }
        log(context, "  [0x%08x] <3> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        long typeSignature = paramType.getTypeSignature();
        log(context, "  [0x%08x]     type 0x%x (%s)", pos, typeSignature, paramType.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_PARAMETER_DECLARATION_4) {
            log(context, "  [0x%08x]     artificial true", pos);
            pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        }
        return pos;
    }

    private int writeMethodDeclarations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        for (MethodEntry method : classEntry.getMethods()) {
            if (method.isInRange() || method.isInlined()) {
                /*
                 * Declare all methods whether they have been compiled or inlined.
                 */
                pos = writeMethodDeclaration(context, classEntry, method, false, buffer, pos);
            }
        }

        return pos;
    }

    private int writeMethodDeclaration(DebugContext context, ClassEntry classEntry, MethodEntry method, boolean isInlined, byte[] buffer, int p) {
        int pos = p;
        String linkageName = uniqueDebugString(method.getSymbolName());
        setMethodDeclarationIndex(method, pos);
        int modifiers = method.getModifiers();
        boolean isStatic = Modifier.isStatic(modifiers);
        log(context, "  [0x%08x] method declaration %s::%s", pos, classEntry.getTypeName(), method.getMethodName());
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
        String name = uniqueDebugString(method.getMethodName());
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
        if (abbrevCode == AbbrevCode.METHOD_DECLARATION | abbrevCode == AbbrevCode.METHOD_DECLARATION_INLINE) {
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
        for (LocalEntry paramInfo : method.getParams()) {
            setMethodLocalIndex(classEntry, method, paramInfo, pos);
            pos = writeMethodParameterDeclaration(context, method, paramInfo, fileIdx, method.isThisParam(paramInfo), level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodParameterDeclaration(DebugContext context, MethodEntry method, LocalEntry paramInfo, int fileIdx, boolean artificial, int level, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method parameter declaration", pos);
        AbbrevCode abbrevCode;
        String paramName = uniqueDebugString(paramInfo.name());
        TypeEntry paramType = paramInfo.type();
        int line = method.getLine();
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
        pos = writeStrSectionOffset(paramName, buffer, pos);
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
        for (MethodEntry.Local local : method.getLocals()) {
            setMethodLocalIndex(classEntry, method, local.localEntry(), pos);
            pos = writeMethodLocalDeclaration(context, local, fileIdx, level, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalDeclaration(DebugContext context, MethodEntry.Local local, int fileIdx, int level, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method local declaration", pos);
        AbbrevCode abbrevCode;
        String paramName = uniqueDebugString(local.localEntry().name());
        TypeEntry paramType = local.localEntry().type();
        int line = local.line();
        if (line >= 0) {
            abbrevCode = AbbrevCode.METHOD_LOCAL_DECLARATION_1;
        } else {
            abbrevCode = AbbrevCode.METHOD_LOCAL_DECLARATION_2;
        }
        log(context, "  [0x%08x] <%d> Abbrev Number %d", pos, level, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     name %s", pos, paramName);
        pos = writeStrSectionOffset(paramName, buffer, pos);
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

    private int writeInterfaceImplementors(DebugContext context, InterfaceClassEntry interfaceClassEntry, byte[] buffer, int p) {
        return interfaceClassEntry.getImplementors().stream().reduce(p,
                        (pos, classEntry) -> writeInterfaceImplementor(context, classEntry, buffer, pos),
                        (oldPos, newPos) -> newPos);
    }

    private int writeInterfaceImplementor(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] interface implementor", pos);
        AbbrevCode abbrevCode = AbbrevCode.INTERFACE_IMPLEMENTOR;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString("_" + classEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        long typeSignature = classEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeSignature, classEntry.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        int modifiers = Modifier.PUBLIC;
        log(context, "  [0x%08x]     modifiers %s", pos, "public");
        pos = writeAttrAccessibility(modifiers, buffer, pos);
        return pos;
    }

    private int writeStaticFieldLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        /*
         * Only write locations for static fields that have an offset greater than 0. A negative
         * offset indicates that the field has been folded into code as an unmaterialized constant.
         */
        int pos = p;
        for (FieldEntry fieldEntry : classEntry.getFields()) {
            if (isManifestedStaticField(fieldEntry)) {
                pos = writeClassStaticFieldLocation(context, classEntry, fieldEntry, buffer, pos);
            }
        }
        return pos;
    }

    private int writeStaticFieldDeclarations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        /*
         * Only write locations for static fields that have an offset greater than 0. A negative
         * offset indicates that the field has been folded into code as an unmaterialized constant.
         */
        int pos = p;
        for (FieldEntry fieldEntry : classEntry.getFields()) {
            if (isManifestedStaticField(fieldEntry)) {
                pos = writeClassStaticFieldDeclaration(context, fieldEntry, buffer, pos);
            }
        }
        return pos;
    }

    private static boolean isManifestedStaticField(FieldEntry fieldEntry) {
        return Modifier.isStatic(fieldEntry.getModifiers()) && fieldEntry.getOffset() >= 0;
    }

    private int writeClassStaticFieldDeclaration(DebugContext context, FieldEntry fieldEntry, byte[] buffer, int p) {
        assert Modifier.isStatic(fieldEntry.getModifiers());

        int pos = p;
        boolean hasFile = !fieldEntry.getFileName().isEmpty();
        log(context, "  [0x%08x] field definition", pos);
        AbbrevCode abbrevCode;
        if (!hasFile) {
            abbrevCode = AbbrevCode.FIELD_DECLARATION_3;
        } else {
            abbrevCode = AbbrevCode.FIELD_DECLARATION_4;
        }
        /* Record the position of the declaration to use when we write the definition. */
        setFieldDeclarationIndex(fieldEntry, pos);
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);

        String name = uniqueDebugString(fieldEntry.fieldName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        /* We may not have a file and line for a field. */
        if (hasFile) {
            int fileIdx = fieldEntry.getFileIdx();
            assert fileIdx > 0;
            log(context, "  [0x%08x]     filename  0x%x (%s)", pos, fileIdx, fieldEntry.getFileName());
            pos = writeAttrData2((short) fileIdx, buffer, pos);
            /* At present we definitely don't have line numbers. */
        }
        TypeEntry valueType = fieldEntry.getValueType();
        /* use the compressed type for the field so pointers get translated if needed */
        long typeSignature = valueType.getTypeSignatureForCompressed();
        log(context, "  [0x%08x]     type  0x%x (%s)", pos, typeSignature, valueType.getTypeName());
        pos = writeTypeSignature(typeSignature, buffer, pos);
        log(context, "  [0x%08x]     accessibility %s", pos, fieldEntry.getModifiersString());
        pos = writeAttrAccessibility(fieldEntry.getModifiers(), buffer, pos);
        /* Static fields are only declared here and are external. */
        log(context, "  [0x%08x]     external(true)", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        log(context, "  [0x%08x]     definition(true)", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        return pos;
    }

    private int writeClassStaticFieldLocation(DebugContext context, ClassEntry classEntry, FieldEntry fieldEntry, byte[] buffer, int p) {
        int pos = p;
        int fieldDefinitionOffset = getFieldDeclarationIndex(fieldEntry);
        log(context, "  [0x%08x] static field location %s.%s", pos, classEntry.getTypeName(), fieldEntry.fieldName());
        AbbrevCode abbrevCode = AbbrevCode.STATIC_FIELD_LOCATION;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // n.b. field definition offset gets written as a Ref4, relative to CU start
        log(context, "  [0x%08x]     specification  0x%x", pos, fieldDefinitionOffset);
        pos = writeAttrRef4(fieldDefinitionOffset, buffer, pos);
        /* Field offset needs to be relocated relative to static primitive or static object base. */
        int offset = fieldEntry.getOffset();
        log(context, "  [0x%08x]     location  heapbase + 0x%x (%s)", pos, offset, (fieldEntry.getValueType() instanceof PrimitiveTypeEntry ? "primitive" : "object"));
        pos = writeHeapLocationExprLoc(offset, buffer, pos);
        return pos;
    }

    private int writeArrays(DebugContext context, byte[] buffer, int p) {
        log(context, "  [0x%08x] array classes", p);
        int pos = p;
        for (ArrayTypeEntry arrayTypeEntry : getArrayTypes()) {
            pos = writeTypeUnits(context, arrayTypeEntry, buffer, pos);
            pos = writeArray(context, arrayTypeEntry, buffer, pos);
        }
        return pos;
    }

    private int writeArrayLayoutTypeUnit(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;

        String loaderId = arrayTypeEntry.getLoaderId();
        int lengthPos = pos;
        pos = writeTUPreamble(context, arrayTypeEntry.getLayoutTypeSignature(), loaderId, buffer, pos);

        /* Write the array layout and array reference DIEs. */
        TypeEntry elementType = arrayTypeEntry.getElementType();
        int size = arrayTypeEntry.getSize();
        log(context, "  [0x%08x] array layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.ARRAY_LAYOUT;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(arrayTypeEntry.getTypeName());
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

        /* All arrays inherit from java.lang.Object */
        ClassEntry objectType = lookupObjectClass();
        String superName = objectType.getTypeName();
        long typeSignature = objectType.getLayoutTypeSignature();
        pos = writeSuperReference(context, typeSignature, superName, buffer, pos);

        /* Write a terminating null attribute for the array layout. */
        pos = writeAttrNull(buffer, pos);

        /* if we opened a namespace then terminate its children */
        if (!loaderId.isEmpty()) {
            pos = writeAttrNull(buffer, pos);
        }

        /* Write a terminating null attribute for the top level TU DIE. */
        pos = writeAttrNull(buffer, pos);

        /* Fix up the CU length. */
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeArray(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        // Write a Java class unit header
        int lengthPos = pos;
        log(context, "  [0x%08x] Array class unit", pos);
        pos = writeCUHeader(buffer, pos);
        assert pos == lengthPos + CU_DIE_HEADER_SIZE;
        AbbrevCode abbrevCode = AbbrevCode.CLASS_UNIT_1;
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrLanguage(DwarfDebugInfo.LANG_ENCODING, buffer, pos);
        log(context, "  [0x%08x]     use_UTF8", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String name = uniqueDebugString("JAVA");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        String compilationDirectory = uniqueDebugString(dwarfSections.getCachePath());
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeStrSectionOffset(compilationDirectory, buffer, pos);

        /* if the array base type is a class with a loader then embed the children in a namespace */
        String loaderId = arrayTypeEntry.getLoaderId();
        if (!loaderId.isEmpty()) {
            pos = writeNameSpace(context, loaderId, buffer, pos);
        }

        /* Write the array layout and array reference DIEs. */
        pos = writeSkeletonArrayLayout(context, arrayTypeEntry, buffer, pos);

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

    private int writeSkeletonArrayLayout(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array layout", pos);
        AbbrevCode abbrevCode = AbbrevCode.CLASS_LAYOUT_ARRAY;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String name = uniqueDebugString(arrayTypeEntry.getTypeName());
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(name), name);
        pos = writeStrSectionOffset(name, buffer, pos);
        log(context, "  [0x%08x]     declaration true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        long typeSignature = arrayTypeEntry.getLayoutTypeSignature();
        log(context, "  [0x%08x]     type 0x%x", pos, typeSignature);
        pos = writeTypeSignature(typeSignature, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeFields(DebugContext context, ArrayTypeEntry arrayTypeEntry, byte[] buffer, int p) {
        int pos = p;
        for (FieldEntry fieldEntry : arrayTypeEntry.getFields()) {
            if (isManifestedField(fieldEntry)) {
                pos = writeField(context, arrayTypeEntry, fieldEntry, buffer, pos);
            }
        }
        return pos;
    }

    private int writeArrayDataType(DebugContext context, TypeEntry elementType, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data type", pos);
        AbbrevCode abbrevCode = AbbrevCode.ARRAY_DATA_TYPE_1;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // Java arrays don't have a fixed byte_size
        String elementTypeName = elementType.getTypeName();
        /* use the compressed type for the element type so pointers get translated */
        long elementTypeSignature = elementType.getTypeSignatureForCompressed();
        log(context, "  [0x%08x]     type idx 0x%x (%s)", pos, elementTypeSignature, elementTypeName);
        pos = writeTypeSignature(elementTypeSignature, buffer, pos);
        return pos;
    }

    private int writeEmbeddedArrayDataType(DebugContext context, TypeEntry valueType, int valueSize, int arraySize, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] embedded array element data type", pos);
        AbbrevCode abbrevCode = AbbrevCode.ARRAY_DATA_TYPE_2;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        // Foreign arrays have a fixed byte_size
        int size = arraySize * valueSize;
        log(context, "  [0x%08x]     byte_size 0x%x", pos, size);
        pos = writeAttrData4(size, buffer, pos);
        String elementTypeName = valueType.getTypeName();
        long elementTypeSignature;
        if (valueType instanceof PointerToTypeEntry pointerTypeEntry) {
            TypeEntry pointerTo = pointerTypeEntry.getPointerTo();
            assert pointerTo != null : "ADDRESS field pointer type must have a known target type";
            // type the array using the referent of the pointer type
            //
            // n.b it is critical for correctness to use the index of the referent rather than
            // the layout type of the referring type even though the latter will (eventually)
            // be set to the same value. the type index of the referent is guaranteed to be set
            // on the first sizing pass before it is consumed here on the second writing pass.
            // However, if this embedded struct field definition precedes the definition of the
            // referring type and the latter precedes the definition of the referent type then
            // the layout index of the referring type may still be unset at this point.
            elementTypeSignature = pointerTo.getTypeSignature();
        } else if (valueType instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
            // type the array using the layout type
            elementTypeSignature = foreignStructTypeEntry.getLayoutTypeSignature();
        } else {
            // otherwise just use the value type
            elementTypeSignature = valueType.getTypeSignature();
        }
        log(context, "  [0x%08x]     type idx 0x%x (%s)", pos, elementTypeSignature, elementTypeName);
        pos = writeTypeSignature(elementTypeSignature, buffer, pos);
        // write subrange child DIE
        log(context, "  [0x%08x] embedded array element range", pos);
        abbrevCode = AbbrevCode.ARRAY_SUBRANGE;
        log(context, "  [0x%08x] <3> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     count 0x%x", pos, arraySize);
        pos = writeAttrData4(arraySize, buffer, pos);
        /*
         * Write a terminating null attribute.
         */
        return writeAttrNull(buffer, pos);
    }

    private int writeArrayElementField(DebugContext context, int offset, int arrayDataTypeIdx, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] array element data field", pos);
        AbbrevCode abbrevCode = AbbrevCode.STRUCT_FIELD;
        log(context, "  [0x%08x] <2> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        String fieldName = uniqueDebugString("data");
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(fieldName), fieldName);
        pos = writeStrSectionOffset(fieldName, buffer, pos);
        log(context, "  [0x%08x]     type idx 0x%x", pos, arrayDataTypeIdx);
        pos = writeInfoSectionOffset(arrayDataTypeIdx, buffer, pos);
        int size = 0;
        log(context, "  [0x%08x]     offset 0x%x (size 0x%x)", pos, offset, size);
        pos = writeAttrData2((short) offset, buffer, pos);
        int modifiers = Modifier.PUBLIC;
        log(context, "  [0x%08x]     modifiers %s", pos, "public");
        return writeAttrAccessibility(modifiers, buffer, pos);
    }

    private int writeMethodLocations(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        for (CompiledMethodEntry compiledMethodEntry : classEntry.compiledMethods()) {
            pos = writeMethodLocation(context, classEntry, compiledMethodEntry, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocation(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledEntry.primary();
        log(context, "  [0x%08x] method location", pos);
        AbbrevCode abbrevCode = AbbrevCode.METHOD_LOCATION;
        log(context, "  [0x%08x] <1> Abbrev Number %d", pos, abbrevCode.ordinal());
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, primary.getLo());
        pos = writeAttrAddress(primary.getLo(), buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, primary.getHi());
        pos = writeAttrAddress(primary.getHi(), buffer, pos);
        /*
         * Should pass true only if method is non-private.
         */
        log(context, "  [0x%08x]     external  true", pos);
        pos = writeFlag(DwarfFlag.DW_FLAG_true, buffer, pos);
        String methodKey = primary.getSymbolName();
        int methodSpecOffset = getMethodDeclarationIndex(primary.getMethodEntry());
        log(context, "  [0x%08x]     specification  0x%x (%s)", pos, methodSpecOffset, methodKey);
        pos = writeInfoSectionOffset(methodSpecOffset, buffer, pos);
        pos = writeMethodParameterLocations(context, classEntry, primary, 2, buffer, pos);
        pos = writeMethodLocalLocations(context, classEntry, primary, 2, buffer, pos);
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

    private int writeMethodParameterLocations(DebugContext context, ClassEntry classEntry, Range range, int depth, byte[] buffer, int p) {
        int pos = p;
        MethodEntry methodEntry;
        if (range.isPrimary()) {
            methodEntry = range.getMethodEntry();
        } else {
            assert !range.isLeaf() : "should only be looking up var ranges for inlined calls";
            methodEntry = range.getCallees().getFirst().getMethodEntry();
        }
        for (LocalEntry paramInfo : methodEntry.getParams()) {
            int refAddr = getMethodLocalIndex(classEntry, methodEntry, paramInfo);
            pos = writeMethodLocalLocation(context, range, paramInfo, refAddr, depth, true, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocations(DebugContext context, ClassEntry classEntry, Range range, int depth, byte[] buffer, int p) {
        int pos = p;
        MethodEntry methodEntry;
        if (range.isPrimary()) {
            methodEntry = range.getMethodEntry();
        } else {
            assert !range.isLeaf() : "should only be looking up var ranges for inlined calls";
            methodEntry = range.getCallees().getFirst().getMethodEntry();
        }

        for (MethodEntry.Local local : methodEntry.getLocals()) {
            int refAddr = getMethodLocalIndex(classEntry, methodEntry, local.localEntry());
            pos = writeMethodLocalLocation(context, range, local.localEntry(), refAddr, depth, false, buffer, pos);
        }
        return pos;
    }

    private int writeMethodLocalLocation(DebugContext context, Range range, LocalEntry localInfo, int refAddr, int depth, boolean isParam, byte[] buffer,
                    int p) {
        int pos = p;
        log(context, "  [0x%08x] method %s location %s:%s", pos, (isParam ? "parameter" : "local"), localInfo.name(), localInfo.type().getTypeName());

        AbbrevCode abbrevCode;
        if (range.hasLocalValues(localInfo)) {
            abbrevCode = (isParam ? AbbrevCode.METHOD_PARAMETER_LOCATION_2 : AbbrevCode.METHOD_LOCAL_LOCATION_2);
        } else {
            abbrevCode = (isParam ? AbbrevCode.METHOD_PARAMETER_LOCATION_1 : AbbrevCode.METHOD_LOCAL_LOCATION_1);
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
        Range primary = compiledEntry.primary();
        if (primary.isLeaf()) {
            return p;
        }
        int pos = p;
        log(context, "  [0x%08x] concrete entries [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodName());
        int depth = 0;
        for (Range subrange : compiledEntry.callRangeStream().toList()) {
            // if we just stepped out of a child range write nulls for each step up
            while (depth > subrange.getDepth()) {
                pos = writeAttrNull(buffer, pos);
                depth--;
            }
            depth = subrange.getDepth();
            pos = writeInlineSubroutine(context, classEntry, subrange, depth + 2, buffer, pos);
            // increment depth to account for parameter and method locations
            depth++;
            pos = writeMethodParameterLocations(context, classEntry, subrange, depth + 2, buffer, pos);
            pos = writeMethodLocalLocations(context, classEntry, subrange, depth + 2, buffer, pos);
        }
        // if we just stepped out of a child range write nulls for each step up
        while (depth > 0) {
            pos = writeAttrNull(buffer, pos);
            depth--;
        }
        return pos;
    }

    private int writeInlineSubroutine(DebugContext context, ClassEntry classEntry, Range caller, int depth, byte[] buffer, int p) {
        assert !caller.isLeaf();
        // the supplied range covers an inline call and references the caller method entry. its
        // child ranges all reference the same inlined called method. leaf children cover code for
        // that inlined method. non-leaf children cover code for recursively inlined methods.
        // identify the inlined method by looking at the first callee
        Range callee = caller.getCallees().getFirst();
        MethodEntry methodEntry = callee.getMethodEntry();
        String methodKey = methodEntry.getSymbolName();
        /* the abstract index was written in the method's class entry */
        int abstractOriginIndex;
        if (classEntry == methodEntry.getOwnerType() && !dwarfSections.isRuntimeCompilation()) {
            abstractOriginIndex = getMethodDeclarationIndex(methodEntry);
        } else {
            abstractOriginIndex = getAbstractInlineMethodIndex(classEntry, methodEntry);
        }

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

    private int writeAbstractInlineMethods(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        Set<MethodEntry> inlinedMethods = collectInlinedMethods(context, classEntry, p);
        int pos = p;
        for (MethodEntry methodEntry : inlinedMethods) {
            // n.b. class entry used to index the method belongs to the inlining method
            // not the inlined method
            setAbstractInlineMethodIndex(classEntry, methodEntry, pos);
            if (dwarfSections.isRuntimeCompilation() && classEntry != methodEntry.getOwnerType()) {
                pos = writeMethodDeclaration(context, classEntry, methodEntry, true, buffer, pos);
            } else {
                pos = writeAbstractInlineMethod(context, classEntry, methodEntry, buffer, pos);
            }
        }
        return pos;
    }

    private Set<MethodEntry> collectInlinedMethods(DebugContext context, ClassEntry classEntry, int p) {
        final HashSet<MethodEntry> methods = new HashSet<>();
        classEntry.compiledMethods().forEach(compiledMethod -> addInlinedMethods(context, compiledMethod, compiledMethod.primary(), methods, p));
        return methods;
    }

    private void addInlinedMethods(DebugContext context, CompiledMethodEntry compiledEntry, Range primary, HashSet<MethodEntry> hashSet, int p) {
        if (primary.isLeaf()) {
            return;
        }
        verboseLog(context, "  [0x%08x] collect abstract inlined methods %s", p, primary.getFullMethodName());
        for (Range subrange : compiledEntry.callRangeStream().toList()) {
            // the subrange covers an inline call and references the caller method entry. its
            // child ranges all reference the same inlined called method. leaf children cover code
            // for
            // that inlined method. non-leaf children cover code for recursively inlined methods.
            // identify the inlined method by looking at the first callee
            Range callee = subrange.getCallees().getFirst();
            MethodEntry methodEntry = callee.getMethodEntry();
            if (hashSet.add(methodEntry)) {
                verboseLog(context, "  [0x%08x]   add abstract inlined method %s", p, methodEntry.getSymbolName());
            }
        }
    }

    private int writeAbstractInlineMethod(DebugContext context, ClassEntry classEntry, MethodEntry method, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] abstract inline method %s::%s", pos, classEntry.getTypeName(), method.getMethodName());
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
        int methodSpecOffset = getMethodDeclarationIndex(method);
        log(context, "  [0x%08x]     specification  0x%x", pos, methodSpecOffset);
        pos = writeInfoSectionOffset(methodSpecOffset, buffer, pos);
        /*
         * If the inline method exists in a different CU then write locals and params otherwise we
         * can just reuse the locals and params in the declaration
         */
        if (classEntry != method.getOwnerType()) {
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
        }
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
        /* save current CU start, so we can write Ref4 attributes as CU offsets. */
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

    private int writeTUHeader(long typeSignature, byte[] buffer, int p) {
        int pos = p;
        /* save current TU start, so we can write Ref4 attributes as CU offsets. */
        unitStart = pos;
        /* CU length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_5, buffer, pos);
        /* Unit type */
        pos = writeDwarfUnitHeader(DwarfUnitHeader.DW_UT_type, buffer, pos);
        /* Address size. */
        pos = writeByte((byte) 8, buffer, pos);
        /* Abbrev offset. */
        pos = writeAbbrevSectionOffset(0, buffer, pos);
        /* Type signature */
        pos = writeTypeSignature(typeSignature, buffer, pos);
        /* Type offset */
        return writeInt(0, buffer, pos);
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

    public int writeCompressedOopConversionExpression(boolean isHub, byte[] buffer, int p) {
        int pos = p;
        /*
         * For an explanation of the conversion rules @see com.oracle.svm.core.heap.ReferenceAccess
         *
         * n.b.
         *
         * The setting for option -H:+/-SpawnIsolates is determined by useHeapBase == true/false.
         * The setting for option -H:+/-UseCompressedReferences is determined by compressionShift >
         * 0.
         *
         */

        boolean useHeapBase = dwarfSections.useHeapBase();
        int reservedHubBitsMask = dwarfSections.reservedHubBitsMask();
        int numReservedHubBits = dwarfSections.numReservedHubBits();
        int compressionShift = dwarfSections.compressionShift();
        int numAlignmentBits = dwarfSections.numAlignmentBits();

        /*
         * First we compute the size of the locexpr and decide how to do any required bit-twiddling
         *
         * The required expression will be one of these paths:
         *
         * push object address ................................ (1 byte) ..... [offset] ............
         * IF isHub && reservedHubBitsMask != 0 .............................. [offset == hub] .....
         * . IF numReservedHubBits == numAlignmentBits && compressionShift == 0 ....................
         * ... push reservedHubBitsMask ....................... (1 byte) ..... [hub, mask] .........
         * ... NOT ............................................ (1 byte) ..... [hub, ~mask] ........
         * ... AND ............................................ (1 byte) ..... [hub] ...............
         * . ELSE ..................................................................................
         * ... push numReservedHubBits ........................ (1 byte) ..... [hub, reserved bits]
         * ... LSHR ........................................... (1 byte) ..... [hub] ...............
         * ... IF compressionShift != numAlignmentBits .............................................
         * ..... push numAlignmentBits - compressionShift ..... (1 byte) ..... [hub, alignment] ....
         * ..... LSHL ......................................... (1 byte) ..... [hub] ...............
         * ... END IF ..............................................................................
         * . END IF ................................................................................
         * END IF ..................................................................................
         * IF useHeapBase ..........................................................................
         * . IF compressionShift != 0 ..............................................................
         * ... push compressionShift .......................... (1 byte) ..... [offset, comp shift]
         * ... LSHL ........................................... (1 byte) ..... [offset] ............
         * . END IF ................................................................................
         * . push rheap+0 ..................................... (2 bytes) .... [offset, rheap] .....
         * . ADD .............................................. (1 byte) ..... [oop] ...............
         * ELSE ....................................................................................
         * ................................................................... [offset == oop] .....
         * END IF ..................................................................................
         * end: .............................................................. [oop] ...............
         */

        int lengthPos = pos;
        /*
         * write dummy expr length (max expression size is 10 -> 1 byte is enough)
         */
        pos = writeULEB(0, buffer, pos);
        int exprStart = pos;
        pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_push_object_address, buffer, pos);
        if (isHub && reservedHubBitsMask != 0) {
            if (numReservedHubBits == numAlignmentBits && compressionShift == 0) {
                pos = writeExprOpcodeLiteral(reservedHubBitsMask, buffer, pos);
                pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_not, buffer, pos);
                pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_and, buffer, pos);
            } else {
                pos = writeExprOpcodeLiteral(numReservedHubBits, buffer, pos);
                pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_shr, buffer, pos);
                if (compressionShift != numAlignmentBits) {
                    pos = writeExprOpcodeLiteral(numAlignmentBits - compressionShift, buffer, pos);
                    pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_shl, buffer, pos);
                }
            }
        }
        if (useHeapBase) {
            if (compressionShift != 0) {
                pos = writeExprOpcodeLiteral(compressionShift, buffer, pos);
                pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_shl, buffer, pos);
            }
            /* add the resulting offset to the heapbase register */
            pos = writeExprOpcodeBReg(dwarfSections.getHeapbaseRegister(), buffer, pos);
            pos = writeSLEB(0, buffer, pos); /* 1 byte. */
            pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_plus, buffer, pos);
        }

        int exprSize = pos - exprStart;
        assert (exprSize >> 7) == 0; // expression length field should fit in one byte
        writeULEB(exprSize, buffer, lengthPos);  // fixup expression length

        return pos;
    }
}
