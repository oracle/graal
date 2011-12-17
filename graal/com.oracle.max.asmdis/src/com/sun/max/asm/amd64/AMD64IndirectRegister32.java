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
 * Aliases for 32-bit AMD64 general registers to be used for indirect addressing.
 */
public enum AMD64IndirectRegister32 implements GeneralRegister<AMD64IndirectRegister32>, IndirectRegister {

    EAX_INDIRECT,
    ECX_INDIRECT,
    EDX_INDIRECT,
    EBX_INDIRECT,
    ESP_INDIRECT,
    EBP_INDIRECT,
    ESI_INDIRECT,
    EDI_INDIRECT,
    R8D_INDIRECT,
    R9D_INDIRECT,
    R10D_INDIRECT,
    R11D_INDIRECT,
    R12D_INDIRECT,
    R13D_INDIRECT,
    R14D_INDIRECT,
    R15D_INDIRECT;

    public static final Enumerator<AMD64IndirectRegister32> ENUMERATOR = new Enumerator<AMD64IndirectRegister32>(AMD64IndirectRegister32.class);

    public static AMD64IndirectRegister32 from(GeneralRegister generalRegister) {
        return ENUMERATOR.get(generalRegister.id());
    }

    public WordWidth width() {
        return WordWidth.BITS_32;
    }

    public int id() {
        return ordinal();
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

    public Enumerator<AMD64IndirectRegister32> enumerator() {
        return ENUMERATOR;
    }

    public AMD64IndirectRegister32 exampleValue() {
        return EBX_INDIRECT;
    }
}
