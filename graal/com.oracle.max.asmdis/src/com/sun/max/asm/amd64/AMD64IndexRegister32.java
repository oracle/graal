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
package com.sun.max.asm.amd64;

import com.sun.max.asm.x86.*;
import com.sun.max.lang.*;
import com.sun.max.util.*;

/**
 * Aliases for 32-bit AMD64 general registers to be used as index registers.
 */
public enum AMD64IndexRegister32 implements GeneralRegister<AMD64IndexRegister32> {

    EAX_INDEX,
    ECX_INDEX,
    EDX_INDEX,
    EBX_INDEX,
    // no ESP_INDEX!
    EBP_INDEX,
    ESI_INDEX,
    EDI_INDEX,
    R8D_INDEX,
    R9D_INDEX,
    R10D_INDEX,
    R11D_INDEX,
    R12D_INDEX,
    R13D_INDEX,
    R14D_INDEX,
    R15D_INDEX;

    public static final Enumerator<AMD64IndexRegister32> ENUMERATOR = new Enumerator<AMD64IndexRegister32>(AMD64IndexRegister32.class);

    public static AMD64IndexRegister32 from(GeneralRegister generalRegister) {
        int ordinal = generalRegister.id();
        if (ordinal >= AMD64GeneralRegister32.ESP.id()) {
            ordinal--;
        }
        return ENUMERATOR.get(ordinal);
    }

    public WordWidth width() {
        return WordWidth.BITS_32;
    }

    public int id() {
        int ordinal = ordinal();
        if (ordinal >= AMD64GeneralRegister32.ESP.id()) {
            ordinal++;
        }
        return ordinal;
    }

    public int value() {
        return id();
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return AMD64GeneralRegister32.from(this).externalValue();
    }

    public String disassembledValue() {
        return AMD64GeneralRegister32.from(this).disassembledValue();
    }

    public Enumerator<AMD64IndexRegister32> enumerator() {
        return ENUMERATOR;
    }

    public AMD64IndexRegister32 exampleValue() {
        return ESI_INDEX;
    }
}
