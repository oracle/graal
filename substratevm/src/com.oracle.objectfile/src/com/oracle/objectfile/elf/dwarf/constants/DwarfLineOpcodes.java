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
 * Standard line section opcodes defined by Dwarf 4.
 */
public interface DwarfLineOpcodes {
    /*
     * 0 can be returned to indicate an invalid opcode.
     */
    byte DW_LNS_undefined = 0;
    /*
     * 0 can be inserted as a prefix for extended opcodes.
     */
    byte DW_LNS_extended_prefix = 0;
    /*
     * Append current state as matrix row 0 args.
     */
    byte DW_LNS_copy = 1;
    /*
     * Increment address 1 uleb arg.
     */
    byte DW_LNS_advance_pc = 2;
    /*
     * Increment line 1 sleb arg.
     */
    byte DW_LNS_advance_line = 3;
    /*
     * Set file 1 uleb arg.
     */
    byte DW_LNS_set_file = 4;
    /*
     * sSet column 1 uleb arg.
     */
    byte DW_LNS_set_column = 5;
    /*
     * Flip is_stmt 0 args.
     */
    byte DW_LNS_negate_stmt = 6;
    /*
     * Set end sequence and copy row 0 args.
     */
    byte DW_LNS_set_basic_block = 7;
    /*
     * Increment address as per opcode 255 0 args.
     */
    byte DW_LNS_const_add_pc = 8;
    /*
     * Increment address 1 ushort arg.
     */
    byte DW_LNS_fixed_advance_pc = 9;
    /*
     * Increment address 1 ushort arg.
     */
    @SuppressWarnings("unused") byte DW_LNS_set_prologue_end = 10;
    /*
     * Increment address 1 ushort arg.
     */
    @SuppressWarnings("unused") byte DW_LNS_set_epilogue_begin = 11;
    /*
     * Extended line section opcodes defined by DWARF 2.
     */
    /*
     * There is no extended opcode 0.
     */
    @SuppressWarnings("unused") byte DW_LNE_undefined = 0;
    /*
     * End sequence of addresses.
     */
    byte DW_LNE_end_sequence = 1;
    /*
     * Set address as explicit long argument.
     */
    byte DW_LNE_set_address = 2;
    /*
     * Set file as explicit string argument.
     */
    byte DW_LNE_define_file = 3;
}
