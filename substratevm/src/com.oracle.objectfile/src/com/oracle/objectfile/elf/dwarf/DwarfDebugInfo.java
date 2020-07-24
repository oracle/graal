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

import java.nio.ByteOrder;

import com.oracle.objectfile.debugentry.DebugInfoBase;

import com.oracle.objectfile.elf.ELFMachine;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DwarfDebugInfo extends DebugInfoBase {

    /*
     * Names of the different ELF sections we create or reference in reverse dependency order.
     */
    public static final String TEXT_SECTION_NAME = ".text";
    public static final String DW_STR_SECTION_NAME = ".debug_str";
    public static final String DW_LINE_SECTION_NAME = ".debug_line";
    public static final String DW_FRAME_SECTION_NAME = ".debug_frame";
    public static final String DW_ABBREV_SECTION_NAME = ".debug_abbrev";
    public static final String DW_INFO_SECTION_NAME = ".debug_info";
    public static final String DW_ARANGES_SECTION_NAME = ".debug_aranges";

    /**
     * Currently generated debug info relies on DWARF spec vesion 2.
     */
    public static final short DW_VERSION_2 = 2;

    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    @SuppressWarnings("unused") public static final int DW_ABBREV_CODE_null = 0;
    public static final int DW_ABBREV_CODE_compile_unit_1 = 1;
    public static final int DW_ABBREV_CODE_compile_unit_2 = 2;
    public static final int DW_ABBREV_CODE_subprogram = 3;

    /*
     * Define all the Dwarf tags we need for our DIEs.
     */
    public static final int DW_TAG_compile_unit = 0x11;
    public static final int DW_TAG_subprogram = 0x2e;
    /*
     * Define all the Dwarf attributes we need for our DIEs.
     */
    public static final int DW_AT_null = 0x0;
    public static final int DW_AT_name = 0x3;
    public static final int DW_AT_comp_dir = 0x1b;
    public static final int DW_AT_stmt_list = 0x10;
    public static final int DW_AT_low_pc = 0x11;
    public static final int DW_AT_hi_pc = 0x12;
    public static final int DW_AT_language = 0x13;
    public static final int DW_AT_external = 0x3f;
    @SuppressWarnings("unused") public static final int DW_AT_return_addr = 0x2a;
    @SuppressWarnings("unused") public static final int DW_AT_frame_base = 0x40;
    /*
     * Define all the Dwarf attribute forms we need for our DIEs.
     */
    public static final int DW_FORM_null = 0x0;
    @SuppressWarnings("unused") private static final int DW_FORM_string = 0x8;
    public static final int DW_FORM_strp = 0xe;
    public static final int DW_FORM_addr = 0x1;
    public static final int DW_FORM_data1 = 0x0b;
    public static final int DW_FORM_data4 = 0x6;
    @SuppressWarnings("unused") public static final int DW_FORM_data8 = 0x7;
    @SuppressWarnings("unused") public static final int DW_FORM_block1 = 0x0a;
    public static final int DW_FORM_flag = 0xc;

    /*
     * Define specific attribute values for given attribute or form types.
     */
    /*
     * DIE header has_children attribute values.
     */
    public static final byte DW_CHILDREN_no = 0;
    public static final byte DW_CHILDREN_yes = 1;
    /*
     * DW_FORM_flag attribute values.
     */
    @SuppressWarnings("unused") public static final byte DW_FLAG_false = 0;
    public static final byte DW_FLAG_true = 1;
    /*
     * Value for DW_AT_language attribute with form DATA1.
     */
    public static final byte DW_LANG_Java = 0xb;

    /*
     * DW_AT_Accessibility attribute values.
     *
     * These are not needed until we make functions members.
     */
    @SuppressWarnings("unused") public static final byte DW_ACCESS_public = 1;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_protected = 2;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_private = 3;

    /*
     * Others that are not yet needed.
     */
    @SuppressWarnings("unused") public static final int DW_AT_type = 0; // only present for non-void
    // functions
    @SuppressWarnings("unused") public static final int DW_AT_accessibility = 0;

    /*
     * CIE and FDE entries.
     */

    /* Full byte/word values. */
    public static final int DW_CFA_CIE_id = -1;
    @SuppressWarnings("unused") public static final int DW_CFA_FDE_id = 0;

    public static final byte DW_CFA_CIE_version = 1;

    /* Values encoded in high 2 bits. */
    public static final byte DW_CFA_advance_loc = 0x1;
    public static final byte DW_CFA_offset = 0x2;
    public static final byte DW_CFA_restore = 0x3;

    /* Values encoded in low 6 bits. */
    public static final byte DW_CFA_nop = 0x0;
    @SuppressWarnings("unused") public static final byte DW_CFA_set_loc1 = 0x1;
    public static final byte DW_CFA_advance_loc1 = 0x2;
    public static final byte DW_CFA_advance_loc2 = 0x3;
    public static final byte DW_CFA_advance_loc4 = 0x4;
    @SuppressWarnings("unused") public static final byte DW_CFA_offset_extended = 0x5;
    @SuppressWarnings("unused") public static final byte DW_CFA_restore_extended = 0x6;
    @SuppressWarnings("unused") public static final byte DW_CFA_undefined = 0x7;
    @SuppressWarnings("unused") public static final byte DW_CFA_same_value = 0x8;
    public static final byte DW_CFA_register = 0x9;
    public static final byte DW_CFA_def_cfa = 0xc;
    @SuppressWarnings("unused") public static final byte DW_CFA_def_cfa_register = 0xd;
    public static final byte DW_CFA_def_cfa_offset = 0xe;

    private DwarfStrSectionImpl dwarfStrSection;
    private DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private DwarfInfoSectionImpl dwarfInfoSection;
    private DwarfARangesSectionImpl dwarfARangesSection;
    private DwarfLineSectionImpl dwarfLineSection;
    private DwarfFrameSectionImpl dwarfFameSection;
    public final ELFMachine elfMachine;

    public DwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl(this);
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new DwarfInfoSectionImpl(this);
        dwarfARangesSection = new DwarfARangesSectionImpl(this);
        dwarfLineSection = new DwarfLineSectionImpl(this);
        if (elfMachine == ELFMachine.AArch64) {
            dwarfFameSection = new DwarfFrameSectionImplAArch64(this);
        } else {
            dwarfFameSection = new DwarfFrameSectionImplX86_64(this);
        }
    }

    public DwarfStrSectionImpl getStrSectionImpl() {
        return dwarfStrSection;
    }

    public DwarfAbbrevSectionImpl getAbbrevSectionImpl() {
        return dwarfAbbrevSection;
    }

    public DwarfFrameSectionImpl getFrameSectionImpl() {
        return dwarfFameSection;
    }

    public DwarfInfoSectionImpl getInfoSectionImpl() {
        return dwarfInfoSection;
    }

    public DwarfARangesSectionImpl getARangesSectionImpl() {
        return dwarfARangesSection;
    }

    public DwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }
}
