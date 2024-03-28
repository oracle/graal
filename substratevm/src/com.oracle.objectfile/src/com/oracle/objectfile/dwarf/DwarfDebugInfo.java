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

package com.oracle.objectfile.dwarf;

import java.nio.ByteOrder;

import com.oracle.objectfile.dwarf.constants.DwarfSectionNameBase;
import com.oracle.objectfile.elf.ELFMachine;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DwarfDebugInfo extends DwarfDebugInfoBase {

    /* Full byte/word values. */
    private final DwarfStrSectionImpl dwarfStrSection;
    private final DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private final DwarfInfoSectionImpl dwarfInfoSection;
    private final DwarfLocSectionImpl dwarfLocSection;
    private final DwarfARangesSectionImpl dwarfARangesSection;
    private final DwarfRangesSectionImpl dwarfRangesSection;
    private final DwarfLineSectionImpl dwarfLineSection;
    private final DwarfFrameSectionImpl dwarfFameSection;
    public final ELFMachine elfMachine;
    /**
     * Register used to hold the heap base.
     */
    private final byte heapbaseRegister;

    @SuppressWarnings("this-escape")
    public DwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl(this);
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new DwarfInfoSectionImpl(this);
        dwarfLocSection = new DwarfLocSectionImpl(this);
        dwarfARangesSection = new DwarfARangesSectionImpl(this);
        dwarfRangesSection = new DwarfRangesSectionImpl(this);
        dwarfLineSection = new DwarfLineSectionImpl(this);

        if (elfMachine == ELFMachine.AArch64) {
            dwarfFameSection = new DwarfFrameSectionImplAArch64(this);
            this.heapbaseRegister = rheapbase_aarch64;
        } else {
            dwarfFameSection = new DwarfFrameSectionImplX86_64(this);
            this.heapbaseRegister = rheapbase_x86;
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

    public DwarfLocSectionImpl getLocSectionImpl() {
        return dwarfLocSection;
    }

    public DwarfARangesSectionImpl getARangesSectionImpl() {
        return dwarfARangesSection;
    }

    public DwarfRangesSectionImpl getRangesSectionImpl() {
        return dwarfRangesSection;
    }

    public DwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }

    public byte getHeapbaseRegister() {
        return heapbaseRegister;
    }

    @Override
    public boolean isAarch64() {
        return elfMachine == ELFMachine.AArch64;
    }

    @Override
    public boolean isAMD64() {
        return elfMachine == ELFMachine.X86_64;
    }

    @Override
    public DwarfSectionNameBase textSectionName() {
        return DwarfSectionName.TEXT_SECTION;
    }

    @Override
    public DwarfSectionNameBase lineSectionName() {
        return DwarfSectionName.DW_LINE_SECTION;
    }

    @Override
    public DwarfSectionNameBase strSectionName() {
        return DwarfSectionName.DW_STR_SECTION;
    }

    @Override
    public DwarfSectionNameBase locSectionName() {
        return DwarfSectionName.DW_LOC_SECTION;
    }

    @Override
    public DwarfSectionNameBase rangesSectionName() {
        return DwarfSectionName.DW_RANGES_SECTION;
    }

    @Override
    public DwarfSectionNameBase arangesSectionName() {
        return DwarfSectionName.DW_ARANGES_SECTION;
    }

    @Override
    public DwarfSectionNameBase frameSectionName() {
        return DwarfSectionName.DW_FRAME_SECTION;
    }

    @Override
    public DwarfSectionNameBase abbrevSectionName() {
        return DwarfSectionName.DW_ABBREV_SECTION;
    }

    @Override
    public DwarfSectionNameBase infoSectionName() {
        return DwarfSectionName.DW_INFO_SECTION;
    }

    /**
     * Various ELF sections created by GraalVM including all debug info sections. The enum sequence
     * starts with the text section (not defined in the DWARF spec and not created by debug info
     * code).
     */
    enum DwarfSectionName implements DwarfSectionNameBase {
        TEXT_SECTION(".text"),
        DW_STR_SECTION(".debug_str"),
        DW_LINE_SECTION(".debug_line"),
        DW_FRAME_SECTION(".debug_frame"),
        DW_ABBREV_SECTION(".debug_abbrev"),
        DW_INFO_SECTION(".debug_info"),
        DW_LOC_SECTION(".debug_loc"),
        DW_ARANGES_SECTION(".debug_aranges"),
        DW_RANGES_SECTION(".debug_ranges");

        private final String value;

        DwarfSectionName(String s) {
            value = s;
        }

        public String value() {
            return value;
        }
    }
}
