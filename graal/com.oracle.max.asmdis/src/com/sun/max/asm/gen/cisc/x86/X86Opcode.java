/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.asm.gen.cisc.x86;

import static com.sun.max.asm.gen.cisc.x86.HexByte.*;

/**
 * x86 instruction prefix bytes.
 */
public final class X86Opcode {

    private X86Opcode() {
    }

    public static final HexByte SEG_ES = _26;
    public static final HexByte SEG_SS = _36;
    public static final HexByte SEG_CS = _2E;
    public static final HexByte SEG_DS = _3E;
    public static final HexByte REX_MIN = _40;
    public static final HexByte REX_MAX = _4F;
    public static final HexByte SEG_FS = _64;
    public static final HexByte SEG_GS = _65;
    public static final HexByte OPERAND_SIZE = _66;
    public static final HexByte ADDRESS_SIZE = _67;
    public static final HexByte FWAIT = _9B;
    public static final HexByte LOCK = _F0;
    public static final HexByte REPNE = _F2;
    public static final HexByte REPE = _F3;

    public static boolean isRexPrefix(HexByte opcode) {
        return X86Opcode.REX_MIN.ordinal() <= opcode.ordinal() && opcode.ordinal() <= X86Opcode.REX_MAX.ordinal();
    }

    public static boolean isFloatingPointEscape(HexByte opcode) {
        switch (opcode) {
            case _D8:
            case _D9:
            case _DA:
            case _DB:
            case _DC:
            case _DD:
            case _DE:
            case _DF:
                return true;
            default:
                return false;
        }
    }

}
