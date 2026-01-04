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
public enum DwarfFrameValue {
    DW_CFA_CIE_version((byte) 1),
    /* Values encoded in high 2 bits. */
    DW_CFA_advance_loc((byte) 0x1),
    DW_CFA_offset((byte) 0x2),
    DW_CFA_restore((byte) 0x3),
    /* Values encoded in low 6 bits. */
    DW_CFA_nop((byte) 0x0),
    @SuppressWarnings("unused")//
    DW_CFA_set_loc1((byte) 0x1),
    DW_CFA_advance_loc1((byte) 0x2),
    DW_CFA_advance_loc2((byte) 0x3),
    DW_CFA_advance_loc4((byte) 0x4),
    @SuppressWarnings("unused")//
    DW_CFA_offset_extended((byte) 0x5),
    @SuppressWarnings("unused")//
    DW_CFA_restore_extended((byte) 0x6),
    @SuppressWarnings("unused")//
    DW_CFA_undefined((byte) 0x7),
    @SuppressWarnings("unused")//
    DW_CFA_same_value((byte) 0x8),
    DW_CFA_register((byte) 0x9),
    DW_CFA_def_cfa((byte) 0xc),
    @SuppressWarnings("unused")//
    DW_CFA_def_cfa_register((byte) 0xd),
    DW_CFA_def_cfa_offset((byte) 0xe);

    private final byte value;

    DwarfFrameValue(byte b) {
        value = b;
    }

    public byte value() {
        return value;
    }
}
