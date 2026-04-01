/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.ObjectFile;

/**
 * Various ELF sections created by GraalVM including all debug info sections. The enum sequence
 * starts with the text section (not defined in the DWARF spec and not created by debug info code).
 */
public enum DwarfSectionName {
    TEXT_SECTION(".text"),
    DW_STR_SECTION(".debug_str"),
    DW_LINE_STR_SECTION(".debug_line_str"),
    DW_LINE_SECTION(".debug_line"),
    DW_FRAME_SECTION(".debug_frame"),
    DW_ABBREV_SECTION(".debug_abbrev"),
    DW_INFO_SECTION(".debug_info"),
    DW_LOCLISTS_SECTION(".debug_loclists"),
    DW_ARANGES_SECTION(".debug_aranges"),
    DW_RNGLISTS_SECTION(".debug_rnglists");

    private final String elfName;
    private final String machoName;

    /**
     * Mach-O section names are stored in a 16-byte field. The field is a fixed-size
     * char array, not a null-terminated string, so all 16 bytes can be used.
     */
    private static final int MACHO_SECTNAME_MAX = 16;

    DwarfSectionName(String elfName) {
        this.elfName = elfName;
        // Convert ELF-style ".debug_*" to Mach-O style "__debug_*"
        // e.g., ".debug_info" -> "__debug_info"
        // Truncate to 16 characters (Mach-O section names are 16-byte fixed fields)
        String converted = "__" + elfName.substring(1).replace('.', '_');
        if (converted.length() > MACHO_SECTNAME_MAX) {
            converted = converted.substring(0, MACHO_SECTNAME_MAX);
        }
        this.machoName = converted;
    }

    /**
     * Returns the ELF-style section name (e.g., ".debug_info").
     */
    public String value() {
        return elfName;
    }

    /**
     * Returns the format-dependent section name.
     * For ELF: ".debug_info"
     * For Mach-O: "__debug_info"
     */
    public String getFormatDependentName(ObjectFile.Format format) {
        if (format == ObjectFile.Format.MACH_O) {
            return machoName;
        }
        return elfName;
    }
}
