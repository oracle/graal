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

import com.oracle.objectfile.LayoutDecision;
import org.graalvm.compiler.debug.DebugContext;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_compile_unit_1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_compile_unit_2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_subprogram;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_comp_dir;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_external;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_hi_pc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_language;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_low_pc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_name;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_null;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_stmt_list;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CHILDREN_no;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CHILDREN_yes;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_addr;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_data1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_data4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_flag;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_null;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_strp;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FRAME_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_compile_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_subprogram;

/**
 * Section generator for debug_abbrev section.
 */
public class DwarfAbbrevSectionImpl extends DwarfSectionImpl {

    public DwarfAbbrevSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_ABBREV_SECTION_NAME;
    }

    @Override
    public void createContent() {
        int pos = 0;
        /*
         * An abbrev table contains abbrev entries for one or more CUs. the table includes a
         * sequence of abbrev entries each of which defines a specific DIE layout employed to
         * describe some DIE in a CU. a table is terminated by a null entry.
         *
         * A null entry has consists of just a 0 abbrev code.
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code; ...... == 0</code>
         *
         * </ul>
         *
         * Mon-null entries have the following format.
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code; ......unique noncode for this layout != 0</code>
         *
         * <li><code>LEB128 tag; .............. defines the type of the DIE (class, subprogram, var
         * etc)</code>
         *
         * <li><code>uint8 has_chldren; ....... is the DIE followed by child DIEs or a sibling
         * DIE</code>
         *
         * <li><code>attribute_spec* .......... zero or more attributes</code>
         *
         * <li><code>null_attribute_spec ...... terminator</code> </ul>
         *
         * An attribute_spec consists of an attribute name and form
         *
         * <ul>
         *
         * <li><code>LEB128 attr_name; ........ 0 for the null attribute name</code>
         *
         * <li><code>LEB128 attr_form; ........ 0 for the null attribute form</code>
         *
         * </ul>
         *
         * For the moment we only use one abbrev table for all CUs. It contains three DIEs. The
         * first two describe the compilation unit itself and the third describes each method within
         * that compilation unit.
         *
         * The DIE layouts are as follows:
         *
         * <ul> <li><code>abbrev_code == 1 or 2, tag == DW_TAG_compilation_unit, has_children</code>
         *
         * <li><code>DW_AT_language : ... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_low_pc : ..... DW_FORM_address</code>
         *
         * <li><code>DW_AT_hi_pc : ...... DW_FORM_address</code>
         *
         * <li><code>DW_AT_stmt_list : .. DW_FORM_data4</code> n.b only for <code>abbrev-code ==
         * 2</code>
         *
         * </ul>
         *
         * <ul> <li><code>abbrev_code == 3, tag == DW_TAG_subprogram, no_children</code>
         *
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_hi_pc : ...... DW_FORM_addr</code>
         *
         * <li><code>DW_AT_external : ... DW_FORM_flag</code>
         *
         * </ul>
         */

        pos = writeCUAbbrev1(null, null, pos);
        pos = writeCUAbbrev2(null, null, pos);
        pos = writeMethodAbbrev(null, null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        pos = writeCUAbbrev1(context, buffer, pos);
        pos = writeCUAbbrev2(context, buffer, pos);
        pos = writeMethodAbbrev(context, buffer, pos);
        assert pos == size;
    }

    @SuppressWarnings("unused")
    private int writeCUAbbrev1(DebugContext context, byte[] buffer, int p) {
        return writeCUAbbrev(context, DW_ABBREV_CODE_compile_unit_1, buffer, p);
    }

    @SuppressWarnings("unused")
    private int writeCUAbbrev2(DebugContext context, byte[] buffer, int p) {
        return writeCUAbbrev(context, DW_ABBREV_CODE_compile_unit_2, buffer, p);
    }

    private int writeAttrType(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    private int writeAttrForm(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    private int writeCUAbbrev(DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        /*
         * Abbrev 1/2 compile unit.
         */
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_language, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_comp_dir, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_compile_unit_1) {
            pos = writeAttrType(DW_AT_stmt_list, buffer, pos);
            pos = writeAttrForm(DW_FORM_data4, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    @SuppressWarnings("unused")
    private int writeMethodAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /*
         * Abbrev 2 compile unit.
         */
        pos = writeAbbrevCode(DW_ABBREV_CODE_subprogram, buffer, pos);
        pos = writeTag(DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_external, buffer, pos);
        pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    /**
     * The debug_abbrev section content depends on debug_frame section content and offset.
     */
    private static final String TARGET_SECTION_NAME = DW_FRAME_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.OFFSET
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
