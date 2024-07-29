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

import com.oracle.objectfile.runtime.dwarf.RuntimeDwarfDebugInfo.AbbrevCode;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfAttribute;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfForm;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfHasChildren;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfTag;
import jdk.graal.compiler.debug.DebugContext;

public class RuntimeDwarfAbbrevSectionImpl extends RuntimeDwarfSectionImpl {

    public RuntimeDwarfAbbrevSectionImpl(RuntimeDwarfDebugInfo dwarfSections) {
        // abbrev section depends on ranges section
        super(dwarfSections, DwarfSectionName.DW_ABBREV_SECTION, DwarfSectionName.DW_FRAME_SECTION);
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        int pos = 0;
        pos = writeAbbrevs(null, null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        pos = writeAbbrevs(context, buffer, pos);

        assert pos == size;
    }

    public int writeAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        // Level 0
        pos = writeMethodCompileUnitAbbrev(context, buffer, pos);

        // Level 1
        pos = writePrimitiveTypeAbbrev(context, buffer, pos);
        pos = writeTypedefAbbrev(context, buffer, pos);
        pos = writeTypedefPointerAbbrev(context, buffer, pos);
        pos = writeMethodLocationAbbrevs(context, buffer, pos);
        pos = writeAbstractInlineMethodAbbrev(context, buffer, pos);

        // Level 2 + inline depth
        pos = writeInlinedSubroutineAbbrev(buffer, pos);
        pos = writeParameterLocationAbbrevs(context, buffer, pos);
        pos = writeLocalLocationAbbrevs(context, buffer, pos);

        /* write a null abbrev to terminate the sequence */
        pos = writeNullAbbrev(context, buffer, pos);
        return pos;
    }

    private int writeAttrType(DwarfAttribute attribute, byte[] buffer, int pos) {
        return writeULEB(attribute.value(), buffer, pos);
    }

    private int writeAttrForm(DwarfForm dwarfForm, byte[] buffer, int pos) {
        return writeULEB(dwarfForm.value(), buffer, pos);
    }

    private int writeHasChildren(DwarfHasChildren hasChildren, byte[] buffer, int pos) {
        return writeByte(hasChildren.value(), buffer, pos);
    }

    private int writeMethodCompileUnitAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(AbbrevCode.METHOD_UNIT, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_compile_unit, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_yes, buffer, pos);
        // pos = writeAttrType(DwarfAttribute.DW_AT_producer, buffer, pos);
        // pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_language, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_use_UTF8, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_comp_dir, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_stmt_list, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_sec_offset, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writePrimitiveTypeAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(AbbrevCode.PRIMITIVE_TYPE, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_base_type, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_bit_size, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_encoding, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }



    private int writeTypedefAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /* A pointer to the class struct type. */
        pos = writeAbbrevCode(AbbrevCode.TYPEDEF, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_typedef, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeTypedefPointerAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /* A pointer to a typedef. */
        pos = writeAbbrevCode(AbbrevCode.TYPEDEF_POINTER, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_pointer_type, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref4, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }


    private int writeMethodLocationAbbrevs(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeMethodLocationAbbrev(context, AbbrevCode.METHOD_LOCATION, buffer, pos);
        pos = writeMethodLocationAbbrev(context, AbbrevCode.METHOD_LOCATION_STATIC, buffer, pos);
        return pos;
    }

    private int writeMethodLocationAbbrev(@SuppressWarnings("unused") DebugContext context, AbbrevCode abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_subprogram, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_external, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
//        pos = writeAttrType(DwarfAttribute.DW_AT_decl_file, buffer, pos);
//        pos = writeAttrForm(DwarfForm.DW_FORM_data2, buffer, pos);
//        pos = writeAttrType(DwarfAttribute.DW_AT_decl_line, buffer, pos);
//        pos = writeAttrForm(DwarfForm.DW_FORM_data2, buffer, pos);
//        pos = writeAttrType(DwarfAttribute.DW_AT_linkage_name, buffer, pos);
//        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_artificial, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        /* This is not in DWARF2 */
        // pos = writeAttrType(DW_AT_virtuality, buffer, pos);
        // pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_containing_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_LOCATION) {
            pos = writeAttrType(DwarfAttribute.DW_AT_object_pointer, buffer, pos);
            pos = writeAttrForm(DwarfForm.DW_FORM_ref4, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeAbstractInlineMethodAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(AbbrevCode.ABSTRACT_INLINE_METHOD, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_subprogram, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_inline, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_external, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }


    private int writeInlinedSubroutineAbbrev(byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(AbbrevCode.INLINED_SUBROUTINE, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_inlined_subroutine, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_abstract_origin, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref4, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_call_file, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data4, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_call_line, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_data4, buffer, pos);
        /* Now terminate. */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeParameterLocationAbbrevs(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeParameterLocationAbbrev(context, AbbrevCode.METHOD_PARAMETER_LOCATION_1, buffer, pos);
        pos = writeParameterLocationAbbrev(context, AbbrevCode.METHOD_PARAMETER_LOCATION_2, buffer, pos);
        return pos;
    }

    private int writeLocalLocationAbbrevs(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeLocalLocationAbbrev(context, AbbrevCode.METHOD_LOCAL_LOCATION_1, buffer, pos);
        pos = writeLocalLocationAbbrev(context, AbbrevCode.METHOD_LOCAL_LOCATION_2, buffer, pos);
        return pos;
    }

    private int writeParameterLocationAbbrev(@SuppressWarnings("unused") DebugContext context, AbbrevCode abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_formal_parameter, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_PARAMETER_LOCATION_2) {
            pos = writeAttrType(DwarfAttribute.DW_AT_location, buffer, pos);
            pos = writeAttrForm(DwarfForm.DW_FORM_sec_offset, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeLocalLocationAbbrev(@SuppressWarnings("unused") DebugContext context, AbbrevCode abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfTag.DW_TAG_variable, buffer, pos);
        pos = writeHasChildren(DwarfHasChildren.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfAttribute.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == AbbrevCode.METHOD_LOCAL_LOCATION_2) {
            pos = writeAttrType(DwarfAttribute.DW_AT_location, buffer, pos);
            pos = writeAttrForm(DwarfForm.DW_FORM_sec_offset, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfAttribute.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfForm.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeNullAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(AbbrevCode.NULL, buffer, pos);
        return pos;
    }
}
