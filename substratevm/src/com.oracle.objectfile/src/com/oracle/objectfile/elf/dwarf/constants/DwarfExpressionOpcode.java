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
public enum DwarfExpressionOpcode {
    DW_OP_addr((byte) 0x03),
    @SuppressWarnings("unused")//
    DW_OP_deref((byte) 0x06),
    DW_OP_dup((byte) 0x12),
    DW_OP_and((byte) 0x1a),
    DW_OP_not((byte) 0x20),
    DW_OP_plus((byte) 0x22),
    DW_OP_shl((byte) 0x24),
    DW_OP_shr((byte) 0x25),
    DW_OP_bra((byte) 0x28),
    DW_OP_eq((byte) 0x29),
    DW_OP_lit0((byte) 0x30),
    DW_OP_reg0((byte) 0x50),
    DW_OP_breg0((byte) 0x70),
    DW_OP_regx((byte) 0x90),
    DW_OP_bregx((byte) 0x92),
    DW_OP_push_object_address((byte) 0x97),
    DW_OP_implicit_value((byte) 0x9e),
    DW_OP_stack_value((byte) 0x9f);

    private final byte value;

    DwarfExpressionOpcode(byte b) {
        value = b;
    }

    public byte value() {
        return value;
    }
}
