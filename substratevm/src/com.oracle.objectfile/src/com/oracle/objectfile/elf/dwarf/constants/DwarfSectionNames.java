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
 * Names of the different ELF sections created by GraalVM in reverse dependency order. The sequence
 * starts with the name of the text section (not defined in the DWARF spec and not created by debug
 * info code).
 */
public interface DwarfSectionNames {
    String TEXT_SECTION_NAME = ".text";
    String DW_STR_SECTION_NAME = ".debug_str";
    String DW_LINE_SECTION_NAME = ".debug_line";
    String DW_FRAME_SECTION_NAME = ".debug_frame";
    String DW_ABBREV_SECTION_NAME = ".debug_abbrev";
    String DW_INFO_SECTION_NAME = ".debug_info";
    String DW_LOC_SECTION_NAME = ".debug_loc";
    String DW_ARANGES_SECTION_NAME = ".debug_aranges";
    String DW_RANGES_SECTION_NAME = ".debug_ranges";
}
