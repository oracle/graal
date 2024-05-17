/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

package com.oracle.objectfile.macho.dsym;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.objectfile.dwarf.DwarfARangesSectionImpl;
import com.oracle.objectfile.dwarf.DwarfAbbrevSectionImpl;
import com.oracle.objectfile.dwarf.DwarfDebugInfoBase;
import com.oracle.objectfile.dwarf.DwarfFrameSectionImplAArch64;
import com.oracle.objectfile.dwarf.DwarfFrameSectionImplX86_64;
import com.oracle.objectfile.dwarf.DwarfInfoSectionImpl;
import com.oracle.objectfile.dwarf.DwarfLineSectionImpl;
import com.oracle.objectfile.dwarf.DwarfSectionImpl;
import com.oracle.objectfile.dwarf.DwarfStrSectionImpl;
import com.oracle.objectfile.dwarf.constants.DwarfSectionNameBase;
import com.oracle.objectfile.macho.MachOCpuType;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DSYMDebugInfo extends DwarfDebugInfoBase {

    private final MachOCpuType cpuType;

    /**
     * Register used to hold the heap base.
     */
    private final byte heapbaseRegister;

    @SuppressWarnings("this-escape")
    public DSYMDebugInfo(MachOCpuType cpuType, ByteOrder byteOrder, Consumer<DwarfSectionImpl> debugSections) {
        super(byteOrder);
        this.cpuType = cpuType;

        debugSections.accept(new DwarfStrSectionImpl(this));
        debugSections.accept(new DwarfAbbrevSectionImpl(this));
        debugSections.accept(new DwarfInfoSectionImpl(this));
        debugSections.accept(new DwarfARangesSectionImpl(this));
        debugSections.accept(new DwarfLineSectionImpl(this));
        // these sections have some small differences in content generation compared to base
        debugSections.accept(new DSYMLocSectionImpl(this));
        debugSections.accept(new DSYMRangesSectionImpl(this));

        if (cpuType.equals(MachOCpuType.ARM64)) {
            debugSections.accept(new DwarfFrameSectionImplAArch64(this));
            this.heapbaseRegister = rheapbase_aarch64;
        } else {
            debugSections.accept(new DwarfFrameSectionImplX86_64(this));
            this.heapbaseRegister = rheapbase_x86;
        }
    }

    public byte getHeapbaseRegister() {
        return heapbaseRegister;
    }

    @Override
    public boolean isAarch64() {
        return cpuType.equals(MachOCpuType.ARM64);
    }

    @Override
    public boolean isAMD64() {
        return cpuType.equals(MachOCpuType.X86_64);
    }

    @Override
    public DwarfSectionNameBase textSectionName() {
        return DSYMSectionName.TEXT_SECTION;
    }

    @Override
    public DwarfSectionNameBase lineSectionName() {
        return DSYMSectionName.DW_LINE_SECTION;
    }

    @Override
    public DwarfSectionNameBase strSectionName() {
        return DSYMSectionName.DW_STR_SECTION;
    }

    @Override
    public DwarfSectionNameBase locSectionName() {
        return DSYMSectionName.DW_LOC_SECTION;
    }

    @Override
    public DwarfSectionNameBase rangesSectionName() {
        return DSYMSectionName.DW_RANGES_SECTION;
    }

    @Override
    public DwarfSectionNameBase arangesSectionName() {
        return DSYMSectionName.DW_ARANGES_SECTION;
    }

    @Override
    public DwarfSectionNameBase frameSectionName() {
        return DSYMSectionName.DW_FRAME_SECTION;
    }

    @Override
    public DwarfSectionNameBase abbrevSectionName() {
        return DSYMSectionName.DW_ABBREV_SECTION;
    }

    @Override
    public DwarfSectionNameBase infoSectionName() {
        return DSYMSectionName.DW_INFO_SECTION;
    }

    @Override
    public boolean layoutDependsOnVaddr() {
        return true;
    }

    @Override
    public boolean isDebugSectionName(String name) {
        return name.startsWith("__debug");
    }

    /**
     * Various ELF sections created by GraalVM including all debug info sections. The enum sequence
     * starts with the text section (not defined in the DWARF spec and not created by debug info
     * code).
     */
    public enum DSYMSectionName implements DwarfSectionNameBase {
        TEXT_SECTION("__text"),
        DW_STR_SECTION("__debug_str"),
        DW_LINE_SECTION("__debug_line"),
        DW_FRAME_SECTION("__debug_frame"),
        DW_ABBREV_SECTION("__debug_abbrev"),
        DW_INFO_SECTION("__debug_info"),
        DW_LOC_SECTION("__debug_loc"),
        DW_ARANGES_SECTION("__debug_aranges"),
        DW_RANGES_SECTION("__debug_ranges");

        private final String value;

        DSYMSectionName(String s) {
            value = s;
        }

        public String value() {
            return value;
        }

        protected static final List<String> debugSections = Arrays.stream(DSYMSectionName.values()).filter(e -> e != DSYMSectionName.TEXT_SECTION).map(e -> e.value()).collect(Collectors.toList());
    }

    public static final List<String> debugSectionList() {
        return Collections.unmodifiableList(DSYMSectionName.debugSections);
    }

    @Override
    public long relocatableLong(long l) {
        return l;
    }

    @Override
    public int relocatableInt(int i) {
        return i;
    }
}
