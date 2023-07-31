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
 * Constants that appear in CIE and FDE frame section entries.
 */
public interface DwarfFrameValues {
    byte DW_CFA_CIE_version = 1;
    /* Values encoded in high 2 bits. */
    byte DW_CFA_advance_loc = 0x1;
    byte DW_CFA_offset = 0x2;
    byte DW_CFA_restore = 0x3;
    /* Values encoded in low 6 bits. */
    byte DW_CFA_nop = 0x0;
    @SuppressWarnings("unused") byte DW_CFA_set_loc1 = 0x1;
    byte DW_CFA_advance_loc1 = 0x2;
    byte DW_CFA_advance_loc2 = 0x3;
    byte DW_CFA_advance_loc4 = 0x4;
    @SuppressWarnings("unused") byte DW_CFA_offset_extended = 0x5;
    @SuppressWarnings("unused") byte DW_CFA_restore_extended = 0x6;
    @SuppressWarnings("unused") byte DW_CFA_undefined = 0x7;
    @SuppressWarnings("unused") byte DW_CFA_same_value = 0x8;
    byte DW_CFA_register = 0x9;
    byte DW_CFA_def_cfa = 0xc;
    @SuppressWarnings("unused") byte DW_CFA_def_cfa_register = 0xd;
    byte DW_CFA_def_cfa_offset = 0xe;
}
