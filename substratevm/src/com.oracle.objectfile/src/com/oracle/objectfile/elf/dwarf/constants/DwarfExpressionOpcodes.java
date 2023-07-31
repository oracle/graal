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
 * Values used to build DWARF expressions and locations.
 */
public interface DwarfExpressionOpcodes {
    byte DW_OP_addr = 0x03;
    @SuppressWarnings("unused") byte DW_OP_deref = 0x06;
    byte DW_OP_dup = 0x12;
    byte DW_OP_and = 0x1a;
    byte DW_OP_not = 0x20;
    byte DW_OP_plus = 0x22;
    byte DW_OP_shl = 0x24;
    byte DW_OP_shr = 0x25;
    byte DW_OP_bra = 0x28;
    byte DW_OP_eq = 0x29;
    byte DW_OP_lit0 = 0x30;
    byte DW_OP_reg0 = 0x50;
    byte DW_OP_breg0 = 0x70;
    byte DW_OP_regx = (byte) 0x90;
    byte DW_OP_bregx = (byte) 0x92;
    byte DW_OP_push_object_address = (byte) 0x97;
    byte DW_OP_implicit_value = (byte) 0x9e;
    byte DW_OP_stack_value = (byte) 0x9f;
}
