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
 * Pre-defined value ranges appropriate to a specific attribute or form.
 */
public interface DwarfAttributeValues {
    /*
     * Compile unit DIE header has_children attribute values.
     */
    byte DW_CHILDREN_no = 0;
    byte DW_CHILDREN_yes = 1;
    /*
     * DW_FORM_flag haas two possible attribute values.
     */
    @SuppressWarnings("unused") byte DW_FLAG_false = 0;
    byte DW_FLAG_true = 1;
    /*
     * DW_AT_language attribute with form DATA1 has a range of pre-defined values.
     */
    byte DW_LANG_Java = 0xb;
    /*
     * Values for DW_AT_inline attribute with form DATA1.
     */
    @SuppressWarnings("unused") byte DW_INL_not_inlined = 0;
    byte DW_INL_inlined = 1;
    @SuppressWarnings("unused") byte DW_INL_declared_not_inlined = 2;
    @SuppressWarnings("unused") byte DW_INL_declared_inlined = 3;
    /*
     * DW_AT_Accessibility attribute values.
     */
    @SuppressWarnings("unused") byte DW_ACCESS_public = 1;
    @SuppressWarnings("unused") byte DW_ACCESS_protected = 2;
    @SuppressWarnings("unused") byte DW_ACCESS_private = 3;
    /*
     * DW_AT_encoding attribute values
     */
    @SuppressWarnings("unused") byte DW_ATE_address = 0x1;
    byte DW_ATE_boolean = 0x2;
    byte DW_ATE_float = 0x4;
    byte DW_ATE_signed = 0x5;
    byte DW_ATE_signed_char = 0x6;
    byte DW_ATE_unsigned = 0x7;
    @SuppressWarnings("unused") byte DW_ATE_unsigned_char = 0x8;
}
