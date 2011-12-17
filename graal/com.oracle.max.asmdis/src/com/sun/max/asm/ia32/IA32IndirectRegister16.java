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
package com.sun.max.asm.ia32;

import com.sun.max.asm.x86.*;
import com.sun.max.lang.*;
import com.sun.max.util.*;

/**
 */
public enum IA32IndirectRegister16 implements GeneralRegister<IA32IndirectRegister16>, IndirectRegister {

    BX_PLUS_SI_INDIRECT(INVALID_ID, "%bx,%si", "bx + si"),
    BX_PLUS_DI_INDIRECT(INVALID_ID, "%bx,%di", "bx + si"),
    BP_PLUS_SI_INDIRECT(INVALID_ID, "%bp,%si", "bp + si"),
    BP_PLUS_DI_INDIRECT(INVALID_ID, "%bp,%di", "bp + di"),
            SI_INDIRECT(IA32GeneralRegister16.SI.id(), "%si", "si"),
            DI_INDIRECT(IA32GeneralRegister16.DI.id(), "%di", "di"),
            BP_INDIRECT(IA32GeneralRegister16.BP.id(), "%bp", "bp"),
            BX_INDIRECT(IA32GeneralRegister16.BX.id(), "%bx", "bx");

    public static final Enumerator<IA32IndirectRegister16> ENUMERATOR = new Enumerator<IA32IndirectRegister16>(IA32IndirectRegister16.class);

    private final int id;
    private final String externalValue;
    private final String disassembledValue;

    private IA32IndirectRegister16(int id, String externalValue, String disassembledValue) {
        this.id = id;
        this.externalValue = externalValue;
        this.disassembledValue = disassembledValue;
    }

    public static IA32IndirectRegister16 from(GeneralRegister generalRegister) {
        for (IA32IndirectRegister16 r : ENUMERATOR) {
            if (r.id == generalRegister.id()) {
                return r;
            }
        }
        throw new ArrayIndexOutOfBoundsException();
    }

    public WordWidth width() {
        return WordWidth.BITS_16;
    }

    public int value() {
        return ordinal();
    }

    public int id() {
        return id;
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return externalValue;
    }

    public String disassembledValue() {
        return disassembledValue;
    }

    public Enumerator<IA32IndirectRegister16> enumerator() {
        return ENUMERATOR;
    }

    public IA32IndirectRegister16 exampleValue() {
        return BX_INDIRECT;
    }
}
