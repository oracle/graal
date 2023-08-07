/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.elf.dwarf.constants;

/**
 * Various ELF sections created by GraalVM including all debug info sections. The enum sequence
 * starts with the text section (not defined in the DWARF spec and not created by debug info code).
 */
public enum DwarfSectionName {
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
